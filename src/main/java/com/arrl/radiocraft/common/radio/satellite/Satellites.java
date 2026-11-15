package com.arrl.radiocraft.common.radio.satellite;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.network.clientbound.CSatelliteBurnoutPacket;
import com.arrl.radiocraft.common.network.clientbound.CSatellitesPacket;
import com.arrl.radiocraft.common.radio.RepeaterRegistry;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.*;

/**
 * Every satellite on orbit (2026-11-11, {@code doc/SatellitePlan.md}), in the overworld's {@code radiocraft_satellites}
 * data. A satellite comes from a contract ({@code SatelliteContractItem}) or the admin command, and goes when its orbit
 * decays ({@link RadiocraftServerConfig#SATELLITE_DECAY_DAYS} real days after launch) or an admin removes it.
 * <p>
 * The radio side ({@link SatelliteRadio}) reads {@link #onOrbit} from the voice thread: an immutable list, replaced
 * whenever a satellite comes or goes. Clients get the list ({@link CSatellitesPacket}) on login and whenever it changes,
 * for the dots in the night sky.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class Satellites extends SavedData {

	public static final String SAVE_NAME = "radiocraft_satellites";
	/** How often the decay is checked, ticks. */
	private static final int DECAY_CHECK_TICKS = 1200;

	private final Map<String, Satellite> byName = new LinkedHashMap<>();
	private int launched = 0;

	private static volatile List<Satellite> ON_ORBIT = List.of();
	/** The ones on orbit that still work: not decommissioned (2026-11-12). */
	private static volatile List<Satellite> WORKING = List.of();

	public static SavedData.Factory<Satellites> factory() {
		return new SavedData.Factory<>(Satellites::new, Satellites::load, null);
	}

	public static Satellites of(MinecraftServer server) {
		Satellites data = server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_NAME);
		if(ON_ORBIT.isEmpty() && !data.byName.isEmpty())
			data.published(); // Loaded after a restart: the radio side sees them from now on.
		return data;
	}

	/** Every satellite on orbit; any thread, never null. */
	public static List<Satellite> onOrbit() {
		return ON_ORBIT;
	}

	/**
	 * Every satellite on orbit that still works (not decommissioned, 2026-11-12): what {@link SatelliteRadio} hears and
	 * sends with. Any thread, never null.
	 */
	public static List<Satellite> working() {
		return WORKING;
	}

	/** The satellite on orbit named {@code name}, or null. Any thread. */
	@Nullable
	public static Satellite byName(String name) {
		for(Satellite satellite : ON_ORBIT)
			if(satellite.name.equalsIgnoreCase(name))
				return satellite;
		return null;
	}

	/** Every satellite, oldest first. */
	public Collection<Satellite> all() {
		return Collections.unmodifiableCollection(byName.values());
	}

	@Nullable
	public Satellite get(String name) {
		return name == null ? null : byName.get(name.toUpperCase(Locale.ROOT));
	}

	/** True if a satellite (any of its three frequencies) or a commercial license already uses {@code hz}. */
	private boolean taken(MinecraftServer server, float hz) {
		return takenBySatellite(hz, true) || CommercialLicenses.of(server).taken(hz);
	}

	/** True if a satellite's uplink or downlink is on {@code hz}, or (with {@code telemetry}) its telemetry. */
	private boolean takenBySatellite(float hz, boolean telemetry) {
		for(Satellite s : byName.values())
			if(s.decommissioned)
				continue; // Its frequencies went back to the pool (2026-11-12).
			else if(SatelliteRules.near(s.uplinkHz, hz) || SatelliteRules.near(s.downlinkHz, hz) || telemetry && s.telemetryHz > 0.0F && SatelliteRules.near(s.telemetryHz, hz))
				return true;
		return false;
	}

	/**
	 * Puts a new satellite up: three free frequencies (uplink, downlink, telemetry), a random tone and orbit, launched now with {@code owner} as its
	 * owner. Null if no pair is free.
	 */
	@Nullable
	public Satellite launch(MinecraftServer server, boolean amateur, UUID owner, String ownerName, Random random) {
		return launch(server, amateur, owner, ownerName, random, null);
	}

	/**
	 * Puts a new geostationary satellite up (2026-11-12) as {@link #launch} does, pointing at {@code aimX, aimZ} (where
	 * its owner activated it) and parked near there ({@link SatelliteGeo#park}). It takes frequencies from the same pool
	 * and a name from the same count as the others. Null if no pair is free.
	 */
	@Nullable
	public Satellite launchGeostationary(MinecraftServer server, boolean amateur, UUID owner, String ownerName, Random random, double aimX, double aimZ) {
		return launch(server, amateur, owner, ownerName, random, SatelliteGeo.park(aimX, aimZ, random));
	}

	@Nullable
	private Satellite launch(MinecraftServer server, boolean amateur, UUID owner, String ownerName, Random random, @Nullable SatelliteGeo geo) {
		SatelliteRules.Frequencies pair = SatelliteRules.frequencies(amateur, random, hz -> taken(server, hz));
		if(pair == null)
			return null;
		String name;
		do
			name = SatelliteRules.name(amateur, ++launched);
		while(byName.containsKey(name));
		Satellite satellite = new Satellite(name, amateur, pair.uplinkHz(), pair.downlinkHz(), pair.telemetryHz(), SatelliteRules.tone(random), owner, ownerName,
				System.currentTimeMillis(), LocalDate.now().toString(), SatelliteOrbit.random(random), UUID.randomUUID(), geo);
		satellite.art = random.nextInt(Satellite.ART_KINDS);
		byName.put(name, satellite);
		changed(server);
		return satellite;
	}

	/** Takes a satellite off orbit; true if there was one. */
	public boolean remove(MinecraftServer server, String name) {
		Satellite gone = byName.remove(name.toUpperCase(Locale.ROOT));
		if(gone == null)
			return false;
		RepeaterRegistry.remove(gone.voiceId);
		changed(server);
		return true;
	}

	/**
	 * Decommissions {@code satellite} (its owner's choice in SATTRACK, 2026-11-12): it stops sending anything and stops
	 * flashing, its frequencies go back to the pool (a commercial one's frequency license is revoked) and it leaves the
	 * repeater directory, but it stays on orbit, trackable, until its orbit decays. False if it already was.
	 */
	public boolean decommission(MinecraftServer server, Satellite satellite) {
		if(satellite.decommissioned)
			return false;
		satellite.decommissioned = true;
		satellite.on = false;
		RepeaterRegistry.remove(satellite.voiceId);
		if(!satellite.amateur) {
			CommercialLicenses licenses = CommercialLicenses.of(server);
			for(CommercialLicense license : List.copyOf(licenses.all()))
				if(license.covers(satellite.downlinkHz) && license.covers(satellite.uplinkHz))
					licenses.revoke(license.callsign());
		}
		changed(server);
		return true;
	}

	/** Hangs {@code satellite} over {@code x, z} for {@code ticks} (an admin's test; not saved). */
	public void pin(MinecraftServer server, Satellite satellite, double x, double z, long ticks) {
		satellite.pinX = x;
		satellite.pinZ = z;
		satellite.pinFrom = -1L; // Held still (the GameTests).
		satellite.pinUntil = server.overworld().getGameTime() + ticks;
		sync(server);
	}

	/**
	 * Plays a pass over {@code x, z} lasting {@code ticks} (the admin's {@code overhead}, 2026-11-11): it rises at one
	 * horizon now, crosses near the point and sets as the time runs out, each satellite on its own heading and to its
	 * own side.
	 */
	public void playPass(MinecraftServer server, Satellite satellite, double x, double z, long ticks) {
		pin(server, satellite, x, z, ticks);
		satellite.pinFrom = server.overworld().getGameTime();
		sync(server); // Again, now that the clients' copy should move.
	}

	/** Turns it on or off. */
	public void setOn(MinecraftServer server, Satellite satellite, boolean on) {
		satellite.on = on;
		changed(server);
	}

	/** Stealth mode on or off (2026-11-12): commercial satellites only. False if it may not. */
	public boolean setStealth(MinecraftServer server, Satellite satellite, boolean stealth) {
		if(satellite.amateur && stealth)
			return false;
		satellite.stealth = stealth;
		changed(server);
		return true;
	}

	public void setIdText(MinecraftServer server, Satellite satellite, String text) {
		satellite.idText = Satellite.cleanId(text);
		changed(server);
	}

	public void setTone(MinecraftServer server, Satellite satellite, float tone) {
		satellite.tone = com.arrl.radiocraft.common.radio.voice.Ctcss.sanitize(tone);
		changed(server);
	}

	/** Adds a player to the allowed list; false if the list is full or has them. */
	public boolean allow(MinecraftServer server, Satellite satellite, String player) {
		String name = player.toLowerCase(Locale.ROOT).strip();
		if(name.isEmpty() || satellite.allowed.contains(name) || satellite.allowed.size() >= Satellite.ALLOWED_MAX)
			return false;
		satellite.allowed.add(name);
		changed(server);
		return true;
	}

	public boolean disallow(MinecraftServer server, Satellite satellite, String player) {
		if(!satellite.allowed.remove(player.toLowerCase(Locale.ROOT).strip()))
			return false;
		changed(server);
		return true;
	}

	/** Sets an amateur satellite's mode; false for a mode it cannot run (a commercial one only repeats). */
	public boolean setMode(MinecraftServer server, Satellite satellite, SatelliteMode mode) {
		if(!mode.allowedOn(satellite.amateur))
			return false;
		satellite.mode = mode;
		changed(server);
		return true;
	}

	/** Sets its APRS frequency (0 for its downlink); false if it is not on 2m. */
	public boolean setAprsHz(MinecraftServer server, Satellite satellite, float hz) {
		if(hz != 0.0F && !SatelliteRules.aprsAllowed(hz))
			return false;
		satellite.aprsHz = hz;
		changed(server);
		return true;
	}

	/** Gives it the picture it sends in SSTV mode: a radio computer's .IMG file named {@code name}. */
	public void setImage(MinecraftServer server, Satellite satellite, byte[] image, String name) {
		satellite.image = image.clone();
		satellite.imageName = name == null ? "" : name;
		changed(server);
	}

	/** Its mailbox changed (saved with the rest). */
	public void mailboxChanged() {
		setDirty();
	}

	/** Puts it in the Earth's shadow on a flat battery for {@code ticks} (an admin's test; not saved). */
	public void eclipse(MinecraftServer server, Satellite satellite, long ticks) {
		satellite.eclipseUntil = server.overworld().getGameTime() + ticks;
	}

	/** True if the server shifts satellites' frequencies by Doppler (2026-11-12, off by default). */
	public static boolean dopplerOn() {
		return configOn(RadiocraftServerConfig.SATELLITE_DOPPLER);
	}

	/** True if night passes may rarely find a satellite eclipsed on a flat battery (2026-11-12, off by default). */
	public static boolean eclipsesOn() {
		return configOn(RadiocraftServerConfig.SATELLITE_ECLIPSES);
	}

	private static boolean configOn(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Boolean> value) {
		try {
			return value.get();
		}
		catch(IllegalStateException e) {
			return false; // Not loaded yet.
		}
	}

	private void changed(MinecraftServer server) {
		setDirty();
		published();
		sync(server);
	}

	private void published() {
		ON_ORBIT = List.copyOf(byName.values());
		WORKING = ON_ORBIT.stream().filter(s -> !s.decommissioned).toList();
	}

	/** Sends every client the list. */
	public static void sync(MinecraftServer server) {
		for(ServerPlayer player : server.getPlayerList().getPlayers())
			CSatellitesPacket.sendTo(player, ON_ORBIT);
	}

	/** Days from launch before its orbit decays. */
	public static int decayDays() {
		return RadiocraftServerConfig.SATELLITE_DECAY_DAYS.get();
	}

	/**
	 * Satellites whose orbit has decayed come down (a geostationary one's twice as fast), working or decommissioned: each
	 * burns up as a tiny streak seen by the players it is above the horizon of ({@link #burnout}).
	 */
	public void decay(MinecraftServer server, long nowMillis) {
		List<Satellite> gone = new ArrayList<>();
		for(Satellite s : byName.values())
			if(s.daysLeft(nowMillis) <= 0)
				gone.add(s);
		for(Satellite s : gone) {
			decayNow(server, s);
			Radiocraft.LOGGER.info("Satellite {} decayed and came down", s.name);
		}
	}

	/** Brings {@code satellite} down now as its orbit's decay does (burning up); returns how many players saw it. */
	public int decayNow(MinecraftServer server, Satellite satellite) {
		int told = burnout(server, satellite);
		remove(server, satellite.name);
		return told;
	}

	/**
	 * Tells each player in the overworld that {@code satellite} is above the horizon of now that it is burning up there
	 * ({@link CSatelliteBurnoutPacket}: only where it is and which way it goes); nobody else hears of it. Returns how
	 * many were told.
	 */
	public static int burnout(MinecraftServer server, Satellite satellite) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		int told = 0;
		for(ServerPlayer player : overworld.players()) {
			SatelliteOrbit.Look look = satellite.look(player.getX(), player.getZ(), now);
			if(!look.up())
				continue;
			CSatelliteBurnoutPacket.sendTo(player, look.subX(), look.subZ(), satellite.orbit.heading(), now);
			told++;
		}
		return told;
	}

	// -------------------- EVENTS --------------------

	@SubscribeEvent
	public static void serverStarted(ServerStartedEvent event) {
		of(event.getServer()).licenseTelemetry(event.getServer());
	}

	/**
	 * Makes every commercial satellite's license grant its telemetry frequency too (2026-11-12): one launched before
	 * telemetry had its channel picked on load without the licenses to hand, so if another license already grants it the
	 * satellite gets a new one (free of licenses and satellites, the same each time). A satellite whose license is gone
	 * is left as it is. Returns how many licenses changed.
	 */
	public int licenseTelemetry(MinecraftServer server) {
		CommercialLicenses licenses = CommercialLicenses.of(server);
		int changed = 0;
		for(Satellite s : byName.values()) {
			if(s.amateur || s.decommissioned)
				continue;
			CommercialLicense own = null;
			for(CommercialLicense license : licenses.all())
				if(license.covers(s.downlinkHz) && license.covers(s.uplinkHz)) {
					own = license;
					break;
				}
			if(own == null)
				continue;
			CommercialLicense holder = licenses.covering(s.telemetryHz);
			if(s.telemetryHz <= 0.0F || holder != null && !holder.callsign().equals(own.callsign())) {
				float hz = SatelliteRules.telemetry(false, new Random(s.name.hashCode()), f -> licenses.taken(f) || takenBySatellite(f, true));
				if(hz <= 0.0F)
					continue;
				s.telemetryHz = hz;
				setDirty();
			}
			if(own.telemetryHz() != s.telemetryHz) {
				licenses.coverTelemetry(own.callsign(), s.telemetryHz);
				changed++;
			}
		}
		if(changed > 0)
			published();
		return changed;
	}

	@SubscribeEvent
	public static void serverStopped(ServerStoppedEvent event) { // The next world in single player starts with none.
		ON_ORBIT = List.of();
		WORKING = List.of();
		SatelliteRadio.clear();
	}

	@SubscribeEvent
	public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if(event.getEntity() instanceof ServerPlayer player)
			CSatellitesPacket.sendTo(player, ON_ORBIT);
	}

	@SubscribeEvent
	public static void serverTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		if(now % DECAY_CHECK_TICKS == 0 && !ON_ORBIT.isEmpty())
			of(server).decay(server, System.currentTimeMillis());
		MoonBounce.tick(overworld, now); // Moonbounce needs no satellite (2026-11-12).
		if(ON_ORBIT.isEmpty())
			return;
		if(now % 100 == 0) // The repeater directory knows them, wherever they are now.
			for(Satellite s : WORKING) {
				SatelliteOrbit.Look look = s.look(0.0D, 0.0D, now);
				RepeaterRegistry.update(s.voiceId, Level.OVERWORLD, BlockPos.containing(look.subX(), 400.0D, look.subZ()), s.idText(),
						s.downlinkHz, s.uplinkHz, s.tone(), 0.0F, !s.amateur);
			}
		SatelliteRadio.tick(overworld, now);
	}

	// -------------------- SAVING --------------------

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		ListTag list = new ListTag();
		for(Satellite s : byName.values())
			list.add(s.toTag());
		tag.put("satellites", list);
		tag.putInt("launched", launched);
		return tag;
	}

	public static Satellites load(CompoundTag tag, HolderLookup.Provider registries) {
		Satellites data = new Satellites();
		ListTag list = tag.getList("satellites", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++) {
			Satellite s = Satellite.fromTag(list.getCompound(i));
			if(!s.name.isEmpty())
				data.byName.put(s.name.toUpperCase(Locale.ROOT), s);
		}
		data.launched = tag.getInt("launched");
		// Saved before satellites had a telemetry beacon (2026-11-12): give each a free channel, the same one every load.
		for(Satellite s : data.byName.values())
			if(s.telemetryHz <= 0.0F) {
				s.telemetryHz = SatelliteRules.legacyTelemetry(s.name, s.amateur, s.uplinkHz, s.downlinkHz, hz -> data.takenBySatellite(hz, true),
						hz -> data.takenBySatellite(hz, false));
				data.setDirty();
			}
		data.published();
		return data;
	}
}
