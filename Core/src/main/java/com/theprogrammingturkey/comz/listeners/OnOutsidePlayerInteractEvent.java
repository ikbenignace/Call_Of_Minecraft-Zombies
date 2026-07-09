package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public class OnOutsidePlayerInteractEvent implements Listener
{
	@EventHandler
	public void onOusidePlayerItemPickUp(EntityPickupItemEvent e)
	{
		if(!(e.getEntity() instanceof Player))
			return;
		Player player = (Player) e.getEntity();
		Game game = GameManager.INSTANCE.getGame(player.getLocation());
		if(game == null || game.getStatus() == null)
			return;

		if(game.getStatus() != GameStatus.INGAME)
			return;

		if(!GameManager.INSTANCE.isPlayerInGame(player) && GameManager.INSTANCE.isLocationInGame(player.getLocation()))
			e.setCancelled(true);

		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			// Fixed: previously this checked currentPowerUps.contains(e.getEntity()) — the PLAYER —
			// which is never a dropped power-up, so the branch was always taken and every power-up
			// pickup was DELETED instead of collected (PowerUpDropListener then ran on a removed
			// entity). Now: if the dropped item is a tracked power-up in this player's game, leave it
			// untouched so PowerUpDropListener can collect it; otherwise it's a stray item — remove it.
			Game playerGame = GameManager.INSTANCE.getGame(player);
			if(playerGame != null && playerGame.powerUpManager.currentPowerUps.contains(e.getItem()))
				return;
			e.getItem().remove();
		}
	}

	@EventHandler
	public void itemDrop(PlayerDropItemEvent event)
	{
		Player player = event.getPlayer();
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			event.setCancelled(true);
		}
		Location loc = player.getLocation();
		if(GameManager.INSTANCE.isLocationInGame(loc))
		{
			if(GameManager.INSTANCE.getGame(loc).getStatus() != GameStatus.INGAME)
				return;

			event.setCancelled(true);
			if(!GameManager.INSTANCE.isPlayerInGame(player))
				player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Do not drop items in this arena!");
		}
	}
}
