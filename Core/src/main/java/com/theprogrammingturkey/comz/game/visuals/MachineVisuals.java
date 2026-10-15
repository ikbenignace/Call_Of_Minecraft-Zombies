package com.theprogrammingturkey.comz.game.visuals;

import com.theprogrammingturkey.comz.game.visuals.MachineLooks.Look;
import com.theprogrammingturkey.comz.game.visuals.MachineLooks.Part;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.ModelDisplay;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Spawns and tears down the display entities of one machine: the pack's 3D item model when every viewer
 * has the pack, otherwise a block-display composite from {@link MachineLooks}.
 */
public final class MachineVisuals
{
	private MachineVisuals()
	{
	}

	/**
	 * Item-model yaw for a machine whose front should face {@code face}. Pack models have their front on -Z,
	 * but the vanilla item display renderer turns item models 180 degrees about Y, so at yaw 0 the front looks
	 * toward +Z (south). A display yaw of t then makes the front look along (sin t, cos t): south 0, east 90,
	 * north 180, west 270.
	 */
	public static float modelYaw(BlockFace face)
	{
		switch(face)
		{
			case EAST:
				return 90f;
			case NORTH:
				return 180f;
			case WEST:
				return 270f;
			default:
				return 0f;
		}
	}

	/**
	 * Block displays are not flipped by the renderer, so a composite authored with its front on -Z needs the
	 * model yaw plus 180 degrees to put that front on the same side as the item model.
	 */
	private static float blockYaw(BlockFace face)
	{
		return modelYaw(face) + 180f;
	}

	/**
	 * @param block   the machine's floor block (the sign / chest block)
	 * @param usePack true when the pack model may be used instead of the block look-alike
	 * @return every entity spawned, empty when the server cannot show any display entity
	 */
	public static List<Entity> spawn(Location block, BlockFace facing, Look look, boolean usePack)
	{
		List<Entity> spawned = new ArrayList<>();
		World world = block.getWorld();
		if(world == null || look == null)
			return spawned;

		Location centre = block.clone().add(0.5, 0.0, 0.5);

		if(usePack && look.modelKey != null)
		{
			// The model's point (8,8,8) sits on the entity, so lift half a block to put model y 0 on the floor.
			Entity model = ModelDisplay.spawnModel(world, centre.clone().add(0.0, 0.5, 0.0), look.modelKey, look.modelScale, modelYaw(facing));
			if(model != null)
			{
				model.setPersistent(false);
				lightLikeSurroundings(model);
				spawned.add(model);
				return spawned;
			}
		}

		if(!Displays.supportsBlockDisplays())
			return spawned;
		float yaw = blockYaw(facing);
		for(Part part : look.parts)
		{
			BlockData data = resolve(part.block);
			Entity entity = Displays.spawnBlock(centre, data, part.x0, part.y0, part.z0, part.x1 - part.x0, part.y1 - part.y0, part.z1 - part.z0, yaw, part.glow);
			if(entity != null)
			{
				entity.setPersistent(false);
				if(!part.glow)
					lightLikeSurroundings(entity);
				spawned.add(entity);
			}
		}
		return spawned;
	}

	private static final int DIM_LIGHT = 3;

	/**
	 * Lights the machine up or darkens it. Lit machines glow at full brightness on their glow parts (the whole
	 * model for the pack); unpowered ones are drawn dim, glow parts included.
	 */
	public static void setPowered(List<Entity> entities, Look look, boolean powered)
	{
		boolean perPart = look != null && entities.size() == look.partCount();
		for(int i = 0; i < entities.size(); i++)
		{
			Entity entity = entities.get(i);
			if(!powered)
				Displays.setBrightness(entity, DIM_LIGHT, DIM_LIGHT);
			else if(!perPart || look.isGlowPart(i))
				Displays.setBrightness(entity, 15, 15);
			else
				lightLikeSurroundings(entity);
		}
	}

	/**
	 * A display takes its light from the block it stands in, which for a machine is its own solid base block,
	 * so it would render pitch black; borrow the brightest open neighbour instead.
	 */
	public static void lightLikeSurroundings(Entity display)
	{
		Block block = display.getLocation().getBlock();
		int blockLight = 0;
		int skyLight = 0;
		for(BlockFace face : AROUND)
		{
			Block side = block.getRelative(face);
			if(side.getType().isOccluding())
				continue;
			blockLight = Math.max(blockLight, side.getLightFromBlocks());
			skyLight = Math.max(skyLight, side.getLightFromSky());
		}
		Displays.setBrightness(display, blockLight, skyLight);
	}

	private static final BlockFace[] AROUND = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

	public static void remove(List<Entity> entities)
	{
		if(entities == null)
			return;
		for(Entity entity : entities)
			if(entity != null && !entity.isDead())
				entity.remove();
		entities.clear();
	}

	public static boolean anyAlive(List<Entity> entities)
	{
		for(Entity entity : entities)
			if(entity != null && !entity.isDead())
				return true;
		return false;
	}

	private static BlockData resolve(String spec)
	{
		String state = "";
		String names = spec;
		int bracket = spec.indexOf('[');
		if(bracket >= 0)
		{
			state = spec.substring(bracket);
			names = spec.substring(0, bracket);
		}
		Material material = Compat.materialOr(Material.STONE, names.split("\\|"));
		if(!state.isEmpty())
		{
			try
			{
				return Bukkit.createBlockData(material.name().toLowerCase(Locale.ROOT) + state);
			} catch(IllegalArgumentException ignored)
			{
				// Unknown state on this version: the plain block still gives the right colour.
			}
		}
		return material.createBlockData();
	}
}
