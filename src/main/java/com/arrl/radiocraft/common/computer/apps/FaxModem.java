package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.common.blockentities.RadioFaxBlockEntity;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.data.FaxRates;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import com.arrl.radiocraft.common.radio.voice.FaxAudio;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The radio computer's fax modem (1.12 step 7): the radio fax's conversation, as {@code RadioFaxBlockEntity} holds it
 * over the radio, so the computer and the fax machines fax one another. The call ({@link TncPacket.Kind#FAX_CALL}, with
 * what is coming), the answer ("OK" or why not), then {@link FaxRates#PACKETS} numbered packets each acknowledged
 * before the next, the last carrying the document; a lost packet goes again, up to {@code fax_packet_retries} times.
 * Same packets, same timing (the fax machine's constants), same tones (the radios make them from the packets). Not the
 * microwave link: the computer faxes over the radio only.
 * <p>
 * The modem only talks; {@link Line} is the station around it: how it transmits, its callsign, whether it can take a
 * document in, and what to do with one. Server thread; nothing is saved (a fax going on when the world closes is lost,
 * as on the machine).
 */
public final class FaxModem {

	public enum Phase { IDLE, CALLING, SENDING, RECEIVING }

	/** The station the modem works for. */
	public interface Line {
		/** Sends {@code packet} through the station's radios; false if no radio took it. */
		boolean transmit(TncPacket packet);

		/** The callsign it faxes under (empty: it cannot). */
		String callsign();

		/** True if a fax to {@code to} is for this station. */
		boolean isForMe(String to);

		/** The band of the radio a fax goes out on (sets the packet timing), or null if no radio is on. */
		@Nullable
		Band band();

		/** Why {@code document} cannot be taken in now (shown to the sender, as "DISK FULL"), or null if it can. */
		@Nullable
		String refuse(ItemStack document);

		/** The document from {@code from} arrived whole (what it is: {@code label}). Returns why it was lost, or null. */
		@Nullable
		String delivered(String from, String label, ItemStack document);

		/** A line for the log (the machine's "TX W1AW: MAP" and so on). */
		void log(String line);
	}

	private final Line line;
	private Phase phase = Phase.IDLE;
	private String peer = "";
	private String label = "";
	private String status = RadioFaxBlockEntity.STATUS_READY;
	private ItemStack sending = ItemStack.EMPTY;
	private int packetTicks = 1;
	private int seq = 0;
	private int expected = 0;
	private int packetsDone = 0;
	private int retries = 0;
	private int packetRetries = 0;
	private int callTries = 0;
	private long deadline = -1L;
	private long statusUntil = -1L;
	private boolean changed = false;

	public FaxModem(Line line) {
		this.line = line;
	}

	/**
	 * Faxes {@code document} to {@code to}. Null if the call went out, else why not (an error key: busy, no callsign
	 * to fax to, to itself, no callsign of its own, no radio on).
	 */
	@Nullable
	public String send(long now, String to, ItemStack document, long callId) {
		String target = TncPacket.sanitizeCallsign(to);
		if(phase != Phase.IDLE)
			return "fax_busy";
		if(target.isEmpty() || TncPacket.isForEveryone(target))
			return "fax_no_callsign";
		if(line.callsign().isEmpty())
			return "station_no_callsign";
		if(target.equals(line.callsign()))
			return "fax_to_self";
		Band band = line.band();
		if(band == null)
			return "station_no_radio";
		sending = document.copyWithCount(1);
		peer = target;
		label = FileTransfer.describe(document);
		packetTicks = FaxRates.packetTicks(sending, band);
		packetsDone = retries = packetRetries = callTries = 0;
		phase = Phase.CALLING;
		sendCall(now, callId);
		return null;
	}

	/** Gives up whatever is going on. */
	public void cancel() {
		if(phase != Phase.IDLE)
			fail("CANCELLED");
	}

	private void sendCall(long now, long id) {
		if(!line.transmit(TncPacket.fax(id, TncPacket.Kind.FAX_CALL, line.callsign(), peer, label + "|" + packetTicks, sending))) {
			fail("NO RADIO");
			return;
		}
		deadline = now + FaxAudio.calling().size() / 2 + RadioFaxBlockEntity.ANSWER_WAIT_TICKS;
		setStatus("CALLING " + peer + (callTries > 0 ? " (" + (callTries + 1) + ")" : ""));
	}

	private void sendPacket(long now, long id) {
		boolean last = seq == FaxRates.PACKETS;
		if(!line.transmit(TncPacket.fax(id, TncPacket.Kind.FAX_PACKET, line.callsign(), peer, seq + "/" + FaxRates.PACKETS + "|" + packetTicks, last ? sending : ItemStack.EMPTY))) {
			fail("NO RADIO");
			return;
		}
		deadline = now + packetTicks + RadioFaxBlockEntity.ACK_WAIT_TICKS;
		setStatus("SENDING PACKET " + seq + "/" + FaxRates.PACKETS + (packetRetries > 0 ? " RETRY " + packetRetries : ""));
	}

	/** Every tick: timeouts. {@code id} makes a packet id if one is needed. */
	public void tick(long now, java.util.function.LongSupplier id) {
		switch(phase) {
			case IDLE -> {
				if(statusUntil >= 0L && now >= statusUntil) {
					statusUntil = -1L;
					setStatus(RadioFaxBlockEntity.STATUS_READY);
				}
			}
			case CALLING -> {
				if(now >= deadline) {
					if(++callTries < RadioFaxBlockEntity.CALL_TRIES)
						sendCall(now, id.getAsLong());
					else
						fail("NO ANSWER");
				}
			}
			case SENDING -> {
				if(now >= deadline) {
					if(packetRetries >= RadiocraftServerConfig.FAX_PACKET_RETRIES.get())
						fail("FAILED " + packetsDone + "/" + FaxRates.PACKETS);
					else {
						packetRetries++;
						retries++;
						sendPacket(now, id.getAsLong());
					}
				}
			}
			case RECEIVING -> {
				if(now >= deadline)
					fail("LOST " + peer);
			}
		}
		if(statusUntil < 0L && phase == Phase.IDLE && !status.equals(RadioFaxBlockEntity.STATUS_READY))
			statusUntil = now + STATUS_TICKS;
	}

	private static final int STATUS_TICKS = 200;

	/**
	 * A fax packet heard ({@code answerCalls}: whether this station takes calls; a fax machine on the same bus takes
	 * them instead).
	 */
	public void receive(long now, TncPacket packet, boolean answerCalls, java.util.function.LongSupplier id) {
		if(!packet.isFax() || !line.isForMe(packet.to()))
			return;
		switch(packet.kind()) {
			case FAX_CALL -> {
				if(answerCalls)
					answer(now, packet, id.getAsLong());
			}
			case FAX_ANSWER -> {
				if(phase == Phase.CALLING && packet.from().equals(peer)) {
					if(packet.text().equals(RadioFaxBlockEntity.ANSWER_OK)) {
						phase = Phase.SENDING;
						seq = 1;
						sendPacket(now, id.getAsLong());
					}
					else
						fail(peer + ": " + packet.text());
				}
			}
			case FAX_PACKET -> {
				if(phase == Phase.RECEIVING && packet.from().equals(peer))
					take(now, packet.faxSeq(), packet.item(), id.getAsLong());
			}
			case FAX_ACK -> {
				if(phase == Phase.SENDING && packet.from().equals(peer) && packet.faxSeq() == seq)
					acked(now, id.getAsLong());
			}
			default -> {}
		}
	}

	private void answer(long now, TncPacket call, long answerId) {
		String problem = phase != Phase.IDLE ? "BUSY" : call.item().isEmpty() ? "CANNOT COPY" : line.refuse(call.item());
		if(!line.transmit(TncPacket.fax(answerId, TncPacket.Kind.FAX_ANSWER, line.callsign(), call.from(), problem == null ? RadioFaxBlockEntity.ANSWER_OK : problem, ItemStack.EMPTY)))
			return;
		if(problem != null) {
			line.log("RX " + call.from() + ": " + problem);
			return;
		}
		peer = call.from();
		label = call.faxLabel();
		packetTicks = Math.max(1, call.faxTicks());
		expected = 1;
		packetsDone = retries = 0;
		phase = Phase.RECEIVING;
		deadline = now + patience();
		setStatus("RECEIVING FROM " + peer);
	}

	/** Ticks to wait for the next packet: as long as the sender keeps trying, and a little (the machine's rule). */
	private long patience() {
		return (long)(packetTicks + RadioFaxBlockEntity.ACK_WAIT_TICKS) * (RadiocraftServerConfig.FAX_PACKET_RETRIES.get() + 1) + RadioFaxBlockEntity.RECEIVE_PATIENCE_TICKS;
	}

	private void take(long now, int n, ItemStack document, long ackId) {
		if(n < 1 || n > expected)
			return; // Out of order: the sender sends it again.
		deadline = now + patience();
		if(n == expected) {
			expected++;
			packetsDone = n;
			setStatus("RECEIVING PACKET " + n + "/" + FaxRates.PACKETS);
		}
		else
			retries++; // Heard again: our ack was lost.
		line.transmit(TncPacket.fax(ackId, TncPacket.Kind.FAX_ACK, line.callsign(), peer, String.valueOf(n), ItemStack.EMPTY));
		if(n == FaxRates.PACKETS && phase == Phase.RECEIVING) {
			String lost = document.isEmpty() ? "CANNOT COPY" : line.delivered(peer, label, document);
			if(lost != null) {
				fail(lost);
				return;
			}
			line.log("RX " + peer + ": " + label);
			phase = Phase.IDLE;
			setStatus("RECEIVED " + label);
		}
	}

	private void acked(long now, long id) {
		packetsDone = seq;
		packetRetries = 0;
		if(seq >= FaxRates.PACKETS) {
			line.log("TX " + peer + ": " + label);
			sending = ItemStack.EMPTY;
			phase = Phase.IDLE;
			setStatus("SENT " + label);
		}
		else {
			seq++;
			sendPacket(now, id);
		}
	}

	private void fail(String reason) {
		line.log((phase == Phase.RECEIVING ? "RX " : "TX ") + peer + ": " + reason);
		sending = ItemStack.EMPTY;
		phase = Phase.IDLE;
		setStatus(reason);
	}

	private void setStatus(String text) {
		status = text;
		statusUntil = -1L;
		changed = true;
	}

	/** True once since the last call if what the screen shows changed. */
	public boolean takeChanged() {
		boolean was = changed;
		changed = false;
		return was;
	}

	public Phase phase() {
		return phase;
	}

	public boolean busy() {
		return phase != Phase.IDLE;
	}

	public String status() {
		return status;
	}

	public String peer() {
		return peer;
	}

	public String label() {
		return label;
	}

	public int packetsDone() {
		return packetsDone;
	}

	public int retries() {
		return retries;
	}
}
