package com.arrl.radiocraft.compat.anotherweathermod;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.entities.AntennaWire;
import com.arrl.radiocraft.common.init.RadiocraftTags;
import com.itcmd.anotherweathermod.api.event.WindDamageEvent;
import com.itcmd.anotherweathermod.api.event.WindDamageEvent.DamageClass;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * What storm winds do to a station (2026-11-13, the maintainer: "Wire antennas make sense to break fairly easily, but
 * mounted antennas like j-poles, the btv, etc shouldnt break quick"; coax "shouldnt break unless its an E5"):
 * <ul>
 * <li>A wire antenna's wire snaps off its holder from a category 3 hurricane's wind ({@link #WIRE_FROM}), more often the
 * harder it blows; the holder stays.</li>
 * <li>A mounted antenna (J-pole, BTV, yagi, dish...) is in Another Weather Mod's {@code wind_damage/resilient} class:
 * AWM offers it only in a category 5 hurricane or EF4+ tornado, and seldom; then it comes down, dropped as an item.</li>
 * <li>Coax is in AWM's {@code wind_resistant_ef5} class: only a category 5 or EF5, and AWM breaks it itself.</li>
 * <li>Any other antenna part (tuners, baluns, dummy loads) stays.</li>
 * </ul>
 */
final class AwmWindDamage {

	/** Wires start to snap at a category 3 hurricane's wind, metres per second, and snap most often at {@link #WIRE_FULL}. */
	static final double WIRE_FROM = 50;
	static final double WIRE_FULL = 75;
	/** The most likely a holder's wire is to snap each time the wind picks it. */
	static final double WIRE_MAX_CHANCE = 0.25;

	private AwmWindDamage() {
	}

	/** Chance a wire snaps each time the wind picks its holder, at {@code windSpeed} metres per second. */
	static double wireChance(double windSpeed) {
		return Math.min(1, Math.max(0, (windSpeed - WIRE_FROM) / (WIRE_FULL - WIRE_FROM))) * WIRE_MAX_CHANCE;
	}

	static void onWindDamage(WindDamageEvent event) {
		BlockState state = event.getState();
		boolean holder = state.is(RadiocraftTags.Blocks.ANTENNA_WIRE_HOLDERS);
		if(!holder && !state.is(RadiocraftTags.Blocks.ANTENNA_BLOCKS))
			return;
		if(event.getDamageClass() == DamageClass.EXTREME)
			return; // Coax: AWM's own rare category 5 / EF5 damage.
		event.setCanceled(true); // Ours to decide, whatever else the block is.
		if(!RadiocraftServerConfig.WIND_ANTENNA_DAMAGE_ENABLED.get())
			return;
		List<? extends Object> wires = AntennaWire.getWires(event.getLevel(), event.getPos());
		if(!wires.isEmpty()) {
			if(event.getLevel().random.nextDouble() < wireChance(event.getWindSpeed())
					&& wires.get(event.getLevel().random.nextInt(wires.size())) instanceof Entity wire)
				wire.hurt(event.getLevel().damageSources().generic(), 1); // Snaps: the wire drops, the holder stays.
			return;
		}
		if(event.getDamageClass() == DamageClass.RESILIENT) // AWM has already rolled its rare chance.
			event.getLevel().destroyBlock(event.getPos(), true);
	}
}
