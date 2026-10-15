package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Transport;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Chat syntax for a transport's options in build mode, e.g.
 * {@code cost=500 cooldown=30 speed=8 power one_way size=3 style=platform name=Main Lander}.
 * Tokens are separated by spaces or commas; {@code name=} takes the rest of the line.
 */
public final class TransportOptions
{
	public static final String USAGE = "cost=<points> cooldown=<seconds> speed=<blocks/s> power|no_power two_way|one_way size=<platform width> group=<cooldown group|none> relock|no_relock style=<zipline|platform|cart|teleport> name=<rest of line>";

	private TransportOptions()
	{
	}

	public static void prompt(Player player, Transport transport)
	{
		CommandUtil.sendMessageToPlayer(player, ChatColor.GOLD + "Editing " + transport.getId() + ": " + summary(transport));
		CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Type options in chat (" + USAGE + "), or 'done'.");
	}

	public static String summary(Transport transport)
	{
		return ChatColor.AQUA + transport.displayName() + ChatColor.GRAY + " (" + transport.getStyle().key()
				+ ", cost " + transport.getCost() + ", cooldown " + transport.getCooldownSeconds() + "s, speed "
				+ transport.getSpeed() + ", " + (transport.isTwoWay() ? "two way" : "one way")
				+ (transport.requiresPower() ? ", needs power" : "")
				+ (transport.getCooldownGroup().isEmpty() ? "" : ", cooldown group " + transport.getCooldownGroup())
				+ (transport.isRelockAfterUse() ? ", relocks after use" : "") + ", " + transport.getWaypoints().size() + " waypoints, "
				+ transport.getCallPoints().size() + " call points)";
	}

	public static void apply(Player player, Transport transport, String message)
	{
		String text = message.trim();
		if(text.equalsIgnoreCase("done") || text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("skip"))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Kept " + summary(transport));
			return;
		}

		String name = null;
		int nameAt = text.toLowerCase(Locale.ROOT).indexOf("name=");
		if(nameAt >= 0)
		{
			name = text.substring(nameAt + 5).trim();
			text = text.substring(0, nameAt);
		}

		StringBuilder errors = new StringBuilder();
		for(String token : text.split("[,\\s]+"))
		{
			if(token.isEmpty())
				continue;
			String[] kv = token.split("=", 2);
			String key = kv[0].toLowerCase(Locale.ROOT);
			String value = kv.length > 1 ? kv[1] : "";
			try
			{
				switch(key)
				{
					case "cost":
						transport.setCost(Integer.parseInt(value));
						break;
					case "cooldown":
						transport.setCooldownSeconds(Integer.parseInt(value));
						break;
					case "speed":
						transport.setSpeed(Double.parseDouble(value));
						break;
					case "size":
						transport.setPlatformSize(Integer.parseInt(value));
						break;
					case "power":
						transport.setRequiresPower(value.isEmpty() || Boolean.parseBoolean(value));
						break;
					case "no_power":
						transport.setRequiresPower(false);
						break;
					case "two_way":
						transport.setTwoWay(value.isEmpty() || Boolean.parseBoolean(value));
						break;
					case "one_way":
						transport.setTwoWay(false);
						break;
					case "group":
					case "cooldown_group":
						transport.setCooldownGroup(value.equalsIgnoreCase("none") ? "" : value);
						break;
					case "relock":
					case "relock_after_use":
						transport.setRelockAfterUse(value.isEmpty() || Boolean.parseBoolean(value));
						break;
					case "no_relock":
						transport.setRelockAfterUse(false);
						break;
					case "style":
						transport.setStyle(Transport.Style.fromKey(value));
						break;
					default:
						errors.append(' ').append(token);
				}
			} catch(NumberFormatException e)
			{
				errors.append(' ').append(token);
			}
		}
		if(name != null)
			transport.setName(name);

		GameManager.INSTANCE.saveAllGames();
		CommandUtil.sendMessageToPlayer(player, ChatColor.GREEN + "Saved " + summary(transport));
		if(errors.length() > 0)
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Ignored:" + errors + ChatColor.GRAY + " (" + USAGE + ")");
	}
}
