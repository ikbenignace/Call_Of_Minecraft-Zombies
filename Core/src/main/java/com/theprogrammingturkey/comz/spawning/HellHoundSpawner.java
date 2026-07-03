package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.BlockUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class HellHoundSpawner extends RoundSpawner
{
	@Override
	public Mob spawnEntity(Game game, SpawnPoint loc, int wave)
	{
		World world = loc.getLocation().getWorld();

		if(world == null)
			return null;

		// #125 — Spawn hellhounds at configured, door-aware spawn points (the same way zombies
		// spawn) rather than scanning an arbitrary radius around a player. The old radius scan
		// ignored door state, so dogs appeared in locked/unopened areas of the arena. Using the
		// spawn-point set keeps dogs in reachable areas and respects room progression.
		List<SpawnPoint> spawnable = new ArrayList<>();
		for(SpawnPoint p : game.spawnManager.getPoints())
			if(game.spawnManager.canSpawnPoint(p))
				spawnable.add(p);

		Player spawnPlayer = game.getPlayersInGame().get(COMZombies.rand.nextInt(game.getPlayersInGame().size()));
		Location location = null;

		if(!spawnable.isEmpty())
		{
			// Prefer spawnable points near a random player so dogs converge on the action.
			SpawnPoint nearest = null;
			double nearestDist = Double.MAX_VALUE;
			for(SpawnPoint p : spawnable)
			{
				double d = p.getLocation().distanceSquared(spawnPlayer.getLocation());
				if(d < nearestDist)
				{
					nearestDist = d;
					nearest = p;
				}
			}
			if(nearest != null)
				location = nearest.getLocation().clone().add(0.5, 0, 0.5);
		}

		if(location == null)
			return null;

		world.strikeLightning(location);

		// Capture a final reference for the delayed fire-clear task (the local `location` is
		// reassigned above so it isn't effectively final).
		final Location fireCheckLoc = location;
		COMZombies.scheduleTask(10, () ->
		{
			if(fireCheckLoc.getBlock().getType().equals(Material.FIRE))
				BlockUtils.setBlockToAir(fireCheckLoc);
		});

		Wolf wolf = (Wolf) world.spawnEntity(location, EntityType.WOLF);
		wolf.setFireTicks(99999999);
		wolf.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 1, true));
		// #125 — make the wolf actively hostile so it actually path-finds to and attacks players.
		// Without setAngry a wild wolf may target a player (setTarget) but not melee it reliably.
		wolf.setAngry(true);
		setFollowDistance(wolf, 512);

		float strength = zombieHealth(wave);
		setMaxHealth(wolf, strength);
		wolf.setHealth(strength);

		setSpeed(wolf, 1.15f);

		// #125 — removed the blanket 60-second auto-remove. The stuck-zombie teleporter in
		// SpawnManager.checkStuck already handles dogs that can't reach players by teleporting them
		// to a reachable spawn near the target, so dogs no longer need to vanish on a timer. The old
		// behaviour killed dogs that simply hadn't reached a player yet, making dog rounds trivial.
		return wolf;
	}
}
