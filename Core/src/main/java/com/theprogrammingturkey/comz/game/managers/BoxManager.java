package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.ParticleFX;
import com.theprogrammingturkey.comz.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class BoxManager
{
	private final Game game;
	private final List<RandomBox> boxes = new ArrayList<>();
	private RandomBox currentBox;
	private boolean multiBox;
	private boolean boxHasMoved;
	/** Repeating task id drawing the mystery-box locator beam(s); -1 when not running. */
	private int beamTaskId = -1;

	public BoxManager(Game game)
	{
		this.game = game;
	}

	public void loadAllBoxesToGame(JsonArray mystery_boxes, JsonObject arenaSettingsJson)
	{
		multiBox = CustomConfig.getBoolean(arenaSettingsJson, "multiple_mystery_boxes", false);
		boxes.clear();
		for(JsonElement boxElem : mystery_boxes)
		{
			if(!boxElem.isJsonObject())
				continue;
			JsonObject boxJson = boxElem.getAsJsonObject();
			Location loc = CustomConfig.getLocationWithWorld(boxJson, "", game.getWorld());
			String facing = CustomConfig.getString(boxJson, "facing", "");
			int cost = CustomConfig.getInt(boxJson, "cost", 2000);
			String boxId = CustomConfig.getString(boxJson, "id", "MISSING");
			RandomBox point = new RandomBox(loc, BlockFace.valueOf(facing), game, boxId, cost);
			boxes.add(point);
		}
	}

	public JsonArray save()
	{
		JsonArray saveJson = new JsonArray();
		for(RandomBox box : boxes)
		{
			JsonObject boxJson = CustomConfig.locationToJsonNoWorld(box.getLocation());
			boxJson.addProperty("facing", box.getFacing().name());
			boxJson.addProperty("cost", box.getCost());
			boxJson.addProperty("id", box.getId());
			saveJson.add(boxJson);
		}
		return saveJson;
	}

	public void resetBoxes()
	{
		boxHasMoved = false;
		if(beamTaskId != -1)
		{
			Bukkit.getScheduler().cancelTask(beamTaskId);
			beamTaskId = -1;
		}
		for(RandomBox box : this.boxes)
		{
			box.reset();
			box.resetUses();
		}
	}

	public List<RandomBox> getAllBoxes()
	{
		return java.util.Collections.unmodifiableList(boxes);
	}

	public RandomBox getBox(String id)
	{
		for(RandomBox b : boxes)
			if(id.equalsIgnoreCase(b.getId()))
				return b;
		return null;
	}

	public RandomBox getBox(Location loc)
	{
		for(RandomBox b : boxes)
			if(b.getLocation().equals(loc))
				return b;
		return null;
	}

	public RandomBox getRandomBox(RandomBox exclude)
	{
		if(boxes.isEmpty())
			return null;

		// Just to prevent infinite loop below
		if(boxes.size() == 1)
			return boxes.get(0);

		RandomBox newBox;
		do
			newBox = boxes.get(COMZombies.rand.nextInt(boxes.size()));
		while(newBox == exclude);

		return newBox;
	}

	public void removeBox(Player player, RandomBox box)
	{
		if(boxes.contains(box))
		{
			player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "MysteryBox removed!");
			BlockUtils.setBlockToAir(box.getLocation());
			boxes.remove(box);
			GameManager.INSTANCE.saveAllGames();
		}
	}

	public void addBox(RandomBox box)
	{
		if(game.getStatus() == Game.GameStatus.DISABLED || game.getStatus() == Game.GameStatus.WAITING)
		{
			boolean same = false;
			for(RandomBox b : boxes)
			{
				if(b.getLocation().equals(box.getLocation()))
				{
					same = true;
					break;
				}
			}
			if(!same)
			{
				boxes.add(box);
				GameManager.INSTANCE.saveAllGames();
			}
		}
	}

	public RandomBox getCurrentbox()
	{
		return currentBox;
	}

	/** Whether a box location already exists within {@code range} blocks of {@code loc}. */
	public boolean hasBoxNear(Location loc, double range)
	{
		for(RandomBox box : boxes)
		{
			Location other = box.getLocation();
			if(other != null && other.getWorld() != null && other.getWorld().equals(loc.getWorld()) && other.distanceSquared(loc) <= range * range)
				return true;
		}
		return false;
	}

	public int getNumBoxes()
	{
		return boxes.size();
	}

	public void setCurrentBox(RandomBox box)
	{
		if(multiBox)
		{
			currentBox = null;
			return;
		}
		if(currentBox != null)
			currentBox.removeBox();
		currentBox = box;
		currentBox.loadBox();
		ensureBeamTask();
	}

	/**
	 * Starts (once) the locator beam that marks the active mystery box so players can find it from
	 * across the map — single-box mode beams the current box, multi-box mode beams them all. The task
	 * self-cancels when the game is no longer INGAME, and is gated by config.visuals.boxBeam.
	 */
	private void ensureBeamTask()
	{
		if(beamTaskId != -1 || !ConfigManager.getMainConfig().visualsBoxBeam)
			return;
		final int[] ticks = {0};
		beamTaskId = COMZombies.scheduleTask(0, 8, () ->
		{
			ticks[0]++;
			if(game.getStatus() != Game.GameStatus.INGAME)
			{
				Bukkit.getScheduler().cancelTask(beamTaskId);
				beamTaskId = -1;
				return;
			}
			List<RandomBox> active = multiBox ? boxes : (currentBox != null ? java.util.Collections.singletonList(currentBox) : java.util.Collections.emptyList());
			for(RandomBox b : active)
			{
				Location base = b.getLocation();
				if(base != null && base.getWorld() != null)
				{
					ParticleFX.beam(base.getWorld(), base.clone().add(0.5, 1.0, 0.5), 2.5, Color.fromRGB(0xFF, 0xD7, 0x00));
					if(ticks[0] % 3 == 0)
						ParticleFX.lightColumn(base.getWorld(), base.clone().add(0.5, 1.0, 0.5), 12.0);
				}
			}
		});
	}

	public void FireSale()
	{
		if(game.isFireSale())
		{
			loadAllBoxes();
		}
		else
		{
			unloadAllBoxes();
			if(currentBox != null)
				currentBox.loadBox();
			else
				loadAllBoxes();
		}
	}

	public void unloadAllBoxes()
	{
		for(RandomBox b : boxes)
			b.removeBox();
	}

	public void loadAllBoxes()
	{
		for(RandomBox b : boxes)
			b.loadBox();
		ensureBeamTask();
	}

	// Finds the closest locations to point loc, it results numToGet amount of
	// spawn points

	public int getTotalBoxes()
	{
		return boxes.size();
	}

	public boolean isMultiBox()
	{
		return multiBox;
	}

	/**
	 * The boxes that are currently represented in the world (a chest at their location): every box in
	 * multi-box mode, otherwise just the single active {@link #currentBox}. Used by the proximity-buy
	 * scanner so F only targets boxes the player can actually see/interact with.
	 */
	public List<RandomBox> getActiveBoxes()
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
			return java.util.Collections.emptyList();
		if(multiBox)
			return new ArrayList<>(boxes);
		return currentBox != null ? java.util.Collections.singletonList(currentBox) : java.util.Collections.emptyList();
	}

	public void setMultiBox(boolean multiBox)
	{
		this.multiBox = multiBox;
	}

	public Game getGame()
	{
		return game;
	}

	public String getNextBoxName()
	{
		return Util.genRandId();
	}

	public boolean hasBoxMoved()
	{
		return boxHasMoved;
	}

	public void teddyBear()
	{
		boxHasMoved = true;
		currentBox.removeBox();
		RandomBox b = getRandomBox(currentBox);
		if(b != null)
			currentBox = b;
		currentBox.loadBox();
	}
}