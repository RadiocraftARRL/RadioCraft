package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.PictureReception;
import com.arrl.radiocraft.common.radio.data.FileTransfer;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.world.item.Items;

/**
 * The SSTV program's server side (1.12 step 7): slow-scan TV pictures (a map picture, in colour) sent to everyone or
 * a callsign and received line by line, as {@link PictureHandler} does it. A picture takes as long as the radio's SSTV
 * tones for a map ({@link FileTransfer#sstvTicks}, about 26 seconds) and keeps as {@code SSTV0001.IMG}. Captions are
 * written on the screen (the picture is saved as a new file before it is sent), so the server needs nothing for them.
 */
public final class SstvHandler extends PictureHandler {

	@Override
	protected ComputerApp app() {
		return ComputerApps.SSTV;
	}

	@Override
	protected String prefix() {
		return "SSTV";
	}

	@Override
	protected String mode() {
		return FileTransfer.MODE_SSTV;
	}

	/** Any picture going out as SSTV tones: the computer's SSTV and plain pictures (not weather charts). */
	@Override
	protected boolean wants(TncPacket packet) {
		return packet.item().is(Items.FILLED_MAP) && !FileTransfer.MODE_WEFAX.equals(FileTransfer.mode(packet.item()));
	}

	@Override
	protected int airTicks() {
		return FileTransfer.sstvTicks();
	}

	@Override
	protected PictureReception.Clock clock() {
		return PictureReception.Clock.SSTV;
	}

	@Override
	protected int blankRgb() {
		return 0x000000;
	}
}
