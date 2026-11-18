package com.arrl.radiocraft.common.init;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blocks.*;
import com.arrl.radiocraft.common.blocks.antennas.*;
import com.arrl.radiocraft.common.blocks.power.ChargeControllerBlock;
import com.arrl.radiocraft.common.blocks.power.LargeBatteryBlock;
import com.arrl.radiocraft.common.blocks.power.SolarPanelBlock;
import com.arrl.radiocraft.common.blocks.radios.*;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registry class for Radiocraft's blocks
 */
public class RadiocraftBlocks {

	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.createBlocks(Radiocraft.MOD_ID);

	// Copy common properties here to avoid using copy method a ton.
	private static final Properties PROPERTIES_STONE = Properties.ofFullCopy(Blocks.STONE);
	private static final Properties PROPERTIES_STONE_NO_OCCLUDE = Properties.ofFullCopy(Blocks.STONE).noCollission();
	private static final Properties PROPERTIES_RADIO = Properties.ofFullCopy(Blocks.STONE).noOcclusion();
	/** Antennas players cannot walk through: their collision shapes follow the model (2026-11-13). */
	private static final Properties PROPERTIES_STONE_SOLID_ANTENNA = Properties.ofFullCopy(Blocks.STONE).noOcclusion();
	private static final Properties PROPERTIES_IRON_BARS = Properties.ofFullCopy(Blocks.IRON_BARS);
	private static final Properties PROPERTIES_FOOTHOLD = Properties.ofFullCopy(Blocks.IRON_BARS).strength(3.0F, 6.0F);
	private static final Properties PROPERTIES_WIRES = Properties.of().sound(SoundType.STONE).instabreak().noCollission().noOcclusion().mapColor(MapColor.COLOR_LIGHT_GRAY); // No tool needed to pick wire back up.

	// Power related blocks
	public static final DeferredHolder<Block, WireBlock> WIRE = BLOCKS.register("wire", () -> new WireBlock(PROPERTIES_WIRES, true, false));
	public static final Supplier<Block> WATERPROOF_WIRE = BLOCKS.register("waterproof_wire", () -> new WireBlock(PROPERTIES_WIRES, true, true));
	/** Links radios, accessories, wired speakers and microwave stations that do not touch (see {@code DataWire}). */
	public static final DeferredHolder<Block, WireBlock> DATA_WIRE = BLOCKS.register("data_wire", () -> WireBlock.data(PROPERTIES_WIRES));
	/** Dyed data wire, one block per colour: it joins only its own colour, so parallel runs stay apart (as dyed coax). */
	public static final java.util.Map<net.minecraft.world.item.DyeColor, DeferredHolder<Block, WireBlock>> COLOURED_DATA_WIRE = colouredDataWire();

	private static java.util.Map<net.minecraft.world.item.DyeColor, DeferredHolder<Block, WireBlock>> colouredDataWire() {
		java.util.Map<net.minecraft.world.item.DyeColor, DeferredHolder<Block, WireBlock>> out = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
		for(net.minecraft.world.item.DyeColor colour : net.minecraft.world.item.DyeColor.values())
			out.put(colour, BLOCKS.register("data_wire_" + colour.getSerializedName(), () -> WireBlock.data(PROPERTIES_WIRES, colour)));
		return out;
	}
	/** Dyed power wire, one block per colour: it joins only its own colour and plain wire, so parallel runs stay apart (as dyed coax). */
	public static final java.util.Map<net.minecraft.world.item.DyeColor, DeferredHolder<Block, WireBlock>> COLOURED_WIRE = colouredWire();

	private static java.util.Map<net.minecraft.world.item.DyeColor, DeferredHolder<Block, WireBlock>> colouredWire() {
		java.util.Map<net.minecraft.world.item.DyeColor, DeferredHolder<Block, WireBlock>> out = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
		for(net.minecraft.world.item.DyeColor colour : net.minecraft.world.item.DyeColor.values())
			out.put(colour, BLOCKS.register("wire_" + colour.getSerializedName(), () -> new WireBlock(PROPERTIES_WIRES, true, false, colour)));
		return out;
	}
	public static final DeferredHolder<Block, SolarPanelBlock> SOLAR_PANEL = BLOCKS.register("solar_panel", () -> new SolarPanelBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, LargeBatteryBlock> LARGE_BATTERY = BLOCKS.register("large_battery", () -> new LargeBatteryBlock(PROPERTIES_RADIO));
	/** An emergency reserve (2026-11-09): full and inert until a redstone signal, never recharged. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.power.StableChemicalBatteryBlock> STABLE_CHEMICAL_BATTERY = BLOCKS.register("stable_chemical_battery",
			() -> new com.arrl.radiocraft.common.blocks.power.StableChemicalBatteryBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, ChargeControllerBlock> CHARGE_CONTROLLER = BLOCKS.register("charge_controller", () -> new ChargeControllerBlock(PROPERTIES_STONE));

	// Radios/receivers/repeaters
	public static final CrystalVariants<Block, VHFBaseStationBlock> VHF_BASE_STATION_VARIANTS = CrystalVariants.register(BLOCKS, "vhf_base_station", crystal -> new VHFBaseStationBlock(PROPERTIES_RADIO, crystal));
	public static final DeferredHolder<Block, VHFBaseStationBlock> VHF_BASE_STATION = VHF_BASE_STATION_VARIANTS.good();
	public static final DeferredHolder<Block, VHFReceiverBlock> VHF_RECEIVER = BLOCKS.register("vhf_receiver", () -> new VHFReceiverBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, VHFRepeaterBlock> VHF_REPEATER = BLOCKS.register("vhf_repeater", () -> new VHFRepeaterBlock(PROPERTIES_RADIO));
	/** The commercial mobile radios (1.12): VHF high band (analog and digital) and low band (analog). */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.CommercialMobileBlock> COMMERCIAL_MOBILE = BLOCKS.register("commercial_mobile",
			() -> new com.arrl.radiocraft.common.blocks.radios.CommercialMobileBlock(PROPERTIES_RADIO, com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel.VHF_MOBILE));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.CommercialMobileBlock> LOW_BAND_MOBILE = BLOCKS.register("low_band_mobile",
			() -> new com.arrl.radiocraft.common.blocks.radios.CommercialMobileBlock(PROPERTIES_RADIO, com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel.LOW_BAND_MOBILE));
	/** The fixed-mount VHF marine radio (1.12): channel-only, 25 W / 1 W, 16/9 and dual watch, hand mic. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.MarineRadioBlock> MARINE_RADIO = BLOCKS.register("marine_radio",
			() -> new com.arrl.radiocraft.common.blocks.radios.MarineRadioBlock(PROPERTIES_RADIO));
	/** The CB mobiles (2026-11-08): 40 AM channels, 5 W, no licence. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.CbRadioBlock> CB_MOBILE_5875 = BLOCKS.register("cb_mobile_5875",
			() -> new com.arrl.radiocraft.common.blocks.radios.CbRadioBlock(PROPERTIES_RADIO, com.arrl.radiocraft.common.radio.cb.CbMobileModel.DELUXE_5875));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.CbRadioBlock> CB_MOBILE_PC68 = BLOCKS.register("cb_mobile_pc68",
			() -> new com.arrl.radiocraft.common.blocks.radios.CbRadioBlock(PROPERTIES_RADIO, com.arrl.radiocraft.common.radio.cb.CbMobileModel.PC68));
	/** The airband radio and the FM broadcast pair (2026-11-13). */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.AirbandRadioBlock> AIRBAND_RADIO = BLOCKS.register("airband_radio",
			() -> new com.arrl.radiocraft.common.blocks.radios.AirbandRadioBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.FmBroadcastTransmitterBlock> FM_BROADCAST_TRANSMITTER = BLOCKS.register("fm_broadcast_transmitter",
			() -> new com.arrl.radiocraft.common.blocks.radios.FmBroadcastTransmitterBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.FmBroadcastTransmitterTopBlock> FM_BROADCAST_TRANSMITTER_TOP = BLOCKS.register("fm_broadcast_transmitter_top",
			() -> new com.arrl.radiocraft.common.blocks.radios.FmBroadcastTransmitterTopBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.FmBroadcastReceiverBlock> FM_BROADCAST_RECEIVER = BLOCKS.register("fm_broadcast_receiver",
			() -> new com.arrl.radiocraft.common.blocks.radios.FmBroadcastReceiverBlock(Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).noOcclusion()));
	/** The aircraft VHF antenna (2026-11-13): on top of a block or under one. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.AirbandAntennaBlock> AIRBAND_ANTENNA = BLOCKS.register("airband_antenna",
			() -> new com.arrl.radiocraft.common.blocks.antennas.AirbandAntennaBlock(PROPERTIES_STONE_SOLID_ANTENNA));
	/** The FM broadcast antenna (2026-11-16): a side-mounted bay on a mast. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.FmBroadcastAntennaBlock> FM_BROADCAST_ANTENNA = BLOCKS.register("fm_broadcast_antenna",
			() -> new com.arrl.radiocraft.common.blocks.antennas.FmBroadcastAntennaBlock(PROPERTIES_STONE_SOLID_ANTENNA));
	/** The airband ground plane (2026-11-13): a ground station's antenna, two blocks tall. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.AirbandGroundPlaneAntennaBlock> AIRBAND_GROUND_PLANE = BLOCKS.register("airband_ground_plane",
			() -> new com.arrl.radiocraft.common.blocks.antennas.AirbandGroundPlaneAntennaBlock(PROPERTIES_STONE_SOLID_ANTENNA));
	/** Plays tapes out loud and into the radios it touches, and records on blank ones. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.TapePlayerBlock> TAPE_PLAYER = BLOCKS.register("tape_player",
			() -> new com.arrl.radiocraft.common.blocks.TapePlayerBlock(Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).noOcclusion()));
	/** Not craftable: found in structures (numbers stations, beacons, dead repeaters). Breaks into a rare part. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.CommercialRadioModuleBlock> COMMERCIAL_RADIO_MODULE = BLOCKS.register("commercial_radio_module",
			() -> new com.arrl.radiocraft.common.blocks.CommercialRadioModuleBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(5.0F, 10.0F).noOcclusion()));
	/** Not craftable: found at repeater sites. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.CommercialRepeaterBlock> COMMERCIAL_REPEATER = BLOCKS.register("commercial_repeater",
			() -> new com.arrl.radiocraft.common.blocks.radios.CommercialRepeaterBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4.0F, 8.0F).noOcclusion()));
	/** Not craftable: the server admins' screen over the moderation log, locked to whoever placed it. */
	public static final DeferredHolder<Block, ModerationComputerBlock> MODERATION_COMPUTER = BLOCKS.register("moderation_computer",
			() -> new ModerationComputerBlock(Properties.ofFullCopy(Blocks.STONE).strength(2.0F, 6.0F).noOcclusion()));
	/** The radio computer (1.12): the PC cabinet with the disk and floppy drive; its monitor goes on top, the scanner beside. */
	public static final DeferredHolder<Block, RadioComputerBlock> RADIO_COMPUTER = BLOCKS.register("radio_computer",
			() -> new RadioComputerBlock(Properties.ofFullCopy(Blocks.STONE).strength(2.0F, 6.0F).noOcclusion()));
	public static final DeferredHolder<Block, ComputerMonitorBlock> COMPUTER_MONITOR = BLOCKS.register("computer_monitor",
			() -> new ComputerMonitorBlock(Properties.ofFullCopy(Blocks.STONE).strength(2.0F, 6.0F).noOcclusion()));
	public static final DeferredHolder<Block, ScannerBlock> SCANNER = BLOCKS.register("scanner",
			() -> new ScannerBlock(Properties.ofFullCopy(Blocks.STONE).strength(2.0F, 6.0F).noOcclusion()));
	/** The External CRT Monitor (2026-11-12): a vintage CRT showing a WXSAT computer's weather satellite mosaic, or a premium radio's waterfall. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.CrtMonitorBlock> EXTERNAL_CRT_MONITOR = BLOCKS.register("external_crt_monitor",
			() -> new com.arrl.radiocraft.common.blocks.CrtMonitorBlock(Properties.ofFullCopy(Blocks.STONE).strength(2.0F, 6.0F).noOcclusion()));
	/** ADS-B (1.12 step 9): the 1090 MHz beacon for craft, the receiver with its radar screen, and its antenna. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.adsb.AdsbBeaconBlock> ADSB_BEACON = BLOCKS.register("adsb_beacon",
			() -> new com.arrl.radiocraft.common.blocks.adsb.AdsbBeaconBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.adsb.AdsbReceiverBlock> ADSB_RECEIVER = BLOCKS.register("adsb_receiver",
			() -> new com.arrl.radiocraft.common.blocks.adsb.AdsbReceiverBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.adsb.AdsbAntennaBlock> ADSB_ANTENNA = BLOCKS.register("adsb_antenna",
			() -> new com.arrl.radiocraft.common.blocks.adsb.AdsbAntennaBlock(PROPERTIES_RADIO));
	/** Weather alerts (1.12): the generator on an APRS station that broadcasts on 162.500 MHz, and the battery receiver. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.WeatherAlertGeneratorBlock> WEATHER_ALERT_GENERATOR = BLOCKS.register("weather_alert_generator",
			() -> new com.arrl.radiocraft.common.blocks.WeatherAlertGeneratorBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.WeatherAlertReceiverBlock> WEATHER_ALERT_RECEIVER = BLOCKS.register("weather_alert_receiver",
			() -> new com.arrl.radiocraft.common.blocks.WeatherAlertReceiverBlock(PROPERTIES_RADIO));
	/** The Headless APRS TNC (2026-11-18): a radio interface and APRS beacon/digipeater in one, an H19's modem. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.HeadlessTncBlock> HEADLESS_TNC = BLOCKS.register("headless_tnc",
			() -> new com.arrl.radiocraft.common.blocks.HeadlessTncBlock(PROPERTIES_RADIO));
	/** The Weather Observer (2026-11-18): a Local Weather Station's readings out through a radio, for weather alert generators. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.WeatherObserverBlock> WEATHER_OBSERVER = BLOCKS.register("weather_observer",
			() -> new com.arrl.radiocraft.common.blocks.WeatherObserverBlock(PROPERTIES_RADIO));
	/** Fire alarms and SCADA on the radio mesh (2026-11-10). Placeholder models until the maintainer's are made. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.FireAlarmBoxBlock> FIRE_ALARM_BOX = BLOCKS.register("fire_alarm_box",
			() -> new com.arrl.radiocraft.common.blocks.mesh.FireAlarmBoxBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.PullStationBlock> PULL_STATION = BLOCKS.register("pull_station",
			() -> new com.arrl.radiocraft.common.blocks.mesh.PullStationBlock(PROPERTIES_RADIO, false));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.PullStationBlock> OUTDOOR_PULL_BOX = BLOCKS.register("outdoor_pull_box",
			() -> new com.arrl.radiocraft.common.blocks.mesh.PullStationBlock(PROPERTIES_RADIO, true));
	/** Yellow and white pull stations and outdoor pull boxes (2026-11-11): the same, in other colours (any kind of alarm). */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.PullStationBlock> YELLOW_PULL_STATION = BLOCKS.register("yellow_pull_station",
			() -> new com.arrl.radiocraft.common.blocks.mesh.PullStationBlock(PROPERTIES_RADIO, false));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.PullStationBlock> WHITE_PULL_STATION = BLOCKS.register("white_pull_station",
			() -> new com.arrl.radiocraft.common.blocks.mesh.PullStationBlock(PROPERTIES_RADIO, false));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.PullStationBlock> YELLOW_OUTDOOR_PULL_BOX = BLOCKS.register("yellow_outdoor_pull_box",
			() -> new com.arrl.radiocraft.common.blocks.mesh.PullStationBlock(PROPERTIES_RADIO, true));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.PullStationBlock> WHITE_OUTDOOR_PULL_BOX = BLOCKS.register("white_outdoor_pull_box",
			() -> new com.arrl.radiocraft.common.blocks.mesh.PullStationBlock(PROPERTIES_RADIO, true));
	/** A vintage panic button (2026-11-11): a pull station on a wall plate, for under desks. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.PanicButtonBlock> PANIC_BUTTON = BLOCKS.register("panic_button",
			() -> new com.arrl.radiocraft.common.blocks.mesh.PanicButtonBlock(Block.Properties.of().strength(1.0F).noOcclusion().sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
	/** A fire alarm bell (2026-11-11): rings while powered by redstone. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.FireBellBlock> FIRE_BELL = BLOCKS.register("fire_bell",
			() -> new com.arrl.radiocraft.common.blocks.mesh.FireBellBlock(Block.Properties.of().strength(1.5F).noOcclusion().sound(net.minecraft.world.level.block.SoundType.METAL).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
	// The outdoor warning sirens moved to Another Weather Mod (2026-11-13).
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.FireAlarmControllerBlock> FIRE_ALARM_CONTROLLER = BLOCKS.register("fire_alarm_controller",
			() -> new com.arrl.radiocraft.common.blocks.mesh.FireAlarmControllerBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.ScadaBlock> SCADA_RTU = BLOCKS.register("scada_rtu",
			() -> new com.arrl.radiocraft.common.blocks.mesh.ScadaBlock(PROPERTIES_RADIO, false));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.mesh.ScadaBlock> SCADA_MASTER = BLOCKS.register("scada_master",
			() -> new com.arrl.radiocraft.common.blocks.mesh.ScadaMasterBlock(PROPERTIES_RADIO)); // Two blocks wide (2026-11-11).
	/** The Real-World Interface (1.12 step 11): links the game's radios to a real radio on the server's sound card. Off by default. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.radios.RealWorldInterfaceBlock> REAL_WORLD_INTERFACE = BLOCKS.register("real_world_interface",
			() -> new com.arrl.radiocraft.common.blocks.radios.RealWorldInterfaceBlock(PROPERTIES_RADIO));

	public static final CrystalVariants<Block, HFRadio10mBlock> HF_RADIO_10M_VARIANTS = CrystalVariants.register(BLOCKS, "hf_radio_10m", crystal -> new HFRadio10mBlock(PROPERTIES_RADIO, crystal));
	public static final DeferredHolder<Block, HFRadio10mBlock> HF_RADIO_10M = HF_RADIO_10M_VARIANTS.good();
	public static final CrystalVariants<Block, HFRadio20mBlock> HF_RADIO_20M_VARIANTS = CrystalVariants.register(BLOCKS, "hf_radio_20m", crystal -> new HFRadio20mBlock(PROPERTIES_RADIO, crystal));
	public static final DeferredHolder<Block, HFRadio20mBlock> HF_RADIO_20M = HF_RADIO_20M_VARIANTS.good();
	public static final CrystalVariants<Block, HFRadio40mBlock> HF_RADIO_40M_VARIANTS = CrystalVariants.register(BLOCKS, "hf_radio_40m", crystal -> new HFRadio40mBlock(PROPERTIES_RADIO, crystal));
	public static final DeferredHolder<Block, HFRadio40mBlock> HF_RADIO_40M = HF_RADIO_40M_VARIANTS.good();
	public static final CrystalVariants<Block, HFRadio80mBlock> HF_RADIO_80M_VARIANTS = CrystalVariants.register(BLOCKS, "hf_radio_80m", crystal -> new HFRadio80mBlock(PROPERTIES_RADIO, crystal));
	public static final DeferredHolder<Block, HFRadio80mBlock> HF_RADIO_80M = HF_RADIO_80M_VARIANTS.good();
	public static final DeferredHolder<Block, HFReceiverBlock> HF_RECEIVER = BLOCKS.register("hf_receiver", () -> new HFReceiverBlock(PROPERTIES_RADIO));

	public static final DeferredHolder<Block, HFRadioAllBandBlock> ALL_BAND_RADIO = BLOCKS.register("all_band_radio", () -> new HFRadioAllBandBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, QRPRadio20mBlock> QRP_RADIO_20M = BLOCKS.register("qrp_radio_20m", () -> new QRPRadio20mBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, QRPRadio40mBlock> QRP_RADIO_40M = BLOCKS.register("qrp_radio_40m", () -> new QRPRadio40mBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, RadioInterfaceBlock> RADIO_INTERFACE = BLOCKS.register("radio_interface", () -> new RadioInterfaceBlock(PROPERTIES_STONE));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.DtmfDecoderBlock> DTMF_DECODER = BLOCKS.register("dtmf_decoder", () -> new com.arrl.radiocraft.common.blocks.DtmfDecoderBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.DtmfEncoderBlock> DTMF_ENCODER = BLOCKS.register("dtmf_encoder", () -> new com.arrl.radiocraft.common.blocks.DtmfEncoderBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, AprsTerminalBlock> APRS_TERMINAL = BLOCKS.register("aprs_h19", () -> new AprsTerminalBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, ParrotControllerBlock> PARROT_CONTROLLER = BLOCKS.register("parrot_controller", () -> new ParrotControllerBlock(PROPERTIES_RADIO));

	// Antenna blocks
	/** Joins power wire of any colour (colours otherwise keep apart). */
	public static final DeferredHolder<Block, WireJunctionBlock> WIRE_JUNCTION = BLOCKS.register("wire_junction",
			() -> new WireJunctionBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0F).noOcclusion()));
	/** Power Switch - On/Off (2026-11-09): connects or parts the power wire on two opposite sides. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.power.OnOffPowerSwitchBlock> POWER_SWITCH_ON_OFF = BLOCKS.register("power_switch_on_off",
			() -> new com.arrl.radiocraft.common.blocks.power.OnOffPowerSwitchBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0F).noOcclusion()));
	/** Power Switch - Left/Right (2026-11-09): connects its input to the output on its left or on its right. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.power.LeftRightPowerSwitchBlock> POWER_SWITCH_LEFT_RIGHT = BLOCKS.register("power_switch_left_right",
			() -> new com.arrl.radiocraft.common.blocks.power.LeftRightPowerSwitchBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0F).noOcclusion()));
	/** Shows the charge of the batteries on its power wire; power passes through it. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.BatteryMeterBlock> BATTERY_METER = BLOCKS.register("battery_meter",
			() -> new com.arrl.radiocraft.common.blocks.BatteryMeterBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0F).noOcclusion()));
	/** In-line on coax: a lightning strike's surge stops here. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.LightningArrestorBlock> LIGHTNING_ARRESTOR = BLOCKS.register("lightning_arrestor",
			() -> new com.arrl.radiocraft.common.blocks.LightningArrestorBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0F).noOcclusion()));
	/** A ground rod (2026-11-08): stack them down into the soil, bond with ground (green) wire. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.GroundRodBlock> GROUND_ROD = BLOCKS.register("ground_rod",
			() -> new com.arrl.radiocraft.common.blocks.GroundRodBlock(Properties.ofFullCopy(Blocks.LIGHTNING_ROD).noOcclusion()));
	public static final DeferredHolder<Block, DuplexerBlock> DUPLEXER = BLOCKS.register("duplexer", () -> new DuplexerBlock(PROPERTIES_STONE_NO_OCCLUDE));
	/** One radio to one of three antennas, chosen by a knob. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.AntennaSwitchBlock> ANTENNA_SWITCH = BLOCKS.register("antenna_switch",
			() -> new com.arrl.radiocraft.common.blocks.antennas.AntennaSwitchBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0F).noOcclusion()));
	/** One antenna to several receivers, receive only. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.AntennaSplitterBlock> ANTENNA_SPLITTER = BLOCKS.register("antenna_splitter",
			() -> new com.arrl.radiocraft.common.blocks.antennas.AntennaSplitterBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0F).noOcclusion()));
	public static final DeferredHolder<Block, AntennaTunerBlock> ANTENNA_TUNER = BLOCKS.register("antenna_tuner", () -> new AntennaTunerBlock(PROPERTIES_STONE));
	public static final DeferredHolder<Block, AutoTunerBlock> AUTO_TUNER = BLOCKS.register("auto_tuner", () -> new AutoTunerBlock(PROPERTIES_STONE_NO_OCCLUDE));

	public static final DeferredHolder<Block, WireBlock> COAX_WIRE = BLOCKS.register("coax_wire", () -> new WireBlock(PROPERTIES_WIRES, false, false));
	/** Dyed coax, one block per colour: it joins only its own colour and plain coax, so parallel runs stay apart. */
	public static final java.util.Map<net.minecraft.world.item.DyeColor, DeferredHolder<Block, WireBlock>> COLOURED_COAX = colouredCoax();

	private static java.util.Map<net.minecraft.world.item.DyeColor, DeferredHolder<Block, WireBlock>> colouredCoax() {
		java.util.Map<net.minecraft.world.item.DyeColor, DeferredHolder<Block, WireBlock>> out = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
		for(net.minecraft.world.item.DyeColor colour : net.minecraft.world.item.DyeColor.values())
			out.put(colour, BLOCKS.register("coax_wire_" + colour.getSerializedName(), () -> new WireBlock(PROPERTIES_WIRES, false, false, colour)));
		return out;
	}
	/** A wall block wire runs through; made with the passthrough item, looks like the block it replaced. */
	public static final DeferredHolder<Block, PassthroughBlock> PASSTHROUGH = BLOCKS.register("passthrough", () -> new PassthroughBlock(Properties.ofFullCopy(Blocks.STONE)));
	/** Several wires in one block (see WireBundleBlock); no item, made by using a wire item on a wire of another kind. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.WireBundleBlock> WIRE_BUNDLE = BLOCKS.register("wire_bundle", () -> new com.arrl.radiocraft.common.blocks.WireBundleBlock(PROPERTIES_WIRES));
	/** Rungs on a tower: the old ones (structures, creative) may give way under a player; crafted ones never do. */
	public static final DeferredHolder<Block, TowerFootholdBlock> TOWER_FOOTHOLD = BLOCKS.register("tower_foothold", () -> new TowerFootholdBlock(PROPERTIES_FOOTHOLD, false));
	public static final DeferredHolder<Block, TowerFootholdBlock> OLD_TOWER_FOOTHOLD = BLOCKS.register("old_tower_foothold", () -> new TowerFootholdBlock(PROPERTIES_FOOTHOLD, true));
	public static final DeferredHolder<Block, AntennaPoleBlock> ANTENNA_POLE = BLOCKS.register("antenna_pole", () -> new AntennaPoleBlock(PROPERTIES_IRON_BARS));
	public static final DeferredHolder<Block, AntennaConnectorBlock> ANTENNA_CONNECTOR = BLOCKS.register("antenna_connector", () -> new AntennaConnectorBlock(PROPERTIES_STONE_NO_OCCLUDE));
	public static final DeferredHolder<Block, BalunBlock> BALUN_ONE_TO_ONE = BLOCKS.register("balun_one_to_one", () -> new BalunBlock(PROPERTIES_STONE_NO_OCCLUDE, "balun_one_to_one"));
	public static final DeferredHolder<Block, BalunBlock> BALUN_FOUR_TO_ONE = BLOCKS.register("balun_four_to_one", () -> new BalunBlock(PROPERTIES_STONE_NO_OCCLUDE, "balun_four_to_one"));
	public static final DeferredHolder<Block, BalunBlock> BALUN_TWO_TO_ONE = BLOCKS.register("balun_two_to_one", () -> new BalunBlock(PROPERTIES_STONE_NO_OCCLUDE, "balun_two_to_one"));
	public static final DeferredHolder<Block, BalunBlock> BALUN_NINE_TO_ONE = BLOCKS.register("balun_nine_to_one", () -> new BalunBlock(PROPERTIES_STONE_NO_OCCLUDE, "balun_nine_to_one"));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.DummyLoadBlock> DUMMY_LOAD = BLOCKS.register("dummy_load", () -> new com.arrl.radiocraft.common.blocks.antennas.DummyLoadBlock(PROPERTIES_STONE_NO_OCCLUDE));
	public static final DeferredHolder<Block, JPoleAntennaBlock> J_POLE_ANTENNA = BLOCKS.register("j_pole_antenna", () -> new JPoleAntennaBlock(PROPERTIES_STONE_NO_OCCLUDE));
	/** The EB-VHF eggbeater, the satellite antenna (2026-11-12). */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.EggbeaterAntennaBlock> EGGBEATER_ANTENNA = BLOCKS.register("eggbeater_antenna", () -> new com.arrl.radiocraft.common.blocks.antennas.EggbeaterAntennaBlock(PROPERTIES_STONE_NO_OCCLUDE));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.RingoAntennaBlock> RINGO_ANTENNA = BLOCKS.register("ringo_antenna", () -> new com.arrl.radiocraft.common.blocks.antennas.RingoAntennaBlock(PROPERTIES_STONE_NO_OCCLUDE));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.FoldedDipoleAntennaBlock> FOLDED_DIPOLE_ANTENNA = BLOCKS.register("folded_dipole_antenna", () -> new com.arrl.radiocraft.common.blocks.antennas.FoldedDipoleAntennaBlock(PROPERTIES_STONE_NO_OCCLUDE));
	public static final DeferredHolder<Block, IonosphericProcessorBlock> IONOSPHERIC_PROCESSOR = BLOCKS.register("ionospheric_processor", () -> new IonosphericProcessorBlock(Properties.ofFullCopy(Blocks.STONE).noOcclusion()
			.lightLevel(state -> state.getValue(IonosphericProcessorBlock.HALF) == DoubleBlockHalf.UPPER ? IonosphericProcessorBlock.VALVE_LIGHT : 0)));
	public static final DeferredHolder<Block, FiveBtvAntennaBlock> FIVE_BTV_ANTENNA = BLOCKS.register("5btv_antenna", () -> new FiveBtvAntennaBlock(PROPERTIES_STONE_SOLID_ANTENNA));
	/** A CB ground plane, five blocks tall (2026-11-08). */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.CbGroundPlaneAntennaBlock> CB_GROUND_PLANE_ANTENNA = BLOCKS.register("cb_ground_plane_antenna",
			() -> new com.arrl.radiocraft.common.blocks.antennas.CbGroundPlaneAntennaBlock(PROPERTIES_STONE_NO_OCCLUDE));
	public static final DeferredHolder<Block, IonosondeBlock> IONOSONDE = BLOCKS.register("ionosonde", () -> new IonosondeBlock(PROPERTIES_STONE_SOLID_ANTENNA));
	public static final DeferredHolder<Block, SlimJimAntennaBlock> SLIM_JIM_ANTENNA = BLOCKS.register("slim_jim_antenna", () -> new SlimJimAntennaBlock(PROPERTIES_STONE_NO_OCCLUDE));
	public static final DeferredHolder<Block, YagiAntennaBlock> YAGI_ANTENNA = BLOCKS.register("yagi_antenna", () -> new YagiAntennaBlock(PROPERTIES_STONE_NO_OCCLUDE));
	public static final DeferredHolder<Block, SatelliteDishBlock> SATELLITE_DISH = BLOCKS.register("satellite_dish", () -> new SatelliteDishBlock(PROPERTIES_STONE_NO_OCCLUDE));
	/** A Band Plan sheet hung on a wall (2026-11-18): placed by the Band Plan item, drops it. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.BandPlanWallBlock> BAND_PLAN_WALL = BLOCKS.register("band_plan_wall",
			() -> new com.arrl.radiocraft.common.blocks.BandPlanWallBlock(Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion().noCollission()
					.instabreak().sound(net.minecraft.world.level.block.SoundType.WOOL).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
	/** The iambic keyer (2026-11-12): beside an HF radio, it keys its CW from two paddles. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.IambicKeyerBlock> IAMBIC_KEYER = BLOCKS.register("iambic_keyer",
			() -> new com.arrl.radiocraft.common.blocks.IambicKeyerBlock(Properties.ofFullCopy(Blocks.STONE).noOcclusion()));
	/** The Computer Redstone block (2026-11-13): redstone out (and in) for a radio computer, set over the LAN. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.ComputerRedstoneBlock> COMPUTER_REDSTONE = BLOCKS.register("computer_redstone",
			() -> new com.arrl.radiocraft.common.blocks.ComputerRedstoneBlock(Properties.ofFullCopy(Blocks.STONE)));
	/** The linear amplifier (2026-11-13): on the coax between an HF radio and its antenna, and on power. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.LinearAmplifierBlock> LINEAR_AMPLIFIER = BLOCKS.register("linear_amplifier",
			() -> new com.arrl.radiocraft.common.blocks.LinearAmplifierBlock(Properties.ofFullCopy(Blocks.STONE).noOcclusion()
					.lightLevel(state -> state.getValue(com.arrl.radiocraft.common.blocks.LinearAmplifierBlock.LIT) ? 4 : 0)));
	/** The satellite tracking antenna (2026-11-12): the dish on a powered az/el mount. */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.antennas.SatelliteTrackerBlock> SATELLITE_TRACKER = BLOCKS.register("satellite_tracker",
			() -> new com.arrl.radiocraft.common.blocks.antennas.SatelliteTrackerBlock(PROPERTIES_STONE_NO_OCCLUDE));

	public static final DeferredHolder<Block, TeletypeBlock> TELETYPE = BLOCKS.register("teletype", () -> new TeletypeBlock(PROPERTIES_STONE));

	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.WefaxReceiverBlock> WEFAX_RECEIVER = BLOCKS.register("wefax_receiver", () -> new com.arrl.radiocraft.common.blocks.WefaxReceiverBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, MicrophoneBlock> MICROPHONE = BLOCKS.register("microphone", () -> new MicrophoneBlock(PROPERTIES_STONE));
	public static final DeferredHolder<Block, DeskChargerBlock> DESK_CHARGER = BLOCKS.register("desk_charger", () -> new DeskChargerBlock(Properties.of().mapColor(DyeColor.GRAY).strength(0.0F, 5.0F).sound(SoundType.METAL).noOcclusion()));

	// Tower legs: lattice beams that join every beam touching them (see TowerBeamBlock).
	public static final DeferredHolder<Block, TowerBeamBlock> TOWER_BEAM_THIN = BLOCKS.register("tower_beam_thin", () -> new TowerBeamBlock(PROPERTIES_IRON_BARS, 4));
	/** A thin beam that flashes a tower's obstruction lights: redstone 2 s on, 2 s off. Creative and structures only. */
	/** A tower's red obstruction light: lit by redstone, light 11. */
	public static final DeferredHolder<Block, TowerBeaconBlock> TOWER_BEACON = BLOCKS.register("tower_beacon",
			() -> new TowerBeaconBlock(Properties.ofFullCopy(Blocks.REDSTONE_LAMP).noOcclusion().sound(net.minecraft.world.level.block.SoundType.METAL)));
	public static final DeferredHolder<Block, TowerBeaconControllerBlock> TOWER_BEACON_CONTROLLER = BLOCKS.register("tower_beacon_controller", () -> new TowerBeaconControllerBlock(PROPERTIES_IRON_BARS));
	public static final DeferredHolder<Block, TowerBeamBlock> TOWER_BEAM = BLOCKS.register("tower_beam", () -> new TowerBeamBlock(PROPERTIES_IRON_BARS, 6));
	public static final DeferredHolder<Block, TowerBeamBlock> TOWER_BEAM_THICK = BLOCKS.register("tower_beam_thick", () -> new TowerBeamBlock(PROPERTIES_IRON_BARS, 8));
	/** The AT&T Long Lines horn-reflector, two by two blocks and four tall. Decorative for now. */
	public static final DeferredHolder<Block, MicrowaveHornBlock> MICROWAVE_HORN = BLOCKS.register("microwave_horn", () -> new MicrowaveHornBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0F, 6.0F).noOcclusion()));

	// Speakers (see SpeakerKind): ones that sit next to a radio, and wired ones on its power wire.
	public static final String[] SPEAKER_WOODS = { "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped" };
	private static final Properties PROPERTIES_SPEAKER_METAL = Properties.of().mapColor(MapColor.METAL).strength(1.5F, 3.0F).sound(SoundType.METAL).noOcclusion();
	public static final java.util.Map<String, DeferredHolder<Block, SpeakerBlock>> DESK_SPEAKERS = deskSpeakers();
	/** The radio fax, one per log, the same woods as the desk speakers (after SPEAKER_WOODS: it reads it). */
	public static final java.util.Map<String, DeferredHolder<Block, RadioFaxBlock>> RADIO_FAXES = radioFaxes();
	public static final DeferredHolder<Block, SpeakerBlock> LARGE_SPEAKER = BLOCKS.register("large_speaker", () -> new SpeakerBlock(Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion(),
			com.arrl.radiocraft.common.radio.voice.SpeakerKind.STANDARD, 1.0D, 0.0D, 0.25D, 15.0D, 16.0D, 15.0D));
	public static final DeferredHolder<Block, SpeakerBlock> PREMIUM_SPEAKER_MARBLE = premiumSpeaker("marble");
	public static final DeferredHolder<Block, SpeakerBlock> PREMIUM_SPEAKER_IRON = premiumSpeaker("iron");
	public static final DeferredHolder<Block, SpeakerBlock> PREMIUM_SPEAKER_GOLD = premiumSpeaker("gold");
	public static final DeferredHolder<Block, WiredSpeakerBlock> WALL_SPEAKER = BLOCKS.register("wall_speaker", () -> new WiredSpeakerBlock(PROPERTIES_SPEAKER_METAL,
			com.arrl.radiocraft.common.radio.voice.SpeakerKind.WALL, 0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D));
	public static final DeferredHolder<Block, WiredSpeakerBlock> LOUDSPEAKER = BLOCKS.register("loudspeaker", () -> new WiredSpeakerBlock(PROPERTIES_SPEAKER_METAL,
			com.arrl.radiocraft.common.radio.voice.SpeakerKind.LOUDSPEAKER, 1.0D, 0.0D, 0.0D, 15.0D, 16.0D, 15.0D));

	// Direction finding: the terminal (plain, or with the world map module), and the antenna modules stacked on it.
	public static final DeferredHolder<Block, DirectionFinderBlock> DIRECTION_FINDER = BLOCKS.register("direction_finder", () -> new DirectionFinderBlock(PROPERTIES_RADIO, false));
	public static final DeferredHolder<Block, DirectionFinderBlock> MAP_DIRECTION_FINDER = BLOCKS.register("map_direction_finder", () -> new DirectionFinderBlock(PROPERTIES_RADIO, true));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.PabxBlock> PABX = BLOCKS.register("pabx", () -> new com.arrl.radiocraft.common.blocks.PabxBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.PhonePaBlock> PHONE_PA = BLOCKS.register("phone_pa", () -> new com.arrl.radiocraft.common.blocks.PhonePaBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.RepeaterAutopatchBlock> REPEATER_AUTOPATCH = BLOCKS.register("repeater_autopatch", () -> new com.arrl.radiocraft.common.blocks.RepeaterAutopatchBlock(PROPERTIES_RADIO));
	public static final DeferredHolder<Block, DirectionFinderModuleBlock> DIRECTION_FINDER_MODULE = BLOCKS.register("direction_finder_module", () -> new DirectionFinderModuleBlock(PROPERTIES_RADIO));

	// Microwave links and what rides them.
	public static final DeferredHolder<Block, MicrowaveRadioBlock> MICROWAVE_RADIO = BLOCKS.register("microwave_radio", () -> new MicrowaveRadioBlock(PROPERTIES_RADIO));
	/** The TA-43 field phone: a party line on data wire (FieldPhoneBlockEntity). */
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.FieldPhoneBlock> FIELD_PHONE = BLOCKS.register("field_phone", () -> new com.arrl.radiocraft.common.blocks.FieldPhoneBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(1.5F).noOcclusion()));
	public static final DeferredHolder<Block, TelephoneBlock> TELEPHONE = BLOCKS.register("telephone", () -> new TelephoneBlock(Properties.ofFullCopy(Blocks.STONE).strength(1.0F).noOcclusion()));

	// Weather: a mast-top station whose readings H19s nearby beacon.
	public static final DeferredHolder<Block, com.arrl.radiocraft.common.blocks.WeatherStationBlock> WEATHER_STATION = BLOCKS.register("weather_station",
			() -> new com.arrl.radiocraft.common.blocks.WeatherStationBlock(PROPERTIES_SPEAKER_METAL));

	private static java.util.Map<String, DeferredHolder<Block, RadioFaxBlock>> radioFaxes() {
		java.util.Map<String, DeferredHolder<Block, RadioFaxBlock>> out = new java.util.LinkedHashMap<>();
		for(String wood : SPEAKER_WOODS)
			out.put(wood, BLOCKS.register("radio_fax_" + wood, () -> new RadioFaxBlock(Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).noOcclusion())));
		return out;
	}

	private static java.util.Map<String, DeferredHolder<Block, SpeakerBlock>> deskSpeakers() {
		java.util.Map<String, DeferredHolder<Block, SpeakerBlock>> out = new java.util.LinkedHashMap<>();
		for(String wood : SPEAKER_WOODS)
			out.put(wood, BLOCKS.register("desk_speaker_" + wood, () -> new SpeakerBlock(Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.0F).noOcclusion(),
					com.arrl.radiocraft.common.radio.voice.SpeakerKind.STANDARD, 5.0D, 0.0D, 5.5D, 11.0D, 5.0D, 10.5D)));
		return out;
	}

	private static DeferredHolder<Block, SpeakerBlock> premiumSpeaker(String colour) {
		return BLOCKS.register("premium_speaker_" + colour, () -> new SpeakerBlock(PROPERTIES_SPEAKER_METAL,
				com.arrl.radiocraft.common.radio.voice.SpeakerKind.PREMIUM, 4.0D, 0.0D, 4.5D, 12.0D, 6.5D, 11.0D));
	}

	public static Supplier<Block> simpleBlock(String name, Properties properties) {
		return BLOCKS.register(name, () -> new Block(properties));
	}

}
