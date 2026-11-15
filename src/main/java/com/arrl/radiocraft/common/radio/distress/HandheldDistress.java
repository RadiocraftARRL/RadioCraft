package com.arrl.radiocraft.common.radio.distress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * The beacon alert a commercial VHF handheld has heard (2026-11-11), the {@code radiocraft:handheld_distress} data
 * component: its built-in receiver on 161.975 MHz listens whatever channel the radio is on, as the mobile's does
 * ({@link com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity}), and the screen scrolls
 * {@link DistressAlert#sosRx} until the holder dismisses it. Kept on the item, so it is still there when the screen
 * is opened again, after a relog, or with the radio in a charger. A handheld remembers one alert: the last new one.
 * No game classes beyond the codecs, so it unit tests without Minecraft.
 *
 * @param alert     The alert as it came over the air ({@link DistressAlert#encode}), its repeats keeping the position
 *                  and state current.
 * @param dismissed The holder took it off the screen: repeats of the same alert do not bring it back.
 */
public record HandheldDistress(String alert, boolean dismissed) {

	public HandheldDistress {
		alert = alert == null ? "" : alert;
	}

	public static final Codec<HandheldDistress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("alert", "").forGetter(HandheldDistress::alert),
			Codec.BOOL.optionalFieldOf("dismissed", false).forGetter(HandheldDistress::dismissed)
	).apply(instance, HandheldDistress::new));

	public static final StreamCodec<ByteBuf, HandheldDistress> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, HandheldDistress::alert,
			ByteBufCodecs.BOOL, HandheldDistress::dismissed,
			HandheldDistress::new);

	/** What hearing a burst did to the handheld's alert. */
	public enum Change {
		/** Nothing to change (an alert that is over, heard for the first time; the same burst again). */
		NONE,
		/** A new active alert: shown, with the alarm. */
		NEW,
		/** The alert it has moved on (a new position, acknowledged, cancelled or spent): kept current, no alarm. */
		UPDATE
	}

	/** The alert, null if the stored line does not decode. */
	@Nullable
	public DistressAlert decoded() {
		return DistressAlert.decode(alert);
	}

	/** The alert while it is on the screen (not dismissed), else null. */
	@Nullable
	public DistressAlert shown() {
		return dismissed ? null : decoded();
	}

	/** The same alert, taken off the screen. */
	public HandheldDistress dismiss() {
		return new HandheldDistress(alert, true);
	}

	/** What hearing {@code heard} does to {@code was} (null: none yet). */
	public static Change change(@Nullable HandheldDistress was, DistressAlert heard) {
		DistressAlert had = was == null ? null : was.decoded();
		if(had == null || had.id() != heard.id())
			return heard.state() == DistressAlert.State.ACTIVE ? Change.NEW : Change.NONE; // One that is already over is nothing to call the holder for.
		if(had.equals(heard) || !(heard.state() == had.state() || heard.state().follows(had.state())))
			return Change.NONE; // The same burst, or one older than what it has (it never goes back to ACTIVE).
		return Change.UPDATE;
	}

	/** {@code was} after hearing {@code heard} ({@code was} itself, or null, when nothing changes). */
	@Nullable
	public static HandheldDistress hear(@Nullable HandheldDistress was, DistressAlert heard) {
		return switch(change(was, heard)) {
			case NEW -> new HandheldDistress(heard.encode(), false);
			case UPDATE -> new HandheldDistress(heard.encode(), was != null && was.dismissed());
			case NONE -> was;
		};
	}
}
