"""Blockstates for the switches that go on the floor or a wall (2026-11-13): the antenna switch and the two power
switches. On the floor each is turned by facing (y) as it always was; on a wall it is the floor model tipped up against
the wall (x = 90, then y by facing), as a lever is (see WallMount.java for the side mapping).

The antenna switch's pointer turns to the live output: on the floor by adding 90 degrees a step to its y; on a wall the
blockstate has no second y after x = 90, so the pointer is written already turned (antenna_switch_pointer_r90, _r180,
_r270, from antenna_switch_pointer.json). Run after tools/antenna_switch_model.py:

    python tools/wall_switch_states.py
"""
import copy
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'radiocraft')
STATES = os.path.join(ROOT, 'blockstates')
MODELS = os.path.join(ROOT, 'models', 'block')
FACINGS = [('north', 0), ('east', 90), ('south', 180), ('west', 270)]


def apply(model, face, y, extra_y=0):
    out = {'model': model}
    if face == 'wall':
        out['x'] = 90
    turn = (y + (extra_y if face == 'floor' else 0)) % 360
    if turn:
        out['y'] = turn
    return out


def lever_switch(name, prop, thrown_model):
    variants = {}
    for face in ('floor', 'wall'):
        for facing, y in FACINGS:
            for thrown in (False, True):
                model = 'radiocraft:block/' + (thrown_model if thrown else name)
                variants[f'face={face},facing={facing},{prop}={str(thrown).lower()}'] = apply(model, face, y)
    return {'variants': variants}


def antenna_switch():
    parts = []
    for face in ('floor', 'wall'):
        for facing, y in FACINGS:
            parts.append({'when': {'face': face, 'facing': facing}, 'apply': apply('radiocraft:block/antenna_switch', face, y)})
            for output in (1, 2, 3):
                pointer = 'radiocraft:block/antenna_switch_pointer' + ('' if face == 'floor' else f'_r{90 * output}')
                parts.append({'when': {'face': face, 'facing': facing, 'output': str(output)},
                              'apply': apply(pointer, face, y, extra_y=90 * output)})
    return {'multipart': parts}


def turned(model, quarter_turns):
    """The model turned clockwise (seen from above) about the block's vertical centre line."""
    out = copy.deepcopy(model)
    for e in out['elements']:
        for _ in range(quarter_turns):
            f, t = e['from'], e['to']
            e['from'] = [16 - t[2], f[1], f[0]]
            e['to'] = [16 - f[2], t[1], t[0]]
            faces = e['faces']
            new = {}
            for old, side in (('north', 'east'), ('east', 'south'), ('south', 'west'), ('west', 'north')):
                if old in faces:
                    new[side] = faces[old]
            for flat, by in (('up', 90), ('down', 270)):
                if flat in faces:
                    face = dict(faces[flat])
                    face['rotation'] = (face.get('rotation', 0) + by) % 360
                    if face['rotation'] == 0:
                        del face['rotation']
                    new[flat] = face
            e['faces'] = new
    return out


def save(path, data):
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def main():
    save(os.path.join(STATES, 'power_switch_on_off.json'), lever_switch('power_switch_on_off', 'on', 'power_switch_on_off_on'))
    save(os.path.join(STATES, 'power_switch_left_right.json'), lever_switch('power_switch_left_right', 'right', 'power_switch_left_right_right'))
    save(os.path.join(STATES, 'antenna_switch.json'), antenna_switch())
    with open(os.path.join(MODELS, 'antenna_switch_pointer.json')) as f:
        pointer = json.load(f)
    for n in (1, 2, 3):
        save(os.path.join(MODELS, f'antenna_switch_pointer_r{90 * n}.json'), turned(pointer, n))
    print('ok')


if __name__ == '__main__':
    main()
