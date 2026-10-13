package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Floating text used by the BO2-fidelity visual feedback layer (e.g. "+60" point pop-ups, round/perk
 * callouts, price holograms). Text displays on 1.19.4+, name-tagged armour stands before that.
 */
public final class DisplayEntityUtil
{
	private DisplayEntityUtil()
	{
	}

	/**
	 * Spawns a see-through floating text at {@code loc} that auto-removes after {@code lifeTicks} ticks.
	 * Null if the world is null.
	 */
	public static Entity floatingText(World world, Location loc, String text, int lifeTicks)
	{
		if(world == null)
			return null;
		Entity td = Displays.spawnText(loc, text, true);
		if(td == null)
			return null;
		td.setPersistent(false);
		COMZombies.scheduleTask(lifeTicks, () ->
		{
			if(!td.isDead())
				td.remove();
		});
		return td;
	}

	/**
	 * Spawns a non-expiring floating text — used for machine price holograms. The caller owns its
	 * lifetime and must {@code remove()} it. Null if the world is null.
	 */
	public static Entity persistentText(World world, Location loc, String text)
	{
		if(world == null)
			return null;
		return Displays.spawnText(loc, text, false);
	}
}
