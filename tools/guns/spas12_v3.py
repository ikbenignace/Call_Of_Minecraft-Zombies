#!/usr/bin/env python3
"""SPAS-12 (BO1): stock folded over the top with its butt hook ahead of the receiver, perforated heat
shield on the barrel, tube magazine, ribbed polymer pump, receiver, pistol grip. 28 units folded."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('spas12_v3', offset=(0, -1, 6))
C = 8

# Barrel, perforated heat shield, tube magazine
g.cyl('z', (C, 9.2), 0.48, -15, 1.0, 'gunmetal', seg=8, name='barrel')
g.cyl('z', (C, 9.2), 0.3, -15.05, -14.9, 'rubber', seg=8, name='bore')
g.box((C - 0.6, 9.5, -12.0), (C + 0.6, 10.2, -1.0), 'steel', name='heat_shield')
for z in range(-11, -1, 1):
    g.box((C - 0.62, 9.75, z + 0.2), (C + 0.62, 10.0, z + 0.6), 'rubber', name='shield_hole')
g.cyl('z', (C, 8.25), 0.42, -14.2, 1.0, 'darkmetal', seg=8, name='mag_tube')
g.cyl('z', (C, 8.25), 0.48, -14.6, -14.2, 'darkmetal', seg=8, name='mag_cap')

# Ribbed polymer pump
g.box((C - 0.95, 7.4, -10.6), (C + 0.95, 9.1, -3.8), 'polymer_dark', name='pump')
for z in (-10.0, -8.9, -7.8, -6.7, -5.6, -4.5):
    g.box((C - 0.97, 7.5, z), (C + 0.97, 9.0, z + 0.35), 'rubber', name='pump_rib')

# Receiver, ejection port, safety
g.box((C - 0.85, 6.9, 1.0), (C + 0.85, 10.1, 8.4), 'gunmetal', name='receiver')
g.box((C - 0.9, 8.0, -1.0), (C + 0.9, 9.9, 1.0), 'gunmetal', name='receiver_front')
g.box((C + 0.84, 8.4, 3.0), (C + 0.9, 9.5, 5.6), 'rubber', name='ejection_port')
g.box((C - 0.15, 6.8, 6.6), (C + 0.15, 7.0, 7.4), 'steel', name='safety')

# Folded stock over the top: two arms, hook butt ahead of the receiver, hinge at the rear
g.box((C - 0.8, 10.1, -2.4), (C - 0.55, 10.5, 9.0), 'gunmetal', name='stock_arm')
g.box((C + 0.55, 10.1, -2.4), (C + 0.8, 10.5, 9.0), 'gunmetal', name='stock_arm')
g.box((C - 0.8, 10.1, -3.2), (C + 0.8, 10.7, -2.4), 'gunmetal', name='stock_butt')
g.box((C - 0.25, 9.3, -3.2), (C + 0.25, 10.2, -2.8), 'gunmetal', name='stock_hook')
g.cyl('x', (10.0, 8.6), 0.35, C - 0.9, C + 0.9, 'steel', seg=8, name='stock_hinge')

# Trigger guard, trigger, pistol grip
g.box((C - 0.22, 6.0, 2.4), (C + 0.22, 6.3, 5.2), 'darkmetal', name='guard_bottom')
g.box((C - 0.22, 6.0, 2.4), (C + 0.22, 6.9, 2.7), 'darkmetal', name='guard_front')
g.box((C - 0.1, 6.3, 3.4), (C + 0.1, 6.9, 3.7), 'steel', name='trigger')
GRIP = ('x', -22.5, (C, 7.0, 6.2))
g.box((C - 0.75, 2.6, 4.8), (C + 0.75, 7.0, 7.9), 'polymer_dark', rot=GRIP, name='pistol_grip')
for y in (3.6, 4.6, 5.6):
    g.box((C - 0.78, y, 4.75), (C + 0.78, y + 0.3, 7.95), 'rubber', rot=GRIP, name='grip_rib')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
g.cyl('x', (7.2, 3.2), 0.25, C - 0.95, C + 0.95, 'steel', seg=8, name='safety_button')
for z0 in (-11.8, -1.4):
    g.box((C - 0.65, 9.3, z0), (C + 0.65, 10.3, z0 + 0.4), 'darkmetal', name='shield_clamp')
g.ring('x', (7.6, -14.4), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
sb(-0.85, -1.0, 7.2, 7.7, 1.6, 2.2, 'steel', name='action_release')
for z in (2.0, 7.5):
    g.cyl('x', (9.5, z), 0.13, C - 0.92, C + 0.92, 'steel', seg=8, name='receiver_screw')
g.box((C - 0.2, 10.1, 7.5), (C + 0.2, 10.3, 8.0), 'darkmetal', name='sight_base')
g.box((C - 0.1, 9.5, -14.5), (C + 0.1, 10.4, -14.0), 'darkmetal', name='front_sight')

# Detail pass 2
g.box((C - 0.8, 10.1, -3.4), (C + 0.8, 10.7, -3.2), 'rubber', name='stock_butt_pad')
for s in (-1, 1):
    sb(s * 0.6, s * 0.66, 9.6, 10.0, -11.5, -1.5, 'gunmetal', name='shield_rail')
g.ring('z', (C, 9.2), 0.48, 0.58, -15.0, -14.6, 'darkmetal', name='muzzle_collar')
g.box((C - 1.0, 7.3, -10.8), (C + 1.0, 9.2, -10.6), 'darkmetal', name='pump_collar_front')
g.box((C - 1.0, 7.3, -3.8), (C + 1.0, 9.2, -3.6), 'darkmetal', name='pump_collar_rear')
g.box((C - 0.5, 6.7, 0.8), (C + 0.5, 6.9, 2.2), 'steel', name='loading_gate')
g.ring('x', (7.5, 8.4), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
g.box((C - 0.8, 2.3, 4.7), (C + 0.8, 2.7, 7.9), 'darkmetal', rot=GRIP, name='grip_cap')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -1], "scale": [0.52, 0.52, 0.52]},
    "thirdperson_lefthand": {"translation": [0, 3, -1], "scale": [0.52, 0.52, 0.52]},
    "firstperson_righthand": {"translation": [1, 2, -1], "scale": [0.66, 0.66, 0.66]},
    "firstperson_lefthand": {"translation": [1, 2, -1], "scale": [0.66, 0.66, 0.66]},
    "ground": {"translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.58, 0.58, 0.58]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
