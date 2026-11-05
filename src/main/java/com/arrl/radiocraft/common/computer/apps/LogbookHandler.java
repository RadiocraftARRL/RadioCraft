package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.Logbook;
import com.arrl.radiocraft.common.radio.Callsigns;
import com.arrl.radiocraft.common.radio.StationsHeard;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The logbook's server side (1.12 step 6): the stations the radios wired to the computer heard talking lately, so a
 * contact can be filled in from them ({@code heard}). Each comes with its callsign (the player's, else their name),
 * the frequency, the radio's mode and a signal report worked out from how strong it was against the radio's noise.
 * <ul>
 * <li>{@code heard}: {@code stations}, a list of {@code call}, {@code player} (the name, if known), {@code mhz},
 * {@code mode}, {@code rst}, {@code ago} (seconds since last heard) and {@code radio} (its block's translation key),
 * most recent first; and {@code radios}, how many radios are wired.</li>
 * </ul>
 */
public final class LogbookHandler implements ComputerAppHandler {

	/** How far back the logbook looks: ten minutes. */
	public static final long HEARD_WITHIN_TICKS = 20L * 60L * 10L;
	/** Most stations offered. */
	private static final int MAX_STATIONS = 24;

	private record Row(StationsHeard.Heard heard, RadioBlockEntity radio) {}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		if(action.equals("heard"))
			reply.ok(heard(context));
		else
			reply.error("unknown_request", action);
	}

	public static CompoundTag heard(ComputerContext context) {
		List<RadioBlockEntity> radios = context.linked(RadioBlockEntity.class);
		long now = context.level().getGameTime();
		UUID self = context.player().getUUID();
		List<Row> rows = new ArrayList<>();
		for(RadioBlockEntity radio : radios)
			for(StationsHeard.Heard heard : radio.getStationsHeard().recent(now, HEARD_WITHIN_TICKS))
				if(!heard.player().equals(self))
					rows.add(new Row(heard, radio));
		rows.sort(Comparator.comparingLong((Row row) -> row.heard().lastGameTime()).reversed());
		ListTag list = new ListTag();
		List<UUID> seen = new ArrayList<>();
		MinecraftServer server = context.server();
		for(Row row : rows) {
			if(list.size() >= MAX_STATIONS)
				break;
			StationsHeard.Heard heard = row.heard();
			if(seen.contains(heard.player())) // Heard on two radios: the latest only.
				continue;
			seen.add(heard.player());
			String mode = mode(row.radio());
			CompoundTag entry = new CompoundTag();
			String name = playerName(server, heard.player());
			String call = Callsigns.playerCallsign(server, heard.player());
			entry.putString("call", call != null ? call : name.toUpperCase(java.util.Locale.ROOT));
			entry.putString("player", name);
			entry.putDouble("mhz", heard.frequencyHz() / 1e6);
			entry.putString("mode", mode);
			entry.putString("rst", Logbook.rst(heard.strength(), row.radio().getNoiseFloor(false), mode.equals("CW")));
			entry.putLong("ago", Math.max(0L, (now - heard.lastGameTime()) / 20L));
			entry.putString("radio", row.radio().getBlockState().getBlock().getDescriptionId());
			list.add(entry);
		}
		CompoundTag out = new CompoundTag();
		out.put("stations", list);
		out.putInt("radios", radios.size());
		return out;
	}

	/** The mode the radio is in, as its display shows it: USB, LSB, CW or FM, DIG on a digital commercial channel. */
	public static String mode(RadioBlockEntity radio) {
		if(radio instanceof CommercialMobileBlockEntity mobile) {
			CommercialChannel channel = mobile.program().active();
			return channel != null && channel.digital() ? "DIG" : "FM";
		}
		if(radio instanceof HFRadioAllBandBlockEntity allBand && allBand.getFMEnabled())
			return "FM";
		if(radio.isFM())
			return "FM";
		if(radio instanceof HFRadioBlockEntity hf && hf.getCWEnabled())
			return "CW";
		return radio.isLowerSideband() ? "LSB" : "USB";
	}

	/** The player's name: online, from the callsign records, or from the profile cache; "" if unknown. */
	private static String playerName(MinecraftServer server, UUID id) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if(online != null)
			return online.getGameProfile().getName();
		if(server.getProfileCache() != null)
			return server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse("");
		return "";
	}
}
