package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.builder.SettingsMenu;
import com.theprogrammingturkey.comz.game.builder.SpecialEnemiesMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Routes clicks in the arena {@link SettingsMenu} chest-GUI to the menu (and persists on close).
 * Only fires for inventories whose holder is a {@code SettingsMenu}, so normal inventories are
 * untouched.
 */
public class SettingsMenuListener implements Listener
{
	@EventHandler
	public void onClick(InventoryClickEvent event)
	{
		InventoryHolder holder = event.getInventory().getHolder();
		if(!isMenu(holder))
			return;
		event.setCancelled(true); // the GUI is not for moving items
		if(event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder)
			return; // clicked their own inventory
		if(holder instanceof SpecialEnemiesMenu)
		{
			((SpecialEnemiesMenu) holder).handleClick(event.getSlot(), event.getClick());
			return;
		}
		SettingsMenu menu = (SettingsMenu) holder;
		if(event.getSlot() == SettingsMenu.SLOT_SPECIALS)
		{
			SpecialEnemiesMenu specials = new SpecialEnemiesMenu(menu.getGame());
			// Opening another inventory inside a click handler is not allowed on every server version.
			COMZombies.scheduleTask(1, () -> event.getWhoClicked().openInventory(specials.getInventory()));
			return;
		}
		menu.handleClick(event.getSlot(), event.getClick());
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event)
	{
		if(isMenu(event.getInventory().getHolder()))
			event.setCancelled(true);
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event)
	{
		InventoryHolder holder = event.getInventory().getHolder();
		if(holder instanceof SettingsMenu)
			((SettingsMenu) holder).save();
		else if(holder instanceof SpecialEnemiesMenu)
			((SpecialEnemiesMenu) holder).save();
	}

	private static boolean isMenu(InventoryHolder holder)
	{
		return holder instanceof SettingsMenu || holder instanceof SpecialEnemiesMenu;
	}
}
