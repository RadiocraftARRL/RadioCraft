package com.arrl.radiocraft.common.radio.commercial;

import com.arrl.radiocraft.common.radio.voice.DigitalVoice;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit IDs and contact lists on commercial radios (1.12 playtest fixes 2): stored with the program, written by the
 * programmer (or given at random), shown by alias, and what a cloning cable reads (keys left behind).
 */
class UnitIdTest {

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
	void theUnitAndContactsSurviveTheCodec() {
		CommercialProgram program = new CommercialProgram(List.of(CommercialChannel.simplex("A", 155_000_000.0F)), 0, 1205,
				List.of(new CommercialContact(1201, "eng 5"), new CommercialContact(1300, "Battalion chief")));
		JsonElement json = CommercialProgram.CODEC.encodeStart(JsonOps.INSTANCE, program).getOrThrow();
		CommercialProgram back = CommercialProgram.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(program, back);
		assertEquals(1205, back.unitId());
		assertEquals("ENG 5", back.contacts().getFirst().alias(), "aliases are upper case");
		assertEquals("BATTALIO", back.contacts().get(1).alias(), "eight characters");
	}

	@Test
	void anOldProgramHasNoUnitAndNoContacts() {
		CommercialProgram old = CommercialProgram.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"channels\":[{\"name\":\"A\",\"rx\":155000000.0,\"tx\":155000000.0}],\"selected\":0}")).getOrThrow();
		assertEquals(0, old.unitId());
		assertTrue(old.contacts().isEmpty());
	}

	@Test
	void contactsAreCleaned() {
		List<CommercialContact> list = new ArrayList<>();
		list.add(new CommercialContact(1201, "ENG 5"));
		list.add(new CommercialContact(1201, "DUPE")); // Same ID: the first is kept.
		list.add(new CommercialContact(0, "NOBODY")); // No ID.
		list.add(new CommercialContact(1300, "")); // No alias.
		list.add(new CommercialContact(12345, "BIG")); // Clamped to 9999.
		for(int i = 0; i < 40; i++)
			list.add(new CommercialContact(2000 + i, "U" + i));
		CommercialProgram program = CommercialProgram.EMPTY.withContacts(list);
		assertEquals(CommercialProgram.MAX_CONTACTS, program.contacts().size());
		assertEquals("ENG 5", program.alias(1201));
		assertNull(program.alias(1300));
		assertEquals("BIG", program.alias(9999));
	}

	@Test
	void theDisplayShowsTheAliasOrTheId() {
		CommercialProgram program = CommercialProgram.EMPTY.withContacts(List.of(new CommercialContact(1201, "ENG 5")));
		assertEquals("ENG 5", program.callerText(1201));
		assertEquals("ID 77", program.callerText(77));
	}

	@Test
	void editingChannelsKeepsTheIdentity() {
		CommercialProgram program = CommercialRadioModel.UPGRADED_HANDHELD.factoryProgram().withUnitId(42)
				.withContacts(List.of(new CommercialContact(43, "PARTNER")));
		CommercialProgram edited = program.with(3, CommercialChannel.simplex("X", 155_100_000.0F)).select(20).turn(1).zone(2, 3).fitTo(16);
		assertEquals(42, edited.unitId());
		assertEquals("PARTNER", edited.alias(43));
		assertEquals(0, new CommercialProgram(List.of(), 0, 10_000, List.of()).unitId(), "out of range is none");
	}

	@Test
	void writingGivesARadioAUnitIdOnce() {
		Bench radio = new Bench(CommercialRadioModel.VHF_MOBILE);
		assertEquals(0, radio.program().unitId(), "a new radio has none");
		assertTrue(CommercialProgramming.write(radio, CommercialProgramming.read(radio)).ok());
		int given = radio.program().unitId();
		assertTrue(given >= 1 && given <= CommercialProgram.UNIT_MAX, "given one at random: " + given);
		assertTrue(CommercialProgramming.write(radio, CommercialProgramming.read(radio)).ok());
		assertEquals(given, radio.program().unitId(), "kept on the next write");
		assertTrue(CommercialProgramming.write(radio, CommercialProgramming.read(radio), 1205, List.of(new CommercialContact(1, "DISPATCH"))).ok());
		assertEquals(1205, radio.program().unitId(), "the programmer's");
		assertEquals("DISPATCH", radio.program().alias(1));
		assertTrue(CommercialProgramming.write(radio, CommercialProgramming.read(radio), 0, null).ok());
		assertEquals(1205, radio.program().unitId(), "0 keeps it");
		assertEquals("DISPATCH", radio.program().alias(1), "null keeps the contacts");
		CommercialProgramming.setUnitId(radio, 9);
		assertEquals(9, radio.program().unitId());
	}

	@Test
	void aRefusedWriteGivesNoUnitId() {
		Bench radio = new Bench(CommercialRadioModel.BASIC_HANDHELD);
		List<CommercialChannel> channels = new ArrayList<>(CommercialProgramming.read(radio));
		channels.set(0, CommercialChannel.parse("P25|155.1|=|DIG"));
		assertTrue(!CommercialProgramming.write(radio, channels, 77, null).ok(), "the brick does no digital");
		assertEquals(0, radio.program().unitId());
	}

	@Test
	void aCloneLeavesHiddenKeysBehind() {
		Bench radio = new Bench(CommercialRadioModel.UPGRADED_HANDHELD);
		assertTrue(CommercialProgramming.writeChannel(radio, 0, CommercialChannel.parse("SWAT|155.5|=|DIG|0|900|H")).ok());
		assertTrue(CommercialProgramming.writeChannel(radio, 1, CommercialChannel.parse("OPS|155.7|=|DIG|0|55|R")).ok());
		List<CommercialChannel> clone = CommercialProgramming.readForClone(radio);
		assertEquals(40, clone.size());
		assertEquals(0, clone.get(0).keyId(), "the hidden key stays on the radio");
		assertTrue(clone.get(0).digital() && clone.get(0).keyReadable(), "the channel comes out clear digital");
		assertEquals(55, clone.get(1).keyId(), "a readable key is copied");
		assertEquals(1, CommercialProgramming.hiddenKeys(radio));
		assertEquals(DigitalVoice.Mode.DIGITAL_CLEAR, clone.get(0).mode());
	}
}
