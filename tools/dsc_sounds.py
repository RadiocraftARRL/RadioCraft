"""Writes the distress alerting sounds (2026-11-09), as tools/cw_static.py writes its static:
  sounds/dsc_alarm.ogg  the DSC distress alarm a marine radio sounds when it hears a distress call: the two-tone
                        warble real DSC radios use (2100 and 1300 Hz, swapping eight times a second), two seconds of it
  sounds/dsc_beep.ogg   one short 2 kHz beep: the countdown while the DISTRESS button is held, and a burst going out
Small mono files at 22050 Hz with soft edges so they do not click. Needs numpy and soundfile.
Run from the repository root:  python tools/dsc_sounds.py
"""
import numpy as np
import soundfile as sf

RATE = 22050
OUT = 'src/main/resources/assets/radiocraft/sounds/'


def tone(freqs, seconds, swap_hz=0.0, level=0.5):
    """A tone; with swap_hz it alternates between the frequencies that many times a second, phase-continuous."""
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    if swap_hz > 0:
        which = (np.floor(t * swap_hz).astype(int)) % len(freqs)
        f = np.array(freqs, dtype=float)[which]
    else:
        f = np.full(n, float(freqs[0]))
    phase = 2 * np.pi * np.cumsum(f) / RATE
    wave = np.sin(phase) * level
    edge = int(RATE * 0.008)  # 8 ms fades in and out.
    ramp = np.linspace(0.0, 1.0, edge)
    wave[:edge] *= ramp
    wave[-edge:] *= ramp[::-1]
    return wave.astype(np.float32)


def write(name, wave):
    with sf.SoundFile(OUT + name, 'w', samplerate=RATE, channels=1, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(wave), 4096):  # In blocks: one big write overflows libsndfile's Vorbis encoder stack.
            f.write(wave[i:i + 4096])
    print('wrote', OUT + name, len(wave) / RATE, 's')


write('dsc_alarm.ogg', tone([2100, 1300], 2.0, swap_hz=8.0, level=0.45))
write('dsc_beep.ogg', tone([2000], 0.12, level=0.4))
