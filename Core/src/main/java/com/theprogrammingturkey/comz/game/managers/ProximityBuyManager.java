package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Alternative-interaction layer: lets an in-game player buy a {@link Door} or use a perk / Pack-a-
 * Punch {@link MachineModelManager.MachineTarget machine} by standing next to it and pressing F
 * (swap hands), instead of clicking a world sign.
 *
 * <p>A repeating task (every {@link #SCAN_PERIOD_TICKS}) scans each in-game player against the
 * arena's closed doors and spawned machines. The nearest in-range buyable becomes that player's
 * "active target" — an action-bar prompt is shown ({@code [F] Open Door — $750}, colored by
 * affordability / power state) and the target is remembered so the F-key listener
 * ({@code OnInventoryChangeEvent}) can route the press to the right buy logic without re-scanning.
 *
 * <p>This layer is a no-op when {@code config.features.proximityBuy} is false. The underlying buy
 * logic is untouched: doors call the same {@code DoorSign.onInteract} path, machines call the same
 * {@code MachineModelManager.handleInteract*} path — this manager only adds an alternative trigger.
 *
 * <p>Door signs are hidden by {@link Door#closeDoor()} when {@code config.features.useDoorSigns} is
 * false, but each Door still carries its buy-point location + price in data, which is what this
 * scanner keys off.
 */
public class ProximityBuyManager
{
	/** Ticks between proximity scans (10 ticks = twice per second — snappy without being noisy). */
	private static final long SCAN_PERIOD_TICKS = 10L;

	/** Horizontal half-extent (blocks) of the buy zone around a door's block region. */
	private static final double DOOR_REACH = 2.0D;
	/** Max distance (blocks, centre-to-centre) from a machine's base at which F will buy it. */
	private static final double MACHINE_REACH = 2.5D;
	/** Max distance (blocks, centre-to-centre) from a mystery box's chest at which F will trigger it. */
	private static final double BOX_REACH = 2.5D;

	private final Game game;
	private int taskId = -1;

	/**
	 * Each in-game player's currently active buy target. Replaced atomically each scan so the F-key
	 * listener always sees the freshest target. Keyed by UUID to avoid holding Player references.
	 */
	private final Map<UUID, BuyTarget> activeTargets = new HashMap<>();

	public ProximityBuyManager(Game game)
	{
		this.game = game;
	}

	/** Start the repeating proximity scan. Idempotent; safe to call on every game start. */
	public void start()
	{
		stop();
		if(!ConfigManager.getMainConfig().proximityBuy)
			return;

		// When door signs are hidden, clear any physical sign blocks left in the world from map
		// authoring (the Door keeps the buy-point Location + saved BlockData in data, so removing the
		// block here doesn't lose anything). Done once at start; Door.closeDoor handles it on teardown.
		if(!ConfigManager.getMainConfig().useDoorSigns)
		{
			for(Door door : game.doorManager.getDoors())
			{
				if(door.isOpened())
					continue;
				for(Location loc : door.getSignsLocations())
					com.theprogrammingturkey.comz.util.BlockUtils.setBlockToAir(loc);
			}
		}

		taskId = COMZombies.scheduleTask(0L, SCAN_PERIOD_TICKS, () ->
		{
			if(game.getStatus() != Game.GameStatus.INGAME)
			{
				if(!activeTargets.isEmpty())
					activeTargets.clear();
				return;
			}
			scan();
		});
	}

	/** Stop scanning and drop all active targets. Safe to call repeatedly. */
	public void stop()
	{
		if(taskId != -1)
		{
			Bukkit.getScheduler().cancelTask(taskId);
			taskId = -1;
		}
		activeTargets.clear();
	}

	/**
	 * If the player currently has an active buy target, consume it (clear + return) so the caller can
	 * route the F press. Returns null when the player isn't near any buyable, in which case the caller
	 * should fall through to the default F behavior (held-weapon display refresh).
	 */
	public BuyTarget consumeBuyTarget(Player player)
	{
		if(!ConfigManager.getMainConfig().proximityBuy)
			return null;
		return activeTargets.remove(player.getUniqueId());
	}

	private void scan()
	{
		// Build the snapshot once per tick — door list + machine targets are read-only here.
		java.util.List<MachineModelManager.MachineTarget> machines = game.machineModelManager.getMachineTargets();

		// Players we should keep targets for (so we can drop stale ones).
		java.util.Set<UUID> seen = new java.util.HashSet<>();

		for(Player player : game.getPlayersInGame())
		{
			if(player == null || !player.isOnline())
				continue;
			seen.add(player.getUniqueId());

			BuyTarget target = findNearestTarget(player, machines);
			if(target != null)
			{
				activeTargets.put(player.getUniqueId(), target);
				sendPrompt(player, target);
			}
			else
			{
				activeTargets.remove(player.getUniqueId());
			}
		}

		// Drop targets for players no longer in the game (left / dead between scans).
		activeTargets.keySet().retainAll(seen);
	}

	private BuyTarget findNearestTarget(Player player, java.util.List<MachineModelManager.MachineTarget> machines)
	{
		BuyTarget best = null;
		double bestDistSq = Double.MAX_VALUE;

		// Doors: in range when the player's bounding box overlaps the door's block region expanded
		// by DOOR_REACH. Pick the nearest such door by region-centre distance.
		for(Door door : game.doorManager.getDoors())
		{
			if(door.isOpened())
				continue;
			BoundingBox zone = doorRegionBox(door, DOOR_REACH);
			if(zone == null)
				continue;
			if(!zone.overlaps(player.getBoundingBox()))
				continue;
			Vector centre = zone.getCenter();
			double dSq = distanceSqXZ(player.getLocation(), centre.getX(), centre.getZ());
			if(dSq < bestDistSq)
			{
				bestDistSq = dSq;
				// Buy point = the door's first sign location (preserved in data even when the sign
				// block is hidden); DoorManager.getDoorFromSign looks it up by this location.
				Location signLoc = door.getSignsLocations().isEmpty() ? null : door.getSignsLocations().get(0);
				best = new BuyTarget(TargetType.DOOR, signLoc, doorPrompt(door, player), null);
			}
		}

		// Machines: nearest by centre distance within MACHINE_REACH. Doors take precedence when tied
		// (a door in front of a machine is the more specific intent), handled by the strict < below.
		for(MachineModelManager.MachineTarget m : machines)
		{
			if(m.centre == null || m.centre.getWorld() == null || !m.centre.getWorld().equals(player.getWorld()))
				continue;
			double dSq = distanceSqXZ(player.getLocation(), m.centre.getX(), m.centre.getZ());
			if(dSq > MACHINE_REACH * MACHINE_REACH)
				continue;
			if(dSq < bestDistSq)
			{
				bestDistSq = dSq;
				best = new BuyTarget(TargetType.MACHINE, m.centre.clone(), machinePrompt(m, player), m.label);
			}
		}

		// Mystery boxes: in-game they're a chest at boxLoc. Within BOX_REACH, F starts/picks the box.
		for(com.theprogrammingturkey.comz.game.features.RandomBox box : game.boxManager.getActiveBoxes())
		{
			Location loc = box.getLocation();
			if(loc == null || loc.getWorld() == null || !loc.getWorld().equals(player.getWorld()))
				continue;
			double dSq = distanceSqXZ(player.getLocation(), loc.getX() + 0.5, loc.getZ() + 0.5);
			if(dSq > BOX_REACH * BOX_REACH)
				continue;
			if(dSq < bestDistSq)
			{
				bestDistSq = dSq;
				best = new BuyTarget(TargetType.BOX, loc.clone(), boxPrompt(box, player), null);
			}
		}

		return best;
	}

	private String boxPrompt(com.theprogrammingturkey.comz.game.features.RandomBox box, Player player)
	{
		// Spinning box: prompt reflects the current state (waiting for weapon pickup vs. ready to spin).
		if(!box.canActivate() && box.canPickWeapon(player))
			return ChatColor.GREEN + "[F] Take weapon";
		if(!box.canActivate())
			return ChatColor.GRAY + "[F] Box is in use";
		boolean canAfford = PointManager.INSTANCE.getPlayersPoints(player) >= box.getCost();
		ChatColor color = canAfford ? ChatColor.GREEN : ChatColor.RED;
		return color + "[F] Mystery Box " + ChatColor.YELLOW + "$" + box.getCost();
	}

	private String doorPrompt(Door door, Player player)
	{
		boolean powerLocked = door.requiresPower() && !game.isPowered();
		boolean canAfford = PointManager.INSTANCE.getPlayersPoints(player) >= door.getCost();
		return doorPromptText(powerLocked, canAfford, door.getCost());
	}

	private String machinePrompt(MachineModelManager.MachineTarget m, Player player)
	{
		// Machine labels already carry their own colour + cost; prefix with the key hint.
		// We don't re-check affordability here (costs vary by perk/pap and the handler reports
		// failure in chat anyway) — the prompt is informational.
		return ChatColor.GREEN + "[F] " + ChatColor.RESET + m.label;
	}

	/**
	 * Pure prompt builder for a door, factored out so it can be unit-tested without Bukkit. Color
	 * rules (per the approved plan):
	 * <ul>
	 *   <li>power required and off → red "Locked — Power required" (regardless of points)</li>
	 *   <li>otherwise affordable → green "[F] Open Door — $cost"</li>
	 *   <li>otherwise unaffordable → red "[F] Open Door — $cost"</li>
	 * </ul>
	 */
	static String doorPromptText(boolean powerLocked, boolean canAfford, int cost)
	{
		if(powerLocked)
			return ChatColor.RED + "[F] " + ChatColor.GRAY + "Locked — Power required";
		ChatColor color = canAfford ? ChatColor.GREEN : ChatColor.RED;
		return color + "[F] Open Door " + ChatColor.YELLOW + "$" + cost;
	}

	/**
	 * Pure nearest-target decision: given the squared distance to the nearest in-range door (or
	 * {@code Double.MAX_VALUE} when none is in range) and the squared distance to the nearest
	 * in-range machine (same convention), returns which one wins. A tie favors the DOOR (more
	 * specific intent). Returns null when neither is in range.
	 */
	static TargetType selectNearestTarget(double doorDistSq, double machineDistSq)
	{
		boolean doorInRange = doorDistSq != Double.MAX_VALUE;
		boolean machineInRange = machineDistSq != Double.MAX_VALUE;
		if(!doorInRange && !machineInRange)
			return null;
		if(doorInRange && !machineInRange)
			return TargetType.DOOR;
		if(!doorInRange) // machine in range only
			return TargetType.MACHINE;
		// both in range: nearest wins, tie → door
		return machineDistSq < doorDistSq ? TargetType.MACHINE : TargetType.DOOR;
	}

	private static void sendPrompt(Player player, String text)
	{
		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
	}

	private static void sendPrompt(Player player, BuyTarget target)
	{
		sendPrompt(player, target.prompt);
	}

	/**
	 * Build the buy zone for a door: a bounding box enclosing all its blocks, expanded by
	 * {@code reach} on every side. Returns null for doors with no blocks (shouldn't normally happen).
	 */
	static BoundingBox doorRegionBox(Door door, double reach)
	{
		java.util.List<org.bukkit.block.Block> blocks = door.getBlocks();
		if(blocks.isEmpty())
			return null;
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for(org.bukkit.block.Block b : blocks)
		{
			minX = Math.min(minX, b.getX());
			minY = Math.min(minY, b.getY());
			minZ = Math.min(minZ, b.getZ());
			maxX = Math.max(maxX, b.getX() + 1);
			maxY = Math.max(maxY, b.getY() + 1);
			maxZ = Math.max(maxZ, b.getZ() + 1);
		}
		return new BoundingBox(minX - reach, minY - reach, minZ - reach, maxX + reach, maxY + reach, maxZ + reach);
	}

	private static double distanceSqXZ(Location a, double x, double z)
	{
		double dx = a.getX() - x;
		double dz = a.getZ() - z;
		return dx * dx + dz * dz;
	}

	/** What kind of buyable the player is standing next to. Determines which buy path F triggers. */
	public enum TargetType { DOOR, MACHINE, BOX }

	/** A resolved buy target: its kind, the location to route the buy to, and the prompt text. */
	public static final class BuyTarget
	{
		public final TargetType type;
		/** Door: a sign location on the door (looked up via DoorManager). Machine: its base centre. */
		public final Location location;
		public final String prompt;
		/** Machine price label (already formatted); null for doors. */
		public final String machineLabel;

		BuyTarget(TargetType type, Location location, String prompt, String machineLabel)
		{
			this.type = type;
			this.location = location;
			this.prompt = prompt;
			this.machineLabel = machineLabel;
		}
	}
}
