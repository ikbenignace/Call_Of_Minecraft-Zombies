package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

public class PowerSign implements IGameSign
{
	@Override
	public void onBreak(Game game, Player player, Location location)
	{
		game.turnOffPower();
		game.removePower(player);
	}

	@Override
	public void onInteract(Game game, Player player, Location location, String[] lines)
	{
		// Always surface a message so a click never silently does nothing — the historical "I clicked
		// power and nothing happened" was caused by hasPower() being false (pre-fix arenas had
		// power_setup saved as false), which fell through here with no feedback at all.
		if(!game.hasPower())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Power isn't set up for this arena! Ask an admin to place a [Zombies] Power sign.");
			return;
		}
		if(game.isPowered())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "The power is already on!");
			return;
		}
		if(game.isPowerSwitchPressed(location))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.YELLOW + "This switch is already on. Find the other one" + (game.getPowerSwitches() > 2 ? "s" : "") + "!");
			return;
		}
		game.pressPowerSwitch(location);
		if(game.isPowered())
			CommandUtil.sendMessageToPlayer(player, ChatColor.GREEN + "Power on!");
	}

	public static String prompt(Game game, Location location)
	{
		if(game.isPowered())
			return ChatColor.GRAY + "Power is on";
		if(game.isPowerSwitchPressed(location))
			return ChatColor.GRAY + "Switch on - Power " + game.pressedPowerSwitchCount() + "/" + game.getPowerSwitches();
		return ChatColor.GREEN + "[F] Turn on Power" + (game.getPowerSwitches() > 1 ? ChatColor.GRAY + " (" + game.pressedPowerSwitchCount() + "/" + game.getPowerSwitches() + ")" : "");
	}

	@Override
	public void onChange(Game game, Player player, SignChangeEvent sign)
	{
		sign.setLine(0, ChatColor.RED + "[Zombies]");
		sign.setLine(1, ChatColor.AQUA + "Power");
		//TODO: Check that there are no other power signs
		// Placing a power sign ENABLES the arena power system (perks/PaP/power-doors then gate on power).
		// Power still starts off each game; players switch it on by clicking this sign in-game.
		game.enablePower(player);
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
