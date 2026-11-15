package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.voice.Detuning;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A VHF radio's STEP (2026-11-14, the maintainer: a satellite on 164.4875 could not be tuned on 20 kHz steps, and
 * 164.480 is too far off to hear it).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class VhfStepGameTests {

	private static final float SATELLITE = 164_487_500.0F;

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void twelveAndAHalfKhzReachesTheSatellite(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 2, 2);
		helper.setBlock(at, RadiocraftBlocks.VHF_BASE_STATION.get());
		VHFRadioBlockEntity radio = GameTestUtil.blockEntity(helper, at, VHFRadioBlockEntity.class);
		helper.assertTrue(radio.hasTuneStep(), "The base station has no STEP setting");
		radio.setFrequency(164_480_000.0F);
		Detuning.Effect before = radio.getDetuning(SATELLITE);
		helper.assertFalse(before.audible(), "7.5 kHz off and still heard clearly: " + before);
		radio.setTuneStepHz(12_500);
		helper.assertTrue(Math.abs(radio.getFrequency() - 164_480_000.0F) < 1.0F, "Changing the step moved the frequency: " + radio.getFrequency());
		radio.updateFrequency(1);
		helper.assertTrue(Math.abs(radio.getFrequency() - SATELLITE) < 50.0F, "One 12.5 kHz click up from 164.480 is not 164.4875: " + radio.getFrequency());
		Detuning.Effect after = radio.getDetuning(SATELLITE);
		helper.assertTrue(after.audible() && after.strength() > 0.95D, "Tuned to 164.4875 and the satellite is not clear: " + after);
		radio.setTuneStepHz(20_000);
		radio.updateFrequency(-1);
		helper.assertTrue(Math.abs(radio.getFrequency() - 164_480_000.0F) < 50.0F, "Back on 20 kHz one click down is not 164.480: " + radio.getFrequency());
		CompoundTag saved = radio.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(saved.getInt("tuneStep") == 20_000, "The step is not saved: " + saved.getInt("tuneStep"));
		radio.setTuneStepHz(7_000);
		helper.assertTrue(radio.getTuneStepHz() == 0, "An odd step was taken: " + radio.getTuneStepHz());
		helper.succeed();
	}

	/**
	 * A radio on 164.4875 with a 6.25 kHz step comes back on 164.4875 when it is saved and loaded (2026-11-14, the
	 * maintainer: it came back as 164.4812, the frequency snapped on the server's 20 kHz grid before its own step was
	 * read, then again on 6.25 kHz).
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void aFineStepFrequencySurvivesALoad(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 2, 2), other = new BlockPos(4, 2, 2);
		helper.setBlock(at, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(other, RadiocraftBlocks.VHF_BASE_STATION.get());
		VHFRadioBlockEntity radio = GameTestUtil.blockEntity(helper, at, VHFRadioBlockEntity.class);
		radio.setTuneStepHz(6_250);
		radio.setFrequency(164_480_000.0F);
		radio.updateFrequency(1);
		radio.updateFrequency(1);
		helper.assertTrue(Math.abs(radio.getFrequency() - SATELLITE) < 50.0F, "Two 6.25 kHz clicks up from 164.480 are not 164.4875: " + radio.getFrequency());
		CompoundTag saved = radio.saveWithoutMetadata(helper.getLevel().registryAccess());
		VHFRadioBlockEntity copy = GameTestUtil.blockEntity(helper, other, VHFRadioBlockEntity.class);
		copy.loadWithComponents(saved, helper.getLevel().registryAccess());
		helper.assertTrue(Math.abs(copy.getFrequency() - SATELLITE) < 50.0F, "Saved on 164.4875, loaded on " + copy.getFrequency());
		helper.assertTrue(copy.getTuneStepHz() == 6_250, "The step came back as " + copy.getTuneStepHz());
		helper.succeed();
	}
}
