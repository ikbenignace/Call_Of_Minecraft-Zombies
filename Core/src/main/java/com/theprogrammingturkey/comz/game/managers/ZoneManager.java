package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.util.Util;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

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

	/** The zones that are areas of the map, leaving out the ones that only keep players or mobs out. */
	public List<Zone> getAreaZones()
	{
		List<Zone> areas = new ArrayList<>(zones.size());
		for(Zone zone : zones)
			if(zone.getPushOutTo() == null)
				areas.add(zone);
		return areas;
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

	public boolean hasPushOutZones()
	{
		for(Zone zone : zones)
			if(zone.getPushOutTo() != null)
				return true;
		return false;
	}

	/** Sends {@code player} to the spot of the first switched-on zone in {@code zonesAt} that keeps players out. */
	public boolean pushOut(Player player, List<Zone> zonesAt)
	{
		for(Zone zone : zonesAt)
		{
			if(!zone.pushesPlayers() || pushesBackIn(zone, true))
				continue;
			send(player, zone.getPushOutTo());
			return true;
		}
		return false;
	}

	/** Sends every mob standing in a switched-on zone that keeps mobs out to that zone's spot. */
	public void pushOutMobs(List<? extends Entity> mobs)
	{
		for(Zone zone : zones)
		{
			if(!zone.pushesMobs() || !isOn(zone) || pushesBackIn(zone, false))
				continue;
			for(Entity mob : mobs)
				if(mob.isValid() && zone.contains(mob.getLocation()))
					send(mob, zone.getPushOutTo());
		}
	}

	/** True when {@code location} lies in a switched-on zone that keeps mobs out, so nothing may spawn or land there. */
	public boolean pushesMobsAt(Location location)
	{
		if(location == null)
			return false;
		for(Zone zone : zones)
			if(zone.pushesMobs() && isOn(zone) && zone.contains(location))
				return true;
		return false;
	}

	/** A push spot inside a switched-on zone that pushes the same kind would teleport back and forth every tick. */
	private boolean pushesBackIn(Zone zone, boolean players)
	{
		Location to = zone.getPushOutTo();
		for(Zone other : zones)
			if((players ? other.pushesPlayers() : other.pushesMobs()) && isOn(other) && other.contains(to))
				return true;
		return false;
	}

	private boolean isOn(Zone zone)
	{
		return !switchedOff.contains(zone.getId().toLowerCase(Locale.ROOT));
	}

	private static void send(Entity entity, Location to)
	{
		Location at = entity.getLocation();
		to.setYaw(at.getYaw());
		to.setPitch(at.getPitch());
		entity.setVelocity(new Vector());
		entity.teleport(to);
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
		for(Zone zone : zones)
			if((zone.pushesPlayers() && pushesBackIn(zone, true)) || (zone.pushesMobs() && pushesBackIn(zone, false)))
				COMZombies.log.log(Level.WARNING, "Zone '" + zone.getId() + "' in arena '" + game.getName() + "' pushes out to a spot inside a zone that pushes the same entities out; it is ignored while that zone is on.");
	}

	public JsonArray save()
	{
		JsonArray json = new JsonArray();
		for(Zone zone : zones)
			json.add(zone.save());
		return json;
	}
}
