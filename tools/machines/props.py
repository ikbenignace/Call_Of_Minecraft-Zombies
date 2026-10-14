#!/usr/bin/env python3
"""Map props as item models: ammo_crate, power_switch (wall mounted, back at z = 16), fridge, bank.
Front towards -Z, floor at y = 0."""
import sys

from mkit import *  # noqa: F401,F403

C = 8
paint('olive', (76, 84, 52), wear=0.05)
paint('olive_dark', (52, 58, 38))
paint('box_grey', (112, 116, 118), wear=0.05)
paint('box_dark', (62, 66, 70))
paint('fridge_white', (222, 224, 216), wear=0.04, amount=0.04)
paint('fridge_trim', (170, 172, 168))
paint('bank_grey', (106, 112, 116), wear=0.04)
paint('bank_dark', (50, 54, 60))
paint('lever_red', (186, 36, 30))
MATERIALS['hazard_stripe'] = Material(gunkit.hazard(), mode='stretch')
lit('screen_green', (30, 120, 60), (170, 255, 190))
lit('red_light', (190, 30, 20), (255, 190, 160))
lit('green_light', (30, 150, 50), (210, 255, 190))
paint('base_dark', (40, 38, 40))
paint('coin_dark', (30, 30, 32))
lit('slot_light', (200, 170, 40), (255, 245, 170))


def ammo_crate():
    graphic('ammo_stencil', 8, 4, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (76, 84, 52, 255), W, H),
        text_center(d, W, H, "AMMO", 'block', 22, (230, 214, 120, 255), cy=0.4),
        text_center(d, W, H, "5.56mm  200 RDS", 'block', 8, (210, 200, 150, 255), cy=0.82)))
    g = new_gun('ammo_crate')
    box(g, 1, 0, 3, 15, 7, 13, 'olive', name='crate')
    box(g, 0.8, 6.2, 2.8, 15.2, 7.4, 13.2, 'olive_dark', name='rim')
    plate(g, 3, 1.4, 11, 5.4, 3.0, 'ammo_stencil')
    for x0 in (1.4, 12.4):
        box(g, x0, 0.4, 2.5, x0 + 0.6, 6.4, 3.0, 'steel', name='band')
    box(g, 6.5, 4, 2.4, 9.5, 6, 3.0, 'steel', name='latch')
    box(g, 0, 3, 5.5, 1, 5, 10.5, 'steel', name='handle')
    box(g, 15, 3, 5.5, 16, 5, 10.5, 'steel', name='handle')
    for i in range(7):
        for j in range(3):
            x, z = 2.4 + i * 1.8, 4.6 + j * 2.4
            box(g, x, 7.0, z, x + 1.2, 8.8, z + 1.2, 'brass', name='bullet')
            box(g, x + 0.15, 8.8, z + 0.15, x + 1.05, 9.8, z + 1.05, 'copper', name='bullet_tip')
    g.box((1, 7.4, 12.4), (15, 13.4, 13.0), 'olive', rot=('x', -22.5, (8, 7.4, 12.7)), name='lid')
    return save(g)


def power_switch():
    graphic('power_label', 7.2, 2.2, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (226, 214, 140, 255), W, H),
        text_center(d, W, H, "POWER", 'block', 18, (150, 30, 24, 255))))
    g = new_gun('power_switch')
    box(g, 3, 1.5, 11, 13, 15.5, 16, 'box_grey', name='box')
    box(g, 2.6, 1.5, 10.6, 13.4, 3.7, 11, 'hazard_stripe', name='stripe')
    plate(g, 4.4, 12.4, 11.6, 14.6, 11, 'power_label')
    box(g, 4.6, 4.4, 10.5, 11.4, 9.6, 11, 'box_dark', name='slot_plate')
    box(g, 5.4, 4.8, 10.2, 10.6, 5.4, 10.5, 'steel', name='track')
    box(g, 6.6, 6.4, 9.4, 9.4, 8.4, 10.5, 'chrome', name='pivot')
    g.box((7.4, 6.8, 9.6), (8.6, 11.4, 10.4), 'steel', rot=('z', -22.5, (8, 7.4, 10)), name='lever_arm')
    g.box((6.6, 11.0, 9.2), (9.4, 12.6, 10.8), 'lever_red', rot=('z', -22.5, (8, 7.4, 10)), name='lever_grip')
    box(g, 4.8, 3.6, 10.6, 5.6, 4.2, 10.9, 'red_light', name='lamp')
    box(g, 10.4, 3.6, 10.6, 11.2, 4.2, 10.9, 'green_light', name='lamp')
    for x, y in ((3.4, 14.4), (12.0, 14.4), (3.4, 2.2), (12.0, 2.2)):
        box(g, x, y, 10.4, x + 0.6, y + 0.6, 11, 'chrome', name='bolt')
    return save(g)


def fridge():
    graphic('fridge_badge', 4, 1.6, lambda d, W, H: (
        d.rounded_rectangle((0, 0, W - 1, H - 1), radius=H * 0.3, fill=(150, 152, 150, 255)),
        text_center(d, W, H, "ICE BOX", 'block', 9, (50, 50, 56, 255))))
    g = new_gun('fridge')
    box(g, 2.5, 0, 3, 13.5, 1.8, 13, 'base_dark', name='plinth')
    box(g, 2, 1.8, 2.6, 14, 28, 13.4, 'fridge_white', name='body')
    stepped_top(g, 2, 14, 2.6, 13.4, 28, 'fridge_white', steps=((0.4, 0.5), (1.0, 0.5)))
    box(g, 2.2, 21, 2.3, 13.8, 21.5, 2.6, 'fridge_trim', name='door_seam')
    box(g, 2.2, 1.8, 2.3, 13.8, 2.4, 2.6, 'fridge_trim', name='kick_plate')
    for y0, y1 in ((23, 27), (5, 18)):
        box(g, 11.6, y0, 1.7, 12.6, y1, 2.4, 'chrome', name='handle')
        box(g, 11.8, y0, 2.3, 12.4, y0 + 0.5, 2.6, 'steel', name='handle_mount')
        box(g, 11.8, y1 - 0.5, 2.3, 12.4, y1, 2.6, 'steel', name='handle_mount')
    plate(g, 4, 18.6, 8, 20.2, 2.6, 'fridge_badge')
    for y in (11, 14, 17):
        box(g, 3.4, y, 2.2, 5.4, y + 0.4, 2.6, 'fridge_trim', name='vent')
    return save(g)


def bank():
    graphic('bank_screen', 8, 4, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (20, 48, 30, 255), W, H),
        text_center(d, W, H, "BANK", 'block', 26, (120, 255, 150, 255), cy=0.4),
        text_center(d, W, H, "DEPOSIT / WITHDRAW", 'block', 7, (120, 255, 150, 255), cy=0.82)))
    graphic('keypad', 4, 4, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (36, 38, 42, 255), W, H),
        [rect(d, 0.1 + i * 0.3, 0.1 + j * 0.22, 0.34 + i * 0.3, 0.26 + j * 0.22, (190, 192, 190, 255), W, H) for i in range(3) for j in range(4)]))
    g = new_gun('bank')
    box(g, 2.5, 0, 2.5, 13.5, 1.4, 13.5, 'bank_dark', name='plinth')
    box(g, 3, 1.4, 3, 13, 12.6, 13, 'bank_grey', name='body')
    box(g, 2.6, 12.4, 2.6, 13.4, 13.2, 13.4, 'steel', name='band')
    box(g, 3, 13.2, 3, 13, 18.8, 13, 'bank_dark', name='head')
    plate(g, 3.8, 13.8, 12.2, 18.2, 3.0, 'bank_screen')
    box(g, 4, 8.6, 2.4, 12, 10.4, 3.0, 'bank_dark', name='slot_hood')
    box(g, 4.6, 9.2, 2.3, 11.4, 9.7, 2.4, 'slot_light', name='slot')
    plate(g, 4.4, 3.2, 8.4, 7.2, 3.0, 'keypad')
    box(g, 9.6, 3.6, 2.3, 12, 6.2, 3.0, 'coin_dark', name='tray')
    box(g, 9.4, 3.2, 2.0, 12.2, 3.8, 3.0, 'steel', name='tray_lip')
    box(g, 7.8, 18.8, 7.8, 8.2, 20, 8.2, 'steel', name='antenna')
    return save(g)


BUILDERS = {'ammo_crate': ammo_crate, 'power_switch': power_switch, 'fridge': fridge, 'bank': bank}

if __name__ == '__main__':
    for k in sys.argv[1:] or list(BUILDERS):
        BUILDERS[k]()
