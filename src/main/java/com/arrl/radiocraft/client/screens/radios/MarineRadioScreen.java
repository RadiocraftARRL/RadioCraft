package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.blockentities.radio.MarineRadioBlockEntity;
import com.arrl.radiocraft.common.menus.MarineRadioMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.network.serverbound.SMarineRadioPacket;
import com.arrl.radiocraft.common.radio.marine.MarineRadioState;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The front panel of the fixed-mount marine radio: power, channel up/down, 16/9, HI/LO, DW, volume, the display and
 * lamps, and a PTT button (the hand mic, sneak and use the radio, is the usual way to talk). Plain fills, a
 * placeholder for the art.
 */
public class MarineRadioScreen extends AbstractContainerScreen<MarineRadioMenu> {

	private static final int CASE = 0xFFE8E8E4, LCD = 0xFFB8C9D8, LCD_OFF = 0xFF4A5560, LCD_INK = 0xFF101820, LED_OFF = 0xFF3A3A3A, LABEL = 0xFF303030;
	private boolean pttHeld = false;

	public MarineRadioScreen(MarineRadioMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 210;
		imageHeight = 110;
	}

	private MarineRadioBlockEntity radio() {
		return menu.blockEntity;
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = true;
		addRenderableWidget(Button.builder(Component.translatable(Radiocraft.translationKey("screen", "commercial.power")),
				b -> SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.TOGGLE_POWER, true)).bounds(leftPos + 8, topPos + 18, 40, 18).build());
		addRenderableWidget(Button.builder(Component.literal("CH -"), b -> press(MarineRadioState.Key.TURN, -1)).bounds(leftPos + 8, topPos + 40, 40, 18).build());
		addRenderableWidget(Button.builder(Component.literal("CH +"), b -> press(MarineRadioState.Key.TURN, 1)).bounds(leftPos + 162, topPos + 40, 40, 18).build());
		addRenderableWidget(Button.builder(Component.literal("16/9"), b -> press(MarineRadioState.Key.SIXTEEN_NINE, 0)).bounds(leftPos + 162, topPos + 18, 40, 18).build());
		addRenderableWidget(Button.builder(Component.literal("H/L"), b -> press(MarineRadioState.Key.HIGH_LOW, 0)).bounds(leftPos + 54, topPos + 66, 34, 18).build());
		addRenderableWidget(Button.builder(Component.literal("DW"), b -> press(MarineRadioState.Key.DUAL_WATCH, 0)).bounds(leftPos + 92, topPos + 66, 34, 18).build());
		addRenderableWidget(Button.builder(Component.literal("VOL -"), b -> volume(-10)).bounds(leftPos + 8, topPos + 86, 40, 18).build());
		addRenderableWidget(Button.builder(Component.literal("VOL +"), b -> volume(10)).bounds(leftPos + 54, topPos + 86, 40, 18).build());
	}

	private void press(MarineRadioState.Key key, int value) {
		SMarineRadioPacket.radio(radio().getBlockPos(), key, value);
	}

	private void volume(int percent) {
		int now = Math.round(radio().getSpeakerGain() * 100.0F);
		int next = Math.max(0, Math.min(200, now + percent));
		radio().setSpeakerGainClient(next / 100.0F);
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, next);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY != 0.0D) {
			press(MarineRadioState.Key.TURN, scrollY > 0.0D ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** The PTT button, bottom right. */
	private boolean overPtt(double x, double y) {
		return x >= leftPos + 132 && x < leftPos + 202 && y >= topPos + 66 && y < topPos + 104;
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
		graphics.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, 0xFF101010);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, CASE);
		boolean on = menu.isPowered() && radio().wasPowered();
		graphics.fill(leftPos + 54, topPos + 18, leftPos + 156, topPos + 60, on ? LCD : LCD_OFF);
		boolean tx = on && radio().isPTTDown() && !radio().isTransmitInhibited();
		boolean rx = on && !tx && radio().getSyncedSignal() > 0.0D;
		graphics.fill(leftPos + 100, topPos + 90, leftPos + 108, topPos + 98, tx ? 0xFFFF3B30 : LED_OFF);
		graphics.fill(leftPos + 116, topPos + 90, leftPos + 124, topPos + 98, rx ? 0xFF3BFF6A : LED_OFF);
		graphics.fill(leftPos + 132, topPos + 66, leftPos + 202, topPos + 104, pttHeld ? 0xFF5A2020 : 0xFF2E2E2E);
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, 8, 6, LABEL, false);
		boolean on = menu.isPowered() && radio().wasPowered();
		if(on) {
			graphics.pose().pushPose();
			graphics.pose().translate(105.0F, 22.0F, 0.0F);
			graphics.pose().scale(2.0F, 2.0F, 1.0F);
			String name = radio().displayLine();
			graphics.drawString(font, name, -font.width(name) / 2, 0, LCD_INK, false);
			graphics.pose().popPose();
			graphics.drawCenteredString(font, radio().displayStatus(), 105, 42, LCD_INK);
			graphics.drawString(font, String.format(Locale.ROOT, "%.3f", radio().getChannel().receiveHz() / 1e6), 58, 51, LCD_INK, false);
			if(radio().getRadioState().watchHz(MarineRadios.region()) > 0.0D)
				graphics.drawString(font, "16", 138, 51, LCD_INK, false); // Dual watch's priority channel.
		}
		graphics.drawString(font, "TX", 99, 101, LABEL, false);
		graphics.drawString(font, "RX", 115, 101, LABEL, false);
		graphics.drawString(font, String.format(Locale.ROOT, "VOL %d%%", Math.round(radio().getSpeakerGain() * 100.0F)), 8, 72, LABEL, false);
		graphics.drawCenteredString(font, "PTT", 167, 81, 0xFFE0E0E0);
	}
}
