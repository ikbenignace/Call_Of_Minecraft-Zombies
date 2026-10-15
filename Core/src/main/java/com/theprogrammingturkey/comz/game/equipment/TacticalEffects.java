package com.theprogrammingturkey.comz.game.equipment;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.ParticleFX;
import com.theprogrammingturkey.comz.util.SoundUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Thrown tactical equipment. The item flies like a grenade; after its fuse the behaviour named in the
 * catalogue runs where it landed.
 */
final class TacticalEffects
{
	private final Game game;
	private final EquipmentManager manager;

	TacticalEffects(Game game, EquipmentManager manager)
	{
		this.game = game;
		this.manager = manager;
	}

	void use(Player player, Equipment equipment)
	{
		Item item = player.getWorld().dropItem(player.getEyeLocation(), equipment.displayStack(true));
		item.setVelocity(player.getLocation().getDirection().multiply(1));
		item.setPickupDelay(Integer.MAX_VALUE);
		manager.track(item);
		SoundUtil.play(player.getWorld(), player.getLocation(), equipment.sound("throw"), SoundCategory.PLAYERS, 1, 1);

		manager.schedule(Math.max(1, equipment.fuseTicks), -1, () ->
		{
			Location at = item.getLocation();
			manager.untrack(item);
			if(!manager.isRunning() || !player.isOnline())
				return;
			switch(equipment.behaviour)
			{
				case "gersch":
					gersch(player, equipment, at);
					break;
				case "qed":
					qed(player, equipment, at);
					break;
				case "matryoshka":
					matryoshka(player, equipment, at, equipment.chain);
					break;
				default:
					explode(player, equipment, at);
					break;
			}
		});
	}

	private void explode(Player player, Equipment equipment, Location at)
	{
		SoundUtil.play(at.getWorld(), at, equipment.sound("explode"), SoundCategory.PLAYERS, 1.4f, 1);
		ParticleFX.blast(at.getWorld(), at);
		float damage = EquipmentManager.damageOf(equipment);
		for(Mob mob : EquipmentManager.zombiesNear(game, at, equipment.radius))
			if(!mob.isDead())
				game.damageMobExplosive(mob, player, damage);
	}

	/** Black hole: pulls zombies in for its duration and kills whatever reaches the centre. */
	private void gersch(Player player, Equipment equipment, Location at)
	{
		Location centre = at.clone().add(0, 0.5, 0);
		World world = centre.getWorld();
		SoundUtil.play(world, centre, equipment.sound("activate"), SoundCategory.PLAYERS, 1.5f, 1);
		final int[] elapsed = {0};
		final int[] task = {-1};
		task[0] = manager.schedule(0, 2, () ->
		{
			elapsed[0] += 2;
			if(!manager.isRunning() || !player.isOnline() || elapsed[0] > equipment.durationTicks)
			{
				manager.cancel(task[0]);
				if(manager.isRunning())
				{
					SoundUtil.play(world, centre, equipment.sound("explode"), SoundCategory.PLAYERS, 1.5f, 0.8f);
					world.spawnParticle(Particle.PORTAL, centre, 120, 0.5, 0.5, 0.5, 1.5);
				}
				return;
			}
			world.spawnParticle(Particle.PORTAL, centre, 40, 0.4, 0.4, 0.4, 1.0);
			world.spawnParticle(Compat.SMOKE, centre, 6, 0.2, 0.2, 0.2, 0.0);
			if(elapsed[0] % 20 == 0)
				SoundUtil.play(world, centre, equipment.sound("loop"), SoundCategory.PLAYERS, 1.0f, 0.6f);
			for(Mob mob : EquipmentManager.zombiesNear(game, centre, equipment.radius))
			{
				if(mob.isDead())
					continue;
				Vector pull = centre.toVector().subtract(mob.getLocation().toVector());
				if(pull.length() <= equipment.killRadius)
				{
					game.damageMob(mob, player, (float) mob.getHealth() + 1000f);
					continue;
				}
				mob.setVelocity(pull.normalize().multiply(equipment.pullStrength).setY(0.1));
			}
		});
	}

	/** Quantum Entanglement Device: one random effect from the catalogue's weighted list. */
	private void qed(Player player, Equipment equipment, Location at)
	{
		SoundUtil.play(at.getWorld(), at, equipment.sound("explode"), SoundCategory.PLAYERS, 1.2f, 1);
		at.getWorld().spawnParticle(Compat.WITCH, at, 40, 0.6, 0.6, 0.6, 0.1);
		String effect = pick(equipment);
		switch(effect)
		{
			case "powerup":
			{
				PowerUp[] choices = PowerUp.values();
				PowerUp powerUp = choices[1 + COMZombies.rand.nextInt(choices.length - 1)];
				Item anchor = at.getWorld().dropItem(at, new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE_BUTTON));
				game.powerUpManager.dropPowerUp(anchor, powerUp);
				anchor.remove();
				break;
			}
			case "revive_all":
				game.downedPlayerManager.reviveDownedPlayers();
				break;
			case "kill_nearby":
				for(Mob mob : EquipmentManager.zombiesNear(game, at, equipment.radius))
					if(!mob.isDead())
						game.damageMob(mob, player, (float) mob.getHealth() + 1000f);
				ParticleFX.blast(at.getWorld(), at);
				break;
			case "teleport_player":
				if(game.arena.getPlayerTPLocation() != null)
					player.teleport(game.arena.getPlayerTPLocation());
				break;
			case "refill_ammo":
				game.getPlayersWeapons(player).maxAmmo();
				break;
			default:
				break;
		}
		if(!effect.isEmpty())
			CommandUtil.actionBar(player, ChatColor.LIGHT_PURPLE + equipment.name + ": " + effect.replace('_', ' '));
	}

	private static String pick(Equipment equipment)
	{
		int total = 0;
		for(Equipment.WeightedEffect effect : equipment.effects)
			total += effect.weight;
		if(total <= 0)
			return "";
		int roll = COMZombies.rand.nextInt(total);
		for(Equipment.WeightedEffect effect : equipment.effects)
		{
			roll -= effect.weight;
			if(roll < 0)
				return effect.effect;
		}
		return "";
	}

	/** Each doll explodes, then the next one hops to a random nearby spot until the chain runs out. */
	private void matryoshka(Player player, Equipment equipment, Location at, int remaining)
	{
		explode(player, equipment, at);
		if(remaining <= 1)
			return;
		double angle = COMZombies.rand.nextDouble() * Math.PI * 2;
		double distance = 1 + COMZombies.rand.nextDouble() * Math.max(0, equipment.chainSpread - 1);
		Location next = at.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
		if(next.getBlock().getType().isSolid())
			next = at.clone();
		Item doll = at.getWorld().dropItem(at, equipment.displayStack(true));
		doll.setPickupDelay(Integer.MAX_VALUE);
		doll.setVelocity(next.toVector().subtract(at.toVector()).multiply(0.15).setY(0.35));
		manager.track(doll);
		manager.schedule(equipment.chainDelayTicks, -1, () ->
		{
			Location landed = doll.getLocation();
			manager.untrack(doll);
			if(manager.isRunning() && player.isOnline())
				matryoshka(player, equipment, landed, remaining - 1);
		});
	}
}
