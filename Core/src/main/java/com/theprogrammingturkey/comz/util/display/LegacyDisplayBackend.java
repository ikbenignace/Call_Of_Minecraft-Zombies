package com.theprogrammingturkey.comz.util.display;

import com.theprogrammingturkey.comz.util.EntitySpawner;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * Pre-1.19.4 fallback: floating text becomes an invisible marker armour stand with a visible name, and
 * item models, animations and hitboxes are unavailable.
 */
public class LegacyDisplayBackend implements DisplayBackend
{
	@Override
	public boolean supportsItemDisplays()
	{
		return false;
	}

	@Override
	public boolean supportsHitboxes()
	{
		return false;
	}

	@Override
	public boolean supportsBlockDisplays()
	{
		return false;
	}

	@Override
	public Entity spawnBlock(Location loc, BlockData block, float cx, float cy, float cz, float sx, float sy, float sz, float yawDegrees, boolean glow)
	{
		return null;
	}

	@Override
	public Entity spawnItem(Location loc, ItemStack stack, float scale, float yawDegrees, float offsetY, boolean faceViewer)
	{
		return null;
	}

	@Override
	public void animate(Entity display, float x, float y, float z, float scale, float yawDegrees, int durationTicks)
	{
	}

	@Override
	public void setItem(Entity display, ItemStack stack)
	{
	}

	@Override
	public void setBrightness(Entity display, int block, int sky)
	{
	}

	@Override
	public Entity spawnText(Location loc, String text, boolean seeThrough)
	{
		World world = loc.getWorld();
		if(world == null)
			return null;
		return EntitySpawner.spawn(world, loc, ArmorStand.class, stand ->
		{
			stand.setVisible(false);
			stand.setMarker(true);
			stand.setGravity(false);
			stand.setInvulnerable(true);
			stand.setSilent(true);
			stand.setCustomName(text);
			stand.setCustomNameVisible(true);
		});
	}

	@Override
	public Entity spawnHitbox(Location loc, float width, float height)
	{
		return null;
	}
}
