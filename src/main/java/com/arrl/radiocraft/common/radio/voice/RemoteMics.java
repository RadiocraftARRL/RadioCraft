package com.arrl.radiocraft.common.radio.voice;

import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RIGCTL's remote microphone (2026-11-09): while a player holds rig control's PTT at a radio computer set to REMOTE
 * audio, their voice goes into the rig as the hand or desk mic would put it there, wherever the rig is: the mic gain,
 * the moderation hooks and the rest of {@link RadioBlockEntity#acceptVoicePacket}. Over a microwave link it goes
 * through a {@link LinkAudio.Line} first (delayed, rough, frames lost with the grade).
 * <p>
 * The game thread says who feeds which rig ({@link #feed}, renewed while the PTT is held, lapsing on its own after
 * {@link #HOLD_NANOS}); the voice thread hands each microphone frame over ({@link #accept}): one map lookup per
 * frame, nothing else when the player feeds nothing.
 */
public final class RemoteMics {

	private RemoteMics() {}

	/** A feed not renewed for this long is ignored (the game thread stopped renewing it: PTT let go, server stalled). */
	public static final long HOLD_NANOS = 1_000_000_000L;

	/** One player's voice into one rig. */
	private static final class Feed {
		final RadioBlockEntity rig;
		final LinkAudio.Impairment impairment;
		@Nullable
		final LinkAudio.Line line;
		volatile long until;

		Feed(RadioBlockEntity rig, LinkAudio.Impairment impairment) {
			this.rig = rig;
			this.impairment = impairment;
			this.line = impairment.clean() ? null : new LinkAudio.Line(impairment);
		}
	}

	private static final Map<UUID, Feed> FEEDS = new ConcurrentHashMap<>();

	/** {@code player}'s voice goes into {@code rig} over a link that does {@code impairment}; renew it at least once a second. Game thread. */
	public static void feed(UUID player, RadioBlockEntity rig, LinkAudio.Impairment impairment) {
		Feed feed = FEEDS.get(player);
		if(feed == null || feed.rig != rig || !feed.impairment.equals(impairment)) {
			feed = new Feed(rig, impairment);
			FEEDS.put(player, feed);
		}
		feed.until = System.nanoTime() + HOLD_NANOS;
	}

	/** {@code player}'s voice no longer goes into a rig remotely. */
	public static void stop(UUID player) {
		FEEDS.remove(player);
	}

	/** Forgets every feed (the server stopped). */
	public static void clear() {
		FEEDS.clear();
	}

	/** The rig {@code player} feeds now, or null. */
	@Nullable
	public static RadioBlockEntity feeding(UUID player) {
		Feed feed = FEEDS.get(player);
		return feed == null || System.nanoTime() > feed.until ? null : feed.rig;
	}

	/**
	 * A microphone frame from {@code player}: into the rig they feed, if any and it is keyed. Returns that rig, so the
	 * caller skips it among the microphones in earshot (it must not get the frame twice), or null. Voice thread.
	 */
	@Nullable
	public static RadioBlockEntity accept(de.maxhenkel.voicechat.api.ServerLevel level, short[] pcm, UUID player) {
		Feed feed = FEEDS.get(player);
		long now = System.nanoTime();
		if(feed == null || now > feed.until || feed.rig.isRemoved())
			return null;
		if(feed.line == null) {
			if(feed.rig.canTransmitVoice())
				feed.rig.acceptVoicePacket(level, pcm, player);
			return feed.rig;
		}
		feed.line.offer(pcm, now);
		send(feed, level, player, now);
		return feed.rig;
	}

	/** Sends what waited on {@code player}'s link once its delay is up (between voice frames). Game thread. */
	public static void drain(UUID player, net.minecraft.server.level.ServerLevel level) {
		Feed feed = FEEDS.get(player);
		if(feed == null || feed.line == null || feed.line.waiting() == 0)
			return;
		send(feed, () -> level, player, System.nanoTime());
	}

	private static void send(Feed feed, de.maxhenkel.voicechat.api.ServerLevel level, UUID player, long now) {
		if(feed.line == null)
			return;
		for(short[] frame : feed.line.due(now))
			if(feed.rig.canTransmitVoice())
				feed.rig.acceptVoicePacket(level, frame, player);
	}
}
