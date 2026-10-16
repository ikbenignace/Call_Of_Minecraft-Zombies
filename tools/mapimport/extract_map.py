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
SETBLOCK_ANY = re.compile(r"setblock (-?\d+) (-?\d+) (-?\d+) (?:minecraft:)?([a-z_]+(?:\[[^\]]*\])?)")
PRICE = re.compile(r"Points=(\d+)\.\.")
BOX_MARKER = re.compile(r"summon minecraft:marker " + COORD + " " + COORD + " " + COORD + r" \{Tags:\[\"Box\"\](?:,Rotation:\[(-?\d+(?:\.\d+)?)F)?")
WALL_SIGN_FACING = re.compile(r"_wall_sign\[facing=([a-z]+)\]")
DATA_MERGE = re.compile(r"data merge block (-?\d+) (-?\d+) (-?\d+) (\{.*\})\s*$")
SIGN_TEXT = re.compile(r"""Text(\d):(?:'((?:[^'\\]|\\.)*)'|"((?:[^"\\]|\\.)*)")""")
SIGN_SET = re.compile(r"setblock (-?\d+) (-?\d+) (-?\d+) (?:minecraft:)?([a-z_]+_sign(?:\[[^\]]*\])?)\s*(\{.*\})")
TP_START = re.compile(r"^tp @[ap](?:\[tag=PlayerOne\])? " + COORD + " " + COORD + " " + COORD + "(?: " + COORD + ")?")
AIR = 'minecraft:air'
MAP_MARGIN = 24
FAR_FROM_SIGN = 16
LATE_SPAWNER_REACH = 10
SKIP_SPAWNER_TAGS = ("Monkey", "Boss", "Napalm", "Shrieker", "Thief", "George", "Item_Spawner")
PLACEHOLDER_TEXT = ('must turn on', 'must be opened', 'door first', 'destination', 'cooling down', 'lander is',
                    'lander has', 'link in progress', 'link not active', 'authori', 'recharging', 'currently in use')


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


def world_sign_lines(be):
    raw = [str(m) for m in be['front_text']['messages']] if 'front_text' in be else [str(be.get('Text%d' % i, '')) for i in range(1, 5)]
    return [json_text(r) for r in raw]


def is_sign(state):
    return state.split('[')[0].endswith('_sign')


def unescape(snbt):
    return re.sub(r'\\(.)', r'\1', snbt)


def sign_lines(nbt):
    """The four lines of sign NBT, with components written 'like this' or "like \\"this\\"" alike."""
    lines = ['', '', '', '']
    for idx, single, double in SIGN_TEXT.findall(nbt):
        lines[int(idx) - 1] = json_text(unescape(double) if double else single)
    return lines


def click_text(nbt):
    """Sign NBT with escaped quotes undone, so click events read the same in both quoting styles."""
    return nbt.replace('\\"', '"')


ROTATION_FACING = ('SOUTH', 'WEST', 'NORTH', 'EAST')


def sign_facing(state):
    m = re.search(r'facing=([a-z]+)', state)
    if m:
        return m.group(1).upper()
    m = re.search(r'rotation=(\d+)', state)
    return ROTATION_FACING[int(round(int(m.group(1)) / 4.0)) % 4] if m else 'NORTH'


def sign_sets(text):
    """Every sign a function places, wall or standing: (pos, 'minecraft:<state>', nbt)."""
    return [(tuple(map(int, m.group(1, 2, 3))), 'minecraft:' + m.group(4), m.group(5)) for m in SIGN_SET.finditer(text)]


def is_placeholder(lines):
    text = ' '.join(lines).lower()
    return any(p in text for p in PLACEHOLDER_TEXT)


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


def box_points(x0, y0, z0, x1, y1, z1):
    return [(x, y, z) for x in range(min(x0, x1), max(x0, x1) + 1)
            for y in range(min(y0, y1), max(y0, y1) + 1) for z in range(min(z0, z1), max(z0, z1) + 1)]


def move_sources(line):
    """The source box of a "clone ... move": a door that slides away leaves its doorway empty."""
    m = CLONE.search(line)
    if not m or ' move' not in line[m.end():] or ' filtered' in line[m.end():]:
        return set()
    return set(box_points(*map(int, m.groups()[:6])))


def condition(line):
    """What an execute line waits for: (power on, doors that must be open, anything else)."""
    s = line.strip()
    if not s.startswith('execute'):
        return False, frozenset(), False
    head = s.rsplit(' run ', 1)[0]
    power = 'Power_On' in head or re.search(r'\bPower=\d', head) is not None
    doors = frozenset(int(n) for n in re.findall(r'tag=D(\d+)_O\b', head) + re.findall(r'\bDoor(\d+)Open=1\b', head))
    rest = re.sub(r'tag=!?(?:D\d+_O|Power_On|PlayerOne)\b|\bDoor\d+Open=\d\b|\bPower=\d\b|\bGameOver=0\b', '', head)
    rest = re.sub(r'scores=\{,*\}', '', rest)
    return power, doors, '=' in rest


def parse_door(paths):
    """Price, the blocks a door opens itself, its spawners, and the blocks it only clears or places when
    something else holds too (power, other doors): [(power, doors, cleared, placed)]."""
    price, cleared, spawns, conditional = None, set(), [], []
    for path in sorted(paths):
        text = read(path)
        if price is None:
            m = PRICE.search(text)
            if m:
                price = int(m.group(1))
        for line in text.splitlines():
            if line.lstrip().startswith('#'):
                continue
            writes = block_writes(line)
            gone = {p for p, s in writes.items() if s == AIR} | move_sources(line)
            fill = FILL_ANY.search(line)
            if fill and fill.group(7) == 'air':
                # "fill ... air replace <block>" only clears that block; which one that is, is the door's
                # start state's business.
                gone |= set(box_points(*map(int, fill.groups()[:6])))
            if not gone and not writes:
                continue
            power, doors, other = condition(line)
            if not (power or doors or other):
                cleared |= gone
            elif not other:
                conditional.append((power, doors, gone, {p: s for p, s in writes.items() if s != AIR}))
        spawns += spawners_in(text)
    return price, cleared, spawns, conditional


def parse_window(path, world):
    """Every board a window's repair stages put back: setblock, fill rows or clones of a spare board."""
    boards = {}
    for line in read(path).splitlines():
        for pos, state in block_writes(line, world).items():
            if state != 'minecraft:air':
                boards[pos] = state
    return boards


YAW_FACING = {0: 'SOUTH', 90: 'WEST', 180: 'NORTH', -180: 'NORTH', -90: 'EAST', 270: 'EAST'}


def facing_of_yaw(yaw):
    return YAW_FACING.get(int(round(float(yaw) / 90.0)) * 90, 'NORTH')


FILL_MODE = re.compile(r"\s+(hollow|outline|keep|replace)(?:\s+(#?[a-z_:/]+))?")
FILL_ANY = re.compile(r"fill (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (?:minecraft:)?([a-z_]+(?:\[[^\]]*\])?)")


def block_writes(text, world=None):
    """Every block a function writes, in order: {(x, y, z): 'minecraft:state'}. With a world, clones are
    followed too (maps keep spare copies of doors and props out of sight and clone them back in)."""
    out = {}
    for line in text.splitlines():
        if line.lstrip().startswith('#'):
            continue
        m = CLONE.search(line)
        if m:
            masked = ' masked' in line[m.end():]
            if world is not None:
                x0, y0, z0, x1, y1, z1, dx, dy, dz = map(int, m.groups())
                bx, by, bz = min(x0, x1), min(y0, y1), min(z0, z1)
                copied = {}
                for x, y, z in box_points(x0, y0, z0, x1, y1, z1):
                    state = out.get((x, y, z)) or world.block(x, y, z)
                    if not (masked and state == AIR):
                        copied[(dx + x - bx, dy + y - by, dz + z - bz)] = state
                for p in move_sources(line):
                    out[p] = AIR
                out.update(copied)
            continue
        m = FILL_ANY.search(line)
        if m:
            # fill ... replace <filter> / keep only touch matching blocks, which needs the world to know.
            mode = FILL_MODE.match(line[m.end():])
            only = None
            if mode and mode.group(1) in ('hollow', 'outline'):
                continue
            if mode and mode.group(1) == 'keep':
                only = 'minecraft:air'
            elif mode and mode.group(2):
                only = mode.group(2) if ':' in mode.group(2) else 'minecraft:' + mode.group(2)
            if only is not None and (world is None or only.startswith('#') and '/' in only):
                continue
            # Block tags are matched loosely by name: #minecraft:terracotta covers every *terracotta.
            tag = only[len('#minecraft:'):] if only and only.startswith('#') else None
            x0, y0, z0, x1, y1, z1 = map(int, m.groups()[:6])
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for y in range(min(y0, y1), max(y0, y1) + 1):
                    for z in range(min(z0, z1), max(z0, z1) + 1):
                        current = None if only is None else out.get((x, y, z)) or world.block(x, y, z)
                        if only is None or (tag in current if tag else current.split('[')[0] == only):
                            out[(x, y, z)] = 'minecraft:' + m.group(7)
            continue
        m = SETBLOCK_ANY.search(line)
        if m:
            out[(int(m.group(1)), int(m.group(2)), int(m.group(3)))] = 'minecraft:' + m.group(4)
    return out


CLONE_TAIL = re.compile(r"\s+(?:(replace|masked)|filtered\s+(#?[a-z0-9_:/]+))(?:\s+(move|force|normal))?")
PLAYSOUND_AT = re.compile(r"playsound (?:minecraft:)?([a-z0-9_.]+) \w+ \S+(?:\s+(-?[\d.]+) (-?[\d.]+) (-?[\d.]+))?(?:\s+([\d.]+))?")


def box_json(x0, y0, z0, x1, y1, z1):
    return {'x1': x0, 'y1': y0, 'z1': z0, 'x2': x1, 'y2': y1, 'z2': z1}


def block_targets(line):
    """The trigger targets one function line stands for: clone, fill, setblock and playsound, or []."""
    if line.lstrip().startswith('#'):
        return []
    m = CLONE.search(line)
    if m:
        x0, y0, z0, x1, y1, z1, dx, dy, dz = map(int, m.groups())
        tail = CLONE_TAIL.match(line[m.end():])
        mode, filt, how = (tail.group(1) or 'filtered', tail.group(2) or '', tail.group(3) or '') if tail else ('replace', '', '')
        value = ' '.join(w for w in ('masked' if mode == 'masked' else '', 'move' if how == 'move' else '') if w) or 'replace'
        target = {'kind': 'clone_blocks', 'value': value,
                  'box': box_json(x0, y0, z0, x1, y1, z1), 'x': dx, 'y': dy, 'z': dz}
        if mode == 'filtered' and filt:
            target['filter'] = filt if ':' in filt else ('#minecraft:' + filt[1:] if filt.startswith('#') else 'minecraft:' + filt)
        return [target]
    m = FILL_ANY.search(line)
    if m:
        mode = FILL_MODE.match(line[m.end():])
        if mode and mode.group(1) in ('hollow', 'outline'):
            return []
        target = {'kind': 'fill_blocks', 'value': 'minecraft:' + m.group(7), 'box': box_json(*map(int, m.groups()[:6]))}
        if mode and mode.group(1) == 'keep':
            target['filter'] = 'minecraft:air'
        elif mode and mode.group(2):
            filt = mode.group(2)
            target['filter'] = filt if ':' in filt else ('#minecraft:' + filt[1:] if filt.startswith('#') else 'minecraft:' + filt)
        return [target]
    if re.search(r'summon minecraft:marker [^{]*\{Tags:\["PaP"', line):
        return [{'kind': 'enable_pack_a_punch'}]
    if re.search(r'kill @e\[tag=PaP\]', line):
        return [{'kind': 'disable_pack_a_punch'}]
    m = SETBLOCK_ANY.search(line)
    if m:
        return [{'kind': 'place_blocks', 'blocks': [{'x': int(m.group(1)), 'y': int(m.group(2)), 'z': int(m.group(3)),
                                                      'blockdata': 'minecraft:' + m.group(4)}]}]
    m = PLAYSOUND_AT.search(line)
    if m and m.group(2) and '~' not in line[m.start():]:
        return [{'kind': 'play_sound', 'value': 'minecraft:' + m.group(1) if ':' not in m.group(1) else m.group(1),
                 'x': int(math.floor(float(m.group(2)))), 'y': int(math.floor(float(m.group(3)))), 'z': int(math.floor(float(m.group(4)))),
                 'volume': min(4.0, float(m.group(5))) if m.group(5) else 2.0}]
    return []


def timeline(text, timer, start=None, countdown=False, until=None):
    """A function driven by a play-time timer ("scores={<timer>=N}" on each line) as trigger targets: one "after"
    target per tick holding what the map does then. A countdown timer (excavators) starts at {@code start} and
    goes down, so N becomes start - N ticks. Lines checking a range ("N..") count from N. Ticks past {@code until}
    are dropped."""
    pattern = re.compile(r"scores=\{[^}]*\b" + re.escape(timer) + r"=(-?\d+)(\.\.)?(-?\d+)?[,}]")
    by_tick = {}
    for line in text.splitlines():
        m = pattern.search(line)
        if not m or line.lstrip().startswith('#'):
            continue
        value = int(m.group(1))
        if countdown and m.group(2):
            # "lo..hi" first holds when a countdown reaches hi; an open "lo.." holds from the start.
            value = int(m.group(3)) if m.group(3) else start
        tick = (start - value) if countdown else value
        if tick < 0 or (until is not None and tick > until):
            continue
        targets = block_targets(line[m.end():])
        if targets:
            by_tick.setdefault(max(1, tick), []).extend(targets)
    return [{'kind': 'after', 'amount': tick, 'targets': targets} for tick, targets in sorted(by_tick.items())]


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


def sign_writes(fn_root):
    """Every sign text the datapack writes, in file order: data merges and sign setblocks alike.
    [(pos, lines, nbt, function path, line)]"""
    out = []
    for path in sorted(glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True)):
        for line in read(path).splitlines():
            if line.lstrip().startswith('#'):
                continue
            m = DATA_MERGE.search(line.strip())
            if m:
                out.append((tuple(map(int, m.group(1, 2, 3))), sign_lines(m.group(4)), m.group(4), path, line))
            for pos, _, nbt in sign_sets(line):
                out.append((pos, sign_lines(nbt), nbt, path, line))
    return out


def merged_signs(writes):
    """Sign texts the datapack writes with "data merge block" (perk signs that light up at power-on)."""
    out = {}
    for pos, lines, _, _, line in writes:
        if not DATA_MERGE.search(line.strip()):
            continue
        # A sign is often merged twice (powered-off placeholder, then the real buy text); keep the buy text.
        if any(l.strip() for l in lines) and (pos not in out or has_cost(lines)):
            out[pos] = lines
    return {pos: lines for pos, lines in out.items() if has_cost(lines) and 'random weapon' not in ' '.join(lines).lower()}


def merged_signs_all(writes):
    out = {}
    for pos, lines, _, _, _ in writes:
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
        # Sparks hang at head height while the kill selectors sit at the floor (Kino trap 1: sparks at y 40,
        # zombies killed around y 37); stretch the area down to where the map really kills.
        for lo, hi in kill_boxes(visuals):
            for rlo, rhi in regions:
                if rlo['x'] - 1 <= hi['x'] and lo['x'] <= rhi['x'] + 1 and rlo['z'] - 1 <= hi['z'] and lo['z'] <= rhi['z'] + 1:
                    rlo['y'] = float(min(rlo['y'], lo['y'] + 1))
                    rhi['y'] = float(max(rhi['y'], hi['y']))
        lowered = (name + ' ' + particle_names).lower()
        kind = next((t for key, t in TRAP_TYPES if key in lowered), 'generic')
        timers = sorted({int(t) for t in TIMER.findall(clock)})
        duration = timers[0] // 20 if timers else -1
        cooldown = (timers[-1] - timers[0]) // 20 if len(timers) > 1 else -1
        traps.append({'n': n, 'name': name, 'type': kind, 'cost': int(price.group(1)) if price else 1000,
                      'signs': signs, 'regions': regions, 'duration': duration, 'cooldown': cooldown})
    return traps


SENTRY_RANGE = re.compile(r"@e\[tag=Zombie,x=(-?[\d.]+),y=(-?[\d.]+),z=(-?[\d.]+),distance=\.\.([\d.]+)\] add")


def parse_sentries(fn_root):
    """maps/sentry/sentryNbuy/clock: an automatic turret bought at its sign that shoots every zombie within range
    of its spot for a while (Kino, Ascension). Becomes a turret trap over a box around that spot."""
    out = []
    folder = os.path.join(fn_root, 'maps', 'sentry')
    for buy in sorted(glob.glob(os.path.join(folder, 'sentry*buy.mcfunction'))):
        n = re.search(r'sentry(\d+)buy', buy).group(1)
        text = read(buy)
        clock_path = os.path.join(folder, 'sentry%sclock.mcfunction' % n)
        clock = read(clock_path) if os.path.exists(clock_path) else ''
        signs = sorted({tuple(map(int, m.group(1, 2, 3))) for m in merges(text) + merges(clock)})
        reach = SENTRY_RANGE.search(clock)
        if not signs or not reach:
            continue
        x, y, z, r = (float(v) for v in reach.groups())
        price = re.search(r'Points=(\d+)\.\.', text)
        timers = sorted({int(t) for t in TIMER.findall(clock)})
        out.append({'n': n, 'signs': signs, 'cost': int(price.group(1)) if price else 1500,
                    'duration': timers[0] // 20 if timers else 30,
                    'region': ({'x': math.floor(x - r), 'y': math.floor(y) - 3, 'z': math.floor(z - r)},
                               {'x': math.floor(x + r), 'y': math.floor(y) + 4, 'z': math.floor(z + r)})})
    return out


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


def power_switches(blocks, index):
    """Power switches: the lever or button by each command block that runs maps/power/power, or one of the
    power_<side> functions of a map with several switches. That is where the power sign goes."""
    out = []
    for pos, command in index['commands']:
        if not re.search(r'maps/power/power(?:_[a-z]+)?\b', command):
            continue
        switch = switch_near(blocks, pos) or (pos[0], pos[1] + 1, pos[2])
        if switch not in out:
            out.append(switch)
    return out


class StartState:
    """Block lookups in the map's start state: what its reset functions put back, else the saved world."""

    def __init__(self, world, writes):
        self.world, self.writes = world, writes

    def block(self, x, y, z):
        state = self.writes.get((x, y, z))
        return state if state and state != AIR else self.world.block(x, y, z)


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
MARKER_TAGS = re.compile(r"summon minecraft:marker " + COORD + " " + COORD + " " + COORD + r" \{Tags:\[([^\]]*)\]")
MARKER_SIGN = re.compile(r"at @e\[tag=([\w-]+)\] run data merge block ~ ~-1 ~ (\{.*\})")


def perk_spot(pos, state, lines=None):
    return {'pos': pos, 'facing': sign_facing(state), 'state': state, 'lines': lines or ['', '', '', '']}


def parse_perk_pool(fn_root):
    """unique/perk_placement/<perk>/<perk><place>.mcfunction builds perk <perk> at <place>. Every place becomes
    one pool spot and every perk gets its sign at a different place (the pool hides them at game start)."""
    folder = os.path.join(fn_root, 'unique', 'perk_placement')
    by_perk = {}
    for path in sorted(glob.glob(os.path.join(folder, '*', '*.mcfunction'))):
        perk = os.path.basename(os.path.dirname(path))
        place = os.path.splitext(os.path.basename(path))[0]
        place = place[len(perk):] if place.startswith(perk) else place
        found = sign_sets(read(path))
        if not found:
            continue
        pos, state, nbt = found[0]
        by_perk.setdefault(perk, {})[place] = perk_spot(pos, state, sign_lines(nbt))
    if not by_perk:
        return parse_perk_sets(fn_root) or parse_perk_drops(fn_root)
    perks = sorted(by_perk)
    places = sorted({p for spots in by_perk.values() for p in spots})
    spots, signs = [], []
    for i, place in enumerate(places):
        owner = perks[i % len(perks)]
        spot = by_perk[owner].get(place) or next(v[place] for v in by_perk.values() if place in v)
        spots.append(spot)
        if i < len(perks) and place in by_perk[owner]:
            signs.append(spot)
    return {'spots': spots, 'signs': signs, 'perks': perks, 'places': places, 'mode': 'random_on_open'}


def parse_perk_sets(fn_root):
    """unique/perks/set/**: random perk layouts, each placing a sign with a perk marker on top; the power
    function writes each perk's buy text under its marker. Every spot any layout uses becomes a pool spot and
    the pool deals the perks out at game start."""
    spots, perks = {}, {}
    for path in sorted(glob.glob(os.path.join(fn_root, 'unique', 'perks', 'set', '**', '*.mcfunction'), recursive=True)):
        text = read(path)
        for pos, state, _ in sign_sets(text):
            # Layouts with another machine model put the sign one block over; that is the same spot.
            if not any(near(pos, other, 1) for other in spots):
                spots[pos] = state
        for m in MARKER_TAGS.finditer(text):
            tags = [t.strip().strip('"') for t in m.group(4).split(',')]
            if 'Perk' in tags:
                perks.update((t, None) for t in tags if t != 'Perk')
    if not spots or not perks:
        return None
    for path in glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True):
        for m in MARKER_SIGN.finditer(read(path)):
            if m.group(1) in perks:
                perks[m.group(1)] = sign_lines(m.group(2))
    names = sorted(p for p, lines in perks.items() if lines)
    ordered = [perk_spot(pos, state) for pos, state in sorted(spots.items())]
    signs = [dict(spot, lines=perks[name]) for spot, name in zip(ordered, names)]
    return {'spots': ordered, 'signs': signs, 'perks': names, 'places': ['%d %d %d' % s['pos'] for s in ordered],
            'mode': 'random_on_start'}


PERK_IDS = (('jugger', 'juggernog'), ('speed', 'speed_cola'), ('double tap', 'double_tap'), ('revive', 'quick_revive'),
            ('mule', 'mule_kick'), ('phd', 'phd_flopper'), ('stamin', 'stamin_up'), ('deadshot', 'deadshot_daiq'))


def perk_id(lines):
    text = ' '.join(lines).lower()
    return next((pid for key, pid in PERK_IDS if key in text), None)


def parse_perk_drops(fn_root):
    """A function that puts different perk signs on one spot depending on a random score (Moon's No Man's Land
    drops Jugger-Nog or Speed Cola at game start): that spot is a pool of those perks, dealt at game start."""
    for path in sorted(glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True)):
        by_pos = {}
        for pos, state, nbt in sign_sets(read(path)):
            lines = sign_lines(nbt)
            pid = perk_id(lines)
            if pid and has_cost(lines):
                by_pos.setdefault(pos, {}).setdefault(pid, (state, lines))
        for pos, perks in sorted(by_pos.items()):
            if len(perks) < 2:
                continue
            first, (state, lines) = next(iter(perks.items()))
            spot = perk_spot(pos, state, lines)
            return {'spots': [spot], 'signs': [spot], 'perks': sorted(perks), 'extra': [p for p in perks if p != first],
                    'places': ['%d %d %d' % pos], 'mode': 'random_on_start'}
    return None


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
            # BO has George walking from round 1; here he comes in at round 5 and returns every 4 rounds once killed.
            rule = {'type': special, 'mode': 'every_x_rounds', 'value': 4, 'max_alive': 1, 'first_round': 5, 'own_round': False}
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


def transport(name, style, points, cost, two_way, requires_power=False, path=None):
    return {'name': name, 'style': style, 'points': [tuple(round(v, 2) for v in p) for p in points], 'cost': cost,
            'two_way': two_way, 'requires_power': requires_power, 'path': path}


def distinct(points):
    """The points without immediate repeats (a ride's start is often also its first teleport)."""
    return [p for i, p in enumerate(points) if i == 0 or not close(p, points[i - 1], 0)]


def cooldown_seconds(text):
    """The longest "<..>Cooldown=N" / "<..>_CD=N" score a transport waits on, in seconds."""
    ticks = [int(n) for n in re.findall(r"\b\w*(?:Cooldown|_CD)=(\d+)", text)]
    return max(ticks) // 20 if ticks else 0


def parse_landers(fn_root):
    """unique/landers/launch/lander_N_launch: where the riders stand on pad N and the height the lander cruises
    at. Pad 0 is the main pad; a lander rises from it, crosses at cruise height to every other pad and comes
    back down. The call stations ("Call lander to position") are matched to the nearest pad."""
    folder = os.path.join(fn_root, 'unique', 'landers')
    pads, cruise = {}, 0.0
    for path in glob.glob(os.path.join(fn_root, 'unique', '*lander*', '**', '*.mcfunction'), recursive=True):
        n = re.search(r'(\d+)', os.path.basename(path))
        riders = [tuple(float(v) for v in m.group(2, 3, 4)) for m in PLAYER_TP.finditer(read(path)) if 'tag=Player' in m.group(1)]
        if riders:
            cruise = max([cruise] + [r[1] for r in riders])
        if n and riders and 'launch' in os.path.basename(path):
            low = min(r[1] for r in riders)
            pads[int(n.group(1))] = mean([r for r in riders if r[1] == low][:4])
    if len(pads) < 2:
        return []
    files = glob.glob(os.path.join(fn_root, 'unique', '*lander*', '**', '*.mcfunction'), recursive=True)
    buy = '\n'.join(read(f) for f in files if 'buy' in os.path.basename(f))
    price = PRICE.search(buy)
    power = read(os.path.join(fn_root, 'maps', 'power', 'power.mcfunction')) if os.path.exists(os.path.join(fn_root, 'maps', 'power', 'power.mcfunction')) else ''
    stations = []
    for f in files:
        for m in merges(read(f)):
            if 'call lander' in ' '.join(sign_lines(m.group(4))).lower():
                pos = tuple(map(int, m.group(1, 2, 3)))
                if pos not in stations:
                    stations.append(pos)
    station = {n: min(stations, key=lambda s: sum((s[i] - p[i]) ** 2 for i in range(3))) for n, p in pads.items()} if stations else {}
    cooldown = cooldown_seconds('\n'.join(read(f) for f in files))
    home = min(pads)
    start = pads[home]
    landers = []
    for n in sorted(pads):
        if n == home:
            continue
        end = pads[n]
        lander = transport('Lander %d' % n, 'platform', [start, (start[0], cruise, start[2]), (end[0], cruise, end[2]), end],
                           int(price.group(1)) if price else 0, True, 'lander' in power.lower(), path=folder)
        lander.update(speed=12.0, cooldown=cooldown, cooldown_group='lander',
                      call_points=[station[k] for k in (home, n) if k in station])
        landers.append(lander)
    return landers


def cabin_clones(text):
    """clone <cabin> <x y z> lines of an elevator move, by the timer value they run at."""
    out = []
    for line in text.splitlines():
        m = CLONE.search(line)
        step = re.search(r"Timer=(\d+)\}", line)
        if m and step:
            out.append((int(step.group(1)), tuple(map(int, m.groups()))))
    return sorted(out)


def parse_elevators(fn_root, start_block):
    """unique/elevator/<car>/*_to_*: each move tags the riders standing on the car at one floor; every floor of
    a car is linked to the next one. The car itself is a cabin cloned up or down the shaft; wherever a move
    leaves it the cabin floor is put down when the start state has none, so riders never land in an empty
    shaft."""
    out = []
    for car in sorted(glob.glob(os.path.join(fn_root, 'unique', 'elevator*', '*'))):
        if not os.path.isdir(car):
            continue
        floors, stops, moves = [], [], sorted(glob.glob(os.path.join(car, '*_to_*.mcfunction')))
        for path in moves:
            text = read(path)
            for m in TAG_SPHERE.finditer(text):
                p = (float(m.group(1)), float(m.group(2)), float(m.group(3)))
                if not any(close(p, f) for f in floors):
                    floors.append(p)
            clones = cabin_clones(text)
            if clones:
                stops.append(clones[-1][1])
        floors.sort(key=lambda p: p[1])
        floor_edits = {}
        for x0, y0, z0, x1, y1, z1, dx, dy, dz in stops:
            bx, by, bz = min(x0, x1), min(y0, y1), min(z0, z1)
            for x in range(bx, max(x0, x1) + 1):
                for z in range(bz, max(z0, z1) + 1):
                    target = (dx + x - bx, dy, dz + z - bz)
                    state = start_block(x, by, z)
                    if state != AIR and start_block(*target) == AIR:
                        floor_edits[target] = state
        buy = '\n'.join(read(f) for f in glob.glob(os.path.join(car, '*buy*.mcfunction')))
        price = PRICE.search(buy)
        for a, b in zip(floors, floors[1:]):
            lift = transport('Elevator %s' % os.path.basename(car), 'teleport', [a, b], int(price.group(1)) if price else 0, True,
                             path=moves[0] if moves else None)
            lift['floor_edits'] = floor_edits
            out.append(lift)
    return out


def parse_jump_pads(fn_root):
    """unique/**/jump_pads: a pad tags the players inside its box, then moves them with absolute tps ordered by
    their own counter. Each pad becomes a one-way ride along those points; pads that share a launch spot (the
    map rotates which one is live) are one ride."""
    out = []
    for path in sorted(glob.glob(os.path.join(fn_root, 'unique', '**', '*jump_pad*', '*.mcfunction'), recursive=True)):
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
            if steps and not any(close(start, o['points'][0], 1) for o in out):
                out.append(transport('Jump pad %s' % tag.split('_')[-1], 'platform', [start] + [steps[k] for k in sorted(steps)], 0, False, path=path))
    return out


def parse_geysers(fn_root):
    """unique/**/geyser_*: standing on the <Tag> marker long enough launches the players straight up to the
    <Tag>_Clear marker, from where they are put down at the landing spot (the first player tp)."""
    out = []
    for path in sorted(glob.glob(os.path.join(fn_root, 'unique', '**', '*geyser*.mcfunction'), recursive=True)):
        text = read(path)
        tag = re.search(r"as @e\[tag=(\w+)\] at @s run execute as @p\[distance=\.\.", text)
        land = re.search(r"tp @p\[[^\]]*\] (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)", text)
        if not tag or not land:
            continue
        base, top = marker_positions(fn_root, tag.group(1)), marker_positions(fn_root, tag.group(1) + '_Clear')
        if not base or not top:
            continue
        a, b = base[0], tuple(float(v) for v in land.groups())
        label = os.path.splitext(os.path.basename(path))[0]
        geyser = transport(friendly_name(label), 'platform', [a, (a[0], top[0][1], a[2]), (b[0], top[0][1], b[2]), b], 0, False, path=path)
        geyser['label'] = label
        out.append(geyser)
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
        pockets, air_tag, lost_by = [], None, {}
        for m in TAG_BOX.finditer(text):
            if m.group(7) == 'add':
                air_tag = air_tag or m.group(8)
                if m.group(8) == air_tag:
                    box = tag_box(m)
                    if box not in pockets:
                        pockets.append(box)
                        line = text[text.rfind('\n', 0, m.start()) + 1:m.start()]
                        breach = re.search(r'tag=!(\w+)', line)
                        if breach:
                            lost_by[len(pockets)] = breach.group(1)
        scoped = any('effect give' in line and re.search(r"scores=\{On_\w+=1", line) for line in text.splitlines())
        if not pockets:
            return {'settings': {'gravity': 0.4}, 'zones': []}
        pts = [p for b in pockets for p in b] + list(anchors)
        zlo = [max(lo[i], min(p[i] for p in pts) - 24) for i in range(3)]
        zhi = [min(hi[i], max(p[i] for p in pts) + 24) for i in range(3)]
        zlo[1], zhi[1] = lo[1], hi[1]
        zones = [{'id': 'zone_low_gravity', 'name': '', 'p1': loc(zlo), 'p2': loc(zhi), 'gravity': 0.4,
                  'no_air': True, 'damage_per_second': 0.0}]
        breaches = {}
        for i, (a, b) in enumerate(pockets, 1):
            zones.append({'id': 'zone_air_%d' % i, 'name': '', 'p1': loc(a), 'p2': loc(b), 'gravity': 1.0,
                          'no_air': False, 'air_pocket': True, 'damage_per_second': 0.0})
            if i in lost_by:
                breaches.setdefault(lost_by[i], []).append('zone_air_%d' % i)
        return {'settings': {} if scoped else {'gravity': 0.4}, 'zones': zones, 'source': os.path.basename(path),
                'breaches': breaches}
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
            return {'plates': [tuple(int(math.floor(v)) for v in p) for p in plates], 'power': power, 'path': name}
    return None


def first_call(nbt):
    m = re.search(r"function (?:custom:)?([a-z0-9_/]+)", click_text(nbt))
    return m.group(1) if m else None


def calls_from(fn_root, name, depth=3):
    """A function's text and that of the functions it runs itself (not the ones its signs run on click)."""
    path = function_path(fn_root, name)
    if depth < 0 or not os.path.exists(path):
        return ''
    text = read(path)
    called = [c for line in text.splitlines() if 'clickEvent' not in line for c in FUNCTION_CALL.findall(line)]
    return '\n'.join([text] + [calls_from(fn_root, c, depth - 1) for c in called])


def link_chains(fn_root):
    """Signs the power function writes whose click runs a *link* function, each followed through the sign that
    function (or one it runs) writes next, and so on: Kino's "Initiate link" -> "Link pad", Der Riese's pad ->
    mainframe. [{'signs', 'functions', 'folder', 'pap', 'timer'}]"""
    power = os.path.join(fn_root, 'maps', 'power', 'power.mcfunction')
    if not os.path.exists(power):
        return []
    chains = []
    starts = [first_call(m.group(4)) for m in merges(read(power))]
    for m in merges(read(power)):
        call = first_call(m.group(4))
        if not call or 'link' not in call.split('/')[-1]:
            continue
        chain, nxt = [], (tuple(map(int, m.group(1, 2, 3))), call)
        while nxt:
            chain.append(nxt)
            text, nxt = calls_from(fn_root, nxt[1]), None
            for m2 in merges(text):
                call2 = first_call(m2.group(4))
                if call2 and 'link' in call2.split('/')[-1] and call2 not in [c for _, c in chain] and call2 not in starts:
                    nxt = (tuple(map(int, m2.group(1, 2, 3))), call2)
                    break
        folder = os.path.dirname(function_path(fn_root, chain[0][1]))
        timer = re.search(r"objectives add (\w+) minecraft\.custom", calls_from(fn_root, chain[0][1]))
        chains.append({'signs': [p for p, _ in chain], 'functions': [c for _, c in chain], 'folder': folder,
                       'pap': any('pap' in read(f).lower() for f in glob.glob(os.path.join(folder, '*.mcfunction'))),
                       'timer': timer.group(1) if timer else None})
    return chains


def link_time_limit(fn_root, chain):
    """Seconds a link stays open: the timer value at which its last sign is reset."""
    if not chain['timer']:
        return 0
    last = '%d %d %d' % chain['signs'][-1]
    ticks = [int(m.group(1)) for path in all_functions(fn_root).values() for line in read(path).splitlines()
             if 'data merge block ' + last in line for m in [re.search(chain['timer'] + r"=(\d+)", line)] if m]
    return min(ticks) // 20 if ticks else 0


def pap_marker_signs(world, fn_root):
    """Pack-a-Punch signs the map writes on its PaP markers (data merge block ~ ~ ~), at game start or mid-game:
    the world may hold them blank, or not at all. Block state from the world's sign, else from the clone source
    that puts the sign there."""
    out = {}
    for path in sorted(glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True)):
        text = read(path)
        m = re.search(r"as @e\[tag=(\w+)\] at @s run data merge block ~ ~ ~ (\{.*Pack-a-Punch.*\})", text)
        if not m:
            continue
        for pos in marker_positions(fn_root, m.group(1)):
            pos = tuple(int(math.floor(v)) for v in pos)
            if pos in out:
                continue
            lines = sign_lines(m.group(2))
            if not has_cost(lines):
                lines[3] = '[Cost: 5000]'
            state = world.block(*pos) if is_sign(world.block(*pos)) else None
            for c in CLONE.finditer(text):
                x0, y0, z0, x1, y1, z1, dx, dy, dz = map(int, c.groups())
                sx, sy, sz = pos[0] - dx + min(x0, x1), pos[1] - dy + min(y0, y1), pos[2] - dz + min(z0, z1)
                if min(x0, x1) <= sx <= max(x0, x1) and min(y0, y1) <= sy <= max(y0, y1) and min(z0, z1) <= sz <= max(z0, z1):
                    found = world.block(sx, sy, sz)
                    if 'sign' in found:
                        state = found
            out[pos] = {'x': pos[0], 'y': pos[1], 'z': pos[2], 'lines': lines, 'blockdata': state or 'minecraft:oak_sign[rotation=0]'}
    return list(out.values())


# ------------------------------------------------------------------ travel functions
PER_PLAYER = re.compile(r"tag=Player(?:One|Two|Three|Four)\b|scores=\{Teleroom=\d+\}")
PAD_MARKER = re.compile(r"as @e\[tag=(\w+)\] at @s run scoreboard players set @a\[distance=\.\.[\d.]+")
SOURCE_SELECTOR = re.compile(r"@[ape]\[x=(-?[\d.]+),y=(-?[\d.]+),z=(-?[\d.]+),distance")
SPECIAL_TRAVEL = ('lander', 'elevator', 'jump_pad', 'geyser')


def moves_zombies(selector):
    return 'Zombie' in selector or 'zombie' in selector


def travel_steps(text):
    """Absolute player teleports grouped into steps: one per timer value, or one per run of per-player lines
    (PlayerOne..Four land a few blocks apart on the same spot). Each step is the mean of its teleports."""
    steps, keys, source, everyone = {}, [], None, False
    group, last_player = 0, False
    for line in text.splitlines():
        m = TP_ANY.search(line)
        if not m or moves_zombies(m.group(2) or ''):
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


def tour_after(text, target):
    """What a teleporter tour does after its destination (Kino: 30 s at Pack-a-Punch, a few seconds in one of four
    side rooms, then back to the projector room): (seconds at the destination, [(points, seconds)]). Spots the tour
    passes several times are the dark waiting room between stops and are skipped. Teleports a few blocks apart
    are one spot (one per player); spots further apart are alternatives a rider is sent to one of."""
    stages = {}
    for line in text.splitlines():
        m = TP_ANY.search(line)
        step = STEP.search(line)
        if m and step and not moves_zombies(m.group(2) or ''):
            stages.setdefault(int(step.group(1)), []).append(tuple(float(v) for v in m.group(3, 4, 5)))
    timers = sorted(stages)
    seen = {}
    for t in timers:
        for p in stages[t]:
            key = tuple(round(v) for v in p)
            seen.setdefault(key, set()).add(t)
    waiting = {k for k, ts in seen.items() if len(ts) >= 2}
    real = [t for t in timers if not all(tuple(round(v) for v in p) in waiting for p in stages[t])]
    at = next((t for t in real if any(close(p, target, 3) for p in stages[t])), None)
    if at is None or at == real[-1]:
        return 0, []

    def leaves(t):
        later = [u for u in timers if u > t]
        return (later[0] - t) // 20 if later else 0

    stops = []
    for t in real[real.index(at) + 1:]:
        pts = stages[t]
        spots = [mean(pts)] if max(max(abs(a[i] - b[i]) for i in range(3)) for a in pts for b in pts) <= 4 else pts
        stops.append((spots, max(1, round(leaves(t)))))
    return round(leaves(at)), stops


def travel_pairs(text):
    """Pads that each send whoever stands on them to their own spot (tp @e[x,y,z,distance] X Y Z, no timer),
    when one function holds several: [(pad, destination)]."""
    pairs = []
    for line in text.splitlines():
        m = TP_ANY.search(line)
        if not m or STEP.search(line) or moves_zombies(m.group(2) or ''):
            continue
        sel = SELECTOR_POS.search(m.group(2) or '')
        if sel:
            pad = tuple(float(v) for v in sel.groups())
            if not any(close(pad, q, 1) for q, _ in pairs):
                pairs.append((pad, tuple(float(v) for v in m.group(3, 4, 5))))
    return pairs if len(pairs) >= 2 else []


def selector_source(text):
    """The first spot a function picks riders around (@a[x=..,y=..,z=..,distance=..])."""
    for line in text.splitlines():
        m = SOURCE_SELECTOR.search(line)
        if m and not moves_zombies(line):
            return tuple(float(v) for v in m.groups())
    return None


def pad_source(fn_root, folder):
    """Teleporters whose riders are picked by standing near a pad marker (as @e[tag=To_Moon] at @s ... @a[distance=..3])."""
    for path in glob.glob(os.path.join(folder, '*.mcfunction')):
        m = PAD_MARKER.search(read(path))
        if m:
            found = marker_positions(fn_root, m.group(1))
            if found:
                return found[0]
    return None


def friendly_name(label):
    """A player-facing name for a travel function: tpaloop -> Teleporter A, zipline_boat -> Zipline (boat)."""
    label = re.sub(r'_?(loop|active)$', '', label.lower())
    m = re.fullmatch(r'tp([a-z])', label)
    if m:
        return 'Teleporter ' + m.group(1).upper()
    words = {'tp': 'teleporter', 'teleporters': 'teleporter', 'iceslide': 'ice slide', 'ziplinetodoc': 'zipline',
             'ziplinetomain': 'zipline', 'pap': 'Pack-a-Punch'}
    parts = [words.get(w, w) for w in label.split('_') if w]
    if not parts:
        return 'Transport'
    if len(parts) >= 3 and parts[1] == 'to':
        return '%s to the %s' % (parts[0].capitalize(), ' '.join(parts[2:]).capitalize())
    head = parts[0][0].upper() + parts[0][1:]
    if len(parts) == 2 and len(parts[1]) == 1:
        return '%s %s' % (head, parts[1].upper())
    return head + (' (%s)' % ' '.join(parts[1:]) if len(parts) > 1 else '')


def teleport_cooldown(steps, text):
    """Seconds before a teleporter can be used again: its whole timer, or for a tour (it brings the riders back)
    the time from the last teleport until it rewrites its sign."""
    numeric = [k for k, _ in steps if isinstance(k, int)]
    if not numeric:
        return 0
    score = re.search(r"scores=\{(\w+)=%d\}" % numeric[0], text)
    if not score:
        return 0
    values = [int(v) for v in re.findall(score.group(1) + r"=(\d+)", text)]
    if len(numeric) >= 3:
        rewrites = [int(v) for line in text.splitlines() if 'data merge block' in line for v in re.findall(score.group(1) + r"=(\d+)", line)]
        later = [v for v in rewrites if v > max(numeric)]
        return (min(later) - max(numeric)) // 20 if later else 0
    return max(values) // 20


def parse_transports(fn_root):
    """Absolute player teleports in the map's travel functions. Short hops in a row are a ride along a path;
    otherwise it is a teleporter to its last stop (the longest stop of a tour). A function with several pads
    that each have their own destination is one teleporter per pad. A flinger throws its riders from the spot
    it watches to where its absolute teleports put them."""
    out = []
    names = ('zipline', 'teleporter', 'travel', 'minecart', 'slide', 'flinger')
    files = [f for f in glob.glob(os.path.join(fn_root, 'unique', '**', '*.mcfunction'), recursive=True)
             if any(k in f.lower() for k in names) and not any(k in f.lower() for k in SPECIAL_TRAVEL)]
    for path in sorted(files):
        text = read(path)
        label = os.path.splitext(os.path.basename(path))[0]
        buy = next((read(f) for f in glob.glob(os.path.join(os.path.dirname(path), '*buy*.mcfunction'))), '')
        price = PRICE.search(buy)
        cost = int(price.group(1)) if price else 0
        pairs = travel_pairs(text)
        if pairs:
            for i, (pad, target) in enumerate(pairs, 1):
                out.append({'name': '%s %d' % (friendly_name(label), i), 'label': '%s_%d' % (label, i), 'style': 'teleport',
                            'points': [pad, target], 'cost': cost, 'path': path})
            continue
        steps, source, everyone = travel_steps(text)
        if not steps:
            continue
        if source is None:
            source = pad_source(fn_root, os.path.dirname(path)) or selector_source(text)
        points = [p for _, p in steps]
        if source is not None:
            points.insert(0, source)
        if len(points) < 2:
            continue
        hops = [max(abs(points[i + 1][j] - points[i][j]) for j in range(3)) for i in range(len(points) - 1)]
        cooldown = 0
        if 'flinger' in label.lower():
            style, points = 'platform', arc(points[0], points[-1], lift=8.0)
        elif len(points) >= 3 and max(hops) <= 16 and 'teleport' not in label.lower():
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
            cooldown = teleport_cooldown(steps, text)
        stay, then = tour_after(text, points[-1]) if style == 'teleport' else (0, [])
        out.append({'name': friendly_name(label), 'label': label, 'style': style, 'points': points, 'cost': cost,
                    'path': path, 'cooldown': cooldown, 'stay': stay, 'then': then})
    return dedupe_transports(out)


def dedupe_transports(transports):
    """Drop repeats and fold a reverse path (to-dock / to-main) into one two-way transport. Teleporters are
    one-way unless such a reverse path exists."""
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
            t.setdefault('two_way', False)
            kept.append(t)
    return kept


# ------------------------------------------------------------------ mechanisms (power walls, shared gates)
MAX_REGION_BLOCKS = 600
MAX_PLACED_BLOCKS = 2000
COSMETIC = ('light', 'barrier', 'structure_void')


def power_paths(fn_root):
    """maps/power/power*.mcfunction in the order the map runs them: power, power2, power3, ..., then the
    per-switch functions of a map with several switches."""
    def order(path):
        name = os.path.basename(path)[:-len('.mcfunction')]
        m = re.fullmatch(r'power(\d*)', name)
        return (0, int(m.group(1) or 1), name) if m else (1, 0, name)
    return sorted(glob.glob(os.path.join(fn_root, 'maps', 'power', 'power*.mcfunction')), key=order)


def power_effects(fn_root, world):
    """What turning the power on does to blocks, per doors it also waits for: {doors: {pos: final state}}.
    Lines that wait on anything else (player counts, timers) are not part of it."""
    groups = {}
    for path in power_paths(fn_root):
        for line in read(path).splitlines():
            if line.lstrip().startswith('#'):
                continue
            _, doors, other = condition(line)
            if other:
                continue
            writes = block_writes(line, world)
            if writes:
                groups.setdefault(doors, {}).update(writes)
    return groups


def minimal(options):
    """Drop the ways to open something that need more than another way does."""
    return {o for o in options if not any(q != o and q[0] <= o[0] and q[1] <= o[1] for q in options)}


def called_paths(fn_root, names, depth=4):
    """The functions named and what they run themselves (not what their signs run on click)."""
    out, queue = set(), [(n, 0) for n in names]
    while queue:
        name, d = queue.pop()
        path = function_path(fn_root, name)
        if path in out or not os.path.exists(path):
            continue
        out.add(path)
        if d < depth:
            queue += [(c, d + 1) for line in read(path).splitlines() if 'clickEvent' not in line for c in FUNCTION_CALL.findall(line)]
    return out


def power_gated(fn_root):
    """Functions the map's loop only runs while the power is on."""
    loops = os.path.join(fn_root, 'unique', 'map_specific_loops.mcfunction')
    if not os.path.exists(loops):
        return set()
    names = [c for line in read(loops).splitlines() if 'Power_On' in line.rsplit(' run ', 1)[0] for c in FUNCTION_CALL.findall(line)]
    return called_paths(fn_root, names)


def relative_name(fn_root, path):
    return os.path.relpath(path, fn_root)[:-len('.mcfunction')].replace(os.sep, '/')


def transport_scope(fn_root, path, others):
    """The functions that belong to one transport: its folder when no other transport shares it, else its own
    file, plus whatever those run."""
    if path is None:
        return set()
    if os.path.isdir(path):
        files = set(glob.glob(os.path.join(path, '**', '*.mcfunction'), recursive=True))
    else:
        folder = os.path.dirname(path)
        shared = any(o and o != path and os.path.dirname(o) == folder for o in others)
        files = {path} if shared else set(glob.glob(os.path.join(folder, '*.mcfunction')))
    return files | called_paths(fn_root, [relative_name(fn_root, f) for f in files])


def transport_signs(fn_root, scope, writes):
    """Signs whose click buys this transport (tags one of its *_B tags), else signs whose click runs one of
    its functions."""
    text = '\n'.join(read(f) for f in scope)
    tags = set(re.findall(r'tag=(\w+_B)\b', text))
    names = {relative_name(fn_root, f) for f in scope}
    tagged, called = [], []
    for pos, _, nbt, _, _ in writes:
        click = click_text(nbt)
        if tags & set(re.findall(r'add (\w+_B)\b', click)):
            tagged.append(pos)
        elif first_call(nbt) in names:
            called.append(pos)
    unique = lambda ps: [p for i, p in enumerate(ps) if p not in ps[:i]]
    return unique(tagged), unique([p for p in called if p not in tagged])


def relinks(path):
    """The transport's own function writes back a sign that starts a link: it has to be linked again after
    every ride (Kino's teleporter)."""
    if not path or not os.path.isfile(path):
        return False
    return any('link' in (first_call(m.group(4)) or '').split('/')[-1] for m in merges(read(path)))


def call_point(p):
    return {'x': p[0] + 0.5, 'y': float(p[1]), 'z': p[2] + 0.5}


def near(a, b, reach):
    return sum((a[i] - b[i]) ** 2 for i in range(3)) <= reach * reach


def sorted_call_points(points, start, end, two_way, reach=8):
    """Sign spots a rider presses at: by the start, and by the end too when it runs both ways."""
    first = [p for p in points if near(p, start, reach)]
    last = [p for p in points if two_way and near(p, end, reach) and p not in first]
    return first + last


def door_zone(n, blocks):
    xs, ys, zs = [p[0] for p in blocks], [p[1] for p in blocks], [p[2] for p in blocks]
    return {'id': 'door_%d' % n, 'name': '', 'p1': loc((min(xs), min(ys), min(zs))), 'p2': loc((max(xs), max(ys), max(zs)))}


def option_id(option):
    power, doors = option
    return '_'.join((['power'] if power else []) + ['door%d' % d for d in sorted(doors)]) + '_opens'


def trigger(tid, name, steps, targets, ordered=False, time_limit=0, reset_on_fail=False):
    return {'id': tid, 'name': name,
            'options': {'ordered': ordered, 'time_limit_seconds': time_limit, 'reset_on_fail': reset_on_fail,
                        'all_players': False, 'repeatable': False, 'simultaneous': False},
            'steps': steps, 'targets': targets}


def click_gates(fn_root, writes, reset_solid):
    """Signs the reset function writes whose click runs a function that clears blocks the reset put up (Shi No
    Numa's "Turn on Zipline" lifts the gate at the far end of the zipline): [(sign, function, cleared)]."""
    out = []
    for pos, _, nbt, path, _ in writes:
        name = first_call(nbt)
        if not name or not os.path.basename(path).startswith('reset') or '/song/' in name:
            continue
        target = function_path(fn_root, name)
        if not os.path.exists(target):
            continue
        cleared = {p for p, s in block_writes(read(target)).items() if s == AIR and p in reset_solid}
        if cleared and not any(name == n for _, n, _ in out):
            out.append((pos, name, cleared))
    return out


def launch_gate(fn_root, folder, start_block):
    """A sign among the lander functions whose click starts a timer that a function with sliding doors counts
    (Ascension's "Activate launch" opens the Pack-a-Punch room): (sign, that function, its door blocks, the timer)."""
    files = glob.glob(os.path.join(folder, '**', '*.mcfunction'), recursive=True)
    for f in files:
        for m in merges(read(f)):
            obj = re.search(r"objectives add (\w+)", click_text(m.group(4)))
            if not obj:
                continue
            for g in files:
                text = read(g)
                if obj.group(1) not in text:
                    continue
                door = set()
                for line in text.splitlines():
                    c = CLONE.search(line)
                    if c and ' move' in line and ' filtered' not in line:
                        x0, y0, z0, x1, y1, z1 = map(int, c.groups()[:6])
                        if min(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)) == 0:
                            door |= set(box_points(x0, y0, z0, x1, y1, z1))
                door = {p: start_block(*p) for p in door if start_block(*p) != AIR}
                if door:
                    return tuple(map(int, m.group(1, 2, 3))), g, door, obj.group(1)
    return None


def timed_pap(fn_root):
    """unique/pap/*: a timer function that puts the Pack-a-Punch marker up for a while and takes it away again
    (Five's panic room): (its text, the timer, its path)."""
    for path in sorted(glob.glob(os.path.join(fn_root, 'unique', 'pap', '*.mcfunction'))):
        text = read(path)
        for line in text.splitlines():
            m = re.search(r'scores=\{(\w+)=\d+\}', line)
            if m and re.search(r'summon minecraft:marker [^{]*\{Tags:\["PaP"', line):
                return text, m.group(1), path
    return None


PLAY_TIMER = re.compile(r"scoreboard objectives add (\w+) minecraft\.custom:minecraft\.play_time")
TAG_CHANGE = re.compile(r"\btag @[ape](?:\[[^\]]*\])? (add|remove) (\w+)")
KEEP_BACK = re.compile(r"as @e\[tag=(\w+)\] at @s run execute unless entity @[ap]\[tag=(\w+)\] run tp @([ape])\[([^\]]*)\] "
                       + COORD + " " + COORD + " " + COORD)


def single_score(timer):
    """A selector checking nothing but {@code timer} at one value: other conditions (player count) mean variants."""
    return re.compile(r"scores=\{" + re.escape(timer) + r"=(\d+)\}")


def pap_open_sequence(fn_root, skip):
    """What unique/pap/** does once a gate's own counter reaches its open value (Shangri-La's stones: the stairs
    come up, a warning timer starts the Pack-a-Punch timer, whose loop takes the stairs away again and places
    the stones anew): the lines checking that one score, followed through every play-time timer such a line
    starts. Returns {'now': targets, 'timeline': after-steps, 'tags': [(tick, tag, added)], 'calls': [(tick,
    function)]} or None. Block writes on {@code skip} (the stones) are left out."""
    texts = [read(p) for p in sorted(glob.glob(os.path.join(fn_root, 'unique', 'pap', '**', '*.mcfunction'), recursive=True))]
    lines = [line for text in texts for line in text.splitlines() if not line.lstrip().startswith('#')]
    timers = {m.group(1) for line in lines for m in PLAY_TIMER.finditer(line)}
    root = None
    for line in lines:
        start = PLAY_TIMER.search(line)
        m = re.search(r"scores=\{(\w+)=(\d+)\}", line)
        if start and m and m.group(1) not in timers:
            root = (m.group(1), int(m.group(2)))
            break
    if not root:
        return None
    by_tick, tags, calls, seen = {}, [], [], set()

    def follow(timer, wanted, offset):
        pattern = single_score(timer)
        for line in lines:
            m = pattern.search(line)
            if not m or wanted is not None and int(m.group(1)) != wanted:
                continue
            tick = offset + (0 if wanted is not None else int(m.group(1)))
            rest = line[m.end():]
            targets = [t for t in block_targets(rest)
                       if not (t['kind'] == 'place_blocks' and all((b['x'], b['y'], b['z']) in skip for b in t['blocks']))]
            if targets:
                by_tick.setdefault(tick, []).extend(targets)
            tags.extend((tick, c.group(2), c.group(1) == 'add') for c in TAG_CHANGE.finditer(rest))
            calls.extend((tick, c) for c in FUNCTION_CALL.findall(rest))
            for started in PLAY_TIMER.findall(rest):
                if started not in seen:
                    seen.add(started)
                    follow(started, None, tick)

    follow(root[0], root[1], 0)
    steps = [{'kind': 'after', 'amount': tick, 'targets': targets} for tick, targets in sorted(by_tick.items()) if tick > 0]
    return {'now': by_tick.get(0, []), 'timeline': steps, 'tags': tags, 'calls': calls}


def keep_backs(fn_root):
    """Players or zombies the map sends back from around a marker while nobody has a tag (Shangri-La keeps everyone
    off the Pack-a-Punch stairs until the stones are done): [{'centre', 'radius', 'to', 'tag', 'mobs'}]."""
    out = []
    for path in sorted(glob.glob(os.path.join(fn_root, 'unique', '**', '*.mcfunction'), recursive=True)):
        for m in KEEP_BACK.finditer(read(path)):
            reach = re.search(r"distance=\.\.([\d.]+)", m.group(4))
            if not reach:
                continue
            for centre in marker_positions(fn_root, m.group(1)):
                out.append({'centre': centre, 'radius': float(reach.group(1)), 'to': tuple(float(v) for v in m.group(5, 6, 7)),
                            'tag': m.group(2), 'mobs': m.group(3) == 'e' or 'tag=Zombie' in m.group(4)})
    return out


def keep_back_zone(zid, k):
    r = k['radius']
    lo = tuple(math.floor(c - r) for c in k['centre'])
    hi = tuple(math.floor(c + r) for c in k['centre'])
    # A tp to whole coordinates lands on a block corner; stand in the middle of that block instead.
    x, y, z = k['to']
    to = (x + 0.5 if x == int(x) else x, y, z + 0.5 if z == int(z) else z)
    return {'id': zid, 'name': '', 'p1': loc(lo), 'p2': loc(hi), 'radius': r, 'push_out_to': loc(to),
            'push_out': 'mobs' if k['mobs'] else 'players'}


HACK_SIGN =re.compile(r"data merge block (-?\d+) (-?\d+) (-?\d+) (\{.*Hold to Hack.*\})")


def hack_success(text):
    """What a hold-to-hack function does when the bar is full ("Hacking=300.."): the block and sound changes
    (leaving out ones that depend on what happened before, like a breach), and the points it pays."""
    targets, points = [], 0
    for line in text.splitlines():
        if 'Hacking=300..' not in line or 'Breach_' in line:
            continue
        paid = re.search(r'Points (\d+)', line)
        if paid:
            points = int(paid.group(1))
        targets += block_targets(line[line.find(' run ') + 5:] if ' run ' in line else line)
    return targets, points


def parse_hacks(fn_root, breaches):
    """Moon's Hacker: excavators that start at random during the game and breach a tunnel unless hacked in time
    (unique/on_moon/excavators: activate_N, excav_N_loop counting E_N_Timer down, check_N for the hack), and
    the Pack-a-Punch hack that shuts the gates around it for a while. Each becomes triggers built from the
    map's own functions. Perk and box hacks are left out."""
    out = {'excavators': [], 'pap': None, 'hacker_spots': []}
    folder = os.path.join(fn_root, 'unique', 'on_moon', 'excavators')
    for act in sorted(glob.glob(os.path.join(folder, 'activate_*.mcfunction'))):
        n = re.search(r'activate_(\d+)', act).group(1)
        a_text = read(act)
        start = re.search(r'set @p\S* (\w+) (\d+)', a_text)
        loop_path = os.path.join(folder, 'excav_%s_loop.mcfunction' % n)
        check_path = os.path.join(folder, 'check_%s.mcfunction' % n)
        if not start or not os.path.exists(loop_path) or not os.path.exists(check_path):
            continue
        timer, value = start.group(1), int(start.group(2))
        loop = read(loop_path)
        steps = timeline(loop, timer, start=value, countdown=True)
        for line in loop.splitlines():
            m = re.search(timer + r'=(-?\d+)\}.* add (Breach_\w+)', line)
            if m and breaches.get(m.group(2)):
                tick = max(1, value - int(m.group(1)))
                steps.append({'kind': 'after', 'amount': tick, 'targets': [{'kind': 'set_zone', 'value': z, 'amount': 0} for z in breaches[m.group(2)]]})
        check = read(check_path)
        sign = HACK_SIGN.search(check) or re.search(r"data merge block (-?\d+) (-?\d+) (-?\d+) ", check)
        rate = re.search(r'add @s Hacking ([1-9]\d*)', check)
        if not sign:
            continue
        targets, points = hack_success(check)
        out['excavators'].append({'n': n, 'start': [t for line in a_text.splitlines() for t in block_targets(line)],
                                  'timeline': sorted(steps, key=lambda s: s['amount']), 'sign': tuple(map(int, sign.group(1, 2, 3))),
                                  'hold': max(3, round(100 / int(rate.group(1)))) if rate else 10, 'hacked': targets, 'points': points})
    pap = os.path.join(fn_root, 'unique', 'equipment', 'hack_pap.mcfunction')
    if os.path.exists(pap):
        text = read(pap)
        sign = HACK_SIGN.search(text) or re.search(r"block (-?\d+) (-?\d+) (-?\d+) minecraft:\w*wall_sign", text)
        timer = re.search(r'scores=\{(\w+_Timer)=\d+\}', text)
        rate = re.search(r'add @s Hacking ([1-9]\d*)', text)
        if sign and timer:
            _, points = hack_success(text)
            out['pap'] = {'sign': tuple(map(int, sign.group(1, 2, 3))), 'timeline': timeline(text, timer.group(1)), 'points': points,
                          'hold': max(3, round(100 / int(rate.group(1)))) if rate else 12}
    out['hacker_spots'] = [tuple(int(math.floor(v)) for v in p) for p in marker_positions(fn_root, 'Hacker_Spawn')]
    return out


def proximity_start(fn_root, path):
    """Where a player has to come for the map to run {@code path} (Der Riese's courtyard spawners: a player within 8
    of a spot tags themselves, and that tag runs the function): (centre, reach) or None."""
    name = relative_name(fn_root, path)
    for text in (read(f) for f in all_functions(fn_root).values()):
        call = re.search(r'@p\[tag=(\w+)\][^\n]*function custom:' + re.escape(name) + r'\b', text)
        if not call:
            continue
        for line in text.splitlines():
            m = re.search(r'x=(-?[\d.]+),y=(-?[\d.]+),z=(-?[\d.]+),distance=\.\.([\d.]+)\][^\n]* add ' + call.group(1) + r'\b', line)
            if m:
                x, y, z, r = (float(v) for v in m.groups())
                return (x, y, z), r
    return None


def door_timeline(steps, door_blocks, door_id):
    """A timeline that slides a door open block by block: our door opens in one go at the moment the map starts
    moving it, and the clones that would move its (by then empty) blocks are left out."""
    first = None
    out = []
    for step in steps:
        kept = []
        for t in step['targets']:
            box = t.get('box')
            touches = t['kind'] == 'clone_blocks' and box and any(
                min(box['x1'], box['x2']) <= x <= max(box['x1'], box['x2']) and min(box['y1'], box['y2']) <= y <= max(box['y1'], box['y2'])
                and min(box['z1'], box['z2']) <= z <= max(box['z1'], box['z2']) for x, y, z in door_blocks)
            if touches:
                first = step['amount'] if first is None else min(first, step['amount'])
            else:
                kept.append(t)
        if kept:
            out.append(dict(step, targets=kept))
    if first is not None:
        out.append({'kind': 'after', 'amount': first, 'targets': [{'kind': 'open_door', 'value': door_id}]})
    return sorted(out, key=lambda s: s['amount'])


def spare_pap_signs(fn_root, world):
    """Pack-a-Punch signs on the spare machines the map keeps out of sight to clone into place."""
    out = set()
    for path in glob.glob(os.path.join(fn_root, 'unique', 'pap', '**', '*.mcfunction'), recursive=True):
        for m in CLONE.finditer(read(path)):
            x0, y0, z0, x1, y1, z1 = map(int, m.groups()[:6])
            lo, hi = (min(x0, x1), min(y0, y1), min(z0, z1)), (max(x0, x1), max(y0, y1), max(z0, z1))
            for be in world.signs_in(lo, hi):
                pos = (int(be['x']), int(be['y']), int(be['z']))
                if inside(pos, lo, hi) and 'pack' in ' '.join(world_sign_lines(be)).lower():
                    out.add(pos)
    return out


def power_pap_gate(fn_root):
    """The map's loop only runs its Pack-a-Punch functions once the power is on."""
    loops = os.path.join(fn_root, 'unique', 'map_specific_loops.mcfunction')
    return os.path.exists(loops) and any('Power_On' in line and 'function custom:unique/pap/' in line
                                         for line in read(loops).splitlines())


def comment_before(text, pos):
    """The last # comment above the line of a function that writes pos."""
    comment = ''
    for line in text.splitlines():
        if line.strip().startswith('#'):
            comment = line.strip().lower()
        elif pos in block_writes(line):
            return comment
    return None


# ------------------------------------------------------------------ assembly
def facing_towards(dx, dz):
    if abs(dx) >= abs(dz):
        return 'EAST' if dx > 0 else 'WEST'
    return 'SOUTH' if dz > 0 else 'NORTH'


def loc(p):
    return {'x': float(p[0]), 'y': float(p[1]), 'z': float(p[2])}


def block_json(p, state):
    return {'x': float(p[0]), 'y': float(p[1]), 'z': float(p[2]), 'blockdata': state, 'material': state.split('[')[0].split(':')[1]}


# The time of day and weather each Black Ops map is played in, held per player by the plugin.
SKIES = {'NachtDerUntoten': {'time_of_day': 'night'}, 'Verruckt': {'time_of_day': 'night'},
         'ShiNoNuma': {'time_of_day': 'night', 'weather': 'rain'}, 'DerRiese': {'time_of_day': 'night'},
         'KinoDerToten': {'time_of_day': 'night'}, 'CallOfTheDead': {'time_of_day': 'night', 'weather': 'storm'},
         'Shangri-La': {'time_of_day': 'day'}, 'Ascension': {'time_of_day': 'dusk'}, 'Moon': {'time_of_day': 'night'},
         'Five': {'time_of_day': 'night'}}


def build(world, datapacks, map_name, arena, world_name, index):
    fn_root = os.path.join(datapacks, map_name, 'data', 'custom', 'functions')
    report = []
    spawns = {}
    writes = sign_writes(fn_root)
    has_power = os.path.isdir(os.path.join(fn_root, 'maps', 'power'))

    def spawn_id(pos):
        if pos not in spawns:
            spawns[pos] = rid()
        return spawns[pos]

    # The map rebuilds its doors when a game starts, so the saved world can hold them open.
    reset_all, reset_texts = {}, []
    reset_door_signs = {}
    for path in sorted(glob.glob(os.path.join(fn_root, 'unique', 'reset*.mcfunction'))):
        text = read(path)
        reset_texts.append(text)
        reset_all.update(block_writes(text, world))
        for pos, state, nbt in sign_sets(text):
            d = re.search(r'add D(\d+)_B', click_text(nbt))
            if d:
                reset_door_signs.setdefault(int(d.group(1)), []).append((pos, state, sign_lines(nbt)))
    reset_state = {p: st for p, st in reset_all.items() if st != AIR}
    start = StartState(world, reset_all)

    def exact_start(x, y, z):
        return reset_all.get((x, y, z)) or world.block(x, y, z)

    door_parts = {n: parse_door(paths) for n, paths in sorted(door_functions(fn_root).items())}
    window_spawns = {}
    door_spawn_ids = {}
    for n, (_, _, door_spawns, _) in door_parts.items():
        for pos, window in door_spawns:
            door_spawn_ids.setdefault(n, set()).add(spawn_id(pos))
            if window:
                window_spawns.setdefault(window, set()).add(pos)
    door_spawn_positions = set(spawns)

    start_spawns, late_spawns = [], []
    for path in sorted(glob.glob(os.path.join(fn_root, '**', '*.mcfunction'), recursive=True)):
        if os.sep + 'doors' + os.sep in path:
            continue
        at_start = os.path.basename(path) in ('begin.mcfunction', 'prepare.mcfunction')
        for pos, window in spawners_in(read(path)):
            if pos in door_spawn_positions:
                continue
            spawn_id(pos)
            if window:
                window_spawns.setdefault(window, set()).add(pos)
            if at_start:
                start_spawns.append(pos)
            else:
                late_spawns.append((pos, path))
    late_spawns = [(p, f) for p, f in late_spawns if p not in start_spawns]

    barriers = []
    for path in sorted(glob.glob(os.path.join(fn_root, 'unique', 'barricades', 'window_*.mcfunction'))):
        w = 'w' + re.search(r'window_(\d+)', path).group(1)
        boards = parse_window(path, world)
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
    start_pos = start_position(fn_root)
    anchors = list(spawns) + [p for p, _ in boxes] + \
        [(int(x['x']), int(x['y']), int(x['z'])) for b in barriers for x in b['blocks']]
    if start_pos:
        anchors.append(tuple(int(v) for v in start_pos[:3]))
    if not anchors:
        raise SystemExit('nothing found for ' + map_name)

    # Door signs: the original world's signs whose click event tags D<N>_B.
    scan_lo = [min(p[i] for p in anchors) - 40 for i in range(3)]
    scan_hi = [max(p[i] for p in anchors) + 40 for i in range(3)]
    door_signs = {}
    for be in world.signs_in(scan_lo, scan_hi):
        m = re.search(r'add D(\d+)_B', sign_raw(be))
        if m:
            pos = (int(be['x']), int(be['y']), int(be['z']))
            door_signs.setdefault(int(m.group(1)), {})[pos] = world.block(*pos)
    # Doors standing open in the saved world have no signs there; the reset function puts them back.
    reset_sign_writes = []
    for n, found in reset_door_signs.items():
        for pos, state, lines in found:
            if pos not in door_signs.get(n, {}):
                door_signs.setdefault(n, {})[pos] = state
                reset_sign_writes.append({'x': pos[0], 'y': pos[1], 'z': pos[2], 'lines': lines, 'blockdata': state})
    sign_spots = {p for found in door_signs.values() for p in found}

    # Some door functions also clear blocks in a different map (copy-paste leftovers in the datapack);
    # blocks outside this map's footprint are not part of the door.
    foot_lo = [min(p[i] for p in anchors) - MAP_MARGIN for i in range(3)]
    foot_hi = [max(p[i] for p in anchors) + MAP_MARGIN for i in range(3)]

    def in_footprint(p):
        return foot_lo[0] <= p[0] <= foot_hi[0] and foot_lo[2] <= p[2] <= foot_hi[2]

    def door_block(p, state):
        return (state != AIR and not is_sign(state) and p not in sign_spots
                and not any(k in state for k in ('command_block', 'lever', 'button')))

    owners = {}
    for n, (_, cleared, _, _) in door_parts.items():
        for p in cleared:
            owners.setdefault(p, set()).add(n)
    doors = []
    for n, (price, cleared, _, _) in door_parts.items():
        own = [(p, start.block(*p)) for p in sorted(cleared) if owners[p] == {n}]
        own = [(p, s) for p, s in own if door_block(p, s)]
        kept = [(p, s) for p, s in own if in_footprint(p)]
        if len(kept) < len(own):
            report.append('door %d: dropped %d blocks outside the map' % (n, len(own) - len(kept)))
        signs_here = list(door_signs.get(n, {}))
        main = kept
        if signs_here and kept:
            groups = clusters([p for p, _ in kept])
            far = [g for g in groups if not any(near(p, s, FAR_FROM_SIGN) for p in g for s in signs_here)]
            for g in far:
                report.append('door %d: %d blocks around %d %d %d are more than %d blocks from its signs' % (
                    n, len(g), g[0][0], g[0][1], g[0][2], FAR_FROM_SIGN))
            near_blocks = {p for g in groups if g not in far for p in g}
            main = [(p, s) for p, s in kept if p in near_blocks] or kept
        doors.append({'n': n, 'price': price or 1000, 'blocks': kept, 'main': [p for p, _ in main],
                      'spawns': sorted(door_spawn_ids.get(n, ())), 'id': rid()})
    door_by_n = {d['n']: d for d in doors if d['blocks']}

    # Blocks that open on more than one door, or on the power: the map's own mechanisms. Each becomes a
    # door without a price or sign that triggers open: power on, or a player walking through one of the doors.
    cleared_by, placed_by = {}, {}
    for p, ns in owners.items():
        if len(ns) > 1:
            cleared_by.setdefault(p, set()).update((False, frozenset([n])) for n in ns)
    for n, (_, _, _, conditional) in door_parts.items():
        for power, ds, gone, placed in conditional:
            option = (power, ds | {n})
            for p in gone:
                cleared_by.setdefault(p, set()).add(option)
            for p, s in placed.items():
                placed_by.setdefault(option, {})[p] = s
    for ds, final in power_effects(fn_root, world).items():
        for p, s in final.items():
            if s == AIR:
                cleared_by.setdefault(p, set()).add((True, ds))
            else:
                placed_by.setdefault((True, ds), {})[p] = s
    own_blocks = {p for d in doors for p, _ in d['blocks']}
    regions = {}
    for p, options in cleared_by.items():
        state = exact_start(*p)
        if p not in own_blocks and in_footprint(p) and door_block(p, state):
            regions.setdefault(frozenset(minimal(options)), {})[p] = state
    mechanism_doors, option_doors = [], {}
    for options, blocks in sorted(regions.items(), key=lambda r: min(r[1])):
        groups = clusters(sorted(blocks))
        too_large = [box_of(g) for g in groups if len(g) > MAX_REGION_BLOCKS]
        for group in groups:
            if len(group) > MAX_REGION_BLOCKS:
                report.append('mechanism of %d blocks around %d %d %d (%s) is too large for a door; left as the world has it'
                              % (len(group), group[0][0], group[0][1], group[0][2], ', '.join(option_id(o) for o in options)))
                continue
            if any(all(b[0][k] - 4 <= p[i] <= b[1][k] + 4 for i, k in enumerate('xyz')) for b in too_large for p in group):
                continue
            door = {'n': None, 'price': 0, 'blocks': [(p, blocks[p]) for p in sorted(group)], 'main': [], 'spawns': [],
                    'id': rid(), 'options': options}
            mechanism_doors.append(door)
            for o in options:
                option_doors.setdefault(o, []).append(door['id'])
    placed_once, claimed = {}, set()
    for option in sorted(placed_by, key=lambda o: (not o[0], sorted(o[1]))):
        blocks = {p: s for p, s in placed_by[option].items()
                  if p not in claimed and in_footprint(p) and not any(k in s for k in COSMETIC) and s != exact_start(*p)}
        if len(blocks) > MAX_PLACED_BLOCKS:
            report.append('%s places %d blocks; too many to replay, left out' % (option_id(option), len(blocks)))
            continue
        if blocks:
            placed_once[option] = blocks
            claimed |= set(blocks)

    doors_all = doors + mechanism_doors
    every = anchors + [p for d in doors_all for p, _ in d['blocks']]
    margin = 12
    lo = [min(p[i] for p in every) - margin for i in range(3)]
    hi = [max(p[i] for p in every) + margin for i in range(3)]
    lo[1] = min(p[1] for p in every) - 6
    hi[1] = max(p[1] for p in every) + 14

    def in_bounds(p):
        return all(lo[i] <= p[i] <= hi[i] for i in range(3))

    door_json = []
    for d in doors:
        if not d['blocks']:
            report.append('door %d: no solid blocks found, skipped' % d['n'])
            continue
        door_json.append({'id': d['id'], 'powerRequired': False, 'price': d['price'],
                          'blocks': [block_json(p, s) for p, s in d['blocks']],
                          'signs': [dict(loc(p), blockdata=s) for p, s in door_signs.get(d['n'], {}).items()],
                          'spawns': d['spawns']})
        report.append('door %d: %d blocks, %d signs, %d spawns, %d points' % (d['n'], len(d['blocks']), len(door_signs.get(d['n'], {})), len(d['spawns']), d['price']))

    def add_mechanism_door(blocks, why, spawn_ids=()):
        door_json.append({'id': blocks['id'], 'powerRequired': False, 'price': 0,
                          'blocks': [block_json(p, s) for p, s in blocks['blocks']], 'signs': [], 'spawns': list(spawn_ids)})
        p = blocks['blocks'][0][0]
        report.append('door (no price, %s): %d blocks at %d %d %d' % (why, len(blocks['blocks']), p[0], p[1], p[2]))

    triggers, zones, pool_spots = [], [], []
    zone_ids = set()
    for option in sorted(set(option_doors) | set(placed_once), key=lambda o: (not o[0], sorted(o[1]))):
        power, ds = option
        missing = [n for n in ds if n not in door_by_n]
        if missing:
            report.append('%s: door %s has no blocks to walk through; left closed' % (option_id(option), missing))
            option_doors.pop(option, None)
            continue
        steps = ([{'kind': 'power_on', 'amount': 0}] if power else []) + \
                [{'kind': 'stand_on', 'zone': 'door_%d' % n, 'amount': 1} for n in sorted(ds)]
        for n in ds:
            if n not in zone_ids:
                zone_ids.add(n)
                zones.append(door_zone(n, door_by_n[n]['main']))
        targets = [{'kind': 'open_door', 'value': i} for i in option_doors.get(option, [])]
        if option in placed_once:
            targets.append({'kind': 'place_blocks', 'blocks': [dict(x=p[0], y=p[1], z=p[2], blockdata=s) for p, s in sorted(placed_once[option].items())]})
        name = ' + '.join((['power'] if power else []) + ['door %d' % n for n in sorted(ds)])
        triggers.append(trigger(option_id(option), 'Opens with ' + name, steps, targets))
    for d in mechanism_doors:
        if any(o in option_doors for o in d['options']):
            add_mechanism_door(d, 'opens with ' + ' or '.join(option_id(o)[:-len('_opens')] for o in sorted(d['options'], key=lambda o: sorted(o[1])) if o in option_doors))
    power_placed = {p for (power, _), blocks in placed_once.items() if power for p in blocks}

    signs = list(reset_sign_writes)
    for pos, lines in merged_signs(writes).items():
        if in_bounds(pos):
            signs.append({'x': pos[0], 'y': pos[1], 'z': pos[2], 'lines': lines})
    switches = [p for p in power_switches(start, index) if in_bounds(p)]
    for pos in switches:
        signs = [x for x in signs if (x['x'], x['y'], x['z']) != pos]
        signs.append({'x': pos[0], 'y': pos[1], 'z': pos[2], 'lines': ['[Zombies]', 'Power', '', ''], 'power': True})
        report.append('power switch at %s' % (pos,))

    def put_sign(pos, lines, blockdata=None):
        nonlocal signs
        signs = [x for x in signs if (x['x'], x['y'], x['z']) != tuple(pos)]
        entry = {'x': pos[0], 'y': pos[1], 'z': pos[2], 'lines': lines}
        if blockdata:
            entry['blockdata'] = blockdata
        signs.append(entry)

    merged = merged_signs_all(writes)
    trap_json = []
    trap_spots = set()

    def write_trap_signs(trap_id, positions, cost):
        for p in positions:
            trap_spots.add(p)
            put_sign(p, ['[Zombies]', 'Trap', trap_id, str(cost)])

    def trap_door(positions):
        """The door a trap waits for: the door function that writes its buy sign, or the door the power
        function checks before writing it."""
        for pos, lines, _, path, line in writes:
            if pos in positions and has_cost(lines):
                m = re.search(r'maps/doors/door(\d+)/', path.replace(os.sep, '/'))
                ds = {int(m.group(1))} if m else condition(line)[1]
                for n in sorted(ds):
                    if n in door_by_n:
                        return door_by_n[n]
        return None

    def add_trap(entry, positions, cost, describe):
        trap_signs = [p for p in positions if in_bounds(p)]
        if len(trap_signs) < len(positions):
            report.append('trap %s: dropped %d sign(s) outside the map' % (entry['id'], len(positions) - len(trap_signs)))
        if trap_signs:
            entry.update(x=float(trap_signs[0][0]), y=float(trap_signs[0][1]), z=float(trap_signs[0][2]))
        elif 'x' not in entry:
            box = entry['region']
            entry.update({k: float((box['p1'][k] + box['p2'][k]) // 2) for k in 'xyz'})
        door = trap_door(set(trap_signs))
        if door:
            entry['requires_door'] = door['id']
        trap_json.append(entry)
        write_trap_signs(entry['id'], trap_signs, cost)
        report.append(describe + (', needs door %d' % door['n'] if door else ''))

    for t in parse_traps(fn_root, merged):
        main = t['regions'][0]
        add_trap({'id': 'trap%s' % t['n'], 'cost': t['cost'], 'type': t['type'], 'requires_power': has_power,
                  'duration_seconds': t['duration'], 'cooldown_seconds': t['cooldown'],
                  'region': {'p1': main[0], 'p2': main[1]},
                  'extra_regions': [{'p1': r[0], 'p2': r[1]} for r in t['regions'][1:]]},
                 t['signs'], t['cost'],
                 'trap %s: %s (%s), %d area(s), %d sign(s), %d points' % (t['n'], t['name'], t['type'], len(t['regions']), len(t['signs']), t['cost']))

    for t in parse_sentries(fn_root):
        add_trap({'id': 'sentry%s' % t['n'], 'cost': t['cost'], 'type': 'turret', 'requires_power': has_power,
                  'duration_seconds': t['duration'], 'cooldown_seconds': 0, 'region': fbox(t['region']), 'extra_regions': []},
                 t['signs'], t['cost'], 'turret %s: %d sign(s), %d points, %ds' % (t['n'], len(t['signs']), t['cost'], t['duration']))

    for t in parse_unique_traps(fn_root):
        main = t['regions'][0]
        anchor = tuple((main[0][k] + main[1][k]) // 2 for k in 'xyz')
        add_trap({'x': float(anchor[0]), 'y': float(anchor[1]), 'z': float(anchor[2]), 'id': t['id'],
                  'cost': t['cost'], 'type': t['type'], 'requires_power': has_power and not t['automatic'],
                  'automatic': t['automatic'], 'duration_seconds': t['duration'], 'cooldown_seconds': t['cooldown'],
                  'region': fbox(main), 'extra_regions': [fbox(r) for r in t['regions'][1:]]},
                 t['signs'], t['cost'],
                 'trap %s: %s, %s, %d area(s), %ds on / %ds off' % (
                     t['id'], t['type'], 'automatic' if t['automatic'] else '%d points, %d sign(s)' % (t['cost'], len(t['signs'])),
                     len(t['regions']), t['duration'], t['cooldown']))

    transport_json = []
    speeds = {'zipline': 8.0, 'platform': 4.0, 'cart': 12.0, 'teleport': 4.0}
    travel = parse_transports(fn_root) + parse_landers(fn_root) + parse_elevators(fn_root, exact_start) + \
        parse_jump_pads(fn_root) + parse_geysers(fn_root)
    travel = [t for t in travel if all(lo[0] - 40 <= p[0] <= hi[0] + 40 and lo[2] - 40 <= p[2] <= hi[2] + 40 for p in t['points'])]
    gated = power_gated(fn_root)
    placed_signs = {}
    for _, _, _, _, line in writes:
        for p, state, _ in sign_sets(line):
            placed_signs.setdefault(p, state)
    power_texts = {path: read(path) for path in power_paths(fn_root)}
    reset_text = '\n'.join(reset_texts)
    scopes = {}
    for t in travel:
        t['id'] = re.sub(r'[^a-z0-9]+', '_', t.get('label', t['name']).lower()).strip('_')
        scope = transport_scope(fn_root, t['path'], [o['path'] for o in travel])
        scopes[t['id']] = scope
        # A call point needs no sign (an elevator's sign rides along with its cabin); a label does.
        tagged, called = [[p for p in found if in_bounds(p)] for found in transport_signs(fn_root, scope, writes)]
        t['signs'] = [p for p in tagged + called if p in placed_signs or is_sign(world.block(*p))]
        a, b = t['points'][0], t['points'][-1]
        if 'call_points' not in t:
            t['call_points'] = sorted_call_points(tagged or called, a, b, t.get('two_way', False))
        if not t['signs'] and t['style'] != 'teleport':
            # A sign the map only fills in with placeholder text (Shangri-La's slide: "Destination not open").
            t['signs'] = [pos for pos, lines, _, _, _ in writes if is_placeholder(lines) and near(pos, a, 4) and in_bounds(pos)][:1]
            t['call_points'] = t['call_points'] or t['signs']
        text = '\n'.join(read(f) for f in scope)
        t['cooldown'] = t.get('cooldown') or cooldown_seconds(text)
        reasons = []
        if t.get('requires_power'):
            reasons.append('power function')
        if scope & gated:
            reasons.append('loop runs with power on')
        if any(pos in t['signs'] and 'must turn on' in ' '.join(lines).lower() for pos, lines, _, path, _ in writes
               if os.path.basename(path).startswith('reset')):
            reasons.append('power placeholder on its sign')
        if any('data merge block %d %d %d' % p in pt for p in t['signs'] for pt in power_texts.values()):
            reasons.append('power writes its sign')
        if any(near(q, a, 4) for q in power_placed):
            reasons.append('power puts its platform in')
        t['requires_power'] = bool(reasons) and has_power
        t['relock'] = relinks(t['path'])
        t['why_power'] = reasons
    folders_of = {t['id']: {os.path.dirname(f) for f in scopes[t['id']]} - {os.path.dirname(t['path'] or '')} for t in travel}
    for t in travel:
        if t.get('cooldown_group') or t['style'] != 'teleport':
            continue
        shared = [f for f in sorted(folders_of[t['id']]) if any(f in folders_of[o['id']] for o in travel if o is not t)]
        if shared:
            t['cooldown_group'] = os.path.basename(shared[0])
    for t in travel:
        entry = {'id': t['id'], 'name': t['name'], 'style': t['style'], 'cost': t['cost'],
                 'requires_power': t['requires_power'], 'cooldown_seconds': t['cooldown'],
                 'speed': t.get('speed', speeds[t['style']]), 'two_way': t.get('two_way', False), 'platform_size': 3,
                 'waypoints': [{'x': p[0], 'y': p[1], 'z': p[2]} for p in distinct(t['points'])],
                 'call_points': [call_point(p) for p in t['call_points']]}
        if t.get('cooldown_group'):
            entry['cooldown_group'] = t['cooldown_group']
        if t['relock']:
            entry['relock_after_use'] = True
        if t.get('then'):
            entry['stay_seconds'] = t['stay']
            entry['then'] = [{'points': [{'x': p[0], 'y': p[1], 'z': p[2]} for p in pts], 'seconds': sec} for pts, sec in t['then']]
            report.append('%s: stays %ds, then %s' % (t['id'], t['stay'], ', '.join('%d spot(s) for %ds' % (len(pts), sec) for pts, sec in t['then'])))
        transport_json.append(entry)
        report.append('transport %s: %s, %d points%s%s%s%s%s%s' % (
            t['name'], t['style'], len(t['points']), ', two-way' if entry['two_way'] else ', one-way',
            ', %d points' % t['cost'] if t['cost'] else '', ', needs power (%s)' % '; '.join(t['why_power']) if t['requires_power'] else '',
            ', %ds cooldown' % t['cooldown'] if t['cooldown'] else '', ' [%s]' % t['cooldown_group'] if t.get('cooldown_group') else '',
            ', %d call point(s)' % len(t['call_points']) if t['call_points'] else ''))

    # Riders must stay inside the arena all the way: landers fly high above the map.
    for t in transport_json:
        for w in t['waypoints'] + t['call_points'] + [p for stop in t.get('then', []) for p in stop['points']]:
            for i, k in enumerate('xyz'):
                lo[i] = min(lo[i], math.floor(w[k]) - (2 if k == 'y' else 4))
                hi[i] = max(hi[i], math.ceil(w[k]) + (8 if k == 'y' else 4))

    settings = {'pap_locked': False, 'perk_pool_mode': 'fixed'}
    settings.update(SKIES.get(arena, {}))
    if len(switches) > 1:
        settings['power_switches'] = len(switches)

    egg = parse_song_egg(fn_root, world, index, lo, hi)
    if egg:
        triggers.append(egg)
        report.append('song egg: %d interact(s) at %d spot(s) -> %s' % (len(egg['steps']), len({(s['x'], s['y'], s['z']) for s in egg['steps']}), egg['targets'][0]['value']))

    pool = parse_perk_pool(fn_root)
    pool_positions = set()
    if pool:
        settings['perk_pool_mode'] = pool['mode']
        if pool['mode'] == 'random_on_open':
            settings['perk_pool_reveal'] = 'door'
        if pool.get('extra'):
            settings['perk_pool_perks'] = pool['extra']
        pool_spots = [{'x': s['pos'][0], 'y': s['pos'][1], 'z': s['pos'][2], 'facing': s['facing']} for s in pool['spots']]
        pool_positions = {s['pos'] for s in pool['spots']}
        for s in pool['signs']:
            put_sign(s['pos'], s['lines'], s['state'])
        report.append('perk pool (%s): %d spots (%s), perks %s' % (pool['mode'], len(pool_spots), ', '.join(pool['places']), ', '.join(' '.join(s['lines']).split('[')[0].strip() for s in pool['signs'])))

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

    def transport_in(folder):
        return [t for t in travel if t['path'] and os.path.dirname(t['path']) == folder and t['id'] in {x['id'] for x in transport_json}]

    def click_label(pos, fallback):
        """The text the map shows on a sign while clicking it does something ("Initiate link", "Link pad with core")."""
        for q, lines, nbt, _, _ in writes:
            text = ' '.join(l.strip() for l in lines if l.strip())
            if q == pos and 'clickEvent' in click_text(nbt) and text and not is_placeholder(lines) and not has_cost(lines):
                return text
        return fallback

    # Teleporters that have to be linked first (Der Riese: each pad to the mainframe, Kino: the projector).
    chains = [c for c in link_chains(fn_root) if all(in_bounds(p) for p in c['signs'])]
    link_triggers = []
    for c in chains:
        if c['pap']:
            continue
        linked = transport_in(c['folder'])
        if not linked:
            report.append('link at %s: no teleporter found in %s' % (c['signs'][0], c['folder']))
            continue
        tid = 'link_' + os.path.basename(c['folder'])
        steps = [{'kind': 'power_on', 'amount': 0}] + [dict(kind='interact', x=p[0], y=p[1], z=p[2], amount=0, description=click_label(p, 'Link')) for p in c['signs']]
        limit = link_time_limit(fn_root, c)
        triggers.append(trigger(tid, 'Link ' + linked[0]['name'], steps, [{'kind': 'enable_teleporter', 'value': t['id']} for t in linked],
                                ordered=True, time_limit=limit, reset_on_fail=limit > 0))
        link_triggers.append((tid, c))
        report.append('link %s: %d sign(s), %ds to finish -> %s' % (tid, len(c['signs']), limit, ', '.join(t['id'] for t in linked)))

    gate = None
    extra_doors = []
    has_pap = os.path.isdir(os.path.join(fn_root, 'unique', 'pap'))
    switch_set = switch_group(fn_root, world, index, lo, hi)
    plates = plate_gate(fn_root)
    pap_chain = next((c for c in chains if c['pap']), None)
    landers = by_label('lander')
    if switch_set and any('pap' in f for f in switch_set['unlocks']):
        gate = {'id': 'pap_switches', 'name': 'Pack-a-Punch switches', 'ordered': False,
                'steps': [{'kind': 'power_on', 'amount': 0}] + [dict(kind='interact', x=p[0], y=p[1], z=p[2], amount=0, description='Switch') for p in switch_set['switches']],
                'teleporters': [t['id'] for f in switch_set['unlocks'] for t in transport_json if f.split('/')[-1] in t['id'] and 'pap' in t['id']]}
    elif plates and has_pap:
        gate = {'id': 'pap_plates', 'name': 'Stepping stones', 'ordered': False,
                'steps': ([{'kind': 'power_on', 'amount': 0}] if plates['power'] else []) +
                         [dict(kind='stand_on', x=p[0], y=p[1], z=p[2], amount=1) for p in plates['plates']], 'teleporters': []}
        seq = pap_open_sequence(fn_root, set(plates['plates']))
        if seq:
            by_tick = {s['amount']: list(s['targets']) for s in seq['timeline']}
            by_tick[0] = list(seq['now'])
            for k in keep_backs(fn_root):
                opened = sorted(t for t, tag, added in seq['tags'] if tag == k['tag'] and added)
                centre = tuple(int(math.floor(v)) for v in k['centre'])
                if not opened or not in_bounds(centre):
                    continue
                zid = 'keep_back_%d' % (sum(1 for z in zones if z['id'].startswith('keep_back_')) + 1)
                zones.append(keep_back_zone(zid, k))
                by_tick.setdefault(opened[0], []).append({'kind': 'set_zone', 'value': zid, 'amount': 0})
                for t in sorted(t for t, tag, added in seq['tags'] if tag == k['tag'] and not added and t > opened[0]):
                    by_tick.setdefault(t, []).append({'kind': 'set_zone', 'value': zid, 'amount': 1})
                report.append('%s: %s within %g of %d %d %d sent to %g %g %g until the gate opens' % (
                    zid, 'zombies' if k['mobs'] else 'players', k['radius'], *centre, *k['to']))
            # Placing the stones again starts the gate over.
            for t in sorted({t for t, name in seq['calls'] if name == plates['path']}):
                by_tick.setdefault(t, []).append({'kind': 'reset_trigger', 'value': 'pap_plates'})
            gate['now'] = by_tick.pop(0)
            gate['timeline'] = [{'kind': 'after', 'amount': t, 'targets': ts} for t, ts in sorted(by_tick.items()) if ts]
    elif pap_chain:
        gate = {'id': 'pap_link', 'name': 'Link the teleporter', 'ordered': True,
                'steps': [{'kind': 'power_on', 'amount': 0}] + [dict(kind='interact', x=p[0], y=p[1], z=p[2], amount=0, description=click_label(p, 'Link')) for p in pap_chain['signs']],
                'teleporters': [t['id'] for t in transport_in(pap_chain['folder']) if t['style'] == 'teleport']}
    elif landers and any('pap' in read(f).lower() for f in glob.glob(os.path.join(fn_root, 'unique', '*lander*', '*.mcfunction'))):
        gate = {'id': 'pap_landers', 'name': 'Ride every lander, then launch', 'ordered': False,
                'steps': [{'kind': 'ride_transport', 'transport': t['id'], 'amount': 0} for t in landers], 'teleporters': []}
        launch = launch_gate(fn_root, os.path.join(fn_root, 'unique', 'landers'), exact_start)
        if launch:
            sign, path, blocks, timer = launch
            gate['steps'].append(dict(kind='interact', x=sign[0], y=sign[1], z=sign[2], amount=0, description=click_label(sign, 'Launch')))
            door = {'id': rid(), 'blocks': sorted(blocks.items()), 'path': path}
            extra_doors.append(door)
            gate['timeline'] = door_timeline(timeline(read(path), timer), set(blocks), door['id'])
            if not any(t['kind'] == 'open_door' for step in gate['timeline'] for t in step['targets']):
                gate['doors'] = [door['id']]
    elif has_power and power_pap_gate(fn_root):
        gate = {'id': 'pap_power', 'name': 'Pack-a-Punch after power', 'ordered': False,
                'steps': [{'kind': 'power_on', 'amount': 0}], 'teleporters': []}
    if link_triggers:
        # The link function every pad runs: what it clears once all pads are linked is the Pack-a-Punch wall.
        common = set.intersection(*[set(c['functions'][1:]) for _, c in link_triggers]) if len(link_triggers) > 1 else set()
        wall = {}
        for name in sorted(common):
            for p, s in block_writes(read(function_path(fn_root, name))).items():
                if s == AIR and p in reset_state and door_block(p, reset_state[p]):
                    wall[p] = reset_state[p]
        if wall:
            is_pap = any('pap' in (comment_before(t, min(wall)) or '') for t in reset_texts)
            door = {'id': rid(), 'blocks': sorted(wall.items())}
            add_mechanism_door(door, 'opens once every pad is linked')
            targets = [{'kind': 'open_door', 'value': door['id']}] + ([{'kind': 'enable_pack_a_punch'}] if is_pap and not gate else [])
            triggers.append(trigger('all_linked', 'Every pad linked', [{'kind': 'trigger_done', 'trigger': tid, 'amount': 0} for tid, _ in link_triggers], targets))
            if is_pap and not gate:
                settings['pap_locked'] = True
                report.append('pack-a-punch gate: link every pad (%d triggers), opens the wall at %d %d %d' % (len(link_triggers), *min(wall)))
    pap_room = timed_pap(fn_root) if gate and gate['teleporters'] else None
    room_spawns = set()
    if pap_room:
        # The gate only opens the teleporters; Pack-a-Punch comes up in the room they lead to, for a while, and
        # after the room's cycle the teleporters lock and the gate has to be done again.
        text, timer, room_path = pap_room
        dest = next(t['waypoints'][-1] for t in transport_json if t['id'] == gate['teleporters'][0])
        zone_id = 'zone_pap_room'
        zones.append({'id': zone_id, 'name': '', 'p1': {'x': math.floor(dest['x']) - 3.0, 'y': math.floor(dest['y']) - 1.0, 'z': math.floor(dest['z']) - 3.0},
                      'p2': {'x': math.floor(dest['x']) + 3.0, 'y': math.floor(dest['y']) + 2.0, 'z': math.floor(dest['z']) + 3.0},
                      'gravity': 1.0, 'no_air': False, 'damage_per_second': 0.0})
        steps = timeline(text, timer)
        # The room's own spawners start partway through the cycle.
        room_spawns = {p for p, f in late_spawns if f == room_path}
        if room_spawns:
            door = {'id': rid(), 'powerRequired': False, 'price': 0, 'blocks': [], 'signs': [], 'spawns': [spawns[p] for p in sorted(room_spawns)]}
            door_json.append(door)
            at = []
            for line in text.splitlines():
                m = re.search(timer + r'=(\d+)\}', line)
                c = re.search(r'summon minecraft:marker ' + COORD + ' ' + COORD + ' ' + COORD, line)
                if m and c and tuple(int(math.floor(float(v))) for v in c.groups()) in room_spawns:
                    at.append(int(m.group(1)))
            steps.append({'kind': 'after', 'amount': min(at) if at else 1, 'targets': [{'kind': 'open_door', 'value': door['id']}]})
            steps.sort(key=lambda s: s['amount'])
        end = steps[-1]['amount'] + 20 if steps else 20
        steps.append({'kind': 'after', 'amount': end, 'targets': [{'kind': 'lock_teleporter', 'value': tid} for tid in gate['teleporters']] +
                      [{'kind': 'reset_trigger', 'value': gate['id']}, {'kind': 'reset_trigger', 'value': 'pap_room'}]})
        room = trigger('pap_room', 'Pack-a-Punch room', [{'kind': 'trigger_done', 'trigger': gate['id'], 'amount': 0},
                                                          {'kind': 'stand_on', 'zone': zone_id, 'amount': 1}], steps, ordered=True)
        room['options']['silent'] = True
        triggers.append(room)
        report.append('pack-a-punch room: %d timed step(s) over %ds after the first player arrives at %d %d %d'
                      % (len(steps), end // 20, dest['x'], dest['y'], dest['z']))
    if gate:
        settings['pap_locked'] = True
        targets = ([] if pap_room else [{'kind': 'enable_pack_a_punch'}]) + [{'kind': 'enable_teleporter', 'value': tid} for tid in gate['teleporters']] + \
                  [{'kind': 'open_door', 'value': i} for i in gate.get('doors', [])] + gate.get('now', []) + gate.get('timeline', [])
        triggers.append(trigger(gate['id'], gate['name'], gate['steps'], targets, ordered=gate['ordered']))
        if gate.get('timeline'):
            report.append('%s: %d timed step(s) over %ds' % (gate['id'], len(gate['timeline']), gate['timeline'][-1]['amount'] // 20))
        report.append('pack-a-punch gate %s: %d step(s)%s%s' % (gate['name'], len(gate['steps']),
                                                              ', unlocks ' + ', '.join(gate['teleporters']) if gate['teleporters'] else '',
                                                              ', opens %d door(s)' % len(gate.get('doors', [])) if gate.get('doors') else ''))
    elif has_pap:
        report.append('pack-a-punch: map has unique/pap but no gate that can be modelled; left unlocked')

    # Moon's Hacker: excavators start at random once the power is on and breach their tunnel unless hacked.
    hacks = parse_hacks(fn_root, (env or {}).get('breaches', {}))
    for ex in hacks['excavators']:
        if not in_bounds(ex['sign']):
            continue
        run = trigger('excavator_%s' % ex['n'], 'Excavator %s' % ex['n'],
                      [{'kind': 'power_on', 'amount': 0}, {'kind': 'round_chance', 'amount': 11}], ex['start'] + ex['timeline'])
        run['options']['silent'] = True
        hack_id = 'hack_excavator_%s' % ex['n']
        targets = [{'kind': 'reset_trigger', 'value': run['id']}] + ex['hacked'] + \
                  ([{'kind': 'give_points_to_player', 'amount': ex['points']}] if ex['points'] else []) + [{'kind': 'reset_trigger', 'value': hack_id}]
        hack = trigger(hack_id, 'Hack excavator %s' % ex['n'],
                       [{'kind': 'trigger_done', 'trigger': run['id'], 'amount': 0},
                        dict(kind='interact', x=ex['sign'][0], y=ex['sign'][1], z=ex['sign'][2], amount=0, description='Hack the excavator',
                             gear='Hacker', hold_seconds=ex['hold'])], targets, ordered=True)
        triggers += [run, hack]
        put_sign(ex['sign'], ['Excavator', '', '', ''])
        report.append('excavator %s: starts at random after power, breaches after %ds, hack at %d %d %d (%ds) for %d points'
                      % (ex['n'], max(s['amount'] for s in ex['timeline']) // 20, *ex['sign'], ex['hold'], ex['points']))
    if hacks['pap'] and in_bounds(hacks['pap']['sign']):
        h = hacks['pap']
        step = dict(kind='interact', x=h['sign'][0], y=h['sign'][1], z=h['sign'][2], amount=0, description='Hack Pack-a-Punch',
                    gear='Hacker', hold_seconds=h['hold'])
        end = (h['timeline'][-1]['amount'] if h['timeline'] else 0) + 20
        # The first hack pays; after that it only shuts the gates again, once the last cycle is over.
        triggers.append(trigger('hack_pap', 'Hack Pack-a-Punch', [step], h['timeline'] +
                                ([{'kind': 'give_points_to_player', 'amount': h['points']}] if h['points'] else [])))
        triggers.append(trigger('hack_pap_again', 'Hack Pack-a-Punch', [{'kind': 'trigger_done', 'trigger': 'hack_pap', 'amount': 0}, dict(step)],
                                h['timeline'] + [{'kind': 'after', 'amount': end, 'targets': [{'kind': 'reset_trigger', 'value': 'hack_pap_again'}]}], ordered=True))
        report.append('pack-a-punch hack at %d %d %d: %d timed step(s), %d points the first time' % (*h['sign'], len(h['timeline']), h['points']))
    spots = [p for p in hacks['hacker_spots'] if in_bounds(p) and is_sign(world.block(*p))]
    if spots:
        put_sign(spots[0], ['', '', 'Trade equipment', 'for Hacker'])
        report.append('hacker: handed out at %d %d %d (the map picks one of %d spots at random)' % (*spots[0], len(spots)))

    # Gates a sign's own function lifts (Shi No Numa's zipline): opened by riding the transport that function
    # starts, else by pressing the sign.
    for sign, name, cleared in click_gates(fn_root, writes, reset_state):
        blocks = {p: reset_state[p] for p in cleared if door_block(p, reset_state[p]) and in_bounds(p)}
        if not blocks:
            continue
        door = {'id': rid(), 'blocks': sorted(blocks.items())}
        text = read(function_path(fn_root, name))
        timers = set(re.findall(r"objectives add (\w+) minecraft\.custom", text))
        ridden = next((t for t in transport_json if any(re.search(r"\b%s=" % tm, '\n'.join(read(f) for f in scopes[t['id']])) for tm in timers)), None)
        step = {'kind': 'ride_transport', 'transport': ridden['id'], 'amount': 0} if ridden else \
            dict(kind='interact', x=sign[0], y=sign[1], z=sign[2], amount=0, description=name.split('/')[-1])
        add_mechanism_door(door, 'opens with ' + (('a ride on ' + ridden['id']) if ridden else 'the sign at %d %d %d' % sign))
        triggers.append(trigger('gate_' + name.split('/')[-1], 'Gate of ' + name.split('/')[-1], [step], [{'kind': 'open_door', 'value': door['id']}]))

    gate_spawns = {}
    for door in extra_doors:
        late_here = [p for p, f in late_spawns if f == door['path']]
        gate_spawns[door['id']] = late_here
        add_mechanism_door(door, 'opened by the %s trigger' % gate['id'], [spawns[p] for p in late_here])
    attached = {p for ps in gate_spawns.values() for p in ps}

    # Spawners the map only starts later (power, a player reaching a spot) belong to the door of their area,
    # when one is right by them; the others start with the game.
    priced = [d for d in door_json if d['price']]
    power_doors = [d for d in door_json if d['id'] in option_doors.get((True, frozenset()), [])]
    near_doors = {}
    for pos, path in late_spawns:
        if pos in attached or pos in room_spawns:
            continue
        reached = proximity_start(fn_root, path) if path not in power_texts else None
        if reached:
            if path not in near_doors:
                (cx, cy, cz), r = reached
                door = {'id': rid(), 'powerRequired': False, 'price': 0, 'blocks': [], 'signs': [], 'spawns': []}
                zone_id = 'zone_near_%d' % (len(near_doors) + 1)
                zones.append({'id': zone_id, 'name': '', 'p1': {'x': math.floor(cx - r), 'y': math.floor(cy - 3), 'z': math.floor(cz - r)},
                              'p2': {'x': math.floor(cx + r), 'y': math.floor(cy + 4), 'z': math.floor(cz + r)},
                              'gravity': 1.0, 'no_air': False, 'damage_per_second': 0.0})
                door_json.append(door)
                triggers.append(trigger('reach_%d' % (len(near_doors) + 1), 'Spawners near %d %d %d' % (cx, cy, cz),
                                        [{'kind': 'stand_on', 'zone': zone_id, 'amount': 1}], [{'kind': 'open_door', 'value': door['id']}]))
                triggers[-1]['options']['silent'] = True
                near_doors[path] = door
                report.append('spawners from %s start when a player comes within %d of %d %d %d' % (relative_name(fn_root, path), r, cx, cy, cz))
            near_doors[path]['spawns'].append(spawns[pos])
            continue
        reach = lambda d: min((b['x'] - pos[0]) ** 2 + (b['y'] - pos[1]) ** 2 + (b['z'] - pos[2]) ** 2 for b in d['blocks'])
        powered = path in power_texts and power_doors
        nearest = min(power_doors if powered else priced, key=reach, default=None)
        if nearest is None or not powered and reach(nearest) > LATE_SPAWNER_REACH ** 2:
            report.append('spawner %d %d %d from %s starts with the game: the map starts it later, but no door is by it'
                          % (pos[0], pos[1], pos[2], relative_name(fn_root, path)))
            continue
        nearest['spawns'].append(spawns[pos])
        report.append('spawner %d %d %d from %s opens with door at %d %d %d' % (
            pos[0], pos[1], pos[2], relative_name(fn_root, path), nearest['blocks'][0]['x'], nearest['blocks'][0]['y'], nearest['blocks'][0]['z']))

    for pap_sign in pap_marker_signs(world, fn_root):
        pos = (pap_sign['x'], pap_sign['y'], pap_sign['z'])
        # A Pack-a-Punch sign the map writes right by the marker already is the machine.
        other = next((s for s in signs if (s['x'], s['y'], s['z']) != pos and 'pack' in ' '.join(s['lines']).lower()
                      and close((s['x'], s['y'], s['z']), pos)), None)
        if not inside(pos, lo, hi) or other:
            continue
        put_sign(pos, pap_sign['lines'], pap_sign['blockdata'])
        report.append('pack-a-punch sign written at %d %d %d (%s)' % (*pos, pap_sign['blockdata']))

    # Transport signs carry a short label: their prompt shows the cost, and buy text the sign converter
    # doesn't know would be left as an unknown sign.
    labels = {'teleport': 'Teleporter', 'platform': 'Ride', 'cart': 'Minecart', 'zipline': ''}
    step_names = {(s['x'], s['y'], s['z']): s.get('description', '') for tr in triggers for s in tr['steps']
                  if s.get('kind') == 'interact' and s.get('description') != '???'}
    kept = [t for t in travel if t['id'] in {x['id'] for x in transport_json}]
    for calls in (False, True):
        for t in kept:
            word = '' if 'zipline' in t['id'] else next((w.capitalize() for w in ('lander', 'elevator', 'slide', 'geyser', 'flinger') if w in t['id']), labels[t['style']])
            for p in t['signs']:
                if p in trap_spots or p in pool_positions or p in sign_spots or (p in t['call_points']) != calls:
                    continue
                put_sign(p, ['', word if calls else step_names.get(p, ''), '', ''])
    # The buy sign beside a call point ("Use Elevator [Cost: 250]", "Teleport to Mainframe") is the transport's
    # too: the converter can't read it, and the transport's prompt already shows the cost.
    ride_words = ('use ', 'teleport', 'elevator', 'zipline', 'lander', 'ride', 'minecart', 'slide')
    current = {(x['x'], x['y'], x['z']): x['lines'] for x in signs}
    done = set()
    for t in kept:
        word = '' if 'zipline' in t['id'] else next((w.capitalize() for w in ('lander', 'elevator', 'slide', 'geyser', 'flinger') if w in t['id']), labels[t['style']])
        for c in t['call_points']:
            cx, cy, cz = (int(math.floor(v)) for v in c)
            for p in box_points(cx - 1, cy - 1, cz - 1, cx + 1, cy + 1, cz + 1):
                if p in done or p in trap_spots or p in sign_spots or any(near(p, q, 1) for q in pool_positions):
                    continue
                lines = current.get(p)
                if lines is None and is_sign(world.block(*p)):
                    be = world.block_entity(*p)
                    lines = world_sign_lines(be) if be is not None else None
                text = ' '.join(lines or []).lower()
                if lines and has_cost(lines) and any(w in text for w in ride_words):
                    put_sign(p, ['', word, '', ''])
                    done.add(p)
    # Signs that only ever show a placeholder ("You must turn on the power first!", "Cooling down...") are
    # blanked, or labelled with the step a trigger waits for there.
    sign_states = placed_signs
    written = {(x['x'], x['y'], x['z']) for x in signs}
    for pos in sorted({pos for pos, lines, _, _, _ in writes if is_placeholder(lines)}):
        if pos in written or pos in trap_spots or pos in sign_spots or not in_bounds(pos) or any(near(pos, q, 1) for q in pool_positions):
            continue
        state = sign_states.get(pos)
        if not state and not is_sign(world.block(*pos)):
            continue
        put_sign(pos, ['', step_names.get(pos, ''), '', ''], state)
        written.add(pos)

    if start_pos:
        spawn = {'x': start_pos[0] + 0.5, 'y': start_pos[1], 'z': start_pos[2] + 0.5}
    else:
        spawn = loc(next(iter(spawns)))
        report.append('no start position found; using a zombie spawn')

    spawn_json = [dict(loc(p), world=world_name, id=i) for p, i in spawns.items()]
    box_json = [dict(loc(p), facing=f, cost=950, id=rid()) for p, f in boxes]
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
    report.insert(0, '%s -> %s: %d spawns (%d at start), %d doors, %d barriers, %d box locations, %d signs to write, %d transports, %d triggers'
                  % (map_name, arena, len(spawn_json), len(start_spawns), len(door_json), len(barriers), len(box_json), len(signs),
                     len(transport_json), len(triggers)))
    # The world was saved mid-game (perks revealed, gates open); the reset function holds the start state.
    # Door blocks are the doors' own business and signs are written separately.
    # A block the map only puts up at reset and some other function clears mid-game is a mechanism the
    # plugin may not run, so it would stay shut for good; those keep whatever the saved world has.
    opened_later = set()
    for path in all_functions(fn_root).values():
        if os.path.basename(path).startswith('reset'):
            continue
        for pos, state in block_writes(read(path)).items():
            if state == AIR:
                opened_later.add(pos)
    door_blocks = {(int(b['x']), int(b['y']), int(b['z'])) for d in door_json for b in d['blocks']}
    start_state = {p: st for p, st in reset_all.items()
                   if in_bounds(p) and p not in door_blocks and '_sign' not in st
                   and (st == AIR or p not in opened_later)
                   and world.block(*p) != st}
    start_state.update(box_edits)
    for t in travel:
        for p, state in t.get('floor_edits', {}).items():
            start_state[p] = state
        if t.get('floor_edits'):
            report.append('%s: cabin floor put down at %d stop block(s)' % (t['id'], len(t['floor_edits'])))
    spare = spare_pap_signs(fn_root, world)
    for p in spare:
        start_state[p] = AIR
    signs = [x for x in signs if (x['x'], x['y'], x['z']) not in spare]
    if spare:
        report.append('removed %d Pack-a-Punch sign(s) on spare machines: %s' % (len(spare), ', '.join('%d %d %d' % p for p in sorted(spare))))
    edits = [{'x': p[0], 'y': p[1], 'z': p[2], 'blockdata': b} for p, b in sorted(start_state.items())
             if 'barrier' not in b and 'sign' not in b or b == AIR]
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
