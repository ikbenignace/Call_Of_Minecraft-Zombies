#!/usr/bin/env python3
"""M1911 (BO2): slide with rear serrations and sights, barrel bushing, frame with beavertail and hammer,
trigger guard loop, steel grip frame raked back with walnut panels, magazine base. Fitted to the BO2
menu-icon side view (706 x 514 px -> 18.5 x 13.5 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('m1911_v3')
C = 8
S = 18.5 / 706


def Z(px):
    return -2 + px * S


def Y(py):
    return 12 - py * S


def box(x0, x1, y0, y1, hw, mat, name, cx=0.0, **kw):
    g.box((C + cx - hw, Y(y1), Z(x0)), (C + cx + hw, Y(y0), Z(x1)), mat, name=name, **kw)


# Slide with rear serrations and sights
box(22, 645, 22, 150, 0.55, 'gunmetal', 'slide')
for px in range(528, 636, 14):
    box(px, px + 7, 30, 142, 0.57, 'darkmetal', 'serration')
box(22, 64, 6, 24, 0.1, 'darkmetal', 'front_sight')
box(600, 650, 6, 24, 0.32, 'darkmetal', 'rear_sight')
g.cyl('z', (C, Y(80)), 0.22, Z(20), Z(24), 'rubber', seg=8, name='bore')

# Barrel, bushing and recoil-spring plug under the slide nose
box(24, 215, 128, 178, 0.42, 'steel', 'barrel_bushing')
box(20, 40, 128, 178, 0.45, 'darkmetal', 'spring_plug')

# Frame with dust cover, slide stop, trigger guard, trigger
box(215, 500, 148, 196, 0.5, 'darkmetal', 'frame')
box(330, 545, 190, 262, 0.5, 'darkmetal', 'trigger_housing')
box(400, 448, 150, 172, 0.58, 'steel', 'slide_stop')
g.wire([(C, Y(192), Z(338)), (C, Y(250), Z(340)), (C, Y(292), Z(385)), (C, Y(292), Z(440)), (C, Y(262), Z(488))],
       0.34, 'darkmetal', name='trigger_guard')
box(385, 405, 196, 252, 0.1, 'steel', 'trigger')

# Beavertail, hammer
box(560, 700, 140, 205, 0.5, 'darkmetal', 'beavertail')
box(640, 690, 58, 125, 0.16, 'steel', 'hammer')
box(565, 640, 100, 150, 0.3, 'steel', 'hammer_housing')

# Grip: one raked steel frame, walnut panels with the same rake, magazine base
ROT = ('x', -22.5, (C, Y(190), Z(520)))
box(435, 585, 150, 430, 0.5, 'darkmetal', 'grip_frame', rot=ROT)
for side in (-1, 1):
    box(448, 572, 205, 415, 0.05, 'wood', 'grip_panel', cx=side * 0.55, rot=ROT)
box(440, 590, 425, 448, 0.52, 'darkmetal', 'magazine_base', rot=ROT)

# Details: ejection port, thumb safety, front serrations, magazine release, muzzle crown
for side in (-1, 1):
    box(405, 500, 40, 100, 0.03, 'rubber', 'ejection_port', cx=side * 0.56)
    g.cyl('x', (Y(222), Z(520)), 0.16, C + side * 0.5 - 0.0 if side > 0 else C - 0.62, C + 0.62 if side > 0 else C - 0.5, 'steel', seg=8, name='mag_release')
box(505, 565, 150, 175, 0.6, 'steel', 'thumb_safety')
for px in range(96, 150, 14):
    box(px, px + 7, 30, 142, 0.57, 'darkmetal', 'front_serration')
g.ring('z', (C, Y(80)), 0.22, 0.4, Z(18), Z(24), 'steel', name='muzzle_crown')

# Detail pass 2
for side in (-1, 1):
    for py in (240, 395):
        box(502, 518, py, py + 14, 0.04, 'steel', 'grip_screw', cx=side * 0.62, rot=ROT)
box(588, 602, 425, 446, 0.1, 'steel', 'lanyard_loop', rot=ROT)
box(500, 512, 40, 100, 0.03, 'steel', 'extractor', cx=0.56)
g.cyl('x', (Y(215), Z(480)), 0.1, C - 0.56, C + 0.56, 'steel', seg=8, name='frame_pin')
g.cyl('x', (Y(170), Z(250)), 0.1, C - 0.56, C + 0.56, 'steel', seg=8, name='dust_cover_pin')
box(430, 440, 215, 415, 0.51, 'rubber', 'front_strap_stipple', rot=ROT)
box(190, 200, 124, 182, 0.46, 'steel', 'bushing_ring')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 0.75, -0.75], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_lefthand": {"translation": [0, 0.75, -0.75], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"translation": [0, 0.5, 0], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [90, -45, 90], "translation": [-0.5, 0.5, -0.5], "scale": [0.65, 0.65, 0.65]},
    "head": {"rotation": [0, 90, 0], "translation": [0.25, -28.75, 0], "scale": [0.7, 0.7, 0.7]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [-0.25, 0, -7], "scale": [0.7, 0.7, 0.7]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
