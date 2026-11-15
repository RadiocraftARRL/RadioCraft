package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.client.blockentity.RadioBlockEntityClientHandler;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.menus.SimpleRadioMenu;
import com.arrl.radiocraft.common.radio.Band;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The airband radio (2026-11-13): a panel-mount aviation COM, AM voice on the aircraft band ({@link Band#AIRBAND},
 * 118.000-136.975 MHz) in 25 kHz channels. As on the real sets it has two frequencies, the one in USE and a STANDBY one:
 * the knobs tune STANDBY (the small knob 25 kHz a click, the big one 1 MHz) and the flip-flop button swaps them. AM is
 * heard as the CB hears it: everyone on the channel at once, with hiss as a signal weakens.
 */
public class AirbandRadioBlockEntity extends RadioBlockEntity {

	public static final float LOW_HZ = 118_000_000.0F, HIGH_HZ = 136_975_000.0F;
	public static final int STEP_HZ = 25_000;
	/** Steps of the big tuning knob: 1 MHz. */
	public static final int COARSE_STEPS = 40;
	/** The frequencies a new radio starts on: the emergency guard frequency in use, the air-to-air one standing by. */
	public static final float DEFAULT_USE_HZ = 121_500_000.0F, DEFAULT_STANDBY_HZ = 122_750_000.0F;
	/** Power a tick listening and on the air: a few watts of AM. */
	public static final int RECEIVE_TICK = 6, TRANSMIT_TICK = 12;

	private float standbyHz = DEFAULT_STANDBY_HZ;

	public AirbandRadioBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.AIRBAND_RADIO.get(), pos, state, Band.getBand(Band.AIRBAND));
		ssbEnabled = true; // Always voice: there is no mode switch.
		frequency = DEFAULT_USE_HZ;
	}

	/** {@code hz} on the 25 kHz grid, inside the band. */
	public static float snap(double hz) {
		long steps = Math.round((hz - LOW_HZ) / STEP_HZ);
		long max = Math.round((HIGH_HZ - LOW_HZ) / STEP_HZ);
		return (float)(LOW_HZ + (double)Math.max(0L, Math.min(max, steps)) * STEP_HZ);
	}

	public float getStandbyFrequency() {
		return standbyHz;
	}

	/** The knobs tune STANDBY, {@code stepCount} 25 kHz steps (the big knob sends {@link #COARSE_STEPS} at a time). */
	@Override
	public void updateFrequency(int stepCount) {
		if(stepCount == 0)
			return;
		standbyHz = snap((double)standbyHz + (double)stepCount * STEP_HZ);
		setChanged();
		updateBlock();
	}

	/** The flip-flop button: USE and STANDBY change places. */
	public void flip() {
		float use = frequency;
		frequency = snap(standbyHz);
		standbyHz = snap(use);
		setChanged();
		updateBlock();
	}

	@Override
	protected float snapToTuningRange(float hz, int stepCount) {
		return snap((double)hz + (double)stepCount * STEP_HZ);
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
	public void setMarsMod(boolean value) {}

	@Override
	public int getFrequencyStep() {
		return STEP_HZ;
	}

	@Override
	public boolean isAM() {
		return true;
	}

	/**
	 * Its panel has a squelch knob (2026-11-13: without this the server ignored the knob, so the squelch stayed open and
	 * the static played whatever it was set to).
	 */
	@Override
	public boolean hasSquelchKnob() {
		return true;
	}

	/** AM hisses on the band's noise while the squelch is down at it. */
	@Override
	public boolean shouldPlayStatic() {
		return true;
	}

	/**
	 * An AM airband set with its squelch open (or MON pressed) hisses as loud as a CB's on a quiet band (2026-11-13: at
	 * the FM sets' VHF floor it was too faint to hear). With no antenna it hisses faintly, as every radio does
	 * ({@link RadioBlockEntity#BARE_STATIC}, lifted a little by its AGC).
	 */
	public static final double AM_HISS = 0.45D;
	@Override
	public double vhfStaticFloor() {
		return AM_HISS;
	}

	@Override
	protected void setupSoundInstances() {
		super.setupSoundInstances();
		RadioBlockEntityClientHandler.startStaticSoundInstance(this);
	}

	/** "121.500", as the windows show it. */
	public static String format(float hz) {
		return String.format(Locale.ROOT, "%.3f", hz / 1_000_000.0D);
	}

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return new SimpleRadioMenu(id, this);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new RadioNetworkObject(level, worldPosition, TRANSMIT_TICK, RECEIVE_TICK);
	}

	@Override
	protected void setupSaveTag(CompoundTag nbt) {
		super.setupSaveTag(nbt);
		nbt.putFloat("standbyHz", standbyHz);
	}

	@Override
	protected void readSaveTag(CompoundTag nbt) {
		float use = nbt.contains("frequency") ? nbt.getFloat("frequency") : DEFAULT_USE_HZ;
		super.readSaveTag(nbt);
		ssbEnabled = true;
		frequency = snap(use < LOW_HZ ? DEFAULT_USE_HZ : use);
		standbyHz = snap(nbt.contains("standbyHz") ? nbt.getFloat("standbyHz") : DEFAULT_STANDBY_HZ);
	}
}
