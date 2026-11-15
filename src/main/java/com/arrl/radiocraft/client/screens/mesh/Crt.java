package com.arrl.radiocraft.client.screens.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * A green-screen terminal's text (2026-11-11): a grid of characters, each normal, bright, dim or inverse, drawn on a
 * fixed cell grid like a real character terminal, the same way on a GUI ({@link #drawGui}) and on a block's CRT in the
 * world ({@link #drawWorld}). The fire alarm controller and the SCADA console fill one each frame from what the server
 * last sent.
 */
public final class Crt {

	public static final byte NORMAL = 0, BRIGHT = 1, DIM = 2, INVERSE = 3;
	/** A character cell in font units. */
	private static final int CELL_W = 6, CELL_H = 9;

	public final int cols, rows;
	private final char[] chars;
	private final byte[] attrs;

	public Crt(int cols, int rows) {
		this.cols = cols;
		this.rows = rows;
		this.chars = new char[cols * rows];
		this.attrs = new byte[cols * rows];
		clear();
	}

	public void clear() {
		Arrays.fill(chars, ' ');
		Arrays.fill(attrs, NORMAL);
	}

	/** Writes {@code text} from column {@code col} of row {@code row}, cut at the right edge. */
	public Crt put(int col, int row, String text, byte attr) {
		if(row < 0 || row >= rows)
			return this;
		for(int i = 0; i < text.length(); i++) {
			int c = col + i;
			if(c < 0 || c >= cols)
				continue;
			chars[row * cols + c] = text.charAt(i);
			attrs[row * cols + c] = attr;
		}
		return this;
	}

	public Crt put(int col, int row, String text) {
		return put(col, row, text, NORMAL);
	}

	/** {@code text} ending at the last column of {@code row}. */
	public Crt right(int row, String text, byte attr) {
		return put(cols - text.length(), row, text, attr);
	}

	/** {@code text} centred on {@code row}. */
	public Crt centre(int row, String text, byte attr) {
		return put((cols - text.length()) / 2, row, text, attr);
	}

	/** The grid's scale to fit {@code w} x {@code h}. */
	public float scale(float w, float h) {
		return Math.min(w / (cols * CELL_W), h / (rows * CELL_H));
	}

	/** The cell under the point {@code px, py} of a GUI area drawn by {@link #drawGui} at x, y, w, h: {col, row}, or null. */
	public int[] cellAt(double px, double py, int x, int y, int w, int h) {
		float s = scale(w, h);
		float ox = x + (w - cols * CELL_W * s) / 2.0F, oy = y + (h - rows * CELL_H * s) / 2.0F;
		int col = (int)Math.floor((px - ox) / (CELL_W * s)), row = (int)Math.floor((py - oy) / (CELL_H * s));
		return col < 0 || row < 0 || col >= cols || row >= rows ? null : new int[] { col, row };
	}

	/** On a GUI, centred in x, y, w, h, in {@code ink} on the screen's own background. */
	public void drawGui(GuiGraphics g, Font font, int x, int y, int w, int h, int ink) {
		float s = scale(w, h);
		float ox = x + (w - cols * CELL_W * s) / 2.0F, oy = y + (h - rows * CELL_H * s) / 2.0F;
		g.pose().pushPose();
		g.pose().translate(ox, oy, 0.0F);
		g.pose().scale(s, s, 1.0F);
		for(int row = 0; row < rows; row++)
			for(int col = 0; col < cols; col++) {
				int i = row * cols + col;
				char c = chars[i];
				byte attr = attrs[i];
				if(attr == INVERSE)
					g.fill(col * CELL_W, row * CELL_H, col * CELL_W + CELL_W, row * CELL_H + CELL_H, ink);
				if(c == ' ')
					continue;
				String text = String.valueOf(c);
				g.drawString(font, text, col * CELL_W + (CELL_W - font.width(text)) / 2, row * CELL_H + 1, colour(attr, ink), false);
			}
		g.pose().popPose();
	}

	/**
	 * On a block's CRT: {@code pose} has its origin at the screen's top left as the viewer sees it, x to their right and
	 * y up, in blocks; the screen is {@code w} x {@code h} blocks. Lit by itself.
	 */
	public void drawWorld(PoseStack pose, MultiBufferSource buffer, Font font, float w, float h, int ink) {
		float s = scale(w, h);
		pose.pushPose();
		pose.translate((w - cols * CELL_W * s) / 2.0F, -(h - rows * CELL_H * s) / 2.0F, 0.0F);
		pose.scale(s, -s, s);
		Matrix4f matrix = pose.last().pose();
		VertexConsumer back = buffer.getBuffer(RenderType.textBackground());
		for(int row = 0; row < rows; row++)
			for(int col = 0; col < cols; col++) {
				int i = row * cols + col;
				if(attrs[i] != INVERSE)
					continue;
				float x0 = col * CELL_W, y0 = row * CELL_H, x1 = x0 + CELL_W, y1 = y0 + CELL_H;
				back.addVertex(matrix, x0, y0, -0.01F).setColor(ink).setLight(LightTexture.FULL_BRIGHT);
				back.addVertex(matrix, x0, y1, -0.01F).setColor(ink).setLight(LightTexture.FULL_BRIGHT);
				back.addVertex(matrix, x1, y1, -0.01F).setColor(ink).setLight(LightTexture.FULL_BRIGHT);
				back.addVertex(matrix, x1, y0, -0.01F).setColor(ink).setLight(LightTexture.FULL_BRIGHT);
			}
		for(int row = 0; row < rows; row++)
			for(int col = 0; col < cols; col++) {
				int i = row * cols + col;
				char c = chars[i];
				if(c == ' ')
					continue;
				String text = String.valueOf(c);
				font.drawInBatch(text, col * CELL_W + (CELL_W - font.width(text)) / 2.0F, row * CELL_H + 1, colour(attrs[i], ink), false, matrix, buffer,
						Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
			}
		pose.popPose();
	}

	private static int colour(byte attr, int ink) {
		return switch(attr) {
			case INVERSE -> 0xFF041008;
			case BRIGHT -> brighter(ink);
			case DIM -> 0xFF000000 | ((ink >> 1) & 0x7F7F7F);
			default -> ink;
		};
	}

	private static int brighter(int argb) {
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		return 0xFF000000 | ((r + 255) / 2) << 16 | ((g + 255) / 2) << 8 | (b + 255) / 2;
	}
}
