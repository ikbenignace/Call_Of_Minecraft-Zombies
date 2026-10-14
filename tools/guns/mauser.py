#!/usr/bin/env python3
"""Mauser C96 trench carbine: long barrel, wooden forend, fixed box magazine ahead of the trigger,
ribbed broomhandle grip and a full wooden stock. Rebuilt in cubes from the user's mesh model
(Java item models can't contain meshes). Measured from its side render, 24.7 px per unit."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('mauser', offset=(0, -3.5, 4))
C = 8


def Z(px):
    return -15 + (px - 12) / 24.7


def Y(py):
    return 11 - (py - 195) / 24.7


def box(x0, x1, py0, py1, pz0, pz1, mat, **kw):
    g.box((C + x0, Y(py1), Z(pz0)), (C + x1, Y(py0), Z(pz1)), mat, **kw)


# Barrel, muzzle crown, front sight, ribbed rear barrel extension
g.cyl('z', (C, Y(213)), 0.36, Z(14), Z(380), 'oldmetal', seg=8, name='barrel')
g.cyl('z', (C, Y(213)), 0.45, Z(10), Z(22), 'oldmetal', seg=8, name='muzzle_crown')
box(-0.12, 0.12, 196, 206, 18, 34, 'darkmetal', name='front_sight')
box(-0.3, 0.3, 203, 206, 15, 40, 'oldmetal', name='front_sight_base')
for px in range(288, 372, 12):
    g.cyl('z', (C, Y(212)), 0.52, Z(px), Z(px + 6), 'darkmetal', seg=8, name='barrel_rib')
g.cyl('z', (C, Y(212)), 0.45, Z(285), Z(378), 'oldmetal', seg=8, name='barrel_extension')

# Wooden forend: slim under the barrel, deepening in steps towards the receiver
box(-0.5, 0.5, 220, 230, 222, 270, 'wood_red', name='forend_tip')
box(-0.55, 0.55, 220, 238, 270, 315, 'wood_red', name='forend_front')
box(-0.6, 0.6, 220, 252, 315, 350, 'wood_red', name='forend_mid')
box(-0.62, 0.62, 220, 270, 350, 378, 'wood_red', name='forend_rear')
box(-0.64, 0.64, 220, 288, 378, 400, 'wood_red', name='forend_heel')

# Receiver: frame, top bolt, rear sight, hammer, side panel seams
box(-0.7, 0.7, 205, 290, 375, 592, 'darkmetal', name='frame')
box(-0.55, 0.55, 196, 206, 380, 585, 'oldmetal', name='bolt')
box(-0.3, 0.3, 188, 197, 540, 562, 'darkmetal', name='rear_sight')
box(-0.25, 0.25, 198, 226, 578, 600, 'oldmetal', rot=('x', 22.5, (C, Y(210), Z(588))), name='hammer')
box(-0.72, 0.72, 228, 232, 400, 570, 'oldmetal', name='frame_seam')
box(-0.72, 0.72, 240, 270, 548, 576, 'oldmetal', name='side_plate')

# Fixed box magazine ahead of the trigger, with floor plate and panel line
box(-0.62, 0.62, 288, 362, 395, 476, 'darkmetal', name='magazine')
box(-0.66, 0.66, 360, 366, 392, 479, 'oldmetal', name='mag_floorplate')
box(-0.64, 0.64, 318, 322, 397, 474, 'oldmetal', name='mag_seam')
box(-0.64, 0.64, 290, 362, 432, 436, 'oldmetal', name='mag_seam_v')

# Trigger guard ring and trigger
box(-0.2, 0.2, 288, 296, 482, 540, 'oldmetal', name='guard_bottom')
box(-0.2, 0.2, 262, 296, 478, 488, 'oldmetal', name='guard_front')
box(-0.2, 0.2, 262, 292, 534, 544, 'oldmetal', name='guard_rear')
box(-0.1, 0.1, 262, 288, 512, 520, 'steel', rot=('x', -22.5, (C, Y(270), Z(516))), name='trigger')

# Broomhandle grip, raked back, ribbed wood
GRIP = ('x', -22.5, (C, Y(285), Z(570)))
box(-0.62, 0.62, 285, 360, 538, 600, 'wood_ribbed', rot=GRIP, name='grip')
box(-0.66, 0.66, 355, 362, 536, 602, 'oldmetal', rot=GRIP, name='grip_cap')

# Stock: socket on the frame, slim wrist sloping down, then a butt with a steep comb and a long
# sloped belly (both 22.5° wedges) ending in a steel butt plate
box(-0.66, 0.66, 236, 292, 588, 620, 'darkmetal', name='stock_socket')
box(-0.58, 0.58, 258, 310, 615, 680, 'wood_red', name='wrist_front')
box(-0.6, 0.6, 264, 316, 680, 740, 'wood_red', name='wrist_mid')
box(-0.62, 0.62, 272, 322, 740, 800, 'wood_red', name='wrist_rear')
box(-0.7, 0.7, 240, 330, 800, 985, 'wood_red', name='butt')
box(-0.69, 0.69, 236, 262, 790, 840, 'wood_red', rot=('x', 45, (C, Y(262), Z(815))), name='comb')
box(-0.68, 0.68, 320, 346, 770, 985, 'wood_red', rot=('x', 22.5, (C, Y(333), Z(877))), name='belly')
box(-0.7, 0.7, 330, 362, 850, 985, 'wood_red', name='belly_fill')
box(-0.7, 0.7, 355, 388, 930, 985, 'wood_red', name='heel')
box(-0.72, 0.72, 244, 390, 985, 1000, 'oldmetal', name='butt_plate')
box(-0.71, 0.71, 300, 306, 830, 870, 'darkmetal', name='tally_marks')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 2, 0], "scale": [0.33, 0.33, 0.33]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.44, 0.44, 0.44]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
