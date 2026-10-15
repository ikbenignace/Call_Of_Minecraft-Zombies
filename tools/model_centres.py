#!/usr/bin/env python3
"""
Writes Core/src/main/resources/model_centres.json: for every comz item model, the centre of its geometry
as an item display draws it (no display transform, turned 180 degrees about Y by the renderer), in blocks
from the display's origin at scale 1, followed by its longest side in blocks. The plugin subtracts the
centre to put a model's visual centre exactly where it wants, e.g. a gun in the middle of the mystery box
or on its wall outline, and uses the length to draw wall buys about life size.

build_pack.py runs this; run it by hand after changing models without building the pack.
"""
import json
import math
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'resourcepack', 'comz-pack', 'assets')
OUT = os.path.join(ROOT, 'Core', 'src', 'main', 'resources', 'model_centres.json')


def model_path(ref):
    ns, _, path = ref.partition(':') if ':' in ref else ('minecraft', ':', ref)
    return os.path.join(ASSETS, ns, 'models', path + '.json')


def elements(ref, depth=0):
    path = model_path(ref)
    if depth > 8 or not os.path.exists(path):
        return None
    with open(path) as f:
        model = json.load(f)
    if 'elements' in model:
        return model['elements']
    return elements(model['parent'], depth + 1) if 'parent' in model else None


def corners(element):
    (x0, y0, z0), (x1, y1, z1) = element['from'], element['to']
    points = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    rot = element.get('rotation')
    if not rot or not rot.get('angle'):
        return points
    a = math.radians(rot['angle'])
    ox, oy, oz = rot.get('origin', (8, 8, 8))
    c, s = math.cos(a), math.sin(a)
    out = []
    for x, y, z in points:
        x, y, z = x - ox, y - oy, z - oz
        if rot['axis'] == 'x':
            y, z = y * c - z * s, y * s + z * c
        elif rot['axis'] == 'y':
            x, z = x * c + z * s, -x * s + z * c
        else:
            x, y = x * c - y * s, x * s + y * c
        out.append((x + ox, y + oy, z + oz))
    return out


def centre(els):
    pts = [p for e in els for p in corners(e)]
    mid = [(min(p[i] for p in pts) + max(p[i] for p in pts)) / 2 for i in range(3)]
    # Pixels to blocks around the model's (8, 8, 8), then the renderer's half turn about Y.
    x, y, z = ((v - 8) / 16 for v in mid)
    longest = max(max(p[i] for p in pts) - min(p[i] for p in pts) for i in range(3)) / 16
    return [round(-x, 4), round(y, 4), round(-z, 4), round(longest, 4)]


def item_model_ref(item_def):
    model = item_def.get('model', {})
    return model.get('model') if model.get('type') == 'minecraft:model' else None


def build():
    table = {}
    items = os.path.join(ASSETS, 'comz', 'items')
    for root, _dirs, names in os.walk(items):
        for name in sorted(names):
            if not name.endswith('.json'):
                continue
            with open(os.path.join(root, name)) as f:
                ref = item_model_ref(json.load(f))
            els = elements(ref) if ref else None
            if els:
                key = os.path.relpath(os.path.join(root, name[:-5]), items).replace(os.sep, '/')
                table['comz:' + key] = centre(els)
    with open(OUT, 'w') as f:
        json.dump(dict(sorted(table.items())), f, indent=1)
        f.write('\n')
    print(f"model centres: {len(table)} -> {os.path.relpath(OUT, ROOT)}")


if __name__ == '__main__':
    build()
