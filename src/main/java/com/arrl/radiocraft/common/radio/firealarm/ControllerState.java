package com.arrl.radiocraft.common.radio.firealarm;

import com.arrl.radiocraft.common.radio.mesh.MeshNode;
import com.arrl.radiocraft.common.radio.mesh.MeshPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What a fire alarm controller knows (2026-11-10), kept with the mesh so it goes on logging while its chunk is not
 * loaded: its log, every box it has heard and how each one stands, whether the buzzer has been silenced, and the slips
 * waiting for the printer. Pure, apart from the NBT.
 */
public final class ControllerState {

	/** Most events kept in the log. */
	public static final int LOG_SIZE = 200;
	/** Most slips waiting for paper. */
	public static final int PRINT_QUEUE = 32;
	/** A box not heard from for this long is MISSING: a day and a half, so one missed check-in is not enough. */
	public static final long MISSING_TICKS = 36000L;
	/** How long a box message's sequence number is remembered, so a retry whose ack was lost is not logged twice. */
	public static final long DUPLICATE_TICKS = 24000L;

	public enum Status { NORMAL, ALARM, TROUBLE, MISSING }

	/** One box as the controller knows it. */
	public static final class Box {
		public final int address;
		public String label = "";
		public long lastHeard;
		public final TreeSet<Integer> alarmZones = new TreeSet<>();
		public boolean tamper = false;
		public boolean missing = false;
		public boolean outOfService = false;

		Box(int address) {
			this.address = address;
		}

		public Status status() {
			if(!alarmZones.isEmpty())
				return Status.ALARM;
			if(tamper)
				return Status.TROUBLE;
			return missing ? Status.MISSING : Status.NORMAL;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("address", address);
			tag.putString("label", label);
			tag.putLong("lastHeard", lastHeard);
			tag.put("zones", new IntArrayTag(alarmZones.stream().mapToInt(Integer::intValue).toArray()));
			tag.putBoolean("tamper", tamper);
			tag.putBoolean("missing", missing);
			tag.putBoolean("outOfService", outOfService);
			return tag;
		}

		static Box load(CompoundTag tag) {
			Box box = new Box(tag.getInt("address"));
			box.label = tag.getString("label");
			box.lastHeard = tag.getLong("lastHeard");
			for(int zone : tag.getIntArray("zones"))
				box.alarmZones.add(zone);
			box.tamper = tag.getBoolean("tamper");
			box.missing = tag.getBoolean("missing");
			box.outOfService = tag.getBoolean("outOfService");
			return box;
		}
	}

	private final Deque<AlarmEvent> log = new ArrayDeque<>();
	private final Map<Integer, Box> boxes = new TreeMap<>();
	private final Map<Long, Long> seen = new HashMap<>(); // box << 32 | seq -> when.
	private final Deque<AlarmEvent> toPrint = new ArrayDeque<>();
	private boolean silenced = true;
	/** Game time SILENCE was last pressed: for {@link #SILENCE_HOLD_TICKS} after, new alarms are logged but stay quiet. */
	private long silencedAt = Long.MIN_VALUE / 2;
	/** How long a SILENCE holds against new alarms (2026-11-11): five minutes. */
	public static final long SILENCE_HOLD_TICKS = 6000L;

	/** The log, oldest first. */
	public List<AlarmEvent> log() {
		return new ArrayList<>(log);
	}

	/** The newest event, or null. */
	public AlarmEvent latest() {
		return log.peekLast();
	}

	public Collection<Box> boxes() {
		return Collections.unmodifiableCollection(boxes.values());
	}

	public Box box(int address) {
		return boxes.get(address);
	}

	/** True if any box is in alarm (the bells ring until it is silenced). */
	public boolean alarm() {
		for(Box box : boxes.values())
			if(!box.outOfService && box.status() == Status.ALARM)
				return true;
		return false;
	}

	/** True while there is a FIRE, TAMPER or MISSING nobody has acknowledged: the buzzer sounds. */
	public boolean needsAttention() {
		return !silenced;
	}

	/** SILENCE with no time: the buzzer and bells go quiet until the next alarm. */
	public void silence() {
		silenced = true;
	}

	/**
	 * Someone pressed SILENCE at game time {@code now} (2026-11-11, was STOP): the buzzer and bells go quiet, and stay
	 * quiet for {@link #SILENCE_HOLD_TICKS} whatever comes in (it is still logged and printed). The alarm itself stays:
	 * the boxes are in alarm until their stations are reset.
	 */
	public void silence(long now) {
		silenced = true;
		silencedAt = now;
	}

	/** The next slip waiting for the printer, without taking it. */
	public AlarmEvent nextSlip() {
		return toPrint.peekFirst();
	}

	/** The printer printed the next slip. */
	public void printed() {
		toPrint.pollFirst();
	}

	public int slipsWaiting() {
		return toPrint.size();
	}

	/** Places a box out of service (true) or back in: an out-of-service box rings no bells and is never MISSING. */
	public void setOutOfService(int address, boolean out) {
		Box box = boxes.get(address);
		if(box != null)
			box.outOfService = out;
	}

	/**
	 * A box's message reached the controller at {@code now} (day time {@code dayTime}). The event it makes, or null if
	 * it was a retry already logged or a plain check-in. The caller acks it either way.
	 */
	public AlarmEvent heard(MeshPacket packet, long now, long dayTime) {
		int seq = FireAlarmMessage.seq(packet);
		long key = ((long)packet.from() << 32) | (seq & 0xFFFFFFFFL);
		boolean duplicate = seq >= 0 && seen.containsKey(key);
		seen.put(key, now);
		Box box = boxes.computeIfAbsent(packet.from(), Box::new);
		String label = MeshNode.clean(FireAlarmMessage.label(packet));
		if(!label.isEmpty())
			box.label = label;
		box.lastHeard = now;
		AlarmEvent event = null;
		if(box.missing) { // Heard again.
			box.missing = false;
			event = new AlarmEvent(now, dayTime, FireAlarmMessage.CHECKIN, box.address, box.label, 0, packet.hops());
		}
		if(duplicate)
			return add(event);
		int zone = FireAlarmMessage.zone(packet);
		String zoneName = FireAlarmMessage.zoneName(packet);
		AlarmKind kind = FireAlarmMessage.kind(packet);
		if(!packet.type().equals(FireAlarmMessage.TAMPER))
			box.tamper = false; // A box that speaks again has been put back.
		switch(packet.type()) {
			case FireAlarmMessage.FIRE -> {
				box.alarmZones.add(zone);
				event = new AlarmEvent(now, dayTime, packet.type(), box.address, box.label, zone, packet.hops(), zoneName, kind);
			}
			case FireAlarmMessage.RESTORE -> {
				box.alarmZones.remove(zone);
				event = new AlarmEvent(now, dayTime, packet.type(), box.address, box.label, zone, packet.hops(), zoneName, kind);
			}
			case FireAlarmMessage.TAMPER -> {
				box.tamper = true;
				event = new AlarmEvent(now, dayTime, packet.type(), box.address, box.label, 0, packet.hops());
			}
			case FireAlarmMessage.TEST -> event = new AlarmEvent(now, dayTime, packet.type(), box.address, box.label, zone, packet.hops(), zoneName);
			default -> {} // A check-in only says the box is there.
		}
		return add(event);
	}

	/** Every {@code now}: boxes not heard from for {@link #MISSING_TICKS} go MISSING. The events it makes. */
	public List<AlarmEvent> sweep(long now, long dayTime) {
		List<AlarmEvent> out = new ArrayList<>();
		for(Box box : boxes.values())
			if(!box.missing && !box.outOfService && now - box.lastHeard > MISSING_TICKS) {
				box.missing = true;
				out.add(add(new AlarmEvent(now, dayTime, AlarmEvent.MISSING, box.address, box.label, 0, 0)));
			}
		Iterator<Map.Entry<Long, Long>> it = seen.entrySet().iterator();
		while(it.hasNext())
			if(now - it.next().getValue() > DUPLICATE_TICKS)
				it.remove();
		return out;
	}

	/** Forgets a box (taken off the list in the app). */
	public void forget(int address) {
		boxes.remove(address);
	}

	private AlarmEvent add(AlarmEvent event) {
		if(event == null)
			return null;
		Box box = boxes.get(event.box());
		boolean ignored = box != null && box.outOfService;
		log.addLast(event);
		while(log.size() > LOG_SIZE)
			log.pollFirst();
		if(event.printed()) {
			toPrint.addLast(event);
			while(toPrint.size() > PRINT_QUEUE)
				toPrint.pollFirst();
		}
		if(event.needsAttention() && !ignored && event.time() - silencedAt >= SILENCE_HOLD_TICKS)
			silenced = false;
		return event;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag events = new ListTag();
		for(AlarmEvent event : log)
			events.add(event.save());
		tag.put("log", events);
		ListTag list = new ListTag();
		for(Box box : boxes.values())
			list.add(box.save());
		tag.put("boxes", list);
		ListTag slips = new ListTag();
		for(AlarmEvent event : toPrint)
			slips.add(event.save());
		tag.put("print", slips);
		tag.putBoolean("silenced", silenced);
		tag.putLong("silencedAt", silencedAt);
		return tag;
	}

	public static ControllerState load(CompoundTag tag) {
		ControllerState state = new ControllerState();
		ListTag events = tag.getList("log", Tag.TAG_COMPOUND);
		for(int i = 0; i < events.size(); i++)
			state.log.addLast(AlarmEvent.load(events.getCompound(i)));
		ListTag list = tag.getList("boxes", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++) {
			Box box = Box.load(list.getCompound(i));
			state.boxes.put(box.address, box);
		}
		ListTag slips = tag.getList("print", Tag.TAG_COMPOUND);
		for(int i = 0; i < slips.size(); i++)
			state.toPrint.addLast(AlarmEvent.load(slips.getCompound(i)));
		state.silenced = !tag.contains("silenced") || tag.getBoolean("silenced");
		if(tag.contains("silencedAt"))
			state.silencedAt = tag.getLong("silencedAt");
		return state;
	}
}
