package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.DisplayEntityUtil;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

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

	/** Preview machine models keyed by the sign location they belong to, for targeted + bulk removal. */
	private final Map<Location, ItemDisplay> previews = new HashMap<>();

	/**
	 * Floating door-id holograms shown above every registered door's centre block while in build mode,
	 * so the editor can tell doors apart at a glance. Keyed by door id; despawned on exit.
	 */
	private final Map<String, TextDisplay> doorHolograms = new HashMap<>();

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
			loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.2, 0.5), 12, 0.3, 0.3, 0.3, 0);
		}
	}

	public Location getPendingPriceSign()
	{
		return pendingPriceSign;
	}

	public void setPendingPriceSign(Location loc)
	{
		this.pendingPriceSign = loc;
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

	public void addPreview(Location signLoc, ItemDisplay display)
	{
		if(display != null)
			previews.put(signLoc, display);
	}

	public void removePreview(Location signLoc)
	{
		ItemDisplay display = previews.remove(signLoc);
		if(display != null && !display.isDead())
			display.remove();
	}

	public void removeAllPreviews()
	{
		for(ItemDisplay display : previews.values())
			if(display != null && !display.isDead())
				display.remove();
		previews.clear();
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
			TextDisplay hologram = DisplayEntityUtil.persistentText(world, at, label);
			if(hologram != null)
				doorHolograms.put(door.doorID, hologram);
		}
	}

	/** Despawn every door-id hologram spawned by {@link #refreshDoorHolograms()}. */
	public void removeDoorHolograms()
	{
		for(TextDisplay hologram : doorHolograms.values())
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
