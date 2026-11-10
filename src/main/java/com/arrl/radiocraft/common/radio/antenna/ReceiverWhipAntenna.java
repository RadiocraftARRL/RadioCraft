package com.arrl.radiocraft.common.radio.antenna;

import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.data.AntennaData;
import com.arrl.radiocraft.common.radio.antenna.data.RubberDuckyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.antenna.types.RubberDuckyAntennaType;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * The VHF receiver's own whip (2026-11-09), used while nothing is coaxed to it: it sits on the VHF antenna network at
 * the receiver and hears as a VHF handheld's rubber duck does. A handheld takes what reaches it as the sender worked it
 * out for where it stands (line of sight from another handheld, {@code HandheldRadiation}; a base antenna's own
 * pattern; a bare connector's few blocks), with no gain of its own on top, so this does the same, as if a handheld
 * were held at the block. It is receive only: it sends nothing. {@link com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity}
 * puts it on the air while the receiver runs with no antenna on its coax, in place of the bare connector.
 */
public class ReceiverWhipAntenna implements IAntenna {

	private final RadioBlockEntity radio;
	private final AntennaPos pos;
	private boolean registered = false;

	public ReceiverWhipAntenna(RadioBlockEntity radio) {
		this.radio = radio;
		this.pos = new AntennaPos(radio.getBlockPos().immutable(), radio.getLevel());
	}

	/** Puts it on the VHF network (true) or takes it off. */
	public void setActive(boolean active) {
		if(active == registered)
			return;
		if(active)
			AntennaNetworkManager.getNetwork(AntennaNetworkManager.VHF_ID).addAntenna(this);
		else
			AntennaNetworkManager.getNetwork(AntennaNetworkManager.VHF_ID).removeAntenna(this);
		registered = active;
	}

	public boolean isActive() {
		return registered;
	}

	@Override
	public void receiveAudioPacket(AntennaVoicePacket packet) {
		if(packet.getStrength() > 0.0D)
			radio.receiveVoice(packet);
	}

	@Override
	public void receiveDataPacket(AntennaDataPacket packet) {
		if(packet.getStrength() > 0.0D)
			radio.receiveData(packet);
	}

	@Override
	public void receiveCWPacket(AntennaCWPacket packet) {} // FM only: CW is not for it.

	@Override
	public void transmitAudioPacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, Band band, float frequencyHertz, UUID sourcePlayer) {}

	@Override
	public void transmitCWPacket(ServerLevel level, Collection<CWBuffer> buffers, Band band, float frequencyHertz) {}

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
