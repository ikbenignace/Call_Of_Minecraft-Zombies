package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.ConfigSetup;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.SoundUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

public class AmmoCrateSign implements IGameSign
{
	@Override
	public void onBreak(Game game, Player player, Location location)
	{

	}

	/** Price of refilling this gun at an ammo crate: PaP rate, the wall ammo price for wall guns, else the default. */
	public static int price(Game game, GunInstance gun)
	{
		ConfigSetup cfg = ConfigManager.getMainConfig();
		if(gun.isPackOfPunched())
			return cfg.ammoCratePackAPunchCost;
		int[] wallPrices = game.wallGunManager.getWallPrices(gun.getType());
		if(wallPrices != null)
			return GunSign.ammoCost(true, wallPrices[0], wallPrices[1]);
		return cfg.ammoCrateDefaultCost;
	}

	private static boolean isFull(GunInstance gun)
	{
		return gun.clipAmmo >= gun.getType().clipAmmo && gun.totalAmmo >= gun.getType().totalAmmo;
	}

	private static GunInstance heldGun(Game game, Player player)
	{
		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		return manager == null ? null : manager.getGun(player.getInventory().getHeldItemSlot());
	}

	/** Action-bar prompt for the gun the player is holding. */
	public static String prompt(Game game, Player player)
	{
		GunInstance gun = heldGun(game, player);
		if(gun == null)
			return ChatColor.GRAY + "No weapon";
		if(isFull(gun))
			return ChatColor.GRAY + "Ammo full";
		int cost = price(game, gun);
		ChatColor color = PointManager.INSTANCE.canBuy(player, cost) ? ChatColor.GREEN : ChatColor.RED;
		return color + "[F] Ammo " + ChatColor.YELLOW + "[" + cost + "]";
	}

	@Override
	public void onInteract(Game game, Player player, Location location, String[] lines)
	{
		GunInstance gun = heldGun(game, player);
		if(gun == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must be holding a gun to use the ammo crate!");
			return;
		}
		if(isFull(gun))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Ammo full!");
			return;
		}

		int cost = price(game, gun);
		if(!PointManager.INSTANCE.canBuy(player, cost))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You do not have enough points to buy that!");
			return;
		}

		PointManager.INSTANCE.takePoints(player, cost);
		PointManager.INSTANCE.notifyPlayer(player);
		gun.clipAmmo = gun.getType().clipAmmo;
		gun.maxAmmo();
		gun.updateWeapon();
		SoundUtil.playKey(player, player.getLocation(), "pickup.ammo", "ENTITY_ITEM_PICKUP", SoundCategory.PLAYERS, 1f, 1f);
	}

	@Override
	public void onChange(Game game, Player player, SignChangeEvent event)
	{
		String thirdLine = ChatColor.stripColor(event.getLine(2));

		if(thirdLine != null && thirdLine.equalsIgnoreCase("") && thirdLine.matches("[0-9]+"))
		{
			event.setLine(0, ChatColor.RED + "" + ChatColor.BOLD + "Invalid cost?");
			return;
		}
		event.setLine(0, ChatColor.RED + "[Zombies]");
		event.setLine(1, ChatColor.AQUA + "Ammo Crate");
		event.setLine(2, thirdLine);
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
