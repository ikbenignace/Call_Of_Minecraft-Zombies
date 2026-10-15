package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.util.Util;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The zones of one arena. Shared registry: the environment, traps and triggers look regions up here.
 */
public class ZoneManager
{
	private final Game game;
	private final List<Zone> zones = new ArrayList<>();

	public ZoneManager(Game game)
	{
		this.game = game;
	}

	public List<Zone> getZones()
	{
		return Collections.unmodifiableList(zones);
	}

	public Zone getZone(String id)
	{
		for(Zone zone : zones)
			if(zone.getId().equalsIgnoreCase(id))
				return zone;
		return null;
	}

	public Zone getZoneByName(String name)
	{
		for(Zone zone : zones)
			if(zone.getName().equalsIgnoreCase(name))
				return zone;
		return null;
	}

	/** Every zone containing {@code location}, smallest first so nested zones win over their parents. */
	public List<Zone> getZonesAt(Location location)
	{
		List<Zone> found = new ArrayList<>(2);
		for(Zone zone : zones)
			if(zone.contains(location))
				found.add(zone);
		if(found.size() > 1)
			found.sort(Comparator.comparingLong(Zone::volume));
		return found;
	}

	public Zone getSmallestZoneAt(Location location)
	{
		List<Zone> found = getZonesAt(location);
		return found.isEmpty() ? null : found.get(0);
	}

	public boolean isInAnyZone(Location location)
	{
		for(Zone zone : zones)
			if(zone.contains(location))
				return true;
		return false;
	}

	public String nextId()
	{
		return Util.nextFreeId("zone", zones.size() + 1, id -> getZone(id) != null);
	}

	public void addZone(Zone zone)
	{
		zones.add(zone);
	}

	public void removeZone(Zone zone)
	{
		zones.remove(zone);
	}

	public void load(JsonArray zonesJson)
	{
		zones.clear();
		for(JsonElement element : zonesJson)
		{
			if(!element.isJsonObject())
				continue;
			Zone zone = Zone.load(element.getAsJsonObject(), game.arena.getWorld());
			if(zone != null)
				zones.add(zone);
		}
	}

	public JsonArray save()
	{
		JsonArray json = new JsonArray();
		for(Zone zone : zones)
			json.add(zone.save());
		return json;
	}
}
