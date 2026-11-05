package com.arrl.radiocraft.client.screens.computer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws a {@link TextGrid} as a VGA text screen: cell backgrounds, then the pictures, then the characters (the vanilla
 * font's glyphs, each centred in its 6 x 10 cell so the text is monospaced) with box-drawing and shading characters as
 * lines and fills, the blinking cursor, and faint CRT scan lines. Everything of a kind goes into one batch (one
 * flush), so the 2000 cells cost a few draw calls, not 2000.
 */
public final class DosRenderer {

	private static final String[] GLYPHS = new String[256];
	private static final int[] GLYPH_WIDTHS = new int[256];
	private static final int SCANLINE = 0x16000000;

	private DosRenderer() {}

	/** Draws {@code grid} with its top left at {@code x, y} (screen pixels before scaling). */
	public static void render(GuiGraphics g, Font font, TextGrid grid, int x, int y, boolean blinkOn) {
		Matrix4f pose = g.pose().last().pose();
		MultiBufferSource.BufferSource buffers = g.bufferSource();
		g.flush();

		// Backgrounds: one quad per run of a colour along a row.
		VertexConsumer fills = buffers.getBuffer(RenderType.gui());
		for(int row = 0; row < TextGrid.ROWS; row++) {
			int start = 0;
			for(int col = 1; col <= TextGrid.COLS; col++)
				if(col == TextGrid.COLS || grid.backgroundAt(col, row) != grid.backgroundAt(start, row)) {
					quad(fills, pose, x + start * TextGrid.CELL_W, y + row * TextGrid.CELL_H, x + col * TextGrid.CELL_W, y + (row + 1) * TextGrid.CELL_H, 0xFF000000 | grid.backgroundAt(start, row));
					start = col;
				}
		}
		g.flush();

		// Pictures, over the backgrounds and under the text.
		for(TextGrid.Image image : grid.images()) {
			ResourceLocation texture = DosImage.texture(image.key(), image.pixels(), image.width(), image.height());
			g.blit(texture, x + image.x(), y + image.y(), image.w(), image.h(), 0.0F, 0.0F, image.width(), image.height(), image.width(), image.height());
		}

		// Lines, blocks and shapes.
		fills = buffers.getBuffer(RenderType.gui());
		for(int row = 0; row < TextGrid.ROWS; row++)
			for(int col = 0; col < TextGrid.COLS; col++) {
				char c = grid.charAt(col, row);
				if(c > 0x2000)
					shape(fills, pose, c, x + col * TextGrid.CELL_W, y + row * TextGrid.CELL_H, 0xFF000000 | grid.foregroundAt(col, row));
			}
		if(blinkOn && grid.cursorVisible()) {
			int cx = x + grid.cursorCol() * TextGrid.CELL_W, cy = y + (grid.cursorRow() + 1) * TextGrid.CELL_H;
			quad(fills, pose, cx, cy - 2, cx + TextGrid.CELL_W, cy, 0xFF000000 | grid.foregroundAt(grid.cursorCol(), grid.cursorRow()));
		}
		g.flush();

		// Characters.
		for(int row = 0; row < TextGrid.ROWS; row++)
			for(int col = 0; col < TextGrid.COLS; col++) {
				char c = grid.charAt(col, row);
				if(c <= ' ' || c > 0x2000 && isShape(c))
					continue;
				String glyph = glyph(c);
				int width = c < 256 ? glyphWidth(font, c) : font.width(glyph);
				float gx = x + col * TextGrid.CELL_W + (TextGrid.CELL_W - (width - 1)) / 2.0F;
				font.drawInBatch(glyph, (float)Math.floor(gx), y + row * TextGrid.CELL_H + 1, 0xFF000000 | grid.foregroundAt(col, row), false, pose, buffers,
						Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
			}
		g.flush();

		// Scan lines, every other pixel row.
		fills = buffers.getBuffer(RenderType.gui());
		for(int line = 1; line < TextGrid.HEIGHT; line += 2)
			quad(fills, pose, x, y + line, x + TextGrid.WIDTH, y + line + 1, SCANLINE);
		g.flush();
	}

	/** A character as the font should draw it: the formatting sign and control characters made harmless. */
	private static String glyph(char c) {
		if(c < 256) {
			String cached = GLYPHS[c];
			if(cached == null) {
				char shown = c == '§' ? '?' : c == '\f' ? '¶' : c < ' ' ? ' ' : c;
				GLYPHS[c] = cached = String.valueOf(shown);
			}
			return cached;
		}
		return String.valueOf(c);
	}

	private static int glyphWidth(Font font, char c) {
		int width = GLYPH_WIDTHS[c];
		if(width == 0)
			GLYPH_WIDTHS[c] = width = Math.max(1, font.width(glyph(c)));
		return width;
	}

	private static boolean isShape(char c) {
		return TextGrid.isBoxDrawing(c) || TextGrid.isBlock(c) || c == TextGrid.ARROW_UP || c == TextGrid.ARROW_DOWN
				|| c == TextGrid.ARROW_LEFT || c == TextGrid.ARROW_RIGHT;
	}

	/** Draws a box-drawing, block or arrow character in the cell at {@code x, y}. */
	private static void shape(VertexConsumer out, Matrix4f pose, char c, int x, int y, int colour) {
		int w = TextGrid.CELL_W, h = TextGrid.CELL_H;
		int cx = x + w / 2 - 1, cy = y + h / 2 - 1;
		switch(c) {
			case TextGrid.FULL -> quad(out, pose, x, y, x + w, y + h, colour);
			case TextGrid.LOWER_HALF -> quad(out, pose, x, y + h / 2, x + w, y + h, colour);
			case TextGrid.UPPER_HALF -> quad(out, pose, x, y, x + w, y + h / 2, colour);
			case TextGrid.SHADE_LIGHT -> quad(out, pose, x, y, x + w, y + h, colour & 0x00FFFFFF | 0x48000000);
			case TextGrid.SHADE_MEDIUM -> quad(out, pose, x, y, x + w, y + h, colour & 0x00FFFFFF | 0x90000000);
			case TextGrid.SHADE_DARK -> quad(out, pose, x, y, x + w, y + h, colour & 0x00FFFFFF | 0xC8000000);
			case TextGrid.ARROW_UP -> {
				for(int i = 0; i < 3; i++)
					quad(out, pose, cx - i, cy - 1 + i, cx + 2 + i, cy + i, colour);
			}
			case TextGrid.ARROW_DOWN -> {
				for(int i = 0; i < 3; i++)
					quad(out, pose, cx - i, cy + 1 - i, cx + 2 + i, cy + 2 - i, colour);
			}
			case TextGrid.ARROW_RIGHT -> {
				for(int i = 0; i < 3; i++)
					quad(out, pose, cx - 1 + i, cy - 2 + i, cx + i, cy + 3 - i, colour);
			}
			case TextGrid.ARROW_LEFT -> {
				for(int i = 0; i < 3; i++)
					quad(out, pose, cx + 1 - i, cy - 2 + i, cx + 2 - i, cy + 3 - i, colour);
			}
			default -> lines(out, pose, c, x, y, cx, cy, colour);
		}
	}

	/** The box-drawing characters: single lines from the cell's middle to its edges, double lines as two. */
	private static void lines(VertexConsumer out, Matrix4f pose, char c, int x, int y, int cx, int cy, int colour) {
		int x1 = x + TextGrid.CELL_W, y1 = y + TextGrid.CELL_H;
		switch(c) {
			case TextGrid.DOUBLE_H -> {
				quad(out, pose, x, cy - 1, x1, cy, colour);
				quad(out, pose, x, cy + 1, x1, cy + 2, colour);
			}
			case TextGrid.DOUBLE_V -> {
				quad(out, pose, cx - 1, y, cx, y1, colour);
				quad(out, pose, cx + 1, y, cx + 2, y1, colour);
			}
			case TextGrid.DOUBLE_TL -> { // Right and down: the outer line round the corner, the inner one inside it.
				quad(out, pose, cx - 1, cy - 1, x1, cy, colour);
				quad(out, pose, cx - 1, cy - 1, cx, y1, colour);
				quad(out, pose, cx + 1, cy + 1, x1, cy + 2, colour);
				quad(out, pose, cx + 1, cy + 1, cx + 2, y1, colour);
			}
			case TextGrid.DOUBLE_TR -> { // Left and down.
				quad(out, pose, x, cy - 1, cx + 2, cy, colour);
				quad(out, pose, cx + 1, cy - 1, cx + 2, y1, colour);
				quad(out, pose, x, cy + 1, cx, cy + 2, colour);
				quad(out, pose, cx - 1, cy + 1, cx, y1, colour);
			}
			case TextGrid.DOUBLE_BL -> { // Right and up.
				quad(out, pose, cx - 1, cy + 1, x1, cy + 2, colour);
				quad(out, pose, cx - 1, y, cx, cy + 2, colour);
				quad(out, pose, cx + 1, cy - 1, x1, cy, colour);
				quad(out, pose, cx + 1, y, cx + 2, cy, colour);
			}
			case TextGrid.DOUBLE_BR -> { // Left and up.
				quad(out, pose, x, cy + 1, cx + 2, cy + 2, colour);
				quad(out, pose, cx + 1, y, cx + 2, cy + 2, colour);
				quad(out, pose, x, cy - 1, cx, cy, colour);
				quad(out, pose, cx - 1, y, cx, cy, colour);
			}
			default -> {
				boolean up, down, left, right;
				switch(c) {
					case TextGrid.H -> { up = false; down = false; left = true; right = true; }
					case TextGrid.V -> { up = true; down = true; left = false; right = false; }
					case TextGrid.TL -> { up = false; down = true; left = false; right = true; }
					case TextGrid.TR -> { up = false; down = true; left = true; right = false; }
					case TextGrid.BL -> { up = true; down = false; left = false; right = true; }
					case TextGrid.BR -> { up = true; down = false; left = true; right = false; }
					case TextGrid.T_DOWN -> { up = false; down = true; left = true; right = true; }
					case TextGrid.T_UP -> { up = true; down = false; left = true; right = true; }
					case TextGrid.T_RIGHT -> { up = true; down = true; left = false; right = true; }
					case TextGrid.T_LEFT -> { up = true; down = true; left = true; right = false; }
					case TextGrid.CROSS -> { up = true; down = true; left = true; right = true; }
					default -> { return; } // Another box-drawing character: not used, not drawn.
				}
				if(left)
					quad(out, pose, x, cy, cx + 1, cy + 1, colour);
				if(right)
					quad(out, pose, cx, cy, x1, cy + 1, colour);
				if(up)
					quad(out, pose, cx, y, cx + 1, cy + 1, colour);
				if(down)
					quad(out, pose, cx, cy, cx + 1, y1, colour);
			}
		}
	}

	/** A filled rectangle into a {@code RenderType.gui()} buffer (as {@link GuiGraphics#fill} does, without its flush). */
	private static void quad(VertexConsumer out, Matrix4f pose, int x0, int y0, int x1, int y1, int argb) {
		out.addVertex(pose, x0, y0, 0.0F).setColor(argb);
		out.addVertex(pose, x0, y1, 0.0F).setColor(argb);
		out.addVertex(pose, x1, y1, 0.0F).setColor(argb);
		out.addVertex(pose, x1, y0, 0.0F).setColor(argb);
	}
}
