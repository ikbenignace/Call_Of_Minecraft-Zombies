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
`pap_locked`, `perk_pool_mode`, `perk_pool_reveal`, `special_enemies`) over the arena settings. Every detector
works on the datapack's structure (folder and function names, selectors, scores), never on fixed coordinates.

## What is imported

| feature | source in the datapack / world | result |
|---|---|---|
| spawns, doors, barricades, box locations, perk and power signs | `maps/doors`, `unique/barricades`, `maps/mysterybox`, `maps/power` | as before |
| traps | `maps/traps/trapN{buy,clock,visuals}`: price, every sign the buy function rewrites, area from the particle spots, else the fire it sets, else the kill selectors (`x/y/z/distance`); `fire`/`flame` makes a fire trap | `traps` |
| map-specific traps | `unique/flogger`, `unique/centrifuge`, `unique/spikes/<side>`: kill selectors or the blocks the trap raises; a folder without a buy function runs by itself (`automatic`) on the cycle of its own clock | `traps` (`flogger`, `centrifuge`, `spikes`) |
| teleporters, ziplines, slides, minecarts | absolute player `tp`s grouped per timer step (or per PlayerOne..Four group); a teleporter goes to its last stop, a tour (3+ stops) to its longest stop; riders picked at a pad marker (`as @e[tag=To_Moon] at @s ...`) start there; a teleporter with its own way back is one-way | `transports` |
| landers | `unique/landers/launch/lander_N_launch` (where riders stand on pad N), price from the buy function | platform transports from pad 0 to every pad, arched 20 blocks up, two-way |
| elevators | `unique/elevator/<car>/*_to_*`: the selector that picks the riders on each floor | two-way teleport transports between floors, free |
| jump pads | `unique/**/jump_pads`: the box that tags the jumpers and their absolute `tp`s in counter order | one-way platform transports |
| song easter egg | `unique/song/egg*`: the egg spots (the sign the function blanks, clickable signs or the button over a command block that run it), clicks needed (`Eggs_Interacted=N`) and the song (`playsound ... music`) | trigger `song_egg`: unordered interact steps (teddy), target `play_song` |
| perk placement pool | `unique/perk_placement/<perk>/<perk><place>`: one pool spot per place, each perk's sign written at a different place | `perk_pool_spots`, `perk_pool_mode=random_on_open`, `perk_pool_reveal=door` |
| Pack-a-Punch gates | numbered switch command blocks whose score unlocks a `*pap*` function (DEFCON); pressure plates a `unique/pap` function places on markers (stepping stones); a chain of "link" signs started by the power function in a folder that mentions PaP (Kino); lander functions that mention PaP (ride every lander) | `pap_locked=true` + trigger with `enable_pack_a_punch` (and `enable_teleporter` for the PaP teleporter it opens) |
| Pack-a-Punch sign written mid-game | `data merge block ~ ~ ~` on a PaP marker, block state from the clone that puts it there | sign with `blockdata` |
| low gravity / no air | a function giving Slow Falling + Jump Boost and tagging air pockets with `tag @e[x,dx,y,dy,z,dz] add <Air>` | one no-air zone with gravity 0.4 around the pockets, an `air_pocket` zone (gravity 1.0) per pocket; arena-wide `gravity` when the effects are not tied to a score like `On_Moon` |
| special enemies | `unique/boss/*astro*`, `*napalm*`, `*shriek*`, `thief`, `maps/enemies/*space_monkey*`, `unique/george` (chance from the `boss/1_to_N` loot, first round from the clock) | `special_enemies` rules |

## Per map

- **Shi No Numa**: perk pool (4 huts), Flogger (750, 2 signs, 30 s on / 30 s off), phone song egg (3 dials).
- **Verrückt**: toilet song egg (3 flushes).
- **Der Riese**: song egg. The mainframe link is not a Pack-a-Punch gate in this datapack.
- **Kino der Toten**: fire trap (trap 2, from the fire it sets), song egg, teleporter goes to the stage; PaP and
  the teleporter are locked until power + "Initiate link" + "Link pad".
- **Ascension**: two fire traps, automatic centrifuge (7 s every ~2 min), three landers, space monkeys replacing
  dog rounds, PaP after riding every lander.
- **Call of the Dead**: song egg, George A. Romero from round 1.
- **Shangri-La**: automatic spike traps left/right, napalm and shrieker zombies, PaP after power + standing on
  the four stepping stones (one after another).
- **Moon**: low gravity / no air outside 21 air pockets, four jump pads, one-way teleporters both ways,
  astronaut, song egg.
- **Five**: DEFCON (power + the four switches, any order) unlocks PaP and the PaP teleporter, PaP sign in the
  panic room, both elevators, thief replacing dog rounds, phone song egg.

## Not imported / approximations

- The song events (`custom.egg.*`) are in the ZombiesMC resource pack, not in comz-pack: the egg plays the raw
  event, so players need that pack (or the sounds added to comz-pack) to hear it.
- Shangri-La stepping stones scale with the player count in BO; here all four have to be stood on (one after
  another is enough). The PaP closing again after a while is not modelled.
- Call of the Dead: PaP lands at a random one of three spots after power; left unlocked with its three signs.
- Moon: PaP is always open (the hacker cage is not modelled); air pockets have air without power and the
  excavator breaches never remove it; P.E.S. is not placed.
- Ascension centrifuge and Shangri-La spikes hurt players like the plugin's trap types do; the map's centrifuge
  timer shortening per kill and the spike patterns are not modelled.
- Shi No Numa perks reveal on any door, not only the hut doors. Kino's teleporter does not tour the side rooms.
- Melee/equipment buys, buildables and the rest of the map scripts (Der Riese fly trap, excavators, ...).
