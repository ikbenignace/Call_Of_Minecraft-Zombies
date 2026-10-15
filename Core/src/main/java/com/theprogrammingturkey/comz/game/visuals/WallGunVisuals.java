package com.theprogrammingturkey.comz.game.visuals;

import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.ModelCentres;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.Material;

/**
 * The chalk-outline wall gun: the weapon's item model lying flat against the wall behind its sign.
 */
public final class WallGunVisuals
{
	private static final float SCALE = 0.9f;
	/** Pack models are scaled so their longest side is this many blocks, about life size; small ones stay at SCALE. */
	private static final double WALL_LENGTH = 1.1;
	/** Pack gun models sit a little below the model origin; this puts unknown ones roughly mid-block. */
	private static final float CENTRE_LIFT = 0.16f * SCALE;
	/** Distance from the block centre towards the wall, so the thin model sits just off the surface. */
	private static final double WALL_INSET = 0.33;
	private static final Particle.DustOptions CHALK = new Particle.DustOptions(Color.WHITE, 0.6f);

	private WallGunVisuals()
	{
	}

	/**
	 * Yaw that makes the barrel run along the wall. Gun models have their barrel on the Z axis, so walls
	 * facing north/south need a quarter turn and walls facing east/west none.
	 */
	public static float yaw(BlockFace facing)
	{
		switch(facing)
		{
			case SOUTH:
				return 90f;
			case NORTH:
				return 270f;
			case WEST:
				return 180f;
			default:
				return 0f;
		}
	}

	/**
	 * @param signBlock the block the wall sign occupies
	 * @param facing    the way the sign (and so the visible gun side) faces, away from the wall
	 * @param raise     blocks to lift the model, used by build previews so the sign text stays readable
	 * @return the spawned display, or null when the server has no item displays
	 */
	public static Entity spawn(Location signBlock, BlockFace facing, BaseGun gun, boolean usePack, double raise)
	{
		if(gun == null || signBlock.getWorld() == null || !Displays.supportsItemDisplays())
			return null;
		ItemStack stack;
		if(usePack && PackModels.isPackEnabled() && gun.itemModel != null && !gun.itemModel.isEmpty())
		{
			stack = new ItemStack(Material.PAPER);
			PackModels.applyFull(stack, gun.itemModel);
		}
		else
		{
			stack = new ItemStack(gun.material == null ? gun.getWeaponType().getMaterial() : gun.material);
		}
		return spawn(signBlock, facing, stack, usePack ? gun.itemModel : null, raise);
	}

	/**
	 * Same wall placement for any item, used by equipment wall buys.
	 *
	 * @param packModel the pack item model shown, or null for a vanilla item
	 */
	public static Entity spawn(Location signBlock, BlockFace facing, ItemStack stack, String packModel, double raise)
	{
		if(stack == null || signBlock.getWorld() == null || !Displays.supportsItemDisplays())
			return null;
		Location at = signBlock.clone().add(0.5 - facing.getModX() * WALL_INSET, 0.5 + raise, 0.5 - facing.getModZ() * WALL_INSET);
		float scale = SCALE;
		double length = ModelCentres.length(packModel);
		if(length > 0)
			scale = (float) Math.min(SCALE, WALL_LENGTH / length);
		float lift = packModel != null ? CENTRE_LIFT * scale / SCALE : 0f;
		Vector shift = ModelCentres.shift(packModel, scale, yaw(facing));
		if(shift != null)
		{
			// Only along the wall and up: the depth stays at the inset so thick models don't sink into the wall.
			at.add(facing.getModX() == 0 ? shift.getX() : 0, shift.getY(), facing.getModZ() == 0 ? shift.getZ() : 0);
			lift = 0f;
		}
		Entity display = Displays.spawnItem(at, stack, scale, yaw(facing), lift, false);
		if(display != null)
			Displays.setBrightness(display, 12, 15);
		return display;
	}

	public static void sparkle(Entity model)
	{
		if(model == null || model.isDead())
			return;
		World world = model.getWorld();
		world.spawnParticle(Compat.DUST, model.getLocation().add(0, 0.1, 0), 2, 0.5, 0.25, 0.5, 0.0, CHALK);
	}
}
