#!/usr/bin/env python3
"""BO2 SMGs from the BO2 icons: Chicom CQB (bullpup with green magazine and carry rail), PDW-57 (tan
P90-style shell with thumbhole and top magazine), MSMC (compact with wooden-tab foregrip), Skorpion EVO
(angular with folding stock), Vector K10 (tan angled lower and skeleton stock), Peacekeeper (black and
white with angled foregrip). Muzzle towards -Z."""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun
from bo2_shotguns import display, grip
from bo2_rifles_a import rail

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


def folding_stock(g, z0, y=8.4, length=5.0, mat='polymer_dark', drop=2.0):
    g.box((C - 0.25, y + 0.6, z0), (C + 0.25, y + 1.1, z0 + length), mat, name='stock_bar')
    g.box((C - 0.25, y - 1.6, z0 + 1.0), (C + 0.25, y - 1.1, z0 + length), mat, name='stock_bar')
    g.box((C - 0.6, y - drop, z0 + length), (C + 0.6, y + 1.3, z0 + length + 0.6), mat, name='butt_plate')


def chicom():
    g = Gun('chicom_v3', offset=(0, -1, 4))
    g.cyl('z', (C, 7.6), 0.55, -15, -12.0, 'gunmetal', seg=8, name='muzzle')
    g.box((C - 0.75, 6.8, -12.0), (C + 0.75, 9.3, -5.0), 'gunmetal', name='handguard')
    g.box((C - 0.9, 6.3, -5.0), (C + 0.9, 9.6, 5.0), 'darkmetal', name='body')
    g.box((C - 1.0, 6.0, 5.0), (C + 1.0, 9.6, 9.0), 'polymer_dark', name='rear_body')
    g.wire([(C, 9.6, -10.0), (C, 11.4, -8.0), (C, 11.4, -1.0), (C, 9.6, 1.0)], 0.35, 'darkmetal', name='carry_rail')
    g.cyl('z', (C, 10.8), 0.55, -9.0, -3.0, 'darkmetal', seg=8, name='scope')
    g.cyl('z', (C + 0.3, 10.2), 0.25, -11.4, -9.6, 'fez_red', seg=8, name='laser')
    g.box((C - 0.6, 2.6, -5.4), (C + 0.6, 6.4, -3.0), 'polymer_dark', rot=('x', -22.5, (C, 6.4, -4.2)), name='grip')
    g.wire([(C, 6.5, -7.4), (C, 5.0, -6.4), (C, 5.0, -4.2), (C, 6.5, -4.0)], 0.4, 'darkmetal', name='trigger_guard')
    g.box((C - 0.55, 0.8, -0.2), (C + 0.55, 6.4, 2.2), 'polymer_olive', name='magazine')
    g.box((C - 1.05, 5.9, 9.0), (C + 1.05, 9.6, 9.4), 'rubber', name='butt_pad')
    sides(g, 0.9, 8.4, 9.0, 1.0, 2.4, 'steel', 'cocking_handle', d=0.4, side=1)
    sides(g, 0.9, 7.2, 8.6, 0.0, 3.0, 'rubber', 'ejection_port', side=-1)
    sides(g, 0.9, 7.0, 7.5, 3.2, 4.0, 'steel', 'selector', side=-1)
    g.box((C - 0.58, 0.80, -0.25), (C + 0.58, 1.05, 2.25), 'rubber', name='mag_base_plate')
    for z in (-14.6, -13.8):
        g.cyl('z', (C, 7.6), 0.62, z, z + 0.3, 'rubber', seg=8, name='muzzle_port')
    g.box((C - 0.3, 9.6, 3.5), (C + 0.3, 10.3, 4.5), 'darkmetal', name='rear_sight')
    screw(g, 0.9, 8.0, -3.0)
    sides(g, 0.75, 8.6, 9.2, -10.5, -9.0, 'steel', 'handguard_vent', d=0.05)
    for z in (-11.0, -9.6, -8.2, -6.8):
        sides(g, 0.75, 7.3, 8.6, z, z + 0.7, 'rubber', 'handguard_slot', d=0.05)
    g.box((C - 0.5, 9.3, -9.0), (C + 0.5, 9.6, -3.0), 'gunmetal', name='scope_mount')
    g.cyl('z', (C, 10.8), 0.62, -9.0, -8.6, 'steel', seg=8, name='scope_ring_front')
    g.cyl('z', (C, 10.8), 0.62, -3.4, -3.0, 'steel', seg=8, name='scope_ring_rear')
    g.box((C - 0.12, 9.6, -10.0), (C + 0.12, 10.0, -9.6), 'steel', name='rail_foot_front')
    g.box((C - 0.12, 9.6, 0.6), (C + 0.12, 10.0, 1.0), 'steel', name='rail_foot_rear')
    sides(g, 0.9, 6.6, 7.0, -2.0, -0.4, 'steel', 'mag_release', side=1)
    g.box((C - 0.35, 9.6, 3.2), (C + 0.35, 9.9, 4.8), 'steel', name='sight_base')
    g.ring('x', (6.6, 7.0), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
    screw(g, 1.0, 8.0, 7.0)
    g.save(display(0.55))


def pdw57():
    g = Gun('pdw57_v3', offset=(0, -1, 5))
    g.cyl('z', (C, 8.0), 0.55, -11.8, -11.0, 'darkmetal', seg=16, name='suppressor_neck')
    g.cyl('z', (C, 8.0), 0.95, -14.0, -11.8, 'darkmetal', seg=16, name='suppressor_cap')
    g.cyl('z', (C, 8.0), 0.5, -14.05, -13.95, 'rubber', seg=16, name='bore')
    # Perforations: small dark dots on the sides and top of the cap, not full rings
    for z in (-13.5, -12.9, -12.3):
        for dy in (-0.35, 0.35):
            for side in (-1, 1):
                x0, x1 = sorted((C + side * 0.93, C + side * 0.97))
                g.box((x0, 8.0 + dy - 0.13, z), (x1, 8.0 + dy + 0.13, z + 0.26), 'rubber', name='cap_hole')
    g.box((C - 1.1, 6.5, -11.0), (C + 1.1, 7.9, 6.0), 'polymer_tan', name='shell')
    g.box((C - 1.0, 7.9, -11.0), (C + 1.0, 9.0, 3.5), 'darkmetal', name='upper_receiver')
    g.box((C - 1.1, 7.9, 3.5), (C + 1.1, 9.0, 6.0), 'polymer_tan', name='shell_rear')
    g.box((C - 1.0, 9.0, -10.0), (C + 1.0, 9.45, 2.5), 'darkmetal', name='top_magazine')
    g.box((C - 0.3, 9.45, -9.5), (C + 0.3, 9.9, -8.5), 'darkmetal', name='front_sight')
    g.box((C - 0.4, 9.45, 1.5), (C + 0.4, 10.2, 2.5), 'darkmetal', name='rear_sight')
    g.box((C - 0.9, 5.5, -10.0), (C + 0.9, 6.7, -8.5), 'polymer_tan', name='front_pillar')
    g.box((C - 0.6, 3.6, -6.0), (C + 0.6, 7.0, -4.0), 'polymer_tan', rot=('x', -22.5, (C, 7.0, -5.0)), name='grip')
    g.box((C - 0.1, 5.8, -7.3), (C + 0.1, 6.6, -7.0), 'steel', name='trigger')
    g.box((C - 0.8, 2.4, -1.7), (C + 0.8, 7.4, 0.7), 'polymer_tan', rot=('x', -45, (C, 7.4, -0.5)), name='rear_leg')
    g.box((C - 0.7, 3.3, -4.6), (C + 0.7, 4.2, 3.4), 'polymer_tan', name='loop_bottom_bar')
    g.box((C - 1.05, 4.4, 3.5), (C + 1.05, 8.6, 6.0), 'darkmetal', name='butt_block')
    g.box((C - 1.15, 5.2, 6.0), (C + 1.15, 8.6, 7.2), 'rubber', name='butt_pad')
    sides(g, 1.0, 8.3, 8.8, -8.0, -6.8, 'steel', 'charging_handle', d=0.35)
    sides(g, 1.1, 6.6, 7.1, -3.0, -2.2, 'steel', 'selector', side=1)
    g.box((C - 0.35, 9.0, 2.5), (C + 0.35, 9.5, 3.3), 'steel', name='mag_latch')
    for z in (-8.0, -6.0, -4.0, -2.0, 0.0):
        sides(g, 1.0, 9.1, 9.35, z, z + 0.8, 'rubber', 'mag_window')
    screw(g, 1.1, 7.2, -9.0)
    screw(g, 1.1, 7.2, 4.5)
    g.cyl('z', (C, 8.0), 0.7, -11.8, -11.3, 'steel', seg=8, name='neck_ring')
    for z in (-9.0, -7.0, -5.0, -3.0, -1.0):
        g.box((C - 0.7, 9.4, z), (C + 0.7, 9.55, z + 0.4), 'gunmetal', name='top_ridge')
    sides(g, 1.0, 7.9, 8.4, 0.8, 2.4, 'rubber', 'ejection_port', side=1, d=0.05)
    sides(g, 0.9, 5.6, 6.5, -9.6, -8.9, 'rubber', 'pillar_slot', d=0.05)
    for side in (-1, 1):
        g.box((C + side * 0.5 - 0.06, 9.45, 1.5), (C + side * 0.5 + 0.06, 10.3, 2.0), 'darkmetal', name='sight_wing')
    screw(g, 1.1, 7.4, -0.5)
    g.save(display(0.6))


def msmc():
    g = Gun('msmc_v3', offset=(0, -1, 6))
    g.cyl('z', (C, 9.2), 0.55, -14.0, -11.8, 'steel', seg=8, name='muzzle_brake')
    for z in (-13.6, -12.8, -12.0):
        g.cyl('z', (C, 9.2), 0.6, z, z + 0.3, 'rubber', seg=8, name='muzzle_port')
    g.cyl('z', (C, 9.2), 0.32, -11.8, -7.4, 'steel', seg=8, name='barrel')
    g.box((C - 0.8, 7.2, -7.4), (C + 0.8, 10.2, 4.0), 'darkmetal', name='upper')
    rail(g, -7.2, 3.6, 10.2, hw=0.5)
    g.box((C - 0.15, 10.6, -6.8), (C + 0.15, 11.4, -6.2), 'darkmetal', name='front_sight')
    g.box((C - 0.55, 3.0, -2.5), (C + 0.55, 7.4, -1.0), 'wood_maroon', name='foregrip')
    g.box((C - 0.6, 2.8, -2.7), (C + 0.6, 3.3, -0.8), 'darkmetal', name='foregrip_cap')
    g.box((C - 0.55, 0.8, -0.3), (C + 0.55, 7.8, 1.7), 'gunmetal', name='grip_magazine')
    g.box((C - 0.22, 6.95, -3.4), (C + 0.22, 7.3, -0.8), 'darkmetal', name='guard_bottom')
    g.box((C - 0.5, 7.9, 3.0), (C + 0.5, 10.0, 5.6), 'polymer_dark', name='stock_block')
    g.box((C - 0.6, 5.4, 5.6), (C + 0.6, 8.9, 6.6), 'polymer_dark', name='butt_plate')
    sides(g, 0.8, 9.0, 9.6, 0.5, 2.0, 'steel', 'charging_handle', d=0.4, side=-1)
    sides(g, 0.8, 8.2, 9.6, -1.0, 2.0, 'rubber', 'ejection_port', side=1)
    g.box((C - 0.58, 0.80, -0.35), (C + 0.58, 1.05, 1.75), 'rubber', name='mag_base_plate')
    g.cyl('x', (5.2, -1.75), 0.15, C - 0.65, C + 0.65, 'steel', seg=8, name='foregrip_screw')
    g.box((C - 0.3, 10.2, 2.4), (C + 0.3, 10.9, 3.4), 'darkmetal', name='rear_sight')
    screw(g, 0.8, 8.2, -5.5)
    screw(g, 0.8, 8.2, 2.8)
    sides(g, 0.55, 6.6, 7.2, 1.0, 1.7, 'steel', 'mag_release', d=0.12)
    for y in (3.9, 4.7, 5.5, 6.3):
        sides(g, 0.55, y, y + 0.25, -2.4, -1.1, 'rubber', 'foregrip_rib', d=0.05)
    g.box((C - 0.45, 8.9, -9.5), (C + 0.45, 9.5, -8.8), 'steel', name='gas_block')
    g.cyl('x', (9.4, 4.8), 0.2, C - 0.55, C + 0.55, 'steel', seg=8, name='stock_pin')
    sides(g, 0.8, 7.5, 8.0, 3.0, 3.8, 'steel', 'selector', d=0.25, side=1)
    g.box((C - 0.1, 6.2, -2.3), (C + 0.1, 7.0, -2.0), 'steel', name='trigger')
    g.save(display(0.6))


def skorpion():
    g = Gun('skorpion_v3', offset=(0, -1, 5))
    g.cyl('z', (C, 7.7), 0.8, -14.0, -11.4, 'steel', seg=8, name='muzzle')
    g.box((C - 0.85, 7.4, -11.4), (C + 0.85, 8.1, -4.0), 'polymer_dark', name='upper_front')
    g.box((C - 0.95, 7.4, -8.5), (C + 0.95, 9.2, 1.0), 'darkmetal', name='upper')
    g.box((C - 0.75, 7.6, 1.0), (C + 0.75, 9.0, 4.0), 'gunmetal', name='upper_rear')
    g.box((C - 0.87, 7.6, -10.0), (C + 0.87, 8.0, -4.0), 'gunmetal', name='side_rail')
    rail(g, -4.0, 3.6, 9.2, hw=0.45)
    g.box((C - 0.2, 9.1, -5.0), (C + 0.2, 10.4, -4.2), 'darkmetal', name='front_sight')
    g.box((C - 0.3, 9.4, 2.0), (C + 0.3, 10.4, 3.2), 'darkmetal', name='rear_sight')
    g.box((C - 0.75, 6.9, -9.0), (C + 0.75, 7.4, 1.0), 'polymer_dark', name='lower')
    g.box((C - 0.5, 5.4, -9.6), (C + 0.5, 7.0, -7.4), 'polymer_dark', name='front_guard')
    g.box((C - 0.55, 1.2, -2.8), (C + 0.55, 6.9, -0.8), 'gunmetal', name='magazine')
    g.box((C - 0.9, 3.0, 0.7), (C + 0.9, 7.2, 2.5), 'polymer_dark', rot=('x', -22.5, (C, 7.2, 1.6)), name='grip')
    g.box((C - 0.22, 6.0, -1.9), (C + 0.22, 6.3, 0.6), 'darkmetal', name='guard_bottom')
    g.box((C - 0.08, 6.3, -0.9), (C + 0.08, 7.0, -0.6), 'steel', name='trigger')
    g.box((C - 0.3, 7.2, 3.6), (C + 0.3, 8.5, 8.6), 'polymer_dark', name='stock_bar')
    g.box((C - 0.6, 5.2, 8.0), (C + 0.6, 8.3, 9.0), 'polymer_dark', name='butt_plate')
    sides(g, 0.95, 8.2, 8.8, -2.0, -1.0, 'steel', 'cocking_handle', d=0.4, side=-1)
    sides(g, 0.95, 7.9, 9.0, -4.0, -1.6, 'rubber', 'ejection_port', side=1)
    sides(g, 0.95, 7.5, 8.0, -0.4, 0.4, 'steel', 'safety_lever', side=-1)
    g.box((C - 0.58, 1.20, -2.85), (C + 0.58, 1.45, -0.75), 'rubber', name='mag_base_plate')
    for z in (-13.6, -12.8):
        g.cyl('z', (C, 7.7), 0.85, z, z + 0.3, 'rubber', seg=8, name='muzzle_port')
    screw(g, 0.95, 8.4, -7.5)
    screw(g, 0.95, 8.4, 0.2, side=1)
    for z in (-10.0, -9.0, -8.0, -7.0, -6.0):
        sides(g, 0.87, 7.7, 7.95, z, z + 0.5, 'rubber', 'handguard_slot')
    for z in (-3.5, -2.5, -1.5, -0.5, 0.5, 1.5, 2.5):
        g.box((C - 0.1, 9.2, z), (C + 0.1, 9.5, z + 0.35), 'steel', name='rail_nub')
    for y in (2.2, 3.0, 3.8, 4.6, 5.4):
        sides(g, 0.55, y, y + 0.3, -2.7, -0.9, 'rubber', 'mag_rib')
    sides(g, 0.55, 6.2, 6.7, -2.8, -2.2, 'steel', 'mag_release', side=1)
    sides(g, 0.6, 7.3, 8.3, 4.2, 4.6, 'steel', 'stock_hinge')
    g.cyl('x', (7.8, 4.4), 0.2, C - 0.64, C + 0.64, 'steel', seg=8, name='stock_hinge_pin')
    g.box((C - 0.3, 10.4, -5.0), (C + 0.3, 10.5, -4.2), 'gunmetal', name='front_sight_hood')
    sides(g, 0.9, 4.0, 6.0, 0.9, 1.1, 'rubber', 'grip_groove', d=0.06)
    g.save(display(0.55))


def vector():
    g = Gun('vector_v3', offset=(0, -1, 5))
    g.cyl('z', (C, 7.6), 0.4, -14.0, -9.6, 'steel', seg=8, name='barrel')
    g.box((C - 0.8, 8.0, -11.4), (C + 0.8, 9.8, 3.1), 'darkmetal', name='upper')
    rail(g, -9.6, 3.0, 9.8, hw=0.45)
    g.box((C - 0.15, 9.8, -8.4), (C + 0.15, 11.6, -7.8), 'darkmetal', name='front_sight')
    g.box((C - 0.2, 9.8, 0.2), (C + 0.2, 11.4, 1.0), 'darkmetal', name='rear_sight')
    g.box((C - 0.85, 7.1, -9.8), (C + 0.85, 8.1, -5.5), 'polymer_tan', name='lower')
    g.box((C - 0.85, 4.1, -5.5), (C + 0.85, 8.1, -0.8), 'polymer_tan', name='lower_magwell')
    g.box((C - 0.6, -1.1, -3.9), (C + 0.6, 4.2, -1.7), 'gunmetal', name='magazine')
    g.box((C - 0.8, 6.6, -0.8), (C + 0.8, 8.1, 2.6), 'polymer_tan', name='trigger_housing')
    g.box((C - 0.2, 4.6, -1.0), (C + 0.2, 4.9, 0.4), 'polymer_tan', name='guard_bottom')
    g.box((C - 0.1, 5.4, -0.6), (C + 0.1, 6.6, -0.3), 'steel', name='trigger')
    g.box((C - 0.6, 3.2, 0.3), (C + 0.6, 7.0, 2.2), 'polymer_dark', name='grip')
    g.box((C - 0.25, 8.1, 2.5), (C + 0.25, 8.9, 7.6), 'polymer_dark', name='stock_bar')
    g.box((C - 0.35, 4.1, 5.8), (C + 0.35, 8.9, 7.6), 'polymer_dark', name='stock_butt')
    g.box((C - 0.5, 4.5, 7.6), (C + 0.5, 7.8, 8.0), 'rubber', name='butt_pad')
    sides(g, 0.8, 9.0, 9.5, 0.5, 2.0, 'steel', 'charging_handle', d=0.4, side=1)
    sides(g, 0.85, 5.2, 6.0, -2.0, -1.2, 'steel', 'mag_release', side=1)
    sides(g, 0.8, 7.4, 7.9, 1.6, 2.4, 'steel', 'selector')
    g.box((C - 0.62, -1.10, -3.95), (C + 0.62, -0.85, -1.65), 'rubber', name='mag_base_plate')
    for z in (-10.8, -9.6):
        sides(g, 0.8, 8.4, 9.4, z, z + 0.5, 'rubber', 'upper_vent')
    screw(g, 0.8, 8.8, -5.0)
    screw(g, 0.8, 8.8, 2.4)
    g.cyl('z', (C, 7.6), 0.6, -11.4, -10.8, 'darkmetal', seg=8, name='barrel_collar')
    for y in (0.2, 1.2, 2.2, 3.2):
        sides(g, 0.6, y, y + 0.35, -3.8, -1.8, 'rubber', 'mag_rib')
    sides(g, 0.6, 3.8, 5.6, 0.6, 1.9, 'rubber', 'grip_panel')
    for s_ in (-1, 1):
        g.box((C + s_ * 0.12, 9.8, -8.5), (C + s_ * 0.32, 11.6, -8.35), 'darkmetal', name='sight_ear')
        g.box((C + s_ * 0.12, 9.8, 0.1), (C + s_ * 0.32, 11.4, 0.25), 'darkmetal', name='sight_ear')
    g.box((C - 0.2, 4.6, -1.0), (C + 0.2, 6.6, -0.8), 'polymer_tan', name='guard_front')
    g.cyl('x', (8.5, 3.0), 0.3, C - 0.4, C + 0.4, 'steel', seg=8, name='stock_hinge')
    screw(g, 0.85, 7.6, -7.5)
    screw(g, 0.85, 6.0, -3.0)
    sides(g, 0.85, 4.6, 5.2, -4.8, -3.4, 'rubber', 'magwell_port')
    g.save(display(0.55))


def peacekeeper():
    g = Gun('peacekeeper_v3', offset=(0, -1, 3))
    g.box((C - 0.8, 7.0, -15), (C + 0.8, 8.8, -12.4), 'gunmetal', name='flash_hider')
    g.cyl('z', (C + 0.95, 8.4), 0.38, -11.8, -9.8, 'steel', seg=8, name='flashlight')
    g.box((C - 0.9, 6.4, -12.4), (C + 0.9, 9.0, -1.0), 'polymer_dark', name='handguard')
    for z in (-11.4, -9.8, -8.2, -6.6, -5.0):
        g.box((C - 0.92, 7.8, z), (C + 0.92, 8.8, z + 1.0), 'rubber', name='handguard_cut')
    rail(g, -12.0, 8.0, 9.0, hw=0.5)
    g.box((C - 0.15, 9.0, -5.8), (C + 0.15, 11.2, -5.2), 'darkmetal', name='front_sight')
    g.box((C - 0.2, 9.0, 4.5), (C + 0.2, 10.9, 5.5), 'darkmetal', name='rear_sight')
    g.box((C - 0.6, 5.5, -10.0), (C + 0.6, 7.1, -3.5), 'polymer_dark', rot=('x', 22.5, (C, 6.3, -10.0)), name='angled_foregrip')
    g.box((C - 0.75, 5.2, -4.5), (C + 0.75, 6.8, -1.6), 'polymer_dark', name='magwell')
    g.box((C - 0.85, 6.4, -1.0), (C + 0.85, 9.0, 7.0), 'polymer_white', name='receiver')
    g.box((C - 0.87, 8.0, 1.0), (C + 0.87, 8.4, 6.0), 'glow_blue', name='receiver_stripe')
    g.box((C - 0.6, 0.0, -0.9), (C + 0.6, 6.9, 2.6), 'polymer_dark', name='magazine')
    GRIP = ('x', -22.5, (C, 6.9, 4.2))
    g.box((C - 0.68, 1.5, 2.9), (C + 0.68, 6.9, 5.5), 'polymer_dark', rot=GRIP, name='grip')
    g.box((C - 0.22, 5.8, 1.6), (C + 0.22, 6.1, 4.0), 'darkmetal', name='guard_bottom')
    g.box((C - 0.08, 6.1, 2.4), (C + 0.08, 6.9, 2.7), 'steel', name='trigger')
    g.box((C - 0.75, 7.9, 7.0), (C + 0.75, 8.9, 9.0), 'polymer_white', name='stock_top')
    g.box((C - 0.75, 7.7, 9.0), (C + 0.75, 8.6, 10.4), 'polymer_white', name='stock_top_rear')
    g.box((C - 0.75, 6.4, 7.0), (C + 0.75, 8.9, 8.0), 'polymer_white', name='stock_front')
    g.box((C - 0.6, 6.0, 8.0), (C + 0.6, 6.8, 10.4), 'polymer_white', name='stock_lower')
    g.box((C - 0.8, 6.0, 10.4), (C + 0.8, 8.4, 11.6), 'polymer_white', name='stock_rear')
    g.box((C - 0.82, 5.7, 11.6), (C + 0.82, 8.5, 12.2), 'rubber', name='butt_pad')
    sides(g, 0.85, 8.3, 8.8, -0.5, 0.8, 'steel', 'charging_handle', d=0.4, side=-1)
    sides(g, 0.85, 6.8, 7.8, 3.0, 5.0, 'rubber', 'ejection_port', side=1)
    sides(g, 0.85, 6.6, 7.1, 5.4, 6.2, 'steel', 'selector', side=-1)
    sides(g, 0.75, 5.7, 6.3, -2.2, -1.6, 'steel', 'mag_release', side=1)
    g.box((C - 0.62, 0.00, -0.95), (C + 0.62, 0.25, 2.65), 'rubber', name='mag_base_plate')
    for z in (-14.4, -13.4):
        sides(g, 0.8, 7.4, 8.4, z, z + 0.4, 'rubber', 'hider_port')
    for x in (-0.3, 0.3):
        g.box((C + x - 0.12, 9.5, -11.4), (C + x + 0.12, 10.9, -11.0), 'darkmetal', name='front_sight_ear')
    g.box((C - 0.3, 9.5, -11.4), (C + 0.3, 9.7, -11.0), 'darkmetal', name='front_sight_base')
    for x in (-0.3, 0.3):
        g.box((C + x - 0.12, 9.5, 4.5), (C + x + 0.12, 10.9, 5.0), 'darkmetal', name='rear_sight_ear')
    sides(g, 0.9, 7.0, 8.4, -6.8, -6.2, 'steel', 'handguard_insert', d=0.1)
    sides(g, 0.9, 7.0, 8.4, -4.0, -3.4, 'steel', 'handguard_insert', d=0.1)
    sides(g, 0.85, 7.6, 8.0, 0.4, 2.8, 'darkmetal', 'trigger_pin_plate', d=0.06, side=-1)
    screw(g, 0.75, 8.0, 9.4)
    screw(g, 0.9, 7.6, -3.0)
    sides(g, 0.75, 7.0, 7.9, 8.0, 10.4, 'darkmetal', 'stock_web', d=0.08)
    sides(g, 0.8, 7.9, 8.2, 10.6, 11.4, 'glow_blue', 'stock_stripe', d=0.06)
    screw(g, 0.85, 7.6, 6.2)
    g.save(display(0.5))


if __name__ == '__main__':
    for f in (chicom, pdw57, msmc, skorpion, vector, peacekeeper):
        f()
