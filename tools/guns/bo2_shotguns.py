#!/usr/bin/env python3
"""BO2 shotguns from the BO2 icons: M1216 (bullpup rotary-tube shotgun), R870 MCS (short pump with
breacher muzzle, light and collapsible stock), S12 (AK-style semi-auto with a spiked muzzle brake).
Muzzle towards -Z."""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

C = 8


def sides(g, w, y0, y1, z0, z1, mat, name, d=0.08, side=0):
    for s in ((-1, 1) if side == 0 else (side,)):
        x0, x1 = sorted((C + s * (w - 0.03), C + s * (w + d)))
        g.box((x0, y0, z0), (x1, y1, z1), mat, name=name)


def screw(g, w, y, z, side=0, r=0.14):
    for s in ((-1, 1) if side == 0 else (side,)):
        x0, x1 = sorted((C + s * (w - 0.03), C + s * (w + 0.07)))
        g.cyl('x', (y, z), r, x0, x1, 'steel', seg=8, name='screw')


def drum_bolts(g, cy, cz, rad):
    for ang in (0, 72, 144, 216, 288):
        a = math.radians(ang)
        for s in (-1, 1):
            x0, x1 = sorted((C + s * 0.9, C + s * 1.12))
            g.cyl('x', (cy + rad * math.sin(a), cz + rad * math.cos(a)), 0.22, x0, x1, 'steel', seg=8, name='drum_bolt')


def display(scale):
    return {
        "thirdperson_righthand": {"translation": [0, 3, -2], "scale": [scale, scale, scale]},
        "thirdperson_lefthand": {"translation": [0, 3, -2], "scale": [scale, scale, scale]},
        "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [scale * 1.25] * 3},
        "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [scale * 1.25] * 3},
        "ground": {"translation": [0, 2, 0], "scale": [scale * 0.8] * 3},
        "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [scale * 1.05] * 3},
        "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
        "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
    }


def grip(g, z, top=7.0, mat='polymer_dark', length=4.2):
    GRIP = ('x', -22.5, (C, top, z))
    g.box((C - 0.68, top - length, z - 1.2), (C + 0.68, top, z + 1.2), mat, rot=GRIP, name='grip')
    g.box((C - 0.22, top - 1.1, z - 3.4), (C + 0.22, top - 0.8, z - 0.9), 'darkmetal', name='guard_bottom')
    g.box((C - 0.08, top - 0.8, z - 2.4), (C + 0.08, top, z - 2.1), 'steel', name='trigger')


def m1216():
    g = Gun('m1216_v3', offset=(0, -1, 2))
    g.cyl('z', (C, 8.2), 0.7, -15, -4.0, 'steel', seg=8, name='barrel')
    g.box((C - 0.35, 8.9, -8.5), (C + 0.35, 10.95, -6.5), 'darkmetal', name='front_sight')
    g.box((C - 0.5, 7.2, -8.3), (C + 0.5, 9.0, -6.7), 'darkmetal', name='front_sight_riser')
    g.box((C - 1.1, 5.2, -9.0), (C + 1.1, 7.45, 2.0), 'polymer_dark', name='tube_housing')
    g.cyl('z', (C, 5.2), 1.0, -8.0, 2.5, 'polymer_olive', seg=16, name='rotary_tubes')
    for z in (-7.8, -3.8, 0.2):
        g.cyl('z', (C, 5.2), 1.08, z, z + 0.5, 'polymer_dark', seg=16, name='tube_band')
    for z in (-7.0, -5.0, -3.0, -1.0):
        g.box((C - 1.12, 6.6, z), (C + 1.12, 7.2, z + 1.2), 'rubber', name='housing_vent')
    g.box((C - 1.0, 8.95, -2.5), (C + 1.0, 9.45, 9.0), 'darkmetal', name='top_rail')
    for z in range(-2, 9, 1):
        g.box((C - 1.02, 9.25, z + 0.2), (C + 1.02, 9.5, z + 0.6), 'gunmetal', name='rail_tooth')
    g.box((C - 1.15, 5.45, -2.5), (C + 1.15, 8.95, 9.5), 'darkmetal', name='body')
    g.box((C - 1.1, 4.45, -8.0), (C + 1.1, 5.7, 9.5), 'darkmetal', name='body_belly')
    g.box((C - 1.0, 5.95, 9.5), (C + 1.0, 8.95, 12.0), 'polymer_dark', name='stock')
    g.box((C - 1.1, 3.95, 11.6), (C + 1.1, 8.95, 12.8), 'polymer_dark', name='butt_hook')
    g.box((C - 1.2, 3.95, 12.8), (C + 1.2, 8.95, 13.2), 'rubber', name='butt_pad')
    g.box((C - 0.35, 9.45, 6.5), (C + 0.35, 10.45, 7.5), 'darkmetal', name='rear_sight')
    grip(g, 6.0, top=5.95)
    sides(g, 1.15, 7.0, 8.4, 7.4, 9.2, 'rubber', 'ejection_port', side=1)
    sides(g, 1.15, 7.6, 8.1, -0.5, 1.6, 'steel', 'cocking_handle', d=0.4, side=-1)
    sides(g, 1.15, 6.3, 6.8, 4.6, 5.6, 'steel', 'selector', side=1)
    for z in (-7.0, -2.0):
        screw(g, 1.1, 6.3, z)
    screw(g, 1.15, 5.9, 8.8)
    sides(g, 1.1, 4.6, 5.1, -6.0, -2.5, 'steel', 'belly_pin', d=0.05)
    g.box((C - 0.35, 7.0, -9.15), (C + 0.35, 8.0, -9.0), 'rubber', name='muzzle_collar')
    for z in (10.2, 10.9):
        sides(g, 1.0, 7.0, 8.4, z, z + 0.35, 'rubber', 'stock_vent', d=0.05)
    for z in (-12.9, -10.4):
        g.cyl('z', (C, 8.2), 0.78, z, z + 0.4, 'gunmetal', seg=8, name='barrel_band')
    g.ring('z', (C, 8.2), 0.4, 0.78, -15.1, -14.9, 'steel', name='muzzle_crown')
    sides(g, 1.15, 7.4, 8.0, 2.5, 3.6, 'steel', 'bolt_release', d=0.15, side=-1)
    g.box((C - 0.5, 9.4, 8.2), (C + 0.5, 9.7, 9.0), 'steel', name='rear_sight_base')
    for z in (-6.0, -4.0, -2.0, 0.0):
        g.box((C - 0.5, 4.4, z), (C + 0.5, 4.55, z + 0.5), 'rubber', name='belly_slot')
    sides(g, 1.1, 4.6, 5.1, 1.0, 3.5, 'gunmetal', 'belly_plate', d=0.05)
    g.save(display(0.48))


def r870():
    g = Gun('r870_v3', offset=(0, -1, 6))
    g.cyl('z', (C, 8.5), 0.5, -15, 0, 'steel', seg=8, name='barrel')
    g.box((C - 0.85, 7.3, -15.0), (C + 0.85, 9.7, -13.2), 'darkmetal', name='breacher')
    for x in (-0.6, -0.2, 0.2, 0.6):
        g.box((C + x - 0.15, 9.5, -15.6), (C + x + 0.15, 10.0, -15.0), 'darkmetal', name='breacher_tooth')
        g.box((C + x - 0.15, 7.0, -15.6), (C + x + 0.15, 7.5, -15.0), 'darkmetal', name='breacher_tooth')
    for z in (-14.4, -13.8):
        g.box((C - 0.88, 7.9, z), (C + 0.88, 9.1, z + 0.3), 'rubber', name='breacher_port')
    g.cyl('z', (C, 7.5), 0.5, -12.0, 0, 'steel', seg=8, name='mag_tube')
    g.box((C - 0.95, 4.1, -11.0), (C + 0.95, 7.0, -4.5), 'polymer_dark', name='pump')
    for z in (-10.4, -9.4, -8.4, -7.4, -6.4, -5.4):
        g.box((C - 0.97, 4.3, z), (C + 0.97, 6.9, z + 0.4), 'rubber', name='pump_rib')
    g.cyl('z', (C, 4.8), 0.8, -12.0, -9.0, 'darkmetal', seg=8, name='light')
    g.cyl('z', (C, 4.8), 0.65, -12.1, -12.0, 'glass', seg=8, name='light_lens')
    g.box((C - 0.85, 6.75, -4.5), (C + 0.85, 9.2, 8.0), 'darkmetal', name='receiver')
    g.box((C - 0.8, 5.5, -4.5), (C + 0.8, 6.9, 3.0), 'darkmetal', name='receiver_lower')
    g.box((C - 0.6, 9.2, -2.4), (C + 0.6, 9.5, 7.9), 'gunmetal', name='receiver_rail')
    g.box((C - 0.15, 9.5, -1.4), (C + 0.15, 10.0, 0.0), 'darkmetal', name='front_sight')
    g.box((C - 0.15, 9.5, 5.6), (C + 0.15, 10.0, 7.0), 'darkmetal', name='rear_sight')
    g.box((C + 0.84, 7.8, -3.0), (C + 0.9, 9.0, -0.4), 'rubber', name='ejection_port')
    grip(g, 10.2, top=6.8)
    g.cyl('z', (C, 7.4), 0.45, 8.0, 13.6, 'gunmetal', seg=8, name='buffer_tube')
    g.box((C - 0.7, 6.2, 7.5), (C + 0.7, 8.2, 16.0), 'polymer_dark', name='stock_body')
    g.box((C - 0.55, 3.6, 14.0), (C + 0.55, 6.4, 15.6), 'polymer_dark', name='stock_toe')
    g.box((C - 0.75, 3.6, 15.6), (C + 0.75, 8.2, 16.0), 'rubber', name='butt_pad')
    sides(g, 0.8, 5.9, 6.4, 0.5, 1.5, 'steel', 'slide_release', side=1)
    sides(g, 0.85, 7.4, 7.9, 5.6, 6.4, 'steel', 'safety_button')
    for z in (-3.6, 6.8):
        screw(g, 0.85, 8.2, z)
    g.cyl('z', (C, 7.5), 0.6, -12.0, -11.6, 'darkmetal', seg=8, name='mag_cap')
    sides(g, 0.8, 4.6, 5.0, -10.5, -9.8, 'rubber', 'light_switch', side=1)
    g.cyl('z', (C, 8.5), 0.62, -2.0, -1.6, 'darkmetal', seg=8, name='barrel_clamp')
    for z in (-1.8, -0.8, 0.2, 1.2, 2.2, 3.2, 4.2, 5.2, 6.2):
        g.box((C - 0.62, 9.3, z), (C + 0.62, 9.55, z + 0.35), 'steel', name='rail_tooth')
    g.box((C - 0.5, 5.3, -4.0), (C + 0.5, 5.6, -1.6), 'rubber', name='loading_port')
    sides(g, 0.95, 5.4, 5.7, -10.0, -4.8, 'steel', 'action_bar')
    g.cyl('x', (7.5, -11.8), 0.2, C - 0.4, C + 0.4, 'steel', seg=8, name='sling_pin')
    g.box((C - 0.4, 8.2, 8.5), (C + 0.4, 8.45, 12.0), 'rubber', name='cheek_pad')
    for z in (9.0, 10.5, 12.0, 13.5):
        sides(g, 0.7, 6.4, 7.8, z, z + 0.3, 'rubber', 'stock_groove', d=0.05)
    g.box((C - 0.1, 5.2, -0.1), (C + 0.1, 6.0, 0.2), 'steel', name='trigger_blade')
    g.cyl('z', (C, 8.5), 0.55, -9.0, -8.6, 'gunmetal', seg=8, name='barrel_band')
    g.save(display(0.5))


def s12():
    g = Gun('s12_v3', offset=(0, -1, 3))
    g.cyl('z', (C, 8.3), 0.75, -15.4, -9.6, 'steel', seg=8, name='muzzle_brake')
    for z in (-14.6, -13.6, -12.6, -11.6, -10.6):
        g.box((C - 0.77, 7.7, z), (C + 0.77, 8.9, z + 0.5), 'rubber', name='brake_port')
    for dy in (-0.75, 0.75):
        g.box((C - 0.15, 8.3 + dy - 0.15, -15.6), (C + 0.15, 8.3 + dy + 0.15, -15.0), 'darkmetal', name='spike')
    g.cyl('z', (C, 8.55), 0.5, -9.6, -3.6, 'steel', seg=8, name='barrel')
    g.box((C - 0.85, 7.05, -5.1), (C + 0.85, 8.55, 1.0), 'polymer_dark', name='quad_rail')
    for z in range(-5, 1, 1):
        g.box((C - 0.87, 8.55, z + 0.2), (C + 0.87, 8.8, z + 0.6), 'gunmetal', name='rail_tooth')
    g.box((C - 0.15, 10.05, -3.1), (C + 0.15, 10.8, -1.8), 'darkmetal', name='front_sight')
    g.box((C - 0.8, 7.05, -3.6), (C + 0.8, 9.85, 9.4), 'darkmetal', name='receiver')
    g.box((C - 0.75, 9.85, -3.1), (C + 0.75, 10.05, 8.9), 'darkmetal', name='top_cover')
    g.box((C - 0.55, 2.3, 4.2), (C + 0.55, 7.1, 6.6), 'gunmetal', name='magazine')
    g.box((C + 0.8, 8.0, 0.0), (C + 0.86, 8.5, 3.0), 'steel', name='selector')
    grip(g, 8.4, top=7.05, length=2.8)
    g.cyl('z', (C, 8.05), 0.5, 9.0, 14.0, 'gunmetal', seg=8, name='buffer_tube')
    g.box((C - 0.7, 6.55, 11.4), (C + 0.7, 8.55, 13.8), 'polymer_dark', name='stock_body')
    g.box((C - 0.55, 5.55, 11.8), (C + 0.55, 6.6, 13.8), 'polymer_dark', name='stock_toe')
    g.box((C - 0.75, 5.55, 13.8), (C + 0.75, 8.55, 14.2), 'rubber', name='butt_pad')
    sides(g, 0.8, 8.9, 9.4, 2.0, 3.4, 'steel', 'charging_handle', d=0.4, side=1)
    g.box((C - 0.45, 8.9, -9.6), (C + 0.45, 9.6, -9.0), 'darkmetal', name='gas_block')
    g.box((C - 0.3, 6.0, 6.6), (C + 0.3, 6.8, 7.2), 'steel', name='mag_release')
    g.box((C - 0.6, 2.30, 4.1), (C + 0.6, 2.55, 6.7), 'rubber', name='mag_base_plate')
    sides(g, 0.8, 7.8, 8.8, 5.0, 7.0, 'rubber', 'ejection_port', side=1)
    screw(g, 0.8, 8.4, 8.4)
    screw(g, 0.8, 8.4, -2.8)
    for z in (-4.5, -3.3, -2.1, -0.9):
        sides(g, 0.85, 7.4, 8.2, z, z + 0.5, 'rubber', 'rail_slot')
    sides(g, 0.85, 7.1, 7.3, -5.1, 1.0, 'steel', 'rail_strip')
    sides(g, 0.85, 7.1, 8.5, -5.1, -4.9, 'steel', 'rail_band')
    sides(g, 0.8, 7.1, 9.8, 4.0, 4.15, 'gunmetal', 'receiver_seam')
    sides(g, 0.8, 9.3, 9.45, -3.4, 9.2, 'steel', 'dust_cover_line')
    for z in (-2.5, -1.0, 0.5, 2.0, 3.5, 5.0):
        g.box((C - 0.55, 10.05, z), (C + 0.55, 10.2, z + 0.3), 'steel', name='top_rail_tooth')
    g.box((C - 0.4, 10.05, 7.6), (C + 0.4, 10.7, 8.4), 'darkmetal', name='rear_sight')
    for s in (-1, 1):
        g.box((C + s * 0.4 - 0.05, 10.7, 7.7), (C + s * 0.4 + 0.05, 11.2, 8.3), 'steel', name='sight_wing')
    for y in (3.0, 4.0, 5.0, 6.0):
        sides(g, 0.55, y, y + 0.25, 4.3, 6.5, 'steel', 'mag_rib')
    sides(g, 0.7, 7.0, 7.2, 11.5, 13.7, 'gunmetal', 'stock_rib')
    sides(g, 0.7, 7.9, 8.1, 11.5, 13.7, 'gunmetal', 'stock_rib')
    g.save(display(0.5))


if __name__ == '__main__':
    for f in (m1216, r870, s12):
        f()
