#!/usr/bin/env python3
"""BO2 MP5 (A3, retractable stock, curved 30-round magazine).

Proportions measured from the BO2 side view: muzzle at z=-15, butt pad at z=13.8, receiver top at
y=10. 1 unit ≈ 2 cm of the real gun.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('mp5', offset=(0, 0, 5.5))
C = 8

# Barrel and tri-lug muzzle
g.cyl('z', (C, 8.35), 0.42, -14.2, -10.4, 'darkmetal', seg=8, name='barrel')
g.cyl('z', (C, 8.35), 0.55, -15.0, -14.2, 'gunmetal', seg=8, name='muzzle_lugs')
for y in (8.9, 7.8):
    g.box((C - 0.18, y - 0.12, -15.0), (C + 0.18, y + 0.12, -14.4), 'gunmetal', name='lug')

# Front sight: base block + hooded ring + post
g.box((C - 0.75, 7.9, -12.1), (C + 0.75, 10.1, -11.0), 'gunmetal', name='front_sight_base')
g.ring('z', (C, 10.95), 0.55, 0.9, -11.95, -11.15, 'gunmetal', name='front_sight_hood')
g.box((C - 0.1, 10.1, -11.7), (C + 0.1, 10.75, -11.4), 'darkmetal', name='front_sight_post')

# Cocking tube above the barrel, cocking handle on the left
g.cyl('z', (C, 9.55), 0.5, -11.0, -3.4, 'gunmetal', seg=8, name='cocking_tube')
g.box((C - 1.35, 9.25, -10.1), (C - 0.9, 9.85, -9.2), 'gunmetal', name='cocking_handle')
g.cyl('x', (9.55, -9.65), 0.28, C - 2.0, C - 1.3, 'darkmetal', seg=8, name='cocking_knob')

# Handguard: slim polymer, rounded underside, front cap
g.box((C - 1.25, 7.35, -10.5), (C + 1.25, 9.15, -3.4), 'darkmetal', name='handguard')
g.cyl('z', (C, 7.95), 0.75, -10.3, -3.6, 'darkmetal', seg=8, name='handguard_belly')
g.box((C - 1.3, 7.2, -10.75), (C + 1.3, 9.2, -10.5), 'gunmetal', name='handguard_cap')
for z in (-9.2, -7.8, -6.4, -5.0):
    g.box((C - 1.3, 7.55, z), (C + 1.3, 7.75, z + 0.7), 'rubber', name='handguard_vent')

# Upper receiver: stamped body with rounded top, side flutes, front trunnion, claw-mount dimples
g.box((C - 1.15, 7.6, -3.6), (C + 1.15, 9.6, 7.7), 'gunmetal', name='receiver')
g.cyl('z', (C, 9.25), 0.95, -3.6, 7.7, 'gunmetal', seg=8, name='receiver_top')
g.box((C - 1.25, 7.4, -3.9), (C + 1.25, 9.95, -2.7), 'gunmetal', name='trunnion')
g.box((C - 1.2, 8.85, -1.6), (C + 1.2, 9.3, 6.3), 'rubber', name='side_flute')
g.box((C - 1.21, 9.3, -1.6), (C + 1.21, 9.4, 6.3), 'steel', name='side_flute_edge')
for z in (-2.4, 3.0):
    g.box((C - 0.45, 10.05, z), (C + 0.45, 10.3, z + 0.5), 'darkmetal', name='claw_dimple')
g.box((C + 1.14, 8.1, -0.2), (C + 1.2, 9.0, 2.6), 'rubber', name='ejection_port')

# Rear sight drum with protective ears
g.box((C - 1.0, 9.95, 3.9), (C + 1.0, 10.4, 5.9), 'gunmetal', name='rear_sight_base')
g.cyl('x', (10.9, 4.9), 0.55, C - 0.75, C + 0.75, 'darkmetal', seg=8, name='rear_sight_drum')
for x0 in (C - 1.05, C + 0.75):
    g.box((x0, 10.2, 4.0), (x0 + 0.3, 11.55, 5.8), 'gunmetal', name='rear_sight_ear')

# Lower receiver / trigger group, selector, magwell, mag release
g.box((C - 1.0, 5.6, -0.6), (C + 1.0, 7.7, 7.0), 'darkmetal', name='trigger_group')
g.box((C - 1.06, 6.0, 0.6), (C - 0.98, 7.35, 5.0), 'gunmetal', name='trigger_group_panel')
g.box((C - 1.2, 6.55, 5.4), (C - 0.98, 7.05, 6.6), 'steel', name='selector')
g.box((C - 1.0, 5.45, -2.65), (C + 1.0, 7.65, -0.5), 'gunmetal', name='magwell')
g.box((C - 0.4, 5.2, -0.55), (C + 0.4, 5.6, 0.25), 'darkmetal', name='mag_release')

# Curved magazine: short straight top, long section tilted 22.5° forward, floor plate
g.box((C - 0.65, 4.6, -2.45), (C + 0.65, 5.5, -0.7), 'darkmetal', name='mag_top')
g.box((C - 0.65, -0.6, -2.45), (C + 0.65, 4.6, -0.7), 'darkmetal', rot=('x', 22.5, (C, 4.6, -1.575)), name='mag_body')
g.box((C - 0.8, -0.95, -2.6), (C + 0.8, -0.6, -0.55), 'gunmetal', rot=('x', 22.5, (C, 4.6, -1.575)), name='mag_floorplate')
g.box((C - 0.78, 0.4, -1.9), (C + 0.78, 4.4, -1.3), 'gunmetal', rot=('x', 22.5, (C, 4.6, -1.575)), name='mag_rib')

# Trigger guard and trigger
g.box((C - 0.4, 4.05, 0.9), (C + 0.4, 4.4, 4.0), 'gunmetal', name='guard_bottom')
g.box((C - 0.4, 4.05, 0.9), (C + 0.4, 5.6, 1.25), 'gunmetal', name='guard_front')
g.box((C - 0.15, 4.75, 2.0), (C + 0.15, 5.6, 2.35), 'steel', rot=('x', -22.5, (C, 5.6, 2.2)), name='trigger')

# Pistol grip, raked back 22.5°, with finger bump and stippled panels
GRIP = ('x', -22.5, (C, 5.6, 5.6))
g.box((C - 0.8, 1.4, 4.75), (C + 0.8, 5.6, 6.6), 'darkmetal', rot=GRIP, name='grip')
for y0 in (2.1, 3.3):
    g.box((C - 0.7, y0, 4.45), (C + 0.7, y0 + 0.75, 4.8), 'darkmetal', rot=GRIP, name='grip_finger_bump')
g.box((C - 0.7, 1.6, 6.55), (C + 0.7, 4.9, 6.85), 'darkmetal', rot=GRIP, name='grip_backstrap')
g.box((C - 0.86, 1.9, 5.0), (C + 0.86, 4.7, 6.4), 'rubber', rot=GRIP, name='grip_stipple')
g.box((C - 0.85, 1.15, 4.6), (C + 0.85, 1.45, 6.9), 'gunmetal', rot=GRIP, name='grip_base')

# Push pins holding the trigger group
for z in (-0.1, 6.4):
    g.cyl('x', (7.2, z), 0.2, C - 1.12, C + 1.12, 'steel', seg=8, name='push_pin')

# Retractable stock: end cap, twin rails, butt plate with rubber pad and cheek hook
g.box((C - 1.1, 7.6, 7.6), (C + 1.1, 9.95, 8.6), 'gunmetal', name='stock_housing')
for x0 in (C - 1.0, C + 0.5):
    g.box((x0, 8.35, 8.6), (x0 + 0.5, 8.85, 12.3), 'steel', name='stock_rail')
g.box((C - 1.1, 5.0, 12.2), (C + 1.1, 9.9, 12.7), 'darkmetal', name='butt_plate')
g.box((C - 1.2, 4.6, 12.7), (C + 1.2, 10.0, 13.3), 'rubber', name='butt_pad')
g.box((C - 1.2, 4.6, 12.3), (C + 1.2, 5.1, 13.2), 'rubber', name='butt_toe')
g.box((C - 1.0, 9.4, 11.9), (C + 1.0, 10.3, 13.2), 'darkmetal', name='cheek_hook')
g.box((C - 0.25, 5.2, 11.6), (C + 0.25, 8.4, 12.2), 'steel', rot=('x', -22.5, (C, 8.4, 11.9)), name='stock_brace')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.7, 0.7, 0.7]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.7, 0.7, 0.7]},
    "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
