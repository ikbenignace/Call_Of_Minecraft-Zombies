# Map import

Turns the ZombiesMC standalone map (command blocks + datapack, `Alpha V3.2.0.1 World`) into COM:Z arenas.

1. Extract (reads the datapack and the original, untouched world; needs `pip install nbtlib`):

       python3 -I tools/mapimport/extract_map.py --world "<path>/Alpha V3.2.0.1 World" --out out/ \
           --map zombie_cod5_asylum --arena Verruckt --map zombie_temple --arena Shangri-La ...

   Datapack → arena: cod5_prototype Nacht, cod5_asylum Verrückt, cod5_sumpf Shi No Numa, cod5_factory Der Riese,
   theater Kino der Toten, cosmodrome Ascension, coast Call of the Dead, temple Shangri-La, moon Moon, pentagon Five.

2. Copy `out/<arena>.json` to `plugins/COM_Zombies/import/` and run `/zombies importmap <arena>` (console works too).
   Back up the world first: it writes the perk/power signs and removes the map's own plank mystery box.

Imported: zombie spawns (start / per door / per window), doors (price, blocks, signs, spawns), barricades,
mystery box locations, perk and power signs, then the buy signs via `/zombies convertsigns`.
Not imported: traps, teleporters, elevators, ziplines, melee/equipment buys and map-specific mechanics.
