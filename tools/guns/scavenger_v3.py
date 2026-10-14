#!/usr/bin/env python3
"""Scavenger (BO1 "Call of the Dead"), traced from the 1147 px side view (24.4 px per unit): long knurled
suppressor with striped band, thin barrel, dotted forend, receiver with boxy scope housing and lens,
curved magazine angled forward, thumbhole skeleton stock with cheek line and butt pad. Muzzle -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('scavenger_v3', offset=(0, -1, -1))
C = 8
K = 24.4


def Z(px):
    return -15 + (px - 15) / K


def Y(py):
    return 12 - (py - 10) / K


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px0)), (C + hw, Y(py0), Z(px1)), mat, **kw)


SY = Y(58)
# Suppressor: tube, knurled body, striped band, end caps
g.cyl('z', (C, SY), 0.62, Z(15), Z(225), 'darkmetal', seg=16, name='suppressor')
for px in range(30, 215, 14):
    g.cyl('z', (C, SY), 0.66, Z(px), Z(px + 5), 'gunmetal', seg=16, name='knurl_ring')
for px in (128, 138, 148):
    g.box((C - 0.7, SY - 0.66, Z(px)), (C + 0.7, SY + 0.66, Z(px + 4)), 'steel', rot=('x', 22.5, (C, SY, Z(px + 2))), name='stripe')
g.cyl('z', (C, SY), 0.68, Z(15), Z(25), 'gunmetal', seg=16, name='front_cap')
g.cyl('z', (C, SY), 0.68, Z(215), Z(228), 'gunmetal', seg=16, name='rear_cap')

# Barrel, forend with dot vents, front sling post
g.cyl('z', (C, SY), 0.4, Z(225), Z(340), 'steel', seg=8, name='barrel')
box(0.7, 42, 108, 330, 525, 'polymer_dark', name='forend')
for px in range(352, 505, 36):
    box(0.72, 48, 54, px, px + 7, 'steel', name='forend_dot')
box(0.72, 98, 108, 330, 525, 'gunmetal', name='forend_lower')

# Receiver, rails, bolt
box(0.85, 30, 110, 520, 690, 'darkmetal', name='receiver')
box(0.87, 54, 60, 525, 685, 'gunmetal', name='receiver_rail')
g.box((C + 0.85, Y(66), Z(640)), (C + 1.2, Y(56), Z(655)), 'steel', name='bolt_handle')

# Scope housing with lens, top plate, mount, cable
box(0.95, -2, 46, 520, 660, 'gunmetal', name='scope_housing')
box(0.97, -4, 4, 540, 650, 'gunmetal', name='scope_top')
g.cyl('z', (C, Y(22)), 0.75, Z(516), Z(522), 'glass', seg=16, name='scope_lens')
g.cyl('z', (C, Y(22)), 0.85, Z(512), Z(518), 'darkmetal', seg=16, name='lens_rim')
box(0.97, 8, 16, 600, 640, 'label_white', name='scope_label')
g.wire([(C - 0.95, Y(30), Z(560)), (C - 0.95, Y(60), Z(580)), (C - 0.95, Y(70), Z(620))], 0.14, 'glow_blue', name='cable')

# Curved magazine angled forward
box(0.6, 108, 150, 540, 610, 'polymer_dark', name='mag_top')
box(0.6, 146, 210, 548, 614, 'polymer_dark', rot=('x', 22.5, (C, Y(148), Z(581))), name='mag_bottom')
for py in (125, 165, 190):
    box(0.62, py, py + 4, 552, 614, 'gunmetal', rot=('x', 22.5, (C, Y(148), Z(581))) if py > 150 else None, name='mag_rib')

# Trigger guard, trigger, thumbhole stock
box(0.22, 120, 126, 615, 680, 'gunmetal', name='guard_bottom')
box(0.1, 108, 122, 645, 651, 'steel', name='trigger')
box(0.8, 58, 106, 690, 860, 'polymer_dark', name='stock_top')
box(0.75, 92, 152, 680, 718, 'polymer_dark', rot=('x', -22.5, (C, Y(100), Z(699))), name='grip')
box(0.78, 132, 158, 715, 860, 'polymer_dark', rot=('x', -22.5, (C, Y(145), Z(715))), name='stock_bottom')
box(0.78, 100, 140, 826, 860, 'polymer_dark', name='stock_rear')
box(0.82, 60, 66, 700, 850, 'gunmetal', name='cheek_line')
box(0.85, 56, 140, 860, 880, 'rubber', name='butt_pad')

# Details: ejection port, muzzle bore, sling swivels, scope turrets, receiver screws, magazine release
box(0.88, 66, 84, 575, 630, 'rubber', name='ejection_port')
g.cyl('z', (C, SY), 0.3, Z(12.5), Z(15), 'rubber', seg=8, name='suppressor_bore')
g.ring('x', (Y(115), Z(360)), 0.18, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (Y(145), Z(840)), 0.18, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
g.cyl('y', (C, Z(600)), 0.3, Y(-4), Y(-4) + 0.35, 'steel', seg=8, name='elevation_turret')
for side in (-1, 1):
    g.cyl('x', (Y(20), Z(590)), 0.3, C + side * 0.9 if side > 0 else C - 1.2, C + 1.2 if side > 0 else C - 0.9, 'steel', seg=8, name='windage_turret')
for px in (540, 670):
    g.cyl('x', (Y(95), Z(px)), 0.12, C - 0.92, C + 0.92, 'steel', seg=8, name='receiver_screw')
box(0.66, 115, 125, 545, 558, 'steel', name='mag_release')

# --- detail pass 3 ---
for px in (372, 408, 444, 480):
    box(0.73, 60, 65, px, px + 5, 'glow_blue', name='forend_led')
box(0.72, 78, 92, 400, 480, 'rubber', name='forend_slot')
g.cyl('z', (C, SY), 0.52, Z(325), Z(336), 'gunmetal', seg=8, name='barrel_clamp')
for px in range(552, 650, 14):
    box(0.99, -6, -2, px, px + 5, 'steel', name='scope_tick')
box(0.62, 208, 214, 552, 614, 'steel', rot=('x', 22.5, (C, Y(148), Z(581))), name='mag_plate')
for py in (70, 90, 110, 130):
    box(0.86, py, py + 3, 862, 878, 'gunmetal', name='butt_groove')
box(0.97, 18, 28, 505, 514, 'darkmetal', name='lens_hood')
for side in (-1, 1):
    g.cyl('x', (Y(80), Z(705)), 0.18, *sorted((C + side * 0.76, C + side * 0.9)), 'steel', seg=8, name='takedown_pin')
    g.cyl('x', (Y(120), Z(845)), 0.15, *sorted((C + side * 0.76, C + side * 0.9)), 'steel', seg=8, name='stock_screw')
    for px in (40, 190):
        g.cyl('x', (SY, Z(px)), 0.16, *sorted((C + side * 0.6, C + side * 0.72)), 'steel', seg=8, name='suppressor_screw')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"translation": [1, 1, -2], "scale": [0.48, 0.48, 0.48]},
    "firstperson_lefthand": {"translation": [1, 1, -2], "scale": [0.48, 0.48, 0.48]},
    "ground": {"translation": [0, 2, 0], "scale": [0.32, 0.32, 0.32]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.38, 0.38, 0.38]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
