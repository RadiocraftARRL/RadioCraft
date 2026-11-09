package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.ICoaxPassThrough;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The lightning arrestor on a coax run. The signal passes straight through it ({@link ICoaxPassThrough}), so the radio
 * and the antenna stay on one network. A strike's surge stops here: see
 * {@link com.arrl.radiocraft.common.radio.rfi.AntennaStrikes}.
 */
public class LightningArrestorNetworkObject extends BENetworkObject implements ICoaxPassThrough {

	public static final ResourceLocation TYPE = Radiocraft.id("lightning_arrestor");

	public LightningArrestorNetworkObject(Level level, BlockPos pos) {
		super(level, pos);
	}

	@Override
	public ResourceLocation getType() {
		return TYPE;
	}
}
