package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.equipment.Equipment;
import com.theprogrammingturkey.comz.game.equipment.EquipmentCatalogue;
import com.theprogrammingturkey.comz.util.BlockUtils;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-click use of held equipment: place a mine, throw a tactical or toggle gear. */
public class EquipmentListener implements Listener
{
	@EventHandler(priority = EventPriority.HIGH)
	public void onUse(PlayerInteractEvent event)
	{
		if(event.getHand() != EquipmentSlot.HAND)
			return;
		Action action = event.getAction();
		if(action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
			return;
		Player player = event.getPlayer();
		Equipment equipment = EquipmentCatalogue.get(Equipment.nameOf(player.getInventory().getItemInMainHand()));
		if(equipment == null || equipment.isMonkeyBomb() || !GameManager.INSTANCE.isPlayerInGame(player))
			return;
		if(action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && BlockUtils.isSign(event.getClickedBlock().getType()))
			return;
		event.setCancelled(true);

		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != Game.GameStatus.INGAME || !game.getLivingPlayers().contains(player)
				|| game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
			return;

		switch(equipment.type)
		{
			case PLACEABLE:
				game.equipmentManager.usePlaceable(player, equipment, placeSpot(player, event), player.getLocation().getYaw());
				break;
			case TACTICAL:
				game.equipmentManager.useTactical(player, equipment);
				break;
			case GEAR:
				game.equipmentManager.useGear(player, equipment);
				break;
			default:
				break;
		}
	}

	/** On top of the clicked block when aiming at a floor, otherwise at the player's feet. */
	private static Location placeSpot(Player player, PlayerInteractEvent event)
	{
		Block clicked = event.getClickedBlock();
		if(event.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null && event.getBlockFace() == BlockFace.UP
				&& clicked.getLocation().distanceSquared(player.getLocation()) <= 16)
			return clicked.getLocation().add(0.5, 1.0, 0.5);
		Location feet = player.getLocation();
		return new Location(feet.getWorld(), feet.getBlockX() + 0.5, feet.getBlockY(), feet.getBlockZ() + 0.5);
	}
}
