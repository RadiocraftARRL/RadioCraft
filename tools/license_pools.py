"""Builds RadioCraft's License Study question pools (data/radiocraft/license_pools/*.json) from the NCVEC pools.

The question text is the NCVEC Question Pool Committee's, public domain. The JSON it reads is russolsen's
ham_radio_question_pool (https://github.com/russolsen/ham_radio_question_pool, Apache-2.0 packaging):
    python -I tools/license_pools.py <path to a clone of ham_radio_question_pool>
Writes technician.json, general.json and extra.json in RadioCraft's own pool format (see the Handbook entry
"License Study" or doc/Credits-QuestionPools.md). Figure questions keep their "figure" name; the game skips them.
"""
import json
import os
import sys

POOLS = [
    {
        'id': 'technician', 'source': 'technician-2026-2030', 'name': 'Technician', 'element': 'Element 2',
        'valid': 'July 1, 2026 to June 30, 2030', 'order': 1,
        'subelements': {
            'T0': 'Safety', 'T1': "Commission's rules", 'T2': 'Operating procedures',
            'T3': 'Radio wave propagation', 'T4': 'Amateur radio practices and station setup',
            'T5': 'Electrical principles', 'T6': 'Electronic and electrical components',
            'T7': 'Station equipment', 'T8': 'Modulation modes', 'T9': 'Antennas and feed lines',
        },
    },
    {
        'id': 'general', 'source': 'general-2023-2027', 'name': 'General', 'element': 'Element 3',
        'valid': 'July 1, 2023 to June 30, 2027', 'order': 2,
        'subelements': {
            'G0': 'Electrical and RF safety', 'G1': "Commission's rules", 'G2': 'Operating procedures',
            'G3': 'Radio wave propagation', 'G4': 'Amateur radio practices', 'G5': 'Electrical principles',
            'G6': 'Circuit components', 'G7': 'Practical circuits', 'G8': 'Signals and emissions',
            'G9': 'Antennas and feed lines',
        },
    },
    {
        'id': 'extra', 'source': 'extra-2024-2028', 'name': 'Amateur Extra', 'element': 'Element 4',
        'valid': 'July 1, 2024 to June 30, 2028', 'order': 3,
        'subelements': {
            'E0': 'Safety', 'E1': "Commission's rules", 'E2': 'Operating procedures',
            'E3': 'Radio wave propagation', 'E4': 'Amateur practices', 'E5': 'Electrical principles',
            'E6': 'Circuit components', 'E7': 'Practical circuits', 'E8': 'Signals and emissions',
            'E9': 'Antennas and transmission lines',
        },
    },
]

# Typography the computer's text-mode font does not have, folded to ASCII.
FOLD = {'\u2018': "'", '\u2019': "'", '\u201c': '"', '\u201d': '"', '\u2013': '-', '\u2014': '-', '\u00b0': ' deg',
        '\u03a9': ' ohm', '\u00b5': 'u', '\u03bc': 'u', '\u00bd': '1/2', '\u00bc': '1/4', '\u00be': '3/4', '\u2026': '...',
        '\u00d7': 'x', '\u00b1': '+/-', '\u2264': '<=', '\u2265': '>=', '\u221a': 'sqrt', '\u03c0': 'pi', '\u03b8': 'theta',
        '\u03bb': 'lambda', '\u00b2': '^2', '\u00b3': '^3', '\u00a0': ' ', '\u03c6': 'phi', '\u2212': '-', '\u03b1': 'alpha',
        '\u03b2': 'beta', '\u0394': 'delta', '\u00e9': 'e'}


def fold(text):
    out = ''.join(FOLD.get(c, c) for c in text.strip())
    bad = [c for c in out if ord(c) > 126 or ord(c) < 32]
    if bad:
        raise SystemExit('unmapped characters %r in %r' % (bad, text))
    return out


def main():
    root = sys.argv[1]
    here = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(here, '..', 'src', 'main', 'resources', 'data', 'radiocraft', 'license_pools')
    for pool in POOLS:
        with open(os.path.join(root, pool['source'], pool['source'] + '.json'), encoding='utf-8') as f:
            source = json.load(f)
        questions = []
        for q in source:
            entry = {'id': q['id'], 'question': fold(q['question']), 'answers': [fold(a) for a in q['answers']],
                     'correct': q['correct_letter']}
            if q.get('refs'):
                entry['refs'] = fold(q['refs'])
            if q.get('figure'):
                entry['figure'] = q['figure'].split('.')[0].upper()
            questions.append(entry)
        data = {'id': pool['id'], 'name': pool['name'], 'element': pool['element'], 'valid': pool['valid'],
                'pass_percent': 74, 'order': pool['order'], 'subelements': pool['subelements'], 'questions': questions}
        with open(os.path.join(out_dir, pool['id'] + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, indent=1, ensure_ascii=True)
            f.write('\n')
        print(pool['id'], len(questions))


if __name__ == '__main__':
    main()
