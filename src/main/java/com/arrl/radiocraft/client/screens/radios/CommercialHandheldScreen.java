package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.capabilities.IVHFHandheldCapability;
import com.arrl.radiocraft.common.capabilities.RadiocraftCapabilities;
import com.arrl.radiocraft.common.items.CommercialHandheldItem;
import com.arrl.radiocraft.common.network.serverbound.SCommercialRadioPacket;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * A commercial handheld seen from the front (sneak and use it), drawn from its own item texture at 16 GUI pixels per
 * model unit with the case in its dye colour, so it looks like the radio in your hand. Its two knobs are the ones on
 * top of the drawn radio (2026-11-06; there were separate dials above it): the 16-position <b>channel</b> knob (stops
 * at 1 and 16) and the <b>power/volume</b> knob (OFF, 1-10: turning it to OFF switches the radio off, up from OFF
 * switches it on). The wheel over a knob turns it a step, dragging it up or down turns it, a click turns it one step
 * and a right click one back. While it turns, and for {@link #READOUT_MS} ms after, a small readout over it says where
 * it is ("CH 7", "VOL 6", "OFF"), and a mark on the knob's face shows its position. The RX and TX lamps light as on
 * the radio.
 * <p>
 * The upgraded radio also has its LCD (channel name, or who is talking; zone, channel and mode) and its keypad: <b>A</b>,
 * <b>B</b>, <b>C</b> and <b>D</b> pick that zone (A = channels 1-16, B 17-32, C 33-48, D 49-64; 2026-11-06, they stepped
 * down and up through three zones before), <b>*</b> shows the radio's unit ID and <b>#</b> the channel's frequency for
 * a few seconds (they were on A and D). The number keys beep and do nothing more. There is nothing to program here: a radio shop does that (CPS or the Radio Cloner). Hold right click
 * with the radio in hand to talk, as always.
 * <p>
 * The texture regions are those of tools/commercial_handheld_models.py.
 */
public class CommercialHandheldScreen extends Screen {

	private static final int WIDTH = 160, BODY_X = 40, BODY_W = 80, BODY_TOP = 48;
	private static final int LCD_INK = 0xFF1F2A14, LED_OFF = 0x00000000, RX_ON = 0xFF3BFF6A, TX_ON = 0xFFFF3B30, POWER_ON = 0xFF4A6BFF;
	private static final ResourceLocation BASIC = Radiocraft.id("textures/item/basic_commercial_handheld.png");
	private static final ResourceLocation UPGRADED = Radiocraft.id("textures/item/upgraded_commercial_handheld.png");
	/** The upgraded radio's keypad, as printed on its keys. */
	private static final String[] KEYS = {"123A", "456B", "789C", "*0#D"};
	/** How long (ticks) * or # keeps its line on the LCD. */
	private static final int INFO_TICKS = 60;

	/** How long a knob's readout stays after it was last turned, and how long it takes to fade, ms. */
	private static final long READOUT_MS = 1500L, FADE_MS = 300L;
	/** Pixels of drag up or down per step of a knob. */
	private static final int DRAG_STEP = 6;
	private static final int CHANNEL = 0, VOLUME = 1;

	private final int index;
	private int left, top;
	/** The knob being pressed (CHANNEL or VOLUME, -1 for none), the button, and where the drag started. */
	private int pressedKnob = -1, pressedButton, dragStartValue;
	private double dragStartY;
	private boolean dragged;
	/** The knob whose readout shows, and when it was last turned. */
	private int readoutKnob = -1;
	private long readoutAt;
	private int pressedKey = -1, pressedTicks = 0;
	private String info = null;
	private int infoTicks = 0;

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

	private boolean upgraded() {
		CommercialRadioModel model = CommercialHandheldItem.modelOf(stack());
		return model != null && model.hasDisplay();
	}

	private int height() {
		return upgraded() ? BODY_TOP + 168 + 16 : BODY_TOP + 136 + 24;
	}

	@Override
	protected void init() {
		super.init();
		left = (width - WIDTH) / 2;
		top = (height - height()) / 2;
	}

	// ---- The knobs on top of the drawn radio -----------------------------------------------------------------------

	/**
	 * Knob {@code knob}'s face as drawn on top of the radio: {x0, y0, x1, y1} in screen pixels (from the model's knob
	 * positions, as the drawing below places them).
	 */
	private int[] knobRect(int knob) {
		int bx = left + BODY_X, by = top + BODY_TOP;
		if(upgraded())
			return knob == CHANNEL ? new int[] {bx + 36, by - 9, bx + 57, by} : new int[] {bx + 60, by - 7, bx + 76, by};
		return knob == CHANNEL ? new int[] {bx + 42, by - 8, bx + 58, by} : new int[] {bx + 62, by - 8, bx + 78, by};
	}

	/** The knob under the mouse (a few pixels of slack round its face), -1 for none. */
	private int knobAt(double x, double y) {
		for(int knob = CHANNEL; knob <= VOLUME; knob++) {
			int[] r = knobRect(knob);
			if(x >= r[0] - 3 && x < r[2] + 3 && y >= r[1] - 6 && y < r[3] + 2)
				return knob;
		}
		return -1;
	}

	/** Where knob {@code knob} is: the channel knob 0-15, the volume knob 0 (off) to 10. */
	private int knobValue(int knob) {
		return knob == CHANNEL ? CommercialHandheldItem.program(stack()).knob() : CommercialHandheldItem.volumeKnob(stack());
	}

	private int knobMax(int knob) {
		return knob == CHANNEL ? CommercialProgram.KNOB_POSITIONS - 1 : CommercialHandheldItem.VOLUME_POSITIONS;
	}

	/** Turns knob {@code knob} to {@code value} (clamped; a real knob stops at its ends) and shows its readout. */
	private void turnKnob(int knob, int value) {
		int to = Math.max(0, Math.min(knobMax(knob), value));
		readoutKnob = knob;
		readoutAt = System.currentTimeMillis();
		if(to == knobValue(knob))
			return;
		if(knob == CHANNEL)
			setChannel(to);
		else
			setVolume(to);
	}

	// ---- Controls --------------------------------------------------------------------------------------------------

	private void setChannel(int knob) {
		ItemStack stack = stack();
		if(!CommercialHandheldItem.is(stack))
			return;
		CommercialProgram program = CommercialHandheldItem.program(stack);
		CommercialProgram turned = program.select(program.zone() * CommercialProgram.KNOB_POSITIONS + knob);
		CommercialHandheldItem.setProgram(stack, turned); // At once on the client; the server does the same.
		SCommercialRadioPacket.handheld(index, turned.selected());
		click(1.8F);
	}

	private void setVolume(int position) {
		ItemStack stack = stack();
		if(!CommercialHandheldItem.is(stack))
			return;
		boolean wasOn = CommercialHandheldItem.volumeKnob(stack) > 0;
		CommercialHandheldItem.setVolumeKnob(stack, position);
		SCommercialRadioPacket.volume(index, position);
		if(wasOn != position > 0)
			click(position > 0 ? 1.2F : 0.9F); // The power switch's click.
	}

	private void pressKey(int key) {
		pressedKey = key;
		pressedTicks = 4;
		click(2.0F);
		ItemStack stack = stack();
		char label = KEYS[key / 4].charAt(key % 4);
		CommercialProgram program = CommercialHandheldItem.program(stack);
		switch(label) {
			case 'A', 'B', 'C', 'D' -> { // Straight to that zone.
				int zone = label - 'A';
				CommercialHandheldItem.selectZone(stack, zone);
				SCommercialRadioPacket.zone(index, zone);
			}
			case '*' -> showInfo(program.unitId() > 0 ? CommercialProgram.unitText(program.unitId()) : "NO ID");
			case '#' -> {
				CommercialChannel channel = program.active();
				showInfo(channel == null ? "NO CH" : String.format(Locale.ROOT, "%.4f", channel.rxHz() / 1e6));
			}
			default -> {}
		}
	}

	private void showInfo(String text) {
		info = text;
		infoTicks = INFO_TICKS;
	}

	/** The key under the mouse (row x 4 + column), -1 for none. */
	private int keyAt(double x, double y) {
		if(!upgraded())
			return -1;
		for(int r = 0; r < 4; r++)
			for(int c = 0; c < 4; c++) {
				int kx = left + BODY_X + (5 + c * 8) * 2, ky = top + BODY_TOP + (49 + r * 8) * 2;
				if(x >= kx && x < kx + 14 && y >= ky && y < ky + 14)
					return r * 4 + c;
			}
		return -1;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int knob = knobAt(mouseX, mouseY);
		if(knob >= 0 && (button == 0 || button == 1)) { // A click steps it on release; a drag turns it instead.
			pressedKnob = knob;
			pressedButton = button;
			dragStartY = mouseY;
			dragStartValue = knobValue(knob);
			dragged = false;
			return true;
		}
		int key = keyAt(mouseX, mouseY);
		if(key >= 0 && button == 0) {
			pressKey(key);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if(pressedKnob >= 0 && pressedButton == 0) {
			int steps = (int)((dragStartY - mouseY) / DRAG_STEP); // Up turns it up.
			if(steps != 0 || dragged) {
				dragged = true;
				turnKnob(pressedKnob, dragStartValue + steps);
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(pressedKnob >= 0 && button == pressedButton) {
			if(!dragged)
				turnKnob(pressedKnob, knobValue(pressedKnob) + (button == 0 ? 1 : -1));
			pressedKnob = -1;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int knob = knobAt(mouseX, mouseY);
		if(knob >= 0 && scrollY != 0.0D) {
			turnKnob(knob, knobValue(knob) + (scrollY > 0.0D ? 1 : -1));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private static void click(float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player != null)
			mc.player.playSound(net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON, 0.3F, pitch);
	}

	@Override
	public void tick() {
		super.tick();
		if(pressedTicks > 0 && --pressedTicks == 0)
			pressedKey = -1;
		if(infoTicks > 0 && --infoTicks == 0)
			info = null;
		if(!CommercialHandheldItem.is(stack()))
			onClose(); // The radio left that slot.
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- Drawing ---------------------------------------------------------------------------------------------------

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		ItemStack stack = stack();
		IVHFHandheldCapability cap = cap();
		CommercialRadioModel model = CommercialHandheldItem.modelOf(stack);
		if(cap == null || model == null)
			return;
		boolean upgraded = model.hasDisplay();
		CommercialProgram program = CommercialHandheldItem.program(stack);
		boolean on = cap.isPowered();
		boolean tx = on && cap.isPTTDown() && CommercialHandheldItem.mayTransmit(stack);
		boolean rx = on && !tx && cap.getReceiveStrength() > 0.0F;
		int bx = left + BODY_X, by = top + BODY_TOP;
		int colour = CommercialHandheldItem.caseColour(stack);
		float r = ((colour >> 16) & 255) / 255.0F, g = ((colour >> 8) & 255) / 255.0F, b = (colour & 255) / 255.0F;

		if(upgraded) {
			// Antenna (x 6.15-6.95, collar 5.8-7.3), the top lamp (7.35-7.6) and the knobs as seen from the front.
			graphics.fill(bx + 10, top + 2, bx + 23, by - 8, 0xFF141416);
			graphics.fill(bx + 9, top, bx + 24, top + 3, 0xFF141416);
			graphics.fill(bx + 5, by - 8, bx + 29, by, 0xFF8C9096);
			graphics.fill(bx + 36, by - 9, bx + 57, by, 0xFF222326);
			graphics.fill(bx + 60, by - 7, bx + 76, by, 0xFF222326);
			graphics.fill(bx + 30, by - 3, bx + 34, by, tx ? TX_ON : rx ? RX_ON : 0xFF5A1E1A);
			// Case (tinted), then the grille, LCD and keypad over it, then the top of the battery.
			graphics.setColor(r, g, b, 1.0F);
			graphics.blit(UPGRADED, bx, by, BODY_W, 168, 40, 0, 40, 84, 128, 128);
			graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
			graphics.blit(UPGRADED, bx, by, BODY_W, 168, 0, 0, 40, 84, 128, 128);
			graphics.blit(UPGRADED, bx, by + 168, BODY_W, 16, 0, 84, 40, 8, 128, 128);
			if(pressedKey >= 0) {
				int kx = bx + (5 + pressedKey % 4 * 8) * 2, ky = by + (49 + pressedKey / 4 * 8) * 2;
				graphics.fill(kx, ky, kx + 14, ky + 14, 0x80000000);
			}
			renderLcd(graphics, program, model, on, tx, bx + 8, by + 66);
		}
		else {
			// Antenna (x 6.2-7.0, collar 5.9-7.3) and the knobs (8.1-9.1 channel, 9.4-10.4 volume) from the front.
			graphics.fill(bx + 11, top + 2, bx + 24, by - 8, 0xFF121214);
			graphics.fill(bx + 6, by - 8, bx + 29, by, 0xFF787C84);
			graphics.fill(bx + 42, by - 8, bx + 58, by, 0xFF787C84);
			graphics.fill(bx + 62, by - 8, bx + 78, by, 0xFF787C84);
			// The maintainer's front: its case copy tinted, then the speaker panel, lamps and seam, then the battery.
			graphics.setColor(r, g, b, 1.0F);
			graphics.blit(BASIC, bx, by, BODY_W, 136, 20, 0, 20, 34, 64, 64);
			graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
			graphics.blit(BASIC, bx + 4, by + 4, 72, 52, 1, 1, 18, 13, 64, 64);
			graphics.blit(BASIC, bx + 8, by + 60, 20, 4, 2, 15, 5, 1, 64, 64);
			graphics.blit(BASIC, bx, by + 128, BODY_W, 8, 0, 32, 20, 2, 64, 64);
			graphics.blit(BASIC, bx, by + 136, BODY_W, 24, 0, 36, 20, 6, 64, 64);
			lamp(graphics, bx + 8, by + 60, tx ? TX_ON : LED_OFF, "TX");
			lamp(graphics, bx + 16, by + 60, rx ? RX_ON : LED_OFF, "RX");
			lamp(graphics, bx + 24, by + 60, on ? POWER_ON : LED_OFF, "ON");
		}

		// Each knob's mark (where it points), then the readout of the one being turned.
		int vol = CommercialHandheldItem.volumeKnob(stack);
		knobMark(graphics, knobRect(CHANNEL), program.knob() / (float)(CommercialProgram.KNOB_POSITIONS - 1));
		knobMark(graphics, knobRect(VOLUME), vol / (float)CommercialHandheldItem.VOLUME_POSITIONS);
		long since = System.currentTimeMillis() - readoutAt;
		boolean turning = pressedKnob >= 0 && dragged;
		if(readoutKnob >= 0 && (turning || since < READOUT_MS)) {
			String text = readoutKnob == CHANNEL ? "CH " + (program.knob() + 1)
					: vol == 0 ? Component.translatable(Radiocraft.translationKey("screen", "commercial.off")).getString() : "VOL " + vol;
			float alpha = turning || since < READOUT_MS - FADE_MS ? 1.0F : (READOUT_MS - since) / (float)FADE_MS;
			readout(graphics, knobRect(readoutKnob), text, readoutKnob == VOLUME && vol == 0, alpha);
		}

		int hovered = knobAt(mouseX, mouseY);
		if(hovered >= 0) {
			if(pressedKnob < 0 && (readoutKnob < 0 || since >= READOUT_MS)) // Kept short, and out of the way while it turns.
				graphics.renderTooltip(font, Component.translatable(Radiocraft.translationKey("screen", hovered == CHANNEL ? "commercial.channel_knob_help" : "commercial.volume_knob_help")),
						mouseX, mouseY);
		}
		else {
			int key = keyAt(mouseX, mouseY);
			char label = key < 0 ? ' ' : KEYS[key / 4].charAt(key % 4);
			String help = label >= 'A' && label <= 'D' ? "commercial.key_" + Character.toLowerCase(label) : label == '*' ? "commercial.key_star" : label == '#' ? "commercial.key_hash" : null;
			if(help != null)
				graphics.renderTooltip(font, Component.translatable(Radiocraft.translationKey("screen", help)), mouseX, mouseY);
		}
	}

	/** The mark on a knob's face: a light line across it, left at the bottom of its range, right at the top. */
	private static void knobMark(GuiGraphics graphics, int[] r, float position) {
		int x = r[0] + 2 + Math.round((r[2] - r[0] - 5) * Math.max(0.0F, Math.min(1.0F, position)));
		graphics.fill(x, r[1] + 1, x + 1, r[3] - 1, 0xFFE8E8E8);
	}

	/** The readout over a knob: a small dark plate with its position, fading out ({@code alpha}). */
	private void readout(GuiGraphics graphics, int[] r, String text, boolean off, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
		if(a < 8)
			return;
		int w = font.width(text) + 6, cx = (r[0] + r[2]) / 2;
		int x0 = cx - w / 2, y1 = r[1] - 3, y0 = y1 - 12;
		graphics.fill(x0, y0, x0 + w, y1, (Math.round(a * 0.8F) << 24) | 0x101012);
		graphics.fill(cx - 1, y1, cx + 1, y1 + 2, (Math.round(a * 0.8F) << 24) | 0x101012);
		graphics.drawString(font, text, x0 + 3, y0 + 2, (a << 24) | (off ? 0xFF8080 : 0xE8E8E8), false);
	}

	/** A lamp on the basic radio's front (4 x 4), lit or left as drawn, with its name under it. */
	private void lamp(GuiGraphics graphics, int x, int y, int lit, String name) {
		if(lit != LED_OFF) {
			graphics.fill(x - 1, y - 1, x + 5, y + 5, (lit & 0x00FFFFFF) | 0x60000000);
			graphics.fill(x, y, x + 4, y + 4, lit);
		}
		graphics.pose().pushPose();
		graphics.pose().translate(x + 2.0F, y + 6.0F, 0.0F);
		graphics.pose().scale(0.5F, 0.5F, 1.0F);
		graphics.drawString(font, name, -font.width(name) / 2, 0, 0xFFD0D0D0, false);
		graphics.pose().popPose();
	}

	/** The upgraded radio's LCD (64 x 24 at x, y): name or caller or the A/D line, then zone, channel and mode. */
	private void renderLcd(GuiGraphics graphics, CommercialProgram program, CommercialRadioModel model, boolean on, boolean tx, int x, int y) {
		if(!on) {
			graphics.fill(x, y, x + 64, y + 24, 0xB0283020); // Off: the glass goes dark.
			return;
		}
		CommercialChannel channel = program.active();
		String name = channel == null ? "-- NO CH --" : channel.name().isEmpty() ? String.format(Locale.ROOT, "%.4f", channel.rxHz() / 1e6) : channel.name();
		String caller = tx ? null : com.arrl.radiocraft.client.CallerIds.callerText(program, channel); // Who is talking: alias or unit ID.
		if(caller != null)
			name = caller;
		if(info != null)
			name = info;
		String line = font.plainSubstrByWidth(name, 62);
		graphics.drawString(font, line, x + 32 - font.width(line) / 2, y + 3, LCD_INK, false);
		String mode = tx ? "TX" : channel == null ? "" : channel.encrypted() ? "ENC" : channel.digital() ? "DIG" : "FM";
		String where = (model.hasZones() ? "Z" + CommercialProgram.zoneLetter(program.zone()) + " " : "") + "CH" + (program.selected() + 1);
		graphics.pose().pushPose();
		graphics.pose().translate(x + 2.0F, y + 15.0F, 0.0F);
		graphics.pose().scale(0.75F, 0.75F, 1.0F);
		graphics.drawString(font, where, 0, 0, LCD_INK, false);
		graphics.drawString(font, mode, (int)(60 / 0.75F) - font.width(mode), 0, LCD_INK, false);
		graphics.pose().popPose();
	}
}
