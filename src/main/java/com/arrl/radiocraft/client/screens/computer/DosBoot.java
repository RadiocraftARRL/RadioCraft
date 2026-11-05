package com.arrl.radiocraft.client.screens.computer;

import com.arrl.radiocraft.common.init.RadiocraftSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * The radio computer starting up, in text mode before the shell: the BIOS power-on self test (a memory count ticking
 * up, the drives found) and RC-DOS loading, about four seconds. Any key or click skips it. A computer opened again
 * while its session is kept does not boot.
 */
final class DosBoot {

	private static final long MEMORY_END = 1300L, DRIVES_END = 2600L, DOS_END = 3600L, DONE = 4200L;
	private static final int MEMORY_KB = 8192;

	private final long start = System.currentTimeMillis();
	private boolean skipped = false;
	private boolean beeped = false;
	private long nextTick = 0L;

	boolean running() {
		return !skipped && elapsed() < DONE;
	}

	void skip() {
		skipped = true;
	}

	private long elapsed() {
		return System.currentTimeMillis() - start;
	}

	void render(TextGrid g, String serial, String drive) {
		long t = elapsed();
		g.clear(DosColours.LIGHT_GREY, DosColours.BLACK);
		if(!beeped) { // The POST beep: all is well.
			beeped = true;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 2.0F, 0.25F));
		}
		int row = 0;
		g.put(0, row++, Dos.tr("boot.bios"), DosColours.WHITE, DosColours.BLACK);
		g.put(0, row++, Dos.tr("boot.copyright"), DosColours.LIGHT_GREY, DosColours.BLACK);
		row++;
		g.put(0, row++, Dos.tr("boot.cpu"), DosColours.LIGHT_GREY, DosColours.BLACK);
		long kb = Math.min(MEMORY_KB, t * MEMORY_KB / MEMORY_END);
		g.put(0, row++, Dos.tr("boot.memory", kb, kb >= MEMORY_KB ? Dos.tr("boot.ok") : ""), DosColours.LIGHT_GREY, DosColours.BLACK);
		if(kb < MEMORY_KB && t >= nextTick) { // The count ticking.
			nextTick = t + 90L;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(RadiocraftSoundEvents.H19_TYPE.get(), 1.8F, 0.08F));
		}
		if(t >= MEMORY_END) {
			row++;
			g.put(0, row++, Dos.tr("boot.floppy", drive), DosColours.LIGHT_GREY, DosColours.BLACK);
			if(t >= MEMORY_END + 500L)
				g.put(0, row++, Dos.tr("boot.disk", serial), DosColours.LIGHT_GREY, DosColours.BLACK);
		}
		if(t >= DRIVES_END) {
			row++;
			g.put(0, row++, Dos.tr("boot.starting"), DosColours.LIGHT_GREY, DosColours.BLACK);
		}
		if(t >= DOS_END) {
			row++;
			g.put(0, row++, Dos.tr("boot.version"), DosColours.LIGHT_GREY, DosColours.BLACK);
			g.put(0, row, "C:\\>SHELL", DosColours.LIGHT_GREY, DosColours.BLACK);
			g.cursor(9, row);
		}
		else
			g.cursor(0, row);
	}
}
