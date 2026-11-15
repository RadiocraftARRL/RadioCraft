package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blockentities.LinearAmplifierBlockEntity;
import com.arrl.radiocraft.common.blocks.power.AbstractPowerNetworkBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The linear amplifier (2026-11-13): a desk box on the coax between an HF radio and its antenna, and on a power wire.
 * Use it to switch OPERATE / STANDBY. Its lamp is lit while it is working (OPERATE and powered) and its plate meter
 * kicks while the radio transmits through it. A blown amplifier smokes and passes the signal straight through; a gold
 * ingot used on it mends it (a new tube). The amplifying is in
 * {@link com.arrl.radiocraft.common.be_networks.network_objects.LinearAmplifierNetworkObject}.
 */
public final class LinearAmplifierBlock extends AbstractPowerNetworkBlock {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty OPERATE = BooleanProperty.create("operate");
	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	public static final BooleanProperty KEYED = BooleanProperty.create("keyed");
	public static final BooleanProperty BLOWN = BooleanProperty.create("blown");
	public static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 2.0D, 15.0D, 8.0D, 14.0D);

	public LinearAmplifierBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(OPERATE, false).setValue(LIT, false).setValue(KEYED, false).setValue(BLOWN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPERATE, LIT, KEYED, BLOWN);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}

	/** OPERATE / STANDBY. */
	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
		if(!level.isClientSide) {
			boolean operate = !state.getValue(OPERATE);
			level.setBlock(pos, state.setValue(OPERATE, operate).setValue(LIT, false).setValue(KEYED, false), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, operate ? 0.9F : 0.7F);
			player.displayClientMessage(Component.translatable(state.getValue(BLOWN) ? "message.radiocraft.amplifier.blown"
					: operate ? "message.radiocraft.amplifier.operate" : "message.radiocraft.amplifier.standby"), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A gold ingot mends a blown amplifier (a new tube). */
	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
			@NotNull InteractionHand hand, @NotNull BlockHitResult hitResult) {
		if(!stack.is(Items.GOLD_INGOT) || !state.getValue(BLOWN))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if(!level.isClientSide) {
			level.setBlock(pos, state.setValue(BLOWN, false), Block.UPDATE_ALL);
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
			player.displayClientMessage(Component.translatable("message.radiocraft.amplifier.mended"), true);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Burns out the amplifier: a pop and smoke; it passes the signal through unamplified until mended. */
	public static void blow(ServerLevel level, BlockPos pos, BlockState state) {
		if(state.getValue(BLOWN))
			return;
		level.setBlock(pos, state.setValue(BLOWN, true).setValue(LIT, false).setValue(KEYED, false), Block.UPDATE_ALL);
		Vec3 centre = pos.getCenter();
		level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, centre.x, centre.y + 0.3D, centre.z, 16, 0.25D, 0.1D, 0.25D, 0.02D);
	}

	@Override
	public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
		if(state.getValue(BLOWN) && random.nextInt(6) == 0)
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3D + random.nextDouble() * 0.4D, pos.getY() + 0.55D, pos.getZ() + 0.3D + random.nextDouble() * 0.4D, 0.0D, 0.03D, 0.0D);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.linear_amplifier"));
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new LinearAmplifierBlockEntity(pos, state);
	}
}
