package com.theprogrammingturkey.comz.spawning.special;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.spawning.RoundSpawner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One entry of specials.json: how a special enemy looks, how tough it is and what it does. */
public class SpecialEnemyType
{
	public final String id;
	public final String displayName;
	public final SpecialBehaviour behaviour;
	/** Entity type names, first one the server knows wins; ZOMBIE is the final fallback. */
	public final List<String> entities;
	public final boolean baby;
	public final double scale;
	public final double speed;
	public final boolean glowing;
	public final boolean showName;
	public final String model;
	public final Map<String, String> equipment;
	private final Map<String, String> sounds;

	public final double healthBase;
	public final double healthPerRound;
	/** When positive, health is this multiple of the round's zombie health instead of base + per round. */
	public final double healthZombieMultiplier;
	public final double healthPerExtraPlayer;

	public final int points;
	public final PowerUp rewardPowerUp;
	public final PowerUp roundEndPowerUp;
	public final int roundCountBase;
	public final int roundCountPerPlayer;
	public final int lifetimeSeconds;
	public final boolean instaKillImmune;
	public final boolean nukeImmune;
	public final JsonObject options;
	public final SpecialEnemyRule defaultRule;
	/** No rule spawns this type before this round unless the arena rule sets its own {@code min_round}. */
	public final int minRound;

	public SpecialEnemyType(JsonObject json)
	{
		id = CustomConfig.getString(json, "id", "unnamed").toLowerCase(Locale.ROOT);
		displayName = CustomConfig.getString(json, "name", id);
		SpecialBehaviour parsed = SpecialBehaviour.fromId(CustomConfig.getString(json, "behaviour", "boss"));
		behaviour = parsed == null ? SpecialBehaviour.BOSS : parsed;
		entities = stringList(json.get("entity"));
		baby = CustomConfig.getBoolean(json, "baby", false);
		scale = CustomConfig.getDouble(json, "scale", 1.0);
		speed = CustomConfig.getDouble(json, "speed", 1.0);
		glowing = CustomConfig.getBoolean(json, "glowing", false);
		showName = CustomConfig.getBoolean(json, "show_name", true);
		model = CustomConfig.getString(json, "model", "");
		equipment = stringMap(json, "equipment");
		sounds = stringMap(json, "sounds");

		JsonObject health = json.has("health") && json.get("health").isJsonObject() ? json.getAsJsonObject("health") : new JsonObject();
		healthBase = CustomConfig.getDouble(health, "base", 10);
		healthPerRound = CustomConfig.getDouble(health, "per_round", 0);
		healthZombieMultiplier = CustomConfig.getDouble(health, "zombie_multiplier", 0);
		healthPerExtraPlayer = CustomConfig.getDouble(health, "per_extra_player", 0);

		points = CustomConfig.getInt(json, "points", 100);
		rewardPowerUp = powerUp(CustomConfig.getString(json, "reward_powerup", "NONE"));
		roundEndPowerUp = powerUp(CustomConfig.getString(json, "round_end_powerup", "MAX_AMMO"));
		JsonObject count = json.has("round_count") && json.get("round_count").isJsonObject() ? json.getAsJsonObject("round_count") : new JsonObject();
		roundCountBase = Math.max(1, CustomConfig.getInt(count, "base", 1));
		roundCountPerPlayer = Math.max(0, CustomConfig.getInt(count, "per_player", 0));
		lifetimeSeconds = CustomConfig.getInt(json, "lifetime_seconds", 0);
		instaKillImmune = CustomConfig.getBoolean(json, "insta_kill_immune", behaviour == SpecialBehaviour.BOSS);
		nukeImmune = CustomConfig.getBoolean(json, "nuke_immune", behaviour == SpecialBehaviour.BOSS);
		minRound = Math.max(1, CustomConfig.getInt(json, "min_round", behaviour == SpecialBehaviour.BOSS ? SpecialEnemyCatalogue.bossRules().minRound : 1));
		options = json.has("options") && json.get("options").isJsonObject() ? json.getAsJsonObject("options") : new JsonObject();

		JsonObject rule = json.has("default_rule") && json.get("default_rule").isJsonObject() ? json.getAsJsonObject("default_rule") : new JsonObject();
		rule.addProperty("type", id);
		defaultRule = SpecialEnemyRule.fromJson(rule);
	}

	public double health(int wave, int players)
	{
		double hp = healthZombieMultiplier > 0
				? RoundSpawner.zombieHealth(wave) * healthZombieMultiplier
				: healthBase + healthPerRound * Math.max(0, wave - 1);
		hp *= 1 + healthPerExtraPlayer * Math.max(0, players - 1);
		return Math.max(1, hp);
	}

	public int roundCount(int players)
	{
		return roundCountBase + roundCountPerPlayer * Math.max(0, players - 1);
	}

	/** sounds.json key for {@code event}; the catalogue may point it elsewhere, else {@code special.<id>.<event>}. */
	public String soundKey(String event)
	{
		String key = sounds.get(event);
		return key == null || key.isEmpty() ? "special." + id + "." + event : key;
	}

	public double option(String key, double def)
	{
		return CustomConfig.getDouble(options, key, def);
	}

	public String option(String key, String def)
	{
		return CustomConfig.getString(options, key, def);
	}

	public boolean option(String key, boolean def)
	{
		return CustomConfig.getBoolean(options, key, def);
	}

	static PowerUp powerUp(String name)
	{
		try
		{
			return PowerUp.valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch(IllegalArgumentException | NullPointerException e)
		{
			return PowerUp.NONE;
		}
	}

	private static List<String> stringList(JsonElement elem)
	{
		if(elem == null || elem.isJsonNull())
			return Collections.emptyList();
		List<String> out = new ArrayList<>();
		if(elem.isJsonArray())
		{
			for(JsonElement e : (JsonArray) elem)
				if(e.isJsonPrimitive())
					out.add(e.getAsString());
		}
		else if(elem.isJsonPrimitive())
		{
			out.add(elem.getAsString());
		}
		return out;
	}

	private static Map<String, String> stringMap(JsonObject json, String key)
	{
		Map<String, String> out = new HashMap<>();
		if(!json.has(key) || !json.get(key).isJsonObject())
			return out;
		for(Map.Entry<String, JsonElement> e : json.getAsJsonObject(key).entrySet())
			if(e.getValue().isJsonPrimitive())
				out.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString());
		return out;
	}
}
