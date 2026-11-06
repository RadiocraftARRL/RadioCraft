package com.arrl.radiocraft.common.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A packet mailbox (the Packet BBS program, computer apps 2): its messages and the callers talking to it, and the
 * classic BBS command set they use. Callers reach it with APRS messages, one command or line each; every answer is a list
 * of lines the server side sends back as messages, each at most {@link #LINE_MAX} characters (an APRS message's text).
 * <p>
 * Commands (any case): {@code ?} or {@code H} help; {@code L} the newest {@link #LIST_MAX} messages for the caller and the
 * bulletins; {@code LM} the caller's own; {@code LB} the bulletins; {@code R n} reads one; {@code SP call [subject]} and
 * {@code SB topic [subject]} send a personal message or a bulletin (subject asked for if missing, then body lines until
 * {@code /EX}; {@code /AB} gives up); {@code K n} kills one (its sender or addressee); {@code D n} sends a message's
 * attached QSL card to the caller as a file (computer batch 3C); {@code B} or {@code BYE} ends.
 * Callsigns match on their base (W1ABC-7 is W1ABC). Pure apart from NBT, so it is unit tested.
 */
public final class PacketBbs {

	/** Characters in one reply line: an APRS message's text ({@code TncPacket.MESSAGE_TEXT_MAX}). */
	public static final int LINE_MAX = 100;
	public static final int MAX_MESSAGES = 200;
	public static final int BODY_MAX = 1000;
	public static final int SUBJECT_MAX = 40;
	public static final int LIST_MAX = 10;
	/** A caller silent this long (game ticks, 10 minutes) is forgotten: a half-written message is dropped. */
	public static final long SESSION_TIMEOUT_TICKS = 20L * 60L * 10L;
	/** The SSIDs a BBS can take. */
	public static final int SSID_MIN = 1, SSID_MAX = 15;

	/**
	 * One message. A personal message is {@code to} a callsign; a bulletin is to a topic ({@code ALL}, {@code NET}...).
	 * {@code time} is the game time it was saved; {@code read} is set when its addressee read it.
	 */
	public record Message(int number, String from, String to, String subject, String body, long time, boolean bulletin, boolean read, CompoundTag attachment) {

		public Message(int number, String from, String to, String subject, String body, long time, boolean bulletin, boolean read) {
			this(number, from, to, subject, body, time, bulletin, read, new CompoundTag());
		}

		public Message {
			attachment = attachment == null ? new CompoundTag() : attachment;
		}

		/** True if a QSL card is attached (computer batch 3C): {@code D n} sends it to the caller. */
		public boolean hasCard() {
			return !attachment.isEmpty();
		}

		public Message withRead() {
			return new Message(number, from, to, subject, body, time, bulletin, true, attachment);
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("n", number);
			tag.putString("from", from);
			tag.putString("to", to);
			tag.putString("subject", subject);
			tag.putString("body", body);
			tag.putLong("time", time);
			tag.putBoolean("bulletin", bulletin);
			tag.putBoolean("read", read);
			if(!attachment.isEmpty())
				tag.put("qsl", attachment.copy());
			return tag;
		}

		public static Message fromTag(CompoundTag tag) {
			return new Message(tag.getInt("n"), tag.getString("from"), tag.getString("to"), tag.getString("subject"), tag.getString("body"),
					tag.getLong("time"), tag.getBoolean("bulletin"), tag.getBoolean("read"), tag.getCompound("qsl"));
		}
	}

	/** Where a caller is: at the prompt, asked for a subject, or writing a body. */
	public enum Stage { IDLE, SUBJECT, BODY }

	/** One caller talking to the BBS. */
	public static final class Session {
		final String call;
		Stage stage = Stage.IDLE;
		boolean bulletin;
		String to = "";
		String subject = "";
		final StringBuilder body = new StringBuilder();
		long lastActive;

		Session(String call, long now) {
			this.call = call;
			this.lastActive = now;
		}

		public Stage stage() {
			return stage;
		}
	}

	private final List<Message> messages = new ArrayList<>(); // Oldest first.
	private int nextNumber = 1;
	private final Map<String, Session> sessions = new LinkedHashMap<>();
	/** QSL cards callers asked for with {@code D n}, to send as files (computer batch 3C). */
	private final List<Download> downloads = new ArrayList<>();

	/** A card to send: {@code to} as it called (with its SSID), the message it is attached to. */
	public record Download(String to, Message message) {}
	private boolean modified = false;

	// -------------------- THE MAILBOX --------------------

	/** Every message, oldest first. */
	public List<Message> messages() {
		return List.copyOf(messages);
	}

	@Nullable
	public Message find(int number) {
		for(Message message : messages)
			if(message.number() == number)
				return message;
		return null;
	}

	/**
	 * Saves a QSL card that came in as a file (computer batch 3C) as a personal message from {@code from} to {@code to}
	 * with the card ({@code card}, its NBT) attached; null if the mailbox is full.
	 */
	@Nullable
	public Message postCard(String from, String to, CompoundTag card, String about, long time) {
		Message made = post(from, to, "QSL CARD DE " + base(from), about, false, time);
		if(made == null)
			return null;
		Message withCard = new Message(made.number(), made.from(), made.to(), made.subject(), made.body(), made.time(), false, false, card.copy());
		messages.set(messages.size() - 1, withCard);
		return withCard;
	}

	/** The cards asked for since the last call, emptied. */
	public List<Download> takeDownloads() {
		List<Download> out = List.copyOf(downloads);
		downloads.clear();
		return out;
	}

	/** Saves a message; null if the mailbox is full. */
	@Nullable
	public Message post(String from, String to, String subject, String body, boolean bulletin, long time) {
		if(messages.size() >= MAX_MESSAGES)
			return null;
		String cleanTo = clean(to, 12).toUpperCase(Locale.ROOT);
		String cleanSubject = clean(subject, SUBJECT_MAX);
		Message message = new Message(nextNumber, clean(from, 12).toUpperCase(Locale.ROOT), cleanTo.isEmpty() ? "ALL" : cleanTo,
				cleanSubject.isEmpty() ? "(NO SUBJECT)" : cleanSubject, body.length() > BODY_MAX ? body.substring(0, BODY_MAX) : body, time, bulletin, false);
		nextNumber = nextNumber % 99999 + 1;
		messages.add(message);
		modified = true;
		return message;
	}

	/** Removes message {@code number}; false if there is none. */
	public boolean delete(int number) {
		boolean removed = messages.removeIf(message -> message.number() == number);
		modified |= removed;
		return removed;
	}

	/** True once since the mailbox changed (the caller saves it then). */
	public boolean takeModified() {
		boolean was = modified;
		modified = false;
		return was;
	}

	/** Personal messages to {@code call} not read yet. */
	public int unreadFor(String call) {
		String base = base(call);
		int count = 0;
		for(Message message : messages)
			if(!message.bulletin() && !message.read() && base(message.to()).equals(base))
				count++;
		return count;
	}

	// -------------------- CALLERS --------------------

	/** The caller {@code call}'s session, or null. */
	@Nullable
	public Session session(String call) {
		return sessions.get(base(call));
	}

	/** Forgets callers silent since {@link #SESSION_TIMEOUT_TICKS}; returns their calls. */
	public List<String> expire(long now) {
		List<String> gone = new ArrayList<>();
		Iterator<Session> it = sessions.values().iterator();
		while(it.hasNext()) {
			Session session = it.next();
			if(now - session.lastActive > SESSION_TIMEOUT_TICKS || now < session.lastActive) {
				gone.add(session.call);
				it.remove();
			}
		}
		return gone;
	}

	/**
	 * {@code from} sent {@code line} to the BBS {@code bbsCall} at game time {@code now}: what it answers, a line per
	 * message. A first contact is greeted first.
	 */
	public List<String> handle(String bbsCall, String from, String line, long now) {
		List<String> out = new ArrayList<>();
		String caller = base(from);
		if(caller.isEmpty())
			return out;
		Session session = sessions.get(caller);
		if(session == null) {
			session = new Session(caller, now);
			sessions.put(caller, session);
			out.add(cut("[RCBBS] " + bbsCall + " MAILBOX - " + unreadFor(caller) + " MSGS FOR YOU - ? FOR HELP"));
		}
		session.lastActive = now;
		String text = line == null ? "" : line.strip();
		switch(session.stage) {
			case SUBJECT -> {
				if(text.equalsIgnoreCase("/AB")) {
					abort(session, out);
					return out;
				}
				session.subject = clean(text, SUBJECT_MAX);
				session.stage = Stage.BODY;
				out.add("ENTER MESSAGE, /EX TO END");
				return out;
			}
			case BODY -> {
				body(bbsCall, session, text, now, out);
				return out;
			}
			default -> {}
		}
		if(text.isEmpty())
			return out;
		command(bbsCall, from, session, text, now, out);
		return out;
	}

	private void body(String bbsCall, Session session, String text, long now, List<String> out) {
		if(text.equalsIgnoreCase("/AB")) {
			abort(session, out);
			return;
		}
		if(text.equalsIgnoreCase("/EX")) {
			Message saved = post(session.call, session.to, session.subject, session.body.toString().stripTrailing(), session.bulletin, now);
			session.stage = Stage.IDLE;
			session.body.setLength(0);
			out.add(saved == null ? "MAILBOX FULL - MSG NOT SAVED" : "MSG " + saved.number() + " SAVED");
			return;
		}
		if(session.body.length() + text.length() + 1 > BODY_MAX) {
			out.add("MSG TOO LONG - /EX TO SAVE OR /AB TO ABORT");
			return;
		}
		session.body.append(clean(text, LINE_MAX)).append('\n');
		// No answer to a body line: the caller keeps typing (real BBSes are silent too).
	}

	private static void abort(Session session, List<String> out) {
		session.stage = Stage.IDLE;
		session.body.setLength(0);
		out.add("MSG ABORTED");
	}

	private void command(String bbsCall, String from, Session session, String text, long now, List<String> out) {
		String[] words = text.split("\\s+", 3);
		String cmd = words[0].toUpperCase(Locale.ROOT);
		switch(cmd) {
			case "?", "H", "HELP" -> {
				out.add("CMDS: L=LIST LM=MINE LB=BULLETINS R n=READ K n=KILL B=BYE");
				out.add("SP CALL [SUBJ]=SEND MSG  SB TOPIC [SUBJ]=SEND BULLETIN  /EX ENDS, /AB ABORTS");
				out.add("D n=DOWNLOAD A QSL CARD (SENT AS A FILE TO YOUR STATION)");
			}
			case "D" -> download(session.call, from, number(words), out);
			case "L" -> list(session.call, out, m -> m.bulletin() || mine(m, session.call));
			case "LM" -> list(session.call, out, m -> !m.bulletin() && mine(m, session.call));
			case "LB" -> list(session.call, out, Message::bulletin);
			case "R" -> read(session.call, number(words), out);
			case "K" -> kill(session.call, number(words), out);
			case "SP", "SB" -> {
				if(words.length < 2) {
					out.add(cmd.equals("SP") ? "SP NEEDS A CALLSIGN: SP W1ABC" : "SB NEEDS A TOPIC: SB ALL");
					return;
				}
				if(messages.size() >= MAX_MESSAGES) {
					out.add("MAILBOX FULL - TRY LATER");
					return;
				}
				session.bulletin = cmd.equals("SB");
				session.to = clean(words[1], 12).toUpperCase(Locale.ROOT);
				session.body.setLength(0);
				if(words.length > 2 && !words[2].isBlank()) {
					session.subject = clean(words[2], SUBJECT_MAX);
					session.stage = Stage.BODY;
					out.add("ENTER MESSAGE, /EX TO END");
				}
				else {
					session.stage = Stage.SUBJECT;
					out.add("SUBJECT:");
				}
			}
			case "B", "BYE" -> {
				sessions.remove(session.call);
				out.add("73 DE " + bbsCall);
			}
			default -> out.add("?? " + cut(cmd) + " - ? FOR HELP");
		}
	}

	private void list(String caller, List<String> out, java.util.function.Predicate<Message> which) {
		int shown = 0;
		for(int i = messages.size() - 1; i >= 0 && shown < LIST_MAX; i--) {
			Message message = messages.get(i);
			if(!which.test(message))
				continue;
			out.add(listLine(message));
			shown++;
		}
		if(shown == 0)
			out.add("NO MSGS");
	}

	/** "12 P* W1ABC>W1XYZ HELLO": number, P or B, * if unread, from>to, subject. */
	public static String listLine(Message message) {
		return cut(message.number() + " " + (message.bulletin() ? "B" : "P") + (message.bulletin() || message.read() ? " " : "*") + " "
				+ message.from() + ">" + message.to() + " " + message.subject());
	}

	private void read(String caller, int number, List<String> out) {
		Message message = number < 0 ? null : find(number);
		if(message == null) {
			out.add(number < 0 ? "R NEEDS A NUMBER: R 12" : "NO MSG " + number);
			return;
		}
		if(!message.bulletin() && !mine(message, caller)) {
			out.add("MSG " + number + " IS NOT FOR YOU");
			return;
		}
		out.addAll(readLines(message));
		if(!message.bulletin() && !message.read() && base(message.to()).equals(caller)) {
			messages.set(messages.indexOf(message), message.withRead());
			modified = true;
		}
	}

	/** A message as it is read out: header, subject, the body cut to lines, and its end. */
	public static List<String> readLines(Message message) {
		List<String> out = new ArrayList<>();
		out.add("MSG " + message.number() + (message.bulletin() ? " BULLETIN " : " ") + "FROM " + message.from() + " TO " + message.to());
		out.add(cut("SUBJ: " + message.subject()));
		out.addAll(split(message.body(), LINE_MAX));
		if(message.hasCard())
			out.add("[QSL CARD ATTACHED - D " + message.number() + " TO DOWNLOAD]");
		out.add("END MSG " + message.number());
		return out;
	}

	private void download(String caller, String from, int number, List<String> out) {
		Message message = number < 0 ? null : find(number);
		if(message == null) {
			out.add(number < 0 ? "D NEEDS A NUMBER: D 12" : "NO MSG " + number);
			return;
		}
		if(!message.hasCard()) {
			out.add("MSG " + number + " HAS NO QSL CARD");
			return;
		}
		if(!mine(message, caller)) {
			out.add("MSG " + number + " IS NOT FOR YOU");
			return;
		}
		downloads.add(new Download(from, message));
		out.add("SENDING QSL CARD " + number + " TO " + cut(from));
		if(!message.read() && base(message.to()).equals(caller)) {
			messages.set(messages.indexOf(message), message.withRead());
			modified = true;
		}
	}

	private void kill(String caller, int number, List<String> out) {
		Message message = number < 0 ? null : find(number);
		if(message == null) {
			out.add(number < 0 ? "K NEEDS A NUMBER: K 12" : "NO MSG " + number);
			return;
		}
		boolean allowed = base(message.from()).equals(caller) || !message.bulletin() && base(message.to()).equals(caller);
		if(!allowed) {
			out.add("MSG " + number + " IS NOT YOURS TO KILL");
			return;
		}
		delete(number);
		out.add("MSG " + number + " KILLED");
	}

	private static boolean mine(Message message, String caller) {
		return base(message.to()).equals(caller) || base(message.from()).equals(caller);
	}

	private static int number(String[] words) {
		if(words.length < 2)
			return -1;
		try {
			return Integer.parseInt(words[1].replace("#", ""));
		}
		catch(NumberFormatException e) {
			return -1;
		}
	}

	// -------------------- TEXT --------------------

	/** A callsign without its SSID, upper case ("w1abc-7" is W1ABC). */
	public static String base(String call) {
		String upper = call == null ? "" : call.strip().toUpperCase(Locale.ROOT);
		int dash = upper.indexOf('-');
		return dash < 0 ? upper : upper.substring(0, dash);
	}

	/** The BBS's callsign: the station's base callsign with {@code -ssid}, or "" with no station callsign. */
	public static String bbsCall(String stationCall, int ssid) {
		String base = base(stationCall);
		return base.isEmpty() ? "" : base + "-" + Math.max(SSID_MIN, Math.min(SSID_MAX, ssid));
	}

	/** {@code text} as lines of at most {@code width}: its own line breaks kept, long lines broken at a space if there is one. */
	public static List<String> split(String text, int width) {
		List<String> out = new ArrayList<>();
		for(String paragraph : text.split("\n", -1)) {
			String rest = paragraph;
			while(rest.length() > width) {
				int cut = rest.lastIndexOf(' ', width);
				if(cut <= 0)
					cut = width;
				out.add(rest.substring(0, cut).stripTrailing());
				rest = rest.substring(cut).stripLeading();
			}
			out.add(rest);
		}
		while(!out.isEmpty() && out.getLast().isEmpty())
			out.removeLast();
		return out;
	}

	/** Printable ASCII only, at most {@code max}. */
	private static String clean(String text, int max) {
		StringBuilder out = new StringBuilder();
		for(char c : (text == null ? "" : text).toCharArray()) {
			if(c >= 32 && c < 127)
				out.append(c);
			if(out.length() >= max)
				break;
		}
		return out.toString().strip();
	}

	private static String cut(String line) {
		return line.length() <= LINE_MAX ? line : line.substring(0, LINE_MAX);
	}

	// -------------------- SAVING --------------------

	/** The mailbox (not the callers) as NBT, for {@code BBS.DAT}. */
	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("next", nextNumber);
		ListTag list = new ListTag();
		for(Message message : messages)
			list.add(message.toTag());
		tag.put("messages", list);
		return tag;
	}

	public static PacketBbs fromTag(@Nullable CompoundTag tag) {
		PacketBbs bbs = new PacketBbs();
		if(tag == null)
			return bbs;
		bbs.nextNumber = Math.max(1, tag.getInt("next"));
		for(Tag entry : tag.getList("messages", Tag.TAG_COMPOUND))
			if(bbs.messages.size() < MAX_MESSAGES)
				bbs.messages.add(Message.fromTag((CompoundTag)entry));
		for(Message message : bbs.messages)
			if(message.number() >= bbs.nextNumber)
				bbs.nextNumber = message.number() % 99999 + 1;
		return bbs;
	}
}
