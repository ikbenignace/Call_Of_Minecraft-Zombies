#!/usr/bin/env python3
"""
Convert a TacZ / Bedrock-geometry gun model (minecraft:geometry, bones+cubes) into a vanilla
Minecraft Java item model usable as a held item.

Vanilla item models can't represent arbitrary rotations, so this bakes the full bone+cube
transform onto each cube's 8 corners and emits the axis-aligned bounding box of those corners as
a vanilla element (positions preserved, tilt approximated). Per-face UVs are carried over. The
whole model is recentred to x~8 and rebased so it sits like the pack's other guns; final display
transforms come from modelkit's canonical block (applied separately or here).

Usage:
  python3 tools/tacz_convert.py <geo.json> --out <model.json> --texref custom/custom/<key> \
      [--order zyx] [--barrel +z]
"""
import argparse, json, math, os


def rot_matrix(rx, ry, rz, order="zyx"):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    M = {"x": Rx, "y": Ry, "z": Rz}
    out = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    for ax in order:  # leftmost applied last
        out = matmul(out, M[ax])
    return out


def matmul(A, B):
    return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(M, v):
    return [sum(M[i][k] * v[k] for k in range(3)) for i in range(3)]


def apply(M, pivot, p):
    d = [p[i] - pivot[i] for i in range(3)]
    r = mv(M, d)
    return [r[i] + pivot[i] for i in range(3)]


VANILLA_ANGLES = [-45.0, -22.5, 0.0, 22.5, 45.0]


def net_rot_matrix(bone_name, by, cube_rot, order):
    """Compose the net ROTATION matrix (no translation) of cube_rot under its bone chain."""
    M = rot_matrix(*cube_rot, order=order) if cube_rot else [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    name = bone_name
    while name and name in by:
        b = by[name]
        r = b.get("rotation")
        if r and any(r):
            M = matmul(rot_matrix(*r, order=order), M)
        name = b.get("parent")
    return M


def match_single_axis(M):
    """If M is (close to) a rotation about one axis by a vanilla angle, return (angle, axis) else None."""
    best = None
    for axis in ("x", "y", "z"):
        for ang in VANILLA_ANGLES:
            R = rot_matrix(*( {"x": (ang, 0, 0), "y": (0, ang, 0), "z": (0, 0, ang)}[axis] ))
            err = sum(abs(M[i][j] - R[i][j]) for i in range(3) for j in range(3))
            if err < 0.02 and (best is None or err < best[2]):
                best = (ang, axis, err)
    if best and best[0] == 0.0:
        return (0.0, "x")  # identity -> no effective rotation
    return (best[0], best[1]) if best else None


def transform_point_chain(bone_name, by, p, order):
    """Apply the full bone chain to point p: rotate around each bone's pivot from this bone up to
    root (child rotation first, then parents). Pivots are in shared model space, so nested rotations
    around DIFFERENT pivots compose correctly only when applied sequentially (not collapsed to one)."""
    name = bone_name
    while name and name in by:
        b = by[name]
        rot = b.get("rotation")
        if rot and any(rot):
            p = apply(rot_matrix(*rot, order=order), b.get("pivot", [0, 0, 0]), p)
        name = b.get("parent")
    return p


def convert(geo_path, out_path, texref, order):
    geo = json.load(open(geo_path))["minecraft:geometry"][0]
    desc = geo["description"]
    tw = desc.get("texture_width", 64)
    th = desc.get("texture_height", 64)
    bones = geo["bones"]
    by = {b["name"]: b for b in bones}

    # rig/non-gun bones to skip (ammo + hand position markers; gun parts are kept)
    SKIP = ("bullet", "lefthand_pos", "righthand_pos")

    elements = []
    FACES = ["north", "south", "east", "west", "up", "down"]
    for b in bones:
        if any(tok in b["name"].lower() for tok in SKIP):
            continue
        for c in b.get("cubes", []):
            o = c["origin"]
            s = c["size"]
            crot = c.get("rotation")
            cpiv = c.get("pivot", [0, 0, 0])

            faces = {}
            for f in FACES:
                uvd = c.get("uv", {})
                if isinstance(uvd, dict) and f in uvd:
                    u, v = uvd[f]["uv"]
                    uw, uh = uvd[f]["uv_size"]
                    faces[f] = {"uv": [u, v, u + uw, v + uh], "texture": "#0"}
                else:
                    faces[f] = {"uv": [0, 0, 1, 1], "texture": "#0"}

            # Net rotation of this cube (bone chain + own rotation). If it's a single-axis vanilla
            # angle we can keep the box SHAPE + that rotation exactly (preserves angled grips/mags
            # instead of fattening them into a slab). Otherwise fall back to AABB of the 8 corners.
            M = net_rot_matrix(b["name"], by, crot, order)
            single = match_single_axis(M)
            # transformed centre (full chain incl. pivots/translations)
            cen_local = [o[a] + s[a] / 2 for a in range(3)]
            if crot and any(crot):
                cen_local = apply(rot_matrix(*crot, order=order), cpiv, cen_local)
            cen = transform_point_chain(b["name"], by, cen_local, order)

            if single is not None and single[0] != 0.0:
                ang, axis = single
                frm = [round(cen[a] - s[a] / 2, 4) for a in range(3)]
                to = [round(cen[a] + s[a] / 2, 4) for a in range(3)]
                el = {"from": frm, "to": to,
                      "rotation": {"angle": ang, "axis": axis, "origin": [round(x, 4) for x in cen]},
                      "faces": faces}
            else:
                # axis-aligned (identity net rotation) OR compound -> AABB of corners
                corners = []
                for i in (0, 1):
                    for j in (0, 1):
                        for k in (0, 1):
                            p = [o[0] + i * s[0], o[1] + j * s[1], o[2] + k * s[2]]
                            if crot and any(crot):
                                p = apply(rot_matrix(*crot, order=order), cpiv, p)
                            corners.append(transform_point_chain(b["name"], by, p, order))
                frm = [round(min(cc[a] for cc in corners), 4) for a in range(3)]
                to = [round(max(cc[a] for cc in corners), 4) for a in range(3)]
                el = {"from": frm, "to": to, "faces": faces}
            if all(abs(el["to"][a] - el["from"][a]) < 1e-4 for a in range(3)):
                continue
            elements.append(el)

    # trim outlier cubes (attachment/view anchors that sit far from the gun body). Use the median
    # cube centre + a generous per-axis window so the gun stays intact but stray cubes are dropped.
    import statistics
    def ctr(e, a):
        return (e["from"][a] + e["to"][a]) / 2
    med = [statistics.median(ctr(e, a) for e in elements) for a in range(3)]
    win = [11, 17, 30]  # x, y, z half-windows (blocks*?) around median to keep
    before = len(elements)
    elements = [e for e in elements if all(abs(ctr(e, a) - med[a]) <= win[a] for a in range(3))]
    if before != len(elements):
        print(f"  trimmed {before - len(elements)} outlier cubes")

    # Fit into Minecraft's valid element range. Vanilla REJECTS the whole model (renders purple) if
    # any coord is outside [-16, 32]. Scale uniformly so the largest span fits in ~46 and centre every
    # axis at 8 -> all coords land in [-15, 31]. Display scale (below) compensates for the shrink.
    ax = [[min(e[k][a] for e in elements for k in ("from", "to")),
           max(e[k][a] for e in elements for k in ("from", "to"))] for a in range(3)]
    span = max(hi - lo for lo, hi in ax) or 1
    sc = min(1.0, 46.0 / span)
    mids = [(lo + hi) / 2 for lo, hi in ax]
    def remap(p):
        return [round((p[a] - mids[a]) * sc + 8, 3) for a in range(3)]
    for e in elements:
        for k in ("from", "to"):
            e[k] = remap(e[k])
        if "rotation" in e:  # rotation origin lives in the same space -> remap it too
            e["rotation"]["origin"] = remap(e["rotation"]["origin"])
    xs = [e[k][0] for e in elements for k in ("from", "to")]
    ys = [e[k][1] for e in elements for k in ("from", "to")]
    zs = [e[k][2] for e in elements for k in ("from", "to")]

    # display: scale so a ~length-L gun sits in hand like the pack's guns (cz75 ~22 long -> 0.3)
    maxdim = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)) or 22
    s = round(6.6 / maxdim, 3)
    display = {
        "thirdperson_righthand": {"translation": [0, 2, -1], "scale": [s, s, s]},
        "thirdperson_lefthand": {"translation": [0, 2, -1], "scale": [s, s, s]},
        "firstperson_righthand": {"rotation": [0, -90, 0], "translation": [1, 2, -3], "scale": [round(s * 1.4, 3)] * 3},
        "firstperson_lefthand": {"rotation": [0, -90, 0], "translation": [1, 2, -3], "scale": [round(s * 1.4, 3)] * 3},
        "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [round(s * 1.5, 3)] * 3},
        "ground": {"scale": [round(s * 0.9, 3)] * 3},
        "head": {"scale": [1, 1, 1]},
        "fixed": {"scale": [round(s * 1.4, 3)] * 3},
    }
    model = {
        "credit": "Made with Blockbench",
        "texture_size": [tw, th],
        "textures": {"0": texref, "particle": texref},
        "elements": elements,
        "display": display,
    }
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    json.dump(model, open(out_path, "w"), indent=2)
    span = (round(max(xs) - min(xs), 1), round(max(ys) - min(ys), 1), round(max(zs) - min(zs), 1))
    print(f"{os.path.basename(geo_path)} -> {out_path}: {len(elements)} elements, span(x,y,z)={span}, tex={tw}x{th}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("geo")
    ap.add_argument("--out", required=True)
    ap.add_argument("--texref", required=True)
    ap.add_argument("--order", default="zyx")
    a = ap.parse_args()
    convert(a.geo, a.out, a.texref, a.order)
