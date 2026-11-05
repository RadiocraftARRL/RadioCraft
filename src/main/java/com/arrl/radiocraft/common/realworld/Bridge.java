package com.arrl.radiocraft.common.realworld;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One Real-World Interface's link to its sound card and serial port, on three threads of its own (never the server
 * thread):
 * <ul>
 *   <li><b>link</b> opens the devices (after the interface's previous bridge, if any, has let go of them), starts the
 *   other two, hands each frame the real radio sends into the game ({@code uplink}), retries missing devices every
 *   {@link #RETRY_MS}, and closes everything when told to stop.</li>
 *   <li><b>capture</b> reads the input, applies its gain, resamples to 48 kHz, cuts 20 ms frames and gates them by VOX
 *   or COS with the hang time and time-out ({@link KeyingTimer}). Nothing goes in while PTT is keyed: the real radio is
 *   simplex too.</li>
 *   <li><b>playback</b> takes the frames the game's receiver heard ({@link #offerDownlink}), keys PTT on the first (with
 *   the TX delay as silence), plays them resampled to the card's rate, sends silence through the hang time, and drops
 *   PTT after it or at the time-out.</li>
 * </ul>
 * Frames travel through queues of {@link #QUEUE_FRAMES} (half a second); a full queue drops its oldest frame, so a slow
 * side never holds up the other (the voice chat thread only ever offers). Status is in volatile fields for the block's
 * screen. Pure Java: no game classes; the device side is an {@link AudioBackend}.
 */
public final class Bridge {

	private static final Logger LOGGER = LoggerFactory.getLogger("radiocraft/real_world_interface");

	/** The game's voice: 48 kHz, 20 ms frames. */
	public static final int RATE = 48_000;
	public static final int FRAME = 960;
	public static final int QUEUE_FRAMES = 25;
	/** How often a device that failed to open is tried again. */
	public static final long RETRY_MS = 10_000L;
	/** How long COS stays open after the line drops (debounce). */
	public static final long COS_HANG_MS = 150L;
	/** How long the link waits for the interface's previous bridge to let go of the devices. */
	public static final long HANDOVER_MS = 3_000L;

	/** How each device is doing. */
	public enum DeviceState { UNUSED, OPENING, OPEN, NOT_FOUND, FAILED }

	/** Everything the screen shows about the bridge, read at once. */
	public record Status(DeviceState input, DeviceState output, DeviceState serial, String error, boolean pttKeyed, boolean uplinkOpen,
						 boolean txTimedOut, boolean rxTimedOut, double inputDb, double outputDb, boolean running) {}

	private final InterfaceSpec spec;
	private final AudioBackend backend;
	private volatile Consumer<short[]> uplink;
	@Nullable
	private final Bridge previous;
	private final BlockingQueue<short[]> downlink = new ArrayBlockingQueue<>(QUEUE_FRAMES);
	private final BlockingQueue<short[]> inbound = new ArrayBlockingQueue<>(QUEUE_FRAMES);
	private final CountDownLatch closed = new CountDownLatch(1);
	private final Thread link;
	private volatile boolean running = true;

	private volatile AudioBackend.Input input;
	private volatile AudioBackend.Output output;
	private volatile AudioBackend.Port port;
	private volatile DeviceState inputState = DeviceState.OPENING;
	private volatile DeviceState outputState = DeviceState.OPENING;
	private volatile DeviceState serialState;
	private volatile String error = "";
	private volatile boolean pttKeyed = false;
	private volatile boolean uplinkOpen = false;
	private volatile boolean txTimedOut = false;
	private volatile boolean rxTimedOut = false;
	private volatile double inputDb = AudioLevels.FLOOR_DB;
	private volatile double outputDb = AudioLevels.FLOOR_DB;

	private Bridge(InterfaceSpec spec, AudioBackend backend, Consumer<short[]> uplink, @Nullable Bridge previous) {
		this.spec = spec;
		this.backend = backend;
		this.uplink = uplink;
		this.previous = previous;
		serialState = spec.hasSerial() ? DeviceState.OPENING : DeviceState.UNUSED;
		link = new Thread(this::runLink, "RadioCraft RWI " + spec.name() + " link");
		link.setDaemon(true);
	}

	/**
	 * Starts a bridge for {@code spec}: {@code uplink} gets each 48 kHz frame the real radio sends while the gate is
	 * open, on the link thread. {@code previous} is the interface's last bridge, whose devices it waits for.
	 */
	public static Bridge start(InterfaceSpec spec, AudioBackend backend, Consumer<short[]> uplink, @Nullable Bridge previous) {
		Bridge bridge = new Bridge(spec, backend, uplink, previous);
		bridge.link.start();
		return bridge;
	}

	public InterfaceSpec spec() {
		return spec;
	}

	/** Points the frames from the real radio somewhere else (the block that owns the bridge was reloaded). */
	public void setUplink(Consumer<short[]> uplink) {
		this.uplink = uplink;
	}

	/** A frame the game's receiver heard (48 kHz, any length, normally 960): out to the real radio. Never blocks. */
	public void offerDownlink(short[] pcm) {
		if(running)
			offer(downlink, pcm);
	}

	private static void offer(BlockingQueue<short[]> queue, short[] frame) {
		while(!queue.offer(frame))
			queue.poll(); // Full: the oldest goes.
	}

	/** True while the real radio's audio is being put on the air in game (VOX/COS open, hang time included). */
	public boolean isUplinkOpen() {
		return uplinkOpen;
	}

	/** True while the real radio's PTT is keyed. */
	public boolean isPttKeyed() {
		return pttKeyed;
	}

	public boolean isRunning() {
		return running;
	}

	public Status status() {
		return new Status(inputState, outputState, serialState, error, pttKeyed, uplinkOpen, txTimedOut, rxTimedOut, inputDb, outputDb, running);
	}

	/** Tells the bridge to stop; it closes its devices on its own thread. Returns at once. */
	public void close() {
		running = false;
		link.interrupt();
	}

	/** Waits up to {@code ms} for the devices to be closed; true if they are. */
	public boolean awaitClosed(long ms) {
		try {
			return closed.await(ms, TimeUnit.MILLISECONDS);
		}
		catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			return closed.getCount() == 0;
		}
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	// -------------------- LINK THREAD --------------------

	private void runLink() {
		Thread capture = null, playback = null;
		try {
			if(previous != null && !previous.awaitClosed(HANDOVER_MS))
				LOGGER.warn("Real-World Interface {}: the previous link still has the devices; opening anyway", spec.name());
			if(!running)
				return;
			openMissing();
			capture = new Thread(this::runCapture, "RadioCraft RWI " + spec.name() + " capture");
			playback = new Thread(this::runPlayback, "RadioCraft RWI " + spec.name() + " playback");
			capture.setDaemon(true);
			playback.setDaemon(true);
			capture.start();
			playback.start();
			long lastTry = now();
			while(running) {
				short[] frame;
				try {
					frame = inbound.poll(50L, TimeUnit.MILLISECONDS);
				}
				catch(InterruptedException e) {
					break;
				}
				if(frame != null) {
					Consumer<short[]> to = uplink;
					try {
						to.accept(frame);
					}
					catch(RuntimeException e) {
						LOGGER.error("Real-World Interface {}: putting audio on the air failed", spec.name(), e);
					}
				}
				if(now() - lastTry >= RETRY_MS) {
					lastTry = now();
					openMissing();
				}
			}
		}
		catch(RuntimeException e) {
			error = String.valueOf(e);
			LOGGER.error("Real-World Interface {} stopped", spec.name(), e);
		}
		finally {
			running = false;
			join(capture);
			join(playback);
			keyPtt(false);
			closeAll();
			uplinkOpen = false;
			pttKeyed = false;
			closed.countDown();
		}
	}

	private static void join(@Nullable Thread thread) {
		if(thread == null)
			return;
		try {
			thread.join(1_000L);
		}
		catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Opens whatever is not open yet; each failure is shown and logged once. */
	private void openMissing() {
		if(spec.hasSerial() && port == null) {
			try {
				AudioBackend.Port opened = backend.openPort(spec.serialPort());
				if(spec.keysPtt())
					opened.set(spec.pttLine(), spec.pttInvert()); // Unkeyed at once: opening a port raises RTS and DTR on many systems.
				port = opened;
				serialState = DeviceState.OPEN;
			}
			catch(IOException | RuntimeException e) {
				failed("serial port", e, s -> serialState = s, serialState);
			}
		}
		if(output == null) {
			try {
				output = backend.openOutput(spec.outputDevice(), spec.sampleRate());
				outputState = DeviceState.OPEN;
			}
			catch(IOException | RuntimeException e) {
				failed("output", e, s -> outputState = s, outputState);
			}
		}
		if(input == null) {
			try {
				input = backend.openInput(spec.inputDevice(), spec.sampleRate());
				inputState = DeviceState.OPEN;
			}
			catch(IOException | RuntimeException e) {
				failed("input", e, s -> inputState = s, inputState);
			}
		}
		if(port != null && output != null && input != null)
			error = "";
	}

	private void failed(String what, Exception e, Consumer<DeviceState> set, DeviceState was) {
		DeviceState now = e instanceof AudioBackend.NotFoundException ? DeviceState.NOT_FOUND : DeviceState.FAILED;
		if(was != now)
			LOGGER.warn("Real-World Interface {}: {}: {}", spec.name(), what, e.getMessage());
		set.accept(now);
		error = what + ": " + e.getMessage();
	}

	private void closeAll() {
		AudioBackend.Port p = port;
		port = null;
		if(p != null)
			quietly(p::close);
		AudioBackend.Output out = output;
		output = null;
		if(out != null)
			quietly(out::close);
		AudioBackend.Input in = input;
		input = null;
		if(in != null)
			quietly(in::close);
	}

	private void quietly(Runnable action) {
		try {
			action.run();
		}
		catch(RuntimeException e) {
			LOGGER.warn("Real-World Interface {}: closing a device: {}", spec.name(), e.toString());
		}
	}

	// -------------------- CAPTURE THREAD: REAL RADIO TO GAME --------------------

	private void runCapture() {
		short[] block = new short[Math.max(1, spec.sampleRate() / 50)]; // 20 ms at the card's rate.
		Resampler resampler = new Resampler(spec.sampleRate(), RATE);
		FrameChunker chunker = new FrameChunker(FRAME);
		KeyingTimer gate = new KeyingTimer(spec.trigger() == InterfaceSpec.Trigger.VOX ? spec.voxHangMs() : COS_HANG_MS, spec.timeoutMs());
		while(running) {
			AudioBackend.Input in = input;
			if(in == null) {
				gate.update(false, now());
				uplinkOpen = false;
				if(!sleep(20L))
					return;
				continue;
			}
			int count;
			try {
				count = in.read(block, block.length);
			}
			catch(IOException | RuntimeException e) {
				failed("input", e, s -> inputState = s, inputState);
				input = null;
				quietly(in::close);
				continue;
			}
			if(count <= 0)
				continue;
			AudioLevels.gain(block, count, spec.inputGain());
			for(short[] frame : chunker.push(resampler.process(block, 0, count))) {
				double db = AudioLevels.rmsDb(frame);
				inputDb = AudioLevels.meter(inputDb, db, 1.5D);
				boolean signal = spec.trigger() == InterfaceSpec.Trigger.VOX ? db >= spec.voxThresholdDb() : cos();
				boolean open = gate.update(signal && !pttKeyed, now()); // Simplex: nothing comes in while we transmit.
				uplinkOpen = open;
				rxTimedOut = gate.isTimedOut();
				if(open)
					offer(inbound, frame);
			}
		}
	}

	/** True while the radio's squelch is open, from its COS line. */
	private boolean cos() {
		AudioBackend.Port p = port;
		return p != null && p.get(spec.cosLine()) != spec.cosInvert();
	}

	private boolean sleep(long ms) {
		try {
			Thread.sleep(ms);
			return true;
		}
		catch(InterruptedException e) {
			return false;
		}
	}

	// -------------------- PLAYBACK THREAD: GAME TO REAL RADIO --------------------

	private void runPlayback() {
		Resampler resampler = new Resampler(RATE, spec.sampleRate());
		KeyingTimer ptt = new KeyingTimer(spec.pttHangMs(), spec.timeoutMs());
		short[] silence = new short[FRAME];
		short[] delay = new short[(int)((long)spec.sampleRate() * spec.pttDelayMs() / 1000L)];
		boolean wasKeyed = false;
		while(running) {
			short[] frame;
			try {
				frame = downlink.poll(20L, TimeUnit.MILLISECONDS);
			}
			catch(InterruptedException e) {
				break;
			}
			boolean keyed = ptt.update(frame != null, now());
			txTimedOut = ptt.isTimedOut();
			if(keyed != wasKeyed) {
				wasKeyed = keyed;
				pttKeyed = keyed;
				keyPtt(keyed);
				if(keyed && delay.length > 0)
					play(delay, delay.length); // The radio's TX delay: let it come up to power before the voice.
			}
			if(!keyed) {
				outputDb = AudioLevels.meter(outputDb, AudioLevels.FLOOR_DB, 1.5D);
				continue;
			}
			short[] pcm = frame == null ? silence : frame.clone(); // The hang time: keep the radio fed with silence.
			AudioLevels.gain(pcm, pcm.length, spec.outputGain());
			outputDb = AudioLevels.meter(outputDb, AudioLevels.rmsDb(pcm), 1.5D);
			short[] out = resampler.process(pcm);
			play(out, out.length);
		}
	}

	private void play(short[] pcm, int count) {
		AudioBackend.Output out = output;
		if(out == null) {
			sleep(Math.max(1L, count * 1000L / Math.max(1, spec.sampleRate()))); // Keep time for the PTT timers.
			return;
		}
		try {
			out.write(pcm, count);
		}
		catch(IOException | RuntimeException e) {
			failed("output", e, s -> outputState = s, outputState);
			output = null;
			quietly(out::close);
		}
	}

	/** Keys or unkeys the real radio, if a serial line keys it. */
	private void keyPtt(boolean keyed) {
		AudioBackend.Port p = port;
		if(p != null && spec.keysPtt()) {
			try {
				p.set(spec.pttLine(), keyed != spec.pttInvert());
			}
			catch(RuntimeException e) {
				LOGGER.warn("Real-World Interface {}: PTT line: {}", spec.name(), e.toString());
			}
		}
	}
}
