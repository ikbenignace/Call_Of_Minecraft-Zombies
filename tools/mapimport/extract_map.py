#!/usr/bin/env python3
"""
Builds COM:Z arena data from the ZombiesMC standalone map (command blocks + datapack), so its maps do not
have to be set up by hand.

Reads, per map datapack (zombie_cod5_asylum, zombie_temple, ...):
  - doors/doorN/*.mcfunction: price ("Points=N.."), the blocks set to air (the door itself) and the
    zombie spawners summoned when it opens;
  - unique/barricades/window_N.mcfunction: every board of window N with its block state;
  - every "summon marker ... Spawner" (start spawners, door spawners, window link via tag wN);
  - maps/mysterybox/move/locN/locNset.mcfunction: each box location and facing;
  - maps/power/power.mcfunction: the perk signs it fills in at power-on ("data merge block");
  - maps/traps/trapN* and unique/<flogger|centrifuge|spikes>: traps (see parse_traps, parse_unique_traps);
  - travel functions (ziplines, teleporters, landers, elevators, jump pads): transports;
  - unique/song/egg*: the song easter egg trigger; unique/perk_placement: the perk pool;
  - Pack-a-Punch gates (switches, plates, teleporter link, landers), low gravity and air pockets, bosses.
And from the ORIGINAL world (not the edited server world): door/board block states, the door signs (by
their D<N>_B click tag), command blocks (power, egg buttons, switches) and clickable signs.

Writes <out>/<arena>.json:
  {"save_data": {...arena save fields...}, "settings": {...imported arena settings...},
   "signs": [{"x","y","z","lines":[4],"blockdata"?}], "block_edits": [...], "report": [...]}
The plugin applies it with /zombies importmap <arena>.

    python3 -I tools/mapimport/extract_map.py --world <original world dir> --map zombie_cod5_asylum --arena Verruckt --out <dir>

Needs nbtlib (pip install nbtlib). The world files are only parsed as NBT; nothing in them is executed.
"""
import argparse
import glob
import io
import json
import math
import os
import random
import re
import string
import struct
import zlib

import nbtlib

COORD = r"(-?\d+(?:\.\d+)?)"
SUMMON_SPAWNER = re.compile(r"summon minecraft:marker " + COORD + " " + COORD + " " + COORD + r" \{Tags:\[([^\]]*)\]")
FILL = re.compile(r"(?:^|run )\s*fill (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) minecraft:air")
SETBLOCK_AIR = re.compile(r"(?:^|run )\s*setblock (-?\d+) (-?\d+) (-?\d+) minecraft:air")
SETBLOCK_ANY = re.compile(r"setblock (-?\d+) (-?\d+) (-?\d+) minecraft:([a-z_]+(?:\[[^\]]*\])?)")
PRICE = re.compile(r"Points=(\d+)\.\.")
BOX_MARKER = re.compile(r"summon minecraft:marker " + COORD + " " + COORD + " " + COORD + r" \{Tags:\[\"Box\"\](?:,Rotation:\[(-?\d+(?:\.\d+)?)F)?")
WALL_SIGN_FACING = re.compile(r"_wall_sign\[facing=([a-z]+)\]")
DATA_MERGE = re.compile(r"data merge block (-?\d+) (-?\d+) (-?\d+) (\{.*\})\s*$")
TP_START = re.compile(r"^tp @[ap](?:\[tag=PlayerOne\])? " + COORD + " " + COORD + " " + COORD + "(?: " + COORD + ")?")
MAP_MARGIN = 24
SKIP_SPAWNER_TAGS = ("Monkey", "Boss", "Napalm", "Shrieker", "Thief", "George")


def rid(n=8):
    return ''.join(random.choice(string.ascii_letters + string.digits) for _ in range(n))


# ------------------------------------------------------------------ world reading
class World:
    """Random block-state and block-entity access to an Anvil world, chunk-cached."""

    def __init__(self, path):
        self.region = os.path.join(path, 'region')
        self.chunks = {}

    def _chunk(self, cx, cz):
        key = (cx, cz)
        if key in self.chunks:
            return self.chunks[key]
        chunk = None
        path = os.path.join(self.region, 'r.%d.%d.mca' % (cx >> 5, cz >> 5))
        if os.path.exists(path):
            with open(path, 'rb') as f:
                data = f.read()
            i = (cx & 31) + (cz & 31) * 32
            off = struct.unpack('>I', b'\0' + data[i * 4:i * 4 + 3])[0]
            if off:
                p = off * 4096
                ln = struct.unpack('>I', data[p:p + 4])[0]
                raw = data[p + 5:p + 4 + ln]
                if data[p + 4] == 2:
                    raw = zlib.decompress(raw)
                chunk = nbtlib.File.parse(io.BytesIO(raw))
        self.chunks[key] = chunk
        return chunk

    def block(self, x, y, z):
        """Block state string like 'minecraft:oak_stairs[facing=east,half=bottom]', or 'minecraft:air'."""
        chunk = self._chunk(x >> 4, z >> 4)
        if chunk is None:
            return 'minecraft:air'
        for sec in chunk.get('sections', []):
            if int(sec['Y']) != y >> 4:
                continue
            states = sec.get('block_states')
            if states is None:
                return 'minecraft:air'
            palette = states['palette']
            if len(palette) == 1 or 'data' not in states:
                return state_string(palette[0])
            bits = max(4, (len(palette) - 1).bit_length())
            per_long = 64 // bits
            index = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
            word = int(states['data'][index // per_long]) & 0xFFFFFFFFFFFFFFFF
            value = (word >> ((index % per_long) * bits)) & ((1 << bits) - 1)
            return state_string(palette[value])
        return 'minecraft:air'

    def block_entity(self, x, y, z):
        chunk = self._chunk(x >> 4, z >> 4)
        if chunk is None:
            return None
        for be in chunk.get('block_entities', []):
            if int(be['x']) == x and int(be['y']) == y and int(be['z']) == z:
                return be
        return None

    def signs_in(self, lo, hi):
        out = []
        for cx in range(lo[0] >> 4, (hi[0] >> 4) + 1):
            for cz in range(lo[2] >> 4, (hi[2] >> 4) + 1):
                chunk = self._chunk(cx, cz)
                if chunk is None:
                    continue
                for be in chunk.get('block_entities', []):
                    if 'sign' in str(be.get('id', '')) and lo[1] <= int(be['y']) <= hi[1]:
                        out.append(be)
        return out


def state_string(entry):
    name = str(entry['Name'])
    props = entry.get('Properties')
    if props:
        name += '[' + ','.join('%s=%s' % (k, str(v)) for k, v in sorted(props.items())) + ']'
    return name


def sign_raw(be):
    """All raw sign text (JSON, click events included), front side for 1.20+ or Text1..4 for older."""
    if 'front_text' in be:
        return ' '.join(str(m) for m in be['front_text']['messages'])
    return ' '.join(str(be.get('Text%d' % i, '')) for i in range(1, 5))


# ------------------------------------------------------------------ datapack parsing
def read(path):
    with open(path, encoding='utf-8', errors='replace') as f:
        return f.read()


def spawners_in(text):
    out = []
    for m in SUMMON_SPAWNER.finditer(text):
        tags = [t.strip().strip('"') for t in m.group(4).split(',')]
        if 'Spawner' not in tags or any(any(s in t for s in SKIP_SPAWNER_TAGS) for t in tags):
            continue
        window = next((t for t in tags if re.fullmatch(r'w\d+', t)), None)
        out.append(((int(float(m.group(1))), int(float(m.group(2))), int(float(m.group(3)))), window))
    return out


def door_functions(fn_root):
    doors = {}
    for path in glob.glob(os.path.join(fn_root, 'maps', 'doors', 'door*', '*.mcfunction')):
        n = re.search(r'door(\d+)', os.path.basename(os.path.dirname(path)))
        if n:
            doors.setdefault(int(n.group(1)), []).append(path)
    return doors


def parse_door(paths):
    price, cleared, spawns = None, set(), []
    for path in sorted(paths):
        text = read(path)
        if price is None:
            m = PRICE.search(text)
            if m:
                price = int(m.group(1))
        for line in text.splitlines():
            m = FILL.search(line)
            if m:
                x0, y0, z0, x1, y1, z1 = map(int, m.groups())
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    for y in range(min(y0, y1), max(y0, y1) + 1):
                        for z in range(min(z0, z1), max(z0, z1) + 1):
                            cleared.add((x, y, z))
                continue
            m = SETBLOCK_AIR.search(line)
            if m:
                cleared.add(tuple(map(int, m.groups())))
        spawns += spawners_in(text)
    return price, cleared, spawns


def parse_window(path):
    boards = {}
    for m in SETBLOCK_ANY.finditer(read(path)):
        if m.group(4) != 'air':
            boards[(int(m.group(1)), int(m.group(2)), int(m.group(3)))] = 'minecraft:' + m.group(4)
    return boards


YAW_FACING = {0: 'SOUTH', 90: 'WEST', 180: 'NORTH', -180: 'NORTH', -90: 'EAST', 270: 'EAST'}


def facing_of_yaw(yaw):
    return YAW_FACING.get(int(round(float(yaw) / 90.0)) * 90, 'NORTH')


FILL_ANY = re.compile(r"fill (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) minecraft:([a-z_]+(?:\[[^\]]*\])?)")


def block_writes(text):
    """Every block a function writes, in order: {(x, y, z): 'minecraft:state'}."""
    out = {}
    for line in text.splitlines():
        m = FILL_ANY.search(line)
        if m:
            x0, y0, z0, x1, y1, z1 = map(int, m.groups()[:6])
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for y in range(min(y0, y1), max(y0, y1) + 1):
                    for z in range(min(z0, z1), max(z0, z1) + 1):
                        out[(x, y, z)] = 'minecraft:' + m.group(7)
            continue
        m = SETBLOCK_ANY.search(line)
        if m:
            out[(int(m.group(1)), int(m.group(2)), int(m.group(3)))] = 'minecraft:' + m.group(4)
    return out


def parse_boxes(fn_root):
    """Box locations (body block under the marker, facing) and the edits that strip the map's own plank box,
    using the blocks the map leaves behind once its box has moved away."""
    boxes, edits = [], {}
    for folder in sorted(glob.glob(os.path.join(fn_root, 'maps', 'mysterybox', 'move', 'loc*'))):
        sets = glob.glob(os.path.join(folder, '*set.mcfunction'))
        if not sets:
            continue
        text = read(sets[0])
        m = BOX_MARKER.search(text)
        if not m:
            continue
        sign = WALL_SIGN_FACING.search(text)
        facing = sign.group(1).upper() if sign else facing_of_yaw(m.group(4) or 0)
        boxes.append(((int(float(m.group(1))), int(float(m.group(2))) - 1, int(float(m.group(3)))), facing))
        gone = {}
        for path in sorted(glob.glob(os.path.join(folder, '*move*.mcfunction'))):
            gone.update(block_writes(read(path)))
        for pos, state in block_writes(text).items():
            edits[pos] = gone.get(pos, 'minecraft:air')
    begin = os.path.join(fn_root, 'unique', 'begin.mcfunction')
    if not boxes and os.path.exists(begin):
        m = BOX_MARKER.search(read(begin))
        if m:
            boxes.append(((int(float(m.group(1))), int(float(m.group(2))) - 1, int(float(m.group(3)))), facing_of_yaw(m.group(4) or 0)))
    return boxes, edits


def json_text(component):
    try:
        j = json.loads(component)
    except (ValueError, TypeError):
        return ''
    if isinstance(j, str):
        return j
    if isinstance(j, dict):
        return j.get('text', '') + ''.join(json_text(json.dumps(e)) for e in j.get('extra', []))
    return ''


def merged_signs(fn_root):
    """Sign texts the datapack writes with "data merge block" (perk signs that light up at power-on)."""
    out = {}
    for path in glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True):
        for line in read(path).splitlines():
            m = DATA_MERGE.search(line.strip())
            if not m:
                continue
            texts = re.findall(r"Text(\d):'((?:[^'\\]|\\.)*)'", m.group(4))
            lines = ['', '', '', '']
            for idx, comp in texts:
                lines[int(idx) - 1] = json_text(comp)
            pos = (int(m.group(1)), int(m.group(2)), int(m.group(3)))
            # A sign is often merged twice (powered-off placeholder, then the real buy text); keep the buy text.
            if any(l.strip() for l in lines) and (pos not in out or has_cost(lines)):
                out[pos] = lines
    return {pos: lines for pos, lines in out.items() if has_cost(lines) and 'random weapon' not in ' '.join(lines).lower()}


def merged_signs_all(fn_root):
    out = {}
    for path in glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True):
        for line in read(path).splitlines():
            m = DATA_MERGE.search(line.strip())
            if not m:
                continue
            texts = re.findall(r"Text(\d):'((?:[^'\\]|\\.)*)'", m.group(4))
            lines = ['', '', '', '']
            for idx, comp in texts:
                lines[int(idx) - 1] = json_text(comp)
            pos = (int(m.group(1)), int(m.group(2)), int(m.group(3)))
            if has_cost(lines) or pos not in out:
                out[pos] = lines
    return out


def has_cost(lines):
    return any('cost' in l.lower() for l in lines)


def start_position(fn_root):
    for name in ('begin', 'prepare'):
        path = os.path.join(fn_root, 'unique', name + '.mcfunction')
        if not os.path.exists(path):
            continue
        for line in read(path).splitlines():
            m = TP_START.match(line.strip())
            if m:
                return [float(m.group(1)), float(m.group(2)), float(m.group(3)), float(m.group(4) or 0)]
    return None

PARTICLE = re.compile(r"particle minecraft:([a-z_]+) (-?\d+(?:\.\d+)?) (-?\d+(?:\.\d+)?) (-?\d+(?:\.\d+)?)")
TIMER = re.compile(r"Timer=(\d+)")
TP_MOVE = re.compile(r"(?:^|run )tp (@[ap])\[x=(-?[\d.]+),y=(-?[\d.]+),z=(-?[\d.]+)[^\]]*\] (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)")
TRAP_TYPES = (('electric', 'electric'), ('fire', 'fire'), ('flame', 'fire'), ('flogger', 'flogger'), ('centrifuge', 'centrifuge'), ('spike', 'spikes'))


def clusters(points, reach=3):
    groups = []
    for p in points:
        joined = [g for g in groups if any(abs(p[0] - q[0]) <= reach and abs(p[2] - q[2]) <= reach and abs(p[1] - q[1]) <= reach for q in g)]
        merged = [p]
        for g in joined:
            merged += g
            groups.remove(g)
        groups.append(merged)
    return sorted(groups, key=len, reverse=True)


def box_of(points):
    return ({'x': float(min(p[0] for p in points)), 'y': float(min(p[1] for p in points) - 1), 'z': float(min(p[2] for p in points))},
            {'x': float(max(p[0] for p in points)), 'y': float(max(p[1] for p in points) + 1), 'z': float(max(p[2] for p in points))})


def parse_traps(fn_root, merged):
    """trapNbuy/clock/visuals: price, signs, name, timings and the kill area: the particle spots that outline it,
    else the fire it sets, else the kill selectors (x/y/z/distance) of its visuals."""
    traps = []
    folder = os.path.join(fn_root, 'maps', 'traps')
    for buy in sorted(glob.glob(os.path.join(folder, 'trap*buy.mcfunction'))):
        n = re.search(r'trap(\d+)buy', buy).group(1)
        text = read(buy)
        clock = read(os.path.join(folder, 'trap%sclock.mcfunction' % n)) if os.path.exists(os.path.join(folder, 'trap%sclock.mcfunction' % n)) else ''
        visuals = read(os.path.join(folder, 'trap%svisuals.mcfunction' % n)) if os.path.exists(os.path.join(folder, 'trap%svisuals.mcfunction' % n)) else ''
        price = PRICE.search(text)
        signs = sorted({tuple(map(int, m.group(1, 2, 3))) for m in merges(text) + merges(clock)})
        if not signs:
            continue
        name = next((merged[p][0].strip() for p in signs if p in merged and merged[p][0].strip()), 'Trap')
        spots = [(int(float(m.group(2))), int(float(m.group(3))), int(float(m.group(4)))) for m in PARTICLE.finditer(visuals)]
        particle_names = ' '.join(m.group(1) for m in PARTICLE.finditer(visuals))
        fire = [p for p, state in block_writes(text).items() if state.split('[')[0] in ('minecraft:fire', 'minecraft:soul_fire')]
        if spots:
            regions = [box_of(c) for c in clusters(spots)]
        elif fire:
            regions = [box_of(c) for c in clusters(fire)]
            particle_names += ' flame'
        else:
            regions = [({k: float(v) for k, v in lo.items()}, {k: float(v) for k, v in hi.items()}) for lo, hi in kill_boxes(visuals)]
        if not regions:
            continue
        lowered = (name + ' ' + particle_names).lower()
        kind = next((t for key, t in TRAP_TYPES if key in lowered), 'generic')
        timers = sorted({int(t) for t in TIMER.findall(clock)})
        duration = timers[0] // 20 if timers else -1
        cooldown = (timers[-1] - timers[0]) // 20 if len(timers) > 1 else -1
        traps.append({'n': n, 'name': name, 'type': kind, 'cost': int(price.group(1)) if price else 1000,
                      'signs': signs, 'regions': regions, 'duration': duration, 'cooldown': cooldown})
    return traps


TP_ANY = re.compile(r"(?:^|run )tp (@[apes])(\[[^\]]*\])? (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)(?:\s|$)")
SELECTOR_POS = re.compile(r"x=(-?[\d.]+),y=(-?[\d.]+),z=(-?[\d.]+)")
STEP = re.compile(r"scores=\{[A-Za-z_]+=(\d+)\}")


def close(a, b, reach=3):
    return max(abs(a[i] - b[i]) for i in range(3)) <= reach


# ------------------------------------------------------------------ world index (command blocks, clickable signs)
CLICK_COMMAND = re.compile(r'"(?:value|command)"\s*:\s*"([^"]*)"')
FUNCTION_CALL = re.compile(r"function custom:([a-z0-9_/]+)")


def scan_world(world_path):
    """One pass over the original world: every command block (position, command) and every sign whose click
    event runs a command (position, commands, block state)."""
    w = World(world_path)
    commands, click_signs = [], []
    for path in glob.glob(os.path.join(world_path, 'region', '*.mca')):
        rx, rz = map(int, re.search(r'r\.(-?\d+)\.(-?\d+)\.mca', path).groups())
        for i in range(1024):
            cx, cz = rx * 32 + i % 32, rz * 32 + i // 32
            chunk = w._chunk(cx, cz)
            if chunk is None:
                continue
            for be in chunk.get('block_entities', []):
                kind = str(be.get('id', ''))
                pos = (int(be['x']), int(be['y']), int(be['z']))
                if 'command_block' in kind:
                    commands.append((pos, str(be.get('Command', ''))))
                elif 'sign' in kind:
                    clicks = CLICK_COMMAND.findall(sign_raw(be))
                    if clicks:
                        click_signs.append((pos, clicks))
            w.chunks.pop((cx, cz), None)
    return {'commands': commands, 'click_signs': click_signs}


def power_blocks(world, index):
    """Power switches: the lever/button next to each command block that runs maps/power/power."""
    out = []
    for pos, command in index['commands']:
        if not re.search(r'maps/power/power\b(?!_)', command):
            continue
        x, y, z = pos
        for dx, dy, dz in ((0, 1, 0), (0, 2, 0), (1, 1, 0), (-1, 1, 0), (0, 1, 1), (0, 1, -1)):
            if any(k in world.block(x + dx, y + dy, z + dz) for k in ('lever', 'button')):
                out.append((x + dx, y + dy, z + dz))
                break
        else:
            out.append((x, y + 1, z))
    return out


def switch_near(world, pos, reach=2):
    """The lever or button closest to a command block (what the player actually presses)."""
    best = None
    for dx in range(-reach, reach + 1):
        for dy in range(-reach, reach + 1):
            for dz in range(-reach, reach + 1):
                p = (pos[0] + dx, pos[1] + dy, pos[2] + dz)
                state = world.block(*p)
                if any(k in state for k in ('lever', 'button', 'pressure_plate')):
                    d = dx * dx + dy * dy + dz * dz
                    if best is None or d < best[0]:
                        best = (d, p)
    return best[1] if best else None


def function_path(fn_root, name):
    """custom:unique/song/egg1 -> <fn_root>/unique/song/egg1.mcfunction"""
    return os.path.join(fn_root, *name.split('/')) + '.mcfunction'


def merges(text):
    """Every "data merge block x y z {...}" line of a function."""
    return [m for m in (DATA_MERGE.search(line.strip()) for line in text.splitlines()) if m]


def inside(p, lo, hi, pad=0):
    return all(lo[i] - pad <= p[i] <= hi[i] + pad for i in range(3))


def all_functions(fn_root):
    return {os.path.relpath(p, fn_root)[:-len('.mcfunction')].replace(os.sep, '/'): p
            for p in glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True)}


def marker_positions(fn_root, tag):
    out = []
    pattern = re.compile(r"summon minecraft:marker " + COORD + " " + COORD + " " + COORD + r" \{Tags:\[([^\]]*)\]")
    for path in glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True):
        for m in pattern.finditer(read(path)):
            if tag in [t.strip().strip('"') for t in m.group(4).split(',')]:
                out.append(tuple(float(v) for v in m.group(1, 2, 3)))
    return out


# ------------------------------------------------------------------ song easter eggs
PLAYSOUND = re.compile(r"playsound (?:minecraft:)?([a-z0-9_.]+) (\w+)")
EGG_COUNT = re.compile(r"Eggs?_Interacted=(\d+)\}")


def parse_song_egg(fn_root, world, index, lo, hi):
    """unique/song/egg*.mcfunction: each one is run by clicking an egg (a sign the function then blanks, or a
    button over a command block). The music-category playsound is the song; Eggs_Interacted=N is how many
    clicks it takes."""
    eggs = sorted(glob.glob(os.path.join(fn_root, 'unique', 'song', 'egg*.mcfunction')))
    if not eggs:
        return None
    sound, needed, spots = None, 0, []
    for path in eggs:
        text = read(path)
        name = 'unique/song/' + os.path.splitext(os.path.basename(path))[0]
        needed = max([needed] + [int(n) for n in EGG_COUNT.findall(text)])
        for line in text.splitlines():
            m = PLAYSOUND.search(line)
            if m and (m.group(2) == 'music' or 'Interacted=%d}' % needed in line):
                sound = m.group(1)
        found = [tuple(map(int, m.groups()[:3])) for m in merges(text) if '"text":""' in m.group(4).replace(' ', '')]
        calls = re.compile(r"function custom:" + re.escape(name) + r"\b")
        for pos, clicks in index['click_signs']:
            if inside(pos, lo, hi) and any(calls.search(c) for c in clicks):
                found.append(pos)
        for pos, command in index['commands']:
            if inside(pos, lo, hi) and calls.search(command):
                found.append(switch_near(world, pos) or (pos[0], pos[1] + 1, pos[2]))
        for pos in found:
            if pos not in spots:
                spots.append(pos)
                break
    if not sound or not spots:
        return None
    needed = max(needed, len(spots))
    steps = [dict(kind='interact', x=p[0], y=p[1], z=p[2], amount=0, visual='teddy', description='???')
             for p in (spots * needed)[:needed]]
    return {'id': 'song_egg', 'name': 'Song Egg',
            'options': {'ordered': False, 'time_limit_seconds': 0, 'reset_on_fail': False, 'all_players': False,
                        'repeatable': False, 'simultaneous': False},
            'steps': steps, 'targets': [{'kind': 'play_song', 'value': 'minecraft:' + sound}]}


# ------------------------------------------------------------------ perk placement pools
WALL_SIGN_SET = re.compile(r"setblock (-?\d+) (-?\d+) (-?\d+) (minecraft:[a-z_]+_wall_sign\[facing=([a-z]+)\])(\{.*\})")


def parse_perk_pool(fn_root):
    """unique/perk_placement/<perk>/<perk><place>.mcfunction builds perk <perk> at <place>. Every place becomes
    one pool spot and every perk gets its sign at a different place (the pool hides them at game start)."""
    folder = os.path.join(fn_root, 'unique', 'perk_placement')
    by_perk = {}
    for path in sorted(glob.glob(os.path.join(folder, '*', '*.mcfunction'))):
        perk = os.path.basename(os.path.dirname(path))
        place = os.path.splitext(os.path.basename(path))[0]
        place = place[len(perk):] if place.startswith(perk) else place
        m = WALL_SIGN_SET.search(read(path))
        if not m:
            continue
        lines = ['', '', '', '']
        for idx, comp in re.findall(r"Text(\d):'((?:[^'\\]|\\.)*)'", m.group(6)):
            lines[int(idx) - 1] = json_text(comp)
        by_perk.setdefault(perk, {})[place] = {'pos': tuple(map(int, m.group(1, 2, 3))), 'facing': m.group(5).upper(),
                                               'state': m.group(4), 'lines': lines}
    if not by_perk:
        return None
    perks = sorted(by_perk)
    places = sorted({p for spots in by_perk.values() for p in spots})
    spots, signs = [], []
    for i, place in enumerate(places):
        owner = perks[i % len(perks)]
        spot = by_perk[owner].get(place) or next(v[place] for v in by_perk.values() if place in v)
        spots.append(spot)
        if i < len(perks) and place in by_perk[owner]:
            signs.append(spot)
    return {'spots': spots, 'signs': signs, 'perks': perks, 'places': places}


# ------------------------------------------------------------------ map-specific traps (unique/<trap>/)
KILL_SELECTOR = re.compile(r"@[ae]\[[^\]]*?x=(-?[\d.]+),y=(-?[\d.]+),z=(-?[\d.]+),distance=(?:[\d.]+)?\.\.([\d.]+)[^\]]*\] add \w*Kill")
CLONE = re.compile(r"clone (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+)")
SCORE_AT = re.compile(r"scores=\{([A-Za-z_]+)=(\d+)(?:\.\.(\d+))?")
UNIQUE_TRAP_DIRS = (('flogger', 'flogger'), ('centrifuge', 'centrifuge'), ('spike', 'spikes'))


def kill_boxes(text):
    out = []
    for m in KILL_SELECTOR.finditer(text):
        x, y, z, r = (float(v) for v in m.groups())
        out.append(({'x': math.floor(x - r), 'y': math.floor(y - 1), 'z': math.floor(z - r)},
                    {'x': math.floor(x + r), 'y': math.floor(y + 3), 'z': math.floor(z + r)}))
    return out


def fbox(box):
    return {'p1': {k: float(v) for k, v in box[0].items()}, 'p2': {k: float(v) for k, v in box[1].items()}}


def trap_window(texts, blocks_only):
    """Ticks the trap is dangerous in its own clock: the score range of the lines that kill or raise its blocks,
    and the length of the whole cycle."""
    active, period = [], 0
    for text in texts:
        for line in text.splitlines():
            if 'playsound' in line or 'particle' in line:
                continue
            m = SCORE_AT.search(line)
            if not m:
                continue
            hit = KILL_SELECTOR.search(line) or (blocks_only and (FILL_ANY.search(line) or SETBLOCK_ANY.search(line)))
            if hit:
                active += [int(m.group(2)), int(m.group(3) or m.group(2))]
        for m in re.finditer(r"scores=\{\w+=(\d+)\.\.\}\] \w+ 0", text):
            period = max(period, int(m.group(1)))
        for m in re.finditer(r"scoreboard players set @a \w+ (\d+)", text):
            period = max(period, int(m.group(1)))
    return (min(active), max(active)) if active else None, period


def parse_unique_traps(fn_root):
    """Traps the map runs from its own folder (unique/flogger, unique/centrifuge, unique/spikes/<side>): bought
    ones have a *buy function (price, signs it rewrites); the others run by themselves on their clock."""
    traps = []
    for folder in sorted(glob.glob(os.path.join(fn_root, 'unique', '*'))):
        name = os.path.basename(folder).lower()
        kind = next((t for key, t in UNIQUE_TRAP_DIRS if key in name), None)
        if not kind or not os.path.isdir(folder):
            continue
        subdirs = sorted(d for d in glob.glob(os.path.join(folder, '*')) if os.path.isdir(d))
        for part in subdirs or [folder]:
            files = sorted(glob.glob(os.path.join(part, '**', '*.mcfunction'), recursive=True))
            texts = [read(f) for f in files]
            buy = '\n'.join(read(f) for f in files if 'buy' in os.path.basename(f))
            regions = []
            for text in texts:
                regions += kill_boxes(text)
            blocks_only = not regions
            if blocks_only:
                raised = set()
                for text in texts:
                    for line in text.splitlines():
                        raised.update(p for p, state in block_writes(line).items() if state != 'minecraft:air' and 'light' not in state)
                groups = clusters(sorted(raised), reach=4)
                regions = [box_of(c) for c in groups if len(c) * 4 >= len(groups[0])]
            if not regions:
                continue
            price = PRICE.search(buy)
            window, period = trap_window(texts, blocks_only)
            timers = sorted({int(t) for t in re.findall(r"Timer=(\d+)\}", '\n'.join(texts))})
            if price and len(timers) >= 2:
                duration, cooldown = timers[0] // 20, (timers[-1] - timers[0]) // 20
            elif window:
                duration = max(1, int(math.ceil((window[1] - window[0]) / 20.0)))
                cooldown = max(1, int(round(period / 20.0)) - duration) if period else 30
            else:
                duration, cooldown = -1, -1
            signs = sorted({tuple(map(int, m.group(1, 2, 3))) for m in merges(buy)})
            label = os.path.relpath(part, os.path.join(fn_root, 'unique')).replace(os.sep, '_')
            traps.append({'id': label, 'type': kind, 'cost': int(price.group(1)) if price else 0, 'automatic': not price,
                          'signs': signs, 'regions': regions, 'duration': duration, 'cooldown': cooldown})
    return traps


# ------------------------------------------------------------------ special enemies
SPECIAL_KEYS = (('astro', 'astronaut'), ('napalm', 'napalm'), ('shriek', 'shrieker'), ('thief', 'thief'),
                ('space_monkey', 'space_monkey'), ('george', 'george_romero'))


def parse_specials(fn_root):
    """Boss/special enemy functions of the map -> special_enemies rules (catalogue ids from specials.json)."""
    files = [f for f in glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True)
             if os.sep + 'unique' + os.sep in f or os.sep + os.path.join('maps', 'enemies') + os.sep in f]
    rules = []
    for key, special in SPECIAL_KEYS:
        mine = [f for f in files if key in os.path.relpath(f, fn_root).lower()]
        if not mine:
            continue
        texts = '\n'.join(read(f) for f in mine)
        rounds = [int(r) for r in re.findall(r"Rounds=(\d+)\b(?!\.\.)", ''.join(read(f) for f in mine if 'clock' in f))]
        first = min(rounds) if rounds else 1
        odds = re.search(r"boss/1_to_(\d+)", texts)
        if special in ('thief', 'space_monkey'):
            rule = {'type': special, 'mode': 'replaces_dog_round', 'value': 100, 'max_alive': 1, 'first_round': 1, 'own_round': True}
        elif special == 'george_romero':
            rule = {'type': special, 'mode': 'once_at_round', 'value': 1, 'max_alive': 1, 'first_round': 1, 'own_round': False}
        else:
            rule = {'type': special, 'mode': 'chance_per_round', 'value': 100 // int(odds.group(1)) if odds else 50,
                    'max_alive': 1, 'first_round': first, 'own_round': False}
        rules.append(rule)
    return rules


# ------------------------------------------------------------------ landers, elevators, jump pads
PLAYER_TP = re.compile(r"tp @[ap]\[([^\]]*)\] (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)")
TAG_BOX = re.compile(r"tag @[ae]\[[^\]]*?x=(-?[\d.]+),dx=(-?[\d.]+),y=(-?[\d.]+),dy=(-?[\d.]+),z=(-?[\d.]+),dz=(-?[\d.]+)[^\]]*\] (add|remove) (\w+)")
TAG_SPHERE = re.compile(r"tag @[ae]\[x=(-?[\d.]+),y=(-?[\d.]+),z=(-?[\d.]+),distance=[\d.]*\.\.[\d.]+\] add (\w+)")


def mean(points):
    return tuple(sum(p[i] for p in points) / len(points) for i in range(3))


def tag_box(m):
    x, dx, y, dy, z, dz = (float(v) for v in m.groups()[:6])
    return ((math.floor(min(x, x + dx)), math.floor(min(y, y + dy)), math.floor(min(z, z + dz))),
            (math.floor(max(x, x + dx)), math.floor(max(y, y + dy)), math.floor(max(z, z + dz))))


def arc(a, b, lift=20.0):
    """Up from a, across above both, down onto b."""
    top = max(a[1], b[1]) + lift
    mid = ((a[0] + b[0]) / 2.0, top + 4, (a[2] + b[2]) / 2.0)
    return [a, (a[0], top, a[2]), mid, (b[0], top, b[2]), b]


def transport(name, style, points, cost, two_way, requires_power=False):
    return {'name': name, 'style': style, 'points': [tuple(round(v, 2) for v in p) for p in points], 'cost': cost,
            'two_way': two_way, 'requires_power': requires_power}


def parse_landers(fn_root):
    """unique/landers/launch/lander_N_launch: where the riders stand on pad N. Pad 0 is the main pad; a lander
    flies from it to every other pad and back."""
    pads = {}
    for path in glob.glob(os.path.join(fn_root, 'unique', '*lander*', '**', '*launch*.mcfunction'), recursive=True):
        n = re.search(r'(\d+)', os.path.basename(path))
        riders = [tuple(float(v) for v in m.group(2, 3, 4)) for m in PLAYER_TP.finditer(read(path)) if 'tag=Player' in m.group(1)]
        if n and riders:
            pads[int(n.group(1))] = mean(riders[:4])
    if len(pads) < 2:
        return []
    buy = '\n'.join(read(f) for f in glob.glob(os.path.join(fn_root, 'unique', '*lander*', '**', '*buy*.mcfunction'), recursive=True))
    price = PRICE.search(buy)
    power = read(os.path.join(fn_root, 'maps', 'power', 'power.mcfunction')) if os.path.exists(os.path.join(fn_root, 'maps', 'power', 'power.mcfunction')) else ''
    start = pads[min(pads)]
    return [transport('Lander %d' % n, 'platform', arc(start, pads[n]), int(price.group(1)) if price else 0, True,
                      'lander' in power.lower()) for n in sorted(pads) if n != min(pads)]


def parse_elevators(fn_root):
    """unique/elevator/<car>/*_to_*: each move tags the riders standing on the car at one floor; every floor of
    a car is linked to the next one."""
    out = []
    for car in sorted(glob.glob(os.path.join(fn_root, 'unique', 'elevator*', '*'))):
        if not os.path.isdir(car):
            continue
        floors = []
        for path in sorted(glob.glob(os.path.join(car, '*_to_*.mcfunction'))):
            for m in TAG_SPHERE.finditer(read(path)):
                p = (float(m.group(1)), float(m.group(2)), float(m.group(3)))
                if not any(close(p, f) for f in floors):
                    floors.append(p)
        floors.sort(key=lambda p: p[1])
        for a, b in zip(floors, floors[1:]):
            out.append(transport('Elevator %s' % os.path.basename(car), 'teleport', [a, b], 0, True))
    return out


def parse_jump_pads(fn_root):
    """unique/**/jump_pads: a pad tags the players inside its box, then moves them with absolute tps ordered by
    their own counter. Each pad becomes a one-way ride along those points."""
    out = []
    for path in glob.glob(os.path.join(fn_root, 'unique', '**', '*jump_pad*', '*.mcfunction'), recursive=True):
        text = read(path)
        for m in TAG_BOX.finditer(text):
            if m.group(7) != 'add':
                continue
            tag = m.group(8)
            lo_c, hi_c = tag_box(m)
            start = ((lo_c[0] + hi_c[0]) / 2.0 + 0.5, float(lo_c[1]), (lo_c[2] + hi_c[2]) / 2.0 + 0.5)
            steps = {}
            for t in re.finditer(r"tp @e\[tag=" + tag + r"\b[^\]]*scores=\{\w+=(\d+)\}\] (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)", text):
                steps[int(t.group(1))] = tuple(float(v) for v in t.group(2, 3, 4))
            if steps:
                out.append(transport('Jump pad %s' % tag.split('_')[-1], 'platform', [start] + [steps[k] for k in sorted(steps)], 0, False))
    return out


# ------------------------------------------------------------------ low gravity / no air (moon)
def parse_environment(fn_root, lo, hi, anchors):
    """A function that gives players Slow Falling + Jump Boost and tags air pockets (tag @e[x,dx,..] add <Air>):
    the pockets become air_pocket zones with normal gravity inside one no-air low-gravity zone around them.
    When the effects are not tied to a score like On_Moon, the gravity is arena-wide instead."""
    for path in glob.glob(os.path.join(fn_root, 'unique', '**', '*.mcfunction'), recursive=True):
        text = read(path)
        if 'slow_falling' not in text or 'jump_boost' not in text:
            continue
        pockets, air_tag = [], None
        for m in TAG_BOX.finditer(text):
            if m.group(7) == 'add':
                air_tag = air_tag or m.group(8)
                if m.group(8) == air_tag:
                    box = tag_box(m)
                    if box not in pockets:
                        pockets.append(box)
        scoped = any('effect give' in line and re.search(r"scores=\{On_\w+=1", line) for line in text.splitlines())
        if not pockets:
            return {'settings': {'gravity': 0.4}, 'zones': []}
        pts = [p for b in pockets for p in b] + list(anchors)
        zlo = [max(lo[i], min(p[i] for p in pts) - 24) for i in range(3)]
        zhi = [min(hi[i], max(p[i] for p in pts) + 24) for i in range(3)]
        zlo[1], zhi[1] = lo[1], hi[1]
        zones = [{'id': 'zone_low_gravity', 'name': '', 'p1': loc(zlo), 'p2': loc(zhi), 'gravity': 0.4,
                  'no_air': True, 'damage_per_second': 0.0}]
        for i, (a, b) in enumerate(pockets, 1):
            zones.append({'id': 'zone_air_%d' % i, 'name': '', 'p1': loc(a), 'p2': loc(b), 'gravity': 1.0,
                          'no_air': False, 'air_pocket': True, 'damage_per_second': 0.0})
        return {'settings': {} if scoped else {'gravity': 0.4}, 'zones': zones, 'source': os.path.basename(path)}
    return None


# ------------------------------------------------------------------ Pack-a-Punch gates
def switch_group(fn_root, world, index, lo, hi):
    """Command blocks running unique/<dir>/<name>_<N> for several N (DEFCON switches): the switches, the score
    they raise and the functions that score unlocks once it reaches its top value."""
    groups = {}
    for pos, command in index['commands']:
        m = re.search(r"function custom:(unique/([a-z_]+)/[a-z_]+?)_(\d+)\b", command)
        if m and inside(pos, lo, hi) and m.group(2) not in ('song', 'power'):
            groups.setdefault(m.group(1), {})[int(m.group(3))] = switch_near(world, pos) or (pos[0], pos[1] + 1, pos[2])
    funcs = all_functions(fn_root)
    for base, switches in groups.items():
        if len(switches) < 2:
            continue
        text = read(function_path(fn_root, '%s_%d' % (base, min(switches))))
        score = re.search(r"scoreboard players add @a (\w+) 1", text)
        if not score:
            continue
        top = len(switches) + 1
        unlocked = set()
        for path in funcs.values():
            for line in read(path).splitlines():
                if re.search(score.group(1) + r"=%d\b" % top, line):
                    unlocked.update(FUNCTION_CALL.findall(line))
        return {'switches': [switches[k] for k in sorted(switches)], 'unlocks': sorted(unlocked)}
    return None


def plate_gate(fn_root):
    """Pressure plates placed on markers (execute at @e[tag=<Plate>] run setblock ~ ~ ~ *pressure_plate) by a
    Pack-a-Punch function: standing on each opens it."""
    for path in glob.glob(os.path.join(fn_root, 'unique', 'pap', '**', '*.mcfunction'), recursive=True):
        m = re.search(r"at @e\[tag=(\w+)[^\]]*\] run setblock ~ ~ ~ (?:minecraft:)?\w*pressure_plate", read(path))
        if m:
            plates = marker_positions(fn_root, m.group(1))
            name = os.path.relpath(path, fn_root)[:-len('.mcfunction')].replace(os.sep, '/')
            power = any(name in read(p) for p in glob.glob(os.path.join(fn_root, 'maps', 'power', '*.mcfunction')))
            return {'plates': [tuple(int(math.floor(v)) for v in p) for p in plates], 'power': power}
    return None


def link_chain(fn_root):
    """A sign the power function writes whose click runs a *link* function, that writes the next sign, and so
    on (Kino's "Initiate link" -> "Link pad"): the signs to press in order."""
    power = os.path.join(fn_root, 'maps', 'power', 'power.mcfunction')
    if not os.path.exists(power):
        return None
    chain, text, seen = [], read(power), set()
    while True:
        nxt = None
        for m in merges(text):
            call = re.search(r"function custom:([a-z0-9_/]+)", m.group(4))
            if call and 'link' in call.group(1).split('/')[-1] and call.group(1) not in seen:
                nxt = (tuple(map(int, m.group(1, 2, 3))), call.group(1))
                break
        if not nxt:
            break
        seen.add(nxt[1])
        chain.append(nxt)
        path = function_path(fn_root, nxt[1])
        if not os.path.exists(path):
            break
        text = read(path)
    if not chain:
        return None
    folder = os.path.dirname(function_path(fn_root, chain[0][1]))
    opens_pap = any('pap' in read(p).lower() for p in glob.glob(os.path.join(folder, '*.mcfunction')))
    return {'signs': [p for p, _ in chain], 'pap': opens_pap, 'folder': os.path.basename(folder)}


def pap_marker_sign(world, fn_root):
    """A Pack-a-Punch sign the map only writes on its PaP marker mid-game (data merge block ~ ~ ~): the sign,
    with the block state of the clone source that puts the sign there."""
    for path in glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True):
        text = read(path)
        m = re.search(r"as @e\[tag=(\w+)\] at @s run data merge block ~ ~ ~ (\{.*Pack-a-Punch.*\})", text)
        if not m:
            continue
        for pos in marker_positions(fn_root, m.group(1)):
            pos = tuple(int(math.floor(v)) for v in pos)
            if 'sign' in world.block(*pos):
                continue
            lines = ['', '', '', '']
            for idx, comp in re.findall(r"Text(\d):'((?:[^'\\]|\\.)*)'", m.group(2)):
                lines[int(idx) - 1] = json_text(comp)
            if not has_cost(lines):
                lines[3] = '[Cost: 5000]'
            state = None
            for c in CLONE.finditer(text):
                x0, y0, z0, x1, y1, z1, dx, dy, dz = map(int, c.groups())
                sx, sy, sz = pos[0] - dx + min(x0, x1), pos[1] - dy + min(y0, y1), pos[2] - dz + min(z0, z1)
                if min(x0, x1) <= sx <= max(x0, x1) and min(y0, y1) <= sy <= max(y0, y1) and min(z0, z1) <= sz <= max(z0, z1):
                    found = world.block(sx, sy, sz)
                    if 'sign' in found:
                        state = found
            return {'x': pos[0], 'y': pos[1], 'z': pos[2], 'lines': lines, 'blockdata': state or 'minecraft:oak_sign[rotation=0]'}
    return None


# ------------------------------------------------------------------ travel functions
PER_PLAYER = re.compile(r"tag=Player(?:One|Two|Three|Four)\b|scores=\{Teleroom=\d+\}")
PAD_MARKER = re.compile(r"as @e\[tag=(\w+)\] at @s run scoreboard players set @a\[distance=\.\.[\d.]+")
SPECIAL_TRAVEL = ('lander', 'elevator', 'jump_pad')


def travel_steps(text):
    """Absolute player teleports grouped into steps: one per timer value, or one per run of per-player lines
    (PlayerOne..Four land a few blocks apart on the same spot). Each step is the mean of its teleports."""
    steps, keys, source, everyone = {}, [], None, False
    group, last_player = 0, False
    for line in text.splitlines():
        m = TP_ANY.search(line)
        if not m:
            last_player = False
            continue
        step = STEP.search(line)
        player_line = PER_PLAYER.search(m.group(2) or '') is not None
        if step:
            key = int(step.group(1))
        else:
            if not (player_line and last_player):
                group += 1
            key = ('order', group)
        last_player = player_line
        if key not in steps:
            steps[key] = []
            keys.append(key)
        steps[key].append(tuple(float(v) for v in m.group(3, 4, 5)))
        sel = SELECTOR_POS.search(m.group(2) or '')
        if source is None and sel:
            source = tuple(float(v) for v in sel.groups())
        everyone = everyone or m.group(1) == '@a'
    return [(k, mean(steps[k])) for k in keys], source, everyone


def pad_source(fn_root, folder):
    """Teleporters whose riders are picked by standing near a pad marker (as @e[tag=To_Moon] at @s ... @a[distance=..3])."""
    for path in glob.glob(os.path.join(folder, '*.mcfunction')):
        m = PAD_MARKER.search(read(path))
        if m:
            found = marker_positions(fn_root, m.group(1))
            if found:
                return found[0]
    return None


def parse_transports(fn_root):
    """Absolute player teleports in the map's travel functions. Short hops in a row are a ride along a path;
    otherwise it is a teleporter to its last stop (the longest stop of a tour)."""
    out = []
    names = ('zipline', 'teleporter', 'travel', 'minecart', 'slide')
    files = [f for f in glob.glob(os.path.join(fn_root, 'unique', '**', '*.mcfunction'), recursive=True)
             if any(k in f.lower() for k in names) and not any(k in f.lower() for k in SPECIAL_TRAVEL)]
    for path in sorted(files):
        steps, source, everyone = travel_steps(read(path))
        if not steps:
            continue
        if source is None:
            source = pad_source(fn_root, os.path.dirname(path))
        points = [p for _, p in steps]
        if source is not None:
            points.insert(0, source)
        if len(points) < 2:
            continue
        hops = [max(abs(points[i + 1][j] - points[i][j]) for j in range(3)) for i in range(len(points) - 1)]
        label = os.path.splitext(os.path.basename(path))[0]
        buy = next((read(f) for f in glob.glob(os.path.join(os.path.dirname(path), '*buy*.mcfunction'))), '')
        price = PRICE.search(buy)
        if len(points) >= 3 and max(hops) <= 16 and 'teleport' not in label.lower():
            style = 'cart' if 'minecart' in path.lower() else ('platform' if everyone else 'zipline')
        else:
            style = 'teleport'
            numeric = [(k, p) for k, p in steps if isinstance(k, int)]
            # A tour (Kino: projector room, stage, side room, back) ends where it started; its longest stop is
            # the destination. Shorter trips pass a waiting room on the way to their last stop.
            if len(numeric) >= 3:
                dwell = [(numeric[i + 1][0] - numeric[i][0], numeric[i][1]) for i in range(len(numeric) - 1)]
                target = max(dwell, key=lambda d: d[0])[1]
            else:
                target = points[-1]
            points = [points[0], target]
        out.append({'name': 'Imported ' + label, 'style': style, 'points': points, 'cost': int(price.group(1)) if price else 0})
    return dedupe_transports(out)


def dedupe_transports(transports):
    """Drop repeats and fold a reverse path (to-dock / to-main) into one two-way transport. A teleporter that
    has a separate way back (its own return pad) stays one-way."""
    kept = []
    for t in transports:
        a, b = t['points'][0], t['points'][-1]
        twin = None
        for k in kept:
            ka, kb = k['points'][0], k['points'][-1]
            if close(a, ka) and close(b, kb):
                twin = k
                break
            if close(a, kb) and close(b, ka):
                k['two_way'] = True
                twin = k
                break
        if twin is None:
            t.setdefault('two_way', t['style'] == 'teleport')
            kept.append(t)
    for t in kept:
        for k in kept:
            if k is not t and t['style'] == k['style'] == 'teleport' and close(k['points'][-1], t['points'][0], 12):
                t['two_way'] = k['two_way'] = False
    return kept


# ------------------------------------------------------------------ assembly
def facing_towards(dx, dz):
    if abs(dx) >= abs(dz):
        return 'EAST' if dx > 0 else 'WEST'
    return 'SOUTH' if dz > 0 else 'NORTH'


def loc(p):
    return {'x': float(p[0]), 'y': float(p[1]), 'z': float(p[2])}


def block_json(p, state):
    return {'x': float(p[0]), 'y': float(p[1]), 'z': float(p[2]), 'blockdata': state, 'material': state.split('[')[0].split(':')[1]}


def build(world, datapacks, map_name, arena, world_name, index):
    fn_root = os.path.join(datapacks, map_name, 'data', 'custom', 'functions')
    report = []
    spawns = {}

    def spawn_id(pos):
        if pos not in spawns:
            spawns[pos] = rid()
        return spawns[pos]

    window_spawns = {}
    door_spawn_positions = set()

    # The map rebuilds its doors when a game starts, so the saved world can hold them open.
    reset_state = {}
    for path in glob.glob(os.path.join(fn_root, 'unique', 'reset*.mcfunction')):
        for pos, state in block_writes(read(path)).items():
            if state != 'minecraft:air':
                reset_state[pos] = state

    doors = []
    for n, paths in sorted(door_functions(fn_root).items()):
        price, cleared, door_spawns = parse_door(paths)
        blocks = [(p, reset_state.get(p) or world.block(*p)) for p in sorted(cleared)]
        blocks = [(p, s) for p, s in blocks if s != 'minecraft:air' and not s.endswith('_sign') and 'command_block' not in s]
        ids = []
        for pos, window in door_spawns:
            ids.append(spawn_id(pos))
            door_spawn_positions.add(pos)
            if window:
                window_spawns.setdefault(window, set()).add(pos)
        doors.append({'n': n, 'price': price or 1000, 'blocks': blocks, 'spawns': sorted(set(ids))})

    start_spawns = []
    for path in glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True):
        if os.sep + 'doors' + os.sep in path:
            continue
        for pos, window in spawners_in(read(path)):
            if pos in door_spawn_positions:
                continue
            spawn_id(pos)
            start_spawns.append(pos)
            if window:
                window_spawns.setdefault(window, set()).add(pos)

    barriers = []
    for path in sorted(glob.glob(os.path.join(fn_root, 'unique', 'barricades', 'window_*.mcfunction'))):
        w = 'w' + re.search(r'window_(\d+)', path).group(1)
        boards = parse_window(path)
        if not boards:
            continue
        cx = sum(p[0] for p in boards) / len(boards)
        cy = min(p[1] for p in boards)
        cz = sum(p[2] for p in boards) / len(boards)
        linked = sorted(window_spawns.get(w, []))
        if linked:
            sx, _, sz = linked[0]
            dx, dz = cx - sx, cz - sz
        else:
            dx, dz = 1, 0
            report.append('window %s has no spawner; repair side guessed' % w)
        facing = facing_towards(dx, dz)
        step = {'EAST': (1, 0), 'WEST': (-1, 0), 'SOUTH': (0, 1), 'NORTH': (0, -1)}[facing]
        repair = (math.floor(cx) + step[0], cy, math.floor(cz) + step[1])
        barriers.append({
            'id': rid(), 'repair_loc': loc(repair), 'repair_facing': facing,
            'blocks': [block_json(p, s) for p, s in sorted(boards.items())],
            'spawns': [spawns[p] for p in linked], 'reward': 0, '_window': w})

    boxes, box_edits = parse_boxes(fn_root)
    start = start_position(fn_root)
    anchors = list(spawns) + [p for p, _ in boxes] + \
        [(int(x['x']), int(x['y']), int(x['z'])) for b in barriers for x in b['blocks']]
    if start:
        anchors.append(tuple(int(v) for v in start[:3]))
    if not anchors:
        raise SystemExit('nothing found for ' + map_name)

    # Door signs: the original world's signs whose click event tags D<N>_B.
    scan_lo = [min(p[i] for p in anchors) - 40 for i in range(3)]
    scan_hi = [max(p[i] for p in anchors) + 40 for i in range(3)]
    door_signs = {}
    for be in world.signs_in(scan_lo, scan_hi):
        m = re.search(r'add D(\d+)_B', sign_raw(be))
        if m:
            door_signs.setdefault(int(m.group(1)), []).append((int(be['x']), int(be['y']), int(be['z'])))

    # Some door functions also clear blocks in a different map (copy-paste leftovers in the datapack);
    # blocks outside this map's footprint are not part of the door.
    foot_lo = [min(p[i] for p in anchors) - MAP_MARGIN for i in range(3)]
    foot_hi = [max(p[i] for p in anchors) + MAP_MARGIN for i in range(3)]
    for d in doors:
        kept = [(p, st) for p, st in d['blocks'] if foot_lo[0] <= p[0] <= foot_hi[0] and foot_lo[2] <= p[2] <= foot_hi[2]]
        if len(kept) < len(d['blocks']):
            report.append('door %d: dropped %d blocks outside the map' % (d['n'], len(d['blocks']) - len(kept)))
        d['blocks'] = kept

    every = anchors + [p for d in doors for p, _ in d['blocks']]
    margin = 12
    lo = [min(p[i] for p in every) - margin for i in range(3)]
    hi = [max(p[i] for p in every) + margin for i in range(3)]
    lo[1] = min(p[1] for p in every) - 6
    hi[1] = max(p[1] for p in every) + 14

    door_json = []
    for d in doors:
        if not d['blocks']:
            report.append('door %d: no solid blocks found, skipped' % d['n'])
            continue
        door_json.append({'id': rid(), 'powerRequired': False, 'price': d['price'],
                          'blocks': [block_json(p, s) for p, s in d['blocks']],
                          'signs': [loc(p) for p in door_signs.get(d['n'], [])],
                          'spawns': d['spawns']})
        report.append('door %d: %d blocks, %d signs, %d spawns, %d points' % (d['n'], len(d['blocks']), len(door_signs.get(d['n'], [])), len(d['spawns']), d['price']))

    spawn_json = [dict(loc(p), world=world_name, id=i) for p, i in spawns.items()]
    box_json = [dict(loc(p), facing=f, cost=950, id=rid()) for p, f in boxes]

    signs = []
    for pos, lines in merged_signs(fn_root).items():
        if all(lo[i] <= pos[i] <= hi[i] for i in range(3)):
            signs.append({'x': pos[0], 'y': pos[1], 'z': pos[2], 'lines': lines})
    for pos in power_blocks(world, index):
        if all(lo[i] <= pos[i] <= hi[i] for i in range(3)):
            signs.append({'x': pos[0], 'y': pos[1], 'z': pos[2], 'lines': ['[Zombies]', 'Power', '', ''], 'power': True})
            report.append('power switch near %s' % (pos,))

    merged = merged_signs_all(fn_root)
    trap_json = []

    def write_trap_signs(trap_id, positions, cost):
        nonlocal signs
        signs = [x for x in signs if (x['x'], x['y'], x['z']) not in positions]
        for p in positions:
            signs.append({'x': p[0], 'y': p[1], 'z': p[2], 'lines': ['[Zombies]', 'Trap', trap_id, str(cost)]})

    for t in parse_traps(fn_root, merged):
        trap_id = 'trap%s' % t['n']
        main = t['regions'][0]
        first = t['signs'][0]
        trap_json.append({'x': float(first[0]), 'y': float(first[1]), 'z': float(first[2]), 'id': trap_id,
                          'cost': t['cost'], 'type': t['type'], 'requires_power': True,
                          'duration_seconds': t['duration'], 'cooldown_seconds': t['cooldown'],
                          'region': {'p1': main[0], 'p2': main[1]},
                          'extra_regions': [{'p1': r[0], 'p2': r[1]} for r in t['regions'][1:]]})
        write_trap_signs(trap_id, t['signs'], t['cost'])
        report.append('trap %s: %s (%s), %d area(s), %d sign(s), %d points' % (t['n'], t['name'], t['type'], len(t['regions']), len(t['signs']), t['cost']))

    for t in parse_unique_traps(fn_root):
        main = t['regions'][0]
        anchor = t['signs'][0] if t['signs'] else tuple((main[0][k] + main[1][k]) // 2 for k in 'xyz')
        trap_json.append({'x': float(anchor[0]), 'y': float(anchor[1]), 'z': float(anchor[2]), 'id': t['id'],
                          'cost': t['cost'], 'type': t['type'], 'requires_power': not t['automatic'],
                          'automatic': t['automatic'], 'duration_seconds': t['duration'], 'cooldown_seconds': t['cooldown'],
                          'region': fbox(main), 'extra_regions': [fbox(r) for r in t['regions'][1:]]})
        write_trap_signs(t['id'], t['signs'], t['cost'])
        report.append('trap %s: %s, %s, %d area(s), %ds on / %ds off' % (
            t['id'], t['type'], 'automatic' if t['automatic'] else '%d points, %d sign(s)' % (t['cost'], len(t['signs'])),
            len(t['regions']), t['duration'], t['cooldown']))

    transport_json = []
    speeds = {'zipline': 8.0, 'platform': 4.0, 'cart': 6.0, 'teleport': 4.0}
    travel = parse_transports(fn_root) + parse_landers(fn_root) + parse_elevators(fn_root) + parse_jump_pads(fn_root)
    for t in travel:
        if not all(lo[0] - 40 <= p[0] <= hi[0] + 40 and lo[2] - 40 <= p[2] <= hi[2] + 40 for p in t['points']):
            continue
        t['id'] = re.sub(r'[^a-z0-9]+', '_', t['name'].lower().replace('imported ', '')).strip('_')
        transport_json.append({'id': t['id'], 'name': t['name'], 'style': t['style'], 'cost': t['cost'],
                               'requires_power': t.get('requires_power', False), 'cooldown_seconds': 0, 'speed': speeds[t['style']],
                               'two_way': t.get('two_way', False), 'platform_size': 3,
                               'waypoints': [{'x': p[0], 'y': p[1], 'z': p[2]} for p in t['points']], 'call_points': []})
        report.append('transport %s: %s, %d points%s%s' % (t['name'], t['style'], len(t['points']), ', two-way' if t.get('two_way') else '',
                                                         ', %d points' % t['cost'] if t['cost'] else ''))

    settings = {'pap_locked': False, 'perk_pool_mode': 'fixed'}
    triggers, zones, pool_spots = [], [], []

    egg = parse_song_egg(fn_root, world, index, lo, hi)
    if egg:
        triggers.append(egg)
        report.append('song egg: %d interact(s) at %d spot(s) -> %s' % (len(egg['steps']), len({(s['x'], s['y'], s['z']) for s in egg['steps']}), egg['targets'][0]['value']))

    pool = parse_perk_pool(fn_root)
    if pool:
        settings.update({'perk_pool_mode': 'random_on_open', 'perk_pool_reveal': 'door'})
        pool_spots = [{'x': s['pos'][0], 'y': s['pos'][1], 'z': s['pos'][2], 'facing': s['facing']} for s in pool['spots']]
        for s in pool['signs']:
            signs = [x for x in signs if (x['x'], x['y'], x['z']) != s['pos']]
            signs.append({'x': s['pos'][0], 'y': s['pos'][1], 'z': s['pos'][2], 'lines': s['lines'], 'blockdata': s['state']})
        report.append('perk pool: %d spots (%s), perks %s' % (len(pool_spots), ', '.join(pool['places']), ', '.join(' '.join(s['lines']).split('[')[0].strip() for s in pool['signs'])))

    rules = parse_specials(fn_root)
    if rules:
        settings['special_enemies'] = rules
        report.append('special enemies: ' + ', '.join('%s (%s %d)' % (r['type'], r['mode'], r['value']) for r in rules))

    env = parse_environment(fn_root, lo, hi, [p for t in transport_json if t['name'].startswith('Jump pad') for p in
                                              [(w['x'], w['y'], w['z']) for w in t['waypoints']]])
    if env:
        settings.update(env['settings'])
        zones += env['zones']
        report.append('environment (%s): %d air pocket(s) inside a no-air zone, gravity 0.4%s' % (
            env.get('source', ''), max(0, len(env['zones']) - 1), ' arena-wide' if env['settings'] else ' outside the pockets'))

    def by_label(fragment):
        return [t for t in transport_json if fragment in t['id']]

    gate = None
    has_pap = os.path.isdir(os.path.join(fn_root, 'unique', 'pap'))
    switches = switch_group(fn_root, world, index, lo, hi)
    plates = plate_gate(fn_root)
    link = link_chain(fn_root)
    landers = by_label('lander')
    if switches and any('pap' in f for f in switches['unlocks']):
        gate = {'id': 'pap_switches', 'name': 'Pack-a-Punch switches', 'ordered': False,
                'steps': [{'kind': 'power_on', 'amount': 0}] + [dict(kind='interact', x=p[0], y=p[1], z=p[2], amount=0, description='Switch') for p in switches['switches']],
                'teleporters': [t['id'] for f in switches['unlocks'] for t in transport_json if f.split('/')[-1] in t['id'] and 'pap' in t['id']]}
    elif plates and has_pap:
        gate = {'id': 'pap_plates', 'name': 'Stepping stones', 'ordered': False,
                'steps': ([{'kind': 'power_on', 'amount': 0}] if plates['power'] else []) +
                         [dict(kind='stand_on', x=p[0], y=p[1], z=p[2], amount=1) for p in plates['plates']], 'teleporters': []}
    elif link and link['pap']:
        gate = {'id': 'pap_link', 'name': 'Link the teleporter', 'ordered': True,
                'steps': [{'kind': 'power_on', 'amount': 0}] + [dict(kind='interact', x=p[0], y=p[1], z=p[2], amount=0, description='Link') for p in link['signs']],
                'teleporters': [t['id'] for t in transport_json if t['style'] == 'teleport' and link['folder'].rstrip('s') in t['id']]}
    elif landers and any('pap' in read(f).lower() for f in glob.glob(os.path.join(fn_root, 'unique', '*lander*', '*.mcfunction'))):
        gate = {'id': 'pap_landers', 'name': 'Ride every lander', 'ordered': False,
                'steps': [{'kind': 'ride_transport', 'transport': t['id'], 'amount': 0} for t in landers], 'teleporters': []}
    if gate:
        settings['pap_locked'] = True
        targets = [{'kind': 'enable_pack_a_punch'}] + [{'kind': 'enable_teleporter', 'value': tid} for tid in gate['teleporters']]
        triggers.append({'id': gate['id'], 'name': gate['name'],
                         'options': {'ordered': gate['ordered'], 'time_limit_seconds': 0, 'reset_on_fail': False, 'all_players': False,
                                     'repeatable': False, 'simultaneous': False},
                         'steps': gate['steps'], 'targets': targets})
        report.append('pack-a-punch gate %s: %d step(s)%s' % (gate['name'], len(gate['steps']),
                                                              ', unlocks ' + ', '.join(gate['teleporters']) if gate['teleporters'] else ''))
    elif has_pap:
        report.append('pack-a-punch: map has unique/pap but no gate that can be modelled; left unlocked')

    pap_sign = pap_marker_sign(world, fn_root)
    if pap_sign and inside((pap_sign['x'], pap_sign['y'], pap_sign['z']), lo, hi):
        signs.append(pap_sign)
        report.append('pack-a-punch sign written at %d %d %d' % (pap_sign['x'], pap_sign['y'], pap_sign['z']))

    if start:
        spawn = {'x': start[0] + 0.5, 'y': start[1], 'z': start[2] + 0.5}
    else:
        spawn = loc(next(iter(spawns)))
        report.append('no start position found; using a zombie spawn')

    save = {
        'world_name': world_name,
        'p1': {'x': float(lo[0]), 'y': float(hi[1]), 'z': float(hi[2])},
        'p2': {'x': float(hi[0]), 'y': float(lo[1]), 'z': float(lo[2])},
        'player_spawn': spawn, 'spectator_spawn': spawn, 'lobby_spawn': spawn,
        'power_setup': any(s.get('power') for s in signs),
        'zombie_spawns': spawn_json, 'mystery_boxes': box_json,
        'barriers': [{k: v for k, v in b.items() if not k.startswith('_')} for b in barriers],
        'doors': door_json, 'teleporters': [], 'traps': trap_json, 'transports': transport_json, 'buildables': [],
        'zones': zones, 'triggers': triggers, 'perk_pool_spots': pool_spots,
    }
    report.insert(0, '%s -> %s: %d spawns (%d at start), %d doors, %d barriers, %d box locations, %d signs to write'
                  % (map_name, arena, len(spawn_json), len(start_spawns), len(door_json), len(barriers), len(box_json), len(signs)))
    edits = [{'x': p[0], 'y': p[1], 'z': p[2], 'blockdata': b} for p, b in sorted(box_edits.items())
             if 'barrier' not in b and 'sign' not in b or b == 'minecraft:air']
    edits = [e for e in edits if 'barrier' not in e['blockdata']]
    return {'save_data': save, 'settings': settings, 'signs': signs, 'block_edits': edits, 'report': report}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--world', required=True)
    ap.add_argument('--map', required=True, action='append')
    ap.add_argument('--arena', required=True, action='append')
    ap.add_argument('--world-name', default='zombies')
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    datapacks = os.path.join(a.world, 'datapacks')
    world = World(a.world)
    index = scan_world(a.world)
    os.makedirs(a.out, exist_ok=True)
    for map_name, arena in zip(a.map, a.arena):
        result = build(world, datapacks, map_name, arena, a.world_name, index)
        with open(os.path.join(a.out, arena + '.json'), 'w') as f:
            json.dump(result, f, indent=1)
        print('\n'.join(result['report']))


if __name__ == '__main__':
    main()
