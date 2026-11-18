package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.menus.RadioMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.radio.Band;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The QRP radios' screen (2026-11-13), after the maintainer's photo of a home-built 40m QRP transceiver: a glossy
 * panel (red for 40m, blue for 20m) screwed into a pine box, drawn here rather than from a texture. No display: two
 * lamps tell you when you are near (lit) or past (blinking) the bottom and top band edges.
 * <ul>
 *     <li>SUPPLY and PHONES jacks and the ANTENNA socket (top left), a label plate, the plug-in CRYSTAL (top right).</li>
 *     <li>TUNING: the big knob on its 0-10 skirt (the skirt reads where you are in the band): scroll, drag up and
 *     down, or click its left half (down) or right half (up). Shift, or the x10 switch, for ten steps at a time.</li>
 *     <li>OFF / RECEIVE / TRANSMIT: the pointer knob. TRANSMIT holds the transmitter keyed on SSB; on CW the KEY
 *     keys it.</li>
 *     <li>VOLUME, MIC, SQUELCH and ATTEN (the RF gain turned down): scroll, or left click up and right click down.</li>
 *     <li>MODE (SSB / CW) and AGC (OFF / SLOW / FAST) toggles, the x10 switch, and QUICK (press to go to the stored
 *     frequency and back, hold to store this one).</li>
 *     <li>KEY: hold the big knob to key (CW) or talk (SSB); right click it for the hand mic.</li>
 * </ul>
 */
public class QrpPanelScreen<T extends RadioMenu<?>> extends HFRadioScreen<T> {

	/** The panel's colours: its face, a lighter gloss and a darker edge. */
	public record Finish(int face, int gloss, int edge) {}

	public static final Finish RED = new Finish(0xFFC41C1C, 0xFFE8504A, 0xFF7E0E0E);
	public static final Finish BLUE = new Finish(0xFF1C4CB4, 0xFF5A88E8, 0xFF0E2A6A);

	private static final int W = 280, H = 180, FRAME = 8;
	private static final int WOOD = 0xFFCDB08A, WOOD_DARK = 0xFFA88A62, PRINT = 0xFFF6EEE4, SILVER = 0xFFC4C4C8, SILVER_DARK = 0xFF7A7A80;
	private static final int BAKELITE = 0xFF2E1610, BAKELITE_LIGHT = 0xFF4A2A1E;
	/** Where things are, in screen pixels from the screen's corner: centre x, centre y, radius. */
	private static final int[] TUNING = {64, 102, 20}, SKIRT = {64, 102, 29}, SWITCH = {170, 100, 11}, KEY = {240, 146, 13};
	private static final int[][] KNOBS = {{34, 152, 10}, {78, 152, 10}, {122, 152, 10}, {166, 152, 10}};
	private static final String[] KNOB_NAMES = {"VOLUME", "MIC", "SQUELCH", "ATTEN"};
	private static final int[] LOW_LAMP = {110, 86, 3}, HIGH_LAMP = {110, 104, 3};
	private static final int[] MODE = {214, 66, 5}, AGC = {250, 66, 5}, COARSE = {214, 106, 5}, QUICK = {250, 106, 5};
	/** The switch's positions, degrees from straight up. */
	private static final double[] SWITCH_ANGLES = {-55.0D, 0.0D, 55.0D};
	private static final String[] SWITCH_NAMES = {"OFF", "RECEIVE", "TRANSMIT"};
	private static final String[] AGC_NAMES = {"OFF", "SLOW", "FAST"};
	private static final double DRAG_PER_STEP = 5.0D;
	private static final long HOLD_MS = 700L;
	/** Radios with x10 latched, by position (client only). */
	private static final Map<Long, Boolean> COARSE_ON = new HashMap<>();

	private final Finish finish;
	/** TRANSMIT selected; and whether that holds PTT down (SSB). */
	private boolean transmit = false, transmitPtt = false;
	/** The KEY knob held down. */
	private boolean keyHeld = false;
	/** Dragging the tuning knob: the mouse's last y and the drag not yet turned into steps. */
	private boolean tuning = false;
	private double tuneY, tuneDrag;
	/** When QUICK went down (0 for not held), and whether its hold has fired. */
	private long quickSince = 0L;
	private boolean quickFired = false;

	public QrpPanelScreen(T menu, Inventory inventory, Component title, String textureName, Finish finish) {
		super(menu, inventory, title, Radiocraft.id("textures/gui/" + textureName + ".png"), Radiocraft.id("textures/gui/" + textureName + "_widgets.png"));
		this.finish = finish;
		this.imageWidth = W;
		this.imageHeight = H;
	}

	private RadioBlockEntity radio() {
		return menu.blockEntity;
	}

	@Nullable
	private HFRadioBlockEntity hf() {
		return radio() instanceof HFRadioBlockEntity h ? h : null;
	}

	private boolean coarse() {
		return COARSE_ON.getOrDefault(radio().getBlockPos().asLong(), false);
	}

	// -------------------- CONTROLS --------------------

	private void tune(int direction) {
		if(!menu.isPowered())
			return;
		int steps = direction * (coarse() || hasShiftDown() ? 10 : 1);
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.STEP_FREQUENCY, steps);
		radio().updateFrequency(steps);
	}

	private void knob(int index, int direction) {
		switch(index) {
			case 0 -> {
				if(direction > 0) onGainUp(null); else onGainDown(null);
			}
			case 1 -> {
				if(direction > 0) onMicGainUp(null); else onMicGainDown(null);
			}
			case 2 -> {
				if(direction > 0) onSquelchUp(null); else onSquelchDown(null);
			}
			default -> { // ATTEN: clockwise attenuates, the RF gain down.
				HFRadioBlockEntity hf = hf();
				if(hf == null)
					return;
				hf.setRfGain(hf.getRfGain() - direction);
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_RF_GAIN, hf.getRfGain());
			}
		}
	}

	/** Turns the OFF / RECEIVE / TRANSMIT switch to {@code position}. */
	private void switchTo(int position) {
		boolean powered = menu.isPowered();
		if(position == 0) {
			releaseTransmit();
			if(powered)
				onPressPower(null);
			return;
		}
		if(!powered)
			onPressPower(null);
		if(position == 1)
			releaseTransmit();
		else if(!transmit) {
			transmit = true;
			if(!cwOn()) { // SSB: TRANSMIT holds the PTT down. CW: the KEY keys it.
				transmitPtt = true;
				onPressPTT(null);
			}
		}
	}

	private void releaseTransmit() {
		if(transmitPtt && !keyHeld)
			onReleasePTT(null);
		transmit = false;
		transmitPtt = false;
	}

	private int switchPosition() {
		return !menu.isPowered() ? 0 : transmit ? 2 : 1;
	}

	private void setAgc(int value) {
		value = Math.floorMod(value, AGC_NAMES.length);
		if(value == radio().getAgc())
			return;
		radio().setAgcClient(value);
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_AGC, value);
	}

	private void toggleMode() {
		if(cwOn())
			onPressSSB(null);
		else {
			if(transmitPtt) { // Into CW: the switch no longer holds the PTT; the KEY keys.
				onReleasePTT(null);
				transmitPtt = false;
			}
			onPressCW(null);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if(!menu.isPowered() && (transmit || keyHeld)) { // Switched off from elsewhere.
			releaseTransmit();
			if(keyHeld) {
				keyHeld = false;
				onReleasePTT(null);
			}
		}
		if(quickSince > 0L && !quickFired && System.currentTimeMillis() - quickSince >= HOLD_MS) {
			SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.QUICK_STORE, true);
			quickFired = true;
		}
	}

	// -------------------- MOUSE --------------------

	private boolean over(int[] c, double x, double y) {
		return Math.hypot(x - (leftPos + c[0]), y - (topPos + c[1])) <= c[2] + 1.5D;
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if(button == 1 && over(KEY, x, y)) {
			HandMicPtt.take(radio().getBlockPos());
			return true;
		}
		for(int i = 0; i < KNOBS.length; i++)
			if(over(KNOBS[i], x, y)) {
				knob(i, button == 1 ? -1 : 1);
				return true;
			}
		if(button != 0)
			return super.mouseClicked(x, y, button);
		if(over(SKIRT, x, y)) {
			tune(x < leftPos + SKIRT[0] ? -1 : 1);
			tuning = true;
			tuneY = y;
			tuneDrag = 0.0D;
			return true;
		}
		if(over(new int[] {SWITCH[0], SWITCH[1], SWITCH[2] + 16}, x, y)) {
			double angle = Math.toDegrees(Math.atan2(x - (leftPos + SWITCH[0]), -(y - (topPos + SWITCH[1]))));
			if(Math.hypot(x - (leftPos + SWITCH[0]), y - (topPos + SWITCH[1])) <= SWITCH[2] + 1.5D) // On the knob: a step round.
				switchTo(Math.max(0, Math.min(2, switchPosition() + (x < leftPos + SWITCH[0] ? -1 : 1))));
			else // On a legend: straight there.
				switchTo(angle < -27.0D ? 0 : angle > 27.0D ? 2 : 1);
			return true;
		}
		if(over(KEY, x, y)) {
			if(menu.isPowered() && !keyHeld) {
				keyHeld = true;
				if(!transmitPtt)
					onPressPTT(null);
			}
			return true;
		}
		if(over(MODE, x, y)) {
			toggleMode();
			return true;
		}
		if(over(AGC, x, y)) {
			setAgc(radio().getAgc() + 1);
			return true;
		}
		if(over(COARSE, x, y)) {
			COARSE_ON.put(radio().getBlockPos().asLong(), !coarse());
			return true;
		}
		if(over(QUICK, x, y)) {
			quickSince = System.currentTimeMillis();
			quickFired = false;
			return true;
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
		if(tuning) {
			tuneDrag += tuneY - y;
			tuneY = y;
			while(Math.abs(tuneDrag) >= DRAG_PER_STEP) {
				int direction = tuneDrag > 0 ? 1 : -1;
				tune(direction);
				tuneDrag -= direction * DRAG_PER_STEP;
			}
			return true;
		}
		return super.mouseDragged(x, y, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		tuning = false;
		if(keyHeld) {
			keyHeld = false;
			if(!transmitPtt)
				onReleasePTT(null);
		}
		if(quickSince > 0L) {
			if(!quickFired)
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.QUICK_TOGGLE, true);
			quickSince = 0L;
		}
		return super.mouseReleased(x, y, button);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if(scrollY == 0.0D)
			return super.mouseScrolled(x, y, scrollX, scrollY);
		int direction = scrollY > 0 ? 1 : -1;
		for(int i = 0; i < KNOBS.length; i++)
			if(over(KNOBS[i], x, y)) {
				knob(i, direction);
				return true;
			}
		if(over(new int[] {SWITCH[0], SWITCH[1], SWITCH[2] + 16}, x, y))
			switchTo(Math.max(0, Math.min(2, switchPosition() + direction)));
		else if(over(AGC, x, y))
			setAgc(Math.max(0, Math.min(2, radio().getAgc() + direction)));
		else if(x >= leftPos && x < leftPos + W && y >= topPos && y < topPos + H)
			tune(direction); // Anywhere else on the panel tunes, as the big knob is the one you reach for.
		else
			return super.mouseScrolled(x, y, scrollX, scrollY);
		return true;
	}

	@Override
	public void onClose() {
		if(keyHeld || transmitPtt)
			onReleasePTT(null);
		keyHeld = false;
		transmit = transmitPtt = false;
		super.onClose();
	}

	// -------------------- DRAWING --------------------

	private static void disc(GuiGraphics g, float cx, float cy, float r, int colour) {
		for(int dy = (int)Math.floor(-r); dy < Math.ceil(r); dy++) {
			float y = dy + 0.5F;
			if(Math.abs(y) > r)
				continue;
			float w = (float)Math.sqrt(r * r - y * y);
			g.fill(Math.round(cx - w), Math.round(cy + dy), Math.round(cx + w), Math.round(cy + dy) + 1, colour);
		}
	}

	private static int brighter(int argb, int by) {
		int r = Math.max(0, Math.min(255, ((argb >> 16) & 0xFF) + by)), gr = Math.max(0, Math.min(255, ((argb >> 8) & 0xFF) + by)),
				b = Math.max(0, Math.min(255, (argb & 0xFF) + by));
		return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
	}

	/** A line of round dots, {@code thick} across. */
	private static void thickLine(GuiGraphics g, float x0, float y0, float x1, float y1, float thick, int colour) {
		float len = (float)Math.hypot(x1 - x0, y1 - y0);
		for(float d = 0; d <= len; d += 0.5F) {
			float t = len == 0 ? 0 : d / len;
			disc(g, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, thick / 2.0F, colour);
		}
	}

	/** Small print centred on {@code x}, top at {@code y} (screen pixels from the corner). */
	private void legend(GuiGraphics g, String text, float x, float y, int colour) {
		g.pose().pushPose();
		g.pose().translate(leftPos + x - font.width(text) / 4.0F, topPos + y, 0.0F);
		g.pose().scale(0.5F, 0.5F, 1.0F);
		g.drawString(font, text, 0, 0, colour, false);
		g.pose().popPose();
	}

	/** A bakelite knob: skirt, knurling, face and a white line turned by {@code turn} (0 to 1, 7 to 5 o'clock). */
	private void bakelite(GuiGraphics g, int[] c, float turn) {
		float cx = leftPos + c[0], cy = topPos + c[1], r = c[2];
		disc(g, cx + 1, cy + 2, r, 0x60000000);
		disc(g, cx, cy, r, BAKELITE);
		for(int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6.0;
			disc(g, cx + (float)Math.cos(a) * (r - 1), cy + (float)Math.sin(a) * (r - 1), 1.0F, BAKELITE_LIGHT);
		}
		disc(g, cx, cy, r * 0.72F, BAKELITE_LIGHT);
		disc(g, cx - 1, cy - 1, r * 0.55F, brighter(BAKELITE_LIGHT, 14));
		double a = Math.toRadians(-135.0 + 270.0 * Math.max(0.0F, Math.min(1.0F, turn)));
		thickLine(g, cx + (float)Math.sin(a) * r * 0.25F, cy - (float)Math.cos(a) * r * 0.25F, cx + (float)Math.sin(a) * (r - 1.5F),
				cy - (float)Math.cos(a) * (r - 1.5F), 1.6F, 0xFFF0F0E8);
	}

	/** A jack: a silver nut round a black hole. */
	private void jack(GuiGraphics g, int x, int y, int r) {
		disc(g, leftPos + x, topPos + y, r, SILVER);
		disc(g, leftPos + x - 0.5F, topPos + y - 0.5F, r - 1.5F, brighter(SILVER, 20));
		disc(g, leftPos + x, topPos + y, r * 0.45F, 0xFF101010);
	}

	/** A bat-handled toggle: up (true) or down. {@code middle} draws it level (a three way switch's middle). */
	private void toggle(GuiGraphics g, int[] c, int position, int positions) {
		float cx = leftPos + c[0], cy = topPos + c[1];
		disc(g, cx, cy, c[2], SILVER_DARK);
		disc(g, cx, cy, c[2] - 1.5F, SILVER);
		float tipY = positions == 3 ? (position == 0 ? -9 : position == 1 ? 0.01F : 9) : (position == 0 ? -9 : 9);
		float tipX = positions == 3 && position == 1 ? 9 : 0;
		thickLine(g, cx, cy, cx + tipX, cy + tipY, 3.0F, 0xFFD8D8DC);
		disc(g, cx + tipX, cy + tipY, 2.0F, 0xFFF4F4F6);
	}

	private void lamp(GuiGraphics g, int[] c, boolean on) {
		float cx = leftPos + c[0], cy = topPos + c[1];
		disc(g, cx, cy, c[2] + 1.5F, SILVER_DARK);
		if(on) {
			disc(g, cx, cy, c[2] + 5, 0x40FF4020);
			disc(g, cx, cy, c[2], 0xFFFF3A22);
			disc(g, cx - 1, cy - 1, 1.2F, 0xFFFFC0A0);
		}
		else
			disc(g, cx, cy, c[2], 0xFF4A1410);
	}

	private void screw(GuiGraphics g, int x, int y) {
		disc(g, leftPos + x, topPos + y, 3.2F, SILVER_DARK);
		disc(g, leftPos + x - 0.4F, topPos + y - 0.4F, 2.6F, SILVER);
		g.fill(leftPos + x - 2, topPos + y, leftPos + x + 2, topPos + y + 1, 0xFF505055);
	}

	/** The radio's crystal, as its plug-in's label shows it: kHz. */
	private String crystalLabel() {
		Band band = menu.getBand();
		if(band == null)
			return "----";
		String name = band.name();
		return switch(name) {
			case "40m" -> "7030";
			case "20m" -> "14060";
			case "80m" -> "3560";
			case "10m" -> "28060";
			default -> String.valueOf(Math.round(band.minFrequency() / 1000.0F + 30));
		};
	}

	@Override
	protected void renderAdditionalBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		RenderSystem.enableBlend();
		int x0 = leftPos, y0 = topPos;
		// The pine box round the panel, with grain.
		g.fill(x0, y0, x0 + W, y0 + H, WOOD);
		for(int i = 0; i < 14; i++) {
			int y = y0 + 1 + i * 13 % H;
			g.fill(x0, y, x0 + W, y + 1, WOOD_DARK);
		}
		for(int i = 0; i < 22; i++) {
			int x = x0 + 3 + i * 37 % W;
			g.fill(x, y0, x + 1, y0 + FRAME, WOOD_DARK);
			g.fill(x, y0 + H - FRAME, x + 1, y0 + H, WOOD_DARK);
		}
		g.fill(x0, y0, x0 + W, y0 + 1, 0xFFE4CCA4);
		g.fill(x0, y0 + H - 1, x0 + W, y0 + H, 0xFF7A6040);
		// The panel: glossy, lit from above, a darker rim.
		int px0 = x0 + FRAME, py0 = y0 + FRAME, px1 = x0 + W - FRAME, py1 = y0 + H - FRAME;
		g.fill(px0 - 1, py0 - 1, px1 + 1, py1 + 1, finish.edge());
		g.fill(px0, py0, px1, py1, finish.face());
		for(int y = py0; y < py0 + 26; y++) { // The gloss.
			int alpha = (int)(70 * (1.0 - (y - py0) / 26.0));
			g.fill(px0, y, px1, y + 1, (alpha << 24) | (finish.gloss() & 0xFFFFFF));
		}
		g.fill(px0, py1 - 2, px1, py1, finish.edge());
		for(int[] s : new int[][] {{14, 14}, {W - 14, 14}, {14, H - 14}, {W - 14, H - 14}, {W / 2, H - 14}, {196, 14}})
			screw(g, s[0], s[1]);

		boolean on = menu.isPowered();
		// Top row: SUPPLY and PHONES jacks, the ANTENNA socket, the label plate and the CRYSTAL.
		jack(g, 30, 26, 5);
		legend(g, "SUPPLY", 30, 33, PRINT);
		jack(g, 30, 50, 5);
		legend(g, "PHONES", 30, 57, PRINT);
		disc(g, x0 + 96, y0 + 30, 7, SILVER_DARK);
		g.fill(x0 + 92, y0 + 14, x0 + 101, y0 + 30, SILVER);
		g.fill(x0 + 93, y0 + 14, x0 + 96, y0 + 30, 0xFFE8E8EC);
		g.fill(x0 + 91, y0 + 26, x0 + 102, y0 + 31, 0xFFF0F0F0);
		legend(g, "ANTENNA", 96, 39, PRINT);
		g.fill(x0 + 116, y0 + 15, x0 + 186, y0 + 45, 0x38FFFFFF); // The clear plate.
		g.fill(x0 + 116, y0 + 15, x0 + 186, y0 + 16, 0x70FFFFFF);
		g.fill(x0 + 116, y0 + 44, x0 + 186, y0 + 45, 0x50000000);
		String bandName = menu.getBand() == null ? "" : menu.getBand().name();
		g.pose().pushPose();
		g.pose().translate(x0 + 151 - font.width("QRP") * 0.75F, y0 + 19, 0);
		g.pose().scale(1.5F, 1.5F, 1.0F);
		g.drawString(font, "QRP", 0, 0, PRINT, false);
		g.pose().popPose();
		legend(g, bandName + " QRP Transceiver", 151, 34, PRINT);
		g.fill(x0 + 222, y0 + 14, x0 + 256, y0 + 40, 0xFF2A1A16); // The crystal can on its plug.
		g.fill(x0 + 222, y0 + 14, x0 + 256, y0 + 16, 0xFF4A3028);
		g.fill(x0 + 226, y0 + 20, x0 + 252, y0 + 34, 0xFFEDE6DC);
		legend(g, crystalLabel(), 239, 22, 0xFF202020);
		legend(g, "KC", 239, 28, 0xFF202020);
		legend(g, "CRYSTAL", 239, 43, PRINT);

		// TUNING: the silver skirt with 0-10, turning with the knob; the marker on the panel above it.
		Band band = menu.getBand();
		float freq = menu.getFrequency();
		float min = band == null ? 0 : band.minFrequency(), max = band == null ? 1 : band.maxFrequency();
		// Not clamped: tuning carries on past the band edges, so the knob and skirt keep turning with it.
		float pos = (freq - min) / Math.max(1.0F, max - min);
		float cx = x0 + SKIRT[0], cy = y0 + SKIRT[1];
		disc(g, cx + 1, cy + 2, SKIRT[2], 0x60000000);
		disc(g, cx, cy, SKIRT[2], SILVER_DARK);
		disc(g, cx, cy, SKIRT[2] - 1, 0xFFDCDCE0);
		disc(g, cx - 1, cy - 1, SKIRT[2] - 6, 0xFFEEEEF2);
		for(int i = 0; i <= 10; i++) {
			double a = Math.toRadians(i * 30.0 - pos * 300.0);
			float tx = cx + (float)Math.sin(a) * (SKIRT[2] - 4.5F), ty = cy - (float)Math.cos(a) * (SKIRT[2] - 4.5F);
			String n = String.valueOf(i);
			g.pose().pushPose();
			g.pose().translate(tx - font.width(n) / 4.0F, ty - 2.0F, 0);
			g.pose().scale(0.5F, 0.5F, 1.0F);
			g.drawString(font, n, 0, 0, 0xFF202024, false);
			g.pose().popPose();
		}
		disc(g, cx, cy, TUNING[2], 0xFF141416);
		for(int i = 0; i < 24; i++) { // Knurling.
			double a = i * Math.PI / 12.0;
			disc(g, cx + (float)Math.cos(a) * (TUNING[2] - 1.5F), cy + (float)Math.sin(a) * (TUNING[2] - 1.5F), 1.0F, 0xFF34343A);
		}
		disc(g, cx, cy, TUNING[2] - 5, 0xFF1E1E22);
		disc(g, cx - 1.5F, cy - 1.5F, TUNING[2] - 9, 0xFF2C2C32);
		double ka = Math.toRadians(-pos * 300.0);
		thickLine(g, cx + (float)Math.sin(ka) * 6, cy - (float)Math.cos(ka) * 6, cx + (float)Math.sin(ka) * (TUNING[2] - 3),
				cy - (float)Math.cos(ka) * (TUNING[2] - 3), 1.8F, 0xFFE8E8E8);
		for(int i = 0; i < 4; i++) // The marker: a small triangle over the skirt.
			g.fill((int)cx - 3 + i, y0 + SKIRT[1] - SKIRT[2] - 6 + i, (int)cx + 4 - i, y0 + SKIRT[1] - SKIRT[2] - 5 + i, PRINT);
		legend(g, "TUNING", SKIRT[0], SKIRT[1] + SKIRT[2] + 3, PRINT);

		// The band edge lamps: lit near an edge, blinking past it.
		float step = RadiocraftServerConfig.HF_FREQUENCY_STEP.get();
		float margin = Math.max(step * 5.0F, (max - min) * 0.03F);
		boolean blink = System.currentTimeMillis() / 300L % 2L == 0L;
		lamp(g, LOW_LAMP, on && (freq < min ? blink : freq <= min + margin));
		lamp(g, HIGH_LAMP, on && (freq > max ? blink : freq >= max - margin));
		legend(g, "LOW EDGE", LOW_LAMP[0] + 16, LOW_LAMP[1] - 2, PRINT);
		legend(g, "HIGH EDGE", HIGH_LAMP[0] + 18, HIGH_LAMP[1] - 2, PRINT);

		// OFF / RECEIVE / TRANSMIT: a brown pointer knob, the legends round it.
		float sx = x0 + SWITCH[0], sy = y0 + SWITCH[1];
		for(int i = 0; i < 3; i++) {
			double a = Math.toRadians(SWITCH_ANGLES[i]);
			float lx = SWITCH[0] + (float)Math.sin(a) * 22, ly = SWITCH[1] - (float)Math.cos(a) * 20 - 3;
			legend(g, SWITCH_NAMES[i], lx, ly, i == switchPosition() ? 0xFFFFFFFF : PRINT);
			thickLine(g, sx + (float)Math.sin(a) * 13, sy - (float)Math.cos(a) * 13, sx + (float)Math.sin(a) * 15, sy - (float)Math.cos(a) * 15, 1.0F, PRINT);
		}
		double sa = Math.toRadians(SWITCH_ANGLES[switchPosition()]);
		disc(g, sx + 1, sy + 2, SWITCH[2], 0x60000000);
		thickLine(g, sx, sy, sx + (float)Math.sin(sa) * (SWITCH[2] + 4), sy - (float)Math.cos(sa) * (SWITCH[2] + 4), 7.0F, BAKELITE);
		disc(g, sx, sy, SWITCH[2], BAKELITE);
		disc(g, sx - 1, sy - 1, SWITCH[2] - 4, BAKELITE_LIGHT);
		thickLine(g, sx + (float)Math.sin(sa) * 4, sy - (float)Math.cos(sa) * 4, sx + (float)Math.sin(sa) * (SWITCH[2] + 3),
				sy - (float)Math.cos(sa) * (SWITCH[2] + 3), 1.2F, 0xFFE8E0D8);

		// The toggles and QUICK.
		toggle(g, MODE, cwOn() ? 2 : 0, 2);
		legend(g, "SSB", MODE[0], MODE[1] - 18, PRINT);
		legend(g, "CW", MODE[0], MODE[1] + 13, PRINT);
		toggle(g, AGC, radio().getAgc(), 3);
		legend(g, "AGC", AGC[0], AGC[1] - 24, PRINT);
		legend(g, "OFF", AGC[0], AGC[1] - 18, PRINT);
		legend(g, "SLOW", AGC[0] + 17, AGC[1] - 2, PRINT);
		legend(g, "FAST", AGC[0], AGC[1] + 13, PRINT);
		toggle(g, COARSE, coarse() ? 0 : 2, 2);
		legend(g, "x10", COARSE[0], COARSE[1] - 18, PRINT);
		legend(g, "x1", COARSE[0], COARSE[1] + 13, PRINT);
		boolean quickDown = quickSince > 0L;
		disc(g, x0 + QUICK[0], y0 + QUICK[1], QUICK[2] + 1.5F, SILVER_DARK);
		disc(g, x0 + QUICK[0], y0 + QUICK[1] + (quickDown ? 1 : 0), QUICK[2], quickDown ? 0xFF701010 : 0xFF1A1A1C);
		legend(g, "QUICK", QUICK[0], QUICK[1] + 10, PRINT);

		// The bottom row: VOLUME, MIC, SQUELCH, ATTEN, then the KEY jack and the KEY.
		HFRadioBlockEntity hf = hf();
		float[] turns = {radio().getSpeakerGain() / 2.0F, radio().getMicGain() / 2.0F, radio().getSquelchLevel(),
				hf == null ? 0.0F : 1.0F - hf.getRfGain() / (float)HFRadioBlockEntity.MAX_RF_GAIN};
		for(int i = 0; i < KNOBS.length; i++) {
			bakelite(g, KNOBS[i], turns[i]);
			legend(g, KNOB_NAMES[i], KNOBS[i][0], KNOBS[i][1] + KNOBS[i][2] + 3, PRINT);
		}
		jack(g, 204, 152, 4);
		legend(g, "KEY", 204, 159, PRINT);
		float kx = x0 + KEY[0], ky = y0 + KEY[1] + (keyHeld ? 1 : 0);
		disc(g, kx + 1, y0 + KEY[1] + 3, KEY[2], 0x60000000);
		disc(g, kx, ky, KEY[2], BAKELITE);
		disc(g, kx - 2, ky - 2, KEY[2] - 4, BAKELITE_LIGHT);
		disc(g, kx - 4, ky - 4, 2.5F, 0x50FFFFFF);
		if(keyHeld || transmitPtt)
			disc(g, kx, ky, KEY[2] + 2, 0x30FF4020);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
	}

	/** What the part under the mouse does, and its setting. */
	@Nullable
	private Component hint(int x, int y) {
		if(over(KEY, x, y))
			return Component.literal(cwOn() ? "KEY: hold to send. Right click: hand mic" : "KEY: hold to talk. Right click: hand mic");
		for(int i = 0; i < KNOBS.length; i++)
			if(over(KNOBS[i], x, y))
				return Component.literal(switch(i) {
					case 0 -> String.format(Locale.ROOT, "VOLUME %d%%", Math.round(radio().getSpeakerGain() * 100.0F));
					case 1 -> String.format(Locale.ROOT, "MIC %d%%", Math.round(radio().getMicGain() * 100.0F));
					case 2 -> "SQUELCH " + radio().squelchLabel();
					default -> "ATTEN " + (hf() == null ? 0 : HFRadioBlockEntity.MAX_RF_GAIN - hf().getRfGain()) + " of " + HFRadioBlockEntity.MAX_RF_GAIN;
				} + ": scroll, or left click up, right click down");
		if(over(SKIRT, x, y))
			return Component.literal("TUNING: scroll or drag; Shift or x10 for ten steps");
		if(over(new int[] {SWITCH[0], SWITCH[1], SWITCH[2] + 16}, x, y))
			return Component.literal(cwOn() ? "OFF / RECEIVE / TRANSMIT (on CW the KEY keys it)" : "OFF / RECEIVE / TRANSMIT (holds it keyed)");
		if(over(LOW_LAMP, x, y) || over(HIGH_LAMP, x, y))
			return Component.literal("Band edge: lit near it, blinking past it");
		if(over(MODE, x, y))
			return Component.literal("Mode: " + (cwOn() ? "CW" : "SSB"));
		if(over(AGC, x, y))
			return Component.literal("AGC " + AGC_NAMES[Math.max(0, Math.min(2, radio().getAgc()))]);
		if(over(COARSE, x, y))
			return Component.literal(coarse() ? "Tuning x10" : "Tuning x1");
		if(over(QUICK, x, y))
			return Component.literal("QUICK: press for the stored frequency and back; hold to store");
		return null;
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		if(RadiocraftClientValues.SCREEN_PTT_PRESSED && !(keyHeld || transmitPtt) && !menu.isPowered())
			RadiocraftClientValues.SCREEN_PTT_PRESSED = false;
		Component hint = hint(mouseX, mouseY);
		if(hint != null)
			g.renderTooltip(font, hint, mouseX, mouseY);
	}
}
