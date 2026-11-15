package com.arrl.radiocraft.common.radio.station;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The pure parts of an FM station's internet stream (2026-11-14): playlists, ICY metadata, host safety, resampling. */
class InternetStreamPartsTest {

	@Test
	void m3uGivesItsFirstStreamResolved() throws IOException {
		String m3u = "#EXTM3U\n#EXTINF:-1,Some Station\nhttp://radio.example.com:8000/live.mp3\nhttp://backup.example.com/live.mp3\n";
		assertEquals("http://radio.example.com:8000/live.mp3", StreamPlaylists.firstStream(m3u, "http://example.com/list.m3u"));
		assertEquals("http://example.com/streams/a.ogg", StreamPlaylists.firstStream("streams/a.ogg\n", "http://example.com/list.m3u"));
		assertTrue(StreamPlaylists.isPlaylist("audio/x-mpegurl", "http://x/y"));
		assertTrue(StreamPlaylists.isPlaylist("", "http://x/y.pls?id=3"));
		assertFalse(StreamPlaylists.isPlaylist("audio/mpeg", "http://x/live"));
	}

	@Test
	void plsGivesFileOne() throws IOException {
		String pls = "[playlist]\r\nNumberOfEntries=2\r\nFile2=http://two.example.com/s\r\nFile1=http://one.example.com/s\r\nTitle1=One\r\nVersion=2\r\n";
		assertEquals("http://one.example.com/s", StreamPlaylists.firstStream(pls, "http://example.com/a.pls"));
	}

	@Test
	void hlsAndEmptyAreRefused() {
		String hls = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nseg1.ts\n";
		IOException e = assertThrows(IOException.class, () -> StreamPlaylists.firstStream(hls, "http://example.com/a.m3u8"));
		assertTrue(e.getMessage().contains("HLS"));
		assertThrows(IOException.class, () -> StreamPlaylists.firstStream("#EXTM3U\n# nothing\n", null));
	}

	@Test
	void icyMetadataIsStrippedAndTheTitleRead() throws IOException {
		int metaint = 8;
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		byte[] audio = "ABCDEFGHIJKLMNOP".getBytes(StandardCharsets.US_ASCII);
		raw.write(audio, 0, 8);
		byte[] meta = "StreamTitle='It's a Song - Band';".getBytes(StandardCharsets.UTF_8);
		int blocks = (meta.length + 15) / 16;
		raw.write(blocks);
		raw.write(meta);
		for(int i = meta.length; i < blocks * 16; i++)
			raw.write(0);
		raw.write(audio, 8, 8);
		raw.write(0); // An empty metadata block.
		List<String> titles = new ArrayList<>();
		InputStream in = new IcyMetadataStream(new ByteArrayInputStream(raw.toByteArray()), metaint, titles::add);
		assertEquals("ABCDEFGHIJKLMNOP", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
		assertEquals(List.of("It's a Song - Band"), titles);
		assertNull(IcyMetadataStream.title("StreamTitle='';"));
		assertNull(IcyMetadataStream.title("StreamUrl='x';"));
	}

	@Test
	void privateAndLocalHostsAreRefused() throws IOException {
		for(String host : new String[] {"127.0.0.1", "10.1.2.3", "172.20.0.1", "192.168.1.10", "169.254.1.1", "100.64.0.1", "0.0.0.0", "::1", "fd12::1", "fe80::1", "224.0.0.1"})
			assertFalse(StreamHosts.allowed(InetAddress.getByName(host), false), host);
		assertTrue(StreamHosts.allowed(InetAddress.getByName("8.8.8.8"), false));
		assertTrue(StreamHosts.allowed(InetAddress.getByName("2001:4860:4860::8888"), false));
		assertTrue(StreamHosts.allowed(InetAddress.getByName("192.168.1.10"), true), "allowed when the server says so");
		assertFalse(StreamHosts.allowed(InetAddress.getByName("224.0.0.1"), true), "never multicast");
		assertThrows(IOException.class, () -> StreamHosts.check("ftp://example.com/a.mp3", true));
		assertThrows(IOException.class, () -> StreamHosts.check("http://127.0.0.1:8000/live", false));
	}

	@Test
	void privateHostsInsideIpv6AreRefused() throws IOException {
		// 6to4, NAT64 and IPv4-compatible forms of 127.0.0.1, 10.0.0.1 and 169.254.169.254.
		for(String host : new String[] {"2002:7f00:1::1", "2002:a00:1::", "64:ff9b::a9fe:a9fe", "64:ff9b::7f00:1", "::a00:1"})
			assertFalse(StreamHosts.allowed(InetAddress.getByName(host), false), host);
		assertTrue(StreamHosts.allowed(InetAddress.getByName("2002:808:808::1"), false), "6to4 of a public address");
		assertTrue(StreamHosts.allowed(InetAddress.getByName("64:ff9b::808:808"), false), "NAT64 of a public address");
	}

	@Test
	void downmixAndResampleCarryOnAcrossChunks() {
		float[] mono = StreamResampler.downmix(new short[] {1000, 3000, -2000, 2000}, 2, 2);
		assertEquals(2000 / 32768.0F, mono[0], 1e-6);
		assertEquals(0.0F, mono[1], 1e-6);
		// A 24 kHz ramp, in two chunks: twice as many samples out, smooth across the join.
		StreamResampler r = new StreamResampler(24_000);
		float[] a = new float[100], b = new float[100];
		for(int i = 0; i < 100; i++) {
			a[i] = i / 400.0F;
			b[i] = (i + 100) / 400.0F;
		}
		short[] one = r.push(a, 100), two = r.push(b, 100);
		assertEquals(400, one.length + two.length, 2);
		short last = one[one.length - 1], first = two[0];
		assertTrue(first >= last && first - last < 60, "the join steps from " + last + " to " + first);
		StreamResampler same = new StreamResampler(48_000);
		assertEquals(100, same.push(a, 100).length);
	}
}
