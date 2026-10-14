#!/usr/bin/env python3
"""Crossbow (BO1): black compound crossbow: rail and barrel, limbs with cam wheels and string, scope on
top, foregrip, pistol grip and skeleton stock; a loaded explosive bolt with its red-lit tip. 34 units."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('crossbow_v3', offset=(0, -1, 2))
C = 8

# Rail, receiver, trough with guide rails
g.box((C - 0.55, 6.0, -15), (C + 0.55, 7.9, -3.5), 'polymer_dark', name='rail_front')
for side in (-1, 1):
    x0, x1 = sorted((C + side * 0.35, C + side * 0.58))
    g.box((x0, 7.9, -15), (x1, 8.25, -3.5), 'polymer_dark', name='guide_rail')
g.box((C - 0.55, 6.0, -4.0), (C + 0.55, 9.0, 7.6), 'darkmetal', name='rail')
g.box((C - 0.6, 4.8, 2.5), (C + 0.6, 9.0, 7.5), 'gunmetal', name='receiver')

# Limbs (riser across the front), cam wheels, string, stirrup
g.box((C - 1.0, 6.2, -14.0), (C + 1.0, 8.2, -12.4), 'gunmetal', name='riser')
for side in (-1, 1):
    x0, x1 = sorted((C + side * 0.8, C + side * 7.5))
    g.box((x0, 7.15, -13.35), (x1, 7.85, -12.85), 'polymer_dark', rot=('y', -22.5 * side, (C + side * 0.8, 7.5, -13.1)), name='limb')
    g.cyl('y', (C + side * 7.0, -10.6), 0.75, 6.9, 8.1, 'darkmetal', seg=8, name='cam_wheel')
    x0, x1 = sorted((C, C + side * 7.4))
    g.box((x0, 7.45, -9.0), (x1, 7.55, -8.9), 'steel', rot=('y', 22.5 * side, (C, 7.5, -8.95)), name='string')
g.box((C - 0.45, 6.9, -9.2), (C + 0.45, 7.9, -8.4), 'steel', name='latch')
for side in (-1, 1):
    x0, x1 = sorted((C + side * 0.35, C + side * 0.6))
    g.box((x0, 4.6, -8.0), (x1, 6.1, -4.0), 'steel', name='stirrup_side')
g.box((C - 0.6, 4.6, -8.0), (C + 0.6, 4.9, -4.0), 'steel', name='stirrup_bottom')

# Explosive bolt lying in the trough
g.cyl('z', (C, 8.1), 0.2, -14.0, -4.2, 'steel', seg=8, name='bolt_shaft')
g.cyl('z', (C, 8.1), 0.3, -15.2, -14.0, 'darkmetal', seg=8, name='bolt_head')
g.box((C - 0.12, 8.0, -15.4), (C + 0.12, 8.2, -15.2), 'bulb', name='bolt_light')

# Scope, trigger group, pistol grip, solid stock
g.cyl('z', (C, 10.0), 0.7, -2.8, 5.0, 'darkmetal', seg=8, name='scope')
g.cyl('z', (C, 10.0), 0.8, -3.2, -2.0, 'darkmetal', seg=8, name='scope_objective')
for z in (-1.0, 3.0):
    g.box((C - 0.3, 8.9, z), (C + 0.3, 9.4, z + 0.8), 'darkmetal', name='scope_mount')
g.box((C - 0.1, 4.2, 3.2), (C + 0.1, 5.0, 3.5), 'steel', name='trigger')
g.box((C - 0.22, 4.0, 3.2), (C + 0.22, 4.3, 5.0), 'darkmetal', name='guard_bottom')
GRIP = ('x', -22.5, (C, 6.8, 5.3))
g.box((C - 0.6, 1.2, 4.4), (C + 0.6, 7.0, 6.2), 'grip_ribs_dark', rot=GRIP, name='pistol_grip')
g.box((C - 0.66, 1.0, 4.3), (C + 0.66, 1.4, 6.3), 'gunmetal', rot=GRIP, name='grip_cap')
g.box((C - 0.45, 1.6, 4.2), (C + 0.45, 6.6, 4.5), 'rubber', rot=GRIP, name='grip_front_strap')
g.box((C - 0.6, 5.6, 7.0), (C + 0.6, 7.6, 15.0), 'worn_steel', name='stock_cheek')
g.box((C - 0.6, 4.9, 7.0), (C + 0.6, 5.7, 11.0), 'worn_steel', name='stock_wrist')
g.box((C - 0.6, 3.8, 11.0), (C + 0.6, 5.7, 14.6), 'worn_steel', name='stock_belly')
g.box((C - 0.6, 3.6, 14.6), (C + 0.6, 7.6, 15.3), 'rubber', name='butt_pad')

# Details: scope turrets and eyepiece, lens, rail slots, safety buttons, cam axles, riser screws, sling swivel
g.cyl('y', (C, 1.0), 0.25, 10.6, 11.0, 'steel', seg=8, name='elevation_turret')
g.cyl('x', (10.0, 1.0), 0.25, C + 0.6, C + 0.9, 'steel', seg=8, name='windage_turret')
g.cyl('x', (10.0, 1.0), 0.25, C - 0.9, C - 0.6, 'steel', seg=8, name='windage_turret')
g.ring('z', (C, 10.0), 0.5, 0.8, 5.0, 5.6, 'steel', name='eyepiece')
g.cyl('z', (C, 10.0), 0.6, -3.25, -3.2, 'glass', seg=8, name='scope_lens')
for z in (-14.4, -13.4, -12.4, -11.4, -10.4, -9.4):
    g.box((C - 0.57, 6.3, z), (C + 0.57, 6.6, z + 0.4), 'rubber', name='rail_slot')
for side in (-1, 1):
    g.cyl('x', (5.5, 4.0), 0.18, C + side * 0.6 if side > 0 else C - 0.72, C + 0.72 if side > 0 else C - 0.6, 'steel', seg=8, name='safety_button')
    g.cyl('y', (C + side * 7.0, -10.6), 0.25, 8.1, 8.35, 'steel', seg=8, name='cam_axle')
    g.cyl('y', (C + side * 0.5, -13.2), 0.15, 8.2, 8.4, 'steel', seg=8, name='riser_screw')
g.ring('x', (3.8, 13.0), 0.15, 0.4, C - 0.12, C + 0.12, 'steel', name='sling_swivel')

# Details pass 2: bolt clip, scope rings, string serving, cheek pad, trigger pivot, butt screws
g.box((C - 0.4, 8.2, -7.5), (C + 0.4, 8.45, -6.9), 'steel', name='bolt_clip')
for z in (-1.0, 3.0):
    g.ring('z', (C, 10.0), 0.62, 0.85, z, z + 0.8, 'steel', name='scope_ring')
g.box((C - 0.4, 7.35, -9.1), (C + 0.4, 7.65, -8.8), 'darkmetal', name='string_serving')
g.box((C - 0.62, 7.6, 8.0), (C + 0.62, 7.85, 13.0), 'rubber', name='cheek_pad')
g.cyl('x', (5.6, 6.5), 0.14, C - 0.65, C + 0.65, 'steel', seg=8, name='trigger_pivot')
for y in (4.4, 6.8):
    g.cyl('z', (C, y), 0.12, 15.3, 15.4, 'steel', seg=8, name='butt_screw')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.55, 0.55, 0.55]},
    "ground": {"translation": [0, 2, 0], "scale": [0.36, 0.36, 0.36]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.44, 0.44, 0.44]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
