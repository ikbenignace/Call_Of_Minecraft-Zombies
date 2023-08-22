package com.theprogrammingturkey.comz;

import java.util.logging.Level;

import org.bukkit.Bukkit;

import dev.geco.gsit.api.GSitAPI;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

public class GSit {
    private boolean enabled = false;

    public GSit()
	{
		if(Bukkit.getPluginManager().isPluginEnabled("GSit"))
		{
			COMZombies.log.log(Level.INFO, COMZombies.CONSOLE_PREFIX + " GSit hook enabled!");
            GSitAPI.getInstance().getCManager().GET_UP_SNEAK = false;
            GSitAPI.getInstance().getCManager().P_POSE_MESSAGE = false;
            GSitAPI.getInstance().getCManager().PS_ALLOW_SIT = false;
            GSitAPI.getInstance().getCManager().P_INTERACT = true;
            this.enabled = true;
		}
	}

    public boolean isEnabled() {
        return enabled;
    }

    public void startCrawl(Player player)
    {
        if (enabled) {
            GSitAPI.startCrawl(player);
        }
    }

    public void stopCrawl(Player player) {
        if (enabled) {
            GSitAPI.stopCrawl(player, null);
        }
    }

    public void startLaying(Player player)
    {
        if (enabled) {
            Block block = player.getLocation().getBlock().getRelative(0, -1, 0);
            GSitAPI.createPose(block, player, Pose.SLEEPING);
        }
    }

    public void stopPose(Player player)
    {
        if (enabled) {
            GSitAPI.removePose(player, null);
        }
    }
}
