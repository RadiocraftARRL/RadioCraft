package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The secret programs (1.12 step 8), server side: their four floppies install like any other, and they are found in
 * loot: the secret disk table always gives one of the four, the ham shack's chest sometimes has one, and the global
 * loot modifiers put them in vanilla chests now and then (and not in chests they do not name). The games, the music
 * and the Bible screen are client side: NeedsTesting "1.12 step 8".
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class SecretAppsGameTests {

	private static final BlockPos COMPUTER = new BlockPos(4, 2, 4);
	private static final List<ComputerApp> SECRET = List.of(ComputerApps.SNAKE, ComputerApps.FALLING_BLOCKS, ComputerApps.MUSIC, ComputerApps.BIBLE);

	private static Set<Item> secretDisks() {
		Set<Item> disks = new HashSet<>();
		for(ComputerApp app : SECRET)
			disks.add(RadiocraftItems.floppy(app));
		return disks;
	}

	private static List<ItemStack> roll(ServerLevel level, ResourceLocation table, BlockPos at) {
		LootTable loot = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, table));
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at)).create(LootContextParamSets.CHEST);
		return loot.getRandomItems(params);
	}

	private static int disks(List<ItemStack> stacks, Set<Item> disks) {
		int n = 0;
		for(ItemStack stack : stacks)
			if(disks.contains(stack.getItem()))
				n += stack.getCount();
		return n;
	}

	private static CompoundTag call(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, action, data, new ComputerReply(player, 7, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	/** Each secret disk goes in the drive and installs its program; all four together are in the shell. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void secretDisksInstall(GameTestHelper helper) {
		helper.setBlock(COMPUTER, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(COMPUTER.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, COMPUTER, RadioComputerBlockEntity.class);
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("secret-apps".getBytes()), "secret"));
		player.getInventory().clearContent();
		BlockPos at = helper.absolutePos(COMPUTER.south(2));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		for(ComputerApp app : SECRET) {
			helper.assertTrue(app.kind() == ComputerApp.Kind.SECRET, app.id() + " is not secret");
			player.getInventory().setItem(0, new ItemStack(RadiocraftItems.floppy(app)));
			CompoundTag slot = new CompoundTag();
			slot.putInt("slot", 0);
			CompoundTag inserted = call(computer, player, "sys.insert", slot);
			helper.assertFalse(inserted.contains("error"), "insert " + app.id() + ": " + inserted);
			CompoundTag which = new CompoundTag();
			which.putString("app", app.id());
			CompoundTag installed = call(computer, player, "sys.install", which);
			helper.assertFalse(installed.contains("error"), "install " + app.id() + ": " + installed);
			helper.assertFalse(call(computer, player, "sys.eject", new CompoundTag()).contains("error"), "eject " + app.id());
			helper.assertTrue(computer.isInstalled(app), app.id() + " is not installed");
		}
		helper.assertTrue(computer.availableApps().containsAll(SECRET), "not all four in the shell: " + computer.availableApps());
		helper.assertTrue(ComputerApps.FALLING_BLOCKS.fileName().equals("PILEUP.EXE"), "the falling-blocks game is " + ComputerApps.FALLING_BLOCKS.fileName());
		helper.succeed();
	}

	/** The secret disk table gives exactly one of the four every time, and every one of them turns up. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void secretDiskTableGivesOneOfFour(GameTestHelper helper) {
		Set<Item> disks = secretDisks(), seen = new HashSet<>();
		for(int i = 0; i < 64; i++) {
			List<ItemStack> stacks = roll(helper.getLevel(), Radiocraft.id("chests/secret_floppies"), helper.absolutePos(COMPUTER));
			helper.assertTrue(stacks.size() == 1 && disks(stacks, disks) == 1, "roll " + i + ": " + stacks);
			seen.add(stacks.getFirst().getItem());
		}
		helper.assertTrue(seen.size() == 4, "only " + seen + " in 64 rolls");
		helper.succeed();
	}

	/** The ham shack's chest has radio parts every time and a secret disk now and then (40%). */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void hamShackChestSometimesHasADisk(GameTestHelper helper) {
		Set<Item> disks = secretDisks();
		int with = 0;
		for(int i = 0; i < 200; i++) {
			List<ItemStack> stacks = roll(helper.getLevel(), Radiocraft.id("chests/ham_shack"), helper.absolutePos(COMPUTER));
			helper.assertTrue(stacks.size() >= 3, "a ham shack chest with " + stacks);
			if(disks(stacks, disks) > 0)
				with++;
		}
		helper.assertTrue(with >= 40 && with <= 130, with + " of 200 ham shack chests had a secret disk (40% expected)");
		helper.succeed();
	}

	/** The loot modifiers put disks in vanilla chests they name, now and then, and in no others. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void vanillaChestsSometimesHaveADisk(GameTestHelper helper) {
		Set<Item> disks = secretDisks();
		int ancient = 0, nether = 0;
		for(int i = 0; i < 400; i++) {
			ancient += disks(roll(helper.getLevel(), ResourceLocation.withDefaultNamespace("chests/ancient_city"), helper.absolutePos(COMPUTER)), disks);
			nether += disks(roll(helper.getLevel(), ResourceLocation.withDefaultNamespace("chests/nether_bridge"), helper.absolutePos(COMPUTER)), disks);
		}
		helper.assertTrue(ancient >= 15 && ancient <= 90, ancient + " disks in 400 ancient city chests (12% expected)");
		helper.assertTrue(nether == 0, nether + " disks in nether fortress chests, which no modifier names");
		helper.succeed();
	}
}
