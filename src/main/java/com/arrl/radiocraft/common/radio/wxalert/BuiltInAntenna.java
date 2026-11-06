package com.arrl.radiocraft.common.radio.wxalert;

import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaDataPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaNetwork;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.antenna.data.AntennaData;
import com.arrl.radiocraft.common.radio.antenna.data.RubberDuckyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.antenna.types.RubberDuckyAntennaType;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The short whip built into a Simple Weather Alert Receiver, used while nothing is coaxed to it: a receive-only antenna
 * on the VHF network, like a handheld's rubber duck, that hears a little less than a real antenna
 * ({@link #GAIN}). It hands the data bursts it hears to the receiver.
 */
public final class BuiltInAntenna implements IAntenna {

	/** What it hears of a signal a real antenna in its place would get. */
	public static final double GAIN = 0.5D;

	private final AntennaPos pos;
	private final BiConsumer<AntennaDataPacket, Double> owner;
	private final AntennaNetwork network = AntennaNetworkManager.getNetwork(AntennaNetworkManager.VHF_ID);
	private boolean registered = false;

	/** {@code owner} gets each data burst with the strength this antenna hears it at. */
	public BuiltInAntenna(Level level, BlockPos pos, BiConsumer<AntennaDataPacket, Double> owner) {
		this.pos = new AntennaPos(pos.immutable(), level);
		this.owner = owner;
	}

	/** Puts it on the air (true) or takes it off. */
	public void setActive(boolean active) {
		if(active == registered)
			return;
		if(active)
			network.addAntenna(this);
		else
			network.removeAntenna(this);
		registered = active;
	}

	public boolean isActive() {
		return registered;
	}

	@Override
	public void receiveDataPacket(AntennaDataPacket packet) {
		owner.accept(packet, packet.getStrength() * GAIN);
	}

	@Override
	public void transmitAudioPacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, Band band, float frequencyHertz, UUID sourcePlayer) {}

	@Override
	public void receiveAudioPacket(AntennaVoicePacket packet) {}

	@Override
	public void transmitCWPacket(ServerLevel level, Collection<CWBuffer> buffers, Band band, float frequencyHertz) {}

	@Override
	public void receiveCWPacket(AntennaCWPacket packet) {}

	@Override
	public AntennaPos getAntennaPos() {
		return pos;
	}

	@Override
	public IAntennaType<? extends AntennaData> getType() {
		return new RubberDuckyAntennaType();
	}

	@Override
	public AntennaData getData() {
		return new RubberDuckyAntennaData(0.155);
	}

	@Override
	public Player getPlayer() {
		return null;
	}
}
