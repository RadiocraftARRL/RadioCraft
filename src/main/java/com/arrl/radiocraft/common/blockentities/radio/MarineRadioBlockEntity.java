package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.client.blockentity.RadioBlockEntityClientHandler;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.menus.MarineRadioMenu;
import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import com.arrl.radiocraft.common.radio.antenna.AntennaDataPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.distress.DistressAlert;
import com.arrl.radiocraft.common.radio.distress.DistressLog;
import com.arrl.radiocraft.common.radio.distress.DistressNature;
import com.arrl.radiocraft.common.radio.distress.DistressPackets;
import com.arrl.radiocraft.common.radio.distress.DscCall;
import com.arrl.radiocraft.common.radio.distress.Mmsi;
import com.arrl.radiocraft.common.radio.marine.MarineRadioState;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import com.arrl.radiocraft.common.radio.voice.Detuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The fixed-mount VHF marine radio's works: an FM transceiver on the marine channels ({@link MarineChannels}), 25 W
 * or 1 W, with a 16/9 key and dual watch, and a hand mic on a cord. Channel-only: stepping the "frequency" (the
 * screen's buttons, sneak-scrolling) turns the channel knob, and the frequencies always come from the channel (a
 * duplex channel listens on the coast frequency and transmits on the ship's). Weather channels only receive; DSC
 * channel 70 carries no voice. Transmissions go out on commercial VHF, so they are in band. Works on a Sable ship like
 * any radio block.
 * <p>
 * DSC distress (2026-11-09): the DISTRESS button, held, sends a distress call ({@link DscCall}) on channel 70 with the
 * caller's MMSI, the radio's position (live on a Sable ship) and the nature of distress picked with NATURE, switches the
 * radio to 16 for the voice MAYDAY, and repeats the call every 3.5 to 4.5 minutes until someone acknowledges it or the
 * button is held again to cancel. A dedicated DSC receiver listens on 70 whatever channel the radio is on (only DSC:
 * distress beacon alerts on 161.975 MHz are heard by a commercial VHF radio tuned there, {@link CommercialMobileBlockEntity},
 * as real AIS beacons are not decoded by a DSC radio). What it hears goes in its
 * {@link DistressLog}; a new call sounds the alarm and is shown on the display, where ACK acknowledges it. The bursts
 * are data packets through the radio's antenna, so they go as far as its voice would.
 */
public class MarineRadioBlockEntity extends RadioBlockEntity implements ChannelDisplay {

	private volatile MarineRadioState radioState = MarineRadioState.DEFAULT; // Read on the voice thread.
	private final MarineRadioState.PriorityWatch priority = new MarineRadioState.PriorityWatch();

	/** How long a heard alert stays on the display, in ticks: two minutes. */
	public static final int SHOW_TICKS = 2400;
	/** The longest the alarm sounds for one alert, in ticks, if nobody presses a key. */
	public static final int ALARM_TICKS = 600;
	/** Ticks between the alarm's warbles (each is two seconds long). */
	private static final int ALARM_PERIOD = 40;

	// DSC (2026-11-09). Server thread; saved and synced.
	private DscCall dscCall = DscCall.IDLE;
	private DistressNature nature = DistressNature.UNDESIGNATED;
	private final DistressLog distressLog = new DistressLog();
	private long alarmStart = 0L, alarmUntil = 0L;

	public MarineRadioBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.MARINE_RADIO.get(), pos, state, MarineRadios.band());
		ssbEnabled = true; // Always in voice: there is no mode switch.
		frequency = (float)radioState.receiveHz(MarineRadios.region());
	}

	// -------------------- CONTROLS --------------------

	/** The radio's channel, power switch and dual watch. */
	public MarineRadioState getRadioState() {
		return radioState;
	}

	/** Sets the channel, power switch and dual watch at once (screen, packets, debug), and retunes to the channel. */
	public void setRadioState(MarineRadioState newState) {
		if(!newState.channel().equals(radioState.channel()) || newState.dualWatch() != radioState.dualWatch())
			priority.reset();
		radioState = newState;
		frequency = (float)newState.receiveHz(MarineRadios.region());
		if(level != null && !level.isClientSide)
			updateIsReceiving();
		setChanged();
		updateBlock();
	}

	/** The channel it is on, in the plan in force. */
	public MarineChannels.Channel getChannel() {
		return radioState.channel(MarineRadios.region());
	}

	/** The 16/9 key. */
	public void pressSixteenNine() {
		setRadioState(radioState.sixteenNine(MarineRadios.region()));
	}

	/** The HI/LO key. */
	public void togglePower() {
		setRadioState(radioState.togglePower());
	}

	/** The DW key. */
	public void toggleDualWatch() {
		setRadioState(radioState.toggleDualWatch());
	}

	/** The buttons and sneak-scrolling step channels, not frequency. */
	@Override
	public void updateFrequency(int stepCount) {
		if(stepCount != 0)
			setRadioState(radioState.turn(MarineRadios.region(), stepCount));
	}

	/** Channelized: the frequency is the channel's, never put on a tuning step. */
	@Override
	protected float snapToTuningRange(float frequency, int stepCount) {
		return frequency;
	}

	/** Its front panel has a SQL knob (2026-11-09). */
	@Override
	public boolean hasSquelchKnob() {
		return true;
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
	public void setMarsMod(boolean value) {} // A marine radio takes no MARS mod: it only has the marine channels.

	// -------------------- RADIO --------------------

	@Override
	public boolean isFM() {
		return true;
	}

	/** FM is squelched: no background static (only a blown transmitter's roar, as on the VHF radios). */
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
		return MarineRadioState.CHANNEL_STEP_HZ;
	}

	/** The ship frequency of the channel (on a duplex channel 4.6 MHz below where it listens). */
	@Override
	public float getTransmitFrequency() {
		return (float)radioState.transmitHz(MarineRadios.region());
	}

	/** The antenna is matched where it transmits. */
	@Override
	public float getSwrFrequency() {
		return getTransmitFrequency();
	}

	/** 25 W, or 1 W on LO and on the 1 W channels. */
	@Override
	protected float transmitPower() {
		return radioState.basePower(MarineRadios.region());
	}

	/** No transmit on a weather channel or on DSC channel 70. */
	@Override
	public boolean isTransmitInhibited() {
		if(!radioState.mayTransmit(MarineRadios.region()))
			return true;
		return super.isTransmitInhibited();
	}

	/** On its channel, and with dual watch on channel 16 too: whichever of the two hears the signal better. */
	@Override
	public Detuning.Effect getDetuning(float txHz, double splatterReach) {
		Detuning.Effect working = Detuning.fm(getTunedFrequency(), txHz, MarineRadioState.CHANNEL_STEP_HZ, splatterReach);
		double watch = radioState.watchHz(MarineRadios.region());
		if(watch <= 0.0D || working.audible())
			return working;
		Detuning.Effect sixteen = Detuning.fm(watch, txHz, MarineRadioState.CHANNEL_STEP_HZ, splatterReach);
		if(sixteen.audible())
			return sixteen;
		return sixteen.qrm() > working.qrm() ? sixteen : working;
	}

	/**
	 * Dual watch gives channel 16 priority: a signal there is heard, and while 16 has been busy in the last
	 * {@link MarineRadioState#PRIORITY_HOLD_NANOS} the working channel is muted.
	 */
	@Override
	public boolean receiveVoice(AntennaVoicePacket packet) {
		double watch = radioState.watchHz(MarineRadios.region());
		if(watch <= 0.0D)
			return super.receiveVoice(packet);
		long now = System.nanoTime();
		if(Detuning.fm(watch, packet.getFrequency(), MarineRadioState.CHANNEL_STEP_HZ).audible()) {
			boolean heard = super.receiveVoice(packet);
			if(heard)
				priority.heardPriority(now);
			return heard;
		}
		if(priority.prioritySpeaking(now))
			return false; // Channel 16 has the receiver.
		return super.receiveVoice(packet);
	}

	/** True while dual watch is holding the receiver on channel 16 (for debug and tests). */
	public boolean isPriorityBusy() {
		return radioState.watchHz(MarineRadios.region()) > 0.0D && priority.prioritySpeaking(System.nanoTime());
	}

	// -------------------- DSC DISTRESS (2026-11-09) --------------------

	/** The radio's own distress call. */
	public DscCall getDscCall() {
		return dscCall;
	}

	/** The nature of distress the next call carries (the NATURE key). */
	public DistressNature getNature() {
		return nature;
	}

	/** What the DSC receiver has heard. */
	public DistressLog getDistressLog() {
		return distressLog;
	}

	/** True while the alarm is sounding at game time {@code now}. */
	public boolean isAlarming(long now) {
		return now < alarmUntil;
	}

	/** Where the radio really is: on a Sable ship, the ship's place in the world. */
	public BlockPos worldPos() {
		return level == null ? worldPosition : com.arrl.radiocraft.compat.sable.Sublevels.world(level, worldPosition);
	}

	/** A DSC key pressed on the screen by {@code player} (see {@link MarineRadioState.Key}). Any key silences the alarm. */
	public void dscKey(MarineRadioState.Key key, int value, java.util.UUID player) {
		silence();
		switch(key) {
			case DISTRESS -> {
				if(value == 1)
					sendDistress(Mmsi.of(player));
				else
					cancelDistress();
			}
			case NATURE -> {
				if(dscCall.canSend()) { // The nature goes with the call: it cannot change once sent.
					nature = nature.next();
					changed();
				}
			}
			case ACK -> {
				DistressLog.Entry shown = level == null ? null : distressLog.shown(level.getGameTime());
				if(shown != null)
					acknowledge(shown.alert().id(), Mmsi.of(player));
			}
			case DISMISS -> {
				distressLog.dismiss();
				changed();
			}
			default -> {}
		}
	}

	/** Stops the alarm. */
	public void silence() {
		if(alarmUntil != 0L) {
			alarmUntil = 0L;
			changed();
		}
	}

	/**
	 * Sends a distress call from {@code mmsi}: on channel 70, then the radio goes to 16 for the voice MAYDAY. Refused
	 * (false) without power, blown, or while a call is already on.
	 */
	public boolean sendDistress(String mmsi) {
		return sendDistress(mmsi, nature);
	}

	/**
	 * As {@link #sendDistress(String)}, with {@code callNature} as the nature of distress (the DISTRESS program's SOS,
	 * 2026-11-09): it becomes the NATURE key's setting too, so the radio shows what it sends.
	 */
	public boolean sendDistress(String mmsi, DistressNature callNature) {
		if(!dscCall.canSend())
			return false;
		nature = callNature == null ? DistressNature.UNDESIGNATED : callNature;
		if(!(level instanceof ServerLevel serverLevel) || !dscCall.canSend() || !isPowered() || isBlown())
			return false;
		long id = serverLevel.getRandom().nextLong();
		if(id == 0L)
			id = 1L;
		dscCall = DscCall.send(id, mmsi, nature, serverLevel.getGameTime(), DscCall.repeatTicks(serverLevel.getRandom().nextDouble()));
		BlockPos at = worldPos();
		burst(dscCall.alert(at.getX(), at.getY(), at.getZ()));
		setRadioState(radioState.select(MarineRadios.region(), MarineChannels.DISTRESS));
		changed();
		return true;
	}

	/** Cancels the radio's distress call (the button held again): a cancel burst goes out. False if there is none. */
	public boolean cancelDistress() {
		if(!dscCall.canCancel())
			return false;
		dscCall = dscCall.cancelled();
		BlockPos at = worldPos();
		burst(dscCall.alert(at.getX(), at.getY(), at.getZ()));
		nature = DistressNature.UNDESIGNATED;
		changed();
		return true;
	}

	/** Why an acknowledgement was refused, or {@link #OK}. */
	public enum AckResult { OK, NO_POWER, UNKNOWN, NOT_DSC, NOT_ACTIVE }

	/**
	 * Acknowledges distress call {@code id} (heard by this radio) as the station {@code byMmsi}: the acknowledgement goes
	 * out on channel 70, and the caller's radio stops repeating when it hears it. Beacons cannot be acknowledged.
	 */
	public AckResult acknowledge(long id, String byMmsi) {
		DistressLog.Entry entry = distressLog.get(id);
		if(entry == null)
			return AckResult.UNKNOWN;
		if(!entry.alert().isDsc())
			return AckResult.NOT_DSC;
		if(entry.alert().state() != DistressAlert.State.ACTIVE)
			return AckResult.NOT_ACTIVE;
		if(!isPowered() || isBlown())
			return AckResult.NO_POWER;
		burst(entry.alert().acknowledgedBy(byMmsi));
		distressLog.setState(id, DistressAlert.State.ACKNOWLEDGED, byMmsi);
		alarmUntil = 0L;
		changed();
		return AckResult.OK;
	}

	/** Puts {@code alert} on the air (channel 70 for DSC), past the voice inhibit on 70; a short beep at the radio. */
	private void burst(DistressAlert alert) {
		if(!(level instanceof ServerLevel serverLevel) || !isPowered() || isBlown())
			return;
		sendDataOn(serverLevel, DistressPackets.packet(serverLevel.getRandom().nextLong(), alert), DistressPackets.frequency(alert));
		serverLevel.playSound(null, worldPosition, RadiocraftSoundEvents.DSC_BEEP.get(), SoundSource.BLOCKS, 0.5F, 0.8F);
	}

	/**
	 * The DSC receiver: a DSC distress burst on channel 70 is taken whatever channel the radio is on, if the radio is on
	 * and the burst is strong enough to decode. Beacon alerts (161.975 MHz) are not DSC: a marine radio never decodes
	 * them, even on that channel (2026-11-09). Everything else goes to the radio as usual.
	 */
	@Override
	public boolean receiveData(AntennaDataPacket packet) {
		DistressAlert alert = DistressPackets.alert(packet.getPayload());
		if(alert == null)
			return super.receiveData(packet);
		if(!alert.isDsc() || !DistressPackets.isDsc(packet.getFrequency()))
			return false;
		if(!(level instanceof ServerLevel serverLevel) || !isPowered())
			return false;
		if(!decodes(packet.getStrength() * desense(packet), getNoiseFloor()))
			return false;
		hearDistress(serverLevel, alert);
		return true;
	}

	/** A distress burst decoded: our own call's ack, or one for the log, the display and the alarm. Server thread. */
	public void hearDistress(ServerLevel serverLevel, DistressAlert alert) {
		long now = serverLevel.getGameTime();
		if(alert.isDsc() && alert.id() == dscCall.alertId() && dscCall.state() != DscCall.State.IDLE) {
			if(alert.state() == DistressAlert.State.ACKNOWLEDGED && dscCall.state() == DscCall.State.ACTIVE) {
				dscCall = dscCall.acknowledged(alert.id(), alert.ackBy()); // Someone has us: stop repeating.
				beep(serverLevel);
				changed();
			}
			return;
		}
		DistressLog.Change change = distressLog.hear(alert, now);
		boolean active = alert.state() == DistressAlert.State.ACTIVE;
		switch(change) {
			case NEW -> {
				distressLog.show(alert.id(), now + SHOW_TICKS);
				if(active)
					alarm(now);
				else
					beep(serverLevel);
			}
			case STATE -> {
				DistressLog.Entry shown = distressLog.shown(now);
				if(shown == null || shown.alert().id() == alert.id()) {
					distressLog.show(alert.id(), now + SHOW_TICKS);
					alarmUntil = 0L; // Acknowledged, cancelled or spent: its alarm stops.
				}
				beep(serverLevel);
			}
			case REPEAT -> {
				if(active && alert.isDsc()) { // A real radio alarms again on each repeat of a call nobody has answered.
					distressLog.show(alert.id(), now + SHOW_TICKS);
					alarm(now);
				}
			}
			case NONE -> {}
		}
		changed();
	}

	private void alarm(long now) {
		alarmStart = now;
		alarmUntil = now + ALARM_TICKS;
	}

	private void beep(ServerLevel serverLevel) {
		serverLevel.playSound(null, worldPosition, RadiocraftSoundEvents.DSC_BEEP.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
	}

	private void changed() {
		setChanged();
		updateBlock();
	}

	/** Repeats the call when due and sounds the alarm, on the server. */
	@Override
	protected void additionalTick() {
		super.additionalTick();
		if(!(level instanceof ServerLevel serverLevel))
			return;
		long now = serverLevel.getGameTime();
		boolean powered = wasPowered();
		if(powered && dscCall.repeatDue(now)) {
			dscCall = dscCall.repeated(now, DscCall.repeatTicks(serverLevel.getRandom().nextDouble()));
			BlockPos at = worldPos();
			burst(dscCall.alert(at.getX(), at.getY(), at.getZ()));
			changed();
		}
		if(alarmUntil > now) {
			if(!powered)
				silence();
			else if((now - alarmStart) % ALARM_PERIOD == 0L)
				serverLevel.playSound(null, worldPosition, RadiocraftSoundEvents.DSC_ALARM.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}

	private CompoundTag saveDsc() {
		CompoundTag tag = new CompoundTag();
		tag.putString("state", dscCall.state().name());
		tag.putLong("id", dscCall.alertId());
		tag.putString("mmsi", dscCall.mmsi());
		tag.putString("call_nature", dscCall.nature().name());
		tag.putLong("sent", dscCall.sentAt());
		tag.putLong("next", dscCall.nextAt());
		tag.putString("ack_by", dscCall.ackBy());
		tag.putString("nature", nature.name());
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
		tag.putLong("alarm_start", alarmStart);
		tag.putLong("alarm_until", alarmUntil);
		return tag;
	}

	private void loadDsc(CompoundTag tag) {
		DscCall.State state;
		try {
			state = DscCall.State.valueOf(tag.getString("state"));
		}
		catch(IllegalArgumentException e) {
			state = DscCall.State.IDLE;
		}
		dscCall = state == DscCall.State.IDLE ? DscCall.IDLE : new DscCall(state, tag.getLong("id"), tag.getString("mmsi"),
				DistressNature.byName(tag.getString("call_nature")), tag.getLong("sent"), tag.getLong("next"), tag.getString("ack_by"));
		nature = DistressNature.byName(tag.getString("nature"));
		java.util.List<DistressLog.Entry> entries = new java.util.ArrayList<>();
		for(Tag t : tag.getList("log", Tag.TAG_COMPOUND)) {
			DistressAlert alert = DistressAlert.decode(((CompoundTag)t).getString("a"));
			if(alert != null)
				entries.add(new DistressLog.Entry(alert, ((CompoundTag)t).getLong("t")));
		}
		distressLog.restore(entries, tag.getLong("shown"), tag.getLong("shown_until"));
		alarmStart = tag.getLong("alarm_start");
		alarmUntil = tag.getLong("alarm_until");
	}

	// -------------------- DISPLAY --------------------

	@Override
	public String displayLine() {
		return radioState.displayLine(MarineRadios.region());
	}

	@Override
	public String displayStatus() {
		return radioState.displayStatus(MarineRadios.region());
	}

	// -------------------- MENU, NETWORK --------------------

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return new MarineRadioMenu(id, this);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new RadioNetworkObject(level, worldPosition, CommonConfig.VHF_BASE_STATION_TRANSMIT_TICK.get(), CommonConfig.VHF_BASE_STATION_RECEIVE_TICK.get());
	}

	// -------------------- SAVE, SYNC, ITEM COMPONENTS --------------------

	@Override
	protected void setupSaveTag(CompoundTag nbt) {
		super.setupSaveTag(nbt);
		MarineRadioState.CODEC.encodeStart(NbtOps.INSTANCE, radioState).result().ifPresent(tag -> nbt.put("marine", tag));
		nbt.put("dsc", saveDsc());
	}

	@Override
	protected void readSaveTag(CompoundTag nbt) {
		super.readSaveTag(nbt);
		ssbEnabled = true;
		if(nbt.contains("marine"))
			MarineRadioState.CODEC.parse(NbtOps.INSTANCE, nbt.get("marine")).resultOrPartial(error -> Radiocraft.LOGGER.warn("Bad marine radio state at {}: {}", worldPosition, error))
					.ifPresent(read -> radioState = read);
		frequency = (float)radioState.receiveHz(MarineRadios.region()); // After the base class, which would put it on a tuning step.
		if(nbt.contains("dsc"))
			loadDsc(nbt.getCompound("dsc"));
	}

	/** Placed from an item: its channel and switches. */
	@Override
	protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
		super.applyImplicitComponents(input);
		MarineRadioState stored = input.get(RadiocraftDataComponent.MARINE_RADIO.value());
		if(stored != null) {
			radioState = stored;
			frequency = (float)stored.receiveHz(MarineRadios.region());
		}
	}

	/** Broken, it drops with its channel and switches (the loot table copies them). */
	@Override
	protected void collectImplicitComponents(DataComponentMap.@NotNull Builder components) {
		super.collectImplicitComponents(components);
		components.set(RadiocraftDataComponent.MARINE_RADIO.value(), radioState);
	}
}
