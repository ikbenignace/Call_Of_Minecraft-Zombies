package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.Wunderfizz;
import com.theprogrammingturkey.comz.listeners.customEvents.PlayerPerkPurchaseEvent;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

public class PerkMachineSign implements IGameSign
{
	@Override
	public void onBreak(Game game, Player player, Location location)
	{

	}

	@Override
	public void onInteract(Game game, Player player, Location location, String[] lines)
	{
		PerkType perk = PerkType.getPerkType(lines[2]);
		if(perk == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "An error occured when trying to buy this perk! Leave the game and contact an admin please.");
			return;
		}

		boolean soloRevive = perk == PerkType.QUICK_REVIVE && game.getPlayersInGame().size() == 1;
		if(!soloRevive && game.hasPower() && !game.isPowered())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must turn on the power first!");
			PerkType.noPower(player);
			return;
		}

		if(game.perkManager.isDrinking(player))
			return;

		if(perk != PerkType.DER_WUNDERFIZZ && game.perkManager.hasPerk(player, perk))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You already have " + perk + "!");
			return;
		}

		if(soloRevive && game.downedPlayerManager.getSelfReviveUses(player.getUniqueId(), ConfigManager.getMainConfig().soloQuickReviveUses) <= 0)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "Quick Revive is out of order!");
			return;
		}

		String costStr = ChatColor.stripColor(lines[3]);
		int cost;
		if(costStr != null && costStr.matches("[0-9]{1,5}"))
		{
			cost = Integer.parseInt(costStr);
		}
		else
		{
			cost = perk.getDefaultCost();
			CommandUtil.sendMessageToPlayer(player, costStr + " is not a valid amount!");
		}
		if(soloRevive)
			cost = ConfigManager.getMainConfig().soloQuickRevivePrice;

		if(perk == PerkType.DER_WUNDERFIZZ && Wunderfizz.interact(game, player, location, cost))
			return;

		if(!PointManager.INSTANCE.canBuy(player, cost))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You do not have enough points to buy this!");
			return;
		}

		if(perk == PerkType.DER_WUNDERFIZZ)
		{
			perk = game.perkManager.getRandomPerk(player);
			if(perk == null)
			{
				player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "You already have all the perks!");
				return;
			}
		}

		if(!game.perkManager.addPerk(player, perk))
			return;

		Bukkit.getPluginManager().callEvent(new PlayerPerkPurchaseEvent(player, perk));
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You now have " + perk.toString().toLowerCase() + "!");
		perk.initialEffect(player);
		perk.playDrinkAnimation(player);
		game.perkManager.startDrinking(player);
		game.perkManager.applyPerkEffect(player, perk);

		PointManager.INSTANCE.takePoints(player, cost);
		PointManager.INSTANCE.notifyPlayer(player);
	}

	@Override
	public void onChange(Game game, Player player, SignChangeEvent sign)
	{
		String thirdLine = ChatColor.stripColor(sign.getLine(2));
		String fourthLine = ChatColor.stripColor(sign.getLine(3));

		PerkType type = PerkType.getPerkType(thirdLine);
		if(type == null)
		{
			sign.setLine(0, ChatColor.RED + "" + ChatColor.BOLD + "No such");
			sign.setLine(1, ChatColor.RED + "" + ChatColor.BOLD + "perk!");
			sign.setLine(2, "");
			sign.setLine(3, "");
		}
		else
		{
			int cost;
			if(fourthLine != null && fourthLine.matches("[0-9]{1,5}"))
			{
				cost = Integer.parseInt(fourthLine);
			}
			else
			{
				cost = type.getDefaultCost();
				if(fourthLine != null && !fourthLine.isEmpty())
					CommandUtil.sendMessageToPlayer(player, fourthLine + " is not a valid amount!");
			}

			sign.setLine(0, ChatColor.RED + "[Zombies]");
			sign.setLine(1, ChatColor.AQUA + "Perk Machine");
			sign.setLine(2, type.toString().toLowerCase());
			sign.setLine(3, Integer.toString(cost));
		}
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
