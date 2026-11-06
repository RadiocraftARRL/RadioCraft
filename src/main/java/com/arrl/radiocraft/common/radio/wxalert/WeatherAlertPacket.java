package com.arrl.radiocraft.common.radio.wxalert;

import com.arrl.radiocraft.common.radio.data.AprsIcon;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The weather alert broadcast on {@link #FREQUENCY_HZ}: a {@link TncPacket.Kind#WX_ALERT} packet from a generator, its
 * position the generator's, its text the alerts a line each ({@link WeatherAlert#encode}, joined by
 * {@link TncPacket#SOLAR_LINE_BREAK}). With no alerts it is a routine broadcast that lets receivers find their nearest
 * generator.
 */
public final class WeatherAlertPacket {

	/** The weather radio channel the generators send on and the receivers hear (WX6). */
	public static final float FREQUENCY_HZ = 162_500_000.0F;
	/** How far off the channel a receiver still hears, Hz. */
	public static final float TOLERANCE_HZ = 1000.0F;

	private WeatherAlertPacket() {}

	/** A broadcast from {@code from} at {@code generator} with {@code alerts} (the worst first; as many as fit). */
	public static TncPacket packet(long id, String from, BlockPos generator, List<WeatherAlert> alerts) {
		StringBuilder text = new StringBuilder();
		for(WeatherAlert alert : alerts) {
			String line = alert.encode();
			if(text.length() + line.length() + 1 > TncPacket.SOLAR_TEXT_MAX)
				break;
			if(!text.isEmpty())
				text.append(TncPacket.SOLAR_LINE_BREAK);
			text.append(line);
		}
		return new TncPacket(id, TncPacket.Kind.WX_ALERT, TncPacket.sanitizeCallsign(from), "", text.toString(), generator.immutable(),
				AprsIcon.WX.name(), 0, ItemStack.EMPTY);
	}

	/** The alerts in a broadcast; empty for a routine one or a packet that is not a broadcast. */
	public static List<WeatherAlert> alerts(TncPacket packet) {
		if(packet.kind() != TncPacket.Kind.WX_ALERT)
			return List.of();
		List<WeatherAlert> out = new ArrayList<>();
		for(String line : packet.solarLines()) {
			WeatherAlert alert = WeatherAlert.decode(line);
			if(alert != null)
				out.add(alert);
		}
		return out;
	}

	/** Whether {@code hz} is the weather alert channel. */
	public static boolean onChannel(float hz) {
		return Math.abs(hz - FREQUENCY_HZ) <= TOLERANCE_HZ;
	}
}
