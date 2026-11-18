package com.arrl.radiocraft.common.radio.satellite.imaging;

import com.arrl.radiocraft.api.weather.StormReport;
import com.arrl.radiocraft.api.weather.WeatherSource;
import com.arrl.radiocraft.api.weather.WeatherSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a weather satellite sees along one scan line (2026-11-12): full-colour radar over the ground, in map colours.
 * <ul>
 * <li><b>Ground</b>: the biome's colour: where the chunk is loaded, its biome at the top block with real water, ice and snow, shaded
 * by the slope to the north as a map is (2026-11-16: not every block's colour, which looked busy beside the rest);
 * elsewhere from the biome source high over the ground, as WEFAX charts find
 * coastlines (no chunk is loaded or generated): seas and rivers blue, sand, snow, forests and jungles green, badlands
 * orange, peaks grey. Always a step darker than a map ({@link MapColor.Brightness#HIGH} is never used), so the weather
 * stands out and can be read back ({@link RadarScale#levelOf}).</li>
 * <li><b>Weather</b>: {@link WeatherSource#rainAt} (a weather mod's storms, or vanilla rain), none over deserts and
 * badlands, snow in cold biomes, magenta in the core of a severe thunderstorm or supercell and purple for its hail. With
 * vanilla weather (the same everywhere) the rain is broken into slowly drifting cells so it reads as radar, never
 * heavier than the weather is.</li>
 * </ul>
 * The ground is kept per pixel cell ({@link #CACHE}): it hardly changes, and a pass crosses it once.
 */
public final class AptPainter {

	/** Ground cells kept. */
	static final int CACHE = 32_768;
	/** How long a storm list is used before it is asked for again, ticks, and how far the scan may move meanwhile. */
	private static final int STORM_TTL = 100;
	private static final double STORM_MOVE = 800.0D, STORM_RADIUS = 2600.0D;
	private static final int NO_RAIN = 1 << 8, COLD = 1 << 9;

	private final Map<Long, Integer> ground = new LinkedHashMap<>(4096, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
			return size() > CACHE;
		}
	};
	private List<StormReport> storms = List.of();
	private long stormsAt = Long.MIN_VALUE;
	private double stormX, stormZ;

	/** Forgets the ground (a test changed it). */
	public void forget() {
		ground.clear();
		stormsAt = Long.MIN_VALUE;
	}

	/** The scan line through {@code cx, cz} with right vector {@code rx, rz} at game time {@code now}. Server thread. */
	public byte[] line(ServerLevel level, double cx, double cz, double rx, double rz, long now) {
		WeatherSource source = WeatherSources.get(level);
		if(now - stormsAt > STORM_TTL || Math.hypot(cx - stormX, cz - stormZ) > STORM_MOVE) {
			storms = source.storms(level, BlockPos.containing(cx, level.getSeaLevel(), cz), STORM_RADIUS, false);
			stormsAt = now;
			stormX = cx;
			stormZ = cz;
		}
		boolean local = source.isLocal();
		byte[] out = new byte[AptSwath.PIXELS];
		for(int i = 0; i < AptSwath.PIXELS; i++) {
			double x = AptSwath.x(cx, rx, i), z = AptSwath.z(cz, rz, i);
			int ground = ground(level, (int)Math.floor(x), (int)Math.floor(z));
			int level0 = NONE_LEVEL;
			if((ground & NO_RAIN) == 0) {
				// Read the weather where this pixel falls in its storm's own ragged outline (a storm's rings are round in
				// the weather mod; pulled out of round the same way at every radius, they stay nested).
				double[] at = warp(x, z, now);
				double rain = source.rainAt(level, at[0], at[1], now);
				if(rain > 0.0D) {
					if(!local)
						rain = cells(rain, source.thunderAt(level, x, z, now), x, z, now);
					boolean[] core = core(at[0], at[1], now);
					int asRain = RadarScale.rain(rain, core[0], core[1]);
					// Snow where it is cold, but a storm's returns (a thunderstorm, its core, rain from moderate up) always
					// show as a storm: the snow colours hid the red of a storm reaching over cold ground (2026-11-14).
					boolean stormy = core[0] || core[1] || asRain >= 4 || source.thunderAt(level, at[0], at[1], now) > 0.0D;
					level0 = (ground & COLD) != 0 && !stormy ? RadarScale.snow(rain) : asRain;
				}
			}
			out[i] = level0 == NONE_LEVEL ? (byte)(ground & 0xFF) : RadarScale.colour(level0);
		}
		return out;
	}

	private static final int NONE_LEVEL = RadarScale.NONE;

	/** How far a storm's outline can swell or pull in: its radius times 1 - this to 1 + this, near enough. */
	static final double RAGGED = 0.3D;

	/**
	 * Where to read the weather for a pixel at {@code x, z}: pulled towards the middle of the nearest storm by its
	 * outline's swelling there ({@link #outline}), so every ring of the storm takes the same ragged shape, stretched
	 * along the way it moves. Unchanged far from any storm, blending out from 1.2 to 1.6 radii.
	 */
	double[] warp(double x, double z, long now) {
		StormReport nearest = null;
		StormReport.TrackPoint centre = null;
		double best = SPIRAL_REACH;
		for(StormReport storm : storms) {
			if(storm.worldWide() || !storm.formed(now))
				continue;
			StormReport.TrackPoint at = storm.at(now);
			if(at.radius() <= 1.0D)
				continue;
			double share = Math.hypot(x - at.x(), z - at.z()) / at.radius();
			if(share >= 1.6D && storm.kind() != StormReport.Kind.HURRICANE)
				continue; // Only a hurricane's arms reach that far.
			if(share < best) {
				best = share;
				nearest = storm;
				centre = at;
			}
		}
		if(nearest == null)
			return new double[] { x, z };
		double dx = x - centre.x(), dz = z - centre.z();
		StormReport.TrackPoint later = nearest.at(now + 400L);
		double heading = Math.hypot(later.x() - centre.x(), later.z() - centre.z()) > 1.0D ? Math.atan2(later.z() - centre.z(), later.x() - centre.x())
				: hash((int)nearest.id(), 99) * Math.PI;
		double angle = Math.atan2(dz, dx);
		double f = outline(nearest.id(), angle, heading, now);
		double blend = best < 1.2D ? 1.0D : Math.max(0.0D, (1.6D - best) / 0.4D);
		f = 1.0D + (f - 1.0D) * blend;
		if(nearest.kind() == StormReport.Kind.HURRICANE)
			f *= spiral(nearest.id(), best, angle, now);
		return new double[] { centre.x() + dx / f, centre.z() + dz / f };
	}

	/** How far out a hurricane's spiral arms reach, in radii. */
	static final double SPIRAL_REACH = 2.2D;
	/** A hurricane's spiral arms, how tightly they wind (radians of turn for each e-fold outward), and how far they pull. */
	static final int ARMS = 3;
	static final double TWIST = 2.4D, TAIL = 0.55D;

	/**
	 * A hurricane's spiral bands (2026-11-18, the maintainer: tails coming off the edges showing rotation): the weather
	 * mod gives a hurricane round rings of rain, so here a pixel {@code share} radii from the eye at {@code angle} reads
	 * nearer the eye along {@link #ARMS} arms that wind out from about half the radius (so their rain trails out as
	 * tails, up to {@link #SPIRAL_REACH} radii) and further out between them (clear gaps), turning slowly over
	 * {@code now}. The arms curve against the storm's turn, counter-clockwise seen from above as its wind blows, as a
	 * hurricane's bands do. The eye and eyewall are left as they are. A divisor for the distance, as {@link #outline}.
	 */
	static double spiral(long id, double share, double angle, long now) {
		double start = smooth((share - 0.45D) / 0.4D);
		if(start <= 0.0D)
			return 1.0D;
		double fade = share <= 1.8D ? 1.0D : Math.max(0.0D, (SPIRAL_REACH - share) / (SPIRAL_REACH - 1.8D));
		int seed = (int)(id ^ (id >>> 32));
		double turn = hash(seed, 77) * 2.0D * Math.PI + now / 24_000.0D * 2.0D * Math.PI; // A turn a day.
		// Angle grows outward (z is south, so the wind's turn takes angle down): the arms trail behind the turn.
		double arm = Math.cos(ARMS * (angle - TWIST * Math.log(Math.max(0.05D, share)) + turn));
		return Math.max(0.35D, 1.0D + TAIL * arm * start * fade);
	}

	private static double smooth(double x) {
		x = Math.max(0.0D, Math.min(1.0D, x));
		return x * x * (3.0D - 2.0D * x);
	}

	/**
	 * How far out a storm reaches at {@code angle} (radians) as a share of its round radius: a few lobes from waves of
	 * its own (fixed by its {@code id}, so it keeps its shape line after line and pass after pass), stretched along
	 * {@code heading}. About 1 - {@link #RAGGED} to 1 + {@link #RAGGED}. The lobes creep round slowly over {@code now}
	 * (2026-11-14, the maintainer: storms change shape a little), each wave its own way at its own pace, so two passes a
	 * few hours apart show the same storm a little reshaped, and lines a tick apart agree.
	 */
	static double outline(long id, double angle, double heading, long now) {
		int seed = (int)(id ^ (id >>> 32));
		double r = 1.0D;
		int[] waves = { 2, 3, 5, 7 };
		double[] sizes = { 0.12D, 0.1D, 0.06D, 0.04D };
		for(int k = 0; k < waves.length; k++) {
			double creep = (hash(seed, k + 20) - 0.5D) * 2.0D * Math.PI * now / (12_000.0D + 12_000.0D * hash(seed, k + 30)); // Up to a turn in half a day.
			r += sizes[k] * (0.5D + hash(seed, k)) * Math.sin(waves[k] * angle + hash(seed, k + 10) * 2.0D * Math.PI + creep);
		}
		r *= 1.0D + 0.18D * Math.cos(2.0D * (angle - heading));
		return Math.max(1.0D - RAGGED * 1.6D, Math.min(1.0D + RAGGED * 1.6D, r));
	}

	/** {severe, hail} at {@code x, z}: in the core of a severe storm, and in a supercell's hail core. */
	private boolean[] core(double x, double z, long now) {
		boolean severe = false, hail = false;
		for(StormReport storm : storms) {
			if(storm.worldWide() || !storm.formed(now))
				continue;
			StormReport.Kind kind = storm.kind();
			boolean big = kind == StormReport.Kind.SUPERCELL || kind == StormReport.Kind.TORNADO;
			if(!big && kind != StormReport.Kind.SEVERE_THUNDERSTORM)
				continue;
			StormReport.TrackPoint at = storm.at(now);
			if(at.intensity() < 0.6F)
				continue;
			double d = Math.hypot(x - at.x(), z - at.z());
			if(d < at.radius() * 0.35D)
				severe = true;
			if(big && at.intensity() > 0.7F && d < at.radius() * 0.18D)
				hail = true;
		}
		return new boolean[] { severe, hail };
	}

	/**
	 * Vanilla rain (one level everywhere) broken into cells: two octaves of smooth noise about 600 blocks across that
	 * drift east over the minutes, heavier where it thunders. Never more than {@code rain} plus the thunder's share.
	 */
	static double cells(double rain, double thunder, double x, double z, long now) {
		double drift = now / 20.0D * 1.5D; // Blocks the weather moves: 1.5 a second.
		double n = 0.65D * noise((x - drift) / 600.0D, z / 600.0D) + 0.35D * noise((x - drift * 1.3D) / 190.0D, z / 190.0D + 17.0D);
		double shaped = Math.max(0.0D, Math.min(1.0D, (n - 0.3D) / 0.6D));
		return Math.min(1.0D, rain * (0.15D + 0.75D * shaped) + thunder * 0.35D * shaped * shaped);
	}

	/** Smooth value noise, 0 to 1. */
	static double noise(double x, double z) {
		int x0 = (int)Math.floor(x), z0 = (int)Math.floor(z);
		double fx = x - x0, fz = z - z0;
		fx = fx * fx * (3.0D - 2.0D * fx);
		fz = fz * fz * (3.0D - 2.0D * fz);
		double a = hash(x0, z0), b = hash(x0 + 1, z0), c = hash(x0, z0 + 1), d = hash(x0 + 1, z0 + 1);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
	}

	private static double hash(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) / (double)(1L << 53);
	}

	/** The ground at block {@code x, z}: its map colour in the low byte, with {@link #NO_RAIN} and {@link #COLD}. */
	private int ground(ServerLevel level, int x, int z) {
		long key = (long)Math.floorDiv(x, AptSwath.BLOCKS_PER_PIXEL) << 32 | (Math.floorDiv(z, AptSwath.BLOCKS_PER_PIXEL) & 0xFFFFFFFFL);
		Integer known = ground.get(key);
		if(known != null)
			return known;
		int value = level.hasChunk(x >> 4, z >> 4) ? loaded(level, x, z) : fromBiome(level, x, z);
		ground.put(key, value);
		return value;
	}

	/**
	 * A loaded column, coloured as unloaded ground is (2026-11-16, the maintainer: every leaf and grass block of the
	 * loaded chunks looked silly beside the plain ground round them): its biome's colour, but real water, ice and snow
	 * where they are on top, and shaded by the slope to the north as a map is.
	 */
	private static int loaded(ServerLevel level, int x, int z) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		BlockPos pos = new BlockPos(x, top, z);
		Holder<Biome> biome = level.getBiome(pos);
		int flags = flags(biome, pos);
		BlockState state = level.getBlockState(pos);
		MapColor block = level.getFluidState(pos).is(FluidTags.WATER) ? MapColor.WATER : state.getMapColor(level, pos);
		int packed;
		if(block == MapColor.WATER || block == MapColor.ICE || block == MapColor.SNOW)
			packed = block.getPackedId(MapColor.Brightness.LOW) & 0xFF;
		else
			packed = biomeColour(biome, (flags & COLD) != 0) & 0xFF;
		boolean plain = (packed & 3) == MapColor.Brightness.LOW.id; // Biomes drawn darker or lighter keep their own shade.
		if(block != MapColor.WATER && plain && level.hasChunk(x >> 4, (z - AptSwath.BLOCKS_PER_PIXEL) >> 4)) {
			int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - AptSwath.BLOCKS_PER_PIXEL) - 1;
			MapColor.Brightness shade = top > north + 1 ? MapColor.Brightness.NORMAL : top < north - 1 ? MapColor.Brightness.LOWEST : MapColor.Brightness.LOW;
			packed = packed & ~3 | shade.id;
		}
		return packed | flags;
	}

	/**
	 * Where the biome source is asked for unloaded ground, block y: high over any land (2026-11-16). At sea level, under
	 * hills and mountains it gave cave biomes (lush and dripstone caves), not what is on top.
	 */
	static final int BIOME_Y = 256;

	/** The ground's map colour id at {@code x, z} as a scan line paints it, no weather and not kept (for tests). */
	public static int groundColour(ServerLevel level, int x, int z) {
		return (level.hasChunk(x >> 4, z >> 4) ? loaded(level, x, z) : fromBiome(level, x, z)) & 0xFF;
	}

	/** Unloaded ground: the colour of its biome from the biome source (cheap; nothing is generated). */
	private static int fromBiome(ServerLevel level, int x, int z) {
		var sampler = level.getChunkSource().randomState().sampler();
		Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource()
				.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.min(BIOME_Y, level.getMaxBuildHeight() - 1)), QuartPos.fromBlock(z), sampler);
		BlockPos pos = new BlockPos(x, level.getSeaLevel(), z);
		int flags = flags(biome, pos);
		return biomeColour(biome, (flags & COLD) != 0) & 0xFF | flags;
	}

	private static int flags(Holder<Biome> biome, BlockPos pos) {
		int flags = 0;
		if(!biome.value().hasPrecipitation())
			flags |= NO_RAIN;
		else if(biome.value().coldEnoughToSnow(pos))
			flags |= COLD;
		return flags;
	}

	/** A biome as seen from orbit, in map colours a step darker than a map's. */
	static byte biomeColour(Holder<Biome> biome, boolean cold) {
		MapColor.Brightness b = MapColor.Brightness.LOW;
		if(biome.is(BiomeTags.IS_DEEP_OCEAN))
			return (cold ? MapColor.ICE : MapColor.WATER).getPackedId(MapColor.Brightness.LOWEST);
		if(biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER))
			return (cold ? MapColor.ICE : MapColor.WATER).getPackedId(b);
		if(cold)
			return MapColor.SNOW.getPackedId(b);
		if(biome.is(BiomeTags.IS_BEACH) || biome.is(Biomes.DESERT))
			return MapColor.SAND.getPackedId(b);
		if(biome.is(BiomeTags.IS_BADLANDS))
			return MapColor.COLOR_ORANGE.getPackedId(b);
		if(biome.is(BiomeTags.IS_SAVANNA))
			return MapColor.TERRACOTTA_YELLOW.getPackedId(MapColor.Brightness.NORMAL);
		if(biome.is(BiomeTags.IS_JUNGLE))
			return MapColor.PLANT.getPackedId(MapColor.Brightness.LOWEST);
		if(biome.is(BiomeTags.IS_TAIGA))
			return MapColor.COLOR_GREEN.getPackedId(MapColor.Brightness.LOWEST);
		if(biome.is(BiomeTags.IS_FOREST))
			return MapColor.PLANT.getPackedId(b);
		if(biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP))
			return MapColor.COLOR_GREEN.getPackedId(b);
		if(biome.is(Biomes.MUSHROOM_FIELDS))
			return MapColor.COLOR_PURPLE.getPackedId(MapColor.Brightness.LOWEST);
		if(biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL))
			return MapColor.STONE.getPackedId(b);
		return MapColor.GRASS.getPackedId(b);
	}
}
