#!/usr/bin/env python3
"""M14 (BO1 zombies wall buy): full-length walnut stock, vented upper handguard, gas cylinder and front
band, slotted flash hider with bayonet lug, receiver with operating rod handle and aperture rear sight,
20-round magazine ahead of the guard, straight wrist and steel butt plate. Proportions from the BO2 menu
render (480 px muzzle-to-butt -> 42 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('m14_v3', offset=(0, -2, -0.8))
C = 8
BY = 9.0

# Flash hider (slotted), barrel, front sight, gas cylinder, front band
g.cyl('z', (C, BY), 0.75, -15, -11.0, 'gunmetal', seg=8, name='flash_hider')
for z in (-14.4, -13.4, -12.4):
    g.box((C - 0.52, BY - 0.15, z), (C + 0.52, BY + 0.15, z + 0.4), 'rubber', name='hider_slot')
g.box((C - 0.2, BY + 0.4, -11.6), (C + 0.2, BY + 1.5, -11.0), 'darkmetal', name='front_sight')
g.box((C - 0.35, BY + 0.4, -11.8), (C + 0.35, BY + 0.9, -10.8), 'darkmetal', name='front_sight_base')
g.box((C - 0.18, BY - 1.0, -12.0), (C + 0.18, BY - 0.45, -11.2), 'darkmetal', name='bayonet_lug')
g.cyl('z', (C, BY), 0.36, -11.0, 2.0, 'steel', seg=8, name='barrel')
g.cyl('z', (C, BY - 0.8), 0.4, -3.0, 4.0, 'steel', seg=8, name='gas_cylinder')
g.box((C - 0.55, BY - 1.15, -1.2), (C + 0.55, BY + 0.45, 0.2), 'gunmetal', name='front_band')

# Stock: full length walnut from the front band to the butt, belly descending towards the butt
g.box((C - 0.75, 6.7, 0.2), (C + 0.75, 8.6, 17.6), 'darkmetal', name='forestock')
g.box((C - 0.68, 6.4, 4.0), (C + 0.68, 6.8, 14.0), 'darkmetal', name='forestock_belly')
g.box((C - 0.72, 8.6, 0.6), (C + 0.72, 9.9, 15.2), 'gunmetal', name='handguard')
for z in (2.0, 4.2, 6.4, 8.6, 10.8):
    g.box((C - 0.74, 9.0, z), (C + 0.74, 9.5, z + 1.2), 'rubber', name='handguard_vent')
g.box((C - 0.65, 6.0, 17.6), (C + 0.65, 8.7, 23.6), 'darkmetal', name='wrist')
g.box((C - 0.8, 4.6, 23.0), (C + 0.8, 8.7, 28.0), 'darkmetal', name='butt')
g.box((C - 0.82, 4.6, 28.0), (C + 0.82, 8.6, 28.5), 'gunmetal', name='butt_plate')

# Gas block and front sight tower
g.box((C - 0.4, BY - 1.2, -8.4), (C + 0.4, BY + 1.4, -6.2), 'darkmetal', name='sight_tower')

# Receiver: body, operating rod and handle, bolt, aperture rear sight
g.box((C - 0.66, 8.4, 15.0), (C + 0.66, 10.4, 21.0), 'steel', name='receiver')
g.box((C - 0.7, 8.6, 13.0), (C - 0.6, 9.2, 18.4), 'darkmetal', name='op_rod')
g.box((C - 1.1, 8.7, 16.8), (C - 0.6, 9.3, 17.6), 'steel', name='op_rod_handle')
g.box((C - 0.45, 10.4, 16.2), (C + 0.45, 10.8, 18.0), 'darkmetal', name='bolt')
g.box((C - 0.5, 10.4, 19.4), (C + 0.5, 11.4, 20.6), 'darkmetal', name='rear_sight')
g.cyl('x', (11.0, 20.0), 0.22, C - 0.6, C + 0.6, 'steel', seg=8, name='rear_sight_knob')

# Pistol grip behind the guard
g.box((C - 0.62, 2.6, 20.0), (C + 0.62, 6.6, 22.4), 'darkmetal', rot=('x', -22.5, (C, 6.4, 21.2)), name='pistol_grip')

# Magazine, trigger guard, trigger
g.box((C - 0.62, 1.2, 15.4), (C + 0.62, 6.7, 19.0), 'darkmetal', name='magazine')
for y in (4.2, 5.3):
    g.box((C - 0.57, y, 16.1), (C + 0.57, y + 0.25, 18.7), 'gunmetal', name='mag_rib')
g.box((C - 0.67, 0.95, 15.3), (C + 0.67, 1.25, 19.1), 'gunmetal', name='mag_floorplate')
g.box((C - 0.25, 5.2, 18.4), (C + 0.25, 5.5, 21.2), 'gunmetal', name='guard_bottom')
g.box((C - 0.25, 5.2, 20.9), (C + 0.25, 6.6, 21.2), 'gunmetal', name='guard_rear')
g.box((C - 0.1, 5.7, 19.3), (C + 0.1, 6.6, 19.6), 'steel', name='trigger')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.64, 0.7, 9.2, 10.0, 17.2, 19.0, 'rubber', name='ejection_port')
sb(0.64, 0.9, 8.5, 8.8, 19.6, 20.6, 'steel', name='selector')
g.ring('x', (7.5, -0.6), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (4.3, 25.5), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
sb(-0.12, 0.12, 10.7, 11.1, 20.6, 20.68, 'rubber', name='aperture')
g.cyl('x', (7.3, 22.0), 0.13, C - 0.7, C + 0.7, 'steel', seg=8, name='wrist_screw')
for y in (7.8, 5.4):
    g.cyl('x', (y, 26.0), 0.14, C - 0.86, C + 0.86, 'steel', seg=8, name='butt_screw')
g.ring('z', (C, BY - 0.8), 0.4, 0.55, -3.0, -2.6, 'darkmetal', name='gas_nut')

# Detail pass 2
for s in (-1, 1):
    for z in (16.4, 18.5, 20.3):
        sb(s * 0.66, s * 0.7, 8.5, 10.3, z, z + 0.12, 'rubber', name='receiver_seam')
    sb(s * 0.76, s * 0.8, 8.6, 9.9, 14.8, 15.1, 'steel', name='handguard_ferrule')
    sb(s * 0.3, s * 0.45, BY + 0.9, BY + 1.5, -11.6, -11.0, 'darkmetal', name='sight_ear')
    sb(s * 0.8, s * 0.83, 4.9, 8.4, 25.4, 25.6, 'rubber', name='stock_seam')
sb(0.62, 0.76, 5.9, 6.5, 15.4, 16.2, 'steel', name='mag_release')
sb(-0.25, 0.25, 5.2, 6.6, 18.2, 18.5, 'gunmetal', name='guard_front')
sb(-0.78, 0.78, 8.5, 9.95, 7.9, 8.1, 'steel', name='handguard_clip')
g.ring('z', (C, BY - 0.8), 0.4, 0.52, -1.6, -1.3, 'steel', name='piston_ring')
for y in (5.0, 6.0, 7.0, 8.0):
    sb(-0.65, 0.65, y, y + 0.2, 28.5, 28.62, 'rubber', name='butt_ridge')

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
