package com.arrl.radiocraft.common.radio;

import com.arrl.radiocraft.Radiocraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every repeater the server has loaded, by the voice id it sends its own audio under (its courtesy beep, hang carrier
 * and Morse ID; relayed voice keeps the talker's id). A radio that hears audio under one of these ids has heard that
 * repeater ({@code RadioBlockEntity#getRepeatersHeard}), which is how the radio computer's Repeater Directory finds
 * repeaters (computer apps 2, 2026-11-06). Filled by {@code VHFRepeaterBlockEntity} (and so the commercial repeater) as it
 * loads, when its settings change and every few seconds; an entry stays while its chunk is unloaded (the radio may have
 * heard it just before), and goes when the repeater is broken or the server stops. Read from the voice thread.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class RepeaterRegistry {

	/**
	 * One repeater as users see it.
	 *
	 * @param dimension  Where it is.
	 * @param pos        Its world position (on a Sable ship, where the ship is).
	 * @param callsign   Its Morse ID callsign; empty if none is set.
	 * @param outputHz   What it transmits on (users listen here).
	 * @param inputHz    What it listens on (users transmit here).
	 * @param accessTone The CTCSS tone a user must send to open it, Hz; 0 for none.
	 * @param outputTone The CTCSS tone it sends, Hz; 0 for none.
	 * @param commercial True for a commercial repeater.
	 */
	public record Info(ResourceKey<Level> dimension, BlockPos pos, String callsign, float outputHz, float inputHz, float accessTone,
	                   float outputTone, boolean commercial) {

		/** True if this already says all of that (so a refresh makes nothing new). */
		public boolean matches(ResourceKey<Level> dimension, BlockPos pos, String callsign, float outputHz, float inputHz, float accessTone,
		                       float outputTone, boolean commercial) {
			return this.dimension == dimension && this.pos.equals(pos) && this.callsign.equals(callsign) && this.outputHz == outputHz
					&& this.inputHz == inputHz && this.accessTone == accessTone && this.outputTone == outputTone && this.commercial == commercial;
		}
	}

	private static final Map<UUID, Info> REPEATERS = new ConcurrentHashMap<>();

	private RepeaterRegistry() {}

	/**
	 * Records the repeater sending as {@code voiceId}. Allocates only when something changed, so a repeater can call it
	 * every few seconds.
	 */
	public static void update(UUID voiceId, ResourceKey<Level> dimension, BlockPos pos, String callsign, float outputHz, float inputHz,
	                          float accessTone, float outputTone, boolean commercial) {
		Info known = REPEATERS.get(voiceId);
		if(known != null && known.matches(dimension, pos, callsign, outputHz, inputHz, accessTone, outputTone, commercial))
			return;
		REPEATERS.put(voiceId, new Info(dimension, pos.immutable(), callsign, outputHz, inputHz, accessTone, outputTone, commercial));
	}

	/** The repeater broken: forgotten. */
	public static void remove(UUID voiceId) {
		REPEATERS.remove(voiceId);
	}

	/** The repeater sending as {@code voiceId}, or null (a player, a tape, nothing). Any thread, no allocation. */
	@Nullable
	public static Info get(@Nullable UUID voiceId) {
		return voiceId == null ? null : REPEATERS.get(voiceId);
	}

	/** True if {@code voiceId} is a repeater's own voice. */
	public static boolean isRepeater(@Nullable UUID voiceId) {
		return voiceId != null && REPEATERS.containsKey(voiceId);
	}

	@SubscribeEvent
	public static void serverStopped(ServerStoppedEvent event) { // The next world in single player starts with none.
		REPEATERS.clear();
	}
}
