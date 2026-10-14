"""
mkit — shared helpers for the machine models (perk machines, Pack-a-Punch, mystery box).

Builds on gunkit: painted-metal materials, procedurally drawn logo/emblem panels, and a few
composite parts (emblem lamp, arched tops, sunburst). Front of every machine is -Z, centred on x = 8.
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import gunkit
from gunkit import Gun, MATERIALS, Material, brushed, wood, glow, ribs

DENSITY = 6
FIXED_DISPLAY = {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}}
SS = 4  # supersampling factor for painted graphics

_FONT_FILES = {
    'script': ['/System/Library/Fonts/Supplemental/Brush Script.ttf', '/System/Library/Fonts/Supplemental/Georgia Bold Italic.ttf'],
    'block': ['/System/Library/Fonts/Supplemental/Impact.ttf', '/System/Library/Fonts/Supplemental/Arial Black.ttf'],
    'serif': ['/System/Library/Fonts/Supplemental/Georgia Bold.ttf'],
}


def font(kind, size):
    for path in _FONT_FILES[kind]:
        if os.path.exists(path):
            return ImageFont.truetype(path, int(size))
    return ImageFont.load_default(int(size))


def new_gun(name):
    g = Gun(name)
    g.DENSITY = DENSITY
    return g


def save(g):
    return g.save(display=FIXED_DISPLAY, pap=False, subdir='machines', detail=False)


def paint(name, rgb, wear=0.025, streak=0.05, amount=0.05):
    """Painted sheet metal with a little edge wear."""
    MATERIALS[name] = Material(brushed(rgb, amount=amount, streak=streak, wear=wear))
    Gun.WORN.add(name)
    return name


def timber(name, rgb, grain=0.2):
    MATERIALS[name] = Material(wood(rgb, grain=grain))
    return name


def lit(name, base, core):
    """Self-lit looking panel: bright core fading to base."""
    MATERIALS[name] = Material(glow(base, core=core), mode='stretch')
    return name


def graphic(name, w_units, h_units, fn, rotate=0):
    """Register a stretched face material painted by fn(draw, W, H) at supersampled size."""
    pw = max(2, min(160, int(round(w_units * DENSITY))))
    ph = max(2, min(160, int(round(h_units * DENSITY))))

    def painter(rng):
        im = Image.new('RGBA', (pw * SS, ph * SS), (0, 0, 0, 0))
        dr = ImageDraw.Draw(im)
        dr.canvas = im  # lets painters composite layers (glows) onto the image
        fn(dr, pw * SS, ph * SS)
        im = im.resize((pw, ph), Image.LANCZOS)
        return im.rotate(rotate) if rotate else im

    MATERIALS[name] = Material(painter, mode='stretch')
    return name


def text_center(d, W, H, s, kind, size, fill, cx=0.5, cy=0.5, stroke=0, stroke_fill=None, fit=0.9):
    """Draw s centred at (cx, cy) in unit coordinates; size is in final texels and shrinks to keep the
    text within fit * width."""
    f = font(kind, size * SS)
    box = d.textbbox((0, 0), s, font=f, stroke_width=stroke * SS)
    w = box[2] - box[0]
    if w > W * fit:
        f = font(kind, size * SS * W * fit / w)
        box = d.textbbox((0, 0), s, font=f, stroke_width=stroke * SS)
    w, h = box[2] - box[0], box[3] - box[1]
    d.text((W * cx - w / 2 - box[0], H * cy - h / 2 - box[1]), s, font=f, fill=fill,
           stroke_width=stroke * SS, stroke_fill=stroke_fill)


def poly(d, pts, fill, W, H):
    d.polygon([(x * W, y * H) for x, y in pts], fill=fill)


def rect(d, x0, y0, x1, y1, fill, W, H, r=0):
    box = (x0 * W, y0 * H, x1 * W, y1 * H)
    if r:
        d.rounded_rectangle(box, radius=r * min(W, H), fill=fill)
    else:
        d.rectangle(box, fill=fill)


def disc_emblem(d, W, H, ring, ring2, face, symbol, symbol_color, accent=None):
    """Round lamp emblem: double ring, face colour, then a symbol drawn in unit coordinates (0..1)."""
    d.ellipse((0, 0, W - 1, H - 1), fill=ring)
    d.ellipse((W * 0.06, H * 0.06, W * 0.94, H * 0.94), fill=ring2)
    d.ellipse((W * 0.12, H * 0.12, W * 0.88, H * 0.88), fill=face)
    symbol(d, W, H, symbol_color, accent or symbol_color)


# ---- symbols (drawn in the disc's unit square) -------------------------------------------------

def sym_shield(d, W, H, c, a):
    poly(d, [(0.28, 0.24), (0.72, 0.24), (0.72, 0.58), (0.5, 0.78), (0.28, 0.58)], c, W, H)
    d.line([(W * 0.36, H * 0.36), (W * 0.64, H * 0.62)], fill=a, width=int(W * 0.06))
    d.line([(W * 0.64, H * 0.36), (W * 0.36, H * 0.62)], fill=a, width=int(W * 0.06))


def sym_hand(d, W, H, c, a):
    rect(d, 0.36, 0.5, 0.66, 0.74, c, W, H, 0.08)
    for i in range(4):
        x = 0.37 + i * 0.075
        rect(d, x, 0.26 + (0.04 if i in (0, 3) else 0), x + 0.055, 0.52, c, W, H, 0.02)
    poly(d, [(0.34, 0.58), (0.24, 0.5), (0.3, 0.46), (0.38, 0.54)], c, W, H)
    for i in range(3):
        y = 0.36 + i * 0.12
        d.line([(W * 0.14, H * y), (W * 0.28, H * y)], fill=a, width=int(W * 0.04))


def sym_bullets(d, W, H, c, a):
    for dx in (-0.1, 0.1):
        x = 0.5 + dx
        rect(d, x - 0.06, 0.4, x + 0.06, 0.74, c, W, H, 0.01)
        poly(d, [(x - 0.06, 0.4), (x + 0.06, 0.4), (x, 0.24)], a, W, H)


def sym_revive(d, W, H, c, a):
    d.ellipse((W * 0.4, H * 0.22, W * 0.56, H * 0.38), fill=c)
    poly(d, [(0.36, 0.42), (0.6, 0.42), (0.58, 0.62), (0.4, 0.62)], c, W, H)
    rect(d, 0.24, 0.66, 0.78, 0.72, c, W, H)
    rect(d, 0.62, 0.3, 0.7, 0.54, a, W, H)
    rect(d, 0.58, 0.38, 0.74, 0.46, a, W, H)


def sym_runner(d, W, H, c, a):
    d.ellipse((W * 0.54, H * 0.2, W * 0.66, H * 0.32), fill=c)
    d.line([(W * 0.56, H * 0.34), (W * 0.46, H * 0.56)], fill=c, width=int(W * 0.07))
    d.line([(W * 0.46, H * 0.56), (W * 0.6, H * 0.68), (W * 0.58, H * 0.8)], fill=c, width=int(W * 0.06))
    d.line([(W * 0.46, H * 0.56), (W * 0.34, H * 0.76)], fill=c, width=int(W * 0.06))
    d.line([(W * 0.54, H * 0.4), (W * 0.7, H * 0.46)], fill=c, width=int(W * 0.05))
    d.line([(W * 0.54, H * 0.4), (W * 0.38, H * 0.36)], fill=c, width=int(W * 0.05))
    for i in range(3):
        y = 0.4 + i * 0.12
        d.line([(W * 0.14, H * y), (W * 0.3, H * y)], fill=a, width=int(W * 0.04))


def sym_burst(d, W, H, c, a):
    pts = []
    for i in range(16):
        r = 0.34 if i % 2 == 0 else 0.15
        ang = i * math.pi / 8
        pts.append((0.5 + r * math.cos(ang), 0.5 + r * math.sin(ang)))
    poly(d, pts, c, W, H)
    d.ellipse((W * 0.4, H * 0.4, W * 0.6, H * 0.6), fill=a)


def sym_crosshair(d, W, H, c, a):
    d.ellipse((W * 0.26, H * 0.26, W * 0.74, H * 0.74), outline=c, width=int(W * 0.05))
    d.line([(W * 0.5, H * 0.16), (W * 0.5, H * 0.84)], fill=c, width=int(W * 0.045))
    d.line([(W * 0.16, H * 0.5), (W * 0.84, H * 0.5)], fill=c, width=int(W * 0.045))
    d.ellipse((W * 0.44, H * 0.44, W * 0.56, H * 0.56), fill=a)


def sym_pistol(d, W, H, c, a):
    rect(d, 0.26, 0.34, 0.74, 0.5, c, W, H, 0.03)
    rect(d, 0.3, 0.5, 0.42, 0.74, c, W, H, 0.02)
    rect(d, 0.46, 0.5, 0.52, 0.58, c, W, H)
    rect(d, 0.7, 0.38, 0.8, 0.44, c, W, H)


def sym_cherry(d, W, H, c, a):
    d.ellipse((W * 0.26, H * 0.5, W * 0.5, H * 0.74), fill=c)
    d.ellipse((W * 0.5, H * 0.46, W * 0.74, H * 0.7), fill=c)
    d.line([(W * 0.38, H * 0.52), (W * 0.52, H * 0.26), (W * 0.64, H * 0.48)], fill=a, width=int(W * 0.04))
    poly(d, [(0.5, 0.26), (0.66, 0.2), (0.6, 0.32)], a, W, H)


def sym_tomb(d, W, H, c, a):
    d.pieslice((W * 0.3, H * 0.2, W * 0.7, H * 0.6), 180, 360, fill=c)
    rect(d, 0.3, 0.4, 0.7, 0.76, c, W, H)
    rect(d, 0.46, 0.34, 0.54, 0.62, a, W, H)
    rect(d, 0.4, 0.42, 0.6, 0.48, a, W, H)


def sym_seven(d, W, H, c, a):
    d.line([(W * 0.3, H * 0.28), (W * 0.7, H * 0.28), (W * 0.42, H * 0.76)], fill=c, width=int(W * 0.09), joint='curve')
    d.line([(W * 0.4, H * 0.5), (W * 0.62, H * 0.5)], fill=a, width=int(W * 0.05))


def sym_vulture(d, W, H, c, a):
    d.line([(W * 0.34, H * 0.74), (W * 0.34, H * 0.4), (W * 0.5, H * 0.26), (W * 0.64, H * 0.34)], fill=c, width=int(W * 0.09), joint='curve')
    poly(d, [(0.62, 0.3), (0.76, 0.4), (0.64, 0.42)], a, W, H)
    d.line([(W * 0.34, H * 0.6), (W * 0.62, H * 0.7)], fill=c, width=int(W * 0.07))
    d.ellipse((W * 0.54, H * 0.3, W * 0.58, H * 0.34), fill=a)


# ---- composite parts ----------------------------------------------------------------------------

def box(g, x0, y0, z0, x1, y1, z1, mat, faces=None, name=None):
    """Axis-aligned box. Layouts are written as seen from the front with low x on the left; seen from
    -Z the +x side is on the viewer's left, so x is mirrored here."""
    return g.box((16 - x1, y0, z0), (16 - x0, y1, z1), mat, faces=faces, name=name)


def stepped_top(g, x0, x1, z0, z1, y, mat, steps=((0.45, 0.4), (1.2, 0.5))):
    """Rounded-looking shoulder: shrinking slabs stacked on a flat top at height y."""
    cy = y
    for inset, h in steps:
        box(g, x0 + inset, cy, z0 + inset, x1 - inset, cy + h, z1 - inset, mat, name='shoulder')
        cy += h
    return cy


def arch(g, cx, cy, r, z0, z1, mat, name='arch'):
    """Round-topped cabinet crown: a z-axis cylinder; its lower half hides inside the body."""
    g.cyl('z', (cx, cy), r, z0, z1, mat, seg=16, name=name)


def plate(g, x0, y0, x1, y1, z, mat, depth=0.15, name='panel', face='north'):
    """Thin plate on the front (north) face carrying a painted graphic."""
    box(g, x0, y0, z - depth, x1, y1, z, mat, faces={face: mat}, name=name)


def emblem_lamp(g, cx, cy, z_back, r, rim_mat, emblem_mat, depth=2.2, name='emblem'):
    """Round lit sign: chrome-rimmed disc with the emblem plate on its -Z face."""
    g.cyl('z', (cx, cy), r, z_back, z_back + depth, rim_mat, seg=16, name=name + '_rim')
    pr = r * 0.98
    box(g, cx - pr, cy - pr, z_back - 0.12, cx + pr, cy + pr, z_back, emblem_mat,
        faces={'north': emblem_mat}, name=name + '_face')


def sunburst(g, cx, cy, z0, z1, r_out, ray_mat, bulb_mat, bulb=0.9):
    """16 rays with bulbs at the tips, built from 8 thin boxes through the centre."""
    t = 0.28
    for ang in (0, 22.5, 45, -22.5):
        for horiz in (False, True):
            frm, to = [cx - t, cy - r_out, z0], [cx + t, cy + r_out, z1]
            if horiz:
                frm, to = [cx - r_out, cy - t, z0], [cx + r_out, cy + t, z1]
            if ang == 0 and horiz:
                pass
            box_rot = ('z', ang, (cx, cy, (z0 + z1) / 2))
            g.box(tuple(frm), tuple(to), ray_mat, rot=box_rot, name='ray')
    for k in range(16):
        a = math.radians(k * 22.5)
        bx, by = cx + r_out * math.sin(a), cy + r_out * math.cos(a)
        box(g, bx - bulb / 2, by - bulb / 2, z0 - 0.1, bx + bulb / 2, by + bulb / 2, z1 + 0.1, bulb_mat, name='bulb')


def vents(g, x0, x1, y0, y1, z, mat_frame, mat_slat, n=4):
    """Louvred vent: frame with a lit slat strip per louvre."""
    box(g, x0, y0, z - 0.15, x1, y1, z, mat_frame, name='vent_frame')
    step = (y1 - y0) / n
    for i in range(n):
        y = y0 + step * (i + 0.3)
        box(g, x0 + 0.4, y, z - 0.3, x1 - 0.4, y + step * 0.4, z - 0.12, mat_slat, name='slat')


def coin_panel(g, x0, x1, y0, y1, z, mat_dark, mat_metal, mat_glow):
    """Coin-op plate: dark panel, display window, coin slot, return flap, dispenser light."""
    box(g, x0, y0, z - 0.5, x1, y1, z, mat_dark, name='coin_panel')
    w, h = x1 - x0, y1 - y0
    box(g, x0 + w * 0.15, y1 - h * 0.28, z - 0.62, x1 - w * 0.15, y1 - h * 0.1, z - 0.5, mat_metal, name='display')
    box(g, x0 + w * 0.42, y0 + h * 0.34, z - 0.65, x0 + w * 0.58, y0 + h * 0.6, z - 0.5, mat_metal, name='coin_slot')
    box(g, x0 + w * 0.3, y0 + h * 0.08, z - 0.6, x0 + w * 0.7, y0 + h * 0.26, z - 0.5, mat_glow, name='dispenser')


def mark_materials(g, names_by_index):
    """Re-point elements (by index into g.elements) at other materials."""
    for i, m in names_by_index.items():
        g.elements[i]['mat'] = m
        g._mat_index(m)
