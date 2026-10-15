package com.theprogrammingturkey.comz.game.features;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A named box region of an arena (two block corners, inclusive) with optional environment flags. Zones
 * are generic: the environment uses the flags, other features (traps, triggers) can use them as plain
 * regions.
 */
public class Zone
{
	/** Gravity value meaning "no override, use the arena's gravity". */
	public static final double NO_GRAVITY_OVERRIDE = -1;

	private final String id;
	private String name;
	private World world;
	private int minX, minY, minZ, maxX, maxY, maxZ;
	private double gravity = NO_GRAVITY_OVERRIDE;
	private boolean noAir;
	/** Breathable pocket inside a no-air zone (a pressurised room on the moon); wins over any no-air zone. */
	private boolean airPocket;
	private double damagePerSecond;

	public Zone(String id, Location corner1, Location corner2)
	{
		this.id = id;
		this.name = "";
		setCorners(corner1, corner2);
	}

	public String getId()
	{
		return id;
	}

	/** Display name shown when a player enters; empty for a silent zone. */
	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name == null ? "" : name;
	}

	public boolean hasName()
	{
		return !name.isEmpty();
	}

	public void setCorners(Location corner1, Location corner2)
	{
		world = corner1.getWorld();
		minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
		minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
		minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
		maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
		maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
		maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
	}

	public World getWorld()
	{
		return world;
	}

	public Location getMin()
	{
		return new Location(world, minX, minY, minZ);
	}

	public Location getMax()
	{
		return new Location(world, maxX, maxY, maxZ);
	}

	/** True when the location lies inside any block of the zone (both corner blocks included). */
	public boolean contains(Location location)
	{
		if(location == null || location.getWorld() != world)
			return false;
		double x = location.getX(), y = location.getY(), z = location.getZ();
		return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
	}

	public long volume()
	{
		return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
	}

	public boolean hasGravityOverride()
	{
		return gravity > 0;
	}

	public double getGravity()
	{
		return gravity;
	}

	/** {@link #NO_GRAVITY_OVERRIDE} (or any value &lt;= 0) removes the override. */
	public void setGravity(double gravity)
	{
		this.gravity = gravity > 0 && !Double.isInfinite(gravity) ? Math.min(gravity, 10) : NO_GRAVITY_OVERRIDE;
	}

	public boolean isNoAir()
	{
		return noAir;
	}

	public void setNoAir(boolean noAir)
	{
		this.noAir = noAir;
	}

	public boolean isAirPocket()
	{
		return airPocket;
	}

	public void setAirPocket(boolean airPocket)
	{
		this.airPocket = airPocket;
	}

	public double getDamagePerSecond()
	{
		return damagePerSecond;
	}

	public void setDamagePerSecond(double damagePerSecond)
	{
		this.damagePerSecond = damagePerSecond > 0 && !Double.isInfinite(damagePerSecond) ? damagePerSecond : 0;
	}

	/** Short human summary of the flags, e.g. "gravity 0.4, no air". */
	public String describeFlags()
	{
		StringBuilder sb = new StringBuilder();
		if(hasGravityOverride())
			sb.append("gravity ").append(gravity);
		if(noAir)
			sb.append(sb.length() > 0 ? ", " : "").append("no air");
		if(airPocket)
			sb.append(sb.length() > 0 ? ", " : "").append("air pocket");
		if(damagePerSecond > 0)
			sb.append(sb.length() > 0 ? ", " : "").append(damagePerSecond).append(" dmg/s");
		return sb.length() == 0 ? "no flags" : sb.toString();
	}

	public static Zone load(JsonObject json, World world)
	{
		Location p1 = CustomConfig.getLocationWithWorld(json, "p1", world);
		Location p2 = CustomConfig.getLocationWithWorld(json, "p2", world);
		if(p1 == null || p2 == null)
			return null;
		Zone zone = new Zone(CustomConfig.getString(json, "id", "zone"), p1, p2);
		zone.setName(CustomConfig.getString(json, "name", ""));
		zone.setGravity(CustomConfig.getDouble(json, "gravity", NO_GRAVITY_OVERRIDE));
		zone.setNoAir(CustomConfig.getBoolean(json, "no_air", false));
		zone.setAirPocket(CustomConfig.getBoolean(json, "air_pocket", false));
		zone.setDamagePerSecond(CustomConfig.getDouble(json, "damage_per_second", 0));
		return zone;
	}

	public JsonObject save()
	{
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("name", name);
		json.add("p1", CustomConfig.locationToJsonNoWorld(getMin()));
		json.add("p2", CustomConfig.locationToJsonNoWorld(getMax()));
		json.addProperty("gravity", gravity);
		json.addProperty("no_air", noAir);
		if(airPocket)
			json.addProperty("air_pocket", true);
		json.addProperty("damage_per_second", damagePerSecond);
		return json;
	}
}
