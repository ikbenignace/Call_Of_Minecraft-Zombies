package com.theprogrammingturkey.comz.util.display;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * Server-version seam for display entities. Typed with plain {@link Entity} so Core never links the
 * 1.19.4+ display classes; spawners return {@code null} when the server can't show the visual.
 */
public interface DisplayBackend
{
	boolean supportsItemDisplays();

	boolean supportsHitboxes();

	/**
	 * @param offsetY       translation above {@code loc}, applied client-side
	 * @param faceViewer    billboard towards the viewer instead of a fixed orientation
	 */
	Entity spawnItem(Location loc, ItemStack stack, float scale, float yawDegrees, float offsetY, boolean faceViewer);

	/** Interpolates a spawned item display to a new translation/scale/yaw. No-op for other entities. */
	void animate(Entity display, float x, float y, float z, float scale, float yawDegrees, int durationTicks);

	Entity spawnText(Location loc, String text, boolean seeThrough);

	/** An invisible right-clickable box. */
	Entity spawnHitbox(Location loc, float width, float height);
}
