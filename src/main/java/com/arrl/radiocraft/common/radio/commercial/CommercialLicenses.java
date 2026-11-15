package com.arrl.radiocraft.common.radio.commercial;

import com.arrl.radiocraft.common.radio.Callsigns;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.*;

/**
 * Every commercial license issued on the server (2026-11-10), in the overworld's {@code radiocraft_commercial_licenses}
 * data: keeps frequencies and callsigns unique, and lets the FCC app and moderators look a frequency or callsign up.
 * The license item carries its own copy of the record ({@link CommercialLicense}); upgrades crafted into it are written
 * back here the next time it is carried ({@link #upgraded}).
 */
public final class CommercialLicenses extends SavedData {

	public static final String SAVE_NAME = "radiocraft_commercial_licenses";

	private final Map<String, CommercialLicense> byCallsign = new LinkedHashMap<>();

	public static SavedData.Factory<CommercialLicenses> factory() {
		return new SavedData.Factory<>(CommercialLicenses::new, CommercialLicenses::load, null);
	}

	public static CommercialLicenses of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_NAME);
	}

	/** Every license, oldest first. */
	public Collection<CommercialLicense> all() {
		return Collections.unmodifiableCollection(byCallsign.values());
	}

	@Nullable
	public CommercialLicense get(String callsign) {
		return callsign == null ? null : byCallsign.get(callsign.toUpperCase(Locale.ROOT));
	}

	/** The license that grants {@code hz} (as output or input), or null. */
	@Nullable
	public CommercialLicense covering(float hz) {
		for(CommercialLicense license : byCallsign.values())
			if(license.covers(hz))
				return license;
		return null;
	}

	/** True if a license already grants {@code hz}. */
	public boolean taken(float hz) {
		return covering(hz) != null;
	}

	/**
	 * Issues a new license in {@code band} to {@code holder}: a random free frequency, a new callsign, today's date.
	 * Null if the band is full.
	 */
	@Nullable
	public CommercialLicense issue(MinecraftServer server, String band, String holder, String holderId, int tier, Random random) {
		float hz = CommercialLicenseRules.pick(band, random, this::taken);
		if(hz <= 0)
			return null;
		String callsign = CommercialLicenseRules.callsign(random, c -> byCallsign.containsKey(c) || Callsigns.isTaken(server, c, null));
		if(callsign.isEmpty())
			return null;
		CommercialLicense license = new CommercialLicense(callsign, band, hz, 0.0F, holder, holderId, 0, LocalDate.now().toString(), false);
		byCallsign.put(callsign, license);
		if(tier > 0)
			license = upgraded(license.withTier(tier, 0.0F), random);
		setDirty();
		return license;
	}

	/**
	 * Issues a license for frequencies already chosen (2026-11-11: a satellite's downlink as the output and its uplink as
	 * the input, at tier 1 for its one base): a new callsign, today's date. Null if no callsign is free.
	 */
	@Nullable
	public CommercialLicense grant(MinecraftServer server, String band, float outputHz, float inputHz, String holder, String holderId, int tier, Random random) {
		return grant(server, band, outputHz, inputHz, 0.0F, holder, holderId, tier, random);
	}

	/**
	 * {@link #grant} with a third frequency (2026-11-12): a commercial satellite's telemetry beacon, granted by the same
	 * license so no one else is given it and the FCC Monitor finds it licensed.
	 */
	@Nullable
	public CommercialLicense grant(MinecraftServer server, String band, float outputHz, float inputHz, float telemetryHz, String holder, String holderId,
	                               int tier, Random random) {
		String callsign = CommercialLicenseRules.callsign(random, c -> byCallsign.containsKey(c) || Callsigns.isTaken(server, c, null));
		if(callsign.isEmpty())
			return null;
		CommercialLicense license = new CommercialLicense(callsign, band, outputHz, inputHz, holder, holderId, tier, LocalDate.now().toString(), false,
				telemetryHz);
		byCallsign.put(callsign, license);
		setDirty();
		return license;
	}

	/** Makes license {@code callsign} grant {@code telemetryHz} too (a commercial satellite saved before it had one); false if there is no such license. */
	public boolean coverTelemetry(String callsign, float telemetryHz) {
		CommercialLicense known = get(callsign);
		if(known == null)
			return false;
		if(known.telemetryHz() != telemetryHz) {
			byCallsign.put(known.callsign(), known.withTelemetry(telemetryHz));
			setDirty();
		}
		return true;
	}

	/**
	 * A license item that may have had upgrades crafted into it: the record as it should now be. The tier only ever goes
	 * up (an old copy cannot take it back down), and from the first upgrade it gets a paired input frequency if it has
	 * none. The registry keeps the result. A license this server never issued (from another world) is taken in as it is.
	 */
	public CommercialLicense upgraded(CommercialLicense carried, Random random) {
		CommercialLicense known = byCallsign.get(carried.callsign());
		int tier = Math.max(carried.tier(), known == null ? 0 : known.tier());
		float input = known != null && known.inputHz() > 0 ? known.inputHz() : carried.inputHz();
		if(tier > 0 && input <= 0)
			input = CommercialLicenseRules.pickInput(carried.band(), carried.outputHz(), random,
					hz -> { CommercialLicense other = covering(hz); return other != null && !other.callsign().equals(carried.callsign()); });
		CommercialLicense base = known != null ? known : carried;
		CommercialLicense result = new CommercialLicense(base.callsign(), base.band(), base.outputHz(), input, base.holder(), base.holderId(),
				tier, base.issued(), false, base.telemetryHz());
		if(!result.equals(known)) {
			byCallsign.put(result.callsign(), result);
			setDirty();
		}
		return carried.copy() ? result.asCopy() : result;
	}

	/** Removes a license; true if there was one. */
	public boolean revoke(String callsign) {
		if(byCallsign.remove(callsign.toUpperCase(Locale.ROOT)) == null)
			return false;
		setDirty();
		return true;
	}

	/** True if {@code callsign} is a license this server issued and has not revoked. */
	public boolean valid(String callsign) {
		return get(callsign) != null;
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		ListTag list = new ListTag();
		for(CommercialLicense license : byCallsign.values())
			list.add(license.toTag());
		tag.put("licenses", list);
		return tag;
	}

	public static CommercialLicenses load(CompoundTag tag, HolderLookup.Provider registries) {
		CommercialLicenses data = new CommercialLicenses();
		ListTag list = tag.getList("licenses", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++) {
			CommercialLicense license = CommercialLicense.fromTag(list.getCompound(i));
			if(!license.callsign().isEmpty())
				data.byCallsign.put(license.callsign(), license);
		}
		return data;
	}
}
