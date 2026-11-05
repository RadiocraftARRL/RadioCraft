package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * SYSINFO, the built-in system information program (in the manner of Microsoft Diagnostics): the computer, its disk and
 * floppy drive, the scanner and its paper, the station it is wired to (callsign, linked radios and devices) and the
 * programs installed. F5 reads it all again.
 */
public final class SysInfoApp extends DosApp {

	@Nullable
	private CompoundTag info;
	private String problem = "";

	public SysInfoApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void opened() {
		refresh();
	}

	private void refresh() {
		session.request("sys.info", response -> {
			if(response.ok()) {
				info = response.data();
				problem = "";
			}
			else
				problem = response.errorText();
		});
	}

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("sysinfo.title"), Dos.tr("sysinfo.keys"));
		if(info == null) {
			g.put(2, 2, problem.isEmpty() ? Dos.tr("sysinfo.reading") : problem, DosColours.TEXT_FG, DosColours.BODY_BG);
			return;
		}
		int fg = DosColours.BODY_FG, bg = DosColours.BODY_BG, head = DosColours.YELLOW;

		// Left: the computer, its disk and drive.
		g.box(1, 1, 39, 22, false, Dos.tr("sysinfo.computer"), DosColours.TEXT_FG, bg);
		int r = 3;
		g.put(3, r++, Dos.tr("sysinfo.serial", info.getString("serial")), fg, bg);
		g.put(3, r++, Dos.tr("sysinfo.dos"), fg, bg);
		g.put(3, r++, Dos.tr("sysinfo.cpu"), fg, bg);
		g.put(3, r++, Dos.tr("sysinfo.memory"), fg, bg);
		r++;
		g.put(3, r++, Dos.tr("sysinfo.disk"), head, bg);
		long used = info.getLong("used"), capacity = info.getLong("capacity");
		g.put(3, r++, Dos.tr("sysinfo.disk_used", Dos.size(used), Dos.size(capacity)), fg, bg);
		g.bar(3, r++, 34, capacity <= 0 ? 0.0D : used / (double)capacity, DosColours.LIGHT_CYAN, bg);
		g.put(3, r++, Dos.tr("sysinfo.disk_files", info.getInt("files"), info.getInt("maxFiles")), fg, bg);
		r++;
		g.put(3, r++, Dos.tr("sysinfo.drive"), head, bg);
		String floppy = info.getString("floppy");
		ComputerApp onDisk = ComputerApps.get(floppy);
		g.put(3, r++, Dos.fit(floppy.isEmpty() ? Dos.tr("drive.empty") : floppy.equals("blank") ? Dos.tr("drive.blank")
				: onDisk != null ? Dos.name(onDisk) + " (" + onDisk.fileName() + ")" : floppy, 35), fg, bg);
		r++;
		g.put(3, r++, Dos.tr("sysinfo.programs"), head, bg);
		ListTag apps = info.getList("apps", Tag.TAG_STRING);
		int installed = 0;
		for(int i = 0; i < apps.size(); i++) {
			ComputerApp app = ComputerApps.get(apps.getString(i));
			if(app != null && !app.builtIn())
				installed++;
		}
		g.put(3, r, Dos.tr("sysinfo.programs_count", apps.size(), installed), fg, bg);

		// Right: the scanner and the station.
		g.box(41, 1, 38, 22, false, Dos.tr("sysinfo.devices"), DosColours.TEXT_FG, bg);
		r = 3;
		g.put(43, r++, Dos.tr("sysinfo.scanner"), head, bg);
		if(info.getBoolean("scanner")) {
			String bed = info.getString("bed");
			g.put(43, r++, Dos.fit(Dos.tr("sysinfo.glass", bed.isEmpty() ? Dos.tr("sysinfo.glass_empty") : bed), 34), fg, bg);
			g.put(43, r++, Dos.tr("sysinfo.paper", info.getInt("paper"), info.getInt("maps")), fg, bg);
			g.put(43, r++, Dos.tr("sysinfo.photo_paper", info.getInt("photo"), info.getInt("color")), fg, bg);
			g.put(43, r++, info.getBoolean("outputFull") ? Dos.tr("sysinfo.output_full") : Dos.tr("sysinfo.output_empty"), fg, bg);
		}
		else
			g.put(43, r++, Dos.tr("sysinfo.no_scanner"), fg, bg);
		r++;
		g.put(43, r++, Dos.tr("sysinfo.station"), head, bg);
		String callsign = info.getString("callsign");
		g.put(43, r++, callsign.isEmpty() ? Dos.tr("sysinfo.no_callsign") : Dos.tr("sysinfo.callsign", callsign), fg, bg);
		ListTag linked = info.getList("linked", Tag.TAG_STRING);
		if(linked.isEmpty())
			g.put(43, r, Dos.tr("sysinfo.no_devices"), fg, bg);
		for(int i = 0; i < linked.size() && r < 22; i++)
			g.put(43, r++, Dos.fit(" " + I18n.get(linked.getString(i)), 34), fg, bg);
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(key == GLFW.GLFW_KEY_F5) {
			refresh();
			return true;
		}
		return false;
	}
}
