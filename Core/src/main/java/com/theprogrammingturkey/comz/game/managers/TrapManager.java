package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Trap;
import com.theprogrammingturkey.comz.game.traps.TrapType;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-Game store of buyable {@link Trap}s, mirroring {@link TeleporterManager}. Each saved trap keeps its sign
 * location (top-level x/y/z), id, cost, type, region ({"zone": id} or {"p1", "p2"}), duration_seconds,
 * cooldown_seconds (-1 = type default) and requires_power.
 */
public class TrapManager
{
	private final Game game;

	private final Map<String, Trap> traps = new HashMap<>();

	public TrapManager(Game game)
	{
		this.game = game;
	}

	public void loadAllTrapsToGame(JsonArray trapsJson)
	{
		for(JsonElement trapElem : trapsJson)
		{
			if(!trapElem.isJsonObject())
				continue;
			JsonObject trapJson = trapElem.getAsJsonObject();

			Location loc = CustomConfig.getLocationWithWorld(trapJson, "", game.getWorld());
			String id = CustomConfig.getString(trapJson, "id", "missing").toLowerCase();
			Trap trap = new Trap(id, loc, CustomConfig.getInt(trapJson, "cost", 1000));
			// Traps saved before types existed have no "type": they stay generic radius traps that follow
			// the config.yml trap timings and are power-gated like before.
			trap.setTypeId(CustomConfig.getString(trapJson, "type", TrapType.GENERIC_ID));
			trap.setRequiresPower(CustomConfig.getBoolean(trapJson, "requires_power", true));
			trap.setDurationSeconds(CustomConfig.getInt(trapJson, "duration_seconds", Trap.USE_TYPE_DEFAULT));
			trap.setCooldownSeconds(CustomConfig.getInt(trapJson, "cooldown_seconds", Trap.USE_TYPE_DEFAULT));
			trap.setRadius(CustomConfig.getDouble(trapJson, "radius", -1));
			if(trapJson.has("region") && trapJson.get("region").isJsonObject())
			{
				JsonObject region = trapJson.getAsJsonObject("region");
				String zone = CustomConfig.getString(region, "zone", "");
				if(!zone.isEmpty())
					trap.setZone(zone);
				else if(region.has("p1") && region.has("p2"))
					trap.setCorners(CustomConfig.getLocationWithWorld(region, "p1", game.getWorld()), CustomConfig.getLocationWithWorld(region, "p2", game.getWorld()));
			}
			traps.put(id, trap);
		}
	}

	public JsonArray save()
	{
		JsonArray saveJson = new JsonArray();
		for(Map.Entry<String, Trap> entry : traps.entrySet())
		{
			Trap trap = entry.getValue();
			JsonObject trapJson = CustomConfig.locationToJsonNoWorld(trap.getCenter());
			trapJson.addProperty("id", entry.getKey());
			trapJson.addProperty("cost", trap.getCost());
			trapJson.addProperty("type", trap.getTypeId());
			trapJson.addProperty("requires_power", trap.requiresPower());
			trapJson.addProperty("duration_seconds", trap.getRawDurationSeconds());
			trapJson.addProperty("cooldown_seconds", trap.getRawCooldownSeconds());
			if(trap.getRadius() >= 0)
				trapJson.addProperty("radius", trap.getRadius());
			JsonObject region = new JsonObject();
			if(trap.getZoneId() != null)
				region.addProperty("zone", trap.getZoneId());
			else if(trap.getCorner1() != null && trap.getCorner2() != null)
			{
				region.add("p1", CustomConfig.locationToJsonNoWorld(trap.getCorner1()));
				region.add("p2", CustomConfig.locationToJsonNoWorld(trap.getCorner2()));
			}
			if(!region.entrySet().isEmpty())
				trapJson.add("region", region);
			saveJson.add(trapJson);
		}

		return saveJson;
	}

	/**
	 * Registers a trap at a sign, or - when the id already exists - moves its sign and updates the cost while
	 * keeping its type, region and timings. Saves all games.
	 */
	public Trap addTrap(String id, Location handle, int cost)
	{
		id = id.toLowerCase();
		Trap trap = traps.get(id);
		if(trap == null)
		{
			trap = new Trap(id, handle, cost);
			traps.put(id, trap);
		}
		else
		{
			trap.setHandle(handle);
			trap.setCost(cost);
		}
		GameManager.INSTANCE.saveAllGames();
		return trap;
	}

	/** First free id of the form {@code trap<n>}. */
	public String nextId()
	{
		int n = traps.size() + 1;
		while(traps.containsKey("trap" + n))
			n++;
		return "trap" + n;
	}

	public void removeTrap(String id)
	{
		id = id.toLowerCase();
		if(traps.remove(id) != null)
			GameManager.INSTANCE.saveAllGames();
	}

	public Trap getTrap(String id)
	{
		if(id == null)
			return null;
		return traps.get(id.toLowerCase());
	}

	public Map<String, Trap> getTraps()
	{
		return traps;
	}

	public void resetAll()
	{
		for(Trap trap : traps.values())
			trap.reset();
	}
}
