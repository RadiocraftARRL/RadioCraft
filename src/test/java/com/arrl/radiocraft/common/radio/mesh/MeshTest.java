package com.arrl.radiocraft.common.radio.mesh;

import com.arrl.radiocraft.common.radio.firealarm.AlarmEvent;
import com.arrl.radiocraft.common.radio.firealarm.ControllerState;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarmMessage;
import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The radio mesh (2026-11-10): link range, fire alarm messages and the controller's log, and SCADA rules. */
class MeshTest {

	private static final double LOS = 500.0D, INTERNAL = 0.25D;

	@Test
	void builtInAntennasReachLessThanExternalOnes() {
		double in = MeshLink.reach(false, INTERNAL), out = MeshLink.reach(true, INTERNAL);
		double both = MeshLink.range(LOS, in, in), mixed = MeshLink.range(LOS, in, out), best = MeshLink.range(LOS, out, out);
		assertTrue(both < mixed && mixed < best, both + " " + mixed + " " + best);
		assertEquals(112.5D, both, 0.01D);
		assertEquals(450.0D, best, 0.01D);
		assertTrue(MeshLink.hears(100.0D, LOS, in, in));
		assertFalse(MeshLink.hears(120.0D, LOS, in, in));
		assertTrue(MeshLink.hears(200.0D, LOS, in, out));
		assertFalse(MeshLink.hears(0.0D, 0.0D, in, in));
	}

	@Test
	void packetsCarryTheirFieldsAndHops() {
		MeshPacket packet = FireAlarmMessage.box(1L, FireAlarmMessage.FIRE, 342, 3, 17, "342 Main St | rear", 8);
		assertEquals(3, FireAlarmMessage.zone(packet));
		assertEquals(17, FireAlarmMessage.seq(packet));
		assertEquals("342 MAIN ST / REAR", FireAlarmMessage.label(packet));
		MeshPacket relayed = packet.relayed();
		assertEquals(1, relayed.hops());
		assertTrue(relayed.mayRelay());
		assertFalse(new MeshPacket(1L, MeshSystem.FIRE, "X", 0, 0, "", 2, 2).mayRelay());
		assertEquals(17, FireAlarmMessage.seq(FireAlarmMessage.ack(2L, 342, 17, 8)));
		assertEquals(packet, MeshPacket.load(packet.save()));
	}

	@Test
	void backoffGrowsToAMinute() {
		assertEquals(60L, FireAlarmMessage.backoff(1));
		assertEquals(120L, FireAlarmMessage.backoff(2));
		assertEquals(1200L, FireAlarmMessage.backoff(20));
		assertFalse(FireAlarmMessage.retriesForever(FireAlarmMessage.CHECKIN));
		assertTrue(FireAlarmMessage.retriesForever(FireAlarmMessage.FIRE));
	}

	@Test
	void controllerLogsAlarmsOnceAndRestores() {
		ControllerState state = new ControllerState();
		MeshPacket fire = FireAlarmMessage.box(1L, FireAlarmMessage.FIRE, 7, 2, 1, "School", 8);
		AlarmEvent event = state.heard(fire, 100L, 6000L);
		assertNotNull(event);
		assertEquals("ZONE 02 FIRE", event.what());
		assertTrue(state.alarm());
		assertTrue(state.needsAttention());
		assertNull(state.heard(fire.relayed(), 120L, 6020L), "A retry of the same message is logged twice");
		assertEquals(1, state.log().size());
		state.silence();
		assertFalse(state.needsAttention());
		AlarmEvent restore = state.heard(FireAlarmMessage.box(2L, FireAlarmMessage.RESTORE, 7, 2, 2, "School", 8), 200L, 6100L);
		assertEquals("ZONE 02 RESTORE", restore.what());
		assertFalse(state.alarm());
		assertFalse(state.needsAttention(), "A restore sounds the buzzer");
		assertEquals(2, state.slipsWaiting());
		assertEquals(ControllerState.Status.NORMAL, state.box(7).status());
		assertNull(state.heard(FireAlarmMessage.box(3L, FireAlarmMessage.CHECKIN, 7, 0, 3, "School", 8), 300L, 6200L), "A check-in is logged");
	}

	@Test
	void silenceHoldsAgainstNewAlarmsForAWhile() {
		ControllerState state = new ControllerState();
		state.heard(FireAlarmMessage.box(1L, FireAlarmMessage.FIRE, 7, 1, 1, "School", 8), 100L, 6000L);
		assertTrue(state.needsAttention());
		state.silence(200L);
		state.heard(FireAlarmMessage.box(2L, FireAlarmMessage.FIRE, 7, 2, 2, "School", 8), 300L, 6200L);
		assertFalse(state.needsAttention(), "A new alarm right after SILENCE sounded again");
		assertTrue(state.alarm(), "SILENCE stopped the alarm");
		state.heard(FireAlarmMessage.box(3L, FireAlarmMessage.FIRE, 7, 3, 3, "School", 8), 200L + ControllerState.SILENCE_HOLD_TICKS, 9000L);
		assertTrue(state.needsAttention(), "A new alarm after the hold stayed quiet");
	}

	@Test
	void silentBoxesGoMissingAndComeBack() {
		ControllerState state = new ControllerState();
		state.heard(FireAlarmMessage.box(1L, FireAlarmMessage.CHECKIN, 9, 0, 1, "Depot", 8), 0L, 0L);
		state.silence();
		assertTrue(state.sweep(ControllerState.MISSING_TICKS - 10, 0L).isEmpty());
		List<AlarmEvent> missing = state.sweep(ControllerState.MISSING_TICKS + 10, 0L);
		assertEquals(1, missing.size());
		assertEquals(AlarmEvent.MISSING, missing.getFirst().type());
		assertEquals(ControllerState.Status.MISSING, state.box(9).status());
		assertTrue(state.needsAttention());
		assertTrue(state.sweep(ControllerState.MISSING_TICKS + 100, 0L).isEmpty(), "MISSING is logged again");
		AlarmEvent back = state.heard(FireAlarmMessage.box(2L, FireAlarmMessage.CHECKIN, 9, 0, 2, "Depot", 8), ControllerState.MISSING_TICKS + 200, 0L);
		assertNotNull(back);
		assertEquals(FireAlarmMessage.CHECKIN, back.type());
		assertEquals(ControllerState.Status.NORMAL, state.box(9).status());
	}

	@Test
	void outOfServiceBoxesRingNoBells() {
		ControllerState state = new ControllerState();
		state.heard(FireAlarmMessage.box(1L, FireAlarmMessage.CHECKIN, 4, 0, 1, "", 8), 0L, 0L);
		state.setOutOfService(4, true);
		state.heard(FireAlarmMessage.box(2L, FireAlarmMessage.FIRE, 4, 1, 2, "", 8), 10L, 0L);
		assertFalse(state.alarm());
		assertFalse(state.needsAttention());
		assertTrue(state.sweep(ControllerState.MISSING_TICKS * 2, 0L).isEmpty());
	}

	@Test
	void slipsSayWhatHappened() {
		AlarmEvent event = new AlarmEvent(0L, 24000L * 11 + 8500L, FireAlarmMessage.FIRE, 342, "FLORENCE ROCHE SCHOOL", 3, 2);
		List<String> slip = event.slip();
		assertEquals("** ZONE 03 FIRE **", slip.get(0));
		assertEquals("DAY 12  14:30", slip.get(1));
		assertEquals("ACCT 0342  R01L1", slip.get(2));
		assertEquals("FLORENCE ROCHE SCHOOL", slip.get(3));
		assertEquals("VIA 2 RELAYS", slip.get(4));
		assertEquals(event, AlarmEvent.load(event.save()));
	}

	@Test
	void scadaRulesParse() {
		ScadaRules.Parsed parsed = ScadaRules.parse(List.of("IF 3.N >= 10 THEN 7.E = 15", "copy 3.north to 0.up  # mirror", "IF 2.D<5 THEN 0.S=1",
				"POLL 3", "", "// nothing", "IF 3.X > 1 THEN 1.N = 1", "POLL 9", "IF 1.N > 1 THEN 1.N = 16"));
		assertEquals(3, parsed.rules().size());
		assertEquals(3, parsed.pollSeconds());
		assertEquals(3, parsed.errors().size(), parsed.errors().toString());
		assertEquals("IF 3.N >= 10 THEN 7.E = 15", parsed.rules().get(0).toString());
		assertEquals("COPY 3.N TO 0.U", parsed.rules().get(1).toString());
		assertEquals(ScadaRules.Op.LT, parsed.rules().get(2).op());
	}

	@Test
	void scadaRulesDriveOutputs() {
		List<ScadaRules.Rule> rules = ScadaRules.parse(List.of("IF 3.N >= 10 THEN 7.E = 15", "IF 3.S > 0 THEN 7.E = 4", "COPY 3.N TO 0.U", "COPY 5.N TO 0.D")).rules();
		ScadaRules.Point north = new ScadaRules.Point(3, 2), south = new ScadaRules.Point(3, 3);
		Map<ScadaRules.Point, Integer> out = ScadaRules.evaluate(rules, p -> p.equals(north) ? 12 : p.equals(south) ? 1 : -1);
		assertEquals(15, out.get(new ScadaRules.Point(7, 5)), "The largest true rule wins");
		assertEquals(12, out.get(new ScadaRules.Point(0, 1)));
		assertEquals(0, out.get(new ScadaRules.Point(0, 0)), "An unknown input drives its output to 0");
		out = ScadaRules.evaluate(rules, p -> p.equals(north) ? 3 : 0);
		assertEquals(0, out.get(new ScadaRules.Point(7, 5)));
	}

	@Test
	void scadaLevelsEncode() {
		int[] levels = {0, 15, -1, 7, 10, -1};
		assertEquals("0f-7a-", ScadaRules.encodeLevels(levels));
		assertArrayEquals(levels, ScadaRules.decodeLevels("0f-7a-"));
		assertArrayEquals(new int[] {-1, -1, -1, -1, -1, -1}, ScadaRules.decodeLevels(""));
	}
}
