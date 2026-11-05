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
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.marine.MarineRadioState;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import com.arrl.radiocraft.common.radio.voice.Detuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
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
 */
public class MarineRadioBlockEntity extends RadioBlockEntity implements ChannelDisplay {

	private volatile MarineRadioState radioState = MarineRadioState.DEFAULT; // Read on the voice thread.
	private final MarineRadioState.PriorityWatch priority = new MarineRadioState.PriorityWatch();

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
	}

	@Override
	protected void readSaveTag(CompoundTag nbt) {
		super.readSaveTag(nbt);
		ssbEnabled = true;
		if(nbt.contains("marine"))
			MarineRadioState.CODEC.parse(NbtOps.INSTANCE, nbt.get("marine")).resultOrPartial(error -> Radiocraft.LOGGER.warn("Bad marine radio state at {}: {}", worldPosition, error))
					.ifPresent(read -> radioState = read);
		frequency = (float)radioState.receiveHz(MarineRadios.region()); // After the base class, which would put it on a tuning step.
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
