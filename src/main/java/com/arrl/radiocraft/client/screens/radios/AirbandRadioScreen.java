package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.blockentities.radio.AirbandRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.menus.SimpleRadioMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The airband radio's screen (2026-11-13): its faceplate laid out as the model has it (four screen pixels to a pixel of
 * {@code airband_radio_front.png}), drawn clean rather than the block's texture blown up, every part on it working. The amber windows show USE and STBY; the flip-flop key between them swaps
 * them. The big concentric knob on the right tunes STBY: the outer ring whole MHz, the inner knob 25 kHz (click its
 * left half to go down, its right half up, or scroll over it). The top left knob is the volume (scroll; click for
 * power), the one under it the squelch (scroll). The button row: PWR, MON (squelch open), PTT (hold), VOL-, VOL+.
 */
public class AirbandRadioScreen extends AbstractContainerScreen<SimpleRadioMenu> {

	/** Texture pixels to screen pixels. */
	private static final int K = 4;
	private static final int AMBER = 0xFFFFA020, AMBER_DIM = 0xFF46220A, PRINT = 0xFFC8C8C0;
	private static final int PLATE = 0xFF222225, BEVEL = 0xFF3A3A3E, SHADE = 0xFF161618, WINDOW = 0xFF1A0E08, BEZEL = 0xFF101012;
	/** Where things are on the faceplate, in its texture's pixels: x0, y0, x1, y1. */
	private static final int[] USE = {13, 3, 29, 10}, STBY = {33, 3, 49, 10}, FLIP = {30, 4, 32, 9};
	private static final int[] VOLUME = {3, 3, 9, 9}, SQUELCH = {3, 11, 9, 17}, OUTER = {48, 7, 58, 17}, INNER = {50, 9, 56, 15};
	private static final int[][] BUTTONS = {{13, 14, 17, 17}, {19, 14, 23, 17}, {25, 14, 29, 17}, {37, 14, 41, 17}, {43, 14, 47, 17}};
	private static final String[] BUTTON_LABELS = {"PWR", "MON", "PTT", "VOL-", "VOL+"};

	private final RadioBlockEntity radio;
	private boolean ptt = false;
	/** The squelch MON opened from, to go back to. */
	private float squelchBeforeMon = -1;

	public AirbandRadioScreen(SimpleRadioMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.radio = menu.blockEntity;
		imageWidth = 60 * K;
		imageHeight = 20 * K + 14;
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = true;
	}

	private AirbandRadioBlockEntity airband() {
		return (AirbandRadioBlockEntity)radio;
	}

	private void send(SBlockRadioControlPacket.Action action, int step, boolean value) {
		SBlockRadioControlPacket.updateServer(radio.getBlockPos(), action, step, value);
	}

	private void step(int steps) {
		if(!menu.isPowered())
			return;
		send(SBlockRadioControlPacket.Action.STEP_FREQUENCY, steps, false);
		radio.updateFrequency(steps);
	}

	private void gain(float delta) {
		float gain = Math.max(0.0F, Math.min(2.0F, radio.getSpeakerGain() + delta));
		radio.setSpeakerGain(gain);
		send(SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, Math.round(gain * 100.0F), false);
	}

	private void squelch(float level) {
		float clamped = Math.max(0.0F, Math.min(1.0F, level));
		radio.setSquelchClient(clamped);
		send(SBlockRadioControlPacket.Action.SET_SQUELCH, Math.round(clamped * 1000.0F), false);
	}

	/** True if the screen point is over {@code r} (texture pixels) of the faceplate. */
	private boolean over(int[] r, double x, double y) {
		double u = (x - leftPos) / K, v = (y - topPos) / K;
		return u >= r[0] && u < r[2] && v >= r[1] && v < r[3];
	}

	/** -1 over the left half of {@code r}, 1 over the right half. */
	private int side(int[] r, double x) {
		return (x - leftPos) / K < (r[0] + r[2]) / 2.0 ? -1 : 1;
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if(button == 0) {
			if(over(FLIP, x, y)) {
				if(menu.isPowered())
					send(SBlockRadioControlPacket.Action.AIRBAND_FLIP, 0, true);
				return true;
			}
			if(over(INNER, x, y)) {
				step(side(INNER, x));
				return true;
			}
			if(over(OUTER, x, y)) {
				step(side(OUTER, x) * AirbandRadioBlockEntity.COARSE_STEPS);
				return true;
			}
			if(over(VOLUME, x, y) || over(BUTTONS[0], x, y)) {
				send(SBlockRadioControlPacket.Action.TOGGLE_POWER, 0, true);
				return true;
			}
			if(over(BUTTONS[1], x, y)) { // MON: the squelch wide open, and back.
				if(squelchBeforeMon < 0) {
					squelchBeforeMon = radio.getSquelchLevel();
					squelch(0.0F);
				}
				else {
					squelch(squelchBeforeMon);
					squelchBeforeMon = -1;
				}
				return true;
			}
			if(over(BUTTONS[2], x, y)) {
				if(menu.isPowered()) {
					ptt = true;
					send(SBlockRadioControlPacket.Action.SET_PTT, 0, true);
					RadiocraftClientValues.SCREEN_PTT_PRESSED = true;
				}
				return true;
			}
			if(over(BUTTONS[3], x, y)) {
				gain(-0.1F);
				return true;
			}
			if(over(BUTTONS[4], x, y)) {
				gain(0.1F);
				return true;
			}
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		if(ptt) {
			ptt = false;
			send(SBlockRadioControlPacket.Action.SET_PTT, 0, false);
			RadiocraftClientValues.SCREEN_PTT_PRESSED = false;
		}
		return super.mouseReleased(x, y, button);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if(scrollY == 0.0D)
			return super.mouseScrolled(x, y, scrollX, scrollY);
		int dir = scrollY > 0 ? 1 : -1;
		if(over(INNER, x, y))
			step(dir);
		else if(over(OUTER, x, y))
			step(dir * AirbandRadioBlockEntity.COARSE_STEPS);
		else if(over(VOLUME, x, y))
			gain(dir * 0.1F);
		else if(over(SQUELCH, x, y))
			squelch(radio.getSquelchLevel() + dir * 0.05F);
		else if(isHovering(0, 0, imageWidth, imageHeight, x, y))
			step(hasShiftDown() ? dir * AirbandRadioBlockEntity.COARSE_STEPS : dir);
		else
			return super.mouseScrolled(x, y, scrollX, scrollY);
		return true;
	}

	@Override
	public void onClose() {
		if(RadiocraftClientValues.SCREEN_PTT_PRESSED)
			send(SBlockRadioControlPacket.Action.SET_PTT, 0, false);
		RadiocraftClientValues.SCREEN_PTT_PRESSED = false;
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = false;
		super.onClose();
	}

	// -------------------- DRAWING --------------------

	private int sx(double u) {
		return leftPos + (int)Math.round(u * K);
	}

	private int sy(double v) {
		return topPos + (int)Math.round(v * K);
	}

	private void box(GuiGraphics g, int[] r, int colour) {
		g.fill(sx(r[0]), sy(r[1]), sx(r[2]), sy(r[3]), colour);
	}

	/** A filled disc, row by row. */
	private static void disc(GuiGraphics g, float cx, float cy, float r, int colour) {
		for(int dy = (int)Math.floor(-r); dy < Math.ceil(r); dy++) {
			float y = dy + 0.5F;
			if(Math.abs(y) > r)
				continue;
			float w = (float)Math.sqrt(r * r - y * y);
			g.fill(Math.round(cx - w), Math.round(cy + dy), Math.round(cx + w), Math.round(cy + dy) + 1, colour);
		}
	}

	/**
	 * A round knob in the box {@code r}: a dark skirt, knurled edge, a face, and a white pointer turned by {@code turn}
	 * (0 to 1, from 7 o'clock round to 5 o'clock).
	 */
	private void knob(GuiGraphics g, int[] r, int face, float turn) {
		float cx = (sx(r[0]) + sx(r[2])) / 2.0F, cy = (sy(r[1]) + sy(r[3])) / 2.0F, radius = (sx(r[2]) - sx(r[0])) / 2.0F;
		disc(g, cx, cy + 1, radius, SHADE);
		disc(g, cx, cy, radius, 0xFF0E0E10);
		for(int i = 0; i < 16; i++) { // Knurling.
			double a = i * Math.PI / 8.0;
			int x = Math.round(cx + (float)Math.cos(a) * (radius - 1.5F)), y = Math.round(cy + (float)Math.sin(a) * (radius - 1.5F));
			g.fill(x, y, x + 1, y + 1, 0xFF3C3C42);
		}
		disc(g, cx, cy, radius - 3, face);
		disc(g, cx, cy - 1, radius - 5, brighter(face));
		disc(g, cx, cy, radius - 5, face);
		double a = Math.toRadians(-135.0 + 270.0 * Math.max(0.0F, Math.min(1.0F, turn)));
		for(float d = radius * 0.25F; d < radius - 3.5F; d += 0.5F) {
			int x = Math.round(cx + (float)Math.sin(a) * d), y = Math.round(cy - (float)Math.cos(a) * d);
			g.fill(x, y, x + 2, y + 2, 0xFFE8E8E0);
		}
	}

	private static int brighter(int argb) {
		int r = Math.min(255, ((argb >> 16) & 0xFF) + 24), gr = Math.min(255, ((argb >> 8) & 0xFF) + 24), b = Math.min(255, (argb & 0xFF) + 24);
		return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		RenderSystem.enableBlend();
		int w = 60 * K, h = 20 * K;
		// The plate, bevelled light top-left and dark bottom-right.
		g.fill(leftPos, topPos, leftPos + w, topPos + h, PLATE);
		g.fill(leftPos, topPos, leftPos + w, topPos + K, BEVEL);
		g.fill(leftPos, topPos, leftPos + K, topPos + h, BEVEL);
		g.fill(leftPos, topPos + h - K, leftPos + w, topPos + h, SHADE);
		g.fill(leftPos + w - K, topPos, leftPos + w, topPos + h, SHADE);
		boolean on = menu.isPowered();
		// The windows: a black bezel, the dark amber glass, unlit 8s behind the reading.
		for(int[] r : new int[][] {USE, STBY}) {
			g.fill(sx(r[0] - 1), sy(r[1] - 1), sx(r[2] + 1), sy(r[3] + 1), BEZEL);
			box(g, r, WINDOW);
			window(g, r, "888.888", AMBER_DIM);
		}
		if(on) {
			window(g, USE, AirbandRadioBlockEntity.format(radio.getFrequency()), AMBER);
			window(g, STBY, AirbandRadioBlockEntity.format(airband().getStandbyFrequency()), AMBER);
		}
		// The flip-flop key: light grey, an arrow each way.
		box(g, FLIP, over(FLIP, mouseX, mouseY) ? 0xFFB4B4AE : 0xFF96968F);
		g.fill(sx(FLIP[0]), sy(FLIP[3]) - 2, sx(FLIP[2]), sy(FLIP[3]), 0xFF5A5A56);
		int fx = sx(FLIP[0]) + 1, fw = sx(FLIP[2]) - sx(FLIP[0]) - 2, fy = sy(FLIP[1]);
		g.fill(fx, fy + 6, fx + fw, fy + 7, 0xFF28282A);
		g.fill(fx, fy + 5, fx + 2, fy + 8, 0xFF28282A);
		g.fill(fx, fy + 13, fx + fw, fy + 14, 0xFF28282A);
		g.fill(fx + fw - 2, fy + 12, fx + fw, fy + 15, 0xFF28282A);
		// The buttons, lit while MON or PTT is on.
		for(int i = 0; i < BUTTONS.length; i++) {
			int[] b = BUTTONS[i];
			boolean lit = (i == 1 && squelchBeforeMon >= 0) || (i == 2 && ptt && on);
			int face = lit ? (i == 1 ? 0xFF3C8A4A : 0xFFA03020) : over(b, mouseX, mouseY) ? 0xFF56565C : 0xFF46464A;
			g.fill(sx(b[0]), sy(b[1]) + 1, sx(b[2]), sy(b[3]) + 1, SHADE);
			box(g, b, face);
			g.fill(sx(b[0]), sy(b[1]), sx(b[2]), sy(b[1]) + 2, brighter(face));
		}
		// The knobs: volume and squelch show their setting; the tuner shows where STBY sits in the band and the MHz.
		float standby = airband().getStandbyFrequency();
		knob(g, VOLUME, 0xFF2A2A2E, radio.getSpeakerGain() / 2.0F);
		knob(g, SQUELCH, 0xFF2A2A2E, radio.getSquelchLevel());
		knob(g, OUTER, 0xFF26262A, (standby - AirbandRadioBlockEntity.LOW_HZ) / (AirbandRadioBlockEntity.HIGH_HZ - AirbandRadioBlockEntity.LOW_HZ));
		knob(g, INNER, 0xFF38383E, (standby % 1_000_000.0F) / 1_000_000.0F);
	}

	/** Digits in a window, centred; every digit is the same width, so a reading sits square on its unlit 8s. */
	private void window(GuiGraphics g, int[] r, String text, int colour) {
		float w = (r[2] - r[0]) * K, h = (r[3] - r[1]) * K;
		float scale = Math.min(w * 0.9F / font.width("888.888"), h * 0.8F / font.lineHeight);
		g.pose().pushPose();
		g.pose().translate(leftPos + r[0] * K + (w - font.width(text) * scale) / 2.0F, topPos + r[1] * K + (h - (font.lineHeight - 1) * scale) / 2.0F, 0.0F);
		g.pose().scale(scale, scale, 1.0F);
		g.drawString(font, text, 0, 0, colour, false);
		g.pose().popPose();
	}

	/** Small print centred on faceplate point (u, v), in labels space (relative to the screen's corner). */
	private void legend(GuiGraphics g, String text, double u, double v) {
		g.pose().pushPose();
		g.pose().translate((float)(u * K) - font.width(text) / 4.0F, (float)(v * K), 0.0F);
		g.pose().scale(0.5F, 0.5F, 1.0F);
		g.drawString(font, text, 0, 0, PRINT, false);
		g.pose().popPose();
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
		legend(g, "PWR  VOL", 6, 1.4);
		legend(g, "SQ", 6, 17.6);
		legend(g, "USE", (USE[0] + USE[2]) / 2.0, 11.4);
		legend(g, "STBY", (STBY[0] + STBY[2]) / 2.0, 11.4);
		legend(g, "TUNE", (OUTER[0] + OUTER[2]) / 2.0, 4.8);
		for(int i = 0; i < BUTTONS.length; i++)
			legend(g, BUTTON_LABELS[i], (BUTTONS[i][0] + BUTTONS[i][2]) / 2.0, 17.6);
		String status = String.format(Locale.ROOT, "VOL %d%%   SQ %s%s", Math.round(radio.getSpeakerGain() * 100.0F), radio.squelchLabel(),
				!menu.isPowered() ? "   (off)" : "");
		g.drawString(font, status, 2, 20 * K + 3, PRINT, false);
	}

	/** What the part under the mouse does. */
	private Component hint(int x, int y) {
		if(over(FLIP, x, y))
			return Component.literal("Swap USE and STBY");
		if(over(INNER, x, y))
			return Component.literal("STBY kHz: click a side or scroll, 25 kHz a step");
		if(over(OUTER, x, y))
			return Component.literal("STBY MHz: click a side or scroll");
		if(over(VOLUME, x, y))
			return Component.literal("Volume: scroll. Click: power");
		if(over(SQUELCH, x, y))
			return Component.literal("Squelch: scroll");
		if(over(BUTTONS[1], x, y))
			return Component.literal("Monitor: opens the squelch");
		if(over(BUTTONS[2], x, y))
			return Component.literal("Push to talk: hold");
		return null;
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Component hint = hint(mouseX, mouseY);
		if(hint != null)
			g.renderTooltip(font, hint, mouseX, mouseY);
		else
			renderTooltip(g, mouseX, mouseY);
	}
}
