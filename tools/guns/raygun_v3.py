#!/usr/bin/env python3
"""Ray Gun v3: the approved raygun_v2 silhouette (one red shell from dial through grip, trigger
guard sweep, blue tube showing above the red frame, wire loops) with a "real" finish and the
structure of the BO3/BO4 Ray Gun:

- v2's 64px texture repainted at 4x: worn oxblood paint, chrome, gunmetal, glowing plasma, gold;
- a raised Blast-O-Matic gauge on each side of the dial (green at the back, as on the real gun);
- stepped chrome louvres, gold antenna loop with star, cone rivets.

Muzzle towards -Z, like v2.
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun, PACK

MODELS = os.path.join(PACK, 'models/custom/item/3d_guns')
TEXTURES = os.path.join(PACK, 'textures/custom/custom')
SCALE = 4


def classify(c):
    r, g, b = c[:3]
    if b > 150 and r < 120:
        return 'blue'
    if g > 90 and r < 90:
        return 'green'
    if r > 110 and g > 90 and b < 60:
        return 'yellow'
    if r > 60 and g < 45 and b < 45:
        return 'red'
    if abs(r - g) < 20 and abs(g - b) < 20:
        return 'gray' if r > 70 else 'dark'
    return 'other'


def repaint_v2():
    src = Image.open(os.path.join(TEXTURES, 'raygun_v2.png')).convert('RGB')
    w, h = src.size
    out = Image.new('RGBA', (w * SCALE, h * SCALE))
    px = out.load()
    rng = random.Random(3)
    for y in range(h * SCALE):
        for x in range(w * SCALE):
            c = src.getpixel((x // SCALE, y // SCALE))
            kind = classify(c)
            # The round switch icon (top-left corner of the sheet) keeps its green light; the green bar
            # next to it is shared with the antenna, so that becomes gold.
            in_indicator = x // SCALE < 8 and y // SCALE < 8
            n = rng.uniform(-0.05, 0.05)
            if kind == 'red':
                shade = c[0] / 112.0
                base = (150, 28, 26)
                col = [base[i] * shade * (1 + n) for i in range(3)]
                if rng.random() < 0.006:
                    col = [176, 96, 88]
            elif kind == 'gray':
                t = c[0] / 128.0
                band = 1.12 if (y % SCALE) == 0 else 1.0
                col = [175 * t * band * (1 + n), 180 * t * band * (1 + n), 188 * t * band * (1 + n)]
            elif kind == 'dark':
                col = [40 * (1 + n), 41 * (1 + n), 44 * (1 + n)]
            elif kind == 'blue':
                v = math.sin(x * 0.35 + 3 * math.sin(y * 0.21)) * math.sin(y * 0.4 + x * 0.05)
                glow = max(0.0, v) ** 2
                col = [90 + 140 * glow, 190 + 60 * glow, 255]
            elif kind in ('green', 'yellow') and not in_indicator:
                t = (c[1] / 120.0) if kind == 'green' else (c[0] / 150.0)
                col = [214 * t * (1 + n), 168 * t * (1 + n), 66 * t * (1 + n)]
            elif kind == 'green' and in_indicator:
                col = [80, 230, 90]
            else:
                col = list(c)
            px[x, y] = tuple(max(0, min(255, int(v))) for v in col) + (255,)
    return out


def gauge_texture(size=128, mirrored=False):
    """Blast-O-Matic face: green->yellow->orange arc band with ticks on the upper half, radial red
    slats on the lower half, chrome hub and needle. Transparent outside the disc."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    d.ellipse((1, 1, size - 2, size - 2), fill=(120, 22, 22, 255))
    d.ellipse((5, 5, size - 6, size - 6), fill=(158, 32, 28, 255))
    # Arc band: green at the rear (left on the east face), orange towards the muzzle.
    stops = [(180, 225, (70, 205, 80)), (225, 270, (150, 220, 70)), (270, 315, (236, 214, 70)), (315, 360, (236, 120, 40))]
    for a0, a1, col in stops:
        if mirrored:
            a0, a1 = 540 - a1, 540 - a0
        d.pieslice((9, 9, size - 10, size - 10), a0, a1, fill=col + (255,))
    d.pieslice((30, 30, size - 31, size - 31), 180, 360, fill=(158, 32, 28, 255))
    for a in range(180, 361, 6):
        r0 = c - 10 if a % 30 == 0 else c - 16
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        d.line((c + r0 * ca, c + r0 * sa, c + (c - 23) * ca, c + (c - 23) * sa), fill=(60, 30, 20, 255), width=1)
    # Lower half: radial slats like the real dial's cooling fins.
    for a in range(10, 171, 20):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        d.line((c + 18 * ca, c + 18 * sa, c + (c - 8) * ca, c + (c - 8) * sa), fill=(96, 16, 16, 255), width=3)
        d.line((c + 18 * ca + 2, c + 18 * sa, c + (c - 8) * ca + 2, c + (c - 8) * sa), fill=(190, 70, 60, 255), width=1)
    # Script plate, needle, chrome hub.
    d.line((c - 22, c - 14, c + 22, c - 14), fill=(214, 168, 66, 255), width=2)
    needle_a = math.radians(300 if not mirrored else 240)
    d.line((c, c, c + (c - 14) * math.cos(needle_a), c + (c - 14) * math.sin(needle_a)), fill=(30, 26, 24, 255), width=3)
    d.ellipse((c - 9, c - 9, c + 9, c + 9), fill=(150, 154, 160, 255))
    d.ellipse((c - 5, c - 5, c + 5, c + 5), fill=(210, 214, 220, 255))
    d.ellipse((c - 2, c - 2, c + 2, c + 2), fill=(70, 70, 75, 255))
    return img


def parts():
    """Extra structure in gunkit materials (texture #2)."""
    g = Gun('raygun_v3_parts')
    # Stepped chrome louvres on both sides of the frame under the tube.
    for side in (-1, 1):
        x0 = 12.25 if side > 0 else 3.35
        for i, (y, z0, z1) in enumerate([(7.0, 9.6, 16.6), (6.15, 10.2, 16.3), (5.3, 10.8, 16.0)]):
            g.box((x0, y, z0), (x0 + 0.4, y + 0.45, z1), 'chrome', name='louvre')
            g.box((x0 + (0.4 if side > 0 else -0.08), y + 0.05, z0 + 0.3), (x0 + (0.48 if side > 0 else 0), y + 0.4, z1 - 0.3), 'darkmetal', name='louvre_mesh')
    # Antenna loop with a star, on top of v2's antenna (z -7.7, top y 16.5).
    g.ring('x', (17.25, -7.7), 0.5, 0.75, 7.9, 8.1, 'gold', name='antenna_loop')
    g.box((7.95, 16.85, -7.75), (8.05, 17.65, -7.65), 'chrome', name='antenna_star')
    g.box((7.95, 17.2, -8.1), (8.05, 17.3, -7.3), 'chrome', name='antenna_star')
    # Gold rivets around the emitter cone (centre y 8, z -8.3..-12.25).
    for z in (-9.6, -10.8):
        for y in (6.7, 8.0, 9.3):
            for x0 in (10.5, 5.38):
                g.box((x0, y - 0.18, z - 0.18), (x0 + 0.12, y + 0.18, z + 0.18), 'gold', name='cone_rivet')
    return g


def main():
    v2 = json.load(open(os.path.join(MODELS, 'raygun_v2.json')))
    elements = []
    for e in v2['elements']:
        e = json.loads(json.dumps(e))
        for f in e['faces'].values():
            f['texture'] = '#1'
        elements.append(e)

    g = parts()
    parts_img, parts_uv, parts_size = g._paint()
    extra = g.model_json('custom/custom/raygun_v3_parts', None, parts_uv, parts_size)['elements']
    for e in extra:
        for f in e['faces'].values():
            f['texture'] = '#2'
    elements += extra

    # Raised gauges: one square face each side; the disc shape comes from the texture's alpha.
    cz, cy, r = 18.2, 9.4, 3.9
    for x0, x1, face, tex in ((11.5, 11.62, 'east', '#3'), (4.38, 4.5, 'west', '#4')):
        faces = {f: {'uv': [0, 0, 0.1, 0.1], 'texture': '#1'} for f in ('north', 'south', 'up', 'down', 'east', 'west')}
        faces[face] = {'uv': [0, 0, 16, 16], 'texture': tex}
        elements.append({'name': 'gauge', 'from': [x0, cy - r, cz - r], 'to': [x1, cy + r, cz + r], 'faces': faces})

    model = {'credit': 'Made with Blockbench',
             'textures': {'1': 'custom/custom/raygun_v3', '2': 'custom/custom/raygun_v3_parts',
                          '3': 'custom/custom/raygun_v3_gauge', '4': 'custom/custom/raygun_v3_gauge_l',
                          'particle': 'custom/custom/raygun_v3'},
             'elements': elements, 'display': v2['display']}
    json.dump(model, open(os.path.join(MODELS, 'raygun_v3.json'), 'w'), indent=1)
    repaint_v2().save(os.path.join(TEXTURES, 'raygun_v3.png'))
    parts_img.save(os.path.join(TEXTURES, 'raygun_v3_parts.png'))
    gauge_texture().save(os.path.join(TEXTURES, 'raygun_v3_gauge.png'))
    gauge_texture(mirrored=True).save(os.path.join(TEXTURES, 'raygun_v3_gauge_l.png'))
    print('wrote raygun_v3:', len(elements), 'elements')


if __name__ == '__main__':
    main()
