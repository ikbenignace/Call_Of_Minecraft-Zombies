package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.equipment.Equipment;
import com.theprogrammingturkey.comz.game.equipment.EquipmentCatalogue;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.util.Compat;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the buy signs of imported command-block maps ("Stakeout / [Cost: 1500] / [Ammo: 750/4500]",
 * "Jugger-Nog / [Cost: 2500]", "Random Weapon", ...) into COM:Z feature signs, so the plugin spawns its
 * wall guns, equipment wall buys, machines and boxes there. Doors, debris, traps and other map mechanics need block regions
 * the signs don't describe; those are only reported. Every rewritten sign's original text goes into the
 * report file so a conversion can be undone by hand.
 */
public final class MapSignConverter
{
	private static final Pattern NUMBER = Pattern.compile("(\\d{2,5})");
	/** Signs of one machine around a map build; only the first becomes the machine. */
	private static final double MACHINE_DEDUPE_RANGE = 3.0;
	private static final double GUN_DEDUPE_RANGE = 2.0;
	/** Buy signs packed this tightly form a shop/loadout wall of the map, not wall buys. */
	private static final double GRID_RANGE = 1.5;
	private static final int GRID_NEIGHBOURS = 3;

	private static final Map<String, String> GUN_ALIASES = new HashMap<>();
	private static final Map<String, PerkType> PERK_ALIASES = new HashMap<>();
	private static final Map<String, String> EQUIPMENT_ALIASES = new HashMap<>();

	static
	{
		GUN_ALIASES.put("m16", "Colt M16A1");
		GUN_ALIASES.put("ak74u", "AK-74u");
		GUN_ALIASES.put("barbipod", "BAR");
		GUN_ALIASES.put("doublebarrelshotgun", "Double-Barreled Shotgun");
		GUN_ALIASES.put("doublebarrel", "Double-Barreled Shotgun");
		GUN_ALIASES.put("sawedoff", "Sawed-Off Shotgun");
		GUN_ALIASES.put("sawnoff", "Sawed-Off Shotgun");
		GUN_ALIASES.put("sawnoffshotgun", "Sawed-Off Shotgun");
		GUN_ALIASES.put("stg", "STG-44");
		GUN_ALIASES.put("g43", "Gewehr 43");
		GUN_ALIASES.put("kar98", "Kar98k");
		GUN_ALIASES.put("garand", "M1 Garand");
		GUN_ALIASES.put("carbine", "M1 Carbine");

		EQUIPMENT_ALIASES.put("bowie", "Bowie Knife");
		EQUIPMENT_ALIASES.put("bowieknife", "Bowie Knife");
		EQUIPMENT_ALIASES.put("claymores", "Claymore");
		EQUIPMENT_ALIASES.put("bettys", "Bouncing Betty");
		EQUIPMENT_ALIASES.put("bouncingbetties", "Bouncing Betty");
		EQUIPMENT_ALIASES.put("spikemores", "Spikemore");
		EQUIPMENT_ALIASES.put("gersch", "Gersch Device");
		EQUIPMENT_ALIASES.put("gerschdevice", "Gersch Device");
		EQUIPMENT_ALIASES.put("quantumentanglementdevice", "QED");
		EQUIPMENT_ALIASES.put("matryoshkadoll", "Matryoshka Dolls");
		EQUIPMENT_ALIASES.put("monkeybombs", "Monkey Bomb");
		EQUIPMENT_ALIASES.put("pes", "P.E.S.");

		PERK_ALIASES.put("juggernog", PerkType.JUGGERNOG);
		PERK_ALIASES.put("speedcola", PerkType.SPEED_COLA);
		PERK_ALIASES.put("doubletap", PerkType.DOUBLE_TAP);
		PERK_ALIASES.put("doubletaprootbeer", PerkType.DOUBLE_TAP);
		PERK_ALIASES.put("quickrevive", PerkType.QUICK_REVIVE);
		PERK_ALIASES.put("buyarevive", PerkType.QUICK_REVIVE);
		PERK_ALIASES.put("staminup", PerkType.STAMIN_UP);
		PERK_ALIASES.put("phdflopper", PerkType.PHD_FLOPPER);
		PERK_ALIASES.put("deadshot", PerkType.DEADSHOT_DAIQ);
		PERK_ALIASES.put("deadshotdaiquiri", PerkType.DEADSHOT_DAIQ);
		PERK_ALIASES.put("mulekick", PerkType.MULE_KICK);
		PERK_ALIASES.put("electriccherry", PerkType.ELECTRIC_C);
		PERK_ALIASES.put("tombstone", PerkType.TOMBSTONE_SODA);
		PERK_ALIASES.put("tombstonesoda", PerkType.TOMBSTONE_SODA);
		PERK_ALIASES.put("whoswho", PerkType.WHOS_WHO);
		PERK_ALIASES.put("vultureaid", PerkType.VULTURE_AID);
		PERK_ALIASES.put("derwunderfizz", PerkType.DER_WUNDERFIZZ);
		PERK_ALIASES.put("wunderfizz", PerkType.DER_WUNDERFIZZ);
	}

	public enum Kind
	{
		WALL_GUN, EQUIPMENT, PERK, PACK_A_PUNCH, MYSTERY_BOX, GRENADE, DUPLICATE, SIGN_WALL, DOOR, UNKNOWN
	}

	public static final class Entry
	{
		public final Kind kind;
		public final Location location;
		public final String name;
		public final String[] original;
		public final String[] converted;

		Entry(Kind kind, Location location, String name, String[] original, String[] converted)
		{
			this.kind = kind;
			this.location = location;
			this.name = name;
			this.original = original;
			this.converted = converted;
		}
	}

	public static final class Report
	{
		public final List<Entry> entries = new ArrayList<>();
		public File file;

		public Map<Kind, Integer> counts()
		{
			Map<Kind, Integer> counts = new LinkedHashMap<>();
			for(Kind kind : Kind.values())
				counts.put(kind, 0);
			for(Entry entry : entries)
				counts.put(entry.kind, counts.get(entry.kind) + 1);
			return counts;
		}
	}

	private MapSignConverter()
	{
	}

	/** Scans the arena (loading its chunks) and, when {@code apply}, rewrites the convertible signs. */
	public static Report convert(Game game, boolean apply)
	{
		Report report = new Report();
		World world = game.arena.getWorld();
		if(world == null || !game.arena.areMinAndMaxSet())
			return report;

		List<Sign> buySigns = new ArrayList<>();
		for(Sign sign : arenaSigns(game, world))
		{
			String[] lines = plainLines(sign);
			if(!lines[0].toLowerCase(Locale.ROOT).contains("[zombies]") && hasCost(lines))
				buySigns.add(sign);
		}

		List<Location> machines = new ArrayList<>();
		Map<String, List<Location>> wallGuns = new HashMap<>();
		for(Sign sign : buySigns)
		{
			String[] lines = plainLines(sign);
			Entry entry = inSignWall(sign, buySigns)
					? new Entry(Kind.SIGN_WALL, sign.getLocation(), nameOf(lines), lines, null)
					: classify(sign.getLocation(), lines, machines, wallGuns);
			report.entries.add(entry);
			if(apply)
				apply(game, sign, entry);
		}
		report.file = writeReport(game, report, apply);
		return report;
	}

	private static List<Sign> arenaSigns(Game game, World world)
	{
		Location min = game.arena.getMin();
		Location max = game.arena.getMax();
		int minCX = Math.min(min.getBlockX(), max.getBlockX()) >> 4;
		int maxCX = Math.max(min.getBlockX(), max.getBlockX()) >> 4;
		int minCZ = Math.min(min.getBlockZ(), max.getBlockZ()) >> 4;
		int maxCZ = Math.max(min.getBlockZ(), max.getBlockZ()) >> 4;
		List<Sign> signs = new ArrayList<>();
		for(int cx = minCX; cx <= maxCX; cx++)
			for(int cz = minCZ; cz <= maxCZ; cz++)
			{
				Chunk chunk = world.getChunkAt(cx, cz);
				for(BlockState state : chunk.getTileEntities())
					if(state instanceof Sign && game.arena.containsBlock(state.getLocation()))
						signs.add((Sign) state);
			}
		return signs;
	}

	private static String[] plainLines(Sign sign)
	{
		String[] lines = new String[4];
		for(int i = 0; i < 4; i++)
		{
			String line = sign.getLine(i);
			lines[i] = line == null ? "" : ChatColor.stripColor(line).trim();
		}
		return lines;
	}

	private static boolean inSignWall(Sign sign, List<Sign> buySigns)
	{
		int neighbours = 0;
		for(Sign other : buySigns)
			if(other != sign && other.getLocation().distanceSquared(sign.getLocation()) <= GRID_RANGE * GRID_RANGE)
				neighbours++;
		return neighbours >= GRID_NEIGHBOURS;
	}

	private static boolean hasCost(String[] lines)
	{
		for(String line : lines)
			if(line.toLowerCase(Locale.ROOT).contains("cost"))
				return true;
		return false;
	}

	/** The feature name: every line that isn't a "[Cost ...]" or "[Ammo ...]" tag. */
	private static String nameOf(String[] lines)
	{
		StringBuilder name = new StringBuilder();
		for(String line : lines)
		{
			String lower = line.toLowerCase(Locale.ROOT);
			if(line.isEmpty() || lower.contains("cost") || lower.contains("ammo") || lower.contains("one time"))
				continue;
			if(name.length() > 0)
				name.append(' ');
			name.append(line);
		}
		return name.toString().trim();
	}

	static String normalize(String s)
	{
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static int numberIn(String[] lines, String tag, int fallback)
	{
		for(String line : lines)
		{
			if(!line.toLowerCase(Locale.ROOT).contains(tag))
				continue;
			Matcher m = NUMBER.matcher(line);
			if(m.find())
				return Integer.parseInt(m.group(1));
		}
		return fallback;
	}

	private static String resolveGun(String name)
	{
		String key = normalize(name);
		String alias = GUN_ALIASES.get(key);
		if(alias != null && WeaponManager.getGun(alias) != null)
			return alias;
		for(String gun : WeaponManager.getGunNames())
			if(normalize(gun).equals(key))
				return gun;
		return null;
	}

	private static Equipment resolveEquipment(String name)
	{
		Equipment exact = EquipmentCatalogue.get(name);
		if(exact != null)
			return exact;
		String key = normalize(name);
		String alias = EQUIPMENT_ALIASES.get(key);
		return alias == null ? null : EquipmentCatalogue.get(alias);
	}

	private static PerkType resolvePerk(String name)
	{
		String key = normalize(name);
		PerkType perk = PERK_ALIASES.get(key);
		if(perk != null)
			return perk;
		for(Map.Entry<String, PerkType> alias : PERK_ALIASES.entrySet())
			if(key.startsWith(alias.getKey()))
				return alias.getValue();
		return null;
	}

	private static boolean near(List<Location> known, Location loc, double range)
	{
		for(Location other : known)
			if(other.distanceSquared(loc) <= range * range)
				return true;
		return false;
	}

	private static Entry classify(Location loc, String[] lines, List<Location> machines, Map<String, List<Location>> wallGuns)
	{
		String name = nameOf(lines);
		String key = normalize(name);
		int cost = numberIn(lines, "cost", 0);

		if(key.contains("door") || key.contains("debris"))
			return new Entry(Kind.DOOR, loc, name, lines, null);

		if(key.contains("packapunch"))
			return machine(Kind.PACK_A_PUNCH, loc, name, lines, machines,
					new String[]{"[Zombies]", "Pack-a-Punch", Integer.toString(cost > 0 ? cost : 5000), ""});

		if(key.contains("randomweapon") || key.contains("mysterybox"))
			return machine(Kind.MYSTERY_BOX, loc, name, lines, machines,
					new String[]{"[Zombies]", "Mystery Box", Integer.toString(cost > 0 ? cost : 950), ""});

		PerkType perk = resolvePerk(name);
		if(perk != null)
		{
			// Imported maps price Quick Revive at its solo 500; the plugin applies the solo price itself.
			int price = perk == PerkType.QUICK_REVIVE || cost <= 0 ? perk.getDefaultCost() : cost;
			return machine(Kind.PERK, loc, name, lines, machines,
					new String[]{"[Zombies]", "Perk Machine", perk.toString().toLowerCase(Locale.ROOT), Integer.toString(price)});
		}

		if(key.contains("grenade") || key.contains("granate"))
			return new Entry(Kind.GRENADE, loc, name, lines, new String[]{"[Zombies]", "Grenade", Integer.toString(cost > 0 ? cost : 250), ""});

		Equipment equipment = resolveEquipment(name);
		if(equipment != null)
		{
			List<Location> seen = wallGuns.computeIfAbsent(equipment.name, g -> new ArrayList<>());
			if(near(seen, loc, GUN_DEDUPE_RANGE))
				return new Entry(Kind.DUPLICATE, loc, name, lines, null);
			seen.add(loc);
			return new Entry(Kind.EQUIPMENT, loc, equipment.name, lines,
					new String[]{"[Zombies]", "Equipment", equipment.name, Integer.toString(cost > 0 ? cost : equipment.cost)});
		}

		String gun = resolveGun(name);
		if(gun != null)
		{
			List<Location> seen = wallGuns.computeIfAbsent(gun, g -> new ArrayList<>());
			if(near(seen, loc, GUN_DEDUPE_RANGE))
				return new Entry(Kind.DUPLICATE, loc, name, lines, null);
			seen.add(loc);
			int ammo = numberIn(lines, "ammo", Math.max(1, cost / 2));
			return new Entry(Kind.WALL_GUN, loc, gun, lines, new String[]{"[Zombies]", "Gun", gun, cost + " / " + ammo});
		}
		return new Entry(Kind.UNKNOWN, loc, name, lines, null);
	}

	private static Entry machine(Kind kind, Location loc, String name, String[] lines, List<Location> machines, String[] converted)
	{
		if(near(machines, loc, MACHINE_DEDUPE_RANGE))
			return new Entry(Kind.DUPLICATE, loc, name, lines, null);
		machines.add(loc);
		return new Entry(kind, loc, name, lines, converted);
	}

	private static void apply(Game game, Sign sign, Entry entry)
	{
		if(entry.kind == Kind.DUPLICATE)
		{
			sign.getBlock().setType(Material.AIR, false);
			return;
		}
		if(entry.converted == null)
			return;
		// An imported arena already has its box locations; the map's "Random Weapon" sign just goes.
		if(entry.kind == Kind.MYSTERY_BOX && game.boxManager.hasBoxNear(sign.getLocation(), MACHINE_DEDUPE_RANGE))
		{
			sign.getBlock().setType(Material.AIR, false);
			return;
		}
		sign.setLine(0, ChatColor.RED + entry.converted[0]);
		sign.setLine(1, ChatColor.AQUA + entry.converted[1]);
		sign.setLine(2, entry.converted[2]);
		sign.setLine(3, entry.converted[3]);
		sign.update(true, false);
		if(entry.kind == Kind.MYSTERY_BOX)
		{
			RandomBox box = new RandomBox(sign.getLocation(), Compat.facingOf(sign.getBlockData()), game,
					game.boxManager.getNextBoxName(), Integer.parseInt(entry.converted[2]));
			game.boxManager.addBox(box);
		}
	}

	private static File writeReport(Game game, Report report, boolean applied)
	{
		File file = new File(COMZombies.getPlugin().getDataFolder(), "convert-" + game.getName() + ".txt");
		try(PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8.name()))
		{
			out.println((applied ? "Converted" : "Dry run for") + " map signs in " + game.getName());
			out.println(report.counts());
			for(Entry e : report.entries)
			{
				Location l = e.location;
				out.println(e.kind + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + "  " + e.name
						+ "  | was: " + String.join(" / ", e.original)
						+ (e.converted == null ? "" : "  | now: " + String.join(" / ", e.converted)));
			}
		} catch(IOException ex)
		{
			COMZombies.log.warning("Could not write the sign conversion report: " + ex.getMessage());
			return null;
		}
		return file;
	}
}
