package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFBaseStationBlockEntity;
import com.arrl.radiocraft.common.datacomponents.CloneImage;
import com.arrl.radiocraft.common.datacomponents.HandheldMemory;
import com.arrl.radiocraft.common.datacomponents.HandheldRadioState;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.CommercialHandheldItem;
import com.arrl.radiocraft.common.items.RadioClonerItem;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialContact;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Radio Cloner (1.12 playtest fixes 2): read one radio, write as many as wanted, only within a family, through the
 * commercial models' checks, unreadable keys left behind, unit IDs never copied; ham 2m dial and memories; marine
 * refused. No radio traffic.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class RadioClonerGameTests {

	private static final BlockPos MOBILE = new BlockPos(2, 2, 2), BASE = new BlockPos(4, 2, 2);

	private static ItemStack cloner() {
		return new ItemStack(RadiocraftItems.RADIO_CLONER.get());
	}

	private static CommercialProgramming.Target target(GameTestHelper helper, ItemStack radio) {
		CommercialProgramming.Target target = CommercialProgramming.of(radio);
		helper.assertTrue(target != null, radio + " is not a commercial radio");
		return target;
	}

	/** An upgraded handheld cloned into two more: channels and contacts copied, the hidden key left behind, own unit IDs. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void commercialClonesLeaveHiddenKeysBehind(GameTestHelper helper) {
		ItemStack source = new ItemStack(RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD.get());
		List<CommercialChannel> channels = new ArrayList<>();
		channels.add(CommercialChannel.parse("DISP|155.0125"));
		channels.add(CommercialChannel.parse("SWAT|155.5|=|DIG|0|321|H"));
		channels.add(CommercialChannel.parse("OPS|155.7|=|DIG|0|55|R"));
		helper.assertTrue(CommercialProgramming.write(target(helper, source), channels, 1205, List.of(new CommercialContact(1201, "ENG 5"))).ok(), "set up");

		ItemStack cloner = cloner();
		RadioClonerItem.Outcome read = RadioClonerItem.useOn(cloner, source, false);
		CloneImage image = RadioClonerItem.image(cloner);
		helper.assertTrue(read.ok() && image != null && image.isCommercial(), "not read: " + read.message().getString());
		helper.assertTrue(image.keysLeft() == 1 && image.channelCount() == 3 && image.program().unitId() == 0, "image " + image);
		helper.assertFalse(image.program().toString().contains("321"), "the hidden key is in the clone: " + image.program());

		for(int copy = 0; copy < 2; copy++) {
			ItemStack clone = new ItemStack(RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD.get());
			RadioClonerItem.Outcome written = RadioClonerItem.useOn(cloner, clone, false);
			helper.assertTrue(written.ok(), "not written: " + written.message().getString());
			CommercialProgram program = CommercialHandheldItem.program(clone);
			helper.assertTrue(program.channel(0).name().equals("DISP"), "channel 1 is " + program.channel(0));
			helper.assertTrue(program.channel(1).digital() && program.channel(1).keyId() == 0, "the hidden key was copied: " + program.channel(1));
			helper.assertTrue(program.channel(2).keyId() == 55, "the readable key was not copied: " + program.channel(2));
			helper.assertTrue("ENG 5".equals(program.alias(1201)), "the contacts were not copied");
			helper.assertTrue(program.unitId() > 0, "the clone has no unit ID of its own");
		}
		helper.assertTrue(RadioClonerItem.image(cloner) != null, "the cloner emptied itself");
		helper.succeed();
	}

	/** The brick takes no digital (refused, nothing written); a 16-channel mobile takes the first 16 of 20. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void commercialClonesRespectTheModel(GameTestHelper helper) {
		ItemStack source = new ItemStack(RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD.get());
		List<CommercialChannel> channels = new ArrayList<>();
		for(int i = 0; i < 20; i++)
			channels.add(CommercialChannel.simplex("CH" + (i + 1), 155_000_000.0F + i * 12_500.0F));
		channels.set(3, CommercialChannel.parse("P25|155.3|=|DIG"));
		helper.assertTrue(CommercialProgramming.write(target(helper, source), channels).ok(), "set up");
		ItemStack cloner = cloner();
		RadioClonerItem.useOn(cloner, source, false);

		ItemStack brick = new ItemStack(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get());
		RadioClonerItem.Outcome refused = RadioClonerItem.useOn(cloner, brick, false);
		helper.assertFalse(refused.ok(), "the brick took a digital channel");
		helper.assertTrue(CommercialHandheldItem.program(brick).channel(0).isBlank(), "a refused clone changed the brick");

		helper.setBlock(MOBILE, RadiocraftBlocks.COMMERCIAL_MOBILE.get());
		CommercialMobileBlockEntity mobile = GameTestUtil.blockEntity(helper, MOBILE, CommercialMobileBlockEntity.class);
		RadioClonerItem.Outcome cut = RadioClonerItem.useOn(cloner, helper.getLevel(), helper.absolutePos(MOBILE), false);
		helper.assertTrue(cut.ok(), "the placed mobile refused: " + cut.message().getString());
		helper.assertTrue(mobile.program().programmedCount() == 16 && mobile.program().channel(15).name().equals("CH16"), "the mobile has " + mobile.program());
		helper.assertTrue(cut.message().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents said && said.getKey().endsWith("cloner.written_cut"),
				"not told what was left out: " + cut.message().getContents());

		// Sneak on the placed mobile: read it instead (replacing what the cloner holds).
		RadioClonerItem.useOn(cloner, helper.getLevel(), helper.absolutePos(MOBILE), true);
		helper.assertTrue(RadioClonerItem.image(cloner).model().equals("vhf_mobile"), "the mobile was not read");
		helper.succeed();
	}

	/** 2m ham: a handheld's dial and memories into another handheld; a base station's dial; families never mix. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void hamClonesAndFamiliesDoNotMix(GameTestHelper helper) {
		ItemStack source = new ItemStack(RadiocraftItems.VHF_HANDHELD.get());
		source.set(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value(),
				new HandheldRadioState(true, false, 146_940_000.0F, 1.0F, 1.0F, 0.0F, 100.0F, -600_000, 100.0F));
		source.set(RadiocraftDataComponent.HANDHELD_MEMORY.value(), HandheldMemory.EMPTY
				.store(new HandheldMemory.Channel(1, 146_520_000.0F, 0.0F, 0.0F, 0, "CALL"))
				.store(new HandheldMemory.Channel(4, 147_180_000.0F, 103.5F, 103.5F, 600_000, "CLUB")));
		ItemStack cloner = cloner();
		helper.assertTrue(RadioClonerItem.useOn(cloner, source, false).ok(), "the ham handheld was not read");
		helper.assertTrue(RadioClonerItem.image(cloner).isHam() && RadioClonerItem.image(cloner).channelCount() == 2, "image " + RadioClonerItem.image(cloner));

		ItemStack copy = new ItemStack(RadiocraftItems.VHF_HANDHELD.get());
		helper.assertTrue(RadioClonerItem.useOn(cloner, copy, false).ok(), "the copy was not written");
		HandheldRadioState state = copy.get(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value());
		helper.assertTrue(state != null && state.freq() == 146_940_000.0F && state.offsetHz() == -600_000 && state.ctcssTone() == 100.0F, "dial " + state);
		HandheldMemory memory = copy.get(RadiocraftDataComponent.HANDHELD_MEMORY.value());
		helper.assertTrue(memory != null && memory.channels().size() == 2 && "CLUB".equals(memory.get(4).name()), "memories " + memory);

		helper.setBlock(BASE, RadiocraftBlocks.VHF_BASE_STATION.get());
		VHFBaseStationBlockEntity base = GameTestUtil.blockEntity(helper, BASE, VHFBaseStationBlockEntity.class);
		helper.assertTrue(RadioClonerItem.useOn(cloner, helper.getLevel(), helper.absolutePos(BASE), false).ok(), "the base station was not written");
		helper.assertTrue(Math.abs(base.getFrequency() - 146_940_000.0F) < 1.0F && base.getOffsetHz() == -600_000 && base.getCtcssTone() == 100.0F,
				"base station on " + base.getFrequency() + " offset " + base.getOffsetHz() + " tone " + base.getCtcssTone());

		// Families do not mix, and marine radios are not cloned.
		helper.assertFalse(RadioClonerItem.useOn(cloner, new ItemStack(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get()), false).ok(), "a ham clone went into a commercial radio");
		helper.assertFalse(RadioClonerItem.useOn(cloner, new ItemStack(RadiocraftItems.MARINE_HANDHELD.get()), false).ok(), "a marine handheld was cloned");
		ItemStack commercial = cloner();
		RadioClonerItem.useOn(commercial, new ItemStack(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get()), false);
		ItemStack ham = new ItemStack(RadiocraftItems.VHF_HANDHELD.get());
		helper.assertFalse(RadioClonerItem.useOn(commercial, ham, false).ok(), "a commercial clone went into a ham handheld");
		helper.assertTrue(ham.get(RadiocraftDataComponent.HANDHELD_MEMORY.value()) == null, "the ham handheld was changed");
		helper.succeed();
	}

	/** Used in the air: the handheld in the other hand is read; sneaking clears the cloner. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void theOtherHandAndClearing(GameTestHelper helper) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("cloner".getBytes()), "cloner"));
		player.getInventory().clearContent();
		ItemStack cloner = cloner();
		player.setItemInHand(InteractionHand.MAIN_HAND, cloner);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get()));
		cloner.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(RadioClonerItem.image(player.getMainHandItem()) != null, "the off hand radio was not read");
		player.setShiftKeyDown(true);
		player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		player.setShiftKeyDown(false);
		helper.assertTrue(RadioClonerItem.image(player.getMainHandItem()) == null, "sneak-use did not clear it");
		helper.succeed();
	}
}
