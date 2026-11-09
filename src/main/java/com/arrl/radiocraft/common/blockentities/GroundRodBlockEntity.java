package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.GroundRodNetworkObject;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.ground.Grounding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A ground rod's block entity: its network object, for the ground wire walk to find, and the ground fault check. A
 * battery, solar panel or charge controller on the same ground wire run as a rod is a fault: every few seconds it
 * sparks at the rod and the batteries on the run lose {@link #FAULT_DRAIN} of their charge into the earth.
 */
public class GroundRodBlockEntity extends BlockEntity implements INetworkObjectProvider {

	/** How often the rod looks for power on its ground wire, ticks. */
	static final int CHECK_TICKS = 40;
	/** Share of each faulted battery's charge lost per check. */
	public static final double FAULT_DRAIN = 0.02D;

	private boolean faulted = false;

	public GroundRodBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.GROUND_ROD.get(), pos, state);
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new GroundRodNetworkObject(level, worldPosition);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide())
			getNetworkObject(level, worldPosition);
	}

	/** True if power was found on this rod's ground wire at the last check. */
	public boolean isFaulted() {
		return faulted;
	}

	public static void serverTick(Level level, BlockPos pos, GroundRodBlockEntity rod) {
		if(!(level instanceof ServerLevel serverLevel) || (level.getGameTime() + pos.asLong()) % CHECK_TICKS != 0L)
			return;
		var sources = Grounding.powerOnGround(level, pos);
		rod.faulted = !sources.isEmpty();
		if(!rod.faulted)
			return;
		for(BENetworkObject source : sources)
			if(source instanceof BatteryNetworkObject battery) {
				int stored = battery.getStorage().getEnergyStored();
				battery.getStorage().setEnergy(stored - (int)Math.ceil(stored * FAULT_DRAIN));
			}
		serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D, 8, 0.1D, 0.1D, 0.1D, 0.2D);
		serverLevel.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.4F, 1.6F);
	}
}
