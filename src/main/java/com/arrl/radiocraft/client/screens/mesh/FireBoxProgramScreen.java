package com.arrl.radiocraft.client.screens.mesh;

import com.arrl.radiocraft.client.screens.radios.HfPanel;
import com.arrl.radiocraft.client.screens.radios.MarinePanels;
import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.MeshDeviceBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.PullStationBlockEntity;
import com.arrl.radiocraft.common.menus.MeshSetupMenu;
import com.arrl.radiocraft.common.network.serverbound.SFireStationNamePacket;
import com.arrl.radiocraft.common.network.serverbound.SMeshSetupPacket;
import com.arrl.radiocraft.common.radio.mesh.MeshNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A fire alarm box's programming panel (2026-11-11), what right-clicking the box opens: the inside of the cabinet, as on
 * the key's panel, with an unlit 16 x 2 LCD and five keys ({@code gui_layouts/fire_alarm_box_program.json}, from
 * {@code tools/fire_alarm_assets.py}). Its items: the frequency, the callsign, the facility (the box's location name)
 * and each location (a pull station or panic button on its wire) by number, with its name.
 * <p>
 * The main menu leads to SETUP and STATUS (route to the controller, signal, the devices it hears). Up and down step
 * through an area's items, left goes back to the menu; ENTER edits one. Editing, left and right move the cursor, up and down change the
 * character under it, ENTER saves and Escape gives up; the keyboard types too. The frequency, callsign and facility go
 * back together in an {@link SMeshSetupPacket}, a station's name in an {@link SFireStationNamePacket}.
 */
public class FireBoxProgramScreen extends AbstractContainerScreen<MeshSetupMenu> {

	/** The LCD's characters a line, and the characters up and down step through. */
	private static final int LCD_COLS = 16, CELL_W = 6;
	private static final String TEXT_CHARS = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-./&#'", CALL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", DIGITS = "0123456789";
	private static final int FREQ_LEN = 8, CALL_MAX = 10;
	private static final long NOTE_MS = 1500L;

	/** Picks the screen for a device's setup: a fire alarm box gets this panel, the rest the plain setup screen. */
	public static AbstractContainerScreen<MeshSetupMenu> create(MeshSetupMenu menu, Inventory inventory, Component title) {
		return menu.isFireBox() ? new FireBoxProgramScreen(menu, inventory, title) : new MeshSetupScreen(menu, inventory, title);
	}

	private enum Kind { FREQUENCY, CALLSIGN, FACILITY, STATION, ALARM_TYPE, INFO, SIGNAL }

	/** One item on the LCD. */
	private static final class Item {
		final Kind kind;
		final int zone;
		String value;
		/** A STATUS line's own title. */
		String fixedTitle = "";

		Item(Kind kind, int zone, String value) {
			this.kind = kind;
			this.zone = zone;
			this.value = value;
		}

		static Item info(String title, String value) {
			Item item = new Item(Kind.INFO, 0, value);
			item.fixedTitle = title;
			return item;
		}

		boolean editable() {
			return kind != Kind.INFO && kind != Kind.SIGNAL;
		}

		int max() {
			return switch(kind) {
				case FREQUENCY -> FREQ_LEN;
				case CALLSIGN -> CALL_MAX;
				case FACILITY -> MeshNode.LABEL_MAX;
				case STATION -> PullStationBlockEntity.NAME_MAX;
				case ALARM_TYPE -> LCD_COLS;
				default -> 0;
			};
		}

		String title() {
			return switch(kind) {
				case FREQUENCY -> "FREQUENCY MHZ";
				case CALLSIGN -> "CALLSIGN";
				case FACILITY -> "FACILITY NAME";
				case STATION -> String.format(Locale.ROOT, "LOCATION %02d", zone);
				case ALARM_TYPE -> String.format(Locale.ROOT, "LOC %02d ALARM", zone);
				case INFO -> fixedTitle;
				case SIGNAL -> "SIGNAL";
			};
		}

		String shown() {
			if(kind == Kind.FREQUENCY && value.isEmpty())
				return "---.----";
			return value.isEmpty() ? "-" : value;
		}
	}

	private final HfPanel panel = HfPanel.loadLayout("fire_alarm_box_program");
	/** The two areas off the main menu: SETUP (what can be set) and STATUS (route, signal, who it hears, 2026-11-11). */
	private static final String[] AREAS = { "SETUP", "STATUS" };
	private final List<Item> setup = new ArrayList<>(), status = new ArrayList<>();
	/** The main menu (-1), or the area open; the menu's line picked; the item in the area. */
	private int area = -1, menuIndex = 0;
	private List<Item> items = setup;
	private int index = 0;
	/** While editing: the value padded to its length, and the cursor in it. Null when browsing. */
	@Nullable
	private StringBuilder edit;
	private int cursor = 0;
	@Nullable
	private String held;
	@Nullable
	private String note;
	private long noteUntil = 0L;
	private double needle = 0.0D;

	public FireBoxProgramScreen(MeshSetupMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = panel.width();
		imageHeight = panel.height();
		setup.add(new Item(Kind.FREQUENCY, 0, menu.hz > 0.0F ? String.format(Locale.ROOT, "%08.4f", menu.hz / 1_000_000.0D) : ""));
		setup.add(new Item(Kind.CALLSIGN, 0, menu.callsign));
		setup.add(new Item(Kind.FACILITY, 0, menu.label));
		for(FireAlarmBoxBlockEntity.Station station : menu.stations)
		{
			setup.add(new Item(Kind.STATION, station.zone(), station.name()));
			setup.add(new Item(Kind.ALARM_TYPE, station.zone(), station.kind().name())); // FIRE, MEDICAL, LOCKDOWN... (2026-11-11)
		}
		status.add(Item.info("ROUTE", switch(menu.hops) {
			case -2 -> "NOT SET UP";
			case -1 -> "NO CONTROLLER";
			case 0 -> "DIRECT TO CTRL";
			case 1 -> "1 HOP TO CTRL";
			default -> menu.hops + " HOPS TO CTRL";
		}));
		status.add(new Item(Kind.SIGNAL, 0, ""));
		if(menu.heard.isEmpty())
			status.add(Item.info("NEIGHBORS", "NONE HEARD"));
		for(MeshSetupMenu.Heard h : menu.heard) {
			String who = switch(h.role()) {
				case CONTROLLER -> "CONTROLLER";
				case BOX -> String.format(Locale.ROOT, "BOX %04d", h.address());
				default -> h.role().name() + " " + h.address();
			};
			status.add(Item.info(String.format(Locale.ROOT, "NBR %3d%%", h.percent()), h.label().isEmpty() ? who : who + " " + h.label()));
		}
	}

	/** The signal line: a bar of ten and the percent, live from the LINK meter. */
	private String signalLine() {
		int percent = (int)Math.round(menu.link() * 100.0D);
		int bars = Math.min(10, (percent + 5) / 10);
		return "#".repeat(bars) + "-".repeat(10 - bars) + String.format(Locale.ROOT, " %3d%%", percent);
	}

	// -------------------- DRAWING --------------------

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(panel.texture(), leftPos, topPos, 0, 0, imageWidth, imageHeight, panel.width(), panel.height());
		needle += (menu.link() - needle) * 0.2D;
		for(HfPanel.Control c : panel.controls()) {
			if(c.type().equals("button"))
				MarinePanels.button(g, leftPos, topPos, c, false, c.name().equals(held));
			else if(c.type().equals("meter"))
				MarinePanels.meter(g, leftPos, topPos, c, needle);
		}
		HfPanel.Control lcd = panel.named("lcd");
		if(lcd == null)
			return;
		int ink = lcd.colour("ink", 0xFF1E281A);
		int x = leftPos + lcd.x() + (lcd.w() - LCD_COLS * CELL_W) / 2, y = topPos + lcd.y() + 2;
		if(area < 0) { // The main menu: SETUP, STATUS.
			boolean showNote = note != null && System.currentTimeMillis() < noteUntil;
			line(g, x, y, showNote ? note : "MAIN MENU", ink, -1, 0);
			line(g, x, y + 11, "> " + AREAS[menuIndex] + " ".repeat(Math.max(1, 9 - AREAS[menuIndex].length())) + (menuIndex + 1) + "/" + AREAS.length, ink, -1, 0);
			return;
		}
		Item item = items.get(index);
		String top, bottom;
		int window = 0;
		boolean showNote = note != null && System.currentTimeMillis() < noteUntil;
		if(edit != null) {
			top = showNote ? note : "EDIT " + item.title();
			window = Math.max(0, Math.min(cursor - LCD_COLS + 1, edit.length() - LCD_COLS));
			window = Math.max(0, Math.min(window, cursor));
			bottom = edit.substring(window, Math.min(edit.length(), window + LCD_COLS));
		}
		else {
			String count = (index + 1) + "/" + items.size();
			top = showNote ? note : item.title();
			if(!showNote && top.length() + count.length() < LCD_COLS)
				top = top + " ".repeat(LCD_COLS - top.length() - count.length()) + count;
			String shown = item.kind == Kind.SIGNAL ? signalLine() : item.shown();
			bottom = shown.length() > LCD_COLS ? shown.substring(0, LCD_COLS - 1) + ">" : shown;
		}
		line(g, x, y, top, ink, -1, 0);
		boolean blink = (System.currentTimeMillis() / 400L) % 2L == 0L;
		line(g, x, y + 11, bottom, ink, edit != null && blink ? cursor - window : -1, lcd.colour("glass", 0xFF96A080));
	}

	/** A line of the LCD, a character a cell; the cell {@code inverse} (or none, -1) dark with the glass's colour in it. */
	private void line(GuiGraphics g, int x, int y, String text, int ink, int inverse, int glass) {
		for(int i = 0; i < Math.min(LCD_COLS, Math.max(text.length(), inverse + 1)); i++) {
			String ch = i < text.length() ? String.valueOf(text.charAt(i)) : " ";
			int cx = x + i * CELL_W;
			int colour = ink;
			if(i == inverse) {
				g.fill(cx, y - 1, cx + CELL_W, y + 9, ink);
				colour = glass;
			}
			if(!ch.equals(" "))
				g.drawString(font, ch, cx + (CELL_W - font.width(ch)) / 2, y, colour, false);
		}
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {}

	// -------------------- KEYS --------------------

	private void key(String name) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.STONE_BUTTON_CLICK_ON, 1.7F, 0.3F));
		note = null;
		if(area < 0) {
			switch(name) {
				case "up" -> menuIndex = Math.floorMod(menuIndex - 1, AREAS.length);
				case "down" -> menuIndex = (menuIndex + 1) % AREAS.length;
				case "enter", "right" -> openArea(menuIndex);
				default -> {}
			}
			return;
		}
		Item item = items.get(index);
		if(edit == null) {
			switch(name) {
				case "up" -> index = Math.floorMod(index - 1, items.size());
				case "down" -> index = (index + 1) % items.size();
				case "left" -> area = -1; // Back to the main menu.
				case "enter", "right" -> {
					if(item.editable())
						startEdit(item);
					else if(name.equals("enter"))
						note("READ ONLY");
				}
				default -> {}
			}
			return;
		}
		if(item.kind == Kind.ALARM_TYPE) {
			switch(name) {
				case "up", "right" -> step(item, 1);
				case "down", "left" -> step(item, -1);
				case "enter" -> save(item);
				default -> {}
			}
			return;
		}
		switch(name) {
			case "left" -> cursor = prevCell(item, cursor);
			case "right" -> cursor = nextCell(item, cursor);
			case "up" -> step(item, 1);
			case "down" -> step(item, -1);
			case "enter" -> save(item);
			default -> {}
		}
	}

	private void openArea(int which) {
		area = which;
		items = which == 0 ? setup : status;
		index = 0;
	}

	/** While editing a location's kind of alarm: the kind shown. Up and down (or left and right) step it. */
	private com.arrl.radiocraft.common.radio.firealarm.AlarmKind editKind = com.arrl.radiocraft.common.radio.firealarm.AlarmKind.FIRE;

	private void showKind() {
		edit = new StringBuilder("< " + editKind.name() + " >");
		cursor = -1; // No character cursor: the whole word steps.
	}

	/** The setup item of kind {@code kind} for the same location as {@code item}. */
	@Nullable
	private Item partner(Item item, Kind kind) {
		for(Item other : setup)
			if(other.kind == kind && other.zone == item.zone)
				return other;
		return null;
	}

	private void startEdit(Item item) {
		if(item.kind == Kind.ALARM_TYPE) {
			editKind = com.arrl.radiocraft.common.radio.firealarm.AlarmKind.of(item.value);
			showKind();
			return;
		}
		String start = item.value;
		if(item.kind == Kind.FREQUENCY && start.isEmpty())
			start = "151.0000";
		edit = new StringBuilder(start);
		while(edit.length() < item.max())
			edit.append(item.kind == Kind.FREQUENCY ? '0' : ' ');
		cursor = 0;
	}

	/** The cells the cursor stops on: every one but the frequency's point. */
	private static boolean editable(Item item, int cell) {
		return !(item.kind == Kind.FREQUENCY && cell == 3);
	}

	private int nextCell(Item item, int from) {
		int at = from;
		do
			at = Math.min(item.max() - 1, at + 1);
		while(!editable(item, at) && at < item.max() - 1);
		return editable(item, at) ? at : from;
	}

	private int prevCell(Item item, int from) {
		int at = from;
		do
			at = Math.max(0, at - 1);
		while(!editable(item, at) && at > 0);
		return editable(item, at) ? at : from;
	}

	private static String charsFor(Item item) {
		return switch(item.kind) {
			case FREQUENCY -> DIGITS;
			case CALLSIGN -> " " + CALL_CHARS;
			default -> TEXT_CHARS;
		};
	}

	private void step(Item item, int by) {
		if(edit == null)
			return;
		if(item.kind == Kind.ALARM_TYPE) {
			editKind = editKind.step(by);
			showKind();
			return;
		}
		String chars = charsFor(item);
		int at = chars.indexOf(edit.charAt(cursor));
		edit.setCharAt(cursor, chars.charAt(Math.floorMod((at < 0 ? 0 : at) + by, chars.length())));
	}

	private void type(Item item, char c) {
		if(edit == null || item.kind == Kind.ALARM_TYPE)
			return;
		c = Character.toUpperCase(c);
		if(charsFor(item).indexOf(c) < 0)
			return;
		edit.setCharAt(cursor, c);
		cursor = nextCell(item, cursor);
	}

	private void note(String text) {
		note = text;
		noteUntil = System.currentTimeMillis() + NOTE_MS;
	}

	private void save(Item item) {
		if(edit == null)
			return;
		String value = edit.toString().strip();
		Item freq = setup.get(0), call = setup.get(1), facility = setup.get(2);
		if(item.kind == Kind.STATION || item.kind == Kind.ALARM_TYPE) { // A location: its name and its kind go together.
			Item name = item.kind == Kind.STATION ? item : partner(item, Kind.STATION);
			Item type = item.kind == Kind.ALARM_TYPE ? item : partner(item, Kind.ALARM_TYPE);
			if(item.kind == Kind.STATION)
				item.value = MeshNode.clean(value);
			else
				item.value = editKind.name();
			SFireStationNamePacket.send(menu.pos, item.zone, name == null ? "" : name.value, com.arrl.radiocraft.common.radio.firealarm.AlarmKind.of(type == null ? "" : type.value));
			edit = null;
			note("SAVED");
			return;
		}
		String freqText = item.kind == Kind.FREQUENCY ? value : freq.value;
		String callText = item.kind == Kind.CALLSIGN ? value.replace(" ", "") : call.value;
		String label = item.kind == Kind.FACILITY ? MeshNode.clean(value) : facility.value;
		float hz;
		try {
			hz = freqText.isEmpty() ? 0.0F : (float)(Double.parseDouble(freqText) * 1_000_000.0D);
		}
		catch(NumberFormatException e) {
			hz = 0.0F;
		}
		item.value = item.kind == Kind.FREQUENCY ? value : item.kind == Kind.CALLSIGN ? callText : label;
		edit = null;
		// Frequency and callsign go together: until both are good, only what is set is kept here.
		String problem = MeshDeviceBlockEntity.setupProblem(hz, callText);
		if(problem == null) {
			SMeshSetupPacket.send(menu.pos, hz, callText, label);
			note("SAVED");
		}
		else if(item.kind == Kind.FACILITY)
			note("SET FREQ + CALL");
		else if(problem.equals("bad_frequency"))
			note(freqText.isEmpty() ? "SET FREQUENCY" : "BAD FREQUENCY");
		else
			note(callText.isEmpty() ? "SET CALLSIGN" : "BAD CALLSIGN");
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HfPanel.Control c = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		if(c != null && button == 0 && c.type().equals("button")) {
			held = c.name();
			key(c.name());
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		held = null;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_UP -> key("up");
			case GLFW.GLFW_KEY_DOWN -> key("down");
			case GLFW.GLFW_KEY_LEFT -> key("left");
			case GLFW.GLFW_KEY_RIGHT -> key("right");
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> key("enter");
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if(edit != null && items.get(index).kind != Kind.ALARM_TYPE) {
					Item item = items.get(index);
					cursor = prevCell(item, cursor);
					edit.setCharAt(cursor, item.kind == Kind.FREQUENCY ? '0' : ' ');
				}
			}
			case GLFW.GLFW_KEY_ESCAPE -> {
				if(edit != null)
					edit = null; // Give up the edit; Escape again goes back to the menu, then closes.
				else if(area >= 0)
					area = -1;
				else
					return super.keyPressed(key, scanCode, modifiers);
			}
			default -> {
				if(edit != null)
					return true; // Typing: never the inventory key or hotbar.
				return super.keyPressed(key, scanCode, modifiers);
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		if(edit != null) {
			type(items.get(index), c);
			return true;
		}
		return super.charTyped(c, modifiers);
	}
}
