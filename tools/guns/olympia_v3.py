#!/usr/bin/env python3
"""Olympia (BO1 over/under shotgun): two stacked barrels with a top rib and bead, walnut forend, engraved
silver receiver with top lever, trigger guard, walnut stock with pistol grip and recoil pad. 36 units long,
proportions from the BO1 menu icon; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('olympia_v3', offset=(0, -1, 0.5))
C = 8

# Over/under barrels, top rib, bead, muzzle bores; the icon tilts the barrels up towards the muzzle, so they
# step up in sections
SECTIONS = ((-15, -9.5, 1.0), (-9.5, -4.0, 0.9), (-4.0, 0.2, 0.7), (0.2, 2.2, 0.5))
for z0, z1, dy in SECTIONS:
    for y in (9.55, 8.55):
        g.cyl('z', (C, y + dy), 0.48, z0, z1 + 0.05, 'darkmetal', seg=16, name='barrel')
    g.box((C - 0.18, 9.75 + dy, z0), (C + 0.18, 9.9 + dy, z1 + 0.05), 'gunmetal', name='top_rib')
    g.box((C - 0.3, 8.95 + dy, z0), (C + 0.3, 9.15 + dy, z1 + 0.05), 'gunmetal', name='side_rib')
for y in (9.55, 8.55):
    g.cyl('z', (C, y + 1.0), 0.26, -15.05, -14.9, 'rubber', seg=8, name='bore')
g.box((C - 0.08, 10.95, -14.6), (C + 0.08, 11.15, -14.3), 'gold', name='bead')

# Walnut forend under the barrels, stepped like the barrels
g.box((C - 0.8, 8.5, -6.4), (C + 0.8, 8.9, -4.0), 'wood', name='forend_front')
g.box((C - 0.8, 8.6, -4.0), (C + 0.8, 8.9, 0.2), 'wood', name='forend')
g.cyl('z', (C, 9.0), 0.35, -6.2, 1.8, 'wood', seg=8, name='forend_belly')
g.box((C - 0.84, 8.3, 0.6), (C + 0.84, 8.9, 2.0), 'worn_steel', name='forend_iron')

# Engraved receiver, hinge pin, top lever, safety
g.box((C - 0.66, 7.6, 2.2), (C + 0.66, 9.9, 6.2), 'worn_steel', name='receiver')
g.box((C - 0.68, 7.9, 2.6), (C + 0.68, 9.6, 5.8), 'engraved_steel', name='engraving_panel')
g.cyl('x', (7.4, 2.8), 0.32, C - 0.82, C + 0.82, 'darkmetal', seg=8, name='hinge_pin')
g.box((C - 0.25, 9.9, 5.2), (C + 0.6, 10.2, 6.6), 'darkmetal', name='top_lever')
g.box((C - 0.15, 9.9, 6.0), (C + 0.15, 10.15, 6.5), 'gold', name='safety')

# Trigger guard and triggers
g.box((C - 0.22, 6.2, 4.9), (C + 0.22, 6.5, 8.0), 'chrome', name='guard_bottom')
g.box((C - 0.22, 6.2, 4.9), (C + 0.22, 7.7, 5.2), 'chrome', name='guard_front')
g.box((C - 0.1, 6.5, 5.8), (C + 0.1, 7.6, 6.1), 'gold', name='trigger')

# Walnut stock: wrist, pistol grip, butt with sloping comb and belly, recoil pad
g.box((C - 0.72, 6.3, 6.6), (C + 0.72, 9.3, 11.0), 'wood', name='wrist')
g.box((C - 0.7, 5.6, 7.6), (C + 0.7, 6.6, 10.0), 'wood', rot=('x', -22.5, (C, 6.4, 8.8)), name='pistol_grip')
g.box((C - 0.72, 5.5, 7.4), (C + 0.72, 5.8, 9.8), 'darkmetal', rot=('x', -22.5, (C, 6.4, 8.8)), name='grip_cap')
g.box((C - 0.82, 5.6, 11.0), (C + 0.82, 9.1, 20.6), 'wood', name='butt')
g.box((C - 0.8, 4.6, 13.0), (C + 0.8, 5.8, 20.6), 'wood', rot=('x', 22.5, (C, 5.0, 16.8)), name='butt_belly')
g.box((C - 0.8, 4.0, 17.4), (C + 0.8, 5.7, 20.6), 'wood', name='butt_heel')
g.box((C - 0.85, 4.2, 20.6), (C + 0.85, 8.6, 21.2), 'rubber', name='recoil_pad')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
g.cyl('x', (8.6, 1.3), 0.12, C - 0.9, C + 0.9, 'brass', seg=8, name='forend_screw')
g.cyl('y', (C, 3.4), 0.2, 9.9, 10.05, 'brass', seg=8, name='tang_screw')
for y, z in ((9.55, 6.0), (8.0, 2.4)):
    g.cyl('x', (y, z), 0.12, C - 0.72, C + 0.72, 'brass', seg=8, name='receiver_screw')
g.box((C - 0.55, 8.9, -7.0), (C + 0.55, 11.0, -6.4), 'gunmetal', name='barrel_band')
g.box((C - 0.55, 8.7, -2.0), (C + 0.55, 10.8, -1.4), 'gunmetal', name='barrel_band')
g.ring('x', (8.5, -3.0), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (3.75, 19.0), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')

# Detail pass 2
for y in (9.55, 8.55):
    g.ring('z', (C, y + 1.0), 0.48, 0.56, -15.0, -14.7, 'steel', name='muzzle_ring')
g.box((C - 0.74, 6.3, 6.2), (C + 0.74, 9.4, 6.6), 'gunmetal', name='tang_cap')
for z in (7.2, 7.8, 8.4):
    g.box((C - 0.74, 6.5, z), (C + 0.74, 9.1, z + 0.18), 'darkmetal', name='wrist_checkering')
for y in (5.2, 7.6):
    g.cyl('z', (C - 0.4, y), 0.12, 21.1, 21.3, 'brass', seg=8, name='pad_screw')
g.box((C - 0.2, 8.1, 1.4), (C + 0.2, 8.3, 1.9), 'steel', name='forend_latch')
for s in (-1, 1):
    sb(s * 0.68, s * 0.7, 9.45, 9.6, 2.6, 5.8, 'gold', name='receiver_inlay')
    sb(s * 0.68, s * 0.7, 7.95, 8.1, 2.6, 5.8, 'gold', name='receiver_inlay')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.56, 0.56, 0.56]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.56, 0.56, 0.56]},
    "ground": {"translation": [0, 2, 0], "scale": [0.36, 0.36, 0.36]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.48, 0.48, 0.48]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
