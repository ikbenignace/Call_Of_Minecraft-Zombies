package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.managers.PerkManager;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.game.weapons.WeaponInstance;
import com.theprogrammingturkey.comz.listeners.PowerUpDropListener;
import com.theprogrammingturkey.comz.util.DisplayEntityUtil;
import com.theprogrammingturkey.comz.util.EntitySpawner;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tombstone Soda drop (BO2 rules): when a player holding the perk bleeds out, their body stays where
 * they went down. After they respawn they walk over it to get back the perks they had (minus
 * Tombstone itself) and their guns. The tombstone fades a while after its owner is back in play.
 */
public class Tombstone
{
	public static final String ENTITY_TAG = "comz_tombstone";

	private static final double PICKUP_RANGE_SQ = 1.6 * 1.6;
	private static final int TICK = 5;

	private final Game game;
	private final UUID ownerId;
	private final String ownerName;
	private final Location location;
	private final List<PerkType> perks;
	private final WeaponInstance[] guns;

	private ArmorStand anchor;
	private Entity body;
	private Entity label;
	private int task = -1;
	private int ticksLeft;

	public Tombstone(Game game, Player owner, Location location, List<PerkType> perks, WeaponInstance[] guns)
	{
		this.game = game;
		this.ownerId = owner.getUniqueId();
		this.ownerName = owner.getName();
		this.location = location.clone();
		this.perks = new ArrayList<>(perks);
		this.guns = guns.clone();
		this.ticksLeft = ConfigManager.getMainConfig().tombstoneLifetime * 20;
	}

	public UUID getOwnerId()
	{
		return ownerId;
	}

	public void spawn()
	{
		World world = location.getWorld();
		if(world == null)
			return;

		anchor = EntitySpawner.spawn(world, location, ArmorStand.class, as ->
		{
			as.setVisible(false);
			as.setGravity(false);
			as.setInvulnerable(true);
			as.setSmall(true);
			as.setBasePlate(false);
			as.setCollidable(false);
			as.setSilent(true);
			as.setPersistent(false);
		});
		body = spawnMannequin();
		if(body == null)
			anchor.getEquipment().setHelmet(ownerHead());

		label = DisplayEntityUtil.persistentText(world, location.clone().add(0, 1.1, 0),
				ChatColor.GRAY + "✝ " + ChatColor.WHITE + ownerName + ChatColor.GRAY + "'s Tombstone");
		task = COMZombies.scheduleTask(TICK, TICK, this::tick);
	}

	/**
	 * Mannequins (MC 1.21.9+) show the owner's real skin lying on the floor. They have no Bukkit API on
	 * the versions Core compiles against, so they are summoned through the vanilla command, anchored
	 * on the armour stand so it lands in the right world.
	 */
	private Entity spawnMannequin()
	{
		if(anchor == null || !hasEntityType("MANNEQUIN"))
			return null;
		String tag = "comz_tomb_" + ownerId;
		String nbt = "{profile:\"" + ownerName + "\",pose:\"swimming\",immovable:1b,hide_description:1b,"
				+ "Invulnerable:1b,Silent:1b,Tags:[\"" + ENTITY_TAG + "\",\"" + tag + "\"]}";
		try
		{
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
					"execute at " + anchor.getUniqueId() + " run summon minecraft:mannequin ~ ~ ~ " + nbt);
		} catch(Exception ignored)
		{
			return null;
		}
		for(Entity e : anchor.getNearbyEntities(1, 1, 1))
			if(e.getScoreboardTags().contains(tag))
				return e;
		return null;
	}

	private static boolean hasEntityType(String name)
	{
		try
		{
			for(EntityType type : EntityType.values())
				if(type.name().equals(name))
					return true;
		} catch(Throwable ignored)
		{
		}
		return false;
	}

	/** The mannequin is found by a proximity lookup after a console summon; sweep by tag in case that missed it. */
	private void sweepTaggedEntities()
	{
		World world = location.getWorld();
		if(world == null)
			return;
		String tag = "comz_tomb_" + ownerId;
		try
		{
			for(Entity e : world.getEntities())
				if(!e.isDead() && e.getScoreboardTags().contains(tag))
					e.remove();
		} catch(RuntimeException ignored)
		{
		}
	}

	private ItemStack ownerHead()
	{
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		if(meta != null)
		{
			meta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerId));
			head.setItemMeta(meta);
		}
		return head;
	}

	private void tick()
	{
		Player owner = Bukkit.getPlayer(ownerId);
		if(owner == null || !game.isPlayerPlaying(owner) || game.downedPlayerManager.isDownedPlayer(owner))
			return;

		if(owner.getWorld().equals(location.getWorld()) && owner.getLocation().distanceSquared(location) <= PICKUP_RANGE_SQ)
		{
			reclaim(owner);
			return;
		}

		ticksLeft -= TICK;
		if(ticksLeft <= 0)
		{
			owner.sendMessage(ChatColor.GRAY + "Your tombstone has faded.");
			game.downedPlayerManager.removeTombstone(this);
		}
	}

	private void reclaim(Player owner)
	{
		// Perks first: Mule Kick changes how many gun slots the guns below may use.
		for(PerkType perk : perks)
			PerkManager.givePerk(game, owner, perk);

		PlayerWeaponManager manager = game.getPlayersWeapons(owner);
		for(WeaponInstance gun : guns)
		{
			if(gun == null)
				continue;
			gun.setSlot(reclaimSlot(owner, manager, gun));
			WeaponInstance occupant = manager.getWeapon(gun.getSlot());
			if(occupant != null)
			{
				if(PowerUpDropListener.isDeathMachine(occupant))
					PowerUpDropListener.revertDeathMachine(game, owner);
				manager.removeWeapon(gun.getSlot());
			}
			manager.addWeapon(gun);
		}
		owner.sendMessage(ChatColor.GREEN + "You reclaimed your tombstone.");
		game.downedPlayerManager.removeTombstone(this);
	}

	/** Prefers the original slot when empty or holding the respawn gun, then any free slot, then the respawn gun, then the original slot. */
	private int reclaimSlot(Player owner, PlayerWeaponManager manager, WeaponInstance gun)
	{
		int original = gun.getSlot();
		WeaponInstance atOriginal = manager.getWeapon(original);
		if(atOriginal == null || isStartingGun(atOriginal))
			return original;

		int gunSlots = game.perkManager.hasPerk(owner, PerkType.MULE_KICK) ? 3 : 2;
		for(int slot = 1; slot <= gunSlots; slot++)
			if(manager.getWeapon(slot) == null)
				return slot;
		for(int slot = 1; slot <= gunSlots; slot++)
			if(isStartingGun(manager.getWeapon(slot)))
				return slot;
		return original;
	}

	private boolean isStartingGun(WeaponInstance weapon)
	{
		return weapon instanceof GunInstance
				&& !((GunInstance) weapon).isPackOfPunched()
				&& ((GunInstance) weapon).getType().getName().equals(game.getStartingGun());
	}

	public void remove()
	{
		if(task != -1)
		{
			Bukkit.getScheduler().cancelTask(task);
			task = -1;
		}
		for(Entity e : new Entity[]{body, label, anchor})
			if(e != null && !e.isDead())
				e.remove();
		sweepTaggedEntities();
		body = label = anchor = null;
	}
}
