package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class CommandUtil
{
	/**
	 * @param player  to send the message to
	 * @param message to be sent to the player
	 */
	public static void sendMessageToPlayer(Player player, String message)
	{
		player.sendMessage(COMZombies.PREFIX + message);
	}

	/**
	 * @param player  to send the message to
	 * @param message to be sent to the player
	 */
	public static void sendClickableMessageToPlayer(Player player, String message, String clickMessage, String command)
	{
		TextComponent first = new TextComponent(COMZombies.PREFIX + message);
		//bold red
		TextComponent component = new TextComponent(clickMessage);
		component.setBold(true);
		component.setColor(net.md_5.bungee.api.ChatColor.GOLD);
		// The BaseComponent[] constructor is the only one that exists on every server from 1.14 to 26.x.
		component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{new TextComponent("Click to run command")}));
		component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
		player.spigot().sendMessage(first, component);
	}

	/**
	 * @param player for the no permission message to be sent to
	 */
	public static void noPermission(Player player, String action)
	{
		player.sendMessage(COMZombies.PREFIX + ChatColor.RED + "You do not have permission to " + action + "!");
	}

	public static void actionBar(Player player, String text)
	{
		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
	}
}
