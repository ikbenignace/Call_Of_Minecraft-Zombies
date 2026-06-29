#!/usr/bin/env python3
"""
Render every gun (or any item-def category) to a single labelled contact-sheet PNG so you can
eyeball all models before uploading the pack — no BlockBench, no server needed. Open the output
in any image viewer.

Usage:
  python3 tools/render_all.py [--cat gun] [--pack resourcepack/comz-pack] [--out dist/gun_contact_sheet.png]

--cat is the subfolder under assets/comz/items/ (e.g. gun, perk, powerup, throwable, machine).
"""
import argparse
import glob
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import model_preview as mp  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cat", default="gun")
    ap.add_argument("--pack", default="resourcepack/comz-pack")
    ap.add_argument("--out", default=None)
    ap.add_argument("--cell", type=int, default=240)
    ap.add_argument("--cols", type=int, default=5)
    a = ap.parse_args()

    out = a.out or f"dist/{a.cat}_contact_sheet.png"
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    tmp = os.path.join(os.path.dirname(out) or ".", f"_cs_{a.cat}")
    os.makedirs(tmp, exist_ok=True)

    itemdefs = sorted(glob.glob(f"{a.pack}/assets/comz/items/{a.cat}/*.json"))
    keys = []
    for idf in itemdefs:
        key = os.path.basename(idf)[:-5]
        if key.endswith("_pap"):  # skip pack-a-punch twins; they mostly mirror the base
            continue
        ref = json.load(open(idf)).get("model", {}).get("model")
        if not ref:
            continue
        mpath = mp.os.path  # silence linters
        model_file = os.path.join(a.pack, "assets", ref.split(":")[0] if ":" in ref else "minecraft",
                                  "models", (ref.split(":")[-1]) + ".json")
        png = os.path.join(tmp, key + ".png")
        try:
            mp.render(model_file, png, a.cell, a.pack)
            keys.append((key, png))
        except Exception as e:
            print(f"render fail {key}: {e}", file=sys.stderr)
            keys.append((key, None))

    cols = a.cols
    rows = (len(keys) + cols - 1) // cols
    lab = 22
    W, H = cols * a.cell, rows * (a.cell + lab)
    sheet = Image.new("RGBA", (W, H), (18, 18, 22, 255))
    d = ImageDraw.Draw(sheet)
    for i, (key, png) in enumerate(keys):
        cx = (i % cols) * a.cell
        cy = (i // cols) * (a.cell + lab)
        d.rectangle([cx, cy, cx + a.cell, cy + lab], fill=(40, 40, 48, 255))
        d.text((cx + 4, cy + 5), key, fill=(230, 230, 235, 255))
        if png and os.path.isfile(png):
            sheet.alpha_composite(Image.open(png).convert("RGBA"), (cx, cy + lab))
        else:
            d.text((cx + 6, cy + lab + 8), "RENDER FAILED", fill=(255, 80, 80, 255))
    sheet.convert("RGB").save(out)
    print(f"wrote {out}  ({len(keys)} models, {sheet.size[0]}x{sheet.size[1]})")


if __name__ == "__main__":
    main()
