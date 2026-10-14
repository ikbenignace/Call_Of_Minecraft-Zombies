#!/usr/bin/env python3
"""
Convert a Blockbench project (any format, cubes only) into a COM:Z Java item model.

Input is the cube dump produced in Blockbench by the MCP `risky_eval` extraction (each cube's world
matrix, which already includes every nested group rotation) plus the .bbmodel file for the texture
images. Every cube becomes one element rotated about its own centre on a single axis:

- angles beyond ±45° are folded back by 90° steps (swapping the cube's cross-section and remapping
  its faces), so no element ever needs more than ±45°;
- the base model snaps angles to the 22.5° grid every 1.21.4+ client accepts;
- an overlay copy keeps the exact angles, which clients from 1.21.6 (25w16a) render.

Usage: bbconvert.py <dump.json> <file.bbmodel> <key> [--flip] [--length L] [--exclude a,b] [--textures 0,2]
"""
import argparse
import base64
import io
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, 'resourcepack/comz-pack')
EXACT_OVERLAY = 'overlay_exact_angles'
GRID = (-45, -22.5, 0, 22.5, 45)
FACES = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
# Texture "up" direction of each face in the element's own frame (Minecraft UV convention).
FACE_UP = {'north': (0, 1, 0), 'south': (0, 1, 0), 'east': (0, 1, 0), 'west': (0, 1, 0), 'up': (0, 0, -1), 'down': (0, 0, 1)}
CENTER = (8.0, 5.5, 5.0)

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.7, 0.7, 0.7]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.7, 0.7, 0.7]},
    "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}


REFERENCE_LENGTH = 29.0


def scaled_display(length):
    """The base display block was tuned on the 29-unit MP5; longer guns shrink to the same on-screen size."""
    k = max(0.55, min(1.3, REFERENCE_LENGTH / length))
    out = json.loads(json.dumps(DISPLAY))
    for slot in ('thirdperson_righthand', 'thirdperson_lefthand', 'firstperson_righthand', 'firstperson_lefthand', 'gui', 'ground'):
        out[slot]['scale'] = [round(v * k, 3) for v in out[slot]['scale']]
    return out


def camo(img):
    """Pack-a-Punch variant of an existing texture: BO2 camo, shaded by the original's luminance."""
    import random
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from gunkit import pap_camo, TILE
    tile = pap_camo(random.Random(sum(img.size)))
    out = img.copy()
    src, dst, tp = img.load(), out.load(), tile.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
            cr, cg, cb, _ = tp[x % TILE, y % TILE]
            k = 0.55 + 1.1 * lum
            dst[x, y] = (min(255, int(cr * k)), min(255, int(cg * k)), min(255, int(cb * k)), a)
    return out


def mat3(e):
    """THREE.js column-major 4x4 -> (3x3 rotation rows, translation)."""
    return [[e[0], e[4], e[8]], [e[1], e[5], e[9]], [e[2], e[6], e[10]]], [e[12], e[13], e[14]]


def mul(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def rot_axis(axis, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return {'x': [[1, 0, 0], [0, c, -s], [0, s, c]],
            'y': [[c, 0, s], [0, 1, 0], [-s, 0, c]],
            'z': [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[axis]


def decompose(r):
    """Best single-axis rotation (axis, degrees) for rotation matrix r, plus residual error."""
    best = None
    for axis, idx in (('x', 0), ('y', 1), ('z', 2)):
        if axis == 'x':
            ang = math.degrees(math.atan2(r[2][1], r[1][1]))
        elif axis == 'y':
            ang = math.degrees(math.atan2(r[0][2], r[2][2]))
        else:
            ang = math.degrees(math.atan2(r[1][0], r[0][0]))
        approx = rot_axis(axis, ang)
        err = sum(abs(approx[i][j] - r[i][j]) for i in range(3) for j in range(3))
        if best is None or err < best[2]:
            best = (axis, ang, err)
    return best


def fold(axis, ang):
    """Fold an angle into [-45, 45] by quarter turns; returns (angle, quarter_turns)."""
    k = round(ang / 90.0)
    rest = ang - 90 * k
    if rest > 45:
        rest -= 90
        k += 1
    if rest < -45:
        rest += 90
        k -= 1
    return rest, k


def snap(ang):
    return min(GRID, key=lambda g: abs(g - ang))


def convert(dump, bbfile, key, flip=False, length=None, exclude=(), texture_ids=None):
    data = json.load(open(dump))
    bb = json.load(open(bbfile))
    flip_m = [[-1, 0, 0], [0, 1, 0], [0, 0, -1]] if flip else [[1, 0, 0], [0, 1, 0], [0, 0, 1]]

    parts = []
    for c in data['cubes']:
        segments = [x.lower() for x in c['chain'] + [c['name']]]
        if any(x.lower() in segments for x in exclude):
            continue
        r, t = mat3(c['matrix'])
        local_center = [(c['from'][i] + c['to'][i]) / 2 - c['origin'][i] for i in range(3)]
        size = [abs(c['to'][i] - c['from'][i]) + 2 * c['inflate'] for i in range(3)]
        center = [a + b for a, b in zip(mul(r, local_center), t)]
        parts.append({'cube': c, 'r': matmul(flip_m, r), 'center': mul(flip_m, center), 'size': size})
    if not parts:
        raise SystemExit('nothing left after exclusions')

    # Whole-model framing from the rotated corners.
    lo, hi = [1e9] * 3, [-1e9] * 3
    for p in parts:
        for sx in (-1, 1):
            for sy in (-1, 1):
                for sz in (-1, 1):
                    corner = mul(p['r'], [sx * p['size'][0] / 2, sy * p['size'][1] / 2, sz * p['size'][2] / 2])
                    for i in range(3):
                        v = p['center'][i] + corner[i]
                        lo[i], hi[i] = min(lo[i], v), max(hi[i], v)
    scale = 1.0
    if length:
        scale = length / (hi[2] - lo[2])
    mid = [(lo[i] + hi[i]) / 2 for i in range(3)]

    used = sorted({f['texture'] for p in parts for f in p['cube']['faces'].values() if f['texture'] is not None})
    if texture_ids is not None:
        used = [u for u in used if u in texture_ids]
    tex_key = {u: str(i) for i, u in enumerate(used)}
    textures = data['textures']

    # Re-labelling a box's own axes (any of the 24 rotations that map axes onto axes, with the
    # cross-section permuted to match) never changes its shape, so try them all and keep the one
    # that leaves a single-axis rotation, e.g. a barrel segment turned 90° about x and 45° about z.
    import itertools
    frames = []
    for perm in itertools.permutations(range(3)):
        for signs in itertools.product((1, -1), repeat=3):
            m = [[0] * 3 for _ in range(3)]
            for col, row in enumerate(perm):
                m[row][col] = signs[col]
            det = (m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                   + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]))
            if det == 1:
                frames.append((m, perm))

    def element(p, exact):
        best = None
        for m, perm in frames:
            axis, ang, err = decompose(matmul(p['r'], m))
            folded, quarters = fold(axis, ang)
            score = (round(err, 3), abs(folded))
            if best is None or score < best[0]:
                best = (score, axis, folded, quarters, err, perm)
        _, axis, folded, quarters, err, perm = best
        size = [p['size'][perm[i]] for i in range(3)]
        axi = 'xyz'.index(axis)
        a, b = [i for i in range(3) if i != axi]
        if quarters % 2:
            size[a], size[b] = size[b], size[a]
        angle = round(folded, 3) if exact else snap(folded)
        center = [(p['center'][i] - mid[i]) * scale + CENTER[i] for i in range(3)]
        half = [s * scale / 2 for s in size]
        el = {'name': p['cube']['name'],
              'from': [round(center[i] - half[i], 4) for i in range(3)],
              'to': [round(center[i] + half[i], 4) for i in range(3)]}
        if abs(angle) > 1e-6:
            el['rotation'] = {'angle': angle, 'axis': axis, 'origin': [round(v, 4) for v in center]}
        new_r = rot_axis(axis, folded)
        faces = {}
        for fname, n in FACES.items():
            world_n = mul(new_r, n)
            src = max(FACES, key=lambda f: sum(x * y for x, y in zip(mul(p['r'], FACES[f]), world_n)))
            f = p['cube']['faces'].get(src)
            if not f or f['texture'] is None or f['texture'] not in tex_key:
                continue
            tw = textures[f['texture']]['uv_width'] or 16
            th = textures[f['texture']]['uv_height'] or 16
            uv = [f['uv'][0] * 16 / tw, f['uv'][1] * 16 / th, f['uv'][2] * 16 / tw, f['uv'][3] * 16 / th]
            up_src = mul(p['r'], FACE_UP[src])
            up_new = mul(new_r, FACE_UP[fname])
            cross = [up_new[1] * up_src[2] - up_new[2] * up_src[1], up_new[2] * up_src[0] - up_new[0] * up_src[2],
                     up_new[0] * up_src[1] - up_new[1] * up_src[0]]
            turn = math.degrees(math.atan2(sum(x * y for x, y in zip(cross, world_n)), sum(x * y for x, y in zip(up_new, up_src))))
            rotation = (f['rotation'] + int(round(turn / 90.0)) * 90) % 360
            face = {'uv': [round(v, 4) for v in uv], 'texture': '#' + tex_key[f['texture']]}
            if rotation:
                face['rotation'] = rotation
            faces[fname] = face
        el['faces'] = faces
        for v in el['from'] + el['to']:
            if not -16 <= v <= 32:
                raise SystemExit('element %s outside [-16, 32]; pass a smaller --length' % el['name'])
        return el, err

    texrefs = {tex_key[u]: 'custom/custom/%s_%d' % (key, i) for i, u in enumerate(used)}
    texrefs['particle'] = texrefs['0']
    base_elements, worst = [], 0
    for p in parts:
        el, err = element(p, exact=False)
        base_elements.append(el)
        worst = max(worst, err)
    exact_elements = [element(p, exact=True)[0] for p in parts]
    model = {'credit': 'Made with Blockbench', 'textures': texrefs, 'elements': base_elements,
             'display': scaled_display((hi[2] - lo[2]) * scale)}

    models_dir = 'assets/minecraft/models/custom/item/3d_guns'
    os.makedirs(os.path.join(PACK, models_dir), exist_ok=True)
    json.dump(model, open(os.path.join(PACK, models_dir, key + '.json'), 'w'), indent=1)
    overlay_dir = os.path.join(PACK, EXACT_OVERLAY, models_dir)
    os.makedirs(overlay_dir, exist_ok=True)
    json.dump(dict(model, elements=exact_elements), open(os.path.join(overlay_dir, key + '.json'), 'w'), indent=1)

    tex_dir = os.path.join(PACK, 'assets/minecraft/textures/custom/custom')
    for u in used:
        src = bb['textures'][u].get('source', '')
        if not src.startswith('data:image'):
            raise SystemExit('texture %s is not embedded in the .bbmodel' % textures[u]['name'])
        img = Image.open(io.BytesIO(base64.b64decode(src.split(',', 1)[1]))).convert('RGBA')
        img.save(os.path.join(tex_dir, '%s_%d.png' % (key, used.index(u))))
        camo(img).save(os.path.join(tex_dir, '%s_pap_%d.png' % (key, used.index(u))))

    pap = {'parent': 'minecraft:custom/item/3d_guns/' + key,
           'textures': {k: v.replace('custom/custom/%s_' % key, 'custom/custom/%s_pap_' % key) for k, v in texrefs.items()}}
    json.dump(pap, open(os.path.join(PACK, models_dir, key + '_pap.json'), 'w'), indent=1)
    items_dir = os.path.join(PACK, 'assets/comz/items/gun')
    for name in (key, key + '_pap'):
        json.dump({'model': {'type': 'minecraft:model', 'model': 'minecraft:custom/item/3d_guns/' + name}},
                  open(os.path.join(items_dir, name + '.json'), 'w'), indent=2)

    off_grid = sum(1 for a, b in zip(base_elements, exact_elements) if a.get('rotation', {}).get('angle', 0) != b.get('rotation', {}).get('angle', 0))
    print('%s: %d elements, %d textures, scale %.3f, %d snapped to the 22.5° grid, worst multi-axis residual %.3f'
          % (key, len(base_elements), len(used), scale, off_grid, worst))


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('dump')
    ap.add_argument('bbmodel')
    ap.add_argument('key')
    ap.add_argument('--flip', action='store_true', help='turn 180° so the muzzle points to -Z')
    ap.add_argument('--length', type=float, help='scale so the gun is this long along Z')
    ap.add_argument('--exclude', default='', help='comma-separated group/cube names to drop (whole names)')
    ap.add_argument('--textures', default=None, help='comma-separated texture indices to keep')
    a = ap.parse_args()
    convert(a.dump, a.bbmodel, a.key, a.flip, a.length, [x for x in a.exclude.split(',') if x],
            [int(x) for x in a.textures.split(',')] if a.textures else None)
