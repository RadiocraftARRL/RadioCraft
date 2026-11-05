package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaPacket;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.menus.RealWorldInterfaceMenu;
import com.arrl.radiocraft.common.moderation.ModerationHooks;
import com.arrl.radiocraft.common.network.clientbound.CRealWorldInterfacePacket;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandEdges;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.antenna.EverywhereAntenna;
import com.arrl.radiocraft.common.radio.voice.RadioModeration;
import com.arrl.radiocraft.common.realworld.Bridge;
import com.arrl.radiocraft.common.realworld.Bridges;
import com.arrl.radiocraft.common.realworld.Everywhere;
import com.arrl.radiocraft.common.realworld.InterfaceSpec;
import com.arrl.radiocraft.common.realworld.MonitorSpeaker;
import com.arrl.radiocraft.common.realworld.RealWorldLicences;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * The Real-World Interface (1.12): a station whose microphone and speaker are a real radio, through a sound card and
 * serial port on the server ({@link Bridge}). What it receives in game on its frequency goes out of the sound card with
 * the real radio's PTT keyed; what the real radio receives, when its VOX or COS opens, goes on the air in game from here
 * as a voice transmission. It is a radio like any other in between: an antenna on its coax (or the bare connector), its
 * own frequency and FM or SSB, power from the wire, the moderation log ({@link ModerationHooks.Tag#REAL_WORLD}).
 * <p>
 * <b>Everywhere</b> mode needs no antenna and ignores propagation: it hears every transmission near its frequency in
 * its dimension at its source ({@link Everywhere}), and its own reach every antenna on the band at full strength
 * ({@link EverywhereAntenna}).
 * <p>
 * The interfaces are set up only in the server config file ({@code real_world_interfaces}); the screen picks one by
 * name, one block per interface at a time ({@link Bridges#claim}). Nothing opens unless
 * {@code real_world_interface_enabled} is on, the block is switched on and powered, and an interface is picked. Every
 * player who talks through it must hold a real amateur licence and use their real callsign; the server operator is the
 * control operator.
 */
public class RealWorldInterfaceBlockEntity extends RadioBlockEntity {

	/** Hz the frequency snaps to: 500 Hz on HF, 2.5 kHz on VHF (every 12.5, 20 and 25 kHz channel is on it). */
	public static final int HF_STEP = 500, VHF_STEP = 2_500;
	/** Ticks between status updates to an open screen. */
	public static final int STATUS_TICKS = 5;

	/** The configured interface it uses (synced and saved); empty for none. */
	private String selected = "";
	private boolean everywhere = false;
	private boolean fm = true;
	// Server: the link, and whether it may carry audio now (switched on, powered, picked, enabled). Read by its threads.
	@Nullable
	private volatile Bridge bridge = null;
	private volatile boolean live = false;
	private boolean bridgeDirty = true;
	@Nullable
	private volatile EverywhereAntenna everywhereAntenna = null;
	/** Transmitting the real radio's audio (synced: the TX lamp); also what deafens its receiver. */
	private volatile boolean txActive = false;
	/** The real radio's PTT is keyed (synced: the RX-to-radio lamp). */
	private boolean pttLamp = false;
	private final UUID voiceId;
	/** Plays what goes out to the real radio, quietly, at the block (1.12 open-question fixes). */
	private final MonitorSpeaker monitor;

	public RealWorldInterfaceBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.REAL_WORLD_INTERFACE.get(), pos, state, Band.getBand("2m"));
		ssbEnabled = true; // Voice only: FM or SSB.
		frequency = 146_520_000.0F; // 2m FM simplex calling.
		voiceId = UUID.nameUUIDFromBytes(("radiocraft:real_world_interface@" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
		monitor = new MonitorSpeaker(pos);
	}

	// -------------------- SETTINGS --------------------

	public String getSelected() {
		return selected;
	}

	public boolean isEverywhere() {
		return everywhere;
	}

	/** True while the real radio's audio is going out in game (synced). */
	public boolean isTxActive() {
		return txActive;
	}

	/** True while the real radio's PTT is keyed by what it receives in game (synced). */
	public boolean isPttLamp() {
		return pttLamp;
	}

	/** The voice id its transmissions go out under (a block's own, not a player's). */
	public UUID getVoiceId() {
		return voiceId;
	}

	/** True if {@code player} may change it ({@code real_world_interface_op_only}). */
	public static boolean mayEdit(Player player) {
		return !RadiocraftServerConfig.REAL_WORLD_INTERFACE_OP_ONLY.get() || player.hasPermissions(2);
	}

	/** Its power switch is op only too, with {@code real_world_interface_op_only} (maintainer, 2026-11-05). */
	@Override
	public boolean mayTogglePower(Player player) {
		return mayEdit(player);
	}

	/** {@code player} clicked "I hold a licence": from now on their voice may go out through any interface. Server. */
	public static void acknowledgeLicence(ServerPlayer player) {
		RealWorldLicences.acknowledge(player.server, player.getUUID());
	}

	/** Its monitor speaker, for tests. */
	public MonitorSpeaker getMonitor() {
		return monitor;
	}

	private static Component message(String key, Object... args) {
		return Component.translatable(Radiocraft.translationKey("message", "real_world_interface." + key), args);
	}

	/**
	 * Picks configured interface {@code name}, or none for an empty name. Returns why not, or null if done. Server side;
	 * {@code player} null for code and tests (no permission check).
	 */
	@Nullable
	public Component select(String name, @Nullable Player player) {
		if(player != null && !mayEdit(player))
			return message("op_only");
		if(name.isEmpty()) {
			selected = "";
			Bridges.release(this);
			changed();
			return null;
		}
		if(!Bridges.enabled())
			return message("disabled");
		InterfaceSpec spec = Bridges.spec(name);
		if(spec == null)
			return message("unknown", name);
		if(!Bridges.claim(spec.name(), this, RealWorldInterfaceBlockEntity::holds)) {
			Object owner = Bridges.owner(spec.name());
			return message("in_use", spec.name(), owner instanceof RealWorldInterfaceBlockEntity other ? other.getBlockPos().toShortString() : "?");
		}
		selected = spec.name();
		changed();
		return null;
	}

	/**
	 * True if {@code owner} is a block still there and still set to an interface. (A block that unloads or is broken lets
	 * go of its claim itself; this catches one that was replaced without either.)
	 */
	private static boolean holds(Object owner) {
		return owner instanceof RealWorldInterfaceBlockEntity be && !be.isRemoved() && !be.selected.isEmpty();
	}

	/** Tunes to {@code hz} on whichever band holds it (any band a radio tunes); false if none does. */
	public boolean setTunedFrequency(double hz, @Nullable Player player) {
		if(player != null && !mayEdit(player))
			return false;
		Band found = Band.getBand((float)hz);
		if(found == null || !BandEdges.canTune(found, hz))
			return false;
		if(band == null || !found.name().equals(band.name())) {
			band = found;
			fm = found.isVhf() || !found.isAmateur(); // The usual mode there: FM on VHF and the commercial bands, SSB on HF.
		}
		frequency = snapToTuningRange((float)hz, 0);
		changed();
		return true;
	}

	/** FM or SSB. */
	public boolean setFm(boolean value, @Nullable Player player) {
		if(player != null && !mayEdit(player))
			return false;
		fm = value;
		changed();
		return true;
	}

	/** Everywhere mode on or off. */
	public boolean setEverywhere(boolean value, @Nullable Player player) {
		if(player != null && !mayEdit(player))
			return false;
		everywhere = value;
		changed();
		return true;
	}

	private void changed() {
		bridgeDirty = true;
		if(level != null && !level.isClientSide)
			updateIsReceiving();
		setChanged();
		updateBlock();
	}

	// -------------------- RADIO --------------------

	@Override
	public boolean isFM() {
		return fm;
	}

	@Override
	public int getFrequencyStep() {
		return band != null && band.isVhf() ? VHF_STEP : HF_STEP;
	}

	/** Its microphone is the real radio: nothing said near the block goes out. */
	@Override
	public void acceptVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, UUID sourcePlayer) {}

	@Override
	public void acceptVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, UUID sourcePlayer, Vec3 speakerPos) {}

	/** No PTT of its own: the real radio's VOX or COS keys it. */
	@Override
	public void setPTTDown(boolean value) {}

	@Override
	public boolean canTransmitVoice() {
		return txActive;
	}

	@Override
	public boolean canLendHandMic() {
		return false;
	}

	/** SWR protected, like the commercial gear it stands for: it never blows. */
	@Override
	protected boolean canBlow() {
		return false;
	}

	@Override
	public boolean shouldPlayStatic() {
		return false;
	}

	@Override
	protected boolean usesBareConnector() {
		return !everywhere;
	}

	@Override
	protected boolean isSkipPath(IAntennaPacket packet) {
		return !everywhere && super.isSkipPath(packet);
	}

	@Override
	protected double desense(IAntennaPacket packet) {
		return everywhere ? 1.0D : super.desense(packet);
	}

	/**
	 * In Everywhere mode it hears only what reaches it at the source ({@link #hearEverywhere}); else only its antenna.
	 * A frame from a player who has not clicked "I hold a licence" is dropped here, before the receiver mixes it, so it
	 * never reaches the real radio ({@link RealWorldLicences}: the mixed audio no longer says who spoke).
	 */
	@Override
	public boolean receiveVoice(AntennaVoicePacket packet) {
		EverywhereAntenna own = everywhereAntenna;
		boolean atSource = own != null && packet.getDestination() == own;
		if(everywhere != atSource)
			return false;
		if(!RealWorldLicences.mayBridge(packet.getSourcePlayer()))
			return false;
		return super.receiveVoice(packet);
	}

	/** A frame on the air anywhere in its dimension ({@link Everywhere#onAir}); any thread. */
	public void hearEverywhere(ServerLevel serverLevel, IAntenna from, @Nullable UUID source, short[] pcm, float hz, @Nullable Band onBand, float ctcss) {
		EverywhereAntenna own = everywhereAntenna;
		if(own == null || !everywhere || !live || onBand == null)
			return;
		AntennaVoicePacket packet = new AntennaVoicePacket(() -> serverLevel, pcm.clone(), onBand, hz, EverywhereAntenna.STRENGTH, from, source, ctcss);
		packet.setDestination(own);
		receiveVoice(packet);
	}

	/** What its receiver plays: out to the real radio, and quietly on its monitor speaker (voice chat thread). */
	private void downlinkFrame(short[] pcm) {
		Bridge link = bridge;
		if(link != null && live) {
			link.offerDownlink(pcm);
			if(level instanceof ServerLevel serverLevel)
				monitor.play(serverLevel, pcm);
		}
	}

	/** A frame from the real radio, its VOX or COS open (the bridge's link thread): on the air from here. */
	private void uplinkFrame(short[] pcm) {
		if(!live || isRemoved() || !(level instanceof ServerLevel serverLevel) || !isPowered())
			return;
		float hz = (float)(getTransmitFrequency() + getDriftHz());
		de.maxhenkel.voicechat.api.ServerLevel voiceLevel = () -> serverLevel;
		ModerationHooks.Tag was = ModerationHooks.push(ModerationHooks.Tag.REAL_WORLD);
		try {
			if(everywhere)
				transmitEverywhere(serverLevel, voiceLevel, pcm, hz);
			else
				transmitVoice(voiceLevel, pcm, hz, voiceId, 0.0F);
		}
		finally {
			ModerationHooks.pop(was);
		}
	}

	private void transmitEverywhere(ServerLevel serverLevel, de.maxhenkel.voicechat.api.ServerLevel voiceLevel, short[] pcm, float hz) {
		EverywhereAntenna own = everywhereAntenna;
		if(own == null || isTransmitInhibited())
			return;
		RadioModeration.voice(voiceLevel, voiceId, pcm, hz); // Operators listening in.
		ModerationHooks.voice(own, this, serverLevel, voiceId, pcm, hz, band, 0.0F); // The log, and other Everywhere interfaces.
		own.transmitAudioPacket(voiceLevel, pcm, band, hz, voiceId, 0.0F);
	}

	// -------------------- THE LINK --------------------

	@Override
	protected void additionalTick() {
		if(!(level instanceof ServerLevel serverLevel))
			return;
		long now = serverLevel.getGameTime();
		boolean powered = isPowered();
		if(bridgeDirty || (now + worldPosition.asLong()) % 10L == 0L) {
			bridgeDirty = false;
			syncBridge(powered);
		}
		tickEverywhere(powered);
		Bridge link = bridge;
		boolean tx = live && link != null && link.isUplinkOpen() && powered && !isTransmitInhibited();
		if(tx != txActive) {
			txActive = tx;
			updateIsReceiving(); // Transmitting, its receiver is deaf (simplex).
			updateBlock();
		}
		boolean ptt = live && link != null && link.isPttKeyed();
		if(ptt != pttLamp) {
			pttLamp = ptt;
			updateBlock();
		}
		if(now % STATUS_TICKS == 0L)
			for(ServerPlayer player : serverLevel.players())
				if(player.containerMenu instanceof RealWorldInterfaceMenu menu && menu.blockEntity == this)
					sendStatus(player);
	}

	/** Starts, stops or restarts the link to match the settings and the config. Server thread. */
	private void syncBridge(boolean powered) {
		InterfaceSpec spec = selected.isEmpty() ? null : Bridges.spec(selected);
		boolean claimed = spec != null && Bridges.enabled() && Bridges.claim(spec.name(), this, RealWorldInterfaceBlockEntity::holds);
		if(spec == null)
			Bridges.release(this);
		boolean want = claimed && powered && ssbEnabled;
		Bridge link = bridge;
		if(link != null && (!want || !link.spec().equals(spec) || !link.isRunning())) {
			live = false;
			Bridges.close(link);
			bridge = link = null;
		}
		if(want && link == null)
			bridge = Bridges.open(spec, this::uplinkFrame);
		live = want;
	}

	/** Everywhere mode with a live link: on the air without an antenna, and listening at every source. */
	private void tickEverywhere(boolean powered) {
		boolean wanted = everywhere && live && powered && band != null;
		EverywhereAntenna own = everywhereAntenna;
		if(wanted) {
			if(own == null)
				everywhereAntenna = own = new EverywhereAntenna(this);
			own.register(band);
			Everywhere.listen(this);
		}
		else {
			Everywhere.stop(this);
			if(own != null) {
				own.unregister();
				everywhereAntenna = null;
			}
		}
	}

	/** Lets go of everything: the link (its devices close on its own thread), the claim, the Everywhere air. */
	private void shutDown() {
		live = false;
		Bridge link = bridge;
		bridge = null;
		Bridges.close(link);
		Bridges.release(this);
		Everywhere.stop(this);
		EverywhereAntenna own = everywhereAntenna;
		everywhereAntenna = null;
		if(own != null)
			own.unregister();
		txActive = false;
		monitor.close();
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide) {
			if(level instanceof ServerLevel serverLevel)
				RealWorldLicences.of(serverLevel.getServer()); // Loaded for the voice thread's licence check.
			voiceReceiver.setTap(this::downlinkFrame); // Its "speaker" is the sound card (and the monitor speaker).
			bridgeDirty = true;
		}
	}

	@Override
	public void setRemoved() {
		if(level != null && !level.isClientSide)
			shutDown();
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		if(level != null && !level.isClientSide)
			shutDown();
		super.onChunkUnloaded();
	}

	/** The link, for tests and the status command; null while there is none. */
	@Nullable
	public Bridge getBridge() {
		return bridge;
	}

	/** True while the link may carry audio (switched on, powered, interface picked and enabled). */
	public boolean isLive() {
		return live;
	}

	// -------------------- STATUS FOR THE SCREEN --------------------

	/** What the screen shows, for {@code viewer}. */
	public CompoundTag statusTag(@Nullable Player viewer) {
		CompoundTag tag = new CompoundTag();
		boolean enabled = Bridges.enabled();
		tag.putBoolean("enabled", enabled);
		tag.putBoolean("mayEdit", viewer == null || mayEdit(viewer));
		if(viewer instanceof ServerPlayer player)
			tag.putBoolean("licensed", RealWorldLicences.hasAcknowledged(player.server, player.getUUID()));
		ListTag names = new ListTag();
		if(enabled)
			for(InterfaceSpec spec : Bridges.specs())
				names.add(StringTag.valueOf(spec.name()));
		tag.put("names", names);
		InterfaceSpec spec = selected.isEmpty() ? null : Bridges.spec(selected);
		Object owner = spec == null ? null : Bridges.owner(spec.name());
		String state;
		if(!enabled)
			state = "disabled";
		else if(selected.isEmpty())
			state = "none";
		else if(spec == null)
			state = "unknown";
		else if(owner != null && owner != this && holds(owner)) {
			state = "in_use";
			tag.putString("inUseAt", owner instanceof RealWorldInterfaceBlockEntity other ? other.getBlockPos().toShortString() : "?");
		}
		else if(!isPowered())
			state = "off";
		else
			state = bridge == null ? "starting" : "running";
		tag.putString("state", state);
		if(spec != null) {
			tag.putString("input", spec.inputDevice());
			tag.putString("output", spec.outputDevice());
			tag.putInt("rate", spec.sampleRate());
			tag.putString("ptt", spec.pttText());
			tag.putString("trigger", spec.triggerText());
			tag.putInt("timeout", spec.timeoutSeconds());
		}
		Bridge link = bridge;
		if(link != null) {
			Bridge.Status status = link.status();
			tag.putString("inputState", status.input().name().toLowerCase(Locale.ROOT));
			tag.putString("outputState", status.output().name().toLowerCase(Locale.ROOT));
			tag.putString("serialState", status.serial().name().toLowerCase(Locale.ROOT));
			tag.putString("error", status.error());
			tag.putBoolean("pttKeyed", status.pttKeyed());
			tag.putBoolean("uplink", status.uplinkOpen());
			tag.putBoolean("txTimedOut", status.txTimedOut());
			tag.putBoolean("rxTimedOut", status.rxTimedOut());
			tag.putFloat("inDb", (float)status.inputDb());
			tag.putFloat("outDb", (float)status.outputDb());
		}
		return tag;
	}

	public void sendStatus(ServerPlayer player) {
		CRealWorldInterfacePacket.send(player, worldPosition, statusTag(player));
	}

	// -------------------- SAVE, SYNC, MENU --------------------

	@Override
	protected void setupSaveTag(CompoundTag nbt) {
		super.setupSaveTag(nbt);
		nbt.putString("interface", selected);
		nbt.putBoolean("everywhere", everywhere);
		nbt.putBoolean("fm", fm);
		nbt.putBoolean("txActive", txActive);
		nbt.putBoolean("pttLamp", pttLamp);
	}

	@Override
	protected void readSaveTag(CompoundTag nbt) {
		super.readSaveTag(nbt);
		ssbEnabled = true;
		selected = nbt.getString("interface");
		everywhere = nbt.getBoolean("everywhere");
		fm = !nbt.contains("fm") || nbt.getBoolean("fm");
		boolean client = level != null && level.isClientSide;
		txActive = client && nbt.getBoolean("txActive"); // The server works these out again.
		pttLamp = client && nbt.getBoolean("pttLamp");
		bridgeDirty = true;
	}

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		if(player instanceof ServerPlayer serverPlayer)
			sendStatus(serverPlayer); // Before the first update, so the screen does not open blank.
		return new RealWorldInterfaceMenu(id, this);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new RadioNetworkObject(level, worldPosition, CommonConfig.VHF_BASE_STATION_TRANSMIT_TICK.get(), CommonConfig.VHF_BASE_STATION_RECEIVE_TICK.get());
	}
}
