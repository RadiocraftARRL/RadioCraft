package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.menus.CommercialMobileMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.network.serverbound.SCommercialRadioPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The front panel of a commercial mobile: power, the channel knob, volume, the channel display and lamps, and a PTT
 * button (the hand mic, sneak and use the radio, is the usual way to talk). Plain fills, a placeholder for the art.
 */
public class CommercialMobileScreen extends AbstractContainerScreen<CommercialMobileMenu> {

	private static final int LCD = 0xFF9DB87A, LCD_INK = 0xFF1F2A14, LED_OFF = 0xFF3A3A3A;
	private boolean pttHeld = false;

	public CommercialMobileScreen(CommercialMobileMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 200;
		imageHeight = 100;
	}

	private CommercialMobileBlockEntity radio() {
		return menu.blockEntity;
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = true;
		addRenderableWidget(Button.builder(Component.translatable(Radiocraft.translationKey("screen", "commercial.power")),
				b -> SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.TOGGLE_POWER, true)).bounds(leftPos + 8, topPos + 18, 40, 18).build());
		addRenderableWidget(Button.builder(Component.literal("CH -"), b -> turn(-1)).bounds(leftPos + 8, topPos + 42, 40, 18).build());
		addRenderableWidget(Button.builder(Component.literal("CH +"), b -> turn(1)).bounds(leftPos + 152, topPos + 42, 40, 18).build());
		addRenderableWidget(Button.builder(Component.literal("VOL -"), b -> volume(-10)).bounds(leftPos + 8, topPos + 70, 40, 18).build());
		addRenderableWidget(Button.builder(Component.literal("VOL +"), b -> volume(10)).bounds(leftPos + 52, topPos + 70, 40, 18).build());
	}

	private void turn(int steps) {
		int position = radio().program().turn(steps).selected();
		SCommercialRadioPacket.mobile(radio().getBlockPos(), position);
	}

	private void volume(int percent) {
		int now = Math.round(radio().getSpeakerGain() * 100.0F);
		int next = Math.max(0, Math.min(200, now + percent));
		radio().setSpeakerGainClient(next / 100.0F);
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, next);
	}

	/** The PTT button, bottom right. */
	private boolean overPtt(double x, double y) {
		return x >= leftPos + 112 && x < leftPos + 192 && y >= topPos + 68 && y < topPos + 92;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(button == 0 && overPtt(mouseX, mouseY)) {
			setPtt(true);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(button == 0 && pttHeld)
			setPtt(false);
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void setPtt(boolean down) {
		pttHeld = down;
		RadiocraftClientValues.SCREEN_PTT_PRESSED = down;
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_PTT, down);
	}

	@Override
	public void onClose() {
		if(pttHeld)
			setPtt(false);
		RadiocraftClientValues.SCREEN_PTT_PRESSED = false;
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = false;
		super.onClose();
	}

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int colour = 0xFF000000 | radio().getCaseColour();
		graphics.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, 0xFF101010);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, colour);
		boolean on = menu.isPowered() && radio().wasPowered();
		graphics.fill(leftPos + 54, topPos + 18, leftPos + 146, topPos + 62, on ? LCD : 0xFF4A5240);
		boolean tx = on && radio().isPTTDown() && !radio().isTransmitInhibited();
		boolean rx = on && !tx && radio().getSyncedSignal() > 0.0D;
		graphics.fill(leftPos + 152, topPos + 20, leftPos + 160, topPos + 28, tx ? 0xFFFF3B30 : LED_OFF);
		graphics.fill(leftPos + 152, topPos + 30, leftPos + 160, topPos + 38, rx ? 0xFF3BFF6A : LED_OFF);
		graphics.fill(leftPos + 112, topPos + 68, leftPos + 192, topPos + 92, pttHeld ? 0xFF5A2020 : 0xFF2E2E2E);
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, 8, 6, 0xFFE0E0E0, false);
		boolean on = menu.isPowered() && radio().wasPowered();
		if(on) {
			graphics.drawCenteredString(font, radio().displayLine(), 100, 26, LCD_INK);
			graphics.drawCenteredString(font, radio().displayStatus(), 100, 42, LCD_INK);
		}
		graphics.drawString(font, "TX", 164, 20, 0xFFC0C0C0, false);
		graphics.drawString(font, "RX", 164, 30, 0xFFC0C0C0, false);
		graphics.drawString(font, String.format(Locale.ROOT, "VOL %d%%", Math.round(radio().getSpeakerGain() * 100.0F)), 54, 92 - 2, 0xFFC0C0C0, false);
		graphics.drawCenteredString(font, "PTT", 152, 76, 0xFFE0E0E0);
	}
}
