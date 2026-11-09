package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.common.be_networks.network_objects.LightningArrestorNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The lightning arrestor's block entity: it only puts the arrestor on the coax ({@link LightningArrestorNetworkObject}). */
public class LightningArrestorBlockEntity extends BlockEntity implements INetworkObjectProvider {

	public LightningArrestorBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.LIGHTNING_ARRESTOR.get(), pos, state);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new LightningArrestorNetworkObject(level, worldPosition);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide())
			getNetworkObject(level, worldPosition); // Makes the object and joins the coax around it.
	}
}
