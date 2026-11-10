package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.menus.CommercialMobileMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.network.serverbound.SCommercialRadioPacket;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The front panel of a commercial mobile (2026-11-09), drawn as its block is: a dark control head in the block's undyed
 * dark case (the dye is not shown here), each part where the block's head has it, laid out by {@code gui_layouts/commercial_mobile.json} or {@code low_band_mobile.json} over
 * {@code textures/gui/commercial/<radio>.png} (both from {@code tools/commercial_radio_assets.py}). The low band set is
 * the taller one, its speaker across the top.
 * <p>
 * The controls are the upgraded commercial handheld's ({@link CommercialHandheldScreen}):
 * <ul>
 *     <li>PWR/VOL: OFF, 1-10. Turning it to OFF switches the radio off, up from OFF switches it on; 1-10 is the
 *     speaker at 20-200%.</li>
 *     <li>CH: the 16-position channel knob, with stops at 1 and 16.</li>
 *     <li>The LCD: the channel name (or who is talking, or CH n on the low band), the channel and mode, and the
 *     frequency.</li>
 *     <li>The keys: A-D pick that zone (a mobile holds 16 channels, one zone: A is it, B-D say so), * shows the unit
 *     ID and # the channel's frequency for a few seconds; the number keys beep and do nothing more, as on the
 *     handheld.</li>
 *     <li>TX and RX lamps, and the MIC jack (hold to talk; right click takes the hand mic off its hook).</li>
 *     <li>The VHF set's DISMISS key, for a beacon's distress alert on the display.</li>
 * </ul>
 * Knobs turn with the wheel over them, a click one step up and a right click one down; the wheel anywhere else steps
 * the channel. There is nothing to program here: a radio shop does that (CPS or the Radio Cloner).
 */
public class CommercialMobileScreen extends AbstractContainerScreen<CommercialMobileMenu> {

	/** The volume knob's positions above OFF; each is 20% of speaker gain. */
	private static final int VOLUME_POSITIONS = 10;
	/** How long * or # keeps its line on the display, ms. */
	private static final long INFO_MS = 3000L;
	/** How long a power change is trusted over the menu's synced switch, ms (it syncs a tick or two later). */
	private static final long POWER_PENDING_MS = 1000L;

	private final HfPanel panel;
	private boolean pttHeld = false;
	@Nullable
	private String held = null;
	@Nullable
	private String info = null;
	private long infoAt = 0L;
	/** The power switch as last set from here, until the menu's copy catches up. */
	private boolean pendingPower;
	private long pendingPowerAt = Long.MIN_VALUE / 2;

	public CommercialMobileScreen(CommercialMobileMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		panel = HfPanel.loadLayout(menu.blockEntity.model() == CommercialRadioModel.LOW_BAND_MOBILE ? "low_band_mobile" : "commercial_mobile");
		imageWidth = panel.width();
		imageHeight = panel.height();
	}

	private CommercialMobileBlockEntity radio() {
		return menu.blockEntity;
	}

	private boolean vhf() {
		return radio().model() != CommercialRadioModel.LOW_BAND_MOBILE;
	}

	/** The power switch: as set from here a moment ago, else as the menu has it. */
	private boolean switchOn() {
		return System.currentTimeMillis() - pendingPowerAt < POWER_PENDING_MS ? pendingPower : menu.isPowered();
	}

	private boolean on() {
		return switchOn() && radio().wasPowered();
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = true;
	}

	// -------------------- CONTROLS --------------------

	private void click(float pitch) {
		if(minecraft != null && minecraft.player != null)
			minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, pitch);
	}

	/** Where the PWR/VOL knob is: 0 OFF, else 1-10 from the speaker gain. */
	private int volumePosition() {
		if(!switchOn())
			return 0;
		return Math.max(1, Math.min(VOLUME_POSITIONS, Math.round(radio().getSpeakerGain() * VOLUME_POSITIONS / 2.0F)));
	}

	/** The PWR/VOL knob to {@code position} (clamped): through OFF it switches the radio, above it sets the speaker. */
	private void setVolume(int position) {
		int to = Math.max(0, Math.min(VOLUME_POSITIONS, position));
		int now = volumePosition();
		if(to == now)
			return;
		if((to == 0) != (now == 0)) {
			SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.TOGGLE_POWER, true);
			pendingPower = to > 0;
			pendingPowerAt = System.currentTimeMillis();
			click(to > 0 ? 1.2F : 0.9F);
		}
		if(to > 0) {
			int percent = to * 200 / VOLUME_POSITIONS;
			radio().setSpeakerGainClient(percent / 100.0F);
			SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_SPEAKER_GAIN, percent);
			click(1.6F);
		}
	}

	/** The CH knob {@code steps} clicks (it stops at 1 and 16). */
	private void turnChannel(int steps) {
		CommercialProgram program = radio().program();
		CommercialProgram turned = program.turn(steps);
		if(turned.selected() == program.selected())
			return;
		SCommercialRadioPacket.mobile(radio().getBlockPos(), turned.selected());
		click(1.8F);
	}

	private void turn(String knob, int steps) {
		if(knob.equals("volume"))
			setVolume(volumePosition() + steps);
		else
			turnChannel(steps);
	}

	/** A key on the head. */
	private void key(String name) {
		click(2.0F);
		CommercialProgram program = radio().program();
		switch(name) {
			case "key_a", "key_b", "key_c", "key_d" -> {
				int zone = name.charAt(4) - 'a';
				if(zone < radio().model().zones())
					SCommercialRadioPacket.mobile(radio().getBlockPos(), program.zone(zone, radio().model().zones()).selected());
				showInfo(radio().model().hasZones() || zone == 0 ? "ZONE " + CommercialProgram.zoneLetter(zone) : "ZONE A ONLY");
			}
			case "key_star" -> showInfo(program.unitId() > 0 ? CommercialProgram.unitText(program.unitId()) : "NO ID");
			case "key_hash" -> {
				CommercialChannel channel = program.active();
				showInfo(channel == null ? "NO CH" : String.format(Locale.ROOT, "%.4f", channel.rxHz() / 1e6));
			}
			case "dismiss" -> dismissDistress();
			default -> {} // The number keys only beep, as on the handheld.
		}
	}

	private void showInfo(String text) {
		info = text;
		infoAt = System.currentTimeMillis();
	}

	/** The beacon distress alert on the display (VHF): {@link CommercialMobileBlockEntity#distressLines}, or null. */
	@Nullable
	private List<String> distressLines() {
		return radio().distressLines();
	}

	/** DISMISS: takes the distress alert off the display (the server ignores it when none is shown). */
	private void dismissDistress() {
		if(distressLines() != null)
			SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.DISMISS_DISTRESS, true);
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
				case "button" -> {
					if(button == 0) {
						held = c.name();
						key(c.name());
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
		boolean on = on();
		boolean tx = on && radio().isPTTDown() && !radio().isTransmitInhibited();
		boolean rx = on && !tx && radio().getSyncedSignal() > 0.0D;
		for(HfPanel.Control c : panel.controls()) {
			switch(c.type()) {
				case "knob" -> MarinePanels.knob(g, leftPos, topPos, c, c.name().equals("volume")
						? volumePosition() / (double)VOLUME_POSITIONS
						: radio().program().knob() / (double)(CommercialProgram.KNOB_POSITIONS - 1));
				case "lamp" -> {
					if(c.name().equals("tx_lamp") ? tx : c.name().equals("rx_lamp") && rx)
						MarinePanels.lamp(g, leftPos, topPos, c, c.colour("colour", 0xFFFF3020));
				}
				case "button" -> MarinePanels.button(g, leftPos, topPos, c, false, c.name().equals(held));
				case "jack" -> {
					if(pttHeld)
						MarinePanels.jackGlow(g, leftPos, topPos, c);
				}
				case "display" -> {
					if(on)
						renderDisplay(g, c, tx);
					else // Off: the glass goes dark.
						g.fill(leftPos + c.x(), topPos + c.y(), leftPos + c.x() + c.w(), topPos + c.y() + c.h(), 0xB0000000 | (c.colour("off", 0x283020) & 0xFFFFFF));
				}
				default -> {}
			}
		}
	}

	/**
	 * The LCD, three lines: the channel name (or who is talking, or what * or # asked for), the channel and the mode
	 * (TX while transmitting), and the frequency. A distress alert takes the whole display (VHF), in up to four lines.
	 */
	private void renderDisplay(GuiGraphics g, HfPanel.Control c, boolean tx) {
		int ink = c.colour("ink", 0xFF1F2A14);
		float x = leftPos + c.x(), y = topPos + c.y(), w = c.w();
		List<String> distress = vhf() ? distressLines() : null;
		if(distress != null && !distress.isEmpty()) {
			List<String> lines = wrapLines(distress, w - 8);
			int step = lines.size() > 3 ? 9 : 12;
			for(int i = 0; i < lines.size(); i++)
				fitted(g, lines.get(i), x + 4, y + 4 + i * step, w - 8, ink);
			return;
		}
		CommercialChannel channel = radio().getChannel();
		String first = info != null && System.currentTimeMillis() - infoAt < INFO_MS ? info : radio().displayLine();
		fitted(g, first, x + 4, y + 4, w - 8, ink);
		String where = vhf() ? "CH " + (radio().getChannelPosition() + 1) : "LOW BAND";
		String mode = tx ? "TX" : channel == null ? "" : channel.encrypted() ? "ENC" : channel.digital() ? "DIG" : "FM";
		MarinePanels.text(g, font, where, x + 4, y + 16, 1.0F, ink, false);
		MarinePanels.text(g, font, mode, x + w - 4, y + 16, 1.0F, ink, true);
		String frequency = channel == null ? "NO CHANNEL" : String.format(Locale.ROOT, "%.4f MHZ", channel.rxHz() / 1e6);
		fitted(g, frequency, x + 4, y + 28, w - 8, ink);
	}

	/** {@code text} at {@code x, y}, shrunk if it would run past {@code width}. */
	private void fitted(GuiGraphics g, String text, float x, float y, float width, int ink) {
		float scale = Math.min(1.0F, width / Math.max(1.0F, font.width(text)));
		MarinePanels.text(g, font, text, x, y + (1.0F - scale) * 4.0F, scale, ink, false);
	}

	/**
	 * At most four lines (the LCD's height): while there is a spare line, a line wider than {@code width} breaks at its
	 * last space that fits ("DISTRESS DETECTED 161.975" over two); what is still too wide is shrunk when drawn.
	 */
	private List<String> wrapLines(List<String> in, float width) {
		List<String> out = new ArrayList<>(in);
		int i = 0;
		while(i < out.size() && out.size() < 4) {
			String line = out.get(i);
			if(font.width(line) > width && line.indexOf(' ') >= 0) {
				int cut = line.indexOf(' ');
				for(int j = cut; j >= 0; j = line.indexOf(' ', j + 1))
					if(font.width(line.substring(0, j)) <= width)
						cut = j;
				out.set(i, line.substring(0, cut));
				out.add(i + 1, line.substring(cut + 1));
				continue;
			}
			i++;
		}
		return out.size() > 4 ? out.subList(0, 4) : out;
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {} // The panel art has its labels.

	@Nullable
	private String tooltip(String name) {
		return switch(name) {
			case "volume" -> volumePosition() == 0 ? "PWR/VOL: off" : String.format(Locale.ROOT, "PWR/VOL %d (%d%%)", volumePosition(), Math.round(radio().getSpeakerGain() * 100.0F));
			case "channel" -> "CH " + (radio().program().knob() + 1) + " of " + CommercialProgram.KNOB_POSITIONS;
			case "ptt" -> "Hold to talk; right click for the hand mic";
			case "key_a", "key_b", "key_c", "key_d" -> radio().model().hasZones() ? "Zone " + Character.toUpperCase(name.charAt(4)) : "Zones: this radio holds 16 channels, all in zone A";
			case "key_star" -> "*: show the unit ID";
			case "key_hash" -> "#: show the channel's frequency";
			case "dismiss" -> "DISMISS: clear a distress alert from the display";
			default -> null;
		};
	}
}
