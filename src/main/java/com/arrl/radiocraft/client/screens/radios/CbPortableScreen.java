package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.items.CbPortableItem;
import com.arrl.radiocraft.common.network.serverbound.SCbRadioPacket;
import com.arrl.radiocraft.common.radio.cb.CbChannels;
import com.arrl.radiocraft.common.radio.cb.CbRadioState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The CB portable seen from the front (sneak and use it, 2026-11-08), laid out by {@code gui_layouts/cb_portable.json}
 * and drawn by {@link CbPanel}: the channel knob standing on top (seen side on, as on the handhelds), the channel
 * window, the LIGHT button (hold it to light the window), VOLUME (its lowest click is OFF, which switches the radio
 * off and lowers the whip) and SQUELCH (shuts the speaker until the RX meter reads past it), and the push-to-talk bar on the left side. Knobs
 * turn with the wheel over them, a click one step up, a right click one down. Hold right click with the radio in hand
 * to talk.
 */
public class CbPortableScreen extends Screen {

	private final int index;
	private final CbPanel panel = CbPanel.load("cb_portable");
	private int left, top;
	private boolean lightHeld = false;

	public CbPortableScreen(int index) {
		super(Component.translatable(Radiocraft.translationKey("screen", "cb_portable.title")));
		this.index = index;
	}

	public static void open(int index) {
		Minecraft.getInstance().setScreen(new CbPortableScreen(index));
	}

	private ItemStack stack() {
		return Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getInventory().getItem(index);
	}

	private CbRadioState state() {
		return CbPortableItem.state(stack());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		super.init();
		left = (width - panel.width) / 2;
		top = Math.max(12, (height - panel.height) / 2);
	}

	@Override
	public void tick() {
		if(!(stack().getItem() instanceof CbPortableItem))
			onClose(); // It left that slot.
	}

	private final CbPanel.Source source = new CbPanel.Source() {
		@Override
		public double knob(String control) {
			CbRadioState state = state();
			return switch(control) {
				case "CHANNEL" -> (state.channel() - CbChannels.FIRST) / (double)(CbChannels.COUNT - 1);
				case "VOLUME" -> state.volume() / (double)CbRadioState.MAX_KNOB;
				case "SQUELCH" -> state.squelch() / (double)CbRadioState.MAX_KNOB;
				default -> 0.0D;
			};
		}

		@Override
		public boolean toggle(String key) {
			return false;
		}

		@Override
		public double meter(String reads) {
			return 0.0D;
		}

		/** The channel drum always shows its number; it is lit while the radio is on. */
		@Override
		public String digits(String shows) {
			return Integer.toString(state().channel());
		}

		@Override
		public boolean lamp(String lights) {
			return false;
		}

		@Override
		public boolean transmitting() {
			return false;
		}

		@Override
		public boolean on() {
			return CbPortableItem.isOn(stack());
		}

		@Override
		public boolean lightHeld() {
			return lightHeld;
		}
	};

	// -------------------- CONTROLS --------------------

	private void turn(String control, int steps) {
		CbRadioState.Key key = switch(control) {
			case "CHANNEL" -> CbRadioState.Key.CHANNEL;
			case "VOLUME" -> CbRadioState.Key.VOLUME;
			case "SQUELCH" -> CbRadioState.Key.SQUELCH;
			default -> null;
		};
		if(key == null)
			return;
		CbPortableItem.setState(stack(), state().press(key, steps)); // Shown at once; the server sets it for real.
		SCbRadioPacket.portable(index, key, steps);
		if(minecraft != null && minecraft.player != null)
			minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, key == CbRadioState.Key.CHANNEL ? 1.6F : 1.2F);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		CbPanel.Widget widget = panel.at(mouseX, mouseY, left, top);
		if(widget != null && "knob".equals(widget.type()) && scrollY != 0.0D) {
			turn(widget.control(), scrollY > 0.0D ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		CbPanel.Widget widget = panel.at(mouseX, mouseY, left, top);
		if(widget != null && "knob".equals(widget.type()) && (button == 0 || button == 1)) {
			turn(widget.control(), button == 0 ? 1 : -1);
			return true;
		}
		if(widget != null && "light".equals(widget.type()) && button == 0) {
			lightHeld = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(button == 0)
			lightHeld = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		panel.render(g, font, left, top, source);
		CbPanel.Widget widget = panel.at(mouseX, mouseY, left, top);
		if(widget == null)
			return;
		CbRadioState state = state();
		String tip = switch(widget.type()) {
			case "knob" -> switch(widget.control()) {
				case "CHANNEL" -> "CH " + state.channel() + "  " + state.displayStatus();
				case "VOLUME" -> state.volume() == 0 ? "OFF" : CbPortableItem.energy(stack()) <= 0 ? "VOL " + state.volume() + " (flat: swap in a small battery)" : "VOL " + state.volume();
				default -> "SQ " + state.squelch() + (state.squelch() == 0 ? " (open)" : "");
			};
			case "light" -> "Hold to light the channel window";
			case "ptt" -> "Push to talk: hold right click with the radio in hand";
			default -> null;
		};
		if(tip != null)
			g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
	}
}
