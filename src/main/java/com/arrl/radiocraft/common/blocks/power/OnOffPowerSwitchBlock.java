package com.arrl.radiocraft.common.blocks.power;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Power Switch - On/Off (2026-11-09): two terminals, on the {@link #FACING} side and the side opposite. On, power
 * passes between them; off, nothing does, and the two sides are separate circuits. Made off.
 */
public class OnOffPowerSwitchBlock extends PowerSwitchBlock {

	/** The lever: true when the two terminals are connected. */
	public static final BooleanProperty ON = BooleanProperty.create("on");

	public OnOffPowerSwitchBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ON);
	}

	@Override
	public boolean passes(BlockState state, Direction side) {
		Direction facing = state.getValue(FACING);
		return state.getValue(ON) && (side == facing || side == facing.getOpposite());
	}

	@Override
	protected BlockState thrown(BlockState state) {
		return state.cycle(ON);
	}

	@Override
	protected boolean isThrown(BlockState state) {
		return state.getValue(ON);
	}

	@Override
	protected Component positionMessage(BlockState state) {
		return Component.translatable(state.getValue(ON) ? "message.radiocraft.power_switch.on" : "message.radiocraft.power_switch.off");
	}

	@Override
	protected String tooltipKey() {
		return "tooltip.radiocraft.power_switch_on_off";
	}
}
