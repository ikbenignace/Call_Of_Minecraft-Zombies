package com.theprogrammingturkey.comz.game.managers;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One physical press can reach the plugin twice (F plus the click it also produces, or both hands),
 * so every purchase entry point shares this per-player window. Only entry points call it; handlers
 * reached from an entry point must not, or they would reject their own caller.
 */
public final class BuyDebounce
{
	private static final long WINDOW_MS = 250L;
	private static final Map<UUID, Long> LAST_BUY = new HashMap<>();

	private BuyDebounce()
	{
	}

	public static boolean tryAcquire(Player player)
	{
		long now = System.currentTimeMillis();
		Long last = LAST_BUY.get(player.getUniqueId());
		if(last != null && now - last < WINDOW_MS)
			return false;
		LAST_BUY.put(player.getUniqueId(), now);
		return true;
	}

	public static void forget(Player player)
	{
		LAST_BUY.remove(player.getUniqueId());
	}

	public static void clear()
	{
		LAST_BUY.clear();
	}
}
