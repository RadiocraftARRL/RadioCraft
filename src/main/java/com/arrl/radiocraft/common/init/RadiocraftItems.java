package com.arrl.radiocraft.common.init;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.datacomponents.HandheldRadioState;
import com.arrl.radiocraft.common.items.*;
import com.arrl.radiocraft.common.items.DeskChargerBlockItem;
import com.arrl.radiocraft.common.radio.crystal.CrystalBand;
import com.arrl.radiocraft.common.radio.crystal.CrystalQuality;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry class for Radiocraft's items
 */
public class RadiocraftItems {

	public static final DeferredRegister<Item> ITEMS = DeferredRegister.createItems(Radiocraft.MOD_ID);

	// Regular Items
	// Raw quartz crystals (dug out of sand) and the encased crystals radios are built with: one per band and quality.
	public static final Map<CrystalBand, Map<CrystalQuality, Supplier<Item>>> CRYSTALS = crystals("crystal");
	public static final Map<CrystalBand, Map<CrystalQuality, Supplier<Item>>> ENCASED_CRYSTALS = crystals("encased_crystal");
	public static final Supplier<Item> RADIO_SPEAKER = simpleItem("radio_speaker", null);
	public static final Supplier<Item> HAND_MICROPHONE = simpleItem("hand_microphone", null);
	/** A radio's hand mic taken out on its cord (not craftable: sneak-use a radio). Looks like the hand microphone. */
	public static final DeferredHolder<Item, com.arrl.radiocraft.common.items.CordedMicrophoneItem> CORDED_MICROPHONE = ITEMS.register("corded_microphone",
			() -> new com.arrl.radiocraft.common.items.CordedMicrophoneItem(new Properties()));
	public static final Supplier<Item> HF_CIRCUIT_BOARD = simpleItem("hf_circuit_board", null);
	public static final Supplier<Item> ALL_BAND_CIRCUIT_BOARD = simpleItem("all_band_circuit_board", null);

	public static final DeferredHolder<Item, SmallBatteryItem> SMALL_BATTERY = ITEMS.register("small_battery", () -> new SmallBatteryItem(new Properties()
			.stacksTo(1)
	));
	
	public static final Supplier<Item> FERRITE_CORE = simpleItem("ferrite_core", null);
	public static final Supplier<Item> COAXIAL_CORE = simpleItem("coaxial_core", null);
	public static final Supplier<Item> INSULATION = simpleItem("insulation", null); // Stands in for rubber: wire jackets, coax.
	public static final Supplier<Item> MICROPHONE_ELEMENT = simpleItem("microphone_element", null);
	/** A reel of tape for the tape player: blank, a server tape found in a structure, or a recording. */
	public static final Supplier<Item> TAPE_REEL = ITEMS.register("tape_reel", () -> new com.arrl.radiocraft.common.items.TapeItem(new Properties())); // A carbon capsule: the heart of every microphone.
	public static final Supplier<Item> ANTENNA_ANALYZER = ITEMS.register("antenna_analyzer", () -> new AntennaAnalyzerItem(new Properties().stacksTo(1)));
	public static final CrystalVariants<Item, VHFHandheldItem> VHF_HANDHELD_VARIANTS = CrystalVariants.register(ITEMS, "vhf_handheld", crystal -> new VHFHandheldItem(new Properties().stacksTo(1).component(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value(), HandheldRadioState.getDefault()), crystal));
	public static final DeferredHolder<Item, VHFHandheldItem> VHF_HANDHELD = VHF_HANDHELD_VARIANTS.good();
	/** The commercial handhelds (1.12): a cheap 1990s brick, and the fire-service radio with a display, digital and encryption. */
	public static final DeferredHolder<Item, CommercialHandheldItem> BASIC_COMMERCIAL_HANDHELD = commercialHandheld("basic_commercial_handheld",
			com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel.BASIC_HANDHELD);
	public static final DeferredHolder<Item, CommercialHandheldItem> UPGRADED_COMMERCIAL_HANDHELD = commercialHandheld("upgraded_commercial_handheld",
			com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel.UPGRADED_HANDHELD);
	/** The VHF marine handheld (1.12): channel-only, 5 W / 1 W, 16/9 and dual watch. */
	public static final DeferredHolder<Item, com.arrl.radiocraft.common.items.MarineHandheldItem> MARINE_HANDHELD = ITEMS.register("marine_handheld",
			() -> new com.arrl.radiocraft.common.items.MarineHandheldItem(new Properties().stacksTo(1)
					.component(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value(), marineHandheldState())
					.component(RadiocraftDataComponent.MARINE_RADIO.value(), com.arrl.radiocraft.common.radio.marine.MarineRadioState.DEFAULT)));
	/** Crafted into a VHF handheld: the handheld's APRS button then opens its APRS menu. */
	public static final Supplier<Item> APRS_MODULE = ITEMS.register("aprs_module", () -> new com.arrl.radiocraft.common.items.AprsModuleItem(new Properties()));
	/** Opens a ham radio's transmitter past the band edges; crafted with the radio. Contraband for amateurs. */
	public static final Supplier<Item> MARS_MOD = ITEMS.register("mars_mod", () -> new com.arrl.radiocraft.common.items.MarsModItem(new Properties()));
	/** Reads one radio's programming and writes it into others (1.12 playtest fixes 2). */
	public static final Supplier<Item> RADIO_CLONER = ITEMS.register("radio_cloner", () -> new com.arrl.radiocraft.common.items.RadioClonerItem(new Properties().stacksTo(1)));
	/** The band plan sheet: right click to read what each part of each band is for. */
	public static final Supplier<Item> BAND_PLAN = ITEMS.register("band_plan", () -> new com.arrl.radiocraft.common.items.BandPlanItem(new Properties().stacksTo(16)));
	/** The camera (1.12): right click prints what you see on a sheet of photo paper (see {@link com.arrl.radiocraft.common.photo.PhotoPrints}). */
	public static final DeferredHolder<Item, CameraItem> CAMERA = ITEMS.register("camera", () -> new CameraItem(new Properties().stacksTo(1)));
	/** Black and white photo paper: the camera prints greys on it. */
	public static final Supplier<Item> PHOTO_PAPER = simpleItem("photo_paper", null);
	/** Colour photo paper. */
	public static final Supplier<Item> COLOR_PHOTO_PAPER = simpleItem("color_photo_paper", null);
	/** A blank 3.5" floppy disk (1.12): crafted into a program's disk for the radio computer. */
	public static final DeferredHolder<Item, FloppyDiskItem> FLOPPY_DISK = ITEMS.register("floppy_disk", () -> new FloppyDiskItem(new Properties().stacksTo(16), null));
	/**
	 * One floppy disk per radio computer program that is not built in, by app id ({@code floppy_<id>}; see
	 * {@link com.arrl.radiocraft.common.computer.ComputerApps}). The secret ones (found in structures) are rare.
	 */
	public static final Map<String, DeferredHolder<Item, FloppyDiskItem>> FLOPPIES = floppies();
	// FCC field gear (1.12): a 1970s field agent's hat and jacket (leather's protection), the seal banner pattern, and
	// the badge only operators hand out (/radiocraft fcc badge; see com.arrl.radiocraft.common.fcc.FccGear).
	public static final DeferredHolder<Item, net.minecraft.world.item.ArmorItem> FCC_HAT = ITEMS.register("fcc_hat",
			() -> new net.minecraft.world.item.ArmorItem(RadiocraftArmorMaterials.FCC, net.minecraft.world.item.ArmorItem.Type.HELMET,
					new Properties().durability(net.minecraft.world.item.ArmorItem.Type.HELMET.getDurability(RadiocraftArmorMaterials.FCC_DURABILITY))));
	public static final DeferredHolder<Item, net.minecraft.world.item.ArmorItem> FCC_JACKET = ITEMS.register("fcc_jacket",
			() -> new net.minecraft.world.item.ArmorItem(RadiocraftArmorMaterials.FCC, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
					new Properties().durability(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE.getDurability(RadiocraftArmorMaterials.FCC_DURABILITY))));
	public static final DeferredHolder<Item, net.minecraft.world.item.BannerPatternItem> FCC_BANNER_PATTERN = ITEMS.register("fcc_banner_pattern",
			() -> new net.minecraft.world.item.BannerPatternItem(com.arrl.radiocraft.common.fcc.FccGear.PATTERN_ITEM, new Properties().stacksTo(1)));
	/** Not craftable and not in the creative tab: an operator issues it with {@code /radiocraft fcc badge}. */
	public static final DeferredHolder<Item, FccBadgeItem> FCC_BADGE = ITEMS.register("fcc_badge",
			() -> new FccBadgeItem(new Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()));
	public static final DeferredHolder<Item, AntennaWireItem> ANTENNA_WIRE = ITEMS.register("antenna_wire", () -> new AntennaWireItem(new Properties()));
	public static final DeferredHolder<Item, PassthroughItem> PASSTHROUGH = ITEMS.register("passthrough", () -> new PassthroughItem(new Properties()));

	// Block Items
	public static final Supplier<BlockItem> WIRE = simpleBlockItem("wire", RadiocraftBlocks.WIRE);
	public static final Supplier<BlockItem> WATERPROOF_WIRE = simpleBlockItem("waterproof_wire", RadiocraftBlocks.WATERPROOF_WIRE);
	public static final Supplier<BlockItem> DATA_WIRE = simpleBlockItem("data_wire", RadiocraftBlocks.DATA_WIRE);
	public static final java.util.Map<net.minecraft.world.item.DyeColor, Supplier<BlockItem>> COLOURED_DATA_WIRE = colouredDataWire();

	private static java.util.Map<net.minecraft.world.item.DyeColor, Supplier<BlockItem>> colouredDataWire() {
		java.util.Map<net.minecraft.world.item.DyeColor, Supplier<BlockItem>> out = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
		RadiocraftBlocks.COLOURED_DATA_WIRE.forEach((colour, block) -> out.put(colour, simpleBlockItem("data_wire_" + colour.getSerializedName(), block)));
		return out;
	}
	public static final java.util.Map<net.minecraft.world.item.DyeColor, Supplier<BlockItem>> COLOURED_WIRE = colouredWire();

	private static java.util.Map<net.minecraft.world.item.DyeColor, Supplier<BlockItem>> colouredWire() {
		java.util.Map<net.minecraft.world.item.DyeColor, Supplier<BlockItem>> out = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
		RadiocraftBlocks.COLOURED_WIRE.forEach((colour, block) -> out.put(colour, simpleBlockItem("wire_" + colour.getSerializedName(), block)));
		return out;
	}
	public static final Supplier<BlockItem> SOLAR_PANEL = simpleBlockItem("solar_panel", RadiocraftBlocks.SOLAR_PANEL);
	public static final Supplier<BlockItem> LARGE_BATTERY = simpleBlockItem("large_battery", RadiocraftBlocks.LARGE_BATTERY);
	public static final Supplier<BlockItem> CHARGE_CONTROLLER = simpleBlockItem("charge_controller", RadiocraftBlocks.CHARGE_CONTROLLER);

	public static final CrystalVariants<Item, BlockItem> VHF_BASE_STATION_VARIANTS = crystalBlockItems("vhf_base_station", RadiocraftBlocks.VHF_BASE_STATION_VARIANTS);
	public static final Supplier<BlockItem> VHF_BASE_STATION = VHF_BASE_STATION_VARIANTS.good();
	public static final Supplier<BlockItem> VHF_RECEIVER = simpleBlockItem("vhf_receiver", RadiocraftBlocks.VHF_RECEIVER);
	public static final Supplier<BlockItem> VHF_REPEATER = simpleBlockItem("vhf_repeater", RadiocraftBlocks.VHF_REPEATER);
	public static final Supplier<BlockItem> COMMERCIAL_MOBILE = ITEMS.register("commercial_mobile", () -> new BlockItem(RadiocraftBlocks.COMMERCIAL_MOBILE.get(), new Properties().stacksTo(1)));
	public static final Supplier<BlockItem> LOW_BAND_MOBILE = ITEMS.register("low_band_mobile", () -> new BlockItem(RadiocraftBlocks.LOW_BAND_MOBILE.get(), new Properties().stacksTo(1)));
	public static final Supplier<BlockItem> MARINE_RADIO = ITEMS.register("marine_radio", () -> new BlockItem(RadiocraftBlocks.MARINE_RADIO.get(), new Properties().stacksTo(1)));
	public static final Supplier<BlockItem> COMMERCIAL_REPEATER = simpleBlockItem("commercial_repeater", RadiocraftBlocks.COMMERCIAL_REPEATER);
	public static final Supplier<BlockItem> COMMERCIAL_RADIO_MODULE = simpleBlockItem("commercial_radio_module", RadiocraftBlocks.COMMERCIAL_RADIO_MODULE);
	public static final Supplier<BlockItem> TAPE_PLAYER = simpleBlockItem("tape_player", RadiocraftBlocks.TAPE_PLAYER);

	public static final CrystalVariants<Item, BlockItem> HF_RADIO_10M_VARIANTS = crystalBlockItems("hf_radio_10m", RadiocraftBlocks.HF_RADIO_10M_VARIANTS);
	public static final Supplier<BlockItem> HF_RADIO_10M = HF_RADIO_10M_VARIANTS.good();
	public static final CrystalVariants<Item, BlockItem> HF_RADIO_20M_VARIANTS = crystalBlockItems("hf_radio_20m", RadiocraftBlocks.HF_RADIO_20M_VARIANTS);
	public static final Supplier<BlockItem> HF_RADIO_20M = HF_RADIO_20M_VARIANTS.good();
	public static final CrystalVariants<Item, BlockItem> HF_RADIO_40M_VARIANTS = crystalBlockItems("hf_radio_40m", RadiocraftBlocks.HF_RADIO_40M_VARIANTS);
	public static final Supplier<BlockItem> HF_RADIO_40M = HF_RADIO_40M_VARIANTS.good();
	public static final CrystalVariants<Item, BlockItem> HF_RADIO_80M_VARIANTS = crystalBlockItems("hf_radio_80m", RadiocraftBlocks.HF_RADIO_80M_VARIANTS);
	public static final Supplier<BlockItem> HF_RADIO_80M = HF_RADIO_80M_VARIANTS.good();
	public static final Supplier<BlockItem> HF_RECEIVER = simpleBlockItem("hf_receiver", RadiocraftBlocks.HF_RECEIVER);

	public static final Supplier<BlockItem> ALL_BAND_RADIO = simpleBlockItem("all_band_radio", RadiocraftBlocks.ALL_BAND_RADIO);
	public static final Supplier<BlockItem> QRP_RADIO_20M = simpleBlockItem("qrp_radio_20m", RadiocraftBlocks.QRP_RADIO_20M);
	public static final Supplier<BlockItem> QRP_RADIO_40M = simpleBlockItem("qrp_radio_40m", RadiocraftBlocks.QRP_RADIO_40M);

	public static final Supplier<BlockItem> RADIO_INTERFACE = simpleBlockItem("radio_interface", RadiocraftBlocks.RADIO_INTERFACE);
	public static final Supplier<BlockItem> DTMF_DECODER = simpleBlockItem("dtmf_decoder", RadiocraftBlocks.DTMF_DECODER);
	public static final Supplier<BlockItem> DTMF_ENCODER = simpleBlockItem("dtmf_encoder", RadiocraftBlocks.DTMF_ENCODER);
	public static final Supplier<BlockItem> APRS_TERMINAL = simpleBlockItem("aprs_h19", RadiocraftBlocks.APRS_TERMINAL);
	public static final Map<String, Supplier<BlockItem>> RADIO_FAXES = radioFaxes();
	/** A roll torn off a teletype, with everything it printed. */
	public static final Supplier<Item> TELETYPE_ROLL = ITEMS.register("teletype_roll", () -> new com.arrl.radiocraft.common.items.TeletypeRollItem(new Properties()));
	public static final Supplier<BlockItem> PARROT_CONTROLLER = simpleBlockItem("parrot_controller", RadiocraftBlocks.PARROT_CONTROLLER);

	// TODO: This may be supposed to be a BlockItem, but this compiles so meh?
	public static final DeferredHolder<Item, AntennaPoleItem> ANTENNA_POLE = ITEMS.register("antenna_pole", () -> new AntennaPoleItem(RadiocraftBlocks.ANTENNA_POLE.get(), new Item.Properties()));
	public static final Supplier<BlockItem> WIRE_JUNCTION = simpleBlockItem("wire_junction", RadiocraftBlocks.WIRE_JUNCTION);
	public static final Supplier<BlockItem> DUPLEXER = simpleBlockItem("duplexer", RadiocraftBlocks.DUPLEXER);
	public static final Supplier<BlockItem> ANTENNA_SPLITTER = simpleBlockItem("antenna_splitter", RadiocraftBlocks.ANTENNA_SPLITTER);
	public static final Supplier<BlockItem> ANTENNA_SWITCH = simpleBlockItem("antenna_switch", RadiocraftBlocks.ANTENNA_SWITCH);
	public static final Supplier<BlockItem> ANTENNA_TUNER = simpleBlockItem("antenna_tuner", RadiocraftBlocks.ANTENNA_TUNER);
	public static final Supplier<BlockItem> AUTO_TUNER = simpleBlockItem("auto_tuner", RadiocraftBlocks.AUTO_TUNER);
	public static final Supplier<BlockItem> ANTENNA_CONNECTOR = simpleBlockItem("antenna_connector", RadiocraftBlocks.ANTENNA_CONNECTOR);
	public static final Supplier<BlockItem> BALUN_ONE_TO_ONE = simpleBlockItem("balun_one_to_one", RadiocraftBlocks.BALUN_ONE_TO_ONE);
	public static final Supplier<BlockItem> BALUN_FOUR_TO_ONE = simpleBlockItem("balun_four_to_one", RadiocraftBlocks.BALUN_FOUR_TO_ONE);
	public static final Supplier<BlockItem> BALUN_TWO_TO_ONE = simpleBlockItem("balun_two_to_one", RadiocraftBlocks.BALUN_TWO_TO_ONE);
	public static final Supplier<BlockItem> BALUN_NINE_TO_ONE = simpleBlockItem("balun_nine_to_one", RadiocraftBlocks.BALUN_NINE_TO_ONE);
	public static final Supplier<BlockItem> COAX_WIRE = simpleBlockItem("coax_wire", RadiocraftBlocks.COAX_WIRE);
	public static final java.util.Map<net.minecraft.world.item.DyeColor, Supplier<BlockItem>> COLOURED_COAX = colouredCoax();

	private static java.util.Map<net.minecraft.world.item.DyeColor, Supplier<BlockItem>> colouredCoax() {
		java.util.Map<net.minecraft.world.item.DyeColor, Supplier<BlockItem>> out = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
		RadiocraftBlocks.COLOURED_COAX.forEach((colour, block) -> out.put(colour, simpleBlockItem("coax_wire_" + colour.getSerializedName(), block)));
		return out;
	}
	public static final Supplier<BlockItem> YAGI_ANTENNA = simpleBlockItem("yagi_antenna", RadiocraftBlocks.YAGI_ANTENNA);
	public static final Supplier<BlockItem> DUMMY_LOAD = simpleBlockItem("dummy_load", RadiocraftBlocks.DUMMY_LOAD);
	public static final Supplier<BlockItem> J_POLE_ANTENNA = simpleBlockItem("j_pole_antenna", RadiocraftBlocks.J_POLE_ANTENNA);
	public static final Supplier<BlockItem> RINGO_ANTENNA = simpleBlockItem("ringo_antenna", RadiocraftBlocks.RINGO_ANTENNA);
	public static final Supplier<BlockItem> FOLDED_DIPOLE_ANTENNA = simpleBlockItem("folded_dipole_antenna", RadiocraftBlocks.FOLDED_DIPOLE_ANTENNA);
	public static final Supplier<BlockItem> IONOSONDE = simpleBlockItem("ionosonde", RadiocraftBlocks.IONOSONDE);
	public static final Supplier<BlockItem> FIVE_BTV_ANTENNA = simpleBlockItem("5btv_antenna", RadiocraftBlocks.FIVE_BTV_ANTENNA);
	public static final Supplier<BlockItem> IONOSPHERIC_PROCESSOR = simpleBlockItem("ionospheric_processor", RadiocraftBlocks.IONOSPHERIC_PROCESSOR);
	public static final Supplier<BlockItem> SLIM_JIM_ANTENNA = simpleBlockItem("slim_jim_antenna", RadiocraftBlocks.SLIM_JIM_ANTENNA);

	public static final Supplier<BlockItem> TELETYPE = simpleBlockItem("teletype", RadiocraftBlocks.TELETYPE);
	public static final Supplier<BlockItem> MICROPHONE = simpleBlockItem("microphone", RadiocraftBlocks.MICROPHONE);
	public static final Supplier<BlockItem> WEFAX_RECEIVER = simpleBlockItem("wefax_receiver", RadiocraftBlocks.WEFAX_RECEIVER);
	public static final Supplier<BlockItem> TOWER_FOOTHOLD = ITEMS.register("tower_foothold", () -> new com.arrl.radiocraft.common.items.TowerFootholdItem(RadiocraftBlocks.TOWER_FOOTHOLD.get(), new Properties()));
	public static final Supplier<BlockItem> OLD_TOWER_FOOTHOLD = ITEMS.register("old_tower_foothold", () -> new com.arrl.radiocraft.common.items.TowerFootholdItem(RadiocraftBlocks.OLD_TOWER_FOOTHOLD.get(), new Properties()));

	public static final Supplier<BlockItem> TOWER_BEAM_THIN = simpleBlockItem("tower_beam_thin", RadiocraftBlocks.TOWER_BEAM_THIN);
	public static final Supplier<BlockItem> TOWER_BEACON = simpleBlockItem("tower_beacon", RadiocraftBlocks.TOWER_BEACON);
	public static final Supplier<BlockItem> TOWER_BEACON_CONTROLLER = simpleBlockItem("tower_beacon_controller", RadiocraftBlocks.TOWER_BEACON_CONTROLLER);
	public static final Supplier<BlockItem> TOWER_BEAM = simpleBlockItem("tower_beam", RadiocraftBlocks.TOWER_BEAM);
	public static final Supplier<BlockItem> TOWER_BEAM_THICK = simpleBlockItem("tower_beam_thick", RadiocraftBlocks.TOWER_BEAM_THICK);
	public static final Supplier<BlockItem> MICROWAVE_HORN = simpleBlockItem("microwave_horn", RadiocraftBlocks.MICROWAVE_HORN);
	public static final Map<String, Supplier<BlockItem>> DESK_SPEAKERS = deskSpeakers();
	public static final Supplier<BlockItem> LARGE_SPEAKER = simpleBlockItem("large_speaker", RadiocraftBlocks.LARGE_SPEAKER);
	public static final Supplier<BlockItem> PREMIUM_SPEAKER_MARBLE = simpleBlockItem("premium_speaker_marble", RadiocraftBlocks.PREMIUM_SPEAKER_MARBLE);
	public static final Supplier<BlockItem> PREMIUM_SPEAKER_IRON = simpleBlockItem("premium_speaker_iron", RadiocraftBlocks.PREMIUM_SPEAKER_IRON);
	public static final Supplier<BlockItem> PREMIUM_SPEAKER_GOLD = simpleBlockItem("premium_speaker_gold", RadiocraftBlocks.PREMIUM_SPEAKER_GOLD);
	public static final Supplier<BlockItem> WALL_SPEAKER = simpleBlockItem("wall_speaker", RadiocraftBlocks.WALL_SPEAKER);
	public static final Supplier<BlockItem> LOUDSPEAKER = simpleBlockItem("loudspeaker", RadiocraftBlocks.LOUDSPEAKER);
	public static final Supplier<BlockItem> DIRECTION_FINDER = simpleBlockItem("direction_finder", RadiocraftBlocks.DIRECTION_FINDER);
	public static final Supplier<BlockItem> MAP_DIRECTION_FINDER = simpleBlockItem("map_direction_finder", RadiocraftBlocks.MAP_DIRECTION_FINDER);
	public static final Supplier<BlockItem> DIRECTION_FINDER_MODULE = simpleBlockItem("direction_finder_module", RadiocraftBlocks.DIRECTION_FINDER_MODULE);
	public static final Supplier<BlockItem> MICROWAVE_RADIO = simpleBlockItem("microwave_radio", RadiocraftBlocks.MICROWAVE_RADIO);
	public static final Supplier<BlockItem> TELEPHONE = simpleBlockItem("telephone", RadiocraftBlocks.TELEPHONE);
	public static final Supplier<BlockItem> PABX = simpleBlockItem("pabx", RadiocraftBlocks.PABX);
	public static final Supplier<BlockItem> PHONE_PA = simpleBlockItem("phone_pa", RadiocraftBlocks.PHONE_PA);
	public static final Supplier<BlockItem> MODERATION_COMPUTER = simpleBlockItem("moderation_computer", RadiocraftBlocks.MODERATION_COMPUTER);
	public static final Supplier<BlockItem> RADIO_COMPUTER = simpleBlockItem("radio_computer", RadiocraftBlocks.RADIO_COMPUTER);
	public static final Supplier<BlockItem> COMPUTER_MONITOR = simpleBlockItem("computer_monitor", RadiocraftBlocks.COMPUTER_MONITOR);
	public static final Supplier<BlockItem> SCANNER = simpleBlockItem("scanner", RadiocraftBlocks.SCANNER);
	public static final Supplier<BlockItem> ADSB_BEACON = simpleBlockItem("adsb_beacon", RadiocraftBlocks.ADSB_BEACON);
	public static final Supplier<BlockItem> ADSB_RECEIVER = simpleBlockItem("adsb_receiver", RadiocraftBlocks.ADSB_RECEIVER);
	public static final Supplier<BlockItem> ADSB_ANTENNA = simpleBlockItem("adsb_antenna", RadiocraftBlocks.ADSB_ANTENNA);
	public static final Supplier<BlockItem> WEATHER_ALERT_GENERATOR = simpleBlockItem("weather_alert_generator", RadiocraftBlocks.WEATHER_ALERT_GENERATOR);
	public static final Supplier<BlockItem> WEATHER_ALERT_RECEIVER = simpleBlockItem("weather_alert_receiver", RadiocraftBlocks.WEATHER_ALERT_RECEIVER);
	public static final Supplier<BlockItem> REAL_WORLD_INTERFACE = simpleBlockItem("real_world_interface", RadiocraftBlocks.REAL_WORLD_INTERFACE);
	public static final Supplier<BlockItem> REPEATER_AUTOPATCH = simpleBlockItem("repeater_autopatch", RadiocraftBlocks.REPEATER_AUTOPATCH);
	/** A telephone's handset while it is off the hook; never crafted or in a creative tab. */
	public static final DeferredHolder<Item, com.arrl.radiocraft.common.items.TelephoneHandsetItem> TELEPHONE_HANDSET = ITEMS.register("telephone_handset",
			() -> new com.arrl.radiocraft.common.items.TelephoneHandsetItem(new Properties()));
	public static final Supplier<BlockItem> FIELD_PHONE = simpleBlockItem("field_phone", RadiocraftBlocks.FIELD_PHONE);
	/** A field phone's handset while it is off the hook; never crafted or in a creative tab. */
	public static final DeferredHolder<Item, com.arrl.radiocraft.common.items.TelephoneHandsetItem> FIELD_PHONE_HANDSET = ITEMS.register("field_phone_handset",
			() -> new com.arrl.radiocraft.common.items.TelephoneHandsetItem(new Properties()));
	public static final Supplier<BlockItem> WEATHER_STATION = simpleBlockItem("weather_station", RadiocraftBlocks.WEATHER_STATION);
	/** A floppy with the world on it: crafted into a direction finder it makes the map enabled one. Cartographers sell it. */
	public static final DeferredHolder<Item, HandheldDirectionFinderItem> HANDHELD_DIRECTION_FINDER = ITEMS.register("handheld_direction_finder",
			() -> new HandheldDirectionFinderItem(new Properties().stacksTo(1)));
	public static final DeferredHolder<Item, HFManpackItem> HF_MANPACK = ITEMS.register("hf_manpack", () -> new HFManpackItem(new Properties()));
	public static final DeferredHolder<Item, ManpackMicItem> MANPACK_MIC = ITEMS.register("manpack_mic", () -> new ManpackMicItem(new Properties()));
	public static final Supplier<Item> WORLD_MAP_MODULE = simpleItem("world_map_module", Component.translatable("tooltip.radiocraft.world_map_module").withStyle(net.minecraft.ChatFormatting.GRAY));

	public static final DeferredHolder<Item, DeskChargerBlockItem> DESK_CHARGER = ITEMS.register("desk_charger", () -> new DeskChargerBlockItem(RadiocraftBlocks.DESK_CHARGER.get(), new Properties()));
	public static final DeferredHolder<Item, SatelliteDishBlockItem> SATELLITE_DISH = ITEMS.register("satellite_dish", () -> new SatelliteDishBlockItem(RadiocraftBlocks.SATELLITE_DISH.get(), new Properties()));


	// Helper methods to cut down on boilerplate
	private static Supplier<Item> simpleItem(String name, Component tooltip) {
        if (tooltip != null) {
            return ITEMS.register(name, () -> new Item(new Properties()) {
                @Override
                public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
                    super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    tooltipComponents.add(tooltip);
                }
            });
        } else {
            return ITEMS.register(name, () -> new Item(new Properties()));
        }
	}

	private static CrystalVariants<Item, BlockItem> crystalBlockItems(String name, CrystalVariants<Block, ? extends Block> blocks) {
		return CrystalVariants.register(ITEMS, name, crystal -> new BlockItem(blocks.get(crystal).get(), new Properties()));
	}

	/** {@code <prefix>_<band>_<quality>} for every band and quality, e.g. {@code encased_crystal_40m_poor}. */
	private static Map<CrystalBand, Map<CrystalQuality, Supplier<Item>>> crystals(String prefix) {
		Map<CrystalBand, Map<CrystalQuality, Supplier<Item>>> out = new EnumMap<>(CrystalBand.class);
		for(CrystalBand band : CrystalBand.values()) {
			Map<CrystalQuality, Supplier<Item>> byQuality = new EnumMap<>(CrystalQuality.class);
			for(CrystalQuality quality : CrystalQuality.values())
				byQuality.put(quality, simpleItem(prefix + "_" + band.getId() + "_" + quality.getId(), null));
			out.put(band, byQuality);
		}
		return out;
	}

	private static Map<String, Supplier<BlockItem>> radioFaxes() {
		Map<String, Supplier<BlockItem>> out = new java.util.LinkedHashMap<>();
		RadiocraftBlocks.RADIO_FAXES.forEach((wood, block) -> out.put(wood, simpleBlockItem("radio_fax_" + wood, block)));
		return out;
	}

	private static Map<String, Supplier<BlockItem>> deskSpeakers() {
		Map<String, Supplier<BlockItem>> out = new java.util.LinkedHashMap<>();
		RadiocraftBlocks.DESK_SPEAKERS.forEach((wood, block) -> out.put(wood, simpleBlockItem("desk_speaker_" + wood, block)));
		return out;
	}

	/** A commercial handheld of {@code model}, with its factory channels and the handheld's radio state. */
	private static DeferredHolder<Item, CommercialHandheldItem> commercialHandheld(String name, com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel model) {
		return ITEMS.register(name, () -> new CommercialHandheldItem(new Properties().stacksTo(1)
				.component(RadiocraftDataComponent.HANDHELD_RADIO_STATE_COMPONENT.value(), HandheldRadioState.getDefault())
				.component(RadiocraftDataComponent.COMMERCIAL_PROGRAM.value(), model.factoryProgram()), model));
	}

	/** A new marine handheld's radio state: off, on channel 16 (156.800 MHz). */
	private static HandheldRadioState marineHandheldState() {
		return new HandheldRadioState(false, false, (float)com.arrl.radiocraft.common.radio.marine.MarineRadioState.SIXTEEN_HZ, 1.0F, 1.0F, 0.0F);
	}

	/** A floppy for each program in {@link com.arrl.radiocraft.common.computer.ComputerApps} that comes on one. */
	private static Map<String, DeferredHolder<Item, FloppyDiskItem>> floppies() {
		Map<String, DeferredHolder<Item, FloppyDiskItem>> out = new java.util.LinkedHashMap<>();
		for(com.arrl.radiocraft.common.computer.ComputerApp app : com.arrl.radiocraft.common.computer.ComputerApps.onFloppies()) {
			net.minecraft.world.item.Rarity rarity = app.kind() == com.arrl.radiocraft.common.computer.ComputerApp.Kind.SECRET ? net.minecraft.world.item.Rarity.RARE : net.minecraft.world.item.Rarity.COMMON;
			out.put(app.id(), ITEMS.register(app.floppyItemId(), () -> new FloppyDiskItem(new Properties().stacksTo(16).rarity(rarity), app)));
		}
		return out;
	}

	/** The floppy disk {@code app} comes on, or null for a built-in program. */
	@org.jetbrains.annotations.Nullable
	public static FloppyDiskItem floppy(com.arrl.radiocraft.common.computer.ComputerApp app) {
		DeferredHolder<Item, FloppyDiskItem> holder = FLOPPIES.get(app.id());
		return holder == null ? null : holder.get();
	}

	/** Both commercial handhelds. */
	public static Item[] commercialHandhelds() {
		return new Item[] { BASIC_COMMERCIAL_HANDHELD.get(), UPGRADED_COMMERCIAL_HANDHELD.get() };
	}

	private static Supplier<BlockItem> simpleBlockItem(String name, Supplier<? extends Block> block) {
		return ITEMS.register(name, () -> new BlockItem(block.get(), new Properties()));
	}

}
