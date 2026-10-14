package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.BlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class HellHoundSpawner extends RoundSpawner
{
	@Override
	public Mob spawnEntity(Game game, SpawnPoint loc, int wave)
	{
		World world = loc.getLocation().getWorld();

		if(world == null)
			return null;

		// #125 — dogs use the door-aware spawn point SpawnManager already picked (biased toward a
		// player) instead of scanning a radius, so they never appear in locked areas.
		Location location = loc.getLocation().clone().add(0.5, 0, 0.5);

		world.strikeLightningEffect(location);
		world.spawnParticle(Particle.FLAME, location, 40, 0.5, 0.4, 0.5, 0.05);

		COMZombies.scheduleTask(10, () ->
		{
			if(location.getBlock().getType().equals(Material.FIRE))
				BlockUtils.setBlockToAir(location);
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

		int[] trailTask = new int[1];
		trailTask[0] = COMZombies.scheduleTask(0, 3, () ->
		{
			if(wolf.isValid())
				wolf.getWorld().spawnParticle(Particle.FLAME, wolf.getLocation().add(0, 0.4, 0), 3, 0.25, 0.15, 0.25, 0.01);
			else
				Bukkit.getScheduler().cancelTask(trailTask[0]);
		});

		// #125 — removed the blanket 60-second auto-remove. The stuck-zombie teleporter in
		// SpawnManager.checkStuck already handles dogs that can't reach players by teleporting them
		// to a reachable spawn near the target, so dogs no longer need to vanish on a timer. The old
		// behaviour killed dogs that simply hadn't reached a player yet, making dog rounds trivial.
		return wolf;
	}
}
