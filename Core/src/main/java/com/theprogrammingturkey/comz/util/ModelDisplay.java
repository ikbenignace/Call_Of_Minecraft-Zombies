package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * Spawns item displays that render the COM:Z resource pack's 3D models (machines, box lid, throwables).
 *
 * <p>Gated behind {@link PackModels#isPackEnabled()}: with the pack on we spawn a display carrying a
 * custom {@code item_model}; otherwise the spawner returns {@code null} and the caller keeps its
 * vanilla visual (chest/sign/dropped item), so pack-less players never see a stray floating base item.
 */
public final class ModelDisplay
{
	/** Neutral base item for model carriers — invisible without a pack-applied {@code item_model}. */
	private static final Material BASE = Material.PAPER;

	private ModelDisplay()
	{
	}

	/**
	 * Spawns a fixed item display at {@code loc} rendering {@code comz:<modelKey>} at the given uniform
	 * {@code scale} and {@code yawDegrees}. Returns {@code null} when the pack is disabled, the server
	 * can't show display entities, or the world is null.
	 */
	public static Entity spawnModel(World world, Location loc, String modelKey, float scale, float yawDegrees)
	{
		if(world == null || !PackModels.isPackEnabled() || !Displays.supportsItemDisplays())
			return null;
		ItemStack stack = new ItemStack(BASE);
		PackModels.apply(stack, modelKey);
		return Displays.spawnItem(loc, stack, scale, yawDegrees, 0f, false);
	}
}
