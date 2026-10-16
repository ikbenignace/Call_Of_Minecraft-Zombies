# Generic map features

Goal: every mechanic of the classic zombies maps (WaW, BO1) can be built on ANY arena from configurable building
blocks. No feature knows a map name. Each feature:

- is saved per arena in `arenas.json` (`save_data` for placed things, `settings` for arena-wide options);
- has defaults in `config.yml` and/or a json catalogue in the plugin folder;
- can be placed and edited in build mode (`/zombies build`) and changed in `/zombies settings`;
- works on 1.14 → 26.x (spigot-api 1.14.4 in Core; newer API only through `util/Compat` or a `Displays`-style
  backend, checked by `sh gradlew -q checkApiCompat`).

## 1. Arena environment (settings)

Per-arena settings (with config.yml defaults):

| key | meaning |
|---|---|
| `gravity` | 1.0 = normal; 0.4 = moon. 1.20.5+ uses the GRAVITY attribute, older servers approximate with Slow Falling + Jump Boost. |
| `jump_boost` | extra jump height multiplier. |
| `force_night`, `fog` | existing night flag plus optional darkness/fog. |
| `special_enemies` | which special enemy types may appear and how (see 6). |
| `dog_round_every_x`, `teddy_bear_chance`, `powerup_settings` | existing. |
| `power_switches` | how many different Power switches must be pressed before the power turns on (default 1; Shangri-La uses 2). Each press shows "Power 1/2". |

## 2. Zones

Named box regions per arena (two corners, like doors), each with flags:

- `gravity` override (low-gravity area);
- `no_air` — players take suffocation damage unless wearing breathing gear (Moon P.E.S.);
- `air_pocket` — breathable inside any no-air zone (pressurised rooms on the moon), whatever the zone sizes;
- `damage` per second (lava, electricity floor);
- `name` shown on the HUD when entering ("Power Room").

Build mode: Zone tool (two clicks for corners, F cycles flag presets). Zones are also reused by traps and triggers.

## 3. Transport (ziplines, landers, elevators, minecarts)

A transport is an ordered list of waypoints plus options:

- `style`: `zipline` (hang under a rope, particles and a line display), `platform` (stand on a moving
  platform/lander), `cart` (seated ride), `teleport` (fade + instant, used for elevators between floors);
- `cost`, `requires_power`, `cooldown_seconds`, `speed`;
- `two_way` (the call point at the far end works too);
- `cooldown_group`: transports with the same group share one cooldown (Der Riese teleporters, Ascension landers);
- locked while a trigger has an `enable_teleporter` target naming its id or name (Five's Pack-a-Punch teleporter);
- `relock_after_use`: after every ride it locks again and those triggers reset, so it has to be re-linked (Kino);
- `stay_seconds` + `then` ([{`points`, `seconds`}]): after arriving, riders stay `stay_seconds`, then visit each `then` stop in turn (a random one of its `points`) for its `seconds` (Kino: 30 s at Pack-a-Punch, a few seconds in a side room, then back to the projector room);
- `call_points`: where players press F to call or ride (each resolves to the nearer end); none = both ends;
- riders are never sent back by the leave-the-arena check while riding;
- optional `on_arrival` trigger (see 7).

Build mode: Transport tool. Right-click adds waypoints, F switches style, shift-right-click finishes. Moving riders
use display entities and teleport interpolation, so nothing depends on a client mod.

## 4. Traps (typed)

The existing `Trap` (cost, kill radius, duration, cooldown) gains:

- `type` from a catalogue (`traps.json`): `electric` (barrier of sparks, lightning sound), `fire` (flames,
  burning), `flogger` (rotating arm, knockback kill), `centrifuge` (spinning, hits players too),
  `spikes`, `generic`. A type defines particles, sounds, whether it hurts players and the damage model;
- `region` (a zone) instead of a single radius, plus the activation sign/handle locations;
- `requires_power`.

Implemented as:

- `traps.json` (plugin folder, new types merged in on update): root `points_per_kill` (default 0, BO gives no
  points for trap kills) and `types.<id>` with `name`, `style` (`electric|fire|arm|spikes|turret|generic`; a turret shoots the nearest zombie in its area one round at a time), `cost`,
  `duration_seconds`, `cooldown_seconds`, `requires_power`, `damage_model` (`instant_kill|per_tick`),
  `mob_damage`/`mob_damage_percent`, `hit_interval_ticks`, `ignite_ticks`, `hurts_players`, `player_damage`,
  `knockback`, `particles`/`secondary_particles` (candidate names, first known one wins), `density`, arm
  (`arm_block`, `arm_degrees_per_tick`, `arm_height`, `arm_thickness`, `arm_hit_width`) and spike
  (`spike_block`, `spike_fallback_block`, `spike_spacing`, `spike_cycle_ticks`) options, and `sounds`
  (`activate`/`loop`/`kill`: sounds.json `key` + `vanilla` fallback, `volume`, `pitch`, `every_ticks`).
  Sound keys live under `traps.<type>` in `sounds.json`.
- `arenas.json` `traps[]`: sign location (`x/y/z`), `id`, `cost`, `type`, `region` (`{"zone": id}` or
  `{"p1", "p2"}`), `extra_regions`, `duration_seconds`, `cooldown_seconds` (-1 = type default), `requires_power`,
  `automatic` (runs by itself whenever it is off cooldown, like the Ascension centrifuge and the Shangri-La
  spikes; needs no sign), `requires_door` (door id: not usable until that door is open, prompt "Open the door
  first"; chat edit `door <id|none>`). Old entries without `type`/`region` stay generic radius traps (config.yml `trap.*`).
- Kills go through `Game.killMobByTrap` (round count, power-up roll, kill stat, optional points to the buyer).
  Burning is visual only. Rotating arms and spikes are block displays on 1.19.4+, particles before.
- Build mode: Feature tool, variant `Trap: <name>` (F cycles type). Right-click two corners, or sneak-right-click
  to reuse the zone you stand in, then right-click a wall to place the activation sign. Sneak-right-click a
  trap sign to edit it in chat (`type`, `cost`, `duration`, `cooldown`, `power`, `auto`, `zone <id|here>`, `delete`).

## 5. Equipment

Catalogue `equipment.json`, bought from wall buys like guns:

- `melee` upgrades (Bowie Knife, Sickle, Ballistic): knife damage multiplier, swing speed, model, one per player.
- `placeable` mines: `claymore` (directional tripwire, explodes towards zombies), `bouncing_betty`
  (proximity, jumps then explodes), `spikemore` (directional spikes). Count per buy, refilled each round.
- `tactical`: Gersch Device (black hole), QED (random effect), Monkey Bomb (existing), Matryoshka dolls.
- `gear`: breathing helmet (P.E.S.), Hacker device (opens/alters things it is used on).

Implementation (`game/equipment`, `equipment.json` in the plugin folder):

- Wall buy sign `[Zombies] / Equipment / <name> / <cost>` (blank cost = catalogue `cost`). In game it shows the
  item on the wall like a wall gun; F (or clicking the model/sign) buys. Buying owned mines/tacticals again
  refills them up to `max_carry` for `refill_cost`; anything else shows "owned". Build mode: Wall Buy tool,
  the equipment entries follow the guns in its variant list. `/zombies convertsigns` turns imported
  "Bowie Knife", "Sickle", "Claymore", "Bouncing Betty", "Spikemore" signs into equipment wall buys.
- `settings`: hotbar slots (`melee_slot`, `gear_slot`, `tactical_slot`, `placeable_slot`; -1 = no item),
  `one_placeable` / `one_tactical` (buying another kind replaces the old one, like BO; false = refuse until used
  up) and `default_melee_cooldown_ms` for the plain knife.
- Common fields: `name`, `type`, `cost`, `refill_cost`, `material`, `item_model` (pack model, also used on the
  wall when the pack is forced), `sounds` (event -> sound enum or namespaced key).
- `melee`: `one_shot_through_round` (knife one-shots zombies through that round; 0 = base knife),
  `damage_multiplier`, `swing_cooldown_ms`, `sounds.swing`. One per player, kept when downed, lost on bleed-out.
- `placeable`: `per_buy`, `max_carry` (defaults to `per_buy`), `max_placed`, `refill_per_round` (topped back up
  to this each round once bought), `trigger` (`cone` | `radius`), `trigger_radius`, `cone_angle`,
  `damage_radius`, `damage` or `one_shot_through_round`, `fuse_ticks`, `pop_up`, `effect` (`blast` | `spikes`).
  Right-click places it at the clicked floor (or your feet) facing where you look.
- `tactical`: `behaviour` = `gersch` (`radius`, `kill_radius`, `pull_strength`, `duration_ticks`), `qed`
  (`effects`: weighted `powerup`, `revive_all`, `kill_nearby`, `teleport_player`, `refill_ammo`), `matryoshka`
  (`chain`, `chain_delay_ticks`, `chain_spread`, `radius`, damage fields) or `monkey_bomb` (sells the existing
  Monkey Bomb throwable). Plus `per_buy`, `max_carry`, `refill_per_round`, `fuse_ticks`.
- `gear`: `behaviour` = `breathing` (puts a breathing-gear tagged `helmet_material` on; right-click the hotbar item
  toggles it) or `none`. The Hacker is such a gear: trigger steps with `gear: Hacker` need it (Moon's excavators and Pack-a-Punch hack).
- Placed mines are removed when their owner goes down, bleeds out or leaves, and with everything else at game end.

## 6. Special enemies

Catalogue `specials.json`. Each type: base mob, health formula, speed, model/skin, sounds and behaviour:

- `steal_perk` (Space Monkey: runs to a perk machine, steals a perk unless killed);
- `steal_weapon` (Thief: grabs a gun and flees, drops it when killed);
- `boss` (George A. Romero, Astronaut, Cosmonaut: big health, special attack);
- `napalm` (explodes in fire on death), `shrieker` (blinding scream).

Arena setting `special_enemies`: list of `{type, mode, value, max_alive, first_round, own_round}` with `mode` one of
`every_x_rounds` | `chance_per_round` | `replaces_dog_round` | `once_at_round`. Edited in `/zombies settings` >
Special enemies (toggle per type, cycle mode, adjust value / max alive / first round / own round). Specials in their
own round hold the round until they die or escape; specials mixed into a regular round never hold it up and despawn
after their `lifetime_seconds`.

## 7. Triggers and sequences

A trigger is a set of steps that unlocks a target when finished. Covers PaP gates and Easter eggs generically.

- step kinds: `interact` (press F on a point), `stand_on` (stand on a plate/zone),
  `kill_in_zone`, `ride_transport`, `power_on`, `round_reached`, `trigger_done` (`trigger`: id of a trigger that
  has to fire first);
- `round_chance` (`amount` percent, rolled at every round start); an `interact` step may need `gear` (equipment name,
  e.g. `Hacker`) and `hold_seconds` (the player stays at the point while a bar fills; walking away cancels);
- options: `ordered`, `time_limit_seconds`, `reset_on_fail`, `all_players`, `silent` (no chimes or completion toast,
  for machinery that runs on its own);
- targets: `open_door`, `enable_pack_a_punch`, `enable_teleporter` (teleporter sign or transport), `play_song`, `give_powerup`,
  `spawn_perk`, `message`, `place_blocks` (`blocks`: `[{x,y,z,blockdata}]` set when it fires, put back at game end;
  Der Riese bridge).
- machinery targets, all undone at game end: `after` (`amount` ticks, then its own `targets`: a timeline),
  `clone_blocks` (`box` {x1..z2} to x/y/z, `value` `replace|masked|move`, optional `filter` block or `#tag`),
  `fill_blocks` (`box`, `value` block state, optional `filter`), `play_sound` (`value`, x/y/z, `volume`),
  `reset_trigger` (calls off a trigger's pending timeline and arms it again), `set_zone` (`value` zone id, `amount` 0
  switches it off: an air pocket after a breach), `lock_teleporter`, `disable_pack_a_punch`,
  `give_points_to_player` (only the player who finished the trigger);
- build mode (Feature tool, Trigger variant): F cycles every step and target kind, right-click adds it. Clone and
  fill targets take the box of the zone you click in (mark it with the Zone tool first); set zone, reset trigger,
  lock teleporter and play sound take what you click. In the chat editor: `sN.gear=Hacker sN.hold=10`,
  `sN.chance=<percent>`, `tN.delay=<seconds>` (puts a target on the timeline; same delays share one step),
  `tN.to=here|x,y,z`, `tN.box=here|zone:<id>|x1,y1,z1,x2,y2,z2`, `tN.mode=replace|masked|move|masked move`,
  `tN.block=<state>`, `tN.filter=<block|#tag>`, `tN.sound=`, `tN.volume=`, `tN.on=<bool>`, `silent`; timed targets
  are edited as `tN.M.<key>=` and removed with `remove tN.M`. `list` shows the timeline in order, and the preview
  marks clone/fill boxes in yellow;
- doors with `price` 0 and no `signs` can only be opened by an `open_door` target and show no buy prompt (power
  walls, Der Riese PaP wall). Door `signs` entries may carry `blockdata`; a missing sign is re-created from it.

Examples: Ascension's rocket (landers, then launch: the rocket timeline opens the PaP door at 21 s), Five's panic room
(30 s of Pack-a-Punch, then the teleporters lock and DEFCON starts over), Moon's excavators (a random start each round,
a breach after 4 minutes unless hacked with the Hacker), Five DEFCON (4 ordered interacts → PaP teleporter), Shangri-La stepping stones (stand_on, all at once,
time limit → PaP), Ascension landers (ride all transports → PaP), Easter-egg songs (interact with 3 items →
play song).

## 8. Perk placement pools

A perk machine can be `pooled`: the arena keeps a pool of spots; machines appear at random spots when a door opens
or a trigger fires (Shi No Numa). Setting: `perk_pool_mode` = `fixed` | `random_on_open` | `random_on_start` (every
spot gets a different random pooled perk at game start; leftovers stay hidden for `spawn_perk`).
Only perk signs on (or next to) a pool spot are pooled; the map's other perks stay where they are. When no perk
sign stands on a spot, every perk in the arena is pooled. `perk_pool_perks` (list of perk types) adds perks that
have no sign of their own, e.g. Moon's No Man's Land spot drops Jugger-Nog or Speed Cola.

## 9. Import

`tools/mapimport/extract_map.py` maps the standalone map onto these features: trap functions → traps,
teleporter functions → transports/teleporters, zipline/lander/elevator/jump pad functions → transports, perk
placement → pools, songs and Pack-a-Punch gates → triggers, low gravity/oxygen → zones, bosses → special enemy
rules (see `tools/mapimport/README.md`). Every import result can be edited in build mode afterwards.
