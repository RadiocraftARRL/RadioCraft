package com.arrl.radiocraft.common.radio.distress;

import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.TransmitterLog;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.antenna.data.AntennaData;
import com.arrl.radiocraft.common.radio.antenna.data.RubberDuckyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.antenna.types.RubberDuckyAntennaType;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import com.arrl.radiocraft.common.radio.voice.handheld.HandheldRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * How a distress beacon goes on the air (2026-11-09), wherever it is (in an inventory, floating as an item): from its
 * stubby whip like a handheld's rubber duck ({@link HandheldRadiation}: line of sight, hills and roofs in the way), on
 * commercial VHF at {@link DistressPackets#BEACON_HZ}. Its alerts are data packets; its homing tone is a packet with
 * nothing in it, several times a second, that direction finders take a bearing on (and handheld direction finders
 * find in the {@link TransmitterLog}, under the beacon's own key).
 */
public final class DistressBeacons {

	private DistressBeacons() {}

	/** Ticks between homing tone packets: often enough that direction finders stay locked on. */
	public static final int HOMING_TICKS = 8;

	/** Sends {@code alert} from {@code pos}, carried by {@code holder} (null when it floats). */
	public static void alert(ServerLevel level, BlockPos pos, @Nullable Player holder, DistressAlert alert) {
		send(level, pos, holder, DistressPackets.packet(level.getRandom().nextLong(), alert), true);
	}

	/** One homing tone packet from {@code pos}; {@code key} stands for this beacon in the transmitter log. */
	public static void homing(ServerLevel level, BlockPos pos, @Nullable Player holder, Object key) {
		TransmitterLog.record(key, level, pos, MarineRadios.band(), DistressPackets.BEACON_HZ);
		send(level, pos, holder, DistressPackets.homing(level.getRandom().nextLong()), false);
	}

	private static void send(ServerLevel level, BlockPos pos, @Nullable Player holder, TncPacket packet, boolean logged) {
		Band band = MarineRadios.band();
		HandheldRadiation.data(new Whip(level, pos, holder), AntennaNetworkManager.getNetwork(AntennaNetworkManager.VHF_ID), level, packet, band,
				DistressPackets.BEACON_HZ, logged);
	}

	/** The beacon's whip for one burst: it only sends, and is never put on the antenna network. */
	private record Whip(ServerLevel level, BlockPos pos, @Nullable Player holder) implements IAntenna {

		@Override
		public void transmitAudioPacket(de.maxhenkel.voicechat.api.ServerLevel voiceLevel, short[] rawAudio, Band band, float frequencyHertz, UUID sourcePlayer) {}

		@Override
		public void receiveAudioPacket(AntennaVoicePacket packet) {}

		@Override
		public void transmitCWPacket(ServerLevel serverLevel, Collection<CWBuffer> buffers, Band band, float frequencyHertz) {}

		@Override
		public void receiveCWPacket(AntennaCWPacket packet) {}

		@Override
		public AntennaPos getAntennaPos() {
			return new AntennaPos(pos, level);
		}

		@Override
		public IAntennaType<? extends AntennaData> getType() {
			return new RubberDuckyAntennaType();
		}

		@Override
		public AntennaData getData() {
			return new RubberDuckyAntennaData(0.0D);
		}

		@Override
		public Player getPlayer() {
			return holder;
		}
	}
}
