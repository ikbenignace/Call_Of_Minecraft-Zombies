package com.theprogrammingturkey.comz.spawning.special;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;

/**
 * Walks a mob to a location. Paper exposes the vanilla pathfinder ({@code Mob#getPathfinder().moveTo}); Spigot has
 * no path API, so there the mob is nudged straight at the goal and hops one-block steps.
 */
final class MobNavigator
{
	private static Method getPathfinder;
	private static Method moveTo;
	private static boolean resolved;

	private MobNavigator()
	{
	}

	static void moveTo(Mob mob, Location goal, double speed)
	{
		if(goal == null || goal.getWorld() != mob.getWorld())
			return;
		mob.setTarget(null);
		if(pathfind(mob, goal, speed))
			return;
		nudge(mob, goal, speed);
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
