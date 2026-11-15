package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.FmBroadcastTransmitterBlockEntity;
import com.arrl.radiocraft.common.menus.SimpleRadioMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.network.serverbound.SFmStationPacket;
import com.arrl.radiocraft.common.radio.station.FmStation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * The FM broadcast transmitter's station panel (2026-11-14, the maintainer's FM station; was a channel and a held ON AIR
 * key): power and the channel, the mic (held to talk, as a desk mic does) and what is live on the air (MIC, a weather
 * warning waiting or going out, a forecast or the station ID), the program (tape player or internet stream, what is
 * playing, the tape standing in for a dead stream), the stream's address where the server allows streams, the
 * forecast's interval and when the next is due, the station ID and its name or callsign, and how far the program ducks
 * under the mic. Mouse wheel over the panel steps the channel.
 */
public class FmStationScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<SimpleRadioMenu> {

	private static final int BLUE = 0xFF1E4A78, EDGE = 0xFF10283F, SILVER = 0xFFB4B8BC, PRINT = 0xFFE8E8E8, DIM = 0xFF9AB0C8;
	private static final int AMBER = 0xFFFFA020, AMBER_DIM = 0xFF3A1E08, RED = 0xFFE03020, RED_DIM = 0xFF401010;

	private final FmBroadcastTransmitterBlockEntity station;
	private EditBox callsign, url;
	private Button idToggle;

	public FmStationScreen(SimpleRadioMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.station = (FmBroadcastTransmitterBlockEntity)menu.blockEntity;
		imageWidth = 256;
		imageHeight = 204;
	}

	private void control(SBlockRadioControlPacket.Action action, int step, boolean value) {
		SBlockRadioControlPacket.updateServer(station.getBlockPos(), action, step, value);
	}

	private void set(String action, String text, int value) {
		SFmStationPacket.send(station.getBlockPos(), action, text, value);
	}

	private Button button(Component label, int x, int y, int w, Button.OnPress press) {
		return addRenderableWidget(Button.builder(label, press).bounds(leftPos + x, topPos + y, w, 14).build());
	}

	private Button button(String label, int x, int y, int w, Button.OnPress press) {
		return button(Component.literal(label), x, y, w, press);
	}

	private void step(int steps) {
		if(!menu.isPowered())
			return;
		control(SBlockRadioControlPacket.Action.STEP_FREQUENCY, steps, false);
		station.updateFrequency(steps);
	}

	@Override
	protected void init() {
		super.init();
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = true; // The MIC key takes your voice to the air.
		button("PWR", 8, 8, 30, b -> control(SBlockRadioControlPacket.Action.TOGGLE_POWER, 0, true));
		button("<<", 160, 26, 40, b -> step(-1));
		button(">>", 204, 26, 40, b -> step(1));
		addRenderableWidget(new MicButton(leftPos + 8, topPos + 48, 70, 14));
		// The program.
		button(Component.translatable("radiocraft.fm_station.tape"), 52, 84, 44, b -> set("program", "", 0));
		button(Component.translatable("radiocraft.fm_station.stream"), 98, 84, 50, b -> set("program", "", 1));
		url = new EditBox(font, leftPos + 52, topPos + 110, 156, 14, Component.translatable("radiocraft.fm_station.url"));
		url.setMaxLength(FmBroadcastTransmitterBlockEntity.URL_LENGTH);
		url.setValue(station.getStreamUrl());
		url.setHint(Component.translatable("radiocraft.fm_station.url_hint"));
		url.visible = FmBroadcastTransmitterBlockEntity.streamsAllowed();
		addRenderableWidget(url);
		Button setUrl = button(Component.translatable("radiocraft.fm_station.set"), 212, 110, 32, b -> set("url", url.getValue(), 0));
		setUrl.visible = url.visible;
		// Forecasts.
		button("-", 112, 140, 14, b -> set("forecast", "", Math.max(0, station.station().forecastMinutes() - 5)));
		button("+", 128, 140, 14, b -> set("forecast", "", station.station().forecastMinutes() + 5));
		// Station ID.
		idToggle = button(idLabel(), 52, 158, 40, b -> set("station_id", "", station.station().stationIdOn() ? 0 : 1));
		callsign = new EditBox(font, leftPos + 96, topPos + 158, 112, 14, Component.translatable("radiocraft.fm_station.callsign"));
		callsign.setMaxLength(FmBroadcastTransmitterBlockEntity.NAME_LENGTH);
		callsign.setValue(station.getCallsign());
		callsign.setHint(Component.translatable("radiocraft.fm_station.callsign_hint"));
		addRenderableWidget(callsign);
		button(Component.translatable("radiocraft.fm_station.set"), 212, 158, 32, b -> set("callsign", callsign.getValue(), 0));
		// The duck under the mic.
		button("-", 112, 176, 14, b -> set("duck", "", Math.max(0, Math.round(station.station().duckLevel() * 100.0F) - 10)));
		button("+", 128, 176, 14, b -> set("duck", "", Math.min(100, Math.round(station.station().duckLevel() * 100.0F) + 10)));
	}

	private Component idLabel() {
		return Component.literal(station.station().stationIdOn() ? "ON" : "OFF");
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if(idToggle != null)
			idToggle.setMessage(idLabel()); // Follows the server's word.
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			if(url.isFocused()) {
				set("url", url.getValue(), 0);
				return true;
			}
			if(callsign.isFocused()) {
				set("callsign", callsign.getValue(), 0);
				return true;
			}
		}
		if((url.isFocused() || callsign.isFocused()) && key != GLFW.GLFW_KEY_ESCAPE && getFocused() != null) {
			getFocused().keyPressed(key, scan, modifiers); // Typing, not the inventory key.
			return true;
		}
		return super.keyPressed(key, scan, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY != 0.0D && isHovering(0, 0, imageWidth, 70, mouseX, mouseY)) {
			step(scrollY > 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		if(RadiocraftClientValues.SCREEN_PTT_PRESSED)
			control(SBlockRadioControlPacket.Action.SET_PTT, 0, false);
		RadiocraftClientValues.SCREEN_PTT_PRESSED = false;
		RadiocraftClientValues.SCREEN_VOICE_ENABLED = false;
		super.onClose();
	}

	// -------------------- DRAWING --------------------

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
		boolean on = menu.isPowered();
		g.fill(x, y, x + w, y + h, EDGE);
		g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BLUE);
		g.fill(x + 2, y + 2, x + 42, y + 66, SILVER);
		g.drawString(font, Component.translatable("radiocraft.fm_station.title"), x + 48, y + 10, PRINT, false);
		// The channel.
		g.fill(x + 48, y + 24, x + 152, y + 42, 0xFF0C0C0E);
		g.fill(x + 50, y + 26, x + 150, y + 40, on ? AMBER_DIM : 0xFF141414);
		if(on)
			g.drawString(font, FmBroadcastRadioBlockEntity.format(station.getFrequency()) + " MHz", x + 56, y + 29, AMBER, false);
		// What is live: the carrier, the mic, the station's voice.
		lamp(g, x + 84, y + 48, on, RED, RED_DIM, "ON AIR");
		lamp(g, x + 132, y + 48, on && station.isMicLive(), RED, RED_DIM, "MIC");
		FmStation.Speaking speaking = on ? station.speakingNow() : FmStation.Speaking.NONE;
		String saying = switch(speaking) {
			case ALERT -> "WX ALERT";
			case FORECAST -> "FORECAST";
			case STATION_ID -> "STATION ID";
			default -> "";
		};
		lamp(g, x + 180, y + 48, speaking != FmStation.Speaking.NONE, AMBER, AMBER_DIM, saying.isEmpty() ? "VOICE" : saying);
		g.fill(x + 6, y + 70, x + w - 6, y + 71, EDGE);
	}

	private void lamp(GuiGraphics g, int x, int y, boolean lit, int on, int off, String label) {
		int width = Math.max(44, font.width(label) + 8);
		g.fill(x, y, x + width, y + 14, lit ? on : off);
		g.drawCenteredString(font, label, x + width / 2, y + 3, lit ? 0xFFFFFFFF : 0xFF806060);
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
		FmStation st = station.station();
		boolean stream = st.program() == FmStation.Program.STREAM;
		g.drawString(font, Component.translatable("radiocraft.fm_station.program"), 8, 87, DIM, false);
		g.drawString(font, stream ? "> STREAM" : "> TAPE", 152, 87, AMBER, false);
		String playing = station.nowPlaying();
		Component line;
		if(stream && !FmBroadcastTransmitterBlockEntity.streamsAllowed())
			line = Component.translatable("radiocraft.fm_station.streams_off");
		else if(station.onFallback())
			line = Component.translatable("radiocraft.fm_station.fallback", playing);
		else if(!playing.isEmpty())
			line = Component.translatable("radiocraft.fm_station.playing", playing);
		else if(stream)
			line = Component.translatable("radiocraft.fm_station.stream_status", station.streamStatus().isEmpty() ? "-" : station.streamStatus());
		else
			line = Component.translatable(station.hasTape() ? "radiocraft.fm_station.tape_idle" : "radiocraft.fm_station.no_tape");
		g.drawString(font, font.plainSubstrByWidth(line.getString(), imageWidth - 16), 8, 98, PRINT, false);
		if(url.visible)
			g.drawString(font, Component.translatable("radiocraft.fm_station.url"), 8, 113, DIM, false);

		// Weather and forecasts.
		g.drawString(font, Component.translatable("radiocraft.fm_station.forecast"), 8, 143, DIM, false);
		int minutes = st.forecastMinutes(), in = station.forecastIn();
		String every = minutes <= 0 ? Component.translatable("radiocraft.fm_station.off").getString()
				: Component.translatable("radiocraft.fm_station.every", minutes).getString()
				+ (in >= 0 ? "  " + Component.translatable("radiocraft.fm_station.next", String.format(Locale.ROOT, "%d:%02d", in / 60, in % 60)).getString() : "");
		g.drawString(font, every, 148, 143, PRINT, false);
		Component wx = station.alertsWaiting() > 0 ? Component.translatable("radiocraft.fm_station.wx_waiting", station.alertsWaiting())
				: Component.translatable(station.hasDecoder() ? "radiocraft.fm_station.wx_decoder" : "radiocraft.fm_station.wx_none");
		g.drawString(font, font.plainSubstrByWidth(wx.getString(), 150), 8, 128, station.alertsWaiting() > 0 ? AMBER : DIM, false);
		if(station.hasWeatherStation())
			g.drawString(font, Component.translatable("radiocraft.fm_station.wx_station"), 160, 128, DIM, false);

		// Station ID.
		g.drawString(font, Component.translatable("radiocraft.fm_station.station_id"), 8, 161, DIM, false);

		// The duck.
		g.drawString(font, Component.translatable("radiocraft.fm_station.duck"), 8, 179, DIM, false);
		g.drawString(font, Math.round(st.duckLevel() * 100.0F) + "%", 148, 179, PRINT, false);
		g.drawString(font, title, 8, imageHeight - 12, DIM, false);
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		renderTooltip(g, mouseX, mouseY);
	}

	/** Held to talk on the air: down while the mouse is (the program ducks under it). */
	private final class MicButton extends AbstractButton {

		MicButton(int x, int y, int w, int h) {
			super(x, y, w, h, Component.translatable("radiocraft.fm_station.mic"));
		}

		@Override
		public void onPress() {
			if(!menu.isPowered())
				return;
			control(SBlockRadioControlPacket.Action.SET_PTT, 0, true);
			RadiocraftClientValues.SCREEN_PTT_PRESSED = true;
		}

		@Override
		public void onRelease(double mouseX, double mouseY) {
			if(RadiocraftClientValues.SCREEN_PTT_PRESSED)
				control(SBlockRadioControlPacket.Action.SET_PTT, 0, false);
			RadiocraftClientValues.SCREEN_PTT_PRESSED = false;
		}

		@Override
		protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
