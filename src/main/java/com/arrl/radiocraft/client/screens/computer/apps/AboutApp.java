package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * ABOUT, the built-in "About RadioCraft" program (1.12, launcher and SETUP): what the mod is and where it came from
 * (the website's words, fitted to the text screen), its version, who to thank and where to read more. Up, Down,
 * Page Up/Down, Home, End and the wheel scroll.
 */
public final class AboutApp extends DosApp {

	/** The paragraphs, lang keys under {@code screen.radiocraft.computer.about.}: "h." ones are headings. */
	private static final String[] PARAGRAPHS = { "h.what", "what", "what2", "h.story", "story", "story2", "story3",
			"h.computer", "computer", "h.credits", "credits", "credits2", "h.more", "more", "url", "discord", "source" };
	private static final int COL = 3, TOP = 6, WIDTH = TextGrid.COLS - 6, HEIGHT = TextGrid.ROWS - 8;

	private record Line(String text, boolean heading) {}

	private List<Line> lines;
	private int top = 0;

	public AboutApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	private List<Line> lines() {
		if(lines == null) {
			lines = new ArrayList<>();
			for(String key : PARAGRAPHS) {
				boolean heading = key.startsWith("h.");
				if(heading && !lines.isEmpty())
					lines.add(new Line("", false));
				for(String text : wrap(Dos.tr("about." + key), WIDTH))
					lines.add(new Line(text, heading));
				if(!heading)
					lines.add(new Line("", false));
			}
		}
		return lines;
	}

	private static String version() {
		return ModList.get().getModContainerById(Radiocraft.MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
	}

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("about.title"), Dos.tr("about.keys"));
		g.box(1, 1, TextGrid.COLS - 2, 4, true, "", DosColours.TEXT_FG, DosColours.BODY_BG);
		g.center(2, Dos.tr("about.banner"), DosColours.ACCENT, DosColours.BODY_BG);
		g.center(3, Dos.tr("about.version", version()), DosColours.BODY_FG, DosColours.BODY_BG);
		List<Line> all = lines();
		top = Math.max(0, Math.min(top, Math.max(0, all.size() - HEIGHT)));
		for(int i = 0; i < HEIGHT && top + i < all.size(); i++) {
			Line line = all.get(top + i);
			g.put(COL, TOP + i, line.text(), line.heading() ? DosColours.ACCENT : DosColours.TEXT_FG, DosColours.BODY_BG);
		}
		if(all.size() > HEIGHT) { // Where in the text: a scroll bar on the right.
			int x = TextGrid.COLS - 2;
			for(int i = 0; i < HEIGHT; i++)
				g.set(x, TOP + i, TextGrid.SHADE_LIGHT, DosColours.BODY_FG, DosColours.BODY_BG);
			int thumb = (int)Math.round((HEIGHT - 1) * (double)top / Math.max(1, all.size() - HEIGHT));
			g.set(x, TOP + thumb, TextGrid.FULL, DosColours.BODY_FG, DosColours.BODY_BG);
		}
	}

	static List<String> wrap(String text, int width) {
		List<String> out = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for(String word : text.split(" ")) {
			if(!current.isEmpty() && current.length() + 1 + word.length() > width) {
				out.add(current.toString());
				current.setLength(0);
			}
			if(!current.isEmpty())
				current.append(' ');
			current.append(word);
		}
		if(!current.isEmpty())
			out.add(current.toString());
		return out;
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_UP -> top--;
			case GLFW.GLFW_KEY_DOWN -> top++;
			case GLFW.GLFW_KEY_PAGE_UP -> top -= HEIGHT - 1;
			case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> top += HEIGHT - 1;
			case GLFW.GLFW_KEY_HOME -> top = 0;
			case GLFW.GLFW_KEY_END -> top = Integer.MAX_VALUE / 2;
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		top += amount > 0 ? -3 : 3;
		return true;
	}
}
