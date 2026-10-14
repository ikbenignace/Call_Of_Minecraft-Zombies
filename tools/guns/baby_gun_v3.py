#!/usr/bin/env python3
"""31-79 JGb215 "Baby Gun" (BO1 "Shangri-La"), traced from the 952 px third-person render (23.25 px per
unit): hooked claw, ball emitter on the upper rod, lower rod with ribbed capsule, brass block with gears,
twisted green focusing crystal, ornate lower arc with wooden plate, engraved dark receiver, box
magazine, trigger, long wooden stock with a leather wrap. Muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('baby_gun_v3', offset=(0, -1, 0))
C = 8
K = 23.25


def Z(px):
    return -15 + (px - 15) / K


def Y(py):
    return 12 - (py - 10) / K


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px0)), (C + hw, Y(py0), Z(px1)), mat, **kw)


# Hooked claw at the front
g.wire([(C, Y(110), Z(45)), (C, Y(130), Z(25)), (C, Y(95), Z(12)), (C, Y(45), Z(15)), (C, Y(25), Z(35)), (C, Y(30), Z(52))], 0.3, 'brass', name='hook')
g.cyl('z', (C, Y(70)), 0.25, Z(12), Z(64), 'brass', seg=8, name='hook_stem')
# Upper rod with ball, lower rod with ribbed capsule
g.cyl('z', (C, Y(70)), 0.45, Z(80), Z(240), 'darkmetal', seg=8, name='upper_rod')
g.cyl('z', (C, Y(70)), 0.8, Z(60), Z(86), 'brass', seg=8, name='ball')
g.cyl('z', (C, Y(70)), 0.5, Z(200), Z(240), 'brass', seg=8, name='upper_collar')
g.cyl('z', (C, Y(118)), 0.6, Z(90), Z(240), 'darkmetal', seg=8, name='lower_rod')
g.cyl('z', (C, Y(118)), 0.5, Z(90), Z(110), 'darkmetal', seg=8, name='lower_tip')
g.cyl('z', (C, Y(118)), 0.85, Z(250), Z(325), 'oldmetal', seg=16, name='capsule')
for px in (262, 282, 302):
    g.cyl('z', (C, Y(118)), 0.92, Z(px), Z(px + 6), 'brass', seg=16, name='capsule_rib')
box(0.75, 55, 138, 230, 250, 'darkmetal', name='rod_mount')
box(0.75, 55, 138, 325, 355, 'darkmetal', name='rod_mount_rear')
box(0.7, 62, 138, 195, 330, 'darkmetal', name='rod_housing')

# Brass gear block with focusing crystal above
box(0.95, 35, 158, 350, 440, 'brass', name='gear_block')
for py in (60, 95):
    g.cyl('x', (Y(py), Z(385)), 0.5, C - 1.05, C + 1.05, 'gold', seg=8, name='gear')
for i, (py0, py1, dz) in enumerate(((95, 130, 0), (60, 95, 12), (25, 60, 0), (5, 25, 12))):
    box(0.45, py0, py1, 445 + dz, 480 + dz, 'glow_green', rot=('y', 22.5 if i % 2 else -22.5, (C, Y((py0 + py1) / 2), Z(462 + dz))), name='crystal')
box(0.7, 125, 140, 430, 500, 'brass', name='crystal_base')

# Ornate lower arc and wooden plate
g.wire([(C, Y(125), Z(300)), (C, Y(150), Z(360)), (C, Y(175), Z(430)), (C, Y(165), Z(510))], 0.35, 'brass', name='lower_arc')
box(0.7, 150, 199, 385, 495, 'wood_tan', name='wood_plate')
for px in (405, 475):
    g.cyl('x', (Y(170), Z(px)), 0.15, C - 0.75, C + 0.75, 'gold', seg=8, name='plate_rivet')

# Engraved receiver, rear sight, magazine, trigger
box(0.85, 45, 130, 500, 690, 'engraved_dark', name='receiver')
box(0.87, 80, 86, 510, 680, 'brass', name='receiver_trim')
box(0.8, 125, 168, 490, 590, 'engraved_dark', name='receiver_lower')
box(0.4, 35, 45, 640, 670, 'darkmetal', name='rear_sight')
box(0.65, 130, 215, 578, 626, 'darkmetal', name='magazine')
for py in (150, 175):
    box(0.67, py, py + 4, 592, 633, 'brass', name='mag_rib')
box(0.22, 130, 136, 640, 685, 'brass', name='guard_bottom')
box(0.1, 125, 138, 655, 661, 'steel', name='trigger')

# Wooden stock with leather wrap
box(0.75, 60, 130, 690, 760, 'wood_light', name='stock_wrist')
box(0.82, 62, 158, 760, 930, 'wood_light', name='stock_butt')
box(0.8, 125, 150, 700, 800, 'wood_light', rot=('x', 22.5, (C, Y(138), Z(730))), name='stock_belly')
box(0.84, 62, 160, 850, 880, 'leather', name='leather_wrap')
box(0.86, 58, 162, 925, 935, 'darkmetal', name='butt_plate')


# --- detail pass 2 ---
for side in (-1, 1):
    a, b = sorted((C + side * 0.8, C + side * 1.0))
    for py, px in ((60, 520), (112, 520), (60, 670), (112, 670)):
        g.cyl('x', (Y(py), Z(px)), 0.12, *sorted((C + side * 0.84, C + side * 1.0)), 'steel', seg=8, name='receiver_screw')
    a, b = sorted((C + side * 0.8, C + side * 1.1))
    g.box((a, Y(135), Z(575)), (b, Y(147), Z(595)), 'steel', name='mag_release')
    a, b = sorted((C + side * 0.84, C + side * 1.15))
    g.box((a, Y(70), Z(560)), (b, Y(62), Z(600)), 'steel', name='charging_handle')
    for py, px in ((45, 365), (150, 365), (45, 430), (150, 430)):
        g.cyl('x', (Y(py), Z(px)), 0.14, *sorted((C + side * 0.9, C + side * 1.1)), 'steel', seg=8, name='gear_bolt')
    for px in (855, 875):
        box(0.86, 64, 156, px, px + 2, 'rubber', name='stitch')
for px in (135, 195):
    g.cyl('z', (C, Y(118)), 0.7, Z(px), Z(px + 8), 'gold', seg=8, name='rod_band')
g.ring('x', (Y(158), Z(900)), 0.25, 0.45, C - 0.12, C + 0.12, 'steel', name='sling_swivel')

# --- detail pass 3 ---
for px in (365, 395, 425):
    box(0.12, 155, 175, px, px + 6, 'darkmetal', name='block_spike')
for px in (150, 180):
    g.cyl('z', (C, Y(70)), 0.55, Z(px), Z(px + 6), 'gold', seg=8, name='upper_band')
for side in (-1, 1):
    for px in (240, 340):
        g.cyl('x', (Y(98), Z(px)), 0.14, *sorted((C + side * 0.72, C + side * 0.84)), 'steel', seg=8, name='mount_bolt')
    box(0.88, 76, 100, 612, 648, 'rubber', name='ejection_port')
    for py in (80, 140):
        g.cyl('x', (Y(py), Z(930)), 0.14, *sorted((C + side * 0.84, C + side * 0.96)), 'steel', seg=8, name='butt_screw')
for px in range(552, 640, 12):
    box(0.3, 41, 47, px, px + 4, 'steel', name='rail_tick')
box(0.67, 212, 219, 576, 628, 'brass', name='mag_plate')
box(0.86, 100, 114, 858, 872, 'brass', name='wrap_buckle')

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
