package com.theprogrammingturkey.comz.modern;

import com.theprogrammingturkey.comz.util.EntitySpawner;
import com.theprogrammingturkey.comz.util.display.DisplayBackend;
import org.bukkit.Location;
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
