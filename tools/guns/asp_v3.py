#!/usr/bin/env python3
"""ASP (BO1): blocky shortened slide with the Guttersnipe sight channel, recurved trigger guard,
translucent ivory grip panels with the brass cartridges visible through the windows. 15 units."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('asp_v3', offset=(0, -1, 12))
C = 8

def Z(px):
    return -12 + (px - 128) / 18


def Y(py):
    return 11 - (py - 30) / 18


def box(x0, x1, y0, y1, hw, mat, name, **kw):
    g.box((C - hw, Y(y1), Z(x0)), (C + hw, Y(y0), Z(x1)), mat, name=name, **kw)


# Slide: square block, sight channel, ejection port, bore
box(128, 386, 44, 92, 0.6, 'gunmetal', 'slide')
box(140, 380, 38, 46, 0.3, 'darkmetal', 'sight_channel')
g.box((C + 0.58, Y(70), Z(222)), (C + 0.63, Y(58), Z(272)), 'rubber', name='ejection_port')
box(128, 190, 78, 98, 0.62, 'darkmetal', 'slide_step')
g.cyl('z', (C, Y(70)), 0.22, Z(127), Z(131), 'rubber', seg=8, name='bore')
box(330, 386, 30, 48, 0.25, 'darkmetal', 'rear_block')

# Frame, trigger guard with recurved front, trigger, slide stop
box(160, 300, 90, 104, 0.52, 'darkmetal', 'frame')
g.wire([(C, Y(108), Z(210)), (C, Y(126), Z(214)), (C, Y(144), Z(236)), (C, Y(148), Z(262)),
        (C, Y(136), Z(288)), (C, Y(112), Z(298))], 0.3, 'darkmetal', name='trigger_guard')
box(244, 252, 98, 136, 0.08, 'steel', 'trigger')
box(235, 300, 80, 90, 0.56, 'darkmetal', 'slide_stop')

# Grip: one tall unrotated box buried in the frame, thin back strap hints at the rake; ivory panel with
# cartridge windows and base plate sit flush on it
GF, GB, GH, GS = (-10, 10, -18, 0)
GX1 = 298 + GF + 80 + GB
ROT = ('x', -22.5, (C, Y(90), Z(298 + GF + 40)))
box(298 + GF, GX1, 86, 236 + GH, 0.55, 'darkmetal', 'grip', rot=ROT)
box(306 + GF, GX1 - 8, 104, 224 + GH, 0.62, 'ivory', 'grip_panel', rot=ROT)
for py in (118, 134, 150, 166):
    box(312 + GF, 346 + GF, py, py + 10, 0.64, 'gold', 'cartridge', rot=ROT)
box(294 + GF, GX1 + 4, 234 + GH, 250 + GH, 0.5, 'steel', 'mag_base', rot=ROT)

# Details: mirrored ejection port, rear serrations, muzzle crown, slide stop pin, safety levers, front post
g.box((C - 0.63, Y(70), Z(222)), (C - 0.58, Y(58), Z(272)), 'rubber', name='ejection_port_left')
for px in range(300, 330, 8):
    box(px, px + 4, 52, 90, 0.62, 'rubber', 'serration')
g.ring('z', (C, Y(70)), 0.22, 0.42, Z(126), Z(131), 'steel', name='muzzle_crown')
g.cyl('x', (Y(85), Z(268)), 0.15, C - 0.62, C + 0.62, 'steel', seg=8, name='slide_stop_pin')
for side in (-1, 1):
    g.box((C + min(side * 0.6, side * 0.67), Y(76), Z(335)), (C + max(side * 0.6, side * 0.67), Y(60), Z(375)), 'steel', name='safety_lever')
box(135, 150, 34, 45, 0.08, 'led_red', 'front_post')

# Details pass 2: hammer, mag catch, slide marking, dust cover stud, grip screw, chamber insert
box(386, 408, 56, 86, 0.2, 'darkmetal', 'hammer')
g.cyl('x', (Y(112), Z(292)), 0.15, C - 0.57, C + 0.57, 'steel', seg=8, name='mag_catch')
box(150, 215, 50, 58, 0.62, 'steel', 'slide_marking')
box(140, 156, 92, 100, 0.55, 'steel', 'rail_stud')
box(238, 268, 60, 68, 0.64, 'steel', 'chamber_insert')
g.cyl('x', (Y(86), Z(160)), 0.14, C - 0.64, C + 0.64, 'steel', seg=8, name='takedown_pin')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [1, 1, 1]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
