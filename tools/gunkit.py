#!/usr/bin/env python3
"""
gunkit — author detailed COM:Z gun item models from parts and materials.

Successor to modelkit: instead of one flat colour per box, every face samples a procedurally painted
material tile (wood grain, brushed metal, brass, glass, ribs, grilles, dials) at a constant texel
density, so large and small parts share one consistent surface look. Round parts are built from
boxes rotated in 22.5° steps (the only angles every supported client accepts).

Conventions (match the pack's approved hand-made guns): muzzle towards -Z, stock towards +Z, gun
centred on x = 8, all coordinates inside the item-model limit [-16, 32].

    from gunkit import Gun
    g = Gun('wunderwaffe')
    g.box((7, 4, 0), (9, 6, 10), 'gunmetal')
    g.cyl('z', (8, 8), 1.2, -10, 0, 'steel', seg=16)
    g.wire([(8, 8, -10), (8, 6, -14)], 0.4, 'steel')
    g.save()
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, 'resourcepack/comz-pack/assets/minecraft')
TILE = 32
ANGLES = (-45, -22.5, 0, 22.5, 45)

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 2.75, -1.5], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"translation": [0, 2.75, -1.5], "scale": [0.6, 0.6, 0.6]},
    "firstperson_righthand": {"translation": [-0.5, 3, -1], "scale": [0.9, 0.9, 0.9]},
    "firstperson_lefthand": {"translation": [-0.5, 3, -1], "scale": [0.9, 0.9, 0.9]},
    "ground": {"translation": [0, 1.5, 0.75], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [90, -45, 90], "translation": [-1.5, 0.5, 0], "scale": [0.42, 0.42, 0.42]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.2, 1.2, 1.2]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [-2.5, 0, -11.25], "scale": [1.1, 1.1, 1.1]},
}


def _clamp(v):
    return max(0, min(255, int(round(v))))


def _mix(c, k):
    return tuple(_clamp(x * k) for x in c)


class Material:
    """A painted 32x32 tile. mode 'tile' samples a face-sized window; 'stretch' maps the whole tile."""

    def __init__(self, painter, mode='tile', density=2.0):
        self.painter, self.mode, self.density = painter, mode, density


def _noise(rng, img, base, amount):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            k = 1 + rng.uniform(-amount, amount)
            px[x, y] = _mix(base, k) + (255,)


def brushed(base, amount=0.06, streak=0.08, wear=0.0):
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE))
        px = img.load()
        rows = [1 + rng.uniform(-streak, streak) for _ in range(TILE)]
        for y in range(TILE):
            for x in range(TILE):
                k = rows[y] * (1 + rng.uniform(-amount, amount))
                if wear and rng.random() < wear:
                    k *= 1.45
                px[x, y] = _mix(base, k) + (255,)
        return img
    return paint


def wood(base, grain=0.18):
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE))
        px = img.load()
        phase = [rng.uniform(0, 6.28) for _ in range(3)]
        for y in range(TILE):
            for x in range(TILE):
                g = math.sin(y * 0.9 + 2.2 * math.sin(x * 0.11 + phase[0]) + phase[1])
                k = 1 + grain * g * 0.6 + rng.uniform(-0.04, 0.04)
                if abs(g) > 0.93:
                    k *= 0.78
                px[x, y] = _mix(base, k) + (255,)
        return img
    return paint


def ribs(base, count=8, vertical=False, depth=0.45):
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE))
        px = img.load()
        period = TILE / count
        for y in range(TILE):
            for x in range(TILE):
                t = ((x if vertical else y) % period) / period
                k = 1.18 if t < 0.25 else (1 - depth if t > 0.8 else 1.0)
                px[x, y] = _mix(base, k * (1 + rng.uniform(-0.04, 0.04))) + (255,)
        return img
    return paint


def dial(face, rim, needle=(30, 20, 20)):
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE), rim + (255,))
        d = ImageDraw.Draw(img)
        d.ellipse((3, 3, TILE - 4, TILE - 4), fill=_mix(rim, 0.6) + (255,))
        d.ellipse((6, 6, TILE - 7, TILE - 7), fill=face + (255,))
        for a in range(0, 360, 30):
            r0, r1 = 8, 10.5
            c = TILE / 2 - 0.5
            d.line((c + r0 * math.cos(math.radians(a)), c + r0 * math.sin(math.radians(a)),
                    c + r1 * math.cos(math.radians(a)), c + r1 * math.sin(math.radians(a))), fill=(40, 40, 40, 255))
        d.line((TILE / 2, TILE / 2, TILE / 2 + 6, TILE / 2 - 6), fill=needle + (255,), width=2)
        d.ellipse((10, 8, 15, 12), fill=(255, 255, 255, 180))
        return img
    return paint


def grille(base, hole=(15, 15, 15)):
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE), base + (255,))
        d = ImageDraw.Draw(img)
        d.ellipse((1, 1, TILE - 2, TILE - 2), fill=_mix(base, 1.15) + (255,))
        for y in range(5, TILE - 4, 4):
            for x in range(5, TILE - 4, 4):
                if (x - 16) ** 2 + (y - 16) ** 2 < 150:
                    d.rectangle((x, y, x + 1, y + 1), fill=hole + (255,))
        return img
    return paint


def glow(base, core=(230, 245, 255)):
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE))
        px = img.load()
        for y in range(TILE):
            for x in range(TILE):
                t = abs(y - TILE / 2) / (TILE / 2)
                c = tuple(_clamp(core[i] * (1 - t) + base[i] * t) for i in range(3))
                px[x, y] = c + (255,)
        return img
    return paint


def energy(base=(22, 62, 205), streak=(150, 225, 255)):
    """Ray Gun glass tube: deep blue with bright swirling plasma streaks."""
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE))
        px = img.load()
        ph = [rng.uniform(0, 6.28) for _ in range(3)]
        for y in range(TILE):
            for x in range(TILE):
                v = math.sin(x * 0.45 + 2.5 * math.sin(y * 0.3 + ph[0]) + ph[1]) * math.sin(y * 0.35 + ph[2])
                t = max(0.0, v) ** 2
                edge = abs(y - TILE / 2) / (TILE / 2)
                c = tuple(_clamp(base[i] * (1 - 0.35 * edge) + (streak[i] - base[i]) * t) for i in range(3))
                px[x, y] = c + (255,)
        return img
    return paint


def gauge():
    """Blast-O-Matic dial: cream face, red/yellow/green arcs, black hub and needle."""
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE), (120, 26, 28, 255))
        d = ImageDraw.Draw(img)
        d.ellipse((1, 1, TILE - 2, TILE - 2), fill=(88, 18, 20, 255))
        d.ellipse((3, 3, TILE - 4, TILE - 4), fill=(226, 214, 180, 255))
        d.pieslice((4, 4, TILE - 5, TILE - 5), 180, 225, fill=(176, 36, 30, 255))
        d.pieslice((4, 4, TILE - 5, TILE - 5), 225, 290, fill=(222, 196, 60, 255))
        d.pieslice((4, 4, TILE - 5, TILE - 5), 290, 360, fill=(70, 160, 70, 255))
        d.ellipse((9, 9, TILE - 10, TILE - 10), fill=(226, 214, 180, 255))
        d.line((16, 16, 9, 9), fill=(20, 20, 20, 255), width=1)
        d.ellipse((13, 13, 18, 18), fill=(24, 24, 26, 255))
        return img
    return paint


def hazard(a=(226, 186, 40), b=(24, 24, 26), width=5):
    """Yellow/black diagonal warning stripes."""
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE))
        px = img.load()
        for y in range(TILE):
            for x in range(TILE):
                c = a if ((x + y) // width) % 2 == 0 else b
                px[x, y] = _mix(c, 1 + rng.uniform(-0.05, 0.05)) + (255,)
        return img
    return paint


def tread(base=(170, 140, 96)):
    """Tyre-like chevron tread for the Thundergun turbine wheel."""
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE))
        px = img.load()
        for y in range(TILE):
            for x in range(TILE):
                v = (y + abs(x - TILE / 2) * 0.8) % 8
                k = 1.15 if v < 3 else 0.7
                px[x, y] = _mix(base, k * (1 + rng.uniform(-0.06, 0.06))) + (255,)
        return img
    return paint


def tiger(a=(40, 48, 66), b=(150, 166, 190)):
    """V-R11 blue tiger-stripe camo."""
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE))
        px = img.load()
        ph = rng.uniform(0, 6.28)
        for y in range(TILE):
            for x in range(TILE):
                v = math.sin(x * 0.42 + 2.2 * math.sin(y * 0.27 + ph)) + 0.5 * math.sin(y * 0.6 + x * 0.1)
                c = b if v > 0.55 else a
                px[x, y] = _mix(c, 1 + rng.uniform(-0.06, 0.06)) + (255,)
        return img
    return paint


def engraved(base, ink=(150, 150, 150), lines=3):
    """Stamped receiver markings: short rows of light glyph strokes on metal."""
    def paint(rng):
        img = brushed(base, amount=0.05, streak=0.05)(rng)
        px = img.load()
        for row in range(lines):
            y = 6 + row * 8
            x = 3
            while x < TILE - 4:
                w = rng.randint(1, 3)
                for dx in range(w):
                    for dy in range(rng.randint(2, 4)):
                        if rng.random() < 0.85 and x + dx < TILE and y + dy < TILE:
                            px[x + dx, y + dy] = ink + (255,)
                x += w + rng.randint(1, 2)
                if rng.random() < 0.15:
                    x += 3
        return img
    return paint


def label(bg=(214, 196, 120), ink=(40, 30, 20), stripe=None):
    """Paper/metal label with printed text rows and an optional colour band."""
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE), bg + (255,))
        d = ImageDraw.Draw(img)
        if stripe:
            d.rectangle((0, 0, TILE, 7), fill=stripe + (255,))
        for y in range(11 if stripe else 4, TILE - 3, 5):
            x = 3
            while x < TILE - 4:
                w = rng.randint(2, 6)
                d.rectangle((x, y, min(TILE - 3, x + w), y + 1), fill=ink + (255,))
                x += w + 2
        d.rectangle((0, 0, TILE - 1, TILE - 1), outline=_mix(bg, 0.6) + (255,))
        return img
    return paint


def pcb():
    """Green circuit board with traces and components."""
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE), (34, 92, 48, 255))
        d = ImageDraw.Draw(img)
        for _ in range(9):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            d.line((x, y, x + rng.randint(-12, 12), y), fill=(196, 168, 80, 255))
            d.line((x, y, x, y + rng.randint(-12, 12)), fill=(196, 168, 80, 255))
        for _ in range(6):
            x, y = rng.randrange(TILE - 6), rng.randrange(TILE - 4)
            d.rectangle((x, y, x + 5, y + 3), fill=(24, 24, 26, 255))
        return img
    return paint


def flat(base, amount=0.05):
    def paint(rng):
        img = Image.new('RGBA', (TILE, TILE))
        _noise(rng, img, base, amount)
        return img
    return paint


def pap_camo(rng, shade=1.0):
    """BO2 Pack-a-Punch camo: dark marbled swirls with purple/teal veins and sparse gold flecks."""
    palette = [(18, 20, 36), (34, 28, 58), (62, 36, 84), (28, 70, 84), (70, 84, 46), (24, 22, 30)]
    img = Image.new('RGBA', (TILE, TILE))
    px = img.load()
    f = [rng.uniform(0.15, 0.35) for _ in range(4)]
    ph = [rng.uniform(0, 6.28) for _ in range(4)]
    for y in range(TILE):
        for x in range(TILE):
            wx = x + 4 * math.sin(y * f[0] + ph[0])
            wy = y + 4 * math.sin(x * f[1] + ph[1])
            v = math.sin(wx * f[2] + ph[2]) + math.sin(wy * f[3] + ph[3]) + 0.6 * math.sin((wx + wy) * 0.21)
            t = (v + 2.6) / 5.2
            c = palette[min(len(palette) - 1, int(t * len(palette)))]
            if abs(v) < 0.08:
                c = (120, 70, 150)
            if rng.random() < 0.012:
                c = (196, 168, 80)
            px[x, y] = _mix(c, shade * (1 + rng.uniform(-0.06, 0.06))) + (255,)
    return img


# Parts that keep their own look on the Pack-a-Punch variant.
PAP_KEEP = {'label_warn', 'label_paper', 'label_white', 'pcb', 'glow_green', 'grip_ribs_dark', 'ice', 'bulb_white', 'hazard', 'rubber', 'dial', 'grille', 'glow_blue', 'glass', 'bulb', 'energy', 'gauge', 'grip_black', 'glow_purple', 'led_red'}

MATERIALS = {
    'gunmetal': Material(brushed((84, 89, 97), wear=0.014)),
    'darkmetal': Material(brushed((38, 42, 50), wear=0.008)),
    'steel': Material(brushed((128, 131, 134), streak=0.1, wear=0.03)),
    'chrome': Material(brushed((178, 182, 186), amount=0.04, streak=0.12)),
    'brass': Material(brushed((150, 112, 52), streak=0.1, wear=0.03)),
    'copper': Material(brushed((150, 82, 48), streak=0.1)),
    'wood': Material(wood((92, 42, 30))),
    'wood_light': Material(wood((122, 64, 38))),
    'rubber': Material(flat((24, 24, 25), 0.06)),
    'red_paint': Material(brushed((132, 28, 30), amount=0.05, streak=0.06, wear=0.035)),
    'red_dark': Material(brushed((92, 20, 22), amount=0.05, wear=0.02)),
    'gold': Material(brushed((198, 156, 64), streak=0.12, wear=0.02)),
    'energy': Material(energy(), mode='stretch'),
    'gauge': Material(gauge(), mode='stretch'),
    'grip_black': Material(ribs((32, 32, 34), count=9, vertical=False, depth=0.55), mode='stretch'),
    'oldmetal': Material(brushed((50, 49, 47), amount=0.1, streak=0.1, wear=0.05)),
    'wood_red': Material(wood((116, 56, 34), grain=0.22)),
    'worn_steel': Material(brushed((112, 114, 110), amount=0.08, streak=0.1, wear=0.05)),
    'grip_ribs_black': Material(ribs((30, 30, 32), count=12, vertical=False, depth=0.5), mode='stretch'),
    'bakelite': Material(brushed((58, 34, 26), amount=0.06, streak=0.05, wear=0.01)),
    'leather': Material(brushed((112, 70, 38), amount=0.08, streak=0.05, wear=0.01)),
    'wood_ribbed_light': Material(ribs((150, 92, 52), count=8, vertical=True, depth=0.35), mode='stretch'),
    'bakelite_orange': Material(brushed((150, 74, 32), amount=0.06, streak=0.06, wear=0.02)),
    'polymer_tan': Material(brushed((150, 128, 96), amount=0.05, streak=0.04, wear=0.02)),
    'bakelite_red': Material(brushed((128, 72, 56), amount=0.06, streak=0.05, wear=0.02)),
    'ivory': Material(brushed((212, 206, 192), amount=0.04, streak=0.04)),
    'red_star': Material(flat((170, 30, 28), 0.05)),
    'glow_purple': Material(glow((120, 40, 200), core=(240, 200, 255)), mode='stretch'),
    'fur_brown': Material(brushed((110, 74, 46), amount=0.12, streak=0.03)),
    'fez_red': Material(flat((150, 30, 34), 0.05)),
    'putty': Material(brushed((196, 178, 132), amount=0.06, streak=0.03)),
    'monkey_skin': Material(flat((214, 160, 128), 0.05)),
    'fur_dark': Material(brushed((72, 46, 30), amount=0.12, streak=0.03)),
    'led_red': Material(glow((200, 30, 20), core=(255, 180, 160)), mode='stretch'),
    'hazard': Material(hazard(), mode='stretch'),
    'tread': Material(tread(), mode='stretch'),
    'bulb_white': Material(glow((150, 210, 160), core=(250, 255, 240)), mode='stretch'),
    'tiger_blue': Material(tiger(), mode='stretch'),
    'ice': Material(glow((120, 170, 220), core=(235, 248, 255)), mode='stretch'),
    'rust_iron': Material(brushed((92, 72, 60), amount=0.1, streak=0.08, wear=0.05)),
    'paint_blue': Material(brushed((30, 52, 118), amount=0.05, streak=0.06, wear=0.012)),
    'paint_red': Material(brushed((116, 30, 28), amount=0.05, streak=0.06, wear=0.012)),
    'paint_yellow': Material(brushed((172, 146, 58), amount=0.06, streak=0.06, wear=0.012)),
    'glow_green': Material(glow((60, 160, 60), core=(210, 255, 180)), mode='stretch'),
    'grip_ribs_dark': Material(ribs((36, 36, 38), count=7, vertical=False, depth=0.55), mode='stretch'),
    'engraved_dark': Material(engraved((46, 48, 52), ink=(150, 150, 146)), mode='stretch'),
    'engraved_steel': Material(engraved((120, 124, 128), ink=(60, 60, 62)), mode='stretch'),
    'label_warn': Material(label((222, 196, 70), (30, 24, 16), stripe=(170, 30, 26)), mode='stretch'),
    'label_paper': Material(label((206, 196, 168), (50, 40, 32)), mode='stretch'),
    'label_white': Material(label((220, 222, 224), (40, 60, 120), stripe=(40, 80, 170)), mode='stretch'),
    'pcb': Material(pcb(), mode='stretch'),
    'stencil_gold': Material(engraved((140, 34, 30), ink=(214, 176, 70), lines=2), mode='stretch'),
    'stencil_atom': Material(dial((214, 176, 70), (36, 64, 150), needle=(214, 176, 70)), mode='stretch'),
    'rust_brown': Material(brushed((84, 72, 66), amount=0.1, streak=0.1, wear=0.03)),
    'wood_ribbed_dark': Material(ribs((92, 74, 56), count=12, vertical=False, depth=0.45), mode='stretch'),
    'wood_maroon': Material(wood((84, 38, 36), grain=0.14)),
    'polymer_white': Material(brushed((184, 186, 182), amount=0.05, streak=0.04, wear=0.02)),
    'polymer_olive': Material(brushed((70, 74, 62), amount=0.05, streak=0.04, wear=0.02)),
    'polymer_dark': Material(brushed((58, 56, 52), amount=0.03, streak=0.01, wear=0.01)),
    'wood_honey': Material(wood((192, 142, 92), grain=0.14)),
    'wood_tan': Material(wood((150, 98, 58), grain=0.16)),
    'wood_ribbed': Material(ribs((112, 58, 36), count=10, vertical=False, depth=0.4), mode='stretch'),
    'ribs_v': Material(ribs((70, 70, 72), count=8, vertical=True), mode='stretch'),
    'ribs_h': Material(ribs((84, 82, 78), count=8, vertical=False), mode='stretch'),
    'dial': Material(dial((214, 206, 180), (60, 60, 62)), mode='stretch'),
    'grille': Material(grille((150, 152, 150)), mode='stretch'),
    'glow_blue': Material(glow((40, 90, 230)), mode='stretch'),
    'glass': Material(glow((90, 110, 120), core=(200, 220, 225)), mode='stretch'),
    'bulb': Material(glow((170, 80, 30), core=(255, 200, 120)), mode='stretch'),
}


PHOTO_MIX = 85


def _match_tone(region, tone):
    """Scale a painted region's colours so its mean matches `tone`, keeping its texture detail."""
    rgb = region.convert('RGB')
    mean = rgb.resize((1, 1), Image.BOX).getpixel((0, 0))
    gains = [t / max(m, 1) for t, m in zip(tone, mean)]
    bands = [b.point(lambda v, g=g: min(255, int(v * g))) for b, g in zip(rgb.split(), gains)]
    out = Image.merge('RGB', bands).convert('RGBA')
    out.putalpha(region.getchannel('A'))
    return out


class Gun:
    def __init__(self, name, texture_size=128, seed=7, offset=(0, 0, 0)):
        """offset shifts the finished model so its centre sits near the block centre like the
        approved guns do (GUI/ground framing depend on it); author in measured coordinates."""
        self.name = name
        self.offset = offset
        self.size = texture_size
        self.elements = []
        self.used = []
        self.rng = random.Random(seed)

    def _mat_index(self, mat):
        if mat not in self.used:
            self.used.append(mat)
        return self.used.index(mat)

    def box(self, frm, to, mat, rot=None, name=None, faces=None):
        """rot = (axis, angle, origin) with angle in ANGLES."""
        frm, to = [round(min(a, b), 4) for a, b in zip(frm, to)], [round(max(a, b), 4) for a, b in zip(frm, to)]
        el = {'name': name or mat, 'from': frm, 'to': to, 'mat': mat, 'faces': faces}
        if rot:
            axis, angle, origin = rot
            assert angle in ANGLES, angle
            if angle:
                el['rotation'] = {'angle': angle, 'axis': axis, 'origin': [round(o, 4) for o in origin]}
        self._mat_index(mat)
        self.elements.append(el)
        return el

    def cyl(self, axis, center, r, a0, a1, mat, seg=8, name=None, faces=None):
        """Regular seg-gon prism along axis between a0..a1 with inradius r; center is the 2D centre in
        the other two axes. Built from slabs (2r across, one polygon side thick) in both orientations at
        each allowed angle, whose union is exactly the polygon."""
        ax = 'xyz'.index(axis)
        others = [i for i in range(3) if i != ax]
        origin = [0, 0, 0]
        origin[ax] = (a0 + a1) / 2
        origin[others[0]], origin[others[1]] = center
        if seg == 4:
            slabs = [(0, r, r)]
        else:
            side = r * math.tan(math.pi / seg)
            angles = {8: [0, 45], 16: [0, 22.5, 45, -22.5]}[seg]
            slabs = [(ang, hu, hv) for ang in angles for hu, hv in ((r, side), (side, r))]
        for ang, hu, hv in slabs:
            frm, to = [0, 0, 0], [0, 0, 0]
            frm[ax], to[ax] = a0, a1
            frm[others[0]], to[others[0]] = center[0] - hu, center[0] + hu
            frm[others[1]], to[others[1]] = center[1] - hv, center[1] + hv
            self.box(frm, to, mat, rot=(axis, ang, origin), name=name, faces=faces)

    def ring(self, axis, center, r_in, r_out, a0, a1, mat, name=None):
        """Hollow octagonal ring around axis (e.g. a hooded sight seen down the barrel)."""
        ax = 'xyz'.index(axis)
        u, v = [i for i in range(3) if i != ax]
        t = r_out - r_in
        rm = (r_in + r_out) / 2
        half = r_out * math.tan(math.pi / 8) * 1.15
        for k in range(8):
            phi = k * 45
            cu = center[0] + rm * math.cos(math.radians(phi))
            cv = center[1] + rm * math.sin(math.radians(phi))
            frm, to = [0, 0, 0], [0, 0, 0]
            frm[ax], to[ax] = a0, a1
            frm[u], to[u] = cu - t / 2, cu + t / 2
            frm[v], to[v] = cv - half, cv + half
            origin = [0, 0, 0]
            origin[ax], origin[u], origin[v] = (a0 + a1) / 2, cu, cv
            ang = 0
            if phi % 180 == 90:
                frm[u], to[u] = cu - half, cu + half
                frm[v], to[v] = cv - t / 2, cv + t / 2
            elif phi % 90:
                ang = 45 if phi in (45, 225) else -45
            self.box(frm, to, mat, rot=(axis, ang, origin), name=name)

    def wire(self, points, t, mat, name=None):
        """Polyline in a plane of constant x. Each segment's direction snaps to an allowed X-axis angle
        and starts where the previous one ended, so the chain stays connected."""
        x = points[0][0]
        cy, cz = points[0][1], points[0][2]
        for _, ty, tz in points[1:]:
            dy, dz = ty - cy, tz - cz
            length = math.hypot(dy, dz)
            if length < 1e-6:
                continue
            heading = math.degrees(math.atan2(dy, dz))
            options = [h for h in range(-180, 181, 45)] + [h + 22.5 for h in range(-180, 180, 45)]
            snapped = min(options, key=lambda h: abs((heading - h + 180) % 360 - 180))
            ey = cy + length * math.sin(math.radians(snapped))
            ez = cz + length * math.cos(math.radians(snapped))
            my, mz = (cy + ey) / 2, (cz + ez) / 2
            along_z = abs(math.cos(math.radians(snapped))) >= abs(math.sin(math.radians(snapped))) - 1e-9
            base = snapped if along_z else snapped - 90
            base = (base + 180) % 180
            if base > 90:
                base -= 180
            pad = t / 2
            if along_z:
                frm, to = (x - t / 2, my - t / 2, mz - length / 2 - pad), (x + t / 2, my + t / 2, mz + length / 2 + pad)
            else:
                frm, to = (x - t / 2, my - length / 2 - pad, mz - t / 2), (x + t / 2, my + length / 2 + pad, mz + t / 2)
            # Minecraft's x-axis rotation turns +z towards -y, so the heading angle maps to -angle.
            self.box(frm, to, mat, rot=('x', -base if base else 0, (x, my, mz)), name=name)
            cy, cz = ey, ez

    def _atlas(self, pap=False):
        per_row = self.size // TILE
        assert len(self.used) <= per_row * per_row, 'too many materials for the atlas'
        img = Image.new('RGBA', (self.size, self.size), (0, 0, 0, 0))
        origins = {}
        for i, mat in enumerate(self.used):
            ox, oy = (i % per_row) * TILE, (i // per_row) * TILE
            rng = random.Random(sum(map(ord, mat)))
            tile = pap_camo(rng, 0.8 if mat == 'darkmetal' else 1.0) if pap and mat not in PAP_KEEP else MATERIALS[mat].painter(rng)
            img.paste(tile, (ox, oy))
            origins[mat] = (ox, oy)
        return img, origins

    def _face_uv(self, mat, origin, w, h, key):
        m = MATERIALS[mat]
        ox, oy = origin
        if m.mode == 'stretch':
            px = (ox + 0.5, oy + 0.5, ox + TILE - 0.5, oy + TILE - 0.5)
        else:
            pw = max(1.0, min(TILE - 1, w * m.density))
            ph = max(1.0, min(TILE - 1, h * m.density))
            rng = random.Random(key)
            sx = ox + rng.uniform(0, TILE - pw)
            sy = oy + rng.uniform(0, TILE - ph)
            px = (sx, sy, sx + pw, sy + ph)
        k = 16 / self.size
        return [round(v * k, 4) for v in px]

    def _fit_offset(self):
        """Shift the requested offset just enough to keep every element inside [-16, 32]."""
        off = list(self.offset)
        for i in range(3):
            lo = min(min(e['from'][i], e['to'][i]) for e in self.elements) + off[i]
            hi = max(max(e['from'][i], e['to'][i]) for e in self.elements) + off[i]
            if lo < -16:
                off[i] += -16 - lo
            elif hi > 32:
                off[i] -= hi - 32
        self.offset = tuple(off)

    # Materials that read as painted/coated: they get chipped edges and light scratches.
    WORN = {'red_paint', 'paint_red', 'paint_blue', 'polymer_olive', 'polymer_tan', 'polymer_dark', 'tiger_blue',
            'bakelite', 'bakelite_red', 'bakelite_orange', 'rust_iron', 'oldmetal'}
    METALS = {'gunmetal', 'darkmetal', 'steel', 'chrome', 'worn_steel', 'brass', 'copper', 'gold', 'oldmetal', 'rust_iron'}
    DENSITY = 8  # texels per model unit, 4x the pack's original 64px guns

    def _face_list(self):
        dims = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
        faces = []
        for idx, e in enumerate(self.elements):
            size = [e['to'][i] - e['from'][i] for i in range(3)]
            for f, (a, b) in dims.items():
                mat = (e['faces'] or {}).get(f, e['mat'])
                pw = max(2, min(160, int(round(size[a] * self.DENSITY))))
                ph = max(2, min(160, int(round(size[b] * self.DENSITY))))
                faces.append((idx, f, mat, pw, ph, size[a], size[b]))
        return faces

    def _pack(self, faces):
        """Shelf-pack every face into a square atlas; returns (size, {(idx, face): (x, y)})."""
        size = 256
        while True:
            x = y = row_h = 0
            pos = {}
            ok = True
            for idx, f, mat, pw, ph, *_ in sorted(faces, key=lambda t: -t[4]):
                if x + pw > size:
                    x, y, row_h = 0, y + row_h + 1, 0
                if y + ph > size:
                    ok = False
                    break
                pos[(idx, f)] = (x, y)
                x += pw + 1
                row_h = max(row_h, ph)
            if ok:
                return size, pos
            size *= 2

    def _paint(self, pap=False):
        faces = self._face_list()
        size, pos = self._pack(faces)
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        tiles = {}
        photo_tone = {}
        if getattr(self, 'photo', None) and not pap:
            # Faces the photo can't reach (top, bottom, ends) take the colour of the element's photo side.
            for idx, f, mat, pw, ph, wu, hu in faces:
                if f == 'east' and mat not in PAP_KEEP and idx not in photo_tone:
                    side = self.photo.region(self.elements[idx], f, pw, ph, rotated_ok=True)
                    if side is not None:
                        photo_tone[idx] = side.convert('RGB').resize((1, 1), Image.BOX).getpixel((0, 0))
        for idx, f, mat, pw, ph, wu, hu in faces:
            key = (mat, pap and mat not in PAP_KEEP)
            if key not in tiles:
                rng = random.Random(sum(map(ord, mat)))
                if key[1]:
                    tiles[key] = pap_camo(rng, 0.8 if mat == 'darkmetal' else 1.0)
                else:
                    tiles[key] = MATERIALS[mat].painter(rng)
            tile = tiles[key]
            stretch = MATERIALS[mat].mode == 'stretch' and not key[1]
            rng = random.Random(idx * 31 + len(f))
            photo = None
            if getattr(self, 'photo', None) and not pap and mat not in PAP_KEEP:
                photo = self.photo.region(self.elements[idx], f, pw, ph)
            if stretch:
                region = tile.resize((pw, ph), Image.NEAREST)
            else:
                region = Image.new('RGBA', (pw, ph))
                ox, oy = rng.randrange(TILE), rng.randrange(TILE)
                for ty in range(-oy, ph, TILE):
                    for tx in range(-ox, pw, TILE):
                        region.paste(tile, (tx, ty))
            if photo is not None:
                # Blend rather than replace: the photo adds real wear and markings, the material keeps
                # each part's identity (steel vs wood vs polymer) so parts stay visually separated.
                photo.putalpha(photo.getchannel('A').point(lambda v: v * PHOTO_MIX // 255))
                region.alpha_composite(photo)
                self._detail(region, mat, wu, hu, rng, pap)
            else:
                if not stretch:
                    self._detail(region, mat, wu, hu, rng, pap)
                tone = photo_tone.get(idx)
                if tone:
                    region = _match_tone(region, tone)
            img.paste(region, pos[(idx, f)])
        uv = {}
        k = 16 / size
        for idx, f, mat, pw, ph, *_ in faces:
            x, y = pos[(idx, f)]
            uv[(idx, f)] = [round(x * k, 5), round(y * k, 5), round((x + pw) * k, 5), round((y + ph) * k, 5)]
        return img, uv, size

    def _detail(self, region, mat, wu, hu, rng, pap):
        """Bevelled edges, chipped paint, scratches, panel lines and screws for one face region."""
        px = region.load()
        w, h = region.size

        def shade(x, y, k):
            r, g, b, a = px[x, y]
            px[x, y] = (_clamp(r * k), _clamp(g * k), _clamp(b * k), a)

        dark = sum(px[w // 2, h // 2][:3]) < 200
        for y in range(h):
            k = 1.12 - 0.24 * (y / max(1, h - 1))
            for x in range(w):
                shade(x, y, k)
        for x in range(w):
            shade(x, 0, 1.7 if dark else 1.32)
            shade(x, h - 1, 0.52)
            if h > 6:
                shade(x, h - 2, 0.8)
        for y in range(h):
            shade(0, y, 1.18)
            shade(w - 1, y, 0.62)
        if (mat in self.WORN or mat in self.METALS) and w > 3 and h > 3:
            chip = (200, 196, 188) if mat in self.WORN and not pap else None
            for x in range(w):
                for y in (0, 1):
                    if rng.random() < (0.12 if y == 0 else 0.05):
                        if chip:
                            px[x, y] = chip + (255,)
                        else:
                            shade(x, y, 1.45)
            for _ in range(int(w * h / 260)):
                x, y = rng.randrange(w), rng.randrange(h)
                length = rng.randint(2, 5)
                for i in range(length):
                    if 0 <= x + i < w and 0 <= y + i // 2 < h:
                        shade(x + i, y + i // 2, 1.2)
        if mat in self.METALS | {'polymer_dark', 'polymer_olive', 'polymer_tan'} and wu >= 3 and hu >= 2.2:
            inset = 3
            for x in range(inset, w - inset):
                shade(x, inset, 0.82)
                shade(x, h - inset - 1, 1.12)
            for y in range(inset, h - inset):
                shade(inset, y, 0.82)
                shade(w - inset - 1, y, 1.12)
            for cx, cy in ((inset + 3, inset + 3), (w - inset - 5, inset + 3), (inset + 3, h - inset - 5), (w - inset - 5, h - inset - 5)):
                if 0 <= cx < w - 1 and 0 <= cy < h - 1:
                    for dx, dy, k in ((0, 0, 0.55), (1, 0, 0.7), (0, 1, 0.7), (1, 1, 1.4)):
                        shade(cx + dx, cy + dy, k)

    def model_json(self, texture_ref, display=None, uv=None, size=None):
        self._fit_offset()
        if uv is None:
            _, uv, size = self._paint()
        out = []
        for idx, e in enumerate(self.elements):
            faces = {f: {'uv': uv[(idx, f)], 'texture': '#0'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}
            shift = lambda p: [round(p[i] + self.offset[i], 4) for i in range(3)]
            el = {'name': e['name'], 'from': shift(e['from']), 'to': shift(e['to']), 'faces': faces}
            if 'rotation' in e:
                el['rotation'] = dict(e['rotation'], origin=shift(e['rotation']['origin']))
            out.append(el)
        self._check_bounds(out)
        return {'credit': 'Made with Blockbench', 'texture_size': [size, size],
                'textures': {'0': texture_ref, 'particle': texture_ref}, 'elements': out,
                'display': display or DISPLAY}

    @staticmethod
    def _check_bounds(elements):
        for e in elements:
            for v in e['from'] + e['to']:
                assert -16 <= v <= 32, ('element outside [-16, 32]', e['name'], e['from'], e['to'])

    def auto_detail(self):
        """Generic small-part pass keyed on part names: engraved side plates and pins on receivers,
        base plates and ribs on magazines, grip ribs, sling swivels on stocks. Added parts share the
        source element's rotation so they stay seated on rotated parts."""
        light = {'steel', 'chrome', 'worn_steel', 'polymer_tan', 'brass'}
        added = []
        for e in list(self.elements):
            name = e['name'] or ''
            f, t = e['from'], e['to']
            sx, sy, sz = t[0] - f[0], t[1] - f[1], t[2] - f[2]
            rot = e.get('rotation')

            def add(frm, to, mat, nm):
                if any(b - a <= 0.01 for a, b in zip(frm, to)):
                    return
                el = {'name': nm, 'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to], 'mat': mat, 'faces': None}
                if rot:
                    el['rotation'] = rot
                self._mat_index(mat)
                added.append(el)

            if any(k in name for k in ('receiver', 'upper', 'body', 'frame', 'slide', 'housing', 'shell', 'casing')) \
                    and 'tube' not in name and sz >= 3 and sy >= 1.2 and sx >= 0.9:
                mat = 'engraved_steel' if e['mat'] in light else 'engraved_dark'
                y0, y1 = f[1] + sy * 0.3, f[1] + sy * 0.62
                z0, z1 = f[2] + sz * 0.25, f[2] + min(sz * 0.6, sz * 0.25 + 4.5)
                for x0, x1 in ((t[0], t[0] + 0.03), (f[0] - 0.03, f[0])):
                    add((x0, y0, z0), (x1, y1, z1), mat, 'engraving')
                for zz in (f[2] + sz * 0.12, f[2] + sz * 0.88):
                    add((f[0] - 0.05, f[1] + sy * 0.18 - 0.14, zz - 0.14), (t[0] + 0.05, f[1] + sy * 0.18 + 0.14, zz + 0.14), 'steel', 'pin')
            elif ('magazine' in name or name.startswith('mag')) and 'tube' not in name and 'well' not in name and sy >= 1.5:
                add((f[0] - 0.05, f[1] - 0.12, f[2] - 0.08), (t[0] + 0.05, f[1] + 0.18, t[2] + 0.08), 'gunmetal', 'mag_baseplate')
                for k in (0.35, 0.62):
                    yy = f[1] + sy * k
                    add((f[0] - 0.02, yy, f[2] + 0.15), (t[0] + 0.02, yy + 0.14, t[2] - 0.15), 'darkmetal', 'mag_rib')
            elif name == 'grip' or name.endswith('_grip') and 'fore' not in name:
                if e['mat'].startswith('wood') or e['mat'] in ('grip_ribs_black', 'grip_ribs_dark', 'wood_ribbed', 'wood_ribbed_dark', 'grip_black'):
                    continue
                for k in (0.3, 0.5, 0.7):
                    yy = f[1] + sy * k
                    add((f[0] - 0.03, yy, f[2] + 0.1), (t[0] + 0.03, yy + 0.18, t[2] - 0.1), 'rubber', 'grip_rib')
            elif any(k in name for k in ('stock_butt', 'butt', 'stock_body')) and name not in ('butt_pad', 'butt_plate') and sz >= 2.5:
                zz = f[2] + sz * 0.3
                add((f[0] + sx / 2 - 0.12, f[1] - 0.45, zz - 0.35), (f[0] + sx / 2 + 0.12, f[1] + 0.02, zz + 0.35), 'steel', 'sling_swivel')
        self.elements.extend(added)

    def use_photo(self, path, flip=None):
        """Texture the side faces from a reference side view (see tools/photo_tex.py)."""
        self._photo_args = (path, flip)

    def save(self, display=None, pap=True):
        if not getattr(self, '_photo_args', None):
            refs_file = os.path.join(ROOT, 'tools/guns/photo_refs.json')
            refs = json.load(open(refs_file)) if os.path.exists(refs_file) else {}
            key = self.name.replace('_v3', '')
            rel = refs.get(key) if key in refs.get('_photo_texture', []) else None
            # An entry is a path, or [path, flip] when the automatic orientation guess must be overridden.
            rel, flip = rel if isinstance(rel, list) else (rel, None)
            path = os.path.join(os.environ.get('COMZ_REFS', '/tmp/comz-refs'), rel) if rel else None
            if path and os.path.exists(path):
                self._photo_args = (path, flip)
        if getattr(self, '_photo_args', None):
            from photo_tex import PhotoRef
            self.photo = PhotoRef(self._photo_args[0], self.elements, self._photo_args[1])
        self.auto_detail()
        for e in self.elements:
            for f in (e['faces'] or {}).values():
                self._mat_index(f)
        self._fit_offset()
        img, uv, size = self._paint()
        texref = 'custom/custom/' + self.name
        model_path = os.path.join(PACK, 'models/custom/item/3d_guns', self.name + '.json')
        tex_path = os.path.join(PACK, 'textures/custom/custom', self.name + '.png')
        model = self.model_json(texref, display, uv, size)
        json.dump(model, open(model_path, 'w'), indent=1)
        img.save(tex_path)
        print('wrote', os.path.relpath(model_path, ROOT), len(self.elements), 'elements,', len(self.used), 'materials,', size, 'px')
        if pap:
            self._save_pap()
        return model_path

    def _save_pap(self):
        """Same geometry, Pack-a-Punch camo texture; the item def gun/<name>_pap points here."""
        name = self.name + '_pap'
        img, _, _ = self._paint(pap=True)
        img.save(os.path.join(PACK, 'textures/custom/custom', name + '.png'))
        model = {'parent': 'minecraft:custom/item/3d_guns/' + self.name,
                 'textures': {'0': 'custom/custom/' + name, 'particle': 'custom/custom/' + name}}
        json.dump(model, open(os.path.join(PACK, 'models/custom/item/3d_guns', name + '.json'), 'w'), indent=1)
        item_def = os.path.join(os.path.dirname(PACK), 'comz/items/gun', name + '.json')
        if os.path.exists(item_def):
            json.dump({'model': {'type': 'minecraft:model', 'model': 'minecraft:custom/item/3d_guns/' + name}}, open(item_def, 'w'), indent=2)
        print('wrote', name)
