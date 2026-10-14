#!/usr/bin/env python3
"""Mystery box (machines/mystery_box.json): the long BO weapon crate on two timber stands, plank sides,
iron straps and latch with chains, and two glowing "?" marks on the lid. Two blocks wide (x -8..24),
front towards -Z, centre x = 8, z = 8."""
import random

from PIL import ImageChops, ImageFilter

from mkit import *  # noqa: F401,F403

paint('crate_dark', (44, 32, 24), wear=0.03)
paint('iron', (46, 48, 54), wear=0.05)
paint('brass_dull', (170, 134, 66), wear=0.03)
timber('crate_wood', (86, 60, 40), 0.2)
GLOW = (225, 248, 255, 255)
HALO = (70, 170, 255)


def planks(d, W, H, seed=3, lid=False):
    rng = random.Random(seed)
    rows = max(2, int(H / (2.4 * DENSITY)))
    for i in range(rows):
        y0, y1 = i * H / rows, (i + 1) * H / rows
        base = (88 + rng.randint(-10, 10), 62 + rng.randint(-8, 8), 42 + rng.randint(-6, 6))
        d.rectangle((0, y0, W, y1), fill=base + (255,))
        for _ in range(int(W / (10 * SS))):
            gx = rng.uniform(0, W)
            d.line([(gx, y0 + 1), (gx + rng.uniform(8, 40) * SS, y0 + rng.uniform(0, 2) * SS)], fill=(base[0] - 24, base[1] - 18, base[2] - 12, 255), width=SS)
        d.line([(0, y1 - SS), (W, y1 - SS)], fill=(26, 18, 12, 255), width=2 * SS)
        for nx in (0.02, 0.98):
            r = 1.2 * SS
            d.ellipse((W * nx - r, (y0 + y1) / 2 - r, W * nx + r, (y0 + y1) / 2 + r), fill=(40, 40, 44, 255))
    if lid:
        d.rectangle((0, 0, W - 1, H - 1), outline=(34, 24, 18, 255), width=3 * SS)


def glow_marks(d, W, H, centres, size, flip=()):
    """Bright light-blue/white ? marks with a soft blue halo, drawn over the planks."""
    marks = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for cx in centres:
        layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        text_center(ImageDraw.Draw(layer), W, H, "?", 'block', H / SS * size, GLOW, cx=0.5, cy=0.5, fit=1.0)
        if cx in flip:
            layer = layer.rotate(180)
        marks.alpha_composite(ImageChops.offset(layer, int((cx - 0.5) * W), 0))
    alpha = marks.getchannel('A')
    halo = Image.new('RGBA', (W, H), HALO + (0,))
    halo.putalpha(alpha.filter(ImageFilter.GaussianBlur(5 * SS)).point(lambda v: min(255, v * 7 // 8)))
    d.canvas.alpha_composite(halo)
    d.canvas.alpha_composite(marks)


def qmarks(d, W, H):
    planks(d, W, H, seed=9, lid=True)
    glow_marks(d, W, H, (0.27, 0.73), 0.62, flip=(0.73,))


def front_q(d, W, H):
    planks(d, W, H, seed=5)
    glow_marks(d, W, H, (0.2, 0.8), 0.8)


def end_q(d, W, H):
    planks(d, W, H, seed=7)
    glow_marks(d, W, H, (0.5,), 0.8)


graphic('crate_front', 30.5, 7.4, front_q)
graphic('crate_back', 30.5, 7.4, lambda d, W, H: planks(d, W, H, seed=6))
graphic('crate_end', 13.6, 7.4, end_q)
graphic('crate_lid', 31.4, 14.6, qmarks, rotate=180)
lit('qglow', (60, 160, 255), (235, 250, 255))


def chain(g, x0, x1, y_top, sag, z=0.2, links=7):
    for i in range(links):
        t = i / (links - 1)
        x = x0 + (x1 - x0) * t
        y = y_top - sag * 4 * t * (1 - t)
        if i % 2:
            box(g, x - 0.8, y - 0.8, z, x + 0.8, y + 0.8, z + 0.5, 'steel', name='chain')
        else:
            box(g, x - 0.5, y - 0.95, z + 0.05, x + 0.5, y + 0.95, z + 0.45, 'chrome', name='chain')


def chain(g, x0, x1, y_top, sag, z=0.2, links=7):
    for i in range(links):
        t = i / (links - 1)
        x = x0 + (x1 - x0) * t
        y = y_top - sag * 4 * t * (1 - t)
        if i % 2:
            box(g, x - 0.8, y - 0.8, z, x + 0.8, y + 0.8, z + 0.5, 'steel', name='chain')
        else:
            box(g, x - 0.5, y - 0.95, z + 0.05, x + 0.5, y + 0.95, z + 0.45, 'chrome', name='chain')


def build():
    g = new_gun('mystery_box')
    for x0 in (-6, 17):
        box(g, x0, 0, 3, x0 + 5, 3, 13, 'crate_dark', name='stand')
    box(g, -7.2, 3, 1.2, 23.2, 10.5, 14.8, 'crate_wood', faces={'north': 'crate_front', 'south': 'crate_back', 'east': 'crate_end', 'west': 'crate_end'}, name='crate')
    box(g, -8, 10.5, 0.4, 24, 13, 15.6, 'crate_dark', name='lid_frame')
    box(g, -7.4, 13, 0.9, 23.4, 13.2, 15.1, 'crate_lid', faces={'up': 'crate_lid'}, name='lid_top')
    for x0 in (-4.2, 18.8):
        box(g, x0, 3, 0.55, x0 + 1.4, 13.2, 1.3, 'iron', name='strap_front')
        box(g, x0, 13, 0.55, x0 + 1.4, 13.4, 15.45, 'iron', name='strap_top')
        box(g, x0, 3, 14.9, x0 + 1.4, 13.2, 15.45, 'iron', name='strap_back')
    for x0 in (-7.7, 22.0):
        box(g, x0, 3, 0.7, x0 + 1.7, 13.3, 1.5, 'iron', name='corner_front')
        box(g, x0, 3, 14.5, x0 + 1.7, 13.3, 15.3, 'iron', name='corner_back')
        box(g, x0, 3.2, 0.7, x0 + 1.7, 4.4, 4.2, 'iron', name='corner_foot')
        box(g, x0, 3.2, 11.8, x0 + 1.7, 4.4, 15.3, 'iron', name='corner_foot')
        for z0 in (0.6, 12.6):
            box(g, x0 - 0.1, 13.2, z0, x0 + 1.8, 13.5, z0 + 2.4, 'iron', name='strap_top')
    box(g, 5.6, 9, 0.3, 10.4, 12.8, 0.7, 'steel', name='latch_plate')
    box(g, 7.2, 6.6, 0.0, 8.8, 9.2, 0.7, 'brass_dull', name='padlock')
    box(g, 7.7, 9.2, 0.2, 8.3, 9.9, 0.7, 'steel', name='padlock_shackle')
    box(g, 7.7, 12.8, 0.5, 8.3, 13.4, 1.2, 'steel', name='hasp')
    chain(g, 2.6, 5.9, 10.0, 1.2, links=5)
    chain(g, 10.1, 13.4, 10.0, 1.2, links=5)
    return save(g)


if __name__ == '__main__':
    build()
