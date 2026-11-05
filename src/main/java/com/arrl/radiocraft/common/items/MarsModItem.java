package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.radios.HFReceiverBlock;
import com.arrl.radiocraft.common.blocks.radios.RadioBlock;
import com.arrl.radiocraft.common.blocks.radios.VHFReceiverBlock;
import com.arrl.radiocraft.common.blocks.radios.VHFRepeaterBlock;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The MARS mod: the little board (or snipped diode) that opens a ham rig's transmitter beyond the amateur bands, as
 * done for the Military Auxiliary Radio System. Crafted together with a radio (shapeless, see
 * {@link com.arrl.radiocraft.common.crafting.MarsModInstallRecipe}) it sets {@link RadiocraftDataComponent#MARS_MOD}
 * on it: an HF band then transmits a quarter of its width past each edge, a VHF radio up to 174 MHz
 * ({@link com.arrl.radiocraft.common.radio.BandEdges}). Not legal for amateur use; the tooltip says so.
 */
public class MarsModItem extends Item {

	public MarsModItem(Properties properties) {
		super(properties);
	}

	/**
	 * True for the radios a MARS mod fits: HF transceivers (single band, all-band, QRP), the VHF base station, the VHF
	 * handheld and the HF manpack. Not receivers (they already tune out of band) nor repeaters.
	 */
	public static boolean fits(ItemStack stack) {
		if(stack.isEmpty() || VHFHandheldItem.isChannelized(stack))
			return false; // Commercial and marine radios are already where they may transmit.
		if(stack.getItem() instanceof BlockItem item && (item.getBlock() instanceof com.arrl.radiocraft.common.blocks.radios.CommercialMobileBlock
				|| item.getBlock() instanceof com.arrl.radiocraft.common.blocks.radios.MarineRadioBlock))
			return false;
		if(stack.getItem() instanceof VHFHandheldItem || stack.getItem() instanceof HFManpackItem)
			return true;
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof RadioBlock
				&& !(blockItem.getBlock() instanceof HFReceiverBlock) && !(blockItem.getBlock() instanceof VHFReceiverBlock)
				&& !(blockItem.getBlock() instanceof VHFRepeaterBlock);
	}

	/** True if {@code stack} has a MARS mod fitted. */
	public static boolean isFitted(ItemStack stack) {
		return stack.has(RadiocraftDataComponent.MARS_MOD.value());
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "mars_mod")).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "mars_mod.contraband")).withStyle(ChatFormatting.RED));
	}
}
