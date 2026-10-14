#!/usr/bin/env python3
"""Winter's Howl (BO1 "Five"), traced from the BO1 model render (1000 px wide, 29.4 px per unit):
rear drum with rings and cooling fins, open frame holding two glowing ice cylinders with a centre
bracket, ribbed coupler, copper wrap, long panelled barrel housing with a forked muzzle, lower strut,
pivot and trigger under the frame, ribbed wooden grip with a lanyard wire. Muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('winters_howl_v3', offset=(0, -1, 2))
C = 8
K = 29.4


def Z(px):
    return -15 + (990 - px) / K


def Y(py):
    return 12 - (py - 20) / K


def box(hw, py0, py1, px0, px1, mat, **kw):
    g.box((C - hw, Y(py1), Z(px1)), (C + hw, Y(py0), Z(px0)), mat, **kw)


# Rear drum, rings, rear block with fins and hammer
DY = Y(115)
g.cyl('z', (C, DY), 2.9, Z(300), Z(205), 'rust_brown', seg=16, name='rear_drum')
for px in (215, 250, 285):
    g.cyl('z', (C, DY), 3.0, Z(px + 8), Z(px), 'darkmetal', seg=16, name='drum_ring')
box(1.1, 22, 205, 160, 210, 'rust_brown', name='rear_block')
for py in (142, 152, 162, 172, 182):
    box(0.9, py, py + 5, 128, 162, 'darkmetal', name='cooling_fin')
box(0.25, 30, 60, 175, 200, 'darkmetal', name='hammer')
g.cyl('x', (Y(80), Z(185)), 0.18, C - 1.15, C + 1.15, 'steel', seg=8, name='rear_pin')

# Open frame: top and bottom bars, centre bracket, front plate, ice cylinders with caps
box(1.05, 8, 38, 290, 500, 'rust_brown', name='frame_top')
box(1.05, 195, 215, 290, 500, 'rust_brown', name='frame_bottom')
box(0.45, 100, 130, 340, 448, 'darkmetal', name='centre_bracket')
box(1.1, 28, 212, 440, 500, 'rust_brown', name='front_plate')
for cy in (75, 155):
    g.cyl('z', (C, Y(cy)), 1.2, Z(440), Z(298), 'ice', seg=16, name='ice_cylinder')
    for px in (296, 432):
        g.cyl('z', (C, Y(cy)), 1.28, Z(px + 10), Z(px), 'darkmetal', seg=16, name='cylinder_cap')

# Ribbed coupler and copper wrap
g.cyl('z', (C, Y(105)), 1.85, Z(545), Z(500), 'darkmetal', seg=16, name='coupler')
for px in (506, 520, 534):
    g.cyl('z', (C, Y(105)), 1.95, Z(px + 6), Z(px), 'rust_brown', seg=16, name='coupler_ring')
g.cyl('z', (C, Y(102)), 1.65, Z(590), Z(545), 'copper', seg=16, name='copper_wrap')
for px in range(548, 588, 6):
    g.cyl('z', (C, Y(102)), 1.7, Z(px + 2), Z(px), 'brass', seg=16, name='copper_turn')

# Panelled barrel housing, panel seams, forked muzzle
box(1.05, 45, 160, 590, 930, 'rust_brown', name='barrel_housing')
for py in (95, 128):
    box(1.08, py, py + 5, 600, 925, 'darkmetal', name='housing_seam')
for px in (735, 860):
    box(1.08, 48, 95, px, px + 5, 'darkmetal', name='housing_seam_v')
box(1.1, 40, 50, 590, 930, 'darkmetal', name='housing_top_rail')
for py in (52, 100, 150):
    g.cyl('z', (C, Y(py)), 0.2, Z(1000), Z(920), 'rust_brown', seg=8, name='muzzle_prong')
g.box((C - 0.12, Y(56), Z(975)), (C + 0.12, Y(10), Z(950)), 'rust_brown', rot=('x', -22.5, (C, Y(20), Z(962))), name='fork_spike')

# Lower strut, under frame bar, pivot, trigger and guard
g.box((C - 0.18, Y(194), Z(930)), (C + 0.18, Y(176), Z(480)), 'rust_brown', name='lower_strut')
box(0.9, 212, 226, 250, 500, 'rust_brown', name='under_frame')
box(0.9, 224, 272, 334, 486, 'rust_brown', name='under_frame_bulge')
g.cyl('x', (Y(205), Z(482)), 0.5, C - 1.15, C + 1.15, 'darkmetal', seg=8, name='pivot')
g.cyl('x', (Y(205), Z(482)), 0.25, C - 1.2, C + 1.2, 'brass', seg=8, name='pivot_cap')
g.wire([(C, Y(250), Z(262)), (C, Y(275), Z(248)), (C, Y(300), Z(255))], 0.22, 'darkmetal', name='trigger')
g.wire([(C, Y(255), Z(320)), (C, Y(300), Z(300)), (C, Y(305), Z(255)), (C, Y(285), Z(230))], 0.2, 'rust_brown', name='trigger_guard')

# Ribbed wooden grip raked back, butt cap, lanyard wire
GRIP = ('x', -22.5, (C, Y(225), Z(185)))
box(0.8, 195, 385, 125, 230, 'wood_ribbed_dark', rot=GRIP, name='grip')
box(0.84, 375, 392, 120, 235, 'rust_brown', rot=GRIP, name='grip_cap')
box(0.98, 270, 372, 135, 222, 'wood_ribbed_dark', rot=GRIP, name='grip_swell')
g.cyl('x', (Y(300), Z(195)), 0.15, C - 0.86, C + 0.86, 'brass', seg=8, name='grip_screw')
g.wire([(C, Y(190), Z(150)), (C, Y(205), Z(80)), (C, Y(215), Z(20))], 0.1, 'rust_brown', name='lanyard_wire')


# --- detail pass 2 ---
for side in (-1, 1):
    for py in (70, 140):
        for px in (620, 700, 780, 900):
            g.cyl('x', (Y(py), Z(px)), 0.15, C + side * 1.0, C + side * 1.15, 'steel', seg=8, name='housing_rivet')
    for py, px in ((60, 470), (180, 470), (60, 320), (180, 320)):
        g.cyl('x', (Y(py), Z(px)), 0.2, C + side * 1.0, C + side * 1.18, 'brass', seg=8, name='frame_bolt')
    g.box((C + side * 1.1, Y(110), Z(190)), (C + side * 1.3, Y(100), Z(165)), 'steel', name='safety_lever')
    g.cyl('x', (Y(105), Z(167)), 0.18, C + side * 1.1, C + side * 1.4, 'brass', seg=8, name='safety_knob')
box(0.12, 32, 44, 905, 915, 'steel', name='front_sight')
box(0.4, 34, 44, 600, 640, 'darkmetal', name='rear_sight')
box(0.05, 30, 36, 612, 630, 'steel', name='rear_sight_blade')
g.ring('x', (Y(185), Z(900)), 0.28, 0.5, C - 0.18, C + 0.18, 'steel', name='sling_ring')

# --- detail pass 3 ---
for px in (350, 385, 420):
    for cy in (75, 155):
        g.cyl('z', (C, Y(cy)), 1.26, Z(px + 4), Z(px), 'steel', seg=16, name='frost_band')
box(0.7, 156, 172, 600, 925, 'darkmetal', name='housing_under_rail')
for px0 in (640, 770):
    box(1.09, 55, 85, px0, px0 + 80, 'steel', name='housing_plate')
for side in (-1, 1):
    g.cyl('x', (DY, Z(250)), 0.22, C + side * 2.85, C + side * 3.05, 'steel', seg=8, name='drum_bolt')
    g.cyl('x', (Y(205), Z(482)), 0.7, C + side * 1.1, C + side * 1.2, 'darkmetal', seg=16, name='pivot_boss')
    g.cyl('x', (Y(300), Z(195)), 0.15, C + side * 0.8, C + side * 0.9, 'brass', seg=8, name='grip_screw_2')
g.cyl('x', (Y(115), Z(400)), 0.15, C - 0.5, C + 0.5, 'steel', seg=8, name='bracket_pin')
box(0.5, 112, 118, 330, 345, 'steel', name='bracket_tab')
box(0.9, 195, 202, 130, 230, 'steel', name='grip_band')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"translation": [1, 2.5, -1], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"translation": [1, 2.5, -1], "scale": [0.6, 0.6, 0.6]},
    "ground": {"translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
