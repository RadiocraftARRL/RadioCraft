package com.arrl.radiocraft.common.realworld;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterfaceSpecTest {

	@Test
	void onlyTheNameIsNeeded() {
		InterfaceSpec spec = InterfaceSpec.parse("name=Shack").spec();
		assertEquals("Shack", spec.name());
		assertEquals("default", spec.inputDevice());
		assertEquals("default", spec.outputDevice());
		assertEquals(48000, spec.sampleRate());
		assertFalse(spec.hasSerial());
		assertFalse(spec.keysPtt()); // RTS by default, but no port to drive it.
		assertEquals(InterfaceSpec.Trigger.VOX, spec.trigger());
		assertEquals(-35.0D, spec.voxThresholdDb());
		assertEquals(180_000L, spec.timeoutMs());
		assertEquals("none", spec.pttText());
	}

	@Test
	void readsEveryKey() {
		InterfaceSpec spec = InterfaceSpec.parse(" name = Shack 2m ; input=USB Audio CODEC; output=Speakers (USB); rate=44100; serial=COM3;"
				+ " PTT=dtr; ptt_invert=yes; trigger=cos; vox_threshold=-40.5; vox_hang=500; cos=DSR; cos_invert=1; input_gain=2;"
				+ " output_gain=0.5; ptt_hang=250; ptt_delay=0; timeout=0;").spec();
		assertEquals("Shack 2m", spec.name());
		assertEquals("USB Audio CODEC", spec.inputDevice());
		assertEquals("Speakers (USB)", spec.outputDevice());
		assertEquals(44100, spec.sampleRate());
		assertEquals("COM3", spec.serialPort());
		assertEquals(AudioBackend.Line.DTR, spec.pttLine());
		assertTrue(spec.pttInvert());
		assertEquals(InterfaceSpec.Trigger.COS, spec.trigger());
		assertEquals(-40.5D, spec.voxThresholdDb());
		assertEquals(500, spec.voxHangMs());
		assertEquals(AudioBackend.Line.DSR, spec.cosLine());
		assertTrue(spec.cosInvert());
		assertEquals(2.0D, spec.inputGain());
		assertEquals(0.5D, spec.outputGain());
		assertEquals(250, spec.pttHangMs());
		assertEquals(0, spec.pttDelayMs());
		assertEquals(0L, spec.timeoutMs());
		assertTrue(spec.keysPtt());
		assertEquals("DTR on COM3 (low)", spec.pttText());
		assertEquals("COS on DSR (low)", spec.triggerText());
	}

	@Test
	void pttNoneKeysNothing() {
		InterfaceSpec spec = InterfaceSpec.parse("name=A;serial=/dev/ttyUSB0;ptt=none").spec();
		assertTrue(spec.hasSerial());
		assertNull(spec.pttLine());
		assertFalse(spec.keysPtt());
	}

	@Test
	void badLinesSayWhy() {
		assertFalse(InterfaceSpec.parse("").ok());
		assertFalse(InterfaceSpec.parse("input=x").ok()); // No name.
		assertTrue(InterfaceSpec.parse("name=A;colour=red").error().contains("unknown key"));
		assertTrue(InterfaceSpec.parse("name=A;rate=fast").error().contains("rate"));
		assertTrue(InterfaceSpec.parse("name=A;rate=1000").error().contains("8000"));
		assertTrue(InterfaceSpec.parse("name=A;ptt=CTS").error().contains("ptt"));
		assertTrue(InterfaceSpec.parse("name=A;trigger=COS").error().contains("serial")); // COS needs a port.
		assertTrue(InterfaceSpec.parse("name=A;vox_threshold=5").error().contains("vox_threshold"));
		assertTrue(InterfaceSpec.parse("name=A;ptt_invert=maybe").error().contains("ptt_invert"));
		assertTrue(InterfaceSpec.parse("name=A;nonsense").error().contains("key=value"));
		assertFalse(InterfaceSpec.parse("name=" + "x".repeat(InterfaceSpec.NAME_MAX + 1)).ok());
		assertFalse(InterfaceSpec.parse("name=bad;name").ok());
	}

	@Test
	void formatReadsBackTheSame() {
		InterfaceSpec spec = InterfaceSpec.parse("name=Shack;input=USB;rate=8000;serial=COM7;ptt=DTR;trigger=COS;cos=CTS;input_gain=1.5;vox_threshold=-30").spec();
		assertEquals(spec, InterfaceSpec.parse(spec.format()).spec());
		InterfaceSpec plain = InterfaceSpec.parse("name=Plain;ptt=NONE").spec();
		assertEquals(plain, InterfaceSpec.parse(plain.format()).spec());
	}

	@Test
	void parseAllSkipsBadLinesAndDuplicateNames() {
		List<String> problems = new ArrayList<>();
		List<InterfaceSpec> specs = InterfaceSpec.parseAll(List.of("name=One", "name=Two;rate=1", "name=one;input=other", "name=Three"), problems);
		assertEquals(List.of("One", "Three"), specs.stream().map(InterfaceSpec::name).toList());
		assertEquals(2, problems.size());
		assertTrue(problems.get(0).startsWith("line 2"));
		assertTrue(problems.get(1).startsWith("line 3"));
		assertEquals("One", InterfaceSpec.find(specs, "ONE").name());
		assertNull(InterfaceSpec.find(specs, "Two"));
	}
}
