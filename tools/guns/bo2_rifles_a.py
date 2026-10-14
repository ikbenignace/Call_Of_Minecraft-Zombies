#!/usr/bin/env python3
"""BO2 assault rifles from the BO2 icons: AN-94, M8A1, M27, MTAR, SMR, Type 25. Muzzle towards -Z."""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun
from bo2_shotguns import display, grip

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


def rail(g, z0, z1, y, hw=0.6):
    g.box((C - hw, y, z0), (C + hw, y + 0.35, z1), 'darkmetal', name='top_rail')
    z = z0 + 0.2
    while z < z1 - 0.3:
        g.box((C - hw - 0.02, y + 0.25, z), (C + hw + 0.02, y + 0.45, z + 0.35), 'gunmetal', name='rail_tooth')
        z += 0.8


def m4_stock(g, z0, y=8.6):
    g.cyl('z', (C, y), 0.42, z0, z0 + 5.0, 'darkmetal', seg=8, name='buffer_tube')
    g.box((C - 0.7, y - 1.2, z0 + 3.0), (C + 0.7, y + 1.2, z0 + 6.6), 'polymer_dark', name='stock_body')
    g.box((C - 0.55, y - 2.6, z0 + 4.6), (C + 0.55, y - 1.0, z0 + 6.6), 'polymer_dark', name='stock_toe')
    g.box((C - 0.75, y - 2.8, z0 + 6.6), (C + 0.75, y + 1.3, z0 + 7.0), 'rubber', name='butt_pad')


def an94():
    g = Gun('an94_v3', offset=(0, -1, 1))
    g.cyl('z', (C, 7.4), 1.15, -15, -12.0, 'steel', seg=8, name='muzzle_device')
    for z in (-14.6, -13.6, -12.6):
        g.cyl('z', (C, 7.4), 1.2, z, z + 0.3, 'rubber', seg=8, name='brake_ridge')
    g.cyl('z', (C, 7.5), 0.85, -12.0, -3.0, 'steel', seg=8, name='barrel')
    g.box((C - 0.4, 8.0, -7.8), (C + 0.4, 11.4, -6.6), 'darkmetal', name='front_sight_block')
    g.box((C - 0.15, 11.4, -7.2), (C + 0.15, 11.8, -6.8), 'darkmetal', name='front_post')
    g.box((C - 0.7, 6.4, -9.0), (C + 0.7, 7.6, -3.0), 'darkmetal', name='lower_rail')
    g.box((C - 0.9, 4.6, -5.5), (C + 0.9, 8.5, 3.6), 'polymer_tan', name='handguard')
    for z in (-2.4, -1.2, 0.0, 1.2, 2.4):
        g.box((C - 0.92, 6.6, z), (C + 0.92, 8.8, z + 0.5), 'polymer_dark', name='handguard_slot')
    g.box((C - 0.8, 5.2, 3.6), (C + 0.8, 9.4, 11.0), 'darkmetal', name='receiver')
    g.box((C - 0.75, 9.4, 3.6), (C + 0.75, 9.6, 10.6), 'darkmetal', name='top_cover')
    g.box((C - 0.35, 9.6, 8.6), (C + 0.35, 10.6, 9.6), 'darkmetal', name='rear_sight')
    g.box((C - 0.6, 4.4, 7.6), (C + 0.6, 5.7, 10.4), 'polymer_tan', name='mag_top')
    LOWER = ('x', 22.5, (C, 4.4, 9.0))
    g.box((C - 0.6, -1.4, 7.6), (C + 0.6, 4.4, 10.4), 'polymer_tan', rot=LOWER, name='mag_bottom')
    for y in (-0.4, 1.2, 2.8):
        g.box((C - 0.62, y, 7.65), (C + 0.62, y + 0.2, 10.35), 'polymer_dark', rot=LOWER, name='mag_rib')
    g.box((C - 0.7, 1.4, 11.4), (C + 0.7, 6.8, 13.2), 'polymer_olive', rot=('x', -22.5, (C, 6.8, 12.3)), name='grip')
    g.box((C - 0.2, 5.2, 9.6), (C + 0.2, 5.5, 11.6), 'darkmetal', name='guard_bottom')
    g.box((C - 0.08, 5.5, 10.4), (C + 0.08, 6.6, 10.7), 'steel', name='trigger')
    g.box((C - 0.7, 6.4, 11.0), (C + 0.7, 9.4, 14.0), 'polymer_dark', name='stock_front')
    g.box((C - 0.75, 4.8, 14.0), (C + 0.75, 8.2, 17.0), 'polymer_olive', name='stock_butt')
    g.box((C - 0.75, 3.6, 17.0), (C + 0.75, 8.2, 18.4), 'polymer_olive', name='stock_heel')
    g.box((C - 0.8, 3.4, 18.4), (C + 0.8, 8.3, 18.9), 'rubber', name='butt_pad')
    sides(g, 0.8, 8.6, 9.2, 6.0, 7.8, 'steel', 'charging_handle', d=0.4, side=-1)
    sides(g, 0.8, 7.8, 9.2, 5.0, 6.9, 'rubber', 'ejection_port', side=1)
    sides(g, 0.8, 7.0, 7.6, 9.0, 10.4, 'steel', 'selector', side=-1)
    sides(g, 0.6, 4.7, 5.3, 7.7, 8.3, 'steel', 'mag_release')
    screw(g, 0.8, 6.6, 4.4)
    screw(g, 0.8, 6.6, 9.4, side=1)
    g.wire([(C, 5.1, -2.0), (C, 4.3, -2.0)], 0.4, 'steel', name='sling_swivel')
    g.box((C - 0.5, 4.2, 9.5), (C + 0.5, 4.4, 10.5), 'rubber', name='mag_baseplate_pad')
    g.cyl('z', (C, 8.55), 0.3, -9.0, -3.0, 'darkmetal', seg=8, name='gas_tube')
    sides(g, 1.0, 7.0, 7.8, -13.4, -12.6, 'rubber', 'brake_port')
    for s in (-1, 1):
        g.box((C + s * 0.4 - (0.15 if s < 0 else 0), 8.0, -7.8), (C + s * 0.4 + (0.0 if s < 0 else 0.15), 10.8, -6.8), 'darkmetal', name='sight_guard')
        g.box((min(C + s * 0.3, C + s * 0.45), 10.6, 8.6), (max(C + s * 0.3, C + s * 0.45), 11.0, 9.6), 'darkmetal', name='rear_ear')
    g.box((C - 0.25, 9.6, 4.0), (C + 0.25, 9.8, 8.4), 'gunmetal', name='dovetail_rail')
    for z, y in ((-2.5, 6.0), (3.0, 7.8), (-2.5, 7.8), (3.0, 6.0)):
        screw(g, 0.9, y, z)
    sides(g, 0.75, 5.5, 5.9, 14.2, 16.8, 'rubber', 'stock_line')
    sides(g, 0.8, 9.0, 9.1, 3.8, 10.8, 'gunmetal', 'receiver_line')
    g.save(display(0.42))


def m8a1():
    g = Gun('m8a1_v3', offset=(0, -1, 3))
    g.cyl('z', (C, 9.0), 0.36, -15, -10.0, 'steel', seg=8, name='barrel')
    g.cyl('z', (C, 9.0), 0.48, -15, -13.8, 'darkmetal', seg=8, name='flash_hider')
    g.box((C - 0.95, 7.4, -10.0), (C + 0.95, 10.6, 8.0), 'polymer_tan', name='body')
    g.box((C - 0.97, 7.4, -10.0), (C + 0.97, 8.2, -2.0), 'polymer_dark', name='handguard_lower')
    for z in (-9.0, -7.4, -5.8):
        g.box((C - 0.98, 9.0, z), (C + 0.98, 9.8, z + 1.0), 'rubber', name='handguard_vent')
    g.box((C - 0.98, 10.0, -6.0), (C + 0.98, 10.6, 6.0), 'polymer_dark', name='carry_stripe')
    g.box((C - 0.7, 10.6, -4.0), (C + 0.7, 11.0, 6.0), 'polymer_dark', name='top_rail')
    g.box((C - 0.6, 3.0, 1.2), (C + 0.6, 7.4, 3.6), 'darkmetal', name='magazine')
    grip(g, 7.2, top=7.4)
    g.box((C - 0.5, 8.0, 8.0), (C + 0.5, 10.4, 9.2), 'polymer_dark', name='stock_hinge')
    g.box((C - 0.3, 7.6, 9.2), (C + 0.3, 10.0, 14.4), 'polymer_dark', name='stock_bar')
    g.box((C - 0.3, 7.4, 11.0), (C + 0.3, 7.8, 14.4), 'polymer_dark', name='stock_lower_bar')
    g.box((C - 0.55, 6.4, 14.4), (C + 0.55, 10.4, 15.2), 'polymer_dark', name='butt_plate')
    sides(g, 0.95, 8.4, 9.8, 1.0, 3.0, 'rubber', 'ejection_port', side=1)
    g.box((C - 0.65, 3.00, 1.1), (C + 0.65, 3.25, 3.7), 'rubber', name='mag_base_plate')
    g.box((C - 0.3, 11.0, 4.2), (C + 0.3, 11.7, 5.0), 'darkmetal', name='rear_aperture')
    g.box((C - 0.15, 10.6, -5.6), (C + 0.15, 11.5, -4.8), 'darkmetal', name='front_post')
    sides(g, 0.95, 8.0, 8.5, 5.4, 6.4, 'steel', 'selector', side=-1)
    for z in (-1.0, 5.0):
        screw(g, 0.95, 9.4, z)
    sides(g, 0.95, 8.7, 9.2, -9.5, -8.3, 'steel', 'handguard_pin')
    rail(g, -3.6, 5.6, 11.0, hw=0.5)
    for z in (-12.2, -11.2):
        g.ring('z', (C, 9.0), 0.36, 0.55, z, z + 0.3, 'darkmetal', name='barrel_ring')
    sides(g, 0.97, 8.9, 9.0, -9.8, -2.2, 'rubber', 'handguard_line')
    sides(g, 0.95, 10.0, 10.1, -2.0, 7.8, 'darkmetal', 'body_panel_line')
    for y in (3.9, 4.8, 5.7, 6.6):
        sides(g, 0.6, y, y + 0.15, 1.25, 3.55, 'rubber', 'mag_rib')
    g.box((C - 0.3, 10.6, 6.2), (C + 0.3, 10.9, 7.4), 'steel', name='charging_handle')
    g.cyl('x', (9.2, 8.6), 0.15, C - 0.55, C + 0.55, 'steel', seg=8, name='hinge_pin')
    sides(g, 0.95, 7.5, 8.0, 3.2, 4.0, 'steel', 'mag_release')
    sides(g, 0.44, 8.8, 9.2, -14.7, -14.0, 'rubber', 'flash_slot')
    g.save(display(0.48))


def m27():
    g = Gun('m27_v3', offset=(0, 0, 0))
    g.cyl('z', (C, 8.2), 0.8, -14, -11.6, 'steel', seg=8, name='flash_hider')
    g.cyl('z', (C, 8.2), 0.34, -11.6, -5.0, 'steel', seg=8, name='barrel')
    g.box((C - 0.95, 7.0, -6.0), (C + 0.95, 9.4, 3.0), 'gunmetal', name='rail_handguard')
    g.box((C - 0.97, 7.4, -3.8), (C + 0.97, 8.8, -0.8), 'polymer_olive', name='rail_cover')
    rail(g, -6.0, 8.0, 9.4)
    g.box((C - 0.8, 5.8, 3.0), (C + 0.8, 9.4, 9.0), 'darkmetal', name='receiver')
    g.box((C - 0.6, 4.4, 3.8), (C + 0.6, 5.9, 6.4), 'darkmetal', name='magwell')
    g.box((C - 0.55, 1.4, 4.0), (C + 0.55, 4.6, 6.6), 'gunmetal', rot=('x', 22.5, (C, 4.6, 5.3)), name='magazine')
    g.box((C - 0.35, 9.8, -3.2), (C + 0.35, 11.4, -1.8), 'darkmetal', name='front_sight')
    g.ring('z', (C, 10.8), 0.5, 0.75, 8.4, 9.4, 'darkmetal', name='sight_ring')
    g.box((C - 0.4, 9.3, 8.4), (C + 0.4, 10.4, 9.4), 'darkmetal', name='sight_base')
    GRIP = ('x', -22.5, (C, 6.0, 7.6))
    g.box((C - 0.68, 2.0, 6.6), (C + 0.68, 6.0, 8.6), 'polymer_dark', rot=GRIP, name='grip')
    g.box((C - 0.22, 5.0, 4.4), (C + 0.22, 5.3, 6.8), 'darkmetal', name='guard_bottom')
    g.box((C - 0.08, 5.2, 5.4), (C + 0.08, 6.0, 5.7), 'steel', name='trigger')
    m4_stock(g, 9.0, y=8.2)
    sides(g, 0.8, 8.4, 9.0, 4.0, 5.4, 'steel', 'charging_handle', d=0.4, side=-1)
    sides(g, 0.8, 7.8, 9.0, 4.5, 6.3, 'rubber', 'ejection_port', side=1)
    sides(g, 0.8, 6.8, 7.3, 7.0, 7.8, 'steel', 'selector', side=-1)
    sides(g, 0.6, 4.7, 5.3, 3.9, 4.5, 'steel', 'mag_release', side=1)
    sides(g, 0.8, 6.3, 6.9, 3.4, 4.0, 'steel', 'bolt_release', side=-1)
    g.wire([(C, 7.1, -5.0), (C, 6.3, -5.0)], 0.4, 'steel', name='sling_swivel')
    for z in (-5.2, -3.9, -2.6, -1.3, 0.0, 1.3):
        sides(g, 0.95, 7.2, 8.0, z, z + 0.6, 'rubber', 'handguard_slot', d=0.05)
    g.cyl('z', (C, 8.2), 0.46, -6.0, -5.4, 'steel', seg=8, name='barrel_nut')
    g.cyl('z', (C, 8.2), 0.5, -9.0, -8.6, 'steel', seg=8, name='gas_block_ring')
    for z in (-13.0, -12.2):
        g.cyl('z', (C, 8.2), 0.86, z, z + 0.3, 'rubber', seg=8, name='hider_groove')
    g.box((C - 0.12, 9.4, -3.2), (C + 0.12, 9.8, 3.0), 'steel', name='rail_bolt_strip')
    g.box((C - 0.5, 9.4, 8.0), (C + 0.5, 9.7, 8.6), 'steel', name='sight_clamp')
    sides(g, 0.6, 1.4, 1.9, 4.0, 6.6, 'rubber', 'mag_base_plate', d=0.05)
    screw(g, 0.95, 8.7, -5.2)
    screw(g, 0.95, 7.7, 2.2)
    g.save(display(0.44))


def mtar():
    g = Gun('mtar_v3', offset=(0, 0, 0))
    g.cyl('z', (C, 6.9), 0.95, -11, -8.0, 'gunmetal', seg=8, name='flash_hider')
    g.cyl('z', (C, 6.9), 0.8, -8.0, -4.0, 'steel', seg=8, name='barrel')
    g.box((C - 1.1, 5.2, -4.0), (C + 1.1, 9.2, 1.5), 'gunmetal', name='handguard_body')
    g.box((C - 0.9, 5.8, 1.5), (C + 0.9, 9.2, 8.0), 'darkmetal', name='body')
    g.box((C - 0.4, 9.2, -3.0), (C + 0.4, 9.6, 8.0), 'darkmetal', name='top_rail')
    g.box((C - 0.1, 9.6, -2.6), (C + 0.1, 10.4, -2.2), 'darkmetal', name='front_sight')
    g.box((C - 0.3, 9.6, 6.8), (C + 0.3, 10.0, 7.6), 'darkmetal', name='rear_sight')
    g.cyl('z', (C + 1.0, 8.6), 0.4, -6.6, -4.0, 'darkmetal', seg=8, name='flashlight')
    g.cyl('z', (C + 1.0, 8.6), 0.32, -6.7, -6.6, 'glass', seg=8, name='flashlight_lens')
    # Closed hand-guard loop: front bar down from the handguard, bottom bar to the grip foot
    g.box((C - 0.5, 1.2, 1.6), (C + 0.5, 6.2, 2.6), 'polymer_dark', rot=('x', -22.5, (C, 6.0, 2.1)), name='loop_front')
    g.box((C - 0.5, 0.8, 3.3), (C + 0.5, 1.7, 6.6), 'polymer_dark', name='loop_bottom')
    g.box((C - 0.1, 4.4, 3.2), (C + 0.1, 5.8, 3.5), 'steel', name='trigger')
    GRIP = ('x', -22.5, (C, 5.9, 5.6))
    g.box((C - 0.6, 0.6, 5.6), (C + 0.6, 6.2, 7.8), 'grip_ribs_dark', rot=GRIP, name='grip')
    g.box((C - 0.6, 0.4, 8.4), (C + 0.6, 6.0, 11.4), 'gunmetal', name='magazine')
    g.box((C - 1.0, 6.0, 8.0), (C + 1.0, 9.6, 15.0), 'polymer_dark', name='butt')
    g.box((C - 1.1, 5.2, 15.0), (C + 1.1, 9.0, 15.3), 'rubber', name='butt_pad')
    sides(g, 0.9, 8.4, 9.0, 2.5, 4.0, 'steel', 'charging_handle', d=0.4, side=-1)
    sides(g, 0.9, 7.4, 8.8, 5.5, 7.5, 'rubber', 'ejection_port', side=1)
    sides(g, 0.9, 6.2, 6.7, 4.0, 5.0, 'steel', 'selector', side=-1)
    for z in (-3.4, -2.4, -1.4, -0.4):
        sides(g, 1.1, 6.4, 8.4, z, z + 0.4, 'rubber', 'handguard_vent')
    g.box((C - 0.65, 0.40, 8.35), (C + 0.65, 0.65, 11.45), 'rubber', name='mag_base_plate')
    screw(g, 0.9, 8.0, 7.2)
    sides(g, 0.9, 5.9, 9.1, 5.2, 5.35, 'gunmetal', 'panel_seam')
    sides(g, 0.9, 5.9, 9.1, 7.6, 7.75, 'gunmetal', 'panel_seam')
    sides(g, 1.1, 5.3, 5.55, -4.0, 1.5, 'steel', 'handguard_rail')
    sides(g, 1.1, 5.3, 9.1, -3.95, -3.75, 'steel', 'handguard_band')
    sides(g, 1.1, 5.3, 9.1, 1.25, 1.45, 'steel', 'handguard_band')
    for z in (-2.2, -1.2, -0.2, 0.8, 1.8, 2.8, 3.8, 4.8, 5.8, 6.8):
        g.box((C - 0.45, 9.55, z), (C + 0.45, 9.7, z + 0.35), 'steel', name='rail_slot')
    g.cyl('z', (C + 1.0, 8.6), 0.47, -5.8, -5.5, 'steel', seg=8, name='light_clamp')
    for y in (1.6, 2.8, 4.0, 5.2):
        sides(g, 0.6, y, y + 0.25, 8.5, 11.3, 'steel', 'mag_rib')
    sides(g, 1.0, 6.3, 9.4, 11.0, 11.15, 'gunmetal', 'stock_seam')
    sides(g, 1.0, 6.3, 9.4, 13.2, 13.35, 'gunmetal', 'stock_seam')
    sides(g, 1.0, 8.2, 8.5, 8.2, 14.8, 'steel', 'stock_strip')
    for s in (-1, 1):
        g.box((C + s * 0.35 - 0.05, 9.6, -2.85), (C + s * 0.35 + 0.05, 10.25, -2.4), 'darkmetal', name='sight_wing')
    g.save(display(0.52))


def smr():
    g = Gun('smr_v3', offset=(0, 0, 0))
    g.cyl('z', (C, 6.9), 1.0, -12, -9.5, 'steel', seg=8, name='muzzle_brake')
    g.cyl('z', (C, 6.9), 0.5, -9.5, -8.5, 'steel', seg=8, name='barrel')
    g.box((C - 0.8, 4.3, -9.5), (C + 0.8, 7.0, -1.0), 'polymer_dark', name='handguard')
    g.box((C - 0.7, 3.6, -1.0), (C + 0.7, 4.7, 3.0), 'polymer_dark', name='handguard_lower')
    for z in (-8.0, -6.8, -5.6, -4.4, -3.2):
        g.box((C - 0.87, 5.6, z), (C + 0.87, 6.6, z + 0.6), 'rubber', name='handguard_vent')
    rail(g, -9.0, 9.0, 7.0, hw=0.5)
    g.box((C - 1.0, 4.6, -1.0), (C + 1.0, 7.2, 3.0), 'gunmetal', name='receiver_front')
    g.box((C - 0.9, 4.6, 3.0), (C + 0.9, 7.2, 9.0), 'darkmetal', name='body')
    g.box((C - 1.05, 4.6, 9.0), (C + 1.05, 7.8, 12.6), 'polymer_dark', name='stock_top')
    g.box((C - 0.65, 1.3, 5.2), (C + 0.65, 5.4, 6.6), 'polymer_dark', rot=('x', -22.5, (C, 5.0, 5.2)), name='grip')
    g.wire([(C, 4.6, 0.2), (C, 3.0, 1.8), (C, 1.8, 3.0)], 0.9, 'polymer_dark', name='grip_strut')
    g.box((C - 0.65, 1.3, 2.6), (C + 0.65, 2.3, 8.4), 'polymer_dark', name='grip_foot')
    g.box((C - 0.12, 3.0, 3.4), (C + 0.12, 4.2, 3.7), 'steel', name='trigger')
    g.box((C - 0.6, 1.3, 7.8), (C + 0.6, 4.8, 12.6), 'gunmetal', name='magazine')
    g.box((C - 0.85, 3.0, 12.6), (C + 0.85, 8.0, 15.6), 'polymer_dark', name='butt_belly')
    g.box((C - 1.0, 2.8, 15.6), (C + 1.0, 8.2, 16.0), 'rubber', name='butt_pad')
    sides(g, 0.9, 5.0, 5.5, 6.0, 6.8, 'steel', 'selector', side=-1)
    sides(g, 0.9, 5.6, 6.8, 3.8, 5.8, 'rubber', 'ejection_port', side=1)
    sides(g, 0.9, 6.4, 7.0, 4.0, 5.4, 'steel', 'charging_handle', d=0.4, side=-1)
    g.box((C - 0.62, 1.30, 7.8), (C + 0.62, 1.55, 12.6), 'rubber', name='mag_base_plate')
    for z in (-11.4, -10.6):
        g.box((C - 1.02, 6.4, z), (C + 1.02, 7.4, z + 0.4), 'rubber', name='brake_port')
    g.box((C - 0.12, 7.0, -8.8), (C + 0.12, 8.2, -8.4), 'darkmetal', name='front_post')
    g.box((C - 0.3, 7.2, 7.5), (C + 0.3, 8.0, 8.3), 'darkmetal', name='rear_sight')
    screw(g, 0.9, 5.8, 7.8)
    screw(g, 1.05, 6.6, 11.0)
    for s_ in (-1, 1):
        g.cyl('z', (C + s_ * 0.38, 4.25), 0.18, -9.2, -4.6, 'steel', seg=8, name='gas_rod')
    sides(g, 0.65, 1.5, 2.1, 3.2, 8.0, 'rubber', 'grip_panel')
    sides(g, 0.6, 3.8, 4.4, 8.0, 8.6, 'steel', 'mag_release')
    for y in (2.0, 2.8, 3.6):
        sides(g, 0.6, y, y + 0.25, 8.2, 12.4, 'rubber', 'mag_rib')
    for s_ in (-1, 1):
        g.box((C + s_ * 0.12, 7.0, -8.9), (C + s_ * 0.3, 8.1, -8.3), 'darkmetal', name='sight_hood')
    g.box((C - 0.1, 2.9, 2.0), (C + 0.1, 3.2, 3.9), 'polymer_dark', name='guard_bottom')
    g.cyl('z', (C, 6.9), 1.08, -11.9, -11.7, 'darkmetal', seg=8, name='brake_ring')
    screw(g, 0.8, 4.9, -6.0)
    screw(g, 0.8, 4.9, -3.0)
    screw(g, 1.0, 7.4, 15.8)
    screw(g, 1.0, 3.6, 15.8)
    g.save(display(0.5))


def type25():
    g = Gun('type25_v3', offset=(0, 0, 0))
    g.cyl('z', (C, 7.7), 0.45, -12, -10.4, 'steel', seg=8, name='flash_hider')
    g.cyl('z', (C, 7.7), 0.3, -10.4, -8.0, 'steel', seg=8, name='barrel')
    g.box((C - 0.8, 5.7, -8.0), (C + 0.8, 8.6, -1.0), 'gunmetal', name='handguard_body')
    g.box((C - 0.95, 5.7, -1.0), (C + 0.95, 9.1, 9.0), 'darkmetal', name='body')
    for z in (-7.0, -5.6, -4.2, -2.8):
        g.box((C - 0.92, 6.4, z), (C + 0.92, 7.6, z + 0.7), 'darkmetal', name='handguard_slot')
    g.box((C - 0.7, 9.1, -2.0), (C + 0.7, 9.9, 4.6), 'darkmetal', name='carry_handle')
    g.box((C - 0.7, 9.1, -2.8), (C + 0.7, 9.5, -2.0), 'darkmetal', name='carry_front')
    g.box((C - 0.7, 9.1, 4.6), (C + 0.7, 9.5, 5.4), 'darkmetal', name='carry_rear')
    g.box((C - 0.85, 5.7, 9.0), (C + 0.85, 9.4, 12.6), 'polymer_dark', name='stock_top')
    g.box((C - 0.7, 9.1, -7.5), (C + 0.7, 9.6, -5.0), 'darkmetal', name='front_sight_base')
    g.box((C - 0.12, 7.8, -7.4), (C + 0.12, 10.4, -6.9), 'darkmetal', name='front_sight')
    g.wire([(C, 5.9, -4.8), (C, 4.4, -3.6), (C, 4.0, -1.8), (C, 5.9, 0.0)], 0.4, 'darkmetal', name='trigger_guard')
    g.box((C - 0.6, 3.0, -0.6), (C + 0.6, 6.2, 1.2), 'polymer_dark', rot=('x', -22.5, (C, 6.2, 0.3)), name='grip')
    g.box((C - 0.55, 1.0, 4.7), (C + 0.55, 6.0, 7.7), 'darkmetal', rot=('x', 22.5, (C, 6.0, 6.2)), name='magazine')
    g.box((C - 0.8, 5.3, 9.0), (C + 0.8, 7.0, 12.6), 'polymer_dark', name='butt')
    g.box((C - 1.0, 5.2, 12.6), (C + 1.0, 9.2, 13.0), 'rubber', name='butt_pad')
    sides(g, 0.95, 8.0, 8.6, 3.0, 4.4, 'steel', 'charging_handle', d=0.4, side=-1)
    sides(g, 0.95, 7.4, 8.7, 2.5, 4.5, 'rubber', 'ejection_port', side=1)
    sides(g, 0.95, 6.2, 6.8, 1.0, 1.8, 'steel', 'selector', side=-1)
    g.cyl('z', (C, 8.9), 0.3, -7.0, -1.0, 'steel', seg=8, name='gas_tube')
    g.box((C - 0.4, 9.9, 3.2), (C + 0.4, 10.5, 4.4), 'darkmetal', name='rear_sight')
    screw(g, 0.95, 7.0, 6.5)
    screw(g, 0.8, 7.0, -6.0)
    sides(g, 0.95, 5.8, 9.0, 5.8, 5.95, 'gunmetal', 'panel_seam')
    sides(g, 0.95, 5.8, 9.0, 8.3, 8.45, 'gunmetal', 'panel_seam')
    sides(g, 0.85, 8.0, 8.3, 9.2, 12.5, 'steel', 'stock_strip')
    sides(g, 0.85, 6.2, 6.4, 9.2, 12.5, 'gunmetal', 'stock_strip')
    sides(g, 0.85, 5.8, 9.2, 10.8, 10.95, 'gunmetal', 'stock_seam')
    sides(g, 0.8, 5.8, 6.1, -8.0, -1.0, 'steel', 'handguard_rail')
    sides(g, 0.8, 5.8, 8.5, -7.95, -7.75, 'steel', 'handguard_band')
    sides(g, 0.8, 5.8, 8.5, -1.3, -1.1, 'steel', 'handguard_band')
    sides(g, 0.7, 9.3, 9.7, 0.5, 3.5, 'rubber', 'handle_window')
    for z in (-1.2, 0.0, 1.2, 2.4, 3.6):
        g.box((C - 0.5, 9.9, z), (C + 0.5, 10.05, z + 0.4), 'steel', name='handle_rib')
    screw(g, 1.0, 8.2, 12.8)
    screw(g, 1.0, 6.2, 12.8)
    for s in (-1, 1):
        g.box((C + s * 0.5 - 0.05, 9.6, -7.55), (C + s * 0.5 + 0.05, 10.3, -6.85), 'darkmetal', name='sight_wing')
    g.save(display(0.5))


if __name__ == '__main__':
    for f in (an94, m8a1, m27, mtar, smr, type25):
        f()
