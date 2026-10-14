#!/usr/bin/env python3
"""CZ75 (BO1): worn steel slide riding inside the frame rails with a slimmer front, rear serrations,
rounded trigger guard, black ribbed grip panels on a raked grip with beavertail, exposed hammer.
Measured from the BO1 third-person render at 27.35 px per unit; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('cz75_v3', offset=(0, -1.5, 13))
C = 8


def Z(px):
    return -15 + (px - 100) / 19.5


def Y(py):
    return 10 - (py - 32) / 19.5


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px0)), (C + hw, Y(py0), Z(px1)), mat, **kw)


# Slide: slimmer front section, full rear section, serrations, ejection port, sights
box(0.5, 34, 84, 100, 218, 'worn_steel', name='slide_front')
box(0.58, 32, 90, 216, 386, 'worn_steel', name='slide')
for px in range(326, 378, 10):
    box(0.6, 40, 88, px, px + 5, 'darkmetal', name='serration')
box(0.6, 46, 64, 245, 305, 'darkmetal', name='ejection_port')
g.cyl('z', (C, Y(62)), 0.22, Z(99), Z(103), 'rubber', seg=8, name='muzzle_bore')
box(0.12, 28, 42, 108, 122, 'darkmetal', name='front_sight')
box(0.4, 26, 40, 358, 380, 'darkmetal', name='rear_sight')

# Frame: dust cover under the slide front, frame rails, slide stop, takedown pin
box(0.62, 86, 92, 104, 250, 'darkmetal', name='dust_cover')
box(0.64, 86, 114, 215, 392, 'darkmetal', name='frame')
box(0.66, 74, 84, 215, 268, 'steel', name='slide_stop')
g.cyl('x', (Y(79), Z(222)), 0.18, C - 0.7, C + 0.7, 'steel', seg=8, name='slide_stop_pin')

# Rounded trigger guard (connected chain) and trigger
g.wire([(C, Y(106), Z(246)), (C, Y(124), Z(238)), (C, Y(138), Z(246)), (C, Y(148), Z(268)),
        (C, Y(144), Z(292)), (C, Y(130), Z(308)), (C, Y(108), Z(312))], 0.42, 'darkmetal', name='trigger_guard')
box(0.12, 104, 136, 264, 278, 'steel', rot=('x', -22.5, (C, Y(120), Z(271))), name='trigger')

# Grip: one tall unrotated box buried in the frame, thin back strap hints at the rake; ribbed panel,
# screws and magazine base plate sit flush on it
GF, GB, GH, GS = (38, 6, 12, 20)
GX1 = 312 + GF + 70 + GB
box(0.6, 100, 236 + GH, 312 + GF, GX1, 'darkmetal', name='grip')
if GS:
    box(0.55, 170, 236 + GH, GX1, GX1 + GS, 'darkmetal', name='grip_backstrap')
box(0.66, 125, 222 + GH, 322 + GF, GX1 - 8, 'grip_ribs_black', name='grip_panel')
g.cyl('x', (Y(145), Z((312 + GX1) / 2)), 0.12, C - 0.7, C + 0.7, 'steel', seg=8, name='grip_screw')
g.cyl('x', (Y(205 + GH), Z((312 + GX1) / 2)), 0.12, C - 0.7, C + 0.7, 'steel', seg=8, name='grip_screw')
box(0.62, 234 + GH, 248 + GH, 308 + GF, GX1 + GS + 4, 'rubber', name='mag_base')
box(0.5, 84, 116, 372, 402, 'darkmetal', name='beavertail')

# Hammer
box(0.22, 52, 100, 392, 426, 'darkmetal', rot=('x', 22.5, (C, Y(95), Z(400))), name='hammer')

# Details: front serrations, dust cover slots, magazine release, thumb safety, sight dot, muzzle crown
for px in range(120, 170, 10):
    box(0.52, 40, 80, px, px + 5, 'darkmetal', name='front_serration')
for px in (130, 160, 190):
    box(0.64, 87, 92, px, px + 8, 'rubber', name='rail_slot')
g.cyl('x', (Y(108), Z(325)), 0.18, C - 0.76, C + 0.76, 'steel', seg=8, name='mag_release')
box(0.7, 62, 80, 335, 370, 'steel', name='thumb_safety')
box(0.13, 29, 37, 112, 118, 'label_white', name='sight_dot')
g.ring('z', (C, Y(62)), 0.22, 0.42, Z(98), Z(103), 'steel', name='muzzle_crown')

# Details pass 2: chamber insert, extractor, grip grooves, trigger pin, hammer rowel, rear notch
box(0.61, 52, 60, 262, 296, 'steel', name='chamber_insert')
box(0.62, 48, 58, 300, 312, 'steel', name='extractor')
for py in (160, 178, 196):
    box(0.68, py, py + 3, 330, 372, 'rubber', name='grip_groove')
g.cyl('x', (Y(100), Z(258)), 0.12, C - 0.67, C + 0.67, 'steel', seg=8, name='trigger_pin')
for py in (60, 70, 80):
    box(0.23, py, py + 3, 400, 418, 'rubber', rot=('x', 22.5, (C, Y(95), Z(400))), name='hammer_rowel')
box(0.41, 30, 36, 366, 372, 'rubber', name='rear_notch')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [1, 1, 1]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
