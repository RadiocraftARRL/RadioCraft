package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The radio computer's monitor (1.12 step 5). Holds nothing: it is a block entity only so its renderer can draw the
 * screen last left on it ({@code ComputerMonitorRenderer}).
 */
public class ComputerMonitorBlockEntity extends BlockEntity {

	public ComputerMonitorBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.COMPUTER_MONITOR.get(), pos, state);
	}
}
