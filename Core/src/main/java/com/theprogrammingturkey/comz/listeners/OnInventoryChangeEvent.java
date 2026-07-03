package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.ProximityBuyManager;
import com.theprogrammingturkey.comz.game.weapons.WeaponInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class OnInventoryChangeEvent implements Listener
{

	@EventHandler
	public void onInventoryChangeEvent(InventoryClickEvent event)
	{
		Player player = (Player) event.getWhoClicked();
		if(GameManager.INSTANCE.isPlayerInGame(player))
			event.setCancelled(true);

		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != GameStatus.INGAME)
			return;

		if(game.getPlayersWeapons(player) != null)
		{
			PlayerWeaponManager gunManager = game.getPlayersWeapons(player);
			if(gunManager.isHeldItemWeapon())
			{
				WeaponInstance weapon = gunManager.getWeapon(player.getInventory().getHeldItemSlot());
				weapon.updateWeapon();
			}
		}
	}

	@EventHandler
	public void onPlayerSwapHandItemsEvent(PlayerSwapHandItemsEvent event)
	{
		if(GameManager.INSTANCE.isPlayerInGame(event.getPlayer()))
			event.setCancelled(true);

		Player player = event.getPlayer();
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != GameStatus.INGAME)
			return;

		// Proximity-buy: if the player is standing next to a door / machine (action-bar prompt shown),
		// F buys it via the existing DoorSign / MachineModelManager paths and we skip the default
		// held-weapon refresh so the press maps cleanly to "buy". When NOT near a buyable, F keeps its
		// original behavior (refresh the held weapon's ammo readout).
		ProximityBuyManager.BuyTarget target = game.proximityBuyManager.consumeBuyTarget(player);
		if(target != null)
		{
			if(target.type == ProximityBuyManager.TargetType.DOOR)
			{
				com.theprogrammingturkey.comz.game.signs.IGameSign doorHandler = SignListener.getSignHandler("door");
				if(doorHandler != null && target.location != null)
					doorHandler.onInteract(game, player, target.location, new String[0]);
			}
			else if(target.type == ProximityBuyManager.TargetType.MACHINE && target.location != null)
			{
				game.machineModelManager.handleInteractByCentre(player, target.location);
			}
			else if(target.type == ProximityBuyManager.TargetType.BOX && target.location != null)
			{
				com.theprogrammingturkey.comz.game.features.RandomBox box = game.boxManager.getBox(target.location);
				if(box != null)
					box.interact(player);
			}
			return;
		}

		if(game.getPlayersWeapons(player) != null)
		{
			PlayerWeaponManager gunManager = game.getPlayersWeapons(player);
			if(gunManager.isHeldItemWeapon())
			{
				WeaponInstance weapon = gunManager.getWeapon(player.getInventory().getHeldItemSlot());
				weapon.updateWeapon();
			}
		}
	}
}
