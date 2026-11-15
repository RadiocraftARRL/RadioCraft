package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.common.blocks.ComputerRedstoneBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a Computer Redstone block keeps (2026-11-13): its name, its named levels ("Door Open" = 4), who may set it from
 * elsewhere on the LAN (players by name, computers by SETUP name), a pulse running, and the level it last read.
 */
public class ComputerRedstoneBlockEntity extends BlockEntity {

	/** The most a computer drives (doc/Plan-Computer4.md). */
	public static final int PER_COMPUTER = 10;
	public static final int MAX_ENTRIES = 16, MAX_NAME = 16, MAX_ACCESS = 16;
	/** A pulse lasts this long (ticks). */
	public static final int PULSE_TICKS = 10;
	private static final int READ_TICKS = 4;

	/** A named level. */
	public record Entry(String label, int level) {}

	private String name = "";
	private final List<Entry> entries = new ArrayList<>();
	private final List<String> players = new ArrayList<>();
	private final List<String> computers = new ArrayList<>();
	private int input = 0;
	private long pulseEnds = -1L;

	public ComputerRedstoneBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.COMPUTER_REDSTONE.get(), pos, state);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = clean(name, MAX_NAME);
		setChanged();
	}

	public List<Entry> getEntries() {
		return List.copyOf(entries);
	}

	/** Adds or replaces the entry for {@code level}; false when full or the label is empty. */
	public boolean putEntry(String label, int level) {
		String clean = clean(label, MAX_NAME);
		if(clean.isEmpty() || level < 0 || level > 15)
			return false;
		entries.removeIf(e -> e.level() == level || e.label().equalsIgnoreCase(clean));
		if(entries.size() >= MAX_ENTRIES)
			return false;
		entries.add(new Entry(clean, level));
		entries.sort((a, b) -> Integer.compare(a.level(), b.level()));
		setChanged();
		return true;
	}

	public void removeEntry(int index) {
		if(index >= 0 && index < entries.size()) {
			entries.remove(index);
			setChanged();
		}
	}

	public List<String> getPlayers() {
		return List.copyOf(players);
	}

	public List<String> getComputers() {
		return List.copyOf(computers);
	}

	/** Sets who else may set it: player names and computer names, comma or space separated. */
	public void setAccess(String playerList, String computerList) {
		players.clear();
		players.addAll(split(playerList));
		computers.clear();
		computers.addAll(split(computerList));
		setChanged();
	}

	/** True if {@code player} (by name) or {@code computer} (by SETUP name) is on its access lists. */
	public boolean allows(String player, String computer) {
		for(String p : players)
			if(p.equalsIgnoreCase(player))
				return true;
		for(String c : computers)
			if(!computer.isEmpty() && c.equalsIgnoreCase(computer))
				return true;
		return false;
	}

	/** The level it reads (while it is off). */
	public int getInput() {
		return input;
	}

	public int getOutput() {
		return getBlockState().hasProperty(ComputerRedstoneBlock.POWER) ? getBlockState().getValue(ComputerRedstoneBlock.POWER) : 0;
	}

	/** "Door Open (4)", or just "4" without a name; "OFF" for 0 without one. */
	public String describe(int level) {
		for(Entry e : entries)
			if(e.level() == level)
				return e.label() + " (" + level + ")";
		return level == 0 ? "OFF" : Integer.toString(level);
	}

	/** Gives {@code level} out from now on (0: off); ends a pulse. */
	public void set(int level) {
		pulseEnds = -1L;
		output(level);
	}

	/** Gives {@code level} out for {@link #PULSE_TICKS}, then goes off. */
	public void pulse(int level) {
		if(this.level == null)
			return;
		output(level);
		pulseEnds = this.level.getGameTime() + PULSE_TICKS;
	}

	private void output(int level) {
		if(this.level == null)
			return;
		int clamped = Math.max(0, Math.min(15, level));
		BlockState state = getBlockState();
		if(state.hasProperty(ComputerRedstoneBlock.POWER) && state.getValue(ComputerRedstoneBlock.POWER) != clamped) {
			this.level.setBlock(worldPosition, state.setValue(ComputerRedstoneBlock.POWER, clamped), Block.UPDATE_ALL);
			this.level.updateNeighborsAt(worldPosition, state.getBlock());
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ComputerRedstoneBlockEntity be) {
		long now = level.getGameTime();
		if(be.pulseEnds >= 0L && now >= be.pulseEnds) {
			be.pulseEnds = -1L;
			be.output(0);
		}
		if(now % READ_TICKS != 0L)
			return;
		int read = 0;
		if(state.getValue(ComputerRedstoneBlock.POWER) == 0) // While it gives out, it would only read itself back.
			for(Direction side : Direction.values())
				read = Math.max(read, level.getSignal(pos.relative(side), side));
		if(read != be.input) {
			be.input = read;
			be.setChanged();
		}
	}

	private static String clean(String text, int max) {
		StringBuilder out = new StringBuilder();
		for(char c : text.trim().toCharArray())
			if(c >= 32 && c < 127 && out.length() < max)
				out.append(c);
		return out.toString().trim();
	}

	private static List<String> split(String list) {
		List<String> out = new ArrayList<>();
		for(String part : list.split("[,\\s]+")) {
			String clean = clean(part, 32);
			if(!clean.isEmpty() && out.size() < MAX_ACCESS && out.stream().noneMatch(clean::equalsIgnoreCase))
				out.add(clean);
		}
		return out;
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString("name", name);
		ListTag list = new ListTag();
		for(Entry e : entries) {
			CompoundTag t = new CompoundTag();
			t.putString("label", e.label());
			t.putInt("level", e.level());
			list.add(t);
		}
		tag.put("entries", list);
		ListTag p = new ListTag();
		players.forEach(s -> p.add(StringTag.valueOf(s)));
		tag.put("players", p);
		ListTag c = new ListTag();
		computers.forEach(s -> c.add(StringTag.valueOf(s)));
		tag.put("computers", c);
		tag.putInt("input", input);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		name = tag.getString("name");
		entries.clear();
		ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++)
			entries.add(new Entry(list.getCompound(i).getString("label"), list.getCompound(i).getInt("level")));
		players.clear();
		ListTag p = tag.getList("players", Tag.TAG_STRING);
		for(int i = 0; i < p.size(); i++)
			players.add(p.getString(i));
		computers.clear();
		ListTag c = tag.getList("computers", Tag.TAG_STRING);
		for(int i = 0; i < c.size(); i++)
			computers.add(c.getString(i));
		input = tag.getInt("input");
	}

	/** For logs and tests. */
	@Override
	public String toString() {
		return String.format(Locale.ROOT, "ComputerRedstone[%s out=%d in=%d]", name, getOutput(), input);
	}
}
