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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A buyable, timed trap. Its {@link TrapType} decides the look, sound and damage; its region is a zone (by id),
 * its own two corners, or - for traps saved before regions existed - a radius around the activation sign.
 * Typed traps cool down from the end of their run (Black Ops); legacy generic traps keep the old behaviour of
 * counting the cooldown from activation.
 */
public class Trap
{
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
	/** Null follows the type's requires_power. */
	private Boolean requiresPower;
	/** Runs by itself whenever it is off cooldown (Ascension centrifuge, Shangri-La spikes); nobody buys it. */
	private boolean automatic;

	/** Epoch millis the cooldown counts from; 0 = never used. Transient. */
	private long lastUsed = 0L;
	private final List<TrapRun> runs = new ArrayList<>();
	/** More boxes the same handle switches on (one trap guarding two doorways); each is a two-corner pair. */
	private final List<Location[]> extraRegions = new ArrayList<>();

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
		return requiresPower != null ? requiresPower : getType().requiresPower();
	}

	public Boolean getRawRequiresPower()
	{
		return requiresPower;
	}

	public void setRequiresPower(Boolean requiresPower)
	{
		this.requiresPower = requiresPower;
	}

	public boolean isAutomatic()
	{
		return automatic;
	}

	public void setAutomatic(boolean automatic)
	{
		this.automatic = automatic;
	}

	public boolean isPowerLocked(Game game)
	{
		return game.isPowerLocked(requiresPower());
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

	public List<Location[]> getExtraRegions()
	{
		return Collections.unmodifiableList(extraRegions);
	}

	public void addExtraRegion(Location corner1, Location corner2)
	{
		extraRegions.add(new Location[]{corner1, corner2});
	}

	public void clearExtraRegions()
	{
		extraRegions.clear();
	}

	public boolean isActive()
	{
		for(TrapRun run : runs)
			if(run.isRunning())
				return true;
		return false;
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
		cancelRuns();
	}

	private void cancelRuns()
	{
		for(TrapRun run : runs)
			run.cancel();
		runs.clear();
	}

	private boolean cooldownFromActivation()
	{
		return TrapType.GENERIC_ID.equals(typeId);
	}

	/** Starts a run credited to {@code activator} over the main region and every extra region. */
	public boolean activate(Game game, Player activator)
	{
		TrapArea area = resolveArea(game);
		if(area == null)
			return false;
		cancelRuns();
		List<TrapArea> areas = new ArrayList<>();
		areas.add(area);
		for(Location[] extra : extraRegions)
			if(extra[0] != null && extra[1] != null && extra[0].getWorld() != null)
				areas.add(TrapArea.box(extra[0], extra[1]));
		boolean fromActivation = cooldownFromActivation();
		if(fromActivation)
			lastUsed = System.currentTimeMillis();
		Runnable onFinish = fromActivation ? () -> {} : () -> lastUsed = System.currentTimeMillis();
		// Shared so an entity standing where regions overlap is still hit only once per interval.
		Map<UUID, Integer> lastHit = new HashMap<>();
		for(TrapArea each : areas)
		{
			TrapRun thisRun = new TrapRun(game, getType(), each, activator, getDurationSeconds(), lastHit, onFinish);
			runs.add(thisRun);
			thisRun.start();
		}
		return true;
	}
}
