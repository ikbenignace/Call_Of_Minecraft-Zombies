package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.signs.AmmoCrateSign;
import com.theprogrammingturkey.comz.game.signs.PackAPunchSign;
import com.theprogrammingturkey.comz.listeners.BarrierRepairListener;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
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
 * arena's closed doors, machines, wall guns, boxes and sign props. The buyable the player is looking
 * at (else the nearest one that needs no aim) becomes that player's "active target" — an action-bar prompt is shown ({@code [F] Open Door — $750}, colored by
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
	/** Max vertical offset between a player and a sign prop for F to reach it. */
	private static final double PROP_REACH_Y = 2.5D;
	/** How far a crosshair ray counts as looking at something. */
	private static final double LOOK_RANGE = 6.0D;

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

		// Door signs are hidden (and later restored) by SignPropManager. Without it the plain signs of
		// a sign-free setup are cleared here; Door.closeDoor handles it on teardown.
		if(!ConfigManager.getMainConfig().useDoorSigns && !game.signPropManager.isActive())
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
			if(game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
			{
				activeTargets.remove(player.getUniqueId());
				continue;
			}

			BuyTarget target = findNearestTarget(player, machines);
			if(target != null)
			{
				activeTargets.put(player.getUniqueId(), target);
				sendPrompt(player, target);
			}
			else
			{
				activeTargets.remove(player.getUniqueId());
				String barrierPrompt = barrierPrompt(player);
				if(barrierPrompt != null)
					sendPrompt(player, barrierPrompt);
			}
		}

		// Drop targets for players no longer in the game (left / dead between scans).
		activeTargets.keySet().retainAll(seen);
	}

	/** A buyable in range of the player, with the box a crosshair must hit for it to count as looked at. */
	private static final class Candidate
	{
		final BuyTarget target;
		final double distSq;
		final BoundingBox aim;
		final boolean requiresLook;

		Candidate(BuyTarget target, double distSq, BoundingBox aim, boolean requiresLook)
		{
			this.target = target;
			this.distSq = distSq;
			this.aim = aim;
			this.requiresLook = requiresLook;
		}
	}

	private static BoundingBox aimBox(Location centre, double halfWidth, double halfHeight)
	{
		return BoundingBox.of(centre.toVector(), centre.toVector()).expand(halfWidth, halfHeight, halfWidth);
	}

	private BuyTarget findNearestTarget(Player player, java.util.List<MachineModelManager.MachineTarget> machines)
	{
		java.util.List<Candidate> candidates = new java.util.ArrayList<>();

		// Doors: in range when the player's bounding box overlaps the door's block region expanded by
		// DOOR_REACH, and only offered while the door is looked at (a door is a big thing to be near).
		for(Door door : game.doorManager.getDoors())
		{
			if(door.isOpened())
				continue;
			BoundingBox zone = doorRegionBox(door, DOOR_REACH);
			BoundingBox region = doorRegionBox(door, 0.0D);
			if(zone == null || region == null || !zone.overlaps(player.getBoundingBox()))
				continue;
			Vector centre = zone.getCenter();
			candidates.add(new Candidate(new BuyTarget(TargetType.DOOR, null, doorPrompt(door, player), null, door, null),
					distanceSqXZ(player.getLocation(), centre.getX(), centre.getZ()), region, true));
		}

		for(MachineModelManager.MachineTarget m : machines)
		{
			if(m.centre == null || m.centre.getWorld() == null || !m.centre.getWorld().equals(player.getWorld()))
				continue;
			double dSq = distanceSqXZ(player.getLocation(), m.centre.getX(), m.centre.getZ());
			if(dSq <= MACHINE_REACH * MACHINE_REACH)
				candidates.add(new Candidate(new BuyTarget(TargetType.MACHINE, m.centre.clone(), machinePrompt(m, player), m.label), dSq, aimBox(m.centre, 0.7, 1.0), false));
		}

		for(MachineModelManager.MachineTarget wallGun : game.wallGunManager.getTargets())
		{
			if(wallGun.centre.getWorld() == null || !wallGun.centre.getWorld().equals(player.getWorld()))
				continue;
			double dSq = distanceSqXZ(player.getLocation(), wallGun.centre.getX(), wallGun.centre.getZ());
			if(dSq <= MACHINE_REACH * MACHINE_REACH)
				candidates.add(new Candidate(new BuyTarget(TargetType.WALL_GUN, wallGun.centre.clone(), com.theprogrammingturkey.comz.game.signs.GunSign.prompt(game, player, wallGun.signLines), wallGun.label), dSq, aimBox(wallGun.centre, 0.7, 0.6), false));
		}

		// Mystery boxes: in-game they're a chest at boxLoc. Within BOX_REACH, F starts/picks the box.
		for(com.theprogrammingturkey.comz.game.features.RandomBox box : game.boxManager.getActiveBoxes())
		{
			Location loc = box.getLocation();
			if(loc == null || loc.getWorld() == null || !loc.getWorld().equals(player.getWorld()))
				continue;
			double dSq = distanceSqXZ(player.getLocation(), loc.getX() + 0.5, loc.getZ() + 0.5);
			if(dSq <= BOX_REACH * BOX_REACH)
				candidates.add(new Candidate(new BuyTarget(TargetType.BOX, loc.clone(), boxPrompt(box, player), null), dSq, aimBox(loc.clone().add(0.5, 0.5, 0.5), 1.0, 0.6), false));
		}

		for(SignPropManager.Prop prop : game.signPropManager.getProps())
		{
			if(prop.getType() == SignPropManager.PropType.DOOR)
				continue;
			Location centre = prop.getCentre();
			if(centre.getWorld() == null || !centre.getWorld().equals(player.getWorld()))
				continue;
			double dSq = distanceSqXZ(player.getLocation(), centre.getX(), centre.getZ());
			if(dSq > MACHINE_REACH * MACHINE_REACH || Math.abs(player.getLocation().getY() - centre.getY()) > PROP_REACH_Y)
				continue;
			String prompt = game.signPropManager.promptFor(prop, player);
			if(prompt != null)
				candidates.add(new Candidate(new BuyTarget(TargetType.PROP, centre, prompt, null, null, prop), dSq, aimBox(centre, 0.6, 0.7), false));
		}

		return choose(player, candidates);
	}

	/**
	 * What the player is looking at wins; with nothing in the crosshair the nearest candidate that does not
	 * need to be looked at is picked.
	 */
	private static BuyTarget choose(Player player, java.util.List<Candidate> candidates)
	{
		Location eye = player.getEyeLocation();
		Vector origin = eye.toVector();
		Vector direction = eye.getDirection();

		Candidate looked = null;
		double lookedDist = Double.MAX_VALUE;
		Candidate nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for(Candidate candidate : candidates)
		{
			RayTraceResult hit = candidate.aim.rayTrace(origin, direction, LOOK_RANGE);
			if(hit != null)
			{
				double dist = hit.getHitPosition().distance(origin);
				if(dist < lookedDist)
				{
					lookedDist = dist;
					looked = candidate;
				}
			}
			if(!candidate.requiresLook && candidate.distSq < nearestSq)
			{
				nearestSq = candidate.distSq;
				nearest = candidate;
			}
		}
		Candidate chosen = looked != null ? looked : nearest;
		return chosen == null ? null : chosen.target;
	}

	/** Repairing is hold-to-rebuild (sneak), not a buy, so it only shows a prompt and never becomes a target. */
	private String barrierPrompt(Player player)
	{
		for(Barrier barrier : game.barrierManager.getBarriers())
		{
			if(barrier.getStage() == -1 || !BarrierRepairListener.isInRange(barrier, player))
				continue;
			if(barrier.isBreaking())
				return ChatColor.GRAY + "Barrier under attack";
			return (player.isSneaking() ? ChatColor.GREEN : ChatColor.YELLOW) + "Hold SHIFT to Rebuild";
		}
		return null;
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
		return color + "[F] Mystery Box " + ChatColor.YELLOW + "[" + box.getCost() + "]";
	}

	private String doorPrompt(Door door, Player player)
	{
		boolean powerLocked = door.requiresPower() && !game.isPowered();
		boolean canAfford = PointManager.INSTANCE.getPlayersPoints(player) >= door.getCost();
		return doorPromptText(powerLocked, canAfford, door.getCost());
	}

	private String machinePrompt(MachineModelManager.MachineTarget m, Player player)
	{
		String[] lines = m.signLines;
		if(lines != null && lines.length > 3)
		{
			String type = ChatColor.stripColor(lines[1]).trim().toLowerCase();
			if(type.equals("pack-a-punch"))
				return ChatColor.GREEN + "[F] " + ChatColor.RESET + PackAPunchSign.prompt(game, player, m.base, lines);
			if(type.equals("perk machine"))
				return perkPrompt(lines, player);
			if(type.equals("ammo crate"))
				return AmmoCrateSign.prompt(game, player);
			if(type.equals("power"))
				return game.isPowered() ? ChatColor.GRAY + "Power is on" : ChatColor.GREEN + "[F] Turn on Power";
			if(type.equals("bank"))
				return ChatColor.GREEN + "[F] Deposit " + ChatColor.GRAY + "| Sneak + F Withdraw";
			if(type.equals("fridge"))
				return ChatColor.GREEN + "[F] Store gun " + ChatColor.GRAY + "| Sneak + F Retrieve";
		}
		return ChatColor.GREEN + "[F] " + ChatColor.RESET + m.label;
	}

	public String perkPrompt(String[] lines, Player player)
	{
		PerkType perk = PerkType.getPerkType(ChatColor.stripColor(lines[2]));
		if(perk == null)
			return ChatColor.RED + "[F] Unknown perk";
		String name = ChatColor.AQUA + perk.toString().replace('_', ' ').toLowerCase();
		boolean soloRevive = perk == PerkType.QUICK_REVIVE && game.getPlayersInGame().size() == 1;
		if(!soloRevive && game.hasPower() && !game.isPowered())
			return ChatColor.RED + "[F] " + name + ChatColor.GRAY + " - power required";
		if(perk != PerkType.DER_WUNDERFIZZ && game.perkManager.hasPerk(player, perk))
			return ChatColor.GRAY + "[F] " + name + " - owned";
		String costStr = ChatColor.stripColor(lines[3]);
		int cost = costStr != null && costStr.matches("[0-9]{1,5}") ? Integer.parseInt(costStr) : perk.getDefaultCost();
		if(soloRevive)
			cost = ConfigManager.getMainConfig().soloQuickRevivePrice;
		ChatColor color = PointManager.INSTANCE.canBuy(player, cost) ? ChatColor.GREEN : ChatColor.RED;
		return color + "[F] " + name + " " + ChatColor.YELLOW + "[" + cost + "]";
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
			return ChatColor.RED + "[F] " + ChatColor.GRAY + "Requires Power";
		ChatColor color = canAfford ? ChatColor.GREEN : ChatColor.RED;
		return color + "[F] Open Door " + ChatColor.YELLOW + "[" + cost + "]";
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
	public enum TargetType { DOOR, MACHINE, WALL_GUN, BOX, PROP }

	/** A resolved buy target: its kind, the location to route the buy to, and the prompt text. */
	public static final class BuyTarget
	{
		public final TargetType type;
		/** Machine, wall gun and box: where to route the buy. Null for doors and props. */
		public final Location location;
		public final String prompt;
		/** Machine price label (already formatted); null otherwise. */
		public final String machineLabel;
		public final Door door;
		public final SignPropManager.Prop prop;

		BuyTarget(TargetType type, Location location, String prompt, String machineLabel, Door door, SignPropManager.Prop prop)
		{
			this.type = type;
			this.location = location;
			this.prompt = prompt;
			this.machineLabel = machineLabel;
			this.door = door;
			this.prop = prop;
		}

		BuyTarget(TargetType type, Location location, String prompt, String machineLabel)
		{
			this(type, location, prompt, machineLabel, null, null);
		}
	}
}
