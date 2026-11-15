package com.arrl.radiocraft.client.screens.mesh;

import com.arrl.radiocraft.common.menus.MeshSetupMenu;
import com.arrl.radiocraft.common.network.serverbound.SMeshSetupPacket;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.scada.Scada;
import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * A fire alarm or SCADA device's setup (2026-11-11): its frequency (commercial VHF 150-174 MHz or low band 30-50 MHz,
 * outside the marine channels) and the callsign it goes out under, typed in, no license needed; its location name;
 * and on an RTU its six faces, each clicked through OFF, IN (it reads the redstone there and reports it) and OUT (it
 * gives off what the master's rules say). A license used on the device still fills both in.
 */
public class MeshSetupScreen extends AbstractContainerScreen<MeshSetupMenu> {

	private static final int BG = 0xF0202622, EDGE = 0xFF5CFF7A, TEXT = 0xFFCFE8D2, DIM = 0xFF7F9A84;
	private EditBox frequency, callsign, label;
	private final Button[] faces = new Button[6];

	public MeshSetupScreen(MeshSetupMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 232;
		imageHeight = menu.hasFaces() ? 196 : 136;
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos + 92, w = imageWidth - 100;
		frequency = field(x, topPos + 26, w, menu.hz > 0 ? CommercialLicenseRules.mhz(menu.hz) : "", 10);
		frequency.setFilter(s -> s.matches("[0-9.]*"));
		callsign = field(x, topPos + 46, w, menu.callsign, 10);
		callsign.setFilter(s -> s.matches("[A-Za-z0-9]*"));
		label = field(x, topPos + 66, w, menu.label, 32);
		setInitialFocus(frequency);
		addRenderableWidget(Button.builder(Component.literal("SET"), b -> set()).bounds(leftPos + imageWidth - 68, topPos + 92, 60, 18).build());
		if(menu.hasFaces())
			for(int i = 0; i < 6; i++) {
				int face = i;
				faces[i] = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(face))
						.bounds(leftPos + 8 + (i % 3) * 72, topPos + 140 + (i / 3) * 22, 70, 18).build());
			}
	}

	private EditBox field(int x, int y, int w, String value, int max) {
		EditBox box = new EditBox(font, x, y, w, 16, Component.empty());
		box.setMaxLength(max);
		box.setValue(value);
		return addRenderableWidget(box);
	}

	private void set() {
		float hz;
		try {
			hz = (float)(Double.parseDouble(frequency.getValue().trim()) * 1_000_000.0D);
		}
		catch(NumberFormatException e) {
			hz = 0.0F;
		}
		SMeshSetupPacket.send(menu.pos, hz, callsign.getValue().trim().toUpperCase(Locale.ROOT), label.getValue().trim());
		onClose();
	}

	private void cycle(int face) {
		Scada.FaceMode next = menu.faceMode(face).next();
		Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, MeshSetupMenu.faceButton(face, next));
	}

	private static String faceName(int face) {
		return switch(ScadaRules.FACES.charAt(face)) {
			case 'D' -> "BOTTOM";
			case 'U' -> "TOP";
			case 'N' -> "NORTH";
			case 'S' -> "SOUTH";
			case 'W' -> "WEST";
			default -> "EAST";
		};
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if(menu.hasFaces())
			for(int i = 0; i < 6; i++) {
				Scada.FaceMode mode = menu.faceMode(i);
				String level = mode == Scada.FaceMode.OFF ? "" : " " + menu.faceLevel(i);
				faces[i].setMessage(Component.literal(faceName(i) + ": " + mode.name() + level));
			}
		super.render(g, mouseX, mouseY, partialTick);
		if(menu.hasFaces())
			for(int i = 0; i < 6; i++)
				if(faces[i].isHovered())
					g.renderTooltip(font, Component.literal("Click: OFF > IN > OUT. IN reads the redstone on this side and reports it; OUT gives off what the master's rules set it to."),
							mouseX, mouseY);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG);
		g.renderOutline(leftPos, topPos, imageWidth, imageHeight, EDGE);
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title.getString().toUpperCase(Locale.ROOT) + " SETUP", 8, 8, EDGE, false);
		g.drawString(font, "Frequency MHz", 8, 30, TEXT, false);
		g.drawString(font, "Callsign", 8, 50, TEXT, false);
		g.drawString(font, "Location name", 8, 70, TEXT, false);
		String role = switch(menu.role) {
			case BOX -> menu.hz > 0 ? String.format(Locale.ROOT, "Box %04d", menu.address) : "Box: no number yet";
			case RTU -> menu.hz > 0 ? "RTU " + menu.address : "RTU: no id yet";
			case CONTROLLER -> "Controller";
			case MASTER -> "Master (unit 0)";
		};
		g.drawString(font, role, 8, 92, TEXT, false);
		g.drawString(font, menu.external ? "Antenna on the coax" : "Built-in antenna", 8, 104, DIM, false);
		g.drawString(font, "VHF 150-174 or 30-50 MHz", 8, 120, DIM, false);
		if(menu.hasFaces())
			g.drawString(font, "Faces (redstone):", 8, 130, TEXT, false);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			set();
			return true;
		}
		if(key == GLFW.GLFW_KEY_ESCAPE)
			return super.keyPressed(key, scanCode, modifiers);
		if(getFocused() instanceof EditBox box && box.isFocused()) { // Typing: never the inventory key or hotbar.
			box.keyPressed(key, scanCode, modifiers);
			return true;
		}
		return super.keyPressed(key, scanCode, modifiers);
	}
}
