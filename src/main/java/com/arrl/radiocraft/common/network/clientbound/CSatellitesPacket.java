package com.arrl.radiocraft.common.network.clientbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatelliteGeo;
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Every satellite on orbit, for the dots in the night sky (2026-11-11): its name, whether it is amateur (green) or
 * commercial (red), its orbit, and an admin's pin if one holds it over a point. Sent on login and whenever the list
 * changes. The client works out where each one is from the world time, as the server does.
 *
 * @param satellites Every satellite on orbit.
 */
public record CSatellitesPacket(List<Entry> satellites) implements CustomPacketPayload {

	/**
	 * One satellite as the sky shows it; since 2026-11-12 with where a geostationary one parks ({@code geo}, null for
	 * one on an ordinary orbit) and whether it is decommissioned (no flashing, only the tumble glint).
	 */
	public record Entry(String name, boolean amateur, SatelliteOrbit orbit, double pinX, double pinZ, long pinUntil, long pinFrom, @Nullable SatelliteGeo geo,
	                    boolean decommissioned) {

		/** What the place {@code x, z} sees of it at {@code gameTime}. */
		public SatelliteOrbit.Look look(double x, double z, long gameTime) {
			if(pinUntil >= 0 && gameTime < pinUntil && pinFrom < 0)
				return SatelliteOrbit.lookAt(x, z, pinX, pinZ);
			if(pinUntil >= 0 && gameTime < pinUntil)
				return SatelliteOrbit.passOver(x, z, pinX, pinZ, pinFrom, pinUntil, gameTime, orbit.heading(), name.hashCode());
			return geo != null ? geo.look(x, z) : orbit.look(x, z, gameTime);
		}
	}

	/** On a client: the list the server sent last (read by {@code SatelliteSkyRenderer}). */
	private static volatile List<Entry> received = List.of();

	/** On a client: every satellite the server says is on orbit. */
	public static List<Entry> received() {
		return received;
	}

	/** On a client leaving a world: none. */
	public static void forget() {
		received = List.of();
	}

	/** Most satellites sent (more are left out of the sky). */
	public static final int MAX = 256;

	public static final CustomPacketPayload.Type<CSatellitesPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("c_satellites"));

	private static final StreamCodec<ByteBuf, Entry> ENTRY = StreamCodec.of(
			(buf, e) -> {
				ByteBufCodecs.stringUtf8(32).encode(buf, e.name());
				buf.writeBoolean(e.amateur());
				buf.writeDouble(e.orbit().heading());
				buf.writeDouble(e.orbit().alongPhase());
				buf.writeDouble(e.orbit().crossPhase());
				buf.writeDouble(e.orbit().drift());
				buf.writeDouble(e.pinX());
				buf.writeDouble(e.pinZ());
				buf.writeLong(e.pinUntil());
				buf.writeLong(e.pinFrom());
				buf.writeBoolean(e.decommissioned());
				buf.writeBoolean(e.geo() != null);
				if(e.geo() != null) {
					buf.writeDouble(e.geo().aimX());
					buf.writeDouble(e.geo().aimZ());
					buf.writeDouble(e.geo().parkX());
					buf.writeDouble(e.geo().parkZ());
				}
			},
			buf -> {
				String name = ByteBufCodecs.stringUtf8(32).decode(buf);
				boolean amateur = buf.readBoolean();
				SatelliteOrbit orbit = new SatelliteOrbit(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
				double pinX = buf.readDouble(), pinZ = buf.readDouble();
				long pinUntil = buf.readLong(), pinFrom = buf.readLong();
				boolean decommissioned = buf.readBoolean();
				SatelliteGeo geo = buf.readBoolean() ? new SatelliteGeo(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
				return new Entry(name, amateur, orbit, pinX, pinZ, pinUntil, pinFrom, geo, decommissioned);
			});

	public static final StreamCodec<ByteBuf, CSatellitesPacket> STREAM_CODEC = StreamCodec.composite(
			ENTRY.apply(ByteBufCodecs.list(MAX)), CSatellitesPacket::satellites,
			CSatellitesPacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CSatellitesPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> received = List.copyOf(packet.satellites()));
	}

	/** Sends {@code player} the list. */
	public static void sendTo(ServerPlayer player, List<Satellite> satellites) {
		List<Entry> entries = new ArrayList<>();
		for(Satellite s : satellites)
			if(entries.size() < MAX)
				entries.add(new Entry(s.name, s.amateur, s.orbit, s.pinX(), s.pinZ(), s.pinUntil(), s.pinFrom(), s.geo, s.decommissioned()));
		PacketDistributor.sendToPlayer(player, new CSatellitesPacket(entries));
	}
}
