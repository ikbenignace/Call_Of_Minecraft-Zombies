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


def sound_values(node):
    if isinstance(node, dict):
        for v in node.values():
            yield from sound_values(v)
    elif isinstance(node, list):
        for v in node:
            yield from sound_values(v)
    elif isinstance(node, str):
        yield node


def check_sounds(pack, sound_config, problems):
    """Every sound file the pack registers must exist, and every pack event the plugin's sounds.json names must be registered."""
    events = set()
    for sj in glob.glob(f"{pack}/assets/*/sounds.json"):
        ns = os.path.basename(os.path.dirname(sj))
        try:
            defs = json.load(open(sj))
        except Exception as e:
            problems.append(f"SOUNDS BAD JSON: {sj} :: {e}")
            continue
        for event, d in defs.items():
            events.add(f"{ns}:{event}")
            for s in d.get("sounds", []):
                entry = s if isinstance(s, dict) else {"name": s}
                if entry.get("type") == "event":
                    continue
                sns, _, path = entry["name"].rpartition(":")
                f = os.path.join(pack, "assets", sns or "minecraft", "sounds", path + ".ogg")
                if not os.path.isfile(f):
                    problems.append(f"SOUND FILE MISSING: {event} -> {f}")
    if not sound_config or not os.path.isfile(sound_config):
        return
    cfg = json.load(open(sound_config))
    cfg.pop("_comment", None)
    for v in sorted(set(sound_values(cfg))):
        if not (v.startswith("custom.") or ":" in v):
            continue
        key = v if ":" in v else "minecraft:" + v
        if key not in events:
            problems.append(f"SOUND EVENT NOT IN PACK: {v} (named in {sound_config})")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", default="resourcepack/comz-pack")
    ap.add_argument("--sound-config", default="Core/src/main/resources/sounds.json")
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

    # also validate every model file parses AND has Minecraft-valid geometry. Vanilla REJECTS a
    # model whole (renders purple/black) if any element coord is outside [-16, 32] or from > to.
    for mf in glob.glob(f"{pack}/assets/*/models/**/*.json", recursive=True):
        try:
            md = json.load(open(mf))
        except Exception as e:
            problems.append(f"MODEL BAD JSON: {mf} :: {e}")
            continue
        for e in md.get("elements", []):
            fr, to = e.get("from"), e.get("to")
            if not fr or not to:
                problems.append(f"ELEMENT missing from/to: {os.path.relpath(mf, pack)}")
                break
            if any(fr[a] > to[a] for a in range(3)):
                problems.append(f"ELEMENT from>to (inverted, MC rejects model): {os.path.relpath(mf, pack)}")
                break
            if any(c < -16 or c > 32 for c in fr + to):
                problems.append(f"ELEMENT coord outside [-16,32] (MC rejects model -> purple): {os.path.relpath(mf, pack)}")
                break
        # UV must stay within texture_size, else MC's bake fails ("Cannot compute translucency out
        # of bounds ... in WxH image") -> missing model. texture_size defaults to 16 when absent.
        ts = md.get("texture_size", [16, 16])
        tw, th = ts[0], ts[1]
        ooburst = None
        for e in md.get("elements", []):
            for fn, fd in e.get("faces", {}).items():
                uv = fd.get("uv")
                if not uv or len(uv) != 4:
                    continue
                if max(uv[0], uv[2]) > tw or max(uv[1], uv[3]) > th or min(uv) < 0:
                    ooburst = uv
                    break
            if ooburst:
                break
        if ooburst:
            problems.append(f"FACE uv {ooburst} out of texture_size {tw}x{th} (bake fails -> purple): {os.path.relpath(mf, pack)}")

    check_sounds(pack, a.sound_config, problems)

    if problems:
        print(f"\n{len(problems)} PROBLEM(S) — missing models render purple/black, missing sounds play nothing:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("OK — every item-definition resolves to an existing model + textures, and every sound resolves to audio.")


if __name__ == "__main__":
    main()
