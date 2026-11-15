package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.common.blockentities.radio.AirbandRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastReceiverBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastTransmitterBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.menus.SimpleRadioMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.client.RadiocraftClientValues;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The front panels of the radios that have no GUI art (2026-11-13), drawn in code: the airband radio (USE and STANDBY
 * windows, tuning knobs as buttons, the flip-flop), the FM broadcast transmitter (its channel and a held ON AIR key)
 * and the FM broadcast receiver (the dial, tuning and volume). Mouse wheel over the panel tunes, as the knobs do.
 */
public class SimpleRadioScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<SimpleRadioMenu> {

	private static final int AMBER = 0xFFFFA020, AMBER_DIM = 0xFF3A1E08, PLATE = 0xFF26262A, BEVEL = 0xFF46464C, PRINT = 0xFFC8C8C0;
	private static final int BLUE = 0xFF1E4A78, SILVER = 0xFFB4B8BC, WOOD = 0xFF70401F, DIAL = 0xFF0C0C0E, ORANGE = 0xFFE07828;

	private final RadioBlockEntity radio;

	public SimpleRadioScreen(SimpleRadioMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.radio = menu.blockEntity;
		imageWidth = 236;
		imageHeight = radio instanceof AirbandRadioBlockEntity ? 92 : 100;
	}

	private void send(SBlockRadioControlPacket.Action action, int step, boolean value) {
		SBlockRadioControlPacket.updateServer(radio.getBlockPos(), action, step, value);
	}

	private void step(int steps) {
		if(!menu.isPowered())
			return;
		send(SBlockRadioControlPacket.Action.STEP_FREQUENCY, steps, false);
		radio.updateFrequency(steps); // Shown at once; the server's word comes back.
	}

	private Button button(String label, int x, int y, int w, Button.OnPress press) {
		return addRenderableWidget(Button.builder(Component.literal(label), press).bounds(leftPos + x, topPos + y, w, 16).build());
	}

	private void gain(float delta) {
		float gain = Math.max(0.0F, Math.min(2.0F, radio.getSpeakerGain() + delta));
		radio.setSpeakerGain(gain);
		send(SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, Math.round(gain * 100.0F), false);
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = radio.hasTransmitter(); // The PTT takes your voice into it.
		button("PWR", 8, 8, 30, b -> send(SBlockRadioControlPacket.Action.TOGGLE_POWER, 0, true));
		if(radio instanceof AirbandRadioBlockEntity) {
			button("VOL-", 8, 62, 30, b -> gain(-0.1F));
			button("VOL+", 40, 62, 30, b -> gain(0.1F));
			button("<->", 106, 26, 24, b -> {
				if(menu.isPowered())
					send(SBlockRadioControlPacket.Action.AIRBAND_FLIP, 0, true);
			});
			button("MHz-", 150, 62, 38, b -> step(-AirbandRadioBlockEntity.COARSE_STEPS));
			button("MHz+", 190, 62, 38, b -> step(AirbandRadioBlockEntity.COARSE_STEPS));
			button("kHz-", 150, 44, 38, b -> step(-1));
			button("kHz+", 190, 44, 38, b -> step(1));
			addRenderableWidget(new PttButton(leftPos + 76, topPos + 62, 66, 16));
		}
		else if(radio instanceof FmBroadcastRadioBlockEntity) {
			button("<<", 150, 70, 38, b -> step(-1));
			button(">>", 190, 70, 38, b -> step(1));
			if(radio instanceof FmBroadcastTransmitterBlockEntity)
				addRenderableWidget(new PttButton(leftPos + 8, topPos + 70, 90, 16));
			else {
				button("VOL-", 8, 70, 30, b -> gain(-0.1F));
				button("VOL+", 40, 70, 30, b -> gain(0.1F));
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY != 0.0D && isHovering(0, 0, imageWidth, imageHeight, mouseX, mouseY)) {
			int steps = scrollY > 0 ? 1 : -1;
			step(hasShiftDown() && radio instanceof AirbandRadioBlockEntity ? steps * AirbandRadioBlockEntity.COARSE_STEPS : steps);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		if(RadiocraftClientValues.SCREEN_PTT_PRESSED)
			send(SBlockRadioControlPacket.Action.SET_PTT, 0, false);
		RadiocraftClientValues.SCREEN_PTT_PRESSED = false;
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = false;
		super.onClose();
	}

	// -------------------- DRAWING --------------------

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
		boolean on = menu.isPowered();
		if(radio instanceof AirbandRadioBlockEntity airband) {
			g.fill(x, y, x + w, y + h, BEVEL);
			g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PLATE);
			window(g, x + 8, y + 26, 94, "USE", on ? AirbandRadioBlockEntity.format(radio.getFrequency()) : "");
			window(g, x + 134, y + 26, 94, "STBY", on ? AirbandRadioBlockEntity.format(airband.getStandbyFrequency()) : "");
			g.drawString(font, "COM", x + 44, y + 12, PRINT, false);
			g.drawString(font, "TUNE STBY", x + 160, y + 12, PRINT, false);
			lamps(g, x + w - 26, y + 8);
		}
		else if(radio instanceof FmBroadcastTransmitterBlockEntity) {
			g.fill(x, y, x + w, y + h, 0xFF10283F);
			g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BLUE);
			g.fill(x + 2, y + 2, x + 34, y + h - 2, SILVER);
			g.drawString(font, "FM TRANSMITTER", x + 44, y + 10, 0xFFE8E8E8, false);
			window(g, x + 44, y + 26, 120, "MHz", on ? FmBroadcastRadioBlockEntity.format(radio.getFrequency()) : "");
			boolean air = on && radio.isPTTDown() && !radio.isTransmitInhibited();
			g.fill(x + 176, y + 30, x + 226, y + 46, air ? 0xFFE03020 : 0xFF401010);
			g.drawCenteredString(font, "ON AIR", x + 201, y + 34, air ? 0xFFFFFFFF : 0xFF806060);
			lamps(g, x + w - 26, y + 8);
		}
		else if(radio instanceof FmBroadcastReceiverBlockEntity) {
			g.fill(x, y, x + w, y + h, WOOD);
			g.fill(x + 6, y + 4, x + w - 6, y + h - 4, 0xFF1C1C1E);
			g.fill(x + 6, y + 4, x + w - 6, y + 7, SILVER);
			// The dial: a scale 88-108 with the pointer at the tuned frequency.
			int dx = x + 44, dw = 184, dy = y + 22;
			g.fill(dx, dy, dx + dw, dy + 30, DIAL);
			for(int mhz = 88; mhz <= 108; mhz += 2) {
				int tx = dx + 6 + (mhz - 88) * (dw - 12) / 20;
				g.fill(tx, dy + 18, tx + 1, dy + 26, ORANGE);
				if(mhz % 4 == 0)
					g.drawCenteredString(font, Integer.toString(mhz), tx, dy + 4, ORANGE);
			}
			double at = (radio.getFrequency() - 88_000_000.0D) / 20_000_000.0D;
			int px = dx + 6 + (int)Math.round(at * (dw - 12));
			g.fill(px, dy + 2, px + 2, dy + 28, on ? 0xFFFF3020 : 0xFF702010);
			g.drawString(font, "FM " + FmBroadcastRadioBlockEntity.format(radio.getFrequency()) + " MHz", dx, dy + 34, on ? ORANGE : 0xFF603010, false);
			lamps(g, x + w - 26, y + 8);
		}
	}

	/** An amber seven-segment window with its legend under it. */
	private void window(GuiGraphics g, int x, int y, int w, String legend, String text) {
		g.fill(x - 1, y - 1, x + w + 1, y + 19, 0xFF101012);
		g.fill(x, y, x + w, y + 18, AMBER_DIM);
		if(!text.isEmpty()) {
			g.pose().pushPose();
			g.pose().translate(x + w / 2.0F, y + 3.0F, 0.0F);
			g.pose().scale(1.6F, 1.6F, 1.0F);
			g.drawCenteredString(font, text, 0, 0, AMBER);
			g.pose().popPose();
		}
		g.drawString(font, legend, x, y + 21, PRINT, false);
	}

	/** RX (green) and TX (red) lamps. */
	private void lamps(GuiGraphics g, int x, int y) {
		boolean on = menu.isPowered();
		boolean tx = on && radio.isPTTDown() && !radio.isTransmitInhibited();
		boolean rx = on && radio.hasTransmitter() ? !tx && radio.getSyncedSignal() > 0.05D : on && radio.getSyncedSignal() > 0.05D;
		g.fill(x, y, x + 8, y + 8, rx ? 0xFF40E060 : 0xFF183018);
		g.fill(x + 10, y, x + 18, y + 8, tx ? 0xFFF03020 : 0xFF301010);
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, 44, imageHeight - 12, PRINT, false);
		String volume = String.format(Locale.ROOT, "VOL %d%%", Math.round(radio.getSpeakerGain() * 100.0F));
		if(radio.hasTransmitter() && !(radio instanceof AirbandRadioBlockEntity))
			return;
		g.drawString(font, volume, 8, radio instanceof AirbandRadioBlockEntity ? 50 : 58, PRINT, false);
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		renderTooltip(g, mouseX, mouseY);
	}

	/** Held to talk: down while the mouse is. */
	private final class PttButton extends AbstractButton {

		PttButton(int x, int y, int w, int h) {
			super(x, y, w, h, Component.literal(radio instanceof FmBroadcastTransmitterBlockEntity ? "ON AIR (hold)" : "PTT (hold)"));
		}

		@Override
		public void onPress() {
			if(!menu.isPowered())
				return;
			send(SBlockRadioControlPacket.Action.SET_PTT, 0, true);
			RadiocraftClientValues.SCREEN_PTT_PRESSED = true;
		}

		@Override
		public void onRelease(double mouseX, double mouseY) {
			if(RadiocraftClientValues.SCREEN_PTT_PRESSED)
				send(SBlockRadioControlPacket.Action.SET_PTT, 0, false);
			RadiocraftClientValues.SCREEN_PTT_PRESSED = false;
		}

		@Override
		protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
