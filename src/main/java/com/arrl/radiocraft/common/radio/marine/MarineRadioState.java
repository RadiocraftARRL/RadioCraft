package com.arrl.radiocraft.common.radio.marine;

import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.MarineChannels.Channel;
import com.arrl.radiocraft.common.radio.MarineChannels.Region;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a VHF marine radio is set to: the channel it is on, the high/low power switch and dual watch. Marine radios are
 * channel-only ({@link MarineChannels}): the knob steps through the channel table, the 16/9 key jumps to the calling
 * channels, and the frequencies always come from the channel. Stored as the {@code radiocraft:marine_radio} data
 * component on the marine handheld and the marine radio's item, and in the marine radio's block entity once placed.
 * No game classes beyond the codecs, so it unit tests without Minecraft.
 *
 * @param channel   the channel name as the table has it ("16", "22A", "WX1")
 * @param previous  the working channel to go back to after the 16/9 key, empty when the key was not used
 * @param highPower the HI/LO switch: 25 W (handheld 5 W) or 1 W
 * @param dualWatch DW: also listens to channel 16, which takes priority while it is busy
 */
public record MarineRadioState(String channel, String previous, boolean highPower, boolean dualWatch) {

	/** Channel 16 (distress, safety and calling), where every ship listens. */
	public static final double SIXTEEN_HZ = 156_800_000.0D;
	/** Marine channels sit 25 kHz apart (the "interstitial" numbers between the 50 kHz ones). */
	public static final int CHANNEL_STEP_HZ = 25_000;
	/**
	 * How long channel 16 holds the receiver after a signal on it, in nanoseconds, before dual watch goes back to the
	 * working channel. A real radio samples 16 every second or two and stays there while it is busy.
	 */
	public static final long PRIORITY_HOLD_NANOS = 1_500_000_000L;

	/** Transmit power against a standard radio (1) of the fixed-mount radio on 1 W (it is 1 on 25 W). */
	public static final float BASE_LOW_POWER = 0.2F;
	/** Transmit power of the handheld on 1 W against its 5 W (1). */
	public static final float HANDHELD_LOW_POWER = 0.45F;

	/** A radio out of the box: on 16, high power, no dual watch. */
	public static final MarineRadioState DEFAULT = new MarineRadioState(MarineChannels.DISTRESS, "", true, false);

	public MarineRadioState {
		channel = channel == null || channel.isBlank() ? MarineChannels.DISTRESS : channel.trim();
		previous = previous == null ? "" : previous.trim();
	}

	public static final Codec<MarineRadioState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("channel", MarineChannels.DISTRESS).forGetter(MarineRadioState::channel),
			Codec.STRING.optionalFieldOf("previous", "").forGetter(MarineRadioState::previous),
			Codec.BOOL.optionalFieldOf("high_power", true).forGetter(MarineRadioState::highPower),
			Codec.BOOL.optionalFieldOf("dual_watch", false).forGetter(MarineRadioState::dualWatch)
	).apply(instance, MarineRadioState::new));

	public static final StreamCodec<ByteBuf, MarineRadioState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, MarineRadioState::channel,
			ByteBufCodecs.STRING_UTF8, MarineRadioState::previous,
			ByteBufCodecs.BOOL, MarineRadioState::highPower,
			ByteBufCodecs.BOOL, MarineRadioState::dualWatch,
			MarineRadioState::new);

	/** The channel it is on in {@code region}'s table; channel 16 if that table has no such channel (the plan changed). */
	public Channel channel(Region region) {
		Channel on = MarineChannels.byName(region, channel);
		return on != null ? on : MarineChannels.byName(region, MarineChannels.DISTRESS);
	}

	/** True if it is on channel 16. */
	public boolean onSixteen(Region region) {
		return channel(region).name().equals(MarineChannels.DISTRESS);
	}

	/** The knob turned {@code steps} channels along the table (it wraps round, as a marine radio's does). */
	public MarineRadioState turn(Region region, int steps) {
		if(steps == 0)
			return this;
		return new MarineRadioState(MarineChannels.step(region, channel(region).name(), steps).name(), "", highPower, dualWatch);
	}

	/** Straight to channel {@code name} (unknown names are refused: the state comes back unchanged). */
	public MarineRadioState select(Region region, String name) {
		Channel wanted = MarineChannels.byName(region, name);
		return wanted == null ? this : new MarineRadioState(wanted.name(), "", highPower, dualWatch);
	}

	/**
	 * The 16/9 key: from a working channel it goes to 16 (and remembers the working channel), from 16 to 9, and from
	 * 9 back to the working channel it left. On a radio that was working channel 9 it goes 9, 16, 9.
	 */
	public MarineRadioState sixteenNine(Region region) {
		String on = channel(region).name();
		if(on.equals(MarineChannels.DISTRESS))
			return new MarineRadioState(MarineChannels.HAILING, previous, highPower, dualWatch);
		if(on.equals(MarineChannels.HAILING) && !previous.isEmpty() && !previous.equalsIgnoreCase(MarineChannels.HAILING)
				&& MarineChannels.byName(region, previous) != null)
			return new MarineRadioState(previous, "", highPower, dualWatch);
		return new MarineRadioState(MarineChannels.DISTRESS, on, highPower, dualWatch);
	}

	/**
	 * The 16 key (the red key, 2026-11-09): from a working channel straight to 16, remembering the working channel;
	 * pressed again on 16, back to it. Unlike 16/9 it never goes to 9.
	 */
	public MarineRadioState sixteen(Region region) {
		String on = channel(region).name();
		if(on.equals(MarineChannels.DISTRESS)) {
			if(!previous.isEmpty() && !previous.equalsIgnoreCase(MarineChannels.DISTRESS) && MarineChannels.byName(region, previous) != null)
				return new MarineRadioState(previous, "", highPower, dualWatch);
			return this;
		}
		return new MarineRadioState(MarineChannels.DISTRESS, on, highPower, dualWatch);
	}

	/**
	 * The radio's keys, as screens send them ({@link com.arrl.radiocraft.common.network.serverbound.SMarineRadioPacket}).
	 * New keys go on the end, so the numbers already sent keep their meaning: {@link #SIXTEEN} (the red key) and
	 * {@link #SELECT} (the mobile's number keys, {@code value} the channel's place in the table), 2026-11-09. The DSC
	 * keys of the fixed-mount radio (2026-11-09) change no channel state; the block entity acts on them
	 * ({@code MarineRadioBlockEntity#dscKey}): {@link #DISTRESS} (the button held for its count, {@code value} 1 to send,
	 * 0 to cancel), {@link #NATURE} (the next nature of distress), {@link #ACK} (acknowledge the alert on the display) and
	 * {@link #DISMISS} (CLR: take it off the display).
	 */
	public enum Key { TURN, SIXTEEN_NINE, HIGH_LOW, DUAL_WATCH, SIXTEEN, SELECT, DISTRESS, NATURE, ACK, DISMISS }

	/** Most clicks one key press may turn the knob (a fast scroll). */
	public static final int MAX_TURN = 10;

	/**
	 * This state with key number {@code key} pressed ({@code value} clicks for {@link Key#TURN}, the table index for
	 * {@link Key#SELECT}); unknown keys change nothing.
	 */
	public MarineRadioState press(Region region, int key, int value) {
		if(key < 0 || key >= Key.values().length)
			return this;
		return switch(Key.values()[key]) {
			case TURN -> turn(region, Math.max(-MAX_TURN, Math.min(MAX_TURN, value)));
			case SIXTEEN_NINE -> sixteenNine(region);
			case HIGH_LOW -> togglePower();
			case DUAL_WATCH -> toggleDualWatch();
			case SIXTEEN -> sixteen(region);
			case SELECT -> value >= 0 && value < MarineChannels.table(region).size() ? select(region, MarineChannels.table(region).get(value).name()) : this;
			case DISTRESS, NATURE, ACK, DISMISS -> this; // DSC: the radio block acts on these, not the channel state.
		};
	}

	/** The HI/LO switch flipped. */
	public MarineRadioState togglePower() {
		return new MarineRadioState(channel, previous, !highPower, dualWatch);
	}

	/** The DW key: dual watch on or off. */
	public MarineRadioState toggleDualWatch() {
		return new MarineRadioState(channel, previous, highPower, !dualWatch);
	}

	/** Where it listens: the channel's frequency, or the coast station's on a duplex channel. */
	public double receiveHz(Region region) {
		return channel(region).receiveHz();
	}

	/** Where it transmits: the ship frequency (the receive frequency on a receive-only channel, where it never keys). */
	public double transmitHz(Region region) {
		Channel on = channel(region);
		return on.isReceiveOnly() ? on.receiveHz() : on.transmitHz();
	}

	/** True if the radio keys up on this channel: not a weather channel, not DSC channel 70 (no voice there). */
	public boolean mayTransmit(Region region) {
		Channel on = channel(region);
		return !on.isReceiveOnly() && on.voice();
	}

	/** True if it transmits on high power: the switch is on HI and the channel allows it (13, 15, 17 and 67 are 1 W). */
	public boolean transmitsHigh(Region region) {
		return highPower && !channel(region).lowPowerOnly();
	}

	/** Transmit power of the fixed-mount radio against a standard radio's 1. */
	public float basePower(Region region) {
		return transmitsHigh(region) ? 1.0F : BASE_LOW_POWER;
	}

	/** Transmit power of the handheld against a standard handheld's 1. */
	public float handheldPower(Region region) {
		return transmitsHigh(region) ? 1.0F : HANDHELD_LOW_POWER;
	}

	/** The frequency dual watch also listens on (channel 16), or 0 when it is off or already on 16. */
	public double watchHz(Region region) {
		return dualWatch && !onSixteen(region) ? SIXTEEN_HZ : 0.0D;
	}

	/** The display's big line: the channel ("16", "22A", "WX1"). */
	public String displayLine(Region region) {
		return channel(region).name();
	}

	/** The display's small line: power, dual watch, the plan ("USA" or "INT"), and what the channel is for in short. */
	public String displayStatus(Region region) {
		Channel on = channel(region);
		StringBuilder out = new StringBuilder();
		out.append(on.isReceiveOnly() ? "RX" : transmitsHigh(region) ? "HI" : "LO");
		if(watchHz(region) > 0.0D)
			out.append(" DW");
		out.append(region == Region.US ? " USA" : " INT");
		if(on.name().equals(MarineChannels.DISTRESS))
			out.append(" DISTRESS");
		else if(on.name().equals(MarineChannels.HAILING))
			out.append(" CALL");
		else if(on.isReceiveOnly())
			out.append(" WX");
		else if(!on.voice())
			out.append(" DSC");
		else if(on.isDuplex())
			out.append(" DUP");
		return out.toString();
	}

	/**
	 * Dual watch's priority on channel 16, shared by a radio's receive calls (which come from the voice threads): a
	 * signal on 16 holds the receiver there, and the working channel is muted until 16 has been quiet for
	 * {@link #PRIORITY_HOLD_NANOS}.
	 */
	public static final class PriorityWatch {
		private volatile long lastPriorityNanos = Long.MIN_VALUE / 2;

		/** A signal was heard on channel 16 at {@code nanos}. */
		public void heardPriority(long nanos) {
			lastPriorityNanos = nanos;
		}

		/** True while 16 has been busy lately, so the working channel stays muted. */
		public boolean prioritySpeaking(long nanos) {
			return nanos - lastPriorityNanos < PRIORITY_HOLD_NANOS;
		}

		/** Forget 16 (dual watch switched off, another channel picked). */
		public void reset() {
			lastPriorityNanos = Long.MIN_VALUE / 2;
		}
	}
}
