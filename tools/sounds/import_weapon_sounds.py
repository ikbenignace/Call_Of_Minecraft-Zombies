#!/usr/bin/env python3
"""
Imports Black Ops 2 weapon audio into the resource pack and wires every gun in guns.json to a
shoot/reload sound event. Idempotent: regenerates the same outputs on every run.

Needs ffmpeg plus either an ffmpeg built with libvorbis or the soundfile+numpy python packages
(e.g. a venv: python3 -m venv /tmp/v && /tmp/v/bin/pip install soundfile numpy, then run /tmp/v/bin/python -I).

Usage: python3 -I tools/sounds/import_weapon_sounds.py [--src DIR] [--zombies DIR] [--ffmpeg PATH]

Source files are only ever read as audio (ffmpeg decode); nothing from the download folders is executed.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
PACK_SOUNDS_JSON = os.path.join(REPO, "resourcepack/comz-pack/assets/minecraft/sounds.json")
PACK_SOUND_DIR = os.path.join(REPO, "resourcepack/comz-pack/assets/minecraft/sounds")
BO2_DIR = os.path.join(PACK_SOUND_DIR, "custom/weapons/bo2")
ZOMBIE_DIR = os.path.join(PACK_SOUND_DIR, "mob/zombie")
CORE_SOUNDS_JSON = os.path.join(REPO, "Core/src/main/resources/sounds.json")
GUNS_JSON = os.path.join(REPO, "Core/src/main/resources/guns.json")

SHOT_PEAK_DB = -1.0
RELOAD_PEAK_DB = -3.0
SHOT_MAX_SECONDS = 1.4
SHOT_FADE_SECONDS = 0.25
PART_MAX_SECONDS = 1.2
SILENCE_TRIM = "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.004"

# Reload part positions: "out" = 0.1 s, "in" = 55% of reload_time, "end" = 85%; floats are fractions of reload_time.
POS_OUT_SECONDS = 0.1
POS_FRACTION = {"in": 0.55, "end": 0.85}

AR_SHARED = "Assualt Rifles/_Shared Reload Sounds/"
PIST_SHARED = "Handguns/_Shared Reload Sounds/"
SG_SHARED = "Shotguns/_Shared Reload Sounds/"
SN_SHARED = "Snipers/_Shared Weapon Sounds/reload/"


def shared(prefix, name):
    return f"{prefix}{name}.LN65.pc.snd.wav"


def ar(name):
    return shared(AR_SHARED, f"fly_assault_{name}")


def pist(name):
    return shared(PIST_SHARED, f"fly_pistol_{name}")


def sniper(name):
    return shared(SN_SHARED, f"fly_sniper_{name}")


def f(path, stem):
    return f"{path}/{stem}.LN65.pc.snd.wav"


def assault_reload(bolt=False):
    parts = [(ar("mag_out"), "out"), (ar("mag_in"), "in")]
    if bolt:
        parts += [(ar("bb"), 0.80), (ar("bf"), 0.89)]
    else:
        parts.append((ar("charge"), "end"))
    return parts


def pistol_reload():
    return [(pist("mag_out"), "out"), (pist("mag_in"), "in"), (pist("sf"), "end")]


# S12 only ships a silenced shot in the source pack, so it borrows the KSG shot.
# id -> {gun, shots, reload, shoot_event (optional override when the shot stays BO1)}
BO2 = {
    "b23r": dict(gun="B23R",
                 shots=["Handguns/B23R/plr/shot/wpn_beretta_fire_plr.LN65.pc.snd.wav"],
                 reload=pistol_reload()),
    "executioner": dict(gun="Executioner",
                        shots=["Handguns/Executioner/plr/shot/wpn_judge_shot_plr.LN65.pc.snd.wav"],
                        reload=[("Handguns/Executioner/foley/fly_judge_empty.LN65.pc.snd.wav", "out"),
                                ("Handguns/Executioner/foley/load/fly_judge_load_00.LN65.pc.snd.wav", 0.40),
                                ("Handguns/Executioner/foley/load/fly_judge_load_01.LN65.pc.snd.wav", 0.60),
                                (pist("hammer"), 0.88)]),
    "fiveseven": dict(gun="Five-Seven",
                      shots=["Handguns/Five Seven/plr/shot/wpn_fiveseven_shot_plr.LN65.pc.snd.wav"],
                      reload=pistol_reload()),
    "kap40": dict(gun="Kap-40",
                  shots=["SMGs/KAP 40/plr/shot/wpn_kard_shot_plr.LN65.pc.snd.wav"],
                  reload=pistol_reload()),
    "m1216": dict(gun="M1216",
                  shots=["Shotguns/M1216/plr/shot/wpn_srm1216_fire.LN65.pc.snd.wav"],
                  reload=[("Shotguns/M1216/foley/fly_srm1216_mag_out.LN65.pc.snd.wav", "out"),
                          ("Shotguns/M1216/foley/fly_srm1216_mag_in.LN65.pc.snd.wav", "in"),
                          ("Shotguns/M1216/foley/fly_srm1216_twist.LN65.pc.snd.wav", "end")]),
    "r870": dict(gun="R870 MCS",
                 shots=["Shotguns/R870 MCS/fire/wpn_870mcs_00.LN65.pc.snd.wav"],
                 reload=[("Shotguns/remington870/reload/fly_rem870_pull.LN65.pc.snd.wav", "out"),
                         (SG_SHARED + "load/load_00.LN65.pc.snd.wav", 0.28),
                         (SG_SHARED + "load/load_01.LN65.pc.snd.wav", 0.42),
                         (SG_SHARED + "load/load_02.LN65.pc.snd.wav", 0.56),
                         ("Shotguns/remington870/reload/fly_rem870_push.LN65.pc.snd.wav", 0.78),
                         (SG_SHARED + "rechamber/rechamber_00.LN65.pc.snd.wav", 0.90)]),
    "s12": dict(gun="S12",
                shots=["Shotguns/KSG/plr/shot/wpn_ksg_fire_plr.LN65.pc.snd.wav"],
                reload=[(SG_SHARED + "load/load_03.LN65.pc.snd.wav", 0.15),
                        (SG_SHARED + "load/load_04.LN65.pc.snd.wav", 0.35),
                        (SG_SHARED + "load/load_05.LN65.pc.snd.wav", 0.55),
                        (SG_SHARED + "fly_shotgun_pump_back.LN65.pc.snd.wav", 0.76),
                        (SG_SHARED + "fly_shotgun_pump_forward.LN65.pc.snd.wav", 0.86)]),
    "an94": dict(gun="AN-94",
                 shots=["Assualt Rifles/AN94/plr/shot/wpn_an94_shot_plr.LN65.pc.snd.wav"],
                 reload=assault_reload(bolt=True)),
    "m16a1": dict(gun="Colt M16A1", shots=None, shoot_event="custom.weapon.shoot.m16",
                  reload=[("Assualt Rifles/M16/reload/fly_m16_mag_out.LN65.pc.snd.wav", "out"),
                          ("Assualt Rifles/M16/reload/fly_m16_mag_in.LN65.pc.snd.wav", "in"),
                          ("Assualt Rifles/M16/reload/fly_m16_charge.LN65.pc.snd.wav", "end")]),
    "m8a1": dict(gun="M8A1",
                 shots=["Assualt Rifles/M8A1/plr/shot/wpn_xm8_shot_plr.LN65.pc.snd.wav"],
                 reload=assault_reload()),
    "m27": dict(gun="M27",
                shots=["Assualt Rifles/M27/plr/shot/wpn_m27_shot_plr.LN65.pc.snd.wav"],
                reload=assault_reload()),
    "mtar": dict(gun="MTAR",
                 shots=["Assualt Rifles/MTAR/plr/shot/wpn_tar21_shot_plr.LN65.pc.snd.wav"],
                 reload=assault_reload()),
    "smr": dict(gun="SMR",
                shots=["Assualt Rifles/SMR/plr/shot/wpn_saritch_shot_plr.LN65.pc.snd.wav"],
                reload=assault_reload(bolt=True)),
    "type25": dict(gun="Type 25",
                   shots=["Assualt Rifles/Type 25/plr/shot/wpn_type95_shot_plr.LN65.pc.snd.wav"],
                   reload=assault_reload()),
    "hamr": dict(gun="HAMR",
                 shots=["LMG/hamr/shot/wpn_hamr_shot_plr.LN65.pc.snd.wav"],
                 reload=[("LMG/mk48/foley/fly_mk48_mag_out.LN65.pc.snd.wav", "out"),
                         ("LMG/mk48/foley/fly_mk48_mag_in.LN65.pc.snd.wav", "in"),
                         ("LMG/m60/reload/fly_m60_charge.LN65.pc.snd.wav", "end")]),
    "lsat": dict(gun="LSAT",
                 shots=["LMG/lsat/plr/shot/wpn_lsat_shot_plr.LN65.pc.snd.wav"],
                 reload=[("LMG/m60/reload/fly_m60_box_off.LN65.pc.snd.wav", "out"),
                         ("LMG/m60/reload/fly_m60_box_on.LN65.pc.snd.wav", "in"),
                         ("LMG/m60/reload/fly_m60_charge.LN65.pc.snd.wav", "end")]),
    "mk48": dict(gun="Mk 48",
                 shots=["LMG/mk48/plr/shot/wpn_mk48_fire_plr.LN65.pc.snd.wav"],
                 reload=[("LMG/mk48/foley/fly_mk48_open.LN65.pc.snd.wav", 0.05),
                         ("LMG/mk48/foley/fly_mk48_mag_out.LN65.pc.snd.wav", 0.18),
                         ("LMG/mk48/foley/fly_mk48_lift_belt.LN65.pc.snd.wav", 0.38),
                         ("LMG/mk48/foley/fly_mk48_mag_in.LN65.pc.snd.wav", "in"),
                         ("LMG/mk48/foley/fly_mk48_seat_belt.LN65.pc.snd.wav", 0.70),
                         ("LMG/mk48/foley/fly_mk48_close.LN65.pc.snd.wav", 0.82)]),
    "m60": dict(gun="M60",
                shots=["LMG/m60/plr/last_shot_00.LN65.pc.snd.wav"],
                reload=[("LMG/m60/reload/fly_m60_open.LN65.pc.snd.wav", 0.04),
                        ("LMG/m60/reload/fly_m60_box_off.LN65.pc.snd.wav", 0.14),
                        ("LMG/m60/reload/fly_m60_pull_belt.LN65.pc.snd.wav", 0.28),
                        ("LMG/m60/reload/fly_m60_place_belt.LN65.pc.snd.wav", 0.44),
                        ("LMG/m60/reload/fly_m60_box_on.LN65.pc.snd.wav", "in"),
                        ("LMG/m60/reload/fly_m60_lid_close.LN65.pc.snd.wav", 0.72),
                        ("LMG/m60/reload/fly_m60_charge.LN65.pc.snd.wav", "end")]),
    "chicom": dict(gun="Chicom CQB",
                   shots=["SMGs/Chicom CQB/plr/shot/wpn_qcw05_shot_plr.LN65.pc.snd.wav"],
                   reload=[("SMGs/Chicom CQB/reload/fly_kiparis_mag_out.LN65.pc.snd.wav", "out"),
                           ("SMGs/Chicom CQB/reload/fly_kiparis_mag_in.LN65.pc.snd.wav", "in"),
                           ("SMGs/Chicom CQB/reload/fly_kiparis_charge.LN65.pc.snd.wav", "end")]),
    "pdw57": dict(gun="PDW-57",
                  shots=["Assualt Rifles/PDW 57/plr/shot/wpn_pdw57_fire_plr.LN65.pc.snd.wav"],
                  reload=assault_reload()),
    "msmc": dict(gun="MSMC",
                 shots=["SMGs/MSMC/shot/wpn_msmc_shot_plr.LN65.pc.snd.wav"],
                 reload=assault_reload()),
    "skorpion": dict(gun="Skorpion EVO",
                     shots=["SMGs/Skorpion Evo/plr/shot/wpn_evoskorpion_fire_plr.LN65.pc.snd.wav"],
                     reload=[("SMGs/Skorpion Evo/reload/fly_skorpion_mag_out.LN65.pc.snd.wav", "out"),
                             ("SMGs/UZI/reload/fly_uzi_mag_in.LN65.pc.snd.wav", "in"),
                             ("SMGs/UZI/reload/fly_uzi_charge.LN65.pc.snd.wav", "end")]),
    "vectork10": dict(gun="Vector K10",
                      shots=["SMGs/Vector K10/plr/shot/wpn_vectork10_shot_plr.LN65.pc.snd.wav"],
                      reload=assault_reload()),
    "peacekeeper": dict(gun="Peacekeeper",
                        shots=["SMGs/MP7/plr/shot/wpn_mp7_shot_plr.LN65.pc.snd.wav"],
                        reload=[("SMGs/MP7/foley/fly_mp7_mag_out.LN65.pc.snd.wav", "out"),
                                ("SMGs/MP7/foley/fly_mp7_mag_in.LN65.pc.snd.wav", "in"),
                                ("SMGs/MP7/foley/fly_mp7_charge.LN65.pc.snd.wav", "end")]),
    "barret": dict(gun="Barret 50KCAL",
                   shots=["Snipers/XPR - 50/shot/wpn_as50_fire_plr.LN65.pc.snd.wav"],
                   reload=[("Snipers/Wa2000/reload/fly_wa2000_mag_out.LN65.pc.snd.wav", "out"),
                           ("Snipers/Wa2000/reload/fly_wa2000_mag_in.LN65.pc.snd.wav", "in"),
                           ("Snipers/Wa2000/reload/fly_wa2000_dial_scope.LN65.pc.snd.wav", "end")]),
    "dsr50": dict(gun="DSR 50",
                  shots=["Snipers/DSR/plr/shot/wpn_dsr_fire_plr.LN65.pc.snd.wav"],
                  reload=[(sniper("mag_out"), "out"), (sniper("mag_in"), "in"),
                          (sniper("bu"), 0.72), (sniper("bb"), 0.80),
                          (sniper("bf"), 0.88), (sniper("bd"), 0.95)]),
    "svuas": dict(gun="SVU-AS",
                  shots=["Snipers/SVU/shot/wpn_svt_fire_plr.LN65.pc.snd.wav"],
                  reload=[(sniper("mag_out"), "out"), (sniper("mag_in"), "in"),
                          (sniper("bb"), 0.80), (sniper("bf"), 0.89)]),
    "chinalake": dict(gun="China Lake",
                      shots=["Fire and Explosions/gren_launcher/china_lake/plr/shot/shot_00.LN65.pc.snd.wav"],
                      reload=[("Fire and Explosions/gren_launcher/attachment/reload/fly_grenlaunch_open.LN65.pc.snd.wav", 0.08),
                              ("Fire and Explosions/gren_launcher/attachment/reload/fly_grenlaunch_gren_out.LN65.pc.snd.wav", 0.25),
                              ("Fire and Explosions/gren_launcher/attachment/reload/fly_grenlaunch_gren_in.LN65.pc.snd.wav", "in"),
                              ("Fire and Explosions/gren_launcher/attachment/reload/fly_grenlaunch_close.LN65.pc.snd.wav", "end")]),
    "m72": dict(gun="M72 LAW",
                shots=["Fire and Explosions/rocket/rpg/plr/shot/wpn_rpg_shot_plr.LN65.pc.snd.wav"],
                reload=[("Fire and Explosions/rocket/rpg/foley/fly_rpg_latch.LN65.pc.snd.wav", "out"),
                        ("Fire and Explosions/rocket/rpg/foley/fly_rpg_slide.LN65.pc.snd.wav", "in"),
                        ("Fire and Explosions/rocket/rpg/foley/fly_rpg_flip.LN65.pc.snd.wav", "end")]),
    "crossbow": dict(gun="Crossbow",
                     shots=[f"Misc Gun/crossbow/plr/shot/shot_0{i}.LN65.pc.snd.wav" for i in range(3)],
                     reload=[("Misc Gun/crossbow/reload/fly_crossbow_draw.LN65.pc.snd.wav", 0.15),
                             ("Misc Gun/crossbow/reload/fly_crossbow_lay_bolt.LN65.pc.snd.wav", "in"),
                             ("Misc Gun/crossbow/reload/fly_crossbow_latch.LN65.pc.snd.wav", "end")]),
    "balknife": dict(gun="Ballistic Knife",
                     shots=[f"Melee Weapons/Ballistic Knife/plr/shot/shot_0{i}.LN65.pc.snd.wav" for i in range(3)],
                     reload=[("Melee Weapons/Ballistic Knife/foley/bal_knife_load.LN65.pc.snd.wav", 0.45)]),
}

# Guns that keep BO1 audio: gun -> (shoot event, reload event)
BO1 = {
    "M1911": ("m1911", "reload_start.m1911"),
    "Python": ("python", "reload_start.python"),
    "CZ75": ("cz75", "reload_start.cz75"),
    "Olympia": ("db", "reload_start.db"),
    "Stakeout": ("stake", "shotgun_load"),
    "SPAS-12": ("spas", "reload_end.spas"),
    "HS-10": ("hs10", "reload_end.hs10"),
    "FAL": ("fal", "reload_start.fal"),
    "M14": ("m14", "reload_start.m14"),
    "FAMAS": ("famas", "reload_start.famas"),
    "Galil": ("galil", "reload_start.galil"),
    "AUG": ("aug", "reload_start.aug"),
    "Commando": ("comm", "reload_start.comm"),
    "G11": ("g11", "reload_start.g11"),
    "RPK": ("rpk", "reload_start.rpk"),
    "HK21": ("hk21", "reload_start.hk21"),
    "MP5": ("mp5", "reload_start.mp5"),
    "MP5K": ("mp5", "reload_start.mp5"),
    "AK-74u": ("ak74", "reload_start.ak74"),
    "PM63": ("pm63", "reload_start.pm63"),
    "MPL": ("mpl", "reload_start.mpl"),
    "Spectre": ("spectre", "reload_start.spectre"),
    "MP40": ("mp40", "reload_start.mp40"),
    "Dragunov": ("drag", "reload_start.drag"),
    "L96A1": ("l96", "reload_start.l96"),
    "Ray Gun": ("ray", "reload_start.ray"),
    "Thundergun": ("thun", "reload_start.thun"),
    "V-R11": ("vr11", "reload_start.vr11"),
}

# No matching audio anywhere: gun -> (shoot event, reload event, note). Reported as "approximate".
APPROXIMATE = {
    "Makarov": ("custom.weapon.shoot.cz75", "custom.weapon.reload_start.cz75", "BO1 CZ75"),
    "ASP": ("custom.weapon.shoot.cz75", "custom.weapon.reload_start.cz75", "BO1 CZ75"),
    "RPD": ("custom.weapon.shoot.rpk", "custom.weapon.reload_start.rpk", "BO1 RPK"),
    "Wunderwaffe DG-2": ("custom.weapon.shoot.thun", "custom.weapon.reload_start.thun", "BO1 Thundergun"),
    "Winter's Howl": ("custom.weapon.shoot.thun", "custom.weapon.reload_start.thun", "BO1 Thundergun"),
    "Wave Gun": ("custom.weapon.shoot.ray", "custom.weapon.reload_start.ray", "BO1 Ray Gun"),
    "Scavenger": ("custom.weapon.shoot.l96", "custom.weapon.reload_start.l96", "BO1 L96A1"),
    "31-79 JGb215": ("custom.weapon.shoot.bo2.chinalake", "custom.weapon.reload.bo2.chinalake", "BO2 China Lake"),
}


class Tools:
    def __init__(self, ffmpeg):
        self.ffmpeg = ffmpeg
        self.tmp = tempfile.mkdtemp(prefix="comz_snd_")

    def close(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run(self, args):
        r = subprocess.run([self.ffmpeg, "-hide_banner", "-nostdin", "-y", *args],
                           capture_output=True, text=True)
        if r.returncode != 0:
            raise RuntimeError(f"ffmpeg failed: {' '.join(args)}\n{r.stderr[-800:]}")
        return r.stderr

    def max_volume_db(self, wav):
        err = self.run(["-i", wav, "-af", "volumedetect", "-f", "null", "-"])
        m = re.search(r"max_volume: (-?[\d.]+) dB", err)
        return float(m.group(1)) if m else 0.0

    def has_libvorbis(self):
        if not hasattr(self, "_libvorbis"):
            r = subprocess.run([self.ffmpeg, "-hide_banner", "-encoders"], capture_output=True, text=True)
            self._libvorbis = "libvorbis" in r.stdout
        return self._libvorbis

    def encode_ogg(self, wav, out, gain_db=0.0):
        """Mono Vorbis. ffmpeg's built-in vorbis encoder is stereo-only, so without libvorbis we use libsndfile."""
        os.makedirs(os.path.dirname(out), exist_ok=True)
        if self.has_libvorbis():
            self.run(["-i", wav, "-af", f"volume={gain_db:.2f}dB", "-ac", "1", "-ar", "44100",
                      "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", out])
            return
        import numpy as np
        import soundfile as sf  # pip install soundfile numpy
        data, rate = sf.read(wav, dtype="float32")
        if data.ndim > 1:
            data = data.mean(axis=1)
        data = np.clip(data * (10 ** (gain_db / 20)), -1.0, 1.0)
        sf.write(out, data, rate, format="OGG", subtype="VORBIS")

    def encode_normalised(self, wav, out, peak_db):
        self.encode_ogg(wav, out, peak_db - self.max_volume_db(wav))

    def shot(self, src, out):
        wav = os.path.join(self.tmp, "shot.wav")
        fade_start = SHOT_MAX_SECONDS - SHOT_FADE_SECONDS
        flt = (f"{SILENCE_TRIM},asetpts=PTS-STARTPTS,atrim=0:{SHOT_MAX_SECONDS},"
               f"afade=t=out:st={fade_start}:d={SHOT_FADE_SECONDS}")
        self.run(["-i", src, "-ac", "1", "-ar", "44100", "-af", flt, "-c:a", "pcm_f32le", wav])
        self.encode_normalised(wav, out, SHOT_PEAK_DB)

    def reload(self, parts, reload_time, out):
        inputs, chains, labels = [], [], []
        for i, (src, pos) in enumerate(parts):
            at = POS_OUT_SECONDS if pos == "out" else (POS_FRACTION[pos] if isinstance(pos, str) else pos) * reload_time
            delay_ms = int(round(at * 1000))
            inputs += ["-i", src]
            chains.append(f"[{i}:a]aresample=44100,aformat=channel_layouts=mono,{SILENCE_TRIM},"
                          f"asetpts=PTS-STARTPTS,atrim=0:{PART_MAX_SECONDS},"
                          f"adelay={delay_ms}:all=1[p{i}]")
            labels.append(f"[p{i}]")
        mix = f"{''.join(labels)}amix=inputs={len(parts)}:normalize=0:duration=longest,atrim=0:{reload_time}[m]"
        wav = os.path.join(self.tmp, "reload.wav")
        self.run([*inputs, "-filter_complex", ";".join(chains + [mix]), "-map", "[m]",
                  "-ar", "44100", "-c:a", "pcm_f32le", wav])
        self.encode_normalised(wav, out, RELOAD_PEAK_DB)

    def mono_copy(self, src, out):
        wav = os.path.join(self.tmp, "mono.wav")
        self.run(["-i", src, "-ac", "1", "-c:a", "pcm_f32le", wav])
        self.encode_ogg(wav, out)

    def channels(self, path):
        probe = os.path.join(os.path.dirname(self.ffmpeg), "ffprobe")
        r = subprocess.run([probe, "-v", "error", "-select_streams", "a:0", "-show_entries",
                            "stream=channels", "-of", "csv=p=0", path], capture_output=True, text=True)
        return int(r.stdout.strip())


def load_guns():
    with open(GUNS_JSON) as fh:
        data = json.load(fh)
    guns = []
    for category in data.values():
        for gun in category["guns"]:
            pap = (gun.get("pack_a_punch_gun") or {}).get("name")
            guns.append((gun["name"], pap, float(gun["reload_time"])))
    return guns


def event_entry(sounds):
    return {"category": "master", "sounds": sounds}


def build_weapons(tools, src, guns):
    reload_times = {name: rt for name, _, rt in guns}
    events = {}
    mapping = {}
    for gid, spec in BO2.items():
        base = os.path.join(BO2_DIR, gid)
        shoot_event = spec.get("shoot_event")
        if spec["shots"]:
            names = []
            for i, rel in enumerate(spec["shots"]):
                name = "shoot" if i == 0 else f"shoot_{i + 1}"
                tools.shot(os.path.join(src, rel), os.path.join(base, name + ".ogg"))
                names.append(f"custom/weapons/bo2/{gid}/{name}")
            shoot_event = f"custom.weapon.shoot.bo2.{gid}"
            events[shoot_event] = event_entry(names)
        parts = [(os.path.join(src, rel), pos) for rel, pos in spec["reload"]]
        tools.reload(parts, reload_times[spec["gun"]], os.path.join(base, "reload.ogg"))
        reload_event = f"custom.weapon.reload.bo2.{gid}"
        events[reload_event] = event_entry([f"custom/weapons/bo2/{gid}/reload"])
        mapping[spec["gun"]] = (shoot_event, reload_event)
        print(f"  bo2 {gid}")
    return events, mapping


def zombie_import(tools, zsrc):
    added = {"say": [], "hurt": [], "death": []}
    if not os.path.isdir(zsrc):
        print(f"zombie source missing: {zsrc}")
        return added
    for fn in sorted(os.listdir(zsrc)):
        m = re.fullmatch(r"(say|hurt|death)(\d*)\.ogg", fn)
        if not m:
            continue
        kind, num = m.group(1), m.group(2) or "1"
        dest = os.path.join(ZOMBIE_DIR, f"bo_{kind}{num}.ogg")
        srcp = os.path.join(zsrc, fn)
        if tools.channels(srcp) == 1:
            shutil.copyfile(srcp, dest)
        else:
            tools.mono_copy(srcp, dest)
        added[kind].append(f"mob/zombie/bo_{kind}{num}")
    key = lambda s: int(re.search(r"(\d+)$", s).group(1))
    return {k: sorted(v, key=key) for k, v in added.items()}


def merge_unique(existing, additions):
    return existing + [a for a in additions if a not in existing]


def write_json(path, data, trailing_newline=True):
    with open(path, "w") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        if trailing_newline:
            fh.write("\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=os.path.expanduser("~/Downloads/BO2 Weapon Sounds"))
    ap.add_argument("--zombies", default=os.path.expanduser("~/Downloads/CoD Zombies/assets/minecraft/sounds/mob/zombie"))
    ap.add_argument("--ffmpeg", default="/opt/homebrew/bin/ffmpeg")
    ap.add_argument("--skip-zombies", action="store_true")
    args = ap.parse_args()

    guns = load_guns()
    tools = Tools(args.ffmpeg)
    try:
        print("weapons")
        events, bo2_map = build_weapons(tools, args.src, guns)
        zombies = {} if args.skip_zombies else zombie_import(tools, args.zombies)
    finally:
        tools.close()

    with open(PACK_SOUNDS_JSON) as fh:
        pack = json.load(fh)
    pack.update(events)
    for kind, event in (("say", "entity.zombie.ambient"), ("hurt", "entity.zombie.hurt"),
                        ("death", "entity.zombie.death")):
        if zombies.get(kind):
            pack[event]["sounds"] = merge_unique(pack[event]["sounds"], zombies[kind])
    write_json(PACK_SOUNDS_JSON, pack, trailing_newline=False)

    with open(CORE_SOUNDS_JSON) as fh:
        core = json.load(fh)
    old_guns = core["guns"]
    new_guns = {"_default": old_guns.get("_default", {"shoot": "", "reload": ""}),
                "_defaultPaP": old_guns.get("_defaultPaP", {"shoot": "custom.weapon.shoot.pap", "reload": ""}),
                "_papLayer": {"shoot": "custom.weapon.shoot.pap"}}
    report = {"bo2": [], "bo1": [], "approx": [], "unmapped": []}
    known = set(pack)
    for name, pap, _ in guns:
        if name in bo2_map:
            shoot, reload = bo2_map[name]
            report["bo2"].append(name)
        elif name in BO1:
            shoot, reload = (f"custom.weapon.shoot.{BO1[name][0]}", f"custom.weapon.{BO1[name][1]}")
            report["bo1"].append(name)
        elif name in APPROXIMATE:
            shoot, reload, note = APPROXIMATE[name]
            report["approx"].append(f"{name} ({note})")
        else:
            report["unmapped"].append(name)
            continue
        for ev in (shoot, reload):
            if ev not in known:
                raise SystemExit(f"event {ev} for {name} is not registered in the pack sounds.json")
        entry = {"shoot": shoot, "reload": reload}
        new_guns[name] = dict(entry)
        if pap:
            new_guns[pap] = dict(entry)
    for key, value in old_guns.items():
        new_guns.setdefault(key, value)
    core["guns"] = new_guns
    write_json(CORE_SOUNDS_JSON, core)

    print(json.dumps(report, indent=1))


if __name__ == "__main__":
    sys.exit(main())
