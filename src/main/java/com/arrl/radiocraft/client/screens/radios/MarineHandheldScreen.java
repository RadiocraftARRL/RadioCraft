package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.capabilities.IVHFHandheldCapability;
import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.capabilities.RadiocraftCapabilities;
import com.arrl.radiocraft.common.items.MarineHandheldItem;
import com.arrl.radiocraft.common.network.serverbound.SHandheldRadioUpdatePacket;
import com.arrl.radiocraft.common.network.serverbound.SMarineRadioPacket;
import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.marine.MarineRadioState;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The front of the marine handheld (sneak and use it): the display with the channel, the channel keys, 16/9, HI/LO,
 * DW, power and volume, and a PTT bar. Drawn with plain fills as a placeholder until the art is done
 * (ModelDesignBounty). Holding the PTT bar (or right click with the radio in hand, as always) talks.
 */
public class MarineHandheldScreen extends Screen {

	private static final int WIDTH = 120, HEIGHT = 190;
	private static final int CASE = 0xFF20262E, LCD = 0xFFB8C9D8, LCD_OFF = 0xFF4A5560, LCD_INK = 0xFF101820, LED_OFF = 0xFF3A3A3A;

	private final int index;
	private int left, top;
	private boolean pttHeld = false;

	public MarineHandheldScreen(int index) {
		super(Component.translatable(Radiocraft.translationKey("screen", "marine_handheld.title")));
		this.index = index;
	}

	public static void open(int index) {
		Minecraft.getInstance().setScreen(new MarineHandheldScreen(index));
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
		addRenderableWidget(Button.builder(Component.literal("CH -"), b -> press(MarineRadioState.Key.TURN, -1)).bounds(left + 8, top + 64, 34, 18).build());
		addRenderableWidget(Button.builder(Component.literal("CH +"), b -> press(MarineRadioState.Key.TURN, 1)).bounds(left + 78, top + 64, 34, 18).build());
		addRenderableWidget(Button.builder(Component.literal("16/9"), b -> press(MarineRadioState.Key.SIXTEEN_NINE, 0)).bounds(left + 43, top + 64, 34, 18).build());
		addRenderableWidget(Button.builder(Component.literal("H/L"), b -> press(MarineRadioState.Key.HIGH_LOW, 0)).bounds(left + 8, top + 86, 34, 18).build());
		addRenderableWidget(Button.builder(Component.literal("DW"), b -> press(MarineRadioState.Key.DUAL_WATCH, 0)).bounds(left + 43, top + 86, 34, 18).build());
		addRenderableWidget(Button.builder(Component.translatable(Radiocraft.translationKey("screen", "commercial.power")), b -> togglePower())
				.bounds(left + 78, top + 86, 34, 18).build());
		addRenderableWidget(Button.builder(Component.literal("-"), b -> volume(-0.1F)).bounds(left + 62, top + 120, 22, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> volume(0.1F)).bounds(left + 88, top + 120, 22, 18).build());
	}

	/** The PTT bar at the bottom: held with the mouse. */
	private boolean overPtt(double x, double y) {
		return x >= left + 10 && x < left + WIDTH - 10 && y >= top + 150 && y < top + 178;
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
			press(MarineRadioState.Key.TURN, scrollY > 0.0D ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void press(MarineRadioState.Key key, int value) {
		ItemStack stack = stack();
		if(!MarineHandheldItem.is(stack))
			return;
		// At once on the client (so the display follows without a round trip); the server does the same.
		MarineHandheldItem.setState(stack, MarineHandheldItem.state(stack).press(MarineRadios.region(), key.ordinal(), value));
		SMarineRadioPacket.handheld(index, key, value);
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
		if(!MarineHandheldItem.is(stack()))
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
		if(cap == null || !MarineHandheldItem.is(stack))
			return;
		MarineChannels.Region region = MarineRadios.region();
		MarineRadioState state = MarineHandheldItem.state(stack);
		MarineChannels.Channel channel = state.channel(region);
		boolean on = cap.isPowered();

		// The display: the channel big, then power, dual watch and what the channel is for.
		graphics.fill(left + 10, top + 16, left + WIDTH - 10, top + 58, on ? LCD : LCD_OFF);
		if(on) {
			graphics.pose().pushPose();
			graphics.pose().translate(left + WIDTH / 2.0F, top + 20, 0.0F);
			graphics.pose().scale(2.0F, 2.0F, 1.0F);
			String name = state.displayLine(region);
			graphics.drawString(font, name, -font.width(name) / 2, 0, LCD_INK, false);
			graphics.pose().popPose();
			graphics.drawCenteredString(font, state.displayStatus(region), left + WIDTH / 2, top + 40, LCD_INK);
			String freq = String.format(Locale.ROOT, "%.3f", channel.receiveHz() / 1e6);
			graphics.drawString(font, freq, left + 14, top + 49, LCD_INK, false);
		}

		// TX and RX lamps.
		boolean tx = on && cap.isPTTDown() && MarineHandheldItem.mayTransmit(stack);
		boolean rx = on && !tx && cap.getReceiveStrength() > 0.0F;
		graphics.drawString(font, "TX", left + 14, top + 110, 0xFFC0C0C0, false);
		graphics.fill(left + 30, top + 110, left + 38, top + 118, tx ? 0xFFFF3B30 : LED_OFF);
		graphics.drawString(font, "RX", left + 14, top + 124, 0xFFC0C0C0, false);
		graphics.fill(left + 30, top + 124, left + 38, top + 132, rx ? 0xFF3BFF6A : LED_OFF);
		graphics.drawString(font, String.format(Locale.ROOT, "VOL %d%%", Math.round(cap.getGain() * 100.0F)), left + 62, top + 110, 0xFFC0C0C0, false);

		// The PTT bar.
		graphics.fill(left + 10, top + 150, left + WIDTH - 10, top + 178, pttHeld ? 0xFF5A2020 : 0xFF2E2E2E);
		graphics.drawCenteredString(font, "PTT", left + WIDTH / 2, top + 160, 0xFFE0E0E0);
	}

	@Override
	public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, 0xFF101010);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, CASE);
		graphics.fill(left, top + HEIGHT - 6, left + WIDTH, top + HEIGHT, 0xFFE8B020); // The float collar's yellow band.
		graphics.drawString(font, title, left + 6, top + 4, 0xFFE0E0E0, false);
	}
}
