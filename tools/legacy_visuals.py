#!/usr/bin/env python3
"""
Port the visual side of a 1.19 resource pack to the layout modern clients read.

- gui/icons.png and gui/widgets.png atlases are sliced into textures/gui/sprites/...;
  only sprites whose pixels differ from the vanilla 1.19 atlas are written.
- Block, entity, effect, particle, painting and misc textures plus block models are
  copied when the modern client still reads that path, or a known renamed path.
  Files the target pack already has are kept.

Usage: python3 tools/legacy_visuals.py <legacy-pack-dir> <vanilla-1.19-client.jar> <vanilla-modern-client.jar>
           [--out resourcepack/comz-pack]
"""
import argparse
import io
import os
import shutil
import zipfile

from PIL import Image, ImageChops

HEART_COLUMNS = {
    "container": 16, "container_blinking": 25,
    "full": 52, "half": 61, "full_blinking": 70, "half_blinking": 79,
    "poisoned_full": 88, "poisoned_half": 97, "poisoned_full_blinking": 106, "poisoned_half_blinking": 115,
    "withered_full": 124, "withered_half": 133, "withered_full_blinking": 142, "withered_half_blinking": 151,
    "absorbing_full": 160, "absorbing_half": 169, "absorbing_full_blinking": 160, "absorbing_half_blinking": 169,
    "frozen_full": 178, "frozen_half": 187, "frozen_full_blinking": 178, "frozen_half_blinking": 187,
}


def hardcore_name(name):
    kind, _, rest = name.partition("_")
    if kind in ("full", "half"):
        return "hardcore_" + name
    return kind + "_hardcore" + ("_" + rest if rest else "")


def icon_rects():
    rects = {"hud/crosshair": (0, 0, 15, 15)}
    for name, x in HEART_COLUMNS.items():
        rects["hud/heart/" + name] = (x, 0, 9, 9)
        rects["hud/heart/" + hardcore_name(name)] = (x, 45, 9, 9)
    rects.update({
        "hud/heart/vehicle_container": (52, 9, 9, 9),
        "hud/heart/vehicle_full": (88, 9, 9, 9),
        "hud/heart/vehicle_half": (97, 9, 9, 9),
        "hud/armor_empty": (16, 9, 9, 9),
        "hud/armor_half": (25, 9, 9, 9),
        "hud/armor_full": (34, 9, 9, 9),
        "hud/air": (16, 18, 9, 9),
        "hud/air_bursting": (25, 18, 9, 9),
        "hud/food_empty": (16, 27, 9, 9),
        "hud/food_empty_hunger": (133, 27, 9, 9),
        "hud/food_full": (52, 27, 9, 9),
        "hud/food_half": (61, 27, 9, 9),
        "hud/food_full_hunger": (88, 27, 9, 9),
        "hud/food_half_hunger": (97, 27, 9, 9),
        "hud/experience_bar_background": (0, 64, 182, 5),
        "hud/experience_bar_progress": (0, 69, 182, 5),
        "hud/jump_bar_background": (0, 84, 182, 5),
        "hud/jump_bar_progress": (0, 89, 182, 5),
        "hud/hotbar_attack_indicator_background": (0, 94, 18, 18),
        "hud/hotbar_attack_indicator_progress": (18, 94, 18, 18),
        "hud/crosshair_attack_indicator_background": (36, 94, 16, 4),
        "hud/crosshair_attack_indicator_progress": (52, 94, 16, 4),
        "hud/crosshair_attack_indicator_full": (68, 94, 16, 16),
    })
    return rects


WIDGET_RECTS = {
    "hud/hotbar": (0, 0, 182, 22),
    "hud/hotbar_selection": (0, 22, 24, 23),
    "hud/hotbar_offhand_left": (24, 22, 29, 24),
    "hud/hotbar_offhand_right": (53, 22, 29, 24),
    "widget/button_disabled": (0, 46, 200, 20),
    "widget/button": (0, 66, 200, 20),
    "widget/button_highlighted": (0, 86, 200, 20),
}

ATLASES = {"gui/icons.png": icon_rects(), "gui/widgets.png": WIDGET_RECTS}


COPIED_DIRS = ("textures/block/", "textures/entity/", "textures/mob_effect/", "textures/particle/",
               "textures/painting/", "textures/misc/", "textures/models/armor/", "textures/item/empty_armor_slot_",
               "models/block/", "textures/custom/custom/")

# Models and block textures may be new files referenced by other models, so they need no vanilla counterpart.
FREE_DIRS = ("models/block/", "textures/block/", "textures/custom/custom/")

RENAMED = {
    "textures/models/armor/gold_layer_1.png": "textures/entity/equipment/humanoid/gold.png",
    "textures/models/armor/gold_layer_2.png": "textures/entity/equipment/humanoid_leggings/gold.png",
    "textures/models/armor/chainmail_layer_1.png": "textures/entity/equipment/humanoid/chainmail.png",
    "textures/models/armor/chainmail_layer_2.png": "textures/entity/equipment/humanoid_leggings/chainmail.png",
    "textures/entity/spider_eyes.png": "textures/entity/spider/spider_eyes.png",
    "textures/entity/rabbit/caerbannog.png": "textures/entity/rabbit/rabbit_caerbannog.png",
    "textures/item/empty_armor_slot_helmet.png": "textures/gui/sprites/container/slot/helmet.png",
    "textures/item/empty_armor_slot_chestplate.png": "textures/gui/sprites/container/slot/chestplate.png",
    "textures/item/empty_armor_slot_leggings.png": "textures/gui/sprites/container/slot/leggings.png",
    "textures/item/empty_armor_slot_boots.png": "textures/gui/sprites/container/slot/boots.png",
    "textures/item/empty_armor_slot_shield.png": "textures/gui/sprites/container/slot/shield.png",
}


def crop(image, rect):
    x, y, w, h = rect
    return image.crop((x, y, x + w, y + h))


def differs(a, b):
    return ImageChops.difference(a, b).getbbox() is not None


def port(legacy_dir, vanilla_jar, out_dir):
    textures = os.path.join(legacy_dir, "assets", "minecraft", "textures")
    sprites = os.path.join(out_dir, "assets", "minecraft", "textures", "gui", "sprites")
    written = 0
    with zipfile.ZipFile(vanilla_jar) as jar:
        for atlas, rects in ATLASES.items():
            path = os.path.join(textures, atlas)
            if not os.path.exists(path):
                continue
            custom = Image.open(path).convert("RGBA")
            vanilla = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + atlas))).convert("RGBA")
            scale = custom.width // vanilla.width
            for sprite, rect in rects.items():
                piece = crop(custom, tuple(v * scale for v in rect))
                if not differs(piece, crop(vanilla, rect).resize(piece.size, Image.NEAREST)):
                    continue
                target = os.path.join(sprites, sprite + ".png")
                os.makedirs(os.path.dirname(target), exist_ok=True)
                piece.save(target)
                written += 1
    inventory = os.path.join(textures, "gui", "container", "inventory.png")
    if os.path.exists(inventory):
        target = os.path.join(out_dir, "assets", "minecraft", "textures", "gui", "container", "inventory.png")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        Image.open(inventory).convert("RGBA").save(target)
        written += 1
    print(f"wrote {written} gui textures under {out_dir}")


def copy_visuals(legacy_dir, modern_jar, out_dir):
    with zipfile.ZipFile(modern_jar) as jar:
        modern = {n[len("assets/minecraft/"):] for n in jar.namelist() if n.startswith("assets/minecraft/")}
    base = os.path.join(legacy_dir, "assets", "minecraft")
    copied, skipped = 0, []
    for root, _dirs, names in os.walk(base):
        for name in names:
            rel = os.path.relpath(os.path.join(root, name), base).replace(os.sep, "/")
            if not rel.startswith(COPIED_DIRS) or name in (".DS_Store", "desktop.ini"):
                continue
            meta = rel.endswith(".mcmeta")
            image = rel[:-len(".mcmeta")] if meta else rel
            target = RENAMED.get(image, image)
            if target not in modern and not rel.startswith(FREE_DIRS):
                if not meta:
                    skipped.append(rel)
                continue
            if meta:
                target += ".mcmeta"
            dest = os.path.join(out_dir, "assets", "minecraft", target)
            if os.path.exists(dest):
                continue
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copyfile(os.path.join(root, name), dest)
            copied += 1
    print(f"copied {copied} visual assets")
    for rel in skipped:
        print(f"  no modern path for {rel}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("legacy")
    ap.add_argument("vanilla_jar")
    ap.add_argument("modern_jar")
    ap.add_argument("--out", default="resourcepack/comz-pack")
    a = ap.parse_args()
    port(a.legacy, a.vanilla_jar, a.out)
    copy_visuals(a.legacy, a.modern_jar, a.out)
