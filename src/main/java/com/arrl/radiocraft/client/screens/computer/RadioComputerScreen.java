package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.client.screens.TerminalWhirr;
import com.arrl.radiocraft.client.screens.moderation.ModerationLayout;
import com.arrl.radiocraft.common.menus.RadioComputerMenu;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The radio computer's screen (1.12 step 5): a VGA monitor's 80 x 25 colour text mode, distinct from the moderation
 * computer's Windows 98 desktop. The programs ({@link DosApp}s on a {@link ComputerSession}) draw into a
 * {@link TextGrid}; {@link DosRenderer} draws it inside a dark bezel, scaled to fit the window (up to twice size, in
 * half steps, so the text stays crisp). The keyboard goes to the program in front; Escape in the shell closes the
 * screen. The fan whirrs while it is open, and the screen is copied onto the monitor in the world.
 */
public final class RadioComputerScreen extends AbstractContainerScreen<RadioComputerMenu> {

	private static final int BEZEL = 8;
	private static final int BEZEL_COLOUR = 0xFF2A2A2C, BEZEL_LIGHT = 0xFF4A4A4E, GLASS_EDGE = 0xFF0A0A0C;
	/** The music note in the top bar while MUSIC plays here: first column, and its width (" ♫ "). */
	private static final int MUSIC_COL = TextGrid.COLS - 4, MUSIC_W = 3;

	private final ComputerSession session;
	private final TextGrid grid = new TextGrid();
	private float scale = 1.0F;
	private int gridX, gridY;
	@Nullable
	private DosBoot boot;
	@Nullable
	private TerminalWhirr whirr;
	private boolean started = false;
	/** Whether the computer had power last tick: when it comes back the computer boots again. */
	private boolean wasPowered = true;
	/** The password prompt's field, while the computer asks this player for its password ({@link RadioComputerMenu#isLocked}). */
	private final DosInput passwordField = new DosInput("", com.arrl.radiocraft.common.computer.ComputerLock.MAX_LENGTH).masked();
	private String lockMessage = "";
	private boolean wasLocked = false;

	public RadioComputerScreen(RadioComputerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		session = ComputerSession.open(menu.getBlockPos(), menu.monitor);
	}

	/** An answer or event from the server ({@code CComputerPacket}). Client thread. */
	public static void receive(String kind, CompoundTag data) {
		ComputerSession.receive(kind, data);
	}

	// -------------------- LAYOUT --------------------

	@Override
	protected void init() {
		Window window = Minecraft.getInstance().getWindow();
		int guiW = window.getGuiScaledWidth(), guiH = window.getGuiScaledHeight();
		int needW = TextGrid.WIDTH + BEZEL * 2 + 8, needH = TextGrid.HEIGHT + BEZEL * 2 + 8;
		float fit = Math.min((float)guiW / needW, (float)guiH / needH);
		scale = fit >= 1.0F ? Math.min(2.0F, (float)Math.floor(fit * 2.0F) / 2.0F) : ModerationLayout.fitScale(guiW, guiH, needW, needH);
		width = (int)(guiW / scale);
		height = (int)(guiH / scale);
		imageWidth = TextGrid.WIDTH + BEZEL * 2;
		imageHeight = TextGrid.HEIGHT + BEZEL * 2;
		super.init();
		gridX = leftPos + BEZEL;
		gridY = topPos + BEZEL;
		if(!started) {
			started = true;
			whirr = TerminalWhirr.start(0.1F);
			if(!session.resumed())
				boot = new DosBoot();
			session.markOpened();
			session.refresh();
		}
	}

	@Override
	public void removed() {
		if(whirr != null)
			whirr.finish();
		session.closed();
		super.removed();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		boolean powered = menu.isPowered();
		if(powered && !wasPowered) { // Power back: it starts up again.
			boot = new DosBoot();
			session.refresh();
		}
		wasPowered = powered;
		if(!powered)
			return;
		boolean locked = menu.isLocked();
		if(wasLocked && !locked) // Password typed: the programs carry on.
			session.refresh();
		wasLocked = locked;
		if(locked)
			return;
		if(boot == null || !boot.running())
			session.tick();
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(@NotNull GuiGraphics g, int rawX, int rawY, float partialTick) {
		renderBackground(g, rawX, rawY, partialTick);
		g.pose().pushPose();
		g.pose().scale(scale, scale, 1.0F);
		// The monitor's bezel and the glass's dark edge.
		g.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, BEZEL_LIGHT);
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BEZEL_COLOUR);
		g.fill(gridX - 2, gridY - 2, gridX + TextGrid.WIDTH + 2, gridY + TextGrid.HEIGHT + 2, GLASS_EDGE);
		long now = System.currentTimeMillis();
		if(!menu.isPowered()) { // Dark glass, and why.
			grid.clear(DosColours.BLACK, DosColours.BLACK);
			grid.center(TextGrid.ROWS / 2, Dos.tr("no_power"), DosColours.DARK_GREY, DosColours.BLACK);
		}
		else if(boot != null && boot.running())
			boot.render(grid, session.serial(), Dos.drive(session));
		else if(menu.isLocked())
			renderLock(now);
		else {
			boot = null;
			session.current().draw(grid, now);
			if(showsMusicNote()) // Music on in the background: a note in the top bar goes back to it.
				grid.put(MUSIC_COL, 0, " \u266B ", DosColours.YELLOW, DosColours.BLUE);
		}
		DosRenderer.render(g, font, grid, gridX, gridY, now / 400L % 2L == 0L);
		g.pose().popPose();
		session.capture(g, gridX * scale, gridY * scale, TextGrid.WIDTH * scale, TextGrid.HEIGHT * scale);
	}

	/** The password prompt: nothing else shows until it is typed. */
	private void renderLock(long now) {
		Dos.frame(grid, Dos.tr("lock.title"), Dos.tr("lock.keys"));
		int w = 40, h = 7, col = (TextGrid.COLS - w) / 2, row = (TextGrid.ROWS - h) / 2;
		grid.box(col, row, w, h, true, Dos.tr("lock.box"), DosColours.TEXT_FG, DosColours.BODY_BG);
		grid.put(col + 2, row + 2, Dos.tr("lock.prompt"), DosColours.TEXT_FG, DosColours.BODY_BG);
		passwordField.render(grid, col + 2, row + 3, w - 4, now / 400L % 2L == 0L);
		if(!lockMessage.isEmpty())
			grid.put(col + 2, row + 5, Dos.fit(lockMessage, w - 4), DosColours.YELLOW, DosColours.BODY_BG);
	}

	/** Sends the typed password; a wrong one clears the field and says so. */
	private void submitPassword() {
		CompoundTag data = new CompoundTag();
		data.putString("password", passwordField.text());
		passwordField.set("");
		lockMessage = "";
		session.request("sys.unlock", data, response -> {
			if(!response.ok())
				lockMessage = response.errorText();
		});
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {}

	// -------------------- INPUT --------------------

	private int col(double rawX) {
		return (int)Math.floor((rawX / scale - gridX) / TextGrid.CELL_W);
	}

	private int row(double rawY) {
		return (int)Math.floor((rawY / scale - gridY) / TextGrid.CELL_H);
	}

	/** True (and the boot skipped) if the boot was running and took this input. */
	private boolean skipBoot() {
		if(boot != null && boot.running()) {
			boot.skip();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if(!menu.isPowered()) {
			if(key == GLFW.GLFW_KEY_ESCAPE)
				onClose();
			return true;
		}
		if(skipBoot())
			return true;
		if(menu.isLocked()) {
			if(key == GLFW.GLFW_KEY_ESCAPE)
				onClose();
			else if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
				submitPassword();
			else
				passwordField.keyPressed(key);
			return true;
		}
		boolean used = session.current().key(key, scanCode, modifiers);
		if(!used && key == GLFW.GLFW_KEY_ESCAPE && session.current() instanceof ShellApp) {
			onClose();
			return true;
		}
		return true; // Every other key is the program's: E must not close the screen while typing.
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		if(!menu.isPowered() || boot != null && boot.running())
			return true;
		if(menu.isLocked()) {
			passwordField.charTyped(c);
			return true;
		}
		session.current().typed(c);
		return true;
	}

	/** True while MUSIC plays on this computer and another page is in front. */
	private boolean showsMusicNote() {
		return ComputerMusic.isPlayingAt(session.pos()) && session.current().app() != com.arrl.radiocraft.common.computer.ComputerApps.MUSIC;
	}

	@Override
	public boolean mouseClicked(double rawX, double rawY, int button) {
		if(!menu.isPowered() || skipBoot() || menu.isLocked())
			return true;
		int c = col(rawX), r = row(rawY);
		if(r == 0 && c >= MUSIC_COL && c < MUSIC_COL + MUSIC_W && showsMusicNote()) {
			session.showMusic();
			Dos.click();
			return true;
		}
		if(TextGrid.inside(c, r))
			session.current().click(c, r, button);
		return true;
	}

	@Override
	public boolean mouseReleased(double rawX, double rawY, int button) {
		return true;
	}

	@Override
	public boolean mouseDragged(double rawX, double rawY, int button, double dragX, double dragY) {
		return true;
	}

	@Override
	public boolean mouseScrolled(double rawX, double rawY, double scrollX, double scrollY) {
		if(!menu.isPowered() || menu.isLocked())
			return true;
		int c = col(rawX), r = row(rawY);
		if(TextGrid.inside(c, r))
			session.current().scroll(c, r, scrollY);
		return true;
	}
}
