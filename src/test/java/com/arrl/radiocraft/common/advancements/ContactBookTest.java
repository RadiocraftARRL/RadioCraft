package com.arrl.radiocraft.common.advancements;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verified two-way contacts: both ways within five minutes, distance between the stations, dupes, bounded memory. */
class ContactBookTest {

	private static final UUID A = new UUID(1L, 1L);
	private static final UUID B = new UUID(2L, 2L);
	private static final String OVERWORLD = "minecraft:overworld";
	private static final float HF = 14_200_000.0F;
	private static final float VHF = 146_520_000.0F;

	private static ContactBook.Heard heard(UUID hearer, UUID talker, int x, int y, int z, float hz, long time) {
		return new ContactBook.Heard(hearer, talker, OVERWORLD, x, y, z, hz, hz < 30e6 ? "20m" : "2m", time);
	}

	@Test
	void oneWayIsNoContact() {
		ContactBook book = new ContactBook();
		assertNull(book.heard(heard(B, A, 0, 64, 0, HF, 100L)));
		assertNull(book.heard(heard(B, A, 0, 64, 0, HF, 200L)), "Hearing the same station again is still one way");
	}

	@Test
	void bothWaysWithinFiveMinutesIsAContact() {
		ContactBook book = new ContactBook();
		assertNull(book.heard(heard(B, A, 1200, 64, 0, HF, 100L))); // B (at 1200, 0) heard A.
		ContactBook.Contact contact = book.heard(heard(A, B, 0, 64, 0, HF, 100L + ContactBook.WINDOW_TICKS)); // A (at 0, 0) heard B.
		assertNotNull(contact);
		assertEquals(1200.0D, contact.distance(), 1e-9, "The distance is between the two hearers' stations");
		assertTrue(contact.hf());
		assertTrue(contact.counts());
		assertTrue(List.of(contact.a(), contact.b()).containsAll(List.of(A, B)));
	}

	@Test
	void tooLateIsNoContact() {
		ContactBook book = new ContactBook();
		book.heard(heard(B, A, 0, 64, 0, HF, 100L));
		assertNull(book.heard(heard(A, B, 0, 64, 0, HF, 101L + ContactBook.WINDOW_TICKS)));
	}

	@Test
	void hearingYourselfIsNothing() {
		ContactBook book = new ContactBook();
		assertNull(book.heard(heard(A, A, 0, 64, 0, HF, 100L)));
		assertEquals(0, book.size());
	}

	@Test
	void aPairIsLookedAtAgainOnlyAfterAWhileAndCountsOnceABandADay() {
		ContactBook book = new ContactBook();
		book.heard(heard(B, A, 0, 64, 0, HF, 0L));
		assertNotNull(book.heard(heard(A, B, 0, 64, 0, HF, 20L)));
		assertNull(book.heard(heard(B, A, 0, 64, 0, HF, 40L)), "Rechecked too soon");
		ContactBook.Contact again = book.heard(heard(B, A, 0, 64, 0, HF, 20L + ContactBook.RECHECK_TICKS));
		assertNotNull(again, "Looked at again after the recheck time");
		assertFalse(again.counts(), "The same pair on the same band the same day is a dupe");
		ContactBook.Contact otherBand = book.heard(heard(A, B, 0, 64, 0, VHF, 20L + 2 * ContactBook.RECHECK_TICKS));
		assertNotNull(otherBand);
		assertTrue(otherBand.counts(), "Another band counts");
		book.heard(heard(B, A, 0, 64, 0, HF, ContactBook.DUPE_TICKS + 30L));
		ContactBook.Contact tomorrow = book.heard(heard(A, B, 0, 64, 0, HF, ContactBook.DUPE_TICKS + 40L));
		assertNotNull(tomorrow);
		assertTrue(tomorrow.counts(), "A day later it counts again");
	}

	@Test
	void earnedFollowsDistanceBandHeightTimeAndSun() {
		ContactBook.Contact nearVhf = new ContactBook.Contact(heard(B, A, 0, 64, 0, VHF, 0L), heard(A, B, 500, 64, 0, VHF, 10L), true);
		assertEquals(List.of(RadiocraftAdvancements.FIRST_CONTACT), ContactBook.earned(nearVhf, 6000L, false));

		ContactBook.Contact skip = new ContactBook.Contact(heard(B, A, 0, 64, 0, HF, 0L), heard(A, B, 1500, 64, 0, HF, 10L), true);
		assertTrue(ContactBook.earned(skip, 6000L, false).contains(RadiocraftAdvancements.SKIP_DAY));
		assertFalse(ContactBook.earned(skip, 6000L, false).contains(RadiocraftAdvancements.DX_HUNTER));

		ContactBook.Contact farVhf = new ContactBook.Contact(heard(B, A, 0, 64, 0, VHF, 0L), heard(A, B, 6000, 64, 0, VHF, 10L), true);
		List<String> far = ContactBook.earned(farVhf, 6000L, false);
		assertTrue(far.contains(RadiocraftAdvancements.DX_HUNTER), "DX Hunter on any band");
		assertFalse(far.contains(RadiocraftAdvancements.SKIP_DAY), "Skip Day is HF only");

		ContactBook.Contact high = new ContactBook.Contact(heard(B, A, 0, 201, 0, VHF, 0L), heard(A, B, 10, 250, 0, VHF, 10L), true);
		assertTrue(ContactBook.earned(high, 6000L, false).contains(RadiocraftAdvancements.MOONBOUNCE));
		ContactBook.Contact oneHigh = new ContactBook.Contact(heard(B, A, 0, 200, 0, VHF, 0L), heard(A, B, 10, 250, 0, VHF, 10L), true);
		assertFalse(ContactBook.earned(oneHigh, 6000L, false).contains(RadiocraftAdvancements.MOONBOUNCE), "Both must be above Y 200");

		assertTrue(ContactBook.earned(nearVhf, 12_000L, false).contains(RadiocraftAdvancements.GREY_LINE), "Dusk");
		assertTrue(ContactBook.earned(nearVhf, 23_500L, true).contains(RadiocraftAdvancements.INTO_THE_STATIC));
	}

	@Test
	void greyLineIsDawnAndDusk() {
		assertTrue(ContactBook.isGreyLine(0L));
		assertTrue(ContactBook.isGreyLine(23_000L));
		assertTrue(ContactBook.isGreyLine(24_000L * 5 + 500L));
		assertTrue(ContactBook.isGreyLine(12_500L));
		assertFalse(ContactBook.isGreyLine(6_000L), "Noon");
		assertFalse(ContactBook.isGreyLine(18_000L), "Midnight");
	}

	@Test
	void otherDimensionHasNoDistance() {
		ContactBook.Contact contact = new ContactBook.Contact(new ContactBook.Heard(B, A, "minecraft:the_nether", 0, 64, 0, HF, "20m", 0L),
				heard(A, B, 9000, 64, 0, HF, 10L), true);
		assertEquals(Double.POSITIVE_INFINITY, contact.distance());
		assertFalse(ContactBook.earned(contact, 6000L, false).contains(RadiocraftAdvancements.DX_HUNTER));
	}

	@Test
	void memoryIsBoundedAndOldHearingsGo() {
		ContactBook book = new ContactBook();
		for(int i = 0; i < ContactBook.MAX_HEARD + 500; i++)
			book.heard(heard(new UUID(100L, i), A, 0, 64, 0, HF, 10L));
		assertTrue(book.size() <= ContactBook.MAX_HEARD, "At most MAX_HEARD hearings: " + book.size());
		book.heard(heard(B, A, 0, 64, 0, HF, 10L + ContactBook.WINDOW_TICKS + 2000L));
		assertEquals(1, book.size(), "Hearings older than the window are dropped");
	}
}
