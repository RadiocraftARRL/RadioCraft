package com.arrl.radiocraft.common.blockentities.adsb;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.common.be_networks.network_objects.AdsbNetworkObjects;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The ADS-B antenna's place on the coax (see {@link AdsbNetworkObjects.Antenna}); nothing else to keep. */
public class AdsbAntennaBlockEntity extends BlockEntity implements INetworkObjectProvider {

	public AdsbAntennaBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.ADSB_ANTENNA.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide)
			getNetworkObject(level, worldPosition); // Joins the coax.
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new AdsbNetworkObjects.Antenna(level, worldPosition);
	}
}
