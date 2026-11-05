"""Writes the ham shack structure templates (RadioCraft 1.12 step 8) without running the game.

    python tools/ham_shack_templates.py

A small timber house, found dark like the buried site: a VHF base station on 146.520 MHz, switched off, with a J-pole
on the roof (coax straight up the north wall), a flat large battery on wire, a radio computer with its monitor, a chest of radio parts (radiocraft:chests/ham_shack, with
a chance of a secret floppy disk), a bed and a chair. Two kinds of template come out:

* data/radiocraft/structure/sites/ham_shack.nbt: the standalone radio site (SiteStructure, never rotated), with a
  one-block margin round the house like the other sites; y 0 is the ground layer.
* data/radiocraft/structure/village/ham_shack_<biome>.nbt for plains, taiga, snowy, savanna and desert: the same
  house in that village's wood and stone, with the jigsaw at the doorstep that joins it to a village street
  ("minecraft:building_entrance", as the vanilla houses have) and one that brings a villager. VillageHouses adds them
  to the vanilla house pools; they are rotated, so RotateBlocksProcessor turns RadioCraft's blocks with them.

The block states are written in full, copied from templates saved in game (weather_observatory, buried_site), so
the wires and coax join without a neighbour update. Edit the layout in house() and run it again.
"""
import gzip
import io
import os
import struct

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'data', 'radiocraft', 'structure')
DATA_VERSION = 3955  # Minecraft 1.21.1

# ---------------------------------------------------------------- NBT writer (gzipped, big endian)

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND = range(11)


class Tag:
    def __init__(self, kind, value):
        self.kind, self.value = kind, value


def byte(v): return Tag(BYTE, int(v))
def double(v): return Tag(DOUBLE, float(v))
def flt(v): return Tag(FLOAT, float(v))


def kind_of(v):
    if isinstance(v, Tag): return v.kind
    if isinstance(v, bool): return BYTE
    if isinstance(v, int): return INT
    if isinstance(v, str): return STRING
    if isinstance(v, list): return LIST
    if isinstance(v, dict): return COMPOUND
    raise TypeError(type(v))


def put(out, v):
    k = kind_of(v)
    raw = v.value if isinstance(v, Tag) else v
    if k == BYTE: out.write(struct.pack('>b', int(raw)))
    elif k == INT: out.write(struct.pack('>i', raw))
    elif k == FLOAT: out.write(struct.pack('>f', raw))
    elif k == DOUBLE: out.write(struct.pack('>d', raw))
    elif k == STRING:
        b = raw.encode('utf-8'); out.write(struct.pack('>H', len(b))); out.write(b)
    elif k == LIST:
        inner = kind_of(raw[0]) if raw else END
        out.write(bytes([inner])); out.write(struct.pack('>i', len(raw)))
        for item in raw: put(out, item)
    elif k == COMPOUND:
        for key, item in raw.items():
            out.write(bytes([kind_of(item)])); b = key.encode('utf-8'); out.write(struct.pack('>H', len(b))); out.write(b)
            put(out, item)
        out.write(bytes([END]))
    else:
        raise TypeError(k)


def write_nbt(path, root):
    out = io.BytesIO()
    out.write(bytes([COMPOUND])); out.write(struct.pack('>H', 0)); put(out, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        with gzip.GzipFile(fileobj=f, mode='wb', mtime=0) as g:
            g.write(out.getvalue())

# ---------------------------------------------------------------- template builder


class Template:
    def __init__(self, size=None):
        self.blocks = {}  # (x, y, z) -> (name, props, nbt)
        self.size = size  # None: just big enough for the blocks

    def set(self, x, y, z, name, props=None, nbt=None):
        self.blocks[(x, y, z)] = (name, dict(props or {}), nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, props)

    def save(self, path):
        xs = [p[0] for p in self.blocks]; ys = [p[1] for p in self.blocks]; zs = [p[2] for p in self.blocks]
        assert min(xs) >= 0 and min(ys) >= 0 and min(zs) >= 0
        size = [max(xs) + 1, max(ys) + 1, max(zs) + 1]
        if self.size:
            assert all(s >= n for s, n in zip(self.size, size)), (self.size, size)
            size = list(self.size)
        palette, index, blocks = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, props, nbt = self.blocks[pos]
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = {'Name': name}
                if props:
                    entry['Properties'] = {k: str(v).lower() for k, v in sorted(props.items())}
                palette.append(entry)
            block = {'pos': list(pos), 'state': index[key]}
            if nbt is not None:
                block['nbt'] = nbt
            blocks.append(block)
        write_nbt(path, {'size': size, 'entities': [], 'blocks': blocks, 'palette': palette, 'DataVersion': DATA_VERSION})
        print(os.path.relpath(path), size, len(blocks), 'blocks,', len(palette), 'states')

# ---------------------------------------------------------------- pieces


def wire_props(north=False, east=False, south=False, west=False, up=False, down=False, hug='none', ceiling=False):
    return {'north': north, 'east': east, 'south': south, 'west': west, 'up': up, 'down': down, 'hug': hug,
            'climb': 'none', 'ceiling': ceiling, 'waterlogged': False}


def radio_nbt(hz):
    return {'id': 'radiocraft:vhf_base_station', 'antennaSWR': double(0.0), 'ctcssTone': flt(0.0), 'ssbEnabled': byte(1),
            'wasPowered': byte(0), 'micGain': flt(1.0), 'frequency': flt(hz), 'speakerGain': flt(1.0), 'coaxShared': byte(0),
            'offsetHz': 0, 'band': '2m'}


MATERIALS = {
    # biome: (floor/foundation, wall, corner log, roof, stairs, door, villagers pool or None)
    'site': ('minecraft:cobblestone', 'minecraft:oak_planks', 'minecraft:oak_log', 'minecraft:spruce_planks', 'minecraft:oak_stairs', 'minecraft:oak_door', None),
    'plains': ('minecraft:cobblestone', 'minecraft:oak_planks', 'minecraft:oak_log', 'minecraft:spruce_planks', 'minecraft:oak_stairs', 'minecraft:oak_door', 'minecraft:village/plains/villagers'),
    'taiga': ('minecraft:cobblestone', 'minecraft:spruce_planks', 'minecraft:spruce_log', 'minecraft:dark_oak_planks', 'minecraft:spruce_stairs', 'minecraft:spruce_door', 'minecraft:village/taiga/villagers'),
    'snowy': ('minecraft:stone_bricks', 'minecraft:spruce_planks', 'minecraft:stripped_spruce_log', 'minecraft:spruce_planks', 'minecraft:spruce_stairs', 'minecraft:spruce_door', 'minecraft:village/snowy/villagers'),
    'savanna': ('minecraft:terracotta', 'minecraft:acacia_planks', 'minecraft:acacia_log', 'minecraft:acacia_planks', 'minecraft:acacia_stairs', 'minecraft:acacia_door', 'minecraft:village/savanna/villagers'),
    'desert': ('minecraft:smooth_sandstone', 'minecraft:cut_sandstone', 'minecraft:chiseled_sandstone', 'minecraft:smooth_sandstone', 'minecraft:sandstone_stairs', 'minecraft:jungle_door', 'minecraft:village/desert/villagers'),
}


def house(t, ox, oz, biome):
    """The house, its north-west bottom corner at (ox, 0, oz): 9 by 7, y 0 the floor, y 4 the flat roof."""
    floor, wall, corner, roof, stairs, door, villagers = MATERIALS[biome]
    log = {} if corner.startswith('minecraft:chiseled') else {'axis': 'y'}
    X = lambda x: ox + x
    Z = lambda z: oz + z
    # Floor and foundation, walls with log corners, the roof.
    t.fill(X(0), 0, Z(0), X(8), 0, Z(6), floor)
    t.fill(X(1), 0, Z(1), X(7), 0, Z(5), wall)
    for x in range(9):
        for z in range(7):
            edge = x in (0, 8) or z in (0, 6)
            for y in range(1, 4):
                if edge:
                    t.set(X(x), y, Z(z), corner if x in (0, 8) and z in (0, 6) else wall, log if x in (0, 8) and z in (0, 6) else None)
                else:
                    t.set(X(x), y, Z(z), 'minecraft:cave_air')
    t.fill(X(0), 4, Z(0), X(8), 4, Z(6), roof)
    # Door in the south wall, windows.
    t.set(X(4), 1, Z(6), door, {'facing': 'north', 'half': 'lower', 'hinge': 'left', 'open': False, 'powered': False})
    t.set(X(4), 2, Z(6), door, {'facing': 'north', 'half': 'upper', 'hinge': 'left', 'open': False, 'powered': False})
    pane = 'minecraft:glass_pane'
    for x in (2, 6):
        t.set(X(x), 2, Z(6), pane, {'east': True, 'west': True, 'north': False, 'south': False, 'waterlogged': False})
    for x in (0, 8):
        t.set(X(x), 2, Z(3), pane, {'north': True, 'south': True, 'east': False, 'west': False, 'waterlogged': False})
    t.set(X(6), 2, Z(0), pane, {'east': True, 'west': True, 'north': False, 'south': False, 'waterlogged': False})
    t.set(X(5), 3, Z(3), 'minecraft:lantern', {'hanging': True, 'waterlogged': False})

    # The station along the north wall: battery, radio, computer and monitor, chest.
    # Found dark (maintainer, 2026-11-05): the battery flat and the radio off, as at the buried site.
    t.set(X(1), 1, Z(1), 'radiocraft:large_battery', {'facing': 'south'}, {'id': 'radiocraft:large_battery', 'energy': 0})
    t.set(X(2), 1, Z(1), 'radiocraft:vhf_base_station', {'facing': 'south', 'ceiling': False, 'blown': False}, radio_nbt(146520000.0))
    # Power: a wire along the front of the desk, from the battery to the radio.
    t.set(X(1), 1, Z(2), 'radiocraft:wire', wire_props(north=True, east=True))
    t.set(X(2), 1, Z(2), 'radiocraft:wire', wire_props(north=True, west=True))
    # Coax: up the north wall from the radio, through the roof, and across to the J-pole.
    for y in (2, 3, 4):
        t.set(X(2), y, Z(1), 'radiocraft:coax_wire', wire_props(up=True, down=True, hug='north'))
    t.set(X(2), 5, Z(1), 'radiocraft:coax_wire', wire_props(down=True, east=True))
    t.set(X(3), 5, Z(1), 'radiocraft:j_pole_antenna', {'half': 'lower', 'facing': 'south', 'north': False, 'east': False, 'south': False, 'west': True},
          {'id': 'radiocraft:antenna'})
    t.set(X(3), 6, Z(1), 'radiocraft:j_pole_antenna', {'half': 'upper', 'facing': 'south', 'north': False, 'east': False, 'south': False, 'west': False})
    t.set(X(7), 5, Z(5), 'minecraft:lightning_rod', {'facing': 'up', 'powered': False, 'waterlogged': False})
    # The computer: logbook and frequency manager installed (no floppy in the drive).
    t.set(X(4), 1, Z(1), 'radiocraft:radio_computer', {'facing': 'south'},
          {'id': 'radiocraft:radio_computer', 'installed': ['logbook', 'freqman']})
    t.set(X(4), 2, Z(1), 'radiocraft:computer_monitor', {'facing': 'south'}, {'id': 'radiocraft:computer_monitor'})
    t.set(X(5), 1, Z(1), 'minecraft:bookshelf')
    t.set(X(7), 1, Z(1), 'minecraft:chest', {'facing': 'south', 'type': 'single', 'waterlogged': False},
          {'id': 'minecraft:chest', 'LootTable': 'radiocraft:chests/ham_shack'})
    # A chair at the radio, a bed, a plant.
    t.set(X(2), 1, Z(3), stairs, {'facing': 'south', 'half': 'bottom', 'shape': 'straight', 'waterlogged': False})
    t.set(X(6), 1, Z(5), 'minecraft:red_bed', {'facing': 'east', 'part': 'foot', 'occupied': False}, {'id': 'minecraft:bed'})
    t.set(X(7), 1, Z(5), 'minecraft:red_bed', {'facing': 'east', 'part': 'head', 'occupied': False}, {'id': 'minecraft:bed'})
    t.set(X(1), 1, Z(5), 'minecraft:potted_poppy')
    if villagers:
        # The villager who lives here, standing on the floor in the middle.
        t.set(X(4), 0, Z(3), 'minecraft:jigsaw', {'orientation': 'up_north'},
              {'id': 'minecraft:jigsaw', 'name': 'minecraft:bottom', 'target': 'minecraft:bottom', 'pool': villagers,
               'final_state': wall, 'joint': 'rollable'})
        # The doorstep, where the house joins the street.
        t.set(X(4), 0, Z(7), 'minecraft:jigsaw', {'orientation': 'south_up'},
              {'id': 'minecraft:jigsaw', 'name': 'minecraft:building_entrance', 'target': 'minecraft:building_entrance',
               'pool': 'minecraft:empty', 'joint': 'aligned',
               'final_state': stairs + '[facing=north,half=bottom,shape=straight,waterlogged=false]'})


def main():
    # A margin of one block round the house (left as it is in the world), like the other sites' open ground.
    site = Template(size=(11, 7, 9))
    house(site, 1, 1, 'site')
    site.save(os.path.join(ROOT, 'sites', 'ham_shack.nbt'))
    for biome in ('plains', 'taiga', 'snowy', 'savanna', 'desert'):
        t = Template()
        house(t, 0, 0, biome)
        t.save(os.path.join(ROOT, 'village', 'ham_shack_' + biome + '.nbt'))


if __name__ == '__main__':
    main()
