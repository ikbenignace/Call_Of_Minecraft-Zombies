#!/usr/bin/env python3
"""BO2 sidearms from the BO2 side-view icons: B23R, Executioner, Five-seveN, KAP-40. Pistol scale
(~16 units long); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

C = 8
PISTOL = {
    "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"translation": [1, 3, 0], "scale": [0.72, 0.72, 0.72]},
    "firstperson_lefthand": {"translation": [1, 3, 0], "scale": [0.72, 0.72, 0.72]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [1, 1, 1]},
}


def straight_grip(g, z_front, z_back, mat, panel=None, base=None, y_top=7.8, y_bot=2.4, strap=0.0):
    """Tall unrotated grip buried in the frame; an optional thin back strap hints at the rake."""
    g.box((C - 0.6, y_bot, z_front), (C + 0.6, y_top, z_back), mat, name='grip')
    if strap:
        g.box((C - 0.55, y_bot, z_back), (C + 0.55, (y_top + y_bot) / 2, z_back + strap), mat, name='grip_backstrap')
    if panel:
        g.box((C - 0.66, y_bot + 0.5, z_front + 0.25), (C + 0.66, y_top - 1.3, z_back - 0.25), panel, name='grip_panel')
    if base:
        g.box((C - 0.62, y_bot - 0.45, z_front - 0.1), (C + 0.62, y_bot + 0.05, z_back + (strap or 0) + 0.1), base, name='mag_base')


def square_guard(g, z0, z1, mat, y0=5.6):
    g.box((C - 0.2, y0, z0), (C + 0.2, y0 + 0.35, z1), mat, name='guard_bottom')
    g.box((C - 0.2, y0, z0), (C + 0.2, y0 + 1.7, z0 + 0.35), mat, name='guard_front')
    g.box((C - 0.08, y0 + 0.4, z0 + 1.4), (C + 0.08, y0 + 1.6, z0 + 1.7), 'steel', name='trigger')


def b23r():
    GR = GRIPS['b23r']
    g = Gun('b23r_v3', offset=(0, -1, 12))
    g.box((C - 0.55, 7.95, -12.2), (C + 0.55, 9.5, -2.0), 'gunmetal', name='slide')
    for z in (-11.0, -9.6, -8.2):
        g.box((C - 0.57, 8.45, z), (C + 0.57, 8.95, z + 0.8), 'rubber', name='slide_cut')
    for z in (-4.6, -4.0, -3.4, -2.8):
        g.box((C - 0.58, 8.1, z), (C + 0.58, 9.4, z + 0.3), 'rubber', name='serration')
    g.box((C - 0.5, 8.05, -13.4), (C + 0.5, 9.35, -12.2), 'steel', name='compensator')
    g.cyl('z', (C, 8.7), 0.2, -13.45, -13.3, 'rubber', seg=8, name='bore')
    g.box((C - 0.5, 7.3, -11.6), (C + 0.5, 7.95, -1.8), 'darkmetal', name='frame')
    g.box((C - 0.45, 6.4, -11.6), (C + 0.45, 7.3, -8.0), 'darkmetal', name='rail')
    for z in (-11.2, -10.4, -9.6, -8.8):
        g.box((C - 0.47, 6.35, z), (C + 0.47, 6.55, z + 0.4), 'darkmetal', name='rail_slot')
    g.box((C - 0.08, 9.5, -11.8), (C + 0.08, 9.8, -11.3), 'darkmetal', name='front_sight')
    g.box((C - 0.3, 9.5, -2.6), (C + 0.3, 9.85, -2.0), 'darkmetal', name='rear_sight')
    square_guard(g, -7.7, -4.3, 'darkmetal', y0=5.85)
    straight_grip(g, GR[0], GR[1], 'polymer_dark', panel='grip_ribs_black', base='gunmetal', y_bot=GR[2], strap=GR[3])
    g.box((C - 0.45, 7.4, -2.2), (C + 0.45, 7.95, -0.9), 'darkmetal', name='beavertail')
    # details
    for side in (-1, 1):
        g.box((C + min(side * 0.55, side * 0.5800000000000001), 8.3, -6.8), (C + max(side * 0.55, side * 0.5800000000000001), 9.1, -5.2), 'rubber', name='ejection_port')
    g.box((C - 0.25, 9.33, -13.2), (C + 0.25, 9.38, -12.4), 'rubber', name='comp_port_top')
    for side in (-1, 1):
        g.box((C + min(side * 0.5, side * 0.53), 8.4, -13.1), (C + max(side * 0.5, side * 0.53), 9.0, -12.5), 'rubber', name='comp_port_side')
    g.box((C - 0.53, 7.55, -7.0), (C + 0.53, 8.05, -5.4), 'steel', name='slide_stop')
    for side in (-1, 1):
        g.cyl('x', (6.6, -3.9), 0.18, C + side * 0.6 if side > 0 else C - 0.72, C + 0.72 if side > 0 else C - 0.6, 'steel', seg=8, name='mag_release')
    g.cyl('x', (5.2, -3.05), 0.12, C - 0.68, C + 0.68, 'steel', seg=8, name='grip_screw')
    g.box((C - 0.12, 9.0, -1.95), (C + 0.12, 9.75, -1.45), 'steel', name='hammer_spur')
    for z in (-5.5, -5.0, -4.5):
        g.box((C - 0.3, 9.45, z), (C + 0.3, 9.56, z + 0.25), 'rubber', name='top_vent')
    for side in (-1, 1):
        g.box((C + min(side * 0.5, side * 0.54), 8.0, -12.6), (C + max(side * 0.5, side * 0.54), 9.2, -12.45), 'steel', name='comp_ridge')
    g.cyl('x', (7.6, -9.5), 0.14, C - 0.52, C + 0.52, 'steel', seg=8, name='takedown_pin')
    g.box((C - 0.5, 2.6, -4.0), (C + 0.5, 2.66, -1.4), 'steel', name='mag_pad')
    g.box((C - 0.04, 9.55, -11.84), (C + 0.04, 9.7, -11.78), 'gold', name='sight_dot')
    g.save(PISTOL)


GRIPS = {'b23r': (-4.2, -1.9, 3.1, 0.5), 'five_seven': (-4.9, -2.7, 3.1, 0.5), 'kap40': (-4.3, -1.5, 4.2, 0.3), 'executioner': (-1.9, 0.5, 3.4, 0.6)}


def executioner():
    GR = GRIPS['executioner']
    g = Gun('executioner_v3', offset=(0, -1, 12))
    g.box((C - 0.36, 8.75, -11.4), (C + 0.36, 9.4, -6.2), 'gunmetal', name='barrel')
    g.box((C - 0.1, 9.4, -11.4), (C + 0.1, 9.6, -6.2), 'darkmetal', name='top_rib')
    g.box((C - 0.08, 9.6, -11.3), (C + 0.08, 9.9, -10.8), 'darkmetal', name='front_sight')
    g.box((C - 0.3, 7.9, -11.0), (C + 0.3, 8.7, -6.2), 'gunmetal', name='ejector_shroud')
    g.box((C - 0.4, 7.4, -7.6), (C + 0.4, 8.0, -6.2), 'darkmetal', name='frame_front')
    g.cyl('z', (C, 9.1), 0.28, -11.45, -11.35, 'rubber', seg=8, name='bore')
    g.cyl('z', (C, 8.35), 1.25, -6.2, -2.6, 'chrome', seg=16, name='cylinder')
    for z in (-5.2, -3.6):
        g.cyl('z', (C, 8.35), 1.28, z, z + 0.35, 'steel', seg=16, name='cylinder_flute')
    g.box((C - 0.55, 6.8, -2.8), (C + 0.55, 8.6, -0.4), 'darkmetal', name='frame_rear')
    g.box((C - 0.5, 6.2, -5.6), (C + 0.5, 7.4, -2.0), 'darkmetal', name='frame_bottom')
    g.box((C - 0.18, 8.6, -2.0), (C + 0.18, 9.0, -1.4), 'steel', name='hammer')
    g.wire([(C, 7.0, -4.6), (C, 6.0, -4.4), (C, 5.6, -3.4), (C, 5.9, -2.2), (C, 6.8, -1.8)], 0.28, 'darkmetal', name='trigger_guard')
    g.box((C - 0.08, 6.1, -3.6), (C + 0.08, 7.0, -3.3), 'steel', name='trigger')
    GRIP = ('x', -22.5, (C, 7.8, GR[0] + 0.6))
    g.box((C - 0.7, GR[2] - 0.4, GR[0] + 0.6), (C + 0.7, 8.3, GR[1] + 0.4), 'grip_ribs_dark', rot=GRIP, name='grip')
    g.box((C - 0.62, GR[2] + 0.6, GR[0] + 0.05), (C + 0.62, 7.6, GR[0] + 0.75), 'rubber', rot=GRIP, name='grip_front_strap')
    g.box((C - 0.6, 6.4, -2.4), (C + 0.6, 7.2, -0.4), 'darkmetal', name='grip_mount')
    g.cyl('x', (5.2, (GR[0] + GR[1]) / 2), 0.35, C - 0.74, C + 0.74, 'fez_red', seg=8, name='grip_medallion')
    # details
    g.cyl('z', (C, 7.75), 0.15, -10.8, -6.2, 'steel', seg=8, name='ejector_rod')
    g.ring('z', (C, 9.1), 0.28, 0.4, -11.55, -11.35, 'steel', name='muzzle_crown')
    g.box((C - 0.62, 7.4, -3.0), (C + 0.62, 7.9, -2.0), 'steel', name='cylinder_latch')
    g.cyl('x', (7.6, -1.2), 0.13, C - 0.63, C + 0.63, 'steel', seg=8, name='frame_screw')
    g.box((C - 0.3, 8.6, -1.2), (C + 0.3, 8.85, -0.5), 'darkmetal', name='rear_sight')
    g.box((C - 0.04, 9.9, -11.2), (C + 0.04, 9.97, -10.9), 'fez_red', name='sight_insert')
    for side in (-1, 1):
        g.box((C + min(side * 0.36, side * 0.4), 8.95, -10.5), (C + max(side * 0.36, side * 0.4), 9.25, -8.0), 'rubber', name='barrel_slot')
    g.cyl('x', (7.7, -6.9), 0.12, C - 0.43, C + 0.43, 'steel', seg=8, name='frame_pin')
    g.box((C - 0.25, 7.1, -6.6), (C + 0.25, 7.5, -5.4), 'steel', name='crane_yoke')
    for k in range(5):
        import math
        a = math.radians(72 * k)
        g.cyl('z', (C + 0.85 * math.sin(a), 8.35 + 0.85 * math.cos(a)), 0.14, -6.28, -6.19, 'rubber', seg=8, name='chamber_mouth')
    g.box((C - 0.15, 8.4, -0.4), (C + 0.15, 8.9, -0.2), 'darkmetal', name='backstrap_stud')
    g.save(PISTOL)


def five_seven():
    GR = GRIPS['five_seven']
    g = Gun('five_seven_v3', offset=(0, -1, 12))
    g.box((C - 0.55, 8.0, -12.0), (C + 0.55, 9.55, -2.0), 'darkmetal', name='slide')
    for z in (-11.4, -10.9, -10.4, -4.4, -3.9, -3.4, -2.9):
        g.box((C - 0.57, 8.15, z), (C + 0.57, 9.45, z + 0.25), 'rubber', name='serration')
    g.cyl('z', (C, 8.775), 0.2, -12.05, -11.9, 'rubber', seg=8, name='bore')
    g.box((C - 0.5, 7.25, -11.6), (C + 0.5, 8.0, -1.8), 'polymer_olive', name='frame')
    g.box((C - 0.45, 7.05, -11.4), (C + 0.45, 7.25, -8.6), 'darkmetal', name='rail')
    g.box((C - 0.08, 9.55, -11.6), (C + 0.08, 9.85, -11.1), 'darkmetal', name='front_sight')
    g.box((C - 0.3, 9.55, -2.6), (C + 0.3, 9.9, -2.0), 'darkmetal', name='rear_sight')
    square_guard(g, -7.9, -4.8, 'polymer_olive', y0=6.1)
    straight_grip(g, GR[0], GR[1], 'polymer_olive', panel='grip_ribs_dark', base='darkmetal', y_bot=GR[2], strap=GR[3])
    # details
    for side in (-1, 1):
        g.box((C + min(side * 0.55, side * 0.5800000000000001), 8.3, -7.4), (C + max(side * 0.55, side * 0.5800000000000001), 9.2, -5.6), 'rubber', name='ejection_port')
    g.box((C - 0.53, 7.55, -7.0), (C + 0.53, 8.05, -5.4), 'steel', name='slide_stop')
    for side in (-1, 1):
        g.cyl('x', (6.6, -4.6), 0.17, C + side * 0.6 if side > 0 else C - 0.72, C + 0.72 if side > 0 else C - 0.6, 'steel', seg=8, name='mag_release')
    for z in (-11.0, -10.4, -9.8, -9.2):
        g.box((C - 0.47, 7.05, z), (C + 0.47, 7.25, z + 0.3), 'rubber', name='rail_slot')
    g.box((C - 0.1, 9.55, -6.0), (C + 0.1, 9.62, -5.2), 'gold', name='chamber_indicator')
    g.ring('z', (C, 8.775), 0.2, 0.36, -12.12, -12.0, 'steel', name='muzzle_crown')
    g.cyl('x', (7.75, -8.6), 0.1, C - 0.52, C + 0.52, 'fez_red', seg=8, name='frame_marker')
    for side in (-1, 1):
        g.box((C + min(side * 0.5, side * 0.54), 7.55, -3.2), (C + max(side * 0.5, side * 0.54), 7.9, -2.4), 'steel', name='safety_lever')
    g.box((C - 0.5, 2.6, -4.9), (C + 0.5, 2.66, -2.2), 'steel', name='mag_pad')
    g.box((C - 0.45, 9.5, -12.0), (C + 0.45, 9.6, -11.7), 'gunmetal', name='muzzle_cap')
    g.box((C - 0.5, 7.4, -9.0), (C + 0.5, 7.5, -8.2), 'steel', name='rail_stop')
    g.save(PISTOL)


def kap40():
    GR = GRIPS['kap40']
    g = Gun('kap40_v3', offset=(0, -1, 12))
    g.box((C - 0.75, 6.5, -11.6), (C + 0.75, 10.0, -6.2), 'polymer_tan', name='front_housing')
    g.box((C - 0.75, 7.6, -6.2), (C + 0.75, 10.0, -0.6), 'polymer_tan', name='upper_housing')
    g.box((C - 0.77, 6.9, -10.8), (C + 0.77, 9.6, -6.6), 'darkmetal', name='side_panel')
    g.box((C - 0.77, 9.6, -7.0), (C + 0.77, 9.9, -1.6), 'darkmetal', name='top_rail')
    g.box((C - 0.7, 5.9, -11.6), (C + 0.7, 6.5, -6.6), 'darkmetal', name='front_rail')
    for z in (-11.2, -10.2, -9.2, -8.2, -7.2):
        g.box((C - 0.72, 5.85, z), (C + 0.72, 6.05, z + 0.4), 'rubber', name='rail_slot')
    g.cyl('z', (C, 8.8), 0.22, -11.65, -11.5, 'rubber', seg=8, name='bore')
    g.box((C - 0.3, 10.0, -1.4), (C + 0.3, 10.4, -0.8), 'darkmetal', name='rear_sight')
    g.box((C - 0.5, 6.5, -6.6), (C + 0.5, 7.7, -4.4), 'polymer_dark', name='frame')
    square_guard(g, -6.6, -3.8, 'polymer_dark', y0=6.2)
    straight_grip(g, GR[0], GR[1], 'polymer_dark', panel='grip_ribs_black', y_bot=GR[2], strap=GR[3])
    g.box((C - 0.5, 3.0, -4.1), (C + 0.5, 4.3, -1.4), 'darkmetal', name='extended_mag')
    g.box((C - 0.55, 2.8, -4.2), (C + 0.55, 3.1, -1.3), 'polymer_tan', name='mag_base')
    # details
    for side in (-1, 1):
        g.box((C + min(side * 0.75, side * 0.78), 8.4, -5.4), (C + max(side * 0.75, side * 0.78), 9.4, -3.4), 'rubber', name='ejection_port')
    g.box((C - 0.78, 7.1, -5.8), (C + 0.78, 7.7, -4.4), 'steel', name='slide_stop')
    for z in (-6.5, -5.95, -5.4, -4.85, -4.3):
        g.box((C - 0.78, 9.65, z), (C + 0.78, 9.85, z + 0.25), 'rubber', name='rail_notch')
    for side in (-1, 1):
        g.cyl('x', (6.9, -3.9), 0.17, C + side * 0.6 if side > 0 else C - 0.72, C + 0.72 if side > 0 else C - 0.6, 'steel', seg=8, name='mag_release')
    g.ring('z', (C, 8.8), 0.22, 0.45, -11.8, -11.55, 'steel', name='muzzle_crown')
    g.cyl('x', (5.6, -2.9), 0.12, C - 0.68, C + 0.68, 'steel', seg=8, name='grip_screw')
    for z, m in ((-10.4, 'steel'), (-9.4, 'gold'), (-8.4, 'steel'), (-7.5, 'fez_red')):
        g.cyl('x', (6.9, z), 0.12, C - 0.8, C + 0.8, m, seg=8, name='status_stud')
    g.cyl('x', (8.9, -3.0), 0.28, C - 0.82, C + 0.82, 'steel', seg=8, name='selector_dial')
    g.box((C - 0.5, 2.62, -4.1), (C + 0.5, 2.82, -1.4), 'rubber', name='mag_pad')
    g.box((C - 0.5, 9.7, -11.0), (C + 0.5, 9.95, -10.4), 'gunmetal', name='front_rail_block')
    g.cyl('x', (7.2, -11.0), 0.13, C - 0.78, C + 0.78, 'steel', seg=8, name='front_pin')
    g.box((C - 0.6, 6.2, -1.0), (C + 0.6, 7.7, -0.55), 'darkmetal', name='rear_plate')
    for z in (-10.2, -9.7, -9.2):
        g.box((C - 0.79, 9.2, z), (C + 0.79, 9.5, z + 0.2), 'steel', name='housing_rib')
    g.save(PISTOL)


if __name__ == '__main__':
    for f in (b23r, executioner, five_seven, kap40):
        f()
