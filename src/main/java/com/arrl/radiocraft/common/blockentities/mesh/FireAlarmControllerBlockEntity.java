package com.arrl.radiocraft.common.blockentities.mesh;

import com.arrl.radiocraft.common.blocks.mesh.FireAlarmControllerBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import com.arrl.radiocraft.common.radio.firealarm.AlarmEvent;
import com.arrl.radiocraft.common.radio.firealarm.ControllerState;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarms;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A fire alarm controller (2026-11-10), after a DMP-704 receiver: it hears the boxes on its frequency, acks them, keeps
 * the log and the box list ({@link ControllerState}, with the mesh, so it logs while unloaded too), buzzes until someone
 * acknowledges an alarm, rings bells (redstone) while a box is in alarm and not silenced, and prints a report slip for
 * each event on the paper loaded into it. Out of paper it shows PAPER OUT and keeps the slips until paper goes in; now
 * and then the head jams (HEAD JAM) and must be cleared. It counts as a base on its license.
 */
public class FireAlarmControllerBlockEntity extends MeshDeviceBlockEntity implements net.minecraft.world.MenuProvider {

	public static final int MAX_PAPER = 64;
	/** Ticks between buzzes, and between printing one slip and the next. */
	public static final int BUZZ_TICKS = 20, PRINT_TICKS = 30;
	/** Chance a slip jams the head. */
	public static final float JAM_CHANCE = 0.02F;

	private int paper = 0;
	private boolean jammed = false;
	private long nextPrintAt = 0L;
	private int viewing = -1; // The log entry NEXT is showing, counted back from the newest; -1 for the newest.
	private final Random random = new Random();

	public FireAlarmControllerBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.FIRE_ALARM_CONTROLLER.get(), pos, state);
	}

	@Override
	public MeshRole role() {
		return MeshRole.CONTROLLER;
	}

	@Override
	protected int firstAddress() {
		return 0;
	}

	@Nullable
	public ControllerState state() {
		return level instanceof ServerLevel serverLevel ? FireAlarms.of(serverLevel.getServer()).controller(globalPos()) : null;
	}

	/** Log events sent to the screen. */
	public static final int SCREEN_EVENTS = 40;

	@Override
	public @NotNull Component getDisplayName() {
		return getCustomName() != null ? getCustomName() : Component.translatable("block.radiocraft.fire_alarm_controller");
	}

	/** Its terminal (2026-11-11): the green screen comes in CMeshScreenPackets, sent at once and then twice a second. */
	@Override
	public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.@NotNull Inventory inventory,
	                                                                      net.minecraft.world.entity.player.@NotNull Player player) {
		if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel)
			com.arrl.radiocraft.common.network.clientbound.CMeshScreenPacket.send(serverPlayer, worldPosition, screenStatus(serverLevel));
		return new com.arrl.radiocraft.common.menus.FireAlarmControllerMenu(id, this);
	}

	private CompoundTag screenStatus(ServerLevel level) {
		return com.arrl.radiocraft.common.computer.apps.FireAlarmHandler.status(level.getServer(), this, FireAlarms.of(level.getServer()).controller(globalPos()), SCREEN_EVENTS);
	}

	public int getPaper() {
		return paper;
	}

	public boolean isJammed() {
		return jammed;
	}

	/** Loads {@code count} sheets; what did not fit. */
	public int addPaper(int count) {
		int fits = Math.min(count, MAX_PAPER - paper);
		paper += fits;
		setChanged();
		return count - fits;
	}

	/** Clears a head jam; false if there was none. */
	public boolean clearJam() {
		if(!jammed)
			return false;
		jammed = false;
		setChanged();
		return true;
	}

	/** SILENCE (was STOP): quiets the buzzer and bells for a while; the alarm stays on the screen and lamp. */
	public boolean acknowledge() {
		ControllerState state = state();
		if(state == null || !state.needsAttention())
			return false;
		state.silence(level == null ? 0L : level.getGameTime());
		if(level instanceof ServerLevel serverLevel)
			FireAlarms.of(serverLevel.getServer()).setDirty();
		return true;
	}

	/** NEXT: steps back one entry in the log; the entry, or null with an empty log. */
	@Nullable
	public AlarmEvent next() {
		ControllerState state = state();
		if(state == null)
			return null;
		List<AlarmEvent> log = state.log();
		if(log.isEmpty())
			return null;
		viewing = (viewing + 1) % log.size();
		return log.get(log.size() - 1 - viewing);
	}

	public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
		if(level instanceof ServerLevel serverLevel && blockEntity instanceof FireAlarmControllerBlockEntity controller)
			controller.serverTick(serverLevel, state);
	}

	private void serverTick(ServerLevel level, BlockState blockState) {
		long now = level.getGameTime();
		MinecraftServer server = level.getServer();
		if(now % 40L == Math.floorMod(worldPosition.hashCode(), 40))
			checkAntenna(level);
		ControllerState state = FireAlarms.of(server).controller(globalPos());
		sendScreen(level, com.arrl.radiocraft.common.menus.FireAlarmControllerMenu.class, () -> screenStatus(level));
		boolean attention = state.needsAttention();
		if(attention && now % BUZZ_TICKS == 0L)
			level.playSound(null, worldPosition, RadiocraftSoundEvents.FIRE_ALARM_BUZZER.get(), SoundSource.BLOCKS, 1.5F, 1.0F); // Full volume, heard 24 blocks off.
		if(state.nextSlip() != null && now >= nextPrintAt && paper > 0 && !jammed)
			print(level, state, now);
		boolean bells = attention && state.alarm();
		boolean alarm = state.alarm();
		if(blockState.getValue(FireAlarmControllerBlock.POWERED) != bells || blockState.getValue(FireAlarmControllerBlock.ALARM) != alarm) {
			level.setBlock(worldPosition, blockState.setValue(FireAlarmControllerBlock.POWERED, bells).setValue(FireAlarmControllerBlock.ALARM, alarm), Block.UPDATE_ALL);
			level.updateNeighborsAt(worldPosition, blockState.getBlock());
		}
	}

	private void print(ServerLevel level, ControllerState state, long now) {
		AlarmEvent event = state.nextSlip();
		nextPrintAt = now + PRINT_TICKS;
		level.playSound(null, worldPosition, RadiocraftSoundEvents.FIRE_ALARM_PRINTER.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
		if(random.nextFloat() < JAM_CHANCE) {
			jammed = true;
			setChanged();
			return;
		}
		state.printed();
		FireAlarms.of(level.getServer()).setDirty();
		paper--;
		setChanged();
		Direction front = getBlockState().getValue(FireAlarmControllerBlock.FACING);
		BlockPos out = worldPosition.relative(front);
		Containers.dropItemStack(level, out.getX() + 0.5D, worldPosition.getY() + 0.5D, out.getZ() + 0.5D, slip(event));
	}

	/** The printed report slip for {@code event}. */
	public static ItemStack slip(AlarmEvent event) {
		ItemStack stack = new ItemStack(RadiocraftItems.FIRE_ALARM_REPORT.get());
		List<Component> lines = new ArrayList<>();
		for(String line : event.slip())
			lines.add(Component.literal(line).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.DARK_GRAY)));
		stack.set(DataComponents.LORE, new ItemLore(lines));
		return stack;
	}

	@Override
	public List<Component> statusLines() {
		List<Component> out = super.statusLines();
		String base = "message.radiocraft.fire_alarm_controller.";
		ControllerState state = state();
		if(state != null) {
			long alarm = state.boxes().stream().filter(b -> b.status() == ControllerState.Status.ALARM).count();
			long trouble = state.boxes().stream().filter(b -> b.status() == ControllerState.Status.TROUBLE || b.status() == ControllerState.Status.MISSING).count();
			out.add(Component.translatable(base + "boxes", state.boxes().size(), alarm, trouble));
			AlarmEvent latest = state.latest();
			if(latest != null)
				out.add(Component.translatable(base + "latest", latest.logLine()).withStyle(latest.needsAttention() ? ChatFormatting.RED : ChatFormatting.WHITE));
			if(state.slipsWaiting() > 0)
				out.add(Component.translatable(base + "slips", state.slipsWaiting()));
		}
		out.add(jammed ? Component.translatable(base + "head_jam").withStyle(ChatFormatting.RED)
				: paper <= 0 ? Component.translatable(base + "paper_out").withStyle(ChatFormatting.RED)
				: Component.translatable(base + "paper", paper));
		return out;
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("paper", paper);
		tag.putBoolean("jammed", jammed);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		paper = tag.getInt("paper");
		jammed = tag.getBoolean("jammed");
	}
}
