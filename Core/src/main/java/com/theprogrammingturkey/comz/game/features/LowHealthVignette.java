package com.theprogrammingturkey.comz.game.features;

import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Red screen-edge tint while a player is hurt, done with a per-player world border whose warning
 * zone covers the whole map. Both calls are Paper/1.18+ API, so they are reflective and silently
 * do nothing on older servers.
 */
public final class LowHealthVignette
{
	private static final Set<UUID> shown = new HashSet<>();

	private LowHealthVignette()
	{
	}

	public static void show(Player player)
	{
		if(!shown.add(player.getUniqueId()))
			return;
		try
		{
			WorldBorder border = (WorldBorder) Bukkit.class.getMethod("createWorldBorder").invoke(null);
			border.setCenter(player.getLocation());
			border.setSize(1_000_000);
			border.setWarningDistance(1_000_000);
			Player.class.getMethod("setWorldBorder", WorldBorder.class).invoke(player, border);
		}
		catch(ReflectiveOperationException | RuntimeException ignored)
		{
		}
	}

	public static void clear(Player player)
	{
		if(!shown.remove(player.getUniqueId()))
			return;
		try
		{
			Player.class.getMethod("setWorldBorder", WorldBorder.class).invoke(player, (Object) null);
		}
		catch(ReflectiveOperationException | RuntimeException ignored)
		{
		}
	}

	public static void clearAll()
	{
		for(UUID id : new HashSet<>(shown))
		{
			Player player = Bukkit.getPlayer(id);
			if(player != null)
				clear(player);
		}
		shown.clear();
	}
}
