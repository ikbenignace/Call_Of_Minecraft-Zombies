#!/usr/bin/env python3
"""
Project a side-view reference image of the real weapon onto the side (east/west) faces of a gunkit
model, so markings, wear, wood grain and colours come from the game render itself.

The model's side silhouette is aligned to the reference's foreground silhouette automatically
(bounding boxes, both horizontal orientations tried, best overlap wins). Where the reference shows
background, or the face is too small to carry detail, the face keeps its procedural material.
"""
from PIL import Image


def _diff(a, b):
    return abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2])


def _mask(img, step=24, drift=70):
    """Foreground mask of a reference: everything not reachable from the image border through
    background. Backgrounds can be gradients or mixed (white JPEG margins around a black icon), so
    the fill follows small colour steps but never drifts far from the border colour it started at."""
    from collections import deque
    w, h = img.size
    px = img.load()
    seed = {}
    queue = deque()
    for x in range(w):
        for y in (0, h - 1):
            if (x, y) not in seed:
                seed[(x, y)] = px[x, y]
                queue.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if (x, y) not in seed:
                seed[(x, y)] = px[x, y]
                queue.append((x, y))
    while queue:
        x, y = queue.popleft()
        c, s = px[x, y], seed[(x, y)]
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in seed:
                n = px[nx, ny]
                # Dark gunmetal sits close to black backgrounds; be strict there.
                dark = sum(s) < 90
                if _diff(n, c) < (step // 3 if dark else step) and _diff(n, s) < (drift // 3 if dark else drift):
                    seed[(nx, ny)] = s
                    queue.append((nx, ny))
    m = Image.new('L', img.size, 255)
    mp = m.load()
    for x, y in seed:
        mp[x, y] = 0
    # Holes enclosed by the gun (trigger guards, thumbholes) are never reached by the fill; on bright
    # backgrounds clear anything that is near-identical to the background colour.
    border = sorted(seed[(x, 0)] for x in range(w))
    bg = border[len(border) // 2]
    if sum(bg) > 600:
        for y in range(h):
            for x in range(w):
                if _diff(px[x, y], bg) < 24:
                    mp[x, y] = 0
    return _largest_parts(m)


def _largest_parts(m, keep=0.02):
    """Drop specks: keep connected foreground parts of at least `keep` of the biggest one."""
    from collections import deque
    w, h = m.size
    mp = m.load()
    label = {}
    parts = []
    for y in range(h):
        for x in range(w):
            if mp[x, y] and (x, y) not in label:
                pts = [(x, y)]
                label[(x, y)] = len(parts)
                q = deque(pts)
                while q:
                    cx, cy = q.popleft()
                    for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                        if 0 <= nx < w and 0 <= ny < h and mp[nx, ny] and (nx, ny) not in label:
                            label[(nx, ny)] = len(parts)
                            pts.append((nx, ny))
                            q.append((nx, ny))
                parts.append(pts)
    if not parts:
        return m
    big = max(len(p) for p in parts)
    for p in parts:
        if len(p) < keep * big:
            for x, y in p:
                mp[x, y] = 0
    return m


def side_polygon(e):
    """Corners of an element projected on the z/y plane, with its rotation about x applied."""
    import math
    (_, y0, z0), (_, y1, z1) = e['from'], e['to']
    pts = [(z0, y0), (z1, y0), (z1, y1), (z0, y1)]
    r = e.get('rotation')
    if r and r['angle'] and r['axis'] == 'x':
        a = math.radians(r['angle'])
        oz, oy = r['origin'][2], r['origin'][1]
        pts = [(oz + (y - oy) * math.sin(a) + (z - oz) * math.cos(a),
                oy + (y - oy) * math.cos(a) - (z - oz) * math.sin(a)) for z, y in pts]
    return pts


def _lift(rgb):
    """Menu-icon renders are dark and desaturated next to in-game lighting; lift them a little so the
    photo-textured faces don't read as flat grey."""
    from PIL import ImageEnhance
    rgb = ImageEnhance.Brightness(rgb).enhance(1.08)
    rgb = ImageEnhance.Contrast(rgb).enhance(1.15)
    return ImageEnhance.Color(rgb).enhance(1.2)


def _fill_holes(rgb, alpha):
    """Fill background pixels of a face crop with the surrounding gun colour (normalised blur), so a
    face never mixes photo and procedural material in two different tones."""
    from PIL import ImageChops, ImageFilter
    if alpha.getextrema()[0] >= 250:
        return rgb.convert('RGBA')
    r = max(2, max(rgb.size) // 3)
    weighted = ImageChops.multiply(rgb, Image.merge('RGB', (alpha,) * 3)).filter(ImageFilter.BoxBlur(r))
    weight = alpha.filter(ImageFilter.BoxBlur(r))
    wp, fp = weight.load(), weighted.load()
    fill = Image.new('RGB', rgb.size)
    pp = fill.load()
    for y in range(rgb.height):
        for x in range(rgb.width):
            w = max(wp[x, y], 1) / 255
            pp[x, y] = tuple(min(255, int(c / w)) for c in fp[x, y])
    out = Image.composite(rgb, fill, alpha)
    return out.convert('RGBA')


class PhotoRef:
    def __init__(self, path, elements, flip=None):
        img = Image.open(path).convert('RGB')
        self.mask = _mask(img)
        bbox = self.mask.getbbox()
        self.img = img.crop(bbox)
        self.mask = self.mask.crop(bbox)
        pts = [p for e in elements for p in side_polygon(e)]
        self.zmin = min(z for z, _ in pts)
        self.zmax = max(z for z, _ in pts)
        self.ymin = min(y for _, y in pts)
        self.ymax = max(y for _, y in pts)
        self.flip = self._best_flip(elements) if flip is None else flip

    def _px(self, z, y, flip):
        w, h = self.img.size
        u = (z - self.zmin) / (self.zmax - self.zmin)
        if flip:
            u = 1 - u
        v = (self.ymax - y) / (self.ymax - self.ymin)
        return u * (w - 1), v * (h - 1)

    def _best_flip(self, elements):
        """Muzzle is -Z; pick the reference orientation whose silhouette overlaps the model's best."""
        w, h = self.img.size
        sil = Image.new('L', (w, h))
        sp = sil.load()
        for e in elements:
            if 'rotation' in e:
                continue
            for flip in (False,):
                x0, y0 = self._px(e['from'][2], e['to'][1], flip)
                x1, y1 = self._px(e['to'][2], e['from'][1], flip)
                for yy in range(int(y0), int(y1) + 1):
                    for xx in range(int(min(x0, x1)), int(max(x0, x1)) + 1):
                        if 0 <= xx < w and 0 <= yy < h:
                            sp[xx, yy] = 255
        mp = self.mask.load()

        def iou(mirror):
            inter = union = 0
            for yy in range(0, h, 2):
                for xx in range(0, w, 2):
                    a = sp[xx, yy] > 0
                    b = mp[(w - 1 - xx) if mirror else xx, yy] > 0
                    inter += a and b
                    union += a or b
            return inter / max(1, union)
        return iou(True) > iou(False)

    def region(self, e, face, pw, ph, rotated_ok=False):
        """Reference crop for an east/west face as a pw x ph image (None if mostly background).
        Rotated elements are skipped unless rotated_ok (then the crop is only good for its colour)."""
        if face not in ('east', 'west'):
            return None
        if 'rotation' in e and e['rotation']['angle'] != 0:
            if not rotated_ok:
                return None
            pts = side_polygon(e)
            zs, ys = [p[0] for p in pts], [p[1] for p in pts]
            e = {'from': [0, min(ys), min(zs)], 'to': [0, max(ys), max(zs)]}
        z0, z1 = e['from'][2], e['to'][2]
        y0, y1 = e['from'][1], e['to'][1]
        ax, ay = self._px(z0, y1, self.flip)
        bx, by = self._px(z1, y0, self.flip)
        box = (int(min(ax, bx)), int(ay), int(max(ax, bx)) + 1, int(by) + 1)
        if box[2] - box[0] < 2 or box[3] - box[1] < 2:
            return None
        m = self.mask.crop(box)
        hist = m.histogram()
        if hist[255] < 0.5 * sum(hist):
            return None
        crop = self.img.crop(box).resize((pw, ph), Image.BILINEAR).convert('RGBA')
        # Shrink the mask a little so the anti-aliased background halo around the gun never shows.
        from PIL import ImageFilter
        alpha = m.filter(ImageFilter.MinFilter(3)).resize((pw, ph), Image.BILINEAR)
        crop = _fill_holes(_lift(crop.convert('RGB')), alpha)
        # Minecraft's east face runs -Z..+Z left to right as seen from +X when the texture is not
        # mirrored; the west face is seen from -X, so it reads mirrored.
        if (face == 'west') != self.flip:
            crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        return crop
