package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.voice.ReceiverMix;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * A radio with nothing on its coax (maintainer, 2026-11-13): it hisses faintly (its own front end), the AGC lifts that
 * a little, the S-meter barely moves, and it hears a transmitter a few blocks away through its bare connector.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class BareRadioGameTests {

	private static final String BATCH = "bare_radio";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-bare-radio".getBytes());

	private static RadioBlockEntity radio(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class);
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		if(!GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class).isPowered)
			radio(helper, pos).toggle();
	}

	private static short[] tone() {
		short[] audio = new short[960];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return audio;
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void bareRadioHissesFaintly(GameTestHelper helper) {
		BlockPos pos = new BlockPos(4, 2, 4);
		helper.setBlock(pos, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.startSequence()
				.thenExecute(() -> {
					helper.assertTrue(radio(helper, pos).getStaticVolume() == 0.0D, "Off, yet it hisses: " + radio(helper, pos).getStaticVolume());
					powerOn(helper, pos);
				})
				.thenWaitUntil(() -> {
					RadioBlockEntity r = radio(helper, pos);
					helper.assertTrue(r.wasPowered() && r.getBareConnector() != null, "Not on its bare connector yet");
					helper.assertTrue(r.getMeterNoise() > 0.0D, "The S-meter reads nothing at all");
				})
				.thenExecute(() -> {
					RadioBlockEntity r = radio(helper, pos);
					double share = r.getStaticVolume();
					helper.assertTrue(share == RadioBlockEntity.BARE_STATIC, "Share " + share);
					helper.assertTrue(r.getMeterNoise() < 0.05D, "The S-meter reads " + r.getMeterNoise() + " with no antenna");
					r.setAgc(RadioBlockEntity.AGC_FAST);
					double lifted = r.getStaticVolumeHeard();
					helper.assertTrue(lifted > share && lifted <= ReceiverMix.AGC_LIFT_CEILING, "AGC on: heard " + lifted);
					r.setAgc(RadioBlockEntity.AGC_OFF);
					helper.assertTrue(r.getStaticVolumeHeard() == share, "AGC off: heard " + r.getStaticVolumeHeard());
				})
				.thenSucceed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void bareRadioHearsOnlyNearby(GameTestHelper helper) {
		BlockPos talker = new BlockPos(2, 2, 3), near = new BlockPos(6, 2, 3), far = new BlockPos(15, 2, 3);
		for(BlockPos pos : List.of(talker, near, far))
			helper.setBlock(pos, RadiocraftBlocks.VHF_BASE_STATION.get());
		helper.startSequence()
				.thenExecute(() -> {
					for(BlockPos pos : List.of(talker, near, far)) {
						radio(helper, pos).setFrequency(146_820_000.0F);
						powerOn(helper, pos);
					}
				})
				.thenWaitUntil(() -> helper.assertTrue(radio(helper, talker).getBareConnector() != null && radio(helper, near).getBareConnector() != null
						&& radio(helper, far).getBareConnector() != null, "No bare connectors yet"))
				.thenExecute(() -> {
					RadioBlockEntity speaker = radio(helper, talker);
					speaker.setPTTDown(true);
					speaker.acceptVoicePacket(() -> helper.getLevel(), tone(), OPERATOR);
					speaker.setPTTDown(false);
					long now = helper.getLevel().getGameTime();
					helper.assertTrue(radio(helper, near).getLastSignalGameTime() == now, "Not heard 4 blocks away");
					helper.assertFalse(radio(helper, far).getLastSignalGameTime() == now, "Heard 13 blocks away");
				})
				.thenSucceed();
	}
}
