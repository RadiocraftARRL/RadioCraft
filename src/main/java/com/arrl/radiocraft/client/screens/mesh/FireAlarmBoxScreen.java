package com.arrl.radiocraft.client.screens.mesh;

import com.arrl.radiocraft.client.screens.radios.HfPanel;
import com.arrl.radiocraft.client.screens.radios.MarinePanels;
import com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity;
import com.arrl.radiocraft.common.menus.FireAlarmBoxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The inside of a radio fire alarm box (2026-11-11), after an old Kidde panel: {@code gui_layouts/fire_alarm_box.json}
 * over {@code textures/gui/fire_alarm/fire_alarm_box.png} (both from {@code tools/fire_alarm_assets.py}). Lamps for
 * NORMAL (power), TROUBLE (no frequency, or a message waiting for the controller's ack), ALARM and zones 1 to 6; the
 * LINK meter (how well the box hears its nearest neighbour on the mesh); SILENCE (red: quiets the bells, the alarm
 * stays), RESET and TEST keys; the DRILL toggle; and the plate with the box number and frequency.
 */
public class FireAlarmBoxScreen extends AbstractContainerScreen<FireAlarmBoxMenu> {

	private final HfPanel panel = HfPanel.loadLayout("fire_alarm_box");
	@Nullable
	private String held;
	private double needle = 0.0D;

	public FireAlarmBoxScreen(FireAlarmBoxMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = panel.width();
		imageHeight = panel.height();
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		HfPanel.Control over = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		String tip = over == null ? null : tooltip(over.name());
		if(tip != null)
			g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(panel.texture(), leftPos, topPos, 0, 0, imageWidth, imageHeight, panel.width(), panel.height());
		needle += (menu.link() - needle) * 0.2D;
		boolean blink = (System.currentTimeMillis() / 500L) % 2L == 0L;
		for(HfPanel.Control c : panel.controls()) {
			String name = c.name();
			switch(c.type()) {
				case "lamp" -> {
					boolean lit = switch(name) {
						case "power_lamp" -> menu.flag(FireAlarmBoxBlockEntity.FLAG_POWER);
						case "trouble_lamp" -> menu.flag(FireAlarmBoxBlockEntity.FLAG_TROUBLE) && (blink || !menu.flag(FireAlarmBoxBlockEntity.FLAG_POWER));
						case "alarm_lamp" -> menu.flag(FireAlarmBoxBlockEntity.FLAG_ALARM) || menu.flag(FireAlarmBoxBlockEntity.FLAG_DRILL) && blink;
						default -> name.startsWith("zone_lamp_") && menu.zone(zoneOf(name));
					};
					if(lit)
						MarinePanels.lamp(g, leftPos, topPos, c, c.colour("colour", 0xFFFF3020));
				}
				case "switch" -> MarinePanels.switchHandle(g, leftPos, topPos, c, switch(name) {
					case "drill" -> menu.flag(FireAlarmBoxBlockEntity.FLAG_DRILL) ? "DRILL" : "OFF";
					default -> "";
				});
				case "button" -> MarinePanels.button(g, leftPos, topPos, c, false, name.equals(held));
				case "meter" -> MarinePanels.meter(g, leftPos, topPos, c, needle);
				case "display" -> {
					String mhz = menu.mhz();
					String text = mhz.isEmpty() ? "NO FREQUENCY" : String.format(Locale.ROOT, "BOX %04d  %s", menu.box(), mhz);
					MarinePanels.centred(g, font, leftPos, topPos, c, text, 1.0F, c.colour("ink", 0xFF202020));
				}
				default -> {}
			}
		}
	}

	private static int zoneOf(String name) {
		try {
			return Integer.parseInt(name.substring("zone_lamp_".length()));
		}
		catch(NumberFormatException e) {
			return 0;
		}
	}

	@Nullable
	private String tooltip(String name) {
		return switch(name) {
			case "test" -> "TEST: send a test to the controller";
			case "reset" -> "RESET: put every pulled station on this box back up";
			case "silence" -> menu.flag(FireAlarmBoxBlockEntity.FLAG_SILENCE) ? "SILENCE: bells silenced; still in alarm until RESET" : "SILENCE: quiet this box's bells (redstone out of its back); it stays in alarm until RESET";
			case "drill" -> "DRILL: ring the bells for a fire drill; nothing is sent";
			case "meter" -> String.format(Locale.ROOT, "LINK %d%%: how well the nearest box or controller on this frequency hears it", Math.round(menu.link() * 100.0D));
			case "power_lamp" -> menu.flag(FireAlarmBoxBlockEntity.FLAG_POWER) ? "NORMAL: on its frequency" : "No frequency: use a commercial license on the box";
			case "trouble_lamp" -> "TROUBLE: no frequency, or waiting for the controller to acknowledge";
			case "alarm_lamp" -> "ALARM: a zone is in alarm";
			default -> name.startsWith("zone_lamp_") ? "Zone " + zoneOf(name) + (menu.zone(zoneOf(name)) ? ": IN ALARM" : ": normal") : null;
		};
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HfPanel.Control c = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		if(c != null && button == 0) {
			int id = switch(c.name()) {
				case "test" -> FireAlarmBoxMenu.BUTTON_TEST;
				case "reset" -> FireAlarmBoxMenu.BUTTON_RESET;
				case "silence" -> FireAlarmBoxMenu.BUTTON_SILENCE;
				case "drill" -> FireAlarmBoxMenu.BUTTON_DRILL;
				default -> -1;
			};
			if(id >= 0) {
				if(c.type().equals("button"))
					held = c.name();
				Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(c.type().equals("switch") ? SoundEvents.LEVER_CLICK : SoundEvents.STONE_BUTTON_CLICK_ON, 1.2F, 0.4F));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		held = null;
		return super.mouseReleased(mouseX, mouseY, button);
	}
}
