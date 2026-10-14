#!/usr/bin/env python3
"""BO2 snipers from the BO2 icons: Barrett M82A1 (huge muzzle brake, long barrel with bipod, ribbed upper
receiver, box magazine, big scope), DSR 50 (bullpup chassis, bipod, scope), SVU-AS (bullpup Dragunov
with suppressor, scope, grip and stock). Muzzle towards -Z."""
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


def scope(g, z0, z1, y, r=0.55, obj=0.85):
    g.cyl('z', (C, y), r, z0 + 1.6, z1 - 1.2, 'darkmetal', seg=8, name='scope_tube')
    g.cyl('z', (C, y), obj, z0, z0 + 1.8, 'darkmetal', seg=8, name='scope_objective')
    g.cyl('z', (C, y), obj - 0.15, z0 - 0.15, z0, 'glass', seg=8, name='objective_lens')
    g.cyl('z', (C, y), r + 0.15, z1 - 1.4, z1, 'darkmetal', seg=8, name='scope_eyepiece')
    g.cyl('y', (C, (z0 + z1) / 2), 0.32, y + r, y + r + 0.5, 'darkmetal', seg=8, name='scope_turret')
    for z in (z0 + 2.4, z1 - 2.6):
        g.box((C - 0.3, y - r - 0.9, z), (C + 0.3, y - r + 0.1, z + 0.7), 'darkmetal', name='scope_ring')


def barret():
    g = Gun('barret_v3', offset=(0, -1, 0))
    g.box((C - 0.7, 7.2, -15.6), (C + 0.7, 9.15, -12.0), 'darkmetal', name='muzzle_brake')
    for z in (-15.2, -14.3, -13.4):
        g.box((C - 0.72, 7.6, z), (C + 0.72, 8.8, z + 0.5), 'rubber', name='brake_port')
    g.cyl('z', (C, 8.4), 0.75, -12.0, -2.0, 'steel', seg=8, name='barrel')
    g.box((C - 0.35, 5.9, -9.0), (C + 0.35, 7.5, 1.4), 'darkmetal', name='bipod_rail')
    for side in (-1, 1):
        g.wire([(C + side * 0.5, 6.2, -9.0), (C + side * 0.5, 6.2, -1.0)], 0.4, 'steel', name='folded_bipod')
    g.box((C - 0.9, 7.15, -2.6), (C + 0.9, 9.65, 12.0), 'worn_steel', name='upper_receiver')
    for z in (-2.0, -1.0, 0.0, 1.0):
        g.box((C - 0.92, 7.8, z), (C + 0.92, 8.9, z + 0.5), 'darkmetal', name='upper_slot')
    g.box((C - 0.85, 5.9, 5.9), (C + 0.85, 7.2, 11.4), 'gunmetal', name='lower_receiver')
    g.box((C - 0.7, 2.65, 6.4), (C + 0.7, 6.0, 10.4), 'darkmetal', name='magazine')
    grip(g, 10.9, top=6.9)
    g.box((C - 0.8, 7.15, 12.0), (C + 0.8, 9.15, 16.4), 'gunmetal', name='stock_top')
    g.box((C - 0.8, 5.15, 13.9), (C + 0.8, 7.2, 16.4), 'gunmetal', name='stock_rear')
    g.box((C - 0.85, 5.15, 16.4), (C + 0.85, 9.15, 17.0), 'rubber', name='butt_pad')
    scope(g, 1.0, 11.5, 10.9, r=0.85, obj=1.25)
    sides(g, 0.9, 8.5, 9.1, 7.0, 8.8, 'steel', 'charging_handle', d=0.4, side=1)
    sides(g, 0.9, 8.0, 9.3, 4.0, 6.0, 'rubber', 'ejection_port', side=-1)
    sides(g, 0.85, 6.3, 6.8, 9.6, 10.4, 'steel', 'selector', side=1)
    g.box((C - 0.72, 2.65, 6.35), (C + 0.72, 2.90, 10.45), 'rubber', name='mag_base_plate')
    g.cyl('x', (6.7, -8.5), 0.35, C - 0.6, C + 0.6, 'steel', seg=8, name='bipod_pivot')
    screw(g, 0.9, 8.8, 10.0)
    screw(g, 0.9, 8.8, -1.5)
    for z in (-8.0, -6.0, -4.0):
        g.cyl('z', (C, 8.4), 0.82, z, z + 0.3, 'darkmetal', seg=8, name='barrel_ring')
    g.cyl('z', (C, 8.4), 0.85, -2.2, -1.9, 'gunmetal', seg=8, name='barrel_clamp')
    for s in (-1, 1):
        for z in (-8.0, -7.0, -6.0, -5.0, -4.0, -3.0, -2.0):
            g.box((C + s * 0.66 - 0.04, 6.05, z), (C + s * 0.66 + 0.04, 6.35, z + 0.3), 'rubber', name='bipod_hole')
    for z in (-2.4, -1.6, -0.8, 0.0):
        g.box((C - 0.5, 9.65, z), (C + 0.5, 9.8, z + 0.35), 'steel', name='rail_slot')
    sides(g, 0.9, 7.25, 9.55, 3.0, 3.15, 'gunmetal', 'receiver_seam')
    sides(g, 0.9, 7.25, 9.55, 11.8, 11.95, 'gunmetal', 'receiver_seam')
    screw(g, 0.85, 6.5, 6.5)
    screw(g, 0.85, 6.5, 10.8)
    sides(g, 0.8, 8.4, 8.6, 12.1, 16.3, 'steel', 'cheek_strip')
    sides(g, 0.8, 5.3, 7.0, 15.2, 15.35, 'steel', 'stock_seam')
    for y in (3.2, 4.2, 5.2):
        sides(g, 0.7, y, y + 0.2, 6.5, 10.3, 'steel', 'mag_rib')
    g.save(display(0.38))


def dsr50():
    g = Gun('dsr50_v3', offset=(0, -1, 1))
    g.box((C - 0.75, 7.25, -15.0), (C + 0.75, 9.25, -11.5), 'darkmetal', name='muzzle_brake')
    g.cyl('z', (C, 8.25), 0.5, -11.5, -8.5, 'steel', seg=8, name='barrel')
    g.box((C - 0.8, 6.75, -8.5), (C + 0.8, 9.25, -1.5), 'darkmetal', name='handguard')
    g.box((C - 0.15, 9.25, -6.0), (C + 0.15, 9.75, -4.0), 'darkmetal', name='front_sight')
    for side in (-1, 1):
        g.wire([(C + side * 0.4, 6.6, -8.5), (C + side * 0.4, 6.0, -2.0)], 0.3, 'steel', name='folded_bipod')
    g.box((C - 0.5, 5.75, -6.0), (C + 0.5, 6.8, -1.5), 'darkmetal', name='lower_rail')
    g.box((C - 0.9, 6.75, -1.5), (C + 0.9, 9.25, 14.0), 'darkmetal', name='chassis')
    g.box((C - 0.92, 7.2, 2.0), (C + 0.92, 8.8, 10.0), 'gunmetal', name='chassis_panel')
    g.box((C - 0.6, 2.2, 5.5), (C + 0.6, 6.8, 7.5), 'gunmetal', name='magazine')
    grip(g, 1.5, top=6.75)
    g.box((C - 0.85, 4.75, 4.0), (C + 0.85, 6.8, 12.5), 'polymer_dark', name='butt_belly')
    g.box((C - 0.85, 2.2, 12.5), (C + 0.85, 6.8, 14.0), 'polymer_dark', name='butt_hook')
    g.box((C - 0.95, 2.2, 14.0), (C + 0.95, 9.25, 14.4), 'rubber', name='butt_pad')
    scope(g, -0.5, 8.5, 10.6, r=0.75, obj=1.0)
    g.cyl('x', (8.2, 4.6), 0.3, C + 0.87, C + 1.27, 'steel', seg=8, name='bolt_knob')
    sides(g, 0.92, 7.9, 8.6, 7.2, 9.0, 'rubber', 'ejection_port', d=0.05, side=1)
    sides(g, 0.9, 8.4, 8.8, 10.5, 11.3, 'steel', 'safety', side=-1)
    g.box((C - 0.62, 2.20, 5.45), (C + 0.62, 2.45, 7.55), 'rubber', name='mag_base_plate')
    for z in (-7.5, -6.2, -4.9, -3.6):
        sides(g, 0.8, 7.4, 8.6, z, z + 0.5, 'rubber', 'handguard_slot')
    screw(g, 0.9, 8.4, 12.0)
    screw(g, 0.9, 8.4, 0.0)
    g.box((C - 0.6, 6.7, -5.0), (C + 0.6, 6.9, -2.0), 'rubber', name='forend_pad')
    for z in (3.0, 5.0):
        g.box((C - 0.6, 9.25, z), (C + 0.6, 9.5, z + 0.7), 'steel', name='top_rail_tooth')
    g.box((C - 0.4, 9.25, 9.6), (C + 0.4, 9.7, 11.0), 'steel', name='rear_sight_flip')
    g.wire([(C - 0.9, 4.0, 13.0), (C - 1.25, 4.0, 13.0)], 0.3, 'steel', name='sling_stud')
    g.wire([(C, 6.75, -1.0), (C, 6.2, -1.0)], 0.35, 'steel', name='front_sling_loop')
    sides(g, 0.92, 4.8, 5.8, 8.2, 9.8, 'rubber', 'cheek_grip', d=0.05)
    sides(g, 0.5, 4.5, 5.0, 6.0, 7.5, 'steel', 'mag_catch', side=1)
    g.box((C - 0.5, 9.25, -0.8), (C + 0.5, 9.45, 0.8), 'gunmetal', name='scope_mount_plate')
    g.cyl('z', (C, 8.25), 0.6, -11.5, -10.8, 'steel', seg=8, name='barrel_collar')
    for z in (-14.2, -13.2, -12.2):
        g.box((C - 0.78, 7.6, z), (C + 0.78, 8.9, z + 0.35), 'rubber', name='brake_slot')
    g.save(display(0.44))


def svu():
    g = Gun('svu_v3', offset=(0, -1, 2))
    g.cyl('z', (C, 8.5), 1.5, -15.0, -7.0, 'darkmetal', seg=16, name='suppressor')
    g.box((C - 0.5, 9.9, -9.5), (C + 0.5, 11.0, -8.0), 'darkmetal', name='front_sight_block')
    g.cyl('z', (C, 8.2), 0.75, -7.0, -5.0, 'steel', seg=8, name='barrel')
    g.cyl('z', (C, 7.6), 0.3, -8.0, -5.0, 'steel', seg=8, name='gas_tube')
    g.box((C - 0.45, 5.45, -7.5), (C + 0.45, 7.0, -4.0), 'darkmetal', name='bipod_clamp')
    g.box((C - 0.5, 5.45, -4.0), (C + 0.5, 7.0, 6.0), 'darkmetal', name='lower_rail')
    g.box((C - 0.85, 6.95, -5.0), (C + 0.85, 9.45, 11.5), 'darkmetal', name='body')
    g.box((C - 0.87, 7.6, -3.0), (C + 0.87, 9.0, 6.0), 'red_dark', name='side_panel')
    g.box((C - 0.5, 9.45, 1.5), (C + 0.5, 11.6, 6.5), 'darkmetal', name='scope_mount')
    g.box((C - 0.6, 2.95, 7.5), (C + 0.6, 6.95, 10.5), 'gunmetal', name='magazine')
    grip(g, 5.5, top=6.45)
    g.box((C - 0.85, 5.45, 7.0, ), (C + 0.85, 7.0, 12.0), 'polymer_dark', name='butt_belly')
    g.box((C - 0.85, 3.95, 11.0), (C + 0.85, 7.0, 12.0), 'polymer_dark', name='butt_hook')
    g.box((C - 0.9, 3.95, 12.0), (C + 0.9, 9.45, 12.4), 'rubber', name='butt_pad')
    scope(g, -1.0, 8.0, 12.4, r=0.95, obj=1.5)
    sides(g, 0.85, 8.4, 9.0, 6.8, 8.4, 'steel', 'charging_handle', d=0.4, side=1)
    sides(g, 0.87, 8.0, 9.2, 6.2, 8.5, 'rubber', 'ejection_port', side=-1)
    sides(g, 0.85, 7.1, 7.6, 8.8, 10.0, 'steel', 'selector', side=1)
    g.box((C - 0.3, 5.0, 10.5), (C + 0.3, 6.0, 11.2), 'steel', name='mag_release')
    g.box((C - 0.62, 2.95, 7.45), (C + 0.62, 3.20, 10.55), 'rubber', name='mag_base_plate')
    for z in (-11.0, -8.0):
        g.cyl('z', (C, 8.5), 1.58, z, z + 0.4, 'gunmetal', seg=16, name='suppressor_band')
    g.cyl('z', (C, 8.5), 0.6, -15.05, -14.95, 'rubber', seg=16, name='bore')
    screw(g, 0.85, 8.2, 9.0)
    screw(g, 0.85, 8.2, -4.2)
    for z in (2.0, 5.2):
        sides(g, 0.5, 9.6, 11.0, z, z + 0.6, 'steel', 'mount_clamp', d=0.15)
    g.cyl('z', (C, 8.5), 1.25, -7.0, -6.6, 'steel', seg=8, name='can_collar')
    sides(g, 0.5, 10.0, 10.8, -9.4, -8.1, 'steel', 'sight_ear', d=0.08)
    for z in (-2.0, -1.0, 0.0, 1.0, 2.0):
        g.box((C - 0.52, 5.4, z), (C + 0.52, 5.6, z + 0.3), 'rubber', name='rail_groove')
    for y in (4.0, 4.8, 5.6):
        sides(g, 0.6, y, y + 0.15, 7.6, 10.4, 'rubber', 'mag_rib', d=0.04)
    g.box((C - 0.4, 7.2, -6.5), (C + 0.4, 8.0, -6.1), 'steel', name='gas_clamp')
    screw(g, 0.87, 8.8, -2.5)
    screw(g, 0.87, 7.9, 5.5)
    g.save(display(0.44))


if __name__ == '__main__':
    for f in (barret, dsr50, svu):
        f()
