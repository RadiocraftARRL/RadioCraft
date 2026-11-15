package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.api.blockentities.radio.ICWReceiver;
import com.arrl.radiocraft.api.blockentities.radio.ICWTransmitter;
import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.client.blockentity.RadioBlockEntityClientHandler;
import com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.network.clientbound.CCWBufferPacket;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import com.arrl.radiocraft.common.radio.morse.CWReceiveBuffer;
import com.arrl.radiocraft.common.radio.morse.CWSendBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class HFRadioBlockEntity extends RadioBlockEntity implements ICWReceiver, ICWTransmitter {

	protected boolean cwEnabled = false;

	protected CWReceiveBuffer cwReceiveBuffer;
	protected CWSendBuffer cwSendBuffer;
	protected final ConcurrentLinkedQueue<QueuedCW> parrotCWQueue = new ConcurrentLinkedQueue<>();

	public HFRadioBlockEntity(BlockEntityType<? extends HFRadioBlockEntity> type, BlockPos pos, BlockState state, Band band) {
		super(type, pos, state, band);
	}

	// -------------------- CW/MORSE IMPLEMENTATION --------------------

	@Override
	public CWReceiveBuffer getCWReceiveBuffer() {
		if(cwReceiveBuffer == null)
			cwReceiveBuffer = new CWReceiveBuffer();
		return cwReceiveBuffer;
	}

	@Override
	public CWSendBuffer getCWSendBuffer() {
		if(cwSendBuffer == null)
			cwSendBuffer = new CWSendBuffer(worldPosition);
		return cwSendBuffer;
	}

	@Override
	public boolean canReceiveCW() {
		return cwEnabled && isPowered();
	}

	/** True while the radio is on and in CW mode, which is all keying needs (there is no PTT for CW). */
	public boolean canTransmitCW() {
		return cwEnabled && isPowered() && !isTransmitInhibited(); // Out of band without a MARS mod the key does nothing.
	}

	@Override
	public void receiveCW(AntennaCWPacket packet) {
		if(level instanceof ServerLevel && !isDeafNow())
			recordCarrier(packet.getStrength()); // A scanner stops on CW too.
		if(level instanceof ServerLevel serverLevel && canReceiveCW() && !isDeafNow()) { // Not while its own key is down.
			List<AntennaNetworkObject> antennas = ((RadioNetworkObject)IBENetworks.getObject(this.level, worldPosition)).getAntennas();
			if(antennas.size() <= 1) { // None: it came in through the bare connector.
				recordSignal(packet.getStrength());
				com.arrl.radiocraft.common.moderation.ModerationHooks.heard(packet, this, level, worldPosition, null, packet.getStrength());
				List<CWBuffer> buffers = List.copyOf(packet.getBuffers());
				if(isParrot())
					parrotCWQueue.add(new QueuedCW(buffers, lastSignalGameTime + parrotDelayTicks));
				// Clients tracking this radio re-order the buffers and play them back through the morse sound instance.
				CCWBufferPacket.sendToTracking(serverLevel, worldPosition, buffers, (float)packet.getStrength());
			}
			else if(antennas.size() > 1)
				overdraw();
		}
	}

	public boolean getCWEnabled() {
		return cwEnabled;
	}

	/** AM voice (2026-11-09, on every HF radio's new panel): a carrier; it talks to CB on 10m's freeband. */
	protected boolean amEnabled = false;

	public boolean getAMEnabled() {
		return amEnabled;
	}

	/** Selects AM: the voice path on, CW off. Deselecting it leaves SSB. */
	public void setAMEnabled(boolean value) {
		if(amEnabled == value)
			return;
		if(value) {
			cwEnabled = false;
			super.setSSBEnabled(true); // Voice on (RadioBlockEntity's: does not clear AM).
		}
		amEnabled = value;
		if(level != null)
			updateIsReceiving();
		updateBlock();
		setChanged();
	}

	@Override
	public boolean isAM() {
		return amEnabled;
	}

	/** SSB is selected when the voice path is on and no other voice mode (AM, the all-band radio's FM) is. */
	public boolean isSSBSelected() {
		return getSSBEnabled() && !isAM() && !isFM();
	}

	/** Every HF radio's panel has a squelch knob (2026-11-09). */
	@Override
	public boolean hasSquelchKnob() {
		return true;
	}

	public void setCWEnabled(boolean value) {
		if(value)
			amEnabled = false;
		if(value && ssbEnabled)
			ssbEnabled = false; // Disable SSB if it as enabled here, don't need to sync here as it's done in super method.
		if(cwEnabled != value) {
			cwEnabled = value;
			if(level != null)
				updateIsReceiving(); // SSB may have just been switched off above, which stops voice receive.
			updateBlock();
			setChanged();
		}
	}

	@Override
	public void setSSBEnabled(boolean value) {
		if(value && amEnabled) {
			amEnabled = false;
			updateBlock();
			setChanged();
		}
		if(value && cwEnabled)
			cwEnabled = false; // Disable CW if it as enabled here, don't need to sync here as it's done in super method.
		super.setSSBEnabled(value);
	}

	@Override
	public void transmitMorse(Collection<CWBuffer> buffers) {
		if(level instanceof ServerLevel serverLevel && canTransmitCW() && !isBlown()) {
			List<AntennaNetworkObject> antennas = ((RadioNetworkObject)IBENetworks.getObject(this.level, worldPosition)).getAntennas();
			if(antennas.size() == 1) {
				com.arrl.radiocraft.common.moderation.ModerationHooks.cw(antennas.get(0).getAntenna(), this, serverLevel, buffers, getSimplexTransmitHz(), band, null);
				antennas.get(0).transmitCWPacket(serverLevel, buffers, band, getSimplexTransmitHz());
				recordTransmit(); // Keying is RF on the air too, for the transmit side of redstone interference.
			}
			else if(antennas.size() > 1)
				overdraw();
			else if(bareConnector != null) { // No antenna: a few blocks out of the bare connector.
				com.arrl.radiocraft.common.moderation.ModerationHooks.cw(bareConnector, this, serverLevel, buffers, getSimplexTransmitHz(), band, null);
				bareConnector.transmitCWPacket(serverLevel, buffers, band, getSimplexTransmitHz());
			}
		}
	}

	@Override
	protected void additionalTick() {
		if(level.isClientSide) {
			sidetoneOn = keyedThisTick;
			if(keyedThisTick)
				quietUntil = Math.max(quietUntil, level.getGameTime() + QUIET_TICKS);
			keyedThisTick = false;
			getCWSendBuffer().tick();
		}
	}

	// -------------------- SIDETONE (client, 2026-11-12) --------------------

	/** How loud the radio's own sidetone is, as a received tone's volume. */
	public static final float SIDETONE = 0.3F;
	/** The static stays off this long after the key last went up, ticks: the receiver mutes while sending. */
	private static final int QUIET_TICKS = 12;

	private boolean keyedThisTick = false;
	private volatile boolean sidetoneOn = false;
	private volatile float sidetoneLevel = SIDETONE;
	private volatile long quietUntil = Long.MIN_VALUE;

	/** Client: the key is down this tick (the screen's key, typed Morse or a keyer), with the radio's own sidetone. */
	public void keyCW() {
		keyCW(SIDETONE);
	}

	/** Client: the key is down this tick, with a sidetone {@code level} loud (a keyer has its own knob). */
	public void keyCW(float level) {
		getCWSendBuffer().setShouldAccumulate();
		keyedThisTick = true;
		sidetoneLevel = level;
	}

	/** Client: the sidetone's volume now, 0 with the key up. */
	public float sidetone() {
		return sidetoneOn ? sidetoneLevel : 0.0F;
	}

	/** Client: true while this radio is sending CW (and a moment after): its static is muted. */
	public boolean keyingQuiet() {
		return level != null && level.getGameTime() < quietUntil;
	}

	/** Client: someone else is keying this radio; mute the static for {@code ticks} (they hear the sidetone as Morse). */
	public void quietFor(int ticks) {
		if(level != null)
			quietUntil = Math.max(quietUntil, level.getGameTime() + ticks);
	}

	@Override
	protected void tickParrot(ServerLevel serverLevel) {
		super.tickParrot(serverLevel);
		long now = serverLevel.getGameTime();
		for(QueuedCW next = parrotCWQueue.peek(); next != null && next.dueTick() <= now; next = parrotCWQueue.peek()) {
			parrotCWQueue.poll();
			transmitMorse(next.buffers());
			recordParrotEcho(serverLevel);
		}
	}

	@Override
	public void setParrotDelay(int delayTicks) {
		super.setParrotDelay(delayTicks);
		if(!isParrot())
			parrotCWQueue.clear();
	}

	/** Morse waiting to be echoed by a parrot. */
	protected record QueuedCW(List<CWBuffer> buffers, long dueTick) {}

	// -------------------- RIT, NOISE BLANKER, RF GAIN (2026-11-11) --------------------

	/** RIT steps either side of the dial, and how far each step tunes the receiver. */
	public static final int MAX_RIT_STEPS = 20, RIT_STEP_HZ = 50;
	/** The RF gain knob's top (full gain). */
	public static final int MAX_RF_GAIN = 10;

	/**
	 * The premium rigs' extra receive controls. Every HF radio has them, at neutral (RIT off, NB off, RF gain full), and
	 * only the premium panels show the knobs: RIT tunes the receiver alone up to 1 kHz either side, as the CB's
	 * clarifier does; the noise blanker takes most of the sparks and aurora crackle out; RF gain turns the front end
	 * down, signals and static alike, as on the CB.
	 */
	protected int ritSteps = 0;
	protected boolean ritOn = false;
	protected boolean noiseBlanker = false;
	protected int rfGain = MAX_RF_GAIN;

	public int getRitSteps() {
		return ritSteps;
	}

	public boolean isRitOn() {
		return ritOn;
	}

	public int getRitHz() {
		return ritOn ? ritSteps * RIT_STEP_HZ : 0;
	}

	public boolean isNoiseBlanker() {
		return noiseBlanker;
	}

	public int getRfGain() {
		return rfGain;
	}

	/** From the panel: RIT {@code steps} (clamped) and whether it is switched in. Client and server alike; the server syncs. */
	public void setRit(int steps, boolean on) {
		ritSteps = Math.max(-MAX_RIT_STEPS, Math.min(MAX_RIT_STEPS, steps));
		ritOn = on;
		changedOnServer();
	}

	public void setNoiseBlanker(boolean on) {
		noiseBlanker = on;
		changedOnServer();
	}

	public void setRfGain(int value) {
		rfGain = Math.max(0, Math.min(MAX_RF_GAIN, value));
		changedOnServer();
	}

	private void changedOnServer() {
		if(level != null && !level.isClientSide) {
			setChanged();
			updateBlock();
		}
	}

	/** The receiver, moved by the RIT when it is in. The transmitter stays on the dial ({@link #getSimplexTransmitHz}). */
	@Override
	public float getTunedFrequency() {
		return super.getTunedFrequency() + getRitHz();
	}

	@Override
	public double getReceiverGain() {
		return 0.05D + 0.95D * rfGain / (double)MAX_RF_GAIN;
	}

	@Override
	public double getImpulseNoiseFactor() {
		return noiseBlanker ? 0.3D : 1.0D;
	}

	// -------------------- BE & SYNC IMPLEMENTATION --------------------

	@Override
	protected void setupSaveTag(CompoundTag nbt) {
		super.setupSaveTag(nbt);
		nbt.putBoolean("cwEnabled", cwEnabled);
		nbt.putBoolean("amEnabled", amEnabled);
		nbt.putInt("ritSteps", ritSteps);
		nbt.putBoolean("ritOn", ritOn);
		nbt.putBoolean("noiseBlanker", noiseBlanker);
		nbt.putInt("rfGain", rfGain);
	}

	@Override
	protected void readSaveTag(CompoundTag nbt) {
		super.readSaveTag(nbt);
		cwEnabled = nbt.getBoolean("cwEnabled");
		amEnabled = nbt.getBoolean("amEnabled");
		ritSteps = nbt.getInt("ritSteps");
		ritOn = nbt.getBoolean("ritOn");
		noiseBlanker = nbt.getBoolean("noiseBlanker");
		rfGain = nbt.contains("rfGain") ? nbt.getInt("rfGain") : MAX_RF_GAIN;
	}

	@Override
	protected void setupSoundInstances() {
		super.setupSoundInstances();
		RadioBlockEntityClientHandler.startMorseSoundInstance(this);
		RadioBlockEntityClientHandler.startStaticSoundInstance(this);
		RadioBlockEntityClientHandler.startInterferenceHumSoundInstance(this);
	}

	@Override
	public boolean shouldPlayStatic() {
		return getSSBEnabled() || getCWEnabled();
	}
}
