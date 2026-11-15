"""Writes PLACEHOLDER block states and models for the fire alarm and SCADA blocks (2026-11-10): plain boxes in vanilla
textures, until the maintainer's models are made (see doc/ModelDesignBounty.md). Each model faces north and the block
states turn it by FACING. Run from the repository root:  python tools/mesh_placeholder_models.py
Delete a block's lines here when its real model goes in, so this does not overwrite it.
"""
import json
import os

ASSETS = 'src/main/resources/assets/radiocraft/'
CREDIT = 'RadioCraft placeholder, made by tools/mesh_placeholder_models.py (see doc/ModelDesignBounty.md)'
ROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def cube(frm, to, tex, name):
    faces = {f: {'texture': '#' + tex} for f in ('north', 'east', 'south', 'west', 'up', 'down')}
    return {'name': name, 'from': frm, 'to': to, 'faces': faces}


def model(name, textures, elements):
    data = {'credit': CREDIT, 'parent': 'block/block', 'textures': dict(textures, particle=list(textures.values())[0]), 'elements': elements}
    with open(ASSETS + 'models/block/' + name + '.json', 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def item(name, parent=None, layer=None):
    data = {'parent': parent} if parent else {'parent': 'minecraft:item/generated', 'textures': {'layer0': layer}}
    with open(ASSETS + 'models/item/' + name + '.json', 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def states(name, variants):
    """variants: list of (extra conditions dict, model name); every facing gets each."""
    parts = []
    for when, model_name in variants:
        for facing, y in ROT.items():
            apply = {'model': 'radiocraft:block/' + model_name}
            if y:
                apply['y'] = y
            parts.append({'when': dict(when, facing=facing), 'apply': apply})
    with open(ASSETS + 'blockstates/' + name + '.json', 'w', encoding='utf-8') as f:
        json.dump({'multipart': parts}, f, indent=2)
        f.write('\n')


def wiring_parts(name, extra=None, pieces=None):
    """Multipart entries for a wall device's wire pieces (tools/fire_alarm_assets.py), turned by facing; extra is
    added to every condition (a panic button's face=wall)."""
    extra = extra or {}
    parts = []
    for facing, y in ROT.items():
        def part(model, whens):
            apply = {'model': 'radiocraft:block/%s_%s' % (pieces or name, model)}
            if y:
                apply['y'] = y
            whens = [dict(w, facing=facing, **extra) for w in whens]
            parts.append({'when': whens[0] if len(whens) == 1 else {'OR': whens}, 'apply': apply})
        part('wire_up', [{'wire_up': 'true'}])
        part('wire_down', [{'wire_down': 'true'}, {'wire_cw': 'floor'}, {'wire_ccw': 'floor'}])
        for side in ('cw', 'ccw'):
            part('wire_%s_wall' % side, [{'wire_' + side: 'wall'}])
            part('wire_%s_floor' % side, [{'wire_' + side: 'floor'}])
    return parts


def dump_states(name, parts):
    with open(ASSETS + 'blockstates/' + name + '.json', 'w', encoding='utf-8') as f:
        json.dump({'multipart': parts}, f, indent=2)
        f.write(chr(10))


def wired_states(name, variants, pieces=None):
    """states() with the wire pieces added (another device's, {pieces}, when they are the same shape)."""
    parts = []
    for when, model_name in variants:
        for facing, y in ROT.items():
            apply = {'model': 'radiocraft:block/' + model_name}
            if y:
                apply['y'] = y
            parts.append({'when': dict(when, facing=facing), 'apply': apply})
    dump_states(name, parts + wiring_parts(name, pieces=pieces))


def panic_states():
    """The panic button (2026-11-11): stood on a wall (its own model, turned by facing), on a floor, or under a desk
    (the floor model turned over, as vanilla's button); and its wire pieces."""
    parts = []
    for facing, y in ROT.items():
        for pressed in ('false', 'true'):
            suffix = '_pressed' if pressed == 'true' else ''
            for face in ('wall', 'floor', 'ceiling'):
                apply = {'model': 'radiocraft:block/panic_button' + ('_wall' if face == 'wall' else '') + suffix}
                rot = (y + 180) % 360 if face == 'ceiling' else y
                if face == 'ceiling':
                    apply['x'] = 180
                if rot:
                    apply['y'] = rot
                parts.append({'when': {'face': face, 'facing': facing, 'pulled': pressed}, 'apply': apply})
    parts += wiring_parts('panic_button', {'face': 'wall'})
    for face in ('floor', 'ceiling'):
        for facing, y in ROT.items():
            rot = (y + 180) % 360 if face == 'ceiling' else y
            for model, when in (('floor_wire_front', {'wire_up': 'true'}), ('floor_wire_back', {'wire_down': 'true'}),
                                ('floor_wire_cw', {'wire_cw': 'floor'}), ('floor_wire_ccw', {'wire_ccw': 'floor'})):
                apply = {'model': 'radiocraft:block/panic_button_' + model}
                if face == 'ceiling':
                    apply['x'] = 180
                if rot:
                    apply['y'] = rot
                parts.append({'when': dict(when, face=face, facing=facing), 'apply': apply})
    dump_states('panic_button', parts)


RED = {'0': 'minecraft:block/red_concrete'}

# Fire alarm box and pull station: real models from tools/fire_alarm_assets.py (2026-11-11); only their block states here.
wired_states('fire_alarm_box', [({}, 'fire_alarm_box'), ({'lit': 'true'}, 'fire_alarm_box_lamp')])
wired_states('pull_station', [({'pulled': 'false'}, 'pull_station'), ({'pulled': 'true'}, 'pull_station_pulled')])
for colour in ('yellow', 'white'):  # The yellow and white ones (2026-11-11): the red one's wire pieces, their own models.
    wired_states(colour + '_pull_station', [({'pulled': 'false'}, colour + '_pull_station'), ({'pulled': 'true'}, colour + '_pull_station_pulled')],
                 pieces='pull_station')
    states(colour + '_outdoor_pull_box', [({'pulled': 'false'}, colour + '_outdoor_pull_box'), ({'pulled': 'true'}, colour + '_outdoor_pull_box_pulled')])

# Outdoor pull box: the real model from tools/fire_alarm_assets.py (2026-11-11); its block states, turned by facing.
states('fire_bell', [({}, 'fire_bell')])  # The fire bell (2026-11-11), from tools/fire_alarm_assets.py: rings when powered, same look.
states('outdoor_pull_box', [({'pulled': 'false'}, 'outdoor_pull_box'), ({'pulled': 'true'}, 'outdoor_pull_box_pulled')])

# Controller: the RC-704 receiver from tools/fire_alarm_controller_assets.py (2026-11-11); only its block states here.
states('fire_alarm_controller', [({'alarm': 'false'}, 'fire_alarm_controller'), ({'alarm': 'true'}, 'fire_alarm_controller_alarm')])

# SCADA RTU and the two-block master: real models from tools/scada_assets.py (2026-11-11); only their block states here.
states('scada_rtu', [({}, 'scada_rtu')])
states('scada_master', [({'part': 'panel'}, 'scada_master'), ({'part': 'screen'}, 'scada_master_screen')])


def button_states(name):
    """A block on a floor, wall or ceiling, turned as vanilla's stone button is; its model drawn lying on the floor."""
    variants = {}
    for face, x in (('floor', 0), ('wall', 90), ('ceiling', 180)):
        for facing, y in ROT.items():
            if face == 'ceiling':
                y = (y + 180) % 360
            for pressed in ('false', 'true'):
                apply = {'model': 'radiocraft:block/' + name + ('_pressed' if pressed == 'true' else '')}
                if x:
                    apply['x'] = x
                if y:
                    apply['y'] = y
                if face == 'wall':
                    apply['uvlock'] = True
                variants['face=%s,facing=%s,pulled=%s' % (face, facing, pressed)] = apply
    with open(ASSETS + 'blockstates/' + name + '.json', 'w', encoding='utf-8') as f:
        json.dump({'variants': variants}, f, indent=2)
        f.write('\n')


# Panic button (2026-11-11): model from tools/fire_alarm_assets.py.
panic_states()

# Items.
item('fire_alarm_key', layer='minecraft:item/trial_key')
item('fire_alarm_report', layer='minecraft:item/paper')
print('wrote the fire alarm and SCADA placeholder models')
