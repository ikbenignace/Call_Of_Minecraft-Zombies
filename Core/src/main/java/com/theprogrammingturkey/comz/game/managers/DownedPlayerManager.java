package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.DownedPlayer;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.CommandUtil;
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
	 * Tier 3 — solo Quick Revive self-revive uses remaining, keyed by player UUID. Initialized lazily
	 * to {@code soloQuickReviveUses} the first time a solo player goes down holding Quick Revive, then
	 * decremented on each automatic self-revive. The map is scoped to a single DownedPlayerManager
	 * instance, and a fresh manager is created per game (see {@code Game} constructor), so a new game
	 * always starts with a fresh use counter without an explicit reset hook.
	 */
	private final Map<UUID, Integer> soloSelfReviveUses = new HashMap<>();

	/**
	 * Tier 3 — pure reclaim gate. Perks are re-granted only when a saved tombstone snapshot exists
	 * for the player AND the Tombstone Soda feature is enabled in config.
	 */
	public static boolean shouldReclaim(boolean hasSnapshot, boolean enabled)
	{
		return hasSnapshot && enabled;
	}

	/**
	 * Tier 3 — pure solo self-revive gate. A downed player auto-revives via Quick Revive only when
	 * the game is solo (a single player) AND they hold the Quick Revive perk AND they still have at
	 * least one self-revive use remaining.
	 */
	public static boolean canSelfRevive(boolean solo, boolean hasQuickRevive, int usesRemaining)
	{
		return solo && hasQuickRevive && usesRemaining > 0;
	}

	/**
	 * Tier 3 — self-revive uses remaining for a player, lazily initialized to {@code max} the first
	 * time it is queried for that player.
	 */
	public int getSelfReviveUses(UUID playerId, int max)
	{
		return soloSelfReviveUses.computeIfAbsent(playerId, id -> max);
	}

	/**
	 * Tier 3 — consume one self-revive use for a player, returning the remaining count after the
	 * decrement (never below zero).
	 */
	public int consumeSelfReviveUse(UUID playerId, int max)
	{
		int remaining = Math.max(0, getSelfReviveUses(playerId, max) - 1);
		soloSelfReviveUses.put(playerId, remaining);
		return remaining;
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

		trySoloSelfRevive(player, game, down);
	}

	/**
	 * Tier 3 — in a solo (1-player) game, if the lone downed player holds Quick Revive and still has
	 * self-revive uses remaining, schedule an automatic self-revive after the configured delay and
	 * decrement their remaining uses. No-op in co-op (more than one player), without the perk, or once
	 * uses are exhausted (the player then bleeds out / the game ends per existing logic).
	 */
	private void trySoloSelfRevive(Player player, Game game, DownedPlayer down)
	{
		boolean solo = game.getPlayersInGame().size() == 1;
		boolean hasQuickRevive = game.perkManager.hasPerk(player, PerkType.QUICK_REVIVE);
		int max = ConfigManager.getMainConfig().soloQuickReviveUses;
		int usesRemaining = getSelfReviveUses(player.getUniqueId(), max);

		if(!canSelfRevive(solo, hasQuickRevive, usesRemaining))
			return;

		int remaining = consumeSelfReviveUse(player.getUniqueId(), max);
		int delayTicks = ConfigManager.getMainConfig().soloReviveDelaySeconds * 20;
		COMZombies.scheduleTask(delayTicks, () ->
		{
			if(down.isPlayerDown())
				down.revivePlayer();
		});
		CommandUtil.sendMessageToPlayer(player, "Quick Revive self-revive — " + remaining + " left");
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
