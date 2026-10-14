#!/usr/bin/env python3
"""MP40 (WaW / BO zombies): barrel with the rest lug under the muzzle, hooded front sight, round
receiver tube with cocking knob, long straight magazine, bakelite grip frame and grip, trigger guard,
unfolded wire stock with butt plate. From the WaW third-person silhouette (320 px -> 30 units)."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('mp40_v3', offset=(0, 0, 5))
C = 8


def Z(v):
    return v - 5


# Barrel, jacket, rest lug, hooded front sight
g.cyl('z', (C, 9.05), 0.5, Z(-10.0), Z(0.0), 'darkmetal', seg=8, name='barrel')
g.cyl('z', (C, 9.0), 0.65, Z(-2.5), Z(0.0), 'darkmetal', seg=8, name='barrel_jacket')
g.box((C - 0.3, 8.3, Z(-7.5)), (C + 0.3, 9.1, Z(-6.5)), 'darkmetal', name='rest_lug')
g.ring('z', (C, 10.1), 0.25, 0.45, Z(-9.3), Z(-8.2), 'darkmetal', name='front_sight_hood')
g.box((C - 0.08, 9.5, Z(-9.0)), (C + 0.08, 10.84, Z(-8.5)), 'darkmetal', name='front_sight')

# Receiver tube, cocking knob and slot, rear sight
g.cyl('z', (C, 8.3), 1.25, Z(-1.0), Z(15.5), 'darkmetal', seg=16, name='receiver_tube')
g.box((C - 1.27, 8.1, Z(3.0)), (C + 1.27, 8.5, Z(10.0)), 'rubber', name='cocking_slot')
g.box((C - 1.9, 8.3, Z(9.0)), (C - 1.2, 8.85, Z(10.0)), 'steel', name='cocking_knob')
g.box((C - 0.3, 9.4, Z(-1.5)), (C + 0.3, 10.0, Z(0.0)), 'darkmetal', name='rear_sight')
g.cyl('z', (C, 8.3), 1.3, Z(-1.0), Z(0.0), 'gunmetal', seg=16, name='front_ring')

# Magwell and long straight magazine
g.box((C - 0.75, 6.0, Z(-1.2)), (C + 0.75, 7.3, Z(2.5)), 'darkmetal', name='magwell')
g.box((C - 0.55, -1.37, Z(-0.4)), (C + 0.55, 6.1, Z(1.8)), 'gunmetal', name='magazine')
for y in (1.5, 3.0, 4.5):
    g.box((C - 0.57, y, Z(-0.35)), (C + 0.57, y + 0.3, Z(1.75)), 'darkmetal', name='mag_rib')

# Bakelite lower frame, trigger guard, grip
g.box((C - 0.7, 6.0, Z(3.0)), (C + 0.7, 7.3, Z(16.0)), 'polymer_dark', name='grip_frame')
g.box((C - 0.5, 6.0, Z(16.0)), (C + 0.5, 6.8, Z(18.5)), 'polymer_dark', name='frame_tail')
g.box((C - 0.2, 5.65, Z(9.0)), (C + 0.2, 6.0, Z(12.6)), 'darkmetal', name='guard_bottom')
g.box((C - 0.1, 5.1, Z(10.6)), (C + 0.1, 6.0, Z(10.9)), 'steel', name='trigger')
g.box((C - 0.65, 2.6, Z(12.4)), (C + 0.65, 6.0, Z(14.8)), 'polymer_dark', rot=('x', -22.5, (C, 6.0, Z(13.6))), name='grip')

# Unfolded wire stock and butt plate
g.box((C - 0.7, 7.0, Z(15.5)), (C + 0.7, 7.4, Z(23.6)), 'gunmetal', name='stock_upper_wire')
g.box((C - 0.7, 6.0, Z(17.0)), (C + 0.7, 6.4, Z(23.6)), 'gunmetal', name='stock_lower_wire')
g.box((C - 0.75, 4.6, Z(23.6)), (C + 0.75, 8.0, Z(24.2)), 'darkmetal', name='butt_plate')
g.cyl('x', (6.2, Z(16.0)), 0.3, C - 0.8, C + 0.8, 'steel', seg=8, name='stock_hinge')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.73, 0.9, 6.3, 6.9, Z(0.3), Z(1.3), 'steel', name='mag_release')
g.ring('x', (5.8, Z(21.0)), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
g.ring('x', (8.15, Z(-1.2)), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='front_swivel')
for z in (7.0, 13.0):
    g.cyl('x', (6.65, Z(z)), 0.13, C - 0.78, C + 0.78, 'steel', seg=8, name='frame_screw')
g.cyl('z', (C, 9.05), 0.3, Z(-10.05), Z(-9.8), 'rubber', seg=8, name='bore')
sb(-0.08, 0.08, 9.65, 9.95, Z(0.0), Z(0.05), 'rubber', name='rear_notch')
sb(-0.62, 0.62, -1.6, -1.37, Z(-0.45), Z(1.85), 'steel', name='mag_baseplate')

# Detail pass 2
for z in (-9.9, -9.5, -9.1):
    g.ring('z', (C, 9.05), 0.5, 0.62, Z(z), Z(z) + 0.2, 'steel', name='muzzle_thread')
sb(-0.9, 0.9, 7.3, 7.45, Z(3.0), Z(16.0), 'darkmetal', name='frame_edge')
for y in (1.0, 2.0, 3.0, 4.0, 5.0):
    sb(-0.56, 0.56, y, y + 0.15, Z(-0.4), Z(1.8), 'darkmetal', name='mag_seam')
for s in (-1, 1):
    sb(s * 0.7, s * 0.78, 6.3, 7.0, Z(3.5), Z(4.2), 'steel', name='frame_clip')
    sb(s * 0.5, s * 0.55, 7.5, 8.5, Z(-0.8), Z(-0.2), 'darkmetal', name='tube_seam')
sb(-0.3, 0.3, 9.9, 10.2, Z(0.0), Z(0.6), 'steel', name='rear_sight_base')
for z in (4.0, 5.2, 6.4, 8.8):
    sb(0.7, 0.76, 6.1, 7.2, Z(z), Z(z) + 0.5, 'rubber', name='grip_groove')
sb(-0.45, 0.45, 6.0, 6.3, Z(17.0), Z(18.4), 'steel', name='stock_latch')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.52, 0.52, 0.52]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.52, 0.52, 0.52]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.66, 0.66, 0.66]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.66, 0.66, 0.66]},
    "ground": {"translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.56, 0.56, 0.56]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
