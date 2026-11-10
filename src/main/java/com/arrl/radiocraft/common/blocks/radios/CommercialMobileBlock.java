package com.arrl.radiocraft.common.blocks.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A commercial mobile radio (VHF high band or low band): a dash-mount box with its hand mic on a cord, like the VHF
 * base station, for a desk, a vehicle or a Sable ship. Channelized and programmed like the handhelds
 * ({@link com.arrl.radiocraft.common.radio.commercial.CommercialProgramming}); its case takes dye. The block entity
 * does the radio ({@link CommercialMobileBlockEntity}).
 */
public class CommercialMobileBlock extends RadioBlock {

	/** The VHF set's shape, 4 px tall, and the low band set's, 6 px (its speaker on top; 2026-11-09). */
	private static final Map<Direction, VoxelShape> SHAPES = shapes(4.0D), LOW_BAND_SHAPES = shapes(6.0D);

	private static Map<Direction, VoxelShape> shapes(double height) {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		shapes.put(Direction.NORTH, Block.box(2.0D, 0.0D, 3.0D, 14.0D, height, 15.0D));
		shapes.put(Direction.SOUTH, Block.box(2.0D, 0.0D, 1.0D, 14.0D, height, 13.0D));
		shapes.put(Direction.WEST, Block.box(3.0D, 0.0D, 2.0D, 15.0D, height, 14.0D));
		shapes.put(Direction.EAST, Block.box(1.0D, 0.0D, 2.0D, 13.0D, height, 14.0D));
		return shapes;
	}

	private final CommercialRadioModel model;

	public CommercialMobileBlock(Properties properties, CommercialRadioModel model) {
		super(properties);
		this.model = model;
	}

	public CommercialRadioModel getModel() {
		return model;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return null;
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		return hanging(state, (model == CommercialRadioModel.LOW_BAND_MOBILE ? LOW_BAND_SHAPES : SHAPES).get(state.getValue(RadioBlock.HORIZONTAL_FACING)));
	}

	/**
	 * While a distress beacon alert is on the display (2026-11-09), sneak and use with an empty hand clears it instead of
	 * taking the hand mic (until the new front panel has its own key); the next sneak and use takes the mic as usual.
	 */
	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
		if(player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof CommercialMobileBlockEntity radio && radio.distressLines() != null) {
			if(!level.isClientSide)
				radio.dismissDistress();
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return super.useWithoutItem(state, level, pos, player, hitResult);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new CommercialMobileBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		CommercialProgram program = stack.get(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value());
		if(program == null)
			program = model.factoryProgram();
		CommercialChannel channel = program.active();
		String name = channel == null ? "-" : channel.name().isEmpty() ? String.format(Locale.ROOT, "%.4f MHz", channel.rxHz() / 1e6) : channel.name();
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "commercial.channel"), program.selected() + 1, name).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "commercial.programmed"), program.programmedCount(), model.channelCount())
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
