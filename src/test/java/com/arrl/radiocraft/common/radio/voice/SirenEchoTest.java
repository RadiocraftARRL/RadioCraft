package com.arrl.radiocraft.common.radio.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SirenEchoTest {

    private static final int RATE = 48000;

    @Test
    void aClickComesBackAtEachTap() {
        SirenEcho echo = new SirenEcho(RATE);
        short[] frame = new short[960];
        frame[0] = 10000;
        short[] heard = new short[RATE];
        for(int f = 0; f < RATE / 960; f++) {
            short[] out = echo.process(f == 0 ? frame : new short[960]);
            System.arraycopy(out, 0, heard, f * 960, 960);
        }
        assertEquals(10000, heard[0], "the dry signal is untouched");
        for(int i = 0; i < SirenEcho.TAPS.length; i++) {
            int at = (int)Math.round(SirenEcho.TAPS[i] * RATE);
            assertTrue(heard[at] > 0 && heard[at] < 10000, "tap " + i + " comes back quieter: " + heard[at]);
        }
        assertEquals(0, heard[RATE / 10], "silence between echoes");
    }

    @Test
    void loudInputDoesNotWrap() {
        SirenEcho echo = new SirenEcho(RATE);
        short[] loud = new short[960];
        java.util.Arrays.fill(loud, Short.MAX_VALUE);
        for(int f = 0; f < 100; f++)
            for(short s : echo.process(loud))
                assertTrue(s > 0, "clipped, not wrapped round");
    }
}
