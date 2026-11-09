package com.arrl.radiocraft.common.radio;

/**
 * Where a ham radio may tune and where it may transmit.
 * <ul>
 *     <li>Every HF transceiver and VHF radio <b>tunes and receives</b> past its band, like a real rig's general
 *     coverage receiver: an HF band grows by half its width, a quarter on each side (20m, 14.000-14.350 MHz, tunes
 *     13.9125-14.4375), and a 2m radio tunes from a quarter below 2m up to the top of commercial VHF (143-174 MHz).</li>
 *     <li>It <b>transmits</b> only inside the band ({@link Band#contains}). With a MARS mod
 *     ({@link com.arrl.radiocraft.common.items.MarsModItem}) it transmits anywhere it tunes. Out of band without one, the
 *     display reads OUT OF BAND and the PTT does nothing.</li>
 *     <li>A transmission outside its band <b>splatters</b>: its interference reaches {@link #OUT_OF_BAND_SPLATTER}
 *     times as far into the frequencies next to it ({@link com.arrl.radiocraft.common.radio.voice.Detuning}).</li>
 * </ul>
 * Commercial bands ({@link Band#COMMERCIAL_BANDS}) tune exactly their band. No game state here except reading the band
 * config, so the maths can be unit tested with hand-made {@link Band}s.
 */
public final class BandEdges {

    /** Share of an HF band's width a MARS mod (and the receiver) reaches past each edge. */
    public static final double EXTENSION = 0.25D;
    /** How much further out-of-band splatter reaches into neighbouring frequencies. */
    public static final double OUT_OF_BAND_SPLATTER = 2.0D;
    /** The top of commercial VHF when the config is not loaded (unit tests). */
    public static final float DEFAULT_VHF_TOP_HZ = 174_000_000.0F;

    private BandEdges() {}

    /** Lowest frequency a radio on {@code band} tunes to. */
    public static float tuneMin(Band band) {
        if(!band.isAmateur())
            return band.minFrequency();
        float min = (float)(band.minFrequency() - band.width() * EXTENSION);
        if("10m".equals(band.name())) // Down through the CB channels, as modded 10m rigs reach ("freeband", 2026-11-08).
            min = Math.min(min, FREEBAND_BOTTOM_HZ);
        return min;
    }

    /** How far down a 10m radio tunes: below CB channel 1. */
    public static final float FREEBAND_BOTTOM_HZ = 26_900_000.0F;

    /** Highest frequency a radio on {@code band} tunes to. */
    public static float tuneMax(Band band) {
        return tuneMax(band, vhfTop());
    }

    /** {@link #tuneMax(Band)} with the top of commercial VHF given, for tests. */
    public static float tuneMax(Band band, float vhfTopHz) {
        if(!band.isAmateur())
            return band.maxFrequency();
        if(band.isVhf())
            return Math.max(band.maxFrequency(), vhfTopHz);
        return (float)(band.maxFrequency() + band.width() * EXTENSION);
    }

    /** True if a radio on {@code band} can tune to {@code hz}. */
    public static boolean canTune(Band band, double hz) {
        return hz >= tuneMin(band) - Band.EDGE_TOLERANCE_HZ && hz <= tuneMax(band) + Band.EDGE_TOLERANCE_HZ;
    }

    /** True if a radio on {@code band} may transmit on {@code hz}: in its band, or anywhere it tunes with a MARS mod. */
    public static boolean canTransmit(Band band, double hz, boolean marsMod) {
        if(band == null)
            return true; // Nothing to check against (an API radio without a band).
        return band.contains(hz) || (marsMod && canTune(band, hz));
    }

    /** True if {@code hz} is outside {@code band}: such a transmission splatters. */
    public static boolean isOutOfBand(Band band, double hz) {
        return band != null && !band.contains(hz);
    }

    /**
     * How far a transmission's splatter reaches against an in-band one's: {@code mars_mod_splatter} (default
     * {@link #OUT_OF_BAND_SPLATTER}) outside its band, 1 inside.
     */
    public static double splatterReach(Band band, double hz) {
        if(!isOutOfBand(band, hz))
            return 1.0D;
        try {
            return com.arrl.radiocraft.RadiocraftServerConfig.MARS_MOD_SPLATTER.get();
        }
        catch(IllegalStateException | NullPointerException e) { // Config not loaded (unit tests).
            return OUT_OF_BAND_SPLATTER;
        }
    }

    /**
     * {@code frequency} moved {@code stepCount} steps of {@code step} Hz, inside the tuning range. The step grid is
     * anchored on the band's bottom edge, so in-band channels stay where they were (counted in whole steps, as
     * {@link com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity#snapToStep} explains).
     */
    public static float snapToTuningRange(Band band, float frequency, int step, int stepCount) {
        return snap(band.minFrequency(), tuneMin(band), tuneMax(band), frequency, step, stepCount);
    }

    /** {@link #snapToTuningRange} with the range given, for tests. */
    public static float snap(double anchor, double low, double high, double frequency, int step, int stepCount) {
        long minSteps = (long)Math.ceil((low - anchor) / step - 1e-9);
        long maxSteps = (long)Math.floor((high - anchor) / step + 1e-9);
        long steps = Math.round((frequency - anchor) / step) + stepCount;
        steps = Math.max(minSteps, Math.min(maxSteps, steps));
        return (float)(anchor + (double)steps * step);
    }

    /** Top of commercial VHF from the band config, or {@link #DEFAULT_VHF_TOP_HZ} without one. */
    private static float vhfTop() {
        try {
            Band commercial = Band.getBand(Band.VHF_COMMERCIAL);
            return commercial == null ? DEFAULT_VHF_TOP_HZ : commercial.maxFrequency();
        }
        catch(IllegalStateException | NullPointerException e) { // Config not loaded yet.
            return DEFAULT_VHF_TOP_HZ;
        }
    }
}
