package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The radio programmer's codeplug (1.12 step 6): 40 positions (16 on most radios), its file and request layout, and that a key a radio
 * will not show never comes out of it, through a read, a save, a write back or a clone to another radio.
 */
class CodeplugTest {

	/** A radio for the programming API with nothing of Minecraft in it. */
	private static final class Bench implements CommercialProgramming.Target {
		CommercialProgram program;
		final CommercialRadioModel model;

		Bench(CommercialRadioModel model) {
			this.model = model;
			this.program = model.factoryProgram();
		}

		@Override
		public CommercialRadioModel model() {
			return model;
		}

		@Override
		public CommercialProgram program() {
			return program;
		}

		@Override
		public void storeProgram(CommercialProgram program) {
			this.program = program;
		}
	}

	@Test
	void alwaysFortyPositions() {
		Codeplug plug = new Codeplug("vhf_mobile", List.of(CommercialChannel.simplex("ONE", 155_000_000.0F)));
		assertEquals(CommercialProgram.MAX_CHANNELS, plug.channels().size());
		assertEquals(40, plug.channels().size());
		assertTrue(plug.channel(39).isBlank());
		assertEquals(16, plug.capacity(), "the mobile holds 16");
		assertEquals(40, Codeplug.blank("upgraded_handheld").capacity());
		assertEquals(40, Codeplug.blank("").capacity(), "no model: room for the largest");
		assertEquals(1, plug.programmedCount());
		assertEquals("TWO", plug.with(3, CommercialChannel.simplex("TWO", 155_100_000.0F)).channel(3).name());
	}

	@Test
	void savesAndLoadsEveryField() {
		CommercialChannel tac = new CommercialChannel("TAC", 155_500_000.0F, 156_000_000.0F, true, 0.0F, 321, true);
		CommercialChannel rx = new CommercialChannel("WX", 162_550_000.0F, 0.0F, false, 0.0F, 0, true);
		CommercialChannel toned = new CommercialChannel("FIRE", 154_280_000.0F, 154_280_000.0F, false, 100.0F, 0, true);
		Codeplug plug = new Codeplug("vhf_mobile", List.of(tac, CommercialChannel.BLANK, rx, toned));
		Codeplug back = Codeplug.fromTag(ComputerFileFormats.nbt(ComputerFileFormats.nbt(plug.toTag())));
		assertEquals(plug, back);
		assertEquals("vhf_mobile", back.model());
		assertTrue(back.channel(1).isBlank());
	}

	@Test
	void aHiddenKeyNeverComesOut() {
		Bench radio = new Bench(CommercialRadioModel.VHF_MOBILE);
		assertTrue(CommercialProgramming.writeChannel(radio, 2, CommercialChannel.parse("TAC|155.5|=|DIG|0|321|H")).ok());

		// Read into a codeplug and saved: the key is hidden in it.
		Codeplug plug = new Codeplug(radio.model().id(), CommercialProgramming.read(radio));
		Codeplug saved = Codeplug.fromTag(ComputerFileFormats.nbt(ComputerFileFormats.nbt(plug.toTag())));
		assertEquals(CommercialChannel.KEY_HIDDEN, saved.channel(2).keyId());

		// Written back, the radio keeps its key, still hidden even if the request asks for it readable.
		List<CommercialChannel> back = new ArrayList<>(saved.channels());
		CommercialChannel asked = back.get(2);
		back.set(2, new CommercialChannel("TAC NEW", asked.rxHz(), asked.txHz(), true, 0.0F, CommercialChannel.KEY_HIDDEN, true));
		assertTrue(CommercialProgramming.write(radio, back).ok());
		assertEquals(321, radio.program().channel(2).keyId(), "the radio kept its key");
		assertEquals("TAC NEW", radio.program().channel(2).name(), "the rest of the channel was written");
		assertFalse(radio.program().channel(2).keyReadable(), "a hidden key cannot be made readable without typing it in");
		assertEquals(CommercialChannel.KEY_HIDDEN, CommercialProgramming.read(radio).get(2).keyId(), "and it still does not read out");

		// Cloned into a radio with no key there, the channel is refused.
		Bench other = new Bench(CommercialRadioModel.VHF_MOBILE);
		CommercialProgramming.Result cloned = CommercialProgramming.write(other, saved.channels());
		assertFalse(cloned.ok());
		assertEquals("hidden_key_unknown", cloned.problems().getFirst().key());

		// A new key typed in replaces it, readable or not as asked.
		back.set(2, new CommercialChannel("TAC", asked.rxHz(), asked.txHz(), true, 0.0F, 999, true));
		assertTrue(CommercialProgramming.write(radio, back).ok());
		assertEquals(999, CommercialProgramming.read(radio).get(2).keyId());
	}

	@Test
	void aSixteenChannelFileLoads() {
		// A .CPG saved before 40-channel radios: a list of 16.
		List<CommercialChannel> sixteen = new ArrayList<>();
		for(int i = 0; i < 16; i++)
			sixteen.add(CommercialChannel.simplex("CH" + (i + 1), 155_000_000.0F + i * 12_500.0F));
		CompoundTag old = new CompoundTag();
		old.putString("model", "basic_handheld");
		old.put("channels", Codeplug.channelsTag(sixteen));
		Codeplug loaded = Codeplug.fromTag(ComputerFileFormats.nbt(ComputerFileFormats.nbt(old)));
		assertEquals(16, loaded.programmedCount());
		assertEquals("CH16", loaded.channel(15).name());
		assertTrue(loaded.channel(16).isBlank());
		assertEquals(0, loaded.beyondCapacity());
		Bench brick = new Bench(CommercialRadioModel.BASIC_HANDHELD);
		assertTrue(CommercialProgramming.write(brick, loaded.channels()).ok(), "a 16-channel file writes into a 16-channel radio");
	}

	@Test
	void fortyChannelsForTheUpgradedHandheldOnly() {
		Codeplug plug = Codeplug.blank("upgraded_handheld");
		for(int i = 0; i < 40; i++)
			plug = plug.with(i, CommercialChannel.simplex("CH" + (i + 1), 155_000_000.0F + i * 12_500.0F));
		assertEquals(40, plug.programmedCount());
		Codeplug back = Codeplug.fromTag(ComputerFileFormats.nbt(ComputerFileFormats.nbt(plug.toTag())));
		assertEquals("CH40", back.channel(39).name(), "all 40 saved");
		Bench upgraded = new Bench(CommercialRadioModel.UPGRADED_HANDHELD);
		assertTrue(CommercialProgramming.write(upgraded, back.channels()).ok());
		assertEquals("CH40", upgraded.program().channel(39).name());

		Codeplug forBrick = new Codeplug("basic_handheld", back.channels());
		assertEquals(24, forBrick.beyondCapacity(), "24 channels do not fit a 16-channel radio");
		assertFalse(CommercialProgramming.write(new Bench(CommercialRadioModel.BASIC_HANDHELD), forBrick.channels()).ok());
	}

	@Test
	void theModelChecksWhatIsWritten() {
		Bench brick = new Bench(CommercialRadioModel.BASIC_HANDHELD);
		Codeplug plug = Codeplug.blank("basic_handheld").with(0, new CommercialChannel("DIG", 155_500_000.0F, 155_500_000.0F, true, 0.0F, 0, true));
		CommercialProgramming.Result result = CommercialProgramming.write(brick, plug.channels());
		assertFalse(result.ok());
		assertEquals("no_digital", result.problems().getFirst().key());
		assertTrue(brick.program().channel(0).isBlank(), "nothing was written");
	}

	@Test
	void theUnitIdAndContactsAreSaved() {
		Codeplug plug = Codeplug.blank("vhf_mobile").withUnitId(1205)
				.withContacts(List.of(new com.arrl.radiocraft.common.radio.commercial.CommercialContact(1201, "ENG 5")));
		Codeplug back = Codeplug.fromTag(plug.toTag());
		assertEquals(1205, back.unitId());
		assertEquals("ENG 5", back.contacts().getFirst().alias());
		assertEquals(back, back.forModel("vhf_mobile"));
		CompoundTag old = new CompoundTag(); // A .CPG from before unit IDs.
		old.putString("model", "vhf_mobile");
		Codeplug older = Codeplug.fromTag(old);
		assertEquals(0, older.unitId());
		assertTrue(older.contacts().isEmpty());
		assertEquals(0, plug.withUnitId(12_000).unitId(), "out of range is none");
	}
}
