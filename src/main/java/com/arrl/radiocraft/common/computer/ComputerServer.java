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
 * <li>{@code open}: the installed programs ({@code apps}), the floppy in the drive and the computer's id.</li>
 * <li>{@code sys.info}: storage, the drive, the scanner and its paper, the linked devices and the station callsign.</li>
 * <li>{@code sys.floppies} (the floppies in the player's inventory), {@code sys.insert} ({@code slot}),
 * {@code sys.eject}, {@code sys.install} / {@code sys.uninstall} ({@code app}).</li>
 * <li>{@code fs.list} ({@code ext}), {@code fs.read} ({@code name}: answers {@code data}), {@code fs.write}
 * ({@code name}, {@code part}, {@code parts}, {@code data}: see {@link ComputerUploads}), {@code fs.delete}
 * ({@code name}), {@code fs.rename} ({@code from}, {@code to}).</li>
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
		handle(computer, player, action, data, new ComputerReply(player, data.getInt("rid")));
	}

	/** Carries out {@code action} for {@code player}, answering {@code reply}. */
	public static void handle(RadioComputerBlockEntity computer, ServerPlayer player, String action, CompoundTag data, ComputerReply reply) {
		ComputerContext context = new ComputerContext(computer, player);
		switch(action) {
			case "open" -> reply.ok(hello(computer));
			case "sys.info" -> reply.ok(info(context));
			case "sys.floppies" -> reply.ok(floppies(player));
			case "sys.insert" -> insert(computer, player, data.getInt("slot"), reply);
			case "sys.eject" -> eject(computer, player, reply);
			case "sys.install" -> install(computer, data.getString("app"), reply);
			case "sys.uninstall" -> uninstall(computer, data.getString("app"), reply);
			case "fs.list" -> reply.ok(list(context.files(), data.getString("ext")));
			case "fs.read" -> read(context.files(), data.getString("name"), reply);
			case "fs.write" -> write(context, data, reply);
			case "fs.delete" -> answer(reply, context.files().delete(data.getString("name")));
			case "fs.rename" -> answer(reply, context.files().rename(data.getString("from"), data.getString("to")));
			case "scan.read" -> scanRead(context, reply);
			case "scan.save" -> scanSave(context, data.getString("name"), reply);
			case "print.image" -> printImage(context, data.getString("name"), reply);
			case "print.text" -> printText(context, data, reply);
			case "print.book" -> printBook(context, data.getString("title"), strings(data.getList("pages", Tag.TAG_STRING)), reply);
			case "app" -> app(context, data, reply);
			case "music" -> { // MUSIC plays on with the screen closed: the computer stays in use for this player.
				computer.listen(player, data.getBoolean("on"));
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/** {@code player} closed the computer's screen: a half-written file is dropped, and each program is told. */
	public static void closed(RadioComputerBlockEntity computer, ServerPlayer player) {
		UPLOADS.cancel(player.getUUID());
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

	/** What the drive holds: "" for nothing, "blank", or the program's id. */
	public static String driveContents(RadioComputerBlockEntity computer) {
		ItemStack floppy = computer.getFloppy();
		if(floppy.isEmpty())
			return "";
		ComputerApp app = FloppyDiskItem.app(floppy);
		return app == null ? "blank" : app.id();
	}

	private static CompoundTag hello(RadioComputerBlockEntity computer) {
		CompoundTag out = new CompoundTag();
		out.put("apps", appIds(computer));
		out.putString("floppy", driveContents(computer));
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
			entry.putString("app", app == null ? "blank" : app.id());
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
		ItemStack disk = player.getInventory().removeItem(slot, 1);
		computer.insertFloppy(disk);
		CompoundTag out = new CompoundTag();
		out.putString("floppy", driveContents(computer));
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
		reply.ok();
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

	// -------------------- FILES --------------------

	private static CompoundTag list(ComputerFileStore files, String extension) {
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
		out.putLong("capacity", ComputerFileStore.capacity());
		out.putInt("count", files.storage().count());
		out.putInt("maxFiles", ComputerFileStore.maxFiles());
		return out;
	}

	private static void read(ComputerFileStore files, String name, ComputerReply reply) {
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
				ComputerStorage.Problem problem = context.files().write(name, result.data());
				if(problem != null) {
					reply.error(problem);
					return;
				}
				CompoundTag out = new CompoundTag();
				out.putString("name", name);
				out.putLong("used", context.files().storage().used());
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
		byte[] image = scanner.scanImage();
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
		byte[] image = scanner.scanImage();
		String name;
		byte[] contents;
		if(image != null) {
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
