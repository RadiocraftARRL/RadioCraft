package com.arrl.radiocraft.common.advancements;

import com.arrl.radiocraft.Radiocraft;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RadioCraft's advancements (the advancements batch, 2026-11-06; the tree is made by
 * {@code datagen/RadiocraftAdvancementProvider}). Each advancement granted from server code has one
 * {@link RadioEventTrigger} criterion whose event id is the advancement's own id, so {@code grant(player, FIRST_CONTACT)}
 * gives {@code radiocraft:first_contact}. Server thread only.
 * <p>
 * A player who is offline when they earn one (the signer of a QSL card someone picked up, the owner of a repeater)
 * gets it at their next login ({@link AdvancementData}).
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class RadiocraftAdvancements {

	public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Radiocraft.MOD_ID);
	public static final DeferredHolder<CriterionTrigger<?>, RadioEventTrigger> EVENT = TRIGGERS.register("event", RadioEventTrigger::new);

	// Getting started (Going Ham, the root, is a vanilla placed_block criterion).
	public static final String IS_THIS_THING_ON = "is_this_thing_on";
	public static final String FIRST_CONTACT = "first_contact";
	public static final String LICENSED_TO_CHILL = "licensed_to_chill";
	public static final String GROUND_PLANE_THEORY = "ground_plane_theory";
	// Distance and propagation.
	public static final String SKIP_DAY = "skip_day";
	public static final String DX_HUNTER = "dx_hunter";
	public static final String GREY_LINE = "grey_line";
	public static final String INTO_THE_STATIC = "into_the_static";
	public static final String NORTHERN_LIGHTS = "northern_lights";
	public static final String MOONBOUNCE = "moonbounce";
	// Modes.
	public static final String DIT_DAH = "dit_dah";
	public static final String TYPEWRITER_THUNDER = "typewriter_thunder";
	public static final String WEAK_SIGNAL_WIZARD = "weak_signal_wizard";
	public static final String PICTURE_THIS = "picture_this";
	public static final String CHART_TOPPER = "chart_topper";
	public static final String PACKET_RAT = "packet_rat";
	// Station building (Shack Upgrade is a vanilla inventory_changed criterion).
	public static final String REPEATER_OWNER = "repeater_owner";
	public static final String SHACK_UPGRADE = "shack_upgrade";
	public static final String MISSION_CONTROL = "mission_control";
	public static final String DIGITAL_NATIVE = "digital_native";
	// Commercial and public service (Out of Band is a vanilla recipe_crafted criterion).
	public static final String FLEET_MANAGER = "fleet_manager";
	public static final String CLONE_WARS = "clone_wars";
	public static final String OUT_OF_BAND = "out_of_band";
	public static final String STORM_SPOTTER = "storm_spotter";
	public static final String TAKE_SHELTER_NOW = "take_shelter_now";
	// Logging and community.
	public static final String NET_CONTROL = "net_control";
	public static final String PAPER_TRAIL = "paper_trail";
	public static final String CARD_COLLECTOR = "card_collector";
	public static final String WORKED_ALL_BIOMES = "worked_all_biomes";
	public static final String CONTEST_STATION = "contest_station";
	// Secret and fun.
	public static final String BIBLE_STUDY = "bible_study";
	public static final String HIGH_SCORE = "high_score";
	public static final String SPIN_THE_DISK = "spin_the_disk";
	public static final String NUMBERS_STATION = "numbers_station";
	public static final String FCC_IS_WATCHING = "the_fcc_is_watching";

	/** Contest Station: this many verified contacts. */
	public static final int CONTEST_CONTACTS = 100;
	/** Digital Native: this many programs installed on one computer. */
	public static final int DIGITAL_NATIVE_PROGRAMS = 10;
	/** Net Control: a net with this many check-ins. */
	public static final int NET_CHECK_INS = 5;
	/** Card Collector: an album holding this many cards. */
	public static final int ALBUM_CARDS = 20;
	/** How near a block (a computer, a teletype, a WEFAX receiver) a player must be to share what it did. */
	public static final double STATION_RANGE = 16.0D;

	/** Fake players GameTests use, which the player list does not know, by id and by name. */
	private static final Map<UUID, ServerPlayer> TEST_PLAYERS = new ConcurrentHashMap<>();
	/**
	 * What was granted to GameTests' players. NeoForge's fake players never make advancement progress (their
	 * {@code FakePlayerAdvancements} keep none), so the tests read the grants here; a test with a real ServerPlayer
	 * checks the advancement files themselves.
	 */
	private static final Map<UUID, java.util.Set<String>> TEST_GRANTS = new ConcurrentHashMap<>();

	private RadiocraftAdvancements() {}

	// -------------------- GRANTING --------------------

	/** Gives {@code player} the advancement {@code event} (a no-op if they have it). */
	public static void grant(@Nullable ServerPlayer player, String event) {
		if(player == null)
			return;
		if(TEST_PLAYERS.containsKey(player.getUUID()))
			TEST_GRANTS.computeIfAbsent(player.getUUID(), id -> ConcurrentHashMap.newKeySet()).add(event);
		EVENT.get().trigger(player, event);
	}

	/** Gives the player {@code player} the advancement {@code event} now if they are online, else at their next login. */
	public static void grant(MinecraftServer server, @Nullable UUID player, String event) {
		if(player == null)
			return;
		ServerPlayer online = player(server, player);
		if(online != null)
			grant(online, event);
		else
			AdvancementData.of(server).addPending(player, event);
	}

	/** Gives {@code event} to every player within {@code range} blocks of {@code pos}. */
	public static void grantNear(ServerLevel level, BlockPos pos, double range, String event) {
		for(ServerPlayer player : near(level, pos, range))
			grant(player, event);
	}

	/**
	 * Gives {@code event} to a station's players: its owner (now or at login) and everyone within
	 * {@link #STATION_RANGE} of it. For what a block does by itself (a computer copying a picture, a printer).
	 */
	public static void grantStation(ServerLevel level, BlockPos pos, @Nullable UUID owner, String event) {
		if(owner != null)
			grant(level.getServer(), owner, event);
		grantNear(level, pos, STATION_RANGE, event);
	}

	/** The players within {@code range} blocks of {@code pos} (GameTests' fake players too). */
	public static List<ServerPlayer> near(ServerLevel level, BlockPos pos, double range) {
		List<ServerPlayer> out = new ArrayList<>();
		double max = range * range;
		for(ServerPlayer player : level.players())
			if(player.distanceToSqr(pos.getCenter()) <= max)
				out.add(player);
		for(ServerPlayer player : TEST_PLAYERS.values())
			if(player.level() == level && !out.contains(player) && player.distanceToSqr(pos.getCenter()) <= max)
				out.add(player);
		return out;
	}

	/** The online player {@code id} (or a GameTest's fake one), else null. */
	@Nullable
	public static ServerPlayer player(MinecraftServer server, @Nullable UUID id) {
		if(id == null)
			return null;
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		return online != null ? online : TEST_PLAYERS.get(id);
	}

	/** The id of the player named {@code name}: online, a GameTest's, or known to the server's profile cache; else null. */
	@Nullable
	public static UUID playerNamed(MinecraftServer server, String name) {
		if(name == null || name.isBlank())
			return null;
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		if(online != null)
			return online.getUUID();
		for(ServerPlayer player : TEST_PLAYERS.values())
			if(player.getGameProfile().getName().equalsIgnoreCase(name))
				return player.getUUID();
		var cache = server.getProfileCache();
		return cache == null ? null : cache.get(name).map(com.mojang.authlib.GameProfile::getId).orElse(null);
	}

	/** The player whose callsign is {@code callsign} (without an SSID), else null. */
	@Nullable
	public static UUID playerWithCallsign(MinecraftServer server, String callsign) {
		if(callsign == null || callsign.isBlank())
			return null;
		com.arrl.radiocraft.api.capabilities.PlayerCallsignData data = com.arrl.radiocraft.common.data.PlayerCallsignSavedData.get(server.overworld()).getCallsignData(callsign);
		if(data == null || data.playerUUID() == null)
			return null;
		try {
			return UUID.fromString(data.playerUUID());
		}
		catch(IllegalArgumentException e) {
			return null;
		}
	}

	/** True if {@code player} has the advancement {@code event} (GameTests, and to skip work already done). */
	public static boolean has(ServerPlayer player, String event) {
		java.util.Set<String> tested = TEST_GRANTS.get(player.getUUID());
		if(tested != null && tested.contains(event))
			return true;
		MinecraftServer server = player.getServer();
		AdvancementHolder holder = server == null ? null : server.getAdvancements().get(Radiocraft.id(event));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	// -------------------- GAMETESTS --------------------

	/** Lets server code find a GameTest's fake player as if it were online. */
	public static void registerTestPlayer(ServerPlayer player) {
		TEST_PLAYERS.put(player.getUUID(), player);
	}

	public static void unregisterTestPlayer(ServerPlayer player) {
		TEST_PLAYERS.remove(player.getUUID());
		TEST_GRANTS.remove(player.getUUID());
	}

	// -------------------- LOGIN --------------------

	/** What was earned while away. */
	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if(event.getEntity() instanceof ServerPlayer player && player.getServer() != null)
			givePending(player);
	}

	/** Gives {@code player} what waits for them. */
	public static void givePending(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if(server == null)
			return;
		for(String event : AdvancementData.of(server).takePending(player.getUUID()))
			grant(player, event);
	}
}
