#!/usr/bin/env python3
"""Spectre M4 (BO1): boxy receiver with cooling slots, short ventilated barrel shroud, folding stock
folded over the top, front handle, magazine in its housing, guard and raked rear grip. Fitted to the BO1
menu-icon side view (379 x 236 px -> 35 x 21.9 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('spectre_v3')
C = 8
S = 35.1 / 379


def Z(px):
    return -10.6 + px * S


def Y(py):
    return 17.75 - py * S


def box(x0, x1, y0, y1, hw, mat, name, cx=0.0, **kw):
    g.box((C + cx - hw, Y(y1), Z(x0)), (C + cx + hw, Y(y0), Z(x1)), mat, name=name, **kw)


# Receiver: upper body, lower body, short ventilated shroud with cooling slots
box(125, 379, 30, 64, 0.85, 'steel', 'upper_receiver')
box(190, 372, 62, 98, 0.85, 'darkmetal', 'lower_receiver')
box(5, 130, 38, 76, 0.85, 'gunmetal', 'barrel_shroud')
for y0 in (46, 64):
    box(25, 100, y0, y0 + 6, 0.87, 'rubber', 'cooling_slot')
box(200, 300, 40, 46, 0.87, 'darkmetal', 'cocking_slot')
g.cyl('z', (C, Y(56)), 0.22, Z(3), Z(8), 'rubber', seg=8, name='bore')

# Sights and the stock folded over the top
box(8, 46, 0, 40, 0.12, 'darkmetal', 'front_sight')
box(46, 118, 22, 40, 0.3, 'gunmetal', 'front_sight_hood')
box(118, 200, 10, 34, 0.3, 'gunmetal', 'sight_rail')
box(310, 352, 0, 34, 0.3, 'darkmetal', 'rear_sight')
box(190, 376, 14, 34, 0.5, 'darkmetal', 'folded_stock')
box(190, 214, 14, 50, 0.6, 'darkmetal', 'stock_hinge')

# Magazine housing and magazine
box(135, 192, 66, 136, 0.7, 'darkmetal', 'magazine_housing')
box(143, 184, 130, 236, 0.45, 'rust_brown', 'magazine')
for y0 in range(150, 230, 20):
    box(143, 184, y0, y0 + 4, 0.47, 'darkmetal', 'magazine_rib')

# Front handle, trigger guard, trigger, rear grip
box(64, 106, 60, 166, 0.5, 'polymer_dark', 'front_handle')
box(196, 214, 80, 120, 0.3, 'gunmetal', 'guard_front')
box(196, 268, 106, 120, 0.3, 'gunmetal', 'guard_bottom')
box(236, 248, 82, 106, 0.1, 'steel', 'trigger')
box(262, 335, 88, 114, 0.85, 'polymer_dark', 'grip_block')
REAR = ('x', -22.5, (C, Y(100), Z(293)))
box(262, 328, 80, 190, 0.6, 'polymer_dark', 'rear_grip', rot=REAR)
box(259, 331, 184, 192, 0.62, 'rubber', 'grip_base', rot=REAR)


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.84, 0.9, Y(34), Y(54), Z(230), Z(290), 'rubber', name='ejection_port')
sb(-0.84, -1.3, Y(38), Y(48), Z(215), Z(235), 'steel', name='cocking_handle')
for s in (-1, 1):
    sb(s * 0.84, s * 1.0, Y(70), Y(80), Z(330), Z(345), 'steel', name='selector')
for px in (205, 360):
    g.cyl('x', (Y(85), Z(px)), 0.14, C - 0.9, C + 0.9, 'steel', seg=8, name='pin')
g.ring('x', (Y(76) - 0.25, Z(15)), 0.22, 0.45, C - 0.12, C + 0.12, 'steel', name='front_swivel')
sb(0.7, 0.85, Y(110), Y(120), Z(170), Z(180), 'steel', name='mag_release')
box(141, 186, 226, 236, 0.5, 'steel', 'mag_baseplate')
sb(-0.1, 0.1, Y(24), Y(8), Z(352), Z(356), 'rubber', name='aperture')
for s in (-1, 1):
    sb(s * 0.12, s * 0.4, Y(14), Y(0), Z(10), Z(40), 'darkmetal', name='front_sight_ear')
    sb(s * 0.3, s * 0.5, Y(18), Y(0), Z(312), Z(350), 'darkmetal', name='rear_sight_ear')
    sb(s * 0.84, s * 0.9, Y(41), Y(43), Z(130), Z(379), 'darkmetal', name='receiver_line')
for y0 in (80, 100, 120, 140):
    box(68, 102, y0, y0 + 4, 0.53, 'rubber', 'handle_rib')
for y0 in (110, 130, 150):
    box(266, 324, y0, y0 + 4, 0.63, 'rubber', 'grip_rib', rot=REAR)
box(122, 134, 34, 80, 0.88, 'darkmetal', 'shroud_band')

# Detail pass 2
box(366, 379, 14, 34, 0.55, 'rubber', 'stock_cap')
box(5, 14, 36, 78, 0.9, 'steel', 'muzzle_ring')
for px in range(132, 190, 12):
    box(px, px + 3, 8, 10, 0.32, 'steel', 'rail_tick')
for px in (150, 345):
    g.cyl('x', (Y(52), Z(px)), 0.13, C - 0.9, C + 0.9, 'steel', seg=8, name='receiver_rivet')
g.cyl('x', (Y(100), Z(85)), 0.14, C - 0.55, C + 0.55, 'steel', seg=8, name='handle_screw')
g.cyl('x', (Y(100), Z(300)), 0.14, C - 0.9, C + 0.9, 'steel', seg=8, name='grip_screw')
for y0 in (160, 185, 210):
    box(150, 178, y0, y0 + 6, 0.47, 'rubber', 'mag_window')
box(140, 190, 128, 134, 0.72, 'steel', 'housing_collar')
box(196, 214, 118, 124, 0.32, 'steel', 'guard_joint')
box(40, 100, 72, 76, 0.87, 'darkmetal', 'shroud_lower_rail')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, -0.75, -0.75], "scale": [0.3, 0.3, 0.3]},
    "thirdperson_lefthand": {"translation": [0, -0.75, -0.75], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"translation": [8, -7.5, -15], "scale": [0.75, 0.75, 0.75]},
    "firstperson_lefthand": {"translation": [8, -7.5, -15], "scale": [0.75, 0.75, 0.75]},
    "ground": {"translation": [0, 0.25, 0], "scale": [0.3, 0.3, 0.3]},
    "gui": {"rotation": [90, -45, 90], "translation": [-0.25, -2.25, 0], "scale": [0.45, 0.45, 0.45]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -33.5, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [-0.5, 0, -6.75], "scale": [0.65, 0.65, 0.65]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
