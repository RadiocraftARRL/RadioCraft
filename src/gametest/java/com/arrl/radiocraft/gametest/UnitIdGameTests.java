package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialContact;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.common.radio.voice.DigitalVoice;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Unit IDs on the air (1.12 playtest fixes 2): every key-up carries the sending radio's unit ID, an MDC burst on analog
 * and in the data on digital; a receiving mobile reads it (even on an encrypted call without the key), and its contact
 * list names it. Mobiles over bare connectors, on their own commercial channels (see CommercialRadioGameTests).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class UnitIdGameTests {

	private static final String BATCH = "commercial_radios";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-unit-id".getBytes());
	private static final BlockPos A = new BlockPos(4, 2, 4), B = new BlockPos(6, 2, 4), C = new BlockPos(4, 2, 6);

	private static short[] tone() {
		short[] audio = new short[960];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return audio;
	}

	private static CommercialMobileBlockEntity mobile(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, CommercialMobileBlockEntity.class);
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	/** Programs the mobile at {@code pos} with {@code spec} on channel 1, unit {@code unit} and {@code contacts}. */
	private static void program(GameTestHelper helper, BlockPos pos, String spec, int unit, List<CommercialContact> contacts) {
		CommercialMobileBlockEntity radio = mobile(helper, pos);
		CommercialProgramming.Result result = CommercialProgramming.write(radio, List.of(CommercialChannel.parse(spec)), unit, contacts);
		helper.assertTrue(result.ok(), "Programming " + spec + " failed: " + result.problems());
		CommercialProgramming.select(radio, 0);
	}

	private static void speak(GameTestHelper helper, BlockPos pos) {
		RadioBlockEntity radio = GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class);
		radio.setPTTDown(true);
		helper.assertTrue(radio.canTransmitVoice(), "Radio at " + pos.toShortString() + " cannot transmit");
		radio.acceptVoicePacket(() -> helper.getLevel(), tone(), OPERATOR);
		radio.setPTTDown(false);
	}

	private static boolean bare(GameTestHelper helper, BlockPos... radios) {
		for(BlockPos pos : radios)
			if(GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).getBareConnector() == null)
				return false;
		return true;
	}

	/** An analog key-up carries unit 1205; the receiving mobile reads it and its contact list calls it ENG 5. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void analogUnitIdIsRead(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(B, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					program(helper, A, "FIRE 1|155.1625", 1205, null);
					program(helper, B, "FIRE 1|155.1625", 1300, List.of(new CommercialContact(1205, "ENG 5")));
					powerOn(helper, A);
					powerOn(helper, B);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B), "No bare connectors yet"))
				.thenExecute(() -> {
					helper.assertTrue(mobile(helper, B).getLastCaller() == 0, "B read a caller before anyone talked");
					speak(helper, A);
					helper.assertTrue(mobile(helper, B).getLastReception() == DigitalVoice.Reception.ANALOG, "Analog, was " + mobile(helper, B).getLastReception());
					helper.assertTrue(mobile(helper, B).getLastCaller() == 1205, "B read unit " + mobile(helper, B).getLastCaller());
					helper.assertTrue("ENG 5".equals(mobile(helper, B).program().callerText(1205)), "the contact list does not name 1205");
				})
				.thenSucceed();
	}

	/** On an encrypted digital call a radio without the key hears it scrambled but still shows who is talking. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void encryptedCallsStillShowTheUnit(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(B, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.setBlock(C, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					program(helper, A, "SWAT|155.1875|=|DIG|0|100|H", 2001, null);
					program(helper, B, "SWAT|155.1875|=|DIG|0|100|H", 2002, null);
					program(helper, C, "OTHER|155.1875|=|DIG|0|200", 2003, null);
					powerOn(helper, A);
					powerOn(helper, B);
					powerOn(helper, C);
				})
				.thenWaitUntil(() -> helper.assertTrue(bare(helper, A, B, C), "No bare connectors yet"))
				.thenExecute(() -> {
					speak(helper, A);
					helper.assertTrue(mobile(helper, B).getLastReception() == DigitalVoice.Reception.CLEAR, "B with the key: " + mobile(helper, B).getLastReception());
					helper.assertTrue(mobile(helper, B).getLastCaller() == 2001, "B read unit " + mobile(helper, B).getLastCaller());
					helper.assertTrue(mobile(helper, C).getLastReception() == DigitalVoice.Reception.SCRAMBLED, "C without the key: " + mobile(helper, C).getLastReception());
					helper.assertTrue(mobile(helper, C).getLastCaller() == 2001, "C should still show unit 2001, read " + mobile(helper, C).getLastCaller());
				})
				.thenSucceed();
	}

	/** The mobile's transmissions carry its unit ID in their mode; a radio with none is given one when programmed. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40, batch = BATCH)
	public static void programmingGivesAUnitId(GameTestHelper helper) {
		helper.setBlock(A, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		helper.runAfterDelay(2, () -> {
			CommercialMobileBlockEntity radio = mobile(helper, A);
			helper.assertTrue(radio.program().unitId() == 0, "a factory radio has a unit ID");
			helper.assertTrue(CommercialProgramming.writeChannel(radio, 0, CommercialChannel.parse("OPS|155.2125")).ok(), "not programmed");
			int unit = radio.program().unitId();
			helper.assertTrue(unit >= 1 && unit <= 9999, "no unit ID given: " + unit);
			CommercialProgramming.setUnitId(radio, 77);
			helper.assertTrue(radio.program().unitId() == 77, "the unit ID was not set");
			helper.succeed();
		});
	}
}
