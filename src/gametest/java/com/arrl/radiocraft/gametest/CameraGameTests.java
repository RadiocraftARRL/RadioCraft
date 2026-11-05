package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.datacomponents.PhotoInfo;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.CameraItem;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.photo.PhotoLedger;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoAlbums;
import com.arrl.radiocraft.common.photo.PhotoImage;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.arrl.radiocraft.common.photo.PhotoPrints;
import com.arrl.radiocraft.common.photo.PhotoUpload;
import com.arrl.radiocraft.common.photo.PhotoUploads;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.data.FaxRates;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The camera and photo paper (1.12 step 4), server side: the shutter opens an upload only with paper, the picture's
 * chunks print a locked map photo on one sheet, black and white paper comes out grey whatever the client sent, stray
 * chunks do nothing, and a photo is a map to the radio fax. The client's frame grab is in NeedsTesting. Recipes need
 * {@code ./gradlew runData}.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class CameraGameTests {

	/** A fake player (its own per test: fake players are cached by name) with a camera in hand and nothing else. */
	private static FakePlayer photographer(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("camera-" + name).getBytes()), name));
		player.getInventory().clearContent();
		player.setPos(helper.absoluteVec(new Vec3(4.5D, 2.0D, 4.5D)));
		player.getCooldowns().removeCooldown(RadiocraftItems.CAMERA.get()); // Fake players never tick their cooldowns down.
		PhotoUploads.close(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RadiocraftItems.CAMERA.get()));
		return player;
	}

	/** Raises the camera (use) and lets go (the shutter): true if the shutter fired. */
	private static boolean shutter(GameTestHelper helper, FakePlayer player) {
		InteractionResult aim = player.getMainHandItem().getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
		boolean fired = aim.consumesAction() && CameraItem.fireShutter(player, InteractionHand.MAIN_HAND);
		player.stopUsingItem();
		return fired;
	}

	/** Every map colour in turn: a picture with plenty of colour in it. */
	private static byte[] rainbow() {
		PhotoPalette palette = MapPalettes.vanilla();
		byte[] pixels = new byte[PhotoImage.PIXELS];
		for(int i = 0; i < pixels.length; i++)
			pixels[i] = palette.id((i / 3) % palette.size());
		return pixels;
	}

	/** Sends {@code pixels} as the client would; the last chunk's result (the photo, or null). */
	private static ItemStack upload(FakePlayer player, int nonce, byte[] pixels) {
		ItemStack last = null;
		for(int index = 0; index < PhotoUpload.CHUNKS; index++)
			last = PhotoUploads.accept(player, nonce, index, PhotoUpload.chunk(pixels, index));
		return last;
	}

	private static int count(FakePlayer player, Item item) {
		int n = 0;
		for(int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
			if(player.getInventory().getItem(slot).is(item))
				n += player.getInventory().getItem(slot).getCount();
		return n;
	}

	/** Shutter, picture, print: a locked map with the picture exactly, one sheet of colour paper used, the film advanced. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void cameraPrintsAColourPhoto(GameTestHelper helper) {
		FakePlayer player = photographer(helper, "colour");
		player.getInventory().setItem(5, new ItemStack(RadiocraftItems.COLOR_PHOTO_PAPER.get(), 3));
		helper.assertTrue(shutter(helper, player), "the shutter did not fire");
		int nonce = PhotoUploads.openNonce(player);
		helper.assertTrue(nonce != 0, "no upload was opened");
		helper.assertTrue(player.getCooldowns().isOnCooldown(RadiocraftItems.CAMERA.get()), "the film did not advance");
		helper.assertFalse(shutter(helper, player), "a second photo before the film advanced");

		byte[] pixels = rainbow();
		ItemStack photo = upload(player, nonce, pixels);
		helper.assertTrue(photo != null && photo.is(Items.FILLED_MAP), "no photo printed");
		MapItemSavedData data = helper.getLevel().getMapData(Objects.requireNonNull(photo.get(DataComponents.MAP_ID)));
		helper.assertTrue(data != null && data.locked, "the photo's map is not locked");
		helper.assertTrue(Arrays.equals(pixels, PhotoPrints.pixels(helper.getLevel(), photo)), "the photo is not the picture sent");
		PhotoInfo info = PhotoPrints.info(photo);
		helper.assertTrue(info != null && info.colour() && info.photographer().equals("colour"), "photo info " + info);
		helper.assertTrue(count(player, RadiocraftItems.COLOR_PHOTO_PAPER.get()) == 2, "used " + (3 - count(player, RadiocraftItems.COLOR_PHOTO_PAPER.get())) + " sheets");
		helper.assertTrue(count(player, Items.FILLED_MAP) == 1, "the photo is not in the inventory");
		helper.assertTrue(PhotoUploads.openNonce(player) == 0, "the upload stayed open");
		helper.succeed();
	}

	/** Black and white paper (in the other hand, so it goes first) prints greys, whatever colours the client sent. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void blackAndWhitePaperPrintsGreys(GameTestHelper helper) {
		FakePlayer player = photographer(helper, "bw");
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(RadiocraftItems.PHOTO_PAPER.get(), 2));
		player.getInventory().setItem(5, new ItemStack(RadiocraftItems.COLOR_PHOTO_PAPER.get(), 2));
		shutter(helper, player);
		ItemStack photo = upload(player, PhotoUploads.openNonce(player), rainbow());
		helper.assertTrue(photo != null, "no photo printed");
		byte[] printed = PhotoPrints.pixels(helper.getLevel(), photo);
		helper.assertTrue(printed != null, "no picture");
		PhotoPalette greys = MapPalettes.greys();
		for(byte id : printed)
			helper.assertTrue(greys.contains(id), "a B&W photo has map colour " + (id & 0xFF));
		PhotoInfo info = PhotoPrints.info(photo);
		helper.assertTrue(info != null && !info.colour(), "photo info " + info);
		helper.assertTrue(player.getOffhandItem().getCount() == 1, "B&W paper in the other hand was not used");
		helper.assertTrue(count(player, RadiocraftItems.COLOR_PHOTO_PAPER.get()) == 2, "colour paper was used");
		helper.succeed();
	}

	/** Without photo paper the shutter does not fire, and no upload opens. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void noPaperNoPhoto(GameTestHelper helper) {
		FakePlayer player = photographer(helper, "nopaper");
		player.getInventory().setItem(3, new ItemStack(Items.PAPER, 16));
		helper.assertFalse(shutter(helper, player), "the shutter fired with no photo paper");
		helper.assertTrue(PhotoUploads.openNonce(player) == 0, "an upload opened with no photo paper");
		helper.assertFalse(player.getCooldowns().isOnCooldown(RadiocraftItems.CAMERA.get()), "the film advanced with no photo");
		helper.succeed();
	}

	/**
	 * Chunks nobody asked for print nothing: none before the shutter, none with the wrong nonce, no repeats; and paper
	 * gone by the time the picture arrives means no photo.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void strayChunksPrintNothing(GameTestHelper helper) {
		FakePlayer player = photographer(helper, "stray");
		byte[] pixels = rainbow();
		helper.assertTrue(upload(player, 12345, pixels) == null, "a photo without the shutter");
		player.getInventory().setItem(5, new ItemStack(RadiocraftItems.COLOR_PHOTO_PAPER.get(), 1));
		shutter(helper, player);
		int nonce = PhotoUploads.openNonce(player);
		helper.assertTrue(upload(player, nonce + 1, pixels) == null, "a photo with the wrong nonce");
		helper.assertTrue(PhotoUploads.accept(player, nonce, 0, PhotoUpload.chunk(pixels, 0)) == null, "one chunk made a photo");
		helper.assertTrue(PhotoUploads.accept(player, nonce, 0, PhotoUpload.chunk(pixels, 0)) == null, "a repeated chunk made a photo");
		helper.assertTrue(PhotoUploads.accept(player, nonce, 1, new byte[5]) == null, "a short chunk made a photo");
		player.getInventory().clearContent(); // The paper is dropped before the rest arrives.
		ItemStack last = null;
		for(int index = 1; index < PhotoUpload.CHUNKS; index++)
			last = PhotoUploads.accept(player, nonce, index, PhotoUpload.chunk(pixels, index));
		helper.assertTrue(last == null, "a photo with no paper left");
		helper.assertTrue(count(player, Items.FILLED_MAP) == 0, "something was printed");
		helper.succeed();
	}

	/** To the radio fax a photo is a map: sent and copied whole (the copy keeps the map and the photo info), as fast. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void photoIsAMapToTheFax(GameTestHelper helper) {
		ItemStack photo = PhotoPrints.photo(helper.getLevel(), rainbow(), new PhotoInfo("W1AW", false, PhotoInfo.CAMERA), helper.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4)));
		helper.assertTrue(FileTransfer.canSend(photo), "the fax will not send a photo");
		helper.assertTrue(FileTransfer.blankFor(photo).is(Items.MAP), "a photo prints on " + FileTransfer.blankFor(photo));
		ItemStack copy = FileTransfer.copy(photo);
		helper.assertTrue(copy != null && Objects.equals(copy.get(DataComponents.MAP_ID), photo.get(DataComponents.MAP_ID)) && PhotoPrints.isPhoto(copy), "the copy is " + copy);
		ItemStack plainMap = new ItemStack(Items.FILLED_MAP);
		Band twoMetres = Band.getBand("2m");
		helper.assertTrue(FaxRates.totalTicks(photo, twoMetres) == FaxRates.totalTicks(plainMap, twoMetres), "a photo takes longer than a map");
		helper.assertTrue(FileTransfer.frameCount(photo) == FileTransfer.frameCount(plainMap), "a photo's SSTV is not a map's");
		helper.succeed();
	}

	/**
	 * With {@code camera_photo_cap} at 2, the third photo is refused until the player deletes one; deleting blanks the
	 * photo's map. Creative players are not limited.
	 */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void photoCapAndDeleting(GameTestHelper helper) {
		int before = RadiocraftServerConfig.CAMERA_PHOTO_CAP.get();
		try {
			RadiocraftServerConfig.CAMERA_PHOTO_CAP.set(2);
			FakePlayer player = photographer(helper, "capped");
			PhotoLedger ledger = PhotoLedger.get(helper.getLevel().getServer());
			for(PhotoAlbums.Photo old : ledger.albums().photos(player.getUUID())) // From an earlier run in this world.
				ledger.delete(helper.getLevel(), player.getUUID(), old.mapId());
			player.getInventory().setItem(5, new ItemStack(RadiocraftItems.COLOR_PHOTO_PAPER.get(), 8));
			ItemStack first = null;
			for(int i = 0; i < 2; i++) {
				player.getCooldowns().removeCooldown(RadiocraftItems.CAMERA.get());
				helper.assertTrue(shutter(helper, player), "photo " + (i + 1) + " refused under the cap");
				ItemStack photo = upload(player, PhotoUploads.openNonce(player), rainbow());
				helper.assertTrue(photo != null, "photo " + (i + 1) + " not printed");
				if(first == null)
					first = photo;
			}
			helper.assertTrue(ledger.albums().count(player.getUUID()) == 2, "album has " + ledger.albums().count(player.getUUID()));
			player.getCooldowns().removeCooldown(RadiocraftItems.CAMERA.get());
			helper.assertFalse(shutter(helper, player), "a third photo over the cap");

			int firstId = Objects.requireNonNull(first.get(DataComponents.MAP_ID)).id();
			helper.assertTrue(ledger.delete(helper.getLevel(), player.getUUID(), firstId), "could not delete the first photo");
			MapItemSavedData data = helper.getLevel().getMapData(Objects.requireNonNull(first.get(DataComponents.MAP_ID)));
			helper.assertTrue(data != null && PhotoLedger.isBlank(data), "the deleted photo still shows");
			helper.assertFalse(ledger.delete(helper.getLevel(), UUID.randomUUID(), firstId), "someone else deleted it");
			player.getCooldowns().removeCooldown(RadiocraftItems.CAMERA.get());
			helper.assertTrue(shutter(helper, player), "no photo after deleting one");

			FakePlayer creative = photographer(helper, "capped-creative");
			creative.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			for(int i = 0; i < 3; i++) {
				creative.getCooldowns().removeCooldown(RadiocraftItems.CAMERA.get());
				helper.assertTrue(shutter(helper, creative), "creative photo " + (i + 1) + " refused");
				helper.assertTrue(upload(creative, PhotoUploads.openNonce(creative), rainbow()) != null, "creative photo " + (i + 1) + " not printed");
			}
			creative.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		}
		finally {
			RadiocraftServerConfig.CAMERA_PHOTO_CAP.set(before);
		}
		helper.succeed();
	}

	/** Eight paper round an empty map make sixteen B&W sheets; red, green and blue dye as the bottom row make colour. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void photoPaperRecipes(GameTestHelper helper) {
		Item p = Items.PAPER, m = Items.MAP;
		ItemStack bw = craft(helper, List.of(p, p, p, p, m, p, p, p, p));
		helper.assertTrue(bw.is(RadiocraftItems.PHOTO_PAPER.get()) && bw.getCount() == 16, "B&W paper recipe made " + bw);
		ItemStack colour = craft(helper, List.of(p, p, p, p, m, p, Items.RED_DYE, Items.GREEN_DYE, Items.BLUE_DYE));
		helper.assertTrue(colour.is(RadiocraftItems.COLOR_PHOTO_PAPER.get()) && colour.getCount() == 16, "colour paper recipe made " + colour);
		helper.succeed();
	}

	private static ItemStack craft(GameTestHelper helper, List<Item> items) {
		CraftingInput input = CraftingInput.of(3, 3, items.stream().map(ItemStack::new).toList());
		return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(recipe -> recipe.value().assemble(input, helper.getLevel().registryAccess()))
				.orElse(ItemStack.EMPTY);
	}
}
