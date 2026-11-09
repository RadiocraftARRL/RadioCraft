package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.StableChemicalBatteryNetworkObject;
import com.arrl.radiocraft.common.capabilities.BENetworksCapability;
import com.arrl.radiocraft.common.datacomponents.ChemicalCharge;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * The Stable Chemical Battery's block entity (2026-11-09): a large battery's, plus whether its seal is broken. It is
 * made full and sealed; the first redstone signal breaks the seal for good ({@link #activate}). The charge and the seal
 * go onto the item when it is broken out ({@link ChemicalCharge}) and come back when it is placed, so a sealed one
 * kept in a chest is still full and sealed.
 */
public class StableChemicalBatteryBlockEntity extends LargeBatteryBlockEntity {

	private static final String ACTIVE_TAG = "active";
	private boolean active = false;

	public StableChemicalBatteryBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.STABLE_CHEMICAL_BATTERY.get(), pos, state);
		rememberEnergy(CommonConfig.LARGE_BATTERY_CAPACITY.get()); // Made full; a saved or carried one overwrites this.
	}

	public boolean isActive() {
		return active;
	}

	/** Breaks the seal, for good: from now on it powers its circuit until it is flat. Nothing happens if it is broken. */
	public void activate() {
		if(active || level == null || level.isClientSide)
			return;
		active = true;
		setChanged();
		if(object() instanceof StableChemicalBatteryNetworkObject battery)
			battery.setActive(true);
		level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F); // The fizz as the cells flood.
	}

	/** The network object if it exists, without making one. */
	private BENetworkObject object() {
		return level == null || level.isClientSide ? null : BENetworksCapability.get(level).getObject(worldPosition);
	}

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable("container.radiocraft.stable_chemical_battery");
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new StableChemicalBatteryNetworkObject(level, worldPosition,
				CommonConfig.LARGE_BATTERY_CAPACITY.get(),
				CommonConfig.LARGE_BATTERY_OUTPUT.get(),
				CommonConfig.LARGE_BATTERY_OUTPUT.get());
	}

	@Override
	public void onNetworkObjectCreated(Level level, BlockPos pos, BENetworkObject networkObject) {
		super.onNetworkObjectCreated(level, pos, networkObject);
		if(networkObject instanceof StableChemicalBatteryNetworkObject battery)
			battery.setActive(active);
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean(ACTIVE_TAG, active);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		active = tag.getBoolean(ACTIVE_TAG);
	}

	@Override
	protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
		super.applyImplicitComponents(input);
		ChemicalCharge carried = input.get(RadiocraftDataComponent.CHEMICAL_CHARGE.value());
		if(carried == null)
			return;
		active = carried.active();
		rememberEnergy(carried.energy());
		if(object() instanceof StableChemicalBatteryNetworkObject battery) { // Already on the wire: it takes the carried charge too.
			battery.getStorage().setEnergy(carried.energy());
			battery.setActive(active);
		}
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.@NotNull Builder components) {
		super.collectImplicitComponents(components);
		components.set(RadiocraftDataComponent.CHEMICAL_CHARGE.value(), new ChemicalCharge(savedEnergy(), active));
	}
}
