package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.DownedPlayer;
import com.theprogrammingturkey.comz.game.features.PerkType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DownedPlayerManager
{
	private final List<DownedPlayer> downedPlayers = new ArrayList<>();

	/**
	 * Tier 3 — Tombstone Soda: perks snapshotted when a player holding Tombstone Soda goes down,
	 * keyed by player UUID, to be re-granted (one-shot) when they reclaim on revive/respawn.
	 */
	private final Map<UUID, List<PerkType>> tombstoneSnapshots = new HashMap<>();

	/**
	 * Tier 3 — pure reclaim gate. Perks are re-granted only when a saved tombstone snapshot exists
	 * for the player AND the Tombstone Soda feature is enabled in config.
	 */
	public static boolean shouldReclaim(boolean hasSnapshot, boolean enabled)
	{
		return hasSnapshot && enabled;
	}

	/**
	 * Tier 3 — store the perks a downed player held so they can be reclaimed later. Replaces any
	 * existing snapshot for that player.
	 */
	public void storeTombstoneSnapshot(UUID playerId, List<PerkType> perks)
	{
		tombstoneSnapshots.put(playerId, new ArrayList<>(perks));
	}

	/**
	 * Tier 3 — true when a tombstone snapshot is currently held for the given player.
	 */
	public boolean hasTombstoneSnapshot(UUID playerId)
	{
		return tombstoneSnapshots.containsKey(playerId);
	}

	/**
	 * Tier 3 — consume (remove and return) the tombstone snapshot for a player, or null if none.
	 * One-shot: a snapshot can only be reclaimed once.
	 */
	public List<PerkType> consumeTombstoneSnapshot(UUID playerId)
	{
		return tombstoneSnapshots.remove(playerId);
	}

	/**
	 * Tier 3 — if the player has a saved tombstone snapshot and the feature is enabled, re-grant
	 * each saved perk and clear the snapshot. No-op otherwise.
	 */
	public void reclaimTombstonePerks(Game game, Player player)
	{
		UUID id = player.getUniqueId();
		boolean enabled = ConfigManager.getMainConfig().tombstoneEnabled;
		if(!shouldReclaim(hasTombstoneSnapshot(id), enabled))
			return;
		List<PerkType> perks = consumeTombstoneSnapshot(id);
		if(perks == null)
			return;
		for(PerkType perk : perks)
			PerkManager.givePerk(game, player, perk);
	}

	public void clearDownedPlayers()
	{
		for(DownedPlayer downedPlayer : downedPlayers)
			downedPlayer.clearDownedState();
	}

	public int numDownedPlayers()
	{
		return downedPlayers.size();
	}

	public void setPlayerDowned(Player player, Game game)
	{
		DownedPlayer down = new DownedPlayer(player, game);
		down.setPlayerDown();
		downedPlayers.add(down);
		player.setHealth(1D);
		game.sendMessageToPlayers(player.getName() + " has gone down! Stand close and right click them to revive");
	}

	public void removeDownedPlayer(Player player)
	{
		for(int i = downedPlayers.size() - 1; i >= 0; i--)
		{
			DownedPlayer downedPlayer = downedPlayers.get(i);
			if(downedPlayer.getPlayer().equals(player))
			{
				downedPlayer.clearDownedState();
				downedPlayers.remove(i);
			}
		}
	}

	public boolean isDownedPlayer(Player player)
	{
		for(DownedPlayer dp : downedPlayers)
			if(dp.getPlayer().equals(player))
				return true;
		return false;
	}

	public DownedPlayer getDownedPlayer(Player player)
	{
		for(DownedPlayer dp : downedPlayers)
			if(dp.getPlayer().equals(player))
				return dp;
		return null;
	}

	public DownedPlayer getDownedPlayerForReviver(Player player)
	{
		for(DownedPlayer dp : downedPlayers)
			if(player.equals(dp.getReviver()))
				return dp;
		return null;
	}

	public void downedPlayerRevived(DownedPlayer dp)
	{
		downedPlayers.remove(dp);
	}

	public void reviveDownedPlayers()
	{
		for(int i = downedPlayers.size() - 1; i >= 0; i--)
			downedPlayers.get(i).revivePlayer();
	}
}
