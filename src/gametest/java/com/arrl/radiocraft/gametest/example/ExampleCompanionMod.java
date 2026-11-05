package com.arrl.radiocraft.gametest.example;

import com.arrl.radiocraft.api.RadiocraftApi;
import com.arrl.radiocraft.api.antenna.AntennaNetworks;
import com.arrl.radiocraft.api.antenna.AntennaTypes;
import com.arrl.radiocraft.api.antenna.CompanionAntennaBlock;
import com.arrl.radiocraft.api.antenna.CompanionAntennaBlockEntity;
import com.arrl.radiocraft.api.antenna.SimpleAntennaType;
import com.arrl.radiocraft.api.radio.CompanionRadioBlock;
import com.arrl.radiocraft.api.radio.CompanionRadioBlockEntity;
import com.arrl.radiocraft.api.radio.RadioBands;
import com.arrl.radiocraft.api.radio.RadioSpec;
import com.arrl.radiocraft.common.radio.Band;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * An example companion mod, written as another mod would write it: it only imports RadioCraft's {@code api} package
 * (plus {@link Band}, the band record the API passes around), vanilla and NeoForge. It adds
 * <ul>
 *     <li>a 6m amateur band (50-54 MHz) that RadioCraft does not have,</li>
 *     <li>a commercial low band (30-50 MHz) FM mobile radio, {@code radiocraft_example:low_band_radio},</li>
 *     <li>a ground plane antenna, {@code radiocraft_example:ground_plane}, matched for low band and 6m.</li>
 * </ul>
 * The blocks have no item, model or recipe (a real mod adds those as for any block); {@link CompanionApiGameTests}
 * places them and proves they talk to RadioCraft's own radios.
 *
 * <p>It lives in the GameTest source set, which is part of the dev runs only and never ships. In a real mod the
 * {@code modid} below is your own mod id; here the classes belong to the "radiocraft" dev mod, so the subscriber uses
 * that and registers its blocks under its own namespace.</p>
 */
@EventBusSubscriber(modid = "radiocraft") // NeoForge sends mod-bus events (registration, capabilities, setup) to the mod bus by itself.
public final class ExampleCompanionMod {

	/** The example's namespace. */
	public static final String NAMESPACE = "radiocraft_example";

	/** 6m: amateur, 50-54 MHz; line of sight like 10m, sporadic skip only when the sun is lively (follows 10m). */
	public static final Band SIX_METERS = new Band("6m", 700, 1500, 4000, 0, 0, 50_000_000.0F, 54_000_000.0F);

	/** The mobile: FM, 12.5 kHz channels, stays in low band, a VHF base station's power draw. */
	public static final RadioSpec LOW_BAND_SPEC = RadioSpec.fm(RadioBands.LOW_BAND, 12_500);

	public static CompanionRadioBlock LOW_BAND_RADIO;
	public static CompanionAntennaBlock GROUND_PLANE;
	public static BlockEntityType<CompanionRadioBlockEntity> LOW_BAND_RADIO_TYPE;
	public static BlockEntityType<CompanionAntennaBlockEntity> GROUND_PLANE_TYPE;

	/** The ground plane: SWR 1.2 and unity gain on low band, 1.5 and +1 dBi on 6m, 10 elsewhere. */
	public static final SimpleAntennaType GROUND_PLANE_ANTENNA = SimpleAntennaType.builder(id("ground_plane"))
			.block(() -> GROUND_PLANE)
			.band(RadioBands.LOW_BAND, 1.2D, 0.0D)
			.band("6m", 1.5D, 1.0D)
			.lineOfSight(1.2D)
			.skip(0.7D)
			.build();

	private ExampleCompanionMod() {}

	/**
	 * An id in the example's namespace.
	 *
	 * @param path the path
	 * @return the id
	 */
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
	}

	/** Blocks and block entity types, as any mod registers them (a DeferredRegister works the same). */
	@SubscribeEvent
	public static void register(RegisterEvent event) {
		event.register(Registries.BLOCK, helper -> {
			BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.METAL).noOcclusion();
			LOW_BAND_RADIO = new CompanionRadioBlock(properties,
					(pos, state) -> new CompanionRadioBlockEntity(LOW_BAND_RADIO_TYPE, pos, state, LOW_BAND_SPEC));
			GROUND_PLANE = new CompanionAntennaBlock(properties, AntennaNetworks.HF, // Low band and 6m are below 100 MHz.
					(pos, state) -> new CompanionAntennaBlockEntity(GROUND_PLANE_TYPE, pos, state));
			helper.register(id("low_band_radio"), LOW_BAND_RADIO);
			helper.register(id("ground_plane"), GROUND_PLANE);
		});
		event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
			LOW_BAND_RADIO_TYPE = BlockEntityType.Builder.<CompanionRadioBlockEntity>of(
					(pos, state) -> new CompanionRadioBlockEntity(LOW_BAND_RADIO_TYPE, pos, state, LOW_BAND_SPEC), LOW_BAND_RADIO).build(null);
			GROUND_PLANE_TYPE = BlockEntityType.Builder.<CompanionAntennaBlockEntity>of(
					(pos, state) -> new CompanionAntennaBlockEntity(GROUND_PLANE_TYPE, pos, state), GROUND_PLANE).build(null);
			helper.register(id("low_band_radio"), LOW_BAND_RADIO_TYPE);
			helper.register(id("ground_plane"), GROUND_PLANE_TYPE);
		});
	}

	/** Without this the blocks never get a network object: the radio would not power on, the antenna reach nothing. */
	@SubscribeEvent
	public static void capabilities(RegisterCapabilitiesEvent event) {
		RadiocraftApi.registerNetworkBlocks(event, LOW_BAND_RADIO, GROUND_PLANE);
	}

	/** The band and the antenna type. The antenna registry is not thread safe, so it goes in enqueueWork. */
	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		if(RadiocraftApi.API_VERSION_MAJOR != 1)
			return; // A real mod would refuse to load against an API it was not written for.
		event.enqueueWork(() -> {
			if(RadioBands.get("6m") == null) // Once, even if setup were to run twice.
				RadioBands.register(SIX_METERS, true, RadioBands.M10, "6m");
			AntennaTypes.registerType(GROUND_PLANE_ANTENNA);
		});
	}
}
