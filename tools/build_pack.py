#!/usr/bin/env python3
"""
Zip the COM:Z resource pack into a distributable .zip and print its SHA-1.

pack.mcmeta is placed at the ZIP ROOT (Minecraft requires this — do NOT nest the
comz-pack/ folder inside the zip). The SHA-1 is what goes in the server config
(resourcePack.sha1) so clients verify/cache the download.

Usage: python3 tools/build_pack.py [--src resourcepack/comz-pack] [--out dist/comz-pack.zip]
"""
import argparse
import hashlib
import os
import zipfile


def build(src, out):
    src = os.path.abspath(src)
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    files = []
    for root, _dirs, names in os.walk(src):
        for n in sorted(names):
            if n == ".DS_Store":
                continue
            files.append(os.path.join(root, n))
    files.sort()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for f in files:
            z.write(f, os.path.relpath(f, src))  # arcname relative to pack root
    sha1 = hashlib.sha1(open(out, "rb").read()).hexdigest()
    size = os.path.getsize(out)
    print(f"wrote {out}")
    print(f"files: {len(files)}  size: {size/1024:.0f} KiB")
    print(f"sha1:  {sha1}")
    return sha1


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default="resourcepack/comz-pack")
    ap.add_argument("--out", default="dist/comz-pack.zip")
    a = ap.parse_args()
    build(a.src, a.out)
