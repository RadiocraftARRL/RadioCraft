package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.screens.widgets.*;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity;
import com.arrl.radiocraft.common.menus.VHFReceiverMenu;
import com.arrl.radiocraft.common.network.serverbound.SVHFReceiverPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The VHF receiver's panel (2026-11-09): power, tuning knob, a display between the knobs, volume knob, mute, and six
 * memory buttons with a lamp over each. A memory button with nothing in it stores the frequency shown; one with a
 * frequency selects it (pressed in); two or more pressed in scan. Shift + click stores over a channel. Every control
 * says what it does on hover.
 */
public class VHFReceiverScreen extends VHFRadioScreen<VHFReceiverMenu> {

	/** The display window drawn between the two knobs (texture coordinates). */
	private static final int LCD_X = 64, LCD_Y = 13, LCD_W = 120, LCD_H = 29;
	private static final int LCD_BACK = 0xFF16261A, LCD_INK = 0x9CFF9C, LCD_DIM = 0x4E7F4E, LCD_WARN = 0xFF7755;
	private static final int[] MEMORY_X = { 42, 72, 101, 131, 160, 190 };

	private final LedIndicator[] leds = new LedIndicator[VHFReceiverBlockEntity.MEMORIES];
	private final ToggleButton[] memoryButtons = new ToggleButton[VHFReceiverBlockEntity.MEMORIES];
	private ToggleButton powerButton, muteButton;
	private Dial tuningDial;

	public VHFReceiverScreen(VHFReceiverMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, Radiocraft.id("textures/gui/vhf_receiver.png"), Radiocraft.id("textures/gui/vhf_receiver_widgets.png"));

		this.imageWidth = 251;
		this.imageHeight = 106;
	}

	private VHFReceiverBlockEntity receiver() {
		return menu.blockEntity;
	}

	@Override
	protected void init() {
		super.init();
		powerButton = addRenderableWidget(new ToggleButton(menu.isPowered(), leftPos + 10, topPos + 11, 14, 19, 0, 0, widgetsTexture, 256, 256, this::onPressPower).withPowerClick());
		muteButton = addRenderableWidget(new ToggleButton(receiver().isMuted(), leftPos + 220, topPos + 9, 22, 22, 0, 67, widgetsTexture, 256, 256, this::onPressMute));

		for(int i = 0; i < leds.length; i++) { // Lamps: lit while a signal comes in on that channel. Not clickable.
			leds[i] = new LedIndicator(Component.literal("LED " + (i + 1)), leftPos + MEMORY_X[i] + 1, topPos + 50, 17, 17, 0, 106, texture, 256, 256);
			leds[i].setIsOn(false);
			addRenderableWidget(leds[i]);
		}
		for(int i = 0; i < memoryButtons.length; i++) {
			int channel = i;
			memoryButtons[i] = addRenderableWidget(new ToggleButton(receiver().isSelected(i), leftPos + MEMORY_X[i], topPos + 73, 19, 19, 93, 38 * i, widgetsTexture, 256, 256, btn -> onMemoryButton(channel)));
		}

		tuningDial = addRenderableWidget(new Dial(leftPos + 30, topPos + 12, 28, 31, 28, 0, widgetsTexture, 256, 256, this::onFrequencyDialUp, this::onFrequencyDialDown));
		speakerGainDial = addRenderableWidget(new Dial(leftPos + 188, topPos + 12, 28, 31, 28, 0, widgetsTexture, 256, 256, this::onGainUp, this::onGainDown)); // Volume
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {

	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
		// Don't render the default title and inventory labels
	}

	/** The buttons and lamps follow the receiver (the server may change them: a scan, the dial leaving the channels). */
	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		VHFReceiverBlockEntity rx = receiver();
		boolean on = menu.getDisplayPhase() == RadioNetworkObject.PHASE_ON;
		for(int i = 0; i < memoryButtons.length; i++) {
			memoryButtons[i].isToggled = rx.isSelected(i);
			leds[i].setIsOn(on && rx.getLitChannel() == i);
		}
		muteButton.isToggled = rx.isMuted();
		powerButton.isToggled = menu.isPowered();
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	protected void renderAdditionalBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(leftPos + LCD_X, topPos + LCD_Y, leftPos + LCD_X + LCD_W, topPos + LCD_Y + LCD_H, LCD_BACK);
	}

	@Override
	protected void renderAdditionalTooltips(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderLcd(graphics);
		Component tip = tooltipAt(mouseX, mouseY);
		if(tip != null)
			graphics.renderTooltip(font, font.split(tip, 200), mouseX, mouseY);
	}

	/** The display: frequency, what it listens to (dial, a channel, scan), mute and the cell. Dark while off. */
	private void renderLcd(GuiGraphics graphics) {
		int phase = menu.getDisplayPhase();
		float cx = leftPos + LCD_X + LCD_W / 2.0F, cy = topPos + LCD_Y + LCD_H / 2.0F;
		if(phase == RadioNetworkObject.PHASE_OFF)
			return;
		if(phase == RadioNetworkObject.PHASE_PWR) { // Switched on but starved: a flat cell, or not enough on the wire.
			drawScaled(graphics, "PWR", cx, cy, 1.0F, LCD_WARN);
			return;
		}
		VHFReceiverBlockEntity rx = receiver();
		drawScaled(graphics, String.format(Locale.ROOT, "%.3f MHz", menu.getFrequency() / 1_000_000.0F), cx, cy, 1.0F, LCD_INK);
		float small = 0.6F, top = topPos + LCD_Y + 4.0F, bottom = topPos + LCD_Y + LCD_H - 4.0F;
		int left = leftPos + LCD_X + 3, right = leftPos + LCD_X + LCD_W - 3;
		drawLeft(graphics, "FM", left, top, small, LCD_INK);
		String mode;
		if(rx.isScanning())
			mode = rx.getLitChannel() >= 0 ? "SCAN " + (rx.getLitChannel() + 1) : "SCAN";
		else if(rx.selectedCount() == 1)
			mode = "MEM " + (Integer.numberOfTrailingZeros(selectedMask(rx)) + 1);
		else
			mode = "DIAL";
		drawRight(graphics, mode, right, top, small, LCD_INK);
		if(rx.isMuted())
			drawLeft(graphics, "MUTE", left, bottom, small, LCD_WARN);
		if(rx.hasCell()) {
			int percent = rx.getCellPercent();
			drawRight(graphics, "CELL " + percent + "%", right, bottom, small, percent < 10 ? LCD_WARN : LCD_DIM);
		}
	}

	private static int selectedMask(VHFReceiverBlockEntity rx) {
		int mask = 0;
		for(int i = 0; i < VHFReceiverBlockEntity.MEMORIES; i++)
			if(rx.isSelected(i))
				mask |= 1 << i;
		return mask;
	}

	private void drawLeft(GuiGraphics graphics, String text, float x, float cy, float scale, int colour) {
		drawScaled(graphics, text, x + font.width(text) * scale / 2.0F, cy, scale, colour);
	}

	private void drawRight(GuiGraphics graphics, String text, float x, float cy, float scale, int colour) {
		drawScaled(graphics, text, x - font.width(text) * scale / 2.0F, cy, scale, colour);
	}

	/** What the control under the mouse does, or null. */
	private Component tooltipAt(int mouseX, int mouseY) {
		VHFReceiverBlockEntity rx = receiver();
		if(over(powerButton, mouseX, mouseY))
			return tip("power");
		if(over(tuningDial, mouseX, mouseY))
			return tip("tuning");
		if(over(speakerGainDial, mouseX, mouseY))
			return tip("volume");
		if(over(muteButton, mouseX, mouseY))
			return tip(rx.isMuted() ? "muted" : "mute");
		for(int i = 0; i < memoryButtons.length; i++) {
			float hz = rx.getMemory(i);
			if(over(memoryButtons[i], mouseX, mouseY))
				return hz == 0.0F ? Component.translatable(key("memory_empty"), i + 1)
						: Component.translatable(key("memory"), i + 1, String.format(Locale.ROOT, "%.3f", hz / 1_000_000.0F));
			if(over(leds[i], mouseX, mouseY))
				return Component.translatable(key("lamp"), i + 1);
		}
		if(isHovering(LCD_X, LCD_Y, LCD_W, LCD_H, mouseX, mouseY))
			return rx.hasCell() ? Component.translatable(key("display_cell"), rx.getCellPercent()) : tip("display");
		return null;
	}

	private static boolean over(AbstractWidget widget, int mouseX, int mouseY) {
		return widget != null && widget.isMouseOver(mouseX, mouseY);
	}

	private static String key(String name) {
		return Radiocraft.translationKey("screen", "vhf_receiver." + name);
	}

	private static Component tip(String name) {
		return Component.translatable(key(name));
	}

	/** The speaker button: pressed in, the receiver is muted. */
	private void onPressMute(ToggleButton button) {
		boolean mute = !receiver().isMuted();
		receiver().setMutedClient(mute);
		SVHFReceiverPacket.send(receiver().getBlockPos(), SVHFReceiverPacket.SET_MUTED, mute ? 1 : 0);
	}

	/** A memory button: shift + click stores the frequency shown; a click selects, lets go, or stores into an empty one. */
	protected void onMemoryButton(int channel) {
		if(!menu.isPowered())
			return; // Like the dial, the memories need it switched on.
		SVHFReceiverPacket.send(receiver().getBlockPos(), Screen.hasShiftDown() ? SVHFReceiverPacket.STORE_MEMORY : SVHFReceiverPacket.PRESS_MEMORY, channel);
	}

}
