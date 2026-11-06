package com.arrl.radiocraft.common.radio.ft8;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * FT8's auto-sequencing (computer batch 3B), as WSJT-X does it: after each slot's decodes it picks what to send in our
 * next slot, so a contact runs by itself once started. Pure, unit tested; the program state saves it.
 * <pre>
 * We call CQ:                         We answer a CQ:
 *   CQ ME GRID        (CALLING_CQ)      DX ME GRID      (REPLIED)
 *   DX ME -12         (REPORT_SENT)     DX ME R-08      (R_REPORT_SENT)
 *   DX ME RR73        (RR73_SENT) *     DX ME 73        (SIGNOFF_SENT) *
 * </pre>
 * * The contact is complete (and logged) when we send RR73, or when we get RR73 and send 73. A message we sent that
 * the other station did not get is sent again when it repeats itself (our report, R-report, RR73 or 73), up to
 * {@link #MAX_TRIES} times with no answer; then the sequencer gives up and goes back to CQ (in CQ mode) or stops.
 * After a contact in CQ mode it calls CQ again.
 */
public final class Ft8Sequencer {

	public enum Step { IDLE, CALLING_CQ, REPLIED, REPORT_SENT, R_REPORT_SENT, RR73_SENT, SIGNOFF_SENT }

	/** Times one message goes out with no answer before giving up on the contact. */
	public static final int MAX_TRIES = 5;

	/** A completed contact: who, their grid, the report we gave them and the one they gave us. */
	public record Qso(String call, String grid, int reportSent, int reportReceived) {}

	private String me = "", grid = "";
	private Step step = Step.IDLE;
	private String dx = "", dxGrid = "";
	private int reportSent, reportReceived;
	private boolean cqMode;
	@Nullable
	private Ft8Message next;
	private int tries;
	/** The last station worked, so a repeat of their R-report or RR73 (they missed our answer) is answered again. */
	private String lastWorked = "";

	/** Our callsign and grid, for the messages made from now on. */
	public void station(String call, String locator) {
		me = Ft8Message.cleanCall(call);
		grid = Ft8.isGrid(locator) ? locator : "";
		if(next != null && next.type() == Ft8Message.Type.CQ)
			next = Ft8Message.cq(me, grid);
	}

	public Step step() {
		return step;
	}

	/** The station being worked, or "". */
	public String dx() {
		return dx;
	}

	/** What goes out in our next slot, or null for nothing. */
	@Nullable
	public Ft8Message next() {
		return next;
	}

	public boolean cqMode() {
		return cqMode;
	}

	public int tries() {
		return tries;
	}

	/** Starts calling CQ, and keeps calling after each contact. */
	public void callCq() {
		cqMode = true;
		toCq();
	}

	private void toCq() {
		step = Step.CALLING_CQ;
		dx = "";
		dxGrid = "";
		tries = 0;
		next = Ft8Message.cq(me, grid);
	}

	/** Answers {@code decode} (a CQ, or a station calling us): we send our grid, and our report on them is its SNR. */
	public void answer(Ft8Decode decode) {
		Ft8Message message = decode.message();
		if(message == null || message.from().isEmpty() || message.from().equals(me))
			return;
		cqMode = false;
		dx = message.from();
		dxGrid = message.grid();
		reportSent = decode.snr();
		tries = 0;
		if(message.isFor(me) && message.type() == Ft8Message.Type.REPORT) { // They already gave us a report.
			reportReceived = message.report();
			step = Step.R_REPORT_SENT;
			next = Ft8Message.rReport(dx, me, reportSent);
			return;
		}
		step = Step.REPLIED;
		next = Ft8Message.reply(dx, me, grid);
	}

	/** Stops transmitting: no CQ, no contact. */
	public void halt() {
		cqMode = false;
		step = Step.IDLE;
		dx = "";
		next = null;
		tries = 0;
	}

	/**
	 * A slot's decodes (strongest first). Returns the contact this completes, or null. Messages not for us are
	 * ignored, apart from noting a caller when we call CQ.
	 */
	@Nullable
	public Qso decoded(List<Ft8Decode> decodes) {
		acted = false;
		for(Ft8Decode decode : decodes) {
			Ft8Message message = decode.message();
			if(message == null || !message.isFor(me))
				continue;
			Qso done = take(message, decode.snr());
			if(done != null || progressed) {
				acted = true;
				return done;
			}
		}
		return null;
	}

	private boolean progressed, acted;

	/** True if the last {@link #decoded} took a message for us: what we send next answers it, in the following slot. */
	public boolean acted() {
		return acted;
	}

	@Nullable
	private Qso take(Ft8Message message, int snr) {
		progressed = false;
		String from = message.from();
		Ft8Message.Type type = message.type();
		if(!lastWorked.isEmpty() && from.equals(lastWorked) && !from.equals(dx)) { // They missed our last answer.
			if(type == Ft8Message.Type.R_REPORT && (step == Step.IDLE || step == Step.CALLING_CQ)) {
				next = Ft8Message.rr73(from, me);
				progressed = true;
				return null;
			}
			if(type == Ft8Message.Type.RR73 && (step == Step.IDLE || step == Step.CALLING_CQ)) {
				next = Ft8Message.signoff(from, me);
				progressed = true;
				return null;
			}
		}
		switch(step) {
			case CALLING_CQ -> {
				if(type == Ft8Message.Type.GRID) {
					dx = from;
					dxGrid = message.grid();
					reportSent = snr;
					step = Step.REPORT_SENT;
					next = Ft8Message.report(dx, me, reportSent);
					tries = 0;
					progressed = true;
				}
				else if(type == Ft8Message.Type.REPORT) { // A caller that skipped its grid.
					dx = from;
					dxGrid = "";
					reportSent = snr;
					reportReceived = message.report();
					step = Step.R_REPORT_SENT;
					next = Ft8Message.rReport(dx, me, reportSent);
					tries = 0;
					progressed = true;
				}
			}
			case REPLIED -> {
				if(!from.equals(dx))
					return null;
				if(type == Ft8Message.Type.REPORT) {
					reportReceived = message.report();
					step = Step.R_REPORT_SENT;
					next = Ft8Message.rReport(dx, me, reportSent);
					tries = 0;
					progressed = true;
				}
			}
			case REPORT_SENT -> {
				if(!from.equals(dx))
					return null;
				if(type == Ft8Message.Type.R_REPORT) {
					reportReceived = message.report();
					step = Step.RR73_SENT;
					next = Ft8Message.rr73(dx, me);
					tries = 0;
					progressed = true;
					return done();
				}
				if(type == Ft8Message.Type.GRID) { // Our report did not get there: it goes again.
					next = Ft8Message.report(dx, me, reportSent);
					progressed = true;
				}
			}
			case R_REPORT_SENT -> {
				if(!from.equals(dx))
					return null;
				if(type == Ft8Message.Type.RR73 || type == Ft8Message.Type.SIGNOFF) {
					step = Step.SIGNOFF_SENT;
					next = Ft8Message.signoff(dx, me);
					tries = 0;
					progressed = true;
					return done();
				}
				if(type == Ft8Message.Type.REPORT) {
					next = Ft8Message.rReport(dx, me, reportSent);
					progressed = true;
				}
			}
			case RR73_SENT -> {
				if(from.equals(dx) && type == Ft8Message.Type.R_REPORT) {
					next = Ft8Message.rr73(dx, me);
					progressed = true;
				}
			}
			case SIGNOFF_SENT -> {
				if(from.equals(dx) && type == Ft8Message.Type.RR73) {
					next = Ft8Message.signoff(dx, me);
					progressed = true;
				}
			}
			case IDLE -> {}
		}
		return null;
	}

	private Qso done() {
		lastWorked = dx;
		return new Qso(dx, dxGrid, reportSent, reportReceived);
	}

	/** Our message {@link #next()} went out. */
	public void sent() {
		tries++;
		switch(step) {
			case IDLE -> next = null; // A one-off answer to the last station worked.
			case CALLING_CQ -> next = Ft8Message.cq(me, grid);
			case RR73_SENT, SIGNOFF_SENT -> finish();
			default -> {
				if(tries >= MAX_TRIES)
					finish();
			}
		}
	}

	/** The contact is over (done, or given up): CQ again in CQ mode, else stop. */
	private void finish() {
		if(cqMode)
			toCq();
		else {
			step = Step.IDLE;
			dx = "";
			next = null;
			tries = 0;
		}
	}

	// -------------------- SAVING --------------------

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("step", step.name());
		tag.putString("dx", dx);
		tag.putString("dxGrid", dxGrid);
		tag.putInt("sent", reportSent);
		tag.putInt("rcvd", reportReceived);
		tag.putBoolean("cq", cqMode);
		tag.putInt("tries", tries);
		tag.putString("last", lastWorked);
		if(next != null)
			tag.putString("next", next.encode());
		return tag;
	}

	public void load(CompoundTag tag) {
		try {
			step = Step.valueOf(tag.getString("step"));
		}
		catch(IllegalArgumentException e) {
			step = Step.IDLE;
		}
		dx = Ft8Message.cleanCall(tag.getString("dx"));
		dxGrid = tag.getString("dxGrid");
		reportSent = tag.getInt("sent");
		reportReceived = tag.getInt("rcvd");
		cqMode = tag.getBoolean("cq");
		tries = tag.getInt("tries");
		lastWorked = Ft8Message.cleanCall(tag.getString("last"));
		next = tag.contains("next") ? Ft8Message.parse(tag.getString("next")) : null;
	}
}
