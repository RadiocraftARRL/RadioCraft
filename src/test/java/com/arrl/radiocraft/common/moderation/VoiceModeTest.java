package com.arrl.radiocraft.common.moderation;

import com.arrl.radiocraft.common.radio.voice.DigitalVoice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The mode the moderation log gives a voice transmission, with the sender's unit ID (1.12 playtest fixes 2). */
class VoiceModeTest {

	@Test
	void theUnitIdIsLogged() {
		assertEquals("FM", ModerationLog.voiceMode("FM", DigitalVoice.Mode.ANALOG));
		assertEquals("FM ID 1205", ModerationLog.voiceMode("FM", new DigitalVoice.Mode(false, 0, 1205)));
		assertEquals("DIG ID 7", ModerationLog.voiceMode("FM", new DigitalVoice.Mode(true, 0, 7)));
		assertEquals("DIG ENC ID 2001", ModerationLog.voiceMode("FM", new DigitalVoice.Mode(true, 100, 2001)));
		assertEquals("USB", ModerationLog.voiceMode("USB", DigitalVoice.Mode.ANALOG));
	}
}
