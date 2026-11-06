package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.Logbook;
import com.arrl.radiocraft.common.computer.NetLog;
import com.arrl.radiocraft.common.radio.weather.WeatherReports;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * The Net Control logger's server side (computer batch 3C). The net itself lives in a {@code .NET} file the screen
 * reads and writes; the server only writes the check-ins into the logbook when the net closes, so an open Logbook
 * reads them again ({@link LogbookHandler#append}).
 * <ul>
 * <li>{@code log} {@code net} (a {@link NetLog} tag): each check-in as a contact in {@code LOGBOOK.LOG}; answers
 * {@code logged}.</li>
 * </ul>
 */
public final class NetControlHandler implements ComputerAppHandler {

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		if(!action.equals("log")) {
			reply.error("unknown_request", action);
			return;
		}
		NetLog net = NetLog.fromTag(data.getCompound("net"));
		List<Logbook.Entry> contacts = net.asContacts(WeatherReports.timestamp(context.level().getDayTime()));
		if(contacts.isEmpty()) {
			reply.error("net_nobody");
			return;
		}
		String problem = LogbookHandler.append(context.computer(), contacts);
		if(problem != null) {
			reply.error(problem);
			return;
		}
		CompoundTag out = new CompoundTag();
		out.putInt("logged", contacts.size());
		reply.ok(out);
	}
}
