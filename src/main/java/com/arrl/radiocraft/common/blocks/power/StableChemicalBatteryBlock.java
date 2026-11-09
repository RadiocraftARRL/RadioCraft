package com.arrl.radiocraft.common.blocks.power;

import com.arrl.radiocraft.api.benetworks.IWireConnectable;
import com.arrl.radiocraft.common.blockentities.StableChemicalBatteryBlockEntity;
import com.arrl.radiocraft.common.datacomponents.ChemicalCharge;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Stable Chemical Battery (maintainer, 2026-11-09): an emergency reserve. It is a large battery in most ways (its
 * capacity and output, the battery meter, its screen, fire) but it cannot be recharged, and it sits inert, full, giving
 * nothing, until it first gets a redstone signal. That breaks its seal for good and it powers its circuit until flat
 * ({@link StableChemicalBatteryBlockEntity}). Placeholder model: the large battery's, in a dark case (ModelDesignBounty).
 */
public class StableChemicalBatteryBlock extends LargeBatteryBlock implements IWireConnectable {

	public StableChemicalBatteryBlock(Properties properties) {
		super(properties);
	}

	@Override
	public boolean acceptsPower(BlockState state) {
		return true; // In code as well as the power_blocks tag, so it connects whatever the tags say.
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new StableChemicalBatteryBlockEntity(pos, state);
	}

	/** A redstone signal breaks the seal (fire and lava beside it still set it alight, see the large battery). */
	@Override
	protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		activateIfSignalled(level, pos);
	}

	/** Placed against a signal that is already on: that breaks the seal too. */
	@Override
	public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		activateIfSignalled(level, pos);
	}

	private static void activateIfSignalled(Level level, BlockPos pos) {
		if(!level.isClientSide && level.hasNeighborSignal(pos) && level.getBlockEntity(pos) instanceof StableChemicalBatteryBlockEntity battery)
			battery.activate();
	}

	@Override
	protected String tooltipKey() {
		return "tooltip.radiocraft.stable_chemical_battery";
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		ChemicalCharge charge = stack.get(RadiocraftDataComponent.CHEMICAL_CHARGE.value());
		if(charge == null || !charge.active())
			tooltip.add(Component.translatable("tooltip.radiocraft.stable_chemical_battery.sealed"));
		else
			tooltip.add(Component.translatable("tooltip.radiocraft.stable_chemical_battery.active",
					Math.round(100.0D * charge.energy() / Math.max(1, com.arrl.radiocraft.CommonConfig.LARGE_BATTERY_CAPACITY.get()))));
	}
}
