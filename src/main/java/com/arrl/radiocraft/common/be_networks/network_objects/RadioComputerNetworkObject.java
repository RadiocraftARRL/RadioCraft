package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The radio computer on the power wire (1.12): while it is in use ({@link #wantsOn}: a screen open, or a background
 * program installed) it draws {@code computer_power_use} a tick, about an HF radio's receive draw, and runs only when
 * that is paid ({@link #isPowered}). Free when {@code radios_need_power} is off. While its screensaver shows
 * ({@link #saving}, computer batch 3A) it draws a quarter of that ({@link #SAVER_SHARE}), at least 1.
 */
public class RadioComputerNetworkObject extends AdsbNetworkObjects.Switched {

	public static final ResourceLocation TYPE = Radiocraft.id("radio_computer");
	/** The share of the draw left while the screensaver shows (the monitor dims, the disk stops). */
	public static final int SAVER_SHARE_DIVISOR = 4;

	/** True while the computer's screensaver shows: it draws less ({@code RadioComputerBlockEntity} sets it). */
	public boolean saving = false;

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
		return drawPerTick();
	}

	/** What it draws a tick while on: the config's, or a quarter of it while the screensaver shows. */
	public int drawPerTick() {
		int use = use();
		return saving && use > 0 ? Math.max(1, use / SAVER_SHARE_DIVISOR) : use;
	}

	@Override
	public ResourceLocation getType() {
		return TYPE;
	}
}
