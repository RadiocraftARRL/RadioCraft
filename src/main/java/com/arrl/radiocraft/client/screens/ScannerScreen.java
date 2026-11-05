package com.arrl.radiocraft.client.screens;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.menus.ScannerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The scanner with its lid up (1.12 step 5): the glass, the paper tray and the output tray, drawn with plain fills
 * (a placeholder until it has art, see doc/ModelDesignBounty.md) over the vanilla inventory panel.
 */
public final class ScannerScreen extends AbstractContainerScreen<ScannerMenu> {

	private static final ResourceLocation INVENTORY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int PANEL_HEIGHT = 78;
	private static final int CASE = 0xFFD8D0B8, CASE_DARK = 0xFF9A927C, GLASS = 0xFF2A3A44, SLOT = 0xFF8B8B8B, SLOT_DARK = 0xFF373737,
			SLOT_LIGHT = 0xFFFFFFFF, TEXT = 0xFF404040;

	public ScannerScreen(ScannerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 176;
		imageHeight = PANEL_HEIGHT - 1 + 96; // The inventory's slots line up with ScannerMenu.INVENTORY_Y.
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		renderTooltip(g, mouseX, mouseY);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos, y = topPos;
		g.fill(x, y, x + imageWidth, y + PANEL_HEIGHT, CASE_DARK);
		g.fill(x + 1, y + 1, x + imageWidth - 1, y + PANEL_HEIGHT - 1, CASE);
		g.fill(x + 24, y + 14, x + 64, y + 62, GLASS); // The glass the bed slot sits on.
		slot(g, ScannerMenu.BED_AT);
		slot(g, ScannerMenu.TRAY_AT);
		slot(g, ScannerMenu.OUTPUT_AT);
		g.blit(INVENTORY_TEXTURE, x, y + PANEL_HEIGHT - 1, 0, 126, 176, 96);
	}

	private void slot(GuiGraphics g, int[] at) {
		int x = leftPos + at[0] - 1, y = topPos + at[1] - 1;
		g.fill(x, y, x + 18, y + 18, SLOT_DARK);
		g.fill(x + 1, y + 1, x + 18, y + 18, SLOT_LIGHT);
		g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
		g.drawString(font, Component.translatable(Radiocraft.translationKey("screen", "scanner.glass")), ScannerMenu.BED_AT[0] + 22, ScannerMenu.BED_AT[1] + 4, TEXT, false);
		g.drawString(font, Component.translatable(Radiocraft.translationKey("screen", "scanner.paper")), ScannerMenu.TRAY_AT[0] + 22, ScannerMenu.TRAY_AT[1] + 4, TEXT, false);
		g.drawString(font, Component.translatable(Radiocraft.translationKey("screen", "scanner.output")), ScannerMenu.OUTPUT_AT[0] - 6, ScannerMenu.OUTPUT_AT[1] - 10, TEXT, false);
		g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
	}
}
