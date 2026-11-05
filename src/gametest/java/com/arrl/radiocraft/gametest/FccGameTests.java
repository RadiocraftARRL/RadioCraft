package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.commands.FccCommands;
import com.arrl.radiocraft.common.datacomponents.FccBadge;
import com.arrl.radiocraft.common.fcc.FccBadges;
import com.arrl.radiocraft.common.fcc.FccGear;
import com.arrl.radiocraft.common.init.RadiocraftArmorMaterials;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.FccBadgeItem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** FCC field gear (1.12 step 10): the hat and jacket, their recipes, the seal banner pattern, and the operators' badge. */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class FccGameTests {

	@GameTest(template = GameTestUtil.EMPTY)
	public static void hatAndJacketProtectLikeLeather(GameTestHelper helper) {
		ArmorItem hat = RadiocraftItems.FCC_HAT.get();
		ArmorItem jacket = RadiocraftItems.FCC_JACKET.get();
		helper.assertTrue(hat.getEquipmentSlot() == EquipmentSlot.HEAD, "The hat is not worn on the head");
		helper.assertTrue(jacket.getEquipmentSlot() == EquipmentSlot.CHEST, "The jacket is not worn on the chest");
		helper.assertTrue(hat.getDefense() == ((ArmorItem)Items.LEATHER_HELMET).getDefense(), "The hat does not protect like a leather cap: " + hat.getDefense());
		helper.assertTrue(jacket.getDefense() == ((ArmorItem)Items.LEATHER_CHESTPLATE).getDefense(), "The jacket does not protect like a leather tunic: " + jacket.getDefense());
		helper.assertTrue(new ItemStack(hat).getMaxDamage() == new ItemStack(Items.LEATHER_HELMET).getMaxDamage(), "The hat does not last like a leather cap");
		helper.assertTrue(hat.getMaterial().is(RadiocraftArmorMaterials.FCC.getKey()), "The hat is not of the FCC material");
		helper.assertTrue(new ItemStack(hat).is(net.minecraft.tags.ItemTags.HEAD_ARMOR_ENCHANTABLE), "The hat cannot take helmet enchantments");
		helper.assertTrue(new ItemStack(jacket).is(net.minecraft.tags.ItemTags.CHEST_ARMOR_ENCHANTABLE), "The jacket cannot take chestplate enchantments");
		helper.assertTrue(hat.isValidRepairItem(new ItemStack(hat), new ItemStack(Items.BROWN_WOOL)), "Brown wool does not mend the hat");
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY)
	public static void recipesMakeTheGear(GameTestHelper helper) {
		Item w = Items.BROWN_WOOL, a = Items.AIR;
		helper.assertTrue(craft(helper, w, w, w, w, Items.BLACK_DYE, w, a, a, a).is(RadiocraftItems.FCC_HAT.get()), "Brown wool and black dye make no hat");
		helper.assertTrue(craft(helper, w, a, w, w, Items.RED_DYE, w, w, w, w).is(RadiocraftItems.FCC_JACKET.get()), "Brown wool and red dye make no jacket");
		helper.assertTrue(craft(helper, Items.PAPER, Items.LIGHTNING_ROD, a, a, a, a, a, a, a).is(RadiocraftItems.FCC_BANNER_PATTERN.get()), "Paper and a lightning rod make no pattern");
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY)
	public static void sealPatternIsLoaded(GameTestHelper helper) {
		var patterns = helper.getLevel().registryAccess().registryOrThrow(Registries.BANNER_PATTERN);
		Holder<BannerPattern> seal = patterns.getHolder(FccGear.PATTERN).orElse(null);
		helper.assertTrue(seal != null, "The FCC seal banner pattern is not loaded");
		helper.assertTrue(seal.is(FccGear.PATTERN_ITEM), "The FCC Banner Pattern item does not put the seal on");
		helper.assertTrue(RadiocraftItems.FCC_BANNER_PATTERN.get().getBannerPattern().equals(FccGear.PATTERN_ITEM), "The pattern item names the wrong tag");

		ItemStack banner = FccGear.banner(helper.getLevel().registryAccess());
		helper.assertTrue(banner.is(Items.BLUE_BANNER), "The FCC banner is not a blue banner");
		BannerPatternLayers layers = banner.get(DataComponents.BANNER_PATTERNS);
		helper.assertTrue(layers != null && !layers.layers().isEmpty(), "The FCC banner has no patterns");
		helper.assertTrue(layers.layers().get(layers.layers().size() - 1).pattern().is(FccGear.PATTERN), "The FCC banner's top layer is not the seal");
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void onlyOperatorsIssueBadges(GameTestHelper helper) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("fcc-agent".getBytes()), "fcc-agent"));
		player.getInventory().clearContent();
		var commands = helper.getLevel().getServer().getCommands();
		commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(0).withSuppressedOutput(), "radiocraft fcc badge @s");
		helper.assertTrue(countBadges(player) == 0, "A player without operator rights issued a badge");

		commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(FccCommands.PERMISSION).withSuppressedOutput(), "radiocraft fcc badge @s");
		helper.assertTrue(countBadges(player) == 1, "An operator's /radiocraft fcc badge gave no badge");
		ItemStack badge = findBadge(player);
		FccBadge stamped = badge.get(RadiocraftDataComponent.FCC_BADGE.value());
		helper.assertTrue(stamped != null, "The issued badge is blank");
		helper.assertTrue("fcc-agent".equals(stamped.agent()) && player.getUUID().equals(stamped.agentId()), "The badge carries the wrong agent: " + stamped);
		helper.assertTrue(stamped.number() == FccBadges.numberFor(player.getUUID()), "The badge carries the wrong number");
		player.getInventory().clearContent();
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY)
	public static void showingABadgeSaysWhoseItIs(GameTestHelper helper) {
		FakePlayer agent = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("fcc-shower".getBytes()), "fcc-shower"));
		FakePlayer thief = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("fcc-thief".getBytes()), "fcc-thief"));
		FccBadge badge = FccGear.badgeFor(agent).get(RadiocraftDataComponent.FCC_BADGE.value());
		helper.assertTrue(key(FccBadgeItem.shownMessage(agent, badge)).endsWith("fcc_badge.shown"), "The agent's own badge reads as someone else's");
		helper.assertTrue(key(FccBadgeItem.shownMessage(thief, badge)).endsWith("fcc_badge.shown_other"), "A badge shown by another player does not say whose it is");
		helper.assertTrue(key(FccBadgeItem.shownMessage(thief, null)).endsWith("fcc_badge.shown_blank"), "A blank badge is not called blank");
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void badgeIsWornOnTheBodySlot(GameTestHelper helper) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("fcc-wearer".getBytes()), "fcc-wearer"));
		ItemStack badge = FccGear.badgeFor(player);
		helper.assertTrue(CuriosApi.getItemStackSlots(badge, helper.getLevel()).containsKey("body"), "The badge does not fit the Curios body (chest) slot");
		var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
		helper.assertTrue(curios.getStacksHandler("body").isPresent(), "Players have no body slot");
		curios.setEquippedCurio("body", 0, badge);
		helper.assertTrue(curios.findFirstCurio(RadiocraftItems.FCC_BADGE.get()).isPresent(), "The worn badge was not found");
		curios.setEquippedCurio("body", 0, ItemStack.EMPTY);
		helper.succeed();
	}

	/**
	 * Badge holders are marked on the moderation computer (maintainer, 2026-11-05): a player issued a badge is a holder
	 * from then on; one carrying or wearing their own badge is too; someone else's badge does not count.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void badgeHoldersAreKnown(GameTestHelper helper) {
		FakePlayer issued = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("fcc-holder".getBytes()), "fcc-holder"));
		FakePlayer carrier = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("fcc-carrier".getBytes()), "fcc-carrier"));
		FakePlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("fcc-stranger".getBytes()), "fcc-stranger"));
		issued.getInventory().clearContent();
		carrier.getInventory().clearContent();
		stranger.getInventory().clearContent();
		FccGear.issueBadge(issued);
		helper.assertTrue(com.arrl.radiocraft.common.fcc.FccBadgeHolders.holds(issued.getUUID()), "An agent issued a badge is not a holder");
		helper.assertFalse(com.arrl.radiocraft.common.fcc.FccBadgeHolders.holds(stranger.getUUID()), "A player never issued a badge is a holder");
		carrier.getInventory().add(FccGear.badgeFor(carrier));
		helper.assertTrue(com.arrl.radiocraft.common.fcc.FccBadgeHolders.carriesOwnBadge(carrier), "A player carrying their own badge does not hold it");
		stranger.getInventory().add(FccGear.badgeFor(carrier));
		helper.assertFalse(com.arrl.radiocraft.common.fcc.FccBadgeHolders.carriesOwnBadge(stranger), "Someone else's badge made a holder");
		issued.getInventory().clearContent();
		carrier.getInventory().clearContent();
		stranger.getInventory().clearContent();
		helper.succeed();
	}

	private static String key(Component message) {
		return message.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static int countBadges(FakePlayer player) {
		int count = 0;
		for(ItemStack stack : player.getInventory().items)
			if(stack.is(RadiocraftItems.FCC_BADGE.get()))
				count += stack.getCount();
		return count;
	}

	private static ItemStack findBadge(FakePlayer player) {
		for(ItemStack stack : player.getInventory().items)
			if(stack.is(RadiocraftItems.FCC_BADGE.get()))
				return stack;
		return ItemStack.EMPTY;
	}

	private static ItemStack craft(GameTestHelper helper, Item... grid) {
		List<ItemStack> items = Arrays.stream(grid).map(ItemStack::new).toList();
		CraftingInput input = CraftingInput.of(3, 3, items);
		return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(recipe -> recipe.value().assemble(input, helper.getLevel().registryAccess()))
				.orElse(ItemStack.EMPTY);
	}
}
