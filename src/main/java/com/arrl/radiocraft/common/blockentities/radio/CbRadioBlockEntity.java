package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.client.blockentity.RadioBlockEntityClientHandler;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blocks.radios.CbRadioBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.menus.CbRadioMenu;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.cb.CbMobileModel;
import com.arrl.radiocraft.common.radio.cb.CbRadioState;
import com.arrl.radiocraft.common.radio.cb.SpeechCompressor;
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

import java.util.UUID;

/**
 * A CB mobile's works (2026-11-08): 40 AM channels on 27 MHz ({@link com.arrl.radiocraft.common.radio.cb.CbChannels}),
 * 5 W, no licence. Channel-only like the marine radio: the frequency always comes from the channel knob. It hears AM
 * ({@link #isAM()}): everyone on the channel at once, bleedover from the next channel, no background static, and hiss
 * as a signal weakens. The CB band ({@link Band#CB}) carries a little less far than 10m, for the lower power. It draws
 * an SSB rig's power while it listens and only a little more on the air. The 5875's speech compressor works on what it
 * sends ({@link SpeechCompressor}); its meters, and the PC68's, read from {@link #getSyncedSignal()},
 * {@link #getModulationLevel()} and {@link #getAntennaSWR()}.
 */
public class CbRadioBlockEntity extends RadioBlockEntity implements ChannelDisplay {

	private volatile CbRadioState radioState = CbRadioState.DEFAULT; // Read on the voice thread.
	private final SpeechCompressor compressor = new SpeechCompressor();
	/** The peak of the last frame sent, 0 to 1 (voice thread), and when; what the modulation meter reads. */
	private volatile double modulationNow = 0.0D;
	private volatile long modulationNanos = 0L;
	/** The modulation as last synced to the client, rounded to tenths. */
	private double modulation = 0.0D;
	/** True while the radio has power to hand (a charged battery on its wire, or it is running): the clock runs. Synced. */
	private boolean supplied = false;

	public CbRadioBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.CB_RADIO.get(), pos, state, Band.getBand(Band.CB));
		ssbEnabled = true; // Always in voice: there is no mode switch.
		frequency = (float)radioState.hz();
	}

	public CbMobileModel model() {
		return getBlockState().getBlock() instanceof CbRadioBlock block ? block.getModel() : CbMobileModel.PC68;
	}

	// -------------------- CONTROLS --------------------

	public CbRadioState getRadioState() {
		return radioState;
	}

	/** Sets the knobs and switches (screen, packets, debug), and retunes to the channel. The PC68 has no compressor. */
	public void setRadioState(CbRadioState newState) {
		newState = model().deluxe() ? newState.withoutFm() : newState.withoutDeluxeControls(); // Only the controls each set has.
		CbRadioState old = radioState;
		radioState = newState;
		frequency = (float)newState.hz();
		if(level != null && !level.isClientSide) {
			applyVolume(old.volume(), newState.volume());
			updateIsReceiving();
		}
		setChanged();
		updateBlock();
	}

	/**
	 * The volume knob is the power switch, as on the real sets: its lowest click is OFF. Only turning it across OFF
	 * switches the radio (on from OFF, off to OFF), and the switch is set, never flipped, so nothing else (the channel
	 * knob, CH9, a quick double click) can turn the radio off. The speaker follows the knob.
	 */
	private void applyVolume(int was, int now) {
		if(now > 0)
			setSpeakerGain(now / 5.0F);
		if((was > 0) == (now > 0))
			return;
		if(getNetworkObject(level, worldPosition) instanceof RadioNetworkObject networkObject && networkObject.wantsOn != (now > 0))
			toggle();
	}

	/**
	 * Keeps the knob where the switch is: a radio switched on some other way (an older save, a command) shows its volume
	 * on at least 1, and one switched off shows OFF. Server side, every couple of ticks.
	 */
	private void matchKnobToSwitch(RadioNetworkObject networkObject) {
		int volume = radioState.volume();
		if(networkObject.wantsOn && volume == 0)
			radioState = radioState.withVolume(Math.max(1, Math.round(getSpeakerGain() * 5.0F)));
		else if(!networkObject.wantsOn && volume > 0)
			radioState = radioState.withVolume(0);
		else
			return;
		setChanged();
		updateBlock();
	}

	/** True while it has power to hand: the clock runs, even with the radio switched off. */
	public boolean isSupplied() {
		return supplied;
	}

	/** The frequency buttons and sneak-scrolling turn the channel knob. */
	@Override
	public void updateFrequency(int stepCount) {
		if(stepCount != 0)
			setRadioState(radioState.press(CbRadioState.Key.CHANNEL, stepCount));
	}

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
	public void setMarsMod(boolean value) {} // Channel-only: a MARS mod has nothing to open up.

	@Override
	public int getFrequencyStep() {
		return com.arrl.radiocraft.common.radio.cb.CbChannels.SPACING_HZ;
	}

	// -------------------- RADIO --------------------

	/** AM, unless the 5875 is on a sideband or the PC68 on FM (2026-11-09). */
	@Override
	public boolean isAM() {
		return radioState.mode() == CbRadioState.Mode.AM;
	}

	@Override
	public boolean isFM() {
		return radioState.mode() == CbRadioState.Mode.FM;
	}

	@Override
	public boolean isLowerSideband() {
		return radioState.mode() == CbRadioState.Mode.LSB;
	}

	/** The clarifier tunes the receiver only, as on most CB sets: the transmitter stays on the channel. */
	@Override
	public float getTunedFrequency() {
		return super.getTunedFrequency() + radioState.clarifierHz();
	}

	@Override
	public double getReceiverGain() {
		return radioState.rfGainFactor();
	}

	@Override
	public double getImpulseNoiseFactor() {
		return radioState.impulseNoiseFactor();
	}

	/** The SQUELCH knob, 0 to 10, against the RX meter's scale. */
	@Override
	public float getSquelchLevel() {
		return radioState.squelch() / (float)CbRadioState.MAX_KNOB;
	}

	/** AM hisses on the band's noise while the squelch is down at it; the SQUELCH knob shuts it (2026-11-09). */
	@Override
	public boolean shouldPlayStatic() {
		return true;
	}

	@Override
	protected void setupSoundInstances() {
		super.setupSoundInstances();
		RadioBlockEntityClientHandler.startStaticSoundInstance(this); // Band hiss with the squelch down, and a blown transmitter's roar.
	}

	/** 5 W, a little more talk power with the compressor on. The band's shorter reach is what the low power costs. */
	@Override
	protected float transmitPower() {
		float power = radioState.mode().ssb() ? CbRadioState.SSB_TALK_POWER : 1.0F; // 12 W PEP on sideband, 4 W of carrier on AM.
		return radioState.compressor() ? power * CbRadioState.COMPRESSOR_TALK_POWER : power;
	}

	@Override
	protected void transmitVoice(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, float txHz, UUID sourcePlayer, float ctcssTone) {
		short[] audio = rawAudio;
		if(radioState.compressor()) {
			audio = compressor.process(rawAudio.clone());
			modulationNow = compressor.lastPeak();
		}
		else
			modulationNow = SpeechCompressor.peak(rawAudio);
		modulationNanos = System.nanoTime();
		super.transmitVoice(level, audio, txHz, sourcePlayer, ctcssTone);
	}

	/** What an SWR meter on the open connector of a radio with no antenna reads: next to everything comes back. */
	public static final double OPEN_SWR = RadioBlockEntity.NO_ANTENNA_SWR;

	/** The SWR the meters read: the antenna's, or {@link #OPEN_SWR} with none (the radio reports 0 then). */
	public double getMeterSwr() {
		return getAntennaSWR() <= 0.01D ? OPEN_SWR : getAntennaSWR();
	}

	/** The TX power meter on the air, 0 to 1: forward power less what the mismatch sends back. */
	public double getPowerMeter() {
		return 0.85D * com.arrl.radiocraft.common.radio.SWRHelper.getEfficiencyMultiplier(getMeterSwr());
	}

	/** The SWR meter, 0 (1:1) to 1 (3:1 and worse). */
	public double getSwrMeter() {
		return Math.max(0.0D, Math.min(1.0D, (getMeterSwr() - 1.0D) / 2.0D));
	}

	/** How hard the transmitter is modulated, 0 to 1 (synced, for the modulation meter); 0 off the air. */
	public double getModulationLevel() {
		return modulation;
	}

	@Override
	protected void additionalTick() {
		super.additionalTick();
		if(level == null || level.isClientSide || level.getGameTime() % 2L != 0L)
			return;
		if(getNetworkObject(level, worldPosition) instanceof RadioNetworkObject switchObject)
			matchKnobToSwitch(switchObject);
		if(level.getGameTime() % 10L == 0L && getNetworkObject(level, worldPosition) instanceof RadioNetworkObject networkObject) {
			boolean now = networkObject.isPowered || networkObject.hasCharge();
			if(now != supplied) {
				supplied = now;
				updateBlock();
			}
		}
		double now = isPTTDown() && System.nanoTime() - modulationNanos < 200_000_000L ? modulationNow : 0.0D;
		double rounded = Math.round(Math.min(1.0D, now) * 10.0D) / 10.0D;
		if(rounded != modulation) {
			modulation = rounded;
			updateBlock();
		}
	}

	// -------------------- DISPLAY --------------------

	/** The 5875's clock: {hours (1-12), minutes, 1 for PM}, from the time of day (it runs off the mains, not the radio). */
	public static int[] clock(long dayTime) {
		long time = Math.floorMod(dayTime, 24000L);
		int hours24 = (int)((time / 1000L + 6L) % 24L), minutes = (int)(time % 1000L * 60L / 1000L);
		return new int[] {hours24 % 12 == 0 ? 12 : hours24 % 12, minutes, hours24 >= 12 ? 1 : 0};
	}

	@Override
	public String displayLine() {
		return radioState.displayLine();
	}

	@Override
	public String displayStatus() {
		return radioState.displayStatus();
	}

	// -------------------- MENU, NETWORK --------------------

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return new CbRadioMenu(id, this);
	}

	/** An SSB rig's draw while it listens, only a little more on the air (5 W). */
	@Override
	public BENetworkObject createNetworkObject() {
		return new RadioNetworkObject(level, worldPosition, CommonConfig.CB_RADIO_TRANSMIT_TICK.get(), CommonConfig.CB_RADIO_RECEIVE_TICK.get());
	}

	// -------------------- SAVE, SYNC, ITEM COMPONENTS --------------------

	@Override
	protected void setupSaveTag(CompoundTag nbt) {
		super.setupSaveTag(nbt);
		CbRadioState.CODEC.encodeStart(NbtOps.INSTANCE, radioState).result().ifPresent(tag -> nbt.put("cb", tag));
		nbt.putDouble("modulation", modulation);
		nbt.putBoolean("supplied", supplied);
	}

	@Override
	protected void readSaveTag(CompoundTag nbt) {
		super.readSaveTag(nbt);
		ssbEnabled = true;
		if(nbt.contains("cb"))
			CbRadioState.CODEC.parse(NbtOps.INSTANCE, nbt.get("cb")).resultOrPartial(error -> Radiocraft.LOGGER.warn("Bad CB radio state at {}: {}", worldPosition, error))
					.ifPresent(read -> radioState = read);
		modulation = nbt.getDouble("modulation");
		supplied = nbt.getBoolean("supplied");
		frequency = (float)radioState.hz(); // After the base class, which would put it on a tuning step.
	}

	@Override
	protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
		super.applyImplicitComponents(input);
		CbRadioState stored = input.get(RadiocraftDataComponent.CB_RADIO.value());
		if(stored != null) {
			radioState = stored;
			frequency = (float)stored.hz();
		}
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.@NotNull Builder components) {
		super.collectImplicitComponents(components);
		components.set(RadiocraftDataComponent.CB_RADIO.value(), radioState);
	}
}
