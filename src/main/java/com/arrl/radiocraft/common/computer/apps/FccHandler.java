package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.DirectionFinderBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The FCC monitoring program's server side (2026-11-10): the direction finder cabled to the computer (touching or on
 * its data wire), and for the frequency it is tuned to, who is licensed there. While the finder hears a signal it
 * logs it: when it started, the frequency, the verdict ({@link Verdict}) and the bearing once the finder has one. An
 * unlicensed transmission is the one to hunt.
 * <ul>
 * <li>{@code status}: {@code finder}, {@code on}, {@code frequency} (Hz), {@code band}, {@code hearing}, {@code meter},
 * {@code bearing}/{@code sigma} if it has one, {@code verdict}, {@code callsign}, {@code holder}, and {@code log}
 * (newest first: {@code time} (game ticks ago), {@code mhz}, {@code verdict}, {@code callsign}, {@code bearing}).</li>
 * <li>{@code frequency} ({@code mhz}), {@code tune} ({@code steps}): retunes the finder.</li>
 * <li>{@code clear}: empties the log.</li>
 * </ul>
 * The log lives in memory, per computer, and is lost when the server stops.
 */
public final class FccHandler implements ComputerAppHandler {

	/** What the program makes of a frequency. */
	public enum Verdict {
		/** No licensing on this server: nothing is checked. */
		NOT_CHECKED,
		/** An amateur band: the operator's own license, not a frequency grant. */
		AMATEUR,
		/** CB: no license needed. */
		CB,
		/** CB from a station transmitting through a linear amplifier: illegal (2026-11-13). */
		CB_AMPLIFIED,
		/** The marine and weather block: licensed by rule. */
		MARINE,
		/** Commercial, and a license grants it. */
		LICENSED,
		/** Commercial, and no license grants it. */
		UNLICENSED,
		/** Outside any band. */
		OUT_OF_BAND,
		/** A MURS channel: no license needed (2026-11-12). */
		MURS
	}

	/** Log entries kept per computer. */
	public static final int LOG_SIZE = 40;
	/** A signal heard again within this many ticks of the last is the same transmission. */
	public static final int SAME_TICKS = 100;

	private record Entry(long startedAt, long lastAt, float hz, Verdict verdict, String callsign, double bearing) {}

	private static final Map<GlobalPos, Deque<Entry>> LOGS = new HashMap<>();

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		DirectionFinderBlockEntity finder = DfHandler.finder(context);
		switch(action) {
			case "status" -> reply.ok(status(context, finder));
			case "clear" -> {
				LOGS.remove(GlobalPos.of(context.level().dimension(), context.pos()));
				reply.ok();
			}
			case "frequency", "tune" -> {
				if(finder == null) {
					reply.error("df_no_finder");
					return;
				}
				if(action.equals("tune")) {
					finder.tune(Math.max(-1000, Math.min(1000, data.getInt("steps"))));
					reply.ok();
				}
				else if(finder.setFrequency(data.getFloat("mhz") * 1_000_000.0F))
					reply.ok();
				else
					reply.error("df_out_of_band");
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/** What {@code hz} is, and the license granting it if any ({@code license} gets it). */
	public static Verdict verdict(MinecraftServer server, float hz, CommercialLicense[] license) {
		if(RadiocraftServerConfig.COMMERCIAL_LICENSING.get() == RadiocraftServerConfig.LicensingMode.OFF)
			return Verdict.NOT_CHECKED;
		if(!CommercialLicenseRules.needsLicense(hz)) { // Checked first: a MARS-extended ham band reaches into commercial VHF.
			if(CommercialLicenseRules.isReserved(hz))
				return Verdict.MARINE;
			if(CommercialLicenseRules.isMurs(hz))
				return Verdict.MURS;
			Band band = Band.getBand(hz);
			if(band == null)
				return Verdict.OUT_OF_BAND;
			return Band.CB.equals(band.name()) ? Verdict.CB : band.isAmateur() ? Verdict.AMATEUR : Verdict.OUT_OF_BAND;
		}
		CommercialLicense found = CommercialLicenses.of(server).covering(hz);
		license[0] = found;
		return found == null ? Verdict.UNLICENSED : Verdict.LICENSED;
	}

	private static CompoundTag status(ComputerContext context, @Nullable DirectionFinderBlockEntity finder) {
		CompoundTag out = new CompoundTag();
		out.putBoolean("finder", finder != null);
		Deque<Entry> log = LOGS.computeIfAbsent(GlobalPos.of(context.level().dimension(), context.pos()), p -> new ArrayDeque<>());
		long now = context.level().getGameTime();
		if(finder != null) {
			float hz = finder.getFrequency();
			out.putBoolean("on", finder.isOn());
			out.putFloat("frequency", hz);
			out.putString("band", DirectionFinderBlockEntity.bandOf(hz).name());
			out.putBoolean("hearing", finder.isHearing());
			out.putDouble("meter", finder.getMeter());
			if(finder.hasBearing()) {
				out.putDouble("bearing", finder.getBearing());
				out.putDouble("sigma", finder.getSigma());
			}
			CommercialLicense[] license = new CommercialLicense[1];
			Verdict verdict = verdict(context.server(), hz, license);
			if(verdict == Verdict.CB && finder.isHearing()) { // Which station it hears: an amplified one is breaking the rules.
				com.arrl.radiocraft.common.radio.TransmitterLog.Entry heard = com.arrl.radiocraft.common.radio.TransmitterLog.nearest(context.level(), finder.getBlockPos(), hz,
						5_000.0D, Double.MAX_VALUE, com.arrl.radiocraft.common.radio.TransmitterLog.KEEP_TICKS);
				if(heard != null && com.arrl.radiocraft.common.be_networks.network_objects.LinearAmplifierNetworkObject.amplifiedCb(context.level(), heard.pos()))
					verdict = Verdict.CB_AMPLIFIED;
			}
			out.putString("verdict", verdict.name());
			if(license[0] != null) {
				out.putString("callsign", license[0].callsign());
				out.putString("holder", license[0].holder());
			}
			if(finder.isOn() && finder.isHearing())
				note(log, now, hz, verdict, license[0] == null ? "" : license[0].callsign(), finder.hasBearing() ? finder.getBearing() : Double.NaN);
		}
		ListTag list = new ListTag();
		for(Entry entry : log) {
			CompoundTag e = new CompoundTag();
			e.putLong("time", now - entry.startedAt());
			e.putString("mhz", CommercialLicenseRules.mhz(entry.hz()));
			e.putString("verdict", entry.verdict().name());
			e.putString("callsign", entry.callsign());
			if(!Double.isNaN(entry.bearing()))
				e.putDouble("bearing", entry.bearing());
			list.add(e);
		}
		out.put("log", list);
		return out;
	}

	/** Logs a signal heard now: the same transmission as the newest entry (updating its bearing), or a new one. */
	private static void note(Deque<Entry> log, long now, float hz, Verdict verdict, String callsign, double bearing) {
		Entry last = log.peekFirst();
		if(last != null && last.hz() == hz && now - last.lastAt() <= SAME_TICKS) {
			log.pollFirst();
			log.addFirst(new Entry(last.startedAt(), now, hz, verdict, callsign, Double.isNaN(bearing) ? last.bearing() : bearing));
			return;
		}
		log.addFirst(new Entry(now, now, hz, verdict, callsign, bearing));
		while(log.size() > LOG_SIZE)
			log.pollLast();
	}
}
