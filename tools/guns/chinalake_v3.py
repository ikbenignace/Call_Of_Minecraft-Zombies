#!/usr/bin/env python3
"""China Lake (BO1): fat 40 mm barrel with tall front sight, ribbed pump, magazine tube underneath,
receiver with ladder sight, wooden grip-stock with butt plate. 38 units; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('chinalake_v3', offset=(0, 0, 0))
C = 8
BY = 6.2

g.cyl('z', (C, BY), 1.15, -15, -3.0, 'gunmetal', seg=16, name='barrel')
g.cyl('z', (C, BY), 0.75, -15.05, -14.9, 'rubber', seg=16, name='bore')
g.box((C - 0.25, BY + 1.1, -14.2), (C + 0.25, BY + 1.9, -13.6), 'darkmetal', name='front_sight')
g.cyl('z', (C, 3.8), 1.0, -13.4, -3.0, 'worn_steel', seg=16, name='mag_tube')
for z in (-13.0, -12.0, -11.0, -10.0, -9.0, -8.0, -7.0):
    g.cyl('z', (C, 3.8), 1.12, z, z + 0.45, 'rust_brown', seg=16, name='spring_coil')
g.box((C - 0.5, 4.7, -12.6), (C + 0.5, 5.3, -12.0), 'steel', name='tube_band')
g.box((C - 1.1, 2.9, -4.6), (C + 1.1, 7.8, 3.7), 'darkmetal', name='receiver')
g.box((C - 1.12, 3.4, -4.0), (C + 1.12, 5.0, -0.4), 'oldmetal', name='pump_slide')
g.box((C - 0.9, 1.6, -2.6), (C + 0.9, 2.9, 3.4), 'darkmetal', name='trigger_housing')
g.box((C - 0.3, 7.8, -8.0), (C + 0.3, 8.6, 3.4), 'darkmetal', name='ladder_rail')
g.box((C - 0.45, 8.6, 1.6), (C + 0.45, 9.0, 3.4), 'darkmetal', name='ladder_sight')
g.box((C + 1.08, 5.6, -3.4), (C + 1.14, 7.2, 1.2), 'rubber', name='loading_port')
g.box((C - 0.22, 0.9, -0.6), (C + 0.22, 1.2, 2.8), 'darkmetal', name='guard_bottom')
g.box((C - 0.22, 1.2, -0.6), (C + 0.22, 1.6, -0.3), 'darkmetal', name='guard_front')
g.box((C - 0.1, 1.1, 0.8), (C + 0.1, 1.6, 1.1), 'steel', name='trigger')
g.box((C - 0.95, 3.3, 3.7), (C + 0.95, 8.0, 10.5), 'wood', name='wrist')
g.box((C - 1.0, 5.4, 10.5), (C + 1.0, 8.3, 22.4), 'wood', name='comb')
g.box((C - 0.98, 2.2, 10.5), (C + 0.98, 5.4, 17.6), 'wood', name='stock_belly')
g.box((C - 1.0, 0.6, 17.6), (C + 1.0, 5.6, 22.4), 'wood', name='heel')
g.box((C - 1.04, 0.5, 22.4), (C + 1.04, 8.4, 22.9), 'rubber', name='butt_plate')

# Details: muzzle crown, windage knobs, receiver screws, pump grooves, cross-bolt safety, sling swivels
g.ring('z', (C, BY), 0.75, 1.15, -15.3, -15.0, 'steel', name='muzzle_crown')
for side in (-1, 1):
    g.cyl('x', (8.8, 2.5), 0.3, C + side * 0.45 if side > 0 else C - 0.6, C + 0.6 if side > 0 else C - 0.45, 'steel', seg=8, name='windage_knob')
for z in (-3.8, -3.1, -2.4, -1.7, -1.0):
    g.box((C - 1.14, 3.5, z), (C + 1.14, 4.9, z + 0.2), 'rubber', name='pump_groove')
for y, z in ((7.0, 2.5), (3.6, 2.5), (7.0, -3.0)):
    g.cyl('x', (y, z), 0.12, C - 1.18, C + 1.18, 'steel', seg=8, name='receiver_screw')
g.cyl('x', (7.1, 3.0), 0.2, C - 1.2, C + 1.2, 'gunmetal', seg=8, name='safety_bolt')
g.ring('x', (2.4, -12.3), 0.2, 0.45, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (0.5, 19.5), 0.2, 0.45, C - 0.12, C + 0.12, 'steel', name='rear_swivel')

# Details pass 2: barrel bands, tube cap, trigger pin, rail slots, butt plate screws
for z in (-11.0, -6.5):
    g.ring('z', (C, BY), 1.1, 1.28, z, z + 0.6, 'steel', name='barrel_band')
g.cyl('z', (C, 3.8), 1.05, -13.7, -13.4, 'steel', seg=16, name='tube_cap')
g.cyl('x', (2.2, 1.5), 0.15, C - 0.95, C + 0.95, 'steel', seg=8, name='trigger_pin')
for z in (-7.0, -5.8, -4.6):
    g.box((C - 0.31, 8.0, z), (C + 0.31, 8.4, z + 0.4), 'rubber', name='rail_slot')
for x in (-0.5, 0.5):
    g.cyl('z', (C + x, 6.8), 0.12, 22.8, 23.0, 'steel', seg=8, name='butt_screw')
    g.cyl('z', (C + x, 1.8), 0.12, 22.8, 23.0, 'steel', seg=8, name='butt_screw')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.44, 0.44, 0.44]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.44, 0.44, 0.44]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.54, 0.54, 0.54]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.54, 0.54, 0.54]},
    "ground": {"translation": [0, 2, 0], "scale": [0.36, 0.36, 0.36]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.44, 0.44, 0.44]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
