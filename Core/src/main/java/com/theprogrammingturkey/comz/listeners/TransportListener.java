package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.managers.BuyDebounce;
import com.theprogrammingturkey.comz.game.managers.TransportManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Keeps transport rides intact (no leaving a cart mid-air, no flying off a zipline, no fall damage) and lets
 * players right-click a call point's block as an alternative to pressing F.
 */
public class TransportListener implements Listener
{
	private static TransportManager managerOf(Entity entity)
	{
		if(entity instanceof Player)
		{
			Game game = GameManager.INSTANCE.getGame((Player) entity);
			return game == null ? null : game.transportManager;
		}
		for(Game game : GameManager.INSTANCE.getGames())
			if(game.transportManager.isTransportCart(entity))
				return game.transportManager;
		return null;
	}

	@EventHandler(ignoreCancelled = true)
	public void onVehicleExit(VehicleExitEvent event)
	{
		TransportManager manager = managerOf(event.getVehicle());
		if(manager != null && !manager.isReleasing() && event.getExited() instanceof Player && manager.isRiding((Player) event.getExited()))
			event.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onVehicleEnter(VehicleEnterEvent event)
	{
		TransportManager manager = managerOf(event.getVehicle());
		if(manager != null && !(event.getEntered() instanceof Player && manager.isRiding((Player) event.getEntered())))
			event.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onVehicleDamage(VehicleDamageEvent event)
	{
		if(managerOf(event.getVehicle()) != null)
			event.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onToggleFlight(PlayerToggleFlightEvent event)
	{
		TransportManager manager = managerOf(event.getPlayer());
		if(manager != null && event.isFlying() && manager.isRiding(event.getPlayer()))
			event.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onFall(EntityDamageEvent event)
	{
		if(event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player))
			return;
		TransportManager manager = managerOf(event.getEntity());
		if(manager != null && manager.isRiding((Player) event.getEntity()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onInteract(PlayerInteractEvent event)
	{
		if(event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null)
			return;
		Player player = event.getPlayer();
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != Game.GameStatus.INGAME || !game.getLivingPlayers().contains(player))
			return;
		TransportManager.CallTarget target = game.transportManager.findClickedCallPoint(event.getClickedBlock().getLocation());
		if(target == null)
			return;
		event.setCancelled(true);
		if(BuyDebounce.tryAcquire(player))
			game.transportManager.use(player, target);
	}
}
