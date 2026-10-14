#!/usr/bin/env python3
"""Wave Gun (BO1 "Moon"): the two Zap Guns locked end to end into one gun. Rear = red Zap Gun (vented
body, dial, green zigzags, blue glow ring, red flange); joined by a ribbed bellows with a hinged arm
frame to the front = blue Zap Gun (atom emblem, green zigzags, copper coil in a wire cage, red top
handle with antenna, flared front collar, finned steel emitter). Two raked pistol grips.
Measured from a side render (1040 px -> 32 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('wave_gun_v3', offset=(0, 0, 0))
C = 8


def Z(px):
    return 16 - (px - 87) / 32.5


def Y(py):
    return 13 - (py - 155) / 32.5


RY, BY, MY = Y(250), Y(265), Y(303)

# Rear: red Zap Gun
g.cyl('z', (C, RY), 1.9, Z(330), Z(110), 'paint_red', seg=16, name='red_body')
g.cyl('z', (C, RY), 1.75, Z(110), Z(92), 'darkmetal', seg=16, name='red_rear_cap')
g.cyl('z', (C, RY), 1.95, Z(390), Z(330), 'glow_blue', seg=16, name='glow_ring')
g.cyl('z', (C, RY), 2.15, Z(440), Z(390), 'paint_red', seg=16, name='red_flange')
g.cyl('z', (C, RY), 2.2, Z(400), Z(392), 'darkmetal', seg=16, name='flange_rim')
for px in (245, 265, 285, 305):
    g.box((C - 0.5, RY + 1.85, Z(px + 10)), (C + 0.5, RY + 2.15, Z(px)), 'darkmetal', name='top_vent')
def decal(side, py0, py1, px0, px1, mat, name):
    hs = [abs(Y(p) - RY) for p in (py0, py1)]
    hmin, hmax = (0.0 if py0 <= 250 <= py1 else min(hs)), max(hs)
    xo = (1.9 ** 2 - hmin ** 2) ** 0.5 + 0.04
    xi = (1.9 ** 2 - hmax ** 2) ** 0.5 - 0.05
    x0, x1 = sorted((C + side * xi, C + side * xo))
    g.box((x0, Y(py1), Z(px1)), (x1, Y(py0), Z(px0)), mat, name=name)


for side in (-1, 1):
    x0, x1 = sorted((C + side * 1.88, C + side * 2.0))
    g.cyl('x', (Y(280), Z(255)), 0.4, x0, x1, 'darkmetal', seg=8, name='dial')
    g.cyl('x', (Y(280), Z(205)), 0.22, x0, x1, 'steel', seg=8, name='knob')
    decal(side, 232, 240, 205, 245, 'glow_green', 'zigzag')
    decal(side, 240, 248, 245, 285, 'glow_green', 'zigzag')
    decal(side, 232, 240, 285, 325, 'glow_green', 'zigzag')
    decal(side, 285, 300, 318, 332, 'rubber', 'arrow')
    decal(side, 290, 296, 300, 318, 'rubber', 'arrow')
    decal(side, 292, 294, 285, 300, 'rubber', 'arrow')
    decal(side, 296, 304, 135, 150, 'rubber', 'arrow')
    decal(side, 296, 304, 160, 175, 'rubber', 'arrow')
g.box((C - 0.3, Y(205), Z(170)), (C + 0.3, Y(180), Z(97)), 'darkmetal', name='rear_fin')

# Joint: ribbed bellows with a hinged arm frame above and below
for i, px in enumerate(range(440, 560, 15)):
    g.cyl('z', (C, RY), 1.25 if i % 2 else 1.05, Z(px + 15), Z(px), 'rubber', seg=8, name='bellows')
for py in (190, 318):
    y = Y(py)
    g.box((C - 0.18, y - 0.18, Z(590)), (C + 0.18, y + 0.18, Z(440)), 'darkmetal', name='frame_arm')
    for px in (455, 580):
        g.cyl('x', (y, Z(px)), 0.38, C - 0.4, C + 0.4, 'gunmetal', seg=8, name='ball_joint')
g.cyl('z', (C, RY), 0.35, Z(600), Z(440), 'steel', seg=8, name='centre_rod')

# Front: blue Zap Gun
g.cyl('z', (C, BY), 1.6, Z(600), Z(560), 'paint_red', seg=16, name='blue_rear_collar')
g.cyl('z', (C, BY), 2.0, Z(905), Z(600), 'paint_blue', seg=16, name='blue_body')
for px in (620, 880):
    g.cyl('z', (C, BY), 2.05, Z(px + 8), Z(px), 'darkmetal', seg=16, name='blue_band')
g.cyl('z', (C, BY), 2.3, Z(980), Z(905), 'paint_blue', seg=16, name='front_collar')
g.cyl('z', (C, BY), 2.35, Z(935), Z(925), 'glow_green', seg=16, name='collar_stripe')
for side in (-1, 1):
    x0, x1 = sorted((C + side * 1.98, C + side * 2.08))
    g.box((x0, Y(310), Z(740)), (x1, Y(270), Z(680)), 'stencil_atom', name='atom_emblem')
    hmin, hmax = abs(Y(310) - BY), abs(Y(318) - BY)
    sx0, sx1 = sorted((C + side * ((2.0 ** 2 - hmax ** 2) ** 0.5 - 0.05), C + side * ((2.0 ** 2 - hmin ** 2) ** 0.5 + 0.02)))
    g.box((sx0, Y(318), Z(860)), (sx1, Y(310), Z(780)), 'glow_green', name='side_stripe')
    # Copper coil inside a wire cage along the upper side
    cx0, cx1 = sorted((C + side * 1.5, C + side * 2.15))
    g.box((cx0, Y(245), Z(860)), (cx1, Y(205), Z(650)), 'brass', name='coil')
    for px in (660, 730, 800, 850):
        g.box((cx0, Y(250), Z(px + 6)), (cx1 + side * 0.05 if side > 0 else cx1, Y(200), Z(px)), 'darkmetal', name='cage_ring')
# Red top handle on posts, antenna
g.box((C - 0.45, Y(200), Z(940)), (C + 0.45, Y(170), Z(790)), 'paint_red', name='top_handle')
for px in (800, 925):
    g.box((C - 0.25, BY + 1.8, Z(px + 10)), (C + 0.25, Y(198), Z(px)), 'darkmetal', name='handle_post')
g.box((C - 0.08, Y(172), Z(1010)), (C + 0.08, Y(166), Z(940)), 'steel', name='antenna')
g.box((C - 0.18, Y(176), Z(1018)), (C + 0.18, Y(158), Z(1006)), 'glow_blue', name='antenna_tip')

# Emitter: steel neck with red rings flaring into a finned bell
g.cyl('z', (C, MY), 1.0, Z(1060), Z(975), 'steel', seg=16, name='emitter_neck')
for px in (1000, 1030):
    g.cyl('z', (C, MY), 1.06, Z(px + 7), Z(px), 'red_dark', seg=16, name='emitter_ring')
g.cyl('z', (C, MY), 1.3, Z(1095), Z(1060), 'steel', seg=16, name='emitter_bell')
g.cyl('z', (C, MY), 1.55, Z(1127), Z(1095), 'steel', seg=16, name='emitter_mouth')
g.cyl('z', (C, MY), 1.0, Z(1129), Z(1125), 'rubber', seg=16, name='emitter_bore')
for dy in (1, -1):
    y0, y1 = sorted((MY + dy * 1.3, MY + dy * 2.2))
    g.box((C - 0.1, y0, Z(1127)), (C + 0.1, y1, Z(1080)), 'darkmetal', name='emitter_fin')
for dx in (1, -1):
    x0, x1 = sorted((C + dx * 1.3, C + dx * 2.2))
    g.box((x0, MY - 0.1, Z(1127)), (x1, MY + 0.1, Z(1080)), 'darkmetal', name='emitter_fin')

# Two chunky raked pistol grips (one per Zap Gun), triggers
for z_front, y_top, depth, length in ((Z(290), Y(315), 3.6, 5.4), (Z(730), Y(332), 3.6, 5.9)):
    rot = ('x', -22.5, (C, y_top, z_front))
    g.box((C - 0.85, y_top - length, z_front), (C + 0.85, y_top + 0.6, z_front + depth), 'grip_ribs_dark', rot=rot, name='grip')
    g.box((C - 0.9, y_top - length - 0.4, z_front - 0.1), (C + 0.9, y_top - length, z_front + depth + 0.1), 'gunmetal', rot=rot, name='grip_cap')
    g.box((C - 0.12, y_top - 1.4, z_front - 1.0), (C + 0.12, y_top + 0.3, z_front - 0.7), 'darkmetal', name='trigger')


# --- detail pass 2 ---
import math
for i in range(8):
    a = math.radians(i * 45 + 22.5)
    g.cyl('z', (C + 1.95 * math.cos(a), RY + 1.95 * math.sin(a)), 0.15, Z(446), Z(436), 'steel', seg=8, name='flange_bolt')
for side in (-1, 1):
    for px in (650, 765, 850):
        x0, x1 = sorted((C + side * 1.92, C + side * 2.1))
        g.cyl('x', (BY, Z(px)), 0.15, x0, x1, 'steel', seg=8, name='body_screw')
g.cyl('z', (C, RY), 0.5, Z(78), Z(95), 'steel', seg=8, name='rear_nut')
g.cyl('z', (C, Y(169)), 0.2, Z(960), Z(950), 'brass', seg=8, name='antenna_bushing')
for z_front, y_top in ((Z(290), Y(315)), (Z(730), Y(332))):
    g.box((C - 0.1, y_top - 1.65, z_front - 1.0), (C + 0.1, y_top - 1.4, z_front + 1.8), 'darkmetal', name='trigger_guard')

# --- detail pass 3 ---
for i in range(10):
    a = math.radians(i * 36)
    g.cyl('z', (C + 2.2 * math.cos(a), BY + 2.2 * math.sin(a)), 0.13, Z(983), Z(970), 'brass', seg=8, name='collar_stud')
for side in (-1, 1):
    g.ring('x', (Y(280), Z(255)), 0.4, 0.62, *sorted((C + side * 1.72, C + side * 1.92)), 'steel', name='dial_bezel')
    ex0, ex1 = sorted((C + side * 2.1, C + side * 2.2))
    for py in (250, 200):
        g.box((ex0 if side < 0 else C + 2.0, Y(py + 1), Z(860)), (ex1 if side > 0 else C - 2.0, Y(py - 1), Z(650)), 'darkmetal', name='cage_rail')
    for px in (455, 580):
        for py in (190, 318):
            g.cyl('x', (Y(py), Z(px)), 0.18, *sorted((C + side * 0.38, C + side * 0.52)), 'steel', seg=8, name='joint_pin')
for px in (815, 850, 885, 915):
    g.box((C - 0.47, Y(200), Z(px + 6)), (C + 0.47, Y(170), Z(px)), 'rubber', name='handle_grip_band')
for z_front, y_top, depth, length in ((Z(290), Y(315), 3.6, 5.4), (Z(730), Y(332), 3.6, 5.9)):
    rot = ('x', -22.5, (C, y_top, z_front))
    for k in range(4):
        yy = y_top - 1.2 - k * 0.9
        g.box((C - 0.88, yy, z_front), (C + 0.88, yy + 0.3, z_front + depth), 'gunmetal', rot=rot, name='grip_rib')
g.cyl('z', (C, MY), 1.36, Z(1066), Z(1058), 'darkmetal', seg=16, name='bell_clamp')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 2, -1], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"translation": [0, 2, -1], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"translation": [2, 0.5, -2], "scale": [0.45, 0.45, 0.45]},
    "firstperson_lefthand": {"translation": [2, 0.5, -2], "scale": [0.45, 0.45, 0.45]},
    "ground": {"translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.8, 0.8, 0.8]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
