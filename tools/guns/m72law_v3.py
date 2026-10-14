#!/usr/bin/env python3
"""M72 LAW (BO1): telescoping olive tube (narrow front section, wider rear), end caps, pop-up front and
rear sights, firing bar on top, folding trigger handle below, sling. 36 units; front towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('m72law_v3', offset=(0, -1, 3))
C = 8
TY = 9.0

g.cyl('z', (C, TY - 0.5), 1.3, -15, 2.0, 'polymer_olive', seg=16, name='front_tube')
g.cyl('z', (C, TY - 0.2), 1.55, 2.0, 20.0, 'polymer_olive', seg=16, name='rear_tube')
g.cyl('z', (C, TY - 0.5), 1.4, -15.2, -14.4, 'gunmetal', seg=16, name='front_cap')
g.cyl('z', (C, TY - 0.2), 1.8, 18.8, 20.8, 'gunmetal', seg=16, name='rear_cap')
g.cyl('z', (C, TY), 0.8, 20.8, 21.0, 'rubber', seg=16, name='rear_opening')
for z in (1.6, 6.0, 14.0):
    g.cyl('z', (C, TY - 0.2), 1.65, z, z + 0.35, 'gunmetal', seg=16, name='band')
g.box((C - 0.5, TY + 0.7, -1.0), (C + 0.5, TY + 1.7, -0.4), 'darkmetal', name='front_sight')
g.box((C - 0.08, TY + 1.7, -0.85), (C + 0.08, TY + 2.1, -0.55), 'darkmetal', name='front_sight_post')
g.box((C - 0.45, TY + 1.15, 12.6), (C + 0.45, TY + 2.2, 13.2), 'darkmetal', name='rear_sight')
g.box((C - 0.25, TY + 1.1, 4.0), (C + 0.25, TY + 1.5, 11.0), 'steel', name='firing_bar')
g.box((C - 0.3, TY + 1.4, 9.6), (C + 0.3, TY + 1.8, 10.6), 'rubber', name='trigger_button')
g.wire([(C, TY - 1.1, -2.5), (C, TY - 3.2, -1.1), (C, TY - 3.4, 0.5), (C, TY - 1.1, 0.9)], 0.5, 'steel', name='trigger_handle')
g.box((C - 0.5, TY - 1.6, -2.5), (C + 0.5, TY - 0.9, 1.1), 'steel', name='trigger_bracket')
g.box((C + 1.35, TY - 0.2, -2.0), (C + 1.5, TY + 0.2, 16.0), 'polymer_dark', name='sling')

# Details: sling swivels, warning label and hazard bands, sight aperture, muzzle and rear rings
for z in (-2.2, 16.2):
    g.ring('x', (TY - 0.2, z), 0.15, 0.38, C + 1.35, C + 1.55, 'steel', name='sling_swivel')
g.box((C - 1.62, TY - 0.7, 8.0), (C - 1.55, TY + 0.3, 13.0), 'label_white', name='warning_label')
for z in (4.5, 15.5):
    g.cyl('z', (C, TY - 0.2), 1.6, z, z + 0.5, 'hazard', seg=16, name='hazard_band')
g.box((C - 0.12, TY + 1.5, 12.57), (C + 0.12, TY + 1.9, 12.6), 'rubber', name='sight_aperture')
g.ring('z', (C, TY - 0.5), 1.0, 1.25, -15.4, -15.2, 'steel', name='muzzle_ring')
g.ring('z', (C, TY), 0.8, 1.1, 20.8, 21.3, 'steel', name='rear_ring')

# Details pass 2: top seam, hinge pins, rivets, safety pin, rear cap bolts
g.box((C - 0.12, TY + 0.7, -14.0), (C + 0.12, TY + 0.95, 0.5), 'darkmetal', name='top_seam')
g.cyl('x', (TY + 1.2, -0.7), 0.1, C - 0.55, C + 0.55, 'steel', seg=8, name='front_sight_hinge')
g.cyl('x', (TY + 1.5, 12.9), 0.1, C - 0.5, C + 0.5, 'steel', seg=8, name='rear_sight_hinge')
g.cyl('x', (TY - 1.25, -0.7), 0.15, C - 0.6, C + 0.6, 'steel', seg=8, name='handle_pivot')
for side in (-1, 1):
    g.cyl('x', (TY - 0.2, 18.2), 0.12, C + side * 1.4 if side > 0 else C - 1.62, C + 1.62 if side > 0 else C - 1.4, 'steel', seg=8, name='cap_rivet')
g.ring('x', (TY + 0.2, 6.5), 0.2, 0.4, C - 1.62, C - 1.45, 'steel', name='safety_pin')
for dy, dx in ((1.75, 0), (-1.75, 0)):
    g.box((C - 0.15, TY - 0.2 + dy - 0.1, 19.4), (C + 0.15, TY - 0.2 + dy + 0.1, 20.2), 'steel', name='cap_bolt')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, 1.5], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"translation": [0, 3, 1.5], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"translation": [1, 2, -2], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"translation": [1, 2, -2], "scale": [0.55, 0.55, 0.55]},
    "ground": {"translation": [0, 2, 0], "scale": [0.36, 0.36, 0.36]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.44, 0.44, 0.44]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
