package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class PointPopup
{
	private static final int LIFETIME_TICKS = 20;
	private static final int KILL_THRESHOLD = 50;

	private PointPopup()
	{
	}

	/** Action-bar tally for the earner, plus a floating number over the zombie on bigger payouts. */
	public static void show(Player player, Entity source, int points)
	{
		if(points <= 0)
			return;
		String text = "+" + points;
		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + text));
		if(points >= KILL_THRESHOLD)
			floatAbove(source.getLocation().add(0, source.getHeight() + 0.2, 0), ChatColor.GOLD + "" + ChatColor.BOLD + text);
	}

	private static void floatAbove(Location loc, String text)
	{
		World world = loc.getWorld();
		if(world == null)
			return;
		ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
		stand.setVisible(false);
		stand.setGravity(false);
		stand.setMarker(true);
		stand.setInvulnerable(true);
		stand.setPersistent(false);
		stand.setCustomName(text);
		stand.setCustomNameVisible(true);
		COMZombies.scheduleTask(LIFETIME_TICKS, () ->
		{
			if(!stand.isDead())
				stand.remove();
		});
	}
}
