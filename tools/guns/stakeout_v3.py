#!/usr/bin/env python3
"""Stakeout (BO1, Ithaca 37 pistol-grip): perforated heat shield over the barrel, tube magazine under it,
ribbed wooden pump, dark receiver wrapped with leather straps, pistol grip. 26 units; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('stakeout_v3', offset=(0, -1, 8))
C = 8

# Barrel, perforated heat shield over the muzzle half, tube magazine with cap
g.cyl('z', (C, 9.0), 0.45, -15, 0, 'gunmetal', seg=8, name='barrel')
g.cyl('z', (C, 9.0), 0.28, -15.05, -14.9, 'rubber', seg=8, name='bore')
g.box((C - 0.55, 9.1, -14.4), (C + 0.55, 10.0, -10.4), 'steel', name='heat_shield')
for i in range(8):
    z = -14.1 + i * 0.47
    g.box((C - 0.57, 9.3, z), (C + 0.57, 9.7, z + 0.26), 'rubber', name='shield_hole')
g.box((C - 0.6, 9.0, -14.5), (C + 0.6, 9.3, -14.2), 'darkmetal', name='shield_clamp_front')
g.box((C - 0.6, 9.0, -10.6), (C + 0.6, 9.3, -10.3), 'darkmetal', name='shield_clamp_rear')
g.cyl('z', (C, 8.35), 0.4, -14.0, 0, 'darkmetal', seg=8, name='mag_tube')
g.cyl('z', (C, 8.35), 0.45, -14.5, -14.0, 'darkmetal', seg=8, name='mag_cap')

# Ribbed wooden pump
g.box((C - 0.9, 7.6, -10.5), (C + 0.9, 9.0, -4.0), 'wood_ribbed_dark', name='pump')
g.cyl('z', (C, 8.1), 0.78, -10.4, -4.1, 'wood_ribbed_dark', seg=8, name='pump_belly')

g.box((C - 1.0, 7.5, -10.7), (C + 1.0, 9.1, -10.4), 'darkmetal', name='pump_collar_front')
g.box((C - 1.0, 7.5, -4.2), (C + 1.0, 9.1, -3.9), 'darkmetal', name='pump_collar_rear')

g.box((C - 0.8, 6.3, -6.2), (C + 0.8, 7.7, -3.0), 'gunmetal', name='action_block')

# Receiver, ejection port, leather straps, sling loop
g.box((C - 0.8, 6.0, -0.6), (C + 0.8, 8.9, 3.0), 'darkmetal', name='receiver')
g.box((C + 0.79, 7.9, 1.0), (C + 0.84, 8.7, 2.8), 'rubber', name='loading_port')
for z0 in (-1.4, 1.4, 2.5):
    g.box((C - 0.9, 6.9, z0), (C + 0.9, 9.0, z0 + 0.5), 'leather', name='strap')
g.box((C - 0.92, 8.5, -0.6), (C + 0.92, 8.9, 3.0), 'leather', name='strap_long')

# Rear tang sloping down into the raked pistol grip
g.box((C - 0.7, 6.3, 2.6), (C + 0.7, 7.9, 5.6), 'darkmetal', rot=('x', 22.5, (C, 7.9, 2.6)), name='tang')

# Trigger guard, trigger, raked pistol grip with a swell at the back strap and a flared cap
g.box((C - 0.22, 6.0, 0.6), (C + 0.22, 6.3, 3.4), 'gunmetal', name='guard_bottom')
g.box((C - 0.22, 6.0, 0.6), (C + 0.22, 7.0, 0.9), 'gunmetal', name='guard_front')
g.box((C - 0.1, 6.3, 1.6), (C + 0.1, 7.0, 1.9), 'steel', name='trigger')
GRIP = ('x', -22.5, (C, 7.6, 1.2))
g.box((C - 0.55, 1.6, 1.0), (C + 0.55, 7.6, 4.3), 'polymer_dark', rot=GRIP, name='pistol_grip')
g.box((C - 0.7, 1.2, 0.8), (C + 0.7, 1.9, 4.6), 'darkmetal', rot=GRIP, name='grip_cap')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
g.cyl('x', (9.15, -14.35), 0.1, C - 0.66, C + 0.66, 'steel', seg=8, name='clamp_screw')
g.ring('x', (7.7, -14.25), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.box((C - 0.12, 8.9, 2.6), (C + 0.12, 9.6, 3.4), 'steel', name='hammer')
for y, z in ((8.0, 0.5), (7.0, 0.5)):
    g.cyl('x', (y, z), 0.12, C - 0.86, C + 0.86, 'brass', seg=8, name='receiver_screw')
sb(0.78, 0.95, 6.3, 6.8, -0.4, 0.2, 'steel', name='action_release')
g.ring('z', (C, 9.0), 0.45, 0.58, -9.8, -9.4, 'darkmetal', name='barrel_band')
g.ring('z', (C, 9.0), 0.45, 0.58, -1.5, -1.1, 'darkmetal', name='barrel_band')

# Detail pass 2
for s in (-1, 1):
    for y in (7.3, 7.9):
        g.cyl('z', (C + s * 0.95, y), 0.17, -0.6, 2.9, 'brass', seg=8, name='shell_loop')
g.box((C - 0.07, 9.4, -14.9), (C + 0.07, 9.7, -14.7), 'gold', name='bead_sight')
g.ring('z', (C, 8.35), 0.4, 0.5, -13.9, -13.6, 'steel', name='mag_tube_ring')
g.cyl('x', (6.15, 2.0), 0.1, C - 0.3, C + 0.3, 'steel', seg=8, name='guard_screw')
g.cyl('x', (7.3, 2.15), 0.15, C - 0.9, C + 0.9, 'steel', seg=8, name='cross_bolt_safety')
sb(-0.9, 0.9, 9.0, 9.2, -10.4, -4.2, 'darkmetal', name='pump_top_rail')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, -1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"translation": [1, 2, -1], "scale": [0.7, 0.7, 0.7]},
    "firstperson_lefthand": {"translation": [1, 2, -1], "scale": [0.7, 0.7, 0.7]},
    "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
