package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketBbsTest {

	private static final String BBS = "W1BTR-1";

	private static boolean has(List<String> lines, String part) {
		return lines.stream().anyMatch(line -> line.contains(part));
	}

	/** {@code from} writes a personal message through the commands; returns its number. */
	private static int send(PacketBbs bbs, String from, String to, String subject, String body, long now) {
		bbs.handle(BBS, from, "SP " + to + " " + subject, now);
		bbs.handle(BBS, from, body, now);
		List<String> saved = bbs.handle(BBS, from, "/EX", now);
		assertTrue(has(saved, "SAVED"), saved.toString());
		return bbs.messages().getLast().number();
	}

	@Test
	void firstContactIsGreetedWithTheMailCount() {
		PacketBbs bbs = new PacketBbs();
		bbs.post("W1ABC", "W1XYZ", "Hi", "Hello", false, 0L);
		List<String> out = bbs.handle(BBS, "W1XYZ-7", "?", 10L);
		assertEquals("[RCBBS] W1BTR-1 MAILBOX - 1 MSGS FOR YOU - ? FOR HELP", out.getFirst());
		assertTrue(has(out, "CMDS"));
		assertFalse(has(bbs.handle(BBS, "W1XYZ", "?", 11L), "[RCBBS]"), "greeted only once (the SSID does not matter)");
	}

	@Test
	void sendPersonalAsksForTheSubject() {
		PacketBbs bbs = new PacketBbs();
		bbs.handle(BBS, "W1ABC", "L", 0L);
		assertEquals(List.of("SUBJECT:"), bbs.handle(BBS, "W1ABC", "sp w1xyz", 1L));
		assertEquals(List.of("ENTER MESSAGE, /EX TO END"), bbs.handle(BBS, "W1ABC", "Field day", 2L));
		assertTrue(bbs.handle(BBS, "W1ABC", "Meet at the park", 3L).isEmpty());
		bbs.handle(BBS, "W1ABC", "Bring a radio", 4L);
		assertEquals(List.of("MSG 1 SAVED"), bbs.handle(BBS, "W1ABC", "/ex", 5L));
		PacketBbs.Message message = bbs.find(1);
		assertNotNull(message);
		assertEquals("W1XYZ", message.to());
		assertEquals("Field day", message.subject());
		assertEquals("Meet at the park\nBring a radio", message.body());
		assertFalse(message.bulletin());
	}

	@Test
	void bulletinsAreForEveryoneAndPersonalMailIsNot() {
		PacketBbs bbs = new PacketBbs();
		int personal = send(bbs, "W1ABC", "W1XYZ", "Secret", "For you", 0L);
		bbs.handle(BBS, "W1ABC", "SB NET Net tonight", 1L);
		bbs.handle(BBS, "W1ABC", "8 pm on the repeater", 1L);
		bbs.handle(BBS, "W1ABC", "/EX", 1L);
		int bulletin = bbs.messages().getLast().number();
		assertTrue(bbs.find(bulletin).bulletin());
		List<String> other = bbs.handle(BBS, "K2OTH", "L", 2L);
		assertTrue(has(other, bulletin + " B"), other.toString());
		assertFalse(has(other, personal + " P"), other.toString());
		assertTrue(has(bbs.handle(BBS, "K2OTH", "R " + personal, 3L), "NOT FOR YOU"));
		List<String> read = bbs.handle(BBS, "K2OTH", "R " + bulletin, 4L);
		assertTrue(has(read, "8 pm on the repeater") && has(read, "END MSG " + bulletin), read.toString());
		assertTrue(has(bbs.handle(BBS, "K2OTH", "LB", 5L), "Net tonight"));
		assertEquals(List.of("NO MSGS"), bbs.handle(BBS, "K2OTH", "LM", 6L));
	}

	@Test
	void readingMarksPersonalMailRead() {
		PacketBbs bbs = new PacketBbs();
		int n = send(bbs, "W1ABC", "W1XYZ", "Hi", "Hello", 0L);
		assertTrue(has(bbs.handle(BBS, "W1XYZ", "LM", 1L), n + " P*"));
		bbs.takeModified();
		bbs.handle(BBS, "W1XYZ", "R " + n, 2L);
		assertTrue(bbs.find(n).read());
		assertTrue(bbs.takeModified());
		assertEquals(0, bbs.unreadFor("W1XYZ"));
		bbs.handle(BBS, "W1ABC", "R " + n, 3L); // The sender may read it; that does not count.
	}

	@Test
	void abortDropsTheDraft() {
		PacketBbs bbs = new PacketBbs();
		bbs.handle(BBS, "W1ABC", "SP W1XYZ", 0L);
		assertEquals(List.of("MSG ABORTED"), bbs.handle(BBS, "W1ABC", "/AB", 1L));
		bbs.handle(BBS, "W1ABC", "SP W1XYZ Subj", 2L);
		bbs.handle(BBS, "W1ABC", "text", 3L);
		assertEquals(List.of("MSG ABORTED"), bbs.handle(BBS, "W1ABC", "/ab", 4L));
		assertTrue(bbs.messages().isEmpty());
	}

	@Test
	void killIsForTheSenderOrAddressee() {
		PacketBbs bbs = new PacketBbs();
		int n = send(bbs, "W1ABC", "W1XYZ", "Hi", "Hello", 0L);
		assertTrue(has(bbs.handle(BBS, "K2OTH", "K " + n, 1L), "NOT YOURS"));
		assertNotNull(bbs.find(n));
		assertTrue(has(bbs.handle(BBS, "W1XYZ-9", "K " + n, 2L), "KILLED"));
		assertNull(bbs.find(n));
		assertTrue(has(bbs.handle(BBS, "W1XYZ", "K 99", 3L), "NO MSG 99"));
		bbs.post("W1ABC", "ALL", "Bul", "Text", true, 4L);
		int b = bbs.messages().getLast().number();
		assertTrue(has(bbs.handle(BBS, "W1XYZ", "K " + b, 5L), "NOT YOURS"), "a bulletin is only its sender's to kill");
	}

	@Test
	void quietCallersAreForgotten() {
		PacketBbs bbs = new PacketBbs();
		bbs.handle(BBS, "W1ABC", "SP W1XYZ Subj", 0L);
		assertEquals(PacketBbs.Stage.BODY, bbs.session("W1ABC").stage());
		assertTrue(bbs.expire(PacketBbs.SESSION_TIMEOUT_TICKS).isEmpty());
		assertEquals(List.of("W1ABC"), bbs.expire(PacketBbs.SESSION_TIMEOUT_TICKS + 1));
		assertNull(bbs.session("W1ABC"));
		List<String> again = bbs.handle(BBS, "W1ABC", "L", PacketBbs.SESSION_TIMEOUT_TICKS + 2);
		assertTrue(again.getFirst().startsWith("[RCBBS]"), "greeted again after the timeout, and back at the prompt: " + again);
	}

	@Test
	void byeEndsTheSession() {
		PacketBbs bbs = new PacketBbs();
		bbs.handle(BBS, "W1ABC", "L", 0L);
		assertEquals(List.of("73 DE W1BTR-1"), bbs.handle(BBS, "W1ABC", "B", 1L));
		assertNull(bbs.session("W1ABC"));
		assertTrue(has(bbs.handle(BBS, "W1ABC", "XYZ", 2L), "? FOR HELP"));
	}

	@Test
	void everyReplyFitsAnAprsMessage() {
		PacketBbs bbs = new PacketBbs();
		String longLine = "word ".repeat(60);
		bbs.post("W1ABC", "ALL", "S".repeat(80), longLine + "\n" + "x".repeat(250), true, 0L);
		List<String> read = bbs.handle(BBS, "W1XYZ", "R 1", 1L);
		for(String line : read)
			assertTrue(line.length() <= PacketBbs.LINE_MAX, line.length() + ": " + line);
		assertTrue(read.size() >= 7, read.toString());
		assertEquals(List.of("a b", "c"), PacketBbs.split("a b\nc", 100));
		assertEquals(List.of("aaaa", "bb"), PacketBbs.split("aaaa bb", 5));
		assertEquals(List.of("abcde", "fg"), PacketBbs.split("abcdefg", 5));
	}

	@Test
	void bodiesAreCappedAndTheMailboxCanFill() {
		PacketBbs bbs = new PacketBbs();
		bbs.handle(BBS, "W1ABC", "SP W1XYZ Long", 0L);
		String chunk = "y".repeat(90);
		List<String> last = List.of();
		for(int i = 0; i < 20; i++)
			last = bbs.handle(BBS, "W1ABC", chunk, 1L);
		assertTrue(has(last, "TOO LONG"));
		bbs.handle(BBS, "W1ABC", "/EX", 2L);
		assertTrue(bbs.find(1).body().length() <= PacketBbs.BODY_MAX);
		for(int i = 1; i < PacketBbs.MAX_MESSAGES; i++)
			assertNotNull(bbs.post("W1ABC", "ALL", "x", "y", true, 3L));
		assertNull(bbs.post("W1ABC", "ALL", "x", "y", true, 3L));
		assertTrue(has(bbs.handle(BBS, "W1ABC", "SB ALL Full", 4L), "FULL"));
	}

	@Test
	void theMailboxSurvivesNbt() {
		PacketBbs bbs = new PacketBbs();
		int n = send(bbs, "W1ABC", "W1XYZ", "Hi", "Hello\nthere", 42L);
		bbs.post("W1ABC", "ALL", "Bul", "Text", true, 43L);
		PacketBbs back = PacketBbs.fromTag(bbs.toTag());
		assertEquals(bbs.messages(), back.messages());
		assertEquals(n + 2, back.post("K2OTH", "W1ABC", "next", "", false, 44L).number(), "numbering carries on");
		assertTrue(PacketBbs.fromTag(null).messages().isEmpty());
	}

	@Test
	void theBbsCallIsTheBaseCallWithItsSsid() {
		assertEquals("W1BTR-1", PacketBbs.bbsCall("w1btr", 1));
		assertEquals("W1BTR-4", PacketBbs.bbsCall("W1BTR-9", 4));
		assertEquals("W1BTR-15", PacketBbs.bbsCall("W1BTR", 99));
		assertEquals("", PacketBbs.bbsCall("", 1));
	}
}
