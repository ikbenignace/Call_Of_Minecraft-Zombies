package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;

/**
 * A sign captured before it is hidden for a game: where it was, what it was and what it said, so it can
 * be rebuilt exactly when the game is torn down.
 */
final class SignSnapshot
{
	private final Location location;
	private final Material material;
	private final BlockData data;
	private final String[] lines;
	private final DyeColor color;

	private SignSnapshot(Location location, Material material, BlockData data, String[] lines, DyeColor color)
	{
		this.location = location;
		this.material = material;
		this.data = data;
		this.lines = lines;
		this.color = color;
	}

	static SignSnapshot of(Sign sign)
	{
		return new SignSnapshot(sign.getLocation(), sign.getType(), sign.getBlockData(), sign.getLines().clone(), sign.getColor());
	}

	Location location()
	{
		return location;
	}

	void hide()
	{
		HiddenSignJournal.record(location, material, data, lines, color, Material.AIR);
		location.getBlock().setType(Material.AIR, false);
	}

	void restore()
	{
		if(restoreBlock(location, material, data, lines, color))
			HiddenSignJournal.clear(location);
	}

	static boolean restoreBlock(Location location, Material material, BlockData data, String[] lines, DyeColor color)
	{
		try
		{
			Block block = location.getBlock();
			block.setType(material, false);
			if(data != null)
				block.setBlockData(data, false);
			BlockState state = block.getState();
			if(state instanceof Sign)
			{
				Sign sign = (Sign) state;
				for(int i = 0; i < lines.length && i < 4; i++)
					sign.setLine(i, lines[i]);
				if(color != null)
					sign.setColor(color);
				sign.update(true);
			}
			return true;
		} catch(RuntimeException e)
		{
			COMZombies.log.warning("Failed to restore a hidden sign at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ": " + e.getMessage());
			return false;
		}
	}
}
