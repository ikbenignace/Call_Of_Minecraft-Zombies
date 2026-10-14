#!/usr/bin/env python3
"""Shared helper: copy a 3d_guns model under a new key with its texture repainted by tools/repaint.py,
plus a PaP-camo variant. Used by the per-gun *_v3.py repaint scripts."""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import PACK
from repaint import repaint
from bbconvert import camo

MODELS = os.path.join(PACK, 'models/custom/item/3d_guns')
TEXTURES = os.path.join(PACK, 'textures')


def repaint_model(base, out, rules, default='keep', display=None):
    model = json.load(open(os.path.join(MODELS, base + '.json')))
    refs = {k: v for k, v in model['textures'].items() if k != 'particle'}
    new_refs, pap_refs = {}, {}
    for i, (key, ref) in enumerate(sorted(refs.items())):
        name = out if len(refs) == 1 else '%s_%d' % (out, i)
        src = os.path.join(TEXTURES, ref.split(':')[-1] + '.png')
        img = repaint(src, os.path.join(TEXTURES, 'custom/custom', name + '.png'), rules, default=default)
        camo(img).save(os.path.join(TEXTURES, 'custom/custom', name + '_pap.png'))
        new_refs[key], pap_refs[key] = 'custom/custom/' + name, 'custom/custom/' + name + '_pap'
    model['textures'] = dict(new_refs, particle=next(iter(new_refs.values())))
    if display:
        model['display'] = display
    json.dump(model, open(os.path.join(MODELS, out + '.json'), 'w'), indent=1)
    json.dump({'parent': 'minecraft:custom/item/3d_guns/' + out, 'textures': dict(pap_refs, particle=next(iter(pap_refs.values())))},
              open(os.path.join(MODELS, out + '_pap.json'), 'w'), indent=1)
    print('wrote', out, '(+pap)')
