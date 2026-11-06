package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.computer.Codeplug;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.radio.commercial.CommercialChannel;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * The frequency manager's server side (1.12 step 6): sending saved channels into a commercial radio, and reading a
 * radio's channels into the list. The radio's other channels (and every key on them, readable or not) are kept: only
 * the knob positions sent to are written, and those carry no key.
 * <ul>
 * <li>{@code radios}: as the programmer's ({@link CommercialTargets#radios}).</li>
 * <li>{@code read} ({@code radio}): the radio's channels, keys hidden.</li>
 * <li>{@code export} ({@code radio}, {@code start} (0-based knob position), {@code channels}): writes the channels
 * from {@code start} on; answers {@code written} (how many) and {@code problems} (nothing is written if any).</li>
 * </ul>
 */
public final class FrequencyManagerHandler implements ComputerAppHandler {

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
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
			case "export" -> reply.ok(export(radio, data.getInt("start"), Codeplug.channels(data.getList("channels", Tag.TAG_COMPOUND))));
			default -> reply.error("unknown_request", action);
		}
	}

	/**
	 * Writes {@code sent} into {@code radio} from channel position {@code start}, everything else as it was. Channels
	 * past the radio's last position (16, or 64 on the upgraded handheld) are a problem ("too_many_channels"), as is
	 * anything the radio's model cannot do.
	 */
	public static CompoundTag export(CommercialTargets.Found radio, int start, List<CommercialChannel> sent) {
		List<CommercialChannel> channels = new ArrayList<>(CommercialProgramming.read(radio.target()));
		int capacity = channels.size(); // The radio's own: read gives every position it holds.
		CompoundTag out;
		if(start < 0 || start >= capacity || start + sent.size() > capacity || sent.isEmpty()) {
			out = CommercialTargets.read(radio);
			CompoundTag problem = new CompoundTag();
			problem.putInt("pos", Math.max(0, Math.min(capacity, start + sent.size()) - 1));
			problem.putString("key", sent.isEmpty() ? "nothing_to_send" : "too_many_channels");
			ListTag problems = new ListTag();
			problems.add(problem);
			out.put("problems", problems);
			out.putInt("written", 0);
			return out;
		}
		for(int i = 0; i < sent.size(); i++) {
			CommercialChannel channel = sent.get(i);
			channels.set(start + i, channel.isBlank() ? CommercialChannel.BLANK : channel.withKey(0)); // A frequency list holds no keys.
		}
		out = CommercialTargets.write(radio, channels);
		out.putInt("written", out.getList("problems", Tag.TAG_COMPOUND).isEmpty() ? sent.size() : 0);
		return out;
	}
}
