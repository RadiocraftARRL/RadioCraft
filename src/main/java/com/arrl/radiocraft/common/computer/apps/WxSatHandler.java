package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerNetwork;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.photo.PhotoImage;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatelliteMode;
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import com.arrl.radiocraft.common.radio.satellite.Satellites;
import com.arrl.radiocraft.common.radio.satellite.imaging.AptLine;
import com.arrl.radiocraft.common.radio.satellite.imaging.AptLines;
import com.arrl.radiocraft.common.radio.satellite.imaging.AptSwath;
import com.arrl.radiocraft.common.radio.satellite.imaging.RadarScale;
import com.arrl.radiocraft.common.radio.satellite.imaging.WxMosaic;
import com.arrl.radiocraft.common.radio.weather.WeatherReports;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WXSAT's server side (2026-11-12, {@code doc/SatellitePlan.md}, "Weather imaging"): weather satellite pictures. A
 * commercial satellite in {@link SatelliteMode#IMAGING} sends scan lines on its downlink ({@link TncPacket.Kind#APT}); a
 * radio linked to the computer that copies one passes it here ({@code RadioBlockEntity#receiveData}) with how well it
 * copied, and the line, through its noise ({@link AptLine#degrade}), goes on the live strip and into the computer's
 * mosaic ({@link WxSatStore}). Lines lost on the way leave gaps in the strip (by their sequence numbers).
 * <p>
 * A background service, as DISTRESS: the computer stays on (and draws power) while WXSAT is installed, so it hears
 * every pass with nobody at the screen. Passes come several times a Minecraft day at times no one waits at a screen for,
 * and the External CRT Monitor shows the mosaic all the time; a program that only listened while someone sat at it would
 * miss nearly all of them. SETUP's background services switch (2026-11-12) can turn this off for one computer: then
 * WXSAT hears passes only while it is open ({@code RadioComputerBlockEntity#serviceRunning}).
 * <ul>
 * <li>{@code status}: {@code sat} (the satellite of the last pass), {@code receiving}, {@code lines}, {@code lost},
 * {@code quality} (the last line's, over the copy threshold), {@code ago} (ticks since the last line), {@code radio}
 * (a radio is linked) with {@code radio_mhz}, {@code imagers} (commercial satellites in imaging mode: {@code name},
 * {@code down} MHz, {@code on}, {@code passing} with {@code el}/{@code az}/{@code shift}, else {@code in} ticks to its
 * next pass and {@code max} elevation), {@code passes} and {@code tiles} kept, {@code v} (the mosaic's version),
 * {@code lv} (the strip's), {@code sx}/{@code sz} (the station).</li>
 * <li>{@code live} ({@code have}: the strip version the screen has): the last {@link #LIVE_ROWS} lines, 0 for a lost
 * one: {@code rows}, {@code pixels}, {@code lv}; or {@code same}.</li>
 * <li>{@code view} ({@code x}, {@code z}, {@code zoom} blocks a pixel, {@code frame} (-1 latest), {@code w}, {@code h}):
 * the mosaic there ({@link WxMosaic#view}): {@code pixels}, {@code frames} (each pass's {@code sat}, {@code time},
 * {@code lines}), {@code frame}, {@code v}.</li>
 * <li>{@code save} ({@code name} and the view's fields): the view as a 128 x 128 .IMG on the computer.</li>
 * <li>{@code print} (the view's fields): the same picture through the scanner, with its area, time and weather as text
 * for plain paper.</li>
 * <li>{@code clear}: forgets the mosaic.</li>
 * </ul>
 */
public final class WxSatHandler implements ComputerAppHandler {

	/** Lines the live view shows, each of {@link #LIVE_W} pixels: the strip halved both ways (every other line and pixel). */
	public static final int LIVE_ROWS = 176;
	/** Pixels across a live view row: half a scan line (2026-11-14, the swath doubled to 512 pixels). */
	public static final int LIVE_W = AptSwath.PIXELS / 2;
	/** Lines kept on the live strip (a long pass). */
	public static final int LIVE_KEEP = 3200;
	/** A gap this long (ticks) without lines starts a new pass. */
	public static final int PASS_GAP_TICKS = 400;
	/** Most lost lines filled in for one gap in the sequence. */
	private static final int MAX_GAP = 64;
	/** The largest view a request may ask for, pixels. */
	public static final int VIEW_MAX_W = 320, VIEW_MAX_H = 200;
	/** The zooms the program offers, blocks a pixel. */
	public static final int[] ZOOMS = { AptSwath.BLOCKS_PER_PIXEL, AptSwath.BLOCKS_PER_PIXEL * 2, AptSwath.BLOCKS_PER_PIXEL * 4, AptSwath.BLOCKS_PER_PIXEL * 8 };

	/** One computer's live strip: the pass coming in (or the last one). Not saved: the mosaic is. */
	public static final class State implements ComputerAppState {
		String satellite = "";
		long started = -1L, lastAt = -1L;
		int lastSeq = -1, lost = 0;
		double quality = 0.0D;
		final List<byte[]> lines = new ArrayList<>(); // null for a lost line.
		long version = 0L;
		/** A geostationary frame coming in (2026-11-14): its number, the last row, the rows in all; -1 for a pass. */
		int frame = -1, row = -1, rows = 0;
		/** True once the frame's end-of-frame signal came. */
		boolean frameDone = false;

		/** The frame coming in, or -1 for an orbiting pass. */
		public int frame() {
			return frame;
		}

		public boolean frameDone() {
			return frameDone;
		}

		public int row() {
			return row;
		}

		public String satellite() {
			return satellite;
		}

		public int lineCount() {
			return lines.size();
		}

		/** Lines that came in (not lost). */
		public int received() {
			int n = 0;
			for(byte[] line : lines)
				if(line != null)
					n++;
			return n;
		}

		public int lost() {
			return lost;
		}

		public long lastAt() {
			return lastAt;
		}

		public double quality() {
			return quality;
		}
	}

	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.WXSAT, State::new);
	}

	/** The computer's mosaic store. Server thread. */
	public static WxSatStore store(RadioComputerBlockEntity computer) {
		MinecraftServer server = computer.getLevel() == null ? null : computer.getLevel().getServer();
		if(server == null)
			throw new IllegalStateException("No server");
		return WxSatStore.of(server, computer.getComputerId());
	}

	@Override
	public boolean ticks() {
		return true; // A background service: installed, it keeps the computer on to hear passes (see the class comment).
	}

	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if((gameTime + computer.getBlockPos().asLong()) % 1200L != 0L || computer.getLevel() == null || computer.getLevel().getServer() == null)
			return;
		WxSatStore store = store(computer);
		long before = store.mosaic().version();
		store.mosaic().age(gameTime);
		if(store.mosaic().version() != before)
			store.setDirty();
	}

	/** A scan line a linked radio copied: degraded by how well it copied, onto the strip and into the mosaic. */
	@Override
	public void receive(RadioComputerBlockEntity computer, TncPacket packet) {
		if(packet.kind() != TncPacket.Kind.APT || !(computer.getLevel() instanceof ServerLevel level))
			return;
		AptLine.Geometry geometry = AptLine.Geometry.decode(packet.text());
		double quality = AptLine.quality(packet.text());
		if(geometry == null || quality < 0.0D)
			return;
		long now = level.getGameTime();
		if(geometry.frameEnd()) { // A geostationary frame's end: it is whole (2026-11-14).
			State state = state(computer);
			if(state.satellite.equals(packet.from()) && state.frame == geometry.frame()) {
				state.frameDone = true;
				state.lastAt = now;
				state.version++;
			}
			return;
		}
		byte[] clean = AptLines.get(packet.id());
		if(clean == null)
			return;
		byte[] line = AptLine.degrade(clean, quality, packet.id() ^ computer.getBlockPos().asLong() * 31L);
		State state = state(computer);
		WxSatStore store = store(computer);
		WxMosaic mosaic = store.mosaic();
		mosaic.station(computer.getBlockPos().getX(), computer.getBlockPos().getZ());
		// A geostationary imager's new frame is a new layer too: laid over the last frame, it replaces it row by row as it
		// comes in, the rows not yet sent showing the frame before (2026-11-14).
		boolean newPass = !state.satellite.equals(packet.from()) || state.lastAt < 0 || now - state.lastAt > PASS_GAP_TICKS || geometry.seq() <= state.lastSeq
				|| geometry.frame() != state.frame;
		if(newPass) {
			state.satellite = packet.from();
			state.started = now;
			state.lines.clear();
			state.lost = 0;
			state.frame = geometry.frame();
			state.frameDone = false;
		}
		else
			for(int s = state.lastSeq + 1; s < geometry.seq() && s - state.lastSeq <= MAX_GAP; s++) { // Lines that never came: gaps.
				state.lines.add(null);
				state.lost++;
			}
		state.lines.add(line);
		if(line == null)
			state.lost++;
		while(state.lines.size() > LIVE_KEEP)
			state.lines.removeFirst();
		state.lastSeq = geometry.seq();
		state.row = geometry.row();
		state.rows = geometry.rows();
		state.lastAt = now;
		state.quality = quality;
		state.version++;
		WxMosaic.Pass pass = mosaic.join(packet.from(), geometry.frame(), now, PASS_GAP_TICKS); // A peer on the LAN may have begun this pass's layer already.
		if(line != null) {
			mosaic.line(pass, line, geometry.cx(), geometry.cz(), geometry.rx(), geometry.rz(), now);
			share(computer, store, packet.from(), geometry, line, now);
		}
		store.setDirty();
	}

	/**
	 * Shares a line this computer copied with the WXSAT computers on its LAN (2026-11-15, the maintainer: "WXSAT maps can
	 * be merged over LAN"), into the same pass's layer in each mosaic ({@link WxMosaic#join}), so every computer on the
	 * network shows the pictures any of them hears. Not passed on further (each copy comes from the computer that heard
	 * it). Neither end may have "Block LAN sync" set ({@link WxSatStore#lanBlocked}, off by default).
	 */
	static void share(RadioComputerBlockEntity computer, WxSatStore store, String satellite, AptLine.Geometry geometry, byte[] line, long now) {
		if(store.lanBlocked())
			return;
		for(ComputerNetwork.Peer peer : ComputerNetwork.peers(computer)) {
			RadioComputerBlockEntity other = peer.computer();
			if(!peer.link().up() || other.isRemoved() || !other.isInstalled(ComputerApps.WXSAT))
				continue;
			WxSatStore theirs = store(other);
			if(theirs.lanBlocked())
				continue;
			WxMosaic mosaic = theirs.mosaic();
			mosaic.station(other.getBlockPos().getX(), other.getBlockPos().getZ());
			mosaic.line(mosaic.join(satellite, geometry.frame(), now, PASS_GAP_TICKS), line, geometry.cx(), geometry.cz(), geometry.rx(), geometry.rz(), now);
			theirs.setDirty();
		}
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		switch(action) {
			case "status" -> reply.ok(status(context));
			case "live" -> reply.ok(live(context, data.getLong("have")));
			case "view" -> reply.ok(view(context, data));
			case "save" -> save(context, data, reply);
			case "print" -> print(context, data, reply);
			case "lan_block" -> {
				store(context.computer()).lanBlocked(data.getBoolean("on"));
				reply.ok();
			}
			case "clear" -> {
				store(context.computer()).clear();
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/** How many WXSAT computers on the LAN share with this one (0 while either blocks it). */
	static int lanSharers(RadioComputerBlockEntity computer, WxSatStore store) {
		if(store.lanBlocked())
			return 0;
		int n = 0;
		for(ComputerNetwork.Peer peer : ComputerNetwork.peers(computer))
			if(peer.link().up() && !peer.computer().isRemoved() && peer.computer().isInstalled(ComputerApps.WXSAT) && !store(peer.computer()).lanBlocked())
				n++;
		return n;
	}

	private static CompoundTag status(ComputerContext context) {
		CompoundTag out = new CompoundTag();
		State state = state(context.computer());
		WxSatStore store = store(context.computer());
		long now = context.level().getGameTime();
		out.putString("sat", state.satellite);
		out.putBoolean("receiving", state.lastAt >= 0 && now - state.lastAt < 40);
		out.putInt("lines", state.lines.size());
		if(state.frame >= 0) { // A geostationary frame: "frame N, row r of R" (2026-11-14).
			out.putInt("frame", state.frame + 1);
			out.putInt("row", state.row + 1);
			out.putInt("rows", state.rows);
			out.putBoolean("frame_done", state.frameDone);
		}
		out.putInt("lost", state.lost);
		out.putDouble("quality", state.quality);
		out.putLong("ago", state.lastAt < 0 ? -1L : now - state.lastAt);
		out.putLong("lv", state.version);
		out.putLong("v", store.version());
		out.putInt("passes", store.mosaic().passCount());
		out.putInt("tiles", store.mosaic().tileCount());
		out.putBoolean("lan_block", store.lanBlocked());
		out.putInt("lan", lanSharers(context.computer(), store));
		BlockPos at = context.pos();
		out.putInt("sx", at.getX());
		out.putInt("sz", at.getZ());
		out.putBoolean("overworld", context.level().dimension() == Level.OVERWORLD);
		List<RadioBlockEntity> radios = context.linked(RadioBlockEntity.class);
		out.putBoolean("radio", !radios.isEmpty());
		if(!radios.isEmpty())
			out.putString("radio_mhz", CommercialLicenseRules.mhz(radios.getFirst().getFrequency()));
		ListTag imagers = new ListTag();
		long overworldNow = context.server().overworld().getGameTime();
		for(Satellite s : Satellites.of(context.server()).all()) {
			if(s.amateur || s.mode() != SatelliteMode.IMAGING || s.decommissioned())
				continue;
			CompoundTag e = new CompoundTag();
			e.putString("name", s.name);
			e.putString("down", CommercialLicenseRules.mhz(s.downlinkHz));
			e.putBoolean("on", s.isOn());
			SatelliteOrbit.Look look = s.look(at.getX(), at.getZ(), overworldNow);
			e.putBoolean("passing", look.up());
			if(look.up()) {
				e.putDouble("el", look.elevation());
				e.putDouble("az", look.azimuth());
				if(Satellites.dopplerOn())
					e.putDouble("shift", s.dopplerHz(s.downlinkHz, at.getX(), at.getZ(), overworldNow));
			}
			else {
				List<SatelliteOrbit.Pass> next = s.nextPasses(at.getX(), at.getZ(), overworldNow, 1);
				if(!next.isEmpty()) {
					e.putLong("in", Math.max(0L, (long)Math.ceil(next.getFirst().rise() - overworldNow)));
					e.putDouble("max", next.getFirst().maxElevation());
				}
			}
			imagers.add(e);
		}
		out.put("imagers", imagers);
		out.putString("why", why(out, radios.isEmpty() ? null : out.getString("radio_mhz")));
		return out;
	}

	/**
	 * Why nothing is coming in (2026-11-14, the maintainer could not tell): "" while lines come in; else, for the first
	 * imager passing now, "no_radio", "wrong_freq" (the radio's frequency and the downlink are in the status), "sat_off",
	 * or "unheard" (on frequency and passing, but nothing copies: too weak, no open sky over the antenna, the radio off
	 * or deaf); "no_pass" when none is up.
	 */
	static String why(CompoundTag status, @Nullable String radioMhz) {
		if(status.getBoolean("receiving"))
			return "";
		ListTag imagers = status.getList("imagers", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for(int i = 0; i < imagers.size(); i++) {
			CompoundTag e = imagers.getCompound(i);
			if(!e.getBoolean("passing"))
				continue;
			status.putString("why_sat", e.getString("name"));
			status.putString("why_down", e.getString("down"));
			if(radioMhz == null)
				return "no_radio";
			if(!e.getBoolean("on"))
				return "sat_off";
			if(!radioMhz.equals(e.getString("down")))
				return "wrong_freq";
			return "unheard";
		}
		return "no_pass";
	}

	private static CompoundTag live(ComputerContext context, long have) {
		State state = state(context.computer());
		CompoundTag out = new CompoundTag();
		out.putLong("lv", state.version);
		if(have == state.version && have != 0L) {
			out.putBoolean("same", true);
			return out;
		}
		// Every other line, newest last, and every other pixel of each: the strip at half scale (the same ground as the
		// mosaic's second zoom), so the live view keeps its size and what it sends.
		int rows = Math.min(LIVE_ROWS, (state.lines.size() + 1) / 2);
		byte[] pixels = new byte[rows * LIVE_W];
		for(int r = 0; r < rows; r++) {
			byte[] line = state.lines.get(state.lines.size() - 1 - 2 * (rows - 1 - r));
			if(line != null)
				for(int c = 0; c < LIVE_W && 2 * c < line.length; c++)
					pixels[r * LIVE_W + c] = line[2 * c];
		}
		out.putInt("rows", rows);
		out.putByteArray("pixels", pixels);
		out.putString("sat", state.satellite);
		return out;
	}

	/** The zoom asked for, made one of {@link #ZOOMS}. */
	static int zoom(int asked) {
		for(int z : ZOOMS)
			if(z >= asked)
				return z;
		return ZOOMS[ZOOMS.length - 1];
	}

	private static CompoundTag view(ComputerContext context, CompoundTag data) {
		WxSatStore store = store(context.computer());
		WxMosaic mosaic = store.mosaic();
		int w = Math.max(1, Math.min(VIEW_MAX_W, data.getInt("w"))), h = Math.max(1, Math.min(VIEW_MAX_H, data.getInt("h")));
		int frame = data.getInt("frame");
		CompoundTag out = new CompoundTag();
		out.putLong("v", store.version());
		out.putInt("w", w);
		out.putInt("h", h);
		out.putByteArray("pixels", mosaic.view(data.getInt("x"), data.getInt("z"), w, h, zoom(data.getInt("zoom")), frame));
		out.putInt("frame", frame < 0 || frame >= mosaic.passCount() ? mosaic.latest() : frame);
		out.put("frames", frames(context.level(), mosaic));
		return out;
	}

	private static ListTag frames(ServerLevel level, WxMosaic mosaic) {
		ListTag list = new ListTag();
		for(WxMosaic.Pass pass : mosaic.passes()) {
			CompoundTag p = new CompoundTag();
			p.putString("sat", pass.satellite());
			p.putString("time", timestamp(level, pass.started()));
			p.putInt("lines", pass.lines());
			list.add(p);
		}
		return list;
	}

	/** Game time {@code gameTime} as a report's clock ({@code D12 0630}). */
	static String timestamp(Level level, long gameTime) {
		return WeatherReports.timestamp(gameTime + level.getDayTime() - level.getGameTime());
	}

	/** The 128 x 128 picture a save or print takes: the view's centre, zoom and frame. */
	private static byte[] picture(WxMosaic mosaic, CompoundTag data) {
		return mosaic.view(data.getInt("x"), data.getInt("z"), PhotoImage.SIZE, PhotoImage.SIZE, zoom(data.getInt("zoom")), data.getInt("frame"));
	}

	private static void save(ComputerContext context, CompoundTag data, ComputerReply reply) {
		String name = ComputerStorage.normalise(data.getString("name"));
		if(name == null || !"IMG".equals(ComputerStorage.extension(name))) {
			reply.error(ComputerStorage.Problem.BAD_NAME);
			return;
		}
		ComputerStorage.Problem problem = context.files().write(name, darkened(picture(store(context.computer()).mosaic(), data)));
		if(problem != null)
			reply.error(problem);
		else
			reply.ok();
	}

	private static void print(ComputerContext context, CompoundTag data, ComputerReply reply) {
		ScannerBlockEntity scanner = context.scanner();
		if(scanner == null) {
			reply.error("no_scanner");
			return;
		}
		WxMosaic mosaic = store(context.computer()).mosaic();
		byte[] pixels = picture(mosaic, data);
		ScannerBlockEntity.Result result = scanner.printImage(darkened(pixels), "WXSAT " + area(data), context.player().getGameProfile().getName(),
				captionLines(context.level(), mosaic, data, pixels));
		if(result != ScannerBlockEntity.Result.OK)
			reply.error(result.key());
		else
			reply.ok();
	}

	/** No-data pixels (colour 0, which a map shows as see-through) as black, for a file or a print. */
	static byte[] darkened(byte[] pixels) {
		byte black = net.minecraft.world.level.material.MapColor.COLOR_BLACK.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.LOWEST);
		byte[] out = pixels.clone();
		for(int i = 0; i < out.length; i++)
			if(out[i] == 0)
				out[i] = black;
		return out;
	}

	/** The area a 128 x 128 picture of the view covers, {@code X 100..1124 Z -300..724}. */
	static String area(CompoundTag data) {
		int span = PhotoImage.SIZE * zoom(data.getInt("zoom"));
		int x0 = data.getInt("x") - span / 2, z0 = data.getInt("z") - span / 2;
		return String.format(Locale.ROOT, "X%d..%d Z%d..%d", x0, x0 + span, z0, z0 + span);
	}

	/** What plain paper prints: the area, the pass and its time, and the weather in it. */
	static List<String> captionLines(ServerLevel level, WxMosaic mosaic, CompoundTag data, byte[] pixels) {
		List<String> out = new ArrayList<>();
		out.add("WEATHER SATELLITE PICTURE");
		out.add(area(data) + ", " + zoom(data.getInt("zoom")) + " BLOCKS/PX");
		WxMosaic.Pass pass = pass(mosaic, data.getInt("frame"));
		out.add(pass == null ? "NO PASSES RECEIVED" : "PASS " + pass.satellite() + " " + timestamp(level, pass.started()));
		out.add(RadarScale.summary(pixels));
		return out;
	}

	@Nullable
	private static WxMosaic.Pass pass(WxMosaic mosaic, int frame) {
		if(mosaic.passCount() == 0)
			return null;
		return mosaic.passes().get(frame < 0 || frame >= mosaic.passCount() ? mosaic.latest() : frame);
	}
}
