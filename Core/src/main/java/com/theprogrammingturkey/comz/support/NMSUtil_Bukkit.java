package com.theprogrammingturkey.comz.support;

import com.theprogrammingturkey.comz.api.INMSUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;

/**
 * {@link INMSUtil} backed only by the Bukkit API, no CraftBukkit/NMS internals. {@code Lidded} and
 * {@code Player#sendBlockDamage} only exist from 1.16, so they are looked up reflectively; on older
 * servers the chest lid and barrier crack overlays are skipped.
 */
public class NMSUtil_Bukkit implements INMSUtil
{
	private static final Class<?> LIDDED = findClass("org.bukkit.block.Lidded");
	private static final Method LID_OPEN = findMethod(LIDDED, "open");
	private static final Method LID_CLOSE = findMethod(LIDDED, "close");
	private static final Method SEND_BLOCK_DAMAGE = findMethod(Player.class, "sendBlockDamage", Location.class, float.class);

	public static boolean isFullySupported()
	{
		return LID_OPEN != null && LID_CLOSE != null && SEND_BLOCK_DAMAGE != null;
	}

	@Override
	public void playChestAction(Location location, boolean open)
	{
		if(location.getWorld() == null || LIDDED == null)
			return;
		BlockState bs = location.getBlock().getState();
		if(LIDDED.isInstance(bs))
			invoke(open ? LID_OPEN : LID_CLOSE, bs);
	}

	@Override
	public void playBlockBreakAction(List<Player> players, int breakerId, int damage, Block block)
	{
		if(SEND_BLOCK_DAMAGE == null)
			return;
		// "damage" is the vanilla destroy stage: -1 clears the overlay, 0..9 are crack stages.
		// sendBlockDamage takes progress 0.0 (no damage) .. 1.0 (most damaged); clamping maps
		// the clear case (-1) to 0.0 = no overlay, and 0..9 onto the crack stages.
		float progress = Math.max(0.0f, Math.min(1.0f, damage / 9.0f));
		Location loc = block.getLocation();
		for(Player player : players)
			invoke(SEND_BLOCK_DAMAGE, player, loc, progress);
	}

	private static void invoke(Method method, Object target, Object... args)
	{
		if(method == null)
			return;
		try
		{
			method.invoke(target, args);
		} catch(ReflectiveOperationException ignored)
		{
		}
	}

	private static Class<?> findClass(String name)
	{
		try
		{
			return Class.forName(name);
		} catch(ClassNotFoundException e)
		{
			return null;
		}
	}

	private static Method findMethod(Class<?> owner, String name, Class<?>... params)
	{
		if(owner == null)
			return null;
		try
		{
			return owner.getMethod(name, params);
		} catch(NoSuchMethodException e)
		{
			return null;
		}
	}
}
