package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.managers.BuyDebounce;
import com.theprogrammingturkey.comz.listeners.customEvents.PlayerTransportRideEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Locale;

/** Feeds trigger steps the events that are not routed through a manager: rides and right-clicks on step blocks. */
public class TriggerListener implements Listener
{
	@EventHandler
	public void onRide(PlayerTransportRideEvent event)
	{
		event.getGame().triggerManager.onRide(event.getPlayer(), event.getTransportId());
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onInteract(PlayerInteractEvent event)
	{
		if(event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null)
			return;
		Player player = event.getPlayer();
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != Game.GameStatus.INGAME || !game.getLivingPlayers().contains(player)
				|| game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
			return;
		Block block = event.getClickedBlock();
		if(!game.triggerManager.hasInteractAt(block))
			return;
		if(!isSwitch(block))
			event.setCancelled(true);
		if(BuyDebounce.tryAcquire(player))
			game.triggerManager.interactAtBlock(player, block);
	}

	/** Buttons and levers keep their own click animation when they are a step's point. */
	private static boolean isSwitch(Block block)
	{
		String name = block.getType().name().toUpperCase(Locale.ROOT);
		return name.endsWith("_BUTTON") || name.equals("LEVER");
	}
}
