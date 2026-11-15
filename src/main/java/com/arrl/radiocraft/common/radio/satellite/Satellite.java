package com.arrl.radiocraft.common.radio.satellite;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import com.arrl.radiocraft.common.computer.PacketBbs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One satellite on orbit (2026-11-11): a server-side record in {@link Satellites}, no entity. Its position is arithmetic
 * from the world time ({@link SatelliteOrbit}). The owner and the players on its allowed list may change its settings
 * (on/off, ID text, tone, allowed list) from SATTRACK while it passes; anyone may use it.
 * <p>
 * <p>
 * Since 2026-11-12 it also has a telemetry frequency its beacon sends on, and an amateur one a {@link SatelliteMode}
 * (with its APRS frequency, its mailbox and its SSTV picture, whichever the mode uses).
 * <p>
 * An admin can pin it over a point for a while ({@code /radiocraft satellite overhead}), for testing: until
 * {@link #pinUntil} it hangs over {@link #pinX}, {@link #pinZ} instead of following its orbit. The pin is not saved.
 * <p>
 * Since 2026-11-12 it may be geostationary ({@link #geo}: parked, its coverage centred where its owner activated it,
 * no passes, decaying twice as fast) and may be {@link #decommissioned} by its owner (silent, its frequencies free,
 * still tracked until its orbit decays).
 */
public final class Satellite {

	/** Most characters of the ID text it sends in Morse. */
	public static final int ID_MAX = 12;
	/** Most players on the allowed list. */
	public static final int ALLOWED_MAX = 16;
	/** Most messages its mailbox keeps (2026-11-12). */
	public static final int MAILBOX_MAX = 40;

	public final String name;
	public final boolean amateur;
	public final float uplinkHz, downlinkHz;
	public final UUID owner;
	public final String ownerName;
	/** Real time it was launched, ms (the decay counts from here). */
	public final long launchMillis;
	/** The day it was launched, {@code 2026-11-11}. */
	public final String launchDay;
	public final SatelliteOrbit orbit;
	/** How many kinds of satellite SATTRACK's Image view draws (sphere and whips, drum, spinner, small sphere, winged, cubesat). */
	public static final int ART_KINDS = 6;
	/** Which one this is, picked at random at launch (2026-11-13); one saved before then gets one from its name. */
	int art;
	/** A voice id of its own, for its Morse ID (what it relays keeps the talker's). */
	public final UUID voiceId;
	/** Fixes its eclipses and the quirks of its telemetry. */
	public final long seed;

	/** Its telemetry beacon's frequency, Hz (given on load to one saved before it had one). */
	volatile float telemetryHz;
	volatile SatelliteMode mode = SatelliteMode.REPEATER;
	/** Its APRS frequency, Hz; 0 for its downlink. */
	volatile float aprsHz = 0.0F;
	/** Its mailbox, made when first used. Server thread. */
	@Nullable
	PacketBbs mailbox;
	/** The picture it sends in SSTV mode (a radio computer's .IMG file), or null; and the file's name. */
	@Nullable
	volatile byte[] image;
	volatile String imageName = "";
	/** An admin's (or a GameTest's) eclipse: on a flat battery until this game time; not saved. */
	volatile long eclipseUntil = -1L;

	// Changed on the server thread, read by the voice thread.
	volatile float tone;
	volatile String idText;
	volatile boolean on = true;
	final List<String> allowed = new ArrayList<>();

	volatile double pinX, pinZ;
	volatile long pinUntil = -1L;
	/** When a played-out pass began (it runs horizon to horizon until {@link #pinUntil}); -1 for a pin held still. */
	volatile long pinFrom = -1L;

	/**
	 * Where a geostationary satellite parks and points (2026-11-12), or null for one on an ordinary orbit (its
	 * {@link #orbit}'s heading still sets which way a geostationary imager scans and a burnout streaks).
	 */
	@Nullable
	public final SatelliteGeo geo;
	/**
	 * Decommissioned by its owner (2026-11-12): its frequencies are free again and it sends nothing (no repeating,
	 * beacon, relay or imaging) and no longer flashes, but it stays on orbit, trackable, until its orbit decays.
	 */
	volatile boolean decommissioned = false;
	/**
	 * Stealth mode (2026-11-12, commercial satellites only): it sends so low that only a satellite tracking antenna
	 * locked on it hears it, and it hears only those; SATTRACK lists only its name and where it is, but for its owner
	 * and those allowed. Turned on only from a station whose tracker is on it.
	 */
	volatile boolean stealth = false;

	public Satellite(String name, boolean amateur, float uplinkHz, float downlinkHz, float telemetryHz, float tone, UUID owner, String ownerName,
	                 long launchMillis, String launchDay, SatelliteOrbit orbit, UUID voiceId) {
		this(name, amateur, uplinkHz, downlinkHz, telemetryHz, tone, owner, ownerName, launchMillis, launchDay, orbit, voiceId, null);
	}

	public Satellite(String name, boolean amateur, float uplinkHz, float downlinkHz, float telemetryHz, float tone, UUID owner, String ownerName,
	                 long launchMillis, String launchDay, SatelliteOrbit orbit, UUID voiceId, @Nullable SatelliteGeo geo) {
		this.geo = geo;
		this.name = name;
		this.amateur = amateur;
		this.uplinkHz = uplinkHz;
		this.downlinkHz = downlinkHz;
		this.tone = tone;
		this.owner = owner;
		this.ownerName = ownerName == null ? "" : ownerName;
		this.launchMillis = launchMillis;
		this.launchDay = launchDay == null ? "" : launchDay;
		this.orbit = orbit;
		this.voiceId = voiceId;
		this.idText = name;
		this.telemetryHz = telemetryHz;
		this.seed = SatelliteEclipse.seed(name, launchMillis);
	}

	public float telemetryHz() {
		return telemetryHz;
	}

	/** True for a geostationary satellite (2026-11-12): it stays put in the sky. */
	/** Which picture SATTRACK's Image view draws for it, 0 to {@link #ART_KINDS} - 1. */
	public int art() {
		return art;
	}

	public boolean geostationary() {
		return geo != null;
	}

	/** True in stealth mode (2026-11-12): heard only through a tracker locked on it. */
	public boolean stealth() {
		return stealth;
	}

	/** True once its owner has decommissioned it (2026-11-12). */
	public boolean decommissioned() {
		return decommissioned;
	}

	/** When its orbit decays, real time ms: a geostationary one's twice as fast ({@link SatelliteRules#GEO_DECAY_RATE}). */
	public long decayMillis() {
		return SatelliteRules.decayMillis(launchMillis, Satellites.decayDays(), geostationary());
	}

	/** Whole days until its orbit decays, from {@code nowMillis}. */
	public long daysLeft(long nowMillis) {
		return SatelliteRules.daysLeft(launchMillis, Satellites.decayDays(), geostationary(), nowMillis);
	}

	/** True in its last week ({@link SatelliteRules#WARN_DAYS}). */
	public boolean decayNear(long nowMillis) {
		return SatelliteRules.decayNear(launchMillis, Satellites.decayDays(), geostationary(), nowMillis);
	}

	/** What it does with its radio: a commercial one repeats or images ({@link SatelliteMode#allowedOn}). */
	public SatelliteMode mode() {
		SatelliteMode now = mode;
		return now.allowedOn(amateur) ? now : SatelliteMode.REPEATER;
	}

	/** Where its APRS digipeater listens and sends: the frequency its owner set, or its downlink. */
	public float aprsHz() {
		return aprsHz > 0.0F ? aprsHz : downlinkHz;
	}

	/** Its mailbox (made empty the first time). Server thread. */
	public PacketBbs mailbox() {
		if(mailbox == null)
			mailbox = new PacketBbs().limited(MAILBOX_MAX);
		return mailbox;
	}

	/** Messages in its mailbox, without making one. */
	public int mailboxCount() {
		PacketBbs box = mailbox;
		return box == null ? 0 : box.messages().size();
	}

	@Nullable
	public byte[] image() {
		return image;
	}

	public String imageName() {
		return imageName;
	}

	/**
	 * True if it is in the Earth's shadow on a flat battery at {@code gameTime} ({@code dayTime} the world's time of
	 * day): an admin made it so, or {@code eclipses} are on and it drew one ({@link SatelliteEclipse}).
	 */
	public boolean eclipsed(long gameTime, long dayTime, boolean eclipses) {
		if(eclipseUntil >= 0 && gameTime < eclipseUntil)
			return true;
		return eclipses && SatelliteEclipse.eclipsed(seed, gameTime, dayTime);
	}

	/** Slots since an eclipse while its battery is still low after one (1 to {@link SatelliteEclipse#RECOVERY_SLOTS}); 0 if not. */
	public int recovering(long gameTime, long dayTime, boolean eclipses) {
		if(eclipsed(gameTime, dayTime, eclipses))
			return 0;
		long since = gameTime - eclipseUntil;
		if(eclipseUntil >= 0 && since < SatelliteEclipse.RECOVERY_SLOTS * SatelliteEclipse.SLOT_TICKS)
			return 1 + (int)(since / SatelliteEclipse.SLOT_TICKS);
		return eclipses ? SatelliteEclipse.recovering(seed, gameTime, dayTime) : 0;
	}

	/** The range rate ({@link SatelliteDoppler#rangeRate}) from {@code x, z} at {@code gameTime}: below 0 approaching. */
	public double rangeRate(double x, double z, long gameTime) {
		return SatelliteDoppler.rangeRate(look(x, z, gameTime), look(x, z, gameTime + SatelliteDoppler.SAMPLE_TICKS));
	}

	/** The Doppler shift, Hz, of a signal on {@code hz} between it and {@code x, z} at {@code gameTime}. */
	public double dopplerHz(float hz, double x, double z, long gameTime) {
		return SatelliteDoppler.shiftHz(hz, rangeRate(x, z, gameTime));
	}

	public float tone() {
		return tone;
	}

	public String idText() {
		return idText;
	}

	public boolean isOn() {
		return on;
	}

	/** The allowed list, lower-case player names. */
	public List<String> allowed() {
		return Collections.unmodifiableList(allowed);
	}

	/** True if {@code player} ({@code name}) may change its settings: the owner or someone on the list. */
	public boolean mayControl(UUID player, String playerName) {
		return owner.equals(player) || playerName != null && allowed.contains(playerName.toLowerCase(Locale.ROOT));
	}

	/** True while an admin has it pinned over a point. */
	public boolean pinned(long gameTime) {
		return pinUntil >= 0 && gameTime < pinUntil;
	}

	public double pinX() {
		return pinX;
	}

	public double pinZ() {
		return pinZ;
	}

	public long pinUntil() {
		return pinUntil;
	}

	public long pinFrom() {
		return pinFrom;
	}

	/**
	 * What the place {@code x, z} sees of it at {@code time}, a game time with a fraction (weather imaging places its
	 * scan lines between ticks, 2026-11-12).
	 */
	public SatelliteOrbit.Look lookAt(double x, double z, double time) {
		long tick = (long)Math.floor(time);
		if(pinned(tick) && pinFrom < 0)
			return SatelliteOrbit.lookAt(x, z, pinX, pinZ);
		if(pinned(tick))
			return SatelliteOrbit.passOver(x, z, pinX, pinZ, pinFrom, pinUntil, time, orbit.heading(), name.hashCode());
		return geo != null ? geo.look(x, z) : orbit.look(x, z, time);
	}

	/** What the place {@code x, z} sees of it at {@code gameTime}. */
	public SatelliteOrbit.Look look(double x, double z, long gameTime) {
		if(pinned(gameTime) && pinFrom < 0)
			return SatelliteOrbit.lookAt(x, z, pinX, pinZ);
		if(pinned(gameTime))
			return SatelliteOrbit.passOver(x, z, pinX, pinZ, pinFrom, pinUntil, gameTime, orbit.heading(), name.hashCode());
		return geo != null ? geo.look(x, z) : orbit.look(x, z, gameTime);
	}

	/**
	 * The next {@code count} passes over {@code x, z} (while pinned, one pass until the pin ends). A geostationary one
	 * has none: it is always up there or never.
	 */
	public List<SatelliteOrbit.Pass> nextPasses(double x, double z, long gameTime, int count) {
		if(pinned(gameTime)) {
			SatelliteOrbit.Look look = pinFrom < 0 ? SatelliteOrbit.lookAt(x, z, pinX, pinZ)
					: SatelliteOrbit.passOver(x, z, pinX, pinZ, pinFrom, pinUntil, (pinFrom + pinUntil) / 2.0D, orbit.heading(), name.hashCode());
			if(look.up())
				return List.of(new SatelliteOrbit.Pass(gameTime, gameTime, pinUntil, look.elevation(), look.azimuth()));
		}
		if(geo != null)
			return List.of();
		return orbit.nextPasses(x, z, gameTime, count);
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("name", name);
		tag.putBoolean("amateur", amateur);
		tag.putFloat("uplink", uplinkHz);
		tag.putFloat("downlink", downlinkHz);
		tag.putFloat("telemetry", telemetryHz);
		tag.putString("mode", mode.name());
		tag.putFloat("aprs", aprsHz);
		if(mailbox != null)
			tag.put("mailbox", mailbox.toTag());
		byte[] picture = image;
		if(picture != null) {
			tag.putByteArray("image", picture);
			tag.putString("image_name", imageName);
		}
		tag.putFloat("tone", tone);
		tag.putUUID("owner", owner);
		tag.putString("owner_name", ownerName);
		tag.putLong("launch_millis", launchMillis);
		tag.putString("launch_day", launchDay);
		tag.putDouble("heading", orbit.heading());
		tag.putDouble("along", orbit.alongPhase());
		tag.putDouble("across", orbit.crossPhase());
		tag.putDouble("drift", orbit.drift());
		tag.putUUID("voice", voiceId);
		tag.putString("id_text", idText);
		tag.putBoolean("on", on);
		tag.putInt("art", art);
		if(geo != null) {
			CompoundTag g = new CompoundTag();
			g.putDouble("aim_x", geo.aimX());
			g.putDouble("aim_z", geo.aimZ());
			g.putDouble("park_x", geo.parkX());
			g.putDouble("park_z", geo.parkZ());
			tag.put("geo", g);
		}
		if(decommissioned)
			tag.putBoolean("decommissioned", true);
		if(stealth)
			tag.putBoolean("stealth", true);
		ListTag list = new ListTag();
		for(String player : allowed)
			list.add(StringTag.valueOf(player));
		tag.put("allowed", list);
		return tag;
	}

	public static Satellite fromTag(CompoundTag tag) {
		SatelliteOrbit orbit = new SatelliteOrbit(tag.getDouble("heading"), tag.getDouble("along"), tag.getDouble("across"), tag.getDouble("drift"));
		UUID owner = tag.hasUUID("owner") ? tag.getUUID("owner") : new UUID(0L, 0L);
		UUID voice = tag.hasUUID("voice") ? tag.getUUID("voice") : UUID.nameUUIDFromBytes(("satellite:" + tag.getString("name")).getBytes());
		SatelliteGeo geo = null;
		if(tag.contains("geo")) {
			CompoundTag g = tag.getCompound("geo");
			geo = new SatelliteGeo(g.getDouble("aim_x"), g.getDouble("aim_z"), g.getDouble("park_x"), g.getDouble("park_z"));
		}
		Satellite satellite = new Satellite(tag.getString("name"), tag.getBoolean("amateur"), tag.getFloat("uplink"), tag.getFloat("downlink"),
				tag.getFloat("telemetry"), tag.getFloat("tone"), owner, tag.getString("owner_name"), tag.getLong("launch_millis"), tag.getString("launch_day"), orbit, voice, geo);
		satellite.decommissioned = tag.getBoolean("decommissioned");
		satellite.stealth = !satellite.amateur && tag.getBoolean("stealth");
		if(tag.contains("id_text"))
			satellite.idText = cleanId(tag.getString("id_text"));
		satellite.on = !tag.contains("on") || tag.getBoolean("on");
		satellite.art = Math.floorMod(tag.contains("art") ? tag.getInt("art") : tag.getString("name").hashCode(), ART_KINDS);
		SatelliteMode mode = SatelliteMode.parse(tag.getString("mode"));
		satellite.mode = mode == null || !mode.allowedOn(satellite.amateur) ? SatelliteMode.REPEATER : mode;
		float aprs = tag.getFloat("aprs");
		satellite.aprsHz = SatelliteRules.aprsAllowed(aprs) ? aprs : 0.0F;
		if(tag.contains("mailbox"))
			satellite.mailbox = PacketBbs.fromTag(tag.getCompound("mailbox")).limited(MAILBOX_MAX);
		if(tag.contains("image")) {
			satellite.image = tag.getByteArray("image");
			satellite.imageName = tag.getString("image_name");
		}
		ListTag list = tag.getList("allowed", Tag.TAG_STRING);
		for(int i = 0; i < list.size() && satellite.allowed.size() < ALLOWED_MAX; i++)
			satellite.allowed.add(list.getString(i).toLowerCase(Locale.ROOT));
		return satellite;
	}

	/** Letters, digits, '/' and spaces Morse can send, upper case, at most {@link #ID_MAX}. */
	public static String cleanId(String text) {
		StringBuilder out = new StringBuilder();
		if(text != null)
			for(char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
				if(c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '/' || c == ' ' || c == '-')
					out.append(c);
				if(out.length() >= ID_MAX)
					break;
			}
		return out.toString().strip();
	}
}
