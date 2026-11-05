package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.benetworks.BENetwork;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.PowerBENetwork;
import com.arrl.radiocraft.api.benetworks.PowerNetworkObject;
import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.common.be_networks.ICoaxNetworkObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The network objects of ADS-B (1.12 step 9): the beacon and the receiver on the power wire (each drawing its
 * {@code adsb_*_power_use} a tick while switched on), and the 1090 MHz antenna on the receiver's coax.
 */
public final class AdsbNetworkObjects {

	private AdsbNetworkObjects() {}

	/** Power drawn a tick, read safely before the config has loaded. */
	private static int use(Supplier<Integer> value, int fallback) {
		try {
			return Math.max(0, value.get());
		}
		catch(IllegalStateException e) { // Config not loaded yet.
			return fallback;
		}
	}

	/** Something on the power wire with a power switch: on when switched on and paid for this tick. */
	public abstract static class Switched extends PowerNetworkObject {

		/** The power switch, and whether it really runs. */
		public boolean wantsOn = false;
		public boolean isPowered = false;

		protected Switched(Level level, BlockPos pos, int buffer) {
			super(level, pos, Math.max(1, buffer), Math.max(1, buffer), Math.max(1, buffer));
		}

		/** Power it draws a tick while on. */
		protected abstract int powerUse();

		@Override
		public boolean isIndirectConsumer() {
			return true;
		}

		@Override
		public boolean isDirectConsumer() {
			return false;
		}

		@Override
		public void tick(Level level, BlockPos pos) {
			if(!wantsOn)
				isPowered = false;
			else if(!RadiocraftServerConfig.RADIO_POWER_REQUIRED.get())
				isPowered = true;
			else
				isPowered = tryConsumeEnergy(powerUse(), false);
		}

		@Override
		public void save(CompoundTag nbt) {
			super.save(nbt);
			nbt.putBoolean("wantsOn", wantsOn);
		}

		@Override
		public void load(IBENetworks cap, CompoundTag nbt) {
			super.load(cap, nbt);
			wantsOn = nbt.getBoolean("wantsOn");
		}
	}

	/** The beacon (transponder): power only. Its antenna is built in. */
	public static class Beacon extends Switched {

		public static final ResourceLocation TYPE = Radiocraft.id("adsb_beacon");

		public Beacon(Level level, BlockPos pos) {
			super(level, pos, use(RadiocraftServerConfig.ADSB_BEACON_POWER_USE::get, 2));
		}

		@Override
		protected int powerUse() {
			return use(RadiocraftServerConfig.ADSB_BEACON_POWER_USE::get, 2);
		}

		@Override
		public ResourceLocation getType() {
			return TYPE;
		}
	}

	/** The receiver: on the power wire, and on the coax to its antennas. */
	public static class Receiver extends Switched implements ICoaxNetworkObject {

		public static final ResourceLocation TYPE = Radiocraft.id("adsb_receiver");

		public Receiver(Level level, BlockPos pos) {
			super(level, pos, use(RadiocraftServerConfig.ADSB_RECEIVER_POWER_USE::get, 4));
		}

		@Override
		protected int powerUse() {
			return use(RadiocraftServerConfig.ADSB_RECEIVER_POWER_USE::get, 4);
		}

		/** The ADS-B antennas on this receiver's coax. */
		public List<BlockPos> antennas() {
			Set<BlockPos> out = new LinkedHashSet<>();
			for(BENetwork network : networks.values()) {
				if(network instanceof PowerBENetwork)
					continue;
				for(BENetworkObject object : network.getNetworkObjects())
					if(object instanceof Antenna)
						out.add(object.getPos());
			}
			return new ArrayList<>(out);
		}

		@Override
		public ResourceLocation getType() {
			return TYPE;
		}
	}

	/** The 1090 MHz antenna on the coax. */
	public static class Antenna extends BENetworkObject implements ICoaxNetworkObject {

		public static final ResourceLocation TYPE = Radiocraft.id("adsb_antenna");

		public Antenna(Level level, BlockPos pos) {
			super(level, pos);
		}

		@Override
		public ResourceLocation getType() {
			return TYPE;
		}
	}
}
