package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import net.minecraft.nbt.CompoundTag;

/**
 * The radio programmer's server side (CPS, 1.12 step 6), on {@link CommercialProgramming}: the radios the computer
 * reaches ({@link CommercialTargets}), reading and writing their channels (16, or 40 on the upgraded handheld), and a factory reset. Keys a radio marks as
 * not readable never leave the server: they come back as {@code KEY_HIDDEN} and, written back, keep the radio's key.
 * <ul>
 * <li>{@code radios}: {@code radios}, the radios on the glass and on the wire.</li>
 * <li>{@code read} ({@code radio}): the radio, its {@code channels} and {@code selected} knob position.</li>
 * <li>{@code write} ({@code radio}, {@code channels}): all or nothing; the answer is the radio read back with
 * {@code problems} (empty when written).</li>
 * <li>{@code factory} ({@code radio}): back to the factory channels; answers like {@code read}.</li>
 * <li>{@code hand} ({@code on}, {@code plug}): the "Program radios" mode on (with the codeplug to write) or off; see
 * {@link HandProgramming}.</li>
 * </ul>
 */
public final class ProgrammerHandler implements ComputerAppHandler {

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		if(action.equals("hand")) { // The "Program radios" mode (1.12 playtest fixes 2).
			HandProgramming.request(context, data, reply);
			return;
		}
		if(action.equals("radios")) {
			reply.ok(CommercialTargets.radios(context));
			return;
		}
		CommercialTargets.Found radio = CommercialTargets.find(context, data.getString("radio"));
		if(radio == null) {
			reply.error(data.getString("radio").isEmpty() ? "no_radio" : "radio_gone");
			return;
		}
		switch(action) {
			case "read" -> reply.ok(CommercialTargets.read(radio));
			case "write" -> reply.ok(CommercialTargets.write(radio, CommercialTargets.channels(data), data.getInt("unit"),
					data.contains("contacts") ? com.arrl.radiocraft.common.computer.Codeplug.contacts(data) : null));
			case "factory" -> {
				CommercialProgramming.factoryReset(radio.target());
				radio.saved().run();
				reply.ok(CommercialTargets.read(radio));
			}
			default -> reply.error("unknown_request", action);
		}
	}
}
