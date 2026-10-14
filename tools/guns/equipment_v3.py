#!/usr/bin/env python3
"""BO1 zombies equipment held models: frag grenade, semtex, claymore, monkey bomb, Gersh device.
Small items centred in the block; spheres are stacked 16-gon slices."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

C = 8
HELD = {
    "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"translation": [2, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"translation": [2, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, 2.5, 0], "scale": [1.9, 1.9, 1.9]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def scaled(display, k):
    """Copy of a display block with the hand/ground slots scaled by k (for small throwables)."""
    out = {}
    for slot, t in display.items():
        t = dict(t)
        if slot.startswith(('thirdperson', 'firstperson', 'ground')):
            t['scale'] = [v * k for v in t['scale']]
        out[slot] = t
    return out


def sphere(g, cx, cy, cz, r, mat, name):
    for dy, k in ((-0.75, 0.66), (-0.4, 0.92), (0, 1.0), (0.4, 0.92), (0.75, 0.66)):
        g.cyl('y', (cx, cz), r * k, cy + dy * r - 0.2 * r, cy + dy * r + 0.2 * r, mat, seg=16, name=name)


def frag():
    g = Gun('frag_v3')
    sphere(g, C, 7.5, 8, 2.4, 'polymer_dark', 'body')
    for y in (6.6, 7.5, 8.4):
        g.cyl('y', (C, 8), 2.45, y - 0.08, y + 0.08, 'rubber', seg=16, name='segment_line')
    g.cyl('y', (C, 8), 0.8, 9.7, 10.9, 'gunmetal', seg=8, name='fuze')
    g.box((C - 0.35, 9.4, 8.6), (C + 0.35, 10.9, 9.0), 'gunmetal', name='spoon_top')
    g.box((C - 0.35, 6.0, 9.9), (C + 0.35, 10.0, 10.2), 'gunmetal', rot=('x', -22.5, (C, 8.0, 10.0)), name='spoon')
    g.ring('x', (10.5, 6.6), 0.4, 0.6, C - 0.1, C + 0.1, 'steel', name='pin_ring')
    import math
    for i in range(8):
        a = math.radians(i * 45)
        g.cyl('y', (C + 0.8 * math.cos(a), 8 + 0.8 * math.sin(a)), 0.12, 9.8, 10.8, 'darkmetal', seg=8, name='fuze_knurl')
    g.cyl('x', (10.55, 8.0), 0.22, C - 1.0, C + 1.0, 'steel', seg=8, name='lever_rivet')
    g.cyl('x', (9.95, 8.0), 0.1, C - 0.9, C + 1.3, 'steel', seg=8, name='pin_shaft')
    g.ring('z', (C + 1.55, 9.95), 0.28, 0.45, 7.9, 8.1, 'steel', name='pull_ring')
    g.cyl('y', (C, 8), 0.7, 5.0, 5.4, 'darkmetal', seg=8, name='base_plug')
    for y in (5.9, 9.0):
        g.cyl('y', (C, 8), 1.2 if y < 6 else 1.2, y, y + 0.1, 'rubber', seg=8, name='lug_line')
    for side in (-1, 1):
        g.box((C + side * 1.1 - 0.25, 8.95, 7.7), (C + side * 1.1 + 0.25, 9.1, 8.3), 'rubber', name='shoulder_slit')
    g.cyl('y', (C, 8), 0.86, 10.2, 10.4, 'darkmetal', seg=8, name='fuze_band')
    g.cyl('x', (10.6, 8.8), 0.2, C - 0.5, C + 0.5, 'steel', seg=8, name='lever_hinge_bolt')
    g.box((C - 0.2, 10.1, 7.15), (C + 0.2, 10.4, 7.3), 'glow_blue', name='fuze_indicator')
    g.cyl('y', (C, 8), 0.4, 4.8, 5.0, 'steel', seg=8, name='base_disc')
    g.box((C - 0.3, 5.9, 9.95), (C + 0.3, 6.3, 10.25), 'darkmetal', name='spoon_tip')
    g.save(scaled(HELD, 0.6))


def semtex():
    """Semtex: tall lump of tan plastic explosive wrapped in black tape, detonator box on top with
    a red wire looping down under the tape."""
    g = Gun('semtex_v3')
    g.box((C - 1.25, 2.6, 6.75), (C + 1.25, 8.0, 9.25), 'putty', name='charge')
    g.box((C - 1.0, 2.2, 7.0), (C + 1.0, 2.6, 9.0), 'putty', name='charge_bottom')
    g.box((C - 1.45, 3.4, 6.95), (C + 1.45, 7.2, 9.05), 'putty', name='charge_bulge')
    g.box((C - 1.5, 5.6, 6.6), (C + 1.5, 7.0, 9.4), 'rubber', name='tape')
    g.box((C - 1.32, 7.0, 6.7), (C + 1.32, 7.4, 9.3), 'rubber', name='tape_edge')
    g.box((C - 0.75, 8.0, 7.2), (C + 0.75, 10.4, 8.8), 'polymer_dark', name='detonator')
    g.box((C + 0.75, 8.6, 7.4), (C + 0.8, 10.2, 8.6), 'label_white', name='detonator_label')
    g.cyl('z', (C - 0.25, 9.8), 0.18, 7.0, 7.2, 'glow_green', seg=8, name='arm_light')
    g.cyl('z', (C - 0.25, 9.1), 0.2, 7.0, 7.2, 'steel', seg=8, name='wire_post')
    g.wire([(C - 0.25, 9.1, 7.0), (C - 0.25, 8.4, 6.6), (C - 0.25, 7.0, 6.4)], 0.12, 'paint_red', name='wire')
    for y in (3.6, 4.5):
        g.box((C - 1.5, y, 6.6), (C + 1.5, y + 0.45, 9.4), 'rubber', name='tape_wrap')
    for dx, mat in ((-0.35, 'led_red'), (0.35, 'steel')):
        g.cyl('y', (C + dx, 8.0), 0.22, 10.4, 10.65, mat, seg=8, name='det_button')
    g.box((C + 1.5, 6.0, 8.5), (C + 1.62, 6.6, 9.0), 'steel', name='wire_clip')
    g.box((C - 0.85, 8.0, 7.1), (C + 0.85, 8.25, 8.9), 'brass', name='det_ferrule')
    for z, y in ((7.0, 9.8), (7.0, 9.1)):
        g.ring('z', (C - 0.25, y), 0.2, 0.34, z, z + 0.2, 'label_white', name='knob_rim')
    for y in (9.2, 9.5):
        g.box((C + 0.8, y, 7.6), (C + 0.83, y + 0.1, 8.4), 'darkmetal', name='label_text')
    g.box((C - 0.8, 8.9, 7.35), (C - 0.75, 9.7, 7.55), 'rubber', name='det_vent_slot')
    g.box((C - 1.2, 5.2, 6.62), (C + 0.4, 5.6, 9.38), 'rubber', name='tape_fold')
    g.wire([(C + 1.5, 5.6, 8.7), (C + 2.0, 5.2, 8.8), (C + 2.1, 4.2, 8.8)], 0.12, 'paint_red', name='wire_tail')
    g.box((C - 1.0, 7.4, 6.55), (C + 1.0, 7.8, 6.7), 'rubber', name='tape_flap')
    g.save(HELD)


def claymore():
    """M18 Claymore: one curved olive body (centre panel plus two wings hinged at its edges, bending back
    so the face is convex towards the enemy), 'FRONT TOWARD ENEMY' face, two pairs of scissor legs,
    detonator wells and peep sight on top."""
    g = Gun('claymore_v3')
    Y0, Y1, Z0, Z1 = 6.0, 8.9, 7.4, 8.6
    g.box((C - 1.3, Y0, Z0), (C + 1.3, Y1, Z1), 'polymer_olive', name='body_centre')
    for side in (-1, 1):
        hinge = C + side * 1.3
        x0, x1 = sorted((hinge, hinge + side * 1.5))
        g.box((x0, Y0, Z0), (x1, Y1, Z1), 'polymer_olive', rot=('y', -22.5 * side, (hinge, 7.7, Z0)), name='body_wing')
    # 'FRONT TOWARD ENEMY' is moulded into the face: two raised lines in a darker olive.
    for y, half in ((7.95, 0.75), (7.35, 1.05)):
        g.box((C - half, y, Z0 - 0.05), (C + half, y + 0.3, Z0), 'polymer_dark', name='front_toward_enemy')
    for side in (-1, 1):
        x = C + side * 1.6
        for dz, ang in ((-0.1, 22.5), (0.1, -22.5)):
            g.box((x - 0.12, 3.6, 7.9 + dz), (x + 0.12, Y0 + 0.4, 8.1 + dz), 'darkmetal', rot=('x', ang, (x, Y0, 8.0)), name='leg')
    for x in (C - 0.8, C + 0.8):
        g.cyl('y', (x, 8.0), 0.3, Y1, Y1 + 0.5, 'gunmetal', seg=8, name='detonator_well')
    g.box((C - 0.2, Y1, 7.8), (C + 0.2, Y1 + 0.35, 8.2), 'darkmetal', name='sight')
    for y in (6.85, 6.55):
        g.box((C - 0.9, y, 7.35), (C + 0.9, y + 0.12, 7.4), 'polymer_dark', name='front_text')
    g.box((C - 0.5, 6.25, 7.35), (C + 0.5, 6.4, 7.4), 'polymer_dark', name='front_text')
    for side in (-1, 1):
        g.box((C + side * 1.45, 5.7, 7.7), (C + side * 1.75, 6.1, 8.3), 'steel', name='leg_bracket')
    for x in (C - 0.8, C + 0.8):
        g.cyl('y', (x, 8.0), 0.36, 9.4, 9.6, 'paint_red', seg=8, name='detonator_cap')
    for side in (-1, 1):
        g.cyl('y', (C + side * 1.3, 8.0), 0.2, 8.85, 9.05, 'steel', seg=8, name='hinge_cap')
    g.cyl('z', (C + 0.5, 7.2), 0.65, 8.6, 8.75, 'darkmetal', seg=16, name='spool_flange')
    g.cyl('z', (C + 0.5, 7.2), 0.45, 8.75, 9.05, 'copper', seg=16, name='spool_wire')
    g.cyl('z', (C + 0.5, 7.2), 0.65, 9.05, 9.2, 'darkmetal', seg=16, name='spool_flange')
    g.box((C - 0.12, 9.0, 7.77), (C + 0.12, 9.2, 7.8), 'rubber', name='sight_slit')
    for y in (8.45, 6.1):
        g.box((C - 1.2, y, Z1), (C + 1.2, y + 0.1, Z1 + 0.05), 'polymer_dark', name='back_ridge')
    for y in (7.7, 7.2):
        g.box((C - 0.9, y, Z1), (C + 0.9, y + 0.12, Z1 + 0.05), 'polymer_dark', name='back_text')
    for side in (-1, 1):
        x0, x1 = sorted((C + side * 0.2, C + side * 0.55))
        g.box((x0, Y1, 7.8), (x1, Y1 + 0.2, 8.2), 'darkmetal', name='sight_arm')
        g.cyl('x', (5.9, 8.0), 0.15, *sorted((C + side * 1.75, C + side * 1.9)), 'steel', seg=8, name='leg_pivot_bolt')
    g.wire([(C + 0.8, 9.6, 8.0), (C + 1.2, 10.2, 8.0), (C + 2.0, 10.0, 8.0)], 0.1, 'paint_blue', name='det_cable')
    g.box((C - 1.2, 8.5, Z0 - 0.05), (C + 1.2, 8.6, Z0), 'polymer_dark', name='front_ridge')
    g.save(HELD)


def monkey_bomb():
    """Cymbal Monkey: sitting wind-up toy facing -Z, oversized head with fez and grin, cymbals held
    out in front, dynamite pack and wind-up key on its back."""
    g = Gun('monkey_bomb_v3')
    for side in (-1, 1):
        g.box((C + side * 0.25 - 0.45, 3.0, 5.8), (C + side * 0.25 + 0.45 + side * 0.45, 3.9, 8.4), 'fur_brown', name='thigh')
        g.box((C + side * 0.7 - 0.45, 3.0, 5.2), (C + side * 0.7 + 0.45, 3.5, 5.8), 'monkey_skin', name='foot')
    g.box((C - 1.1, 3.0, 7.0), (C + 1.1, 6.4, 9.4), 'fur_brown', name='torso')
    g.box((C - 0.65, 3.8, 6.95), (C + 0.65, 5.8, 7.0), 'putty', name='belly')
    g.box((C - 1.3, 6.4, 6.6), (C + 1.3, 9.0, 9.2), 'fur_brown', name='head')
    g.box((C - 1.0, 6.6, 6.45), (C + 1.0, 8.7, 6.6), 'monkey_skin', name='face')
    g.box((C - 0.8, 6.65, 6.0), (C + 0.8, 7.7, 6.45), 'monkey_skin', name='muzzle')
    g.box((C - 0.62, 6.95, 5.95), (C + 0.62, 7.45, 6.0), 'polymer_white', name='teeth')
    g.box((C - 0.62, 7.17, 5.93), (C + 0.62, 7.23, 5.95), 'rubber', name='teeth_gap')
    for x in (C - 0.45, C + 0.45):
        g.box((x - 0.28, 7.85, 6.4), (x + 0.28, 8.4, 6.45), 'polymer_white', name='eye')
        g.box((x - 0.1, 7.95, 6.38), (x + 0.1, 8.25, 6.4), 'led_red', name='pupil')
        g.box((x - 0.35, 8.45, 6.4), (x + 0.35, 8.6, 6.45), 'fur_dark', name='brow')
    for side in (-1, 1):
        x0, x1 = sorted((C + side * 1.3, C + side * 1.9))
        g.box((x0, 7.3, 7.4), (x1, 8.4, 8.1), 'monkey_skin', name='ear')
    g.cyl('y', (C, 7.9), 0.8, 9.0, 10.1, 'fez_red', seg=8, name='fez')
    g.box((C - 0.06, 10.1, 7.85), (C + 0.06, 10.25, 8.6), 'gold', name='tassel_cord')
    g.box((C - 0.1, 9.4, 8.6), (C + 0.1, 10.2, 8.75), 'gold', name='tassel')
    for side in (-1, 1):
        x0, x1 = sorted((C + side * 1.05, C + side * 1.6))
        g.box((x0, 5.3, 6.4), (x1, 5.9, 8.4), 'fur_brown', name='arm')
        g.box((x0, 5.25, 6.0), (x1, 5.95, 6.4), 'monkey_skin', name='hand')
        cx0, cx1 = sorted((C + side * 1.6, C + side * 1.78))
        g.cyl('x', (5.6, 6.3), 1.35, cx0, cx1, 'gold', seg=16, name='cymbal')
        kx0, kx1 = sorted((C + side * 1.78, C + side * 1.9))
        g.cyl('x', (5.6, 6.3), 0.3, kx0, kx1, 'brass', seg=8, name='cymbal_knob')
    for x in (C - 0.75, C, C + 0.75):
        g.cyl('y', (x, 9.85), 0.34, 3.4, 6.2, 'paint_red', seg=8, name='dynamite')
    g.box((C - 1.15, 4.4, 9.4), (C + 1.15, 4.8, 10.25), 'rubber', name='tape')
    g.box((C - 0.6, 5.2, 10.2), (C + 0.6, 5.9, 10.5), 'darkmetal', name='timer')
    g.box((C + 0.2, 5.45, 10.5), (C + 0.45, 5.65, 10.55), 'led_red', name='timer_led')
    g.wire([(C - 0.75, 6.2, 9.85), (C - 0.75, 7.4, 9.15)], 0.08, 'paint_blue', name='wire')
    g.wire([(C + 0.75, 6.2, 9.85), (C + 0.75, 7.2, 9.15)], 0.08, 'paint_red', name='wire')
    g.box((C - 0.12, 4.0, 10.0), (C + 0.12, 4.6, 11.1), 'brass', name='key_shaft')
    g.box((C - 0.8, 3.5, 11.1), (C + 0.8, 4.7, 11.3), 'brass', name='key_bow')
    for side in (-1, 1):
        x0, x1 = sorted((C + side * 1.62, C + side * 1.95))
        g.ring('x', (7.6, 6.3), 0.32, 0.55, x0, x1, 'brass', name='cymbal_rim')
        g.ring('x', (5.6, 6.3), 1.15, 1.42, *sorted((C + side * 1.6, C + side * 1.86)), 'brass', name='cymbal_rim')
        g.box((C + side * 0.8, 3.6, 11.1), (C + side * 1.0, 4.0, 11.3), 'brass', name='key_tooth')
        g.box((C + side * 0.8, 4.2, 11.1), (C + side * 1.0, 4.5, 11.3), 'brass', name='key_tooth')
    g.cyl('y', (C, 7.9), 0.86, 9.0, 9.2, 'gold', seg=8, name='fez_band')
    for x in (C - 0.3, C + 0.3):
        g.box((x - 0.12, 7.45, 5.97), (x + 0.12, 7.6, 6.0), 'rubber', name='nostril')
    for x in (C - 0.75, C, C + 0.75):
        g.cyl('y', (x, 9.85), 0.36, 6.2, 6.4, 'brass', seg=8, name='dynamite_cap')
    g.box((C - 0.45, 5.3, 10.5), (C - 0.25, 5.5, 10.55), 'paint_blue', name='timer_button')
    g.box((C - 0.2, 3.4, 6.9), (C + 0.2, 6.4, 7.0), 'rubber', rot=('z', 45, (C, 4.9, 7.0)), name='bandolier')
    g.box((C - 0.25, 4.7, 6.85), (C + 0.25, 5.1, 6.95), 'brass', name='bandolier_buckle')
    g.box((C - 0.3, 9.3, 7.05), (C + 0.3, 9.8, 7.25), 'gold', name='fez_emblem')
    g.wire([(C + 0.75, 6.4, 9.85), (C + 1.4, 8.5, 10.2), (C + 1.4, 10.0, 9.5), (C + 0.4, 10.15, 8.5)], 0.1, 'darkmetal', name='cable_arc')
    for side in (-1, 1):
        x0, x1 = sorted((C + side * 1.05, C + side * 1.6))
        for f in (0.15, 0.3):
            g.box((x0 + f * 0.8, 5.3, 5.97), (x0 + f * 0.8 + 0.05, 5.9, 6.0), 'rubber', name='finger_line')
        g.box((C + side * 0.7 - 0.1, 3.0, 5.17), (C + side * 0.7 + 0.1, 3.5, 5.2), 'rubber', name='toe_line')
    for x in (C - 0.75, C, C + 0.75):
        g.cyl('y', (x, 9.85), 0.38, 4.6, 5.0, 'putty', seg=8, name='dynamite_band')
    g.save(HELD)


def gersh():
    """Gersh Device (Ascension): worn yellow box with hazard-striped ends, indicator lights and a dial
    on the face, side carry handle and folding legs underneath."""
    g = Gun('gersh_v3')
    g.box((C - 1.6, 5.4, 5.6), (C + 1.6, 8.4, 10.4), 'paint_yellow', name='body')
    for z0, z1 in ((5.0, 5.6), (10.4, 11.0)):
        g.box((C - 1.7, 5.3, z0), (C + 1.7, 8.5, z1), 'hazard', name='hazard_end')
    g.box((C - 1.2, 8.4, 6.2), (C + 1.2, 8.8, 9.8), 'paint_yellow', name='top_plate')
    for z in (6.6, 7.4):
        g.cyl('y', (C - 0.5, z), 0.24, 8.8, 9.0, 'glow_green', seg=8, name='indicator')
    g.cyl('y', (C + 0.4, 8.9), 0.55, 8.8, 9.0, 'darkmetal', seg=16, name='dial')
    g.cyl('y', (C + 0.4, 8.9), 0.35, 9.0, 9.05, 'label_white', seg=16, name='dial_face')
    g.box((C - 0.9, 8.8, 9.2), (C - 0.3, 8.85, 9.7), 'label_warn', name='warning_label')
    g.box((C + 1.6, 6.2, 6.4), (C + 1.65, 7.8, 9.6), 'darkmetal', name='side_panel')
    for z in (6.8, 9.2):
        g.box((C + 1.6, 7.1, z - 0.2), (C + 2.2, 7.4, z + 0.2), 'darkmetal', name='handle_post')
    g.box((C + 2.0, 7.1, 6.6), (C + 2.3, 7.4, 9.4), 'rubber', name='handle')
    for zc in (6.2, 9.8):
        for xs in (-1, 1):
            g.box((C + xs * 1.1 - 0.3, 3.4, zc - 0.3), (C + xs * 1.1 + 0.3, 5.4, zc + 0.3), 'darkmetal',
                  rot=('x', 22.5 if zc > 8 else -22.5, (C, 5.4, zc)), name='leg')
            g.box((C + xs * 1.1 - 0.35, 3.2, zc - 0.4), (C + xs * 1.1 + 0.35, 3.6, zc + 0.4), 'paint_yellow',
                  rot=('x', 22.5 if zc > 8 else -22.5, (C, 5.4, zc)), name='foot')
    for x in (C - 0.95, C + 0.95):
        for z in (6.5, 9.5):
            g.cyl('y', (x, z), 0.14, 8.8, 8.9, 'steel', seg=8, name='top_rivet')
    g.box((C - 1.65, 5.4, 5.6), (C - 1.6, 5.8, 10.4), 'hazard', name='hazard_edge')
    for y in (6.2, 6.7, 7.2):
        g.box((C - 1.65, y, 6.4), (C - 1.6, y + 0.15, 9.6), 'rubber', name='vent')
    g.cyl('y', (C - 1.0, 9.5), 0.08, 8.8, 10.6, 'steel', seg=8, name='antenna')
    g.cyl('y', (C - 1.0, 9.5), 0.15, 10.6, 10.75, 'led_red', seg=8, name='antenna_tip')
    g.box((C - 0.9, 8.8, 8.0), (C - 0.3, 8.95, 8.7), 'paint_red', name='switch_guard')
    g.cyl('y', (C + 0.4, 8.9), 0.15, 9.05, 9.4, 'darkmetal', seg=8, name='stick')
    g.cyl('y', (C + 0.4, 8.9), 0.24, 9.4, 9.65, 'steel', seg=8, name='stick_ball')
    for zs in (-1, 1):
        for xs in (-1, 1):
            z0, z1 = (5.6, 5.9) if zs < 0 else (10.1, 10.4)
            x0, x1 = sorted((C + xs * 1.6, C + xs * 1.78))
            g.box((x0, 5.4, z0), (x1, 8.4, z1), 'darkmetal', name='corner_post')
    g.box((C + 1.65, 6.4, 7.5), (C + 1.7, 6.9, 8.2), 'label_warn', name='side_warning')
    g.box((C + 1.65, 7.2, 7.6), (C + 1.75, 7.7, 8.0), 'paint_red', name='side_switch')
    g.wire([(C + 2.15, 7.25, 9.4), (C + 2.15, 7.0, 10.0), (C + 1.7, 6.5, 10.5)], 0.12, 'copper', name='cable')
    g.save(HELD)


if __name__ == '__main__':
    for f in (frag, semtex, claymore, monkey_bomb, gersh):
        f()
