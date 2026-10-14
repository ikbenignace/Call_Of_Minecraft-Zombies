package com.theprogrammingturkey.comz.game.visuals;

import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * The chalk-outline wall gun: the weapon's item model lying flat against the wall behind its sign.
 */
public final class WallGunVisuals
{
	private static final float SCALE = 1.2f;
	/** Pack gun models are centred a little below and behind the model origin; this puts them mid-block. */
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
	static float yaw(BlockFace facing)
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

		Location at = signBlock.clone().add(0.5 - facing.getModX() * WALL_INSET, 0.5 + raise, 0.5 - facing.getModZ() * WALL_INSET);
		Entity display = Displays.spawnItem(at, stack, SCALE, yaw(facing), usePack ? CENTRE_LIFT : 0f, false);
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
