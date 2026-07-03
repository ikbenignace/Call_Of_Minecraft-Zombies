package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

/**
 * A physical machine in the world: a solid base block (always visible, even without the pack), an
 * optional 3D {@link ItemDisplay} overlay that covers the block when the pack is on, an invisible
 * {@link Interaction} hitbox that makes it right-clickable (forwarding to the machine's sign logic),
 * and a floating price {@link TextDisplay}. Kept together so the whole machine tears down cleanly.
 *
 * <p>While the machine is spawned the backing feature sign is removed from the world (the machine
 * is the in-game representation); its text + facing BlockData are captured here so the buy logic can
 * still read type/cost, and the sign is restored on {@link #remove()}.
 */
public class MachineModel
{
	/** The block the machine occupies (its base). */
	final Location baseLoc;
	/** The original block data at {@code baseLoc}, restored on removal. */
	final BlockData originalBlock;
	/** Location of the backing feature sign (the data source: type + cost). */
	final Location signLoc;
	/** The sign's 4 lines, captured at spawn so the buy logic works after the sign block is cleared. */
	String[] signLines;
	/** The sign's BlockData (its facing), captured so the sign is restored exactly on teardown. */
	BlockData signBlockData;

	ItemDisplay model;      // null when the pack isn't guaranteed for all players
	Interaction interaction;
	TextDisplay hologram;

	MachineModel(Location baseLoc, BlockData originalBlock, Location signLoc)
	{
		this.baseLoc = baseLoc;
		this.originalBlock = originalBlock;
		this.signLoc = signLoc;
	}

	/** Despawn every entity, restore the base block, and put the feature sign back. */
	void remove()
	{
		try
		{
			if(model != null && !model.isDead())
				model.remove();
			if(interaction != null && !interaction.isDead())
				interaction.remove();
			if(hologram != null && !hologram.isDead())
				hologram.remove();
			if(baseLoc.getWorld() != null && originalBlock != null)
				baseLoc.getBlock().setBlockData(originalBlock, false);
			// Restore the backing feature sign exactly as it was (text + facing).
			if(signLoc != null && signLoc.getWorld() != null && signBlockData != null)
			{
				signLoc.getBlock().setBlockData(signBlockData, false);
				if(signLoc.getBlock().getState() instanceof org.bukkit.block.Sign restored)
				{
					for(int i = 0; i < signLines.length && i < 4; i++)
						restored.setLine(i, signLines[i]);
					restored.update(true);
				}
			}
		}
		catch(Exception e)
		{
			COMZombies.log.warning("Failed to remove a machine model: " + e.getMessage());
		}
	}
}

