package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * On-disk record of every feature sign currently hidden for a game. Signs are taken off the walls while a
 * game runs, so a crash or kill would otherwise lose them for good; on the next enable every entry still
 * here is rebuilt. Written synchronously on each hide/restore since both are rare and tiny.
 */
public final class HiddenSignJournal
{
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, JsonObject> ENTRIES = new LinkedHashMap<>();
	private static final Map<String, JsonObject> PENDING = new LinkedHashMap<>();

	private HiddenSignJournal()
	{
	}

	private static File file()
	{
		return new File(COMZombies.getPlugin().getDataFolder(), "hidden-signs.json");
	}

	private static String key(Location loc)
	{
		return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
	}

	public static void record(Sign sign, Material replacement)
	{
		record(sign.getLocation(), sign.getType(), sign.getBlockData(), sign.getLines(), sign.getColor(), replacement);
	}

	/** Keeps the first entry per block, which is the real sign; a later one would only be the replacement. */
	static void record(Location loc, Material material, BlockData data, String[] lines, DyeColor color, Material replacement)
	{
		if(loc.getWorld() == null)
			return;
		String key = key(loc);
		if(ENTRIES.containsKey(key))
			return;
		PENDING.remove(key);

		JsonObject json = new JsonObject();
		json.addProperty("world", loc.getWorld().getName());
		json.addProperty("x", loc.getBlockX());
		json.addProperty("y", loc.getBlockY());
		json.addProperty("z", loc.getBlockZ());
		json.addProperty("material", material.name());
		if(data != null)
			json.addProperty("data", data.getAsString());
		JsonArray lineArray = new JsonArray();
		for(int i = 0; i < 4; i++)
			lineArray.add(lines != null && i < lines.length && lines[i] != null ? lines[i] : "");
		json.add("lines", lineArray);
		if(color != null)
			json.addProperty("color", color.name());
		if(replacement != null)
			json.addProperty("replacement", replacement.name());
		ENTRIES.put(key, json);
		save();
	}

	static void clear(Location loc)
	{
		if(loc.getWorld() != null && ENTRIES.remove(key(loc)) != null)
			save();
	}

	private static void save()
	{
		File file = file();
		File temp = new File(file.getParentFile(), file.getName() + ".tmp");
		try
		{
			file.getParentFile().mkdirs();
			JsonArray array = new JsonArray();
			for(JsonObject entry : ENTRIES.values())
				array.add(entry);
			for(JsonObject entry : PENDING.values())
				array.add(entry);
			try(Writer writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8))
			{
				GSON.toJson(array, writer);
			}
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch(IOException | RuntimeException e)
		{
			COMZombies.log.warning("Could not write the hidden sign journal: " + e.getMessage());
		}
	}

	/**
	 * Loads the entries a previous run left behind and rebuilds the ones whose world is loaded, overwriting
	 * whatever replaced them. Entries for worlds that are not loaded yet stay pending until the world loads.
	 * Entries for signs hidden by the current run are never touched.
	 */
	public static void restoreAll()
	{
		File file = file();
		if(file.exists())
		{
			try(Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
			{
				JsonElement root = new JsonParser().parse(reader);
				if(root != null && root.isJsonArray())
					for(JsonElement element : root.getAsJsonArray())
						addPending(element);
			} catch(IOException | RuntimeException e)
			{
				COMZombies.log.warning("Could not read the hidden sign journal: " + e.getMessage());
				return;
			}
		}
		restorePending();
	}

	private static void addPending(JsonElement element)
	{
		try
		{
			JsonObject entry = element.getAsJsonObject();
			String key = entry.get("world").getAsString() + ";" + entry.get("x").getAsInt() + ";" + entry.get("y").getAsInt() + ";" + entry.get("z").getAsInt();
			if(!ENTRIES.containsKey(key))
				PENDING.putIfAbsent(key, entry);
		} catch(RuntimeException e)
		{
			COMZombies.log.warning("Dropping an unreadable hidden sign journal entry: " + e.getMessage());
		}
	}

	private static void restorePending()
	{
		int restored = 0;
		boolean changed = false;
		Iterator<JsonObject> it = PENDING.values().iterator();
		while(it.hasNext())
		{
			JsonObject entry = it.next();
			try
			{
				World world = Bukkit.getWorld(entry.get("world").getAsString());
				if(world == null)
					continue;
				Location loc = new Location(world, entry.get("x").getAsInt(), entry.get("y").getAsInt(), entry.get("z").getAsInt());
				Material material = Material.valueOf(entry.get("material").getAsString());
				BlockData data = entry.has("data") ? Bukkit.createBlockData(entry.get("data").getAsString()) : null;
				JsonArray lineArray = entry.getAsJsonArray("lines");
				String[] lines = new String[4];
				for(int i = 0; i < 4; i++)
					lines[i] = i < lineArray.size() ? lineArray.get(i).getAsString() : "";
				DyeColor color = entry.has("color") ? DyeColor.valueOf(entry.get("color").getAsString()) : null;
				SignSnapshot.restoreBlock(loc, material, data, lines, color);
				restored++;
			} catch(RuntimeException e)
			{
				COMZombies.log.warning("Dropping an unreadable hidden sign journal entry: " + e.getMessage());
			}
			it.remove();
			changed = true;
		}
		if(changed)
			save();
		if(restored > 0)
			COMZombies.log.info(COMZombies.CONSOLE_PREFIX + "Restored " + restored + " feature sign(s) left hidden by an unclean shutdown.");
	}

	public static final class WorldLoadRetry implements Listener
	{
		@EventHandler
		public void onWorldLoad(WorldLoadEvent event)
		{
			if(!PENDING.isEmpty())
				restorePending();
		}
	}
}
