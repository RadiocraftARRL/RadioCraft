package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.satellite.SatelliteLicense;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A satellite license (2026-11-11): the satellite's name, its three frequencies (uplink, downlink, telemetry), its tone and launch day
 * ({@link SatelliteLicense}). It authorizes nothing the commercial license from the same contract does not; it stays
 * as a keepsake once the satellite has come down.
 */
public class SatelliteLicenseItem extends Item {

	public SatelliteLicenseItem(Properties properties) {
		super(properties);
	}

	@Override
	public @NotNull Component getName(@NotNull ItemStack stack) {
		SatelliteLicense license = stack.get(RadiocraftDataComponent.SATELLITE_LICENSE.value());
		if(license == null || license.name().isEmpty())
			return super.getName(stack);
		return Component.translatable(Radiocraft.translationKey("item", "satellite_license.named"), license.name());
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		SatelliteLicense license = stack.get(RadiocraftDataComponent.SATELLITE_LICENSE.value());
		if(license == null) {
			tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "satellite_license.blank")).withStyle(ChatFormatting.GRAY));
			return;
		}
		java.util.function.BiConsumer<String, Object[]> line = (key, args) ->
				tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", "satellite_license." + key), args).withStyle(ChatFormatting.GRAY));
		line.accept(license.amateur() ? "amateur" : "commercial", new Object[0]);
		line.accept("uplink", new Object[] { CommercialLicenseRules.mhz(license.uplinkHz()) });
		line.accept("downlink", new Object[] { CommercialLicenseRules.mhz(license.downlinkHz()) });
		if(license.telemetryHz() > 0.0F)
			line.accept("telemetry", new Object[] { CommercialLicenseRules.mhz(license.telemetryHz()) });
		line.accept("tone", new Object[] { Ctcss.format(license.tone()) });
		line.accept("holder", new Object[] { license.holder() });
		line.accept("launched", new Object[] { license.launchDay() });
	}
}
