#!/usr/bin/env python3
"""Second model states derived from the base machine models, so the game can swap them at runtime:
power_switch_on (lever flipped, lamps swapped) and mystery_box_open (lid hinged open at the back)."""
import copy
import json
import os
import sys

PACK = os.path.join(os.path.dirname(__file__), '..', '..', 'resourcepack', 'comz-pack', 'assets')
MODELS = os.path.join(PACK, 'minecraft', 'models', 'custom', 'item', 'machines')
ITEMS = os.path.join(PACK, 'comz', 'items', 'machine')

LID_PARTS = ('lid_frame', 'lid_top', 'strap_top', 'hasp', 'latch_plate', 'chain', 'padlock', 'padlock_shackle')
LID_ANGLE = float(os.environ.get('LID_ANGLE', 45))


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


def mystery_box_open():
    model = copy.deepcopy(load('mystery_box'))
    for el in model['elements']:
        if el.get('name') in LID_PARTS:
            el['rotation'] = {'angle': LID_ANGLE, 'axis': 'x', 'origin': [8, 10.5, 15.6]}
    write('mystery_box_open', model)


if __name__ == '__main__':
    power_switch_on()
    mystery_box_open()
