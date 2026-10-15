package com.theprogrammingturkey.comz.game.features;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * One condition of a {@link Trigger}. Which fields matter depends on the {@link Kind}:
 * <ul>
 *     <li>{@code interact}: {@code point} (or the legacy quest {@code sign} id), optional {@code cost} and
 *     {@code visual} (an item floating on the point; {@code teddy} for the box teddy bear);</li>
 *     <li>{@code stand_on}: {@code zone} or {@code point}; {@code amount} players (0 = every living player);</li>
 *     <li>{@code kill_in_zone}: {@code zone} and {@code amount} kills;</li>
 *     <li>{@code ride_transport}: {@code transport} id;</li>
 *     <li>{@code round_reached}: {@code amount} is the round.</li>
 * </ul>
 */
public class TriggerStep
{
	public enum Kind
	{
		INTERACT("Interact"),
		STAND_ON("Stand on"),
		KILL_IN_ZONE("Kill in zone"),
		RIDE_TRANSPORT("Ride transport"),
		POWER_ON("Power on"),
		ROUND_REACHED("Round reached");

		private final String label;

		Kind(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		public String key()
		{
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind fromKey(String key)
		{
			if(key == null)
				return null;
			for(Kind kind : values())
				if(kind.name().equalsIgnoreCase(key.trim()))
					return kind;
			return null;
		}
	}

	public static final String NO_VISUAL = "none";
	public static final String TEDDY_VISUAL = "teddy";

	private final Kind kind;
	private Vector point;
	private String zone = "";
	private String transport = "";
	private String sign = "";
	private int amount;
	private int cost;
	private String visual = NO_VISUAL;
	private String description = "";

	private boolean done;
	private int progress;
	private final Set<UUID> contributors = new HashSet<>();

	public TriggerStep(Kind kind)
	{
		this.kind = kind;
		this.amount = defaultAmount(kind);
	}

	private static int defaultAmount(Kind kind)
	{
		switch(kind)
		{
			case KILL_IN_ZONE:
				return 10;
			case ROUND_REACHED:
				return 5;
			case STAND_ON:
				return 1;
			default:
				return 0;
		}
	}

	public Kind getKind()
	{
		return kind;
	}

	/** Block coordinates of the step's point, or null for zone/transport/state steps. */
	public Vector getPoint()
	{
		return point;
	}

	public void setPoint(Vector point)
	{
		this.point = point == null ? null : new Vector(point.getBlockX(), point.getBlockY(), point.getBlockZ());
	}

	public Location pointLocation(World world)
	{
		return point == null ? null : new Location(world, point.getX(), point.getY(), point.getZ());
	}

	public boolean isAtBlock(Location block)
	{
		return point != null && block != null && point.getBlockX() == block.getBlockX()
				&& point.getBlockY() == block.getBlockY() && point.getBlockZ() == block.getBlockZ();
	}

	public String getZone()
	{
		return zone;
	}

	public void setZone(String zone)
	{
		this.zone = zone == null ? "" : zone;
	}

	public boolean usesZone()
	{
		return !zone.isEmpty();
	}

	public String getTransport()
	{
		return transport;
	}

	public void setTransport(String transport)
	{
		this.transport = transport == null ? "" : transport;
	}

	/** Id of a legacy {@code [Zombies] / Quest} sign that completes this interact step. */
	public String getSign()
	{
		return sign;
	}

	public void setSign(String sign)
	{
		this.sign = sign == null ? "" : sign.trim();
	}

	public int getAmount()
	{
		return amount;
	}

	public void setAmount(int amount)
	{
		this.amount = Math.max(0, amount);
	}

	public int getCost()
	{
		return cost;
	}

	public void setCost(int cost)
	{
		this.cost = Math.max(0, cost);
	}

	public String getVisual()
	{
		return visual;
	}

	public void setVisual(String visual)
	{
		this.visual = visual == null || visual.trim().isEmpty() ? NO_VISUAL : visual.trim().toLowerCase(Locale.ROOT);
	}

	public boolean hasVisual()
	{
		return !NO_VISUAL.equals(visual);
	}

	public String getDescription()
	{
		return description;
	}

	public void setDescription(String description)
	{
		this.description = description == null ? "" : description;
	}

	public boolean isDone()
	{
		return done;
	}

	public void setDone(boolean done)
	{
		this.done = done;
	}

	public int getProgress()
	{
		return progress;
	}

	public int addProgress()
	{
		return ++progress;
	}

	public Set<UUID> getContributors()
	{
		return contributors;
	}

	public void resetProgress()
	{
		done = false;
		progress = 0;
		contributors.clear();
	}

	public String describe()
	{
		switch(kind)
		{
			case INTERACT:
				if(!sign.isEmpty())
					return "interact with quest sign '" + sign + "'";
				return "interact at " + coords() + (cost > 0 ? ", cost " + cost : "") + (hasVisual() ? ", visual " + visual : "");
			case STAND_ON:
				return "stand on " + (usesZone() ? zone : coords()) + " (" + (amount <= 0 ? "all players" : amount + " player(s)") + ")";
			case KILL_IN_ZONE:
				return "kill " + amount + " in " + zone;
			case RIDE_TRANSPORT:
				return "ride " + transport;
			case POWER_ON:
				return "power on";
			case ROUND_REACHED:
				return "reach round " + amount;
			default:
				return kind.key();
		}
	}

	private String coords()
	{
		return point == null ? "?" : point.getBlockX() + "," + point.getBlockY() + "," + point.getBlockZ();
	}

	public static TriggerStep load(JsonObject json)
	{
		Kind kind = Kind.fromKey(CustomConfig.getString(json, "kind", ""));
		if(kind == null)
			return null;
		TriggerStep step = new TriggerStep(kind);
		if(json.has("x") && json.has("y") && json.has("z"))
			step.setPoint(new Vector(CustomConfig.getInt(json, "x", 0), CustomConfig.getInt(json, "y", 0), CustomConfig.getInt(json, "z", 0)));
		step.setZone(CustomConfig.getString(json, "zone", ""));
		step.setTransport(CustomConfig.getString(json, "transport", ""));
		step.setSign(CustomConfig.getString(json, "sign", ""));
		step.setAmount(CustomConfig.getInt(json, "amount", step.amount));
		step.setCost(CustomConfig.getInt(json, "cost", 0));
		step.setVisual(CustomConfig.getString(json, "visual", NO_VISUAL));
		step.setDescription(CustomConfig.getString(json, "description", ""));
		return step;
	}

	public JsonObject save()
	{
		JsonObject json = new JsonObject();
		json.addProperty("kind", kind.key());
		if(point != null)
		{
			json.addProperty("x", point.getBlockX());
			json.addProperty("y", point.getBlockY());
			json.addProperty("z", point.getBlockZ());
		}
		if(!zone.isEmpty())
			json.addProperty("zone", zone);
		if(!transport.isEmpty())
			json.addProperty("transport", transport);
		if(!sign.isEmpty())
			json.addProperty("sign", sign);
		json.addProperty("amount", amount);
		if(cost > 0)
			json.addProperty("cost", cost);
		if(hasVisual())
			json.addProperty("visual", visual);
		if(!description.isEmpty())
			json.addProperty("description", description);
		return json;
	}
}
