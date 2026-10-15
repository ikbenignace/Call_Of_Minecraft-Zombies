#!/usr/bin/env python3
"""Second model states derived from the base machine models, so the game can swap them at runtime:
power_switch_on (lever flipped, lamps swapped) and mystery_box_open (lid standing open at the back)."""
import copy
import json
import os
import sys

PACK = os.path.join(os.path.dirname(__file__), '..', '..', 'resourcepack', 'comz-pack', 'assets')
MODELS = os.path.join(PACK, 'minecraft', 'models', 'custom', 'item', 'machines')
ITEMS = os.path.join(PACK, 'comz', 'items', 'machine')

LID_PARTS = ('lid_frame', 'lid_top', 'strap_top', 'hasp', 'latch_plate', 'chain', 'padlock', 'padlock_shackle')
# Element rotations stop at 45 degrees, which leaves the lid over the box where the weapon rises, so the
# open lid is rebuilt standing upright: each part turned a quarter about the hinge at the lid's top back edge.
HINGE_Y, HINGE_Z = 13.0, 15.6
# A quarter turn sends each face's normal (x, y, z) to (x, -z, y).
OPEN_FACES = {'up': 'south', 'north': 'up', 'down': 'north', 'south': 'down', 'east': 'east', 'west': 'west'}


def load(name):
    with open(os.path.join(MODELS, name + '.json')) as f:
        return json.load(f)


def write(name, model):
    with open(os.path.join(MODELS, name + '.json'), 'w') as f:
        json.dump(model, f, indent=2)
        f.write('\n')
    with open(os.path.join(ITEMS, name + '.json'), 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'minecraft:custom/item/machines/' + name}}, f, indent=2)
        f.write('\n')


def power_switch_on():
    model = copy.deepcopy(load('power_switch'))
    lamps = []
    for el in model['elements']:
        name = el.get('name', '')
        if name.startswith('lever'):
            el['rotation']['angle'] = -el['rotation']['angle']
        elif name == 'lamp':
            lamps.append(el)
    if len(lamps) == 2:
        for i in (0, 2):
            lamps[0]['from'][i], lamps[1]['from'][i] = lamps[1]['from'][i], lamps[0]['from'][i]
            lamps[0]['to'][i], lamps[1]['to'][i] = lamps[1]['to'][i], lamps[0]['to'][i]
    write('power_switch_on', model)


def stand_up(el):
    (x0, y0, z0), (x1, y1, z1) = el['from'], el['to']
    ys = sorted((HINGE_Y - (z - HINGE_Z) for z in (z0, z1)))
    zs = sorted((HINGE_Z + (y - HINGE_Y) for y in (y0, y1)))
    el['from'] = [x0, round(ys[0], 4), round(zs[0], 4)]
    el['to'] = [x1, round(ys[1], 4), round(zs[1], 4)]
    faces = {}
    for name, face in el['faces'].items():
        face = dict(face)
        if name in ('east', 'west'):
            face['rotation'] = (face.get('rotation', 0) + 90) % 360
        faces[OPEN_FACES[name]] = face
    el['faces'] = faces
    el.pop('rotation', None)


def mystery_box_open():
    model = copy.deepcopy(load('mystery_box'))
    for el in model['elements']:
        if el.get('name') in LID_PARTS:
            stand_up(el)
    write('mystery_box_open', model)


if __name__ == '__main__':
    power_switch_on()
    mystery_box_open()
