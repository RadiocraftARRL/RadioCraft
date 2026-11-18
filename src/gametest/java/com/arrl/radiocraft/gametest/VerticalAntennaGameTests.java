package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.antenna.AntennaTypes;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.blocks.antennas.AntennaPoleBlock;
import com.arrl.radiocraft.common.blocks.antennas.VerticalAntennaBaseBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftTags;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.types.VerticalAntennaType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Vertical Antenna Base and antenna poles (maintainer, 2026-11-18): poles on a base are a vertical tuned by the
 * coil's tap, they taper on a base and only there; a 1:1 balun with poles on it is no longer a vertical; a pole holds
 * no antenna wire but an isolator or a balun stands on top of it; coax beside a pole clings to it.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class VerticalAntennaGameTests {

	private static void stack(GameTestHelper helper, BlockPos base, int poles) {
		for(int i = 1; i <= poles; i++)
			helper.setBlock(base.above(i), RadiocraftBlocks.ANTENNA_POLE.get());
	}

	/** The SWR by tap and pole count, off band, and the poles' match. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void tapAndPolesSetTheBand(GameTestHelper helper) {
		helper.assertTrue(VerticalAntennaType.swr(5, 20, 14_200_000.0F) == VerticalAntennaType.MATCHED_SWR, "5 poles on 20 m do not match");
		helper.assertTrue(VerticalAntennaType.swr(4, 20, 14_200_000.0F) == VerticalAntennaType.ONE_OFF_SWR, "one pole short is not about 2.5:1");
		helper.assertTrue(VerticalAntennaType.swr(6, 40, 7_150_000.0F) == VerticalAntennaType.MATCHED_SWR, "6 poles tapped for 40 m do not match");
		helper.assertTrue(VerticalAntennaType.swr(7, 80, 3_800_000.0F) == VerticalAntennaType.MATCHED_SWR, "7 poles tapped for 80 m do not match");
		helper.assertTrue(VerticalAntennaType.swr(3, 10, 28_400_000.0F) == VerticalAntennaType.MATCHED_SWR, "3 poles on 10 m do not match");
		helper.assertTrue(VerticalAntennaType.swr(5, 20, 7_150_000.0F) == 10.0D, "a 20 m tap matched on 40 m");
		helper.assertTrue(VerticalAntennaType.swr(0, 20, 14_200_000.0F) == 10.0D, "a bare base matched");

		BlockPos base = new BlockPos(3, 1, 3);
		helper.setBlock(base, RadiocraftBlocks.VERTICAL_ANTENNA_BASE.get().defaultBlockState().setValue(VerticalAntennaBaseBlock.TAP, VerticalAntennaBaseBlock.Tap.B40));
		stack(helper, base, 6);
		StaticAntenna<?> antenna = AntennaTypes.match(helper.getLevel(), helper.absolutePos(base));
		helper.assertTrue(antenna != null && antenna.type instanceof VerticalAntennaType, "the base with poles is not a vertical");
		helper.assertTrue(antenna.getSWR(7_150_000.0F) == VerticalAntennaType.MATCHED_SWR, "the built 40 m vertical reads " + antenna.getSWR(7_150_000.0F));
		helper.succeed();
	}

	/** Poles on a base taper by height; poles elsewhere do not; a balun with poles is no vertical. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void polesTaperOnlyOnABase(GameTestHelper helper) {
		BlockPos base = new BlockPos(2, 1, 2), balun = new BlockPos(8, 1, 2), ground = new BlockPos(12, 1, 2);
		helper.setBlock(base, RadiocraftBlocks.VERTICAL_ANTENNA_BASE.get());
		helper.setBlock(balun, RadiocraftBlocks.BALUN_ONE_TO_ONE.get());
		stack(helper, base, 5);
		stack(helper, balun, 5);
		helper.setBlock(ground.below(), net.minecraft.world.level.block.Blocks.STONE);
		stack(helper, ground.below(), 2);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					for(int i = 1; i <= 5; i++) {
						helper.assertBlockProperty(base.above(i), AntennaPoleBlock.ON_BASE, true);
						helper.assertBlockProperty(base.above(i), AntennaPoleBlock.DISTANCE, i - 1);
						helper.assertBlockProperty(balun.above(i), AntennaPoleBlock.ON_BASE, false);
					}
					helper.assertBlockProperty(ground, AntennaPoleBlock.ON_BASE, false);
					StaticAntenna<?> old = AntennaTypes.match(helper.getLevel(), helper.absolutePos(balun));
					helper.assertTrue(old == null || !(old.type instanceof VerticalAntennaType) && old.getSWR(14_200_000.0F) >= 10.0D,
							"a 1:1 balun with poles is still a matched vertical: " + (old == null ? null : old.type));
				})
				.thenSucceed();
	}

	/** A pole holds no antenna wire; an isolator or a balun on top of one stays; coax beside a pole clings to it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void polesCarryIsolatorsAndCoax(GameTestHelper helper) {
		BlockPos pole = new BlockPos(3, 1, 3), iso = pole.above(), polePos2 = new BlockPos(6, 1, 3), balun = polePos2.above();
		helper.setBlock(pole, RadiocraftBlocks.ANTENNA_POLE.get());
		helper.setBlock(iso, RadiocraftBlocks.ANTENNA_CONNECTOR.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.antennas.AntennaConnectorBlock.PLACED_ON, Direction.UP));
		helper.setBlock(polePos2, RadiocraftBlocks.ANTENNA_POLE.get());
		helper.setBlock(balun, RadiocraftBlocks.BALUN_ONE_TO_ONE.get().defaultBlockState()
				.setValue(com.arrl.radiocraft.common.blocks.antennas.BalunBlock.PLACED_ON, Direction.UP));
		helper.assertFalse(helper.getBlockState(pole).is(RadiocraftTags.Blocks.ANTENNA_WIRE_HOLDERS), "a pole still holds antenna wire");
		helper.assertTrue(WireBlock.beamReach(helper.getLevel(), helper.absolutePos(pole)) > 0, "coax beside a pole does not cling to it");
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					helper.assertBlockPresent(RadiocraftBlocks.ANTENNA_CONNECTOR.get(), iso);
					helper.assertBlockPresent(RadiocraftBlocks.BALUN_ONE_TO_ONE.get(), balun);
					helper.assertTrue(helper.getBlockState(iso).is(RadiocraftTags.Blocks.ANTENNA_WIRE_HOLDERS), "an isolator is not a wire holder");
				})
				.thenSucceed();
	}
}
