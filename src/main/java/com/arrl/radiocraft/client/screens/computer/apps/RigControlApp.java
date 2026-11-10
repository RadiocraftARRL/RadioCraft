package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerResponse;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.common.computer.Waterfall;
import com.arrl.radiocraft.common.radio.BandScope;
import com.arrl.radiocraft.common.radio.bandplan.BandPlan;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RIGCTL, rig control and a live waterfall (computer batch 3B): the radio on the computer's radio interface, driven from
 * the keyboard as CAT software drives a real rig. The big display shows the frequency, mode, band, power, PTT, S-meter
 * and squelch; under it the waterfall scrolls down across the band (or a quarter, or a sixteenth of it round the
 * dial, Z, and a sixty-fourth), a column per bin, brighter for stronger signals, with the dial marked; click a signal to tune to it, or
 * Ctrl+arrows to jump to the next one. Left/Right tune a step (Shift: ten), F types a frequency, PgUp/PgDn change band
 * (the all-band radio), M the mode, P the power, T the PTT. S scans a range and F3 the memory channels (the Frequency
 * Manager's list, F5 loads another .FRQ, F4 stores the dial), stopping on a signal over the squelch (Q); Space holds
 * the scan, X stops it. The server side is {@code RigControlHandler}; the waterfall rows are its band scope, asked for
 * four times a second while this is on screen.
 * <p>
 * F6 opens the Remote page (F2 is Minecraft's screenshot key) (2026-11-09): L switches the audio between LOCAL (the rig's own speaker and mics) and REMOTE
 * (this computer is the rig's speaker, and T's PTT takes your voice into it); A, W and C set whether and to whom this
 * computer lets another drive its rig (REMOTE ACCESS, OWNER or ANYONE WITH CODE, the access code); the list shows the
 * computers on this one's network that allow it, by network name and link (WIRE, MW GOOD/FAIR/POOR): Enter connects
 * (asking for the code), D disconnects, K takes the rig back from a remote user here. Connected, every key on the main
 * page drives the host's rig, and the top line says so; at the host it says who drives it from where (F8 takes over).
 */
public final class RigControlApp extends DosApp {

	private static final int DISPLAY_ROW = 2, SCALE_ROW = 6, FALL_TOP = 7, FALL_ROWS = 14, FALL_LEFT = 2, LABEL_ROW = FALL_TOP + FALL_ROWS;
	private static final long SAMPLE_MS = 250L, STATUS_MS = 5_000L;
	/** The waterfall's colours, faint to strong: themed like any of the sixteen. */
	private static final int[] RAMP = { DosColours.BLUE, DosColours.LIGHT_BLUE, DosColours.CYAN, DosColours.LIGHT_CYAN, DosColours.LIGHT_GREEN,
			DosColours.YELLOW, DosColours.LIGHT_RED, DosColours.WHITE };

	private CompoundTag station = new CompoundTag();
	private CompoundTag rig = new CompoundTag();
	private final byte[][] rows = new byte[FALL_ROWS][BandScope.BINS];
	private final long[] rowSeeds = new long[FALL_ROWS];
	private long rowCount;
	private double lo, hi, noise;
	private long lastSample, lastStatus;
	private boolean sampling;
	private final List<String> memoryNames = new ArrayList<>();
	private final List<Double> memoryMhz = new ArrayList<>();
	private int memoryAt = -1;
	/** The Remote page: shown instead of the display and waterfall. */
	private boolean remotePage;
	private CompoundTag remote = new CompoundTag();
	private ListTag hosts = new ListTag();
	private int hostAt;
	private static final int HOSTS_TOP = 14, HOSTS_ROWS = 5;

	public RigControlApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	public void opened() {
		status();
	}

	@Override
	public void resumed() {
		status();
	}

	@Override
	public void tick() {
		long now = System.currentTimeMillis();
		if(!sampling && now - lastSample >= SAMPLE_MS && station.getString("problem").isEmpty() && rig.contains("hz"))
			sample();
		if(now - lastStatus > STATUS_MS)
			status();
	}

	@Override
	protected boolean onEscape() {
		if(remotePage) {
			remotePage = false;
			return true;
		}
		session.app(app, "leave", new CompoundTag(), response -> {});
		exit();
		return true;
	}

	private void status() {
		lastStatus = System.currentTimeMillis();
		session.app(app, "status", new CompoundTag(), response -> {
			if(!response.ok())
				return;
			station = response.data();
			rig = station.getCompound("rig");
			remote = station.getCompound("remote");
			offerRemote();
			offerPick();
			memoryNames.clear();
			memoryMhz.clear();
			ListTag list = station.getList("memories", Tag.TAG_COMPOUND);
			for(int i = 0; i < list.size(); i++) {
				FrequencyList.Entry entry = FrequencyList.Entry.fromTag(list.getCompound(i));
				memoryNames.add(entry.name());
				memoryMhz.add(entry.rxMhz());
			}
		});
	}

	private void sample() {
		sampling = true;
		lastSample = System.currentTimeMillis();
		session.app(app, "scope", new CompoundTag(), response -> {
			sampling = false;
			if(response.ok())
				take(response.data());
		});
	}

	@Override
	public void event(String kind, CompoundTag data) {
		if(kind.equals("scope")) // Pushed while the screen is closed and we are near the monitor.
			take(data);
		else if(kind.equals("remote") || kind.equals("remote_ended")) { // A session began or ended: the screen follows.
			if(kind.equals("remote") && !data.getString("player").isEmpty() && data.getString("reason").isEmpty())
				session.message(Dos.tr("rig.remote.started", data.getString("player")), false);
			status();
			if(remotePage)
				refreshRemote();
		}
	}

	/** A new waterfall row and the rig's state. */
	private void take(CompoundTag data) {
		rig = data;
		double newLo = data.getDouble("lo"), newHi = data.getDouble("hi");
		if(newLo != lo || newHi != hi) { // Another span: the old rows mean other frequencies.
			for(byte[] row : rows)
				java.util.Arrays.fill(row, (byte)0);
			lo = newLo;
			hi = newHi;
		}
		noise = data.getDouble("noise");
		for(int i = FALL_ROWS - 1; i > 0; i--) {
			System.arraycopy(rows[i - 1], 0, rows[i], 0, BandScope.BINS);
			rowSeeds[i] = rowSeeds[i - 1];
		}
		byte[] row = data.getByteArray("row");
		for(int i = 0; i < BandScope.BINS; i++)
			rows[0][i] = i < row.length ? row[i] : 0;
		rowSeeds[0] = ++rowCount * 0x9E3779B97F4A7C15L;
	}

	// -------------------- ACTIONS --------------------

	private void act(String action, CompoundTag data) {
		session.app(app, action, data, this::answered);
	}

	private void answered(ComputerResponse response) {
		if(!response.ok()) {
			session.message(response.errorText(), true);
			return;
		}
		CompoundTag data = response.data();
		if(data.contains("rig")) {
			station = data;
			rig = data.getCompound("rig");
			remote = data.getCompound("remote");
		}
	}

	// -------------------- THE REMOTE PAGE --------------------

	/** Whether the Remote page has been offered since the program started (once: Esc goes back to the main page). */
	private boolean remoteOffered;

	/**
	 * No radio on this computer's own station (2026-11-09): say so and go straight to the Remote page, where any rig
	 * this computer can reach over its network is listed to connect to.
	 */
	private void offerRemote() {
		String problem = station.getString("problem");
		if(remoteOffered || remote.contains("to") || !(problem.equals("station_no_interface") || problem.equals("rig_no_radio")))
			return;
		remoteOffered = true;
		remotePage = true;
		refreshRemote();
		session.message(Dos.tr("rig.remote.no_local"), false);
	}

	/** Asked once per opening whether the computer's own radio should be picked. */
	private boolean pickOffered;

	/**
	 * More than one radio on this computer's bus and none picked yet (2026-11-09): the picker comes up by itself, once
	 * (R brings it back; the choice is kept with the computer). Not while driving a remote host's rig.
	 */
	private void offerPick() {
		if(pickOffered || remote.contains("to") || remotePage || hasDialog() || station.getBoolean("picked")
				|| station.getList("radios", Tag.TAG_COMPOUND).size() < 2 || !station.getString("problem").isEmpty())
			return;
		pickOffered = true;
		pickRadio();
	}

	private void refreshRemote() {
		session.app(app, "remote", new CompoundTag(), this::remoteAnswered);
		session.app(app, "hosts", new CompoundTag(), response -> {
			if(response.ok()) {
				hosts = response.data().getList("hosts", Tag.TAG_COMPOUND);
				hostAt = Math.max(0, Math.min(hostAt, hosts.size() - 1));
			}
		});
	}

	private void remoteAnswered(ComputerResponse response) {
		if(!response.ok()) {
			session.message(response.errorText(), true);
			return;
		}
		remote = response.data();
	}

	private void remoteAct(String action, CompoundTag data) {
		session.app(app, action, data, response -> {
			remoteAnswered(response);
			if(response.ok())
				status();
		});
	}

	private void setHost(boolean access, boolean anyone) {
		CompoundTag data = new CompoundTag();
		data.putBoolean("access", access);
		data.putBoolean("anyone", anyone);
		remoteAct("host", data);
	}

	private void askCode() {
		DosInput field = new DosInput(remote.getString("code"), com.arrl.radiocraft.common.computer.apps.RigControlHandler.MAX_CODE, c -> c > ' ' && c <= '~');
		showDialog(DosDialog.ask(Dos.tr("rig.remote.code_title"), Dos.tr("rig.remote.code_prompt"), field, value -> {
			CompoundTag data = new CompoundTag();
			data.putString("code", value);
			remoteAct("code", data);
		}));
	}

	private void connect() {
		if(hosts.isEmpty()) {
			session.message(Dos.tr("rig.remote.no_hosts"), true);
			return;
		}
		CompoundTag host = hosts.getCompound(Math.max(0, Math.min(hostAt, hosts.size() - 1)));
		if(host.getBoolean("code"))
			showDialog(DosDialog.ask(Dos.tr("rig.remote.code_title"), Dos.tr("rig.remote.enter_code", host.getString("name")),
					new DosInput("", com.arrl.radiocraft.common.computer.apps.RigControlHandler.MAX_CODE).masked(), code -> connect(host, code)));
		else
			connect(host, "");
	}

	private void connect(CompoundTag host, String code) {
		CompoundTag data = new CompoundTag();
		data.putString("id", host.getString("id"));
		data.putString("code", code);
		session.app(app, "connect", data, response -> {
			if(!response.ok()) {
				session.message(response.errorText(), true);
				return;
			}
			remote = response.data();
			remotePage = false;
			session.message(Dos.tr("rig.remote.connected", host.getString("name"), host.getString("link")), false);
			status();
		});
	}

	private boolean remoteKey(int key) {
		boolean configure = remote.getBoolean("mayConfigure");
		switch(key) {
			case GLFW.GLFW_KEY_F6 -> remotePage = false;
			case GLFW.GLFW_KEY_UP -> hostAt = Math.max(0, hostAt - 1);
			case GLFW.GLFW_KEY_DOWN -> hostAt = Math.max(0, Math.min(hosts.size() - 1, hostAt + 1));
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> connect();
			case GLFW.GLFW_KEY_D -> remoteAct("disconnect", new CompoundTag());
			case GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_F8 -> remoteAct("takeover", new CompoundTag());
			case GLFW.GLFW_KEY_H -> refreshRemote();
			case GLFW.GLFW_KEY_L -> {
				CompoundTag data = new CompoundTag();
				data.putBoolean("remote", !remote.getBoolean("audio"));
				remoteAct("audio", data);
			}
			case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_C -> {
				if(!configure) {
					session.message(Dos.tr("err.remote_owner_settings"), true);
					return true;
				}
				if(key == GLFW.GLFW_KEY_A)
					setHost(!remote.getBoolean("access"), remote.getBoolean("anyone"));
				else if(key == GLFW.GLFW_KEY_W)
					setHost(remote.getBoolean("access"), !remote.getBoolean("anyone"));
				else
					askCode();
			}
			default -> {
				return false;
			}
		}
		Dos.click();
		return true;
	}

	private void renderRemote(TextGrid g) {
		int bg = DosColours.BODY_BG, fg = DosColours.TEXT_FG, dim = DosColours.DIM;
		g.box(0, 2, TextGrid.COLS, 20, true, Dos.tr("rig.remote.box"), DosColours.BODY_FG, bg);
		boolean audio = remote.getBoolean("audio");
		int col = g.put(2, 3, Dos.tr("rig.remote.audio"), fg, bg);
		// The setting in force is highlighted (black on cyan: blue on the blue page did not show, 2026-11-09).
		col = g.put(col + 1, 3, Dos.tr("rig.remote.local"), audio ? dim : DosColours.HIGHLIGHT_FG, audio ? bg : DosColours.HIGHLIGHT_BG);
		col = g.put(col + 1, 3, Dos.tr("rig.remote.remote"), audio ? DosColours.HIGHLIGHT_FG : dim, audio ? DosColours.HIGHLIGHT_BG : bg);
		g.put(col + 2, 3, Dos.fit(Dos.tr(audio ? "rig.remote.audio_remote" : "rig.remote.audio_local"), TextGrid.COLS - col - 4), dim, bg);

		String myName = remote.getString("myName");
		g.put(2, 5, Dos.tr("rig.remote.host_title", myName.isEmpty() ? Dos.tr("rig.remote.no_name") : myName), DosColours.ACCENT, bg);
		boolean access = remote.getBoolean("access");
		g.put(4, 6, Dos.tr("rig.remote.access", Dos.tr(access ? "rig.remote.on" : "rig.remote.off")), fg, bg);
		g.put(4, 7, Dos.tr("rig.remote.who", Dos.tr(remote.getBoolean("anyone") ? "rig.remote.anyone" : "rig.remote.owner")), fg, bg);
		String code = remote.getString("code");
		g.put(4, 8, Dos.tr("rig.remote.code", !remote.getBoolean("hasCode") ? Dos.tr("rig.remote.no_code")
				: code.isEmpty() ? "******" : code), fg, bg);
		if(!remote.getBoolean("mayConfigure"))
			g.put(44, 6, Dos.tr("rig.remote.owner_sets"), dim, bg);
		if(remote.contains("by"))
			g.put(4, 9, Dos.fit(Dos.tr("rig.remote.hosting", remote.getString("by"), remote.getString("via"), remote.getString("viaLink")), TextGrid.COLS - 6),
					DosColours.YELLOW, bg);
		else
			g.put(4, 9, Dos.tr("rig.remote.no_session"), dim, bg);

		g.put(2, 11, Dos.tr("rig.remote.connect_title"), DosColours.ACCENT, bg);
		if(remote.contains("to"))
			g.put(4, 12, Dos.fit(Dos.tr("rig.remote.driving_page", remote.getString("to"), remote.getString("toLink")), TextGrid.COLS - 6), DosColours.YELLOW, bg);
		else
			g.put(4, 12, Dos.tr("rig.remote.not_connected"), dim, bg);
		g.put(4, 13, Dos.fit(Dos.tr("rig.remote.list_head"), TextGrid.COLS - 6), dim, bg);
		if(hosts.isEmpty())
			g.put(4, HOSTS_TOP, Dos.tr("rig.remote.no_hosts"), dim, bg);
		int first = Math.max(0, Math.min(hostAt - HOSTS_ROWS + 1, hosts.size() - HOSTS_ROWS));
		for(int i = 0; i < HOSTS_ROWS && first + i < hosts.size(); i++) {
			CompoundTag host = hosts.getCompound(first + i);
			boolean picked = first + i == hostAt;
			String line = String.format(Locale.ROOT, "%-20s %-12s %-5s %-6s %s", Dos.fit(host.getString("name"), 20), Dos.fit(host.getString("link"), 12),
					host.getBoolean("code") ? Dos.tr("rig.remote.yes") : "-", host.getBoolean("ownerOnly") ? Dos.tr("rig.remote.owner_short") : Dos.tr("rig.remote.anyone_short"),
					!host.getBoolean("on") ? Dos.tr("rig.remote.off_short") : host.getBoolean("busy") ? Dos.tr("rig.remote.busy") : "");
			g.putField(3, HOSTS_TOP + i, " " + line, TextGrid.COLS - 6, picked ? DosColours.HIGHLIGHT_FG : fg, picked ? DosColours.HIGHLIGHT_BG : bg); // The cursor.
		}
		g.putField(1, 20, " " + Dos.tr("rig.remote.keys"), TextGrid.COLS - 2, DosColours.BODY_FG, bg);
	}

	private static CompoundTag tag(String key, double value) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble(key, value);
		return tag;
	}

	private static CompoundTag intTag(String key, int value) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(key, value);
		return tag;
	}

	private void tuneTo(double hz) {
		act("tune", tag("hz", hz));
	}

	/** The strongest level seen lately per bin (the last few rows), for jumping to signals. */
	private int[] recentLevels() {
		int[] out = new int[BandScope.BINS];
		for(int r = 0; r < Math.min(4, FALL_ROWS); r++)
			for(int i = 0; i < out.length; i++)
				out[i] = Math.max(out[i], rows[r][i]);
		return out;
	}

	private void jump(int dir) {
		if(!(hi > lo))
			return;
		int from = Waterfall.bin(rig.getDouble("hz"), lo, hi, BandScope.BINS);
		int to = Waterfall.nextSignal(recentLevels(), Math.max(0, from), dir, 2);
		if(to < 0) {
			session.message(Dos.tr("rig.no_signal"), false);
			return;
		}
		tuneTo(Waterfall.centre(to, lo, hi, BandScope.BINS));
	}

	private void memory(int dir) {
		if(memoryMhz.isEmpty()) {
			session.message(Dos.tr("err.rig_no_memories"), true);
			return;
		}
		memoryAt = Math.floorMod(memoryAt + dir, memoryMhz.size());
		tuneTo(memoryMhz.get(memoryAt) * 1_000_000.0D);
		session.message(Dos.tr("rig.memory_tuned", memoryAt + 1, memoryNames.get(memoryAt)), false);
	}

	private void typedFrequency() {
		DosInput field = new DosInput(FrequencyList.mhz(rig.getDouble("hz") / 1_000_000.0D), 12, c -> Character.isDigit(c) || c == '.');
		showDialog(DosDialog.ask(Dos.tr("rig.freq_title"), Dos.tr("rig.freq_prompt"), field, value -> {
			double mhz = FrequencyList.parseMhz(value);
			if(mhz > 0.0D)
				tuneTo(mhz * 1_000_000.0D);
			else
				session.message(Dos.tr("err.rig_out_of_range"), true);
		}));
	}

	private void scanRange() {
		double from = hi > lo ? lo : rig.getDouble("bandLo"), to = hi > lo ? hi : rig.getDouble("bandHi");
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("rig.scan_from"), new DosInput(FrequencyList.mhz(from / 1_000_000.0D), 12, c -> Character.isDigit(c) || c == '.')),
				new DosFormDialog.Field(Dos.tr("rig.scan_to"), new DosInput(FrequencyList.mhz(to / 1_000_000.0D), 12, c -> Character.isDigit(c) || c == '.')));
		showDialog(new DosFormDialog(Dos.tr("rig.scan_title"), Dos.tr("rig.scan_help"), fields, values -> {
			double low = FrequencyList.parseMhz(values.get(0)), high = FrequencyList.parseMhz(values.get(1));
			if(!(low > 0.0D) || !(high > 0.0D))
				return Dos.tr("err.rig_bad_range");
			CompoundTag data = new CompoundTag();
			data.putString("mode", "range");
			data.putDouble("low", low * 1_000_000.0D);
			data.putDouble("high", high * 1_000_000.0D);
			act("scan", data);
			return null;
		}));
	}

	private void scanMemories() {
		CompoundTag data = new CompoundTag();
		data.putString("mode", "memory");
		act("scan", data);
	}

	private void storeMemory() {
		DosInput field = new DosInput("", FrequencyList.NAME_MAX);
		showDialog(DosDialog.ask(Dos.tr("rig.store_title"), Dos.tr("rig.store_prompt", station.getString("memoryFile")), field, value -> {
			CompoundTag data = new CompoundTag();
			data.putString("name", value);
			session.app(app, "store", data, response -> {
				answered(response);
				if(response.ok()) {
					session.message(Dos.tr("rig.stored", station.getString("memoryFile")), false);
					status();
				}
			});
		}));
	}

	private void loadMemories() {
		showDialog(new DosFilePicker(session, Dos.tr("rig.load_title"), ComputerFileFormats.FREQUENCIES, name -> {
			CompoundTag data = new CompoundTag();
			data.putString("file", name);
			session.app(app, "memories", data, response -> {
				answered(response);
				if(response.ok())
					status();
			});
		}));
	}

	private void pickRadio() {
		ListTag radios = station.getList("radios", Tag.TAG_COMPOUND);
		if(radios.size() < 2) {
			session.message(Dos.tr("rig.one_radio"), false);
			return;
		}
		List<String> lines = new ArrayList<>();
		for(int i = 0; i < radios.size(); i++) {
			CompoundTag radio = radios.getCompound(i);
			lines.add(Dos.fit(I18n.get(radio.getString("name")), 30) + "  " + mhz(radio.getDouble("hz"))
					+ (radio.contains("on") && !radio.getBoolean("on") ? "  " + Dos.tr("rig.pick_off") : ""));
		}
		showDialog(new DosPickDialog(Dos.tr("rig.pick_title"), Dos.tr("rig.pick_prompt"), lines, index -> {
			CompoundTag data = new CompoundTag();
			data.putLong("pos", radios.getCompound(index).getLong("pos"));
			act("select", data);
		}));
	}

	// -------------------- KEYS AND MOUSE --------------------

	/** T is held down: the rig is keyed until it is let go. */
	private boolean pttHeld;

	private void ptt(boolean on) {
		CompoundTag data = new CompoundTag();
		data.putBoolean("on", on);
		session.app(app, "ptt", data, response -> {
			answered(response);
			if(on && response.ok() && pttHeld)
				goAhead(); // Keyed: a beep says go ahead.
		});
	}

	@Override
	protected boolean onKeyUp(int key) {
		if(key != GLFW.GLFW_KEY_T || !pttHeld)
			return false;
		pttHeld = false;
		ptt(false);
		return true;
	}

	/** The go-ahead beep when RIGCTL keys the rig (2026-11-09): two short high pips at the computer, for this player. */
	private static void goAhead() {
		net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), 2.0F, 0.35F));
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(guide) { // Any key closes the key guide.
			guide = false;
			return true;
		}
		if(key == GLFW.GLFW_KEY_SLASH && Screen.hasShiftDown()) { // ?: every key, on one page.
			guide = true;
			return true;
		}
		if(remotePage)
			return remoteKey(key);
		boolean shift = Screen.hasShiftDown();
		switch(key) {
			case GLFW.GLFW_KEY_F6 -> {
				remotePage = true;
				refreshRemote();
			}
			case GLFW.GLFW_KEY_F8 -> {
				if(!remote.contains("by"))
					return false;
				act("takeover", new CompoundTag());
				status();
			}
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
				int dir = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
				if(Dos.ctrl())
					jump(dir);
				else
					act("step", intTag("steps", dir * (shift ? 10 : 1)));
			}
			case GLFW.GLFW_KEY_UP -> memory(1);
			case GLFW.GLFW_KEY_DOWN -> memory(-1);
			case GLFW.GLFW_KEY_PAGE_UP -> act("band", intTag("dir", 1));
			case GLFW.GLFW_KEY_PAGE_DOWN -> act("band", intTag("dir", -1));
			case GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> typedFrequency();
			case GLFW.GLFW_KEY_M -> act("mode", new CompoundTag());
			case GLFW.GLFW_KEY_P -> act("power", new CompoundTag());
			case GLFW.GLFW_KEY_T -> { // Held to talk (2026-11-09); the key repeating while held does not key it again.
				if(pttHeld)
					return true;
				pttHeld = true;
				ptt(true);
			}
			case GLFW.GLFW_KEY_C -> { // Close the screen with RIGCTL left running, as the music player does.
				Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
				if(screen != null)
					screen.onClose();
			}
			case GLFW.GLFW_KEY_S -> scanRange();
			case GLFW.GLFW_KEY_F3 -> scanMemories();
			case GLFW.GLFW_KEY_F4 -> storeMemory();
			case GLFW.GLFW_KEY_F5 -> loadMemories();
			case GLFW.GLFW_KEY_SPACE -> act("hold", new CompoundTag());
			case GLFW.GLFW_KEY_X -> {
				CompoundTag data = new CompoundTag();
				data.putString("mode", "off");
				act("scan", data);
			}
			case GLFW.GLFW_KEY_Z -> act("zoom", intTag("zoom", rig.getInt("zoom") + (shift ? -1 : 1)));
			case GLFW.GLFW_KEY_Q -> act("squelch", intTag("level", (rig.getInt("squelch") + (shift ? 9 : 1)) % 10));
			case GLFW.GLFW_KEY_R -> pickRadio();
			default -> {
				return false;
			}
		}
		Dos.click();
		return true;
	}

	/** Where the clickable parts of the display were drawn: the mode, PWR and SQL (columns, end exclusive). */
	private int modeFrom = -1, modeTo = -1, powerTo = -1, squelchTo = -1;
	private static final int POWER_COL = 52, SQUELCH_COL = 28;
	/** The key guide (?) is open. */
	private boolean guide;

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(guide) {
			guide = false;
			return true;
		}
		if(!remotePage && row == DISPLAY_ROW + 1 && col >= modeFrom && col < modeTo) { // Click the mode: the next one.
			act("mode", new CompoundTag());
			Dos.click();
			return true;
		}
		if(!remotePage && row == DISPLAY_ROW + 1 && col >= POWER_COL && col < powerTo) { // PWR: on or off.
			act("power", new CompoundTag());
			Dos.click();
			return true;
		}
		if(!remotePage && row == DISPLAY_ROW + 2 && col >= SQUELCH_COL && col < squelchTo) { // SQL: up, right click down.
			act("squelch", intTag("level", (rig.getInt("squelch") + (button == 1 ? 9 : 1)) % 10));
			Dos.click();
			return true;
		}
		if(remotePage) {
			int index = Math.max(0, Math.min(hostAt - HOSTS_ROWS + 1, hosts.size() - HOSTS_ROWS)) + row - HOSTS_TOP;
			if(row >= HOSTS_TOP && row < HOSTS_TOP + HOSTS_ROWS && index >= 0 && index < hosts.size()) {
				if(index == hostAt)
					connect(); // A second click on the picked host connects.
				hostAt = index;
				return true;
			}
			return false;
		}
		if(row >= SCALE_ROW && row < FALL_TOP + FALL_ROWS && col >= FALL_LEFT && col < FALL_LEFT + BandScope.BINS && hi > lo) {
			tuneTo(Waterfall.centre(col - FALL_LEFT, lo, hi, BandScope.BINS));
			return true;
		}
		return false;
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		if(remotePage)
			return false;
		act("step", intTag("steps", amount > 0 ? 1 : -1));
		return true;
	}

	// -------------------- DRAWING --------------------

	static String mhz(double hz) {
		return String.format(Locale.ROOT, "%.3f", hz / 1_000_000.0D);
	}

	/** "14.074.00": MHz, kHz and tens of Hz, as a rig's display reads. */
	static String dial(double hz) {
		long tens = Math.round(hz / 10.0D);
		long mhz = tens / 100_000, khz = tens / 100 % 1000, rest = tens % 100;
		return String.format(Locale.ROOT, "%d.%03d.%02d", mhz, khz, rest);
	}

	/** How many lines the key guide has (rig.guide.1 to rig.guide.N). */
	private static final int GUIDE_LINES = 16;

	private void renderGuide(TextGrid g) {
		int top = 2, height = GUIDE_LINES + 3;
		g.box(1, top, TextGrid.COLS - 2, height, true, Dos.tr("rig.guide.title"), DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		g.fill(2, top + 1, TextGrid.COLS - 4, height - 2, ' ', DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		for(int i = 1; i <= GUIDE_LINES; i++) {
			String line = Dos.tr("rig.guide." + i);
			boolean heading = !line.startsWith(" ");
			g.put(3, top + i, Dos.fit(line, TextGrid.COLS - 6), heading ? DosColours.BLUE : DosColours.DIALOG_FG, DosColours.DIALOG_BG);
		}
		g.put(3, top + height - 2, Dos.tr("rig.guide.close"), DosColours.DARK_GREY, DosColours.DIALOG_BG);
	}

	@Override
	public void render(TextGrid g, long now) {
		String name = rig.contains("name") ? I18n.get(rig.getString("name")) : "";
		Dos.frame(g, Dos.tr("rig.title", name), Dos.tr("rig.keys"));
		int bg = DosColours.BODY_BG;
		if(guide) {
			renderGuide(g);
			return;
		}
		if(remotePage) {
			remoteBanner(g);
			renderRemote(g);
			footer(g, bg);
			return;
		}
		String problem = station.getString("problem");
		if(!problem.isEmpty()) {
			g.putField(0, 1, " " + Dos.tr("err." + problem), TextGrid.COLS, DosColours.ERROR_FG, DosColours.ERROR_BG);
			g.put(2, 4, Dos.tr("rig.need_interface"), DosColours.TEXT_FG, bg);
			footer(g, bg);
			return;
		}
		g.putField(0, 1, " " + Dos.tr("rig.station", station.getString("myCall").isEmpty() ? "-" : station.getString("myCall"),
				station.getList("radios", Tag.TAG_COMPOUND).size()), TextGrid.COLS, DosColours.BLACK, DosColours.LIGHT_GREY);
		remoteBanner(g);
		drawDisplay(g, now);
		drawScale(g);
		drawFall(g);
		footer(g, bg);
	}

	/** Row 1 while a session runs: whose rig this drives, or who drives this one from where. */
	private void remoteBanner(TextGrid g) {
		if(remote.contains("to"))
			g.putField(0, 1, " " + Dos.tr("rig.remote.driving", remote.getString("to"), remote.getString("toLink")), TextGrid.COLS, DosColours.WHITE, DosColours.MAGENTA);
		else if(remote.contains("by"))
			g.putField(0, 1, " " + Dos.tr("rig.remote.banner", remote.getString("by"), remote.getString("via"), remote.getString("viaLink")), TextGrid.COLS,
					DosColours.WHITE, DosColours.RED);
	}

	private void drawDisplay(TextGrid g, long now) {
		int fg = DosColours.FIELD_FG, field = DosColours.FIELD_BG;
		g.box(0, DISPLAY_ROW - 0, TextGrid.COLS, 4, true, "", DosColours.BODY_FG, DosColours.BODY_BG);
		g.fill(1, DISPLAY_ROW + 1, TextGrid.COLS - 2, 2, ' ', fg, field);
		boolean power = rig.getBoolean("power");
		int digits = power ? DosColours.ACCENT : DosColours.DIM;
		int col = g.put(2, DISPLAY_ROW + 1, dial(rig.getDouble("hz")), digits, field);
		col = g.put(col + 1, DISPLAY_ROW + 1, "MHz", DosColours.DIM, field);
		modeFrom = col + 3;
		col = g.put(col + 3, DISPLAY_ROW + 1, Dos.fit(rig.getString("mode"), 4), DosColours.TEXT_FG, field);
		modeTo = col;
		String band = rig.getString("band");
		col = g.put(col + 3, DISPLAY_ROW + 1, band.equals("low_band") ? Dos.tr("rig.low_band") : band, DosColours.TEXT_FG, field);
		if(rig.getBoolean("channelized"))
			g.put(col + 2, DISPLAY_ROW + 1, Dos.tr("rig.channelized"), DosColours.DIM, field);
		powerTo = g.put(POWER_COL, DISPLAY_ROW + 1, power ? Dos.tr("rig.power_on") : Dos.tr("rig.power_off"), DosColours.WHITE, power ? DosColours.GREEN : DosColours.DARK_GREY);
		boolean tx = rig.getBoolean("ptt");
		String txText = !rig.getBoolean("transmitter") ? " RX ONLY " : rig.getBoolean("inhibited") && tx ? " INHIBIT " : tx ? "   TX    " : "   RX    ";
		g.put(62, DISPLAY_ROW + 1, txText, DosColours.WHITE, tx ? DosColours.RED : DosColours.BLUE);
		g.put(72, DISPLAY_ROW + 1, Dos.tr("rig.zoom", 1 << (2 * Math.max(0, Math.min(Waterfall.ZOOMS - 1, rig.getInt("zoom"))))), DosColours.DIM, field);
		// S-meter, squelch, scan.
		int meter = rig.getInt("meter");
		g.put(2, DISPLAY_ROW + 2, "S", DosColours.DIM, field);
		for(int i = 0; i < 15; i++)
			g.set(4 + i, DISPLAY_ROW + 2, i < meter ? TextGrid.FULL : TextGrid.SHADE_LIGHT, i < meter ? (i >= 9 ? DosColours.BAD : DosColours.GOOD) : DosColours.DIM, field);
		g.put(4 + 16, DISPLAY_ROW + 2, meter >= 10 ? "+" + (meter - 9) * 10 : "S" + Math.min(9, (meter * 9 + 14) / 15), DosColours.TEXT_FG, field);
		squelchTo = g.put(SQUELCH_COL, DISPLAY_ROW + 2, Dos.tr("rig.squelch", rig.getInt("squelch")), DosColours.TEXT_FG, field);
		String scan = rig.getString("scan");
		if(!scan.isEmpty() && !scan.equals("OFF")) {
			boolean blink = (now / 400) % 2 == 0;
			String what = rig.getBoolean("scanHeld") ? Dos.tr("rig.scan_held") : rig.getBoolean("scanStopped") ? Dos.tr("rig.scan_stopped") : Dos.tr("rig.scanning");
			g.put(38, DISPLAY_ROW + 2, (scan.equals("MEMORY") ? Dos.tr("rig.scan_memory") : Dos.tr("rig.scan_range")) + " " + what,
					rig.getBoolean("scanStopped") || blink ? DosColours.ACCENT : DosColours.DIM, field);
		}
		else
			g.put(38, DISPLAY_ROW + 2, Dos.tr("rig.memories", memoryMhz.size(), station.getString("memoryFile")), DosColours.DIM, field);
	}

	private void drawScale(TextGrid g) {
		int bg = DosColours.BODY_BG;
		g.hline(FALL_LEFT, SCALE_ROW, BandScope.BINS, TextGrid.H, DosColours.BODY_FG, bg);
		if(!(hi > lo))
			return;
		for(BandPlan.Marker marker : BandPlan.current().markers()) { // The band plan's spots: FT8, PSK31, SSTV...
			int bin = Waterfall.bin(marker.hz(), lo, hi, BandScope.BINS);
			if(bin >= 0)
				g.set(FALL_LEFT + bin, SCALE_ROW, '+', DosColours.LIGHT_MAGENTA, bg);
		}
		int dial = Waterfall.bin(rig.getDouble("hz"), lo, hi, BandScope.BINS);
		if(dial >= 0)
			g.set(FALL_LEFT + dial, SCALE_ROW, TextGrid.ARROW_DOWN, DosColours.ACCENT, bg);
		g.set(FALL_LEFT - 1, SCALE_ROW, TextGrid.TL, DosColours.BODY_FG, bg);
		g.set(FALL_LEFT + BandScope.BINS, SCALE_ROW, TextGrid.TR, DosColours.BODY_FG, bg);
	}

	private void drawFall(TextGrid g) {
		int bg = DosColours.BODY_BG, field = DosColours.FIELD_BG;
		int dial = hi > lo ? Waterfall.bin(rig.getDouble("hz"), lo, hi, BandScope.BINS) : -1;
		double speckle = Math.max(0.02D, Math.min(0.35D, noise)); // More noise, more snow.
		for(int r = 0; r < FALL_ROWS; r++) {
			int row = FALL_TOP + r;
			g.set(FALL_LEFT - 1, row, TextGrid.V, DosColours.BODY_FG, bg);
			g.set(FALL_LEFT + BandScope.BINS, row, TextGrid.V, DosColours.BODY_FG, bg);
			long seed = rowSeeds[r];
			for(int i = 0; i < BandScope.BINS; i++) {
				int level = rows[r][i];
				if(level > 0) {
					int shade = Math.min(RAMP.length - 1, (level - 1) / 2);
					char c = (level & 1) == 1 && shade > 0 ? TextGrid.SHADE_DARK : TextGrid.FULL;
					g.set(FALL_LEFT + i, row, c, RAMP[shade], shade > 0 ? RAMP[shade - 1] : field);
					continue;
				}
				seed = seed * 6364136223846793005L + 1442695040888963407L;
				boolean snow = seed != 0L && rowSeeds[r] != 0L && ((seed >>> 40) & 0xFFFF) / 65536.0D < speckle * 0.4D;
				g.set(FALL_LEFT + i, row, snow ? TextGrid.SHADE_LIGHT : ' ', DosColours.BLUE, field);
			}
			if(dial >= 0 && rows[r][dial] == 0)
				g.set(FALL_LEFT + dial, row, ':', DosColours.DIM, field); // The dial, down the waterfall.
		}
		g.fill(FALL_LEFT - 1, LABEL_ROW, BandScope.BINS + 2, 1, ' ', DosColours.BODY_FG, bg);
		g.set(FALL_LEFT - 1, LABEL_ROW, TextGrid.BL, DosColours.BODY_FG, bg);
		g.hline(FALL_LEFT, LABEL_ROW, BandScope.BINS, TextGrid.H, DosColours.BODY_FG, bg);
		g.set(FALL_LEFT + BandScope.BINS, LABEL_ROW, TextGrid.BR, DosColours.BODY_FG, bg);
		if(hi > lo) {
			String left = mhz(lo), middle = mhz((lo + hi) / 2.0D), right = mhz(hi);
			g.put(FALL_LEFT, LABEL_ROW, " " + left + " ", DosColours.TEXT_FG, bg);
			g.put(FALL_LEFT + BandScope.BINS / 2 - middle.length() / 2 - 1, LABEL_ROW, " " + middle + " ", DosColours.TEXT_FG, bg);
			g.put(FALL_LEFT + BandScope.BINS - right.length() - 2, LABEL_ROW, " " + right + " ", DosColours.TEXT_FG, bg);
		}
		else
			g.put(FALL_LEFT + 2, FALL_TOP + 2, Dos.tr("rig.waiting"), DosColours.DIM, field);
	}

	private void footer(TextGrid g, int bg) {
		g.putField(0, 22, " " + Dos.tr("rig.hint"), TextGrid.COLS, DosColours.DIM, bg);
		String message = session.message();
		if(!message.isEmpty())
			g.putField(0, 23, " " + message, TextGrid.COLS, session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
		else
			g.putField(0, 23, " " + Dos.tr("rig.help"), TextGrid.COLS, DosColours.BODY_FG, bg);
	}
}
