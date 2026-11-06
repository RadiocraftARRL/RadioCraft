package com.arrl.radiocraft.compat;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.WeatherAlertGeneratorBlockEntity;
import com.arrl.radiocraft.common.blockentities.WeatherAlertReceiverBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

/**
 * Jade lines for the weather alert blocks: the generator's status (why it is not on the air: no APRS station, too few
 * weather beacons or too close together, no antenna or power; or its alerts) and the receiver's battery, station and
 * last alert.
 */
public enum WeatherAlertProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		for(Tag line : accessor.getServerData().getList("lines", Tag.TAG_STRING)) {
			Component component = Component.Serializer.fromJson(line.getAsString(), accessor.getLevel().registryAccess());
			if(component != null)
				tooltip.add(component);
		}
	}

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		List<Component> lines;
		if(accessor.getBlockEntity() instanceof WeatherAlertGeneratorBlockEntity generator)
			lines = generator.statusLines();
		else if(accessor.getBlockEntity() instanceof WeatherAlertReceiverBlockEntity receiver)
			lines = receiver.statusLines();
		else
			return;
		ListTag list = new ListTag();
		for(Component line : lines)
			list.add(StringTag.valueOf(Component.Serializer.toJson(line, accessor.getLevel().registryAccess())));
		data.put("lines", list);
	}

	@Override
	public ResourceLocation getUid() {
		return Radiocraft.id("weather_alert");
	}
}
