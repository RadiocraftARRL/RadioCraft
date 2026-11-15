package com.arrl.radiocraft.common.blocks;

import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.radio.morse.IambicKeyer;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The iambic keyer (2026-11-12, after a Bencher BY-1 paddle on a Heathkit HD-1410 keyer): placed beside an HF radio
 * (the all-band radio included), it keys that radio's CW. Using it opens its front panel ({@code KeyerScreen}): MODE,
 * SPEED and SIDETONE, which it keeps in its block state, and the paddles (A dit, D dah, rebindable). Its finger pieces
 * come in {@link Finish}es: crafted with a dye, glass, a metal or a gem it takes that finish, and keeps it when broken.
 */
public class IambicKeyerBlock extends HorizontalDirectionalBlock {

	public static final MapCodec<IambicKeyerBlock> CODEC = simpleCodec(IambicKeyerBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final EnumProperty<KeyerMode> MODE = EnumProperty.create("mode", KeyerMode.class);
	/** 1 slowest (6 WPM) to 4 fastest (24 WPM). */
	public static final IntegerProperty SPEED = IntegerProperty.create("speed", 1, 4);
	/** 0 off to 3 loud. */
	public static final IntegerProperty SIDETONE = IntegerProperty.create("sidetone", 0, 3);
	public static final EnumProperty<Finish> FINISH = EnumProperty.create("finish", Finish.class);

	private static final VoxelShape SHAPE = Shapes.or(Block.box(2.0D, 0.0D, 3.0D, 14.0D, 3.0D, 15.0D), Block.box(4.0D, 3.0D, 1.0D, 12.0D, 7.0D, 9.0D));

	public enum KeyerMode implements StringRepresentable {
		IAMBIC_A(IambicKeyer.Mode.IAMBIC_A), IAMBIC_B(IambicKeyer.Mode.IAMBIC_B), BUG(IambicKeyer.Mode.BUG), STRAIGHT(IambicKeyer.Mode.STRAIGHT);

		public final IambicKeyer.Mode mode;

		KeyerMode(IambicKeyer.Mode mode) {
			this.mode = mode;
		}

		public KeyerMode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		@Override
		public @NotNull String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The finger pieces' finish, and what to craft the keyer with for it. */
	public enum Finish implements StringRepresentable {
		AMBER(() -> Items.HONEYCOMB), CLEAR(() -> Items.GLASS_PANE),
		WHITE(() -> Items.WHITE_DYE), ORANGE(() -> Items.ORANGE_DYE), MAGENTA(() -> Items.MAGENTA_DYE), LIGHT_BLUE(() -> Items.LIGHT_BLUE_DYE),
		YELLOW(() -> Items.YELLOW_DYE), LIME(() -> Items.LIME_DYE), PINK(() -> Items.PINK_DYE), GRAY(() -> Items.GRAY_DYE),
		LIGHT_GRAY(() -> Items.LIGHT_GRAY_DYE), CYAN(() -> Items.CYAN_DYE), PURPLE(() -> Items.PURPLE_DYE), BLUE(() -> Items.BLUE_DYE),
		BROWN(() -> Items.BROWN_DYE), GREEN(() -> Items.GREEN_DYE), RED(() -> Items.RED_DYE), BLACK(() -> Items.BLACK_DYE),
		IRON(() -> Items.IRON_INGOT), GOLD(() -> Items.GOLD_INGOT), COPPER(() -> Items.COPPER_INGOT), DIAMOND(() -> Items.DIAMOND),
		EMERALD(() -> Items.EMERALD), LAPIS(() -> Items.LAPIS_LAZULI), AMETHYST(() -> Items.AMETHYST_SHARD), QUARTZ(() -> Items.QUARTZ),
		NETHERITE(() -> Items.NETHERITE_INGOT);

		public final Supplier<Item> material;

		Finish(Supplier<Item> material) {
			this.material = material;
		}

		@Override
		public @NotNull String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	@SuppressWarnings("this-escape")
	public IambicKeyerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, KeyerMode.IAMBIC_B).setValue(SPEED, 2)
				.setValue(SIDETONE, 2).setValue(FINISH, Finish.AMBER));
	}

	@Override
	protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, MODE, SPEED, SIDETONE, FINISH);
	}

	/** Its finger pieces toward the player who placed it. The finish comes from the item ({@code block_state}). */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(level.isClientSide)
			com.arrl.radiocraft.client.screens.KeyerScreen.open(pos); // A separate class, so the server never loads it.
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Dit length in ticks at a SPEED setting. */
	public static int unit(int speed) {
		return 5 - Math.max(1, Math.min(4, speed));
	}

	/** The sidetone's volume at a SIDETONE setting. */
	public static float sidetone(int level) {
		return switch(level) {
			case 0 -> 0.0F;
			case 1 -> 0.15F;
			case 3 -> 0.5F;
			default -> HFRadioBlockEntity.SIDETONE;
		};
	}

	/** The HF radio a keyer at {@code pos} keys: the first beside it, or null. */
	@Nullable
	public static HFRadioBlockEntity radioBeside(Level level, BlockPos pos) {
		for(Direction side : Direction.values())
			if(level.getBlockEntity(pos.relative(side)) instanceof HFRadioBlockEntity radio)
				return radio;
		return null;
	}

	/** True if a keyer stands beside the radio at {@code radioPos} (so its CW may come from the keyer's screen). */
	public static boolean keyerBeside(Level level, BlockPos radioPos) {
		for(Direction side : Direction.values())
			if(level.getBlockState(radioPos.relative(side)).getBlock() instanceof IambicKeyerBlock)
				return true;
		return false;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.iambic_keyer").withStyle(ChatFormatting.GRAY));
		net.minecraft.world.item.component.BlockItemStateProperties props = stack.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
		Finish finish = props == null ? null : props.get(FINISH);
		tooltip.add(Component.translatable("tooltip.radiocraft.iambic_keyer.finish",
				Component.translatable("finish.radiocraft." + (finish == null ? Finish.AMBER : finish).getSerializedName())).withStyle(ChatFormatting.DARK_GRAY));
	}
}
