#!/usr/bin/env python3
"""Dragunov SVD (BO1/BO2): slotted flash hider, long barrel, front sight, gas block, vented olive
handguard, receiver with top cover, PSO scope on its side mount, box magazine, skeleton thumbhole stock
with cheek rest. From the BO2 menu render (462 px -> 46 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('dragunov_v3', offset=(0, 0, 0))
C = 8


# Flash hider, barrel, front sight, gas block
g.cyl('z', (C, 7.0), 0.8, -14.5, -11.5, 'darkmetal', seg=8, name='flash_hider')
for z in (-14.2, -13.4, -12.6):
    g.box((C - 0.85, 6.4, z), (C + 0.85, 7.6, z + 0.4), 'rubber', name='hider_slot')
g.cyl('z', (C, 6.95), 0.8, -11.5, 2.5, 'gunmetal', seg=8, name='barrel')
g.box((C - 0.3, 7.8, -14.0), (C + 0.3, 10.6, -12.2), 'darkmetal', name='front_sight')
g.box((C - 0.5, 7.5, -3.0), (C + 0.5, 9.0, -1.0), 'darkmetal', name='gas_block')

# Vented olive handguard
g.box((C - 0.82, 6.2, -2.0), (C + 0.82, 10.0, 14.5), 'polymer_olive', name='handguard')
for z in (1.0, 3.5, 6.0):
    g.box((C - 0.84, 8.0, z), (C + 0.84, 9.0, z + 1.6), 'rubber', name='handguard_vent')

# Receiver, rear cover, safety lever
g.box((C - 0.78, 5.4, 2.5), (C + 0.78, 6.3, 16.0), 'darkmetal', name='receiver')
g.box((C - 0.78, 6.2, 14.5), (C + 0.78, 8.2, 16.0), 'darkmetal', name='receiver_rear')
g.box((C - 0.74, 8.2, 14.5), (C + 0.74, 10.1, 26.0), 'gunmetal', name='top_cover')
g.box((C - 0.8, 6.5, 7.0), (C + 0.8, 7.2, 13.0), 'steel', name='safety_lever')

# PSO scope with side mount
g.cyl('z', (C - 0.3, 11.4), 0.85, 15.0, 22.0, 'gunmetal', seg=8, name='scope_tube')
g.cyl('z', (C - 0.3, 11.4), 1.0, 22.0, 25.0, 'darkmetal', seg=8, name='scope_eyepiece')
g.cyl('z', (C - 0.3, 11.4), 1.0, 15.0, 16.5, 'darkmetal', seg=8, name='scope_objective')
g.box((C - 0.55, 12.0, 19.0), (C - 0.05, 12.6, 20.5), 'darkmetal', name='scope_turret')
g.box((C - 0.35, 10.0, 16.5), (C + 0.35, 10.7, 22.0), 'darkmetal', name='scope_mount')

# Box magazine and trigger guard
g.box((C - 0.6, 0.6, 15.2), (C + 0.6, 5.6, 18.6), 'gunmetal', name='magazine')
for y in (1.8, 3.0, 4.2):
    g.box((C - 0.62, y, 15.3), (C + 0.62, y + 0.25, 18.5), 'darkmetal', name='mag_rib')
g.box((C - 0.22, 3.2, 18.6), (C + 0.22, 3.6, 22.3), 'gunmetal', name='guard_bottom')
g.box((C - 0.1, 2.7, 20.0), (C + 0.1, 3.3, 20.4), 'steel', name='trigger')

# Thumbhole stock with cheek rest, grip and butt
g.box((C - 0.7, 5.4, 16.0), (C + 0.7, 7.8, 30.6), 'polymer_dark', name='stock_upper_bar')
g.box((C - 0.72, 7.8, 25.5), (C + 0.72, 8.2, 28.0), 'polymer_dark', name='cheek_rest')
g.box((C - 0.68, 3.6, 16.0), (C + 0.68, 5.4, 24.6), 'polymer_dark', name='stock_mid_bar')
g.box((C - 0.68, 0.5, 22.3), (C + 0.68, 5.4, 24.6), 'polymer_dark', name='stock_grip')
g.box((C - 0.7, 1.1, 22.3), (C + 0.7, 2.6, 30.6), 'polymer_dark', name='stock_lower_bar')
g.box((C - 0.7, 2.6, 28.2), (C + 0.7, 5.4, 30.6), 'polymer_dark', name='stock_rear_post')
g.box((C - 0.75, 1.1, 30.2), (C + 0.75, 8.0, 30.6), 'rubber', name='butt_pad')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.72, 0.78, 8.4, 9.4, 17.0, 20.0, 'rubber', name='ejection_port')
sb(0.72, 1.3, 8.6, 9.1, 14.8, 15.6, 'steel', name='bolt_handle')
g.cyl('x', (8.85, 15.2), 0.25, C + 1.2, C + 1.6, 'steel', seg=8, name='bolt_knob')
g.ring('x', (0.9, 29.5), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
g.cyl('z', (C - 0.3, 11.4), 0.9, 25.0, 25.5, 'rubber', seg=8, name='eyecup')
g.cyl('z', (C - 0.3, 11.4), 0.8, 14.8, 15.0, 'glass', seg=8, name='objective_lens')
for z in (17.5, 20.8):
    g.ring('z', (C - 0.3, 11.4), 0.85, 0.95, z, z + 0.4, 'steel', name='scope_band')
for s in (-1, 1):
    sb(s * 0.3, s * 0.55, 9.0, 10.6, -13.8, -12.4, 'darkmetal', name='sight_ear')
sb(-0.65, 0.65, 0.6, 0.85, 15.1, 18.7, 'steel', name='mag_baseplate')
sb(-0.3, 0.3, 10.0, 10.7, 12.0, 14.0, 'darkmetal', name='rear_sight')
for y, z in ((4.5, 20.0), (6.6, 28.0)):
    g.cyl('x', (y, z), 0.13, C - 0.76, C + 0.76, 'steel', seg=8, name='stock_screw')

# Detail pass 2
for s in (-1, 1):
    for z in (17.0, 24.0):
        sb(s * 0.74, s * 0.78, 8.3, 10.0, z, z + 0.12, 'darkmetal', name='cover_seam')
    sb(s * 0.68, s * 0.72, 1.0, 4.8, 22.6, 24.3, 'rubber', name='grip_panel')
    sb(s * 0.3, s * 0.5, 10.0, 10.7, 12.2, 13.8, 'darkmetal', name='rear_sight_ear')
    sb(s * 0.8, s * 0.86, 6.2, 6.4, -1.5, 14.0, 'steel', name='handguard_strip')
sb(-0.84, -0.7, 8.6, 9.4, 17.0, 24.0, 'steel', name='mount_rail')
sb(-0.85, 0.85, 6.2, 10.0, 14.0, 14.3, 'steel', name='handguard_rear_cap')
sb(-0.85, 0.85, 6.2, 10.0, -2.0, -1.7, 'steel', name='handguard_front_cap')
g.cyl('z', (C, 6.95), 0.86, -11.6, -11.2, 'steel', seg=8, name='barrel_collar')
sb(0.6, 0.75, 4.6, 5.4, 15.2, 15.9, 'steel', name='mag_release')
g.ring('z', (C - 0.3, 11.4), 1.0, 1.08, 23.0, 23.4, 'steel', name='focus_ring')
for y in (2.0, 3.6, 5.2, 6.8):
    sb(-0.65, 0.65, y, y + 0.3, 30.6, 30.72, 'darkmetal', name='butt_ridge')

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
