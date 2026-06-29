package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.ModelDisplay;
import com.theprogrammingturkey.comz.util.PackModels;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ItemDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * BO2-fidelity layer: spawns static 3D machine models (Pack-a-Punch, per-perk Perk-a-Cola,
 * mystery box) next to their existing feature signs and tears them down on game end.
 *
 * <p>Feature signs are not tracked in a central list — they are matched at interact time from sign
 * text (see {@code SignListener}). So at game start we scan the arena's loaded chunk tile-entities
 * (signs are tile entities, so this is cheap — no per-block volume scan) for the relevant sign types
 * and spawn one {@link ItemDisplay} per machine. Every display is remembered for removal.
 *
 * <p>The whole layer is gated behind {@link PackModels#isPackEnabled()}: pack off → nothing spawns
 * and the existing sign-only visuals remain (dual-path).
 */
public class MachineModelManager
{
	private final Game game;
	private final List<ItemDisplay> displays = new ArrayList<>();

	public MachineModelManager(Game game)
	{
		this.game = game;
	}

	/** Scan the arena for feature signs and spawn the matching machine models. No-op without a pack. */
	public void spawnAll()
	{
		removeAll();
		if(!PackModels.isPackEnabled())
			return;

		World world = game.arena.getWorld();
		if(world == null || !game.arena.areMinAndMaxSet())
			return;

		Location min = game.arena.getMin();
		Location max = game.arena.getMax();
		int minCX = Math.min(min.getBlockX(), max.getBlockX()) >> 4;
		int maxCX = Math.max(min.getBlockX(), max.getBlockX()) >> 4;
		int minCZ = Math.min(min.getBlockZ(), max.getBlockZ()) >> 4;
		int maxCZ = Math.max(min.getBlockZ(), max.getBlockZ()) >> 4;

		for(int cx = minCX; cx <= maxCX; cx++)
		{
			for(int cz = minCZ; cz <= maxCZ; cz++)
			{
				if(!world.isChunkLoaded(cx, cz))
					continue;
				for(BlockState state : world.getChunkAt(cx, cz).getTileEntities())
				{
					if(!(state instanceof Sign))
						continue;
					if(!game.arena.containsBlock(state.getLocation()))
						continue;
					trySpawnForSign((Sign) state);
				}
			}
		}
	}

	private void trySpawnForSign(Sign sign)
	{
		String type = ChatColor.stripColor(sign.getLine(1)).trim().toLowerCase();
		String modelKey;
		if(type.equals("pack-a-punch"))
		{
			modelKey = "machine/pap";
		}
		// Mystery box visuals are owned by RandomBox (it knows the adjacent chest location + lid animation).
		else if(type.equals("perk machine"))
		{
			PerkType perk = PerkType.getPerkType(ChatColor.stripColor(sign.getLine(2)));
			if(perk == null)
				return;
			modelKey = perk.getMachineModelKey();
		}
		else
		{
			return;
		}
		if(modelKey == null)
			return;

		spawn(sign, modelKey);
	}

	/**
	 * Spawn a machine model in front of the wall sign, centred on the block the sign is attached to and
	 * offset out along the sign's facing so it stands in the open rather than inside the wall.
	 */
	private void spawn(Sign sign, String modelKey)
	{
		Location signLoc = sign.getLocation();
		BlockFace facing = BlockFace.NORTH;
		BlockData data = sign.getBlockData();
		if(data instanceof Directional)
			facing = ((Directional) data).getFacing();

		float yaw = yawFromFace(facing);
		Location at = signLoc.clone().add(0.5 + facing.getModX() * 0.4, 0.0, 0.5 + facing.getModZ() * 0.4);

		ItemDisplay display = ModelDisplay.spawnModel(signLoc.getWorld(), at, modelKey, 1.0f, yaw);
		if(display != null)
			displays.add(display);
	}

	/** Yaw (degrees) so a FIXED display faces outward along {@code face}. */
	private static float yawFromFace(BlockFace face)
	{
		switch(face)
		{
			case SOUTH:
				return 0f;
			case WEST:
				return 90f;
			case NORTH:
				return 180f;
			case EAST:
				return 270f;
			default:
				return 0f;
		}
	}

	/** Remove all spawned machine displays. Safe to call repeatedly. */
	public void removeAll()
	{
		for(ItemDisplay display : displays)
		{
			try
			{
				if(display != null && !display.isDead())
					display.remove();
			} catch(Exception e)
			{
				COMZombies.log.warning("Failed to remove a machine model display: " + e.getMessage());
			}
		}
		displays.clear();
	}
}
