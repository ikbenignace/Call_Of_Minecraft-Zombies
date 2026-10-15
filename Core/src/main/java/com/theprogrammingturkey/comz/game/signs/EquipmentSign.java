package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.equipment.Equipment;
import com.theprogrammingturkey.comz.game.equipment.EquipmentCatalogue;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

/**
 * Equipment wall buy: {@code [Zombies] / Equipment / <catalogue name> / <cost>}. A blank cost uses the
 * catalogue price. Buying again refills stackable equipment (mines, tacticals).
 */
public class EquipmentSign implements IGameSign
{
	public static Equipment equipmentOf(String[] lines)
	{
		return lines.length > 2 ? EquipmentCatalogue.get(ChatColor.stripColor(lines[2]).trim()) : null;
	}

	public static int costOf(String[] lines, Equipment equipment)
	{
		try
		{
			return Integer.parseInt(ChatColor.stripColor(lines[3]).trim());
		} catch(RuntimeException ex)
		{
			return equipment.cost;
		}
	}

	public static String prompt(Game game, Player player, String[] lines)
	{
		Equipment equipment = equipmentOf(lines);
		if(equipment == null)
			return ChatColor.RED + "[F] Unknown equipment";
		return game.equipmentManager.prompt(player, equipment, costOf(lines, equipment));
	}

	@Override
	public void onInteract(Game game, Player player, Location location, String[] lines)
	{
		Equipment equipment = equipmentOf(lines);
		if(equipment == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "That equipment doesn't exist!");
			return;
		}
		game.equipmentManager.buy(player, equipment, costOf(lines, equipment));
	}

	@Override
	public void onChange(Game game, Player player, SignChangeEvent event)
	{
		String name = event.getLine(2) == null ? "" : ChatColor.stripColor(event.getLine(2)).trim();
		Equipment equipment = EquipmentCatalogue.get(name);
		if(equipment == null)
		{
			event.setLine(0, ChatColor.RED + "Invalid Item!");
			event.setLine(1, "");
			event.setLine(2, "");
			event.setLine(3, "");
			return;
		}
		event.setLine(0, ChatColor.RED + "[Zombies]");
		event.setLine(1, ChatColor.AQUA + "Equipment");
		event.setLine(2, equipment.name);
		String cost = event.getLine(3) == null ? "" : ChatColor.stripColor(event.getLine(3)).trim();
		event.setLine(3, cost.matches("[0-9]{1,6}") ? cost : Integer.toString(equipment.cost));
	}

	@Override
	public void onBreak(Game game, Player player, Location location)
	{
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
