package com.arrl.radiocraft.datagen;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.crafting.H19ScreenGlassIngredient;
import com.arrl.radiocraft.common.init.CrystalVariants;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.radio.crystal.CrystalBand;
import com.arrl.radiocraft.common.radio.crystal.CrystalQuality;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class RadiocraftRecipesProvider extends RecipeProvider {
    public RadiocraftRecipesProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput recipeOutput) {
        // Maintainer's recipes, 2026-10-26.
        // The HF manpack: a 20m QRP rig and a 10m radio in a bundle on a leather chestplate.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.HF_MANPACK.get())
                .requires(Items.BUNDLE)
                .requires(Items.LEATHER_CHESTPLATE)
                .requires(RadiocraftItems.QRP_RADIO_20M.get())
                .requires(Ingredient.of(RadiocraftItems.HF_RADIO_10M_VARIANTS.pure().get(), RadiocraftItems.HF_RADIO_10M_VARIANTS.good().get(), RadiocraftItems.HF_RADIO_10M_VARIANTS.poor().get()))
                .unlockedBy("has_qrp_radio_20m", has(RadiocraftItems.QRP_RADIO_20M.get()))
                .save(recipeOutput, Radiocraft.id("hf_manpack"));
        // The direction finder: an HF and a VHF scanner, a clock and an amethyst crystal.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.DIRECTION_FINDER.get())
                .requires(RadiocraftItems.HF_RECEIVER.get())
                .requires(RadiocraftItems.VHF_RECEIVER.get())
                .requires(Items.CLOCK)
                .requires(Items.AMETHYST_SHARD)
                .unlockedBy("has_hf_receiver", has(RadiocraftItems.HF_RECEIVER.get()))
                .save(recipeOutput, Radiocraft.id("direction_finder"));
        // Its module: a compass and a pure crystal of any band, in iron.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.DIRECTION_FINDER_MODULE.get(), 1)
                .pattern("III")
                .pattern("ICI")
                .pattern("IXI")
                .define('I', Items.IRON_INGOT)
                .define('C', Items.COMPASS)
                .define('X', Ingredient.of(pureCrystals()))
                .unlockedBy("has_compass", has(Items.COMPASS))
                .save(recipeOutput, Radiocraft.id("direction_finder_module"));
        // The handheld direction finder: a multiband (all-band) HF board and a compass, in iron.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.HANDHELD_DIRECTION_FINDER.get(), 1)
                .pattern("III")
                .pattern("IBI")
                .pattern("ICI")
                .define('I', Items.IRON_INGOT)
                .define('B', RadiocraftItems.ALL_BAND_CIRCUIT_BOARD.get())
                .define('C', Items.COMPASS)
                .unlockedBy("has_all_band_circuit_board", has(RadiocraftItems.ALL_BAND_CIRCUIT_BOARD.get()))
                .save(recipeOutput, Radiocraft.id("handheld_direction_finder"));
        // The small microwave dish. The large horn has no recipe: it only turns up in structures.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.SATELLITE_DISH.get(), 1)
                .pattern("III")
                .pattern("IC ")
                .pattern("IWI")
                .define('I', Items.IRON_INGOT)
                .define('C', Items.COPPER_INGOT)
                .define('W', RadiocraftItems.WIRE.get())
                .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                .save(recipeOutput, Radiocraft.id("microwave_dish"));

        // The microwave radio (maintainer's, mid-game): gold waveguides, a pure VHF crystal as the frequency reference,
        // an ender pearl for the beam, coax and an iron chassis.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.MICROWAVE_RADIO.get(), 1)
                .pattern("GXG")
                .pattern("CEC")
                .pattern("III")
                .define('G', Items.GOLD_INGOT)
                .define('X', RadiocraftItems.ENCASED_CRYSTALS.get(com.arrl.radiocraft.common.radio.crystal.CrystalBand.VHF).get(com.arrl.radiocraft.common.radio.crystal.CrystalQuality.PURE).get())
                .define('C', RadiocraftItems.COAX_WIRE.get())
                .define('E', Items.ENDER_PEARL)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
                .save(recipeOutput, Radiocraft.id("microwave_radio"));
        // Desk speakers, one per wood: that wood's planks, wool grille cloth, a speaker and a nugget terminal strip.
        RadiocraftItems.DESK_SPEAKERS.forEach((wood, item) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, item.get(), 1)
                .pattern("PWP")
                .pattern("PSP")
                .pattern("PNP")
                .define('P', net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(wood + "_planks")))
                .define('W', ItemTags.WOOL)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('N', Items.IRON_NUGGET)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput, Radiocraft.id("desk_speaker_" + wood)));
        // The large speaker: two drivers (woofer and tweeter) behind a big wool grille, chrome feet.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.LARGE_SPEAKER.get(), 1)
                .pattern("PWP")
                .pattern("SWS")
                .pattern("PIP")
                .define('P', ItemTags.PLANKS)
                .define('W', ItemTags.WOOL)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput, Radiocraft.id("large_speaker"));
        // Premium speakers: two drivers and a copper crossover in a case of marble (calcite), iron or gold.
        premiumSpeaker(recipeOutput, RadiocraftItems.PREMIUM_SPEAKER_MARBLE.get(), Items.CALCITE, "marble");
        premiumSpeaker(recipeOutput, RadiocraftItems.PREMIUM_SPEAKER_IRON.get(), Items.IRON_INGOT, "iron");
        premiumSpeaker(recipeOutput, RadiocraftItems.PREMIUM_SPEAKER_GOLD.get(), Items.GOLD_INGOT, "gold");

        // The telephone: a carbon capsule for the handset, redstone for the ringer and dial, a terracotta case.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.TELEPHONE.get(), 1)
                .pattern(" M ")
                .pattern("PRP")
                .pattern("PPP")
                .define('M', RadiocraftItems.MICROPHONE_ELEMENT.get())
                .define('R', Items.REDSTONE)
                .define('P', Items.TERRACOTTA)
                .unlockedBy("has_microphone_element", has(RadiocraftItems.MICROPHONE_ELEMENT.get()))
                .save(recipeOutput, Radiocraft.id("telephone"));
        // The TA-43 field phone: a carbon capsule for the handset, a note block for the bell, the line's binding posts
        // (data wire), in an olive drab iron case.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.FIELD_PHONE.get(), 1)
                .pattern(" M ")
                .pattern("GNG")
                .pattern("IDI")
                .define('M', RadiocraftItems.MICROPHONE_ELEMENT.get())
                .define('G', Items.GREEN_DYE)
                .define('N', Items.NOTE_BLOCK)
                .define('I', Items.IRON_INGOT)
                .define('D', RadiocraftItems.DATA_WIRE.get())
                .unlockedBy("has_microphone_element", has(RadiocraftItems.MICROPHONE_ELEMENT.get()))
                .save(recipeOutput, Radiocraft.id("field_phone"));
        // The Phone PA: a phone's works and a radio speaker's output, in an iron case.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.PHONE_PA.get(), 1)
                .pattern("III")
                .pattern("DTD")
                .pattern("ISI")
                .define('I', Items.IRON_INGOT)
                .define('D', RadiocraftItems.DATA_WIRE.get())
                .define('T', RadiocraftItems.TELEPHONE.get())
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .unlockedBy("has_telephone", has(RadiocraftItems.TELEPHONE.get()))
                .save(recipeOutput, Radiocraft.id("phone_pa"));
        // The phone PABX: a comparator for the switching, data wire for the lines, a redstone ringer, in an iron case.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.PABX.get(), 1)
                .pattern("III")
                .pattern("DCD")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('D', RadiocraftItems.DATA_WIRE.get())
                .define('C', Items.COMPARATOR)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_telephone", has(RadiocraftItems.TELEPHONE.get()))
                .save(recipeOutput, Radiocraft.id("pabx"));
        // The repeater autopatch: the PABX's, with a DTMF decoder in place of the ringer to hear its codes off the air.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.REPEATER_AUTOPATCH.get(), 1)
                .pattern("III")
                .pattern("DCD")
                .pattern("IXI")
                .define('I', Items.IRON_INGOT)
                .define('D', RadiocraftItems.DATA_WIRE.get())
                .define('C', Items.COMPARATOR)
                .define('X', RadiocraftItems.DTMF_DECODER.get())
                .unlockedBy("has_dtmf_decoder", has(RadiocraftItems.DTMF_DECODER.get()))
                .save(recipeOutput, Radiocraft.id("repeater_autopatch"));
        // The DTMF encoder: a row of keys on a tone board, wired to the radio (maintainer, 2026-11-04).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.DTMF_ENCODER.get(), 1)
                .pattern("UUU")
                .pattern("WBW")
                .define('U', Items.STONE_BUTTON)
                .define('W', RadiocraftItems.WIRE.get())
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get()))
                .save(recipeOutput);
        // The DTMF decoder: the tone board feeding a comparator (the code match), redstone torches its lamps and output.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.DTMF_DECODER.get(), 1)
                .pattern("WBW")
                .pattern("RCR")
                .define('W', RadiocraftItems.WIRE.get())
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .define('R', Items.REDSTONE_TORCH)
                .define('C', Items.COMPARATOR)
                .unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get()))
                .save(recipeOutput);
        // The weather station: copper cups, a quartz radiation shield, a comparator for the electronics, a bars mast mount.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.WEATHER_STATION.get(), 1)
                .pattern("C C")
                .pattern("QRQ")
                .pattern(" B ")
                .define('C', Items.COPPER_INGOT)
                .define('Q', Items.SMOOTH_QUARTZ_SLAB)
                .define('R', Items.COMPARATOR)
                .define('B', Items.IRON_BARS)
                .unlockedBy("has_comparator", has(Items.COMPARATOR))
                .save(recipeOutput, Radiocraft.id("weather_station"));
        // Wall speakers, two at a time: an iron frame, wool grille cloth, a speaker and a nugget terminal strip.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.WALL_SPEAKER.get(), 2)
                .pattern("IWI")
                .pattern("WSW")
                .pattern("INI")
                .define('I', Items.IRON_INGOT)
                .define('W', ItemTags.WOOL)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('N', Items.IRON_NUGGET)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput, Radiocraft.id("wall_speaker"));
        // The loudspeaker: a cone of iron round a radio speaker.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.LOUDSPEAKER.get(), 1)
                .pattern("III")
                .pattern("ISI")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput, Radiocraft.id("loudspeaker"));
        // The tape player: a wooden case, an iron capstan, two speakers either side of the redstone works.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.TAPE_PLAYER.get(), 1)
                .pattern("PIP")
                .pattern("SRS")
                .pattern("PPP")
                .define('P', ItemTags.PLANKS)
                .define('I', Items.IRON_INGOT)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput, Radiocraft.id("tape_player"));
        // A blank tape reel (they do not stack): nugget hubs, kelp tape on a paper label.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.TAPE_REEL.get(), 1)
                .pattern("NKN")
                .pattern("KPK")
                .pattern("NKN")
                .define('N', Items.IRON_NUGGET)
                .define('K', Items.DRIED_KELP)
                .define('P', Items.PAPER)
                .unlockedBy("has_dried_kelp", has(Items.DRIED_KELP))
                .save(recipeOutput, Radiocraft.id("tape_reel"));

        // A direction finder with the world map module in its drive plots fixes on a map.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.MAP_DIRECTION_FINDER.get())
                .requires(RadiocraftItems.DIRECTION_FINDER.get())
                .requires(RadiocraftItems.WORLD_MAP_MODULE.get())
                .unlockedBy("has_world_map_module", has(RadiocraftItems.WORLD_MAP_MODULE.get()))
                .save(recipeOutput, Radiocraft.id("map_direction_finder"));
        // Basic components
        buildWireRecipe(recipeOutput);
        buildWaterproofWireRecipe(recipeOutput);
        buildDataWireRecipe(recipeOutput);
        buildAntennaSplitterRecipe(recipeOutput);
        buildAntennaSwitchRecipe(recipeOutput);
        buildInsulationRecipes(recipeOutput);
        buildAntennaPoleRecipe(recipeOutput);
        buildTowerFootholdRecipe(recipeOutput);
        buildAntennaWireRecipe(recipeOutput);
        buildFerriteCore(recipeOutput);
        buildLargeBatteryRecipe(recipeOutput);
        buildCoaxialCoreRecipe(recipeOutput);
        buildMicrophoneRecipe(recipeOutput);
        buildSolarPanelRecipe(recipeOutput);
        buildEncasedCrystalRecipes(recipeOutput);
        buildRadioSpeakerRecipe(recipeOutput);
        buildCoaxWireRecipe(recipeOutput);
        buildDyedCoaxRecipes(recipeOutput);
        buildDyedDataWireRecipes(recipeOutput);
        buildDyedWireRecipes(recipeOutput);
        buildDuplexerRecipe(recipeOutput);
        buildSmallBatteryRecipe(recipeOutput);
        buildRadioInterfaceRecipe(recipeOutput);
        buildParrotControllerRecipe(recipeOutput);
        buildHandMicrophoneRecipe(recipeOutput);
        buildMicrophoneElementRecipe(recipeOutput);
        buildAntennaToolsRecipe(recipeOutput);
        buildAntennaAnalyzerRecipe(recipeOutput);
        buildHfCircuitBoardRecipe(recipeOutput);
        buildAntennaConnectorRecipe(recipeOutput);
        buildBalunRecipes(recipeOutput);
        buildDummyLoadRecipe(recipeOutput);
        buildVhfAntennaRecipes(recipeOutput);
        buildPowerDeviceRecipes(recipeOutput);
        buildPassthroughRecipe(recipeOutput);

        // Receivers and radios
        buildHfReceiverRecipe(recipeOutput);
        buildVhfReceiverRecipe(recipeOutput);
        buildVhfBaseStationRecipe(recipeOutput);
        buildSolarWeatherStationRecipe(recipeOutput);
        buildHfRadio10mRecipe(recipeOutput);
        buildHfRadio20mRecipe(recipeOutput);
        buildHfRadio40mRecipe(recipeOutput);
        buildHfRadio80mRecipe(recipeOutput);
        buildVhfRepeaterRecipe(recipeOutput);
        buildVhfHandheldRecipe(recipeOutput);
        buildCommercialRadioRecipes(recipeOutput);
        buildMarineRadioRecipes(recipeOutput);
        buildCbRadioRecipes(recipeOutput);
        buildQrpRadioRecipes(recipeOutput);

        // All band radio variants (shapeless)
        buildAllBandRadioRecipes(recipeOutput);

        // Late additions: station and antenna gear (recipe review, 2026-10-23)
        buildFiveBtvRecipe(recipeOutput);
        buildAutoTunerRecipe(recipeOutput);
        buildAprsTerminalRecipe(recipeOutput);
        buildRadioFaxRecipes(recipeOutput);
        buildIonosondeRecipes(recipeOutput);
        buildWefaxReceiverRecipe(recipeOutput);
    }

    /** The Hustler 5-BTV: aluminium tubing (copper here), four traps for 10/15/20/40m and a coax feed. Late game. */
    private void buildFiveBtvRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.FIVE_BTV_ANTENNA.get(), 1)
                .pattern("LCL")
                .pattern("FCF")
                .pattern("FOF")
                .define('L', Items.LIGHTNING_ROD) // The top whip sections.
                .define('C', Items.COPPER_BLOCK) // The tubing.
                .define('F', RadiocraftItems.FERRITE_CORE.get()) // The traps.
                .define('O', RadiocraftItems.COAX_WIRE.get())
                .unlockedBy("has_ferrite_core", has(RadiocraftItems.FERRITE_CORE.get()))
                .save(recipeOutput);
    }

    /**
     * The automatic tuner: a manual tuner's network switched by relays (repeaters), an SWR bridge (comparators), and a
     * controller on two boards clocked by a pure crystal of any band. Late game.
     */
    private void buildAutoTunerRecipe(RecipeOutput recipeOutput) {
        Item[] pureCrystals = Arrays.stream(CrystalBand.values())
                .map(band -> RadiocraftItems.ENCASED_CRYSTALS.get(band).get(CrystalQuality.PURE).get())
                .toArray(Item[]::new);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.AUTO_TUNER.get(), 1)
                .pattern("PCP")
                .pattern("BTB")
                .pattern("IXI")
                .define('P', Items.REPEATER)
                .define('C', Items.COMPARATOR)
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .define('T', RadiocraftItems.ANTENNA_TUNER.get())
                .define('I', Items.IRON_INGOT)
                .define('X', Ingredient.of(pureCrystals))
                .unlockedBy("has_antenna_tuner", has(RadiocraftItems.ANTENNA_TUNER.get()))
                .save(recipeOutput);
    }

    /**
     * The H19: a CRT (tinted glass, or stained glass too where the server allows it, see
     * {@link H19ScreenGlassIngredient}), a logic board and a keyboard of buttons.
     */
    private void buildAprsTerminalRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.APRS_TERMINAL.get(), 1)
                .pattern("ITI")
                .pattern("IBI")
                .pattern("UWU")
                .define('I', Items.IRON_INGOT)
                .define('T', H19ScreenGlassIngredient.INSTANCE.toVanilla())
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .define('U', Items.STONE_BUTTON) // The keys.
                .define('W', RadiocraftItems.WIRE.get())
                .unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get()))
                .save(recipeOutput);
    }

    /** The radio fax, one per log: that log's case round a modem board, scanner glass on top and paper in the tray. */
    private void buildRadioFaxRecipes(RecipeOutput recipeOutput) {
        RadiocraftItems.RADIO_FAXES.forEach((wood, item) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, item.get(), 1)
                .pattern("LGL")
                .pattern("WBW")
                .pattern("LPL")
                .define('L', BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(logName(wood))))
                .define('G', Items.GLASS_PANE)
                .define('W', RadiocraftItems.WIRE.get())
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .define('P', Items.PAPER)
                .unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get()))
                .save(recipeOutput, Radiocraft.id("radio_fax_" + wood)));
    }

    /** The vanilla log item of a wood: stems in the nether, the bamboo block. */
    private static String logName(String wood) {
        return switch(wood) {
            case "crimson", "warped" -> wood + "_stem";
            case "bamboo" -> "bamboo_block";
            default -> wood + "_log";
        };
    }

    /**
     * The ionosonde antenna (copper and antenna wire, fed by coax) and its processor: valves (redstone torches in
     * glass), a board, amethyst for the sounder's oscillator and a clock for the sweep.
     */
    private void buildIonosondeRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.IONOSONDE.get(), 1)
                .pattern("LAL")
                .pattern("CAC")
                .pattern("COC")
                .define('L', Items.LIGHTNING_ROD)
                .define('A', RadiocraftItems.ANTENNA_WIRE.get())
                .define('C', Items.COPPER_INGOT)
                .define('O', RadiocraftItems.COAX_WIRE.get())
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.IONOSPHERIC_PROCESSOR.get(), 1)
                .pattern("RGR")
                .pattern("ABA")
                .pattern("IKI")
                .define('R', Items.REDSTONE_TORCH) // The valves.
                .define('G', Items.GLASS)
                .define('A', Items.AMETHYST_SHARD)
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .define('I', Items.IRON_INGOT)
                .define('K', Items.CLOCK)
                .unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get()))
                .save(recipeOutput);
    }

    /** The WEFAX receiver: a board driving a paper drum, timed by a clock (real ones turn at 120 rpm). */
    private void buildWefaxReceiverRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.WEFAX_RECEIVER.get(), 1)
                .pattern("IPI")
                .pattern("WBW")
                .pattern("IKI")
                .define('I', Items.IRON_INGOT)
                .define('P', Items.PAPER)
                .define('W', RadiocraftItems.WIRE.get())
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .define('K', Items.CLOCK)
                .unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get()))
                .save(recipeOutput);
    }

    private void buildWireRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.WIRE.get(), 9)
                .pattern("#  ")
                .pattern(" # ")
                .pattern("  #")
                .define('#', Items.COPPER_INGOT)
                .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                .save(recipeOutput);
    }

    private void buildWaterproofWireRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.WATERPROOF_WIRE.get(), 3)
                .pattern("KKK")
                .pattern("###")
                .define('#', RadiocraftItems.WIRE.get())
                .define('K', RadiocraftItems.INSULATION.get())
                .unlockedBy("has_insulation", has(RadiocraftItems.INSULATION.get()))
                .save(recipeOutput);
    }

    /** The antenna switch (maintainer, 2026-10-30): the splitter's box with a lever for the knob. */
    private void buildAntennaSwitchRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ANTENNA_SWITCH.get(), 1)
                .pattern(" C ")
                .pattern("CIC")
                .pattern(" L ")
                .define('C', RadiocraftItems.COAX_WIRE.get())
                .define('I', Items.IRON_INGOT)
                .define('L', Items.LEVER)
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
    }

    /** The receive-only antenna splitter (maintainer, 2026-10-30): a metal box with a coax port on each side. */
    private void buildAntennaSplitterRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ANTENNA_SPLITTER.get(), 1)
                .pattern(" C ")
                .pattern("CIC")
                .pattern(" C ")
                .define('C', RadiocraftItems.COAX_WIRE.get())
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
    }

    /** Data wire (maintainer, 2026-10-30): 3 wire and 6 insulation make 16. */
    private void buildDataWireRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.DATA_WIRE.get(), 16)
                .pattern("KKK")
                .pattern("###")
                .pattern("KKK")
                .define('#', RadiocraftItems.WIRE.get())
                .define('K', RadiocraftItems.INSULATION.get())
                .unlockedBy("has_insulation", has(RadiocraftItems.INSULATION.get()))
                .save(recipeOutput);
    }

    /** Insulation, the mod's rubber, three ways: the better the material, the more a craft gives. */
    private void buildInsulationRecipes(RecipeOutput recipeOutput) {
        insulation(recipeOutput, Ingredient.of(Items.DRIED_KELP), Items.STRING, 6, "kelp", has(Items.DRIED_KELP));
        insulation(recipeOutput, Ingredient.of(Items.LEATHER), Items.STRING, 8, "leather", has(Items.LEATHER));
        insulation(recipeOutput, Ingredient.of(ItemTags.WOOL), Items.SLIME_BALL, 16, "slime", has(Items.SLIME_BALL));
    }

    /** {@code material} plus {@code binder} gives {@code count} insulation. */
    private void insulation(RecipeOutput recipeOutput, Ingredient material, ItemLike binder, int count, String from, Criterion<InventoryChangeTrigger.TriggerInstance> unlock) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.INSULATION.get(), count)
                .requires(material)
                .requires(binder)
                .unlockedBy("has_" + from, unlock)
                .save(recipeOutput, Radiocraft.id("insulation_from_" + from));
    }

    private void buildTowerFootholdRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.TOWER_FOOTHOLD.get(), 3)
                .pattern("I ")
                .pattern("IS")
                .pattern("I ")
                .define('I', Items.IRON_INGOT)
                .define('S', Items.STICK)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(recipeOutput);
        // Lightning arrestor: coax in and out either side of a
        // lightning rod (the gas tube and ground lug), in an iron case.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.LIGHTNING_ARRESTOR.get(), 1)
                .pattern(" I ")
                .pattern("CRC")
                .pattern(" I ")
                .define('I', Items.IRON_INGOT)
                .define('C', RadiocraftItems.COAX_WIRE.get())
                .define('R', Items.LIGHTNING_ROD)
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
        // Ground rod: copper-clad steel, as real rods are. Two at a time.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.GROUND_ROD.get(), 2)
                .pattern("C")
                .pattern("I")
                .pattern("C")
                .define('C', Items.COPPER_INGOT)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                .save(recipeOutput);
        // Battery meter: a comparator behind a glass face, wire
        // through it, in an iron case.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.BATTERY_METER.get(), 1)
                .pattern("IGI")
                .pattern("WCW")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS_PANE)
                .define('W', RadiocraftItems.WIRE.get())
                .define('C', Items.COMPARATOR)
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
        // Wire junction (maintainer, 2026-10-28): an iron box round a piece of plain wire, two at a time.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.WIRE_JUNCTION.get(), 2)
                .pattern(" I ")
                .pattern("IWI")
                .pattern(" I ")
                .define('I', Items.IRON_INGOT)
                .define('W', RadiocraftItems.WIRE.get())
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
        // Power switches: a lever on an iron body, a piece of
        // wire for each terminal. The Left/Right has a third terminal, so one more wire.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.POWER_SWITCH_ON_OFF.get(), 1)
                .pattern(" L ")
                .pattern("WIW")
                .define('L', Items.LEVER)
                .define('I', Items.IRON_INGOT)
                .define('W', RadiocraftItems.WIRE.get())
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.POWER_SWITCH_LEFT_RIGHT.get(), 1)
                .pattern(" L ")
                .pattern("WIW")
                .pattern(" W ")
                .define('L', Items.LEVER)
                .define('I', Items.IRON_INGOT)
                .define('W', RadiocraftItems.WIRE.get())
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
        // Stable Chemical Battery: an iron case of reserve cells,
        // blaze powder and glowstone for the chemistry, and redstone for the trigger that breaks the seal.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.STABLE_CHEMICAL_BATTERY.get(), 1)
                .pattern("IRI")
                .pattern("GBG")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .define('G', Items.GLOWSTONE_DUST)
                .define('B', Items.BLAZE_POWDER)
                .unlockedBy("has_blaze_powder", has(Items.BLAZE_POWDER))
                .save(recipeOutput);
        // Tower beams, all iron, four at a time: an X of lattice round a heavier core.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.TOWER_BEAM_THIN.get(), 4)
                .pattern("N N")
                .pattern("NIN")
                .pattern("N N")
                .define('N', Items.IRON_NUGGET)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.TOWER_BEAM.get(), 4)
                .pattern("I I")
                .pattern(" I ")
                .pattern("I I")
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.TOWER_BEAM_THICK.get(), 4)
                .pattern("I I")
                .pattern(" B ")
                .pattern("I I")
                .define('I', Items.IRON_INGOT)
                .define('B', Items.IRON_BLOCK)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(recipeOutput);
        // Tower beacon (maintainer, 2026-10-28): a redstone lamp and torch on a tower beam of any size.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.TOWER_BEACON.get())
                .requires(Items.REDSTONE_TORCH)
                .requires(Ingredient.of(RadiocraftItems.TOWER_BEAM_THIN.get(), RadiocraftItems.TOWER_BEAM.get(), RadiocraftItems.TOWER_BEAM_THICK.get()))
                .requires(Items.REDSTONE_LAMP)
                .unlockedBy("has_redstone_lamp", has(Items.REDSTONE_LAMP))
                .save(recipeOutput);
    }

    private void buildAntennaPoleRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ANTENNA_POLE.get(), 3)
                .pattern(" # ")
                .pattern(" # ")
                .pattern(" W ")
                .define('#', Items.IRON_INGOT)
                .define('W', RadiocraftItems.WIRE.get())
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
    }

    private void buildAntennaWireRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ANTENNA_WIRE.get(), 1)
                .pattern("K# ")
                .pattern("K# ")
                .pattern("K# ")
                .define('#', RadiocraftItems.WIRE.get())
                .define('K', RadiocraftItems.INSULATION.get())
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
    }

    private void buildFerriteCore(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.FERRITE_CORE.get(), 2)
                .pattern(" K ")
                .pattern("KIK")
                .pattern(" K ")
                .define('I', Items.IRON_INGOT)
                .define('K', Items.CLAY_BALL) // Ferrite is iron oxide fired like a ceramic.
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(recipeOutput);
    }

    private void buildLargeBatteryRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.LARGE_BATTERY.get(), 1)
                .pattern("LSL")
                .pattern("LSL")
                .pattern("LSL")
                .define('L', ItemTags.PLANKS) // The crate.
                .define('S', RadiocraftItems.SMALL_BATTERY.get())
                .unlockedBy("has_small_battery", has(RadiocraftItems.SMALL_BATTERY.get()))
                .save(recipeOutput);
    }

    private void buildCoaxialCoreRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.COAXIAL_CORE.get(), 6)
                .pattern("KKK")
                .pattern("CCC")
                .pattern("KKK")
                .define('K', RadiocraftItems.INSULATION.get()) // The dielectric around the centre conductor.
                .define('C', Items.COPPER_INGOT)
                .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                .save(recipeOutput);
    }

    private void buildMicrophoneRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.MICROPHONE.get(), 1)
                .pattern(" E ")
                .pattern(" D ")
                .pattern(" C ")
                .define('C', RadiocraftItems.WIRE.get())
                .define('D', Items.IRON_INGOT)
                .define('E', RadiocraftItems.MICROPHONE_ELEMENT.get())
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
    }

    private void buildSolarPanelRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.SOLAR_PANEL.get(), 1)
                .pattern("GGG")
                .pattern("LLL")
                .pattern("I#I")
                .define('#', RadiocraftItems.WIRE.get())
                .define('G', Items.GLASS)
                .define('L', Items.LAPIS_LAZULI) // The cells.
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
    }

    private void buildEncasedCrystalRecipes(RecipeOutput recipeOutput) {
        for(CrystalBand band : CrystalBand.values())
            for(CrystalQuality quality : CrystalQuality.values()) {
                Item crystal = RadiocraftItems.CRYSTALS.get(band).get(quality).get();
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.ENCASED_CRYSTALS.get(band).get(quality).get())
                        .requires(crystal)
                        .requires(Items.IRON_INGOT)
                        .unlockedBy("has_crystal", has(crystal))
                        .save(recipeOutput);
            }
    }

    private void buildRadioSpeakerRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.RADIO_SPEAKER.get(), 1)
                .pattern(" I ")
                .pattern("PAP")
                .pattern("P#P")
                .define('#', RadiocraftItems.WIRE.get())
                .define('P', ItemTags.PLANKS)
                .define('A', Items.NOTE_BLOCK)
                .define('I', Items.IRON_INGOT) // The magnet.
                .unlockedBy("has_note_block", has(Items.NOTE_BLOCK))
                .save(recipeOutput);
    }

    /** Eight wire round a dye: eight of that colour, which joins only its own colour and plain wire. */
    private void buildDyedWireRecipes(RecipeOutput recipeOutput) {
        RadiocraftItems.COLOURED_WIRE.forEach((colour, wire) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, wire.get(), 8)
                .pattern("WWW")
                .pattern("WDW")
                .pattern("WWW")
                .define('W', RadiocraftItems.WIRE.get())
                .define('D', net.minecraft.world.item.DyeItem.byColor(colour))
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput, com.arrl.radiocraft.Radiocraft.id("wire_" + colour.getSerializedName())));
    }

    /** Eight data wire round a dye: eight of that colour, which joins only its own colour. */
    private void buildDyedDataWireRecipes(RecipeOutput recipeOutput) {
        RadiocraftItems.COLOURED_DATA_WIRE.forEach((colour, wire) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, wire.get(), 8)
                .pattern("WWW")
                .pattern("WDW")
                .pattern("WWW")
                .define('W', RadiocraftItems.DATA_WIRE.get())
                .define('D', net.minecraft.world.item.DyeItem.byColor(colour))
                .unlockedBy("has_data_wire", has(RadiocraftItems.DATA_WIRE.get()))
                .save(recipeOutput, com.arrl.radiocraft.Radiocraft.id("data_wire_" + colour.getSerializedName())));
    }

    /** Eight coax round a dye: eight of that colour, which joins only its own colour and plain coax. */
    private void buildDyedCoaxRecipes(RecipeOutput recipeOutput) {
        RadiocraftItems.COLOURED_COAX.forEach((colour, coax) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, coax.get(), 8)
                .pattern("CCC")
                .pattern("CDC")
                .pattern("CCC")
                .define('C', RadiocraftItems.COAX_WIRE.get())
                .define('D', net.minecraft.world.item.DyeItem.byColor(colour))
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput, com.arrl.radiocraft.Radiocraft.id("coax_wire_" + colour.getSerializedName())));
    }

    private void buildCoaxWireRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.COAX_WIRE.get(), 6)
                .pattern("KKK")
                .pattern("###")
                .pattern("CCC")
                .define('#', RadiocraftItems.WIRE.get()) // The braided shield.
                .define('K', RadiocraftItems.INSULATION.get()) // The jacket.
                .define('C', RadiocraftItems.COAXIAL_CORE.get())
                .unlockedBy("has_coaxial_core", has(RadiocraftItems.COAXIAL_CORE.get()))
                .save(recipeOutput);
    }

    private void buildDuplexerRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.DUPLEXER.get(), 1)
                .pattern("III")
                .pattern("BBB")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('B', Items.BUCKET) // The tuned cavities.
                .unlockedBy("has_vhf_base_station", has(RadiocraftItems.VHF_BASE_STATION.get()))
                .save(recipeOutput);
    }

        private void buildSmallBatteryRecipe(RecipeOutput recipeOutput) {
        // A dead alkaline cell: smelted down, its steel can comes back as an iron nugget (all the
        // iron one cell takes), as iron tools smelt to nuggets. A furnace or a blast furnace.
        net.minecraft.data.recipes.SimpleCookingRecipeBuilder.smelting(Ingredient.of(RadiocraftItems.DEAD_ALKALINE_BATTERY.get()), RecipeCategory.MISC, Items.IRON_NUGGET, 0.1F, 200)
                .unlockedBy("has_dead_alkaline_battery", has(RadiocraftItems.DEAD_ALKALINE_BATTERY.get()))
                .save(recipeOutput, com.arrl.radiocraft.Radiocraft.id("iron_nugget_from_smelting_dead_alkaline_battery"));
        net.minecraft.data.recipes.SimpleCookingRecipeBuilder.blasting(Ingredient.of(RadiocraftItems.DEAD_ALKALINE_BATTERY.get()), RecipeCategory.MISC, Items.IRON_NUGGET, 0.1F, 100)
                .unlockedBy("has_dead_alkaline_battery", has(RadiocraftItems.DEAD_ALKALINE_BATTERY.get()))
                .save(recipeOutput, com.arrl.radiocraft.Radiocraft.id("iron_nugget_from_blasting_dead_alkaline_battery"));
        // The Small Alkaline Battery: a steel can (iron nuggets), zinc and manganese (redstone), carbon.
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.SMALL_ALKALINE_BATTERY.get(), 2)
                .requires(Items.IRON_NUGGET).requires(Items.IRON_NUGGET).requires(Items.REDSTONE).requires(ItemTags.COALS)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.SMALL_BATTERY.get(), 1)
                .pattern("IGI")
                .pattern("GCG")
                .pattern("#G#")
                .define('#', RadiocraftItems.WIRE.get())
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS)
                .define('C', ItemTags.COALS) // The carbon rod; charcoal does as well as coal.
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
    }

    private void buildRadioInterfaceRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.RADIO_INTERFACE.get(), 1)
                .pattern("   ")
                .pattern("#MS")
                .pattern("##D")
                .define('#', RadiocraftItems.WIRE.get())
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('M', RadiocraftItems.MICROPHONE.get())
                .define('D', Items.DIAMOND)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
    }

    private void buildHandMicrophoneRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.HAND_MICROPHONE.get(), 1)
                .pattern(" B ")
                .pattern("WEW")
                .pattern("   ")
                .define('W', RadiocraftItems.WIRE.get())
                .define('E', RadiocraftItems.MICROPHONE_ELEMENT.get())
                .define('B', ItemTags.BUTTONS)
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
    }

    /** A carbon microphone element, as vintage mics used: carbon granules behind a paper diaphragm, in a metal cup. */
    private void buildMicrophoneElementRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.MICROPHONE_ELEMENT.get(), 2)
                .pattern(" P ")
                .pattern("NCN")
                .define('P', Items.PAPER)
                .define('N', Items.IRON_NUGGET)
                .define('C', ItemTags.COALS)
                .unlockedBy("has_paper", has(Items.PAPER))
                .save(recipeOutput);
    }

    private void buildAntennaToolsRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ANTENNA_TUNER.get(), 1)
                .pattern("###")
                .pattern("#L#")
                .pattern("CPI")
                .define('#', RadiocraftItems.WIRE.get())
                .define('L', Items.LIGHTNING_ROD)
                .define('C', Items.COPPER_INGOT) // The roller coil.
                .define('P', Items.PAPER) // With the iron plates, the variable capacitor.
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
    }

    private void buildAntennaAnalyzerRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ANTENNA_ANALYZER.get(), 1)
                .pattern("CGC")
                .pattern("#H#")
                .pattern("DBD")
                .define('#', RadiocraftItems.WIRE.get())
                .define('C', Items.COPPER_INGOT)
                .define('G', Items.GLASS_PANE)
                .define('H', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .define('D', Items.DIAMOND) // A later-game tool.
                .define('B', RadiocraftItems.SMALL_BATTERY.get())
                .unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get()))
                .save(recipeOutput);
    }

    private void buildAntennaConnectorRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ANTENNA_CONNECTOR.get(), 2)
                .pattern(" # ")
                .pattern("KCK")
                .pattern(" O ")
                .define('#', RadiocraftItems.WIRE.get())
                .define('K', RadiocraftItems.INSULATION.get())
                .define('C', Items.COPPER_INGOT)
                .define('O', RadiocraftItems.COAX_WIRE.get())
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
    }

    /** A balun is wire wound on a ferrite core: the more windings, the bigger the ratio. */
    private void buildBalunRecipes(RecipeOutput recipeOutput) {
        balun(recipeOutput, RadiocraftItems.BALUN_ONE_TO_ONE.get(), 1);
        balun(recipeOutput, RadiocraftItems.BALUN_TWO_TO_ONE.get(), 2);
        balun(recipeOutput, RadiocraftItems.BALUN_FOUR_TO_ONE.get(), 3);
        balun(recipeOutput, RadiocraftItems.BALUN_NINE_TO_ONE.get(), 4);
    }

    private void balun(RecipeOutput recipeOutput, ItemLike balun, int windings) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, balun)
                .requires(RadiocraftItems.FERRITE_CORE.get())
                .requires(RadiocraftItems.COAX_WIRE.get())
                .requires(RadiocraftItems.WIRE.get(), windings)
                .unlockedBy("has_ferrite_core", has(RadiocraftItems.FERRITE_CORE.get()))
                .save(recipeOutput);
    }

    /** The VHF antennas, each laid out in its own shape. */
    private void buildVhfAntennaRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.J_POLE_ANTENNA.get(), 1)
                .pattern("  C")
                .pattern("COC")
                .pattern("CCC")
                .define('C', Items.COPPER_INGOT) // One bent tube, a J.
                .define('O', RadiocraftItems.COAX_WIRE.get())
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.RINGO_ANTENNA.get(), 1)
                .pattern(" I ")
                .pattern(" A ")
                .pattern("DBC")
                .define('I', Items.IRON_INGOT) // The mast tip.
                .define('A', RadiocraftItems.ANTENNA_WIRE.get()) // The radiator.
                .define('D', Items.DIAMOND)
                .define('B', Items.IRON_BLOCK) // The base and the matching ring.
                .define('C', RadiocraftItems.COAX_WIRE.get())
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.SLIM_JIM_ANTENNA.get(), 1)
                .pattern("#K#")
                .pattern("#K#")
                .pattern("#O#")
                .define('#', RadiocraftItems.WIRE.get()) // Twin-lead: two wires with spacers between.
                .define('K', RadiocraftItems.INSULATION.get())
                .define('O', RadiocraftItems.COAX_WIRE.get())
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.YAGI_ANTENNA.get(), 1)
                .pattern("LIL")
                .pattern("LIL")
                .pattern(" O ")
                .define('L', Items.LIGHTNING_ROD) // The elements across the boom.
                .define('I', Items.IRON_INGOT)
                .define('O', RadiocraftItems.COAX_WIRE.get())
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
    }

    private void buildDummyLoadRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.DUMMY_LOAD.get(), 1)
                .pattern(" O ")
                .pattern("ICI")
                .pattern("III")
                .define('O', RadiocraftItems.COAX_WIRE.get())
                .define('I', Items.IRON_INGOT)
                .define('C', Items.CHARCOAL) // The carbon resistor.
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
    }

    private void buildPowerDeviceRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.DESK_CHARGER.get(), 1)
                .pattern("I I")
                .pattern("#R#")
                .pattern("SSS")
                .define('I', Items.IRON_INGOT) // The cradle.
                .define('#', RadiocraftItems.WIRE.get())
                .define('R', Items.REDSTONE)
                .define('S', Items.SMOOTH_STONE_SLAB)
                .unlockedBy("has_wire", has(RadiocraftItems.WIRE.get()))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.CHARGE_CONTROLLER.get(), 1)
                .pattern("III")
                .pattern("#C#")
                .pattern("ICI")
                .define('I', Items.IRON_INGOT)
                .define('#', RadiocraftItems.WIRE.get())
                .define('C', Items.COMPARATOR) // Regulates the charge.
                .unlockedBy("has_solar_panel", has(RadiocraftItems.SOLAR_PANEL.get()))
                .save(recipeOutput);
    }

    /** Cheap and plentiful: a thick wall needs one per block. */
    private void buildPassthroughRecipe(RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.PASSTHROUGH.get(), 4)
                .requires(RadiocraftItems.WIRE.get())
                .requires(RadiocraftItems.COAX_WIRE.get())
                .requires(RadiocraftItems.INSULATION.get())
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
    }

    private void buildHfCircuitBoardRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.HF_CIRCUIT_BOARD.get(), 1)
                .pattern("SSS")
                .pattern("PRC")
                .pattern("III")
                .define('P', Items.PAPER) // Paper capacitors.
                .define('S', Items.SAND)
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE_TORCH) // The valve.
                .define('C', Items.COPPER_INGOT) // Tuning coils.
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);
    }

    private void buildHfReceiverRecipe(RecipeOutput recipeOutput) {
        Item[] poorCrystals = Arrays.stream(CrystalBand.values()).filter(band -> band != CrystalBand.VHF)
                .map(band -> RadiocraftItems.ENCASED_CRYSTALS.get(band).get(CrystalQuality.POOR).get()).toArray(Item[]::new);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.HF_RECEIVER.get(), 1)
                .pattern("LII")
                .pattern("CRS")
                .pattern("I#I")
                .define('#', RadiocraftItems.WIRE.get())
                .define('R', Ingredient.of(poorCrystals)) // Any poor HF crystal: a use for the ones sand turns up.
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('C', Items.COPPER_INGOT)
                .define('L', Items.LIGHTNING_ROD) // The antenna, on top.
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
    }

    private void buildVhfReceiverRecipe(RecipeOutput recipeOutput) {
        Item crystal = RadiocraftItems.ENCASED_CRYSTALS.get(CrystalBand.VHF).get(CrystalQuality.POOR).get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.VHF_RECEIVER.get(), 1)
                .pattern("W#W")
                .pattern("WCS")
                .pattern("III")
                .define('#', RadiocraftItems.WIRE.get())
                .define('I', Items.IRON_INGOT)
                .define('W', ItemTags.PLANKS)
                .define('C', crystal)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
    }

    private void buildVhfBaseStationRecipe(RecipeOutput recipeOutput) {
        crystalRadio(recipeOutput, RadiocraftItems.VHF_BASE_STATION_VARIANTS, CrystalBand.VHF, result -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                        .pattern("III")
                        .pattern("HRS")
                        .pattern("#WW")
                        .define('#', RadiocraftItems.WIRE.get())
                        .define('I', Items.IRON_INGOT)
                        .define('H', RadiocraftItems.HAND_MICROPHONE.get())
                        .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                        .define('W', ItemTags.PLANKS)
                        .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get())));
    }

    private void buildParrotControllerRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.PARROT_CONTROLLER.get(), 1)
                .pattern("III")
                .pattern("SPM")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('P', Items.REPEATER) // Sets the echo delay.
                .define('M', RadiocraftItems.HAND_MICROPHONE.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
    }

    private void buildSolarWeatherStationRecipe(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.TELETYPE.get(), 1)
                .pattern("AKG")
                .pattern("A##")
                .pattern("##I")
                .define('#', RadiocraftItems.WIRE.get())
                .define('I', Items.IRON_INGOT)
                .define('A', RadiocraftItems.ANTENNA_POLE.get())
                .define('K', Items.CLOCK)
                .define('G', Items.GLASS)
                .unlockedBy("has_antenna_pole", has(RadiocraftItems.ANTENNA_POLE.get()))
                .save(recipeOutput);
    }

    private void buildHfRadio10mRecipe(RecipeOutput recipeOutput) {
        buildHfRadioRecipe(recipeOutput, RadiocraftItems.HF_RADIO_10M_VARIANTS, CrystalBand.BAND_10M, Items.GOLD_INGOT); // Gold for the high band.
    }

    private void buildHfRadio20mRecipe(RecipeOutput recipeOutput) {
        buildHfRadioRecipe(recipeOutput, RadiocraftItems.HF_RADIO_20M_VARIANTS, CrystalBand.BAND_20M, Items.IRON_INGOT);
    }

    private void buildHfRadio40mRecipe(RecipeOutput recipeOutput) {
        buildHfRadioRecipe(recipeOutput, RadiocraftItems.HF_RADIO_40M_VARIANTS, CrystalBand.BAND_40M, Items.IRON_INGOT);
    }

    private void buildHfRadio80mRecipe(RecipeOutput recipeOutput) {
        buildHfRadioRecipe(recipeOutput, RadiocraftItems.HF_RADIO_80M_VARIANTS, CrystalBand.BAND_80M, Items.IRON_INGOT);
    }

    /** Every HF radio is built the same way: the crystal ('R') is cut for its band, and {@code base} is the bottom row. */
    private void buildHfRadioRecipe(RecipeOutput recipeOutput, CrystalVariants<Item, ? extends Item> radio, CrystalBand band, ItemLike base) {
        crystalRadio(recipeOutput, radio, band, result -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                        .pattern("BHS")
                        .pattern("#RT")
                        .pattern("III")
                        .define('#', RadiocraftItems.WIRE.get())
                        .define('H', RadiocraftItems.HAND_MICROPHONE.get())
                        .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                        .define('T', Items.TINTED_GLASS)
                        .define('I', base)
                        .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                        .unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get())));
    }

    /**
     * The all-band circuit board holds a pure crystal for every band on an HF circuit board; the all-band radio is
     * then built like the other HF rigs with the board where their crystal goes.
     */
    private void buildAllBandRadioRecipes(RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder board = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.ALL_BAND_CIRCUIT_BOARD.get(), 1)
                .requires(RadiocraftItems.HF_CIRCUIT_BOARD.get());
        for(CrystalBand band : CrystalBand.values())
            board.requires(RadiocraftItems.ENCASED_CRYSTALS.get(band).get(CrystalQuality.PURE).get());
        board.unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ALL_BAND_RADIO.get(), 1)
                .pattern("BHS")
                .pattern("#RT")
                .pattern("III")
                .define('#', RadiocraftItems.WIRE.get())
                .define('H', RadiocraftItems.HAND_MICROPHONE.get())
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('T', Items.TINTED_GLASS)
                .define('I', Items.IRON_INGOT)
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .define('R', RadiocraftItems.ALL_BAND_CIRCUIT_BOARD.get())
                .unlockedBy("has_all_band_circuit_board", has(RadiocraftItems.ALL_BAND_CIRCUIT_BOARD.get()))
                .save(recipeOutput);
    }

    private void buildVhfRepeaterRecipe(RecipeOutput recipeOutput) {
        Item crystal = RadiocraftItems.ENCASED_CRYSTALS.get(CrystalBand.VHF).get(CrystalQuality.PURE).get(); // Repeaters only take pure crystals.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.VHF_REPEATER.get(), 1)
                .pattern("ITI")
                .pattern("CBR")
                .pattern("I#I")
                .define('#', RadiocraftItems.WIRE.get())
                .define('I', Items.IRON_INGOT)
                .define('R', crystal)
                .define('B', variants(RadiocraftItems.VHF_BASE_STATION_VARIANTS))
                .define('C', Items.COPPER_INGOT)
                .define('T', Items.IRON_TRAPDOOR)
                .unlockedBy("has_vhf_base_station", has(RadiocraftItems.VHF_BASE_STATION.get()))
                .unlockedBy("has_encased_crystal", has(crystal))
                .save(recipeOutput);
    }

    /** QRP rigs are built around poor crystals, and only take those. */
    private void buildQrpRadioRecipes(RecipeOutput recipeOutput) {
        buildQrpRadioRecipe(recipeOutput, RadiocraftItems.QRP_RADIO_20M.get(), CrystalBand.BAND_20M);
        buildQrpRadioRecipe(recipeOutput, RadiocraftItems.QRP_RADIO_40M.get(), CrystalBand.BAND_40M);
    }

    private void buildQrpRadioRecipe(RecipeOutput recipeOutput, ItemLike radio, CrystalBand band) {
        Item crystal = RadiocraftItems.ENCASED_CRYSTALS.get(band).get(CrystalQuality.POOR).get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, radio, 1)
                .pattern("CBC")
                .pattern("#R#")
                .pattern("WWW")
                .define('#', RadiocraftItems.WIRE.get())
                .define('C', Items.COPPER_INGOT)
                .define('W', ItemTags.PLANKS) // A wooden case: lighter than the full HF radios.
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .define('R', crystal)
                .unlockedBy("has_encased_crystal", has(crystal))
                .save(recipeOutput);
    }

    /**
     * {@code recipe} once for each crystal quality: the shaped recipe for the radio of that quality, with the encased
     * crystal for {@code band} as 'R'. The good radio keeps the plain recipe id. Also adds the crystal swap: any other
     * quality of the radio plus an encased crystal gives the radio of that crystal's quality.
     */
    private void crystalRadio(RecipeOutput recipeOutput, CrystalVariants<Item, ? extends Item> radio, CrystalBand band, Function<ItemLike, ShapedRecipeBuilder> recipe) {
        for(CrystalQuality quality : CrystalQuality.values()) {
            Item result = radio.get(quality).get();
            Item crystal = RadiocraftItems.ENCASED_CRYSTALS.get(band).get(quality).get();
            recipe.apply(result)
                    .define('R', crystal)
                    .unlockedBy("has_encased_crystal", has(crystal))
                    .save(recipeOutput);

            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result)
                    .requires(Ingredient.of(radio.values().filter(other -> other != result).toArray(Item[]::new)))
                    .requires(crystal)
                    .unlockedBy("has_encased_crystal", has(crystal))
                    .save(recipeOutput, Radiocraft.id(BuiltInRegistries.ITEM.getKey(result).getPath() + "_from_crystal_swap"));
        }
    }

    /** Any crystal quality of a radio. */
    private static Ingredient variants(CrystalVariants<Item, ? extends Item> radio) {
        return Ingredient.of(radio.values().toArray(Item[]::new));
    }

    /** Any quality of encased crystal for {@code band}: a commercial radio is synthesized, the crystal is only its reference. */
    private static Ingredient encasedCrystal(CrystalBand band) {
        return Ingredient.of(RadiocraftItems.ENCASED_CRYSTALS.get(band).values().stream().map(Supplier::get).toArray(ItemLike[]::new));
    }

    /** Any encased crystal, any band and quality. */
    private static Ingredient anyEncasedCrystal() {
        return Ingredient.of(RadiocraftItems.ENCASED_CRYSTALS.values().stream().flatMap(m -> m.values().stream()).map(Supplier::get).toArray(ItemLike[]::new));
    }

    /** The commercial radios (1.12 step 2): a cheap brick, its fire-service upgrade, and the two mobiles. */
    private void buildCommercialRadioRecipes(RecipeOutput recipeOutput) {
        // The 1990s brick (maintainer, 2026-11-05): an antenna, a VHF crystal, a speaker and a built-in mic element in an
        // iron case with one PTT button. No keypad, so it costs about what the ham VHF handheld does.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get())
                .pattern(" L ")
                .pattern("SCE")
                .pattern("IBI")
                .define('L', Items.LIGHTNING_ROD)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('C', encasedCrystal(CrystalBand.VHF))
                .define('E', RadiocraftItems.MICROPHONE_ELEMENT.get())
                .define('I', Items.IRON_INGOT)
                .define('B', ItemTags.BUTTONS)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
        // The fire-service handheld (maintainer, 2026-11-05): the brick with a display (glass pane), a keypad (buttons)
        // and a comparator for the digital and encryption logic. Its channels are not carried over.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD.get())
                .pattern(" G ")
                .pattern("BHB")
                .pattern("RCR")
                .define('G', Items.GLASS_PANE)
                .define('B', ItemTags.BUTTONS)
                .define('H', RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get())
                .define('R', Items.REDSTONE)
                .define('C', Items.COMPARATOR)
                .unlockedBy("has_basic_commercial_handheld", has(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get()))
                .save(recipeOutput);
        // The mobiles (maintainer, 2026-11-05): the VHF base station's parts in an all-metal case with power leads. Low band
        // takes a 10m crystal.
        mobileRecipe(recipeOutput, RadiocraftItems.COMMERCIAL_MOBILE.get(), CrystalBand.VHF);
        mobileRecipe(recipeOutput, RadiocraftItems.LOW_BAND_MOBILE.get(), CrystalBand.BAND_10M);
    }

    /**
     * The marine radios (1.12 step 3): built like the commercial ones, with waterproof wire for a life
     * at sea. The fixed-mount radio is the mobile's recipe on waterproof wire; the handheld the brick's with it.
     */
    /**
     * The CB radios (2026-11-08): built on 10m crystals. The portable and the PC68 take any 10m
     * crystal, the 5875 a good or pure one (and a clock for its clock, dark oak for its woodgrain lid).
     */
    private void buildCbRadioRecipes(RecipeOutput recipeOutput) {
        Ingredient anyTen = encasedCrystal(CrystalBand.BAND_10M);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.CB_PORTABLE.get())
                .pattern("  L")
                .pattern("SCE")
                .pattern("IBI")
                .define('L', Items.LIGHTNING_ROD)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('C', anyTen)
                .define('E', RadiocraftItems.MICROPHONE_ELEMENT.get())
                .define('I', Items.IRON_INGOT)
                .define('B', ItemTags.BUTTONS)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
        // PC68: a button on top for its CH9 switch, which also keeps it apart from the Low Band Mobile.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.CB_MOBILE_PC68.get())
                .pattern("IBI")
                .pattern("MCS")
                .pattern("#R#")
                .define('I', Items.IRON_INGOT)
                .define('B', ItemTags.BUTTONS)
                .define('M', RadiocraftItems.HAND_MICROPHONE.get())
                .define('C', anyTen)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('#', RadiocraftItems.WIRE.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
        // The CB ground plane: tubing up the middle (lightning rods), radials, coax to the base.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.CB_GROUND_PLANE_ANTENNA.get())
                .pattern(" L ")
                .pattern(" L ")
                .pattern("RCR")
                .define('L', Items.LIGHTNING_ROD)
                .define('R', RadiocraftItems.ANTENNA_WIRE.get())
                .define('C', RadiocraftItems.COAX_WIRE.get())
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.CB_MOBILE_5875.get())
                .pattern("PKP")
                .pattern("SCM")
                .pattern("IRI")
                .define('P', Items.DARK_OAK_PLANKS)
                .define('K', Items.CLOCK)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('C', Ingredient.of(RadiocraftItems.ENCASED_CRYSTALS.get(CrystalBand.BAND_10M).get(com.arrl.radiocraft.common.radio.crystal.CrystalQuality.GOOD).get(),
                        RadiocraftItems.ENCASED_CRYSTALS.get(CrystalBand.BAND_10M).get(com.arrl.radiocraft.common.radio.crystal.CrystalQuality.PURE).get())) // Good or better.
                .define('M', RadiocraftItems.HAND_MICROPHONE.get())
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
    }

    private void buildMarineRadioRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.MARINE_HANDHELD.get())
                .pattern("WLW")
                .pattern("SCE")
                .pattern("IBI")
                .define('W', RadiocraftItems.WATERPROOF_WIRE.get())
                .define('L', Items.LIGHTNING_ROD)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('C', encasedCrystal(CrystalBand.VHF))
                .define('E', RadiocraftItems.MICROPHONE_ELEMENT.get())
                .define('I', Items.IRON_INGOT)
                .define('B', ItemTags.BUTTONS)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.MARINE_RADIO.get())
                .pattern("III")
                .pattern("MCS")
                .pattern("#R#")
                .define('I', Items.IRON_INGOT)
                .define('M', RadiocraftItems.HAND_MICROPHONE.get())
                .define('C', encasedCrystal(CrystalBand.VHF))
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('#', RadiocraftItems.WATERPROOF_WIRE.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
        // The Real-World Interface (1.12 step 11; maintainer, 2026-11-05): a rig interface box. A radio interface (the TNC,
        // which already carries the audio both ways), leads to the real rig, a comparator for the VOX, a board, an iron case.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.REAL_WORLD_INTERFACE.get())
                .pattern("ICI")
                .pattern("#R#")
                .pattern("IBI")
                .define('I', Items.IRON_INGOT)
                .define('C', Items.COMPARATOR)
                .define('#', RadiocraftItems.WIRE.get())
                .define('R', RadiocraftItems.RADIO_INTERFACE.get())
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .unlockedBy("has_radio_interface", has(RadiocraftItems.RADIO_INTERFACE.get()))
                .save(recipeOutput);
    }

    private void mobileRecipe(RecipeOutput recipeOutput, Item result, CrystalBand crystal) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("III")
                .pattern("MCS")
                .pattern("#R#")
                .define('I', Items.IRON_INGOT)
                .define('M', RadiocraftItems.HAND_MICROPHONE.get())
                .define('C', encasedCrystal(crystal))
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('#', RadiocraftItems.WIRE.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
    }

    private void buildVhfHandheldRecipe(RecipeOutput recipeOutput) {
        crystalRadio(recipeOutput, RadiocraftItems.VHF_HANDHELD_VARIANTS, CrystalBand.VHF, result -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                        .pattern("L  ")
                        .pattern("MRS")
                        .pattern("BBB")
                        .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                        .define('L', Items.LIGHTNING_ROD)
                        .define('M', RadiocraftItems.HAND_MICROPHONE.get())
                        .define('B', ItemTags.BUTTONS)
                        .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get())));

        // The APRS module: a compass for its GPS on a circuit board, in copper. Crafted with a handheld it goes inside.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.APRS_MODULE.get())
                .pattern("WPW")
                .pattern("CBC")
                .define('W', RadiocraftItems.WIRE.get())
                .define('P', Items.COMPASS)
                .define('C', Items.COPPER_INGOT)
                .define('B', RadiocraftItems.HF_CIRCUIT_BOARD.get())
                .unlockedBy("has_vhf_handheld", has(RadiocraftItems.VHF_HANDHELD.get()))
                .save(recipeOutput);
        net.minecraft.data.recipes.SpecialRecipeBuilder.special(com.arrl.radiocraft.common.crafting.AprsModuleInstallRecipe::new)
                .save(recipeOutput, Radiocraft.id("vhf_handheld_aprs_module"));

        // The MARS mod (maintainer, 2026-11-05): a diode (redstone torch) and jumper wires on a spare crystal of any band.
        // Crafted with a radio it goes inside.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.MARS_MOD.get())
                .pattern("WTW")
                .pattern(" C ")
                .define('W', RadiocraftItems.WIRE.get())
                .define('T', Items.REDSTONE_TORCH)
                .define('C', anyEncasedCrystal())
                // Contraband (maintainer, 2026-11-05): never offered by the recipe book. Its only criterion can never
                // trigger, so the recipe is unlocked only by /recipe give. JEI hides it too (RadiocraftJeiPlugin); the
                // Handbook still shows it.
                .unlockedBy("impossible", net.minecraft.advancements.CriteriaTriggers.IMPOSSIBLE.createCriterion(
                        new net.minecraft.advancements.critereon.ImpossibleTrigger.TriggerInstance()))
                .save(recipeOutput);
        net.minecraft.data.recipes.SpecialRecipeBuilder.special(com.arrl.radiocraft.common.crafting.MarsModInstallRecipe::new)
                .save(recipeOutput, Radiocraft.id("mars_mod_install"));

        // The Radio Cloner (1.12 playtest fixes 2): the cloning cable (wire) on an iron box with its memory
        // (diamond) and logic (comparator).
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RadiocraftItems.RADIO_CLONER.get())
                .pattern(" W ")
                .pattern("IDI")
                .pattern("ICI")
                .define('W', RadiocraftItems.WIRE.get())
                .define('I', Items.IRON_INGOT)
                .define('D', Items.DIAMOND)
                .define('C', Items.COMPARATOR)
                .unlockedBy("has_basic_commercial_handheld", has(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get()))
                .unlockedBy("has_vhf_handheld", has(RadiocraftItems.VHF_HANDHELD.get()))
                .save(recipeOutput);

        // The band plan sheet: paper and ink. Cheap, so every shack can pin one up.
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.BAND_PLAN.get())
                .requires(Items.PAPER)
                .requires(Items.INK_SAC)
                .unlockedBy("has_paper", has(Items.PAPER))
                .save(recipeOutput);

        // The camera (1.12 step 4): an iron body, a glass lens, a redstone shutter and a button to fire it.
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RadiocraftItems.CAMERA.get())
                .pattern(" B ")
                .pattern("IGI")
                .pattern("IRI")
                .define('B', ItemTags.BUTTONS)
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS_PANE)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_map", has(Items.MAP))
                .save(recipeOutput);
        // Photo paper: an empty map in eight paper makes sixteen black and white sheets; the bottom row as red, green and
        // blue dye makes colour paper.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.PHOTO_PAPER.get(), 16)
                .pattern("PPP")
                .pattern("PMP")
                .pattern("PPP")
                .define('P', Items.PAPER)
                .define('M', Items.MAP)
                .unlockedBy("has_map", has(Items.MAP))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.COLOR_PHOTO_PAPER.get(), 16)
                .pattern("PPP")
                .pattern("PMP")
                .pattern("RGB")
                .define('P', Items.PAPER)
                .define('M', Items.MAP)
                .define('R', Items.RED_DYE)
                .define('G', Items.GREEN_DYE)
                .define('B', Items.BLUE_DYE)
                .unlockedBy("has_map", has(Items.MAP))
                .save(recipeOutput);

        radioComputer(recipeOutput);
        adsb(recipeOutput);
        fccGear(recipeOutput);
    }

    /**
     * FCC field gear (1.12 step 10): the felt hat (brown wool with a black band), the corduroy jacket (brown wool with a
     * red tie) and the seal banner pattern (paper and a lightning rod, for the bolts on the seal). The badge has no
     * recipe: operators issue it with /radiocraft fcc badge.
     */
    private void fccGear(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, RadiocraftItems.FCC_HAT.get())
                .pattern("WWW")
                .pattern("WBW")
                .define('W', Items.BROWN_WOOL)
                .define('B', Items.BLACK_DYE)
                .unlockedBy("has_brown_wool", has(Items.BROWN_WOOL))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, RadiocraftItems.FCC_JACKET.get())
                .pattern("W W")
                .pattern("WTW")
                .pattern("WWW")
                .define('W', Items.BROWN_WOOL)
                .define('T', Items.RED_DYE)
                .unlockedBy("has_brown_wool", has(Items.BROWN_WOOL))
                .save(recipeOutput);
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.FCC_BANNER_PATTERN.get())
                .requires(Items.PAPER)
                .requires(Items.LIGHTNING_ROD)
                .unlockedBy("has_lightning_rod", has(Items.LIGHTNING_ROD))
                .save(recipeOutput);
    }

    /**
     * ADS-B (1.12 step 9): the beacon (a transponder box: its blade antenna, a circuit board, iron case), the receiver
     * (a green CRT over a circuit board) and the antenna (a short copper stub on coax).
     */
    private static void adsb(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ADSB_BEACON.get())
                .pattern(" L ")
                .pattern("ICI")
                .pattern("IRI")
                .define('L', Items.LIGHTNING_ROD)
                .define('I', Items.IRON_INGOT)
                .define('C', encasedCrystal(CrystalBand.VHF))
                .define('R', Items.REDSTONE)
                .unlockedBy("has_lightning_rod", has(Items.LIGHTNING_ROD))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ADSB_RECEIVER.get())
                .pattern("IGI")
                .pattern("RCR")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.LIME_STAINED_GLASS_PANE)
                .define('R', Items.REDSTONE)
                .define('C', Items.COMPARATOR)
                .unlockedBy("has_comparator", has(Items.COMPARATOR))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.ADSB_ANTENNA.get())
                .pattern("C")
                .pattern("I")
                .pattern("X")
                .define('C', Items.COPPER_INGOT)
                .define('I', Items.IRON_INGOT)
                .define('X', RadiocraftItems.COAX_WIRE.get())
                .unlockedBy("has_coax_wire", has(RadiocraftItems.COAX_WIRE.get()))
                .save(recipeOutput);
        // Weather alerts (1.12): the generator is an iron cabinet that reads the
        // sky (daylight detector) and decides (comparators) round a VHF crystal; the receiver a small radio with a whip
        // (lightning rod), a speaker and a red lamp (redstone torch).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.WEATHER_ALERT_GENERATOR.get())
                .pattern("IDI")
                .pattern("CXC")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('D', Items.DAYLIGHT_DETECTOR)
                .define('C', Items.COMPARATOR)
                .define('X', encasedCrystal(CrystalBand.VHF))
                .define('R', Items.REDSTONE)
                .unlockedBy("has_comparator", has(Items.COMPARATOR))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.WEATHER_ALERT_RECEIVER.get())
                .pattern(" L ")
                .pattern("SXT")
                .pattern("III")
                .define('L', Items.LIGHTNING_ROD)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('X', encasedCrystal(CrystalBand.VHF))
                .define('T', Items.REDSTONE_TORCH)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput);
    }

    /**
     * The radio computer (1.12 step 5): the computer, its monitor and scanner, a blank floppy, and each program's floppy
     * (a blank disk and something that stands for the program). The secret programs have no recipe: they are found.
     */
    private static void radioComputer(RecipeOutput recipeOutput) {
        // The computer is late game (maintainer, 2026-11-05): gold edge connectors and a crystal clock oscillator, a
        // processor between nether quartz blocks, and a floppy drive door in an iron case. The processor is a diamond
        // block (maintainer, 2026-11-06: something players really have to strive for).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.RADIO_COMPUTER.get())
                .pattern("GXG")
                .pattern("QDQ")
                .pattern("ITI")
                .define('G', Items.GOLD_INGOT)
                .define('X', anyEncasedCrystal())
                .define('Q', Items.QUARTZ_BLOCK)
                .define('D', Items.DIAMOND_BLOCK)
                .define('I', Items.IRON_INGOT)
                .define('T', Items.IRON_TRAPDOOR)
                .unlockedBy("has_diamond", has(Items.DIAMOND))
                .save(recipeOutput);
        // A CRT (a glass pane in an iron case, a redstone tube) over a keyboard of buttons.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.COMPUTER_MONITOR.get())
                .pattern("III")
                .pattern("IGI")
                .pattern("BRB")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS_PANE)
                .define('B', ItemTags.BUTTONS)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_hf_circuit_board", has(RadiocraftItems.HF_CIRCUIT_BOARD.get()))
                .save(recipeOutput);
        // Flatbed glass on top, ink for the printer underneath.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RadiocraftItems.SCANNER.get())
                .pattern("GGG")
                .pattern("IRI")
                .pattern("IKI")
                .define('G', Items.GLASS_PANE)
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .define('K', Items.INK_SAC)
                .unlockedBy("has_paper", has(Items.PAPER))
                .save(recipeOutput);
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.FLOPPY_DISK.get(), 2)
                .requires(Items.IRON_NUGGET) // The shutter.
                .requires(Items.REDSTONE) // The magnetic disk.
                .requires(Items.BLACK_DYE) // The plastic shell.
                .requires(Items.PAPER) // The label.
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(recipeOutput);
        java.util.Map<com.arrl.radiocraft.common.computer.ComputerApp, net.minecraft.world.level.ItemLike> programs = new java.util.LinkedHashMap<>();
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.BOOK_EDITOR, Items.WRITABLE_BOOK);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.LOGBOOK, Items.BOOK);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.FREQUENCY_MANAGER, RadiocraftItems.BAND_PLAN.get());
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.PROGRAMMER, Items.COMPARATOR);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.SOLAR_WEATHER, Items.DAYLIGHT_DETECTOR);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.APRS, Items.COMPASS);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.WEFAX, Items.MAP);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.RADIO_FAX, Items.PAPER);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.SSTV, Items.PAINTING);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.DIRECTION_FINDING, Items.SPYGLASS);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.ADSB, Items.FEATHER);
        // Computer apps 2 (2026-11-06), for the maintainer's review.
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.RTTY, Items.NOTE_BLOCK); // Mark and space tones.
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.BBS, Items.CHEST); // The mailbox.
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.PROPAGATION, Items.CLOCK); // The hours ahead.
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.REPEATERS, Items.REPEATER);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.SCHEDULER, Items.BELL); // Computer batch 3A: it rings the hours.
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.RIG_CONTROL, Items.LEVER); // Computer batch 3B: the rig's controls.
        // Computer batch 3C: the net control station reads the roll from a lectern; certificates go in a frame;
        // QSL cards are printed in ink.
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.NET_CONTROL, Items.LECTERN);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.AWARDS, Items.ITEM_FRAME);
        programs.put(com.arrl.radiocraft.common.computer.ComputerApps.QSL, Items.INK_SAC);
        programs.forEach((app, ingredient) -> net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, java.util.Objects.requireNonNull(RadiocraftItems.floppy(app)))
                .requires(RadiocraftItems.FLOPPY_DISK.get())
                .requires(ingredient)
                .unlockedBy("has_floppy_disk", has(RadiocraftItems.FLOPPY_DISK.get()))
                .save(recipeOutput));
        // The QSL album (computer batch 3C): a book bound with leather and two more sheets.
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RadiocraftItems.QSL_ALBUM.get())
                .requires(Items.BOOK)
                .requires(Items.LEATHER)
                .requires(Items.PAPER, 2)
                .unlockedBy("has_book", has(Items.BOOK))
                .save(recipeOutput);
        // FT8 (computer batch 3B): a blank floppy and any pressure plate. Vanilla 1.21.1 has no item tag
        // for every pressure plate (minecraft:pressure_plates is a block tag only), so: the wooden ones' tag and the rest.
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, java.util.Objects.requireNonNull(RadiocraftItems.floppy(com.arrl.radiocraft.common.computer.ComputerApps.FT8)))
                .requires(RadiocraftItems.FLOPPY_DISK.get())
                .requires(net.minecraft.world.item.crafting.Ingredient.fromValues(java.util.stream.Stream.of( // One plain vanilla ingredient list.
                        new net.minecraft.world.item.crafting.Ingredient.TagValue(net.minecraft.tags.ItemTags.WOODEN_PRESSURE_PLATES),
                        new net.minecraft.world.item.crafting.Ingredient.ItemValue(new net.minecraft.world.item.ItemStack(Items.STONE_PRESSURE_PLATE)),
                        new net.minecraft.world.item.crafting.Ingredient.ItemValue(new net.minecraft.world.item.ItemStack(Items.POLISHED_BLACKSTONE_PRESSURE_PLATE)),
                        new net.minecraft.world.item.crafting.Ingredient.ItemValue(new net.minecraft.world.item.ItemStack(Items.LIGHT_WEIGHTED_PRESSURE_PLATE)),
                        new net.minecraft.world.item.crafting.Ingredient.ItemValue(new net.minecraft.world.item.ItemStack(Items.HEAVY_WEIGHTED_PRESSURE_PLATE)))))
                .unlockedBy("has_floppy_disk", has(RadiocraftItems.FLOPPY_DISK.get()))
                .save(recipeOutput);
    }

    /** Every pure crystal, raw or encased, of any band. */
    private static net.minecraft.world.item.ItemStack[] pureCrystals() {
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
        for(com.arrl.radiocraft.common.radio.crystal.CrystalBand band : com.arrl.radiocraft.common.radio.crystal.CrystalBand.values()) {
            out.add(new net.minecraft.world.item.ItemStack(RadiocraftItems.CRYSTALS.get(band).get(com.arrl.radiocraft.common.radio.crystal.CrystalQuality.PURE).get()));
            out.add(new net.minecraft.world.item.ItemStack(RadiocraftItems.ENCASED_CRYSTALS.get(band).get(com.arrl.radiocraft.common.radio.crystal.CrystalQuality.PURE).get()));
        }
        return out.toArray(net.minecraft.world.item.ItemStack[]::new);
    }

    private static void premiumSpeaker(RecipeOutput recipeOutput, Item speaker, Item material, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, speaker, 1)
                .pattern("MSM")
                .pattern("MCM")
                .pattern("MSM")
                .define('M', material)
                .define('S', RadiocraftItems.RADIO_SPEAKER.get())
                .define('C', Items.COPPER_INGOT)
                .unlockedBy("has_radio_speaker", has(RadiocraftItems.RADIO_SPEAKER.get()))
                .save(recipeOutput, Radiocraft.id("premium_speaker_" + name));
    }
}
