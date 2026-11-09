"""Writes sounds/static_cw.ogg (2026-11-09): the HF static through a CW filter, about 500 Hz wide round a 700 Hz note,
as an HF radio in CW hears the band. Filtered in the frequency domain over the whole loop, which keeps it seamless.
It is quieter than the wide static (a narrow filter lets less noise through)."""
import numpy as np
import soundfile as sf

SRC = 'src/main/resources/assets/radiocraft/sounds/static.ogg'
OUT = 'src/main/resources/assets/radiocraft/sounds/static_cw.ogg'
CENTRE, WIDTH, EDGE = 700.0, 500.0, 120.0  # Hz: passband and the soft skirt either side.
LEVEL = 0.45  # RMS against the wide static.

data, rate = sf.read(SRC, always_2d=True)
mono = data.mean(axis=1)
spectrum = np.fft.rfft(mono)
freqs = np.fft.rfftfreq(len(mono), 1.0 / rate)
lo, hi = CENTRE - WIDTH / 2, CENTRE + WIDTH / 2
gain = np.clip(np.minimum((freqs - (lo - EDGE)) / EDGE, ((hi + EDGE) - freqs) / EDGE), 0.0, 1.0)
gain = 0.5 - 0.5 * np.cos(np.pi * gain)  # Raised-cosine skirts.
filtered = np.fft.irfft(spectrum * gain, n=len(mono))
filtered *= LEVEL * np.sqrt(np.mean(mono ** 2)) / max(1e-9, np.sqrt(np.mean(filtered ** 2)))
peak = np.max(np.abs(filtered))
if peak > 0.98:
    filtered *= 0.98 / peak
out = filtered.astype(np.float32)
with sf.SoundFile(OUT, 'w', samplerate=rate, channels=1, format='OGG', subtype='VORBIS') as f:
    for i in range(0, len(out), 4096):  # In blocks: one big write overflows libsndfile's Vorbis encoder stack.
        f.write(out[i:i + 4096])
print('wrote', OUT, len(filtered) / rate, 's at', rate)
