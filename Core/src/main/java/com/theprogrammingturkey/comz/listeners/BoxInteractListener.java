package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Right-click on a mystery-box CHEST → start / pick up the box. The chest is the box's in-game
 * representation (RandomBox.loadBox places it once INGAME), so clicking it must route to the box's
 * interact logic — the same way a [Zombies]/Mystery Box sign click does via {@link SignListener}.
 *
 * <p>Runs at {@code HIGH} priority and cancels the event when handled, so the vanilla chest inventory
 * never opens. Build-mode clicks are left untouched (handled by {@link BuildModeListener}).
 */
public class BoxInteractListener implements Listener
{
	@EventHandler(priority = EventPriority.HIGH)
	public void onRightClickBox(PlayerInteractEvent event)
	{
		if(event.getAction() != Action.RIGHT_CLICK_BLOCK)
			return;

		Block clicked = event.getClickedBlock();
		if(clicked == null || clicked.getType() != Material.CHEST)
			return;

		// Build mode owns its own clicks; don't intercept box-authoring there.
		if(com.theprogrammingturkey.comz.game.builder.BuildModeManager.INSTANCE.isInBuild(event.getPlayer()))
			return;

		if(!GameManager.INSTANCE.isPlayerInGame(event.getPlayer()))
			return;

		Game game = GameManager.INSTANCE.getGame(event.getPlayer());
		if(game == null || game.getStatus() != Game.GameStatus.INGAME)
			return;

		RandomBox box = game.boxManager.getBox(clicked.getLocation());
		if(box == null)
			return;

		box.interact(event.getPlayer());
		event.setCancelled(true);
	}
}
