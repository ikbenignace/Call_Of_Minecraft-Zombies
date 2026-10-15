package com.theprogrammingturkey.comz.spawning.special;

import com.google.gson.JsonElement;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.COMZConfig;
import com.theprogrammingturkey.comz.config.ConfigManager;

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

	private SpecialEnemyCatalogue()
	{
	}

	public static void load()
	{
		TYPES.clear();
		JsonElement root = ConfigManager.getConfig(COMZConfig.SPECIALS).getJson();
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

	public static SpecialEnemyType get(String id)
	{
		return id == null ? null : TYPES.get(id.toLowerCase(Locale.ROOT));
	}

	public static List<SpecialEnemyType> all()
	{
		return Collections.unmodifiableList(new ArrayList<>(TYPES.values()));
	}
}
