"""Builds the iambic keyer's art (2026-11-12), after the maintainer's photo of a Bencher BY-1 paddle: a black wrinkle
base with a label, a round pivot with two levers, two springs back to a post, two adjustment screw posts, and two
triangular finger pieces sticking out of the front.

The finger pieces come in finishes (IambicKeyerBlock.Finish): one parent model whose #paddle texture each finish's
model replaces, so a finish is just a texture. Amber (the photo's) and clear are see-through.

Writes models/block/iambic_keyer.json (the parent), models/block/iambic_keyer_<finish>.json,
models/item/iambic_keyer.json (with an override per finish), blockstates/iambic_keyer.json,
textures/block/iambic_keyer.png and textures/block/keyer_paddle/<finish>.png.

    python tools/keyer_model.py
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/radiocraft')
FINISHES = {
    'amber': (214, 140, 38, 165), 'clear': (225, 238, 242, 80),
    'white': (233, 236, 236, 255), 'orange': (240, 118, 19, 255), 'magenta': (189, 68, 179, 255), 'light_blue': (58, 175, 217, 255),
    'yellow': (248, 197, 39, 255), 'lime': (112, 185, 25, 255), 'pink': (237, 141, 172, 255), 'gray': (62, 68, 71, 255),
    'light_gray': (142, 142, 134, 255), 'cyan': (21, 137, 145, 255), 'purple': (121, 42, 172, 255), 'blue': (53, 57, 157, 255),
    'brown': (114, 71, 40, 255), 'green': (84, 109, 27, 255), 'red': (160, 39, 34, 255), 'black': (20, 21, 25, 255),
    'iron': (214, 216, 218, 255), 'gold': (247, 210, 72, 255), 'copper': (196, 108, 70, 255), 'diamond': (98, 226, 220, 255),
    'emerald': (42, 196, 92, 255), 'lapis': (38, 72, 168, 255), 'amethyst': (150, 100, 200, 255), 'quartz': (236, 230, 220, 255),
    'netherite': (72, 64, 66, 255),
}
SHINY = {'iron', 'gold', 'copper', 'diamond', 'emerald', 'lapis', 'amethyst', 'quartz', 'netherite'}

# The body's texture, 16x16, swatches 4x4: name -> (x0, y0)
SW = {'base': (0, 0), 'metal': (4, 0), 'label': (8, 0), 'dark': (12, 0), 'spring': (0, 4)}
COLOURS = {'base': (30, 31, 36), 'metal': (196, 198, 202), 'label': (236, 236, 230), 'dark': (90, 92, 96), 'spring': (160, 156, 146)}

rnd = random.Random(1410)
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for name, (x0, y0) in SW.items():
    for y in range(y0, y0 + 4):
        for x in range(x0, x0 + 4):
            j = rnd.randint(-7, 7) if name == 'base' else rnd.randint(-4, 4)  # Wrinkle finish on the base.
            img.putpixel((x, y), tuple(max(0, min(255, v + j)) for v in COLOURS[name]) + (255,))
for x in range(9, 11):  # The label's black lettering.
    img.putpixel((x, 1), (40, 40, 40, 255))
img.putpixel((9, 2), (40, 40, 40, 255))
for y in range(4, 8):  # The spring's coils.
    for x in range(0, 4):
        if (x + y) % 2 == 0:
            img.putpixel((x, y), (110, 106, 98, 255))
os.makedirs(os.path.join(ROOT, 'textures/block/keyer_paddle'), exist_ok=True)
img.save(os.path.join(ROOT, 'textures/block/iambic_keyer.png'))

for finish, (r, g, b, a) in FINISHES.items():
    p = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            j = rnd.randint(-5, 5)
            shine = 40 if finish in SHINY and (x + y) % 7 == 0 else 0  # A glint on metal and gems.
            edge = -25 if x in (0, 15) or y in (0, 15) else 0  # A darker rim.
            p.putpixel((x, y), (max(0, min(255, r + j + shine + edge)), max(0, min(255, g + j + shine + edge)),
                                max(0, min(255, b + j + shine + edge)), a))
    p.save(os.path.join(ROOT, 'textures/block/keyer_paddle/%s.png' % finish))


def uv(name):
    x0, y0 = SW[name]
    return [x0 + 0.5, y0 + 0.5, x0 + 3.5, y0 + 3.5]


def box(frm, to, swatch, rotation=None, texture='#0'):
    faces = {f: {'uv': uv(swatch) if texture == '#0' else [1, 1, 15, 15], 'texture': texture} for f in ('north', 'east', 'south', 'west', 'up', 'down')}
    el = {'from': frm, 'to': to, 'faces': faces}
    if rotation:
        el['rotation'] = rotation
    return el


def paddle(x0):
    """A triangular finger piece in the y-z plane: three stepped plates, widest at the lever."""
    x1 = x0 + 0.5
    return [box([x0, 3.5, 0.5], [x1, 4.5, 4.5], 'base', texture='#paddle'),
            box([x0, 4.5, 1.0], [x1, 5.5, 4.5], 'base', texture='#paddle'),
            box([x0, 5.5, 2.0], [x1, 6.5, 4.5], 'base', texture='#paddle'),
            box([x0, 6.5, 3.0], [x1, 7.25, 4.5], 'base', texture='#paddle')]


elements = [
    box([2, 0, 3], [14, 3, 15], 'base'),                                    # the base
    box([3, 3, 12.5], [8, 3.1, 14.5], 'label'),                             # its label
    box([7.25, 3, 12.25], [8.75, 6, 13.75], 'metal'),                       # the spring post
    box([7.6, 6, 12.6], [8.4, 6.5, 13.4], 'dark'),                          # its cap
    box([6.6, 4.6, 8.5], [7.1, 5.1, 12.25], 'spring'),                      # the springs
    box([8.9, 4.6, 8.5], [9.4, 5.1, 12.25], 'spring'),
    box([6.25, 3, 6], [9.75, 6.25, 8.5], 'metal'),                          # the pivot, round
    box([6.25, 3, 6], [9.75, 6.25, 8.5], 'metal', {'angle': 45, 'axis': 'y', 'origin': [8, 4.5, 7.25]}),
    box([7.5, 6.25, 6.75], [8.5, 6.75, 7.75], 'dark'),                      # its bearing screw
    box([3.0, 3, 5.75], [4.5, 5.25, 7.25], 'metal'),                        # the adjustment screw posts
    box([11.5, 3, 5.75], [13, 5.25, 7.25], 'metal'),
    box([4.5, 4.0, 6.25], [6.25, 4.5, 6.75], 'dark'),                       # their screws, in to the levers
    box([9.75, 4.0, 6.25], [11.5, 4.5, 6.75], 'dark'),
    box([7.0, 4.25, 4.5], [7.5, 5.0, 6.0], 'metal'),                        # the levers
    box([8.5, 4.25, 4.5], [9.0, 5.0, 6.0], 'metal'),
] + paddle(6.75) + paddle(8.75)


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent='\t')
        f.write('\n')


# The parent model is the maintainer's since 2026-11-13 (scaled in Blockbench): written only if it is missing, so the
# finishes (which only swap #paddle and inherit everything else) always follow the maintainer's version.
if not os.path.exists(os.path.join(ROOT, 'models/block/iambic_keyer.json')):
  write('models/block/iambic_keyer.json', {
    'credit': 'Made with tools/keyer_model.py (a Bencher BY-1 style iambic paddle)',
    'render_type': 'minecraft:translucent', 'texture_size': [16, 16],
    'textures': {'0': 'radiocraft:block/iambic_keyer', 'paddle': 'radiocraft:block/keyer_paddle/amber', 'particle': 'radiocraft:block/iambic_keyer'},
    'elements': elements,
    'display': {
        'gui': {'rotation': [30, 135, 0], 'translation': [0, 2, 0], 'scale': [0.9, 0.9, 0.9]},
        'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [-90, 0, 0], 'translation': [0, 0, -2], 'scale': [0.8, 0.8, 0.8]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [0, 135, 0], 'translation': [0, 3, 0], 'scale': [0.6, 0.6, 0.6]},
        'firstperson_lefthand': {'rotation': [0, 135, 0], 'translation': [0, 3, 0], 'scale': [0.6, 0.6, 0.6]}},
})
names = list(FINISHES)
for finish in names:
    write('models/block/iambic_keyer_%s.json' % finish, {'parent': 'radiocraft:block/iambic_keyer',
                                                         'textures': {'paddle': 'radiocraft:block/keyer_paddle/%s' % finish}})
# The item: amber by default, each finish by the radiocraft:finish predicate (its index / 100, ClientSetupEvents).
write('models/item/iambic_keyer.json', {
    'parent': 'radiocraft:block/iambic_keyer_amber',
    'overrides': [{'predicate': {'radiocraft:finish': round(i / 100.0, 2)}, 'model': 'radiocraft:block/iambic_keyer_%s' % f}
                  for i, f in enumerate(names) if i > 0],
})
variants = {}
for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
    for finish in names:
        v = {'model': 'radiocraft:block/iambic_keyer_%s' % finish}
        if y:
            v['y'] = y
        variants['facing=%s,finish=%s' % (facing, finish)] = v
write('blockstates/iambic_keyer.json', {'variants': variants})
print('wrote the iambic keyer: %d finishes' % len(names))
