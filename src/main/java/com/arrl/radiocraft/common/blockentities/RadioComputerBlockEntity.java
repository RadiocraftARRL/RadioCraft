package com.arrl.radiocraft.common.blockentities;

import java.util.HashSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.Connection;
import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioComputerNetworkObject;
import com.arrl.radiocraft.common.blocks.ComputerMonitorBlock;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppHandlers;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerFileStore;
import com.arrl.radiocraft.common.datacomponents.ComputerData;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.items.FloppyDiskItem;
import com.arrl.radiocraft.common.menus.RadioComputerMenu;
import com.arrl.radiocraft.common.network.clientbound.CComputerPacket;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.be_networks.DataWire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The radio computer itself (1.12 step 5): the box under the monitor that holds the disk, the floppy drive and the
 * installed programs. Its disk is a {@link ComputerFileStore} named by {@link #getComputerId()}; the id and the
 * installed programs go with the item when it is broken ({@link ComputerData}), the floppy in the drive drops.
 * <p>
 * Use the monitor on top (or the computer) to sit at it; the screen talks to {@link com.arrl.radiocraft.common.computer.ComputerServer}.
 * It is a terminal on the station bus ({@link StationTerminal}), so packets the radio interfaces on its bus decode
 * reach its installed programs ({@link ComputerAppHandler#receive}), each once however many radio interfaces hear it.
 * It is on the power wire ({@link RadioComputerNetworkObject}): while in use (someone at its screen, or a program that
 * runs in the background installed) it draws {@code computer_power_use} a tick, and with no power its screen goes dark,
 * its programs stop and it hears no packets ({@link #isPowered}). It ticks on the server for that and for the programs
 * that ask to ({@link ComputerAppHandler#ticks}), and keeps each program's server state ({@link #state}).
 */
public class RadioComputerBlockEntity extends BlockEntity implements MenuProvider, StationTerminal, INetworkObjectProvider {

	/** How far from the computer (squared, blocks) its user may stand. */
	public static final double REACH_SQR = 64.0D;

	@Nullable
	private UUID computerId;
	private final Set<String> installed = new LinkedHashSet<>();
	private ItemStack floppy = ItemStack.EMPTY;

	// Server only: the programs' states (saved under "appStates"), the handlers that tick, and the packets seen.
	private final Map<String, ComputerAppState> states = new HashMap<>();
	private CompoundTag savedStates = new CompoundTag();
	private ComputerAppHandler[] tickers = new ComputerAppHandler[0];
	private boolean tickersStale = true;
	private final SeenPackets seen = new SeenPackets();
	/** Someone has its screen open (looked at every {@link #VIEWER_CHECK_TICKS}, and set at once when one opens it). */
	private boolean hasViewers = false;
	private boolean powered = false;
	private static final int VIEWER_CHECK_TICKS = 10;
	/** How far a player may go from the computer and still hear its MUSIC, blocks (the client stops it there too). */
	public static final double MUSIC_RANGE = 24.0D;
	/** Players listening to its MUSIC with the screen closed (server); it stays in use, drawing power, for them. */
	private final Set<UUID> listeners = new HashSet<>();
	/**
	 * Players with the CPS in its "Program radios" mode here (1.12 playtest fixes 2), with the codeplug they program
	 * radios with and when they last used it (server). The computer stays in use, drawing power, while anyone has it.
	 */
	private final Map<UUID, HandSession> handSessions = new HashMap<>();

	/** One player's "Program radios" mode: the codeplug loaded in the CPS, and the game time it was last used. */
	public static final class HandSession {
		public com.arrl.radiocraft.common.computer.Codeplug plug;
		public long lastUse;

		HandSession(com.arrl.radiocraft.common.computer.Codeplug plug, long lastUse) {
			this.plug = plug;
			this.lastUse = lastUse;
		}
	}

	public RadioComputerBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.RADIO_COMPUTER.get(), pos, state);
	}

	/** The id that names this computer's disk; made the first time it is asked for. */
	public UUID getComputerId() {
		if(computerId == null) {
			computerId = UUID.randomUUID();
			setChanged();
		}
		return computerId;
	}

	/** This computer's disk. Server only. */
	public ComputerFileStore files() {
		if(!(level instanceof ServerLevel server))
			throw new IllegalStateException("A radio computer's files are only on the server");
		return ComputerFileStore.of(server.getServer(), getComputerId());
	}

	// -------------------- OWNER AND PASSWORD --------------------

	/** Players who typed the password since they opened the screen (server; forgotten when they close it). */
	private final Set<UUID> unlocked = new HashSet<>();

	/** {@code player} placed it: they own it, unless its disk already has an owner (placed again after breaking). */
	public void claim(Player player) {
		if(level instanceof ServerLevel && files().owner() == null)
			files().setOwner(player.getUUID());
	}

	/** True if {@code player} may set or clear the password: its owner, an operator, or anyone while it has no owner. */
	public boolean mayChangePassword(ServerPlayer player) {
		UUID owner = files().owner();
		return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
	}

	/** True if {@code player} must type the password before using it (operators never do). Server. */
	public boolean isLockedFor(ServerPlayer player) {
		return level instanceof ServerLevel && !unlocked.contains(player.getUUID()) && !player.hasPermissions(2) && files().hasPassword();
	}

	/** {@code player} types {@code password}: true (and unlocked until they close the screen) if it is right. */
	public boolean unlock(ServerPlayer player, String password) {
		if(!files().checkPassword(getComputerId(), password))
			return false;
		unlocked.add(player.getUUID());
		return true;
	}

	/** {@code player} left the screen: they are asked again next time. */
	public void relock(UUID player) {
		unlocked.remove(player);
	}

	// -------------------- POWER --------------------

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide)
			getNetworkObject(level, worldPosition); // On the power wire.
	}

	@Override
	public BENetworkObject createNetworkObject() {
		return new RadioComputerNetworkObject(level, worldPosition);
	}

	@Nullable
	private RadioComputerNetworkObject powerObject() {
		return level != null && getNetworkObject(level, worldPosition) instanceof RadioComputerNetworkObject object ? object : null;
	}

	/**
	 * True while it runs: in use and fed by the power wire (always, with {@code radios_need_power} off). The screen
	 * reads it through {@link RadioComputerMenu#isPowered}; it is also sent to clients with the block's update, so a
	 * player listening to its MUSIC away from the screen hears it stop when the power goes.
	 */
	public boolean isPowered() {
		return powered;
	}

	/**
	 * {@code player} starts ({@code on}) or stops listening to this computer's MUSIC away from its screen: while anyone
	 * listens the computer is in use (drawing power). A listener is forgotten when they log out, change dimension or
	 * go further than {@link #MUSIC_RANGE}.
	 */
	public void listen(ServerPlayer player, boolean on) {
		boolean changed = on ? listeners.add(player.getUUID()) : listeners.remove(player.getUUID());
		if(changed)
			updatePower(false);
	}

	// -------------------- PROGRAM RADIOS MODE --------------------

	/** How long the "Program radios" mode stays on unused, ticks (5 minutes). */
	public static final long HAND_TIMEOUT_TICKS = 5L * 60L * 20L;

	/** {@code player} turns the CPS's "Program radios" mode on with {@code plug} loaded (or loads a new one into it). */
	public void startHand(ServerPlayer player, com.arrl.radiocraft.common.computer.Codeplug plug) {
		long now = level == null ? 0L : level.getGameTime();
		HandSession session = handSessions.get(player.getUUID());
		if(session == null) {
			handSessions.put(player.getUUID(), new HandSession(plug, now));
			updatePower(false);
		}
		else {
			session.plug = plug;
			session.lastUse = now;
		}
	}

	/** {@code player}'s mode ends (the CPS closed, another program opened, or turned off). */
	public void stopHand(UUID player) {
		if(handSessions.remove(player) != null)
			updatePower(false);
	}

	/** {@code player}'s "Program radios" mode here, or null if it is off. Using it counts as use (the timeout restarts). */
	@Nullable
	public HandSession handSession(UUID player) {
		HandSession session = handSessions.get(player);
		if(session != null && level != null)
			session.lastUse = level.getGameTime();
		return session;
	}

	/** True if anyone has the mode on here (for tests). */
	public boolean hasHandSessions() {
		return !handSessions.isEmpty();
	}

	/**
	 * Ends the mode for those who left it idle past {@link #HAND_TIMEOUT_TICKS}, logged out or went to another
	 * dimension, and for everyone when the computer has no power; each one still online is told why.
	 */
	private void pruneHandSessions(boolean noPower) {
		if(handSessions.isEmpty() || !(level instanceof ServerLevel server))
			return;
		long now = server.getGameTime();
		handSessions.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getServer().getPlayerList().getPlayer(entry.getKey());
			String reason = noPower ? "no_power" : player == null || player.level() != server ? "gone"
					: now - entry.getValue().lastUse > HAND_TIMEOUT_TICKS ? "timeout" : null;
			if(reason == null)
				return false;
			if(player != null)
				com.arrl.radiocraft.common.computer.apps.HandProgramming.ended(player, reason);
			return true;
		});
	}

	/** Drops listeners who left (offline, another dimension, out of range). */
	private void pruneListeners() {
		if(listeners.isEmpty() || !(level instanceof ServerLevel server))
			return;
		listeners.removeIf(id -> {
			ServerPlayer player = server.getServer().getPlayerList().getPlayer(id);
			return player == null || player.level() != server || player.distanceToSqr(worldPosition.getCenter()) > MUSIC_RANGE * MUSIC_RANGE;
		});
	}

	/** Works out whether it is in use and paid for; with a screen just opened, at once. */
	private void updatePower(boolean checkViewers) {
		boolean was = powered;
		if(checkViewers) {
			hasViewers = !viewers().isEmpty();
			pruneListeners();
			pruneHandSessions(false);
		}
		RadioComputerNetworkObject object = powerObject();
		boolean inUse = hasViewers || !listeners.isEmpty() || !handSessions.isEmpty() || tickers.length > 0;
		if(object == null)
			powered = inUse && !RadiocraftServerConfig.RADIO_POWER_REQUIRED.get();
		else {
			if(object.wantsOn != inUse) {
				object.wantsOn = inUse;
				object.tick(level, worldPosition); // Pays for this tick now, so a screen opened on a fed computer is never dark.
			}
			powered = inUse && object.isPowered;
		}
		if(!powered && !handSessions.isEmpty() && level != null && !level.isClientSide) { // No power: the mode ends (and stops asking for power).
			pruneHandSessions(true);
			if(object != null && object.wantsOn && !hasViewers && listeners.isEmpty() && tickers.length == 0)
				object.wantsOn = false;
		}
		if(powered != was && level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); // Clients see the power (MUSIC).
	}

	// -------------------- CLIENT SYNC --------------------

	/** Only the power goes to clients (the disk, programs and states stay on the server). */
	@Override
	public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("powered", powered);
		return tag;
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		powered = tag.getBoolean("powered");
	}

	@Override
	public void onDataPacket(@NotNull Connection connection, @NotNull ClientboundBlockEntityDataPacket packet, HolderLookup.@NotNull Provider registries) {
		powered = packet.getTag().getBoolean("powered");
	}

	// -------------------- PROGRAMS --------------------

	/** The programs this computer has: the built-in ones and those installed, in shell order. */
	public List<ComputerApp> availableApps() {
		return ComputerApps.available(installed);
	}

	public boolean isInstalled(ComputerApp app) {
		return app.builtIn() || installed.contains(app.id());
	}

	/** Installs {@code app} (from a floppy: the caller checks the drive); false if built in or already there. */
	public boolean install(ComputerApp app) {
		if(app.builtIn() || ComputerApps.get(app.id()) == null || installed.size() >= ComputerData.MAX_INSTALLED || !installed.add(app.id()))
			return false;
		tickersStale = true;
		setChanged();
		return true;
	}

	/** Removes an installed program (its files stay); false if it was not installed or is built in. */
	public boolean uninstall(ComputerApp app) {
		if(app.builtIn() || !installed.remove(app.id()))
			return false;
		tickersStale = true;
		states.remove(app.id());
		savedStates.remove(app.id());
		setChanged();
		return true;
	}

	// -------------------- FLOPPY DRIVE --------------------

	public ItemStack getFloppy() {
		return floppy;
	}

	/** Puts one disk of {@code stack} in the drive (the caller takes it from the player); false if it is full or no floppy. */
	public boolean insertFloppy(ItemStack stack) {
		if(!floppy.isEmpty() || !FloppyDiskItem.isFloppy(stack))
			return false;
		floppy = stack.copyWithCount(1);
		setChanged();
		return true;
	}

	/** Takes the disk out of the drive (empty if there was none). */
	public ItemStack ejectFloppy() {
		ItemStack out = floppy;
		floppy = ItemStack.EMPTY;
		if(!out.isEmpty())
			setChanged();
		return out;
	}

	// -------------------- THE STATION --------------------

	/** Where its monitor goes: on top. */
	public BlockPos monitorPos() {
		return worldPosition.above();
	}

	public boolean hasMonitor() {
		return level != null && level.getBlockState(monitorPos()).getBlock() instanceof ComputerMonitorBlock;
	}

	/** The scanner beside it (touching, or on its data wire), or null. Looked up when asked; no ticking. */
	@Nullable
	public ScannerBlockEntity findScanner() {
		if(level == null)
			return null;
		List<ScannerBlockEntity> scanners = DataWire.linked(level, worldPosition, ScannerBlockEntity.class);
		return scanners.isEmpty() ? null : scanners.getFirst();
	}

	/** Opens the computer's screen for {@code player}, shown on the monitor at {@code monitor}. */
	public void open(ServerPlayer player, BlockPos monitor) {
		hasViewers = true;
		updatePower(false);
		player.openMenu(this, buffer -> {
			buffer.writeBlockPos(worldPosition);
			buffer.writeBlockPos(monitor);
		});
	}

	/** The players with this computer's screen open. */
	public List<ServerPlayer> viewers() {
		List<ServerPlayer> out = new ArrayList<>();
		if(level instanceof ServerLevel server)
			for(ServerPlayer player : server.players())
				if(player.containerMenu instanceof RadioComputerMenu menu && menu.computer == this)
					out.add(player);
		return out;
	}

	/** Sends program {@code app} an event ({@code kind} with {@code data}) on {@code player}'s screen. */
	public static void push(ServerPlayer player, ComputerApp app, String kind, CompoundTag data) {
		CompoundTag tag = new CompoundTag();
		tag.putString("app", app.id());
		tag.putString("kind", kind);
		tag.put("data", data);
		CComputerPacket.send(player, "event", tag);
	}

	/** Sends program {@code app} an event on the screen of everyone at this computer. */
	public void push(ComputerApp app, String kind, CompoundTag data) {
		for(ServerPlayer player : viewers())
			push(player, app, kind, data);
	}

	/**
	 * A packet from a radio interface on the bus: to every installed program that listens. With two interfaces on the bus
	 * it comes once from each, so each packet id is passed on once (1.12 step 7 fix: the programs heard it twice).
	 */
	@Override
	public void receive(TncPacket packet) {
		if(!powered || !seen.remember(packet.id()))
			return;
		for(ComputerApp app : availableApps()) {
			ComputerAppHandler handler = ComputerAppHandlers.get(app);
			if(handler != null)
				handler.receive(this, packet);
		}
	}

	/** Notes a packet this computer sent itself, so it is not news when a digipeater or a second interface brings it back. */
	public void sent(TncPacket packet) {
		seen.remember(packet.id());
	}

	// -------------------- PROGRAM STATE AND TICKING --------------------

	/**
	 * {@code app}'s server state on this computer: the one made before, or a new one from {@code make} (loaded from the
	 * save if the computer was saved with one). Server thread.
	 */
	@SuppressWarnings("unchecked")
	public <T extends ComputerAppState> T state(ComputerApp app, Supplier<T> make) {
		ComputerAppState state = states.get(app.id());
		if(state == null) {
			state = make.get();
			if(savedStates.contains(app.id()) && level != null)
				state.load(savedStates.getCompound(app.id()), level.registryAccess());
			states.put(app.id(), state);
		}
		return (T)state;
	}

	/** {@code app}'s state if it has been made, else null (nothing is made). */
	@Nullable
	public ComputerAppState existingState(ComputerApp app) {
		return states.get(app.id());
	}

	public static void tick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if(blockEntity instanceof RadioComputerBlockEntity computer && level instanceof ServerLevel)
			computer.serverTick(level.getGameTime());
	}

	private void serverTick(long gameTime) {
		if(tickersStale) {
			tickersStale = false;
			List<ComputerAppHandler> list = new ArrayList<>();
			for(ComputerApp app : availableApps()) {
				ComputerAppHandler handler = ComputerAppHandlers.get(app);
				if(handler != null && handler.ticks())
					list.add(handler);
			}
			tickers = list.toArray(new ComputerAppHandler[0]);
		}
		updatePower((gameTime + worldPosition.asLong()) % VIEWER_CHECK_TICKS == 0L);
		if(!powered)
			return; // Dark: the background programs wait for power.
		for(ComputerAppHandler handler : tickers)
			handler.tick(this, gameTime);
	}

	// -------------------- MENU --------------------

	@Override
	public @NotNull Component getDisplayName() {
		return Component.translatable(Radiocraft.translationKey("container", "radio_computer"));
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return new RadioComputerMenu(id, this, monitorPos(), player);
	}

	// -------------------- SAVE AND ITEM --------------------

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		if(computerId != null)
			tag.putUUID("computerId", computerId);
		ListTag apps = new ListTag();
		for(String id : installed)
			apps.add(StringTag.valueOf(id));
		tag.put("installed", apps);
		if(!floppy.isEmpty())
			tag.put("floppy", floppy.save(registries));
		CompoundTag appStates = savedStates.copy(); // States not asked for since loading keep what they had.
		for(Map.Entry<String, ComputerAppState> entry : states.entrySet()) {
			CompoundTag one = new CompoundTag();
			entry.getValue().save(one, registries);
			if(one.isEmpty())
				appStates.remove(entry.getKey());
			else
				appStates.put(entry.getKey(), one);
		}
		if(!appStates.isEmpty())
			tag.put("appStates", appStates);
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		computerId = tag.hasUUID("computerId") ? tag.getUUID("computerId") : computerId;
		installed.clear();
		ListTag apps = tag.getList("installed", Tag.TAG_STRING);
		for(int i = 0; i < apps.size(); i++)
			if(ComputerApps.get(apps.getString(i)) != null) // A program from a removed addon is forgotten.
				installed.add(apps.getString(i));
		floppy = tag.contains("floppy") ? ItemStack.parseOptional(registries, tag.getCompound("floppy")) : ItemStack.EMPTY;
		savedStates = tag.getCompound("appStates").copy();
		states.clear();
		tickersStale = true;
	}

	/** Placed from an item that was a computer: its id (and so its files) and programs come back. */
	@Override
	protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
		super.applyImplicitComponents(input);
		ComputerData data = input.get(RadiocraftDataComponent.COMPUTER.value());
		if(data != null) {
			computerId = data.id();
			installed.clear();
			for(String id : data.installed())
				if(ComputerApps.get(id) != null)
					installed.add(id);
			tickersStale = true;
		}
	}

	/** Broken, it drops with its id and programs (the loot table copies the component). */
	@Override
	protected void collectImplicitComponents(DataComponentMap.@NotNull Builder components) {
		super.collectImplicitComponents(components);
		components.set(RadiocraftDataComponent.COMPUTER.value(), new ComputerData(getComputerId(), List.copyOf(installed)));
	}
}
