package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

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
		{
			savedPlayerInfo.put(player.getUniqueId(), info);
			writeBackup(player, info);
		}
	}

	public static void restorePlayerInfo(Player player)
	{
		if(savedPlayerInfo.containsKey(player.getUniqueId()))
		{
			CachedPlayerInfo info = savedPlayerInfo.get(player.getUniqueId());
			// While the plugin is disabling nothing can be scheduled any more.
			if(COMZombies.getPlugin().isEnabled())
				COMZombies.scheduleTask(() -> player.teleport(info.oldLoc));
			else
				player.teleport(info.oldLoc);
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
			deleteBackup(player.getUniqueId());
		}
	}

	/** Gives every online player still holding a cached pre-game state their things back (plugin disable). */
	public static void restoreAll()
	{
		for(UUID id : new ArrayList<>(savedPlayerInfo.keySet()))
		{
			Player player = Bukkit.getPlayer(id);
			if(player != null && player.isOnline())
				restorePlayerInfo(player);
		}
	}

	/** Restores the on-disk pre-game state of every online player (left behind by a crash). */
	public static void restoreAllBackups()
	{
		for(Player player : Bukkit.getOnlinePlayers())
			restoreBackup(player);
	}

	/**
	 * Restores a pre-game state that only survived on disk, which happens when the server crashed
	 * while the player was in a game. No-op while the in-memory state is still around.
	 */
	public static void restoreBackup(Player player)
	{
		if(savedPlayerInfo.containsKey(player.getUniqueId()))
			return;
		File file = backupFile(player.getUniqueId());
		if(!file.exists())
			return;
		try
		{
			YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
			player.getInventory().clear();
			player.getInventory().setContents(readItems(yml.getConfigurationSection("inventory"), player.getInventory().getContents().length));
			player.getInventory().setArmorContents(readItems(yml.getConfigurationSection("armor"), player.getInventory().getArmorContents().length));
			try
			{
				player.setGameMode(GameMode.valueOf(yml.getString("gamemode", "SURVIVAL")));
			} catch(IllegalArgumentException e)
			{
				player.setGameMode(GameMode.SURVIVAL);
			}
			player.setTotalExperience(yml.getInt("total_exp", 0));
			World world = Bukkit.getWorld(yml.getString("location.world", ""));
			if(world != null)
				player.teleport(new Location(world, yml.getDouble("location.x"), yml.getDouble("location.y"), yml.getDouble("location.z"),
						(float) yml.getDouble("location.yaw"), (float) yml.getDouble("location.pitch")));
			player.updateInventory();
			player.sendMessage(COMZombies.PREFIX + ChatColor.GREEN + "Your inventory from before your last zombies game was restored.");
			deleteBackup(player.getUniqueId());
		} catch(RuntimeException e)
		{
			COMZombies.log.log(Level.WARNING, "Could not restore the inventory backup of " + player.getName() + " (" + file.getPath() + ")", e);
		}
	}

	private static File backupFile(UUID id)
	{
		return new File(new File(COMZombies.getPlugin().getDataFolder(), "inventories"), id + ".yml");
	}

	private static void deleteBackup(UUID id)
	{
		File file = backupFile(id);
		if(file.exists() && !file.delete())
			COMZombies.log.log(Level.WARNING, "Could not delete the inventory backup " + file.getPath());
	}

	private static void writeBackup(Player player, CachedPlayerInfo info)
	{
		File file = backupFile(player.getUniqueId());
		YamlConfiguration yml = new YamlConfiguration();
		yml.set("name", player.getName());
		yml.set("gamemode", info.gameMode.name());
		yml.set("total_exp", info.totalExp);
		if(info.oldLoc.getWorld() != null)
		{
			yml.set("location.world", info.oldLoc.getWorld().getName());
			yml.set("location.x", info.oldLoc.getX());
			yml.set("location.y", info.oldLoc.getY());
			yml.set("location.z", info.oldLoc.getZ());
			yml.set("location.yaw", info.oldLoc.getYaw());
			yml.set("location.pitch", info.oldLoc.getPitch());
		}
		writeItems(yml, "inventory", info.invContents);
		writeItems(yml, "armor", info.armorContents);
		try
		{
			File dir = file.getParentFile();
			if(!dir.exists() && !dir.mkdirs())
				throw new IOException("Could not create " + dir.getPath());
			yml.save(file);
		} catch(IOException e)
		{
			COMZombies.log.log(Level.WARNING, "Could not write the inventory backup of " + player.getName(), e);
		}
	}

	private static void writeItems(YamlConfiguration yml, String path, ItemStack[] items)
	{
		yml.createSection(path);
		for(int i = 0; i < items.length; i++)
			if(items[i] != null)
				yml.set(path + "." + i, items[i]);
	}

	private static ItemStack[] readItems(ConfigurationSection section, int size)
	{
		ItemStack[] items = new ItemStack[size];
		if(section == null)
			return items;
		for(String key : section.getKeys(false))
		{
			try
			{
				int slot = Integer.parseInt(key);
				if(slot >= 0 && slot < size)
					items[slot] = section.getItemStack(key);
			} catch(NumberFormatException ignored)
			{
			}
		}
		return items;
	}
}
