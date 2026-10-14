package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.dev.DevMenu;
import com.theprogrammingturkey.comz.game.dev.DevTools;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class DevCommand extends SubCommand
{
	private static final List<String> SUBCOMMANDS = Arrays.asList("points", "round", "skip", "killall", "god", "perk", "gun", "pap",
			"ammo", "power", "powerup", "down", "revive", "box", "status");

	public DevCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.DEBUG.hasPerm(player))
		{
			CommandUtil.noPermission(player, "use the developer menu");
			return true;
		}

		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null)
		{
			error(player, "You must be in a game to use the developer menu. Join one with /zombies join.");
			return true;
		}

		if(args.length < 2)
		{
			player.openInventory(new DevMenu(player, game).getInventory());
			return true;
		}

		String sub = args[1].toLowerCase(Locale.ROOT);
		switch(sub)
		{
			case "points":
				points(player, args);
				break;
			case "round":
				Integer round = parseInt(player, args, 2, "/zombies dev round <n>");
				if(round != null)
					DevTools.setRound(player, game, round);
				break;
			case "skip":
				DevTools.skipRound(player, game);
				break;
			case "killall":
				DevTools.killAll(player, game);
				break;
			case "god":
				DevTools.toggleGod(player);
				break;
			case "perk":
				perk(player, game, args);
				break;
			case "gun":
				if(args.length < 3)
					error(player, "Usage: /zombies dev gun <name>");
				else
					DevTools.giveGun(player, game, joinName(args, 2));
				break;
			case "pap":
				DevTools.packAPunch(player, game);
				break;
			case "ammo":
				DevTools.maxAmmo(player, game);
				break;
			case "power":
				DevTools.power(player, game);
				break;
			case "powerup":
				powerUp(player, game, args);
				break;
			case "down":
				DevTools.down(player, game);
				break;
			case "revive":
				revive(player, game, args);
				break;
			case "box":
				DevTools.moveBox(player, game);
				break;
			case "status":
				DevTools.status(player, game);
				break;
			default:
				error(player, "Unknown dev command. Options: " + String.join(", ", SUBCOMMANDS));
		}
		return true;
	}

	private void points(Player player, String[] args)
	{
		Integer amount = parseInt(player, args, 2, "/zombies dev points <amount> [player]");
		if(amount == null)
			return;
		Player target = player;
		if(args.length > 3)
		{
			target = Bukkit.getPlayerExact(args[3]);
			if(target == null || GameManager.INSTANCE.getGame(target) == null)
			{
				error(player, args[3] + " is not online or not in a game.");
				return;
			}
		}
		DevTools.addPoints(player, target, amount);
	}

	private void perk(Player player, Game game, String[] args)
	{
		if(args.length < 3)
		{
			error(player, "Usage: /zombies dev perk <PERK|all>");
			return;
		}
		if(args[2].equalsIgnoreCase("all"))
		{
			DevTools.giveAllPerks(player, game);
			return;
		}
		PerkType perk = PerkType.getPerkType(args[2]);
		if(perk == null)
			error(player, "Unknown perk '" + args[2] + "'.");
		else
			DevTools.givePerk(player, game, perk);
	}

	private void powerUp(Player player, Game game, String[] args)
	{
		if(args.length < 3)
		{
			error(player, "Usage: /zombies dev powerup <TYPE>");
			return;
		}
		PowerUp powerUp = parsePowerUp(args[2]);
		if(powerUp == null)
			error(player, "Unknown power-up '" + args[2] + "'. Options: " + String.join(", ", powerUpNames()));
		else
			DevTools.spawnPowerUp(player, game, powerUp);
	}

	private void revive(Player player, Game game, String[] args)
	{
		Player target = player;
		if(args.length > 2)
		{
			target = Bukkit.getPlayerExact(args[2]);
			if(target == null || GameManager.INSTANCE.getGame(target) != game)
			{
				error(player, args[2] + " is not in your game.");
				return;
			}
		}
		DevTools.revive(player, target, game);
	}

	private static PowerUp parsePowerUp(String name)
	{
		for(PowerUp powerUp : PowerUp.values())
			if(powerUp != PowerUp.NONE && powerUp.name().equalsIgnoreCase(name))
				return powerUp;
		return null;
	}

	private static List<String> powerUpNames()
	{
		return Arrays.stream(PowerUp.values()).filter(p -> p != PowerUp.NONE).map(PowerUp::name).collect(Collectors.toList());
	}

	private static String joinName(String[] args, int from)
	{
		return String.join(" ", Arrays.copyOfRange(args, from, args.length)).replace('_', ' ');
	}

	private static Integer parseInt(Player player, String[] args, int index, String usage)
	{
		if(args.length <= index)
		{
			error(player, "Usage: " + usage);
			return null;
		}
		try
		{
			return Integer.parseInt(args[index]);
		} catch(NumberFormatException e)
		{
			error(player, "'" + args[index] + "' is not a whole number. Usage: " + usage);
			return null;
		}
	}

	private static void error(Player player, String message)
	{
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + message);
	}

	@Override
	public List<String> onTabComplete(Player player, String[] args)
	{
		if(!COMZPermission.DEBUG.hasPerm(player))
			return new ArrayList<>();

		if(args.length == 2)
			return filter(SUBCOMMANDS, args[1]);

		String sub = args[1].toLowerCase(Locale.ROOT);
		String current = args[args.length - 1];
		if(args.length == 3)
		{
			switch(sub)
			{
				case "points":
					return filter(Arrays.asList("1000", "10000", "100000", "-1000"), current);
				case "round":
					return filter(Arrays.asList("5", "10", "20", "30", "50"), current);
				case "perk":
					List<String> perks = Arrays.stream(PerkType.values()).filter(p -> p != PerkType.DER_WUNDERFIZZ).map(PerkType::name).collect(Collectors.toList());
					perks.add("all");
					return filter(perks, current);
				case "powerup":
					return filter(powerUpNames(), current);
				case "revive":
					return filter(playerNames(), current);
			}
		}

		if(sub.equals("gun"))
			return filter(WeaponManager.getGunNames().stream().map(name -> name.replace(' ', '_')).collect(Collectors.toList()), current);
		if(sub.equals("points") && args.length == 4)
			return filter(playerNames(), current);
		return new ArrayList<>();
	}

	private static List<String> playerNames()
	{
		return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
	}

	private static List<String> filter(List<String> options, String prefix)
	{
		String lower = prefix.toLowerCase(Locale.ROOT);
		return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
	}
}
