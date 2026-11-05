package com.arrl.radiocraft;

import com.arrl.radiocraft.common.init.*;
import com.arrl.radiocraft.common.network.RadiocraftNetworking;
import com.arrl.radiocraft.compat.TopCompat;
import com.arrl.radiocraft.datagen.RadiocraftBlockLootProvider;
import com.arrl.radiocraft.datagen.RadiocraftBlockTagsProvider;
import com.arrl.radiocraft.datagen.RadiocraftBlockstateProvider;
import com.arrl.radiocraft.datagen.RadiocraftLanguageProvider;
import com.arrl.radiocraft.datagen.RadiocraftRecipesProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider.Factory;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.arrl.radiocraft.compat.curios.CuriosCompat;
import com.arrl.radiocraft.compat.anotherweathermod.AwmCompat;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.util.List;
import java.util.Random;
import java.util.Set;

@Mod(Radiocraft.MOD_ID)
public class Radiocraft {

    public static final String MOD_ID = "radiocraft";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Random RANDOM = new Random();
    public static final boolean IS_DEVELOPMENT_ENV = System.getenv("RADIOCRAFT_DEV_ENV") != null;
    public static final String NETWORK_VERSION = "1";

    public Radiocraft(IEventBus modEventBus) {
        registerRegistries(modEventBus);

        TopCompatRegistry tcr = new TopCompatRegistry();
        modEventBus.register(tcr);
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(CuriosCompat::register));
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(AwmCompat::register));
        // The radio computer's office programs' server sides (1.12 step 6).
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.arrl.radiocraft.common.computer.apps.OfficeApps::register));
        // And its radio programs' (1.12 step 7).
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.arrl.radiocraft.common.computer.apps.RadioApps::register));

        NeoForge.EVENT_BUS.register(RadiocraftSavedData.class);
        // The ham shack in village house pools (1.12 step 8).
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerAboutToStartEvent.class, com.arrl.radiocraft.common.worldgen.VillageHouses::onServerAboutToStart);
        // ADS-B (1.12 step 9): the beacons on the air are kept in memory per level; a closed world forgets them.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class, event -> com.arrl.radiocraft.common.radio.adsb.AdsbAirspace.clear());
        // The CPS's "Program radios" mode: a radio held while right clicking the computer (1.12 playtest fixes 2).
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, com.arrl.radiocraft.common.computer.apps.HandProgramming::onRightClickBlock);
        // Real-World Interfaces (1.12 step 11): unkey every real radio and close every sound card and serial port.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppingEvent.class, event -> com.arrl.radiocraft.common.realworld.Bridges.closeAll(2_000L));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class, event -> com.arrl.radiocraft.common.realworld.Everywhere.clear());

        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC, Radiocraft.MOD_ID + "-common.toml");
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, RadiocraftServerConfig.SPEC, Radiocraft.MOD_ID + "-server.toml");
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, RadiocraftClientConfig.SPEC, Radiocraft.MOD_ID + "-client.toml");
    }

    // This is probably not the best/easiest way to do this, but I couldn't get it to cooperate otherwise, and this is perfectly valid.
    static class TopCompatRegistry {
        @SubscribeEvent
        public void commonSetup(FMLCommonSetupEvent event) {
            TopCompat.register();
        }
    }

    // Registering deferred registries to the event bus
    private static void registerRegistries(IEventBus modEventBus) {

        RadiocraftAntennaTypes.register();
        BENetworkTypes.register();
        RadiocraftBlocks.BLOCKS.register(modEventBus);
        com.arrl.radiocraft.common.init.RadiocraftArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        RadiocraftItems.ITEMS.register(modEventBus);
        RadiocraftBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        RadiocraftEntityTypes.ENTITIES.register(modEventBus);
        RadiocraftMenuTypes.MENU_TYPES.register(modEventBus);
        RadiocraftSoundEvents.SOUND_EVENTS.register(modEventBus);
        RadiocraftTabs.CREATIVE_TABS.register(modEventBus);
        RadiocraftDataComponent.DATA_COMPONENTS.register(modEventBus);
        RadiocraftLootModifiers.LOOT_MODIFIERS.register(modEventBus);
        RadiocraftIngredientTypes.INGREDIENT_TYPES.register(modEventBus);
        com.arrl.radiocraft.common.init.RadiocraftRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        RadiocraftStructures.register(modEventBus);
        com.arrl.radiocraft.compat.create.CreateCompat.register(modEventBus); // The alternator, with Create.

        modEventBus.addListener(Radiocraft::gatherData);
        modEventBus.addListener(Radiocraft::registerPackets);
    }

    //Networking registration is done using the subscribeEvent method as it doesn't seem to have a differed registry implementation
    //Placed here for locational consistency with other registrations
    @SubscribeEvent
    public static void registerPackets(final RegisterPayloadHandlersEvent event) {
        // Sets the current network version
        final PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        RadiocraftNetworking.register(registrar);
    }

    // Added to mod event bus, for data gen
    public static void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        gen.addProvider(event.includeServer(),
                new RadiocraftBlockTagsProvider(gen.getPackOutput(), event.getLookupProvider(), existingFileHelper));
        gen.addProvider(event.includeClient(), (Factory<RadiocraftLanguageProvider>) output ->
                new RadiocraftLanguageProvider(output, "en_us"));
        gen.addProvider(event.includeClient(), (Factory<RadiocraftBlockstateProvider>) output -> new
                RadiocraftBlockstateProvider(output, existingFileHelper));
        gen.addProvider(true, (Factory<RadiocraftRecipesProvider>) output -> new
                RadiocraftRecipesProvider(output, event.getLookupProvider()));
        gen.addProvider(event.includeServer(), (Factory<LootTableProvider>) output -> new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(RadiocraftBlockLootProvider::new, LootContextParamSets.BLOCK)),
                event.getLookupProvider()));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static String translationKey(String prefix, String suffix) {
        return String.format("%s.%s.%s", prefix, MOD_ID, suffix);
    }

}
