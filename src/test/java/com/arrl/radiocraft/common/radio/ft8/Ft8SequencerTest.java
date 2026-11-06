package com.arrl.radiocraft.common.radio.ft8;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FT8's auto-sequencing (computer batch 3B): two sequencers work each other slot by slot, as two computers would, and
 * both log the contact with the reports each gave; lost messages are sent again; an unanswered contact gives up.
 */
class Ft8SequencerTest {

	private static Ft8Sequencer station(String call, String grid) {
		Ft8Sequencer seq = new Ft8Sequencer();
		seq.station(call, grid);
		return seq;
	}

	/** What {@code from} sends this slot, as the other station decodes it at {@code snr}; null if it sends nothing. */
	private static List<Ft8Decode> send(Ft8Sequencer from, long slot, int snr) {
		Ft8Message next = from.next();
		if(next == null)
			return List.of();
		from.sent();
		return List.of(new Ft8Decode(slot, snr, 1500, next.encode()));
	}

	@Test
	void aWholeContactRunsByItselfAndBothLogIt() {
		Ft8Sequencer a = station("W1AAA", "FN31"), b = station("K2BBB", "JJ00");
		a.callCq();
		List<Ft8Decode> heard = send(a, 0L, -10); // A: CQ W1AAA FN31
		assertEquals("CQ W1AAA FN31", heard.getFirst().text());
		b.answer(heard.getFirst()); // B clicks it: B's report on A is -10.
		assertEquals(Ft8Sequencer.Step.REPLIED, b.step());
		heard = send(b, 1L, -14); // B: W1AAA K2BBB JJ00
		assertEquals("W1AAA K2BBB JJ00", heard.getFirst().text());
		assertNull(a.decoded(heard));
		assertEquals(Ft8Sequencer.Step.REPORT_SENT, a.step());
		heard = send(a, 2L, -10); // A: K2BBB W1AAA -14
		assertEquals("K2BBB W1AAA -14", heard.getFirst().text());
		assertNull(b.decoded(heard));
		assertEquals(Ft8Sequencer.Step.R_REPORT_SENT, b.step());
		heard = send(b, 3L, -14); // B: W1AAA K2BBB R-10
		assertEquals("W1AAA K2BBB R-10", heard.getFirst().text());
		Ft8Sequencer.Qso aLogged = a.decoded(heard);
		assertEquals(new Ft8Sequencer.Qso("K2BBB", "JJ00", -14, -10), aLogged, "A logs when it sends RR73");
		heard = send(a, 4L, -10); // A: K2BBB W1AAA RR73
		assertEquals("K2BBB W1AAA RR73", heard.getFirst().text());
		assertEquals(Ft8Sequencer.Step.CALLING_CQ, a.step(), "in CQ mode A calls CQ again");
		Ft8Sequencer.Qso bLogged = b.decoded(heard);
		assertEquals(new Ft8Sequencer.Qso("W1AAA", "FN31", -10, -14), bLogged, "B logs on RR73");
		heard = send(b, 5L, -14); // B: W1AAA K2BBB 73
		assertEquals("W1AAA K2BBB 73", heard.getFirst().text());
		assertEquals(Ft8Sequencer.Step.IDLE, b.step(), "B is done");
		assertNull(b.next());
		assertNull(a.decoded(heard), "the 73 logs nothing more");
		assertEquals("CQ W1AAA FN31", a.next().encode());
	}

	@Test
	void aLostReportIsSentAgain() {
		Ft8Sequencer a = station("W1AAA", "FN31"), b = station("K2BBB", "JJ00");
		a.callCq();
		b.answer(send(a, 0L, -10).getFirst());
		a.decoded(send(b, 1L, -14));
		send(a, 2L, -10); // The report is lost: B never hears it.
		List<Ft8Decode> again = send(b, 3L, -14); // B sends its grid again.
		assertEquals("W1AAA K2BBB JJ00", again.getFirst().text());
		assertNull(a.decoded(again));
		assertTrue(a.acted());
		assertEquals("K2BBB W1AAA -14", a.next().encode(), "A sends the report again");
		assertEquals(Ft8Sequencer.Step.REPORT_SENT, a.step());
	}

	@Test
	void aLostRr73IsAnsweredAfterTheContact() {
		Ft8Sequencer a = station("W1AAA", "FN31"), b = station("K2BBB", "JJ00");
		a.callCq();
		b.answer(send(a, 0L, -10).getFirst());
		a.decoded(send(b, 1L, -14));
		b.decoded(send(a, 2L, -10));
		assertNotNull(a.decoded(send(b, 3L, -14)));
		send(a, 4L, -10); // RR73 lost. A is calling CQ again.
		List<Ft8Decode> again = send(b, 5L, -14); // B repeats its R-report.
		assertEquals("W1AAA K2BBB R-10", again.getFirst().text());
		assertNull(a.decoded(again), "not logged twice");
		assertEquals("K2BBB W1AAA RR73", a.next().encode(), "A answers with RR73 once more");
		a.sent();
		assertEquals("CQ W1AAA FN31", a.next().encode(), "then back to CQ");
	}

	@Test
	void anUnansweredCallGivesUp() {
		Ft8Sequencer b = station("K2BBB", "JJ00");
		b.answer(new Ft8Decode(0L, -10, 1200, "CQ W1AAA FN31"));
		for(int i = 0; i < Ft8Sequencer.MAX_TRIES - 1; i++) {
			b.sent();
			assertEquals(Ft8Sequencer.Step.REPLIED, b.step());
		}
		b.sent();
		assertEquals(Ft8Sequencer.Step.IDLE, b.step(), "gave up after " + Ft8Sequencer.MAX_TRIES);
		assertNull(b.next());
	}

	@Test
	void theStrongestCallerIsAnsweredAndOthersIgnored() {
		Ft8Sequencer a = station("W1AAA", "FN31");
		a.callCq();
		a.sent();
		List<Ft8Decode> callers = new ArrayList<>(List.of(new Ft8Decode(1L, -3, 900, "W1AAA K2BBB JJ00"), new Ft8Decode(1L, -17, 1700, "W1AAA K3CCC JJ01"),
				new Ft8Decode(1L, 0, 2100, "CQ K4DDD JJ02"), new Ft8Decode(1L, 5, 2500, "K5EEE K6FFF -10")));
		a.decoded(Ft8Decode.resolve(callers));
		assertEquals("K2BBB", a.dx());
		assertEquals("K2BBB W1AAA -03", a.next().encode());
	}

	@Test
	void haltStopsAndMessagesForOthersAreIgnored() {
		Ft8Sequencer a = station("W1AAA", "FN31");
		assertNull(a.decoded(List.of(new Ft8Decode(1L, -3, 900, "W1AAA K2BBB JJ00"))), "idle: a caller is not answered");
		assertFalse(a.acted());
		assertNull(a.next());
		a.callCq();
		a.halt();
		assertNull(a.next());
		assertFalse(a.cqMode());
	}

	@Test
	void itSavesAndLoads() {
		Ft8Sequencer a = station("W1AAA", "FN31"), b = station("K2BBB", "JJ00");
		a.callCq();
		b.answer(send(a, 0L, -10).getFirst());
		a.decoded(send(b, 1L, -14));
		CompoundTag tag = a.toTag();
		Ft8Sequencer loaded = station("W1AAA", "FN31");
		loaded.load(tag);
		assertEquals(Ft8Sequencer.Step.REPORT_SENT, loaded.step());
		assertEquals("K2BBB", loaded.dx());
		assertEquals("K2BBB W1AAA -14", loaded.next().encode());
		assertTrue(loaded.cqMode());
	}
}
