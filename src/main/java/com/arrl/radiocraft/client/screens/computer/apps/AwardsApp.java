package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AWARDS (computer batch 3C): operating awards from the logbook, every contact counting. The list page shows each
 * award with its level, the count against the next goal and a bar; Enter opens its progress page (what was worked and
 * what is still missing), F5 prints the certificate for the level reached on the scanner, F6 shows the distance records
 * (the longest contact per band and per mode), F3 reads another log, F4 reads the log again. The server works it all
 * out ({@code AwardsHandler}).
 */
public final class AwardsApp extends DosApp {

	private enum Page { LIST, DETAIL, RECORDS }

	private String fileName = "LOGBOOK.LOG";
	private CompoundTag status;
	private final DosList list = new DosList();
	private final DosList detail = new DosList();
	private Page page = Page.LIST;

	public AwardsApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		list.onActivate(index -> showDetail());
	}

	@Override
	public void opened() {
		refresh();
	}

	@Override
	public void resumed() {
		refresh();
	}

	@Override
	public void openFile(String name) {
		fileName = name;
		refresh();
	}

	private void refresh() {
		CompoundTag data = new CompoundTag();
		data.putString("file", fileName);
		session.app(app, "status", data, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			status = response.data();
			fileName = status.getString("file");
			List<String> lines = new ArrayList<>();
			for(CompoundTag award : awards())
				lines.add(listLine(award));
			int keep = list.selected();
			list.setItems(lines);
			if(keep >= 0)
				list.select(keep);
		});
	}

	private List<CompoundTag> awards() {
		List<CompoundTag> out = new ArrayList<>();
		if(status != null) {
			ListTag list = status.getList("awards", Tag.TAG_COMPOUND);
			for(int i = 0; i < list.size(); i++)
				out.add(list.getCompound(i));
		}
		return out;
	}

	private static int[] tiers(CompoundTag award) {
		ListTag list = award.getList("tiers", Tag.TAG_INT);
		int[] out = new int[list.size()];
		for(int i = 0; i < out.length; i++)
			out[i] = list.getInt(i);
		return out;
	}

	private static String listLine(CompoundTag award) {
		int[] tiers = tiers(award);
		int level = award.getInt("level");
		StringBuilder stars = new StringBuilder();
		for(int i = 0; i < tiers.length; i++)
			stars.append(i < level ? '*' : '.');
		int next = tiers.length == 0 ? 0 : tiers[Math.min(level, tiers.length - 1)];
		String count = level >= tiers.length ? Dos.tr("awards.done", award.getLong("count")) : award.getLong("count") + "/" + next;
		return String.format(Locale.ROOT, "%-28s %-8s %14s", Dos.tr("awards." + award.getString("id")), stars, count);
	}

	private CompoundTag selected() {
		List<CompoundTag> awards = awards();
		int at = list.selected();
		return at >= 0 && at < awards.size() ? awards.get(at) : null;
	}

	private void showDetail() {
		CompoundTag award = selected();
		if(award == null)
			return;
		List<String> lines = new ArrayList<>();
		lines.add(Dos.tr("awards.about." + award.getString("id")));
		int[] tiers = tiers(award);
		StringBuilder levels = new StringBuilder();
		for(int i = 0; i < tiers.length; i++)
			levels.append(i < award.getInt("level") ? "[" + tiers[i] + "] " : tiers[i] + " ");
		lines.add(Dos.tr("awards.levels", levels.toString().strip()));
		lines.add("");
		ListTag worked = award.getList("worked", Tag.TAG_STRING);
		ListTag missing = award.getList("missing", Tag.TAG_STRING);
		if(!worked.isEmpty()) {
			lines.add(Dos.tr("awards.worked", worked.size()));
			for(int i = 0; i < worked.size(); i++)
				lines.add("  " + pretty(worked.getString(i)));
		}
		if(!missing.isEmpty()) {
			lines.add(Dos.tr("awards.missing", missing.size()));
			for(int i = 0; i < missing.size(); i++)
				lines.add("  " + pretty(missing.getString(i)));
		}
		detail.setItems(lines);
		detail.select(0);
		page = Page.DETAIL;
	}

	private void showRecords() {
		if(status == null)
			return;
		List<String> lines = new ArrayList<>();
		ListTag records = status.getList("records", Tag.TAG_COMPOUND);
		if(records.isEmpty())
			lines.add(Dos.tr("awards.no_records"));
		for(int i = 0; i < records.size(); i++) {
			CompoundTag r = records.getCompound(i);
			lines.add(String.format(Locale.ROOT, "%-8s %-12s %8s  %s", r.getString("key"), r.getString("call"), r.getInt("distance"), r.getString("date")));
		}
		detail.setItems(lines);
		detail.select(0);
		page = Page.RECORDS;
	}

	/** "minecraft:dark_forest" as "Dark Forest (minecraft)"; anything else as it is. */
	private static String pretty(String id) {
		int colon = id.indexOf(':');
		if(colon < 0)
			return id;
		String path = id.substring(colon + 1).replace('_', ' ').replace('/', ' ');
		StringBuilder out = new StringBuilder();
		for(String word : path.split(" "))
			if(!word.isEmpty())
				out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
		String ns = id.substring(0, colon);
		return out.toString().strip() + (ns.equals("minecraft") ? "" : " (" + ns + ")");
	}

	private void printCertificate() {
		CompoundTag award = selected();
		if(award == null)
			return;
		if(award.getInt("level") == 0) {
			session.message(Dos.tr("awards.not_yet"), true);
			return;
		}
		CompoundTag data = new CompoundTag();
		data.putString("file", fileName);
		data.putString("award", award.getString("id"));
		session.app(app, "certificate", data, response -> {
			if(response.ok())
				session.message(Dos.tr("awards.printed", Dos.tr("awards." + award.getString("id"))), false);
			else
				showError(response);
		});
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		int bg = DosColours.BODY_BG;
		switch(page) {
			case LIST -> {
				Dos.frame(g, Dos.tr("awards.title", fileName), Dos.tr("awards.keys"));
				g.put(1, 1, Dos.tr("awards.summary", status == null ? 0 : status.getInt("contacts")), DosColours.TEXT_FG, bg);
				g.put(1, 2, String.format(Locale.ROOT, "%-28s %-8s %14s  %s", Dos.tr("awards.col.award"), Dos.tr("awards.col.level"), Dos.tr("awards.col.count"),
						Dos.tr("awards.col.next")), DosColours.YELLOW, bg);
				g.hline(1, 3, TextGrid.COLS - 2, TextGrid.H, DosColours.BODY_FG, bg);
				if(status == null)
					g.put(2, 4, Dos.tr("files.reading"), DosColours.TEXT_FG, bg);
				else {
					list.render(g, 1, 4, 54, 18, DosColours.TEXT_FG, bg, !hasDialog());
					List<CompoundTag> awards = awards();
					for(int r = 0; r < 18; r++) {
						int index = list.top() + r;
						if(index >= awards.size())
							break;
						CompoundTag a = awards.get(index);
						int[] tiers = tiers(a);
						int level = a.getInt("level");
						double fraction = level >= tiers.length ? 1.0D : Math.min(1.0D, a.getLong("count") / (double)Math.max(1, tiers[level]));
						g.bar(57, 4 + r, 21, fraction, level >= tiers.length ? DosColours.GOOD : level > 0 ? DosColours.ACCENT : DosColours.DIM, bg);
					}
				}
			}
			case DETAIL -> {
				CompoundTag award = selected();
				Dos.frame(g, award == null ? "" : Dos.tr("awards.detail_title", Dos.tr("awards." + award.getString("id")), award.getLong("count")), Dos.tr("awards.detail_keys"));
				detail.render(g, 1, 2, TextGrid.COLS - 2, 20, DosColours.TEXT_FG, bg, !hasDialog());
			}
			case RECORDS -> {
				Dos.frame(g, Dos.tr("awards.records_title"), Dos.tr("awards.detail_keys"));
				g.put(1, 1, String.format(Locale.ROOT, "%-8s %-12s %8s  %s", Dos.tr("awards.col.key"), Dos.tr("awards.col.call"), Dos.tr("awards.col.blocks"),
						Dos.tr("awards.col.date")), DosColours.YELLOW, bg);
				detail.render(g, 1, 2, TextGrid.COLS - 2, 20, DosColours.TEXT_FG, bg, !hasDialog());
			}
		}
		String message = session.message();
		g.putField(0, 23, " " + (message.isEmpty() ? Dos.tr("awards.hint") : message), TextGrid.COLS,
				!message.isEmpty() && session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onEscape() {
		if(page != Page.LIST) {
			page = Page.LIST;
			return true;
		}
		return super.onEscape();
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_F3 -> showDialog(new DosFilePicker(session, Dos.tr("awards.open_title"), ComputerFileFormats.LOG, this::openFile));
			case GLFW.GLFW_KEY_F4 -> refresh();
			case GLFW.GLFW_KEY_F5 -> printCertificate();
			case GLFW.GLFW_KEY_F6 -> showRecords();
			case GLFW.GLFW_KEY_BACKSPACE -> page = Page.LIST;
			default -> {
				return page == Page.LIST ? list.keyPressed(key) : detail.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		return page == Page.LIST ? list.mouseClicked(col, row) : detail.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return page == Page.LIST ? list.mouseScrolled(col, row, amount) : detail.mouseScrolled(col, row, amount);
	}
}
