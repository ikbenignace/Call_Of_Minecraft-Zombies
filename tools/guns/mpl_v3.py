#!/usr/bin/env python3
"""Walther MPL (BO1): long worn-steel receiver with a perforated barrel shroud, front sight post,
rear sight, magazine in the magwell ahead of the round trigger guard, raked grip with panels, wire stock
folded under the receiver. From the BO1 menu icon (205 px -> 21 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('mpl_v3', offset=(0, 0, 9))
C = 8


def Z(px):
    return -15 + (px - 120) / 23


def Y(py):
    return 10 - (py - 25) / 23


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px0)), (C + hw, Y(py0), Z(px1)), mat, **kw)


# Upper receiver with ventilated handguard
box(0.72, 62, 135, 395, 640, 'polymer_tan', name='receiver')
box(0.65, 64, 135, 140, 400, 'polymer_tan', name='shroud')
for px in range(185, 380, 32):
    box(0.67, 114, 126, px, px + 20, 'rubber', name='shroud_hole')
g.cyl('z', (C, Y(100)), 0.3, Z(130), Z(142), 'darkmetal', seg=8, name='muzzle')
box(0.1, 28, 80, 155, 185, 'darkmetal', name='front_sight')
box(0.4, 28, 80, 560, 615, 'darkmetal', name='rear_sight')
box(0.74, 68, 90, 220, 630, 'darkmetal', name='bolt_track')
g.box((C - 1.2, Y(92), Z(250)), (C - 0.72, Y(76), Z(275)), 'steel', name='cocking_knob')

# Lower receiver, magwell and straight magazine
box(0.7, 130, 215, 412, 600, 'polymer_tan', name='lower_receiver')
box(0.55, 205, 378, 455, 508, 'gunmetal', name='magazine')
box(0.58, 370, 382, 451, 512, 'darkmetal', name='mag_base')

# Round trigger guard, trigger
g.wire([(C, Y(140), Z(485)), (C, Y(160), Z(480)), (C, Y(190), Z(500)), (C, Y(205), Z(530)),
        (C, Y(195), Z(570)), (C, Y(165), Z(580)), (C, Y(140), Z(575))], 0.5, 'darkmetal', name='trigger_guard')
box(0.1, 150, 185, 540, 552, 'steel', name='trigger')

# Raked grip with panels
GRIP = ('x', -22.5, (C, Y(150), Z(600)))
box(0.62, 140, 290, 570, 650, 'polymer_tan', rot=GRIP, name='grip')
box(0.66, 170, 280, 580, 645, 'grip_ribs_dark', rot=GRIP, name='grip_panel')

# Folding stock arms along the rear top
g.box((C - 0.75, Y(130), Z(560)), (C - 0.6, Y(70), Z(676)), 'steel', name='stock_wire')
g.box((C + 0.6, Y(130), Z(560)), (C + 0.75, Y(70), Z(676)), 'steel', name='stock_wire')
g.box((C - 0.75, Y(135), Z(640)), (C + 0.75, Y(70), Z(676)), 'darkmetal', name='stock_butt')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.7, 0.77, Y(95), Y(115), Z(430), Z(500), 'rubber', name='ejection_port')
for s in (-1, 1):
    sb(s * 0.7, s * 0.88, Y(150), Y(158), Z(560), Z(580), 'steel', name='selector')
sb(0.7, 0.85, Y(150), Y(160), Z(436), Z(452), 'steel', name='mag_release')
g.ring('x', (Y(139), Z(190)), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='sling_swivel')
for s in (-1, 1):
    sb(s * 0.25, s * 0.35, Y(36), Y(64), Z(158), Z(182), 'darkmetal', name='sight_ear')
sb(-0.35, 0.35, Y(64), Y(56), Z(155), Z(185), 'darkmetal', name='sight_bridge')
for py in (240, 270, 300, 330):
    sb(-0.57, 0.57, Y(py + 5), Y(py), Z(458), Z(505), 'darkmetal', name='mag_rib')
sb(-0.15, 0.15, Y(60), Y(40), Z(615), Z(617), 'rubber', name='aperture')
for px in (425, 590):
    g.cyl('x', (Y(150), Z(px)), 0.12, C - 0.74, C + 0.74, 'steel', seg=8, name='pin')

# Detail pass 2
box(0.69, 135, 143, 140, 400, 'darkmetal', name='shroud_rail')
sb(-0.6, 0.6, Y(100) - 0.3, Y(100) + 0.3, Z(125), Z(130), 'steel', name='muzzle_ring')
for px in (420, 520, 600):
    sb(-0.73, 0.73, Y(66), Y(70), Z(px), Z(px + 3), 'steel', name='receiver_line')
sb(-0.5, 0.5, Y(215), Y(222), Z(412), Z(600), 'darkmetal', name='lower_flange')
sb(0.68, 0.8, Y(100), Y(112), Z(560), Z(575), 'steel', name='bolt_release')
sb(-0.55, 0.55, Y(236), Y(240), Z(455), Z(508), 'steel', name='mag_lip')
g.cyl('x', (Y(110), Z(300)), 0.12, C - 0.67, C + 0.67, 'steel', seg=8, name='shroud_pin')
g.ring('x', (Y(122), Z(650)), 0.2, 0.4, C + 0.7, C + 0.82, 'steel', name='stock_loop')
sb(-0.74, 0.74, Y(70), Y(76), Z(630), Z(640), 'steel', name='stock_hinge_cap')
sb(-0.4, 0.4, Y(36), Y(30), Z(560), Z(615), 'steel', name='rear_sight_top')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_righthand": {"translation": [1, 2.5, 0], "scale": [0.78, 0.78, 0.78]},
    "firstperson_lefthand": {"translation": [1, 2.5, 0], "scale": [0.78, 0.78, 0.78]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.78, 0.78, 0.78]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [1, 1, 1]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
