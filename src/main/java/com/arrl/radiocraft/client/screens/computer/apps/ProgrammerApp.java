package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerResponse;
import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosApp;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.DosFilePicker;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.client.screens.computer.DosList;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.Codeplug;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.FrequencyList;
import com.arrl.radiocraft.client.screens.computer.DosPickDialog;
import com.arrl.radiocraft.common.computer.apps.HandProgramming;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialContact;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgram;
import com.arrl.radiocraft.common.radio.commercial.CommercialRadioModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CPS, the commercial radio programmer (1.12 step 6; "customer programming software", as the radio shops call it):
 * reads a commercial radio's channels (16, or 40 on the upgraded handheld in zones A-C; the list scrolls), lets them be edited, and writes them back, all or nothing, refusing what the
 * model cannot do. A placed mobile wired to the computer is read and written through the cable (F5, F6). A handheld or
 * a mobile in item form is read or written by hand: F5 or F6 (with no radio wired, or "a radio in your hand" picked)
 * asks the player to press Continue, the screen closes, and right clicking the computer holding the radio reads or
 * writes it and opens the CPS again with it (2026-11-06). For a fleet, F9 turns on the "Program radios" mode, Close leaves the screen with the
 * CPS in that mode, and right clicking the computer or its monitor holding a radio writes the codeplug into it (sneak
 * to read the radio instead; {@code HandProgramming} on the server). A key the radio marks as not readable shows as
 * {@code ****}: it never comes off the radio, and written back it stays the radio's own; a new key can be typed over
 * it. Codeplugs save as {@code .CPG} files, to load into other radios (a hidden key cannot be cloned that way: such a
 * channel is refused). Each codeplug carries a unit ID (F4; sent with every key-up) and a contact list of unit IDs
 * and aliases (F7).
 * <p>
 * F5 reads the radio (wired, or by hand), F6 writes it, Enter edits a channel, Del clears one, Ctrl+C / Ctrl+V copy a channel to another,
 * F3 opens a codeplug, Ctrl+S saves one, F8 erases the radio back to factory blank, F9 programs radios by hand.
 */
public final class ProgrammerApp extends DosApp {

	private static final int LIST_ROW = 5;
	private static final String HIDDEN = "****";

	@Nullable
	private CompoundTag radio;
	private Codeplug plug = Codeplug.blank("");
	private int knob = -1;
	private boolean modified = false;
	@Nullable
	private String fileName;
	@Nullable
	private CommercialChannel clipboard;
	private final Map<Integer, String> problems = new HashMap<>();
	private final DosList list = new DosList();
	/** The "Program radios" mode is on (the server agreed). */
	private boolean handMode = false;
	/** "read" or "write" while waiting for one radio to be right clicked on the computer (Read or Write by hand), else null. */
	@Nullable
	private String armed;
	/** Where the mode's Close button is drawn (row, first column). */
	private static final int CLOSE_ROW = 21, CLOSE_COL = 70;

	public ProgrammerApp(ComputerSession session, ComputerApp app) {
		super(session, app);
		list.onActivate(this::edit);
		rebuild();
	}

	@Override
	public void openFile(String name) {
		load(name);
	}

	// -------------------- THE CODEPLUG --------------------

	@Nullable
	private CommercialRadioModel model() {
		return CommercialRadioModel.byId(plug.model());
	}

	/** How many channel positions the codeplug's radio holds (all 40 with no model). */
	private int capacity() {
		return plug.capacity();
	}

	private void rebuild() {
		List<String> lines = new ArrayList<>();
		boolean zones = capacity() > CommercialProgram.KNOB_POSITIONS;
		for(int i = 0; i < capacity(); i++)
			lines.add(line(i, plug.channel(i), i == knob, problems.containsKey(i), zones));
		list.setItems(lines);
		if(handMode)
			sendHand(true); // The radios programmed by hand get the codeplug as it is now.
	}

	/**
	 * One channel position as a row: number, name, RX, TX, mode, tone, key and whether it may be read. On a radio with
	 * a zone switch the zone letter comes after the mark at the first channel of each zone ("A", "B", "C").
	 */
	private static String line(int position, CommercialChannel channel, boolean knob, boolean problem, boolean zones) {
		String mark = problem ? "!" : knob ? ">" : " ";
		if(zones && mark.equals(" ") && position % CommercialProgram.KNOB_POSITIONS == 0)
			mark = String.valueOf(CommercialProgram.zoneLetter(position / CommercialProgram.KNOB_POSITIONS));
		if(channel.isBlank())
			return String.format(Locale.ROOT, "%s%2d  %s", mark, position + 1, Dos.tr("cps.blank"));
		String tx = channel.isReceiveOnly() ? Dos.tr("cps.rx_only") : FrequencyList.mhz(channel.txHz() / 1e6);
		String tone = !channel.digital() && channel.ctcssTone() > 0.0F ? String.format(Locale.ROOT, "%.1f", channel.ctcssTone()) : "";
		return String.format(Locale.ROOT, "%s%2d  %-8s %10s %10s %-4s %5s  %-6s %s", mark, position + 1, channel.name(),
				FrequencyList.mhz(channel.rxHz() / 1e6), tx, channel.digital() ? "DIG" : "FM", tone, keyText(channel),
				channel.digital() && channel.keyId() != 0 ? channel.keyReadable() ? "R" : "H" : "");
	}

	private static String keyText(CommercialChannel channel) {
		if(!channel.digital() || channel.keyId() == 0)
			return "";
		return channel.keyId() == CommercialChannel.KEY_HIDDEN ? HIDDEN : String.valueOf(channel.keyId());
	}

	/** A write's or export's problems as lines: "CH 3: The transmit frequency is outside this radio's bands". */
	static String problemsText(ListTag problems) {
		StringBuilder out = new StringBuilder();
		for(int i = 0; i < problems.size(); i++) {
			CompoundTag problem = problems.getCompound(i);
			if(!out.isEmpty())
				out.append('\n');
			out.append(Dos.tr("cps.problem", problem.getInt("pos") + 1, I18n.get("message.radiocraft.commercial." + problem.getString("key"))));
		}
		return out.toString();
	}

	/** Takes in a radio's answer to read, write or factory: the radio, its channels, the knob. */
	private void took(ComputerResponse response) {
		took(response.data());
	}

	private void took(CompoundTag data) {
		radio = data.copy();
		radio.remove("channels");
		radio.remove("problems");
		radio.remove("contacts");
		plug = new Codeplug(data.getString("model"), Codeplug.channels(data.getList("channels", Tag.TAG_COMPOUND)), data.getInt("unit"), Codeplug.contacts(data));
		knob = data.getInt("selected");
		problems.clear();
		modified = false;
		rebuild();
	}

	private void load(String name) {
		session.files().read(name, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			CompoundTag tag = ComputerFileFormats.nbt(response.data().getByteArray("data"));
			if(tag == null) {
				session.message(Dos.tr("err.bad_contents"), true);
				return;
			}
			Codeplug loaded = Codeplug.fromTag(tag);
			// The codeplug is meant for the radio connected, if there is one: its model checks the channels.
			plug = radio == null ? loaded : loaded.forModel(radio.getString("model"));
			int dropped = plug.beyondCapacity();
			if(dropped > 0) // A 40-channel codeplug in a 16-channel radio: what does not fit is left out, and said so.
				plug = new Codeplug(plug.model(), plug.channels().subList(0, plug.capacity()), plug.unitId(), plug.contacts());
			fileName = ComputerStorage.normalise(name);
			problems.clear();
			modified = radio != null;
			rebuild();
			if(dropped > 0)
				session.message(Dos.tr("cps.opened_dropped", fileName, plug.programmedCount(), dropped, plug.capacity()), true);
			else
				session.message(Dos.tr("cps.opened", fileName, plug.programmedCount()), false);
		});
	}

	private void save(@Nullable Runnable after) {
		if(fileName == null) {
			showDialog(DosDialog.ask(Dos.tr("cps.save_title"), Dos.tr("cps.save_as"), DosInput.fileName(""), typed -> {
				fileName = ComputerStorage.suggest(typed, ComputerFileFormats.CODEPLUG);
				save(after);
			}));
			return;
		}
		String name = fileName;
		session.files().write(name, ComputerFileFormats.nbt(plug.toTag()), response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			session.message(Dos.tr("cps.saved", name), false);
			if(after != null)
				after.run();
		});
	}

	private void whenSafe(Runnable then) {
		if(!modified) {
			then.run();
			return;
		}
		showDialog(DosDialog.confirm(Dos.tr("cps.unsaved_title"), Dos.tr("cps.unsaved"), then));
	}

	// -------------------- THE RADIO --------------------

	private void readRadio() {
		whenSafe(() -> RadioPicker.pickOrHand(this, session, app, chosen -> {
			CompoundTag data = new CompoundTag();
			data.putString("radio", chosen.getString("id"));
			session.app(app, "read", data, response -> {
				if(!response.ok()) {
					showError(response);
					return;
				}
				took(response);
				session.message(Dos.tr("cps.read", RadioPicker.name(response.data()), plug.programmedCount()), false);
			});
		}, () -> promptHand("read")));
	}

	/** Writes the codeplug into the radio read last (or one picked now). */
	private void writeRadio() {
		if(handMode && radio != null && radio.getString("id").equals("hand")) { // Programming radios by hand already: click with it.
			session.message(Dos.tr("cps.hand_write_hint"), true);
			return;
		}
		if(radio == null || radio.getString("id").equals("hand")) { // Read by hand (or nothing read): a wired one, or one in the hand.
			RadioPicker.pickOrHand(this, session, app, chosen -> confirmWrite(chosen.getString("id"), RadioPicker.name(chosen)), () -> promptHand("write"));
			return;
		}
		confirmWrite(radio.getString("id"), RadioPicker.name(radio));
	}

	private void confirmWrite(String id, String name) {
		showDialog(DosDialog.confirm(Dos.tr("cps.write_title"), Dos.tr("cps.write", plug.programmedCount(), name), () -> {
			CompoundTag data = new CompoundTag();
			data.putString("radio", id);
			data.put("channels", Codeplug.channelsTag(plug.channels()));
			Codeplug.putIdentity(data, plug.unitId(), plug.contacts());
			session.app(app, "write", data, response -> {
				if(!response.ok()) {
					showError(response);
					return;
				}
				ListTag refused = response.data().getList("problems", Tag.TAG_COMPOUND);
				if(refused.isEmpty()) {
					took(response);
					session.message(Dos.tr("cps.written", name), false);
					return;
				}
				problems.clear();
				for(int i = 0; i < refused.size(); i++)
					problems.put(refused.getCompound(i).getInt("pos"), I18n.get("message.radiocraft.commercial." + refused.getCompound(i).getString("key")));
				rebuild();
				showDialog(DosDialog.message(Dos.tr("cps.refused_title"), Dos.tr("cps.refused") + "\n" + problemsText(refused)));
			});
		}));
	}

	private void factory() {
		if(radio == null) {
			session.message(Dos.tr("cps.read_first"), true);
			return;
		}
		String id = radio.getString("id"), name = RadioPicker.name(radio);
		if(id.equals("hand")) {
			session.message(Dos.tr("cps.hand_no_factory"), true);
			return;
		}
		showDialog(DosDialog.confirm(Dos.tr("cps.factory_title"), Dos.tr("cps.factory", name), () -> {
			CompoundTag data = new CompoundTag();
			data.putString("radio", id);
			session.app(app, "factory", data, response -> {
				if(!response.ok()) {
					showError(response);
					return;
				}
				took(response);
				session.message(Dos.tr("cps.factory_done", name), false);
			});
		}));
	}

	// -------------------- EDITING --------------------

	private void edit(int position) {
		if(position < 0 || position >= capacity())
			return;
		CommercialChannel channel = plug.channel(position);
		boolean blank = channel.isBlank();
		String rx = blank ? "" : FrequencyList.mhz(channel.rxHz() / 1e6);
		String tx = blank ? "" : channel.isReceiveOnly() ? "RX" : Math.abs(channel.txHz() - channel.rxHz()) < 1.0F ? "" : FrequencyList.mhz(channel.txHz() / 1e6);
		String tone = !blank && !channel.digital() && channel.ctcssTone() > 0.0F ? String.format(Locale.ROOT, "%.1f", channel.ctcssTone()) : "";
		String key = channel.keyId() == 0 ? "0" : keyText(channel);
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("cps.name"), new DosInput(channel.name(), CommercialChannel.NAME_MAX)),
				new DosFormDialog.Field(Dos.tr("cps.rx"), new DosInput(rx, 12, c -> c >= '0' && c <= '9' || c == '.')),
				new DosFormDialog.Field(Dos.tr("cps.tx"), new DosInput(tx, 12, c -> c >= '0' && c <= '9' || c == '.' || c == 'R' || c == 'r' || c == 'X' || c == 'x')),
				new DosFormDialog.Field(Dos.tr("cps.mode"), new DosInput(channel.digital() ? "DIG" : "FM", 3)),
				new DosFormDialog.Field(Dos.tr("cps.tone"), new DosInput(tone, 6, c -> c >= '0' && c <= '9' || c == '.')),
				new DosFormDialog.Field(Dos.tr("cps.key"), new DosInput(key, 5, c -> c >= '0' && c <= '9' || c == '*')),
				new DosFormDialog.Field(Dos.tr("cps.readable"), new DosInput(channel.keyReadable() ? "Y" : "N", 1, c -> "YyNn".indexOf(c) >= 0)));
		showDialog(new DosFormDialog(Dos.tr("cps.edit_title", position + 1), Dos.tr("cps.form_hint"), fields, values -> {
			CommercialChannel made;
			try {
				made = parse(channel, values);
			}
			catch(IllegalArgumentException e) {
				return e.getMessage();
			}
			CommercialRadioModel model = model();
			if(model != null && !made.isBlank()) {
				String problem = model.check(made.keyId() == CommercialChannel.KEY_HIDDEN ? made.withKey(1) : made);
				if(problem != null)
					return I18n.get("message.radiocraft.commercial." + problem);
			}
			plug = plug.with(position, made);
			problems.remove(position);
			modified = true;
			rebuild();
			return null;
		}).focus(blank ? 1 : 0));
	}

	/**
	 * The channel the form describes. A blank RX clears it. "****" keeps the key the radio has (it was not readable);
	 * any number replaces it.
	 *
	 * @throws IllegalArgumentException with the words to show, if a field is wrong.
	 */
	private static CommercialChannel parse(CommercialChannel old, List<String> values) {
		String rxText = values.get(1).strip();
		if(rxText.isEmpty())
			return CommercialChannel.BLANK;
		double rx = FrequencyList.parseMhz(rxText);
		if(rx <= 0.0D)
			throw new IllegalArgumentException(Dos.tr("freqman.bad_rx"));
		String txText = values.get(2).strip();
		double tx = txText.equalsIgnoreCase("RX") ? 0.0D : txText.isEmpty() ? rx : FrequencyList.parseMhz(txText);
		if(tx < 0.0D)
			throw new IllegalArgumentException(Dos.tr("freqman.bad_tx"));
		FrequencyList.Mode mode = FrequencyList.Mode.parse(values.get(3));
		if(mode == null || !mode.commercial())
			throw new IllegalArgumentException(Dos.tr("cps.bad_mode"));
		boolean digital = mode == FrequencyList.Mode.DIG;
		float tone = 0.0F;
		if(!values.get(4).isBlank()) {
			try {
				tone = Float.parseFloat(values.get(4).strip());
			}
			catch(NumberFormatException e) {
				throw new IllegalArgumentException(Dos.tr("freqman.bad_tone"));
			}
		}
		int key = 0;
		boolean readable = !values.get(6).strip().equalsIgnoreCase("N");
		String keyText = values.get(5).strip();
		if(digital && !keyText.isEmpty() && !keyText.equals("0")) {
			if(keyText.equals(HIDDEN)) {
				if(old.keyId() != CommercialChannel.KEY_HIDDEN)
					throw new IllegalArgumentException(Dos.tr("cps.bad_key"));
				key = CommercialChannel.KEY_HIDDEN;
				readable = false; // A key the programmer may not see stays that way.
			}
			else {
				try {
					key = Integer.parseInt(keyText);
				}
				catch(NumberFormatException e) {
					throw new IllegalArgumentException(Dos.tr("cps.bad_key"));
				}
				if(key < 1 || key > CommercialChannel.MAX_KEY)
					throw new IllegalArgumentException(Dos.tr("cps.bad_key"));
			}
		}
		return new CommercialChannel(values.get(0), (float)(rx * 1e6), (float)(tx * 1e6), digital, digital ? 0.0F : tone, key, readable).sanitized();
	}

	/** F4: the unit ID the codeplug writes (0: each radio keeps its own, or is given one). */
	private void editUnitId() {
		List<DosFormDialog.Field> fields = List.of(new DosFormDialog.Field(Dos.tr("cps.unit_id"),
				new DosInput(String.valueOf(plug.unitId()), 4, c -> c >= '0' && c <= '9')));
		showDialog(new DosFormDialog(Dos.tr("cps.unit_title"), Dos.tr("cps.unit_hint"), fields, values -> {
			int id;
			try {
				id = values.getFirst().isBlank() ? 0 : Integer.parseInt(values.getFirst().strip());
			}
			catch(NumberFormatException e) {
				return Dos.tr("cps.bad_unit");
			}
			if(id < 0 || id > CommercialProgram.UNIT_MAX)
				return Dos.tr("cps.bad_unit");
			plug = plug.withUnitId(id);
			modified = true;
			rebuild();
			return null;
		}));
	}

	/** F7: the contact list, unit ID to alias. Pick one to edit (a blank alias deletes it), or the last line to add one. */
	private void editContacts() {
		List<CommercialContact> contacts = plug.contacts();
		List<String> lines = new ArrayList<>();
		for(CommercialContact contact : contacts)
			lines.add(String.format(Locale.ROOT, "%4d  %s", contact.id(), contact.alias()));
		if(contacts.size() < CommercialProgram.MAX_CONTACTS)
			lines.add(Dos.tr("cps.contact_new"));
		showDialog(new DosPickDialog(Dos.tr("cps.contacts_title"), Dos.tr("cps.contacts_hint", contacts.size(), CommercialProgram.MAX_CONTACTS), lines,
				chosen -> editContact(chosen < contacts.size() ? chosen : -1)));
	}

	private void editContact(int index) {
		CommercialContact old = index >= 0 && index < plug.contacts().size() ? plug.contacts().get(index) : null;
		List<DosFormDialog.Field> fields = List.of(
				new DosFormDialog.Field(Dos.tr("cps.contact_id"), new DosInput(old == null ? "" : String.valueOf(old.id()), 4, c -> c >= '0' && c <= '9')),
				new DosFormDialog.Field(Dos.tr("cps.contact_alias"), new DosInput(old == null ? "" : old.alias(), CommercialContact.ALIAS_MAX)));
		showDialog(new DosFormDialog(Dos.tr(old == null ? "cps.contact_add_title" : "cps.contact_edit_title"), Dos.tr("cps.contact_hint"), fields, values -> {
			List<CommercialContact> out = new ArrayList<>(plug.contacts());
			if(old != null)
				out.remove(index);
			String alias = CommercialContact.sanitizeAlias(values.get(1));
			if(!alias.isEmpty()) {
				int id;
				try {
					id = Integer.parseInt(values.get(0).strip());
				}
				catch(NumberFormatException e) {
					return Dos.tr("cps.bad_unit");
				}
				if(id < 1 || id > CommercialProgram.UNIT_MAX)
					return Dos.tr("cps.bad_unit");
				out.removeIf(contact -> contact.id() == id);
				out.add(Math.min(old == null ? out.size() : index, out.size()), new CommercialContact(id, alias));
			}
			plug = plug.withContacts(out);
			modified = true;
			rebuild();
			return null;
		}).focus(old == null ? 0 : 1));
	}

	// -------------------- READ OR WRITE ONE RADIO BY HAND --------------------

	/** Asks the player to press Continue, then to right click the computer holding the radio ({@code action} "read" or "write"). */
	private void promptHand(String action) {
		String text = action.equals("read") ? Dos.tr("cps.hand_prompt.read") : Dos.tr("cps.hand_prompt.write", plug.programmedCount());
		showDialog(DosDialog.choice(Dos.tr("cps.hand_prompt_title." + action), text, new String[] { Dos.tr("button.continue"), Dos.tr("button.cancel") }, index -> {
			if(index == 0)
				arm(action);
		}));
	}

	/** Continue: the server waits for the radio (60 s), and the screen closes so the player can click with it. */
	private void arm(String action) {
		CompoundTag data = new CompoundTag();
		data.putBoolean("on", true);
		data.put("plug", plug.toTag());
		data.putString("arm", action);
		session.app(app, "hand", data, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			handMode = false; // Armed for one radio: it replaces the mode, which ends with it.
			armed = action;
			HandProgramming.clientComputer = session.pos(); // This client's click on the computer is the programming's, not the screen's.
			closeScreen();
		});
	}

	/** The armed read or write is done or gave up: this client's clicks on the computer open it again. */
	private void disarm() {
		if(armed == null)
			return;
		armed = null;
		if(!handMode && session.pos().equals(HandProgramming.clientComputer))
			HandProgramming.clientComputer = null;
	}

	// -------------------- PROGRAM RADIOS (BY HAND) --------------------

	/** F9: the "Program radios" mode on or off. */
	private void toggleHand() {
		if(handMode) {
			endHand(true);
			session.message(Dos.tr("cps.hand_off"), false);
			return;
		}
		sendHand(true);
	}

	/** Tells the server the mode is on (with the codeplug) or off. Turning on, the answer turns it on here. */
	private void sendHand(boolean on) {
		CompoundTag data = new CompoundTag();
		data.putBoolean("on", on);
		if(on)
			data.put("plug", plug.toTag());
		boolean starting = on && !handMode;
		session.app(app, "hand", data, response -> {
			if(!starting)
				return;
			if(!response.ok()) {
				showError(response);
				return;
			}
			handMode = true;
			HandProgramming.clientComputer = session.pos();
			session.message(Dos.tr("cps.hand_on"), false);
		});
	}

	/** The mode ends here (and, if {@code tell}, on the server). */
	private void endHand(boolean tell) {
		if(armed != null) {
			disarm();
			if(tell)
				sendHand(false);
		}
		if(!handMode)
			return;
		handMode = false;
		if(session.pos().equals(HandProgramming.clientComputer))
			HandProgramming.clientComputer = null;
		if(tell)
			sendHand(false);
	}

	/** Closes the computer's screen with the CPS left in front, in the mode. */
	private void closeScreen() {
		Screen screen = Minecraft.getInstance().screen;
		if(screen != null)
			screen.onClose();
	}

	@Override
	public void event(String kind, CompoundTag data) {
		switch(kind) {
			case "hand_read" -> {
				if(data.getBoolean("armed"))
					disarm();
				took(data);
				plug = plug.withUnitId(0); // Each radio keeps its own unit ID: a read one is not written into the next.
				rebuild();
				session.message(Dos.tr("cps.hand_read", RadioPicker.name(data), plug.programmedCount(), data.getInt("unit")), false);
			}
			case "hand_written" -> {
				if(data.getBoolean("armed"))
					disarm();
				problems.clear();
				if(plug.unitId() > 0) // The server moved on to the next unit ID.
					plug = plug.withUnitId(data.getInt("next"));
				rebuild();
				session.message(Dos.tr("cps.hand_written", data.getString("name"), data.getInt("unit")), false);
			}
			case "hand_refused" -> {
				if(data.getBoolean("armed"))
					disarm();
				ListTag refused = data.getList("problems", Tag.TAG_COMPOUND);
				problems.clear();
				for(int i = 0; i < refused.size(); i++)
					problems.put(refused.getCompound(i).getInt("pos"), I18n.get("message.radiocraft.commercial." + refused.getCompound(i).getString("key")));
				rebuild();
				session.message(Dos.tr("cps.hand_refused", data.getString("name")), true);
			}
			case "hand_end" -> {
				disarm();
				endHand(false);
				session.message(Dos.tr("cps.hand_end." + data.getString("reason")), true);
			}
			default -> {}
		}
	}

	@Override
	public void covered() {
		endHand(true); // Another program opened: the mode ends.
	}

	@Override
	protected void exit() {
		endHand(true);
		super.exit();
	}

	private void clear() {
		int position = list.selected();
		if(position < 0 || plug.channel(position).isBlank())
			return;
		plug = plug.with(position, CommercialChannel.BLANK);
		problems.remove(position);
		modified = true;
		rebuild();
	}

	// -------------------- DRAWING --------------------

	@Override
	public void render(TextGrid g, long now) {
		Dos.frame(g, Dos.tr("cps.title") + (fileName == null ? "" : " - " + fileName) + (modified ? "*" : ""), Dos.tr("cps.keys"));
		int fg = DosColours.BODY_FG, bg = DosColours.BODY_BG;
		if(radio == null)
			g.put(1, 1, Dos.fit(Dos.tr("cps.no_radio"), TextGrid.COLS - 2), DosColours.YELLOW, bg);
		else
			g.put(1, 1, Dos.fit(Dos.tr("cps.radio", RadioPicker.name(radio), RadioPicker.where(radio)), TextGrid.COLS - 2), DosColours.YELLOW, bg);
		CommercialRadioModel model = model();
		if(model != null) {
			String bands = radio == null ? "" : String.join(", ", com.arrl.radiocraft.common.computer.ComputerServer.strings(radio.getList("bands", Tag.TAG_STRING)));
			g.put(1, 2, Dos.fit(Dos.tr("cps.model", Dos.tr("cps.model." + model.id()), bands, yes(model.digital()), yes(model.encryption())), TextGrid.COLS - 2), fg, bg);
		}
		else
			g.put(1, 2, Dos.tr("cps.no_model"), fg, bg);
		g.put(1, 3, String.format(Locale.ROOT, " %2s  %-8s %10s %10s %-4s %5s  %-6s %s", Dos.tr("cps.col.ch"), Dos.tr("cps.col.name"), Dos.tr("cps.col.rx"),
				Dos.tr("cps.col.tx"), Dos.tr("cps.col.mode"), Dos.tr("cps.col.tone"), Dos.tr("cps.col.key"), Dos.tr("cps.col.readable")), DosColours.TEXT_FG, bg);
		g.hline(1, 4, TextGrid.COLS - 2, TextGrid.H, fg, bg);
		list.render(g, 1, LIST_ROW, TextGrid.COLS - 2, CommercialProgram.KNOB_POSITIONS, DosColours.TEXT_FG, bg, !hasDialog()); // 16 rows; 40 scroll.

		int selected = list.selected();
		String problem = problems.get(selected);
		if(armed != null) // Seen on the monitor while the screen is closed: what the computer waits for.
			g.putField(0, CLOSE_ROW, " " + Dos.fit(Dos.tr("cps.armed_banner." + armed), TextGrid.COLS - 2), TextGrid.COLS, DosColours.WHITE, DosColours.RED);
		else if(handMode) {
			g.putField(0, CLOSE_ROW, " " + Dos.fit(Dos.tr("cps.hand_banner"), CLOSE_COL - 2), CLOSE_COL, DosColours.WHITE, DosColours.RED);
			g.putField(CLOSE_COL, CLOSE_ROW, Dos.tr("cps.close_button"), TextGrid.COLS - CLOSE_COL, DosColours.BLACK, DosColours.LIGHT_GREY);
		}
		else if(problem != null)
			g.put(1, 21, Dos.fit(Dos.tr("cps.problem", selected + 1, problem), TextGrid.COLS - 2), DosColours.LIGHT_RED, bg);
		else if(knob >= 0 && capacity() > CommercialProgram.KNOB_POSITIONS)
			g.put(1, 21, Dos.tr("cps.knob_zone", knob + 1, String.valueOf(CommercialProgram.zoneLetter(knob / CommercialProgram.KNOB_POSITIONS)),
					knob % CommercialProgram.KNOB_POSITIONS + 1), fg, bg);
		else if(knob >= 0)
			g.put(1, 21, Dos.tr("cps.knob", knob + 1), fg, bg);
		g.put(1, 22, Dos.fit(Dos.tr("cps.legend"), TextGrid.COLS - 2), DosColours.DARK_GREY, bg);
		String message = session.message();
		String status = Dos.tr("cps.status", plug.programmedCount(), capacity(), plug.unitId() == 0 ? Dos.tr("cps.unit_none") : String.valueOf(plug.unitId()),
				plug.contacts().size());
		g.putField(0, 23, " " + (message.isEmpty() ? status : message), TextGrid.COLS,
				!message.isEmpty() && session.messageIsError() ? DosColours.RED : DosColours.BLACK, DosColours.LIGHT_GREY);
	}

	private static String yes(boolean value) {
		return Dos.tr(value ? "cps.yes" : "cps.no");
	}

	// -------------------- INPUT --------------------

	@Override
	protected boolean onEscape() {
		whenSafe(this::exit);
		return true;
	}

	@Override
	protected boolean onKey(int key, int scanCode, int modifiers) {
		if(Dos.isSaveKey(key)) {
			save(null);
			return true;
		}
		if(Dos.ctrl() && key == GLFW.GLFW_KEY_C && list.selected() >= 0) {
			clipboard = plug.channel(list.selected());
			session.message(Dos.tr("cps.copied", list.selected() + 1), false);
			return true;
		}
		if(Dos.ctrl() && key == GLFW.GLFW_KEY_V && list.selected() >= 0 && clipboard != null) {
			CommercialChannel pasted = clipboard;
			if(pasted.keyId() == CommercialChannel.KEY_HIDDEN) { // A hidden key belongs to its own channel: it does not copy.
				session.message(Dos.tr("cps.hidden_no_copy"), true);
				return true;
			}
			plug = plug.with(list.selected(), pasted);
			problems.remove(list.selected());
			modified = true;
			rebuild();
			return true;
		}
		switch(key) {
			case GLFW.GLFW_KEY_F5 -> readRadio();
			case GLFW.GLFW_KEY_F6 -> writeRadio();
			case GLFW.GLFW_KEY_F3 -> whenSafe(() -> showDialog(new DosFilePicker(session, Dos.tr("cps.open_title"), ComputerFileFormats.CODEPLUG, this::load)));
			case GLFW.GLFW_KEY_F8 -> factory();
			case GLFW.GLFW_KEY_F4 -> editUnitId();
			case GLFW.GLFW_KEY_F7 -> editContacts();
			case GLFW.GLFW_KEY_F9 -> toggleHand();
			case GLFW.GLFW_KEY_C -> {
				if(!handMode)
					return list.keyPressed(key);
				closeScreen();
			}
			case GLFW.GLFW_KEY_DELETE -> clear();
			default -> {
				return list.keyPressed(key);
			}
		}
		return true;
	}

	@Override
	protected boolean onClick(int col, int row, int button) {
		if(handMode && row == CLOSE_ROW && col >= CLOSE_COL) {
			closeScreen();
			return true;
		}
		return list.mouseClicked(col, row);
	}

	@Override
	protected boolean onScroll(int col, int row, double amount) {
		return list.mouseScrolled(col, row, amount);
	}
}
