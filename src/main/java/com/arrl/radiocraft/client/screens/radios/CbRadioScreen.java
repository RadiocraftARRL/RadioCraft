package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.blockentities.radio.CbRadioBlockEntity;
import com.arrl.radiocraft.common.menus.CbRadioMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.network.serverbound.SCbRadioPacket;
import com.arrl.radiocraft.common.radio.cb.CbChannels;
import com.arrl.radiocraft.common.radio.cb.CbRadioState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The front panel of a CB mobile (2026-11-08): the 5875 or the PC68, laid out by {@code gui_layouts/cb_mobile_*.json}
 * and drawn by {@link CbPanel} (textures under {@code textures/gui/cb/}).
 * <ul>
 *     <li><b>5875</b>: clock (it runs whenever the radio has power to hand), RX signal / TX power meter, channel LED,
 *     ON AIR, SWR / modulation meter. Working: CHANNEL, VOLUME (its lowest click is OFF: the knob is the power
 *     switch), SQUELCH (shuts the speaker until the RX meter reads past it), MOD/SWR, SPEECH COMPRESSION, and the MIC jack (hold to talk).</li>
 *     <li><b>PC68</b>: channel (in its bezel), VOL (the inner knob, OFF at the bottom) and SQ (the outer ring), CH9, the
 *     meter and the RX/TX lamp. Its hand mic is plugged in: hold the jack to talk.</li>
 * </ul>
 * Knobs turn with the wheel over them, a click (one step up) or a right click (one down). The other switches are drawn
 * but do nothing.
 */
public class CbRadioScreen extends AbstractContainerScreen<CbRadioMenu> {

	private final CbPanel panel;
	private boolean pttHeld = false;

	public CbRadioScreen(CbRadioMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		panel = CbPanel.load(menu.blockEntity.model().id());
		imageWidth = panel.width;
		imageHeight = panel.height;
	}

	private CbRadioBlockEntity radio() {
		return menu.blockEntity;
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = true;
	}

	// -------------------- WHAT THE PANEL SHOWS --------------------

	private boolean on() {
		return radio().wasPowered();
	}

	private boolean transmitting() {
		return on() && radio().isPTTDown() && !radio().isTransmitInhibited();
	}

	private final CbPanel.Source source = new CbPanel.Source() {
		@Override
		public double knob(String control) {
			CbRadioState state = radio().getRadioState();
			return switch(control) {
				case "CHANNEL" -> (state.channel() - CbChannels.FIRST) / (double)(CbChannels.COUNT - 1);
				case "VOLUME" -> state.volume() / (double)CbRadioState.MAX_KNOB;
				case "SQUELCH" -> state.squelch() / (double)CbRadioState.MAX_KNOB;
				case "MIC_GAIN" -> radio().getMicGain() / 2.0D; // 0 to 200%, as on the other radios.
				case "FUNCTION" -> state.mode() == CbRadioState.Mode.USB ? 0.5D : state.mode() == CbRadioState.Mode.LSB ? 1.0D : 0.0D;
				case "CLARIFIER" -> (state.clarifier() + CbRadioState.MAX_CLARIFIER) / (2.0D * CbRadioState.MAX_CLARIFIER);
				case "RF_GAIN" -> state.rfGain() / (double)CbRadioState.MAX_KNOB;
				default -> 0.5D;
			};
		}

		@Override
		public boolean toggle(String key) {
			CbRadioState state = radio().getRadioState();
			return switch(key) {
				case "METER" -> state.swrMeter();
				case "COMPRESSOR" -> state.compressor();
				case "CHANNEL_9" -> state.channel() == CbChannels.EMERGENCY;
				case "NB" -> state.nb();
				case "ANL" -> state.anl();
				case "NB_ANL" -> state.nb() && state.anl();
				case "AM_FM" -> state.mode() != CbRadioState.Mode.FM; // Up is AM.
				default -> false;
			};
		}

		@Override
		public double meter(String reads) {
			if(!on())
				return 0.0D;
			boolean tx = transmitting();
			if("signal".equals(reads)) // RX signal while listening, TX power (less the mismatch loss) on the air.
				return tx ? radio().getPowerMeter() : Math.min(1.0D, radio().getSyncedSignal());
			if(!tx) // SWR or modulation: only on the air.
				return 0.0D;
			return radio().getRadioState().swrMeter() ? radio().getSwrMeter() : radio().getModulationLevel();
		}

		@Override
		public String digits(String shows) {
			if("clock".equals(shows)) {
				if(!radio().isSupplied() || minecraft == null || minecraft.level == null)
					return "";
				int[] clock = CbRadioBlockEntity.clock(minecraft.level.getDayTime());
				return String.format(Locale.ROOT, "%d:%02d", clock[0], clock[1]);
			}
			return on() ? radio().displayLine() : "";
		}

		@Override
		public boolean lamp(String lights) {
			boolean pm = minecraft != null && minecraft.level != null && CbRadioBlockEntity.clock(minecraft.level.getDayTime())[2] == 1;
			return switch(lights) {
				case "clock_am" -> radio().isSupplied() && !pm;
				case "clock_pm" -> radio().isSupplied() && pm;
				case "on" -> on();
				case "tx" -> transmitting();
				case "rx_or_tx" -> transmitting() || (on() && radio().getSyncedSignal() > 0.0D);
				case "rx" -> !transmitting() && on() && radio().getSyncedSignal() > 0.0D;
				case "am" -> on() && radio().getRadioState().mode() == CbRadioState.Mode.AM;
				case "usb" -> on() && radio().getRadioState().mode() == CbRadioState.Mode.USB;
				case "lsb" -> on() && radio().getRadioState().mode() == CbRadioState.Mode.LSB;
				case "fm" -> on() && radio().getRadioState().mode() == CbRadioState.Mode.FM;
				default -> false;
			};
		}

		@Override
		public boolean transmitting() {
			return CbRadioScreen.this.transmitting();
		}

		@Override
		public boolean on() {
			return CbRadioScreen.this.on();
		}

		@Override
		public boolean pttHeld() {
			return pttHeld;
		}
	};

	// -------------------- CONTROLS --------------------

	private void turn(CbPanel.Widget widget, double mx, double my, int steps) {
		String control = CbPanel.knobControl(widget, mx, my, leftPos, topPos);
		switch(control) {
			case "CHANNEL" -> SCbRadioPacket.radio(radio().getBlockPos(), CbRadioState.Key.CHANNEL, steps);
			case "VOLUME" -> SCbRadioPacket.radio(radio().getBlockPos(), CbRadioState.Key.VOLUME, steps); // The server switches the power with it.
			case "SQUELCH" -> SCbRadioPacket.radio(radio().getBlockPos(), CbRadioState.Key.SQUELCH, steps);
			case "FUNCTION" -> SCbRadioPacket.radio(radio().getBlockPos(), CbRadioState.Key.FUNCTION, steps);
			case "CLARIFIER" -> SCbRadioPacket.radio(radio().getBlockPos(), CbRadioState.Key.CLARIFIER, steps);
			case "RF_GAIN" -> SCbRadioPacket.radio(radio().getBlockPos(), CbRadioState.Key.RF_GAIN, steps);
			case "MIC_GAIN" -> { // As the other radios' mic gain dials: 10% a click, 0 to 200%.
				int percent = Math.max(0, Math.min(200, Math.round(radio().getMicGain() * 100.0F) + steps * 10));
				radio().setMicGainClient(percent / 100.0F);
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_MIC_GAIN, percent);
			}
			default -> {}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		CbPanel.Widget widget = panel.at(mouseX, mouseY, leftPos, topPos);
		if(scrollY != 0.0D && widget != null && "knob".equals(widget.type())) {
			turn(widget, mouseX, mouseY, scrollY > 0.0D ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		CbPanel.Widget widget = panel.at(mouseX, mouseY, leftPos, topPos);
		if(widget != null) {
			switch(widget.type()) {
				case "ptt" -> {
					if(button == 0) {
						setPtt(true);
						return true;
					}
					if(button == 1) {
						HandMicPtt.take(radio().getBlockPos());
						return true;
					}
				}
				case "knob" -> {
					if(button == 0 || button == 1) {
						turn(widget, mouseX, mouseY, button == 0 ? 1 : -1);
						return true;
					}
				}
				case "toggle" -> {
					if(button == 0 && !"NONE".equals(widget.key())) {
						SCbRadioPacket.radio(radio().getBlockPos(), CbRadioState.Key.valueOf(widget.key()), 0);
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

	// -------------------- DRAWING --------------------

	@Override
	protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		panel.render(graphics, font, leftPos, topPos, source);
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {} // Printed on the panel.

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		CbPanel.Widget widget = panel.at(mouseX, mouseY, leftPos, topPos);
		String tip = widget == null ? null : tooltip(widget, mouseX, mouseY);
		if(tip != null)
			graphics.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
	}

	private String tooltip(CbPanel.Widget widget, double mx, double my) {
		CbRadioState state = radio().getRadioState();
		return switch(widget.type()) {
			case "knob" -> switch(CbPanel.knobControl(widget, mx, my, leftPos, topPos)) {
				case "CHANNEL" -> "CH " + state.channel() + "  " + state.displayStatus();
				case "VOLUME" -> state.volume() == 0 ? "OFF" : "VOL " + state.volume();
				case "SQUELCH" -> "SQ " + state.squelch() + (state.squelch() == 0 ? " (open)" : "");
				case "MIC_GAIN" -> "MIC " + Math.round(radio().getMicGain() * 100.0F) + "%";
				case "FUNCTION" -> "FUNCTION: " + state.mode().name();
				case "CLARIFIER" -> state.clarifier() == 0 ? "CLAR centre" : String.format(Locale.ROOT, "CLAR %+d Hz", state.clarifierHz());
				case "RF_GAIN" -> "RF GAIN " + state.rfGain() + (state.rfGain() == CbRadioState.MAX_KNOB ? " (full)" : "");
				default -> widget.label() + " (not fitted)";
			};
			case "toggle" -> switch(widget.key()) {
				case "NONE" -> widget.label() + " (not fitted)";
				case "NB" -> "Noise blanker: " + (state.nb() ? "ON" : "OFF") + " (blanks sparks and crackle)";
				case "ANL" -> "Auto noise limiter: " + (state.anl() ? "ON" : "OFF") + " (clips noise spikes)";
				case "NB_ANL" -> "NB/ANL: " + (state.nb() && state.anl() ? "ON" : "OFF") + " (cuts sparks and crackle)";
				case "AM_FM" -> state.mode() == CbRadioState.Mode.FM ? "FM" : "AM";
				default -> null;
			};
			case "ptt" -> "Hold to talk; right click for the hand mic";
			default -> null;
		};
	}
}
