package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scheduler's timing and jobs (computer batch 3A): occurrences, catching up once, clocks set back, typed times. */
class ScheduleTest {

	private static Schedule.Job every(int minutes, long anchor) {
		return new Schedule.Job(1, Schedule.Kind.STATION_ID, Schedule.When.EVERY, minutes, true, "", "", "", anchor);
	}

	private static Schedule.Job daily(Schedule.When when, int minutes) {
		return new Schedule.Job(1, Schedule.Kind.REMINDER, when, minutes, true, "", "Net", "", 0L);
	}

	/** Steps a job through {@code times} (one look each), counting the runs. */
	private static int runs(Schedule.Job job, long[] gameTimes, long[] dayTimes, long[] realMinutes) {
		long last = Schedule.UNSET;
		int runs = 0;
		for(int i = 0; i < gameTimes.length; i++) {
			Schedule.Step step = Schedule.step(last, Schedule.occurrence(job, gameTimes[i], dayTimes[i], realMinutes[i]));
			last = step.last();
			if(step.run())
				runs++;
		}
		return runs;
	}

	@Test
	void everyNMinutesCountsFromTheSave() {
		Schedule.Job job = every(10, 5_000L);
		assertEquals(0L, Schedule.occurrence(job, 5_000L, 0L, 0L));
		assertEquals(0L, Schedule.occurrence(job, 5_000L + 11_999L, 0L, 0L));
		assertEquals(1L, Schedule.occurrence(job, 5_000L + 12_000L, 0L, 0L));
		// Looked at once a second for 35 minutes: 3 runs, the first ten minutes after the save.
		long[] times = new long[35 * 60];
		for(int i = 0; i < times.length; i++)
			times[i] = 5_000L + i * 20L;
		assertEquals(3, runs(job, times, new long[times.length], new long[times.length]));
	}

	@Test
	void theFirstLookNeverRuns() {
		Schedule.Step first = Schedule.step(Schedule.UNSET, 42L);
		assertFalse(first.run());
		assertEquals(42L, first.last());
		assertTrue(Schedule.step(42L, 43L).run());
		assertFalse(Schedule.step(43L, 43L).run());
	}

	@Test
	void aLongAbsenceRunsOnceAndAClockSetBackNever() {
		assertTrue(Schedule.step(3L, 50L).run(), "back after hours: due");
		assertEquals(50L, Schedule.step(3L, 50L).last(), "and only once: the count catches up");
		Schedule.Step back = Schedule.step(50L, 10L);
		assertFalse(back.run(), "the world's clock set back runs nothing");
		assertEquals(10L, back.last(), "and counts from there");
	}

	@Test
	void dailyOnTheWorldsClock() {
		// The world's clock: 0 ticks is 6:00, so 14000 ticks is 20:00.
		assertEquals(6 * 60L, Schedule.gameMinutes(0L));
		assertEquals(20 * 60L, Schedule.gameMinutes(14_000L));
		assertEquals(24 * 60L + 6 * 60L, Schedule.gameMinutes(24_000L));
		Schedule.Job net = daily(Schedule.When.DAILY_GAME, 20 * 60);
		long before = Schedule.occurrence(net, 0L, 13_983L, 0L), after = Schedule.occurrence(net, 0L, 14_000L, 0L);
		assertEquals(before + 1L, after, "20:00 passes between 19:59 and 20:00");
		assertEquals(after + 1L, Schedule.occurrence(net, 0L, 38_000L, 0L), "next day's 20:00");
		// Three world days at a look a second (20 ticks): three reminders.
		long[] day = new long[3 * 24_000 / 20];
		for(int i = 0; i < day.length; i++)
			day[i] = 1_000L + i * 20L;
		assertEquals(3, runs(net, new long[day.length], day, new long[day.length]));
	}

	@Test
	void dailyOnTheRealClock() {
		Schedule.Job net = daily(Schedule.When.DAILY_REAL, 8 * 60 + 30);
		long midnight = 20_000L * 24L * 60L; // Some day's midnight, in minutes since the epoch.
		assertEquals(Schedule.occurrence(net, 0L, 0L, midnight + 8 * 60 + 29) + 1L, Schedule.occurrence(net, 0L, 0L, midnight + 8 * 60 + 30));
		long[] minutes = new long[2 * 24 * 60];
		for(int i = 0; i < minutes.length; i++)
			minutes[i] = midnight + i;
		assertEquals(2, runs(net, new long[minutes.length], new long[minutes.length], minutes), "two real days, two runs");
	}

	@Test
	void secondsToTheNextRun() {
		assertEquals(600L, Schedule.secondsToNext(every(10, 0L), 0L, 0L, 0L));
		assertEquals(1L, Schedule.secondsToNext(every(10, 0L), 11_980L, 0L, 0L));
		// 19:00 on the world's clock (13000 ticks); 20:00 is 1000 ticks, 50 seconds, away.
		assertEquals(50L, Schedule.secondsToNext(daily(Schedule.When.DAILY_GAME, 20 * 60), 0L, 13_000L, 0L));
		// 08:00 real; 08:30 is half an hour away.
		assertEquals(1800L, Schedule.secondsToNext(daily(Schedule.When.DAILY_REAL, 8 * 60 + 30), 0L, 0L, 8L * 3600L));
		assertEquals(24L * 3600L, Schedule.secondsToNext(daily(Schedule.When.DAILY_REAL, 8 * 60), 0L, 0L, 8L * 3600L), "just now: tomorrow");
	}

	@Test
	void timesAreTyped() {
		assertArrayEquals(new Object[] { Schedule.When.EVERY, 30 }, Schedule.parseWhen("30"));
		assertArrayEquals(new Object[] { Schedule.When.EVERY, 30 }, Schedule.parseWhen(" every 30 "));
		assertArrayEquals(new Object[] { Schedule.When.EVERY, 120 }, Schedule.parseWhen("every 2h"));
		assertArrayEquals(new Object[] { Schedule.When.EVERY, 15 }, Schedule.parseWhen("15 min"));
		assertArrayEquals(new Object[] { Schedule.When.DAILY_GAME, 20 * 60 }, Schedule.parseWhen("at 20:00"));
		assertArrayEquals(new Object[] { Schedule.When.DAILY_GAME, 6 * 60 + 5 }, Schedule.parseWhen("06:05"));
		assertArrayEquals(new Object[] { Schedule.When.DAILY_REAL, 20 * 60 + 5 }, Schedule.parseWhen("at 8:05p real"));
		assertArrayEquals(new Object[] { Schedule.When.DAILY_REAL, 5 }, Schedule.parseWhen("AT 12:05AM REAL"));
		for(String bad : new String[] { "", "soon", "0", "every 0", "every 25h", "at 24:00", "at 7:60", "at 13:00p", "at 30", "real", null })
			assertNull(Schedule.parseWhen(bad), String.valueOf(bad));
		for(Schedule.When when : Schedule.When.values())
			for(int minutes : new int[] { 1, 59, 60, 90, 1439 }) {
				if(when == Schedule.When.EVERY && minutes > Schedule.MAX_EVERY_MINUTES)
					continue;
				Object[] read = Schedule.parseWhen(Schedule.typed(when, minutes));
				assertNotNull(read, when + " " + minutes);
				assertEquals(when, read[0]);
				assertEquals(minutes, read[1]);
			}
	}

	@Test
	void jobsAreCheckedAndCleaned() {
		Schedule.Job chart = new Schedule.Job(0, Schedule.Kind.WEFAX, Schedule.When.DAILY_GAME, 6 * 60, true, "wx.img", "", "", 0L);
		assertEquals("WX.IMG", chart.file());
		assertNull(chart.problem());
		assertEquals("sched_need_image", new Schedule.Job(0, Schedule.Kind.WEFAX, Schedule.When.EVERY, 5, true, "WX.TXT", "", "", 0L).problem());
		assertEquals("sched_need_text", new Schedule.Job(0, Schedule.Kind.REMINDER, Schedule.When.EVERY, 5, true, "", "  ", "", 0L).problem());
		Schedule.Job reminder = new Schedule.Job(0, Schedule.Kind.REMINDER, Schedule.When.EVERY, 99_999, true, "", "x".repeat(300), "w1 abc", 0L);
		assertEquals(Schedule.MAX_EVERY_MINUTES, reminder.minutes());
		assertEquals(Schedule.TEXT_MAX, reminder.text().length());
		assertEquals("W1ABC", reminder.to());
		assertEquals(23 * 60 + 59, new Schedule.Job(0, Schedule.Kind.REMINDER, Schedule.When.DAILY_GAME, -1, true, "", "a", "", 0L).minutes());
	}

	@Test
	void theListKeepsItsIdsAndItsCap() {
		List<Schedule.Job> jobs = new ArrayList<>();
		for(int i = 0; i < Schedule.MAX_JOBS; i++) {
			jobs = Schedule.put(jobs, every(5, 0L).withId(0));
			assertNotNull(jobs);
		}
		assertEquals(Schedule.MAX_JOBS, jobs.size());
		assertEquals(Schedule.MAX_JOBS, jobs.getLast().id());
		assertNull(Schedule.put(jobs, every(5, 0L).withId(0)), "past the cap");
		List<Schedule.Job> replaced = Schedule.put(jobs, every(7, 0L).withId(3));
		assertNotNull(replaced);
		assertEquals(7, replaced.get(2).minutes(), "id 3 replaced in place");
		List<Schedule.Job> fewer = Schedule.remove(jobs, 3);
		assertEquals(Schedule.MAX_JOBS - 1, fewer.size());
		assertEquals(3, Schedule.nextId(fewer));
		// Through the file and back.
		List<Schedule.Job> read = Schedule.fromTag(Schedule.toTag(fewer));
		assertEquals(fewer, read);
		assertTrue(Schedule.fromTag(null).isEmpty());
	}
}
