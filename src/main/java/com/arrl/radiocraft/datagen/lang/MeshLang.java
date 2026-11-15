package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/** Fire alarms and SCADA on the radio mesh (2026-11-10): blocks, items, tooltips, status lines and sounds. */
public final class MeshLang {

	private MeshLang() {}

	public static void add(LanguageProvider provider) {
		provider.addBlock(RadiocraftBlocks.FIRE_ALARM_BOX, "Radio Fire Alarm Box");
		provider.addBlock(RadiocraftBlocks.PULL_STATION, "Fire Alarm Pull Station");
		provider.addBlock(RadiocraftBlocks.OUTDOOR_PULL_BOX, "Outdoor Fire Alarm Pull Box");
		provider.addBlock(RadiocraftBlocks.YELLOW_PULL_STATION, "Yellow Alarm Pull Station");
		provider.addBlock(RadiocraftBlocks.WHITE_PULL_STATION, "White Alarm Pull Station");
		provider.addBlock(RadiocraftBlocks.YELLOW_OUTDOOR_PULL_BOX, "Yellow Outdoor Alarm Pull Box");
		provider.addBlock(RadiocraftBlocks.WHITE_OUTDOOR_PULL_BOX, "White Outdoor Alarm Pull Box");
		provider.addBlock(RadiocraftBlocks.PANIC_BUTTON, "Panic Button");
		provider.addBlock(RadiocraftBlocks.FIRE_BELL, "Fire Bell");
		provider.addBlock(RadiocraftBlocks.FIRE_ALARM_CONTROLLER, "Fire Alarm Controller");
		provider.addBlock(RadiocraftBlocks.SCADA_RTU, "SCADA RTU");
		provider.addBlock(RadiocraftBlocks.SCADA_MASTER, "SCADA Master");
		provider.addItem(RadiocraftItems.FIRE_ALARM_KEY, "Fire Alarm Key");
		provider.addItem(RadiocraftItems.FIRE_ALARM_REPORT, "Fire Alarm Report");

		BiConsumer<String, String> t = (key, text) -> provider.add("tooltip.radiocraft." + key, text);
		t.accept("fire_alarm_box", "Sends its pull stations' alarms to a fire alarm controller by radio, relayed box to box. Right-click for its programming panel: frequency, callsign, facility name and a name for each location (pull station or panic button) by number (no license needed; a commercial license used on it fills them in). Open it with the fire alarm key for its lamps, SILENCE, RESET and TEST. Rings bells by redstone out of its back while in alarm. Poor range on its own antenna, normal with one on its coax.");
		t.accept("panic_button", "A pull station on a wall plate: goes on a wall, floor or under a desk. Wire it to a fire alarm box with data wire (or put it touching). Press to raise the alarm; reset it with the fire alarm key. Shift + right-click shows its location number.");
		t.accept("fire_bell", "A fire alarm bell for a wall: rings while it has redstone power. A fire alarm box powers the wall behind it in alarm (SILENCE quiets it), so put bells on that wall or run redstone from it.");
		t.accept("pull_station", "Wire it to a fire alarm box with data wire (or put it touching). Right-click to pull; reset it with the fire alarm key. Shift + right-click shows its location number. Name it and pick its kind of alarm (FIRE, MEDICAL, LOCKDOWN, TORNADO...) on the box's programming panel.");
		t.accept("yellow_pull_station", "A pull station in yellow, for alarms that are not fire (pick its kind on the box's programming panel). Wire it to a fire alarm box with data wire; right-click to pull, the fire alarm key resets it.");
		t.accept("white_pull_station", "A pull station in white, for alarms that are not fire (pick its kind on the box's programming panel). Wire it to a fire alarm box with data wire; right-click to pull, the fire alarm key resets it.");
		t.accept("yellow_outdoor_pull_box", "An outdoor pull box in yellow on its post. Wire it to a fire alarm box with data wire; pick its kind of alarm on the box's programming panel.");
		t.accept("white_outdoor_pull_box", "An outdoor pull box in white on its post. Wire it to a fire alarm box with data wire; pick its kind of alarm on the box's programming panel.");
		t.accept("fire_alarm_controller", "An RC-704 receiver: hears the fire alarm boxes on its frequency, buzzes, rings bells by redstone and prints a report on paper for each alarm. Right-click for its terminal: NEXT, SILENCE, FAST and E (the menu: box list, setup). Paper loads the printer.");
		t.accept("fire_alarm_key", "Resets a pulled pull station or panic button, and opens a fire alarm box: TEST, RESET, SILENCE and DRILL inside.");
		t.accept("scada_rtu", "Reports its redstone faces to a SCADA master by radio and puts out what the master's rules say. Right-click to set its frequency, callsign and faces (OFF, IN or OUT); shift + right-click a face steps it too.");
		t.accept("scada_master", "A console two blocks wide: polls the SCADA RTUs on its frequency and runs rules on what they read; right-click for its screen (SETUP sets the frequency and callsign). Use a book and quill on it for the rules, one a line: IF 3.N >= 10 THEN 7.E = 15, COPY 3.N TO 0.U, POLL 2 (seconds an RTU, 1 to 3). Redstone comes out of its control panel half.");

		BiConsumer<String, String> m = (key, text) -> provider.add("message.radiocraft." + key, text);
		m.accept("mesh.no_frequency", "No frequency: right-click it to set one up.");
		m.accept("mesh.frequency", "%s MHz, callsign %s");
		m.accept("mesh.antenna_external", "Antenna on the coax: reaches about %s blocks to a device like it");
		m.accept("mesh.antenna_internal", "Built-in antenna: reaches about %s blocks to a device like it");
		m.accept("mesh.set", "Set to %s MHz (callsign %s)%s");
		m.accept("mesh.already", "Already on %s MHz.");
		m.accept("mesh_setup.done", "Set to %s MHz, callsign %s (address %s).");
		m.accept("mesh_setup.same", "Kept %s MHz, callsign %s (address %s).");
		m.accept("mesh_setup.bad_frequency", "Not a frequency these can use: 150 to 174 MHz or 30 to 50 MHz, outside the marine channels (156 to 162.6).");
		m.accept("mesh_setup.bad_callsign", "A callsign is 3 to 10 letters and digits.");

		m.accept("fire_alarm_box.number", "Box %s: %s");
		m.accept("fire_alarm_box.as_box", ", box %s");
		m.accept("fire_alarm_box.stations", "Pull stations wired: %s");
		m.accept("fire_alarm_box.in_alarm", "In alarm: zones %s");
		m.accept("fire_alarm_box.waiting", "Waiting for the controller's ack (%s queued)");
		m.accept("fire_alarm_box.test", "Test sent.");

		m.accept("fire_alarm_controller.boxes", "Boxes: %s (%s in alarm, %s trouble or missing)");
		m.accept("fire_alarm_controller.latest", "Latest: %s");
		m.accept("fire_alarm_controller.slips", "Reports waiting to print: %s");
		m.accept("fire_alarm_controller.head_jam", "HEAD JAM: shift + right-click to clear it");
		m.accept("fire_alarm_controller.paper_out", "PAPER OUT: right-click with paper");
		m.accept("fire_alarm_controller.paper", "Paper: %s sheets");
		m.accept("fire_alarm_controller.jam_cleared", "Printer head cleared.");
		m.accept("fire_alarm_controller.log_empty", "The log is empty.");
		m.accept("fire_alarm_controller.silenced", "Silenced: the buzzer and bells stay quiet for 5 minutes. Still in alarm until the stations are reset.");

		m.accept("pull_station.pulled", "FIRE ALARM PULLED");
		m.accept("pull_station.needs_key", "Pulled: reset it with the fire alarm key.");
		m.accept("pull_station.reset", "Pull station reset.");
		m.accept("pull_station.zone", "Zone %s");
		m.accept("pull_station.no_zone", "no zone yet (not wired to a fire alarm box)");
		m.accept("pull_station.number", "Location %s (%s)");
		m.accept("pull_station.number_named", "Location %s: %s (%s)");
		m.accept("pull_station.no_number", "No location number yet: wire it to a fire alarm box.");
		m.accept("fire_alarm_box.no_station", "Location %s is no longer on this box's wire.");
		m.accept("panic_button.pressed", "PANIC BUTTON PRESSED");
		m.accept("panic_button.needs_key", "Pressed: reset it with the fire alarm key.");
		m.accept("panic_button.reset", "Panic button reset.");

		m.accept("scada.as_rtu", ", RTU %s");
		m.accept("scada.as_master", " as the master");
		m.accept("scada.rtu_id", "RTU %s");
		m.accept("scada.no_faces", "Every face is off: shift + right-click a face to set it IN or OUT.");
		m.accept("scada.faces", "Faces: %s");
		m.accept("scada.face_set", "%s face: %s");
		m.accept("scada.face.down", "Bottom");
		m.accept("scada.face.up", "Top");
		m.accept("scada.face.north", "North");
		m.accept("scada.face.south", "South");
		m.accept("scada.face.west", "West");
		m.accept("scada.face.east", "East");
		m.accept("scada.rules", "Rules: %s, polling each RTU every %s s");
		m.accept("scada.rule_error", "Cannot read: %s");
		m.accept("scada.rules_loaded", "Loaded %s rules; polling each RTU every %s s.");
		m.accept("scada.rtu_ok", "RTU %s: %s (%s of %s polls answered)");
		m.accept("scada.rtu_fail", "RTU %s: COMM FAIL");

		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.fire_alarm", "Fire Alarm");
		s.accept("app.fire_alarm.description", "The console for a fire alarm controller cabled to the computer: its boxes, its log, acknowledge, out of service.");
		s.accept("err.fire_no_controller", "No fire alarm controller cabled to the computer");
		s.accept("fire.title", "FIRE ALARM - Radio Box Receiver");
		s.accept("fire.keys", "F2=Acknowledge  F4=Map  F5=Out of service  Del=Forget box  PgUp/Dn=Log");
		s.accept("fire.map", "Box map");
		s.accept("fire.map_unplaced", "%s box(es) not on the map (not heard on the mesh)");
		s.accept("fire.reading", "Reading the controller...");
		s.accept("fire.no_controller", "No fire alarm controller.");
		s.accept("fire.no_controller_hint", "Put one touching the computer, or run data wire to it.");
		s.accept("fire.no_frequency", "No frequency: set it up on the controller's terminal (E, 3)");
		s.accept("fire.head", "%s MHz  %s");
		s.accept("fire.state_alarm", "*** ALARM ***");
		s.accept("fire.state_trouble", "*** TROUBLE ***");
		s.accept("fire.state_silenced", "ALARM (silenced)");
		s.accept("fire.state_normal", "NORMAL");
		s.accept("fire.boxes", "Boxes");
		s.accept("fire.no_boxes", "No box has been heard yet.");
		s.accept("fire.heard", "Last heard %s min ago");
		s.accept("fire.log", "Log");
		s.accept("fire.log_empty", "Nothing logged yet.");
		s.accept("fire.paper", "Printer: %s sheets");
		s.accept("fire.paper_out", "Printer: PAPER OUT");
		s.accept("fire.head_jam", "Printer: HEAD JAM");
		s.accept("fire.slips", "(%s reports waiting)");
		s.accept("fire.forget_title", "Forget box");
		s.accept("fire.forget", "Take box %s off the list? It comes back when it is heard again.");

		s.accept("app.scada", "SCADA");
		s.accept("app.scada.description", "The HMI for a SCADA master cabled to the computer: every RTU's points live, and its rules.");
		s.accept("err.scada_no_master", "No SCADA master cabled to the computer");
		s.accept("err.scada_bad_rule", "Not a rule. Try IF 3.N >= 10 THEN 7.E = 15, COPY 3.N TO 0.U or POLL 2");
		s.accept("scada.title", "SCADA - Master Station HMI");
		s.accept("scada.keys", "Ins=Add rule  Del=Delete rule  F3=Poll speed  F4=Trends");
		s.accept("scada.reading", "Reading the master...");
		s.accept("scada.no_master", "No SCADA master.");
		s.accept("scada.no_master_hint", "Put one touching the computer, or run data wire to it.");
		s.accept("scada.no_frequency", "No frequency: the master's SETUP page");
		s.accept("scada.head", "%s MHz  %s   Poll every %s s per RTU");
		s.accept("scada.points", "Points");
		s.accept("scada.points_head", "Unit           D    U    N    S    W    E   Last reply");
		s.accept("scada.wants", "set to");
		s.accept("scada.comm_fail", "COMM FAIL");
		s.accept("scada.never", "not answered yet");
		s.accept("scada.ago", "%ss ago (%s/%s)");
		s.accept("scada.no_rtus", "No RTU on this frequency.");
		s.accept("scada.rules", "Rules");
		s.accept("scada.no_rules", "No rules. Ins to add one, or use a book and quill on the master.");
		s.accept("scada.no_power", "NO POWER: the master is dark. Run a powered power wire to it.");
		s.accept("scada.trend_title", "Trend: %s, face %s");
		s.accept("scada.trend_none", "No readings: this face is not an input, or nothing has been read yet.");
		s.accept("scada.trend_span", "%s readings over %s s, the newest %s s ago (right)");
		s.accept("scada.trend_keys", "Up/Down unit  Left/Right face  F4 back");
		s.accept("scada.add_title", "Add rule");
		s.accept("scada.add_hint", "IF 3.N >= 10 THEN 7.E = 15 / COPY 3.N TO 0.U");

		provider.add("subtitles.radiocraft.mesh_chirp", "Data burst");
		provider.add("subtitles.radiocraft.fire_alarm_buzzer", "Fire alarm buzzer");
		provider.add("subtitles.radiocraft.fire_bell", "Fire bell rings");
		provider.add("subtitles.radiocraft.fire_alarm_printer", "Report printing");
	}
}
