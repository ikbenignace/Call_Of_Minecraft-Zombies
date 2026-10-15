package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.builder.MapSignConverter;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * {@code /zombies convertsigns <arena> [apply]} — converts the buy signs of an imported command-block map
 * into COM:Z features. Without {@code apply} it is a dry run that only writes the report.
 */
public class ConvertSignsCommand extends SubCommand
{
	public ConvertSignsCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.BUILD.hasPerm(player))
		{
			CommandUtil.noPermission(player, "convert map signs");
			return true;
		}
		run(player, args);
		return true;
	}

	/** Shared by players and the console. */
	public static void run(CommandSender sender, String[] args)
	{
		if(args.length < 2 || !GameManager.INSTANCE.isValidArena(args[1]))
		{
			sender.sendMessage(ChatColor.RED + "Usage: /zombies convertsigns <arena> [apply]");
			return;
		}
		Game game = GameManager.INSTANCE.getGame(args[1]);
		if(game.getStatus() == Game.GameStatus.INGAME || game.getStatus() == Game.GameStatus.STARTING || !game.getPlayersInGame().isEmpty())
		{
			sender.sendMessage(ChatColor.RED + game.getName() + " has players in it! End the game first.");
			return;
		}
		boolean apply = args.length > 2 && args[2].equalsIgnoreCase("apply");
		MapSignConverter.Report report = MapSignConverter.convert(game, apply);

		sender.sendMessage(ChatColor.GOLD + (apply ? "Converted" : "Dry run:") + " " + report.entries.size() + " map buy signs in " + game.getName());
		for(Map.Entry<MapSignConverter.Kind, Integer> count : report.counts().entrySet())
			if(count.getValue() > 0)
				sender.sendMessage(ChatColor.GRAY + "  " + count.getKey().name().toLowerCase().replace('_', ' ') + ": " + ChatColor.WHITE + count.getValue());
		if(report.file != null)
			sender.sendMessage(ChatColor.GRAY + "Details: " + report.file.getPath());
		if(!apply)
			sender.sendMessage(ChatColor.YELLOW + "Run /zombies convertsigns " + game.getName() + " apply to convert. Doors and debris must still be set up in build mode.");
	}
}
