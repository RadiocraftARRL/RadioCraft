package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.benetworks.BENetwork;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.api.benetworks.PowerBENetwork;
import com.arrl.radiocraft.api.benetworks.PowerNetworkObject;
import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.common.be_networks.WireUtils;
import com.arrl.radiocraft.common.be_networks.network_objects.PowerSwitchNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The block entity of both power switches (2026-11-09): puts the switch on the power wire
 * ({@link PowerSwitchNetworkObject}), remembers the redstone level going into it so a signal throws the lever once, as
 * it rises, and rebuilds the power networks around it when the lever moves.
 */
public class PowerSwitchBlockEntity extends BlockEntity implements INetworkObjectProvider {

	private static final String SIGNAL_TAG = "redstone";
	/** The redstone level going into it when last looked at. */
	private int lastSignal = 0;

	public PowerSwitchBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.POWER_SWITCH.get(), pos, state);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new PowerSwitchNetworkObject(level, worldPosition);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide())
			getNetworkObject(level, worldPosition); // Makes the object and joins the wires around it.
	}

	/** The redstone level going into it when last looked at. */
	public int lastSignal() {
		return lastSignal;
	}

	/** Remembers the redstone level going into it now. */
	public void setLastSignal(int signal) {
		if(lastSignal != signal) {
			lastSignal = signal;
			setChanged();
		}
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt(SIGNAL_TAG, lastSignal);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		lastSignal = tag.getInt(SIGNAL_TAG);
	}

	/**
	 * Rebuilds the power networks the switch at {@code pos} is part of, after its lever moved: everything on them comes
	 * off its power networks and joins again, so the walk (which passes through the switch only between the terminals
	 * the lever connects) sorts out which circuits are joined now. The power counterpart of
	 * {@link AntennaSwitchBlockEntity#rewire}.
	 */
	public static void rewire(ServerLevel level, BlockPos pos) {
		BENetworkObject self = IBENetworks.getObject(level, pos);
		if(self == null)
			return;
		Set<BENetworkObject> members = new LinkedHashSet<>();
		members.add(self);
		for(BENetwork network : self.getNetworks().values())
			if(network instanceof PowerBENetwork)
				members.addAll(network.getNetworkObjects());
		for(BENetworkObject member : members)
			member.clearNetworks(true);
		for(BENetworkObject member : members)
			WireUtils.tryConnect(level, member.getPos(), object -> object instanceof PowerNetworkObject, PowerBENetwork::new, WireUtils.powerWires());
	}
}
