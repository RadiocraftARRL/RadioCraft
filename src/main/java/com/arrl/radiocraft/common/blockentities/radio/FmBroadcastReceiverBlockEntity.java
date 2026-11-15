package com.arrl.radiocraft.common.blockentities.radio;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.VHFReceiverNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.PortableBatteries;
import com.arrl.radiocraft.common.items.SmallAlkalineBatteryItem;
import com.arrl.radiocraft.common.radio.antenna.ReceiverWhipAntenna;
import com.arrl.radiocraft.common.radio.wxalert.ReceiverSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The FM broadcast receiver (2026-11-13): a wood-cased table radio that plays FM broadcast stations. Receive only.
 * It hears through its own wire antenna (a VHF whip, {@link ReceiverWhipAntenna}) until an antenna is coaxed to it,
 * which then takes over. Draws very little: {@link #WIRE_DRAW} FE a tick from a power wire, or, with nothing on the wire,
 * a Small Alkaline (or NiCd) Battery put in with shift + right-click, as the VHF receiver takes one
 * ({@link CellRadio}). A cell lasts a tenth as long as in the weather alert receiver ({@link #CELL_DRAIN_FACTOR}): it
 * plays all the time. Its BASS and TREBLE knobs shape what its speaker plays
 * ({@link com.arrl.radiocraft.common.radio.voice.ToneControl}).
 */
public class FmBroadcastReceiverBlockEntity extends FmBroadcastRadioBlockEntity implements CellRadio {

	public static final int RECEIVE_TICK = 2;
	/** FE a tick it takes from a power wire. */
	public static final int WIRE_DRAW = 1;
	/** How many times faster than the weather alert receiver it uses up a cell. */
	public static final int CELL_DRAIN_FACTOR = 10;
	/** The tone knobs' range: -{@link #TONE_STEPS} to {@link #TONE_STEPS}, 0 flat. */
	public static final int TONE_STEPS = 10;

	@Nullable
	private ReceiverWhipAntenna whip = null;
	private ItemStack cell = ItemStack.EMPTY;
	private double cellDebt = 0.0D;
	private int syncedCellPercent = -1;
	private int bass = 0, treble = 0;

	public FmBroadcastReceiverBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.FM_BROADCAST_RECEIVER.get(), pos, state);
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
	protected boolean usesBareConnector() {
		return false;
	}

	@Override
	protected void additionalTick() {
		super.additionalTick();
		if(level == null || level.isClientSide)
			return;
		boolean wanted = isPowered() && getNetworkObject(level, worldPosition) instanceof RadioNetworkObject radio && radio.getAntennas().isEmpty();
		if(wanted && whip == null)
			whip = new ReceiverWhipAntenna(this);
		if(whip != null)
			whip.setActive(wanted);
		tickCell();
	}

	/** The whip, while it is on the air; null otherwise. Server side. */
	@Nullable
	public ReceiverWhipAntenna getWhip() {
		return whip != null && whip.isActive() ? whip : null;
	}

	@Override
	public void setRemoved() {
		if(whip != null)
			whip.setActive(false);
		whip = null;
		super.setRemoved();
	}

	/** Pays from the wire first, then from its cell. */
	@Override
	public BENetworkObject createNetworkObject() {
		return new VHFReceiverNetworkObject(level, worldPosition, WIRE_DRAW, WIRE_DRAW);
	}

	// -------------------- TONE --------------------

	public int getBass() {
		return bass;
	}

	public int getTreble() {
		return treble;
	}

	/** Sets TREBLE ({@code trebleKnob}) or BASS to {@code steps}, -{@link #TONE_STEPS} to {@link #TONE_STEPS}. */
	public void setTone(boolean trebleKnob, int steps) {
		int clamped = Math.max(-TONE_STEPS, Math.min(TONE_STEPS, steps));
		if(trebleKnob)
			treble = clamped;
		else
			bass = clamped;
		applyTone();
		setChanged();
		updateBlock();
	}

	/** Client: what the screen shows before the server answers. */
	public void setToneClient(boolean trebleKnob, int steps) {
		int clamped = Math.max(-TONE_STEPS, Math.min(TONE_STEPS, steps));
		if(trebleKnob)
			treble = clamped;
		else
			bass = clamped;
	}

	private void applyTone() {
		voiceReceiver.setTone(bass / (double)TONE_STEPS, treble / (double)TONE_STEPS);
	}

	// -------------------- CELL --------------------

	public ItemStack getCell() {
		return cell;
	}

	@Override
	public boolean hasCell() {
		return !cell.isEmpty();
	}

	@Nullable
	private IEnergyStorage cellStorage() {
		return cell.isEmpty() || !PortableBatteries.isCell(cell) ? null : cell.getCapability(Capabilities.EnergyStorage.ITEM);
	}

	@Override
	public int getCellEnergy() {
		IEnergyStorage storage = cellStorage();
		return storage == null ? 0 : storage.getEnergyStored();
	}

	/** The cell's charge, 0 to 100; -1 without one (client: as last synced). */
	public int getCellPercent() {
		if(level != null && level.isClientSide)
			return syncedCellPercent;
		return hasCell() ? (int)Math.round(100.0D * getCellEnergy() / Math.max(1, CommonConfig.SMALL_BATTERY_CAPACITY.get())) : -1;
	}

	/** FE a tick it takes from a cell while it plays: {@link #CELL_DRAIN_FACTOR} times the weather alert receiver's. */
	public static double cellDrainPerTick() {
		return ReceiverSignals.drainPerTick(CommonConfig.SMALL_BATTERY_CAPACITY.get(), ReceiverSignals.BATTERY_DAYS) * CELL_DRAIN_FACTOR;
	}

	/**
	 * Puts {@code stack} (one Small NiCd or Small Alkaline Battery, or empty) in and gives back the cell that was there:
	 * a NiCd as it is, an alkaline with what is left in it, or a Dead Alkaline Battery once it is flat. Server side.
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

	private void tickCell() {
		if(isOnCell()) {
			IEnergyStorage storage = cellStorage();
			if(storage != null) {
				cellDebt += cellDrainPerTick();
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

	private void syncCell(boolean force) {
		if(level == null || level.isClientSide)
			return;
		int percent = getCellPercent();
		if(force || percent != syncedCellPercent) {
			syncedCellPercent = percent;
			updateBlock();
		}
	}

	// -------------------- SAVE AND SYNC --------------------

	@Override
	protected void setupSaveTag(CompoundTag nbt) {
		super.setupSaveTag(nbt);
		nbt.putInt("bass", bass);
		nbt.putInt("treble", treble);
		nbt.putInt("cellPercent", getCellPercent());
	}

	@Override
	protected void readSaveTag(CompoundTag nbt) {
		super.readSaveTag(nbt);
		bass = Math.max(-TONE_STEPS, Math.min(TONE_STEPS, nbt.getInt("bass")));
		treble = Math.max(-TONE_STEPS, Math.min(TONE_STEPS, nbt.getInt("treble")));
		if(nbt.contains("cellPercent"))
			syncedCellPercent = nbt.getInt("cellPercent");
		applyTone();
	}

	/** The cell needs the registries to save, so it goes here rather than in {@link #setupSaveTag}. */
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

	/** Receive only: no hand mic to take off it (2026-11-13). */
	@Override
	public boolean canLendHandMic() {
		return false;
	}
}
