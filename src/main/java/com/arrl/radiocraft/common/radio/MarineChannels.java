package com.arrl.radiocraft.common.radio;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The VHF marine channel table (156.025-162.025 MHz, inside {@link Band#VHF_COMMERCIAL}). Marine radios are
 * channel-only: they pick a channel, never a frequency.
 * <ul>
 *     <li>Channels 1-28 put the ship's transmitter on 156.050 MHz + 50 kHz per channel, 60-88 on 156.025 MHz + 50 kHz
 *     per channel (so 16 is 156.800, 9 is 156.450, 88 is 157.425).</li>
 *     <li>A <b>duplex</b> channel's coast station transmits 4.6 MHz higher, and the ship listens there (ch 28: ship
 *     157.400, coast 162.000). A <b>simplex</b> channel uses the ship frequency both ways.</li>
 *     <li>{@link Region#INTERNATIONAL} is the ITU table. {@link Region#US} uses many of the duplex channels as simplex on
 *     the ship frequency, named with an "A" (22A, the Coast Guard liaison channel), keeps a few duplex public
 *     correspondence channels (20, 24-28, 84-87) and adds the receive-only NOAA weather channels (WX1-WX7).</li>
 * </ul>
 * Channel 70 is DSC (digital calling only, no voice); 13 and 67 are bridge-to-bridge at 1 W; 15 and 17 are low power
 * only. No game classes, so it unit tests without Minecraft. Step 3's marine radios build on {@link #table(Region)}.
 */
public final class MarineChannels {

	/** Which channel plan a radio follows. */
	public enum Region { US, INTERNATIONAL }

	/** Coast stations transmit this far above the ship on a duplex channel. */
	public static final double DUPLEX_SPLIT_HZ = 4_600_000.0D;

	/**
	 * One marine channel.
	 *
	 * @param name         as the radio shows it: "16", "22A", "WX1"
	 * @param transmitHz   where a ship radio transmits (0 for a receive-only channel)
	 * @param receiveHz    where a ship radio listens: the coast frequency on a duplex channel, else {@code transmitHz}
	 * @param use          what the channel is for, for screens and the band plan
	 * @param lowPowerOnly the radio drops to 1 W on this channel (13, 15, 17, 67)
	 * @param voice        false for DSC (70) only
	 */
	public record Channel(String name, double transmitHz, double receiveHz, String use, boolean lowPowerOnly, boolean voice) {
		/** True if the ship transmits and receives on different frequencies. */
		public boolean isDuplex() {
			return transmitHz > 0.0D && Math.abs(receiveHz - transmitHz) > 1.0D;
		}

		/** True for the weather channels: a ship radio only listens. */
		public boolean isReceiveOnly() {
			return transmitHz <= 0.0D;
		}

		/** Where a coast station transmits (what the ship hears). */
		public double coastTransmitHz() {
			return receiveHz;
		}
	}

	/** Distress, safety and calling. */
	public static final String DISTRESS = "16";
	/** Recreational calling and hailing (US); a working channel elsewhere. */
	public static final String HAILING = "9";

	/** International duplex channels (ITU Appendix 18, simplified). */
	private static final Set<Integer> INTERNATIONAL_DUPLEX = Set.of(1, 2, 3, 4, 5, 7, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28,
			60, 61, 62, 63, 64, 65, 66, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88);
	/** US channels used simplex on the ship frequency (the "A" channels). */
	private static final Set<Integer> US_A = Set.of(1, 3, 5, 7, 18, 19, 21, 22, 23, 61, 63, 64, 65, 66, 78, 79, 80, 81, 82, 83, 88);
	/** US duplex public correspondence (marine operator) channels. */
	private static final Set<Integer> US_DUPLEX = Set.of(20, 24, 25, 26, 27, 28, 84, 85, 86, 87);
	/** US simplex channels on their own number. */
	private static final Set<Integer> US_SIMPLEX = Set.of(6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 67, 68, 69, 71, 72, 73, 74, 77);
	/** Channels limited to 1 W. */
	private static final Set<Integer> LOW_POWER = Set.of(13, 15, 17, 67);
	/** US NOAA weather channels WX1-WX7, in MHz. */
	private static final double[] WEATHER_MHZ = { 162.550, 162.400, 162.475, 162.425, 162.450, 162.500, 162.525 };

	private static final List<Channel> US_TABLE = build(Region.US);
	private static final List<Channel> INTERNATIONAL_TABLE = build(Region.INTERNATIONAL);

	private MarineChannels() {}

	/** The channels of {@code region}, in the order a channel knob steps through them (1, 2, ... 88, then WX). */
	public static List<Channel> table(Region region) {
		return region == Region.US ? US_TABLE : INTERNATIONAL_TABLE;
	}

	/** Ship transmit frequency of channel number {@code number} (1-28 or 60-88), in Hz; 0 for any other number. */
	public static double shipFrequencyHz(int number) {
		if(number >= 1 && number <= 28)
			return 156_050_000.0D + 50_000.0D * (number - 1);
		if(number >= 60 && number <= 88)
			return 156_025_000.0D + 50_000.0D * (number - 60);
		return 0.0D;
	}

	/** The channel called {@code name} ("16", "22a", "WX1"), or null. */
	@Nullable
	public static Channel byName(Region region, String name) {
		String wanted = name.trim().toUpperCase(Locale.ROOT);
		for(Channel channel : table(region))
			if(channel.name().equals(wanted))
				return channel;
		return null;
	}

	/** Index of the channel called {@code name} in {@link #table}, or -1. */
	public static int indexOf(Region region, String name) {
		List<Channel> table = table(region);
		for(int i = 0; i < table.size(); i++)
			if(table.get(i).name().equalsIgnoreCase(name.trim()))
				return i;
		return -1;
	}

	/** The channel {@code steps} along the knob from {@code name} (wrapping round); channel 16 if {@code name} is unknown. */
	public static Channel step(Region region, String name, int steps) {
		List<Channel> table = table(region);
		int at = indexOf(region, name);
		if(at < 0)
			return byName(region, DISTRESS);
		return table.get(Math.floorMod(at + steps, table.size()));
	}

	/** The channel a ship transmits (or, for WX, receives) on within {@code toleranceHz} of {@code hz}, or null. */
	@Nullable
	public static Channel channelAt(Region region, double hz, double toleranceHz) {
		for(Channel channel : table(region)) {
			double at = channel.isReceiveOnly() ? channel.receiveHz() : channel.transmitHz();
			if(Math.abs(at - hz) <= toleranceHz)
				return channel;
		}
		return null;
	}

	/**
	 * What the channel is for in a word or two, upper case, for a display beside its number (the marine radio's LED
	 * window, 2026-11-09): "DISTRESS", "COAST GUARD", "WEATHER". The US names follow the Coast Guard's channel list;
	 * the international ones are broader. Empty for an unknown channel.
	 */
	public static String label(Region region, String name) {
		Channel channel = byName(region, name);
		if(channel == null)
			return "";
		if(channel.isReceiveOnly())
			return "WEATHER";
		String digits = channel.name().replaceAll("[^0-9]", "");
		int number = digits.isEmpty() ? 0 : Integer.parseInt(digits);
		if(region == Region.INTERNATIONAL)
			return switch(number) {
				case 16 -> "DISTRESS";
				case 6 -> "SAFETY";
				case 13 -> "BRIDGE";
				case 15, 17 -> "ON BOARD";
				case 70 -> "DSC";
				case 8, 10, 72, 77 -> "INTERSHIP";
				default -> channel.isDuplex() ? "PUBLIC CORR" : "PORT OPS";
			};
		return switch(number) {
			case 16 -> "DISTRESS";
			case 9 -> "CALLING";
			case 6 -> "SAFETY";
			case 13, 67 -> "BRIDGE";
			case 70 -> "DSC";
			case 22 -> "COAST GUARD";
			case 21, 23, 81, 82, 83 -> "USCG";
			case 17 -> "STATE";
			case 15 -> "ON BOARD";
			case 68, 69, 71, 72, 78 -> "NON-COMM";
			case 1, 5, 11, 12, 14 -> "PORT OPS VTS";
			case 20, 63, 65, 66, 73, 74, 77 -> "PORT OPS";
			case 24, 25, 26, 27, 28, 84, 85, 86, 87 -> "MARINE OPER";
			case 61, 64 -> "GOVERNMENT";
			default -> "COMMERCIAL";
		};
	}

	private static List<Channel> build(Region region) {
		List<Channel> out = new ArrayList<>();
		for(int number = 1; number <= 88; number++) {
			if(number > 28 && number < 60)
				continue;
			double ship = shipFrequencyHz(number);
			boolean lowPower = LOW_POWER.contains(number);
			String use = use(number, region);
			if(region == Region.INTERNATIONAL) {
				boolean duplex = INTERNATIONAL_DUPLEX.contains(number);
				out.add(new Channel(Integer.toString(number), ship, duplex ? ship + DUPLEX_SPLIT_HZ : ship, use, lowPower, number != 70));
			}
			else if(US_A.contains(number))
				out.add(new Channel(number + "A", ship, ship, use, lowPower, true));
			else if(US_DUPLEX.contains(number))
				out.add(new Channel(Integer.toString(number), ship, ship + DUPLEX_SPLIT_HZ, use, lowPower, true));
			else if(US_SIMPLEX.contains(number) || number == 70)
				out.add(new Channel(Integer.toString(number), ship, ship, use, lowPower, number != 70));
			// Not used in the US: 2, 4, 60, 62, 75, 76.
		}
		if(region == Region.US)
			for(int i = 0; i < WEATHER_MHZ.length; i++)
				out.add(new Channel("WX" + (i + 1), 0.0D, Math.round(WEATHER_MHZ[i] * 1_000_000.0D), "NOAA weather (receive only)", false, true));
		return List.copyOf(out);
	}

	private static String use(int number, Region region) {
		return switch(number) {
			case 16 -> "Distress, safety and calling";
			case 9 -> region == Region.US ? "Calling and hailing (recreational)" : "Port operations and ship movement";
			case 6 -> "Intership safety";
			case 13 -> "Bridge to bridge (1 W)";
			case 67 -> region == Region.US ? "Bridge to bridge (1 W)" : "Intership and port operations";
			case 70 -> "DSC (digital calling, no voice)";
			case 22 -> region == Region.US ? "Coast Guard liaison" : "Port operations and public correspondence";
			case 15, 17 -> "On-board communication (1 W)";
			case 68, 69, 71, 72, 78 -> region == Region.US ? "Non-commercial (recreational)" : "Port operations and intership";
			case 20, 24, 25, 26, 27, 28, 84, 85, 86, 87 -> "Public correspondence (marine operator)";
			default -> region == Region.US ? "Commercial and port operations" : "Port operations and public correspondence";
		};
	}
}
