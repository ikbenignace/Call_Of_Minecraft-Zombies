package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

public class GunSign implements IGameSign
{
	@Override
	public void onBreak(Game game, Player player, Location location)
	{

	}

	@Override
	public void onInteract(Game game, Player player, Location location, String[] lines)
	{
		int buyPoints;
		int refillPoints;
		try
		{
			String line3 = ChatColor.stripColor(lines[3]);
			buyPoints = Integer.parseInt(line3.substring(0, line3.indexOf("/")).trim());
			refillPoints = Integer.parseInt(line3.substring(line3.indexOf("/") + 1).trim());
		} catch(RuntimeException ex)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "This wall gun has an invalid price!");
			return;
		}
		BaseGun gunType = WeaponManager.getGun(ChatColor.stripColor(lines[2]));

		if(gunType == null)
		{
			player.sendRawMessage(COMZombies.PREFIX + " Sorry! That gun doesn't seem to exist!");
			return;
		}

		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		GunInstance owned = manager.getGun(gunType);
		GunInstance ownedPackAPunched = gunType.isPackAPunchable() ? manager.getGun(gunType.getPackAPunchGun()) : null;
		if(owned != null || ownedPackAPunched != null)
		{
			GunInstance target = owned != null ? owned : ownedPackAPunched;
			int ammoCost;
			if(owned == null)
				ammoCost = ConfigManager.getMainConfig().wallAmmoPackAPunchCost;
			else if(ConfigManager.getMainConfig().wallAmmoPriceDivisor > 0)
				ammoCost = buyPoints / ConfigManager.getMainConfig().wallAmmoPriceDivisor;
			else
				ammoCost = refillPoints;

			if(PointManager.INSTANCE.canBuy(player, ammoCost))
			{
				PointManager.INSTANCE.takePoints(player, ammoCost);
				target.maxAmmo();
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "Filling ammo!");
				PointManager.INSTANCE.notifyPlayer(player);
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			}
		}
		else if(PointManager.INSTANCE.canBuy(player, buyPoints))
		{
			PointManager.INSTANCE.takePoints(player, buyPoints);
			manager.addWeapon(gunType);
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You got the " + ChatColor.GOLD + "" + ChatColor.BOLD + gunType.getName() + ChatColor.RED + ChatColor.BOLD + "!");
			com.theprogrammingturkey.comz.util.SoundUtil.play(player.getWorld(), player.getLocation(), com.theprogrammingturkey.comz.util.SoundConfig.get("gun.buy", "ENTITY_GHAST_SHOOT"), org.bukkit.SoundCategory.MASTER, 1, 1);
			PointManager.INSTANCE.notifyPlayer(player);
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
		}
	}

	@Override
	public void onChange(Game game, Player player, SignChangeEvent event)
	{
		String thirdLine = ChatColor.stripColor(event.getLine(2));
		String fourthLine = ChatColor.stripColor(event.getLine(3));

		if(thirdLine == null || thirdLine.equalsIgnoreCase(""))
		{
			event.setLine(0, ChatColor.RED + "" + ChatColor.BOLD + "No gun?");
			return;
		}
		event.setLine(0, ChatColor.RED + "[Zombies]");
		event.setLine(1, ChatColor.AQUA + "Gun");
		event.setLine(2, thirdLine);
		if(WeaponManager.getGun(thirdLine) == null)
		{
			event.setLine(0, ChatColor.RED + "Invalid Gun!");
			event.setLine(1, "");
			event.setLine(2, "");
			event.setLine(3, "");
			return;
		}
		String price = "";
		try
		{
			price += fourthLine.substring(0, fourthLine.indexOf("/")).trim();
			price += " / ";
			price += fourthLine.substring(fourthLine.indexOf("/") + 1).trim();
		} catch(Exception ex)
		{
			price = "200 / 100";
		}
		event.setLine(3, price);
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
