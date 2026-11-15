package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.TapePlayerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.datacomponents.TapeContents;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.radio.tape.TapeRecordings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The tape player for an FM station (2026-11-14): hoppers feed its queue and empty its out tray, QUEUE mode plays the
 * tapes in turn, a station's pulls play it a frame at a time, and it does not key an FM transmitter on its wire.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class TapePlayerHopperGameTests {

	/** A recorded tape of {@code seconds} of tone. */
	private static ItemStack tape(GameTestHelper helper, double seconds) {
		UUID id = UUID.randomUUID();
		short[] audio = new short[(int)(48000 * seconds)];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(8000 * Math.sin(2 * Math.PI * 600 * i / 48000.0));
		TapeRecordings.save(helper.getLevel().getServer(), id, audio);
		ItemStack tape = new ItemStack(RadiocraftItems.TAPE_REEL.get());
		tape.set(RadiocraftDataComponent.TAPE_CONTENTS.get(), TapeContents.BLANK.withRecording(id, Math.max(1, (int)Math.round(seconds))));
		return tape;
	}

	private static TapePlayerBlockEntity player(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, TapePlayerBlockEntity.class);
	}

	private static int count(HopperBlockEntity hopper) {
		int n = 0;
		for(int i = 0; i < hopper.getContainerSize(); i++)
			n += hopper.getItem(i).getCount();
		return n;
	}

	/** A hopper above fills the queue; QUEUE mode plays both tapes in turn; a hopper below takes them away played. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400)
	public static void hoppersFeedTheQueueAndTakeThePlayedTapes(GameTestHelper helper) {
		BlockPos pos = new BlockPos(4, 3, 4), above = pos.above(), below = pos.below();
		helper.setBlock(pos, RadiocraftBlocks.TAPE_PLAYER.get());
		helper.setBlock(above, Blocks.HOPPER);
		helper.setBlock(below, Blocks.HOPPER);
		helper.setBlock(below.below(), Blocks.STONE);
		player(helper, pos).setMode(TapePlayerBlockEntity.Mode.QUEUE);
		HopperBlockEntity top = GameTestUtil.blockEntity(helper, above, HopperBlockEntity.class);
		top.setItem(0, tape(helper, 0.5D));
		top.setItem(1, tape(helper, 0.5D));
		HopperBlockEntity bottom = GameTestUtil.blockEntity(helper, below, HopperBlockEntity.class);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(count(top) == 0, "the hopper above still holds " + count(top)))
				.thenWaitUntil(() -> helper.assertTrue(count(bottom) == 2, "the hopper below has " + count(bottom) + " played tapes"))
				.thenExecute(() -> {
					TapePlayerBlockEntity p = player(helper, pos);
					helper.assertTrue(p.getQueue().isEmpty() && p.getOut().isEmpty() && p.getTape().isEmpty(),
							"left in it: queue " + p.getQueue().size() + ", out " + p.getOut().size() + ", tape " + p.getTape());
				})
				.thenSucceed();
	}

	/** The queue fills to nine and no more, and a comparator reads it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void theQueueHoldsNine(GameTestHelper helper) {
		BlockPos pos = new BlockPos(4, 2, 4);
		helper.setBlock(pos, RadiocraftBlocks.TAPE_PLAYER.get());
		TapePlayerBlockEntity p = player(helper, pos);
		helper.assertTrue(p.comparatorSignal() == 0, "an empty queue reads " + p.comparatorSignal());
		for(int i = 0; i < TapePlayerBlockEntity.QUEUE_SIZE; i++)
			helper.assertTrue(p.enqueue(tape(helper, 0.5D)), "tape " + (i + 1) + " would not queue");
		helper.assertFalse(p.enqueue(tape(helper, 0.5D)), "a tenth tape queued");
		helper.assertTrue(p.comparatorSignal() == 15, "a full queue reads " + p.comparatorSignal());
		helper.assertTrue(p.itemHandler(net.minecraft.core.Direction.UP).insertItem(0, tape(helper, 0.5D), true).getCount() == 1, "a full queue took a hopper's tape");
		helper.succeed();
	}

	/** Pulled by a station, it plays only as fast as it is pulled, a frame a pull; its own tick sends nothing. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void aStationsPullsPlayIt(GameTestHelper helper) {
		BlockPos pos = new BlockPos(4, 2, 4);
		helper.setBlock(pos, RadiocraftBlocks.TAPE_PLAYER.get());
		TapePlayerBlockEntity p = player(helper, pos);
		p.insert(tape(helper, 3.0D));
		int[] frames = { 0 };
		helper.startSequence()
				.thenExecute(() -> {
					p.markPulled(helper.getLevel().getGameTime());
					p.play();
				})
				.thenWaitUntil(() -> { // Loaded and the first frame out.
					if(p.nextFrame() != null)
						frames[0]++;
					helper.assertTrue(frames[0] > 0, "no frame yet");
				})
				.thenExecuteFor(30, () -> p.markPulled(helper.getLevel().getGameTime())) // Pulled but no frames asked for.
				.thenExecute(() -> {
					helper.assertTrue(p.getState() == TapePlayerBlockEntity.STATE_PLAYING, "stopped while pulled");
					helper.assertTrue(p.getSeconds() == 0, "it played on by itself: " + p.getSeconds() + " s");
					helper.assertTrue(p.live() && !p.atBreak(), "playing but not live, or at a break");
					for(int i = 0; i < 100; i++)
						helper.assertTrue(p.nextFrame() != null, "frame " + i + " missing");
					helper.assertTrue(p.getSeconds() == 2, "a hundred more pulls is " + p.getSeconds() + " s in");
				})
				.thenSucceed();
	}

	/** It keys a radio touching it, as ever, but not an FM transmitter touching it: a station pulls its program. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200)
	public static void itDoesNotKeyAnFmTransmitter(GameTestHelper helper) {
		BlockPos pos = new BlockPos(4, 2, 4), fm = pos.east(), vhf = pos.west();
		helper.setBlock(pos, RadiocraftBlocks.TAPE_PLAYER.get());
		helper.setBlock(fm, RadiocraftBlocks.FM_BROADCAST_TRANSMITTER.get());
		helper.setBlock(fm.above(), RadiocraftBlocks.FM_BROADCAST_TRANSMITTER_TOP.get());
		helper.setBlock(vhf, RadiocraftBlocks.VHF_BASE_STATION.get());
		TapePlayerBlockEntity p = player(helper, pos);
		p.insert(tape(helper, 3.0D));
		helper.startSequence()
				.thenExecute(p::play)
				.thenWaitUntil(() -> helper.assertTrue(p.getState() == TapePlayerBlockEntity.STATE_PLAYING, "not playing"))
				.thenExecute(() -> {
					helper.assertTrue(GameTestUtil.blockEntity(helper, vhf, RadioBlockEntity.class).isPTTDown(), "the VHF radio beside it was not keyed");
					helper.assertFalse(GameTestUtil.blockEntity(helper, fm, RadioBlockEntity.class).isPTTDown(), "the FM transmitter was keyed");
				})
				.thenSucceed();
	}
}
