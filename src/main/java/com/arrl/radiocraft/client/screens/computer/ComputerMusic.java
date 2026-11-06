package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.MusicShuffle;
import com.arrl.radiocraft.common.computer.MusicTracks;
import com.arrl.radiocraft.common.network.serverbound.SStationPacket;
import com.arrl.radiocraft.common.radio.BEVoiceReceiver;
import com.arrl.radiocraft.common.radio.voice.SpeakerKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The MUSIC program's player (1.12 playtest): what is playing, on which computer, kept apart from the program's page
 * so the tune plays on while the player is in another program, at the shell, or has closed the screen. One tune at a
 * time for the whole client ({@link ComputerMidi}). Client only and heard only by this player. Repeat and shuffle are
 * the client's (they last until the game is closed); shuffle plays the tunes in a shuffled order, every one once
 * before any comes again, never the same twice in a row, and goes on round ({@link MusicShuffle}); without it the
 * list plays down once.
 * <p>
 * While it plays it is tied to its computer: the sound comes from the computer, or from the speakers connected to it
 * (positional sounds fading with distance, see {@link ComputerMidi} and {@link #outlets}), and it stops when the player
 * goes out of earshot of all of them, changes dimension or logs out, or the computer is broken or loses power. The computer keeps drawing power while a
 * player listens (the server is told with the {@code music} request, see {@link RadioComputerBlockEntity#listen}), and
 * tells the client its power with the block's update.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID, value = Dist.CLIENT)
public final class ComputerMusic {

	/** Silent and stopped this far from it, blocks (the server forgets a listener past it too). */
	public static final double MAX_RANGE = RadioComputerBlockEntity.MUSIC_RANGE;

	@Nullable
	private static List<MusicTracks.Track> tracks;
	/** The computer it plays on, while a tune is loaded. */
	@Nullable
	private static BlockPos pos;
	@Nullable
	private static ClientLevel level;
	/** The track loaded, or -1. */
	private static int playing = -1;
	private static boolean paused;
	private static boolean repeat;
	private static boolean shuffle;
	private static final MusicShuffle SHUFFLE = new MusicShuffle();
	/** Steps of MUSIC's own volume control (- and +): 10 is full, each step 10 %. */
	public static final int VOLUME_STEPS = 10;
	/** MUSIC's volume, 0 to {@link #VOLUME_STEPS}: the client's, kept until the game is closed, on top of the game's sliders. */
	private static int volume = VOLUME_STEPS;
	private static String problem = "";

	private ComputerMusic() {}

	// -------------------- WHAT IT KNOWS --------------------

	/** The tunes ({@code assets/radiocraft/computer/music/tracks.txt}), read once. */
	public static List<MusicTracks.Track> tracks() {
		if(tracks == null) {
			byte[] bytes = resource("tracks.txt");
			tracks = bytes == null ? List.of() : MusicTracks.parse(new String(bytes, StandardCharsets.UTF_8));
		}
		return tracks;
	}

	/** The track loaded (playing or paused), or -1. */
	public static int playing() {
		return playing;
	}

	public static boolean paused() {
		return paused;
	}

	public static boolean repeat() {
		return repeat;
	}

	public static boolean shuffle() {
		return shuffle;
	}

	/** The file of the last tune that could not be found ("" if it was). */
	public static String problem() {
		return problem;
	}

	/** True while a tune is loaded on the computer at {@code at}. */
	public static boolean isPlayingAt(BlockPos at) {
		return playing >= 0 && at.equals(pos);
	}

	// -------------------- CONTROLS --------------------

	/** Plays track {@code index} on the computer at {@code at} (moving the music there if it was on another). */
	public static void play(BlockPos at, int index) {
		play(at, index, true);
	}

	/**
	 * The next tune on the computer at {@code at}: the next in the shuffle, or down the list (round to the top), or
	 * {@code fallback} (the tune picked) if nothing is playing there and shuffle is off.
	 */
	public static void next(BlockPos at, int fallback) {
		int current = isPlayingAt(at) ? playing : -1;
		int size = tracks().size();
		if(shuffle)
			play(at, SHUFFLE.next(current, size, ThreadLocalRandom.current()));
		else
			play(at, current < 0 ? fallback : (current + 1) % Math.max(1, size));
	}

	/**
	 * The tune before on the computer at {@code at}: the one heard before in shuffle (a new shuffled one if there is
	 * none), or up the list (round to the bottom), or {@code fallback} if nothing is playing there.
	 */
	public static void back(BlockPos at, int fallback) {
		int current = isPlayingAt(at) ? playing : -1;
		int size = tracks().size();
		if(shuffle) {
			int earlier = SHUFFLE.back(current, size);
			if(earlier >= 0)
				play(at, earlier, false);
			else
				play(at, SHUFFLE.next(current, size, ThreadLocalRandom.current()));
		}
		else
			play(at, current < 0 ? fallback : Math.floorMod(current - 1, Math.max(1, size)));
	}

	/** Plays track {@code index}; {@code remember} puts the tune it replaces on the shuffle's history (not when going back). */
	private static void play(BlockPos at, int index, boolean remember) {
		List<MusicTracks.Track> list = tracks();
		if(index < 0 || index >= list.size())
			return;
		byte[] midi = resource(list.get(index).file());
		if(midi == null) {
			problem = list.get(index).file();
			return;
		}
		if(pos != null && !pos.equals(at))
			tellServer(pos, false); // One tune for the whole client: the other computer stops.
		if(pos == null || !pos.equals(at) || playing < 0)
			tellServer(at, true);
		int previous = pos != null && pos.equals(at) ? playing : -1;
		SHUFFLE.played(remember ? previous : -1, index);
		pos = at.immutable();
		level = Minecraft.getInstance().level;
		problem = "";
		playing = index;
		paused = false;
		applyVolume();
		ComputerMidi.play(midi, outlets(pos));
	}

	/** Stops and lets go of the computer. */
	public static void stop() {
		if(playing >= 0 && pos != null)
			tellServer(pos, false);
		if(playing >= 0)
			ComputerMidi.stop();
		playing = -1;
		paused = false;
		pos = null;
		level = null;
	}

	public static void togglePause() {
		if(playing < 0)
			return;
		paused = !paused;
		ComputerMidi.pause(paused);
	}

	/** MUSIC's own volume, 0 to {@link #VOLUME_STEPS}. */
	public static int volume() {
		return volume;
	}

	/** Turns MUSIC up ({@code steps} positive) or down; heard at once (the sound card ramps to it). */
	public static void changeVolume(int steps) {
		volume = Math.max(0, Math.min(VOLUME_STEPS, volume + steps));
		applyVolume();
	}

	public static void toggleRepeat() {
		repeat = !repeat;
	}

	public static void toggleShuffle() {
		shuffle = !shuffle;
		SHUFFLE.reset();
	}

	// -------------------- EVERY TICK --------------------

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if(playing < 0 || pos == null)
			return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null || mc.player == null || mc.level != level) { // Logged out or another dimension.
			stop();
			return;
		}
		if(!(mc.level.getBlockEntity(pos) instanceof RadioComputerBlockEntity computer) || !computer.isPowered()) { // Broken, or no power.
			stop();
			return;
		}
		List<ComputerMidi.Outlet> outlets = outlets(pos);
		if(!inReach(outlets)) { // Out of earshot of the computer and all its speakers.
			stop();
			return;
		}
		ComputerMidi.setOutlets(outlets);
		ComputerMidi.tick();
		if(!paused && ComputerMidi.finished()) {
			List<MusicTracks.Track> list = tracks();
			if(repeat)
				play(pos, playing);
			else if(shuffle)
				play(pos, SHUFFLE.next(playing, list.size(), ThreadLocalRandom.current()));
			else if(playing + 1 < list.size())
				play(pos, playing + 1);
			else
				stop();
		}
	}

	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		playing = -1;
		paused = false;
		pos = null;
		level = null;
		ComputerMidi.close();
	}

	/** MUSIC's own volume onto the sound (the game's Master and Records sliders apply to it as to any sound). */
	private static void applyVolume() {
		float own = volume / (float)VOLUME_STEPS;
		ComputerMidi.setVolume(own * own); // Squared: each step sounds about as big.
	}

	/**
	 * Where the music on the computer at {@code at} comes out: the speakers connected to it (desk and premium speakers
	 * touching it, wall speakers and loudspeakers on its data wire; {@link RadioComputerBlockEntity#getSpeakers}), each
	 * in its own voice, which replace its little built-in speaker; with none, the computer itself.
	 */
	private static List<ComputerMidi.Outlet> outlets(BlockPos at) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.level != null && mc.level.getBlockEntity(at) instanceof RadioComputerBlockEntity computer && !computer.getSpeakers().isEmpty()) {
			List<BEVoiceReceiver.WiredSpeaker> speakers = computer.getSpeakers();
			if(speakers != lastSpeakers) { // Made again only when the server sends a new list.
				lastSpeakers = speakers;
				List<ComputerMidi.Outlet> out = new ArrayList<>();
				for(BEVoiceReceiver.WiredSpeaker speaker : speakers)
					out.add(new ComputerMidi.Outlet(speaker.pos().getCenter(), speaker.kind(), RadioComputerBlockEntity.musicRange(speaker.kind())));
				lastOutlets = List.copyOf(out);
			}
			return lastOutlets;
		}
		return List.of(new ComputerMidi.Outlet(at.getCenter(), SpeakerKind.BUILT_IN, (float)MAX_RANGE));
	}

	@Nullable
	private static List<BEVoiceReceiver.WiredSpeaker> lastSpeakers;
	private static List<ComputerMidi.Outlet> lastOutlets = List.of();

	/** True if the player is within earshot of one of {@code outlets}. */
	private static boolean inReach(List<ComputerMidi.Outlet> outlets) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null)
			return true;
		for(ComputerMidi.Outlet outlet : outlets)
			if(mc.player.getEyePosition().distanceToSqr(outlet.pos()) <= (double)outlet.range() * outlet.range())
				return true;
		return false;
	}

	/** Tells the computer at {@code at} this player listens ({@code on}) or has stopped. */
	private static void tellServer(BlockPos at, boolean on) {
		if(Minecraft.getInstance().getConnection() == null)
			return;
		CompoundTag data = new CompoundTag();
		data.putBoolean("on", on);
		SStationPacket.send(at, "music", data);
	}

	/** A file from the music folder of the resource packs, or null. */
	@Nullable
	static byte[] resource(String file) {
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(Radiocraft.id("computer/music/" + file));
		if(resource.isEmpty())
			return null;
		try(InputStream in = resource.get().open()) {
			return in.readNBytes(1 << 20); // A tune is a few KB; a megabyte is plenty.
		}
		catch(IOException e) {
			Radiocraft.LOGGER.warn("Could not read computer/music/{}", file, e);
			return null;
		}
	}
}
