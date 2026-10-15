package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.Trap;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import java.util.ArrayList;

/**
 * Activation sign (handle) of a {@link Trap}. Line 2 = trap id, line 3 = cost. onChange registers the trap at
 * the sign (keeping type/region of an existing trap with that id), onInteract buys a run, onBreak removes it.
 */
public class TrapSign implements IGameSign
{
	@Override
	public void onBreak(Game game, Player player, Location location)
	{
		if(game == null)
			return;
		for(Trap trap : new ArrayList<>(game.trapManager.getTraps().values()))
		{
			if(!location.equals(trap.getCenter()))
				continue;
			trap.reset();
			game.trapManager.removeTrap(trap.getId());
		}
	}

	@Override
	public void onInteract(Game game, Player player, Location location, String[] lines)
	{
		if(!GameManager.INSTANCE.isPlayerInGame(player))
			return;

		String trapId = ChatColor.stripColor(lines[2]).toLowerCase();
		Trap trap = game.trapManager.getTrap(trapId);
		if(trap == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "ERROR trap does not exist!");
			return;
		}

		if(trap.isPowerLocked(game))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must turn on the power first!");
			PerkType.noPower(player);
			return;
		}

		if(trap.isDoorLocked(game))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Open the door first!");
			return;
		}

		if(trap.isActive())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Trap is already active!");
			return;
		}

		// Cooldown gate before charging, so a recharging trap never takes points.
		int secondsLeft = trap.secondsUntilReady();
		if(secondsLeft > 0)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Trap is recharging! " + secondsLeft + "s left.");
			return;
		}

		int cost = trap.getCost();
		if(!PointManager.INSTANCE.canBuy(player, cost))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			return;
		}
		if(!trap.activate(game, player))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "This trap has no valid region.");
			return;
		}
		PointManager.INSTANCE.takePoints(player, cost);
		PointManager.INSTANCE.notifyPlayer(player);
		CommandUtil.sendMessageToPlayer(player, ChatColor.GREEN + trap.getType().getName() + " activated!");
	}

	@Override
	public void onChange(Game game, Player player, SignChangeEvent event)
	{
		String trapId = ChatColor.stripColor(event.getLine(2));
		if(trapId == null || trapId.isEmpty())
		{
			event.setLine(0, ChatColor.RED + "" + ChatColor.BOLD + "Missing");
			event.setLine(1, ChatColor.RED + "" + ChatColor.BOLD + "trap id!");
			event.setLine(2, "");
			event.setLine(3, "");
			return;
		}

		String costLine = ChatColor.stripColor(event.getLine(3));
		int cost;
		if(costLine == null || !costLine.matches("[0-9]+"))
			cost = 1000;
		else
			cost = Integer.parseInt(costLine);

		game.trapManager.addTrap(trapId, event.getBlock().getLocation(), cost);

		event.setLine(0, ChatColor.RED + "[Zombies]");
		event.setLine(1, ChatColor.AQUA + "Trap");
		event.setLine(2, trapId.toLowerCase());
		event.setLine(3, String.valueOf(cost));
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
