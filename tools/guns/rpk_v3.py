#!/usr/bin/env python3
"""RPK (BO1): long barrel with a deployed bipod near the muzzle, front sight, wooden upper and lower
handguard, AK-pattern receiver with top cover and rear sight, orange curved magazine, wooden grip, black
club-foot stock. 42 units; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('rpk_v3', offset=(0, 1, -1))
C = 8


def Y(v):
    return v - 1


def Z(v):
    return v + 1


# Barrel, muzzle, front sight, bipod (deployed)
g.cyl('z', (C, Y(10.4)), 0.55, Z(-15), Z(1.6), 'gunmetal', seg=8, name='barrel')
g.cyl('z', (C, Y(10.4)), 0.7, Z(-15), Z(-14.0), 'darkmetal', seg=8, name='muzzle')
g.box((C - 0.35, Y(10.9), Z(-14.0)), (C + 0.35, Y(11.8), Z(-12.5)), 'darkmetal', name='front_sight')
g.box((C - 0.45, Y(8.8), Z(-13.0)), (C + 0.45, Y(9.9), Z(-11.0)), 'darkmetal', name='bipod_clamp')
for side in (-1, 1):
    x0 = C + side * 0.45
    g.wire([(x0, Y(9.0), Z(-12.5)), (x0, Y(2.6), Z(-14.0))], 0.25, 'gunmetal', name='bipod_leg_front')
    g.wire([(x0, Y(8.8), Z(-11.5)), (x0, Y(4.8), Z(-7.5))], 0.25, 'gunmetal', name='bipod_leg_rear')
    g.box((x0 - 0.15, Y(2.3), Z(-14.9)), (x0 + 0.15, Y(2.9), Z(-13.0)), 'gunmetal', name='bipod_foot')

g.box((C - 0.5, Y(9.4), Z(-12.0)), (C + 0.5, Y(10.2), Z(-5.5)), 'darkmetal', name='gas_block_shroud')

# Gas tube and wooden handguards
g.cyl('z', (C, Y(11.4)), 0.5, Z(-13.5), Z(1.6), 'steel', seg=8, name='gas_tube')
g.box((C - 0.62, Y(10.6), Z(-4.5)), (C + 0.62, Y(11.5), Z(1.6)), 'wood_honey', name='upper_handguard')
g.box((C - 0.8, Y(8.8), Z(-5.5)), (C + 0.8, Y(10.6), Z(1.6)), 'wood_honey', name='lower_handguard')
g.box((C - 0.75, Y(8.2), Z(-2.5)), (C + 0.75, Y(8.9), Z(0.8)), 'wood_honey', name='handguard_belly')

# AK receiver, top cover, rear sight, selector, rivets
g.box((C - 0.75, Y(7.3), Z(1.6)), (C + 0.75, Y(10.8), Z(11.0)), 'darkmetal', name='receiver')
g.box((C - 0.72, Y(10.8), Z(1.6)), (C + 0.72, Y(12.0), Z(10.6)), 'gunmetal', name='top_cover')
g.box((C - 0.45, Y(12.0), Z(1.6)), (C + 0.45, Y(12.3), Z(3.0)), 'darkmetal', name='rear_sight')
g.box((C + 0.75, Y(8.6), Z(5.0)), (C + 0.82, Y(9.1), Z(9.6)), 'steel', name='selector')
for z in (3.0, 4.0, 9.8):
    g.cyl('x', (Y(9.0), Z(z)), 0.12, C - 0.8, C + 0.8, 'steel', seg=8, name='rivet')

# Orange curved magazine
g.box((C - 0.6, Y(4.3), Z(0.2)), (C + 0.6, Y(7.4), Z(4.5)), 'bakelite_orange', name='mag_top')
LOWER = ('x', 22.5, (C, Y(4.4), Z(2.35)))
g.box((C - 0.6, Y(1.6), Z(0.5)), (C + 0.6, Y(4.6), Z(4.2)), 'bakelite_orange', rot=LOWER, name='mag_bottom')
for y in (2.4, 3.4):
    g.box((C - 0.62, Y(y), Z(0.55)), (C + 0.62, Y(y + 0.2), Z(4.15)), 'wood_maroon', rot=LOWER, name='mag_rib')
for y in (4.8, 5.6):
    g.box((C - 0.62, Y(y), Z(0.3)), (C + 0.62, Y(y + 0.2), Z(4.4)), 'wood_maroon', name='mag_rib')

# Trigger guard, trigger, wooden grip
g.box((C - 0.22, Y(6.3), Z(5.8)), (C + 0.22, Y(7.3), Z(10.0)), 'gunmetal', name='guard_bottom')
g.box((C - 0.1, Y(5.6), Z(6.4)), (C + 0.1, Y(6.4), Z(6.7)), 'steel', name='trigger')
g.box((C - 0.65, Y(4.3), Z(8.4)), (C + 0.65, Y(7.3), Z(10.6)), 'wood_honey', name='grip')

# Black club-foot stock
g.box((C - 0.7, Y(7.0), Z(11.0)), (C + 0.7, Y(9.5), Z(19.7)), 'darkmetal', name='stock_comb')
# Belly sloping down to the butt: one rotated slab instead of a staircase of boxes.
g.box((C - 0.72, Y(6.6), Z(11.0)), (C + 0.72, Y(8.0), Z(17.0)), 'darkmetal',
      rot=('x', 22.5, (C, Y(8.0), Z(11.0))), name='stock_belly')
g.box((C - 0.75, Y(4.4), Z(15.0)), (C + 0.75, Y(8.9), Z(19.7)), 'darkmetal', name='stock_rear')
g.box((C - 0.72, Y(3.5), Z(18.0)), (C + 0.72, Y(4.5), Z(19.7)), 'darkmetal', name='club_foot')
g.box((C - 0.78, Y(3.5), Z(19.7)), (C + 0.78, Y(8.9), Z(19.9)), 'darkmetal', name='butt_plate')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.73, 0.8, Y(9.2), Y(10.2), Z(4.5), Z(8.5), 'rubber', name='ejection_port')
g.ring('x', (Y(8.0), Z(-1.0)), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (Y(3.3), Z(18.8)), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
sb(-0.35, 0.35, Y(5.0), Y(5.6), Z(4.5), Z(5.2), 'steel', name='mag_latch')
g.ring('z', (C, Y(11.35)), 0.3, 0.5, Z(-14.1), Z(-12.4), 'darkmetal', name='front_sight_hood')
for z in (-8.0, -3.0):
    g.ring('z', (C, Y(11.4)), 0.5, 0.62, Z(z), Z(z) + 0.4, 'darkmetal', name='gas_tube_clamp')
for y, z in ((7.5, 13.5), (6.0, 17.0)):
    g.cyl('x', (Y(y), Z(z)), 0.13, C - 0.8, C + 0.8, 'steel', seg=8, name='stock_screw')

# Detail pass 2
for s in (-1, 1):
    sb(s * 0.74, s * 0.8, Y(10.3), Y(10.5), Z(1.8), Z(10.8), 'darkmetal', name='receiver_line')
sb(-0.55, 0.55, Y(12.0), Y(12.3), Z(2.6), Z(3.4), 'steel', name='sight_blade')
sb(-0.72, 0.72, Y(8.2), Y(8.4), Z(-2.4), Z(0.7), 'steel', name='handguard_strap')
sb(0.78, 0.9, Y(10.0), Y(10.8), Z(9.0), Z(10.0), 'steel', name='dust_cover_latch')
g.ring('z', (C, Y(10.4)), 0.55, 0.68, Z(-13.4), Z(-13.0), 'steel', name='muzzle_ring')
for z in (-10.5, -6.0):
    g.cyl('x', (Y(9.8), Z(z)), 0.12, C - 0.5, C + 0.5, 'steel', seg=8, name='bipod_pin')
sb(0.65, 0.72, Y(5.0), Y(7.0), Z(8.8), Z(9.0), 'rubber', name='grip_edge')
sb(-0.7, 0.7, Y(9.5), Y(9.7), Z(11.0), Z(19.6), 'steel', name='comb_line')

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
