package com.theprogrammingturkey.comz.api;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

public interface INMSUtil
{
	void playChestAction(Location location, boolean open);

	/**
	 * Sends a block-break progress overlay to the given players.
	 *
	 * @param breakerId a stable, per-block id so the client tracks each barrier block's break
	 *                 stage independently. Reusing the same id for multiple blocks makes the
	 *                 client clobber earlier blocks' progress — exactly the inconsistent
	 *                 breaking-levels bug. The caller should derive this from the block position.
	 * @param damage   the vanilla destroy stage: -1 clears the overlay, 0..9 are crack stages.
	 * @param block    the block to render the break overlay on.
	 */
	void playBlockBreakAction(List<Player> players, int breakerId, int damage, Block block);
}