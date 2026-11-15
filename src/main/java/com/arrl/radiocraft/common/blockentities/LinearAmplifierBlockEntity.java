package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.common.be_networks.network_objects.LinearAmplifierNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The linear amplifier's block entity: it only hosts the network object (the lamp and meter are block states). */
public class LinearAmplifierBlockEntity extends BlockEntity implements INetworkObjectProvider {

	public LinearAmplifierBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.LINEAR_AMPLIFIER.get(), pos, state);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new LinearAmplifierNetworkObject(level, worldPosition);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(!level.isClientSide())
			getNetworkObject(level, worldPosition); // Forces the network object to be created and wired.
	}
}
