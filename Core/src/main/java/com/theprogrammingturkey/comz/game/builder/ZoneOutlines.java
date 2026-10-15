package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.util.Compat;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Build-mode particle outlines of an arena's zones (only the editing player sees them), plus a live box
 * from the first corner to the targeted block while a new zone is being placed.
 */
final class ZoneOutlines
{
	private static final double VIEW_DISTANCE = 64;
	/** Upper bound on particles per box so huge zones stay cheap. */
	private static final int MAX_POINTS_PER_BOX = 360;
	private static final int TARGET_RANGE = 48;

	private ZoneOutlines()
	{
	}

	static void draw(Player player, BuildSession session)
	{
		for(Zone zone : session.getGame().zoneManager.getZones())
		{
			if(zone.getWorld() != player.getWorld() || distanceToBox(player.getLocation(), zone) > VIEW_DISTANCE)
				continue;
			Location min = zone.getMin();
			Location max = zone.getMax();
			drawBox(player, min.getBlockX(), min.getBlockY(), min.getBlockZ(), max.getBlockX() + 1, max.getBlockY() + 1, max.getBlockZ() + 1, colorOf(zone));
		}

		Location corner = session.getZoneCorner();
		if(corner == null || corner.getWorld() != player.getWorld())
			return;
		Block target = player.getTargetBlockExact(TARGET_RANGE);
		Location other = target != null ? target.getLocation() : corner;
		drawBox(player, Math.min(corner.getBlockX(), other.getBlockX()), Math.min(corner.getBlockY(), other.getBlockY()), Math.min(corner.getBlockZ(), other.getBlockZ()),
				Math.max(corner.getBlockX(), other.getBlockX()) + 1, Math.max(corner.getBlockY(), other.getBlockY()) + 1, Math.max(corner.getBlockZ(), other.getBlockZ()) + 1, Color.WHITE);
	}

	/** Action-bar summary of the zone the player stands in, so zones can be told apart while editing. */
	static String describeZoneAt(Player player, BuildSession session)
	{
		Zone zone = session.getGame().zoneManager.getSmallestZoneAt(player.getLocation());
		if(zone == null)
			return null;
		return ChatColor.AQUA + zone.getId() + (zone.hasName() ? ChatColor.WHITE + " \"" + zone.getName() + "\"" : "") + ChatColor.GRAY + ": " + zone.describeFlags();
	}

	private static Color colorOf(Zone zone)
	{
		if(zone.isNoAir())
			return Color.AQUA;
		if(zone.getDamagePerSecond() > 0)
			return Color.RED;
		if(zone.hasGravityOverride())
			return Color.PURPLE;
		return Color.YELLOW;
	}

	private static double distanceToBox(Location loc, Zone zone)
	{
		Location min = zone.getMin();
		Location max = zone.getMax();
		double dx = Math.max(0, Math.max(min.getX() - loc.getX(), loc.getX() - (max.getX() + 1)));
		double dy = Math.max(0, Math.max(min.getY() - loc.getY(), loc.getY() - (max.getY() + 1)));
		double dz = Math.max(0, Math.max(min.getZ() - loc.getZ(), loc.getZ() - (max.getZ() + 1)));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static void drawBox(Player player, double x1, double y1, double z1, double x2, double y2, double z2, Color color)
	{
		double perimeter = 4 * ((x2 - x1) + (y2 - y1) + (z2 - z1));
		double step = Math.max(0.5, perimeter / MAX_POINTS_PER_BOX);
		Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
		for(double y : new double[]{y1, y2})
			for(double z : new double[]{z1, z2})
				line(player, x1, y, z, x2, y, z, step, dust);
		for(double x : new double[]{x1, x2})
			for(double z : new double[]{z1, z2})
				line(player, x, y1, z, x, y2, z, step, dust);
		for(double x : new double[]{x1, x2})
			for(double y : new double[]{y1, y2})
				line(player, x, y, z1, x, y, z2, step, dust);
	}

	private static void line(Player player, double x1, double y1, double z1, double x2, double y2, double z2, double step, Particle.DustOptions dust)
	{
		double length = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
		int points = Math.max(1, (int) Math.ceil(length / step));
		for(int i = 0; i <= points; i++)
		{
			double t = (double) i / points;
			player.spawnParticle(Compat.DUST, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, 1, 0, 0, 0, 0, dust);
		}
	}
}
