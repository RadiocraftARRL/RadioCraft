package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.PowerNetworkObject;
import com.arrl.radiocraft.common.be_networks.IPassThroughNetworkObject;
import com.arrl.radiocraft.common.blocks.power.PowerSwitchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A power switch on the power wire (2026-11-09), On/Off or Left/Right. Like the wire junction it is one node for the
 * wire walk, but it passes only between the terminals its lever connects ({@link #passesSide}, from
 * {@link PowerSwitchBlock#passes}); wire on any other side ends at it, so that side is a circuit of its own. Coax does
 * not run through it. It stores, draws and gives nothing.
 */
public class PowerSwitchNetworkObject extends PowerNetworkObject implements IPassThroughNetworkObject {

	public static final ResourceLocation TYPE = Radiocraft.id("power_switch");

	public PowerSwitchNetworkObject(Level level, BlockPos pos) {
		super(level, pos, 0, 0, 0);
	}

	/** The power wire only. */
	@Override
	public boolean passesThrough(boolean power) {
		return power;
	}

	@Override
	public boolean passesSide(boolean power, Direction side) {
		BlockState state = level.getBlockState(pos);
		return power && state.getBlock() instanceof PowerSwitchBlock block && block.passes(state, side);
	}

	@Override
	public boolean isIndirectConsumer() {
		return false;
	}

	@Override
	public boolean isDirectConsumer() {
		return false;
	}

	@Override
	public ResourceLocation getType() {
		return TYPE;
	}
}
