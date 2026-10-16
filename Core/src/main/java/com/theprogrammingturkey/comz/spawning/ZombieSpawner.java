package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.Compat;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Zombie;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ZombieSpawner extends RoundSpawner
{
	/** Bukkit metadata key marking a zombie that has been turned into a slow gas crawler. */
	public static final String CRAWLER_META = "comz_crawler";

	private static final String BASE_SPEED_META = "comz_base_speed";

	/** Marks a Nova-style crawler: the only kind that leaves a toxic gas cloud behind. */
	public static final String GAS_CRAWLER_META = "comz_gas_crawler";

	/** How often (ticks) a crawler emits its gas / re-applies the brief poison cloud. */
	private static final long GAS_PERIOD_TICKS = 40L;

	/** How often (ticks) the crawler upkeep tick runs (re-asserts the crawl pose, drives gas). */
	private static final long CRAWLER_TICK_TICKS = 4L;

	/** Crawl-pose re-asserts per gas emission ({@code GAS_PERIOD_TICKS / CRAWLER_TICK_TICKS}). */
	private static final int TICKS_PER_GAS = (int) (GAS_PERIOD_TICKS / CRAWLER_TICK_TICKS);

	private static final RoundSpawner SPEED_HELPER = new ZombieSpawner();

	/**
	 * Returns true if the given entity has been converted into a crawler.
	 */
	public static boolean isCrawler(Mob mob)
	{
		if(mob == null || !mob.hasMetadata(CRAWLER_META))
			return false;
		for(MetadataValue mv : mob.getMetadata(CRAWLER_META))
			if(mv.getOwningPlugin() == COMZombies.getPlugin() && mv.asBoolean())
				return true;
		return false;
	}

	/**
	 * Tier 2 — Converts a live zombie into a "crawler": low health, very slow movement
	 * (heavy SLOWNESS + reduced speed attribute) and periodically emits a toxic gas that
	 * briefly poisons nearby players. The entity is tagged with {@link #CRAWLER_META} so it
	 * can be identified later (e.g. to exempt it from Insta-Kill). Safe to call once per mob;
	 * a no-op if the mob is null, dead or already a crawler.
	 */
	public static void convertToCrawler(Game game, Mob mob)
	{
		if(mob == null || mob.isDead() || isCrawler(mob))
			return;

		mob.setMetadata(CRAWLER_META, new FixedMetadataValue(COMZombies.getPlugin(), true));

		// BO crawlers keep whatever health they had left; crawlerHealth > 0 is an optional ceiling.
		double crawlerHealth = ConfigManager.getMainConfig().crawlerHealth;
		if(crawlerHealth > 0 && ZombieHealth.virtualHealth(mob) > crawlerHealth)
			ZombieHealth.applyHealth(mob, crawlerHealth, crawlerHealth);
		if(COMZombies.rand.nextDouble() < ConfigManager.getMainConfig().crawlerGasChance)
			mob.setMetadata(GAS_CRAWLER_META, new FixedMetadataValue(COMZombies.getPlugin(), true));

		// Slow, but still able to crawl toward players. The crawler speed is driven solely by the
		// configurable crawlerSpeedMultiplier (default 0.35) and multiplies the zombie's real base
		// speed (~0.23) — no Slowness potion is stacked on top. That lands the crawler at ~0.08,
		// a genuine slow crawl. (An earlier attempt used getDefaultValue() inside setSpeed, which
		// read the generic 0.7 attribute default and made crawlers ~3x too fast.)
		SPEED_HELPER.setSpeed(mob, (float) ConfigManager.getMainConfig().crawlerSpeedMultiplier);

		// Read as a small ground-hugging crawler: shrink the model + hitbox (~0.6) and bump jump
		// strength so the shorter body can still clear a one-block spawn lip instead of stalling.
		SPEED_HELPER.setScale(mob, 0.6d);
		SPEED_HELPER.setJumpStrength(mob, 0.6d);
		applyCrawlPose(mob);

		scheduleCrawlerTick(game, mob, 0);
	}

	public static boolean isGasCrawler(Mob mob)
	{
		for(MetadataValue mv : mob.getMetadata(GAS_CRAWLER_META))
			if(mv.getOwningPlugin() == COMZombies.getPlugin() && mv.asBoolean())
				return true;
		return false;
	}

	/**
	 * Forces the swimming/crawl pose so the zombie visibly crawls along the ground. Prefers Paper's
	 * fixed-pose API ({@code setPose(Pose, true)}) when present (no per-tick flicker); otherwise
	 * falls back to the cross-platform swimming flag, which the upkeep tick re-asserts.
	 */
	private static void applyCrawlPose(Mob mob)
	{
		try
		{
			mob.getClass().getMethod("setPose", Pose.class, boolean.class).invoke(mob, Pose.SWIMMING, true);
		}
		catch(ReflectiveOperationException | RuntimeException ignored)
		{
			mob.setSwimming(true);
		}
	}

	/**
	 * Single guarded upkeep task for a crawler: re-asserts the crawl pose every
	 * {@link #CRAWLER_TICK_TICKS} and emits the poison gas every {@link #GAS_PERIOD_TICKS}. It
	 * self-terminates (no reschedule) the moment the mob dies or leaves the game, so it can never
	 * leak past the crawler's lifetime or the game's end.
	 */
	private static void scheduleCrawlerTick(Game game, Mob mob, int tick)
	{
		COMZombies.scheduleTask(CRAWLER_TICK_TICKS, () ->
		{
			if(mob.isDead() || !game.spawnManager.isEntitySpawned(mob))
				return;

			applyCrawlPose(mob);
			if(tick % TICKS_PER_GAS == 0 && isGasCrawler(mob))
				emitGas(game, mob);

			scheduleCrawlerTick(game, mob, tick + 1);
		});
	}

	/**
	 * Spawns a poison-cloud particle puff at the crawler and applies a brief poison to any
	 * in-game player within {@code crawlerGasRadius} blocks.
	 */
	private static void emitGas(Game game, Mob mob)
	{
		Location loc = mob.getLocation();
		if(loc.getWorld() != null)
			loc.getWorld().spawnParticle(Compat.ITEM_SLIME, loc.clone().add(0, 0.5, 0), 12, 0.4, 0.3, 0.4, 0.01);

		double radius = ConfigManager.getMainConfig().crawlerGasRadius;
		double radiusSq = radius * radius;
		for(Player pl : game.getPlayersInGame())
		{
			if(game.downedPlayerManager.isDownedPlayer(pl))
				continue;
			if(pl.getWorld() == loc.getWorld() && pl.getLocation().distanceSquared(loc) <= radiusSq)
				pl.addPotionEffect(new PotionEffect(PotionEffectType.POISON, (int) (GAS_PERIOD_TICKS + 20), 0, true));
		}
	}

	/**
	 * BO1 move speed roll: a zombie rolls in {@code [round * perRound, round * perRound + spread)};
	 * up to the walk threshold it walks, up to the run threshold it runs, above that it sprints.
	 * With the defaults round 1 is ~80% walkers and every zombie sprints from round 9.
	 */
	static float speedMultiplier(int wave)
	{
		com.theprogrammingturkey.comz.config.ConfigSetup cfg = ConfigManager.getMainConfig();
		int spread = Math.max(1, cfg.zombieMoveSpeedSpread);
		int roll = Math.max(0, wave) * cfg.zombieMoveSpeedPerRound + COMZombies.rand.nextInt(spread);
		if(roll > cfg.zombieRunThreshold)
			return (float) cfg.zombieSprinterSpeed;
		if(roll > cfg.zombieWalkThreshold)
			return (float) cfg.zombieRunnerSpeed;
		return 1f;
	}

	/** Bumps the last zombie of a round to sprinter speed; a no-op on crawlers or a mob already that fast. */
	public static void makeSprinter(Mob mob)
	{
		if(mob == null || mob.isDead() || isCrawler(mob))
			return;
		AttributeInstance attr = mob.getAttribute(Compat.MOVEMENT_SPEED);
		if(attr == null)
			return;
		double base = attr.getDefaultValue();
		for(MetadataValue mv : mob.getMetadata(BASE_SPEED_META))
			if(mv.getOwningPlugin() == COMZombies.getPlugin())
				base = mv.asDouble();
		double sprint = base * ConfigManager.getMainConfig().zombieSprinterSpeed;
		if(attr.getBaseValue() < sprint)
			attr.setBaseValue(sprint);
	}

	@Override
	public Mob spawnEntity(Game game, SpawnPoint loc, int wave)
	{
		if(loc.getLocation().getWorld() == null)
			return null;
		Location location = new Location(loc.getLocation().getWorld(), loc.getLocation().getBlockX(), loc.getLocation().getBlockY(), loc.getLocation().getBlockZ());
		location.add(0.5, 0, 0.5);
		Zombie zomb = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
		if(game.barrierManager.getBarrier(loc) == null)
		{
			com.theprogrammingturkey.comz.util.ParticleFX.groundBreak(location.getWorld(), location);
			com.theprogrammingturkey.comz.util.SoundUtil.playKey(location.getWorld(), location, "zombie.spawn", "BLOCK_GRAVEL_BREAK", org.bukkit.SoundCategory.HOSTILE, 0.8F, 1.0F);
		}
		COMZombies.scheduleTask(10, () ->
		{
			if(zomb.getEquipment() != null)
				zomb.getEquipment().clear();
		});
		zomb.setBaby(false);
		setFollowDistance(zomb, 512);

		ZombieHealth.applyHealth(zomb, zombieHealth(wave));

		AttributeInstance speedAttr = zomb.getAttribute(Compat.MOVEMENT_SPEED);
		if(speedAttr != null)
			zomb.setMetadata(BASE_SPEED_META, new FixedMetadataValue(COMZombies.getPlugin(), speedAttr.getValue()));
		double variation = ConfigManager.getMainConfig().zombieSpeedVariation;
		float speed = speedMultiplier(wave) * (float) (1 + variation * (COMZombies.rand.nextDouble() * 2 - 1));
		if(speed != 1f)
			setSpeed(zomb, speed);

		// #130/#96 — barrier breaking is now proximity-driven (BarrierManager.tickBarriers checks
		// which zombies are near each barrier), so we no longer tie a barrier to the specific zombie
		// that spawned at a linked point. The old initBarrier(zomb) here caused barriers to only break
		// when their "linked" zombie was alive, leaving adjacent barriers untouched.

		return zomb;
	}
}
