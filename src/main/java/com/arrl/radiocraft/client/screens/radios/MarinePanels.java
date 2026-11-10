package com.arrl.radiocraft.client.screens.radios;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * Drawing the moving parts of the marine radios' panels (2026-11-09): the panel PNGs and their layouts come from
 * {@code tools/marine_radio_assets.py} and load as an {@link HfPanel} ({@code gui_layouts/marine_radio.json},
 * {@code marine_handheld.json}). Knob pointers, switch handles and lamps are drawn as the HF panels draw theirs; the
 * mic jack ("jack"), the side push-to-talk bar ("side_ptt") and the handheld's top knob seen side on ("side_knob", a
 * white index mark that runs round the cylinder) are this pair's own. Coordinates are panel pixels from {@code left},
 * {@code top}.
 */
final class MarinePanels {

	private MarinePanels() {}

	/** A knob's pointer at {@code value} (0 to 1) between its {@code min_deg} and {@code max_deg}. */
	static void knob(GuiGraphics g, int left, int top, HfPanel.Control c, double value) {
		double min = c.num("min_deg", -135), max = c.num("max_deg", 135);
		double deg = min + Math.max(0.0D, Math.min(1.0D, value)) * (max - min);
		ray(g, left, top, c.num("cx", 0), c.num("cy", 0), deg, c.num("pointer_from", 0), c.num("pointer_r", 6), (int)c.num("pointer_width", 1),
				c.colour("pointer_colour", 0xFFE0E0E0));
	}

	/**
	 * The handheld's top knob seen from the front: its index mark slides across the cylinder as it turns, and is round
	 * the back (not drawn) past a quarter turn either way.
	 */
	static void sideKnob(GuiGraphics g, int left, int top, HfPanel.Control c, double value) {
		double min = c.num("min_deg", -135), max = c.num("max_deg", 135);
		double deg = min + Math.max(0.0D, Math.min(1.0D, value)) * (max - min);
		if(Math.abs(deg) >= 85.0D)
			return;
		int x = (int)Math.floor(c.num("cx", 0) + c.num("r", 8) * Math.sin(Math.toRadians(deg)));
		fill(g, left, top, x, (int)c.num("y0", 0), 1, (int)(c.num("y1", 0) - c.num("y0", 0)), c.colour("mark_colour", 0xFFF0F0F0));
	}

	/** A switch's handle in position {@code position} (one of its {@code positions}). */
	static void switchHandle(GuiGraphics g, int left, int top, HfPanel.Control c, String position) {
		int i = c.strings("positions").indexOf(position);
		var handles = c.rects("handles");
		if(i < 0 || i >= handles.size())
			return;
		int[] h = handles.get(i);
		fill(g, left, top, h[0], h[1], h[2], h[3], c.colour("handle_shadow", 0xFF606060));
		fill(g, left, top, h[0], h[1], h[2] - 1, h[3] - 1, c.colour("handle_colour", 0xFFC8C8C8));
	}

	/** A lamp lit in {@code colour}, with a lighter centre. */
	static void lamp(GuiGraphics g, int left, int top, HfPanel.Control c, int colour) {
		fill(g, left, top, c.x(), c.y(), c.w(), c.h(), colour);
		if(c.w() >= 3 && c.h() >= 3)
			fill(g, left, top, c.x() + 1, c.y() + 1, c.w() - 2, c.h() - 2, lighter(colour));
	}

	/** A key: its lit strip when {@code lit}, and darkened while {@code pressed}. */
	static void button(GuiGraphics g, int left, int top, HfPanel.Control c, boolean lit, boolean pressed) {
		int[] strip = c.lit();
		if(lit && strip != null)
			fill(g, left, top, strip[0], strip[1], strip[2], strip[3], strip[4]);
		if(pressed)
			fill(g, left, top, c.x(), c.y(), c.w(), c.h(), 0x55000000);
	}

	/** A red glow round the mic jack while it is held for push-to-talk. */
	static void jackGlow(GuiGraphics g, int left, int top, HfPanel.Control c) {
		int colour = c.colour("glow", 0xFFFF3020);
		double cx = c.x() + c.w() / 2.0D, cy = c.y() + c.h() / 2.0D, r = c.w() / 2.0D;
		for(int y = c.y() - 2; y < c.y() + c.h() + 2; y++)
			for(int x = c.x() - 2; x < c.x() + c.w() + 2; x++) {
				double d = Math.hypot(x + 0.5D - cx, y + 0.5D - cy);
				if(d >= r - 0.5D && d < r + 1.5D)
					fill(g, left, top, x, y, 1, 1, colour);
			}
	}

	/**
	 * The DISTRESS button's hinged cover (2026-11-09): shut, a red see-through lid over the button and its rim
	 * ({@code cover}); lifted, its edge in the strip above the button ({@code cover_open}).
	 */
	static void cover(GuiGraphics g, int left, int top, HfPanel.Control c, boolean open) {
		int fill = c.colour("cover_colour", 0xFFC82828), edge = c.colour("cover_edge", 0xFF701010), shine = c.colour("cover_shine", 0xFFF07070);
		var rects = c.rects(open ? "cover_open" : "cover");
		if(rects.isEmpty())
			return;
		int[] r = rects.getFirst();
		if(open) {
			fill(g, left, top, r[0], r[1], r[2], r[3], fill);
			fill(g, left, top, r[0], r[1], r[2], 1, shine);
			fill(g, left, top, r[0], r[1] + r[3] - 1, r[2], 1, edge);
		}
		else {
			fill(g, left, top, r[0], r[1], r[2], r[3], 0x96000000 | (fill & 0xFFFFFF));
			fill(g, left, top, r[0], r[1], r[2], 1, shine);
			fill(g, left, top, r[0], r[1] + r[3] - 1, r[2], 1, edge);
			fill(g, left, top, r[0], r[1], 1, r[3], edge);
			fill(g, left, top, r[0] + r[2] - 1, r[1], 1, r[3], edge);
		}
	}

	/** The side push-to-talk bar tinted red while held. */
	static void sidePttGlow(GuiGraphics g, int left, int top, HfPanel.Control c) {
		fill(g, left, top, c.x(), c.y(), c.w(), c.h(), 0x88000000 | (c.colour("glow", 0xFFFF3020) & 0xFFFFFF));
	}

	/** {@code text} at {@code scale}, left at {@code x} (or right-aligned to it when {@code right}), top at {@code y}. */
	static void text(GuiGraphics g, Font font, String text, float x, float y, float scale, int colour, boolean right) {
		g.pose().pushPose();
		g.pose().translate(right ? x - font.width(text) * scale : x, y, 0.0F);
		g.pose().scale(scale, scale, 1.0F);
		g.drawString(font, text, 0, 0, colour, false);
		g.pose().popPose();
	}

	/** {@code text} as large as fits in the control's box (at most {@code maxScale}), centred in it. */
	static void centred(GuiGraphics g, Font font, int left, int top, HfPanel.Control c, String text, float maxScale, int colour) {
		if(text.isEmpty())
			return;
		float scale = Math.min(maxScale, Math.min(c.w() * 0.84F / font.width(text), c.h() * 0.8F / (font.lineHeight - 1)));
		float x = left + c.x() + (c.w() - font.width(text) * scale) / 2.0F;
		float y = top + c.y() + (c.h() - (font.lineHeight - 2) * scale) / 2.0F;
		text(g, font, text, x, y, scale, colour, false);
	}

	/** The control called {@code name} that can be worked under the screen point, or null. */
	@Nullable
	static HfPanel.Control at(HfPanel panel, int left, int top, double mouseX, double mouseY) {
		return panel.at(mouseX - left, mouseY - top);
	}

	private static void ray(GuiGraphics g, int left, int top, double cx, double cy, double deg, double from, double to, int width, int colour) {
		double sin = Math.sin(Math.toRadians(deg)), cos = Math.cos(Math.toRadians(deg));
		int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
		for(double t = from; t <= to; t += 0.35D) {
			int x = (int)Math.floor(cx + t * sin), y = (int)Math.floor(cy - t * cos);
			if(x == lastX && y == lastY)
				continue;
			lastX = x;
			lastY = y;
			fill(g, left, top, x, y, Math.max(1, width), Math.max(1, width), colour);
		}
	}

	private static void fill(GuiGraphics g, int left, int top, int x, int y, int w, int h, int colour) {
		g.fill(left + x, top + y, left + x + w, top + y + h, colour);
	}

	private static int lighter(int argb) {
		int r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;
		return (argb & 0xFF000000) | ((r + 255) / 2) << 16 | ((gr + 255) / 2) << 8 | (b + 255) / 2;
	}
}
