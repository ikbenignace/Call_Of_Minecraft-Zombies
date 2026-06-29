#!/usr/bin/env python3
"""
In-house isometric previewer for Minecraft/BlockBench item models.

Reads a BlockBench-format model JSON (elements + textures + texture_size) and its
texture PNG(s) and renders an orthographic 2:1 dimetric ("isometric") preview PNG.
Purpose: let the model author SEE the geometry/proportions/UV before in-game testing.
Not a pixel-accurate Minecraft renderer — flat per-face shading, painter's algorithm —
but enough to catch shape, scale and UV mistakes in a tight iterate loop.

Usage:
  python3 tools/model_preview.py <model.json> [-o out.png] [--size 512] [--pack <pack_root>]

<pack_root> defaults to resourcepack/comz-pack (its assets/minecraft/textures/<ref>.png
is how a model "textures" ref like "custom/custom/cz75" resolves). Missing textures fall
back to a magenta checker so they are obvious.
"""
import argparse
import json
import math
import os
import sys

from PIL import Image

# Per-face brightness so the 3D form reads at a glance (top lit, sides progressively darker).
FACE_SHADE = {"up": 1.0, "south": 0.82, "north": 0.7, "east": 0.62, "west": 0.74, "down": 0.5}


def rot_point(p, axis, angle_deg, origin):
    """Rotate point p around a single axis ('x'|'y'|'z') through origin by angle_deg."""
    if not angle_deg:
        return p
    a = math.radians(angle_deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:  # z
        x, y = x * c - y * s, x * s + y * c
    return [x + origin[0], y + origin[1], z + origin[2]]


def project(p, scale, ox, oy):
    """2:1 dimetric projection of model-space point (x up = -y screen)."""
    x, y, z = p
    px = ox + (x - z) * scale
    py = oy - y * scale + (x + z) * 0.5 * scale
    return px, py


def face_corners(f, t, face):
    """3D corners of a face in UV order [top-left, top-right, bottom-right, bottom-left]."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east":  [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west":  [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up":    [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down":  [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def inv_affine(M):
    """Invert a 2x3 affine [[a,b,c],[d,e,f]] -> coeffs for PIL (maps dst->src)."""
    a, b, c = M[0]
    d, e, f = M[1]
    det = a * e - b * d
    if abs(det) < 1e-9:
        return None
    ia, ib = e / det, -b / det
    id_, ie = -d / det, a / det
    ic = -(ia * c + ib * f)
    if_ = -(id_ * c + ie * f)
    return (ia, ib, ic, id_, ie, if_)


def load_texture(ref, pack_root, tex_size):
    """Resolve a model texture ref to a PIL image; magenta checker if missing."""
    path = os.path.join(pack_root, "assets", "minecraft", "textures", ref + ".png")
    if os.path.isfile(path):
        return Image.open(path).convert("RGBA")
    w, h = tex_size
    img = Image.new("RGBA", (max(w, 16), max(h, 16)), (0, 0, 0, 255))
    px = img.load()
    for yy in range(img.height):
        for xx in range(img.width):
            px[xx, yy] = (255, 0, 220, 255) if (xx // 4 + yy // 4) % 2 else (40, 40, 40, 255)
    return img


def shade(img, mult):
    """Multiply RGB by mult (keep alpha)."""
    r, g, b, a = img.split()
    lut = [min(255, int(i * mult)) for i in range(256)]
    return Image.merge("RGBA", (r.point(lut), g.point(lut), b.point(lut), a))


def render(model_path, out_path, canvas, pack_root):
    model = json.load(open(model_path))
    tex_size = model.get("texture_size", [16, 16])
    tw, th = tex_size
    textures = {k: load_texture(v, pack_root, tex_size) for k, v in model.get("textures", {}).items()}

    elements = model.get("elements", [])
    # Collect every face with its projected corners + a depth key for painter's sort.
    faces = []
    for el in elements:
        f, t = el["from"], el["to"]
        rot = el.get("rotation")
        for fname, fdata in el.get("faces", {}).items():
            corners3d = face_corners(f, t, fname)
            if rot:
                corners3d = [tuple(rot_point(list(c), rot["axis"], rot.get("angle", 0), rot["origin"])) for c in corners3d]
            depth = sum(sum(c) for c in corners3d) / len(corners3d)  # x+y+z avg; larger = nearer camera
            faces.append((depth, fname, fdata, corners3d))
    faces.sort(key=lambda x: x[0])  # far -> near

    # Flat (2D sprite) model: no elements (e.g. parent builtin/generated + layer0). Render the
    # texture centred so these guns still show on a contact sheet instead of crashing.
    if not faces:
        out = Image.new("RGBA", (canvas, canvas), (28, 30, 34, 255))
        tex = textures.get("layer0") or textures.get("0") or next(iter(textures.values()), None)
        if tex is not None:
            s = int(canvas * 0.8)
            scaled = tex.resize((s, s), Image.NEAREST)
            out.alpha_composite(scaled, ((canvas - s) // 2, (canvas - s) // 2))
        out.save(out_path)
        print(f"wrote {out_path} ({canvas}x{canvas}, flat sprite)")
        return

    # Fit projection to canvas with a margin.
    pad = canvas * 0.08
    pts = []
    for _, _, _, c3d in faces:
        pts += [project(p, 1.0, 0, 0) for p in c3d]
    minx = min(p[0] for p in pts); maxx = max(p[0] for p in pts)
    miny = min(p[1] for p in pts); maxy = max(p[1] for p in pts)
    span = max(maxx - minx, maxy - miny) or 1
    scale = (canvas - 2 * pad) / span
    ox = pad - minx * scale + (canvas - 2 * pad - (maxx - minx) * scale) / 2
    oy = pad - miny * scale + (canvas - 2 * pad - (maxy - miny) * scale) / 2

    out = Image.new("RGBA", (canvas, canvas), (28, 30, 34, 255))
    for _, fname, fdata, c3d in faces:
        tkey = fdata.get("texture", "#0").lstrip("#")
        tex = textures.get(tkey) or next(iter(textures.values()), None)
        if tex is None:
            continue
        uv = fdata.get("uv", [0, 0, tw, th])
        # texture_size maps uv (0..tw/th) onto the actual image pixels.
        sx0 = uv[0] / tw * tex.width; sy0 = uv[1] / th * tex.height
        sx1 = uv[2] / tw * tex.width; sy1 = uv[3] / th * tex.height
        cx0, cy0 = int(min(sx0, sx1)), int(min(sy0, sy1))
        cx1, cy1 = max(int(max(sx0, sx1)), cx0 + 1), max(int(max(sy0, sy1)), cy0 + 1)
        crop = tex.crop((cx0, cy0, cx1, cy1))
        if uv[2] < uv[0]:
            crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        if uv[3] < uv[1]:
            crop = crop.transpose(Image.FLIP_TOP_BOTTOM)
        crop = crop.resize((max(crop.width, 1), max(crop.height, 1)), Image.NEAREST)
        crop = shade(crop, FACE_SHADE.get(fname, 0.7))
        w, h = crop.size

        # Destination parallelogram corners (TL, TR, BL) from the face's UV-ordered 3D corners.
        P = [project(p, scale, ox, oy) for p in c3d]
        TL, TR, BL = P[0], P[1], P[3]
        M = [
            [(TR[0] - TL[0]) / w, (BL[0] - TL[0]) / h, TL[0]],
            [(TR[1] - TL[1]) / w, (BL[1] - TL[1]) / h, TL[1]],
        ]
        coeffs = inv_affine(M)
        if coeffs is None:
            continue
        warped = crop.transform((canvas, canvas), Image.AFFINE, coeffs, resample=Image.NEAREST)
        out.alpha_composite(warped)

    out.save(out_path)
    print(f"wrote {out_path} ({canvas}x{canvas}, {len(faces)} faces)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("-o", "--out", default=None)
    ap.add_argument("--size", type=int, default=512)
    ap.add_argument("--pack", default="resourcepack/comz-pack")
    a = ap.parse_args()
    out = a.out or os.path.splitext(os.path.basename(a.model))[0] + "_preview.png"
    render(a.model, out, a.size, a.pack)
