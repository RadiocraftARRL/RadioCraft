"""Makes the Simple Weather Alert Receiver's sounds (1.12 weather alerts; see doc/Credits-WeatherVoice.md):

- weather_alert/tone.ogg: the attention tone, a steady 1050 Hz sine for 5 s, the classic weather radio alarm.
- weather_alert/word_<id>.ogg: one clip per word or phrase of the announcement, spoken by Piper TTS with the
  en_US "ljspeech" (high) voice. The receiver splices them in game as the real automated weather voice does
  (common/radio/wxalert/WeatherVoiceClip lists them with their lengths).

The voice: Piper (https://github.com/OHF-Voice/piper1-gpl, GPL-3.0) with the voice model en_US-ljspeech-high from
https://huggingface.co/rhasspy/piper-voices (MIT), trained on the LJ Speech dataset (public domain). Nothing in the
clips is under a licence that stops them being shipped in a GPLv3 mod.

Needs Python 3.10+ with numpy, soundfile (libsndfile with Ogg Vorbis) and piper-tts:

    python -m venv venv && venv/Scripts/python -m pip install piper-tts numpy soundfile
    (download en_US-ljspeech-high.onnx and .onnx.json from the piper-voices repository, en/en_US/ljspeech/high)
    venv/Scripts/python tools/weather_voice.py --model path/to/en_US-ljspeech-high.onnx

--tone-only makes the tone without Piper. The script prints the clips' lengths as Java enum entries for
WeatherVoiceClip; paste them in if the words change.

--station (2026-11-14) renders the FM broadcast station's extra words (STATION_WORDS: forecasts, station IDs, letters
for callsigns), only those whose clip does not exist yet, so the weather radio's clips are never touched. The station
reads them on the server (common/radio/station/StationVoice; its VOCABULARY lists the same ids). A local venv:

    python -m venv tools/.venv && tools/.venv/Scripts/python -m pip install piper-tts numpy soundfile
    (the model in tools/.venv/voices; both are git-ignored)
    tools/.venv/Scripts/python tools/weather_voice.py --station --model tools/.venv/voices/en_US-ljspeech-high.onnx
"""
import argparse
import os
import wave
import io

import numpy as np
import soundfile as sf

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/radiocraft/sounds/weather_alert')

# (id, what is spoken). Ids are the WeatherVoiceClip enum's names in lower case.
WORDS = [
    ('intro', 'Radio craft weather alert.'),
    ('tornado_warning', 'Tornado warning'),
    ('tornado_watch', 'Tornado watch'),
    ('severe_thunderstorm_warning', 'Severe thunderstorm warning'),
    ('severe_thunderstorm_watch', 'Severe thunderstorm watch'),
    ('flash_flood_warning', 'Flash flood warning'),
    ('high_wind_warning', 'High wind warning'),
    ('hurricane_warning', 'Hurricane warning'),
    ('tropical_storm_warning', 'Tropical storm warning'),
    ('blizzard_warning', 'Blizzard warning'),
    ('dust_storm_warning', 'Dust storm warning'),
    ('dense_fog_advisory', 'Dense fog advisory'),
    ('excessive_heat_warning', 'Excessive heat warning'),
    ('extreme_cold_warning', 'Extreme cold warning'),
    ('for_the_area', 'for the area.'),
    ('the_storm_is', 'The storm is'),
    ('over_your_location', 'over your location'),
    ('blocks', 'blocks'),
    ('to_the', 'to the'),
    ('moving', 'moving'),
    ('category', 'category'),
    ('north', 'north'),
    ('northeast', 'northeast'),
    ('east', 'east'),
    ('southeast', 'southeast'),
    ('south', 'south'),
    ('southwest', 'southwest'),
    ('west', 'west'),
    ('northwest', 'northwest'),
    ('take_shelter_now', 'Take shelter now.'),
    ('stay_indoors', 'Stay indoors.'),
    ('move_to_higher_ground', 'Move to higher ground.'),
    ('end_of_message', 'End of message.'),
    ('zero', 'zero'), ('one', 'one'), ('two', 'two'), ('three', 'three'), ('four', 'four'), ('five', 'five'),
    ('six', 'six'), ('seven', 'seven'), ('eight', 'eight'), ('nine', 'nine'), ('ten', 'ten'), ('eleven', 'eleven'),
    ('twelve', 'twelve'), ('thirteen', 'thirteen'), ('fourteen', 'fourteen'), ('fifteen', 'fifteen'),
    ('sixteen', 'sixteen'), ('seventeen', 'seventeen'), ('eighteen', 'eighteen'), ('nineteen', 'nineteen'),
    ('twenty', 'twenty'), ('thirty', 'thirty'), ('forty', 'forty'), ('fifty', 'fifty'), ('sixty', 'sixty'),
    ('seventy', 'seventy'), ('eighty', 'eighty'), ('ninety', 'ninety'), ('hundred', 'hundred'),
    ('thousand', 'thousand'),
]

# The FM broadcast station's words (2026-11-14): forecasts, station IDs, letters for callsigns, and single words so a
# relayed alert's text can be read word by word. Ids are StationVoice's.
STATION_WORDS = [
    ('this_is', 'This is'), ('fm', 'F M'), ('point', 'point'), ('percent', 'percent'), ('degrees', 'degrees'),
    ('minus', 'minus'), ('the_forecast_for', 'The forecast for'), ('today', 'today'), ('tonight', 'tonight'),
    ('tomorrow', 'tomorrow'), ('this_morning', 'this morning'), ('this_afternoon', 'this afternoon'),
    ('this_evening', 'this evening'), ('chance_of', 'chance of'), ('likely', 'likely'), ('rain', 'rain'),
    ('snow', 'snow'), ('hail', 'hail'), ('thunderstorms', 'thunderstorms'), ('showers', 'showers'),
    ('clear', 'clear'), ('sunny', 'sunny'), ('partly_cloudy', 'partly cloudy'), ('cloudy', 'cloudy'),
    ('fog', 'fog'), ('windy', 'windy'), ('heavy', 'heavy'), ('light', 'light'), ('blowing_dust', 'blowing dust'),
    ('dry', 'dry'), ('no_storms', 'no storms'), ('winds_from_the', 'Winds from the'), ('at', 'at'),
    ('kilometers_per_hour', 'kilometers per hour'), ('calm', 'calm'), ('winds_calm', 'Winds calm.'),
    ('currently', 'Currently'), ('high', 'high'), ('low', 'low'), ('and', 'and'), ('then', 'then'),
    ('warning', 'warning'), ('watch', 'watch'), ('advisory', 'advisory'), ('tornado', 'tornado'),
    ('severe', 'severe'), ('thunderstorm', 'thunderstorm'), ('storm', 'storm'), ('hurricane', 'hurricane'),
    ('flood', 'flood'), ('wind', 'wind'), ('heat', 'heat'), ('cold', 'cold'), ('blizzard', 'blizzard'),
    ('the', 'the'), ('for', 'for'), ('area', 'area'), ('is', 'is'), ('in', 'in'), ('your', 'your'),
    ('of', 'of'), ('a', 'a'), ('possible', 'possible'), ('now', 'now'),
    ('letter_a', 'ay'), ('letter_b', 'bee'), ('letter_c', 'see'), ('letter_d', 'dee'), ('letter_e', 'ee'),
    ('letter_f', 'eff'), ('letter_g', 'jee'), ('letter_h', 'aitch'), ('letter_i', 'eye'), ('letter_j', 'jay'),
    ('letter_k', 'kay'), ('letter_l', 'ell'), ('letter_m', 'em'), ('letter_n', 'en'), ('letter_o', 'oh'),
    ('letter_p', 'pee'), ('letter_q', 'cue'), ('letter_r', 'are'), ('letter_s', 'ess'), ('letter_t', 'tee'),
    ('letter_u', 'you'), ('letter_v', 'vee'), ('letter_w', 'double you'), ('letter_x', 'ex'), ('letter_y', 'why'),
    ('letter_z', 'zee'),
]

TONE_HZ = 1050.0
TONE_SECONDS = 5.0
RATE = 22050


def write_ogg(name, samples, rate):
    path = os.path.join(ROOT, name + '.ogg')
    sf.write(path, samples.astype(np.float32), rate, format='OGG', subtype='VORBIS')
    return path


def tone():
    t = np.arange(int(RATE * TONE_SECONDS)) / RATE
    wave_ = 0.5 * np.sin(2 * np.pi * TONE_HZ * t)
    ramp = int(RATE * 0.005)  # 5 ms ends, no click.
    wave_[:ramp] *= np.linspace(0, 1, ramp)
    wave_[-ramp:] *= np.linspace(1, 0, ramp)
    write_ogg('tone', wave_, RATE)


def trim(samples, rate):
    """Cuts the silence Piper leaves at both ends, keeping 20 ms before and 60 ms after the speech."""
    loud = np.nonzero(np.abs(samples) > 0.02)[0]
    if len(loud) == 0:
        return samples
    start = max(0, loud[0] - int(rate * 0.02))
    end = min(len(samples), loud[-1] + int(rate * 0.06))
    return samples[start:end]


def speak(voice, text):
    buffer = io.BytesIO()
    with wave.open(buffer, 'wb') as out:
        voice.synthesize_wav(text, out)
    buffer.seek(0)
    with wave.open(buffer, 'rb') as src:
        rate = src.getframerate()
        pcm = np.frombuffer(src.readframes(src.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0
    return trim(pcm, rate), rate


def words(model):
    from piper import PiperVoice
    voice = PiperVoice.load(model)
    lines = []
    for word, text in WORDS:
        samples, rate = speak(voice, text)
        peak = float(np.max(np.abs(samples))) or 1.0
        samples = samples * (0.85 / peak)  # Every clip at the same loudness.
        write_ogg('word_' + word, samples, rate)
        lines.append('\t%s(%d),' % (word.upper(), round(len(samples) * 1000 / rate)))
    print('\n'.join(lines))


def station_words(model):
    """The station's words whose clips do not exist yet (existing clips are left exactly as they are)."""
    from piper import PiperVoice
    voice = PiperVoice.load(model)
    made = []
    for word, text in STATION_WORDS:
        if os.path.exists(os.path.join(ROOT, 'word_' + word + '.ogg')):
            continue
        samples, rate = speak(voice, text)
        peak = float(np.max(np.abs(samples))) or 1.0
        write_ogg('word_' + word, samples * (0.85 / peak), rate)
        made.append(word)
    print('made %d station clips: %s' % (len(made), ' '.join(made)))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--model', help='the Piper voice, en_US-ljspeech-high.onnx')
    parser.add_argument('--tone-only', action='store_true')
    parser.add_argument('--station', action='store_true', help="only the FM station's new words")
    args = parser.parse_args()
    os.makedirs(ROOT, exist_ok=True)
    if args.station:
        station_words(args.model)
    else:
        tone()
        if not args.tone_only:
            words(args.model)
