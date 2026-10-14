#!/usr/bin/env python3
"""FN FAL (BO2): flash hider, slim barrel, tall gas block / front sight, long honey-wood handguard with
three oval vents, black receiver, straight magazine, raked black grip, honey-wood stock with butt plate.
Measured from the BO2 menu render (scaled to 760 px wide) at 17.67 px per unit; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('fal_v3', offset=(0, -4, 1.5))
C = 8


def Z(v):
    return v - 1.5


def Y(v):
    return v + 4


def box(hw, y0, y1, z0, z1, mat, **kw):
    g.box((C - hw, Y(y0), Z(z0)), (C + hw, Y(y1), Z(z1)), mat, **kw)


BY = Y(4.25)
# Flash hider (slotted), barrel, sling bands
g.cyl('z', (C, BY), 0.85, Z(-13.5), Z(-9.5), 'darkmetal', seg=8, name='flash_hider')
for z in (-13.2, -12.4, -11.6, -10.8):
    box(0.6, 3.6, 4.9, z, z + 0.4, 'rubber', name='hider_slot')
g.cyl('z', (C, BY), 0.5, Z(-9.5), Z(0.0), 'gunmetal', seg=8, name='barrel')
g.cyl('z', (C, BY), 0.85, Z(-9.5), Z(-5.5), 'darkmetal', seg=8, name='barrel_band')
g.cyl('z', (C, BY), 0.65, Z(-5.0), Z(-2.5), 'darkmetal', seg=8, name='barrel_band')

# Gas block with tall front sight and lower lug
box(0.55, 3.3, 6.5, -1.3, 2.1, 'darkmetal', name='gas_block')
box(0.18, 6.5, 7.3, 0.0, 1.5, 'darkmetal', name='front_sight')
box(0.45, 6.2, 6.8, -0.5, 2.0, 'darkmetal', name='front_sight_ears')
box(0.25, 2.4, 3.4, 0.0, 1.2, 'darkmetal', name='bayonet_lug')

# Handguard: honey wood, rounded top edge, three oval vents
box(0.9, 3.0, 6.0, 2.1, 12.2, 'wood_honey', name='handguard')
g.cyl('z', (C, Y(6.0)), 0.78, Z(2.1), Z(12.2), 'wood_honey', seg=8, name='handguard_top')
for z in (4.0, 5.9, 7.8):
    box(0.98, 5.0, 5.7, z, z + 1.1, 'rubber', name='handguard_vent')
box(1.0, 3.0, 6.7, -1.5, -0.8, 'darkmetal', name='handguard_cap')
box(0.8, 2.2, 3.1, 2.1, 12.2, 'wood_honey', name='handguard_lower')

# Receiver: body, top cover, charging handle, rear sight, magwell
box(0.8, 0.8, 5.5, 12.2, 20.5, 'darkmetal', name='receiver')
box(0.8, 3.0, 5.5, 20.5, 22.5, 'darkmetal', name='receiver_rear')
box(0.72, 5.3, 5.8, 12.2, 22.0, 'darkmetal', name='receiver_cover')
box(0.82, 3.6, 4.0, 12.2, 21.0, 'darkmetal', name='receiver_rail')
box(0.25, 4.1, 5.0, 12.2, 13.2, 'steel', name='charging_handle')
g.box((C - 1.35, Y(4.1), Z(12.4)), (C - 0.8, Y(4.9), Z(13.2)), 'steel', name='charging_knob')
box(0.4, 5.5, 6.5, 19.0, 21.4, 'darkmetal', name='rear_sight')
box(0.85, 0.5, 2.4, 12.5, 16.8, 'darkmetal', name='magwell')

# Straight magazine with ribs
box(0.62, -4.0, 0.9, 13.5, 16.5, 'gunmetal', name='magazine')
for y in (-2.8, -1.5, -0.2):
    box(0.64, y, y + 0.3, 13.6, 16.4, 'darkmetal', name='mag_rib')
box(0.66, -4.0, -3.4, 13.3, 16.7, 'darkmetal', name='mag_floorplate')

# Trigger guard, trigger, grip
box(0.25, 0.45, 0.8, 16.5, 20.0, 'darkmetal', name='guard_bottom')
box(0.25, 0.45, 2.0, 16.3, 16.7, 'darkmetal', name='guard_front')
box(0.12, 0.8, 2.0, 17.2, 17.6, 'steel', name='trigger')
box(0.65, -2.1, 1.0, 18.0, 21.5, 'rubber', name='grip')
box(0.68, -2.3, -2.0, 18.0, 21.5, 'darkmetal', name='grip_cap')

# Stock: honey wood, deepening towards the butt (stepped belly), black butt plate
box(0.75, 2.9, 5.1, 20.5, 24.0, 'wood_honey', name='stock_wrist')
box(0.78, 1.6, 5.2, 24.0, 26.5, 'wood_honey', name='stock_mid')
box(0.8, 0.6, 5.2, 26.5, 28.1, 'wood_honey', name='stock_butt')
box(0.8, -0.7, 0.7, 26.5, 28.1, 'wood_honey', name='stock_heel')
box(0.82, -0.7, 5.3, 28.1, 28.6, 'darkmetal', name='butt_plate')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.78, 0.86, Y(3.0), Y(4.6), Z(14.0), Z(17.0), 'rubber', name='ejection_port')
sb(-0.8, -1.15, Y(2.2), Y(2.6), Z(17.8), Z(19.4), 'steel', name='selector')
g.ring('x', (Y(-0.9), Z(27.3)), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
g.ring('x', (BY - 0.75, Z(-3.75)), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='front_swivel')
for z in (14.0, 19.0):
    g.cyl('x', (Y(4.8), Z(z)), 0.13, C - 0.86, C + 0.86, 'steel', seg=8, name='rivet')
sb(-0.12, 0.12, Y(5.9), Y(6.3), Z(21.4), Z(21.46), 'rubber', name='aperture')
g.cyl('x', (Y(5.0), Z(0.4)), 0.3, C - 0.62, C + 0.62, 'steel', seg=8, name='gas_regulator')

# Detail pass 2
for s in (-1, 1):
    for z in (13.0, 20.2):
        sb(s * 0.8, s * 0.84, Y(1.2), Y(5.2), Z(z), Z(z + 0.12), 'gunmetal', name='receiver_seam')
    sb(s * 0.4, s * 0.55, Y(5.5), Y(6.5), Z(18.7), Z(19.1), 'steel', name='sight_ear')
    sb(s * 0.62, s * 0.69, Y(-1.8), Y(0.6), Z(18.4), Z(21.1), 'gunmetal', name='grip_panel')
sb(-0.3, 0.3, Y(5.8), Y(5.95), Z(12.5), Z(21.5), 'steel', name='cover_rail')
sb(-0.95, 0.95, Y(3.0), Y(6.0), Z(12.1), Z(12.3), 'steel', name='handguard_cap_steel')
sb(0.85, 0.95, Y(0.7), Y(1.4), Z(16.0), Z(16.8), 'steel', name='mag_release')
for y in (-0.3, 1.8, 3.9):
    sb(-0.7, 0.7, Y(y), Y(y + 0.4), Z(28.6), Z(28.72), 'rubber', name='butt_ridge')
g.cyl('x', (BY, Z(-7.5)), 0.13, C - 0.9, C + 0.9, 'steel', seg=8, name='band_bolt')
sb(-0.25, 0.25, Y(0.45), Y(1.4), Z(19.7), Z(20.0), 'darkmetal', name='guard_rear')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.42, 0.42, 0.42]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.42, 0.42, 0.42]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.52, 0.52, 0.52]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.52, 0.52, 0.52]},
    "ground": {"translation": [0, 2, 0], "scale": [0.34, 0.34, 0.34]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.44, 0.44, 0.44]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
