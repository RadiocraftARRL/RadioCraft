package com.arrl.radiocraft.common.computer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The radio computer's MUSIC program's track list (1.12 step 8): {@code assets/radiocraft/computer/music/tracks.txt},
 * a {@code file|title|composer|seconds} line per MIDI file in that folder, in play order; {@code #} starts a comment.
 * Every file is public domain (see {@code doc/Credits-Music.md}). Pure (no game classes), so it is unit tested; the
 * client reads the resource and plays the files with {@code javax.sound.midi}. A resource pack can replace the list
 * and the files.
 */
public final class MusicTracks {

	/** One track: its file name in the music folder, title, composer and length in seconds (0 if not given). */
	public record Track(String file, String title, String composer, int seconds) {
		/** "4:13". */
		public String length() {
			return seconds <= 0 ? "--:--" : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
		}
	}

	private MusicTracks() {}

	/**
	 * The tracks in {@code text}. A line with no title, or a file name that is not a plain lower-case {@code .mid} name
	 * (a resource path cannot have capitals), is skipped.
	 */
	public static List<Track> parse(String text) {
		List<Track> tracks = new ArrayList<>();
		for(String raw : text.split("\n")) {
			String line = raw.strip();
			if(line.isEmpty() || line.startsWith("#"))
				continue;
			String[] parts = line.split("\\|", -1);
			if(parts.length < 2)
				continue;
			String file = parts[0].strip();
			String title = parts[1].strip();
			if(title.isEmpty() || !file.matches("[a-z0-9_.-]+\\.mid"))
				continue;
			String composer = parts.length > 2 ? parts[2].strip() : "";
			int seconds = 0;
			if(parts.length > 3)
				try {
					seconds = Math.max(0, Integer.parseInt(parts[3].strip()));
				}
				catch(NumberFormatException ignored) {
					// No length then.
				}
			tracks.add(new Track(file, title, composer, seconds));
		}
		return tracks;
	}
}
