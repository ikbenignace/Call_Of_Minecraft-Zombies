package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * BO zombies chase the player they can reach soonest, not the one closest through a wall. On Paper the length
 * of the mob's own pathfinder route decides; elsewhere (or when no route exists) straight-line distance does.
 * Targets are refreshed every {@code zombieRetargetTicks}; downed, dead and Who's Who ghost players are ignored.
 */
public final class ZombieTargeting
{
	/** Unreachable players are only chosen when nobody is reachable. */
	private static final double UNREACHABLE_PENALTY = 10000;
	private static final int PATHED_CANDIDATES = 2;

	private static final Map<Game, Integer> TASKS = new WeakHashMap<>();

	private static boolean reflectionTried;
	private static Method getPathfinder;
	private static Method findPath;
	private static Method getPoints;
	private static Method getFinalPoint;

	private ZombieTargeting()
	{
	}

	/** Starts the per-game retarget loop once; it stops itself when the game is no longer running. */
	public static void start(Game game)
	{
		Integer running = TASKS.get(game);
		if(running != null && running != -1 && (Bukkit.getScheduler().isQueued(running) || Bukkit.getScheduler().isCurrentlyRunning(running)))
			return;
		long period = Math.max(1, ConfigManager.getMainConfig().zombieRetargetTicks);
		final int[] id = {-1};
		id[0] = COMZombies.scheduleTask(period, period, () ->
		{
			if(game.getStatus() != Game.GameStatus.INGAME)
			{
				Bukkit.getScheduler().cancelTask(id[0]);
				TASKS.remove(game);
				return;
			}
			retargetAll(game);
		});
		TASKS.put(game, id[0]);
	}

	private static void retargetAll(Game game)
	{
		for(Mob mob : new ArrayList<>(game.spawnManager.getEntities()))
		{
			if(mob.isDead() || HellHoundSpawner.isDog(mob) || BossSpawner.isBoss(mob) || game.specialEnemyManager.isSteering(mob) || isLured(mob))
				continue;
			Player target = choose(game, mob);
			if(target != null && mob.getTarget() != target)
				mob.setTarget(target);
		}
	}

	/** A Monkey Bomb or other decoy holds the zombie's attention until it is gone. */
	private static boolean isLured(Mob mob)
	{
		LivingEntity current = mob.getTarget();
		return current != null && !(current instanceof Player) && current.isValid();
	}

	public static boolean isTargetable(Game game, Player player)
	{
		return player.isOnline() && !player.isDead() && game.getLivingPlayers().contains(player)
				&& !game.downedPlayerManager.isDownedPlayer(player) && !game.downedPlayerManager.isGhost(player);
	}

	public static Player choose(Game game, Entity entity)
	{
		List<Player> candidates = new ArrayList<>();
		for(Player player : game.getPlayersInGame())
			if(player.getWorld() == entity.getWorld() && isTargetable(game, player))
				candidates.add(player);
		if(candidates.isEmpty())
			return null;
		if(candidates.size() == 1)
			return candidates.get(0);

		boolean usePaths = ConfigManager.getMainConfig().zombiePathTargeting && entity instanceof Mob;
		if(usePaths && candidates.size() > PATHED_CANDIDATES)
		{
			Location from = entity.getLocation();
			candidates.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(from)));
			candidates = new ArrayList<>(candidates.subList(0, PATHED_CANDIDATES));
		}
		Player best = null;
		double bestCost = Double.MAX_VALUE;
		for(Player player : candidates)
		{
			double cost = usePaths ? pathCost((Mob) entity, player) : entity.getLocation().distance(player.getLocation());
			if(cost < bestCost)
			{
				best = player;
				bestCost = cost;
			}
		}
		return best;
	}

	private static double pathCost(Mob mob, Player player)
	{
		double straight = mob.getLocation().distance(player.getLocation());
		Double length = pathLength(mob, player);
		if(length == null)
			return straight;
		if(length < 0)
			return UNREACHABLE_PENALTY + straight;
		return length;
	}

	/** Route length along Paper's pathfinder; null without that API, negative when the route misses the player. */
	private static Double pathLength(Mob mob, Player player)
	{
		if(!resolveReflection())
			return null;
		try
		{
			Object pathfinder = getPathfinder.invoke(mob);
			Object path = pathfinder == null ? null : findPath.invoke(pathfinder, player);
			if(path == null)
				return -1.0;
			@SuppressWarnings("unchecked")
			List<Location> points = (List<Location>) getPoints.invoke(path);
			Location end = (Location) getFinalPoint.invoke(path);
			if(end == null || end.getWorld() != player.getWorld() || end.distanceSquared(player.getLocation()) > 9)
				return -1.0;
			double length = 0;
			Location previous = mob.getLocation();
			for(Location point : points)
			{
				if(point.getWorld() == previous.getWorld())
					length += point.distance(previous);
				previous = point;
			}
			return length;
		}
		catch(ReflectiveOperationException | RuntimeException e)
		{
			return null;
		}
	}

	private static boolean resolveReflection()
	{
		if(reflectionTried)
			return getPathfinder != null;
		reflectionTried = true;
		try
		{
			Class<?> pathfinderType = Class.forName("com.destroystokyo.paper.entity.Pathfinder");
			Class<?> resultType = Class.forName("com.destroystokyo.paper.entity.Pathfinder$PathResult");
			getPathfinder = Mob.class.getMethod("getPathfinder");
			findPath = pathfinderType.getMethod("findPath", LivingEntity.class);
			getPoints = resultType.getMethod("getPoints");
			getFinalPoint = resultType.getMethod("getFinalPoint");
		}
		catch(ReflectiveOperationException | RuntimeException e)
		{
			getPathfinder = null;
		}
		return getPathfinder != null;
	}
}
