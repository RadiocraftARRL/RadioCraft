package com.arrl.radiocraft.common.realworld;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A serial port's control lines through jSerialComm (Apache 2.0 / LGPL 3, Maven Central {@code com.fazecast:jSerialComm}),
 * the usual way a ham rig interface keys PTT (RTS or DTR) and reports COS (CTS or DSR). The only class that names
 * jSerialComm: {@link JavaSoundBackend} loads it by name, so the library (and the native code it unpacks into the
 * temp folder the first time {@link SerialPort} is touched) never loads while the feature is off. No data is sent on
 * the port; its speed does not matter.
 */
public final class SerialControlPort implements AudioBackend.Port {

	private final SerialPort port;

	/** Opens {@code name} (COM3, /dev/ttyUSB0, or a port's descriptive name). Called by reflection. */
	public SerialControlPort(String name) throws IOException {
		SerialPort found;
		try {
			found = SerialPort.getCommPort(name);
		}
		catch(SerialPortInvalidPortException e) {
			throw new AudioBackend.NotFoundException("no serial port named '" + name + "'");
		}
		if(!found.openPort()) {
			if(!exists(name))
				throw new AudioBackend.NotFoundException("no serial port named '" + name + "'");
			throw new IOException("serial port " + name + " will not open (in use by another program, or no permission)");
		}
		port = found;
	}

	private static boolean exists(String name) {
		for(SerialPort each : SerialPort.getCommPorts())
			if(name.equalsIgnoreCase(each.getSystemPortName()) || name.equalsIgnoreCase(each.getSystemPortPath()) || name.equalsIgnoreCase(each.getDescriptivePortName()))
				return true;
		return false;
	}

	/** Every serial port there is, as "COM3 (USB Serial Port)". Called by reflection. */
	public static List<String> list() {
		List<String> out = new ArrayList<>();
		for(SerialPort each : SerialPort.getCommPorts())
			out.add(each.getSystemPortName() + " (" + each.getDescriptivePortName() + ")");
		return out;
	}

	@Override
	public void set(AudioBackend.Line line, boolean on) {
		switch(line) {
			case RTS -> {
				if(on)
					port.setRTS();
				else
					port.clearRTS();
			}
			case DTR -> {
				if(on)
					port.setDTR();
				else
					port.clearDTR();
			}
			case CTS, DSR -> {} // Inputs.
		}
	}

	@Override
	public boolean get(AudioBackend.Line line) {
		return switch(line) {
			case CTS -> port.getCTS();
			case DSR -> port.getDSR();
			case RTS -> port.getRTS();
			case DTR -> port.getDTR();
		};
	}

	@Override
	public void close() {
		port.closePort();
	}
}
