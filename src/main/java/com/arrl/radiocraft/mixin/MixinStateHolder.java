package com.arrl.radiocraft.mixin;

import com.arrl.radiocraft.common.blocks.SharedStateNeighbours;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.google.common.collect.Table;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * The wire blocks' states share one neighbour lookup per block instead of a table each ({@link SharedStateNeighbours},
 * 2026-11-16). Not applied when FerriteCore is installed ({@link RadiocraftMixinPlugin}): it already does this for
 * every block.
 */
@Mixin(StateHolder.class)
public abstract class MixinStateHolder<O, S> {

	@Shadow
	@Final
	protected O owner;
	@Shadow
	@Final
	private Reference2ObjectArrayMap<Property<?>, Comparable<?>> values;
	@Shadow
	private Table<Property<?>, Comparable<?>, S> neighbours;

	@Inject(method = "populateNeighbours", at = @At("HEAD"), cancellable = true)
	private void radiocraft$shareWireNeighbours(Map<Map<Property<?>, Comparable<?>>, S> possible, CallbackInfo ci) {
		if(neighbours != null || !(owner instanceof WireBlock) || values.isEmpty())
			return;
		neighbours = SharedStateNeighbours.of(possible, values);
		ci.cancel();
	}
}
