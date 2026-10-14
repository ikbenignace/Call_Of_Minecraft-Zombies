#!/usr/bin/env python3
"""MP5K (BO1): the approved MP5 receiver, grip and sights with a stubby barrel, short handguard with a
vertical foregrip, a straight 15-round magazine and an end cap instead of the stock.

Proportions measured from the BO2 side view: muzzle at z=-15, butt pad at z=13.8, receiver top at
y=10. 1 unit ≈ 2 cm of the real gun.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('mp5k', offset=(0, 0, 3))
C = 8


def Z(px):
    return -9 + (px - 135) / 15


def Y(py):
    return 14.5 - (py - 20) / 17


def box(x0, x1, y0, y1, hw, mat, name, **kw):
    g.box((C - hw, Y(y1), Z(x0)), (C + hw, Y(y0), Z(x1)), mat, name=name, **kw)


BY = Y(84)

# Barrel and tri-lug muzzle
g.cyl('z', (C, BY), 0.42, Z(148), Z(175), 'steel', seg=8, name='barrel')
g.cyl('z', (C, BY), 0.55, Z(135), Z(150), 'gunmetal', seg=8, name='muzzle_lugs')
for py in (72, 94):
    box(135, 146, py - 3, py + 3, 0.18, 'darkmetal', 'lug')

# Front sight: base block + hooded ring + post
box(140, 162, 48, 100, 0.75, 'darkmetal', 'front_sight_base')
g.ring('z', (C, Y(32)), 0.55, 0.9, Z(140), Z(158), 'gunmetal', name='front_sight_hood')
box(146, 152, 38, 48, 0.1, 'darkmetal', 'front_sight_post')

# Cocking tube above the barrel, cocking handle on the left
g.cyl('z', (C, Y(58)), 0.5, Z(160), Z(215), 'gunmetal', seg=8, name='cocking_tube')
g.box((C - 1.35, Y(66), Z(172)), (C - 0.9, Y(50), Z(186)), 'gunmetal', name='cocking_handle_arm')
g.cyl('x', (Y(58), Z(179)), 0.28, C - 2.0, C - 1.3, 'darkmetal', seg=8, name='cocking_knob')

# Handguard: slim polymer, rounded underside, front cap
box(160, 258, 64, 100, 1.25, 'polymer_dark', 'handguard')
g.cyl('z', (C, Y(90)), 0.75, Z(165), Z(250), 'polymer_dark', seg=8, name='handguard_belly')
box(156, 162, 62, 102, 1.3, 'gunmetal', 'handguard_cap')
for px in (185, 215):
    box(px, px + 10, 96, 100, 1.3, 'rubber', 'handguard_vent')

# Upper receiver: stamped body with rounded top, side flutes, front trunnion, claw-mount dimples
box(172, 392, 52, 100, 1.15, 'gunmetal', 'receiver')
g.cyl('z', (C, Y(62)), 0.95, Z(172), Z(392), 'gunmetal', seg=8, name='receiver_top')
box(152, 178, 46, 102, 1.25, 'gunmetal', 'trunnion')
box(200, 380, 70, 80, 1.2, 'rubber', 'side_flute')
box(200, 380, 68, 70, 1.21, 'steel', 'side_flute_edge')
for px in (190, 300):
    box(px, px + 8, 48, 52, 0.45, 'darkmetal', 'claw_dimple')
g.box((C + 1.14, Y(92), Z(215)), (C + 1.2, Y(76), Z(262)), 'rubber', name='ejection_port')

box(215, 345, 40, 54, 0.5, 'darkmetal', 'top_rail')

# Rear sight drum with protective ears
box(340, 380, 44, 52, 1.0, 'gunmetal', 'rear_sight_base')
g.cyl('x', (Y(36), Z(358)), 0.55, C - 0.75, C + 0.75, 'darkmetal', seg=8, name='rear_sight_drum')
for x0 in (C - 1.05, C + 0.75):
    g.box((x0, Y(46), Z(344)), (x0 + 0.3, Y(24), Z(372)), 'gunmetal', name='rear_sight_ear')

# Lower receiver / trigger group, selector, magwell, mag release
box(250, 395, 98, 134, 1.0, 'darkmetal', 'trigger_group')
box(270, 370, 104, 126, 1.06, 'gunmetal', 'trigger_group_panel')
box(350, 372, 108, 118, 1.2, 'steel', 'selector')
box(236, 278, 96, 140, 1.0, 'gunmetal', 'magwell')
box(262, 282, 132, 140, 0.4, 'darkmetal', 'mag_release')

# Straight magazine
box(240, 274, 138, 250, 0.65, 'darkmetal', 'magazine')
box(236, 278, 244, 252, 0.7, 'gunmetal', 'mag_floorplate')
box(246, 268, 150, 236, 0.67, 'gunmetal', 'mag_rib')

# Folded foregrip loop under the handguard
box(160, 172, 98, 178, 0.55, 'rubber', 'foregrip')
box(196, 208, 98, 178, 0.55, 'rubber', 'foregrip_rear')
box(160, 208, 166, 178, 0.55, 'gunmetal', 'foregrip_base')

# Trigger guard and trigger
box(290, 345, 144, 152, 0.4, 'gunmetal', 'guard_bottom')
box(290, 300, 132, 152, 0.4, 'gunmetal', 'guard_front')
box(312, 318, 134, 148, 0.15, 'steel', 'trigger')

# Pistol grip, raked back 45 degrees, with finger bump and stippled panels
GRIP = ('x', -22.5, (C, Y(130), Z(358)))
g.box((C - 0.8, Y(130) - 5.0, Z(358) - 1.1), (C + 0.8, Y(130) + 0.8, Z(358) + 1.1), 'polymer_dark', rot=GRIP, name='grip')
g.box((C - 0.7, Y(130) - 3.2, Z(358) - 1.35), (C + 0.7, Y(130) - 1.4, Z(358) - 1.1), 'polymer_dark', rot=GRIP, name='grip_finger_bump')
g.box((C - 0.7, Y(130) - 4.8, Z(358) + 1.1), (C + 0.7, Y(130) - 0.2, Z(358) + 1.35), 'polymer_dark', rot=GRIP, name='grip_backstrap')
g.box((C - 0.86, Y(130) - 4.4, Z(358) - 0.8), (C + 0.86, Y(130) - 0.8, Z(358) + 0.8), 'rubber', rot=GRIP, name='grip_stipple')
g.box((C - 0.85, Y(130) - 5.3, Z(358) - 1.3), (C + 0.85, Y(130) - 5.0, Z(358) + 1.3), 'gunmetal', rot=GRIP, name='grip_base')

# Push pins holding the trigger group
for px in (262, 380):
    g.cyl('x', (Y(106), Z(px)), 0.2, C - 1.12, C + 1.12, 'steel', seg=8, name='push_pin')

# End cap with sling loop instead of a stock
box(386, 400, 48, 104, 1.1, 'gunmetal', 'end_cap')
g.ring('x', (Y(70), Z(395)), 0.3, 0.5, C - 0.15, C + 0.15, 'steel', name='sling_loop')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
g.cyl('z', (C, BY), 0.28, -9.05, -8.8, 'rubber', seg=8, name='bore')
sb(-0.46, -0.53, Y(56), Y(60), Z(190), Z(212), 'rubber', name='cocking_slot')
g.cyl('x', (Y(36), Z(358)), 0.12, C - 1.1, C + 1.1, 'steel', seg=8, name='sight_axle')
g.cyl('x', (Y(80), Z(240)), 0.15, C - 1.31, C + 1.31, 'steel', seg=8, name='handguard_pin')
for py in (60, 90):
    g.cyl('x', (Y(py), Z(393)), 0.13, C - 1.15, C + 1.15, 'steel', seg=8, name='end_cap_screw')
for px in range(225, 340, 18):
    sb(-0.52, 0.52, Y(47), Y(41), Z(px), Z(px + 6), 'rubber', name='rail_slot')

# Detail pass 2
g.ring('z', (C, Y(58)), 0.5, 0.6, Z(160), Z(166), 'steel', name='cocking_tube_collar')
g.ring('z', (C, Y(58)), 0.5, 0.6, Z(210), Z(216), 'steel', name='cocking_tube_end')
g.cyl('x', (Y(80), Z(170)), 0.15, C - 1.31, C + 1.31, 'steel', seg=8, name='handguard_front_pin')
for py in (160, 182, 204):
    box(254, 260, py, py + 6, 0.68, 'rubber', 'mag_witness_hole')
box(300, 306, 56, 98, 1.16, 'rubber', 'receiver_groove')
g.cyl('x', (Y(120), Z(258)), 0.18, C - 1.02, C + 1.02, 'steel', seg=8, name='mag_catch_button')
sb(-0.9, 0.9, Y(44), Y(40), Z(215), Z(222), 'steel', name='rail_stop')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"translation": [1, 2, -1.5], "scale": [0.65, 0.65, 0.65]},
    "firstperson_lefthand": {"translation": [1, 2, -1.5], "scale": [0.65, 0.65, 0.65]},
    "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
