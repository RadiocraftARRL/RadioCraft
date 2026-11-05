package com.arrl.radiocraft.common.fcc;

import com.arrl.radiocraft.common.datacomponents.FccBadge;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.compat.curios.CuriosCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who holds an FCC badge (1.12 open-question fixes), so the Radio Moderation Computer can mark them. The badge stays
 * decorative otherwise: this gives it no powers. A player holds one if a badge was ever issued to them with
 * {@code /radiocraft fcc badge} (kept in the overworld's {@code radiocraft_fcc_badges} data), or if they carry, or wear in
 * Curios, a badge issued to them now (a badge from before this list was kept).
 */
public final class FccBadgeHolders extends SavedData {

	public static final String SAVE_NAME = "radiocraft_fcc_badges";

	private final Set<UUID> issued = new LinkedHashSet<>();

	public static SavedData.Factory<FccBadgeHolders> factory() {
		return new SavedData.Factory<>(FccBadgeHolders::new, FccBadgeHolders::load, null);
	}

	public static FccBadgeHolders of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_NAME);
	}

	/** Notes that {@code agent} was issued a badge. */
	public static void issued(MinecraftServer server, UUID agent) {
		FccBadgeHolders holders = of(server);
		if(holders.issued.add(agent))
			holders.setDirty();
	}

	/** True if {@code player} holds an FCC badge (see the class comment). False with no server running or for null. */
	public static boolean holds(@Nullable UUID player) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if(player == null || server == null)
			return false;
		if(of(server).issued.contains(player))
			return true;
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		return online != null && carriesOwnBadge(online);
	}

	/** True if {@code player} carries (or wears) a badge issued to them. */
	public static boolean carriesOwnBadge(ServerPlayer player) {
		for(ItemStack stack : player.getInventory().items)
			if(isOwnBadge(stack, player.getUUID()))
				return true;
		for(ItemStack stack : player.getInventory().offhand)
			if(isOwnBadge(stack, player.getUUID()))
				return true;
		for(ItemStack stack : CuriosCompat.wornFccBadges(player))
			if(isOwnBadge(stack, player.getUUID()))
				return true;
		return false;
	}

	private static boolean isOwnBadge(ItemStack stack, UUID player) {
		FccBadge badge = stack.isEmpty() ? null : stack.get(RadiocraftDataComponent.FCC_BADGE.value());
		return badge != null && FccBadges.isOwnBadge(badge.agentId(), player);
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		ListTag list = new ListTag();
		for(UUID id : issued)
			list.add(NbtUtils.createUUID(id));
		tag.put("issued", list);
		return tag;
	}

	public static FccBadgeHolders load(CompoundTag tag, HolderLookup.Provider registries) {
		FccBadgeHolders holders = new FccBadgeHolders();
		for(Tag element : tag.getList("issued", Tag.TAG_INT_ARRAY))
			holders.issued.add(NbtUtils.loadUUID(element));
		return holders;
	}
}
