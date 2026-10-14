package com.theprogrammingturkey.comz.util.display;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.util.ServerFeatures;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

public final class Displays
{
	private static final String MODERN_BACKEND = "com.theprogrammingturkey.comz.modern.ModernDisplayBackend";

	private static DisplayBackend backend = new LegacyDisplayBackend();

	private Displays()
	{
	}

	public static void init()
	{
		if(!ServerFeatures.DISPLAY_ENTITIES)
			return;
		try
		{
			backend = (DisplayBackend) Class.forName(MODERN_BACKEND).getDeclaredConstructor().newInstance();
		} catch(ReflectiveOperationException | LinkageError e)
		{
			COMZombies.log.warning(COMZombies.CONSOLE_PREFIX + "Could not load display entity support, falling back to armour stands: " + e);
		}
	}

	public static boolean supportsItemDisplays()
	{
		return backend.supportsItemDisplays();
	}

	public static boolean supportsHitboxes()
	{
		return backend.supportsHitboxes();
	}

	public static boolean supportsBlockDisplays()
	{
		return backend.supportsBlockDisplays();
	}

	public static Entity spawnBlock(Location loc, BlockData block, float cx, float cy, float cz, float sx, float sy, float sz, float yawDegrees, boolean glow)
	{
		if(loc.getWorld() == null || block == null)
			return null;
		return nonPersistent(backend.spawnBlock(loc, block, cx, cy, cz, sx, sy, sz, yawDegrees, glow));
	}

	public static Entity spawnItem(Location loc, ItemStack stack, float scale, float yawDegrees, float offsetY, boolean faceViewer)
	{
		if(loc.getWorld() == null)
			return null;
		return nonPersistent(backend.spawnItem(loc, stack, scale, yawDegrees, offsetY, faceViewer));
	}

	public static void setItem(Entity display, ItemStack stack)
	{
		if(display == null || display.isDead() || stack == null)
			return;
		backend.setItem(display, stack);
	}

	public static void setBrightness(Entity display, int block, int sky)
	{
		if(display == null || display.isDead())
			return;
		backend.setBrightness(display, block, sky);
	}

	public static void clearBrightness(Entity display)
	{
		setBrightness(display, -1, -1);
	}

	public static void animate(Entity display, float x, float y, float z, float scale, float yawDegrees, int durationTicks)
	{
		if(display == null || display.isDead())
			return;
		backend.animate(display, x, y, z, scale, yawDegrees, durationTicks);
	}

	public static Entity spawnText(Location loc, String text, boolean seeThrough)
	{
		if(loc.getWorld() == null)
			return null;
		return nonPersistent(backend.spawnText(loc, text, seeThrough));
	}

	public static Entity spawnHitbox(Location loc, float width, float height)
	{
		if(loc.getWorld() == null)
			return null;
		return nonPersistent(backend.spawnHitbox(loc, width, height));
	}

	// Visual entities are rebuilt every game, so a copy saved with the chunk would only be an orphan after a restart.
	private static Entity nonPersistent(Entity entity)
	{
		if(entity != null)
			entity.setPersistent(false);
		return entity;
	}
}
