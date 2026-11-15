package com.arrl.radiocraft.common.radio.station;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The playlists an internet radio station's link may be (2026-11-14): {@code .m3u}/{@code .m3u8} (a list of URLs, lines
 * starting with # are comments) and {@code .pls} ({@code [playlist]} with {@code File1=...}). Either is read for its first
 * stream. HLS (segmented) playlists, which an {@code .m3u8} often is, cannot be played as one stream and are refused
 * with a reason. Pure: no game classes, unit tested.
 */
public final class StreamPlaylists {

	/** The most of a playlist read, bytes: anything bigger is not a playlist. */
	public static final int MAX_BYTES = 64 * 1024;

	private StreamPlaylists() {}

	/** True if a response with this content type (or, failing that, this URL) is a playlist rather than audio. */
	public static boolean isPlaylist(String contentType, String url) {
		String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		if(type.contains("mpegurl") || type.contains("scpls") || type.contains("x-pls"))
			return true;
		String path = url == null ? "" : url.toLowerCase(Locale.ROOT);
		int query = path.indexOf('?');
		if(query >= 0)
			path = path.substring(0, query);
		return path.endsWith(".m3u") || path.endsWith(".m3u8") || path.endsWith(".pls");
	}

	/** True if {@code start} (the first bytes of a body) looks like a playlist's text. */
	public static boolean looksLikePlaylist(String start) {
		String s = start.stripLeading().toLowerCase(Locale.ROOT);
		return s.startsWith("#extm3u") || s.startsWith("[playlist]") || s.startsWith("http://") || s.startsWith("https://");
	}

	/**
	 * The first stream in playlist {@code body}, resolved against {@code base} (the playlist's own URL) if relative.
	 *
	 * @throws IOException HLS, or no stream in it
	 */
	public static String firstStream(String body, String base) throws IOException {
		String upper = body.toUpperCase(Locale.ROOT);
		if(upper.contains("#EXT-X-TARGETDURATION") || upper.contains("#EXT-X-STREAM-INF") || upper.contains("#EXT-X-MEDIA-SEQUENCE"))
			throw new IOException("HLS (segmented) streams cannot be played: use a direct MP3 or Ogg stream link");
		String[] lines = body.split("\r?\n|\r");
		String first = lines.length == 0 ? "" : lines[0].strip();
		if(first.toLowerCase(Locale.ROOT).startsWith("[playlist]") || upper.contains("\nFILE1=")) {
			Map<Integer, String> files = new TreeMap<>();
			for(String line : lines) {
				String l = line.strip();
				int eq = l.indexOf('=');
				if(eq > 4 && l.substring(0, 4).equalsIgnoreCase("file")) {
					try {
						files.put(Integer.parseInt(l.substring(4, eq).strip()), l.substring(eq + 1).strip());
					}
					catch(NumberFormatException ignored) {
					}
				}
			}
			for(String url : files.values())
				if(!url.isEmpty())
					return resolve(base, url);
			throw new IOException("the playlist has no stream in it");
		}
		for(String line : lines) {
			String l = line.strip();
			if(l.isEmpty() || l.startsWith("#"))
				continue;
			return resolve(base, l);
		}
		throw new IOException("the playlist has no stream in it");
	}

	private static String resolve(String base, String url) throws IOException {
		try {
			return base == null ? URI.create(url).toString() : URI.create(base).resolve(url).toString();
		}
		catch(IllegalArgumentException e) {
			throw new IOException("a bad link in the playlist: " + url);
		}
	}
}
