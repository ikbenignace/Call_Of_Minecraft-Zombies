package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.util.Compat;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.util.logging.Level;

/**
 * Black Ops health outgrows the server's max-health attribute cap (2048 on Spigot/Paper) in the late
 * rounds. A mob's real health is clamped to {@code zombieHealthCap}; the remaining "virtual" BO health
 * is kept by scaling every hit with {@code realMax / virtualMax}.
 */
public final class ZombieHealth
{
	public static final String DAMAGE_SCALE_META = "comz_damage_scale";

	private ZombieHealth()
	{
	}

	public static double applyHealth(LivingEntity mob, double virtualMax)
	{
		return applyHealth(mob, virtualMax, virtualMax);
	}

	/**
	 * Sets the mob's max health to {@code virtualMax} BO health and its current health to
	 * {@code virtualCurrent}. Never throws, so a spawned mob can always be registered.
	 *
	 * @return the real max health the mob ended up with
	 */
	public static double applyHealth(LivingEntity mob, double virtualMax, double virtualCurrent)
	{
		if(virtualMax <= 0)
			virtualMax = 1;
		double cap = Math.max(1, ConfigManager.getMainConfig().zombieHealthCap);
		double realMax = Math.min(virtualMax, cap);
		try
		{
			AttributeInstance attr = Compat.MAX_HEALTH == null ? null : mob.getAttribute(Compat.MAX_HEALTH);
			if(attr != null)
			{
				attr.setBaseValue(realMax);
				realMax = Math.min(realMax, attr.getValue());
			}
			else
			{
				realMax = Math.min(realMax, mob.getMaxHealth());
			}
			double scale = realMax / virtualMax;
			mob.setMetadata(DAMAGE_SCALE_META, new FixedMetadataValue(COMZombies.getPlugin(), scale));
			double realCurrent = Math.max(0.5, Math.min(realMax, virtualCurrent * scale));
			mob.setHealth(realCurrent);
		}
		catch(RuntimeException e)
		{
			COMZombies.log.log(Level.WARNING, "Could not set health " + virtualMax + " on " + mob.getType(), e);
		}
		return realMax;
	}

	/** Real health per point of BO health; 1 for mobs that were never capped. */
	public static double damageScale(Entity mob)
	{
		if(mob == null)
			return 1;
		for(MetadataValue mv : mob.getMetadata(DAMAGE_SCALE_META))
			if(mv.getOwningPlugin() == COMZombies.getPlugin())
				return mv.asDouble() > 0 ? mv.asDouble() : 1;
		return 1;
	}

	public static double scaledDamage(Entity mob, double virtualDamage)
	{
		return virtualDamage * damageScale(mob);
	}

	public static double virtualHealth(LivingEntity mob)
	{
		return mob.getHealth() / damageScale(mob);
	}
}
