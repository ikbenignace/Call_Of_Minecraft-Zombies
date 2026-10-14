#!/usr/bin/env python3
"""
Give chosen elements of an existing model a gunkit material (wood, brass, rubber, ...) through an extra
texture, keeping everything else on the model's own (optionally repainted) texture.

    from retex import Retexture
    rt = Retexture('fal')                     # loads 3d_guns/fal.json
    rt.assign(rt.group('stock'), 'wood_light')
    rt.save('fal_v3', base_rules=RULES)       # writes 3d_guns/fal_v3(.json/_pap) + textures
"""
import json
import os
import random

from gunkit import MATERIALS, PACK, TILE, Gun, pap_camo
from repaint import repaint
from bbconvert import camo

MODELS = os.path.join(PACK, 'models/custom/item/3d_guns')
TEXTURES = os.path.join(PACK, 'textures/custom/custom')
DIMS = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


class Retexture:
    def __init__(self, key):
        self.key = key
        self.model = json.load(open(os.path.join(MODELS, key + '.json')))
        self.elements = self.model['elements']
        self.assigned = {}

    def group(self, name):
        """Element indices of a Blockbench group (searched recursively)."""
        out = []

        def walk(children, inside):
            for c in children:
                if isinstance(c, dict):
                    walk(c.get('children', []), inside or c.get('name') == name)
                elif inside:
                    out.append(c)
        walk(self.model.get('groups', []), False)
        return out

    def where(self, pred, among=None):
        idx = range(len(self.elements)) if among is None else among
        return [i for i in idx if pred(self.elements[i])]

    def assign(self, indices, material):
        for i in indices:
            self.assigned[i] = material

    def save(self, out_key, base_rules=None, base_default='keep', display=None):
        src_ref = next(v for k, v in self.model['textures'].items() if k != 'particle')
        src_png = os.path.join(TEXTURES, src_ref.split('/')[-1] + '.png')
        base_png = os.path.join(TEXTURES, out_key + '.png')
        base_img = repaint(src_png, base_png, base_rules or [], default=base_default)

        g = Gun(out_key + '_mat')
        for m in sorted(set(self.assigned.values())):
            g._mat_index(m)
        atlas, origins = g._atlas()
        atlas.save(os.path.join(TEXTURES, out_key + '_mat.png'))

        model = json.loads(json.dumps(self.model))
        base_keys = [k for k in model['textures'] if k != 'particle']
        model['textures'] = {k: 'custom/custom/' + out_key for k in base_keys}
        model['textures']['mat'] = 'custom/custom/%s_mat' % out_key
        model['textures']['particle'] = 'custom/custom/' + out_key
        for i, mat in self.assigned.items():
            e = model['elements'][i]
            size = [e['to'][k] - e['from'][k] for k in range(3)]
            for f, face in e['faces'].items():
                a, b = DIMS[f]
                face['uv'] = g._face_uv(mat, origins[mat], size[a], size[b], i * 7 + len(f))
                face['texture'] = '#mat'
                face.pop('rotation', None)
        if display:
            model['display'] = display
        json.dump(model, open(os.path.join(MODELS, out_key + '.json'), 'w'), indent=1)

        camo(base_img).save(os.path.join(TEXTURES, out_key + '_pap.png'))
        pap_atlas, _ = g._atlas(pap=True)
        pap_atlas.save(os.path.join(TEXTURES, out_key + '_pap_mat.png'))
        pap = {'parent': 'minecraft:custom/item/3d_guns/' + out_key,
               'textures': dict({k: 'custom/custom/%s_pap' % out_key for k in base_keys},
                                mat='custom/custom/%s_pap_mat' % out_key, particle='custom/custom/%s_pap' % out_key)}
        json.dump(pap, open(os.path.join(MODELS, out_key + '_pap.json'), 'w'), indent=1)
        print('wrote %s (+pap): %d elements retextured' % (out_key, len(self.assigned)))
