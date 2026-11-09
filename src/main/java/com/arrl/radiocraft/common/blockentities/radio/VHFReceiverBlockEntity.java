package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.VHFReceiverNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.PortableBatteries;
import com.arrl.radiocraft.common.items.SmallAlkalineBatteryItem;
import com.arrl.radiocraft.common.menus.VHFReceiverMenu;
import com.arrl.radiocraft.common.radio.Band;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The 2m FM receiver: a radio that only listens. Besides its tuning dial it has (2026-11-09):
 * <ul>
 * <li><b>Six memory channels.</b> A numbered button with nothing stored in it stores the frequency on the dial and
 * selects it; shift + click stores over it. Each selected channel's button stays pressed in. One selected: the receiver
 * listens there. Two or more: it <b>scans</b> them, {@link #SCAN_DWELL_TICKS} on each, stops on one with a signal and
 * stays {@link #SCAN_HANG_TICKS} after it goes quiet. Turning the tuning dial leaves the channels for the dial (the
 * buttons pop out). The lamp over a channel's button lights while a signal comes in on it.</li>
 * <li><b>A mute button</b> (the speaker icon): muted, it plays nothing, but its lamps and scan still see signals.</li>
 * <li><b>A small cell.</b> Shift + right-click with a Small NiCd or Small Alkaline Battery puts one in (and takes the
 * old one out); shift + right-click with an empty hand takes it out. With nothing on its power wire it runs from the
 * cell ({@link VHFReceiverNetworkObject}): a full one lasts about half as long as in the weather alert receiver while
 * it only listens ({@link #CELL_DAYS}), and {@link #ACTIVE_DRAW_FACTOR} times faster while it plays a signal. Wired
 * power never charges the cell, as the weather alert receiver never charges its own. A flat alkaline comes out dead.</li>
 * </ul>
 */
public class VHFReceiverBlockEntity extends VHFRadioBlockEntity {

    /** Memory channels, one for each numbered button. */
    public static final int MEMORIES = 6;
    /** How long the scan listens on each channel for a signal before it moves on, ticks. */
    public static final int SCAN_DWELL_TICKS = 8;
    /** How long the scan stays on a channel after its signal goes quiet, ticks (a reply usually comes within it). */
    public static final int SCAN_HANG_TICKS = 40;
    /** A signal heard this recently still counts as coming in (voice frames are 20 ms apart), ticks. */
    public static final int HEARD_HOLD_TICKS = 6;
    /** Game days a full cell runs it while it only listens: half the weather alert receiver's. */
    public static final double CELL_DAYS = ReceiverSignals.BATTERY_DAYS / 2.0D;
    /** How many times its idle draw it takes from the cell while it plays a signal (the speaker amplifier). */
    public static final double ACTIVE_DRAW_FACTOR = 4.0D;

    /** Stored frequencies in Hz, 0 for an empty channel. */
    private final float[] memories = new float[MEMORIES];
    /** Selected channels, a bit each: none is the dial, one is that channel, more is a scan. */
    private int selected = 0;
    /** The channel whose lamp is lit (a signal coming in on it), -1 for none. Synced. */
    private int litChannel = -1;
    private boolean muted = false;
    private ItemStack cell = ItemStack.EMPTY;

    // Server only, not saved.
    private int scanIndex = -1; // The selected channel the scan is on.
    private float scanHz = 0.0F; // What the scan last tuned to: anything else means the dial was turned.
    private long dwellStart = 0L;
    private long lastHeard = Long.MIN_VALUE / 2;
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

    /** Muted, it takes nothing in to play; the carrier a scan stops on is still seen (RadioBlockEntity#recordCarrier). */
    @Override
    protected boolean isListening() {
        return !muted && super.isListening();
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

    /** Tells the client when the cell's percentage moves (the screen shows it). */
    private void syncCell(boolean force) {
        int percent = hasCell() ? getCellPercent() : -1;
        if(force || percent != syncedCellPercent) {
            syncedCellPercent = percent;
            updateBlock();
        }
    }

    // -------------------- MEMORIES AND SCAN --------------------

    /** The frequency stored in {@code channel} (0 to 5), 0 if it is empty. */
    public float getMemory(int channel) {
        return channel >= 0 && channel < MEMORIES ? memories[channel] : 0.0F;
    }

    public boolean isSelected(int channel) {
        return channel >= 0 && channel < MEMORIES && (selected & (1 << channel)) != 0;
    }

    /** How many channels are selected: 0 the dial, 1 a channel, 2 or more a scan. */
    public int selectedCount() {
        return Integer.bitCount(selected);
    }

    public boolean isScanning() {
        return selectedCount() >= 2;
    }

    public int getLitChannel() {
        return litChannel;
    }

    public boolean isMuted() {
        return muted;
    }

    /** Client side, so the screen shows a change before the server syncs it back. */
    public void setMutedClient(boolean value) {
        muted = value;
    }

    public void setMuted(boolean value) {
        if(muted != value) {
            muted = value;
            setChanged();
            updateBlock();
        }
    }

    /**
     * A numbered button pressed: an empty channel stores the frequency on the dial and is selected; a stored one is
     * selected or let go. With one channel left selected the receiver tunes to it. Server side.
     */
    public void pressMemory(int channel) {
        if(channel < 0 || channel >= MEMORIES)
            return;
        if(memories[channel] == 0.0F) {
            memories[channel] = frequency;
            selected |= 1 << channel;
        }
        else
            selected ^= 1 << channel;
        afterSelectionChange();
    }

    /** Shift + click on a numbered button: stores the frequency on the dial in it, whatever was there. Server side. */
    public void storeMemory(int channel) {
        if(channel < 0 || channel >= MEMORIES)
            return;
        memories[channel] = frequency;
        afterSelectionChange();
    }

    private void afterSelectionChange() {
        scanIndex = -1;
        scanHz = 0.0F;
        dwellStart = level == null ? 0L : level.getGameTime(); // scanIndex -1: the scan goes to its first channel at once.
        lastHeard = Long.MIN_VALUE / 2;
        if(selectedCount() == 1) {
            int only = Integer.numberOfTrailingZeros(selected);
            tuneTo(memories[only]);
            scanIndex = only;
            scanHz = frequency;
        }
        setChanged();
        updateBlock();
    }

    /** The selected channels holding a frequency, in button order. */
    private List<Integer> scanList() {
        List<Integer> out = new ArrayList<>(MEMORIES);
        for(int i = 0; i < MEMORIES; i++)
            if(isSelected(i) && memories[i] != 0.0F)
                out.add(i);
        return out;
    }

    private void tickScan(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        boolean powered = isPowered();
        long carrierAt = getCarrierGameTime(); // Heard since the scan last tuned: a frame from the channel it just left does not count.
        boolean carrier = carrierAt >= 0 && now - carrierAt <= HEARD_HOLD_TICKS && carrierAt > dwellStart;
        if(selected != 0 && scanHz != 0.0F && frequency != scanHz) { // The dial was turned: back to the dial.
            selected = 0;
            scanIndex = -1;
            scanHz = 0.0F;
            setChanged();
            updateBlock();
        }
        if(powered && selected != 0) {
            List<Integer> channels = scanList();
            if(channels.isEmpty())
                selected = 0;
            else if(carrier)
                lastHeard = now;
            else if(!channels.contains(scanIndex) || (now - lastHeard > SCAN_HANG_TICKS && now - dwellStart >= SCAN_DWELL_TICKS && channels.size() > 1)) {
                int next = channels.get(0);
                for(int channel : channels)
                    if(channel > scanIndex) {
                        next = channel;
                        break;
                    }
                scanIndex = next;
                tuneTo(memories[next]);
                scanHz = frequency;
                dwellStart = now;
            }
        }
        int lit = -1;
        if(powered && carrier) {
            if(selected != 0)
                lit = scanIndex;
            else
                for(int i = 0; i < MEMORIES && lit < 0; i++)
                    if(memories[i] != 0.0F && memories[i] == frequency)
                        lit = i;
        }
        if(lit != litChannel) {
            litChannel = lit;
            updateBlock();
        }
    }

    @Override
    protected void additionalTick() {
        super.additionalTick();
        if(level instanceof ServerLevel serverLevel) {
            tickCell(serverLevel);
            tickScan(serverLevel);
        }
    }

    // -------------------- SAVE AND SYNC --------------------

    @Override
    protected void setupSaveTag(CompoundTag nbt) {
        super.setupSaveTag(nbt);
        int[] bits = new int[MEMORIES];
        for(int i = 0; i < MEMORIES; i++)
            bits[i] = Float.floatToIntBits(memories[i]);
        nbt.putIntArray("memories", bits);
        nbt.putInt("memorySelected", selected);
        nbt.putInt("litChannel", litChannel);
        nbt.putBoolean("muted", muted);
    }

    @Override
    protected void readSaveTag(CompoundTag nbt) {
        super.readSaveTag(nbt);
        int[] bits = nbt.getIntArray("memories");
        for(int i = 0; i < MEMORIES; i++)
            memories[i] = i < bits.length ? Float.intBitsToFloat(bits[i]) : 0.0F;
        selected = nbt.getInt("memorySelected") & ((1 << MEMORIES) - 1);
        litChannel = nbt.contains("litChannel") ? nbt.getInt("litChannel") : -1;
        muted = nbt.getBoolean("muted");
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
