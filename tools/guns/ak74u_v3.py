#!/usr/bin/env python3
"""AK-74u (BO1/BO2 wall buy): conical muzzle booster, tall front sight block, gas tube over a rounded
handguard, receiver with stamped top cover and hinged rear sight, big forward-curving magazine, raked
grip, skeleton stock folded along the left side. From the BO2 menu render (415 px -> 25 units)."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('ak74u_v3', offset=(0, 1, 5))
C = 8


def Z(px):
    return -15 + (px - 40) / 16.6


def Y(py):
    return 10 - (py - 25) / 16.6


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px0)), (C + hw, Y(py0), Z(px1)), mat, **kw)


BY = Y(65)
# Muzzle booster (cone), barrel, front sight block
g.cyl('z', (C, BY), 0.55, Z(40), Z(56), 'darkmetal', seg=8, name='booster_front')
g.cyl('z', (C, BY), 0.45, Z(56), Z(80), 'darkmetal', seg=8, name='booster_rear')
g.cyl('z', (C, BY), 0.38, Z(80), Z(160), 'steel', seg=8, name='barrel')
box(0.5, 50, 80, 112, 148, 'darkmetal', name='front_sight_block')
box(0.32, 8, 50, 120, 140, 'darkmetal', name='front_sight_ears')
box(0.1, 14, 40, 128, 132, 'steel', name='front_sight_post')

# Gas tube over the rounded handguard
g.cyl('z', (C, Y(33)), 0.62, Z(150), Z(300), 'darkmetal', seg=8, name='gas_tube')
box(0.85, 42, 96, 155, 300, 'gunmetal', name='handguard')
g.cyl('z', (C, Y(88)), 0.8, Z(158), Z(298), 'gunmetal', seg=8, name='handguard_belly')
box(0.88, 40, 46, 290, 302, 'steel', name='handguard_retainer')

# Receiver: body, top cover, hinged rear sight, rivets, selector lever, charging handle
box(0.8, 26, 100, 298, 452, 'darkmetal', name='receiver')
box(0.78, 20, 30, 300, 446, 'darkmetal', name='top_cover')
box(0.55, 12, 28, 296, 322, 'darkmetal', name='rear_sight_hinge')
for px in (330, 352, 430):
    g.cyl('x', (Y(80), Z(px)), 0.13, C - 0.85, C + 0.85, 'steel', seg=8, name='rivet')
box(0.86, 50, 58, 330, 420, 'steel', name='selector_lever')
g.box((C + 0.8, Y(45), Z(400)), (C + 1.3, Y(38), Z(412)), 'steel', name='charging_handle')

# Magazine: straight upper part under the magwell, lower part turned 22.5 degrees towards the muzzle
g.box((C - 0.6, 2.5, 1.0), (C + 0.6, 6.2, 5.4), 'darkmetal', name='magazine_upper')
LOWER = ('x', 22.5, (C, 2.5, 3.1))
g.box((C - 0.6, -2.4, 1.4), (C + 0.6, 2.5, 4.7), 'darkmetal', rot=LOWER, name='magazine')
for y in (-1.2, 0.4):
    g.box((C - 0.62, y, 1.45), (C + 0.62, y + 0.2, 4.65), 'gunmetal', rot=LOWER, name='mag_rib')

# Trigger guard, trigger, raked grip
box(0.22, 116, 122, 364, 412, 'gunmetal', name='guard_bottom')
box(0.1, 100, 116, 384, 390, 'steel', name='trigger')
box(0.6, 96, 178, 408, 452, 'polymer_dark', rot=('x', -22.5, (C, Y(98), Z(430))), name='grip')

# Skeleton stock folded along the left side of the receiver
g.box((C - 1.15, Y(72), Z(300)), (C - 0.82, Y(64), Z(456)), 'darkmetal', name='folded_stock_upper')
g.box((C - 1.15, Y(96), Z(300)), (C - 0.82, Y(88), Z(456)), 'darkmetal', name='folded_stock_lower')
g.box((C - 1.15, Y(96), Z(300)), (C - 0.82, Y(64), Z(312)), 'darkmetal', name='folded_butt')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.78, 0.84, Y(46), Y(30), Z(330), Z(400), 'rubber', name='ejection_port')
sb(-0.25, 0.25, 2.5, 3.2, 5.4, 6.4, 'steel', name='mag_catch')
g.cyl('x', (Y(33), Z(215)), 0.13, C - 0.66, C + 0.66, 'steel', seg=8, name='gas_tube_pin')
g.ring('x', (Y(84), Z(130)), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (Y(60), Z(455)), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
sb(-0.58, 0.58, Y(22), Y(16), Z(304), Z(312), 'steel', name='sight_slider')
for px in (42, 47, 52):
    sb(-0.58, 0.58, Y(70), Y(60), Z(px), Z(px + 2.5), 'rubber', name='booster_slot')
g.cyl('x', (Y(80), Z(306)), 0.15, C - 1.2, C - 0.7, 'steel', seg=8, name='stock_hinge_pin')

# Detail pass 2
g.ring('z', (C, BY), 0.38, 0.5, Z(100), Z(108), 'steel', name='barrel_gas_ring')
g.cyl('x', (Y(65), Z(130)), 0.12, C - 0.52, C + 0.52, 'steel', seg=8, name='sight_block_pin')
for s in (-1, 1):
    sb(s * 0.8, s * 0.86, Y(70), Y(66), Z(310), Z(440), 'steel', name='receiver_stamp_rib')
    for px in (185, 215, 245, 275):
        sb(s * 0.85, s * 0.9, Y(62), Y(52), Z(px), Z(px + 14), 'rubber', name='handguard_vent')
g.cyl('x', (Y(40), Z(445)), 0.12, C - 0.86, C + 0.86, 'steel', seg=8, name='trunnion_pin')
sb(-0.15, 0.15, Y(16), Y(16) + 0.2, Z(307), Z(309), 'rubber', name='sight_notch')
g.cyl('x', (Y(119), Z(400)), 0.12, C - 0.3, C + 0.3, 'steel', seg=8, name='guard_rivet')
g.box((C - 0.65, -2.7, 1.3), (C + 0.65, -2.4, 4.8), 'steel', rot=LOWER, name='mag_floorplate')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 2.5, -2], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"translation": [0, 2.5, -2], "scale": [0.6, 0.6, 0.6]},
    "firstperson_righthand": {"translation": [1, 1.5, -1], "scale": [0.75, 0.75, 0.75]},
    "firstperson_lefthand": {"translation": [1, 1.5, -1], "scale": [0.75, 0.75, 0.75]},
    "ground": {"translation": [0, 2, 0], "scale": [0.48, 0.48, 0.48]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.66, 0.66, 0.66]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
