package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.ConfigSetup;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.traps.TrapArea;
import com.theprogrammingturkey.comz.game.traps.TrapRun;
import com.theprogrammingturkey.comz.game.traps.TrapType;
import com.theprogrammingturkey.comz.game.traps.TrapTypes;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A buyable, timed trap. Its {@link TrapType} decides the look, sound and damage; its region is a zone (by id),
 * its own two corners, or - for traps saved before regions existed - a radius around the activation sign.
 * After a run it cools down before it can be bought again.
 */
public class Trap
{
	/** Duration/cooldown value meaning "use the type's default". */
	public static final int USE_TYPE_DEFAULT = -1;

	private final String id;
	private Location handle;
	private int cost;
	private String typeId = TrapType.GENERIC_ID;
	private String zoneId;
	private Location corner1;
	private Location corner2;
	private double radius = -1;
	private int durationSeconds = USE_TYPE_DEFAULT;
	private int cooldownSeconds = USE_TYPE_DEFAULT;
	private boolean requiresPower = true;

	/** Epoch millis the last run ended; 0 = never used. Transient. */
	private long lastUsed = 0L;
	private TrapRun run;

	public Trap(String id, Location handle, int cost)
	{
		this.id = id;
		this.handle = handle;
		this.cost = cost;
	}

	public String getId()
	{
		return id;
	}

	/** Where the activation sign/handle is. */
	public Location getCenter()
	{
		return handle;
	}

	public void setHandle(Location handle)
	{
		this.handle = handle;
	}

	public int getCost()
	{
		return cost;
	}

	public void setCost(int cost)
	{
		this.cost = Math.max(0, cost);
	}

	public TrapType getType()
	{
		return TrapTypes.get(typeId);
	}

	public String getTypeId()
	{
		return typeId;
	}

	public void setTypeId(String typeId)
	{
		this.typeId = typeId == null || typeId.isEmpty() ? TrapType.GENERIC_ID : typeId.toLowerCase();
	}

	public String getZoneId()
	{
		return zoneId;
	}

	/** Use a zone as the region; clears any own corners. */
	public void setZone(String zoneId)
	{
		this.zoneId = zoneId == null || zoneId.isEmpty() ? null : zoneId;
		if(this.zoneId != null)
		{
			corner1 = null;
			corner2 = null;
		}
	}

	public Location getCorner1()
	{
		return corner1;
	}

	public Location getCorner2()
	{
		return corner2;
	}

	/** Use an own box as the region; clears any zone link. */
	public void setCorners(Location corner1, Location corner2)
	{
		this.corner1 = corner1;
		this.corner2 = corner2;
		if(corner1 != null && corner2 != null)
			zoneId = null;
	}

	/** Legacy kill radius around the sign; negative means the config.yml default. */
	public double getRadius()
	{
		return radius;
	}

	public void setRadius(double radius)
	{
		this.radius = radius;
	}

	public int getRawDurationSeconds()
	{
		return durationSeconds;
	}

	public int getRawCooldownSeconds()
	{
		return cooldownSeconds;
	}

	public void setDurationSeconds(int seconds)
	{
		durationSeconds = seconds < 0 ? USE_TYPE_DEFAULT : seconds;
	}

	public void setCooldownSeconds(int seconds)
	{
		cooldownSeconds = seconds < 0 ? USE_TYPE_DEFAULT : seconds;
	}

	public int getDurationSeconds()
	{
		return durationSeconds >= 0 ? Math.max(1, durationSeconds) : getType().getDurationSeconds();
	}

	public int getCooldownSeconds()
	{
		return cooldownSeconds >= 0 ? cooldownSeconds : getType().getCooldownSeconds();
	}

	public boolean requiresPower()
	{
		return requiresPower;
	}

	public void setRequiresPower(boolean requiresPower)
	{
		this.requiresPower = requiresPower;
	}

	/** True when the arena has a power switch, this trap needs it, and it is still off. */
	public boolean isPowerLocked(Game game)
	{
		return requiresPower && game.hasPower() && !game.isPowered();
	}

	/** Short description of where the trap acts, for build-mode messages. */
	public String describeRegion()
	{
		if(zoneId != null)
			return "zone " + zoneId;
		if(corner1 != null && corner2 != null)
			return "box " + corner1.getBlockX() + "," + corner1.getBlockY() + "," + corner1.getBlockZ()
					+ " -> " + corner2.getBlockX() + "," + corner2.getBlockY() + "," + corner2.getBlockZ();
		return "radius " + effectiveRadius() + " around the sign";
	}

	private double effectiveRadius()
	{
		if(radius >= 0)
			return radius;
		ConfigSetup cfg = ConfigManager.getMainConfig();
		return cfg == null ? 4.0 : cfg.trapKillRadius;
	}

	/** The region to act on right now, or null when it cannot be resolved (missing world). */
	public TrapArea resolveArea(Game game)
	{
		if(zoneId != null)
		{
			Zone zone = game.zoneManager.getZone(zoneId);
			if(zone != null && zone.getWorld() != null)
				return TrapArea.box(zone.getMin(), zone.getMax());
		}
		if(corner1 != null && corner2 != null && corner1.getWorld() != null)
			return TrapArea.box(corner1, corner2);
		if(handle == null || handle.getWorld() == null)
			return null;
		return TrapArea.sphere(handle.clone().add(0.5, 0, 0.5), effectiveRadius());
	}

	public boolean isActive()
	{
		return run != null && run.isRunning();
	}

	public long getLastUsed()
	{
		return lastUsed;
	}

	/**
	 * Pure cooldown decision, extracted for testing. Mirrors {@code TeleporterManager.offCooldown}.
	 *
	 * @return true if enough time has elapsed since the last use for the trap to fire again.
	 */
	public static boolean isReady(long lastUsedMs, long nowMs, int cooldownSeconds)
	{
		if(cooldownSeconds <= 0)
			return true;
		return (nowMs - lastUsedMs) >= (cooldownSeconds * 1000L);
	}

	/** Seconds before this trap can be bought again, or 0 when ready. */
	public int secondsUntilReady()
	{
		int cooldown = getCooldownSeconds();
		if(cooldown <= 0)
			return 0;
		long now = System.currentTimeMillis();
		if(isReady(lastUsed, now, cooldown))
			return 0;
		long remainingMs = (cooldown * 1000L) - (now - lastUsed);
		// Round up so a partial second still reads as "1s left" rather than "0s".
		return (int) ((remainingMs + 999L) / 1000L);
	}

	public boolean canActivate()
	{
		return !isActive() && secondsUntilReady() == 0;
	}

	public void reset()
	{
		lastUsed = 0L;
		if(run != null)
			run.cancel();
		run = null;
	}

	/** Starts a run credited to {@code activator}; the cooldown starts when the run ends. */
	public boolean activate(Game game, Player activator)
	{
		TrapArea area = resolveArea(game);
		if(area == null)
			return false;
		if(run != null)
			run.cancel();
		TrapRun thisRun = new TrapRun(game, getType(), area, activator, getDurationSeconds(), () -> lastUsed = System.currentTimeMillis());
		run = thisRun;
		thisRun.start();
		return true;
	}
}
