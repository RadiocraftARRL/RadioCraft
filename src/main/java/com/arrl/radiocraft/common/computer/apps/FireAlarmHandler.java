package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmControllerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.firealarm.AlarmEvent;
import com.arrl.radiocraft.common.radio.firealarm.ControllerState;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarms;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The FIRE ALARM program's server side (2026-11-10): the fire alarm controller touching the computer or on its data
 * wire.
 * <ul>
 * <li>{@code status}: {@code controller}; then {@code mhz}, {@code license}, {@code attention}, {@code alarm},
 * {@code paper}, {@code jammed}, {@code slips}, {@code boxes} (by number: {@code box}, {@code label}, {@code status},
 * {@code zones}, {@code ago} in ticks since it was heard, {@code out} if out of service) and {@code log} (newest first:
 * {@code line}, {@code label}, {@code type}, {@code attention}).</li>
 * <li>{@code silence}: acknowledges, as STOP on the controller.</li>
 * <li>{@code service} ({@code box}, {@code out}): places a box out of service or back in.</li>
 * <li>{@code forget} ({@code box}): takes a box off the list.</li>
 * </ul>
 */
public final class FireAlarmHandler implements ComputerAppHandler {

	/** Log lines sent. */
	public static final int LOG_LINES = 60;

	@Nullable
	public static FireAlarmControllerBlockEntity controller(ComputerContext context) {
		List<FireAlarmControllerBlockEntity> found = context.linked(FireAlarmControllerBlockEntity.class);
		return found.isEmpty() ? null : found.getFirst();
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		FireAlarmControllerBlockEntity controller = controller(context);
		if(controller == null) {
			if(action.equals("status")) {
				CompoundTag none = new CompoundTag();
				none.putBoolean("controller", false);
				reply.ok(none);
			}
			else
				reply.error("fire_no_controller");
			return;
		}
		ControllerState state = FireAlarms.of(context.server()).controller(controller.globalPos());
		switch(action) {
			case "status" -> reply.ok(status(context.server(), controller, state, LOG_LINES));
			case "silence" -> {
				controller.acknowledge();
				reply.ok();
			}
			case "service" -> {
				state.setOutOfService(data.getInt("box"), data.getBoolean("out"));
				FireAlarms.of(context.server()).setDirty();
				reply.ok();
			}
			case "map" -> reply.ok(map(context, controller, state)); // The box map (2026-11-13).
			case "forget" -> {
				state.forget(data.getInt("box"));
				FireAlarms.of(context.server()).setDirty();
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/**
	 * The box map: terrain round the controller and every box it knows whose place the mesh has, and those places
	 * ({@code boxes}: {@code box}, {@code x}, {@code z}); the program draws the boxes in their status colours.
	 */
	private static CompoundTag map(ComputerContext context, FireAlarmControllerBlockEntity controller, ControllerState state) {
		net.minecraft.core.BlockPos home = controller.getBlockPos();
		net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension = controller.globalPos().dimension();
		com.arrl.radiocraft.common.radio.mesh.RadioMesh mesh = com.arrl.radiocraft.common.radio.mesh.RadioMesh.of(context.server());
		List<net.minecraft.core.BlockPos> at = new java.util.ArrayList<>();
		ListTag places = new ListTag();
		for(ControllerState.Box box : state.boxes()) {
			com.arrl.radiocraft.common.radio.mesh.MeshNode node = controller.getFrequency() > 0
					? mesh.find(com.arrl.radiocraft.common.radio.mesh.MeshRole.BOX, controller.getFrequency(), box.address) : null;
			if(node == null || !node.pos().dimension().equals(dimension))
				continue;
			at.add(node.pos().pos());
			CompoundTag p = new CompoundTag();
			p.putInt("box", box.address);
			p.putInt("x", node.pos().pos().getX());
			p.putInt("z", node.pos().pos().getZ());
			places.add(p);
		}
		com.arrl.radiocraft.common.radio.data.AprsMapPainter.Fit fit = com.arrl.radiocraft.common.radio.data.AprsMapPainter.fit(home, at,
				com.arrl.radiocraft.common.radio.data.AprsMapPainter.SIZE, com.arrl.radiocraft.common.radio.data.AprsMapPainter.SIZE);
		net.minecraft.server.level.ServerLevel level = context.server().getLevel(dimension);
		byte[] pixels = com.arrl.radiocraft.common.radio.data.AprsMapPainter.terrain(level == null ? context.level() : level, fit);
		com.arrl.radiocraft.common.radio.data.AprsMapPainter.home(pixels, fit, home.getX() + 0.5D, home.getZ() + 0.5D);
		CompoundTag out = new CompoundTag();
		out.putByteArray("pixels", pixels);
		out.putInt("cx", fit.centerX());
		out.putInt("cz", fit.centerZ());
		out.putInt("bpp", fit.blocksPerPixel());
		out.put("boxes", places);
		return out;
	}

	/**
	 * The controller as the FIRE ALARM program and its own screen show it, with the newest {@code logLines} events; each
	 * event also in parts for the screen ({@code what}, {@code account}, {@code zone}, {@code day}, {@code clock},
	 * {@code hops}), and {@code day}/{@code clock} now.
	 */
	public static CompoundTag status(net.minecraft.server.MinecraftServer server, FireAlarmControllerBlockEntity controller, ControllerState state, int logLines) {
		CompoundTag out = new CompoundTag();
		out.putBoolean("controller", true);
		out.putString("mhz", controller.getFrequency() > 0 ? CommercialLicenseRules.mhz(controller.getFrequency()) : "");
		out.putString("license", controller.getLicense());
		out.putBoolean("attention", state.needsAttention());
		out.putBoolean("alarm", state.alarm());
		out.putInt("paper", controller.getPaper());
		out.putBoolean("jammed", controller.isJammed());
		out.putInt("slips", state.slipsWaiting());
		long now = server.overworld().getGameTime();
		long dayTime = server.overworld().getDayTime();
		out.putLong("dayNow", AlarmEvent.day(dayTime));
		out.putString("clockNow", AlarmEvent.clock(dayTime));
		out.putLong("dayTimeNow", dayTime);
		out.putBoolean("silenced", state.alarm() && !state.needsAttention());
		ListTag boxes = new ListTag();
		for(ControllerState.Box box : state.boxes()) {
			CompoundTag b = new CompoundTag();
			b.putInt("box", box.address);
			b.putString("label", box.label);
			b.putString("status", box.status().name());
			b.put("zones", new IntArrayTag(box.alarmZones.stream().mapToInt(Integer::intValue).toArray()));
			b.putLong("ago", Math.max(0L, now - box.lastHeard));
			b.putBoolean("out", box.outOfService);
			boxes.add(b);
		}
		out.put("boxes", boxes);
		ListTag log = new ListTag();
		List<AlarmEvent> events = state.log();
		for(int i = events.size() - 1; i >= 0 && log.size() < logLines; i--) {
			AlarmEvent event = events.get(i);
			CompoundTag e = new CompoundTag();
			e.putString("line", event.logLine());
			e.putString("label", event.label());
			e.putString("type", event.type());
			e.putBoolean("attention", event.needsAttention());
			e.putString("what", event.what());
			e.putString("account", event.account());
			e.putInt("zone", event.zone());
			e.putString("zoneName", event.zoneName());
			e.putString("kind", event.kind().name());
			e.putLong("day", AlarmEvent.day(event.dayTime()));
			e.putString("clock", AlarmEvent.clock(event.dayTime()));
			e.putLong("dayTime", event.dayTime());
			e.putInt("hops", event.hops());
			log.add(e);
		}
		out.put("log", log);
		return out;
	}
}
