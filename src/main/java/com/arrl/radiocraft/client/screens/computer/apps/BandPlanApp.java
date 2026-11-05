package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.radio.bandplan.BandPlan;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * BANDPLAN, the built-in band plan viewer: the server's band plan (Step 1's {@link BandPlan}, from the server config)
 * as one coloured bar per band, markers under it, a cursor to read what each part of a band is for, and the legend.
 * Up/Down pick a band, Left/Right move the cursor (Shift for bigger steps), or click a bar.
 */
public final class BandPlanApp extends DosApp {

	private static final int LABEL_COL = 1, BAR_COL = 11, BAR_W = 66, FIRST_ROW = 2;

	private int band = 0;
	private int cursor = BAR_W / 2;

	public BandPlanApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	private static BandPlan plan() {
		return BandPlan.current();
	}

	private static List<BandPlan.Row> rows() {
		return plan().rows(BandPlan.sheetBands());
	}

	/** The frequency under cell {@code cell} of {@code row}'s bar, Hz. */
	private static double hzAt(BandPlan.Row row, int cell) {
		double low = row.band().minFrequency(), high = row.band().maxFrequency();
		return low + (cell + 0.5D) / BAR_W * (high - low);
	}

	@Nullable
	private static BandPlan.Segment segmentAt(BandPlan.Row row, double hz) {
		for(BandPlan.Segment segment : row.segments())
			if(segment.contains(hz))
				return segment;
		return null;
	}

	private static String mhz(double hz) {
		return String.format(Locale.ROOT, "%.3f", hz / 1.0e6D);
	}

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("bandplan.title"), Dos.tr("bandplan.keys"));
		List<BandPlan.Row> rows = rows();
		if(rows.isEmpty())
			return;
		band = Math.max(0, Math.min(band, rows.size() - 1));
		int bg = DosColours.BODY_BG;
		for(int i = 0; i < rows.size(); i++) {
			BandPlan.Row row = rows.get(i);
			int r = FIRST_ROW + i * 2;
			if(r + 1 > 15)
				break;
			boolean selected = i == band;
			g.putField(LABEL_COL, r, row.band().displayName(), BAR_COL - LABEL_COL - 1, selected ? DosColours.HIGHLIGHT_FG : DosColours.TEXT_FG,
					selected ? DosColours.HIGHLIGHT_BG : bg);
			for(int cell = 0; cell < BAR_W; cell++) {
				BandPlan.Segment segment = segmentAt(row, hzAt(row, cell));
				g.set(BAR_COL + cell, r, ' ', DosColours.WHITE, segment == null ? DosColours.DARK_GREY : segment.rgb());
			}
			double low = row.band().minFrequency(), width = (double)row.band().maxFrequency() - low;
			for(BandPlan.Marker marker : row.markers()) {
				int cell = (int)Math.min(BAR_W - 1, Math.max(0, (marker.hz() - low) / width * BAR_W));
				g.set(BAR_COL + cell, r + 1, TextGrid.ARROW_UP, DosColours.YELLOW, bg);
			}
			if(selected)
				g.set(BAR_COL + cursor, r, TextGrid.V, DosColours.WHITE, g.backgroundAt(BAR_COL + cursor, r));
		}

		// What the cursor is on.
		BandPlan.Row row = rows.get(band);
		double hz = hzAt(row, cursor);
		BandPlan.Segment segment = segmentAt(row, hz);
		g.put(LABEL_COL, 16, Dos.tr("bandplan.band", row.band().displayName(), mhz(row.band().minFrequency()), mhz(row.band().maxFrequency())), DosColours.TEXT_FG, bg);
		g.put(LABEL_COL, 17, Dos.fit(Dos.tr("bandplan.cursor", mhz(hz), segment == null ? Dos.tr("bandplan.unplanned") : segment.use()), TextGrid.COLS - 2), DosColours.YELLOW, bg);
		double halfCell = ((double)row.band().maxFrequency() - row.band().minFrequency()) / BAR_W;
		List<BandPlan.Marker> near = plan().markersNear(hz, halfCell);
		if(!near.isEmpty()) {
			StringBuilder marks = new StringBuilder();
			for(BandPlan.Marker marker : near)
				marks.append(marks.isEmpty() ? "" : ", ").append(mhz(marker.hz())).append(' ').append(marker.label());
			g.put(LABEL_COL, 18, Dos.fit(Dos.tr("bandplan.markers", marks.toString()), TextGrid.COLS - 2), DosColours.LIGHT_CYAN, bg);
		}

		// The legend, four columns.
		g.hline(0, 19, TextGrid.COLS, TextGrid.H, DosColours.BODY_FG, bg);
		g.put(2, 19, " " + Dos.tr("bandplan.legend") + " ", DosColours.TEXT_FG, bg);
		List<String> colours = plan().legendColours();
		for(int i = 0; i < colours.size() && i < 16; i++) {
			int c = 1 + (i % 4) * 20, r = 20 + i / 4;
			g.set(c, r, TextGrid.FULL, BandPlan.rgb(colours.get(i)), bg);
			g.set(c + 1, r, TextGrid.FULL, BandPlan.rgb(colours.get(i)), bg);
			g.put(c + 3, r, Dos.fit(BandPlan.legend(colours.get(i)), 16), DosColours.BODY_FG, bg);
		}
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		int step = Screen.hasShiftDown() ? 6 : 1;
		switch(key) {
			case GLFW.GLFW_KEY_UP -> band = Math.max(0, band - 1);
			case GLFW.GLFW_KEY_DOWN -> band = band + 1;
			case GLFW.GLFW_KEY_LEFT -> cursor = Math.max(0, cursor - step);
			case GLFW.GLFW_KEY_RIGHT -> cursor = Math.min(BAR_W - 1, cursor + step);
			case GLFW.GLFW_KEY_HOME -> cursor = 0;
			case GLFW.GLFW_KEY_END -> cursor = BAR_W - 1;
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(col < BAR_COL || col >= BAR_COL + BAR_W || row < FIRST_ROW || row >= 16)
			return false;
		band = (row - FIRST_ROW) / 2;
		cursor = col - BAR_COL;
		return true;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		cursor = Math.max(0, Math.min(BAR_W - 1, cursor - (int)Math.signum(amount)));
		return true;
	}
}
