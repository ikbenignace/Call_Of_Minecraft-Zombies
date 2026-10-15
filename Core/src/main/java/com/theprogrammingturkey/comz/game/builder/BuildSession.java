package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.Trap;
import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.game.features.Transport;
import com.theprogrammingturkey.comz.game.visuals.MachineLooks;
import com.theprogrammingturkey.comz.game.visuals.MachineVisuals;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.DisplayEntityUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player state for an active in-game build-mode session: which arena is being edited, the player's
 * real inventory/gamemode to restore on exit, the selected variant for each tool, and any build-time
 * preview machine models spawned so far (despawned on exit).
 */
public class BuildSession
{
	private final Game game;
	private final ItemStack[] savedContents;
	private final GameMode savedMode;
	private final int savedHeldSlot;
	private final boolean savedAllowFlight;
	private final boolean savedFlying;

	/** Selected variant index per tool, indexed by {@link BuildTool#ordinal()}. */
	private final int[] variantIndex = new int[BuildTool.values().length];

	/** Preview machine visuals (pack model or block look-alike parts) keyed by the sign location, for targeted + bulk removal. */
	private final Map<Location, List<Entity>> previews = new HashMap<>();

	/**
	 * Floating door-id holograms shown above every registered door's centre block while in build mode,
	 * so the editor can tell doors apart at a glance. Keyed by door id; despawned on exit.
	 */
	private final Map<String, Entity> doorHolograms = new HashMap<>();

	/**
	 * The room whose spawns/barriers new placements are linked to. {@code null} = the starting room
	 * (spawns with no door gate, always active). When a door is the active room, spawns placed get
	 * {@code door.addSpawnPoint} so they only activate once that door is opened.
	 */
	private Door activeRoomDoor;

	/** A door currently being block-selected with the Door tool (before it is finalized), or null. */
	private Door doorInProgress;

	/** True when {@link #doorInProgress} is an already-registered door being edited (vs a brand-new one). */
	private boolean doorInProgressExisting = false;

	/** A barrier currently being block-selected with the Barrier tool (before finalize), or null. */
	private Barrier barrierInProgress;

	/** True when {@link #barrierInProgress} is an already-registered barrier being edited. */
	private boolean barrierInProgressExisting = false;

	/**
	 * Snapshot of the original block keys of the feature being edited (captured at startEditing), used by
	 * {@link #cancelEditInProgress()} to revert the feature's block set — and the world — to their
	 * pre-edit state when the player discards an in-progress edit (tool switch / quit / cancel).
	 */
	private List<Block> editSnapshot = new ArrayList<>();

	/** Temporary selection-marker blocks (door/barrier), mapping location → the real BlockData to restore. */
	private final Map<Location, BlockData> markerOriginals = new HashMap<>();

	/** Debounce so a single right-click can't place two things from one rapid event burst. */
	private long lastPlaceMs = 0L;

	/** Sign location awaiting a typed price/cost via chat (sneak-right-click price edit), or null. */
	private Location pendingPriceSign;

	/** Door awaiting a typed price via chat (Door tool "Set Price" variant), or null. Sign-free counterpart
	 *  to {@link #pendingPriceSign} — used when door signs are disabled so an admin can still edit a door's
	 *  price by right-clicking a door block. */
	private Door pendingPriceDoor;

	/** First corner of a zone being placed with a Feature-tool zone preset, or null. */
	private Location zoneCorner;

	/** Zone awaiting typed edits (name / flags) via chat, or null. */
	private Zone pendingZoneEdit;

	/** Region picked for the next trap placed with a Feature-tool trap variant: corners or a zone id. */
	private Location trapCorner1;
	private Location trapCorner2;
	private String trapZoneId;

	/** Trap awaiting typed edits (type / cost / timings / power / region) via chat, or null. */
	private Trap pendingTrapEdit;

	private int zoneOutlineTask = -1;
	private int zoneOutlineTicks;
	/** A transport whose waypoints are being placed with the Feature tool's Transport variant, or null. */
	private Transport transportInProgress;

	/** Order the in-progress points were added in (true = waypoint, false = call point), for undo. */
	private final List<Boolean> transportHistory = new ArrayList<>();

	/** Transport awaiting typed options via chat (after finishing or sneak-left-clicking it), or null. */
	private Transport pendingTransportOptions;

	private int transportPreviewTask = -1;

	public BuildSession(Player player, Game game)
	{
		this.game = game;
		this.savedContents = player.getInventory().getContents();
		this.savedMode = player.getGameMode();
		this.savedHeldSlot = player.getInventory().getHeldItemSlot();
		this.savedAllowFlight = player.getAllowFlight();
		this.savedFlying = player.isFlying();
	}

	public Game getGame()
	{
		return game;
	}

	public Door getActiveRoomDoor()
	{
		return activeRoomDoor;
	}

	public void setActiveRoomDoor(Door door)
	{
		this.activeRoomDoor = door;
	}

	public Door getDoorInProgress()
	{
		return doorInProgress;
	}

	public void setDoorInProgress(Door door)
	{
		this.doorInProgress = door;
	}

	public boolean isDoorInProgressExisting()
	{
		return doorInProgressExisting;
	}

	public Barrier getBarrierInProgress()
	{
		return barrierInProgress;
	}

	public void setBarrierInProgress(Barrier barrier)
	{
		this.barrierInProgress = barrier;
	}

	public boolean isBarrierInProgressExisting()
	{
		return barrierInProgressExisting;
	}

	/**
	 * Begin editing an already-registered door in place: mark every current door block as a selection
	 * marker (so the player sees/toggles them with the Door tool), snapshot the original block set so a
	 * discarded edit restores the door exactly as it was, and briefly highlight the spawns this door
	 * gates so the editor can see the room it unlocks. Idempotent: starting again on the same door is a
	 * no-op; starting on a different door/boundary cancels the previous edit first.
	 */
	public void startEditingDoor(Door door)
	{
		if(doorInProgress == door && doorInProgressExisting)
			return;
		cancelEditInProgress();
		this.doorInProgress = door;
		this.doorInProgressExisting = true;
		this.editSnapshot = new ArrayList<>(door.getBlocks());
		for(Block b : door.getBlocks())
			addMarker(b, Material.LIME_STAINED_GLASS);
		highlightLinkedSpawns(door.getSpawnsInRoomDoorLeadsTo());
	}

	/**
	 * Begin editing an already-registered barrier in place — mirrors {@link #startEditingDoor}.
	 */
	public void startEditingBarrier(Barrier barrier)
	{
		if(barrierInProgress == barrier && barrierInProgressExisting)
			return;
		cancelEditInProgress();
		this.barrierInProgress = barrier;
		this.barrierInProgressExisting = true;
		this.editSnapshot = new ArrayList<>(barrier.getBlocks());
		for(Block b : barrier.getBlocks())
			addMarker(b, Material.ORANGE_STAINED_GLASS);
		highlightLinkedSpawns(barrier.getSpawnPoints());
	}

	/**
	 * Discard the in-progress door/barrier edit and restore the world to its pre-edit state: re-mark the
	 * snapshot blocks, revert the feature's block set to the snapshot, and clear the session. Called on
	 * tool switch, quit, and when the player starts editing a different feature.
	 */
	public void cancelEditInProgress()
	{
		if(doorInProgress != null && doorInProgressExisting)
		{
			// Re-add any snapshot blocks the player removed mid-edit and drop any they added.
			for(Block b : editSnapshot)
				if(!doorInProgress.hasDoorLoc(b))
				{
					doorInProgress.addDoorBlock(b.getLocation());
					// addMarker captures the live block's data; the live block may currently be the
					// marker glass if restoreMarker already ran. Restore via the snapshot location.
					if(!markerOriginals.containsKey(b.getLocation()))
						markerOriginals.put(b.getLocation(), b.getBlockData());
				}
			for(Block b : new ArrayList<>(doorInProgress.getBlocks()))
				if(!editSnapshot.contains(b))
					doorInProgress.removeDoorBlock(b.getLocation());
		}
		if(barrierInProgress != null && barrierInProgressExisting)
		{
			for(Block b : editSnapshot)
				if(!barrierInProgress.hasBarrierLoc(b))
				{
					barrierInProgress.addBarrierBlock(b.getLocation());
					if(!markerOriginals.containsKey(b.getLocation()))
						markerOriginals.put(b.getLocation(), b.getBlockData());
				}
			for(Block b : new ArrayList<>(barrierInProgress.getBlocks()))
				if(!editSnapshot.contains(b))
					barrierInProgress.removeBarrierBlock(b.getLocation());
		}
		restoreAllMarkers();
		doorInProgress = null;
		doorInProgressExisting = false;
		barrierInProgress = null;
		barrierInProgressExisting = false;
		editSnapshot.clear();
	}

	/** True if either a door or barrier edit-existing session is currently active. */
	public boolean isEditingExisting()
	{
		return (doorInProgress != null && doorInProgressExisting)
				|| (barrierInProgress != null && barrierInProgressExisting);
	}

	/**
	 * Finalize-clear the session's in-progress edit state WITHOUT touching markers (the caller handles
	 * marker restore / block removal as appropriate for the finalize outcome). Used by the finalize path
	 * so it can read the door/barrier after clearing the flags without cancelEditInProgress re-mutating it.
	 */
	public void clearEditInProgress()
	{
		doorInProgress = null;
		doorInProgressExisting = false;
		barrierInProgress = null;
		barrierInProgressExisting = false;
		editSnapshot.clear();
	}

	/**
	 * One-shot particle puff above each linked spawn so the editor can see which zombie spawns this
	 * door/barrier feeds without leaving a permanent block marker (the END_PORTAL_FRAME markers from
	 * {@link Game#showSpawnLocations()} already show every spawn; this just highlights the linked subset).
	 */
	private void highlightLinkedSpawns(List<SpawnPoint> spawns)
	{
		if(spawns == null)
			return;
		for(SpawnPoint sp : spawns)
		{
			if(sp == null)
				continue;
			Location loc = sp.getLocation();
			if(loc == null || loc.getWorld() == null)
				continue;
			loc.getWorld().spawnParticle(Compat.HAPPY_VILLAGER, loc.clone().add(0.5, 1.2, 0.5), 12, 0.3, 0.3, 0.3, 0);
		}
	}

	public Transport getTransportInProgress()
	{
		return transportInProgress;
	}

	public void startTransport(Transport transport)
	{
		transportInProgress = transport;
		transportHistory.clear();
	}

	public void clearTransportInProgress()
	{
		transportInProgress = null;
		transportHistory.clear();
	}

	public void addTransportPoint(Vector point, boolean waypoint)
	{
		if(transportInProgress == null)
			return;
		(waypoint ? transportInProgress.getWaypoints() : transportInProgress.getCallPoints()).add(point);
		transportHistory.add(waypoint);
	}

	/** Removes the most recently added point; returns false when there was nothing to undo. */
	public boolean undoTransportPoint()
	{
		if(transportInProgress == null || transportHistory.isEmpty())
			return false;
		boolean waypoint = transportHistory.remove(transportHistory.size() - 1);
		List<Vector> points = waypoint ? transportInProgress.getWaypoints() : transportInProgress.getCallPoints();
		if(!points.isEmpty())
			points.remove(points.size() - 1);
		return true;
	}

	public Transport getPendingTransportOptions()
	{
		return pendingTransportOptions;
	}

	public void setPendingTransportOptions(Transport transport)
	{
		this.pendingTransportOptions = transport;
	}

	/** True while the Feature tool is held with its Transport variant selected. */
	public boolean holdingTransportTool(Player player)
	{
		BuildTool tool = BuildTool.fromSlot(player.getInventory().getHeldItemSlot());
		return tool == BuildTool.FEATURE && BuildTool.TRANSPORT_VARIANT.equals(tool.variantLabel(getVariant(tool)));
	}

	/** Draws every transport path (and the one being built) to the builder while the Transport tool is held. */
	public void startTransportPreview(Player player)
	{
		stopTransportPreview();
		transportPreviewTask = com.theprogrammingturkey.comz.COMZombies.scheduleTask(5L, 5L, () ->
		{
			if(!player.isOnline() || !holdingTransportTool(player))
				return;
			for(Transport transport : game.transportManager.getTransports())
				drawTransport(player, transport, Color.AQUA, Color.YELLOW);
			if(transportInProgress != null)
				drawTransport(player, transportInProgress, Color.LIME, Color.ORANGE);
		});
	}

	public void stopTransportPreview()
	{
		if(transportPreviewTask != -1)
		{
			Bukkit.getScheduler().cancelTask(transportPreviewTask);
			transportPreviewTask = -1;
		}
	}

	private static void drawTransport(Player player, Transport transport, Color pathColor, Color callColor)
	{
		Particle dustType = Compat.DUST;
		if(dustType == null)
			return;
		Particle.DustOptions path = new Particle.DustOptions(pathColor, 0.8f);
		Particle.DustOptions node = new Particle.DustOptions(Color.WHITE, 1.4f);
		Particle.DustOptions call = new Particle.DustOptions(callColor, 1.6f);
		double lift = transport.getStyle() == Transport.Style.ZIPLINE ? Transport.ROPE_HEIGHT : 0.2D;
		List<Vector> points = transport.getWaypoints();
		for(int i = 0; i < points.size(); i++)
		{
			Vector a = points.get(i);
			player.spawnParticle(dustType, a.getX(), a.getY() + 0.2, a.getZ(), 2, 0.05, 0.05, 0.05, 0, node);
			if(i == 0)
				continue;
			Vector from = points.get(i - 1);
			double len = from.distance(a);
			for(double d = 0; d <= len; d += 0.5)
			{
				Vector p = from.clone().add(a.clone().subtract(from).multiply(len <= 0 ? 0 : d / len));
				player.spawnParticle(dustType, p.getX(), p.getY() + lift, p.getZ(), 1, 0, 0, 0, 0, path);
			}
		}
		for(Vector c : transport.effectiveCallPoints())
			player.spawnParticle(dustType, c.getX(), c.getY() + 0.6, c.getZ(), 3, 0.15, 0.3, 0.15, 0, call);
	}

	public Location getPendingPriceSign()
	{
		return pendingPriceSign;
	}

	public void setPendingPriceSign(Location loc)
	{
		this.pendingPriceSign = loc;
	}

	public Location getZoneCorner()
	{
		return zoneCorner;
	}

	public void setZoneCorner(Location corner)
	{
		this.zoneCorner = corner;
	}

	public Location getTrapCorner1()
	{
		return trapCorner1;
	}

	public Location getTrapCorner2()
	{
		return trapCorner2;
	}

	public String getTrapZoneId()
	{
		return trapZoneId;
	}

	public void setTrapCorner1(Location corner)
	{
		trapCorner1 = corner;
		trapCorner2 = null;
		trapZoneId = null;
	}

	public void setTrapCorner2(Location corner)
	{
		trapCorner2 = corner;
	}

	public void setTrapZone(String zoneId)
	{
		trapZoneId = zoneId;
		trapCorner1 = null;
		trapCorner2 = null;
	}

	/** True once a zone or both corners have been picked for the next trap. */
	public boolean hasTrapRegion()
	{
		return trapZoneId != null || (trapCorner1 != null && trapCorner2 != null);
	}

	public void clearTrapRegion()
	{
		trapCorner1 = null;
		trapCorner2 = null;
		trapZoneId = null;
	}

	public Trap getPendingTrapEdit()
	{
		return pendingTrapEdit;
	}

	public void setPendingTrapEdit(Trap trap)
	{
		pendingTrapEdit = trap;
	}

	public Zone getPendingZoneEdit()
	{
		return pendingZoneEdit;
	}

	public void setPendingZoneEdit(Zone zone)
	{
		this.pendingZoneEdit = zone;
	}

	/** Start drawing zone outlines for this builder; the action bar names the zone they stand in while the zone tool is held. */
	public void startZoneOutlines(Player player)
	{
		stopZoneOutlines();
		zoneOutlineTicks = 0;
		zoneOutlineTask = COMZombies.scheduleTask(0L, 10L, () ->
		{
			if(!player.isOnline())
			{
				stopZoneOutlines();
				return;
			}
			ZoneOutlines.draw(player, this);
			zoneOutlineTicks += 10;
			if(zoneOutlineTicks % 40 == 0 && isHoldingZoneTool(player))
			{
				String text = ZoneOutlines.describeZoneAt(player, this);
				if(text != null)
					player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
			}
		});
	}

	public void stopZoneOutlines()
	{
		if(zoneOutlineTask != -1)
		{
			Bukkit.getScheduler().cancelTask(zoneOutlineTask);
			zoneOutlineTask = -1;
		}
	}

	/** The zone preset selected on the Feature tool, or null when the Feature tool is on a sign variant. */
	public ZonePreset selectedZonePreset()
	{
		return ZonePreset.fromLabel(BuildTool.FEATURE.variantLabel(getVariant(BuildTool.FEATURE)));
	}

	private boolean isHoldingZoneTool(Player player)
	{
		return BuildTool.fromSlot(player.getInventory().getHeldItemSlot()) == BuildTool.FEATURE && selectedZonePreset() != null;
	}

	public Door getPendingPriceDoor()
	{
		return pendingPriceDoor;
	}

	public void setPendingPriceDoor(Door door)
	{
		this.pendingPriceDoor = door;
	}

	/** True if a place happened within the last 200ms (call to debounce rapid double right-clicks). */
	public boolean onPlaceCooldown()
	{
		long now = System.currentTimeMillis();
		if(now - lastPlaceMs < 200L)
			return true;
		lastPlaceMs = now;
		return false;
	}

	/** Turn a block into a bright selection marker, remembering its real data for later restore. */
	public void addMarker(Block block, Material marker)
	{
		if(!markerOriginals.containsKey(block.getLocation()))
			markerOriginals.put(block.getLocation(), block.getBlockData());
		block.setType(marker, false);
	}

	/** Restore one marker block to its real data (on deselect). */
	public void restoreMarker(Block block)
	{
		BlockData data = markerOriginals.remove(block.getLocation());
		if(data != null)
			block.setBlockData(data, false);
	}

	/** Restore every outstanding marker block (on finalize / exit / cancel). */
	public void restoreAllMarkers()
	{
		for(Map.Entry<Location, BlockData> e : markerOriginals.entrySet())
			if(e.getKey().getWorld() != null)
				e.getKey().getBlock().setBlockData(e.getValue(), false);
		markerOriginals.clear();
	}

	/** Human label for the active room: {@code "Starting room"} or the door id. */
	public String roomLabel()
	{
		return activeRoomDoor == null ? "Starting room" : "Door " + activeRoomDoor.doorID;
	}

	public int getVariant(BuildTool tool)
	{
		return variantIndex[tool.ordinal()];
	}

	/** Advance the tool's variant index, wrapping at {@code size}. No-op when {@code size <= 1}. */
	public void cycleVariant(BuildTool tool, int size)
	{
		if(size <= 1)
			return;
		variantIndex[tool.ordinal()] = (variantIndex[tool.ordinal()] + 1) % size;
	}

	public void clampVariant(BuildTool tool, int size)
	{
		if(size <= 0)
		{
			variantIndex[tool.ordinal()] = 0;
			return;
		}
		if(variantIndex[tool.ordinal()] >= size)
			variantIndex[tool.ordinal()] = 0;
	}

	public void addPreview(Location signLoc, List<Entity> displays)
	{
		if(displays != null && !displays.isEmpty())
			previews.put(signLoc, displays);
	}

	public void removePreview(Location signLoc)
	{
		MachineVisuals.remove(previews.remove(signLoc));
	}

	public void removeAllPreviews()
	{
		for(List<Entity> displays : previews.values())
			MachineVisuals.remove(displays);
		previews.clear();
	}

	/** Spawn (or respawn) a preview for every perk machine, Pack-a-Punch and mystery box sign in the arena. */
	public void refreshMachinePreviews(Player player)
	{
		removeAllPreviews();
		boolean usePack = MachineSigns.previewUsesPack(player);
		MachineSigns.forEachArenaSign(game, sign ->
		{
			if(MachineLooks.forSignLines(sign.getLines()) != null || MachineSigns.isWallBuy(sign))
				addPreview(sign.getLocation(), MachineSigns.previewModel(sign, usePack));
		});
	}

	/**
	 * Spawn (or refresh) a floating door-id hologram above the centre block of every registered door in
	 * this session's arena. Called on build-mode enter and whenever the door set changes (finalize/edit)
	 * so the labels stay in sync. Existing holograms are despawned first to avoid duplicates.
	 */
	public void refreshDoorHolograms()
	{
		removeDoorHolograms();
		World world = game.getWorld();
		if(world == null)
			return;
		for(Door door : game.doorManager.getDoors())
		{
			List<Block> blocks = door.getBlocks();
			if(blocks.isEmpty())
				continue;
			// Centre of the door's bounding box, hovering one block above the top of the door.
			double cx = 0, cy = 0, cz = 0;
			for(Block b : blocks)
			{
				cx += b.getX();
				cy += b.getY();
				cz += b.getZ();
			}
			cx /= blocks.size();
			cy /= blocks.size();
			cz /= blocks.size();
			Location at = new Location(world, cx + 0.5, cy + 1.4, cz + 0.5);
			String label = ChatColor.AQUA + "" + ChatColor.BOLD + "Door " + door.doorID
					+ (door.isOpened() ? ChatColor.GREEN + " (open)" : "");
			Entity hologram = DisplayEntityUtil.persistentText(world, at, label);
			if(hologram != null)
			{
				hologram.setPersistent(false);
				doorHolograms.put(door.doorID, hologram);
			}
		}
	}

	/** Despawn every door-id hologram spawned by {@link #refreshDoorHolograms()}. */
	public void removeDoorHolograms()
	{
		for(Entity hologram : doorHolograms.values())
			if(hologram != null && !hologram.isDead())
				hologram.remove();
		doorHolograms.clear();
	}

	/** Restore the player's pre-build inventory, held slot, gamemode and flight state. */
	public void restore(Player player)
	{
		player.getInventory().setContents(savedContents);
		player.getInventory().setHeldItemSlot(savedHeldSlot);
		// Set gamemode first (switching to/from creative forces allowFlight), then restore the saved flight
		// state so a survival admin doesn't keep the build-mode flight.
		player.setGameMode(savedMode);
		player.setAllowFlight(savedAllowFlight);
		player.setFlying(savedAllowFlight && savedFlying);
		player.updateInventory();
	}
}
