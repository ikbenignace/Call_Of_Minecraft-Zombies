#!/usr/bin/env python3
"""
Imports Black Ops 2 melee, grenade, explosion, pickup and dry-fire audio into the resource pack and
registers the matching sound events. Idempotent: regenerates the same outputs on every run.

Needs the same ffmpeg + soundfile/numpy setup as import_weapon_sounds.py.

Usage: python3 -I tools/sounds/import_fx_sounds.py [--src DIR] [--ffmpeg PATH]

Source files are only ever read as audio (ffmpeg decode); nothing from the download folders is executed.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import import_weapon_sounds as weapons  # noqa: E402

FX_DIR = os.path.join(weapons.PACK_SOUND_DIR, "custom/fx")
PEAK_DB = -2.0
MAX_SECONDS = 4.0
FADE_SECONDS = 0.15

MELEE = "Melee Weapons/Bowie Knife/"
GRENADE = "Fire and Explosions/grenade/"
BOUNCE = GRENADE + "bounce/frag/earth/"


def wav(path):
    return path if path.endswith(".wav") else f"{path}.LN65.pc.snd.wav"


# event -> (pack output folder/name stem, source stems, max seconds)
FX = {
    "custom.melee.swing": ("melee/swing", [f"{MELEE}bowie_swing/bowie_swing_0{i}" for i in range(3)], 1.2),
    "custom.melee.hit": ("melee/hit", [f"{MELEE}bowie_stab/bowie_stab_0{i}" for i in range(3)], 1.2),
    "custom.grenade.pin": ("grenade/pin", [GRENADE + "foley/pin/pin"], 1.0),
    "custom.grenade.throw": ("grenade/throw", [GRENADE + "foley/throw/gren_throw"], 1.0),
    "custom.grenade.bounce": ("grenade/bounce", [f"{BOUNCE}earth_0{i}" for i in range(5)], 1.0),
    "custom.grenade.explode": ("grenade/explode", [GRENADE + "explosion/explode_close/explode_close_00",
                                                   GRENADE + "explosion/explode/explode_00"], 4.0),
    "custom.grenade.semtex_stick": ("grenade/semtex_stick", [GRENADE + "sticky/stick/stick_00"], 1.0),
    "custom.grenade.semtex_alert": ("grenade/semtex_alert", [GRENADE + "sticky/alert/semtex_alert"], 1.0),
    "custom.grenade.semtex_pin": ("grenade/semtex_pin", [GRENADE + "sticky/alert/semtex_pin_pull"], 1.5),
    "custom.grenade.semtex_charge": ("grenade/semtex_charge", [GRENADE + "sticky/alert/semtex_charge"], 2.0),
    "custom.claymore.plant": ("claymore/plant", [GRENADE + "claymore/claymore_plant_00"], 1.5),
    "custom.claymore.alert": ("claymore/alert", [GRENADE + "claymore/alert/alert_00"], 1.0),
    "custom.weapon.dryfire": ("weapon/dryfire", ["Handguns/dry_fire/plr/dry_fire_00",
                                                 "Assualt Rifles/dry_fire/plr/dry_fire_00",
                                                 "Shotguns/dry_fire/plr/dry_fire_00"], 0.6),
    "custom.pickup.ammo": ("pickup/ammo", ["_Pickup Items/ammo/pickup_00", "_Pickup Items/ammo/pickup_01"], 1.0),
    "custom.pickup.weapon": ("pickup/weapon", ["_Pickup Items/weapon/fly_weapon_pickup_00"], 1.0),
    "custom.zombie.spawn": ("zombie/spawn", [GRENADE + f"explosion/debris/dirt/dirt_0{i}" for i in range(3)], 1.6),
    "custom.trap.flame": ("trap/flame", ["Fire and Explosions/flame_thrower/wpn_flamethrower_loop_npc.LL65.pc.snd.wav"], 2.5),
}


def clip(tools, src, out, max_seconds):
    wavfile = os.path.join(tools.tmp, "fx.wav")
    fade_start = max(0.0, max_seconds - FADE_SECONDS)
    flt = (f"{weapons.SILENCE_TRIM},asetpts=PTS-STARTPTS,atrim=0:{max_seconds},"
           f"afade=t=out:st={fade_start}:d={FADE_SECONDS}")
    tools.run(["-i", src, "-ac", "1", "-ar", "44100", "-af", flt, "-c:a", "pcm_f32le", wavfile])
    tools.encode_normalised(wavfile, out, PEAK_DB)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=os.path.expanduser("~/Downloads/BO2 Weapon Sounds"))
    ap.add_argument("--ffmpeg", default="/opt/homebrew/bin/ffmpeg")
    args = ap.parse_args()

    tools = weapons.Tools(args.ffmpeg)
    events = {}
    try:
        for event, (stem, sources, max_seconds) in FX.items():
            names = []
            for i, source in enumerate(sources, 1):
                src = os.path.join(args.src, wav(source))
                if not os.path.isfile(src):
                    raise SystemExit(f"missing source: {src}")
                name = f"{stem}_{i:02d}" if len(sources) > 1 else stem
                clip(tools, src, os.path.join(FX_DIR, name + ".ogg"), max_seconds)
                names.append(f"custom/fx/{name}")
            events[event] = weapons.event_entry(names)
            print(event, len(names))
    finally:
        tools.close()

    with open(weapons.PACK_SOUNDS_JSON) as fh:
        pack = json.load(fh)
    pack.update(events)
    pack["entity.generic.explode"]["sounds"] = ["custom/fx/grenade/explode_01", "custom/fx/grenade/explode_02"]
    weapons.write_json(weapons.PACK_SOUNDS_JSON, pack, trailing_newline=False)


if __name__ == "__main__":
    sys.exit(main())
