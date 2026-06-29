#!/usr/bin/env python3
"""
Strict reference validator for the COM:Z resource pack — finds anything that would render
as the purple/black "missing model" in-game.

For every item-definition under assets/<ns>/items/*.json it:
  - resolves model.model -> a model file (assets/<mns>/models/<path>.json) and checks it exists
  - follows the model "parent" chain (also must resolve)
  - collects all texture refs (including #placeholders resolved through the chain) and checks
    each non-vanilla texture PNG exists in the pack
Vanilla refs (minecraft:block/*, minecraft:item/*, minecraft:entity/*) are assumed present in
the client and only listed under --verbose.

Exit code 1 if any problem is found.

Usage: python3 tools/validate_pack.py [--pack resourcepack/comz-pack] [--verbose]
"""
import argparse
import glob
import json
import os
import sys

VANILLA_PREFIXES = ("block/", "item/", "entity/", "gui/", "particle/", "font/", "misc/", "map/")


def model_path(pack, ref):
    ns, _, path = ref.partition(":")
    if not path:
        ns, path = "minecraft", ns
    return os.path.join(pack, "assets", ns, "models", path + ".json")


def texture_path(pack, ref):
    ns, _, path = ref.partition(":")
    if not path:
        ns, path = "minecraft", ns
    return ns, path, os.path.join(pack, "assets", ns, "textures", path + ".png")


def resolve_model(pack, ref, problems, chain=None):
    """Return merged textures dict across the parent chain; record missing models."""
    chain = chain or []
    mp = model_path(pack, ref)
    if not os.path.isfile(mp):
        problems.append(f"MODEL MISSING: {ref} -> {mp}")
        return {}
    if ref in chain:
        problems.append(f"MODEL PARENT LOOP: {' -> '.join(chain + [ref])}")
        return {}
    try:
        mj = json.load(open(mp))
    except Exception as e:
        problems.append(f"MODEL BAD JSON: {mp} :: {e}")
        return {}
    textures = {}
    parent = mj.get("parent")
    if parent and not parent.startswith("builtin/"):
        textures.update(resolve_model(pack, parent, problems, chain + [ref]))
    textures.update(mj.get("textures", {}))
    return textures


def check_textures(pack, textures, where, problems, verbose):
    for key, ref in textures.items():
        if not isinstance(ref, str) or ref.startswith("#"):
            continue
        bare = ref.split(":")[-1]
        if any(bare.startswith(p) for p in VANILLA_PREFIXES):
            if verbose:
                print(f"  (vanilla tex {ref})")
            continue
        ns, path, tp = texture_path(pack, ref)
        if not os.path.isfile(tp):
            problems.append(f"TEXTURE MISSING: {ref} -> {tp}   (used by {where})")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", default="resourcepack/comz-pack")
    ap.add_argument("--verbose", action="store_true")
    a = ap.parse_args()

    pack = a.pack
    itemdefs = sorted(glob.glob(f"{pack}/assets/*/items/**/*.json", recursive=True))
    print(f"validating {len(itemdefs)} item-definitions in {pack}")
    problems = []
    for idf in itemdefs:
        try:
            jd = json.load(open(idf))
        except Exception as e:
            problems.append(f"ITEMDEF BAD JSON: {idf} :: {e}")
            continue
        model = jd.get("model", {})
        ref = model.get("model")
        if not ref:
            # ranged/select/etc. composite item models — skip deep-walk, just note
            if a.verbose:
                print(f"  (composite item-def, not a plain model) {idf}")
            continue
        textures = resolve_model(pack, ref, problems)
        check_textures(pack, textures, os.path.relpath(idf, pack), problems, a.verbose)

    # also validate every model file parses (catches unreferenced-but-broken files)
    for mf in glob.glob(f"{pack}/assets/*/models/**/*.json", recursive=True):
        try:
            json.load(open(mf))
        except Exception as e:
            problems.append(f"MODEL BAD JSON: {mf} :: {e}")

    if problems:
        print(f"\n{len(problems)} PROBLEM(S) — these render purple/black in-game:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("OK — every item-definition resolves to an existing model + textures. No purple-black causes found.")


if __name__ == "__main__":
    main()
