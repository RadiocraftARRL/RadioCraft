package com.arrl.radiocraft.common.radio.ground;

import com.arrl.radiocraft.api.benetworks.BENetwork;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.common.be_networks.WireUtils;
import com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.ChargeControllerNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.GroundRodNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.LightningArrestorNetworkObject;
import com.arrl.radiocraft.common.be_networks.network_objects.SolarPanelNetworkObject;
import com.arrl.radiocraft.common.blocks.GroundRodBlock;
import com.arrl.radiocraft.common.blocks.Strand;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.ground.GroundQuality.Soil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a station's ground out of the world (maintainer, 2026-11-08): the ground rods bonded to a device by
 * <b>ground wire</b> (green wire; no other colour bonds to a rod), or touching it, and how good they are
 * ({@link GroundQuality}). A radio is grounded through its own ground wire, or through a lightning arrestor on its
 * coax that is (single point grounding: the coax shield is bonded where it comes into the building, as at a
 * professional site).
 */
public final class Grounding {

	private Grounding() {}

	/** Ground wire: the green power wire. */
	public static WireBlock groundWire() {
		return RadiocraftBlocks.COLOURED_WIRE.get(DyeColor.GREEN).get();
	}

	/** The ground rods bonded to the device at {@code pos}: down its ground wire, or touching it. */
	public static Set<BlockPos> rodsFrom(Level level, BlockPos pos) {
		Set<BlockPos> out = new HashSet<>();
		for(Direction direction : Direction.values())
			if(level.getBlockState(pos.relative(direction)).getBlock() instanceof GroundRodBlock)
				out.add(pos.relative(direction).immutable());
		Set<WireBlock> wires = Set.of(groundWire());
		Set<Strand> blackList = new HashSet<>();
		blackList.add(Strand.device(pos));
		Map<BENetworkObject, Direction> reached = new HashMap<>();
		for(BlockPos link : WireUtils.wireLinksOf(level, pos, wires).keySet())
			WireUtils.enter(reached, blackList, level, link, null, o -> o instanceof GroundRodNetworkObject, wires);
		for(BENetworkObject object : reached.keySet())
			out.add(object.getPos());
		return out;
	}

	/**
	 * How well the radio at {@code radioPos} is grounded: through its own ground wire, and through every lightning
	 * arrestor on {@code coaxNetworks} (its coax), all the rods bonded together.
	 */
	public static double station(Level level, BlockPos radioPos, Iterable<? extends BENetwork> coaxNetworks) {
		Set<BlockPos> rods = rodsFrom(level, radioPos);
		for(BENetwork network : coaxNetworks)
			for(BENetworkObject object : network.getNetworkObjects())
				if(object instanceof LightningArrestorNetworkObject)
					rods.addAll(rodsFrom(level, object.getPos()));
		return quality(level, rods);
	}

	/** True if the arrestor at {@code pos} has a ground good enough to send a strike into. */
	public static boolean arrestorGrounded(Level level, BlockPos pos) {
		return quality(level, rodsFrom(level, pos)) >= GroundQuality.ARRESTOR_MIN;
	}

	/** How good these rods are together: each column of stacked rods is one rod, as deep as it is long. */
	public static double quality(Level level, Set<BlockPos> rods) {
		Set<BlockPos> tops = new HashSet<>();
		for(BlockPos rod : rods) {
			BlockPos top = rod;
			while(level.getBlockState(top.above()).getBlock() instanceof GroundRodBlock)
				top = top.above();
			tops.add(top);
		}
		double[] each = new double[tops.size()];
		int i = 0;
		for(BlockPos top : tops)
			each[i++] = rod(level, top);
		return GroundQuality.combine(each);
	}

	/** One column of rods, from its top down. */
	static double rod(Level level, BlockPos top) {
		List<Soil> segments = new ArrayList<>();
		boolean water = false;
		BlockPos at = top;
		while(level.getBlockState(at).getBlock() instanceof GroundRodBlock && segments.size() < 32) {
			segments.add(soilAround(level, at));
			for(Direction direction : Direction.Plane.HORIZONTAL)
				for(int d = 1; d <= 3 && !water; d++)
					water = level.getFluidState(at.relative(direction, d)).is(FluidTags.WATER);
			at = at.below();
		}
		Holder<Biome> biome = level.getBiome(top);
		boolean raining = level.isRaining() && biome.value().getPrecipitationAt(top) == Biome.Precipitation.RAIN;
		double downfall = biome.value().getModifiedClimateSettings().downfall();
		boolean frozen = biome.value().coldEnoughToSnow(top);
		return GroundQuality.rod(segments, GroundQuality.moisture(downfall, raining, water), frozen);
	}

	/** What a rod segment is driven into: the most common soil of the four blocks round it (the better on a tie). */
	static Soil soilAround(Level level, BlockPos pos) {
		int[] counts = new int[Soil.values().length];
		for(Direction direction : Direction.Plane.HORIZONTAL)
			counts[classify(level.getBlockState(pos.relative(direction))).ordinal()]++;
		Soil best = Soil.NONE;
		int most = 0;
		for(Soil soil : Soil.values())
			if(counts[soil.ordinal()] > most) {
				best = soil;
				most = counts[soil.ordinal()];
			}
		return best;
	}

	/** The kind of ground a block is. */
	public static Soil classify(BlockState state) {
		if(state.getFluidState().is(FluidTags.WATER) || state.is(Blocks.MUD) || state.is(Blocks.MUDDY_MANGROVE_ROOTS))
			return Soil.WET;
		if(state.is(Blocks.CLAY))
			return Soil.CLAY;
		if(state.is(BlockTags.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW))
			return Soil.FROZEN;
		if(state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH))
			return Soil.LOAM;
		if(state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(BlockTags.TERRACOTTA))
			return Soil.DRY;
		if(state.is(BlockTags.SAND))
			return Soil.SAND;
		if(state.is(Blocks.GRAVEL) || state.is(Blocks.SUSPICIOUS_GRAVEL))
			return Soil.GRAVEL;
		if(state.isAir() || !state.isSolid())
			return Soil.NONE;
		return Soil.ROCK;
	}

	/**
	 * The power sources on the ground wire from the rod at {@code pos}: a battery, solar panel or charge controller on a
	 * ground run is a ground fault ({@link com.arrl.radiocraft.common.blockentities.GroundRodBlockEntity}).
	 */
	public static List<BENetworkObject> powerOnGround(Level level, BlockPos pos) {
		Set<WireBlock> wires = Set.of(groundWire());
		Set<Strand> blackList = new HashSet<>();
		blackList.add(Strand.device(pos));
		Map<BENetworkObject, Direction> reached = new HashMap<>();
		for(BlockPos link : WireUtils.wireLinksOf(level, pos, wires).keySet())
			WireUtils.enter(reached, blackList, level, link, null,
					o -> o instanceof BatteryNetworkObject || o instanceof SolarPanelNetworkObject || o instanceof ChargeControllerNetworkObject, wires);
		return new ArrayList<>(reached.keySet());
	}
}
