package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.items.SatelliteContractItem;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.distress.DistressAlert;
import com.arrl.radiocraft.common.radio.distress.DistressBeacons;
import com.arrl.radiocraft.common.radio.satellite.Satellite;
import com.arrl.radiocraft.common.radio.satellite.SatelliteDelay;
import com.arrl.radiocraft.common.radio.satellite.SatelliteMode;
import com.arrl.radiocraft.common.radio.satellite.SatelliteOrbit;
import com.arrl.radiocraft.common.radio.satellite.SatelliteRadio;
import com.arrl.radiocraft.common.radio.satellite.Satellites;
import com.arrl.radiocraft.common.radio.satellite.imaging.AptLine;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Geostationary satellites, the transit delay and decommissioning (2026-11-12, {@code doc/SatellitePlan.md}). A
 * geostationary satellite points at the plot: a station 4,000 blocks away hears it after half an orbiting one's delay,
 * one 10,500 blocks away never does; it digipeats after half the delay too, relays distress at once and images a strip
 * through the plot. Decommissioned from SATTRACK by its owner (not a stranger, not an allowed player), a satellite
 * frees its frequencies (a commercial one's license is revoked), stays listed, and repeats, relays and beacons nothing.
 * <p>
 * Their own batch, like the other satellite tests: a satellite covers 10,000 blocks.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class SatelliteGeoGameTests {

	private static final String BATCH = "satellite_geo";
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-satellite-operator".getBytes());
	private static final int INSIDE = 4000, OUTSIDE = 10_500;

	@AfterBatch(batch = BATCH)
	public static void bringThemDown(ServerLevel level) {
		SatelliteGameTests.bringThemDown(level);
	}

	private static BlockPos centre(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(8, 2, 8));
	}

	/** A geostationary satellite pointing at the plot. */
	private static Satellite geo(GameTestHelper helper, boolean amateur, long seed) {
		MinecraftServer server = helper.getLevel().getServer();
		BlockPos centre = centre(helper);
		Satellite satellite = Satellites.of(server).launchGeostationary(server, amateur, OPERATOR, "gametest", new Random(seed), centre.getX(), centre.getZ());
		helper.assertTrue(satellite != null, "no frequency pair left");
		return satellite;
	}

	/**
	 * Voice through a geostationary satellite: held, then down 4,000 blocks away no sooner than half the orbiting delay,
	 * never 10,500 blocks away. It stays put and has no passes.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void geostationaryRelaysToItsCoverageAfterHalfTheDelay(GameTestHelper helper) {
		Satellite satellite = geo(helper, true, 1201L);
		SatelliteGameTests.Probe talker = new SatelliteGameTests.Probe(helper, 3);
		SatelliteGameTests.Probe inside = new SatelliteGameTests.Probe(helper, INSIDE);
		SatelliteGameTests.Probe outside = new SatelliteGameTests.Probe(helper, -OUTSIDE);
		long[] spoke = { 0L };
		helper.startSequence()
				.thenExecute(() -> {
					BlockPos centre = centre(helper);
					long now = helper.getLevel().getGameTime();
					helper.assertTrue(satellite.geostationary() && satellite.nextPasses(centre.getX(), centre.getZ(), now, 4).isEmpty(), "a geostationary one has no passes");
					SatelliteOrbit.Look here = satellite.look(centre.getX(), centre.getZ(), now);
					helper.assertTrue(here.up() && here.equals(satellite.look(centre.getX(), centre.getZ(), now + 12_345L)), "it does not stay put over the plot: " + here);
					helper.assertFalse(satellite.look(centre.getX() - OUTSIDE, centre.getZ(), now).up(), "up 10,500 blocks from where it points");
					helper.assertTrue(here.distance() < 1.0D, "its coverage is not centred on where it was activated: " + here.distance());
					SatelliteRadio.onVoice(talker, helper.getLevel(), OPERATOR, SatelliteGameTests.toneFrame(), satellite.uplinkHz, satellite.tone());
					spoke[0] = System.currentTimeMillis();
					helper.assertTrue(inside.voiceFrom(satellite) == 0 && SatelliteRadio.holding(satellite) == 1, "not held for the transit delay");
				})
				.thenWaitUntil(() -> helper.assertTrue(inside.voiceFrom(satellite) == 1, "not heard " + INSIDE + " blocks from where it points"))
				.thenExecute(() -> {
					long waited = System.currentTimeMillis() - spoke[0];
					helper.assertTrue(waited >= SatelliteDelay.voiceMillis(true) - 5, "came down after " + waited + " ms");
					helper.assertTrue(outside.voiceFrom(satellite) == 0, "heard outside its coverage");
					talker.remove();
					inside.remove();
					outside.remove();
				})
				.thenSucceed();
	}

	/** An APRS digipeat through a geostationary satellite comes down after half the orbiting delay; a distress alert at once. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 200, batch = BATCH)
	public static void geostationaryDigipeatsAfterHalfTheDelayAndRelaysDistressAtOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = geo(helper, true, 1202L);
		helper.assertTrue(Satellites.of(server).setMode(server, satellite, SatelliteMode.APRS), "could not set APRS");
		SatelliteGameTests.Probe talker = new SatelliteGameTests.Probe(helper, 5);
		SatelliteGameTests.Probe inside = new SatelliteGameTests.Probe(helper, INSIDE + 10);
		long[] sent = { 0L };
		helper.startSequence()
				.thenExecute(() -> {
					BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
					DistressAlert alert = new DistressAlert(1202L, DistressAlert.Type.BEACON, DistressAlert.State.ACTIVE, "GeoCastaway", null,
							at.getX(), at.getY(), at.getZ(), helper.getLevel().getGameTime(), "");
					DistressBeacons.alert(helper.getLevel(), at, null, alert);
					// At once, by this satellite or another of the batch up over the plot (the first up relays it).
					helper.assertTrue(inside.data(TncPacket.Kind.DISTRESS).stream().anyMatch(p -> p.getPayload().text().contains("GeoCastaway")), "distress not relayed at once");
					sent[0] = helper.getLevel().getGameTime();
					SatelliteRadio.onData(talker, helper.getLevel(), TncPacket.beacon(120_201L, "W1GEO", at, "VIA GEO", "HOUSE"), satellite.aprsHz());
					helper.assertTrue(inside.dataFrom(satellite, TncPacket.Kind.BEACON).isEmpty(), "digipeated at once");
				})
				.thenWaitUntil(() -> helper.assertTrue(inside.dataFrom(satellite, TncPacket.Kind.BEACON).size() == 1, "not digipeated"))
				.thenExecute(() -> {
					long took = helper.getLevel().getGameTime() - sent[0];
					int want = SatelliteDelay.ticks(SatelliteDelay.Kind.DIGIPEAT, true);
					helper.assertTrue(took >= want && took <= want + 2, "digipeated after " + took + " ticks, not " + want);
					helper.assertTrue(want < SatelliteDelay.ticks(SatelliteDelay.Kind.DIGIPEAT, false), "not shorter than an orbiting one's");
					talker.remove();
					inside.remove();
				})
				.thenSucceed();
	}

	/**
	 * A geostationary weather imager sends frames (2026-11-14): a square grid centred where it points, rows north to
	 * south with rising row numbers, then an end-of-frame with no pixels, then the next frame from row 0.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 600, batch = BATCH)
	public static void geostationaryImagesWhereItPoints(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellite satellite = geo(helper, false, 1203L);
		helper.assertTrue(Satellites.of(server).setMode(server, satellite, SatelliteMode.IMAGING), "could not set IMAGING");
		SatelliteGameTests.Probe inside = new SatelliteGameTests.Probe(helper, 30);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(inside.dataFrom(satellite, TncPacket.Kind.APT).size() >= 3, "no scan lines"))
				.thenExecute(() -> {
					BlockPos centre = centre(helper);
					List<AptLine.Geometry> lines = inside.dataFrom(satellite, TncPacket.Kind.APT).stream()
							.map(p -> AptLine.Geometry.decode(p.getPayload().text())).toList();
					int lastRow = -1;
					for(AptLine.Geometry g : lines) {
						helper.assertTrue(g != null && g.inFrame(), "not a frame line");
						helper.assertTrue(Math.abs(g.cx() - centre.getX()) <= 1, "a row not centred where it points: x " + g.cx());
						helper.assertTrue(Math.abs(g.cz() - centre.getZ()) <= com.arrl.radiocraft.common.radio.satellite.imaging.AptSwath.SWATH_BLOCKS / 2 + 1,
								"a row outside its frame: z " + g.cz());
						helper.assertTrue(g.rx() == 1.0D && g.rz() == 0.0D, "rows run west to east");
						helper.assertTrue(g.row() > lastRow, "rows out of order: " + g.row() + " after " + lastRow);
						lastRow = g.row();
					}
					// Skip to the frame's last rows: they, the end of the frame, and then row 0 of the next.
					com.arrl.radiocraft.common.radio.satellite.SatelliteRadio.geoSkipTo(satellite, lines.getFirst().rows() - 2);
				})
				.thenWaitUntil(() -> {
					int[] at = com.arrl.radiocraft.common.radio.satellite.SatelliteRadio.geoScan(satellite);
					helper.assertTrue(at[0] >= 1 && at[1] >= 1, "no next frame yet: frame " + at[0] + ", row " + at[1]);
				})
				.thenExecute(() -> {
					List<AptLine.Geometry> lines = inside.dataFrom(satellite, TncPacket.Kind.APT).stream()
							.map(p -> AptLine.Geometry.decode(p.getPayload().text())).toList();
					helper.assertTrue(lines.stream().anyMatch(g -> g != null && g.frameEnd() && g.frame() == 0), "no end of frame 0");
					helper.assertTrue(lines.stream().anyMatch(g -> g != null && g.frame() == 1 && g.row() == 0), "frame 1 did not start at row 0");
					inside.remove();
				})
				.thenSucceed();
	}

	private static CompoundTag decommission(Satellite satellite, String confirm) {
		CompoundTag data = new CompoundTag();
		data.putString("name", satellite.name);
		data.putString("field", "decommission");
		data.putString("confirm", confirm);
		return data;
	}

	@Nullable
	private static CompoundTag listed(RadioComputerBlockEntity computer, FakePlayer player, Satellite satellite) {
		CompoundTag status = SatelliteGameTests.sattrack(computer, player, "status", new CompoundTag()).getCompound("data");
		for(Tag t : status.getList("satellites", Tag.TAG_COMPOUND))
			if(((CompoundTag)t).getString("name").equals(satellite.name))
				return (CompoundTag)t;
		return null;
	}

	/**
	 * Decommissioning from SATTRACK: a stranger and an allowed player cannot, the owner can (with the app's confirmation);
	 * then it takes no more changes, is listed DEAD, repeats, relays and beacons nothing, and its frequencies are free: a
	 * new satellite drawn the same way gets its pair, and a commercial one's license is revoked.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 600, batch = BATCH)
	public static void decommissionFreesFrequenciesAndStopsRelaying(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Satellites satellites = Satellites.of(server);
		Satellite satellite = satellites.launch(server, true, OPERATOR, "gametest", new Random(4242L));
		helper.assertTrue(satellite != null, "no frequency pair left");
		BlockPos centre = centre(helper);
		satellites.pin(server, satellite, centre.getX(), centre.getZ(), 6000L);
		BlockPos at = new BlockPos(4, 2, 4);
		helper.setBlock(at, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(at.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, at, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.SATTRACK);
		BlockPos stand = helper.absolutePos(at.south(2));
		FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(OPERATOR, "gametest"));
		owner.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
		FakePlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("sat-geo-stranger".getBytes()), "GeoStranger"));
		stranger.moveTo(owner.getX(), owner.getY(), owner.getZ());
		FakePlayer friend = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("sat-geo-friend".getBytes()), "GeoFriend"));
		friend.moveTo(owner.getX(), owner.getY(), owner.getZ());
		satellites.allow(server, satellite, "GeoFriend");
		SatelliteGameTests.Probe talker = new SatelliteGameTests.Probe(helper, 5);
		SatelliteGameTests.Probe far = new SatelliteGameTests.Probe(helper, INSIDE + 20);
		int[] telemetry = { 0 };
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(far.dataFrom(satellite, TncPacket.Kind.TELEMETRY).isEmpty(), "no telemetry before"))
				.thenExecute(() -> {
					helper.assertTrue("sat_not_yours".equals(SatelliteGameTests.sattrack(computer, stranger, "set", decommission(satellite, satellite.name)).getString("error")),
							"a stranger decommissioned it");
					helper.assertTrue("sat_owner_only".equals(SatelliteGameTests.sattrack(computer, friend, "set", decommission(satellite, satellite.name)).getString("error")),
							"an allowed player decommissioned it");
					helper.assertTrue(SatelliteGameTests.sattrack(computer, owner, "set", decommission(satellite, "")).contains("error"), "decommissioned unconfirmed");
					helper.assertFalse(satellite.decommissioned(), "decommissioned anyway");
					CompoundTag done = SatelliteGameTests.sattrack(computer, owner, "set", decommission(satellite, satellite.name));
					helper.assertFalse(done.contains("error"), "the owner could not: " + done);
					helper.assertTrue(satellite.decommissioned() && !Satellites.working().contains(satellite) && Satellites.onOrbit().contains(satellite),
							"not decommissioned, or gone from orbit");
					CompoundTag listed = listed(computer, owner, satellite);
					helper.assertTrue(listed != null && listed.getBoolean("dead") && listed.getBoolean("passing"), "not listed, dead and trackable: " + listed);
					CompoundTag tone = new CompoundTag();
					tone.putString("name", satellite.name);
					tone.putString("field", "tone");
					tone.putFloat("tone", 100.0F);
					helper.assertTrue("sat_decommissioned".equals(SatelliteGameTests.sattrack(computer, owner, "set", tone).getString("error")), "changed after");

					SatelliteRadio.onVoice(talker, helper.getLevel(), OPERATOR, SatelliteGameTests.toneFrame(), satellite.uplinkHz, satellite.tone());
					helper.assertTrue(SatelliteRadio.holding(satellite) == 0, "it heard voice");
					int relayed = far.dataFrom(satellite, TncPacket.Kind.DISTRESS).size(); // Others' alerts before it went dead.
					BlockPos spot = helper.absolutePos(new BlockPos(5, 2, 5));
					DistressBeacons.alert(helper.getLevel(), spot, null, new DistressAlert(4243L, DistressAlert.Type.BEACON, DistressAlert.State.ACTIVE, "Nobody", null,
							spot.getX(), spot.getY(), spot.getZ(), helper.getLevel().getGameTime(), ""));
					helper.assertTrue(far.dataFrom(satellite, TncPacket.Kind.DISTRESS).size() == relayed, "it relayed distress");
					telemetry[0] = far.dataFrom(satellite, TncPacket.Kind.TELEMETRY).size();

					// Its pair is free: a satellite drawn the same way gets it.
					Satellite next = satellites.launch(server, true, OPERATOR, "gametest", new Random(4242L));
					helper.assertTrue(next != null && next.downlinkHz == satellite.downlinkHz && next.uplinkHz == satellite.uplinkHz,
							"its pair was not freed: " + (next == null ? "none" : next.downlinkHz) + " vs " + satellite.downlinkHz);

					// A commercial one's license goes back too.
					List<net.minecraft.world.item.ItemStack> papers = SatelliteContractItem.launch(helper.getLevel(), owner, false);
					helper.assertTrue(papers != null, "no commercial frequencies left");
					Satellite commercial = satellites.all().stream().filter(s -> !s.amateur && s.ownerName.equals("gametest") && !s.decommissioned())
							.reduce((a, b) -> b).orElseThrow();
					CommercialLicenses licenses = CommercialLicenses.of(server);
					helper.assertTrue(licenses.taken(commercial.downlinkHz), "the license does not hold its downlink");
					satellites.pin(server, commercial, stand.getX(), stand.getZ(), 2400L);
					helper.assertFalse(SatelliteGameTests.sattrack(computer, owner, "set", decommission(commercial, commercial.name)).contains("error"), "could not");
					helper.assertFalse(licenses.taken(commercial.downlinkHz) || licenses.taken(commercial.uplinkHz) || licenses.taken(commercial.telemetryHz()),
							"its license still holds its frequencies");
				})
				.thenIdle(SatelliteRadio.TELEMETRY_TICKS * 2 + 20)
				.thenExecute(() -> {
					helper.assertTrue(far.voiceFrom(satellite) == 0, "it repeated after");
					helper.assertTrue(far.dataFrom(satellite, TncPacket.Kind.TELEMETRY).size() == telemetry[0], "it kept beaconing");
					talker.remove();
					far.remove();
				})
				.thenSucceed();
	}
}
