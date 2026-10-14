#!/usr/bin/env python3
"""
Turn the BO2 perk HUD shaders (IWI, DXT5) into the mob-effect icons the plugin uses as perk icons.

    python3 -I tools/textures/perk_icons.py "<folder with *.iwi>"

Each perk is shown in game as a hidden vanilla potion effect (see PerkType); this overwrites that
effect's icon in the pack with the real perk shader.
"""
import io
import os
import struct
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'resourcepack/comz-pack/assets/minecraft/textures/mob_effect')

EFFECT_FOR_SHADER = {
    'specialty_juggernaut_zombies': 'hero_of_the_village',
    'specialty_fastreload_zombies': 'luck',
    'specialty_quickrevive_zombies': 'unluck',
    'specialty_doubletap_zombies': 'dolphins_grace',
    'specialty_marathon_zombies': 'trial_omen',
    'specialty_divetonuke_zombies': 'fire_resistance',
    'specialty_ads_zombies': 'saturation',
    'specialty_mulekick_zombies': 'bad_omen',
    'minimap_icon_electric_cherry': 'conduit_power',
    'specialty_vulture_zombies': 'water_breathing',
    'specialty_tombstone_zombies': 'wind_charged',
    'minimap_icon_chugabud': 'raid_omen',
}
FOURCC = {0x0B: (b'DXT1', 8), 0x0C: (b'DXT3', 16), 0x0D: (b'DXT5', 16)}


def read_iwi(path):
    """Largest mip of a BO2 (version 0x1B) IWI image. Mips are stored smallest first."""
    data = open(path, 'rb').read()
    fourcc, block = FOURCC[data[4]]
    w, h = struct.unpack('<HH', data[6:10])
    top = data[-(w // 4) * (h // 4) * block:]
    header = struct.pack('<4sIIIIIII44sII4sIIIIIIIIII', b'DDS ', 124, 0x1007, h, w, len(top), 0, 1,
                         b'\0' * 44, 32, 4, fourcc, 0, 0, 0, 0, 0, 0x1000, 0, 0, 0, 0)
    return Image.open(io.BytesIO(header + top)).convert('RGBA')


def main(src):
    for shader, effect in EFFECT_FOR_SHADER.items():
        path = os.path.join(src, shader + '.iwi')
        if not os.path.exists(path):
            print('missing', shader)
            continue
        read_iwi(path).save(os.path.join(OUT, effect + '.png'))
        print(shader, '->', effect)


if __name__ == '__main__':
    main(sys.argv[1])
