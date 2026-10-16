package com.theprogrammingturkey.comz.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.builder.MapSignConverter;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@code /zombies importmap <arena>} — applies {@code plugins/COM_Zombies/import/<arena>.json}, produced by
 * {@code tools/mapimport/extract_map.py} from a standalone command-block map: replaces the arena's spawns,
 * doors, barriers, mystery boxes, traps, transports, zones, triggers and perk pool spots (keeping its spawn
 * points and settings when it already exists, growing its bounds to cover the import, apart from the environment, Pack-a-Punch gate, perk pool
 * and special enemy settings found in the import's {@code settings}), writes the map's buy and power signs,
 * removes the map's own plank box and then converts the buy signs.
 */
public class ImportMapCommand extends SubCommand
{
	private static final String[] REPLACED = {"zombie_spawns", "mystery_boxes", "barriers", "doors", "traps", "transports", "power_setup",
			"zones", "triggers", "perk_pool_spots"};
	private static final String[] IMPORTED_SETTINGS = {"gravity", "jump_boost", "fog", "pap_locked", "perk_pool_mode", "perk_pool_reveal", "perk_pool_perks", "special_enemies", "power_switches",
			"time_of_day", "weather"};
	private static final String[] KEPT_WHEN_PRESENT = {"player_spawn", "spectator_spawn", "lobby_spawn"};

	public ImportMapCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.BUILD.hasPerm(player))
		{
			CommandUtil.noPermission(player, "import maps");
			return true;
		}
		run(player, args);
		return true;
	}

	public static void run(CommandSender sender, String[] args)
	{
		if(args.length < 2)
		{
			sender.sendMessage(ChatColor.RED + "Usage: /zombies importmap <arena>");
			return;
		}
		String name = args[1];
		File file = new File(new File(COMZombies.getPlugin().getDataFolder(), "import"), name + ".json");
		if(!file.isFile())
		{
			sender.sendMessage(ChatColor.RED + "No import file at " + file.getPath());
			return;
		}

		Game existing = GameManager.INSTANCE.isValidArena(name) ? GameManager.INSTANCE.getGame(name) : null;
		if(existing != null && (existing.getStatus() == Game.GameStatus.INGAME || existing.getStatus() == Game.GameStatus.STARTING || !existing.getPlayersInGame().isEmpty()))
		{
			sender.sendMessage(ChatColor.RED + name + " has players in it! End the game first.");
			return;
		}

		JsonObject imported;
		try(Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
		{
			@SuppressWarnings("deprecation")
			JsonElement parsed = new JsonParser().parse(reader);
			imported = parsed.getAsJsonObject();
		} catch(IOException | RuntimeException e)
		{
			sender.sendMessage(ChatColor.RED + "Could not read " + file.getName() + ": " + e.getMessage());
			return;
		}

		JsonObject importedSave = imported.getAsJsonObject("save_data");
		World world = Bukkit.getWorld(importedSave.get("world_name").getAsString());
		if(world == null)
		{
			sender.sendMessage(ChatColor.RED + "World " + importedSave.get("world_name").getAsString() + " is not loaded.");
			return;
		}

		JsonObject arenaJson = existing != null ? existing.saveGame() : newArena();
		JsonObject save = arenaJson.getAsJsonObject("save_data");
		for(String key : REPLACED)
		{
			if(importedSave.has(key))
				save.add(key, importedSave.get(key));
			else
				save.remove(key);
		}
		for(String key : KEPT_WHEN_PRESENT)
			if(!save.has(key) || save.get(key).isJsonNull())
				save.add(key, importedSave.get(key));
		growBounds(save, importedSave);
		save.addProperty("world_name", world.getName());
		repairSettings(arenaJson.getAsJsonObject("settings"));
		mergeSettings(arenaJson.getAsJsonObject("settings"), imported.getAsJsonObject("settings"));

		int edits = applyBlockEdits(world, imported.getAsJsonArray("block_edits"));
		int signs = writeSigns(world, imported.getAsJsonArray("signs"));

		if(existing != null)
		{
			existing.endGame();
			GameManager.INSTANCE.removeGame(existing);
		}
		Game game = new Game(name);
		if(!game.loadGame(arenaJson))
		{
			sender.sendMessage(ChatColor.RED + "The imported arena did not load; see the console.");
			if(existing != null)
				GameManager.INSTANCE.addGame(existing);
			return;
		}
		GameManager.INSTANCE.addGame(game);
		GameManager.INSTANCE.saveAllGames();

		MapSignConverter.Report report = MapSignConverter.convert(game, true);
		sender.sendMessage(ChatColor.GOLD + "Imported " + name + ": " + save.getAsJsonArray("zombie_spawns").size() + " spawns, "
				+ save.getAsJsonArray("doors").size() + " doors, " + save.getAsJsonArray("barriers").size() + " barriers, "
				+ save.getAsJsonArray("mystery_boxes").size() + " box locations, " + signs + " signs, " + edits + " block edits.");
		for(Map.Entry<MapSignConverter.Kind, Integer> count : report.counts().entrySet())
			if(count.getValue() > 0)
				sender.sendMessage(ChatColor.GRAY + "  " + count.getKey().name().toLowerCase().replace('_', ' ') + ": " + ChatColor.WHITE + count.getValue());
		if(imported.has("report"))
			for(JsonElement line : imported.getAsJsonArray("report"))
				COMZombies.log.info("[import " + name + "] " + line.getAsString());
	}

	/**
	 * Keeps the arena's own bounds but grows them to cover everything the import needs (a lander's flight
	 * above the map, say), so riders are never told to return to the arena.
	 */
	private static void growBounds(JsonObject save, JsonObject imported)
	{
		if(!save.has("p1") || !save.has("p2") || save.get("p1").isJsonNull() || save.get("p2").isJsonNull())
		{
			save.add("p1", imported.get("p1"));
			save.add("p2", imported.get("p2"));
			return;
		}
		JsonObject[] corners = {save.getAsJsonObject("p1"), save.getAsJsonObject("p2"), imported.getAsJsonObject("p1"), imported.getAsJsonObject("p2")};
		JsonObject min = new JsonObject();
		JsonObject max = new JsonObject();
		for(String axis : new String[]{"x", "y", "z"})
		{
			double lo = Double.MAX_VALUE;
			double hi = -Double.MAX_VALUE;
			for(JsonObject corner : corners)
			{
				lo = Math.min(lo, corner.get(axis).getAsDouble());
				hi = Math.max(hi, corner.get(axis).getAsDouble());
			}
			min.addProperty(axis, lo);
			max.addProperty(axis, hi);
		}
		save.add("p1", min);
		save.add("p2", max);
	}

	private static JsonObject newArena()
	{
		JsonObject arena = new JsonObject();
		arena.add("save_data", new JsonObject());
		JsonObject settings = new JsonObject();
		settings.addProperty("min_players", 1);
		settings.addProperty("max_players", 8);
		settings.addProperty("teddy_bear_chance", 100);
		settings.addProperty("StartingGun", "M1911");
		settings.addProperty("dog_round_every_x", -1);
		settings.addProperty("max_ammo_replenish_clip", false);
		settings.addProperty("force_night", false);
		settings.addProperty("multiple_mystery_boxes", false);
		JsonObject powerUps = new JsonObject();
		powerUps.addProperty("drop_percentage", -1);
		settings.add("powerup_settings", powerUps);
		arena.add("settings", settings);
		return arena;
	}

	/** Arenas created before new-arena defaults existed were saved with zeros that switch these features off. */
	private static void repairSettings(JsonObject settings)
	{
		if(settings == null)
			return;
		if(settings.has("min_players") && settings.get("min_players").getAsInt() <= 0)
			settings.addProperty("min_players", 1);
		if(settings.has("teddy_bear_chance") && settings.get("teddy_bear_chance").getAsInt() <= 0)
			settings.addProperty("teddy_bear_chance", 100);
		if(settings.has("dog_round_every_x") && settings.get("dog_round_every_x").getAsInt() == 0)
			settings.addProperty("dog_round_every_x", ConfigManager.getMainConfig().dogRoundEveryX);
		JsonObject powerUps = settings.has("powerup_settings") ? settings.getAsJsonObject("powerup_settings") : null;
		if(powerUps != null && powerUps.has("drop_percentage") && powerUps.get("drop_percentage").getAsInt() == 0)
			powerUps.addProperty("drop_percentage", -1);
	}

	private static void mergeSettings(JsonObject settings, JsonObject importedSettings)
	{
		if(settings == null || importedSettings == null)
			return;
		for(String key : IMPORTED_SETTINGS)
			if(importedSettings.has(key))
				settings.add(key, importedSettings.get(key));
	}

	private static int applyBlockEdits(World world, JsonArray edits)
	{
		if(edits == null)
			return 0;
		int count = 0;
		for(JsonElement element : edits)
		{
			JsonObject edit = element.getAsJsonObject();
			try
			{
				BlockData data = Bukkit.createBlockData(edit.get("blockdata").getAsString());
				world.getBlockAt(edit.get("x").getAsInt(), edit.get("y").getAsInt(), edit.get("z").getAsInt()).setBlockData(data, false);
				count++;
			} catch(IllegalArgumentException ignored)
			{
			}
		}
		return count;
	}

	/**
	 * Writes each sign's text. A sign that isn't there becomes the sign in its {@code blockdata} when given (a
	 * sign the map only places mid-game), otherwise a wall sign facing the way the replaced block faced (the
	 * power switch is a lever).
	 */
	private static int writeSigns(World world, JsonArray signs)
	{
		if(signs == null)
			return 0;
		int count = 0;
		for(JsonElement element : signs)
		{
			JsonObject json = element.getAsJsonObject();
			Block block = world.getBlockAt(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
			BlockState state = block.getState();
			if(!(state instanceof Sign) && json.has("blockdata"))
			{
				try
				{
					block.setBlockData(Bukkit.createBlockData(json.get("blockdata").getAsString()), false);
					state = block.getState();
				} catch(IllegalArgumentException ignored)
				{
				}
			}
			if(!(state instanceof Sign))
			{
				BlockFace facing = block.getBlockData() instanceof Directional ? ((Directional) block.getBlockData()).getFacing() : BlockFace.NORTH;
				block.setType(Material.OAK_WALL_SIGN, false);
				BlockData data = block.getBlockData();
				if(data instanceof Directional && ((Directional) data).getFaces().contains(facing))
				{
					((Directional) data).setFacing(facing);
					block.setBlockData(data, false);
				}
				state = block.getState();
				if(!(state instanceof Sign))
					continue;
			}
			Sign sign = (Sign) state;
			JsonArray lines = json.getAsJsonArray("lines");
			for(int i = 0; i < 4 && i < lines.size(); i++)
				sign.setLine(i, lines.get(i).getAsString());
			sign.update(true, false);
			count++;
		}
		return count;
	}
}
