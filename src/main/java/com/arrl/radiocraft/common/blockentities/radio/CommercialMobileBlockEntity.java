package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.client.blockentity.RadioBlockEntityClientHandler;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blocks.radios.CommercialMobileBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.menus.CommercialMobileMenu;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import com.arrl.radiocraft.common.radio.antenna.AntennaDataPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import com.arrl.radiocraft.common.radio.distress.DistressAlert;
import com.arrl.radiocraft.common.radio.distress.DistressLog;
import com.arrl.radiocraft.common.radio.distress.DistressNature;
import com.arrl.radiocraft.common.radio.distress.DistressPackets;
import com.arrl.radiocraft.common.radio.voice.Detuning;
import com.arrl.radiocraft.common.radio.voice.DigitalVoice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A commercial mobile radio's works: an FM (and, on VHF, digital) transceiver on one of its 16 programmed channels.
 * The knob is the only control a user has: stepping the "frequency" (the screen's buttons, sneak-scrolling) turns it
 * to the next channel. The frequency, tones and mode always come from the channel; a blank knob position hears nothing
 * and will not key. Transmissions go out on the radio's own commercial band, so they are in band (no splatter, see
 * {@link com.arrl.radiocraft.common.radio.BandEdges}). Programmed through {@link CommercialProgramming}.
 * <p>
 * Distress beacons (2026-11-09): a VHF mobile whose channel is on 161.975 MHz (programmed there in the CPS, with the
 * knob on it) decodes the alerts distress beacons send there ({@link DistressPackets#BEACON_HZ}), as a radio with an
 * AIS-SART decoder would. A new alert sounds a short alarm and puts {@code DISTRESS DETECTED 161.975} and the beacon's
 * position on the display ({@link #distressLines}) until it is dismissed ({@link #dismissDistress}: the screen's
 * {@code DISMISS_DISTRESS} control, or sneak and use the radio while the alert shows). What it has heard goes in its
 * {@link DistressLog}, which the DISTRESS program reads. The program's personal SOS goes out through it too
 * ({@link #sendBeaconAlert}). A low band mobile never hears them: it cannot be put on 161.975.
 */
public class CommercialMobileBlockEntity extends RadioBlockEntity implements CommercialProgramming.Target, ChannelDisplay {

	/** The case colour when it has not been dyed. */
	public static final int DEFAULT_CASE_COLOUR = 0x2B2D30;

	private CommercialProgram program;
	private int caseColour = -1; // RGB of the dyed case, -1 undyed.

	/** How long a caller's ID stays on the display after the last word of it, ms (client). */
	public static final long CALLER_SHOW_MS = 4000L;
	/** How often a caller still talking is sent to clients again, ns (server). */
	private static final long CALLER_REPEAT_NANOS = 1_500_000_000L;
	private final com.arrl.radiocraft.common.radio.voice.MdcBurst.Sender mdcSender = new com.arrl.radiocraft.common.radio.voice.MdcBurst.Sender();
	/** The unit ID last read off a transmission (server: voice thread; client: from the last sync), 0 for none. */
	private volatile int caller = 0;
	private volatile long callerNanos = Long.MIN_VALUE / 2; // Server: when it was last sent to clients.
	private long callerShownMillis = 0L; // Client: when the last sync carrying a caller arrived.

	public CommercialMobileBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.COMMERCIAL_MOBILE.get(), pos, state, modelOf(state).homeBand());
		ssbEnabled = true; // Always in voice: there is no mode switch.
		program = modelOf(state).factoryProgram();
		CommercialChannel channel = program.active();
		if(channel != null)
			frequency = channel.rxHz();
	}

	private static CommercialRadioModel modelOf(BlockState state) {
		return state.getBlock() instanceof CommercialMobileBlock mobile ? mobile.getModel() : CommercialRadioModel.VHF_MOBILE;
	}

	// -------------------- PROGRAMMING (CommercialProgramming.Target) --------------------

	@Override
	public CommercialRadioModel model() {
		return modelOf(getBlockState());
	}

	@Override
	public CommercialProgram program() {
		return program;
	}

	@Override
	public void storeProgram(CommercialProgram newProgram) {
		program = model().fit(newProgram);
		CommercialChannel channel = program.active();
		if(channel != null)
			frequency = channel.rxHz();
		if(level != null && !level.isClientSide)
			updateIsReceiving();
		setChanged();
		updateBlock();
	}

	/** The channel the knob is on, null on an unprogrammed position. */
	@Nullable
	public CommercialChannel getChannel() {
		return program.active();
	}

	/** The knob position, 0-based. */
	public int getChannelPosition() {
		return program.selected();
	}

	/** Turns the knob to {@code position} (0-based). */
	public void selectChannel(int position) {
		storeProgram(program.select(position));
	}

	/** The radio's buttons and sneak-scrolling step channels, not frequency. */
	@Override
	public void updateFrequency(int stepCount) {
		if(stepCount != 0)
			storeProgram(program.turn(stepCount));
	}

	/** Channelized: the frequency is the channel's, never put on a tuning step. */
	@Override
	protected float snapToTuningRange(float frequency, int stepCount) {
		return frequency;
	}

	@Override
	public boolean tunesOutOfBand() {
		return false;
	}

	@Override
	public boolean hasMarsMod() {
		return false;
	}

	@Override
	public void setMarsMod(boolean value) {} // A commercial radio takes no MARS mod: it is already where it may transmit.

	// -------------------- RADIO --------------------

	@Override
	public boolean isFM() {
		return true;
	}

	/** FM and digital are squelched: no background static (only a blown transmitter's roar, as on the VHF radios). */
	@Override
	public boolean shouldPlayStatic() {
		return false;
	}

	@Override
	protected void setupSoundInstances() {
		super.setupSoundInstances();
		RadioBlockEntityClientHandler.startStaticSoundInstance(this);
	}

	@Override
	public int getFrequencyStep() {
		return CommercialChannel.CHANNEL_STEP_HZ;
	}

	@Override
	public float getTransmitFrequency() {
		CommercialChannel channel = getChannel();
		return channel == null || channel.isReceiveOnly() ? frequency : channel.txHz();
	}

	/** The antenna is matched where it transmits. */
	@Override
	public float getSwrFrequency() {
		return getTransmitFrequency();
	}

	@Override
	public float getTransmitTone() {
		CommercialChannel channel = getChannel();
		return channel == null ? 0.0F : channel.analogTone();
	}

	@Override
	public float getToneSquelch() {
		return getTransmitTone();
	}

	@Override
	protected DigitalVoice.Mode transmitMode() {
		CommercialChannel channel = getChannel();
		return channel == null ? DigitalVoice.Mode.ANALOG : channel.mode().withUnit(program.unitId());
	}

	/** The talker's voice: on an analog channel each key-up starts with the unit ID burst (digital carries the ID in its data). */
	@Override
	public void acceptVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, java.util.UUID sourcePlayer) {
		CommercialChannel channel = getChannel();
		int unit = channel == null || channel.digital() ? 0 : program.unitId();
		super.acceptVoicePacket(level, mdcSender.frame(rawAudio, unit, System.nanoTime()), sourcePlayer);
	}

	@Nullable
	@Override
	protected DigitalVoice.Decoder digitalDecoder() {
		CommercialChannel channel = getChannel();
		return channel == null ? null : channel.decoder();
	}

	/** Narrowband channels: a signal one 12.5 kHz channel off splatters a little. */
	@Override
	public Detuning.Effect getDetuning(float txHz, double splatterReach) {
		return Detuning.fm(getTunedFrequency(), txHz, CommercialChannel.CHANNEL_STEP_HZ, splatterReach);
	}

	/** No transmit on a blank knob position, a receive-only channel, or a channel outside the radio's bands. */
	@Override
	public boolean isTransmitInhibited() {
		CommercialChannel channel = getChannel();
		if(channel == null || channel.isReceiveOnly() || !model().covers(channel.txHz()))
			return true;
		return super.isTransmitInhibited();
	}

	@Override
	public boolean receiveVoice(AntennaVoicePacket packet) {
		if(getChannel() == null)
			return false; // A blank knob position: the receiver is muted.
		boolean heard = super.receiveVoice(packet);
		if(heard)
			callerHeard(DigitalVoice.unitHeard(packet.getVoiceMode(), getLastReception()));
		return heard;
	}

	/**
	 * The receiver read unit {@code unit}'s ID (0: none) off what it heard: the display shows it. Sent to clients when it
	 * changes and again every {@link #CALLER_REPEAT_NANOS} while the unit talks. Voice thread; the sync goes out on the
	 * server thread.
	 */
	private void callerHeard(int unit) {
		if(unit <= 0)
			return;
		long now = System.nanoTime();
		if(unit == caller && now - callerNanos < CALLER_REPEAT_NANOS)
			return;
		caller = unit;
		callerNanos = now;
		if(level != null && level.getServer() != null)
			level.getServer().execute(this::updateBlock);
	}

	/** The unit ID last read off a transmission, 0 if none (server; for tests and debug). */
	public int getLastCaller() {
		return caller;
	}

	/** Client: what the display shows for the caller (alias or "ID 1234") while one is fresh, else null. */
	@Nullable
	public String callerText() {
		if(caller <= 0 || !model().hasDisplay() || System.currentTimeMillis() - callerShownMillis > CALLER_SHOW_MS)
			return null;
		return program.callerText(caller);
	}

	// -------------------- DISTRESS BEACONS (2026-11-09) --------------------

	/** The header line while a beacon alert is shown. */
	public static final String DISTRESS_HEADER = "DISTRESS DETECTED 161.975";

	// Server: what it has heard; client: from the last sync (the alert shown, for the display).
	private final DistressLog distressLog = new DistressLog();

	/** True for a radio that can be put on 161.975 MHz (the VHF mobile, not the low band one). */
	public boolean hearsBeacons() {
		return model().covers(DistressPackets.BEACON_HZ);
	}

	/** True while the knob is on a channel at 161.975 MHz: the radio hears distress beacons. */
	public boolean isOnBeaconFrequency() {
		return getChannel() != null && hearsBeacons() && DistressPackets.isBeacon(getTunedFrequency());
	}

	/** The beacon alerts it has heard. */
	public DistressLog getDistressLog() {
		return distressLog;
	}

	/**
	 * The display's lines while a beacon alert is shown, null when none: {@link #DISTRESS_HEADER}, the beacon's
	 * position ("X 120 Y 64 Z -340") and who it is (the owner, and the nature or, once it is over, its state). Shown
	 * until dismissed; the position follows the beacon's repeats. Client side from the synced state (on the server,
	 * the server's own).
	 */
	@Nullable
	public List<String> distressLines() {
		if(level == null)
			return null;
		DistressLog.Entry shown = distressLog.shown(level.getGameTime());
		if(shown == null)
			return null;
		DistressAlert alert = shown.alert();
		String what = alert.state() != DistressAlert.State.ACTIVE ? alert.state().name()
				: alert.nature() == DistressNature.UNDESIGNATED ? "BEACON" : alert.nature().shortLabel();
		return List.of(DISTRESS_HEADER, alert.coordinates(), (alert.owner() + " " + what).strip());
	}

	/** Clears the alert off the display. False if none was shown. Server thread. */
	public boolean dismissDistress() {
		if(level == null || distressLog.shown(level.getGameTime()) == null)
			return false;
		distressLog.dismiss();
		setChanged();
		updateBlock();
		return true;
	}

	/**
	 * A beacon alert reached the antenna: decoded if the radio is on, its knob is on 161.975 MHz and it is strong
	 * enough. A DSC call is not for this radio (channel 70 is the marine radios'). Anything else goes to the radio as
	 * usual.
	 */
	@Override
	public boolean receiveData(AntennaDataPacket packet) {
		DistressAlert alert = DistressPackets.alert(packet.getPayload());
		if(alert == null)
			return super.receiveData(packet);
		if(alert.isDsc() || !DistressPackets.isBeacon(packet.getFrequency()) || !isOnBeaconFrequency())
			return false;
		if(!(level instanceof ServerLevel serverLevel) || !isPowered() || isDeafNow())
			return false;
		Detuning.Effect effect = getDetuning(packet.getFrequency());
		if(!effect.audible() || !decodes(packet.getStrength() * effect.strength() * desense(packet), getNoiseFloor()))
			return false;
		hearBeacon(serverLevel, alert);
		return true;
	}

	/** A beacon alert decoded: logged; a new active one shown on the display with a short alarm. Server thread. */
	public void hearBeacon(ServerLevel serverLevel, DistressAlert alert) {
		DistressLog.Change change = distressLog.hear(alert, serverLevel.getGameTime());
		if(change == DistressLog.Change.NONE)
			return;
		if(change == DistressLog.Change.NEW && alert.state() == DistressAlert.State.ACTIVE) {
			distressLog.show(alert.id(), Long.MAX_VALUE); // Until dismissed.
			serverLevel.playSound(null, worldPosition, RadiocraftSoundEvents.DSC_ALARM.get(), SoundSource.BLOCKS, 1.0F, 1.2F);
		}
		setChanged();
		updateBlock(); // The display follows the beacon's position and state.
	}

	/**
	 * Sends {@code alert} on 161.975 MHz through the radio's antenna, whatever channel it is on (the DISTRESS program's
	 * personal SOS). False if it cannot: off, blown, or a radio that cannot reach 161.975.
	 */
	public boolean sendBeaconAlert(DistressAlert alert) {
		if(!(level instanceof ServerLevel serverLevel) || !hearsBeacons() || !isPowered() || isBlown())
			return false;
		sendDataOn(serverLevel, DistressPackets.packet(serverLevel.getRandom().nextLong(), alert), DistressPackets.BEACON_HZ);
		return true;
	}

	/** Where the radio really is: on a Sable ship, the ship's place in the world. */
	public BlockPos worldPos() {
		return level == null ? worldPosition : com.arrl.radiocraft.compat.sable.Sublevels.world(level, worldPosition);
	}

	private CompoundTag saveDistress() {
		CompoundTag tag = new CompoundTag();
		ListTag heard = new ListTag();
		for(DistressLog.Entry entry : distressLog.entries()) {
			CompoundTag e = new CompoundTag();
			e.putString("a", entry.alert().encode());
			e.putLong("t", entry.heardAt());
			heard.add(e);
		}
		tag.put("log", heard);
		tag.putLong("shown", distressLog.shownId());
		tag.putLong("shown_until", distressLog.shownUntil());
		return tag;
	}

	private void loadDistress(CompoundTag tag) {
		List<DistressLog.Entry> entries = new ArrayList<>();
		for(Tag t : tag.getList("log", Tag.TAG_COMPOUND)) {
			DistressAlert alert = DistressAlert.decode(((CompoundTag)t).getString("a"));
			if(alert != null)
				entries.add(new DistressLog.Entry(alert, ((CompoundTag)t).getLong("t")));
		}
		distressLog.restore(entries, tag.getLong("shown"), tag.getLong("shown_until"));
	}

	// -------------------- DISPLAY --------------------

	/** The display's big line: the channel name (or the channel number on a radio without a name display). */
	@Override
	public String displayLine() {
		String callerText = level != null && level.isClientSide ? callerText() : null;
		if(callerText != null)
			return callerText; // Who is talking, for a few seconds.
		CommercialChannel channel = getChannel();
		if(channel == null)
			return "CH " + (getChannelPosition() + 1) + " --";
		if(model() == CommercialRadioModel.LOW_BAND_MOBILE || channel.name().isEmpty())
			return "CH " + (getChannelPosition() + 1);
		return channel.name();
	}

	/** The display's small line: the channel number, mode and receive frequency. */
	@Override
	public String displayStatus() {
		CommercialChannel channel = getChannel();
		if(channel == null)
			return "NO CHANNEL";
		String mode = channel.encrypted() ? "DIG ENC" : channel.digital() ? "DIG" : "FM";
		return String.format(Locale.ROOT, "%d %s %.4f", getChannelPosition() + 1, mode, channel.rxHz() / 1e6);
	}

	// -------------------- CASE COLOUR --------------------

	/** The case colour as a block tint (RGB): the dyed colour, or the default. */
	public int getCaseColour() {
		return caseColour < 0 ? DEFAULT_CASE_COLOUR : caseColour;
	}

	public boolean isDyed() {
		return caseColour >= 0;
	}

	// -------------------- MENU, NETWORK --------------------

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return new CommercialMobileMenu(id, this);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new RadioNetworkObject(level, worldPosition, CommonConfig.VHF_BASE_STATION_TRANSMIT_TICK.get(), CommonConfig.VHF_BASE_STATION_RECEIVE_TICK.get());
	}

	// -------------------- SAVE, SYNC, ITEM COMPONENTS --------------------

	@Override
	protected void setupSaveTag(CompoundTag nbt) {
		super.setupSaveTag(nbt);
		CommercialProgram.CODEC.encodeStart(NbtOps.INSTANCE, program).result().ifPresent(tag -> nbt.put("program", tag));
		nbt.putInt("caseColour", caseColour);
		boolean fresh = caller > 0 && System.nanoTime() - callerNanos < 2L * CALLER_REPEAT_NANOS;
		nbt.putInt("caller", fresh ? caller : 0); // For the display (sent with every sync; 0 once it is stale).
		nbt.put("distress", saveDistress()); // Saved, and synced for the display (2026-11-09).
	}

	@Override
	protected void readSaveTag(CompoundTag nbt) {
		super.readSaveTag(nbt);
		ssbEnabled = true;
		if(nbt.contains("program"))
			CommercialProgram.CODEC.parse(NbtOps.INSTANCE, nbt.get("program")).resultOrPartial(error -> Radiocraft.LOGGER.warn("Bad commercial radio program at {}: {}", worldPosition, error))
					.ifPresent(read -> program = read);
		CommercialChannel channel = program.active();
		if(channel != null)
			frequency = channel.rxHz(); // After the base class, which would put it back on a tuning step.
		if(level != null && level.isClientSide && nbt.contains("caller")) {
			int heard = nbt.getInt("caller");
			if(heard > 0) {
				caller = heard;
				callerShownMillis = System.currentTimeMillis();
			}
		}
		if(nbt.contains("distress"))
			loadDistress(nbt.getCompound("distress"));
		if(nbt.contains("caseColour")) {
			int colour = nbt.getInt("caseColour");
			if(colour != caseColour) {
				caseColour = colour;
				if(level != null && level.isClientSide) // Redraw the chunk with the new tint.
					level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
			}
		}
	}

	/** Placed from an item: its programming and its case colour. */
	@Override
	protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
		super.applyImplicitComponents(input);
		CommercialProgram stored = input.get(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value());
		if(stored != null) {
			program = stored;
			CommercialChannel channel = program.active();
			if(channel != null)
				frequency = channel.rxHz();
		}
		DyedItemColor dyed = input.get(DataComponents.DYED_COLOR);
		caseColour = dyed == null ? -1 : dyed.rgb() & 0xFFFFFF;
	}

	/** Broken, it drops with its programming and colour (the loot table copies both). */
	@Override
	protected void collectImplicitComponents(DataComponentMap.@NotNull Builder components) {
		super.collectImplicitComponents(components);
		components.set(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value(), program);
		if(caseColour >= 0)
			components.set(DataComponents.DYED_COLOR, new DyedItemColor(caseColour, true));
	}
}
