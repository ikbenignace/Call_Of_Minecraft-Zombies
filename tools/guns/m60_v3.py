#!/usr/bin/env python3
"""M60 (BO1): flash hider, barrel with carry handle and gas cylinder, bipod, round finned handguard,
receiver with feed cover and rear sight, olive ammo box with belt, pistol grip, big black stock.
44 units; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('m60_v3', offset=(0, 1, -2))
C = 8


def Y(v):
    return v - 1


def Z(v):
    return v + 1


# Flash hider, barrel, front sight block, gas cylinder, bipod, carry handle
g.cyl('z', (C, Y(10.3)), 0.6, Z(-16), Z(-13.5), 'gunmetal', seg=8, name='flash_hider')
g.cyl('z', (C, Y(10.3)), 0.36, Z(-13.5), Z(-2.0), 'steel', seg=8, name='barrel')
g.box((C - 0.3, Y(9.1), Z(-13.5)), (C + 0.3, Y(12.6), Z(-11.5)), 'darkmetal', name='front_sight')
g.cyl('z', (C, Y(9.6)), 0.5, Z(-13.5), Z(-3.0), 'gunmetal', seg=8, name='gas_cylinder')
g.wire([(C - 0.45, Y(8.3), Z(-8.7)), (C - 0.45, Y(4.8), Z(-9.7))], 0.22, 'gunmetal', name='bipod_leg')
g.wire([(C + 0.45, Y(8.3), Z(-8.7)), (C + 0.45, Y(4.8), Z(-9.7))], 0.22, 'gunmetal', name='bipod_leg')
g.wire([(C - 0.45, Y(4.8), Z(-9.7)), (C - 0.45, Y(3.0), Z(-10.9))], 0.22, 'gunmetal', name='bipod_foot')
g.wire([(C + 0.45, Y(4.8), Z(-9.7)), (C + 0.45, Y(3.0), Z(-10.9))], 0.22, 'gunmetal', name='bipod_foot')
g.box((C - 0.2, Y(10.3), Z(-3.0)), (C + 0.2, Y(11.6), Z(-2.3)), 'darkmetal', name='handle_post')
g.box((C - 0.25, Y(10.6), Z(-11.5)), (C + 0.25, Y(11.6), Z(-2.3)), 'gunmetal', name='carry_handle')

# Round finned handguard
g.cyl('z', (C, Y(9.35)), 0.95, Z(-9.0), Z(-1.5), 'polymer_dark', seg=8, name='handguard')
for z in (-8.4, -7.2, -6.0, -4.8, -3.6, -2.4):
    g.cyl('z', (C, Y(9.35)), 1.02, Z(z), Z(z + 0.3), 'rubber', seg=8, name='handguard_fin')

# Receiver, feed cover, rear sight, ammo box and belt
g.box((C - 0.85, Y(8.1), Z(-2.0)), (C + 0.85, Y(10.1), Z(12.5)), 'darkmetal', name='receiver')
g.box((C - 0.9, Y(10.1), Z(-2.3)), (C + 0.9, Y(11.6), Z(9.5)), 'gunmetal', name='feed_cover')
g.box((C - 0.35, Y(11.6), Z(7.5)), (C + 0.35, Y(12.6), Z(8.7)), 'darkmetal', name='rear_sight')
g.box((C - 1.45, Y(2.4), Z(-3.0)), (C + 0.9, Y(8.1), Z(5.0)), 'polymer_olive', name='ammo_box')
g.box((C - 1.5, Y(7.5), Z(-3.1)), (C + 0.95, Y(7.8), Z(5.1)), 'polymer_dark', name='ammo_box_lid')
g.box((C - 1.5, Y(2.4), Z(-3.1)), (C + 0.95, Y(2.7), Z(5.1)), 'polymer_dark', name='ammo_box_base')
g.box((C - 1.0, Y(8.1), Z(1.4)), (C - 0.86, Y(10.0), Z(4.2)), 'gold', name='ammo_belt')

# Trigger guard, trigger, pistol grip
g.box((C - 0.22, Y(6.6), Z(8.4)), (C + 0.22, Y(6.9), Z(11.0)), 'gunmetal', name='guard_bottom')
g.box((C - 0.1, Y(6.9), Z(9.4)), (C + 0.1, Y(8.1), Z(9.7)), 'steel', name='trigger')
g.box((C - 0.7, Y(5.4), Z(9.6)), (C + 0.7, Y(8.1), Z(12.0)), 'polymer_dark', rot=('x', -22.5, (C, Y(8.1), Z(10.8))), name='grip')

# Big black stock with sloping belly and shoulder rest
g.box((C - 0.85, Y(6.6), Z(12.5)), (C + 0.85, Y(10.2), Z(18.0)), 'polymer_dark', name='stock_front')
g.box((C - 0.9, Y(5.4), Z(18.0)), (C + 0.9, Y(10.2), Z(22.5)), 'polymer_dark', name='stock_butt')
g.box((C - 0.88, Y(4.6), Z(20.5)), (C + 0.88, Y(5.4), Z(22.5)), 'polymer_dark', name='stock_heel')
g.box((C - 0.95, Y(4.6), Z(22.5)), (C + 0.95, Y(10.3), Z(23.0)), 'rubber', name='butt_plate')
g.box((C - 0.3, Y(10.2), Z(20.6)), (C + 0.3, Y(10.5), Z(22.5)), 'gunmetal', name='shoulder_rest')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.88, 0.95, Y(8.5), Y(9.8), Z(8.0), Z(10.5), 'rubber', name='ejection_port')
for s in (-1, 1):
    sb(s * 0.9, s * 1.0, Y(10.5), Y(11.3), Z(5.0), Z(6.0), 'steel', name='cover_latch')
    sb(s * 0.3, s * 0.55, Y(11.2), Y(12.6), Z(-13.3), Z(-11.7), 'darkmetal', name='sight_ear')
sb(0.85, 1.3, Y(9.0), Y(9.6), Z(5.0), Z(6.4), 'steel', name='cocking_handle')
for z in (-15.5, -14.8, -14.1):
    sb(-0.62, 0.62, Y(10.3) - 0.12, Y(10.3) + 0.12, Z(z), Z(z + 0.35), 'rubber', name='hider_slot')
g.ring('x', (Y(8.2), Z(-5.0)), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (Y(4.4), Z(21.5)), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
sb(-0.1, 0.1, Y(11.9), Y(12.4), Z(8.7), Z(8.76), 'rubber', name='aperture')
for z in (10.5, 0.0):
    g.cyl('x', (Y(9.0), Z(z)), 0.13, C - 0.9, C + 0.9, 'steel', seg=8, name='receiver_pin')

# Reference-driven details: feed tray, heat vents, gas nut, bipod collar, ammo box latches and handle
for s in (-1, 1):
    for z in (-1.0, 3.0):
        g.cyl('y', (C + s * 0.5, Z(z)), 0.14, Y(11.55), Y(11.72), 'steel', seg=8, name='cover_rivet')
    sb(s * 0.9, s * 1.02, Y(8.9), Y(9.9), Z(-7.4), Z(-6.4), 'rubber', name='heat_vent')
    sb(s * 0.9, s * 1.02, Y(8.9), Y(9.9), Z(-4.2), Z(-3.2), 'rubber', name='heat_vent')
    sb(s * 0.3, s * 0.6, Y(8.0), Y(8.5), Z(-9.2), Z(-8.2), 'steel', name='bipod_collar')
sb(-0.5, 0.5, Y(11.6), Y(11.85), Z(6.8), Z(9.2), 'steel', name='rear_sight_leaf')
g.ring('z', (C, Y(9.6)), 0.45, 0.65, Z(-13.5), Z(-12.9), 'steel', name='gas_nut')
sb(-1.58, -1.42, Y(5.6), Y(6.8), Z(0.0), Z(1.0), 'steel', name='box_latch')
sb(0.88, 1.02, Y(5.6), Y(6.8), Z(0.0), Z(1.0), 'steel', name='box_latch')
g.wire([(C - 0.3, Y(8.3), Z(0.3)), (C - 0.3, Y(8.9), Z(0.3)), (C + 0.0, Y(8.9), Z(0.3))], 0.2, 'steel', name='box_handle')
sb(-0.5, 0.5, Y(10.2), Y(10.5), Z(18.0), Z(20.5), 'rubber', name='stock_rest_pad')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.42, 0.42, 0.42]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.42, 0.42, 0.42]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 2, 0], "scale": [0.33, 0.33, 0.33]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
