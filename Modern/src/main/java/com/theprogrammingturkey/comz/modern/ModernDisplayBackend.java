package com.theprogrammingturkey.comz.modern;

import com.theprogrammingturkey.comz.util.EntitySpawner;
import com.theprogrammingturkey.comz.util.display.DisplayBackend;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Display-entity backend for 1.19.4+.
 */
public class ModernDisplayBackend implements DisplayBackend
{
	@Override
	public boolean supportsItemDisplays()
	{
		return true;
	}

	@Override
	public boolean supportsHitboxes()
	{
		return true;
	}

	@Override
	public boolean supportsBlockDisplays()
	{
		return true;
	}

	@Override
	public Entity spawnBlock(Location loc, BlockData block, float cx, float cy, float cz, float sx, float sy, float sz, float yawDegrees, boolean glow)
	{
		double yaw = Math.toRadians(yawDegrees);
		float cos = (float) Math.cos(yaw);
		float sin = (float) Math.sin(yaw);
		// Left rotation turns the scaled box about its own origin, so the corner offset is rotated by hand
		// to keep every part of a composite on the same pivot (the spawn location).
		float tx = cx * cos + cz * sin;
		float tz = -cx * sin + cz * cos;
		return EntitySpawner.spawn(loc.getWorld(), loc, BlockDisplay.class, display ->
		{
			display.setBlock(block);
			display.setBillboard(Display.Billboard.FIXED);
			display.setTransformation(new Transformation(
					new Vector3f(tx, cy, tz),
					new AxisAngle4f((float) yaw, 0f, 1f, 0f),
					new Vector3f(sx, sy, sz),
					new AxisAngle4f(0f, 0f, 1f, 0f)));
			if(glow)
				display.setBrightness(new Display.Brightness(15, 15));
			// A zero-size cull box makes the part pop out whenever its origin leaves the screen.
			display.setDisplayWidth(4f);
			display.setDisplayHeight(4f);
			display.setViewRange(1.5f);
		});
	}

	@Override
	public Entity spawnItem(Location loc, ItemStack stack, float scale, float yawDegrees, float offsetY, boolean faceViewer)
	{
		return EntitySpawner.spawn(loc.getWorld(), loc, ItemDisplay.class, display ->
		{
			display.setItemStack(stack);
			display.setBillboard(faceViewer ? Display.Billboard.CENTER : Display.Billboard.FIXED);
			display.setTransformation(transform(0f, offsetY, 0f, scale, yawDegrees));
			display.setInterpolationDelay(0);
		});
	}

	@Override
	public void animate(Entity entity, float x, float y, float z, float scale, float yawDegrees, int durationTicks)
	{
		if(!(entity instanceof Display))
			return;
		Display display = (Display) entity;
		display.setInterpolationDelay(0);
		display.setInterpolationDuration(durationTicks);
		display.setTransformation(transform(x, y, z, scale, yawDegrees));
	}

	@Override
	public void setItem(Entity entity, ItemStack stack)
	{
		if(entity instanceof ItemDisplay)
			((ItemDisplay) entity).setItemStack(stack);
	}

	@Override
	public void setBrightness(Entity entity, int block, int sky)
	{
		if(!(entity instanceof Display))
			return;
		((Display) entity).setBrightness(block < 0 ? null : new Display.Brightness(block, sky));
	}

	@Override
	public Entity spawnText(Location loc, String text, boolean seeThrough)
	{
		return EntitySpawner.spawn(loc.getWorld(), loc, TextDisplay.class, display ->
		{
			display.setText(text);
			display.setBillboard(Display.Billboard.CENTER);
			display.setSeeThrough(seeThrough);
			display.setDefaultBackground(false);
		});
	}

	@Override
	public Entity spawnHitbox(Location loc, float width, float height)
	{
		return EntitySpawner.spawn(loc.getWorld(), loc, Interaction.class, interaction ->
		{
			interaction.setInteractionWidth(width);
			interaction.setInteractionHeight(height);
			interaction.setResponsive(true);
		});
	}

	private static Transformation transform(float x, float y, float z, float scale, float yawDegrees)
	{
		return new Transformation(
				new Vector3f(x, y, z),
				new AxisAngle4f((float) Math.toRadians(yawDegrees), 0f, 1f, 0f),
				new Vector3f(scale, scale, scale),
				new AxisAngle4f(0f, 0f, 1f, 0f));
	}
}
