package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.client.screens.widgets.ToggleButton;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.menus.RadioMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.radio.SWRHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The HF radios' new front panels (2026-11-09, approved by the maintainer): one screen for the 10m, 20m, 40m, 80m and
 * all-band radios, drawn from an {@link HfPanel}. The panel PNG is the art; this draws what moves (knob pointers, switch
 * handles, the mode lever, the tuning dial's dimple, the meter's needle, lamps and lit keys) and prints the frequency.
 * <ul>
 *     <li>Knobs: scroll, or left click up and right click down. AF and MIC 0 to 200%, SQL against the S-meter.</li>
 *     <li>Tuning dial: scroll, or drag up and down.</li>
 *     <li>Switches and the mode lever: click a position, or scroll through them.</li>
 *     <li>The meter reads S on receive, and S/PO/SWR on the air as its selector says (remembered per radio).</li>
 *     <li>PTT: hold; right click for the hand mic.</li>
 * </ul>
 * The old screens ({@link HFRadio10mScreen} and the rest, on their {@code _old} textures) are kept for now.
 */
public class HfPanelScreen<T extends RadioMenu<?>> extends HFRadioScreen<T> {

	private static final String[] AGC_NAMES = { "OFF", "SLOW", "FAST" };
	/** What the meter shows on the air (on receive it is always the S-meter). */
	private static final String[] METER_NAMES = { "PO", "SWR" };
	private static final String[] MODES = { "SSB", "CW", "AM" };
	/** The meter selector each radio was left on, by its position (client only): 0 PO, 1 SWR. */
	private static final Map<Long, Integer> METER_MODE = new HashMap<>();
	/** Radios with the 10x coarse tuning key latched, by position (client only). */
	private static final Map<Long, Boolean> COARSE = new HashMap<>();
	/** The way each lever or knob3 last moved on a left click, so clicking on walks it back from its end. */
	private final Map<String, Integer> clickDirection = new HashMap<>();
	/** Degrees the tuning dial turns per frequency step, and the drag that makes a step. */
	private static final double DIAL_DEG_PER_STEP = 12.0D, DRAG_PER_STEP = 5.0D;
	/** The highest SWR at which the antenna lamp shows a good match. */
	private static final double GOOD_SWR = 2.0D;

	private final HfPanel panel;
	/** The button being held down (shown pressed), null for none. */
	@Nullable
	private String held;
	/** When {@link #held} went down, and whether its hold action (QUICK, MEMORY, VFO/MEM) has fired. */
	private long heldSince;
	private boolean holdFired;
	/** How long a key is held for its second job (QUICK store, MEMORY write, VFO/MEM copy), ms. */
	private static final long HOLD_MS = 700L;
	/** Writing a memory (MEMORY held on the VFO): the channel the dial is on, -1 when not writing. */
	private int memoryWrite = -1;
	/** A word flashed in the display after a hold ("QUICK SET", "M07 SET"), until this time. */
	@Nullable
	private String flash;
	private long flashUntil;
	private boolean draggingDial;
	private double dragAccum, dialDeg = -135.0D, needle;

	public HfPanelScreen(T menu, Inventory inventory, Component title, String radio) {
		this(menu, inventory, title, HfPanel.load(radio));
	}

	private HfPanelScreen(T menu, Inventory inventory, Component title, HfPanel panel) {
		super(menu, inventory, title, panel.texture(), panel.texture());
		this.panel = panel;
		this.imageWidth = panel.width();
		this.imageHeight = panel.height();
		this.textureWidth = panel.width();
		this.textureHeight = panel.height();
		this.displayTextDrop = 4.0F; // Below the mode and TX markers.
	}

	private boolean coarse() {
		return COARSE.getOrDefault(radio().getBlockPos().asLong(), false);
	}

	/** SSB lights only when no other voice mode is on (AM shares the voice path). */
	@Override
	protected boolean isSSBSelected() {
		return ssbOn();
	}

	/**
	 * The screen for {@code radio}'s menu, to register: {@code gui_layouts/hf_<radio>.json}, or for a Premium radio
	 * {@code hf_<radio>_pure.json} where it has a panel of its own (the Swan, TS-520R, RC-K2 and RTX-100, 2026-11-11).
	 */
	public static <M extends RadioMenu<?>> net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<M, HfPanelScreen<M>> of(String radio) {
		return (menu, inventory, title) -> {
			String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(menu.blockEntity.getBlockState().getBlock()).getPath();
			String own = radio + "_pure";
			return new HfPanelScreen<>(menu, inventory, title, path.equals(own) && HfPanel.exists(own) ? own : radio);
		};
	}

	@Nullable
	private HFRadioBlockEntity hf() {
		return radio() instanceof HFRadioBlockEntity h ? h : null;
	}

	private void setRit(int steps, boolean on) {
		HFRadioBlockEntity hf = hf();
		if(hf == null)
			return;
		hf.setRit(steps, on);
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_RIT, hf.getRitSteps(), on);
	}

	private void setNoiseBlanker(boolean on) {
		HFRadioBlockEntity hf = hf();
		if(hf == null || hf.isNoiseBlanker() == on)
			return;
		hf.setNoiseBlanker(on);
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_NB, on);
	}

	private void setRfGain(int value) {
		HFRadioBlockEntity hf = hf();
		if(hf == null)
			return;
		hf.setRfGain(value);
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_RF_GAIN, hf.getRfGain());
	}

	private RadioBlockEntity radio() {
		return menu.blockEntity;
	}

	@Nullable
	private com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity vhf() {
		return radio() instanceof com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity v ? v : null;
	}

	private boolean amOn() {
		return hfRadio() != null && hfRadio().getAMEnabled();
	}

	private boolean ssbOn() {
		return hfRadio() != null && hfRadio().isSSBSelected();
	}

	/** The VHF radio's repeater offset as its knob shows it: -, S (simplex) or +. */
	private String offsetPosition() {
		com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity v = vhf();
		int offset = v == null ? 0 : v.getOffsetHz();
		return offset < 0 ? "-" : offset > 0 ? "+" : "S";
	}

	private void setOffset(String position) {
		com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity v = vhf();
		if(v == null)
			return;
		int offset = position.equals("-") ? -com.arrl.radiocraft.common.radio.voice.Ctcss.REPEATER_OFFSET_HZ
				: position.equals("+") ? com.arrl.radiocraft.common.radio.voice.Ctcss.REPEATER_OFFSET_HZ : 0;
		if(offset == v.getOffsetHz())
			return;
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_OFFSET, offset);
		v.setOffsetHz(offset);
	}

	private void stepTone(int steps) {
		com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity v = vhf();
		if(v == null)
			return;
		float tone = com.arrl.radiocraft.common.radio.voice.Ctcss.step(v.getCtcssTone(), steps);
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_TONE, com.arrl.radiocraft.common.radio.voice.Ctcss.index(tone));
		v.setCtcssTone(tone);
	}

	@Nullable
	private HFRadioAllBandBlockEntity allBand() {
		return radio() instanceof HFRadioAllBandBlockEntity a ? a : null;
	}

	/** The radio's memories, if it has them (the all-band radio, the VHF base station). */
	@Nullable
	private com.arrl.radiocraft.common.blockentities.radio.MemoryRadio memories() {
		return radio() instanceof com.arrl.radiocraft.common.blockentities.radio.MemoryRadio m ? m : null;
	}

	private int meterMode() {
		return METER_MODE.getOrDefault(radio().getBlockPos().asLong(), 0);
	}

	// -------------------- STATE --------------------

	/** Where a knob is, 0 to 1. */
	private double knobValue(String name) {
		return switch(name) {
			case "af_gain" -> radio().getSpeakerGain() / 2.0D;
			case "mic_gain" -> radio().getMicGain() / 2.0D;
			case "squelch" -> radio().squelchKnob();
			case "rit" -> hf() == null ? 0.5D : (hf().getRitSteps() + HFRadioBlockEntity.MAX_RIT_STEPS) / (2.0D * HFRadioBlockEntity.MAX_RIT_STEPS);
			case "rf_gain" -> hf() == null ? 1.0D : hf().getRfGain() / (double)HFRadioBlockEntity.MAX_RF_GAIN;
			default -> 0.5D;
		};
	}

	/** The position a switch, knob3 or lever is in, by name. */
	@Nullable
	private String position(String name) {
		return switch(name) {
			case "agc" -> AGC_NAMES[Math.max(0, Math.min(2, radio().getAgc()))];
			case "meter_select" -> METER_NAMES[meterMode()];
			case "power" -> menu.isPowered() ? "ON" : "OFF";
			case "mode_lever" -> cwOn() ? "CW" : amOn() ? "AM" : "SSB";
			case "offset" -> offsetPosition();
			case "nb" -> hf() != null && hf().isNoiseBlanker() ? "ON" : "OFF";
			case "rit_switch" -> hf() != null && hf().isRitOn() ? "ON" : "OFF";
			default -> null;
		};
	}

	private boolean selected(String name) {
		HFRadioAllBandBlockEntity allBand = allBand();
		return switch(name) {
			case "mode_ssb" -> ssbOn();
			case "mode_cw" -> cwOn();
			case "mode_am" -> amOn();
			case "mode_fm" -> allBand != null && allBand.getFMEnabled();
			case "meter_po" -> meterMode() == 0;
			case "meter_swr" -> meterMode() == 1;
			case "coarse" -> coarse();
			case "quick" -> radio().isOnQuick();
			case "vfo_mem" -> memories() != null && memories().isMemoryMode();
			case "memory" -> memoryWrite >= 0;
			case "rit_on" -> hf() != null && hf().isRitOn();
			case "nb_on" -> hf() != null && hf().isNoiseBlanker();
			default -> false;
		};
	}

	private boolean lampLit(String name) {
		boolean on = menu.getDisplayPhase() == RadioNetworkObject.PHASE_ON;
		return switch(name) {
			case "on_lamp", "pilot_lamp" -> menu.getDisplayPhase() != RadioNetworkObject.PHASE_OFF;
			case "tx_lamp" -> on && isTransmitting();
			case "rx_lamp", "busy_lamp" -> on && !isTransmitting() && radio().getSyncedSignal() > 0.0D;
			case "ant_lamp" -> on && radio().getAntennaSWR() > 0.01D && radio().getAntennaSWR() <= GOOD_SWR;
			case "mode_ssb_lamp" -> on && selected("mode_ssb");
			case "mode_cw_lamp" -> on && selected("mode_cw");
			case "mode_am_lamp" -> on && selected("mode_am");
			case "rit_lamp" -> on && hf() != null && hf().isRitOn();
			case "nb_lamp" -> on && hf() != null && hf().isNoiseBlanker();
			default -> false;
		};
	}

	/**
	 * What the meter reads, 0 to 1. On receive the S-meter: the strongest station or the band's noise, whichever is
	 * higher (so the static is never louder than the meter says). On the air PO or SWR, and only while power goes out:
	 * SSB only while there is speech (or CW or data), AM and FM whenever keyed.
	 */
	private double meterTarget() {
		if(menu.getDisplayPhase() != RadioNetworkObject.PHASE_ON)
			return 0.0D;
		if(isTransmitting()) {
			if(!radio().isPowerOut())
				return 0.0D;
			double swr = radio().getAntennaSWR() <= 0.01D ? RadioBlockEntity.NO_ANTENNA_SWR : radio().getAntennaSWR();
			return meterMode() == 1 ? Math.max(0.0D, Math.min(1.0D, (swr - 1.0D) / 2.0D)) : 0.85D * SWRHelper.getEfficiencyMultiplier(swr);
		}
		return Math.min(1.0D, Math.max(radio().getSyncedSignal(), radio().getMeterNoise()));
	}

	// -------------------- DRAWING --------------------

	@Override
	protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

	@Override
	protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {}

	/** Fires a held key's second job once it has been down long enough. */
	private void tickHold() {
		if(held == null || holdFired || System.currentTimeMillis() - heldSince < HOLD_MS)
			return;
		com.arrl.radiocraft.common.blockentities.radio.MemoryRadio memories = memories();
		switch(held) {
			case "quick" -> {
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.QUICK_STORE, true);
				flash("QUICK SET");
			}
			case "memory" -> {
				if(memories == null || memories.isMemoryMode())
					return;
				memoryWrite = memories.getMemoryChannel(); // Pick a channel with the dial, press MEMORY to store.
			}
			case "vfo_mem" -> {
				if(memories == null || !memories.isMemoryMode())
					return;
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.MEM_TO_VFO, true);
				flash("TO VFO");
			}
			default -> {
				return;
			}
		}
		holdFired = true;
		click();
	}

	/** A held key let go before its hold: its plain press. */
	private void shortPress(String name) {
		com.arrl.radiocraft.common.blockentities.radio.MemoryRadio memories = memories();
		switch(name) {
			case "quick" -> SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.QUICK_TOGGLE, true);
			case "vfo_mem" -> {
				memoryWrite = -1;
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.VFO_MEM, true);
			}
			case "memory" -> {
				if(memoryWrite >= 0 && memories != null) { // Writing: this press stores it.
					SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.MEM_STORE, memoryWrite);
					flash(String.format(Locale.ROOT, "M%02d SET", memoryWrite + 1));
					memoryWrite = -1;
				}
			}
			default -> {
				return;
			}
		}
		click();
	}

	private void flash(String text) {
		flash = text;
		flashUntil = System.currentTimeMillis() + 1200L;
	}

	@Override
	protected void renderAdditionalBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		tickHold();
		needle += (meterTarget() - needle) * 0.25D;
		for(HfPanel.Control c : panel.controls()) {
			switch(c.type()) {
				case "knob" -> {
					double deg = c.num("min_deg", -135) + knobValue(c.name()) * (c.num("max_deg", 135) - c.num("min_deg", -135));
					pointer(g, c, deg);
				}
				case "knob3" -> {
					int i = c.strings("positions").indexOf(position(c.name()));
					double[] angles = c.nums("angles_deg");
					if(i >= 0 && i < angles.length)
						pointer(g, c, angles[i]);
				}
				case "dial" -> {
					double r = c.num("mark_r", 10), size = c.num("mark_size", 4);
					double mx = c.num("cx", 0) + r * Math.sin(Math.toRadians(dialDeg)), my = c.num("cy", 0) - r * Math.cos(Math.toRadians(dialDeg));
					int x0 = (int)Math.floor(mx - size / 2.0D), y0 = (int)Math.floor(my - size / 2.0D), s = (int)Math.round(size);
					fillPanel(g, x0 + 1, y0 + 1, s, s, c.colour("mark_shadow", 0xFF101010));
					fillPanel(g, x0, y0, s, s, c.colour("mark_colour", 0xFF303030));
				}
				case "switch" -> {
					int i = c.strings("positions").indexOf(position(c.name()));
					List<int[]> handles = c.rects("handles");
					if(i >= 0 && i < handles.size()) {
						int[] h = handles.get(i);
						fillPanel(g, h[0], h[1], h[2], h[3], c.colour("handle_shadow", 0xFF606060));
						fillPanel(g, h[0], h[1], h[2] - 1, h[3] - 1, c.colour("handle_colour", 0xFFC8C8C8));
					}
				}
				case "lever" -> {
					int i = c.strings("positions").indexOf(position(c.name()));
					double[] angles = c.nums("angles_deg");
					if(i >= 0 && i < angles.length) {
						double deg = angles[i], length = c.num("length", 10);
						ray(g, c.num("cx", 0), c.num("cy", 0), deg, 0, length, (int)c.num("width", 2), c.colour("colour", 0xFFC0C0C0), null);
						int tip = (int)c.num("tip_size", 3);
						double tx = c.num("cx", 0) + length * Math.sin(Math.toRadians(deg)), ty = c.num("cy", 0) - length * Math.cos(Math.toRadians(deg));
						fillPanel(g, (int)Math.floor(tx - tip / 2.0D), (int)Math.floor(ty - tip / 2.0D), tip, tip, c.colour("tip_colour", 0xFFE0E0E0));
					}
				}
				case "button" -> {
					int[] lit = c.lit();
					if(lit != null && selected(c.name()))
						fillPanel(g, lit[0], lit[1], lit[2], lit[3], lit[4]);
					if(c.name().equals(held))
						fillPanel(g, c.x(), c.y(), c.w(), c.h(), 0x55000000); // Pressed in.
				}
				case "lamp" -> {
					if(lampLit(c.name())) {
						int colour = c.colour("colour", 0xFFFF3020);
						fillPanel(g, c.x(), c.y(), c.w(), c.h(), colour);
						if(c.w() >= 3 && c.h() >= 3)
							fillPanel(g, c.x() + 1, c.y() + 1, c.w() - 2, c.h() - 2, lighter(colour));
					}
				}
				case "slide_rule" -> slideRule(g, c); // The Swan's dial window (2026-11-11): the scale moves under the hairline.
				case "bar" -> { // An LED or LCD bar meter (the RC-K2, the RTX-100, 2026-11-11): as many segments lit as the needle reads.
					List<int[]> segments = c.rects("segments");
					List<String> colours = c.strings("colours");
					int lit = (int)Math.ceil(needle * segments.size() - 0.05D);
					for(int i = 0; i < lit && i < segments.size(); i++) {
						int[] r = segments.get(i);
						int colour = i < colours.size() ? HfPanel.parseColour(colours.get(i), 0xFF40FF40) : 0xFF40FF40;
						fillPanel(g, r[0], r[1], r[2], r[3], colour);
					}
				}
				case "meter" -> {
					double[] pivot = c.nums("pivot");
					if(pivot.length == 2) {
						double deg = c.num("zero_deg", -50) + needle * (c.num("full_deg", 50) - c.num("zero_deg", -50));
						ray(g, pivot[0], pivot[1], deg, 0, c.num("radius", 30), 1, c.colour("needle_colour", 0xFF202020), new int[] { c.x(), c.y(), c.w(), c.h() });
					}
				}
				default -> {}
			}
		}
	}

	/** Digits 0 to 9 of the panels' 3 x 5 pixel lettering (tools/hf_gui_proposals/hf_10m_20m_kit.py), a row at a time. */
	private static final String[] DIGITS = { "111101101101111", "010110010010111", "110001010100111", "110001010001110", "101101111001001",
			"111100110001110", "011100111101111", "111001010010010", "111101111101111", "111101111001110" };

	/**
	 * A slide-rule dial: an arc of ticks (every 10 kHz, longer every 50) and numbers (every 100 kHz, the MHz times ten:
	 * "141" is 14.1 MHz) turned so the dial frequency sits under a fixed red hairline at the window's centre. Fields: the
	 * window (x, y, w, h), the arc's centre (cx, cy) and radius (r), deg_per_khz, ink and hairline colours.
	 */
	private void slideRule(GuiGraphics g, HfPanel.Control c) {
		double cx = c.num("cx", c.x() + c.w() / 2.0D), cy = c.num("cy", c.y() + c.h() + 46), r = c.num("r", 58), perKhz = c.num("deg_per_khz", 0.1452D);
		int ink = c.colour("ink", 0xFF28241E), hair = c.colour("hairline", 0xFFAA1E18);
		double khz = radio().getFrequency() / 1000.0D;
		double span = 34.0D / perKhz; // kHz either side of the hairline that can show.
		long first = (long)Math.floor((khz - span) / 10.0D) * 10L, last = (long)Math.ceil((khz + span) / 10.0D) * 10L;
		for(long m = first; m <= last; m += 10L) {
			double a = Math.toRadians(270.0D + (m - khz) * perKhz);
			int len = m % 50L == 0L ? 4 : 2;
			for(int k = (int)r; k < r + len; k++)
				slidePixel(g, c, cx, cy, a, k, ink);
			if(m % 100L == 0L) {
				String label = Long.toString(Math.round(m / 100.0D));
				int tx = (int)Math.round(cx + Math.cos(a) * (r - 6) - (label.length() * 4 - 1) / 2.0D), ty = (int)Math.round(cy + Math.sin(a) * (r - 6) - 2);
				for(int i = 0; i < label.length(); i++) {
					String glyph = DIGITS[label.charAt(i) - '0'];
					for(int gy = 0; gy < 5; gy++)
						for(int gx = 0; gx < 3; gx++)
							if(glyph.charAt(gy * 3 + gx) == '1')
								slideFill(g, c, tx + i * 4 + gx, ty + gy, ink);
				}
			}
		}
		fillPanel(g, c.x() + c.w() / 2, c.y(), 1, c.h(), hair); // The hairline, over the scale.
	}

	private void slidePixel(GuiGraphics g, HfPanel.Control c, double cx, double cy, double a, int k, int colour) {
		slideFill(g, c, (int)Math.round(cx - 0.5D + Math.cos(a) * k), (int)Math.round(cy - 0.5D + Math.sin(a) * k), colour);
	}

	/** One pixel of the dial, kept inside its window. */
	private void slideFill(GuiGraphics g, HfPanel.Control c, int x, int y, int colour) {
		if(x >= c.x() && x < c.x() + c.w() && y >= c.y() && y < c.y() + c.h())
			fillPanel(g, x, y, 1, 1, colour);
	}

	private void pointer(GuiGraphics g, HfPanel.Control c, double deg) {
		ray(g, c.num("cx", 0), c.num("cy", 0), deg, c.num("pointer_from", 0), c.num("pointer_r", 6), (int)c.num("pointer_width", 1), c.colour("pointer_colour", 0xFFE0E0E0), null);
	}

	/** A pixel line from {@code from} to {@code to} out from (cx, cy) at {@code deg}, optionally clipped to {x, y, w, h}. */
	private void ray(GuiGraphics g, double cx, double cy, double deg, double from, double to, int width, int colour, @Nullable int[] clip) {
		double sin = Math.sin(Math.toRadians(deg)), cos = Math.cos(Math.toRadians(deg));
		int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
		for(double t = from; t <= to; t += 0.35D) {
			int x = (int)Math.floor(cx + t * sin), y = (int)Math.floor(cy - t * cos);
			if(x == lastX && y == lastY)
				continue;
			lastX = x;
			lastY = y;
			if(clip != null && (x < clip[0] || y < clip[1] || x >= clip[0] + clip[2] || y >= clip[1] + clip[3]))
				continue;
			fillPanel(g, x, y, Math.max(1, width), Math.max(1, width), colour);
		}
	}

	private void fillPanel(GuiGraphics g, int x, int y, int w, int h, int colour) {
		g.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, colour);
	}

	private static int lighter(int argb) {
		int r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;
		return (argb & 0xFF000000) | ((r + 255) / 2) << 16 | ((gr + 255) / 2) << 8 | (b + 255) / 2;
	}

	@Override
	protected void renderAdditionalTooltips(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		HfPanel.Control display = panel.named("display");
		com.arrl.radiocraft.common.blockentities.radio.MemoryRadio memories = memories();
		boolean on = menu.getDisplayPhase() == RadioNetworkObject.PHASE_ON;
		if(display != null && on && (memoryWrite >= 0 && memories != null || flash != null && System.currentTimeMillis() < flashUntil)) {
			int ink = display.colour("ink", 0xFFFFFFFF) & 0xFFFFFF;
			float cx = leftPos + display.x() + display.w() / 2.0F, cy = topPos + display.y() + display.h() / 2.0F + displayTextDrop;
			if(memoryWrite >= 0 && memories != null) { // Writing a memory: its number and settings top left, what it holds now.
				float hz = memories.getMemoryHz(memoryWrite);
				String top = String.format(Locale.ROOT, "MW %02d", memoryWrite + 1) + (hz <= 0.0F ? "" : " " + memories.memoryLabel(memoryWrite));
				drawScaled(g, top, leftPos + display.x() + 2 + font.width(top) * 0.6F / 2.0F, topPos + display.y() + 5, 0.6F, ink);
				drawScaled(g, hz <= 0.0F ? "EMPTY" : com.arrl.radiocraft.common.radio.TuningStep.mhz(hz), cx, cy, 0.9F, ink);
			}
			else
				drawScaled(g, flash, cx, cy, 1.0F, ink);
		}
		else if(display != null) {
			float scale = Math.min(1.5F, Math.min((display.w() - 6) / (float)font.width("28.000 MHz"), (display.h() - 4) / 11.0F));
			renderDisplay(g, display.x(), display.y(), display.w(), display.h(), scale, display.colour("ink", 0xFFFFFFFF) & 0xFFFFFF);
		}
		HfPanel.Control over = panel.at(mouseX - leftPos, mouseY - topPos);
		String tip = over == null ? null : tooltip(over.name());
		if(tip != null)
			g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
	}

	@Nullable
	private String tooltip(String name) {
		HFRadioAllBandBlockEntity allBand = allBand();
		return switch(name) {
			case "af_gain" -> String.format(Locale.ROOT, "AF %d%%", Math.round(radio().getSpeakerGain() * 100.0F));
			case "mic_gain" -> String.format(Locale.ROOT, "MIC %d%%", Math.round(radio().getMicGain() * 100.0F));
			case "squelch" -> "SQL " + radio().squelchLabel();
			case "agc" -> "AGC " + position("agc");
			case "offset" -> "Repeater offset: " + (vhf() == null ? "" : com.arrl.radiocraft.common.radio.voice.Ctcss.formatOffset(vhf().getOffsetHz()));
			case "step_up", "step_down" -> vhf() == null || !vhf().hasTuneStep() ? null
					: "STEP " + com.arrl.radiocraft.common.radio.TuningStep.label(vhf().getFrequencyStep()) + " kHz: click to tune a step; right click for a "
					+ (name.equals("step_up") ? "bigger" : "smaller") + " step";
			case "tone_up", "tone_down" -> "CTCSS tone: " + (vhf() == null ? "" : com.arrl.radiocraft.common.radio.voice.Ctcss.format(vhf().getCtcssTone()));
			case "meter", "meter_select", "meter_po", "meter_swr" -> "S on receive; " + METER_NAMES[meterMode()] + " on the air";
			case "coarse" -> coarse() ? "10x tuning ON (steps 100 kHz)" : "10x tuning OFF (steps 10 kHz)";
			case "quick" -> "QUICK: hold to store this frequency; press to jump to it, and again to come back";
			case "memory" -> memories() == null ? null : memoryWrite >= 0 ? "Turn the dial to a channel, press MEMORY to store"
					: "MEMORY: hold to store this frequency" + (vhf() != null ? ", offset, tone and step" : "") + " in one of " + memories().memoryCount() + " channels";
			case "vfo_mem" -> memories() == null ? null : "VFO/MEM: press to switch; hold on a memory to copy it to the VFO";
			case "tuning" -> "Tune: scroll, or drag up and down";
			case "band_up", "band_down" -> allBand == null || allBand.getBand() == null ? null : "BAND " + allBand.getBand().name();
			case "ptt" -> "Hold to talk; right click for the hand mic";
			case "rit" -> hf() == null ? null : String.format(Locale.ROOT, "RIT %+d Hz%s", hf().getRitSteps() * HFRadioBlockEntity.RIT_STEP_HZ,
					hf().isRitOn() ? "" : " (off: switch RIT on to use it)");
			case "rit_on", "rit_switch" -> hf() == null ? null : "RIT " + (hf().isRitOn() ? "ON" : "OFF") + ": tunes the receiver only, the transmitter stays put";
			case "rit_clear" -> "RIT CLEAR: back to zero";
			case "nb", "nb_on" -> hf() == null ? null : "Noise blanker " + (hf().isNoiseBlanker() ? "ON" : "OFF") + " (blanks sparks and crackle)";
			case "rf_gain" -> hf() == null ? null : "RF GAIN " + hf().getRfGain() + (hf().getRfGain() == HFRadioBlockEntity.MAX_RF_GAIN ? " (full)" : "");
			default -> null;
		};
	}

	@Override
	protected String modeLabel() {
		com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity v = vhf();
		if(v != null) { // The VHF rig is FM only: its corner shows the offset and tone instead, and a step that is not the usual.
			String label = com.arrl.radiocraft.common.radio.voice.Ctcss.formatOffset(v.getOffsetHz()) + " T" + com.arrl.radiocraft.common.radio.voice.Ctcss.format(v.getCtcssTone());
			int step = v.getFrequencyStep();
			if(step != com.arrl.radiocraft.RadiocraftServerConfig.VHF_FREQUENCY_STEP.get())
				label = label + " " + com.arrl.radiocraft.common.radio.TuningStep.label(step) + "K";
			com.arrl.radiocraft.common.blockentities.radio.MemoryRadio memories = memories();
			return memories != null && memories.isMemoryMode() ? String.format(Locale.ROOT, "M%02d ", memories.getMemoryChannel() + 1) + label : label;
		}
		HFRadioAllBandBlockEntity allBand = allBand();
		String mode = allBand != null && allBand.getFMEnabled() ? "FM" : amOn() ? "AM" : super.modeLabel();
		if(allBand != null && allBand.isMemoryMode()) // On a memory: its number before the mode.
			return String.format(Locale.ROOT, "M%02d %s", allBand.getMemoryChannel() + 1, mode == null ? "" : mode);
		HFRadioBlockEntity hf = hf();
		if(hf != null && hf.isRitOn()) // The RIT's offset after the mode, as the rigs show it.
			return (mode == null ? "" : mode + " ") + String.format(Locale.ROOT, "R%+.2f", hf.getRitHz() / 1000.0F);
		return mode;
	}

	// -------------------- CONTROLS --------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HfPanel.Control c = panel.at(mouseX - leftPos, mouseY - topPos);
		if(c != null && (button == 0 || button == 1) && press(c, button, mouseX - leftPos, mouseY - topPos))
			return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean press(HfPanel.Control c, int button, double px, double py) {
		int step = button == 0 ? 1 : -1;
		switch(c.type()) {
			case "knob" -> {
				turn(c, step);
				return true;
			}
			case "knob3", "lever" -> {
				List<String> positions = c.strings("positions");
				int i = positions.indexOf(position(c.name()));
				int direction = clickDirection.getOrDefault(c.name(), 1);
				if(i >= 0 && (i + direction < 0 || i + direction >= positions.size()))
					direction = -direction; // At the end: back the other way.
				clickDirection.put(c.name(), direction);
				turn(c, button == 0 ? direction : -direction);
				return true;
			}
			case "dial" -> {
				if(button == 0) {
					draggingDial = true;
					dragAccum = 0.0D;
				}
				return true;
			}
			case "switch" -> {
				List<int[]> handles = c.rects("handles");
				List<String> positions = c.strings("positions");
				int best = -1;
				double bestDist = Double.MAX_VALUE;
				for(int i = 0; i < handles.size() && i < positions.size(); i++) {
					int[] h = handles.get(i);
					double d = Math.hypot(h[0] + h[2] / 2.0D - px, h[1] + h[3] / 2.0D - py);
					if(d < bestDist) {
						bestDist = d;
						best = i;
					}
				}
				if(best >= 0)
					setPosition(c.name(), positions.get(best));
				return true;
			}
			case "button", "meter", "bar" -> {
				return pressButton(c.name(), button);
			}
			default -> {
				return false;
			}
		}
	}

	private boolean pressButton(String name, int button) {
		HFRadioAllBandBlockEntity allBand = allBand();
		if(name.equals("ptt")) {
			if(button == 1) {
				HandMicPtt.take(radio().getBlockPos());
				return true;
			}
			held = name;
			onPressPTT(null);
			return true;
		}
		if(button == 1 && (name.equals("step_up") || name.equals("step_down")) && vhf() != null && vhf().hasTuneStep()) {
			// Right click on STEP: the step's size (2026-11-14), up a size on the up key, down on the down key.
			int now = vhf().getFrequencyStep();
			int size = name.equals("step_up") ? com.arrl.radiocraft.common.radio.TuningStep.next(now) : com.arrl.radiocraft.common.radio.TuningStep.previous(now);
			SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_TUNE_STEP, size);
			vhf().setTuneStepHz(size); // The server syncs it back.
			click();
			return true;
		}
		if(button != 0)
			return false;
		if(name.equals("quick") || name.equals("memory") || name.equals("vfo_mem")) { // A press now, or a hold.
			held = name;
			heldSince = System.currentTimeMillis();
			holdFired = false;
			return true;
		}
		if(memoryWrite >= 0 && !name.equals("tuning"))
			memoryWrite = -1; // Any other key leaves the memory write.
		switch(name) {
			case "power" -> {
				ToggleButton.playPowerClick(!menu.isPowered());
				onPressPower(null);
				return true;
			}
			case "mode_ssb" -> onPressSSB(null);
			case "mode_cw" -> onPressCW(null);
			case "mode_am" -> selectAm();
			case "mode_fm" -> {
				if(allBand == null || allBand.getFMEnabled())
					return true;
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_FM, true);
				allBand.setFMEnabled(true);
			}
			case "meter_po", "meter_swr" -> METER_MODE.put(radio().getBlockPos().asLong(), name.equals("meter_po") ? 0 : 1);
			case "meter" -> METER_MODE.put(radio().getBlockPos().asLong(), (meterMode() + 1) % 2);
			case "coarse" -> COARSE.put(radio().getBlockPos().asLong(), !coarse());
			case "rit_on" -> {
				if(hf() != null)
					setRit(hf().getRitSteps(), !hf().isRitOn());
			}
			case "rit_clear" -> {
				if(hf() != null)
					setRit(0, hf().isRitOn());
			}
			case "nb_on" -> {
				if(hf() != null)
					setNoiseBlanker(!hf().isNoiseBlanker());
			}
			case "tone_up" -> stepTone(1);
			case "tone_down" -> stepTone(-1);
			case "step_up" -> stepButton(1);
			case "step_down" -> stepButton(-1);
			case "band_up", "band_down" -> {
				if(allBand == null)
					return true;
				int index = allBand.getBandIndex() + (name.equals("band_up") ? 1 : -1);
				if(index < 0 || index >= HFRadioAllBandBlockEntity.BANDS.size())
					return true;
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_BAND, index);
				allBand.setBandIndex(index);
			}
			default -> {
				return false;
			}
		}
		held = name;
		click();
		return true;
	}

	/**
	 * The dial or a step key on a radio's memories (all-band, VHF base station): while writing one, through every channel; on the
	 * memories, to the next stored channel. True if it did that (not tuning).
	 */
	private boolean memoryChannelStep(int steps) {
		com.arrl.radiocraft.common.blockentities.radio.MemoryRadio memories = memories();
		if(memories == null)
			return false;
		if(memoryWrite >= 0) {
			memoryWrite = Math.floorMod(memoryWrite + steps, memories.memoryCount());
			dialDeg += steps * DIAL_DEG_PER_STEP;
			return true;
		}
		if(memories.isMemoryMode()) {
			int next = memories.nextStored(memories.getMemoryChannel(), steps > 0 ? 1 : -1);
			if(next >= 0)
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.MEM_SELECT, next);
			dialDeg += steps * DIAL_DEG_PER_STEP;
			return true;
		}
		return false;
	}

	/** The up and down keys: 10 kHz a press, 100 kHz with the 10x key latched. */
	private void stepButton(int direction) {
		if(!menu.isPowered() || memoryChannelStep(direction))
			return;
		int steps = vhf() != null ? (coarse() ? 10 : 1) // VHF: a channel step, ten with 10x.
				: Math.max(1, (coarse() ? 100_000 : 10_000) / Math.max(1, radio().getFrequencyStep()));
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.STEP_FREQUENCY, direction * steps);
		radio().updateFrequency(direction * steps);
	}

	private void selectAm() {
		if(amOn())
			return;
		SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_AM, true);
		if(hfRadio() != null)
			hfRadio().setAMEnabled(true);
	}

	private void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.4F));
	}

	/** A knob, knob3, lever or switch turned {@code steps}. */
	private void turn(HfPanel.Control c, int steps) {
		switch(c.type()) {
			case "knob" -> {
				for(int i = 0; i < Math.abs(steps); i++) {
					boolean up = steps > 0;
					switch(c.name()) {
						case "af_gain" -> {
							if(up) onGainUp(null); else onGainDown(null);
						}
						case "mic_gain" -> {
							if(up) onMicGainUp(null); else onMicGainDown(null);
						}
						case "squelch" -> {
							if(up) onSquelchUp(null); else onSquelchDown(null);
						}
						case "rit" -> {
							if(hf() != null)
								setRit(hf().getRitSteps() + (up ? 1 : -1), hf().isRitOn());
						}
						case "rf_gain" -> {
							if(hf() != null)
								setRfGain(hf().getRfGain() + (up ? 1 : -1));
						}
						default -> {}
					}
				}
			}
			case "dial" -> {
				if(!menu.isPowered())
					return;
				if(memoryChannelStep(steps))
					return;
				int count = steps * (coarse() ? 10 : 1); // The 10x key: ten steps a detent.
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.STEP_FREQUENCY, count);
				radio().updateFrequency(count);
				dialDeg += steps * DIAL_DEG_PER_STEP;
			}
			default -> { // knob3, lever, switch: step through the positions.
				List<String> positions = c.strings("positions");
				int i = positions.indexOf(position(c.name()));
				if(i < 0)
					return;
				int next = Math.max(0, Math.min(positions.size() - 1, i + steps));
				if(next != i)
					setPosition(c.name(), positions.get(next));
			}
		}
	}

	private void setPosition(String name, String position) {
		switch(name) {
			case "offset" -> setOffset(position);
			case "agc" -> {
				int value = List.of(AGC_NAMES).indexOf(position);
				if(value < 0 || value == radio().getAgc())
					return;
				radio().setAgcClient(value);
				SBlockRadioControlPacket.updateServer(radio().getBlockPos(), SBlockRadioControlPacket.Action.SET_AGC, value);
			}
			case "meter_select" -> METER_MODE.put(radio().getBlockPos().asLong(), Math.max(0, List.of(METER_NAMES).indexOf(position)));
			case "nb" -> setNoiseBlanker(position.equals("ON"));
			case "rit_switch" -> {
				if(hf() != null)
					setRit(hf().getRitSteps(), position.equals("ON"));
			}
			case "power" -> {
				if(position.equals("ON") == menu.isPowered())
					return;
				ToggleButton.playPowerClick(!menu.isPowered());
				onPressPower(null);
				return;
			}
			case "mode_lever" -> {
				switch(position) {
					case "SSB" -> onPressSSB(null);
					case "CW" -> onPressCW(null);
					case "AM" -> selectAm();
					default -> {}
				}
			}
			default -> {
				return;
			}
		}
		click();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		HfPanel.Control c = panel.at(mouseX - leftPos, mouseY - topPos);
		if(c != null && scrollY != 0.0D && List.of("knob", "knob3", "lever", "switch", "dial").contains(c.type())) {
			turn(c, scrollY > 0.0D ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if(draggingDial) {
			dragAccum -= dragY; // Up tunes up.
			HfPanel.Control dial = panel.named("tuning");
			while(dial != null && Math.abs(dragAccum) >= DRAG_PER_STEP) {
				int step = dragAccum > 0 ? 1 : -1;
				dragAccum -= step * DRAG_PER_STEP;
				turn(dial, step);
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if("ptt".equals(held))
			onReleasePTT(null);
		if(held != null && !holdFired && (held.equals("quick") || held.equals("memory") || held.equals("vfo_mem")))
			shortPress(held);
		held = null;
		draggingDial = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void onClose() {
		if("ptt".equals(held))
			onReleasePTT(null);
		held = null;
		super.onClose();
	}
}
