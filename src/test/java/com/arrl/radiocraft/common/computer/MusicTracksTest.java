package com.arrl.radiocraft.common.computer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The MUSIC program's track list (1.12 step 8), and that every tune shipped is listed and credited. */
class MusicTracksTest {

	@Test
	void readsTheList() {
		List<MusicTracks.Track> tracks = MusicTracks.parse("""
				# a comment
				one.mid|First Tune|Someone|125
				two.mid|Second Tune||
				Bad Name.mid|Skipped|Nobody|10
				three.mid||No title|10
				four.mid|Fourth|Anon|x
				""");
		assertEquals(3, tracks.size());
		assertEquals(new MusicTracks.Track("one.mid", "First Tune", "Someone", 125), tracks.getFirst());
		assertEquals("2:05", tracks.getFirst().length());
		assertEquals("--:--", tracks.get(1).length());
		assertEquals(0, tracks.get(2).seconds());
	}

	@Test
	void everyTuneShippedIsListedAndCredited() throws IOException {
		Path root = projectRoot();
		Path folder = root.resolve("src/main/resources/assets/radiocraft/computer/music");
		List<MusicTracks.Track> tracks = MusicTracks.parse(Files.readString(folder.resolve("tracks.txt")));
		assertTrue(tracks.size() >= 8, "only " + tracks.size() + " tunes");
		Set<String> listed = new HashSet<>();
		String credits = Files.readString(root.resolve("doc/Credits-Music.md"));
		for(MusicTracks.Track track : tracks) {
			assertTrue(listed.add(track.file()), track.file() + " listed twice");
			Path file = folder.resolve(track.file());
			assertTrue(Files.isRegularFile(file), "no " + track.file());
			byte[] bytes = Files.readAllBytes(file);
			assertEquals("MThd", new String(bytes, 0, 4, StandardCharsets.US_ASCII), track.file() + " is not a MIDI file");
			assertTrue(credits.contains("`" + track.file() + "`"), track.file() + " is not in doc/Credits-Music.md");
		}
		try(Stream<Path> files = Files.list(folder)) {
			files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".mid"))
					.forEach(n -> assertTrue(listed.contains(n), n + " is not in tracks.txt"));
		}
	}

	private static Path projectRoot() {
		for(Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
			if(Files.isDirectory(dir.resolve("src/main/resources/assets/radiocraft")))
				return dir;
		return Path.of("");
	}
}
