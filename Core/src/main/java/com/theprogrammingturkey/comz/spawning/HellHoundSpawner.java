package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.ConfigSetup;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.SoundConfig;
import com.theprogrammingturkey.comz.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HellHoundSpawner extends RoundSpawner
{
	public static final String DOG_META = "comz_hellhound";
	public static final String DOG_TARGET_META = "comz_hellhound_target";

	private static final float BO_ROUND_ONE_ZOMBIE_HEALTH = 150f;
	private static final int SPOT_ATTEMPTS = 24;
	private static final double MIN_BARRIER_DISTANCE = 3.0;

	private static final Map<Game, Integer> fogTasks = new HashMap<>();

	@Override
	public Mob spawnEntity(Game game, SpawnPoint loc, int wave)
	{
		Player target = leastTargetedPlayer(game, null);
		Location location = target != null ? findSpotNear(game, target) : null;
		if(location == null)
			location = loc.getLocation().clone().add(0.5, 0, 0.5);
		World world = location.getWorld();
		if(world == null)
			return null;

		world.strikeLightningEffect(location);
		SoundUtil.playKey(world, location, "round.dogSpawn", "", SoundCategory.HOSTILE, 1.0f, 1.0f);

		Wolf wolf = (Wolf) world.spawnEntity(location, EntityType.WOLF);
		wolf.setMetadata(DOG_META, new FixedMetadataValue(COMZombies.getPlugin(), true));
		if(target != null)
			assign(wolf, target);
		wolf.setFireTicks(99999999);
		wolf.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 1, true));
		// #125 — make the wolf actively hostile so it actually path-finds to and attacks players.
		wolf.setAngry(true);
		setFollowDistance(wolf, 512);
		ZombieHealth.applyHealth(wolf, dogHealth(game.spawnManager.getDogRoundNumber()));
		setSpeed(wolf, 1.15f);

		// The hound must count as spawned right away (the round spawner tallies it), so it waits out the
		// lightning strike invisible and frozen instead of being spawned later.
		int delay = ConfigManager.getMainConfig().dogSpawnDelayTicks;
		if(delay > 0)
		{
			wolf.setAI(false);
			wolf.setSilent(true);
			wolf.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, delay + 5, 0, false, false));
			COMZombies.scheduleTask(delay, () -> arrive(game, wolf));
		}
		else
		{
			arrive(game, wolf);
		}

		int[] trailTask = new int[1];
		trailTask[0] = COMZombies.scheduleTask(0, 3, () ->
		{
			if(!wolf.isValid())
				Bukkit.getScheduler().cancelTask(trailTask[0]);
			else if(wolf.hasAI())
				wolf.getWorld().spawnParticle(Particle.FLAME, wolf.getLocation().add(0, 0.4, 0), 3, 0.25, 0.15, 0.25, 0.01);
		});

		return wolf;
	}

	private static void arrive(Game game, Wolf wolf)
	{
		if(!wolf.isValid())
			return;
		wolf.removePotionEffect(PotionEffectType.INVISIBILITY);
		wolf.setAI(true);
		wolf.setSilent(false);
		wolf.setAngry(true);
		Location at = wolf.getLocation();
		wolf.getWorld().spawnParticle(Particle.FLAME, at.clone().add(0, 0.5, 0), 40, 0.5, 0.4, 0.5, 0.05);
		wolf.getWorld().spawnParticle(Compat.SMOKE, at.clone().add(0, 0.5, 0), 20, 0.4, 0.3, 0.4, 0.02);
		SoundUtil.play(wolf.getWorld(), at, "ENTITY_WOLF_GROWL", SoundCategory.HOSTILE, 1.0f, 0.7f);
		Player target = assignedTarget(game, wolf);
		if(target != null)
			wolf.setTarget(target);
	}

	/** BO hound health for the given dog round of the game (1-based), on the zombie health scale. */
	public static double dogHealth(int dogRound)
	{
		List<Integer> table = ConfigManager.getMainConfig().dogHealthByDogRound;
		int bo = table.get(Math.max(0, Math.min(table.size() - 1, dogRound - 1)));
		return bo * (zombieHealth(1) / BO_ROUND_ONE_ZOMBIE_HEALTH);
	}

	public static boolean isDog(Entity entity)
	{
		if(!(entity instanceof Wolf) || !entity.hasMetadata(DOG_META))
			return false;
		for(MetadataValue mv : entity.getMetadata(DOG_META))
			if(mv.getOwningPlugin() == COMZombies.getPlugin() && mv.asBoolean())
				return true;
		return false;
	}

	// --- targeting ---------------------------------------------------------------------------------------------

	/** The player this hound hunts; a downed or departed target is swapped for the least-hunted living player. */
	public static Player assignedTarget(Game game, Mob dog)
	{
		Player current = storedTarget(dog);
		if(current != null && isHuntable(game, current) && current.getWorld() == dog.getWorld())
			return current;
		Player next = leastTargetedPlayer(game, dog);
		if(next != null)
			assign(dog, next);
		return next;
	}

	private static Player storedTarget(Mob dog)
	{
		for(MetadataValue mv : dog.getMetadata(DOG_TARGET_META))
		{
			if(mv.getOwningPlugin() != COMZombies.getPlugin())
				continue;
			try
			{
				return Bukkit.getPlayer(UUID.fromString(mv.asString()));
			} catch(IllegalArgumentException e)
			{
				return null;
			}
		}
		return null;
	}

	private static void assign(Mob dog, Player target)
	{
		dog.setMetadata(DOG_TARGET_META, new FixedMetadataValue(COMZombies.getPlugin(), target.getUniqueId().toString()));
	}

	private static boolean isHuntable(Game game, Player player)
	{
		return game.getLivingPlayers().contains(player) && !game.downedPlayerManager.isDownedPlayer(player) && !game.downedPlayerManager.isGhost(player);
	}

	private static Player leastTargetedPlayer(Game game, Mob exclude)
	{
		Map<Player, Integer> hunted = new HashMap<>();
		for(Player p : game.getLivingPlayers())
			if(isHuntable(game, p))
				hunted.put(p, 0);
		if(hunted.isEmpty())
			return null;
		for(Mob mob : game.spawnManager.getEntities())
		{
			if(mob == exclude || mob.isDead() || !isDog(mob))
				continue;
			Player t = storedTarget(mob);
			if(t != null && hunted.containsKey(t))
				hunted.put(t, hunted.get(t) + 1);
		}
		int fewest = Integer.MAX_VALUE;
		List<Player> best = new ArrayList<>();
		for(Map.Entry<Player, Integer> e : hunted.entrySet())
		{
			if(e.getValue() < fewest)
			{
				fewest = e.getValue();
				best.clear();
			}
			if(e.getValue() == fewest)
				best.add(e.getKey());
		}
		return best.get(COMZombies.rand.nextInt(best.size()));
	}

	// --- spawn spot --------------------------------------------------------------------------------------------

	/** A standable block a few blocks from the player, in the player's sight, inside the arena and away from windows. */
	static Location findSpotNear(Game game, Player player)
	{
		ConfigSetup cfg = ConfigManager.getMainConfig();
		Location base = player.getLocation();
		World world = base.getWorld();
		if(world == null)
			return null;
		for(int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++)
		{
			double angle = COMZombies.rand.nextDouble() * Math.PI * 2;
			double dist = cfg.dogSpawnRadiusMin + COMZombies.rand.nextDouble() * (cfg.dogSpawnRadiusMax - cfg.dogSpawnRadiusMin);
			int x = (int) Math.floor(base.getX() + Math.cos(angle) * dist);
			int z = (int) Math.floor(base.getZ() + Math.sin(angle) * dist);
			for(int dy = 2; dy >= -3; dy--)
			{
				Block feet = world.getBlockAt(x, base.getBlockY() + dy, z);
				if(isStandableSpot(feet) && isFairSpot(game, player, feet.getLocation()))
					return feet.getLocation().add(0.5, 0, 0.5);
			}
		}
		return null;
	}

	public static boolean isStandableSpot(Block feet)
	{
		Block below = feet.getRelative(0, -1, 0);
		Block head = feet.getRelative(0, 1, 0);
		return feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid() && below.getType().isSolid();
	}

	private static boolean isFairSpot(Game game, Player player, Location spot)
	{
		if(game.arena != null && game.arena.areMinAndMaxSet() && !game.arena.containsBlock(spot))
			return false;
		for(Barrier barrier : game.barrierManager.getBarriers())
			for(Block b : barrier.getBlocks())
				if(b.getWorld() == spot.getWorld() && b.getLocation().distanceSquared(spot) < MIN_BARRIER_DISTANCE * MIN_BARRIER_DISTANCE)
					return false;
		Location eye = player.getEyeLocation();
		Vector toSpot = spot.clone().add(0.5, 0.6, 0.5).toVector().subtract(eye.toVector());
		double length = toSpot.length();
		if(length < 0.1)
			return false;
		RayTraceResult hit = spot.getWorld().rayTraceBlocks(eye, toSpot.normalize(), length, FluidCollisionMode.NEVER, true);
		return hit == null || hit.getHitBlock() == null;
	}

	// --- death -------------------------------------------------------------------------------------------------

	/** Hounds burst into flame when they die, singeing players standing right next to them. */
	public static void explode(Game game, Entity dog)
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
			return;
		ConfigSetup cfg = ConfigManager.getMainConfig();
		Location centre = dog.getLocation().add(0, 0.4, 0);
		World world = centre.getWorld();
		if(world == null)
			return;
		world.spawnParticle(Particle.FLAME, centre, 60, 0.6, 0.4, 0.6, 0.08);
		world.spawnParticle(Particle.LAVA, centre, 8, 0.4, 0.2, 0.4, 0);
		world.spawnParticle(Compat.SMOKE, centre, 20, 0.4, 0.3, 0.4, 0.03);
		SoundUtil.play(world, centre, "ENTITY_GENERIC_EXPLODE", SoundCategory.HOSTILE, 0.7f, 1.5f);
		SoundUtil.play(world, centre, "ENTITY_BLAZE_SHOOT", SoundCategory.HOSTILE, 1.0f, 0.8f);
		if(cfg.dogExplosionDamage <= 0 || cfg.dogExplosionRadius <= 0)
			return;
		double radiusSq = cfg.dogExplosionRadius * cfg.dogExplosionRadius;
		for(Player p : game.getLivingPlayers())
		{
			if(p.getWorld() != world || p.getLocation().distanceSquared(centre) > radiusSq)
				continue;
			if(game.downedPlayerManager.isDownedPlayer(p) || game.downedPlayerManager.isGhost(p) || game.perkManager.hasPerk(p, PerkType.PHD_FLOPPER))
				continue;
			p.damage(cfg.dogExplosionDamage);
		}
	}

	// --- round atmosphere --------------------------------------------------------------------------------------

	/** Dog round start for one player: the announcer stinger, thunder, a title and a short darkening. */
	public static void announceDogRound(Game game, Player player, int wave)
	{
		float volume = ConfigManager.getMainConfig().roundSoundVolume;
		SoundUtil.play(player, player.getLocation(), SoundConfig.get("round.dogs", "ENTITY_ENDER_DRAGON_GROWL"), SoundCategory.MASTER, volume, 1);
		SoundUtil.play(player, player.getLocation(), "ENTITY_LIGHTNING_BOLT_THUNDER", SoundCategory.MASTER, volume * 0.8f, 0.7f);
		player.sendTitle(ChatColor.WHITE + "Round " + wave, ChatColor.RED + "Fetch me their souls!", 5, 40, 10);
		if(Compat.DARKNESS != null)
			player.addPotionEffect(new PotionEffect(Compat.DARKNESS, 80, 0, false, false, false));
		startFog(game);
	}

	private static void startFog(Game game)
	{
		if(!ConfigManager.getMainConfig().dogRoundFog || fogTasks.containsKey(game))
			return;
		int[] task = new int[1];
		task[0] = COMZombies.scheduleTask(0, 10, () ->
		{
			if(game.getStatus() != Game.GameStatus.INGAME || !game.spawnManager.isDogRound())
			{
				Bukkit.getScheduler().cancelTask(task[0]);
				fogTasks.remove(game);
				return;
			}
			for(Player p : game.getPlayersAndSpectators())
			{
				Location at = p.getLocation().add(0, 1, 0);
				p.spawnParticle(Particle.CLOUD, at, 12, 6, 1.5, 6, 0.0);
				p.spawnParticle(Compat.SMOKE, at, 30, 7, 1.2, 7, 0.0);
			}
		});
		if(task[0] != -1)
			fogTasks.put(game, task[0]);
	}
}
