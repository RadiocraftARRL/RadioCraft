package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.be_networks.network_objects.SatelliteTrackerNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.satellite.Moon;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatelliteMode;
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import com.arrl.radiocraft.common.radio.satellite.SatelliteRadio;
import com.arrl.radiocraft.common.radio.satellite.SatelliteRules;
import com.arrl.radiocraft.common.radio.satellite.SatelliteTelemetry;
import com.arrl.radiocraft.common.radio.satellite.Satellites;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SATTRACK's server side (2026-11-11, {@code doc/SatellitePlan.md}; modes, telemetry, Doppler and eclipses 2026-11-12):
 * every satellite with its next passes over the computer, and its owner's controls. No antenna needed at the computer
 * for the list; its telemetry needs a radio on the computer's station bus tuned to the telemetry frequency (the frames
 * are decoded and the latest kept per satellite). Anyone sees the list; only the owner and the players on its allowed
 * list change a satellite, and only while it is passing over the computer.
 * <ul>
 * <li>{@code status}: {@code overworld} (false: no satellites over another dimension), {@code doppler} (the server
 * shifts frequencies), {@code satellites}: each with {@code name}, {@code amateur}, {@code up}/{@code down}/{@code tlm}
 * (MHz), {@code tone}, {@code on}, {@code id}, {@code mode}, {@code aprs} (MHz) with {@code aprs_warn} on terrestrial
 * APRS, {@code image} (the SSTV picture's name), {@code mail} (messages in its mailbox), {@code owner}, {@code allowed}
 * (names), {@code launched}, {@code days} left, {@code warn} near the decay, {@code mine} (this player may control it),
 * {@code passing} (up over the computer now, with {@code el}/{@code az}, {@code shift} the downlink's Doppler shift in Hz
 * and {@code eclipse} if it is silent in the Earth's shadow), {@code telemetry} (the last frame heard here, decoded:
 * {@code volts}, {@code amps}, {@code temp}, {@code sun}, {@code mode}, {@code dk}, {@code low}, {@code ago} seconds),
 * {@code passes} (each {@code in} ticks to its rise, 0 if up now; {@code el} its highest; {@code from} the compass point
 * it rises in; {@code ticks} it is up).</li>
 * <li>{@code set} ({@code name}, {@code field}: {@code on} with {@code on}, {@code id} with {@code text}, {@code tone}
 * with {@code tone}, {@code allow} or {@code disallow} with {@code player}, {@code mode} with {@code mode},
 * {@code aprs} with {@code mhz} (0 for the downlink), {@code image} with {@code file} (an .IMG on the computer),
 * {@code decommission} with {@code confirm} its name (its owner alone, after the app has asked twice; 2026-11-12)).
 * A decommissioned satellite takes no more changes ({@code sat_decommissioned}).</li>
 * </ul>
 * Since 2026-11-12 each satellite also has {@code geo} (geostationary: always up here or never, no passes), {@code dead}
 * (decommissioned) and {@code owner_is_me}; and {@code stealth}. A stealth satellite someone else owns comes with
 * {@code hidden}: only its name, {@code geo}, {@code dead} and where it is. {@code set} takes {@code stealth} with
 * {@code on} (commercial only; on only while a tracker on the station's coax is locked on it).
 */
public final class SatTrackHandler implements ComputerAppHandler {



	/** Passes listed per satellite. */
	public static final int PASSES = 4;

	/** A telemetry frame heard, and when (game time). */
	public record Heard(SatelliteTelemetry telemetry, long at) {}

	/** One computer's telemetry heard, per satellite. Not saved: telemetry is live. */
	public static final class State implements ComputerAppState {
		final Map<String, Heard> heard = new HashMap<>();

		public Map<String, Heard> heard() {
			return Map.copyOf(heard);
		}
	}

	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.SATTRACK, State::new);
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		switch(action) {
			case "status" -> reply.ok(status(context));
			case "set" -> set(context, data, reply);
			case "track" -> track(context, data, reply);
			default -> reply.error("unknown_request", action);
		}
	}

	/** A telemetry frame a radio on the computer's bus decoded: kept as the satellite's latest. */
	@Override
	public void receive(RadioComputerBlockEntity computer, TncPacket packet) {
		if(packet.kind() != TncPacket.Kind.TELEMETRY || computer.getLevel() == null)
			return;
		SatelliteTelemetry telemetry = SatelliteTelemetry.decode(packet.text());
		if(telemetry != null)
			state(computer).heard.put(packet.from(), new Heard(telemetry, computer.getLevel().getGameTime()));
	}

	private static CompoundTag status(ComputerContext context) {
		CompoundTag out = new CompoundTag();
		boolean overworld = context.level().dimension() == Level.OVERWORLD;
		out.putBoolean("overworld", overworld);
		boolean doppler = Satellites.dopplerOn();
		out.putBoolean("doppler", doppler);
		ListTag list = new ListTag();
		MinecraftServer server = context.server();
		long now = server.overworld().getGameTime();
		long millis = System.currentTimeMillis();
		BlockPos at = context.pos();
		Map<String, Heard> heard = state(context.computer()).heard;
		List<SatelliteTrackerNetworkObject> trackers = trackers(context);
		out.putBoolean("trackers", !trackers.isEmpty());
		String chosen = trackers.isEmpty() ? "" : trackers.getFirst().getChosen();
		out.putString("chosen", chosen);
		if(overworld) { // The Moon first (2026-11-12): moonbounce through a tracker on it.
			CompoundTag moon = new CompoundTag();
			moon.putString("name", Moon.NAME);
			moon.putBoolean("moon", true);
			SatelliteOrbit.Look look = Moon.look(context.level().getTimeOfDay(1.0F));
			moon.putBoolean("passing", Moon.usable(context.level().getTimeOfDay(1.0F)));
			moon.putDouble("el", look.elevation());
			moon.putDouble("az", look.azimuth());
			moon.putBoolean("chosen", chosen.equals(Moon.NAME));
			moon.putBoolean("locked", locked(trackers, Moon.NAME));
			list.add(moon);
		}
		for(Satellite s : Satellites.of(server).all()) {
			CompoundTag e = new CompoundTag();
			e.putString("name", s.name);
			e.putInt("art", s.art());
			boolean mine = s.mayControl(context.player().getUUID(), context.player().getGameProfile().getName());
			e.putBoolean("stealth", s.stealth());
			e.putBoolean("chosen", chosen.equals(s.name));
			e.putBoolean("locked", locked(trackers, s.name));
			if(s.stealth() && !mine) { // Stealth (2026-11-12): its name and where it is, nothing more.
				e.putBoolean("hidden", true);
				e.putBoolean("geo", s.geostationary());
				e.putBoolean("dead", s.decommissioned());
				where(e, s, context, overworld, at, now, doppler, server);
				list.add(e);
				continue;
			}
			e.putBoolean("amateur", s.amateur);
			e.putString("up", CommercialLicenseRules.mhz(s.uplinkHz));
			e.putString("down", CommercialLicenseRules.mhz(s.downlinkHz));
			e.putString("tlm", CommercialLicenseRules.mhz(s.telemetryHz()));
			e.putString("tone", Ctcss.format(s.tone()));
			e.putFloat("tone_hz", s.tone());
			e.putBoolean("on", s.isOn());
			e.putString("id", s.idText());
			e.putString("mode", s.mode().name());
			e.putString("aprs", CommercialLicenseRules.mhz(s.aprsHz()));
			e.putBoolean("aprs_warn", SatelliteRules.aprsNotRecommended(s.aprsHz()));
			e.putString("image", s.imageName());
			e.putInt("mail", s.mailboxCount());
			e.putString("owner", s.ownerName);
			ListTag allowed = new ListTag();
			for(String player : s.allowed())
				allowed.add(StringTag.valueOf(player));
			e.put("allowed", allowed);
			e.putString("launched", s.launchDay);
			e.putLong("days", s.daysLeft(millis));
			e.putBoolean("warn", s.decayNear(millis));
			e.putBoolean("geo", s.geostationary());
			e.putBoolean("dead", s.decommissioned());
			e.putBoolean("owner_is_me", s.owner.equals(context.player().getUUID()));
			e.putBoolean("mine", mine);
			Heard last = heard.get(TncPacket.sanitizeCallsign(s.name));
			if(last != null) {
				SatelliteTelemetry t = last.telemetry();
				CompoundTag tlm = new CompoundTag();
				tlm.putDouble("volts", t.volts());
				tlm.putDouble("amps", t.amps());
				tlm.putInt("temp", t.tempC());
				tlm.putBoolean("sun", t.sunlit());
				tlm.putString("mode", t.mode());
				tlm.putLong("dk", t.daysLeft());
				tlm.putBoolean("low", t.low());
				tlm.putBoolean("saving", t.powerSaving());
				tlm.putLong("ago", RadioStation.secondsSince(context.level().getGameTime(), last.at()));
				e.put("telemetry", tlm);
			}
			where(e, s, context, overworld, at, now, doppler, server);
			list.add(e);
		}
		out.put("satellites", list);
		return out;
	}

	/** Where {@code s} is from the computer: passing (and its look), and its next passes. */
	private static void where(CompoundTag e, Satellite s, ComputerContext context, boolean overworld, BlockPos at, long now, boolean doppler, MinecraftServer server) {
		{
			if(overworld) {
				SatelliteOrbit.Look look = s.look(at.getX(), at.getZ(), now);
				e.putBoolean("passing", look.up());
				if(look.up()) {
					e.putDouble("el", look.elevation());
					e.putDouble("az", look.azimuth());
					if(doppler)
						e.putDouble("shift", s.dopplerHz(s.downlinkHz, at.getX(), at.getZ(), now));
					e.putBoolean("eclipse", SatelliteRadio.silent(s, server.overworld(), now));
				}
				if(!s.geostationary()) { // Where it is round its track, for the Location view (2026-11-13).
					double[] track = s.orbit.track(at.getX(), at.getZ(), now);
					e.putDouble("along", track[0]);
					e.putDouble("across", track[1]);
					e.putDouble("along_speed", s.orbit.alongSpeed());
					e.putDouble("across_speed", s.orbit.acrossSpeed());
				}
				ListTag passes = new ListTag();
				for(SatelliteOrbit.Pass pass : s.nextPasses(at.getX(), at.getZ(), now, PASSES)) {
					CompoundTag p = new CompoundTag();
					p.putLong("in", Math.max(0L, (long)Math.ceil(pass.rise() - now)));
					p.putDouble("el", pass.maxElevation());
					p.putString("from", SatelliteOrbit.compass(pass.riseAzimuth()));
					p.putLong("ticks", (long)Math.ceil(pass.set() - Math.max(now, pass.rise())));
					passes.add(p);
				}
				e.put("passes", passes);
			}
		}
	}

	/** The satellite tracking antennas on the coax of the station's radios. */
	private static List<SatelliteTrackerNetworkObject> trackers(ComputerContext context) {
		List<SatelliteTrackerNetworkObject> out = new java.util.ArrayList<>();
		for(com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity radio : RadioStation.radios(RadioStation.bus(context.computer())))
			if(com.arrl.radiocraft.api.capabilities.IBENetworks.getObject(context.level(), radio.getBlockPos()) instanceof com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject network)
				for(com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject antenna : network.getAntennas())
					if(antenna instanceof SatelliteTrackerNetworkObject tracker && !out.contains(tracker))
						out.add(tracker);
		return out;
	}

	private static boolean locked(List<SatelliteTrackerNetworkObject> trackers, String name) {
		for(SatelliteTrackerNetworkObject tracker : trackers)
			if(com.arrl.radiocraft.common.radio.satellite.SatelliteTracking.lockedOn(tracker.getPos().asLong(), name))
				return true;
		return false;
	}

	/**
	 * {@code track} ({@code name}: a satellite, {@link Moon#NAME}, or empty for automatic): points every tracking antenna
	 * on the station's coax at it (2026-11-12). Anyone at the computer may: it is the station's own antenna.
	 */
	private static void track(ComputerContext context, CompoundTag data, ComputerReply reply) {
		List<SatelliteTrackerNetworkObject> trackers = trackers(context);
		if(trackers.isEmpty()) {
			reply.error("sat_no_tracker");
			return;
		}
		String name = data.getString("name");
		if(!name.isEmpty() && !name.equalsIgnoreCase(Moon.NAME) && Satellites.of(context.server()).get(name) == null) {
			reply.error("sat_unknown", name);
			return;
		}
		for(SatelliteTrackerNetworkObject tracker : trackers)
			tracker.setChosen(name);
		reply.ok();
	}

	private static void set(ComputerContext context, CompoundTag data, ComputerReply reply) {
		MinecraftServer server = context.server();
		Satellites satellites = Satellites.of(server);
		Satellite s = satellites.get(data.getString("name"));
		if(s == null) {
			reply.error("sat_unknown", data.getString("name"));
			return;
		}
		if(s.decommissioned()) {
			reply.error("sat_decommissioned", s.name);
			return;
		}
		if(!s.mayControl(context.player().getUUID(), context.player().getGameProfile().getName())) {
			reply.error("sat_not_yours", s.name);
			return;
		}
		String field = data.getString("field");
		if(field.equals("decommission") && !s.owner.equals(context.player().getUUID())) { // Its owner's alone, not the allowed list's.
			reply.error("sat_owner_only", s.name);
			return;
		}
		BlockPos at = context.pos();
		if(context.level().dimension() != Level.OVERWORLD || !s.look(at.getX(), at.getZ(), server.overworld().getGameTime()).up()) {
			reply.error("sat_not_passing", s.name);
			return;
		}
		switch(field) {
			case "on" -> satellites.setOn(server, s, data.getBoolean("on"));
			case "stealth" -> { // 2026-11-12: commercial only, and on only through a tracker locked on it.
				boolean on = data.getBoolean("on");
				if(s.amateur) {
					reply.error("sat_stealth_commercial", s.name);
					return;
				}
				if(on && !RadioStation.trackerLockedOn(context.level(), RadioStation.radios(RadioStation.bus(context.computer())), s.name)) {
					reply.error("sat_stealth_no_tracker", s.name);
					return;
				}
				satellites.setStealth(server, s, on);
			}
			case "decommission" -> { // 2026-11-12: the app asks twice first.
				if(!data.getString("confirm").equals(s.name)) {
					reply.error("unknown_request", field);
					return;
				}
				satellites.decommission(server, s);
			}
			case "id" -> satellites.setIdText(server, s, data.getString("text"));
			case "tone" -> satellites.setTone(server, s, data.getFloat("tone"));
			case "allow" -> {
				if(!satellites.allow(server, s, data.getString("player"))) {
					reply.error("sat_allow_failed", data.getString("player"));
					return;
				}
			}
			case "disallow" -> {
				if(!satellites.disallow(server, s, data.getString("player"))) {
					reply.error("sat_not_allowed", data.getString("player"));
					return;
				}
			}
			case "mode" -> {
				SatelliteMode mode = SatelliteMode.parse(data.getString("mode"));
				if(mode == null) {
					reply.error("unknown_request", data.getString("mode"));
					return;
				}
				if(!satellites.setMode(server, s, mode)) {
					reply.error(s.amateur ? "sat_no_imaging" : "sat_no_modes", s.name); // Imaging is for commercial satellites only (2026-11-12).
					return;
				}
			}
			case "aprs" -> {
				if(!s.amateur) {
					reply.error("sat_no_modes", s.name);
					return;
				}
				float hz = Math.round(data.getDouble("mhz") * 1000.0D) * 1000.0F; // To the kHz.
				if(!satellites.setAprsHz(server, s, hz)) {
					reply.error("sat_aprs_bad", CommercialLicenseRules.mhz(hz));
					return;
				}
			}
			case "image" -> {
				if(!s.amateur) {
					reply.error("sat_no_modes", s.name);
					return;
				}
				String file = ComputerStorage.normalise(data.getString("file"));
				byte[] pixels = file == null ? null : context.computer().files().read(file);
				if(pixels == null || !ComputerFileFormats.isImage(pixels)) {
					reply.error("picture_not_image");
					return;
				}
				satellites.setImage(server, s, pixels, file);
			}
			default -> {
				reply.error("unknown_request", data.getString("field"));
				return;
			}
		}
		reply.ok();
	}
}
