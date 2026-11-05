package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.weather.WeatherSource;
import com.arrl.radiocraft.api.weather.WeatherSources;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.WefaxReceiverBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.PictureReception;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.voice.WefaxAudio;
import com.arrl.radiocraft.common.radio.weather.WeatherReports;
import com.arrl.radiocraft.common.radio.wefax.WefaxChart;
import com.arrl.radiocraft.common.radio.wefax.WefaxPrints;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The WEFAX program's server side (1.12 step 7): weather fax charts, in greys, as {@link PictureHandler} does pictures.
 * It receives charts other stations send (a radio computer's WEFAX, sent with weather fax tones that last a sheet's
 * {@code wefax_bulletin_seconds}; a WEFAX receiver block with AUTO on prints them too), the solar bulletins an
 * Ionospheric Processor sends and its blackout alerts, and on request the coast station's broadcast (WX REPT: the radar
 * chart, then the forecast sheet, as the WEFAX receiver prints them; its tones play through the station's radios). WX REPT
 * needs a radio with a real antenna on the bus, as the receiver does (unless {@code wefax_weather_always_works}). It
 * sends any picture as a chart (in greys). What comes in keeps as {@code WFX00001.IMG}.
 * <p>
 * Its own requests: {@code wxrept} (the coast station's two charts), {@code chart} ({@code kind} radar or forecast: the
 * chart for here written into {@code WXRADAR.IMG} or {@code WXFCST.IMG}, to send as this station's own); its status adds
 * {@code broadcast} (charts of the coast station still to come) and {@code canWxrept}.
 */
public final class WefaxHandler extends PictureHandler {

	/** A chart of the coast station's still to come, and its tones. */
	private record Queued(String label, byte[] pixels) {}

	/** WEFAX's state: the pictures, and the coast station's broadcast going on. */
	public static final class WefaxState extends State {
		final ArrayDeque<Queued> queue = new ArrayDeque<>();
		@Nullable
		List<short[]> frames;
		int frame = 0;
		long lastAlertAt = -1L;
	}

	@Override
	protected State makeState() {
		return new WefaxState();
	}

	@Override
	protected ComputerApp app() {
		return ComputerApps.WEFAX;
	}

	@Override
	protected String prefix() {
		return "WFX";
	}

	@Override
	protected String mode() {
		return FileTransfer.MODE_WEFAX;
	}

	@Override
	protected byte[] outgoing(byte[] pixels) {
		return MapPalettes.vanilla().greyscaled(MapPalettes.vanilla().sanitised(pixels));
	}

	@Override
	protected boolean wants(TncPacket packet) {
		return FileTransfer.MODE_WEFAX.equals(FileTransfer.mode(packet.item()));
	}

	@Override
	protected int airTicks() {
		return FileTransfer.wefaxTicks();
	}

	@Override
	protected PictureReception.Clock clock() {
		return PictureReception.Clock.WEFAX;
	}

	@Override
	protected int blankRgb() {
		return 0xF0F0F0;
	}

	// -------------------- SOLAR BULLETINS AND ALERTS --------------------

	@Override
	protected void receiveMore(RadioComputerBlockEntity computer, State state, TncPacket packet) {
		if(!(computer.getLevel() instanceof ServerLevel level) || !(state instanceof WefaxState wefax))
			return;
		List<String> lines = packet.solarLines();
		if(lines.isEmpty())
			return;
		long now = level.getGameTime();
		if(packet.kind() == TncPacket.Kind.SOLAR) {
			// The bulletin's packet comes as its start tone ends: the picture is that far along already.
			int ticks = WefaxReceiverBlockEntity.printDuration();
			long start = now - Math.round(WefaxAudio.START_SHARE * ticks);
			start(computer, state, packet.id(), packet.from(), WefaxPrints.bulletinTitle(packet.from()), WefaxPrints.bulletinPixels(packet.from(), lines), start, ticks, false);
		}
		else if(packet.kind() == TncPacket.Kind.ALERT && (wefax.lastAlertAt < 0 || now - wefax.lastAlertAt >= WefaxReceiverBlockEntity.ALERT_GAP_TICKS)) {
			wefax.lastAlertAt = now; // An alert is a burst of packet tones: kept at once, as a sheet.
			start(computer, state, packet.id(), packet.from(), "SOLAR ALERT", WefaxChart.paintText("SOLAR ALERT", lines), now, 1, true);
			state.reception.finish(true);
			done(computer, state);
		}
	}

	// -------------------- THE COAST STATION AND CHARTS OF ITS OWN --------------------

	@Override
	protected void addStatus(ComputerContext context, State state, CompoundTag out) {
		out.putInt("broadcast", state instanceof WefaxState wefax ? wefax.queue.size() : 0);
		out.putBoolean("canWxrept", canWxrept(context));
	}

	private static boolean canWxrept(ComputerContext context) {
		return RadiocraftServerConfig.WEFAX_WEATHER_ALWAYS.get() || RadioStation.hasAntenna(context.level(), RadioStation.radios(context.bus()));
	}

	@Override
	protected boolean handleMore(ComputerContext context, State state, String action, CompoundTag data, ComputerReply reply) {
		if(!(state instanceof WefaxState wefax))
			return false;
		ServerLevel level = context.level();
		BlockPos here = Sublevels.world(level, context.pos());
		WeatherSource source = WeatherSources.get(level);
		switch(action) {
			case "wxrept" -> {
				if(!canWxrept(context)) {
					reply.error("wefax_no_antenna");
					return true;
				}
				if(state.reception != null || !wefax.queue.isEmpty()) {
					reply.error("wefax_busy");
					return true;
				}
				wefax.queue.add(new Queued(WefaxChart.Type.RADAR.title(), WefaxChart.paint(level, source, here, WefaxChart.Type.RADAR, "")));
				wefax.queue.add(new Queued("FORECAST", WefaxChart.paintText("FORECAST", forecast(level, source, here))));
				reply.ok();
			}
			case "chart" -> {
				boolean radar = !"forecast".equals(data.getString("kind"));
				String callsign = context.bus().callsign();
				byte[] pixels = radar ? WefaxChart.paint(level, source, here, WefaxChart.Type.RADAR, callsign)
						: WefaxChart.paintText("FORECAST " + callsign, forecast(level, source, here));
				String name = radar ? "WXRADAR.IMG" : "WXFCST.IMG";
				ComputerStorage.Problem problem = context.files().write(name, pixels);
				if(problem != null) {
					reply.error(problem);
					return true;
				}
				CompoundTag out = new CompoundTag();
				out.putString("name", name);
				reply.ok(out);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** The forecast sheet's lines: the report here, then the storms about. */
	private static List<String> forecast(ServerLevel level, WeatherSource source, BlockPos here) {
		List<String> lines = new ArrayList<>(WeatherReports.lines(level, source, here, ""));
		lines.add("");
		lines.addAll(WeatherReports.stormList(level, source, here, WefaxReceiverBlockEntity.FORECAST_RANGE));
		return lines;
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if(!(computer.existingState(app()) instanceof WefaxState state))
			return;
		if(state.reception == null && !state.queue.isEmpty()) { // The coast station's next chart.
			Queued next = state.queue.poll();
			int ticks = WefaxReceiverBlockEntity.printDuration();
			start(computer, state, RadioStation.newId(computer.getLevel()), "COAST STATION", next.label(), next.pixels(), gameTime, ticks, true);
			state.frames = WefaxAudio.frames(next.pixels(), WefaxChart.SIZE, ticks);
			state.frame = 0;
			state.radios = RadioStation.radios(RadioStation.bus(computer));
		}
		List<short[]> frames = state.frames;
		if(frames != null) { // Its tones, through the station's radios.
			if(state.reception == null || state.frame >= frames.size())
				state.frames = null;
			else {
				int count = gameTime % 2 == 0 ? 3 : 2;
				for(int i = 0; i < count && state.frame < frames.size(); i++) {
					short[] frame = frames.get(state.frame++);
					for(RadioBlockEntity radio : state.radios)
						radio.playBroadcast(state, frame, 0.7D);
				}
			}
		}
		if(state.reception != null)
			tickReception(computer, state, gameTime);
	}
}
