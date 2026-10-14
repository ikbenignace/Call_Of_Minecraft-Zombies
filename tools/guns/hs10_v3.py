#!/usr/bin/env python3
"""HS-10 (BO1, High Standard model 10 bullpup shotgun): barrel and shroud up front, sloping one-piece
shell from the pump to the recoil pad, carry rail with the integral flashlight on top, pistol grip
mid-body, slide handle under the barrel. 30 units; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('hs10_v3', offset=(0, -1, 5))
C = 8

# Barrel with shroud and front sight
g.cyl('z', (C, 9.6), 0.42, -15, -6.0, 'steel', seg=8, name='barrel')
g.cyl('z', (C, 9.6), 0.26, -15.05, -14.9, 'rubber', seg=8, name='bore')
g.cyl('z', (C, 9.7), 1.3, -15, -6.0, 'darkmetal', seg=16, name='barrel_shroud')
g.box((C - 0.12, 9.6, -14.6), (C + 0.12, 10.3, -14.2), 'darkmetal', name='front_sight')

# Slide handle under the barrel
g.box((C - 0.7, 6.6, -14.4), (C + 0.7, 8.9, -6.0), 'darkmetal', name='slide_handle')
g.box((C - 0.5, 6.0, -8.4), (C + 0.5, 6.7, -6.2), 'darkmetal', name='slide_grip')

# Shell: front section, main body, sloping underside towards the butt, recoil pad
g.box((C - 0.95, 6.2, -6.2), (C + 0.95, 10.3, 2.0), 'polymer_dark', name='shell_front')
g.box((C - 1.0, 6.4, 2.0), (C + 1.0, 10.4, 14.4), 'polymer_dark', name='shell_body')
g.box((C - 0.98, 5.9, 8.0), (C + 0.98, 6.6, 14.4), 'polymer_dark', name='shell_belly')
g.box((C - 0.96, 5.6, 3.0), (C + 0.96, 6.8, 9.0), 'polymer_dark', rot=('x', 22.5, (C, 6.2, 6.0)), name='shell_slope')
g.box((C - 1.02, 8.2, 2.4), (C + 1.02, 8.5, 13.8), 'darkmetal', name='shell_seam')
g.box((C + 1.0, 8.6, 6.0), (C + 1.05, 9.8, 9.0), 'rubber', name='ejection_port')
g.box((C - 1.05, 4.0, 14.4), (C + 1.05, 10.5, 15.2), 'rubber', name='recoil_pad')

# Carry rail and integral flashlight
g.box((C - 0.35, 10.4, -6.0), (C + 0.35, 11.0, 6.0), 'gunmetal', name='carry_rail')
g.box((C - 0.25, 11.0, -4.6), (C + 0.25, 11.6, -3.6), 'darkmetal', name='front_post')
g.cyl('z', (C, 11.0), 0.55, -1.0, 3.6, 'gunmetal', seg=8, name='flashlight')
g.cyl('z', (C, 11.0), 0.45, -1.2, -1.0, 'glass', seg=8, name='flashlight_lens')
g.box((C - 0.3, 10.8, 0.0), (C + 0.3, 11.0, 3.0), 'gunmetal', name='flashlight_mount')

# Trigger guard, trigger, pistol grip mid-body
g.box((C - 0.22, 5.3, -5), (C + 0.22, 5.6, -2.2), 'darkmetal', name='guard_bottom')
g.box((C - 0.1, 5.6, -4), (C + 0.1, 6.4, -3.7), 'steel', name='trigger')
GRIP = ('x', -22.5, (C, 7.2, -2.4))
g.box((C - 0.8, 1.6, -2.4), (C + 0.8, 7.4, 0), 'polymer_dark', rot=GRIP, name='pistol_grip')
g.box((C - 0.85, 1.3, -2.5), (C + 0.85, 1.7, 0.1), 'darkmetal', rot=GRIP, name='grip_cap')
for y in (2.6, 3.6, 4.6, 5.6):
    g.box((C - 0.83, y, -2.45), (C + 0.83, y + 0.3, 0.05), 'rubber', rot=GRIP, name='grip_rib')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
g.ring('x', (5.7, 12.0), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
g.ring('x', (5.8, -7.3), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
for z in (-5.6, -2.2, 4.0, 4.8, 5.5):
    for s in (-1, 1):
        sb(s * 0.33, s * 0.38, 10.5, 10.9, z, z + 0.4, 'rubber', name='rail_slot')
sb(0.5, 0.7, 10.8, 11.2, 2.6, 3.2, 'steel', name='light_switch')
g.ring('z', (C, 11.0), 0.45, 0.62, -1.4, -1.0, 'steel', name='lens_bezel')
for y, z in ((9.6, 3.5), (9.6, 12.5), (7.2, 3.5), (7.2, 12.5)):
    g.cyl('x', (y, z), 0.13, C - 1.06, C + 1.06, 'steel', seg=8, name='shell_screw')
for z in (-13.5, -12.5, -11.5):
    g.box((C - 1.33, 9.2, z), (C + 1.33, 10.2, z + 0.4), 'rubber', name='shroud_vent')

# Detail pass 2
g.ring('z', (C, 9.7), 1.3, 1.38, -15, -14.7, 'steel', name='shroud_cap')
g.ring('z', (C, 9.7), 1.3, 1.38, -6.3, -6.0, 'darkmetal', name='shroud_band')
for z in (-8.0, -7.4, -6.8):
    g.box((C - 0.52, 6.05, z), (C + 0.52, 6.6, z + 0.25), 'rubber', name='slide_rib')
for y in (5.5, 9.5):
    for s in (-1, 1):
        g.cyl('z', (C + s * 0.6, y), 0.15, 15.2, 15.4, 'steel', seg=8, name='pad_screw')
sb(1.0, 1.07, 8.5, 8.6, 5.8, 9.2, 'steel', name='port_sill')
sb(1.0, 1.07, 9.8, 9.95, 5.8, 9.2, 'steel', name='port_header')
g.box((C - 0.22, 5.6, -5), (C + 0.22, 6.3, -4.7), 'darkmetal', name='guard_front')
g.ring('z', (C, 11.0), 0.55, 0.62, 3.4, 3.6, 'darkmetal', name='light_end_cap')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -1], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"translation": [0, 3, -1], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"translation": [1, 2, -1], "scale": [0.64, 0.64, 0.64]},
    "firstperson_lefthand": {"translation": [1, 2, -1], "scale": [0.64, 0.64, 0.64]},
    "ground": {"translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.56, 0.56, 0.56]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
