package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Recognises helmets that let a player breathe inside a no-air zone. An item counts when it carries the
 * plugin's breathing-gear tag (set via {@link #tag(ItemStack)}) or its material is listed in
 * {@code config.environment.breathingGearMaterials}.
 */
public final class BreathingGear
{
	private static NamespacedKey key;

	private BreathingGear()
	{
	}

	private static NamespacedKey key()
	{
		if(key == null)
			key = new NamespacedKey(COMZombies.getPlugin(), "breathing_gear");
		return key;
	}

	public static boolean isBreathingGear(ItemStack stack)
	{
		if(stack == null || stack.getType().isAir())
			return false;
		List<String> materials = ConfigManager.getMainConfig().breathingGearMaterials;
		if(materials != null)
			for(String name : materials)
				if(Material.matchMaterial(name) == stack.getType())
					return true;
		ItemMeta meta = stack.getItemMeta();
		return meta != null && meta.getPersistentDataContainer().has(key(), PersistentDataType.BYTE);
	}

	public static boolean isWearing(Player player)
	{
		return isBreathingGear(player.getInventory().getHelmet());
	}

	/** Marks {@code stack} as breathing gear in place and returns it. */
	public static ItemStack tag(ItemStack stack)
	{
		ItemMeta meta = stack.getItemMeta();
		if(meta == null)
			return stack;
		meta.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
		stack.setItemMeta(meta);
		return stack;
	}
}
