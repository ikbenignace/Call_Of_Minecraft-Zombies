# Map import

Turns the ZombiesMC standalone map (command blocks + datapack, `Alpha V3.2.0.1 World`) into COM:Z arenas.

1. Extract (reads the datapack and the original, untouched world; needs `pip install nbtlib`):

       python3 -I tools/mapimport/extract_map.py --world "<path>/Alpha V3.2.0.1 World" --out out/ \
           --map zombie_cod5_asylum --arena Verruckt --map zombie_temple --arena Shangri-La ...

   Datapack → arena: cod5_prototype Nacht, cod5_asylum Verrückt, cod5_sumpf Shi No Numa, cod5_factory Der Riese,
   theater Kino der Toten, cosmodrome Ascension, coast Call of the Dead, temple Shangri-La, moon Moon, pentagon Five.

2. Copy `out/<arena>.json` to `plugins/COM_Zombies/import/` and run `/zombies importmap <arena>` (console works too).
   Back up the world first: it writes the perk/power/trap signs and removes the map's own plank mystery box.

The import replaces the arena's `zombie_spawns`, `doors`, `barriers`, `mystery_boxes`, `traps`, `transports`,
`zones`, `triggers` and `perk_pool_spots`, and copies the `settings` it found (`gravity`, `jump_boost`, `fog`,
`pap_locked`, `perk_pool_mode`, `perk_pool_reveal`, `power_switches`, `special_enemies`) over the arena settings. Every detector
works on the datapack's structure (folder and function names, selectors, scores), never on fixed coordinates.

## What is imported

| feature | source in the datapack / world | result |
|---|---|---|
| spawns, doors, barricades, box locations, perk and power signs | `maps/doors`, `unique/barricades`, `maps/mysterybox`, `maps/power` | as before; door blocks never include signs, a `clone ... move` (sliding door) counts its source box, door signs carry `blockdata`, block clusters more than 16 blocks from every sign of their door are reported |
| power switches | command blocks running `maps/power/power` or `power_<side>` (Shangri-La has two): the power sign goes on the lever/button next to it in the start state | `power_switches` when more than one |
| blocks that open with the power, or with any of several doors | air written by `maps/power/power*` (in order, last write wins), fills behind `execute if` power / door-open conditions in door functions, and blocks several doors clear | doors without price or signs, each opened by a trigger per way to open it: `power_on` and/or `stand_on` the doorway zone (`door_N`) of the door it waits for; blocks the power puts in (bridge, DEFCON levers, trapdoors) are a `place_blocks` target. Mechanisms over 600 blocks (Ascension's rocket) are left as the world has them |
| gates a sign's own function lifts | a sign the reset function writes whose click function clears blocks the reset put up | door without price, opened by riding the transport that function starts, else by pressing the sign |
| spawners started later | spawners summoned outside `begin`/`prepare` and the doors: by the power, they open with the power's doors; by a gate function, with that gate's door; otherwise with a priced door within 10 blocks, else they start with the game (reported) | door `spawns` |
| traps | `maps/traps/trapN{buy,clock,visuals}`: price, every sign the buy function rewrites inside the map, area from the particle spots, else the fire it sets, else the kill selectors (`x/y/z/distance`); `fire`/`flame` makes a fire trap; a door function that writes the buy sign, or a door-open condition on the line that does, makes it wait for that door | `traps` (`requires_door`; `requires_power` only in maps with `maps/power`) |
| map-specific traps | `unique/flogger`, `unique/centrifuge`, `unique/spikes/<side>`: kill selectors or the blocks the trap raises; a folder without a buy function runs by itself (`automatic`) on the cycle of its own clock | `traps` (`flogger`, `centrifuge`, `spikes`) |
| teleporters, ziplines, slides, minecarts, flingers | absolute player `tp`s grouped per timer step (or per PlayerOne..Four group), zombie `tp`s ignored; a teleporter goes to its last stop, a tour (3+ stops) to its longest stop; riders picked at a pad marker (`as @e[tag=To_Moon] at @s ...`) or a selector (`@a[x,y,z,distance]`) start there; a function with several pad -> spot `tp`s is one teleporter per pad; a flinger arcs from its spot to its absolute `tp`s | `transports`, one-way unless a reverse path exists |
| transport details | its buy/use signs (click tags its `*_B` tag, else runs one of its functions) near its start (and end when two-way); `*Cooldown`/`*_CD` scores, a teleporter's own timer (a tour: from its last stop until it rewrites its sign); power: its loop only runs with `Power_On`, a reset "turn on the power" or power-written sign, or power puts its platform in; teleporters sharing a helper folder (Der Riese's mainframe) share a cooldown; a teleporter that writes back its "Initiate link" sign relocks after use | `call_points`, `cooldown_seconds`, `requires_power`, `cooldown_group`, `relock_after_use`; the signs get a short label (blank for ziplines) |
| landers | `unique/landers/launch/lander_N_launch` (where riders stand on pad N), cruise height from the highest rider `tp`, price and `Lander_CD` from the lander functions, "Call lander" stations matched to the nearest pad | platform transports from pad 0 to every pad: straight up, across at cruise height, down; two-way, speed 12, `cooldown_group` `lander`, call points at both stations |
| elevators | `unique/elevator/<car>/*_to_*`: the selector that picks the riders on each floor, the cabin clone each move ends with, price from `*_buy` | two-way teleport transports between floors, call points at the "Use Elevator" signs; the cabin floor is put down (block edits) at every stop the start state leaves empty |
| jump pads | `unique/**/jump_pads`: the box that tags the jumpers and their absolute `tp`s in counter order | one-way platform transports; pads sharing a launch spot (rotating) are one |
| geysers | `unique/**/geyser_*`: the `<Tag>` marker the players stand on, the `<Tag>_Clear` marker they are lifted to, the landing `tp` | one-way free platform transports |
| song easter egg | `unique/song/egg*`: the egg spots (the sign the function blanks, clickable signs or the button over a command block that run it), clicks needed (`Eggs_Interacted=N`) and the song (`playsound ... music`) | trigger `song_egg`: unordered interact steps (teddy), target `play_song` |
| perk placement pool | `unique/perk_placement/<perk>/<perk><place>`: one pool spot per place, each perk's sign written at a different place | `perk_pool_spots`, `perk_pool_mode=random_on_open`, `perk_pool_reveal=door` |
| random perk layouts | `unique/perks/set/**`: every sign spot a layout uses (one block apart is one spot), perks from the markers on top, buy text from `data merge block ~ ~-1 ~` at those markers | `perk_pool_spots`, `perk_pool_mode=random_on_start` |
| teleporter links | signs the power function writes whose click runs a `*link*` function, followed through the signs that function (or what it runs) writes next; time limit from the timer that resets the last sign | per pad an ordered trigger (power, the signs) with `enable_teleporter`; when every pad's link runs the same function and it clears a wall the reset puts up under a `#... pap` comment, a `trigger_done` trigger opens it and enables Pack-a-Punch |
| placeholder signs | signs only written with "You must turn on the power first!", "Cooling down...", "Destination not open", ... | blanked, or labelled with the trigger step they are |
| Pack-a-Punch gates | numbered switch command blocks whose score unlocks a `*pap*` function (DEFCON); pressure plates a `unique/pap` function places on markers (stepping stones); a chain of "link" signs started by the power function in a folder that mentions PaP (Kino); lander functions that mention PaP (ride every lander, then the sign whose click starts the timer of the function that slides the PaP room doors open); a loop that only runs `unique/pap` functions with `Power_On` | `pap_locked=true` + trigger with `enable_pack_a_punch` (and `enable_teleporter` for the PaP teleporters it opens, `open_door` for the PaP room) |
| spare Pack-a-Punch machines | PaP signs inside the source box of a `unique/pap` clone | removed (block edit to air) |
| Pack-a-Punch sign written mid-game | `data merge block ~ ~ ~` on a PaP marker, block state from the clone that puts it there | sign with `blockdata` |
| low gravity / no air | a function giving Slow Falling + Jump Boost and tagging air pockets with `tag @e[x,dx,y,dy,z,dz] add <Air>` | one no-air zone with gravity 0.4 around the pockets, an `air_pocket` zone (gravity 1.0) per pocket; arena-wide `gravity` when the effects are not tied to a score like `On_Moon` |
| special enemies | `unique/boss/*astro*`, `*napalm*`, `*shriek*`, `thief`, `maps/enemies/*space_monkey*`, `unique/george` (chance from the `boss/1_to_N` loot, first round from the clock) | `special_enemies` rules |

## Per map

- **Shi No Numa**: perk pool (4 huts), Flogger (750, 2 signs, 30 s on / 30 s off), phone song egg (3 dials);
  zipline (60 s cooldown, blank signs as call points) whose far-end gate opens on the first ride. No power, so
  traps need none; the Flogger waits for door 3.
- **Verrückt**: toilet song egg (3 flushes), the iron/glass wall opens with the power.
- **Der Riese**: song egg; teleporters A/B/C need power and their own link (power, pad sign, mainframe within
  25 s), go one way to the mainframe and share a 15 s cooldown; once all three are linked the PaP wall opens and
  PaP is enabled. The power opens the prismarine walls and swings the bridge in. Traps 2/3 wait for doors 2/6.
- **Kino der Toten**: fire trap (trap 2, from the fire it sets), song egg, teleporter goes to the stage (one way,
  58 s cooldown, relinked after every use); PaP and the teleporter are locked until power + "Initiate link" +
  "Link pad". The power opens the stage curtain and starts the stage spawners.
- **Ascension**: two fire traps (trap 1 waits for door 6), automatic centrifuge (7 s every ~2 min), three
  landers, space monkeys replacing dog rounds, PaP after riding every lander and activating the launch; the
  launch opens the PaP room and its spawners.
- **Call of the Dead**: song egg, George A. Romero from round 1, flinger (power + one of doors 2/10/11/16 opens
  its platform), lighthouse gate opens with door 7 or 9, PaP after power.
- **Shangri-La**: automatic spike traps left/right, napalm and shrieker zombies, PaP after power + standing on
  the four stepping stones (one after another); two power switches; six random perk spots dealt at game start;
  geysers; geyser debris opens with door 3 or 4 / door 6 or 7, the slide gate with power and door 3 or 4.
- **Moon**: low gravity / no air outside 21 air pockets, three jump pads (two share a launch spot) that need
  power, one-way teleporters both ways, astronaut, song egg.
- **Five**: DEFCON (power + the four switches, any order) unlocks PaP and the PaP teleporters, PaP sign in the
  panic room, both elevators (250, cabin floor at every stop), seven pad teleporters that need power, the power
  puts the DEFCON levers in, thief replacing dog rounds, phone song egg.

## Not imported / approximations

- The song events (`custom.egg.*`) are in the ZombiesMC resource pack, not in comz-pack: the egg plays the raw
  event, so players need that pack (or the sounds added to comz-pack) to hear it.
- Shangri-La stepping stones scale with the player count in BO; here all four have to be stood on (one after
  another is enough). The PaP closing again after a while is not modelled.
- Call of the Dead: PaP lands at a random one of three spots after power; here PaP unlocks with the power.
- Moon: PaP is always open (the hacker cage is not modelled); air pockets have air without power and the
  excavator breaches never remove it; P.E.S. is not placed.
- Ascension centrifuge and Shangri-La spikes hurt players like the plugin's trap types do; the map's centrifuge
  timer shortening per kill and the spike patterns are not modelled.
- Shi No Numa perks reveal on any door, not only the hut doors. Kino's teleporter does not tour the side rooms.
- A mechanism that waits for a door opens when a player walks through that doorway (`stand_on` its zone, checked
  twice a second), not the moment the door is bought. When several doors can open it, its placed blocks come with
  the first of them only.
- Der Riese courtyard spawners start with the game (the map starts them when a player reaches the courtyard),
  as do Five's panic room and elevator spawners. Ascension's sentry turret signs are imported as plain buy signs
  the plugin has no feature for.
- Melee/equipment buys, buildables and the rest of the map scripts (Der Riese fly trap, excavators, ...).
