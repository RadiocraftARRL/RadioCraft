package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The premium HF rigs' extra receive controls (2026-11-11), on every HF radio: RIT moves the receiver only (CW and
 * data still go out on the dial), the noise blanker cuts impulse noise, RF gain turns the front end down, and all of it
 * survives a save.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class PremiumRigGameTests {

	@GameTest(template = GameTestUtil.EMPTY)
	public static void ritMovesOnlyTheReceiver(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 1, 2);
		helper.setBlock(at, RadiocraftBlocks.HF_RADIO_20M_VARIANTS.pure().get());
		HFRadioBlockEntity radio = GameTestUtil.blockEntity(helper, at, HFRadioBlockEntity.class);
		float tx = radio.getSimplexTransmitHz();
		helper.assertTrue(Math.abs(radio.getTunedFrequency() - tx) < 0.5F, "Off the dial with the RIT out");
		radio.setRit(10, false);
		helper.assertTrue(Math.abs(radio.getTunedFrequency() - tx) < 0.5F, "The RIT moved the receiver while switched out");
		radio.setRit(10, true);
		helper.assertTrue(Math.abs(radio.getTunedFrequency() - tx - 10 * HFRadioBlockEntity.RIT_STEP_HZ) < 0.5F,
				"The RIT did not move the receiver: " + (radio.getTunedFrequency() - tx));
		helper.assertTrue(Math.abs(radio.getSimplexTransmitHz() - tx) < 0.5F, "The RIT moved the transmitter");
		helper.assertTrue(Math.abs(radio.getTransmitFrequency() - radio.getFrequency()) < 0.5F, "The RIT moved the voice transmitter");
		radio.setRit(99, true);
		helper.assertTrue(radio.getRitSteps() == HFRadioBlockEntity.MAX_RIT_STEPS, "The RIT went past its end");

		helper.assertTrue(radio.getImpulseNoiseFactor() == 1.0D, "Noise blanker on from the box");
		radio.setNoiseBlanker(true);
		helper.assertTrue(radio.getImpulseNoiseFactor() < 0.5D, "The noise blanker cut nothing");
		helper.assertTrue(radio.getReceiverGain() == 1.0D, "RF gain not full from the box");
		radio.setRfGain(0);
		helper.assertTrue(radio.getReceiverGain() < 0.1D, "RF gain at 0 still full");

		net.minecraft.nbt.CompoundTag saved = radio.saveWithoutMetadata(helper.getLevel().registryAccess());
		radio.setRit(0, false);
		radio.setNoiseBlanker(false);
		radio.setRfGain(HFRadioBlockEntity.MAX_RF_GAIN);
		radio.loadWithComponents(saved, helper.getLevel().registryAccess());
		helper.assertTrue(radio.isRitOn() && radio.getRitSteps() == HFRadioBlockEntity.MAX_RIT_STEPS && radio.isNoiseBlanker() && radio.getRfGain() == 0,
				"Not kept through a save");
		helper.succeed();
	}
}
