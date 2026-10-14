#!/usr/bin/env python3
"""PM-63 RAK (BO1): long reciprocating slide with the muzzle-lip compensator over a short frame,
vertical folding foregrip, trigger guard, grip housing the magazine (base protruding), folded stock
along the frame. From the BO1 third-person render (215 px -> 20 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('pm63_v3', offset=(0, 0, 10))
C = 8


def Z(px):
    return -15 + (px - 35) / 22


def Y(py):
    return 10.5 - (py - 35) / 22


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px0)), (C + hw, Y(py0), Z(px1)), mat, **kw)


# Slide with muzzle tube and lip, serrations, ejection port, sights
box(0.7, 55, 90, 100, 500, 'gunmetal', name='slide')
box(0.3, 64, 78, 50, 104, 'steel', name='muzzle_tube')
box(0.5, 62, 80, 35, 52, 'steel', name='muzzle_lip')
g.cyl('z', (C, Y(71)), 0.25, Z(35), Z(37), 'rubber', seg=8, name='bore')
for px in (450, 464, 478, 492):
    box(0.72, 58, 88, px, px + 5, 'darkmetal', name='serration')
box(0.72, 62, 80, 300, 380, 'rubber', name='ejection_port')
box(0.12, 38, 58, 122, 142, 'darkmetal', name='front_sight')
box(0.35, 36, 58, 318, 345, 'darkmetal', name='rear_sight')

# Frame under the slide, with the rear hinge block
box(0.65, 88, 142, 90, 340, 'darkmetal', name='frame')
box(0.68, 88, 94, 90, 340, 'gunmetal', name='frame_rail')
box(0.62, 88, 112, 340, 500, 'darkmetal', name='frame_rear')
box(0.65, 90, 130, 440, 500, 'gunmetal', name='hinge_block')

# Vertical folding foregrip
box(0.45, 95, 240, 118, 150, 'darkmetal', name='foregrip')
box(0.5, 228, 242, 116, 152, 'steel', name='foregrip_tip')
g.cyl('x', (Y(125), Z(131)), 0.4, C - 0.5, C + 0.5, 'steel', seg=8, name='foregrip_pivot')

# Trigger guard and trigger
box(0.22, 148, 160, 255, 340, 'darkmetal', name='guard_bottom')
box(0.22, 128, 160, 250, 262, 'darkmetal', name='guard_front')
box(0.1, 128, 150, 293, 302, 'steel', name='trigger')

# Grip with magazine inside, base protruding
box(0.65, 92, 235, 330, 402, 'polymer_dark', name='grip')
box(0.55, 232, 248, 333, 399, 'darkmetal', name='mag_base')
for py in (150, 168, 186, 204):
    box(0.67, py, py + 7, 340, 380, 'rubber', name='grip_rib')

# Folded wire stock along the right side
g.box((C + 0.65, Y(106), Z(115)), (C + 0.85, Y(98), Z(450)), 'steel', name='folded_stock')
g.box((C + 0.65, Y(132), Z(448)), (C + 0.85, Y(96), Z(460)), 'steel', name='folded_butt')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(-0.65, -0.82, Y(108), Y(100), Z(300), Z(316), 'steel', name='safety')
g.cyl('x', (Y(125), Z(325)), 0.13, C - 0.7, C + 0.7, 'steel', seg=8, name='takedown_pin')
sb(-0.7, -0.95, Y(74), Y(62), Z(420), Z(440), 'steel', name='cocking_grip')
sb(-0.3, 0.3, Y(60), Y(52), Z(116), Z(150), 'darkmetal', name='front_sight_base')
sb(-0.1, 0.1, Y(48), Y(38), Z(345), Z(347.5), 'rubber', name='rear_notch')
g.cyl('x', (Y(165), Z(366)), 0.13, C - 0.7, C + 0.7, 'steel', seg=8, name='grip_screw')
for py in (120, 150, 180, 210):
    sb(-0.48, 0.48, Y(py + 8), Y(py), Z(118), Z(150), 'rubber', name='foregrip_rib')

# Detail pass 2
for px in (150, 190, 230):
    box(0.5, 96, 100, px, px + 3, 'darkmetal', name='frame_ridge')
sb(-0.72, 0.72, Y(64), Y(60), Z(36), Z(40), 'darkmetal', name='muzzle_collar')
g.cyl('x', (Y(75), Z(160)), 0.13, C - 0.72, C + 0.72, 'steel', seg=8, name='slide_pin')
g.cyl('x', (Y(75), Z(470)), 0.13, C - 0.72, C + 0.72, 'steel', seg=8, name='rear_pin')
sb(-0.5, 0.5, Y(237), Y(231), Z(118), Z(150), 'rubber', name='foregrip_cap')
sb(-0.3, 0.3, Y(95), Y(88), Z(118), Z(150), 'steel', name='foregrip_latch')
g.ring('x', (Y(110), Z(455)), 0.22, 0.42, C - 0.77, C - 0.65, 'steel', name='stock_loop')
sb(-0.68, 0.68, Y(242), Y(248), Z(336), Z(396), 'steel', name='mag_plate')
sb(0.65, 0.72, Y(120), Y(200), Z(335), Z(338), 'rubber', name='grip_edge')
sb(-0.3, 0.3, Y(56), Y(52), Z(480), Z(498), 'steel', name='rear_cap')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_righthand": {"translation": [1, 2.5, 0], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"translation": [1, 2.5, 0], "scale": [0.8, 0.8, 0.8]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [1, 1, 1]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
