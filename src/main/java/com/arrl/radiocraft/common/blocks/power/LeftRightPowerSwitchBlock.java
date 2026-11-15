package com.arrl.radiocraft.common.blocks.power;

import com.arrl.radiocraft.common.blocks.WallMount;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Power Switch - Left/Right (2026-11-09): an input on the {@link #FACING} side and an output to each side of it, left
 * and right as the player who placed it saw them (the input faces that player). Power passes between the input and
 * the selected output only; the other output is a circuit of its own. Made on the left. On a wall the input is at the
 * bottom and the outputs left and right as you face the wall.
 */
public class LeftRightPowerSwitchBlock extends PowerSwitchBlock {

	/** The lever: true when the right output is selected, false for the left. */
	public static final BooleanProperty RIGHT = BooleanProperty.create("right");

	public LeftRightPowerSwitchBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH).setValue(RIGHT, false));
	}

	/** The left output's side, as seen by someone facing the input: clockwise from it, looking down. */
	public static Direction left(BlockState state) {
		return WallMount.toWorld(state, frameFacing(state).getClockWise());
	}

	/** The right output's side. */
	public static Direction right(BlockState state) {
		return WallMount.toWorld(state, frameFacing(state).getCounterClockWise());
	}

	/** The side of the output the lever selects. */
	public static Direction selected(BlockState state) {
		return state.getValue(RIGHT) ? right(state) : left(state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACE, FACING, RIGHT);
	}

	@Override
	protected boolean passesInFrame(BlockState state, Direction side, Direction facing) {
		return side == facing || side == (state.getValue(RIGHT) ? facing.getCounterClockWise() : facing.getClockWise());
	}

	@Override
	protected BlockState thrown(BlockState state) {
		return state.cycle(RIGHT);
	}

	@Override
	protected boolean isThrown(BlockState state) {
		return state.getValue(RIGHT);
	}

	@Override
	protected Component positionMessage(BlockState state) {
		return Component.translatable(state.getValue(RIGHT) ? "message.radiocraft.power_switch.right" : "message.radiocraft.power_switch.left");
	}

	@Override
	protected String tooltipKey() {
		return "tooltip.radiocraft.power_switch_left_right";
	}
}
