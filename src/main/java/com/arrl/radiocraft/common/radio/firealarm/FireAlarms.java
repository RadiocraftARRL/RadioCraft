package com.arrl.radiocraft.common.radio.firealarm;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.radio.mesh.MeshNode;
import com.arrl.radiocraft.common.radio.mesh.MeshPacket;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import com.arrl.radiocraft.common.radio.mesh.RadioMesh;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * The radio fire alarm (2026-11-10), in the overworld's {@code radiocraft_fire_alarms} data so it runs whether the
 * boxes' and controllers' chunks are loaded or not. Each box keeps a queue of messages it is sending: the head goes out,
 * and again after a growing wait ({@link FireAlarmMessage#backoff}) until a controller acks it; its lamp blinks
 * meanwhile. Once a day each box checks in. Each controller keeps a {@link ControllerState}: what it heard, which boxes
 * are in alarm, and MISSING for a box not heard from in a day and a half.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class FireAlarms extends SavedData {

	public static final String SAVE_NAME = "radiocraft_fire_alarms";
	/** Ticks between a box's check-ins: one game day. */
	public static final long CHECK_IN_TICKS = 24000L;
	/** How soon a newly set up box checks in, so its controller lists it. */
	public static final long FIRST_CHECK_IN_TICKS = 40L;
	/** Tries a check-in gets before the box waits for tomorrow's. */
	public static final int CHECK_IN_TRIES = 3;
	/** Ticks a controller waits before it acks. */
	public static final int ACK_DELAY_TICKS = 4;

	private static final class Pending {
		final String type;
		final int zone;
		final int seq;
		final String name; // The station's name, sent with it.
		AlarmKind kind = AlarmKind.FIRE; // And its kind of alarm.
		int tries = 0;
		long nextAt;

		Pending(String type, int zone, int seq, long nextAt) {
			this(type, zone, seq, nextAt, "");
		}

		Pending(String type, int zone, int seq, long nextAt, String name) {
			this.type = type;
			this.zone = zone;
			this.seq = seq;
			this.nextAt = nextAt;
			this.name = name;
		}
	}

	private static final class BoxQueue {
		int seq = 0;
		long nextCheckIn;
		final Deque<Pending> pending = new ArrayDeque<>();
	}

	private final Map<GlobalPos, BoxQueue> boxes = new LinkedHashMap<>();
	private final Map<GlobalPos, ControllerState> controllers = new LinkedHashMap<>();
	private final Random random = new Random();

	public static SavedData.Factory<FireAlarms> factory() {
		return new SavedData.Factory<>(FireAlarms::new, FireAlarms::load, null);
	}

	public static FireAlarms of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_NAME);
	}

	public static int hopLimit() {
		return RadiocraftServerConfig.MESH_HOP_LIMIT.get();
	}

	// -------------------- BOXES --------------------

	/** A box was set up (or set to a new frequency): it checks in shortly. */
	public void boxReady(MinecraftServer server, GlobalPos pos) {
		BoxQueue queue = boxes.computeIfAbsent(pos, p -> new BoxQueue());
		queue.nextCheckIn = server.overworld().getGameTime() + FIRST_CHECK_IN_TICKS;
		setDirty();
	}

	/** The box at {@code pos} sends {@code type} for {@code zone}, until acked. */
	public void report(MinecraftServer server, GlobalPos pos, String type, int zone) {
		report(server, pos, type, zone, "");
	}

	/** As above, for station {@code zone} named {@code name} (blank for none): the name goes with it. */
	public void report(MinecraftServer server, GlobalPos pos, String type, int zone, String name) {
		report(server, pos, type, zone, name, AlarmKind.FIRE);
	}

	/** As above, for a location whose alarm is {@code kind} (MEDICAL, LOCKDOWN...). */
	public void report(MinecraftServer server, GlobalPos pos, String type, int zone, String name, AlarmKind kind) {
		if(RadioMesh.of(server).get(pos) == null)
			return; // No frequency yet: nothing goes out.
		BoxQueue queue = boxes.computeIfAbsent(pos, p -> new BoxQueue());
		queue.seq = (queue.seq + 1) & 0xFFFF;
		Pending pending = new Pending(type, zone, queue.seq, server.overworld().getGameTime(), name);
		pending.kind = kind;
		queue.pending.addLast(pending);
		setDirty();
	}

	/** True while the box at {@code pos} has a message out that no controller has acked (not counting its check-in). */
	public boolean waiting(GlobalPos pos) {
		BoxQueue queue = boxes.get(pos);
		if(queue == null)
			return false;
		for(Pending pending : queue.pending)
			if(!pending.type.equals(FireAlarmMessage.CHECKIN))
				return true;
		return false;
	}

	/** Messages the box at {@code pos} has waiting for an ack. */
	public int queued(GlobalPos pos) {
		BoxQueue queue = boxes.get(pos);
		return queue == null ? 0 : queue.pending.size();
	}

	/** The box at {@code pos} is gone: its last burst is TAMPER, then it is off the mesh. */
	public void boxRemoved(MinecraftServer server, GlobalPos pos) {
		RadioMesh mesh = RadioMesh.of(server);
		MeshNode node = mesh.get(pos);
		if(node != null) {
			BoxQueue queue = boxes.computeIfAbsent(pos, p -> new BoxQueue());
			mesh.send(server, pos, FireAlarmMessage.box(mesh.newId(), FireAlarmMessage.TAMPER, node.address(), 0, (queue.seq + 1) & 0xFFFF, node.label(), hopLimit()));
			mesh.remove(pos);
		}
		boxes.remove(pos);
		setDirty();
	}

	private void acked(GlobalPos pos, int seq) {
		BoxQueue queue = boxes.get(pos);
		if(queue != null && queue.pending.removeIf(p -> p.seq == seq))
			setDirty();
	}

	// -------------------- CONTROLLERS --------------------

	/** What the controller at {@code pos} knows (made empty if new). */
	public ControllerState controller(GlobalPos pos) {
		return controllers.computeIfAbsent(pos, p -> {
			setDirty();
			return new ControllerState();
		});
	}

	@Nullable
	public ControllerState controllerIfAny(GlobalPos pos) {
		return controllers.get(pos);
	}

	public void controllerRemoved(MinecraftServer server, GlobalPos pos) {
		RadioMesh.of(server).remove(pos);
		if(controllers.remove(pos) != null)
			setDirty();
	}

	// -------------------- ON THE AIR --------------------

	/** What a fire alarm device does with a burst it hears ({@link RadioMesh.Listener}). */
	public static boolean heard(MinecraftServer server, MeshNode at, MeshPacket packet, long now) {
		FireAlarms alarms = of(server);
		if(at.role() == MeshRole.BOX) {
			if(packet.type().equals(FireAlarmMessage.ACK) && packet.to() == at.address()) {
				alarms.acked(at.pos(), FireAlarmMessage.seq(packet));
				return true;
			}
			return false;
		}
		if(at.role() != MeshRole.CONTROLLER || packet.type().equals(FireAlarmMessage.ACK))
			return true;
		ControllerState state = alarms.controller(at.pos());
		state.heard(packet, now, server.overworld().getDayTime());
		alarms.setDirty();
		RadioMesh mesh = RadioMesh.of(server);
		mesh.sendLater(server, at.pos(), FireAlarmMessage.ack(mesh.newId(), packet.from(), FireAlarmMessage.seq(packet), hopLimit()), ACK_DELAY_TICKS);
		return true;
	}

	private void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		RadioMesh mesh = RadioMesh.of(server);
		Iterator<Map.Entry<GlobalPos, BoxQueue>> it = boxes.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<GlobalPos, BoxQueue> entry = it.next();
			MeshNode node = mesh.get(entry.getKey());
			if(node == null || node.role() != MeshRole.BOX) {
				it.remove();
				setDirty();
				continue;
			}
			BoxQueue queue = entry.getValue();
			if(now >= queue.nextCheckIn) {
				queue.nextCheckIn = now + CHECK_IN_TICKS;
				if(queue.pending.stream().noneMatch(p -> p.type.equals(FireAlarmMessage.CHECKIN))) {
					queue.seq = (queue.seq + 1) & 0xFFFF;
					queue.pending.addLast(new Pending(FireAlarmMessage.CHECKIN, 0, queue.seq, now));
				}
				setDirty();
			}
			Pending head = queue.pending.peekFirst();
			if(head == null || now < head.nextAt)
				continue;
			mesh.send(server, node.pos(), FireAlarmMessage.box(mesh.newId(), head.type, node.address(), head.zone, head.seq, node.label(), head.name, head.kind, hopLimit()));
			head.tries++;
			if(!FireAlarmMessage.retriesForever(head.type) && head.tries >= CHECK_IN_TRIES)
				queue.pending.pollFirst();
			else
				head.nextAt = now + FireAlarmMessage.backoff(head.tries) + random.nextInt(20);
			setDirty();
		}
		if(now % 20L == 0L) {
			Iterator<Map.Entry<GlobalPos, ControllerState>> controllersIt = controllers.entrySet().iterator();
			while(controllersIt.hasNext()) {
				Map.Entry<GlobalPos, ControllerState> entry = controllersIt.next();
				MeshNode node = mesh.get(entry.getKey());
				if(node == null || node.role() != MeshRole.CONTROLLER) {
					continue; // Set up but no frequency yet, or its chunk not loaded since: kept.
				}
				if(!entry.getValue().sweep(now, server.overworld().getDayTime()).isEmpty())
					setDirty();
			}
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		of(event.getServer()).tick(event.getServer());
	}

	// -------------------- SAVE --------------------

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		ListTag list = new ListTag();
		for(Map.Entry<GlobalPos, BoxQueue> entry : boxes.entrySet()) {
			CompoundTag box = new CompoundTag();
			MeshNode.savePos(box, entry.getKey());
			BoxQueue queue = entry.getValue();
			box.putInt("seq", queue.seq);
			box.putLong("nextCheckIn", queue.nextCheckIn);
			ListTag pending = new ListTag();
			for(Pending p : queue.pending) {
				CompoundTag message = new CompoundTag();
				message.putString("type", p.type);
				message.putInt("zone", p.zone);
				message.putInt("seq", p.seq);
				message.putInt("tries", p.tries);
				message.putLong("nextAt", p.nextAt);
				message.putString("name", p.name);
				message.putString("kind", p.kind.name());
				pending.add(message);
			}
			box.put("pending", pending);
			list.add(box);
		}
		tag.put("boxes", list);
		ListTag controllerList = new ListTag();
		for(Map.Entry<GlobalPos, ControllerState> entry : controllers.entrySet()) {
			CompoundTag controller = entry.getValue().save();
			MeshNode.savePos(controller, entry.getKey());
			controllerList.add(controller);
		}
		tag.put("controllers", controllerList);
		return tag;
	}

	public static FireAlarms load(CompoundTag tag, HolderLookup.Provider registries) {
		FireAlarms alarms = new FireAlarms();
		ListTag list = tag.getList("boxes", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++) {
			CompoundTag box = list.getCompound(i);
			GlobalPos pos = MeshNode.loadPos(box);
			if(pos == null)
				continue;
			BoxQueue queue = new BoxQueue();
			queue.seq = box.getInt("seq");
			queue.nextCheckIn = box.getLong("nextCheckIn");
			ListTag pending = box.getList("pending", Tag.TAG_COMPOUND);
			for(int j = 0; j < pending.size(); j++) {
				CompoundTag message = pending.getCompound(j);
				Pending p = new Pending(message.getString("type"), message.getInt("zone"), message.getInt("seq"), message.getLong("nextAt"), message.getString("name"));
				p.tries = message.getInt("tries");
				p.kind = AlarmKind.of(message.getString("kind"));
				queue.pending.addLast(p);
			}
			alarms.boxes.put(pos, queue);
		}
		ListTag controllerList = tag.getList("controllers", Tag.TAG_COMPOUND);
		for(int i = 0; i < controllerList.size(); i++) {
			CompoundTag controller = controllerList.getCompound(i);
			GlobalPos pos = MeshNode.loadPos(controller);
			if(pos != null)
				alarms.controllers.put(pos, ControllerState.load(controller));
		}
		return alarms;
	}
}
