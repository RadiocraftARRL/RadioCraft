package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blocks.ComputerMonitorBlock;
import com.arrl.radiocraft.common.computer.Codeplug;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The CPS's "Program radios" mode (1.12 playtest fixes 2; it replaced programming a radio laid on the scanner's glass):
 * the player turns it on in the CPS, closes the screen (the CPS stays in front, in the mode), then right clicks the
 * computer or its monitor holding a commercial radio (a handheld, or a mobile still in item form): a plain click writes
 * the CPS's codeplug into the radio, a sneak click reads the radio into the CPS. The radio is the one in the main hand,
 * or in the off hand when the main hand is empty. Placed, wired mobiles are still programmed through the cable
 * ({@link CommercialTargets}).
 * <p>
 * Keys a radio will not let the programmer read stay hidden as they do with the cable: a read gives them as
 * {@code KEY_HIDDEN}, and writing a hidden key into a radio that has no key at that position is refused. The unit ID
 * in the codeplug, if set, is written and then advanced by one, so a fleet programmed one radio after another gets
 * IDs in sequence; with none set, each radio keeps its own (or is given one).
 * <p>
 * The mode is held on the server per player and computer ({@link RadioComputerBlockEntity#startHand}) and ends when the
 * CPS is closed or another program is opened (the client says so), when the computer loses power, or after
 * {@link RadioComputerBlockEntity#HAND_TIMEOUT_TICKS} unused. Clients mirror it in {@link #clientComputer} so their
 * click is not turned into opening the screen or placing the mobile.
 */
public final class HandProgramming {

	/** The computer whose "Program radios" mode is on in this client's CPS, or null (set by the client's CPS only). */
	@Nullable
	public static volatile BlockPos clientComputer = null;

	private HandProgramming() {}

	// -------------------- THE CPS'S REQUEST --------------------

	/**
	 * {@code hand} ({@code on}, {@code plug}): turns the mode on with the codeplug {@code plug} (also sent again after
	 * every edit while on) or off. Answers {@code on}; error {@code no_power} if the computer could not run it.
	 */
	public static void request(ComputerContext context, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		ServerPlayer player = context.player();
		CompoundTag out = new CompoundTag();
		if(!data.getBoolean("on")) {
			computer.stopHand(player.getUUID());
			out.putBoolean("on", false);
			reply.ok(out);
			return;
		}
		computer.startHand(player, Codeplug.fromTag(data.getCompound("plug")));
		if(computer.handSession(player.getUUID()) == null) { // The computer could not power the mode.
			reply.error("no_power");
			return;
		}
		out.putBoolean("on", true);
		reply.ok(out);
	}

	// -------------------- THE CLICK --------------------

	/**
	 * A right click on a block: if it is a radio computer (or its monitor) whose mode is on for this player, and the
	 * player holds a programmable radio, the radio is programmed (or read, sneaking) and the click goes no further
	 * (no screen opens, no mobile is placed). Both sides: the client cancels from {@link #clientComputer}.
	 */
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		Level level = event.getLevel();
		ItemStack stack = radioIn(player, event.getHand());
		if(stack == null)
			return;
		RadioComputerBlockEntity computer = computerAt(level, event.getPos());
		if(computer == null)
			return;
		if(level.isClientSide) {
			if(!computer.getBlockPos().equals(clientComputer))
				return;
		}
		else if(!(player instanceof ServerPlayer serverPlayer) || !use(computer, serverPlayer, stack, player.isShiftKeyDown()))
			return;
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
	}

	/** The radio the click programs: the one in {@code hand}, or the off hand's when the main hand is empty. Null if none. */
	@Nullable
	static ItemStack radioIn(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if(CommercialProgramming.isProgrammable(held))
			return held;
		if(hand == InteractionHand.MAIN_HAND && held.isEmpty()) {
			ItemStack off = player.getOffhandItem();
			if(CommercialProgramming.isProgrammable(off))
				return off;
		}
		return null;
	}

	/** The radio computer at {@code pos}, or under the monitor at {@code pos}. */
	@Nullable
	public static RadioComputerBlockEntity computerAt(Level level, BlockPos pos) {
		if(level.getBlockEntity(pos) instanceof RadioComputerBlockEntity computer)
			return computer;
		return level.getBlockState(pos).getBlock() instanceof ComputerMonitorBlock ? ComputerMonitorBlock.computerUnder(level, pos) : null;
	}

	/**
	 * Programs ({@code read} false) or reads the radio {@code stack} for {@code player} at {@code computer}. False if
	 * the mode is not on for them there (the click then does what it always did). Server side.
	 */
	public static boolean use(RadioComputerBlockEntity computer, ServerPlayer player, ItemStack stack, boolean read) {
		RadioComputerBlockEntity.HandSession session = computer.handSession(player.getUUID());
		CommercialProgramming.Target target = CommercialProgramming.of(stack);
		if(session == null || target == null)
			return false;
		CommercialTargets.Found found = new CommercialTargets.Found("hand", stack.getItem().getDescriptionId(), CommercialTargets.customName(stack), "hand", target, () -> {});
		Component name = stack.getHoverName();
		if(read) {
			CompoundTag radio = CommercialTargets.read(found);
			session.plug = new Codeplug(target.model().id(), Codeplug.channels(radio.getList("channels", net.minecraft.nbt.Tag.TAG_COMPOUND)),
					0, target.program().contacts()); // The radio's own unit ID is shown, not written into the next radio.
			RadioComputerBlockEntity.push(player, ComputerApps.PROGRAMMER, "hand_read", radio);
			player.displayClientMessage(message("read", name, target.program().programmedCount()).withStyle(ChatFormatting.GREEN), true);
			sound(player, computer.getBlockPos(), true);
			return true;
		}
		Codeplug plug = session.plug;
		CommercialProgramming.Result result = CommercialProgramming.write(target, plug.channels(), plug.unitId(), plug.contacts());
		if(!result.ok()) {
			CompoundTag refused = new CompoundTag();
			refused.put("problems", CommercialTargets.problems(result));
			refused.putString("name", name.getString());
			RadioComputerBlockEntity.push(player, ComputerApps.PROGRAMMER, "hand_refused", refused);
			CommercialProgramming.Problem first = result.problems().getFirst();
			player.displayClientMessage(message("refused", name, first.position() + 1,
					Component.translatable(Radiocraft.translationKey("message", "commercial." + first.key()))).withStyle(ChatFormatting.RED), true);
			sound(player, computer.getBlockPos(), false);
			return true;
		}
		CommercialProgram written = target.program();
		if(plug.unitId() > 0) // The next radio gets the next unit ID.
			session.plug = plug.withUnitId(plug.unitId() >= CommercialProgram.UNIT_MAX ? 1 : plug.unitId() + 1);
		CompoundTag done = CommercialTargets.read(found);
		done.putString("name", name.getString());
		done.putInt("next", session.plug.unitId());
		RadioComputerBlockEntity.push(player, ComputerApps.PROGRAMMER, "hand_written", done);
		player.displayClientMessage(message("written", name, written.programmedCount(), written.unitId()).withStyle(ChatFormatting.GREEN), true);
		sound(player, computer.getBlockPos(), true);
		return true;
	}

	/** Tells {@code player} their mode ended ({@code reason}: no_power, timeout, gone), on the CPS and the action bar. */
	public static void ended(ServerPlayer player, String reason) {
		CompoundTag data = new CompoundTag();
		data.putString("reason", reason);
		RadioComputerBlockEntity.push(player, ComputerApps.PROGRAMMER, "hand_end", data);
		if(!reason.equals("gone"))
			player.displayClientMessage(message("ended." + reason).withStyle(ChatFormatting.YELLOW), true);
	}

	/** A programming cable's beep: high for done, low for refused. */
	private static void sound(ServerPlayer player, BlockPos pos, boolean ok) {
		player.level().playSound(null, pos, ok ? SoundEvents.NOTE_BLOCK_BIT.value() : SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS,
				0.6F, ok ? 1.8F : 0.6F);
	}

	/** {@code message.radiocraft.computer.hand.<key>}. */
	static MutableComponent message(String key, Object... args) {
		return Component.translatable(Radiocraft.translationKey("message", "computer.hand." + key), args);
	}
}
