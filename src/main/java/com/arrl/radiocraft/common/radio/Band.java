package com.arrl.radiocraft.common.radio;

import com.arrl.radiocraft.RadiocraftServerConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines the properties of a band
 * @param name The name of the band (name in m/cm, or {@link #LOW_BAND} / {@link #VHF_COMMERCIAL})
 * @param losRange The range of the band in meters (blocks)
 * @param minSkipDay The minimum distance to skip during daytime in meters (blocks)
 * @param maxSkipDay The maximum distance to skip during daytime in meters (blocks)
 * @param minSkipNight The minimum distance to skip during nighttime in meters (blocks)
 * @param maxSkipNight The maximum distance to skip during nighttime in meters (blocks)
 * @param minFrequency The minimum frequency of the band in Hz
 * @param maxFrequency The maximum frequency of the band in Hz
 */
public record Band(String name, int losRange, int minSkipDay, int maxSkipDay, int minSkipNight, int maxSkipNight, float minFrequency, float maxFrequency) {

    /** Commercial low band, 30-50 MHz: just above 10m, between 10m and VHF in how it travels. On the HF antenna network. */
    public static final String LOW_BAND = "low_band";
    /** Commercial VHF (high band), 150-174 MHz: business, public safety, federal, and the marine channels. Like 2m. */
    public static final String VHF_COMMERCIAL = "vhf_commercial";
    /**
     * Citizens Band, 26.965-27.405 MHz (11m): 40 AM channels, 5 W, no licence (2026-11-08). On the HF antenna network and
     * carried like 10m, a little less far for the low power (line of sight 25% shorter, skip 30% shorter); it follows
     * 10m's solar weather.
     */
    public static final String CB = "cb";
    /** The CB channels' span (channel 1 to 40, with a little room), for code that must not touch the config. */
    public static final double CB_MIN_HZ = 26_960_000.0D, CB_MAX_HZ = 27_410_000.0D;

    /** True on the CB channels (26.96-27.41 MHz), whatever the server config says the CB band is. */
    public static boolean isCbFrequency(double hz) {
        return hz >= CB_MIN_HZ && hz <= CB_MAX_HZ;
    }
    /** The bands that are not amateur bands: no MARS extension, and left out of {@link #getDefaults()}. */
    public static final Set<String> COMMERCIAL_BANDS = Set.of(LOW_BAND, VHF_COMMERCIAL, CB);
    /** Frequencies at or above this are on the VHF antenna network (and line of sight like 2m). */
    public static final float VHF_NETWORK_FROM_HZ = 100_000_000.0F;
    /** Slack on band edges, for float rounding at VHF (16 Hz at 174 MHz). */
    public static final float EDGE_TOLERANCE_HZ = 50.0F;

    /**
     * Get the band for a given "named" wavelength.
     * @param wavelength A whole wavelength in m
     * @return Band instance or null if not found
     */
    public static Band getBand(int wavelength) {
        RadiocraftServerConfig.BandConfig bandConfig = RadiocraftServerConfig.BAND_CONFIGS.get(wavelength + "m");
        if(bandConfig == null) return null;
        return bandConfig.getBand();
    }

    /**
     * Get the band for a given frequency in Hz: the narrowest band that contains it. A frequency in no band but inside
     * an amateur band's tuning range (its MARS extension, see {@link BandEdges}) gives that amateur band, so antennas
     * and tuners still see out-of-band signals on the band they belong to.
     * @param frequencyHertz The frequency in Hz
     * @return Band instance or null if no band (or tuning range) contains this frequency.
     */
    public static Band getBand(float frequencyHertz) {
        Band best = null;
        Band near = null;
        for(Registered registered : REGISTERED.values()) { // Bands other mods added (no config entry, see #register).
            Band band = registered.band();
            if(band.contains(frequencyHertz) && (best == null || band.width() < best.width()))
                best = band;
        }
        for(RadiocraftServerConfig.BandConfig config : RadiocraftServerConfig.BAND_CONFIGS.values()) {
            Band band = config.getBand();
            if(band.contains(frequencyHertz)) {
                if(best == null || band.width() < best.width())
                    best = band;
            }
            else if(best == null && band.isAmateur() && frequencyHertz >= BandEdges.tuneMin(band) && frequencyHertz <= BandEdges.tuneMax(band)) {
                if(near == null || band.width() < near.width())
                    near = band;
            }
        }
        return best != null ? best : near;
    }

    /**
     * Get the band for a given name.
     * @param name The name of the band (name in m)
     * @return Band instance or null if not found
     */
    public static Band getBand(String name) {
        Registered registered = name == null ? null : REGISTERED.get(name);
        if(registered != null)
            return registered.band(); // Checked first: a registered name never shadows a config band (register refuses those).
        RadiocraftServerConfig.BandConfig bandConfig = RadiocraftServerConfig.BAND_CONFIGS.get(name);
        if(bandConfig == null) return null;
        return bandConfig.getBand();
    }

    /**
     * True for the VHF bands (2m and commercial VHF), which normally have no skip at all and live on the VHF antenna
     * network; see {@link com.arrl.radiocraft.common.radio.solar.Aurora}. Low band (30-50 MHz) is on the HF network.
     */
    public boolean isVhf() {
        return minFrequency >= VHF_NETWORK_FROM_HZ;
    }

    /** True for amateur (ham) bands; false for {@link #COMMERCIAL_BANDS}. */
    public boolean isAmateur() {
        Registered registered = REGISTERED.get(name);
        return registered != null ? registered.amateur() : !COMMERCIAL_BANDS.contains(name);
    }

    /** True if {@code hz} is inside the band (edges included, with {@link #EDGE_TOLERANCE_HZ} of slack). */
    public boolean contains(double hz) {
        return hz >= minFrequency - EDGE_TOLERANCE_HZ && hz <= maxFrequency + EDGE_TOLERANCE_HZ;
    }

    /** Width of the band in Hz. */
    public double width() {
        return (double)maxFrequency - minFrequency;
    }

    /** Wavelength at the middle of the band, in metres. */
    public double wavelengthMeters() {
        return BandUtils.getWavelengthMetersFromFrequencyHertz(((double)minFrequency + maxFrequency) / 2.0D);
    }

    /** Short name for screens and chat: "20m", "Low Band", "VHF Comm". */
    public String displayName() {
        Registered registered = REGISTERED.get(name);
        if(registered != null)
            return registered.displayName();
        return switch(name) {
            case LOW_BAND -> "Low Band";
            case VHF_COMMERCIAL -> "VHF Comm";
            case CB -> "CB";
            default -> name;
        };
    }

    /**
     * The name a band's solar weather is looked up by: low band follows 10m (it opens for skip only when the sun is
     * lively), so data packs need no entry for it unless they want one.
     */
    public String conditionName() {
        Registered registered = REGISTERED.get(name);
        if(registered != null)
            return registered.conditionName();
        return LOW_BAND.equals(name) || CB.equals(name) ? "10m" : name;
    }

    // -------------------- BANDS OTHER MODS ADD --------------------

    /**
     * A band added in code by another mod ({@link com.arrl.radiocraft.api.radio.RadioBands#register}): it has no
     * server config entry, so its numbers are fixed by the mod that adds it.
     *
     * @param band the band
     * @param amateur true for an amateur band (tunes and transmits past its edges with a MARS mod, see {@link BandEdges})
     * @param conditionName the band whose solar weather it follows when a solar event does not name it
     * @param displayName the short name screens show
     */
    public record Registered(Band band, boolean amateur, String conditionName, String displayName) {}

    private static final Map<String, Registered> REGISTERED = new ConcurrentHashMap<>(); // Read from the voice thread.

    /**
     * Adds a band that is not in the server config. Refuses the names of RadioCraft's own bands and a name already
     * registered. Use {@link com.arrl.radiocraft.api.radio.RadioBands#register}, which documents the rules.
     *
     * @throws IllegalArgumentException if the name is taken
     */
    public static void register(Registered registered) {
        String name = registered.band().name();
        for(Band band : getAllDefaults())
            if(band.name().equals(name))
                throw new IllegalArgumentException("Band " + name + " is one of RadioCraft's own bands");
        if(REGISTERED.putIfAbsent(name, registered) != null)
            throw new IllegalArgumentException("Band " + name + " is already registered");
    }

    /** The bands other mods registered, in no particular order. */
    public static Collection<Band> getRegistered() {
        List<Band> out = new ArrayList<>();
        for(Registered registered : REGISTERED.values())
            out.add(registered.band());
        return out;
    }

    /** True if {@code name} is a band another mod registered (not one from the server config). */
    public static boolean isRegistered(String name) {
        return name != null && REGISTERED.containsKey(name);
    }

    /** The default amateur bands (the ones every HF/VHF ham radio uses). Used to init the defaults in RadiocraftServerConfig. */
    public static Collection<Band> getDefaults(){
        return List.of(
                new Band("2m",500,0,0,0,0,144_000_000,148_000_000),
                // 10m skips far (one long hop) but only while the sun is lively: the solar events close it otherwise.
                new Band("10m",800,2500,8000,2500,6000,28_000_000,29_700_000),
                new Band("20m",300,2000,6000,800,1000,14_000_000,14_350_000),
                new Band("40m",900,1000,1100,100,3000,7_000_000,7_300_000),
                new Band("80m",600,700,800,600,2000,3_500_000,4_000_000)
        );
    }

    /**
     * The default commercial bands. Low band carries further than 2m by line of sight, not quite as far as 10m, and skips by day
     * only when 10m does (see {@link #conditionName()}), not at night; commercial VHF is like 2m.
     */
    public static Collection<Band> getCommercialDefaults() {
        return List.of(
                new Band(LOW_BAND, 650, 3000, 7000, 0, 0, 30_000_000, 50_000_000),
                new Band(VHF_COMMERCIAL, 500, 0, 0, 0, 0, 150_000_000, 174_000_000),
                new Band(CB, 600, 1750, 5600, 1750, 4200, 26_960_000, 27_410_000) // 10m's reach, toned down for 5 W.
        );
    }

    /** Every default band, amateur first. */
    public static List<Band> getAllDefaults() {
        List<Band> out = new ArrayList<>(getDefaults());
        out.addAll(getCommercialDefaults());
        return out;
    }

}
