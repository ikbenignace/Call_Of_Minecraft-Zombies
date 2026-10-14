#!/usr/bin/env python3
"""Pack-a-Punch machine (machines/pap.json): low barrel-shaped teal body on splayed legs with three
rollers behind a front window, the lit "PACK A PUNCH" sign on posts above and the gun emblem under it.
Front towards -Z; overhangs the block to x -4..20, z 1..15 (contract limit -2..18)."""
from mkit import *  # noqa: F401,F403

C = 8
paint('pap_teal', (52, 128, 150), wear=0.05)
paint('pap_teal_dark', (30, 78, 96), wear=0.04)
paint('pap_brass', (176, 138, 62), wear=0.03)
paint('pap_black', (24, 26, 30))
MATERIALS['roller_rib'] = Material(ribs((176, 168, 150), count=10, vertical=True, depth=0.5), mode='stretch')
lit('pap_bulb', (230, 190, 90), (255, 250, 200))
PASTEL = [(222, 96, 120), (86, 170, 200), (230, 190, 60), (110, 190, 110), (214, 120, 200), (230, 130, 70), (90, 150, 220), (200, 90, 90), (120, 190, 160), (220, 160, 60)]


def sign(d, W, H):
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=H * 0.12, fill=(30, 78, 96, 255))
    d.rounded_rectangle((W * 0.02, H * 0.06, W * 0.98, H * 0.94), radius=H * 0.1, fill=(226, 232, 214, 255))
    letters = "PACK A PUNCH"
    size = H * 0.46
    f = font('serif', size)
    total = sum(d.textlength(ch, font=f) for ch in letters)
    if total > W * 0.9:
        f = font('serif', size * W * 0.9 / total)
        total = sum(d.textlength(ch, font=f) for ch in letters)
    x = W / 2 - total / 2
    i = 0
    for ch in letters:
        w = d.textlength(ch, font=f)
        if ch != ' ':
            d.text((x, H * 0.14), ch, font=f, fill=PASTEL[i % len(PASTEL)] + (255,), stroke_width=SS, stroke_fill=(70, 70, 80, 255))
            i += 1
        x += w
    text_center(d, W, H, "Increase your firepower!", 'script', H * 0.16, (110, 70, 60, 255), cy=0.82)


def oval(d, W, H):
    d.ellipse((0, 0, W - 1, H - 1), fill=(36, 96, 110, 255))
    d.ellipse((W * 0.05, H * 0.08, W * 0.95, H * 0.92), fill=(236, 230, 205, 255))
    rect(d, 0.22, 0.34, 0.78, 0.5, (60, 60, 64, 255), W, H, 0.1)
    rect(d, 0.3, 0.5, 0.4, 0.78, (60, 60, 64, 255), W, H, 0.1)
    rect(d, 0.7, 0.4, 0.86, 0.46, (60, 60, 64, 255), W, H)


def decal(d, W, H):
    d.ellipse((0, 0, W - 1, H - 1), fill=(36, 20, 50, 255))
    d.ellipse((W * 0.1, H * 0.1, W * 0.9, H * 0.9), fill=(210, 60, 50, 255))
    d.line([(W * 0.28, H * 0.72), (W * 0.72, H * 0.28)], fill=(36, 20, 50, 255), width=3 * SS)
    rect(d, 0.36, 0.38, 0.64, 0.52, (36, 20, 50, 255), W, H, 0.1)


graphic('pap_sign', 17, 6, sign)
graphic('pap_oval', 7, 3.2, oval)
graphic('pap_decal', 2.6, 2.6, decal)


def build():
    g = new_gun('pap')
    # legs and feet
    for x, ang in ((-2.0, 22.5), (17.0, -22.5)):
        for z in (2.6, 12.4):
            cx = 16 - x - 0.6
            g.box((cx - 0.6, 0.6, z), (cx + 0.6, 5.4, z + 1.2), 'pap_teal_dark', rot=('z', ang, (cx, 5.2, z + 0.6)), name='leg')
            fx = cx + (1.8 if cx > 8 else -1.8)
            g.box((fx - 1.4, 0, z - 0.4), (fx + 1.4, 0.8, z + 1.6), 'pap_black', name='foot')
    # hull under the window and the rear wall
    box(g, -2.4, 4.6, 1.4, 18.4, 7, 14.6, 'pap_teal', name='hull')
    box(g, -1.4, 7, 9.4, 17.4, 13.2, 15, 'pap_teal_dark', name='back_wall')
    box(g, -1.4, 6.6, 1, 17.4, 8.4, 15, 'pap_teal', name='sill')
    box(g, 1, 8.4, 1.5, 15, 8.6, 14, 'pap_black', name='floor')
    # end caps give the window its sloped, bulged ends
    for cx in (-1.4, 17.4):
        g.cyl('z', (cx, 10.5), 2.6, 1, 15, 'pap_teal', seg=8, name='end_cap')
    box(g, -1.4, 12.8, 1, 17.4, 14.2, 15, 'pap_teal', name='roof')
    box(g, -1.6, 14.2, 0.8, 17.6, 14.6, 1.4, 'pap_brass', name='roof_trim')
    # the three rollers
    for x in (3.6, 8, 12.4):
        g.cyl('y', (x, 7), 2.1, 8.6, 12.8, 'roller_rib', seg=16, name='roller')
        g.cyl('y', (x, 7), 2.3, 12.5, 12.9, 'steel', seg=16, name='roller_cap')
        g.cyl('y', (x, 7), 2.3, 8.5, 8.9, 'steel', seg=16, name='roller_cap')
    plate(g, 13.2, 4.6, 15.8, 7.2, 1.4, 'pap_decal')
    # sign, posts and emblem
    box(g, 1.4, 14.1, 6.4, 2.6, 19.8, 8, 'pap_teal_dark', name='post')
    box(g, 13.4, 14.1, 6.4, 14.6, 19.8, 8, 'pap_teal_dark', name='post')
    box(g, -0.4, 19.6, 5, 16.4, 26.4, 9.6, 'pap_teal_dark', name='sign_box')
    plate(g, -0.2, 19.8, 16.2, 26.2, 5.0, 'pap_sign', depth=0.2)
    box(g, 7.4, 14.1, 4.2, 8.6, 16.4, 4.8, 'pap_brass', name='emblem_stem')
    plate(g, 4.5, 15.8, 11.5, 19, 4.2, 'pap_oval', depth=0.4)
    return save(g)


if __name__ == '__main__':
    build()
