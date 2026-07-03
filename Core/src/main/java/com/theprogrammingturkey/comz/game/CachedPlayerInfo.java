package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CachedPlayerInfo
{
	public static Map<UUID, CachedPlayerInfo> savedPlayerInfo = new HashMap<>();

	private Location oldLoc;
	private GameMode gameMode;
	private boolean flying;
	private int totalExp;
	private ItemStack[] invContents;
	private ItemStack[] armorContents;
	// #153 — the scoreboard the player had BEFORE joining a zombies game (typically the server
	// main board that prefix/nametag plugins such as NametagEdit attach to). The game swaps in its
	// own scoreboard on join; restoring this on leave/end stops those plugins' tab-list prefixes
	// from vanishing until a rejoin/reload. May be null when Bukkit has no ScoreboardManager.
	private Scoreboard oldScoreboard;

	public static void savePlayerInfo(Player player)
	{
		CachedPlayerInfo info = new CachedPlayerInfo();
		info.oldLoc = player.getLocation().clone();
		info.gameMode = player.getGameMode();
		info.flying = player.isFlying();
		info.totalExp = player.getTotalExperience();
		info.invContents = player.getInventory().getContents().clone();
		info.armorContents = player.getInventory().getArmorContents().clone();
		info.oldScoreboard = player.getScoreboard();
		//don't overwrite existing info
		if(!savedPlayerInfo.containsKey(player.getUniqueId()))
			savedPlayerInfo.put(player.getUniqueId(), info);
	}

	public static void restorePlayerInfo(Player player)
	{
		if(savedPlayerInfo.containsKey(player.getUniqueId()))
		{
			CachedPlayerInfo info = savedPlayerInfo.get(player.getUniqueId());
			COMZombies.scheduleTask(() -> player.teleport(info.oldLoc));
			player.setGameMode(info.gameMode);
			if(player.getAllowFlight())
				player.setFlying(info.flying);
			player.setTotalExperience(info.totalExp);
			player.getInventory().setContents(info.invContents);
			player.getInventory().setArmorContents(info.armorContents);
			// #153 — restore the player's pre-game scoreboard. Fall back to the server main
			// scoreboard when nothing was captured (e.g. join-time edge cases) so we never hand the
			// player a blank board that strips external prefix/nametag plugins.
			Scoreboard toRestore = info.oldScoreboard;
			if(toRestore == null && Bukkit.getScoreboardManager() != null)
				toRestore = Bukkit.getScoreboardManager().getMainScoreboard();
			if(toRestore != null)
				player.setScoreboard(toRestore);
			savedPlayerInfo.remove(player.getUniqueId());
		}
	}
}
