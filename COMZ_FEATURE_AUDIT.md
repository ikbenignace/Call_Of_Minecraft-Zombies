# COM:Z — CoD Zombies Feature Audit

Branch audited: `fix/open-issues-bugs` (tip `1b53f25`).

This is a 1:1 parity checklist against the Call of Duty: Zombies (BO1/BO2-era)
feature set. Items are marked **Present** (implemented with real behavior), **Cosmetic**
(icon/model only, no gameplay effect), or **Missing**. References point at the file that
implements the behavior.

---

## 1. Perks

The perk system rides each perk on a unique vanilla `PotionEffectType` (infinite, particles
hidden) reskinned via the resource pack, with real behavior applied separately in the relevant
game system. The 4-perk default cap is config-driven (`config.perks.maxPerks`).

| Perk | Status | Notes / file |
|---|---|---|
| Jugger-Nog | Present | Divides incoming zombie melee damage by `juggernogHealth`. `EntityListener.java` |
| Speed Cola | Present | Multiplies reload time by `speedColaReloadMultiplier`. `GunInstance.java` |
| Double Tap 2.0 | Present | Fire-rate boost via `doubleTapFireMultiplier` (the 2.0 interpretation). `GunInstance.java` |
| Quick Revive | Present | Co-op revive-speed boost **and** solo self-revive (`soloQuickReviveUses`). `DownedPlayer*.java` |
| Stamin-Up | Present | Sets walk speed to 0.28 (no SPEED potion, so no FOV shift). `PerkManager.java` |
| PhD Flopper | Present | Fall-damage immunity + explosion/AoE on fall. `PlayerListener.java` |
| Deadshot Daiquiri | Present | `deadshotHeadshotMultiplier` on headshots. `WeaponListener.java`, `WMDamageListener.java` |
| Mule Kick | Present | Expands gun-slot cap 3 → 4. `PlayerWeaponManager.java` |
| Electric Cherry | Present | Electric AoE on reload. `GunInstance.java`, `WMDamageListener.java` |
| Vulture Aid | Present | Rolling points/ammo drops on zombie kills. `Game.java` (`tryVultureDrop`) |
| Tombstone Soda | Present | Snapshot perks/weapons on down; reclaim at body. `DownedPlayer*.java` |
| Who's Who | Present | Full BO2 solo ghost self-revive. `WhosWhoGhost.java`, `DownedPlayerManager.java` |
| Der WunderfizZ | Present | Random-perk dispenser (not a perk itself). `PerkMachineSign.java` |
| **Widow's Wine** | **Missing** | No enum constant, no behavior. |

**Verdict:** 12 of 13 canonical perks have real behavior; Widow's Wine is the only gap.

---

## 2. Power-Ups

Drop infrastructure: configurable `powerUpDropPercentage` (~2%), per-round cap
(`maxPowerUpsPerRound`), ground cap (`maxPowerUpsOnGround`) with oldest-eviction, 30s lifetime
with countdown nameplate + themed beam. Dog-round and boss kills drop guaranteed rewards
bypassing the random roll.

| Power-up | Status | Notes / file |
|---|---|---|
| Max Ammo | Present | Refills reserve ammo for all players. `PowerUpDropListener.java` |
| Insta-Kill | Present | Timed one-shots (crawler exempt). `Game.damageMob` |
| Nuke | Present | Clears zombies + 400 pts to **every** player (800 under Double Points). |
| Double Points | Present | Timed 2× points. |
| Carpenter | Present | Repairs all barriers. (NB: CoD also awards 200 pts — currently points-free.) |
| Fire Sale | Present | Multi-box + 10-pt box cost, timed. |
| Death Machine | Present | Temporary minigun to the picker. `giveDeathMachine` |
| Bonfire Sale | Present | Fire Sale + global PaP cost override (`bonfirePaPCost`). |
| Bonus Points (Point Cache) | Present | `bonusPointsAmount` to all players. |
| Random Perk (Free Perk) | Present | Grants a random perk. |
| **Max Points** | **Missing** | |
| **Blood Money** | **Missing** | |
| **Full Power** | **Missing** | |
| **Zombie Blood** | **Missing** | |

**Verdict:** 10 of 15 canonical power-ups implemented.

---

## 3. Wonder / Special Weapons

| Weapon | Status | Notes / file |
|---|---|---|
| Ray Gun | Present | Splash AoE + PaP variant. `WeaponListener.java` |
| Thundergun | Present | Knockback blast; PaP "Zeus Cannon". `WeaponListener.java` |
| Wunderwaffe DG-2 | Present | Chain lightning; PaP "DG-3 JZ". `WeaponListener.java` |
| Monkey Bomb | Present | Throwable lure + fuse explosion. `WeaponListener.java` |
| Death Machine | Present | Special gun granted by the power-up. |
| **Winter's Howl** | **Missing** | |
| **Gersh Device** | **Missing** | |
| **Matryoshka** | **Missing** | |
| **Emp Grenade / QED / Scavenger / VR-11 / Wave Gun** | **Missing** | |

Optional **WeaponMechanics integration** maps all standard guns + PaP variants to WM and honors
perks (`integration/wm/*`).

**Verdict:** 5 of ~14 wonder/special weapons implemented (the BO2 Kino/Five core set).

---

## 4. Major Systems

| System | Status | Notes |
|---|---|---|
| Round / spawn system | Present | CoD-style zombie health curve (linear→exponential), player-scaled board cap, dog rounds, boss rounds, last-zombie crawler hold. |
| Dog (hellhound) rounds | Present | Every-Nth-wave, fiery hostile hounds, guaranteed Max Ammo. Global fallback so never silently disabled. |
| Boss rounds | Present | George Romero + Brutus alternating; Brutus perk-disable; BossBar; guaranteed drops. `BossSpawner.java` |
| Barriers | Present | Proximity breaking, configurable break speed, consistent stage across blocks, repair points. |
| Doors / areas | Present | Per-door cost, power-locked doors, block restore on close. |
| Power switch | Present | Gates perks, PaP, powered doors. |
| Mystery Box | Present | Multi-box, Fire Sale, teddy relocate, monkey-bomb box-move, 950 pts (10 in Fire Sale). |
| Pack-a-Punch | Present | 5000 pts, teleporter-gated (opt-in), re-pack ammo refill, Bonfire override. |
| Teleporters | Present | Cooldown + charge-up + PaP-room access grant + pad zombie kill. |
| Wall buys / ammo | Present | Sign-based buy + refill. |
| Downed / revive | Present | Co-op proximity revive, bleed-out, downed state. |
| Solo QR self-revive | Present | Limited uses, configurable delay. |
| Who's Who ghost | Present | Solo-only ghost self-revive with proximity/timeout loop. |
| Crawler | Present | Low-HP slow zombie + poison gas; excluded from stuck-teleport. |
| Traps | Present | Buyable timed kill-zone, cooldown. `Trap*.java` |
| Buildables | Present | Generic parts framework; concrete Zombie Shield buildable. |
| Easter Egg quest engine | Present | Data-driven multi-step objective engine. `Quest*.java` |
| Perma-perks | Present | Persistent progression; Perma-Jugg + Perma-Quick-Revive (lite). |
| Bank | Present | Deposit (fee) + withdraw; persistent balance. |
| Fridge | Present | Store/retrieve one gun persistently. |
| Leaderboards / stats | Present | `leaderboards/*` |
| Kits / round rewards | Present | `kits/*` |
| Resource pack support | Present | Custom gun/perk/power-up models + sounds; optional WM integration. |
| In-game build mode | Present | Fly-mode arena editing, room progression, settings GUI. |

---

## 5. Known Minor Gaps vs CoD (not blocking, by design decision not to add this pass)

- **Carpenter** awards no points (CoD gives 200 to all players).
- **Insta-Kill** non-lethal hits award hit-points, not the kill bonus (CoD gives the kill value on every hit during Insta-Kill).
- **Wall-buy ammo refill** is not enforced as half the wall price (admin-defined per sign).
- No per-power-up rarity weighting (all enabled power-ups are equally weighted).
- Zombie spawn selection has no line-of-sight / out-of-view fog-of-war logic.
- No weapon-swap animation delay (Minecraft hotbar is instant).
- Missing perks/power-ups/wonder-weapons listed in sections 1–3.

---

## 6. Bug Audit Summary

A full-codebase analysis (core loop, perks/weapons/power-ups/machines, listeners/commands/config)
was performed. Of the candidate defects flagged, **11 real bugs were confirmed and fixed** on
this branch (see commit history), and 2 candidate "bugs" were verified to be **false positives**
(load-bearing code, not defects):

- `EntityListener.damage` trailing `e.setCancelled(true)` on mob damage — **NOT a bug.** All
  weapon/wonder-weapon/trap damage is applied through the custom `Game.damageMob()` ray-trace
  path, not Bukkit's native damage event. The unconditional cancel is required to prevent
  Bukkit's damage from double-applying on top of the custom model.
- `Game.damageMob` Insta-Kill round-advance condition — **NOT a bug.** `SpawnManager.removeEntity`
  already calls `nextWave()` (and drops the dog-round Max Ammo) when the mob list empties, so the
  in-`damageMob` re-check is intentional belt-and-suspenders guarded by `changingRound`.

The fixed bugs are documented in their commit messages.
