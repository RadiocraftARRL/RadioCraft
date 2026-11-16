"""Model and texture for the FM broadcast antenna (2026-11-16), from the maintainer's photo.

A side-mounted, circularly polarised FM bay: two dipole tubes joined by an open ring. The upper tube rises from one
end of the ring and the lower tube hangs from the other, both on the side away from the mast. The ring is held at its
near side by a clamp and feed box on a horizontal side arm, and the arm is clamped to the mast.

Drawn facing north: the mast is to the south (the arm's clamp sits on the block's south face), so the block's FACING
is the way the arm points, away from the mast. The tubes run above and below the block (about y -6 to 23), like the
real bay is taller than the arm is long.

Writes the texture, the block and item models and the blockstate. Run from the repo root.
"""
import json
import math
import os

from PIL import Image

ROOT = 'src/main/resources/assets/radiocraft'
NAME = 'fm_broadcast_antenna'

# Texture regions (uv units on a 16 x 16 texture, 1 px each).
ALU = (0, 0, 8, 16)  # Weathered aluminium tube.
STEEL = (8, 0, 12, 16)  # Galvanised clamps.
FEED = (12, 0, 16, 16)  # The darker feed box and arm.


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def texture():
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if x < 8:  # Aluminium: light with a darker streak down each tube.
                c = (190, 194, 196) if x not in (0, 7) else (150, 154, 158)
                if x in (2, 3):
                    c = (214, 218, 220)
                if (x * 7 + y * 3) % 11 == 0:
                    c = tuple(v - 12 for v in c)
            elif x < 12:  # Galvanised steel, spangled.
                c = (138, 142, 140) if (x + y) % 3 else (160, 164, 162)
                if y % 4 == 0:
                    c = (110, 114, 112)
            else:  # The arm and feed box: duller, darker steel.
                c = (112, 116, 118) if (x + y) % 2 else (98, 102, 104)
            img.putpixel((x, y), c + (255,))
    os.makedirs(ROOT + '/textures/block', exist_ok=True)
    img.save(ROOT + '/textures/block/' + NAME + '.png')


def region_uv(region, frm, to, face):
    """A face's uv inside a texture region, sized to the face (clamped to the region)."""
    u0, v0, u1, v1 = region
    w = {'north': to[0] - frm[0], 'south': to[0] - frm[0], 'east': to[2] - frm[2], 'west': to[2] - frm[2],
         'up': to[0] - frm[0], 'down': to[0] - frm[0]}[face]
    h = {'north': to[1] - frm[1], 'south': to[1] - frm[1], 'east': to[1] - frm[1], 'west': to[1] - frm[1],
         'up': to[2] - frm[2], 'down': to[2] - frm[2]}[face]
    return [u0, v0, min(u1, u0 + max(w, 0.5)), min(v1, v0 + max(h, 0.5))]


def box(name, frm, to, region, rotation=None):
    frm = [round(v, 3) for v in frm]
    to = [round(v, 3) for v in to]
    e = {'name': name, 'from': frm, 'to': to,
         'faces': {f: {'uv': region_uv(region, frm, to, f), 'texture': '#all'}
                   for f in ('north', 'east', 'south', 'west', 'up', 'down')}}
    if rotation:
        e['rotation'] = rotation
    return e


def tube(name, x, z, y0, y1, w, region=ALU):
    return box(name, [x - w / 2, y0, z - w / 2], [x + w / 2, y1, z + w / 2], region)


# The ring: a 16-gon of radius R round (CX, Y, CZ) in the horizontal plane, open on the far (north) side between
# the two tube ends. Minecraft turns an element at most 45 degrees, so each segment is laid along x or z and turned
# by -45..45 to point along the ring.
CX, CZ, Y, R, T = 8.0, 5.2, 8.0, 5.0, 0.8


def ring():
    out = []
    step = 22.5
    for i in range(16):
        mid = -90 + step * i  # Position angle (degrees in x/z, z down the page): -90 is the far side.
        if i == 0:  # The segment at the far point is the gap (so every segment turns by a multiple of 22.5).
            continue
        rad = math.radians(mid)
        px, pz = CX + R * math.cos(rad), CZ + R * math.sin(rad)
        tangent = (mid + 90) % 180  # Direction of the segment, mod 180.
        if tangent > 90:
            tangent -= 180  # Now -90..90.
        length = 2 * R * math.sin(math.radians(step / 2)) + 0.35  # A little overlap at the joints.
        if -45 <= tangent <= 45:
            frm = [px - length / 2, Y - T / 2, pz - T / 2]
            to = [px + length / 2, Y + T / 2, pz + T / 2]
            angle = -tangent
        else:
            frm = [px - T / 2, Y - T / 2, pz - length / 2]
            to = [px + T / 2, Y + T / 2, pz + length / 2]
            angle = -(tangent - 90 if tangent > 0 else tangent + 90)
        rot = {'angle': angle, 'axis': 'y', 'origin': [round(px, 3), Y, round(pz, 3)]} if abs(angle) > 0.01 else None
        out.append(box('ring_%d' % i, frm, to, ALU, rot))
    return out


def ring_end(side):
    """Where the ring stops beside the gap: the foot of a dipole tube."""
    a = math.radians(-90 + side * 11.25)
    return CX + R * math.cos(a), CZ + R * math.sin(a)


def model():
    ux, uz = ring_end(-1)  # Upper tube on one side of the gap,
    lx, lz = ring_end(1)  # the lower tube on the other.
    elements = ring() + [
        # The upper dipole half: a thick tube up from the ring, a clamp, a thinner tip.
        tube('upper_elbow', ux, uz, Y - 0.4, Y + 1.0, 0.9),
        tube('upper_tube', ux, uz, Y + 1.0, 20.0, 1.0),
        tube('upper_clamp', ux, uz, 19.5, 20.5, 1.3, STEEL),
        tube('upper_tip', ux, uz, 20.0, 23.0, 0.8),
        # The lower half: down from the ring, the same the other way up.
        tube('lower_elbow', lx, lz, Y - 1.0, Y + 0.4, 0.9),
        tube('lower_tube', lx, lz, -3.0, Y - 1.0, 1.0),
        tube('lower_clamp', lx, lz, -3.5, -2.5, 1.3, STEEL),
        tube('lower_tip', lx, lz, -6.0, -3.0, 0.8),
        # The ring's clamp and the feed box under it, at the near side of the ring.
        box('ring_clamp', [7.0, 7.0, 9.4], [9.0, 9.0, 11.0], STEEL),
        box('feed_box', [7.3, 5.6, 9.6], [8.7, 7.0, 10.8], FEED),
        # The side arm back to the mast, and its mast clamp on the south face.
        box('arm', [7.4, 7.4, 11.0], [8.6, 8.6, 15.4], FEED),
        box('arm_collar', [7.2, 7.2, 13.6], [8.8, 8.8, 14.4], STEEL),
        box('mast_clamp', [6.0, 6.0, 15.4], [10.0, 10.0, 16.0], STEEL),
        box('mast_clamp_bolt_l', [5.5, 9.0, 15.2], [6.5, 9.6, 16.0], STEEL),
        box('mast_clamp_bolt_r', [9.5, 6.4, 15.2], [10.5, 7.0, 16.0], STEEL),
    ]
    return {'credit': 'tools/fm_broadcast_antenna_model.py', 'parent': 'block/block', 'ambientocclusion': False,
            'textures': {'all': 'radiocraft:block/' + NAME, 'particle': 'radiocraft:block/' + NAME},
            'elements': elements,
            'display': {
                'gui': {'rotation': [20, 225, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.25, 0.25, 0.25]},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.3, 0.3, 0.3]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
                'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]}}}


def blockstate():
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        v = {'model': 'radiocraft:block/' + NAME}
        if y:
            v['y'] = y
        variants['facing=' + facing] = v
    return {'variants': variants}


if __name__ == '__main__':
    texture()
    write(ROOT + '/models/block/' + NAME + '.json', model())
    write(ROOT + '/models/item/' + NAME + '.json', {'parent': 'radiocraft:block/' + NAME})
    write(ROOT + '/blockstates/' + NAME + '.json', blockstate())
    print('wrote', NAME)
