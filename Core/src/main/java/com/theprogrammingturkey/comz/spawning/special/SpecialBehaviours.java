package com.theprogrammingturkey.comz.spawning.special;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.SoundUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What each {@link SpecialBehaviour} does on spawn, every tick and on death. */
final class SpecialBehaviours
{
	private static final PotionEffectType BLINDNESS = Compat.potionEffect("BLINDNESS");

	private SpecialBehaviours()
	{
	}

	static void onSpawn(SpecialEnemyManager manager, ActiveSpecial a)
	{
		switch(a.type.behaviour)
		{
			case STEAL_PERK:
				a.steering = true;
				break;
			case BOSS:
				if(a.type.option("bossbar", true))
					manager.createBossBar(a);
				manager.broadcast(ChatColor.RED + a.type.displayName + ChatColor.YELLOW + " has appeared!");
				break;
			default:
				break;
		}
	}

	static void tick(SpecialEnemyManager manager, ActiveSpecial a)
	{
		switch(a.type.behaviour)
		{
			case STEAL_PERK:
				tickPerkThief(manager, a);
				break;
			case STEAL_WEAPON:
				tickWeaponThief(manager, a);
				break;
			case BOSS:
				tickBoss(manager, a);
				break;
			case NAPALM:
				tickNapalm(manager, a);
				break;
			case SHRIEKER:
				tickShrieker(manager, a);
				break;
		}
	}

	static void onDeath(SpecialEnemyManager manager, ActiveSpecial a)
	{
		Game game = manager.game;
		if(a.type.behaviour == SpecialBehaviour.NAPALM && !a.exploded)
			explode(manager, a);

		for(ActiveSpecial.StolenPerk stolen : a.stolenPerks)
			returnPerk(game, stolen.player, stolen.perk);
		a.stolenPerks.clear();

		if(a.stolenWeapon != null)
			returnWeapon(manager, a);
	}

	// --- steal_perk -------------------------------------------------------------------------------------------

	private static void tickPerkThief(SpecialEnemyManager manager, ActiveSpecial a)
	{
		if(!a.steering)
			return;
		Map<Location, PerkType> machines = manager.perkMachines();
		if(a.goal == null || !machines.containsKey(a.goal))
		{
			a.goal = pickMachine(machines, a);
			a.lastActionAt = 0;
			if(a.goal == null)
			{
				a.steering = false;
				return;
			}
		}

		double reach = a.type.option("reach_distance", 2.5);
		if(horizontalDistanceSq(a.mob.getLocation(), a.goal) > reach * reach)
		{
			a.lastActionAt = 0;
			MobNavigator.moveTo(a.mob, a.goal, a.type.option("path_speed", 1.3));
			return;
		}

		a.mob.setTarget(null);
		long now = System.currentTimeMillis();
		if(a.lastActionAt == 0)
		{
			a.lastActionAt = now;
			a.mob.getWorld().spawnParticle(Compat.ENCHANTED_HIT, a.goal, 20, 0.4, 0.6, 0.4, 0.1);
		}
		if(now - a.lastActionAt < a.type.option("steal_seconds", 2.0) * 1000)
			return;

		stealPerkFromMachine(manager, a, machines.get(a.goal));
		a.visitedMachines.add(a.goal);
		a.goal = null;
		a.lastActionAt = 0;
	}

	private static Location pickMachine(Map<Location, PerkType> machines, ActiveSpecial a)
	{
		List<Location> options = new ArrayList<>();
		for(Location loc : machines.keySet())
			if(!a.visitedMachines.contains(loc) && loc.getWorld() == a.mob.getWorld())
				options.add(loc);
		return options.isEmpty() ? null : options.get(COMZombies.rand.nextInt(options.size()));
	}

	private static void stealPerkFromMachine(SpecialEnemyManager manager, ActiveSpecial a, PerkType perk)
	{
		Game game = manager.game;
		SoundUtil.playKey(a.mob.getWorld(), a.goal, a.type.soundKey("attack"), "ENTITY_WITCH_CELEBRATE", SoundCategory.HOSTILE, 1.0f, 1.2f);
		if(perk == null)
			return;
		List<Player> owners = new ArrayList<>();
		for(Player p : manager.livingPlayers())
			if(game.perkManager.hasPerk(p, perk))
				owners.add(p);
		if(owners.isEmpty())
			return;
		Player victim = owners.get(COMZombies.rand.nextInt(owners.size()));
		game.perkManager.removePerkEffect(victim, perk);
		a.stolenPerks.add(new ActiveSpecial.StolenPerk(victim, perk));
		manager.broadcast(ChatColor.RED + a.type.displayName + ChatColor.YELLOW + " stole " + ChatColor.GOLD + victim.getName()
				+ ChatColor.YELLOW + "'s " + ChatColor.AQUA + perk + ChatColor.YELLOW + "! Kill it to get it back.");
	}

	/** Re-grants a stolen perk directly: it was owned before, so the max-perks cap must not swallow it. */
	private static void returnPerk(Game game, Player player, PerkType perk)
	{
		if(!game.getPlayersInGame().contains(player) || game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
			return;
		List<PerkType> perks = game.perkManager.getPlayersPerks(player);
		if(perks.contains(perk))
			return;
		perks.add(perk);
		perk.initialEffect(player);
		game.perkManager.applyPerkEffect(player, perk);
		player.sendMessage(COMZombies.PREFIX + ChatColor.GREEN + "Your " + perk + " is back.");
	}

	// --- steal_weapon -----------------------------------------------------------------------------------------

	private static void tickWeaponThief(SpecialEnemyManager manager, ActiveSpecial a)
	{
		if(!a.steering)
		{
			Player victim = manager.nearestPlayer(a.mob.getLocation(), a.type.option("grab_distance", 2.0));
			if(victim != null)
				grabWeapon(manager, a, victim);
			return;
		}

		if(a.secondsInPhase() >= a.type.option("flee_seconds", 15.0))
		{
			String gun = a.stolenWeapon != null ? a.stolenWeapon.getBaseWeapon().getName() : "loot";
			a.stolenWeapon = null;
			manager.escape(a, ChatColor.RED + a.type.displayName + ChatColor.YELLOW + " got away with " + ChatColor.GOLD
					+ (a.weaponOwner != null ? a.weaponOwner.getName() + "'s " : "") + gun + ChatColor.YELLOW + "!");
			return;
		}

		if(a.goal == null || horizontalDistanceSq(a.mob.getLocation(), a.goal) < 4)
			a.goal = fleeGoal(manager, a);
		MobNavigator.moveTo(a.mob, a.goal, a.type.option("path_speed", 1.4));
	}

	private static void grabWeapon(SpecialEnemyManager manager, ActiveSpecial a, Player victim)
	{
		PlayerWeaponManager weapons = manager.game.getPlayersWeapons(victim);
		int slot = victim.getInventory().getHeldItemSlot();
		GunInstance gun = weapons.getGun(slot);
		if(gun == null || "Death Machine".equalsIgnoreCase(gun.getBaseWeapon().getName()))
			return;

		weapons.removeWeapon(gun);
		victim.getInventory().setItem(slot, null);
		victim.updateInventory();
		a.weaponOwner = victim;
		a.stolenWeapon = gun;
		a.stolenSlot = slot;
		a.steering = true;
		a.goal = null;
		a.newPhase();
		SpecialEnemyManager.attributes().setSpeed(a.mob, (float) a.type.option("flee_speed", 1.5));

		SoundUtil.playKey(a.mob.getWorld(), a.mob.getLocation(), a.type.soundKey("attack"), "ENTITY_ITEM_PICKUP", SoundCategory.HOSTILE, 1.0f, 0.8f);
		if(a.mob.getEquipment() != null)
			a.mob.getEquipment().setItemInMainHand(gun.getBaseWeapon().getStack());
		manager.broadcast(ChatColor.RED + a.type.displayName + ChatColor.YELLOW + " grabbed " + ChatColor.GOLD + victim.getName()
				+ ChatColor.YELLOW + "'s " + gun.getBaseWeapon().getName() + "! Kill it before it escapes.");
	}

	/** The open spawn point farthest from the nearest player, so the thief runs away from the group. */
	private static Location fleeGoal(SpecialEnemyManager manager, ActiveSpecial a)
	{
		Player chaser = manager.nearestPlayer(a.mob.getLocation(), 512);
		Location from = chaser != null ? chaser.getLocation() : a.mob.getLocation();
		Location best = null;
		double bestSq = -1;
		for(SpawnPoint point : manager.game.spawnManager.getPoints())
		{
			Location loc = point.getLocation();
			if(loc.getWorld() != from.getWorld() || !manager.game.spawnManager.canSpawnPoint(point))
				continue;
			double d = loc.distanceSquared(from);
			if(d > bestSq)
			{
				bestSq = d;
				best = loc.clone().add(0.5, 0, 0.5);
			}
		}
		return best != null ? best : a.mob.getLocation();
	}

	private static void returnWeapon(SpecialEnemyManager manager, ActiveSpecial a)
	{
		Player owner = a.weaponOwner;
		GunInstance gun = (GunInstance) a.stolenWeapon;
		a.stolenWeapon = null;
		Game game = manager.game;
		if(owner == null || !game.getPlayersInGame().contains(owner))
			return;

		PlayerWeaponManager weapons = game.getPlayersWeapons(owner);
		int slot = a.stolenSlot;
		if(!isFreeSlot(owner, weapons, slot))
			slot = weapons.getCorrectSlot(gun.getBaseWeapon());
		if(!isFreeSlot(owner, weapons, slot))
		{
			owner.sendMessage(COMZombies.PREFIX + ChatColor.RED + "No room to take your " + gun.getBaseWeapon().getName() + " back.");
			return;
		}
		gun.setSlot(slot);
		weapons.addWeapon(gun);
		owner.sendMessage(COMZombies.PREFIX + ChatColor.GREEN + "You got your " + gun.getBaseWeapon().getName() + " back!");
	}

	private static boolean isFreeSlot(Player player, PlayerWeaponManager weapons, int slot)
	{
		if(slot < 0 || weapons.getWeapon(slot) != null)
			return false;
		ItemStack stack = player.getInventory().getItem(slot);
		return stack == null || stack.getType() == Material.AIR;
	}

	// --- boss -------------------------------------------------------------------------------------------------

	private static void tickBoss(SpecialEnemyManager manager, ActiveSpecial a)
	{
		long now = System.currentTimeMillis();
		if(now - a.lastActionAt < a.type.option("attack_cooldown_seconds", 8.0) * 1000)
			return;
		Player target = manager.nearestPlayer(a.mob.getLocation(), a.type.option("trigger_distance", 3.0));
		if(target == null)
			return;
		a.lastActionAt = now;

		String attack = a.type.option("attack", "ground_slam");
		SoundUtil.playKey(a.mob.getWorld(), a.mob.getLocation(), a.type.soundKey("attack"), "ENTITY_RAVAGER_ROAR", SoundCategory.HOSTILE, 1.5f, 1.0f);
		if(attack.equalsIgnoreCase("grab_teleport"))
			grabAndTeleport(manager, a, target);
		else if(attack.equalsIgnoreCase("steal_perk"))
			stealPerkOnHit(manager, a, target);
		else
			groundSlam(manager, a);
	}

	private static void groundSlam(SpecialEnemyManager manager, ActiveSpecial a)
	{
		Location centre = a.mob.getLocation();
		World world = centre.getWorld();
		double radius = a.type.option("attack_radius", 4.0);
		double damage = a.type.option("attack_damage", 6.0);
		double knockback = a.type.option("knockback", 1.2);

		world.spawnParticle(Compat.EXPLOSION, centre, 3, radius / 3, 0.2, radius / 3, 0);
		world.spawnParticle(Compat.BLOCK, centre, 60, radius / 2, 0.1, radius / 2, 0, centre.clone().subtract(0, 1, 0).getBlock().getBlockData());
		for(Player p : manager.livingPlayers())
		{
			if(p.getWorld() != world || p.getLocation().distanceSquared(centre) > radius * radius)
				continue;
			Vector push = p.getLocation().toVector().subtract(centre.toVector()).setY(0);
			if(push.lengthSquared() < 0.01)
				push = new Vector(COMZombies.rand.nextDouble() - 0.5, 0, COMZombies.rand.nextDouble() - 0.5);
			p.setVelocity(push.normalize().multiply(knockback).setY(0.5));
			if(damage > 0)
				p.damage(damage);
		}
	}

	private static void grabAndTeleport(SpecialEnemyManager manager, ActiveSpecial a, Player target)
	{
		SpawnPoint point = randomOpenSpawn(manager);
		if(point == null)
			return;
		Location from = target.getLocation();
		Location to = point.getLocation().clone().add(0.5, 0, 0.5);
		to.setYaw(from.getYaw());
		to.setPitch(from.getPitch());
		from.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.3);
		target.teleport(to);
		to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.3);
		SoundUtil.play(to.getWorld(), to, "ENTITY_ENDERMAN_TELEPORT", SoundCategory.HOSTILE, 1.0f, 1.0f);
		target.sendMessage(COMZombies.PREFIX + ChatColor.RED + a.type.displayName + ChatColor.YELLOW + " grabbed you and threw you away!");
	}

	private static SpawnPoint randomOpenSpawn(SpecialEnemyManager manager)
	{
		List<SpawnPoint> open = new ArrayList<>();
		for(SpawnPoint point : manager.game.spawnManager.getPoints())
			if(manager.game.spawnManager.canSpawnPoint(point))
				open.add(point);
		return open.isEmpty() ? null : open.get(COMZombies.rand.nextInt(open.size()));
	}

	private static void stealPerkOnHit(SpecialEnemyManager manager, ActiveSpecial a, Player target)
	{
		List<PerkType> perks = manager.game.perkManager.getPlayersPerks(target);
		if(perks.isEmpty())
			return;
		PerkType perk = perks.get(COMZombies.rand.nextInt(perks.size()));
		manager.game.perkManager.removePerkEffect(target, perk);
		target.sendMessage(COMZombies.PREFIX + ChatColor.RED + a.type.displayName + ChatColor.YELLOW + " took your " + ChatColor.AQUA + perk + ChatColor.YELLOW + "!");
	}

	// --- napalm -----------------------------------------------------------------------------------------------

	private static void tickNapalm(SpecialEnemyManager manager, ActiveSpecial a)
	{
		Location loc = a.mob.getLocation().add(0, 0.8, 0);
		a.mob.getWorld().spawnParticle(Particle.FLAME, loc, 6, 0.3, 0.5, 0.3, 0.01);

		if(a.lastActionAt == 0)
		{
			if(manager.nearestPlayer(a.mob.getLocation(), a.type.option("trigger_distance", 2.5)) == null)
				return;
			a.lastActionAt = System.currentTimeMillis();
			SoundUtil.playKey(a.mob.getWorld(), loc, a.type.soundKey("attack"), "ENTITY_CREEPER_PRIMED", SoundCategory.HOSTILE, 1.0f, 0.8f);
			return;
		}
		a.mob.getWorld().spawnParticle(Particle.LAVA, loc, 3, 0.3, 0.3, 0.3, 0);
		if(System.currentTimeMillis() - a.lastActionAt >= a.type.option("fuse_seconds", 1.5) * 1000)
		{
			explode(manager, a);
			manager.selfDestruct(a);
		}
	}

	private static void explode(SpecialEnemyManager manager, ActiveSpecial a)
	{
		a.exploded = true;
		Location centre = a.mob.getLocation();
		World world = centre.getWorld();
		double radius = a.type.option("explode_radius", 4.0);
		double damage = a.type.option("explode_damage", 8.0);
		int fireTicks = (int) (a.type.option("fire_seconds", 4.0) * 20);

		world.spawnParticle(Compat.EXPLOSION, centre, 4, 0.8, 0.5, 0.8, 0);
		world.spawnParticle(Particle.FLAME, centre, 120, radius / 2, 0.6, radius / 2, 0.08);
		world.spawnParticle(Particle.LAVA, centre, 20, radius / 3, 0.3, radius / 3, 0);
		SoundUtil.playKey(world, centre, a.type.soundKey("explode"), "ENTITY_GENERIC_EXPLODE", SoundCategory.HOSTILE, 1.5f, 0.9f);
		for(Player p : manager.livingPlayers())
		{
			if(p.getWorld() != world || p.getLocation().distanceSquared(centre) > radius * radius)
				continue;
			if(manager.game.perkManager.hasPerk(p, PerkType.PHD_FLOPPER))
				continue;
			if(damage > 0)
				p.damage(damage);
			if(fireTicks > 0)
				p.setFireTicks(fireTicks);
		}
	}

	// --- shrieker ---------------------------------------------------------------------------------------------

	private static void tickShrieker(SpecialEnemyManager manager, ActiveSpecial a)
	{
		long now = System.currentTimeMillis();
		if(now - a.lastActionAt < a.type.option("scream_cooldown_seconds", 10.0) * 1000)
			return;
		if(manager.nearestPlayer(a.mob.getLocation(), a.type.option("trigger_distance", 6.0)) == null)
			return;
		a.lastActionAt = now;

		Location centre = a.mob.getLocation().add(0, 1.6, 0);
		World world = centre.getWorld();
		double radius = a.type.option("scream_radius", 8.0);
		int ticks = (int) (a.type.option("blind_seconds", 4.0) * 20);
		world.spawnParticle(Compat.SMOKE, centre, 40, 0.6, 0.4, 0.6, 0.05);
		SoundUtil.playKey(world, centre, a.type.soundKey("attack"), "ENTITY_GHAST_SCREAM", SoundCategory.HOSTILE, 2.0f, 0.7f);
		for(Player p : manager.livingPlayers())
		{
			if(p.getWorld() != world || p.getLocation().distanceSquared(centre) > radius * radius)
				continue;
			if(BLINDNESS != null)
				p.addPotionEffect(new PotionEffect(BLINDNESS, ticks, 0, false, false));
			if(Compat.DARKNESS != null)
				p.addPotionEffect(new PotionEffect(Compat.DARKNESS, ticks, 0, false, false));
		}
	}

	private static double horizontalDistanceSq(Location a, Location b)
	{
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
