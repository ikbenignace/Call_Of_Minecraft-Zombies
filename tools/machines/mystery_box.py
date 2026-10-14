#!/usr/bin/env python3
"""Mystery box (machines/mystery_box.json): the long BO weapon crate on two timber stands, plank sides,
iron straps and latch with chains, and two glowing "?" marks on the lid. Two blocks wide (x -8..24),
front towards -Z, centre x = 8, z = 8."""
import random

from PIL import ImageChops, ImageFilter

from mkit import *  # noqa: F401,F403

paint('crate_dark', (74, 52, 34), wear=0.03)
paint('iron', (52, 54, 58), wear=0.04)
paint('brass_dull', (150, 118, 60), wear=0.03)
timber('crate_wood', (146, 100, 58), 0.2)


def planks(d, W, H, seed=3, lid=False):
    rng = random.Random(seed)
    rows = max(2, int(H / (2.4 * DENSITY)))
    for i in range(rows):
        y0, y1 = i * H / rows, (i + 1) * H / rows
        base = (150 + rng.randint(-14, 14), 102 + rng.randint(-10, 10), 58 + rng.randint(-8, 8))
        d.rectangle((0, y0, W, y1), fill=base + (255,))
        for _ in range(int(W / (10 * SS))):
            gx = rng.uniform(0, W)
            d.line([(gx, y0 + 1), (gx + rng.uniform(8, 40) * SS, y0 + rng.uniform(0, 2) * SS)], fill=(base[0] - 28, base[1] - 22, base[2] - 14, 255), width=SS)
        d.line([(0, y1 - SS), (W, y1 - SS)], fill=(52, 34, 22, 255), width=2 * SS)
        for nx in (0.02, 0.98):
            r = 1.2 * SS
            d.ellipse((W * nx - r, (y0 + y1) / 2 - r, W * nx + r, (y0 + y1) / 2 + r), fill=(60, 56, 54, 255))
    if lid:
        d.rectangle((0, 0, W - 1, H - 1), outline=(70, 46, 30, 255), width=3 * SS)


def qmarks(d, W, H):
    planks(d, W, H, seed=9, lid=True)
    marks = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for cx, flip in ((0.27, False), (0.73, True)):
        layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        text_center(ImageDraw.Draw(layer), W, H, "?", 'block', H / SS * 0.62, (255, 206, 60, 255), cx=0.5, cy=0.5, fit=1.0)
        if flip:
            layer = layer.rotate(180)
        marks.alpha_composite(ImageChops.offset(layer, int((cx - 0.5) * W), 0))
    alpha = marks.getchannel('A')
    halo = Image.new('RGBA', (W, H), (255, 150, 30, 0))
    halo.putalpha(alpha.filter(ImageFilter.GaussianBlur(5 * SS)).point(lambda v: min(255, v * 3 // 4)))
    d.canvas.alpha_composite(halo)
    d.canvas.alpha_composite(marks)


graphic('crate_front', 30.5, 7.4, lambda d, W, H: planks(d, W, H, seed=5))
graphic('crate_back', 30.5, 7.4, lambda d, W, H: planks(d, W, H, seed=6))
graphic('crate_lid', 31.4, 14.6, qmarks, rotate=180)
lit('qglow', (230, 150, 30), (255, 240, 170))


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
    box(g, -7.5, 3, 1, 23.5, 10.5, 15, 'crate_wood', faces={'north': 'crate_front', 'south': 'crate_back'}, name='crate')
    box(g, -8, 10.5, 0.4, 24, 13, 15.6, 'crate_dark', name='lid_frame')
    box(g, -7.4, 13, 0.9, 23.4, 13.2, 15.1, 'crate_lid', faces={'up': 'crate_lid'}, name='lid_top')
    for x0 in (-4.2, 18.8):
        box(g, x0, 3, 0.55, x0 + 1.4, 13.2, 1, 'iron', name='strap_front')
        box(g, x0, 13, 0.55, x0 + 1.4, 13.4, 15.45, 'iron', name='strap_top')
        box(g, x0, 3, 15, x0 + 1.4, 13.2, 15.45, 'iron', name='strap_back')
    for x0 in (-7.6, 22.1):
        box(g, x0, 3, 0.7, x0 + 1.5, 13.3, 1.4, 'iron', name='corner_front')
        box(g, x0, 3, 14.6, x0 + 1.5, 13.3, 15.3, 'iron', name='corner_back')
    box(g, 5.6, 9, 0.3, 10.4, 12.8, 0.7, 'steel', name='latch_plate')
    box(g, 7.4, 7, 0.1, 8.6, 9.2, 0.7, 'brass_dull', name='padlock')
    box(g, 7.7, 12.8, 0.5, 8.3, 13.4, 1.2, 'steel', name='hasp')
    chain(g, -3.5, 5.6, 9.6, 3.2, links=8)
    chain(g, 10.4, 19.5, 9.6, 3.2, links=8)
    return save(g)


if __name__ == '__main__':
    build()
