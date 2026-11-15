package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.common.be_networks.DataWire;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.CrtMonitorBlock;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.Waterfall;
import com.arrl.radiocraft.common.computer.apps.RigControlHandler;
import com.arrl.radiocraft.common.computer.apps.WxSatStore;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.network.clientbound.CCrtMonitorPacket;
import com.arrl.radiocraft.common.network.clientbound.CCrtWaterfallPacket;
import com.arrl.radiocraft.common.radio.BandScope;
import com.arrl.radiocraft.common.radio.crystal.CrystalQuality;
import com.arrl.radiocraft.common.radio.satellite.imaging.WxMosaic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * An External CRT Monitor (2026-11-12, was the Weather Display). It shows one of two things.
 * <p>
 * <b>A WXSAT computer's mosaic.</b> Every monitor works out its screen ({@link CrtMonitorGroups}: the same-facing
 * monitors round it in the plane of their faces) and keeps where it sits in it, which goes to clients with the block.
 * The screen's bottom-left monitor, its master, does the work for the screen: it finds the radio computer with WXSAT
 * the screen is linked to (beside one of the monitors touching it, through any chain of monitors, or on a data wire),
 * paints the computer's mosaic centred on the screen ({@link #WORLD_PER_BLOCK} blocks of ground per monitor) and sends
 * it to the players near ({@link CCrtMonitorPacket}) whenever it changes: every {@link #REFRESH_TICKS} at most while
 * lines come in. In LOOP it sends the last {@link #LOOP_FRAMES} passes. The screen shows while the computer is on.
 * <p>
 * <b>A premium radio's waterfall.</b> A monitor on its own (no other monitor in its screen) that no WXSAT computer
 * links to, touching a premium radio ({@link #premium}: the Pure-crystal HF rigs and VHF base station), shows a narrow
 * waterfall round the radio's dial ({@link Waterfall#monitorSpan}: NARROW or WIDE, both much narrower than rig
 * control's whole band). It reads the radio's second band scope ({@link RadioBlockEntity#monitorScope}), the same
 * signals the antenna network already hands the radio, and sends a row of levels ({@link CCrtWaterfallPacket}, 76
 * bytes) every {@link #WATERFALL_TICKS} only when the row or the span changed, only to the players near; the client
 * repeats the last row while nothing changes. A computer wins over a radio.
 */
public class CrtMonitorBlockEntity extends BlockEntity {

	/** What the screen shows from a WXSAT computer. */
	public enum Mode {
		LIVE, LOOP, OFF;

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Mode of(int ordinal) {
			return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : LIVE;
		}
	}

	/** What the screen shows of a radio: its waterfall, narrow or wide, or nothing. */
	public enum Span {
		NARROW, WIDE, OFF;

		public Span next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Span of(int ordinal) {
			return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NARROW;
		}
	}

	/** Ground one monitor shows across (and up), blocks: one mosaic tile. */
	public static final int WORLD_PER_BLOCK = WxMosaic.TILE_BLOCKS;
	/** Picture pixels per monitor, across and up. */
	public static final int PIXELS_PER_BLOCK = WxMosaic.TILE;
	/** Passes the loop shows. */
	public static final int LOOP_FRAMES = 6;
	/** Most ticks between pictures while the mosaic changes. */
	public static final int REFRESH_TICKS = 40;
	/** Ticks between waterfall rows (two a second, as rig control's monitor mirror). */
	public static final int WATERFALL_TICKS = 10;
	/** Ticks the radio's monitor scope lasts after the last row. */
	private static final int WATERFALL_LEASE = 40;
	/** Monitors searched for a computer through a chain of them. */
	private static final int MAX_CHAIN = 64;

	private Mode mode = Mode.LIVE;
	private Span span = Span.NARROW;
	// Where it sits in its screen (synced): the master's position, its own cell from the bottom left, the screen's size.
	private BlockPos master;
	private int cellA = 0, cellB = 0, groupW = 1, groupH = 1;
	private boolean groupStale = true;
	/** Showing a radio's waterfall (synced: the client draws the waterfall, not the mosaic). */
	private boolean radioMode = false;
	// The master's, server only: the computer or radio and what went out last.
	@Nullable
	private BlockPos computerPos;
	@Nullable
	private BlockPos radioPos;
	private String sentKey = "";
	private long sentAt = Long.MIN_VALUE;
	private final Set<UUID> sentTo = new HashSet<>();
	private final float[] bins = new float[BandScope.BINS];
	private byte[] fallRow = new byte[0];
	private String fallKey = "";
	private final Set<UUID> fallSentTo = new HashSet<>();
	// Client only: the power-on and -off animation.
	public boolean clientLit = false;
	public long clientLitChangedAt = Long.MIN_VALUE;

	public CrtMonitorBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.EXTERNAL_CRT_MONITOR.get(), pos, state);
		master = pos;
	}

	public Mode mode() {
		return mode;
	}

	public Span span() {
		return span;
	}

	/** True while it shows a radio's waterfall rather than a computer's mosaic. */
	public boolean radioMode() {
		return radioMode;
	}

	/** The radio whose waterfall it shows (server), or null. */
	@Nullable
	public BlockPos radioPos() {
		return radioPos;
	}

	/** The screen's master (its bottom-left monitor). */
	public BlockPos master() {
		return master;
	}

	/** This monitor's cell in its screen, from the bottom left as the viewer sees it. */
	public int cellA() {
		return cellA;
	}

	public int cellB() {
		return cellB;
	}

	public int groupW() {
		return groupW;
	}

	public int groupH() {
		return groupH;
	}

	public boolean isMaster() {
		return master.equals(worldPosition);
	}

	/** A block beside it changed: work the screen out again on the next tick. */
	public void neighboursChanged() {
		groupStale = true;
	}

	/** Right-click: on a radio, the next span; else the whole screen's next mode. Returns the new one's key. */
	public String cycle() {
		return radioMode ? cycleSpan().key() : cycleMode().key();
	}

	/** The whole screen goes to the next computer mode (this monitor's next). Returns it. */
	public Mode cycleMode() {
		Mode next = mode.next();
		if(level != null)
			for(CrtMonitorBlockEntity member : members())
				member.setMode(next);
		setMode(next);
		return next;
	}

	/** The next waterfall span: NARROW, WIDE, OFF. Returns it. */
	public Span cycleSpan() {
		span = span.next();
		fallKey = "";
		setChanged();
		sync();
		return span;
	}

	private void setMode(Mode next) {
		if(mode != next) {
			mode = next;
			sentKey = "";
			setChanged();
			sync();
		}
	}

	/** The monitors of its screen that are loaded. */
	private List<CrtMonitorBlockEntity> members() {
		List<CrtMonitorBlockEntity> out = new ArrayList<>();
		if(level == null)
			return out;
		Direction facing = getBlockState().getValue(CrtMonitorBlock.FACING), right = facing.getCounterClockWise();
		for(int a = 0; a < groupW; a++)
			for(int b = 0; b < groupH; b++) {
				BlockPos at = master.relative(right, a).above(b);
				if(level.isLoaded(at) && level.getBlockEntity(at) instanceof CrtMonitorBlockEntity display)
					out.add(display);
			}
		return out;
	}

	// -------------------- SERVER --------------------

	public static void serverTick(Level level, BlockPos pos, BlockState state, CrtMonitorBlockEntity display) {
		if(!(level instanceof ServerLevel server))
			return;
		long now = level.getGameTime();
		if(display.groupStale || (now + pos.asLong()) % 10L == 0L)
			display.formGroup(server, state);
		if(display.isMaster() && (now + pos.asLong()) % 20L == 0L)
			display.feed(server, now);
		if(display.radioMode && (now + pos.asLong()) % WATERFALL_TICKS == 0L)
			display.feedWaterfall(server, now);
	}

	/** Works out its screen; syncs if it changed. */
	private void formGroup(ServerLevel level, BlockState state) {
		groupStale = false;
		Direction facing = state.getValue(CrtMonitorBlock.FACING), right = facing.getCounterClockWise();
		Set<Long> cells = new HashSet<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		cells.add(CrtMonitorGroups.cell(0, 0));
		queue.add(new int[] { 0, 0 });
		while(!queue.isEmpty() && cells.size() < CrtMonitorGroups.MAX_CELLS) {
			int[] c = queue.poll();
			for(int[] step : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
				int a = c[0] + step[0], b = c[1] + step[1];
				if(cells.contains(CrtMonitorGroups.cell(a, b)))
					continue;
				BlockPos at = worldPosition.relative(right, a).above(b);
				if(!level.isLoaded(at))
					continue;
				BlockState there = level.getBlockState(at);
				if(there.getBlock() instanceof CrtMonitorBlock && there.getValue(CrtMonitorBlock.FACING) == facing) {
					cells.add(CrtMonitorGroups.cell(a, b));
					queue.add(new int[] { a, b });
				}
			}
		}
		CrtMonitorGroups.Rect rect = CrtMonitorGroups.groupOf(0, 0, cells);
		BlockPos newMaster = worldPosition.relative(right, rect.a()).above(rect.b());
		if(!newMaster.equals(master) || cellA != -rect.a() || cellB != -rect.b() || groupW != rect.w() || groupH != rect.h()) {
			master = newMaster;
			cellA = -rect.a();
			cellB = -rect.b();
			groupW = rect.w();
			groupH = rect.h();
			sentKey = "";
			if(groupW * groupH > 1)
				linkRadio(null); // Part of a bigger screen: no waterfall.
			setChanged();
			sync();
		}
	}

	/** The computer with WXSAT the screen is linked to: beside one of a chain of monitors from this one, or on a data wire. */
	@Nullable
	public RadioComputerBlockEntity findComputer() {
		if(level == null)
			return null;
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(worldPosition);
		seen.add(worldPosition);
		while(!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for(RadioComputerBlockEntity computer : DataWire.linked(level, at, RadioComputerBlockEntity.class))
				if(computer.isInstalled(ComputerApps.WXSAT))
					return computer;
			// Beside the computer's own screen (the monitor block on top of it) counts as beside the computer: that is
			// what a player sees as the PC (2026-11-14, the maintainer's monitor next to the PC stayed blank).
			for(Direction side : Direction.values()) {
				BlockPos screen = at.relative(side);
				if(level.isLoaded(screen) && level.getBlockState(screen).getBlock() instanceof com.arrl.radiocraft.common.blocks.ComputerMonitorBlock
						&& level.getBlockEntity(screen.below()) instanceof RadioComputerBlockEntity computer && computer.isInstalled(ComputerApps.WXSAT))
					return computer;
			}
			for(Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				if(seen.size() < MAX_CHAIN && !seen.contains(next) && level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof CrtMonitorBlock) {
					seen.add(next);
					queue.add(next);
				}
			}
		}
		return null;
	}

	/** True for a radio whose waterfall a monitor shows: a premium one (built with a Pure crystal), not plain or cheap. */
	public static boolean premium(RadioBlockEntity radio) {
		return radio.getCrystalQuality() == CrystalQuality.PURE;
	}

	/** A premium radio touching this monitor, or null. */
	@Nullable
	public RadioBlockEntity findRadio() {
		if(level == null)
			return null;
		for(Direction side : Direction.values()) {
			BlockPos at = worldPosition.relative(side);
			if(level.isLoaded(at) && level.getBlockEntity(at) instanceof RadioBlockEntity radio && premium(radio))
				return radio;
		}
		return null;
	}

	/** Shows {@code radio}'s waterfall from now on, or (null) none. */
	private void linkRadio(@Nullable RadioBlockEntity radio) {
		BlockPos pos = radio == null ? null : radio.getBlockPos();
		if(radioPos != null && !radioPos.equals(pos) && level != null && level.isLoaded(radioPos) && level.getBlockEntity(radioPos) instanceof RadioBlockEntity old)
			old.dropMonitorScope();
		radioPos = pos;
		boolean now = pos != null;
		if(now != radioMode) {
			radioMode = now;
			sentKey = "";
			fallKey = "";
			fallSentTo.clear();
			setChanged();
			sync();
		}
	}

	/**
	 * The master's round: find the computer (or, a lone monitor with none, a premium radio), and send the computer's
	 * picture if it changed or someone new is near.
	 */
	private void feed(ServerLevel level, long now) {
		RadioComputerBlockEntity computer = null;
		if(computerPos != null && level.isLoaded(computerPos) && level.getBlockEntity(computerPos) instanceof RadioComputerBlockEntity known
				&& known.isInstalled(ComputerApps.WXSAT) && (now + worldPosition.asLong()) % 100L != 0L)
			computer = known;
		else {
			computer = findComputer();
			computerPos = computer == null ? null : computer.getBlockPos();
		}
		if(computer == null && groupW == 1 && groupH == 1) { // A computer wins over a radio.
			RadioBlockEntity radio = findRadio();
			if(radio != null) {
				linkRadio(radio);
				return; // feedWaterfall sends its rows.
			}
		}
		linkRadio(null);
		boolean on = computer != null && computer.isPowered() && mode != Mode.OFF;
		WxSatStore store = on ? WxSatStore.of(level.getServer(), computer.getComputerId()) : null;
		String key = mode + "|" + on + "|" + groupW + "x" + groupH + "|" + (store == null ? 0L : store.version());
		List<ServerPlayer> near = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(worldPosition), false);
		boolean changed = !key.equals(sentKey);
		if(changed && now - sentAt < REFRESH_TICKS && store != null && sentKey.startsWith(mode + "|" + on + "|" + groupW + "x" + groupH + "|"))
			changed = false; // Lines are coming in: at most one picture every REFRESH_TICKS.
		List<ServerPlayer> to = new ArrayList<>();
		for(ServerPlayer player : near)
			if(changed || !sentTo.contains(player.getUUID()))
				to.add(player);
		if(to.isEmpty())
			return;
		CCrtMonitorPacket packet = picture(store, on);
		for(ServerPlayer player : to)
			PacketDistributor.sendToPlayer(player, packet);
		if(changed) {
			sentKey = key;
			sentAt = now;
			sentTo.clear();
		}
		for(ServerPlayer player : to)
			sentTo.add(player.getUUID());
		sentTo.removeIf(id -> near.stream().noneMatch(p -> p.getUUID().equals(id)));
	}

	/**
	 * A waterfall row from the radio, sent to the players near if it or the span changed (a quiet band sends nothing,
	 * and a steady carrier one row): the client repeats the last row until a new one comes. With nobody near the
	 * radio's monitor scope is let go, so it costs nothing.
	 */
	private void feedWaterfall(ServerLevel level, long now) {
		RadioBlockEntity radio = radioPos != null && level.isLoaded(radioPos) && level.getBlockEntity(radioPos) instanceof RadioBlockEntity r && premium(r) ? r : null;
		if(radio == null) {
			linkRadio(null);
			return;
		}
		List<ServerPlayer> near = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(worldPosition), false);
		if(near.isEmpty()) {
			radio.dropMonitorScope();
			fallSentTo.clear();
			return;
		}
		CCrtWaterfallPacket update = sample(radio, now);
		String key = update.on() + "|" + span + "|" + update.lowHz() + "|" + update.highHz() + "|" + update.dialHz();
		boolean changed = !key.equals(fallKey) || !Arrays.equals(update.row(), fallRow);
		CCrtWaterfallPacket fresh = null;
		for(ServerPlayer player : near) {
			boolean isNew = !fallSentTo.contains(player.getUUID());
			if(isNew) {
				if(fresh == null)
					fresh = new CCrtWaterfallPacket(worldPosition, update.on(), update.span(), update.lowHz(), update.highHz(), update.dialHz(), now, true, update.row());
				PacketDistributor.sendToPlayer(player, fresh);
			}
			else if(changed)
				PacketDistributor.sendToPlayer(player, update);
		}
		fallKey = key;
		fallRow = update.row();
		fallSentTo.clear();
		for(ServerPlayer player : near)
			fallSentTo.add(player.getUUID());
	}

	/**
	 * The waterfall's row now from {@code radio} (its monitor scope drained into levels over its noise), with the span:
	 * empty and off while the radio is off or the span is OFF (the scope is let go then). Server thread; also tests.
	 */
	public CCrtWaterfallPacket sample(RadioBlockEntity radio, long now) {
		boolean on = span != Span.OFF && radio.isPowered();
		double dial = radio.getFrequency(), lo = 0.0D, hi = 0.0D;
		byte[] row = new byte[0];
		if(on) {
			double[] range = RigControlHandler.bandRange(radio);
			double[] s = range == null ? Waterfall.monitorSpan(span == Span.WIDE, dial, 0.0D, 0.0D) : Waterfall.monitorSpan(span == Span.WIDE, dial, range[0], range[1]);
			lo = s[0];
			hi = s[1];
			radio.monitorScope(lo, hi, WATERFALL_LEASE).drain(bins);
			double noise = radio.getNoiseFloor(false);
			row = new byte[BandScope.BINS];
			for(int i = 0; i < row.length; i++)
				row[i] = (byte)Waterfall.level(bins[i], noise);
		}
		else
			radio.dropMonitorScope();
		return new CCrtWaterfallPacket(worldPosition, on, span.ordinal(), lo, hi, dial, now, false, row);
	}

	/** The screen's picture: the mosaic centred on the screen, the latest or (LOOP) the last few passes. */
	CCrtMonitorPacket picture(@Nullable WxSatStore store, boolean on) {
		int w = groupW * PIXELS_PER_BLOCK, h = groupH * PIXELS_PER_BLOCK;
		List<byte[]> frames = new ArrayList<>();
		if(on && store != null) {
			WxMosaic mosaic = store.mosaic();
			int[] centre = centre();
			if(mode == Mode.LOOP && mosaic.passCount() > 1)
				for(int f = Math.max(0, mosaic.passCount() - LOOP_FRAMES); f < mosaic.passCount(); f++)
					frames.add(mosaic.view(centre[0], centre[1], w, h, WORLD_PER_BLOCK / PIXELS_PER_BLOCK, f));
			else
				frames.add(mosaic.view(centre[0], centre[1], w, h, WORLD_PER_BLOCK / PIXELS_PER_BLOCK, -1));
		}
		return CCrtMonitorPacket.of(worldPosition, on, mode.ordinal(), w, h, frames);
	}

	/** The block the screen's picture is centred on: the middle of its face, in x and z. */
	int[] centre() {
		Direction right = getBlockState().getValue(CrtMonitorBlock.FACING).getCounterClockWise();
		double half = (groupW - 1) / 2.0D;
		return new int[] { (int)Math.floor(master.getX() + 0.5D + right.getStepX() * half), (int)Math.floor(master.getZ() + 0.5D + right.getStepZ() * half) };
	}

	@Override
	public void setRemoved() {
		if(radioPos != null && level != null && !level.isClientSide && level.isLoaded(radioPos) && level.getBlockEntity(radioPos) instanceof RadioBlockEntity radio)
			radio.dropMonitorScope(); // Broken: the radio stops tapping for it (the lease would run out anyway).
		super.setRemoved();
	}

	// -------------------- SAVING AND SYNC --------------------

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("mode", mode.ordinal());
		tag.putInt("span", span.ordinal());
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		mode = Mode.of(tag.getInt("mode"));
		span = Span.of(tag.getInt("span"));
		if(tag.contains("master")) { // The client's copy of where it sits in its screen.
			master = BlockPos.of(tag.getLong("master"));
			cellA = tag.getInt("a");
			cellB = tag.getInt("b");
			groupW = Math.max(1, tag.getInt("w"));
			groupH = Math.max(1, tag.getInt("h"));
			radioMode = tag.getBoolean("radio");
		}
	}

	@Override
	public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("mode", mode.ordinal());
		tag.putInt("span", span.ordinal());
		tag.putLong("master", master.asLong());
		tag.putInt("a", cellA);
		tag.putInt("b", cellB);
		tag.putInt("w", groupW);
		tag.putInt("h", groupH);
		tag.putBoolean("radio", radioMode);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	private void sync() {
		if(level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}
}
