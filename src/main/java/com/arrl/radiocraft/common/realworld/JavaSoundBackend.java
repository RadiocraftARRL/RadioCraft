package com.arrl.radiocraft.common.realworld;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The server's own sound cards ({@code javax.sound.sampled}, part of the JDK) and serial ports (jSerialComm, through
 * {@link SerialControlPort}, which is only loaded by name when a port is opened or listed: nothing of jSerialComm
 * loads, and none of its native code is unpacked, unless an enabled interface has a serial port or an operator lists
 * the ports). Lines are opened as 16-bit signed little-endian PCM, mono if the card will, else stereo, with a buffer of
 * {@link #BUFFER_MS} so PTT and audio stay together.
 */
public final class JavaSoundBackend implements AudioBackend {

	/** The sound card buffer each way, ms: small, so silence written in the hang time does not pile up ahead of voice. */
	public static final int BUFFER_MS = 100;
	private static final String SERIAL_CLASS = "com.arrl.radiocraft.common.realworld.SerialControlPort";

	@Override
	public Input openInput(String device, int rate) throws IOException {
		Mixer.Info mixer = find(device, TargetDataLine.class);
		IOException last = null;
		for(int channels = 1; channels <= 2; channels++) {
			AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			try {
				TargetDataLine line = (TargetDataLine)(mixer == null ? AudioSystem.getLine(info) : AudioSystem.getMixer(mixer).getLine(info));
				line.open(format, bufferBytes(rate, channels));
				line.start();
				return new LineInput(line, channels);
			}
			catch(IllegalArgumentException | LineUnavailableException | SecurityException e) {
				last = new IOException(device + " will not open at " + rate + " Hz, " + channels + " channel(s): " + e.getMessage(), e);
			}
		}
		throw last;
	}

	@Override
	public Output openOutput(String device, int rate) throws IOException {
		Mixer.Info mixer = find(device, SourceDataLine.class);
		IOException last = null;
		for(int channels = 1; channels <= 2; channels++) {
			AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			try {
				SourceDataLine line = (SourceDataLine)(mixer == null ? AudioSystem.getLine(info) : AudioSystem.getMixer(mixer).getLine(info));
				line.open(format, bufferBytes(rate, channels));
				line.start();
				return new LineOutput(line, channels);
			}
			catch(IllegalArgumentException | LineUnavailableException | SecurityException e) {
				last = new IOException(device + " will not open at " + rate + " Hz, " + channels + " channel(s): " + e.getMessage(), e);
			}
		}
		throw last;
	}

	private static int bufferBytes(int rate, int channels) {
		return Math.max(256, rate * BUFFER_MS / 1000) * PcmCodec.frameBytes(channels);
	}

	/**
	 * The mixer for {@code device} that has lines of {@code kind}: the one named exactly (any case), else the first whose
	 * name or description holds it. Null for "default" (the system's own). Throws if there is none.
	 */
	private static Mixer.Info find(String device, Class<?> kind) throws IOException {
		if(device.isEmpty() || device.equalsIgnoreCase(InterfaceSpec.DEFAULT_DEVICE))
			return null;
		String wanted = device.toLowerCase(Locale.ROOT);
		Mixer.Info partial = null;
		for(Mixer.Info info : AudioSystem.getMixerInfo()) {
			if(!AudioSystem.getMixer(info).isLineSupported(new javax.sound.sampled.Line.Info(kind)))
				continue;
			if(info.getName().equalsIgnoreCase(device))
				return info;
			if(partial == null && (info.getName().toLowerCase(Locale.ROOT).contains(wanted) || info.getDescription().toLowerCase(Locale.ROOT).contains(wanted)))
				partial = info;
		}
		if(partial == null)
			throw new NotFoundException("no sound card " + (kind == TargetDataLine.class ? "input" : "output") + " named '" + device + "'");
		return partial;
	}

	private static List<String> names(Class<?> kind) {
		List<String> out = new ArrayList<>();
		for(Mixer.Info info : AudioSystem.getMixerInfo())
			if(AudioSystem.getMixer(info).isLineSupported(new javax.sound.sampled.Line.Info(kind)))
				out.add(info.getName() + (info.getDescription().isEmpty() ? "" : " (" + info.getDescription() + ")"));
		return out;
	}

	@Override
	public List<String> inputs() {
		return names(TargetDataLine.class);
	}

	@Override
	public List<String> outputs() {
		return names(SourceDataLine.class);
	}

	@Override
	public Port openPort(String name) throws IOException {
		try {
			return (Port)Class.forName(SERIAL_CLASS).getConstructor(String.class).newInstance(name);
		}
		catch(InvocationTargetException e) {
			if(e.getCause() instanceof IOException io)
				throw io;
			throw new IOException("serial port " + name + ": " + e.getCause(), e.getCause());
		}
		catch(ReflectiveOperationException | LinkageError e) {
			throw new IOException("serial ports are not available (jSerialComm did not load): " + e, e);
		}
	}

	@Override
	public List<String> ports() {
		try {
			List<?> raw = (List<?>)Class.forName(SERIAL_CLASS).getMethod("list").invoke(null);
			return raw.stream().map(String::valueOf).toList();
		}
		catch(ReflectiveOperationException | LinkageError e) {
			return List.of("(serial ports are not available: " + e + ")");
		}
	}

	/** A capture line read 16-bit, any channel count, as mono. */
	private static final class LineInput implements Input {
		private final TargetDataLine line;
		private final int channels;
		private byte[] bytes = new byte[0];

		LineInput(TargetDataLine line, int channels) {
			this.line = line;
			this.channels = channels;
		}

		@Override
		public int read(short[] into, int max) throws IOException {
			int want = Math.min(max, into.length) * PcmCodec.frameBytes(channels);
			if(bytes.length < want)
				bytes = new byte[want];
			if(!line.isOpen())
				throw new IOException("the input closed");
			int got = line.read(bytes, 0, want);
			return PcmCodec.decode(bytes, got, channels, false, into);
		}

		@Override
		public void close() {
			line.stop();
			line.flush();
			line.close();
		}
	}

	/** A playback line written 16-bit, the mono sample on every channel. */
	private static final class LineOutput implements Output {
		private final SourceDataLine line;
		private final int channels;
		private byte[] bytes = new byte[0];

		LineOutput(SourceDataLine line, int channels) {
			this.line = line;
			this.channels = channels;
		}

		@Override
		public void write(short[] pcm, int count) throws IOException {
			int need = count * PcmCodec.frameBytes(channels);
			if(bytes.length < need)
				bytes = new byte[need];
			if(!line.isOpen())
				throw new IOException("the output closed");
			int length = PcmCodec.encode(pcm, count, channels, false, bytes);
			line.write(bytes, 0, length);
		}

		@Override
		public void close() {
			line.drain();
			line.stop();
			line.close();
		}
	}
}
