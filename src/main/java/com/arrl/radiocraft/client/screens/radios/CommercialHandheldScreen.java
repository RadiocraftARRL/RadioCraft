package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.capabilities.IVHFHandheldCapability;
import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.capabilities.RadiocraftCapabilities;
import com.arrl.radiocraft.common.items.CommercialHandheldItem;
import com.arrl.radiocraft.common.network.serverbound.SCommercialRadioPacket;
import com.arrl.radiocraft.common.network.serverbound.SHandheldRadioUpdatePacket;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The top of a commercial handheld (sneak and use it): the 16-position channel knob, the power/volume knob, the RX and
 * TX LEDs and, on the upgraded model, its zone switch (A = channels 1-16, B = 17-32, C = 33-40, as 1990s fire radios
 * reached 40 channels with a 16-position knob) and its little display with the channel name, zone, channel and mode.
 * There is nothing to program here: a radio shop does that. Drawn with plain fills as a placeholder until the art is done (ModelDesignBounty).
 * Holding the PTT button (or right click with the radio in hand, as always) talks.
 */
public class CommercialHandheldScreen extends Screen {

	private static final int WIDTH = 120, HEIGHT = 176;
	private static final int LCD = 0xFF9DB87A, LCD_INK = 0xFF1F2A14, LED_OFF = 0xFF3A3A3A, RX_ON = 0xFF3BFF6A, TX_ON = 0xFFFF3B30;

	private final int index;
	private int left, top;
	private boolean pttHeld = false;
	private Button zoneButton;

	public CommercialHandheldScreen(int index) {
		super(Component.translatable(Radiocraft.translationKey("screen", "commercial_handheld.title")));
		this.index = index;
	}

	public static void open(int index) {
		Minecraft.getInstance().setScreen(new CommercialHandheldScreen(index));
	}

	private ItemStack stack() {
		return Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getInventory().getItem(index);
	}

	private IVHFHandheldCapability cap() {
		return stack().getCapability(RadiocraftCapabilities.VHF_HANDHELDS);
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = true;
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(left + 10, top + 30, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(left + 90, top + 30, 20, 20).build());
		CommercialRadioModel model = CommercialHandheldItem.modelOf(stack());
		if(model != null && model.hasZones())
			zoneButton = addRenderableWidget(Button.builder(zoneLabel(), b -> nextZone()).bounds(left + 40, top + 58, 40, 16).build());
		addRenderableWidget(Button.builder(Component.translatable(Radiocraft.translationKey("screen", "commercial.power")), b -> togglePower())
				.bounds(left + 10, top + 120, 46, 18).build());
		addRenderableWidget(Button.builder(Component.literal("-"), b -> volume(-0.1F)).bounds(left + 62, top + 120, 22, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> volume(0.1F)).bounds(left + 88, top + 120, 22, 18).build());
	}

	/** The PTT bar on the side: held with the mouse. */
	private boolean overPtt(double x, double y) {
		return x >= left + 10 && x < left + WIDTH - 10 && y >= top + 144 && y < top + 166;
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

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY != 0.0D) {
			turn(scrollY > 0.0D ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void turn(int steps) {
		ItemStack stack = stack();
		if(!CommercialHandheldItem.is(stack))
			return;
		CommercialProgram turned = CommercialHandheldItem.program(stack).turn(steps);
		CommercialHandheldItem.setProgram(stack, turned); // At once on the client; the server does the same.
		SCommercialRadioPacket.handheld(index, turned.selected());
		click();
	}

	/** The zone switch's label: "Zone A". */
	private Component zoneLabel() {
		return Component.translatable(Radiocraft.translationKey("screen", "commercial.zone"),
				String.valueOf(CommercialProgram.zoneLetter(CommercialHandheldItem.program(stack()).zone())));
	}

	/** The zone switch one click on, A, B, C and back to A. */
	private void nextZone() {
		ItemStack stack = stack();
		CommercialRadioModel model = CommercialHandheldItem.modelOf(stack);
		if(model == null || !model.hasZones())
			return;
		CommercialProgram program = CommercialHandheldItem.program(stack);
		CommercialProgram switched = program.zone((program.zone() + 1) % model.zones(), model.zones());
		CommercialHandheldItem.setProgram(stack, switched);
		SCommercialRadioPacket.handheld(index, switched.selected());
		click();
	}

	private void togglePower() {
		IVHFHandheldCapability cap = cap();
		if(cap == null)
			return;
		cap.setPowered(!cap.isPowered());
		SHandheldRadioUpdatePacket.updateServer(index, cap);
		click();
	}

	private void volume(float step) {
		IVHFHandheldCapability cap = cap();
		if(cap == null)
			return;
		cap.setGain(Math.max(0.0F, Math.min(1.5F, Math.round((cap.getGain() + step) * 10.0F) / 10.0F)));
		SHandheldRadioUpdatePacket.updateServer(index, cap);
	}

	private void setPtt(boolean down) {
		IVHFHandheldCapability cap = cap();
		if(cap == null)
			return;
		pttHeld = down;
		cap.setPTTDown(down);
		RadiocraftClientValues.SCREEN_PTT_PRESSED = down;
		SHandheldRadioUpdatePacket.updateServer(index, cap);
	}

	private static void click() {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player != null)
			mc.player.playSound(net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON, 0.3F, 1.8F);
	}

	@Override
	public void tick() {
		super.tick();
		if(zoneButton != null)
			zoneButton.setMessage(zoneLabel());
		if(!CommercialHandheldItem.is(stack()))
			onClose(); // The radio left that slot.
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
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		ItemStack stack = stack();
		IVHFHandheldCapability cap = cap();
		CommercialRadioModel model = CommercialHandheldItem.modelOf(stack);
		if(cap == null || model == null)
			return;
		CommercialProgram program = CommercialHandheldItem.program(stack);
		CommercialChannel channel = program.active();
		boolean on = cap.isPowered();

		// The knob: its position, 1 to 16 (in the zone the zone switch is on).
		graphics.fill(left + 36, top + 24, left + 84, top + 56, 0xFF1A1A1A);
		String position = String.valueOf(program.knob() + 1);
		graphics.drawCenteredString(font, position, left + 60, top + 36, 0xFFE0E0E0);
		graphics.drawCenteredString(font, Component.translatable(Radiocraft.translationKey("screen", "commercial.channel_knob")), left + 60, top + 14, 0xFFC0C0C0);

		// The LEDs.
		boolean tx = on && cap.isPTTDown() && CommercialHandheldItem.mayTransmit(stack);
		boolean rx = on && !tx && cap.getReceiveStrength() > 0.0F;
		graphics.drawString(font, "RX", left + 14, top + 62, 0xFFC0C0C0, false);
		graphics.fill(left + 30, top + 62, left + 38, top + 70, rx ? RX_ON : LED_OFF);
		graphics.drawString(font, "TX", left + 82, top + 62, 0xFFC0C0C0, false);
		graphics.fill(left + 98, top + 62, left + 106, top + 70, tx ? TX_ON : LED_OFF);

		// The display (upgraded model only): the channel name (the caller's alias or unit ID while one talks), its number and mode.
		if(model.hasDisplay()) {
			graphics.fill(left + 14, top + 76, left + WIDTH - 14, top + 108, on ? LCD : 0xFF4A5240);
			if(on) {
				String name = channel == null ? "-- NO CH --" : channel.name().isEmpty() ? String.format(Locale.ROOT, "%.4f", channel.rxHz() / 1e6) : channel.name();
				String caller = tx ? null : com.arrl.radiocraft.client.CallerIds.callerText(program, channel); // Who is talking: alias or unit ID.
				if(caller != null)
					name = caller;
				graphics.drawCenteredString(font, name, left + WIDTH / 2, top + 81, LCD_INK);
				String mode = channel == null ? "" : channel.encrypted() ? "ENC" : channel.digital() ? "DIG" : "FM";
				String where = model.hasZones() ? "Z" + CommercialProgram.zoneLetter(program.zone()) + " CH" + (program.selected() + 1) : "CH" + (program.selected() + 1);
				graphics.drawString(font, where, left + 18, top + 95, LCD_INK, false);
				graphics.drawString(font, mode, left + WIDTH - 18 - font.width(mode), top + 95, LCD_INK, false);
			}
		}
		else
			graphics.drawCenteredString(font, on ? (channel == null ? "-" : "") : "", left + WIDTH / 2, top + 88, 0xFF808080);

		graphics.drawString(font, String.format(Locale.ROOT, "VOL %d%%", Math.round(cap.getGain() * 100.0F)), left + 14, top + 110, 0xFFC0C0C0, false);

		// The PTT bar.
		graphics.fill(left + 10, top + 144, left + WIDTH - 10, top + 166, pttHeld ? 0xFF5A2020 : 0xFF2E2E2E);
		graphics.drawCenteredString(font, "PTT", left + WIDTH / 2, top + 151, 0xFFE0E0E0);
	}

	@Override
	public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		int colour = DyedItemColor.getOrDefault(stack(), CommercialHandheldItem.DEFAULT_CASE_COLOUR);
		graphics.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, 0xFF101010);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF000000 | colour);
		graphics.drawString(font, title, left + 6, top + 3, 0xFFE0E0E0, false);
	}
}
