package com.arrl.radiocraft.client.screens.mesh;

import com.arrl.radiocraft.client.screens.radios.HfPanel;
import com.arrl.radiocraft.client.screens.radios.MarinePanels;
import com.arrl.radiocraft.common.menus.ScadaMasterMenu;
import com.arrl.radiocraft.common.radio.scada.Scada;
import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * The SCADA master's console (2026-11-11), after a 1970s mainframe's: {@code gui_layouts/scada_master.json} over
 * {@code textures/gui/scada/scada_master.png} (from {@code tools/scada_assets.py}). It boots with the maker's logo,
 * then the CRT is a text terminal ({@link ScadaCrt}): click a tab or turn DISPLAY for POINTS, RULES, I/O and SETUP
 * (Tab and the arrow keys work too); scroll with the arrows or the wheel. SETUP's fields (frequency, callsign, name)
 * take typing straight in, ENTER saves, Escape undoes; POLL steps with left and right (2026-11-11). POLL SEC steps 1, 2, 3 s an RTU. The LOCAL
 * I/O toggles, and the I/O page's rows, step a face of the master OFF, IN, OUT with each click (right click back).
 * Lamps: power, POLL (a poll waiting), TX (going out), COMM FAIL; the meter reads the share of polls answered.
 */
public class ScadaMasterScreen extends AbstractContainerScreen<ScadaMasterMenu> {

	private final HfPanel panel = HfPanel.loadLayout("scada_master");
	private final Crt crt = new Crt(ScadaCrt.COLS, ScadaCrt.ROWS);
	private final Crt boot = new Crt(ScadaCrt.BOOT_COLS, ScadaCrt.BOOT_ROWS);
	private final long opened = System.currentTimeMillis();
	private int tab = 0;
	private int scroll = 0;
	@Nullable
	private String held;
	private double needle = 0.0D;
	/** SETUP's fields as typed (FREQ, CALL, NAME), the row picked, whether anything is unsaved, and the last note. */
	private final String[] fields = { "", "", "" };
	private int setupRow = 0;
	private boolean dirty = false;
	private String note = "";
	private long noteUntil = 0L;
	private static final int[] FIELD_MAX = { 9, 10, 32 };

	public ScadaMasterScreen(ScadaMasterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = panel.width();
		imageHeight = panel.height();
	}

	@Nullable
	private CompoundTag status() {
		return MeshScreens.get(menu.getBlockPos());
	}

	private boolean booting() {
		return System.currentTimeMillis() - opened < ScadaCrt.BOOT_MS;
	}

	// -------------------- STATE --------------------

	private Scada.FaceMode faceMode(int face) {
		CompoundTag s = status();
		String modes = s == null ? "" : s.getString("modes"); // "FNU": OFF, IN, OUT by the mode's second letter.
		char c = face < modes.length() ? modes.charAt(face) : 'F';
		return c == 'N' ? Scada.FaceMode.IN : c == 'U' ? Scada.FaceMode.OUT : Scada.FaceMode.OFF;
	}

	private boolean on() {
		CompoundTag s = status();
		return s != null && !s.getString("mhz").isEmpty();
	}

	/** The share of polls answered, over every RTU. */
	private double answered() {
		CompoundTag s = status();
		if(s == null)
			return 0.0D;
		int polls = 0, replies = 0;
		ListTag units = s.getList("units", Tag.TAG_COMPOUND);
		for(int i = 0; i < units.size(); i++) {
			polls += units.getCompound(i).getInt("polls");
			replies += units.getCompound(i).getInt("replies");
		}
		return polls == 0 ? 0.0D : replies / (double)polls;
	}

	private boolean anyFail() {
		CompoundTag s = status();
		if(s == null)
			return false;
		ListTag units = s.getList("units", Tag.TAG_COMPOUND);
		for(int i = 0; i < units.size(); i++)
			if(units.getCompound(i).getBoolean("fail"))
				return true;
		return false;
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		HfPanel.Control over = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		String tip = over == null ? null : tooltip(over.name());
		if(tip != null)
			g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(panel.texture(), leftPos, topPos, 0, 0, imageWidth, imageHeight, panel.width(), panel.height());
		needle += (answered() - needle) * 0.2D;
		CompoundTag s = status();
		for(HfPanel.Control c : panel.controls()) {
			String name = c.name();
			switch(c.type()) {
				case "knob3" -> MarinePanels.knob3(g, leftPos, topPos, c, name.equals("poll") ? Integer.toString(s == null ? 1 : Math.max(1, s.getInt("poll"))) : ScadaCrt.TABS[tab]);
				case "switch" -> {
					int face = faceIndex(name);
					if(face >= 0)
						MarinePanels.switchHandle(g, leftPos, topPos, c, faceMode(face).name());
				}
				case "lamp" -> {
					boolean lit = switch(name) {
						case "power_lamp" -> on() || booting();
						case "poll_lamp" -> s != null && s.getBoolean("polling");
						case "tx_lamp" -> s != null && s.getBoolean("sending");
						case "fail_lamp" -> anyFail();
						default -> false;
					};
					if(lit)
						MarinePanels.lamp(g, leftPos, topPos, c, c.colour("colour", 0xFF40FF60));
				}
				case "button" -> MarinePanels.button(g, leftPos, topPos, c, false, name.equals(held));
				case "meter" -> MarinePanels.meter(g, leftPos, topPos, c, needle);
				case "display" -> {
					int ink = c.colour("ink", 0xFF40FF60);
					if(booting()) {
						ScadaCrt.boot(boot, System.currentTimeMillis() - opened);
						boot.drawGui(g, font, leftPos + c.x() + 2, topPos + c.y() + 2, c.w() - 4, c.h() - 4, ink);
					}
					else {
						scroll = Math.max(0, Math.min(scroll, ScadaCrt.scrollMax(s, tab)));
						ScadaCrt.fill(crt, s, tab, scroll, tab == 3 ? setupView(s) : null);
						crt.drawGui(g, font, leftPos + c.x() + 2, topPos + c.y() + 2, c.w() - 4, c.h() - 4, ink);
					}
				}
				default -> {}
			}
		}
	}

	private static int faceIndex(String name) {
		return name.startsWith("face_") && name.length() == 6 ? ScadaRules.FACES.indexOf(Character.toUpperCase(name.charAt(5))) : -1;
	}

	@Nullable
	private String tooltip(String name) {
		int face = faceIndex(name);
		if(face >= 0)
			return "Master's " + ScadaCrt.faceName(face).toLowerCase(Locale.ROOT) + " side (0." + ScadaRules.FACES.charAt(face) + "): " + faceMode(face).name()
					+ ". Click: OFF > IN > OUT. IN reads redstone there; OUT gives off what the rules set.";
		CompoundTag s = status();
		return switch(name) {
			case "poll" -> "POLL SEC: each RTU's poll takes " + (s == null ? 1 : s.getInt("poll")) + " s. Click to change.";
			case "page" -> "DISPLAY: " + ScadaCrt.TABS[tab] + ". Click for the next page.";
			case "scroll_up", "scroll_down" -> "Scroll the screen";
			case "meter" -> String.format(Locale.ROOT, "POLL OK: %d%% of polls answered", Math.round(answered() * 100.0D));
			case "fail_lamp" -> "COMM FAIL: an RTU has missed three polls in a row";
			case "poll_lamp" -> "POLL: waiting for an RTU's reply";
			case "tx_lamp" -> "TX: a poll going out";
			case "power_lamp" -> on() ? "On " + s.getString("mhz") + " MHz" : "No frequency: the SETUP page";
			default -> null;
		};
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {}

	// -------------------- SETUP (typed straight in, 2026-11-11) --------------------

	/** What SETUP shows: the typed fields (the master's own until something is typed). */
	private ScadaCrt.SetupView setupView(@Nullable CompoundTag s) {
		if(!dirty && s != null) {
			fields[0] = s.getString("mhz");
			fields[1] = s.getString("license");
			fields[2] = s.getString("label");
		}
		boolean blink = (System.currentTimeMillis() / 400L) % 2L == 0L;
		return new ScadaCrt.SetupView(fields.clone(), setupRow, blink && setupRow < ScadaCrt.SETUP_FIELDS, dirty,
				System.currentTimeMillis() < noteUntil ? note : "");
	}

	private void note(String text) {
		note = text;
		noteUntil = System.currentTimeMillis() + 2500L;
	}

	/** True if {@code c} may go in field {@code field}. */
	private static boolean accepts(int field, char c) {
		return switch(field) {
			case 0 -> Character.isDigit(c) || c == '.';
			case 1 -> Character.isLetterOrDigit(c) && c < 128;
			default -> c >= 32 && c < 127;
		};
	}

	private void typed(char c) {
		if(setupRow >= ScadaCrt.SETUP_FIELDS || !accepts(setupRow, c) || fields[setupRow].length() >= FIELD_MAX[setupRow])
			return;
		fields[setupRow] += Character.toUpperCase(c);
		dirty = true;
	}

	private void backspace() {
		if(setupRow < ScadaCrt.SETUP_FIELDS && !fields[setupRow].isEmpty()) {
			fields[setupRow] = fields[setupRow].substring(0, fields[setupRow].length() - 1);
			dirty = true;
		}
	}

	private void stepPoll(int by) {
		CompoundTag s = status();
		int now = s == null ? 1 : Math.max(1, s.getInt("poll"));
		send(ScadaMasterMenu.pollButton(Math.floorMod(now - 1 + by, ScadaRules.MAX_POLL) + 1));
		click(true);
	}

	/** ENTER: checks the frequency and callsign and sends all three. */
	private void saveSetup() {
		float hz;
		try {
			hz = fields[0].isEmpty() ? 0.0F : (float)(Double.parseDouble(fields[0]) * 1_000_000.0D);
		}
		catch(NumberFormatException e) {
			hz = 0.0F;
		}
		String problem = com.arrl.radiocraft.common.blockentities.mesh.MeshDeviceBlockEntity.setupProblem(hz, fields[1]);
		if(problem != null) {
			note(problem.equals("bad_frequency") ? (fields[0].isEmpty() ? "TYPE A FREQUENCY" : "BAD FREQ: 150-174 OR 30-50") : "BAD CALL: 3-10 LETTERS/DIGITS");
			setupRow = problem.equals("bad_frequency") ? 0 : 1;
			return;
		}
		com.arrl.radiocraft.common.network.serverbound.SMeshSetupPacket.send(menu.getBlockPos(), hz, fields[1], fields[2]);
		dirty = false;
		note("SAVED");
	}

	/** Keys on the SETUP page; false if it is not one SETUP takes. */
	private boolean setupKey(int key, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_UP -> setupRow = Math.floorMod(setupRow - 1, ScadaCrt.SETUP_ROWS);
			case GLFW.GLFW_KEY_DOWN -> setupRow = (setupRow + 1) % ScadaCrt.SETUP_ROWS;
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
				if(setupRow == ScadaCrt.SETUP_FIELDS)
					stepPoll(key == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
				else
					setTab(tab + (key == GLFW.GLFW_KEY_RIGHT ? 1 : -1));
			}
			case GLFW.GLFW_KEY_TAB -> setTab(tab + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1));
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> saveSetup();
			case GLFW.GLFW_KEY_BACKSPACE -> backspace();
			default -> {
				return false;
			}
		}
		return true;
	}

	// -------------------- CONTROLS --------------------

	private void send(int id) {
		Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
	}

	private void click(boolean lever) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(lever ? SoundEvents.LEVER_CLICK : SoundEvents.STONE_BUTTON_CLICK_ON, 1.3F, 0.4F));
	}

	private void setTab(int next) {
		tab = Math.floorMod(next, ScadaCrt.TABS.length);
		scroll = 0;
	}

	/** Steps the master's face {@code face} one mode on (or back). */
	private void cycleFace(int face, boolean back) {
		Scada.FaceMode[] modes = Scada.FaceMode.values();
		Scada.FaceMode next = modes[Math.floorMod(faceMode(face).ordinal() + (back ? -1 : 1), modes.length)];
		send(ScadaMasterMenu.faceButton(face, next));
		click(true);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(button != 0 && button != 1)
			return super.mouseClicked(mouseX, mouseY, button);
		boolean back = button == 1;
		HfPanel.Control c = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		HfPanel.Control screen = panel.named("crt");
		if(screen != null && !booting()) { // On the CRT: a tab, an I/O row, or SETUP's edit line.
			int[] cell = crt.cellAt(mouseX, mouseY, leftPos + screen.x() + 2, topPos + screen.y() + 2, screen.w() - 4, screen.h() - 4);
			if(cell != null) {
				int picked = ScadaCrt.tabAt(cell[0], cell[1]);
				if(picked >= 0) {
					setTab(picked);
					click(false);
					return true;
				}
				int face = cell[1] - ScadaCrt.IO_FIRST_ROW;
				if(tab == 2 && face >= 0 && face < 6) {
					cycleFace(face, back);
					return true;
				}
				int row = tab == 3 ? ScadaCrt.setupRowAt(cell[1]) : -1;
				if(row >= 0) { // A field to type into, or POLL (a click steps it, right click back).
					if(row == ScadaCrt.SETUP_FIELDS && setupRow == row)
						stepPoll(back ? -1 : 1);
					else
						click(false);
					setupRow = row;
					return true;
				}
			}
		}
		if(c != null) {
			int face = faceIndex(c.name());
			if(face >= 0 && c.type().equals("switch")) { // Anywhere on a toggle: the next position.
				cycleFace(face, back);
				return true;
			}
			switch(c.name()) {
				case "poll" -> {
					stepPoll(back ? -1 : 1);
					return true;
				}
				case "page" -> {
					setTab(tab + (back ? -1 : 1));
					click(true);
					return true;
				}
				case "scroll_up", "scroll_down" -> {
					held = c.name();
					scroll = Math.max(0, scroll + (c.name().equals("scroll_up") ? -1 : 1));
					click(false);
					return true;
				}
				default -> {}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if(tab == 3 && !booting()) {
			if(key == GLFW.GLFW_KEY_ESCAPE && dirty) { // Undo what was typed; Escape again closes.
				dirty = false;
				note("UNDONE");
				return true;
			}
			if(setupKey(key, modifiers)) {
				click(false);
				return true;
			}
			if(key != GLFW.GLFW_KEY_ESCAPE)
				return true; // Typing: never the inventory key or the hotbar.
		}
		switch(key) {
			case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_RIGHT -> setTab(tab + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1));
			case GLFW.GLFW_KEY_LEFT -> setTab(tab - 1);
			case GLFW.GLFW_KEY_UP -> scroll = Math.max(0, scroll - 1);
			case GLFW.GLFW_KEY_DOWN -> scroll++;
			default -> {
				return super.keyPressed(key, scanCode, modifiers);
			}
		}
		click(false);
		return true;
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		if(tab == 3 && !booting()) {
			typed(c);
			return true;
		}
		return super.charTyped(c, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY != 0.0D) {
			scroll = Math.max(0, scroll - (int)Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		held = null;
		return super.mouseReleased(mouseX, mouseY, button);
	}
}
