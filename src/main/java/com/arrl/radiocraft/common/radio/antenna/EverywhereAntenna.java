package com.arrl.radiocraft.common.radio.antenna;

import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.common.blockentities.radio.RealWorldInterfaceBlockEntity;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.data.AntennaData;
import com.arrl.radiocraft.common.radio.antenna.data.RubberDuckyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.antenna.types.RubberDuckyAntennaType;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The air of a Real-World Interface in Everywhere mode (1.12): no antenna, no propagation. What it sends reaches every
 * antenna in its dimension on the network of its band at {@link #STRENGTH}, however far (each still applies its own
 * gain and pattern). It hears nothing through the network: the interface hears every transmission at its source
 * instead ({@link com.arrl.radiocraft.common.realworld.Everywhere}), so distance and terrain cannot cut it off. Voice
 * only: no CW or data. Not recorded for direction finders, which have nothing to point at.
 */
public class EverywhereAntenna implements IAntenna {

	/** How strong its signal arrives everywhere: full scale, as from a station next door without the overload. */
	public static final double STRENGTH = 1.0D;

	private final RealWorldInterfaceBlockEntity owner;
	private final AntennaPos pos;
	private ResourceLocation networkId = null;

	public EverywhereAntenna(RealWorldInterfaceBlockEntity owner) {
		this.owner = owner;
		this.pos = new AntennaPos(owner.getBlockPos(), owner.getLevel());
	}

	public RealWorldInterfaceBlockEntity getOwner() {
		return owner;
	}

	/** Puts it on the network of {@code band} (HF or VHF), moving it if the band changed; null takes it off. */
	public void register(Band band) {
		ResourceLocation wanted = band == null ? null : band.isVhf() ? AntennaNetworkManager.VHF_ID : AntennaNetworkManager.HF_ID;
		if(wanted != null && wanted.equals(networkId))
			return;
		unregister();
		if(wanted != null) {
			AntennaNetworkManager.getNetwork(wanted).addAntenna(this);
			networkId = wanted;
		}
	}

	public void unregister() {
		if(networkId != null) {
			AntennaNetworkManager.getNetwork(networkId).removeAntenna(this);
			networkId = null;
		}
	}

	public boolean isRegistered() {
		return networkId != null;
	}

	private List<IAntenna> others() {
		if(networkId == null)
			return List.of();
		Set<IAntenna> all = AntennaNetworkManager.getNetwork(networkId).allAntennas();
		List<IAntenna> out = new ArrayList<>();
		synchronized(all) {
			for(IAntenna antenna : all) {
				AntennaPos there = antenna.getAntennaPos();
				if(antenna != this && there != null && there.level() == pos.level())
					out.add(antenna);
			}
		}
		return out;
	}

	@Override
	public void transmitAudioPacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, Band band, float frequencyHertz, UUID sourcePlayer) {
		transmitAudioPacket(level, rawAudio, band, frequencyHertz, sourcePlayer, 0.0F);
	}

	@Override
	public void transmitAudioPacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, Band band, float frequencyHertz, UUID sourcePlayer, float ctcssTone) {
		for(IAntenna other : others()) {
			AntennaVoicePacket packet = new AntennaVoicePacket(level, rawAudio.clone(), band, frequencyHertz, STRENGTH, this, sourcePlayer, ctcssTone);
			packet.setDestination(other);
			other.receiveAudioPacket(packet);
		}
	}

	/** Heard at the source instead (see the class comment). */
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
		return new RubberDuckyAntennaType(); // Something harmless for code that asks; nothing about it is used.
	}

	@Override
	public AntennaData getData() {
		return new RubberDuckyAntennaData(0.0D);
	}

	@Override
	public Player getPlayer() {
		return null;
	}
}
