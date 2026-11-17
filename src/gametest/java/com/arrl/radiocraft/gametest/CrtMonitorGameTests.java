package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.CrtMonitorBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.CrtMonitorBlock;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.network.clientbound.CCrtWaterfallPacket;
import com.arrl.radiocraft.common.radio.BandScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The External CRT Monitor on a radio (2026-11-12): a lone monitor touching a premium radio shows its waterfall
 * (not on a plain or cheap radio, not in a wall of monitors, and not when a WXSAT computer links to it: the computer
 * wins); right-click cycles NARROW, WIDE, OFF; the waterfall reads the radio's own monitor band scope, a few kHz wide.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class CrtMonitorGameTests {

	private static void monitor(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, RadiocraftBlocks.EXTERNAL_CRT_MONITOR.get().defaultBlockState().setValue(CrtMonitorBlock.FACING, Direction.SOUTH));
	}

	private static CrtMonitorBlockEntity monitorAt(GameTestHelper helper, BlockPos at) {
		return GameTestUtil.blockEntity(helper, at, CrtMonitorBlockEntity.class);
	}

	/** Only a premium radio (Pure crystal) gets a waterfall; plain and cheap ones do not. Right-click: NARROW, WIDE, OFF. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 120)
	public static void radioModeOnlyOnPremiumRadios(GameTestHelper helper) {
		BlockPos plain = new BlockPos(2, 2, 2), premium = new BlockPos(6, 2, 2), cheap = new BlockPos(10, 2, 2), hf = new BlockPos(2, 2, 8);
		helper.setBlock(plain, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.setBlock(premium, RadiocraftBlocks.VHF_BASE_STATION_VARIANTS.pure().get());
		helper.setBlock(cheap, RadiocraftBlocks.VHF_BASE_STATION_VARIANTS.poor().get());
		helper.setBlock(hf, RadiocraftBlocks.HF_RADIO_20M_VARIANTS.pure().get());
		for(BlockPos radio : new BlockPos[] { plain, premium, cheap, hf })
			monitor(helper, radio.east());
		helper.startSequence()
				.thenWaitUntil(() -> {
					helper.assertTrue(monitorAt(helper, premium.east()).radioMode(), "no waterfall on the premium VHF base station");
					helper.assertTrue(monitorAt(helper, hf.east()).radioMode(), "no waterfall on the premium 20m rig");
				})
				.thenIdle(25) // A full round for the others.
				.thenExecute(() -> {
					helper.assertFalse(monitorAt(helper, plain.east()).radioMode(), "a waterfall on a plain radio");
					helper.assertFalse(monitorAt(helper, cheap.east()).radioMode(), "a waterfall on a cheap radio");
					CrtMonitorBlockEntity m = monitorAt(helper, premium.east());
					helper.assertTrue(m.radioPos().equals(helper.absolutePos(premium)), "linked to " + m.radioPos());
					helper.assertTrue(m.span() == CrtMonitorBlockEntity.Span.NARROW, "starts NARROW: " + m.span());
					helper.assertTrue("wide".equals(m.cycle()), "right-click goes to WIDE");
					helper.assertTrue("off".equals(m.cycle()), "then OFF");
					helper.assertTrue("narrow".equals(m.cycle()), "then NARROW again");
					helper.assertTrue(m.mode() == CrtMonitorBlockEntity.Mode.LIVE, "the computer mode was touched");
				})
				.thenSucceed();
	}

	/** A monitor touching both a premium radio and a computer with WXSAT shows the computer's mosaic. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void aComputerWinsOverARadio(GameTestHelper helper) {
		BlockPos radio = new BlockPos(4, 2, 4), at = radio.east(), computerAt = at.east();
		helper.setBlock(radio, RadiocraftBlocks.VHF_BASE_STATION_VARIANTS.pure().get());
		monitor(helper, at);
		helper.setBlock(computerAt, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(monitorAt(helper, at).radioMode(), "without WXSAT the radio's waterfall shows"))
				.thenExecute(() -> GameTestUtil.blockEntity(helper, computerAt, RadioComputerBlockEntity.class).install(ComputerApps.WXSAT))
				.thenWaitUntil(() -> {
					CrtMonitorBlockEntity m = monitorAt(helper, at);
					helper.assertFalse(m.radioMode(), "still on the radio with a WXSAT computer beside it");
					helper.assertTrue(m.findComputer() != null, "not linked to the computer");
					helper.assertTrue("loop".equals(m.cycle()), "right-click cycles the computer modes");
				})
				.thenSucceed();
	}

	/** A monitor that is part of a wall shows no radio; broken back to one, it does. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 120)
	public static void aWallShowsNoRadio(GameTestHelper helper) {
		BlockPos radio = new BlockPos(4, 2, 4), at = radio.east(), above = at.above();
		helper.setBlock(radio, RadiocraftBlocks.HF_RADIO_20M_VARIANTS.pure().get());
		monitor(helper, at);
		monitor(helper, above);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(monitorAt(helper, at).groupH() == 2, "the two did not join"))
				.thenIdle(25)
				.thenExecute(() -> {
					helper.assertFalse(monitorAt(helper, at).radioMode(), "a monitor in a wall shows the radio");
					helper.assertFalse(monitorAt(helper, above).radioMode(), "the top one shows the radio");
				})
				.thenExecute(() -> helper.setBlock(above, Blocks.AIR))
				.thenWaitUntil(() -> helper.assertTrue(monitorAt(helper, at).radioMode(), "alone again, it does not show the radio"))
				.thenSucceed();
	}

	/**
	 * The waterfall reads the radio's monitor band scope (not rig control's) round the dial: 50 kHz NARROW and 200 kHz
	 * WIDE on VHF, 6 kHz on HF; with the span OFF or the radio off, nothing, and the scope is let go.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 120)
	public static void theWaterfallWatchesRoundTheDial(GameTestHelper helper) {
		BlockPos vhf = new BlockPos(4, 2, 4), hf = new BlockPos(10, 2, 4);
		helper.setBlock(vhf, RadiocraftBlocks.VHF_BASE_STATION_VARIANTS.pure().get());
		helper.setBlock(hf, RadiocraftBlocks.HF_RADIO_20M_VARIANTS.pure().get());
		monitor(helper, vhf.east());
		monitor(helper, hf.east());
		helper.startSequence()
				.thenExecute(() -> {
					on(helper, vhf);
					on(helper, hf);
				})
				.thenWaitUntil(() -> helper.assertTrue(monitorAt(helper, vhf.east()).radioMode() && monitorAt(helper, hf.east()).radioMode(), "not on the radios"))
				.thenExecute(() -> {
					long now = helper.getLevel().getGameTime();
					RadioBlockEntity radio = GameTestUtil.blockEntity(helper, vhf, RadioBlockEntity.class);
					CrtMonitorBlockEntity m = monitorAt(helper, vhf.east());
					CCrtWaterfallPacket row = m.sample(radio, now);
					helper.assertTrue(row.on() && row.row().length == BandScope.BINS, "no row from a radio that is on: " + row.on());
					helper.assertTrue(Math.abs(row.highHz() - row.lowHz() - 50_000.0D) < 1.0D, "VHF NARROW is " + (row.highHz() - row.lowHz()) + " Hz");
					helper.assertTrue(row.lowHz() <= row.dialHz() && row.dialHz() <= row.highHz(), "the dial is not in the span");
					helper.assertTrue(radio.hasMonitorScope(), "no monitor scope on the radio");
					helper.assertFalse(radio.hasScope(), "it took rig control's scope");
					m.cycleSpan();
					CCrtWaterfallPacket wide = m.sample(radio, now);
					helper.assertTrue(Math.abs(wide.highHz() - wide.lowHz() - 200_000.0D) < 1.0D, "VHF WIDE is " + (wide.highHz() - wide.lowHz()) + " Hz");
					m.cycleSpan();
					helper.assertFalse(m.sample(radio, now).on(), "OFF still shows");
					helper.assertFalse(radio.hasMonitorScope(), "OFF kept the scope");

					RadioBlockEntity rig = GameTestUtil.blockEntity(helper, hf, RadioBlockEntity.class);
					CCrtWaterfallPacket hfRow = monitorAt(helper, hf.east()).sample(rig, now);
					helper.assertTrue(hfRow.on() && Math.abs(hfRow.highHz() - hfRow.lowHz() - 6_000.0D) < 1.0D, "HF NARROW is " + (hfRow.highHz() - hfRow.lowHz()) + " Hz");
					rig.toggle(); // Off.
				})
				.thenWaitUntil(() -> {
					RadioBlockEntity rig = GameTestUtil.blockEntity(helper, hf, RadioBlockEntity.class);
					helper.assertFalse(monitorAt(helper, hf.east()).sample(rig, helper.getLevel().getGameTime()).on(), "a radio switched off still shows");
					helper.assertFalse(rig.hasMonitorScope(), "a radio switched off kept the scope");
				})
				.thenSucceed();
	}

	private static void on(GameTestHelper helper, BlockPos at) {
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, at, RadioBlockEntity.class);
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, at, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			radio.toggle();
		helper.assertTrue(networkObject.isPowered, "the radio at " + at.toShortString() + " did not power on");
	}
	/** The station's cross on a monitor's mosaic (2026-11-16): red at the station, arms two pixels out, nothing off the picture. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void theStationIsCrossedOnTheMosaic(GameTestHelper helper) {
		int w = 128, h = 128;
		byte[] pixels = new byte[w * h];
		CrtMonitorBlockEntity.cross(pixels, w, h, 40.5D, 60.5D);
		for(int[] p : new int[][] { { 40, 60 }, { 42, 60 }, { 38, 60 }, { 40, 62 }, { 40, 58 } })
			helper.assertTrue(pixels[p[0] + p[1] * w] == CrtMonitorBlockEntity.CROSS, "no red at " + p[0] + "," + p[1]);
		helper.assertTrue(pixels[41 + 61 * w] != CrtMonitorBlockEntity.CROSS, "red off the arms");
		helper.assertTrue(pixels[50 + 70 * w] == 0, "painted far from the cross");
		byte[] off = new byte[w * h];
		CrtMonitorBlockEntity.cross(off, w, h, -20.0D, 60.0D);
		for(byte b : off)
			helper.assertTrue(b == 0, "a cross for a station off the picture");
		helper.succeed();
	}
	/** The loop leaves out passes that showed the screen's ground nothing, and repeats; all blank, one blank frame. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void theLoopSkipsBlankAndRepeatedPasses(GameTestHelper helper) {
		byte[] blank = new byte[16], a = new byte[16], b = new byte[16];
		a[3] = 5;
		b[3] = 5;
		b[7] = 9;
		java.util.List<byte[]> kept = CrtMonitorBlockEntity.loopFrames(java.util.List.of(blank, a, a.clone(), b));
		helper.assertTrue(kept.size() == 2 && kept.get(0)[3] == 5 && kept.get(1)[7] == 9, "kept " + kept.size() + " frames");
		java.util.List<byte[]> none = CrtMonitorBlockEntity.loopFrames(java.util.List.of(blank, blank.clone()));
		helper.assertTrue(none.size() == 1, "all blank: " + none.size() + " frames");
		helper.succeed();
	}
}
