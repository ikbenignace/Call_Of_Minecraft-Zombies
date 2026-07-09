package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.actions.BaseAction;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class PlayerChatListener implements Listener
{

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerChat(AsyncPlayerChatEvent playerChat)
	{
		COMZombies plugin = COMZombies.getPlugin();
		Player player = playerChat.getPlayer();
		String message = playerChat.getMessage().replaceFirst(" ", "").trim();

		// Build-mode price edit: a sneak-right-clicked sign is waiting for a typed price. Consume the
		// message and apply it on the main thread (block edits can't run from this async event).
		if(com.theprogrammingturkey.comz.game.builder.BuildModeManager.INSTANCE.hasPendingPriceEdit(player))
		{
			playerChat.setCancelled(true);
			COMZombies.scheduleTask(1, () -> com.theprogrammingturkey.comz.game.builder.BuildModeManager.INSTANCE.handlePriceChat(player, message));
			return;
		}

		if(plugin.activeActions.containsKey(player))
		{
			BaseAction action = plugin.activeActions.get(player);

			if(message.equalsIgnoreCase("cancel"))
			{
				COMZombies.scheduleTask(1, () ->
				{
					plugin.activeActions.remove(player);
					action.cancelAction();
				});
			}
			else
			{
				COMZombies.scheduleTask(1, () -> action.onChatMessage(message));
			}
			playerChat.setCancelled(true);
		}

		if(plugin.isEditingASign.containsKey(player))
		{
			// Any chat while editing a sign is consumed (not just the "done" message); otherwise a
			// player typing anything else would leak the message to the server while still locked
			// in sign-edit mode.
			playerChat.setCancelled(true);

			if(message.equalsIgnoreCase("done"))
			{
				Location loc = plugin.isEditingASign.get(player);
				plugin.isEditingASign.remove(player);
				CommandUtil.sendMessageToPlayer(player, "You are No longer editing a sign");
				// Block/sign mutation and the synchronous SignChangeEvent must run on the main thread,
				// not this async chat thread (calling a sync event from async throws). The location
				// and sign lines are captured now so the deferred task sees the intended state.
				COMZombies.scheduleTask(1, () ->
				{
					if(loc.getBlock().getState() instanceof Sign)
					{
						Sign sign = (Sign) loc.getBlock().getState();
						Bukkit.getServer().getPluginManager().callEvent(new SignChangeEvent(sign.getBlock(), player, sign.getLines()));
						sign.update();
					}
				});
			}
		}
	}
}
