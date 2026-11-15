package com.arrl.radiocraft.common.blocks.antennas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.arrl.radiocraft.common.radio.antenna.YagiAim;

import java.util.List;

/**
 * The VHF yagi. Since 2026-11-12 it is aimed in 16 directions (22.5 degree steps), the way the player faces when placing
 * it: {@link #FACING} is the nearest of the four, and {@link #TURN} the step either side of it (0 is 22.5 degrees
 * anticlockwise, 1 straight along FACING, 2 and 3 22.5 and 45 degrees clockwise). Straight along FACING it is the
 * ordinary block model; turned, the model is drawn by {@code YagiAntennaRenderer}, turned about the mast as one piece.
 */
public class YagiAntennaBlock extends DoubleVHFAntennaBlock {
    public static final VoxelShape POST = Block.box(11.0D, 0.0D, 6.0D, 15.0D, 16.0D, 10.0D);
    /** The 22.5 degree step off {@link #FACING}: see the class comment. 1 (none) is what older yagis load with. */
    public static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 3);
    /** The TURN with no step. */
    public static final int STRAIGHT = YagiAim.STRAIGHT;

    @SuppressWarnings("this-escape")
    public YagiAntennaBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TURN, STRAIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TURN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if(state == null)
            return null;
        float bearing = context.getPlayer() == null ? context.getHorizontalDirection().toYRot() + 180.0F : context.getRotation() + 180.0F;
        int[] split = YagiAim.split(YagiAim.step(bearing));
        return state.setValue(FACING, Direction.from2DDataValue((split[0] + 2) % 4)).setValue(TURN, split[1]);
    }

    /** The compass bearing (0 north, 90 east) a yagi in {@code state} points, in degrees. */
    public static double bearing(BlockState state) {
        int quadrant = Math.floorMod(state.getValue(FACING).get2DDataValue() + 2, 4); // 2D values: S 0, W 1, N 2, E 3.
        return YagiAim.bearing(quadrant, state.hasProperty(TURN) ? state.getValue(TURN) : STRAIGHT);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return rotateHorizontalPlaneDirection(Direction.WEST, state.getValue(FACING), POST);
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return rotateHorizontalPlaneDirection(Direction.WEST, state.getValue(FACING), POST);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("tooltip.radiocraft.yagi_antenna"));
    }
}
