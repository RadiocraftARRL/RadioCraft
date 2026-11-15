package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastReceiverBlockEntity;
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
 * The FM broadcast receiver's screen (2026-11-13): the table radio's front as its model has it ({@code
 * tools/airband_fm_models.py}, RX_ layout: five screen pixels to a pixel of its front texture), drawn clean, every part
 * working. The walnut case and woven grille on the left; on the right the chrome panel with the tall dial (its pointer
 * on the station; click the dial to tune there), the big TUNING knob (scroll, or click a side), VOLUME, BASS and TREBLE
 * (scroll or click a side), and two switches: POWER and MUTE. Scrolling anywhere else on it tunes too.
 */
public class FmBroadcastReceiverScreen extends AbstractContainerScreen<SimpleRadioMenu> {

	/** Texture pixels to screen pixels. */
	private static final int K = 5;
	private static final int W = 56, H = 32;
	private static final int[] GRILLE = {5, 5, 32, 27}, PANEL = {34, 4, 52, 28}, DIAL = {36, 6, 42, 26};
	private static final int[] TUNING = {43, 6, 50, 13}, VOLUME = {44, 15, 49, 20}, BASS = {43, 21, 46, 24}, TREBLE = {47, 21, 50, 24};
	private static final int[] POWER = {43, 25, 45, 27}, MUTE = {48, 25, 50, 27};
	private static final int WALNUT = 0xFF5C3620, WALNUT_DARK = 0xFF422515, WALNUT_LIGHT = 0xFF704428, CHROME = 0xFFC4C6CA,
			CHROME_DARK = 0xFF808288, BRUSHED = 0xFFB0B0AC, GLASS = 0xFF22160E, ORANGE = 0xFFF08828, SCALE = 0xFFDCD2BE,
			PRINT = 0xFF303030;

	private final FmBroadcastReceiverBlockEntity radio;
	/** The volume MUTE took away, to give back; negative while not muted. */
	private float gainBeforeMute = -1.0F;

	public FmBroadcastReceiverScreen(SimpleRadioMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.radio = (FmBroadcastReceiverBlockEntity)menu.blockEntity;
		imageWidth = W * K;
		imageHeight = H * K + 14;
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = true;
	}

	// -------------------- CONTROLS --------------------

	private void send(SBlockRadioControlPacket.Action action, int step, boolean value) {
		SBlockRadioControlPacket.updateServer(radio.getBlockPos(), action, step, value);
	}

	/** The dial is a string and a pointer: it turns whether the set is on or off (2026-11-13). */
	private void tune(int steps) {
		if(steps == 0)
			return;
		send(SBlockRadioControlPacket.Action.STEP_FREQUENCY, steps, false);
		radio.updateFrequency(steps);
	}

	private void gain(float delta) {
		float gain = Math.max(0.0F, Math.min(2.0F, radio.getSpeakerGain() + delta));
		radio.setSpeakerGain(gain);
		send(SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, Math.round(gain * 100.0F), false);
		gainBeforeMute = -1.0F;
	}

	private void tone(boolean treble, int delta) {
		int now = (treble ? radio.getTreble() : radio.getBass()) + delta;
		int clamped = Math.max(-FmBroadcastReceiverBlockEntity.TONE_STEPS, Math.min(FmBroadcastReceiverBlockEntity.TONE_STEPS, now));
		radio.setToneClient(treble, clamped);
		send(SBlockRadioControlPacket.Action.FM_TONE, clamped, treble);
	}

	private void mute() {
		if(gainBeforeMute < 0.0F) {
			gainBeforeMute = radio.getSpeakerGain();
			radio.setSpeakerGain(0.0F);
			send(SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, 0, false);
		}
		else {
			float back = gainBeforeMute;
			gainBeforeMute = -1.0F;
			radio.setSpeakerGain(back);
			send(SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, Math.round(back * 100.0F), false);
		}
	}

	/** True if the screen point is over {@code r} (texture pixels). */
	private boolean over(int[] r, double x, double y) {
		double u = (x - leftPos) / K, v = (y - topPos) / K;
		return u >= r[0] && u < r[2] && v >= r[1] && v < r[3];
	}

	/** -1 over the left half of {@code r}, 1 over the right. */
	private int side(int[] r, double x) {
		return (x - leftPos) / K < (r[0] + r[2]) / 2.0D ? -1 : 1;
	}

	/** The station at the dial's height {@code y}: the bottom of the window 88.1, the top 107.9. */
	private float dialHz(double y) {
		double v = (y - topPos) / K;
		double t = 1.0D - (v - (DIAL[1] + 1)) / (DIAL[3] - DIAL[1] - 2);
		t = Math.max(0.0D, Math.min(1.0D, t));
		return FmBroadcastRadioBlockEntity.snap(FmBroadcastRadioBlockEntity.LOW_HZ + t * (FmBroadcastRadioBlockEntity.HIGH_HZ - FmBroadcastRadioBlockEntity.LOW_HZ));
	}

	/** Where {@code hz} sits up the dial, 0 at the bottom, 1 at the top. */
	private static float dialAt(float hz) {
		return (hz - FmBroadcastRadioBlockEntity.LOW_HZ) / (FmBroadcastRadioBlockEntity.HIGH_HZ - FmBroadcastRadioBlockEntity.LOW_HZ);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if(button == 0) {
			if(over(POWER, x, y)) {
				send(SBlockRadioControlPacket.Action.TOGGLE_POWER, 0, true);
				return true;
			}
			if(over(MUTE, x, y)) {
				mute();
				return true;
			}
			if(over(DIAL, x, y)) {
				tune(Math.round((dialHz(y) - radio.getFrequency()) / FmBroadcastRadioBlockEntity.STEP_HZ));
				return true;
			}
			if(over(TUNING, x, y)) {
				tune(side(TUNING, x));
				return true;
			}
			if(over(VOLUME, x, y)) {
				gain(side(VOLUME, x) * 0.1F);
				return true;
			}
			if(over(BASS, x, y)) {
				tone(false, side(BASS, x));
				return true;
			}
			if(over(TREBLE, x, y)) {
				tone(true, side(TREBLE, x));
				return true;
			}
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if(scrollY == 0.0D)
			return super.mouseScrolled(x, y, scrollX, scrollY);
		int dir = scrollY > 0 ? 1 : -1;
		if(over(VOLUME, x, y))
			gain(dir * 0.1F);
		else if(over(BASS, x, y))
			tone(false, dir);
		else if(over(TREBLE, x, y))
			tone(true, dir);
		else if(isHovering(0, 0, imageWidth, imageHeight, x, y))
			tune(hasShiftDown() ? dir * 10 : dir); // The tuning knob, the dial, or anywhere on the radio.
		else
			return super.mouseScrolled(x, y, scrollX, scrollY);
		return true;
	}

	@Override
	public void onClose() {
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

	private static void disc(GuiGraphics g, float cx, float cy, float r, int colour) {
		for(int dy = (int)Math.floor(-r); dy < Math.ceil(r); dy++) {
			float yy = dy + 0.5F;
			if(Math.abs(yy) > r)
				continue;
			float w = (float)Math.sqrt(r * r - yy * yy);
			g.fill(Math.round(cx - w), Math.round(cy + dy), Math.round(cx + w), Math.round(cy + dy) + 1, colour);
		}
	}

	private static int shade(int argb, int by) {
		int r = Math.max(0, Math.min(255, ((argb >> 16) & 0xFF) + by)), gr = Math.max(0, Math.min(255, ((argb >> 8) & 0xFF) + by)),
				b = Math.max(0, Math.min(255, (argb & 0xFF) + by));
		return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
	}

	/** A chrome knob filling {@code r}: a dark skirt, a knurled chrome rim, a bright cap and a black line for {@code turn} (0 to 1). */
	private void knob(GuiGraphics g, int[] r, float turn) {
		float cx = (sx(r[0]) + sx(r[2])) / 2.0F, cy = (sy(r[1]) + sy(r[3])) / 2.0F, radius = (sx(r[2]) - sx(r[0])) / 2.0F;
		disc(g, cx + 1, cy + 2, radius, 0x60000000);
		disc(g, cx, cy, radius, CHROME_DARK);
		for(int i = 0; i < 24; i++) { // Knurling.
			double a = i * Math.PI / 12.0;
			int x = Math.round(cx + (float)Math.cos(a) * (radius - 1.5F)), y = Math.round(cy + (float)Math.sin(a) * (radius - 1.5F));
			g.fill(x, y, x + 1, y + 1, 0xFF5A5C60);
		}
		disc(g, cx, cy, radius - 2.5F, CHROME);
		disc(g, cx - radius * 0.2F, cy - radius * 0.25F, radius * 0.35F, 0xFFE8EAEE); // The shine.
		double a = Math.toRadians(-135.0 + 270.0 * Math.max(0.0F, Math.min(1.0F, turn)));
		for(float d = radius * 0.2F; d < radius - 3.0F; d += 0.5F) {
			int x = Math.round(cx + (float)Math.sin(a) * d), y = Math.round(cy - (float)Math.cos(a) * d);
			g.fill(x, y, x + 2, y + 2, 0xFF202022);
		}
	}

	/**
	 * A bat-handle toggle as on the set: a chrome plate with a bevel and two screws, a knurled hex nut, and a tapered
	 * chrome lever thrown up (on) or down, a ball on its end, its shadow on the plate, ON and OFF marks either side.
	 */
	private void toggle(GuiGraphics g, int[] r, boolean on) {
		int x0 = sx(r[0]) - 2, x1 = sx(r[2]) + 2, y0 = sy(r[1]) - 3, y1 = sy(r[3]) + 3;
		// The plate: chrome, light top-left edge, dark bottom-right, a screw above and below.
		g.fill(x0 + 1, y0 + 1, x1 + 1, y1 + 1, 0x50000000);
		g.fill(x0, y0, x1, y1, 0xFFA8AAAE);
		g.fill(x0, y0, x1, y0 + 1, 0xFFE6E8EC);
		g.fill(x0, y0, x0 + 1, y1, 0xFFDADCE0);
		g.fill(x0, y1 - 1, x1, y1, 0xFF6E7076);
		g.fill(x1 - 1, y0, x1, y1, 0xFF7A7C82);
		float cx = (x0 + x1) / 2.0F, cy = (y0 + y1) / 2.0F;
		for(float sy : new float[] {y0 + 2.5F, y1 - 2.5F}) {
			disc(g, cx, sy, 1.4F, 0xFF6A6C70);
			g.fill(Math.round(cx) - 1, Math.round(sy), Math.round(cx) + 1, Math.round(sy) + 1, 0xFF3A3A3E); // The slot.
		}
		// ON / OFF marks: a little bar above, a ring below.
		g.fill(x1 + 1, y0 + 2, x1 + 3, y0 + 3, on ? 0xFF303030 : 0xFF707070);
		g.fill(x1 + 1, y1 - 3, x1 + 3, y1 - 2, on ? 0xFF707070 : 0xFF303030);
		// The nut: dark ring, chrome face, a highlight.
		disc(g, cx, cy, 3.6F, 0xFF4A4C50);
		disc(g, cx, cy, 2.8F, 0xFFC8CACE);
		disc(g, cx - 0.8F, cy - 0.8F, 1.2F, 0xFFF2F4F6);
		// The lever's shadow, then the lever: tapering from the nut, shaded with a bright edge, a ball on its end.
		int dir = on ? -1 : 1;
		float len = 7.5F;
		for(float t = 0.0F; t <= len; t += 0.5F) {
			float y = cy + dir * t;
			float half = 1.6F - t * 0.08F;
			g.fill(Math.round(cx - half) + 1, Math.round(y) + 1, Math.round(cx + half) + 1, Math.round(y) + 2, 0x40000000);
		}
		for(float t = 0.0F; t <= len; t += 0.5F) {
			float y = cy + dir * t;
			float half = 1.6F - t * 0.08F;
			int l = Math.round(cx - half), rr = Math.round(cx + half);
			g.fill(l, Math.round(y), rr, Math.round(y) + 1, 0xFFB4B6BA);
			g.fill(l, Math.round(y), l + 1, Math.round(y) + 1, 0xFFF4F6F8);
			g.fill(rr - 1, Math.round(y), rr, Math.round(y) + 1, 0xFF6E7076);
		}
		float bx = cx, by = cy + dir * len;
		disc(g, bx, by, 2.0F, 0xFF8A8C90);
		disc(g, bx - 0.4F, by - 0.4F, 1.4F, 0xFFD8DADE);
		g.fill(Math.round(bx - 1), Math.round(by - 1), Math.round(bx), Math.round(by), 0xFFFFFFFF);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		RenderSystem.enableBlend();
		int w = W * K, h = H * K;
		// The walnut case, grain running along it.
		g.fill(leftPos, topPos, leftPos + w, topPos + h, WALNUT);
		for(int y = 0; y < h; y += 3) {
			int wave = (int)(Math.sin(y * 0.37) * 18 + Math.sin(y * 0.11) * 30);
			g.fill(leftPos, topPos + y, leftPos + w, topPos + y + 1, (y / 3) % 4 == 0 ? WALNUT_DARK : (y / 3) % 7 == 3 ? WALNUT_LIGHT : WALNUT);
			g.fill(leftPos + 40 + wave, topPos + y, leftPos + 90 + wave, topPos + y + 1, WALNUT_DARK);
		}
		g.fill(sx(3), sy(3), sx(W - 3), sy(H - 3), 0xFF18120E); // The shadow inside the lip.
		// The grille: chrome frame, woven cloth.
		box(g, GRILLE, CHROME);
		g.fill(sx(GRILLE[0]), sy(GRILLE[3]) - 2, sx(GRILLE[2]), sy(GRILLE[3]), CHROME_DARK);
		int gx0 = sx(GRILLE[0] + 1), gy0 = sy(GRILLE[1] + 1), gx1 = sx(GRILLE[2] - 1), gy1 = sy(GRILLE[3] - 1);
		g.fill(gx0, gy0, gx1, gy1, 0xFFB48450); // The cloth, woven: darker weft one way, lighter warp the other.
		for(int y = gy0; y < gy1; y += 2)
			g.fill(gx0, y, gx1, y + 1, (y / 2) % 6 == 0 ? 0xFF7C5228 : 0xFF9A6C3C);
		for(int x = gx0 + 1; x < gx1; x += 2)
			g.fill(x, gy0, x + 1, gy1, (x / 2) % 7 == 0 ? 0x50FFE0B0 : 0x30FFE0B0);
		g.fill(sx(GRILLE[0] + 2), sy(GRILLE[1] + 2), sx(GRILLE[0] + 7), sy(GRILLE[1] + 4), 0xFFD6D6D2); // A blank nameplate.
		// The panel: chrome frame, brushed aluminium with fine lines.
		box(g, PANEL, CHROME);
		g.fill(sx(PANEL[0]), sy(PANEL[1]), sx(PANEL[2]), sy(PANEL[1]) + 2, 0xFFE4E6EA);
		g.fill(sx(PANEL[0]), sy(PANEL[3]) - 2, sx(PANEL[2]), sy(PANEL[3]), CHROME_DARK);
		g.fill(sx(PANEL[0] + 1), sy(PANEL[1] + 1), sx(PANEL[2] - 1), sy(PANEL[3] - 1), BRUSHED);
		for(int y = sy(PANEL[1] + 1); y < sy(PANEL[3] - 1); y += 2)
			g.fill(sx(PANEL[0] + 1), y, sx(PANEL[2] - 1), y + 1, 0xFFA6A6A2);
		// The dial: dark glass lit orange behind, the scale, the pointer on the station.
		boolean on = menu.isPowered();
		g.fill(sx(DIAL[0]) - 2, sy(DIAL[1]) - 2, sx(DIAL[2]) + 2, sy(DIAL[3]) + 2, CHROME_DARK);
		box(g, DIAL, GLASS);
		if(on)
			g.fill(sx(DIAL[0]) + 1, sy(DIAL[1]) + 1, sx(DIAL[2]) - 1, sy(DIAL[3]) - 1, 0x30F08828);
		int top = sy(DIAL[1] + 1), bottom = sy(DIAL[3] - 1);
		g.fill(sx(DIAL[0] + 3.5), top, sx(DIAL[0] + 3.5) + 2, bottom, on ? 0xFFB06020 : 0xFF6A3A16);
		for(int mhz = 88; mhz <= 108; mhz++) {
			float t = (mhz * 1_000_000.0F - FmBroadcastRadioBlockEntity.LOW_HZ) / (FmBroadcastRadioBlockEntity.HIGH_HZ - FmBroadcastRadioBlockEntity.LOW_HZ);
			if(t < 0.0F || t > 1.0F)
				continue;
			int y = Math.round(bottom - t * (bottom - top));
			boolean major = mhz % 4 == 0;
			g.fill(sx(DIAL[0] + 3.5) - (major ? 6 : 3), y, sx(DIAL[0] + 3.5), y + 1, SCALE);
		}
		float pointer = dialAt(radio.getFrequency());
		int py = Math.round(bottom - pointer * (bottom - top));
		g.fill(sx(DIAL[0]) + 1, py - 1, sx(DIAL[2]) - 1, py + 1, on ? ORANGE : 0xFF9A5A24);
		g.fill(sx(DIAL[0] + 4.5), sy(DIAL[1] + 1), sx(DIAL[0] + 5.3), sy(DIAL[1] + 1.8), on ? 0xFFFFB040 : 0xFF4A2A10); // The FM lamp.
		// The knobs and switches.
		knob(g, TUNING, pointer);
		knob(g, VOLUME, radio.getSpeakerGain() / 2.0F);
		knob(g, BASS, (radio.getBass() + FmBroadcastReceiverBlockEntity.TONE_STEPS) / (2.0F * FmBroadcastReceiverBlockEntity.TONE_STEPS));
		knob(g, TREBLE, (radio.getTreble() + FmBroadcastReceiverBlockEntity.TONE_STEPS) / (2.0F * FmBroadcastReceiverBlockEntity.TONE_STEPS));
		toggle(g, POWER, on);
		toggle(g, MUTE, gainBeforeMute >= 0.0F);
	}

	/** Small print centred on texture point (u, v), relative to the screen's corner. */
	private void legend(GuiGraphics g, String text, double u, double v, int colour) {
		g.pose().pushPose();
		g.pose().translate((float)(u * K) - font.width(text) / 4.0F, (float)(v * K), 0.0F);
		g.pose().scale(0.5F, 0.5F, 1.0F);
		g.drawString(font, text, 0, 0, colour, false);
		g.pose().popPose();
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
		legend(g, "TUNING", (TUNING[0] + TUNING[2]) / 2.0D, 4.9, PRINT);
		legend(g, "VOLUME", (VOLUME[0] + VOLUME[2]) / 2.0D, 13.9, PRINT);
		legend(g, "BASS", (BASS[0] + BASS[2]) / 2.0D, 20.0, PRINT);
		legend(g, "TREBLE", (TREBLE[0] + TREBLE[2]) / 2.0D, 20.0, PRINT);
		legend(g, "POWER", (POWER[0] + POWER[2]) / 2.0D, 27.15, PRINT);
		legend(g, "MUTE", (MUTE[0] + MUTE[2]) / 2.0D, 27.15, PRINT);
		legend(g, "FM", DIAL[0] + 5.0, DIAL[1] + 2.1, SCALE);
		legend(g, "MHz", (DIAL[0] + DIAL[2]) / 2.0D, DIAL[3] + 0.2, PRINT);
		for(int mhz = 88; mhz <= 108; mhz += 4) { // The scale's figures, beside its long ticks.
			float t = (mhz * 1_000_000.0F - FmBroadcastRadioBlockEntity.LOW_HZ) / (FmBroadcastRadioBlockEntity.HIGH_HZ - FmBroadcastRadioBlockEntity.LOW_HZ);
			t = Math.max(0.0F, Math.min(1.0F, t));
			double v = (DIAL[3] - 1) - t * (DIAL[3] - DIAL[1] - 2) - 0.45;
			legend(g, Integer.toString(mhz), DIAL[0] + 1.3, v, SCALE);
		}
		int cell = radio.getCellPercent();
		String power = !menu.isPowered() ? "OFF" : cell >= 0 ? String.format(Locale.ROOT, "BATTERY %d%%", cell) : "";
		String status = String.format(Locale.ROOT, "FM %s MHz   VOL %d%%   BASS %+d   TREBLE %+d   %s", FmBroadcastRadioBlockEntity.format(radio.getFrequency()),
				Math.round(radio.getSpeakerGain() * 100.0F), radio.getBass(), radio.getTreble(), power);
		g.drawString(font, status, 2, H * K + 3, 0xFFC8C8C0, false);
	}

	/** What the part under the mouse does. */
	private Component hint(int x, int y) {
		if(over(DIAL, x, y))
			return Component.translatable("screen.radiocraft.fm_receiver.dial");
		if(over(TUNING, x, y))
			return Component.translatable("screen.radiocraft.fm_receiver.tuning");
		if(over(VOLUME, x, y))
			return Component.translatable("screen.radiocraft.fm_receiver.volume");
		if(over(BASS, x, y))
			return Component.translatable("screen.radiocraft.fm_receiver.bass");
		if(over(TREBLE, x, y))
			return Component.translatable("screen.radiocraft.fm_receiver.treble");
		if(over(POWER, x, y))
			return Component.translatable("screen.radiocraft.fm_receiver.power");
		if(over(MUTE, x, y))
			return Component.translatable("screen.radiocraft.fm_receiver.mute");
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
