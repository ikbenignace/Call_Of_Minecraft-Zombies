#!/usr/bin/env python3
"""
Repaint a low-res gun texture into a detailed one without touching the model's UVs.

The texture is upscaled (default 4x) and every source pixel is classified by colour; each class is
re-rendered with a material (worn blued steel, chrome, checkered walnut, oxblood paint, glow, ...).
Pixels on the border of a uniform-colour region get an edge highlight: those borders are the face
edges in the UV layout, so the model reads as bevelled and worn instead of flat-shaded.

    from repaint import repaint
    repaint('m1911.png', 'm1911_v3.png', [(lambda c: c[0] > 120 and c[1] < 80, 'walnut_check'), ...],
            default='blued')
"""
import math
import random

from PIL import Image


def _clamp(v):
    return max(0, min(255, int(round(v))))


def _lum(c):
    return (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 255.0


# material(x, y, src_colour, rng) -> rgb ; x, y are in the upscaled texture.
def blued(x, y, c, rng):
    t = 0.75 + 0.55 * _lum(c)
    streak = 1 + 0.06 * math.sin(y * 1.7 + (x % 7))
    n = 1 + rng.uniform(-0.05, 0.05)
    return (58 * t * streak * n, 64 * t * streak * n, 76 * t * streak * n)


def steel(x, y, c, rng):
    t = 0.7 + 0.6 * _lum(c)
    n = 1 + rng.uniform(-0.04, 0.04) + 0.05 * math.sin(y * 2.1)
    return (150 * t * n, 154 * t * n, 160 * t * n)


def gunsteel(x, y, c, rng):
    """Polished but worn gun steel (BO2 M1911 / revolver look): mid grey with fine streaks and dark pits."""
    t = 0.65 + 0.55 * _lum(c)
    streak = 1 + 0.07 * math.sin(y * 1.9 + (x % 5) * 0.7)
    n = 1 + rng.uniform(-0.06, 0.06)
    if rng.random() < 0.02:
        n *= 0.6
    return (82 * t * streak * n, 84 * t * streak * n, 90 * t * streak * n)


def steel_dark(x, y, c, rng):
    """Worn blued revolver/rifle steel: grey with a faint blue cast and fine streaks."""
    n = 1 + rng.uniform(-0.05, 0.05) + 0.05 * math.sin(y * 2.3 + (x % 3))
    if rng.random() < 0.015:
        n *= 1.35
    return (92 * n, 96 * n, 104 * n)


def chrome(x, y, c, rng):
    t = 0.8 + 0.5 * _lum(c)
    band = 1.15 if (y // 2) % 5 == 0 else 1.0
    return (190 * t * band, 195 * t * band, 205 * t * band)


def darkmetal(x, y, c, rng):
    n = 1 + rng.uniform(-0.06, 0.06)
    return (38 * n, 39 * n, 42 * n)


def walnut_check(x, y, c, rng):
    t = 0.7 + 0.6 * _lum(c)
    check = 0.78 if (x + y) % 4 == 0 or (x - y) % 4 == 0 else 1.0
    n = 1 + rng.uniform(-0.05, 0.05)
    return (112 * t * check * n, 64 * t * check * n, 38 * t * check * n)


def walnut(x, y, c, rng):
    t = 0.7 + 0.6 * _lum(c)
    g = 1 + 0.12 * math.sin(y * 0.9 + 2 * math.sin(x * 0.15))
    return (118 * t * g, 66 * t * g, 38 * t * g)


def oxblood(x, y, c, rng):
    t = c[0] / 112.0
    n = 1 + rng.uniform(-0.05, 0.05)
    if rng.random() < 0.006:
        return (176, 96, 88)
    return (150 * t * n, 28 * t * n, 26 * t * n)


def gold(x, y, c, rng):
    t = 0.75 + 0.5 * _lum(c)
    return (214 * t, 168 * t, 66 * t)


def keep(x, y, c, rng):
    return c


MATERIALS = {f.__name__: f for f in (blued, steel, steel_dark, gunsteel, chrome, darkmetal, walnut_check, walnut, oxblood, gold, keep)}


def repaint(src_path, out_path, rules, default='keep', scale=4, edge=1.35, seed=5, edge_threshold=40):
    """rules: list of (predicate(rgb[, x, y[, region_size]]) -> bool, material name); first match wins.
    x, y are source-texture pixel coordinates and region_size the pixel count of the same-colour area,
    for telling apart parts that share a colour (e.g. grip panels vs. pins)."""
    import inspect
    src = Image.open(src_path).convert('RGBA')
    w, h = src.size
    sp = src.load()
    # Size of each pixel's same-colour connected region, so rules can tell big panels from small parts.
    region = [[0] * w for _ in range(h)]
    for y0 in range(h):
        for x0 in range(w):
            if region[y0][x0] or sp[x0, y0][3] == 0:
                continue
            stack, seen, col = [(x0, y0)], [], sp[x0, y0]
            region[y0][x0] = -1
            while stack:
                x, y = stack.pop()
                seen.append((x, y))
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < w and 0 <= ny < h and not region[ny][nx] and sp[nx, ny] == col:
                        region[ny][nx] = -1
                        stack.append((nx, ny))
            for x, y in seen:
                region[y][x] = len(seen)

    def matches(pred, rgb, x, y):
        n = len(inspect.signature(pred).parameters)
        return pred(rgb) if n == 1 else pred(rgb, x, y) if n == 3 else pred(rgb, x, y, region[y][x])

    kinds = [[None] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            c = sp[x, y]
            if c[3] == 0:
                continue
            rgb = c[:3]
            kinds[y][x] = next((m for pred, m in rules if matches(pred, rgb, x, y)), default)

    def border(x, y):
        c = sp[x, y]
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if not (0 <= nx < w and 0 <= ny < h) or sp[nx, ny][3] == 0:
                return (dx, dy)
            o = sp[nx, ny]
            if abs(o[0] - c[0]) + abs(o[1] - c[1]) + abs(o[2] - c[2]) > edge_threshold:
                return (dx, dy)
        return None

    out = Image.new('RGBA', (w * scale, h * scale), (0, 0, 0, 0))
    op = out.load()
    rng = random.Random(seed)
    for y in range(h * scale):
        for x in range(w * scale):
            sx, sy = x // scale, y // scale
            kind = kinds[sy][sx]
            if kind is None:
                continue
            c = sp[sx, sy][:3]
            col = MATERIALS[kind](x, y, c, rng)
            b = border(sx, sy)
            if b and kind not in ('keep',):
                dx, dy = b
                at_edge = (dx == 1 and x % scale == scale - 1) or (dx == -1 and x % scale == 0) or \
                          (dy == 1 and y % scale == scale - 1) or (dy == -1 and y % scale == 0)
                if at_edge:
                    col = tuple(v * edge for v in col)
            op[x, y] = tuple(_clamp(v) for v in col) + (sp[sx, sy][3],)
    out.save(out_path)
    return out
