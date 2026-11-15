package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosMenuBar;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LAN REDSTONE (2026-11-13): the Computer Redstone blocks of this computer and of the LAN's other computers this player
 * may set. Left, the blocks; right, the picked one: what it gives out and reads, by its level names, its named levels
 * and (for this computer's) who else may set it. Up/Down picks a block, Left/Right a named level; Enter turns it on at
 * that level, P pulses it, O turns it off, L sets any level. For this computer's: F6 names it, Ins adds a named level,
 * Del removes one, F3 sets who may set it.
 */
public final class RedstoneApp extends DosApp {

	private static final long STATUS_MS = 500L;

	@Nullable
	private CompoundTag status;
	private long lastStatus;
	private int selected = 0, entry = 0;

	public RedstoneApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		setMenuBar(new DosMenuBar(List.of(
				new DosMenuBar.Menu(Dos.tr("menu.file"), List.of(
						item("menu.exit", "Esc", GLFW.GLFW_KEY_ESCAPE))),
				new DosMenuBar.Menu(Dos.tr("menu.redstone.output"), List.of(
						item("menu.redstone.on", "Enter", GLFW.GLFW_KEY_ENTER).when(() -> picked() != null),
						item("menu.redstone.pulse", "P", GLFW.GLFW_KEY_P).when(() -> picked() != null),
						item("menu.redstone.off", "O", GLFW.GLFW_KEY_O).when(() -> picked() != null),
						item("menu.redstone.level", "L", GLFW.GLFW_KEY_L).when(() -> picked() != null))),
				new DosMenuBar.Menu(Dos.tr("menu.edit"), List.of(
						item("menu.redstone.rename", "F6", GLFW.GLFW_KEY_F6).when(this::editable),
						item("menu.redstone.add_entry", "Ins", GLFW.GLFW_KEY_INSERT).when(this::editable),
						item("menu.redstone.delete_entry", "Del", GLFW.GLFW_KEY_DELETE).when(this::editable),
						item("menu.redstone.access", "F3", GLFW.GLFW_KEY_F3).when(this::editable))),
				new DosMenuBar.Menu(Dos.tr("menu.view"), List.of(
						item("menu.previous", "Up", GLFW.GLFW_KEY_UP),
						item("menu.next", "Down", GLFW.GLFW_KEY_DOWN),
						item("menu.redstone.previous_level", "Left", GLFW.GLFW_KEY_LEFT).when(() -> pickedEntry() != null),
						item("menu.redstone.next_level", "Right", GLFW.GLFW_KEY_RIGHT).when(() -> pickedEntry() != null))))));
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
	public void tick() {
		if(!hasDialog() && System.currentTimeMillis() - lastStatus > STATUS_MS)
			refresh();
	}

	private void refresh() {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(response.ok())
				status = response.data();
		});
	}

	/** Every block: this computer's first, then the LAN's. */
	private List<CompoundTag> blocks() {
		List<CompoundTag> out = new ArrayList<>();
		if(status == null)
			return out;
		for(String list : new String[] { "local", "remote" }) {
			ListTag tags = status.getList(list, Tag.TAG_COMPOUND);
			for(int i = 0; i < tags.size(); i++)
				out.add(tags.getCompound(i));
		}
		return out;
	}

	@Nullable
	private CompoundTag picked() {
		List<CompoundTag> blocks = blocks();
		return blocks.isEmpty() ? null : blocks.get(Math.max(0, Math.min(selected, blocks.size() - 1)));
	}

	/** The picked block is this computer's, and this player may change its settings. */
	private boolean editable() {
		CompoundTag b = picked();
		return b != null && b.getString("computer").isEmpty() && status != null && status.getBoolean("editable");
	}

	@Nullable
	private CompoundTag pickedEntry() {
		CompoundTag b = picked();
		if(b == null)
			return null;
		ListTag entries = b.getList("entries", Tag.TAG_COMPOUND);
		return entries.isEmpty() ? null : entries.getCompound(Math.max(0, Math.min(entry, entries.size() - 1)));
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		CompoundTag b = picked();
		switch(key) {
			case GLFW.GLFW_KEY_UP -> {
				selected = Math.max(0, selected - 1);
				entry = 0;
			}
			case GLFW.GLFW_KEY_DOWN -> {
				selected = Math.min(Math.max(0, blocks().size() - 1), selected + 1);
				entry = 0;
			}
			case GLFW.GLFW_KEY_LEFT -> entry = Math.max(0, entry - 1);
			case GLFW.GLFW_KEY_RIGHT -> entry++;
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_P -> {
				CompoundTag e = pickedEntry();
				if(b != null)
					set(b, e == null ? 15 : e.getInt("level"), key == GLFW.GLFW_KEY_P);
			}
			case GLFW.GLFW_KEY_O -> {
				if(b != null)
					set(b, 0, false);
			}
			case GLFW.GLFW_KEY_L -> {
				if(b != null) {
					DosInput field = new DosInput("", 2, Character::isDigit);
					showDialog(DosDialog.ask(Dos.tr("redstone.level_title"), Dos.tr("redstone.level_hint"), field, text -> {
						try {
							set(b, Integer.parseInt(text.trim()), false);
						}
						catch(NumberFormatException ignored) {}
					}));
				}
			}
			case GLFW.GLFW_KEY_F6 -> { // Not F2: Minecraft takes that for screenshots.
				if(editable() && b != null) {
					DosInput field = new DosInput(b.getString("name"), 16, c -> c >= 32 && c < 127);
					showDialog(DosDialog.ask(Dos.tr("redstone.rename_title"), Dos.tr("redstone.rename_hint"), field, text -> {
						CompoundTag data = at(b);
						data.putString("name", text);
						send("rename", data);
					}));
				}
			}
			case GLFW.GLFW_KEY_INSERT -> {
				if(editable() && b != null) {
					DosInput field = new DosInput("", 20, c -> c >= 32 && c < 127);
					showDialog(DosDialog.ask(Dos.tr("redstone.entry_title"), Dos.tr("redstone.entry_hint"), field, text -> {
						int eq = text.lastIndexOf('=');
						if(eq <= 0)
							return;
						try {
							CompoundTag data = at(b);
							data.putString("label", text.substring(0, eq).trim());
							data.putInt("level", Integer.parseInt(text.substring(eq + 1).trim()));
							send("entry", data);
						}
						catch(NumberFormatException ignored) {}
					}));
				}
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if(editable() && b != null && pickedEntry() != null) {
					CompoundTag data = at(b);
					data.putInt("index", Math.max(0, Math.min(entry, b.getList("entries", Tag.TAG_COMPOUND).size() - 1)));
					send("unentry", data);
				}
			}
			case GLFW.GLFW_KEY_F3 -> {
				if(editable() && b != null) {
					DosInput players = new DosInput(String.join(", ", strings(b.getList("players", Tag.TAG_STRING))), 60, c -> c >= 32 && c < 127);
					showDialog(DosDialog.ask(Dos.tr("redstone.players_title"), Dos.tr("redstone.players_hint"), players, playerText -> {
						DosInput computers = new DosInput(String.join(", ", strings(b.getList("computers", Tag.TAG_STRING))), 60, c -> c >= 32 && c < 127);
						showDialog(DosDialog.ask(Dos.tr("redstone.computers_title"), Dos.tr("redstone.computers_hint"), computers, computerText -> {
							CompoundTag data = at(b);
							data.putString("players", playerText);
							data.putString("computers", computerText);
							send("access", data);
						}));
					}));
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private static List<String> strings(ListTag list) {
		List<String> out = new ArrayList<>();
		for(int i = 0; i < list.size(); i++)
			out.add(list.getString(i));
		return out;
	}

	private static CompoundTag at(CompoundTag block) {
		CompoundTag data = new CompoundTag();
		data.putString("computer", block.getString("computer"));
		data.putLong("pos", block.getLong("pos"));
		return data;
	}

	private void set(CompoundTag block, int level, boolean pulse) {
		CompoundTag data = at(block);
		data.putInt("level", Math.max(0, Math.min(15, level)));
		data.putBoolean("pulse", pulse);
		send("set", data);
	}

	private void send(String action, CompoundTag data) {
		session.app(app, action, data, response -> {
			if(response.ok())
				refresh();
			else
				showError(response);
		});
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("redstone.title"), Dos.tr("redstone.keys"));
		int bg = DosColours.BODY_BG;
		CompoundTag s = status;
		if(s == null) {
			g.put(3, 3, Dos.tr("redstone.reading"), DosColours.BODY_FG, bg);
			return;
		}
		String name = s.getString("name");
		g.put(2, 1, Dos.fit(name.isEmpty() ? Dos.tr("redstone.no_name") : Dos.tr("redstone.this", name), 77), name.isEmpty() ? DosColours.YELLOW : DosColours.TEXT_FG, bg);

		g.box(1, 2, 38, 20, false, Dos.tr("redstone.blocks"), DosColours.TEXT_FG, bg);
		List<CompoundTag> blocks = blocks();
		selected = Math.max(0, Math.min(selected, Math.max(0, blocks.size() - 1)));
		if(blocks.isEmpty())
			for(int i = 0; i < 3; i++)
				g.put(3, 4 + i, Dos.fit(Dos.tr("redstone.none_" + i), 34), DosColours.BODY_FG, bg);
		int first = Math.max(0, selected - 17);
		for(int i = first; i < blocks.size() && i - first < 18; i++) {
			CompoundTag b = blocks.get(i);
			String where = b.getString("computer").isEmpty() ? "" : b.getString("computer") + ":";
			String label = b.getString("name").isEmpty() ? Dos.tr("redstone.unnamed") : b.getString("name");
			String line = String.format(Locale.ROOT, "%-22s %s", Dos.fit(where + label, 22), b.getInt("out") > 0 ? "ON " + b.getInt("out") : "off");
			boolean picked = i == selected;
			int fg = b.getInt("out") > 0 ? DosColours.LIGHT_RED : DosColours.TEXT_FG;
			g.putField(2, 3 + i - first, Dos.fit(line, 36), 36, picked ? DosColours.HIGHLIGHT_FG : fg, picked ? DosColours.HIGHLIGHT_BG : bg);
		}

		g.box(40, 2, 39, 20, false, Dos.tr("redstone.details"), DosColours.TEXT_FG, bg);
		CompoundTag b = picked();
		if(b == null)
			return;
		int row = 3;
		String computer = b.getString("computer").isEmpty() ? Dos.tr("redstone.here") : b.getString("computer");
		g.put(42, row++, Dos.fit(Dos.tr("redstone.on_computer", computer), 36), DosColours.DIM, bg);
		g.put(42, row++, Dos.fit(Dos.tr("redstone.output", b.getString("outText")), 36), b.getInt("out") > 0 ? DosColours.LIGHT_RED : DosColours.TEXT_FG, bg);
		g.put(42, row++, Dos.fit(b.getInt("out") > 0 ? Dos.tr("redstone.input_busy") : Dos.tr("redstone.input", b.getString("inText")), 36), DosColours.TEXT_FG, bg);
		row++;
		g.put(42, row++, Dos.tr("redstone.entries"), DosColours.YELLOW, bg);
		ListTag entries = b.getList("entries", Tag.TAG_COMPOUND);
		entry = Math.max(0, Math.min(entry, Math.max(0, entries.size() - 1)));
		if(entries.isEmpty())
			g.put(42, row++, Dos.fit(Dos.tr("redstone.no_entries"), 36), DosColours.BODY_FG, bg);
		for(int i = 0; i < entries.size() && row < 16; i++) {
			CompoundTag e = entries.getCompound(i);
			boolean picked = i == entry;
			g.putField(42, row++, Dos.fit(String.format(Locale.ROOT, "%2d  %s", e.getInt("level"), e.getString("label")), 36), 36,
					picked ? DosColours.HIGHLIGHT_FG : DosColours.TEXT_FG, picked ? DosColours.HIGHLIGHT_BG : bg);
		}
		if(b.getString("computer").isEmpty()) {
			g.put(42, 17, Dos.tr("redstone.access"), DosColours.YELLOW, bg);
			List<String> players = strings(b.getList("players", Tag.TAG_STRING));
			List<String> computers = strings(b.getList("computers", Tag.TAG_STRING));
			g.put(42, 18, Dos.fit(Dos.tr("redstone.players", players.isEmpty() ? "-" : String.join(", ", players)), 36), DosColours.TEXT_FG, bg);
			g.put(42, 19, Dos.fit(Dos.tr("redstone.computers", computers.isEmpty() ? "-" : String.join(", ", computers)), 36), DosColours.TEXT_FG, bg);
			if(!s.getBoolean("editable"))
				g.put(42, 20, Dos.fit(Dos.tr("redstone.owner_only"), 36), DosColours.DIM, bg);
		}
	}
}
