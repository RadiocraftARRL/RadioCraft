package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseCertificate;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * A commercial frequency license (2026-11-10): a locked map with the license drawn on it
 * ({@link CommercialLicenseCertificate}), so it hangs in an item frame, carrying its {@link CommercialLicense}. It is a
 * {@link MapItem} (not a vanilla filled map) so vanilla map cloning does not duplicate it, while item frames and the map
 * sync still treat it as a map.
 * <p>
 * The upgrade and copy recipes hand out a license with no picture: the next time it is carried it is checked against
 * {@link CommercialLicenses} (an upgrade gets its paired input frequency there) and its picture is drawn.
 */
public class CommercialLicenseItem extends MapItem {

	/** The inventory tint of a license: cream paper. */
	public static final int TINT = 0xE8DFC0;

	public CommercialLicenseItem(Properties properties) {
		super(properties);
	}

	/** The license on {@code stack}, or null. */
	@Nullable
	public static CommercialLicense license(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(RadiocraftDataComponent.COMMERCIAL_LICENSE.value());
	}

	/** True if {@code stack} is an original license (not a copy). */
	public static boolean isOriginal(ItemStack stack) {
		CommercialLicense license = license(stack);
		return license != null && license.original();
	}

	/** A license item for {@code license}, its picture drawn. */
	public static ItemStack make(ServerLevel level, CommercialLicense license) {
		ItemStack stack = new ItemStack(com.arrl.radiocraft.common.init.RadiocraftItems.COMMERCIAL_LICENSE.get());
		stack.set(RadiocraftDataComponent.COMMERCIAL_LICENSE.value(), license);
		draw(level, stack, license);
		return stack;
	}

	/** Draws {@code license} on a new locked map and puts it on {@code stack}. */
	public static void draw(ServerLevel level, ItemStack stack, CommercialLicense license) {
		MapId id = level.getFreeMapId();
		MapItemSavedData data = MapItemSavedData.createFresh(0, 0, (byte)0, false, false, level.dimension()).locked();
		byte[] pixels = CommercialLicenseCertificate.render(license);
		System.arraycopy(pixels, 0, data.colors, 0, Math.min(pixels.length, data.colors.length));
		data.setDirty();
		level.setMapData(id, data);
		stack.set(DataComponents.MAP_ID, id);
		stack.set(DataComponents.MAP_COLOR, new MapItemColor(TINT));
	}

	@Override
	public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
		if(level instanceof ServerLevel server && !stack.has(DataComponents.MAP_ID)) {
			CommercialLicense license = license(stack);
			if(license != null) {
				CommercialLicense now = CommercialLicenses.of(server.getServer()).upgraded(license, new Random(server.random.nextLong()));
				stack.set(RadiocraftDataComponent.COMMERCIAL_LICENSE.value(), now);
				draw(server, stack, now);
			}
		}
		super.inventoryTick(stack, level, entity, slot, selected);
	}

	@Override
	public @NotNull Component getName(@NotNull ItemStack stack) {
		CommercialLicense license = license(stack);
		String key = license != null && Band.LOW_BAND.equals(license.band()) ? "commercial_license.low_band" : "commercial_license.vhf";
		Component name = Component.translatable(Radiocraft.translationKey("item", key));
		return license != null && license.copy() ? Component.translatable(Radiocraft.translationKey("item", "commercial_license.copy"), name) : name;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		CommercialLicense license = license(stack);
		if(license == null)
			return;
		java.util.function.BiConsumer<String, Object[]> line = (key, args) ->
				tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "commercial_license." + key), args).withStyle(ChatFormatting.GRAY));
		line.accept("callsign", new Object[] { license.callsign() });
		line.accept("holder", new Object[] { license.holder() });
		line.accept("frequency", new Object[] { CommercialLicenseRules.mhz(license.outputHz()) });
		if(license.inputHz() > 0)
			line.accept("input", new Object[] { CommercialLicenseRules.mhz(license.inputHz()) });
		if(license.telemetryHz() > 0)
			line.accept("telemetry", new Object[] { CommercialLicenseRules.mhz(license.telemetryHz()) });
		line.accept("units", new Object[] { license.subscribers(), license.bases() });
		line.accept("issued", new Object[] { license.issued() });
		if(license.copy())
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "commercial_license.is_copy")).withStyle(ChatFormatting.RED));
		else if(license.tier() < CommercialLicense.MAX_TIER)
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "commercial_license.upgrade_hint")).withStyle(ChatFormatting.DARK_GRAY));
	}
}
