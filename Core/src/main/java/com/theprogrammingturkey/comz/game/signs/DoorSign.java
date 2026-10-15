package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

public class DoorSign implements IGameSign
{
	@Override
	public void onBreak(Game game, Player player, Location location)
	{
	}

	@Override
	public void onInteract(Game game, Player player, Location location, String[] lines)
	{
		tryOpen(game, player, game.doorManager.getDoorFromSign(location));
	}

	/** Buy path shared by the sign, the proximity key and the door hitbox. */
	public static void tryOpen(Game game, Player player, Door door)
	{
		if(door == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "An error occured when trying to open this door! Leave the game an contact an admin please.");
		}
		else if(door.isTriggerOnly())
		{
			return;
		}
		else if(door.isOpened())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "This door is already open!");
		}
		else if(door.requiresPower() && !game.isPowered())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "This door requires power to open!");
		}
		else if(!door.canOpen(PointManager.INSTANCE.getPlayerPoints(player).getPoints()))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
		}
		else
		{
			door.openDoor();
			door.playerDoorOpenSound();
			com.theprogrammingturkey.comz.util.SoundUtil.playKey(player, player.getLocation(), "door.buy", "ENTITY_EXPERIENCE_ORB_PICKUP", org.bukkit.SoundCategory.PLAYERS, 1f, 1f);
			PointManager.INSTANCE.takePoints(player, door.getCost());
			PointManager.INSTANCE.notifyPlayer(player);
			game.signPropManager.onDoorOpened(door);
			game.perkPoolManager.onDoorOpened();
			CommandUtil.sendMessageToPlayer(player, ChatColor.GREEN + "Door opened!");
		}
	}

	@Override
	public void onChange(Game game, Player player, SignChangeEvent event)
	{

	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
