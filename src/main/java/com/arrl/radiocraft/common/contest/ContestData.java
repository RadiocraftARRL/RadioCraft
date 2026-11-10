package com.arrl.radiocraft.common.contest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The running {@code /contest} (2026-11-09), in the overworld's {@code radiocraft_contest} data so it survives a
 * restart: its name, when it started (the real clock for the report, the game time to tell which contacts are its
 * own) and its log. One contest at a time; nothing is kept once it stops (its report book has it).
 */
public final class ContestData extends SavedData {

	public static final String SAVE_NAME = "radiocraft_contest";
	/** The most contacts one contest logs (a bound on the saved data); later ones are left out. */
	public static final int MAX_QSOS = 10_000;

	private boolean running;
	private String name = "";
	private long startMillis;
	private long startGameTime;
	private final List<ContestReport.Qso> qsos = new ArrayList<>();

	public static SavedData.Factory<ContestData> factory() {
		return new SavedData.Factory<>(ContestData::new, ContestData::load, null);
	}

	public static ContestData of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(factory(), SAVE_NAME);
	}

	public boolean running() {
		return running;
	}

	public String name() {
		return name;
	}

	public long startMillis() {
		return startMillis;
	}

	public long startGameTime() {
		return startGameTime;
	}

	/** The log, oldest first (read only). */
	public List<ContestReport.Qso> qsos() {
		return Collections.unmodifiableList(qsos);
	}

	/** Starts contest {@code name} ("" for none); false if one is running. */
	public boolean start(String name, long millis, long gameTime) {
		if(running)
			return false;
		running = true;
		this.name = name == null ? "" : name;
		startMillis = millis;
		startGameTime = gameTime;
		qsos.clear();
		setDirty();
		return true;
	}

	/** Ends the contest and forgets it. */
	public void stop() {
		running = false;
		name = "";
		qsos.clear();
		setDirty();
	}

	/** Logs {@code qso}; false if the log is full or no contest runs. */
	public boolean add(ContestReport.Qso qso) {
		if(!running || qsos.size() >= MAX_QSOS)
			return false;
		qsos.add(qso);
		setDirty();
		return true;
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		tag.putBoolean("running", running);
		tag.putString("name", name);
		tag.putLong("startMillis", startMillis);
		tag.putLong("startGameTime", startGameTime);
		ListTag list = new ListTag();
		for(ContestReport.Qso q : qsos) {
			CompoundTag c = new CompoundTag();
			c.putUUID("a", q.a());
			c.putString("aName", q.aName());
			c.putUUID("b", q.b());
			c.putString("bName", q.bName());
			c.putFloat("aHz", q.aHeardHz());
			c.putFloat("bHz", q.bHeardHz());
			c.putInt("aY", q.aY());
			c.putInt("bY", q.bY());
			c.putString("aMode", q.aMode());
			c.putString("bMode", q.bMode());
			c.putString("aBiome", q.aBiome());
			c.putString("bBiome", q.bBiome());
			c.putDouble("distance", q.distance());
			c.putString("band", q.band());
			c.putLong("time", q.gameTime());
			list.add(c);
		}
		tag.put("qsos", list);
		return tag;
	}

	public static ContestData load(CompoundTag tag, HolderLookup.Provider registries) {
		ContestData data = new ContestData();
		data.running = tag.getBoolean("running");
		data.name = tag.getString("name");
		data.startMillis = tag.getLong("startMillis");
		data.startGameTime = tag.getLong("startGameTime");
		for(Tag element : tag.getList("qsos", Tag.TAG_COMPOUND)) {
			CompoundTag c = (CompoundTag)element;
			if(!c.hasUUID("a") || !c.hasUUID("b"))
				continue;
			data.qsos.add(new ContestReport.Qso(c.getUUID("a"), c.getString("aName"), c.getUUID("b"), c.getString("bName"),
					c.getFloat("aHz"), c.getFloat("bHz"), c.getInt("aY"), c.getInt("bY"), c.getString("aMode"), c.getString("bMode"),
					c.getString("aBiome"), c.getString("bBiome"), c.getDouble("distance"), c.getString("band"), c.getLong("time")));
		}
		return data;
	}
}
