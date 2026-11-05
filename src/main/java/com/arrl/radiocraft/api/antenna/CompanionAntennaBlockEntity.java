package com.arrl.radiocraft.api.antenna;

import com.arrl.radiocraft.common.blockentities.AntennaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of another mod's antenna feed point: it puts a network object on the coax, asks
 * {@link AntennaTypes#match} what antenna is built here (when it loads, and again a few seconds after
 * {@link #markAntennaChanged()}), and puts that antenna on the air. Register a {@link BlockEntityType} for it with your
 * block, and make your block a {@link CompanionAntennaBlock} (or any block implementing {@link IAntennaBlock} that
 * ticks it with {@code AntennaBlockEntity::tick} on the server and leaves the networks when removed).
 *
 * @since 1.12.0
 */
public class CompanionAntennaBlockEntity extends AntennaBlockEntity {

	/**
	 * Makes the block entity. The antenna network comes from the block ({@link IAntennaBlock}), HF otherwise.
	 *
	 * @param type your block entity type
	 * @param pos where it is
	 * @param state the block
	 */
	public CompanionAntennaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, AntennaBlockEntity.networkFor(state));
	}
}
