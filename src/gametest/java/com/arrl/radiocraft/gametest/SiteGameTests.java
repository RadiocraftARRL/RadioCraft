package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.CommercialRadioModuleBlockEntity;
import com.arrl.radiocraft.common.blocks.StackedBlocks;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.CommercialStations;
import com.arrl.radiocraft.common.radio.TransmitterLog;
import com.arrl.radiocraft.common.radio.voice.StationProgram;
import com.arrl.radiocraft.common.worldgen.RotateBlocksProcessor;
import com.arrl.radiocraft.common.worldgen.SitePiece;
import com.arrl.radiocraft.common.worldgen.SiteVariant;
import com.arrl.radiocraft.common.worldgen.VillageHouses;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.gametest.sites.SiteCheck;
import com.arrl.radiocraft.gametest.sites.SiteTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The radio sites the world generator places, and the commercial radio module stations that stay on the air without
 * their chunks: every site works as found, Long Lines keeps one to four horns, and a site's module station is
 * predicted just as the site will have it.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class SiteGameTests {

	/** Room for the biggest site (Long Lines, 19 by 42 by 13). */
	private static final String SITE = "empty_site";
	private static final String SITES_BATCH = "radio_sites";

	@BeforeBatch(batch = SITES_BATCH)
	public static void powerRequired(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(true);
	}

	@AfterBatch(batch = SITES_BATCH)
	public static void powerFree(ServerLevel level) {
		RadiocraftServerConfig.RADIO_POWER_REQUIRED.set(false);
	}

	private static StructureTemplate template(GameTestHelper helper, String name) {
		return helper.getLevel().getStructureManager().get(Radiocraft.id("sites/" + name))
				.orElseThrow(() -> new IllegalStateException("no template sites/" + name));
	}

	/** Puts site {@code name} down with all its parts and waits for every station in it to be working. */
	private static void siteWorks(GameTestHelper helper, String name, boolean powered) {
		siteWorks(helper, name, powered, false);
	}

	/** {@link #siteWorks}, and with {@code dark} every radio in it off and every battery flat as well. */
	private static void siteWorks(GameTestHelper helper, String name, boolean powered, boolean dark) {
		ServerLevel level = helper.getLevel();
		level.setDayTime(6000L); // Noon.
		BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
		StructureTemplate template = template(helper, name);
		template.placeInWorld(level, origin, origin, SitePiece.settings(), level.getRandom(), Block.UPDATE_ALL);
		Vec3i size = template.getSize();
		BlockPos far = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
		helper.succeedWhen(() -> {
			SiteCheck.Report report = SiteCheck.check(level, origin, far, powered);
			helper.assertTrue(report.problems().isEmpty(), name + ": " + String.join("; ", report.problems()));
			if(dark)
				assertDark(helper, origin, size);
		});
	}

	@GameTest(template = SITE, batch = SITES_BATCH, timeoutTicks = 200)
	public static void repeaterSiteWorks(GameTestHelper helper) {
		siteWorks(helper, "repeater_site", true);
	}

	@GameTest(template = SITE, batch = SITES_BATCH, timeoutTicks = 200)
	public static void buriedSiteIsWiredUp(GameTestHelper helper) {
		siteWorks(helper, "buried_site", false);
	}

	@GameTest(template = SITE, batch = SITES_BATCH, timeoutTicks = 200)
	public static void ionosphericObservatoryWorks(GameTestHelper helper) {
		siteWorks(helper, "ionospheric_observatory", true);
	}

	@GameTest(template = SITE, batch = SITES_BATCH, timeoutTicks = 200)
	public static void weatherObservatoryWorks(GameTestHelper helper) {
		siteWorks(helper, "weather_observatory", true);
	}

	@GameTest(template = SITE, batch = SITES_BATCH, timeoutTicks = 200)
	public static void directionFindingStationWorks(GameTestHelper helper) {
		siteWorks(helper, "direction_finding_station", true);
	}

	@GameTest(template = SITE, batch = SITES_BATCH, timeoutTicks = 200)
	public static void longLinesWorks(GameTestHelper helper) {
		siteWorks(helper, "long_lines", true);
	}

	/** Found dark (maintainer, 2026-11-05): wired up, but the battery flat and the radio off, like the buried site. */
	@GameTest(template = SITE, batch = SITES_BATCH, timeoutTicks = 200)
	public static void hamShackIsFoundDark(GameTestHelper helper) {
		siteWorks(helper, "ham_shack", false, true);
	}

	/** Every radio in the box from {@code origin} of {@code size} is off and every battery empty. */
	private static void assertDark(GameTestHelper helper, BlockPos origin, Vec3i size) {
		for(BlockPos at : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			net.minecraft.world.level.block.entity.BlockEntity be = helper.getLevel().getBlockEntity(at);
			if(be instanceof com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity radio)
				helper.assertFalse(radio.isPowered(), "a radio was found on at " + at.toShortString());
			if(com.arrl.radiocraft.api.capabilities.IBENetworks.getObject(helper.getLevel(), at) instanceof com.arrl.radiocraft.common.be_networks.network_objects.BatteryNetworkObject battery)
				helper.assertTrue(battery.getStorage().getEnergyStored() == 0, "a battery was found charged at " + at.toShortString());
		}
	}

	/**
	 * The village ham shack, turned as a village turns its houses (known shapes, so nothing is fixed up after): the radio
	 * faces into the room, its coax still reaches the J-pole and its wire the battery, for every turn.
	 */
	@GameTest(template = SITE, batch = SITES_BATCH, timeoutTicks = 200)
	public static void villageHamShackWorksTurned(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.setDayTime(6000L);
		StructureTemplate template = level.getStructureManager().get(VillageHouses.template("plains"))
				.orElseThrow(() -> new IllegalStateException("no template " + VillageHouses.template("plains")));
		List<BoundingBox> boxes = new java.util.ArrayList<>();
		Rotation[] turns = { Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90 };
		for(int i = 0; i < turns.length; i++) {
			// Four copies, one in each corner of the plot (a turn swings the house round its origin).
			BlockPos origin = helper.absolutePos(ORIGINS[i]);
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(turns[i]).setKnownShape(true)
					.addProcessor(JigsawReplacementProcessor.INSTANCE).addProcessor(RotateBlocksProcessor.INSTANCE);
			template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);
			boxes.add(template.getBoundingBox(settings, origin));
			BlockPos radio = StructureTemplate.calculateRelativePosition(settings, new BlockPos(2, 1, 1)).offset(origin);
			BlockState state = level.getBlockState(radio);
			helper.assertTrue(state.is(RadiocraftBlocks.VHF_BASE_STATION.get()), turns[i] + ": no radio at " + radio.toShortString() + " but " + state);
			helper.assertTrue(state.getValue(com.arrl.radiocraft.common.blocks.radios.RadioBlock.HORIZONTAL_FACING) == turns[i].rotate(net.minecraft.core.Direction.SOUTH),
					turns[i] + ": the radio faces " + state);
		}
		helper.succeedWhen(() -> {
			for(int i = 0; i < boxes.size(); i++) {
				BoundingBox box = boxes.get(i);
				SiteCheck.Report report = SiteCheck.check(level, new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()), false);
				helper.assertTrue(report.problems().isEmpty(), turns[i] + ": " + String.join("; ", report.problems()));
			}
		});
	}

	/** Where each turned copy of the village shack goes, so the four miss each other on the 24 by 24 plot. */
	private static final BlockPos[] ORIGINS = { new BlockPos(1, 1, 1), new BlockPos(22, 1, 1), new BlockPos(9, 1, 22), new BlockPos(14, 1, 22) };

	/** The server added the shack to every vanilla village type's house pool, once. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void villagesCanHaveAHamShack(GameTestHelper helper) {
		var pools = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.TEMPLATE_POOL);
		for(String type : VillageHouses.TYPES) {
			var pool = pools.get(VillageHouses.housePool(type));
			helper.assertTrue(pool != null, "no house pool for " + type);
			var templates = VillageHouses.templates(pool);
			long shacks = templates.stream().filter(element -> element.toString().contains(VillageHouses.template(type).toString())).count();
			helper.assertTrue(shacks == VillageHouses.WEIGHT, type + " houses have " + shacks + " ham shacks");
			helper.assertTrue(helper.getLevel().getStructureManager().get(VillageHouses.template(type)).isPresent(), "no template for " + type);
		}
		helper.assertTrue(VillageHouses.add(helper.getLevel().registryAccess()) == 0, "the shacks were added a second time");
		helper.succeed();
	}

	/** RadioCraft's blocks turn with a turned piece: a radio's facing, a wire's sides and the side it hugs. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void radioCraftBlocksTurnWithThePiece(GameTestHelper helper) {
		BlockState coax = RadiocraftBlocks.COAX_WIRE.get().defaultBlockState().setValue(WireBlock.EAST, true).setValue(WireBlock.UP, true)
				.setValue(WireBlock.HUG, WireBlock.Edge.NORTH);
		BlockState turned = RotateBlocksProcessor.rotate(coax, Rotation.CLOCKWISE_90);
		helper.assertTrue(turned.getValue(WireBlock.SOUTH) && !turned.getValue(WireBlock.EAST) && turned.getValue(WireBlock.UP), "coax sides: " + turned);
		helper.assertTrue(turned.getValue(WireBlock.HUG) == WireBlock.Edge.EAST, "coax hug: " + turned);
		helper.assertTrue(RotateBlocksProcessor.rotate(coax, Rotation.NONE) == coax, "turned by nothing");
		BlockState radio = RadiocraftBlocks.VHF_BASE_STATION.get().defaultBlockState().setValue(com.arrl.radiocraft.common.blocks.radios.RadioBlock.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH);
		helper.assertTrue(RotateBlocksProcessor.rotate(radio, Rotation.CLOCKWISE_180).getValue(com.arrl.radiocraft.common.blocks.radios.RadioBlock.HORIZONTAL_FACING) == net.minecraft.core.Direction.NORTH,
				"the radio did not turn");
		helper.succeed();
	}

	private static int horns(List<BoundingBox> removed) {
		int[][] horns = { { 1, 33, 2 }, { 5, 33, 2 }, { 1, 33, 8 }, { 5, 33, 8 } };
		int kept = 0;
		for(int[] horn : horns)
			if(removed.stream().noneMatch(box -> box.isInside(new BlockPos(horn[0], horn[1], horn[2]))))
				kept++;
		return kept;
	}

	/** Copies of Long Lines here and there keep one to four horns, not always the same number; one placed has as many as worked out. */
	@GameTest(template = SITE, timeoutTicks = 20)
	public static void longLinesKeepsOneToFourHorns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<SiteVariant> variants = SiteTemplates.variants(level, "long_lines");
		helper.assertTrue(!variants.isEmpty(), "long_lines has no variants");
		Set<Integer> counts = new HashSet<>();
		for(int i = 0; i < 64; i++) {
			int kept = horns(SiteVariant.removed(variants, level.getSeed(), new BlockPos(i * 448, 140, i * -272)));
			helper.assertTrue(kept >= 1 && kept <= 4, kept + " horns");
			counts.add(kept);
		}
		helper.assertTrue(counts.size() >= 3, "only ever " + counts + " horns");
		BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
		template(helper, "long_lines").placeInWorld(level, origin, origin, SitePiece.settings(variants), level.getRandom(), Block.UPDATE_ALL);
		int expected = horns(SiteVariant.removed(variants, level.getSeed(), origin));
		helper.runAfterDelay(2, () -> {
			int horns = 0, radios = 0;
			for(BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(18, 41, 12))) {
				BlockEntity entity = level.getBlockEntity(pos);
				if(entity == null)
					continue;
				ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
				if(id != null && id.getPath().equals("microwave_horn"))
					horns++;
				if(id != null && id.getPath().equals("microwave_radio"))
					radios++;
			}
			helper.assertTrue(horns == expected, horns + " horns placed, " + expected + " worked out");
			helper.assertTrue(radios == expected, radios + " microwave radios for " + expected + " horns");
			helper.succeed();
		});
	}

	/** The station worked out for a site's module is the one the module has once the site is placed. */
	@GameTest(template = SITE, timeoutTicks = 40)
	public static void predictedModuleIsThePlacedOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
		SitePiece piece = new SitePiece(level.getStructureManager(), Radiocraft.id("sites/repeater_site"), origin, List.of());
		List<CommercialStations.Station> predicted = piece.stations(level);
		helper.assertTrue(predicted.size() == 1, predicted.size() + " stations worked out for the repeater site");
		piece.template().placeInWorld(level, origin, origin, piece.placeSettings(), level.getRandom(), Block.UPDATE_ALL);
		CommercialStations.Station expected = predicted.getFirst();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockEntity(expected.pos()) instanceof CommercialRadioModuleBlockEntity, "no module at " + expected.pos().toShortString());
			CommercialRadioModuleBlockEntity module = (CommercialRadioModuleBlockEntity)level.getBlockEntity(expected.pos());
			CommercialStations.Station placed = module.station();
			helper.assertTrue(placed != null && placed.equals(new CommercialStations.Station(expected.pos(), expected.kind(), expected.band(), expected.frequency(),
					expected.seed(), expected.callsign(), false)), "predicted " + expected + ", placed " + placed);
			helper.assertTrue(CommercialStations.get(level).get(expected.pos()) != null, "the placed module is not on the air");
			helper.succeed();
		});
	}

	/** A station whose chunk is not loaded (nothing there at all) is on the air all the same. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 100)
	public static void stationSendsWithItsChunkUnloaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(200_000, 0, 0);
		float hz = 147_555_000.0F;
		helper.assertTrue(!level.isLoaded(far), "the far spot is loaded");
		CommercialStations.get(level).register(new CommercialStations.Station(far, StationProgram.Kind.DF_BEEPER, "2m", hz, 1L, "K0TST", false));
		helper.succeedWhen(() -> {
			helper.assertTrue(TransmitterLog.nearest(level, far, hz, 1000.0D, 64.0D, 20) != null, "nothing on the air from the unloaded station");
			CommercialStations.get(level).remove(far);
		});
	}

	/** A module whose chunk unloads stays on the air; one broken goes off it. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void moduleStaysOnTheAirUnloadedNotBroken(GameTestHelper helper) {
		BlockPos bottom = new BlockPos(4, 2, 4), other = new BlockPos(10, 2, 10);
		for(BlockPos at : new BlockPos[] { bottom, other })
			for(int i = 0; i < StackedBlocks.HEIGHT; i++)
				helper.setBlock(at.above(i), RadiocraftBlocks.COMMERCIAL_RADIO_MODULE.get().defaultBlockState().setValue(StackedBlocks.PART, i));
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			CommercialStations stations = CommercialStations.get(level);
			helper.assertTrue(stations.get(helper.absolutePos(bottom)) != null && stations.get(helper.absolutePos(other)) != null, "the modules are not on the air");
			CommercialRadioModuleBlockEntity module = GameTestUtil.blockEntity(helper, other, CommercialRadioModuleBlockEntity.class);
			module.onChunkUnloaded();
			module.setRemoved();
			helper.assertTrue(stations.get(helper.absolutePos(other)) != null, "an unloaded module went off the air");
			level.setBlock(helper.absolutePos(bottom), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			helper.assertTrue(stations.get(helper.absolutePos(bottom)) == null, "a broken module is still on the air");
			stations.remove(helper.absolutePos(other));
			helper.succeed();
		});
	}

	/** A station struck off when its chunk is loaded and its module is not there (broken while unloaded, or predicted wrong). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 60)
	public static void missingModuleIsStruckOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(6, 2, 6));
		CommercialStations.get(level).register(new CommercialStations.Station(pos, StationProgram.Kind.CW_BEACON, Band.getBand("40m").name(), 7_050_000.0F,
				2L, "K0TST", true));
		helper.succeedWhen(() -> helper.assertTrue(CommercialStations.get(level).get(pos) == null, "a station with no module is still on the air"));
	}
}
