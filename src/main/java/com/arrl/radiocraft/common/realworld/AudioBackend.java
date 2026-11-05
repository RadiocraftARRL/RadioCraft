package com.arrl.radiocraft.common.realworld;

import java.io.IOException;
import java.util.List;

/**
 * Where a Real-World Interface's sound card and serial port come from: the server's own hardware
 * ({@link JavaSoundBackend}: {@code javax.sound.sampled} and jSerialComm), or a stand-in for the GameTests. Every call
 * may block (opening a device, reading a frame), so only the interface's own threads ({@link Bridge}) make them, never
 * the server thread. Audio is 16-bit mono at the rate asked for; a backend converts channels itself.
 */
public interface AudioBackend {

	/** The serial port's modem control lines: RTS and DTR are outputs (PTT), CTS and DSR inputs (COS). */
	enum Line { RTS, DTR, CTS, DSR }

	/** Thrown when no device or port has the name asked for (as opposed to one that is there but will not open). */
	class NotFoundException extends IOException {
		private static final long serialVersionUID = 1L;

		public NotFoundException(String message) {
			super(message);
		}
	}

	/** A sound card input, open and running. */
	interface Input extends AutoCloseable {
		/**
		 * Reads up to {@code max} samples into {@code into}, blocking until they are there (about {@code max / rate}
		 * seconds). Returns how many were read, 0 or more.
		 */
		int read(short[] into, int max) throws IOException;

		@Override
		void close();
	}

	/** A sound card output, open and running. */
	interface Output extends AutoCloseable {
		/** Plays {@code count} samples of {@code pcm}, blocking while the device's small buffer is full. */
		void write(short[] pcm, int count) throws IOException;

		@Override
		void close();
	}

	/** An open serial port, used only for its control lines. */
	interface Port extends AutoCloseable {
		/** Drives output line {@code line} (RTS or DTR) high ({@code on}) or low. */
		void set(Line line, boolean on);

		/** The state of line {@code line}; false if it cannot be read. */
		boolean get(Line line);

		@Override
		void close();
	}

	/** Opens the sound card input whose name holds {@code device} ("default" for the system's), at {@code rate} Hz. */
	Input openInput(String device, int rate) throws IOException;

	/** Opens the sound card output whose name holds {@code device} ("default" for the system's), at {@code rate} Hz. */
	Output openOutput(String device, int rate) throws IOException;

	/** Opens serial port {@code name}. */
	Port openPort(String name) throws IOException;

	/** The names of the sound card inputs there are, for {@code /radiocraft realworld devices}. */
	List<String> inputs();

	/** The names of the sound card outputs there are. */
	List<String> outputs();

	/** The serial ports there are. */
	List<String> ports();
}
