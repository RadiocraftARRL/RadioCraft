package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.computer.apps.FccHandler;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.CommercialLicenseItem;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Commercial frequency licenses (2026-11-10): claiming one, upgrading and copying it, and the FCC program's verdict. */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommercialLicenseGameTests {

	@GameTest(template = GameTestUtil.EMPTY)
	public static void claimingABlankGrantsAFreeFrequency(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RadiocraftItems.BLANK_VHF_LICENSE.get()));
		ItemStack result = RadiocraftItems.BLANK_VHF_LICENSE.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
		CommercialLicense license = CommercialLicenseItem.license(result);
		helper.assertTrue(license != null, "Right-clicking a blank license gave no license: " + result);
		helper.assertTrue(result.has(DataComponents.MAP_ID), "The license has no picture");
		helper.assertTrue(Band.VHF_COMMERCIAL.equals(license.band()), "Not a VHF license: " + license.band());
		helper.assertTrue(CommercialLicenseRules.grantable(Band.VHF_COMMERCIAL, license.outputHz()), "Not a grantable frequency: " + license.outputHz());
		helper.assertTrue(CommercialLicenseRules.isLicenseCallsign(license.callsign()), "Not a WQ callsign: " + license.callsign());
		helper.assertTrue(license.subscribers() == 10 && license.bases() == 0, "A new license is not 10 units, no bases");
		CommercialLicenses all = CommercialLicenses.of(helper.getLevel().getServer());
		helper.assertTrue(license.equals(all.get(license.callsign())), "The server does not know the license");
		helper.assertTrue(all.covering(license.outputHz()) != null, "The frequency is not taken");
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY)
	public static void upgradeAddsABaseAndAnInput(GameTestHelper helper) {
		CommercialLicense license = issue(helper);
		ItemStack item = CommercialLicenseItem.make(helper.getLevel(), license);
		ItemStack upgraded = craft(helper, item, new ItemStack(RadiocraftItems.COMMERCIAL_LICENSE_UPGRADE.get()));
		CommercialLicense up = CommercialLicenseItem.license(upgraded);
		helper.assertTrue(up != null && up.tier() == 1, "License + upgrade did not make a tier 1 license: " + upgraded);
		helper.assertTrue(!upgraded.has(DataComponents.MAP_ID), "The upgraded license keeps the old picture");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		upgraded.inventoryTick(helper.getLevel(), player, 0, false);
		up = CommercialLicenseItem.license(upgraded);
		helper.assertTrue(upgraded.has(DataComponents.MAP_ID), "The upgraded license was not drawn when carried");
		helper.assertTrue(up.inputHz() > 0 && CommercialLicenseRules.grantable(up.band(), up.inputHz()), "No repeater input after the first upgrade");
		helper.assertTrue(up.subscribers() == 20 && up.bases() == 1, "Tier 1 is not 20 units and 1 base");
		helper.assertTrue(CommercialLicenses.of(helper.getLevel().getServer()).get(up.callsign()).tier() == 1, "The server did not take the upgrade");
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY)
	public static void copiesAreStampedAndKeepTheOriginal(GameTestHelper helper) {
		CommercialLicense license = issue(helper);
		ItemStack item = CommercialLicenseItem.make(helper.getLevel(), license);
		List<ItemStack> grid = grid(item, new ItemStack(Items.MAP));
		CraftingInput input = CraftingInput.of(3, 3, grid);
		var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElse(null);
		helper.assertTrue(recipe != null, "License + empty map makes nothing");
		ItemStack copy = recipe.value().assemble(input, helper.getLevel().registryAccess());
		helper.assertTrue(CommercialLicenseItem.license(copy) != null && CommercialLicenseItem.license(copy).copy(), "The result is not a copy");
		helper.assertTrue(!CommercialLicenseItem.isOriginal(copy), "A copy counts as the original");
		helper.assertTrue(CommercialLicenseItem.isOriginal(recipe.value().getRemainingItems(input).get(0)), "The original is not given back");
		helper.assertTrue(craft(helper, copy, new ItemStack(RadiocraftItems.COMMERCIAL_LICENSE_UPGRADE.get())).isEmpty(), "A copy takes an upgrade");
		helper.succeed();
	}

	@GameTest(template = GameTestUtil.EMPTY)
	public static void fccVerdicts(GameTestHelper helper) {
		CommercialLicense license = issue(helper);
		CommercialLicense[] found = new CommercialLicense[1];
		var server = helper.getLevel().getServer();
		helper.assertTrue(FccHandler.verdict(server, license.outputHz(), found) == FccHandler.Verdict.LICENSED && found[0] != null
				&& found[0].callsign().equals(license.callsign()), "A licensed frequency is not LICENSED to its holder");
		float free = CommercialLicenseRules.pick(Band.LOW_BAND, new Random(5), hz -> CommercialLicenses.of(server).taken(hz));
		helper.assertTrue(FccHandler.verdict(server, free, new CommercialLicense[1]) == FccHandler.Verdict.UNLICENSED, "A free low band frequency is not UNLICENSED");
		helper.assertTrue(FccHandler.verdict(server, 146_520_000.0F, new CommercialLicense[1]) == FccHandler.Verdict.AMATEUR, "2m is not AMATEUR");
		helper.assertTrue(FccHandler.verdict(server, 156_800_000.0F, new CommercialLicense[1]) == FccHandler.Verdict.MARINE, "Channel 16 is not MARINE");
		CommercialLicenses.of(server).revoke(license.callsign());
		helper.assertTrue(FccHandler.verdict(server, license.outputHz(), new CommercialLicense[1]) == FccHandler.Verdict.UNLICENSED, "A revoked license still counts");
		helper.succeed();
	}

	private static CommercialLicense issue(GameTestHelper helper) {
		return CommercialLicenses.of(helper.getLevel().getServer()).issue(helper.getLevel().getServer(), Band.VHF_COMMERCIAL, "tester", "",
				0, new Random(helper.getLevel().random.nextLong()));
	}

	private static List<ItemStack> grid(ItemStack... stacks) {
		List<ItemStack> grid = new ArrayList<>(List.of(stacks));
		while(grid.size() < 9)
			grid.add(ItemStack.EMPTY);
		return grid;
	}

	private static ItemStack craft(GameTestHelper helper, ItemStack... stacks) {
		CraftingInput input = CraftingInput.of(3, 3, grid(stacks));
		return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(recipe -> recipe.value().assemble(input, helper.getLevel().registryAccess()))
				.orElse(ItemStack.EMPTY);
	}
}
