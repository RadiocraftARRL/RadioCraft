package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.be_networks.DataWire;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.FloppyDiskItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The radio computer's server side (1.12 step 5): carries out one request from the screen of a player at a computer
 * (an {@link com.arrl.radiocraft.common.network.serverbound.SStationPacket} through
 * {@link com.arrl.radiocraft.common.menus.RadioComputerMenu}, which has checked they are still at it) and answers it
 * with a {@link ComputerReply}. Every request's data carries the client's request id, {@code rid}.
 * <p>
 * The platform's requests, open to every program:
 * <ul>
 * <li>{@code open}: the installed programs ({@code apps}), the floppy in the drive, the computer's id, whether it has a
 * password ({@code password}), whether this player may change it and the settings ({@code owner}), and SETUP's
 * {@code settings}.</li>
 * <li>{@code sys.unlock} ({@code password}): unlocks a computer with a password for this player until they close the
 * screen; every other request is refused with {@code locked} until then (operators are never asked).
 * {@code sys.password} ({@code password}, "" for none): its owner or an operator sets it (SETUP's Password page).</li>
 * <li>{@code sys.settings} ({@code settings}, a {@link ComputerSettings} tag): SETUP's launcher view, colour scheme, name
 * and clock; the same people as the password may change them ({@code not_owner_settings}). Answers and pushes (to
 * everyone at the computer, as a {@link ComputerApps#SETUP} event {@code settings}) the settings kept. {@code open}
 * answers them too. On a network ({@link ComputerNetwork}) a new name must be one no reachable computer has
 * ({@code netname_in_use}), and not empty ({@code netname_needed}).</li>
 * <li>{@code sys.netname} ({@code name}): the NETWORK NAME prompt's answer (2026-11-09): the same rule and the same
 * people as SETUP's name; answers and pushes the settings as {@code sys.settings} does, and the prompt goes.</li>
 * <li>{@code sys.info}: storage, the drive, the scanner and its paper, the linked devices and the station callsign.</li>
 * <li>{@code sys.saver} ({@code on}): the screensaver came on or went off on this player's screen; the computer
 * draws less power while it shows for everyone at it. Answers {@code text}, the station's callsign, for it to show.
 * Every other request counts as use and keeps the screensaver off (computer batch 3A).</li>
 * <li>{@code sys.floppies} (the floppies in the player's inventory), {@code sys.insert} ({@code slot}),
 * {@code sys.eject}, {@code sys.install} / {@code sys.uninstall} ({@code app}).</li>
 * <li>{@code fs.list} ({@code ext}), {@code fs.read} ({@code name}: answers {@code data}), {@code fs.write}
 * ({@code name}, {@code part}, {@code parts}, {@code data}: see {@link ComputerUploads}), {@code fs.delete}
 * ({@code name}), {@code fs.rename} ({@code from}, {@code to}). Each takes {@code drive}: "A" for the floppy in the
 * drive ({@link FloppyDrive}; refused with {@code no_disk} while it is empty), anything else (or none) for C:, the
 * computer's disk. {@code fs.list} answers the drive's {@code label} too.</li>
 * <li>{@code fs.copy} ({@code from}, {@code to}: paths such as {@code A:NOTES.TXT}, {@link DrivePath}; {@code to} may be
 * a drive alone; {@code overwrite}): copies a file on the server, between drives or on one. A program can never be
 * copied onto a floppy ({@code program}). {@code fs.label} ({@code label}): the floppy's volume label.</li>
 * <li>{@code scan.read} (what is on the scanner's glass: {@code kind} image or book, {@code title}, {@code image} or
 * {@code pages}), {@code scan.save} ({@code name}: the glass into {@code NAME.IMG} or {@code NAME.TXT}),
 * {@code print.image} ({@code name} of an {@code .IMG}), {@code print.text} ({@code name} of a {@code .TXT}, or
 * {@code title} and {@code text}), {@code print.book} ({@code title}, {@code pages}).</li>
 * <li>{@code app} ({@code app}, {@code action}, {@code data}): to that program's {@link ComputerAppHandler}, if it is
 * installed here.</li>
 * </ul>
 * Errors are lang key suffixes under {@code screen.radiocraft.computer.err.}.
 */
public final class ComputerServer {

	/** Files being written in parts, per player. */
	private static final ComputerUploads.Assembler<UUID> UPLOADS = new ComputerUploads.Assembler<>();
	/** Most linked devices System Info lists. */
	private static final int MAX_LINKED = 16;

	private ComputerServer() {}

	/** Carries out {@code action} for {@code player}, answering on their screen. Server thread. */
	public static void handle(RadioComputerBlockEntity computer, ServerPlayer player, String action, CompoundTag data) {
		long start = System.nanoTime();
		handle(computer, player, action, data, new ComputerReply(player, data.getInt("rid")));
		double ms = (System.nanoTime() - start) / 1.0E6D;
		if(ms >= 20.0D || "open".equals(action)) // Debug timing for slow opens and requests (2026-11-06 performance fixes).
			com.arrl.radiocraft.Radiocraft.LOGGER.debug("Radio computer request {} took {} ms", action, String.format(java.util.Locale.ROOT, "%.2f", ms));
	}

	/** Carries out {@code action} for {@code player}, answering {@code reply}. */
	public static void handle(RadioComputerBlockEntity computer, ServerPlayer player, String action, CompoundTag data, ComputerReply reply) {
		ComputerContext context = new ComputerContext(computer, player);
		if(!"sys.unlock".equals(action) && computer.isLockedFor(player)) { // A password is set: nothing until it is typed.
			reply.error("locked");
			return;
		}
		if(!"sys.saver".equals(action))
			computer.activity(player); // Someone is using it: no screensaver (computer batch 3A).
		switch(action) {
			case "open" -> {
				CompoundTag out = hello(computer);
				out.putBoolean("password", computer.files().hasPassword());
				out.putBoolean("owner", computer.mayChangePassword(player));
				out.put("settings", computer.files().settings().toTag());
				reply.ok(out);
			}
			case "sys.unlock" -> {
				if(computer.unlock(player, data.getString("password")))
					reply.ok();
				else
					reply.error("wrong_password");
			}
			case "sys.password" -> password(computer, player, data.getString("password"), reply);
			case "sys.settings" -> settings(computer, player, data.getCompound("settings"), reply);
			case "sys.netname" -> netName(computer, player, data.getString("name"), reply);
			case "sys.info" -> reply.ok(info(context));
			case "sys.floppies" -> reply.ok(floppies(player));
			case "sys.insert" -> insert(computer, player, data.getInt("slot"), reply);
			case "sys.eject" -> eject(computer, player, reply);
			case "sys.install" -> {
				install(computer, data.getString("app"), reply);
				if(computer.installedCount() >= com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.DIGITAL_NATIVE_PROGRAMS) // Digital Native.
					com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grant(player, com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.DIGITAL_NATIVE);
			}
			case "sys.secret" -> { // A secret program was opened on this player's screen (BIBLE): its advancement.
				ComputerApp app = ComputerApps.get(data.getString("app"));
				if(app != null && app.kind() == ComputerApp.Kind.SECRET && computer.isInstalled(app) && app == ComputerApps.BIBLE)
					com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grant(player, com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.BIBLE_STUDY);
				reply.ok();
			}
			case "sys.uninstall" -> uninstall(computer, data.getString("app"), reply);
			case "fs.list" -> {
				ComputerVolume volume = volume(context, data, reply);
				if(volume != null)
					reply.ok(list(volume, data.getString("ext")));
			}
			case "fs.read" -> {
				ComputerVolume volume = volume(context, data, reply);
				if(volume != null)
					read(volume, data.getString("name"), reply);
			}
			case "fs.write" -> write(context, data, reply);
			case "fs.delete" -> {
				ComputerVolume volume = volume(context, data, reply);
				if(volume != null)
					answer(reply, volume.delete(data.getString("name")));
			}
			case "fs.rename" -> {
				ComputerVolume volume = volume(context, data, reply);
				if(volume != null)
					answer(reply, volume.rename(data.getString("from"), data.getString("to")));
			}
			case "fs.copy" -> copy(context, data.getString("from"), data.getString("to"), data.getBoolean("overwrite"), reply);
			case "fs.label" -> {
				ComputerStorage.Problem problem = FloppyDrive.setLabel(computer, data.getString("label"));
				if(problem != null)
					reply.error(problem);
				else
					reply.ok(hello(computer));
			}
			case "scan.read" -> scanRead(context, reply);
			case "scan.save" -> scanSave(context, data.getString("name"), reply);
			case "print.image" -> printImage(context, data.getString("name"), reply);
			case "print.text" -> printText(context, data, reply);
			case "print.book" -> printBook(context, data.getString("title"), strings(data.getList("pages", Tag.TAG_STRING)), reply);
			case "app" -> app(context, data, reply);
			case "sys.saver" -> { // The screensaver came on ("on") or went off on this player's screen.
				computer.setSaver(player, data.getBoolean("on"));
				CompoundTag out = new CompoundTag();
				out.putString("text", context.bus().callsign());
				reply.ok(out);
			}
			case "music" -> { // MUSIC plays on with the screen closed: the computer stays in use for this player.
				computer.listen(player, data.getBoolean("on"));
				if(data.getBoolean("on") && computer.isInstalled(ComputerApps.MUSIC)) // Spin the Disk: a tune is playing.
					com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grant(player, com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.SPIN_THE_DISK);
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/** {@code player} closed the computer's screen: a half-written file is dropped, each program is told, and it locks again. */
	public static void closed(RadioComputerBlockEntity computer, ServerPlayer player) {
		UPLOADS.cancel(player.getUUID());
		computer.activity(player); // Up from the screen: the wait for the screensaver starts now.
		computer.relock(player.getUUID());
		ComputerContext context = new ComputerContext(computer, player);
		for(ComputerApp app : computer.availableApps()) {
			ComputerAppHandler handler = ComputerAppHandlers.get(app);
			if(handler != null)
				handler.closed(context);
		}
	}

	private static void answer(ComputerReply reply, @Nullable ComputerStorage.Problem problem) {
		if(problem == null)
			reply.ok();
		else
			reply.error(problem);
	}

	// -------------------- SYSTEM --------------------

	/** The programs on {@code computer}, as ids in shell order. */
	public static ListTag appIds(RadioComputerBlockEntity computer) {
		ListTag out = new ListTag();
		for(ComputerApp app : computer.availableApps())
			out.add(StringTag.valueOf(app.id()));
		return out;
	}

	/** What the drive holds: "" for nothing, "blank", "data" (a data floppy, computer batch 3A), or the program's id. */
	public static String driveContents(RadioComputerBlockEntity computer) {
		ItemStack floppy = computer.getFloppy();
		ComputerApp app = FloppyDiskItem.app(floppy);
		return app != null ? app.id() : FloppyDrive.kind(floppy).word();
	}

	private static CompoundTag hello(RadioComputerBlockEntity computer) {
		CompoundTag out = new CompoundTag();
		out.put("apps", appIds(computer));
		out.putString("floppy", driveContents(computer));
		out.putString("floppyLabel", FloppyDrive.label(computer.getFloppy()));
		out.putString("serial", computer.getComputerId().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
		return out;
	}

	private static CompoundTag info(ComputerContext context) {
		RadioComputerBlockEntity computer = context.computer();
		CompoundTag out = hello(computer);
		ComputerFileStore files = context.files();
		out.putLong("used", files.storage().used());
		out.putLong("capacity", ComputerFileStore.capacity());
		out.putInt("files", files.storage().count());
		out.putInt("maxFiles", ComputerFileStore.maxFiles());
		ScannerBlockEntity scanner = context.scanner();
		out.putBoolean("scanner", scanner != null);
		if(scanner != null) {
			out.putString("bed", scanner.bedTitle());
			out.putInt("paper", scanner.paperCount(Items.PAPER));
			out.putInt("maps", scanner.paperCount(Items.MAP));
			out.putInt("photo", scanner.paperCount(RadiocraftItems.PHOTO_PAPER.get()));
			out.putInt("color", scanner.paperCount(RadiocraftItems.COLOR_PHOTO_PAPER.get()));
			out.putBoolean("outputFull", !scanner.output().isEmpty());
		}
		ListTag linked = new ListTag();
		for(BlockPos at : DataWire.linked(context.level(), context.pos())) {
			if(linked.size() >= MAX_LINKED)
				break;
			if(!context.level().isLoaded(at))
				continue;
			BlockEntity be = context.level().getBlockEntity(at);
			if(be == null || be instanceof ScannerBlockEntity)
				continue;
			linked.add(StringTag.valueOf(be.getBlockState().getBlock().getDescriptionId()));
		}
		out.put("linked", linked);
		out.putString("callsign", context.bus().callsign());
		return out;
	}

	private static CompoundTag floppies(ServerPlayer player) {
		ListTag list = new ListTag();
		for(int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if(!FloppyDiskItem.isFloppy(stack))
				continue;
			ComputerApp app = FloppyDiskItem.app(stack);
			CompoundTag entry = new CompoundTag();
			entry.putInt("slot", slot);
			entry.putString("app", app != null ? app.id() : FloppyDrive.kind(stack).word());
			entry.putString("label", FloppyDrive.label(stack));
			list.add(entry);
		}
		CompoundTag out = new CompoundTag();
		out.put("floppies", list);
		return out;
	}

	private static void insert(RadioComputerBlockEntity computer, ServerPlayer player, int slot, ComputerReply reply) {
		if(!computer.getFloppy().isEmpty()) {
			reply.error("drive_full");
			return;
		}
		if(slot < 0 || slot >= player.getInventory().getContainerSize() || !FloppyDiskItem.isFloppy(player.getInventory().getItem(slot))) {
			reply.error("no_floppy");
			return;
		}
		if(com.arrl.radiocraft.common.items.AllAppsFloppyItem.is(player.getInventory().getItem(slot))) { // Creative: every program, and it comes back out.
			for(ComputerApp app : ComputerApps.onFloppies())
				if(app.kind() != ComputerApp.Kind.SECRET && !computer.isInstalled(app)) // The secret ones stay to be found (2026-11-09).
					computer.install(app);
			CompoundTag out = new CompoundTag();
			out.put("apps", appIds(computer));
			out.putString("floppy", "");
			out.putString("floppyLabel", "");
			reply.ok(out);
			return;
		}
		ItemStack disk = player.getInventory().removeItem(slot, 1);
		computer.insertFloppy(disk);
		CompoundTag out = new CompoundTag();
		out.putString("floppy", driveContents(computer));
		out.putString("floppyLabel", FloppyDrive.label(computer.getFloppy()));
		reply.ok(out);
	}

	private static void eject(RadioComputerBlockEntity computer, ServerPlayer player, ComputerReply reply) {
		ItemStack disk = computer.ejectFloppy();
		if(disk.isEmpty()) {
			reply.error("drive_empty");
			return;
		}
		if(!player.getInventory().add(disk))
			player.drop(disk, false);
		CompoundTag out = new CompoundTag();
		out.putString("floppy", "");
		out.putString("floppyLabel", "");
		reply.ok(out);
	}

	private static void install(RadioComputerBlockEntity computer, String id, ComputerReply reply) {
		ComputerApp app = ComputerApps.get(id);
		if(app == null)
			reply.error("unknown_app", id);
		else if(app.builtIn())
			reply.error("built_in");
		else if(computer.isInstalled(app))
			reply.error("already_installed");
		else if(computer.getFloppy().isEmpty())
			reply.error("drive_empty");
		else if(FloppyDiskItem.app(computer.getFloppy()) != app)
			reply.error("wrong_floppy");
		else {
			computer.install(app);
			CompoundTag out = new CompoundTag();
			out.put("apps", appIds(computer));
			reply.ok(out);
		}
	}

	private static void uninstall(RadioComputerBlockEntity computer, String id, ComputerReply reply) {
		ComputerApp app = ComputerApps.get(id);
		if(app == null)
			reply.error("unknown_app", id);
		else if(app.builtIn())
			reply.error("built_in");
		else if(!computer.uninstall(app))
			reply.error("not_installed");
		else {
			CompoundTag out = new CompoundTag();
			out.put("apps", appIds(computer));
			reply.ok(out);
		}
	}

	/** Sets (or with "" clears) the password: only its owner or an operator. */
	private static void password(RadioComputerBlockEntity computer, ServerPlayer player, String password, ComputerReply reply) {
		if(!computer.mayChangePassword(player))
			reply.error("not_owner");
		else if(!ComputerLock.valid(password))
			reply.error("bad_password", String.valueOf(ComputerLock.MAX_LENGTH));
		else {
			ComputerFileStore files = computer.files();
			if(files.owner() == null)
				files.setOwner(player.getUUID()); // Placed before computers had owners: the first to lock it owns it.
			files.setPassword(computer.getComputerId(), password);
			CompoundTag out = new CompoundTag();
			out.putBoolean("password", files.hasPassword());
			reply.ok(out);
		}
	}

	/**
	 * SETUP's settings: kept on the disk if this player may change them (the password's rule), then sent to everyone at
	 * the computer so their screens change at once.
	 */
	private static void settings(RadioComputerBlockEntity computer, ServerPlayer player, CompoundTag settings, ComputerReply reply) {
		if(!computer.mayChangePassword(player)) {
			reply.error("not_owner_settings");
			return;
		}
		ComputerSettings kept = ComputerSettings.fromTag(settings);
		if(!kept.name().equals(computer.files().settings().name())) { // A new name: unique on its network.
			String refused = ComputerNetwork.nameRefused(computer, kept.name());
			if(refused != null) {
				reply.error(refused);
				return;
			}
			computer.setNeedsNetworkName(false);
		}
		keep(computer, kept, reply);
	}

	/** The NETWORK NAME prompt's answer: SETUP's name, by the same rules. */
	private static void netName(RadioComputerBlockEntity computer, ServerPlayer player, String name, ComputerReply reply) {
		if(!computer.mayChangePassword(player)) {
			reply.error("not_owner_settings");
			return;
		}
		String clean = ComputerSettings.cleanName(name);
		String refused = clean.isEmpty() ? "netname_needed" : ComputerNetwork.nameRefused(computer, clean);
		if(refused != null) {
			reply.error(refused);
			return;
		}
		computer.setNeedsNetworkName(false);
		keep(computer, computer.files().settings().withName(clean), reply);
	}

	/** Keeps {@code kept} on the disk, answers with it and shows it on every screen at the computer. */
	private static void keep(RadioComputerBlockEntity computer, ComputerSettings kept, ComputerReply reply) {
		computer.files().setSettings(kept);
		CompoundTag out = new CompoundTag();
		out.put("settings", kept.toTag());
		reply.ok(out);
		computer.push(ComputerApps.SETUP, "settings", out.copy());
	}

	// -------------------- FILES --------------------

	/**
	 * The drive a file request names ({@code drive} "A" is the floppy), or null after answering {@code no_disk} when
	 * that is the floppy drive and it is empty.
	 */
	@Nullable
	private static ComputerVolume volume(ComputerContext context, CompoundTag data, ComputerReply reply) {
		char drive = DrivePath.driveOf(data.getString("drive"));
		if(drive == DrivePath.FLOPPY && FloppyDrive.kind(context.computer().getFloppy()) == FloppyDrive.Kind.NONE) {
			reply.error(ComputerStorage.Problem.NO_DISK);
			return null;
		}
		return context.volume(drive);
	}

	/**
	 * Copies {@code fromText} to {@code toText} ({@link DrivePath}s; C: unless they say A:) on the server, so a picture
	 * never crosses the network. A destination that is a drive alone keeps the name. Answers the new file's
	 * {@code name} (with its drive) and {@code size}.
	 */
	public static void copy(ComputerContext context, String fromText, String toText, boolean overwrite, ComputerReply reply) {
		DrivePath from = DrivePath.parse(fromText, DrivePath.COMPUTER), to = DrivePath.parse(toText, DrivePath.COMPUTER);
		if(from == null || to == null || from.isDriveOnly()) {
			reply.error(ComputerStorage.Problem.BAD_NAME);
			return;
		}
		String target = to.isDriveOnly() ? from.name() : to.name();
		if(to.onFloppy() && FloppyDisk.isProgramName(target)) {
			reply.error(ComputerStorage.Problem.PROGRAM);
			return;
		}
		for(DrivePath path : new DrivePath[] { from, to })
			if(path.onFloppy() && FloppyDrive.kind(context.computer().getFloppy()) == FloppyDrive.Kind.NONE) {
				reply.error(ComputerStorage.Problem.NO_DISK);
				return;
			}
		ComputerVolume source = context.volume(from.drive()), destination = context.volume(to.drive());
		ComputerStorage.FileEntry entry = source.storage().entry(from.name());
		if(entry == null) {
			reply.error(ComputerStorage.isValidName(from.name()) ? ComputerStorage.Problem.NOT_FOUND : ComputerStorage.Problem.BAD_NAME);
			return;
		}
		String normalised = ComputerStorage.normalise(target);
		if(normalised == null) {
			reply.error(ComputerStorage.Problem.BAD_NAME);
			return;
		}
		if(from.drive() == to.drive() && entry.name().equals(normalised)) {
			reply.error("copy_onto_itself");
			return;
		}
		if(!overwrite && destination.storage().exists(normalised)) {
			reply.error(ComputerStorage.Problem.EXISTS);
			return;
		}
		ComputerStorage.Problem problem = destination.write(normalised, entry.data());
		if(problem != null) {
			reply.error(problem);
			return;
		}
		CompoundTag out = hello(context.computer());
		out.putString("name", to.drive() + ":" + normalised);
		out.putInt("size", entry.size());
		reply.ok(out);
	}

	private static CompoundTag list(ComputerVolume files, String extension) {
		ListTag list = new ListTag();
		for(ComputerStorage.FileEntry entry : files.list(extension)) {
			CompoundTag file = new CompoundTag();
			file.putString("name", entry.name());
			file.putInt("size", entry.size());
			file.putLong("modified", entry.modified());
			list.add(file);
		}
		CompoundTag out = new CompoundTag();
		out.put("files", list);
		out.putLong("used", files.storage().used());
		out.putLong("capacity", files.totalBytes());
		out.putInt("count", files.storage().count());
		out.putInt("maxFiles", files.fileLimit());
		out.putString("drive", String.valueOf(files.drive()));
		out.putString("label", files.label());
		return out;
	}

	private static void read(ComputerVolume files, String name, ComputerReply reply) {
		ComputerStorage.FileEntry entry = files.storage().entry(name);
		if(entry == null) {
			reply.error(ComputerStorage.isValidName(name) ? ComputerStorage.Problem.NOT_FOUND : ComputerStorage.Problem.BAD_NAME);
			return;
		}
		CompoundTag out = new CompoundTag();
		out.putString("name", entry.name());
		out.putByteArray("data", entry.data());
		out.putLong("modified", entry.modified());
		reply.ok(out);
	}

	private static void write(ComputerContext context, CompoundTag data, ComputerReply reply) {
		String name = ComputerStorage.normalise(data.getString("name"));
		if(name == null) {
			UPLOADS.cancel(context.player().getUUID());
			reply.error(ComputerStorage.Problem.BAD_NAME);
			return;
		}
		long now = context.level().getGameTime();
		UPLOADS.expire(now);
		ComputerUploads.Result result = UPLOADS.accept(context.player().getUUID(), name, data.getInt("part"), data.getInt("parts"), data.getByteArray("data"), now);
		switch(result.status()) {
			case BAD -> reply.error("upload_failed");
			case PARTIAL -> {
				CompoundTag out = new CompoundTag();
				out.putBoolean("partial", true);
				reply.ok(out);
			}
			case DONE -> {
				ComputerVolume volume = volume(context, data, reply);
				if(volume == null)
					return;
				ComputerStorage.Problem problem = volume.write(name, result.data());
				if(problem != null) {
					reply.error(problem);
					return;
				}
				if(name.equalsIgnoreCase("PILEUP.DAT") && context.computer().isInstalled(ComputerApps.FALLING_BLOCKS)) // High Score: PILEUP saves its table.
					com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.grant(context.player(), com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.HIGH_SCORE);
				CompoundTag out = new CompoundTag();
				out.putString("name", name);
				out.putLong("used", volume.storage().used());
				if(volume.drive() == DrivePath.FLOPPY)
					out.putString("floppy", driveContents(context.computer())); // A blank floppy may be a data floppy now.
				reply.ok(out);
			}
		}
	}

	// -------------------- SCANNER --------------------

	@Nullable
	private static ScannerBlockEntity scanner(ComputerContext context, ComputerReply reply) {
		ScannerBlockEntity scanner = context.scanner();
		if(scanner == null)
			reply.error("no_scanner");
		return scanner;
	}

	private static void scanRead(ComputerContext context, ComputerReply reply) {
		ScannerBlockEntity scanner = scanner(context, reply);
		if(scanner == null)
			return;
		CompoundTag out = new CompoundTag();
		out.putString("title", scanner.bedTitle());
		com.arrl.radiocraft.common.qsl.QslCard qsl = scanner.scanQsl(); // A QSL card reads as its front (computer batch 3C).
		byte[] image = qsl != null ? com.arrl.radiocraft.common.qsl.QslCards.frontPicture(qsl) : scanner.scanImage();
		List<String> pages = image == null ? scanner.scanPages() : null;
		if(image != null) {
			out.putString("kind", "image");
			out.putByteArray("image", image);
		}
		else if(pages != null) {
			out.putString("kind", "book");
			out.put("pages", stringList(pages));
		}
		else {
			reply.error(ScannerBlockEntity.Result.BED_EMPTY.key());
			return;
		}
		reply.ok(out);
	}

	private static void scanSave(ComputerContext context, String typed, ComputerReply reply) {
		ScannerBlockEntity scanner = scanner(context, reply);
		if(scanner == null)
			return;
		com.arrl.radiocraft.common.qsl.QslCard qsl = scanner.scanQsl();
		byte[] image = qsl != null ? null : scanner.scanImage();
		String name;
		byte[] contents;
		if(qsl != null) { // A QSL card is kept as a card file, for the QSL Designer, FAX and the BBS (computer batch 3C).
			name = ComputerStorage.suggest(typed, com.arrl.radiocraft.common.qsl.QslCards.EXTENSION);
			contents = com.arrl.radiocraft.common.qsl.QslCards.file(qsl);
		}
		else if(image != null) {
			name = ComputerStorage.suggest(typed, ComputerFileFormats.IMAGE);
			contents = image;
		}
		else {
			List<String> pages = scanner.scanPages();
			if(pages == null) {
				reply.error(ScannerBlockEntity.Result.BED_EMPTY.key());
				return;
			}
			name = ComputerStorage.suggest(typed, ComputerFileFormats.TEXT);
			contents = ComputerStorage.text(BookPages.join(pages));
		}
		ComputerStorage.Problem problem = context.files().write(name, contents);
		if(problem != null) {
			reply.error(problem);
			return;
		}
		CompoundTag out = new CompoundTag();
		out.putString("name", name);
		reply.ok(out);
	}

	private static void printImage(ComputerContext context, String name, ComputerReply reply) {
		ScannerBlockEntity scanner = scanner(context, reply);
		if(scanner == null)
			return;
		byte[] pixels = context.files().read(name);
		if(pixels == null) {
			reply.error(ComputerStorage.Problem.NOT_FOUND);
			return;
		}
		String base = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
		printed(scanner.printImage(pixels, base, context.player().getGameProfile().getName()), reply);
	}

	private static void printText(ComputerContext context, CompoundTag data, ComputerReply reply) {
		String text, title;
		if(data.contains("name")) {
			text = context.files().readText(data.getString("name"));
			if(text == null) {
				reply.error(ComputerStorage.Problem.NOT_FOUND);
				return;
			}
			String name = ComputerStorage.normalise(data.getString("name"));
			title = name == null ? "" : name.substring(0, name.indexOf('.'));
		}
		else {
			text = data.getString("text");
			title = data.getString("title");
		}
		printBook(context, title, BookPages.paginate(text), reply);
	}

	private static void printBook(ComputerContext context, String title, List<String> pages, ComputerReply reply) {
		if(pages.size() > BookDocument.PAGES_PER_VOLUME) { // The tray holds one stack of paper (step 6 fix: it used to say "no paper").
			reply.error("too_many_pages", String.valueOf(BookDocument.PAGES_PER_VOLUME));
			return;
		}
		ScannerBlockEntity scanner = scanner(context, reply);
		if(scanner != null)
			printed(scanner.printBook(title, context.player().getGameProfile().getName(), pages), reply);
	}

	private static void printed(ScannerBlockEntity.Result result, ComputerReply reply) {
		if(result == ScannerBlockEntity.Result.OK)
			reply.ok();
		else
			reply.error(result.key());
	}

	// -------------------- PROGRAMS --------------------

	private static void app(ComputerContext context, CompoundTag data, ComputerReply reply) {
		ComputerApp app = ComputerApps.get(data.getString("app"));
		if(app == null || !context.computer().isInstalled(app)) {
			reply.error("not_installed");
			return;
		}
		ComputerAppHandler handler = ComputerAppHandlers.get(app);
		if(handler == null) {
			reply.error("no_handler");
			return;
		}
		handler.handle(context, data.getString("action"), data.getCompound("data"), reply);
	}

	// -------------------- HELPERS --------------------

	public static ListTag stringList(List<String> strings) {
		ListTag out = new ListTag();
		for(String s : strings)
			out.add(StringTag.valueOf(s));
		return out;
	}

	public static List<String> strings(ListTag list) {
		List<String> out = new ArrayList<>();
		for(int i = 0; i < list.size(); i++)
			out.add(list.getString(i));
		return out;
	}
}
