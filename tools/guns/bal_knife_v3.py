#!/usr/bin/env python3
"""Ballistic Knife (BO1/BO2): spring-launched knife. Broad black spear-point blade with a row of slots
and a fuller, steel guard, then a skeletonised launcher handle (two rails with finger cut-outs), a
release lever on top and a spring cap at the heel. Fitted to the BO2 pick-up icon (520 px -> 22 units);
muzzle (blade tip) towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('bal_knife_v3', offset=(0, -4.6, 7.2))
C = 8
S = 22 / 520


def Z(px):
    return -11 + (px - 10) * S


def Y(py):
    return 13 - (py - 90) * S


# Blade: stepped spear point (wider towards the guard), fuller and slots
BH = 0.18
for i in range(8):
    z0, z1 = 10 + i * 15, 25 + i * 15
    t = (i + 0.5) / 8
    mat = 'steel' if i < 2 else 'darkmetal'
    g.box((C - BH, Y(140 + 18 * t), Z(z0)), (C + BH, Y(132 - 20 * t), Z(z1)), mat, name='blade_taper')
g.box((C - BH, Y(158), Z(130)), (C + BH, Y(112), Z(290)), 'darkmetal', name='blade')
g.box((C - BH - 0.02, Y(122), Z(120)), (C + BH + 0.02, Y(116), Z(285)), 'steel', name='blade_spine_edge')
g.box((C - BH - 0.02, Y(158), Z(40)), (C + BH + 0.02, Y(150), Z(285)), 'steel', name='cutting_edge')
g.box((C - BH - 0.01, Y(150), Z(60)), (C + BH + 0.01, Y(145), Z(285)), 'gunmetal', name='edge_bevel')
for px in (175, 200, 225, 250):
    g.box((C - BH - 0.03, Y(140), Z(px)), (C + BH + 0.03, Y(130), Z(px + 14)), 'rubber', name='blade_slot')
# Guard
g.box((C - 0.45, Y(172), Z(290)), (C + 0.45, Y(108), Z(304)), 'gunmetal', name='guard')
# Launcher handle: upper and lower rails joined by posts, finger cut-outs between them
g.box((C - 0.5, Y(124), Z(304)), (C + 0.5, Y(110), Z(512)), 'polymer_dark', name='handle_top_rail')
g.box((C - 0.5, Y(190), Z(304)), (C + 0.5, Y(172), Z(512)), 'polymer_dark', name='handle_bottom_rail')
for px in (304, 375, 445, 498):
    g.box((C - 0.48, Y(176), Z(px)), (C + 0.48, Y(120), Z(px + 14)), 'polymer_dark', name='handle_post')
g.box((C - 0.14, Y(158), Z(318)), (C + 0.14, Y(150), Z(498)), 'gunmetal', name='launch_rod')
for px in (330, 360, 395, 425, 460):
    g.box((C - 0.32, Y(160), Z(px)), (C + 0.32, Y(150), Z(px + 10)), 'steel', name='spring_coil')
# Release lever on top, spring cap and heel
g.box((C - 0.15, Y(110), Z(440)), (C + 0.15, Y(97), Z(490)), 'steel', name='release_lever')
g.box((C - 0.55, Y(192), Z(498)), (C + 0.55, Y(108), Z(520)), 'gunmetal', name='heel_cap')
g.box((C - 0.12, Y(186), Z(520)), (C + 0.12, Y(176), Z(526)), 'led_red', name='heel_light')

# Details: fuller grooves, guard screw, handle rivets, lever pivot, rail grip ribs, lanyard ring
for a, b in ((135, 170), (264, 285)):
    g.box((C - BH - 0.03, Y(129), Z(a)), (C + BH + 0.03, Y(126), Z(b)), 'rubber', name='fuller')
g.cyl('x', (Y(140), Z(297)), 0.15, C - 0.5, C + 0.5, 'steel', seg=8, name='guard_screw')
for px in (375, 445, 498):
    g.cyl('x', (Y(148), Z(px + 7)), 0.12, C - 0.56, C + 0.56, 'steel', seg=8, name='handle_rivet')
g.cyl('x', (Y(104), Z(450)), 0.12, C - 0.22, C + 0.22, 'gold', seg=8, name='lever_pivot')
for px in (330, 355, 380, 405):
    g.box((C - 0.52, Y(123), Z(px)), (C + 0.52, Y(111), Z(px + 8)), 'rubber', name='grip_rib')
g.ring('x', (Y(165), Z(528)), 0.15, 0.4, C - 0.12, C + 0.12, 'steel', name='lanyard_ring')

# Reference detail: carbon top-rail panel, corner pins, guard lug, finger scallop liners, heel stripe, blade bevel
g.box((C - 0.53, Y(122), Z(330)), (C + 0.53, Y(113), Z(440)), 'gunmetal', name='top_carbon_panel')
for px in (322, 470):
    g.cyl('x', (Y(117), Z(px)), 0.11, C - 0.54, C + 0.54, 'steel', seg=8, name='top_pin')
for px in (322, 470):
    g.cyl('x', (Y(181), Z(px)), 0.11, C - 0.54, C + 0.54, 'steel', seg=8, name='bottom_pin')
g.box((C - 0.4, Y(190), Z(290)), (C + 0.4, Y(172), Z(304)), 'steel', name='guard_lug')
for px in (335, 405):
    g.box((C - 0.52, Y(176), Z(px)), (C + 0.52, Y(170), Z(px + 40)), 'rubber', name='scallop_liner')
g.box((C - 0.56, Y(186), Z(500)), (C + 0.56, Y(176), Z(512)), 'led_red', name='heel_stripe')
g.box((C - BH - 0.03, Y(150), Z(120)), (C + BH + 0.03, Y(146), Z(164)), 'steel', name='serration')
g.box((C - 0.17, Y(114), Z(300)), (C + 0.17, Y(108), Z(330)), 'steel', name='guard_ramp')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
    "firstperson_righthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.45, 0.45, 0.45]},
    "firstperson_lefthand": {"translation": [1.25, 1.25, -3.5], "scale": [0.45, 0.45, 0.45]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [1, 1, 1]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
