#!/usr/bin/env python3
"""Paints the Black Ops hellhound wolf skin (64x32, vanilla wolf UV layout).

Usage: python3 tools/textures/hellhound_skin.py [pack_dir] [preview.png]
Writes wolf.png / wolf_tame.png / wolf_angry.png and the same three for every 1.21+ wolf variant under
<pack_dir>/assets/minecraft/textures/entity/wolf/, plus a preview at /tmp/bb/hellhound_preview.png.
Deterministic (fixed seed).
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
PACK = os.path.join(ROOT, "resourcepack/comz-pack")
PREVIEW_OUT = "/tmp/bb/hellhound_preview.png"

VARIANTS = ["", "ashen_", "black_", "chestnut_", "rusty_", "snowy_", "spotted_", "striped_", "woods_"]

FUR = (38, 36, 36)
FUR_DARK = (22, 21, 22)
FUR_LIGHT = (58, 55, 54)
BELLY = (52, 48, 46)
BURN = (12, 10, 10)
ASH = (74, 68, 64)
EMBER = (214, 78, 18)
EMBER_DIM = (130, 40, 12)
NOSE = (8, 8, 9)
TOOTH = (196, 188, 170)
INNER_EAR = (96, 34, 20)


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


def box(x, y, w, h, d):
    return {
        "top": (x + d, y, w, d),
        "bottom": (x + d + w, y, w, d),
        "right": (x, y + d, d, h),
        "front": (x + d, y + d, w, h),
        "left": (x + d + w, y + d, d, h),
        "back": (x + 2 * d + w, y + d, w, h),
    }


class Painter:
    def __init__(self, angry):
        self.angry = angry
        self.rng = random.Random(1983)
        self.img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        self.px = self.img.load()

    def put(self, x, y, c):
        self.px[x, y] = tuple(c) + (255,)

    def fill(self, x, y, w, h, base, jitter=5):
        for j in range(y, y + h):
            for i in range(x, x + w):
                self.put(i, j, shade(base, self.rng.randint(-jitter, jitter)))

    def splat(self, x, y, w, h, color, count):
        for _ in range(count):
            self.put(x + self.rng.randrange(w), y + self.rng.randrange(h), shade(color, self.rng.randint(-4, 4)))

    def burnt(self, x, y, w, h, patches):
        """Scorched patches with a glowing ember rim; angry hounds smoulder hotter."""
        for _ in range(patches):
            pw, ph = self.rng.randint(2, max(2, w // 2)), self.rng.randint(1, max(1, h // 3))
            px0, py0 = x + self.rng.randrange(max(1, w - pw + 1)), y + self.rng.randrange(max(1, h - ph + 1))
            self.fill(px0, py0, pw, ph, BURN, 3)
            if self.rng.random() < (0.9 if self.angry else 0.5):
                ex, ey = px0 + self.rng.randrange(pw), py0 + self.rng.randrange(ph)
                self.put(ex, ey, EMBER if self.angry else EMBER_DIM)

    def fur(self, x, y, w, h, base=FUR, burn=2):
        self.fill(x, y, w, h, base)
        self.splat(x, y, w, h, FUR_DARK, w * h // 3)
        self.splat(x, y, w, h, FUR_LIGHT, w * h // 12)
        self.splat(x, y, w, h, ASH, max(1, w * h // 24))
        self.burnt(x, y, w, h, burn)

    def part(self, origin, w, h, d, painter):
        for name, rect in box(origin[0], origin[1], w, h, d).items():
            painter(name, *rect)

    def head(self, name, x, y, w, h):
        self.fur(x, y, w, h, FUR_DARK if name == "top" else FUR, burn=2)
        if name == "front":
            eye_hot = (255, 120, 20) if self.angry else EMBER
            eye_core = (255, 236, 120) if self.angry else (255, 170, 50)
            for ex in (1, 4):
                self.put(x + ex, y + 2, eye_core)
                self.put(x + ex - 1 if ex == 1 else x + ex + 1, y + 2, eye_hot)
                self.put(x + ex, y + 3, shade(EMBER_DIM, 10) if self.angry else FUR_DARK)
            for i in range(1, 5):
                self.put(x + i, y + 1, FUR_DARK)

    def muzzle(self, name, x, y, w, h):
        self.fur(x, y, w, h, FUR, burn=1)
        if name == "front":
            self.put(x + 1, y, NOSE)
            if self.angry:
                self.put(x, y + 2, TOOTH)
                self.put(x + 2, y + 2, TOOTH)
                self.put(x + 1, y + 2, EMBER)
        if name == "bottom":
            self.fill(x, y, w, h, FUR_DARK, 3)

    def ear(self, name, x, y, w, h):
        self.fur(x, y, w, h, FUR_DARK, burn=0)
        if name == "front":
            self.fill(x, y, w, h - 1, INNER_EAR, 4)
            self.put(x, y + h - 1, BURN)

    def body(self, name, x, y, w, h):
        base = BELLY if name == "bottom" else FUR_DARK if name == "top" else FUR
        self.fur(x, y, w, h, base, burn=4 if name in ("top", "left", "right") else 1)

    def mane(self, name, x, y, w, h):
        self.fur(x, y, w, h, FUR_DARK, burn=3)
        self.splat(x, y, w, h, FUR_LIGHT, w * h // 10)

    def leg(self, name, x, y, w, h):
        self.fur(x, y, w, h, FUR, burn=1)
        if name in ("front", "back", "left", "right") and h > 4:
            self.fill(x, y + h - 2, w, 2, BURN, 2)
            if self.angry:
                self.put(x + self.rng.randrange(w), y + h - 2, EMBER_DIM)

    def tail(self, name, x, y, w, h):
        self.fur(x, y, w, h, FUR_DARK, burn=1)
        if name in ("front", "back", "left", "right"):
            self.fill(x, y + h - 3, w, 3, BURN, 2)
            self.put(x, y + h - 2, EMBER if self.angry else EMBER_DIM)

    def render(self):
        self.part((0, 0), 6, 6, 4, self.head)
        self.part((0, 10), 3, 3, 3, self.muzzle)
        self.part((16, 14), 2, 2, 1, self.ear)
        self.part((18, 14), 6, 9, 6, self.body)
        self.part((21, 0), 8, 6, 7, self.mane)
        self.part((0, 18), 2, 8, 2, self.leg)
        self.part((9, 18), 2, 8, 2, self.tail)
        return self.img


def write_pack(pack_dir, base, angry):
    out = os.path.join(pack_dir, "assets/minecraft/textures/entity/wolf")
    os.makedirs(out, exist_ok=True)
    count = 0
    for variant in VARIANTS:
        for suffix, img in (("", base), ("tame", base), ("angry", angry)):
            if variant == "" and suffix == "":
                name = "wolf.png"
            else:
                name = "wolf_" + variant + suffix
                name = name.rstrip("_") + ".png"
            img.save(os.path.join(out, name), optimize=True)
            count += 1
    return count


def preview(base, angry, path):
    scale = 8
    sheet = Image.new("RGBA", (64 * scale, 32 * scale * 2 + 12), (70, 90, 70, 255))
    for row, img in enumerate((base, angry)):
        big = img.resize((64 * scale, 32 * scale), Image.NEAREST)
        sheet.paste(big, (0, row * (32 * scale + 12)), big)
    face = Image.new("RGBA", (6 * 10 * 2 + 30, 6 * 10), (70, 90, 70, 255))
    for n, img in enumerate((base, angry)):
        head = img.crop((4, 4, 10, 10)).resize((60, 60), Image.NEAREST)
        face.paste(head, (n * 90, 0), head)
    full = Image.new("RGBA", (sheet.width, sheet.height + face.height + 12), (70, 90, 70, 255))
    full.paste(sheet, (0, 0))
    full.paste(face, (0, sheet.height + 12))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    full.save(path)


def main():
    pack = sys.argv[1] if len(sys.argv) > 1 else PACK
    prev = sys.argv[2] if len(sys.argv) > 2 else PREVIEW_OUT
    base = Painter(False).render()
    angry = Painter(True).render()
    print("wrote", write_pack(pack, base, angry), "textures")
    preview(base, angry, prev)
    print("preview", prev)


if __name__ == "__main__":
    main()
