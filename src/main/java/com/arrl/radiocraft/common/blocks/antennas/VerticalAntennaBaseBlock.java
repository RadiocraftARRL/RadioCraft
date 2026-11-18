package com.arrl.radiocraft.common.blocks.antennas;

import com.arrl.radiocraft.common.blockentities.AntennaBlockEntity;
import com.arrl.radiocraft.common.radio.antenna.types.VerticalAntennaType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * The Vertical Antenna Base (2026-11-18, replacing the 1:1 balun vertical): a stone pad with radials laid over it and a
 * loading coil, fed by coax, with {@link com.arrl.radiocraft.common.blocks.antennas.AntennaPoleBlock antenna poles}
 * stacked on top as the radiator (they taper as they go up). The coil's {@link #TAP} picks the band; each band wants a
 * set number of poles ({@link Tap#poles}, see {@link VerticalAntennaType}). Right click moves the tap (sneaking, back);
 * it says how many poles the band wants and how many are up.
 */
public class VerticalAntennaBaseBlock extends AntennaCenterBlock {

	/** The coil's tap: the band and the poles it wants. 40 and 80 m use more of the coil, so a short stack resonates. */
	public enum Tap implements StringRepresentable {
		B10(10, 3), B20(20, 5), B40(40, 6), B80(80, 7);

		public final int metres, poles;

		Tap(int metres, int poles) {
			this.metres = metres;
			this.poles = poles;
		}

		public String bandName() {
			return metres + "m";
		}

		public Tap next(boolean back) {
			Tap[] all = values();
			return all[Math.floorMod(ordinal() + (back ? -1 : 1), all.length)];
		}

		@Override
		public @NotNull String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final EnumProperty<Tap> TAP = EnumProperty.create("tap", Tap.class);
	private static final VoxelShape SHAPE = Shapes.or(Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D), Block.box(6.0D, 2.0D, 6.0D, 10.0D, 16.0D, 10.0D));

	public VerticalAntennaBaseBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(TAP, Tap.B20));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(TAP);
	}

	/** Moves the coil's tap and says what the new band wants. */
	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!level.isClientSide) {
			Tap tap = state.getValue(TAP).next(player.isShiftKeyDown());
			level.setBlock(pos, state.setValue(TAP, tap), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
			if(level.getBlockEntity(pos) instanceof AntennaBlockEntity antenna)
				antenna.markAntennaChanged();
			int poles = VerticalAntennaType.poles(level, pos);
			player.displayClientMessage(Component.translatable("message.radiocraft.vertical_antenna_base.tap", tap.bandName(), tap.poles, poles)
					.withStyle(poles == tap.poles ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.radiocraft.vertical_antenna_base").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return SHAPE;
	}
}
