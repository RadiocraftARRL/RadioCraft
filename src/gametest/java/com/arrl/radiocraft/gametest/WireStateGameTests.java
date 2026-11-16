package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.WireBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * The wire blocks' shared state neighbour lookup ({@code SharedStateNeighbours}, 2026-11-16): from every state of every
 * wire block, setting each property to each of its values gives the state with exactly those values, the same as the
 * vanilla table it replaced. (With FerriteCore installed the mixin is off and this checks FerriteCore's.)
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class WireStateGameTests {

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void everyWireStateFindsItsNeighbours(GameTestHelper helper) {
		int blocks = 0, checked = 0;
		for(Block block : BuiltInRegistries.BLOCK) {
			if(!(block instanceof WireBlock))
				continue;
			blocks++;
			for(BlockState state : block.getStateDefinition().getPossibleStates()) {
				for(Property<?> property : state.getProperties()) {
					for(Comparable<?> value : property.getPossibleValues()) {
						BlockState next = set(state, property, value);
						Map<Property<?>, Comparable<?>> expected = new HashMap<>(state.getValues());
						expected.put(property, value);
						if(next.getBlock() != block || !new HashMap<>(next.getValues()).equals(expected))
							throw new GameTestAssertException("Setting " + property.getName() + "=" + value + " on " + state + " gave " + next);
						if(trySet(state, property, value) != next)
							throw new GameTestAssertException("trySetValue " + property.getName() + "=" + value + " on " + state + " differs from setValue");
						checked++;
					}
				}
			}
		}
		if(blocks < 4)
			throw new GameTestAssertException("Only " + blocks + " wire blocks registered");
		helper.assertTrue(checked > 0, "Nothing checked");
		// Without FerriteCore the shared table must be the one in use (the mixin applied).
		if(!net.neoforged.fml.ModList.get().isLoaded("ferritecore")) {
			String table = neighbours(RadiocraftBlocks.COAX_WIRE.get().defaultBlockState()).getClass().getName();
			if(!table.endsWith("SharedStateNeighbours$Neighbours"))
				throw new GameTestAssertException("Wire states use " + table + ", not the shared table");
		}
		helper.succeed();
	}

	private static Object neighbours(BlockState state) {
		try {
			java.lang.reflect.Field field = StateHolder.class.getDeclaredField("neighbours");
			field.setAccessible(true);
			return field.get(state);
		} catch(ReflectiveOperationException e) {
			throw new GameTestAssertException("No neighbours field: " + e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState set(BlockState state, Property property, Comparable value) {
		return state.setValue(property, value);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState trySet(BlockState state, Property property, Comparable value) {
		return state.trySetValue(property, value);
	}
}
