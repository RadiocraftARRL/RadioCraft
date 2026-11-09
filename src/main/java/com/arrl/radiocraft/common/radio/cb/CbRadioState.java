package com.arrl.radiocraft.common.radio.cb;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a CB radio's knobs and switches are set to (2026-11-08). Stored as the {@code radiocraft:cb_radio} data
 * component on the CB items (the portable keeps it in the hand; a mobile's item carries it when broken) and in the
 * mobiles' block entities once placed. No game classes beyond the codecs.
 *
 * @param channel    1 to 40 ({@link CbChannels})
 * @param compressor the 5875's SPEECH COMPRESSION switch on MAX: punchier audio and a little more talk power
 * @param swrMeter   the 5875's MOD/SWR switch: its right-hand meter reads SWR (true) or modulation (false)
 * @param squelch    0 to 10 (0 open): the speaker stays shut until the RX meter reads at least squelch / 10
 * @param volume     0 to 10 (the volume knob; 0 is OFF, which switches the radio off)
 * @param mode       AM, USB or LSB on the 5875 (its FUNCTION knob), AM or FM on the PC68 (2026-11-09)
 * @param clarifier  -10 to 10: the receiver tuned that many 100 Hz steps off the channel (the 5875's CLAR)
 * @param rfGain     0 to 10 (10 full): the receiver's front end turned down, signals and static with it (the 5875's RF GAIN)
 * @param nb         the noise blanker: blanks impulse noise (sparks, redstone, crackle)
 * @param anl        the automatic noise limiter: clips what the blanker leaves, a little less well
 */
public record CbRadioState(int channel, boolean compressor, boolean swrMeter, int squelch, int volume,
		Mode mode, int clarifier, int rfGain, boolean nb, boolean anl) {

	/** How a CB radio is modulated: AM (all of them), the 5875's sidebands, the PC68's FM. */
	public enum Mode {
		AM, USB, LSB, FM;

		public boolean ssb() {
			return this == USB || this == LSB;
		}
	}

	/** Out of the box: channel 19, compressor off, meter on modulation, squelch open, volume off (the radio is off), AM, RF gain full. */
	public static final CbRadioState DEFAULT = new CbRadioState(CbChannels.HIGHWAY, false, false, 0, 0);
	public static final int MAX_KNOB = 10;
	/** Clarifier steps either side of the channel, and how far each step tunes. */
	public static final int MAX_CLARIFIER = 10, CLARIFIER_STEP_HZ = 100;
	/** Transmit power against a standard radio (1) with the compressor on: more average modulation, more talk power. */
	public static final float COMPRESSOR_TALK_POWER = 1.15F;
	/** SSB against AM on CB: 12 W PEP against a 4 W carrier, all of it in the voice. */
	public static final float SSB_TALK_POWER = 1.4F;

	public CbRadioState {
		channel = CbChannels.clamp(channel);
		squelch = Math.max(0, Math.min(MAX_KNOB, squelch));
		volume = Math.max(0, Math.min(MAX_KNOB, volume));
		mode = mode == null ? Mode.AM : mode;
		clarifier = Math.max(-MAX_CLARIFIER, Math.min(MAX_CLARIFIER, clarifier));
		rfGain = Math.max(0, Math.min(MAX_KNOB, rfGain));
	}

	/** The original five controls, the rest as they come out of the box (AM, clarifier centred, RF gain full, NB and ANL off). */
	public CbRadioState(int channel, boolean compressor, boolean swrMeter, int squelch, int volume) {
		this(channel, compressor, swrMeter, squelch, volume, Mode.AM, 0, MAX_KNOB, false, false);
	}

	private static final Codec<Mode> MODE_CODEC = Codec.STRING.xmap(s -> {
		try {
			return Mode.valueOf(s);
		}
		catch(IllegalArgumentException e) {
			return Mode.AM;
		}
	}, Mode::name);

	public static final Codec<CbRadioState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("channel", CbChannels.HIGHWAY).forGetter(CbRadioState::channel),
			Codec.BOOL.optionalFieldOf("compressor", false).forGetter(CbRadioState::compressor),
			Codec.BOOL.optionalFieldOf("swr_meter", false).forGetter(CbRadioState::swrMeter),
			Codec.INT.optionalFieldOf("squelch", 0).forGetter(CbRadioState::squelch),
			Codec.INT.optionalFieldOf("volume", 0).forGetter(CbRadioState::volume),
			MODE_CODEC.optionalFieldOf("mode", Mode.AM).forGetter(CbRadioState::mode),
			Codec.INT.optionalFieldOf("clarifier", 0).forGetter(CbRadioState::clarifier),
			Codec.INT.optionalFieldOf("rf_gain", MAX_KNOB).forGetter(CbRadioState::rfGain),
			Codec.BOOL.optionalFieldOf("nb", false).forGetter(CbRadioState::nb),
			Codec.BOOL.optionalFieldOf("anl", false).forGetter(CbRadioState::anl)
	).apply(instance, CbRadioState::new));

	public static final StreamCodec<ByteBuf, CbRadioState> STREAM_CODEC = StreamCodec.of((buf, s) -> {
		ByteBufCodecs.VAR_INT.encode(buf, s.channel());
		ByteBufCodecs.VAR_INT.encode(buf, s.squelch());
		ByteBufCodecs.VAR_INT.encode(buf, s.volume());
		ByteBufCodecs.VAR_INT.encode(buf, s.mode().ordinal());
		ByteBufCodecs.VAR_INT.encode(buf, s.clarifier() + MAX_CLARIFIER);
		ByteBufCodecs.VAR_INT.encode(buf, s.rfGain());
		int flags = (s.compressor() ? 1 : 0) | (s.swrMeter() ? 2 : 0) | (s.nb() ? 4 : 0) | (s.anl() ? 8 : 0);
		ByteBufCodecs.VAR_INT.encode(buf, flags);
	}, buf -> {
		int channel = ByteBufCodecs.VAR_INT.decode(buf), squelch = ByteBufCodecs.VAR_INT.decode(buf), volume = ByteBufCodecs.VAR_INT.decode(buf);
		int mode = ByteBufCodecs.VAR_INT.decode(buf), clarifier = ByteBufCodecs.VAR_INT.decode(buf) - MAX_CLARIFIER, rfGain = ByteBufCodecs.VAR_INT.decode(buf);
		int flags = ByteBufCodecs.VAR_INT.decode(buf);
		return new CbRadioState(channel, (flags & 1) != 0, (flags & 2) != 0, squelch, volume,
				Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, mode))], clarifier, rfGain, (flags & 4) != 0, (flags & 8) != 0);
	});

	/** The controls a player can work, sent from the screens. New keys go on the end (packets send the ordinal). */
	public enum Key {
		/** The channel knob, {@code value} clicks. */
		CHANNEL,
		/** The PC68's CH9 switch: straight to channel 9. */
		CHANNEL_9,
		COMPRESSOR,
		METER,
		/** The squelch knob, {@code value} steps. */
		SQUELCH,
		/** The volume knob, {@code value} steps. */
		VOLUME,
		/** The 5875's FUNCTION knob: AM, USB, LSB, {@code value} clicks. */
		FUNCTION,
		/** The PC68's AM/FM switch. */
		AM_FM,
		/** The clarifier, {@code value} steps of 100 Hz. */
		CLARIFIER,
		/** The RF gain knob, {@code value} steps. */
		RF_GAIN,
		NB,
		ANL,
		/** The PC68's NB/ANL switch: both at once. */
		NB_ANL
	}

	/** {@code key} worked with {@code value}. */
	public CbRadioState press(Key key, int value) {
		return switch(key) {
			case CHANNEL -> withChannel(CbChannels.turn(channel, value));
			case CHANNEL_9 -> withChannel(CbChannels.EMERGENCY);
			case COMPRESSOR -> new CbRadioState(channel, !compressor, swrMeter, squelch, volume, mode, clarifier, rfGain, nb, anl);
			case METER -> new CbRadioState(channel, compressor, !swrMeter, squelch, volume, mode, clarifier, rfGain, nb, anl);
			case SQUELCH -> new CbRadioState(channel, compressor, swrMeter, squelch + value, volume, mode, clarifier, rfGain, nb, anl);
			case VOLUME -> withVolume(volume + value);
			case FUNCTION -> withMode(Mode.values()[Math.max(0, Math.min(2, sidebandIndex() + value))]);
			case AM_FM -> withMode(mode == Mode.FM ? Mode.AM : Mode.FM);
			case CLARIFIER -> new CbRadioState(channel, compressor, swrMeter, squelch, volume, mode, clarifier + value, rfGain, nb, anl);
			case RF_GAIN -> new CbRadioState(channel, compressor, swrMeter, squelch, volume, mode, clarifier, rfGain + value, nb, anl);
			case NB -> new CbRadioState(channel, compressor, swrMeter, squelch, volume, mode, clarifier, rfGain, !nb, anl);
			case ANL -> new CbRadioState(channel, compressor, swrMeter, squelch, volume, mode, clarifier, rfGain, nb, !anl);
			case NB_ANL -> new CbRadioState(channel, compressor, swrMeter, squelch, volume, mode, clarifier, rfGain, !(nb && anl), !(nb && anl));
		};
	}

	/** Where the FUNCTION knob points: AM 0, USB 1, LSB 2 (FM, which it cannot reach, counts as AM). */
	private int sidebandIndex() {
		return mode == Mode.FM ? 0 : mode.ordinal();
	}

	/** {@link #press} from a packet's key number; unchanged for a number that is no key. */
	public CbRadioState press(int key, int value) {
		return key >= 0 && key < Key.values().length ? press(Key.values()[key], value) : this;
	}

	public CbRadioState withChannel(int newChannel) {
		return new CbRadioState(newChannel, compressor, swrMeter, squelch, volume, mode, clarifier, rfGain, nb, anl);
	}

	public CbRadioState withVolume(int newVolume) {
		return new CbRadioState(channel, compressor, swrMeter, squelch, newVolume, mode, clarifier, rfGain, nb, anl);
	}

	public CbRadioState withMode(Mode newMode) {
		return new CbRadioState(channel, compressor, swrMeter, squelch, volume, newMode, clarifier, rfGain, nb, anl);
	}

	/** Without the 5875's extras (compressor, MOD/SWR, sidebands, clarifier, RF gain), as the PC68 has none. */
	public CbRadioState withoutDeluxeControls() {
		return new CbRadioState(channel, false, false, squelch, volume, mode.ssb() ? Mode.AM : mode, 0, MAX_KNOB, nb, anl);
	}

	/** Without FM, which the 5875 has no switch for. */
	public CbRadioState withoutFm() {
		return mode == Mode.FM ? withMode(Mode.AM) : this;
	}

	/** The clarifier's offset, Hz. */
	public int clarifierHz() {
		return clarifier * CLARIFIER_STEP_HZ;
	}

	/** The RF gain as a factor on what the receiver takes in: full at 10, a twentieth at 0. */
	public double rfGainFactor() {
		return 0.05D + 0.95D * rfGain / (double)MAX_KNOB;
	}

	/** How much impulse noise (sparks, redstone, aurora crackle) gets through: NB blanks most, ANL clips some. */
	public double impulseNoiseFactor() {
		return impulseNoiseFactor(nb, anl);
	}

	public static double impulseNoiseFactor(boolean nb, boolean anl) {
		return (nb ? 0.3D : 1.0D) * (anl ? 0.6D : 1.0D);
	}

	public double hz() {
		return CbChannels.hz(channel);
	}

	/** "19" */
	public String displayLine() {
		return String.format(java.util.Locale.ROOT, "%02d", channel);
	}

	/** "27.185 AM" */
	public String displayStatus() {
		return CbChannels.mhz(channel) + " " + mode.name();
	}
}
