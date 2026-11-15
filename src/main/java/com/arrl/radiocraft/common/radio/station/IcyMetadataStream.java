package com.arrl.radiocraft.common.radio.station;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * An Icecast/Shoutcast stream's audio with its ICY metadata taken out (2026-11-14). Asked for with
 * {@code Icy-MetaData: 1}, the server puts a metadata block after every {@code icy-metaint} bytes of audio: one byte, its
 * length in 16s, then that much text like {@code StreamTitle='Artist - Song';}. Left in, it would be decoded as audio
 * (clicks, lost frames). Each title is handed to {@code onTitle}. Pure: unit tested.
 */
public final class IcyMetadataStream extends FilterInputStream {

	private final int metaint;
	private final Consumer<String> onTitle;
	private int untilMeta;

	public IcyMetadataStream(InputStream in, int metaint, Consumer<String> onTitle) {
		super(in);
		this.metaint = metaint;
		this.onTitle = onTitle;
		this.untilMeta = metaint;
	}

	@Override
	public int read() throws IOException {
		byte[] one = new byte[1];
		int n = read(one, 0, 1);
		return n <= 0 ? -1 : one[0] & 0xFF;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if(len == 0)
			return 0;
		if(untilMeta == 0) {
			readMetadata();
			untilMeta = metaint;
		}
		int n = in.read(b, off, Math.min(len, untilMeta));
		if(n > 0)
			untilMeta -= n;
		return n;
	}

	@Override
	public long skip(long n) throws IOException {
		byte[] scratch = new byte[(int)Math.min(4096, Math.max(0, n))];
		long left = n;
		while(left > 0) {
			int got = read(scratch, 0, (int)Math.min(scratch.length, left));
			if(got <= 0)
				break;
			left -= got;
		}
		return n - left;
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	private void readMetadata() throws IOException {
		int length = in.read();
		if(length < 0)
			return;
		int bytes = length * 16;
		if(bytes == 0)
			return;
		byte[] meta = in.readNBytes(bytes);
		String title = title(new String(meta, StandardCharsets.UTF_8));
		if(title != null)
			onTitle.accept(title);
	}

	/** The {@code StreamTitle} in a metadata block, or null if it has none (or it is empty). */
	public static String title(String metadata) {
		String key = "StreamTitle='";
		int start = metadata.indexOf(key);
		if(start < 0)
			return null;
		start += key.length();
		int end = metadata.indexOf("';", start);
		if(end < 0)
			end = metadata.lastIndexOf('\'');
		if(end < start)
			end = metadata.length();
		String title = metadata.substring(start, end).replace("\0", "").strip();
		return title.isEmpty() ? null : title;
	}
}
