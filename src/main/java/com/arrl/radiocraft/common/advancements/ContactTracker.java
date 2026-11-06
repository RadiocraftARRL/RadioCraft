package com.arrl.radiocraft.common.advancements;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.api.antenna.IAntennaPacket;
import com.arrl.radiocraft.common.init.RadiocraftData;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import com.arrl.radiocraft.common.radio.solar.Aurora;
import com.arrl.radiocraft.common.radio.solar.SolarEvent;
import com.arrl.radiocraft.common.radio.solar.SolarEventManager;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Who heard whom, for the advancements (the advancements batch): Is This Thing On?, Northern Lights, Numbers Station,
 * and the verified contacts of {@link ContactBook} (First Contact, Skip Day, DX Hunter, Grey Line, Into the Static,
 * Moonbounce, Contest Station).
 * <p>
 * <b>Cost.</b> {@link #heard} sits on the voice path: every receiver's moderation hook calls it for every voice frame
 * it hears (50 a second per receiver per talker), mostly on the voice chat thread. It does no lookup of any player or
 * block there: an instanceof, a hash of the receiver's identity and the talker's id into a fixed table of
 * {@link #SLOTS} slots, and {@code System.nanoTime}; only when that receiver has not reported that talker for
 * {@link #REFRESH_NANOS} (3 s) does it put one small event on a queue (at most {@link #MAX_QUEUED} waiting). So a
 * transmission costs one event per receiver every 3 seconds, not one per frame, and no allocation per frame. The server
 * thread drains the queue each tick and only then finds who was listening (the player holding the radio, or the
 * players within {@link #SPEAKER_RANGE} of a block radio), snapshots the station positions and asks the book.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class ContactTracker {

	/** As the moderation log: a player this near a block radio's speaker hears it. */
	public static final double SPEAKER_RANGE = 16.0D;
	/** A receiver reports a talker again after this long while they keep talking. */
	public static final long REFRESH_NANOS = 3_000_000_000L;
	public static final int SLOTS = 1024;
	public static final int MAX_QUEUED = 2048;
	/** Events handled per server tick at most (the rest wait for the next). */
	private static final int PER_TICK = 256;

	private static final AtomicLongArray KEYS = new AtomicLongArray(SLOTS);
	private static final AtomicLongArray TIMES = new AtomicLongArray(SLOTS);
	private static final ConcurrentLinkedQueue<Event> QUEUE = new ConcurrentLinkedQueue<>();
	private static final AtomicInteger QUEUED = new AtomicInteger();
	/** The voice ids of numbers stations on the air (a commercial radio module running the NUMBERS program). */
	private static final Set<UUID> NUMBERS = ConcurrentHashMap.newKeySet();

	private static final ContactBook BOOK = new ContactBook();

	private record Event(UUID talker, Level level, @Nullable BlockPos pos, @Nullable Player listener, float hz) {}

	private ContactTracker() {}

	// -------------------- THE VOICE PATH (any thread) --------------------

	/**
	 * {@code receiver} heard {@code packet} at {@code pos} in {@code level}; {@code listener} is the player holding it,
	 * or null for a block radio. Cheap (see the class comment); any thread.
	 */
	public static void heard(IAntennaPacket packet, Object receiver, @Nullable Level level, @Nullable BlockPos pos, @Nullable Player listener) {
		if(!(packet instanceof AntennaVoicePacket voice) || level == null || level.isClientSide() || (pos == null && listener == null))
			return;
		UUID talker = voice.getSourcePlayer();
		if(talker == null)
			return;
		long key = (System.identityHashCode(receiver) * 0x9E3779B97F4A7C15L) ^ talker.getMostSignificantBits() ^ (talker.getLeastSignificantBits() * 31L);
		int slot = (int)(key ^ (key >>> 29)) & (SLOTS - 1);
		long now = System.nanoTime();
		if(KEYS.get(slot) == key && now - TIMES.get(slot) < REFRESH_NANOS)
			return;
		KEYS.set(slot, key);
		TIMES.set(slot, now);
		if(QUEUED.incrementAndGet() > MAX_QUEUED) {
			QUEUED.decrementAndGet();
			return;
		}
		QUEUE.add(new Event(talker, level, pos, listener, voice.getFrequency()));
	}

	/** A numbers station is on the air under {@code voiceId}. */
	public static void numbersStation(UUID voiceId) {
		NUMBERS.add(voiceId);
	}

	// -------------------- THE SERVER THREAD --------------------

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if(QUEUE.isEmpty())
			return;
		MinecraftServer server = event.getServer();
		for(int i = 0; i < PER_TICK; i++) {
			Event e = QUEUE.poll();
			if(e == null)
				break;
			QUEUED.decrementAndGet();
			if(e.level() instanceof ServerLevel level && level.getServer() == server)
				hear(level, e.talker(), e.pos(), e.listener(), e.hz());
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		QUEUE.clear();
		QUEUED.set(0);
		BOOK.clear();
		NUMBERS.clear();
		for(int i = 0; i < SLOTS; i++)
			KEYS.set(i, 0L);
	}

	/**
	 * Voice from {@code talker} (a player, or a block's voice id) was heard on {@code hz} by a radio at {@code pos}
	 * (held by {@code listener}, or a block radio whose nearby players hear it). Server thread; the queue's events come
	 * here, and GameTests call it directly.
	 */
	public static void hear(ServerLevel level, UUID talker, @Nullable BlockPos pos, @Nullable Player listener, float hz) {
		List<ServerPlayer> hearers;
		if(listener instanceof ServerPlayer holding)
			hearers = List.of(holding);
		else if(pos != null)
			hearers = RadiocraftAdvancements.near(level, Sublevels.world(level, pos), SPEAKER_RANGE);
		else
			return;
		if(hearers.isEmpty())
			return;
		MinecraftServer server = level.getServer();
		boolean aurora = Aurora.isActive(level);
		boolean numbers = NUMBERS.contains(talker);
		ServerPlayer speaker = RadiocraftAdvancements.player(server, talker);
		for(ServerPlayer hearer : hearers) {
			if(aurora)
				RadiocraftAdvancements.grant(hearer, RadiocraftAdvancements.NORTHERN_LIGHTS);
			if(numbers)
				RadiocraftAdvancements.grant(hearer, RadiocraftAdvancements.NUMBERS_STATION);
			if(speaker == null || hearer == speaker)
				continue;
			RadiocraftAdvancements.grant(speaker, RadiocraftAdvancements.IS_THIS_THING_ON);
			BlockPos station = listener == hearer || pos == null ? hearer.blockPosition() : Sublevels.world(level, pos);
			heard(level, hearer.getUUID(), talker, station, hz);
		}
	}

	/**
	 * Player {@code hearer} heard player {@code talker} on {@code hz}, at a station at {@code station} in {@code level}:
	 * a voice reaching them, or an FT8 contact their computer logged. Grants what a contact this makes earns both.
	 */
	public static void heard(ServerLevel level, UUID hearer, UUID talker, BlockPos station, float hz) {
		Band band = Band.getBand(hz);
		ContactBook.Contact contact = BOOK.heard(new ContactBook.Heard(hearer, talker, level.dimension().location().toString(),
				station.getX(), station.getY(), station.getZ(), hz, band == null ? "" : band.name(), level.getGameTime()));
		if(contact == null)
			return;
		MinecraftServer server = level.getServer();
		List<String> earned = ContactBook.earned(contact, level.getDayTime(), solarStorm(level));
		for(UUID station2 : new UUID[] { contact.a(), contact.b() }) {
			for(String event : earned)
				RadiocraftAdvancements.grant(server, station2, event);
			if(contact.counts() && AdvancementData.of(server).countContact(station2) >= RadiocraftAdvancements.CONTEST_CONTACTS)
				RadiocraftAdvancements.grant(server, station2, RadiocraftAdvancements.CONTEST_STATION);
		}
	}

	/** True during a solar storm or a blackout: the two noisiest solar events (poor and blackout). */
	public static boolean solarStorm(Level level) {
		SolarEvent.SolarEventInstance current = SolarEventManager.getEvent(level);
		return current != null && RadiocraftData.SOLAR_EVENTS.conditionLevel(current.getEvent()) <= 2;
	}

	/** Hearings in the book (tests). */
	public static int booked() {
		return BOOK.size();
	}
}
