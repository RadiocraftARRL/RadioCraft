package com.arrl.radiocraft.api.antenna;

import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.StaticAntenna;
import com.arrl.radiocraft.common.radio.antenna.data.EmptyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.types.NonDirectionalAntennaType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * An omnidirectional antenna type described by numbers instead of code: where it is (a block, or any test of the
 * level), and for each band or frequency range it covers, its SWR and gain. Everything else (line of sight, skip, solar
 * weather, aurora, terrain, the coax and tuners) is RadioCraft's usual propagation, as for the stock verticals.
 *
 * <pre>{@code
 * SimpleAntennaType groundPlane = AntennaTypes.registerType(SimpleAntennaType.builder(id("ground_plane"))
 *         .block(MY_GROUND_PLANE)
 *         .band(RadioBands.LOW_BAND, 1.2D, 0.0D)   // SWR 1.2, 0 dBi on 30-50 MHz
 *         .band("10m", 2.5D, -1.0D)
 *         .build());
 * }</pre>
 *
 * <p>Frequencies outside every listed band and range see {@link Builder#outsideSwr} (10 by default: the radio
 * hardly gets a signal out, and an auto tuner may or may not match it) and 0 dBi. Bands are looked up by name when the
 * SWR is asked, so a band's edges follow the server config.</p>
 *
 * <p>For a directional antenna, or one whose SWR depends on how it is built (wire lengths), implement
 * {@link IAntennaType} yourself.</p>
 *
 * @since 1.12.0
 */
public final class SimpleAntennaType extends NonDirectionalAntennaType<EmptyAntennaData> {

	/**
	 * What the antenna does on one band or frequency range.
	 *
	 * @param bandName the band's name, or null for a plain range
	 * @param minHz the bottom of the range in Hz (ignored for a band)
	 * @param maxHz the top of the range in Hz (ignored for a band)
	 * @param swr the SWR there, at least 1
	 * @param gainDbi the gain there in dBi (0 is a quarter-wave vertical; negative for a short whip)
	 */
	public record Coverage(String bandName, double minHz, double maxHz, double swr, double gainDbi) {

		/**
		 * Whether {@code hz} is in this band or range.
		 *
		 * @param hz the frequency in Hz
		 * @return true if covered
		 */
		public boolean covers(double hz) {
			if(bandName == null)
				return hz >= minHz && hz <= maxHz;
			Band band = Band.getBand(bandName);
			return band != null && band.contains(hz);
		}
	}

	private final List<Coverage> coverage;
	private final double outsideSwr;
	private final BiPredicate<Level, BlockPos> matcher;
	private final double lightningFactor;

	private SimpleAntennaType(Builder builder) {
		super(builder.id, 0.0D, 0.0D, builder.lineOfSight, builder.skip); // Gain is per band, applied below.
		this.coverage = List.copyOf(builder.coverage);
		this.outsideSwr = builder.outsideSwr;
		this.matcher = builder.matcher;
		this.lightningFactor = builder.lightningFactor;
	}

	/**
	 * Starts describing a type.
	 *
	 * @param id the type's id, in your mod's namespace
	 * @return a builder
	 */
	public static Builder builder(ResourceLocation id) {
		return new Builder(id);
	}

	/**
	 * What the antenna does at {@code hz}: the first listed band or range that covers it.
	 *
	 * @param hz the frequency in Hz
	 * @return the coverage, or null outside all of them
	 */
	public Coverage coverageAt(double hz) {
		for(Coverage c : coverage)
			if(c.covers(hz))
				return c;
		return null;
	}

	/**
	 * The gain at {@code hz} in dBi: the covering band's, 0 outside.
	 *
	 * @param hz the frequency in Hz
	 * @return the gain in dBi
	 */
	public double gainDbiAt(double hz) {
		Coverage c = coverageAt(hz);
		return c == null ? 0.0D : c.gainDbi();
	}

	private double gainLinear(double hz) {
		return Math.pow(10.0D, gainDbiAt(hz) / 10.0D);
	}

	/**
	 * Finds an antenna of this type at {@code pos}: one when the builder's block or matcher says so.
	 *
	 * @param level the level
	 * @param pos the feed point block
	 * @return the antenna, or null
	 */
	@Override
	public StaticAntenna<EmptyAntennaData> match(Level level, BlockPos pos) {
		return matcher.test(level, pos) ? new StaticAntenna<>(this, pos, level) : null;
	}

	/**
	 * The listed SWR where the antenna covers {@code frequencyHertz}, else {@link Builder#outsideSwr}.
	 *
	 * @param data unused
	 * @param frequencyHertz the frequency in Hz
	 * @return the SWR
	 */
	@Override
	public double getSWR(EmptyAntennaData data, float frequencyHertz) {
		Coverage c = coverageAt(frequencyHertz);
		return c == null ? outsideSwr : c.swr();
	}

	/**
	 * RadioCraft's propagation times this antenna's gain on the packet's frequency.
	 *
	 * @param packet the signal going out
	 * @param data unused
	 * @param destination where it goes
	 * @param isCW true for CW (it carries further)
	 * @return the strength multiplier
	 */
	@Override
	public double getTransmitEfficiency(IAntennaPacket packet, EmptyAntennaData data, BlockPos destination, boolean isCW) {
		return super.getTransmitEfficiency(packet, data, destination, isCW) * gainLinear(packet.getFrequency());
	}

	/**
	 * The arriving strength times this antenna's gain on the packet's frequency.
	 *
	 * @param packet the signal arriving
	 * @param data unused
	 * @param pos where it came from
	 * @return the strength
	 */
	@Override
	public double getReceiveEfficiency(IAntennaPacket packet, EmptyAntennaData data, BlockPos pos) {
		return super.getReceiveEfficiency(packet, data, pos) * gainLinear(packet.getFrequency());
	}

	/**
	 * A fresh, empty data object (this type saves nothing).
	 *
	 * @return the data
	 */
	@Override
	public EmptyAntennaData getDefaultData() {
		return new EmptyAntennaData();
	}

	/**
	 * The share of lightning strikes on this antenna that do damage, as set on the builder (1 by default).
	 *
	 * @return the factor
	 */
	@Override
	public double lightningDamageFactor() {
		return lightningFactor;
	}

	/** Describes a {@link SimpleAntennaType}. Every setter returns the builder. */
	public static final class Builder {

		private final ResourceLocation id;
		private final List<Coverage> coverage = new ArrayList<>();
		private double outsideSwr = 10.0D;
		private double lineOfSight = 1.0D;
		private double skip = 1.0D;
		private double lightningFactor = 1.0D;
		private BiPredicate<Level, BlockPos> matcher = null;

		private Builder(ResourceLocation id) {
			this.id = Objects.requireNonNull(id, "id");
		}

		/**
		 * The antenna is any {@code block}: one block is the whole antenna (and its feed point).
		 *
		 * @param block the block, as a supplier so it can be a registry holder
		 * @return this builder
		 */
		public Builder block(Supplier<? extends Block> block) {
			Objects.requireNonNull(block, "block");
			this.matcher = (level, pos) -> level.getBlockState(pos).is(block.get());
			return this;
		}

		/**
		 * The antenna is wherever {@code matcher} says, asked with the feed point block (a structure around it, say).
		 * Called on the server thread when the antenna block loads or the build changes; keep it cheap.
		 *
		 * @param matcher the test
		 * @return this builder
		 */
		public Builder matcher(BiPredicate<Level, BlockPos> matcher) {
			this.matcher = Objects.requireNonNull(matcher, "matcher");
			return this;
		}

		/**
		 * The antenna works on band {@code bandName} (a stock band such as {@code "20m"} or
		 * {@link com.arrl.radiocraft.api.radio.RadioBands#LOW_BAND}, or one another mod registered).
		 *
		 * @param bandName the band's name
		 * @param swr the SWR across the band, at least 1
		 * @param gainDbi the gain in dBi
		 * @return this builder
		 */
		public Builder band(String bandName, double swr, double gainDbi) {
			coverage.add(new Coverage(Objects.requireNonNull(bandName, "bandName"), 0.0D, 0.0D, Math.max(1.0D, swr), gainDbi));
			return this;
		}

		/**
		 * The antenna works between {@code minHz} and {@code maxHz} (part of a band, or across several).
		 *
		 * @param minHz the bottom in Hz
		 * @param maxHz the top in Hz
		 * @param swr the SWR there, at least 1
		 * @param gainDbi the gain in dBi
		 * @return this builder
		 */
		public Builder range(double minHz, double maxHz, double swr, double gainDbi) {
			coverage.add(new Coverage(null, Math.min(minHz, maxHz), Math.max(minHz, maxHz), Math.max(1.0D, swr), gainDbi));
			return this;
		}

		/**
		 * The SWR everywhere the antenna does not cover (10 by default).
		 *
		 * @param swr the SWR, at least 1
		 * @return this builder
		 */
		public Builder outsideSwr(double swr) {
			this.outsideSwr = Math.max(1.0D, swr);
			return this;
		}

		/**
		 * How well it does line of sight (ground wave), against a quarter-wave vertical's 1.2 or a J-pole's 1.
		 *
		 * @param efficiency the multiplier (1 by default)
		 * @return this builder
		 */
		public Builder lineOfSight(double efficiency) {
			this.lineOfSight = efficiency;
			return this;
		}

		/**
		 * How well it does skip (sky wave), against a quarter-wave vertical's 0.7. VHF antennas usually set 0.
		 *
		 * @param efficiency the multiplier (1 by default)
		 * @return this builder
		 */
		public Builder skip(double efficiency) {
			this.skip = efficiency;
			return this;
		}

		/**
		 * The share of lightning strikes that do damage, against an ordinary antenna's 1 (a DC-grounded antenna, less).
		 *
		 * @param factor 0 to 1
		 * @return this builder
		 */
		public Builder lightningDamageFactor(double factor) {
			this.lightningFactor = Math.max(0.0D, factor);
			return this;
		}

		/**
		 * Makes the type. Register it with {@link AntennaTypes#registerType} (or {@link AntennaTypes#registerPriorityType}).
		 *
		 * @return the type
		 * @throws IllegalStateException if neither a block nor a matcher was given
		 */
		public SimpleAntennaType build() {
			if(matcher == null)
				throw new IllegalStateException("SimpleAntennaType " + id + " needs a block or a matcher");
			return new SimpleAntennaType(this);
		}
	}
}
