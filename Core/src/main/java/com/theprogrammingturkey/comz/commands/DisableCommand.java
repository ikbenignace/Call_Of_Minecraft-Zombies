package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class DisableCommand extends SubCommand
{
	public DisableCommand(COMZPermission permission)
	{
		super(permission);
	}
	
	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.DISABLE_ARENA.hasPerm(player))
		{
			CommandUtil.noPermission(player, "disable this arena");
			return true;
		}

		if(args.length == 1)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Incorrect usage! Please use /zombies disable [arena]");
			return true;
		}
		else
		{
			if(GameManager.INSTANCE.isValidArena(args[1]))
			{
				Game game = GameManager.INSTANCE.getGame(args[1]);
				if(game == null)
				{
					CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "That arena does not exist!");
				}
				else
				{
					game.setDisabled();
					CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Arena " + game.getName() + " has been disabled!");
					return true;
				}
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "That arena does not exist!");
			}
		}
		return true;
	}

}
