package com.arrl.radiocraft.common.worldgen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ham shack templates (1.12 step 8, written by {@code tools/ham_shack_templates.py}): the standalone site and the
 * five village houses each have the station (radio on 146.520 MHz but off, its J-pole, a flat battery, computer and monitor) and the
 * chest with the shack's loot; the village ones join a street by a doorstep jigsaw on their south edge, the site has
 * none. Reads the files as NBT; nothing of the game is started.
 */
class HamShackTemplatesTest {

	private static final Path STRUCTURES = projectRoot().resolve("src/main/resources/data/radiocraft/structure");

	private record Block(int x, int y, int z, String name, CompoundTag properties, CompoundTag nbt) {}

	private static List<Block> blocks(CompoundTag template) {
		ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
		ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
		List<Block> out = new ArrayList<>();
		for(int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompound(i);
			CompoundTag state = palette.getCompound(b.getInt("state"));
			int[] pos = { b.getList("pos", Tag.TAG_INT).getInt(0), b.getList("pos", Tag.TAG_INT).getInt(1), b.getList("pos", Tag.TAG_INT).getInt(2) };
			out.add(new Block(pos[0], pos[1], pos[2], state.getString("Name"), state.getCompound("Properties"), b.getCompound("nbt")));
		}
		return out;
	}

	private static CompoundTag read(String name) throws IOException {
		return NbtIo.readCompressed(STRUCTURES.resolve(name + ".nbt"), NbtAccounter.unlimitedHeap());
	}

	private static List<Block> named(List<Block> blocks, String name) {
		return blocks.stream().filter(b -> b.name().equals(name)).toList();
	}

	private static void hasTheStation(String name, List<Block> blocks) {
		List<Block> radios = named(blocks, "radiocraft:vhf_base_station");
		assertEquals(1, radios.size(), name + " radios");
		assertEquals(146.52e6F, radios.getFirst().nbt().getFloat("frequency"), 1.0F, name);
		assertFalse(radios.getFirst().nbt().getBoolean("wasPowered"), name + ": a found shack's radio is on");
		assertEquals(2, named(blocks, "radiocraft:j_pole_antenna").size(), name + ": J-pole halves");
		List<Block> batteries = named(blocks, "radiocraft:large_battery");
		assertEquals(1, batteries.size(), name);
		assertEquals(0, batteries.getFirst().nbt().getInt("energy"), name + ": a found shack's battery is charged");
		Block computer = named(blocks, "radiocraft:radio_computer").getFirst();
		Block monitor = named(blocks, "radiocraft:computer_monitor").getFirst();
		assertTrue(monitor.x() == computer.x() && monitor.z() == computer.z() && monitor.y() == computer.y() + 1, name + ": the monitor is not on the computer");
		List<Block> chests = named(blocks, "minecraft:chest");
		assertEquals(1, chests.size(), name);
		assertEquals("radiocraft:chests/ham_shack", chests.getFirst().nbt().getString("LootTable"), name);
		// The coax runs from the top of the radio to the side of the J-pole's foot.
		Block radio = radios.getFirst();
		assertTrue(blocks.stream().anyMatch(b -> b.name().equals("radiocraft:coax_wire") && b.x() == radio.x() && b.z() == radio.z() && b.y() == radio.y() + 1
				&& b.properties().getString("down").equals("true")), name + ": no coax on top of the radio");
	}

	@Test
	void theSiteHasTheStationAndNoJigsaw() throws IOException {
		CompoundTag template = read("sites/ham_shack");
		List<Block> blocks = blocks(template);
		hasTheStation("site", blocks);
		assertTrue(named(blocks, "minecraft:jigsaw").isEmpty());
		assertTrue(named(blocks, "minecraft:structure_void").isEmpty());
		ListTag size = template.getList("size", Tag.TAG_INT);
		assertEquals(List.of(11, 7, 9), List.of(size.getInt(0), size.getInt(1), size.getInt(2)));
	}

	@Test
	void eachVillageHouseJoinsAStreetAndHasAVillager() throws IOException {
		for(String type : List.of("plains", "taiga", "snowy", "savanna", "desert")) {
			CompoundTag template = read("village/ham_shack_" + type);
			List<Block> blocks = blocks(template);
			hasTheStation(type, blocks);
			int depth = template.getList("size", Tag.TAG_INT).getInt(2);
			List<Block> jigsaws = named(blocks, "minecraft:jigsaw");
			assertEquals(2, jigsaws.size(), type);
			Block door = jigsaws.stream().filter(j -> j.nbt().getString("name").equals("minecraft:building_entrance")).findFirst().orElseThrow();
			assertEquals(depth - 1, door.z(), type + ": the doorstep is not on the edge");
			assertEquals(0, door.y(), type);
			assertEquals("south_up", door.properties().getString("orientation"), type);
			Block villager = jigsaws.stream().filter(j -> j.nbt().getString("name").equals("minecraft:bottom")).findFirst().orElseThrow();
			assertEquals("minecraft:village/" + type + "/villagers", villager.nbt().getString("pool"), type);
			// The door in the wall is right behind the doorstep.
			assertTrue(blocks.stream().anyMatch(b -> b.name().endsWith("_door") && b.x() == door.x() && b.z() == door.z() - 1 && b.y() == 1), type + ": no door");
		}
	}

	private static Path projectRoot() {
		for(Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
			if(Files.isDirectory(dir.resolve("src/main/resources/assets/radiocraft")))
				return dir;
		return Path.of("");
	}
}
