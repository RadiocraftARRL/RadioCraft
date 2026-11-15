package com.arrl.radiocraft.common.blockentities.mesh;

import com.arrl.radiocraft.common.be_networks.DataWire;
import com.arrl.radiocraft.common.blocks.mesh.FireAlarmBoxBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarmMessage;
import com.arrl.radiocraft.common.radio.firealarm.FireAlarms;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * A radio fire alarm box (2026-11-10): the transmitter for the pull stations on its data wire (or touching it), and for
 * its own redstone input (zone 0: a smoke detector from another mod, a tripwire). When a station is pulled it sends FIRE
 * for that zone, and RESTORE when the station is reset with the key, each until a controller acks it
 * ({@link FireAlarms}); its lamp blinks while it waits. Broken, it sends TAMPER.
 * <p>
 * The key opens it (2026-11-11, {@link com.arrl.radiocraft.common.menus.FireAlarmBoxMenu}): inside, lamps for power,
 * trouble, alarm and zones 1 to 6, the radio LINK meter, TEST (sends a test), RESET (resets every pulled station on it),
 * and two toggles. The box rings the building's bells itself: a redstone signal out of its back, into the wall behind
 * it, while a zone is in alarm, unless SILENCE (the red button) was pressed; DRILL rings them for a fire drill without sending anything.
 */
public class FireAlarmBoxBlockEntity extends MeshDeviceBlockEntity implements MenuProvider {

	/** Most zones (pull stations) one box serves. */
	public static final int MAX_ZONES = 99;
	public static final int SCAN_TICKS = 10;

	private final Set<Integer> reported = new TreeSet<>(); // Zones it has sent FIRE for and not yet RESTORE.
	private int stations = 0;
	private boolean silenced = false;
	private boolean drill = false;
	/** What the bells were last told, so neighbours are only updated when it changes. */
	private boolean ringing = false;

	/** The panel's data slots: flags, LINK meter (0-100), box number, MHz and kHz of the frequency. */
	public static final int DATA_FLAGS = 0, DATA_LINK = 1, DATA_BOX = 2, DATA_MHZ = 3, DATA_KHZ = 4, DATA_COUNT = 5;
	public static final int FLAG_POWER = 1, FLAG_TROUBLE = 2, FLAG_ALARM = 4, FLAG_SILENCE = 8, FLAG_DRILL = 16, FLAG_ZONE_1 = 32;
	/** Zone lamps on the panel. */
	public static final int PANEL_ZONES = 6;

	private final ContainerData panelData = new ContainerData() {
		@Override
		public int get(int index) {
			MinecraftServer server = level == null ? null : level.getServer();
			int khz = Math.round(getFrequency() / 1000.0F);
			return switch(index) {
				case DATA_FLAGS -> flags(server);
				case DATA_LINK -> server == null ? 0 : (int)Math.round(100.0D * com.arrl.radiocraft.common.radio.mesh.RadioMesh.of(server).bestLink(globalPos()));
				case DATA_BOX -> getAddress();
				case DATA_MHZ -> khz / 1000;
				case DATA_KHZ -> khz % 1000;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	private int flags(@Nullable MinecraftServer server) {
		int flags = 0;
		if(getFrequency() > 0.0F)
			flags |= FLAG_POWER;
		if(getFrequency() <= 0.0F || server != null && FireAlarms.of(server).waiting(globalPos()))
			flags |= FLAG_TROUBLE;
		if(!reported.isEmpty())
			flags |= FLAG_ALARM;
		if(silenced)
			flags |= FLAG_SILENCE;
		if(drill)
			flags |= FLAG_DRILL;
		for(int zone = 1; zone <= PANEL_ZONES; zone++)
			if(reported.contains(zone))
				flags |= FLAG_ZONE_1 << (zone - 1);
		return flags;
	}

	@Override
	public @NotNull Component getDisplayName() {
		return getCustomName() != null ? getCustomName() : Component.translatable("block.radiocraft.fire_alarm_box");
	}

	@Override
	public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return new com.arrl.radiocraft.common.menus.FireAlarmBoxMenu(id, this, panelData);
	}

	/** The panel's buttons and toggles ({@link com.arrl.radiocraft.common.menus.FireAlarmBoxMenu}). */
	public void panelButton(ServerLevel level, int button) {
		switch(button) {
			case com.arrl.radiocraft.common.menus.FireAlarmBoxMenu.BUTTON_TEST -> {
				if(getFrequency() > 0.0F)
					test(level.getServer());
			}
			case com.arrl.radiocraft.common.menus.FireAlarmBoxMenu.BUTTON_RESET -> resetStations(level);
			case com.arrl.radiocraft.common.menus.FireAlarmBoxMenu.BUTTON_SILENCE -> silenced = !reported.isEmpty(); // Quiets the bells; the box stays in alarm until reset.
			case com.arrl.radiocraft.common.menus.FireAlarmBoxMenu.BUTTON_DRILL -> drill = !drill;
			default -> {
				return;
			}
		}
		setChanged();
		updateBells(level);
	}

	/** RESET: every pulled station on the box goes back up, as the key does one at a time. */
	public void resetStations(ServerLevel level) {
		for(PullStationBlockEntity station : DataWire.linked(level, worldPosition, PullStationBlockEntity.class))
			station.reset();
	}

	/** True while the box rings its bells: a zone in alarm and not silenced, or a drill. */
	public boolean bellsRinging() {
		return drill || !reported.isEmpty() && !silenced;
	}

	/** The back of the box, where the bell wiring goes into the wall. */
	public Direction back() {
		return getBlockState().getValue(FireAlarmBoxBlock.FACING).getOpposite();
	}

	/** What comparators were last told: the box in alarm. */
	private boolean comparatorAlarm = false;

	/**
	 * A comparator reading the box (2026-11-11): 15 while any zone is in alarm, SILENCE or not, until the stations are
	 * reset: for fire suppression, door releases and the like. A drill gives nothing.
	 */
	public int comparatorSignal() {
		return reported.isEmpty() ? 0 : 15;
	}

	private void updateBells(ServerLevel level) {
		boolean alarm = !reported.isEmpty();
		if(alarm != comparatorAlarm) {
			comparatorAlarm = alarm;
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
		}
		boolean now = bellsRinging();
		if(now == ringing)
			return;
		ringing = now;
		if(!now && silenced && reported.isEmpty())
			silenced = false; // Restored: SILENCE needs pressing again for the next alarm.
		Block block = getBlockState().getBlock();
		level.updateNeighborsAt(worldPosition, block);
		level.updateNeighborsAt(worldPosition.relative(back()), block);
	}

	public FireAlarmBoxBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.FIRE_ALARM_BOX.get(), pos, state);
	}

	@Override
	public MeshRole role() {
		return MeshRole.BOX;
	}

	@Override
	protected void onFrequencySet(MinecraftServer server) {
		FireAlarms.of(server).boxReady(server, globalPos());
	}

	/** TEST on the panel: a test message. */
	public void test(MinecraftServer server) {
		FireAlarms.of(server).report(server, globalPos(), FireAlarmMessage.TEST, 0);
	}

	public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
		if(level instanceof ServerLevel serverLevel && blockEntity instanceof FireAlarmBoxBlockEntity box)
			box.serverTick(serverLevel, state);
	}

	private void serverTick(ServerLevel level, BlockState state) {
		long now = level.getGameTime();
		MinecraftServer server = level.getServer();
		if(now % 40L == Math.floorMod(worldPosition.hashCode(), 40))
			checkAntenna(level);
		if(getFrequency() > 0.0F && now % SCAN_TICKS == 0L)
			scan(level, server);
		updateBells(level);
		boolean waiting = FireAlarms.of(server).waiting(globalPos());
		boolean lit = waiting && (now / 10L) % 2L == 0L;
		if(state.getValue(FireAlarmBoxBlock.LIT) != lit)
			level.setBlock(worldPosition, state.setValue(FireAlarmBoxBlock.LIT, lit), Block.UPDATE_CLIENTS);
	}

	/** A location on the box: its number, name and kind of alarm, for the programming panel. */
	public record Station(int zone, String name, com.arrl.radiocraft.common.radio.firealarm.AlarmKind kind) {}

	/** The numbered stations on its data wire, by number (2026-11-11). */
	public List<Station> stationList(ServerLevel level) {
		List<Station> out = new java.util.ArrayList<>();
		for(PullStationBlockEntity station : DataWire.linked(level, worldPosition, PullStationBlockEntity.class))
			if(station.getZone() > 0)
				out.add(new Station(station.getZone(), station.getName(), station.getKind()));
		out.sort(java.util.Comparator.comparingInt(Station::zone));
		return out;
	}

	/** Names station {@code zone} on its wire; false if there is none. */
	public boolean nameStation(ServerLevel level, int zone, String name) {
		return setStation(level, zone, name, null);
	}

	/** Names location {@code zone} on its wire and sets its kind of alarm (null: leave it); false if there is none. */
	public boolean setStation(ServerLevel level, int zone, String name, @Nullable com.arrl.radiocraft.common.radio.firealarm.AlarmKind kind) {
		for(PullStationBlockEntity station : DataWire.linked(level, worldPosition, PullStationBlockEntity.class))
			if(station.getZone() == zone) {
				station.setName(name);
				if(kind != null)
					station.setKind(kind);
				return true;
			}
		return false;
	}

	/** Looks at its pull stations and its redstone input, and reports what changed. */
	private void scan(ServerLevel level, MinecraftServer server) {
		List<PullStationBlockEntity> found = DataWire.linked(level, worldPosition, PullStationBlockEntity.class);
		stations = found.size();
		Set<Integer> used = new HashSet<>();
		for(PullStationBlockEntity station : found)
			if(station.getZone() > 0)
				used.add(station.getZone());
		FireAlarms alarms = FireAlarms.of(server);
		for(PullStationBlockEntity station : found) {
			if(station.getZone() <= 0) {
				int zone = 1;
				while(used.contains(zone) && zone < MAX_ZONES)
					zone++;
				station.setZone(zone);
				used.add(zone);
			}
			int zone = station.getZone();
			boolean pulled = station.isPulled();
			if(pulled && reported.add(zone)) {
				alarms.report(server, globalPos(), FireAlarmMessage.FIRE, zone, station.getName(), station.getKind());
				setChanged();
			}
			else if(!pulled && reported.remove(zone)) {
				alarms.report(server, globalPos(), FireAlarmMessage.RESTORE, zone, station.getName(), station.getKind());
				setChanged();
			}
		}
		boolean detector = false; // Any side but the back: the box's own bell signal goes out of the back.
		for(Direction side : Direction.values())
			if(side != back() && level.getSignal(worldPosition.relative(side), side) > 0)
				detector = true;
		if(detector && reported.add(0)) {
			alarms.report(server, globalPos(), FireAlarmMessage.FIRE, 0);
			setChanged();
		}
		else if(!detector && reported.remove(0)) {
			alarms.report(server, globalPos(), FireAlarmMessage.RESTORE, 0);
			setChanged();
		}
	}

	/** Zones in alarm. */
	public Set<Integer> alarmZones() {
		return reported;
	}

	@Override
	public List<Component> statusLines() {
		List<Component> out = super.statusLines();
		if(getFrequency() <= 0.0F)
			return out;
		String base = "message.radiocraft.fire_alarm_box.";
		out.add(1, Component.translatable(base + "number", String.format(Locale.ROOT, "%04d", getAddress()), label().isEmpty() ? "-" : label()));
		out.add(Component.translatable(base + "stations", stations));
		if(!reported.isEmpty())
			out.add(Component.translatable(base + "in_alarm", reported.toString()).withStyle(ChatFormatting.RED));
		if(level instanceof ServerLevel serverLevel) {
			int queued = FireAlarms.of(serverLevel.getServer()).queued(globalPos());
			if(FireAlarms.of(serverLevel.getServer()).waiting(globalPos()))
				out.add(Component.translatable(base + "waiting", queued).withStyle(ChatFormatting.YELLOW));
		}
		return out;
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("reported", new IntArrayTag(reported.stream().mapToInt(Integer::intValue).toArray()));
		tag.putBoolean("silenced", silenced);
		tag.putBoolean("drill", drill);
		tag.putBoolean("ringing", ringing);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		reported.clear();
		for(int zone : tag.getIntArray("reported"))
			reported.add(zone);
		silenced = tag.getBoolean("silenced");
		drill = tag.getBoolean("drill");
		ringing = tag.getBoolean("ringing");
	}
}
