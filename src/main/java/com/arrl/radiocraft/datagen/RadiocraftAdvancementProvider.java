package com.arrl.radiocraft.datagen;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.advancements.RadioEventTrigger;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.advancements.critereon.RecipeCraftedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.arrl.radiocraft.common.advancements.RadiocraftAdvancements.*;

/**
 * RadioCraft's advancement tree (the advancements batch, 2026-11-06; spec {@code doc/Plan-Advancements.md}). Going Ham
 * is the root, the tab's background stone. Every advancement but the root, Shack Upgrade, Out of Band and part of
 * Ground Plane Theory is granted from server code through {@link RadioEventTrigger} (event id = advancement id). The
 * titles and descriptions live here too ({@link #addLang}), so the tree and its words are one table.
 */
public final class RadiocraftAdvancementProvider {

	private RadiocraftAdvancementProvider() {}

	public static final String ROOT = "going_ham";

	/** One advancement of the tree; {@code parent} null for the root. */
	public record Def(String id, String parent, Supplier<? extends ItemLike> icon, AdvancementType frame, boolean hidden, String title, String description) {}

	private static Supplier<? extends ItemLike> floppy(String app) {
		return () -> RadiocraftItems.floppy(ComputerApps.get(app));
	}

	private static Def task(String id, String parent, Supplier<? extends ItemLike> icon, String title, String description) {
		return new Def(id, parent, icon, AdvancementType.TASK, false, title, description);
	}

	private static Def goal(String id, String parent, Supplier<? extends ItemLike> icon, String title, String description) {
		return new Def(id, parent, icon, AdvancementType.GOAL, false, title, description);
	}

	private static Def challenge(String id, String parent, Supplier<? extends ItemLike> icon, String title, String description) {
		return new Def(id, parent, icon, AdvancementType.CHALLENGE, false, title, description);
	}

	private static Def hidden(Def def) {
		return new Def(def.id(), def.parent(), def.icon(), def.frame(), true, def.title(), def.description());
	}

	/** The tree, parents before children. */
	public static final List<Def> TREE = List.of(
			task(ROOT, null, RadiocraftItems.HF_RADIO_20M, "Going Ham",
					"You placed a radio. Remember: it needs power from a battery on wire, and an antenna on coax, before it makes a sound."),
			// Getting started.
			task(IS_THIS_THING_ON, ROOT, RadiocraftItems.VHF_HANDHELD, "Is This Thing On?",
					"Your voice reached another player's radio while they were close enough to hear it"),
			task(LICENSED_TO_CHILL, ROOT, () -> Items.NAME_TAG, "Licensed to Chill", "Get a callsign with /callsign"),
			task(GROUND_PLANE_THEORY, ROOT, RadiocraftItems.ANTENNA_WIRE, "Ground Plane Theory", "Build your first antenna"),
			goal(FIRST_CONTACT, IS_THIS_THING_ON, RadiocraftItems.HAND_MICROPHONE, "First Contact",
					"Make a two-way contact: you heard another player and they heard you within five minutes (FT8 contacts count)"),
			// Distance and propagation.
			goal(SKIP_DAY, FIRST_CONTACT, RadiocraftItems.HF_RADIO_40M, "Skip Day", "Make a contact over 1,000 blocks on HF"),
			challenge(DX_HUNTER, SKIP_DAY, RadiocraftItems.ALL_BAND_RADIO, "DX Hunter", "Make a contact over 5,000 blocks"),
			goal(GREY_LINE, FIRST_CONTACT, () -> Items.CLOCK, "Grey Line", "Make a contact at dawn or dusk"),
			goal(INTO_THE_STATIC, FIRST_CONTACT, RadiocraftItems.IONOSONDE, "Into the Static", "Make a contact during a solar storm or a radio blackout"),
			hidden(challenge(MOONBOUNCE, FIRST_CONTACT, () -> RadiocraftItems.SATELLITE_DISH.get(), "Moonbounce", "Make a contact with both stations above Y 200")),
			challenge(CONTEST_STATION, FIRST_CONTACT, floppy("logbook"), "Contest Station",
					"Make 100 two-way contacts (each station counts once a band a day)"),
			goal(NORTHERN_LIGHTS, ROOT, () -> Items.LIME_STAINED_GLASS, "Northern Lights", "Hear a signal while an aurora is in the sky"),
			// Modes.
			task(DIT_DAH, ROOT, RadiocraftItems.QRP_RADIO_40M, "Dit Dah", "Send Morse code (CW)"),
			task(TYPEWRITER_THUNDER, DIT_DAH, RadiocraftItems.TELETYPE, "Typewriter Thunder", "Copy RTTY on a teletype or the computer's RTTY program"),
			task(CHART_TOPPER, TYPEWRITER_THUNDER, RadiocraftItems.WEFAX_RECEIVER, "Chart Topper", "Print a WEFAX chart"),
			goal(PACKET_RAT, TYPEWRITER_THUNDER, RadiocraftItems.APRS_TERMINAL, "Packet Rat", "Post a message on another station's Packet BBS over the air"),
			// Station building.
			goal(REPEATER_OWNER, GROUND_PLANE_THEORY, RadiocraftItems.VHF_REPEATER, "Repeater Owner", "Another player's voice went out through a repeater you placed"),
			task(SHACK_UPGRADE, ROOT, RadiocraftItems.RADIO_COMPUTER, "Shack Upgrade", "Get a radio computer"),
			goal(MISSION_CONTROL, SHACK_UPGRADE, RadiocraftItems.COMPUTER_MONITOR, "Mission Control",
					"Sit at a radio computer with a monitor, a scanner, a radio interface and speakers"),
			goal(DIGITAL_NATIVE, SHACK_UPGRADE, RadiocraftItems.FLOPPY_DISK, "Digital Native", "Install 10 programs on one radio computer"),
			goal(WEAK_SIGNAL_WIZARD, SHACK_UPGRADE, floppy("ft8"), "Weak Signal Wizard", "Complete an FT8 contact"),
			task(PICTURE_THIS, SHACK_UPGRADE, floppy("sstv"), "Picture This", "Receive an SSTV picture"),
			// Commercial and public service.
			task(FLEET_MANAGER, SHACK_UPGRADE, RadiocraftItems.BASIC_COMMERCIAL_HANDHELD, "Fleet Manager", "Program a radio by hand with the CPS"),
			task(CLONE_WARS, FLEET_MANAGER, RadiocraftItems.RADIO_CLONER, "Clone Wars", "Read or write a radio with the Radio Cloner"),
			hidden(goal(OUT_OF_BAND, FLEET_MANAGER, RadiocraftItems.MARS_MOD, "Out of Band", "Fit a MARS mod to a radio")),
			goal(STORM_SPOTTER, ROOT, RadiocraftItems.WEATHER_ALERT_GENERATOR, "Storm Spotter", "Your Weather Alert Generator sent its first warning"),
			challenge(TAKE_SHELTER_NOW, STORM_SPOTTER, RadiocraftItems.WEATHER_ALERT_RECEIVER, "Take Shelter Now",
					"Be near a weather alert receiver that sounds a tornado warning, then see the tornado come within 100 blocks of it"),
			// Logging and community.
			goal(NET_CONTROL, SHACK_UPGRADE, floppy("netlog"), "Net Control", "Run a net with 5 or more check-ins and log it"),
			task(PAPER_TRAIL, SHACK_UPGRADE, RadiocraftItems.QSL_CARD, "Paper Trail", "Another player picked up or holds a QSL card you signed"),
			goal(CARD_COLLECTOR, PAPER_TRAIL, RadiocraftItems.QSL_ALBUM, "Card Collector", "Fill a QSL album with 20 cards"),
			challenge(WORKED_ALL_BIOMES, SHACK_UPGRADE, () -> Items.FILLED_MAP, "Worked All Biomes", "Earn the Awards program's Worked All Biomes award"),
			// Secret and fun.
			hidden(task(BIBLE_STUDY, SHACK_UPGRADE, floppy("bible"), "Bible Study", "Open the Bible on a radio computer")),
			hidden(task(HIGH_SCORE, SHACK_UPGRADE, floppy("falling_blocks"), "High Score", "Get on PILEUP's high score table")),
			hidden(task(SPIN_THE_DISK, SHACK_UPGRADE, floppy("music"), "Spin the Disk", "Play a tune in MUSIC")),
			hidden(goal(NUMBERS_STATION, ROOT, RadiocraftItems.COMMERCIAL_RADIO_MODULE, "Numbers Station", "Hear a numbers station")),
			hidden(task(FCC_IS_WATCHING, ROOT, RadiocraftItems.FCC_BADGE, "The FCC Is Watching", "Be given an FCC badge")));

	public static AdvancementProvider create(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, ExistingFileHelper files) {
		return new AdvancementProvider(output, registries, files, List.of(RadiocraftAdvancementProvider::generate));
	}

	private static void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper files) {
		Map<String, AdvancementHolder> made = new HashMap<>();
		for(Def def : TREE) {
			Advancement.Builder builder = Advancement.Builder.advancement();
			if(def.parent() != null)
				builder.parent(made.get(def.parent()));
			ResourceLocation background = def.parent() == null ? ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png") : null;
			builder.display(new ItemStack(def.icon().get()), Component.translatable(titleKey(def.id())), Component.translatable(descriptionKey(def.id())),
					background, def.frame(), true, def.parent() != null, def.hidden());
			criteria(def.id(), builder);
			made.put(def.id(), builder.save(saver, Radiocraft.id(def.id()).toString()));
		}
		// The Handbook's secret programs page unlocks with it; no display (not in the tree).
		Advancement.Builder.advancement()
				.addCriterion("has_secret_disk", InventoryChangeTrigger.TriggerInstance.hasItems(
						RadiocraftItems.floppy(ComputerApps.SNAKE), RadiocraftItems.floppy(ComputerApps.FALLING_BLOCKS),
						RadiocraftItems.floppy(ComputerApps.MUSIC), RadiocraftItems.floppy(ComputerApps.BIBLE)))
				.save(saver, Radiocraft.id("found_secret_floppy").toString());
	}

	/** Each advancement's criteria: vanilla ones where they fit, else the event of its own id. */
	private static void criteria(String id, Advancement.Builder builder) {
		switch(id) {
			case ROOT -> builder.addCriterion("placed_radio", ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(LocationCheck.checkLocation(LocationPredicate.Builder.location()
					.setBlock(BlockPredicate.Builder.block().of(radios())))));
			case SHACK_UPGRADE -> builder.addCriterion("has_computer", InventoryChangeTrigger.TriggerInstance.hasItems(RadiocraftItems.RADIO_COMPUTER.get()));
			case OUT_OF_BAND -> builder.addCriterion("fitted", RecipeCraftedTrigger.TriggerInstance.craftedItem(Radiocraft.id("mars_mod_install")));
			case GROUND_PLANE_THEORY -> {
				builder.addCriterion("built", RadioEventTrigger.TriggerInstance.of(id));
				builder.addCriterion("placed_antenna", ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(LocationCheck.checkLocation(LocationPredicate.Builder.location()
						.setBlock(BlockPredicate.Builder.block().of(antennas())))));
				builder.requirements(AdvancementRequirements.Strategy.OR);
			}
			default -> builder.addCriterion("done", RadioEventTrigger.TriggerInstance.of(id));
		}
	}

	private static Block[] radios() {
		java.util.List<Block> out = new java.util.ArrayList<>();
		java.util.stream.Stream.of(RadiocraftBlocks.HF_RADIO_10M_VARIANTS, RadiocraftBlocks.HF_RADIO_20M_VARIANTS, RadiocraftBlocks.HF_RADIO_40M_VARIANTS,
				RadiocraftBlocks.HF_RADIO_80M_VARIANTS, RadiocraftBlocks.VHF_BASE_STATION_VARIANTS).forEach(variants -> variants.values().forEach(out::add));
		out.addAll(List.of(RadiocraftBlocks.ALL_BAND_RADIO.get(), RadiocraftBlocks.QRP_RADIO_20M.get(), RadiocraftBlocks.QRP_RADIO_40M.get(), RadiocraftBlocks.HF_RECEIVER.get(),
				RadiocraftBlocks.VHF_RECEIVER.get()));
		return out.toArray(new Block[0]);
	}

	/** Antennas that are whole as placed (a wire antenna is built on the wires, and granted from its block entity). */
	private static Block[] antennas() {
		return new Block[] { RadiocraftBlocks.J_POLE_ANTENNA.get(), RadiocraftBlocks.SLIM_JIM_ANTENNA.get(), RadiocraftBlocks.YAGI_ANTENNA.get(),
				RadiocraftBlocks.RINGO_ANTENNA.get(), RadiocraftBlocks.FOLDED_DIPOLE_ANTENNA.get(), RadiocraftBlocks.FIVE_BTV_ANTENNA.get(),
				RadiocraftBlocks.SATELLITE_DISH.get(), RadiocraftBlocks.MICROWAVE_HORN.get(), RadiocraftBlocks.ADSB_ANTENNA.get() };
	}

	public static String titleKey(String id) {
		return "advancements.radiocraft." + id + ".title";
	}

	public static String descriptionKey(String id) {
		return "advancements.radiocraft." + id + ".description";
	}

	/** The titles and descriptions, for the language provider. */
	public static void addLang(LanguageProvider provider) {
		for(Def def : TREE) {
			provider.add(titleKey(def.id()), def.title());
			provider.add(descriptionKey(def.id()), def.description());
		}
	}
}
