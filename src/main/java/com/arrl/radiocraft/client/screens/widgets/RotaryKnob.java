package com.arrl.radiocraft.client.screens.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A round knob seen from above, drawn with fills: a rim, a pointer, a dot for every position on a 270 degree arc (the
 * one it is on lit) and a label at each end stop. Not a widget: the screen hands it the mouse ({@link #press},
 * {@link #drag}, {@link #release}, {@link #scroll}) so a drag keeps turning it wherever the mouse is let go.
 * Left click turns it one step clockwise, right click one step back, the wheel likewise; dragging around it points it
 * at the mouse. It stops at both ends: the bottom gap between them does nothing.
 */
public class RotaryKnob {

	private static final double START = 225.0D, SWEEP = 270.0D;
	private static final int BODY = 0xFF26272A, RIM = 0xFF5A5D63, POINTER = 0xFFE8E8E8, DOT = 0xFF6A6D73, DOT_ON = 0xFFFFD25A, LABEL = 0xFFC8C8C8;

	private final int cx, cy, radius, positions;
	private final String startLabel, endLabel;
	private final IntSupplier position;
	private final IntConsumer onTurn;
	private boolean pressed, dragged;
	private int button;
	private double pressX, pressY;

	/**
	 * @param positions How many positions (at least 2).
	 * @param position  Where it is now, 0 to positions - 1.
	 * @param onTurn    Called with the new position when it moves (never past an end).
	 */
	public RotaryKnob(int cx, int cy, int radius, int positions, String startLabel, String endLabel, IntSupplier position, IntConsumer onTurn) {
		this.cx = cx;
		this.cy = cy;
		this.radius = radius;
		this.positions = Math.max(2, positions);
		this.startLabel = startLabel;
		this.endLabel = endLabel;
		this.position = position;
		this.onTurn = onTurn;
	}

	/** The angle (degrees, counter-clockwise from the right) of position {@code p}. */
	private double angle(int p) {
		return START - p * SWEEP / (positions - 1);
	}

	public boolean isOver(double x, double y) {
		double dx = x - cx, dy = y - cy;
		return dx * dx + dy * dy <= (radius + 5) * (radius + 5);
	}

	public boolean press(double x, double y, int mouseButton) {
		if(!isOver(x, y) || mouseButton > 1)
			return false;
		pressed = true;
		dragged = false;
		button = mouseButton;
		pressX = x;
		pressY = y;
		return true;
	}

	public boolean drag(double x, double y) {
		if(!pressed)
			return false;
		if(!dragged && Math.abs(x - pressX) + Math.abs(y - pressY) < 4)
			return true;
		dragged = true;
		double a = Math.toDegrees(Math.atan2(cy - y, x - cx));
		if(a <= -135.0D)
			a += 360.0D;
		if(a < -45.0D - SWEEP / (positions - 1) / 2.0D && a > -135.0D)
			return true; // In the gap below the knob, between the stops.
		int p = (int)Math.round((START - a) / (SWEEP / (positions - 1)));
		p = Math.max(0, Math.min(positions - 1, p));
		if(p != position.getAsInt())
			onTurn.accept(p);
		return true;
	}

	public boolean release() {
		if(!pressed)
			return false;
		pressed = false;
		if(!dragged)
			step(button == 1 ? -1 : 1);
		return true;
	}

	public boolean scroll(double x, double y, double amount) {
		if(!isOver(x, y) || amount == 0.0D)
			return false;
		step(amount > 0.0D ? 1 : -1);
		return true;
	}

	private void step(int steps) {
		int next = position.getAsInt() + steps;
		if(next >= 0 && next < positions)
			onTurn.accept(next);
	}

	public void render(GuiGraphics graphics, Font font) {
		int at = Math.max(0, Math.min(positions - 1, position.getAsInt()));
		for(int p = 0; p < positions; p++) {
			double a = Math.toRadians(angle(p));
			int dx = (int)Math.round(cx + Math.cos(a) * (radius + 3)), dy = (int)Math.round(cy - Math.sin(a) * (radius + 3));
			graphics.fill(dx - 1, dy - 1, dx + 1, dy + 1, p == at ? DOT_ON : DOT);
		}
		disc(graphics, radius, RIM);
		disc(graphics, radius - 1, BODY);
		double a = Math.toRadians(angle(at));
		for(int i = 2; i < radius - 1; i++) {
			int px = (int)Math.round(cx + Math.cos(a) * i), py = (int)Math.round(cy - Math.sin(a) * i);
			graphics.fill(px - 1, py - 1, px + 1, py + 1, POINTER);
		}
		label(graphics, font, startLabel, angle(0));
		label(graphics, font, endLabel, angle(positions - 1));
	}

	private void label(GuiGraphics graphics, Font font, String text, double degrees) {
		double a = Math.toRadians(degrees);
		float x = (float)(cx + Math.cos(a) * (radius + 9)), y = (float)(cy - Math.sin(a) * (radius + 9));
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0.0F);
		graphics.pose().scale(0.5F, 0.5F, 1.0F);
		graphics.drawString(font, text, -font.width(text) / 2, -4, LABEL, false);
		graphics.pose().popPose();
	}

	private void disc(GuiGraphics graphics, int r, int colour) {
		for(int y = -r; y <= r; y++) {
			int half = (int)Math.round(Math.sqrt(r * r - y * y + 0.25D));
			graphics.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, colour);
		}
	}
}
