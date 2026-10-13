package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects optional server capabilities by probing for the actual classes/methods rather than parsing
 * the version string, so forks and new version numbering (26.x) are handled the same way.
 */
public final class ServerFeatures
{
	/** ItemDisplay / TextDisplay / Interaction entities (1.19.4+). */
	public static final boolean DISPLAY_ENTITIES = hasClass("org.bukkit.entity.ItemDisplay") && hasClass("org.bukkit.entity.Interaction");
	/** ItemMeta#setItemModel, required by the resource pack's item definitions (1.21.4+ clients). */
	public static final boolean ITEM_MODELS = hasMethod(ItemMeta.class, "setItemModel", NamespacedKey.class);

	private ServerFeatures()
	{
	}

	public static void logDegradedFeatures()
	{
		List<String> missing = new ArrayList<>();
		if(!DISPLAY_ENTITIES)
			missing.add("3D machine models, machine hitboxes and display animations (needs 1.19.4+)");
		if(!ITEM_MODELS)
			missing.add("resource pack item models (needs 1.21.4+)");
		for(String feature : missing)
			COMZombies.log.warning(COMZombies.CONSOLE_PREFIX + "Disabled on this server version: " + feature);
	}

	public static boolean hasClass(String name)
	{
		try
		{
			Class.forName(name, false, ServerFeatures.class.getClassLoader());
			return true;
		} catch(ClassNotFoundException | LinkageError e)
		{
			return false;
		}
	}

	public static boolean hasMethod(Class<?> owner, String name, Class<?>... params)
	{
		try
		{
			owner.getMethod(name, params);
			return true;
		} catch(NoSuchMethodException | LinkageError e)
		{
			return false;
		}
	}
}
