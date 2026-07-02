#!/usr/bin/env python3
"""
modelkit — author Minecraft/BlockBench item models from primitives instead of hand-typing
hundreds of cuboids, with automatic detailed-texture atlas packing.

Per-gun authoring scripts import this:

    from modelkit import Model
    m = Model(name="lsat", texture_size=64)
    m.box([2, 6, 0], [4, 9, 22], color=(60, 60, 64))          # receiver
    m.box([2.5, 9, 2], [3.5, 11, 6], color=(30, 30, 32))      # sight
    ...
    m.save_model("resourcepack/comz-pack/assets/minecraft/models/custom/item/gen/lsat.json",
                 texture_ref="custom/item/gen/lsat")
    m.save_texture("resourcepack/comz-pack/assets/minecraft/textures/custom/item/gen/lsat.png")

Each box reserves a cell in the texture atlas. Cells are NOT flat: they get a bevel (lighter
top/left highlight, darker bottom/right shadow), a panel seam, and slight deterministic dither
so the metal reads with edges and depth instead of one mono colour. Build with barrel along +Z
(length in z); the display block matches the pack's existing 3d_guns so the gun sits correctly
in hand (no mirrored/backwards orientation).
"""
import json
import os

from PIL import Image

# Canonical display block copied from the pack's 3d_guns (cz75/m1911_v2): barrel +Z, no hand-view
# rotation (so geometry orientation is used directly), gui rotated [90,-45,90]. Keeps gen guns
# oriented the same way in hand as the hand-made models.
DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 2.0, -1.25], "scale": [0.35, 0.35, 0.35]},
    "thirdperson_lefthand": {"translation": [0, 2.0, -1.25], "scale": [0.35, 0.35, 0.35]},
    "firstperson_righthand": {"translation": [1.5, 3.0, -5], "scale": [0.45, 0.45, 0.45]},
    "firstperson_lefthand": {"translation": [1.5, 3.0, -5], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [90, -45, 90], "translation": [-1.5, 3.5, -0.5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
    "head": {"translation": [0, 13, 0], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
}


def _clamp(v):
    return max(0, min(255, int(v)))


def _shade(color, mult):
    return tuple(_clamp(c * mult) for c in color)


class _Cell:
    __slots__ = ("x", "y", "s", "color")

    def __init__(self, x, y, s, color):
        self.x, self.y, self.s, self.color = x, y, s, color


class Model:
    """Accumulates boxes, packs detailed swatches into a texture atlas, emits model JSON + PNG."""

    FACES = ("north", "south", "east", "west", "up", "down")

    def __init__(self, name, texture_size=64):
        self.name = name
        # accept int OR (w,h) tuple/list (older build scripts pass a tuple); use the larger dim
        if isinstance(texture_size, (tuple, list)):
            texture_size = max(texture_size)
        self.size = int(texture_size)
        self.boxes = []   # (from, to, rotation|None, color)
        self._cells = []
        self._cell = 0

    def box(self, frm, to, color=(120, 120, 120), rotation=None):
        """Add a cuboid from->to (model space). color = base RGB. rotation optional
        {'angle','axis','origin'} (BlockBench: angle in {0,±22.5,±45}, single axis)."""
        self.boxes.append((list(map(float, frm)), list(map(float, to)), rotation, tuple(color)))
        return self

    def _pack(self):
        self._cells = []
        n = max(1, len(self.boxes))
        # choose a cell size so all boxes fit in the atlas (leave 1px margin between cells)
        import math
        cols = max(1, int(self.size // 4))
        cell = max(3, self.size // max(1, math.ceil(math.sqrt(n))))
        while (self.size // cell) ** 2 < n and cell > 3:
            cell -= 1
        self._cell = cell
        per_row = max(1, self.size // cell)
        for i, (_f, _t, _r, color) in enumerate(self.boxes):
            cx = (i % per_row) * cell
            cy = (i // per_row) * cell
            if cy + cell > self.size:
                raise ValueError(f"atlas {self.size}px too small for {n} boxes; raise texture_size")
            self._cells.append(_Cell(cx, cy, cell, color))

    def _faces_json(self, cell):
        # inset 0.25px so neighbouring cells never bleed across a face edge
        uv = [cell.x + 0.25, cell.y + 0.25, cell.x + cell.s - 0.25, cell.y + cell.s - 0.25]
        return {f: {"uv": uv, "texture": "#0"} for f in self.FACES}

    def save_model(self, path, texture_ref):
        self._pack()
        elements = []
        for (frm, to, rot, _c), cell in zip(self.boxes, self._cells):
            el = {"from": frm, "to": to, "faces": self._faces_json(cell)}
            if rot:
                el["rotation"] = rot
            elements.append(el)
        model = {
            "credit": "Made with modelkit",
            "texture_size": [self.size, self.size],
            "textures": {"0": texture_ref, "particle": texture_ref},
            "elements": elements,
            "display": DISPLAY,
        }
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as fh:
            json.dump(model, fh, indent=2)
        print(f"model -> {path} ({len(elements)} boxes)")

    def save_texture(self, path):
        if not self._cells:
            self._pack()
        # RGB (not RGBA): MC 26.2 only bounds-checks UVs for translucent (alpha) textures during
        # bake; an RGBA item texture with any out-of-range UV fails the whole model. RGB is safe.
        img = Image.new("RGB", (self.size, self.size), (24, 24, 28))
        px = img.load()
        for cell in self._cells:
            base = cell.color
            hi = _shade(base, 1.25)   # top/left highlight
            sh = _shade(base, 0.7)    # bottom/right shadow
            seam = _shade(base, 0.55)
            s = cell.s
            for j in range(s):
                for i in range(s):
                    c = base
                    if i == 0 or j == 0:
                        c = hi
                    elif i == s - 1 or j == s - 1:
                        c = sh
                    elif j == s // 2:
                        c = seam            # horizontal panel seam
                    else:
                        # subtle deterministic dither for a non-flat metal look
                        d = ((i * 7 + j * 13) % 5) - 2
                        c = _shade(base, 1.0 + d * 0.03)
                    px[cell.x + i, cell.y + j] = (c[0], c[1], c[2])
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        print(f"texture -> {path} ({self.size}x{self.size}, {len(self._cells)} cells)")
