package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.common.be_networks.BatteryMeterReading;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryMeterNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The battery meter's block entity. Server side, it takes a reading every tick ({@link BatteryMeterNetworkObject#read})
 * and sends clients what the face shows when that changes: volts to a tenth, percent, and the trend. It also tells the
 * neighbours when the comparator output moves.
 */
public class BatteryMeterBlockEntity extends BlockEntity implements INetworkObjectProvider {

	/** Ticks between updates to clients, at most. */
	private static final int SYNC_TICKS = 10;

	private boolean shownBatteries;
	private int shownDeciVolts;
	private int shownPercent;
	private BatteryMeterReading.Trend shownTrend = BatteryMeterReading.Trend.REST;
	private int comparator;

	public BatteryMeterBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.BATTERY_METER.get(), pos, state);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new BatteryMeterNetworkObject(level, worldPosition);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide())
			getNetworkObject(level, worldPosition); // Makes the object and joins the wires around it.
	}

	public static void tick(Level level, BlockPos pos, BlockState state, BatteryMeterBlockEntity be) {
		if(level.isClientSide || !(IBENetworks.getObject(level, pos) instanceof BatteryMeterNetworkObject meter))
			return;
		BatteryMeterReading reading = meter.read();
		if(reading.comparator() != be.comparator) {
			be.comparator = reading.comparator();
			level.updateNeighbourForOutputSignal(pos, state.getBlock());
		}
		if(level.getGameTime() % SYNC_TICKS != Math.floorMod(pos.asLong(), SYNC_TICKS))
			return;
		boolean batteries = reading.hasBatteries();
		int deciVolts = (int)Math.round(reading.volts() * 10.0D);
		if(batteries != be.shownBatteries || deciVolts != be.shownDeciVolts || reading.percent() != be.shownPercent || reading.trend() != be.shownTrend) {
			be.shownBatteries = batteries;
			be.shownDeciVolts = deciVolts;
			be.shownPercent = reading.percent();
			be.shownTrend = reading.trend();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		}
	}

	/** The comparator output, 0 to 15 (server side). */
	public int getComparator() {
		return comparator;
	}

	/** What the face shows (client side, synced). */
	public boolean isShownBatteries() {
		return shownBatteries;
	}

	public double getShownVolts() {
		return shownDeciVolts / 10.0D;
	}

	public int getShownPercent() {
		return shownPercent;
	}

	public BatteryMeterReading.Trend getShownTrend() {
		return shownTrend;
	}

	@Override
	public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		tag.putBoolean("batteries", shownBatteries);
		tag.putInt("deciVolts", shownDeciVolts);
		tag.putInt("percent", shownPercent);
		tag.putString("trend", shownTrend.name());
		return tag;
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		if(!tag.contains("trend"))
			return;
		shownBatteries = tag.getBoolean("batteries");
		shownDeciVolts = tag.getInt("deciVolts");
		shownPercent = tag.getInt("percent");
		try {
			shownTrend = BatteryMeterReading.Trend.valueOf(tag.getString("trend"));
		}
		catch(IllegalArgumentException e) {
			shownTrend = BatteryMeterReading.Trend.REST;
		}
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
