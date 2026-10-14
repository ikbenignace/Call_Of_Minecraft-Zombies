#!/usr/bin/env python3
"""FAMAS (BO1/BO2 zombies): bullpup with a big blocky butt and dark cheek rest, long carry handle with the
cocking lever inside it, finger-grooved handguard, white front cap, ribbed flash hider, magazine behind the
finger-grooved grip. Measured from the BO1 third-person render at 30.3 px per unit; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('famas_v3', offset=(0, -3.5, 3))
C = 8


def Z(px):
    return -15 + (1045 - px) / 30.3


def Y(py):
    return 10 - (py - 110) / 30.3


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px1)), (C + hw, Y(py0), Z(px0)), mat, **kw)


BY = Y(99)
# Flash hider, barrel, front cap
g.cyl('z', (C, BY), 0.42, Z(1045), Z(980), 'darkmetal', seg=8, name='flash_hider')
for px in (990, 1005, 1020):
    box(0.44, 92, 106, px, px + 5, 'rubber', name='hider_rib')
g.cyl('z', (C, BY), 0.25, Z(980), Z(880), 'gunmetal', seg=8, name='barrel')
g.cyl('z', (C, Y(100)), 0.55, Z(885), Z(800), 'polymer_white', seg=8, name='front_cap')

# Body: upper receiver line and finger-grooved handguard
box(0.9, 108, 130, 395, 890, 'polymer_white', name='receiver')
box(0.85, 128, 165, 575, 860, 'polymer_white', name='handguard')
for px in (610, 660, 710, 760, 810):
    box(0.88, 156, 168, px, px + 22, 'polymer_dark', name='handguard_groove')
box(0.92, 118, 126, 580, 840, 'polymer_dark', name='handguard_seam')
g.cyl('x', (Y(146), Z(730)), 0.22, C - 0.92, C + 0.92, 'steel', seg=8, name='takedown_pin')

# Lower body joining receiver, grip, magwell and handguard
box(0.85, 126, 192, 395, 585, 'polymer_white', name='lower_body')

# Carry handle: rear post, front post, top rail, cocking lever, pivots
box(0.55, 36, 112, 400, 445, 'gunmetal', name='handle_rear_post')
box(0.5, 40, 112, 735, 800, 'gunmetal', name='handle_front_post')
box(0.4, 32, 50, 410, 760, 'gunmetal', name='handle_rail')
box(0.22, 86, 98, 430, 800, 'darkmetal', name='cocking_lever')
g.cyl('x', (Y(92), Z(780)), 0.32, C - 0.5, C + 0.5, 'steel', seg=8, name='lever_pivot')
g.cyl('x', (Y(78), Z(438)), 0.3, C - 0.5, C + 0.5, 'steel', seg=8, name='handle_pivot')
box(0.2, 20, 36, 700, 720, 'darkmetal', name='front_sight')
box(0.3, 24, 40, 420, 440, 'darkmetal', name='rear_sight')

# Grip with finger grooves, raked slightly back; trigger and guard
GRIP = ('x', 0, (C, Y(185), Z(510)))
box(0.62, 180, 318, 468, 552, 'polymer_dark', rot=GRIP, name='grip')
for py in (210, 245, 280):
    box(0.58, py, py + 12, 545, 560, 'polymer_dark', rot=GRIP, name='grip_finger')
box(0.18, 205, 214, 545, 640, 'darkmetal', name='guard_bottom')
box(0.18, 168, 214, 630, 640, 'darkmetal', name='guard_front')
box(0.1, 170, 196, 585, 595, 'steel', name='trigger')

# Magazine behind the grip, slight forward cant
MAG = ('x', 0, (C, Y(205), Z(338)))
box(0.5, 205, 400, 292, 384, 'gunmetal', rot=MAG, name='magazine')
box(0.52, 300, 330, 292, 384, 'polymer_white', rot=MAG, name='mag_tape')
box(0.6, 192, 214, 285, 395, 'polymer_white', name='magwell')

# Butt: blocky stock with dark cheek rest and recoil pad
box(1.05, 90, 272, 50, 300, 'polymer_white', name='butt')
box(1.0, 215, 272, 160, 300, 'polymer_white', name='butt_belly')
box(1.08, 82, 160, 228, 395, 'polymer_dark', name='cheek_rest')
box(1.0, 140, 175, 300, 400, 'polymer_white', name='butt_neck')
box(1.1, 90, 272, 45, 72, 'rubber', name='butt_pad')
box(1.09, 110, 118, 230, 380, 'steel', name='cheek_tape')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
for s in (-1, 1):
    sb(s * 0.88, s * 0.93, Y(112), Y(124), Z(520), Z(600), 'rubber', name='ejection_port')
    sb(s * 0.58, s * 0.7, Y(196), Y(206), Z(350), Z(368), 'steel', name='mag_release')
for py in (120, 245):
    g.cyl('x', (Y(py), Z(58)), 0.15, C - 1.15, C + 1.15, 'steel', seg=8, name='butt_screw')
g.ring('x', (Y(171), Z(800)), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (Y(278), Z(220)), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
for px in (500, 560, 620, 680):
    for s in (-1, 1):
        sb(s * 0.38, s * 0.43, Y(36), Y(46), Z(px + 12), Z(px), 'rubber', name='rail_slot')

g.cyl('z', (C, Y(100)), 0.62, Z(850), Z(838), 'darkmetal', seg=8, name='cap_ring')
g.cyl('z', (C, BY), 0.34, Z(930), Z(910), 'gunmetal', seg=8, name='gas_block')
for s in (-1, 1):
    for px in (620, 790):
        g.cyl('x', (Y(146), Z(px)), 0.15, C + s * 0.8, C + s * 0.92, 'steel', seg=8, name='handguard_screw')
    for px in (260, 360):
        g.cyl('x', (Y(130), Z(px)), 0.15, C + s * 1.0, C + s * 1.14, 'steel', seg=8, name='cheek_rivet')
for py in (230, 244, 258):
    for s in (-1, 1):
        sb(s * 1.03, s * 1.09, Y(py), Y(py + 5), Z(80), Z(280), 'rubber', name='butt_ridge')
g.cyl('x', (Y(190), Z(625)), 0.14, C - 0.22, C + 0.22, 'steel', seg=8, name='guard_pin')
box(0.3, 82, 100, 425, 450, 'steel', name='cocking_knob')
box(0.32, 22, 34, 698, 702, 'darkmetal', name='sight_ear')
box(0.32, 22, 34, 718, 722, 'darkmetal', name='sight_ear')
box(0.36, 22, 26, 416, 446, 'darkmetal', name='rear_hood')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.62, 0.62, 0.62]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.62, 0.62, 0.62]},
    "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.52, 0.52, 0.52]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
