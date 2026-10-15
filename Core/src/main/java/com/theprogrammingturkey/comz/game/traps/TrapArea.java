package com.theprogrammingturkey.comz.game.traps;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The space a running trap affects, resolved once per activation: a block box (zone or own corners) or, for
 * traps saved before regions existed, a sphere around the trap's sign.
 */
public final class TrapArea
{
	/**
	 * Boxes are usually drawn by clicking floor blocks, so entities standing on the top layer count as inside
	 * up to this many blocks above it.
	 */
	private static final double HEADROOM = 2.0;

	private final World world;
	private final double minX, minY, minZ, maxX, maxY, maxZ;
	private final Location sphereCentre;
	private final double radius;
	private final double floorY;

	private TrapArea(World world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Location sphereCentre, double radius)
	{
		this.world = world;
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxY = maxY;
		this.maxZ = maxZ;
		this.sphereCentre = sphereCentre;
		this.radius = radius;
		this.floorY = findFloor();
	}

	/** Inclusive block corners. */
	public static TrapArea box(Location corner1, Location corner2)
	{
		return new TrapArea(corner1.getWorld(),
				Math.min(corner1.getBlockX(), corner2.getBlockX()), Math.min(corner1.getBlockY(), corner2.getBlockY()), Math.min(corner1.getBlockZ(), corner2.getBlockZ()),
				Math.max(corner1.getBlockX(), corner2.getBlockX()) + 1, Math.max(corner1.getBlockY(), corner2.getBlockY()) + 1, Math.max(corner1.getBlockZ(), corner2.getBlockZ()) + 1,
				null, 0);
	}

	public static TrapArea sphere(Location centre, double radius)
	{
		double r = Math.max(0.5, radius);
		return new TrapArea(centre.getWorld(), centre.getX() - r, centre.getY() - 1, centre.getZ() - r, centre.getX() + r, centre.getY() + 2, centre.getZ() + r, centre.clone(), r);
	}

	/** Visual floor: the top of the lowest solid layer under the centre, or the bottom of the box if it is open. */
	private double findFloor()
	{
		if(world == null)
			return minY;
		Block block = world.getBlockAt((int) Math.floor(centreX()), (int) Math.floor(minY), (int) Math.floor(centreZ()));
		return block.getType().isSolid() ? minY + 1 : minY;
	}

	public World getWorld()
	{
		return world;
	}

	public boolean contains(Location location)
	{
		if(location == null || location.getWorld() == null || !location.getWorld().equals(world))
			return false;
		if(sphereCentre != null)
			return location.distance(sphereCentre) <= radius;
		double x = location.getX(), y = location.getY(), z = location.getZ();
		return x >= minX && x < maxX && z >= minZ && z < maxZ && y >= minY && y < maxY + HEADROOM;
	}

	public double minX()
	{
		return minX;
	}

	public double minZ()
	{
		return minZ;
	}

	public double maxX()
	{
		return maxX;
	}

	public double maxZ()
	{
		return maxZ;
	}

	public double sizeX()
	{
		return maxX - minX;
	}

	public double sizeZ()
	{
		return maxZ - minZ;
	}

	public double centreX()
	{
		return (minX + maxX) / 2;
	}

	public double centreZ()
	{
		return (minZ + maxZ) / 2;
	}

	public double floorY()
	{
		return floorY;
	}

	public Location centre()
	{
		return new Location(world, centreX(), floorY, centreZ());
	}

	/** Uniform random point on the floor plane, {@code height} above it. */
	public Location randomPoint(double height)
	{
		return new Location(world, minX + Math.random() * sizeX(), floorY + height, minZ + Math.random() * sizeZ());
	}
}
