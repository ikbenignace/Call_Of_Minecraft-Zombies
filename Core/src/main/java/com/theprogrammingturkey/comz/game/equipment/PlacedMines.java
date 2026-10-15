package com.theprogrammingturkey.comz.game.equipment;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.EntitySpawner;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.util.ParticleFX;
import com.theprogrammingturkey.comz.util.SoundUtil;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mines placed by players. One scan task per game runs while any mine is down: an armed mine fires when a
 * zombie enters its trigger area (the front cone for directional mines, a circle otherwise), optionally pops
 * up, then hits every zombie in its damage area after the fuse.
 */
final class PlacedMines
{
	private static final long SCAN_PERIOD = 2L;
	/** Mines ignore zombies for this long after placement so a mine dropped under a zombie is not wasted. */
	private static final int ARM_TICKS = 20;
	private static final double POP_HEIGHT = 1.2;

	private final Game game;
	private final EquipmentManager manager;
	private final List<Mine> mines = new ArrayList<>();
	private int scanTask = -1;

	PlacedMines(Game game, EquipmentManager manager)
	{
		this.game = game;
		this.manager = manager;
	}

	private static final class Mine
	{
		final UUID owner;
		final Equipment equipment;
		final Location centre;
		final Vector facing;
		Entity model;
		int age;
		boolean triggered;

		Mine(UUID owner, Equipment equipment, Location centre, Vector facing)
		{
			this.owner = owner;
			this.equipment = equipment;
			this.centre = centre;
			this.facing = facing;
		}
	}

	int countPlaced(Player player, Equipment equipment)
	{
		int count = 0;
		for(Mine mine : mines)
			if(mine.owner.equals(player.getUniqueId()) && mine.equipment == equipment && !mine.triggered)
				count++;
		return count;
	}

	boolean place(Player player, Equipment equipment, Location at, float yaw)
	{
		World world = at.getWorld();
		if(world == null)
			return false;
		Vector facing = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
		Mine mine = new Mine(player.getUniqueId(), equipment, at.clone(), facing);
		mine.model = spawnModel(equipment, at, yaw);
		manager.track(mine.model);
		mines.add(mine);
		SoundUtil.play(world, at, equipment.sound("place"), SoundCategory.PLAYERS, 1, 1);
		ensureScanning();
		return true;
	}

	private static Entity spawnModel(Equipment equipment, Location at, float yaw)
	{
		boolean usePack = PackModels.isPackEnabled() && equipment.hasPackModel();
		if(Displays.supportsItemDisplays())
		{
			Entity display = Displays.spawnItem(at.clone().add(0, 0.25, 0), equipment.displayStack(usePack), 0.6f, -yaw, 0f, false);
			if(display != null)
				return display;
		}
		Location standLoc = at.clone().add(0, -1.2, 0);
		standLoc.setYaw(yaw);
		return EntitySpawner.spawn(at.getWorld(), standLoc, ArmorStand.class, stand ->
		{
			stand.setVisible(false);
			stand.setGravity(false);
			stand.setInvulnerable(true);
			stand.setSmall(false);
			stand.setMarker(true);
			stand.setPersistent(false);
			stand.setHelmet(equipment.displayStack(usePack));
		});
	}

	private void ensureScanning()
	{
		if(scanTask != -1)
			return;
		scanTask = manager.schedule(SCAN_PERIOD, SCAN_PERIOD, this::scan);
	}

	private void stopScanning()
	{
		manager.cancel(scanTask);
		scanTask = -1;
	}

	private void scan()
	{
		if(!manager.isRunning() || mines.isEmpty())
		{
			if(!manager.isRunning())
				removeAll();
			stopScanning();
			return;
		}
		for(Mine mine : new ArrayList<>(mines))
		{
			mine.age += SCAN_PERIOD;
			if(mine.triggered || mine.age < ARM_TICKS)
				continue;
			if(!EquipmentManager.zombiesNear(game, mine.centre, mine.equipment.triggerRadius).stream().anyMatch(mob -> inArea(mine, mob.getLocation())))
				continue;
			trigger(mine);
		}
	}

	private static boolean inArea(Mine mine, Location target)
	{
		if(!mine.equipment.coneTrigger || mine.equipment.coneAngle >= 360)
			return true;
		Vector to = target.toVector().subtract(mine.centre.toVector()).setY(0);
		if(to.lengthSquared() < 0.01)
			return true;
		double angle = Math.toDegrees(to.angle(mine.facing));
		return angle <= mine.equipment.coneAngle / 2.0;
	}

	private void trigger(Mine mine)
	{
		mine.triggered = true;
		Equipment equipment = mine.equipment;
		World world = mine.centre.getWorld();
		SoundUtil.play(world, mine.centre, equipment.sound("trigger"), SoundCategory.PLAYERS, 1.2f, 1);
		if(equipment.popUp && mine.model != null && !mine.model.isDead())
			mine.model.teleport(mine.model.getLocation().add(0, POP_HEIGHT, 0));
		manager.schedule(Math.max(1, equipment.fuseTicks), -1, () -> explode(mine));
	}

	private void explode(Mine mine)
	{
		if(!mines.remove(mine))
			return;
		manager.untrack(mine.model);
		if(!manager.isRunning())
			return;
		Equipment equipment = mine.equipment;
		Location blastAt = mine.centre.clone().add(0, equipment.popUp ? POP_HEIGHT : 0.3, 0);
		World world = blastAt.getWorld();
		SoundUtil.play(world, blastAt, equipment.sound("explode"), SoundCategory.PLAYERS, 1.5f, 1);
		if("spikes".equals(equipment.effect))
			spikes(mine, blastAt);
		else
			ParticleFX.blast(world, blastAt);

		Player owner = Bukkit.getPlayer(mine.owner);
		if(owner == null || !game.getPlayersInGame().contains(owner))
			return;
		float damage = EquipmentManager.damageOf(equipment);
		for(Mob mob : EquipmentManager.zombiesNear(game, mine.centre, equipment.damageRadius))
			if(inArea(mine, mob.getLocation()) && !mob.isDead())
				game.damageMob(mob, owner, damage);
	}

	private static void spikes(Mine mine, Location from)
	{
		World world = from.getWorld();
		if(world == null)
			return;
		double half = Math.toRadians(Math.min(mine.equipment.coneAngle, 180) / 2.0);
		for(int ray = 0; ray < 9; ray++)
		{
			Vector dir = mine.facing.clone().rotateAroundY(-half + (2 * half) * ray / 8.0).normalize();
			for(double d = 0.5; d <= mine.equipment.damageRadius; d += 0.5)
				world.spawnParticle(Particle.CRIT, from.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
		}
		world.spawnParticle(Compat.SMOKE, from, 10, 0.3, 0.2, 0.3, 0.02);
	}

	void removeOwnedBy(UUID owner)
	{
		for(Mine mine : new ArrayList<>(mines))
			if(mine.owner.equals(owner))
			{
				mines.remove(mine);
				manager.untrack(mine.model);
			}
	}

	void removeAll()
	{
		for(Mine mine : mines)
			manager.untrack(mine.model);
		mines.clear();
		stopScanning();
	}
}
