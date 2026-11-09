package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.voice.Modulation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The new HF panels' modes and switches (2026-11-09): AM on every HF radio, the AGC, the squelch knob. */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class HfModeGameTests {

	private static final BlockPos A = new BlockPos(1, 1, 1), B = new BlockPos(3, 1, 1);

	/** AM, SSB and CW are one choice on an HF radio; on the all-band radio FM joins them. AGC clamps to its three settings. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void amSsbCwAndAgcOnHfRadios(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.HF_RADIO_20M.get());
		helper.setBlock(B, RadiocraftBlocks.ALL_BAND_RADIO.get());
		helper.runAfterDelay(2, () -> {
			HFRadioBlockEntity radio = GameTestUtil.blockEntity(helper, A, HFRadioBlockEntity.class);
			helper.assertTrue(radio.hasSquelchKnob(), "An HF radio has a squelch knob");
			radio.setAMEnabled(true);
			helper.assertTrue(radio.isAM() && radio.getSSBEnabled() && !radio.isSSBSelected() && radio.getModulation() == Modulation.AM, "AM selected");
			radio.setCWEnabled(true);
			helper.assertFalse(radio.isAM(), "CW deselects AM");
			radio.setAMEnabled(true);
			helper.assertFalse(radio.getCWEnabled(), "AM deselects CW");
			radio.setSSBEnabled(true);
			helper.assertTrue(radio.isSSBSelected() && !radio.isAM() && radio.getModulation() == Modulation.SSB, "SSB deselects AM");
			radio.setAgc(7);
			helper.assertTrue(radio.getAgc() == RadioBlockEntity.AGC_FAST, "AGC clamps to FAST");
			radio.setAgc(RadioBlockEntity.AGC_OFF);
			helper.assertTrue(radio.getAgc() == RadioBlockEntity.AGC_OFF, "AGC OFF");

			HFRadioAllBandBlockEntity allBand = GameTestUtil.blockEntity(helper, B, HFRadioAllBandBlockEntity.class);
			allBand.setFMEnabled(true);
			allBand.setAMEnabled(true);
			helper.assertTrue(allBand.isAM() && !allBand.isFM(), "AM deselects FM on the all-band radio");
			allBand.setFMEnabled(true);
			helper.assertTrue(allBand.isFM() && !allBand.isAM(), "FM deselects AM on the all-band radio");
			helper.succeed();
		});
	}
}
