package com.arrl.radiocraft.client.events;


import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.entity.AntennaWireEntityRenderer;
import com.arrl.radiocraft.client.screens.*;
import com.arrl.radiocraft.client.screens.radios.*;
import com.arrl.radiocraft.common.init.RadiocraftEntityTypes;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.blockentities.DeskChargerBlockEntity;
import com.arrl.radiocraft.client.render.DeskChargerBlockRenderer;
import com.arrl.radiocraft.client.render.RadioDisplayRenderer;
import com.arrl.radiocraft.client.render.RadioDisplays;
import com.arrl.radiocraft.client.render.VHFReceiverRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid= Radiocraft.MOD_ID, value= Dist.CLIENT)
public class ClientSetupEvents {

	@SubscribeEvent
	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(RadiocraftMenuTypes.LARGE_BATTERY.get(), LargeBatteryScreen::new);
		event.register(RadiocraftMenuTypes.SOLAR_PANEL.get(), SolarPanelScreen::new);
		event.register(RadiocraftMenuTypes.CHARGE_CONTROLLER.get(), ChargeControllerScreen::new);
		event.register(RadiocraftMenuTypes.HF_RADIO_10M.get(), com.arrl.radiocraft.client.screens.radios.HfPanelScreen.of("hf_radio_10m")); // The new panel (2026-11-09); the old screen, HFRadio10mScreen, is on _old textures.
		event.register(RadiocraftMenuTypes.HF_RADIO_20M.get(), com.arrl.radiocraft.client.screens.radios.HfPanelScreen.of("hf_radio_20m")); // The new panel (2026-11-09); the old screen, HFRadio20mScreen, is on _old textures.
		event.register(RadiocraftMenuTypes.HF_RADIO_40M.get(), com.arrl.radiocraft.client.screens.radios.HfPanelScreen.of("hf_radio_40m")); // The new panel (2026-11-09); the old screen, HFRadio40mScreen, is on _old textures.
		event.register(RadiocraftMenuTypes.HF_RADIO_80M.get(), com.arrl.radiocraft.client.screens.radios.HfPanelScreen.of("hf_radio_80m")); // The new panel (2026-11-09); the old screen, HFRadio80mScreen, is on _old textures.
		event.register(RadiocraftMenuTypes.HF_RADIO_ALL_BAND.get(), com.arrl.radiocraft.client.screens.radios.HfPanelScreen.of("hf_radio_all_band")); // The new panel (2026-11-09); the old screen, HFRadioAllBandScreen, is on _old textures.
		event.register(RadiocraftMenuTypes.QRP_RADIO_20M.get(), QRPRadio20mScreen::new);
		event.register(RadiocraftMenuTypes.QRP_RADIO_40M.get(), QRPRadio40mScreen::new);
		event.register(RadiocraftMenuTypes.VHF_BASE_STATION.get(), com.arrl.radiocraft.client.screens.radios.HfPanelScreen.of("vhf_base_station")); // The new panel (2026-11-09); the old screen, VHFBaseStationScreen, is on _old textures.
		event.register(RadiocraftMenuTypes.COMMERCIAL_MOBILE.get(), CommercialMobileScreen::new);
		event.register(RadiocraftMenuTypes.MARINE_RADIO.get(), MarineRadioScreen::new);
		event.register(RadiocraftMenuTypes.CB_RADIO.get(), com.arrl.radiocraft.client.screens.radios.CbRadioScreen::new);
		event.register(RadiocraftMenuTypes.VHF_RECEIVER.get(), VHFReceiverScreen::new);
		event.register(RadiocraftMenuTypes.HF_RECEIVER.get(), HFReceiverScreen::new);
		event.register(RadiocraftMenuTypes.DESK_CHARGER.get(), DeskChargerScreen::new);
		event.register(RadiocraftMenuTypes.RADIO_INTERFACE.get(), RadioInterfaceScreen::new);
		event.register(RadiocraftMenuTypes.DTMF_DECODER.get(), DtmfDecoderScreen::new);
		event.register(RadiocraftMenuTypes.DTMF_ENCODER.get(), DtmfEncoderScreen::new);
		event.register(RadiocraftMenuTypes.TELETYPE.get(), TeletypeScreen::new);
		event.register(RadiocraftMenuTypes.APRS_TERMINAL.get(), AprsTerminalScreen::new);
		event.register(RadiocraftMenuTypes.RADIO_FAX.get(), RadioFaxScreen::new);
		event.register(RadiocraftMenuTypes.DUPLEXER.get(), DuplexerScreen::new);
		event.register(RadiocraftMenuTypes.ANTENNA_TUNER.get(), AntennaTunerScreen::new);
		event.register(RadiocraftMenuTypes.VHF_REPEATER.get(), VHFRepeaterScreen::new);
		event.register(RadiocraftMenuTypes.DIRECTION_FINDER.get(), DirectionFinderScreen::new);
		event.register(RadiocraftMenuTypes.MICROWAVE_RADIO.get(), MicrowaveRadioScreen::new);
		event.register(RadiocraftMenuTypes.TELEPHONE.get(), TelephoneScreen::new);
		event.register(RadiocraftMenuTypes.PABX.get(), com.arrl.radiocraft.client.screens.PabxScreen::new);
		event.register(RadiocraftMenuTypes.PHONE_PA.get(), com.arrl.radiocraft.client.screens.PhonePaScreen::new);
		event.register(RadiocraftMenuTypes.MODERATION_COMPUTER.get(), com.arrl.radiocraft.client.screens.moderation.ModerationComputerScreen::new);
		event.register(RadiocraftMenuTypes.RADIO_COMPUTER.get(), com.arrl.radiocraft.client.screens.computer.RadioComputerScreen::new);
		event.register(RadiocraftMenuTypes.SCANNER.get(), com.arrl.radiocraft.client.screens.ScannerScreen::new);
		event.register(RadiocraftMenuTypes.ADSB_BEACON.get(), com.arrl.radiocraft.client.screens.adsb.AdsbBeaconScreen::new);
		event.register(RadiocraftMenuTypes.ADSB_RECEIVER.get(), com.arrl.radiocraft.client.screens.adsb.AdsbReceiverScreen::new);
		event.register(RadiocraftMenuTypes.REAL_WORLD_INTERFACE.get(), com.arrl.radiocraft.client.screens.RealWorldInterfaceScreen::new);
		event.register(RadiocraftMenuTypes.REPEATER_AUTOPATCH.get(), com.arrl.radiocraft.client.screens.RepeaterAutopatchScreen::new);
		event.register(RadiocraftMenuTypes.IONOSPHERIC_PROCESSOR.get(), com.arrl.radiocraft.client.screens.IonosphericProcessorScreen::new);
		event.register(RadiocraftMenuTypes.TAPE_PLAYER.get(), com.arrl.radiocraft.client.screens.TapePlayerScreen::new);
		event.register(RadiocraftMenuTypes.WEFAX_RECEIVER.get(), com.arrl.radiocraft.client.screens.WefaxReceiverScreen::new);
		event.register(RadiocraftMenuTypes.COMMERCIAL_RADIO_MODULE.get(), com.arrl.radiocraft.client.screens.CommercialRadioModuleScreen::new);
	}
	
	@SubscribeEvent
	public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(com.arrl.radiocraft.common.init.RadiocraftBlockEntities.TAPE_PLAYER.get(), com.arrl.radiocraft.client.render.TapePlayerRenderer::new);
		event.registerBlockEntityRenderer(com.arrl.radiocraft.common.init.RadiocraftBlockEntities.WEATHER_STATION.get(), com.arrl.radiocraft.client.render.WeatherStationRenderer::new);
		if(com.arrl.radiocraft.compat.create.CreateCompat.isActive())
			com.arrl.radiocraft.compat.create.CreateAlternatorClient.registerRenderers(event);
		event.registerEntityRenderer(RadiocraftEntityTypes.ANTENNA_WIRE.get(), AntennaWireEntityRenderer::new);
		// Desk charger geo renderer
		// Cast to the concrete BlockEntityType generic to satisfy method signature
		// Use a lambda with an unchecked raw cast to avoid generic signature issues in the registration helper.
		event.registerBlockEntityRenderer((net.minecraft.world.level.block.entity.BlockEntityType) RadiocraftBlockEntities.DESK_CHARGER.get(), ctx -> new DeskChargerBlockRenderer(ctx));
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.PASSTHROUGH.get(), com.arrl.radiocraft.client.render.PassthroughRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.TELEPHONE.get(), com.arrl.radiocraft.client.render.TelephoneRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.MODERATION_COMPUTER.get(), com.arrl.radiocraft.client.render.ModerationComputerRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.COMPUTER_MONITOR.get(), com.arrl.radiocraft.client.render.ComputerMonitorRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.ADSB_RECEIVER.get(), com.arrl.radiocraft.client.render.AdsbScopeRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.FIELD_PHONE.get(), com.arrl.radiocraft.client.render.FieldPhoneRenderer::new);
		// Satellite dish geo renderer
		event.registerBlockEntityRenderer((net.minecraft.world.level.block.entity.BlockEntityType) RadiocraftBlockEntities.SATELLITE_DISH.get(), ctx -> new com.arrl.radiocraft.client.render.SatelliteDishRenderer(ctx));
		// The frequency, mode and S-meter on each radio's screen; a radio whose model has no DISPLAY draws nothing.
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.AUTO_TUNER.get(), com.arrl.radiocraft.client.render.AutoTunerDisplayRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.BATTERY_METER.get(), com.arrl.radiocraft.client.render.BatteryMeterRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.MICROWAVE_HORN.get(), com.arrl.radiocraft.client.render.MicrowaveHornRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.HF_RADIO_10M.get(), RadioDisplayRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.HF_RADIO_20M.get(), RadioDisplayRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.HF_RADIO_40M.get(), RadioDisplayRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.HF_RADIO_80M.get(), RadioDisplayRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.HF_RADIO_ALL_BAND.get(), RadioDisplayRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.HF_RECEIVER.get(), RadioDisplayRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.VHF_BASE_STATION.get(), RadioDisplayRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.COMMERCIAL_MOBILE.get(), RadioDisplayRenderer::new); // Channel display, lamps, mic cord.
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.MARINE_RADIO.get(), RadioDisplayRenderer::new);
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.CB_RADIO.get(), com.arrl.radiocraft.client.render.CbRadioRenderer::new); // Channel, clock, meters, lamps, mic cord.
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.VHF_RECEIVER.get(), VHFReceiverRenderer::new); // No display: switches, the lit numeral, SET (2026-11-09).
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.QRP_RADIO_20M.get(), RadioDisplayRenderer::new); // No screen; draws the mic cord.
		event.registerBlockEntityRenderer(RadiocraftBlockEntities.QRP_RADIO_40M.get(), RadioDisplayRenderer::new);
	}

	/** Radio screens are read from their models (see {@link RadioDisplays}): read them afresh after a resource reload. */
	@SubscribeEvent
	public static void registerReloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
		event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager -> RadioDisplays.clear());
	}

	/** The handheld direction finder's lamp blinks with its beeps (see {@link com.arrl.radiocraft.client.HandheldDfClient}). */
	@SubscribeEvent
	public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
		// The Config button in the mod list: NeoForge's own screen over the client, common and server (in a world) settings.
		// The Real-World Interface's settings are left off it: they are set only in the server config file (maintainer,
		// 2026-11-04), so nobody wires a sound card to the air from a menu.
		net.neoforged.fml.ModList.get().getModContainerById(Radiocraft.MOD_ID).ifPresent(container -> container.registerExtensionPoint(
				net.neoforged.neoforge.client.gui.IConfigScreenFactory.class, (net.neoforged.neoforge.client.gui.IConfigScreenFactory)(mod, parent) ->
						new net.neoforged.neoforge.client.gui.ConfigurationScreen(mod, parent, (context, key, original) -> key.startsWith("real_world_interface") ? null : original)));
		if(com.arrl.radiocraft.compat.create.CreateCompat.isActive())
			event.enqueueWork(com.arrl.radiocraft.compat.create.CreateAlternatorClient::registerVisual);
		if(com.arrl.radiocraft.compat.curios.CuriosCompat.isLoaded()) // The handheld on the belt.
			event.enqueueWork(() -> {
				com.arrl.radiocraft.compat.curios.HandheldBeltRenderer.register();
				com.arrl.radiocraft.compat.curios.CbPortableBackRenderer.register(); // The CB portable on the back slot.
				com.arrl.radiocraft.compat.curios.FccBadgeRenderer.register(); // The FCC badge on the chest or belt (2026-11-09).
			});
		event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(com.arrl.radiocraft.common.init.RadiocraftItems.HANDHELD_DIRECTION_FINDER.get(),
				Radiocraft.id("lit"), (stack, level, entity, seed) -> entity == net.minecraft.client.Minecraft.getInstance().player && com.arrl.radiocraft.client.HandheldDfClient.isLit() ? 1.0F : 0.0F));
		// The CB portable's whip is pulled out while it is on.
		event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(com.arrl.radiocraft.common.init.RadiocraftItems.CB_PORTABLE.get(),
				Radiocraft.id("extended"), (stack, level, entity, seed) -> com.arrl.radiocraft.common.items.CbPortableItem.isOn(stack) ? 1.0F : 0.0F));
		// The distress beacon's strobe is lit while it transmits (2026-11-09; models/item/distress_beacon.json).
		event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(com.arrl.radiocraft.common.init.RadiocraftItems.DISTRESS_BEACON.get(),
				Radiocraft.id("active"), (stack, level, entity, seed) -> com.arrl.radiocraft.common.items.DistressBeaconItem.isActive(stack) ? 1.0F : 0.0F));
	}

	/** Tape reels: the reel and its label tinted by the tape's colourway. */
	@SubscribeEvent
	public static void registerItemColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
		event.register((stack, tint) -> tint == 1 ? com.arrl.radiocraft.common.items.TapeItem.reelColour(stack)
				: tint == 2 ? com.arrl.radiocraft.common.items.TapeItem.labelColour(stack) : -1, com.arrl.radiocraft.common.init.RadiocraftItems.TAPE_REEL.get());
		// Commercial radios: the case (tint 0) takes dye like leather armour. The handhelds' 3D models tint only their
		// case faces (light grey); undyed, each model gets its own colour (the basic's grey, the upgraded's black).
		event.register((stack, tint) -> tint == 0 ? com.arrl.radiocraft.common.items.CommercialHandheldItem.caseColour(stack) : -1,
				com.arrl.radiocraft.common.init.RadiocraftItems.BASIC_COMMERCIAL_HANDHELD.get(), com.arrl.radiocraft.common.init.RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD.get());
		event.register((stack, tint) -> tint == 0 ? net.minecraft.world.item.component.DyedItemColor.getOrDefault(stack, com.arrl.radiocraft.common.items.CommercialHandheldItem.DEFAULT_CASE_COLOUR) : -1,
				com.arrl.radiocraft.common.init.RadiocraftItems.COMMERCIAL_MOBILE.get(), com.arrl.radiocraft.common.init.RadiocraftItems.LOW_BAND_MOBILE.get());
		// Floppy disks: one texture, the label (tint 1) in each program's colour.
		event.register((stack, tint) -> tint == 1 ? com.arrl.radiocraft.client.screens.computer.ComputerAppScreens.labelColour(stack) : -1,
				com.arrl.radiocraft.common.init.RadiocraftItems.FLOPPIES.values().stream().map(net.neoforged.neoforge.registries.DeferredHolder::get).toArray(net.minecraft.world.level.ItemLike[]::new));
	}

	/** A placed commercial mobile's case in its dyed colour (the block entity holds it). */
	@SubscribeEvent
	public static void registerBlockColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
		event.register((state, level, pos, tint) -> tint == 0 && level != null && pos != null
						&& level.getBlockEntity(pos) instanceof com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity mobile
						? mobile.getCaseColour() : tint == 0 ? com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity.DEFAULT_CASE_COLOUR : -1,
				com.arrl.radiocraft.common.init.RadiocraftBlocks.COMMERCIAL_MOBILE.get(), com.arrl.radiocraft.common.init.RadiocraftBlocks.LOW_BAND_MOBILE.get());
	}

	/** The HF manpack on a player's back is drawn from its block model, which is loaded on its own for that. */
	@SubscribeEvent
	public static void registerAdditionalModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
		event.register(com.arrl.radiocraft.client.render.ManpackLayer.MODEL);
		event.register(com.arrl.radiocraft.client.render.WeatherStationRenderer.VANE);
		event.register(com.arrl.radiocraft.client.render.WeatherStationRenderer.CUPS);
		event.register(com.arrl.radiocraft.client.render.TapePlayerRenderer.REEL);
		event.register(com.arrl.radiocraft.client.render.HandheldItemRenderer.RX_LED);
	}

	/** The VHF handheld's item models hand their drawing to {@link com.arrl.radiocraft.client.render.HandheldItemRenderer} (its live LCD). */
	@SubscribeEvent
	public static void modifyBakedModels(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
		com.arrl.radiocraft.client.render.HandheldItemRenderer.wrapModels(event.getModels());
	}

	/** A telephone handset raises its holder's arm to the face (vanilla's goat horn pose), the handset by the ear. */
	@SubscribeEvent
	public static void registerClientExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
		event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
			@Override
			public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.InteractionHand hand, net.minecraft.world.item.ItemStack stack) {
				return net.minecraft.client.model.HumanoidModel.ArmPose.TOOT_HORN;
			}
		}, com.arrl.radiocraft.common.init.RadiocraftItems.TELEPHONE_HANDSET.get(), com.arrl.radiocraft.common.init.RadiocraftItems.FIELD_PHONE_HANDSET.get());
		event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() { // The handheld's live LCD.
			@Override
			public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return com.arrl.radiocraft.client.render.HandheldItemRenderer.get();
			}
		}, com.arrl.radiocraft.common.init.RadiocraftItems.VHF_HANDHELD_VARIANTS.values().toArray(net.minecraft.world.item.Item[]::new));
		event.registerItem(com.arrl.radiocraft.client.render.FccJacketModel.extensions(), com.arrl.radiocraft.common.init.RadiocraftItems.FCC_JACKET.get()); // Cut close to the body (2026-11-09).
	}

	@SubscribeEvent
	public static void addLayers(EntityRenderersEvent.AddLayers event) {
		for(net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
			if(event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
				renderer.addLayer(new com.arrl.radiocraft.client.render.ManpackLayer(renderer));
				renderer.addLayer(new com.arrl.radiocraft.client.render.PhoneEarLayer(renderer));
			}
		}
	}

	/** The wire model works its look out from the neighbours, see {@link com.arrl.radiocraft.client.model.WireModel}. */
	@net.neoforged.bus.api.SubscribeEvent
	public static void registerGeometryLoaders(net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders event) {
		event.register(com.arrl.radiocraft.client.model.WireModel.LOADER_ID, com.arrl.radiocraft.client.model.WireModel.Loader.INSTANCE);
		event.register(com.arrl.radiocraft.client.model.TowerBeamModel.LOADER_ID, com.arrl.radiocraft.client.model.TowerBeamModel.Loader.INSTANCE);
	}
}
