package com.theprogrammingturkey.comz.spawning.special;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.spawning.BossSpawner;
import com.theprogrammingturkey.comz.spawning.HellHoundSpawner;
import com.theprogrammingturkey.comz.spawning.RoundSpawner;
import com.theprogrammingturkey.comz.spawning.SpawnManager;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.util.ParticleFX;
import com.theprogrammingturkey.comz.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-arena special enemies: reads the arena's {@code special_enemies} rules, decides each round which specials
 * appear (a dedicated round or mixed into a regular one), spawns them and runs their behaviours.
 * <p>
 * Specials live in the {@link SpawnManager} mob list like any zombie, so damage, kills and cleanup follow the
 * usual paths. Mixed-in specials are registered as non-blocking: the round can end while they are alive and they
 * carry over until killed or their lifetime runs out.
 */
public class SpecialEnemyManager
{
	private static final long TICK_PERIOD = 5L;
	private static final int LOCK_FX_EVERY_TICKS = 4;
	private static final int TRAIL_SAMPLE_EVERY_TICKS = 8;
	private static final int TRAIL_SIZE = 128;
	private static final double TRAIL_SPACING = 3.0;
	private static final RoundSpawner ATTRIBUTES = new SpecialRoundSpawner(null);
	private static final Set<String> OVERSIZED_WARNED = new HashSet<>();

	final Game game;
	private final List<SpecialEnemyRule> rules = new ArrayList<>();
	private final Map<Mob, ActiveSpecial> active = new HashMap<>();
	private final List<SpecialEnemyRule> pendingMixed = new ArrayList<>();
	private final List<Integer> pendingTasks = new ArrayList<>();
	private SpecialEnemyRule roundRule;
	private boolean lastRemovalEscaped;
	private int tickTask = -1;
	private Map<Location, PerkType> perkMachines = new HashMap<>();
	private long perkMachinesAt;
	private final Map<String, Location> lockedMachines = new HashMap<>();
	/** Spots players have recently stood on: known to be inside the playable, opened part of the map. */
	private final List<Location> playerTrail = new ArrayList<>();
	private int tickCount;

	public SpecialEnemyManager(Game game)
	{
		this.game = game;
	}

	public void load(JsonObject settings)
	{
		rules.clear();
		if(!settings.has("special_enemies") || !settings.get("special_enemies").isJsonArray())
			return;
		for(JsonElement elem : settings.getAsJsonArray("special_enemies"))
			if(elem.isJsonObject())
				rules.add(SpecialEnemyRule.fromJson(elem.getAsJsonObject()));
	}

	public void save(JsonObject settings)
	{
		JsonArray array = new JsonArray();
		for(SpecialEnemyRule rule : rules)
			array.add(rule.toJson());
		settings.add("special_enemies", array);
	}

	public List<SpecialEnemyRule> getRules()
	{
		return rules;
	}

	/** First rule for the type, or null when the type is not enabled on this arena. */
	public SpecialEnemyRule getRule(String typeId)
	{
		for(SpecialEnemyRule rule : rules)
			if(rule.type.equalsIgnoreCase(typeId))
				return rule;
		return null;
	}

	public void setEnabled(SpecialEnemyType type, boolean enabled)
	{
		rules.removeIf(rule -> rule.type.equalsIgnoreCase(type.id));
		if(enabled)
			rules.add(type.defaultRule.copy());
	}

	// --- round planning ---------------------------------------------------------------------------------------

	/**
	 * Decides the special side of round {@code wave}. Returns the type of a dedicated special round, or null for a
	 * regular (or dog) round; in the regular case the specials to mix in are queued for {@link #startRound}.
	 */
	public SpecialEnemyType planRound(int wave, boolean dogRound)
	{
		pendingMixed.clear();
		roundRule = null;

		List<SpecialEnemyRule> ownRound = new ArrayList<>();
		for(SpecialEnemyRule rule : rules)
		{
			SpecialEnemyType type = SpecialEnemyCatalogue.get(rule.type);
			if(type == null)
				continue;
			int roll = COMZombies.rand.nextInt(100);
			int floor = rule.roundFloor(type);
			if(dogRound)
			{
				if(rule.replacesDogRound(wave, roll, floor))
					ownRound.add(rule);
			}
			else if(rule.mode != SpecialEnemyRule.Mode.REPLACES_DOG_ROUND && rule.firesOn(wave, roll, floor))
			{
				if(rule.isOwnRound())
					ownRound.add(rule);
				else
					pendingMixed.add(rule);
			}
		}

		if(ownRound.isEmpty())
			return null;
		pendingMixed.clear();
		roundRule = ownRound.get(COMZombies.rand.nextInt(ownRound.size()));
		return SpecialEnemyCatalogue.get(roundRule.type);
	}

	/** Concurrent cap for the dedicated special round, or 0 when there is none. */
	public int roundMaxAlive()
	{
		if(roundRule == null)
			return 0;
		SpecialEnemyType type = SpecialEnemyCatalogue.get(roundRule.type);
		return type != null && type.behaviour == SpecialBehaviour.BOSS ? 1 : roundRule.maxAlive;
	}

	/** Schedules the mixed-in specials of a regular round, spread over its first minute. */
	public void startRound(int wave)
	{
		for(SpecialEnemyRule rule : new ArrayList<>(pendingMixed))
		{
			long delay = 20L * (10 + COMZombies.rand.nextInt(40));
			final int[] id = new int[1];
			id[0] = COMZombies.scheduleTask(delay, () ->
			{
				pendingTasks.remove((Integer) id[0]);
				if(game.getStatus() != Game.GameStatus.INGAME || game.getWave() != wave)
					return;
				spawnMixed(rule, wave);
			});
			if(id[0] != -1)
				pendingTasks.add(id[0]);
		}
		pendingMixed.clear();
	}

	private void spawnMixed(SpecialEnemyRule rule, int wave)
	{
		SpecialEnemyType type = SpecialEnemyCatalogue.get(rule.type);
		if(type == null)
			return;
		int cap = type.behaviour == SpecialBehaviour.BOSS ? 1 : rule.maxAlive;
		if(aliveOfType(type) >= cap)
			return;
		SpawnPoint point = pickSpawnPoint();
		if(point == null)
			return;
		Mob mob = spawn(type, point.getLocation(), wave, false);
		if(mob != null)
			game.spawnManager.addSpecialMob(mob, false);
	}

	private int aliveOfType(SpecialEnemyType type)
	{
		int n = 0;
		for(ActiveSpecial a : active.values())
			if(a.type == type && !a.mob.isDead())
				n++;
		return n;
	}

	/** A door-open spawn point close to a random living player. */
	SpawnPoint pickSpawnPoint()
	{
		List<SpawnPoint> open = game.spawnManager.getPoints().stream().filter(game.spawnManager::canSpawnPoint).collect(Collectors.toList());
		if(open.isEmpty())
			return null;
		List<Player> players = livingPlayers();
		if(players.isEmpty())
			return open.get(COMZombies.rand.nextInt(open.size()));
		Player near = players.get(COMZombies.rand.nextInt(players.size()));
		List<SpawnPoint> nearest = SpawnManager.nearestPoints(open, near.getLocation(), 3);
		return nearest.get(COMZombies.rand.nextInt(nearest.size()));
	}

	// --- spawning ---------------------------------------------------------------------------------------------

	/** Spawns and registers a special; the caller adds it to the spawn manager's mob list. */
	public Mob spawn(SpecialEnemyType type, Location point, int wave, boolean blocksRound)
	{
		World world = point.getWorld();
		if(world == null)
			return null;
		boolean boss = type.behaviour == SpecialBehaviour.BOSS;
		if(type.behaviour == SpecialBehaviour.STEAL_PERK)
			point = spawnNearPerkMachine(point);
		Location loc = boss ? BossSpawner.spawnLocation(game, point)
				: new Location(world, point.getBlockX() + 0.5, point.getBlockY(), point.getBlockZ() + 0.5);
		if(loc.getWorld() != null)
			world = loc.getWorld();

		Entity entity = world.spawnEntity(loc, resolveEntity(type));
		if(!(entity instanceof Mob))
		{
			entity.remove();
			entity = world.spawnEntity(loc, EntityType.ZOMBIE);
		}
		Mob mob = (Mob) entity;

		applyLooks(mob, type);
		if(boss && !BossSpawner.fitThroughDoorways(mob, type.scale) && OVERSIZED_WARNED.add(type.id))
			COMZombies.log.warning("Boss " + type.id + " (" + mob.getType() + ") is bigger than a doorway and this server can't scale it down; it may get stuck.");
		ATTRIBUTES.setFollowDistance(mob, 512);
		if(type.speed != 1.0)
			ATTRIBUTES.setSpeed(mob, (float) type.speed);
		if(!boss && type.scale != 1.0)
			ATTRIBUTES.setScale(mob, type.scale);
		double health = type.health(wave, game.getPlayersInGame().size());
		double cappedHealth = com.theprogrammingturkey.comz.spawning.ZombieHealth.applyHealth(mob, health);

		if(boss)
			BossSpawner.markBoss(mob);
		ActiveSpecial special = new ActiveSpecial(mob, type, blocksRound);
		special.maxHealth = cappedHealth;
		special.nextAmbientAt = System.currentTimeMillis() + 3000L + COMZombies.rand.nextInt(6000);
		active.put(mob, special);

		ParticleFX.groundBreak(world, loc);
		SoundUtil.playKey(world, loc, type.soundKey("spawn"), "ENTITY_ZOMBIE_AMBIENT", SoundCategory.HOSTILE, 1.0f, 1.0f);
		SpecialBehaviours.onSpawn(this, special);
		ensureTicking();
		return mob;
	}

	/** The open zombie spawn closest to a random perk machine, so perk thieves come out right next to their prey. */
	private Location spawnNearPerkMachine(Location fallback)
	{
		List<Location> machines = new ArrayList<>(perkMachines().keySet());
		machines.removeIf(m -> m.getWorld() != fallback.getWorld());
		if(machines.isEmpty())
			return fallback;
		Location machine = machines.get(COMZombies.rand.nextInt(machines.size()));
		List<SpawnPoint> open = game.spawnManager.getPoints().stream().filter(game.spawnManager::canSpawnPoint).collect(Collectors.toList());
		List<SpawnPoint> nearest = SpawnManager.nearestPoints(open, machine, 1);
		return nearest.isEmpty() ? fallback : nearest.get(0).getLocation();
	}

	private static EntityType resolveEntity(SpecialEnemyType type)
	{
		for(String name : type.entities)
		{
			EntityType et = Compat.entityType(name.toUpperCase(Locale.ROOT));
			if(et != null && et.getEntityClass() != null && Mob.class.isAssignableFrom(et.getEntityClass()))
				return et;
		}
		return EntityType.ZOMBIE;
	}

	private void applyLooks(Mob mob, SpecialEnemyType type)
	{
		mob.setCustomName(ChatColor.RED + type.displayName);
		mob.setCustomNameVisible(type.showName);
		mob.setGlowing(type.glowing);
		mob.setRemoveWhenFarAway(false);
		if(mob instanceof Zombie)
			((Zombie) mob).setBaby(type.baby);
		else if(mob instanceof Ageable)
		{
			if(type.baby)
				((Ageable) mob).setBaby();
			else
				((Ageable) mob).setAdult();
		}
		// Piglins and hoglins turn into zombified variants outside the Nether, dropping out of the game.
		Compat.invokeIfPresent(mob, "setImmuneToZombification", new Class<?>[]{boolean.class}, true);
		if(type.behaviour == SpecialBehaviour.NAPALM)
		{
			mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Compat.INFINITE_DURATION, 1, true, false));
			mob.setFireTicks(Integer.MAX_VALUE);
		}
		// Natural spawn equipment is rolled after the spawn call returns, so dress the mob a little later.
		COMZombies.scheduleTask(10, () ->
		{
			if(mob.isValid())
				applyEquipment(mob, type);
		});
	}

	private static void applyEquipment(Mob mob, SpecialEnemyType type)
	{
		EntityEquipment eq = mob.getEquipment();
		if(eq == null)
			return;
		eq.clear();
		ItemStack head = item(type.equipment.get("head"));
		if(!type.model.isEmpty() && PackModels.isPackEnabled())
		{
			head = new ItemStack(head != null ? head.getType() : Material.PAPER);
			PackModels.apply(head, type.model);
		}
		eq.setHelmet(head);
		eq.setChestplate(item(type.equipment.get("chest")));
		eq.setLeggings(item(type.equipment.get("legs")));
		eq.setBoots(item(type.equipment.get("feet")));
		eq.setItemInMainHand(item(type.equipment.get("hand")));
		eq.setHelmetDropChance(0);
		eq.setChestplateDropChance(0);
		eq.setLeggingsDropChance(0);
		eq.setBootsDropChance(0);
		eq.setItemInMainHandDropChance(0);
	}

	private static ItemStack item(String material)
	{
		Material mat = Compat.material(material);
		return mat == null || mat == Material.AIR ? null : new ItemStack(mat);
	}

	// --- per-tick behaviour -----------------------------------------------------------------------------------

	public void start()
	{
		ensureTicking();
	}

	private void ensureTicking()
	{
		if(tickTask != -1)
			return;
		tickTask = COMZombies.scheduleTask(TICK_PERIOD, TICK_PERIOD, this::tick);
	}

	private void tick()
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
			return;
		tickCount++;
		if(tickCount % TRAIL_SAMPLE_EVERY_TICKS == 0)
			samplePlayerTrail();
		if(tickCount % LOCK_FX_EVERY_TICKS == 0)
			showLocks();
		long now = System.currentTimeMillis();
		for(ActiveSpecial a : new ArrayList<>(active.values()))
		{
			if(!a.mob.isValid() || !active.containsKey(a.mob))
				continue;
			if(a.type.lifetimeSeconds > 0 && a.secondsAlive() >= a.type.lifetimeSeconds)
			{
				escape(a, null);
				continue;
			}
			if(now >= a.nextAmbientAt)
			{
				SoundUtil.playKey(a.mob.getWorld(), a.mob.getLocation(), a.type.soundKey("ambient"), "ENTITY_ZOMBIE_AMBIENT", SoundCategory.HOSTILE, 1.0f, 1.0f);
				a.nextAmbientAt = now + 6000L + COMZombies.rand.nextInt(8000);
			}
			updateBossBar(a);
			SpecialBehaviours.tick(this, a);
		}
	}

	/** Removes a special without death effects or rewards; whatever it stole is gone. */
	void escape(ActiveSpecial a, String message)
	{
		a.quietRemoval = true;
		a.escaped = true;
		if(message != null)
			broadcast(message);
		World world = a.mob.getWorld();
		world.spawnParticle(Compat.SMOKE, a.mob.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.02);
		game.spawnManager.killMob(a.mob);
	}

	/** Removes a special that took itself out (napalm explosion): counts as gone, without rewards. */
	void selfDestruct(ActiveSpecial a)
	{
		a.quietRemoval = true;
		game.spawnManager.killMob(a.mob);
	}

	void createBossBar(ActiveSpecial a)
	{
		BarColor color;
		try
		{
			color = BarColor.valueOf(a.type.option("bossbar_color", "RED").toUpperCase(Locale.ROOT));
		} catch(IllegalArgumentException e)
		{
			color = BarColor.RED;
		}
		a.bossBar = Bukkit.createBossBar(ChatColor.RED + a.type.displayName, color, BarStyle.SEGMENTED_10);
		for(Player p : game.getPlayersInGame())
			a.bossBar.addPlayer(p);
	}

	private void updateBossBar(ActiveSpecial a)
	{
		if(a.bossBar == null || a.maxHealth <= 0)
			return;
		a.bossBar.setProgress(Math.max(0.0, Math.min(1.0, a.mob.getHealth() / a.maxHealth)));
	}

	// --- hooks from the game / spawn manager ------------------------------------------------------------------

	public boolean isSpecial(Entity entity)
	{
		return entity instanceof Mob && active.containsKey(entity);
	}

	/** True while the special steers itself, so the shared retargeting and stuck teleport must leave it alone. */
	public boolean isSteering(Mob mob)
	{
		ActiveSpecial a = active.get(mob);
		return a != null && a.steering;
	}

	public boolean blocksRound(Mob mob)
	{
		ActiveSpecial a = active.get(mob);
		return a == null || a.blocksRound;
	}

	public boolean isInstaKillImmune(Mob mob)
	{
		ActiveSpecial a = active.get(mob);
		return a != null ? a.type.instaKillImmune : BossSpawner.isBoss(mob);
	}

	public boolean isNukeImmune(Mob mob)
	{
		ActiveSpecial a = active.get(mob);
		return a != null ? a.type.nukeImmune : BossSpawner.isBoss(mob);
	}

	/** A nuke just kills a napalm zombie: no blast of its own. */
	public void suppressDeathBlast(Mob mob)
	{
		ActiveSpecial a = active.get(mob);
		if(a != null && a.type.behaviour == SpecialBehaviour.NAPALM)
			a.exploded = true;
	}

	public boolean showsName(Mob mob)
	{
		ActiveSpecial a = active.get(mob);
		return a != null && a.type.showName;
	}

	public int killPoints(Mob mob, int regular)
	{
		ActiveSpecial a = active.get(mob);
		return a == null ? regular : a.type.points;
	}

	/**
	 * The special was killed by a player or a trap ({@code killer} null when the trap's buyer left): drop its
	 * reward. Runs before the mob leaves the spawn manager.
	 */
	public void onKilled(Mob mob, Player killer)
	{
		ActiveSpecial a = active.get(mob);
		if(a == null)
			return;
		if(a.type.rewardPowerUp != PowerUp.NONE)
			game.powerUpManager.dropPowerUp(mob, a.type.rewardPowerUp);
		if(a.type.behaviour == SpecialBehaviour.BOSS)
			broadcast(killer != null
					? ChatColor.GOLD + killer.getName() + ChatColor.YELLOW + " killed " + ChatColor.RED + a.type.displayName + ChatColor.YELLOW + "!"
					: ChatColor.RED + a.type.displayName + ChatColor.YELLOW + " was killed!");
	}

	/** The mob left the spawn manager for any reason; runs death effects unless the special removed itself. */
	public void onMobRemoved(Entity entity)
	{
		ActiveSpecial a = entity instanceof Mob ? active.remove(entity) : null;
		if(a == null)
			return;
		lastRemovalEscaped = a.escaped;
		if(a.bossBar != null)
			a.bossBar.removeAll();
		if(game.getStatus() != Game.GameStatus.INGAME || a.quietRemoval)
			return;
		SoundUtil.playKey(entity.getWorld(), entity.getLocation(), a.type.soundKey("death"), "ENTITY_ZOMBIE_DEATH", SoundCategory.HOSTILE, 1.0f, 1.0f);
		SpecialBehaviours.onDeath(this, a);
	}

	/** The last mob of a dedicated special round is gone: the round-end power-up, unless it got away. */
	public void onSpecialRoundCleared(Entity last, SpecialEnemyType type)
	{
		if(type != null && type.roundEndPowerUp != PowerUp.NONE && !lastRemovalEscaped)
			game.powerUpManager.dropPowerUp(last, type.roundEndPowerUp);
	}

	public void stop()
	{
		if(tickTask != -1)
		{
			Bukkit.getScheduler().cancelTask(tickTask);
			tickTask = -1;
		}
		for(int id : pendingTasks)
			Bukkit.getScheduler().cancelTask(id);
		pendingTasks.clear();
		pendingMixed.clear();
		roundRule = null;
		perkMachinesAt = 0;
		lockedMachines.clear();
		playerTrail.clear();
		tickCount = 0;
		for(ActiveSpecial a : active.values())
		{
			if(a.bossBar != null)
				a.bossBar.removeAll();
			if(a.mob.isValid())
				a.mob.remove();
		}
		active.clear();
	}

	// --- helpers for behaviours -------------------------------------------------------------------------------

	/** Perk machine positions, rescanned at most every few seconds (the scan walks the arena's tile entities). */
	Map<Location, PerkType> perkMachines()
	{
		long now = System.currentTimeMillis();
		if(now - perkMachinesAt > 5000L)
		{
			perkMachines = game.machineModelManager.getPerkMachines();
			perkMachinesAt = now;
		}
		return perkMachines;
	}

	/** In-game players who can be targeted: not dead, downed or a Who's Who ghost. */
	List<Player> livingPlayers()
	{
		List<Player> out = new ArrayList<>();
		for(Player p : game.getLivingPlayers())
			if(!game.downedPlayerManager.isDownedPlayer(p) && !game.downedPlayerManager.isGhost(p))
				out.add(p);
		return out;
	}

	Player nearestPlayer(Location loc, double maxDistance)
	{
		Player best = null;
		double bestSq = maxDistance * maxDistance;
		for(Player p : livingPlayers())
		{
			if(p.getWorld() != loc.getWorld())
				continue;
			double d = p.getLocation().distanceSquared(loc);
			if(d <= bestSq)
			{
				bestSq = d;
				best = p;
			}
		}
		return best;
	}

	void broadcast(String message)
	{
		for(Player p : game.getPlayersAndSpectators())
			p.sendMessage(COMZombies.PREFIX + message);
	}

	static RoundSpawner attributes()
	{
		return ATTRIBUTES;
	}

	// --- machine locks (Brutus) -------------------------------------------------------------------------------

	private static String blockKey(Location loc)
	{
		return (loc.getWorld() == null ? "" : loc.getWorld().getName()) + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
	}

	/** Locks every perk machine and mystery box within {@code radius} of {@code at}. */
	public void lockMachinesNear(Location at, double radius, String by)
	{
		if(radius <= 0)
			return;
		List<Location> machines = new ArrayList<>(perkMachines().keySet());
		for(RandomBox box : game.boxManager.getActiveBoxes())
			if(box.getLocation() != null)
				machines.add(box.getLocation().getBlock().getLocation().add(0.5, 0.5, 0.5));
		double radiusSq = radius * radius;
		for(Location machine : machines)
		{
			if(machine.getWorld() != at.getWorld() || machine.distanceSquared(at) > radiusSq)
				continue;
			String key = blockKey(machine);
			if(lockedMachines.containsKey(key))
				continue;
			lockedMachines.put(key, machine.clone());
			World world = machine.getWorld();
			world.spawnParticle(Compat.SMOKE, machine, 30, 0.4, 0.6, 0.4, 0.03);
			world.spawnParticle(Compat.ELECTRIC_SPARK, machine, 20, 0.4, 0.6, 0.4, 0.1);
			SoundUtil.play(world, machine, "BLOCK_ANVIL_LAND", SoundCategory.HOSTILE, 1.0f, 0.6f);
			broadcast(ChatColor.RED + by + ChatColor.YELLOW + " locked a machine! Pay " + ChatColor.GOLD + ConfigManager.getMainConfig().brutusUnlockCost
					+ ChatColor.YELLOW + " points at it to unlock it.");
		}
	}

	/**
	 * True when the machine at {@code machine} is locked and the interaction is used up: either the player paid to unlock
	 * it or was told it is locked. The next interaction buys as usual.
	 */
	public boolean blockedByLock(Player player, Location machine)
	{
		if(machine == null || lockedMachines.isEmpty())
			return false;
		String key = blockKey(machine);
		Location locked = lockedMachines.get(key);
		if(locked == null)
			return false;
		int cost = ConfigManager.getMainConfig().brutusUnlockCost;
		if(!PointManager.INSTANCE.canBuy(player, cost))
		{
			player.sendMessage(COMZombies.PREFIX + ChatColor.RED + "This machine is locked! It costs " + cost + " points to unlock.");
			return true;
		}
		PointManager.INSTANCE.takePoints(player, cost);
		PointManager.INSTANCE.notifyPlayer(player);
		lockedMachines.remove(key);
		SoundUtil.play(locked.getWorld(), locked, "BLOCK_IRON_DOOR_OPEN", SoundCategory.BLOCKS, 1.0f, 1.0f);
		locked.getWorld().spawnParticle(Compat.HAPPY_VILLAGER, locked, 15, 0.4, 0.6, 0.4, 0);
		player.sendMessage(COMZombies.PREFIX + ChatColor.GREEN + "Machine unlocked.");
		return true;
	}

	public boolean isLocked(Location machine)
	{
		return machine != null && lockedMachines.containsKey(blockKey(machine));
	}

	private void showLocks()
	{
		for(Location locked : lockedMachines.values())
			if(locked.getWorld() != null)
				locked.getWorld().spawnParticle(Compat.SMOKE, locked.clone().add(0, 0.6, 0), 4, 0.3, 0.3, 0.3, 0.01);
	}

	// --- safe in-map spots ------------------------------------------------------------------------------------

	private void samplePlayerTrail()
	{
		for(Player p : livingPlayers())
		{
			if(!p.isOnGround() || p.isInsideVehicle() || p.isFlying())
				continue;
			Location spot = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
			boolean known = false;
			for(Location l : playerTrail)
				if(l.getWorld() == spot.getWorld() && l.distanceSquared(spot) < TRAIL_SPACING * TRAIL_SPACING)
				{
					known = true;
					break;
				}
			if(known)
				continue;
			if(playerTrail.size() >= TRAIL_SIZE)
				playerTrail.remove(0);
			playerTrail.add(spot);
		}
	}

	/**
	 * A random spot inside the playable area for throwing {@code player} away: somewhere players have stood this game, at
	 * least {@code minDistance} from them when possible. Falls back to the arena's player spawn.
	 */
	Location safeTeleportSpot(Player player, double minDistance)
	{
		List<Location> far = new ArrayList<>();
		List<Location> any = new ArrayList<>();
		for(Location l : playerTrail)
		{
			if(l.getWorld() != player.getWorld() || !HellHoundSpawner.isStandableSpot(l.getBlock()))
				continue;
			any.add(l);
			if(l.distanceSquared(player.getLocation()) >= minDistance * minDistance)
				far.add(l);
		}
		List<Location> pool = !far.isEmpty() ? far : any;
		if(!pool.isEmpty())
			return pool.get(COMZombies.rand.nextInt(pool.size())).clone();
		return game.arena.hasPlayerTPLocation() ? game.arena.getPlayerTPLocation().clone() : null;
	}
}
