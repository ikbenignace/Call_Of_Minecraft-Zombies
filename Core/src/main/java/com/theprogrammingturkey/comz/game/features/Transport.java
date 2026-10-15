package com.theprogrammingturkey.comz.game.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A ride along an ordered list of waypoints (ziplines, landers, carts, elevators). Waypoints are the
 * rider's feet positions, stored world-less like every other arena feature; the zipline rope is drawn
 * {@link #ROPE_HEIGHT} above them so the rider hangs below it.
 */
public class Transport
{
	public static final double ROPE_HEIGHT = 2.2D;

	public enum Style
	{
		ZIPLINE("Zipline", 500, 10, 8.0D),
		PLATFORM("Platform", 750, 20, 4.0D),
		CART("Cart", 500, 10, 6.0D),
		TELEPORT("Elevator", 250, 5, 0.0D);

		private final String label;
		private final int defaultCost;
		private final int defaultCooldown;
		private final double defaultSpeed;

		Style(String label, int defaultCost, int defaultCooldown, double defaultSpeed)
		{
			this.label = label;
			this.defaultCost = defaultCost;
			this.defaultCooldown = defaultCooldown;
			this.defaultSpeed = defaultSpeed;
		}

		public String getLabel()
		{
			return label;
		}

		public String key()
		{
			return name().toLowerCase(Locale.ROOT);
		}

		public Style next()
		{
			Style[] all = values();
			return all[(ordinal() + 1) % all.length];
		}

		public static Style fromKey(String key)
		{
			if(key != null)
				for(Style style : values())
					if(style.key().equalsIgnoreCase(key.trim()))
						return style;
			return ZIPLINE;
		}
	}

	private final String id;
	private String name;
	private Style style;
	private final List<Vector> waypoints = new ArrayList<>();
	private final List<Vector> callPoints = new ArrayList<>();
	private int cost;
	private boolean requiresPower;
	private int cooldownSeconds;
	private double speed;
	private boolean twoWay;
	private int platformSize = 3;

	public Transport(String id, Style style)
	{
		this.id = id.toLowerCase(Locale.ROOT);
		this.style = style;
		this.name = "";
		applyStyleDefaults();
		this.twoWay = true;
	}

	/** Resets cost, cooldown and speed to the style's defaults (used while building, before options are typed). */
	public void applyStyleDefaults()
	{
		cost = style.defaultCost;
		cooldownSeconds = style.defaultCooldown;
		speed = style.defaultSpeed;
	}

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name == null ? "" : name.trim();
	}

	/** The admin-given name, or the style label ("Zipline") when none was set. */
	public String displayName()
	{
		return name.isEmpty() ? style.getLabel() : name;
	}

	public Style getStyle()
	{
		return style;
	}

	public void setStyle(Style style)
	{
		this.style = style;
	}

	public List<Vector> getWaypoints()
	{
		return waypoints;
	}

	public List<Vector> getCallPoints()
	{
		return callPoints;
	}

	/** Explicit call points, or both ends of the path when the admin placed none. */
	public List<Vector> effectiveCallPoints()
	{
		if(!callPoints.isEmpty() || waypoints.isEmpty())
			return callPoints;
		List<Vector> ends = new ArrayList<>();
		ends.add(waypoints.get(0));
		if(waypoints.size() > 1)
			ends.add(waypoints.get(waypoints.size() - 1));
		return ends;
	}

	public int getCost()
	{
		return cost;
	}

	public void setCost(int cost)
	{
		this.cost = Math.max(0, cost);
	}

	public boolean requiresPower()
	{
		return requiresPower;
	}

	public void setRequiresPower(boolean requiresPower)
	{
		this.requiresPower = requiresPower;
	}

	public int getCooldownSeconds()
	{
		return cooldownSeconds;
	}

	public void setCooldownSeconds(int cooldownSeconds)
	{
		this.cooldownSeconds = Math.max(0, cooldownSeconds);
	}

	public double getSpeed()
	{
		return speed;
	}

	public void setSpeed(double speed)
	{
		this.speed = Math.max(0.5D, Math.min(40D, speed));
	}

	public boolean isTwoWay()
	{
		return twoWay;
	}

	public void setTwoWay(boolean twoWay)
	{
		this.twoWay = twoWay;
	}

	public int getPlatformSize()
	{
		return platformSize;
	}

	public void setPlatformSize(int platformSize)
	{
		this.platformSize = Math.max(1, Math.min(9, platformSize));
	}

	public boolean isUsable()
	{
		return waypoints.size() >= 2;
	}

	public double length()
	{
		double total = 0;
		for(int i = 1; i < waypoints.size(); i++)
			total += waypoints.get(i - 1).distance(waypoints.get(i));
		return total;
	}

	/**
	 * Position {@code distance} blocks along the path, measured from the start ({@code reverse == false}) or
	 * from the end. Clamped to the path's ends.
	 */
	public Vector pointAt(double distance, boolean reverse)
	{
		if(waypoints.isEmpty())
			return new Vector();
		double total = length();
		double d = Math.max(0, Math.min(total, reverse ? total - distance : distance));
		for(int i = 1; i < waypoints.size(); i++)
		{
			Vector a = waypoints.get(i - 1);
			Vector b = waypoints.get(i);
			double segment = a.distance(b);
			if(d <= segment || i == waypoints.size() - 1)
			{
				double t = segment <= 0 ? 1 : Math.min(1, d / segment);
				return a.clone().add(b.clone().subtract(a).multiply(t));
			}
			d -= segment;
		}
		return waypoints.get(waypoints.size() - 1).clone();
	}

	public Vector start()
	{
		return waypoints.get(0);
	}

	public Vector end()
	{
		return waypoints.get(waypoints.size() - 1);
	}

	/** True when {@code point} is closer to the path's start than to its end. */
	public boolean nearerStart(Vector point)
	{
		return point.distanceSquared(start()) <= point.distanceSquared(end());
	}

	/** The waypoint closest to {@code point}: the safe place to drop a rider whose ride is cut short. */
	public Vector nearestWaypoint(Vector point)
	{
		Vector best = waypoints.get(0);
		for(Vector waypoint : waypoints)
			if(waypoint.distanceSquared(point) < best.distanceSquared(point))
				best = waypoint;
		return best;
	}

	/** Smallest distance from {@code point} to any waypoint or call point, for picking a transport to remove/edit. */
	public double distanceTo(Vector point)
	{
		double best = Double.MAX_VALUE;
		for(Vector v : waypoints)
			best = Math.min(best, v.distanceSquared(point));
		for(Vector v : callPoints)
			best = Math.min(best, v.distanceSquared(point));
		return Math.sqrt(best);
	}

	public static Location toLocation(World world, Vector v)
	{
		return new Location(world, v.getX(), v.getY(), v.getZ());
	}

	public static Transport load(JsonObject json)
	{
		String id = CustomConfig.getString(json, "id", "transport");
		Transport transport = new Transport(id, Style.fromKey(CustomConfig.getString(json, "style", "zipline")));
		transport.setName(CustomConfig.getString(json, "name", ""));
		transport.setCost(CustomConfig.getInt(json, "cost", transport.cost));
		transport.setRequiresPower(CustomConfig.getBoolean(json, "requires_power", false));
		transport.setCooldownSeconds(CustomConfig.getInt(json, "cooldown_seconds", transport.cooldownSeconds));
		transport.setSpeed(CustomConfig.getDouble(json, "speed", transport.speed));
		transport.setTwoWay(CustomConfig.getBoolean(json, "two_way", true));
		transport.setPlatformSize(CustomConfig.getInt(json, "platform_size", 3));
		readPoints(json, "waypoints", transport.waypoints);
		readPoints(json, "call_points", transport.callPoints);
		return transport;
	}

	public JsonObject save()
	{
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("name", name);
		json.addProperty("style", style.key());
		json.addProperty("cost", cost);
		json.addProperty("requires_power", requiresPower);
		json.addProperty("cooldown_seconds", cooldownSeconds);
		json.addProperty("speed", speed);
		json.addProperty("two_way", twoWay);
		json.addProperty("platform_size", platformSize);
		json.add("waypoints", writePoints(waypoints));
		json.add("call_points", writePoints(callPoints));
		return json;
	}

	private static void readPoints(JsonObject json, String key, List<Vector> into)
	{
		if(!json.has(key) || !json.get(key).isJsonArray())
			return;
		for(JsonElement elem : json.get(key).getAsJsonArray())
		{
			if(!elem.isJsonObject())
				continue;
			JsonObject point = elem.getAsJsonObject();
			into.add(new Vector(CustomConfig.getDouble(point, "x", 0), CustomConfig.getDouble(point, "y", 0), CustomConfig.getDouble(point, "z", 0)));
		}
	}

	private static JsonArray writePoints(List<Vector> points)
	{
		JsonArray array = new JsonArray();
		for(Vector v : points)
		{
			JsonObject point = new JsonObject();
			point.addProperty("x", v.getX());
			point.addProperty("y", v.getY());
			point.addProperty("z", v.getZ());
			array.add(point);
		}
		return array;
	}
}
