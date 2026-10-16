package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.equipment.EquipmentCatalogue;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.game.weapons.Weapon;
import com.theprogrammingturkey.comz.game.weapons.WeaponInstance;
import com.theprogrammingturkey.comz.game.weapons.WeaponType;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlayerWeaponManager
{
	private final List<WeaponInstance> weapons = new ArrayList<>();
	private final Player player;

	public PlayerWeaponManager(Player player)
	{
		this.player = player;
	}

	/**
	 * @return List of guns in the manager
	 */
	public List<WeaponInstance> getWeapons()
	{
		return weapons;
	}

	/**
	 * Gets a slot to put the gun in.
	 *
	 * @return Slot to put a gun in.
	 */
	public int getCorrectSlot(Weapon weapon)
	{
		int slot = player.getInventory().getHeldItemSlot();

		switch(weapon.getWeaponType())
		{
			case GRENADE:
				slot = 8;
				break;
			case MONKEY_BOMB:
				slot = EquipmentCatalogue.tacticalSlot >= 0 ? EquipmentCatalogue.tacticalSlot : 7;
				break;
			case ASSAULT_RIFLES:
			case SUB_MACHINE_GUNS:
			case LIGHT_MACHINE_GUNS:
			case PISTOLS:
			case SNIPER_RIFLES:
			case SHOTGUNS:
			case SPECIAL:
				if(GameManager.INSTANCE.isPlayerInGame(player))
				{
					Game game = GameManager.INSTANCE.getGame(player);
					if(game.perkManager.hasPerk(player, PerkType.MULE_KICK))
					{
						if(slot > 3)
							slot = 3;
						else if(slot < 1)
							slot = 1;
					}
					else
					{
						if(slot > 2)
							slot = 2;
						if(slot < 1)
							slot = 1;
					}

					ItemStack slotStack = player.getInventory().getItem(slot);
					int tempSlot = slot;
					while(slotStack != null && !slotStack.getType().equals(Material.AIR))
					{
						tempSlot++;
						if(game.perkManager.hasPerk(player, PerkType.MULE_KICK) && tempSlot == 4)
							tempSlot = 1;
						else if(!game.perkManager.hasPerk(player, PerkType.MULE_KICK) && tempSlot == 3)
							tempSlot = 1;

						slotStack = player.getInventory().getItem(tempSlot);
						if(tempSlot == slot)
							break;
					}
					slot = tempSlot;
				}
				break;
		}

		return slot;
	}

	public void addWeapon(Weapon weapon)
	{
		int slot = this.getCorrectSlot(weapon);
		detach(getWeapon(slot));
		addWeapon(weapon.getNewInstance(player, slot));
	}

	/**
	 * Adds a weapon to the array list of weapons
	 *
	 * @param weapon : Weapon to add
	 */
	public void addWeapon(WeaponInstance weapon)
	{
		if(weapon == null)
			return;
		weapon.setDetached(false);
		weapons.add(weapon);
		weapon.updateWeapon();
		player.updateInventory();
	}

	public void updateWeapons() {
		for (WeaponInstance weapon : weapons) {
			weapon.updateWeapon();
			player.updateInventory();
		}
	}

	/**
	 * Used to check if the item the player is holding is a gun
	 *
	 * @return if the item in the players hand is a gun.
	 */
	public boolean isHeldItemGun()
	{
		for(WeaponInstance weapon : weapons)
			if(weapon instanceof GunInstance && weapon.getSlot() == player.getInventory().getHeldItemSlot())
				return true;
		return false;
	}

	/**
	 * Used to check if the item the player is holding is a weapon
	 *
	 * @return if the item in the players hand is a weapon.
	 */
	public boolean isHeldItemWeapon()
	{
		for(WeaponInstance weapon : weapons)
			if(weapon.getSlot() == player.getInventory().getHeldItemSlot())
				return true;
		return false;
	}

	/**
	 * Used to get a gun based off of a slot in the players inventory
	 *
	 * @param slot : Slot number gun is located
	 * @return Gun held in that slot
	 */
	public GunInstance getGun(int slot)
	{
		for(WeaponInstance weapon : weapons)
			if(weapon instanceof GunInstance && weapon.getSlot() == slot)
				return (GunInstance) weapon;
		return null;
	}

	public WeaponInstance getWeapon(int slot)
	{
		for(WeaponInstance weapon : weapons)
			if(weapon.getSlot() == slot)
				return weapon;
		return null;
	}

	public GunInstance getGun(BaseGun gun)
	{
		for(WeaponInstance weapon : weapons)
			if(weapon instanceof GunInstance && ((GunInstance) weapon).getType().equals(gun))
				return (GunInstance) weapon;
		return null;
	}

	/**
	 * Used to remove a gun from the guns list if contained.
	 *
	 * @param gun : Gun to remove from the list.
	 */
	public void removeWeapon(WeaponInstance gun)
	{
		detach(gun);
	}

	private void detach(WeaponInstance weapon)
	{
		if(weapon == null)
			return;
		weapons.remove(weapon);
		weapon.setDetached(true);
	}

	public WeaponInstance removeWeapon(int slot)
	{
		for(int i = weapons.size() - 1; i >= 0; i--)
			if(weapons.get(i).getSlot() == slot)
			{
				WeaponInstance removed = weapons.remove(i);
				removed.setDetached(true);
				return removed;
			}
		return null;
	}

	public boolean hasGun(BaseGun gun)
	{
		for(WeaponInstance weapon : weapons)
			if(weapon instanceof GunInstance && ((GunInstance) weapon).getType().equals(gun))
				return true;
		return false;
	}

	public boolean hasFullGrenades()
	{
		WeaponInstance grenades = getWeaponOfType(WeaponType.GRENADE);
		return grenades != null && grenades.totalAmmo >= ConfigManager.getMainConfig().maxGrenades;
	}

	public WeaponInstance getWeaponOfType(WeaponType type)
	{
		for(WeaponInstance weapon : weapons)
			if(weapon.getBaseWeapon().getWeaponType() == type)
				return weapon;
		return null;
	}

	/** Round refill: tops lethal grenades up by {@code amount}, never past {@code cap}. */
	public void addGrenades(int amount, int cap)
	{
		if(amount <= 0 || cap <= 0)
			return;
		WeaponInstance grenades = getWeaponOfType(WeaponType.GRENADE);
		if(grenades == null)
		{
			Weapon grenade = WeaponManager.getWeapon("Grenade");
			if(grenade == null)
				return;
			grenades = grenade.getNewInstance(player, getCorrectSlot(grenade));
			grenades.totalAmmo = 0;
			detach(getWeapon(grenades.getSlot()));
			weapons.add(grenades);
		}
		if(grenades.totalAmmo >= cap)
			return;
		grenades.totalAmmo = Math.min(cap, grenades.totalAmmo + amount);
		grenades.updateWeapon();
		player.updateInventory();
	}

	/**
	 * Spends one throwable from the weapon in {@code slot}. The weapon's own count is the source of truth,
	 * otherwise the next {@link WeaponInstance#updateWeapon()} (F, inventory clicks) would restore the stack.
	 *
	 * @return false when that slot holds no grenade / Monkey Bomb weapon
	 */
	public boolean consumeThrowable(int slot)
	{
		WeaponInstance weapon = getWeapon(slot);
		if(weapon == null || weapon instanceof GunInstance)
			return false;
		WeaponType type = weapon.getBaseWeapon().getWeaponType();
		if(type != WeaponType.GRENADE && type != WeaponType.MONKEY_BOMB)
			return false;
		weapon.totalAmmo = Math.max(0, weapon.totalAmmo - 1);
		if(weapon.totalAmmo == 0)
		{
			detach(weapon);
			player.getInventory().setItem(slot, null);
		}
		else
		{
			weapon.updateWeapon();
		}
		return true;
	}

	public void maxAmmo()
	{
		for(WeaponInstance weapon : weapons)
			weapon.maxAmmo();
	}
}
