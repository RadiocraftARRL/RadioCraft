package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.VHFBaseStationBlockEntity;
import com.arrl.radiocraft.common.datacomponents.HandheldMemory;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.RadioClonerItem;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket.Action;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The VHF base station's memory channels (2026-11-14, the maintainer: "the vhf base stations are missing the MEMORY
 * button and MEM/VFO toggle and the ability to save and navigate those channels"), through the panel's packet actions
 * ({@link SBlockRadioControlPacket#applyMemory}).
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class VhfMemoryGameTests {

	private static final BlockPos AT = new BlockPos(2, 2, 2), COPY = new BlockPos(5, 2, 2);
	private static final float REPEATER = 146_937_500.0F, CALLING = 145_500_000.0F, VFO = 144_600_000.0F;

	private static VHFBaseStationBlockEntity station(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, RadiocraftBlocks.VHF_BASE_STATION.get());
		return GameTestUtil.blockEntity(helper, at, VHFBaseStationBlockEntity.class);
	}

	private static void tune(VHFBaseStationBlockEntity radio, float hz, int offset, float tone, int step) {
		radio.setTuneStepHz(step);
		radio.setOffsetHz(offset);
		radio.setCtcssTone(tone);
		radio.setFrequency(hz);
		radio.updateFrequency(0);
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 50.0F;
	}

	/** Two channels stored, the VFO left elsewhere, the memories stepped through and left again; saved and loaded. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void storeRecallAndStepThroughMemories(GameTestHelper helper) {
		VHFBaseStationBlockEntity radio = station(helper, AT);
		tune(radio, REPEATER, -Ctcss.REPEATER_OFFSET_HZ, 100.0F, 12_500);
		helper.assertTrue(near(radio.getFrequency(), REPEATER), "Not on the repeater: " + radio.getFrequency());
		SBlockRadioControlPacket.applyMemory(radio, Action.MEM_STORE, 2);
		tune(radio, CALLING, 0, Ctcss.NONE, 0);
		SBlockRadioControlPacket.applyMemory(radio, Action.MEM_STORE, 7);
		helper.assertTrue(near(radio.getMemoryHz(2), REPEATER) && near(radio.getMemoryHz(7), CALLING), "Channels 3 and 8: " + radio.getMemoryHz(2) + ", " + radio.getMemoryHz(7));
		helper.assertTrue(radio.getMemoryOffset(2) == -Ctcss.REPEATER_OFFSET_HZ && radio.getMemoryTone(2) == 100.0F && radio.getMemoryStep(2) == 12_500,
				"Channel 3 lost its settings: " + radio.memoryLabel(2) + " step " + radio.getMemoryStep(2));

		tune(radio, VFO, 0, Ctcss.NONE, 0);
		SBlockRadioControlPacket.applyMemory(radio, Action.VFO_MEM, 0);
		helper.assertTrue(radio.isMemoryMode() && radio.getMemoryChannel() == 7 && near(radio.getFrequency(), CALLING), "VFO/MEM did not go to the last channel stored: "
				+ radio.getMemoryChannel() + " on " + radio.getFrequency());
		helper.assertTrue(radio.nextStored(7, 1) == 2 && radio.nextStored(2, -1) == 7, "Stepping skips the empty channels and wraps");
		SBlockRadioControlPacket.applyMemory(radio, Action.MEM_SELECT, 2);
		helper.assertTrue(near(radio.getFrequency(), REPEATER) && radio.getOffsetHz() == -Ctcss.REPEATER_OFFSET_HZ && radio.getCtcssTone() == 100.0F
				&& radio.getTuneStepHz() == 12_500, "Channel 3 did not bring its frequency, offset, tone and step: " + radio.getFrequency());
		SBlockRadioControlPacket.applyMemory(radio, Action.MEM_SELECT, 30); // Empty: stays.
		helper.assertTrue(radio.getMemoryChannel() == 2, "An empty channel was selected");
		SBlockRadioControlPacket.applyMemory(radio, Action.MEM_STORE, 9); // Not while on the memories.
		helper.assertTrue(radio.getMemoryHz(9) == 0.0F, "MEMORY stored while on the memories");

		CompoundTag saved = radio.saveWithoutMetadata(helper.getLevel().registryAccess());
		VHFBaseStationBlockEntity copy = station(helper, COPY);
		copy.loadWithComponents(saved, helper.getLevel().registryAccess());
		helper.assertTrue(copy.isMemoryMode() && copy.getMemoryChannel() == 2 && near(copy.getMemoryHz(7), CALLING) && copy.getMemoryTone(2) == 100.0F,
				"The memories were not saved");

		SBlockRadioControlPacket.applyMemory(radio, Action.VFO_MEM, 0);
		helper.assertTrue(!radio.isMemoryMode() && near(radio.getFrequency(), VFO) && radio.getOffsetHz() == 0 && radio.getCtcssTone() == Ctcss.NONE && radio.getTuneStepHz() == 0,
				"Back on the VFO it is not where it was: " + radio.getFrequency() + " " + radio.getOffsetHz() + " " + radio.getCtcssTone());

		// VFO/MEM held on a memory: the VFO takes it.
		SBlockRadioControlPacket.applyMemory(radio, Action.VFO_MEM, 0);
		SBlockRadioControlPacket.applyMemory(radio, Action.MEM_SELECT, 7);
		SBlockRadioControlPacket.applyMemory(radio, Action.MEM_TO_VFO, 0);
		SBlockRadioControlPacket.applyMemory(radio, Action.VFO_MEM, 0);
		SBlockRadioControlPacket.applyMemory(radio, Action.VFO_MEM, 0);
		helper.assertTrue(!radio.isMemoryMode() && near(radio.getFrequency(), CALLING), "The VFO did not take channel 8: " + radio.getFrequency());
		helper.succeed();
	}

	/** A channel off the 2m band (cloned in from somewhere) is not tuned by a radio without a MARS mod. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void aMemoryOutOfBandNeedsTheMarsMod(GameTestHelper helper) {
		VHFBaseStationBlockEntity radio = station(helper, AT);
		tune(radio, VFO, 0, Ctcss.NONE, 0);
		helper.assertTrue(!radio.hasMarsMod(), "A new base station has a MARS mod");
		radio.loadHandheldMemory(new HandheldMemory(List.of(new HandheldMemory.Channel(0, 164_487_500.0F, 0.0F, 0.0F, 0)), false, 0, null, HandheldMemory.Display.FREQUENCY));
		SBlockRadioControlPacket.applyMemory(radio, Action.VFO_MEM, 0);
		// Any radio receives out of band (a weather satellite's downlink); without a MARS mod it may not transmit there.
		helper.assertTrue(Math.abs(radio.getFrequency() - 164_487_500.0F) < 1.0F, "The memory was not recalled: " + radio.getFrequency());
		helper.assertTrue(!radio.mayTransmitOn(radio.getFrequency()), "It may transmit out of band without a MARS mod");
		helper.succeed();
	}

	/** The Radio Cloner carries a base station's first ten memories to a handheld, and back. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void theClonerCarriesTheMemories(GameTestHelper helper) {
		VHFBaseStationBlockEntity radio = station(helper, AT);
		tune(radio, CALLING, 0, Ctcss.NONE, 0);
		SBlockRadioControlPacket.applyMemory(radio, Action.MEM_STORE, 1);
		tune(radio, VFO, 0, Ctcss.NONE, 0);
		SBlockRadioControlPacket.applyMemory(radio, Action.MEM_STORE, 20); // Past a handheld's ten: stays behind.
		ItemStack cloner = new ItemStack(RadiocraftItems.RADIO_CLONER.get());
		helper.assertTrue(RadioClonerItem.useOn(cloner, helper.getLevel(), helper.absolutePos(AT), false).ok(), "The base station was not read");
		ItemStack handheld = new ItemStack(RadiocraftItems.VHF_HANDHELD.get());
		helper.assertTrue(RadioClonerItem.useOn(cloner, handheld, false).ok(), "The handheld was not written");
		HandheldMemory memory = handheld.get(RadiocraftDataComponent.HANDHELD_MEMORY.value());
		helper.assertTrue(memory != null && memory.channels().size() == 1 && memory.get(1) != null && near(memory.get(1).frequency(), CALLING), "Handheld memories: " + memory);

		VHFBaseStationBlockEntity other = station(helper, COPY);
		helper.assertTrue(RadioClonerItem.useOn(cloner, helper.getLevel(), helper.absolutePos(COPY), false).ok(), "The other base station was not written");
		helper.assertTrue(near(other.getMemoryHz(1), CALLING) && other.getMemoryHz(20) == 0.0F, "The other base station's memories: " + other.getMemoryHz(1));
		helper.succeed();
	}
}
