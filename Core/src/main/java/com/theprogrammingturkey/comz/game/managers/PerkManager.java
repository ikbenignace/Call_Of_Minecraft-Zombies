package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.CommandUtil;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PerkManager
{
	private final Map<Player, List<PerkType>> playersPerks = new HashMap<>();

	/** Applies a perk's reskinned vanilla effect: infinite, hidden particles, HUD icon shown. */
	public void applyPerkEffect(Player player, PerkType perk)
	{
		if(perk.getIconEffect() == null)
			return;
		player.addPotionEffect(new PotionEffect(perk.getIconEffect(), PotionEffect.INFINITE_DURATION, perk.getAmplifier(), false, false, true));
	}

	/** Strips the perk's vanilla effect from the player (icon disappears). */
	public void clearPerkEffect(Player player, PerkType perk)
	{
		if(perk.getIconEffect() != null)
			player.removePotionEffect(perk.getIconEffect());
	}

	/** Re-syncs every owned perk's effect — used after revive / re-entry. */
	public void reapplyAllEffects(Player player)
	{
		for(PerkType perk : getPlayersPerks(player))
			applyPerkEffect(player, perk);
	}

	/** Removes a single perk from the player: drops it from the list and clears its effect. */
	public void removePerkEffect(Player player, PerkType effect)
	{
		List<PerkType> perks = playersPerks.get(player);
		if(perks != null && perks.remove(effect))
			clearPerkEffect(player, effect);
	}

	public List<PerkType> getPlayersPerks(Player player)
	{
		return playersPerks.computeIfAbsent(player, k -> new ArrayList<>());
	}

	public boolean hasPerk(Player player, PerkType type)
	{
		return playersPerks.getOrDefault(player, new ArrayList<>()).contains(type);
	}

	public boolean addPerk(Player player, PerkType type)
	{
		List<PerkType> playerPerks = playersPerks.computeIfAbsent(player, k -> new ArrayList<>());

		if(playerPerks.contains(type))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You already have " + type + "!");
			return false;
		}

		if(playerPerks.size() >= ConfigManager.getMainConfig().maxPerks)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You can only have " + ConfigManager.getMainConfig().maxPerks + " perks!");
			return false;
		}
		playerPerks.add(type);
		playersPerks.put(player, playerPerks);
		return true;
	}

	public PerkType getRandomPerk(Player player)
	{
		List<PerkType> current = getPlayersPerks(player);
		return PerkType.getRandomPerk(current);
	}

	public void clearPerks()
	{
		playersPerks.clear();
	}

	public void clearPlayersPerks(Player player)
	{
		List<PerkType> perks = playersPerks.remove(player);
		if(perks != null)
			for(PerkType perk : perks)
				clearPerkEffect(player, perk);
	}

	public static void givePerk(Game game, Player player, PerkType perk)
	{
		if(!game.perkManager.addPerk(player, perk))
			return;
		perk.initialEffect(player);
		game.perkManager.applyPerkEffect(player, perk);
	}
}
