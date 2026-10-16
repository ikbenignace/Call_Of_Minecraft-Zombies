package com.theprogrammingturkey.comz.spawning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.ConfigSetup;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.spawning.special.SpecialEnemyCatalogue;
import com.theprogrammingturkey.comz.spawning.special.SpecialEnemyType;
import com.theprogrammingturkey.comz.spawning.special.SpecialRoundSpawner;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class SpawnManager
{
	private static final Map<RoundSpawnType, RoundSpawner> roundSpawnerMap = new HashMap<>();

	static
	{
		roundSpawnerMap.put(RoundSpawnType.REGULAR, new ZombieSpawner());
		roundSpawnerMap.put(RoundSpawnType.HELL_HOUNDS, new HellHoundSpawner());
		roundSpawnerMap.put(RoundSpawnType.BOSS, new BossSpawner());
	}

	private final Game game;
	private final List<SpawnPoint> points = new ArrayList<>();
	private final List<Mob> mobs = new ArrayList<>();
	/** Specials mixed into a regular round: in the game, but the round may end while they live. */
	private final Set<Mob> nonBlocking = Collections.newSetFromMap(new IdentityHashMap<>());
	private RoundSpawner roundSpawner = new ZombieSpawner();
	private boolean canSpawn = false;
	private double spawnInterval;
	private int nextDogRound = -1;
	private int dogRoundNumber = 0;

	private long spawnDelayTicks()
	{
		return Math.max(1L, Math.round(spawnInterval * 20));
	}
	private final double spawnDelayFactor;
	private final double baseSpawnDelay;
	private int mobsSpawned = 0;
	private int mobsToSpawn = 0;
	private boolean dogRound = false;
	private boolean bossRound = false;
	private SpecialEnemyType specialRoundType = null;
	private int specialBoardCap = 0;

	/**
	 * Task id of the self-rescheduling periodic update() task, or -1 when none is scheduled.
	 * Tracked so {@link #reset()} (called on game end) can cancel the still-pending tick instead
	 * of letting it fire forever against a non-INGAME game.
	 */
	private int updateTaskId = -1;

	/** 0L — stuck-detection state: last sampled location and accumulated no-progress ticks per mob. */
	private final Map<Mob, Location> stuckLastLoc = new HashMap<>();
	private final Map<Mob, Long> stuckTicks = new HashMap<>();
	/** update() reschedules itself every 100 ticks, so that is the stuck-check sampling interval. */
	private static final long STUCK_CHECK_INTERVAL = 100L;

	public SpawnManager(Game game)
	{
		this.game = game;
		baseSpawnDelay = COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.zombieSpawnDelay", 2.0);
		spawnInterval = baseSpawnDelay;
		double factor = COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.zombieSpawnDelayFactor", 1.0526);
		spawnDelayFactor = factor > 0 ? factor : 1.0;
	}

	public void loadAllSpawnsToGame(JsonArray spawnsSaveJson)
	{
		points.clear();

		for(JsonElement spawnElem : spawnsSaveJson)
		{
			if(!spawnElem.isJsonObject())
				continue;
			Location loc = CustomConfig.getLocationWithWorld(spawnElem.getAsJsonObject(), "", game.getWorld());
			String id = CustomConfig.getString(spawnElem.getAsJsonObject(), "id", "MISSING");
			if(loc != null && !id.equals("MISSING"))
				points.add(new SpawnPoint(loc, game, loc.getBlock().getType(), id));
			else
				COMZombies.log.log(Level.WARNING, "Failed to load zombie spawn! ID: " + id + " w/ Loc: " + loc);
		}
	}

	public JsonArray save()
	{
		JsonArray spawnsSaveJson = new JsonArray();
		for(SpawnPoint spawn : points)
		{
			JsonObject spawnJson = CustomConfig.locationToJson(spawn.getLocation());
			spawnJson.addProperty("id", spawn.getID());
			spawnsSaveJson.add(spawnJson);
		}

		return spawnsSaveJson;
	}

	public SpawnPoint getSpawnPoint(String id)
	{
		for(SpawnPoint p : points)
			if(id.equalsIgnoreCase(p.getID()))
				return p;
		return null;
	}

	public SpawnPoint getSpawnPoint(Location loc)
	{
		for(SpawnPoint point : points)
			if(point.getLocation().equals(loc))
				return point;
		return null;
	}

	public void removePoint(SpawnPoint point)
	{
		if(points.contains(point))
		{
			BlockUtils.setBlockToAir(point.getLocation());
			points.remove(point);
		}
		GameManager.INSTANCE.saveAllGames();
	}

	public List<SpawnPoint> getPoints()
	{
		return points;
	}

	public void killMob(Entity entity)
	{
		if(entity instanceof Player)
			return;

		while(!entity.isDead())
			entity.remove();

		this.removeEntity(entity);
	}

	public void nuke()
	{
		for(int i = mobs.size() - 1; i >= 0; i--)
			if(i < mobs.size() && !game.specialEnemyManager.isNukeImmune(mobs.get(i)))
			{
				game.specialEnemyManager.suppressDeathBlast(mobs.get(i));
				killMob(mobs.get(i));
			}
	}

	public void killAll(boolean nextWave)
	{
		for(int i = mobs.size() - 1; i >= 0; i--)
			killMob(this.mobs.get(i));

		if(nextWave)
			game.nextWave();
		mobs.clear();
	}

	/** Developer tooling: drops the rest of the round (alive and unspawned) without drops or points and advances. */
	public void skipRound()
	{
		canSpawn = false;
		dogRound = false;
		bossRound = false;
		specialRoundType = null;
		mobsSpawned = mobsToSpawn;
		killAll(true);
	}

	public List<Mob> getEntities()
	{
		return mobs;
	}

	/** Adds a special spawned outside the round spawner; {@code blocksRound} false lets the round end without it. */
	public void addSpecialMob(Mob mob, boolean blocksRound)
	{
		prepareGameMob(mob);
		mobs.add(mob);
		if(!blocksRound)
			nonBlocking.add(mob);
	}

	/** Removal after a real kill: hounds burst into flames, which nukes, cleanup and despawns must not trigger. */
	public void removeKilledEntity(Entity entity)
	{
		if(HellHoundSpawner.isDog(entity) && mobs.contains(entity))
			HellHoundSpawner.explode(game, entity);
		removeEntity(entity);
	}

	public void removeEntity(Entity entity)
	{
		if(!(entity instanceof Mob))
			return;

		mobs.remove(entity);
		boolean blockedRound = !nonBlocking.remove(entity);
		stuckLastLoc.remove(entity);
		stuckTicks.remove(entity);
		game.specialEnemyManager.onMobRemoved(entity);

		if(blockedRound && getZombiesAlive() == 0 && mobsSpawned >= mobsToSpawn)
		{
			if(dogRound && ConfigManager.getMainConfig().dogRoundMaxAmmoDrop)
				game.powerUpManager.dropPowerUp(entity, PowerUp.MAX_AMMO);
			if(specialRoundType != null)
				game.specialEnemyManager.onSpecialRoundCleared(entity, specialRoundType);
			// Tier 4 — last boss of a boss round drops a guaranteed Max Ammo + Random Perk.
			if(bossRound && entity instanceof Mob)
				BossSpawner.dropBossRewards(game, (Mob) entity);
			game.nextWave();
		}
		else
		{
			maybeConvertLastZombie();
		}

		game.scoreboard.update();
	}

	/**
	 * When the wave is fully spawned and exactly one regular zombie remains, it becomes a crawler
	 * (lastZombieCrawler) or, from lastZombieSprintRound, a sprinter as in BO. Called whenever the
	 * alive count drops and on the periodic update tick.
	 */
	private void maybeConvertLastZombie()
	{
		if(dogRound || bossRound || specialRoundType != null)
			return;
		ConfigSetup cfg = ConfigManager.getMainConfig();
		boolean crawl = cfg.lastZombieCrawler;
		boolean sprint = cfg.lastZombieSprintRound > 0 && game.getWave() >= cfg.lastZombieSprintRound;
		if(!shouldConvertLastZombie(getZombiesAlive(), mobsSpawned, mobsToSpawn, crawl || sprint))
			return;

		Mob last = null;
		for(Mob mob : mobs)
			if(!nonBlocking.contains(mob))
				last = mob;
		if(last == null || ZombieSpawner.isCrawler(last) || BossSpawner.isBoss(last) || game.specialEnemyManager.isSpecial(last))
			return;
		if(crawl)
			ZombieSpawner.convertToCrawler(game, last);
		else if(last instanceof Zombie)
			ZombieSpawner.makeSprinter(last);
	}

	/** Game mobs must never despawn or pick up power-ups, grenades or dropped gear. */
	private static void prepareGameMob(Mob mob)
	{
		mob.setRemoveWhenFarAway(false);
		mob.setCanPickupItems(false);
	}

	public boolean addPoint(SpawnPoint point)
	{
		if(game.getStatus() == GameStatus.DISABLED)
			return points.add(point);
		return false;
	}

	// Finds the closest locations to point loc, it results numToGet amount of
	// spawn points

	public int getTotalSpawns()
	{
		return points.size();
	}

	public Game getGame()
	{
		return game;
	}

	private List<SpawnPoint> getNearestPoints(Location loc, int numToGet)
	{
		return nearestPoints(game.spawnManager.getPoints(), loc, numToGet);
	}

	/**
	 * Returns the {@code numToGet} spawn points closest to {@code loc}, nearest
	 * first. Pure function extracted for testability.
	 */
	public static List<SpawnPoint> nearestPoints(List<SpawnPoint> points, Location loc, int numToGet)
	{
		if(numToGet < 0)
			throw new IllegalArgumentException("numToGet should not be less than zero");
		if(numToGet == 0)
			return new ArrayList<>();

		return points.stream()
				.sorted(Comparator.comparingDouble(point -> point.getLocation().distanceSquared(loc)))
				.limit(numToGet)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * 0b — Maximum zombies allowed on the board at once. Scales with player count:
	 * {@code base + perPlayer * (players - 1)}, clamped so a 0/1 player game uses {@code base}.
	 */
	public static int maxOnBoard(int players, int base, int perPlayer)
	{
		return base + perPlayer * Math.max(0, players - 1);
	}

	/**
	 * BO1/BO2 round zombie count: {@code base + perPlayer * (players - 1) * mult} (solo adds half of
	 * {@code perPlayer * mult}), where {@code mult = max(1, round / 5)} and from round 10 also times
	 * {@code round * roundFactor}. Rounds 1-5 take 25/30/50/70/90% of that. Solo gives
	 * 6, 8, 13, 18, 24 for rounds 1-5; four players get 10 on round 1 and 240 on round 20.
	 */
	public static int boZombieCount(int wave, int players, int base, int perPlayer, double roundFactor, double multiplier)
	{
		int round = Math.max(1, wave);
		double mult = Math.max(1, round / 5.0);
		if(round >= 10)
			mult *= round * roundFactor;
		int max = base;
		if(players <= 1)
			max += (int) (0.5 * perPlayer * mult);
		else
			max += (int) ((players - 1) * perPlayer * mult);
		double[] early = {0.25, 0.3, 0.5, 0.7, 0.9};
		if(round <= early.length)
			max = (int) (max * early[round - 1]);
		return Math.max(1, (int) Math.round(max * multiplier));
	}

	/** Spawn delay for a round: base divided by factor each round after the first, floored at min. */
	public static double spawnDelayForWave(double base, double factor, double min, int wave)
	{
		return Math.max(min, base / Math.pow(factor, Math.max(0, wave - 1)));
	}

	private boolean rollDogRound(int wave, int everyX, ConfigSetup cfg)
	{
		if(everyX <= 0 || wave <= 0)
			return false;
		if(nextDogRound < 0)
		{
			int span = cfg.dogRoundFirstMax - cfg.dogRoundFirstMin + 1;
			nextDogRound = cfg.dogRoundFirstMin + COMZombies.rand.nextInt(Math.max(1, span));
		}
		if(wave > nextDogRound)
			nextDogRound = wave + everyX;
		if(wave != nextDogRound)
			return false;
		nextDogRound = wave + everyX + COMZombies.rand.nextInt(2);
		return true;
	}

	/**
	 * 0g — Number of hell hounds on a dog round: {@code perPlayer * players}, at least 1.
	 */
	public static int dogCount(int players, int perPlayer)
	{
		return Math.max(1, perPlayer * players);
	}

	/**
	 * 0L — Running no-progress tick counter for a mob. Returns 0 the instant the mob makes
	 * progress (moves at least {@code moveThreshold}); otherwise grows the previous counter by
	 * one check interval. The caller teleports the mob once the result reaches the stuck threshold.
	 */
	public static long updateStuckTicks(long prevStuckTicks, double movedDistance, double moveThreshold, long intervalTicks)
	{
		return movedDistance >= moveThreshold ? 0L : prevStuckTicks + intervalTicks;
	}

	/**
	 * Tier 2 — Whether the single remaining zombie of a round should be converted into a slow
	 * crawler so players can hold the round. True only when the feature is enabled, the whole
	 * wave has already been spawned ({@code mobsSpawned >= mobsToSpawn}) and exactly one zombie
	 * is alive. Pure decision extracted for testability.
	 */
	public static boolean shouldConvertLastZombie(int aliveCount, int mobsSpawned, int mobsToSpawn, boolean enabled)
	{
		return enabled && aliveCount == 1 && mobsSpawned >= mobsToSpawn;
	}

	/**
	 * Whether wave {@code wave} should be a dog (hellhound) round, given the configured interval
	 * {@code dogRoundEveryX}. A dog round fires every Nth wave (e.g. N=5 -> waves 5,10,15...). A
	 * value of 0 or negative disables dog rounds. Wave 0 is never a dog round (it is the pre-game
	 * wave). Pure decision extracted for testability.
	 */
	public static boolean isDogRound(int wave, int dogRoundEveryX)
	{
		return dogRoundEveryX > 0 && wave > 0 && wave % dogRoundEveryX == 0;
	}

	/**
	 * Tier 4 — whether {@code wave} is a boss round given the configured cadence. Boss rounds are
	 * disabled entirely when {@code every <= 0}, and round 0 never triggers one. Pure gate so the
	 * cadence is unit-testable without a running game.
	 */
	public static boolean isBossRound(int wave, int every)
	{
		return every > 0 && wave > 0 && wave % every == 0;
	}

	private void smartSpawn(final int wave)
	{
		if(!this.canSpawn || wave != game.getWave())
			return;
		if(game.getStatus() != GameStatus.INGAME)
			return;
		if(this.mobsSpawned >= this.mobsToSpawn)
			return;

		ConfigSetup cfg = ConfigManager.getMainConfig();
		int boardCap = specialRoundType != null && specialBoardCap > 0 ? specialBoardCap
				: dogRound ? cfg.dogMaxAlivePerPlayer * Math.max(1, game.getPlayersInGame().size())
				: maxOnBoard(game.getPlayersInGame().size(), cfg.zombieBoardBase, cfg.zombieBoardPerPlayer);
		if(getZombiesAlive() >= boardCap)
		{
			COMZombies.scheduleTask(spawnDelayTicks(), () -> smartSpawn(wave));
			return;
		}

		int playersSize = game.getPlayersInGame().size();

		// Truly no spawn points configured for this arena — a setup error, not a temporary lock.
		// Only this case ends the game (with the existing message); the all-spawns-locked case is
		// handled below by retrying.
		if(points.isEmpty())
		{
			oopsWeHadAnError();
			return;
		}

		// #160 — Pre-compute the spawnable candidate set once. The old loop re-rolled a random point
		// from the nearest list up to 1000 times and, if none passed canSpawn (e.g. every spawn is
		// behind a closed door at round 1), ended the game with a "lack of skillez" message. Instead
		// we filter to the actually-spawnable points up front and pick from those; if none are
		// spawnable right now we skip this spawn tick and try again shortly rather than killing the
		// game. This makes round 1 robust for arenas whose only spawns are inside the first room
		// behind a door that opens from the start (or where the door-state isn't yet set).
		SpawnPoint selectPoint = null;
		Player player = game.getPlayersInGame().get(COMZombies.rand.nextInt(playersSize));

		List<SpawnPoint> candidates = points.stream().filter(this::canSpawn).collect(Collectors.toList());
		List<SpawnPoint> active = ConfigManager.getMainConfig().spawnActiveZonesOnly ? activeZoneSpawns(candidates) : Collections.emptyList();
		if(!active.isEmpty())
		{
			selectPoint = active.get(COMZombies.rand.nextInt(active.size()));
		}
		else if(!candidates.isEmpty())
		{
			// Bias toward the spawns nearest a random player (keeps zombies spawning near players),
			// but fall back to any spawnable point if the nearest set is empty.
			List<SpawnPoint> nearest = getNearestPoints(player.getLocation(), Math.min(mobsToSpawn, candidates.size()));
			nearest = nearest.stream().filter(this::canSpawn).collect(Collectors.toList());
			if(!nearest.isEmpty())
				selectPoint = nearest.get(COMZombies.rand.nextInt(nearest.size()));
			else
				selectPoint = candidates.get(COMZombies.rand.nextInt(candidates.size()));
		}

		if(selectPoint == null)
		{
			// No spawnable point right now (all behind closed doors). Don't end the game — retry
			// shortly. Players opening a door will free up spawns and spawning will resume. Only
			// warn once per few seconds to avoid log spam.
			COMZombies.scheduleTask(spawnDelayTicks(), () -> smartSpawn(wave));
			return;
		}

		final SpawnPoint finalPoint = selectPoint;
		COMZombies.scheduleTask(spawnDelayTicks(), () ->
		{
			if(!this.canSpawn || wave != game.getWave())
				return;

			try
			{
				Mob ent = roundSpawner.spawnEntity(game, finalPoint, wave);
				if(ent != null)
				{
					prepareGameMob(ent);
					mobs.add(ent);
					game.barrierManager.recordSpawn(ent, finalPoint);
					ent.setTarget(targetFor(ent));
					mobsSpawned++;
				}
			}
			catch(RuntimeException e)
			{
				COMZombies.log.log(Level.WARNING, "Failed to spawn a round " + wave + " mob at " + finalPoint, e);
			}
			smartSpawn(wave);
		});
	}

	public void update()
	{
		ZombieTargeting.start(game);
		// Cancel any previously scheduled update tick before arming a new one, so there is at most
		// one pending at a time (defensive against double startArena / re-entry).
		cancelUpdateTask();
		updateTaskId = COMZombies.scheduleTask(100, () ->
		{
			updateTaskId = -1;
			if(game.getStatus() != GameStatus.INGAME)
				return;

			for(int i = mobs.size() - 1; i >= 0; i--)
			{
				Mob mob = mobs.get(i);
				if(mob.isDead())
					removeEntity(mob);
				else if(!game.specialEnemyManager.isSteering(mob))
				{
					Player nearest = targetFor(mob);
					if(!BossSpawner.isBoss(mob))
						mob.setTarget(nearest);
					checkStuck(mob, nearest);
				}
			}

			// #130/#96 — drive barrier breaking by zombie proximity each tick.
			game.barrierManager.tickBarriers();

			maybeConvertLastZombie();

			update();
		});
	}

	/**
	 * Cancels the pending update() tick (if any). Called by {@link #reset()} on game end so the
	 * self-rescheduling task does not outlive the game.
	 */
	private void cancelUpdateTask()
	{
		if(updateTaskId != -1)
		{
			Bukkit.getScheduler().cancelTask(updateTaskId);
			updateTaskId = -1;
		}
	}

	/**
	 * 0L — Detects a mob that has made no progress for {@code zombieStuckSeconds} and teleports it
	 * to a reachable (door-open) spawn point near the targeted player so it can resume pathing.
	 * This is what unsticks the common "1 zombie left wedged on geometry" round hang.
	 */
	private void checkStuck(Mob mob, Player nearest)
	{
		ConfigSetup cfg = ConfigManager.getMainConfig();
		// A mob hitting its target or tearing boards off a window is busy, not stuck.
		if(cfg.zombieStuckSeconds <= 0 || nearest == null || isEngaged(mob, cfg))
		{
			stuckLastLoc.remove(mob);
			stuckTicks.remove(mob);
			return;
		}

		Location current = mob.getLocation();
		Location last = stuckLastLoc.get(mob);
		double moved = (last != null && last.getWorld() == current.getWorld()) ? last.distance(current) : Double.MAX_VALUE;
		// Crawlers barely move per interval, so they only count as stuck when they move under a fifth of the threshold.
		double threshold = ZombieSpawner.isCrawler(mob) ? cfg.zombieStuckMoveThreshold * 0.2 : cfg.zombieStuckMoveThreshold;
		long accum = updateStuckTicks(stuckTicks.getOrDefault(mob, 0L), moved, threshold, STUCK_CHECK_INTERVAL);
		stuckLastLoc.put(mob, current.clone());

		if(accum >= (long) cfg.zombieStuckSeconds * 20L)
		{
			teleportStuckMob(mob, nearest);
			accum = 0L;
		}
		stuckTicks.put(mob, accum);
	}

	private boolean isEngaged(Mob mob, ConfigSetup cfg)
	{
		Location at = mob.getLocation();
		double reachSq = cfg.zombieStuckTargetRange * cfg.zombieStuckTargetRange;
		for(Player pl : game.getPlayersInGame())
			if(pl.getWorld() == at.getWorld() && pl.getLocation().distanceSquared(at) <= reachSq)
				return true;

		// Bosses never tear boards, so one parked at a window is stuck, not busy.
		if(BossSpawner.isBoss(mob))
			return false;
		double barrierRange = cfg.barrierBreakRadius + 1;
		double barrierSq = barrierRange * barrierRange;
		for(Barrier barrier : game.barrierManager.getBarriers())
		{
			if(barrier.getStage() >= 5)
				continue;
			for(Block b : barrier.getBlocks())
				if(b.getWorld() == at.getWorld() && b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(at) <= barrierSq)
					return true;
		}
		return false;
	}

	/**
	 * Black Ops only spawns zombies in zones holding a player and the zones next to them. A zone is
	 * next to an occupied zone when their boxes touch or an opened door sits between them. Spawns
	 * outside every zone count as active while a player is outside every zone too. Returns an empty
	 * list when the arena has no zones covering spawns, so the caller falls back to nearest spawns.
	 */
	public List<SpawnPoint> activeSpawnPoints(List<SpawnPoint> candidates)
	{
		return activeZoneSpawns(candidates);
	}

	private List<SpawnPoint> activeZoneSpawns(List<SpawnPoint> candidates)
	{
		List<Zone> zones = game.zoneManager.getAreaZones();
		if(zones.isEmpty() || candidates.isEmpty())
			return Collections.emptyList();

		Set<Zone> occupied = Collections.newSetFromMap(new IdentityHashMap<>());
		boolean playerOutsideZones = false;
		for(Player pl : game.getPlayersInGame())
		{
			boolean inZone = false;
			for(Zone zone : zones)
			{
				if(zone.contains(pl.getLocation()))
				{
					occupied.add(zone);
					inZone = true;
				}
			}
			playerOutsideZones |= !inZone;
		}

		Set<Zone> active = Collections.newSetFromMap(new IdentityHashMap<>());
		active.addAll(occupied);
		for(Zone zone : zones)
		{
			if(active.contains(zone))
				continue;
			for(Zone from : occupied)
			{
				if(zonesTouch(zone, from, 1) || joinedByOpenDoor(zone, from))
				{
					active.add(zone);
					break;
				}
			}
		}

		List<SpawnPoint> eligible = new ArrayList<>();
		boolean anyZoned = false;
		for(SpawnPoint point : candidates)
		{
			Location centre = point.getLocation().clone().add(0.5, 0.5, 0.5);
			boolean zoned = false;
			boolean inActive = false;
			for(Zone zone : zones)
			{
				if(zone.contains(centre))
				{
					zoned = true;
					inActive |= active.contains(zone);
				}
			}
			anyZoned |= zoned;
			if(zoned ? inActive : playerOutsideZones)
				eligible.add(point);
		}
		return anyZoned ? eligible : Collections.emptyList();
	}

	private static boolean zonesTouch(Zone a, Zone b, int margin)
	{
		if(a.getWorld() != b.getWorld())
			return false;
		Location aMin = a.getMin(), aMax = a.getMax(), bMin = b.getMin(), bMax = b.getMax();
		return aMin.getBlockX() <= bMax.getBlockX() + margin && bMin.getBlockX() <= aMax.getBlockX() + margin
				&& aMin.getBlockY() <= bMax.getBlockY() + margin && bMin.getBlockY() <= aMax.getBlockY() + margin
				&& aMin.getBlockZ() <= bMax.getBlockZ() + margin && bMin.getBlockZ() <= aMax.getBlockZ() + margin;
	}

	private boolean joinedByOpenDoor(Zone a, Zone b)
	{
		for(Door door : game.doorManager.getDoors())
		{
			if(!door.isOpened())
				continue;
			boolean nearA = false, nearB = false;
			for(Block block : door.getBlocks())
			{
				nearA |= nearZone(a, block);
				nearB |= nearZone(b, block);
				if(nearA && nearB)
					return true;
			}
		}
		return false;
	}

	private static boolean nearZone(Zone zone, Block block)
	{
		if(block.getWorld() != zone.getWorld())
			return false;
		Location min = zone.getMin(), max = zone.getMax();
		return block.getX() >= min.getBlockX() - 1 && block.getX() <= max.getBlockX() + 1
				&& block.getY() >= min.getBlockY() - 1 && block.getY() <= max.getBlockY() + 1
				&& block.getZ() >= min.getBlockZ() - 1 && block.getZ() <= max.getBlockZ() + 1;
	}

	/**
	 * 0L — Teleports a stuck mob to the reachable spawn point nearest the target player.
	 */
	private void teleportStuckMob(Mob mob, Player target)
	{
		if(BossSpawner.isBoss(mob))
		{
			Location spot = BossSpawner.safeSpawnLocation(game, target.getLocation());
			if(spot != null)
			{
				mob.teleport(spot);
				mob.setTarget(target);
			}
			return;
		}
		if(HellHoundSpawner.isDog(mob))
		{
			Location spot = HellHoundSpawner.findSpotNear(game, target);
			if(spot != null && !game.zoneManager.pushesMobsAt(spot))
			{
				mob.teleport(spot);
				mob.setTarget(target);
				return;
			}
		}
		List<SpawnPoint> spawnable = points.stream()
				.filter(point -> canSpawn(point) && !game.zoneManager.pushesMobsAt(point.getLocation().clone().add(0.5, 0, 0.5)))
				.collect(Collectors.toList());
		if(spawnable.isEmpty())
			return;
		List<SpawnPoint> nearest = nearestPoints(spawnable, target.getLocation(), 1);
		if(nearest.isEmpty())
			return;
		Location dest = nearest.get(0).getLocation().clone().add(0.5, 0, 0.5);
		mob.teleport(dest);
		game.barrierManager.recordSpawn(mob, nearest.get(0));
		mob.setTarget(target);
	}

	private Player targetFor(Mob mob)
	{
		return HellHoundSpawner.isDog(mob) ? HellHoundSpawner.assignedTarget(game, mob) : getNearestPlayer(mob);
	}

	private Player getNearestPlayer(Entity e)
	{
		return ZombieTargeting.choose(game, e);
	}

	public void setSpawnInterval(double interval)
	{
		this.spawnInterval = interval;
	}

	private boolean canSpawn(SpawnPoint point)
	{
		return canSpawnPoint(point);
	}

	/**
	 * #125 — Public door-aware spawn check for a SpawnPoint. Hellhounds use this to avoid spawning
	 * in locked areas. A point not associated with any door is always spawnable; one inside a
	 * door's room is only spawnable if that door is open.
	 */
	public boolean canSpawnPoint(SpawnPoint point)
	{
		if(point == null)
			return false;
		return canSpawnAt(point.getLocation());
	}

	/**
	 * #125 — Whether a location is spawnable: not contained in any closed door's room. A location
	 * not associated with any door is always spawnable; one inside a door's room is only spawnable
	 * if that door is open. Used by the hellhound spawner so dogs don't appear in locked areas.
	 */
	public boolean canSpawnAt(Location loc)
	{
		if(loc == null)
			return false;
		boolean isContained = false;
		boolean maySpawn = false;
		for(Door door : game.doorManager.getDoors())
		{
			for(SpawnPoint p : door.getSpawnsInRoomDoorLeadsTo())
			{
				if(p.getLocation().equals(loc))
				{
					if(door.isOpened())
						maySpawn = true;
					isContained = true;
				}
			}
		}
		if(!isContained)
			return true;
		return maySpawn;
	}

	private void oopsWeHadAnError()
	{
		if(game.getStatus() != GameStatus.INGAME)
			return;

		COMZombies.log.log(Level.SEVERE, "Arena '" + game.getName() + "' has no zombie spawn points configured! Ending the game.");
		for(Player pl : game.getPlayersInGame())
			pl.sendMessage(ChatColor.RED + "This arena has no zombie spawn points set up! The game cannot continue. Ask an admin to add spawns.");
		game.endGame();
	}

	public RoundSpawnType nextWave(int wave, final List<Player> players)
	{
		canSpawn = false;
		mobsSpawned = 0;

		ConfigSetup cfg = ConfigManager.getMainConfig();

		// Tier 4 — boss rounds take precedence over dog rounds on shared waves.
		if(isBossRound(wave, cfg.bossRoundEveryX) && wave >= SpecialEnemyCatalogue.bossRules().minRound)
		{
			bossRound = true;
			dogRound = false;
			specialRoundType = null;
			game.specialEnemyManager.planRound(wave, false);
			// Small wave: a handful of heavy bosses (at least 1, ~half the player count).
			mobsToSpawn = Math.max(1, game.getPlayersInGame().size() / 2);
			roundSpawner = roundSpawnerMap.get(RoundSpawnType.BOSS);
			updateSpawnInterval(wave);
			return RoundSpawnType.BOSS;
		}

		boolean dogWave = rollDogRound(wave, game.getDogRoundEveryX(), cfg);
		SpecialEnemyType special = game.specialEnemyManager.planRound(wave, dogWave);
		if(special != null)
		{
			bossRound = false;
			dogRound = false;
			specialRoundType = special;
			specialBoardCap = game.specialEnemyManager.roundMaxAlive();
			mobsToSpawn = special.roundCount(game.getPlayersInGame().size());
			roundSpawner = new SpecialRoundSpawner(special);
			updateSpawnInterval(wave);
			return RoundSpawnType.SPECIAL;
		}
		specialRoundType = null;

		if(dogWave)
		{
			bossRound = false;
			dogRound = true;
			dogRoundNumber++;
			mobsToSpawn = dogCount(game.getPlayersInGame().size(), dogRoundNumber == 1 ? cfg.dogsPerPlayerFirstRound : cfg.dogsPerPlayer);
			roundSpawner = roundSpawnerMap.get(RoundSpawnType.HELL_HOUNDS);
			updateSpawnInterval(wave);
			return RoundSpawnType.HELL_HOUNDS;
		}
		else
		{
			bossRound = false;
			dogRound = false;
			mobsToSpawn = boZombieCount(wave, players.size(), cfg.zombieCountBase, cfg.zombieCountPerPlayer, cfg.zombieRoundMultiplier, cfg.zombieCountMultiplier);
			roundSpawner = roundSpawnerMap.get(RoundSpawnType.REGULAR);
			updateSpawnInterval(wave);
			return RoundSpawnType.REGULAR;
		}
	}

	private void updateSpawnInterval(int wave)
	{
		spawnInterval = spawnDelayForWave(baseSpawnDelay, spawnDelayFactor, ConfigManager.getMainConfig().zombieSpawnDelayMin, wave);
	}

	public void startWave(int wave)
	{
		canSpawn = true;

		if(game.getPlayersInGame().isEmpty() && game.getStatus() == GameStatus.INGAME)
		{
			this.game.endGame();
			Bukkit.broadcastMessage(COMZombies.PREFIX + "SmartSpawn was sent a players list with no players in it! Game was ended");
			return;
		}
		else if(game.getStatus() != GameStatus.INGAME)
		{
			return;
		}

		this.smartSpawn(wave);
		game.specialEnemyManager.startRound(wave);
	}

	public int getMobsToSpawn()
	{
		return this.mobsToSpawn;
	}

	public int getMobsSpawned()
	{
		return this.mobsSpawned;
	}

	/** Mobs alive that hold up the round; mixed-in specials are not counted. */
	public int getZombiesAlive()
	{
		return this.mobs.size() - this.nonBlocking.size();
	}

	/** Type of the current dedicated special round, or null. */
	public SpecialEnemyType getSpecialRoundType()
	{
		return specialRoundType;
	}

	public double getSpawnInterval()
	{
		return Math.round(this.spawnInterval * 100) / 100.0;
	}

	public boolean isDogRound()
	{
		return dogRound;
	}

	/** How many dog rounds this game has had, counting the current one. */
	public int getDogRoundNumber()
	{
		return Math.max(1, dogRoundNumber);
	}

	public boolean isEntitySpawned(Mob ent)
	{
		return this.mobs.contains(ent);
	}

	public void reset()
	{
		cancelUpdateTask();
		this.mobs.clear();
		this.nonBlocking.clear();
		this.canSpawn = false;
		this.mobsSpawned = 0;
		this.mobsToSpawn = 0;
		this.dogRound = false;
		this.bossRound = false;
		this.specialRoundType = null;
		this.spawnInterval = baseSpawnDelay;
		this.nextDogRound = -1;
		this.dogRoundNumber = 0;
	}

	public String getNewSpawnPointNum()
	{
		return Util.genRandId();
	}
}
