#!/usr/bin/env python3
"""HK21 (BO1): G3-family belt/box LMG: long tan slotted handguard, hooded front sight on the cocking tube,
deployed bipod, black receiver with drum rear sight, straight box magazine, black grip, fixed tan stock
with recoil pad. 42 units; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('hk21_v3', offset=(0, 1, -1))
C = 8


def Y(v):
    return v - 1


def Z(v):
    return v + 1


# Flash hider, barrel, cocking tube, hooded front sight, bipod
g.cyl('z', (C, Y(10.65)), 0.55, Z(-16), Z(-13.5), 'gunmetal', seg=8, name='flash_hider')
g.cyl('z', (C, Y(10.65)), 0.4, Z(-13.5), Z(-9.5), 'steel', seg=8, name='barrel')
g.cyl('z', (C, Y(12.1)), 0.5, Z(-12.0), Z(2.0), 'gunmetal', seg=8, name='cocking_tube')
g.box((C - 0.6, Y(10.4), Z(-13.5)), (C + 0.6, Y(12.2), Z(-11.5)), 'darkmetal', name='front_sight_base')
g.ring('z', (C, Y(12.2)), 0.45, 0.7, Z(-13.4), Z(-11.6), 'darkmetal', name='front_hood')
g.box((C - 1.25, Y(11.9), Z(-10.0)), (C - 0.5, Y(12.5), Z(-9.2)), 'steel', name='cocking_handle')
g.box((C - 0.5, Y(9.0), Z(-11.8)), (C + 0.5, Y(9.9), Z(-10.2)), 'darkmetal', name='bipod_mount')
for side in (-1, 1):
    x0 = C + side * 0.5
    g.wire([(x0, Y(9.3), Z(-11.5)), (x0, Y(3.9), Z(-14.0))], 0.22, 'gunmetal', name='bipod_leg_front')
    g.wire([(x0, Y(9.0), Z(-10.5)), (x0, Y(4.5), Z(-7.0))], 0.22, 'gunmetal', name='bipod_leg_rear')

# Long tan handguard with slots
g.box((C - 0.95, Y(9.9), Z(-12.5)), (C + 0.95, Y(12.0), Z(-1.0)), 'polymer_tan', name='handguard')
g.box((C - 0.95, Y(8.9), Z(-7.0)), (C + 0.95, Y(9.9), Z(-2.0)), 'polymer_tan', name='handguard_chin')
for z in (-11.0, -9.0, -7.0, -5.0, -3.0):
    g.box((C - 0.97, Y(10.4), Z(z)), (C + 0.97, Y(11.4), Z(z + 1.2)), 'rubber', name='handguard_slot')

# Receiver, drum rear sight, magwell, magazine
g.box((C - 0.85, Y(7.9), Z(-2.0)), (C + 0.85, Y(11.6), Z(10.0)), 'darkmetal', name='receiver')
g.cyl('z', (C, Y(12.0)), 0.85, Z(-1.0), Z(9.5), 'darkmetal', seg=8, name='receiver_top')
g.box((C - 0.9, Y(12.1), Z(-0.8)), (C + 0.9, Y(12.45), Z(8.8)), 'gunmetal', name='flute')
g.cyl('x', (Y(11.9), Z(9.2)), 0.6, C - 0.6, C + 0.6, 'darkmetal', seg=8, name='rear_drum')
g.box((C - 0.9, Y(11.2), Z(8.4)), (C - 0.6, Y(12.4), Z(9.9)), 'darkmetal', name='drum_ear')
g.box((C + 0.6, Y(11.2), Z(8.4)), (C + 0.9, Y(12.4), Z(9.9)), 'darkmetal', name='drum_ear')
g.box((C - 0.82, Y(6.4), Z(-2.0)), (C + 0.82, Y(7.9), Z(1.8)), 'darkmetal', name='magwell')
g.box((C - 0.66, Y(4.1), Z(-1.8)), (C + 0.66, Y(6.5), Z(1.6)), 'gunmetal', name='magazine')
g.box((C - 0.7, Y(3.9), Z(-1.9)), (C + 0.7, Y(4.2), Z(1.7)), 'darkmetal', name='mag_base')

# Trigger group, grip
g.box((C - 0.75, Y(6.4), Z(-2.0)), (C + 0.75, Y(7.9), Z(7.8)), 'darkmetal', name='trigger_group')
g.box((C - 0.1, Y(5.2), Z(3.6)), (C + 0.1, Y(6.4), Z(3.9)), 'steel', name='trigger')
g.box((C - 0.68, Y(4.0), Z(5.3)), (C + 0.68, Y(6.5), Z(7.8)), 'polymer_dark', name='grip')

# Fixed tan stock
g.box((C - 0.8, Y(7.4), Z(9.5)), (C + 0.8, Y(9.9), Z(12.0)), 'polymer_tan', name='stock_front')
g.box((C - 0.85, Y(4.4), Z(12.0)), (C + 0.85, Y(9.9), Z(14.5)), 'polymer_tan', name='stock_mid')
g.box((C - 0.85, Y(3.4), Z(14.5)), (C + 0.85, Y(9.9), Z(20.0)), 'polymer_tan', name='stock_butt')
g.box((C - 0.83, Y(2.4), Z(17.5)), (C + 0.83, Y(3.5), Z(20.0)), 'polymer_tan', name='stock_heel')
g.box((C - 0.9, Y(2.4), Z(20.0)), (C + 0.9, Y(9.9), Z(20.2)), 'rubber', name='recoil_pad')
g.box((C - 0.87, Y(6.6), Z(15.0)), (C + 0.87, Y(7.4), Z(16.6)), 'polymer_dark', name='stock_mark')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.83, 0.9, Y(9.0), Y(10.6), Z(3.0), Z(6.5), 'rubber', name='ejection_port')
for s in (-1, 1):
    sb(s * 0.75, s * 0.92, Y(7.0), Y(7.6), Z(6.6), Z(7.4), 'steel', name='selector')
sb(0.82, 0.95, Y(6.6), Y(7.2), Z(1.4), Z(2.2), 'steel', name='mag_release')
g.ring('x', (Y(8.7), Z(-4.5)), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (Y(2.2), Z(18.8)), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
g.cyl('x', (Y(11.9), Z(9.2)), 0.15, C - 0.95, C + 0.95, 'steel', seg=8, name='sight_axle')
for z in (-15.4, -14.7, -14.0):
    sb(-0.57, 0.57, Y(10.65) - 0.12, Y(10.65) + 0.12, Z(z), Z(z + 0.35), 'rubber', name='hider_slot')
for z in (-12.0, -1.5):
    g.cyl('x', (Y(11.0), Z(z)), 0.13, C - 1.0, C + 1.0, 'steel', seg=8, name='handguard_pin')
for y, z in ((8.5, 19.0), (4.5, 16.5)):
    g.cyl('x', (Y(y), Z(z)), 0.13, C - 0.91, C + 0.91, 'steel', seg=8, name='stock_screw')

# Detail pass 2
for z in (-3.4, -2.6, -1.8):
    sb(-0.96, 0.96, Y(9.95), Y(10.1), Z(z), Z(z + 0.35), 'darkmetal', name='handguard_groove')
sb(-0.3, 0.3, Y(12.45), Y(12.8), Z(8.0), Z(8.6), 'steel', name='rear_sight_post')
sb(-0.6, 0.6, Y(10.2), Y(10.6), Z(-9.5), Z(-9.1), 'steel', name='barrel_nut')
g.ring('z', (C, Y(10.65)), 0.4, 0.55, Z(-12.8), Z(-12.3), 'darkmetal', name='barrel_collar')
sb(-0.4, 0.4, Y(11.9), Y(12.15), Z(-12.6), Z(-12.2), 'steel', name='sight_pin')
sb(-0.68, 0.68, Y(4.1), Y(4.3), Z(-1.9), Z(1.7), 'steel', name='mag_lip')
for s in (-1, 1):
    sb(s * 0.66, s * 0.72, Y(4.6), Y(6.0), Z(-1.0), Z(-0.6), 'darkmetal', name='mag_notch')
    sb(s * 0.85, s * 0.92, Y(9.3), Y(11.0), Z(0.0), Z(0.5), 'darkmetal', name='receiver_seam')
sb(-0.68, 0.68, Y(6.7), Y(7.0), Z(5.3), Z(7.8), 'rubber', name='grip_edge')
sb(-0.88, 0.88, Y(7.8), Y(8.0), Z(10.0), Z(12.0), 'steel', name='stock_plate')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.42, 0.42, 0.42]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.42, 0.42, 0.42]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.52, 0.52, 0.52]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.52, 0.52, 0.52]},
    "ground": {"translation": [0, 2, 0], "scale": [0.34, 0.34, 0.34]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
