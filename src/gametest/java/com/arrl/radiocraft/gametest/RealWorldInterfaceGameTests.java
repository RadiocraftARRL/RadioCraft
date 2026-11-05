package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RealWorldInterfaceBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.realworld.AudioBackend;
import com.arrl.radiocraft.common.realworld.AudioLevels;
import com.arrl.radiocraft.common.realworld.Bridge;
import com.arrl.radiocraft.common.realworld.Bridges;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The Real-World Interface (1.12 step 11) with a stand-in sound card and serial port ({@link FakeBackend}): in-game voice
 * goes out to the "sound card" with PTT keyed and unkeyed after the hang time; the "real radio's" audio, past the VOX
 * or with COS up, goes on the air in game; the time-out timer drops PTT; one block per interface; Everywhere mode
 * ignores distance; the devices close when the block goes. The bridges run on their own threads in real time, so the
 * tests wait on conditions rather than ticks. Each test has its own interface (claims are server-wide) and frequency.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class RealWorldInterfaceGameTests {

	private static final String BATCH = "real_world_interface";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-rwi-operator".getBytes());
	private static final FakeBackend FAKE = new FakeBackend();
	private static boolean savedEnabled;
	private static List<? extends String> savedInterfaces = List.of();

	private static final BlockPos RWI = new BlockPos(3, 2, 3);
	private static final BlockPos STATION = new BlockPos(6, 2, 3); // Three blocks off: in bare connector range.

	@BeforeBatch(batch = BATCH)
	public static void fakeDevices(ServerLevel level) {
		savedEnabled = RadiocraftServerConfig.REAL_WORLD_INTERFACE_ENABLED.get();
		savedInterfaces = RadiocraftServerConfig.REAL_WORLD_INTERFACES.get();
		Bridges.setBackendForTesting(FAKE);
		RadiocraftServerConfig.REAL_WORLD_INTERFACE_ENABLED.set(true);
		RadiocraftServerConfig.REAL_WORLD_INTERFACES.set(List.of(
				"name=GT Vox;input=fakeA;output=fakeA;serial=PORTA;ptt=RTS;vox_threshold=-30;vox_hang=200;ptt_hang=200;ptt_delay=0",
				"name=GT Cos;input=fakeB;output=fakeB;rate=8000;serial=PORTB;ptt=DTR;ptt_invert=true;trigger=COS;cos=CTS;vox_hang=100;ptt_hang=300;ptt_delay=20;timeout=2",
				"name=GT Shared;input=fakeC;output=fakeC",
				"name=GT Everywhere;input=fakeD;output=fakeD;serial=PORTD;vox_threshold=-30;vox_hang=200;ptt_hang=200",
				"name=GT Gone;input=fakeE;output=fakeE;serial=PORTE",
				"name=GT Claim;input=fakeF;output=fakeF"));
	}

	@AfterBatch(batch = BATCH)
	public static void realDevices(ServerLevel level) {
		RadiocraftServerConfig.REAL_WORLD_INTERFACE_ENABLED.set(savedEnabled);
		RadiocraftServerConfig.REAL_WORLD_INTERFACES.set(List.copyOf(savedInterfaces));
		Bridges.setBackendForTesting(null);
	}

	// -------------------- HELPERS --------------------

	private static RealWorldInterfaceBlockEntity rwi(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, RealWorldInterfaceBlockEntity.class);
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	/** The interface at {@code pos} on {@code name} and {@code khz}, switched on. */
	private static void setUp(GameTestHelper helper, BlockPos pos, String name, int khz) {
		RealWorldInterfaceBlockEntity block = rwi(helper, pos);
		helper.assertTrue(block.select(name, null) == null, "could not pick " + name);
		helper.assertTrue(block.setTunedFrequency(khz * 1000.0D, null), "could not tune " + khz);
		powerOn(helper, pos);
	}

	private static VHFRadioBlockEntity station(GameTestHelper helper, BlockPos pos, int khz) {
		VHFRadioBlockEntity radio = GameTestUtil.blockEntity(helper, pos, VHFRadioBlockEntity.class);
		radio.setFrequency(khz * 1000.0F);
		powerOn(helper, pos);
		return radio;
	}

	/** The link is up with its sound card open. */
	private static void linked(GameTestHelper helper, BlockPos pos) {
		Bridge bridge = rwi(helper, pos).getBridge();
		helper.assertTrue(bridge != null && bridge.status().input() == Bridge.DeviceState.OPEN && bridge.status().output() == Bridge.DeviceState.OPEN,
				"not linked: " + (bridge == null ? "no bridge" : bridge.status()));
	}

	private static short[] tone(double amplitude) {
		short[] audio = new short[Bridge.FRAME];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 800 * i / 48000.0D) * amplitude);
		return audio;
	}

	/** One frame said into the station's microphone. */
	private static void speak(GameTestHelper helper, VHFRadioBlockEntity radio) {
		radio.setPTTDown(true);
		radio.acceptVoicePacket(() -> helper.getLevel(), tone(8000), OPERATOR);
		radio.setPTTDown(false);
	}

	// -------------------- TESTS --------------------

	/**
	 * A station talks: the frames come out of the sound card with RTS keyed, and RTS drops after the hang time. Then the
	 * real radio's audio past the VOX goes on the air, and the station hears it.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 10_000, batch = BATCH) // Waits on the bridge threads (wall time).
	public static void bridgesBothWaysWithVox(GameTestHelper helper) {
		helper.setBlock(RWI, RadiocraftBlocks.REAL_WORLD_INTERFACE.get());
		helper.setBlock(STATION, RadiocraftBlocks.VHF_BASE_STATION.get());
		FakeDevice device = FAKE.device("fakeA");
		FakePort port = FAKE.port("PORTA");
		long[] mark = new long[1];
		helper.startSequence()
				.thenExecute(() -> {
					setUp(helper, RWI, "GT Vox", 147_420);
					station(helper, STATION, 147_420);
				})
				.thenWaitUntil(() -> linked(helper, RWI))
				.thenExecute(() -> helper.assertFalse(port.line(AudioBackend.Line.RTS), "RTS is up before anyone talked"))
				.thenExecute(() -> speak(helper, GameTestUtil.blockEntity(helper, STATION, VHFRadioBlockEntity.class)))
				.thenExecute(() -> speak(helper, GameTestUtil.blockEntity(helper, STATION, VHFRadioBlockEntity.class)))
				.thenExecute(() -> speak(helper, GameTestUtil.blockEntity(helper, STATION, VHFRadioBlockEntity.class)))
				.thenWaitUntil(() -> helper.assertTrue(port.ever(AudioBackend.Line.RTS, true), "RTS did not key; played " + device.played.size()))
				.thenWaitUntil(() -> helper.assertTrue(device.loudest() > -40.0D, "the station's voice did not reach the sound card: " + device.loudest()))
				.thenWaitUntil(() -> helper.assertFalse(port.line(AudioBackend.Line.RTS), "RTS stayed keyed after the hang time"))
				.thenExecute(() -> {
					mark[0] = helper.getLevel().getGameTime();
					for(int i = 0; i < 10; i++)
						device.mic.add(tone(8000)); // About -15 dBFS: past the -30 dBFS VOX.
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, STATION, VHFRadioBlockEntity.class).getLastSignalGameTime() > mark[0],
						"the station never heard the real radio"))
				.thenWaitUntil(() -> helper.assertFalse(rwi(helper, RWI).isTxActive(), "still transmitting after the VOX hang"))
				.thenSucceed();
	}

	/** Below the VOX threshold nothing goes on the air. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 4_000, batch = BATCH)
	public static void quietAudioStaysOffTheAir(GameTestHelper helper) {
		helper.setBlock(RWI, RadiocraftBlocks.REAL_WORLD_INTERFACE.get());
		helper.setBlock(STATION, RadiocraftBlocks.VHF_BASE_STATION.get());
		BlockPos rwi = RWI;
		helper.startSequence()
				.thenExecute(() -> {
					// Its own interface, with the default -35 dBFS VOX.
					setUp(helper, rwi, "GT Shared", 147_440);
					station(helper, STATION, 147_440);
				})
				.thenWaitUntil(() -> linked(helper, rwi))
				.thenExecute(() -> {
					for(int i = 0; i < 10; i++)
						FAKE.device("fakeC").mic.add(tone(200)); // About -44 dBFS.
				})
				.thenWaitUntil(() -> helper.assertTrue(FAKE.device("fakeC").mic.isEmpty(), "the bridge never read the quiet audio")) // Wall time.
				.thenIdle(20)
				.thenExecute(() -> {
					helper.assertTrue(GameTestUtil.blockEntity(helper, STATION, VHFRadioBlockEntity.class).getLastSignalGameTime() < 0,
							"quiet audio went on the air");
					helper.assertFalse(rwi(helper, rwi).getBridge().isUplinkOpen(), "the VOX opened on quiet audio");
				})
				.thenSucceed();
	}

	/**
	 * COS on CTS opens the way in whatever the audio level; DTR keys inverted (high when idle); the 2 s time-out drops
	 * PTT while the in-game station keeps talking, and the 8 kHz card gets 8 kHz audio.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20_000, batch = BATCH) // Waits on a 2 s wall-time time-out.
	public static void cosKeysInAndTimeoutDropsPtt(GameTestHelper helper) {
		helper.setBlock(RWI, RadiocraftBlocks.REAL_WORLD_INTERFACE.get());
		helper.setBlock(STATION, RadiocraftBlocks.VHF_BASE_STATION.get());
		FakeDevice device = FAKE.device("fakeB");
		FakePort port = FAKE.port("PORTB");
		long[] mark = new long[1];
		helper.startSequence()
				.thenExecute(() -> {
					setUp(helper, RWI, "GT Cos", 147_460);
					station(helper, STATION, 147_460);
				})
				.thenWaitUntil(() -> linked(helper, RWI))
				.thenWaitUntil(() -> helper.assertTrue(port.line(AudioBackend.Line.DTR), "inverted DTR is not high (unkeyed) when idle"))
				.thenExecute(() -> {
					mark[0] = helper.getLevel().getGameTime();
					port.set(AudioBackend.Line.CTS, true); // The real radio's squelch opens.
					for(int i = 0; i < 10; i++)
						device.mic.add(tone(100)); // Quiet: only COS opens the way.
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, STATION, VHFRadioBlockEntity.class).getLastSignalGameTime() > mark[0],
						"COS did not put the real radio on the air"))
				.thenExecute(() -> port.set(AudioBackend.Line.CTS, false))
				.thenWaitUntil(() -> helper.assertFalse(rwi(helper, RWI).getBridge().isUplinkOpen(), "COS dropped but the way in stayed open"))
				.thenExecute(() -> device.played.clear())
				// The station talks without a break: PTT keys, then the 2 s time-out drops it. The time-out is wall time and
				// the test server ticks as fast as it can, so the station talks every tick until it fires.
				.thenWaitUntil(() -> {
					speak(helper, GameTestUtil.blockEntity(helper, STATION, VHFRadioBlockEntity.class));
					helper.assertTrue(rwi(helper, RWI).getBridge().status().txTimedOut(), "the time-out timer never fired");
				})
				.thenWaitUntil(() -> helper.assertTrue(port.line(AudioBackend.Line.DTR), "PTT is still keyed past the time-out"))
				.thenExecute(() -> {
					helper.assertTrue(device.playedSamples() > 8000, "hardly anything was played: " + device.playedSamples());
					helper.assertTrue(device.playedSamples() < 3 * 8000, "more than 8 kHz audio for the time: " + device.playedSamples());
				})
				.thenSucceed();
	}

	/** A second block cannot take an interface another is using; the first letting go frees it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void oneBlockPerInterface(GameTestHelper helper) {
		BlockPos other = RWI.east(2);
		helper.setBlock(RWI, RadiocraftBlocks.REAL_WORLD_INTERFACE.get());
		helper.setBlock(other, RadiocraftBlocks.REAL_WORLD_INTERFACE.get());
		helper.startSequence()
				.thenExecute(() -> {
					helper.assertTrue(rwi(helper, RWI).select("GT Claim", null) == null, "the first could not pick it");
					helper.assertTrue(rwi(helper, other).select("GT Claim", null) != null, "the second took an interface in use");
					helper.assertTrue(rwi(helper, other).select("No Such Interface", null) != null, "picked an interface the config does not list");
					helper.assertTrue(rwi(helper, RWI).select("", null) == null, "could not let go");
					helper.assertTrue(rwi(helper, other).select("GT Claim", null) == null, "the second could not pick it once free");
					helper.assertTrue(rwi(helper, other).statusTag(null).getString("state").equals("off"), "state " + rwi(helper, other).statusTag(null).getString("state"));
					rwi(helper, other).select("", null);
				})
				.thenSucceed();
	}

	/** Breaking the block closes the sound card and serial port (and lets go of PTT). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 4_000, batch = BATCH)
	public static void devicesCloseWhenTheBlockGoes(GameTestHelper helper) {
		BlockPos pos = RWI.south(6);
		helper.setBlock(pos, RadiocraftBlocks.REAL_WORLD_INTERFACE.get());
		FakeDevice device = FAKE.device("fakeE");
		FakePort port = FAKE.port("PORTE");
		helper.startSequence()
				.thenExecute(() -> setUp(helper, pos, "GT Gone", 147_500))
				.thenWaitUntil(() -> linked(helper, pos))
				.thenExecute(() -> helper.assertTrue(device.inputOpen && device.outputOpen && port.open, "not all open"))
				.thenExecute(() -> helper.setBlock(pos, Blocks.AIR))
				.thenWaitUntil(() -> helper.assertFalse(device.inputOpen || device.outputOpen || port.open, "still open after the block went"))
				.thenExecute(() -> helper.assertTrue(Bridges.owner("GT Gone") == null, "the claim outlived the block"))
				.thenSucceed();
	}

	/**
	 * Everywhere mode: a station with a J-pole twelve blocks off (far past the bare connector) is heard at its source,
	 * and the real radio's audio reaches it, with no antenna on the interface.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 10_000, batch = BATCH) // Waits on the bridge threads (wall time).
	public static void everywhereIgnoresDistance(GameTestHelper helper) {
		BlockPos rwi = new BlockPos(1, 2, 1);
		BlockPos far = new BlockPos(13, 2, 10);
		helper.setBlock(rwi, RadiocraftBlocks.REAL_WORLD_INTERFACE.get());
		GameTestUtil.radioWithJPole(helper, far);
		FakeDevice device = FAKE.device("fakeD");
		FakePort port = FAKE.port("PORTD");
		long[] mark = new long[1];
		helper.startSequence()
				.thenExecute(() -> {
					setUp(helper, rwi, "GT Everywhere", 147_480);
					helper.assertTrue(rwi(helper, rwi).setEverywhere(true, null), "Everywhere refused");
					station(helper, far, 147_480);
				})
				.thenWaitUntil(() -> linked(helper, rwi))
				.thenWaitUntil(() -> helper.assertTrue(rwi(helper, rwi).isLive(), "not live"))
				.thenIdle(2) // The Everywhere air registers on the next tick.
				.thenExecute(() -> speak(helper, GameTestUtil.blockEntity(helper, far, VHFRadioBlockEntity.class)))
				.thenExecute(() -> speak(helper, GameTestUtil.blockEntity(helper, far, VHFRadioBlockEntity.class)))
				.thenWaitUntil(() -> helper.assertTrue(port.ever(AudioBackend.Line.RTS, true) && device.loudest() > -40.0D, "the far station was not heard"))
				.thenWaitUntil(() -> helper.assertFalse(port.line(AudioBackend.Line.RTS), "RTS stayed keyed"))
				.thenExecute(() -> {
					mark[0] = helper.getLevel().getGameTime();
					for(int i = 0; i < 10; i++)
						device.mic.add(tone(8000));
				})
				.thenWaitUntil(() -> helper.assertTrue(GameTestUtil.blockEntity(helper, far, VHFRadioBlockEntity.class).getLastSignalGameTime() > mark[0],
						"the far station never heard the real radio"))
				.thenSucceed();
	}

	// -------------------- THE STAND-IN DEVICES --------------------

	/** One stand-in sound card: what the "real radio" sends ({@link #mic}) and what it was played. */
	static final class FakeDevice {
		final BlockingQueue<short[]> mic = new LinkedBlockingQueue<>();
		final List<short[]> played = Collections.synchronizedList(new ArrayList<>());
		volatile boolean inputOpen = false;
		volatile boolean outputOpen = false;

		double loudest() {
			synchronized(played) {
				return played.stream().mapToDouble(AudioLevels::rmsDb).max().orElse(AudioLevels.FLOOR_DB);
			}
		}

		int playedSamples() {
			synchronized(played) {
				return played.stream().mapToInt(frame -> frame.length).sum();
			}
		}
	}

	/** One stand-in serial port: its lines, as the bridge set them or the test does (CTS/DSR). */
	static final class FakePort {
		final Map<AudioBackend.Line, Boolean> lines = new ConcurrentHashMap<>();
		volatile boolean open = false;

		private final Map<AudioBackend.Line, Boolean> everHigh = new ConcurrentHashMap<>();
		private final Map<AudioBackend.Line, Boolean> everLow = new ConcurrentHashMap<>();

		boolean line(AudioBackend.Line line) {
			return lines.getOrDefault(line, false);
		}

		/** True if the line was ever set to {@code value} (a short PTT can come and go between two ticks). */
		boolean ever(AudioBackend.Line line, boolean value) {
			return (value ? everHigh : everLow).containsKey(line);
		}

		void set(AudioBackend.Line line, boolean on) {
			lines.put(line, on);
			(on ? everHigh : everLow).put(line, true);
		}
	}

	/** Sound cards and serial ports by name, made on first use; reads and writes keep real time. */
	static final class FakeBackend implements AudioBackend {
		private final Map<String, FakeDevice> devices = new ConcurrentHashMap<>();
		private final Map<String, FakePort> ports = new ConcurrentHashMap<>();

		FakeDevice device(String name) {
			return devices.computeIfAbsent(name, k -> new FakeDevice());
		}

		FakePort port(String name) {
			return ports.computeIfAbsent(name, k -> new FakePort());
		}

		@Override
		public Input openInput(String name, int rate) throws IOException {
			if(name.startsWith("missing"))
				throw new NotFoundException("no " + name);
			FakeDevice device = device(name);
			device.inputOpen = true;
			return new Input() {
				@Override
				public int read(short[] into, int max) throws IOException {
					short[] frame;
					try {
						frame = device.mic.poll(max * 1000L / rate, TimeUnit.MILLISECONDS);
					}
					catch(InterruptedException e) {
						throw new IOException("interrupted", e);
					}
					int count = Math.min(max, into.length);
					for(int i = 0; i < count; i++)
						into[i] = frame == null ? 0 : frame[i * frame.length / count]; // Any rate: the 48 kHz frame stretched over the block.
					return count;
				}

				@Override
				public void close() {
					device.inputOpen = false;
				}
			};
		}

		@Override
		public Output openOutput(String name, int rate) throws IOException {
			if(name.startsWith("missing"))
				throw new NotFoundException("no " + name);
			FakeDevice device = device(name);
			device.outputOpen = true;
			return new Output() {
				@Override
				public void write(short[] pcm, int count) throws IOException {
					short[] copy = new short[count];
					System.arraycopy(pcm, 0, copy, 0, count);
					device.played.add(copy);
					try {
						Thread.sleep(count * 1000L / rate); // A sound card plays in real time.
					}
					catch(InterruptedException e) {
						throw new IOException("interrupted", e);
					}
				}

				@Override
				public void close() {
					device.outputOpen = false;
				}
			};
		}

		@Override
		public Port openPort(String name) {
			FakePort port = port(name);
			port.open = true;
			return new Port() {
				@Override
				public void set(Line line, boolean on) {
					port.set(line, on);
				}

				@Override
				public boolean get(Line line) {
					return port.line(line);
				}

				@Override
				public void close() {
					port.open = false;
				}
			};
		}

		@Override
		public List<String> inputs() {
			return List.copyOf(devices.keySet());
		}

		@Override
		public List<String> outputs() {
			return List.copyOf(devices.keySet());
		}

		@Override
		public List<String> ports() {
			return List.copyOf(ports.keySet());
		}
	}
}
