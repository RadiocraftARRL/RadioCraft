package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.network.serverbound.SStationPacket;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One radio computer as this player's game sees it (1.12 step 5): the programs running on its screen (the shell at the
 * bottom, the program in front on top), what the server last said (installed programs, the floppy in the drive), the
 * requests waiting for an answer, and a picture of the screen for the monitor in the world. Kept for {@link #KEEP_MS}
 * after the screen closes, so opening it again carries on where it was left (no boot, the editor's text still there).
 * <p>
 * The programs' way to the server: {@link #request} (any platform request, see
 * {@link com.arrl.radiocraft.common.computer.ComputerServer}), {@link #app} (the program's own server side),
 * {@link #files()} (the disk), {@link #scanner()} (scanning and printing).
 */
public final class ComputerSession {

	/** How long a closed computer's screen is kept, ms. */
	public static final long KEEP_MS = 10L * 60L * 1000L;
	/** Most requests left waiting; past this the oldest is forgotten (its program never hears back). */
	private static final int MAX_PENDING = 256;

	private static final Map<BlockPos, ComputerSession> SESSIONS = new HashMap<>();
	/** The session whose screen was opened last: answers from the server go to it. */
	@Nullable
	private static ComputerSession active;
	private static int mirrors = 0;

	final BlockPos pos;
	BlockPos monitorPos;
	private final ClientLevel level;
	private final Deque<DosApp> stack = new ArrayDeque<>();
	private final Map<Integer, Consumer<ComputerResponse>> pending = new LinkedHashMap<>();
	private final ComputerFiles files = new ComputerFiles(this);
	private final ComputerScanner scanner = new ComputerScanner(this);
	private int nextRid = 1;
	private boolean open = false;
	private boolean everOpened = false;
	private long closedAt = -1L;

	// What the server last said.
	private final List<String> apps = new ArrayList<>();
	private String floppy = "";
	private String serial = "";
	private boolean helloReceived = false;

	// The status line's message.
	private String message = "";
	private boolean messageIsError = false;
	private long messageAt = 0L;

	// The monitor's picture.
	@Nullable
	private TextureTarget mirror;
	@Nullable
	private ResourceLocation mirrorId;
	private int frames = 0;

	private ComputerSession(BlockPos pos, BlockPos monitorPos, ClientLevel level) {
		this.pos = pos.immutable();
		this.monitorPos = monitorPos.immutable();
		this.level = level;
		for(ComputerApp app : ComputerApps.builtIn())
			apps.add(app.id());
		stack.push(new ShellApp(this));
	}

	/** The session for the computer at {@code pos}, shown on the monitor at {@code monitor}: the kept one, or a new one. */
	static ComputerSession open(BlockPos pos, BlockPos monitor) {
		forget();
		ClientLevel level = Minecraft.getInstance().level;
		ComputerSession session = SESSIONS.get(pos);
		if(session == null || session.open || session.level != level) {
			if(session != null)
				session.release();
			session = new ComputerSession(pos, monitor, level);
			SESSIONS.put(session.pos, session);
		}
		session.monitorPos = monitor.immutable();
		session.open = true;
		active = session;
		return session;
	}

	/** True if the screen was open before in this session (it carries on: no boot). */
	boolean resumed() {
		return everOpened;
	}

	void markOpened() {
		everOpened = true;
	}

	/** The screen closed: the programs are kept (see the class comment). */
	void closed() {
		open = false;
		closedAt = System.currentTimeMillis();
		for(DosApp app : stack)
			app.closed();
	}

	private static void forget() {
		long now = System.currentTimeMillis();
		ClientLevel level = Minecraft.getInstance().level;
		for(Iterator<ComputerSession> it = SESSIONS.values().iterator(); it.hasNext(); ) {
			ComputerSession session = it.next();
			boolean music = ComputerMusic.isPlayingAt(session.pos) && session.level == level; // Its music page stays while it plays.
			if(!session.open && (now - session.closedAt > KEEP_MS && !music || session.level != level)) {
				session.release();
				if(active == session)
					active = null;
				it.remove();
			}
		}
		if(SESSIONS.isEmpty())
			DosImage.releaseAll();
	}

	private void release() {
		if(pos.equals(com.arrl.radiocraft.common.computer.apps.HandProgramming.clientComputer))
			com.arrl.radiocraft.common.computer.apps.HandProgramming.clientComputer = null; // Its CPS is gone: no more programming by hand here.
		if(mirrorId != null)
			Minecraft.getInstance().getTextureManager().release(mirrorId);
		if(mirror != null)
			mirror.destroyBuffers();
		mirror = null;
		mirrorId = null;
		for(Consumer<ComputerResponse> waiting : pending.values())
			waiting.accept(ComputerResponse.lost());
		pending.clear();
	}

	// -------------------- TALKING TO THE SERVER --------------------

	/**
	 * Sends {@code action} with {@code data} (a copy is sent, with the request id added) to the computer's server side;
	 * {@code done} gets the answer on the client thread.
	 */
	public void request(String action, CompoundTag data, Consumer<ComputerResponse> done) {
		int rid = nextRid++;
		CompoundTag sent = data.copy();
		sent.putInt("rid", rid);
		pending.put(rid, done);
		if(pending.size() > MAX_PENDING) {
			Iterator<Integer> oldest = pending.keySet().iterator();
			oldest.next();
			oldest.remove();
		}
		SStationPacket.send(pos, action, sent);
	}

	public void request(String action, Consumer<ComputerResponse> done) {
		request(action, new CompoundTag(), done);
	}

	/** Sends {@code action} with {@code data} to {@code app}'s own server side ({@code ComputerAppHandler}). */
	public void app(ComputerApp app, String action, CompoundTag data, Consumer<ComputerResponse> done) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", app.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		request("app", wrapped, done);
	}

	public ComputerFiles files() {
		return files;
	}

	public ComputerScanner scanner() {
		return scanner;
	}

	/** Asks the server again which programs are installed and what is in the drive. */
	public void refresh() {
		request("open", response -> {});
	}

	/** An answer or event from the server (a {@code CComputerPacket}). */
	static void receive(String kind, CompoundTag data) {
		ComputerSession session = active;
		if(session == null)
			return;
		if("reply".equals(kind)) {
			Consumer<ComputerResponse> done = session.pending.remove(data.getInt("rid"));
			CompoundTag answer = data.getCompound("data");
			session.learn(answer);
			if(done != null)
				done.accept(ComputerResponse.of(data));
		}
		else if("event".equals(kind)) {
			String app = data.getString("app");
			for(DosApp running : new ArrayList<>(session.stack))
				if(running.app() != null && running.app().id().equals(app))
					running.event(data.getString("kind"), data.getCompound("data"));
		}
	}

	/** Picks up the programs list, the drive and the serial number from any answer that carries them. */
	private void learn(CompoundTag answer) {
		if(answer.contains("apps", Tag.TAG_LIST)) {
			ListTag list = answer.getList("apps", Tag.TAG_STRING);
			apps.clear();
			for(int i = 0; i < list.size(); i++)
				apps.add(list.getString(i));
			helloReceived = true;
		}
		if(answer.contains("floppy", Tag.TAG_STRING))
			floppy = answer.getString("floppy");
		if(answer.contains("serial", Tag.TAG_STRING))
			serial = answer.getString("serial");
	}

	// -------------------- WHAT IT KNOWS --------------------

	/** The programs on this computer, in shell order (the built-in ones until the server has answered). */
	public List<ComputerApp> apps() {
		List<ComputerApp> out = new ArrayList<>();
		for(String id : apps) {
			ComputerApp app = ComputerApps.get(id);
			if(app != null)
				out.add(app);
		}
		return out;
	}

	public boolean isInstalled(ComputerApp app) {
		return app.builtIn() || apps.contains(app.id());
	}

	/** What is in the drive: "" for nothing, "blank", or a program's id. */
	public String floppy() {
		return floppy;
	}

	/** The computer's serial number (the start of its id). */
	public String serial() {
		return serial;
	}

	boolean helloReceived() {
		return helloReceived;
	}

	public BlockPos pos() {
		return pos;
	}

	// -------------------- PROGRAMS --------------------

	/** The program in front. */
	public DosApp current() {
		return stack.peek();
	}

	/** Starts {@code app} in front (the "not available yet" page if it has no screen). */
	public DosApp launch(ComputerApp app) {
		DosApp started = ComputerAppScreens.create(this, app);
		DosApp behind = stack.peek();
		stack.push(started);
		if(behind != null)
			behind.covered();
		started.opened();
		return started;
	}

	/** Starts {@code app} with file {@code name} open in it. */
	public DosApp launch(ComputerApp app, String name) {
		DosApp started = launch(app);
		started.openFile(name);
		return started;
	}

	/**
	 * Brings the MUSIC page to the front (the note in the top bar): the one already running, or a new one. Nothing if
	 * it is in front already.
	 */
	public void showMusic() {
		DosApp front = stack.peek();
		if(front != null && front.app() == ComputerApps.MUSIC)
			return;
		for(DosApp running : stack)
			if(running.app() == ComputerApps.MUSIC) {
				stack.remove(running);
				stack.push(running);
				if(front != null)
					front.covered();
				running.resumed();
				return;
			}
		launch(ComputerApps.MUSIC);
	}

	/** Ends {@code app} (never the shell); the one behind it comes to the front. */
	public void exit(DosApp app) {
		if(stack.size() <= 1 || app == stack.peekLast())
			return;
		boolean front = app == stack.peek();
		stack.remove(app);
		if(front)
			stack.peek().resumed();
	}

	void tick() {
		DosApp front = current();
		if(front != null)
			front.tick();
	}

	// -------------------- STATUS LINE --------------------

	/** Flashes {@code text} on the screen's bottom line for a few seconds (the shell and programs use it for news). */
	public void message(String text, boolean error) {
		message = text;
		messageIsError = error;
		messageAt = System.currentTimeMillis();
	}

	/** The status line's message while it is fresh, else empty. */
	public String message() {
		return System.currentTimeMillis() - messageAt < 6000L ? message : "";
	}

	public boolean messageIsError() {
		return messageIsError;
	}

	// -------------------- THE MONITOR --------------------

	/** The picture left on the monitor at {@code monitor}, or null if none (it shows the model's own screen). */
	@Nullable
	public static ResourceLocation monitorPicture(BlockPos monitor) {
		forget();
		for(ComputerSession session : SESSIONS.values())
			if(session.monitorPos.equals(monitor))
				return session.mirrorId;
		return null;
	}

	/** Copies the text screen (at {@code x, y}, {@code w} by {@code h} GUI pixels) into the monitor's picture, every few frames. */
	void capture(GuiGraphics g, double x, double y, double w, double h) {
		if(frames++ % 4 != 0)
			return;
		g.flush();
		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget main = minecraft.getMainRenderTarget();
		if(mirror == null) {
			mirror = new TextureTarget(TextGrid.WIDTH, TextGrid.HEIGHT, false, Minecraft.ON_OSX);
			mirror.setFilterMode(GL11.GL_LINEAR);
			mirrorId = Radiocraft.id("computer_monitor/" + mirrors++);
			minecraft.getTextureManager().register(mirrorId, new MirrorTexture(mirror.getColorTextureId()));
		}
		double scale = minecraft.getWindow().getGuiScale();
		int x0 = (int)Math.round(x * scale), x1 = (int)Math.round((x + w) * scale);
		int y0 = (int)Math.round(y * scale), y1 = (int)Math.round((y + h) * scale);
		// GL counts rows from the bottom.
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mirror.frameBufferId);
		GlStateManager._glBlitFrameBuffer(x0, main.height - y1, x1, main.height - y0, 0, 0, TextGrid.WIDTH, TextGrid.HEIGHT,
				GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
		mirror.bindWrite(false); // The screen's alpha is whatever its blending left; the picture is opaque.
		RenderSystem.colorMask(false, false, false, true);
		RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
		RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
		RenderSystem.colorMask(true, true, true, true);
		main.bindWrite(true);
	}

	/** The picture's colour texture, owned by its render target (which frees it), registered so a render type can use it. */
	private static final class MirrorTexture extends AbstractTexture {
		MirrorTexture(int id) {
			this.id = id;
		}

		@Override
		public void load(ResourceManager resourceManager) {}

		@Override
		public void releaseId() {
			id = -1;
		}

		@Override
		public void close() {
			id = -1;
		}
	}
}
