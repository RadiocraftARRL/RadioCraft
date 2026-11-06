package com.arrl.radiocraft.common.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The one criterion every RadioCraft advancement made in server code uses ({@code radiocraft:event}): its condition is
 * an event id ({@code "first_contact"}), and {@link RadiocraftAdvancements#grant} fires it. One trigger for all of them
 * instead of a class per advancement; vanilla criteria are used where they fit (placing a block, holding an item).
 * Firing it for a player who already has the advancement costs a map lookup (the trigger keeps listeners only for
 * advancements not yet done).
 */
public final class RadioEventTrigger extends SimpleCriterionTrigger<RadioEventTrigger.TriggerInstance> {

	@Override
	public @NotNull Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	/** Fires event {@code event} for {@code player}. Server thread. */
	public void trigger(ServerPlayer player, String event) {
		trigger(player, instance -> instance.event().equals(event));
	}

	/** The condition: the event's id. */
	public record TriggerInstance(Optional<ContextAwarePredicate> player, String event) implements SimpleCriterionTrigger.SimpleInstance {

		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
				Codec.STRING.fieldOf("event").forGetter(TriggerInstance::event)
		).apply(instance, TriggerInstance::new));

		/** The criterion for event {@code event}, for datagen. */
		public static Criterion<TriggerInstance> of(String event) {
			return RadiocraftAdvancements.EVENT.get().createCriterion(new TriggerInstance(Optional.empty(), event));
		}
	}
}
