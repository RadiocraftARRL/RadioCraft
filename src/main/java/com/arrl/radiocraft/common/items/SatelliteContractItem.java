package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatelliteLicense;
import com.arrl.radiocraft.common.radio.satellite.Satellites;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * An amateur or commercial satellite contract (2026-11-11, {@code doc/SatellitePlan.md}), sold by master cartographers.
 * Right-clicked, it puts a satellite on orbit ({@link Satellites#launch}) with the player as its owner, on a frequency
 * uplink, downlink and telemetry frequency no other satellite uses, and gives them a commercial frequency license covering both frequencies, a satellite
 * license and the SATTRACK floppy. No launch pad or rocket (yet).
 */
public class SatelliteContractItem extends Item {

	private final boolean amateur;
	/** A geostationary satellite contract (2026-11-12): parked over where it is used, pointing there. */
	private final boolean geostationary;

	public SatelliteContractItem(boolean amateur, Properties properties) {
		this(amateur, false, properties);
	}

	public SatelliteContractItem(boolean amateur, boolean geostationary, Properties properties) {
		super(properties);
		this.amateur = amateur;
		this.geostationary = geostationary;
	}

	public boolean amateur() {
		return amateur;
	}

	public boolean geostationary() {
		return geostationary;
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(!(level instanceof ServerLevel server))
			return InteractionResultHolder.sidedSuccess(stack, true);
		List<ItemStack> granted = launch(server, player, amateur, geostationary);
		if(granted == null) {
			player.displayClientMessage(Component.translatable(Radiocraft.translationKey("message", "satellite.no_pair")).withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.8F);
		stack.consume(1, player);
		for(ItemStack item : granted)
			if(!player.getInventory().add(item))
				player.drop(item, false);
		return InteractionResultHolder.success(stack);
	}

	/**
	 * Launches a satellite for {@code player} and makes its papers: the commercial license (commercial only), the satellite license and the
	 * SATTRACK floppy. Tells the player. Null if no frequency pair is free.
	 */
	public static List<ItemStack> launch(ServerLevel level, Player player, boolean amateur) {
		return launch(level, player, amateur, false);
	}

	/**
	 * As {@link #launch(ServerLevel, Player, boolean)}; {@code geostationary} (2026-11-12) parks it near where the player
	 * stands, pointing there: the centre of its coverage and of its imaging.
	 */
	public static List<ItemStack> launch(ServerLevel level, Player player, boolean amateur, boolean geostationary) {
		Random random = new Random(level.random.nextLong());
		Satellites satellites = Satellites.of(level.getServer());
		Satellite satellite = geostationary
				? satellites.launchGeostationary(level.getServer(), amateur, player.getUUID(), player.getGameProfile().getName(), random, player.getX(), player.getZ())
				: satellites.launch(level.getServer(), amateur, player.getUUID(), player.getGameProfile().getName(), random);
		if(satellite == null)
			return null;
		List<ItemStack> out = new ArrayList<>();
		// A commercial satellite's standard license: the downlink as its frequency, the uplink as its input, its telemetry
		// beacon's frequency as a third (2026-11-12), one base (the satellite). An amateur one gets none (maintainer, 2026-11-11): 2m needs no frequency license, and the satellite
		// registry keeps its pair from being handed out again.
		if(!amateur) {
			CommercialLicense license = CommercialLicenses.of(level.getServer()).grant(level.getServer(), Band.VHF_COMMERCIAL,
					satellite.downlinkHz, satellite.uplinkHz, satellite.telemetryHz(), player.getGameProfile().getName(), player.getStringUUID(), 1, random);
			if(license != null)
				out.add(CommercialLicenseItem.make(level, license));
		}
		ItemStack satelliteLicense = new ItemStack(RadiocraftItems.SATELLITE_LICENSE.get());
		satelliteLicense.set(RadiocraftDataComponent.SATELLITE_LICENSE.value(), SatelliteLicense.of(satellite));
		out.add(satelliteLicense);
		out.add(new ItemStack(RadiocraftItems.floppy(ComputerApps.SATTRACK)));
		if(geostationary)
			player.sendSystemMessage(Component.translatable(Radiocraft.translationKey("message", "satellite.geostationary"), satellite.name,
					player.blockPosition().getX(), player.blockPosition().getZ()));
		player.sendSystemMessage(Component.translatable(Radiocraft.translationKey("message", "satellite.launched"), satellite.name,
				CommercialLicenseRules.mhz(satellite.uplinkHz), CommercialLicenseRules.mhz(satellite.downlinkHz), CommercialLicenseRules.mhz(satellite.telemetryHz()),
				Ctcss.format(satellite.tone())));
		return out;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		String kind = amateur ? "amateur" : "commercial";
		tooltip.add(Component.translatable(Radiocraft.translationKey("tooltip", geostationary ? kind + "_geostationary_contract" : kind + "_satellite_contract"))
				.withStyle(ChatFormatting.GRAY));
	}
}
