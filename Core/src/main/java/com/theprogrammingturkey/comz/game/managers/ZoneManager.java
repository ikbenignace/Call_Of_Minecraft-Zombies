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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The zones of one arena. Shared registry: the environment, traps and triggers look regions up here.
 */
public class ZoneManager
{
	private final Game game;
	private final List<Zone> zones = new ArrayList<>();
	private final Set<String> switchedOff = new HashSet<>();

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

	/**
	 * Switches a zone off for the rest of the game (or back on): a switched-off zone no longer applies its
	 * environment, e.g. an air pocket after Moon's excavators breach it. Every zone is on again at game end.
	 */
	public void setSwitchedOff(String id, boolean off)
	{
		if(off)
			switchedOff.add(id.toLowerCase(Locale.ROOT));
		else
			switchedOff.remove(id.toLowerCase(Locale.ROOT));
	}

	public void switchAllOn()
	{
		switchedOff.clear();
	}

	/** Every switched-on zone containing {@code location}, smallest first so nested zones win over their parents. */
	public List<Zone> getZonesAt(Location location)
	{
		List<Zone> found = new ArrayList<>(2);
		for(Zone zone : zones)
			if(zone.contains(location) && !switchedOff.contains(zone.getId().toLowerCase(Locale.ROOT)))
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
