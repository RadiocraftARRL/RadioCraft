package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.ImageCaption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * STUDY, licence exam study (computer apps 2, 2026-11-06): flash cards and practice exams from the question pools the
 * server has (see {@code LicenseStudyHandler}). Pages: the pool menu (S study, E exam, P progress); a study card (A-D
 * answer, I or ? "I don't know", Enter or Space for the next card; the right answer green, a wrong pick red); a
 * practice exam (A-D pick, Left/Right or PgUp/PgDn move, F10 grade), then its review with the wrong ones marked (W
 * jumps to the next wrong one); and progress per subelement with the aptitude estimate (R resets the pool).
 */
public final class LicenseStudyApp extends DosApp {

	private enum Page { MENU, STUDY, EXAM, PROGRESS }

	private static final int TEXT_COL = 2, TEXT_WIDTH = TextGrid.COLS - 4;
	private static final int ANSWER_INDENT = 3;

	private Page page = Page.MENU;
	private String problem = "";
	private final DosList poolList = new DosList();
	private final List<CompoundTag> pools = new ArrayList<>();

	// Study.
	private String pool = "";
	private String poolName = "";
	private String sub = "";
	@Nullable
	private CompoundTag card;
	private int chosen = -2; // -2 not answered yet, -1 "I don't know".
	private int correct = -1;
	private long again = 0L;
	private int studied = 0, studiedRight = 0;

	// Exam.
	private final List<CompoundTag> exam = new ArrayList<>();
	private int[] examChoices = new int[0];
	@Nullable
	private int[] examCorrect;
	private int examAt = 0;
	private int passMark = 0;
	private String examResult = "";
	private boolean examPassed = false;
	private List<String> skipped = List.of();

	// Progress.
	@Nullable
	private CompoundTag progress;

	// Where answers were last drawn, for clicks: first and last row of each.
	private final int[] answerTop = new int[8], answerBottom = new int[8];
	private int answerCount = 0;

	public LicenseStudyApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		poolList.onActivate(i -> startStudy());
	}

	@Override
	public void opened() {
		loadPools();
	}

	@Override
	public void resumed() {
		if(page == Page.MENU)
			loadPools();
	}

	private void loadPools() {
		session.app(app, "pools", new CompoundTag(), response -> {
			if(!response.ok()) {
				problem = response.errorText();
				return;
			}
			problem = "";
			pools.clear();
			ListTag list = response.data().getList("pools", Tag.TAG_COMPOUND);
			List<String> lines = new ArrayList<>();
			for(int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				pools.add(entry);
				lines.add(String.format(Locale.ROOT, " %-15s %-10s %4d  %4d  %4d  %4s", Dos.fit(entry.getString("name"), 15),
						Dos.fit(entry.getString("element"), 10), entry.getInt("questions"), entry.getInt("seen"), entry.getInt("mastered"),
						percent(entry.getDouble("chance"))));
			}
			poolList.setItems(lines);
		});
	}

	@Nullable
	private CompoundTag selectedPool() {
		int i = poolList.selected();
		return i < 0 || i >= pools.size() ? null : pools.get(i);
	}

	private static String percent(double fraction) {
		return Math.round(fraction * 100.0D) + "%";
	}

	// -------------------- STUDY --------------------

	/** Asks which subelement to study (or all), then starts. */
	private void startStudy() {
		CompoundTag selected = selectedPool();
		if(selected == null)
			return;
		String id = selected.getString("id");
		String name = selected.getString("name");
		session.app(app, "progress", poolTag(id), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			ListTag subs = response.data().getList("subs", Tag.TAG_COMPOUND);
			List<String> lines = new ArrayList<>();
			List<String> codes = new ArrayList<>();
			lines.add(Dos.tr("study.all_subelements"));
			codes.add("");
			for(int i = 0; i < subs.size(); i++) {
				CompoundTag s = subs.getCompound(i);
				lines.add(String.format(Locale.ROOT, "%-3s %-40s %3d/%-3d", s.getString("code"), Dos.fit(s.getString("name"), 40), s.getInt("seen"), s.getInt("total")));
				codes.add(s.getString("code"));
			}
			showDialog(new DosPickDialog(Dos.tr("study.pick_title"), Dos.tr("study.pick", name), lines, i -> {
				pool = id;
				poolName = name;
				sub = codes.get(i);
				studied = 0;
				studiedRight = 0;
				page = Page.STUDY;
				nextCard();
			}));
		});
	}

	private static CompoundTag poolTag(String id) {
		CompoundTag tag = new CompoundTag();
		tag.putString("pool", id);
		return tag;
	}

	private void nextCard() {
		CompoundTag data = poolTag(pool);
		data.putString("sub", sub);
		session.app(app, "next", data, response -> {
			if(!response.ok()) {
				showError(response);
				page = Page.MENU;
				return;
			}
			card = response.data();
			chosen = -2;
			correct = -1;
		});
	}

	private void answer(int choice) {
		if(card == null || chosen != -2 || choice >= card.getList("answers", Tag.TAG_STRING).size())
			return;
		chosen = choice;
		CompoundTag data = poolTag(pool);
		data.putString("id", card.getString("id"));
		data.putInt("choice", choice);
		session.app(app, "answer", data, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			correct = response.data().getInt("correct");
			again = response.data().getLong("again");
			studied++;
			if(response.data().getBoolean("right"))
				studiedRight++;
		});
	}

	// -------------------- EXAM --------------------

	private void startExam() {
		CompoundTag selected = selectedPool();
		if(selected == null)
			return;
		String id = selected.getString("id");
		String name = selected.getString("name");
		session.app(app, "exam", poolTag(id), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			pool = id;
			poolName = name;
			exam.clear();
			ListTag questions = response.data().getList("questions", Tag.TAG_COMPOUND);
			for(int i = 0; i < questions.size(); i++)
				exam.add(questions.getCompound(i));
			examChoices = new int[exam.size()];
			java.util.Arrays.fill(examChoices, -1);
			examCorrect = null;
			examAt = 0;
			passMark = response.data().getInt("passMark");
			skipped = ComputerServer.strings(response.data().getList("skipped", Tag.TAG_STRING));
			examResult = "";
			page = Page.EXAM;
		});
	}

	private int unanswered() {
		int n = 0;
		for(int choice : examChoices)
			if(choice < 0)
				n++;
		return n;
	}

	private void askGrade() {
		if(examCorrect != null || exam.isEmpty())
			return;
		int left = unanswered();
		String message = left == 0 ? Dos.tr("study.grade_confirm") : Dos.tr("study.grade_unanswered", left);
		showDialog(DosDialog.confirm(Dos.tr("study.grade_title"), message, this::grade));
	}

	private void grade() {
		CompoundTag data = poolTag(pool);
		ListTag ids = new ListTag();
		for(CompoundTag question : exam)
			ids.add(StringTag.valueOf(question.getString("id")));
		data.put("ids", ids);
		data.putIntArray("choices", examChoices.clone());
		session.app(app, "grade", data, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			CompoundTag result = response.data();
			examCorrect = result.getIntArray("correct");
			examPassed = result.getBoolean("passed");
			examResult = Dos.tr(examPassed ? "study.passed" : "study.failed", result.getInt("score"), result.getInt("total"), result.getInt("passMark"));
			examAt = 0;
			showDialog(DosDialog.message(Dos.tr(examPassed ? "study.passed_title" : "study.failed_title"), examResult));
		});
	}

	private boolean examWrong(int i) {
		return examCorrect != null && i < examCorrect.length && examChoices[i] != examCorrect[i];
	}

	private void nextWrong() {
		for(int k = 1; k <= exam.size(); k++) {
			int i = (examAt + k) % exam.size();
			if(examWrong(i)) {
				examAt = i;
				return;
			}
		}
	}

	// -------------------- PROGRESS --------------------

	private void showProgress() {
		CompoundTag selected = selectedPool();
		if(selected == null && pool.isEmpty())
			return;
		String id = page == Page.MENU && selected != null ? selected.getString("id") : pool;
		String name = page == Page.MENU && selected != null ? selected.getString("name") : poolName;
		session.app(app, "progress", poolTag(id), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			pool = id;
			poolName = name;
			progress = response.data();
			page = Page.PROGRESS;
		});
	}

	private void askReset() {
		showDialog(DosDialog.confirm(Dos.tr("study.reset_title"), Dos.tr("study.reset", poolName), () -> session.app(app, "reset", poolTag(pool), response -> {
			if(!response.ok())
				showError(response);
			else
				showProgress();
		})));
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		switch(page) {
			case MENU -> {
				if(key == GLFW.GLFW_KEY_S)
					startStudy();
				else if(key == GLFW.GLFW_KEY_E)
					startExam();
				else if(key == GLFW.GLFW_KEY_P)
					showProgress();
				else
					return poolList.keyPressed(key);
				return true;
			}
			case STUDY -> {
				if(chosen == -2 && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_F)
					answer(key - GLFW.GLFW_KEY_A);
				else if(chosen == -2 && (key == GLFW.GLFW_KEY_I || key == GLFW.GLFW_KEY_SLASH))
					answer(-1);
				else if(chosen != -2 && correct >= 0 && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE))
					nextCard();
				else if(key == GLFW.GLFW_KEY_P)
					showProgress();
				else
					return false;
				return true;
			}
			case EXAM -> {
				if(exam.isEmpty())
					return false;
				if(key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_UP)
					examAt = Math.max(0, examAt - 1);
				else if(key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
					examAt = Math.min(exam.size() - 1, examAt + 1);
				else if(key == GLFW.GLFW_KEY_HOME)
					examAt = 0;
				else if(key == GLFW.GLFW_KEY_END)
					examAt = exam.size() - 1;
				else if(key == GLFW.GLFW_KEY_F10)
					askGrade();
				else if(examCorrect != null && key == GLFW.GLFW_KEY_W)
					nextWrong();
				else if(examCorrect == null && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_F)
					examPick(key - GLFW.GLFW_KEY_A);
				else
					return false;
				return true;
			}
			case PROGRESS -> {
				if(key == GLFW.GLFW_KEY_R) {
					askReset();
					return true;
				}
				return false;
			}
		}
		return false;
	}

	private void examPick(int choice) {
		if(choice < exam.get(examAt).getList("answers", Tag.TAG_STRING).size()) {
			examChoices[examAt] = choice;
			Dos.click();
		}
	}

	@Override
	protected boolean onEscape() {
		if(page == Page.MENU)
			return super.onEscape();
		if(page == Page.EXAM && examCorrect == null && !exam.isEmpty()) {
			showDialog(DosDialog.confirm(Dos.tr("study.leave_exam_title"), Dos.tr("study.leave_exam"), this::backToMenu));
			return true;
		}
		backToMenu();
		return true;
	}

	private void backToMenu() {
		page = Page.MENU;
		card = null;
		loadPools();
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(page == Page.MENU)
			return poolList.mouseClicked(col, row);
		for(int i = 0; i < answerCount; i++) {
			if(row >= answerTop[i] && row <= answerBottom[i]) {
				if(page == Page.STUDY)
					answer(i);
				else if(page == Page.EXAM && examCorrect == null)
					examPick(i);
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		if(page == Page.MENU)
			return poolList.mouseScrolled(col, row, amount);
		if(page == Page.EXAM && !exam.isEmpty()) {
			examAt = Math.max(0, Math.min(exam.size() - 1, examAt - (int)Math.signum(amount)));
			return true;
		}
		return false;
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		answerCount = 0;
		switch(page) {
			case MENU -> renderMenu(g);
			case STUDY -> renderStudy(g);
			case EXAM -> renderExam(g);
			case PROGRESS -> renderProgress(g);
		}
	}

	private void renderMenu(TextGrid g) {
		Dos.frame(g, Dos.tr("study.title"), Dos.tr("study.keys_menu"));
		int bg = DosColours.BODY_BG;
		if(!problem.isEmpty()) {
			g.put(2, 3, Dos.fit(problem, TextGrid.COLS - 4), DosColours.LIGHT_RED, bg);
			g.put(2, 5, Dos.fit(Dos.tr("study.no_pools_hint"), TextGrid.COLS - 4), DosColours.BODY_FG, bg);
			return;
		}
		g.put(2, 2, Dos.tr("study.intro"), DosColours.TEXT_FG, bg);
		g.put(2, 4, String.format(Locale.ROOT, " %-15s %-10s %4s  %4s  %4s  %4s", Dos.tr("study.col_pool"), Dos.tr("study.col_element"),
				Dos.tr("study.col_questions"), Dos.tr("study.col_seen"), Dos.tr("study.col_mastered"), Dos.tr("study.col_pass")), DosColours.YELLOW, bg);
		poolList.render(g, 2, 5, 60, 8, DosColours.TEXT_FG, bg, true);
		CompoundTag selected = selectedPool();
		if(selected == null)
			return;
		int row = 14;
		g.box(2, row, TextGrid.COLS - 4, 8, false, selected.getString("name"), DosColours.BODY_FG, bg);
		g.put(4, row + 1, Dos.fit(Dos.tr("study.valid", selected.getString("element"), selected.getString("valid")), TextGrid.COLS - 8), DosColours.TEXT_FG, bg);
		g.put(4, row + 2, Dos.fit(Dos.tr("study.exam_line", selected.getInt("examSize"), selected.getInt("passMark")), TextGrid.COLS - 8), DosColours.TEXT_FG, bg);
		g.put(4, row + 3, Dos.fit(Dos.tr("study.questions_line", selected.getInt("questions"), selected.getInt("figures")), TextGrid.COLS - 8), DosColours.BODY_FG, bg);
		g.put(4, row + 4, Dos.fit(Dos.tr("study.aptitude", String.format(Locale.ROOT, "%.1f", selected.getDouble("expected")), selected.getInt("examSize"),
				percent(selected.getDouble("chance"))), TextGrid.COLS - 8), chanceColour(selected.getDouble("chance")), bg);
		g.bar(4, row + 5, TextGrid.COLS - 8, selected.getInt("questions") == 0 ? 0.0D : selected.getInt("mastered") / (double)selected.getInt("questions"),
				DosColours.LIGHT_GREEN, DosColours.DARK_GREY);
		g.put(4, row + 6, Dos.fit(Dos.tr("study.mastered_line", selected.getInt("mastered"), selected.getInt("questions")), TextGrid.COLS - 8), DosColours.BODY_FG, bg);
	}

	private static int chanceColour(double chance) {
		return chance >= 0.8D ? DosColours.LIGHT_GREEN : chance >= 0.4D ? DosColours.YELLOW : DosColours.LIGHT_RED;
	}

	private void renderStudy(TextGrid g) {
		Dos.frame(g, Dos.tr("study.title_study", poolName), Dos.tr(chosen == -2 ? "study.keys_card" : "study.keys_answered"));
		int bg = DosColours.BODY_BG;
		g.put(TextGrid.COLS - 24, 0, String.format(Locale.ROOT, "%22s", Dos.tr("study.session", studiedRight, studied)), DosColours.TITLE_FG, DosColours.TITLE_BG);
		if(card == null) {
			g.put(2, 3, Dos.tr("study.loading"), DosColours.TEXT_FG, bg);
			return;
		}
		String header = card.getString("id") + "  " + card.getString("sub") + " " + card.getString("subName");
		g.put(TEXT_COL, 2, Dos.fit(header, 50), DosColours.YELLOW, bg);
		String state = card.getBoolean("new") ? Dos.tr("study.new_card") : Dos.tr("study.level", card.getInt("level"), card.getInt("seen"));
		g.put(TextGrid.COLS - 2 - state.length(), 2, state, card.getBoolean("new") ? DosColours.LIGHT_CYAN : DosColours.BODY_FG, bg);
		int row = drawQuestion(g, card, 4, chosen == -2 ? -1 : correct, chosen >= 0 ? chosen : -1, -1);
		row += 1;
		if(chosen != -2 && correct >= 0) {
			String letter = String.valueOf((char)('A' + correct));
			String verdict = chosen == correct ? Dos.tr("study.right") : chosen == -1 ? Dos.tr("study.idk_answer", letter) : Dos.tr("study.wrong_answer", letter);
			int fg = chosen == correct ? DosColours.LIGHT_GREEN : chosen == -1 ? DosColours.YELLOW : DosColours.LIGHT_RED;
			g.put(TEXT_COL, Math.min(row, 21), Dos.fit(verdict, TEXT_WIDTH), fg, bg);
			g.put(TEXT_COL, Math.min(row + 1, 22), Dos.fit(Dos.tr("study.again", again), TEXT_WIDTH), DosColours.BODY_FG, bg);
			if(!card.getString("refs").isEmpty())
				g.put(TEXT_COL, Math.min(row + 2, 23), Dos.fit(Dos.tr("study.refs", card.getString("refs")), TEXT_WIDTH), DosColours.BODY_FG, bg);
		}
	}

	/**
	 * Draws {@code question}'s text and answers from {@code top}; the answer {@code right} green, {@code wrong} red and
	 * {@code picked} highlighted (each -1 for none). Notes where each answer went for clicks. Returns the next free row.
	 */
	private int drawQuestion(TextGrid g, CompoundTag question, int top, int right, int wrong, int picked) {
		int bg = DosColours.BODY_BG;
		int row = top;
		for(String line : ImageCaption.wrap(question.getString("question"), TEXT_WIDTH)) {
			if(row > 12)
				break;
			g.put(TEXT_COL, row++, line, DosColours.TEXT_FG, bg);
		}
		row++;
		List<String> answers = ComputerServer.strings(question.getList("answers", Tag.TAG_STRING));
		answerCount = Math.min(answers.size(), answerTop.length);
		for(int i = 0; i < answerCount; i++) {
			int fg = DosColours.TEXT_FG;
			int back = bg;
			if(i == right) {
				fg = DosColours.BLACK;
				back = DosColours.LIGHT_GREEN;
			}
			else if(i == wrong) {
				fg = DosColours.WHITE;
				back = DosColours.RED;
			}
			else if(i == picked) {
				fg = DosColours.HIGHLIGHT_FG;
				back = DosColours.HIGHLIGHT_BG;
			}
			answerTop[i] = row;
			List<String> lines = ImageCaption.wrap(answers.get(i), TEXT_WIDTH - ANSWER_INDENT);
			for(int l = 0; l < lines.size() && row < TextGrid.ROWS - 3; l++) {
				String prefix = l == 0 ? (char)('A' + i) + ". " : "   ";
				g.putField(TEXT_COL, row++, prefix + lines.get(l), TEXT_WIDTH, fg, back);
			}
			answerBottom[i] = row - 1;
		}
		return row;
	}

	private void renderExam(TextGrid g) {
		boolean graded = examCorrect != null;
		Dos.frame(g, Dos.tr(graded ? "study.title_review" : "study.title_exam", poolName), Dos.tr(graded ? "study.keys_review" : "study.keys_exam"));
		int bg = DosColours.BODY_BG;
		if(exam.isEmpty()) {
			g.put(2, 3, Dos.tr("study.loading"), DosColours.TEXT_FG, bg);
			return;
		}
		CompoundTag question = exam.get(examAt);
		String header = Dos.tr("study.question_of", examAt + 1, exam.size()) + "   " + question.getString("id") + "  " + question.getString("sub") + " "
				+ question.getString("subName");
		g.put(TEXT_COL, 2, Dos.fit(header, TEXT_WIDTH), DosColours.YELLOW, bg);
		if(graded)
			drawQuestion(g, question, 4, examCorrect[examAt], examWrong(examAt) ? examChoices[examAt] : -1, -1);
		else
			drawQuestion(g, question, 4, -1, -1, examChoices[examAt]);
		// The answer sheet: a cell per question, the current one highlighted.
		int row = TextGrid.ROWS - 3;
		int width = Math.min(exam.size(), TextGrid.COLS - 4);
		for(int i = 0; i < width; i++) {
			char c;
			int fg;
			if(graded) {
				c = examWrong(i) ? 'x' : '+';
				fg = examWrong(i) ? DosColours.LIGHT_RED : DosColours.LIGHT_GREEN;
			}
			else {
				c = examChoices[i] < 0 ? '.' : (char)('A' + examChoices[i]);
				fg = examChoices[i] < 0 ? DosColours.DARK_GREY : DosColours.TEXT_FG;
			}
			g.set(TEXT_COL + i, row, c, i == examAt ? DosColours.HIGHLIGHT_FG : fg, i == examAt ? DosColours.HIGHLIGHT_BG : DosColours.BLACK);
		}
		String status = graded ? examResult : Dos.tr("study.answered", exam.size() - unanswered(), exam.size(), passMark);
		g.put(TEXT_COL, row - 1, Dos.fit(status, TEXT_WIDTH), graded ? examPassed ? DosColours.LIGHT_GREEN : DosColours.LIGHT_RED : DosColours.BODY_FG, bg);
		if(!skipped.isEmpty())
			g.put(TEXT_COL + width + 2, row, Dos.fit(Dos.tr("study.skipped", String.join(" ", skipped)), Math.max(0, TEXT_WIDTH - width - 2)), DosColours.DARK_GREY, bg);
	}

	private void renderProgress(TextGrid g) {
		Dos.frame(g, Dos.tr("study.title_progress", poolName), Dos.tr("study.keys_progress"));
		int bg = DosColours.BODY_BG;
		if(progress == null) {
			g.put(2, 3, Dos.tr("study.loading"), DosColours.TEXT_FG, bg);
			return;
		}
		g.put(2, 2, String.format(Locale.ROOT, "%-3s %-38s %5s %5s %5s", Dos.tr("study.col_sub"), Dos.tr("study.col_name"), Dos.tr("study.col_questions"),
				Dos.tr("study.col_seen"), Dos.tr("study.col_mastered")), DosColours.YELLOW, bg);
		ListTag subs = progress.getList("subs", Tag.TAG_COMPOUND);
		int row = 3;
		for(int i = 0; i < subs.size() && row < 16; i++, row++) {
			CompoundTag s = subs.getCompound(i);
			int total = s.getInt("total");
			g.put(2, row, String.format(Locale.ROOT, "%-3s %-38s %5d %5d %5d", s.getString("code"), Dos.fit(s.getString("name"), 38), total, s.getInt("seen"),
					s.getInt("mastered")), DosColours.TEXT_FG, bg);
			double seen = total == 0 ? 0.0D : s.getInt("seen") / (double)total;
			double mastered = total == 0 ? 0.0D : s.getInt("mastered") / (double)total;
			g.bar(64, row, 14, seen, DosColours.YELLOW, DosColours.DARK_GREY); // Seen in yellow, the mastered part of it green.
			int masteredCells = (int)Math.round(mastered * 14);
			for(int c = 0; c < masteredCells; c++)
				g.set(64 + c, row, TextGrid.FULL, DosColours.LIGHT_GREEN, DosColours.DARK_GREY);
		}
		row++;
		double chance = progress.getDouble("chance");
		g.put(2, row++, Dos.fit(Dos.tr("study.aptitude", String.format(Locale.ROOT, "%.1f", progress.getDouble("expected")), progress.getInt("total"),
				percent(chance)), TextGrid.COLS - 4), chanceColour(chance), bg);
		g.put(2, row++, Dos.fit(Dos.tr("study.pass_needs", progress.getInt("passMark"), progress.getInt("total")), TextGrid.COLS - 4), DosColours.BODY_FG, bg);
		g.put(2, row++, Dos.fit(Dos.tr("study.answers_line", progress.getLong("answered"), progress.getInt("correct"), progress.getInt("wrong"), progress.getInt("idk")),
				TextGrid.COLS - 4), DosColours.BODY_FG, bg);
		g.put(2, row + 1, Dos.fit(Dos.tr("study.legend"), TextGrid.COLS - 4), DosColours.DARK_GREY, bg);
	}
}
