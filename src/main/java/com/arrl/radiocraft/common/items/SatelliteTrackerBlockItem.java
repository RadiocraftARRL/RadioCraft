package com.arrl.radiocraft.common.items;

import com.arrl.radiocraft.Radiocraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/** The satellite tracking antenna's item (2026-11-12), drawn from the block's GeckoLib model. */
public class SatelliteTrackerBlockItem extends BlockItem implements GeoItem {

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public SatelliteTrackerBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		consumer.accept(new GeoRenderProvider() {
			private GeoItemRenderer<SatelliteTrackerBlockItem> renderer;

			@Override
			public @Nullable BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
				if(renderer == null)
					renderer = new GeoItemRenderer<>(new DefaultedBlockGeoModel<>(Radiocraft.id("satellite_tracker")));
				return renderer;
			}
		});
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
