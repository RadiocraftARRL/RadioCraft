package com.arrl.radiocraft.client.screens.mesh;

import com.arrl.radiocraft.client.screens.radios.HfPanel;
import com.arrl.radiocraft.common.menus.FireAlarmControllerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * The fire alarm controller's terminal (2026-11-11): the RC-704's CRT, {@code gui_layouts/fire_alarm_controller.json}
 * over {@code textures/gui/fire_alarm/fire_alarm_controller.png} (from {@code tools/fire_alarm_controller_assets.py}),
 * with {@link FireAlarmCrt}'s screen in it. Touch the soft keys on the screen or use the keyboard: N or Down = NEXT
 * (older event), S = SILENCE, F or Home = FAST (newest), E = the menu; on the menu 1-5; on the box list Up/Down and Enter
 * (out of service or back in).
 */
public class FireAlarmControllerScreen extends AbstractContainerScreen<FireAlarmControllerMenu> {

	private final HfPanel panel = HfPanel.loadLayout("fire_alarm_controller");
	private final Crt crt = new Crt(FireAlarmCrt.COLS, FireAlarmCrt.ROWS);
	private FireAlarmCrt.View view = FireAlarmCrt.View.EVENT;
	private int event = 0, selected = 0;

	public FireAlarmControllerScreen(FireAlarmControllerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = panel.width();
		imageHeight = panel.height();
	}

	private HfPanel.Control crtControl() {
		HfPanel.Control c = panel.named("crt");
		if(c == null)
			throw new IllegalStateException("gui_layouts/fire_alarm_controller.json has no crt");
		return c;
	}

	private CompoundTag status() {
		return MeshScreens.get(menu.getBlockPos());
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(panel.texture(), leftPos, topPos, 0, 0, imageWidth, imageHeight, panel.width(), panel.height());
		HfPanel.Control c = crtControl();
		boolean blink = (System.currentTimeMillis() / 500L) % 2L == 0L;
		FireAlarmCrt.fill(crt, status(), view, event, selected, blink);
		crt.drawGui(g, font, leftPos + c.x() + 3, topPos + c.y() + 3, c.w() - 6, c.h() - 6, c.colour("ink", 0xFF5CFF7A));
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {}

	private void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.STONE_BUTTON_CLICK_ON, 1.6F, 0.35F));
	}

	private void send(int id) {
		Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
	}

	private int boxCount() {
		CompoundTag s = status();
		return s == null ? 0 : s.getList("boxes", Tag.TAG_COMPOUND).size();
	}

	private int eventCount() {
		CompoundTag s = status();
		return s == null ? 0 : s.getList("log", Tag.TAG_COMPOUND).size();
	}

	/** A soft key: 0 NEXT, 1 SILENCE, 2 FAST, 3 E. */
	private void softKey(int key) {
		click();
		switch(key) {
			case 0 -> {
				if(view == FireAlarmCrt.View.BOXES)
					selected = Math.min(Math.max(0, boxCount() - 1), selected + 1);
				else {
					view = FireAlarmCrt.View.EVENT;
					event = eventCount() == 0 ? 0 : (event + 1) % eventCount();
				}
			}
			case 1 -> send(FireAlarmControllerMenu.BUTTON_SILENCE);
			case 2 -> {
				if(view == FireAlarmCrt.View.BOXES)
					selected = Math.max(0, selected - 1);
				else {
					view = FireAlarmCrt.View.EVENT;
					event = 0;
				}
			}
			case 3 -> view = view == FireAlarmCrt.View.MENU ? FireAlarmCrt.View.EVENT : FireAlarmCrt.View.MENU;
			default -> {}
		}
	}

	private void menuItem(int item) {
		click();
		switch(item) {
			case 1 -> {
				view = FireAlarmCrt.View.EVENT;
				event = 0;
			}
			case 2 -> view = FireAlarmCrt.View.BOXES;
			case 3 -> send(FireAlarmControllerMenu.BUTTON_SETUP);
			case 4 -> send(FireAlarmControllerMenu.BUTTON_CLEAR_JAM);
			case 5 -> send(FireAlarmControllerMenu.BUTTON_SILENCE);
			default -> {}
		}
	}

	private void toggleService() {
		CompoundTag s = status();
		if(s == null)
			return;
		ListTag boxes = s.getList("boxes", Tag.TAG_COMPOUND);
		if(selected < 0 || selected >= boxes.size())
			return;
		CompoundTag box = boxes.getCompound(selected);
		click();
		send(FireAlarmControllerMenu.serviceButton(box.getInt("box"), !box.getBoolean("out")));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HfPanel.Control c = crtControl();
		int[] cell = crt.cellAt(mouseX, mouseY, leftPos + c.x() + 3, topPos + c.y() + 3, c.w() - 6, c.h() - 6);
		if(cell != null && button == 0) {
			int key = FireAlarmCrt.keyAt(cell[0], cell[1]);
			if(key >= 0) {
				softKey(key);
				return true;
			}
			if(view == FireAlarmCrt.View.MENU && cell[1] >= 2 && cell[1] <= 6) {
				menuItem(cell[1] - 1);
				return true;
			}
			if(view == FireAlarmCrt.View.BOXES && cell[1] >= 1 && cell[1] < FireAlarmCrt.KEY_ROW - 1) {
				int rows = FireAlarmCrt.KEY_ROW - 2;
				int first = Math.max(0, Math.min(selected - rows + 1, boxCount() - rows));
				int picked = first + cell[1] - 1;
				if(picked < boxCount()) {
					if(picked == selected)
						toggleService();
					selected = picked;
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_N -> softKey(0);
			case GLFW.GLFW_KEY_S -> softKey(1);
			case GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_HOME -> softKey(2);
			case GLFW.GLFW_KEY_E -> softKey(3);
			case GLFW.GLFW_KEY_DOWN -> softKey(view == FireAlarmCrt.View.BOXES ? 0 : 0);
			case GLFW.GLFW_KEY_UP -> {
				if(view == FireAlarmCrt.View.BOXES)
					softKey(2);
				else {
					click();
					event = Math.max(0, event - 1);
				}
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if(view == FireAlarmCrt.View.BOXES)
					toggleService();
			}
			default -> {
				if(view == FireAlarmCrt.View.MENU && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_5) {
					menuItem(key - GLFW.GLFW_KEY_0);
					return true;
				}
				return super.keyPressed(key, scanCode, modifiers);
			}
		}
		return true;
	}
}
