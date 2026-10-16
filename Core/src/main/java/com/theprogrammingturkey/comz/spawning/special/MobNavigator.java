package com.theprogrammingturkey.comz.spawning.special;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.spawning.HellHoundSpawner;
import com.theprogrammingturkey.comz.util.Compat;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Walks a mob to a location. Paper exposes the vanilla pathfinder ({@code Mob#getPathfinder().moveTo}); Spigot has
 * no path API, so there the mob is nudged straight at the goal and hops one-block steps.
 * <p>
 * A mob that gets no closer for a while first hops sideways to get off the wall, and is finally teleported next to
 * its goal, so a special never stays wedged on geometry.
 */
final class MobNavigator
{
	private static final long HOP_AFTER_MS = 2500L;
	private static final long HOP_EVERY_MS = 1000L;
	private static final long TELEPORT_AFTER_MS = 7000L;
	private static final double PROGRESS_BLOCKS = 1.0;
	private static final int GOAL_SEARCH_RADIUS = 2;

	private static Method getPathfinder;
	private static Method moveTo;
	private static boolean resolved;

	private static final Map<Mob, Progress> progress = new WeakHashMap<>();

	private static final class Progress
	{
		Location goal;
		double bestDistance;
		long lastProgressAt;
		long lastHopAt;

		Progress(Location goal, double distance, long now)
		{
			this.goal = goal;
			this.bestDistance = distance;
			this.lastProgressAt = now;
		}
	}

	private MobNavigator()
	{
	}

	static void moveTo(Mob mob, Location goal, double speed)
	{
		if(goal == null || goal.getWorld() != mob.getWorld())
			return;
		mob.setTarget(null);
		if(unstick(mob, goal))
			return;
		if(pathfind(mob, goal, speed))
			return;
		nudge(mob, goal, speed);
	}

	/** Tracks progress toward the goal; returns true when it moved the mob itself this tick. */
	private static boolean unstick(Mob mob, Location goal)
	{
		long now = System.currentTimeMillis();
		double distance = mob.getLocation().distance(goal);
		Progress p = progress.get(mob);
		if(p == null || !sameBlock(p.goal, goal))
		{
			progress.put(mob, new Progress(goal.clone(), distance, now));
			return false;
		}
		if(distance <= p.bestDistance - PROGRESS_BLOCKS)
		{
			p.bestDistance = distance;
			p.lastProgressAt = now;
			return false;
		}

		long stuckFor = now - p.lastProgressAt;
		if(stuckFor >= TELEPORT_AFTER_MS)
		{
			Location to = standingSpotNear(goal);
			if(to != null)
			{
				mob.getWorld().spawnParticle(Compat.SMOKE, mob.getLocation().add(0, 0.8, 0), 20, 0.3, 0.5, 0.3, 0.02);
				mob.teleport(to);
				mob.getWorld().spawnParticle(Compat.SMOKE, to.clone().add(0, 0.8, 0), 20, 0.3, 0.5, 0.3, 0.02);
			}
			p.bestDistance = mob.getLocation().distance(goal);
			p.lastProgressAt = now;
			return true;
		}
		if(stuckFor >= HOP_AFTER_MS && now - p.lastHopAt >= HOP_EVERY_MS && mob.isOnGround())
		{
			p.lastHopAt = now;
			Vector toGoal = goal.toVector().subtract(mob.getLocation().toVector()).setY(0);
			if(toGoal.lengthSquared() < 0.01)
				return false;
			Vector side = new Vector(-toGoal.getZ(), 0, toGoal.getX()).normalize();
			if(COMZombies.rand.nextBoolean())
				side.multiply(-1);
			mob.setVelocity(side.multiply(0.35).add(toGoal.normalize().multiply(0.1)).setY(0.45));
			return true;
		}
		return false;
	}

	private static Location standingSpotNear(Location goal)
	{
		Block centre = goal.getBlock();
		Location best = null;
		double bestSq = Double.MAX_VALUE;
		for(int dx = -GOAL_SEARCH_RADIUS; dx <= GOAL_SEARCH_RADIUS; dx++)
			for(int dz = -GOAL_SEARCH_RADIUS; dz <= GOAL_SEARCH_RADIUS; dz++)
				for(int dy = -2; dy <= 1; dy++)
				{
					Block feet = centre.getRelative(dx, dy, dz);
					if(!HellHoundSpawner.isStandableSpot(feet))
						continue;
					Location spot = feet.getLocation().add(0.5, 0, 0.5);
					double d = spot.distanceSquared(goal);
					if(d < bestSq)
					{
						bestSq = d;
						best = spot;
					}
				}
		return best;
	}

	private static boolean sameBlock(Location a, Location b)
	{
		return a.getWorld() == b.getWorld() && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
	}

	private static boolean pathfind(Mob mob, Location goal, double speed)
	{
		resolve();
		if(getPathfinder == null || moveTo == null)
			return false;
		try
		{
			Object pathfinder = getPathfinder.invoke(mob);
			Object ok = moveTo.invoke(pathfinder, goal, speed);
			return !(ok instanceof Boolean) || (Boolean) ok;
		} catch(ReflectiveOperationException | RuntimeException | LinkageError e)
		{
			return false;
		}
	}

	private static void resolve()
	{
		if(resolved)
			return;
		resolved = true;
		try
		{
			getPathfinder = Mob.class.getMethod("getPathfinder");
			Class<?> pathfinderClass = Class.forName("com.destroystokyo.paper.entity.Pathfinder");
			moveTo = pathfinderClass.getMethod("moveTo", Location.class, double.class);
		} catch(ReflectiveOperationException | LinkageError e)
		{
			getPathfinder = null;
			moveTo = null;
		}
	}

	private static void nudge(Mob mob, Location goal, double speed)
	{
		Location from = mob.getLocation();
		Vector dir = goal.toVector().subtract(from.toVector()).setY(0);
		if(dir.lengthSquared() < 0.01)
			return;
		dir.normalize();
		Vector velocity = dir.clone().multiply(0.22 * speed);
		velocity.setY(mob.getVelocity().getY());
		Block ahead = from.clone().add(dir).getBlock();
		if(mob.isOnGround() && ahead.getType().isSolid() && !ahead.getRelative(0, 1, 0).getType().isSolid())
			velocity.setY(0.42);
		mob.setVelocity(velocity);
	}
}
