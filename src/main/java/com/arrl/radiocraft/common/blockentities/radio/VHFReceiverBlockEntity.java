package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.VHFReceiverNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.PortableBatteries;
import com.arrl.radiocraft.common.items.SmallAlkalineBatteryItem;
import com.arrl.radiocraft.common.menus.VHFReceiverMenu;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.ReceiverWhipAntenna;
import com.arrl.radiocraft.common.radio.wxalert.ReceiverSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The VHF receiver: since 2026-11-09 an eight-channel crystal-style scanner after a 1970s "scan FM monitor", 144 to 174
 * MHz FM (2 m ham, marine and commercial VHF). It has no dial and no display:
 * <ul>
 * <li><b>Eight channels</b>, each with a slide switch: up on, down off. The ones switched on are <b>scanned</b>
 * ({@link #tickScan}): {@link #SCAN_DWELL_TICKS} on each, stopping on one whose signal opens the squelch and moving on
 * {@link #SCAN_HANG_TICKS} after it goes quiet. One switched on: it sits there. None: silence. The channel it is on has
 * its numeral lit. The <b>MAN/AUTO</b> key holds the scan where it is (MAN) or lets it run (AUTO).</li>
 * <li><b>SET</b>: with the SET key in and exactly one channel switched on, the SET lamp lights and the TUNE knob moves
 * that channel's frequency, {@link #STEP_HZ} a click, inside {@link #MIN_HZ} to {@link #MAX_HZ}. Otherwise TUNE does
 * nothing.</li>
 * <li><b>VOLUME</b> with OFF (the power switch) and <b>SQUELCH</b>.</li>
 * <li><b>A small cell.</b> Shift + right-click with a Small NiCd or Small Alkaline Battery puts one in (and takes the
 * old one out); shift + right-click with an empty hand takes it out. With nothing on its power wire it runs from the
 * cell ({@link VHFReceiverNetworkObject}): a full one lasts about half as long as in the weather alert receiver while
 * it only listens ({@link #CELL_DAYS}), and {@link #ACTIVE_DRAW_FACTOR} times faster while it plays a signal. Wired
 * power never charges the cell, as the weather alert receiver never charges its own. A flat alkaline comes out dead.</li>
 * </ul>
 * Saves from before the scanner (a dial and six memories) load: see {@link #readOldMemories}.
 */
public class VHFReceiverBlockEntity extends VHFRadioBlockEntity {

    /** Channels, one for each numeral and slide switch. */
    public static final int CHANNELS = 8;
    private static final int ALL_CHANNELS = (1 << CHANNELS) - 1;
    /** What it tunes: the bottom of 2 m to the top of commercial VHF (marine is inside it). */
    public static final float MIN_HZ = 144_000_000.0F, MAX_HZ = 174_000_000.0F;
    /**
     * TUNE's step, and the grid every channel sits on: 2.5 kHz reaches the 5 kHz ham steps, the 25 kHz marine channels
     * and the 12.5 kHz commercial ones. Shift + turn moves {@link #COARSE_STEP_HZ}, control + turn {@link #FAST_STEP_HZ}.
     */
    public static final int STEP_HZ = 2_500, COARSE_STEP_HZ = 25_000, FAST_STEP_HZ = 1_000_000;
    /** The crystals it comes with: 2 m simplex calling, two 2 m repeater outputs, marine 16 and 9, fire and police mutual aid, NOAA weather. */
    public static final float[] FACTORY_HZ = { 146_520_000.0F, 146_940_000.0F, 147_000_000.0F, 156_800_000.0F, 156_450_000.0F,
            154_280_000.0F, 155_475_000.0F, 162_550_000.0F };
    /** How long the scan listens on each channel for a signal before it moves on, ticks. */
    public static final int SCAN_DWELL_TICKS = 4; // Twice the first speed (maintainer, 2026-11-09).
    /** How long the scan stays on a channel after its signal goes quiet, ticks (a reply usually comes within it). */
    public static final int SCAN_HANG_TICKS = 40;
    /** A signal heard this recently still counts as coming in (voice frames are 20 ms apart), ticks. */
    public static final int HEARD_HOLD_TICKS = 6;
    /** Game days a full cell runs it while it only listens: half the weather alert receiver's. */
    public static final double CELL_DAYS = ReceiverSignals.BATTERY_DAYS / 2.0D;
    /** How many times its idle draw it takes from the cell while it plays a signal (the speaker amplifier). */
    public static final double ACTIVE_DRAW_FACTOR = 4.0D;

    /** Each channel's frequency in Hz, on the {@link #STEP_HZ} grid. */
    private final float[] channels = FACTORY_HZ.clone();
    /** The channel switches that are up, a bit each. A new receiver has channel 1 on. */
    private int channelsOn = 1;
    private boolean setMode = false;
    private boolean scanHold = false;
    /** The channel the scan is on, -1 before it first goes to one. Synced. */
    private int scanChannel = -1;
    /** Stopped on a signal (or waiting out the hang after it). Synced. */
    private boolean scanStopped = false;
    /** Memories past the eighth from an old save (float bits), kept so nothing is lost. */
    private int[] extraMemories = new int[0];
    private ItemStack cell = ItemStack.EMPTY;

    // Server only, not saved.
    @Nullable
    private ReceiverWhipAntenna whip = null; // Its own whip, on the air while it runs with nothing on its coax.
    private long dwellStart = 0L; // When the scan last moved: only a signal after it counts.
    private long lastHeard = Long.MIN_VALUE / 2;
    private int syncedScanChannel = -2;
    private double cellDebt = 0.0D; // Fractions of FE owed to the cell's drain.
    private int syncedCellPercent = -1;

    public VHFReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(RadiocraftBlockEntities.VHF_RECEIVER.get(), pos, state, Band.getBand("2m"));
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.vhf_receiver");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new VHFReceiverMenu(id, this);
    }

    @Override
    public boolean canTransmitVoice() {
        return false;
    }

    @Override
    public boolean hasTransmitter() {
        return false;
    }

    @Override
    public BENetworkObject createNetworkObject() {
        return new VHFReceiverNetworkObject(level, worldPosition, CommonConfig.VHF_RECEIVER_TICK.get(), CommonConfig.VHF_RECEIVER_TICK.get());
    }

	/** A receiver has no microphone. */
	@Override
	public boolean canLendHandMic() {
		return false;
	}

    /** With every channel switched off it hears nothing. */
    @Override
    protected boolean isListening() {
        return channelsOn != 0 && super.isListening();
    }

    // -------------------- CELL --------------------

    public ItemStack getCell() {
        return cell;
    }

    public boolean hasCell() {
        return !cell.isEmpty();
    }

    @Nullable
    private IEnergyStorage cellStorage() {
        return cell.isEmpty() || !PortableBatteries.isCell(cell) ? null : cell.getCapability(Capabilities.EnergyStorage.ITEM);
    }

    /** FE left in the cell (0 with none). */
    public int getCellEnergy() {
        IEnergyStorage storage = cellStorage();
        return storage == null ? 0 : storage.getEnergyStored();
    }

    public static int cellCapacity() {
        return CommonConfig.SMALL_BATTERY_CAPACITY.get();
    }

    /** The cell's charge, 0 to 100. */
    public int getCellPercent() {
        return (int)Math.round(100.0D * getCellEnergy() / Math.max(1, cellCapacity()));
    }

    /** FE a tick it takes from a cell while it only listens. */
    public static double idleDrainPerTick() {
        return cellCapacity() / (CELL_DAYS * ReceiverSignals.TICKS_PER_DAY);
    }

    /**
     * Puts {@code stack} (one Small NiCd or Small Alkaline Battery, or empty) in and gives back the cell that was there:
     * a NiCd as it is, an alkaline with what is left in it, or a Dead Alkaline Battery once it is flat
     * ({@link PortableBatteries#swap} does the same for the handhelds). Server side.
     */
    public ItemStack swapCell(ItemStack stack) {
        ItemStack old = cell;
        int oldEnergy = getCellEnergy();
        cell = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        cellDebt = 0.0D;
        setChanged();
        syncCell(true);
        if(old.getItem() instanceof SmallAlkalineBatteryItem && oldEnergy <= 0)
            return new ItemStack(RadiocraftItems.DEAD_ALKALINE_BATTERY.get());
        return old;
    }

    /** True while it is switched on and running from its cell (server side). */
    public boolean isOnCell() {
        return level != null && !level.isClientSide && getNetworkObject(level, worldPosition) instanceof VHFReceiverNetworkObject radio && radio.isOnCell();
    }

    /** True while a signal is coming in and playing (server side). */
    public boolean isHearing(long now) {
        long heard = getLastSignalGameTime();
        return heard >= 0 && now - heard <= SIGNAL_SYNC_HOLD_TICKS && squelchOpenNow();
    }

    private void tickCell(ServerLevel serverLevel) {
        if(isOnCell()) {
            IEnergyStorage storage = cellStorage();
            if(storage != null) {
                cellDebt += idleDrainPerTick() * (isHearing(serverLevel.getGameTime()) ? ACTIVE_DRAW_FACTOR : 1.0D);
                int take = (int)cellDebt;
                if(take > 0) {
                    cellDebt -= take;
                    storage.extractEnergy(take, false);
                    setChanged();
                }
            }
        }
        syncCell(false);
    }

    /** Tells the client when the cell's percentage moves (the VOLUME knob's tooltip shows it). */
    private void syncCell(boolean force) {
        int percent = hasCell() ? getCellPercent() : -1;
        if(force || percent != syncedCellPercent) {
            syncedCellPercent = percent;
            updateBlock();
        }
    }

    // -------------------- CHANNELS AND SCAN --------------------

    /** Puts {@code hz} on the {@link #STEP_HZ} grid inside {@link #MIN_HZ} to {@link #MAX_HZ}. */
    public static float snapHz(double hz) {
        if(!Double.isFinite(hz))
            return MIN_HZ;
        long steps = Math.round((hz - MIN_HZ) / STEP_HZ);
        steps = Math.max(0L, Math.min((long)((MAX_HZ - MIN_HZ) / STEP_HZ), steps));
        return (float)(MIN_HZ + steps * (double)STEP_HZ);
    }

    /** The frequency of {@code channel} (0 to 7), in Hz. */
    public float getChannelHz(int channel) {
        return channel >= 0 && channel < CHANNELS ? channels[channel] : 0.0F;
    }

    public boolean isChannelOn(int channel) {
        return channel >= 0 && channel < CHANNELS && (channelsOn & (1 << channel)) != 0;
    }

    /** How many channel switches are up: 0 silence, 1 that channel, 2 or more a scan. */
    public int channelsOnCount() {
        return Integer.bitCount(channelsOn);
    }

    /** The only channel switched on, or -1 if none or more than one is. */
    public int onlyChannel() {
        return channelsOnCount() == 1 ? Integer.numberOfTrailingZeros(channelsOn) : -1;
    }

    public boolean isScanning() {
        return channelsOnCount() >= 2;
    }

    /** The channel the scan is on (its numeral is lit while the receiver runs), -1 before it first goes to one. Synced. */
    public int getScanChannel() {
        return scanChannel;
    }

    /** True while a scan is stopped on a signal, or waiting out {@link #SCAN_HANG_TICKS} after one. Synced. */
    public boolean isScanStopped() {
        return scanStopped;
    }

    /** The SET key is in. */
    public boolean isSetMode() {
        return setMode;
    }

    /** MAN: the scan holds on the channel it is on (the key out); AUTO, the key in, scans. */
    public boolean isScanHold() {
        return scanHold;
    }

    /** True if the SET lamp may light and TUNE retunes: SET in and exactly one channel switched on (the receiver must also be running). */
    public boolean isSetLit() {
        return setMode && channelsOnCount() == 1;
    }

    /** A channel switch flipped up ({@code on}) or down. Works with the power off, as a switch does. */
    public void setChannelOn(int channel, boolean on) {
        if(channel < 0 || channel >= CHANNELS || isChannelOn(channel) == on)
            return;
        channelsOn = on ? channelsOn | (1 << channel) : channelsOn & ~(1 << channel);
        changed();
    }

    public void setSetMode(boolean value) {
        if(setMode != value) {
            setMode = value;
            changed();
        }
    }

    public void setScanHold(boolean value) {
        if(scanHold != value) {
            scanHold = value;
            changed();
        }
    }

    /** Sets {@code channel}'s frequency, put on the grid and inside the range ({@link #snapHz}). The radio follows if it is on that channel. */
    public void setChannelHz(int channel, float hz) {
        if(channel < 0 || channel >= CHANNELS)
            return;
        channels[channel] = snapHz(hz);
        if(level != null && !level.isClientSide && channel == scanChannel && isChannelOn(channel))
            tuneTo(channels[channel]);
        changed();
    }

    /**
     * TUNE turned {@code deltaHz}: moves the one channel that is switched on, while SET is in ({@link #isSetLit}). False,
     * and nothing changes, otherwise. The screen calls it too, to show the new frequency before the server answers.
     */
    public boolean tuneSetChannel(int deltaHz) {
        int channel = onlyChannel();
        if(!setMode || channel < 0)
            return false;
        setChannelHz(channel, (float)(channels[channel] + (double)deltaHz));
        return true;
    }

    /** Saved and sent to the client (server side; the client only keeps its own copy). */
    private void changed() {
        if(level != null && !level.isClientSide) {
            setChanged();
            updateBlock();
        }
    }

    /** The next switched-on channel after {@code from}, round to the first; -1 if none is on. */
    private int nextOn(int from) {
        for(int k = 1; k <= CHANNELS; k++) {
            int channel = Math.floorMod(from + k, CHANNELS);
            if(isChannelOn(channel))
                return channel;
        }
        return -1;
    }

    /**
     * True if a signal came in since the scan last moved, recently enough to still count, and strong enough to open the
     * squelch: a carrier (voice from a radio in the world, CW, data tones) or a frame played straight in (playBroadcast).
     */
    private boolean heardSince(long since, long now) {
        long carrierAt = getCarrierGameTime(), signalAt = getLastSignalGameTime();
        boolean carrier = carrierAt > since && now - carrierAt <= HEARD_HOLD_TICKS && squelchOpens(getCarrierStrength());
        boolean signal = signalAt > since && now - signalAt <= HEARD_HOLD_TICKS && squelchOpens(getLastSignalStrength());
        return carrier || signal;
    }

    private void stepTo(int channel, long now) {
        scanChannel = channel;
        dwellStart = now;
        lastHeard = Long.MIN_VALUE / 2;
        if(channel >= 0)
            tuneTo(channels[channel]);
    }

    /**
     * The scan, as on a crystal scanner: it steps through the switched-on channels, {@link #SCAN_DWELL_TICKS} on each,
     * stops on one with a signal that opens the squelch, and moves on {@link #SCAN_HANG_TICKS} after it goes quiet. MAN
     * holds it where it is. One channel on: it sits there. Off, or no channel on: it waits where it was.
     */
    private void tickScan(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        int count = channelsOnCount();
        boolean stopped = false;
        if(isPowered() && count > 0) {
            if(!isChannelOn(scanChannel))
                stepTo(nextOn(scanChannel), now);
            else if(heardSince(dwellStart, now))
                lastHeard = now;
            else if(count > 1 && !scanHold && now - lastHeard > SCAN_HANG_TICKS && now - dwellStart >= SCAN_DWELL_TICKS)
                stepTo(nextOn(scanChannel), now);
            if(scanChannel >= 0 && frequency != channels[scanChannel])
                tuneTo(channels[scanChannel]); // Something else moved the frequency (a rig control app): it has no dial, only its channels.
            stopped = count > 1 && now - lastHeard <= SCAN_HANG_TICKS;
        }
        if(stopped != scanStopped || scanChannel != syncedScanChannel) {
            scanStopped = stopped;
            syncedScanChannel = scanChannel;
            setChanged();
            updateBlock();
        }
    }

    @Override
    protected void additionalTick() {
        super.additionalTick();
        if(level instanceof ServerLevel serverLevel) {
            tickWhip();
            tickCell(serverLevel);
            tickScan(serverLevel);
        }
    }

    // -------------------- THE WHIP --------------------

    /**
     * Its own whip stands in for the bare connector (2026-11-09): with nothing on its coax it hears as a VHF handheld
     * held here would ({@link ReceiverWhipAntenna}). An antenna coaxed to it takes over completely (the whip comes off
     * the air), as plugging an antenna into a scanner's jack cuts its whip out: a real VHF antenna is the better one.
     */
    @Override
    protected boolean usesBareConnector() {
        return false;
    }

    private void tickWhip() {
        boolean wanted = isPowered() && getNetworkObject(level, worldPosition) instanceof RadioNetworkObject radio && radio.getAntennas().isEmpty();
        if(wanted && whip == null)
            whip = new ReceiverWhipAntenna(this);
        if(whip != null)
            whip.setActive(wanted);
    }

    /** The whip, while it is on the air (it runs with nothing on its coax); null otherwise. Server side. */
    @Nullable
    public ReceiverWhipAntenna getWhip() {
        return whip != null && whip.isActive() ? whip : null;
    }

    private void dropWhip() {
        if(whip != null)
            whip.setActive(false);
        whip = null;
    }

    @Override
    public void setRemoved() {
        if(level != null && !level.isClientSide())
            dropWhip();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if(level != null && !level.isClientSide())
            dropWhip();
        super.onChunkUnloaded();
    }

    /** Its tuning is the channels' grid, inside 144 to 174 MHz: anything that steps or types a frequency lands on it. */
    @Override
    protected float snapToTuningRange(float frequency, int stepCount) {
        return snapHz((double)frequency + (double)stepCount * STEP_HZ);
    }

    @Override
    public int getFrequencyStep() {
        return STEP_HZ;
    }

    /** VOLUME and SQUELCH, as on the real set. */
    @Override
    public boolean hasSquelchKnob() {
        return true;
    }

    // -------------------- SAVE AND SYNC --------------------

    @Override
    protected void setupSaveTag(CompoundTag nbt) {
        super.setupSaveTag(nbt);
        int[] bits = new int[CHANNELS];
        for(int i = 0; i < CHANNELS; i++)
            bits[i] = Float.floatToIntBits(channels[i]);
        nbt.putIntArray("channels", bits);
        nbt.putInt("channelsOn", channelsOn);
        nbt.putBoolean("setMode", setMode);
        nbt.putBoolean("scanHold", scanHold);
        nbt.putInt("scanChannel", scanChannel);
        nbt.putBoolean("scanStopped", scanStopped);
        if(extraMemories.length > 0)
            nbt.putIntArray("extraMemories", extraMemories);
    }

    @Override
    protected void readSaveTag(CompoundTag nbt) {
        super.readSaveTag(nbt);
        if(nbt.contains("channels")) {
            int[] bits = nbt.getIntArray("channels");
            for(int i = 0; i < CHANNELS; i++)
                channels[i] = i < bits.length ? snapHz(Float.intBitsToFloat(bits[i])) : FACTORY_HZ[i];
            channelsOn = nbt.getInt("channelsOn") & ALL_CHANNELS;
            setMode = nbt.getBoolean("setMode");
            scanHold = nbt.getBoolean("scanHold");
            scanChannel = nbt.contains("scanChannel") ? Math.max(-1, Math.min(CHANNELS - 1, nbt.getInt("scanChannel"))) : -1;
            scanStopped = nbt.getBoolean("scanStopped");
            extraMemories = nbt.getIntArray("extraMemories");
        }
        else if(nbt.contains("memories") || nbt.contains("memorySelected"))
            readOldMemories(nbt);
    }

    /**
     * A receiver saved before it became a scanner (2026-11-09: a tuning dial and six memories, pressed in to select or
     * scan). Its first eight memories become the channels (an empty one keeps the factory crystal), and the ones it had
     * selected are switched on. One that listened on its dial instead gets the dial's frequency in its first empty
     * channel, switched on, so it still hears what it heard. Memories past eight are kept as they were
     * ({@code extraMemories}). Mute is gone: only VOLUME's OFF silences it now.
     */
    private void readOldMemories(CompoundTag nbt) {
        int[] bits = nbt.getIntArray("memories");
        int empty = 0;
        for(int i = 0; i < CHANNELS; i++) {
            float hz = i < bits.length ? Float.intBitsToFloat(bits[i]) : 0.0F;
            if(hz > 0.0F && Float.isFinite(hz))
                channels[i] = snapHz(hz);
            else {
                channels[i] = FACTORY_HZ[i];
                empty |= 1 << i;
            }
        }
        channelsOn = nbt.getInt("memorySelected") & ALL_CHANNELS & ~empty;
        if(channelsOn == 0) {
            if(empty != 0 && frequency >= MIN_HZ - STEP_HZ && frequency <= MAX_HZ + STEP_HZ) {
                int slot = Integer.numberOfTrailingZeros(empty);
                channels[slot] = snapHz(frequency);
                channelsOn = 1 << slot;
            }
            else
                channelsOn = 1;
        }
        extraMemories = bits.length > CHANNELS ? Arrays.copyOfRange(bits, CHANNELS, bits.length) : new int[0];
        setMode = false;
        scanHold = false;
        scanChannel = -1;
        scanStopped = false;
    }

    /** Memories an old save had past the eighth, as float bits; kept and saved again, never used. */
    public int[] getExtraMemories() {
        return extraMemories.clone();
    }

    /** The cell needs the registries to save, so it goes here rather than in {@link #setupSaveTag} (also the sync tag). */
    @Override
    protected void saveAdditional(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(nbt, registries);
        if(!cell.isEmpty())
            nbt.put("cell", cell.save(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(nbt, registries);
        cell = nbt.contains("cell") ? ItemStack.parseOptional(registries, nbt.getCompound("cell")) : ItemStack.EMPTY;
    }
}
