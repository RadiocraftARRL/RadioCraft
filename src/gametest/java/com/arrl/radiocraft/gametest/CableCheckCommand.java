package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.Strand;
import com.arrl.radiocraft.common.blocks.WireGeometry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /cablecheck <from> <to>} (dev runs only: it lives with the GameTests, 2026-11-14): the cable matrix's checks
 * ({@link WireGeometry#check}, its stub and overlap checks, {@link CableShapes}) on every cable in a box of a real
 * world, for a player's build that looks wrong. Problems, and the centre lines of each block they name, go to
 * {@code cablecheck.txt} in the game folder; the count to chat. {@code /cablecheck <centre> <radius>} checks a cube.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public class CableCheckCommand {

	private static final Pattern POS = Pattern.compile("(-?\\d+), (-?\\d+), (-?\\d+)");

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		// /cablecheck save <name> <from> <to>: the box as a GameTest structure (src/gametest/resources), a player's build kept
		// as a test (CableHouseGameTests).
		event.getDispatcher().register(Commands.literal("cablecheck").requires(s -> s.hasPermission(2))
				.then(Commands.literal("save").then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
						.then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos())
								.executes(c -> save(c.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name"),
										BlockPosArgument.getLoadedBlockPos(c, "from"), BlockPosArgument.getLoadedBlockPos(c, "to"))))))));
		event.getDispatcher().register(Commands.literal("cablecheck").requires(s -> s.hasPermission(2))
				.then(Commands.argument("from", BlockPosArgument.blockPos())
						.then(Commands.argument("to", BlockPosArgument.blockPos())
								.executes(c -> run(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "from"), BlockPosArgument.getLoadedBlockPos(c, "to"))))
						.then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
								.executes(c -> {
									BlockPos centre = BlockPosArgument.getLoadedBlockPos(c, "from");
									int r = IntegerArgumentType.getInteger(c, "radius");
									return run(c.getSource(), centre.offset(-r, -r, -r), centre.offset(r, r, r));
								}))));
	}

	/** Each block the problems name (relative to {@code origin}): what it is, what is round it, its strands' centre lines. */
	static List<String> describeNamed(ServerLevel level, java.util.Collection<String> problems, BlockPos origin) {
		List<String> out = new ArrayList<>();
		Set<BlockPos> named = new LinkedHashSet<>();
		for(String problem : problems) {
			Matcher m = POS.matcher(problem);
			if(m.find())
				named.add(new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
		}
		for(BlockPos rel : named) {
			BlockPos p = rel.offset(origin);
			out.add("");
			out.add(rel.toShortString() + " " + level.getBlockState(p).getBlock().getName().getString());
			StringBuilder around = new StringBuilder("  around:");
			for(net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
				net.minecraft.world.level.block.state.BlockState n = level.getBlockState(p.relative(d));
				around.append(' ').append(d.getName()).append('=').append(n.isAir() ? "air" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(n.getBlock()).getPath());
				if(n.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING))
					around.append('(').append(n.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getName()).append(')');
			}
			out.add(around.toString());
			for(String line : WireGeometry.describe(level, p))
				out.add("  " + line);
		}
		return out;
	}

	private static int save(CommandSourceStack source, String name, BlockPos a, BlockPos b) {
		BlockPos lo = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		BlockPos hi = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
		template.fillFromWorld(source.getLevel(), lo, hi.subtract(lo).offset(1, 1, 1), false, null);
		Path file = Path.of(System.getProperty("radiocraft.structureOut", "../../src/gametest/resources/data/radiocraft/structure")).resolve(name + ".nbt");
		try {
			Files.createDirectories(file.getParent());
			net.minecraft.nbt.NbtIo.writeCompressed(template.save(new net.minecraft.nbt.CompoundTag()), file);
		}
		catch(IOException e) {
			source.sendFailure(Component.literal("Could not write " + file + ": " + e.getMessage()));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Saved " + lo.toShortString() + " to " + hi.toShortString() + " as " + file.toAbsolutePath().normalize()), false);
		return 1;
	}

	private static int run(CommandSourceStack source, BlockPos a, BlockPos b) {
		ServerLevel level = source.getLevel();
		BlockPos lo = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		BlockPos hi = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		Set<String> problems = new LinkedHashSet<>(WireGeometry.check(level, lo, hi));
		problems.addAll(CableMatrixGameTests.overlaps(level, lo, hi, BlockPos.ZERO, Set.of()));
		problems.addAll(CableMatrixGameTests.stubs(level, lo, hi, BlockPos.ZERO));
		problems.addAll(CableShapes.check(level, lo, hi, BlockPos.ZERO, null, null));
		int cables = 0;
		for(BlockPos p : BlockPos.betweenClosed(lo, hi))
			if(!Strand.at(level, p).isEmpty())
				cables++;
		// Each problem, then the centre lines of every block named.
		List<String> out = new ArrayList<>();
		out.add("Cable check " + lo.toShortString() + " to " + hi.toShortString() + ": " + cables + " cable blocks, " + problems.size() + " problems");
		out.addAll(describeNamed(level, problems, BlockPos.ZERO));
		Path file = source.getServer().getServerDirectory().resolve("cablecheck.txt");
		try {
			Files.write(file, out, StandardCharsets.UTF_8);
		}
		catch(IOException e) {
			source.sendFailure(Component.literal("Could not write " + file + ": " + e.getMessage()));
		}
		int found = problems.size(), blocks = cables;
		source.sendSuccess(() -> Component.literal(blocks + " cable blocks, " + found + " problems: " + file.toAbsolutePath()), false);
		return found;
	}
}
