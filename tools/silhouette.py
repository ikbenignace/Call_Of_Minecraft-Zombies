#!/usr/bin/env python3
"""
Compare a model's side silhouette with its reference side view.

    python3 tools/silhouette.py b23r_v3 [more keys...]

Writes /tmp/sil/<key>.png: grey = both, red = model sticks out, blue = reference part the model misses,
with a 1-unit grid (z left to right, y bottom to top) so fixes can be read off as model coordinates.
Prints the IoU per model. References come from tools/guns/photo_refs.json (COMZ_REFS root).
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

from photo_tex import PhotoRef, side_polygon

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, 'resourcepack/comz-pack/assets/minecraft/models/custom/item/3d_guns')
OUT = '/tmp/sil'


def elements(key):
    d = json.load(open(os.path.join(MODELS, key + '.json')))
    while 'elements' not in d:
        d = json.load(open(os.path.join(MODELS, d['parent'].split('/')[-1] + '.json')))
    return d['elements']


def compare(key, refs_root, refs):
    rel = refs.get(key.replace('_v3', ''))
    if not rel:
        print('%s: no reference' % key)
        return
    rel, flip = rel if isinstance(rel, list) else (rel, None)
    els = elements(key)
    ref = PhotoRef(os.path.join(refs_root, rel), els, flip)
    w, h = ref.img.size
    scale = max(1, 900 // w)
    W, H = w * scale, h * scale
    model = Image.new('L', (W, H))
    draw = ImageDraw.Draw(model)
    for e in els:
        if e['to'][0] - e['from'][0] < 0.05:
            continue
        poly = []
        for z, y in side_polygon(e):
            px, py = ref._px(z, y, ref.flip)
            poly.append((px * scale, py * scale))
        draw.polygon(poly, fill=255)
    rmask = ref.mask.resize((W, H))
    mp, rp = model.load(), rmask.load()
    out = Image.new('RGB', (W, H), (20, 20, 24))
    op = out.load()
    inter = union = 0
    for y in range(H):
        for x in range(W):
            a, b = mp[x, y] > 0, rp[x, y] > 0
            inter += a and b
            union += a or b
            if a and b:
                op[x, y] = (150, 150, 150)
            elif a:
                op[x, y] = (230, 60, 60)
            elif b:
                op[x, y] = (70, 110, 240)
    g = ImageDraw.Draw(out)
    zs = range(math.ceil(ref.zmin), math.floor(ref.zmax) + 1)
    ys = range(math.ceil(ref.ymin), math.floor(ref.ymax) + 1)
    for z in zs:
        px = ref._px(z, ref.ymin, ref.flip)[0] * scale
        g.line([(px, 0), (px, H)], fill=(60, 60, 70) if z % 5 else (110, 110, 60))
        if z % 2 == 0:
            g.text((px + 2, 2), str(z), fill=(220, 220, 120))
    for yv in ys:
        py = ref._px(ref.zmin, yv, ref.flip)[1] * scale
        g.line([(0, py), (W, py)], fill=(60, 60, 70) if yv % 5 else (110, 110, 60))
        g.text((2, py + 1), str(yv), fill=(220, 220, 120))
    os.makedirs(OUT, exist_ok=True)
    out.save(os.path.join(OUT, key + '.png'))
    print('%s: IoU %.2f' % (key, inter / max(1, union)))


if __name__ == '__main__':
    refs = json.load(open(os.path.join(ROOT, 'tools/guns/photo_refs.json')))
    root = os.environ.get('COMZ_REFS', '/tmp/comz-refs')
    for k in sys.argv[1:]:
        if k.startswith('_'):
            continue
        compare(k, root, refs)
