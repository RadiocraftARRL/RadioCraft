package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.mesh.ScadaMasterBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.mesh.MeshNode;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import com.arrl.radiocraft.common.radio.mesh.RadioMesh;
import com.arrl.radiocraft.common.radio.scada.Scada;
import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The SCADA program's server side (2026-11-10): the SCADA master touching the computer or on its data wire, its points
 * and its rules.
 * <ul>
 * <li>{@code status}: {@code master}; then {@code mhz}, {@code license}, {@code poll} (seconds an RTU), {@code modes}
 * (the master's faces, "FNU..." for OFF, IN, OUT), {@code levels} (its faces' levels), {@code units} (by id: {@code id},
 * {@code inputs} as six hex digits or '-', {@code outputs} the rules want, {@code fail}, {@code polls}, {@code replies},
 * {@code ago} in ticks since its last reply, -1 if never), {@code rules} and {@code errors} (text).</li>
 * <li>{@code add} ({@code text}): adds a rule line. {@code delete} ({@code index}): removes one. {@code poll} ({@code n}, seconds 1 to 3).</li>
 * </ul>
 */
public final class ScadaHandler implements ComputerAppHandler {

	@Nullable
	public static ScadaMasterBlockEntity master(ComputerContext context) {
		List<ScadaMasterBlockEntity> found = context.linked(ScadaMasterBlockEntity.class);
		return found.isEmpty() ? null : found.getFirst();
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		ScadaMasterBlockEntity device = master(context);
		if(device == null) {
			if(action.equals("status")) {
				CompoundTag none = new CompoundTag();
				none.putBoolean("master", false);
				reply.ok(none);
			}
			else
				reply.error("scada_no_master");
			return;
		}
		Scada.Master master = device.master(context.server());
		switch(action) {
			case "status" -> reply.ok(status(context.server(), device, master));
			case "add" -> {
				String text = data.getString("text").trim();
				ScadaRules.Parsed parsed = ScadaRules.parse(List.of(text));
				if(!parsed.errors().isEmpty() || (parsed.rules().isEmpty() && parsed.pollSeconds() == 0)) {
					reply.error("scada_bad_rule");
					return;
				}
				List<String> lines = new ArrayList<>(master.ruleText);
				lines.add(text);
				device.setRules(context.server(), lines);
				reply.ok();
			}
			case "delete" -> {
				int index = data.getInt("index");
				List<String> lines = new ArrayList<>(master.ruleText);
				if(index >= 0 && index < lines.size()) {
					lines.remove(index);
					device.setRules(context.server(), lines);
				}
				reply.ok();
			}
			case "trend" -> { // 2026-11-13: one unit's readings over time, a hex digit a reading per face.
				int unit = data.getInt("unit");
				Scada.Trend trend = unit == 0 ? master.trend : master.rtus.containsKey(unit) ? master.rtus.get(unit).trend : null;
				CompoundTag out = new CompoundTag();
				out.putInt("unit", unit);
				ListTag faces = new ListTag();
				for(int f = 0; f < 6; f++)
					faces.add(StringTag.valueOf(trend == null ? "" : trend.face(f)));
				out.put("faces", faces);
				out.putLong("span", trend == null || trend.size() < 2 ? 0L : (trend.time(trend.size() - 1) - trend.time(0)) / 20L);
				out.putLong("ago", trend == null || trend.size() == 0 ? -1L : Math.max(0L, context.server().overworld().getGameTime() - trend.time(trend.size() - 1)) / 20L);
				reply.ok(out);
			}
			case "poll" -> {
				master.pollSeconds = Math.max(ScadaRules.MIN_POLL, Math.min(ScadaRules.MAX_POLL, data.getInt("n")));
				Scada.of(context.server()).changed();
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/** The master as the SCADA program and the console's own screen show it. */
	public static CompoundTag status(net.minecraft.server.MinecraftServer server, ScadaMasterBlockEntity device, Scada.Master master) {
		CompoundTag out = new CompoundTag();
		out.putBoolean("master", true);
		out.putString("mhz", device.getFrequency() > 0 ? CommercialLicenseRules.mhz(device.getFrequency()) : "");
		out.putString("license", device.getLicense());
		out.putString("label", device.label());
		out.putInt("poll", master.pollSeconds);
		out.putBoolean("powered", master.powered);
		StringBuilder modes = new StringBuilder();
		for(Scada.FaceMode mode : master.modes)
			modes.append(mode.name().charAt(1));
		out.putString("modes", modes.toString());
		int[] levels = new int[6];
		for(int i = 0; i < 6; i++)
			levels[i] = master.modes[i] == Scada.FaceMode.IN ? master.inputs[i] : master.modes[i] == Scada.FaceMode.OUT ? master.outputs[i] : -1;
		out.putString("levels", ScadaRules.encodeLevels(levels));
		long now = server.overworld().getGameTime();
		Map<Integer, Scada.Seen> units = new TreeMap<>(master.rtus);
		if(device.getFrequency() > 0)
			for(MeshNode node : RadioMesh.of(server).on(MeshRole.RTU, device.getFrequency()))
				units.putIfAbsent(node.address(), null);
		ListTag list = new ListTag();
		for(Map.Entry<Integer, Scada.Seen> entry : units.entrySet()) {
			Scada.Seen seen = entry.getValue();
			CompoundTag u = new CompoundTag();
			u.putInt("id", entry.getKey());
			u.putString("inputs", seen == null ? "------" : ScadaRules.encodeLevels(seen.inputs));
			int[] wanted = new int[6];
			for(int i = 0; i < 6; i++)
				wanted[i] = master.wanted.getOrDefault(new ScadaRules.Point(entry.getKey(), i), -1);
			u.putString("outputs", ScadaRules.encodeLevels(wanted));
			u.putBoolean("fail", seen != null && seen.commFail);
			u.putInt("polls", seen == null ? 0 : seen.polls);
			u.putInt("replies", seen == null ? 0 : seen.replies);
			u.putLong("ago", seen == null || seen.lastReply < 0 ? -1L : Math.max(0L, now - seen.lastReply));
			list.add(u);
		}
		out.put("units", list);
		ListTag rules = new ListTag();
		for(String line : master.ruleText)
			rules.add(StringTag.valueOf(line));
		out.put("rules", rules);
		ListTag errors = new ListTag();
		for(String line : master.ruleErrors)
			errors.add(StringTag.valueOf(line));
		out.put("errors", errors);
		out.putBoolean("polling", master.polling());
		out.putBoolean("sending", master.polling() && now - master.pollStartedAt() < 6L);
		out.putInt("link", (int)Math.round(100.0D * RadioMesh.of(server).bestLink(device.globalPos())));
		return out;
	}
}
