#!/usr/bin/env python3
"""Makarov PM (BO1): rounded slide with rear serrations and slide-mounted safety lever, round trigger
guard, reddish bakelite grip with checkering, star medallion and lanyard loop. 16 units."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('makarov_v3', offset=(0, -1, 12))
C = 8

def Z(px):
    return -12 + (px - 128) / 19


def Y(py):
    return 11 - (py - 30) / 19


def box(x0, x1, y0, y1, hw, mat, name, **kw):
    g.box((C - hw, Y(y1), Z(x0)), (C + hw, Y(y0), Z(x1)), mat, name=name, **kw)


# Slide: body with rounded top, serrations, safety lever, sights, bore
box(128, 384, 50, 92, 0.55, 'worn_steel', 'slide')
g.cyl('z', (C, Y(62)), 0.5, Z(128), Z(386), 'worn_steel', seg=8, name='slide_top')
for px in (290, 302, 314, 326, 338):
    box(px, px + 6, 56, 90, 0.58, 'darkmetal', 'serration')
g.cyl('x', (Y(68), Z(352)), 0.35, C - 0.75, C - 0.55, 'steel', seg=8, name='safety_lever')
box(130, 146, 44, 54, 0.08, 'darkmetal', 'front_sight')
box(360, 380, 44, 54, 0.3, 'darkmetal', 'rear_sight')
g.cyl('z', (C, Y(60)), 0.2, Z(127), Z(130), 'rubber', seg=8, name='bore')

# Frame, round trigger guard, trigger, hammer
box(175, 388, 88, 114, 0.5, 'darkmetal', 'frame')
g.wire([(C, Y(100), Z(214)), (C, Y(122), Z(220)), (C, Y(138), Z(245)), (C, Y(138), Z(270)), (C, Y(122), Z(292)), (C, Y(106), Z(296))],
       0.3, 'darkmetal', name='trigger_guard')
box(258, 266, 100, 126, 0.08, 'steel', 'trigger')
box(384, 408, 64, 100, 0.18, 'darkmetal', 'hammer')

# Bakelite grip: one tall unrotated box buried in the frame, thin back strap for the rake, flush base plate
GF, GB, GH, GS = (10, -14, -22, 20)
box(296 + GF, 296 + GF + 90 + GB, 96, 232 + GH, 0.6, 'bakelite_red', 'grip')
if GS:
    box(296 + GF + 90 + GB, 296 + GF + 90 + GB + GS, 160, 232 + GH, 0.55, 'bakelite_red', 'grip_backstrap')
g.cyl('x', (Y(150), Z(296 + GF + 45 + GB / 2)), 0.5, C - 0.66, C + 0.66, 'steel', seg=8, name='medallion')
box(292 + GF, 296 + GF + 90 + GB + GS + 4, 230 + GH, 242 + GH, 0.55, 'steel', 'grip_base')
g.ring('x', (Y(236 + GH), Z(296 + GF + 90 + GB + GS)), 0.18, 0.32, C - 0.12, C + 0.12, 'steel', name='lanyard_loop')

# Details: ejection ports, muzzle crown, takedown latch, grip screw
for side in (-1, 1):
    g.box((C + min(side * 0.55, side * 0.58), Y(72), Z(200)), (C + max(side * 0.55, side * 0.58), Y(58), Z(255)), 'rubber', name='ejection_port')
g.ring('z', (C, Y(60)), 0.2, 0.4, Z(126), Z(130), 'steel', name='muzzle_crown')
g.cyl('x', (Y(104), Z(200)), 0.22, C - 0.6, C + 0.6, 'steel', seg=8, name='takedown_latch')
g.cyl('x', (Y(190), Z(330)), 0.14, C - 0.68, C + 0.68, 'steel', seg=8, name='grip_screw')

# Details pass 2: grip checkering, mag catch, hammer spur, front slide notch, sight notch
for px in (322, 342, 362):
    box(px, px + 4, 112, 200, 0.61, 'darkmetal', 'grip_checker')
g.cyl('x', (Y(108), Z(300)), 0.15, C - 0.54, C + 0.54, 'steel', seg=8, name='mag_catch')
box(398, 414, 60, 72, 0.2, 'steel', 'hammer_spur')
for side in (-1, 1):
    g.box((C + min(side * 0.5, side * 0.57), Y(86), Z(150)), (C + max(side * 0.5, side * 0.57), Y(58), Z(166)), 'darkmetal', name='front_notch')
box(368, 373, 44, 54, 0.31, 'rubber', 'sight_notch')
box(300, 330, 60, 66, 0.57, 'rubber', 'slide_mark')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.66, 0.66, 0.66]},
    "firstperson_lefthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.66, 0.66, 0.66]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [1, 1, 1]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
