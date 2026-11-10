package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.blockentities.radio.MarineRadioBlockEntity;
import com.arrl.radiocraft.common.menus.MarineRadioMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.network.serverbound.SMarineRadioPacket;
import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import com.arrl.radiocraft.common.radio.MarineChannels;
import com.arrl.radiocraft.common.radio.distress.DistressAlert;
import com.arrl.radiocraft.common.radio.distress.DistressLog;
import com.arrl.radiocraft.common.radio.distress.DscCall;
import com.arrl.radiocraft.common.radio.distress.Mmsi;
import com.arrl.radiocraft.common.radio.marine.MarineRadioState;
import com.arrl.radiocraft.common.radio.marine.MarineRadios;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The front panel of the fixed-mount marine radio (2026-11-09), after a 1980s set: a cream case with a recessed black
 * front, laid out by {@code gui_layouts/marine_radio.json} over {@code textures/gui/marine/marine_radio.png} (both from
 * {@code tools/marine_radio_assets.py}).
 * <ul>
 *     <li>The red LED display: the channel big, the plan (USA or INT), HI/LO (RX on a weather channel) and DW, and
 *     under them what the channel is for ({@link MarineChannels#label}).</li>
 *     <li>Switches PWR, HI/LO and DW; TX and RX lamps; knobs VOL (the speaker), SQL (the squelch) and CH (steps the
 *     channels); the MIC jack (hold to talk, right click for the hand mic).</li>
 *     <li>The keypad: 16/9, CH up and down, the red 16 key, and the number keys, which take a channel by number (two
 *     digits, or one and ENT; the US "A" channels by their number; CLR forgets them).</li>
 *     <li>DSC (2026-11-09): the red DISTRESS button under its cover (click the cover to lift it, right click the button
 *     or click the lifted cover to shut it; hold the button 3 s, counting down with a beep a second, to send a distress
 *     call, or to cancel one that is on), NAT (the nature of distress, shown on the display a moment), ACK (acknowledge
 *     the call on the display) and CLR (take an alert off the display). A heard alert takes the display: what it is,
 *     the caller's MMSI or name and the nature, and where it is; the first line flashes while the alarm sounds.</li>
 * </ul>
 * FM and channels only: no mode, no VFO. Knobs turn with the wheel over them, a click one step up, a right click one
 * down; the wheel anywhere else steps the channel.
 */
public class MarineRadioScreen extends AbstractContainerScreen<MarineRadioMenu> {

	/** How long a half-typed channel number waits for its second digit. */
	private static final long ENTRY_TIMEOUT_MS = 4000L;

	private final HfPanel panel = HfPanel.loadLayout("marine_radio");
	private boolean pttHeld = false;
	@Nullable
	private String held = null;
	/** Digits typed on the keypad so far, and when the last came; "--" for a moment after a number with no channel. */
	private String entry = "";
	private long entryAt = 0L;
	/** The DISTRESS cover lifted; when the button was pressed (-1 not held) and the countdown beeps so far. */
	private boolean coverOpen = false;
	private long holdStart = -1L;
	private int holdBeeps = 0;
	/** When NAT was last pressed: the display shows the nature for a moment. */
	private long natureAt = 0L;

	/** How long the DISTRESS button is held to send or cancel, in milliseconds. */
	private static final long HOLD_MS = 3000L;

	public MarineRadioScreen(MarineRadioMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = panel.width();
		imageHeight = panel.height();
	}

	private MarineRadioBlockEntity radio() {
		return menu.blockEntity;
	}

	private boolean on() {
		return menu.isPowered() && radio().wasPowered();
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = true;
	}

	// -------------------- CONTROLS --------------------

	private void press(MarineRadioState.Key key, int value) {
		SMarineRadioPacket.radio(radio().getBlockPos(), key, value);
		click(1.8F);
	}

	private void click(float pitch) {
		if(minecraft != null && minecraft.player != null)
			minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, pitch);
	}

	/** A knob turned {@code steps} clicks. */
	private void turn(String knob, int steps) {
		switch(knob) {
			case "volume" -> {
				int next = Math.max(0, Math.min(200, Math.round(radio().getSpeakerGain() * 100.0F) + 10 * steps));
				radio().setSpeakerGainClient(next / 100.0F);
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, next);
				click(1.2F);
			}
			case "squelch" -> {
				int next = Math.max(0, Math.min(100, Math.round(radio().getSquelchLevel() * 100.0F) + 10 * steps));
				radio().setSquelchClient(next / 100.0F);
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_SQUELCH, next);
				click(1.2F);
			}
			case "channel" -> press(MarineRadioState.Key.TURN, steps);
			default -> {}
		}
	}

	/** A key on the keypad (or a switch flipped). Anything but a number key, CLR or ENT forgets a half-typed number. */
	private void key(String name) {
		boolean digit = name.length() == 5 && name.startsWith("key_") && Character.isDigit(name.charAt(4));
		if(!digit && !name.equals("key_clr") && !name.equals("key_ent"))
			entry = "";
		switch(name) {
			case "power" -> {
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.TOGGLE_POWER, true);
				click(1.0F);
			}
			case "hi_lo" -> press(MarineRadioState.Key.HIGH_LOW, 0);
			case "dual_watch" -> press(MarineRadioState.Key.DUAL_WATCH, 0);
			case "key_16_9" -> press(MarineRadioState.Key.SIXTEEN_NINE, 0);
			case "key_16" -> press(MarineRadioState.Key.SIXTEEN, 0);
			case "key_up" -> press(MarineRadioState.Key.TURN, 1);
			case "key_down" -> press(MarineRadioState.Key.TURN, -1);
			case "key_clr" -> {
				entry = "";
				if(shownAlert() != null)
					press(MarineRadioState.Key.DISMISS, 0); // CLR takes an alert off the display.
				else
					click(1.4F);
			}
			case "key_nature" -> {
				natureAt = System.currentTimeMillis();
				press(MarineRadioState.Key.NATURE, 0);
			}
			case "key_ack" -> press(MarineRadioState.Key.ACK, 0);
			case "key_ent" -> {
				if(isTyping())
					enterChannel();
				click(1.8F);
			}
			default -> {
				if(digit)
					digit(name.charAt(4));
			}
		}
	}

	private boolean isTyping() {
		return !entry.isEmpty() && !entry.equals("--") && System.currentTimeMillis() - entryAt < ENTRY_TIMEOUT_MS;
	}

	private void digit(char d) {
		if(!isTyping())
			entry = "";
		entry += d;
		entryAt = System.currentTimeMillis();
		click(1.8F);
		if(entry.length() >= 2)
			enterChannel();
	}

	/** The typed number to its channel: that number, or its US "A" channel; "--" for a moment if there is neither. */
	private void enterChannel() {
		MarineChannels.Region region = MarineRadios.region();
		String number = Integer.toString(Integer.parseInt(entry));
		MarineChannels.Channel channel = MarineChannels.byName(region, number);
		if(channel == null)
			channel = MarineChannels.byName(region, number + "A");
		if(channel == null) {
			entry = "--";
			entryAt = System.currentTimeMillis();
			return;
		}
		entry = "";
		SMarineRadioPacket.radio(radio().getBlockPos(), MarineRadioState.Key.SELECT, MarineChannels.indexOf(region, channel.name()));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY == 0.0D)
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		HfPanel.Control c = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		turn(c != null && c.type().equals("knob") ? c.name() : "channel", scrollY > 0.0D ? 1 : -1);
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HfPanel.Control c = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		if(c != null) {
			switch(c.type()) {
				case "jack" -> {
					if(button == 0) {
						setPtt(true);
						return true;
					}
					if(button == 1) { // Right click: the hand mic instead.
						HandMicPtt.take(radio().getBlockPos());
						return true;
					}
				}
				case "knob" -> {
					if(button == 0 || button == 1) {
						turn(c.name(), button == 0 ? 1 : -1);
						return true;
					}
				}
				case "switch", "button" -> {
					if(button == 0) {
						held = c.name();
						key(c.name());
						return true;
					}
				}
				case "distress" -> {
					if(button == 1 && coverOpen) { // Right click: shut the cover.
						coverOpen = false;
						holdStart = -1L;
						click(0.8F);
						return true;
					}
					if(button == 0 && !coverOpen) {
						coverOpen = true;
						click(0.9F);
						return true;
					}
					if(button == 0) {
						held = c.name();
						holdStart = System.currentTimeMillis();
						holdBeeps = 0;
						return true;
					}
				}
				case "cover" -> {
					if(button == 0 && coverOpen) {
						coverOpen = false;
						holdStart = -1L;
						click(0.8F);
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
			holdStart = -1L; // Let go before the count: nothing is sent.
			if(pttHeld)
				setPtt(false);
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	/** The DISTRESS button's count while it is held: a beep each second, and the call (or its cancel) at three. */
	@Override
	protected void containerTick() {
		super.containerTick();
		if(holdStart < 0L)
			return;
		if(!on()) {
			holdStart = -1L;
			return;
		}
		long heldMs = System.currentTimeMillis() - holdStart;
		int second = (int)(heldMs / 1000L);
		if(second >= holdBeeps && second < 3) {
			holdBeeps = second + 1;
			beep(1.0F);
		}
		if(heldMs >= HOLD_MS) {
			holdStart = -1L;
			SMarineRadioPacket.radio(radio().getBlockPos(), MarineRadioState.Key.DISTRESS, radio().getDscCall().canCancel() ? 0 : 1);
			beep(1.5F);
		}
	}

	private void beep(float pitch) {
		if(minecraft != null && minecraft.player != null)
			minecraft.player.playSound(RadiocraftSoundEvents.DSC_BEEP.get(), 0.6F, pitch);
	}

	/** Game time on this client, for the synced display and alarm times. */
	private long gameTime() {
		return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
	}

	/** The heard alert on the display, or null. */
	@Nullable
	private DistressLog.Entry shownAlert() {
		return radio().getDistressLog().shown(gameTime());
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
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		HfPanel.Control c = MarinePanels.at(panel, leftPos, topPos, mouseX, mouseY);
		String tip = c == null ? null : tooltip(c.name());
		if(tip != null)
			graphics.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(panel.texture(), leftPos, topPos, 0, 0, panel.width(), panel.height(), panel.width(), panel.height());
		MarineChannels.Region region = MarineRadios.region();
		MarineRadioState state = radio().getRadioState();
		boolean on = on();
		boolean tx = on && radio().isPTTDown() && !radio().isTransmitInhibited();
		boolean rx = on && !tx && radio().getSyncedSignal() > 0.0D;
		for(HfPanel.Control c : panel.controls()) {
			switch(c.type()) {
				case "knob" -> MarinePanels.knob(g, leftPos, topPos, c, switch(c.name()) {
					case "volume" -> radio().getSpeakerGain() / 2.0D;
					case "squelch" -> radio().getSquelchLevel();
					case "channel" -> MarineChannels.indexOf(region, state.channel(region).name()) / (double)Math.max(1, MarineChannels.table(region).size() - 1);
					default -> 0.0D;
				});
				case "switch" -> MarinePanels.switchHandle(g, leftPos, topPos, c, switch(c.name()) {
					case "power" -> menu.isPowered() ? "ON" : "OFF";
					case "hi_lo" -> state.highPower() ? "HI" : "LO";
					case "dual_watch" -> state.dualWatch() ? "ON" : "OFF";
					default -> "";
				});
				case "lamp" -> {
					if(c.name().equals("tx_lamp") ? tx : c.name().equals("rx_lamp") && rx)
						MarinePanels.lamp(g, leftPos, topPos, c, c.colour("colour", 0xFFFF3020));
				}
				case "button" -> MarinePanels.button(g, leftPos, topPos, c, false, c.name().equals(held));
				case "distress" -> {
					MarinePanels.cover(g, leftPos, topPos, c, coverOpen);
					if(holdStart >= 0L)
						MarinePanels.button(g, leftPos, topPos, c, false, true);
				}
				case "jack" -> {
					if(pttHeld)
						MarinePanels.jackGlow(g, leftPos, topPos, c);
				}
				case "display" -> {
					if(on)
						renderDisplay(g, c, state, region);
				}
				default -> {}
			}
		}
	}

	/**
	 * The LED window: the channel at twice size top left (or the digits being typed), the plan and HI/LO/RX and DW down
	 * the right, and what the channel is for along the bottom, shrunk if it would run past the window.
	 */
	private void renderDisplay(GuiGraphics g, HfPanel.Control c, MarineRadioState state, MarineChannels.Region region) {
		int ink = c.colour("ink", 0xFFFF4030);
		float x = leftPos + c.x(), y = topPos + c.y(), w = c.w(), h = c.h();
		String[] dsc = dscLines();
		if(dsc != null) { // DSC has the display: three lines, each shrunk to fit; the first flashes while the alarm sounds.
			boolean flash = radio().isAlarming(gameTime()) && (System.currentTimeMillis() / 400L) % 2L == 1L;
			for(int i = 0; i < dsc.length; i++) {
				if(i == 0 && flash)
					continue;
				float scale = Math.min(1.0F, (w - 8) / Math.max(1.0F, font.width(dsc[i])));
				MarinePanels.text(g, font, dsc[i], x + 4, y + 3 + i * 11 + (1.0F - scale) * 4.0F, scale, ink, false);
			}
			return;
		}
		MarineChannels.Channel channel = state.channel(region);
		boolean typing = isTyping() || entry.equals("--") && System.currentTimeMillis() - entryAt < 1200L;
		String big = typing ? (entry.length() == 1 ? entry + "-" : entry) : channel.name();
		MarinePanels.text(g, font, big, x + 4, y + 3, 2.0F, ink, false);
		MarinePanels.text(g, font, region == MarineChannels.Region.US ? "USA" : "INT", x + w - 3, y + 3, 1.0F, ink, true);
		String flags = (channel.isReceiveOnly() ? "RX" : state.transmitsHigh(region) ? "HI" : "LO") + (state.dualWatch() ? " DW" : "");
		MarinePanels.text(g, font, flags, x + w - 3, y + 12, 1.0F, ink, true);
		String name = typing ? (entry.equals("--") ? "NO CHANNEL" : "ENTER CH")
				: System.currentTimeMillis() - natureAt < 3000L ? "NAT " + radio().getNature().shortLabel() // NAT pressed a moment ago.
				: MarineChannels.label(region, channel.name());
		float scale = Math.min(1.0F, (w - 8) / Math.max(1.0F, font.width(name)));
		MarinePanels.text(g, font, name, x + 4, y + h - 11 + (1.0F - scale) * 4.0F, scale, ink, false);
	}

	/**
	 * What DSC puts on the display, three lines, or null to show the channel: the DISTRESS button's count while held, a
	 * heard alert while it is shown, else the radio's own call while it is on or acknowledged.
	 */
	@Nullable
	private String[] dscLines() {
		DscCall call = radio().getDscCall();
		if(holdStart >= 0L) {
			long left = Math.max(1L, 3L - (System.currentTimeMillis() - holdStart) / 1000L);
			return call.canCancel() ? new String[] { "CANCEL DISTRESS", "HOLD " + left, "" }
					: new String[] { "SEND DISTRESS", "HOLD " + left, radio().getNature().label() };
		}
		DistressLog.Entry shown = shownAlert();
		if(shown != null) {
			DistressAlert a = shown.alert();
			String head = switch(a.state()) {
				case ACTIVE -> a.isDsc() ? "DSC DISTRESS" : "BEACON ALERT";
				case ACKNOWLEDGED -> "DSC ACK " + a.ackBy();
				case CANCELLED -> "DSC CANCELLED";
				case SPENT -> "BEACON SPENT";
			};
			String who = a.isDsc() ? a.owner() + " " + a.nature().shortLabel() : a.owner();
			return new String[] { head, who, "X " + a.x() + " Z " + a.z() };
		}
		return switch(call.state()) {
			case ACTIVE -> {
				long ticks = call.ticksToRepeat(gameTime());
				yield new String[] { "DISTRESS SENT", call.nature().label(), String.format(Locale.ROOT, "NEXT RPT %d:%02d", ticks / 1200L, ticks / 20L % 60L) };
			}
			case ACKNOWLEDGED -> new String[] { "ACKNOWLEDGED", "BY " + call.ackBy(), "STAY ON CH 16" };
			default -> null;
		};
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {} // The panel art has its labels.

	@Nullable
	private String tooltip(String name) {
		MarineChannels.Region region = MarineRadios.region();
		MarineRadioState state = radio().getRadioState();
		return switch(name) {
			case "volume" -> String.format(Locale.ROOT, "VOL %d%%", Math.round(radio().getSpeakerGain() * 100.0F));
			case "squelch" -> radio().getSquelchLevel() <= 0.0F ? "SQL open" : String.format(Locale.ROOT, "SQL %d", Math.round(radio().getSquelchLevel() * 10.0F));
			case "channel" -> "CH " + state.displayLine(region) + ": " + state.channel(region).use();
			case "power" -> menu.isPowered() ? "Power: on" : "Power: off";
			case "hi_lo" -> state.highPower() ? "HI: 25 W (1 W on 13, 15, 17, 67)" : "LO: 1 W";
			case "dual_watch" -> state.dualWatch() ? "Dual watch: on (channel 16 too)" : "Dual watch: off";
			case "key_16_9" -> "16/9: to 16, then 9, then back";
			case "key_16" -> "16: distress and calling (again: back)";
			case "key_up" -> "Channel up";
			case "key_down" -> "Channel down";
			case "key_clr" -> "Clear the number";
			case "key_ent" -> "Enter a one-digit channel";
			case "ptt" -> "Hold to talk; right click for the hand mic";
			case "key_nature" -> "NAT: nature of distress for your call: " + radio().getNature().label();
			case "key_ack" -> ackTooltip();
			case "distress" -> !coverOpen ? "DISTRESS: click to lift the cover"
					: radio().getDscCall().canCancel() ? "Hold 3 s to cancel your distress call; right click shuts the cover"
					: "Hold 3 s to send a distress call as MMSI " + myMmsi() + "; right click shuts the cover";
			case "distress_cover" -> coverOpen ? "Click to shut the cover" : null;
			default -> name.startsWith("key_") ? "Type a channel: two digits, or one and ENT" : null;
		};
	}

	private String ackTooltip() {
		DistressLog.Entry shown = shownAlert();
		if(shown != null && shown.alert().isDsc() && shown.alert().state() == DistressAlert.State.ACTIVE)
			return "ACK: acknowledge the distress call from " + shown.alert().owner();
		return "ACK: acknowledge the distress call on the display";
	}

	/** The viewer's MMSI, the number a call from them carries. */
	private String myMmsi() {
		return minecraft != null && minecraft.player != null ? Mmsi.of(minecraft.player.getUUID()) : "";
	}
}
