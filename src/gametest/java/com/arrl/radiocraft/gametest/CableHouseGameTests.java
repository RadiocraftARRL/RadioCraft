package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.WireGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The maintainer's own house (2026-11-14, "Demo World" around -34 87 547: a bunker of radios, batteries, computers and
 * switches with power, data and coax run along walls, up corners, through passthroughs and in bundles), saved with
 * {@code /cablecheck save} as {@code house_cables}: every cable check of the matrix on a real build.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableHouseGameTests {

	@GameTest(template = "house_cables", timeoutTicks = 200, required = false, batch = "cablehouse") // Alone: its radios share the GameTests' one HF network.
	public static void houseCablesLookRight(GameTestHelper helper) {
		helper.startSequence().thenIdle(5).thenExecute(() -> {
			ServerLevel level = helper.getLevel();
			net.minecraft.world.phys.AABB box = helper.getBounds();
			BlockPos lo = BlockPos.containing(box.minX, box.minY, box.minZ), hi = BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1);
			BlockPos origin = helper.absolutePos(BlockPos.ZERO);
			Set<String> problems = new LinkedHashSet<>();
			for(String p : WireGeometry.check(level, lo, hi))
				problems.add(relative(p, origin));
			problems.addAll(CableMatrixGameTests.overlaps(level, lo, hi, origin, Set.of()));
			problems.addAll(CableMatrixGameTests.stubs(level, lo, hi, origin));
			problems.addAll(CableShapes.check(level, lo, hi, origin, null, null));
			if(!problems.isEmpty()) {
				// In the house's own coordinates (origin -55 82 517 in Demo World), to find them in the world.
				StringBuilder all = new StringBuilder(problems.size() + " cable problems in the house (house positions; the world's are these + -55 82 517):");
				for(String p : problems)
					all.append(System.lineSeparator()).append("  ").append(p);
				for(String line : CableCheckCommand.describeNamed(level, problems, origin))
					all.append(System.lineSeparator()).append(line);
				Radiocraft.LOGGER.info(all.toString());
				helper.fail(problems.size() + " cable problems in the house, the first: " + problems.iterator().next());
			}
		}).thenSucceed();
	}

	/**
	 * A bundle in the house whose two strands come in from the east in one order and leave in the other (2026-11-15):
	 * the grey power strand's step across goes over the plain one, a bridge (CableRoutes#bridges), instead of running
	 * through it, and the bundle solver still sees the crossing it could not avoid.
	 */
	/**
	 * Upstairs in the maintainer's house (2026-11-16, Demo World -42 98 533 to -37 107 546, saved as {@code house_upstairs}):
	 * a bundle kept in a corner put its red strand a pixel from the face it leaves by, with no room to step over to
	 * where it met the next bundle, so the step poked into that block and flickered against its cable (a gap at the
	 * face); and by the window a bundle whose strands step into a passthrough ran them through each other. Each is
	 * now drawn straight through the face on the cramped side, and the room side steps.
	 */
	@GameTest(template = "house_upstairs", timeoutTicks = 100)
	public static void crampedStrandsStepOnTheRoomySide(GameTestHelper helper) {
		helper.startSequence().thenIdle(5).thenExecute(() -> {
			java.util.List<String> problems = new java.util.ArrayList<>();
			for(BlockPos at : java.util.List.of(new BlockPos(2, 2, 12), new BlockPos(2, 9, 3), new BlockPos(3, 9, 3), new BlockPos(3, 9, 2))) {
				BlockPos abs = helper.absolutePos(at);
				problems.addAll(WireGeometry.check(helper.getLevel(), abs, abs));
			}
			helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(4, problems.size()))));
		}).thenSucceed();
	}

	@GameTest(template = "house_cables", timeoutTicks = 200, batch = "cablehouse")
	public static void houseCrossingIsBridged(GameTestHelper helper) {
		helper.startSequence().thenIdle(5).thenExecute(() -> {
			BlockPos at = helper.absolutePos(new BlockPos(15, 2, 19));
			java.util.List<String> problems = WireGeometry.check(helper.getLevel(), at, at);
			problems.removeIf(p -> !p.contains("runs through"));
			helper.assertTrue(problems.isEmpty(), "The strands still run through each other: " + problems);
			boolean raised = false;
			for(WireGeometry.Segment s : WireGeometry.at(helper.getLevel(), at))
				raised |= s.from().y - at.getY() > 2.0D / 16.0D && s.to().y - at.getY() > 2.0D / 16.0D && s.from().y < at.getY() + 0.5D;
			helper.assertTrue(raised, "No strand is raised over the other");
			helper.assertTrue(WireGeometry.crossings(helper.getLevel(), java.util.List.of(at)) == 1, "The solver no longer sees the crossing it must keep few");
		}).thenSucceed();
	}

	/**
	 * Every radio in the house and the antennas its coax reaches (2026-11-14, the maintainer: Jade said two antennas on
	 * a radio with only black coax to an eggbeater; grey coax in the same bundle and passthrough went to a VHF dipole).
	 * Logged for now, house positions.
	 */
	@GameTest(template = "house_cables", timeoutTicks = 200, required = false, batch = "cablehouse")
	public static void houseRadiosAndTheirAntennas(GameTestHelper helper) {
		helper.startSequence().thenIdle(20).thenExecute(() -> {
			BlockPos origin = helper.absolutePos(BlockPos.ZERO);
			net.minecraft.world.phys.AABB box = helper.getBounds();
			StringBuilder out = new StringBuilder("Radios in the house and their antennas:");
			for(BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
				if(!(com.arrl.radiocraft.api.capabilities.IBENetworks.getObject(helper.getLevel(), p) instanceof com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject radio))
					continue;
				out.append(System.lineSeparator()).append("  ").append(p.subtract(origin).toShortString()).append(' ')
						.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(helper.getLevel().getBlockState(p).getBlock()).getPath()).append(':');
				for(com.arrl.radiocraft.common.be_networks.network_objects.AntennaNetworkObject antenna : radio.getAntennas()) {
					BlockPos at = antenna.getPos();
					out.append(' ').append(at.subtract(origin).toShortString()).append('=')
							.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(helper.getLevel().getBlockState(at).getBlock()).getPath());
				}
			}
			Radiocraft.LOGGER.info(out.toString());
		}).thenSucceed();
	}

	private static final java.util.regex.Pattern POS = java.util.regex.Pattern.compile("^(-?\\d+), (-?\\d+), (-?\\d+)");

	/** WireGeometry.check's positions are absolute: to the house's. */
	private static String relative(String problem, BlockPos origin) {
		java.util.regex.Matcher m = POS.matcher(problem);
		if(!m.find())
			return problem;
		BlockPos p = new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))).subtract(origin);
		return p.toShortString() + problem.substring(m.end());
	}
}
