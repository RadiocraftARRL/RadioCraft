package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerNetwork;
import com.arrl.radiocraft.common.computer.ComputerReply;
import net.minecraft.core.HolderLookup;
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
 * LAN EMAIL (2026-11-13, doc/Plan-Computer4.md): mail between the users of a LAN's computers. An address is
 * {@code user@COMPUTER}: the username a player picked on a computer (the same one LAN RELAY uses, {@link RelayHandler})
 * and the computer's SETUP name. Each computer keeps its users' inboxes, saved with it.
 * <p>
 * Mail goes out through the sending computer's outbox. It is delivered at once when the other computer is on the LAN
 * and takes mail (it is running, or its background services are on with power at hand:
 * {@link RadioComputerBlockEntity#takesMail}), else it waits there and goes when it can: the sending computer keeps
 * trying while its background services are on (EMAIL is a background service, and it keeps the computer on while mail
 * waits), and a computer looking at its mail pulls what waits for it on the computers it reaches that take mail. Mail
 * still waiting after {@link #EXPIRY_TICKS} comes back to the sender.
 * <ul>
 * <li>{@code status}: {@code me}, {@code computer} (this computer's name), {@code inbox} (newest first: id, from,
 * subject, sent, read), {@code outbox} (to, subject, sent).</li>
 * <li>{@code read} ({@code id}): one message, with its body, and marks it read.</li>
 * <li>{@code send} ({@code to}, {@code subject}, {@code body}).</li>
 * <li>{@code delete} ({@code id}).</li>
 * </ul>
 */
public final class EmailHandler implements ComputerAppHandler {

	public static final int MAX_SUBJECT = 40, MAX_BODY = 600;
	/** Mailboxes one computer keeps. */
	static final int MAX_USERS = 32;
	/** Messages one user's inbox keeps (the oldest go first). */
	public static final int INBOX = 60;
	/** Messages one computer's outbox holds. */
	public static final int OUTBOX = 40;
	/** How often the sending computer tries its outbox again. */
	public static final long RETRY_TICKS = 100L;
	/** Undelivered mail comes back after three Minecraft days. */
	public static final long EXPIRY_TICKS = 72000L;
	/** Who undelivered mail comes back from. */
	public static final String DAEMON = "MAILER-DAEMON";

	/** One message. {@code to} is the full address; {@code sent} the "day HH:MM" it was sent; {@code at} game time. */
	public record Mail(long id, String from, String to, String subject, String body, String sent, long at, boolean read) {

		Mail asRead() {
			return new Mail(id, from, to, subject, body, sent, at, true);
		}

		CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putLong("id", id);
			t.putString("from", from);
			t.putString("to", to);
			t.putString("subject", subject);
			t.putString("body", body);
			t.putString("sent", sent);
			t.putLong("at", at);
			t.putBoolean("read", read);
			return t;
		}

		static Mail load(CompoundTag t) {
			return new Mail(t.getLong("id"), t.getString("from"), t.getString("to"), t.getString("subject"), t.getString("body"),
					t.getString("sent"), t.getLong("at"), t.getBoolean("read"));
		}
	}

	/** What EMAIL keeps on one computer: its users' inboxes (by username, lower case) and its outbox. */
	public static final class State implements ComputerAppState {
		final Map<String, List<Mail>> inboxes = new LinkedHashMap<>();
		final List<Mail> outbox = new ArrayList<>();
		long nextId = 1L;
		long lastTry = Long.MIN_VALUE;

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			CompoundTag boxes = new CompoundTag();
			inboxes.forEach((user, mails) -> {
				ListTag list = new ListTag();
				mails.forEach(mail -> list.add(mail.save()));
				boxes.put(user, list);
			});
			tag.put("inboxes", boxes);
			ListTag out = new ListTag();
			outbox.forEach(mail -> out.add(mail.save()));
			tag.put("outbox", out);
			tag.putLong("nextId", nextId);
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			CompoundTag boxes = tag.getCompound("inboxes");
			for(String user : boxes.getAllKeys()) {
				ListTag list = boxes.getList(user, Tag.TAG_COMPOUND);
				List<Mail> mails = new ArrayList<>();
				for(int i = 0; i < list.size(); i++)
					mails.add(Mail.load(list.getCompound(i)));
				inboxes.put(user, mails);
			}
			ListTag out = tag.getList("outbox", Tag.TAG_COMPOUND);
			for(int i = 0; i < out.size(); i++)
				outbox.add(Mail.load(out.getCompound(i)));
			nextId = Math.max(1L, tag.getLong("nextId"));
		}

		List<Mail> inbox(String user) {
			return inboxes.computeIfAbsent(user.toLowerCase(Locale.ROOT), u -> new ArrayList<>());
		}

		/** Puts {@code mail} in {@code user}'s inbox here, under a new id. */
		void deliver(String user, Mail mail) {
			String key = user.toLowerCase(Locale.ROOT);
			if(!inboxes.containsKey(key) && inboxes.size() >= MAX_USERS) // A computer keeps so many mailboxes; the oldest goes (2026-11-15).
				inboxes.remove(inboxes.keySet().iterator().next());
			List<Mail> box = inbox(user);
			box.add(new Mail(nextId++, mail.from(), mail.to(), mail.subject(), mail.body(), mail.sent(), mail.at(), false));
			while(box.size() > INBOX)
				box.removeFirst();
		}
	}

	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.EMAIL, State::new);
	}

	/** This player's username on {@code computer} (LAN RELAY's), "" for none. */
	public static String user(RadioComputerBlockEntity computer, java.util.UUID player) {
		return RelayHandler.state(computer).names.getOrDefault(player, "");
	}

	/** {@code user@COMPUTER} split in two, or null if it is not an address. */
	@Nullable
	public static String[] parse(String address) {
		int at = address.indexOf('@');
		if(at <= 0 || at != address.lastIndexOf('@') || at == address.length() - 1)
			return null;
		String user = address.substring(0, at).trim(), computer = address.substring(at + 1).trim();
		return RelayHandler.validName(user) && !computer.isEmpty() ? new String[] { user, computer } : null;
	}

	/** Strips control characters (keeping new lines in a body) and cuts to {@code max}. */
	static String clean(String text, int max, boolean lines) {
		StringBuilder out = new StringBuilder();
		for(char c : text.toCharArray())
			if(out.length() < max)
				out.append(c >= 32 && c != 127 || lines && c == '\n' ? c : ' ');
		return out.toString();
	}

	/** "Day N HH:MM", Minecraft time. */
	static String stamp(long dayTime) {
		return "D" + (dayTime / 24000L + 1L) + " " + RelayHandler.clock(dayTime);
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		RadioComputerBlockEntity computer = context.computer();
		State mine = state(computer);
		String me = user(computer, context.player().getUUID());
		if(!action.equals("status") && me.isEmpty()) {
			reply.error("relay_no_name"); // The same username as LAN RELAY's.
			return;
		}
		switch(action) {
			case "status" -> {
				collect(computer);
				reply.ok(status(computer, me));
			}
			case "read" -> {
				List<Mail> box = mine.inbox(me);
				long id = data.getLong("id");
				for(int i = 0; i < box.size(); i++)
					if(box.get(i).id() == id) {
						Mail mail = box.get(i).asRead();
						box.set(i, mail);
						computer.setChanged();
						CompoundTag out = summary(mail);
						out.putString("to", mail.to());
						out.putString("body", mail.body());
						reply.ok(out);
						return;
					}
				reply.error("email_gone");
			}
			case "delete" -> {
				long id = data.getLong("id");
				if(mine.inbox(me).removeIf(mail -> mail.id() == id))
					computer.setChanged();
				reply.ok();
			}
			case "send" -> {
				String[] to = parse(data.getString("to").trim());
				if(to == null) {
					reply.error("email_bad_address");
					return;
				}
				String here = ComputerNetwork.name(computer);
				if(here.isEmpty()) {
					reply.error("email_needs_name");
					return;
				}
				if(mine.outbox.size() >= OUTBOX) {
					reply.error("email_outbox_full");
					return;
				}
				String subject = clean(data.getString("subject").trim(), MAX_SUBJECT, false);
				Mail mail = new Mail(0L, me + "@" + here, to[0] + "@" + to[1], subject.isEmpty() ? "(no subject)" : subject,
						clean(data.getString("body"), MAX_BODY, true), stamp(context.level().getDayTime()), context.level().getGameTime(), false);
				mine.outbox.add(mail);
				computer.setChanged();
				flush(computer, context.level().getGameTime());
				CompoundTag out = new CompoundTag();
				out.putBoolean("waiting", mine.outbox.contains(mail));
				reply.ok(out);
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/**
	 * Delivers what {@code computer}'s outbox can: to itself, or to a powered computer it reaches by that name. Mail
	 * waiting past {@link #EXPIRY_TICKS} comes back to its sender's inbox here.
	 */
	public static void flush(RadioComputerBlockEntity computer, long now) {
		State mine = state(computer);
		if(mine.outbox.isEmpty())
			return;
		mine.lastTry = now;
		String here = ComputerNetwork.name(computer);
		List<ComputerNetwork.Peer> peers = null;
		boolean changed = false;
		for(Iterator<Mail> it = mine.outbox.iterator(); it.hasNext();) {
			Mail mail = it.next();
			String[] to = parse(mail.to());
			if(to == null) {
				it.remove();
				changed = true;
				continue;
			}
			RadioComputerBlockEntity target = null;
			if(ComputerNetwork.sameName(to[1], here))
				target = computer;
			else {
				if(peers == null)
					peers = ComputerNetwork.peers(computer);
				for(ComputerNetwork.Peer peer : peers)
					if(peer.link().up() && peer.computer().takesMail() && ComputerNetwork.sameName(ComputerNetwork.name(peer.computer()), to[1])) {
						target = peer.computer();
						break;
					}
			}
			if(target != null) {
				state(target).deliver(to[0], mail);
				target.setChanged();
				it.remove();
				changed = true;
			}
			else if(now - mail.at() > EXPIRY_TICKS || now < mail.at()) {
				String[] from = parse(mail.from());
				if(from != null)
					mine.deliver(from[0], new Mail(0L, DAEMON + "@" + here, mail.from(), clean("Undelivered: " + mail.subject(), MAX_SUBJECT, false),
							"Your mail to " + mail.to() + " could not be delivered: that computer was not on the LAN, or not powered.\n\n" + mail.body(),
							mail.sent(), now, false));
				it.remove();
				changed = true;
			}
		}
		if(changed)
			computer.setChanged();
	}

	/** Pulls the mail waiting for {@code computer} in the outboxes of the powered computers it reaches. */
	static void collect(RadioComputerBlockEntity computer) {
		if(ComputerNetwork.name(computer).isEmpty() || computer.getLevel() == null)
			return;
		long now = computer.getLevel().getGameTime();
		flush(computer, now);
		for(ComputerNetwork.Peer peer : ComputerNetwork.peers(computer))
			if(peer.link().up() && peer.computer().takesMail() && !state(peer.computer()).outbox.isEmpty())
				flush(peer.computer(), now);
	}

	private static CompoundTag summary(Mail mail) {
		CompoundTag t = new CompoundTag();
		t.putLong("id", mail.id());
		t.putString("from", mail.from());
		t.putString("subject", mail.subject());
		t.putString("sent", mail.sent());
		t.putBoolean("read", mail.read());
		return t;
	}

	private static CompoundTag status(RadioComputerBlockEntity computer, String me) {
		State mine = state(computer);
		CompoundTag out = new CompoundTag();
		out.putString("me", me);
		out.putString("computer", ComputerNetwork.name(computer));
		ListTag inbox = new ListTag();
		if(!me.isEmpty()) {
			List<Mail> box = mine.inbox(me);
			for(int i = box.size() - 1; i >= 0; i--)
				inbox.add(summary(box.get(i)));
		}
		out.put("inbox", inbox);
		ListTag outbox = new ListTag();
		for(Mail mail : mine.outbox)
			if(!me.isEmpty() && mail.from().toLowerCase(Locale.ROOT).startsWith(me.toLowerCase(Locale.ROOT) + "@")) {
				CompoundTag t = new CompoundTag();
				t.putString("to", mail.to());
				t.putString("subject", mail.subject());
				t.putString("sent", mail.sent());
				outbox.add(t);
			}
		out.put("outbox", outbox);
		return out;
	}

	/** Tries the outbox again now and then. */
	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		State mine = state(computer);
		if(!mine.outbox.isEmpty() && computer.isPowered() && (gameTime - mine.lastTry >= RETRY_TICKS || gameTime < mine.lastTry))
			flush(computer, gameTime);
	}

	@Override
	public boolean ticks() {
		return true;
	}

	/** Only a computer with mail waiting to go stays on for EMAIL. */
	@Override
	public boolean keepsOn(RadioComputerBlockEntity computer) {
		return !state(computer).outbox.isEmpty();
	}
}
