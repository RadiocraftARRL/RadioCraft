package com.arrl.radiocraft.common.radio.voice.handheld;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.common.items.CbPortableItem;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.TransmitterLog;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaNetwork;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.antenna.data.AntennaData;
import com.arrl.radiocraft.common.radio.antenna.data.EmptyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.antenna.types.CbWhipAntennaType;
import com.arrl.radiocraft.common.radio.cb.CbRadioState;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import com.arrl.radiocraft.common.radio.solar.Aurora;
import com.arrl.radiocraft.common.radio.solar.SolarEvent;
import com.arrl.radiocraft.common.radio.solar.SolarEventManager;
import com.arrl.radiocraft.common.radio.voice.Detuning;
import com.arrl.radiocraft.common.radio.voice.RadioVoiceProcessor;
import com.arrl.radiocraft.common.radio.voice.RadiocraftVoicePlugin;
import com.arrl.radiocraft.common.radio.voice.ReceiverMix;
import com.arrl.radiocraft.common.radio.voice.SpeakerKind;
import com.arrl.radiocraft.common.radio.voice.VoiceMix;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The CB portable in a player's hand (2026-11-08): a 5 W, 40-channel AM walkie-talkie with its whip pulled out
 * ({@link CbWhipAntennaType}). Like the HF manpack it is an antenna on the move on the HF network, its position worked
 * out again every tick. It is on while its volume knob is off OFF; it talks while the player holds right click with it
 * in hand, and only that player's voice goes in. What it hears comes out of its little speaker where the player
 * stands, AM as the CB mobiles hear it ({@link Detuning#am}, {@link ReceiverMix#amHiss}). It runs off its own cells,
 * which a small battery in the other hand recharges ({@link CbPortableItem}).
 */
public class CbPortableRadio implements IAntenna {

	private static final Map<UUID, CbPortableRadio> RADIOS = new ConcurrentHashMap<>();

	private WeakReference<Player> playerRef;
	private volatile AntennaNetwork network = null;
	private final AtomicReference<AntennaPos> antennaPos = new AtomicReference<>();
	private volatile CbRadioState state = null; // Null while it is not in the player's hand, or off.
	private volatile boolean indoors = false;
	private volatile boolean transmitting = false;
	private final VoiceMix.Aligner aligner = new VoiceMix.Aligner(); // Used under this object's lock.
	private final VoiceMix.Renderer renderer = new VoiceMix.Renderer();
	private de.maxhenkel.voicechat.api.opus.OpusEncoder encoder;
	private LocationalAudioChannel channel = null;
	private volatile long lastHeardNanos = 0L;
	private final com.arrl.radiocraft.common.radio.voice.ModeMix.Tones tones = new com.arrl.radiocraft.common.radio.voice.ModeMix.Tones(); // Under this object's lock.

	private CbPortableRadio(Player player) {
		playerRef = new WeakReference<>(player);
	}

	public static Optional<CbPortableRadio> get(UUID uuid) {
		return Optional.ofNullable(RADIOS.get(uuid));
	}

	/** The portable in the player's hand (main hand first), else on their back (Curios), or empty. */
	public static ItemStack find(Player player) {
		if(player.getMainHandItem().getItem() instanceof CbPortableItem)
			return player.getMainHandItem();
		if(player.getOffhandItem().getItem() instanceof CbPortableItem)
			return player.getOffhandItem();
		for(ItemStack worn : com.arrl.radiocraft.compat.curios.CuriosCompat.wornCbPortables(player))
			return worn;
		return ItemStack.EMPTY;
	}

	/** True if {@code stack} is in one of the player's hands (it talks on right click there; worn, on the worn PTT key). */
	private static boolean inHand(Player player, ItemStack stack) {
		return player.getMainHandItem() == stack || player.getOffhandItem() == stack;
	}

	@Nullable
	public Player getPlayer() {
		return playerRef == null ? null : playerRef.get();
	}

	/** True while it is on the air. */
	public boolean isTransmitting() {
		return transmitting;
	}

	/** True if it heard a station in the last half second (for the client's lamp and tests). */
	public boolean isReceiving() {
		return System.nanoTime() - lastHeardNanos < 500_000_000L;
	}

	/** Once a tick: where it is, whether it is on and keyed, and its cells. */
	private void tick() {
		Player player = getPlayer();
		ItemStack stack = player == null ? ItemStack.EMPTY : find(player);
		if(stack.isEmpty() || player.isSpectator() || !CbPortableItem.isOn(stack)) {
			state = null;
			antennaPos.set(null);
			transmitting = false;
			return;
		}
		boolean keyed = inHand(player, stack)
				? PlayerRadioManager.get(player.getUUID()).map(PlayerRadio::isUseHeld).orElse(false) && !player.isShiftKeyDown()
				: PlayerRadioManager.get(player.getUUID()).map(PlayerRadio::isWornPttHeld).orElse(false); // On the back: the worn PTT key.
		if(!CbPortableItem.drain(stack, keyed, isReceiving(), player.level().getGameTime())) { // Flat: it goes dead.
			state = null;
			antennaPos.set(null);
			transmitting = false;
			return;
		}
		transmitting = keyed;
		state = CbPortableItem.state(stack);
		antennaPos.set(new AntennaPos(BlockPos.containing(player.getEyePosition()), player.level()));
		indoors = com.arrl.radiocraft.common.radio.terrain.TerrainPropagation.isIndoors(player.level(), BlockPos.containing(player.getEyePosition()));
	}

	/** HF noise where it stands: the solar noise floor (a share of it for a local signal), plus any aurora. */
	private double noiseFloor(Level level, BlockPos pos, boolean skip) {
		SolarEvent.SolarEventInstance event = SolarEventManager.getEvent(level);
		double noise = (event == null ? 0.0D : event.getEvent().getNoise()) * (skip ? 1.0D : RadiocraftServerConfig.LOS_SOLAR_NOISE_SHARE.get());
		if(Aurora.affects(level, pos))
			noise += RadiocraftServerConfig.AURORA_STATIC.get();
		return Math.min(1.0D, noise);
	}

	// -------------------- TRANSMIT --------------------

	/** The holder talks: out over the whip while it is on and keyed. Voice chat thread. */
	public void acceptVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, UUID sourcePlayer) {
		CbRadioState current = state;
		Player player = getPlayer();
		if(current == null || !transmitting || player == null || !player.getUUID().equals(sourcePlayer))
			return;
		transmitAudioPacket(level, rawAudio.clone(), Band.getBand(Band.CB), (float)current.hz(), sourcePlayer);
	}

	@Override
	public void transmitAudioPacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, Band band, float frequencyHertz, UUID sourcePlayer) {
		AntennaNetwork network = this.network;
		AntennaPos here = antennaPos.get();
		if(network == null || here == null || band == null || !here.level().equals(level.getServerLevel()))
			return;
		TransmitterLog.record(this, here, band, frequencyHertz);
		com.arrl.radiocraft.common.moderation.ModerationHooks.voice(this, this, here.level(), sourcePlayer, rawAudio, frequencyHertz, band, 0.0F);
		com.arrl.radiocraft.common.radio.voice.Modulation was = com.arrl.radiocraft.common.radio.voice.Modulation.push(com.arrl.radiocraft.common.radio.voice.Modulation.AM);
		try {
		for(IAntenna antenna : others(network)) {
			AntennaPos there = antenna.getAntennaPos();
			if(there == null || there.level() != here.level())
				continue;
			AntennaVoicePacket packet = new AntennaVoicePacket(level, rawAudio.clone(), band, frequencyHertz, 1.0F, this, sourcePlayer, 0.0F);
			packet.setDestination(antenna);
			packet.setStrength(CbWhipAntennaType.INSTANCE.getTransmitEfficiency(packet, new EmptyAntennaData(), there.position(), false));
			if(packet.getStrength() > 0.0D)
				antenna.receiveAudioPacket(packet);
		}
		}
		finally {
			com.arrl.radiocraft.common.radio.voice.Modulation.pop(was);
		}
	}

	/** A CB radio sends no Morse. */
	@Override
	public void transmitCWPacket(net.minecraft.server.level.ServerLevel level, Collection<CWBuffer> buffers, Band band, float frequencyHertz) {}

	private Set<IAntenna> others(AntennaNetwork network) {
		Set<IAntenna> master = network.allAntennas();
		Set<IAntenna> copy;
		synchronized(master) {
			copy = new HashSet<>(master);
		}
		copy.remove(this);
		return copy;
	}

	// -------------------- RECEIVE --------------------

	@Override
	public void receiveAudioPacket(AntennaVoicePacket packet) {
		CbRadioState current = state;
		AntennaPos here = antennaPos.get();
		Player player = getPlayer();
		if(current == null || transmitting || here == null || player == null || RadiocraftVoicePlugin.API == null || packet.getLevel() != here.level())
			return;
		com.arrl.radiocraft.common.radio.voice.ModeMix.Result mix = com.arrl.radiocraft.common.radio.voice.ModeMix.of(com.arrl.radiocraft.common.radio.voice.Modulation.AM, packet.getModulation(), Detuning.am(current.hz(), packet.getFrequency(), packet.getSplatterReach()),
				current.hz(), packet.getFrequency());
		Detuning.Effect effect = mix.effect();
		if(!effect.audible())
			return; // Bleedover alone opens nothing.
		double strength = CbWhipAntennaType.INSTANCE.getReceiveEfficiency(packet, new EmptyAntennaData(), here.position()) * effect.strength()
				* TransmitterLog.desense(here.level(), here.position(), packet.getFrequency(), packet.getSource(), this);
		if(strength <= 0.0D)
			return;
		AntennaPos sentFrom = packet.getSource() == null ? null : packet.getSource().getAntennaPos(); // Read once: cleared on the server thread when the sender puts its radio away (2026-11-09).
		if(sentFrom == null)
			return;
		double blocks = Math.sqrt(com.arrl.radiocraft.compat.sable.Sublevels.distSqr(here.level(), sentFrom.position(), here.position()));
		strength *= CbWhipAntennaType.INSTANCE.receiveRangeFactor(packet.getBand(), blocks, here.level().isDay()); // Half a mobile's reach.
		if(strength <= 0.0D)
			return;
		double squelch = current.squelch() / (double)CbRadioState.MAX_KNOB;
		boolean skipPath = packet.getBand() != null && blocks > packet.getBand().losRange();
		if(!com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity.squelchOpens(squelch, strength)
				&& !com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity.squelchOpensOnNoise(squelch, noiseFloor(here.level(), here.position(), skipPath)))
			return; // Nothing (station or band noise) reaches the squelch knob's setting.
		com.arrl.radiocraft.common.moderation.ModerationHooks.heard(packet, this, here.level(), here.position(), player, strength);
		lastHeardNanos = System.nanoTime();
		boolean skip = packet.getBand() != null && blocks > packet.getBand().losRange();
		double noise = noiseFloor(here.level(), here.position(), skip);
		double gain = current.volume() / 5.0D;
		double level = ReceiverMix.amLevel(strength, noise) * gain;
		double multipath = skip ? RadiocraftServerConfig.SKIP_MULTIPATH_DEPTH.get() : 0.0D;
		synchronized(this) {
			if(channel == null) {
				channel = RadiocraftVoicePlugin.API.createLocationalAudioChannel(UUID.randomUUID(), RadiocraftVoicePlugin.API.fromServerLevel(packet.getLevel()),
						RadiocraftVoicePlugin.API.createPosition(player.getX(), player.getEyeY(), player.getZ()));
				if(channel == null)
					return;
				channel.setDistance(12.0F);
			}
			channel.updateLocation(RadiocraftVoicePlugin.API.createPosition(player.getX(), player.getEyeY(), player.getZ()));
			long now = System.nanoTime();
			short[] heard = mix.garble() ? com.arrl.radiocraft.common.radio.voice.ModeMix.garble(packet.getRawAudio(), tones) : packet.getRawAudio();
			List<VoiceMix.Signal> slot = aligner.offer(new VoiceMix.Signal(VoiceMix.key(packet.getSourcePlayer(), packet.getSource()), packet.getSourcePlayer(),
					heard, level, 0.0D, 0.0D, Math.max(effect.splatter(), ReceiverMix.overload(blocks)), 0.0D, -1.0D,
					strength * ReceiverMix.nearness(blocks), multipath, ReceiverMix.amHiss(strength, noise)), now);
			if(slot.isEmpty())
				return;
			short[] audio = renderer.render(slot, SpeakerKind.BUILT_IN.apply(RadioVoiceProcessor.currentSettings()), 1.0D, now).pcm();
			if(encoder == null)
				encoder = RadiocraftVoicePlugin.API.createEncoder();
			channel.send(encoder.encode(audio));
		}
	}

	@Override
	public void receiveCWPacket(AntennaCWPacket packet) {} // AM: Morse is just a tone it does not decode.

	// -------------------- IANTENNA --------------------

	@Override
	public AntennaPos getAntennaPos() {
		return antennaPos.get();
	}

	@Override
	public IAntennaType<? extends AntennaData> getType() {
		return CbWhipAntennaType.INSTANCE;
	}

	@Override
	public AntennaData getData() {
		return new EmptyAntennaData();
	}

	@Override
	public boolean isIndoors() {
		return indoors;
	}

	// -------------------- LIFECYCLE --------------------

	@EventBusSubscriber(modid = Radiocraft.MOD_ID)
	public static final class Events {

		private Events() {}

		@SubscribeEvent
		public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
			CbPortableRadio radio = new CbPortableRadio(event.getEntity());
			RADIOS.put(event.getEntity().getUUID(), radio);
			radio.joinNetwork();
		}

		@SubscribeEvent
		public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
			CbPortableRadio radio = RADIOS.remove(event.getEntity().getUUID());
			if(radio != null && radio.network != null)
				radio.network.removeAntenna(radio);
			if(radio != null)
				synchronized(radio) {
					if(radio.encoder != null) {
						radio.encoder.close();
						radio.encoder = null;
					}
				}
		}

		@SubscribeEvent
		public static void onClone(PlayerEvent.Clone event) {
			CbPortableRadio radio = RADIOS.get(event.getOriginal().getUUID());
			if(radio != null) {
				radio.playerRef = new WeakReference<>(event.getEntity());
				radio.joinNetwork();
			}
		}

		@SubscribeEvent
		public static void onServerTick(ServerTickEvent.Post event) {
			for(CbPortableRadio radio : RADIOS.values())
				radio.tick();
		}
	}

	private void joinNetwork() {
		if(network != null)
			network.removeAntenna(this);
		network = AntennaNetworkManager.getNetwork(AntennaNetworkManager.HF_ID);
		if(network != null)
			network.addAntenna(this);
	}
}
