package com.arrl.radiocraft.common.radio.commercial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/**
 * A commercial frequency license (maintainer, 2026-11-10; see {@code doc/Plan-FireAlarmScadaLicensing.md}): a Part 90
 * style callsign, the band, the frequency it grants (and from the first upgrade a paired input, for a repeater), who it
 * was issued to, its tier and the day it was issued. It rides on the license item as a data component and is kept in
 * {@link CommercialLicenses} so frequencies and callsigns stay unique.
 * <p>
 * Subscribers are mobiles, portables and bases together (a deliberate simplification): {@link #subscribers()} is 10 at
 * tier 0 and doubles with each upgrade. Bases (repeaters, SCADA masters, fire alarm controllers) start at one with the
 * first upgrade and double from then on. Nothing is tracked per radio: the FCC app counts what it hears.
 *
 * @param callsign  The license's callsign ({@code WQAB123}).
 * @param band      {@link com.arrl.radiocraft.common.radio.Band#VHF_COMMERCIAL} or {@code LOW_BAND}.
 * @param outputHz  The frequency granted, in Hz.
 * @param inputHz   The paired input frequency in Hz (a repeater listens there), or 0 before the first upgrade.
 * @param holder    The name of the player it was issued to.
 * @param holderId  Their UUID, as a string (empty for one granted to a name only).
 * @param tier      Upgrades crafted into it, 0 to {@link #MAX_TIER}.
 * @param issued    The day it was issued, {@code 2026-11-10}.
 * @param copy      True on a copy (crafted from the original and an empty map): it shows COPY and is no license to
 *                  program radios with.
 * @param telemetryHz A third frequency it grants, in Hz: a commercial satellite's telemetry beacon (2026-11-12); 0 for
 *                  none (every other license).
 */
public record CommercialLicense(String callsign, String band, float outputHz, float inputHz, String holder, String holderId,
								int tier, String issued, boolean copy, float telemetryHz) {

	/** Subscribers at tier 0. */
	public static final int BASE_SUBSCRIBERS = 10;
	/** The most upgrades a license takes (640 subscribers, 32 bases). */
	public static final int MAX_TIER = 6;
	/** How near a frequency must be to one a license grants to count as it: half a 12.5 kHz channel. */
	public static final float MATCH_HZ = 6_250.0F;

	/** A license with no third frequency (all but a commercial satellite's). */
	public CommercialLicense(String callsign, String band, float outputHz, float inputHz, String holder, String holderId, int tier, String issued, boolean copy) {
		this(callsign, band, outputHz, inputHz, holder, holderId, tier, issued, copy, 0.0F);
	}

	public CommercialLicense {
		callsign = callsign == null ? "" : callsign.toUpperCase(Locale.ROOT);
		band = band == null ? "" : band;
		holder = holder == null ? "" : holder;
		holderId = holderId == null ? "" : holderId;
		issued = issued == null ? "" : issued;
		tier = Math.max(0, Math.min(MAX_TIER, tier));
		telemetryHz = Math.max(0.0F, telemetryHz);
	}

	/** Mobiles, portables and bases the license allows. */
	public int subscribers() {
		return BASE_SUBSCRIBERS << tier;
	}

	/** Bases (repeaters, SCADA masters, fire alarm controllers) the license allows: none until the first upgrade. */
	public int bases() {
		return tier == 0 ? 0 : 1 << (tier - 1);
	}

	/** True if {@code hz} is the frequency this license grants, its paired input or its telemetry frequency. */
	public boolean covers(float hz) {
		return hz > 0 && (Math.abs(hz - outputHz) <= MATCH_HZ || inputHz > 0 && Math.abs(hz - inputHz) <= MATCH_HZ
				|| telemetryHz > 0 && Math.abs(hz - telemetryHz) <= MATCH_HZ);
	}

	/** True if this is the original (not a copy). */
	public boolean original() {
		return !copy;
	}

	public CommercialLicense withTier(int newTier, float newInputHz) {
		return new CommercialLicense(callsign, band, outputHz, newInputHz, holder, holderId, newTier, issued, copy, telemetryHz);
	}

	public CommercialLicense asCopy() {
		return new CommercialLicense(callsign, band, outputHz, inputHz, holder, holderId, tier, issued, true, telemetryHz);
	}

	/** The same license granting {@code hz} as its telemetry frequency too (a commercial satellite's). */
	public CommercialLicense withTelemetry(float hz) {
		return new CommercialLicense(callsign, band, outputHz, inputHz, holder, holderId, tier, issued, copy, hz);
	}

	/** True if {@code other} is the same license at the same tier (a copy and its original count as the same). */
	public boolean sameGrant(CommercialLicense other) {
		return callsign.equals(other.callsign) && tier == other.tier && outputHz == other.outputHz && inputHz == other.inputHz && telemetryHz == other.telemetryHz;
	}

	public static final Codec<CommercialLicense> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("callsign").forGetter(CommercialLicense::callsign),
			Codec.STRING.optionalFieldOf("band", "").forGetter(CommercialLicense::band),
			Codec.FLOAT.optionalFieldOf("output", 0.0F).forGetter(CommercialLicense::outputHz),
			Codec.FLOAT.optionalFieldOf("input", 0.0F).forGetter(CommercialLicense::inputHz),
			Codec.STRING.optionalFieldOf("holder", "").forGetter(CommercialLicense::holder),
			Codec.STRING.optionalFieldOf("holder_id", "").forGetter(CommercialLicense::holderId),
			Codec.INT.optionalFieldOf("tier", 0).forGetter(CommercialLicense::tier),
			Codec.STRING.optionalFieldOf("issued", "").forGetter(CommercialLicense::issued),
			Codec.BOOL.optionalFieldOf("copy", false).forGetter(CommercialLicense::copy),
			Codec.FLOAT.optionalFieldOf("telemetry", 0.0F).forGetter(CommercialLicense::telemetryHz)
	).apply(instance, CommercialLicense::new));

	public static final StreamCodec<ByteBuf, CommercialLicense> STREAM_CODEC = StreamCodec.of(
			(buf, l) -> {
				ByteBufCodecs.stringUtf8(16).encode(buf, l.callsign);
				ByteBufCodecs.stringUtf8(32).encode(buf, l.band);
				buf.writeFloat(l.outputHz);
				buf.writeFloat(l.inputHz);
				ByteBufCodecs.stringUtf8(64).encode(buf, l.holder);
				ByteBufCodecs.stringUtf8(40).encode(buf, l.holderId);
				buf.writeByte(l.tier);
				ByteBufCodecs.stringUtf8(16).encode(buf, l.issued);
				buf.writeBoolean(l.copy);
				buf.writeFloat(l.telemetryHz);
			},
			buf -> new CommercialLicense(ByteBufCodecs.stringUtf8(16).decode(buf), ByteBufCodecs.stringUtf8(32).decode(buf),
					buf.readFloat(), buf.readFloat(), ByteBufCodecs.stringUtf8(64).decode(buf), ByteBufCodecs.stringUtf8(40).decode(buf),
					buf.readByte(), ByteBufCodecs.stringUtf8(16).decode(buf), buf.readBoolean(), buf.readFloat()));

	/** As NBT, for {@link CommercialLicenses} and computer replies. */
	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("callsign", callsign);
		tag.putString("band", band);
		tag.putFloat("output", outputHz);
		tag.putFloat("input", inputHz);
		tag.putString("holder", holder);
		tag.putString("holder_id", holderId);
		tag.putInt("tier", tier);
		tag.putString("issued", issued);
		tag.putBoolean("copy", copy);
		if(telemetryHz > 0)
			tag.putFloat("telemetry", telemetryHz);
		tag.putInt("subscribers", subscribers());
		tag.putInt("bases", bases());
		return tag;
	}

	public static CommercialLicense fromTag(CompoundTag tag) {
		return new CommercialLicense(tag.getString("callsign"), tag.getString("band"), tag.getFloat("output"), tag.getFloat("input"),
				tag.getString("holder"), tag.getString("holder_id"), tag.getInt("tier"), tag.getString("issued"), tag.getBoolean("copy"),
				tag.getFloat("telemetry"));
	}
}
