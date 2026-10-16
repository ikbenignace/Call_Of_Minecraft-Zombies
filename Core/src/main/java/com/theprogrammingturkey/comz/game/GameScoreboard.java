package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;

public class GameScoreboard
{

	private final Game game;
	private final ScoreboardManager manager = Bukkit.getScoreboardManager();
	private final Scoreboard board;
	private final Team team;
	private final Objective objective;
	private final Score round;
	private final Score zombiesLeft;
	private final HashMap<Player, Score> playerScores = new HashMap<>();
	private final BossBar roundBar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);

	public GameScoreboard(Game game)
	{
		this.game = game;
		board = manager.getNewScoreboard();
		team = board.registerNewTeam(game.getName());
		team.setDisplayName(ChatColor.RED + game.getName());
		team.setCanSeeFriendlyInvisibles(true);
		team.setAllowFriendlyFire(false);
		objective = board.registerNewObjective(this.game.getName(), "dummy", ChatColor.RED + this.game.getName());
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		round = objective.getScore(ChatColor.RED + "Round");
		round.setScore(0);
		zombiesLeft = objective.getScore(ChatColor.RED + "Zombies Left");
		zombiesLeft.setScore(0);
		roundBar.setVisible(false);
	}

	public void addPlayer(Player player)
	{
		team.addEntry(player.getName());
		if(ConfigManager.getMainConfig().visualsRoundBar)
			roundBar.addPlayer(player);
		if(!game.isPlayerSpectating(player))
		{
			Score s = objective.getScore(player.getName());
			playerScores.put(player, s);
			for(Player pl : game.getPlayersInGame())
			{
				if(pl.isValid())
					pl.setScoreboard(board);
				playerScores.get(player).setScore(500);
			}
		}
		game.signManager.updateGame();
	}

	public void removePlayer(Player player)
	{
		team.removeEntry(player.getName());
		board.resetScores(player.getName());
		roundBar.removePlayer(player);
		// #153 — do NOT hand the player a blank scoreboard here. CachedPlayerInfo.restorePlayerInfo
		// (called just before this in the leave/end flow) restores the player's pre-game scoreboard,
		// which is usually the server main board that prefix/nametag plugins attach to. Overwriting
		// it with a fresh empty board was stripping those prefixes until a rejoin/reload. Only fall
		// back to the main scoreboard if, for some reason, nothing restored one.
		Scoreboard current = player.getScoreboard();
		if(current == null || current == this.board)
		{
			Scoreboard main = manager.getMainScoreboard();
			if(main != null)
				player.setScoreboard(main);
		}
		playerScores.remove(player);
		game.signManager.updateGame();
	}

	public void update()
	{
		round.setScore(game.getWave());
		objective.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Round " + game.getWave());
		int toSpawn = game.spawnManager.getMobsToSpawn();
		int left = (toSpawn - game.spawnManager.getMobsSpawned()) + game.spawnManager.getZombiesAlive();
		zombiesLeft.setScore(left);
		updateRoundBar(toSpawn, left);

		for(Player player : playerScores.keySet())
			playerScores.get(player).setScore(PointManager.INSTANCE.getPlayersPoints(player));

		game.signManager.updateGame();
	}

	/** BO keeps the round number on screen in red; the bar drains as the round's zombies die. */
	private void updateRoundBar(int roundTotal, int left)
	{
		int wave = game.getWave();
		roundBar.setVisible(wave > 0);
		if(wave <= 0)
			return;
		roundBar.setTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "ROUND " + wave);
		roundBar.setProgress(roundTotal <= 0 ? 0 : Math.max(0, Math.min(1, left / (double) roundTotal)));
	}
}
