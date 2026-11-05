package com.arrl.radiocraft.common.realworld;

import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.common.blockentities.radio.RealWorldInterfaceBlockEntity;
import com.arrl.radiocraft.common.radio.Band;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Real-World Interfaces in Everywhere mode with a live link: each hears every voice frame that goes on the air in
 * its dimension near its frequency, at its source, with no distance, terrain or band conditions in the way. The
 * transmitters' one source hook ({@link com.arrl.radiocraft.common.moderation.ModerationHooks#voice}) calls
 * {@link #onAir}, on whatever thread is transmitting; while no interface is listening that is one empty-list read.
 */
public final class Everywhere {

	private static final List<RealWorldInterfaceBlockEntity> LISTENING = new CopyOnWriteArrayList<>();

	private Everywhere() {}

	public static void listen(RealWorldInterfaceBlockEntity listener) {
		if(!LISTENING.contains(listener))
			LISTENING.add(listener);
	}

	public static void stop(RealWorldInterfaceBlockEntity listener) {
		LISTENING.remove(listener);
	}

	public static boolean isListening(RealWorldInterfaceBlockEntity listener) {
		return LISTENING.contains(listener);
	}

	/** Server stopped: nobody is listening. */
	public static void clear() {
		LISTENING.clear();
	}

	/**
	 * A voice frame going out from {@code antenna}, sent by {@code radio}, on {@code hz}: handed to every listening
	 * interface in that level but the sender (see {@link RealWorldInterfaceBlockEntity#hearEverywhere}).
	 */
	public static void onAir(@Nullable Object antenna, Object radio, @Nullable Level level, @Nullable UUID source, short[] pcm, float hz, @Nullable Band band, float ctcss) {
		if(LISTENING.isEmpty() || !(antenna instanceof IAntenna from) || !(level instanceof ServerLevel serverLevel))
			return;
		for(RealWorldInterfaceBlockEntity listener : LISTENING)
			if(listener != radio && listener.getLevel() == level)
				listener.hearEverywhere(serverLevel, from, source, pcm, hz, band, ctcss);
	}
}
