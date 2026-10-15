package com.theprogrammingturkey.comz.game.features;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * What a {@link Trigger} does once all its steps are complete. {@code value} depends on the kind: door id,
 * teleporter name, sound key, power-up type ({@code random} allowed), perk type ({@code pool} reveals the next
 * pooled perk machine) or the message text ({@code &} colour codes).
 */
public class TriggerTarget
{
	public enum Kind
	{
		OPEN_DOOR("Open door", ""),
		ENABLE_PACK_A_PUNCH("Enable Pack-a-Punch", ""),
		ENABLE_TELEPORTER("Enable teleporter", ""),
		PLAY_SONG("Play song", "trigger.song"),
		GIVE_POWERUP("Give power-up", "random"),
		SPAWN_PERK("Spawn perk", "pool"),
		MESSAGE("Message", "&dSomething stirs..."),
		TURN_ON_POWER("Turn on power", ""),
		GIVE_POINTS("Give points", "");

		private final String label;
		private final String defaultValue;

		Kind(String label, String defaultValue)
		{
			this.label = label;
			this.defaultValue = defaultValue;
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

	private final Kind kind;
	private String value;
	private Vector point;
	private int amount;
	private boolean title = true;

	public TriggerTarget(Kind kind)
	{
		this.kind = kind;
		this.value = kind.defaultValue;
		this.amount = kind == Kind.GIVE_POINTS ? 5000 : 0;
	}

	public Kind getKind()
	{
		return kind;
	}

	public String getValue()
	{
		return value;
	}

	public void setValue(String value)
	{
		this.value = value == null ? "" : value.trim();
	}

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
		return point == null ? null : new Location(world, point.getX() + 0.5, point.getY(), point.getZ() + 0.5);
	}

	public int getAmount()
	{
		return amount;
	}

	public void setAmount(int amount)
	{
		this.amount = Math.max(0, amount);
	}

	/** Messages: shown as a title (true) or in chat (false). */
	public boolean isTitle()
	{
		return title;
	}

	public void setTitle(boolean title)
	{
		this.title = title;
	}

	public String describe()
	{
		switch(kind)
		{
			case OPEN_DOOR:
				return "open door " + value;
			case ENABLE_TELEPORTER:
				return "enable teleporter " + value;
			case PLAY_SONG:
				return "play song " + value;
			case GIVE_POWERUP:
				return "give power-up " + value + (point == null ? "" : " at " + point.getBlockX() + "," + point.getBlockY() + "," + point.getBlockZ());
			case SPAWN_PERK:
				return "spawn perk " + value;
			case MESSAGE:
				return (title ? "title " : "chat ") + "\"" + value + "\"";
			case GIVE_POINTS:
				return "give " + amount + " points";
			default:
				return kind.getLabel().toLowerCase(Locale.ROOT);
		}
	}

	public static TriggerTarget load(JsonObject json)
	{
		Kind kind = Kind.fromKey(CustomConfig.getString(json, "kind", ""));
		if(kind == null)
			return null;
		TriggerTarget target = new TriggerTarget(kind);
		target.setValue(CustomConfig.getString(json, "value", kind.defaultValue));
		if(json.has("x") && json.has("y") && json.has("z"))
			target.setPoint(new Vector(CustomConfig.getInt(json, "x", 0), CustomConfig.getInt(json, "y", 0), CustomConfig.getInt(json, "z", 0)));
		target.setAmount(CustomConfig.getInt(json, "amount", target.amount));
		target.setTitle(CustomConfig.getBoolean(json, "title", true));
		return target;
	}

	public JsonObject save()
	{
		JsonObject json = new JsonObject();
		json.addProperty("kind", kind.key());
		if(!value.isEmpty())
			json.addProperty("value", value);
		if(point != null)
		{
			json.addProperty("x", point.getBlockX());
			json.addProperty("y", point.getBlockY());
			json.addProperty("z", point.getBlockZ());
		}
		if(kind == Kind.GIVE_POINTS)
			json.addProperty("amount", amount);
		if(kind == Kind.MESSAGE)
			json.addProperty("title", title);
		return json;
	}
}
