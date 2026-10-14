#!/usr/bin/env python3
"""Paints the Black Ops style zombie skin (64x64, standard Minecraft zombie UV layout).

Usage: python3 tools/textures/zombie_skin.py [out.png] [preview.png]
Defaults write the pack texture and /tmp/bb/zombie_skin_preview.png. Deterministic (fixed seed).
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
PACK_OUT = os.path.join(ROOT, "resourcepack/comz-pack/assets/minecraft/textures/entity/zombie/zombie.png")
PREVIEW_OUT = "/tmp/bb/zombie_skin_preview.png"

SKIN = (92, 108, 84)
SKIN_DARK = (62, 76, 58)
SKIN_ROT = (78, 88, 60)
CLOTH = (58, 62, 54)
CLOTH_DARK = (36, 39, 34)
CLOTH_LIGHT = (74, 79, 68)
LEATHER = (22, 20, 18)
BLOOD = (96, 14, 12)
BLOOD_DARK = (58, 8, 8)
EYE = (255, 168, 30)
EYE_HOT = (255, 226, 110)
BUTTON = (150, 140, 90)

rng = random.Random(1944)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


def fill(x, y, w, h, base, jitter=7):
    for j in range(y, y + h):
        for i in range(x, x + w):
            px[i, j] = shade(base, rng.randint(-jitter, jitter)) + (255,)


def put(x, y, c):
    px[x, y] = tuple(c) + (255,)


def splat(x, y, w, h, color, count):
    for _ in range(count):
        put(x + rng.randrange(w), y + rng.randrange(h), shade(color, rng.randint(-10, 10)))


def box(x, y, w, h, d):
    """Face rectangles of a UV-unwrapped box whose top-left unwrap corner is (x, y)."""
    return {
        "top": (x + d, y, w, d),
        "bottom": (x + d + w, y, w, d),
        "right": (x, y + d, d, h),
        "front": (x + d, y + d, w, h),
        "left": (x + d + w, y + d, d, h),
        "back": (x + 2 * d + w, y + d, w, h),
    }


def paint_part(origin, w, h, d, painter):
    for name, rect in box(origin[0], origin[1], w, h, d).items():
        painter(name, *rect)


def head(name, x, y, w, h):
    fill(x, y, w, h, SKIN)
    splat(x, y, w, h, SKIN_DARK, w * h // 5)
    splat(x, y, w, h, SKIN_ROT, w * h // 8)
    if name == "front":
        for ex in (1, 5):
            fill(x + ex - 1 if ex > 1 else x + ex, y + 3, 2, 3, (22, 26, 20), 3)
            put(x + ex, y + 4, EYE)
            put(x + ex + (1 if ex == 1 else 0), y + 4, EYE_HOT if ex == 1 else EYE)
        fill(x + 2, y + 6, 4, 1, BLOOD_DARK, 4)
        fill(x + 3, y + 7, 2, 1, BLOOD, 4)
        fill(x + 3, y + 6, 2, 1, (210, 205, 185), 2)
        put(x + 3, y + 2, BLOOD)
        put(x + 3, y + 3, BLOOD_DARK)
        put(x + 6, y + 5, BLOOD_DARK)
    if name in ("right", "left", "back"):
        splat(x, y, w, h, BLOOD_DARK, 5)


def helmet(name, x, y, w, h):
    """Hat layer: a battered steel helmet covering the crown and the upper rows of the head."""
    rows = {"top": h, "front": 3, "back": 4, "right": 4, "left": 4}.get(name, 0)
    if not rows:
        return
    fill(x, y, w, rows, (70, 76, 66), 6)
    if name == "top":
        for i in range(w):
            put(x + i, y + h // 2, (50, 55, 48))
    else:
        for i in range(w):
            put(x + i, y + rows - 1, (30, 33, 29))
    splat(x, y, w, rows, (110, 76, 50), 3)
    if name == "front":
        put(x + 5, y + 1, (190, 190, 180))
        put(x + 2, y + 2, BLOOD_DARK)
    if name == "right":
        for i in range(2, 5):
            put(x + i, y + 1, (96, 96, 90))


def body(name, x, y, w, h):
    fill(x, y, w, h, CLOTH)
    splat(x, y, w, h, CLOTH_DARK, w * h // 4)
    splat(x, y, w, h, CLOTH_LIGHT, w * h // 10)
    if name == "front":
        for j in range(y, y + h):
            put(x + 3, j, shade(CLOTH_DARK, -6))
            put(x + 4, j, shade(CLOTH_DARK, -6))
        for j in (y + 2, y + 5, y + 8):
            put(x + 3, j, BUTTON)
            put(x + 4, j, BUTTON)
        fill(x, y + h - 3, w, 1, LEATHER, 2)
        put(x + 3, y + h - 3, BUTTON)
        put(x + 4, y + h - 3, BUTTON)
        fill(x + 5, y + 3, 2, 3, (96, 80, 72), 5)
        fill(x + 5, y + 4, 2, 1, SKIN_DARK, 3)
        put(x + 1, y + 2, (150, 150, 140))
        put(x + 1, y + 3, (150, 150, 140))
        splat(x, y + 3, w, 6, BLOOD, 14)
        fill(x + 2, y + 5, 2, 3, BLOOD_DARK, 5)
    elif name == "back":
        fill(x, y + h - 3, w, 1, LEATHER, 2)
        splat(x, y, w, h, BLOOD_DARK, 8)
    elif name in ("top", "bottom"):
        fill(x, y, w, h, CLOTH_DARK, 4)


def arm(name, x, y, w, h):
    sleeve = 6
    fill(x, y, w, h, SKIN)
    splat(x, y, w, h, SKIN_DARK, w * h // 4)
    if name in ("top",):
        fill(x, y, w, h, CLOTH)
        return
    if name == "bottom":
        fill(x, y, w, h, SKIN_DARK)
        return
    fill(x, y, w, sleeve, CLOTH)
    splat(x, y, w, sleeve, CLOTH_DARK, 6)
    for i in range(w):
        if rng.random() < 0.55:
            put(x + i, y + sleeve - 1, SKIN_ROT)
        put(x + i, y + sleeve, shade(SKIN_DARK, -10))
    splat(x, y + sleeve, w, h - sleeve, BLOOD, 7)
    splat(x, y + sleeve, w, h - sleeve, BLOOD_DARK, 4)
    if name == "front":
        fill(x + 1, y + 1, 2, 1, (150, 150, 140), 4)


def leg(name, x, y, w, h):
    fill(x, y, w, h, CLOTH_DARK)
    splat(x, y, w, h, CLOTH, w * h // 6)
    if name in ("top", "bottom"):
        return
    fill(x, y + h - 3, w, 3, LEATHER, 3)
    for i in range(w):
        if rng.random() < 0.5:
            put(x + i, y + h - 4, SKIN_ROT)
    splat(x, y, w, h - 3, BLOOD_DARK, 6)
    if name == "front":
        fill(x + 1, y + 4, 2, 2, SKIN_ROT, 4)


paint_part((0, 0), 8, 8, 8, head)
paint_part((32, 0), 8, 8, 8, helmet)
paint_part((16, 16), 8, 12, 4, body)
paint_part((40, 16), 4, 12, 4, arm)
paint_part((32, 48), 4, 12, 4, arm)
paint_part((0, 16), 4, 12, 4, leg)
paint_part((16, 48), 4, 12, 4, leg)


def preview(path, scale=8):
    """Assembled front and back views of the unfolded skin."""
    def face(part_origin, w, h, d, which):
        x, y, fw, fh = box(part_origin[0], part_origin[1], w, h, d)[which]
        return img.crop((x, y, x + fw, y + fh))

    canvas = Image.new("RGBA", (36, 34), (34, 34, 40, 255))
    for k, view in enumerate(("front", "back")):
        ox = 2 + k * 18
        parts = [
            ((0, 0), 8, 8, 8, (ox + 4, 1)),
            ((32, 0), 8, 8, 8, (ox + 4, 1)),
            ((16, 16), 8, 12, 4, (ox + 4, 9)),
            ((40, 16), 4, 12, 4, (ox + 0, 9) if view == "front" else (ox + 12, 9)),
            ((32, 48), 4, 12, 4, (ox + 12, 9) if view == "front" else (ox + 0, 9)),
            ((0, 16), 4, 12, 4, (ox + 4, 21) if view == "front" else (ox + 8, 21)),
            ((16, 48), 4, 12, 4, (ox + 8, 21) if view == "front" else (ox + 4, 21)),
        ]
        for origin, w, h, d, pos in parts:
            f = face(origin, w, h, d, view)
            canvas.alpha_composite(f, pos)
    canvas = canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST)
    sheet = Image.new("RGBA", (canvas.width + 64 * 4 + 16, max(canvas.height, 256)), (34, 34, 40, 255))
    sheet.alpha_composite(canvas, (0, 0))
    flat = Image.new("RGBA", (64, 64), (34, 34, 40, 255))
    flat.alpha_composite(img)
    sheet.alpha_composite(flat.resize((256, 256), Image.NEAREST), (canvas.width + 16, 0))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.convert("RGB").save(path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else PACK_OUT
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    preview(sys.argv[2] if len(sys.argv) > 2 else PREVIEW_OUT)
    print("wrote", out)
