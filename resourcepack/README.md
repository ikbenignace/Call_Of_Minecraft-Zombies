# COM:Z Custom Resource Pack

Custom gun/perk/power-up models + sounds for Call of Minecraft: Zombies, built from the
BOZ "Alpha V3.2.0.1" pack and modernized for the 1.21.4+ / 26.2 item-model system.

## How it hooks into the plugin

- **Models** — the plugin sets the `minecraft:item_model` component on items via
  `ItemMeta.setItemModel(comz:<key>)` (see `util/PackModels.java`). Guns read their key from
  `guns.json` (`item_model`); power-ups carry keys in `PowerUp`. Keys:
  `gun/<slug>`, `gun/<slug>_pap`, `powerup/<slug>`, `misc/box_teddy`.
  Without the pack installed the component is ignored and items keep their vanilla look.
- **Perk icons** — perks are not items; they render as reskinned vanilla potion-effect icons.
  Each perk maps to one `PotionEffectType` in `PerkType.java`; the pack overrides that effect's
  `assets/minecraft/textures/mob_effect/<effect>.png`. Source art lives in `perk-icons/` (named by
  effect) and is copied into the build by `build_pack.py`. Without the pack the player sees the
  plain vanilla effect icon.
- **Sounds** — `util/SoundUtil.java` plays a string that is either a Bukkit enum name OR a
  namespaced pack event. **All** sound mappings live in the dedicated **`sounds.json`** config
  (`plugins/COM_Zombies/sounds.json`): event sounds (`perk`, `box`, `round`, `packapunch`,
  `teleporter`, `door`, `trap`, `gun.buy`, `game.join`), per-power-up (`powerup.<TYPE>`), and
  per-gun overrides (`guns.<GunName>.shoot|reload`, with `_default`/`_defaultPaP` fallbacks that
  themselves fall back to guns.json `sound`/`reload_sound`). Defaults are vanilla enums; point any
  value at a `comz:` event to use the pack's audio. The pack also `replace:true`s several vanilla
  events (glass break, explosions, …) so those are remapped automatically.

### Safety when no pack is installed

If `config.resourcePack.enabled` is **false**, the plugin applies **no** custom item models — items
keep their plain vanilla material look, so clients never render a missing-model (black/purple) cube.
Likewise `SoundUtil` skips any `comz:`-namespaced sound event while the pack is disabled (vanilla
enum/`minecraft:` sounds always play). Enabling the pack is the server owner's assertion that the
hosted zip is valid; a per-player gate on download success would require packet-level item rewriting
and is out of scope.

## Build

```bash
python3 resourcepack/build_pack.py "<source pack dir>" "<output dir>"
# default source: ~/Downloads/.../Alpha V3.2.0.1 Resources, default output: resourcepack/build/comz-pack
```

The script is **pruning**: it copies only the models reachable from the item defs, the textures
those models use, and sounds minus the big unused folders (`custom/maps`, `custom/players`,
`custom/egg`, `ambient/cave`). The 310 MB raw pack becomes ~29 MB. It validates every item def
resolves to a real model and reports which guns use a closest-fallback model (see
`gun-model-mapping.md`).

- **Per-gun custom audio** is opt-in: set `guns.<GunName>.shoot`/`reload` in `sounds.json` to the
  pack's `comz:` weapon events (kept vanilla by default so no-pack servers still get audio).

## Host & enable

1. Zip the **contents** of the output dir (so `pack.mcmeta` is at the zip root), e.g.
   `cd <output dir> && zip -r ../comz-pack.zip .`
2. Upload the zip somewhere players can download it (GitHub release asset, web server, …).
3. `sha1sum comz-pack.zip` → copy the 40-char hash.
4. In `config.yml`:
   ```yaml
   config:
     resourcePack:
       enabled: true
       url: "https://.../comz-pack.zip"
       sha1: "<sha1 from step 3>"
       force: true
       kickOnDecline: false
   ```
5. Join a game → the client downloads the pack; watch the console for the
   `PlayerResourcePackStatusEvent` outcome. If the client warns about the version, adjust
   `PACK_FORMAT` / `SUPPORTED_*` in `build_pack.py` and rebuild.

## Known gaps / follow-ups

- **25 BO2 guns use closest-class fallback models** (see `gun-model-mapping.md`). Drop real
  BO2 models under `assets/minecraft/models/custom/item/bo2/<slug>` and repoint the slug in
  `build_pack.py`.
- **Perk icons are placeholders** — `perk-icons/*.png` are generated stand-ins
  (`generate_placeholders.py`). Drop real BO2 perk-bottle PNGs in (same effect filenames) and
  rerun `build_pack.py`; no code change needed.
- **Per-gun custom audio is opt-in** — set `sound`/`reload_sound` in `guns.json` to the pack's
  `comz:` events (kept as vanilla enums by default so servers without the pack still get audio).
- **Boss models** (George/Brutus) cannot be reskinned individually by a resource pack — a
  reskin would change all zombies/piglin-brutes. Only their sounds/name/glow distinguish them.
