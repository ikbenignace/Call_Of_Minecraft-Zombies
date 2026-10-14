#!/usr/bin/env python3
"""Wunderwaffe DG-2 (WaW/BO1): electrode rod with brass coil inside a prong cage (four hooked wire prongs,
three cage rings), slim barrel with brass fittings, ribbed capacitor shroud, dark receiver with a
crenellated top rail, three glowing vacuum bulbs sticking out of the right side, blue glass tube, speaker
grille and dial, vertical-slat battery box and horizontal-slat battery pack joined by a hanging cable,
copper valve on top at the back, dark red wooden stock with metal tusks underneath.
Measured from the BO1 third-person render (1005 px -> 44 units); muzzle towards -Z."""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('wunderwaffe_v3', offset=(0, -1, 0))
C = 8


def Z(px):
    return -15 + (px - 10) / 22.8


def Y(py):
    return 12 - (py - 10) / 22.8


def box(x0, x1, py0, py1, px0, px1, mat, **kw):
    g.box((C + x0, Y(py1), Z(px0)), (C + x1, Y(py0), Z(px1)), mat, **kw)


AY = Y(56)
# Electrode with brass coil
g.cyl('z', (C, AY), 0.22, Z(12), Z(112), 'steel', seg=8, name='electrode')
for px in range(26, 96, 9):
    g.cyl('z', (C, AY), 0.38, Z(px), Z(px + 4), 'brass', seg=8, name='coil')
g.cyl('z', (C, AY), 0.3, Z(8), Z(14), 'brass', seg=8, name='electrode_tip')

# Prong cage: three rings and four hooked prongs
for px in (100, 214, 262):
    g.ring('z', (C, AY), 1.5, 1.75, Z(px), Z(px + 5), 'chrome', name='cage_ring')
for (dx, dy) in ((-0.8, 1.2), (0.8, 1.2), (-0.8, -1.2), (0.8, -1.2)):
    x = C + dx
    tip_y = AY + dy * 2.3
    tip_y = AY + dy * 1.9
    g.wire([(x, AY + dy * 1.1, Z(102)), (x, AY + dy * 1.5, Z(70)), (x, tip_y, Z(44))], 0.24, 'chrome', name='prong')
    g.wire([(x, tip_y, Z(44)), (x, tip_y - dy * 0.55, Z(54))], 0.24, 'chrome', name='prong_hook')

for px in (100, 214, 262):
    g.box((C - 0.09, AY - 1.6, Z(px)), (C + 0.09, AY + 1.6, Z(px + 5)), 'chrome', name='cage_spoke')
for dy in (-1.2, 1.2):
    g.cyl('z', (C, AY + dy), 0.18, Z(100), Z(262), 'chrome', seg=8, name='cage_rod')
g.cyl('z', (C, AY), 1.0, Z(130), Z(215), 'steel', seg=16, name='coil_core')

# Barrel with brass fittings, ribbed capacitor shroud
g.cyl('z', (C, AY), 0.3, Z(110), Z(392), 'steel', seg=8, name='barrel')
for px in (128, 156):
    g.cyl('z', (C, AY), 0.48, Z(px), Z(px + 14), 'brass', seg=8, name='barrel_fitting')
SY = Y(98)
g.cyl('z', (C, SY), 1.4, Z(300), Z(392), 'steel', seg=16, name='capacitor_shroud')
for px in range(304, 388, 11):
    g.cyl('z', (C, SY), 1.48, Z(px), Z(px + 4), 'gunmetal', seg=16, name='shroud_rib')

# Receiver, crenellated top rail, side glass tube, bulbs, grille, dial
box(-0.8, 0.8, 46, 122, 390, 500, 'darkmetal', name='receiver_front')
box(-0.8, 0.8, 40, 136, 500, 702, 'darkmetal', name='receiver')
box(-0.7, 0.7, 30, 42, 528, 692, 'gunmetal', name='top_rail')
for px in range(532, 690, 22):
    box(-0.6, 0.6, 22, 30, px, px + 11, 'gunmetal', name='rail_tooth')
box(0.78, 0.86, 84, 100, 440, 522, 'glow_blue', name='glass_tube')
box(0.76, 0.9, 80, 85, 434, 528, 'brass', name='glass_frame')
box(0.76, 0.9, 99, 104, 434, 528, 'brass', name='glass_frame')
box(0.88, 0.92, 86, 98, 440, 522, 'glow_blue', name='glass_tube_face')
# Three round gauge windows on the receiver side (both sides), each in a steel bezel
for px in (572, 608, 644):
    for x0, x1, gx0, gx1 in ((C + 0.8, C + 1.0, C + 0.95, C + 1.3), (C - 1.0, C - 0.8, C - 1.3, C - 0.95)):
        g.cyl('x', (Y(100), Z(px)), 0.78, x0, x1, 'steel', seg=16, name='window_bezel')
        g.cyl('x', (Y(100), Z(px)), 0.62, gx0, gx1, 'glass', seg=16, name='gauge_window')
box(-0.82, 0.82, 84, 118, 556, 662, 'gunmetal', name='window_plate')
g.cyl('x', (Y(152), Z(678)), 1.25, C + 0.8, C + 1.05, 'grille', seg=16, name='speaker_grille')
g.cyl('x', (Y(152), Z(678)), 1.35, C + 0.78, C + 0.9, 'steel', seg=16, name='grille_rim')
# Antenna wires on top of the cage and receiver
g.box((C - 0.07, AY + 0.1, Z(57)), (C + 0.07, AY + 1.4, Z(63)), 'steel', name='antenna_mast')
g.wire([(C, AY + 1.3, Z(60)), (C, AY + 2.2, Z(52)), (C, AY + 2.6, Z(62))], 0.12, 'steel', name='antenna')
g.cyl('x', (Y(160), Z(640)), 0.45, C + 0.8, C + 1.15, 'brass', seg=8, name='dial_knob')

# Battery box (vertical slats), battery pack (horizontal slats), hanging cable
box(-0.75, 0.75, 110, 176, 380, 535, 'ribs_v', name='battery_box')
box(-0.82, 0.82, 106, 116, 376, 539, 'brass', name='box_cap_top')
box(-0.82, 0.82, 170, 180, 376, 539, 'brass', name='box_cap_bottom')
box(-0.82, 0.82, 110, 176, 376, 384, 'darkmetal', name='box_strap_front')
box(-0.82, 0.82, 110, 176, 531, 539, 'darkmetal', name='box_strap_rear')
box(-0.7, 0.7, 136, 200, 566, 650, 'ribs_h', name='battery_pack')
box(-0.78, 0.78, 136, 144, 562, 654, 'brass', name='pack_band_top')
box(-0.78, 0.78, 190, 206, 562, 654, 'darkmetal', name='pack_base')
box(-0.2, 0.2, 204, 214, 590, 626, 'brass', name='pack_terminal')
g.wire([(C + 0.9, Y(162), Z(512)), (C + 0.9, Y(240), Z(522)), (C + 0.9, Y(290), Z(565)),
        (C + 0.9, Y(290), Z(595)), (C + 0.9, Y(215), Z(602))], 0.3, 'rubber', name='cable')
g.cyl('x', (Y(162), Z(512)), 0.4, C + 0.7, C + 1.15, 'brass', seg=8, name='cable_plug')

# Copper valve at the back of the receiver top
g.cyl('z', (C, Y(46)), 0.5, Z(700), Z(745), 'copper', seg=8, name='valve_body')
g.cyl('z', (C, Y(46)), 0.7, Z(742), Z(770), 'bulb', seg=8, name='valve_bulb')

box(-0.55, 0.55, 120, 172, 745, 815, 'darkmetal', name='grip_housing')
# Trigger and dark red wooden stock with tusks
box(-0.1, 0.1, 130, 185, 684, 694, 'steel', name='trigger')
# Rifle stock: wrist set into the receiver, widening (comb up, belly down) towards the butt
box(-0.7, 0.7, 84, 140, 690, 800, 'wood_maroon', name='stock_wrist')
box(-0.73, 0.73, 76, 162, 800, 900, 'wood_maroon', name='stock_mid')
box(-0.76, 0.76, 66, 188, 900, 1000, 'wood_maroon', name='stock_butt')
box(-0.82, 0.82, 64, 192, 998, 1015, 'darkmetal', name='butt_plate')
for dx in (-0.45, 0.45):
    g.wire([(C + dx, Y(138), Z(703)), (C + dx, Y(185), Z(735)), (C + dx, Y(190), Z(780))], 0.2, 'steel', name='tusk')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.48, 0.48, 0.48]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.48, 0.48, 0.48]},
    "ground": {"translation": [0, 2, 0], "scale": [0.32, 0.32, 0.32]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.38, 0.38, 0.38]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
