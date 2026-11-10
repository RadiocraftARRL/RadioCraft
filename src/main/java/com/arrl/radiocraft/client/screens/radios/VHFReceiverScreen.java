package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.render.VHFReceiverRenderer;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.VHFReceiverBlockEntity;
import com.arrl.radiocraft.common.menus.VHFReceiverMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.network.serverbound.SVHFReceiverPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The VHF receiver's front panel (2026-11-09), a 1970s eight-channel crystal scanner: a black wraparound case and a
 * chrome front, laid out by {@code gui_layouts/vhf_receiver.json} over {@code textures/gui/vhf_receiver.png} (both from
 * {@code tools/vhf_receiver_assets.py}), as the block's front has them.
 * <ul>
 *     <li>Top left, the key box: the blue MAN/AUTO key (out MAN, the scan holds; in AUTO, it scans), the red SET key
 *     and the SET lamp, lit while SET is in and exactly one channel is switched on.</li>
 *     <li>Under it, VOLUME (all the way down clicks OFF: the power switch), SQUELCH, and TUNE, which moves the one
 *     switched-on channel's frequency while the SET lamp is lit: {@link VHFReceiverBlockEntity#STEP_HZ} a click, shift
 *     {@link VHFReceiverBlockEntity#COARSE_STEP_HZ}, control {@link VHFReceiverBlockEntity#FAST_STEP_HZ}.</li>
 *     <li>Top right, the channel window: the numeral of the channel the scan is on lights red. Under it the eight slide
 *     switches, up on and down off.</li>
 * </ul>
 * No display: the frequency shows above the panel in a tooltip-like label ({@link #hint}), never over a control. Knobs
 * turn with the wheel over them, a click one step up, a right click one down; the wheel over a switch moves it, and
 * anywhere else it turns TUNE.
 */
public class VHFReceiverScreen extends AbstractContainerScreen<VHFReceiverMenu> {

	/** How long the label keeps showing the channel being set after TUNE last turned, ms. */
	private static final long TUNED_SHOW_MS = 1500L;

	private final HfPanel panel = HfPanel.loadLayout("vhf_receiver");
	/** When TUNE last turned (ms), and whether it could set anything then. */
	private long tunedAt = 0L;

	public VHFReceiverScreen(VHFReceiverMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = panel.width();
		imageHeight = panel.height();
	}

	private VHFReceiverBlockEntity receiver() {
		return menu.blockEntity;
	}

	/** Running: switched on, with the power to. */
	private boolean on() {
		return menu.isPowered() && menu.getDisplayPhase() == RadioNetworkObject.PHASE_ON;
	}

	// -------------------- CONTROLS --------------------

	private void click(float pitch) {
		if(minecraft != null && minecraft.player != null)
			minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, pitch);
	}

	/** A knob turned {@code steps} clicks. */
	private void turn(String knob, int steps) {
		VHFReceiverBlockEntity rx = receiver();
		switch(knob) {
			case "volume" -> { // Down past the lowest setting clicks OFF; up from OFF switches on.
				int gain = Math.max(10, Math.round(rx.getSpeakerGain() * 100.0F));
				if(!menu.isPowered()) {
					if(steps > 0) {
						SBlockRadioControlPacket.updateServer(rx.getBlockPos(), SBlockRadioControlPacket.Action.TOGGLE_POWER, true);
						setGain(gain);
						click(0.9F);
					}
					return;
				}
				int next = gain + 10 * steps;
				if(next < 10) {
					SBlockRadioControlPacket.updateServer(rx.getBlockPos(), SBlockRadioControlPacket.Action.TOGGLE_POWER, true);
					click(0.7F);
					return;
				}
				setGain(Math.min(200, next));
				click(1.2F);
			}
			case "squelch" -> {
				int next = Math.max(0, Math.min(100, Math.round(rx.getSquelchLevel() * 100.0F) + 10 * steps));
				rx.setSquelchClient(next / 100.0F);
				SBlockRadioControlPacket.updateServer(rx.getBlockPos(), SBlockRadioControlPacket.Action.SET_SQUELCH, next);
				click(1.2F);
			}
			case "tune" -> {
				tunedAt = System.currentTimeMillis();
				if(!on() || !rx.isSetLit())
					return; // The label says why it does nothing.
				int step = Screen.hasControlDown() ? VHFReceiverBlockEntity.FAST_STEP_HZ
						: Screen.hasShiftDown() ? VHFReceiverBlockEntity.COARSE_STEP_HZ : VHFReceiverBlockEntity.STEP_HZ;
				rx.tuneSetChannel(step * steps); // Shown at once; the server's answer follows.
				SVHFReceiverPacket.send(rx.getBlockPos(), SVHFReceiverPacket.TUNE, step * steps);
				click(1.5F);
			}
			default -> {}
		}
	}

	private void setGain(int percent) {
		receiver().setSpeakerGainClient(percent / 100.0F);
		SBlockRadioControlPacket.updateServer(receiver().getBlockPos(), SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, percent);
	}

	/** Channel {@code channel}'s switch pushed up ({@code up}) or down. */
	private void flip(int channel, boolean up) {
		if(receiver().isChannelOn(channel) == up)
			return;
		receiver().setChannelOn(channel, up); // Client copy only; the server saves and syncs it.
		SVHFReceiverPacket.send(receiver().getBlockPos(), SVHFReceiverPacket.SET_CHANNEL, channel * 2 + (up ? 1 : 0));
		click(up ? 1.3F : 1.1F);
	}

	private void pressKey(String name) {
		VHFReceiverBlockEntity rx = receiver();
		if(name.equals("key_set")) {
			boolean in = !rx.isSetMode();
			rx.setSetMode(in);
			SVHFReceiverPacket.send(rx.getBlockPos(), SVHFReceiverPacket.SET_KEY, in ? 1 : 0);
		}
		else if(name.equals("key_man")) {
			boolean hold = !rx.isScanHold();
			rx.setScanHold(hold);
			SVHFReceiverPacket.send(rx.getBlockPos(), SVHFReceiverPacket.HOLD_KEY, hold ? 1 : 0);
		}
		click(0.8F);
	}

	private static int channelOf(HfPanel.Control c) {
		return Integer.parseInt(c.name().substring(c.name().lastIndexOf('_') + 1)) - 1;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY == 0.0D)
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		HfPanel.Control c = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		if(c != null && c.type().equals("switch"))
			flip(channelOf(c), scrollY > 0.0D);
		else
			turn(c != null && c.type().equals("knob") ? c.name() : "tune", scrollY > 0.0D ? 1 : -1);
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HfPanel.Control c = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		if(c != null && (button == 0 || button == 1)) {
			switch(c.type()) {
				case "knob" -> {
					turn(c.name(), button == 0 ? 1 : -1);
					return true;
				}
				case "switch" -> {
					int channel = channelOf(c);
					flip(channel, !receiver().isChannelOn(channel));
					return true;
				}
				case "key" -> {
					if(button == 0) {
						pressKey(c.name());
						return true;
					}
				}
				default -> {}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		HfPanel.Control c = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		renderHint(graphics, hint(c));
		List<Component> tip = c == null ? null : tooltip(c);
		if(tip != null && !tip.isEmpty()) {
			List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
			for(Component line : tip)
				lines.addAll(font.split(line, 220));
			graphics.renderTooltip(font, lines, mouseX, mouseY);
		}
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(panel.texture(), leftPos, topPos, 0, 0, panel.width(), panel.height(), panel.width(), panel.height());
		VHFReceiverBlockEntity rx = receiver();
		boolean on = on();
		for(HfPanel.Control c : panel.controls()) {
			switch(c.type()) {
				case "knob" -> MarinePanels.knob(g, leftPos, topPos, c, knobValue(c));
				case "key" -> key(g, c, c.name().equals("key_set") ? rx.isSetMode() : !rx.isScanHold());
				case "switch" -> slideSwitch(g, c, rx.isChannelOn(channelOf(c)));
				case "lamp" -> {
					if(!on)
						break;
					if(c.name().equals("set_lamp") && rx.isSetLit())
						MarinePanels.lamp(g, leftPos, topPos, c, c.colour("colour", 0xFFFF3020));
					else if(c.name().startsWith("numeral_") && rx.getScanChannel() == channelOf(c) && rx.isChannelOn(channelOf(c)))
						numeral(g, c);
				}
				default -> {}
			}
		}
	}

	/** Where a knob's pointer sits, 0 to 1 of its travel. VOLUME on starts past OFF ({@code on_min_deg}). */
	private double knobValue(HfPanel.Control c) {
		VHFReceiverBlockEntity rx = receiver();
		return switch(c.name()) {
			case "volume" -> {
				if(!menu.isPowered())
					yield 0.0D;
				double min = c.num("min_deg", -135), max = c.num("max_deg", 135), onMin = c.num("on_min_deg", min);
				double gain = Math.max(0.0D, Math.min(1.0D, rx.getSpeakerGain() / 2.0D));
				yield (onMin - min + gain * (max - onMin)) / (max - min);
			}
			case "squelch" -> rx.getSquelchLevel();
			case "tune" -> {
				int channel = rx.onlyChannel() >= 0 ? rx.onlyChannel() : rx.getScanChannel();
				yield channel < 0 ? 0.0D
						: (rx.getChannelHz(channel) - VHFReceiverBlockEntity.MIN_HZ) / (double)(VHFReceiverBlockEntity.MAX_HZ - VHFReceiverBlockEntity.MIN_HZ);
			}
			default -> 0.0D;
		};
	}

	/** A latching key's ribbed cap, standing out or pushed in (lower and darker). */
	private void key(GuiGraphics g, HfPanel.Control c, boolean in) {
		var handles = c.rects("handles");
		if(handles.size() < 2)
			return;
		int[] h = handles.get(in ? 1 : 0);
		int face = c.colour("cap_colour", 0xFF3076C4), light = c.colour("cap_light", 0xFF6EA8E4), dark = c.colour("cap_dark", 0xFF184078);
		if(in) {
			light = face;
			face = darker(face);
		}
		int x = leftPos + h[0], y = topPos + h[1];
		g.fill(x, y, x + h[2], y + h[3], face);
		g.fill(x, y, x + h[2], y + 1, light);
		g.fill(x, y + h[3] - 1, x + h[2], y + h[3], dark);
		for(int rx = x + 2; rx < x + h[2] - 1; rx += 3)
			g.fill(rx, y + 2, rx + 1, y + h[3] - 2, dark);
	}

	/** A slide switch's chrome handle, with its dark cap, up (on) or down. */
	private void slideSwitch(GuiGraphics g, HfPanel.Control c, boolean up) {
		var handles = c.rects("handles");
		int i = c.strings("positions").indexOf(up ? "ON" : "OFF");
		if(i < 0 || i >= handles.size())
			return;
		int[] h = handles.get(i);
		int x = leftPos + h[0], y = topPos + h[1];
		g.fill(x, y, x + h[2], y + h[3], c.colour("handle_shadow", 0xFF46484C));
		g.fill(x, y, x + h[2] - 1, y + h[3] - 1, c.colour("handle_colour", 0xFFD6D8DC));
		g.fill(x + 2, y + 2, x + h[2] - 3, y + h[3] - 3, c.colour("cap_colour", 0xFF28282C));
	}

	/** The lit numeral: the painted glyph again, bright, over a dim glow a pixel round each of its dots. */
	private void numeral(GuiGraphics g, HfPanel.Control c) {
		int digit = (int)c.num("digit", 0);
		if(digit < 1 || digit > VHFReceiverRenderer.DIGITS.length)
			return;
		String glyph = VHFReceiverRenderer.DIGITS[digit - 1];
		int s = (int)c.num("glyph_scale", 2), x0 = leftPos + (int)c.num("glyph_x", c.x()), y0 = topPos + (int)c.num("glyph_y", c.y());
		int lit = c.colour("colour", 0xFFFF4A30), glow = c.colour("glow", 0xFF6E160C);
		for(int pass = 0; pass < 2; pass++)
			for(int gy = 0; gy < 5; gy++)
				for(int gx = 0; gx < 3; gx++) {
					if(glyph.charAt(gy * 3 + gx) != '1')
						continue;
					int x = x0 + gx * s, y = y0 + gy * s;
					if(pass == 0)
						g.fill(x - 1, y - 1, x + s + 1, y + s + 1, glow);
					else
						g.fill(x, y, x + s, y + s, lit);
				}
	}

	private static int darker(int argb) {
		int r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;
		return (argb & 0xFF000000) | (r * 4 / 5) << 16 | (gr * 4 / 5) << 8 | b * 4 / 5;
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {} // The panel art has its labels.

	// -------------------- THE LABEL ABOVE THE PANEL --------------------

	/** "146.520", or "154.4375" off the 5 kHz grid. */
	public static String mhz(float hz) {
		long rounded = Math.round(hz / (double)VHFReceiverBlockEntity.STEP_HZ) * VHFReceiverBlockEntity.STEP_HZ;
		return String.format(Locale.ROOT, rounded % 5_000L == 0L ? "%.3f" : "%.4f", rounded / 1_000_000.0D);
	}

	private static String key(String name) {
		return Radiocraft.translationKey("screen", "vhf_receiver." + name);
	}

	private Component channelLine(String kind, int channel) {
		return Component.translatable(key("hint." + kind), channel + 1, mhz(receiver().getChannelHz(channel)));
	}

	/**
	 * What the label over the panel says, or null for nothing: a hovered switch's channel; TUNE's channel while it is
	 * hovered or was just turned (or why it cannot set one); else, running, the channel being set or heard, SCAN while
	 * the scan moves, or that no channel is on.
	 */
	@Nullable
	private Component hint(@Nullable HfPanel.Control hovered) {
		VHFReceiverBlockEntity rx = receiver();
		if(hovered != null && hovered.type().equals("switch"))
			return channelLine("channel", channelOf(hovered));
		boolean tuning = hovered != null && hovered.name().equals("tune") || System.currentTimeMillis() - tunedAt < TUNED_SHOW_MS;
		boolean setKey = hovered != null && hovered.name().equals("key_set");
		if(tuning || setKey && rx.isSetMode()) {
			if(!rx.isSetMode())
				return Component.translatable(key("hint.set_off"));
			if(!rx.isSetLit())
				return Component.translatable(key("hint.set_one"));
			if(!on())
				return Component.translatable(key("hint.off"));
			return channelLine("set", rx.onlyChannel());
		}
		if(!menu.isPowered())
			return null;
		if(menu.getDisplayPhase() != RadioNetworkObject.PHASE_ON)
			return Component.translatable(key("hint.no_power"));
		if(rx.isSetLit())
			return channelLine("set", rx.onlyChannel());
		int channel = rx.getScanChannel();
		if(rx.channelsOnCount() == 0 || channel < 0 || !rx.isChannelOn(channel))
			return Component.translatable(key("hint.no_channels"));
		if(rx.isScanning() && rx.isScanHold())
			return channelLine("hold", channel);
		if(rx.isScanning() && !rx.isScanStopped())
			return Component.translatable(key("hint.scan"));
		return channelLine("channel", channel);
	}

	/** {@code text} in a tooltip's box, centred over the panel just above it (inside the window's top if there is no room). */
	private void renderHint(GuiGraphics g, @Nullable Component text) {
		if(text == null)
			return;
		int w = font.width(text);
		int x = leftPos + (imageWidth - w) / 2, y = Math.max(4, topPos - 16);
		g.pose().pushPose();
		g.pose().translate(0.0F, 0.0F, 400.0F);
		TooltipRenderUtil.renderTooltipBackground(g, x, y, w, font.lineHeight - 1, 0);
		g.drawString(font, text, x, y, 0xFFFFFFFF, false);
		g.pose().popPose();
	}

	/**
	 * What a key under the mouse does, or null. Only the two keys explain themselves: the knobs' tooltips covered the
	 * panel (maintainer, 2026-11-09), and the switches say their channel in the label instead.
	 */
	@Nullable
	private List<Component> tooltip(HfPanel.Control c) {
		VHFReceiverBlockEntity rx = receiver();
		return switch(c.name()) {
			case "key_set" -> List.of(Component.translatable(key(rx.isSetMode() ? "set_in" : "set_out")));
			case "key_man" -> List.of(Component.translatable(key(rx.isScanHold() ? "man" : "auto")));
			default -> null;
		};
	}
}
