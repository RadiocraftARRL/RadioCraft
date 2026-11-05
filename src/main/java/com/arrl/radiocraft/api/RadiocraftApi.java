package com.arrl.radiocraft.api;

import com.arrl.radiocraft.CommonConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.capabilities.IBENetworks;
import com.arrl.radiocraft.api.capabilities.IVHFHandheldCapability;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.capabilities.BENetworksCapability;
import com.arrl.radiocraft.common.capabilities.RadiocraftCapabilities;
import com.arrl.radiocraft.common.capabilities.VHFHandheldCapability;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;

/**
 * The entry point for mods that add to RadioCraft: the API version, and the registration a radio, antenna or handheld
 * of another mod needs that would otherwise mean reaching into RadioCraft's internal classes.
 *
 * <p>Everything in {@code com.arrl.radiocraft.api} is the supported API. Classes elsewhere ({@code common},
 * {@code client}) are internal: some API signatures name them (a {@link com.arrl.radiocraft.common.radio.Band}, a
 * packet), and those types stay, but their other members may change in any release. See the API guide on the
 * RadioCraft website wiki.</p>
 *
 * @since 1.12.0
 */
public final class RadiocraftApi {

	/**
	 * The API version, raised when the API grows (minor) or breaks (major). 1.0 is the API as of RadioCraft 1.12.0.
	 * Compare with {@link #API_VERSION_MAJOR} to refuse an incompatible RadioCraft.
	 */
	public static final String API_VERSION = "1.0";
	/** The major API version: changes only when something in the API is removed or changes its meaning. */
	public static final int API_VERSION_MAJOR = 1;
	/** The minor API version: raised when something is added. */
	public static final int API_VERSION_MINOR = 0;

	/** RadioCraft's capability on the level's power and coax networks, for {@link IBENetworks}. */
	public static final BlockCapability<IBENetworks, Void> BE_NETWORKS = RadiocraftCapabilities.BE_NETWORKS;
	/** RadioCraft's handheld radio state on a handheld item stack (battery, frequency, power, PTT, tones). */
	public static final ItemCapability<IVHFHandheldCapability, Void> HANDHELDS = RadiocraftCapabilities.VHF_HANDHELDS;

	private RadiocraftApi() {}

	/**
	 * Makes {@code blocks} part of RadioCraft's power and coax networks: without this, their block entity never gets
	 * a network object, so a radio never powers on and an antenna never reaches a radio. Call it from your
	 * {@link RegisterCapabilitiesEvent} handler for every block whose block entity implements
	 * {@link com.arrl.radiocraft.api.benetworks.INetworkObjectProvider} (every radio and antenna block).
	 *
	 * @param event the event
	 * @param blocks your blocks
	 */
	public static void registerNetworkBlocks(RegisterCapabilitiesEvent event, Block... blocks) {
		event.registerBlock(RadiocraftCapabilities.BE_NETWORKS, (level, pos, state, be, side) -> BENetworksCapability.get(level), blocks);
	}

	/**
	 * Gives handheld radio items (subclasses of RadioCraft's {@code VHFHandheldItem}) the handheld radio state and the
	 * battery energy RadioCraft's own handhelds have: without them the item is not a radio. Call it from your
	 * {@link RegisterCapabilitiesEvent} handler. See {@link com.arrl.radiocraft.api.items.IHandheldRadio}.
	 *
	 * @param event the event
	 * @param items your handheld items
	 */
	public static void registerHandhelds(RegisterCapabilitiesEvent event, Item... items) {
		event.registerItem(RadiocraftCapabilities.VHF_HANDHELDS, (stack, context) -> new VHFHandheldCapability(stack), items);
		event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> new ComponentEnergyStorage(stack,
				RadiocraftDataComponent.RADIO_ENERGY.get(), CommonConfig.SMALL_BATTERY_CAPACITY.get()), items);
	}

	/**
	 * A network object for a radio (or anything that should hear the antenna on its coax, see
	 * {@link com.arrl.radiocraft.api.blockentities.radio.IAntennaReceiver}): it draws {@code receiveUse} FE a tick
	 * while on, {@code transmitUse} while transmitting, from the power network, and antennas on its coax hand it what
	 * they hear. Return it from {@code createNetworkObject()}. The radio's power switch and the transmitting flag live
	 * on it; {@code RadioBlockEntity} drives them.
	 *
	 * @param level the level
	 * @param pos the block
	 * @param transmitUse FE per tick while transmitting (a VHF base station uses 13, a 10m HF radio 26)
	 * @param receiveUse FE per tick while on (a VHF base station uses 2, a 10m HF radio 8)
	 * @return the object
	 */
	public static BENetworkObject radioNetworkObject(Level level, BlockPos pos, int transmitUse, int receiveUse) {
		return new RadioNetworkObject(level, pos, transmitUse, receiveUse);
	}

	/**
	 * The network object at a block, made on demand for a block entity that provides one. Null if there is none.
	 *
	 * @param level the level
	 * @param pos the block
	 * @return the object, or null
	 */
	public static BENetworkObject networkObject(Level level, BlockPos pos) {
		if(level.getBlockEntity(pos) instanceof com.arrl.radiocraft.api.benetworks.INetworkObjectProvider provider)
			return provider.getNetworkObject(level, pos);
		return IBENetworks.getObject(level, pos);
	}

	/**
	 * The block tags RadioCraft's wires look for. A block in a tag connects to that wire; a block implementing
	 * {@link com.arrl.radiocraft.api.benetworks.IWireConnectable} can say so in code instead. RadioCraft's radio
	 * blocks always take coax and data wire.
	 */
	public static final class Tags {

		/** {@code radiocraft:power_blocks}: power wire joins these. */
		public static final TagKey<Block> POWER_BLOCKS = RadiocraftTags.Blocks.POWER_BLOCKS;
		/** {@code radiocraft:coax_blocks}: coax joins these. */
		public static final TagKey<Block> COAX_BLOCKS = RadiocraftTags.Blocks.COAX_BLOCKS;
		/** {@code radiocraft:data_blocks}: data wire joins these. */
		public static final TagKey<Block> DATA_BLOCKS = RadiocraftTags.Blocks.DATA_BLOCKS;

		private Tags() {}
	}
}
