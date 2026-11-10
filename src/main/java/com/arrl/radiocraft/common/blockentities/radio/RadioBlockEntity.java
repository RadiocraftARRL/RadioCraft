package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.common.radio.voice.RadioModeration;
import com.arrl.radiocraft.common.moderation.ModerationHooks;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.api.blockentities.radio.IBEVoiceReceiver;
import com.arrl.radiocraft.api.blockentities.radio.IVoiceTransmitter;
import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.client.blockentity.RadioBlockEntityClientHandler;
import com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.ITogglableBE;
import com.arrl.radiocraft.common.blocks.radios.RadioBlock;
import com.arrl.radiocraft.common.radio.BEVoiceReceiver;
import com.arrl.radiocraft.common.radio.crystal.CrystalQuality;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.SWRHelper;
import com.arrl.radiocraft.common.radio.VoiceTransmitters;
import com.arrl.radiocraft.common.network.clientbound.CInterferencePacket;
import com.arrl.radiocraft.api.antenna.IAntennaPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaDataPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.voice.Afsk;
import com.arrl.radiocraft.common.radio.voice.Rtty;
import com.arrl.radiocraft.common.radio.rfi.InterferenceLevel;
import com.arrl.radiocraft.common.radio.rfi.PickupZone;
import com.arrl.radiocraft.common.radio.rfi.RedstoneInterference;
import com.arrl.radiocraft.common.radio.rfi.TransmitInterference;
import com.arrl.radiocraft.common.radio.solar.Aurora;
import com.arrl.radiocraft.common.radio.solar.SolarEvent.SolarEventInstance;
import com.arrl.radiocraft.common.radio.solar.SolarEventManager;
import com.arrl.radiocraft.common.radio.antenna.BareConnectorAntenna;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import com.arrl.radiocraft.common.radio.voice.Detuning;
import com.arrl.radiocraft.common.radio.voice.RepeaterAudio;
import com.arrl.radiocraft.common.radio.voice.ReceiverMix;
import com.arrl.radiocraft.common.sounds.RadioMorseSoundInstance;
import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.CordedMicrophoneItem;
import com.arrl.radiocraft.common.items.MicCord;
import com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadio;
import com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadioManager;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

public abstract class RadioBlockEntity extends BlockEntity implements ITogglableBE, IVoiceTransmitter, IBEVoiceReceiver, INetworkObjectProvider, MenuProvider,
        com.arrl.radiocraft.api.blockentities.radio.IRadio, com.arrl.radiocraft.api.blockentities.radio.IAntennaReceiver {

    protected boolean ssbEnabled = true; // A fresh radio comes up in SSB, so it plays static and hears voice as soon as it is powered.
    protected boolean isPTTDown = false; // Set by PTT button packets; synced to clients so the receiver sounds can go quiet while transmitting.

    protected Band band; // Band the radio is configured for, usually not changed.
    protected boolean marsMod = false; // A MARS mod is fitted: it may transmit anywhere it tunes (BandEdges).
    protected float frequency; // Frequency the radio is currently using (in Hz)
    protected volatile float speakerGain = 1.0F; // Read on the voice thread.
    protected volatile float micGain = 1.0F;
    /**
     * The squelch (2026-11-09), as a fraction of the S-meter's full scale: 0 is open (everything plays, static too); set
     * higher, nothing plays until a signal reads at least that on the meter. Only radios with a squelch knob set it.
     */
    protected volatile float squelch = 0.0F;
    /**
     * The AGC (2026-11-09). SLOW, as every radio always had it: the receiver holds a signal's level and the static ducks
     * under it, coming back slowly. FAST: the static comes back quickly between words. OFF: no levelling at all, so
     * strong stations blast and weak ones are faint, and the static never ducks.
     */
    protected volatile int agc = AGC_SLOW;
        public static final int AGC_OFF = 0, AGC_SLOW = 1, AGC_FAST = 2;
    /**
     * The QUICK key (2026-11-09): held, it stores the frequency in {@link #quickHz}; pressed, it jumps there and
     * remembers where it came from in {@link #quickReturnHz}, and pressed again goes back. 0 for none.
     */
    protected float quickHz = 0.0F, quickReturnHz = 0.0F;

    protected final BEVoiceReceiver voiceReceiver; // Acts as a container for voip channel info
    protected double antennaSWR; // Used clientside to calculate volume of static, and serverside for overdraw.
    /**
     * True while the radio's only antenna is a dummy load (synced): it takes in almost no signal, so almost no static
     * either (maintainer, 2026-10-30), see {@link #DUMMY_LOAD_STATIC}.
     */
    protected boolean onDummyLoad;
    /** The share of the static a radio on a dummy load still plays: the receiver's own hiss. */
    public static final double DUMMY_LOAD_STATIC = 0.05D;
    protected boolean tuneFail; // An automatic tuner on the coax gave up on the antenna; the screen says TUNE FAIL.
    /**
     * More than one radio (or direction finder module) shares this radio's antenna coax, or it has more than one
     * antenna: overdrawn, it hears nothing and cannot match. The screen says COAX SHARED, so the silence has a reason.
     */
    protected boolean coaxShared;

    // The hand mic on its cord (see takeHandMic): who has it out, which lending it is, and whether it is keying us.
    @Nullable
    protected UUID micHolder = null;
    // The desk microphone beside the radio (see MicrophoneBlock): who is holding its button, whether it is locked on,
    // and where it stands. Server side, not saved: a reload lets go of it.
    private UUID deskMicUser = null;
    private boolean deskMicLocked = false;
    private boolean deskMicKeyed = false;
    private long deskMicKeyedSince = -1L;
    private volatile BlockPos deskMicPos = null; // Read on the voice thread while keyed.
    @Nullable
    protected UUID micId = null;
    private boolean micKeyed = false;
    /** How far from the radio, in blocks, the hand mic's cord reaches before it snaps back. */
    public static final double MIC_CORD_LENGTH = 5.0D;
    protected boolean wasPowered; // Used for rendering clientside. The NetworkObject is the one actually controlling this.

    // Last received signal, server side only. Written from the voice thread, read on the game thread.
    protected volatile double lastSignalStrength = 0.0D;
    protected volatile long lastSignalGameTime = -1L;
    /** How long the S-meter (comparator output) keeps showing a signal after its last frame, like a meter's needle falling back. */
    public static final int METER_HOLD_TICKS = 20;
    private int lastMeterReading = 0; // What the comparators next to us last saw, so they are only told about changes.

    // Parrot mode (see setParrotDelay). The queue is filled from the voice thread and drained on the game thread.
    protected volatile int parrotDelayTicks = 0;
    protected volatile long lastParrotEchoGameTime = -1L;
    protected final ConcurrentLinkedQueue<QueuedVoice> parrotVoiceQueue = new ConcurrentLinkedQueue<>();

    protected final AtomicReference<BlockPos> micPos = new AtomicReference<>(); //thread safe position reference, overkill but makes purpose clear

    // Redstone interference (see RedstoneInterference). The server gathers pops during a tick, scales them for the band
    // and config, and sends the result to clients, which keep their own decaying copy for the static volume.
    protected final InterferenceLevel interference = new InterferenceLevel();
    /** The part of {@link #interference} that redstone switching caused: what the RFI sound follows (lightning static does not set it off). */
    protected final InterferenceLevel redstoneInterference = new InterferenceLevel();
    private double pendingInterference = 0.0D; // Redstone pops, still to be scaled for band and config.
    private double pendingDirectInterference = 0.0D; // Already scaled noise from other sources (lightning).
    private AntennaNetworkObject zoneAntenna = null;
    private int zoneRefreshTicks = 0;
    private static final int ZONE_REFRESH_TICKS = 100; // Antenna wires and coax can change without the antenna object changing.
    // Transmit side: what the current transmission is doing to nearby redstone, null while not transmitting.
    @Nullable
    private TransmitInterference.Targets transmitTargets = null;
    protected volatile long lastTransmitGameTime = -1L; // Last tick anything went out the antenna (voice frame or CW), from any thread.
    // The antenna socket while nothing is on the coax: a few blocks of range (server only, see BareConnectorAntenna).
    @Nullable
    protected volatile BareConnectorAntenna bareConnector = null;

    public RadioBlockEntity(BlockEntityType<? extends RadioBlockEntity> type, BlockPos pos, BlockState state, Band band) {
        super(type, pos, state);
        this.micPos.set(pos);
        this.band = band;
        this.frequency = band == null ? 0 : band.minFrequency();
        this.voiceReceiver = new BEVoiceReceiver(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Once a second, starts any of the radio's looping sounds that the client's sound engine dropped. */
    public static <T extends BlockEntity> void clientTick(Level level, BlockPos pos, BlockState state, T t) {
        if(t instanceof RadioBlockEntity be && level.getGameTime() % 20 == 0)
            RadioBlockEntityClientHandler.keepSoundsPlaying(be);
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T t) {
        if(t instanceof RadioBlockEntity be) {
            if(!level.isClientSide)
                be.voiceReceiver.setFrame(Sublevels.frame(level, pos)); // Its speaker audio follows a Sable ship it rides.
            RadioNetworkObject networkObject = (RadioNetworkObject)be.getNetworkObject(level, pos);
            boolean powered = networkObject != null && networkObject.isPowered;
            boolean shouldUpdate = false;

            if(be.wasPowered != powered) { // Tracked both ways, so the client also learns when the radio turns off.
                be.wasPowered = powered;
                be.updateIsReceiving(); // Power may have changed under us (network drained, reload), so re-arm or mute the receiver.
                shouldUpdate = true;
            }

            double newSWR = 0.0D; // No power, no SWR (and no static).
            AntennaNetworkObject antenna = null;
            if(!level.isClientSide)
                be.tickBareConnector(powered && be.usesBareConnector() && networkObject.getAntennas().isEmpty());
            if(powered) {
                List<AntennaNetworkObject> antennas = networkObject.getAntennas();
                if(antennas.size() == 1) {
                    antenna = antennas.get(0);
                    newSWR = antenna.getSWR(be.getSwrFrequency());
                }
                else if(antennas.size() > 1)
                    newSWR = 10.0D;
            }

            boolean shared = powered && ((antenna != null && antenna.isOverdrawn()) || networkObject.getAntennas().size() > 1);
            if(shared != be.coaxShared) {
                be.coaxShared = shared;
                shouldUpdate = true;
            }

            boolean tuneFail = antenna != null && antenna.isTuneFailed();
            if(tuneFail != be.tuneFail) {
                be.tuneFail = tuneFail;
                shouldUpdate = true;
            }

            if(newSWR != be.antennaSWR) {
                be.antennaSWR = newSWR;
                shouldUpdate = true; // If SWR has changed, notify the client about it.
            }
            boolean dummyLoad = antenna instanceof com.arrl.radiocraft.common.be_networks.network_objects.DummyLoadNetworkObject;
            if(dummyLoad != be.onDummyLoad) {
                be.onDummyLoad = dummyLoad;
                shouldUpdate = true;
            }
            if(be.tickSyncedSignal(level.getGameTime()))
                shouldUpdate = true; // The client ducks its static under the signal.
            if(!level.isClientSide && be.tickReceiverSync(level.getGameTime(), powered))
                shouldUpdate = true; // The S-meter's noise, the squelch, power out: for the panel and the static.

            if(shouldUpdate) {
                be.setChanged();
                be.updateBlock();
            }
            if(level instanceof ServerLevel serverLevel) {
                be.voiceReceiver.tickSquelchTail(serverLevel.getGameTime()); // FM: a burst of static as a signal drops.
                be.tickRemoteListeners(serverLevel.getGameTime()); // RIGCTL remote audio: computers playing this radio.
                be.tickParrot(serverLevel);
                be.tickInterference(serverLevel, antenna);
                be.tickBlowout(serverLevel);
                be.tickHandMic(serverLevel);
                be.tickDeskMic(serverLevel);
                be.tickMeter(serverLevel);
                be.tickGround(serverLevel);
                be.tickData(serverLevel, powered);
                be.tickDtmf(serverLevel, powered);
                if((serverLevel.getGameTime() + pos.asLong()) % 20L == 0L) // Speakers come and go rarely: once a second is plenty.
                    be.refreshSpeakers(serverLevel);
            }
            be.additionalTick();
        }
    }

    // -------------------- SPEAKERS --------------------

    /**
     * Finds what the radio plays through: the best speaker touching it ({@link com.arrl.radiocraft.common.blocks.SpeakerBlock},
     * else its own built-in one), and every wired speaker touching it or on its data wire
     * ({@link com.arrl.radiocraft.common.blocks.WiredSpeakerBlock}, {@link com.arrl.radiocraft.common.be_networks.DataWire}).
     * Power wire no longer reaches them (2026-10-30).
     */
    protected void refreshSpeakers(ServerLevel serverLevel) {
        com.arrl.radiocraft.common.radio.voice.SpeakerKind best = com.arrl.radiocraft.common.radio.voice.SpeakerKind.BUILT_IN;
        for(Direction direction : Direction.values()) {
            BlockPos at = worldPosition.relative(direction);
            if(serverLevel.isLoaded(at) && serverLevel.getBlockState(at).getBlock() instanceof com.arrl.radiocraft.common.blocks.SpeakerBlock speaker)
                best = com.arrl.radiocraft.common.radio.voice.SpeakerKind.better(best, speaker.getKind());
        }
        voiceReceiver.setSpeaker(best);

        List<BEVoiceReceiver.WiredSpeaker> wired = new java.util.ArrayList<>();
        for(BlockPos at : com.arrl.radiocraft.common.be_networks.DataWire.linked(serverLevel, worldPosition))
            if(serverLevel.isLoaded(at) && serverLevel.getBlockState(at).getBlock() instanceof com.arrl.radiocraft.common.blocks.WiredSpeakerBlock speaker)
                wired.add(new BEVoiceReceiver.WiredSpeaker(at.immutable(), speaker.getKind()));
        if(!wired.equals(voiceReceiver.getWiredSpeakers())) {
            voiceReceiver.setWiredSpeakers(wired);
            updateBlock(); // Clients play the static through them too.
        }
    }

    // -------------------- REMOTE AUDIO (RIGCTL, 2026-11-09) --------------------

    /** A radio computer playing this radio (RIGCTL REMOTE audio): its speakers, until game time {@code until}. */
    private record RemoteListen(List<BEVoiceReceiver.RemoteSpeaker> speakers, long until) {}

    /** The computers playing this radio, by position (server, game thread). */
    private final java.util.Map<BlockPos, RemoteListen> remoteListeners = new java.util.HashMap<>();

    /**
     * The radio computer at {@code computer} plays this radio through {@code speakers} until game time {@code until}
     * (RIGCTL REMOTE audio): as if it were one more wired speaker, wherever it stands. The computer renews it while it
     * listens; it lapses by itself if the computer goes away. Server, game thread.
     */
    public void remoteListen(BlockPos computer, List<BEVoiceReceiver.RemoteSpeaker> speakers, long until) {
        RemoteListen was = remoteListeners.put(computer.immutable(), new RemoteListen(List.copyOf(speakers), until));
        if(was == null || !was.speakers().equals(speakers))
            pushRemoteSpeakers();
    }

    /** The computer at {@code computer} stops playing this radio. */
    public void stopRemoteListen(BlockPos computer) {
        if(remoteListeners.remove(computer) != null)
            pushRemoteSpeakers();
    }

    /** The computers playing this radio now (for tests and {@code /rcdebug}). */
    public java.util.Set<BlockPos> remoteListeners() {
        return java.util.Set.copyOf(remoteListeners.keySet());
    }

    private void pushRemoteSpeakers() {
        List<BEVoiceReceiver.RemoteSpeaker> all = new java.util.ArrayList<>();
        for(RemoteListen listen : remoteListeners.values())
            all.addAll(listen.speakers());
        boolean changed = !all.equals(voiceReceiver.getRemoteSpeakers());
        voiceReceiver.setRemoteSpeakers(all);
        if(changed) // The client plays the static and Morse where the voice goes (2026-11-09).
            updateBlock();
    }

    /** Client: the computers' speakers this radio plays through instead of its own (synced), empty when it plays itself. */
    private List<BEVoiceReceiver.WiredSpeaker> clientRemoteSpeakers = List.of();

    /** True while a radio computer plays this radio (RIGCTL REMOTE audio): the radio's own speaker is quiet. */
    public boolean isAudioRouted() {
        return level != null && level.isClientSide ? !clientRemoteSpeakers.isEmpty() : !voiceReceiver.getRemoteSpeakers().isEmpty();
    }

    /** Where the static (and Morse) play: the computers' speakers while it is routed there, else the radio's wired speakers. */
    public List<BEVoiceReceiver.WiredSpeaker> getStaticSpeakers() {
        if(level != null && level.isClientSide)
            return clientRemoteSpeakers.isEmpty() ? getWiredSpeakers() : clientRemoteSpeakers;
        List<BEVoiceReceiver.RemoteSpeaker> remote = voiceReceiver.getRemoteSpeakers();
        return remote.isEmpty() ? getWiredSpeakers() : remote.stream().map(BEVoiceReceiver.RemoteSpeaker::speaker).toList();
    }

    /** Lets computers that stopped renewing go, and sends the remote audio that waited on a link. */
    private void tickRemoteListeners(long gameTime) {
        if(remoteListeners.isEmpty())
            return;
        if(remoteListeners.values().removeIf(listen -> gameTime > listen.until()))
            pushRemoteSpeakers();
        voiceReceiver.tickRemote();
    }

    /** The wired speakers as the client last heard of them (see {@link #refreshSpeakers}); the server's own on the server. */
    public List<BEVoiceReceiver.WiredSpeaker> getWiredSpeakers() {
        return voiceReceiver.getWiredSpeakers();
    }

    // -------------------- BARE CONNECTOR (NO ANTENNA) --------------------

    /** Keeps the bare connector on the antenna network of this radio's band while it is wanted, and off it otherwise. */
    /** False for a station that reaches the air some other way with no antenna (a Real-World Interface in Everywhere mode). */
    protected boolean usesBareConnector() {
        return true;
    }

    private void tickBareConnector(boolean wanted) {
        if(wanted) {
            if(bareConnector == null)
                bareConnector = new BareConnectorAntenna(this);
            bareConnector.register(band);
        }
        else
            dropBareConnector();
    }

    private void dropBareConnector() {
        BareConnectorAntenna bare = bareConnector;
        if(bare != null) {
            bare.unregister();
            bareConnector = null;
        }
    }

    /** The bare connector, while the radio is on with nothing on its coax; null otherwise. */
    @Nullable
    public BareConnectorAntenna getBareConnector() {
        return bareConnector;
    }

    // -------------------- DATA (DIGITAL INTERFACE) --------------------

    private final ArrayDeque<TncPacket> dataQueue = new ArrayDeque<>(); // Server thread only.
    private TncPacket sendingData = null;
    private List<short[]> dataFrames = List.of();
    private int dataFrame = 0;
    private UUID dataVoiceId = null;
    private final double[] rttyPhase = new double[1]; // Keeps RTTY tones continuous from one packet to the next.
    private long lastRttyGameTime = -100L;

    /**
     * Queues a packet from a digital interface: the radio sends its burst of tones (heard by any radio on the
     * frequency, like voice) and then the packet itself. Nothing is queued while the radio is off.
     *
     * @return True if the packet will go out.
     */
    public boolean sendData(TncPacket packet) {
        if(level == null || level.isClientSide || !(getNetworkObject(level, worldPosition) instanceof RadioNetworkObject networkObject) || !networkObject.isPowered)
            return false;
        dataQueue.add(packet);
        return true;
    }

    /** True if the radio is switched on and has the power it needs (server side). */
    public boolean isPowered() {
        return level != null && getNetworkObject(level, worldPosition) instanceof RadioNetworkObject networkObject && networkObject.isPowered;
    }

    /** True while a packet burst or DTMF tones are going out or waiting to. */
    public boolean isSendingData() {
        return sendingData != null || !dataQueue.isEmpty() || !dtmfFrames.isEmpty();
    }

    // -------------------- DTMF --------------------

    private final ArrayDeque<short[]> dtmfFrames = new ArrayDeque<>(); // Server thread only.
    private UUID dtmfVoiceId = null;
    private final com.arrl.radiocraft.common.radio.voice.DtmfListener dtmfListener = new com.arrl.radiocraft.common.radio.voice.DtmfListener();
    /** True while a DTMF decoder (or autopatch, {@link com.arrl.radiocraft.common.blockentities.DtmfKeyReceiver}) is linked: only then does it look for key tones in what it hears. */
    private volatile boolean dtmfDecoderLinked = false;

    /**
     * Sends {@code digits} as DTMF tones, from a DTMF encoder: on the radio's transmit frequency with its CTCSS tone,
     * like its voice, so a repeater passes them on. Nothing goes out from a receiver or a radio that is off.
     *
     * @return True if the tones will go out.
     */
    public boolean sendDtmf(String digits) {
        String keys = com.arrl.radiocraft.common.radio.voice.Dtmf.sanitize(digits);
        if(keys.isEmpty() || !hasTransmitter() || isBlown() || !isPowered())
            return false;
        dtmfFrames.addAll(com.arrl.radiocraft.common.radio.voice.Dtmf.frames(keys));
        ModerationHooks.dtmfQueued(this, keys);
        return true;
    }

    /**
     * Sends {@code callsign} in Morse (20 wpm, the repeaters' CW ID tone) as an automatic station ID, the radio
     * computer's scheduler's (computer batch 3A): through the DTMF encoder's path, on the radio's transmit frequency
     * with its CTCSS tone, so the same rules hold (a transmitter, switched on, not blown).
     *
     * @return True if the ID will go out.
     */
    public boolean sendMorseId(String callsign) {
        String call = com.arrl.radiocraft.common.radio.voice.RepeaterAudio.sanitizeCallsign(callsign);
        if(call.isEmpty() || !hasTransmitter() || isBlown() || !isPowered())
            return false;
        dtmfFrames.addAll(com.arrl.radiocraft.common.radio.voice.RepeaterAudio.morse("DE " + call));
        return true;
    }

    /** True while DTMF tones are going out or waiting to. */
    public boolean isSendingDtmf() {
        return !dtmfFrames.isEmpty();
    }

    /** False for receivers: they have nothing to send DTMF (or anything) with. */
    public boolean hasTransmitter() {
        return true;
    }

    /** Plays the DTMF tones in hand out frame by frame, 2.5 frames of 20 ms per tick like data. */
    private void tickDtmfFrames(ServerLevel serverLevel) {
        if(dtmfVoiceId == null)
            dtmfVoiceId = UUID.nameUUIDFromBytes(("radiocraft-dtmf-" + worldPosition.asLong()).getBytes(StandardCharsets.UTF_8));
        int frames = serverLevel.getGameTime() % 2 == 0 ? 3 : 2;
        ModerationHooks.Tag was = ModerationHooks.push(ModerationHooks.Tag.dtmf(this));
        try {
            for(int i = 0; i < frames && !dtmfFrames.isEmpty(); i++) {
                transmitVoice(() -> serverLevel, dtmfFrames.poll(), (float)(getTransmitFrequency() + getDriftHz()), dtmfVoiceId, getTransmitTone());
                recordTransmit();
            }
        }
        finally {
            ModerationHooks.pop(was);
        }
    }

    /**
     * Keeps track of whether a DTMF decoder is linked (touching, or on the data wire), and hands it the keys copied
     * from what the radio received.
     */
    private void tickDtmf(ServerLevel serverLevel, boolean powered) {
        long now = serverLevel.getGameTime();
        if((now + worldPosition.asLong()) % 10L == 0L)
            dtmfDecoderLinked = !com.arrl.radiocraft.common.be_networks.DataWire.linked(serverLevel, worldPosition, com.arrl.radiocraft.common.blockentities.DtmfKeyReceiver.class).isEmpty();
        if(!dtmfDecoderLinked || !powered) {
            dtmfListener.clear();
            return;
        }
        List<Character> keys = dtmfListener.drain(now);
        if(keys.isEmpty())
            return;
        for(com.arrl.radiocraft.common.blockentities.DtmfKeyReceiver decoder : com.arrl.radiocraft.common.be_networks.DataWire.linked(serverLevel, worldPosition, com.arrl.radiocraft.common.blockentities.DtmfKeyReceiver.class))
            for(char key : keys)
                decoder.receiveDtmf(this, key);
    }

    /** The key a DTMF decoder follows a station by: who is talking, or for tones a block sent, its antenna. */
    private static Object dtmfSource(AntennaVoicePacket packet) {
        return packet.getSourcePlayer() != null ? packet.getSourcePlayer() : packet.getSource();
    }

    /** The client's copy of {@link #isSendingData()}, for the TX lamp. */
    private boolean syncedSendingData = false;

    /** True while data is going out, as last synced to the client: the TX lamp lights for it. */
    public boolean isSendingDataSynced() {
        return syncedSendingData;
    }

    /** Plays the packet in hand out frame by frame (2.5 frames of 20 ms per tick) and sends it once the tones are done. */
    private void tickData(ServerLevel serverLevel, boolean powered) {
        tickDataFrames(serverLevel, powered);
        if(isSendingData() != syncedSendingData) {
            syncedSendingData = isSendingData();
            updateBlock();
        }
    }

    /** The frame at which a bulletin's packet goes out (the end of its start tone), -1 once sent or for other packets. */
    private int dataHeaderAt = -1;

    private void tickDataFrames(ServerLevel serverLevel, boolean powered) {
        if(!powered) {
            dataQueue.clear();
            sendingData = null;
            dtmfFrames.clear();
            return;
        }
        if(sendingData == null && !dtmfFrames.isEmpty()) { // DTMF waits for a packet already going out, then goes first.
            tickDtmfFrames(serverLevel);
            return;
        }
        if(sendingData == null) {
            sendingData = dataQueue.poll();
            if(sendingData == null)
                return;
            dataHeaderAt = -1;
            if(sendingData.kind() == TncPacket.Kind.RTTY) // Straight on from the last characters, or a lead-in after a pause.
                dataFrames = Rtty.frames(sendingData.text(), serverLevel.getGameTime() - lastRttyGameTime > 2, rttyPhase);
            else if(sendingData.kind() == TncPacket.Kind.FILE) {
                transmitData(serverLevel, sendingData.header()); // Receivers learn a file is coming and how long it takes.
                dataFrames = FileTransfer.audio(sendingData.item(), serverLevel, sendingData.id());
            }
            else if(sendingData.isFax())
                dataFrames = com.arrl.radiocraft.common.radio.voice.FaxAudio.frames(sendingData);
            else if(sendingData.kind() == TncPacket.Kind.FT8) // 12.64 s of eight-tone FSK at the station's offset.
                dataFrames = com.arrl.radiocraft.common.radio.ft8.Ft8Audio.frames(sendingData.ft8Message(), sendingData.ft8Offset());
            else if(sendingData.kind() == TncPacket.Kind.SOLAR) {
                // A bulletin is a weather fax sheet: the picture's tones, for as long as the server says. Receivers
                // learn of it as it starts and print it line by line as the tones come in.
                dataFrames = com.arrl.radiocraft.common.radio.voice.WefaxAudio.frames(
                        com.arrl.radiocraft.common.radio.wefax.WefaxPrints.bulletinPixels(sendingData.from(), sendingData.solarLines()),
                        com.arrl.radiocraft.common.radio.wefax.WefaxChart.SIZE, RadiocraftServerConfig.WEFAX_BULLETIN_SECONDS.get() * 20);
                dataHeaderAt = (int)(dataFrames.size() * com.arrl.radiocraft.common.radio.voice.WefaxAudio.START_SHARE);
            }
            else
                dataFrames = Afsk.burst(sendingData.airBytes(), sendingData.id());
            dataFrame = 0;
        }
        if(dataVoiceId == null)
            dataVoiceId = Afsk.markDataSource(UUID.nameUUIDFromBytes(("radiocraft-tnc-" + worldPosition.asLong()).getBytes(StandardCharsets.UTF_8)));
        int frames = serverLevel.getGameTime() % 2 == 0 ? 3 : 2;
        for(int i = 0; i < frames && dataFrame < dataFrames.size(); i++) {
            transmitVoice(() -> serverLevel, dataFrames.get(dataFrame++), getTunedFrequency(), dataVoiceId, 0.0F);
            recordTransmit();
        }
        if(dataHeaderAt >= 0 && dataFrame >= dataHeaderAt) { // The start tone is done: receivers lock on and start printing.
            dataHeaderAt = -1;
            transmitData(serverLevel, sendingData);
        }
        if(dataFrame >= dataFrames.size()) {
            if(sendingData.kind() == TncPacket.Kind.RTTY)
                lastRttyGameTime = serverLevel.getGameTime();
            if(sendingData.kind() != TncPacket.Kind.SOLAR) // A bulletin went out as it started.
                transmitData(serverLevel, sendingData);
            sendingData = null;
        }
    }

    /** Puts a packet on the air on the radio's own frequency (packet is simplex), the way voice goes out. */
    protected void transmitData(ServerLevel serverLevel, TncPacket packet) {
        if(isBlown() || isTransmitInhibited())
            return;
        sendDataOn(serverLevel, packet, getTunedFrequency());
    }

    /**
     * Puts a packet on the air on {@code hertz} through the radio's antenna (or its bare connector), whatever it is
     * tuned to. {@link #transmitData} sends on the radio's own frequency; the marine radio's DSC sends its bursts on
     * channel 70 with this (2026-11-09). Checks nothing about whether the radio may transmit: callers do.
     */
    protected void sendDataOn(ServerLevel serverLevel, TncPacket packet, float hertz) {
        if(!(IBENetworks.getObject(serverLevel, worldPosition) instanceof RadioNetworkObject networkObject))
            return;
        RadioModeration.data(serverLevel, packet, hertz); // Operators reading along (/radio moderateall).
        List<AntennaNetworkObject> antennas = networkObject.getAntennas();
        if(antennas.size() == 1) {
            ModerationHooks.data(antennas.get(0).getAntenna(), this, serverLevel, packet, hertz, band, null);
            antennas.get(0).transmitDataPacket(serverLevel, packet, band, hertz);
        }
        else if(antennas.size() > 1)
            overdraw();
        else {
            BareConnectorAntenna bare = bareConnector;
            if(bare != null) {
                ModerationHooks.data(bare, this, serverLevel, packet, hertz, band, null);
                bare.transmitDataPacket(serverLevel, packet, band, hertz);
            }
        }
    }

    /**
     * A data packet reached this radio's antenna. It decodes if the radio is on, not transmitting, tuned to it (a
     * modem cannot copy tones shifted by a detuned SSB receiver) and it is far enough above the noise
     * ({@link RadiocraftServerConfig#TNC_MIN_SNR}); then every digital interface next to the radio gets it. RTTY
     * still prints a little under that, with more and more characters garbled, down to half of it.
     *
     * @return True if the packet decoded.
     */
    public boolean receiveData(AntennaDataPacket packet) {
        if(!(level instanceof ServerLevel serverLevel) || !(getNetworkObject(level, worldPosition) instanceof RadioNetworkObject networkObject) || !networkObject.isPowered)
            return false;
        if(isDeafNow() || packet.getPayload().kind() == TncPacket.Kind.HOMING) // A beacon's homing tone carries nothing to decode (2026-11-09).
            return false;
        Detuning.Effect effect = getDetuning(packet.getFrequency());
        if(!effect.audible() || effect.pitchShiftHz() != 0.0D || effect.splatter() > 0.0D)
            return false;
        double strength = packet.getStrength() * effect.strength() * desense(packet);
        boolean skip = isSkipPath(packet);
        if(skip)
            strength *= ReceiverMix.fading(level.getGameTime() / 20.0D, pathSeed(packet), RadiocraftServerConfig.SKIP_FADING_DEPTH.get());
        double noise = getNoiseFloor(skip);
        double quality = strength / copyThreshold(noise);
        TncPacket payload = packet.getPayload();
        boolean ft8 = payload.kind() == TncPacket.Kind.FT8;
        if(ft8) { // FT8 digs far below the copy threshold, and the decoder notes how far above the noise it was.
            if(!com.arrl.radiocraft.common.radio.ft8.Ft8.decodes(quality))
                return false;
            payload = payload.withFt8Snr(com.arrl.radiocraft.common.radio.ft8.Ft8.snr(quality));
        }
        if(payload.kind() == TncPacket.Kind.RTTY) {
            double collision = rttyCollision(payload.from(), strength, level.getGameTime());
            if(collision < 0.0D)
                return false; // Lost under another station sending at the same time.
            if(collision > 0.0D)
                payload = payload.withText(Rtty.garble(payload.text(), collision, new java.util.Random(level.getRandom().nextLong())));
        }
        if(payload.kind() == TncPacket.Kind.RTTY && quality >= RTTY_GARBLE_FLOOR && quality < 1.0D)
            payload = payload.withText(Rtty.garble(payload.text(), (1.0D - quality) / (1.0D - RTTY_GARBLE_FLOOR), new java.util.Random(level.getRandom().nextLong())));
        else if(!ft8 && !decodes(strength, noise))
            return false;
        if((payload.kind() == TncPacket.Kind.FAX_PACKET || payload.kind() == TncPacket.Kind.FAX_ACK) // A weak signal drops fax packets now and then: they are sent again.
                && level.getRandom().nextDouble() < com.arrl.radiocraft.common.radio.data.FaxRates.lossChance(strength, noise))
            return false;
        recordSignal(strength);
        ModerationHooks.heard(packet, this, level, worldPosition, null, strength); // A hearer of the logged transmission.
        for(RadioInterfaceBlockEntity radioInterface : getRadioInterfaces())
            radioInterface.receive(this, payload);
        // Teletypes, fax machines and WEFAX receivers have their own modems and take packets straight from the radio:
        // touching it, or on its data wire.
        java.util.Set<Object> fed = new java.util.HashSet<>();
        for(BlockPos at : com.arrl.radiocraft.common.be_networks.DataWire.linked(level, worldPosition)) {
            if(!level.isLoaded(at))
                continue;
            if(level.getBlockEntity(at) instanceof com.arrl.radiocraft.common.blockentities.TeletypeBlockEntity teletype && fed.add(teletype))
                teletype.receive(payload);
            else if(level.getBlockEntity(at) instanceof com.arrl.radiocraft.common.blockentities.RadioFaxBlockEntity fax && fed.add(fax))
                fax.receive(payload);
            else if(level.getBlockEntity(at) instanceof com.arrl.radiocraft.common.blockentities.WefaxReceiverBlockEntity wefax && fed.add(wefax))
                wefax.receiveOverAir(payload); // Solar bulletins from an Ionospheric Processor.
            else {
                com.arrl.radiocraft.common.blockentities.IonosphericProcessorBlockEntity processor = com.arrl.radiocraft.common.blockentities.IonosphericProcessorBlockEntity.at(level, at);
                if(processor != null && fed.add(processor))
                    processor.receive(payload); // Requests for the solar weather.
            }
        }
        return true;
    }

    // -------------------- BAND SCOPE AND CARRIER (computer batch 3B) --------------------

    /** The band scope while the rig control program's waterfall watches this radio, else null. */
    private volatile com.arrl.radiocraft.common.radio.BandScope scope;
    private volatile double carrierStrength = 0.0D;
    private volatile long carrierGameTime = -1L;

    /**
     * The band scope on this radio, watching {@code lowHz} to {@code highHz} for {@code leaseTicks} more ticks: made
     * if there is none, else moved and renewed. It drops itself once the lease runs out. Server thread.
     */
    public com.arrl.radiocraft.common.radio.BandScope scope(double lowHz, double highHz, int leaseTicks) {
        long until = (level == null ? 0L : level.getGameTime()) + leaseTicks;
        com.arrl.radiocraft.common.radio.BandScope current = scope;
        if(current == null) {
            current = new com.arrl.radiocraft.common.radio.BandScope(lowHz, highHz, until);
            scope = current;
        }
        else {
            current.span(lowHz, highHz);
            current.renew(until);
        }
        return current;
    }

    /** True while a band scope watches this radio (tests). */
    public boolean hasScope() {
        return scope != null;
    }

    public void dropScope() {
        scope = null;
    }

    /** A voice signal for the band scope, shaped by its mode and how loud the voice is (2026-11-09); one null check if none. */
    public void scopeHearVoice(com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket packet) {
        com.arrl.radiocraft.common.radio.BandScope watching = scope;
        if(watching == null)
            return;
        if(level != null && level.getGameTime() > watching.leaseUntil()) {
            scope = null;
            return;
        }
        watching.hearVoice(packet.getFrequency(), packet.getStrength(), packet.getModulation(), packet.isLowerSideband(), packet.envelope());
    }

    /** Taps a signal reaching the antenna for the band scope, if one is watching; one null check if not. */
    public void scopeHear(float hz, double strength) {
        com.arrl.radiocraft.common.radio.BandScope watching = scope;
        if(watching == null)
            return;
        if(level != null && level.getGameTime() > watching.leaseUntil()) {
            scope = null; // Nobody looked for a while.
            return;
        }
        watching.hear(hz, strength);
    }

    /** A signal on this radio's frequency reached it: the squelch would open, whatever the speaker is set to. */
    protected void recordCarrier(double strength) {
        if(level == null || !(strength > 0.0D))
            return;
        carrierStrength = strength;
        carrierGameTime = level.getGameTime();
    }

    /** Strength of the last signal heard on frequency (voice, data tones or CW), in any mode. */
    public double getCarrierStrength() {
        return carrierStrength;
    }

    /** Game time it was heard, -1 if never. */
    public long getCarrierGameTime() {
        return carrierGameTime;
    }

    /**
     * Tunes the radio to {@code hz} as typed on its keypad: put on its step grid, inside what it tunes (its band, or
     * the band's tuning range on ham radios). False if {@code hz} is outside that, and nothing changes. Server side.
     */
    public boolean tuneTo(float hz) {
        if(band == null || !Float.isFinite(hz))
            return false;
        boolean inRange = tunesOutOfBand() ? com.arrl.radiocraft.common.radio.BandEdges.canTune(band, hz) : band.contains(hz);
        if(!inRange)
            return false;
        float stepped = snapToTuningRange(hz, 0);
        if(stepped != frequency) {
            frequency = stepped;
            setChanged();
            updateBlock();
        }
        return true;
    }

    /** Ticks within which RTTY from two stations counts as sent at the same time (each sends a character every four). */
    public static final int RTTY_COLLISION_TICKS = 6;
    /** RTTY senders heard lately: callsign to {game time, strength}. Server only. */
    private final java.util.Map<String, double[]> rttyHeard = new java.util.HashMap<>();

    /**
     * Two stations sending RTTY on one frequency at once garble each other. The one much stronger (four times) mostly
     * copies (the capture effect), the one much weaker is lost, and between those most characters are wrong and some
     * are lost. Returns the share of characters to garble, 0 for none, or -1 for lost.
     */
    private double rttyCollision(String from, double strength, long now) {
        rttyHeard.values().removeIf(heard -> now - (long)heard[0] > RTTY_COLLISION_TICKS * 4L);
        double strongest = 0.0D;
        for(java.util.Map.Entry<String, double[]> entry : rttyHeard.entrySet())
            if(!entry.getKey().equals(from) && now - (long)entry.getValue()[0] <= RTTY_COLLISION_TICKS)
                strongest = Math.max(strongest, entry.getValue()[1]);
        rttyHeard.put(from, new double[] { now, strength });
        if(strongest <= 0.0D)
            return 0.0D;
        double ratio = strength / strongest;
        if(ratio >= 4.0D)
            return 0.05D;
        if(ratio <= 0.25D || level.getRandom().nextFloat() < 0.3F)
            return -1.0D;
        return 0.6D;
    }

    /** RTTY below this share of the copy threshold is lost; between it and the threshold it prints garbled. */
    public static final double RTTY_GARBLE_FLOOR = 0.5D;

    /** Weakest signal that copies cleanly over {@code noise}. */
    public static double copyThreshold(double noise) {
        return Math.max(RadiocraftServerConfig.TNC_MIN_SIGNAL.get(), noise * RadiocraftServerConfig.TNC_MIN_SNR.get());
    }

    /** True if a packet at {@code strength} copies over {@code noise}: no weaker than the noise times the minimum SNR. */
    public static boolean decodes(double strength, double noise) {
        return strength > 0.0D && strength >= copyThreshold(noise);
    }

    /** Radio interfaces on any side of this radio, or on its data wire. */
    public List<RadioInterfaceBlockEntity> getRadioInterfaces() {
        if(level == null)
            return List.of();
        return com.arrl.radiocraft.common.be_networks.DataWire.linked(level, worldPosition, RadioInterfaceBlockEntity.class);
    }

    // -------------------- S-METER (COMPARATOR OUTPUT) --------------------

    /**
     * The strength of the signal being received right now, 0 to 15, the way a comparator reads it: a signal meter on
     * the front panel. It follows the last voice or CW frame for {@link #METER_HOLD_TICKS} and then falls back to 0.
     */
    public int getMeterReading() {
        if(level == null || lastSignalGameTime < 0 || level.getGameTime() - lastSignalGameTime > METER_HOLD_TICKS)
            return 0;
        return Mth.clamp(Mth.ceil(lastSignalStrength * 15.0D), 1, 15);
    }

    /** Comparators only re-read a block when it tells them to, so do that whenever the meter moves. */
    private void tickMeter(ServerLevel serverLevel) {
        int reading = getMeterReading();
        if(reading != lastMeterReading) {
            lastMeterReading = reading;
            serverLevel.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    // -------------------- REDSTONE INTERFERENCE --------------------

    /**
     * Keeps this radio's {@link PickupZone} registered while it is powered with one antenna, and sends the pops gathered
     * this tick to the clients. The zone is rebuilt when the antenna changes and every {@link #ZONE_REFRESH_TICKS}, since
     * wires and coax can change under the same antenna object.
     */
    private void tickInterference(ServerLevel serverLevel, @Nullable AntennaNetworkObject antenna) {
        if(antenna != zoneAntenna || (antenna != null && --zoneRefreshTicks <= 0)) {
            zoneAntenna = antenna;
            zoneRefreshTicks = ZONE_REFRESH_TICKS;
            if(antenna == null)
                RedstoneInterference.unregister(serverLevel, worldPosition);
            else
                RedstoneInterference.register(serverLevel, RedstoneInterference.buildZone(serverLevel, worldPosition, antenna));
        }

        if(pendingInterference > 0.0D || pendingDirectInterference > 0.0D) {
            double redstone = pendingInterference * RedstoneInterference.bandFactor(band) * RedstoneInterference.configFactor(antennaSWR);
            double scaled = redstone + pendingDirectInterference;
            pendingInterference = 0.0D;
            pendingDirectInterference = 0.0D;
            if(scaled > 0.0D) {
                interference.add(serverLevel.getGameTime(), scaled);
                redstoneInterference.add(serverLevel.getGameTime(), redstone);
                CInterferencePacket.sendToTracking(serverLevel, worldPosition, scaled, redstone);
            }
        }

        boolean transmitting = antenna != null && isTransmittingNow(serverLevel);
        if(transmitting && transmitTargets == null) {
            PickupZone zone = RedstoneInterference.getZone(serverLevel, worldPosition);
            boolean vertical = antenna.getAntenna() != null && TransmitInterference.VERTICAL_ANTENNAS.contains(antenna.getAntenna().type.getId());
            transmitTargets = zone == null ? TransmitInterference.Targets.EMPTY : TransmitInterference.pick(serverLevel, zone, isQrp(), vertical, antennaSWR);
            TransmitInterference.start(serverLevel, transmitTargets);
        }
        else if(!transmitting && transmitTargets != null)
            stopTransmitInterference(serverLevel);
    }

    private void stopTransmitInterference(ServerLevel serverLevel) {
        if(transmitTargets != null) {
            TransmitInterference.stop(serverLevel, transmitTargets);
            transmitTargets = null;
        }
    }

    /**
     * True while the radio's own transmitter has the antenna (voice, morse, data or DTMF going out): a transceiver hears
     * nothing then. Repeaters, which listen on one frequency while sending on another, say so in
     * {@link #hearsWhileTransmitting()}. Read on the voice chat thread too.
     */
    public boolean isDeafNow() {
        if(hearsWhileTransmitting() || !(level instanceof ServerLevel serverLevel))
            return false;
        return isTransmittingNow(serverLevel) || sendingData != null || !dtmfFrames.isEmpty();
    }

    /** False for a transceiver: it cannot hear while it transmits. True for a repeater. */
    protected boolean hearsWhileTransmitting() {
        return false;
    }

    /** True while RF is going out: PTT held in SSB, or morse keyed within the last couple of ticks. A blown radio sends nothing. */
    protected boolean isTransmittingNow(ServerLevel serverLevel) {
        if(isBlown())
            return false;
        return canTransmitVoice() || (lastTransmitGameTime >= 0 && serverLevel.getGameTime() - lastTransmitGameTime <= 2);
    }

    // -------------------- HAND MIC ON A CORD --------------------

    /** False for radios without a hand mic to lend (receivers, the repeater). */
    public boolean canLendHandMic() {
        return true;
    }

    /** The player who has this radio's hand mic out, null while it is on the radio (synced, for drawing the cord). */
    @Nullable
    public UUID getMicHolder() {
        return micHolder;
    }

    /** Which lending the mic out now is, null while it is on the radio. */
    @Nullable
    public UUID getMicId() {
        return micId;
    }

    /**
     * Sneak-right-click with an empty hand: the radio's hand mic goes into the player's hand on its cord. One mic
     * per radio. Holding right click with it talks (see {@link #tickHandMic}).
     */
    public void takeHandMic(ServerPlayer player) {
        if(micHolder != null && !micHolder.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(Radiocraft.translationKey("message", "mic_in_use")), true);
            return;
        }
        if(micHolder != null)
            return; // Already in their hand.
        if(!player.getMainHandItem().isEmpty()) {
            player.displayClientMessage(Component.translatable(Radiocraft.translationKey("message", "mic_hand_full")), true);
            return;
        }
        micHolder = player.getUUID();
        micId = UUID.randomUUID();
        ItemStack mic = new ItemStack(RadiocraftItems.CORDED_MICROPHONE.get());
        mic.set(RadiocraftDataComponent.MIC_CORD.get(), new MicCord(worldPosition, micId));
        player.setItemInHand(InteractionHand.MAIN_HAND, mic);
        level.playSound(null, worldPosition, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
        setChanged();
        updateBlock();
    }

    /** Right-click with the mic: it goes back on the radio. */
    public void hangUpHandMic(@Nullable Player player) {
        if(player != null)
            removeMic(player);
        releaseMic();
        if(level != null)
            level.playSound(null, worldPosition, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    /**
     * While the mic is out: the cord snaps back if its holder has gone, is more than {@link #MIC_CORD_LENGTH} blocks
     * away or no longer holds it in a hand, and otherwise holding right click (reported by their client, as for
     * handhelds) with the mic in the main hand keys the radio.
     */
    private void tickHandMic(ServerLevel serverLevel) {
        if(micHolder == null)
            return;
        Player player = serverLevel.getPlayerByUUID(micHolder);
        boolean inHand = player != null && (CordedMicrophoneItem.isMicOf(player.getMainHandItem(), this) || CordedMicrophoneItem.isMicOf(player.getOffhandItem(), this));
        if(player == null || !inHand || player.position().distanceTo(worldPosition.getCenter()) > MIC_CORD_LENGTH) {
            if(player != null)
                removeMic(player);
            releaseMic();
            serverLevel.playSound(null, worldPosition, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
            return;
        }
        boolean talking = CordedMicrophoneItem.isMicOf(player.getMainHandItem(), this)
                && PlayerRadioManager.get(micHolder).map(PlayerRadio::isUseHeld).orElse(false);
        if(talking != micKeyed) {
            micKeyed = talking;
            setPTTDown(talking);
            // The mic's PTT switch clicks in the player's hand, as the desk mic's button does; softer letting go.
            serverLevel.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), talking ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.PLAYERS, talking ? 0.4F : 0.25F, talking ? 1.6F : 1.8F);
        }
    }

    /** Takes this radio's mic out of the player's inventory, wherever it is. */
    private void removeMic(Player player) {
        Inventory inventory = player.getInventory();
        for(int i = 0; i < inventory.getContainerSize(); i++) {
            if(CordedMicrophoneItem.isMicOf(inventory.getItem(i), this))
                inventory.setItem(i, ItemStack.EMPTY);
        }
    }

    /** The mic is back on the radio: stop keying if it was, and forget the lending. */
    private void releaseMic() {
        if(micKeyed) {
            micKeyed = false;
            setPTTDown(false);
        }
        micHolder = null;
        micId = null;
        setChanged();
        updateBlock();
    }

    // -------------------- DESK MICROPHONE --------------------

    /** Within this many blocks of the desk mic a voice goes out at full level; further off it falls as 1 / distance. */
    public static final double DESK_MIC_CLOSE = 1.5D;
    /** Past this many blocks the desk mic hears nothing. */
    public static final double DESK_MIC_REACH = 16.0D;
    /** How far the player holding the desk mic's button can wander before it lets go. */
    public static final double DESK_MIC_HOLD_REACH = 6.0D;

    /** How loud a voice {@code distance} blocks from the desk mic comes through: 1 up close, half at 3 blocks, none past 16. */
    public static double deskMicGain(double distance) {
        if(distance > DESK_MIC_REACH)
            return 0.0D;
        return distance <= DESK_MIC_CLOSE ? 1.0D : DESK_MIC_CLOSE / distance;
    }

    /** Right click on the desk mic at {@code micPos}: the radio talks while the player keeps holding right click. */
    public void pressDeskMic(Player player, BlockPos micPos) {
        deskMicPos = micPos;
        deskMicUser = player.getUUID();
    }

    /** Sneak and right click on the desk mic: talking stays on until clicked again (or the timeout). True when now locked. */
    public boolean toggleDeskMicLock(Player player, BlockPos micPos) {
        deskMicPos = micPos;
        deskMicLocked = !deskMicLocked;
        deskMicUser = deskMicLocked ? null : deskMicUser;
        return deskMicLocked;
    }

    public boolean isDeskMicLocked() {
        return deskMicLocked;
    }

    public boolean isDeskMicKeyed() {
        return deskMicKeyed;
    }

    /** Keys the radio while the desk mic is held or locked, and lets go after the timeout with a short blip. */
    private void tickDeskMic(ServerLevel serverLevel) {
        boolean held = false;
        if(deskMicUser != null) {
            Player player = serverLevel.getPlayerByUUID(deskMicUser);
            held = player != null && deskMicPos != null && player.position().distanceTo(deskMicPos.getCenter()) <= DESK_MIC_HOLD_REACH
                    && PlayerRadioManager.get(deskMicUser).map(PlayerRadio::isUseHeld).orElse(false);
            if(!held)
                deskMicUser = null; // Let go of the button.
        }
        boolean keyed = deskMicLocked || held;
        long now = serverLevel.getGameTime();
        int timeout = RadiocraftServerConfig.DESK_MIC_TIMEOUT_SECONDS.get() * 20;
        if(keyed && deskMicKeyed && timeout > 0 && now - deskMicKeyedSince >= timeout) {
            keyed = false;
            deskMicLocked = false;
            deskMicUser = null;
            serverLevel.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.6F, 0.6F); // Timed out.
        }
        if(keyed != deskMicKeyed) {
            deskMicKeyed = keyed;
            deskMicKeyedSince = keyed ? now : -1L;
            setPTTDown(keyed);
        }
    }

    /** A voice heard while the desk mic is keyed comes through quieter the further the speaker stands from the mic. */
    @Override
    public void acceptVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, UUID sourcePlayer, Vec3 speakerPos) {
        BlockPos mic = deskMicPos;
        if(deskMicKeyed && mic != null && speakerPos != null) {
            double gain = deskMicGain(speakerPos.distanceTo(mic.getCenter()));
            if(gain <= 0.0D)
                return;
            if(gain < 1.0D) {
                short[] quieter = new short[rawAudio.length];
                for(int i = 0; i < rawAudio.length; i++)
                    quieter[i] = (short)Math.round(rawAudio[i] * gain);
                rawAudio = quieter;
            }
        }
        acceptVoicePacket(level, rawAudio, sourcePlayer);
    }

    // -------------------- BLOWN TRANSMITTER --------------------

    /** True once the transmitter has burnt out (see {@link RadioBlock#BLOWN}). */
    public boolean isBlown() {
        BlockState state = getBlockState();
        return state.hasProperty(RadioBlock.BLOWN) && state.getValue(RadioBlock.BLOWN);
    }

    /** False for transmitters with SWR protection, which never blow. */
    /** The SWR a transmitter sees with no antenna on its coax, for blowing it out (maintainer, 2026-11-08). */
    public static final double NO_ANTENNA_SWR = 6.0D;

    protected boolean canBlow() {
        return true;
    }

    /**
     * Each tick on the air into a bad SWR may burn the transmitter out, at {@link SWRHelper#blowChancePerSecond} spread
     * over the second's 20 ticks.
     */
    private void tickBlowout(ServerLevel serverLevel) {
        if(!canBlow() || !isTransmittingNow(serverLevel))
            return;
        if(getModulation() == com.arrl.radiocraft.common.radio.voice.Modulation.SSB && !puttingOutSsb(serverLevel.getGameTime()))
            return; // SSB has no carrier: keyed but silent, nothing goes out to reflect (2026-11-09).
        double swr = antennaSWR <= 0.01D ? NO_ANTENNA_SWR : antennaSWR; // Nothing on the coax: the open connector reflects it all back.
        double perSecond = SWRHelper.blowChancePerSecond(swr, RadiocraftServerConfig.RADIO_BLOW_SWR.get(), RadiocraftServerConfig.RADIO_BLOW_CHANCE.get());
        if(perSecond > 0.0D && serverLevel.random.nextDouble() < perSecond / 20.0D)
            blow(serverLevel);
    }

    /**
     * Burns out the transmitter: a pop and a puff of smoke, and the radio is blown from now on. A radio built with a
     * poor crystal may instead go up like TNT ({@code radio_explode_chance}); what is left of it drops as salvage.
     */
    public void blow(ServerLevel serverLevel) {
        if(isBlown())
            return;
        stopTransmitInterference(serverLevel);
        BlockPos pos = worldPosition;
        serverLevel.setBlock(pos, getBlockState().setValue(RadioBlock.BLOWN, true), Block.UPDATE_ALL);
        Vec3 centre = pos.getCenter();
        serverLevel.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 0.8F);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, centre.x, centre.y + 0.3D, centre.z, 12, 0.2D, 0.1D, 0.2D, 0.02D);
        if(getCrystalQuality() == CrystalQuality.POOR && serverLevel.random.nextDouble() < RadiocraftServerConfig.RADIO_EXPLODE_CHANCE.get())
            serverLevel.explode(null, centre.x, centre.y, centre.z, 4.0F, Level.ExplosionInteraction.TNT);
    }

    /** Subclasses call this whenever they put something on the air that PTT does not already cover (CW keying). */
    private volatile double txPeak = 0.0D;
    private volatile long txPeakGameTime = -1L;

    /** How hard the voice is driving the transmitter now, 0 to 1 (its last frame's peak, or 0 if none lately). */
    public double getTxModulation() {
        return level != null && txPeakGameTime >= 0 && level.getGameTime() - txPeakGameTime <= 3 ? Math.min(1.0D, txPeak) : 0.0D;
    }

    /** Last game time a voice frame with sound in it went out, -1 for never (any thread). */
    private volatile long lastSpeechGameTime = -1L;
    /** A frame is speech when it peaks over this, of full scale: room hiss at the mic is not. */
    static final int SPEECH_PEAK = 1000;

    static boolean hasSpeech(short[] pcm) {
        for(short sample : pcm)
            if(sample > SPEECH_PEAK || sample < -SPEECH_PEAK)
                return true;
        return false;
    }

    /** True if SSB is putting power out now: speech, CW or data within the last couple of ticks. */
    private boolean puttingOutSsb(long now) {
        return (lastSpeechGameTime >= 0 && now - lastSpeechGameTime <= 2) || (lastTransmitGameTime >= 0 && now - lastTransmitGameTime <= 2);
    }

    protected void recordTransmit() {
        if(level != null)
            lastTransmitGameTime = level.getGameTime();
    }

    /** Low-power rigs reach less redstone when transmitting. */
    public boolean isQrp() {
        return false;
    }

    /** What the current transmission is doing to nearby redstone, null while not transmitting. */
    @Nullable
    public TransmitInterference.Targets getTransmitTargets() {
        return transmitTargets;
    }

    /** Called by {@link RedstoneInterference} on the server thread for each redstone switch coupled into this station. */
    public void addInterference(double amount) {
        pendingInterference += amount;
    }

    /**
     * Adds noise that has already been scaled by its source (a lightning crash, see
     * {@link com.arrl.radiocraft.common.radio.rfi.LightningStatic}): it skips the redstone band and config factors
     * but goes out to the clients in the same packet. Server thread only.
     */
    public void addDirectInterference(double amount) {
        pendingDirectInterference += Math.max(0.0D, amount);
    }

    /** The decaying interference level, 0 to 1, on whichever side this block entity lives. */
    public double getInterference() {
        return level == null ? 0.0D : interference.get(level.getGameTime());
    }

    public InterferenceLevel getInterferenceLevel() {
        return interference;
    }

    /** The redstone part of the interference, 0 to 1 (see {@link #redstoneInterference}). */
    public double getRedstoneInterference() {
        return level == null ? 0.0D : redstoneInterference.get(level.getGameTime());
    }

    public InterferenceLevel getRedstoneInterferenceLevel() {
        return redstoneInterference;
    }

    // -------------------- PARROT --------------------

    /**
     * Turns the radio into a parrot (simplex repeater): whatever it receives is sent out again through its own antenna
     * {@code delayTicks} later, so one player can hear their own transmission come back. 0 switches it off. Not saved
     * with the world: a {@link com.arrl.radiocraft.common.blockentities.ParrotControllerBlockEntity} next to the radio
     * re-applies its delay every tick, and {@code /rcdebug parrot} sets it directly for testing.
     */
    public void setParrotDelay(int delayTicks) {
        parrotDelayTicks = Math.max(0, delayTicks);
        if(parrotDelayTicks == 0)
            parrotVoiceQueue.clear();
    }

    public int getParrotDelay() {
        return parrotDelayTicks;
    }

    public boolean isParrot() {
        return parrotDelayTicks > 0;
    }

    /** Ticks of quiet after the last echoed frame before the parrot counts the over as finished and beeps. */
    public static final int PARROT_BEEP_GAP_TICKS = 3;
    private final ArrayDeque<short[]> parrotBeep = new ArrayDeque<>();
    private boolean parrotBeepPending = false;
    private UUID parrotVoiceId = null;

    /**
     * Sends out every queued transmission whose delay has run out, and once an echoed voice over has finished, a
     * courtesy beep like a repeater's. Server tick only.
     */
    protected void tickParrot(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        boolean echoed = false;
        ModerationHooks.Tag was = ModerationHooks.push(ModerationHooks.Tag.PARROT); // An echo: the original was logged at its source.
        try {
            for(QueuedVoice next = parrotVoiceQueue.peek(); next != null && next.dueTick() <= now; next = parrotVoiceQueue.peek()) {
                parrotVoiceQueue.poll();
                acceptVoicePacket(() -> serverLevel, next.audio(), next.sourcePlayer());
                recordParrotEcho(serverLevel);
                echoed = true;
            }
        }
        finally {
            ModerationHooks.pop(was);
        }
        if(echoed)
            parrotBeepPending = true;
        else if(parrotBeepPending && parrotVoiceQueue.isEmpty() && now - lastParrotEchoGameTime >= PARROT_BEEP_GAP_TICKS) {
            parrotBeepPending = false;
            if(isParrot())
                parrotBeep.addAll(RepeaterAudio.courtesyBeep());
        }
        if(parrotBeep.isEmpty())
            return;
        if(parrotVoiceId == null)
            parrotVoiceId = UUID.nameUUIDFromBytes(("radiocraft-parrot-" + worldPosition.asLong()).getBytes(StandardCharsets.UTF_8));
        int frames = now % 2 == 0 ? 3 : 2; // 2.5 frames of 20 ms per 50 ms tick.
        ModerationHooks.Tag beep = ModerationHooks.push(ModerationHooks.Tag.SKIP); // Its courtesy beep is not worth a line.
        try {
            for(int i = 0; i < frames && !parrotBeep.isEmpty(); i++) {
                acceptVoicePacket(() -> serverLevel, parrotBeep.poll(), parrotVoiceId);
                recordParrotEcho(serverLevel);
            }
        }
        finally {
            ModerationHooks.pop(beep);
        }
    }

    /** True while the parrot has a courtesy beep still to send, for tests. */
    public boolean isParrotBeeping() {
        return !parrotBeep.isEmpty();
    }

    /** Remembers that an echo just went out, for the parrot controller's transmit light. */
    protected void recordParrotEcho(ServerLevel serverLevel) {
        lastParrotEchoGameTime = serverLevel.getGameTime();
    }

    /** Game time at which this radio last sent out a parrot echo, -1 if never. */
    public long getLastParrotEchoGameTime() {
        return lastParrotEchoGameTime;
    }

    /** One voice frame waiting to be echoed. */
    protected record QueuedVoice(short[] audio, UUID sourcePlayer, long dueTick) {}

    /**
     * Network objects only live in memory, so after a world reload the radio's object comes back switched off. Put it
     * back the way the block entity last saw it.
     */
    @Override
    public void onNetworkObjectCreated(Level level, BlockPos pos, BENetworkObject networkObject) {
        if(wasPowered && networkObject instanceof RadioNetworkObject radio)
            radio.setWantsOn(true); // The switch was on; whether it runs again depends on the power, as before.
    }

    // Override this for any additional ticks needed like CWSendBuffers
    protected void additionalTick() {}

    // -------------------- GROUND --------------------

    /** How well the station is grounded, 0 to 1 ({@link com.arrl.radiocraft.common.radio.ground.Grounding#station}); synced. */
    protected double groundQuality = 0.0D;
    /** How often the ground is measured again, ticks. */
    static final int GROUND_CHECK_TICKS = 100;

    public double getGroundQuality() {
        return groundQuality;
    }

    /** Measures the ground now and then: rods on the radio's ground wire, or on an arrestor's on its coax. HF only. */
    private void tickGround(ServerLevel serverLevel) {
        if((serverLevel.getGameTime() + worldPosition.asLong()) % GROUND_CHECK_TICKS != 0L)
            return;
        updateGround(serverLevel);
    }

    /** Measures the ground at once (also for tests and debug). */
    public void updateGround(ServerLevel serverLevel) {
        double quality = 0.0D;
        if(band != null && !band.isVhf() && getNetworkObject(serverLevel, worldPosition) instanceof RadioNetworkObject networkObject) {
            List<com.arrl.radiocraft.api.benetworks.BENetwork> coax = new java.util.ArrayList<>();
            for(com.arrl.radiocraft.api.benetworks.BENetwork network : networkObject.getNetworks().values())
                if(!(network instanceof com.arrl.radiocraft.api.benetworks.PowerBENetwork))
                    coax.add(network);
            quality = com.arrl.radiocraft.common.radio.ground.Grounding.station(serverLevel, worldPosition, coax);
        }
        quality = Math.round(quality * 100.0D) / 100.0D;
        if(quality != groundQuality) {
            groundQuality = quality;
            setChanged();
            updateBlock();
        }
    }

    // -------------------- POWER IMPLEMENTATION --------------------

    /** True if {@code player} may switch it on or off from its screen (every radio but an op-only Real-World Interface). */
    public boolean mayTogglePower(net.minecraft.world.entity.player.Player player) {
        return true;
    }

    @Override
    public void toggle() {
        if(!level.isClientSide()) {
            if(getNetworkObject(level, worldPosition) instanceof RadioNetworkObject networkObject) {
                networkObject.setWantsOn(!networkObject.wantsOn); // Settles isPowered at once from the energy on hand.
                updateIsReceiving();
                updateBlock();
            }
        }
    }

    public void overdraw() {}

    // -------------------- VOICE/RADIO IMPLEMENTATION --------------------

    @Override
    public BEVoiceReceiver getVoiceReceiver() {
        return voiceReceiver;
    }

    public boolean getSSBEnabled() {
        return ssbEnabled;
    }

    public void setSSBEnabled(boolean value) {
        if(ssbEnabled != value) {
            ssbEnabled = value;
            updateIsReceiving();
            updateBlock();
            setChanged();
        }
    }

    public void updateIsReceiving() {
        if(!level.isClientSide() && getNetworkObject(level, worldPosition) instanceof RadioNetworkObject networkObject) {
            if(canTransmitVoice()) {
                networkObject.setTransmitting(true);
                getVoiceReceiver().setReceiving(false);
            }
            else {
                networkObject.setTransmitting(false);
                getVoiceReceiver().setReceiving(ssbEnabled && networkObject.isPowered); // A radio that is off hears nothing.
            }
        }
    }

    /**
     * Server side entry point for a voice transmission that reached this radio's antenna, on its frequency or near it
     * ({@link Detuning}). Called from the voice chat thread. A signal the radio can hear is recorded (so the server,
     * debug tools and tests can see what arrived) and handed to the {@link BEVoiceReceiver} for playback; one that is
     * only close enough to splatter (FM) is remembered and roughens what the radio hears on its own frequency.
     *
     * @return True if the radio was listening and the packet is heard.
     */
    public boolean receiveVoice(AntennaVoicePacket packet) {
        scopeHearVoice(packet); // The band scope sees every frequency (computer batch 3B), shaped by its mode and voice.
        Detuning.Effect effect = getDetuning(packet.getFrequency(), packet.getSplatterReach());
        // Another mode than ours: SSB hears AM with its carrier's whistle and FM as a warble; AM hears FM as a warble and SSB only as QRM.
        com.arrl.radiocraft.common.radio.voice.ModeMix.Result mix = com.arrl.radiocraft.common.radio.voice.ModeMix.of(getModulation(), packet.getModulation(), effect, getTunedFrequency(), packet.getFrequency(),
                packet.isLowerSideband() == isLowerSideband());
        effect = mix.effect();
        if(effect.isNone() || isDeafNow())
            return false;
        boolean skip = isSkipPath(packet);
        double strength = packet.getStrength() * desense(packet) * getReceiverGain(); // The RF gain knob, where there is one.
        if(skip && level != null) // Skip signals swell and fade (QSB); line of sight holds steady.
            strength *= ReceiverMix.fading(level.getGameTime() / 20.0D, pathSeed(packet), RadiocraftServerConfig.SKIP_FADING_DEPTH.get());
        // SSB is only there while someone speaks (2026-11-09): the S-meter, the squelch and the static follow the voice.
        double voice = packet.getModulation() == com.arrl.radiocraft.common.radio.voice.Modulation.SSB ? packet.envelope() : 1.0D;
        boolean between = voice < ReceiverMix.SSB_SILENT;
        if(effect.audible() && !between)
            recordCarrier(strength * effect.strength() * voice); // A scanner's squelch opens whatever the speaker mode is.
        // A modem beside the radio copies the tones whatever mode the radio's speaker is in, as packets decode.
        if(level != null && isPowered() && effect.audible() && effect.pitchShiftHz() == 0.0D && effect.splatter() <= 0.0D
                && decodes(strength * effect.strength(), getNoiseFloor(skip)))
            lastCopyGameTime = level.getGameTime();
        if(!isListening())
            return false;
        if(!effect.audible()) { // The squelch stays shut; the splatter waits for a signal on frequency.
            adjacentQrm = effect.qrm() * strength;
            adjacentQrmGameTime = level == null ? 0L : level.getGameTime();
            return false;
        }
        if(!Ctcss.opens(getToneSquelch(), packet.getCtcssTone()))
            return false; // Tone squelch: not our tone, the speaker stays quiet.
        strength *= effect.strength();
        // Digital voice (commercial radios): decoded clean, scrambled without the key, a buzz on an analog receiver,
        // or gone below the cliff (the S-meter still shows it).
        com.arrl.radiocraft.common.radio.voice.DigitalVoice.Reception reception = com.arrl.radiocraft.common.radio.voice.DigitalVoice.receive(digitalDecoder(), packet.getVoiceMode(), strength);
        lastReception = reception;
        if(!between)
            recordSignal(strength * voice); // A pause between words reads nothing: the meter drops, the static comes up.
        if(reception == com.arrl.radiocraft.common.radio.voice.DigitalVoice.Reception.DROPPED)
            return false;
        if(!squelchOpenNow())
            return false; // Nothing on the meter reaches the squelch: the S-meter moves, the speaker stays shut.
        if(level != null) // Who was heard, for the radio computer's logbook (fixed slots, no allocation per frame).
            stationsHeard(packet, strength);
        if(level != null && com.arrl.radiocraft.common.radio.RepeaterRegistry.isRepeater(packet.getSourcePlayer())) // A repeater's own beep, carrier or ID: for the Repeater Directory.
            repeatersHeard.heard(packet.getSourcePlayer(), packet.getFrequency(), strength, level.getGameTime());
        ModerationHooks.heard(packet, this, level, worldPosition, null, strength); // A hearer of the logged transmission.
        if(isParrot()) // Queue the audio as it arrived, before the receiver scales it, so the echo carries one path's loss, not two.
            parrotVoiceQueue.add(new QueuedVoice(packet.getRawAudio().clone(), packet.getSourcePlayer(), lastSignalGameTime + parrotDelayTicks));
        double rasp = skip && band != null && band.isVhf() ? 1.0D : 0.0D; // A VHF signal beyond line of sight came off the aurora.
        if(!skip && band != null && !band.isVhf())
            rasp = blackoutDistortion(); // Local HF in a blackout gets through, but rough.
        // A DTMF decoder listens to the receiver's audio, before the speaker's processing changes it in place. Tones
        // pitched off by a detuned SSB receiver are no longer the keys' tones.
        if(dtmfDecoderLinked && this.level != null && Math.abs(effect.pitchShiftHz()) <= com.arrl.radiocraft.common.radio.voice.Dtmf.MAX_PITCH_SHIFT_HZ)
            dtmfListener.frame(dtmfSource(packet), packet.getRawAudio(), strength / copyThreshold(getNoiseFloor(skip)), rasp,
                    Math.max(effect.splatter(), Detuning.splatterOn(1.0D, getAdjacentQrm(), strength)), this.level.getGameTime());
        // FM holds its volume as the signal fades and gets noisier instead; SSB sinks into the noise. Decoded digital
        // has no noise at all: full volume, no FM hiss, no squelch tail.
        boolean digitalAudio = reception.digitalAudio();
        double level = isFM() || digitalAudio ? speakerGain
                : agc == AGC_OFF ? ReceiverMix.agcOffLevel(strength) * speakerGain // No AGC: as loud as it comes in.
                : isAM() ? ReceiverMix.amLevel(strength, getNoiseFloor(skip)) * speakerGain // The AGC holds AM up; it hisses as it weakens.
                : ReceiverMix.voiceLevel(strength, getNoiseFloor(skip)) * speakerGain;
        short[] audio = com.arrl.radiocraft.common.radio.voice.DigitalVoice.render(reception, packet.getRawAudio(), strength, nextDigitalSeed());
        if(mix.garble())
            audio = com.arrl.radiocraft.common.radio.voice.ModeMix.garble(audio, modeTones);
        if(mix.carrierHz() > 0.0D)
            audio = com.arrl.radiocraft.common.radio.voice.ModeMix.withCarrier(audio, mix.carrierHz(), modeTones);
        com.arrl.radiocraft.api.antenna.IAntenna.AntennaPos sentFrom = packet.getSource() == null ? null : packet.getSource().getAntennaPos(); // Read once: the voice thread races the server.
        double blocks = sentFrom == null ? Double.MAX_VALUE : Math.sqrt(Sublevels.distSqr(this.level, sentFrom.position(), worldPosition));
        boolean ssbHf = !isFM() && band != null && !band.isVhf();
        getVoiceReceiver().receive(packet, audio, level, rasp, effect.pitchShiftHz(), isFM() ? effect.splatter() : Math.max(effect.splatter(), ReceiverMix.overload(blocks)),
                Detuning.splatterOn(1.0D, getAdjacentQrm(), strength), isFM() && !digitalAudio ? strength : -1.0D, strength * ReceiverMix.nearness(blocks),
                skip && ssbHf ? RadiocraftServerConfig.SKIP_MULTIPATH_DEPTH.get() : 0.0D,
                isAM() && !digitalAudio ? ReceiverMix.amHiss(strength, getNoiseFloor(skip)) : -1.0D);
        return true;
    }

    /** The stations this radio heard talking lately (who, where, how strong), for the logbook (1.12 step 6). */
    private final com.arrl.radiocraft.common.radio.StationsHeard stationsHeard = new com.arrl.radiocraft.common.radio.StationsHeard();
    /** Repeaters heard by their own voice id (beep, hang carrier, ID), for the radio computer's Repeater Directory. */
    private final com.arrl.radiocraft.common.radio.StationsHeard repeatersHeard = new com.arrl.radiocraft.common.radio.StationsHeard();

    /**
     * The last {@link com.arrl.radiocraft.common.radio.StationsHeard#SLOTS} repeaters this radio heard send their own
     * audio (keyed by the repeater's voice id, see {@link com.arrl.radiocraft.common.radio.RepeaterRegistry}).
     */
    public com.arrl.radiocraft.common.radio.StationsHeard getRepeatersHeard() {
        return repeatersHeard;
    }

    /**
     * Notes who was heard, with a snapshot of where the transmitting antenna was at this moment (computer batch 3C).
     * The snapshot never leaves the server as coordinates: the logbook makes a grid square, biome and rounded
     * distance of it. No allocation for a station already in a slot (the dimension's id object is kept as it is).
     */
    private void stationsHeard(com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket packet, double strength) {
        com.arrl.radiocraft.api.antenna.IAntenna source = packet.getSource();
        com.arrl.radiocraft.api.antenna.IAntenna.AntennaPos at = source == null ? null : source.getAntennaPos();
        if(at == null || at.level() == null)
            stationsHeard.heard(packet.getSourcePlayer(), packet.getFrequency(), strength, level.getGameTime());
        else {
            BlockPos pos = at.world();
            stationsHeard.heard(packet.getSourcePlayer(), packet.getFrequency(), strength, level.getGameTime(), true, pos.getX(), pos.getZ(),
                    at.level().dimension().location());
        }
    }

    /** The stations this radio heard talking lately, server side (the radio computer's logbook reads them). */
    public com.arrl.radiocraft.common.radio.StationsHeard getStationsHeard() {
        return stationsHeard;
    }

    // -------------------- DIGITAL VOICE --------------------

    /** What the receiver made of the last transmission it heard (analog, decoded, scrambled...), for debug and tests. */
    private volatile com.arrl.radiocraft.common.radio.voice.DigitalVoice.Reception lastReception = com.arrl.radiocraft.common.radio.voice.DigitalVoice.Reception.ANALOG;
    private long digitalSeed = System.nanoTime() | 1L; // Voice thread; a race only changes the noise.

    /** What the receiver made of the last transmission it heard: analog FM/SSB, or how it took a digital one. */
    public com.arrl.radiocraft.common.radio.voice.DigitalVoice.Reception getLastReception() {
        return lastReception;
    }

    private long nextDigitalSeed() {
        digitalSeed = digitalSeed * 6364136223846793005L + 1442695040888963407L;
        return digitalSeed;
    }

    /**
     * How this radio's transmissions go out: analog for ham radios; a commercial radio's channel may be digital (and
     * encrypted). Read on the voice thread around each frame sent (see {@link #transmitVoice}).
     */
    protected com.arrl.radiocraft.common.radio.voice.DigitalVoice.Mode transmitMode() {
        return com.arrl.radiocraft.common.radio.voice.DigitalVoice.Mode.ANALOG;
    }

    /** The receiver's digital decoder (with its key), or null for an analog receiver, which hears digital as a buzz. */
    @Nullable
    protected com.arrl.radiocraft.common.radio.voice.DigitalVoice.Decoder digitalDecoder() {
        return null;
    }

    /**
     * True while the receiver takes signals in. Normally that is when it plays them (switched on, voice mode, not
     * transmitting); a repeater listens whenever it is on, and its speaker is a separate switch.
     */
    protected boolean isListening() {
        return getVoiceReceiver().isReceiving();
    }

    /** Ticks a splattering neighbour is remembered for (it sends a frame every 20 ms, so it is still talking). */
    public static final int ADJACENT_QRM_HOLD_TICKS = 2;

    // Splatter from a signal a few kHz off (FM), already scaled by its strength. Written on the voice chat thread.
    private volatile double adjacentQrm = 0.0D;
    private volatile long adjacentQrmGameTime = -1L;

    /** Splatter (scaled by the neighbour's strength) a signal just off frequency is putting on this radio, 0 if none lately. */
    public double getAdjacentQrm() {
        long now = level == null ? 0L : level.getGameTime();
        return adjacentQrmGameTime >= 0 && now - adjacentQrmGameTime <= ADJACENT_QRM_HOLD_TICKS ? adjacentQrm : 0.0D;
    }

    /** Game time a signal just off frequency last splattered onto this radio, -1 if never. */
    public long getAdjacentQrmGameTime() {
        return adjacentQrmGameTime;
    }

    /** How this radio's receiver takes a signal on {@code txHz}: as SSB, or as FM for {@link #isFM() FM} radios. */
    public Detuning.Effect getDetuning(float txHz) {
        return getDetuning(txHz, 1.0D);
    }

    /** {@link #getDetuning(float)} for a transmitter whose splatter reaches {@code splatterReach} times as far (out of band). */
    public Detuning.Effect getDetuning(float txHz, double splatterReach) {
        if(isAM())
            return Detuning.am(getTunedFrequency(), txHz, splatterReach);
        if(isFM())
            return Detuning.fm(getTunedFrequency(), txHz, band != null && band.isVhf() ? RadiocraftServerConfig.VHF_FREQUENCY_STEP.get() : 0, splatterReach);
        return Detuning.ssb(getTunedFrequency(), txHz, isLowerSideband(), splatterReach);
    }

    /** The carrier and warble this radio makes of other modes' signals, kept between frames (voice thread). */
    private final com.arrl.radiocraft.common.radio.voice.ModeMix.Tones modeTones = new com.arrl.radiocraft.common.radio.voice.ModeMix.Tones();

    /** How this radio sends and hears voice: AM ({@link #isAM()}), FM ({@link #isFM()}) or SSB. */
    public com.arrl.radiocraft.common.radio.voice.Modulation getModulation() {
        return isAM() ? com.arrl.radiocraft.common.radio.voice.Modulation.AM : isFM() ? com.arrl.radiocraft.common.radio.voice.Modulation.FM : com.arrl.radiocraft.common.radio.voice.Modulation.SSB;
    }

    /** True if the radio receives voice as FM (VHF radios); SSB otherwise. */
    public boolean isFM() {
        return false;
    }

    /**
     * True if the radio receives voice as AM (the CB radios, 2026-11-08): heard as SSB is, everyone on the channel at once
     * and with bleedover from the next one, but held at volume like FM, with hiss only as the signal weakens.
     */
    public boolean isAM() {
        return false;
    }

    /**
     * The receiver's front-end gain, 1 for full (2026-11-09): an RF GAIN knob turns signals and the band's noise down
     * together. Read on the voice thread and the client (the static).
     */
    public double getReceiverGain() {
        return 1.0D;
    }

    /**
     * How much of the impulse noise (redstone sparks, aurora crackle) reaches the speaker, 1 for all of it: a noise
     * blanker or limiter takes most of it out (2026-11-09).
     */
    public double getImpulseNoiseFactor() {
        return 1.0D;
    }

    /** SSB on bands below 10 MHz (80 and 40m) is lower sideband, above it upper, as hams use it. */
    public boolean isLowerSideband() {
        return band != null && band.minFrequency() < 10_000_000;
    }

    /** True if the packet came from further away than line of sight on this band: by skip (or, on VHF, the aurora). */
    protected boolean isSkipPath(IAntennaPacket packet) {
        com.arrl.radiocraft.api.antenna.IAntenna.AntennaPos from = packet.getSource() == null ? null : packet.getSource().getAntennaPos();
        if(band == null || from == null)
            return false;
        return Sublevels.distSqr(level, from.position(), worldPosition) > (double)band.losRange() * band.losRange();
    }

    /** What a transmitter close to this radio's antenna leaves of {@code packet}'s signal ({@link com.arrl.radiocraft.common.radio.TransmitterLog#desense}). */
    protected double desense(IAntennaPacket packet) {
        if(level == null)
            return 1.0D;
        com.arrl.radiocraft.api.antenna.IAntenna own = packet.getDestination();
        com.arrl.radiocraft.api.antenna.IAntenna.AntennaPos ownPos = own == null ? null : own.getAntennaPos();
        BlockPos at = ownPos == null ? worldPosition : ownPos.position();
        return com.arrl.radiocraft.common.radio.TransmitterLog.desense(level, at, packet.getFrequency(), packet.getSource(), own);
    }

    /** A number for the path between the sender and this radio, so each path fades on its own. */
    private long pathSeed(IAntennaPacket packet) {
        com.arrl.radiocraft.api.antenna.IAntenna.AntennaPos from = packet.getSource() == null ? null : packet.getSource().getAntennaPos();
        return (from == null ? 0L : from.position().asLong() * 31L) + worldPosition.asLong();
    }

    /**
     * The noise this radio's receiver hears, 0 to 1: on HF the solar noise floor plus redstone and lightning noise and
     * any aurora overhead, on VHF the fixed receiver noise ({@link RadiocraftServerConfig#VHF_NOISE_FLOOR}). The client
     * plays the same sum as static ({@link com.arrl.radiocraft.common.sounds.RadioStaticSoundInstance}).
     */
    public double getNoiseFloor() {
        return getNoiseFloor(1.0D);
    }

    /**
     * How much a line of sight HF voice flutters under the solar noise: none up to a poor day's noise (0.7), rising to
     * {@link RadiocraftServerConfig#BLACKOUT_LOS_DISTORTION} in a full blackout (maintainer, 2026-10-31).
     */
    public double blackoutDistortion() {
        SolarEventInstance event = level == null ? null : SolarEventManager.getEvent(level);
        double noise = event == null ? 0.0D : event.getEvent().getNoise();
        return RadiocraftServerConfig.BLACKOUT_LOS_DISTORTION.get() * Math.max(0.0D, Math.min(1.0D, (noise - 0.7D) / 0.3D));
    }

    /**
     * The noise a signal over this path is copied against: a skip signal against the whole noise floor, a line of
     * sight one against only {@link RadiocraftServerConfig#LOS_SOLAR_NOISE_SHARE} of the solar part (in a blackout the
     * sky noise is mostly what the dead ionosphere would have carried, and local ground wave still gets through).
     */
    public double getNoiseFloor(boolean skip) {
        return getNoiseFloor(skip ? 1.0D : RadiocraftServerConfig.LOS_SOLAR_NOISE_SHARE.get());
    }

    private double getNoiseFloor(double solarShare) {
        if(level == null)
            return 0.0D;
        if(band != null && band.isVhf())
            return getCrystalEffect().applyNoise(RadiocraftServerConfig.VHF_NOISE_FLOOR.get());
        SolarEventInstance event = SolarEventManager.getEvent(level);
        // A ground takes local noise (riding in on the coax shield) off hardest, and some of the rest (2026-11-08).
        double noise = (event == null ? 0.0D : event.getEvent().getNoise()) * solarShare
                + getInterference() * com.arrl.radiocraft.common.radio.ground.GroundQuality.localNoiseFactor(groundQuality) * getImpulseNoiseFactor();
        if(Aurora.affects(level, worldPosition))
            noise += RadiocraftServerConfig.AURORA_STATIC.get() * getImpulseNoiseFactor();
        noise *= com.arrl.radiocraft.common.radio.ground.GroundQuality.noiseFactor(groundQuality);
        return getCrystalEffect().applyNoise(Math.min(1.0D, noise)) * getReceiverGain();
    }

    // -------------------- CRYSTAL --------------------

    /** The crystal the radio was built with: each quality is its own block. */
    public CrystalQuality getCrystalQuality() {
        return getBlockState().getBlock() instanceof RadioBlock radio ? radio.getCrystalQuality() : CrystalQuality.GOOD;
    }

    /**
     * The crystal as the radio behaves: QRP rigs are designed around poor crystals, so they hear and tune like a good
     * one whatever they were built with.
     */
    public CrystalQuality getCrystalEffect() {
        return isQrp() ? CrystalQuality.GOOD : getCrystalQuality();
    }

    /** Hz a poor crystal has the radio off its dial frequency right now, 0 for any other crystal. */
    public double getDriftHz() {
        return level == null ? 0.0D : getCrystalEffect().driftHz(level.getGameTime(), worldPosition.asLong());
    }

    /** Where the radio really is: the dial frequency plus any crystal drift. It listens and transmits here. */
    public float getTunedFrequency() {
        return (float)(frequency + getDriftHz());
    }

    // -------------------- RECEIVED SIGNAL (CLIENT SYNC) --------------------

    /** Ticks the synced signal holds after the last frame, so the static comes back up gently between words. */
    public static final int SIGNAL_SYNC_HOLD_TICKS = 6;

    /**
     * Signal strength the client uses to duck the static under a received signal ({@link ReceiverMix#noiseDuck}),
     * rounded to 1/20 so it is only resent when it really moves.
     */
    protected double syncedSignal = 0.0D;

    public double getSyncedSignal() {
        return syncedSignal;
    }

    private boolean tickSyncedSignal(long gameTime) {
        double current = lastSignalGameTime >= 0 && gameTime - lastSignalGameTime <= SIGNAL_SYNC_HOLD_TICKS ? agcSignal : 0.0D;
        double rounded = Math.round(current * 20.0D) / 20.0D;
        if(current > 0.0D && rounded == 0.0D)
            rounded = 0.05D; // A faint signal still counts.
        if(rounded == syncedSignal)
            return false;
        syncedSignal = rounded;
        return true;
    }

    /** Remembers the strength and game time of the last signal that reached this radio. */
    protected void recordSignal(double strength) {
        long now = level == null ? 0L : level.getGameTime();
        lastSignalStrength = strength;
        lastSignalGameTime = now;
        if(strength >= agcSignal || now - agcSignalGameTime > AGC_SIGNAL_TICKS) {
            agcSignal = strength;
            agcSignalGameTime = now;
        }
    }

    /**
     * What the static ducks under: with stations talking at once the strongest sets the AGC, not whichever frame came
     * last, so a weaker one does not bring the static up between the stronger one's frames. It falls to a weaker signal
     * once nothing stronger has come for {@link #AGC_SIGNAL_TICKS}.
     */
    protected volatile double agcSignal = 0.0D;
    protected volatile long agcSignalGameTime = -1L;
    private static final int AGC_SIGNAL_TICKS = 2;

    /** When a signal last came in strong enough over the noise for a modem to copy it, -1 for never. Server side. */
    private volatile long lastCopyGameTime = -1L;

    /**
     * True if a signal strong enough for a modem to copy reached the radio within the last few ticks: a weather fax
     * printing from it prints clean lines, and speckle while it is not.
     */
    public boolean isCopying(long gameTime) {
        return lastCopyGameTime >= 0 && gameTime - lastCopyGameTime <= 3;
    }

    /**
     * Plays a frame from a station that is not in the world (the weather fax coast station) through the radio's
     * speaker at {@code strength}, lighting its receive lamp, as if it had come in off the antenna. Server side.
     */
    public void playBroadcast(Object source, short[] frame, double strength) {
        if(!(level instanceof ServerLevel serverLevel) || !isPowered() || !isListening())
            return;
        recordSignal(strength);
        lastCopyGameTime = serverLevel.getGameTime();
        if(!squelchOpenNow())
            return;
        double out = isFM() ? speakerGain : isAM() ? ReceiverMix.amLevel(strength, getNoiseFloor(false)) * speakerGain
                : ReceiverMix.voiceLevel(strength, getNoiseFloor(false)) * speakerGain;
        getVoiceReceiver().receive(serverLevel, source, frame, out);
    }

    /** Strength (0-1) of the last voice or CW signal this radio received, 0 if it never received one. */
    public double getLastSignalStrength() {
        return lastSignalStrength;
    }

    /** Game time at which the last signal was received, -1 if none. */
    public long getLastSignalGameTime() {
        return lastSignalGameTime;
    }

    public float getFrequency() {
        return frequency;
    }

    public void setFrequency(float frequency) {
        this.frequency = frequency;
    }

    public float getSpeakerGain() {
        return speakerGain;
    }

    public void setSpeakerGain(float speakerGain) {
        this.speakerGain = Mth.clamp(speakerGain, 0.0F, 2.0F);
        setChanged();
        updateBlock();
    }

    public float getMicGain() {
        return micGain;
    }

    /** Client side, so a turned dial shows its new value before the server syncs it back. */
    public void setSpeakerGainClient(float gain) {
        this.speakerGain = Mth.clamp(gain, 0.0F, 2.0F);
    }

    public void setMicGainClient(float gain) {
        this.micGain = Mth.clamp(gain, 0.0F, 2.0F);
    }

        public float getQuickHz() {
        return quickHz;
    }

    /** True while on the quick-recall frequency (the QUICK key is lit). */
    public boolean isOnQuick() {
        return quickReturnHz > 0.0F;
    }

    /** Held QUICK: remember this frequency. Server side. */
    public void storeQuick() {
        quickHz = frequency;
        quickReturnHz = 0.0F;
        setChanged();
        updateBlock();
    }

    /** Pressed QUICK: to the stored frequency, or back from it. Server side. */
    public void toggleQuick() {
        if(quickReturnHz > 0.0F) {
            float back = quickReturnHz;
            quickReturnHz = 0.0F;
            recallFrequency(back);
        }
        else if(quickHz > 0.0F) {
            float from = frequency;
            if(recallFrequency(quickHz))
                quickReturnHz = from;
        }
        setChanged();
        updateBlock();
    }

    /** Tunes straight to {@code hz} (QUICK, memories); the all-band radio changes band to get there. False if it cannot. */
    public boolean recallFrequency(float hz) {
        return tuneTo(hz);
    }

    public int getAgc() {
        return agc;
    }

    /** Server side, from the AGC switch. */
    public void setAgc(int value) {
        this.agc = Mth.clamp(value, AGC_OFF, AGC_FAST);
        setChanged();
        updateBlock();
    }

    public void setAgcClient(int value) {
        this.agc = Mth.clamp(value, AGC_OFF, AGC_FAST);
    }

    /** The squelch, 0 (open) to 1 (only a full-scale signal), of the S-meter's scale. */
    public float getSquelchLevel() {
        return squelch;
    }

    /** True if this radio has a squelch knob on its panel (the screen shows one, and the server takes its setting). */
    public boolean hasSquelchKnob() {
        return false;
    }

    public static final float SQUELCH_STEP = 0.1F;

    /** Server side, from the squelch knob. */
    public void setSquelch(float level) {
        this.squelch = Mth.clamp(level, 0.0F, 1.0F);
        setChanged();
        updateBlock();
    }

    /** Client side, so a turned knob shows its new setting before the server syncs it back. */
    public void setSquelchClient(float level) {
        this.squelch = Mth.clamp(level, 0.0F, 1.0F);
    }

    /** True if a signal of {@code strength} (the S-meter's 0 to 1) opens the squelch. */
    public boolean squelchOpens(double strength) {
        return squelchOpens(getSquelchLevel(), strength);
    }

    /** True if a signal of {@code strength} opens a squelch set to {@code squelch}, both of the S-meter's scale. */
    public static boolean squelchOpens(double squelch, double strength) {
        return squelch <= 0.0D || strength + 1.0E-9D >= squelch;
    }

    /**
     * Client side: true if the background static plays. A squelch at or under the band's noise ({@code noise}) is held
     * open by the noise itself; one set higher shuts it until a signal over the setting comes in (the static then comes
     * up under it, as on a real set).
     */
    public boolean squelchOpenOnStatic(double noise) {
        return getSquelchLevel() <= 0.0F || syncedSquelchOpen;
    }

    /**
     * Server side: true if the squelch is open now (2026-11-09). A squelch opens on whatever the S-meter reads, the band's
     * noise or the strongest station heard in the last few ticks, and then lets everything through: every station on
     * the channel, and the static. Static is never heard without the stations under it, nor a station without static.
     */
    public boolean squelchOpenNow() {
        float squelch = getSquelchLevel();
        if(squelch <= 0.0F)
            return true;
        if(squelchOpensOnNoise(squelch, meterNoise))
            return true;
        long now = level == null ? 0L : level.getGameTime();
        return agcSignalGameTime >= 0 && now - agcSignalGameTime <= SQUELCH_HOLD_TICKS && squelchOpens(squelch, agcSignal);
    }

    /** Ticks the squelch stays open after the strongest station's last frame (between words). */
    static final int SQUELCH_HOLD_TICKS = 6;

    /** The band's noise as the S-meter reads it (server: refreshed every few ticks; client: synced). */
    protected volatile double meterNoise = 0.0D;
    /** Whether the squelch is open, as the server last said (client: gates the static with the stations). */
    protected boolean syncedSquelchOpen = true;
    /** True while this transmitter is putting power out (synced): SSB only while there is speech, CW or data. */
    protected boolean syncedPowerOut = false;

    /** The band noise on the S-meter, 0 to 1 (client: synced). */
    public double getMeterNoise() {
        return meterNoise;
    }

    /** True while the transmitter is putting power out (client: synced): AM and FM whenever keyed, SSB while speaking. */
    public boolean isPowerOut() {
        return syncedPowerOut;
    }

    /** Refreshes the noise, the squelch and power-out for the client; true if any changed. Server, every few ticks. */
    private boolean tickReceiverSync(long now, boolean powered) {
        boolean changed = false;
        if(now % 4L == 0L) {
            double noise = powered ? Math.round(getNoiseFloor(true) * 20.0D) / 20.0D : 0.0D;
            if(noise != meterNoise) {
                meterNoise = noise;
                changed = true;
            }
        }
        boolean open = squelchOpenNow();
        if(open != syncedSquelchOpen) {
            syncedSquelchOpen = open;
            changed = true;
        }
        boolean out = powered && !isBlown() && isPTTDown() && (getModulation() != com.arrl.radiocraft.common.radio.voice.Modulation.SSB || puttingOutSsb(now))
                || powered && !isBlown() && lastTransmitGameTime >= 0 && now - lastTransmitGameTime <= 2;
        if(out != syncedPowerOut) {
            syncedPowerOut = out;
            changed = true;
        }
        return changed;
    }

    /** True if the band's own noise ({@code noise}, of the S-meter's scale) holds a squelch set to {@code squelch} open. */
    public static boolean squelchOpensOnNoise(double squelch, double noise) {
        return squelch <= 0.0D || noise + 1.0E-9D >= squelch;
    }

    public void setMicGain(float micGain) {
        this.micGain = Mth.clamp(micGain, 0.0F, 2.0F);
        setChanged();
        updateBlock();
    }

    public Band getBand() {
        return band;
    }

    public void setBand(Band band) {
        this.band = band;
        updateIsReceiving();
        updateBlock();
    }

    public boolean isPTTDown() {
        return isPTTDown;
    }

    public void setPTTDown(boolean value) {
        if(isPTTDown != value) {
            isPTTDown = value;
            updateIsReceiving();
            updateBlock();
            if(level != null && !level.isClientSide) // For "keys up but nobody hears it" reports (logs/debug.log).
                com.arrl.radiocraft.Radiocraft.LOGGER.debug("[ptt-debug] radio {} ptt={} ssb={} powered={} blown={} canTransmitVoice={} micPos={}", worldPosition.toShortString(),
                        isPTTDown, ssbEnabled, isPowered(), isBlown(), canTransmitVoice(), getPos());
        }
    }

    /**
     * Switches the radio to another band (radios with a band switch): the frequency goes to the bottom of the new band,
     * and the antenna match is re-measured on the next tick.
     */
    protected void changeBand(Band newBand) {
        band = newBand;
        frequency = newBand.minFrequency();
        if(level != null && !level.isClientSide())
            updateIsReceiving();
        updateBlock();
        setChanged();
    }

    /**
     * Move the current frequency by the specified number of steps as defined in {@link RadiocraftServerConfig}.
     *
     * @param stepCount The number of steps to increment by.
     */
    public void updateFrequency(int stepCount) {
        frequency = snapToTuningRange(frequency, stepCount);
        setChanged();
        updateBlock();
    }

    /**
     * {@code frequency} moved {@code stepCount} steps, inside what the radio tunes: past its band on ham radios
     * ({@link com.arrl.radiocraft.common.radio.BandEdges}), only the band where {@link #tunesOutOfBand()} is false.
     */
    protected float snapToTuningRange(float frequency, int stepCount) {
        if(band == null)
            return frequency;
        if(!tunesOutOfBand())
            return snapToStep(band, frequency, getFrequencyStep(), stepCount);
        return com.arrl.radiocraft.common.radio.BandEdges.snapToTuningRange(band, frequency, getFrequencyStep(), stepCount);
    }

    /**
     * True if the dial goes past the band edges (a ham rig's general coverage receive): HF transceivers, VHF radios and
     * the receivers. Repeaters stay in their band.
     */
    public boolean tunesOutOfBand() {
        return true;
    }

    /** True if this radio's transmitter is limited to its band (unless it has a MARS mod); false for repeaters and receivers. */
    public boolean limitsTransmitToBand() {
        return hasTransmitter();
    }

    /** True if a MARS mod is fitted. */
    public boolean hasMarsMod() {
        return marsMod;
    }

    /** Fits or removes the MARS mod (crafting puts it on the item; placing the item sets it here). */
    public void setMarsMod(boolean value) {
        if(marsMod != value) {
            marsMod = value;
            setChanged();
            updateBlock();
        }
    }

    /** True if the radio transmits outside its band (its transmissions there splatter). */
    public boolean isOutOfBand() {
        return band != null && !band.contains(getTransmitFrequency());
    }

    /**
     * True while the transmitter refuses to key: tuned out of band without a MARS mod. The display shows OUT OF BAND.
     * Works client side too (the band config is synced, and so is the MARS flag).
     */
    public boolean isTransmitInhibited() {
        return limitsTransmitToBand() && band != null && !mayTransmitOn(getTransmitFrequency());
    }

    /**
     * True if the transmitter may key on {@code hz}: in its band, or anywhere it tunes with a MARS mod
     * ({@link com.arrl.radiocraft.common.radio.BandEdges#canTransmit}). A radio (another mod's, too) whose allowed
     * transmit range is something else overrides this; {@link #isTransmitInhibited()} follows it. Client safe.
     */
    @Override
    public boolean mayTransmitOn(double hz) {
        return com.arrl.radiocraft.common.radio.BandEdges.canTransmit(band, hz, marsMod);
    }

    /** Hz the frequency dial moves per click: the HF step, or the VHF channel step on VHF radios. */
    public int getFrequencyStep() {
        return RadiocraftServerConfig.HF_FREQUENCY_STEP.get();
    }

    /**
     * {@code frequency} moved {@code stepCount} steps and put back on the band's step grid, inside the band. Counted in
     * whole steps from the bottom of the band, because a float cannot hold every step exactly at 2m (144.001 MHz is
     * stored as 144.001008): adding steps to the float and checking it against the grid would never land on it.
     */
    public static float snapToStep(Band band, float frequency, int step, int stepCount) {
        long maxSteps = (long)((band.maxFrequency() - band.minFrequency()) / step); // Whole steps only, so the top never takes a half step.
        long steps = Math.round(((double)frequency - band.minFrequency()) / step) + stepCount;
        steps = Math.max(0L, Math.min(maxSteps, steps));
        return (float)(band.minFrequency() + (double)steps * step);
    }

    @Override
    public void acceptVoicePacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, UUID sourcePlayer) {
        float gain = micGain;
        if(gain != 1.0F) { // The mic gain dial.
            short[] scaled = new short[rawAudio.length];
            for(int i = 0; i < rawAudio.length; i++)
                scaled[i] = (short)Mth.clamp(Math.round(rawAudio[i] * gain), Short.MIN_VALUE, Short.MAX_VALUE);
            rawAudio = scaled;
        }
        transmitVoice(level, rawAudio, (float)(getTransmitFrequency() + getDriftHz()), sourcePlayer, getTransmitTone());
    }

    /**
     * Puts one voice frame on the air on {@code txHz}: out of the antenna, or out of the bare connector (a few blocks)
     * when nothing is on the coax. More than one antenna overdraws the radio instead.
     */
    protected void transmitVoice(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, float txHz, UUID sourcePlayer, float ctcssTone) {
        if(isBlown() || isTransmitInhibited())
            return;
        if(!(IBENetworks.getObject(this.level, worldPosition) instanceof RadioNetworkObject networkObject))
            return;
        if(this.level != null && hasSpeech(rawAudio))
            lastSpeechGameTime = this.level.getGameTime(); // SSB only puts power out while there is something to send.
        if(this.level != null) { // How hard the voice drives it, for meters on the air (RIGCTL's S-meter, 2026-11-09).
            int peak = 0;
            for(short sample : rawAudio)
                peak = Math.max(peak, Math.abs((int)sample));
            txPeak = peak / 32768.0D;
            txPeakGameTime = this.level.getGameTime();
        }
        RadioModeration.voice(level, sourcePlayer, rawAudio, txHz); // Operators listening in (/radio moderateall).
        List<AntennaNetworkObject> antennas = networkObject.getAntennas();
        // Every packet made below carries this radio's mode (analog, or a commercial channel's digital and key).
        com.arrl.radiocraft.common.radio.voice.DigitalVoice.Mode was = com.arrl.radiocraft.common.radio.voice.DigitalVoice.push(transmitMode());
        com.arrl.radiocraft.common.radio.voice.Modulation wasModulation = com.arrl.radiocraft.common.radio.voice.Modulation.push(getModulation()); // And SSB, AM or FM.
        Boolean wasSideband = com.arrl.radiocraft.common.radio.voice.Modulation.pushLowerSideband(isLowerSideband()); // And on which sideband.
        try {
            if(antennas.size() == 1) {
                AntennaNetworkObject antenna = antennas.get(0);
                ModerationHooks.voice(antenna.getAntenna(), this, this.level, sourcePlayer, rawAudio, txHz, band, ctcssTone); // The moderation log (logged once, here at the source).
                antenna.transmitAudioPacket(level, rawAudio, band, txHz, sourcePlayer, ctcssTone, transmitPower());
            }
            else if(antennas.size() > 1)
                overdraw();
            else {
                BareConnectorAntenna bare = bareConnector;
                if(bare != null) {
                    ModerationHooks.voice(bare, this, this.level, sourcePlayer, rawAudio, txHz, band, ctcssTone);
                    bare.transmitAudioPacket(level, rawAudio, band, txHz, sourcePlayer, ctcssTone);
                }
            }
        }
        finally {
            com.arrl.radiocraft.common.radio.voice.DigitalVoice.pop(was);
            com.arrl.radiocraft.common.radio.voice.Modulation.pop(wasModulation);
            com.arrl.radiocraft.common.radio.voice.Modulation.popLowerSideband(wasSideband);
        }
    }

    /** How strong this transmitter is against a standard radio's (1): the commercial repeater runs hotter. */
    protected float transmitPower() {
        return 1.0F;
    }

    /** Where the radio transmits: its frequency, or with a repeater offset on VHF radios. */
    public float getTransmitFrequency() {
        return frequency;
    }

    /** CTCSS tone the radio sends under its voice, 0 for none. */
    public float getTransmitTone() {
        return 0.0F;
    }

    /** CTCSS tone the receiver needs to open its squelch, 0 for none (it opens for anything). */
    public float getToneSquelch() {
        return 0.0F;
    }

    @Override
    public boolean canTransmitVoice() {
        if(getNetworkObject(level, worldPosition) instanceof RadioNetworkObject networkObject)
            return ssbEnabled && networkObject.isPowered && isPTTDown && !isTransmitInhibited();
        return false;
    }

    @Override
    public Vec3 getPos() {
        return Sublevels.worldCenter(level, this.micPos.get()); // Players near a radio on a Sable ship are near the ship.
    }

    /** The frequency the antenna's match is measured at: where the radio transmits from its dial (a repeater: its output). */
    public float getSwrFrequency() {
        return frequency;
    }

    /** True while an automatic tuner on the coax has given up on the antenna (synced, for the screen's TUNE FAIL). */
    /** True while the antenna coax is shared with another radio or DF module (or the radio has two antennas): it hears nothing. */
    public boolean isCoaxShared() {
        return coaxShared;
    }

    public boolean isTuneFailed() {
        return tuneFail;
    }

    /** SWR the server last measured on the connected antenna, 0 when off or without an antenna. */
    public double getAntennaSWR() {
        return antennaSWR;
    }

    public double getStaticVolume() {
        if(antennaSWR <= 0.01D)
            return 0.0D;
        else {
            return SWRHelper.getEfficiencyMultiplier(antennaSWR) * (onDummyLoad ? DUMMY_LOAD_STATIC : 1.0D);
        }
    }

    public boolean shouldPlayStatic() {
        return getSSBEnabled();
    }

    /**
     * True while the receiver side of the radio is muted because it is transmitting: a rig cannot hear while PTT is
     * down. Clientside this follows the synced PTT flag, so every player near the radio hears it go quiet.
     */
    public boolean isReceiverMuted() {
        return isPTTDown;
    }

    // -------------------- BE & SYNC IMPLEMENTATION --------------------

    /**
     * Writes the radio's state. Used for both the world save ({@link #saveAdditional}) and the client sync tag
     * ({@link #getUpdateTag}). Subclasses add their own fields by overriding this and calling super.
     */
    protected void setupSaveTag(CompoundTag nbt) {
        nbt.putBoolean("ssbEnabled", ssbEnabled);
        nbt.putBoolean("marsMod", marsMod);
        if(band != null)
            nbt.putString("band", band.name());
        nbt.putFloat("frequency", frequency);
        nbt.putFloat("speakerGain", speakerGain);
        nbt.putFloat("micGain", micGain);
        nbt.putFloat("squelch", squelch);
                nbt.putInt("agc", agc);
        nbt.putFloat("quickHz", quickHz);
        nbt.putFloat("quickReturnHz", quickReturnHz);
        nbt.putDouble("meterNoise", meterNoise);
        nbt.putBoolean("squelchOpen", syncedSquelchOpen);
        nbt.putBoolean("powerOut", syncedPowerOut);
        nbt.putDouble("antennaSWR", antennaSWR);
        nbt.putBoolean("dummyLoad", onDummyLoad);
        nbt.putBoolean("tuneFail", tuneFail);
        nbt.putBoolean("coaxShared", coaxShared);
        if(micHolder != null && micId != null) {
            nbt.putUUID("micHolder", micHolder);
            nbt.putUUID("micId", micId);
        }
        nbt.putBoolean("wasPowered", wasPowered);
        nbt.putBoolean("pttDown", isPTTDown);
        nbt.putBoolean("sendingData", syncedSendingData);
        nbt.putDouble("signal", syncedSignal);
        nbt.putDouble("ground", groundQuality);
        List<BEVoiceReceiver.WiredSpeaker> wired = voiceReceiver.getWiredSpeakers();
        nbt.putLongArray("wiredSpeakers", wired.stream().mapToLong(speaker -> speaker.pos().asLong()).toArray());
        nbt.putIntArray("wiredSpeakerKinds", wired.stream().mapToInt(speaker -> speaker.kind().ordinal()).toArray());
        List<BEVoiceReceiver.RemoteSpeaker> remote = voiceReceiver.getRemoteSpeakers();
        nbt.putLongArray("remoteSpeakers", remote.stream().mapToLong(speaker -> speaker.speaker().pos().asLong()).toArray());
        nbt.putIntArray("remoteSpeakerKinds", remote.stream().mapToInt(speaker -> speaker.speaker().kind().ordinal()).toArray());
    }

    /**
     * Reads the state written by {@link #setupSaveTag}. Used for both world load and client sync. Missing keys keep
     * their current values, so a tag from an older save or a partial update is safe.
     */
    protected void readSaveTag(CompoundTag nbt) {
        ssbEnabled = nbt.getBoolean("ssbEnabled");
        if(nbt.contains("marsMod"))
            marsMod = nbt.getBoolean("marsMod");
        if(nbt.contains("band")) {
            Band saved = Band.getBand(nbt.getString("band"));
            if(saved != null)
                band = saved;
        }
        if(nbt.contains("frequency"))
            frequency = nbt.getFloat("frequency");
        speakerGain = nbt.contains("speakerGain") ? nbt.getFloat("speakerGain") : 1.0F;
        micGain = nbt.contains("micGain") ? nbt.getFloat("micGain") : 1.0F;
        squelch = nbt.contains("squelch") ? nbt.getFloat("squelch") : 0.0F;
                agc = nbt.contains("agc") ? nbt.getInt("agc") : AGC_SLOW;
        quickHz = nbt.getFloat("quickHz");
        quickReturnHz = nbt.getFloat("quickReturnHz");
        meterNoise = nbt.getDouble("meterNoise");
        syncedSquelchOpen = !nbt.contains("squelchOpen") || nbt.getBoolean("squelchOpen");
        syncedPowerOut = nbt.getBoolean("powerOut");
        antennaSWR = nbt.getDouble("antennaSWR");
        onDummyLoad = nbt.getBoolean("dummyLoad");
        tuneFail = nbt.getBoolean("tuneFail");
        coaxShared = nbt.getBoolean("coaxShared");
        micHolder = nbt.hasUUID("micHolder") ? nbt.getUUID("micHolder") : null;
        micId = nbt.hasUUID("micId") ? nbt.getUUID("micId") : null;
        wasPowered = nbt.getBoolean("wasPowered");
        isPTTDown = nbt.getBoolean("pttDown");
        syncedSendingData = nbt.getBoolean("sendingData");
        syncedSignal = nbt.getDouble("signal");
        groundQuality = nbt.getDouble("ground");
        if(nbt.contains("wiredSpeakers")) {
            long[] positions = nbt.getLongArray("wiredSpeakers");
            int[] kinds = nbt.getIntArray("wiredSpeakerKinds");
            List<BEVoiceReceiver.WiredSpeaker> wired = new java.util.ArrayList<>();
            for(int i = 0; i < positions.length && i < kinds.length; i++)
                wired.add(new BEVoiceReceiver.WiredSpeaker(BlockPos.of(positions[i]),
                        com.arrl.radiocraft.common.radio.voice.SpeakerKind.values()[Math.max(0, Math.min(kinds[i], com.arrl.radiocraft.common.radio.voice.SpeakerKind.values().length - 1))]));
            voiceReceiver.setWiredSpeakers(wired);
        }
        if(nbt.contains("remoteSpeakers")) {
            long[] positions = nbt.getLongArray("remoteSpeakers");
            int[] kinds = nbt.getIntArray("remoteSpeakerKinds");
            List<BEVoiceReceiver.WiredSpeaker> remote = new java.util.ArrayList<>();
            for(int i = 0; i < positions.length && i < kinds.length; i++)
                remote.add(new BEVoiceReceiver.WiredSpeaker(BlockPos.of(positions[i]),
                        com.arrl.radiocraft.common.radio.voice.SpeakerKind.values()[Math.max(0, Math.min(kinds[i], com.arrl.radiocraft.common.radio.voice.SpeakerKind.values().length - 1))]));
            clientRemoteSpeakers = List.copyOf(remote);
        }

        if(band != null) { // Reset the frequency if the saved one is outside what the radio tunes, and put it back on a step.
            boolean tunable = tunesOutOfBand() ? com.arrl.radiocraft.common.radio.BandEdges.canTune(band, frequency)
                    : frequency <= band.maxFrequency() && frequency >= band.minFrequency();
            if(!tunable)
                frequency = band.minFrequency();
            else
                frequency = snapToTuningRange(frequency, 0);
        }
    }

    /**
     * Override this method if you need other sound instances-- for example {@link RadioMorseSoundInstance}. Only called
     * clientside.
     */
    protected void setupSoundInstances() {}

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(nbt, registries);
        setupSaveTag(nbt);
    }

    /** A MARS-modded radio item placed down keeps the mod (the item's mars_mod component). */
    @Override
    protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        marsMod = input.get(RadiocraftDataComponent.MARS_MOD.value()) != null;
    }

    /** Broken again, it drops with the mod (the loot table copies the component). */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.@NotNull Builder components) {
        super.collectImplicitComponents(components);
        if(marsMod)
            components.set(RadiocraftDataComponent.MARS_MOD.value(), net.minecraft.util.Unit.INSTANCE);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(nbt, registries);
        readSaveTag(nbt);
    }

    /** Sent to clients on chunk load and whenever {@link #updateBlock} fires; read back through {@link #loadAdditional}. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if(level.isClientSide())
            setupSoundInstances();
        else {
            // If on server, register self to listen for voice packets & notify client of loaded data.
            VoiceTransmitters.addListener(level, this);
            getNetworkObject(level, worldPosition); // This forces the network object to get initialised.
            isPTTDown = false; // Nobody has the GUI open on a freshly loaded radio, whatever a crash mid-transmission saved.
            updateIsReceiving(); // Re-arm voice receive for a radio that was saved switched on in SSB.
            updateBlock();
        }
    }

    @Override
    public void setRemoved() {
        if(!level.isClientSide()) {
            VoiceTransmitters.removeListener(level, this); // Stop listening when removed.
            dropBareConnector();
            RedstoneInterference.unregister(level, worldPosition);
            zoneAntenna = null;
            if(level instanceof ServerLevel serverLevel)
                stopTransmitInterference(serverLevel);
            voiceReceiver.close(); // Its Opus encoders; it makes new ones if it plays again.
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if(!level.isClientSide()) {
            VoiceTransmitters.removeListener(level, this); // Stop listening when chunk unloads.
            dropBareConnector();
            RedstoneInterference.unregister(level, worldPosition);
            zoneAntenna = null;
            if(level instanceof ServerLevel serverLevel)
                stopTransmitInterference(serverLevel);
            voiceReceiver.close(); // Its Opus encoders; it makes new ones if it plays again.
        }
        super.onChunkUnloaded();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Menu data slot: the power switch, 1 when the player has switched the radio on. */
    public static final int DATA_SWITCH = 0;
    /** Menu data slot: what the front panel shows, a {@code RadioNetworkObject.PHASE_*} value. */
    public static final int DATA_DISPLAY_PHASE = 1;
    public static final int DATA_COUNT = 2;

    public ContainerData getDataSlots() {
        if(level.isClientSide)
            return new SimpleContainerData(DATA_COUNT);

        RadioNetworkObject networkObject = (RadioNetworkObject)IBENetworks.getObject(level, worldPosition);
        return networkObject == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override
            public int get(int index) {
                return switch(index) {
                    case DATA_SWITCH -> networkObject.wantsOn ? 1 : 0;
                    case DATA_DISPLAY_PHASE -> networkObject.getDisplayPhase();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {} // This doesn't need a setter-- use packets instead. NetworkObject isn't on client.

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    public boolean wasPowered() {
        return wasPowered;
    }

    protected void updateBlock() {
        if(level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

}
