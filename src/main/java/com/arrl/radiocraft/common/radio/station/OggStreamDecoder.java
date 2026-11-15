package com.arrl.radiocraft.common.radio.station;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;

/**
 * Decodes an Ogg Vorbis stream as it arrives, with no end (2026-11-14, FM station internet streams; the tape player's
 * {@code OggDecoder} reads a whole file). An Icecast Vorbis stream is chained: each song may start a new logical stream
 * with fresh headers, so a page from a new serial number starts the decoder over. Each decoded chunk goes to
 * {@code sink} as mono at the stream's rate.
 */
public final class OggStreamDecoder {

	/** Where the decoded audio goes: {@code n} mono samples (-1..1) at {@code rate}. */
	public interface Sink {
		void accept(float[] mono, int n, int rate) throws IOException;
	}

	/** Called with each logical stream's nominal bit rate (bits a second; 0 if it does not say). */
	public interface BitrateCheck {
		void check(int bitsPerSecond) throws IOException;
	}

	private static final int CHUNK = 4096;

	private OggStreamDecoder() {}

	/** Decodes {@code in} until it ends or {@code running} turns false. */
	public static void run(InputStream in, Sink sink, BitrateCheck bitrate, BooleanSupplier running) throws IOException {
		SyncState sync = new SyncState();
		StreamState stream = new StreamState();
		Page page = new Page();
		Packet packet = new Packet();
		Info info = null;
		Comment comment = null;
		DspState dsp = null;
		Block block = null;
		int serial = 0, headers = 0;
		boolean any = false;
		sync.init();
		float[][][] pcm = new float[1][][];
		try {
			while(running.getAsBoolean()) {
				int result = sync.pageout(page);
				if(result == 0) {
					int at = sync.buffer(CHUNK);
					int read = in.read(sync.data, at, CHUNK);
					if(read <= 0)
						return;
					sync.wrote(read);
					continue;
				}
				if(result < 0)
					continue; // A gap: carry on at the next page.
				if(!any || page.bos() != 0 || page.serialno() != serial) { // A new logical stream (the next song).
					if(any) {
						stream.clear();
						if(block != null)
							block.clear();
						if(dsp != null)
							dsp.clear();
					}
					serial = page.serialno();
					stream.init(serial);
					info = new Info();
					comment = new Comment();
					info.init();
					comment.init();
					dsp = null;
					block = null;
					headers = 0;
					any = true;
				}
				stream.pagein(page);
				while(stream.packetout(packet) == 1) {
					if(headers < 3) {
						if(info.synthesis_headerin(comment, packet) < 0)
							throw new IOException("not Vorbis audio");
						if(++headers == 3) {
							// JOrbis keeps the nominal bitrate package-private: the bytes-per-second cap on the connection limits it instead.
							dsp = new DspState();
							dsp.synthesis_init(info);
							block = new Block(dsp);
							block.init(dsp);
						}
						continue;
					}
					if(block.synthesis(packet) == 0)
						dsp.synthesis_blockin(block);
					int[] index = new int[info.channels];
					int samples;
					while((samples = dsp.synthesis_pcmout(pcm, index)) > 0) {
						float[] mono = new float[samples];
						int channels = info.channels;
						for(int i = 0; i < samples; i++) {
							float sum = 0.0F;
							for(int c = 0; c < channels; c++)
								sum += pcm[0][c][index[c] + i];
							mono[i] = sum / channels;
						}
						sink.accept(mono, samples, info.rate);
						dsp.synthesis_read(samples);
					}
				}
			}
		}
		finally {
			stream.clear();
			if(block != null)
				block.clear();
			if(dsp != null)
				dsp.clear();
			sync.clear();
		}
	}
}
