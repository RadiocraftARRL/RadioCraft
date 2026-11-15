package com.arrl.radiocraft.common.radio.satellite;

import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.antenna.types.vhf.SatelliteTrackerAntennaType;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Moonbounce (EME, 2026-11-12): CW on VHF sent through a satellite tracking antenna locked on the {@link Moon} comes
 * back {@link Moon#ECHO_TICKS} later to every tracker locked on the Moon, the sender's own included (hearing your own
 * echo is how a real EME station checks itself). The echo is very weak and fades in and out ({@link Moon#echo}); space
 * weather fades it further. Nothing else reaches the Moon and back: no voice, no other antenna. Server thread.
 */
public final class MoonBounce {

	private record Pending(long dueTick, List<CWBuffer> buffers, Band band, float hz, IAntenna from) {}

	private static final List<Pending> PENDING = new ArrayList<>();

	private MoonBounce() {}

	/** Server stopped: nothing on its way. */
	public static void clear() {
		synchronized(PENDING) {
			PENDING.clear();
		}
	}

	/** True if {@code antenna} is a tracker locked on the Moon now. */
	public static boolean onTheMoon(@Nullable Object antenna) {
		if(!(antenna instanceof IAntenna a) || !(a.getType() instanceof SatelliteTrackerAntennaType))
			return false;
		IAntenna.AntennaPos at = a.getAntennaPos();
		return at != null && SatelliteTracking.lockedOn(at.position().asLong(), Moon.NAME);
	}

	/** CW going out from {@code antenna} on {@code hz}: off to the Moon, if it is aimed there on VHF. */
	public static void onCW(@Nullable Object antenna, @Nullable Level level, Collection<CWBuffer> buffers, float hz) {
		if(!(level instanceof ServerLevel server) || level.dimension() != Level.OVERWORLD || !onTheMoon(antenna) || !Moon.usable(level.getTimeOfDay(1.0F)))
			return;
		Band band = Band.getBand(hz);
		if(band == null || !band.isVhf())
			return;
		List<CWBuffer> copy = new ArrayList<>();
		for(CWBuffer buffer : buffers)
			copy.add(new CWBuffer(buffer.getId(), buffer.getInputs().clone()));
		synchronized(PENDING) {
			PENDING.add(new Pending(server.getGameTime() + Moon.ECHO_TICKS, copy, band, hz, (IAntenna)antenna));
		}
	}

	/** Every tick, in the overworld: echoes whose time has come go down to every tracker on the Moon. */
	static void tick(ServerLevel level, long now) {
		List<Pending> due = new ArrayList<>();
		synchronized(PENDING) {
			if(PENDING.isEmpty())
				return;
			PENDING.removeIf(p -> {
				if(p.dueTick() > now)
					return false;
				due.add(p);
				return true;
			});
		}
		if(due.isEmpty() || !Moon.usable(level.getTimeOfDay(1.0F)))
			return; // Set while they were on their way: lost.
		Set<IAntenna> all = AntennaNetworkManager.getNetwork(AntennaNetworkManager.VHF_ID).allAntennas();
		List<IAntenna> listeners;
		synchronized(all) {
			listeners = new ArrayList<>(all);
		}
		long seed = level.getDayTime() / 24000L * 31L + 7L; // Each night fades its own way.
		for(Pending p : due)
			for(IAntenna other : listeners) {
				if(!onTheMoon(other) || other.getAntennaPos() == null || other.getAntennaPos().level() != level)
					continue;
				double strength = Moon.echo(now, seed) * SatellitePattern.weather(SatelliteRadio.aurora(level, other.getAntennaPos().position()), SatelliteRadio.storm(level));
				List<CWBuffer> buffers = new ArrayList<>();
				for(CWBuffer buffer : p.buffers())
					buffers.add(new CWBuffer(buffer.getId(), buffer.getInputs().clone()));
				AntennaCWPacket packet = new AntennaCWPacket(level, buffers, p.band(), p.hz(), strength, p.from());
				packet.setDestination(other);
				other.receiveCWPacket(packet);
			}
	}
}
