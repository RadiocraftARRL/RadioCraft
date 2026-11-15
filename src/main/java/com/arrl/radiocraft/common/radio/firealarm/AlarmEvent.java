package com.arrl.radiocraft.common.radio.firealarm;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One line of a fire alarm controller's log (2026-11-10), as its screen shows it and its printer prints it. Pure.
 *
 * @param time    Game time it came in.
 * @param dayTime The overworld's day time then (for the day and clock on the slip).
 * @param type    FIRE, RESTORE, TEST, TAMPER, MISSING or CHECKIN (a box heard again after it went missing).
 * @param box     The box number.
 * @param label   The box's location.
 * @param zone    The zone (pull station) number; 0 for the box's own redstone input or a message about the whole box.
 * @param hops    Boxes it was relayed through on the way.
 * @param zoneName The station's name ("KITCHEN", set on the box's panel, 2026-11-11), empty for none.
 * @param kind     What the location's alarm is (FIRE, MEDICAL, LOCKDOWN..., 2026-11-11).
 */
public record AlarmEvent(long time, long dayTime, String type, int box, String label, int zone, int hops, String zoneName, AlarmKind kind) {

	public AlarmEvent(long time, long dayTime, String type, int box, String label, int zone, int hops) {
		this(time, dayTime, type, box, label, zone, hops, "", AlarmKind.FIRE);
	}

	public AlarmEvent(long time, long dayTime, String type, int box, String label, int zone, int hops, String zoneName) {
		this(time, dayTime, type, box, label, zone, hops, zoneName, AlarmKind.FIRE);
	}

	/** The controller's receiver and line, as a DMP prints it (one receiver, one radio line). */
	public static final String LINE = "R01L1";

	/** True for events the buzzer sounds for until someone acknowledges them. */
	public boolean needsAttention() {
		return type.equals(FireAlarmMessage.FIRE) || type.equals(FireAlarmMessage.TAMPER) || type.equals(MISSING);
	}

	/** True for events the printer prints a slip for. */
	public boolean printed() {
		return !type.equals(FireAlarmMessage.CHECKIN);
	}

	/** What the controller logs when a box has not checked in for a day and a half. */
	public static final String MISSING = "MISSING";

	/** Day number (1 on the first day) of {@code dayTime}. */
	public static long day(long dayTime) {
		return dayTime / 24000L + 1L;
	}

	/** The clock at {@code dayTime}, "HH:MM" (day time 0 is 06:00). */
	public static String clock(long dayTime) {
		long minutes = ((dayTime + 6000L) % 24000L) * 1440L / 24000L;
		return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
	}

	/** "FIRE", "ZONE 03 FIRE" and so on: what happened, as the screen shows it. */
	public String what() {
		String name = switch(type) {
			case FireAlarmMessage.FIRE -> kind.name(); // FIRE, MEDICAL, LOCKDOWN...
			case FireAlarmMessage.RESTORE -> "RESTORE";
			case FireAlarmMessage.TEST -> "TEST";
			case FireAlarmMessage.TAMPER -> "TAMPER";
			case MISSING -> "MISSING";
			case FireAlarmMessage.CHECKIN -> "CHECK-IN RESTORED";
			default -> type;
		};
		if((type.equals(FireAlarmMessage.FIRE) || type.equals(FireAlarmMessage.RESTORE)))
			return zone == 0 ? name + " DETECTOR" : String.format(Locale.ROOT, "ZONE %02d %s", zone, name);
		return name;
	}

	/** The account (box) number as printed, "0342". */
	public String account() {
		return String.format(Locale.ROOT, "%04d", box);
	}

	/** One line for a log list: "D12 14:32 0342 ZONE 03 FIRE". */
	public String logLine() {
		return String.format(Locale.ROOT, "D%d %s %s %s", day(dayTime), clock(dayTime), account(), what());
	}

	/** The lines of the slip the printer prints. */
	public List<String> slip() {
		List<String> out = new ArrayList<>();
		out.add(needsAttention() ? "** " + what() + " **" : what());
		out.add("DAY " + day(dayTime) + "  " + clock(dayTime));
		out.add("ACCT " + account() + "  " + LINE);
		if(!label.isEmpty())
			out.add(label);
		if(!zoneName.isEmpty())
			out.add("LOCATION: " + zoneName);
		out.add(hops == 0 ? "DIRECT" : "VIA " + hops + (hops == 1 ? " RELAY" : " RELAYS"));
		return out;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("time", time);
		tag.putLong("dayTime", dayTime);
		tag.putString("type", type);
		tag.putInt("box", box);
		tag.putString("label", label);
		tag.putInt("zone", zone);
		tag.putInt("hops", hops);
		tag.putString("zoneName", zoneName);
		tag.putString("kind", kind.name());
		return tag;
	}

	public static AlarmEvent load(CompoundTag tag) {
		return new AlarmEvent(tag.getLong("time"), tag.getLong("dayTime"), tag.getString("type"), tag.getInt("box"), tag.getString("label"),
				tag.getInt("zone"), tag.getInt("hops"), tag.getString("zoneName"), AlarmKind.of(tag.getString("kind")));
	}
}
