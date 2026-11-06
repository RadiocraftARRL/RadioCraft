package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerResponse;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * INSTALL, the built-in setup program: "Insert a disk in drive A:". It lists the floppy disks in your inventory to put
 * in the drive (or use one on the computer), installs the program on the disk in the drive (the disk is not used up)
 * with a copying bar, ejects it, and removes installed programs (Del; their files stay on the disk).
 */
public final class InstallApp extends DosApp {

	/** How long the copying bar takes, ms (the server has already installed it). */
	private static final long COPY_MS = 1800L;

	private record Entry(String text, @Nullable Runnable action, @Nullable ComputerApp removable) {}

	private final DosList list = new DosList();
	private final List<Entry> entries = new ArrayList<>();
	private final List<CompoundTag> floppies = new ArrayList<>();
	@Nullable
	private ComputerApp copying;
	private long copyStart = 0L;

	public InstallApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		list.onActivate(index -> {
			if(index >= 0 && index < entries.size() && entries.get(index).action() != null)
				entries.get(index).action().run();
		});
	}

	@Override
	public void opened() {
		refresh();
	}

	@Override
	public void resumed() {
		refresh();
	}

	private void refresh() {
		session.refresh();
		session.request("sys.floppies", response -> {
			floppies.clear();
			if(response.ok()) {
				ListTag list = response.data().getList("floppies", Tag.TAG_COMPOUND);
				for(int i = 0; i < list.size(); i++)
					floppies.add(list.getCompound(i));
			}
		});
	}

	private void build() {
		entries.clear();
		String floppy = session.floppy();
		ComputerApp onDisk = ComputerApps.get(floppy);
		if(onDisk != null && !session.isInstalled(onDisk))
			entries.add(new Entry(Dos.tr("install.install", Dos.name(onDisk)), () -> install(onDisk), null));
		else if(onDisk != null)
			entries.add(new Entry(Dos.tr("install.already", Dos.name(onDisk)), null, null));
		else if(floppy.equals("blank"))
			entries.add(new Entry(Dos.tr("install.blank"), null, null));
		else if(floppy.equals("data")) // Computer batch 3A: files, not a program.
			entries.add(new Entry(Dos.tr("install.data", Dos.drive(session)), null, null));
		if(!floppy.isEmpty())
			entries.add(new Entry(Dos.tr("install.eject"), this::eject, null));
		else {
			for(CompoundTag disk : floppies) {
				ComputerApp app = ComputerApps.get(disk.getString("app"));
				String label = disk.getString("label");
				String name = app != null ? Dos.name(app) : !"data".equals(disk.getString("app")) ? Dos.tr("drive.blank")
						: label.isEmpty() ? Dos.tr("drive.data") : Dos.tr("drive.data_label", label);
				int slot = disk.getInt("slot");
				entries.add(new Entry(Dos.tr("install.insert", name), () -> insert(slot), null));
			}
			if(floppies.isEmpty())
				entries.add(new Entry(Dos.tr("install.no_floppies"), null, null));
		}
		entries.add(new Entry("", null, null));
		entries.add(new Entry(Dos.tr("install.installed"), null, null));
		boolean any = false;
		for(ComputerApp app : session.apps())
			if(!app.builtIn()) {
				entries.add(new Entry("  " + String.format("%-12s %s", app.fileName(), Dos.name(app)), null, app));
				any = true;
			}
		if(!any)
			entries.add(new Entry("  " + Dos.tr("install.none_installed"), null, null));
		List<String> lines = new ArrayList<>();
		for(Entry entry : entries)
			lines.add(entry.text());
		list.setItems(lines);
	}

	private void install(ComputerApp app) {
		CompoundTag data = new CompoundTag();
		data.putString("app", app.id());
		session.request("sys.install", data, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			copying = app;
			copyStart = System.currentTimeMillis();
		});
	}

	private void insert(int slot) {
		CompoundTag data = new CompoundTag();
		data.putInt("slot", slot);
		session.request("sys.insert", data, this::afterDrive);
	}

	private void eject() {
		session.request("sys.eject", this::afterDrive);
	}

	private void afterDrive(ComputerResponse response) {
		if(!response.ok())
			showError(response);
		refresh();
	}

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("install.title"), Dos.tr("install.keys"));
		int bg = DosColours.BODY_BG;
		g.put(2, 2, Dos.tr("install.drive", Dos.drive(session)), DosColours.TEXT_FG, bg);
		g.put(2, 3, Dos.tr("install.hint"), DosColours.BODY_FG, bg);
		if(copying != null) {
			long t = now - copyStart;
			g.box(10, 8, 60, 7, true, Dos.tr("install.copying_title"), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
			g.shadow(10, 8, 60, 7);
			g.put(13, 10, Dos.fit(Dos.tr("install.copying", copying.fileName()), 54), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
			g.bar(13, 12, 54, t / (double)COPY_MS, DosColours.BLUE, DosColours.DIALOG_BG);
			if(t >= COPY_MS) {
				showDialog(DosDialog.message(Dos.tr("install.done_title"), Dos.tr("install.done", Dos.name(copying))));
				copying = null;
				refresh();
			}
			return;
		}
		build();
		g.box(1, 5, 78, 18, false, Dos.tr("install.choose"), DosColours.TEXT_FG, bg);
		list.render(g, 2, 6, 76, 16, DosColours.TEXT_FG, bg, !hasDialog());
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(copying != null)
			return true;
		if(key == GLFW.GLFW_KEY_DELETE) {
			int index = list.selected();
			ComputerApp app = index >= 0 && index < entries.size() ? entries.get(index).removable() : null;
			if(app != null)
				showDialog(DosDialog.confirm(Dos.tr("install.remove_title"), Dos.tr("install.remove", Dos.name(app)), () -> {
					CompoundTag data = new CompoundTag();
					data.putString("app", app.id());
					session.request("sys.uninstall", data, response -> {
						if(!response.ok())
							showError(response);
						else
							session.message(Dos.tr("install.removed", Dos.name(app)), false);
					});
				}));
			return true;
		}
		return list.keyPressed(key);
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		return copying == null && list.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return list.mouseScrolled(col, row, amount);
	}
}
