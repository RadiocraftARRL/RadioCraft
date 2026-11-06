package com.arrl.radiocraft.datagen;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.capabilities.LicenseClass;
import com.arrl.radiocraft.common.init.CrystalVariants;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftEntityTypes;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.radio.crystal.CrystalBand;
import com.arrl.radiocraft.common.radio.crystal.CrystalQuality;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.data.LanguageProvider;
import org.apache.commons.lang3.text.WordUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Datagen class for localization files.
 */
public class RadiocraftLanguageProvider extends LanguageProvider {

	private final String locale;

	// Add translations to this map-- crude way to allow for multiple language sets without a ton of classes
	private static final Map<String, Consumer<LanguageProvider>> TRANSLATION_PROVIDERS = new HashMap<>();

	static {
		// Add translation sets here.
		TRANSLATION_PROVIDERS.put("en_us", (provider) -> {
			provider.add(Radiocraft.translationKey("tabs", "main_tab"), "RadioCraft");

			provider.addItem(RadiocraftItems.PASSTHROUGH, "Wire Passthrough");
			for(CrystalBand band : CrystalBand.values())
				for(CrystalQuality quality : CrystalQuality.values()) {
					provider.addItem(RadiocraftItems.CRYSTALS.get(band).get(quality), quality.getLabel() + " " + band.getLabel() + " Crystal");
					provider.addItem(RadiocraftItems.ENCASED_CRYSTALS.get(band).get(quality), "Encased " + quality.getLabel() + " " + band.getLabel() + " Crystal");
				}
			provider.addItem(RadiocraftItems.RADIO_SPEAKER, "Radio Speaker");
			provider.addItem(RadiocraftItems.HAND_MICROPHONE, "Hand Microphone");
			provider.addItem(RadiocraftItems.CORDED_MICROPHONE, "Hand Microphone");
			provider.add(Radiocraft.translationKey("tooltip", "corded_microphone"), "On its radio's cord: hold right click to talk, right click the radio to hang it up. The cord reaches 5 blocks.");
			provider.add(Radiocraft.translationKey("message", "mic_in_use"), "Someone else has this radio's microphone");
			provider.add(Radiocraft.translationKey("message", "mic_hand_full"), "Empty your hand to take the microphone");
			provider.add(Radiocraft.translationKey("tooltip", "telephone_handset"), "On its phone's cord: talk into it during a call. Sneak and right click the phone to hang up. The cord reaches 5 blocks.");
			provider.add(Radiocraft.translationKey("message", "handset_in_use"), "Someone else is on this phone");
			provider.add(Radiocraft.translationKey("message", "handset_hand_full"), "Empty your hand to pick up the phone");
			provider.add(Radiocraft.translationKey("message", "field_phone_overloaded"), "The line is dead: %s field phones on it, %s at most");
			provider.addItem(RadiocraftItems.FIELD_PHONE_HANDSET, "Field Phone Handset");
			provider.addItem(RadiocraftItems.TELEPHONE_HANDSET, "Telephone Handset");
			provider.addItem(RadiocraftItems.HF_CIRCUIT_BOARD, "HF Circuit Board");
			provider.addItem(RadiocraftItems.ALL_BAND_CIRCUIT_BOARD, "All-Band Circuit Board");
			provider.addItem(RadiocraftItems.SMALL_BATTERY, "Small Alkaline Battery");
			provider.addItem(RadiocraftItems.APRS_MODULE, "APRS Module");
			provider.addItem(RadiocraftItems.MARS_MOD, "MARS Mod");
			provider.addItem(RadiocraftItems.RADIO_CLONER, "Radio Cloner");
			provider.addItem(RadiocraftItems.BAND_PLAN, "Band Plan Sheet");
			provider.addItem(RadiocraftItems.FERRITE_CORE, "Ferrite Core");
			provider.addItem(RadiocraftItems.COAXIAL_CORE, "Coaxial Core");
			provider.addItem(RadiocraftItems.INSULATION, "Insulation");
			provider.addItem(RadiocraftItems.MICROPHONE_ELEMENT, "Microphone Element");
			provider.addItem(RadiocraftItems.ANTENNA_ANALYZER, "Antenna Analyzer");
			provider.addItem(RadiocraftItems.ANTENNA_WIRE, "Antenna Wire");
			addCrystalRadio(provider, RadiocraftItems.VHF_HANDHELD_VARIANTS, "VHF Handheld Radio");

			provider.addBlock(RadiocraftBlocks.WIRE, "Wire");
			provider.addBlock(RadiocraftBlocks.WATERPROOF_WIRE, "Waterproof Wire");
			provider.addBlock(RadiocraftBlocks.DATA_WIRE, "Data Wire");
			provider.addBlock(RadiocraftBlocks.SOLAR_PANEL, "Solar Panel");
			provider.addBlock(RadiocraftBlocks.TELETYPE, "Teletype Machine");
			provider.addBlock(RadiocraftBlocks.LARGE_BATTERY, "Large Battery");
			addCrystalRadio(provider, RadiocraftItems.VHF_BASE_STATION_VARIANTS, "VHF Base Station");
			provider.addBlock(RadiocraftBlocks.VHF_RECEIVER, "VHF Receiver");
			provider.addBlock(RadiocraftBlocks.VHF_REPEATER, "VHF Repeater");
			provider.addBlock(RadiocraftBlocks.COMMERCIAL_REPEATER, "Commercial Repeater");
			provider.addBlock(RadiocraftBlocks.COMMERCIAL_RADIO_MODULE, "Commercial Radio Module");
			provider.addItem(RadiocraftItems.TAPE_REEL, "Tape Reel");
			provider.addBlock(RadiocraftBlocks.TAPE_PLAYER, "Tape Player");
			provider.add("container.radiocraft.tape_player", "Tape Player");
			provider.add("item.radiocraft.tape_reel.blank", "Blank");
			provider.add("item.radiocraft.tape_reel.recorded", "Recorded, %s:%s");
			provider.add("screen.radiocraft.commercial_radio_module.no_band", "That frequency is in no band");
			provider.add("block.radiocraft.commercial_radio_module.sealed", "The cabinet is sealed. Whatever it sends, it sends on its own.");
			addCrystalRadio(provider, RadiocraftItems.HF_RADIO_10M_VARIANTS, "10m Radio");
			addCrystalRadio(provider, RadiocraftItems.HF_RADIO_20M_VARIANTS, "20m Radio");
			addCrystalRadio(provider, RadiocraftItems.HF_RADIO_40M_VARIANTS, "40m Radio");
			addCrystalRadio(provider, RadiocraftItems.HF_RADIO_80M_VARIANTS, "80m Radio");
			provider.addBlock(RadiocraftBlocks.HF_RECEIVER, "HF Receiver");
			provider.addBlock(RadiocraftBlocks.ALL_BAND_RADIO, "All Band Radio");
			provider.addBlock(RadiocraftBlocks.QRP_RADIO_20M, "QRP Radio (20m)");
			provider.addBlock(RadiocraftBlocks.QRP_RADIO_40M, "QRP Radio (40m)");
			provider.addBlock(RadiocraftBlocks.ANTENNA_POLE, "Antenna Pole");
			provider.addBlock(RadiocraftBlocks.TOWER_FOOTHOLD, "Tower Foothold");
			provider.addBlock(RadiocraftBlocks.OLD_TOWER_FOOTHOLD, "Old Tower Foothold");
			provider.addBlock(RadiocraftBlocks.DUPLEXER, "Duplexer");
			provider.addBlock(RadiocraftBlocks.ANTENNA_SPLITTER, "Antenna Splitter");
			provider.addBlock(RadiocraftBlocks.ANTENNA_SWITCH, "Antenna Switch");
			provider.add("tooltip.radiocraft.antenna_switch", "One radio, three antennas: the brass input turns to the radio's coax by itself. Right click to switch between outputs 1, 2 and 3.");
			provider.add("message.radiocraft.antenna_switch", "Antenna %s");
			provider.add("tooltip.radiocraft.antenna_splitter", "Receive only: coax from one antenna to several radios on its other sides. Each hears the antenna, a little weaker; none can transmit through it.");
			provider.addBlock(RadiocraftBlocks.WIRE_JUNCTION, "Wire Junction");
			provider.add("tooltip.radiocraft.wire_junction", "Joins power wire of any colour run into it. Not coax.");
			provider.addBlock(RadiocraftBlocks.ANTENNA_TUNER, "Antenna Tuner");
			provider.addBlock(RadiocraftBlocks.AUTO_TUNER, "Automatic Antenna Tuner");
			provider.addBlock(RadiocraftBlocks.ANTENNA_CONNECTOR, "Antenna Connector");
			provider.addBlock(RadiocraftBlocks.BALUN_ONE_TO_ONE, "Balun (1:1)");
			provider.addBlock(RadiocraftBlocks.BALUN_FOUR_TO_ONE, "Balun (4:1)");
			provider.addBlock(RadiocraftBlocks.BALUN_TWO_TO_ONE, "Balun (2:1)");
			provider.addBlock(RadiocraftBlocks.BALUN_NINE_TO_ONE, "Balun (9:1)");
			provider.addBlock(RadiocraftBlocks.COAX_WIRE, "Coaxial Wire");
			provider.addBlock(RadiocraftBlocks.WIRE_BUNDLE, "Wire Bundle");
			RadiocraftBlocks.COLOURED_COAX.forEach((colour, block) -> provider.addBlock(block, dyeName(colour) + " Coaxial Wire"));
			RadiocraftBlocks.COLOURED_DATA_WIRE.forEach((colour, block) -> provider.addBlock(block, dyeName(colour) + " Data Wire"));
			RadiocraftBlocks.COLOURED_WIRE.forEach((colour, block) -> provider.addBlock(block, dyeName(colour) + " Wire"));
			provider.addBlock(RadiocraftBlocks.RADIO_INTERFACE, "Radio Interface");
			provider.addBlock(RadiocraftBlocks.APRS_TERMINAL, "H19 APRS Terminal");
			RadiocraftBlocks.RADIO_FAXES.forEach((wood, block) -> provider.addBlock(block, woodName(wood) + " Radio Fax"));
			provider.addItem(RadiocraftItems.TELETYPE_ROLL, "Teletype Roll");
			provider.add("item.radiocraft.teletype_roll.lines", "%s lines printed");
			provider.add(Radiocraft.translationKey("tooltip", "teletype_roll"), "Torn off a teletype. Right click to read it; right click a teletype with an empty roll to feed it back in.");
			provider.add(Radiocraft.translationKey("tooltip", "radio_fax"), "Faxes books, paper, maps and enchanted books to another fax by callsign, through the radio it touches or its station's radio interface. Stock it with paper, books and maps for what it receives.");
			provider.addBlock(RadiocraftBlocks.PARROT_CONTROLLER, "Simplex Repeat Controller");
			provider.addBlock(RadiocraftBlocks.CHARGE_CONTROLLER, "Charge Controller");
			provider.addBlock(RadiocraftBlocks.MICROPHONE, "Microphone");
			provider.addBlock(RadiocraftBlocks.YAGI_ANTENNA, "Yagi Antenna");
			provider.addBlock(RadiocraftBlocks.J_POLE_ANTENNA, "J-Pole Antenna");
			provider.addBlock(RadiocraftBlocks.RINGO_ANTENNA, "VHF Ringo Antenna");
			provider.addBlock(RadiocraftBlocks.FOLDED_DIPOLE_ANTENNA, "VHF Folded Dipole Antenna");
			provider.addBlock(RadiocraftBlocks.DUMMY_LOAD, "Dummy Load");
			provider.add("tooltip.radiocraft.dummy_load", "A perfect match on every band that hardly radiates: about 10 blocks. Fits on a radio or its coax.");
			provider.addBlock(RadiocraftBlocks.SLIM_JIM_ANTENNA, "Slim Jim Antenna");
			provider.addBlock(RadiocraftBlocks.SATELLITE_DISH, "Microwave Dish");
			provider.addBlock(RadiocraftBlocks.IONOSONDE, "Ionosonde Antenna");
			provider.addBlock(RadiocraftBlocks.FIVE_BTV_ANTENNA, "5-BTV Multiband HF Antenna");
			provider.addBlock(RadiocraftBlocks.IONOSPHERIC_PROCESSOR, "Ionospheric Processor");
			provider.addBlock(RadiocraftBlocks.WEFAX_RECEIVER, "WEFAX Receiver");
			provider.addBlock(RadiocraftBlocks.DESK_CHARGER, "Desk Charger");
			provider.addBlock(RadiocraftBlocks.TOWER_BEAM_THIN, "Thin Tower Beam");
			provider.addBlock(RadiocraftBlocks.TOWER_BEACON, "Tower Beacon");
			provider.addBlock(RadiocraftBlocks.TOWER_BEACON_CONTROLLER, "Tower Beacon Controller");
			provider.add("tooltip.radiocraft.tower_beacon_controller", "Flashes a tower's lights: a redstone signal two seconds on, two seconds off. Found on towers.");
			provider.addBlock(RadiocraftBlocks.TOWER_BEAM, "Tower Beam");
			provider.addBlock(RadiocraftBlocks.TOWER_BEAM_THICK, "Thick Tower Beam");
			provider.addBlock(RadiocraftBlocks.MICROWAVE_HORN, "Microwave Horn Antenna");
			RadiocraftBlocks.DESK_SPEAKERS.forEach((wood, block) -> provider.addBlock(block, woodName(wood) + " Desk Speaker"));
			provider.addBlock(RadiocraftBlocks.LARGE_SPEAKER, "Large Speaker");
			provider.addBlock(RadiocraftBlocks.PREMIUM_SPEAKER_MARBLE, "Marble Premium Speaker");
			provider.addBlock(RadiocraftBlocks.PREMIUM_SPEAKER_IRON, "Iron Premium Speaker");
			provider.addBlock(RadiocraftBlocks.PREMIUM_SPEAKER_GOLD, "Gold Premium Speaker");
			provider.addBlock(RadiocraftBlocks.WALL_SPEAKER, "Wall Speaker");
			provider.addBlock(RadiocraftBlocks.LOUDSPEAKER, "Loudspeaker");
			provider.addBlock(RadiocraftBlocks.DIRECTION_FINDER, "Direction Finder");
			provider.addBlock(RadiocraftBlocks.MAP_DIRECTION_FINDER, "Map Enabled Direction Finder");
			provider.addBlock(RadiocraftBlocks.DIRECTION_FINDER_MODULE, "Direction Finder Module");
			provider.addItem(RadiocraftItems.WORLD_MAP_MODULE, "World Map Module");
			provider.addItem(RadiocraftItems.HANDHELD_DIRECTION_FINDER, "Handheld Direction Finder");
			provider.addBlock(RadiocraftBlocks.MICROWAVE_RADIO, "Microwave Radio");
			provider.addItem(RadiocraftItems.HF_MANPACK, "HF Manpack Radio");
			provider.addItem(RadiocraftItems.MANPACK_MIC, "Manpack Handset");
			provider.add("tooltip.radiocraft.hf_manpack", "A QRP 10m/20m SSB and CW radio you carry. Wear it (sneak right click), open it with right click or the Open Worn Manpack key.");
			provider.add("tooltip.radiocraft.manpack_mic", "Hold right click to talk. Sneak right click hangs it back up.");
			provider.add("key.radiocraft.open_manpack", "Open Worn Manpack");
			provider.addBlock(RadiocraftBlocks.TELEPHONE, "Telephone");
			provider.addBlock(RadiocraftBlocks.PABX, "Phone PABX");
			provider.addBlock(RadiocraftBlocks.PHONE_PA, "Phone PA");
			provider.addBlock(RadiocraftBlocks.FIELD_PHONE, "TA-43 Field Phone");
			provider.add("tooltip.radiocraft.field_phone", "A party line: wire field phones together with data wire, no exchange or power needed. Right click to pick up; the others ring. Up to 6 on a line, or 12 and louder with power on any of them.");
			provider.add("tooltip.radiocraft.phone_pa", "Put it on a Phone PABX, then call its extension: it answers at once and the caller's voice comes out of every speaker touching it or on its data wire. Right click to set its extension and limit it to local calls.");
			provider.add("container.radiocraft.phone_pa", "Phone PA");
			provider.add("screen.radiocraft.phone_pa.extension", "Extension");
			provider.add("screen.radiocraft.phone_pa.extension_label", "Extension (empty: the PABX picks)");
			provider.add("screen.radiocraft.phone_pa.speakers", "Speakers connected: %s");
			provider.add("screen.radiocraft.phone_pa.local_only", "Allow within local PABX only");
			provider.add("screen.radiocraft.phone_pa.save", "Save");
			provider.add("screen.radiocraft.phone_pa.saved", "Saved");
			provider.add("screen.radiocraft.phone_pa.bad_extension", "The extension is a number from 0 to 99999");
			provider.add("screen.radiocraft.phone_pa.extension_taken", "No PABX, or extension %s is already in use");
			// Repeater autopatch
			provider.addBlock(RadiocraftBlocks.REPEATER_AUTOPATCH, "Repeater Autopatch Controller");
			provider.add("tooltip.radiocraft.repeater_autopatch", "Phone calls through a VHF repeater. Link it to a Phone PABX and a repeater (touching, or on data wire). On the air: key its activation code, then a number and * to dial (# cancels); # hangs up. Calls to its extension go out on the repeater. Right click to set its extension and activation code.");
			provider.add("container.radiocraft.repeater_autopatch", "Repeater Autopatch Controller");
			provider.add("screen.radiocraft.repeater_autopatch.extension", "Extension");
			provider.add("screen.radiocraft.repeater_autopatch.extension_label", "Extension (blank: PABX picks)");
			provider.add("screen.radiocraft.repeater_autopatch.code", "Activation code");
			provider.add("screen.radiocraft.repeater_autopatch.code_label", "Activation code (0-9 A-D, max %s)");
			provider.add("screen.radiocraft.repeater_autopatch.repeater", "Repeater: linked");
			provider.add("screen.radiocraft.repeater_autopatch.no_repeater", "Repeater: none linked");
			provider.add("screen.radiocraft.repeater_autopatch.idle", "Idle");
			provider.add("screen.radiocraft.repeater_autopatch.armed", "Armed, dialing: %s");
			provider.add("screen.radiocraft.repeater_autopatch.calling", "Calling %s");
			provider.add("screen.radiocraft.repeater_autopatch.ringing", "Incoming call from %s");
			provider.add("screen.radiocraft.repeater_autopatch.in_call", "In call with %s");
			provider.add("screen.radiocraft.repeater_autopatch.save", "Save");
			provider.add("screen.radiocraft.repeater_autopatch.saved", "Saved");
			provider.add("screen.radiocraft.repeater_autopatch.bad_extension", "The extension is a number from 0 to 99999");
			provider.add("screen.radiocraft.repeater_autopatch.bad_code", "The activation code is up to %s keys: 0-9 and A-D");
			provider.add("screen.radiocraft.repeater_autopatch.extension_taken", "No PABX, or extension %s is already in use");
			// DTMF
			provider.addBlock(RadiocraftBlocks.DTMF_DECODER, "DTMF Decoder");
			provider.addBlock(RadiocraftBlocks.DTMF_ENCODER, "DTMF Encoder");
			provider.add("tooltip.radiocraft.dtmf_decoder", "Touch it to a radio (or run data wire to it) and set a code: the red lamp lights as the code starts coming in over the air, the green one when all of it has, and it pulses or toggles redstone.");
			provider.add("tooltip.radiocraft.dtmf_encoder", "Touch it to a radio (or run data wire to it): key in a number and SEND puts it on the air as touch tones, through repeaters too.");
			provider.add("container.radiocraft.dtmf_decoder", "DTMF Decoder");
			provider.add("container.radiocraft.dtmf_encoder", "DTMF Encoder");
			provider.add("screen.radiocraft.dtmf_decoder.code", "CODE");
			provider.add("screen.radiocraft.dtmf_decoder.code_hint", "0-9, A-D, * and #, up to 16 keys. Enter or SAVE sets it");
			provider.add("screen.radiocraft.dtmf_decoder.save", "SAVE");
			provider.add("screen.radiocraft.dtmf_decoder.pulse", "ON MATCH: PULSE");
			provider.add("screen.radiocraft.dtmf_decoder.toggle", "ON MATCH: TOGGLE");
			provider.add("screen.radiocraft.dtmf_decoder.tip.mode", "Pulse gives a one second redstone pulse for each match; toggle turns the output on, and off at the next match");
			provider.add("screen.radiocraft.dtmf_decoder.receiving", "RECEIVING");
			provider.add("screen.radiocraft.dtmf_decoder.match", "MATCH");
			provider.add("screen.radiocraft.dtmf_decoder.heard", "HEARD: %s");
			provider.add("screen.radiocraft.dtmf_encoder.del", "DEL");
			provider.add("screen.radiocraft.dtmf_encoder.clear", "CLR");
			provider.add("screen.radiocraft.dtmf_encoder.redial", "REDIAL");
			provider.add("screen.radiocraft.dtmf_encoder.send", "SEND");
			provider.add("screen.radiocraft.dtmf_encoder.last", "LAST: %s");
			provider.add("message.radiocraft.dtmf_encoder.no_radio", "No radio next to the encoder is switched on and able to transmit");
			provider.add("tooltip.radiocraft.pabx", "Every telephone needs one: phones touching it or on its data wire call each other by extension. Needs power. PABXes on one data wire or across a microwave link call each other by master number, then extension; with the same master number they act as one PABX. Right click to set its master number, first extension, All-Call extension and name.");
			provider.add("container.radiocraft.pabx", "Phone PABX");
			provider.add("screen.radiocraft.pabx.master", "Master number");
			provider.add("screen.radiocraft.pabx.first", "First extension");
			provider.add("screen.radiocraft.pabx.all_call", "All-Call extension");
			provider.add("screen.radiocraft.pabx.name", "PABX name");
			provider.add("screen.radiocraft.pabx.master_label", "Master number (%s digits, or none)");
			provider.add("screen.radiocraft.pabx.first_label", "First extension");
			provider.add("screen.radiocraft.pabx.all_call_label", "All-Call extension (or none)");
			provider.add("screen.radiocraft.pabx.name_label", "PABX name (up to %s)");
			provider.add("screen.radiocraft.pabx.save", "Save");
			provider.add("screen.radiocraft.pabx.powered", "Powered");
			provider.add("screen.radiocraft.pabx.no_power", "No power");
			provider.add("screen.radiocraft.pabx.saved", "Saved");
			provider.add("screen.radiocraft.pabx.master_length", "A master number has exactly %s digits, or none");
			provider.add("screen.radiocraft.pabx.bad_all_call", "The All-Call extension is a number from 0 to 99999, or none");
			provider.add("screen.radiocraft.pabx.all_call_taken", "A phone already has extension %s");
			provider.add("screen.radiocraft.pabx.ext_in_use", "EXT IN USE: %s");
			provider.add("screen.radiocraft.pabx.bad_extension", "The first extension is a number from 0 to 99999");
			provider.addBlock(RadiocraftBlocks.WEATHER_STATION, "Weather Station");
			// The alternator only exists with Create (compat/create), so it is named by key.
			provider.add("block.radiocraft.alternator", "Alternator");
			provider.add("radiocraft.goggles.alternator.production", "Energy Production:");
			provider.add("container.radiocraft.microwave_radio", "Microwave Radio");
			provider.add("container.radiocraft.telephone", "Telephone");
			provider.add("tooltip.radiocraft.microwave_radio", "A digital point to multipoint link over microwave dishes on its coax. Carries APRS between H19s, bearings between direction finders, and telephone calls.");
			provider.add("tooltip.radiocraft.microwave_dish", "One end of a microwave link: coax it to a microwave radio and point it at the other end's dish.");
			provider.add("tooltip.radiocraft.telephone", "Put it on a powered Phone PABX (touching, or on its data wire) and dial another phone's extension, or another PABX's master number and an extension. Sneak and right click to pick up the handset; place it on the side of a block to hang it on the wall.");
			provider.add("tooltip.radiocraft.weather_station", "Put it up high in the open. Any H19 APRS terminal within 64 blocks sends its weather in its beacons.");
			provider.add("screen.radiocraft.microwave.identity_taken", "Another microwave radio already has that callsign and ID");
			provider.add("screen.radiocraft.telephone.extension_taken", "No PABX, or that extension is already in use");
			provider.add("tooltip.radiocraft.handheld_direction_finder", "Right click to set the frequency. Held, it beeps faster the more you face a station transmitting there.");
			provider.add("container.radiocraft.direction_finder", "Direction Finder");
			provider.add("container.radiocraft.map_direction_finder", "Map Enabled Direction Finder");
			provider.add("tooltip.radiocraft.speaker", "Place it touching a radio: the radio sounds fuller than through its own little speaker.");
			provider.add("tooltip.radiocraft.premium_speaker", "Place it touching a radio: the radio's whole audio range, as clean as it gets.");
			provider.add("tooltip.radiocraft.wall_speaker", "Run wire from a radio to it: plays the radio here. Several can share one radio. A little rough.");
			provider.add("tooltip.radiocraft.loudspeaker", "Run wire from a radio to it: plays the radio loud enough to hear far away, with some distortion.");
			provider.add("tooltip.radiocraft.direction_finder", "Stack 3 to 8 direction finder modules against it, each with the same kind of antenna, and tune to a signal.");
			provider.add("tooltip.radiocraft.map_direction_finder", "A direction finder that plots its fix on a map of the world.");
			provider.add("tooltip.radiocraft.direction_finder_module", "One antenna input of a direction finder: coax to an antenna, and stack it against the direction finder or another module.");
			provider.add("tooltip.radiocraft.world_map_module", "Craft it with a direction finder to plot fixes on a map. Cartographers sell them.");

			provider.addEntityType(RadiocraftEntityTypes.ANTENNA_WIRE, "Antenna Wire");

			provider.add(Radiocraft.translationKey("commands", "callsign.list.empty"), "No callsigns found");
			provider.add(Radiocraft.translationKey("commands", "callsign.get.success"), "%s's callsign is %s of license class %s.");
			provider.add(Radiocraft.translationKey("commands", "callsign.get.failure"), "%s does not have a callsign.");
			provider.add(Radiocraft.translationKey("commands", "callsign.get.failure.multiple"), "Cannot get the callsign of multiple targets at once.");
            provider.add(Radiocraft.translationKey("commands", "callsign.get.failure.permission"), "You must have permission to change callsigns to use this command.");
			provider.add(Radiocraft.translationKey("commands", "callsign.reset.success"), "%s's callsign has been reset.");
			provider.add(Radiocraft.translationKey("commands", "callsign.reset.failure.multiple"), "Cannot reset the callsign of multiple targets at once.");
			provider.add(Radiocraft.translationKey("commands", "callsign.set.success"), "%s's callsign has been set to %s and license class %s.");
			provider.add(Radiocraft.translationKey("commands", "callsign.set.failure.multiple"), "Cannot reset the callsign of multiple targets at once.");
			provider.add(Radiocraft.translationKey("commands", "callsign.generate.failure.multiple"), "Cannot generate callsigns for multiple targets at once.");
			provider.add(Radiocraft.translationKey("commands", "callsign.generate.success"), "%s now has callsign %s and license class %s.");
			provider.add(Radiocraft.translationKey("commands", "callsign.generate.existing"), "%s already has callsign %s and license class %s.");
			provider.add(Radiocraft.translationKey("commands", "callsign.generate.collision"), "%s could not be assigned callsign %s because it is already used by %s. Please assign a unique callsign manually (e.g., %s).");
			provider.add(Radiocraft.translationKey("commands", "callsign.generate.online.empty"), "No players are currently online.");
			provider.add(Radiocraft.translationKey("commands", "callsign.generate.online.none"), "All online players already have callsigns.");

			provider.add(Radiocraft.translationKey("screen", "chargecontroller.power"), "%s FE/s");
			provider.add(Radiocraft.translationKey("screen", "radio.tx"), "Transmitting");
			provider.add(Radiocraft.translationKey("screen", "radio.rx"), "Receiving");

			provider.add(Radiocraft.translationKey("screen", "desk_charger"), "Desk Charger");
			
			// Container titles for new GUIs
			provider.add("container.radiocraft.radio_interface", "Radio Interface");
			provider.add("container.radiocraft.aprs_terminal", "H19 APRS Terminal");
			provider.add("container.radiocraft.teletype", "Teletype Machine");
			provider.add(Radiocraft.translationKey("screen", "teletype.set_callsign_first"), "Set the teletype's callsign first");
			provider.add(Radiocraft.translationKey("screen", "teletype.no_radio"), "No radio switched on next to the teletype");
			provider.add(Radiocraft.translationKey("screen", "teletype.send_failed"), "Nothing sent: the radio is busy or cannot take RTTY");
			provider.add(Radiocraft.translationKey("screen", "teletype.paper"), "PAPER: %s LINES");
			provider.add(Radiocraft.translationKey("screen", "teletype.paper_out"), "PAPER OUT");
			provider.add(Radiocraft.translationKey("screen", "teletype.edit_callsign"), "TELETYPE CALLSIGN: ENTER saves, empty claims yours");
			provider.add(Radiocraft.translationKey("screen", "teletype.tip.solar"), "SOLAR WX: print the solar weather (needs a radio with an antenna next to it)");
			provider.add(Radiocraft.translationKey("screen", "teletype.tip.callsign"), "Callsign: click to change (empty claims your own)");
			provider.add(Radiocraft.translationKey("screen", "teletype.tip.set_by"), "%s, set by %s");
			provider.add(Radiocraft.translationKey("screen", "teletype.tip.paper_out"), "Out of paper: right click the teletype with paper, or feed it with a hopper");
			provider.add("item.radiocraft.aprs_map", "APRS Map");
			provider.add("item.radiocraft.wefax_chart.radar", "WEFAX Radar Chart");
			provider.add("item.radiocraft.wefax_chart.forecast", "WEFAX Forecast Chart");
			provider.add("item.radiocraft.wefax_chart.surface", "WEFAX Surface Analysis");
			provider.add("item.radiocraft.wefax_chart.solar", "WEFAX Solar Weather Report");
			provider.add("screen.radiocraft.wefax.take", "Click to take the sheet");
			provider.add("tooltip.radiocraft.wefax_receiver", "Put a radio with an antenna beside it and paper in it: prints weather charts and the solar report.");
			provider.add("screen.radiocraft.wefax.weather", "WX REPT: print the next weather chart (radar, forecast, surface analysis)");
			provider.add("screen.radiocraft.wefax.solar", "SOLR WX: print the solar weather report");
			provider.add("screen.radiocraft.wefax.auto", "AUTO: print a forecast when dangerous weather is on its way");
			provider.add("screen.radiocraft.wefax.auto_on", "On");
			provider.add("screen.radiocraft.wefax.auto_off", "Off");
			provider.add("screen.radiocraft.wefax.paper", "Paper: %s sheets");
			provider.add("screen.radiocraft.wefax.paper_full", "The paper tray is full");
			provider.add("screen.radiocraft.wefax.radio", "Power: a radio is beside it");
			provider.add("screen.radiocraft.wefax.no_radio", "No radio with an antenna beside it");
			provider.add("screen.radiocraft.wefax.processor_offline", "The Ionospheric Processor beside it has no ionosonde");
			provider.add("screen.radiocraft.wefax.requesting", "Asking for the solar weather...");
			provider.add("screen.radiocraft.wefax.no_answer", "WEFAX: no Ionospheric Processor answered");
			provider.add("tooltip.radiocraft.ionospheric_processor", "Coax to an Ionosonde Antenna lets it read the sky. Through a radio it touches it answers solar weather requests and warns of blackouts.");
			provider.add("container.radiocraft.ionospheric_processor", "Ionospheric Processor");
			provider.add("screen.radiocraft.ionospheric_processor.title", "IONOSPHERIC PROCESSOR %s");
			provider.add("screen.radiocraft.ionospheric_processor.sounder_online", "SOUNDER   ONLINE");
			provider.add("screen.radiocraft.ionospheric_processor.sounder_offline", "SOUNDER   NO IONOSONDE");
			provider.add("screen.radiocraft.ionospheric_processor.radio", "RADIO     %s MHZ");
			provider.add("screen.radiocraft.ionospheric_processor.no_radio", "RADIO     NONE ON");
			provider.add("screen.radiocraft.ionospheric_processor.forecast_blackout", "FORECAST  BLACKOUT IN %s MIN");
			provider.add("screen.radiocraft.ionospheric_processor.forecast_quiet", "FORECAST  NO BLACKOUT");
			provider.add("screen.radiocraft.ionospheric_processor.sent", "SENT      %s BULLETINS, %s ALERTS");
			provider.add("screen.radiocraft.ionospheric_processor.bulletins", "[1] TIMED BULLETINS ..... %s");
			provider.add("screen.radiocraft.ionospheric_processor.alerts", "[2] BLACKOUT ALERTS ..... %s");
			provider.add("screen.radiocraft.ionospheric_processor.send", "[3] SEND BULLETIN NOW");
			provider.add("screen.radiocraft.ionospheric_processor.on", "ON");
			provider.add("screen.radiocraft.ionospheric_processor.off", "OFF");
			provider.add("screen.radiocraft.ionospheric_processor.nowhere", "Nothing to send it through: no radio on and no WEFAX beside it");
			provider.add("screen.radiocraft.ionospheric_processor.no_ionosonde", "No Ionosonde Antenna on its coax");
			provider.add("message.radiocraft.solar_weather_station.blackout_expected", "Blackout expected in about %s min");
			provider.add("message.radiocraft.lightning_fried_radio", "The lightning fried the radio you were carrying");
			provider.add("screen.radiocraft.wefax.no_paper", "Out of paper");
			provider.add("screen.radiocraft.wefax.busy", "Still printing");
			provider.add("item.radiocraft.aprs_map.from", "From the H19 at %s, %s, %s");
			provider.add("item.radiocraft.aprs_map.wall", "APRS Map (%s x %s wall)");
			provider.add("item.radiocraft.aprs_map.tile", "Row %s, column %s of %s x %s (top left is north west)");
			provider.add("container.radiocraft.radio_fax", "Radio Fax");
			provider.add("container.radiocraft.duplexer", "Duplexer");
			provider.add("container.radiocraft.antenna_tuner", "Antenna Tuner");

			// Antenna tuner readout (indices are AntennaTunerBlockEntity.STATUS_*)
			provider.add(Radiocraft.translationKey("screen", "antenna_tuner.status.0"), "No radio on this coax");
			provider.add(Radiocraft.translationKey("screen", "antenna_tuner.status.1"), "Radio is off");
			provider.add(Radiocraft.translationKey("screen", "antenna_tuner.status.2"), "No antenna recognized");
			provider.add(Radiocraft.translationKey("screen", "antenna_tuner.status.3"), "Tuned");
			provider.add(Radiocraft.translationKey("screen", "antenna_tuner.status.4"), "Antenna too far off to match: tuned as close as it goes");
			provider.add(Radiocraft.translationKey("screen", "antenna_tuner.status.5"), "Overdraw: too many radios or antennas");
			provider.add(Radiocraft.translationKey("screen", "antenna_tuner.status.6"), "Tuners disabled on this server");
			provider.add(Radiocraft.translationKey("screen", "antenna_tuner.antenna_swr"), "Antenna SWR: %s");
			provider.add(Radiocraft.translationKey("screen", "antenna_tuner.status.7"), "Turn the dials: green is a match");
			provider.add(Radiocraft.translationKey("tooltip", "antenna_tuner"), "Put it anywhere on the coax between a radio and its antenna. Turn its three dials until the lamp is solid green: red blinks faster as you get closer, yellow-green below SWR 3. The further off resonance the antenna, the more power is lost in the tuner.");
			provider.add(Radiocraft.translationKey("tooltip", "auto_tuner"), "Put it on the coax between a radio and its antenna, with power. Tunes itself whenever the radio changes band or moves 1 MHz. Shows TUNE FAIL on the radio when the antenna is too far off.");
			provider.add(Radiocraft.translationKey("tooltip", "balun_one_to_one"), "Feed point for dipoles, inverted Vs, end-feds, quarter wave verticals and Moxons.");
			provider.add(Radiocraft.translationKey("tooltip", "balun_four_to_one"), "Feed point for off-centre-fed dipoles (short arm a third to two thirds of the long one, matched on the even harmonics too) and delta loops.");
			provider.add(Radiocraft.translationKey("tooltip", "balun_two_to_one"), "Feed point for quad loops.");
			provider.add(Radiocraft.translationKey("tooltip", "balun_nine_to_one"), "Feed point for a random wire: one wire of any shape, at least half a wavelength long. Needs an antenna tuner.");
			provider.add("container.radiocraft.vhf_repeater", "VHF Repeater");
			provider.add(Radiocraft.translationKey("screen", "repeater.rx_frequency"), "Input (receive) frequency, MHz");
			provider.add(Radiocraft.translationKey("screen", "repeater.tx_frequency"), "Output (transmit) frequency, MHz");
			provider.add(Radiocraft.translationKey("screen", "repeater.frequency"), "Frequency");
			provider.add(Radiocraft.translationKey("screen", "repeater.callsign"), "Callsign, sent in Morse");
			provider.add(Radiocraft.translationKey("screen", "repeater.rx_tone"), "Access tone (CTCSS RX)");
			provider.add(Radiocraft.translationKey("screen", "repeater.tx_tone"), "Output tone (CTCSS TX)");
			provider.add(Radiocraft.translationKey("screen", "repeater.id_off"), "ID: off");
			provider.add(Radiocraft.translationKey("screen", "repeater.id_every"), "ID every %s min");
			provider.add(Radiocraft.translationKey("screen", "repeater.hang"), "Hang time: %s s");
			provider.add(Radiocraft.translationKey("screen", "repeater.timeout"), "Timeout: %s");
			provider.add(Radiocraft.translationKey("screen", "repeater.timeout_off"), "Timeout: off");
			provider.add(Radiocraft.translationKey("screen", "repeater.tip.power"), "Power");
			provider.add(Radiocraft.translationKey("screen", "repeater.tip.prt_rx"), "Input frequency: %s MHz");
			provider.add(Radiocraft.translationKey("screen", "repeater.tip.prt_tx"), "Output frequency: %s MHz");
			provider.add(Radiocraft.translationKey("screen", "repeater.tip.cw_id"), "Callsign ID: %s");
			provider.add(Radiocraft.translationKey("screen", "repeater.tip.ctc_rx"), "Access tone: %s");
			provider.add(Radiocraft.translationKey("screen", "repeater.tip.ctc_tx"), "Output tone: %s");
			provider.add(Radiocraft.translationKey("screen", "repeater.tip.mic"), "Mic: talk out through the repeater from here");
			provider.add(Radiocraft.translationKey("screen", "repeater.tip.tx"), "Transmitter: repeat what comes in");
			provider.add(Radiocraft.translationKey("screen", "repeater.tip.speaker"), "Speaker: listen to the input here");

			// Radio interface, APRS terminal and transfer interface
			provider.add(Radiocraft.translationKey("screen", "station.no_interface"), "Not joined to a Radio Interface: place it touching one, or touching a terminal that is");
			provider.add(Radiocraft.translationKey("screen", "station.set_callsign_first"), "Set a callsign on the Radio Interface first");
			provider.add(Radiocraft.translationKey("screen", "station.no_radio"), "No radio switched on next to the Radio Interface");
			provider.add(Radiocraft.translationKey("screen", "station.send_failed"), "Nothing sent: check the message, and that the Radio Interface is set to TX");
			provider.add(Radiocraft.translationKey("screen", "radio_interface.no_callsign"), "You have no callsign to claim it with");
			provider.add(Radiocraft.translationKey("screen", "radio_interface.callsign"), "CALLSIGN");
			provider.add(Radiocraft.translationKey("screen", "radio_interface.callsign_hint"), "Enter saves; leave it empty to claim your own");
			provider.add(Radiocraft.translationKey("screen", "radio_interface.set_by"), "Set by %s");
			provider.add(Radiocraft.translationKey("screen", "radio_interface.tx_on"), "TX ON");
			provider.add(Radiocraft.translationKey("screen", "radio_interface.tx_off"), "TX OFF: LISTEN ONLY");
			provider.add(Radiocraft.translationKey("screen", "radio_interface.tip.tx"), "Switch off to only listen: nothing goes out from this station, digipeats included");
			provider.add(Radiocraft.translationKey("screen", "aprs.no_beacons"), "No beacons heard");
			provider.add(Radiocraft.translationKey("screen", "aprs.no_messages"), "No messages");
			provider.add(Radiocraft.translationKey("screen", "aprs.edit_comment"), "BEACON COMMENT");
			provider.add(Radiocraft.translationKey("screen", "aprs.edit_keys"), "ENTER saves, ESC cancels");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.beacon"), "Beacon now: send this station's position");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.unread"), "New message! Open MSG to read it");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.no_unread"), "No new messages");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.callsign"), "Station callsign: set on the Radio Interface");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.no_interface"), "Not joined to a Radio Interface");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.comment"), "Beacon comment, up to 30 characters: click to change");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.icon"), "Map icon: click for the next one");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.beacon_enable"), "Beacon by itself every few minutes (a redstone pulse beacons too)");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.digipeat_on"), "Digipeating ON: the Radio Interface repeats packets heard (click to stop)");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.digipeat_off"), "Digipeating OFF: click to have the Radio Interface repeat packets heard, up to 8 hops");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.rpt"), "RPT: lit while repeating a packet");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.heard"), "Heard %s min ago, direct");
			provider.add(Radiocraft.translationKey("screen", "aprs.tip.heard_via"), "Heard %s min ago, via %s digipeater(s)");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.busy"), "A fax is already going out or coming in");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.nothing_to_send"), "Put a book, paper, map or enchanted book in the document slot");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.no_callsign"), "Type the callsign to fax to");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.to_self"), "That is this station's own callsign");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.no_radio"), "No radio switched on next to the fax or its radio interface");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.tip.send"), "SEND: fax the document to the callsign on the LCD (ENTER does too)");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.tip.to"), "TO: the callsign to fax to; click a recent one to use it again");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.tip.my"), "MY: this fax's callsign (empty takes yours, or its radio interface's)");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.tip.document"), "The document to send: a book, paper, a map or an enchanted book (used up when sent)");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.tip.tray"), "Copies received");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.tip.supplies"), "Blank paper, books and maps it prints on");
			provider.add(Radiocraft.translationKey("screen", "radio_fax.log"), "LOG");
			provider.add(Radiocraft.translationKey("screen", "teletype.tip.roll"), "Sneak and right click the machine with an empty hand to tear the roll off");
			provider.add(Radiocraft.translationKey("commands", "aprs.sent"), "APRS packet delivered to %s APRS terminal(s)");
			provider.add(Radiocraft.translationKey("commands", "aprs.bad_frequency"), "Frequency must be in MHz (such as 144.390) or \"all\"");
			
			// Digital Interface tab labels
			provider.add(Radiocraft.translationKey("gui", "tab.arps"), "ARPS");
			provider.add(Radiocraft.translationKey("gui", "tab.msg"), "MSG");
			provider.add(Radiocraft.translationKey("gui", "tab.rtty"), "RTTY");
			provider.add(Radiocraft.translationKey("gui", "tab.files"), "FILES");

			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.header"), "%s at %s");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.resonant"), "Resonant on the %s band: SWR %s at %s MHz.");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.too_short"), "Near the %s band but too short: SWR %s there, resonant at %s MHz. Lengthen it.");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.too_long"), "Near the %s band but too long: SWR %s there, resonant at %s MHz. Shorten it.");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.poor_match"), "Best on the %s band, but the SWR there is %s at best: use a tuner.");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.not_resonant"), "Not resonant on any band.");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.sweep"), "SWR by band: %s");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.overdraw"), "%s antennas on one feedline: a radio on it would see a bad match.");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.nothing"), "Use it on an antenna, its balun, its coax or its radio.");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.no_antenna"), "No antenna on this feedline.");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.handheld_with_stats"), "You are holding a handheld radio; the antenna is %s m long. SWR at %s MHz is %s");
			provider.add(Radiocraft.translationKey("message", "antenna_analyzer.unknown_handheld_antenna"), "You are holding a handheld radio, but it's using an antenna we know nothing about.");

            provider.add(Radiocraft.translationKey("tooltip", "antenna_analyzer"), "Right click an antenna, its balun, its coax or its radio: the band it is resonant on, whether it is too short or too long, and its SWR on every band.");
            provider.add(Radiocraft.translationKey("tooltip", "parrot_controller"), "Repeats received messages back out the connected radio. Affectionately called a \"parrot\" repeater.");
            provider.add(Radiocraft.translationKey("message", "parrot_controller.delay"), "Repeat delay: %ss");
            provider.add(Radiocraft.translationKey("message", "parrot_controller.linked"), "Linked to the radio to the %s");
            for(String side : new String[] {"north", "east", "south", "west"})
                provider.add(Radiocraft.translationKey("message", "side." + side), side);

            provider.add(Radiocraft.translationKey("tooltip", "teletype"), "RTTY through the radios it touches, printed on its own paper. SOLAR WX prints the solar conditions, which it reads through a radio with an antenna (not a dummy load) next to it. A comparator reads 1 (blackout) to 6 (great).");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.report"), "Solar conditions: %s (noise floor %s%%). %s band: %s");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.no_data"), "No solar weather data yet");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.no_radio"), "No signal: the station needs a radio next to it with an antenna (not a dummy load)");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.good"), "Good");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.fair"), "Fair");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.poor"), "Poor");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.closed"), "Closed");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.normal"), "Normal");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.aurora"), "Aurora");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.aurora_active"), "Aurora: active beyond Z ±%s");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.aurora_expected"), "Aurora expected: storm on the way, watch beyond Z ±%s");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.header"), "Solar Conditions:");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.overall"), "Overall: %s");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.noise"), "Noise Floor: %s%%");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.band"), "%s Band: %s");
            provider.add("subtitles.radiocraft.teletype", "Teletype printing");
            provider.add("subtitles.radiocraft.teletype_ding", "Teletype bell");
            provider.add("subtitles.radiocraft.redstone_rfi", "Radio picks up interference");
            provider.add("subtitles.radiocraft.h19_running", "Terminal whirs");
            provider.add("subtitles.radiocraft.h19_bell", "Terminal bell");
            provider.add(Radiocraft.translationKey("solar_event", "none"), "None");
            provider.add(Radiocraft.translationKey("solar_event", "great"), "Great");
            provider.add(Radiocraft.translationKey("solar_event", "good"), "Good");
            provider.add(Radiocraft.translationKey("solar_event", "average"), "Average");
            provider.add(Radiocraft.translationKey("solar_event", "fair"), "Fair");
            provider.add(Radiocraft.translationKey("solar_event", "poor"), "Poor");
            provider.add(Radiocraft.translationKey("solar_event", "blackout"), "Blackout");

            provider.add(Radiocraft.translationKey("tooltip", "not_implemented"), "§7Not Implemented (Decorative Only)§r");
            provider.add(Radiocraft.translationKey("tooltip", "not_implemented_crafting_only"), "§7Not Implemented (Crafting Ingredient Only)§r");
            provider.add(Radiocraft.translationKey("tooltip", "passthrough"), "Use on a plain solid block: it keeps its look but lets wire and coax run straight through it. Break it to get both back.");
            provider.add(Radiocraft.translationKey("tooltip", "wire"), "Carries power between solar panels, charge controllers, batteries and radios. Runs along the ground, up walls and over steps.");
            provider.add(Radiocraft.translationKey("tooltip", "waterproof_wire"), "Power wire that can be laid under water. Runs along the ground, up walls and over steps.");
            provider.add(Radiocraft.translationKey("tooltip", "data_wire"), "Links a radio to accessories, wired speakers and stations that are not beside it. Carries no power. Runs along the ground, up walls and over steps.");
            provider.add(Radiocraft.translationKey("tooltip", "coax_wire"), "Carries the signal between a radio and its antenna's balun. Runs along the ground, up walls and over steps.");
            provider.add(Radiocraft.translationKey("tooltip", "wire_bundle"), "Use on a wire of another kind to lay both in one block (up to 7). Sneak + right click a bundle with an empty hand to take one out.");
            provider.add("key.categories.radiocraft", "RadioCraft");
            provider.add("key.radiocraft.worn_ptt", "Talk on worn radio");
            provider.add("death.attack.radiocraft.electrocution", "%1$s was electrocuted");
            provider.add("key.radiocraft.cw_key", "CW key (in a radio screen)");
            provider.add(Radiocraft.translationKey("message", "moderate.on_air"), "On air: %s, %s MHz");
            provider.add(Radiocraft.translationKey("commands", "moderate.all"), "Listening to all radio traffic; /radio moderate off to stop");
            provider.add(Radiocraft.translationKey("commands", "moderate.one"), "Listening to %s's radio traffic; /radio moderate off to stop");
            provider.add(Radiocraft.translationKey("commands", "moderate.off"), "Stopped listening to radio traffic");
            provider.add(Radiocraft.translationKey("commands", "moderate.not_on"), "You were not listening to radio traffic");
            provider.add(Radiocraft.translationKey("message", "moderation.notice.radio"), "This server records voice sent over radios for moderation (kept %s days).");
            provider.add(Radiocraft.translationKey("message", "moderation.notice.proximity"), "This server records voice chat for moderation (kept %s days).");
            provider.add(Radiocraft.translationKey("message", "moderation.notice.both"), "This server records voice chat and voice sent over radios for moderation (kept %s days).");
            provider.add(Radiocraft.translationKey("commands", "moderation.not_running"), "The moderation log is not running");
            provider.add(Radiocraft.translationKey("commands", "moderation.status"), "Moderation log: radio %s, proximity %s, metadata only %s, kept %s days; %s transmissions logged, %s on the air; %s MB; %s frames dropped");
            provider.add(Radiocraft.translationKey("commands", "moderation.listed"), "%s of %s transmissions shown, newest first");
            provider.add(Radiocraft.translationKey("commands", "moderation.purged"), "Moderation log retention run: %s files deleted");
            provider.add(Radiocraft.translationKey("commands", "moderation.flagged"), "Transmission %s flagged: kept until unflagged");
            provider.add(Radiocraft.translationKey("commands", "moderation.unflagged"), "Transmission %s unflagged");
            provider.add(Radiocraft.translationKey("commands", "moderation.unknown"), "No transmission %s in the moderation log");
            provider.add(Radiocraft.translationKey("commands", "moderation.exported"), "Exported %s file(s) to %s");
            addModerationComputer(provider);
            provider.add(Radiocraft.translationKey("screen", "cw.chat"), "Type to send as Morse");
            provider.add(Radiocraft.translationKey("screen", "cw.send"), "Send");
            provider.add(Radiocraft.translationKey("screen", "cw.sending"), "Sending...");
            provider.add(Radiocraft.translationKey("screen", "callsign.title"), "Choose your callsign");
            provider.add(Radiocraft.translationKey("screen", "callsign.form"), "A real prefix (K, W, VE, G, 2E, DL, JA...), a digit, then one to three letters: K1ABC, VE3XY");
            provider.add(Radiocraft.translationKey("screen", "callsign.digits"), "Digits allowed: %s");
            provider.add(Radiocraft.translationKey("screen", "callsign.box"), "Callsign");
            provider.add(Radiocraft.translationKey("screen", "callsign.claim"), "Claim");
            provider.add(Radiocraft.translationKey("screen", "callsign.later"), "Later");
            provider.add(Radiocraft.translationKey("screen", "callsign.choose"), "Choose one for me");
            provider.add(Radiocraft.translationKey("message", "callsign.claimed"), "Your callsign is now %s");
            provider.add(Radiocraft.translationKey("message", "callsign.ok"), "Done");
            provider.add(Radiocraft.translationKey("message", "callsign.disabled"), "Callsigns are handed out by the server operators here");
            provider.add(Radiocraft.translationKey("message", "callsign.already_has"), "You already have a callsign; an operator can reset it");
            provider.add(Radiocraft.translationKey("message", "callsign.invalid"), "Not a valid callsign: a real prefix (K, W, VE, G...), a digit (%s), then one to three letters");
            provider.add(Radiocraft.translationKey("message", "callsign.taken"), "That callsign is taken");
            provider.add(Radiocraft.translationKey("message", "repeater_callsign.either"), "A repeater callsign is K, N, W, G, M, F or I, then %s, then two letters, or your own callsign");
            provider.add(Radiocraft.translationKey("message", "repeater_callsign.auto"), "A repeater callsign here is K, N, W, G, M, F or I, then %s, then two letters");
            provider.add(Radiocraft.translationKey("message", "repeater_callsign.own"), "Repeaters here carry their owner's callsign (yours, once you have one)");
            provider.add(Radiocraft.translationKey("screen", "repeater.callsign_auto"), "Make one up");
            provider.add(Radiocraft.translationKey("screen", "repeater.callsign_own"), "Mine");
            provider.add(Radiocraft.translationKey("message", "solar_weather_station.sporadic_e"), "Sporadic E: VHF open for long range");
            provider.add("death.attack.radiocraft.electrocution.player", "%1$s was electrocuted whilst fighting %2$s");
            provider.add(Radiocraft.translationKey("tooltip", "microphone"), "Stands beside a radio. Hold right click on it to talk; sneak and right click to lock talking on. Speak close: it gets quieter as you step away. Lets go after a timeout.");
            provider.add(Radiocraft.translationKey("message", "desk_mic.locked"), "Desk mic locked on (lets go after %ss)");
            provider.add(Radiocraft.translationKey("message", "desk_mic.unlocked"), "Desk mic released");
            provider.add(Radiocraft.translationKey("tooltip", "tower_foothold"), "A rung for climbing a tower, or standing on. Place it against a wall like a ladder.");
            provider.add(Radiocraft.translationKey("tooltip", "old_tower_foothold"), "Weathered. Now and then one gives way under a climber.");
            provider.add(Radiocraft.translationKey("commands", "guide.given"), "Gave %s the ARRL Radiocraft Handbook");
            provider.add(Radiocraft.translationKey("commands", "guide.no_patchouli"), "The ARRL Radiocraft Handbook needs the Patchouli mod, which is not installed");
            provider.add(Radiocraft.translationKey("tooltip", "antenna_pole"), "Holds the end of an antenna wire. Use antenna wire on this and another holder to string a wire between them.");
            provider.add(Radiocraft.translationKey("tooltip", "antenna_connector"), "Joins antenna wires and coax: a wire holder you can also run coax into.");
            provider.add(Radiocraft.translationKey("tooltip", "j_pole_antenna"), "VHF antenna for 2m base stations and repeaters. Connect it with coax.");
            provider.add(Radiocraft.translationKey("tooltip", "ringo_antenna"), "Commercial 2m vertical: 10% more gain than a J-pole, and DC grounded, so lightning seldom does damage.");
            provider.add(Radiocraft.translationKey("tooltip", "folded_dipole_antenna"), "Commercial two-bay 2m folded dipole for towers: 25% more gain than a J-pole, and DC grounded, so lightning seldom does damage.");
            provider.add(Radiocraft.translationKey("tooltip", "five_btv_antenna"), "Trap vertical, seven blocks tall: matched on every HF band and VHF. On HF it sends its power low, so it reaches less by line of sight and further by skip. Coax goes to the base.");
            provider.add(Radiocraft.translationKey("tooltip", "ionosonde"), "Sounding antenna for the Ionospheric Processor. On a radio it hears VHF well but matches poorly everywhere, worst on HF.");
            provider.add(Radiocraft.translationKey("tooltip", "slim_jim_antenna"), "VHF antenna for 2m base stations and repeaters. Connect it with coax.");
            provider.add(Radiocraft.translationKey("tooltip", "yagi_antenna"), "Directional VHF antenna: strongest in the direction it points. Connect it with coax.");
            provider.add(Radiocraft.translationKey("tooltip", "solar_panel"), "Makes power in daylight. Wire it to a charge controller.");
            provider.add(Radiocraft.translationKey("tooltip", "charge_controller"), "Sits between solar panels and batteries and manages the charge. Wire panels in and batteries out.");
            provider.add(Radiocraft.translationKey("tooltip", "large_battery"), "Stores power for the station: charged from a charge controller, it runs the radios on its wire. Batteries wired together share charge evenly.");
            provider.add(Radiocraft.translationKey("tooltip", "hf_radio_10m"), "HF transceiver for the 10m band (28 MHz). SSB voice and CW. Needs a 10m antenna on coax.");
            provider.add(Radiocraft.translationKey("tooltip", "hf_radio_20m"), "HF transceiver for the 20m band (14 MHz). SSB voice and CW. Needs a 20m antenna on coax.");
            provider.add(Radiocraft.translationKey("tooltip", "hf_radio_40m"), "HF transceiver for the 40m band (7 MHz). SSB voice and CW. Needs a 40m antenna on coax.");
            provider.add(Radiocraft.translationKey("tooltip", "hf_radio_80m"), "HF transceiver for the 80m band (3.5 MHz). SSB voice and CW. Needs an 80m antenna on coax.");
            provider.add(Radiocraft.translationKey("tooltip", "qrp_radio_20m"), "Low power 20m transceiver: shorter range, lighter to build. Needs a 20m antenna on coax.");
            provider.add(Radiocraft.translationKey("tooltip", "qrp_radio_40m"), "Low power 40m transceiver: shorter range, lighter to build. Needs a 40m antenna on coax.");
            provider.add(Radiocraft.translationKey("tooltip", "hf_receiver"), "Listens on the HF bands. Receive only.");
            provider.add(Radiocraft.translationKey("tooltip", "vhf_base_station"), "2m FM transceiver for local contacts and repeaters. Needs a VHF antenna on coax.");
            provider.add(Radiocraft.translationKey("tooltip", "vhf_receiver"), "Listens on 2m FM. Receive only.");
            provider.add(Radiocraft.translationKey("tooltip", "energy_stored"), "Energy: %s / %s FE");
            provider.add(Radiocraft.translationKey("tooltip", "energy_stored_joules"), "Energy: %s / %s J");
            provider.add(Radiocraft.translationKey("tooltip", "battery_percentage"), "Charge: %s%%");
			provider.add(Radiocraft.translationKey("tooltip", "small_battery"), "A common small battery, typically starts with 50-70% charge");
			provider.add(Radiocraft.translationKey("tooltip", "vhf_handheld_battery_swap"), "Click battery on VHF Handheld to swap batteries");
			provider.add(Radiocraft.translationKey("tooltip", "vhf_handheld_aprs"), "APRS module installed");
			provider.add(Radiocraft.translationKey("message", "scroll_tuned"), "%s MHz");
			provider.add(Radiocraft.translationKey("tooltip", "aprs_module"), "Craft with a VHF handheld to install it: APRS opens its station list and messages");
			provider.add(Radiocraft.translationKey("tooltip", "mars_mod"), "Craft with an HF radio, VHF radio, handheld or manpack: it then transmits past the band edges (HF a quarter of the band each side, VHF up to 174 MHz)");
			provider.add(Radiocraft.translationKey("tooltip", "mars_mod.contraband"), "Contraband: not legal for amateur use. Out-of-band signals splatter.");
			provider.add(Radiocraft.translationKey("tooltip", "mars_mod.fitted"), "MARS mod fitted: transmits out of band");
			// The Radio Cloner (1.12 playtest fixes 2).
			provider.add(Radiocraft.translationKey("tooltip", "cloner.empty"), "Empty: use it on a radio to read it");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.holds"), "Holds: %s, read from the %s");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.channels"), "%s channels, %s contacts");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.keys_left"), "%s encryption keys left behind (those channels are clear)");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.dial"), "Dial %s MHz, %s memory channels");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.usage"), "Use on a radio block, or with a handheld in your other hand. Sneak-use in the air to clear");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.family.commercial"), "commercial radio");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.family.ham_vhf"), "2m ham radio");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.model.basic_handheld"), "basic commercial handheld");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.model.upgraded_handheld"), "upgraded commercial handheld");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.model.vhf_mobile"), "VHF commercial mobile");
			provider.add(Radiocraft.translationKey("tooltip", "cloner.model.low_band_mobile"), "low band mobile");
			provider.add(Radiocraft.translationKey("message", "cloner.read"), "Read the %s: %s channels");
			provider.add(Radiocraft.translationKey("message", "cloner.read_keys"), "Read the %s: %s channels; %s encryption keys it would not give up were left behind");
			provider.add(Radiocraft.translationKey("message", "cloner.read_ham"), "Read the %s: %s MHz and %s memory channels");
			provider.add(Radiocraft.translationKey("message", "cloner.written"), "Cloned into the %s: %s channels");
			provider.add(Radiocraft.translationKey("message", "cloner.written_cut"), "Cloned into the %s: %s channels (%s more did not fit: it holds %s)");
			provider.add(Radiocraft.translationKey("message", "cloner.written_ham"), "Cloned into the %s: %s MHz, %s memory channels");
			provider.add(Radiocraft.translationKey("message", "cloner.refused"), "The %s cannot take this clone: CH %s: %s");
			provider.add(Radiocraft.translationKey("message", "cloner.incompatible"), "The %s cannot take a clone of a %s");
			provider.add(Radiocraft.translationKey("message", "cloner.unsupported"), "The cloner's cable does not fit the %s");
			provider.add(Radiocraft.translationKey("message", "cloner.marine"), "Marine radios have fixed channels: nothing to clone");
			provider.add(Radiocraft.translationKey("message", "cloner.no_radio"), "Hold a radio in your other hand, or use the cloner on a radio block");
			provider.add(Radiocraft.translationKey("message", "cloner.cleared"), "Cloner cleared");
			provider.add(Radiocraft.translationKey("message", "cloner.empty"), "The cloner is already empty");
			provider.add(Radiocraft.translationKey("tooltip", "band_plan"), "Right click to read what each part of each band is for, ham and commercial");
			provider.add(Radiocraft.translationKey("screen", "band_plan.title"), "Band Plan");
			provider.add(Radiocraft.translationKey("screen", "band_plan.hint"), "Scroll to read on; hover a band for its use");
			provider.add(Radiocraft.translationKey("screen", "band_plan.legend"), "Legend");
			addCommercialRadios(provider);
			addMarineRadios(provider);
			addCamera(provider);
			addRadioComputer(provider);
			addOfficeApps(provider);
			addRadioApps(provider);
			addSecretApps(provider);
			// Computer apps 2 (2026-11-06): each program's words in its own class under datagen/lang.
			com.arrl.radiocraft.datagen.lang.RttyLang.add(provider);
			com.arrl.radiocraft.datagen.lang.BbsLang.add(provider);
			com.arrl.radiocraft.datagen.lang.PropagationLang.add(provider);
			com.arrl.radiocraft.datagen.lang.RepeaterLang.add(provider);
			com.arrl.radiocraft.datagen.lang.LicenseStudyLang.add(provider);
			addAdsb(provider);
			addFccGear(provider);
			addRealWorldInterface(provider);
			provider.add(Radiocraft.translationKey("tooltip", "crystal_pure"), "Pure crystal: a little less noise.");
			provider.add(Radiocraft.translationKey("tooltip", "crystal_poor"), "Poor crystal: a little more noise, and it drifts off frequency.");
			// Shown by ItemTooltipEvents on items whose class adds no tooltip of its own.
			provider.add(Radiocraft.translationKey("tooltip", "all_band_radio"), "HF and VHF transceiver: 80, 40, 20 and 10m and 2m, in SSB, CW and FM. Needs an antenna for the band on coax.");
			provider.add(Radiocraft.translationKey("tooltip", "alternator"), "Turn its shaft and it pushes power into the block beside it, such as a large battery.");
			provider.add(Radiocraft.translationKey("tooltip", "aprs_h19"), "APRS terminal: touch it to a radio interface on a VHF station for beacons, the station list, messages and maps.");
			provider.add(Radiocraft.translationKey("tooltip", "aprs_map"), "Right click an H19 with an empty map for a map of the APRS stations it has heard.");
			provider.add(Radiocraft.translationKey("tooltip", "commercial_radio_module"), "Sealed cabinet that sends one thing on one frequency for ever. Found at radio sites; break it for a rare part.");
			provider.add(Radiocraft.translationKey("tooltip", "commercial_repeater"), "Four-block repeater rack with its duplexer, battery and charge controller built in. Found at radio sites.");
			provider.add(Radiocraft.translationKey("tooltip", "desk_charger"), "Set a VHF handheld in it to charge. Needs power on its wire.");
			provider.add(Radiocraft.translationKey("tooltip", "duplexer"), "Set it on a VHF repeater so it can send and receive on one antenna. Coax the antenna from it.");
			provider.add(Radiocraft.translationKey("tooltip", "microwave_horn"), "A large microwave antenna that reaches six times as far as a dish. Found in structures.");
			provider.add(Radiocraft.translationKey("tooltip", "radio_interface"), "Touch it to a VHF radio to make a packet station: holds the call sign and the TX switch for APRS and fax.");
			provider.add(Radiocraft.translationKey("tooltip", "tape_player"), "Plays a tape reel out loud and into every radio touching it, which transmits it.");
			provider.add(Radiocraft.translationKey("tooltip", "tower_beacon"), "Red obstruction light for the top of a tower. A tower beacon controller makes it blink.");
			provider.add(Radiocraft.translationKey("tooltip", "tower_beam"), "Joins every tower beam it touches, edges and corners included, into a lattice tower.");
			provider.add(Radiocraft.translationKey("tooltip", "tower_beam_thin"), "Joins every tower beam it touches, edges and corners included, into a lattice tower.");
			provider.add(Radiocraft.translationKey("tooltip", "tower_beam_thick"), "Joins every tower beam it touches, edges and corners included, into a lattice tower.");
			provider.add(Radiocraft.translationKey("tooltip", "vhf_repeater"), "Listens on its input frequency and sends it out on its output. Needs a duplexer, an antenna and power.");
			provider.add(Radiocraft.translationKey("tooltip", "all_band_circuit_board"), "The heart of the all-band radio, built with a pure encased crystal of every band.");
			provider.add(Radiocraft.translationKey("tooltip", "antenna_wire"), "Right click one antenna holder, then another, to string wire between them. Feed it from a balun.");
			provider.add(Radiocraft.translationKey("tooltip", "coaxial_core"), "Insulated wire core, made into coaxial wire.");
			provider.add(Radiocraft.translationKey("tooltip", "ferrite_core"), "Wound into baluns and the 5-BTV.");
			provider.add(Radiocraft.translationKey("tooltip", "hand_microphone"), "Part of every radio you talk on.");
			provider.add(Radiocraft.translationKey("tooltip", "hf_circuit_board"), "Part of HF radios and most station electronics.");
			provider.add(Radiocraft.translationKey("tooltip", "insulation"), "Wire jackets, coax and antenna parts. Made from kelp, leather or slime.");
			provider.add(Radiocraft.translationKey("tooltip", "microphone_element"), "A carbon capsule: the heart of every microphone and telephone.");
			provider.add(Radiocraft.translationKey("tooltip", "radio_speaker"), "Part of every radio and speaker.");
			provider.add(Radiocraft.translationKey("tooltip", "tape_reel"), "Plays in a tape player, out loud and over the radios touching it.");
			provider.add(Radiocraft.translationKey("tooltip", "vhf_handheld"), "2m FM radio you carry: hold right click to talk, sneak right click to open it. Runs on a small battery.");
			provider.add(Radiocraft.translationKey("tooltip", "vhf_handheld_poor"), "2m FM radio you carry: hold right click to talk, sneak right click to open it. Runs on a small battery.");
			provider.add(Radiocraft.translationKey("tooltip", "vhf_handheld_pure"), "2m FM radio you carry: hold right click to talk, sneak right click to open it. Runs on a small battery.");
			for(CrystalBand band : CrystalBand.values())
				for(CrystalQuality quality : CrystalQuality.values()) {
					String makes = switch(quality) {
						case POOR -> "Makes a Cheap radio that drifts off frequency.";
						case GOOD -> "Makes the plain radio.";
						case PURE -> "Makes a Premium radio: a touch less static.";
					};
					provider.add(Radiocraft.translationKey("tooltip", "crystal_" + band.getId() + "_" + quality.getId()), "Found digging sand. Encase it in iron before it goes in a radio.");
					provider.add(Radiocraft.translationKey("tooltip", "encased_crystal_" + band.getId() + "_" + quality.getId()), makes + " Craft it with a radio to swap that radio's crystal.");
				}

			provider.add(Radiocraft.translationKey("message", "battery_swapped"), "Battery swapped");
            provider.add(Radiocraft.translationKey("message", "radio_battery_empty"), "Radio battery depleted");

			// Jade configuration translations
			provider.add("config.jade.plugin_radiocraft.radiocraft", "RadioCraft");
			provider.add("config.jade.plugin_radiocraft.antenna_wire", "Antenna Wire");
			provider.add("config.jade.plugin_radiocraft.antenna_info", "Antenna Info");
			provider.add("config.jade.plugin_radiocraft.radio", "Radio");
			provider.add("config.jade.plugin_radiocraft.coax_wire", "Coax Wire");
			provider.add("config.jade.plugin_radiocraft.parrot_controller", "Simplex Repeat Controller");
			provider.add("config.jade.plugin_radiocraft.teletype", "Teletype Machine");
			provider.add("config.jade.plugin_radiocraft.battery", "Battery Charge");
			provider.add("config.jade.plugin_radiocraft.solar_panel", "Solar Panel Output");

			// The config screen (mod list, Config): every setting and section named, as NeoForge looks them up.
			provider.add(Radiocraft.MOD_ID + ".configuration.title", "RadioCraft");
			java.util.Set<String> configKeys = new java.util.HashSet<>();
			for(net.neoforged.neoforge.common.ModConfigSpec spec : java.util.List.of(com.arrl.radiocraft.RadiocraftClientConfig.SPEC,
					com.arrl.radiocraft.CommonConfig.SPEC, com.arrl.radiocraft.RadiocraftServerConfig.SPEC))
				addConfigNames(provider, spec.getSpec(), configKeys);

			Arrays.stream(LicenseClass.values()).forEach((licenseClass) -> {
                // I'm not going to bother pulling in the Apache Commons Text library, because I really don't want to shadow it in
                @SuppressWarnings("deprecation")
				String friendlyClassName = WordUtils.capitalizeFully(licenseClass.name().replace("_", " "));
				provider.add(Radiocraft.translationKey("license_class", licenseClass.name()), friendlyClassName);
			});
		});
	}

	/** The commercial radios (1.12 step 2): names, tooltips, their screens and the programmer's refusals. */
	private static void addCommercialRadios(LanguageProvider provider) {
		provider.addItem(RadiocraftItems.BASIC_COMMERCIAL_HANDHELD, "Basic Commercial Handheld");
		provider.addItem(RadiocraftItems.UPGRADED_COMMERCIAL_HANDHELD, "Upgraded Commercial Handheld");
		provider.addBlock(RadiocraftBlocks.COMMERCIAL_MOBILE, "Commercial Mobile Radio");
		provider.addBlock(RadiocraftBlocks.LOW_BAND_MOBILE, "Low Band Mobile Radio");
		provider.add(Radiocraft.translationKey("tooltip", "basic_commercial_handheld"), "16-channel 150-174 MHz FM brick. Sneak and use for the knob; hold right click to talk. Dyeable.");
		provider.add(Radiocraft.translationKey("tooltip", "upgraded_commercial_handheld"), "Fire-service handheld: 150-174 MHz and 2m, digital voice and encryption, a channel display. Dyeable.");
		provider.add(Radiocraft.translationKey("tooltip", "commercial_mobile"), "16-channel 150-174 MHz mobile, analog and digital. Sneak and use for its hand mic. Needs a VHF antenna and power. Dyeable.");
		provider.add(Radiocraft.translationKey("tooltip", "low_band_mobile"), "16-channel 30-50 MHz FM mobile. Sneak and use for its hand mic. Needs a low band antenna (a long dipole) and power. Dyeable.");
		provider.add(Radiocraft.translationKey("tooltip", "commercial.channel"), "Channel %s: %s");
		provider.add(Radiocraft.translationKey("tooltip", "commercial.programmed"), "%s of %s channels programmed. Programmed with a radio computer.");
		provider.add(Radiocraft.translationKey("tooltip", "commercial.unit_id"), "Unit ID %s");
		provider.add(Radiocraft.translationKey("screen", "commercial_handheld.title"), "Commercial Handheld");
		provider.add(Radiocraft.translationKey("screen", "commercial.power"), "PWR");
		provider.add(Radiocraft.translationKey("screen", "commercial.channel_knob"), "CHANNEL");
		provider.add(Radiocraft.translationKey("screen", "commercial.zone"), "Zone %s");
		provider.add(Radiocraft.translationKey("message", "commercial.rx_out_of_range"), "The receive frequency is outside this radio's bands");
		provider.add(Radiocraft.translationKey("message", "commercial.tx_out_of_range"), "The transmit frequency is outside this radio's bands");
		provider.add(Radiocraft.translationKey("message", "commercial.no_digital"), "This radio has no digital voice");
		provider.add(Radiocraft.translationKey("message", "commercial.no_encryption"), "This radio cannot encrypt");
		provider.add(Radiocraft.translationKey("message", "commercial.too_many_channels"), "The radio has no room for this channel (16 channels, 40 on the upgraded handheld)");
		provider.add(Radiocraft.translationKey("message", "commercial.no_such_channel"), "No such channel: 1 to 16");
		provider.add(Radiocraft.translationKey("message", "commercial.hidden_key_unknown"), "The key is hidden and the radio has none on that channel to keep");
	}

	/** The marine radios (1.12 step 3): names, tooltips and their screens. */
	private static void addMarineRadios(LanguageProvider provider) {
		provider.addItem(RadiocraftItems.MARINE_HANDHELD, "Marine Handheld");
		provider.addBlock(RadiocraftBlocks.MARINE_RADIO, "Marine Radio");
		provider.add(Radiocraft.translationKey("tooltip", "marine_handheld"), "VHF marine handheld: the marine channels, 5 W or 1 W, 16/9 key and dual watch. Sneak and use for the keys; hold right click to talk.");
		provider.add(Radiocraft.translationKey("tooltip", "marine_radio"), "Fixed-mount VHF marine radio: 25 W or 1 W, 16/9 key and dual watch. Sneak and use for its hand mic. Needs a VHF antenna and power; works on ships.");
		provider.add(Radiocraft.translationKey("tooltip", "marine.channel"), "Channel %s: %s");
		provider.add(Radiocraft.translationKey("tooltip", "marine.high_handheld"), "High power (5 W)");
		provider.add(Radiocraft.translationKey("tooltip", "marine.high_base"), "High power (25 W)");
		provider.add(Radiocraft.translationKey("tooltip", "marine.low"), "Low power (1 W)");
		provider.add(Radiocraft.translationKey("screen", "marine_handheld.title"), "Marine Handheld");
	}

	/** The camera, photo paper and the photos it prints (1.12 step 4). */
	private static void addCamera(LanguageProvider provider) {
		provider.addItem(RadiocraftItems.CAMERA, "Camera");
		provider.addItem(RadiocraftItems.PHOTO_PAPER, "Photo Paper");
		provider.addItem(RadiocraftItems.COLOR_PHOTO_PAPER, "Color Photo Paper");
		provider.add(Radiocraft.translationKey("tooltip", "camera"), "Hold right click to aim (scroll to zoom up to 3x), let go to photograph what the viewfinder shows onto photo paper from your inventory or other hand. Color paper prints in color, plain photo paper in black and white.");
		provider.add(Radiocraft.translationKey("tooltip", "photo_paper"), "Black and white. A camera prints a photo on it; the photo is a map, so it can be framed, copied or sent by radio fax.");
		provider.add(Radiocraft.translationKey("tooltip", "color_photo_paper"), "A camera prints a color photo on it; the photo is a map, so it can be framed, copied or sent by radio fax.");
		provider.add(Radiocraft.translationKey("item", "photo"), "Photo");
		provider.add(Radiocraft.translationKey("item", "photo.bw"), "B&W Photo");
		provider.add(Radiocraft.translationKey("item", "photo.by"), "Taken by %s");
		provider.add(Radiocraft.translationKey("message", "camera.no_paper"), "No photo paper");
		provider.add(Radiocraft.translationKey("message", "camera.disabled"), "Cameras are switched off on this server");
		provider.add(Radiocraft.translationKey("message", "camera.cap"), "You have %s photos, the most this server lets one player keep (%s).");
		provider.add(Radiocraft.translationKey("message", "camera.cap.choose"), "Choose photos to delete");
		provider.add(Radiocraft.translationKey("tooltip", "photo.deleted"), "Photo deleted");
		provider.add(Radiocraft.translationKey("screen", "camera.zoom"), "%sx");
		provider.add(Radiocraft.translationKey("screen", "camera.color"), "COLOR");
		provider.add(Radiocraft.translationKey("screen", "camera.bw"), "B&W");
		provider.add(Radiocraft.translationKey("screen", "camera.no_paper"), "NO PAPER");
		provider.add(Radiocraft.translationKey("screen", "camera.hint"), "Scroll to zoom, let go to shoot");
		provider.add(Radiocraft.translationKey("commands", "photos.none"), "You have no photos");
		provider.add(Radiocraft.translationKey("commands", "photos.header"), "Your photos (%s), oldest first, page %s of %s:");
		provider.add(Radiocraft.translationKey("commands", "photos.entry"), "#%s, day %s (%s)");
		provider.add(Radiocraft.translationKey("commands", "photos.delete"), "Delete");
		provider.add(Radiocraft.translationKey("commands", "photos.more"), "More");
		provider.add(Radiocraft.translationKey("commands", "photos.not_yours"), "Map #%s is not one of your photos");
		provider.add(Radiocraft.translationKey("commands", "photos.confirm"), "Delete photo %s? It goes blank everywhere, copies too.");
		provider.add(Radiocraft.translationKey("commands", "photos.confirm.yes"), "Yes, delete it");
		provider.add(Radiocraft.translationKey("commands", "photos.deleted"), "Photo #%s deleted; you have %s left");
	}

	/** The radio computer (1.12 step 5): its three blocks, the floppy disks, and every word on its screen and programs. */
	private static void addRadioComputer(LanguageProvider provider) {
		provider.addBlock(RadiocraftBlocks.RADIO_COMPUTER, "Radio Computer");
		provider.addBlock(RadiocraftBlocks.COMPUTER_MONITOR, "Computer Monitor");
		provider.addBlock(RadiocraftBlocks.SCANNER, "Scanner");
		provider.add(Radiocraft.translationKey("container", "radio_computer"), "Radio Computer");
		provider.add(Radiocraft.translationKey("container", "scanner"), "Scanner");
		provider.add(Radiocraft.translationKey("tooltip", "radio_computer"), "The PC for the shack: holds the files, the floppy drive and the programs. Put a computer monitor on top; a scanner beside it scans and prints. Needs power while in use, about an HF radio's receive draw.");
		provider.add(Radiocraft.translationKey("tooltip", "computer_monitor"), "Goes on top of a radio computer: right click it to use the computer. Use a floppy disk on it to put the disk in the drive.");
		provider.add(Radiocraft.translationKey("tooltip", "scanner"), "Set it beside a radio computer: it scans maps, photos and books in, and prints photos, maps and books out on the paper in its tray.");
		provider.addItem(RadiocraftItems.FLOPPY_DISK, "Floppy Disk");
		provider.add(Radiocraft.translationKey("tooltip", "floppy_disk"), "Blank. Craft it with the right part to make a program's disk for the radio computer.");
		provider.add(Radiocraft.translationKey("tooltip", "floppy_disk.install"), "Put it in a radio computer's drive and run INSTALL. The disk is not used up.");
		provider.add(Radiocraft.translationKey("item", "printout"), "Printout");
		provider.add(Radiocraft.translationKey("message", "computer.drive_full"), "There is already a disk in drive A: (sneak and right click the computer to eject it)");
		provider.add(Radiocraft.translationKey("message", "computer.floppy_blank"), "Blank disk in drive A:");
		provider.add(Radiocraft.translationKey("message", "computer.floppy_inserted"), "%s disk in drive A: run INSTALL to install it");
		provider.add(Radiocraft.translationKey("message", "computer.no_monitor"), "This computer has no monitor: put a computer monitor on top of it");
		provider.add(Radiocraft.translationKey("message", "computer.no_computer"), "This monitor has nothing to show: put it on top of a radio computer");
		// The CPS's "Program radios" mode (1.12 playtest fixes 2).
		provider.add(Radiocraft.translationKey("message", "computer.hand.written"), "Programmed the %s: %s channels, unit ID %s");
		provider.add(Radiocraft.translationKey("message", "computer.hand.read"), "Read the %s into the CPS (%s channels)");
		provider.add(Radiocraft.translationKey("message", "computer.hand.refused"), "The %s was not programmed: CH %s: %s");
		provider.add(Radiocraft.translationKey("message", "computer.hand.ended.no_power"), "The CPS stopped programming radios: the computer has no power");
		provider.add(Radiocraft.translationKey("message", "computer.hand.ended.timeout"), "The CPS stopped programming radios after 5 minutes unused");
		provider.add(Radiocraft.translationKey("screen", "scanner.glass"), "Glass");
		provider.add(Radiocraft.translationKey("screen", "scanner.paper"), "Paper");
		provider.add(Radiocraft.translationKey("screen", "scanner.output"), "Out");

		// The programs: their names, floppies and one-line descriptions (ComputerApp.nameKey / descriptionKey).
		java.util.function.BiConsumer<String, String[]> app = (id, text) -> {
			provider.add("screen.radiocraft.computer.app." + id, text[0]);
			provider.add("screen.radiocraft.computer.app." + id + ".description", text[1]);
		};
		app.accept("edit", new String[] { "Text Editor", "Write, open and save text files; print them as books on the scanner." });
		app.accept("sysinfo", new String[] { "System Info", "The computer, its disk and drive, the scanner and the station it is wired to." });
		app.accept("bandplan", new String[] { "Band Plan", "Every band as a coloured bar: what each part of each band is for." });
		app.accept("install", new String[] { "Install", "Install a program from a floppy disk, or remove one." });
		app.accept("bookedit", new String[] { "Book Editor", "Write books of any length on the computer and print them." });
		app.accept("logbook", new String[] { "Logbook", "Log your contacts: date, time, call, frequency, mode, report and notes." });
		app.accept("freqman", new String[] { "Frequency Manager", "Keep lists of channels and frequencies, and send them to commercial radios." });
		app.accept("programmer", new String[] { "Commercial Radio Programmer", "Read and write the channels of commercial radios (CPS)." });
		app.accept("solar", new String[] { "Solar Weather", "Solar weather, band conditions and forecasts." });
		app.accept("aprs", new String[] { "APRS", "APRS stations, maps and messages through a VHF radio and radio interface." });
		app.accept("wefax", new String[] { "WEFAX", "Receive and send weather fax charts." });
		app.accept("radiofax", new String[] { "Radio Fax", "Send and receive documents and maps by radio fax." });
		app.accept("sstv", new String[] { "SSTV", "Send and receive pictures by slow-scan television; caption, save and resend them." });
		app.accept("df", new String[] { "Direction Finding", "Bigger maps for the direction finder, accuracy at a glance, past searches kept." });
		app.accept("snake", new String[] { "Snake", "The classic. Eat, grow, and do not bite your own tail." });
		app.accept("falling_blocks", new String[] { "Pileup", "Callers pile into the band: fill a row to work them, before the band fills up." });
		app.accept("music", new String[] { "Music Player", "Old public-domain tunes, played on the computer's sound card." });
		app.accept("bible", new String[] { "Bible", "The four gospels, World English Bible: read by chapter, go to a verse, search." });
		app.accept("adsb", new String[] { "ADS-B", "Aircraft on a radar screen, from an ADS-B receiver." });
		app.accept("rtty", new String[] { "RTTY", "Live radioteletype on HF: type ahead, macros, and a receive window that saves to disk." });
		app.accept("bbs", new String[] { "Packet BBS", "Run a packet mailbox on VHF, or connect to one: messages and bulletins by APRS." });
		app.accept("propagation", new String[] { "Propagation Predictor", "Which HF bands open to a place or station over the next hours, from the live sky." });
		app.accept("repeaters", new String[] { "Repeater Directory", "Repeaters your radios have heard and ones you add, sent on to the frequency manager." });
		app.accept("license", new String[] { "License Study", "Flash cards and practice exams from the amateur radio question pools." });
		for(com.arrl.radiocraft.common.computer.ComputerApp program : com.arrl.radiocraft.common.computer.ComputerApps.onFloppies())
			provider.add(java.util.Objects.requireNonNull(RadiocraftItems.floppy(program)), FLOPPY_NAMES.get(program.id()) + " Floppy Disk");

		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("error", "Error");
		s.accept("no_power", "No power: run a power wire to the computer");
		s.accept("err.no_power", "The computer has no power");
		s.accept("button.ok", "OK");
		s.accept("button.yes", "Yes");
		s.accept("button.no", "No");
		s.accept("button.cancel", "Cancel");
		s.accept("button.open", "Open");
		s.accept("button.save", "Save");
		s.accept("button.discard", "Discard");
		s.accept("keys.back", "Esc=Back");
		s.accept("files.all", "All files:");
		s.accept("files.of", "*.%s files:");
		s.accept("files.reading", "Reading the disk...");
		s.accept("files.none", "No files.");
		s.accept("drive.empty", "No disk");
		s.accept("drive.blank", "Blank disk");
		// Errors from the server (ComputerServer, ComputerStorage.Problem, ScannerBlockEntity.Result).
		s.accept("err.bad_name", "Not a file name: up to 8 letters or digits, a dot, and up to 3 more (LOGBOOK.TXT)");
		s.accept("err.too_big", "File too big (256K at most)");
		s.accept("err.disk_full", "Disk full: delete some files first");
		s.accept("err.too_many_files", "Too many files on the disk: delete some first");
		s.accept("err.not_found", "File not found");
		s.accept("err.locked", "Type the computer's password first");
		s.accept("err.wrong_password", "Wrong password");
		s.accept("err.not_owner", "Only the computer's owner (who placed it) or an operator can change its password");
		s.accept("err.bad_password", "A password is at most %s letters, digits or symbols");
		s.accept("lock.title", "RC-DOS");
		s.accept("lock.keys", "Enter=OK  Esc=Leave");
		s.accept("lock.box", "Password");
		s.accept("lock.prompt", "This computer has a password:");
		s.accept("shell.password_title", "Password");
		s.accept("shell.password", "New password for this computer (empty for none):");
		s.accept("shell.password_set", "Password set: everyone is asked for it when they open the computer");
		s.accept("shell.password_cleared", "Password removed: anyone can use the computer");
		s.accept("err.exists", "A file with that name already exists");
		s.accept("err.bad_contents", "That file's contents do not suit its type");
		s.accept("err.unknown_request", "The computer does not know %s");
		s.accept("err.drive_full", "There is already a disk in drive A:");
		s.accept("err.no_floppy", "That floppy disk is no longer in your inventory");
		s.accept("err.drive_empty", "No disk in drive A:");
		s.accept("err.unknown_app", "Unknown program %s");
		s.accept("err.built_in", "That program is built in");
		s.accept("err.already_installed", "That program is already installed");
		s.accept("err.wrong_floppy", "That program is not on the disk in drive A:");
		s.accept("err.not_installed", "That program is not installed");
		s.accept("err.no_handler", "That program has nothing to do on the server yet");
		s.accept("err.upload_failed", "The file did not arrive whole: try again");
		s.accept("err.no_scanner", "No scanner: put one beside the computer");
		s.accept("err.no_paper", "Not enough paper in the scanner's tray");
		s.accept("err.output_full", "Paper jam: take the last print out of the scanner's output tray");
		s.accept("err.bed_empty", "Nothing to scan on the scanner's glass (put a map, photo or book on it)");
		s.accept("err.bad_image", "Not a picture");
		s.accept("err.nothing_to_print", "Nothing to print");
		s.accept("err.lost", "No answer from the computer");
		// The boot.
		s.accept("boot.bios", "RadioCraft Systems BIOS v1.12");
		s.accept("boot.copyright", "Copyright (C) 1994 ARRL Radiocraft. All rights reserved.");
		s.accept("boot.cpu", "CPU: 80486DX2-66");
		s.accept("boot.memory", "Memory Test: %sK %s");
		s.accept("boot.ok", "OK");
		s.accept("boot.floppy", "Floppy drive A: 1.44M 3.5\" (%s)");
		s.accept("boot.disk", "Hard disk C: installed, serial %s");
		s.accept("boot.starting", "Starting RC-DOS...");
		s.accept("boot.version", "RC-DOS Version 6.22");
		// The shell.
		s.accept("shell.title", "RC-DOS Shell");
		s.accept("shell.serial", "Serial %s");
		s.accept("shell.programs", "Programs");
		s.accept("shell.files", "Files on C:");
		s.accept("shell.no_files", "No files yet.");
		s.accept("shell.keys_programs", "Enter=Run  Tab=Files  F6=Scan  F8=Password  Esc=Leave");
		s.accept("shell.keys_files", "Enter=Open  Del=Delete  F7=Rename  F5=Print  F6=Scan  Tab=Programs");
		s.accept("shell.file_info", "%s  %s");
		s.accept("shell.disk", "C: %s used of %s, %s files");
		s.accept("shell.drive", "A: %s");
		s.accept("shell.no_opener", "No program here opens .%s files");
		s.accept("shell.delete_title", "Delete");
		s.accept("shell.delete", "Delete %s? It cannot be brought back.");
		s.accept("shell.deleted", "%s deleted");
		s.accept("shell.rename_title", "Rename");
		s.accept("shell.rename", "New name for %s:");
		s.accept("shell.renamed", "Renamed to %s");
		s.accept("shell.printed", "%s sent to the printer");
		s.accept("shell.cannot_print", "Cannot print .%s files");
		s.accept("shell.scan_title", "Scan");
		s.accept("shell.scan", "Scan what is on the glass into a file named:");
		s.accept("shell.scanned", "Scanned into %s");
		s.accept("view.title", "VIEW - %s");
		s.accept("view.keys", "F5=Print  Esc=Back");
		s.accept("pending.line1", "This program is not ready in this version.");
		s.accept("pending.line2", "It stays installed for when it is.");
		// The text editor.
		s.accept("edit.title", "EDIT - %s");
		s.accept("edit.untitled", "UNTITLED");
		s.accept("edit.keys", "^S=Save  F3=Open  F4=New  F5=Print  F6=Scan  Esc=Exit");
		s.accept("edit.status", "Line %s  Col %s  %s of %s characters");
		s.accept("edit.opened", "Opened %s");
		s.accept("edit.saved", "Saved %s");
		s.accept("edit.save_title", "Save As");
		s.accept("edit.save_as", "File name (.TXT is added):");
		s.accept("edit.open_title", "Open");
		s.accept("edit.unsaved_title", "Unsaved changes");
		s.accept("edit.unsaved", "The document has changed. Save it first?");
		s.accept("edit.printed", "Sent to the printer");
		s.accept("edit.save_to_print", "A long document prints from its file: save it first (Ctrl+S)");
		s.accept("edit.not_a_book", "Put a book on the scanner's glass to read it in");
		s.accept("edit.scanned", "Read in %s");
		// System info.
		s.accept("sysinfo.title", "SYSINFO - System Information");
		s.accept("sysinfo.keys", "F5=Refresh  Esc=Exit");
		s.accept("sysinfo.reading", "Reading the system...");
		s.accept("sysinfo.computer", "Computer");
		s.accept("sysinfo.serial", "Serial number: %s");
		s.accept("sysinfo.dos", "Operating system: RC-DOS 6.22");
		s.accept("sysinfo.cpu", "Processor: 80486DX2, 66 MHz");
		s.accept("sysinfo.memory", "Memory: 640K base, 7552K extended");
		s.accept("sysinfo.disk", "Hard disk C:");
		s.accept("sysinfo.disk_used", "%s used of %s");
		s.accept("sysinfo.disk_files", "%s files (at most %s)");
		s.accept("sysinfo.drive", "Floppy drive A:");
		s.accept("sysinfo.programs", "Programs");
		s.accept("sysinfo.programs_count", "%s programs, %s installed from disks");
		s.accept("sysinfo.devices", "Devices");
		s.accept("sysinfo.scanner", "Scanner");
		s.accept("sysinfo.glass", "On the glass: %s");
		s.accept("sysinfo.glass_empty", "nothing");
		s.accept("sysinfo.paper", "Tray: %s paper, %s maps");
		s.accept("sysinfo.photo_paper", "Tray: %s photo, %s color photo paper");
		s.accept("sysinfo.output_full", "Output tray: a print is waiting");
		s.accept("sysinfo.output_empty", "Output tray: empty");
		s.accept("sysinfo.no_scanner", "Not connected (set one beside the computer)");
		s.accept("sysinfo.station", "Station");
		s.accept("sysinfo.callsign", "Callsign: %s");
		s.accept("sysinfo.no_callsign", "No callsign (no radio interface on the bus)");
		s.accept("sysinfo.no_devices", "Nothing else wired to the computer");
		// The band plan.
		s.accept("bandplan.title", "BANDPLAN - Band Plan");
		s.accept("bandplan.keys", "Up/Down=Band  Left/Right=Tune (Shift: faster)  Esc=Exit");
		s.accept("bandplan.band", "%s: %s - %s MHz");
		s.accept("bandplan.cursor", "%s MHz: %s");
		s.accept("bandplan.unplanned", "not in the plan");
		s.accept("bandplan.markers", "Near: %s");
		s.accept("bandplan.legend", "Legend");
		// Install.
		s.accept("install.title", "INSTALL - RC-DOS Setup");
		s.accept("install.keys", "Enter=Choose  Del=Remove program  Esc=Exit");
		s.accept("install.drive", "Drive A: %s");
		s.accept("install.hint", "Insert a disk in drive A: from the list below, or use a floppy disk on the computer.");
		s.accept("install.choose", "Choose");
		s.accept("install.install", "Install %s from drive A:");
		s.accept("install.already", "%s is already installed");
		s.accept("install.blank", "The disk in drive A: is blank");
		s.accept("install.eject", "Eject the disk");
		s.accept("install.insert", "Insert disk: %s");
		s.accept("install.no_floppies", "No floppy disks in your inventory");
		s.accept("install.installed", "Installed programs (Del removes one; its files stay):");
		s.accept("install.none_installed", "None yet");
		s.accept("install.copying_title", "Installing");
		s.accept("install.copying", "Copying A:\\%s to C:\\RCDOS ...");
		s.accept("install.done_title", "Setup complete");
		s.accept("install.done", "%s is installed. Find it in the shell's Programs list.");
		s.accept("install.remove_title", "Remove program");
		s.accept("install.remove", "Remove %s from this computer? Its files stay on the disk, and the floppy can install it again.");
		s.accept("install.removed", "%s removed");
	}

	/** The radio computer's office programs (1.12 step 6): book editor, logbook, frequency manager, CPS, solar weather. */
	private static void addOfficeApps(LanguageProvider provider) {
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("button.choose", "Choose");
		s.accept("pick.none", "Nothing to choose from.");
		// Errors from the programs' server sides.
		s.accept("err.no_radio", "No radio chosen");
		s.accept("err.radio_gone", "That radio is no longer on the scanner's glass or wired to the computer");
		s.accept("err.solar_no_radio", "No radio found: wire a radio to the computer or to a radio interface at it, or an Ionospheric Processor");
		s.accept("err.solar_processor_offline", "The Ionospheric Processor is offline: put an Ionosonde Antenna on its coax");
		s.accept("err.solar_radio_off", "The radio is switched off: turn it on to hear the solar and weather broadcasts");
		s.accept("err.solar_no_antenna", "The radio has no antenna: connect a real antenna to its coax (a dummy load hears nothing)");
		s.accept("err.no_such_volume", "This book has no volume %s");
		s.accept("err.too_many_pages", "Too long for one book: the paper tray holds %s sheets. The book editor prints long books in volumes");
		provider.add(Radiocraft.translationKey("message", "commercial.nothing_to_send"), "Nothing to send");

		// The radios the programmer and frequency manager reach.
		s.accept("radio.none", "No commercial radio wired to the computer. A handheld is programmed in the CPS by hand (F9).");
		s.accept("radio.in_hand", "in your hand");
		s.accept("radio.wired", "wired, at %s");
		s.accept("radio.pick_title", "Which radio?");
		s.accept("radio.pick", "The computer reaches more than one commercial radio:");

		// BOOKEDIT.
		s.accept("bookedit.title", "BOOKEDIT - %s");
		s.accept("bookedit.keys", "^S=Save  F3=Open  F4=New  F5=Print  F7/F8=Page  F9=Add  F10=Del  F12=Title");
		s.accept("bookedit.heading", "\"%s\" by %s");
		s.accept("bookedit.no_title", "Untitled");
		s.accept("bookedit.page", "Page %s of %s");
		s.accept("bookedit.side_page", "Page %s of %s");
		s.accept("bookedit.side_volume", "Volume %s of %s");
		s.accept("bookedit.side_volume_pages", "(pages %s to %s)");
		s.accept("bookedit.side_lines", "Book lines: %s of %s");
		s.accept("bookedit.side_over", "Too long: the end is cut off");
		s.accept("bookedit.side_chars", "%s of %s characters");
		s.accept("bookedit.side_help1", "A book page is 14 lines");
		s.accept("bookedit.side_help2", "of about 19 letters.");
		s.accept("bookedit.side_help3", "F6 reads in a book.");
		s.accept("bookedit.status", "Line %s  Col %s");
		s.accept("bookedit.opened", "Opened %s (%s pages)");
		s.accept("bookedit.saved", "Saved %s");
		s.accept("bookedit.save_title", "Save As");
		s.accept("bookedit.save_as", "File name (.BOK is added):");
		s.accept("bookedit.open_title", "Open Book");
		s.accept("bookedit.unsaved", "The book has changed. Save it first?");
		s.accept("bookedit.print_title", "Print");
		s.accept("bookedit.which_volume", "The book prints as %s volumes of up to %s pages (a full paper tray each). Print volume:");
		s.accept("bookedit.no_volume", "Volumes are 1 to %s");
		s.accept("bookedit.printed", "Volume %s sent to the printer (%s pages)");
		s.accept("bookedit.scanned", "Read in \"%s\" (%s pages)");
		s.accept("bookedit.info_title", "Title and Author");
		s.accept("bookedit.title_field", "Title:");
		s.accept("bookedit.author_field", "Author:");
		s.accept("bookedit.full", "A book has %s pages at most");
		s.accept("bookedit.delete_title", "Delete Page");
		s.accept("bookedit.delete", "Take page %s out of the book?");

		// LOGBOOK.
		s.accept("logbook.title", "LOGBOOK - %s");
		s.accept("logbook.keys", "Ins=Add  Enter=Edit  Del=Delete  F6=Heard  F3=Open  F4=New  F7=ADIF  Esc=Exit");
		s.accept("logbook.col.date", "DATE");
		s.accept("logbook.col.utc", "UTC");
		s.accept("logbook.col.call", "CALL");
		s.accept("logbook.col.mhz", "MHZ");
		s.accept("logbook.col.mode", "MODE");
		s.accept("logbook.col.sent", "SENT");
		s.accept("logbook.col.rcvd", "RCVD");
		s.accept("logbook.col.notes", "NOTES");
		s.accept("logbook.empty", "No contacts yet. Ins adds one; F6 fills one in from a station your radios heard.");
		s.accept("logbook.status", "%s contacts (at most %s in a log)");
		s.accept("logbook.opened", "Opened %s (%s contacts)");
		s.accept("logbook.add_title", "New Contact");
		s.accept("logbook.edit_title", "Edit Contact");
		s.accept("logbook.form_hint", "Times are UTC, as hams log them. Reports: 59 on phone, 599 on CW.");
		s.accept("logbook.call", "Call:");
		s.accept("logbook.date", "Date (YYYYMMDD):");
		s.accept("logbook.time", "Time (HHMM):");
		s.accept("logbook.freq", "Frequency (MHz):");
		s.accept("logbook.mode", "Mode:");
		s.accept("logbook.sent", "Report sent:");
		s.accept("logbook.rcvd", "Report received:");
		s.accept("logbook.notes", "Notes:");
		s.accept("logbook.need_call", "Type the other station's call");
		s.accept("logbook.bad_time", "Not a date and time: 20261104 and 1430");
		s.accept("logbook.bad_freq", "Not a frequency in MHz (14.225)");
		s.accept("logbook.full", "A log holds %s contacts: start a new one (F4)");
		s.accept("logbook.logged", "Logged %s");
		s.accept("logbook.worked_before", "Logged %s (worked %s times before)");
		s.accept("logbook.delete_title", "Delete Contact");
		s.accept("logbook.delete", "Delete the contact with %s on %s?");
		s.accept("logbook.heard_title", "Stations Heard");
		s.accept("logbook.heard_hint", "Heard by the radios wired to the computer in the last ten minutes:");
		s.accept("logbook.no_radios", "No radio is wired to the computer (touching it, or by data wire)");
		s.accept("logbook.nobody_heard", "The wired radios heard nobody talk in the last ten minutes");
		s.accept("logbook.ago_s", "%ss ago");
		s.accept("logbook.ago_m", "%s min ago");
		s.accept("logbook.op", "Op %s,");
		s.accept("logbook.exported", "Wrote %s (ADIF, %s contacts)");
		s.accept("logbook.open_title", "Open Log");
		s.accept("logbook.new_title", "New Log");
		s.accept("logbook.new", "Name of the new log (.LOG is added):");
		s.accept("logbook.exists", "%s already exists: open it with F3");
		s.accept("logbook.created", "Started %s");

		// FREQMAN.
		s.accept("freqman.title", "FREQMAN - %s");
		s.accept("freqman.keys", "Ins=Add  Enter=Edit  Del=Delete  Spc=Mark  F5=Send  F6=Read  F3=Open  F4=New");
		s.accept("freqman.col.name", "NAME");
		s.accept("freqman.col.rx", "RX MHZ");
		s.accept("freqman.col.tx", "TX MHZ");
		s.accept("freqman.col.mode", "MODE");
		s.accept("freqman.col.tone", "TONE");
		s.accept("freqman.col.notes", "NOTES");
		s.accept("freqman.empty", "No channels yet. Ins adds one; F6 reads a commercial radio's channels in.");
		s.accept("freqman.status", "%s channels, %s marked.  Ctrl+Up/Down moves one, F8 sorts by frequency");
		s.accept("freqman.opened", "Opened %s (%s channels)");
		s.accept("freqman.rx_only", "RX ONLY");
		s.accept("freqman.simplex", "SIMPLEX");
		s.accept("freqman.plan", "%s MHz: %s");
		s.accept("freqman.offset", ", offset %s MHz");
		s.accept("freqman.add_title", "New Channel");
		s.accept("freqman.edit_title", "Edit Channel");
		s.accept("freqman.form_hint", "TX blank for simplex, RX for receive only. Modes: FM DIG USB LSB CW AM.");
		s.accept("freqman.name", "Name:");
		s.accept("freqman.rx", "Receive (MHz):");
		s.accept("freqman.tx", "Transmit (MHz):");
		s.accept("freqman.mode", "Mode:");
		s.accept("freqman.tone", "CTCSS tone (Hz):");
		s.accept("freqman.notes", "Notes:");
		s.accept("freqman.bad_rx", "Not a receive frequency in MHz (146.520)");
		s.accept("freqman.bad_tx", "Not a transmit frequency: MHz, blank for simplex, or RX");
		s.accept("freqman.bad_mode", "The mode is FM, DIG, USB, LSB, CW or AM");
		s.accept("freqman.bad_tone", "Not a tone in Hz (100.0), or blank for none");
		s.accept("freqman.full", "A list holds %s channels");
		s.accept("freqman.delete_title", "Delete Channel");
		s.accept("freqman.delete", "Delete %s from the list?");
		s.accept("freqman.nothing", "Nothing to send: mark channels with Space, or pick one");
		s.accept("freqman.not_commercial", "%s is %s: commercial radios take FM and DIG channels only");
		s.accept("freqman.too_many", "This radio holds %s channels: mark fewer");
		s.accept("freqman.send_title", "Send to Radio");
		s.accept("freqman.send", "Send %s channels into the %s from channel (1 to %s):");
		s.accept("freqman.sent", "Sent %s channels into the %s from channel %s");
		s.accept("freqman.refused_title", "Not Sent");
		s.accept("freqman.read", "Read %s channels in from the %s");
		s.accept("freqman.open_title", "Open Channel List");
		s.accept("freqman.new_title", "New Channel List");
		s.accept("freqman.new", "Name of the new list (.FRQ is added):");

		// CPS, the commercial radio programmer.
		s.accept("cps.title", "CPS - Commercial Radio Programming");
		s.accept("cps.keys", "F5=Read F6=Write F9=By hand F4=Unit ID F7=Contacts F3=Open ^S=Save F8=Factory");
		s.accept("cps.no_radio", "No radio read. F5 reads a wired mobile; F9 programs radios held in your hand.");
		s.accept("cps.radio", "Radio: %s, %s");
		s.accept("cps.model", "%s  %s MHz  Digital: %s  Encryption: %s");
		s.accept("cps.no_model", "Codeplug for no particular radio");
		s.accept("cps.model.basic_handheld", "Basic handheld");
		s.accept("cps.model.upgraded_handheld", "Upgraded handheld");
		s.accept("cps.model.vhf_mobile", "VHF mobile");
		s.accept("cps.model.low_band_mobile", "Low band mobile");
		s.accept("cps.yes", "yes");
		s.accept("cps.no", "no");
		s.accept("cps.col.ch", "CH");
		s.accept("cps.col.name", "NAME");
		s.accept("cps.col.rx", "RX MHZ");
		s.accept("cps.col.tx", "TX MHZ");
		s.accept("cps.col.mode", "MODE");
		s.accept("cps.col.tone", "TONE");
		s.accept("cps.col.key", "KEY");
		s.accept("cps.col.readable", "R/H");
		s.accept("cps.blank", "-- not programmed --");
		s.accept("cps.rx_only", "RX ONLY");
		s.accept("cps.knob", "The radio's knob is on channel %s");
		s.accept("cps.knob_zone", "The radio is on channel %s (zone %s, knob %s)");
		s.accept("cps.legend", "**** = a key the radio will not show; written back it is kept. H = key hidden, R = readable.");
		s.accept("cps.status", "%s of %s channels.  Unit ID %s, %s contacts.  Enter=Edit Del=Clear ^C/^V=Copy");
		s.accept("cps.unit_none", "none");
		s.accept("cps.unit_title", "Unit ID");
		s.accept("cps.unit_hint", "Sent with every key-up (MDC on FM, in the data on digital). 0: each radio keeps its own, or gets one.");
		s.accept("cps.unit_id", "Unit ID (0-9999):");
		s.accept("cps.bad_unit", "A unit ID is 1 to 9999");
		s.accept("cps.contacts_title", "Contact List");
		s.accept("cps.contacts_hint", "Unit IDs a radio with a display shows by name (%s of %s). Enter edits.");
		s.accept("cps.contact_new", "(add a contact)");
		s.accept("cps.contact_add_title", "New Contact");
		s.accept("cps.contact_edit_title", "Contact");
		s.accept("cps.contact_hint", "A blank alias deletes the contact.");
		s.accept("cps.contact_id", "Unit ID:");
		s.accept("cps.contact_alias", "Alias (8):");
		s.accept("cps.hand_on", "Program radios: close the screen, hold a radio and right click the computer");
		s.accept("cps.hand_off", "No longer programming radios by hand");
		s.accept("cps.hand_banner", "PROGRAM RADIOS: hold a radio, right click the computer to write it; sneak to read it");
		s.accept("cps.close_button", "[ Close ]");
		s.accept("cps.hand_read", "Read the %s: %s channels (its unit ID %s stays with it)");
		s.accept("cps.hand_written", "Programmed the %s, unit ID %s");
		s.accept("cps.hand_refused", "The %s was not programmed: see the marked channels");
		s.accept("cps.hand_end.no_power", "Stopped programming radios: the computer has no power");
		s.accept("cps.hand_end.timeout", "Stopped programming radios: 5 minutes unused");
		s.accept("cps.hand_end.gone", "Stopped programming radios");
		s.accept("cps.hand_write_hint", "A radio read by hand is written by hand: hold it and right click the computer");
		s.accept("cps.hand_mode_off", "A radio read by hand is written by hand: F9, then right click the computer holding it");
		s.accept("cps.hand_no_factory", "A radio in your hand is reset by writing it: F9 and right click the computer");
		s.accept("cps.problem", "CH %s: %s");
		s.accept("cps.edit_title", "Channel %s");
		s.accept("cps.form_hint", "RX blank clears the channel. TX blank for simplex, RX for receive only. Key 0 is clear; **** keeps the radio's.");
		s.accept("cps.name", "Name (8):");
		s.accept("cps.rx", "Receive (MHz):");
		s.accept("cps.tx", "Transmit (MHz):");
		s.accept("cps.mode", "Mode (FM/DIG):");
		s.accept("cps.tone", "CTCSS tone (Hz):");
		s.accept("cps.key", "Encryption key:");
		s.accept("cps.readable", "Key readable (Y/N):");
		s.accept("cps.bad_mode", "A commercial channel is FM or DIG");
		s.accept("cps.bad_key", "A key is 1 to 65535, 0 for none (**** only where the radio hides one)");
		s.accept("cps.read", "Read the %s: %s channels");
		s.accept("cps.write_title", "Write to Radio");
		s.accept("cps.write", "Write these %s channels into the %s? Its old channels are replaced.");
		s.accept("cps.written", "Written into the %s");
		s.accept("cps.refused_title", "Not Written");
		s.accept("cps.refused", "The radio cannot take these channels; nothing was written:");
		s.accept("cps.factory_title", "Factory Reset");
		s.accept("cps.factory", "Erase the %s back to factory blank? Every channel and key on it is lost.");
		s.accept("cps.factory_done", "The %s is erased, blank as from the factory");
		s.accept("cps.read_first", "Read a radio first (F5)");
		s.accept("cps.copied", "Channel %s copied");
		s.accept("cps.hidden_no_copy", "A hidden key stays with its own channel: it cannot be copied");
		s.accept("cps.unsaved_title", "Not Written");
		s.accept("cps.unsaved", "The channels have changes not written to a radio or saved. Throw them away?");
		s.accept("cps.save_title", "Save Codeplug");
		s.accept("cps.save_as", "File name (.CPG is added):");
		s.accept("cps.saved", "Saved %s");
		s.accept("cps.opened", "Opened %s (%s channels)");
		s.accept("cps.opened_dropped", "Opened %s (%s channels); %s past channel %s left out: this radio has no room for them");
		s.accept("cps.open_title", "Open Codeplug");

		// SOLAR.
		s.accept("solar.title_solar", "SOLAR - Solar Weather");
		s.accept("solar.title_forecast", "SOLAR - Weather Forecast");
		s.accept("solar.keys", "Tab=Solar/Forecast  F5=Refresh  F6=Print  F7=Save  Up/Down=Scroll  Esc=Exit");
		s.accept("solar.tab_solar", "SOLAR");
		s.accept("solar.tab_forecast", "FORECAST");
		s.accept("solar.reading", "Listening for the reports...");
		s.accept("solar.via_direct", "Reports: the server's own");
		s.accept("solar.via_processor", "Reports: Ionospheric Processor %s");
		s.accept("solar.via_radio", "Reports: geophysical alert and weather broadcasts, copied on the %s");
		s.accept("solar.no_receiver_title", "No Receiver");
		s.accept("solar.no_receiver_hint", "Wire a powered radio with a real antenna to the computer\n(touching it or by data wire), or an Ionospheric Processor.");
		s.accept("solar.no_event", "No solar weather has been reported yet.");
		s.accept("solar.sun", "The Sun");
		s.accept("solar.conditions", "Conditions: %s (level %s of 6)");
		s.accept("solar.noise", "Noise floor: %s%%");
		s.accept("solar.daytime", "Daytime: the D layer soaks up the low bands");
		s.accept("solar.nighttime", "Night: the low bands open up");
		s.accept("solar.left", "This lasts about %s more minutes");
		s.accept("solar.bands", "HF Bands");
		s.accept("solar.col.band", "BAND");
		s.accept("solar.col.now", "NOW");
		s.accept("solar.col.day", "DAY");
		s.accept("solar.col.night", "NIGHT");
		s.accept("solar.low_band", "Low Band");
		s.accept("solar.sky", "VHF and the Sky");
		s.accept("solar.vhf", "VHF: %s");
		s.accept("solar.vhf.normal", "normal, line of sight");
		s.accept("solar.vhf.aurora", "aurora overhead: signals bounce off it, with a rasp");
		s.accept("solar.aurora_active", "Aurora: active beyond Z %s");
		s.accept("solar.aurora_expected", "Aurora: expected beyond Z %s");
		s.accept("solar.aurora_none", "Aurora: none");
		s.accept("solar.es_open", "Sporadic E: open (VHF skips a long way)");
		s.accept("solar.es_closed", "Sporadic E: closed");
		s.accept("solar.next", "Next: %s in about %s minutes");
		s.accept("solar.next_unknown", "Next: not known yet (the forecast comes near the end of this spell)");
		s.accept("solar.blackout_warning", "RADIO BLACKOUT COMING: HF will close.");
		s.accept("solar.here", "Here Now");
		s.accept("solar.here_values", "%sC (%sF)  %s hPa  %s%% humidity  wind %s m/s from %s");
		s.accept("solar.warning", "WARNING: %s");
		s.accept("solar.report", "Report");
		s.accept("solar.report_local", "Report, Outlook and Storms");
		s.accept("solar.more", "%s/%s");
		s.accept("solar.print_header", "SOLAR WEATHER REPORT");
		s.accept("solar.print_solar", "Solar Weather");
		s.accept("solar.print_forecast", "Forecast");
	}

	/** The radio computer's radio programs (1.12 step 7): APRS, FAX, SSTV, WEFAX and DFIND. */
	private static void addRadioApps(LanguageProvider provider) {
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		provider.add("item.radiocraft.wefax_chart.station", "WEFAX Chart from %s");
		// Errors from the programs' server sides.
		s.accept("err.station_no_interface", "No radio interface cabled to the computer: touch one to it, or run a data wire to it");
		s.accept("err.station_no_callsign", "The station has no callsign: set one on the radio interface (or in APRS with F7)");
		s.accept("err.station_no_own_callsign", "You have no callsign of your own yet: type one in");
		s.accept("err.station_no_radio", "No radio switched on at the radio interface");
		s.accept("err.aprs_no_vhf", "APRS needs a VHF radio switched on at the radio interface");
		s.accept("err.aprs_not_sent", "No radio took the packet");
		s.accept("err.aprs_no_recipient", "Who is it to?");
		s.accept("err.aprs_no_text", "Nothing to send");
		s.accept("err.fax_busy", "A fax is already going on");
		s.accept("err.fax_no_callsign", "Fax to which callsign? (A fax goes to one station, never to ALL)");
		s.accept("err.fax_to_self", "That is this station's own callsign");
		s.accept("err.fax_cannot_send", "That file cannot be faxed: pictures (.IMG), text (.TXT) and books (.BOK) can");
		s.accept("err.picture_not_image", "That is not a picture (.IMG)");
		s.accept("err.wefax_busy", "A chart is already coming in");
		s.accept("err.wefax_no_antenna", "The coast station is not heard: WX REPT needs a radio with a real antenna at the radio interface");
		s.accept("err.df_no_finder", "No direction finder cabled to the computer");
		s.accept("err.df_no_search", "No such saved search");
		s.accept("err.df_nothing_to_save", "No bearings to save yet");
		s.accept("err.df_out_of_band", "The direction finder does not cover that frequency");
		s.accept("err.df_entries_full", "The direction finder holds six typed bearings: remove one first (F8)");

		// The station line every radio program shows.
		s.accept("station.reading", "Asking the station...");
		s.accept("station.no_interface", "No radio interface cabled to the computer: touch one to it or run a data wire");
		s.accept("station.no_call", "(no callsign)");
		s.accept("station.no_radio", "Station %s: no radio switched on at the radio interface");
		s.accept("station.no_vhf", "Station %s: APRS needs a VHF radio switched on at the radio interface");
		s.accept("station.ready", "Station %s on %s MHz");
		s.accept("station.ready_no_call", "On %s MHz, but no callsign: set one on the radio interface (or APRS, F7)");

		// APRS.
		s.accept("aprs.title", "APRS - %s");
		s.accept("aprs.keys", "Tab=Page  F5=Beacon  N=Message  R=Reply  F6=Setup  F7=Call  F8=Clear  Esc=Exit");
		s.accept("aprs.tab_stations", "STATIONS");
		s.accept("aprs.tab_messages", "MESSAGES");
		s.accept("aprs.tab_map", "MAP");
		s.accept("aprs.beacon_auto", "Beacon every %s min, last %s.  Symbol %s.  Comment: %s");
		s.accept("aprs.beacon_manual", "Beacon by hand (F5), last %2$s.  Symbol %3$s.  Comment: %4$s");
		s.accept("aprs.never", "never");
		s.accept("aprs.ago", "%s ago");
		s.accept("aprs.beaconed", "Beacon sent");
		s.accept("aprs.stations", "Stations heard: %s");
		s.accept("aprs.stations_head", "CALL       SYMBOL     DIST BRG HEARD HOP  COMMENT");
		s.accept("aprs.no_stations", "No stations heard yet: they show here as their beacons come in.");
		s.accept("aprs.at", "At %s, %s: %s blocks away, bearing %s");
		s.accept("aprs.no_position", "Its beacons carry no position");
		s.accept("aprs.first_heard", "First heard %s ago.  %s");
		s.accept("aprs.messages", "Messages: %s");
		s.accept("aprs.to_short", "to %s");
		s.accept("aprs.from_short", "from %s");
		s.accept("aprs.delivery.sending", "SENDING");
		s.accept("aprs.delivery.retrying", "RETRY %s");
		s.accept("aprs.delivery.sent", "SENT");
		s.accept("aprs.delivery.failed", "FAILED");
		s.accept("aprs.new", "NEW");
		s.accept("aprs.no_messages", "No messages. N writes one.");
		s.accept("aprs.map_reading", "Painting the map...");
		s.accept("aprs.message_in", "Message from %s");
		s.accept("aprs.station_in", "New station heard: %s");
		s.accept("aprs.compose_title", "Message");
		s.accept("aprs.compose_hint", "To a callsign (or ALL for a bulletin), up to 100 characters. It goes again until the far end acks it.");
		s.accept("aprs.to", "To");
		s.accept("aprs.text", "Text");
		s.accept("aprs.need_to", "Who is it to?");
		s.accept("aprs.need_text", "Nothing to send");
		s.accept("aprs.sent", "Message to %s on its way");
		s.accept("aprs.setup_title", "Beacon");
		s.accept("aprs.setup_hint", "Symbols: %s. Automatic beacons go every %s minutes while the VHF radio is on.");
		s.accept("aprs.comment", "Comment");
		s.accept("aprs.icon", "Symbol");
		s.accept("aprs.auto", "Auto (Y/N)");
		s.accept("aprs.call_title", "Station callsign");
		s.accept("aprs.call_hint", "The callsign the radio interfaces send under. Leave it empty for your own.");
		s.accept("aprs.clear_title", "Clear");
		s.accept("aprs.clear_ask", "Forget every station heard?");

		// FAX.
		s.accept("fax.title", "FAX - Radio Fax");
		s.accept("fax.keys", "F3=Send  F4=Hang up  Enter=Open  F5=Print  F6=Scan+Send  F7=Auto  Del=Delete");
		s.accept("fax.line", "LINE");
		s.accept("fax.from", "From %s: %s");
		s.accept("fax.to", "To %s: %s");
		s.accept("fax.packets", "%s/%s, %s again");
		s.accept("fax.auto_on", "AUTO ANSWER ON");
		s.accept("fax.auto_off", "AUTO ANSWER OFF");
		s.accept("fax.machine", "Fax machine answers");
		s.accept("fax.inbox", "Received (%s)");
		s.accept("fax.inbox_empty", "Nothing received yet.");
		s.accept("fax.log", "Log");
		s.accept("fax.received", "%s received from %s");
		s.accept("fax.pick_title", "Fax which file?");
		s.accept("fax.cannot_send_kind", "A fax sends pictures (.IMG), text (.TXT) and books (.BOK), not .%s files");
		s.accept("fax.to_title", "Fax to");
		s.accept("fax.to_hint", "Fax %s to which callsign?");
		s.accept("fax.to_hint_recent", "Fax %s to which callsign? Lately: %s");
		s.accept("fax.delete_title", "Delete");
		s.accept("fax.delete_ask", "Delete %s from the disk?");

		// SSTV and WEFAX share their screen.
		s.accept("pic.live", "COMING IN");
		s.accept("pic.nothing", "No pictures yet.");
		s.accept("pic.reading", "Reading...");
		s.accept("pic.receiving_title", "On the air");
		s.accept("pic.from", "%s: %s");
		s.accept("pic.lines", "Line %s of %s, %s lost to noise");
		s.accept("pic.press_l", "L shows it");
		s.accept("pic.gallery", "Pictures (%s)");
		s.accept("pic.gallery_empty", "No pictures on the disk.");
		s.accept("pic.info", "From %s: %s (%s noisy lines)");
		s.accept("pic.received", "%s received from %s");
		s.accept("pic.lost_disk_full", "A picture from %s was lost: the disk is full");
		s.accept("pic.delete_title", "Delete");
		s.accept("pic.delete_ask", "Delete %s from the disk?");
		s.accept("sstv.title", "SSTV - Slow-Scan Television");
		s.accept("sstv.keys", "F3=Send  F4=Caption  F5=Print  F6=Scan  Del=Delete  L=Live  Esc=Exit");
		s.accept("sstv.listening", "Listening for pictures to all or to us.");
		s.accept("sstv.send_title", "Send by SSTV");
		s.accept("sstv.send_hint", "Send %s on the air? Everyone in range receives it.");
		s.accept("sstv.sending", "Sending %s: about half a minute on the air");
		s.accept("sstv.caption_title", "Caption");
		s.accept("sstv.caption_hint", "Text along the top and the bottom, in capitals. The captioned picture is saved as a new file.");
		s.accept("sstv.caption_top", "Top");
		s.accept("sstv.caption_bottom", "Bottom");
		s.accept("sstv.caption_save", "Save as");
		s.accept("sstv.caption_none", "Write a caption first");
		s.accept("sstv.scan_not_picture", "Scanned into %s: that is not a picture");
		s.accept("wefax.title", "WEFAX - Weather Fax");
		s.accept("wefax.keys", "F3=Send  F4=WX REPT  F5=Print  F7=Chart  Del=Delete  L=Live  Esc=Exit");
		s.accept("wefax.listening", "Listening for charts and solar bulletins.");
		s.accept("wefax.send_title", "Send as WEFAX");
		s.accept("wefax.send_hint", "Send %s on the air as a weather chart? Everyone in range receives it.");
		s.accept("wefax.sending", "Sending %s as a chart: it takes as long as a fax sheet");
		s.accept("wefax.wxrept_started", "Coast station: the radar chart, then the forecast");
		s.accept("wefax.chart_title", "Chart");
		s.accept("wefax.chart_ask", "Draw a chart of the weather here, to send as this station's own?");
		s.accept("wefax.chart_radar", "Radar");
		s.accept("wefax.chart_forecast", "Forecast");
		s.accept("wefax.coast_coming", "Coast station: %s more to come");
		s.accept("wefax.no_antenna_hint", "WX REPT needs a radio with an antenna");

		// DFIND.
		s.accept("df.title", "DFIND - Direction Finding");
		s.accept("df.keys", "Tab=Page  F3=Save  F5=Power  <>=Tune  PgUp/Dn=Band  F6=Add  F7=MHz  F8=Remove");
		s.accept("df.tab_map", "MAP");
		s.accept("df.tab_3d", "3D");
		s.accept("df.tab_searches", "SEARCHES");
		s.accept("df.set", "Direction finder");
		s.accept("df.reading", "Reading the set...");
		s.accept("df.no_finder", "No direction finder");
		s.accept("df.no_finder_hint", "Cable one to the computer.");
		s.accept("df.off", "Switched off (F5)");
		s.accept("df.no_power", "No power on its wire");
		s.accept("df.on", "On");
		s.accept("df.modules", "Modules: %s locked, %s/%s ready");
		s.accept("df.meter", "Signal");
		s.accept("df.bearing", "Bearing %s, +/- %s deg");
		s.accept("df.no_bearing", "No bearing yet");
		s.accept("df.here", "Here: %s, %s");
		s.accept("df.fix", "FIX at %s, %s");
		s.accept("df.fix_radius", "within %s blocks");
		s.accept("df.problem.ok", "Fix");
		s.accept("df.problem.too_few", "No fix: needs two bearings");
		s.accept("df.problem.too_close", "No fix: stations too close");
		s.accept("df.problem.too_parallel", "No fix: bearings too parallel");
		s.accept("df.problem.diverge", "No fix: bearings point apart");
		s.accept("df.bearings", "Bearings:");
		s.accept("df.source.own", "HERE");
		s.accept("df.source.typed", "TYPE");
		s.accept("df.source.linked", "LINK");
		s.accept("df.map_now", "The search now");
		s.accept("df.map_search", "Saved search %s");
		s.accept("df.map_reading", "Painting the map...");
		s.accept("df.map_back", "Esc: back");
		s.accept("df.scale", "Across:");
		s.accept("df.scale_value", "%s blocks");
		s.accept("df.legend", "Key:");
		s.accept("df.legend_own", "Our bearing");
		s.accept("df.legend_typed", "Typed in");
		s.accept("df.legend_linked", "By microwave");
		s.accept("df.legend_fix", "Fix, circle");
		s.accept("df.legend_spread", "Dashes: error");
		s.accept("df.scene_empty", "No bearings to show yet.");
		s.accept("df.scene_fix", "The hill is where it likely is: %s blocks of error");
		s.accept("df.scene_no_fix", "No fix yet: the wedges are the bearings and their error");
		s.accept("df.searches_head", "UTC         MHz     N  FIX          NOTE");
		s.accept("df.no_searches", "No searches saved. F3 saves the one now.");
		s.accept("df.no_fix_short", "no fix");
		s.accept("df.searches_keys", "Enter=Show on the map  Del=Delete");
		s.accept("df.freq_title", "Frequency");
		s.accept("df.freq_hint", "MHz, in a band the direction finder covers:");
		s.accept("df.not_a_number", "%s is not a number");
		s.accept("df.add_title", "Add a bearing");
		s.accept("df.add_hint", "A bearing another station took: where it was and which way (compass degrees, 0 north).");
		s.accept("df.add_x", "X");
		s.accept("df.add_z", "Z");
		s.accept("df.add_bearing", "Bearing");
		s.accept("df.add_sigma", "Error +/-");
		s.accept("df.add_bad", "Numbers only");
		s.accept("df.save_title", "Save search");
		s.accept("df.save_hint", "A note for it (what you were hunting):");
		s.accept("df.saved", "Search saved in DFSEARCH.DAT");
		s.accept("df.delete_title", "Delete");
		s.accept("df.delete_ask", "Delete this saved search?");
	}

	/** The secret programs (1.12 step 8): Snake, Pileup, the music player and the Bible, and the ham shack's loot. */
	private static void addSecretApps(LanguageProvider provider) {
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		provider.add(Radiocraft.translationKey("tooltip", "floppy_disk.secret"), "A rare disk: it was never sold in shops.");
		// Shared by the games.
		s.accept("game.paused", "PAUSED");
		s.accept("game.resume", "Press P or Enter to carry on");
		s.accept("game.over", "GAME OVER: %s points");
		s.accept("game.new_score", "A new high score: number %s on this computer!");
		s.accept("game.again", "Press Enter to play again");
		s.accept("game.high_scores", "High scores");
		s.accept("game.no_scores", "No scores yet.");
		// SNAKE.
		s.accept("snake.title", "SNAKE   Score %s   Best %s");
		s.accept("snake.keys", "Enter=Play  P=Pause  Esc=Exit");
		s.accept("snake.keys_play", "Arrows/WASD=Steer  P=Pause  Esc=Exit");
		s.accept("snake.name", "S N A K E");
		s.accept("snake.start", "Press Enter to start. Steer with the arrows.");
		s.accept("snake.status", "Length %s   Eaten %s");
		// PILEUP.
		s.accept("pileup.title", "PILEUP - work the callers before the band fills up");
		s.accept("pileup.keys", "Enter=Play  P=Pause  Esc=Exit");
		s.accept("pileup.keys_play", "Left/Right=Move  Up/X=Turn  Z=Turn back  Down=Drop  Space=Slam  P=Pause");
		s.accept("pileup.name", "P I L E U P");
		s.accept("pileup.start", "Press Enter to start");
		s.accept("pileup.log", "Log");
		s.accept("pileup.band", "Band:   %s");
		s.accept("pileup.qsos", "QSOs:   %s");
		s.accept("pileup.score", "Score:  %s");
		s.accept("pileup.best", "Best:   %s");
		s.accept("pileup.next_band", "%s QSOs to the next band");
		s.accept("pileup.next", "Next caller");
		s.accept("pileup.help", "How to play");
		s.accept("pileup.help.0", "Callers fall into the");
		s.accept("pileup.help.1", "band. Fill a row from");
		s.accept("pileup.help.2", "edge to edge to work");
		s.accept("pileup.help.3", "them: it is a QSO and");
		s.accept("pileup.help.4", "leaves the band.");
		s.accept("pileup.help.5", "");
		s.accept("pileup.help.6", "Every 8 QSOs you move");
		s.accept("pileup.help.7", "up a band: faster.");
		s.accept("pileup.help.8", "A full band ends it.");
		// MUSIC.
		s.accept("music.title", "MUSIC - the computer's sound card");
		s.accept("music.keys", "Enter=Play  Space=Pause  S=Stop  N=Next  B=Back  R=Repeat  C=Close  Esc=Leave playing");
		s.accept("music.close_button", "[ Close ]");
		s.accept("music.elsewhere", "Music is playing on another computer. Pick a tune to play it here instead.");
		s.accept("music.tracks", "Tracks");
		s.accept("music.col_title", "Title");
		s.accept("music.col_composer", "Composer");
		s.accept("music.col_time", "Time");
		s.accept("music.now", "Now playing");
		s.accept("music.stopped", "Stopped. Pick a tune and press Enter.");
		s.accept("music.paused", "[paused]");
		s.accept("music.repeat", "(repeat)");
		s.accept("music.no_tracks", "No tunes found on the disk.");
		s.accept("music.missing", "Cannot find the tune %s.");
		s.accept("music.no_sound", "The sound card does not work: %s");
		// BIBLE.
		s.accept("bible.title", "BIBLE - World English Bible   %s");
		s.accept("bible.keys", "PgUp/PgDn=Read  </>=Chapter  F3=Go to  F4=Books  F5=Find  F6=Found  Esc=Exit");
		s.accept("bible.heading", "THE GOSPEL ACCORDING TO %s, CHAPTER %s");
		s.accept("bible.where", "%s, chapter %s of %s   (line %s of %s)");
		s.accept("bible.missing", "The Bible text is missing from this computer's disk.");
		s.accept("bible.goto_title", "Go to");
		s.accept("bible.goto_prompt", "Book, chapter and verse (John 3:16, Mk 4, Luke):");
		s.accept("bible.goto_bad", "There is no %s in the four gospels.");
		s.accept("bible.books_title", "Books");
		s.accept("bible.books_prompt", "Pick a book:");
		s.accept("bible.book_line", "%s (%s chapters)");
		s.accept("bible.chapters_prompt", "Pick a chapter:");
		s.accept("bible.chapter_line", "Chapter %s (%s verses)");
		s.accept("bible.find_title", "Find");
		s.accept("bible.find_prompt", "Verses with all of these words:");
		s.accept("bible.results_title", "BIBLE - verses with \"%s\"");
		s.accept("bible.results_keys", "Enter=Read it  F5=Find again  Esc=Back");
		s.accept("bible.results_count", "%s verses found. Pick one to read it in its chapter.");
		s.accept("bible.results_many", "More than %s verses found: the first ones are listed. Try more words.");
		s.accept("bible.results_none", "No verse has all of those words.");
	}

	/** ADS-B (1.12 step 9): the beacon, receiver and antenna, their screens, and the computer's ADS-B program. */
	private static void addAdsb(LanguageProvider provider) {
		provider.addBlock(RadiocraftBlocks.ADSB_BEACON, "ADS-B Beacon");
		provider.addBlock(RadiocraftBlocks.ADSB_RECEIVER, "ADS-B Receiver");
		provider.addBlock(RadiocraftBlocks.ADSB_ANTENNA, "ADS-B Antenna");
		provider.add(Radiocraft.translationKey("container", "adsb_beacon"), "ADS-B Beacon");
		provider.add(Radiocraft.translationKey("container", "adsb_receiver"), "ADS-B Receiver");
		provider.add(Radiocraft.translationKey("tooltip", "adsb_beacon"), "1090 MHz transponder for anything that flies: sends its position, flight ID, altitude, speed and track once a second. Works on Sable ships and Create Aeronautics craft. Place it facing the nose. Needs power.");
		provider.add(Radiocraft.translationKey("tooltip", "adsb_receiver"), "Shows the aircraft its ADS-B antenna hears on a green radar screen. The higher the aircraft flies, the further: very far from Y 200 up. Needs power; a radio computer cabled to it runs the ADS-B program.");
		provider.add(Radiocraft.translationKey("tooltip", "adsb_antenna"), "Short 1090 MHz vertical for an ADS-B receiver: coax it to the receiver, or set it on top of one.");
		java.util.function.BiConsumer<String, String> b = (key, text) -> provider.add("screen.radiocraft.adsb_beacon." + key, text);
		b.accept("callsign", "Flight ID");
		b.accept("callsign_hint", "Up to 8 letters and digits; Enter sets it. Left empty, a beacon on a named ship sends the ship's name.");
		b.accept("squawk", "Squawk");
		b.accept("squawk_hint", "Four digits 0-7; Enter sets it. 1200 VFR; 7700 emergency, 7600 radio failure, 7500 hijack.");
		b.accept("bad_squawk", "A squawk is four digits, each 0 to 7");
		b.accept("power_on", "Turn on");
		b.accept("power_off", "Turn off");
		b.accept("state.off", "OFF");
		b.accept("state.no_power", "ON, NO POWER");
		b.accept("state.transmitting", "TRANSMITTING 1090 MHz");
		b.accept("icao", "ICAO address %s");
		b.accept("sending", "Sends %s  squawk %s");
		b.accept("on_ship", "On ship %s");
		b.accept("on_ground", "Not on a ship (a fixed beacon)");
		b.accept("position", "Position %s, %s, %s");
		b.accept("altitude", "ALT %s ft   V/S %s ft/min");
		b.accept("speed", "GS %s kt   TRK %s");
		b.accept("silent", "Silent: not transmitting");
		java.util.function.BiConsumer<String, String> r = (key, text) -> provider.add("screen.radiocraft.adsb_receiver." + key, text);
		r.accept("power_on", "On");
		r.accept("power_off", "Off");
		r.accept("state.off", "OFF");
		r.accept("state.no_power", "NO POWER");
		r.accept("state.no_antenna", "NO ANTENNA");
		r.accept("state.receiving", "RECEIVING 1090");
		r.accept("range", "Range %s");
		r.accept("reach", "Reach %s high, %s low");
		r.accept("count", "%s aircraft");
		r.accept("no_targets", "NO TRAFFIC");
		r.accept("lost", "LOST %s S AGO");
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("err.adsb_no_receiver", "No ADS-B receiver cabled to the computer");
		s.accept("adsb.title", "ADS-B - 1090 MHz extended squitter");
		s.accept("adsb.keys", "F5=Receiver on/off  +/-=Range  Up/Down=Pick  T=Tags  Esc=Exit");
		s.accept("adsb.scope", "Scope %s");
		s.accept("adsb.traffic", "Traffic");
		s.accept("adsb.reading", "Reading the receiver...");
		s.accept("adsb.no_receiver", "No ADS-B receiver cabled to the computer.");
		s.accept("adsb.no_receiver_hint", "Touch one to it, or run a data wire to it.");
		s.accept("adsb.state.off", "Receiver OFF: F5 turns it on");
		s.accept("adsb.state.no_power", "Receiver ON, but it has no power");
		s.accept("adsb.state.no_antenna", "No ADS-B antenna on the receiver");
		s.accept("adsb.state.receiving", "Receiving 1090 MHz");
		s.accept("adsb.reach", "Reach %s high, %s low");
		s.accept("adsb.head", "FLIGHT   FL    KT TRK    KM SQWK");
		s.accept("adsb.none", "No traffic.");
		s.accept("adsb.more", "and %s more further out");
		s.accept("adsb.pick", "Up and Down pick an aircraft.");
		s.accept("adsb.detail_alt", "ALT %s ft   V/S %s ft/min");
		s.accept("adsb.detail_speed", "GS %s kt   TRK %s");
		s.accept("adsb.detail_where", "BRG %s   %s km   heard %s s ago");
		s.accept("adsb.scope_dark", "(the scope is dark)");
	}

	/** The floppy disks' names, by program id. */
	private static final Map<String, String> FLOPPY_NAMES = Map.ofEntries(
			Map.entry("bookedit", "Book Editor"), Map.entry("logbook", "Logbook"), Map.entry("freqman", "Frequency Manager"),
			Map.entry("programmer", "Radio Programmer"), Map.entry("solar", "Solar Weather"), Map.entry("aprs", "APRS"),
			Map.entry("wefax", "WEFAX"), Map.entry("radiofax", "Radio Fax"), Map.entry("sstv", "SSTV"),
			Map.entry("df", "Direction Finding"), Map.entry("snake", "Snake"), Map.entry("falling_blocks", "Pileup"),
			Map.entry("music", "Music Player"), Map.entry("bible", "Bible"), Map.entry("adsb", "ADS-B"),
			Map.entry("rtty", "RTTY"), Map.entry("bbs", "Packet BBS"), Map.entry("propagation", "Propagation Predictor"),
			Map.entry("repeaters", "Repeater Directory"), Map.entry("license", "License Study"));

	/** FCC field gear (1.12 step 10): the hat, jacket, seal banner pattern and banner names, the badge, its tooltips, messages and command. */
	private static void addFccGear(LanguageProvider provider) {
		provider.addItem(RadiocraftItems.FCC_HAT, "FCC Field Agent Hat");
		provider.addItem(RadiocraftItems.FCC_JACKET, "FCC Field Agent Jacket");
		provider.addItem(RadiocraftItems.FCC_BANNER_PATTERN, "Banner Pattern");
		provider.add(Radiocraft.translationKey("item", "fcc_banner_pattern.desc"), "FCC Seal");
		provider.addItem(RadiocraftItems.FCC_BADGE, "FCC Badge");
		provider.add(Radiocraft.translationKey("item", "fcc_banner"), "FCC Banner");
		for(net.minecraft.world.item.DyeColor colour : net.minecraft.world.item.DyeColor.values())
			provider.add("block.radiocraft.banner.fcc." + colour.getName(), dyeName(colour) + " FCC Seal");
		provider.add(Radiocraft.translationKey("tooltip", "fcc_hat"), "A 1970s FCC field agent's felt hat. Keeps the rain off about as well as a leather cap.");
		provider.add(Radiocraft.translationKey("tooltip", "fcc_jacket"), "A 1970s FCC field agent's corduroy jacket and tie. Protects like a leather tunic.");
		provider.add(Radiocraft.translationKey("tooltip", "fcc_banner_pattern"), "Use at a loom to put the FCC seal on a banner or shield.");
		provider.add(Radiocraft.translationKey("tooltip", "fcc_badge"), "Right click to show it to the players around you. Worn on the chest (body) slot with Curios.");
		provider.add(Radiocraft.translationKey("tooltip", "fcc_badge.agent"), "Field Agent %s");
		provider.add(Radiocraft.translationKey("tooltip", "fcc_badge.number"), "Badge No. %s");
		provider.add(Radiocraft.translationKey("tooltip", "fcc_badge.blank"), "Blank: not issued to anyone");
		provider.add(Radiocraft.translationKey("message", "fcc_badge.shown"), "%s shows FCC badge No. %s: Field Agent, Federal Communications Commission");
		provider.add(Radiocraft.translationKey("message", "fcc_badge.shown_other"), "%s shows an FCC badge issued to %s (No. %s)");
		provider.add(Radiocraft.translationKey("message", "fcc_badge.shown_blank"), "%s shows a blank FCC badge");
		provider.add(Radiocraft.translationKey("message", "fcc_badge.issued"), "You have been issued an FCC badge. Right click it to show it.");
		provider.add(Radiocraft.translationKey("commands", "fcc.badge.one"), "Issued an FCC badge to %s");
		provider.add(Radiocraft.translationKey("commands", "fcc.badge.many"), "Issued FCC badges to %s players");
	}

	private static void addRealWorldInterface(LanguageProvider provider) {
		provider.addBlock(RadiocraftBlocks.REAL_WORLD_INTERFACE, "Real-World Interface");
		provider.add(Radiocraft.translationKey("tooltip", "real_world_interface"), "Links the game's radios to a real radio on the server's sound card and serial port, by antenna or Everywhere. Off unless the server operator sets it up in the server config.");
		provider.add(Radiocraft.translationKey("tooltip", "real_world_interface.licence"), "Everyone who talks through it must hold a real amateur licence and use their own real callsign.");
		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.real_world_interface." + key, text);
		s.accept("warning", "LICENCE WARNING: this transmits on a REAL radio. Talk only if you hold a real amateur licence, and give your own real callsign. The server operator is the control operator and is responsible.");
		s.accept("interface", "Interface");
		s.accept("none", "(none)");
		s.accept("frequency", "MHz");
		s.accept("tune", "Tune");
		s.accept("air", "On air");
		s.accept("antenna", "Antenna");
		s.accept("everywhere", "Everywhere");
		s.accept("power", "Power");
		s.accept("state.waiting", "...");
		s.accept("state.disabled", "Turned off in the server config");
		s.accept("state.none", "Pick an interface");
		s.accept("state.unknown", "Not in the server config any more");
		s.accept("state.in_use", "In use by the interface at %s");
		s.accept("state.off", "Switched off, or no power");
		s.accept("state.starting", "Opening the devices...");
		s.accept("state.running", "Linked to the real radio");
		s.accept("input", "In: %s at %s Hz");
		s.accept("output", "Out: %s");
		s.accept("ptt_line", "PTT: %s");
		s.accept("trigger", "%s, time-out %s s");
		s.accept("device.open", "FOUND");
		s.accept("device.not_found", "NOT FOUND");
		s.accept("device.failed", "WON'T OPEN");
		s.accept("device.opening", "OPENING");
		s.accept("device.unused", "-");
		s.accept("lamp.ptt", "PTT");
		s.accept("lamp.tx", "TX");
		s.accept("lamp.tot", "TOT");
		s.accept("lamp.power", "ON");
		s.accept("level.in", "In");
		s.accept("level.out", "Out");
		s.accept("licence", "I hold an amateur licence and will give my own callsign");
		s.accept("licence.done", "Licence acknowledged: your voice can go out");
		s.accept("licence.tooltip", "Click once (for every interface on this server). Until you do, nothing you say on the air goes out through a Real-World Interface.");
		java.util.function.BiConsumer<String, String> m = (key, text) -> provider.add(Radiocraft.translationKey("message", "real_world_interface." + key), text);
		m.accept("op_only", "Only an operator can change or switch a Real-World Interface");
		m.accept("disabled", "The Real-World Interface is turned off in the server config");
		m.accept("unknown", "No interface named %s in the server config");
		m.accept("in_use", "%s is in use by the interface at %s");
		m.accept("bad_frequency", "No band holds %s MHz");
		java.util.function.BiConsumer<String, String> c = (key, text) -> provider.add(Radiocraft.translationKey("commands", "realworld." + key), text);
		c.accept("disabled", "The Real-World Interface is off: set real_world_interface_enabled in the server config");
		c.accept("listing", "Asking the sound system and serial ports...");
		c.accept("inputs", "Sound card inputs (input=):");
		c.accept("outputs", "Sound card outputs (output=):");
		c.accept("ports", "Serial ports (serial=):");
		c.accept("none", "No interfaces in real_world_interfaces");
		c.accept("line", "%s: block %s, %s");
	}

	private static void addModerationComputer(LanguageProvider provider) {
		provider.addBlock(RadiocraftBlocks.MODERATION_COMPUTER, "Radio Moderation Computer");
		provider.add(Radiocraft.translationKey("container", "moderation_computer"), "Radio Moderation Computer");
		provider.add(Radiocraft.translationKey("tooltip", "moderation_computer"), "For server admins: who is talking, recordings, the link and radio maps. Locked to whoever places it. Not craftable.");
		provider.add(Radiocraft.translationKey("message", "moderation_computer.unowned"), "Placed without an owner: an operator can claim it by using it");
		provider.add(Radiocraft.translationKey("message", "moderation_computer.claimed"), "This moderation computer is yours now");
		provider.add(Radiocraft.translationKey("message", "moderation_computer.locked"), "Locked: this moderation computer belongs to %s");
		provider.add(Radiocraft.translationKey("message", "moderation_computer.locked_unowned"), "Locked: only an operator can claim this moderation computer");
		provider.add(Radiocraft.translationKey("message", "moderate.heard"), "Heard by %s: %s, %s MHz");
		provider.add(Radiocraft.translationKey("commands", "moderation.not_computer"), "No moderation computer at %s");
		provider.add(Radiocraft.translationKey("commands", "moderation.unlocked"), "Moderation computer at %s unlocked (was %s's); the next operator to use it owns it");

		java.util.function.BiConsumer<String, String> s = (key, text) -> provider.add(Radiocraft.translationKey("screen", "moderation_computer." + key), text);
		s.accept("tab.live", "Live");
		s.accept("tab.live_proximity", "Live");
		s.accept("tab.archive", "Archive");
		s.accept("tab.map", "Map");
		s.accept("tab.links", "Links");
		s.accept("tab.tapes", "Tapes");
		s.accept("tab.proximity", "Proximity");
		s.accept("tab.access", "Access");
		s.accept("range.last_hour", "Last hour");
		s.accept("range.today", "Today");
		s.accept("range.last_24_hours", "Last 24 hours");
		s.accept("range.last_7_days", "Last 7 days");
		s.accept("range.all", "Everything kept");
		for(String[] column : new String[][] { { "talker", "Talker" }, { "kind", "Kind" }, { "frequency", "MHz" }, { "radio", "Radio" }, { "position", "Where" },
				{ "hearing", "Heard" }, { "time", "Time" }, { "speaker", "Speaker" }, { "length", "Length" }, { "from", "From" }, { "to", "To" }, { "count", "Times" },
				{ "recorded", "Recorded" }, { "speakers", "Speakers" }, { "plays", "Aired" }, { "last_played", "Last aired" }, { "admins", "Admins" }, { "who", "Who" },
				{ "action", "Did" }, { "transmission", "Recording" }, { "detail", "Detail" } })
			s.accept("col." + column[0], column[1]);
		for(String[] kind : new String[][] { { "player_voice", "Voice" }, { "proximity", "Voice chat" }, { "autopatch", "Phone patch" }, { "block_audio", "Block audio" },
				{ "tape", "Tape" }, { "server_tape", "Server tape" }, { "tape_recording", "Tape recorded" }, { "commercial", "Broadcast" }, { "repeated", "Repeated" },
				{ "parrot", "Parrot echo" }, { "dtmf", "DTMF" }, { "cw", "CW" }, { "data", "Data" }, { "real_world", "Real radio" } })
			s.accept("kind." + kind[0], kind[1]);
		s.accept("kinds.all", "All kinds");
		s.accept("kinds.voice", "Voice");
		s.accept("kinds.tapes", "Tapes");
		s.accept("kinds.data", "Data");
		s.accept("kinds.cw", "CW");
		s.accept("kinds.notes", "Notes");
		s.accept("role.either", "talked or heard");
		s.accept("role.talker", "talked");
		s.accept("role.hearer", "heard");
		s.accept("radio.handheld", "Handheld");
		s.accept("radio.manpack", "Manpack");
		s.accept("radio.commercial_module", "Commercial module");
		s.accept("radio.proximity", "Voice chat");
		s.accept("on", "on");
		s.accept("off", "off");

		s.accept("live.heading", "On the air now");
		s.accept("live.heading_proximity", "Talking in voice chat now");
		s.accept("live.empty", "Nobody is on the air.");
		s.accept("live.empty_proximity", "Nobody is talking in voice chat (or it is not being recorded).");
		s.accept("live.listen", "Listen to talker");
		s.accept("live.listen_frequency", "Listen");
		s.accept("live.system", "Include system/world stations");
		s.accept("live.freeze", "Freeze");
		s.accept("archive.logging_off", "Logging is disabled: nothing is being recorded (server config moderation_record_radio).");
		s.accept("archive.logging_off_proximity", "Logging is disabled: voice chat is not being recorded (server config moderation_record_proximity).");
		s.accept("live.listen_voice", "Listen to speaker");
		s.accept("live.stop", "Stop listening");
		s.accept("live.player", "Player:");
		s.accept("live.hear_through", "Hear what they hear");
		s.accept("live.voice_of", "Hear their voice chat");
		s.accept("live.not_listening", "Not listening");
		s.accept("live.listening_sends", "Listening to %s");
		s.accept("live.listening_hears", "Hearing what %s hears");
		s.accept("live.listening_voice", "Hearing %s's voice chat");
		s.accept("live.listening_frequency", "Tuned to %s MHz");

		s.accept("archive.player", "Player");
		s.accept("archive.anyone", "anyone");
		s.accept("archive.words", "Words");
		s.accept("archive.words_hint", "name, callsign, text...");
		s.accept("archive.mhz", "MHz");
		s.accept("archive.to", "to");
		s.accept("archive.kind", "Kind");
		s.accept("archive.when", "When");
		s.accept("archive.flagged_only", "Flagged only");
		s.accept("archive.radio", "Radio");
		s.accept("archive.where", "Where");
		s.accept("archive.pos_hint", "x, y, z");
		s.accept("archive.world", "World");
		s.accept("archive.any_world", "Any world");
		s.accept("archive.search", "Search");
		s.accept("archive.clear", "Clear");
		s.accept("archive.press_search", "Set the filters and press Search.");
		s.accept("archive.none", "Nothing matches.");
		s.accept("archive.no_results", "No results");
		s.accept("archive.page", "%s-%s of %s");
		s.accept("archive.pick", "Pick a recording on the left to see it here.");
		s.accept("archive.play_as", "Play");
		s.accept("archive.clean", "as sent (clean)");
		s.accept("archive.heard_by", "as heard by %s (%s%%)");
		s.accept("archive.play", "Play");
		s.accept("archive.stop", "Stop");
		s.accept("archive.flag", "Flag");
		s.accept("archive.unflag", "Unflag");
		s.accept("archive.export", "Export");
		s.accept("archive.export_flagged", "Export flagged");

		s.accept("detail.kind", "Kind: %s");
		s.accept("detail.flagged", "FLAGGED (kept)");
		s.accept("detail.talkers", "Talkers: %s");
		s.accept("detail.frequency", "Frequency: %s MHz %s, band %s");
		s.accept("detail.ctcss", "tone %s Hz");
		s.accept("detail.radio", "Radio: %s at %s, %s");
		s.accept("detail.antenna", "Antenna at %s");
		s.accept("detail.hearers", "Heard by %s:");
		s.accept("detail.more", "and %s more");
		s.accept("detail.text", "Text: %s");
		s.accept("detail.cw", "CW: %s");
		s.accept("detail.packets", "Data packets (%s):");
		s.accept("detail.linked", "Repeats recording #%s");
		s.accept("detail.tape", "Tape recording %s");
		s.accept("detail.frames", "Audio: %s frames kept");
		s.accept("detail.no_audio", "No audio kept");

		s.accept("map.refresh", "Refresh");
		s.accept("map.fit", "Fit");
		s.accept("map.show_in_archive", "Show in Archive");
		s.accept("map.scale", "%s blocks/pixel, %s radios. Drag to move, wheel to zoom.");
		s.accept("map.empty", "No radios on the air here in this time.");
		s.accept("map.last_talker", "Last talker: %s");
		s.accept("map.frequency", "Frequency: %s MHz");
		s.accept("map.counts", "Sent %s, heard %s");
		s.accept("map.last", "Last on the air: %s");

		s.accept("links.proximity", "Voice chat instead of radio");
		s.accept("links.empty", "Nobody heard anybody in this time.");
		s.accept("tapes.speaker", "Speaker");
		s.accept("tapes.empty", "No player tapes recorded or aired in this time.");
		s.accept("proximity.live", "On now");
		s.accept("proximity.archive", "Archive");
		s.accept("proximity.recording", "Voice chat is being recorded.");
		s.accept("proximity.not_recording", "Voice chat is not recorded (moderation_record_proximity is off).");

		s.accept("access.owner", "Owner: %s");
		s.accept("access.you_own", "You own this computer.");
		s.accept("access.owner_only", "Only the owner can change who has access.");
		s.accept("access.no_admins", "No admins added.");
		s.accept("access.name_hint", "player name");
		s.accept("access.add", "Add admin");
		s.accept("access.remove", "Remove selected");
		s.accept("access.transfer_label", "Hand the computer over to:");
		s.accept("access.transfer", "Hand over");
		s.accept("access.reboot", "Reboot");
		s.accept("access.audit", "Audit log");
		s.accept("access.no_audit", "Nothing logged in this time.");

		s.accept("status.rec_on", "REC: radio %s, voice chat %s");
		s.accept("status.rec_off", "Not recording");
		s.accept("status.storage", "%s of %s GB, kept %s days");
		s.accept("status.no_log", "No log");

		s.accept("msg.admin_added", "%s can open this computer now");
		s.accept("msg.admin_removed", "%s can no longer open this computer");
		s.accept("msg.transferred", "This computer belongs to %s now; you stay on as an admin");
		s.accept("msg.exported", "Exported %s file(s): %s");
		s.accept("msg.exported_none", "Nothing to export: no audio kept");
		s.accept("err.unknown_player", "No player called %s is known to this server");
		s.accept("err.unknown_transmission", "That recording is gone (past retention?)");
		s.accept("err.not_running", "The moderation log is not running: turn on moderation_record_radio");
		s.accept("err.failed", "Failed: %s");
		s.accept("err.owner_only", "Only the owner can do that");
		s.accept("err.admins_full", "This computer has as many admins as it can take");
		s.accept("err.already_admin", "%s can already open this computer");
		s.accept("err.denied", "You no longer have access to this computer");
		s.accept("err.bad_frequency", "Type frequencies in MHz, like 146.52");
		s.accept("err.bad_position", "Type a position as x, y, z");
		s.accept("err.play_no_voice", "Playback needs Simple Voice Chat connected");
		s.accept("err.play_read_failed", "Could not read that recording");
		s.accept("err.play_unknown", "That recording is gone (past retention?)");
		s.accept("err.play_no_audio", "No audio was kept for that recording");
	}

	/**
	 * Names each setting and section in {@code config} for the config screen ({@code radiocraft.configuration.<key>},
	 * see {@link ConfigNames}); a key already named (in another file or section) is named once.
	 */
	private static void addConfigNames(LanguageProvider provider, com.electronwill.nightconfig.core.UnmodifiableConfig config, java.util.Set<String> done) {
		for(com.electronwill.nightconfig.core.UnmodifiableConfig.Entry entry : config.entrySet()) {
			Object value = entry.getRawValue();
			boolean section = value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig;
			if(done.add(entry.getKey()))
				provider.add(Radiocraft.MOD_ID + ".configuration." + entry.getKey(), section ? ConfigNames.section(entry.getKey()) : ConfigNames.name(entry.getKey()));
			if(value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig inner)
				addConfigNames(provider, inner, done);
		}
	}

	/** Names a radio's crystal versions: "Premium 40m Radio", "40m Radio", "Cheap 40m Radio". */
	private static void addCrystalRadio(LanguageProvider provider, CrystalVariants<Item, ? extends Item> radio, String name) {
		for(CrystalQuality quality : CrystalQuality.values())
			provider.addItem(radio.get(quality), quality.radioPrefix() + name);
	}

	public RadiocraftLanguageProvider(PackOutput output, String locale) {
		super(output, Radiocraft.MOD_ID, locale);
		this.locale = locale;
	}

	@Override
	protected void addTranslations() {
		// No error checking, I want a hard crash here if datagen has something bad as it's all hardcoded and should not run if erroring.
		// Only runs with datagen run config
		TRANSLATION_PROVIDERS.get(locale).accept(this);
	}

	/** {@code dark_oak} as {@code Dark Oak}. */
	private static String woodName(String wood) {
		StringBuilder out = new StringBuilder();
		for(String word : wood.split("_")) {
			if(!out.isEmpty())
				out.append(' ');
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	/** "light_blue" as "Light Blue". */
	private static String dyeName(net.minecraft.world.item.DyeColor colour) {
		StringBuilder out = new StringBuilder();
		for(String word : colour.getSerializedName().split("_"))
			out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		return out.toString();
	}
}
