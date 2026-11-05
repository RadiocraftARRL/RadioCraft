package com.arrl.radiocraft.client.screens;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.bandplan.BandPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Band Plan sheet ({@link com.arrl.radiocraft.common.items.BandPlanItem}): a sheet of paper with one bar per band,
 * lowest band first, each split into coloured segments by use, with tick marks for the markers and their labels
 * underneath, then a legend. Scrolls with the wheel or the keys; hovering a bar names the frequency, its use and any
 * marker there. Reads {@link BandPlan#current()} (the server's config, synced at login).
 */
public class BandPlanScreen extends Screen {

	private static final int PAPER = 0xFFF4EFDF, EDGE = 0xFFB9AE90, INK = 0xFF2B2118, FAINT = 0xFF6E6550, MARK = 0xFF111111;
	private static final int MARGIN = 10, BAR = 10, LINE = 10;
	private static final int MAX_WIDTH = 440;

	private final BandPlan plan;
	private final List<BandPlan.Row> rows;
	private final List<String> legend;

	// Layout, worked out in init() for the window size.
	private int left, top, paperWidth, paperHeight, barLeft, barWidth;
	private final List<Integer> rowY = new ArrayList<>(); // Content y of each row's title line.
	private final List<List<FormattedCharSequence>> markerLines = new ArrayList<>();
	private int legendY, contentHeight;
	private float scroll = 0.0F;

	public BandPlanScreen() {
		super(Component.translatable(Radiocraft.translationKey("screen", "band_plan.title")));
		this.plan = BandPlan.current();
		this.rows = plan.rows(BandPlan.sheetBands());
		this.legend = plan.legendColours();
	}

	public static void open() {
		Minecraft.getInstance().setScreen(new BandPlanScreen());
	}

	@Override
	protected void init() {
		super.init();
		paperWidth = Math.min(MAX_WIDTH, width - 20);
		left = (width - paperWidth) / 2;
		top = 10;
		paperHeight = height - 20;
		barLeft = left + MARGIN;
		barWidth = paperWidth - MARGIN * 2;
		rowY.clear();
		markerLines.clear();
		int y = LINE * 2 + 4; // Title and hint.
		for(BandPlan.Row row : rows) {
			rowY.add(y);
			List<FormattedCharSequence> lines = new ArrayList<>();
			if(!row.markers().isEmpty()) {
				StringBuilder text = new StringBuilder();
				for(BandPlan.Marker marker : row.markers()) {
					if(!text.isEmpty())
						text.append("   ");
					text.append('|').append(' ').append(mhz(marker.hz())).append(' ').append(marker.label());
				}
				lines.addAll(font.split(Component.literal(text.toString()), barWidth));
			}
			markerLines.add(lines);
			y += LINE + BAR + 2 + 8 + lines.size() * LINE + 8; // Title, bar, edge labels, marker lines, gap.
		}
		legendY = y;
		y += LINE + 2 + (legend.size() + 1) / 2 * LINE + 6;
		contentHeight = y;
		clampScroll();
	}

	private int viewHeight() {
		return paperHeight - 4;
	}

	private void clampScroll() {
		scroll = Mth.clamp(scroll, 0.0F, Math.max(0.0F, contentHeight - viewHeight()));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= (float)scrollY * LINE * 3;
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		switch(keyCode) {
			case GLFW.GLFW_KEY_PAGE_UP -> scroll -= viewHeight() - LINE * 2;
			case GLFW.GLFW_KEY_PAGE_DOWN -> scroll += viewHeight() - LINE * 2;
			case GLFW.GLFW_KEY_HOME -> scroll = 0.0F;
			case GLFW.GLFW_KEY_END -> scroll = Float.MAX_VALUE;
			case GLFW.GLFW_KEY_UP -> scroll -= LINE;
			case GLFW.GLFW_KEY_DOWN -> scroll += LINE;
			default -> {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
		clampScroll();
		return true;
	}

	@Override
	public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.fill(left - 1, top - 1, left + paperWidth + 1, top + paperHeight + 1, EDGE);
		guiGraphics.fill(left, top, left + paperWidth, top + paperHeight, PAPER);
		guiGraphics.enableScissor(left, top + 2, left + paperWidth, top + paperHeight - 2);
		int origin = top + 4 - Math.round(scroll);

		guiGraphics.drawString(font, title, left + (paperWidth - font.width(title)) / 2, origin, INK, false);
		Component hint = Component.translatable(Radiocraft.translationKey("screen", "band_plan.hint"));
		guiGraphics.drawString(font, hint, left + (paperWidth - font.width(hint)) / 2, origin + LINE, FAINT, false);

		List<Component> tooltip = null;
		for(int i = 0; i < rows.size(); i++) {
			BandPlan.Row row = rows.get(i);
			Band band = row.band();
			int y = origin + rowY.get(i);
			if(y > top + paperHeight || y + LINE + BAR + 40 + markerLines.get(i).size() * LINE < top)
				continue; // Off the sheet.
			String heading = band.displayName() + "  " + mhz(band.minFrequency()) + " - " + mhz(band.maxFrequency()) + " MHz"
					+ (band.isAmateur() ? "" : "  (commercial)");
			guiGraphics.drawString(font, heading, barLeft, y, INK, false);
			int barTop = y + LINE;
			guiGraphics.fill(barLeft - 1, barTop - 1, barLeft + barWidth + 1, barTop + BAR + 1, INK);
			guiGraphics.fill(barLeft, barTop, barLeft + barWidth, barTop + BAR, 0xFFFFFFFF);
			for(BandPlan.Segment segment : row.segments()) {
				int x1 = x(band, segment.fromHz()), x2 = Math.max(x1 + 1, x(band, segment.toHz()));
				guiGraphics.fill(x1, barTop, x2, barTop + BAR, 0xFF000000 | segment.rgb());
			}
			for(BandPlan.Marker marker : row.markers()) {
				int mx = x(band, marker.hz());
				guiGraphics.fill(mx, barTop - 3, mx + 1, barTop + BAR + 3, MARK);
			}
			small(guiGraphics, mhz(band.minFrequency()), barLeft, barTop + BAR + 2, FAINT, false);
			small(guiGraphics, mhz(band.maxFrequency()), barLeft + barWidth, barTop + BAR + 2, FAINT, true);
			int lineY = barTop + BAR + 10;
			for(FormattedCharSequence line : markerLines.get(i)) {
				guiGraphics.drawString(font, line, barLeft, lineY, INK, false);
				lineY += LINE;
			}
			if(mouseX >= barLeft && mouseX < barLeft + barWidth && mouseY >= barTop && mouseY < barTop + BAR
					&& mouseY >= top && mouseY < top + paperHeight)
				tooltip = hover(row, mouseX);
		}

		int y = origin + legendY;
		guiGraphics.drawString(font, Component.translatable(Radiocraft.translationKey("screen", "band_plan.legend")), barLeft, y, INK, false);
		y += LINE + 2;
		int column = barWidth / 2;
		for(int i = 0; i < legend.size(); i++) {
			String colour = legend.get(i);
			int x = barLeft + (i % 2) * column, ly = y + (i / 2) * LINE;
			guiGraphics.fill(x, ly, x + 8, ly + 8, INK);
			guiGraphics.fill(x + 1, ly + 1, x + 7, ly + 7, 0xFF000000 | BandPlan.rgb(colour));
			guiGraphics.drawString(font, BandPlan.legend(colour), x + 11, ly, INK, false);
		}
		guiGraphics.disableScissor();
		if(tooltip != null)
			guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
	}

	/** The tooltip for the mouse at {@code mouseX} over {@code row}'s bar: frequency, use, and markers under it. */
	private List<Component> hover(BandPlan.Row row, int mouseX) {
		Band band = row.band();
		double hzPerPixel = ((double)band.maxFrequency() - band.minFrequency()) / barWidth;
		double hz = band.minFrequency() + (mouseX - barLeft + 0.5D) * hzPerPixel;
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(String.format(Locale.ROOT, "%.4f MHz", hz / 1_000_000.0D)));
		String use = plan.useAt(hz);
		out.add(Component.literal(use == null ? "-" : use));
		for(BandPlan.Marker marker : plan.markersNear(hz, hzPerPixel * 2.0D))
			out.add(Component.literal(mhz(marker.hz()) + " " + marker.label()));
		return out;
	}

	/** Screen x of {@code hz} on {@code band}'s bar. */
	private int x(Band band, double hz) {
		double t = (hz - band.minFrequency()) / ((double)band.maxFrequency() - band.minFrequency());
		return barLeft + (int)Math.round(Mth.clamp(t, 0.0D, 1.0D) * barWidth);
	}

	/** Small text (three quarters size), left-aligned at {@code x} or right-aligned to it. */
	private void small(GuiGraphics guiGraphics, String text, int x, int y, int colour, boolean right) {
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(right ? x - font.width(text) * 0.75F : x, y, 0.0F);
		guiGraphics.pose().scale(0.75F, 0.75F, 1.0F);
		guiGraphics.drawString(font, text, 0, 0, colour, false);
		guiGraphics.pose().popPose();
	}

	/** Hz as MHz text: three decimals, or more if the frequency needs them (151.8200 stays 151.820). */
	private static String mhz(double hz) {
		return String.format(Locale.ROOT, "%.3f", hz / 1_000_000.0D);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
