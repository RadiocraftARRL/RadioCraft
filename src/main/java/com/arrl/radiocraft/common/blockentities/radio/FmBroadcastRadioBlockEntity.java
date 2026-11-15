package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.common.menus.SimpleRadioMenu;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.voice.Detuning;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What the FM broadcast transmitter and the broadcast receiver share (2026-11-13): the FM broadcast band
 * ({@link Band#FM_BROADCAST}) in 200 kHz channels from 88.1 to 107.9 MHz, FM voice, and their screen
 * ({@link SimpleRadioMenu}).
 */
public abstract class FmBroadcastRadioBlockEntity extends RadioBlockEntity {

	public static final float LOW_HZ = 88_100_000.0F, HIGH_HZ = 107_900_000.0F;
	public static final int STEP_HZ = 200_000;
	public static final float DEFAULT_HZ = 99_900_000.0F;

	protected FmBroadcastRadioBlockEntity(BlockEntityType<? extends RadioBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state, Band.getBand(Band.FM_BROADCAST));
		ssbEnabled = true; // Always voice.
		frequency = DEFAULT_HZ;
	}

	/** {@code hz} on the 200 kHz grid (88.1, 88.3 ...), inside the band. */
	public static float snap(double hz) {
		long steps = Math.round((hz - LOW_HZ) / STEP_HZ);
		long max = Math.round((HIGH_HZ - LOW_HZ) / STEP_HZ);
		return (float)(LOW_HZ + (double)Math.max(0L, Math.min(max, steps)) * STEP_HZ);
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
	public boolean isFM() {
		return true;
	}

	/** Wide FM: a station is heard across its own 200 kHz channel and splatters only onto the next. */
	@Override
	public Detuning.Effect getDetuning(float txHz, double splatterReach) {
		return Detuning.fm(getTunedFrequency(), txHz, STEP_HZ, splatterReach);
	}

	/** "99.9", as the dial reads. */
	public static String format(float hz) {
		return String.format(Locale.ROOT, "%.1f", hz / 1_000_000.0D);
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
	protected void readSaveTag(CompoundTag nbt) {
		float hz = nbt.contains("frequency") ? nbt.getFloat("frequency") : DEFAULT_HZ;
		super.readSaveTag(nbt);
		ssbEnabled = true;
		frequency = snap(hz < LOW_HZ - STEP_HZ ? DEFAULT_HZ : hz);
	}
}
