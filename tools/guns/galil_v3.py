#!/usr/bin/env python3
"""Galil ARM (BO1/BO2): AK-pattern receiver, hooded tall front sight over the gas block, gas tube above the
barrel, folded bipod under it, short ribbed handguard, long forward-curving magazine, raked grip and the
tubular folding stock. Proportions from the BO2 menu render (495 px -> 38 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('galil_v3', offset=(0, -2, 0))
C = 8

def Y(v):
    return v + 2


# Muzzle device, barrel, gas tube, folded bipod
g.cyl('z', (C, Y(6.45)), 0.8, -15, -11.5, 'steel', seg=8, name='muzzle_device')
g.cyl('z', (C, Y(6.45)), 0.45, -12, -4.5, 'steel', seg=8, name='barrel')
g.cyl('z', (C, Y(7.4)), 0.4, -3.5, 6.5, 'gunmetal', seg=8, name='gas_tube')
g.box((C - 0.2, Y(4.2), -5.0), (C + 0.2, Y(4.9), 4.0), 'steel', name='bipod_leg')
g.box((C - 0.35, Y(4.2), -5.0), (C + 0.35, Y(5.6), -3.5), 'darkmetal', name='bipod_hinge')

# Front sight block with tall hooded post
g.box((C - 0.5, Y(5.6), -3.6), (C + 0.5, Y(8.2), -1.4), 'darkmetal', name='gas_block')
g.box((C - 0.12, Y(8.2), -3.2), (C + 0.12, Y(9.7), -2.0), 'darkmetal', name='front_post')
g.ring('z', (C, Y(9.0)), 0.4, 0.62, -3.6, -1.6, 'darkmetal', name='front_hood')

# Handguard (ribbed) and receiver
g.box((C - 0.7, Y(5.2), -5.0), (C + 0.7, Y(8.2), 6.5), 'polymer_dark', name='handguard')
for z0 in (-4.2, -2.0, 0.2, 2.4, 4.6):
    g.box((C - 0.72, Y(5.3), z0), (C + 0.72, Y(8.0), z0 + 0.7), 'rubber', name='handguard_rib')
g.box((C - 0.75, Y(4.7), 6.5), (C + 0.75, Y(8.2), 17.5), 'darkmetal', name='receiver')
g.box((C - 0.68, Y(8.2), 7.0), (C + 0.68, Y(8.6), 16.5), 'worn_steel', name='top_cover')
g.box((C - 0.78, Y(5.2), 6.5), (C + 0.78, Y(8.0), 9.0), 'worn_steel', name='trunnion_plate')
g.box((C - 1.1, Y(7.0), 10.0), (C - 0.75, Y(7.5), 11.5), 'steel', name='charging_handle')
g.box((C - 0.3, Y(8.6), 14.0), (C + 0.3, Y(9.3), 15.5), 'darkmetal', name='rear_sight')
g.box((C + 0.75, Y(6.0), 13.0), (C + 0.82, Y(6.6), 15.0), 'steel', name='selector')

# Long magazine curving forward
g.box((C - 0.55, Y(0.2), 8.8), (C + 0.55, Y(4.7), 12.4), 'gunmetal', name='mag_top')
g.box((C - 0.55, Y(-2.65), 8.4), (C + 0.55, Y(0.3), 11.8), 'gunmetal', rot=('x', 22.5, (C, Y(0.3), 10.1)), name='mag_bottom')
g.box((C - 0.58, Y(-2.9), 8.0), (C + 0.58, Y(-2.6), 11.6), 'darkmetal', rot=('x', 22.5, (C, Y(0.3), 10.1)), name='mag_floorplate')

# Trigger housing, guard, trigger, raked grip
g.box((C - 0.5, Y(3.5), 12.4), (C + 0.5, Y(4.9), 16.5), 'darkmetal', name='trigger_housing')
g.box((C - 0.22, Y(3.4), 12.4), (C + 0.22, Y(3.8), 15.5), 'gunmetal', name='guard_bottom')
g.box((C - 0.1, Y(3.8), 13.2), (C + 0.1, Y(4.9), 13.7), 'steel', name='trigger')
g.box((C - 0.6, Y(0.7), 14.0), (C + 0.6, Y(4.9), 17.2), 'polymer_dark', rot=('x', -22.5, (C, Y(4.9), 15.6)), name='grip')

# Tubular folding stock: top tube, lower tube, hinge, butt plate
g.box((C - 0.25, Y(6.2), 17.0), (C + 0.25, Y(6.9), 23.0), 'gunmetal', name='stock_top_tube')
g.box((C - 0.25, Y(4.6), 17.0), (C + 0.25, Y(5.8), 22.5), 'gunmetal', name='stock_lower_tube')
g.box((C - 0.3, Y(4.6), 17.0), (C + 0.3, Y(7.2), 18.5), 'gunmetal', name='stock_hinge')
g.box((C + 0.78, Y(6.3), 8.5), (C + 1.18, Y(6.8), 22.5), 'gunmetal', name='folded_stock_top_tube')
g.box((C + 0.78, Y(4.9), 8.5), (C + 1.18, Y(5.4), 22.5), 'gunmetal', name='folded_stock_low_tube')
g.box((C + 0.78, Y(4.9), 21.9), (C + 1.18, Y(6.8), 22.5), 'gunmetal', name='folded_stock_end')
g.box((C - 0.55, Y(2.3), 21.8), (C + 0.55, Y(6.9), 23.07), 'polymer_dark', name='butt_plate')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.73, 0.79, Y(6.9), Y(7.9), 11.0, 13.5, 'rubber', name='ejection_port')
sb(-0.3, 0.3, Y(4.9), Y(5.2), 2.5, 3.5, 'steel', name='bipod_clip')
for z in (-14.6, -13.6, -12.6):
    sb(-0.82, 0.82, Y(6.45) - 0.15, Y(6.45) + 0.15, z, z + 0.4, 'rubber', name='muzzle_slot')
for y, z in ((6.2, 8.0), (6.2, 16.0), (5.4, 12.0)):
    g.cyl('x', (Y(y), z), 0.12, C - 0.8, C + 0.8, 'steel', seg=8, name='rivet')
sb(-0.1, 0.1, Y(8.8), Y(9.15), 15.5, 15.56, 'rubber', name='aperture')
g.ring('x', (Y(2.0), 22.4), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
sb(-0.45, 0.45, Y(3.3), Y(3.9), 12.4, 12.9, 'steel', name='mag_catch')

# Detail pass 2
for s in (-1, 1):
    sb(s * 0.75, s * 0.79, Y(5.0), Y(8.0), 9.2, 9.35, 'rubber', name='receiver_seam')
    for z in (14.0, 15.1):
        sb(s * 0.3, s * 0.5, Y(8.6), Y(9.3), z, z + 0.4, 'steel', name='rear_sight_ear')
sb(-0.2, 0.2, Y(8.6), Y(8.75), 7.0, 16.5, 'darkmetal', name='cover_ridge')
g.cyl('x', (Y(6.8), -2.5), 0.15, C - 0.62, C + 0.62, 'steel', seg=8, name='gas_block_pin')
for z in (-9.0, -6.0):
    g.ring('z', (C, Y(6.45)), 0.45, 0.6, z, z + 0.4, 'steel', name='barrel_ring')
for y in (1.2, 2.4, 3.6):
    sb(-0.6, 0.6, Y(y), Y(y + 0.3), 9.0, 12.2, 'darkmetal', name='mag_rib')
g.cyl('x', (Y(4.2), 15.5), 0.13, C - 0.6, C + 0.6, 'steel', seg=8, name='housing_screw')
g.cyl('x', (Y(5.9), 17.7), 0.2, C - 0.45, C + 0.45, 'steel', seg=8, name='stock_hinge_pin')
for y in (3.0, 4.3, 5.6):
    sb(-0.5, 0.5, Y(y), Y(y + 0.5), 23.07, 23.19, 'rubber', name='butt_ridge')
g.cyl('x', (Y(6.4), 9.0), 0.25, C + 0.78, C + 1.4, 'steel', seg=8, name='fold_latch')
sb(-0.75, -0.82, Y(6.0), Y(6.6), 13.0, 15.0, 'steel', name='selector_left')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.55, 0.55, 0.55]},
    "ground": {"translation": [0, 2, 0], "scale": [0.36, 0.36, 0.36]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.46, 0.46, 0.46]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
