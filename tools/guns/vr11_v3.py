#!/usr/bin/env python3
"""V-R11 (BO1 "Call of the Dead"), traced from the mystery-box side view (29 px per unit, muzzle at the
right of the image): two long spines reaching back over the loop stock, blue tiger-camo body rising to a
hump with the red bulb, side vents, emitter head with stacked rings and claws, rounded 'C8' canister and
folding claw arm under the front, hose loop, looped grip-stock at the rear. Muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('vr11_v3', offset=(0, -1, 2))
C = 8
K = 29.0


def Z(px):
    return -15 + (940 - px) / K


def Y(py):
    return 12 - (py - 10) / K


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px1)), (C + hw, Y(py0), Z(px0)), mat, **kw)


def sz(z):
    return z - 2


def sy(y):
    return y + 1


def sbox(hw, z0, z1, y0, y1, mat, **kw):
    """Box in saved-model coordinates: z0 rear, z1 front."""
    g.box((C - hw, sy(y0), sz(z1)), (C + hw, sy(y1), sz(z0)), mat, **kw)


def swire(pts, w, mat, **kw):
    g.wire([(C, sy(y), sz(z)) for z, y in pts], w, mat, **kw)


# Rear spines: long top rail reaching back over the stock
for dx in (-0.45, 0.45):
    g.box((C + dx - 0.2, sy(8.2), sz(17)), (C + dx + 0.2, sy(9.3), sz(6)), 'gunmetal', name='spine')
sbox(0.65, 17, 15.5, 8.1, 9.4, 'gunmetal', name='spine_tip')

# Camo body
sbox(0.9, 11.5, 6, 6.8, 8.8, 'tiger_blue', name='rear_body')
sbox(1.0, 8, -8, 5.4, 9.9, 'tiger_blue', name='mid_body')
sbox(0.95, 9, 6.5, 8.6, 9.6, 'tiger_blue', rot=('x', 22.5, (C, sy(8.8), sz(8))), name='rear_slope')
sbox(1.0, 3.5, -0.5, 9.8, 10.4, 'tiger_blue', name='hump')
sbox(0.95, -6, -9, 5.8, 9.0, 'tiger_blue', name='front_block')
for z in (-0.5, -2):
    sbox(1.03, z + 0.7, z, 7.0, 8.6, 'rubber', name='side_vent')
sbox(1.02, 7, -6, 5.9, 6.2, 'darkmetal', name='body_seam')
g.box((C + 0.95, sy(6.5), sz(-6.5)), (C + 1.02, sy(8.5), sz(-8)), 'stencil_atom', name='emblem')

# Red bulb on the hump
g.cyl('y', (C, sz(0.5)), 0.7, sy(10.4), sy(11.0), 'led_red', seg=8, name='red_bulb')
g.cyl('y', (C, sz(0.5)), 0.85, sy(10.2), sy(10.6), 'darkmetal', seg=8, name='bulb_base')

# Emitter head: stacked rings and claws
EY = 7.3
g.cyl('z', (C, sy(EY)), 1.2, sz(-12), sz(-8.5), 'gunmetal', seg=16, name='emitter_body')
for z in (-9.5, -10.5, -11.5):
    g.cyl('z', (C, sy(EY)), 1.35, sz(z - 0.6), sz(z), 'steel', seg=16, name='emitter_ring')
g.cyl('z', (C, sy(EY)), 0.6, sz(-12.6), sz(-12), 'led_red', seg=8, name='emitter_core')
for dy in (-1, 1):
    for dx in (-0.6, 0.6):
        g.box((C + dx - 0.15, sy(EY) + dy * 0.9 - 0.15, sz(-12.8)), (C + dx + 0.15, sy(EY) + dy * 0.9 + 0.15, sz(-9)), 'darkmetal', name='claw')

# Under-front canister and folding claw arm
g.cyl('z', (C, sy(5.0)), 1.2, sz(-8.6), sz(-3.4), 'polymer_dark', seg=16, name='canister')
g.box((C + 1.15, sy(4.4), sz(-7)), (C + 1.22, sy(5.8), sz(-5)), 'engraved_dark', name='c8_marking')
swire([(-8, 4.6), (-10.5, 4.2), (-12.8, 2.2)], 0.4, 'darkmetal', name='claw_arm')
sbox(0.5, -12.2, -13, 1.8, 3.2, 'darkmetal', name='claw_arm_tip')

# Hose / belly tube under the body
swire([(-2, 5.8), (0, 4.4), (2.5, 4.0), (5, 4.6)], 0.5, 'rubber', name='hose')
sbox(0.8, 5.5, -3.4, 4.4, 5.6, 'polymer_olive', name='belly')

# Looped grip-stock at the rear bottom, trigger
sbox(0.6, 6.0, 9.4, 2.2, 7.2, 'polymer_olive', rot=('x', -22.5, (C, sy(7.0), sz(7.6))), name='grip')
sbox(0.7, 3.5, 10.0, 3.9, 5.4, 'polymer_olive', name='underframe')
sbox(0.1, 3.2, 3.0, 3.0, 4.8, 'steel', name='trigger')


# --- detail pass 2 ---
for side in (-1, 1):
    x0, x1 = sorted((C + side * 0.97, C + side * 1.04))
    g.box((x0, sy(7.6), sz(6.5)), (x1, sy(8.7), sz(4)), 'rubber', name='ejection_port')
    for z, y in ((5, 6.4), (2, 6.4), (-4, 6.4), (-4, 9.2)):
        a, b = sorted((C + side * 0.98, C + side * 1.12))
        g.cyl('x', (sy(y), sz(z)), 0.12, a, b, 'steel', seg=8, name='screw')
    a, b = sorted((C + side * 0.98, C + side * 1.35))
    g.box((a, sy(8.3), sz(3.8)), (b, sy(8.7), sz(2.6)), 'steel', name='charging_handle')
    g.box((a if side < 0 else C + 1.25, sy(8.2), sz(3.9)), (b if side > 0 else C - 1.25, sy(8.8), sz(2.5)), 'steel', name='handle_knob')
sbox(0.12, -6.8, -7.4, 9.8, 10.9, 'steel', name='front_sight')
sbox(0.5, 5.5, 4.6, 9.8, 10.5, 'darkmetal', name='rear_sight')
g.ring('x', (sy(3.9), sz(9.5)), 0.25, 0.45, C - 0.12, C + 0.12, 'steel', name='sling_swivel')
g.ring('y', (C, sz(0.5)), 0.86, 1.05, sy(10.3), sy(10.8), 'steel', name='bulb_guard')

# --- detail pass 3 ---
for side in (-1, 1):
    x0, x1 = sorted((C + side * 1.0, C + side * 1.06))
    for y in (8.65, 6.95):
        g.box((x0, sy(y), sz(0.9)), (x1, sy(y + 0.12), sz(-2.4)), 'steel', name='vent_frame')
    for z in (-3.5, -7.5):
        g.cyl('z', (C, sy(5.0)), 1.28, sz(z - 0.3), sz(z), 'steel', seg=16, name='canister_band') if side > 0 else None
g.box((C - 0.65, sy(8.4), sz(12.5)), (C + 0.65, sy(9.1), sz(12)), 'steel', name='spine_bridge')
g.box((C - 0.65, sy(8.4), sz(9)), (C + 0.65, sy(9.1), sz(8.5)), 'steel', name='spine_bridge')
for z, y in ((-13.2, 1), (-13.2, -1)):
    for dx in (-0.6, 0.6):
        g.box((C + dx - 0.2, sy(EY) + y * 0.9 - 0.2, sz(-13.3)), (C + dx + 0.2, sy(EY) + y * 0.9 + 0.2, sz(-12.7)), 'steel', name='claw_tip')
for z, y in ((-2, 5.8), (5, 4.6)):
    g.box((C - 0.45, sy(y) - 0.4, sz(z) - 0.25), (C + 0.45, sy(y) + 0.4, sz(z) + 0.25), 'steel', name='hose_collar')
g.cyl('z', (C, sy(5.0)), 1.0, sz(-3.4), sz(-3.0), 'steel', seg=8, name='canister_cap')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -2], "scale": [0.48, 0.48, 0.48]},
    "thirdperson_lefthand": {"translation": [0, 3, -2], "scale": [0.48, 0.48, 0.48]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.58, 0.58, 0.58]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.58, 0.58, 0.58]},
    "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
