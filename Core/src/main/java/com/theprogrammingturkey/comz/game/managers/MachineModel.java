package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.visuals.MachineVisuals;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * A physical machine in the world: a solid base block (always visible, even without the pack), an
 * 3D item model (pack guaranteed) or a block-display look-alike overlaying the block, an invisible
 * interaction hitbox that makes it right-clickable (forwarding to the machine's sign logic),
 * and a floating price text. The machine occupies the backing sign's own block, so the
 * sign's material, facing and text are snapshotted here and fully restored on removal. Kept together
 * so the whole machine tears down cleanly.
 */
public class MachineModel
{
	/** The block the machine occupies (its base). Always the sign's own block. */
	final Location baseLoc;
	/** Location of the backing feature sign (the data source: type + cost). Same block as {@link #baseLoc}. */
	final Location signLoc;
	/** The sign's material (e.g. {@code OAK_WALL_SIGN}), restored on removal. */
	private final Material signMaterial;
	/** The sign's block data (carries facing), restored on removal. */
	private final BlockData signData;
	/** The sign's four lines (with color codes), captured before overwrite and restored on removal. */
	final String[] signLines;

	/** Pack item model, or the block-display look-alike when the pack isn't guaranteed for all players. */
	final List<Entity> visuals = new ArrayList<>();
	Entity interaction;
	Entity hologram;
	String label;

	MachineModel(Location baseLoc, Location signLoc, Material signMaterial, BlockData signData, String[] signLines)
	{
		this.baseLoc = baseLoc;
		this.signLoc = signLoc;
		this.signMaterial = signMaterial;
		this.signData = signData;
		this.signLines = signLines;
	}

	/**
	 * Despawn every entity and rebuild the backing sign (material + facing + text) so the arena is left
	 * exactly as it was before the machine was spawned.
	 */
	void remove()
	{
		try
		{
			MachineVisuals.remove(visuals);
			if(interaction != null && !interaction.isDead())
				interaction.remove();
			if(hologram != null && !hologram.isDead())
				hologram.remove();
			if(baseLoc.getWorld() != null)
			{
				// Restore material + facing first, then re-fetch the fresh tile state to write the text
				// back (setType/setBlockData drops the Sign tile state, so the lines can't be set until the
				// sign material is back in place).
				baseLoc.getBlock().setType(signMaterial, false);
				if(signData != null)
					baseLoc.getBlock().setBlockData(signData, false);
				if(signLines != null)
				{
					org.bukkit.block.BlockState state = baseLoc.getBlock().getState();
					if(state instanceof Sign)
					{
						Sign sign = (Sign) state;
						for(int i = 0; i < signLines.length && i < 4; i++)
							sign.setLine(i, signLines[i]);
						sign.update(true);
					}
				}
			}
		}
		catch(Exception e)
		{
			COMZombies.log.warning("Failed to remove a machine model: " + e.getMessage());
		}
	}
}