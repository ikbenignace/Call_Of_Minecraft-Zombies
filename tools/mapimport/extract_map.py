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
  - maps/power/power.mcfunction: the perk signs it fills in at power-on ("data merge block").
And from the ORIGINAL world (not the edited server world): door/board block states, the door signs (by
their D<N>_B click tag) and the block under the power command block.

Writes <out>/<arena>.json:
  {"save_data": {...arena save fields...}, "signs": [{"x","y","z","lines":[4],"facing"}], "report": [...]}
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
    """trapNbuy/clock/visuals: price, sign, name, timings and the particle spots that outline the kill area."""
    traps = []
    folder = os.path.join(fn_root, 'maps', 'traps')
    for buy in sorted(glob.glob(os.path.join(folder, 'trap*buy.mcfunction'))):
        n = re.search(r'trap(\d+)buy', buy).group(1)
        text = read(buy)
        clock = read(os.path.join(folder, 'trap%sclock.mcfunction' % n)) if os.path.exists(os.path.join(folder, 'trap%sclock.mcfunction' % n)) else ''
        visuals = read(os.path.join(folder, 'trap%svisuals.mcfunction' % n)) if os.path.exists(os.path.join(folder, 'trap%svisuals.mcfunction' % n)) else ''
        price = PRICE.search(text)
        sign = DATA_MERGE.search(text) or DATA_MERGE.search(clock)
        if not sign:
            continue
        pos = (int(sign.group(1)), int(sign.group(2)), int(sign.group(3)))
        name = (merged.get(pos) or ['Trap'])[0].strip() or 'Trap'
        spots = [(int(float(m.group(2))), int(float(m.group(3))), int(float(m.group(4)))) for m in PARTICLE.finditer(visuals)]
        particle_names = ' '.join(m.group(1) for m in PARTICLE.finditer(visuals))
        if not spots:
            continue
        lowered = (name + ' ' + particle_names).lower()
        kind = next((t for key, t in TRAP_TYPES if key in lowered), 'generic')
        timers = sorted({int(t) for t in TIMER.findall(clock)})
        duration = timers[0] // 20 if timers else -1
        cooldown = (timers[-1] - timers[0]) // 20 if len(timers) > 1 else -1
        regions = [box_of(c) for c in clusters(spots)]
        traps.append({'n': n, 'name': name, 'type': kind, 'cost': int(price.group(1)) if price else 1000,
                      'sign': pos, 'regions': regions, 'duration': duration, 'cooldown': cooldown})
    return traps


TP_ANY = re.compile(r"(?:^|run )tp (@[apes])(\[[^\]]*\])? (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)(?:\s|$)")
SELECTOR_POS = re.compile(r"x=(-?[\d.]+),y=(-?[\d.]+),z=(-?[\d.]+)")
STEP = re.compile(r"scores=\{[A-Za-z_]+=(\d+)\}")


def parse_transports(fn_root):
    """Absolute player teleports in the map's travel functions, ordered by their timer step (one destination per
    step). Short hops in a row are a ride along a path; one long jump is a teleporter/elevator."""
    out = []
    names = ('zipline', 'lander', 'elevator', 'teleporter', 'travel', 'minecart', 'slide')
    files = [f for f in glob.glob(os.path.join(fn_root, 'unique', '**', '*.mcfunction'), recursive=True)
             if any(k in f.lower() for k in names)]
    for path in sorted(files):
        steps = {}
        source = None
        everyone = False
        order = 0
        for line in read(path).splitlines():
            m = TP_ANY.search(line)
            if not m:
                continue
            order += 1
            step = STEP.search(line)
            key = int(step.group(1)) if step else order
            if key in steps:
                continue
            steps[key] = tuple(float(v) for v in m.group(3, 4, 5))
            sel = SELECTOR_POS.search(m.group(2) or '')
            if source is None and sel:
                source = tuple(float(v) for v in sel.groups())
            everyone = everyone or m.group(1) == '@a'
        if not steps:
            continue
        points = [steps[k] for k in sorted(steps)]
        if source is not None:
            points.insert(0, source)
        if len(points) < 2:
            continue
        hops = [max(abs(points[i + 1][j] - points[i][j]) for j in range(3)) for i in range(len(points) - 1)]
        label = os.path.splitext(os.path.basename(path))[0]
        buy = next((read(f) for f in glob.glob(os.path.join(os.path.dirname(path), '*buy*.mcfunction'))), '')
        price = PRICE.search(buy)
        if len(points) >= 3 and max(hops) <= 16 and 'teleport' not in label.lower():
            style = 'cart' if 'minecart' in path.lower() else ('platform' if everyone or 'lander' in path.lower() else 'zipline')
        else:
            style = 'teleport'
            points = [points[0], points[-1]]
        out.append({'name': 'Imported ' + label, 'style': style, 'points': points, 'cost': int(price.group(1)) if price else 0})
    return dedupe_transports(out)


def dedupe_transports(transports):
    """Drop repeats and fold a reverse path (to-dock / to-main) into one two-way transport."""
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
    return kept


def close(a, b, reach=3):
    return max(abs(a[i] - b[i]) for i in range(3)) <= reach



# ------------------------------------------------------------------ assembly
def facing_towards(dx, dz):
    if abs(dx) >= abs(dz):
        return 'EAST' if dx > 0 else 'WEST'
    return 'SOUTH' if dz > 0 else 'NORTH'


def loc(p):
    return {'x': float(p[0]), 'y': float(p[1]), 'z': float(p[2])}


def block_json(p, state):
    return {'x': float(p[0]), 'y': float(p[1]), 'z': float(p[2]), 'blockdata': state, 'material': state.split('[')[0].split(':')[1]}


def build(world, datapacks, map_name, arena, world_name, power_blocks):
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
    for pos in power_blocks:
        if all(lo[i] <= pos[i] <= hi[i] for i in range(3)):
            signs.append({'x': pos[0], 'y': pos[1], 'z': pos[2], 'lines': ['[Zombies]', 'Power', '', ''], 'power': True})
            report.append('power switch near %s' % (pos,))

    merged = merged_signs_all(fn_root)
    trap_json = []
    for t in parse_traps(fn_root, merged):
        trap_id = 'trap%s' % t['n']
        main = t['regions'][0]
        trap_json.append({'x': float(t['sign'][0]), 'y': float(t['sign'][1]), 'z': float(t['sign'][2]), 'id': trap_id,
                          'cost': t['cost'], 'type': t['type'], 'requires_power': True,
                          'duration_seconds': t['duration'], 'cooldown_seconds': t['cooldown'],
                          'region': {'p1': main[0], 'p2': main[1]},
                          'extra_regions': [{'p1': r[0], 'p2': r[1]} for r in t['regions'][1:]]})
        signs = [x for x in signs if (x['x'], x['y'], x['z']) != t['sign']]
        signs.append({'x': t['sign'][0], 'y': t['sign'][1], 'z': t['sign'][2], 'lines': ['[Zombies]', 'Trap', trap_id, str(t['cost'])]})
        report.append('trap %s: %s (%s), %d area(s), %d points' % (t['n'], t['name'], t['type'], len(t['regions']), t['cost']))

    transport_json = []
    speeds = {'zipline': 8.0, 'platform': 4.0, 'cart': 6.0, 'teleport': 4.0}
    for t in parse_transports(fn_root):
        if not all(lo[0] - 40 <= p[0] <= hi[0] + 40 and lo[2] - 40 <= p[2] <= hi[2] + 40 for p in t['points']):
            continue
        transport_json.append({'id': rid(), 'name': t['name'], 'style': t['style'], 'cost': t['cost'], 'requires_power': False,
                               'cooldown_seconds': 0, 'speed': speeds[t['style']], 'two_way': t.get('two_way', False), 'platform_size': 3,
                               'waypoints': [{'x': p[0], 'y': p[1], 'z': p[2]} for p in t['points']], 'call_points': []})
        report.append('transport %s: %s, %d points%s' % (t['name'], t['style'], len(t['points']), ', two-way' if t.get('two_way') else ''))

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
    }
    report.insert(0, '%s -> %s: %d spawns (%d at start), %d doors, %d barriers, %d box locations, %d signs to write'
                  % (map_name, arena, len(spawn_json), len(start_spawns), len(door_json), len(barriers), len(box_json), len(signs)))
    edits = [{'x': p[0], 'y': p[1], 'z': p[2], 'blockdata': b} for p, b in sorted(box_edits.items())
             if 'barrier' not in b and 'sign' not in b or b == 'minecraft:air']
    edits = [e for e in edits if 'barrier' not in e['blockdata']]
    return {'save_data': save, 'signs': signs, 'block_edits': edits, 'report': report}


def power_command_blocks(world_path, datapacks):
    """Power switches: the block next to each command block that runs maps/power/power."""
    w = World(world_path)
    out = []
    for path in glob.glob(os.path.join(world_path, 'region', '*.mca')):
        rx, rz = map(int, re.search(r'r\.(-?\d+)\.(-?\d+)\.mca', path).groups())
        for i in range(1024):
            cx, cz = rx * 32 + i % 32, rz * 32 + i // 32
            chunk = w._chunk(cx, cz)
            if chunk is None:
                continue
            for be in chunk.get('block_entities', []):
                if 'command_block' in str(be.get('id', '')) and re.search(r'maps/power/power\b(?!_)', str(be.get('Command', ''))):
                    x, y, z = int(be['x']), int(be['y']), int(be['z'])
                    for dx, dy, dz in ((0, 1, 0), (0, 2, 0), (1, 1, 0), (-1, 1, 0), (0, 1, 1), (0, 1, -1)):
                        state = w.block(x + dx, y + dy, z + dz)
                        if any(k in state for k in ('lever', 'button')):
                            out.append((x + dx, y + dy, z + dz))
                            break
                    else:
                        out.append((x, y + 1, z))
            w.chunks.pop((cx, cz), None)
    return out


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
    power = power_command_blocks(a.world, datapacks)
    os.makedirs(a.out, exist_ok=True)
    for map_name, arena in zip(a.map, a.arena):
        result = build(world, datapacks, map_name, arena, a.world_name, power)
        with open(os.path.join(a.out, arena + '.json'), 'w') as f:
            json.dump(result, f, indent=1)
        print('\n'.join(result['report']))


if __name__ == '__main__':
    main()
