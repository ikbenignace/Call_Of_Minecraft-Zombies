package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Door;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
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

	/** Selected variant index per tool, indexed by {@link BuildTool#ordinal()}. */
	private final int[] variantIndex = new int[BuildTool.values().length];

	/** Preview machine models keyed by the sign location they belong to, for targeted + bulk removal. */
	private final Map<Location, ItemDisplay> previews = new HashMap<>();

	/**
	 * The room whose spawns/barriers new placements are linked to. {@code null} = the starting room
	 * (spawns with no door gate, always active). When a door is the active room, spawns placed get
	 * {@code door.addSpawnPoint} so they only activate once that door is opened.
	 */
	private Door activeRoomDoor;

	/** A door currently being block-selected with the Door tool (before it is finalized), or null. */
	private Door doorInProgress;

	public BuildSession(Player player, Game game)
	{
		this.game = game;
		this.savedContents = player.getInventory().getContents();
		this.savedMode = player.getGameMode();
		this.savedHeldSlot = player.getInventory().getHeldItemSlot();
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

	/** Restore the player's pre-build inventory, held slot and gamemode. */
	public void restore(Player player)
	{
		player.getInventory().setContents(savedContents);
		player.getInventory().setHeldItemSlot(savedHeldSlot);
		player.setGameMode(savedMode);
		player.updateInventory();
	}
}
