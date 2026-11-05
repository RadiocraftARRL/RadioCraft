package com.arrl.radiocraft.common.realworld;

import com.arrl.radiocraft.common.radio.voice.RadiocraftVoicePlugin;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Real-World Interface's monitor speaker (1.12 open-question fixes): what goes out to the real radio, played quietly
 * at the block for players standing by it, as a real station's monitor speaker lets the room hear what is being sent.
 * Through Simple Voice Chat, like a radio's own speaker; nothing without a voice chat server. Any thread (the voice
 * chat thread plays it); one frame at a time.
 */
public final class MonitorSpeaker {

	/** How far it is heard, blocks: the room, not the street. */
	public static final float DISTANCE = 6.0F;
	/** Its loudness against a radio's own speaker. */
	public static final double GAIN = 0.35D;

	private final BlockPos pos;
	@Nullable
	private LocationalAudioChannel channel;
	@Nullable
	private OpusEncoder encoder;
	private long frames = 0L;

	public MonitorSpeaker(BlockPos pos) {
		this.pos = pos.immutable();
	}

	/** Plays one 20 ms frame (48 kHz mono, as voice chat takes it) quietly at the block. */
	public synchronized void play(ServerLevel level, short[] pcm) {
		frames++;
		if(RadiocraftVoicePlugin.API == null)
			return;
		if(channel == null) {
			channel = RadiocraftVoicePlugin.API.createLocationalAudioChannel(UUID.randomUUID(), RadiocraftVoicePlugin.API.fromServerLevel(level),
					RadiocraftVoicePlugin.API.createPosition(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
			if(channel == null)
				return;
			channel.setDistance(DISTANCE);
		}
		if(encoder == null)
			encoder = RadiocraftVoicePlugin.API.createEncoder();
		channel.send(encoder.encode(quieter(pcm)));
	}

	/** How many frames it has been given (played, or dropped with no voice chat server): for tests. */
	public synchronized long frames() {
		return frames;
	}

	/** {@code pcm} at {@link #GAIN}. */
	static short[] quieter(short[] pcm) {
		short[] out = new short[pcm.length];
		for(int i = 0; i < pcm.length; i++)
			out[i] = (short)Math.round(pcm[i] * GAIN);
		return out;
	}

	/** Lets go of the encoder (a new one is made if it plays again). */
	public synchronized void close() {
		if(encoder != null) {
			encoder.close();
			encoder = null;
		}
		channel = null;
	}
}
