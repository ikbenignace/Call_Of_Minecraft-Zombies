package com.theprogrammingturkey.comz.spawning.special;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.COMZConfig;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** The special enemy types from specials.json, keyed by id. */
public final class SpecialEnemyCatalogue
{
	private static final Map<String, SpecialEnemyType> TYPES = new LinkedHashMap<>();
	private static BossRules bossRules = new BossRules(new JsonObject());

	/** The catalogue's {@code boss} block: when bosses may first appear and how they must fit the map. */
	public static final class BossRules
	{
		public final int minRound;
		/** Collision box height cap, so a boss walks through any 2-high doorway or window. */
		public final double maxHeight;
		public final double maxWidth;
		public final double minPlayerDistance;
		/** How many of the eight blocks around a boss spawn must be walkable, so it is not boxed in. */
		public final int minOpenNeighbours;

		BossRules(JsonObject json)
		{
			minRound = Math.max(1, CustomConfig.getInt(json, "min_round", 5));
			maxHeight = Math.max(0.5, CustomConfig.getDouble(json, "max_height", 1.95));
			maxWidth = Math.max(0.3, CustomConfig.getDouble(json, "max_width", 0.9));
			minPlayerDistance = Math.max(0, CustomConfig.getDouble(json, "min_player_distance", 6));
			minOpenNeighbours = Math.max(0, Math.min(8, CustomConfig.getInt(json, "min_open_neighbours", 3)));
		}
	}

	private SpecialEnemyCatalogue()
	{
	}

	public static void load()
	{
		TYPES.clear();
		JsonElement root = ConfigManager.getConfig(COMZConfig.SPECIALS).getJson();
		JsonElement boss = root != null && root.isJsonObject() ? root.getAsJsonObject().get("boss") : null;
		bossRules = new BossRules(boss != null && boss.isJsonObject() ? boss.getAsJsonObject() : new JsonObject());
		if(root == null || !root.isJsonObject() || !root.getAsJsonObject().has("types"))
		{
			COMZombies.log.log(Level.WARNING, COMZombies.CONSOLE_PREFIX + "specials.json has no 'types' list; special enemies are unavailable.");
			return;
		}
		for(JsonElement elem : root.getAsJsonObject().getAsJsonArray("types"))
		{
			if(!elem.isJsonObject())
				continue;
			try
			{
				SpecialEnemyType type = new SpecialEnemyType(elem.getAsJsonObject());
				TYPES.put(type.id, type);
			} catch(RuntimeException e)
			{
				COMZombies.log.log(Level.WARNING, COMZombies.CONSOLE_PREFIX + "Skipping a broken entry in specials.json: " + elem, e);
			}
		}
	}

	public static BossRules bossRules()
	{
		return bossRules;
	}

	public static SpecialEnemyType get(String id)
	{
		return id == null ? null : TYPES.get(id.toLowerCase(Locale.ROOT));
	}

	public static List<SpecialEnemyType> all()
	{
		return Collections.unmodifiableList(new ArrayList<>(TYPES.values()));
	}
}
