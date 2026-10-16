package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.spawning.special.SpecialEnemyCatalogue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.SoundUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tier 4 — boss-round spawner for the two signature heavy zombies:
 *
 * <ul>
 *   <li><b>George Romero</b> (BO1, "Call of the Dead") — a very tanky, slow, glowing zombie
 *       that relentlessly chases the nearest player. On death it drops a guaranteed Max Ammo
 *       plus a Random Perk, mirroring his "drop your weapon as a reward" payoff. APPROXIMATION:
 *       the canonical George cannot be permanently killed (only stunned with electricity); here
 *       he is killable so a boss round can end. He is a re-skinned Zombie with a retarget task.</li>
 *   <li><b>Brutus</b> (BO2, "Mob of the Dead") — an armored prison-guard boss. Modeled as a
 *       {@code PIGLIN_BRUTE} (armored, brute-class mob the API ships) with very high HP. His
 *       signature ability is disabling perks: when he damages a nearby player, one of that
 *       player's perks is temporarily removed and restored after {@code brutusDisableSeconds}.
 *       APPROXIMATION: the disable is implemented as a full remove+restore of one random perk
 *       (reversible, simple) rather than a greyed-out icon, and the trigger is wired through the
 *       existing entity-damage listener via the {@link #BRUTUS_META} marker.</li>
 * </ul>
 *
 * Bosses are added to the normal {@code mobs} list so the round ends through the usual
 * {@code removeEntity} path. A boss that cannot reach a player is handled by the shared stuck-mob
 * teleporter in {@link SpawnManager} rather than despawned on a timer, since any despawn of the
 * last boss would pay out the boss rewards without a kill.
 */
public class BossSpawner extends RoundSpawner
{
	/** Marks a spawned Brutus so the damage listener can fire its perk-disable ability. */
	public static final String BRUTUS_META = "comz_brutus";
	public static final String BOSS_META = "comz_boss";
	private static final double MIN_BARRIER_DISTANCE = 2.5;
	private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	public enum BossType
	{
		GEORGE,
		BRUTUS
	}

	/**
	 * Tier 4 — boss max health on the plugin's compressed HP scale: the normal zombie curve for
	 * the round multiplied by {@code mult}, clamped so a boss is never weaker than a normal zombie
	 * of the same round (a misconfigured multiplier &lt; 1 still yields a real "boss").
	 */
	public static float bossHealth(int wave, double mult)
	{
		float base = zombieHealth(wave);
		float boss = (float) (base * mult);
		return Math.max(base, boss);
	}

	/**
	 * Tier 4 — deterministic boss selection. Boss rounds alternate George / Brutus by their
	 * ordinal index ({@code wave / every}): the first boss round is George, the second Brutus,
	 * and so on. Documented and pure so it can be unit-tested without Bukkit.
	 *
	 * @param wave  current round
	 * @param every boss-round cadence (assumed &gt; 0 when this is reached)
	 * @return which boss spawns this round
	 */
	public static BossType bossForRound(int wave, int every)
	{
		int index = every <= 0 ? wave : wave / every;
		return (index % 2 == 1) ? BossType.GEORGE : BossType.BRUTUS;
	}

	/** Convenience alias kept for symmetry with the test naming; returns {@link #bossForRound}. */
	static BossType bossCountForRound(int wave)
	{
		return bossForRound(wave, 1);
	}

	@Override
	public Mob spawnEntity(Game game, SpawnPoint loc, int wave)
	{
		World world = loc.getLocation().getWorld();
		if(world == null)
			return null;

		Location location = spawnLocation(game, loc.getLocation());

		BossType type = bossForRound(wave, Math.max(1, ConfigManager.getMainConfig().bossRoundEveryX));
		double mult = ConfigManager.getMainConfig().bossHealthMultiplier;
		float strength = bossHealth(wave, mult);

		Mob boss = (type == BossType.GEORGE) ? spawnGeorge(world, location, strength) : spawnBrutus(world, location, strength);
		if(boss == null)
			return null;
		markBoss(boss);
		fitThroughDoorways(boss, 1.0);

		setFollowDistance(boss, 512);
		ZombieHealth.applyHealth(boss, strength);

		Player nearest = nearestPlayer(game, boss);
		if(nearest != null)
			boss.setTarget(nearest);

		// Relentless retarget: keep chasing the nearest living in-game player.
		final int[] retargetTaskId = new int[1];
		retargetTaskId[0] = COMZombies.scheduleTask(40L, 40L, () ->
		{
			if(boss.isDead() || !game.spawnManager.isEntitySpawned(boss))
			{
				Bukkit.getScheduler().cancelTask(retargetTaskId[0]);
				return;
			}
			Player p = nearestPlayer(game, boss);
			if(p != null)
				boss.setTarget(p);
		});

		// Tier 4 — boss presence: stinger + a tracked health BossBar for the fight, gated by config.
		spawnBossBar(game, boss, strength, type);

		if(type == BossType.BRUTUS)
			startMachineLocking(game, boss);

		return boss;
	}

	/**
	 * Tier 4 — boss presence visuals. When enabled via {@code visualsBossBar}: plays a vanilla
	 * WITHER spawn stinger to every in-game player and shows a red SEGMENTED_10 BossBar that tracks
	 * the boss's remaining health. A short repeating task updates the bar and tears it down (removes
	 * all players + self-cancels) once the boss is dead or no longer spawned. The bar/task are kept
	 * locally and cleaned up by the same task, so a boss fight leaves no leaked UI behind.
	 */
	private void spawnBossBar(Game game, Mob boss, float maxHealth, BossType type)
	{
		if(!ConfigManager.getMainConfig().visualsBossBar)
			return;

		World world = boss.getWorld();
		Location loc = boss.getLocation();

		// (a) Boss stinger to everyone in the game; without the pack it falls back to the vanilla wither spawn.
		for(Player p : game.getPlayersInGame())
			SoundUtil.playKey(p, loc, type == BossType.GEORGE ? "boss.george.spawn" : "boss.brutus.spawn", "ENTITY_WITHER_SPAWN", SoundCategory.HOSTILE, 1.0f, 0.8f);

		// (b) Health BossBar; max health may have been clamped by the attribute, so re-read it.
		AttributeInstance maxAttr = boss.getAttribute(Compat.MAX_HEALTH);
		final double max = (maxAttr != null) ? maxAttr.getBaseValue() : maxHealth;
		String title = (type == BossType.GEORGE) ? "George Romero" : "Brutus";
		final BossBar bar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SEGMENTED_10);
		for(Player p : game.getPlayersInGame())
			bar.addPlayer(p);

		// Holder so the repeating task can cancel itself once the boss is gone.
		final int[] taskId = new int[1];
		taskId[0] = COMZombies.scheduleTask(0L, 10L, () ->
		{
			if(boss.isDead() || !game.spawnManager.isEntitySpawned(boss))
			{
				bar.removeAll();
				Bukkit.getScheduler().cancelTask(taskId[0]);
				return;
			}
			double progress = (max > 0) ? (boss.getHealth() / max) : 0.0;
			// Clamp into [0,1]: BossBar#setProgress rejects out-of-range values.
			bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
		});
	}

	private Mob spawnGeorge(World world, Location location, float strength)
	{
		Mob george = (Mob) world.spawnEntity(location, EntityType.ZOMBIE);
		george.setCustomName("George Romero");
		george.setCustomNameVisible(true);
		george.setGlowing(true);
		// Slow, lumbering bruiser.
		setSpeed(george, 0.7f);
		return george;
	}

	private Mob spawnBrutus(World world, Location location, float strength)
	{
		Mob brutus;
		try
		{
			if(Compat.PIGLIN_BRUTE == null)
				throw new IllegalStateException("No piglin brutes before 1.16.2");
			brutus = (Mob) world.spawnEntity(location, Compat.PIGLIN_BRUTE);
		}
		catch(Throwable t)
		{
			// Fallback: an armored zombie if PIGLIN_BRUTE is unavailable on this server build.
			brutus = (Mob) world.spawnEntity(location, EntityType.ZOMBIE);
			if(brutus.getEquipment() != null)
			{
				brutus.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_HELMET));
				brutus.getEquipment().setChestplate(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_CHESTPLATE));
			}
		}
		// Piglin brutes zombify after ~15s outside the Nether, which would silently swap Brutus
		// for an untracked zombified piglin.
		Compat.invokeIfPresent(brutus, "setImmuneToZombification", new Class<?>[]{boolean.class}, true);
		brutus.setCustomName("Brutus");
		brutus.setCustomNameVisible(true);
		brutus.setMetadata(BRUTUS_META, new FixedMetadataValue(COMZombies.getPlugin(), true));
		return brutus;
	}

	/**
	 * Tier 4 — true if the given entity is a Brutus boss (used by the damage listener to fire the
	 * perk-disable ability). Mirrors {@code ZombieSpawner.isCrawler} metadata-check style.
	 */
	/** George, Brutus or a catalogue special with the boss behaviour: immune to Insta-Kill, nukes, crawling and the Gersch. */
	public static boolean isBoss(Entity entity)
	{
		if(entity == null || !entity.hasMetadata(BOSS_META))
			return false;
		for(MetadataValue mv : entity.getMetadata(BOSS_META))
			if(mv.getOwningPlugin() == COMZombies.getPlugin() && mv.asBoolean())
				return true;
		return false;
	}

	public static void markBoss(Entity entity)
	{
		entity.setMetadata(BOSS_META, new FixedMetadataValue(COMZombies.getPlugin(), true));
	}

	/**
	 * Scales the boss to {@code wantedScale}, capped so its collision box fits a 1x2 doorway (catalogue
	 * {@code boss.max_height} / {@code max_width}). Without the scale attribute (before 1.20.5) nothing can be
	 * resized, so it returns whether the mob's own box already fits.
	 */
	public static boolean fitThroughDoorways(Mob mob, double wantedScale)
	{
		SpecialEnemyCatalogue.BossRules rules = SpecialEnemyCatalogue.bossRules();
		AttributeInstance attr = Compat.SCALE == null ? null : mob.getAttribute(Compat.SCALE);
		double current = attr == null || attr.getValue() <= 0 ? 1.0 : attr.getValue();
		double height = mob.getHeight() / current;
		double width = mob.getWidth() / current;
		double cap = Math.min(height > 0 ? rules.maxHeight / height : wantedScale, width > 0 ? rules.maxWidth / width : wantedScale);
		if(attr == null)
			return height <= rules.maxHeight + 1.0E-3 && width <= rules.maxWidth + 1.0E-3;
		attr.setBaseValue(Math.max(0.1, Math.min(wantedScale, cap)));
		return true;
	}

	/** {@link #safeSpawnLocation} or, when the map offers nothing better, the centre of {@code preferred}'s block. */
	public static Location spawnLocation(Game game, Location preferred)
	{
		Location safe = safeSpawnLocation(game, preferred);
		return safe != null ? safe : preferred.getBlock().getLocation().add(0.5, 0, 0.5);
	}

	/**
	 * Where a boss may enter: a ground spawn not linked to a window, clear of barriers, with headroom and space
	 * around it, a few blocks from the players, preferring the active zones and the spawns nearest a random
	 * player. Falls back to an open spot near a player; null when there is none.
	 */
	public static Location safeSpawnLocation(Game game, Location preferred)
	{
		SpecialEnemyCatalogue.BossRules rules = SpecialEnemyCatalogue.bossRules();
		List<Player> players = livingPlayers(game);
		List<SpawnPoint> open = new ArrayList<>();
		for(SpawnPoint point : game.spawnManager.getPoints())
			if(isBossSpawn(game, point, players, rules))
				open.add(point);
		List<SpawnPoint> active = game.spawnManager.activeSpawnPoints(open);
		List<SpawnPoint> pool = active.isEmpty() ? open : active;
		if(!pool.isEmpty())
		{
			Location anchor = !players.isEmpty() ? players.get(COMZombies.rand.nextInt(players.size())).getLocation()
					: preferred != null ? preferred : pool.get(0).getLocation();
			List<SpawnPoint> nearest = SpawnManager.nearestPoints(pool, anchor, 3);
			return nearest.get(COMZombies.rand.nextInt(nearest.size())).getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
		}
		Collections.shuffle(players, COMZombies.rand);
		for(Player player : players)
		{
			Location spot = HellHoundSpawner.findSpotNear(game, player);
			if(spot != null && isOpenSpot(spot.getBlock(), rules.minOpenNeighbours) && !game.zoneManager.pushesMobsAt(spot))
				return spot;
		}
		return null;
	}

	private static boolean isBossSpawn(Game game, SpawnPoint point, List<Player> players, SpecialEnemyCatalogue.BossRules rules)
	{
		Location loc = point.getLocation();
		if(loc.getWorld() == null || !game.spawnManager.canSpawnPoint(point) || game.barrierManager.getBarrier(point) != null)
			return false;
		Block feet = loc.getBlock();
		if(!isOpenSpot(feet, rules.minOpenNeighbours))
			return false;
		Location centre = feet.getLocation().add(0.5, 0, 0.5);
		if(game.zoneManager.pushesMobsAt(centre))
			return false;
		double minSq = rules.minPlayerDistance * rules.minPlayerDistance;
		for(Player player : players)
			if(player.getWorld() == loc.getWorld() && player.getLocation().distanceSquared(centre) < minSq)
				return false;
		double barrierSq = MIN_BARRIER_DISTANCE * MIN_BARRIER_DISTANCE;
		for(Barrier barrier : game.barrierManager.getBarriers())
			for(Block b : barrier.getBlocks())
				if(b.getWorld() == loc.getWorld() && b.getLocation().add(0.5, 0, 0.5).distanceSquared(centre) < barrierSq)
					return false;
		return true;
	}

	/** Standable with two blocks of headroom and at least {@code minOpen} walkable blocks around it, so not boxed in. */
	public static boolean isOpenSpot(Block feet, int minOpen)
	{
		if(!HellHoundSpawner.isStandableSpot(feet))
			return false;
		int open = 0;
		for(int[] d : NEIGHBOURS)
		{
			Block side = feet.getRelative(d[0], 0, d[1]);
			Block sideHead = side.getRelative(0, 1, 0);
			if(side.isPassable() && !side.isLiquid() && sideHead.isPassable() && !sideHead.isLiquid() && ++open >= minOpen)
				return true;
		}
		return open >= minOpen;
	}

	private static List<Player> livingPlayers(Game game)
	{
		List<Player> out = new ArrayList<>();
		for(Player pl : game.getPlayersInGame())
			if(!game.downedPlayerManager.isDownedPlayer(pl))
				out.add(pl);
		return out;
	}

	/** Mob of the Dead: Brutus shuts down the perk machines and mystery boxes he walks past. */
	private void startMachineLocking(Game game, Mob brutus)
	{
		final int[] taskId = new int[1];
		taskId[0] = COMZombies.scheduleTask(20L, 20L, () ->
		{
			if(brutus.isDead() || !game.spawnManager.isEntitySpawned(brutus) || game.getStatus() != Game.GameStatus.INGAME)
			{
				Bukkit.getScheduler().cancelTask(taskId[0]);
				return;
			}
			game.specialEnemyManager.lockMachinesNear(brutus.getLocation(), ConfigManager.getMainConfig().brutusLockRadius, "Brutus");
		});
	}

	public static boolean isBrutus(Mob mob)
	{
		if(mob == null || !mob.hasMetadata(BRUTUS_META))
			return false;
		for(MetadataValue mv : mob.getMetadata(BRUTUS_META))
			if(mv.getOwningPlugin() == COMZombies.getPlugin() && mv.asBoolean())
				return true;
		return false;
	}

	/**
	 * Tier 4 — boss death reward: a guaranteed Max Ammo plus a Random Perk drop, the classic
	 * "kill the boss, get the goods" payoff. Called from the kill path so the round still ends
	 * through the normal {@code removeEntity} flow.
	 */
	public static void dropBossRewards(Game game, Mob boss)
	{
		game.powerUpManager.dropPowerUp(boss, PowerUp.MAX_AMMO);
		game.powerUpManager.dropPowerUp(boss, PowerUp.RANDOM_PERK);
	}

	private Player nearestPlayer(Game game, Mob mob)
	{
		Player closest = null;
		double dist = Double.MAX_VALUE;
		for(Player pl : game.getPlayersInGame())
		{
			if(game.downedPlayerManager.isDownedPlayer(pl))
				continue;
			if(pl.getWorld() != mob.getWorld())
				continue;
			double d = pl.getLocation().distanceSquared(mob.getLocation());
			if(d < dist)
			{
				dist = d;
				closest = pl;
			}
		}
		return closest;
	}
}
