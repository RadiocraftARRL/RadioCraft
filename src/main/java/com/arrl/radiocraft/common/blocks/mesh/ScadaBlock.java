package com.arrl.radiocraft.common.blocks.mesh;

import com.arrl.radiocraft.common.blockentities.mesh.MeshDeviceBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.ScadaFacesBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.ScadaMasterBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.ScadaRtuBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.scada.Scada;
import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A SCADA RTU or the SCADA master (2026-11-10). Shift + right-click a face with an empty hand to set it OFF, IN or OUT;
 * right-click tells its status. A book and quill (or written book) used on the master is its rule book (see
 * {@link ScadaRules}). Gives off redstone on its OUT faces.
 */
public class ScadaBlock extends MeshDeviceBlock {

	/** The RTU's case and the fins on top (tools' scada_rtu model, 2026-11-11), by facing; not a full block. */
	private static final java.util.Map<Direction, net.minecraft.world.phys.shapes.VoxelShape> SHAPES = shapes(2.0D, 0.0D, 0.0D, 14.0D, 8.5D, 15.0D);

	@Override
	protected net.minecraft.world.phys.shapes.@NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
	                                                                        net.minecraft.world.phys.shapes.@NotNull CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	public static final MapCodec<ScadaBlock> RTU_CODEC = simpleCodec(p -> new ScadaBlock(p, false));

	private final boolean master;

	@SuppressWarnings("this-escape")
	public ScadaBlock(Properties properties, boolean master) {
		super(properties);
		this.master = master;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public boolean isMaster() {
		return master;
	}

	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
	                                                   @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		List<String> lines = bookLines(stack);
		if(!master || lines == null)
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		if(level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof ScadaMasterBlockEntity device) {
			ScadaRules.Parsed parsed = device.setRules(serverLevel.getServer(), lines);
			level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6F, 1.0F);
			player.sendSystemMessage(Component.translatable("message.radiocraft.scada.rules_loaded", parsed.rules().size(),
					device.master(serverLevel.getServer()).pollSeconds).withStyle(ChatFormatting.GREEN));
			for(String error : parsed.errors())
				player.sendSystemMessage(Component.translatable("message.radiocraft.scada.rule_error", error).withStyle(ChatFormatting.RED));
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** The lines of a book and quill or written book, or null for anything else. */
	@Nullable
	public static List<String> bookLines(ItemStack stack) {
		List<String> pages = new ArrayList<>();
		WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if(writable != null)
			writable.getPages(false).forEach(pages::add);
		else if(written != null)
			for(Component page : written.getPages(false))
				pages.add(page.getString());
		else
			return null;
		List<String> lines = new ArrayList<>();
		for(String page : pages)
			for(String line : page.split("\n"))
				lines.add(line);
		return lines;
	}

	@Override
	protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if(!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof ScadaFacesBlockEntity device))
			return InteractionResult.sidedSuccess(level.isClientSide);
		if(player.isSecondaryUseActive()) {
			Direction face = face(hit);
			Scada.FaceMode mode = device.cycle(serverLevel.getServer(), face);
			level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, 1.8F);
			player.displayClientMessage(Component.translatable("message.radiocraft.scada.face_set",
					Component.translatable("message.radiocraft.scada.face." + face.getSerializedName()), mode.name()), true);
		}
		else if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
			device.openSetup(serverPlayer); // Frequency, callsign and the faces (2026-11-11).
		return InteractionResult.SUCCESS;
	}

	@Override
	protected Component addressText(MeshDeviceBlockEntity device) {
		return master ? Component.translatable("message.radiocraft.scada.as_master") : Component.translatable("message.radiocraft.scada.as_rtu", device.getAddress());
	}

	@Override
	public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
		if(level instanceof ServerLevel serverLevel && newState.getBlock() != this && level.getBlockEntity(pos) instanceof ScadaFacesBlockEntity device)
			Scada.of(serverLevel.getServer()).removed(serverLevel.getServer(), device.globalPos());
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	protected boolean isSignalSource(@NotNull BlockState state) {
		return true;
	}

	/** {@code direction} points from the block asking toward this one, so the face it touches is the opposite. */
	@Override
	protected int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
		return level.getBlockEntity(pos) instanceof ScadaFacesBlockEntity device ? device.emitted(direction.getOpposite()) : 0;
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return RTU_CODEC; // The master has its own block (ScadaMasterBlock).
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return master ? new ScadaMasterBlockEntity(pos, state) : new ScadaRtuBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		if(level.isClientSide)
			return null;
		return type == RadiocraftBlockEntities.SCADA_RTU.get() || type == RadiocraftBlockEntities.SCADA_MASTER.get() ? ScadaFacesBlockEntity::tick : null;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable(master ? "tooltip.radiocraft.scada_master" : "tooltip.radiocraft.scada_rtu").withStyle(ChatFormatting.GRAY));
	}
}
