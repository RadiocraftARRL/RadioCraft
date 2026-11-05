package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.capabilities.IVHFHandheldCapability;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.capabilities.RadiocraftCapabilities;
import com.arrl.radiocraft.common.crafting.MarsModInstallRecipe;
import com.arrl.radiocraft.common.init.RadiocraftDataComponent;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.MarsModItem;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.bandplan.BandPlan;
import com.arrl.radiocraft.common.radio.solar.SolarEventManager;
import com.arrl.radiocraft.common.radio.voice.handheld.PlayerRadio;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static com.arrl.radiocraft.gametest.TransmissionGameTests.*;

/**
 * 1.12 step 1: radios tune past their band but only transmit there with a MARS mod; out-of-band signals splatter
 * twice as far; the commercial bands and the band plan. The radio tests run in a batch of their own below the 20m
 * band (13.92-13.98 MHz), each 30 kHz from the next.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class BandEdgeGameTests {

	private static final String BATCH = "band_edges";
	private static final int F_REFUSED = -20; // 13.980 MHz
	private static final int F_MARS = -50; // 13.950 MHz
	private static final int F_SPLATTER = -80; // 13.920 MHz
	private static final UUID OPERATOR = UUID.nameUUIDFromBytes("gametest-band-edges".getBytes());

	private static short[] tone() {
		short[] audio = new short[960];
		for(int i = 0; i < audio.length; i++)
			audio[i] = (short)(Math.sin(2 * Math.PI * 440 * i / 48000.0D) * 8000);
		return audio;
	}

	/** Keyed out of band without a MARS mod, the radio refuses: nothing goes out, and the display reads OUT OF BAND. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void outOfBandNeedsAMarsMod(GameTestHelper helper) {
		stationsUp(helper)
				.thenExecute(() -> {
					setupSsb(helper, RADIO_A, F_REFUSED);
					setupSsb(helper, RADIO_B, F_REFUSED);
					HFRadioBlockEntity a = radio(helper, RADIO_A);
					helper.assertTrue(a.getFrequency() < a.getBand().minFrequency(), "The dial should go below 20m, was " + a.getFrequency());
					helper.assertTrue(a.isOutOfBand() && a.isTransmitInhibited(), "Out of band without a MARS mod should inhibit transmit");
					a.setPTTDown(true);
					helper.assertFalse(a.canTransmitVoice(), "Keyed out of band without a MARS mod");
					a.acceptVoicePacket(() -> helper.getLevel(), tone(), OPERATOR);
					a.setPTTDown(false);
					assertHeardNothing(helper, RADIO_B);
					a.setSSBEnabled(false);
					a.setCWEnabled(true);
					helper.assertFalse(a.canTransmitCW(), "CW out of band without a MARS mod");
				})
				.thenSucceed();
	}

	/** With a MARS mod the same radio transmits there, and it is heard like any other signal. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void marsModTransmitsOutOfBand(GameTestHelper helper) {
		stationsUp(helper)
				.thenExecute(() -> {
					setupSsb(helper, RADIO_A, F_MARS);
					setupSsb(helper, RADIO_B, F_MARS);
					radio(helper, RADIO_A).setMarsMod(true);
					helper.assertFalse(radio(helper, RADIO_A).isTransmitInhibited(), "A MARS mod should lift the inhibit");
					speakInto(helper, RADIO_A);
					assertHeard(helper, RADIO_B, EXPECTED_VOICE_STRENGTH);
				})
				.thenSucceed();
	}

	/** Out of band, the signal still splatters 15 kHz away, where an in-band one is long gone. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 300, batch = BATCH)
	public static void outOfBandSplattersTwiceAsFar(GameTestHelper helper) {
		stationsUp(helper)
				.thenExecute(() -> {
					setupSsb(helper, RADIO_A, F_SPLATTER);
					setupSsb(helper, RADIO_B, F_SPLATTER + 15);
					radio(helper, RADIO_A).setMarsMod(true);
					helper.assertTrue(radio(helper, RADIO_B).getDetuning(radio(helper, RADIO_A).getFrequency()).isNone(), "In band, 15 kHz off is clear");
					speakInto(helper, RADIO_A);
					assertHeardNothing(helper, RADIO_B);
					HFRadioBlockEntity b = radio(helper, RADIO_B);
					helper.assertTrue(b.getAdjacentQrmGameTime() == helper.getLevel().getGameTime() && b.getAdjacentQrm() > 0.0D,
							"The out-of-band signal 15 kHz off should splatter, was " + b.getAdjacentQrm());
				})
				.thenSucceed();
	}

	/** A radio placed from a modded item keeps the mod, and drops with it. */
	@GameTest(template = GameTestUtil.EMPTY)
	public static void marsModSurvivesPlacingAndBreaking(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, com.arrl.radiocraft.common.init.RadiocraftBlocks.HF_RADIO_20M.get());
		HFRadioBlockEntity radio = GameTestUtil.blockEntity(helper, pos, HFRadioBlockEntity.class);
		helper.assertFalse(radio.hasMarsMod(), "A fresh radio has no MARS mod");
		ItemStack modded = new ItemStack(RadiocraftItems.HF_RADIO_20M.get());
		modded.set(RadiocraftDataComponent.MARS_MOD.value(), net.minecraft.util.Unit.INSTANCE);
		radio.applyComponentsFromItemStack(modded);
		helper.assertTrue(radio.hasMarsMod(), "Placed from a modded item, the radio should have the mod");
		DataComponentMap components = radio.collectComponents();
		helper.assertTrue(components.has(RadiocraftDataComponent.MARS_MOD.value()), "Broken, the radio should drop with the mod");
		helper.succeed();
	}

	/** Crafting a radio with a MARS mod keeps everything on the radio; receivers and modded radios do not match. */
	@GameTest(template = GameTestUtil.EMPTY)
	public static void marsModCraftingKeepsTheRadio(GameTestHelper helper) {
		MarsModInstallRecipe recipe = new MarsModInstallRecipe(CraftingBookCategory.MISC);
		ItemStack handheld = new ItemStack(RadiocraftItems.VHF_HANDHELD.get());
		handheld.set(RadiocraftDataComponent.RADIO_ENERGY.value(), 1234);
		ItemStack mod = new ItemStack(RadiocraftItems.MARS_MOD.get());
		CraftingInput input = CraftingInput.of(2, 1, List.of(handheld, mod));
		helper.assertTrue(recipe.matches(input, helper.getLevel()), "Handheld + MARS mod should match");
		ItemStack out = recipe.assemble(input, helper.getLevel().registryAccess());
		helper.assertTrue(MarsModItem.isFitted(out), "The result should have the mod");
		helper.assertTrue(Integer.valueOf(1234).equals(out.get(RadiocraftDataComponent.RADIO_ENERGY.value())), "The charge should be kept");
		helper.assertTrue(out.has(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value()), "The radio's state should be kept");
		helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(out, mod.copy())), helper.getLevel()), "A modded radio takes no second mod");
		helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(new ItemStack(RadiocraftItems.HF_RECEIVER.get()), mod.copy())), helper.getLevel()),
				"A receiver takes no MARS mod");
		helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(new ItemStack(RadiocraftItems.VHF_REPEATER.get()), mod.copy())), helper.getLevel()),
				"A repeater takes no MARS mod");
		helper.assertTrue(recipe.matches(CraftingInput.of(2, 1, List.of(new ItemStack(RadiocraftItems.HF_RADIO_20M.get()), mod.copy())), helper.getLevel()),
				"An HF radio takes a MARS mod");
		helper.succeed();
	}

	/** A handheld tunes up to 174 MHz but transmits outside 2m only with a MARS mod. */
	@GameTest(template = GameTestUtil.EMPTY)
	public static void handheldTunesCommercialVhfButNeedsAMarsModToTransmit(GameTestHelper helper) {
		ItemStack handheld = new ItemStack(RadiocraftItems.VHF_HANDHELD.get());
		IVHFHandheldCapability cap = handheld.getCapability(RadiocraftCapabilities.VHF_HANDHELDS);
		helper.assertTrue(cap != null, "No handheld capability");
		cap.setFrequencyHertz(155_000_000.0F);
		helper.assertTrue(cap.getFrequencyHertz() == 155_000_000.0F, "The handheld should tune 155 MHz, was " + cap.getFrequencyHertz());
		cap.setFrequencyHertz(180_000_000.0F);
		helper.assertTrue(cap.getFrequencyHertz() == 174_000_000.0F, "The handheld should stop at 174 MHz, was " + cap.getFrequencyHertz());
		helper.assertFalse(PlayerRadio.handheldMayTransmit(handheld, 155_000_000.0F), "155 MHz without a MARS mod");
		helper.assertTrue(PlayerRadio.handheldMayTransmit(handheld, 146_520_000.0F), "146.520 is in 2m");
		helper.assertTrue(PlayerRadio.handheldInhibited(handheld, cap), "Tuned to 174 MHz without a mod, the handheld should show OUT OF BAND");
		handheld.set(RadiocraftDataComponent.MARS_MOD.value(), net.minecraft.util.Unit.INSTANCE);
		helper.assertTrue(PlayerRadio.handheldMayTransmit(handheld, 155_000_000.0F), "155 MHz with a MARS mod");
		helper.succeed();
	}

	/** The commercial bands are in the band table, found by frequency, and low band follows 10m's solar weather. */
	@GameTest(template = GameTestUtil.EMPTY)
	public static void commercialBandsAreConfigured(GameTestHelper helper) {
		Band low = Band.getBand(40_000_000.0F), vhf = Band.getBand(155_000_000.0F);
		helper.assertTrue(low != null && low.name().equals(Band.LOW_BAND), "40 MHz should be low band, was " + low);
		helper.assertTrue(vhf != null && vhf.name().equals(Band.VHF_COMMERCIAL), "155 MHz should be commercial VHF, was " + vhf);
		helper.assertTrue(vhf.isVhf() && !low.isVhf(), "Commercial VHF is VHF; low band is on the HF network");
		Band near = Band.getBand(13_950_000.0F);
		helper.assertTrue(near != null && near.name().equals("20m"), "13.950 MHz (below 20m) should map to 20m, was " + near);
		helper.assertTrue(Band.getBand(100_000_000.0F) == null, "100 MHz is in no band");
		helper.assertTrue(SolarEventManager.getCondition(helper.getLevel(), low) == SolarEventManager.getCondition(helper.getLevel(), Band.getBand("10m")),
				"Low band should follow 10m's solar weather");
		helper.succeed();
	}

	/** The band plan comes from the server config: the defaults, and an admin's edit shows up. */
	@GameTest(template = GameTestUtil.EMPTY, batch = "band_plan")
	public static void bandPlanFollowsTheServerConfig(GameTestHelper helper) {
		BandPlan plan = BandPlan.current();
		helper.assertTrue(plan.segments().size() == BandPlan.DEFAULT_SEGMENTS.size(), "Default segments: " + plan.segments().size());
		helper.assertTrue(!plan.markersNear(144_390_000.0D, 1.0D).isEmpty(), "144.390 APRS marker");
		List<? extends String> before = RadiocraftServerConfig.BAND_PLAN_MARKERS.get();
		try {
			RadiocraftServerConfig.BAND_PLAN_MARKERS.set(List.of("7.123|Test net"));
			BandPlan edited = BandPlan.current();
			helper.assertTrue(edited.markers().size() == 1 && edited.markers().getFirst().label().equals("Test net"), "The edited markers: " + edited.markers());
		}
		finally {
			RadiocraftServerConfig.BAND_PLAN_MARKERS.set(before);
		}
		helper.assertTrue(BandPlan.current().markers().size() == BandPlan.DEFAULT_MARKERS.size(), "Markers should be back to the defaults");
		List<BandPlan.Row> rows = BandPlan.current().rows(BandPlan.sheetBands());
		helper.assertTrue(rows.size() == 7, "Seven bands on the sheet, was " + rows.size());
		helper.succeed();
	}
}
