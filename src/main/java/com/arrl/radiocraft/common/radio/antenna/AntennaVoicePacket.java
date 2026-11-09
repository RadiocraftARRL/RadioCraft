package com.arrl.radiocraft.common.radio.antenna;

import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaPacket;
import com.arrl.radiocraft.common.radio.Band;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public class AntennaVoicePacket implements IAntennaPacket {

	private final de.maxhenkel.voicechat.api.ServerLevel level; // This is a voice api ServerLevel not a minecraft one.
	private final short[] rawAudio;
	private final Band band;
	private final float frequency;
	private double strength;
	private final IAntenna source;
	private IAntenna destination = null; // Set by the transmitting antenna for each receiver, for terrain.
	private final UUID sourcePlayer;
	private final float ctcssTone; // Sub-audible tone under the voice, 0 for none (see Ctcss).
	// Analog or digital (and its key), from the transmitter: whatever it pushed on this thread (DigitalVoice#push).
	private final com.arrl.radiocraft.common.radio.voice.DigitalVoice.Mode voiceMode = com.arrl.radiocraft.common.radio.voice.DigitalVoice.current();
	// SSB, AM or FM: what the transmitter pushed on this thread (Modulation#push), else its band's usual (2026-11-08).
	private final com.arrl.radiocraft.common.radio.voice.Modulation modulation;
	// The sideband of an SSB signal (true lower): what the transmitter pushed, else hams' habit, LSB below 10 MHz.
	private final boolean lowerSideband;


	public AntennaVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, Band band, float frequency, double strength, IAntenna source, UUID sourcePlayer) {
		this(level, rawAudio, band, frequency, strength, source, sourcePlayer, 0.0F);
	}

	public AntennaVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, Band band, float frequency, double strength, IAntenna source, UUID sourcePlayer, float ctcssTone) {
		this.ctcssTone = ctcssTone;
		this.level = level;
		this.rawAudio = rawAudio;
		this.band = band;
		this.frequency = frequency;
		this.strength = strength;
		this.source = source;
		this.sourcePlayer = sourcePlayer;
		com.arrl.radiocraft.common.radio.voice.Modulation pushed = com.arrl.radiocraft.common.radio.voice.Modulation.current();
		this.modulation = pushed != null ? pushed : com.arrl.radiocraft.common.radio.voice.Modulation.usual(band);
		Boolean lower = com.arrl.radiocraft.common.radio.voice.Modulation.currentLowerSideband();
		this.lowerSideband = lower != null ? lower : frequency < 10_000_000.0F;
	}

	private volatile double envelope = -1.0D;

	/**
	 * How loud the voice is in this frame, 0 to 1 ({@link com.arrl.radiocraft.common.radio.voice.ReceiverMix#ssbEnvelope}),
	 * worked out the first time it is asked (only a watched waterfall and an SSB receiver ask) and kept (2026-11-09).
	 */
	public double envelope() {
		double e = envelope;
		if(e < 0.0D)
			envelope = e = com.arrl.radiocraft.common.radio.voice.ReceiverMix.ssbEnvelope(rawAudio);
		return e;
	}

	/** True if an SSB signal is on the lower sideband. */
	public boolean isLowerSideband() {
		return lowerSideband;
	}

	/** How the transmitter modulated this: what a receiver in another mode makes of it is {@link com.arrl.radiocraft.common.radio.voice.ModeMix}. */
	public com.arrl.radiocraft.common.radio.voice.Modulation getModulation() {
		return modulation;
	}

	@Override
	public Band getBand() {
		return band;
	}

	@Override
	public float getFrequency() {
		return frequency;
	}

	@Override
	public double getStrength() {
		return strength;
	}

	@Override
	public IAntenna getDestination() {
		return destination;
	}

	public void setDestination(IAntenna destination) {
		this.destination = destination;
	}

	@Override
	public IAntenna getSource() {
		return source;
	}

	public ServerLevel getLevel() {
		return (ServerLevel)level.getServerLevel();
	}

	public short[] getRawAudio() {
		return rawAudio;
	}

	public void setStrength(double strength) {
		this.strength = strength;
	}

	public UUID getSourcePlayer() {
		return sourcePlayer;
	}

	/** The CTCSS tone the transmitter sent, 0 for none. */
	public float getCtcssTone() {
		return ctcssTone;
	}

	/** Analog FM, or digital voice with its encryption key id (see {@link com.arrl.radiocraft.common.radio.voice.DigitalVoice}). */
	public com.arrl.radiocraft.common.radio.voice.DigitalVoice.Mode getVoiceMode() {
		return voiceMode;
	}

}
