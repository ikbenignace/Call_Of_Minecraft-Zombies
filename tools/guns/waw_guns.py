#!/usr/bin/env python3
"""World at War wall-buy guns for the Nacht / Verrückt / Shi No Numa arenas: Thompson, Kar98k, STG-44,
Gewehr 43, Trench Gun, BAR, M1 Garand, Type 100, FG42, Arisaka, M1 Carbine, Double-Barreled and Sawed-Off
shotguns. Roughly one model unit per inch; muzzle towards -Z.

    python3 tools/guns/waw_guns.py            # build all
    python3 tools/guns/waw_guns.py kar98k bar # build some
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

C = 8
T22 = math.tan(math.radians(22.5))
S22 = math.sin(math.radians(22.5))


def box(g, w, y0, y1, z0, z1, mat, name=None, rot=None, x=0.0):
    g.box((C + x - w, y0, z0), (C + x + w, y1, z1), mat, rot=rot, name=name)


def side(g, w, y0, y1, z0, z1, mat, name, d=0.08, s=1):
    x0, x1 = sorted((C + s * (w - 0.03), C + s * (w + d)))
    g.box((x0, y0, z0), (x1, y1, z1), mat, name=name)


def pin(g, w, y, z, mat='steel', r=0.12):
    g.cyl('x', (y, z), r, C - w - 0.05, C + w + 0.05, mat, seg=8, name='pin')


def swivel(g, y, z):
    g.ring('x', (y, z), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='swivel')


def guard(g, y_frame, z0, z1, trigger_z=None):
    box(g, 0.22, y_frame - 0.9, y_frame - 0.62, z0, z1, 'darkmetal', name='guard_bottom')
    box(g, 0.22, y_frame - 0.9, y_frame + 0.02, z0, z0 + 0.3, 'darkmetal', name='guard_front')
    box(g, 0.22, y_frame - 0.9, y_frame + 0.02, z1 - 0.3, z1, 'darkmetal', name='guard_rear')
    tz = trigger_z if trigger_z is not None else z0 + (z1 - z0) * 0.4
    box(g, 0.08, y_frame - 0.62, y_frame + 0.02, tz, tz + 0.3, 'steel', name='trigger')


def stock(g, w, top, yb, y_heel, z_wrist, z_slope, z_butt, mat, plate='darkmetal', comb=None):
    """Wrist with a flat belly at yb, a 22.5 degree belly line falling to the heel at y_heel, butt to
    z_butt and a butt plate. comb lowers the top line of the butt (Mauser-style drop) when given."""
    drop = yb - y_heel
    z_end = z_slope + drop / T22
    steps = 4
    box(g, w * 0.9, yb, yb + drop / steps + 0.5, z_slope, z_slope + drop / S22, mat, name='stock_belly',
        rot=('x', 22.5, (C, yb, z_slope)))
    cuts = [z_slope + (z_end - z_slope) * i / steps for i in range(1, steps)] + [z_end + 0.2]
    box(g, w * 0.94, yb, top, z_wrist, cuts[0], mat, name='stock_wrist')
    for i in range(1, steps):
        box(g, w * 0.94, yb - drop * i / steps, top, cuts[i - 1], cuts[i], mat, name='stock_fill')
    box(g, w, y_heel, comb if comb else top, z_end, z_butt, mat, name='stock_butt')
    box(g, w + 0.03, y_heel, comb if comb else top, z_butt, z_butt + 0.35, plate, name='butt_plate')
    return z_end


def display(scale):
    return {
        "thirdperson_righthand": {"translation": [0, 3, -2.5], "scale": [scale] * 3},
        "thirdperson_lefthand": {"translation": [0, 3, -2.5], "scale": [scale] * 3},
        "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [round(scale * 1.25, 3)] * 3},
        "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [round(scale * 1.25, 3)] * 3},
        "ground": {"translation": [0, 2, 0], "scale": [round(scale * 0.8, 3)] * 3},
        "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [scale] * 3},
        "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
        "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
    }


def finish(g):
    """Centre the gun like the approved models (z around 7, y around 5) and size the display by length."""
    ys = [v for e in g.elements for v in (e['from'][1], e['to'][1])]
    zs = [v for e in g.elements for v in (e['from'][2], e['to'][2])]
    g.offset = (0, round(5 - (min(ys) + max(ys)) / 2, 2), round(7 - (min(zs) + max(zs)) / 2, 2))
    length = max(zs) - min(zs)
    g.save(display(round(min(0.62, 18.5 / length), 2)))


# ---------------------------------------------------------------------------------------------- SMGs

def thompson():
    """M1928A1 as in WaW: Cutts compensator, finned barrel, vertical foregrip, 20-round stick magazine,
    top cocking knob, Lyman rear sight, walnut pistol grip and stock."""
    g = Gun('thompson')
    W = 'wood_light'
    box(g, 0.6, 8.4, 9.8, -15.0, -13.0, 'darkmetal', name='compensator')
    for z in (-14.6, -14.0, -13.4):
        box(g, 0.4, 9.75, 9.88, z, z + 0.3, 'rubber', name='comp_slot')
    g.cyl('z', (C, 9.1), 0.3, -15.05, -14.95, 'rubber', name='bore')
    box(g, 0.1, 9.8, 10.5, -14.3, -13.7, 'darkmetal', name='front_sight')
    g.cyl('z', (C, 9.1), 0.4, -13.0, -2.4, 'darkmetal', name='barrel')
    for i in range(10):
        z = -10.4 + i * 0.75
        g.cyl('z', (C, 9.1), 0.68, z, z + 0.35, 'darkmetal', name='barrel_fin')
    box(g, 0.42, 7.8, 8.8, -12.7, -11.1, 'darkmetal', name='foregrip_mount')
    box(g, 0.62, 4.6, 7.9, -12.9, -10.9, W, name='foregrip')
    for y in (5.3, 6.1, 6.9):
        box(g, 0.64, y, y + 0.25, -12.95, -12.6, 'wood', name='finger_groove')
    box(g, 0.66, 4.35, 4.65, -13.0, -10.8, 'darkmetal', name='foregrip_cap')

    box(g, 0.75, 7.6, 10.2, -2.5, 7.5, 'darkmetal', name='receiver')
    box(g, 0.55, 10.2, 10.5, -2.3, 7.3, 'darkmetal', name='receiver_top')
    box(g, 0.12, 10.48, 10.56, -1.6, 2.4, 'rubber', name='knob_slot')
    box(g, 0.28, 10.5, 11.05, 0.4, 1.4, 'steel', name='cocking_knob')
    box(g, 0.42, 10.5, 11.3, 5.2, 6.5, 'darkmetal', name='rear_sight')
    box(g, 0.32, 11.3, 11.6, 5.4, 5.8, 'darkmetal', name='rear_sight_leaf')
    side(g, 0.75, 8.6, 9.6, 1.8, 4.4, 'rubber', 'ejection_port')
    side(g, 0.75, 7.75, 8.15, 4.0, 5.0, 'steel', 'selector', s=-1)
    side(g, 0.75, 7.75, 8.15, 2.2, 3.2, 'steel', 'safety', s=-1)

    box(g, 0.7, 6.2, 7.6, -1.0, 7.5, 'darkmetal', name='frame')
    box(g, 0.78, 5.6, 7.6, -1.2, 1.8, 'darkmetal', name='magwell')
    box(g, 0.55, 0.8, 5.7, -0.8, 1.4, 'darkmetal', name='magazine')
    guard(g, 6.2, 2.3, 5.2)
    g.box((C - 0.64, 2.3, 5.3), (C + 0.64, 6.3, 7.5), W, rot=('x', -22.5, (C, 6.3, 6.4)), name='pistol_grip')

    z_end = stock(g, 0.72, 9.3, 6.3, 4.2, 7.5, 9.0, 18.0, W)
    swivel(g, 4.0, z_end + 2.0)
    pin(g, 0.75, 8.3, -1.6)
    pin(g, 0.75, 8.3, 6.6)
    finish(g)


def type100():
    """Type 100: perforated barrel jacket with bayonet lug, curved magazine feeding from the left, full
    one-piece wooden stock with a long forend."""
    g = Gun('type100')
    W = 'wood_tan'
    g.cyl('z', (C, 9.0), 0.3, -15.05, -14.95, 'rubber', name='bore')
    g.cyl('z', (C, 9.0), 0.45, -15.0, -13.4, 'darkmetal', name='muzzle')
    g.cyl('z', (C, 9.0), 0.66, -13.6, -3.0, 'darkmetal', name='barrel_jacket')
    for z in (-12.6, -11.4, -10.2, -9.0, -7.8):
        for y in (8.75, 9.25):
            box(g, 0.68, y - 0.15, y + 0.15, z, z + 0.6, 'rubber', name='jacket_hole')
        box(g, 0.25, 9.66, 9.72, z, z + 0.6, 'rubber', name='jacket_hole')
    box(g, 0.1, 9.6, 10.4, -13.4, -12.8, 'darkmetal', name='front_sight')
    box(g, 0.3, 9.5, 9.8, -13.6, -12.6, 'darkmetal', name='front_sight_base')
    box(g, 0.18, 7.8, 8.4, -13.4, -11.6, 'steel', name='bayonet_lug')
    g.cyl('z', (C, 9.0), 0.72, -3.2, 8.5, 'darkmetal', name='receiver')
    box(g, 0.3, 9.6, 10.3, 5.0, 6.6, 'darkmetal', name='rear_sight')
    box(g, 0.08, 9.4, 9.8, -2.0, 5.0, 'rubber', name='bolt_slot', x=0.7)
    box(g, 0.35, 8.8, 9.3, 3.0, 3.6, 'steel', name='cocking_handle', x=1.0)
    # Magazine from the left, curving down towards the muzzle
    box(g, 0.75, 8.2, 9.8, -2.2, 0.0, 'darkmetal', name='magwell', x=-1.2)
    g.box((C - 5.6, 8.35, -1.9), (C - 1.9, 9.65, -0.3), 'darkmetal', name='magazine')
    g.box((C - 5.65, 8.3, -1.95), (C - 5.35, 9.7, -0.25), 'gunmetal', name='mag_baseplate')

    box(g, 0.66, 7.0, 8.5, -11.0, 8.5, W, name='forend')
    box(g, 0.6, 6.6, 7.1, -9.0, 4.0, W, name='forend_belly')
    box(g, 0.68, 7.4, 9.7, -11.2, -10.6, 'steel', name='barrel_band')
    swivel(g, 6.6, -6.0)
    guard(g, 6.6, 3.6, 6.4)
    z_end = stock(g, 0.68, 9.6, 6.6, 4.0, 8.5, 10.0, 21.0, W)
    swivel(g, 3.8, z_end + 1.5)
    box(g, 0.55, 6.1, 6.7, 10.0, 12.2, W, name='grip_swell', rot=('x', -22.5, (C, 6.6, 10.0)))
    finish(g)


# ------------------------------------------------------------------------------------- bolt actions

def bolt_rifle(name, W, arisaka=False):
    """Mauser-pattern bolt-action. Kar98k: turned-down bolt, hooded front sight, single barrel band
    and nose cap. Arisaka Type 99: longer upper handguard, straight bolt with an oval knob, sliding dust
    cover, two barrel bands, monopod-free short forend tip."""
    g = Gun(name)
    BY = 9.0
    g.cyl('z', (C, BY), 0.25, -15.05, -14.95, 'rubber', name='bore')
    g.cyl('z', (C, BY), 0.36, -15.0, 3.0, 'darkmetal', name='barrel')
    if arisaka:
        box(g, 0.1, 9.3, 10.2, -14.6, -14.0, 'darkmetal', name='front_sight')
        box(g, 0.35, 9.25, 9.55, -14.8, -13.8, 'darkmetal', name='front_sight_base')
    else:
        box(g, 0.38, 9.3, 9.45, -14.8, -13.6, 'darkmetal', name='front_sight_base')
        for s in (-1, 1):
            box(g, 0.05, 9.45, 10.5, -14.7, -13.7, 'darkmetal', name='sight_hood', x=s * 0.34)
        box(g, 0.38, 10.4, 10.55, -14.7, -13.7, 'darkmetal', name='sight_hood')
        box(g, 0.08, 9.45, 10.1, -14.4, -14.0, 'darkmetal', name='front_sight')
    nose = -12.8 if not arisaka else -13.2
    box(g, 0.55, 7.7, 9.45, nose - 0.9, nose, 'steel', name='nose_cap')
    box(g, 0.16, 7.2, 7.7, nose - 0.8, nose - 0.1, 'steel', name='bayonet_lug')
    g.cyl('z', (C, 8.0), 0.13, -14.4, nose - 0.9, 'steel', name='cleaning_rod')
    box(g, 0.58, 7.5, 8.9, nose, 4.0, W, name='forend')
    box(g, 0.48, 6.9, 7.6, nose + 1.0, 3.0, W, name='forend_belly')
    hg0 = -11.2 if arisaka else -6.0
    box(g, 0.44, 8.9, 9.45, hg0, 2.0, W, name='handguard')
    for z in ((-9.0, -4.5) if arisaka else (-7.0,)):
        box(g, 0.62, 7.4, 9.55, z, z + 0.45, 'steel', name='barrel_band')
    swivel(g, 7.2, -7.0 if not arisaka else -4.3)
    box(g, 0.4, 9.0, 9.9, 1.6, 3.8, 'darkmetal', name='rear_sight_base')
    box(g, 0.3, 9.9, 10.15, 2.2, 3.8, 'darkmetal', name='rear_sight_leaf')

    g.cyl('z', (C, 9.25), 0.62, 3.6, 12.4, 'darkmetal', name='receiver_ring')
    box(g, 0.6, 8.6, 9.5, 3.8, 12.4, 'darkmetal', name='receiver')
    g.cyl('z', (C, 9.45), 0.42, 8.8, 13.6, 'steel', name='bolt_body')
    box(g, 0.38, 9.05, 10.0, 13.4, 14.6, 'steel', name='bolt_shroud')
    box(g, 0.1, 10.0, 10.5, 13.8, 14.3, 'steel', name='safety_flag')
    if arisaka:
        box(g, 0.66, 9.45, 10.05, 8.4, 11.4, 'darkmetal', name='dust_cover')
        g.box((C + 0.4, 9.15, 12.3), (C + 2.0, 9.5, 12.7), 'steel', name='bolt_handle')
        g.box((C + 1.9, 8.9, 12.05), (C + 2.5, 9.75, 12.95), 'steel', name='bolt_knob')
    else:
        g.box((C + 0.4, 9.05, 12.1), (C + 1.5, 9.45, 12.5), 'steel', name='bolt_handle')
        g.box((C + 1.3, 8.3, 12.15), (C + 1.6, 9.2, 12.45), 'steel', name='bolt_stem', rot=('x', 22.5, (C + 1.45, 9.2, 12.3)))
        g.cyl('x', (8.15, 12.75), 0.42, C + 1.1, C + 1.95, 'steel', name='bolt_knob')

    box(g, 0.66, 6.6, 8.7, 3.8, 12.5, W, name='stock_action')
    box(g, 0.5, 6.25, 6.62, 5.0, 9.0, 'darkmetal', name='floorplate')
    pin(g, 0.66, 7.7, 4.6, 'darkmetal')
    guard(g, 6.6, 9.4, 12.3)
    yb, heel = 6.4, 3.6
    z_end = stock(g, 0.66, 8.9, yb, heel, 12.5, 15.0, 28.6, W, comb=8.7)
    box(g, 0.5, 5.2, 6.5, 13.4, 15.4, W, name='grip_bump', rot=('x', -22.5, (C, 6.5, 13.4)))
    swivel(g, heel - 0.2, z_end + 3.0)
    if not arisaka:
        side(g, 0.66, 5.2, 6.4, 23.5, 25.6, 'steel', 'sling_slot_disc', s=-1)
        g.cyl('x', (5.8, 24.55), 0.55, C - 0.75, C - 0.6, 'steel', name='buttstock_disc')
    else:
        box(g, 0.68, 4.4, 8.7, 22.0, 22.15, 'wood', name='stock_joint')
    finish(g)


def kar98k():
    bolt_rifle('kar98k', 'wood_red')


def arisaka():
    bolt_rifle('arisaka', 'wood_honey', arisaka=True)


# -------------------------------------------------------------------------------------- semi-autos

def m1_garand():
    """M1 Garand: gas cylinder with the front sight on top at the muzzle, two-piece upper handguard,
    operating rod handle on the right, aperture rear sight with windage knobs, en-bloc fed, walnut stock."""
    g = Gun('m1_garand')
    W = 'wood_red'
    BY = 9.0
    g.cyl('z', (C, BY), 0.3, -15.05, -14.95, 'rubber', name='bore')
    g.cyl('z', (C, BY), 0.38, -15.0, 1.0, 'darkmetal', name='barrel')
    g.cyl('z', (C, BY - 0.55), 0.55, -14.4, -11.4, 'darkmetal', name='gas_cylinder')
    box(g, 0.48, BY - 0.2, BY + 0.85, -13.4, -12.4, 'darkmetal', name='front_sight_base')
    box(g, 0.08, BY + 0.85, BY + 1.5, -13.1, -12.7, 'darkmetal', name='front_sight')
    for s in (-1, 1):
        box(g, 0.06, BY + 0.85, BY + 1.45, -13.2, -12.6, 'darkmetal', name='sight_ear', x=s * 0.38)
    box(g, 0.2, BY - 0.6, BY - 0.25, -14.9, -14.4, 'steel', name='gas_plug')
    g.cyl('z', (C, BY - 0.55), 0.6, -11.4, -10.8, 'steel', name='front_band')
    box(g, 0.18, 7.6, 8.4, -11.4, -10.8, 'steel', name='bayonet_lug')

    box(g, 0.62, 6.8, 8.7, -10.8, 10.5, W, name='forend')
    box(g, 0.5, 6.4, 6.9, -9.0, 6.0, W, name='forend_belly')
    box(g, 0.5, 8.7, 9.55, -10.4, -2.8, W, name='handguard_front')
    box(g, 0.5, 8.7, 9.55, -2.2, 6.8, W, name='handguard_rear')
    box(g, 0.56, 8.5, 9.65, -2.8, -2.2, 'steel', name='lower_band')
    swivel(g, 6.2, -3.0)
    side(g, 0.62, 8.3, 8.75, -10.8, 9.0, 'darkmetal', 'op_rod', d=0.12)
    g.box((C + 0.6, 8.25, 8.6), (C + 1.3, 8.95, 9.4), 'steel', name='op_rod_handle')

    box(g, 0.66, 8.2, 10.0, 6.8, 13.4, 'darkmetal', name='receiver')
    box(g, 0.5, 10.0, 10.25, 7.4, 10.8, 'steel', name='bolt')
    box(g, 0.08, 9.3, 9.95, 9.0, 10.6, 'rubber', name='clip_slot', x=0.0)
    box(g, 0.5, 10.0, 10.9, 11.8, 13.2, 'darkmetal', name='rear_sight')
    g.cyl('x', (10.5, 12.5), 0.3, C - 0.85, C + 0.85, 'steel', name='sight_knob')
    box(g, 0.1, 10.9, 11.15, 12.2, 12.9, 'darkmetal', name='aperture')

    box(g, 0.66, 6.3, 8.3, 10.5, 14.0, W, name='stock_action')
    box(g, 0.5, 5.9, 6.35, 8.6, 11.8, 'darkmetal', name='trigger_housing')
    guard(g, 6.3, 10.9, 13.8)
    z_end = stock(g, 0.7, 9.2, 6.3, 3.9, 13.6, 15.4, 28.4, W)
    box(g, 0.56, 5.0, 6.4, 13.8, 16.0, W, name='grip_bump', rot=('x', -22.5, (C, 6.4, 13.8)))
    side(g, 0.7, 6.0, 7.2, z_end + 3.0, z_end + 4.0, 'steel', 'oiler_trap', d=0.05)
    swivel(g, 3.7, z_end + 2.0)
    pin(g, 0.66, 7.6, 11.2)
    finish(g)


def gewehr43():
    """Gewehr 43: short upper handguard, gas piston tube above the barrel, hooded front sight,
    detachable 10-round magazine, charging handle on the left, walnut stock."""
    g = Gun('gewehr43')
    W = 'wood'
    BY = 9.0
    g.cyl('z', (C, BY), 0.3, -15.05, -14.95, 'rubber', name='bore')
    g.cyl('z', (C, BY), 0.38, -15.0, 2.0, 'darkmetal', name='barrel')
    box(g, 0.35, 9.3, 9.45, -14.7, -13.6, 'darkmetal', name='front_sight_base')
    for s in (-1, 1):
        box(g, 0.05, 9.45, 10.4, -14.6, -13.7, 'darkmetal', name='sight_hood', x=s * 0.32)
    box(g, 0.35, 10.3, 10.45, -14.6, -13.7, 'darkmetal', name='sight_hood')
    box(g, 0.08, 9.45, 10.0, -14.3, -13.9, 'darkmetal', name='front_sight')
    box(g, 0.5, 8.4, 10.1, -9.8, -8.6, 'darkmetal', name='gas_block')
    g.cyl('z', (C, 9.85), 0.3, -9.0, 4.0, 'darkmetal', name='gas_tube')
    box(g, 0.55, 7.7, 9.5, -12.3, -11.3, 'steel', name='nose_cap')
    box(g, 0.58, 7.4, 8.9, -11.3, 4.0, W, name='forend')
    box(g, 0.48, 6.9, 7.5, -10.0, 3.0, W, name='forend_belly')
    box(g, 0.44, 8.9, 9.6, -6.0, 1.6, W, name='handguard')
    box(g, 0.62, 7.3, 10.25, -6.5, -6.0, 'steel', name='barrel_band')
    swivel(g, 7.1, -6.25)
    box(g, 0.42, 9.6, 10.6, 1.6, 4.0, 'darkmetal', name='rear_sight')

    box(g, 0.64, 8.2, 10.1, 4.0, 13.0, 'darkmetal', name='receiver')
    box(g, 0.5, 10.1, 10.35, 5.0, 12.4, 'steel', name='bolt_carrier')
    side(g, 0.64, 8.9, 9.7, 6.0, 9.0, 'rubber', 'ejection_port')
    g.box((C - 1.5, 9.3, 7.0), (C - 0.6, 9.7, 7.5), 'steel', name='charging_handle')
    g.box((C - 1.8, 9.2, 6.9), (C - 1.4, 9.85, 7.6), 'steel', name='charging_knob')
    side(g, 0.64, 8.4, 9.3, 4.4, 12.4, 'darkmetal', 'scope_rail', d=0.18)

    box(g, 0.66, 6.6, 8.3, 4.0, 13.0, W, name='stock_action')
    box(g, 0.58, 3.6, 6.7, 5.6, 8.6, 'darkmetal', name='magazine')
    box(g, 0.62, 3.4, 3.65, 5.5, 8.7, 'gunmetal', name='mag_floorplate')
    guard(g, 6.6, 9.6, 12.6)
    z_end = stock(g, 0.68, 9.2, 6.4, 3.8, 13.0, 15.2, 28.4, W, comb=8.9)
    box(g, 0.5, 5.2, 6.5, 13.4, 15.4, W, name='grip_bump', rot=('x', -22.5, (C, 6.5, 13.4)))
    swivel(g, 3.6, z_end + 2.5)
    finish(g)


def m1_carbine():
    """M1 Carbine: short barrel with an eared front sight, barrel band with sling swivel, short upper
    handguard, 15-round magazine, slide with a handle on the right, flip rear sight, walnut stock."""
    g = Gun('m1_carbine')
    W = 'wood_light'
    BY = 9.0
    g.cyl('z', (C, BY), 0.26, -15.05, -14.95, 'rubber', name='bore')
    g.cyl('z', (C, BY), 0.36, -15.0, 0.0, 'darkmetal', name='barrel')
    box(g, 0.32, 9.25, 9.6, -14.7, -13.7, 'darkmetal', name='front_sight_base')
    box(g, 0.08, 9.6, 10.3, -14.4, -14.0, 'darkmetal', name='front_sight')
    for s in (-1, 1):
        box(g, 0.06, 9.6, 10.25, -14.6, -13.8, 'darkmetal', name='sight_ear', x=s * 0.28)
    box(g, 0.62, 7.4, 9.55, -9.6, -8.6, 'steel', name='barrel_band')
    swivel(g, 7.2, -9.1)
    box(g, 0.6, 7.4, 8.9, -9.6, 6.0, W, name='forend')
    box(g, 0.5, 6.9, 7.5, -8.6, 3.0, W, name='forend_belly')
    box(g, 0.45, 8.9, 9.55, -8.6, -1.0, W, name='handguard')
    box(g, 0.66, 8.2, 9.9, -1.0, 5.6, 'darkmetal', name='receiver')
    box(g, 0.5, 9.9, 10.1, -0.6, 3.4, 'steel', name='bolt')
    side(g, 0.66, 8.5, 8.95, -6.0, 2.4, 'darkmetal', 'slide', d=0.14)
    g.box((C + 0.66, 8.4, 1.8), (C + 1.3, 9.05, 2.5), 'steel', name='slide_handle')
    box(g, 0.32, 9.9, 10.7, 4.0, 5.4, 'darkmetal', name='rear_sight')
    box(g, 0.6, 6.6, 8.3, 0.0, 6.6, W, name='stock_action')
    box(g, 0.48, 6.0, 6.65, 0.4, 2.0, 'darkmetal', name='magwell')
    box(g, 0.46, 3.4, 6.1, 0.5, 1.9, 'darkmetal', name='magazine')
    box(g, 0.48, 6.0, 6.65, 2.0, 6.0, 'darkmetal', name='trigger_housing')
    guard(g, 6.0, 2.5, 5.0)
    side(g, 0.48, 6.1, 6.5, 2.0, 2.6, 'steel', 'mag_catch')
    z_end = stock(g, 0.62, 8.9, 6.4, 4.2, 6.6, 8.0, 21.0, W)
    box(g, 0.48, 5.2, 6.5, 6.8, 8.6, W, name='grip_bump', rot=('x', -22.5, (C, 6.5, 6.8)))
    side(g, 0.62, 5.4, 6.8, z_end + 2.0, z_end + 3.2, 'darkmetal', 'oiler_slot', d=0.04, s=-1)
    swivel(g, 4.0, z_end + 1.0)
    finish(g)


# ------------------------------------------------------------------------------------ assault / LMG

def stg44():
    """StG 44: long barrel with front sight post, gas tube above it, stamped upper and lower receivers,
    curved 30-round magazine, ribbed bakelite pistol grip, tangent rear sight, fixed wooden butt."""
    g = Gun('stg44')
    BY = 8.9
    g.cyl('z', (C, BY), 0.26, -15.05, -14.95, 'rubber', name='bore')
    g.cyl('z', (C, BY), 0.38, -15.0, -3.6, 'darkmetal', name='barrel')
    g.ring('z', (C, BY), 0.38, 0.5, -14.8, -14.2, 'darkmetal', name='muzzle_nut')
    box(g, 0.36, 9.2, 9.9, -13.6, -12.6, 'darkmetal', name='front_sight_base')
    box(g, 0.08, 9.9, 10.6, -13.3, -12.9, 'darkmetal', name='front_sight')
    for s in (-1, 1):
        box(g, 0.05, 9.9, 10.5, -13.5, -12.7, 'darkmetal', name='sight_hood', x=s * 0.31)
    box(g, 0.5, 8.5, 10.6, -9.4, -8.4, 'darkmetal', name='gas_block')
    g.cyl('z', (C, 10.0), 0.55, -8.4, -1.0, 'darkmetal', name='gas_tube')
    for z in (-7.4, -6.2, -5.0, -3.8):
        box(g, 0.4, 10.5, 10.58, z, z + 0.5, 'rubber', name='gas_vent')

    box(g, 0.68, 8.2, 10.6, -3.6, 11.0, 'darkmetal', name='receiver')
    box(g, 0.5, 10.6, 10.85, -3.0, 10.6, 'darkmetal', name='receiver_top')
    box(g, 0.4, 10.85, 11.6, -1.2, 1.0, 'darkmetal', name='rear_sight')
    box(g, 0.32, 11.6, 11.8, -1.0, 0.4, 'darkmetal', name='sight_leaf')
    side(g, 0.68, 9.0, 10.0, 2.2, 5.4, 'rubber', 'ejection_port')
    g.box((C - 1.5, 9.8, -1.8), (C - 0.65, 10.2, -1.3), 'steel', name='cocking_handle')
    side(g, 0.68, 8.4, 8.8, -3.4, 3.0, 'gunmetal', 'stamped_rib', d=0.05, s=-1)
    side(g, 0.68, 8.4, 8.8, 5.4, 10.6, 'gunmetal', 'stamped_rib', d=0.05)

    box(g, 0.62, 6.8, 8.25, 1.0, 10.6, 'darkmetal', name='lower_frame')
    box(g, 0.7, 6.2, 8.25, -2.9, 0.6, 'darkmetal', name='magwell')
    box(g, 0.55, 3.6, 6.3, -2.6, 0.3, 'darkmetal', name='magazine')
    g.box((C - 0.55, 0.0, -2.6), (C + 0.55, 4.6, 0.3), 'darkmetal', name='magazine_lower',
          rot=('x', 22.5, (C, 3.7, -1.15)))
    g.box((C - 0.6, -0.3, -2.7), (C + 0.6, 0.05, 0.4), 'gunmetal', name='mag_baseplate',
          rot=('x', 22.5, (C, 3.7, -1.15)))
    guard(g, 6.8, 2.6, 5.4)
    g.box((C - 0.62, 2.8, 5.6), (C + 0.62, 6.9, 7.9), 'grip_ribs_dark', rot=('x', -22.5, (C, 6.9, 6.7)), name='pistol_grip')
    side(g, 0.62, 7.0, 7.5, 4.0, 5.0, 'steel', 'selector', s=-1)
    box(g, 0.58, 7.6, 10.4, 10.6, 12.0, 'darkmetal', name='stock_ferrule')
    z_end = stock(g, 0.66, 10.0, 7.6, 4.8, 12.0, 12.6, 22.4, 'wood')
    swivel(g, 4.6, z_end + 2.0)
    pin(g, 0.68, 9.0, 9.8)
    pin(g, 0.62, 7.4, 2.0)
    finish(g)


def bar():
    """M1918 BAR: flash hider, long barrel, gas cylinder with regulator below it, wooden handguard,
    long receiver with the 20-round box magazine ahead of the guard, rear aperture sight, straight
    wooden butt with steel plate."""
    g = Gun('bar')
    W = 'wood'
    BY = 9.2
    g.cyl('z', (C, BY), 0.3, -15.05, -14.95, 'rubber', name='bore')
    g.cyl('z', (C, BY), 0.55, -15.0, -12.8, 'darkmetal', name='flash_hider')
    for z in (-14.6, -13.8):
        box(g, 0.57, BY - 0.12, BY + 0.12, z, z + 0.5, 'rubber', name='hider_slot')
    box(g, 0.08, BY + 0.5, BY + 1.3, -12.6, -12.1, 'darkmetal', name='front_sight')
    box(g, 0.3, BY + 0.35, BY + 0.6, -12.8, -11.9, 'darkmetal', name='front_sight_base')
    g.cyl('z', (C, BY), 0.42, -12.8, 1.0, 'darkmetal', name='barrel')
    box(g, 0.5, 7.5, BY + 0.4, -11.0, -9.6, 'darkmetal', name='gas_regulator')
    g.cyl('z', (C, 8.1), 0.45, -9.6, 1.0, 'darkmetal', name='gas_cylinder')
    box(g, 0.75, 7.2, 9.7, -6.6, 0.8, W, name='handguard')
    for y in (7.9, 8.7):
        box(g, 0.77, y, y + 0.22, -6.2, 0.4, 'wood_maroon', name='handguard_groove')
    swivel(g, 7.4, -8.0)

    box(g, 0.82, 7.0, 10.2, 0.8, 13.6, 'darkmetal', name='receiver')
    box(g, 0.6, 10.2, 10.45, 1.2, 12.8, 'darkmetal', name='receiver_top')
    box(g, 0.5, 10.45, 11.5, 11.2, 12.8, 'darkmetal', name='rear_sight')
    box(g, 0.1, 11.5, 11.75, 11.7, 12.3, 'darkmetal', name='aperture')
    side(g, 0.82, 8.6, 9.6, 4.6, 8.2, 'rubber', 'ejection_port')
    side(g, 0.82, 7.6, 8.2, 1.4, 6.6, 'rubber', 'cocking_slot', s=-1)
    g.box((C - 1.6, 7.6, 2.4), (C - 0.8, 8.2, 3.1), 'steel', name='cocking_handle')
    side(g, 0.82, 7.3, 7.8, 9.6, 10.6, 'steel', 'selector', s=-1)
    box(g, 0.66, 3.4, 7.05, 3.4, 7.4, 'darkmetal', name='magazine')
    box(g, 0.7, 3.15, 3.45, 3.3, 7.5, 'gunmetal', name='mag_floorplate')
    box(g, 0.62, 6.2, 7.05, 7.6, 13.6, 'darkmetal', name='trigger_housing')
    guard(g, 6.2, 8.4, 11.4)
    z_end = stock(g, 0.74, 9.8, 6.3, 3.9, 13.6, 15.6, 29.8, W)
    box(g, 0.56, 5.0, 6.4, 14.4, 16.6, W, name='grip_bump', rot=('x', -22.5, (C, 6.4, 14.4)))
    swivel(g, 3.7, z_end + 2.5)
    pin(g, 0.82, 7.8, 12.6)
    finish(g)


def fg42():
    """FG42: muzzle brake, folded bipod under the barrel, wooden handguard, magazine feeding from the
    left side, steeply raked pistol grip, tall rear sight, wooden butt in line with the bore."""
    g = Gun('fg42')
    BY = 9.0
    g.cyl('z', (C, BY), 0.26, -15.05, -14.95, 'rubber', name='bore')
    g.cyl('z', (C, BY), 0.55, -15.0, -12.8, 'darkmetal', name='muzzle_brake')
    for z in (-14.6, -13.9, -13.2):
        box(g, 0.57, BY - 0.14, BY + 0.14, z, z + 0.4, 'rubber', name='brake_slot')
    g.cyl('z', (C, BY), 0.38, -12.8, -2.0, 'darkmetal', name='barrel')
    box(g, 0.08, BY + 0.4, BY + 1.6, -12.4, -11.9, 'darkmetal', name='front_sight')
    box(g, 0.3, BY + 0.3, BY + 0.6, -12.6, -11.6, 'darkmetal', name='front_sight_base')
    box(g, 0.55, 8.1, 9.5, -11.2, -10.2, 'steel', name='bipod_clamp')
    for s in (-1, 1):
        box(g, 0.12, 8.0, 8.3, -10.4, -3.0, 'steel', name='bipod_leg', x=s * 0.38)
        box(g, 0.2, 7.85, 8.35, -3.4, -2.6, 'steel', name='bipod_foot', x=s * 0.38)
    box(g, 0.7, 7.6, 9.6, -6.0, 0.0, 'wood', name='handguard')
    for y in (8.2, 8.9):
        box(g, 0.72, y, y + 0.22, -5.6, -0.4, 'wood_maroon', name='handguard_groove')

    box(g, 0.68, 7.6, 10.2, 0.0, 12.0, 'darkmetal', name='receiver')
    box(g, 0.5, 10.2, 10.45, 0.4, 11.6, 'darkmetal', name='receiver_top')
    box(g, 0.3, 10.45, 11.8, 9.6, 10.6, 'darkmetal', name='rear_sight')
    side(g, 0.68, 8.6, 9.6, 3.6, 6.4, 'rubber', 'ejection_port')
    g.box((C + 0.68, 8.0, 0.8), (C + 1.4, 8.4, 1.4), 'steel', name='cocking_handle')
    box(g, 0.7, 8.0, 9.8, 2.0, 4.4, 'darkmetal', name='magwell', x=-1.1)
    g.box((C - 5.4, 8.25, 2.3), (C - 1.8, 9.55, 4.1), 'darkmetal', name='magazine')
    g.box((C - 5.45, 8.2, 2.25), (C - 5.2, 9.6, 4.15), 'gunmetal', name='mag_baseplate')

    box(g, 0.6, 6.6, 7.65, 3.6, 10.8, 'darkmetal', name='trigger_housing')
    guard(g, 6.6, 4.6, 7.4)
    g.box((C - 0.62, 2.6, 7.2), (C + 0.62, 6.7, 9.4), 'wood', rot=('x', -45, (C, 6.7, 8.3)), name='pistol_grip')
    box(g, 0.6, 7.4, 9.9, 12.0, 13.4, 'darkmetal', name='stock_ferrule')
    box(g, 0.66, 6.0, 9.9, 13.4, 22.6, 'wood', name='stock_butt')
    box(g, 0.69, 6.0, 9.9, 22.6, 22.95, 'darkmetal', name='butt_plate')
    box(g, 0.5, 5.6, 6.1, 14.0, 22.6, 'wood', name='stock_belly')
    swivel(g, 5.4, 20.0)
    pin(g, 0.68, 8.0, 11.2)
    finish(g)


# ---------------------------------------------------------------------------------------- shotguns

def trench_gun():
    """Winchester M1897 trench gun: perforated heat shield and bayonet lug, tube magazine, ribbed
    wooden slide, receiver with exposed hammer, straight-grip walnut stock."""
    g = Gun('trench_gun')
    W = 'wood_tan'
    BY = 9.2
    g.cyl('z', (C, BY), 0.32, -15.05, -14.95, 'rubber', name='bore')
    g.cyl('z', (C, BY), 0.44, -15.0, 3.0, 'darkmetal', name='barrel')
    box(g, 0.62, BY - 0.3, BY + 0.8, -13.6, -1.6, 'darkmetal', name='heat_shield')
    for z in [-13.0 + i * 0.85 for i in range(13)]:
        box(g, 0.64, BY + 0.05, BY + 0.45, z, z + 0.4, 'rubber', name='shield_hole')
        box(g, 0.3, BY + 0.78, BY + 0.84, z, z + 0.4, 'rubber', name='shield_hole')
    box(g, 0.68, BY - 0.4, BY + 0.85, -14.2, -13.6, 'darkmetal', name='shield_clamp')
    box(g, 0.68, BY - 0.4, BY + 0.85, -1.6, -1.0, 'darkmetal', name='shield_clamp')
    box(g, 0.2, 7.2, 8.0, -14.6, -13.4, 'steel', name='bayonet_lug')
    box(g, 0.07, BY + 0.85, BY + 1.15, -14.0, -13.8, 'gold', name='bead')
    g.cyl('z', (C, 8.1), 0.4, -13.6, 3.0, 'darkmetal', name='mag_tube')
    g.cyl('z', (C, 8.1), 0.46, -14.0, -13.6, 'darkmetal', name='mag_cap')
    box(g, 0.85, 7.2, 8.85, -9.0, -3.0, 'wood_ribbed', name='pump')
    box(g, 0.75, 6.9, 7.3, -8.8, -3.2, 'wood_ribbed', name='pump_belly')
    side(g, 0.4, 7.8, 8.2, -3.0, 3.0, 'steel', 'action_bar', d=0.08)

    box(g, 0.78, 6.2, 9.7, 3.0, 9.0, 'darkmetal', name='receiver')
    side(g, 0.78, 7.8, 9.2, 4.0, 7.2, 'rubber', 'ejection_port')
    box(g, 0.5, 6.0, 6.25, 3.6, 7.0, 'darkmetal', name='loading_port')
    box(g, 0.18, 9.4, 10.4, 8.2, 8.8, 'steel', name='hammer', rot=('x', 22.5, (C, 9.4, 8.5)))
    pin(g, 0.78, 7.0, 4.0, 'darkmetal')
    pin(g, 0.78, 8.6, 8.2, 'darkmetal')
    guard(g, 6.2, 7.0, 9.8)
    z_end = stock(g, 0.68, 9.2, 6.4, 3.8, 9.0, 11.2, 24.4, W, plate='rubber')
    swivel(g, 3.6, z_end + 2.0)
    finish(g)


def double_barrel(name='double_barrel', sawed=False):
    """Side-by-side double: two barrels with a centre rib and bead, splinter forend, boxlock action with
    top lever and twin triggers, walnut stock. The sawed-off cuts the barrels at the forend and the stock
    down to a rounded pistol grip."""
    g = Gun(name)
    W = 'wood'
    BY = 9.1
    front = -7.0 if sawed else -15.0
    for s in (-1, 1):
        g.cyl('z', (C + s * 0.52, BY), 0.5, front, 3.0, 'darkmetal', seg=16, name='barrel')
        g.cyl('z', (C + s * 0.52, BY), 0.32, front - 0.05, front + 0.1, 'rubber', name='bore')
    box(g, 0.2, BY + 0.3, BY + 0.62, front, 3.0, 'gunmetal', name='top_rib')
    box(g, 0.3, BY - 0.62, BY - 0.3, front, 3.0, 'gunmetal', name='bottom_rib')
    box(g, 0.07, BY + 0.62, BY + 0.85, front + 0.3, front + 0.55, 'gold', name='bead')
    if sawed:
        g.ring('z', (C - 0.52, BY), 0.5, 0.56, front, front + 0.2, 'steel', name='cut_edge')
        g.ring('z', (C + 0.52, BY), 0.5, 0.56, front, front + 0.2, 'steel', name='cut_edge')
    box(g, 0.85, 7.7, 8.8, -4.4, 2.8, W, name='forend')
    box(g, 0.7, 7.35, 7.8, -4.0, 2.4, W, name='forend_belly')
    box(g, 0.9, 7.6, 8.4, 2.0, 3.0, 'worn_steel', name='forend_iron')

    box(g, 1.04, 7.2, 9.7, 3.0, 7.0, 'worn_steel', name='receiver')
    box(g, 1.06, 7.6, 9.4, 3.4, 6.6, 'engraved_steel', name='engraving_panel')
    g.cyl('x', (7.5, 3.4), 0.3, C - 1.1, C + 1.1, 'darkmetal', name='hinge_pin')
    box(g, 0.3, 9.7, 10.0, 5.6, 7.4, 'darkmetal', name='top_lever')
    box(g, 0.12, 9.7, 9.95, 6.0, 6.5, 'gold', name='safety')
    guard(g, 7.2, 4.6, 8.0, trigger_z=5.2)
    box(g, 0.08, 6.6, 7.2, 6.1, 6.4, 'steel', name='trigger_rear')
    if sawed:
        box(g, 0.78, 7.0, 9.5, 7.0, 9.0, W, name='stock_wrist')
        GRIP = ('x', -22.5, (C, 9.0, 7.6))
        g.box((C - 0.74, 3.0, 7.2), (C + 0.74, 9.0, 9.8), W, rot=GRIP, name='pistol_grip')
        g.box((C - 0.7, 2.6, 7.3), (C + 0.7, 3.05, 9.7), 'wood_red', rot=GRIP, name='grip_cut')
    else:
        z_end = stock(g, 0.82, 9.5, 6.8, 4.0, 7.0, 9.6, 23.0, W, plate='rubber')
        box(g, 0.66, 5.6, 6.9, 7.6, 9.8, W, name='grip_bump', rot=('x', -22.5, (C, 6.9, 7.6)))
        swivel(g, 3.8, z_end + 2.0)
    pin(g, 1.04, 8.0, 6.4, 'brass')
    finish(g)


def sawed_off():
    double_barrel('sawed_off', sawed=True)


BUILDERS = {
    'thompson': thompson, 'kar98k': kar98k, 'stg44': stg44, 'gewehr43': gewehr43, 'trench_gun': trench_gun,
    'bar': bar, 'm1_garand': m1_garand, 'type100': type100, 'fg42': fg42, 'arisaka': arisaka,
    'm1_carbine': m1_carbine, 'double_barrel': double_barrel, 'sawed_off': sawed_off,
}

if __name__ == '__main__':
    for key in sys.argv[1:] or BUILDERS:
        BUILDERS[key]()
