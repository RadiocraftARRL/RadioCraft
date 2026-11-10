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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The marine handheld seen from the front (sneak and use it, 2026-11-09), after a 1980s portable: a tall off-white
 * case, laid out by {@code gui_layouts/marine_handheld.json} over {@code textures/gui/marine/marine_handheld.png}
 * (both from {@code tools/marine_radio_assets.py}).
 * <ul>
 *     <li>On top: the rubber duck, the red 16 key, and the VOL/OFF knob (its lowest click switches the radio off; a
 *     click up from off switches it on).</li>
 *     <li>The speaker grille with the LED (red while transmitting, green while a signal comes in) and the plate.</li>
 *     <li>A little LCD with the channel number only, and keys CH down, CH up, 16/9, H/L (lit while on 1 W) and DW (lit
 *     while dual watch is on).</li>
 *     <li>The push-to-talk bar on the left side (hold it; or hold right click with the radio in hand, as always).</li>
 * </ul>
 * FM and channels only. The wheel over the knob turns it; anywhere else it steps the channel.
 */
public class MarineHandheldScreen extends Screen {

	/** The VOL knob's range (the handheld's gain) and its step. */
	private static final float MAX_GAIN = 1.5F, GAIN_STEP = 0.1F;

	private final int index;
	private final HfPanel panel = HfPanel.loadLayout("marine_handheld");
	private int left, top;
	private boolean pttHeld = false;
	@Nullable
	private String held = null;

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
		left = (width - panel.width()) / 2;
		top = Math.max(2, (height - panel.height()) / 2);
	}

	// -------------------- CONTROLS --------------------

	private void press(MarineRadioState.Key key, int value) {
		ItemStack stack = stack();
		if(!MarineHandheldItem.is(stack))
			return;
		// At once on the client (so the display follows without a round trip); the server does the same.
		MarineHandheldItem.setState(stack, MarineHandheldItem.state(stack).press(MarineRadios.region(), key.ordinal(), value));
		SMarineRadioPacket.handheld(index, key, value);
		click(1.8F);
	}

	/** The VOL/OFF knob turned one click: down from its lowest setting switches off, up from off switches on. */
	private void turnVolume(int steps) {
		IVHFHandheldCapability cap = cap();
		if(cap == null)
			return;
		if(!cap.isPowered()) {
			if(steps > 0)
				cap.setPowered(true);
		}
		else if(steps < 0 && cap.getGain() <= GAIN_STEP + 0.001F)
			cap.setPowered(false);
		else
			cap.setGain(Math.max(GAIN_STEP, Math.min(MAX_GAIN, Math.round((cap.getGain() + steps * GAIN_STEP) * 10.0F) / 10.0F)));
		SHandheldRadioUpdatePacket.updateServer(index, cap);
		click(1.2F);
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

	private static void click(float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player != null)
			mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, pitch);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HfPanel.Control c = MarinePanels.at(panel, left, top, mouseX, mouseY);
		if(c != null) {
			switch(c.type()) {
				case "side_ptt" -> {
					if(button == 0) {
						setPtt(true);
						return true;
					}
				}
				case "side_knob" -> {
					if(button == 0 || button == 1) {
						turnVolume(button == 0 ? 1 : -1);
						return true;
					}
				}
				case "button" -> {
					if(button == 0) {
						held = c.name();
						switch(c.name()) {
							case "key_16" -> press(MarineRadioState.Key.SIXTEEN, 0);
							case "key_16_9" -> press(MarineRadioState.Key.SIXTEEN_NINE, 0);
							case "key_up" -> press(MarineRadioState.Key.TURN, 1);
							case "key_down" -> press(MarineRadioState.Key.TURN, -1);
							case "hi_lo" -> press(MarineRadioState.Key.HIGH_LOW, 0);
							case "dual_watch" -> press(MarineRadioState.Key.DUAL_WATCH, 0);
							default -> {}
						}
						return true;
					}
				}
				default -> {}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(button == 0) {
			held = null;
			if(pttHeld)
				setPtt(false);
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY == 0.0D)
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		HfPanel.Control c = MarinePanels.at(panel, left, top, mouseX, mouseY);
		if(c != null && c.type().equals("side_knob"))
			turnVolume(scrollY > 0.0D ? 1 : -1);
		else
			press(MarineRadioState.Key.TURN, scrollY > 0.0D ? 1 : -1);
		return true;
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

	// -------------------- DRAWING --------------------

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		ItemStack stack = stack();
		IVHFHandheldCapability cap = cap();
		if(cap == null || !MarineHandheldItem.is(stack))
			return;
		g.blit(panel.texture(), left, top, 0, 0, panel.width(), panel.height(), panel.width(), panel.height());
		MarineChannels.Region region = MarineRadios.region();
		MarineRadioState state = MarineHandheldItem.state(stack);
		boolean on = cap.isPowered();
		boolean tx = on && cap.isPTTDown() && MarineHandheldItem.mayTransmit(stack);
		boolean rx = on && !tx && cap.getReceiveStrength() > 0.0F;
		for(HfPanel.Control c : panel.controls()) {
			switch(c.type()) {
				case "side_knob" -> {
					if(on)
						MarinePanels.sideKnob(g, left, top, c, cap.getGain() / MAX_GAIN);
				}
				case "lamp" -> {
					if(tx || rx)
						MarinePanels.lamp(g, left, top, c, tx ? c.colour("colour", 0xFFFF3020) : c.colour("rx_colour", 0xFF40E060));
				}
				case "button" -> MarinePanels.button(g, left, top, c,
						on && (c.name().equals("hi_lo") ? !state.highPower() : c.name().equals("dual_watch") && state.dualWatch()), c.name().equals(held));
				case "side_ptt" -> {
					if(pttHeld)
						MarinePanels.sidePttGlow(g, left, top, c);
				}
				case "display" -> { // The channel number only, as big as fits.
					if(on)
						MarinePanels.centred(g, font, left, top, c, state.displayLine(region), 2.0F, c.colour("ink", 0xFF141C14));
				}
				default -> {}
			}
		}
		HfPanel.Control over = MarinePanels.at(panel, left, top, mouseX, mouseY);
		String tip = over == null ? null : tooltip(over.name(), cap, state, region);
		if(tip != null)
			g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
	}

	@Nullable
	private static String tooltip(String name, IVHFHandheldCapability cap, MarineRadioState state, MarineChannels.Region region) {
		return switch(name) {
			case "volume" -> cap.isPowered() ? String.format(Locale.ROOT, "VOL %d%% (turn down past the lowest to switch off)", Math.round(cap.getGain() * 100.0F)) : "OFF (turn up to switch on)";
			case "key_16" -> "16: distress and calling (again: back)";
			case "key_16_9" -> "16/9: to 16, then 9, then back";
			case "key_up" -> "Channel up";
			case "key_down" -> "Channel down";
			case "hi_lo" -> state.highPower() ? "HI: 5 W (1 W on 13, 15, 17, 67)" : "LO: 1 W";
			case "dual_watch" -> state.dualWatch() ? "Dual watch: on (channel 16 too)" : "Dual watch: off";
			case "ptt" -> "Push to talk: hold, or hold right click with the radio in hand";
			default -> null;
		};
	}
}
