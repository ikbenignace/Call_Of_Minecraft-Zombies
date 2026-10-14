package com.theprogrammingturkey.comz.util.display;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
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

	boolean supportsBlockDisplays();

	/**
	 * @param offsetY       translation above {@code loc}, applied client-side
	 * @param faceViewer    billboard towards the viewer instead of a fixed orientation
	 */
	Entity spawnItem(Location loc, ItemStack stack, float scale, float yawDegrees, float offsetY, boolean faceViewer);

	/**
	 * A single block box. {@code (cx, cy, cz)} is the box's minimum corner and {@code (sx, sy, sz)} its size,
	 * both in blocks relative to {@code loc} before rotation; the whole box is then turned by
	 * {@code yawDegrees} around the vertical axis through {@code loc}, so several parts spawned at the same
	 * {@code loc} and yaw turn together as one composite.
	 */
	Entity spawnBlock(Location loc, BlockData block, float cx, float cy, float cz, float sx, float sy, float sz, float yawDegrees, boolean glow);

	/** Interpolates a spawned item display to a new translation/scale/yaw. No-op for other entities. */
	void animate(Entity display, float x, float y, float z, float scale, float yawDegrees, int durationTicks);

	/** Overrides the light a display is drawn with; a negative {@code block} restores the world light. No-op for other entities. */
	void setBrightness(Entity display, int block, int sky);

	Entity spawnText(Location loc, String text, boolean seeThrough);

	/** An invisible right-clickable box. */
	Entity spawnHitbox(Location loc, float width, float height);
}
