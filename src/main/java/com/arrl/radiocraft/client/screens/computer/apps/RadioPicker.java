package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.common.computer.ComputerApp;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Finds the commercial radio to work on for the radio programmer and the frequency manager (1.12 step 6): asks the
 * program's server side for the radios the computer reaches ({@code radios}: mobiles on its data wire), and
 * hands the one to use to {@code chosen}: at once if there is one, from a list if there are several. With none it
 * says how to connect one.
 */
final class RadioPicker {

	private RadioPicker() {}

	/** A radio's name as the screen shows it: its anvil name, else its item or block name. */
	static String name(CompoundTag radio) {
		String custom = radio.getString("custom");
		return custom.isEmpty() ? I18n.get(radio.getString("nameKey")) : custom;
	}

	/** Where it is: "in your hand" (read in the "Program radios" mode), or "wired, at x y z". */
	static String where(CompoundTag radio) {
		String where = radio.getString("where");
		return where.equals("hand") ? Dos.tr("radio.in_hand") : Dos.tr("radio.wired", where);
	}

	/**
	 * As {@link #pick}, with a radio held in the player's hand as one more choice (the CPS, 2026-11-06): with no radio
	 * wired, {@code hand} runs at once; with some, "a radio in your hand" heads the list.
	 */
	static void pickOrHand(DosApp owner, ComputerSession session, ComputerApp app, Consumer<CompoundTag> chosen, Runnable hand) {
		session.app(app, "radios", new CompoundTag(), response -> {
			if(!response.ok()) {
				session.message(response.errorText(), true);
				return;
			}
			ListTag radios = response.data().getList("radios", Tag.TAG_COMPOUND);
			if(radios.isEmpty()) {
				hand.run();
				return;
			}
			List<String> lines = new ArrayList<>();
			lines.add(Dos.tr("radio.hand_choice"));
			for(int i = 0; i < radios.size(); i++)
				lines.add(Dos.fit(name(radios.getCompound(i)) + " - " + where(radios.getCompound(i)), 70));
			owner.showDialog(new DosPickDialog(Dos.tr("radio.pick_title"), Dos.tr("radio.pick_or_hand"), lines, index -> {
				if(index == 0)
					hand.run();
				else
					chosen.accept(radios.getCompound(index - 1));
			}));
		});
	}

	static void pick(DosApp owner, ComputerSession session, ComputerApp app, Consumer<CompoundTag> chosen) {
		session.app(app, "radios", new CompoundTag(), response -> {
			if(!response.ok()) {
				session.message(response.errorText(), true);
				return;
			}
			ListTag radios = response.data().getList("radios", Tag.TAG_COMPOUND);
			if(radios.isEmpty()) {
				session.message(Dos.tr("radio.none"), true);
				return;
			}
			if(radios.size() == 1) {
				chosen.accept(radios.getCompound(0));
				return;
			}
			List<String> lines = new ArrayList<>();
			for(int i = 0; i < radios.size(); i++)
				lines.add(Dos.fit(name(radios.getCompound(i)) + " - " + where(radios.getCompound(i)), 70));
			owner.showDialog(new DosPickDialog(Dos.tr("radio.pick_title"), Dos.tr("radio.pick"), lines, index -> chosen.accept(radios.getCompound(index))));
		});
	}
}
