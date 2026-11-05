package com.arrl.radiocraft.common.radio.commercial;

import com.arrl.radiocraft.common.radio.voice.Ctcss;
import com.arrl.radiocraft.common.radio.voice.DigitalVoice;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One programmed channel of a commercial radio, as a radio shop would program it: a name for the display, the receive
 * and transmit frequencies (the same for simplex, different for a repeater pair; transmit 0 for receive only), analog
 * FM or digital, an optional CTCSS tone (analog only: sent, and needed to open the squelch), and for digital an
 * encryption key id (0 for clear) with whether a programmer may read the key back out.
 *
 * @param name        What the display shows, upper case, at most {@link #NAME_MAX} characters.
 * @param rxHz        Receive frequency in Hz; 0 for an unprogrammed (blank) knob position.
 * @param txHz        Transmit frequency in Hz; 0 for receive only.
 * @param digital     True for digital voice.
 * @param ctcssTone   CTCSS tone in Hz on analog channels, 0 for none.
 * @param keyId       Encryption key id on digital channels, 1 to {@link #MAX_KEY}; 0 for clear; {@link #KEY_HIDDEN}
 *                    only in what {@link CommercialProgramming#read} hands out for a key that may not be read.
 * @param keyReadable False if a programmer may not read the key back (it shows as {@link #KEY_HIDDEN}).
 */
public record CommercialChannel(String name, float rxHz, float txHz, boolean digital, float ctcssTone, int keyId, boolean keyReadable) {

	/** Characters in a channel's name (the upgraded handheld's display). */
	public static final int NAME_MAX = 8;
	/** Highest encryption key id. */
	public static final int MAX_KEY = 65535;
	/** A key id a programmer was not allowed to read; written back, it keeps the key the radio already has. */
	public static final int KEY_HIDDEN = -1;
	/** The channel spacing of commercial VHF and low band (narrowband), for adjacent-channel splatter. */
	public static final int CHANNEL_STEP_HZ = 12_500;

	/** An unprogrammed knob position: the radio hears nothing and cannot transmit there. */
	public static final CommercialChannel BLANK = new CommercialChannel("", 0.0F, 0.0F, false, 0.0F, 0, true);

	/** A simplex analog channel with no tone. */
	public static CommercialChannel simplex(String name, float hz) {
		return new CommercialChannel(name, hz, hz, false, 0.0F, 0, true);
	}

	public static final Codec<CommercialChannel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("name", "").forGetter(CommercialChannel::name),
			Codec.FLOAT.optionalFieldOf("rx", 0.0F).forGetter(CommercialChannel::rxHz),
			Codec.FLOAT.optionalFieldOf("tx", 0.0F).forGetter(CommercialChannel::txHz),
			Codec.BOOL.optionalFieldOf("digital", false).forGetter(CommercialChannel::digital),
			Codec.FLOAT.optionalFieldOf("ctcss", 0.0F).forGetter(CommercialChannel::ctcssTone),
			Codec.INT.optionalFieldOf("key", 0).forGetter(CommercialChannel::keyId),
			Codec.BOOL.optionalFieldOf("keyReadable", true).forGetter(CommercialChannel::keyReadable)
	).apply(instance, CommercialChannel::new));

	private static final StreamCodec<ByteBuf, String> NAME_CODEC = ByteBufCodecs.stringUtf8(NAME_MAX * 4);

	public static final StreamCodec<ByteBuf, CommercialChannel> STREAM_CODEC = StreamCodec.of(
			(buf, channel) -> {
				NAME_CODEC.encode(buf, channel.name());
				buf.writeFloat(channel.rxHz());
				buf.writeFloat(channel.txHz());
				buf.writeBoolean(channel.digital());
				buf.writeFloat(channel.ctcssTone());
				buf.writeInt(channel.keyId());
				buf.writeBoolean(channel.keyReadable());
			},
			buf -> new CommercialChannel(NAME_CODEC.decode(buf), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat(), buf.readInt(), buf.readBoolean()));

	/** True for an unprogrammed knob position. */
	public boolean isBlank() {
		return rxHz <= 0.0F;
	}

	/** True if the channel only receives (no transmit frequency). */
	public boolean isReceiveOnly() {
		return !isBlank() && txHz <= 0.0F;
	}

	/** True for a digital channel with an encryption key. */
	public boolean encrypted() {
		return digital && keyId != 0;
	}

	/** The mode transmissions on this channel go out with. */
	public DigitalVoice.Mode mode() {
		return digital ? new DigitalVoice.Mode(true, Math.max(0, keyId)) : DigitalVoice.Mode.ANALOG;
	}

	/** The receiver's digital decoder on this channel (holding its key), null on an analog channel. */
	@Nullable
	public DigitalVoice.Decoder decoder() {
		return digital ? new DigitalVoice.Decoder(Math.max(0, keyId)) : null;
	}

	/** The CTCSS tone sent and needed to open the squelch: the channel's on analog, none on digital. */
	public float analogTone() {
		return digital ? Ctcss.NONE : ctcssTone;
	}

	/** This channel as a programmer may read it: a key that is not readable comes out as {@link #KEY_HIDDEN}. */
	public CommercialChannel withKeyHidden() {
		if(keyReadable || keyId == 0)
			return this;
		return new CommercialChannel(name, rxHz, txHz, digital, ctcssTone, KEY_HIDDEN, false);
	}

	/** This channel with the key {@code key}. */
	public CommercialChannel withKey(int key) {
		return new CommercialChannel(name, rxHz, txHz, digital, ctcssTone, key, keyReadable);
	}

	/**
	 * This channel made valid: name cleaned, frequencies not negative, a standard tone or none, the key in range (none
	 * on analog), a hidden key left hidden (the caller resolves it first). A blank stays {@link #BLANK}.
	 */
	public CommercialChannel sanitized() {
		if(isBlank())
			return BLANK;
		int key = !digital ? 0 : keyId == KEY_HIDDEN ? KEY_HIDDEN : Math.max(0, Math.min(MAX_KEY, keyId));
		return new CommercialChannel(sanitizeName(name), rxHz, Math.max(0.0F, txHz), digital, digital ? 0.0F : Ctcss.sanitize(ctcssTone), key, keyReadable || key == 0);
	}

	/** Upper case printable ASCII, at most {@link #NAME_MAX} characters. */
	public static String sanitizeName(String name) {
		StringBuilder out = new StringBuilder();
		for(char c : name.toUpperCase(Locale.ROOT).strip().toCharArray()) {
			if(c >= 32 && c < 127)
				out.append(c);
			if(out.length() >= NAME_MAX)
				break;
		}
		return out.toString().strip();
	}

	// -------------------- TEXT FORM (debug command, programmer import/export) --------------------

	/**
	 * The channel as one line of text: {@code NAME|rx MHz|tx MHz|FM or DIG|tone|key|R or H}, e.g.
	 * {@code FIRE 1|154.2800|154.2800|FM|100.0|0|R}. A blank channel is {@code -}; a receive-only one has tx {@code RX}.
	 */
	public String format() {
		if(isBlank())
			return "-";
		return String.format(Locale.ROOT, "%s|%.4f|%s|%s|%s|%d|%s", name, rxHz / 1e6, isReceiveOnly() ? "RX" : String.format(Locale.ROOT, "%.4f", txHz / 1e6),
				digital ? "DIG" : "FM", ctcssTone > 0.0F ? String.format(Locale.ROOT, "%.1f", ctcssTone) : "0", keyId, keyReadable ? "R" : "H");
	}

	/**
	 * Reads {@link #format()}'s text form. Only the name and receive frequency are needed: {@code NAME|rx} is a simplex
	 * analog channel; a missing or {@code =} transmit frequency is the receive one; the mode defaults to FM, the tone to
	 * none, the key to 0 and the readable flag to R. {@code -} is a blank channel.
	 *
	 * @throws IllegalArgumentException if a field cannot be read.
	 */
	public static CommercialChannel parse(String text) {
		String spec = text.strip();
		if(spec.equals("-") || spec.isEmpty())
			return BLANK;
		String[] parts = spec.split("\\|", -1);
		if(parts.length < 2)
			throw new IllegalArgumentException("Need at least NAME|rxMHz");
		String name = parts[0];
		float rx = mhz(parts[1], "rx");
		float tx = rx;
		if(parts.length > 2 && !parts[2].isBlank() && !parts[2].strip().equals("="))
			tx = parts[2].strip().equalsIgnoreCase("RX") ? 0.0F : mhz(parts[2], "tx");
		boolean digital = false;
		if(parts.length > 3 && !parts[3].isBlank()) {
			String mode = parts[3].strip().toUpperCase(Locale.ROOT);
			if(mode.equals("DIG") || mode.equals("DIGITAL") || mode.equals("P25"))
				digital = true;
			else if(!mode.equals("FM") && !mode.equals("ANALOG"))
				throw new IllegalArgumentException("Mode is FM or DIG, not " + parts[3]);
		}
		float tone = 0.0F;
		if(parts.length > 4 && !parts[4].isBlank()) {
			try {
				tone = Float.parseFloat(parts[4].strip());
			}
			catch(NumberFormatException e) {
				throw new IllegalArgumentException("Bad tone " + parts[4]);
			}
		}
		int key = 0;
		if(parts.length > 5 && !parts[5].isBlank()) {
			try {
				key = Integer.parseInt(parts[5].strip());
			}
			catch(NumberFormatException e) {
				throw new IllegalArgumentException("Bad key id " + parts[5]);
			}
		}
		boolean readable = parts.length <= 6 || !parts[6].strip().equalsIgnoreCase("H");
		return new CommercialChannel(name, rx, tx, digital, tone, key, readable).sanitized();
	}

	private static float mhz(String text, String what) {
		try {
			double mhz = Double.parseDouble(text.strip());
			if(mhz <= 0.0D || mhz > 3000.0D)
				throw new IllegalArgumentException("Bad " + what + " frequency " + text);
			return (float)(mhz * 1e6);
		}
		catch(NumberFormatException e) {
			throw new IllegalArgumentException("Bad " + what + " frequency " + text);
		}
	}
}
