#!/usr/bin/env python3
"""Colt Python (BO1): long ventilated-rib barrel over a full underlug, fluted cylinder between frame straps,
hammer, round trigger guard, raked walnut grip with checkered panels, front ramp sight. Fitted to the BO1
menu-icon side view (472 x 225 px -> 22.7 x 10.8 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('python_v3')
C = 8
S = 22.7 / 472


def Z(px):
    return -4 + px * S


def Y(py):
    return 11.4 - py * S


def box(x0, x1, y0, y1, hw, mat, name, cx=0.0, **kw):
    g.box((C + cx - hw, Y(y1), Z(x0)), (C + cx + hw, Y(y0), Z(x1)), mat, name=name, **kw)


# Barrel with raised ventilated rib, full underlug, ramp sight, muzzle
box(0, 300, 28, 56, 0.3, 'gunmetal', 'barrel')
box(0, 300, 22, 29, 0.16, 'darkmetal', 'rib')
for px in range(40, 230, 36):
    box(px, px + 16, 19, 22, 0.18, 'rubber', 'rib_vent')
box(0, 255, 50, 70, 0.28, 'gunmetal', 'underlug')
box(215, 262, 50, 74, 0.3, 'gunmetal', 'underlug_web')
box(5, 40, 3, 33, 0.1, 'darkmetal', 'front_sight')
g.cyl('z', (C, Y(40)), 0.16, Z(-2), Z(2), 'rubber', seg=8, name='bore')

# Frame with window straps around the cylinder, top strap with rear sight
box(250, 345, 30, 48, 0.6, 'darkmetal', 'frame_top')
box(250, 345, 124, 150, 0.6, 'darkmetal', 'frame_bottom')
box(335, 400, 50, 100, 0.6, 'darkmetal', 'frame_rear')
box(315, 345, 18, 32, 0.3, 'darkmetal', 'rear_sight')
box(232, 262, 44, 130, 0.45, 'darkmetal', 'frame_front')
g.cyl('z', (C, Y(87)), 1.75, Z(258), Z(338), 'steel', seg=16, name='cylinder')
for ang_y in (-1.15, 0.0, 1.15):
    g.box((C - 1.77, Y(87) + ang_y - 0.14, Z(266)), (C + 1.77, Y(87) + ang_y + 0.14, Z(332)), 'rubber', name='flute')
g.cyl('z', (C, Y(87)), 0.3, Z(332), Z(342), 'darkmetal', seg=8, name='cylinder_pin')

# Hammer and trigger guard, trigger
box(352, 380, 52, 80, 0.15, 'steel', 'hammer')
box(366, 386, 48, 58, 0.15, 'steel', 'hammer_spur')
g.wire([(C, Y(135), Z(272)), (C, Y(152), Z(270)), (C, Y(166), Z(282)), (C, Y(168), Z(312)), (C, Y(154), Z(335)), (C, Y(140), Z(338))],
       0.32, 'darkmetal', name='trigger_guard')
box(296, 308, 104, 140, 0.1, 'steel', 'trigger')

# Grip: one raked piece with checkered walnut panels
ROT = ('x', -22.5, (C, Y(70), Z(349)))
box(330, 415, 78, 238, 0.5, 'darkmetal', 'grip_frame', rot=ROT)
for side in (-1, 1):
    box(336, 409, 82, 228, 0.05, 'wood_ribbed_light', 'grip_panel', cx=side * 0.55, rot=ROT)

# Details: ejector rod, muzzle crown, cylinder latch, frame screws, red ramp insert
g.cyl('z', (C, Y(66)), 0.12, Z(8), Z(250), 'steel', seg=8, name='ejector_rod')
g.ring('z', (C, Y(40)), 0.16, 0.3, Z(-2), Z(2), 'steel', name='muzzle_crown')
box(345, 378, 66, 84, 0.66, 'steel', 'cylinder_latch')
for px, py in ((247, 60), (385, 62), (385, 92)):
    g.cyl('x', (Y(py), Z(px)), 0.12, C - 0.68, C + 0.68, 'steel', seg=8, name='frame_screw')
box(8, 16, 8, 25, 0.12, 'led_red', 'sight_insert')

# Details pass 2: grip medallion, heel cap, underlug vents, barrel plate, hammer checkering, sight notch, ejector collar
box(368, 388, 112, 132, 0.64, 'gold', 'grip_medallion', rot=ROT)
box(333, 418, 234, 246, 0.52, 'steel', 'grip_heel_cap', rot=ROT)
for px in (120, 160):
    box(px, px + 22, 58, 63, 0.3, 'rubber', 'underlug_vent')
box(70, 200, 36, 44, 0.31, 'steel', 'barrel_plate')
for py in (54, 60, 66):
    box(358, 376, py, py + 2, 0.17, 'rubber', 'hammer_checker')
box(322, 330, 17, 24, 0.32, 'rubber', 'sight_notch')
g.cyl('z', (C, Y(66)), 0.2, Z(232), Z(244), 'steel', seg=8, name='ejector_collar')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 0.75, -1.5], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_lefthand": {"translation": [0, 0.75, -1.5], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"translation": [3.5, -1.25, -8], "scale": [0.75, 0.75, 0.75]},
    "firstperson_lefthand": {"translation": [3.5, -1.25, -8], "scale": [0.75, 0.75, 0.75]},
    "ground": {"translation": [0, -0.25, 0], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [90, -45, 90], "translation": [-0.25, 0.25, -0.5], "scale": [0.65, 0.65, 0.65]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -29.25, 0], "scale": [0.75, 0.75, 0.75]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [-0.5, 0, -6.25], "scale": [0.75, 0.75, 0.75]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
