package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A ground rod (2026-11-08). It joins no power or coax network (it carries nothing): it is only there for the walk
 * along ground wire from a radio or a lightning arrestor to find ({@link com.arrl.radiocraft.common.radio.ground.Grounding}).
 */
public class GroundRodNetworkObject extends BENetworkObject {

	public static final ResourceLocation TYPE = Radiocraft.id("ground_rod");

	public GroundRodNetworkObject(Level level, BlockPos pos) {
		super(level, pos);
	}

	@Override
	public ResourceLocation getType() {
		return TYPE;
	}
}
