#!/usr/bin/env python3
"""BO2 LMGs from the BO2 icons: HAMR (drum under an SCAR-like body), LSAT (boxy tan/black receiver, big
muzzle brake, ammo box), RPD (long barrel, front sight ring, drum, wooden furniture), Mk 48 (feed tray,
ammo pouch, short stock). Muzzle towards -Z."""
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



def drum_faces(g, cy, cz, r, mat):
    """Cover a drum's side faces with an octagonal plate (square + square at 45°) so the slab
    construction of the cylinder doesn't read as a fan of wedges."""
    h = r * 0.70
    for x0, x1 in ((C - 0.99, C - 0.94), (C + 0.94, C + 0.99)):
        for ang in (0, 45):
            kw = {'rot': ('x', ang, (C, cy, cz))} if ang else {}
            g.box((x0, cy - h, cz - h), (x1, cy + h, cz + h), mat, name='drum_face', **kw)

def hamr():
    g = Gun('hamr_v3', offset=(0, -1, 1))
    g.cyl('z', (C, 8.1), 0.75, -15, -10.5, 'steel', seg=8, name='flash_hider')
    g.cyl('z', (C, 8.35), 0.5, -10.5, -4.5, 'steel', seg=8, name='barrel')
    g.box((C - 0.6, 6.9, -4.2), (C + 0.6, 7.3, -2.8), 'gunmetal', name='bipod_mount')
    g.box((C - 0.85, 6.5, -3.8), (C + 0.85, 6.95, -2.6), 'darkmetal', name='bipod_bracket')
    for side in (-1, 1):
        g.wire([(C + side * 0.4, 6.4, -9.5), (C + side * 0.4, 7.1, -3.5)], 0.45, 'darkmetal', name='folded_bipod')
    g.box((C - 0.9, 7.1, -4.5), (C + 0.9, 9.4, 2.0), 'gunmetal', name='handguard')
    rail(g, -4.5, 9.5, 9.4)
    g.box((C - 0.15, 9.4, -4.0), (C + 0.15, 11.6, -3.2), 'darkmetal', name='front_sight')
    g.box((C - 0.15, 9.8, 7.5), (C + 0.15, 11.1, 8.1), 'darkmetal', name='rear_sight')
    g.box((C - 0.85, 7.35, 1.0), (C + 0.85, 9.4, 9.5), 'darkmetal', name='receiver')
    g.box((C - 0.8, 6.35, 2.0), (C + 0.8, 7.4, 7.5), 'gunmetal', name='drum_mount')
    g.cyl('x', (4.5, 4.6), 3.3, C - 0.95, C + 0.95, 'gunmetal', seg=16, name='drum')
    drum_faces(g, 4.5, 4.6, 3.3, 'gunmetal')
    g.cyl('x', (4.5, 4.6), 0.7, C - 1.05, C + 1.05, 'steel', seg=8, name='drum_hub')
    g.box((C - 0.8, 6.35, 7.5), (C + 0.8, 7.4, 10.4), 'darkmetal', name='lower_receiver')
    grip(g, 9.4, top=7.0, length=4.4)
    g.box((C - 0.75, 6.85, 9.0), (C + 0.75, 9.35, 13.0), 'darkmetal', name='stock_front')
    g.box((C - 0.8, 6.35, 12.6), (C + 0.8, 8.85, 16.0), 'darkmetal', name='stock_butt')
    g.box((C - 0.8, 4.4, 14.5), (C + 0.8, 6.4, 16.0), 'darkmetal', name='stock_toe')
    g.box((C - 0.85, 3.4, 16.0), (C + 0.85, 8.85, 16.4), 'rubber', name='butt_pad')
    sides(g, 0.85, 8.4, 9.0, 5.0, 6.6, 'steel', 'charging_handle', d=0.4, side=1)
    sides(g, 0.85, 7.7, 8.8, 4.0, 5.6, 'rubber', 'ejection_port', side=-1)
    sides(g, 0.85, 7.6, 8.0, 8.0, 8.8, 'steel', 'selector', side=1)
    for z in (-3.5, -2.5, -1.5, -0.5, 0.5):
        sides(g, 0.9, 8.0, 8.8, z, z + 0.4, 'rubber', 'handguard_vent')
    drum_bolts(g, 4.5, 4.6, 2.2)
    screw(g, 0.85, 8.6, 8.8)
    g.box((C - 0.55, 7.7, -8.0), (C + 0.55, 9.0, -6.8), 'gunmetal', name='gas_block')
    g.box((C - 0.3, 9.4, 7.3), (C + 0.3, 9.85, 8.3), 'steel', name='rear_sight_base')
    for side in (-1, 1):
        g.box((C + side * 0.4 - 0.2, 6.2, -9.9), (C + side * 0.4 + 0.2, 6.5, -9.4), 'steel', name='bipod_foot')
    for z in (-14.6, -14.0, -13.4):
        g.box((C - 0.72, 7.8, z), (C + 0.72, 8.4, z + 0.3), 'rubber', name='flash_slot')
    g.box((C - 0.22, 5.9, 6.0), (C + 0.22, 6.9, 6.3), 'darkmetal', name='guard_front')
    g.box((C - 0.6, 8.85, 12.6), (C + 0.6, 9.2, 15.0), 'rubber', name='cheek_pad')
    g.wire([(C, 4.7, 15.2), (C, 4.1, 15.2)], 0.3, 'steel', name='sling_swivel')
    g.save(display(0.44))


def lsat():
    g = Gun('lsat_v3', offset=(0, -1, 1))
    g.box((C - 0.75, 6.85, -14.5), (C + 0.75, 8.85, -9.5), 'steel', name='muzzle_brake')
    for z in (-13.9, -12.9, -11.9, -10.9):
        g.box((C - 0.77, 7.2, z), (C + 0.77, 8.5, z + 0.5), 'rubber', name='brake_port')
    g.cyl('z', (C, 8.1), 0.65, -10.0, -6.0, 'steel', seg=8, name='barrel')
    g.box((C - 0.12, 8.8, -7.0), (C + 0.12, 11.6, -6.2), 'darkmetal', name='front_sight')
    g.box((C - 0.95, 6.35, -7.5), (C + 0.95, 9.35, -3.0), 'gunmetal', name='handguard')
    for z in (-7.0, -5.8, -4.6):
        g.box((C - 0.97, 7.4, z), (C + 0.97, 8.6, z + 0.7), 'polymer_dark', name='handguard_slot')
    g.box((C - 0.9, 5.85, -5.5), (C + 0.9, 6.4, -3.0), 'gunmetal', name='rail_lower')
    g.box((C - 1.0, 5.85, -3.0), (C + 1.0, 9.1, 11.5), 'darkmetal', name='receiver')
    g.box((C - 0.95, 3.85, -3.0), (C + 0.95, 5.9, 8.5), 'darkmetal', name='receiver_lower')
    g.box((C - 1.02, 9.1, 0.0), (C + 1.02, 9.6, 3.5), 'gunmetal', name='feed_cover')
    g.box((C - 0.5, 9.1, 7.5), (C + 0.5, 10.4, 9.5), 'darkmetal', name='rear_sight')
    for z in (0.4, 1.4, 2.4, 3.4):
        g.box((C - 1.1, 7.6, z), (C - 1.0, 8.4, z + 0.6), 'glow_blue', name='belt_link')
    g.box((C - 1.1, 1.35, 2.0), (C + 1.1, 3.9, 7.0), 'darkmetal', name='ammo_box')
    grip(g, 7.6, top=5.0)
    g.cyl('z', (C, 7.9), 0.42, 11.0, 14.0, 'darkmetal', seg=8, name='buffer_tube')
    g.box((C - 0.85, 3.85, 13.5), (C + 0.85, 8.85, 16.2), 'polymer_dark', name='stock')
    for z in (13.9, 14.9):
        g.box((C - 0.87, 5.2, z), (C + 0.87, 8.0, z + 0.6), 'rubber', name='stock_slot')
    g.box((C - 0.85, 3.85, 16.2), (C + 0.85, 8.85, 16.6), 'rubber', name='butt_pad')
    sides(g, 1.0, 8.0, 8.6, 8.5, 10.0, 'steel', 'charging_handle', d=0.4, side=1)
    sides(g, 1.0, 7.0, 8.6, 4.5, 7.0, 'rubber', 'ejection_port', side=1)
    sides(g, 1.0, 6.2, 6.8, 9.8, 10.6, 'steel', 'selector', side=-1)
    sides(g, 1.1, 2.2, 3.2, 3.0, 3.6, 'steel', 'box_latch')
    for z in (-2.0, 6.0, 10.0):
        screw(g, 1.0, 8.4, z)
    g.box((C - 0.5, 9.35, -7.3), (C + 0.5, 9.5, -3.2), 'steel', name='handguard_top_rail')
    sides(g, 0.95, 6.4, 6.6, -7.4, -3.1, 'steel', 'handguard_rail')
    sides(g, 0.95, 6.4, 9.3, -7.5, -7.3, 'steel', 'handguard_band')
    sides(g, 0.95, 6.4, 9.3, -3.2, -3.0, 'steel', 'handguard_band')
    g.cyl('z', (C, 8.95), 0.3, -9.8, -7.5, 'steel', seg=8, name='gas_piston')
    for s in (-1, 1):
        g.box((C + s * 0.5 - 0.05, 10.4, 7.7), (C + s * 0.5 + 0.05, 10.9, 8.3), 'steel', name='sight_wing')
    for z in (11.8, 12.8):
        g.cyl('z', (C, 7.9), 0.5, z, z + 0.2, 'steel', seg=8, name='tube_ring')
    sides(g, 1.0, 5.95, 9.0, 8.2, 8.35, 'gunmetal', 'receiver_seam')
    sides(g, 1.0, 5.95, 9.0, 11.2, 11.35, 'gunmetal', 'receiver_seam')
    sides(g, 1.0, 5.95, 9.0, -2.9, -2.75, 'gunmetal', 'receiver_seam')
    for z in (4.4, 5.4, 6.4):
        sides(g, 1.1, 1.5, 3.7, z, z + 0.2, 'gunmetal', 'box_rib')
    sides(g, 0.85, 4.0, 8.7, 15.6, 15.75, 'gunmetal', 'stock_seam')
    screw(g, 1.02, 9.35, 0.3)
    g.box((C - 0.4, 9.6, 1.0), (C + 0.4, 9.9, 2.4), 'steel', name='cover_latch')
    g.save(display(0.44))


def rpd():
    g = Gun('rpd_v3', offset=(0, -1, -1))
    g.cyl('z', (C, 8.35), 0.5, -15, 3.0, 'steel', seg=8, name='barrel')
    g.ring('z', (C, 10.0), 0.7, 1.0, -9.0, -8.2, 'darkmetal', name='front_sight_ring')
    g.box((C - 0.35, 8.6, -9.0), (C + 0.35, 9.3, -8.2), 'darkmetal', name='front_sight_base')
    g.box((C - 0.6, 6.4, -8.5), (C + 0.6, 8.0, 5.5), 'gunmetal', name='gas_housing')
    g.box((C - 0.35, 5.9, -6.5), (C + 0.35, 6.5, 5.5), 'steel', name='cleaning_rod')
    g.box((C - 0.7, 6.2, -8.5), (C + 0.7, 8.2, -7.5), 'darkmetal', name='gas_block')
    g.box((C - 0.75, 6.6, 3.0), (C + 0.75, 9.0, 6.5), 'wood_maroon', name='handguard')
    g.box((C - 0.85, 6.6, 5.5), (C + 0.85, 9.1, 17.0), 'darkmetal', name='receiver')
    g.box((C - 0.8, 9.1, 8.0), (C + 0.8, 9.6, 13.6), 'gunmetal', name='feed_cover')
    g.box((C - 0.6, 9.6, 10.5), (C + 0.6, 10.1, 13.6), 'gunmetal', name='feed_hump')
    g.wire([(C - 0.9, 9.0, 9.0), (C - 0.9, 7.6, 8.6), (C - 0.9, 6.4, 9.0)], 0.3, 'brass', name='ammo_belt')
    g.cyl('x', (5.5, 13.0), 4.2, C - 0.95, C + 0.95, 'gunmetal', seg=16, name='drum')
    drum_faces(g, 5.5, 13.0, 4.2, 'gunmetal')
    g.cyl('x', (5.5, 13.0), 0.9, C - 1.05, C + 1.05, 'steel', seg=8, name='drum_hub')
    grip(g, 16.5, top=6.6, mat='wood_maroon')
    g.box((C - 0.75, 6.1, 14.0), (C + 0.75, 8.1, 17.0), 'wood_maroon', name='stock_wrist')
    g.box((C - 0.8, 5.1, 17.0), (C + 0.8, 8.1, 21.4), 'wood_maroon', name='stock_butt')
    g.box((C - 0.85, 5.0, 21.4), (C + 0.85, 8.2, 21.8), 'darkmetal', name='butt_plate')
    sides(g, 0.85, 8.1, 8.5, 7.0, 12.0, 'rubber', 'handle_slot', d=0.05, side=1)
    sides(g, 0.85, 7.8, 8.8, 9.0, 9.8, 'steel', 'charging_handle', d=0.4, side=1)
    sides(g, 0.85, 6.9, 7.5, 14.0, 14.8, 'steel', 'selector', side=-1)
    g.cyl('z', (C, 8.35), 0.65, -15, -14.0, 'darkmetal', seg=8, name='muzzle_nut')
    for z in (-12.0, -4.0):
        g.cyl('z', (C, 8.35), 0.6, z, z + 0.4, 'darkmetal', seg=8, name='barrel_band')
    drum_bolts(g, 5.5, 13.0, 2.9)
    g.wire([(C, 5.3, 19.0), (C, 4.4, 19.0)], 0.4, 'steel', name='sling_swivel')
    screw(g, 0.85, 7.4, 16.0)
    screw(g, 0.85, 8.4, 6.6)
    g.box((C - 0.45, 5.8, -6.9), (C + 0.45, 6.6, -6.5), 'darkmetal', name='rod_cap')
    sides(g, 0.8, 9.15, 9.5, 12.0, 13.0, 'steel', 'cover_latch', d=0.1)
    for y, z in ((8.5, 8.9), (8.0, 8.7), (7.5, 8.6)):
        g.box((C - 1.45, y, z), (C - 1.1, y + 0.4, z + 0.3), 'brass', name='belt_round')
    sides(g, 0.8, 6.0, 7.0, 18.0, 19.5, 'steel', 'stock_plate', d=0.06)
    g.box((C - 0.22, 5.5, 13.1), (C + 0.22, 6.7, 13.4), 'darkmetal', name='guard_front')
    g.save(display(0.4))


def mk48():
    g = Gun('mk48_v3', offset=(0, -1, 3))
    g.cyl('z', (C, 7.25), 0.75, -15, -11, 'steel', seg=8, name='flash_hider')
    g.cyl('z', (C, 7.25), 0.5, -11, -6.0, 'steel', seg=8, name='barrel')
    g.box((C - 0.35, 5.9, -11.0), (C + 0.35, 6.75, -4.4), 'gunmetal', name='gas_tube')
    g.box((C - 0.8, 4.7, -7.0), (C + 0.8, 5.2, 1.6), 'darkmetal', name='lower_rail')
    g.box((C - 0.2, 7.8, -7.0), (C + 0.2, 10.3, -5.5), 'darkmetal', name='front_sight')
    g.box((C - 0.75, 5.2, -7.0), (C + 0.75, 8.3, -4.4), 'gunmetal', name='handguard')
    g.box((C - 0.15, 8.8, -2.0), (C + 0.15, 10.3, 0.0), 'darkmetal', name='carry_handle')
    g.box((C - 0.9, 5.2, -4.5), (C + 0.9, 8.8, 11.0), 'darkmetal', name='receiver')
    g.box((C - 0.92, 8.8, 3.0), (C + 0.92, 9.3, 10.0), 'gunmetal', name='feed_cover')
    g.box((C - 0.3, 8.8, 8.5), (C + 0.3, 9.3, 11.0), 'darkmetal', name='rear_sight')
    for z in (-3.0, -2.0, -1.0):
        g.box((C - 1.0, 7.0, z), (C - 0.9, 8.0, z + 0.6), 'brass', name='belt_link')
    g.box((C - 1.45, -0.6, 2.0), (C + 1.45, 5.8, 9.3), 'gunmetal', name='ammo_pouch')
    g.box((C - 1.47, 1.8, 2.1), (C + 1.47, 2.4, 9.2), 'polymer_dark', name='pouch_strap')
    g.box((C - 1.47, 4.8, 2.0), (C + 1.47, 5.9, 9.3), 'polymer_dark', name='pouch_lid')
    grip(g, 8.0, top=5.8)
    g.box((C - 0.25, 5.6, 11.0), (C + 0.25, 7.2, 13.5), 'darkmetal', name='stock_bar')
    g.box((C - 0.7, 4.0, 13.0), (C + 0.7, 7.4, 15.6), 'polymer_dark', name='stock')
    g.box((C - 0.75, 4.0, 15.6), (C + 0.75, 7.4, 16.0), 'rubber', name='butt_pad')
    sides(g, 0.9, 7.8, 8.4, 5.0, 6.8, 'steel', 'charging_handle', d=0.4, side=-1)
    sides(g, 0.9, 6.2, 7.6, 6.8, 8.8, 'rubber', 'ejection_port', side=1)
    sides(g, 0.9, 6.0, 6.5, 6.6, 7.4, 'steel', 'selector', side=-1)
    g.box((C - 0.35, 9.3, 3.2), (C + 0.35, 9.6, 4.0), 'steel', name='cover_latch')
    for z in (-6.5, -5.5):
        sides(g, 0.75, 6.0, 7.0, z, z + 0.4, 'rubber', 'handguard_slot')
    g.box((C - 0.3, 4.3, 1.75), (C + 0.3, 5.2, 2.05), 'steel', name='pouch_buckle')
    screw(g, 0.9, 7.4, -3.0)
    screw(g, 0.9, 7.4, 10.0)
    for z in (-10.6, -9.6):
        g.ring('z', (C, 7.25), 0.5, 0.65, z, z + 0.3, 'darkmetal', name='barrel_ring')
    sides(g, 0.72, 7.0, 7.5, -14.5, -12.5, 'rubber', 'hider_slot')
    for z in (-6.8, -6.0, -5.2):
        g.box((C - 0.6, 5.0, z), (C + 0.6, 5.2, z + 0.4), 'darkmetal', name='handguard_rail')
    sides(g, 0.9, 5.6, 5.7, -4.3, 10.8, 'gunmetal', 'receiver_line')
    for z in (3.0, 4.6, 6.2, 7.8):
        sides(g, 1.45, 2.6, 4.6, z, z + 0.3, 'polymer_dark', 'pouch_rib')
    for z, y in ((2.0, 1.0), (9.0, 1.0), (2.0, 4.0), (9.0, 4.0)):
        screw(g, 1.45, y, z)
    g.box((C - 0.5, 9.3, 8.7), (C - 0.3, 9.6, 10.8), 'darkmetal', name='sight_ear')
    g.box((C + 0.3, 9.3, 8.7), (C + 0.5, 9.6, 10.8), 'darkmetal', name='sight_ear')
    g.cyl('x', (6.6, 12.2), 0.2, C - 0.25, C + 0.25, 'steel', seg=8, name='stock_pin')
    g.save(display(0.46))


if __name__ == '__main__':
    for f in (hamr, lsat, rpd, mk48):
        f()
