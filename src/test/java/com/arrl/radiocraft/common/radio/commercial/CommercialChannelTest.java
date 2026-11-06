package com.arrl.radiocraft.common.radio.commercial;

import com.arrl.radiocraft.common.radio.voice.DigitalVoice;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Commercial channels, programs and models, and the programming API on a stand-in radio. */
class CommercialChannelTest {

	@Test
	void textFormRoundTrips() {
		CommercialChannel fire = CommercialChannel.parse("fire tac 2|154.2800|153.9500|DIG|0|4242|H");
		assertEquals("FIRE TAC", fire.name(), "upper case, eight characters");
		assertEquals(154_280_000.0F, fire.rxHz(), 1.0F);
		assertEquals(153_950_000.0F, fire.txHz(), 1.0F);
		assertTrue(fire.digital() && fire.encrypted() && !fire.keyReadable());
		assertEquals(fire, CommercialChannel.parse(fire.format()));
		CommercialChannel simple = CommercialChannel.parse("MURS 1|151.82");
		assertEquals(simple.rxHz(), simple.txHz());
		assertFalse(simple.digital());
		assertTrue(CommercialChannel.parse("NOAA|162.55|RX").isReceiveOnly());
		assertTrue(CommercialChannel.parse("-").isBlank());
		assertThrows(IllegalArgumentException.class, () -> CommercialChannel.parse("BAD|abc"));
		assertThrows(IllegalArgumentException.class, () -> CommercialChannel.parse("BAD|151.82|=|AM"));
	}

	@Test
	void analogCarriesItsToneAndNoKey() {
		CommercialChannel analog = CommercialChannel.parse("BUS|151.625|=|FM|100|77");
		assertEquals(100.0F, analog.analogTone(), 0.01F);
		assertEquals(0, analog.keyId(), "analog has no key");
		assertNull(analog.decoder());
		assertEquals(DigitalVoice.Mode.ANALOG, analog.mode());
		CommercialChannel digital = CommercialChannel.parse("P25|155.1|=|DIG|100");
		assertEquals(0.0F, digital.analogTone(), "digital sends no CTCSS");
		assertEquals(DigitalVoice.Mode.DIGITAL_CLEAR, digital.mode());
	}

	@Test
	void unreadableKeysAreHidden() {
		CommercialChannel secret = CommercialChannel.parse("SWAT|155.5|=|DIG|0|900|H");
		assertEquals(CommercialChannel.KEY_HIDDEN, secret.withKeyHidden().keyId());
		CommercialChannel open = CommercialChannel.parse("OPS|155.5|=|DIG|0|900|R");
		assertEquals(900, open.withKeyHidden().keyId());
	}

	@Test
	void theKnobStopsAtOneAndSixteen() {
		CommercialProgram program = new CommercialProgram(List.of(CommercialChannel.simplex("A", 151_000_000.0F)), 0);
		assertEquals(0, program.turn(-3).selected());
		assertEquals(15, program.turn(40).selected());
		assertNull(program.select(4).active(), "an unprogrammed position");
		CommercialProgram written = program.with(5, CommercialChannel.simplex("F", 152_000_000.0F));
		assertEquals(6, written.channels().size(), "padded with blanks");
		assertEquals(2, written.programmedCount());
		assertEquals(1, written.with(5, CommercialChannel.BLANK).channels().size(), "trailing blanks are trimmed");
	}

	private static List<CommercialChannel> channels(int count) {
		List<CommercialChannel> out = new ArrayList<>();
		for(int i = 0; i < count; i++)
			out.add(CommercialChannel.simplex("CH" + (i + 1), 151_000_000.0F + i * 12_500.0F));
		return out;
	}

	/** A full upgraded handheld: 64 channels, four zones of 16. */
	private static List<CommercialChannel> full() {
		return channels(64);
	}

	@Test
	void capacityIsPerModel() {
		assertEquals(16, CommercialRadioModel.BASIC_HANDHELD.channelCount());
		assertEquals(64, CommercialRadioModel.UPGRADED_HANDHELD.channelCount());
		assertEquals(16, CommercialRadioModel.VHF_MOBILE.channelCount());
		assertEquals(16, CommercialRadioModel.LOW_BAND_MOBILE.channelCount());
		assertFalse(CommercialRadioModel.BASIC_HANDHELD.hasZones());
		assertEquals(4, CommercialRadioModel.UPGRADED_HANDHELD.zones(), "zones A, B, C and D");
		assertEquals(64, CommercialProgram.MAX_CHANNELS);
		assertEquals(64, CommercialProgram.MAX_POSITIONS, "every position of four full zones");
	}

	@Test
	void theZoneSwitchKeepsTheKnob() {
		CommercialProgram program = new CommercialProgram(full(), 3); // Zone A, knob 4.
		assertEquals(64, program.channels().size());
		CommercialProgram zoneB = program.zone(1, 4);
		assertEquals(19, zoneB.selected(), "zone B, knob 4 is channel 20");
		assertEquals("CH20", zoneB.active().name());
		assertEquals('B', CommercialProgram.zoneLetter(zoneB.zone()));
		assertEquals(3, zoneB.knob());
		assertEquals(31, zoneB.turn(40).selected(), "the knob stops at 16 inside its zone");
		assertEquals(16, zoneB.turn(-40).selected(), "and at 1");
		CommercialProgram zoneC = zoneB.turn(40).zone(2, 4);
		assertEquals(47, zoneC.selected(), "zone C, knob 16");
		assertEquals("CH48", zoneC.active().name());
		CommercialProgram zoneD = zoneC.zone(3, 4);
		assertEquals(63, zoneD.selected(), "zone D, knob 16");
		assertEquals("CH64", zoneD.active().name());
		assertEquals(3, program.zone(9, 4).zone(), "the zone switch has four positions");
		CommercialProgram old = new CommercialProgram(channels(40), 0).zone(3, 4);
		assertNull(old.active(), "a 40-channel program has nothing in zone D");
		assertEquals("CH40", old.zone(2, 4).turn(7).active().name(), "zone C, knob 8 is the last of 40");
		assertEquals(0, program.zone(2, 1).zone(), "a radio with no zone switch stays in A");
	}

	@Test
	void fittingToASmallerRadio() {
		CommercialProgram program = new CommercialProgram(full(), 53); // Zone D, knob 6.
		assertSame(program, CommercialRadioModel.UPGRADED_HANDHELD.fit(program));
		CommercialProgram small = CommercialRadioModel.VHF_MOBILE.fit(program);
		assertEquals(16, small.channels().size(), "channels past 16 dropped");
		assertEquals(5, small.selected(), "the knob kept, in zone A");
		List<CommercialChannel> tooMany = new ArrayList<>(full());
		tooMany.add(CommercialChannel.simplex("X", 152_000_000.0F));
		assertEquals(64, new CommercialProgram(tooMany, 0).channels().size(), "never more than 64 stored");
	}

	@Test
	void aSixteenChannelSaveStillLoads() {
		// The component as 1.12 saved it before the playtest: 16 channels, the knob on 6.
		StringBuilder json = new StringBuilder("{\"selected\":5,\"channels\":[");
		for(int i = 0; i < 16; i++) {
			float hz = 151_000_000.0F + i * 12_500.0F;
			json.append(i == 0 ? "" : ",").append("{\"name\":\"CH").append(i + 1).append("\",\"rx\":").append(hz).append(",\"tx\":").append(hz).append('}');
		}
		json.append("]}");
		CommercialProgram loaded = CommercialProgram.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json.toString())).getOrThrow();
		assertEquals(16, loaded.channels().size());
		assertEquals(5, loaded.selected());
		assertEquals(0, loaded.zone());
		assertEquals("CH6", loaded.active().name());
		assertSame(loaded, CommercialRadioModel.UPGRADED_HANDHELD.fit(loaded), "unchanged on the 64-channel handheld");
		assertSame(loaded, CommercialRadioModel.BASIC_HANDHELD.fit(loaded), "and on the 16-channel one");
		CommercialProgram again = CommercialProgram.CODEC.parse(JsonOps.INSTANCE, CommercialProgram.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow()).getOrThrow();
		assertEquals(loaded, again);
	}

	@Test
	void theUpgradedHandheldTakesSixtyFourAndTheBrickSixteen() {
		Bench upgraded = new Bench(CommercialRadioModel.UPGRADED_HANDHELD);
		assertTrue(CommercialProgramming.write(upgraded, full()).ok());
		assertEquals(64, CommercialProgramming.read(upgraded).size());
		assertEquals("CH64", CommercialProgramming.read(upgraded).get(63).name());
		assertTrue(CommercialProgramming.writeChannel(upgraded, 63, CommercialChannel.simplex("LAST", 152_000_000.0F)).ok());
		assertFalse(CommercialProgramming.writeChannel(upgraded, 64, CommercialChannel.BLANK).ok(), "no 65th channel");
		CommercialProgramming.select(upgraded, 49);
		assertEquals("CH50", upgraded.program().active().name());
		assertEquals(3, upgraded.program().zone());
		assertTrue(CommercialProgramming.write(upgraded, channels(40)).ok(), "a 40-channel codeplug still goes in");
		assertEquals(40, upgraded.program().programmedCount());

		Bench brick = new Bench(CommercialRadioModel.BASIC_HANDHELD);
		CommercialProgramming.Result refused = CommercialProgramming.write(brick, full());
		assertFalse(refused.ok(), "64 channels do not fit a 16-channel radio");
		assertEquals("too_many_channels", refused.problems().getFirst().key());
		assertEquals(16, refused.problems().getFirst().position(), "the first channel that does not fit");
		List<CommercialChannel> padded = new ArrayList<>(full().subList(0, 16));
		while(padded.size() < 64)
			padded.add(CommercialChannel.BLANK);
		assertTrue(CommercialProgramming.write(brick, padded).ok(), "blanks past 16 are fine");
		assertEquals(16, CommercialProgramming.read(brick).size());
		CommercialProgramming.select(brick, 49);
		assertEquals(0, brick.program().zone(), "no zone switch");
		assertEquals(1, brick.program().selected(), "the knob position kept");
	}

	@Test
	void modelsRefuseWhatTheyCannotDo() {
		CommercialChannel twoMeters = CommercialChannel.simplex("2M", 146_520_000.0F);
		CommercialChannel encrypted = CommercialChannel.parse("ENC|155.0|=|DIG|0|5");
		assertEquals("rx_out_of_range", CommercialRadioModel.BASIC_HANDHELD.check(twoMeters));
		assertNull(CommercialRadioModel.UPGRADED_HANDHELD.check(twoMeters), "the upgraded handheld covers 2m");
		assertEquals("no_digital", CommercialRadioModel.BASIC_HANDHELD.check(encrypted));
		assertNull(CommercialRadioModel.VHF_MOBILE.check(encrypted));
		assertEquals("rx_out_of_range", CommercialRadioModel.LOW_BAND_MOBILE.check(encrypted));
		assertNull(CommercialRadioModel.LOW_BAND_MOBILE.check(CommercialChannel.simplex("LB", 39_460_000.0F)));
		assertEquals("tx_out_of_range", CommercialRadioModel.VHF_MOBILE.check(CommercialChannel.parse("X|155.0|146.0")));
		for(CommercialRadioModel model : CommercialRadioModel.values())
			assertEquals(0, model.factoryProgram().channels().size(), model + " ships blank");
	}

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
	void programmingIsAllOrNothingAndKeepsHiddenKeys() {
		Bench radio = new Bench(CommercialRadioModel.VHF_MOBILE);
		assertTrue(CommercialProgramming.writeChannel(radio, 2, CommercialChannel.parse("TAC|155.5|=|DIG|0|321|H")).ok());
		List<CommercialChannel> read = CommercialProgramming.read(radio);
		assertEquals(16, read.size(), "the mobile holds 16 channels");
		assertEquals(CommercialChannel.KEY_HIDDEN, read.get(2).keyId(), "the programmer cannot read the key");
		assertTrue(CommercialProgramming.write(radio, read).ok(), "written back as read");
		assertEquals(321, radio.program().channel(2).keyId(), "the hidden key is kept");

		List<CommercialChannel> bad = new ArrayList<>(read);
		bad.set(0, CommercialChannel.simplex("HAM", 146_520_000.0F));
		CommercialProgramming.Result result = CommercialProgramming.write(radio, bad);
		assertFalse(result.ok());
		assertEquals(0, result.problems().getFirst().position());
		assertTrue(radio.program().channel(0).isBlank(), "nothing was written");

		assertFalse(CommercialProgramming.writeChannel(radio, 5, CommercialChannel.parse("NEW|155.6|=|DIG|0|-1")).ok(), "a hidden key with nothing to keep");
		assertFalse(CommercialProgramming.writeChannel(radio, 16, CommercialChannel.BLANK).ok());
		CommercialProgramming.select(radio, 2);
		assertEquals("TAC", radio.program().active().name());
	}
}
