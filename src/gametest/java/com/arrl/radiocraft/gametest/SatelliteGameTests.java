package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.RadioInterfaceBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.PacketBbs;
import com.arrl.radiocraft.common.computer.apps.BbsHandler;
import com.arrl.radiocraft.common.computer.apps.FccHandler;
import com.arrl.radiocraft.common.photo.PhotoImage;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.arrl.radiocraft.api.antenna.IAntenna;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import com.arrl.radiocraft.common.radio.antenna.AntennaCWPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaDataPacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.antenna.data.AntennaData;
import com.arrl.radiocraft.common.radio.antenna.data.RubberDuckyAntennaData;
import com.arrl.radiocraft.common.radio.antenna.networks.AntennaNetworkManager;
import com.arrl.radiocraft.common.radio.antenna.types.RubberDuckyAntennaType;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.distress.DistressAlert;
import com.arrl.radiocraft.common.radio.distress.DistressBeacons;
import com.arrl.radiocraft.common.radio.distress.DistressPackets;
import com.arrl.radiocraft.common.radio.morse.CWBuffer;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatelliteDelay;
import com.arrl.radiocraft.common.radio.satellite.SatelliteRadio;
import com.arrl.radiocraft.common.radio.satellite.SatelliteMode;
import com.arrl.radiocraft.common.radio.satellite.SatelliteRules;
import com.arrl.radiocraft.common.radio.satellite.SatelliteTelemetry;
import com.arrl.radiocraft.common.radio.satellite.Satellites;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Satellites (2026-11-11, {@code doc/SatellitePlan.md}), each pinned over its test plot. An FM repeater: a station on
 * its uplink with its tone is heard on its downlink by a station in the plot and by a listening antenna 4,000 blocks
 * away; without the tone, or under a roof, nothing comes down. Distress relay: a beacon's alert comes down once to the
 * far antenna, and a copy of it sent again is not relayed again.
 * <p>
 * Their own batch: a satellite covers 10,000 blocks, so the plots of other batches stay out of reach.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class SatelliteGameTests {

	private static final String BATCH = "satellites";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-satellite-operator".getBytes());
	private static final BlockPos A = new BlockPos(3, 2, 3), B = new BlockPos(10, 2, 3);
	/** How far the listening antenna is from the plot: well past line of sight, well inside the coverage. */
	private static final int FAR = 4000;

	@AfterBatch(batch = BATCH)
	public static void bringThemDown(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Satellites satellites = Satellites.of(server);
		for(Satellite s : new ArrayList<>(satellites.all()))
			satellites.remove(server, s.name);
		SatelliteRadio.forgetSky();
	}

	/** A new amateur satellite hanging over the plot for the rest of the test. */
	private static Satellite overhead(GameTestHelper helper, long seed) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = Satellites.of(server).launch(server, true, OPERATOR, "gametest", new Random(seed));
		helper.assertTrue(satellite != null, "no frequency pair left");
		BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
		Satellites.of(server).pin(server, satellite, centre.getX(), centre.getZ(), 2400L);
		return satellite;
	}

	/** An antenna on the VHF network {@code FAR} blocks east of the plot that counts what comes down from satellites. */
	static final class Probe implements IAntenna { // Also SatelliteGeoGameTests'.
		final AntennaPos pos;
		final List<AntennaVoicePacket> voice = new ArrayList<>();
		final List<AntennaDataPacket> data = new ArrayList<>();

		Probe(GameTestHelper helper, int east) {
			pos = new AntennaPos(helper.absolutePos(new BlockPos(8, 2, 8)).east(east), helper.getLevel());
			AntennaNetworkManager.getNetwork(AntennaNetworkManager.VHF_ID).addAntenna(this);
		}

		void remove() {
			AntennaNetworkManager.getNetwork(AntennaNetworkManager.VHF_ID).removeAntenna(this);
		}

		/** Frames {@code satellite} sent on its downlink (not its telemetry beacon). */
		synchronized long voiceFrom(Satellite satellite) {
			return voice.stream().filter(p -> p.getSource() instanceof SatelliteRadio.SatelliteAntenna s && s.satellite() == satellite
					&& p.getFrequency() == satellite.downlinkHz).count();
		}

		/** Packets of {@code kind} from any satellite. */
		synchronized List<AntennaDataPacket> data(TncPacket.Kind kind) {
			return data.stream().filter(p -> p.getPayload().kind() == kind).toList();
		}

		/** Packets of {@code kind} from {@code satellite}. */
		synchronized List<AntennaDataPacket> dataFrom(Satellite satellite, TncPacket.Kind kind) {
			return data.stream().filter(p -> p.getSource() instanceof SatelliteRadio.SatelliteAntenna s && s.satellite() == satellite
					&& p.getPayload().kind() == kind).toList();
		}

		@Override
		public synchronized void receiveAudioPacket(AntennaVoicePacket packet) {
			if(SatelliteRadio.isSatellite(packet.getSource()))
				voice.add(packet);
		}

		@Override
		public synchronized void receiveDataPacket(AntennaDataPacket packet) {
			if(SatelliteRadio.isSatellite(packet.getSource()))
				data.add(packet);
		}

		@Override
		public void transmitAudioPacket(de.maxhenkel.voicechat.api.ServerLevel level, short[] rawAudio, Band band, float frequencyHertz, UUID sourcePlayer) {}

		@Override
		public void transmitCWPacket(ServerLevel level, Collection<CWBuffer> buffers, Band band, float frequencyHertz) {}

		@Override
		public void receiveCWPacket(AntennaCWPacket packet) {}

		@Override
		public AntennaPos getAntennaPos() {
			return pos;
		}

		@Override
		public IAntennaType<? extends AntennaData> getType() {
			return new RubberDuckyAntennaType();
		}

		@Override
		public AntennaData getData() {
			return new RubberDuckyAntennaData(0.0D);
		}

		@Override
		public Player getPlayer() {
			return null;
		}
	}

	private static VHFRadioBlockEntity station(GameTestHelper helper, BlockPos pos) {
		return GameTestUtil.blockEntity(helper, pos, VHFRadioBlockEntity.class);
	}

	private static void powerOn(GameTestHelper helper, BlockPos pos) {
		RadioNetworkObject networkObject = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class);
		if(!networkObject.isPowered)
			GameTestUtil.blockEntity(helper, pos, RadioBlockEntity.class).toggle();
		helper.assertTrue(networkObject.isPowered, "Radio at " + pos.toShortString() + " did not power on");
	}

	private static void antennaUp(GameTestHelper helper, BlockPos pos) {
		int found = GameTestUtil.networkObject(helper, pos, RadioNetworkObject.class).getAntennas().size();
		helper.assertTrue(found == 1, "Radio at " + pos.toShortString() + " sees " + found + " antennas");
	}

	static short[] toneFrame() {
		short[] audio = new short[960];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return audio;
	}

	private static void speak(GameTestHelper helper, BlockPos pos) {
		VHFRadioBlockEntity radio = station(helper, pos);
		radio.setPTTDown(true);
		helper.assertTrue(radio.canTransmitVoice(), "Station at " + pos.toShortString() + " cannot transmit");
		radio.acceptVoicePacket(() -> helper.getLevel(), toneFrame(), OPERATOR);
		radio.setPTTDown(false);
	}

	private static boolean heardNow(GameTestHelper helper, BlockPos pos) {
		return station(helper, pos).getLastSignalGameTime() == helper.getLevel().getGameTime();
	}

	/**
	 * Two stations with eggbeaters (a J-pole has a null straight up, where the test pins the satellite): A on the satellite's downlink with the minus offset (so it transmits on the uplink),
	 * sending {@code tone}; B listening on the downlink.
	 */
	private static void stations(GameTestHelper helper, Satellite satellite, float tone) {
		VHFRadioBlockEntity a = station(helper, A), b = station(helper, B);
		a.setFrequency(satellite.downlinkHz);
		a.setOffsetHz(-Ctcss.REPEATER_OFFSET_HZ);
		a.setCtcssTone(tone);
		b.setFrequency(satellite.downlinkHz);
		powerOn(helper, A);
		powerOn(helper, B);
		helper.assertTrue(a.getTransmitFrequency() == satellite.uplinkHz, "A transmits on " + a.getTransmitFrequency() + ", not the uplink " + satellite.uplinkHz);
	}

	/**
	 * With the tone, the satellite repeats A's voice to B (on another frequency, so only by the satellite) and far away,
	 * after its transit delay (2026-11-12: not at once, then {@link SatelliteDelay#VOICE_MS} later).
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void repeatsVoiceWithItsTone(GameTestHelper helper) {
		GameTestUtil.radioWithEggbeater(helper, A);
		GameTestUtil.radioWithEggbeater(helper, B);
		Satellite satellite = overhead(helper, 101L);
		Probe far = new Probe(helper, FAR);
		long[] spoke = { 0L, 0L };
		helper.startSequence()
				.thenWaitUntil(() -> {
					antennaUp(helper, A);
					antennaUp(helper, B);
				})
				.thenExecute(() -> stations(helper, satellite, satellite.tone()))
				.thenIdle(1)
				.thenExecute(() -> {
					speak(helper, A);
					spoke[0] = helper.getLevel().getGameTime();
					spoke[1] = System.currentTimeMillis();
					helper.assertTrue(far.voiceFrom(satellite) == 0, "came down at once: no transit delay");
					helper.assertTrue(SatelliteRadio.holding(satellite) == 1, "the satellite is not holding the frame: " + SatelliteRadio.holding(satellite));
				})
				.thenWaitUntil(() -> helper.assertTrue(far.voiceFrom(satellite) == 1, "the antenna " + FAR + " blocks away got " + far.voiceFrom(satellite) + " frames, not 1"))
				.thenExecute(() -> {
					long waited = System.currentTimeMillis() - spoke[1];
					helper.assertTrue(waited >= SatelliteDelay.voiceMillis(false) - 5, "came down after " + waited + " ms, before the delay");
					helper.assertTrue(station(helper, B).getLastSignalGameTime() > spoke[0], "B on the downlink did not hear A through the satellite");
					AntennaVoicePacket first = far.voice.stream().filter(p -> p.getFrequency() == satellite.downlinkHz).findFirst().orElseThrow();
					helper.assertTrue(first.getCtcssTone() == satellite.tone(), "it came down on the downlink with the tone");
					// Turned off, it repeats nothing.
					Satellites.of(helper.getLevel().getServer()).setOn(helper.getLevel().getServer(), satellite, false);
					speak(helper, A);
				})
				.thenIdle(SatelliteDelay.ticks(SatelliteDelay.Kind.VOICE, false) + 10)
				.thenExecute(() -> {
					helper.assertTrue(far.voiceFrom(satellite) == 1, "repeated while off");
					far.remove();
				})
				.thenSucceed();
	}

	/**
	 * A vertical has a null straight up (2026-11-12): with the satellite pinned right over a J-pole, the satellite cannot
	 * hear its uplink, and what comes down to a J-pole is weak; an eggbeater hears it strongly.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void verticalsFadeOverhead(GameTestHelper helper) {
		GameTestUtil.radioWithJPole(helper, A);
		GameTestUtil.radioWithEggbeater(helper, B);
		Satellite satellite = overhead(helper, 303L);
		Probe far = new Probe(helper, FAR + 100);
		helper.startSequence()
				.thenWaitUntil(() -> {
					antennaUp(helper, A);
					antennaUp(helper, B);
				})
				.thenExecute(() -> stations(helper, satellite, satellite.tone()))
				.thenIdle(1)
				.thenExecute(() -> {
					speak(helper, A);
					helper.assertFalse(heardNow(helper, B), "the satellite heard a J-pole from straight overhead");
					helper.assertTrue(far.voiceFrom(satellite) == 0 && SatelliteRadio.holding(satellite) == 0, "repeated a J-pole's uplink from overhead");
					far.remove();
					double up = satellite.look(helper.absolutePos(A).getX(), helper.absolutePos(A).getZ(), helper.getLevel().getGameTime()).elevation();
					helper.assertTrue(up > 85.0D, "the satellite is only " + up + " degrees up");
					helper.assertTrue(SatelliteRadio.pattern(GameTestUtil.networkObject(helper, A, RadioNetworkObject.class).getAntennas().get(0).getAntenna())
							== com.arrl.radiocraft.common.radio.satellite.SatellitePattern.VERTICAL, "a J-pole is not a vertical");
					helper.assertTrue(SatelliteRadio.pattern(GameTestUtil.networkObject(helper, B, RadioNetworkObject.class).getAntennas().get(0).getAntenna())
							== com.arrl.radiocraft.common.radio.satellite.SatellitePattern.EGGBEATER, "the eggbeater is not one");
					helper.assertTrue(SatelliteRadio.strength(up, null) > SatelliteRadio.strength(up, GameTestUtil.networkObject(helper, A, RadioNetworkObject.class).getAntennas().get(0).getAntenna()),
							"a J-pole hears it as well as the plain curve overhead");
				})
				.thenSucceed();
	}

	/** Without the tone, or with the transmitter's antenna under a stone roof, nothing comes down. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void ignoresNoToneAndClosedSky(GameTestHelper helper) {
		GameTestUtil.radioWithEggbeater(helper, A);
		GameTestUtil.radioWithEggbeater(helper, B);
		Satellite satellite = overhead(helper, 202L);
		Probe far = new Probe(helper, FAR + 50);
		helper.startSequence()
				.thenWaitUntil(() -> {
					antennaUp(helper, A);
					antennaUp(helper, B);
				})
				.thenExecute(() -> stations(helper, satellite, Ctcss.NONE))
				.thenIdle(1)
				.thenExecute(() -> {
					speak(helper, A);
					helper.assertFalse(heardNow(helper, B), "B heard A without the tone");
					helper.assertTrue(far.voiceFrom(satellite) == 0 && SatelliteRadio.holding(satellite) == 0, "repeated without the tone");
					// The right tone, but a stone roof over A's J-pole (two blocks south of it, two tall).
					station(helper, A).setCtcssTone(satellite.tone());
					for(int dx = -1; dx <= 1; dx++)
						for(int dz = -1; dz <= 1; dz++)
							helper.setBlock(A.south(2).offset(dx, 3, dz), Blocks.STONE);
				})
				.thenWaitUntil(() -> helper.assertFalse(helper.getLevel().canSeeSky(helper.absolutePos(A.south(2))), "the roof has not darkened the pole yet"))
				.thenExecute(() -> {
					SatelliteRadio.forgetSky();
					speak(helper, A);
					helper.assertTrue(far.voiceFrom(satellite) == 0 && SatelliteRadio.holding(satellite) == 0, "repeated from under a roof");
					far.remove();
				})
				.thenSucceed();
	}

	/**
	 * A distress beacon's alert under open sky comes down to the far antenna once, on 161.975 with its alert whole; a
	 * copy of it sent again (as a digipeater would) is not relayed again; the next burst is.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void relaysADistressAlertOnce(GameTestHelper helper) {
		overhead(helper, 303L); // Pinned over the plot: any satellite up may relay.
		Probe far = new Probe(helper, FAR + 100);
		Probe nearby = new Probe(helper, 3);
		helper.startSequence()
				.thenExecute(() -> {
					BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
					DistressAlert alert = new DistressAlert(4242L, DistressAlert.Type.BEACON, DistressAlert.State.ACTIVE, "Castaway", null,
							at.getX(), at.getY(), at.getZ(), helper.getLevel().getGameTime(), "");
					DistressBeacons.alert(helper.getLevel(), at, null, alert);
					helper.assertTrue(far.data(TncPacket.Kind.DISTRESS).size() == 1, "the far antenna got " + far.data(TncPacket.Kind.DISTRESS).size() + " relayed bursts, not 1");
					AntennaDataPacket relayed = far.data(TncPacket.Kind.DISTRESS).getFirst();
					helper.assertTrue(DistressPackets.isBeacon(relayed.getFrequency()), "relayed on " + relayed.getFrequency());
					DistressAlert copy = DistressPackets.alert(relayed.getPayload());
					helper.assertTrue(copy != null && copy.id() == 4242L && copy.owner().equals("Castaway"), "the alert came down whole: " + relayed.getPayload().text());
					helper.assertTrue(relayed.getPayload().hops() == 1, "a relayed copy is marked one hop");
					// The copy sent on again from the nearby antenna: not relayed a second time.
					TncPacket again = relayed.getPayload();
					SatelliteRadio.onData(nearby, helper.getLevel(), again, DistressPackets.BEACON_HZ);
					helper.assertTrue(far.data(TncPacket.Kind.DISTRESS).size() == 1, "a relayed copy was relayed again");
					// A new burst of the same alert is.
					DistressBeacons.alert(helper.getLevel(), at, null, alert);
					helper.assertTrue(far.data(TncPacket.Kind.DISTRESS).size() == 2, "the next burst was not relayed");
					far.remove();
					nearby.remove();
				})
				.thenSucceed();
	}

	// -------------------- SATTRACK --------------------

	static CompoundTag sattrack(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.SATTRACK.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag setTone(String name, float tone) {
		CompoundTag data = new CompoundTag();
		data.putString("name", name);
		data.putString("field", "tone");
		data.putFloat("tone", tone);
		return data;
	}

	/**
	 * SATTRACK lists the satellite with its passes; its owner changes its tone while it passes over the computer, a
	 * stranger cannot, and nobody can once it is away below the horizon.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = BATCH)
	public static void sattrackListsAndOnlyTheOwnerChangesItOverhead(GameTestHelper helper) {
		Satellite satellite = overhead(helper, 404L);
		BlockPos at = new BlockPos(4, 2, 4);
		helper.setBlock(at, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(at.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, at, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.SATTRACK);
		BlockPos stand = helper.absolutePos(at.south(2));
		FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(OPERATOR, "gametest"));
		owner.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
		FakePlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("sat-stranger".getBytes()), "Stranger"));
		stranger.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
		helper.startSequence()
				.thenExecute(() -> {
					CompoundTag status = sattrack(computer, owner, "status", new CompoundTag()).getCompound("data");
					CompoundTag listed = null;
					for(Tag t : status.getList("satellites", Tag.TAG_COMPOUND))
						if(((CompoundTag)t).getString("name").equals(satellite.name))
							listed = (CompoundTag)t;
					helper.assertTrue(listed != null, "not listed: " + status);
					helper.assertTrue(listed.getBoolean("passing") && listed.getBoolean("mine") && listed.getLong("days") == Satellites.decayDays(),
							"listed as passing, mine, with its days: " + listed);
					ListTag passes = listed.getList("passes", Tag.TAG_COMPOUND);
					helper.assertTrue(!passes.isEmpty() && passes.getCompound(0).getLong("in") == 0L, "the pass now is first: " + passes);

					float tone = Ctcss.step(satellite.tone(), satellite.tone() >= 250.0F ? -1 : 1);
					CompoundTag refused = sattrack(computer, stranger, "set", setTone(satellite.name, tone));
					helper.assertTrue("sat_not_yours".equals(refused.getString("error")), "a stranger changed it: " + refused);
					CompoundTag done = sattrack(computer, owner, "set", setTone(satellite.name, tone));
					helper.assertFalse(done.contains("error"), "the owner could not: " + done);
					helper.assertTrue(satellite.tone() == tone, "the tone is " + satellite.tone());

					MinecraftServer server = helper.getLevel().getServer();
					Satellites.of(server).pin(server, satellite, stand.getX() + 50_000.0D, stand.getZ(), 2400L); // Far away.
					CompoundTag away = sattrack(computer, owner, "set", setTone(satellite.name, satellite.tone() == 100.0F ? 67.0F : 100.0F));
					helper.assertTrue("sat_not_passing".equals(away.getString("error")), "changed below the horizon: " + away);
				})
				.thenSucceed();
	}
	// -------------------- MODES, TELEMETRY, ECLIPSES (2026-11-12) --------------------

	/** Their own batch too, so the first batch's satellites are down first. */
	private static final String MODES = "satellite_modes";

	@AfterBatch(batch = MODES)
	public static void bringModesDown(ServerLevel level) {
		bringThemDown(level);
	}

	private static final BlockPos INTERFACE = A.north(), COMPUTER = A.north().west();

	/** A's radio (J-pole, on {@code hz}) with a radio interface north of it ({@code callsign}, digipeating off). */
	private static RadioInterfaceBlockEntity dataStation(GameTestHelper helper, float hz, String callsign) {
		GameTestUtil.radioWithEggbeater(helper, A);
		helper.setBlock(INTERFACE, RadiocraftBlocks.RADIO_INTERFACE.get());
		station(helper, A).setFrequency(hz);
		RadioInterfaceBlockEntity radioInterface = GameTestUtil.blockEntity(helper, INTERFACE, RadioInterfaceBlockEntity.class);
		radioInterface.setCallsign(callsign, "gametest");
		radioInterface.setDigipeatEnabled(false);
		return radioInterface;
	}

	/** A radio computer west of the interface with {@code app} installed. */
	private static RadioComputerBlockEntity computer(GameTestHelper helper, ComputerApp app) {
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(COMPUTER.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
		computer.install(app);
		return computer;
	}

	private static FakePlayer owner(GameTestHelper helper) {
		BlockPos stand = helper.absolutePos(COMPUTER.south(3));
		FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(OPERATOR, "gametest"));
		owner.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
		return owner;
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, ComputerApp app, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", app.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	/** SATTRACK's entry for {@code satellite}, or null. */
	private static CompoundTag listed(RadioComputerBlockEntity computer, FakePlayer player, Satellite satellite) {
		CompoundTag status = sattrack(computer, player, "status", new CompoundTag()).getCompound("data");
		for(Tag t : status.getList("satellites", Tag.TAG_COMPOUND))
			if(((CompoundTag)t).getString("name").equals(satellite.name))
				return (CompoundTag)t;
		return null;
	}

	private static CompoundTag field(Satellite satellite, String field) {
		CompoundTag data = new CompoundTag();
		data.putString("name", satellite.name);
		data.putString("field", field);
		return data;
	}

	private static CompoundTag mode(Satellite satellite, SatelliteMode mode) {
		CompoundTag data = field(satellite, "mode");
		data.putString("mode", mode.name());
		return data;
	}

	/** Lines {@code computer}'s BBS terminal heard (not its own). */
	private static boolean termHeard(RadioComputerBlockEntity computer, String part) {
		return BbsHandler.state(computer).termLog().stream().anyMatch(line -> !line.startsWith("> ") && line.contains(part));
	}

	/**
	 * APRS mode: a beacon sent on the satellite's APRS frequency (its downlink) from the plot comes down once, one hop
	 * on, 4,000 blocks away; one on terrestrial APRS (not its frequency) does not.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = MODES)
	public static void aprsDigipeatsAcrossTheFootprint(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = overhead(helper, 505L);
		helper.assertTrue(Satellites.of(server).setMode(server, satellite, SatelliteMode.APRS), "could not set APRS");
		RadioInterfaceBlockEntity station = dataStation(helper, satellite.aprsHz(), "W1APR");
		Probe far = new Probe(helper, FAR + 200);
		BlockPos at = helper.absolutePos(A);
		helper.startSequence()
				.thenWaitUntil(() -> antennaUp(helper, A))
				.thenExecute(() -> {
					powerOn(helper, A);
					helper.assertTrue(station.transmit(TncPacket.beacon(4711L, "W1APR", at, "VIA SAT", "HOUSE")), "the station did not send");
				})
				.thenWaitUntil(() -> helper.assertTrue(far.dataFrom(satellite, TncPacket.Kind.BEACON).size() == 1, "not digipeated 4,000 blocks away"))
				.thenExecute(() -> {
					AntennaDataPacket copy = far.dataFrom(satellite, TncPacket.Kind.BEACON).getFirst();
					helper.assertTrue(copy.getPayload().from().equals("W1APR") && copy.getPayload().hops() == 1 && at.equals(copy.getPayload().position()),
							"the beacon came down whole, one hop on: " + copy.getPayload());
					helper.assertTrue(copy.getFrequency() == satellite.aprsHz(), "on its APRS frequency: " + copy.getFrequency());
					station(helper, A).setFrequency(SatelliteRules.TERRESTRIAL_APRS_HZ);
					station.transmit(TncPacket.beacon(4712L, "W1APR", at, "NOT VIA SAT", "HOUSE"));
				})
				.thenIdle(80)
				.thenExecute(() -> {
					helper.assertTrue(far.dataFrom(satellite, TncPacket.Kind.BEACON).size() == 1, "digipeated off its frequency, or twice");
					far.remove();
				})
				.thenSucceed();
	}

	/**
	 * MAILBOX mode through the Packet BBS program's terminal page: W1AAA leaves a message for W1BBB on one pass; the
	 * satellite answers nothing below the horizon; on its next pass W1BBB (the station's new callsign) lists and reads it.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 1400, batch = MODES)
	public static void mailboxUploadThenDownloadOnALaterPass(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = overhead(helper, 606L);
		helper.assertTrue(Satellites.of(server).setMode(server, satellite, SatelliteMode.MAILBOX), "could not set MAILBOX");
		RadioInterfaceBlockEntity station = dataStation(helper, satellite.downlinkHz, "W1AAA");
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.BBS);
		FakePlayer player = owner(helper);
		BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
		long[] greetings = { 0L };
		Consumer<String> say = text -> {
			CompoundTag data = new CompoundTag();
			data.putString("to", satellite.name);
			data.putString("text", text);
			CompoundTag reply = app(computer, player, ComputerApps.BBS, "term", data);
			helper.assertFalse(reply.contains("error"), "could not send '" + text + "': " + reply);
		};
		helper.startSequence()
				.thenWaitUntil(() -> antennaUp(helper, A))
				.thenExecute(() -> powerOn(helper, A))
				.thenWaitUntil(() -> helper.assertTrue(computer.isPowered(), "the computer did not come on"))
				.thenExecute(() -> say.accept("SP W1BBB Pass report"))
				.thenWaitUntil(() -> helper.assertTrue(termHeard(computer, "ENTER MESSAGE"), "no prompt from the satellite: " + BbsHandler.state(computer).termLog()))
				.thenIdle(20)
				.thenExecute(() -> say.accept("Hello from the last pass"))
				.thenIdle(30)
				.thenExecute(() -> say.accept("/EX"))
				.thenWaitUntil(() -> helper.assertTrue(termHeard(computer, "MSG 1 SAVED"), "not saved: " + BbsHandler.state(computer).termLog()))
				.thenExecute(() -> {
					PacketBbs.Message message = satellite.mailbox().find(1);
					helper.assertTrue(message != null && message.from().equals("W1AAA") && message.to().equals("W1BBB") && message.body().equals("Hello from the last pass"),
							"stored on board: " + message);
					helper.assertTrue(satellite.toTag().getCompound("mailbox").getList("messages", Tag.TAG_COMPOUND).size() == 1, "not in the saved data");
					// Gone below the horizon: nothing answers.
					Satellites.of(server).pin(server, satellite, centre.getX() + 50_000.0D, centre.getZ(), 2400L);
					station.setCallsign("W1BBB", "gametest");
					greetings[0] = BbsHandler.state(computer).termLog().stream().filter(line -> line.contains("MSGS FOR YOU")).count();
					say.accept("LM");
				})
				.thenIdle(80)
				.thenExecute(() -> {
					helper.assertTrue(BbsHandler.state(computer).termLog().stream().filter(line -> line.contains("MSGS FOR YOU")).count() == greetings[0],
							"answered below the horizon: " + BbsHandler.state(computer).termLog());
					Satellites.of(server).pin(server, satellite, centre.getX(), centre.getZ(), 2400L); // Its next pass.
					say.accept("LM");
				})
				.thenWaitUntil(() -> helper.assertTrue(termHeard(computer, "W1AAA>W1BBB Pass report"), "not listed for W1BBB: " + BbsHandler.state(computer).termLog()))
				.thenExecute(() -> helper.assertTrue(termHeard(computer, "1 MSGS FOR YOU"), "not greeted with the message waiting"))
				.thenIdle(20)
				.thenExecute(() -> say.accept("R 1"))
				.thenWaitUntil(() -> helper.assertTrue(termHeard(computer, "END MSG 1"), "not read back: " + BbsHandler.state(computer).termLog()))
				.thenExecute(() -> {
					helper.assertTrue(termHeard(computer, "Hello from the last pass"), "the text did not come down");
					helper.assertTrue(satellite.mailbox().find(1).read(), "not marked read");
				})
				.thenSucceed();
	}

	/**
	 * SATTRACK's mode controls: a stranger cannot switch it, the owner can while it passes (and set its APRS frequency,
	 * 144.390 being allowed but flagged, 150 MHz refused), nobody can once it is away, and a commercial one has no modes.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = MODES)
	public static void modeSwitchRefusedWhenNotPassingOrNotAllowed(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = overhead(helper, 707L);
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.SATTRACK);
		FakePlayer owner = owner(helper);
		FakePlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("sat-stranger-2".getBytes()), "Stranger2"));
		stranger.moveTo(owner.getX(), owner.getY(), owner.getZ());
		helper.startSequence()
				.thenExecute(() -> {
					CompoundTag refused = sattrack(computer, stranger, "set", mode(satellite, SatelliteMode.SSTV));
					helper.assertTrue("sat_not_yours".equals(refused.getString("error")), "a stranger switched it: " + refused);
					helper.assertTrue(satellite.mode() == SatelliteMode.REPEATER, "it changed anyway");
					CompoundTag done = sattrack(computer, owner, "set", mode(satellite, SatelliteMode.APRS));
					helper.assertFalse(done.contains("error"), "the owner could not: " + done);
					helper.assertTrue(satellite.mode() == SatelliteMode.APRS, "the mode is " + satellite.mode());

					CompoundTag aprs = field(satellite, "aprs");
					aprs.putDouble("mhz", 144.390D);
					helper.assertFalse(sattrack(computer, owner, "set", aprs).contains("error"), "144.390 refused");
					CompoundTag listed = listed(computer, owner, satellite);
					helper.assertTrue(listed != null && listed.getString("mode").equals("APRS") && listed.getBoolean("aprs_warn")
							&& listed.getString("aprs").startsWith("144.39"), "listed as APRS on 144.390, flagged: " + listed);
					aprs.putDouble("mhz", 150.0D);
					helper.assertTrue("sat_aprs_bad".equals(sattrack(computer, owner, "set", aprs).getString("error")), "150 MHz accepted");

					BlockPos stand = owner.blockPosition();
					Satellites.of(server).pin(server, satellite, stand.getX() + 50_000.0D, stand.getZ(), 2400L);
					CompoundTag away = sattrack(computer, owner, "set", mode(satellite, SatelliteMode.MAILBOX));
					helper.assertTrue("sat_not_passing".equals(away.getString("error")), "switched below the horizon: " + away);
					helper.assertTrue(satellite.mode() == SatelliteMode.APRS, "it changed anyway");

					Satellite commercial = Satellites.of(server).launch(server, false, OPERATOR, "gametest", new Random(708L));
					helper.assertTrue(commercial != null, "no commercial frequencies left");
					Satellites.of(server).pin(server, commercial, stand.getX(), stand.getZ(), 2400L);
					CompoundTag none = sattrack(computer, owner, "set", mode(commercial, SatelliteMode.SSTV));
					helper.assertTrue("sat_no_modes".equals(none.getString("error")) && commercial.mode() == SatelliteMode.REPEATER, "a commercial one switched: " + none);
				})
				.thenSucceed();
	}

	/**
	 * The telemetry beacon: a radio at the computer tuned to the telemetry frequency copies its frames and SATTRACK shows
	 * them decoded; a far antenna hears them on that frequency too. SATTRACK decodes while the computer is on (someone
	 * at it, or a background program: the BBS here, as a fake player at the screen does not count).
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 400, batch = MODES)
	public static void telemetryReceived(GameTestHelper helper) {
		Satellite satellite = overhead(helper, 808L);
		dataStation(helper, satellite.telemetryHz(), "W1TLM");
		RadioComputerBlockEntity computer = computer(helper, ComputerApps.SATTRACK);
		computer.install(ComputerApps.BBS);
		FakePlayer owner = owner(helper);
		Probe far = new Probe(helper, FAR + 300);
		helper.startSequence()
				.thenWaitUntil(() -> antennaUp(helper, A))
				.thenExecute(() -> powerOn(helper, A))
				.thenWaitUntil(() -> {
					CompoundTag listed = listed(computer, owner, satellite);
					helper.assertTrue(listed != null && listed.contains("telemetry"), "no telemetry in SATTRACK (far antenna " + far.dataFrom(satellite, TncPacket.Kind.TELEMETRY).size()
							+ " frames, radio on " + station(helper, A).getFrequency() + " last signal " + station(helper, A).getLastSignalGameTime() + " now " + helper.getLevel().getGameTime()
							+ ", computer " + computer.isPowered() + "): " + listed);
				})
				.thenExecute(() -> {
					CompoundTag t = listed(computer, owner, satellite).getCompound("telemetry");
					helper.assertTrue(t.getDouble("volts") > 11.0D && t.getDouble("volts") < 14.5D, "volts: " + t);
					helper.assertTrue(t.getString("mode").equals("REPEATER") && t.getLong("dk") == Satellites.decayDays() && !t.getBoolean("low"), "decoded: " + t);
					List<AntennaDataPacket> frames = far.dataFrom(satellite, TncPacket.Kind.TELEMETRY);
					helper.assertTrue(!frames.isEmpty() && frames.getFirst().getFrequency() == satellite.telemetryHz(), "the far antenna: " + frames.size());
					helper.assertTrue(SatelliteTelemetry.decode(frames.getFirst().getPayload().text()) != null, "not a telemetry frame: " + frames.getFirst().getPayload().text());
					far.remove();
				})
				.thenSucceed();
	}

	/**
	 * Eclipsed on a flat battery: no repeating (the distress relay still works) and only one power-saving telemetry frame
	 * (battery flat, SAFE, PWR=SAVING) in the first minute, no Morse name; out of it, the next telemetry shows the battery
	 * low and the mode SAFE without power saving, and it repeats again.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 700, batch = MODES)
	public static void eclipseSilence(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = overhead(helper, 909L);
		Satellites.of(server).eclipse(server, satellite, 6000L);
		// 3,000 blocks off, about 27 degrees up: a probe is a rubber ducky, a vertical, and could not uplink from right under it (2026-11-12).
		Probe near = new Probe(helper, 3000);
		Probe far = new Probe(helper, FAR + 400);
		helper.startSequence()
				.thenExecute(() -> {
					helper.assertTrue(SatelliteRadio.silent(satellite, server.overworld(), helper.getLevel().getGameTime()), "not eclipsed");
					SatelliteRadio.onVoice(near, helper.getLevel(), OPERATOR, toneFrame(), satellite.uplinkHz, satellite.tone());
					helper.assertTrue(far.voiceFrom(satellite) == 0 && SatelliteRadio.holding(satellite) == 0, "repeated while eclipsed");
					BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
					DistressAlert alert = new DistressAlert(9090L, DistressAlert.Type.BEACON, DistressAlert.State.ACTIVE, "Eclipsed", null,
							at.getX(), at.getY(), at.getZ(), helper.getLevel().getGameTime(), "");
					DistressBeacons.alert(helper.getLevel(), at, null, alert);
					helper.assertTrue(far.data(TncPacket.Kind.DISTRESS).stream().anyMatch(p -> p.getPayload().text().contains("Eclipsed")), "distress not relayed while eclipsed");
				})
				.thenIdle(SatelliteRadio.TELEMETRY_TICKS * 2 + 60)
				.thenExecute(() -> {
					List<AntennaDataPacket> frames = far.dataFrom(satellite, TncPacket.Kind.TELEMETRY);
					helper.assertTrue(frames.size() == 1, "eclipsed, it sent " + frames.size() + " frames in " + (SatelliteRadio.TELEMETRY_TICKS * 2 + 60) + " ticks, not 1");
					SatelliteTelemetry saving = SatelliteTelemetry.decode(frames.getFirst().getPayload().text());
					helper.assertTrue(saving != null && saving.powerSaving() && saving.low() && saving.mode().equals(SatelliteTelemetry.SAFE),
							"the eclipse frame shows power saving: " + frames.getFirst().getPayload().text());
					helper.assertTrue(far.voice.stream().noneMatch(v -> v.getSourcePlayer() != null && v.getSourcePlayer().equals(satellite.voiceId)),
							"sent its name in Morse while eclipsed");
					Satellites.of(server).eclipse(server, satellite, 0L); // Out of the shadow.
				})
				.thenWaitUntil(() -> helper.assertTrue(far.dataFrom(satellite, TncPacket.Kind.TELEMETRY).size() >= 2, "no telemetry after the eclipse"))
				.thenExecute(() -> {
					SatelliteTelemetry t = SatelliteTelemetry.decode(far.dataFrom(satellite, TncPacket.Kind.TELEMETRY).getLast().getPayload().text());
					helper.assertTrue(t != null && t.low() && !t.powerSaving() && t.mode().equals(SatelliteTelemetry.SAFE) && t.volts() < SatelliteTelemetry.LOW_VOLTS,
							"the battery shows low: " + t);
					SatelliteRadio.onVoice(near, helper.getLevel(), OPERATOR, toneFrame(), satellite.uplinkHz, satellite.tone());
				})
				.thenWaitUntil(() -> helper.assertTrue(far.voiceFrom(satellite) == 1, "not repeating after the eclipse: " + far.voiceFrom(satellite)))
				.thenExecute(() -> {
					near.remove();
					far.remove();
				})
				.thenSucceed();
	}

	/** SSTV mode: its picture comes down on the downlink as a file header, the SSTV tones and the file, to a far antenna. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 1400, batch = MODES)
	public static void sstvPictureComesDown(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = overhead(helper, 1010L);
		byte[] picture = new byte[PhotoImage.PIXELS];
		for(int i = 0; i < picture.length; i++)
			picture[i] = (byte)(4 + (i / 128 + i % 128) % 40); // Map colours, a pattern.
		Satellites.of(server).setMode(server, satellite, SatelliteMode.SSTV);
		Satellites.of(server).setImage(server, satellite, picture, "TEST.IMG");
		Probe far = new Probe(helper, FAR + 500);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(far.dataFrom(satellite, TncPacket.Kind.FILE_HEADER).isEmpty(), "no picture started"))
				.thenExecute(() -> {
					AntennaDataPacket header = far.dataFrom(satellite, TncPacket.Kind.FILE_HEADER).getFirst();
					helper.assertTrue(header.getPayload().item().is(Items.FILLED_MAP) && FileTransfer.MODE_SSTV.equals(FileTransfer.mode(header.getPayload().item())),
							"an SSTV picture: " + header.getPayload());
					helper.assertTrue(header.getFrequency() == satellite.downlinkHz, "on the downlink");
				})
				.thenWaitUntil(() -> helper.assertFalse(far.dataFrom(satellite, TncPacket.Kind.FILE).isEmpty(), "the picture never finished"))
				.thenExecute(() -> {
					helper.assertTrue(far.voiceFrom(satellite) > 500, "SSTV tones: " + far.voiceFrom(satellite) + " frames");
					helper.assertFalse(SatelliteRadio.sendingSstv(satellite), "still sending");
					far.remove();
				})
				.thenSucceed();
	}

	/**
	 * A commercial satellite's license grants its telemetry frequency too: the FCC Monitor calls it LICENSED and no new
	 * license gets it. A license from before (without it) gets it on load, and if another license had been given that
	 * channel meanwhile, the satellite moves its telemetry to a free one instead.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100, batch = MODES)
	public static void commercialTelemetryIsLicensed(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		FakePlayer owner = owner(helper);
		helper.startSequence()
				.thenExecute(() -> {
					List<net.minecraft.world.item.ItemStack> papers = com.arrl.radiocraft.common.items.SatelliteContractItem.launch(helper.getLevel(), owner, false);
					helper.assertTrue(papers != null, "no commercial frequencies left");
					Satellite satellite = Satellites.of(server).all().stream().filter(s -> !s.amateur && s.ownerName.equals("gametest"))
							.reduce((a, b) -> b).orElseThrow();
					CommercialLicenses licenses = CommercialLicenses.of(server);
					CommercialLicense own = licenses.covering(satellite.downlinkHz);
					helper.assertTrue(own != null && own.covers(satellite.uplinkHz) && own.telemetryHz() == satellite.telemetryHz(), "the license: " + own);
					CommercialLicense[] found = new CommercialLicense[1];
					helper.assertTrue(FccHandler.verdict(server, satellite.telemetryHz(), found) == FccHandler.Verdict.LICENSED
							&& found[0].callsign().equals(own.callsign()), "the telemetry is not LICENSED to it");
					helper.assertTrue(licenses.taken(satellite.telemetryHz()), "the telemetry channel could be handed out again");

					// A license from before telemetry: covered on load.
					licenses.coverTelemetry(own.callsign(), 0.0F);
					helper.assertTrue(licenses.covering(satellite.telemetryHz()) == null, "still covered");
					helper.assertTrue(Satellites.of(server).licenseTelemetry(server) >= 1, "nothing changed");
					helper.assertTrue(licenses.get(own.callsign()).telemetryHz() == satellite.telemetryHz(), "not covered on load");

					// Its channel given to someone else meanwhile: the satellite moves.
					float old = satellite.telemetryHz();
					licenses.coverTelemetry(own.callsign(), 0.0F);
					CommercialLicense other = licenses.grant(server, com.arrl.radiocraft.common.radio.Band.VHF_COMMERCIAL, old, 0.0F, "Someone", "", 0, new Random(1L));
					helper.assertTrue(other != null, "could not grant the other license");
					Satellites.of(server).licenseTelemetry(server);
					helper.assertTrue(satellite.telemetryHz() != old && licenses.get(own.callsign()).covers(satellite.telemetryHz())
							&& licenses.covering(satellite.telemetryHz()).callsign().equals(own.callsign()), "not moved to a free licensed channel: " + satellite.telemetryHz());
					licenses.revoke(other.callsign());
					for(CommercialLicense l : List.copyOf(licenses.all()))
						if(l.holder().equals("gametest"))
							licenses.revoke(l.callsign());
				})
				.thenSucceed();
	}
}
