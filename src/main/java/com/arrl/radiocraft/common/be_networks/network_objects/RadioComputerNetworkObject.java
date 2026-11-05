package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The radio computer on the power wire (1.12): while it is in use ({@link #wantsOn}: a screen open, or a background
 * program installed) it draws {@code computer_power_use} a tick, about an HF radio's receive draw, and runs only when
 * that is paid ({@link #isPowered}). Free when {@code radios_need_power} is off.
 */
public class RadioComputerNetworkObject extends AdsbNetworkObjects.Switched {

	public static final ResourceLocation TYPE = Radiocraft.id("radio_computer");

	public RadioComputerNetworkObject(Level level, BlockPos pos) {
		super(level, pos, use());
	}

	/** Power drawn a tick, read safely before the config has loaded. */
	private static int use() {
		try {
			return Math.max(0, RadiocraftServerConfig.COMPUTER_POWER_USE.get());
		}
		catch(IllegalStateException e) { // Config not loaded yet.
			return 8;
		}
	}

	@Override
	protected int powerUse() {
		return use();
	}

	@Override
	public ResourceLocation getType() {
		return TYPE;
	}
}
