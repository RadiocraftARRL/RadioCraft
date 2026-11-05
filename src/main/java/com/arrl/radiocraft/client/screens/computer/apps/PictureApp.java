package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerFiles;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.photo.PhotoImage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the SSTV and WEFAX programs (1.12 step 7) share: a big picture on the left, the one picked in the gallery (every
 * {@code .IMG} on the disk: pictures received, scanned, captioned, charts) or, while one comes in over the radio, that
 * one line by line (L goes back to it). On the right what is coming in and the gallery. F3 sends the picture shown;
 * F5 prints it on the scanner; Del deletes it. The programs add their own keys.
 */
public abstract class PictureApp extends DosApp {

	private static final long REFRESH_MS = 3_000L;

	@Nullable
	protected CompoundTag status;
	private long lastAsked = 0L;
	protected final DosList gallery = new DosList();
	protected final List<String> names = new ArrayList<>();
	/** The pictures read off the disk, by name. */
	protected final Map<String, byte[]> pictures = new HashMap<>();
	/** The picture coming in, as last pushed (null for none), and whether the big picture follows it. */
	@Nullable
	protected byte[] live;
	protected boolean showLive = true;
	private String liveFrom = "", liveLabel = "";
	private int liveRows = 0, liveLost = 0;
	private boolean wasReceiving = false;

	protected PictureApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		gallery.onActivate(index -> showLive = false);
	}

	/** The program's title (row 0). */
	protected abstract String title();

	/** The program's key bar. */
	protected abstract String keys();

	/** The word key prefix for this program ("sstv" or "wefax"). */
	protected abstract String words();

	/** Its own keys; false if not one. */
	protected boolean moreKeys(int key) {
		return false;
	}

	@Override
	public void opened() {
		refresh();
		listFiles();
	}

	@Override
	public void resumed() {
		refresh();
		listFiles();
	}

	@Override
	public void tick() {
		if(System.currentTimeMillis() - lastAsked > REFRESH_MS && !hasDialog())
			refresh();
	}

	@Override
	public void event(String kind, CompoundTag data) {
		if("rx".equals(kind)) {
			takeLive(data);
			if(!wasReceiving)
				showLive = true; // A new picture starting: show it.
			wasReceiving = true;
		}
		else if("received".equals(kind)) {
			wasReceiving = false;
			live = null;
			if(data.contains("error"))
				session.message(Dos.tr("pic.lost_disk_full", data.getString("from")), true);
			else {
				session.message(Dos.tr("pic.received", data.getString("name"), data.getString("from")), false);
				String name = data.getString("name");
				pictures.remove(name);
				listFiles(name);
			}
			refresh();
		}
	}

	private void takeLive(CompoundTag data) {
		byte[] pixels = data.getByteArray("pixels");
		if(pixels.length == PhotoImage.PIXELS)
			live = pixels;
		liveFrom = data.getString("from");
		liveLabel = data.getString("label");
		liveRows = data.getInt("rows");
		liveLost = data.getInt("lost");
	}

	protected void refresh() {
		lastAsked = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			status = response.data();
			if(status.getBoolean("receiving")) {
				takeLive(status);
				wasReceiving = true;
			}
			else {
				live = null;
				wasReceiving = false;
			}
		});
	}

	protected void listFiles() {
		listFiles(null);
	}

	/** Reads the gallery; {@code select} is picked if given. */
	protected void listFiles(@Nullable String select) {
		session.files().list(ComputerFileFormats.IMAGE, response -> {
			if(!response.ok())
				return;
			String keep = select != null ? select : selectedName();
			names.clear();
			for(ComputerFiles.Info file : ComputerFiles.infos(response))
				names.add(file.name());
			if(keep != null && names.contains(keep))
				gallery.select(names.indexOf(keep));
			if(select != null)
				showLive = false;
		});
	}

	@Nullable
	protected String selectedName() {
		int index = gallery.selected();
		return index >= 0 && index < names.size() ? names.get(index) : null;
	}

	/** The picked picture's pixels, read once (null while it is being read). */
	@Nullable
	protected byte[] selectedPicture() {
		String name = selectedName();
		if(name == null)
			return null;
		byte[] pixels = pictures.get(name);
		if(pixels == null && !pictures.containsKey(name)) {
			pictures.put(name, null); // Being read.
			session.files().read(name, response -> {
				byte[] data = response.ok() ? response.data().getByteArray("data") : new byte[0];
				pictures.put(name, data.length == PhotoImage.PIXELS ? data : new byte[0]); // Not a picture: shown as none.
			});
		}
		return pixels;
	}

	/** Who sent a picture received, from the status, or null for one not received. */
	@Nullable
	protected CompoundTag receivedInfo(String name) {
		if(status == null)
			return null;
		ListTag list = status.getList("received", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++)
			if(list.getCompound(i).getString("name").equals(name))
				return list.getCompound(i);
		return null;
	}

	// -------------------- ACTIONS --------------------

	/** Sends the picture picked, to everyone in range (after a yes). */
	protected void send() {
		String name = selectedName();
		if(name == null)
			return;
		showDialog(DosDialog.confirm(Dos.tr(words() + ".send_title"), Dos.tr(words() + ".send_hint", name), () -> {
			CompoundTag data = new CompoundTag();
			data.putString("name", name);
			session.app(app, "send", data, response -> {
				if(response.ok())
					session.message(Dos.tr(words() + ".sending", name), false);
				else
					showError(response);
			});
		}));
	}

	protected void print() {
		String name = selectedName();
		if(name == null)
			return;
		session.scanner().printImage(name, response -> {
			if(response.ok())
				session.message(Dos.tr("edit.printed"), false);
			else
				showError(response);
		});
	}

	protected void delete() {
		String name = selectedName();
		if(name == null)
			return;
		showDialog(DosDialog.confirm(Dos.tr("pic.delete_title"), Dos.tr("pic.delete_ask", name), () -> session.files().delete(name, response -> {
			pictures.remove(name);
			CompoundTag data = new CompoundTag();
			data.putString("name", name);
			session.app(app, "forget", data, ignored -> refresh());
			listFiles();
		})));
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, title(), keys());
		int bg = DosColours.BODY_BG;
		StationLine.render(g, 1, status, false);

		// The big picture.
		boolean liveShown = showLive && live != null;
		String shownName = liveShown ? Dos.tr("pic.live") : selectedName();
		g.box(1, 2, 36, 22, true, shownName == null ? "" : shownName, DosColours.TEXT_FG, bg);
		byte[] picture = liveShown ? live : selectedPicture();
		if(picture != null && picture.length == PhotoImage.PIXELS && !hasDialog())
			g.image(12, 30, 192, 192, liveShown ? "pic:live:" + app.id() : "pic:" + app.id() + ":" + shownName, picture, 128, 128);
		else if(picture == null)
			g.put(4, 12, Dos.tr(names.isEmpty() && live == null ? "pic.nothing" : "pic.reading"), DosColours.BODY_FG, bg);

		// What is coming in.
		g.box(38, 2, 41, 7, false, Dos.tr("pic.receiving_title"), DosColours.TEXT_FG, bg);
		if(live != null) {
			g.put(40, 3, Dos.fit(Dos.tr("pic.from", liveFrom, liveLabel), 37), DosColours.LIGHT_CYAN, bg);
			g.bar(40, 4, 37, liveRows / 128.0D, DosColours.LIGHT_CYAN, bg);
			g.put(40, 5, Dos.fit(Dos.tr("pic.lines", liveRows, 128, liveLost), 37), liveLost > 0 ? DosColours.YELLOW : DosColours.BODY_FG, bg);
			if(!liveShown)
				g.put(40, 6, Dos.tr("pic.press_l"), DosColours.BODY_FG, bg);
		}
		else
			g.put(40, 3, Dos.fit(Dos.tr(words() + ".listening"), 37), DosColours.BODY_FG, bg);
		renderExtra(g, bg);

		// The gallery.
		g.box(38, 9, 41, 13, false, Dos.tr("pic.gallery", names.size()), DosColours.TEXT_FG, bg);
		List<String> lines = new ArrayList<>();
		for(String name : names) {
			CompoundTag info = receivedInfo(name);
			lines.add(info == null ? name : String.format(Locale.ROOT, "%-12s %-10s %s", name, info.getString("from"), StationLine.utc(info.getLong("utc"))));
		}
		gallery.setItems(lines);
		if(lines.isEmpty())
			g.put(40, 11, Dos.tr("pic.gallery_empty"), DosColours.BODY_FG, bg);
		else
			gallery.render(g, 39, 10, 39, 11, DosColours.TEXT_FG, bg, !liveShown);
		String name = selectedName();
		CompoundTag info = name == null ? null : receivedInfo(name);
		if(info != null)
			g.put(38, 22, Dos.fit(Dos.tr("pic.info", info.getString("from"), info.getString("label"), info.getInt("lost")), 41), DosColours.BODY_FG, bg);
		StationLine.message(g, session);
	}

	/** Anything the program shows besides (row 7, in the box of what is coming in). */
	protected void renderExtra(TextGrid g, int bg) {}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_F3 -> send();
			case GLFW.GLFW_KEY_F5 -> print();
			case GLFW.GLFW_KEY_DELETE -> delete();
			default -> {
				if(moreKeys(key))
					return true;
				if(gallery.keyPressed(key)) {
					showLive = false;
					return true;
				}
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onChar(char c) {
		if(Character.toUpperCase(c) == 'L') {
			showLive = live != null;
			return true;
		}
		return false;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(gallery.mouseClicked(col, row)) {
			showLive = false;
			return true;
		}
		return false;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return gallery.mouseScrolled(col, row, amount);
	}
}
