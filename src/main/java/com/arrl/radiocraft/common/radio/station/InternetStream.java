package com.arrl.radiocraft.common.radio.station;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An internet radio stream an FM broadcast station puts on the air (2026-11-14, the maintainer: "attach it to an internet
 * radio stream (direct mp3 or an m3u), so players can basically pass those through as radio stations"). The station's
 * owner gives a link; the server fetches it on a thread of its own and decodes it into the radio's audio, which the
 * station pulls a frame at a time ({@link ProgramSource}).
 * <ul>
 * <li><b>What plays:</b> a direct MP3 stream (JLayer) or Ogg Vorbis stream (JOrbis), or an {@code .m3u}/{@code .pls}
 * playlist that points to one ({@link StreamPlaylists}; HLS is refused). AAC is not supported.</li>
 * <li><b>Off unless the server allows it</b> ({@code fm_internet_streams}, default off), at most
 * {@code fm_stream_max_streams} at once on the server.</li>
 * <li><b>Safety</b> ({@link StreamHosts}): http and https only; no loopback or private addresses unless
 * {@code fm_stream_allow_private_hosts}; each redirect (at most {@link #MAX_REDIRECTS}) checked again; connect and read
 * time-outs; nothing over {@link #MAX_BITRATE}.</li>
 * <li><b>Buffering:</b> about {@link #BUFFER_SECONDS} s ahead, {@link #PREBUFFER_SECONDS} s before it plays (and again
 * after it runs dry). The decoder waits while the buffer is full, so a recording (not a live stream) is read only as
 * fast as it plays.</li>
 * <li><b>Dropped:</b> it reconnects after 1, 2, 4 ... {@link #MAX_BACKOFF_SECONDS} s; a link that can never play (a
 * private address, HLS, AAC, too high a bit rate) stops with {@link Status#ERROR} and its reason.</li>
 * </ul>
 * The station starts it ({@link #start}) when the stream is its program and something should be heard, and stops it
 * ({@link #stop}) when not: fetching costs the server bandwidth whether anyone listens or not, so a station should stop
 * its stream while no FM receiver in range is on (and start it again when one is: it takes a second or two to buffer).
 */
public final class InternetStream implements ProgramSource {

	/** Where a stream is. */
	public enum Status { OFF, CONNECTING, BUFFERING, PLAYING, ERROR }

	public static final int MAX_REDIRECTS = 5;
	/** The highest bit rate taken, bits a second. */
	public static final int MAX_BITRATE = 512_000;
	public static final int BUFFER_SECONDS = 3, PREBUFFER_SECONDS = 1;
	public static final int MAX_BACKOFF_SECONDS = 60;
	private static final int CONNECT_TIMEOUT_MS = 8_000, READ_TIMEOUT_MS = 15_000;
	/** Over this many bytes a second on average after {@link #RATE_GRACE_MS} (a 512 kbps stream and its opening burst), it is cut off. */
	private static final long MAX_BYTES_PER_SECOND = 96_000L;
	private static final long RATE_GRACE_MS = 30_000L;
	private static final String USER_AGENT = "RadioCraft FM station (Minecraft mod)";

	/** Streams open on this server (each holding a connection). */
	private static final AtomicInteger OPEN = new AtomicInteger();

	private final String url;
	private final UUID owner;
	private final short[] ring = new short[StreamResampler.OUT_RATE * BUFFER_SECONDS];
	private int head, size;
	private boolean buffering = true;
	private volatile Status status = Status.OFF;
	private volatile String reason = "";
	private volatile String title = "";
	private volatile boolean running;
	private volatile boolean counted;
	@Nullable
	private volatile Thread thread;
	@Nullable
	private volatile InputStream open;

	public InternetStream(String url, UUID owner) {
		this.url = url == null ? "" : url.strip();
		this.owner = owner;
	}

	public String url() {
		return url;
	}

	public UUID owner() {
		return owner;
	}

	public Status status() {
		return status;
	}

	/** Why it is in {@link Status#ERROR} (or why it is reconnecting); empty otherwise. */
	public String reason() {
		return reason;
	}

	/** True if the server allows internet streams at all ({@code fm_internet_streams}). */
	public static boolean allowed() {
		return RadiocraftServerConfig.FM_INTERNET_STREAMS.get();
	}

	/** Starts fetching, unless it is fetching already. Refused (ERROR) when the server does not allow streams or has too many. */
	public synchronized void start() {
		if(running)
			return;
		if(!allowed()) {
			fail("internet streams are off on this server");
			return;
		}
		if(url.isEmpty()) {
			fail("no link set");
			return;
		}
		if(OPEN.incrementAndGet() > RadiocraftServerConfig.FM_STREAM_MAX_STREAMS.get()) {
			OPEN.decrementAndGet();
			fail("too many internet streams on this server");
			return;
		}
		counted = true;
		running = true;
		status = Status.CONNECTING;
		reason = "";
		Thread t = new Thread(this::loop, "RadioCraft FM stream");
		t.setDaemon(true);
		thread = t;
		t.start();
	}

	/** Stops fetching at once (the connection is closed, the thread ends) and empties the buffer. */
	public synchronized void stop() {
		running = false;
		Thread t = thread;
		thread = null;
		closeOpen();
		if(t != null)
			t.interrupt();
		synchronized(ring) {
			size = 0;
			head = 0;
			buffering = true;
			ring.notifyAll();
		}
		release();
		if(status != Status.ERROR)
			status = Status.OFF;
	}

	private void release() {
		if(counted) {
			counted = false;
			OPEN.decrementAndGet();
		}
	}

	private void fail(String why) {
		reason = why;
		status = Status.ERROR;
	}

	@Override
	@Nullable
	public short[] nextFrame() {
		synchronized(ring) {
			if(buffering) {
				if(size < StreamResampler.OUT_RATE * PREBUFFER_SECONDS)
					return null;
				buffering = false;
				if(running)
					status = Status.PLAYING;
			}
			if(size < FRAME) {
				buffering = true; // Ran dry: wait for a second's worth again.
				if(running)
					status = Status.BUFFERING;
				return null;
			}
			short[] out = new short[FRAME];
			int tail = Math.floorMod(head - size, ring.length);
			for(int i = 0; i < FRAME; i++)
				out[i] = ring[(tail + i) % ring.length];
			size -= FRAME;
			ring.notifyAll();
			return out;
		}
	}

	@Override
	public boolean live() {
		return running && status == Status.PLAYING;
	}

	@Override
	public String nowPlaying() {
		return title;
	}

	// -------------------- THE FETCHING THREAD --------------------

	private void loop() {
		int backoff = 1;
		try {
			while(running) {
				long began = System.currentTimeMillis();
				try {
					status = Status.CONNECTING;
					play(url, 0);
					if(!running)
						break;
					reason = "the stream ended";
				}
				catch(Permanent e) {
					fail(e.getMessage());
					running = false;
					break;
				}
				catch(IOException | RuntimeException e) {
					if(!running)
						break;
					reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
					Radiocraft.LOGGER.debug("FM stream {} dropped: {}", url, reason);
				}
				finally {
					closeOpen();
				}
				if(System.currentTimeMillis() - began > 30_000L)
					backoff = 1; // It played a while: start the backoff over.
				if(!running)
					break;
				status = Status.CONNECTING;
				try {
					Thread.sleep(backoff * 1000L);
				}
				catch(InterruptedException e) {
					break;
				}
				backoff = Math.min(MAX_BACKOFF_SECONDS, backoff * 2);
			}
		}
		finally {
			synchronized(this) {
				if(thread == Thread.currentThread() || thread == null) {
					running = false;
					release();
				}
			}
		}
	}

	/** A link that will never play: no point trying again. */
	private static final class Permanent extends IOException {
		private static final long serialVersionUID = 1L;

		Permanent(String message) {
			super(message);
		}
	}

	/** Fetches {@code link} (a playlist is followed to its stream) and decodes it until it ends or the stream stops. */
	private void play(String link, int depth) throws IOException {
		if(depth > 2)
			throw new Permanent("playlists point to playlists too many times");
		HttpURLConnection connection = connect(link);
		String type = connection.getContentType() == null ? "" : connection.getContentType().toLowerCase(Locale.ROOT);
		String finalUrl = connection.getURL().toString();
		InputStream raw = new BufferedInputStream(new RateLimited(connection.getInputStream()), 16 * 1024);
		open = raw;
		if(StreamPlaylists.isPlaylist(type, finalUrl) || type.startsWith("text/") || sniffPlaylist(raw)) {
			byte[] body = raw.readNBytes(StreamPlaylists.MAX_BYTES);
			closeOpen();
			String next;
			try {
				next = StreamPlaylists.firstStream(new String(body, StandardCharsets.UTF_8), finalUrl);
			}
			catch(IOException e) {
				throw new Permanent(e.getMessage());
			}
			play(next, depth + 1);
			return;
		}
		if(type.contains("aac") || type.contains("mp4"))
			throw new Permanent("AAC streams are not supported: use an MP3 or Ogg stream");
		InputStream audio = raw;
		int metaint = connection.getHeaderFieldInt("icy-metaint", 0);
		if(metaint > 0)
			audio = new IcyMetadataStream(raw, metaint, t -> title = t);
		else {
			String name = connection.getHeaderField("icy-name");
			if(name != null && !name.isBlank() && title.isEmpty())
				title = name.strip();
		}
		String br = connection.getHeaderField("icy-br");
		if(br != null) {
			try {
				if(Integer.parseInt(br.split(",")[0].strip()) * 1000 > MAX_BITRATE)
					throw new Permanent("the stream's bit rate is over 512 kbps");
			}
			catch(NumberFormatException ignored) {
			}
		}
		status = Status.BUFFERING;
		boolean ogg = type.contains("ogg") || type.contains("vorbis") || startsWith(raw, "OggS");
		if(ogg)
			OggStreamDecoder.run(audio, this::deliver, bps -> {
				if(bps > MAX_BITRATE)
					throw new Permanent("the stream's bit rate is over 512 kbps");
			}, () -> running);
		else
			decodeMp3(audio);
	}

	/** Opens {@code link}, following redirects by hand so each new host is checked ({@link StreamHosts}). */
	private HttpURLConnection connect(String link) throws IOException {
		boolean allowPrivate = RadiocraftServerConfig.FM_STREAM_ALLOW_PRIVATE_HOSTS.get();
		String at = link;
		for(int hop = 0; hop <= MAX_REDIRECTS; hop++) {
			URI uri;
			try {
				uri = StreamHosts.check(at, allowPrivate);
			}
			catch(java.net.UnknownHostException e) {
				throw new IOException("unknown host " + e.getMessage());
			}
			catch(IOException e) {
				throw new Permanent(e.getMessage());
			}
			HttpURLConnection c = (HttpURLConnection)uri.toURL().openConnection();
			c.setInstanceFollowRedirects(false);
			c.setConnectTimeout(CONNECT_TIMEOUT_MS);
			c.setReadTimeout(READ_TIMEOUT_MS);
			c.setRequestProperty("User-Agent", USER_AGENT);
			c.setRequestProperty("Icy-MetaData", "1");
			c.setRequestProperty("Accept", "audio/mpeg, audio/ogg, application/ogg, audio/x-mpegurl, audio/x-scpls, */*;q=0.5");
			int code = c.getResponseCode();
			if(code >= 300 && code < 400) {
				String next = c.getHeaderField("Location");
				c.disconnect();
				if(next == null)
					throw new IOException("a redirect with nowhere to go");
				at = uri.resolve(next).toString();
				continue;
			}
			if(code != 200) {
				c.disconnect();
				if(code == 404 || code == 403 || code == 401 || code == 410)
					throw new Permanent("the server answered " + code);
				throw new IOException("the server answered " + code);
			}
			return c;
		}
		throw new Permanent("too many redirects");
	}

	private static boolean sniffPlaylist(InputStream in) throws IOException {
		in.mark(64);
		byte[] start = in.readNBytes(32);
		in.reset();
		return StreamPlaylists.looksLikePlaylist(new String(start, StandardCharsets.ISO_8859_1));
	}

	private static boolean startsWith(InputStream in, String magic) throws IOException {
		in.mark(16);
		byte[] start = in.readNBytes(magic.length());
		in.reset();
		return new String(start, StandardCharsets.ISO_8859_1).equals(magic);
	}

	private void decodeMp3(InputStream in) throws IOException {
		Bitstream bitstream = new Bitstream(in);
		Decoder decoder = new Decoder();
		int errors = 0;
		try {
			while(running) {
				Header header;
				try {
					header = bitstream.readFrame();
				}
				catch(JavaLayerException e) {
					throw new IOException("not an MP3 stream (" + e.getMessage() + ")");
				}
				if(header == null)
					return; // The end.
				if(header.bitrate() > MAX_BITRATE)
					throw new Permanent("the stream's bit rate is over 512 kbps");
				try {
					SampleBuffer out = (SampleBuffer)decoder.decodeFrame(header, bitstream);
					int channels = Math.max(1, out.getChannelCount());
					int frames = out.getBufferLength() / channels;
					deliver(StreamResampler.downmix(out.getBuffer(), frames, channels), frames, out.getSampleFrequency());
					errors = 0;
				}
				catch(JavaLayerException | ArrayIndexOutOfBoundsException e) {
					if(++errors > 50)
						throw new IOException("the MP3 stream will not decode");
				}
				finally {
					bitstream.closeFrame();
				}
			}
		}
		finally {
			try {
				bitstream.close();
			}
			catch(JavaLayerException ignored) {
			}
		}
	}

	@Nullable
	private StreamResampler resampler;

	/** Decoded audio into the buffer, at the radio's rate; waits while the buffer is full. */
	private void deliver(float[] mono, int n, int rate) throws IOException {
		if(resampler == null || resampler.inRate() != rate)
			resampler = new StreamResampler(rate);
		short[] out = resampler.push(mono, n);
		int at = 0;
		synchronized(ring) {
			while(at < out.length) {
				if(!running)
					throw new IOException("stopped");
				int room = ring.length - size;
				if(room == 0) {
					try {
						ring.wait(200L);
					}
					catch(InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IOException("stopped");
					}
					continue;
				}
				int take = Math.min(room, out.length - at);
				for(int i = 0; i < take; i++)
					ring[(head + i) % ring.length] = out[at + i];
				head = (head + take) % ring.length;
				size += take;
				at += take;
			}
		}
	}

	private void closeOpen() {
		InputStream in = open;
		open = null;
		if(in != null) {
			try {
				in.close();
			}
			catch(IOException ignored) {
			}
		}
	}

	/** Cuts the stream off if it comes in much faster than any allowed bit rate plays (after a grace for the opening burst). */
	private static final class RateLimited extends FilterInputStream {
		private final long started = System.currentTimeMillis();
		private long bytes;

		RateLimited(InputStream in) {
			super(in);
		}

		@Override
		public int read() throws IOException {
			int b = super.read();
			if(b >= 0)
				count(1);
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int n = super.read(b, off, len);
			if(n > 0)
				count(n);
			return n;
		}

		private void count(int n) throws IOException {
			bytes += n;
			long elapsed = System.currentTimeMillis() - started;
			if(elapsed > RATE_GRACE_MS && bytes * 1000L / elapsed > MAX_BYTES_PER_SECOND)
				throw new Permanent("the stream comes in faster than 512 kbps");
		}
	}
}
