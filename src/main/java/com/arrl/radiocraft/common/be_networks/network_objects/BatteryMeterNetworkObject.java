package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.benetworks.BENetwork;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.PowerBENetwork;
import com.arrl.radiocraft.api.benetworks.PowerNetworkObject;
import com.arrl.radiocraft.common.be_networks.BatteryMeterReading;
import com.arrl.radiocraft.common.be_networks.IPassThroughNetworkObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * The battery meter on a power wire. Power passes through it like a wire junction ({@link IPassThroughNetworkObject}),
 * and it stores, draws and gives nothing. It reads the batteries on its wire ({@link #read}): their charge, and the net
 * flow in or out, smoothed over about a second.
 */
public class BatteryMeterNetworkObject extends PowerNetworkObject implements IPassThroughNetworkObject {

	public static final ResourceLocation TYPE = Radiocraft.id("battery_meter");

	private long lastEnergy = -1;
	private int lastBatteries = -1;
	private double flow;
	private BatteryMeterReading reading = BatteryMeterReading.NONE;

	public BatteryMeterNetworkObject(Level level, BlockPos pos) {
		super(level, pos, 0, 0, 0);
	}

	/**
	 * Takes a reading. Called once a tick by the block entity. The change in the batteries' charge since the last
	 * call is the flow. A change in which batteries are on the wire starts the flow over, so adding a full battery does
	 * not read as a charge.
	 */
	public BatteryMeterReading read() {
		Set<BENetworkObject> seen = new HashSet<>();
		long energy = 0, capacity = 0;
		for(BENetwork network : getNetworks().values()) {
			if(!(network instanceof PowerBENetwork))
				continue;
			for(BENetworkObject object : network.getNetworkObjects()) {
				if(object instanceof BatteryNetworkObject battery && seen.add(battery)) {
					energy += battery.getStorage().getEnergyStored();
					capacity += battery.getStorage().getMaxEnergyStored();
				}
			}
		}
		int batteries = seen.size();
		if(batteries != lastBatteries || lastEnergy < 0)
			flow = 0.0D;
		else
			flow = BatteryMeterReading.smooth(flow, energy - lastEnergy);
		lastEnergy = energy;
		lastBatteries = batteries;
		reading = new BatteryMeterReading(batteries, energy, capacity, flow);
		return reading;
	}

	/** The last reading taken. */
	public BatteryMeterReading getReading() {
		return reading;
	}

	@Override
	public boolean isIndirectConsumer() {
		return false;
	}

	@Override
	public boolean isDirectConsumer() {
		return false;
	}

	@Override
	public ResourceLocation getType() {
		return TYPE;
	}
}
