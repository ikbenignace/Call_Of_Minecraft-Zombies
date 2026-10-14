#!/usr/bin/env python3
"""HK G11 (BO1): boxy olive casing with a lower front section around the recessed muzzle, long optic /
carry rail on top, cocking wheel on the side, raked grip with guard, square butt. From the BO1 menu icon
(232 px -> 30 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('g11_v3', offset=(0, 0.5, 5))
C = 8


def Z(px):
    return -15 + (px - 8) / 7.73


def Y(py):
    return 11 - (py - 20) / 7.73


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px0)), (C + hw, Y(py0), Z(px1)), mat, **kw)


# Casing: main box, lower front section, recessed muzzle, panel seams
box(1.2, 24, 96, 82, 238, 'polymer_olive', name='casing')
box(1.15, 36, 66, 8, 84, 'polymer_olive', name='front_section')
g.cyl('z', (C, Y(60)), 0.45, Z(6), Z(10), 'rubber', seg=8, name='muzzle')
box(1.22, 50, 54, 84, 236, 'polymer_dark', name='casing_seam')
box(1.22, 28, 84, 140, 144, 'polymer_dark', name='casing_seam_v')
box(1.25, 64, 82, 180, 226, 'polymer_dark', name='side_panel')
box(1.22, 26, 96, 230, 240, 'polymer_dark', name='butt')

# Optic / carry rail with front and rear posts
box(0.5, 24, 34, 20, 140, 'gunmetal', name='optic')
box(0.55, 18, 24, 20, 36, 'darkmetal', name='optic_front')
box(0.55, 2, 24, 98, 146, 'darkmetal', name='optic_rear')
box(0.4, 30, 38, 28, 132, 'darkmetal', name='optic_mount')

# Cocking wheel on the left side
g.cyl('x', (Y(62), Z(165)), 0.95, C - 1.45, C - 1.2, 'darkmetal', seg=16, name='cocking_wheel')
g.box((C - 1.6, Y(62) - 0.15, Z(165) - 0.9), (C - 1.45, Y(62) + 0.15, Z(165) + 0.9), 'steel', name='wheel_handle')

# Trigger guard, trigger, raked grip
box(0.25, 106, 110, 92, 138, 'darkmetal', name='guard_bottom')
box(0.25, 94, 110, 92, 98, 'darkmetal', name='guard_front')
box(0.1, 96, 106, 110, 114, 'steel', name='trigger')
box(0.65, 92, 140, 100, 142, 'polymer_dark', rot=('x', -22.5, (C, Y(94), Z(121))), name='grip')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
for y, z in ((Y(40), Z(100)), (Y(88), Z(220))):
    g.cyl('x', (y, z), 0.15, C - 1.26, C + 1.26, 'steel', seg=8, name='casing_screw')
sb(-0.5, 0.5, Y(22), Y(6), Z(146), Z(153), 'rubber', name='eyecup')
sb(-0.4, 0.4, Y(19), Y(23), Z(18), Z(20), 'glass', name='front_lens')
for s in (-1, 1):
    sb(s * 1.18, s * 1.28, Y(70), Y(80), Z(190), Z(205), 'steel', name='selector')
g.ring('x', (Y(80), Z(240) + 0.2), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='sling_loop')
g.ring('z', (C, Y(60)), 0.45, 0.62, Z(6), Z(9), 'steel', name='muzzle_collar')
for px in (30, 42, 54):
    sb(-1.17, 1.17, Y(62), Y(40), Z(px), Z(px + 4), 'rubber', name='vent_slit')
for px in range(40, 132, 16):
    sb(-0.55, 0.55, Y(24), Y(24) + 0.15, Z(px), Z(px + 6), 'darkmetal', name='rail_tooth')
for px_, py_ in ((200, 30), (100, 90), (228, 30)):
    g.cyl('x', (Y(py_), Z(px_)), 0.15, C - 1.26, C + 1.26, 'steel', seg=8, name='casing_screw')
sb(1.18, 1.3, Y(80), Y(58), Z(150), Z(175), 'darkmetal', name='latch_plate')
sb(1.3, 1.36, Y(72), Y(66), Z(156), Z(170), 'steel', name='latch_catch')
for s in (-1, 1):
    sb(s * 1.15, s * 1.2, Y(54), Y(52), Z(10), Z(82), 'darkmetal', name='front_line')
for py_ in (104, 114, 124):
    box(0.68, py_, py_ + 4, 104, 140, 'rubber', rot=('x', -22.5, (C, Y(94), Z(121))), name='grip_rib')
sb(-1.25, 1.25, Y(96), Y(26), Z(240), Z(243), 'rubber', name='butt_plate')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 2, -3], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 2, -3], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"translation": [1, 0.5, -2], "scale": [0.66, 0.66, 0.66]},
    "firstperson_lefthand": {"translation": [1, 0.5, -2], "scale": [0.66, 0.66, 0.66]},
    "ground": {"translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.56, 0.56, 0.56]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
