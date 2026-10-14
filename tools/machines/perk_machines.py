#!/usr/bin/env python3
"""
The twelve perk-a-cola machines as item models (machines/perk_<key>.json), after the Black Ops
zombies renders: retro vending cabinets, coolers and jukebox-style shells each topped by their
round lit emblem. One block footprint (x/z 0..16), front towards -Z, floor at y = 0.

    python3 tools/machines/perk_machines.py [key ...]
"""
import sys

from mkit import *  # noqa: F401,F403

C = 8
CHROME, STEEL, DARK = 'chrome', 'steel', 'darkmetal'
paint('cream', (200, 188, 150))
paint('cream_dirty', (176, 164, 128))
paint('coin_dark', (30, 30, 32))
paint('base_dark', (40, 38, 40))
lit('orange_light', (210, 120, 30), (255, 230, 150))
lit('white_light', (150, 175, 150), (255, 255, 245))
lit('amber_light', (200, 140, 30), (255, 240, 170))
lit('cyan_light', (30, 150, 190), (220, 255, 255))
lit('green_light', (30, 150, 50), (210, 255, 190))
lit('red_light', (190, 30, 20), (255, 190, 160))
lit('purple_light', (130, 40, 190), (240, 200, 255))
lit('yellow_light', (210, 180, 40), (255, 250, 190))
lit('glass_pale', (110, 140, 150), (210, 235, 240))
lit('glass_green', (30, 130, 80), (190, 255, 210))


def lamp_stalk(g, x, y0, y1, mat='steel'):
    box(g, x - 0.45, y0, 7.55, x + 0.45, y1, 8.45, mat, name='stalk')


def vend_front(g, cx, w, y0, y1, z, glow='orange_light', dark='coin_dark', mat='steel'):
    """Retro vending front: coin plate with display, slot, return flap and a lit dispenser."""
    coin_panel(g, cx - w / 2, cx + w / 2, y0, y1, z, dark, mat, glow)


def finish(g):
    return save(g)


def logo_text(bg, lines):
    """Rect sign graphic: background colour plus (text, font, size, colour, cy) lines."""
    def fn(d, W, H):
        if bg:
            rect(d, 0, 0, 1, 1, bg, W, H)
        for s, kind, size, col, cy in lines:
            text_center(d, W, H, s, kind, size, col, cy=cy)
    return fn


def sym_mule(d, W, H, c, a):
    poly(d, [(0.2, 0.2), (0.8, 0.2), (0.8, 0.56), (0.5, 0.84), (0.2, 0.56)], c, W, H)
    rect(d, 0.3, 0.3, 0.7, 0.42, a, W, H, 0.03)
    rect(d, 0.3, 0.42, 0.4, 0.62, a, W, H, 0.02)
    rect(d, 0.44, 0.42, 0.5, 0.48, a, W, H)


def emblem(kind, ring, ring2, face, sym, col, acc=None):
    return lambda d, W, H: disc_emblem(d, W, H, ring, ring2, face, sym, col, acc)


# ------------------------------------------------------------------------------------------------
def juggernog():
    paint('jug_red', (150, 34, 28), wear=0.04)
    paint('jug_red_dark', (100, 22, 20))
    graphic('jug_logo', 8.8, 8, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (216, 204, 168, 255), W, H),
        text_center(d, W, H, "Jugger-", 'script', 30, (120, 34, 24, 255), cy=0.3),
        text_center(d, W, H, "Nog", 'script', 34, (120, 34, 24, 255), cy=0.62),
        text_center(d, W, H, "ICE COLD", 'block', 8, (90, 80, 60, 255), cy=0.88)))
    graphic('jug_emblem', 5.4, 5.4, lambda d, W, H: disc_emblem(
        d, W, H, (190, 190, 190, 255), (250, 250, 250, 255), (205, 40, 30, 255), sym_shield, (250, 245, 235, 255), (205, 40, 30, 255)))
    g = new_gun('perk_juggernog')
    box(g, 2.5, 0, 2.5, 13.5, 1.2, 13.5, 'base_dark', name='plinth')
    box(g, 3, 1.2, 3, 13, 13.5, 13, 'jug_red', name='body_lower')
    box(g, 3, 13.3, 2.9, 13, 13.8, 13.1, STEEL, name='band')
    box(g, 3, 13.8, 3, 13, 25, 13, 'cream', name='body_upper')
    top = stepped_top(g, 3, 13, 3, 13, 25, 'cream')
    plate(g, 3.6, 16.2, 12.4, 24.4, 3.0, 'jug_logo')
    vend_front(g, 8, 6.4, 6.8, 12.8, 3.0)
    box(g, 6.4, 3.4, 2.5, 9.6, 5.6, 3, 'orange_light', name='dispense_slot')
    box(g, 3, 1.2, 2.8, 3.5, 25, 3.0, 'jug_red_dark', name='edge_l')
    box(g, 12.5, 1.2, 2.8, 13, 25, 3.0, 'jug_red_dark', name='edge_r')
    lamp_stalk(g, C, top - 0.2, top + 0.6)
    emblem_lamp(g, C, top + 2.6, 6.9, 2.6, CHROME, 'jug_emblem')
    return finish(g)


# ------------------------------------------------------------------------------------------------
def speed_cola():
    paint('sc_green', (40, 112, 54), wear=0.04)
    paint('sc_green_dark', (26, 72, 38))
    graphic('sc_logo', 12, 6.4, logo_text((206, 196, 160, 255), [
        ("Speed Cola", 'script', 40, (150, 44, 30, 255), 0.45), ("ICE COLD", 'block', 8, (150, 80, 50, 255), 0.88)]))
    graphic('sc_bottles', 2.8, 11, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (70, 110, 150, 255), W, H),
        [d.ellipse((W * 0.14, H * (0.03 + i * 0.16), W * 0.86, H * (0.03 + i * 0.16) + W * 0.72),
                   fill=(240, 248, 255, 255) if i < 3 else (110, 200, 90, 255)) for i in range(6)]))
    graphic('sc_emblem', 5.4, 5.4, emblem(0, (200, 200, 190, 255), (250, 250, 245, 255), (250, 250, 245, 255), sym_hand, (40, 130, 60, 255), (40, 130, 60, 255)))
    g = new_gun('perk_speed_cola')
    box(g, 1.5, 0, 2.5, 14.5, 1.2, 13.5, 'base_dark', name='plinth')
    box(g, 2, 1.2, 3, 14, 14, 13, 'sc_green', name='body_lower')
    box(g, 2, 13.8, 2.9, 14, 14.3, 13.1, STEEL, name='band')
    box(g, 2, 14.3, 3, 14, 23, 13, 'cream', name='body_upper')
    top = stepped_top(g, 2, 14, 3, 13, 23, 'cream')
    plate(g, 2.4, 16.4, 13.6, 22.8, 3.0, 'sc_logo')
    vend_front(g, 5.8, 5.4, 6.8, 14, 3.0, glow='white_light')
    plate(g, 9.4, 6.2, 12.2, 17.2, 3.0, 'sc_bottles')
    vents(g, 3, 7, 2, 5.4, 3.0, 'sc_green_dark', 'green_light')
    vents(g, 9, 13, 2, 5.4, 3.0, 'sc_green_dark', 'green_light')
    lamp_stalk(g, C, top - 0.2, top + 0.6)
    emblem_lamp(g, C, top + 2.7, 6.9, 2.7, CHROME, 'sc_emblem')
    return finish(g)


def double_tap():
    paint('dt_brown', (104, 36, 26), wear=0.05)
    paint('dt_dark', (62, 24, 20))
    paint('dt_gold', (200, 146, 40))
    MATERIALS['dt_corr'] = Material(ribs((126, 112, 96), count=12, vertical=False, depth=0.5), mode='stretch')

    def cap(d, W, H):
        for i in range(28):
            a = i * 2 * math.pi / 28
            x, y = W * (0.5 + 0.44 * math.cos(a)), H * (0.5 + 0.44 * math.sin(a))
            d.ellipse((x - W * 0.07, y - H * 0.07, x + W * 0.07, y + H * 0.07), fill=(214, 168, 70, 255))
        d.ellipse((W * 0.06, H * 0.06, W * 0.94, H * 0.94), fill=(214, 168, 70, 255))
        d.ellipse((W * 0.14, H * 0.14, W * 0.86, H * 0.86), fill=(78, 40, 24, 255))
        d.ellipse((W * 0.18, H * 0.18, W * 0.82, H * 0.82), fill=(236, 224, 196, 255))
        d.ellipse((W * 0.24, H * 0.24, W * 0.76, H * 0.76), fill=(226, 120, 30, 255))
        sym_bullets(d, W, H, (250, 240, 215, 255), (250, 240, 215, 255))
    graphic('dt_cap', 8, 8, cap)
    graphic('dt_logo', 12, 4.6, logo_text((74, 30, 22, 255), [("Double Tap", 'script', 36, (232, 178, 70, 255), 0.5)]))
    graphic('dt_cents', 2.4, 2.4, logo_text(None, [("10¢", 'block', 22, (232, 178, 70, 255), 0.5)]))
    g = new_gun('perk_double_tap')
    box(g, 1, 0, 2.5, 15, 1.2, 13.5, 'base_dark', name='plinth')
    box(g, 1.5, 1.2, 3, 14.5, 9, 13, 'dt_dark', name='body_lower')
    box(g, 1.5, 9, 3, 14.5, 17, 13, 'dt_corr', name='body_mid')
    box(g, 1.5, 17, 3, 14.5, 25, 13, 'dt_brown', name='body_upper')
    stepped_top(g, 1.5, 14.5, 3, 13, 25, 'dt_brown')
    for y in (8.6, 16.6):
        box(g, 1.3, y, 2.8, 14.7, y + 0.8, 13.2, 'dt_gold', name='trim')
    plate(g, 2.4, 2.2, 13.6, 7.6, 3.0, 'dt_logo')
    plate(g, 4, 17.6, 12, 25.6 - 0.8, 3.0, 'dt_cap')
    plate(g, 12.2, 18, 14.2, 20.4, 3.0, 'dt_cents')
    coin_panel(g, 3, 6.6, 10.4, 15.4, 3.0, 'coin_dark', STEEL, 'amber_light')
    box(g, 8.4, 10.4, 2.4, 13, 14.8, 3.0, 'coin_dark', name='dispense_bay')
    box(g, 8.9, 10.9, 2.35, 12.5, 12.2, 2.5, 'amber_light', name='dispense_light')
    return finish(g)


def quick_revive():
    paint('qr_blue', (62, 128, 176), wear=0.05)
    paint('qr_blue_dark', (40, 88, 128))
    paint('qr_white', (226, 228, 230))
    graphic('qr_sign', 12, 4.8, logo_text((200, 210, 205, 255), [("Quick Revive!", 'script', 44, (50, 130, 180, 255), 0.5)]))
    def qr_panel(d, W, H):
        rect(d, 0, 0, 1, 1, (232, 232, 230, 255), W, H)
        red = (186, 30, 24, 255)
        poly(d, [(0.3, 0.0), (0.75, 0.0), (0.7, 0.2), (0.6, 0.18), (0.5, 0.3), (0.4, 0.2), (0.34, 0.22)], red, W, H)
        for x, w, y in ((0.36, 0.06, 0.5), (0.5, 0.08, 0.86), (0.64, 0.05, 0.4)):
            rect(d, x, 0.15, x + w, y, red, W, H)
            d.ellipse((W * (x - 0.01), H * (y - 0.03), W * (x + w + 0.01), H * (y + 0.03)), fill=red)
        d.ellipse((W * 0.76, H * 0.55, W * 0.84, H * 0.6), fill=(60, 60, 60, 255))
    graphic('qr_panel', 6.8, 8.6, qr_panel)
    graphic('qr_bubbles', 2.6, 6, lambda d, W, H: [d.ellipse((W * x, H * y, W * (x + r), H * (y + r * W / H)), outline=(220, 235, 245, 255), width=SS)
                                                    for x, y, r in ((0.2, 0.1, 0.35), (0.55, 0.3, 0.25), (0.15, 0.55, 0.3), (0.6, 0.78, 0.3))])
    graphic('qr_emblem', 7.2, 7.2, emblem(0, (200, 205, 205, 255), (60, 140, 190, 255), (90, 225, 240, 255), sym_revive, (250, 252, 252, 255), (210, 40, 30, 255)))
    g = new_gun('perk_quick_revive')
    for x, z in ((3, 4.5), (13, 4.5), (3, 11.5), (13, 11.5)):
        g.cyl('y', (x, z), 0.9, 0, 3, 'base_dark', seg=8, name='foot')
    box(g, 1.5, 2.6, 3, 14.5, 13, 13, 'qr_blue', name='cooler')
    box(g, 1.2, 12.6, 2.8, 14.8, 13.8, 13.2, 'qr_blue_dark', name='lid')
    box(g, 2, 13.8, 5, 14, 17.4, 12.5, 'qr_blue', name='hood')
    g.box((2, 13.8, 4.2), (14, 18.8, 5.2), 'qr_sign', rot=('x', -22.5, (8, 13.8, 4.7)), faces={'north': 'qr_sign'}, name='sign')
    plate(g, 4.6, 3.4, 11.4, 12, 3.0, 'qr_panel')
    plate(g, 1.8, 5.2, 4.4, 11.2, 3.0, 'qr_bubbles')
    plate(g, 11.6, 5.2, 14.2, 11.2, 3.0, 'qr_bubbles')
    box(g, 6.6, 13.8, 5.2, 9.4, 14.6, 6.6, STEEL, name='handle')
    box(g, 7.6, 17, 7.6, 8.4, 23.4, 8.4, STEEL, name='pole')
    emblem_lamp(g, C, 26.4, 6.9, 3.4, 'qr_white', 'qr_emblem', depth=2.0)
    return finish(g)


def stamin_up():
    timber('su_wood', (168, 90, 34), 0.22)
    paint('su_wood_dark', (112, 58, 26))
    paint('su_green', (98, 104, 52))
    paint('su_yellow', (232, 176, 36))
    graphic('su_sign', 9.4, 2.6, lambda d, W, H: (
        d.rounded_rectangle((0, 0, W - 1, H - 1), radius=H * 0.5, fill=(236, 178, 36, 255), outline=(176, 110, 30, 255), width=2 * SS),
        text_center(d, W, H, "STAMIN-UP", 'block', 20, (190, 70, 26, 255))))
    graphic('su_emblem', 7.2, 7.2, emblem(0, (206, 200, 180, 255), (250, 250, 245, 255), (240, 130, 40, 255), sym_runner, (255, 250, 230, 255), (255, 250, 230, 255)))
    for i, c in enumerate(((140, 50, 190), (210, 40, 36), (226, 214, 50), (240, 140, 30), (60, 190, 70))):
        lit('su_lamp%d' % i, c, tuple(min(255, v + 90) for v in c))
    g = new_gun('perk_stamina_up')
    box(g, 2, 0, 2.5, 14, 1.6, 13.5, 'su_green', name='plinth')
    box(g, 3, 1.6, 3, 13, 13.5, 13, 'su_wood', name='cabinet')
    box(g, 3, 13.5, 3.5, 13, 19.5, 12.5, 'su_wood', name='shell')
    arch(g, C, 19.5, 5, 3.5, 12.5, 'su_wood')
    box(g, 2.3, 14.5, 4, 3.2, 22, 12, 'su_green', name='speaker_l')
    box(g, 12.8, 14.5, 4, 13.7, 22, 12, 'su_green', name='speaker_r')
    box(g, 2.8, 13.2, 2.8, 13.2, 13.7, 13.2, STEEL, name='band')
    box(g, 7.2, 24.4, 7.2, 8.8, 26.4, 8.8, 'su_green', name='finial')
    emblem_lamp(g, C, 20, 2.6, 3.6, CHROME, 'su_emblem', depth=1.0)
    plate(g, 3.2, 14.2, 12.8, 16.8, 2.9, 'su_sign')
    for i in range(5):
        x = 3.8 + i * 1.9
        box(g, x, 11.6, 2.6, x + 1.3, 13.0, 3.0, 'su_lamp%d' % i, name='perk_lamp')
    box(g, 3.4, 5, 2.4, 12.6, 10, 3.0, 'coin_dark', name='tray_recess')
    box(g, 3.2, 4.4, 2.0, 12.8, 5.2, 3.0, STEEL, name='tray_lip')
    for i in range(5):
        x = 3.9 + i * 1.8
        box(g, x, 10.4, 2.5, x + 1.1, 11.2, 3.0, STEEL, name='button')
    return finish(g)


def phd_flopper():
    paint('phd_purple', (104, 44, 142), wear=0.04)
    paint('phd_dark', (50, 42, 58))
    paint('phd_rust', (120, 66, 40))
    graphic('phd_emblem', 6, 6, emblem(0, (190, 190, 200, 255), (110, 60, 160, 255), (160, 60, 190, 255), sym_burst, (255, 220, 255, 255), (255, 240, 120, 255)))
    graphic('phd_lights', 5, 6, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (24, 24, 28, 255), W, H, 0.04),
        rect(d, 0.04, 0.03, 0.96, 0.97, (236, 190, 40, 255), W, H, 0.04),
        rect(d, 0.1, 0.08, 0.9, 0.92, (24, 24, 28, 255), W, H, 0.04),
        [d.ellipse((W * 0.38, H * (0.14 + i * 0.2), W * 0.62, H * (0.14 + i * 0.2) + W * 0.24), fill=c)
         for i, c in enumerate(((220, 50, 40, 255), (70, 170, 70, 255), (240, 240, 230, 255), (170, 70, 220, 255)))]))
    graphic('phd_label', 6, 3, logo_text((226, 130, 30, 255), [("PhD", 'block', 22, (96, 30, 130, 255), 0.35), ("FLOPPER", 'block', 11, (96, 30, 130, 255), 0.75)]))
    lit('yellow_bulb', (214, 170, 40), (255, 250, 190))
    g = new_gun('perk_phd_flopper')
    box(g, 2, 0, 2, 14, 3, 14, 'phd_dark', name='base')
    box(g, 3.4, 3, 3.4, 12.6, 7, 12.6, 'phd_dark', name='taper')
    box(g, 5, 7, 4.5, 11, 21, 11.5, 'phd_purple', name='column')
    box(g, 4.7, 6.7, 4.2, 11.3, 7.3, 11.8, STEEL, name='collar')
    plate(g, 5.5, 14, 10.5, 20, 4.5, 'phd_lights')
    coin_panel(g, 5.6, 10.4, 8.8, 13.4, 4.5, 'coin_dark', STEEL, 'purple_light')
    plate(g, 5, 0.6, 11, 3.6, 2.0, 'phd_label')
    box(g, 6, 4.4, 3.0, 10, 6.6, 3.4, 'coin_dark', name='tray')
    box(g, 7.6, 21, 7.6, 8.4, 22.9, 8.4, STEEL, name='stalk')
    emblem_lamp(g, C, 25.8, 6.9, 3, CHROME, 'phd_emblem')
    sunburst(g, C, 25.8, 7.5, 8.3, 4.5, 'phd_dark', 'yellow_bulb')
    return finish(g)


def deadshot():
    paint('ds_dark', (58, 60, 62), wear=0.05)
    paint('ds_light', (96, 102, 100))
    paint('ds_olive', (84, 94, 66))
    graphic('ds_sign', 9.6, 6, logo_text((120, 132, 108, 255), [("DEADSHOT", 'block', 22, (40, 50, 40, 255), 0.4), ("Daiquiri", 'script', 24, (40, 50, 40, 255), 0.72)]))
    graphic('ds_emblem', 7, 4.4, lambda d, W, H: (
        d.pieslice((0, -H * 0.9, W - 1, H * 1.0), 0, 180, fill=(140, 200, 80, 255)),
        d.pieslice((W * 0.06, -H * 0.74, W * 0.94, H * 0.86), 0, 180, fill=(70, 120, 50, 255)),
        d.ellipse((W * 0.42, H * 0.18, W * 0.58, H * 0.6), outline=(240, 245, 240, 255), width=2 * SS)))
    lit('ds_drink', (150, 40, 54), (230, 110, 120))
    g = new_gun('perk_deadshot')
    for x, z in ((3.6, 4.4), (12.4, 4.4), (3.6, 11.6), (12.4, 11.6)):
        box(g, x - 0.5, 0, z - 0.5, x + 0.5, 5.4, z + 0.5, 'ds_dark', name='leg')
    box(g, 2.5, 5, 3, 13.5, 17, 13, 'ds_dark', name='cooler')
    box(g, 2.2, 16.6, 2.8, 13.8, 17.6, 13.2, 'ds_light', name='lid')
    plate(g, 3.2, 9.4, 12.8, 15.6, 3.0, 'ds_sign')
    box(g, 4.6, 6, 2.2, 11.4, 9.2, 3.0, 'ds_light', name='tray')
    box(g, 5, 6.4, 2.1, 11, 8.6, 3.0, 'coin_dark', name='tray_recess')
    box(g, 6.4, 8.8, 2.4, 7.2, 9.6, 3.0, STEEL, name='tap')
    box(g, 8.8, 8.8, 2.4, 9.6, 9.6, 3.0, STEEL, name='tap')
    gx = 9.4
    g.cyl('y', (gx, C), 0.6, 17.4, 21.4, 'glass_pale', seg=8, name='stem')
    for r, y0, y1 in ((1.4, 20.8, 21.6), (2.6, 21.6, 22.4), (3.6, 22.4, 23.4), (4.4, 23.4, 24.6), (4.8, 24.6, 26.2)):
        g.cyl('y', (gx, C), r, y0, y1, 'glass_pale', seg=16, name='glass')
    g.cyl('y', (gx, C), 4.4, 25.6, 26.5, 'ds_drink', seg=16, name='drink')
    box(g, 11.6, 17, 12, 13, 24.4, 13.6, 'ds_dark', name='mount')
    box(g, 8.4, 24, 13.2, 15.4, 28.4, 13.6, 'ds_emblem', faces={'north': 'ds_emblem'}, name='wedge')
    return finish(g)


def mule_kick():
    paint('mk_green', (50, 112, 62), wear=0.04)
    paint('mk_cream', (214, 206, 162))
    paint('mk_brown', (104, 52, 36))
    paint('mk_plinth', (206, 204, 128))
    def banner(d, W, H):
        rect(d, 0, 0, 1, 1, (120, 36, 30, 255), W, H)
        for k in range(0, 12, 2):
            poly(d, [(0.5, 1.0), (k / 12, 0), ((k + 1) / 12, 0)], (214, 170, 100, 255), W, H)
        text_center(d, W, H, "Mule Kick", 'script', 22, (250, 240, 210, 255), cy=0.5, stroke=1, stroke_fill=(90, 30, 24, 255))
    graphic('mk_banner', 9.2, 2.6, banner)
    graphic('mk_door', 5, 11, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (92, 40, 34, 255), W, H, 0.05),
        rect(d, 0.08, 0.04, 0.92, 0.96, (130, 56, 44, 255), W, H, 0.05),
        d.ellipse((W * 0.2, H * 0.18, W * 0.8, H * 0.3), fill=(230, 214, 170, 255)),
        d.pieslice((W * 0.32, H * 0.1, W * 0.68, H * 0.2), 180, 360, fill=(230, 214, 170, 255)),
        d.line([(W * 0.25, H * 0.34), (W * 0.75, H * 0.52)], fill=(230, 214, 170, 255), width=3 * SS),
        d.line([(W * 0.75, H * 0.34), (W * 0.25, H * 0.52)], fill=(230, 214, 170, 255), width=3 * SS),
        text_center(d, W, H, "UNO MAS", 'block', 8, (230, 214, 170, 255), cy=0.82)))
    graphic('mk_dots', 3.8, 9, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (206, 198, 150, 255), W, H),
        [d.ellipse((W * 0.2, H * (0.05 + i * 0.16), W * 0.8, H * (0.05 + i * 0.16) + W * 0.6), fill=(150, 150, 120, 255) if i % 2 else (60, 110, 70, 255)) for i in range(6)]))
    graphic('mk_emblem', 7.2, 7.2, emblem(0, (150, 70, 50, 255), (250, 250, 245, 255), (250, 250, 245, 255), sym_mule, (40, 130, 60, 255), (250, 250, 245, 255)))
    g = new_gun('perk_mule_kick')
    box(g, 0.5, 0, 1.5, 15.5, 2, 14.5, 'mk_plinth', name='plinth')
    box(g, 2.5, 2, 3, 13.5, 8.5, 13, 'mk_green', name='body_lower')
    box(g, 2.5, 8.5, 3, 13.5, 18, 13, 'mk_cream', name='body_upper')
    arch(g, C, 18, 5.5, 3, 13, 'mk_brown')
    box(g, 2.5, 18, 3, 13.5, 19, 13, 'mk_brown', name='body_cap')
    plate(g, 3.4, 17.4, 12.6, 20.0, 3.0, 'mk_banner')
    plate(g, 3.2, 8.4, 7.2, 17.2, 3.0, 'mk_dots')
    plate(g, 8, 5.4, 13, 16.4, 3.0, 'mk_door')
    vents(g, 3.4, 12.6, 2.6, 5.2, 3.0, 'mk_green', 'green_light', n=3)
    lamp_stalk(g, C, 22, 24.2)
    emblem_lamp(g, C, 27, 6.9, 3.6, CHROME, 'mk_emblem')
    return finish(g)


def electric_cherry():
    paint('ec_blue', (46, 84, 132), wear=0.05)
    paint('ec_rust', (118, 76, 42), wear=0.06)
    paint('ec_dark', (46, 46, 52), wear=0.05)
    graphic('ec_emblem', 8.4, 8.4, emblem(0, (230, 230, 235, 255), (40, 90, 170, 255), (44, 96, 190, 255), sym_cherry, (214, 36, 36, 255), (90, 200, 90, 255)))
    graphic('ec_front', 9.4, 9, logo_text((50, 50, 56, 255), [("5¢", 'serif', 60, (222, 214, 190, 255), 0.5)]))
    graphic('ec_window', 9.6, 2.6, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (196, 180, 150, 255), W, H),
        rect(d, 0.04, 0.12, 0.96, 0.88, (150, 56, 48, 255), W, H),
        [d.ellipse((W * (0.2 + i * 0.2), H * 0.3, W * (0.3 + i * 0.2), H * 0.7), fill=(220, 120, 100, 255)) for i in range(4)]))
    lit('blue_coil', (50, 120, 230), (210, 240, 255))
    g = new_gun('perk_electric_cherry')
    box(g, 1.5, 0, 2.5, 14.5, 1.6, 13.5, 'ec_dark', name='plinth')
    box(g, 2, 1.6, 3, 14, 13.5, 13, 'ec_rust', name='cabinet')
    plate(g, 3, 2.6, 13, 12.4, 3.0, 'ec_front')
    box(g, 1.7, 13.5, 2.8, 14.3, 14.5, 13.2, 'ec_dark', name='shelf')
    plate(g, 3, 14.5, 13, 17.2, 3.4, 'ec_window')
    box(g, 3, 14.5, 3.4, 13, 17.2, 13, 'ec_blue', name='window_body')
    box(g, 2.6, 17.2, 3.4, 4, 25, 12.6, 'ec_blue', name='post_l')
    box(g, 12, 17.2, 3.4, 13.4, 25, 12.6, 'ec_blue', name='post_r')
    arch(g, C, 24, 5.4, 3.4, 12.6, 'ec_blue')
    arch(g, C, 21.8, 4.7, 3.0, 3.4, 'ec_dark')
    emblem_lamp(g, C, 21.8, 3.0, 4.0, CHROME, 'ec_emblem', depth=1.4)
    for y in (17.6, 19.6, 21.6):
        box(g, 1.4, y, 7, 2.6, y + 0.8, 9, 'blue_coil', name='coil')
        box(g, 13.4, y, 7, 14.6, y + 0.8, 9, 'blue_coil', name='coil')
    return finish(g)


def tombstone():
    paint('stone', (136, 136, 130), wear=0.0, streak=0.02, amount=0.13)
    paint('stone_dark', (84, 84, 82), wear=0.0, streak=0.02, amount=0.12)
    graphic('tomb_logo', 7.2, 4.2, logo_text((196, 192, 176, 255), [("Tombstone", 'serif', 26, (60, 60, 56, 255), 0.35), ("SODA", 'block', 22, (60, 90, 50, 255), 0.75)]))
    graphic('tomb_emblem', 6.6, 6.6, emblem(0, (170, 170, 164, 255), (110, 140, 90, 255), (200, 196, 180, 255), sym_tomb, (70, 70, 66, 255), (200, 196, 180, 255)))
    graphic('tomb_lights', 2.8, 11, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (60, 74, 60, 255), W, H),
        [d.ellipse((W * 0.14, H * (0.03 + i * 0.16), W * 0.86, H * (0.03 + i * 0.16) + W * 0.72), fill=(120, 230, 120, 255), outline=(30, 60, 30, 255), width=SS) for i in range(6)]))
    g = new_gun('perk_tombstone')
    box(g, 1.5, 0, 2.5, 14.5, 1.6, 13.5, 'stone_dark', name='plinth')
    box(g, 2, 1.6, 3, 14, 20, 13, 'stone', name='body')
    arch(g, C, 20, 6, 3, 13, 'stone')
    box(g, 1.7, 5.8, 2.8, 14.3, 6.4, 13.2, 'stone_dark', name='band')
    vents(g, 2.8, 13.2, 1.8, 5.4, 3.0, 'stone_dark', 'white_light', n=3)
    coin_panel(g, 3, 7.4, 8.4, 17.4, 3.0, 'coin_dark', STEEL, 'green_light')
    plate(g, 9, 7.2, 12.2, 18.4, 3.0, 'tomb_lights')
    plate(g, 4.4, 19.8, 11.6, 24, 3.0, 'tomb_logo')
    emblem_lamp(g, C, 28, 6.8, 3.3, 'stone_dark', 'tomb_emblem', depth=2.4)
    return finish(g)


def lifebuoy(g, r_in, r_out, y0, y1):
    """Octagonal ring of eight boxes around the body, alternating white and red segments."""
    rm, t = (r_in + r_out) / 2, r_out - r_in
    length = 2 * rm * math.tan(math.pi / 8) + t * 0.45
    for k in range(8):
        phi = k * 45
        cx, cz = C + rm * math.cos(math.radians(phi)), C + rm * math.sin(math.radians(phi))
        mat = 'ww_red' if k % 2 == 0 else 'ww_white'
        if phi % 90 == 0:
            alongz = phi % 180 == 0
            hx, hz = (t / 2, length / 2) if alongz else (length / 2, t / 2)
            g.box((cx - hx, y0, cz - hz), (cx + hx, y1, cz + hz), mat, name='lifebuoy')
        else:
            ang = -45 if phi in (45, 225) else 45
            g.box((cx - t / 2, y0, cz - length / 2), (cx + t / 2, y1, cz + length / 2), mat, rot=('y', ang, (cx, (y0 + y1) / 2, cz)), name='lifebuoy')


def whos_who():
    paint('ww_blue', (44, 104, 164), wear=0.04)
    paint('ww_yellow', (228, 172, 40))
    paint('ww_white', (226, 228, 224))
    paint('ww_red', (196, 44, 36))
    graphic('ww_emblem', 4.4, 4.4, emblem(0, (230, 230, 230, 255), (40, 90, 160, 255), (240, 240, 240, 255), sym_seven, (40, 100, 170, 255), (230, 60, 40, 255)))
    graphic('ww_bottle', 1.8, 3.6, lambda d, W, H: (
        rect(d, 0, 0, 1, 1, (44, 104, 164, 255), W, H),
        rect(d, 0.3, 0.45, 0.7, 0.9, (246, 240, 220, 255), W, H, 0.05),
        rect(d, 0.4, 0.15, 0.6, 0.5, (246, 240, 220, 255), W, H)))
    g = new_gun('perk_whos_who')
    box(g, 3, 0, 3, 13, 1.4, 13, 'ww_yellow', name='plinth')
    g.cyl('y', (C, C), 5, 1.4, 20, 'ww_blue', seg=8, name='barrel')
    box(g, 5.4, 1.4, 2.7, 6.2, 20, 3.2, 'ww_yellow', name='stripe_l')
    box(g, 9.8, 1.4, 2.7, 10.6, 20, 3.2, 'ww_yellow', name='stripe_r')
    plate(g, 6.4, 5.6, 9.6, 9, 3.0, 'ww_bottle')
    plate(g, 6, 15.2, 10, 19.2, 2.9, 'ww_emblem')
    g.cyl('y', (C, C), 4.2, 20, 21.4, 'ww_yellow', seg=8, name='shoulder')
    lifebuoy(g, 5.2, 7.5, 10.8, 13.2)
    for r, y0, y1 in ((3.5, 21.4, 22.4), (3.2, 22.4, 23.4), (2.6, 23.4, 24.2), (1.8, 24.2, 24.9), (0.9, 24.9, 25.4)):
        g.cyl('y', (C, C), r, y0, y1, 'glass_green', seg=16, name='dome')
    box(g, 7.7, 21.4, 7.7, 8.3, 26.6, 8.3, 'ww_blue', name='antenna')
    return finish(g)


def vulture_aid():
    paint('va_green', (74, 92, 42), wear=0.05)
    paint('va_brown', (96, 64, 38))
    paint('va_brass', (182, 142, 62), wear=0.03)
    paint('va_cream', (212, 198, 156))
    graphic('va_emblem', 5.4, 5.4, emblem(0, (190, 150, 70, 255), (250, 245, 230, 255), (96, 116, 60, 255), sym_vulture, (240, 230, 190, 255), (240, 190, 70, 255)))
    graphic('va_vpanel', 6.4, 8.4, lambda d, W, H: (
        poly(d, [(0.0, 0.0), (1.0, 0.0), (1.0, 0.3), (0.5, 1.0), (0.0, 0.3)], (214, 200, 158, 255), W, H),
        poly(d, [(0.1, 0.06), (0.9, 0.06), (0.9, 0.28), (0.5, 0.88), (0.1, 0.28)], (200, 184, 140, 255), W, H)))
    g = new_gun('perk_vulture_aid')
    for x, z in ((3.5, 4.5), (12.5, 4.5), (3.5, 11.5), (12.5, 11.5)):
        box(g, x - 0.6, 0, z - 0.6, x + 0.6, 3, z + 0.6, 'va_brass', name='foot')
    box(g, 2.5, 3, 3, 13.5, 3.8, 13, 'va_brass', name='base_trim')
    box(g, 2.5, 3.8, 3, 13.5, 17, 13, 'va_green', name='body')
    for x0, y0, y1 in ((3, 17, 18.6), (3.8, 18.6, 20.2), (4.6, 20.2, 21.6)):
        box(g, x0, y0, 3.2, 16 - x0, y1, 12.8, 'va_green', name='shoulder')
    arch(g, C, 21.6, 3.4, 3.2, 12.8, 'va_green')
    plate(g, 4.8, 11.6, 11.2, 20.0, 3.0, 'va_vpanel')
    emblem_lamp(g, C, 20.4, 2.9, 2.7, 'va_brass', 'va_emblem', depth=1.2)
    box(g, 4.5, 6.6, 2.4, 11.5, 8.4, 3.0, 'amber_light', name='slot')
    box(g, 4.2, 6.2, 2.2, 11.8, 6.7, 3.0, 'va_brass', name='slot_lip')
    box(g, 4, 9.8, 2.6, 5.6, 10.8, 3.0, 'va_brass', name='coin_a')
    box(g, 10.4, 9.8, 2.6, 12, 11.2, 3.0, 'va_cream', name='coin_b')
    box(g, 13.5, 7, 6.5, 14.8, 14, 9.5, 'va_brass', name='pump')
    box(g, 14.4, 12, 7.2, 15.2, 15, 7.8, 'va_brass', name='pump_handle')
    box(g, 7.4, 24.6, 7.4, 8.6, 25.8, 8.6, 'va_brass', name='bulb_stem')
    g.cyl('y', (C, C), 1.0, 25.8, 27.4, 'va_cream', seg=8, name='bulb')
    return finish(g)


BUILDERS = {
    'juggernog': juggernog, 'speed_cola': speed_cola, 'double_tap': double_tap, 'quick_revive': quick_revive,
    'stamin_up': stamin_up, 'phd_flopper': phd_flopper, 'deadshot': deadshot, 'mule_kick': mule_kick,
    'electric_cherry': electric_cherry, 'tombstone': tombstone, 'whos_who': whos_who, 'vulture_aid': vulture_aid,
}

if __name__ == '__main__':
    keys = sys.argv[1:] or list(BUILDERS)
    for k in keys:
        BUILDERS[k]()
