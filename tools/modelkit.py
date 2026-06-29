#!/usr/bin/env python3
"""
modelkit — author Minecraft/BlockBench item models from primitives instead of hand-typing
hundreds of cuboids, with automatic UV packing onto a texture atlas.

Per-gun authoring scripts import this:

    from modelkit import Model
    m = Model(name="lsat", texture_size=(64, 64))
    m.box([2, 6, 0], [4, 9, 22], color=(60, 60, 64))      # receiver
    m.box([2.5, 9, 2], [3.5, 11, 6], color=(30, 30, 32))  # sight
    ...
    m.save_model("resourcepack/comz-pack/assets/minecraft/models/custom/item/2blops/lsat.json",
                 texture_ref="custom/item/2blops/lsat")
    m.save_texture("resourcepack/comz-pack/assets/minecraft/textures/custom/item/2blops/lsat.png")

Each box reserves a region of the atlas and is flat-shaded per face from `color` (a quick
base look you then refine in the texture, or paint over). The result is a normal BlockBench
model + a PNG you can preview with tools/model_preview.py and iterate.
"""
import json
import os

from PIL import Image


def _display_block():
    """Sensible default display transforms for a gun item (matches the pack's 3d_guns style)."""
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 4, 1], "scale": [0.7, 0.7, 0.7]},
        "gui": {"rotation": [0, -135, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 14, 0], "scale": [1, 1, 1]},
    }


class _Cell:
    __slots__ = ("x", "y", "w", "h", "color")

    def __init__(self, x, y, w, h, color):
        self.x, self.y, self.w, self.h, self.color = x, y, w, h, color


class Model:
    """Accumulates boxes, packs their faces into a texture atlas, emits model JSON + PNG."""

    FACES = ("north", "south", "east", "west", "up", "down")

    def __init__(self, name, texture_size=(64, 64)):
        self.name = name
        self.tw, self.th = texture_size
        self.boxes = []          # (from, to, rotation|None, color)
        self._cells = []         # atlas reservations, parallel to boxes' faces

    def box(self, frm, to, color=(120, 120, 120), rotation=None):
        """Add a cuboid from->to (model space, 0..16). color = base RGB. rotation optional
        {'angle','axis','origin'}."""
        self.boxes.append((list(frm), list(to), rotation, tuple(color)))
        return self

    # ---- atlas packing -------------------------------------------------------
    def _pack(self):
        """Shelf-pack one small rectangle per box (a single swatch reused by all 6 faces).
        Keeps UVs trivial and the atlas tiny; refine the PNG afterwards if wanted."""
        self._cells = []
        x = y = shelf_h = 0
        sw = 2  # swatch size in atlas px per box
        for _frm, _to, _rot, color in self.boxes:
            if x + sw > self.tw:
                x = 0
                y += shelf_h
                shelf_h = 0
            self._cells.append(_Cell(x, y, sw, sw, color))
            x += sw
            shelf_h = max(shelf_h, sw)
        if y + shelf_h > self.th:
            raise ValueError(f"atlas {self.tw}x{self.th} too small for {len(self.boxes)} boxes; raise texture_size")

    def _faces_json(self, cell):
        uv = [cell.x, cell.y, cell.x + cell.w, cell.y + cell.h]
        return {f: {"uv": uv, "texture": "#0"} for f in self.FACES}

    # ---- output --------------------------------------------------------------
    def save_model(self, path, texture_ref):
        self._pack()
        elements = []
        for (frm, to, rot, _color), cell in zip(self.boxes, self._cells):
            el = {"from": frm, "to": to, "faces": self._faces_json(cell)}
            if rot:
                el["rotation"] = rot
            elements.append(el)
        model = {
            "credit": "Made with modelkit",
            "texture_size": [self.tw, self.th],
            "textures": {"0": texture_ref, "particle": texture_ref},
            "elements": elements,
            "display": _display_block(),
        }
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as fh:
            json.dump(model, fh, indent=2)
        print(f"model -> {path} ({len(elements)} boxes)")

    def save_texture(self, path):
        if not self._cells:
            self._pack()
        img = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        px = img.load()
        for cell in self._cells:
            r, g, b = cell.color
            for yy in range(cell.y, cell.y + cell.h):
                for xx in range(cell.x, cell.x + cell.w):
                    px[xx, yy] = (r, g, b, 255)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        print(f"texture -> {path} ({self.tw}x{self.th})")
