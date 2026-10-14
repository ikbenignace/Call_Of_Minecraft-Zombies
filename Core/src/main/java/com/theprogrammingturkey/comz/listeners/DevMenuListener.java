package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.game.dev.DevMenu;
import com.theprogrammingturkey.comz.util.COMZPermission;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class DevMenuListener implements Listener
{
	@EventHandler
	public void onClick(InventoryClickEvent event)
	{
		InventoryHolder holder = event.getInventory().getHolder();
		if(!(holder instanceof DevMenu))
			return;
		event.setCancelled(true);
		if(event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof DevMenu))
			return;
		Player player = (Player) event.getWhoClicked();
		if(!COMZPermission.DEBUG.hasPerm(player))
		{
			player.closeInventory();
			return;
		}
		((DevMenu) holder).handleClick(event.getSlot());
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event)
	{
		if(event.getInventory().getHolder() instanceof DevMenu)
			event.setCancelled(true);
	}
}
