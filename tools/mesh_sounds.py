"""Writes the fire alarm and SCADA sounds (2026-11-10), as tools/dsc_sounds.py writes the DSC ones:
  sounds/mesh_chirp.ogg          a fire alarm box's or SCADA device's data burst: 0.12 s of 1200/2200 Hz FSK, quiet,
                                 much shorter than an APRS burst
  sounds/fire_alarm_buzzer.ogg   a controller's trouble buzzer: one 0.25 s buzz (a 400 Hz square, softened)
  sounds/fire_alarm_printer.ogg  a controller's dot-matrix printer: a line of short needle bursts, then a paper feed
  sounds/fire_bell.ogg           a fire bell: half a second of the striker on the gong, 18 strikes a second
Small mono files at 22050 Hz with soft edges so they do not click. Needs numpy and soundfile.
Run from the repository root:  python tools/mesh_sounds.py
"""
import numpy as np
import soundfile as sf

RATE = 22050
OUT = 'src/main/resources/assets/radiocraft/sounds/'
rng = np.random.default_rng(1)


def soften(wave, edge_s=0.006):
    edge = int(RATE * edge_s)
    ramp = np.linspace(0.0, 1.0, edge)
    wave[:edge] *= ramp
    wave[-edge:] *= ramp[::-1]
    return wave


def fsk(seconds, baud=1200, level=0.35):
    n = int(RATE * seconds)
    bits = rng.integers(0, 2, int(seconds * baud) + 1)
    t = np.arange(n)
    f = np.where(bits[(t * baud) // RATE] == 1, 1200.0, 2200.0)
    wave = np.sin(2 * np.pi * np.cumsum(f) / RATE) * level
    return soften(wave.astype(np.float32))


def buzz(seconds, freq=400.0, level=0.8):
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    square = np.sign(np.sin(2 * np.pi * freq * t))
    # Soften the square with a short moving average so it buzzes rather than rasps.
    kernel = np.ones(6) / 6
    wave = np.convolve(square, kernel, mode='same') * level
    return soften(wave.astype(np.float32), 0.01)


def bell(seconds=0.5, strikes_per_s=18.0, level=0.7):
    """A fire bell (2026-11-11): the striker hitting a gong 18 times a second. A gong's partials are not harmonic; each
    strike rings them up, they decay between strikes, and the clip is a whole number of strikes so it repeats evenly."""
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    partials = ((1180.0, 1.0, 9.0), (2440.0, 0.55, 14.0), (3260.0, 0.35, 20.0), (4710.0, 0.2, 28.0))
    period = 1.0 / strikes_per_s
    since = t % period                       # time since the last strike
    wave = np.zeros(n)
    for f, a, decay in partials:
        wave += a * np.sin(2 * np.pi * f * t) * np.exp(-decay * since * 6)
    click = np.exp(-since * 900) * rng.normal(0, 0.4, n)  # the striker's tick
    wave = (wave * 0.8 + click) * level / 1.6
    return soften(wave.astype(np.float32), 0.003)


def printer(level=0.35):
    parts = []
    for _ in range(14):  # Needle bursts across a line.
        burst = rng.normal(0, 1, int(RATE * 0.018)) * np.exp(-np.linspace(0, 6, int(RATE * 0.018)))
        parts.append(burst)
        parts.append(np.zeros(int(RATE * rng.uniform(0.008, 0.02))))
    feed_n = int(RATE * 0.12)  # The paper feed: a low ratchet.
    t = np.arange(feed_n) / RATE
    feed = (np.sign(np.sin(2 * np.pi * 60 * t)) * 0.3 + rng.normal(0, 0.15, feed_n)) * np.linspace(1.0, 0.2, feed_n)
    parts.append(feed)
    wave = np.concatenate(parts) * level
    return soften(wave.astype(np.float32))


def write(name, wave):
    with sf.SoundFile(OUT + name, 'w', samplerate=RATE, channels=1, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(wave), 4096):  # In blocks: one big write overflows libsndfile's Vorbis encoder stack.
            f.write(wave[i:i + 4096])
    print('wrote', OUT + name, len(wave) / RATE, 's')


write('mesh_chirp.ogg', fsk(0.12))
write('fire_alarm_buzzer.ogg', buzz(0.25))
write('fire_alarm_printer.ogg', printer())
write('fire_bell.ogg', bell())
