package com.theprogrammingturkey.comz.game.traps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.COMZConfig;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The trap catalogue loaded from {@code traps.json}, in file order. */
public final class TrapTypes
{
	private static final Map<String, TrapType> TYPES = new LinkedHashMap<>();
	private static TrapType generic = null;

	private TrapTypes()
	{
	}

	public static void load()
	{
		TYPES.clear();
		generic = null;
		JsonElement element = ConfigManager.getConfig(COMZConfig.TRAPS).getJson();
		JsonObject root = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		if(addMissingDefaults(root))
			ConfigManager.getConfig(COMZConfig.TRAPS).saveConfig(root);

		int pointsPerKill = CustomConfig.getInt(root, "points_per_kill", 0);
		if(root.has("types") && root.get("types").isJsonObject())
		{
			for(Map.Entry<String, JsonElement> entry : root.getAsJsonObject("types").entrySet())
			{
				if(!entry.getValue().isJsonObject())
					continue;
				try
				{
					TrapType type = new TrapType(entry.getKey(), entry.getValue().getAsJsonObject(), pointsPerKill);
					TYPES.put(type.getId(), type);
				} catch(RuntimeException e)
				{
					COMZombies.log.warning(COMZombies.CONSOLE_PREFIX + "Skipping trap type '" + entry.getKey() + "' in traps.json: " + e);
				}
			}
		}
		if(!TYPES.containsKey(TrapType.GENERIC_ID))
			TYPES.put(TrapType.GENERIC_ID, TrapType.builtinGeneric());
	}

	/** New types shipped with a plugin update are added to an older traps.json; edited types are left alone. */
	private static boolean addMissingDefaults(JsonObject root)
	{
		JsonObject defaults = bundledDefaults();
		if(defaults == null)
			return false;
		boolean changed = false;
		for(Map.Entry<String, JsonElement> entry : defaults.entrySet())
		{
			if(!root.has(entry.getKey()))
			{
				root.add(entry.getKey(), entry.getValue());
				changed = true;
			}
		}
		if(root.get("types").isJsonObject() && defaults.get("types").isJsonObject())
		{
			JsonObject types = root.getAsJsonObject("types");
			for(Map.Entry<String, JsonElement> entry : defaults.getAsJsonObject("types").entrySet())
			{
				if(!types.has(entry.getKey()))
				{
					types.add(entry.getKey(), entry.getValue());
					changed = true;
				}
			}
		}
		return changed;
	}

	private static JsonObject bundledDefaults()
	{
		InputStream stream = COMZombies.getPlugin().getResource(COMZConfig.TRAPS.getFileName());
		if(stream == null)
			return null;
		try(Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			JsonElement element = new JsonParser().parse(reader);
			JsonObject json = element.isJsonObject() ? element.getAsJsonObject() : null;
			return json != null && json.has("types") ? json : null;
		} catch(Exception e)
		{
			return null;
		}
	}

	/** The type with this id, or the generic type for unknown/blank ids so old and broken saves still work. */
	public static TrapType get(String id)
	{
		if(TYPES.isEmpty())
			TYPES.put(TrapType.GENERIC_ID, TrapType.builtinGeneric());
		TrapType type = id == null ? null : TYPES.get(id.toLowerCase(Locale.ROOT));
		return type != null ? type : getGeneric();
	}

	public static TrapType getGeneric()
	{
		TrapType type = TYPES.get(TrapType.GENERIC_ID);
		if(type != null)
			return type;
		if(generic == null)
			generic = TrapType.builtinGeneric();
		return generic;
	}

	public static boolean exists(String id)
	{
		return id != null && TYPES.containsKey(id.toLowerCase(Locale.ROOT));
	}

	public static List<TrapType> all()
	{
		if(TYPES.isEmpty())
			return Collections.singletonList(getGeneric());
		return Collections.unmodifiableList(new ArrayList<>(TYPES.values()));
	}
}
