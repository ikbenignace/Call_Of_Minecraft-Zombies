package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.DownedPlayer;
import com.theprogrammingturkey.comz.game.features.PerkType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The single place that decides a player's walk speed, so downed state, Stamin-Up and aiming can
 * never overwrite each other (wading slows on top of them).
 */
public final class PlayerSpeed
{
	public static final float DEFAULT_WALK_SPEED = 0.2f;
	public static final float DOWNED_WALK_SPEED = 0.02f;
	/** A negative walk speed is the client-side trick that zooms the FOV for a sniper scope. */
	private static final float SCOPE_WALK_SPEED = -0.2f;

	public enum Aim
	{
		SIGHTS, SCOPE
	}

	private static final Map<UUID, Aim> AIMING = new HashMap<>();
	/** Walk-speed multiplier of the water the player wades through; absent when on dry ground. */
	private static final Map<UUID, Float> WADING = new HashMap<>();

	private PlayerSpeed()
	{
	}

	public static void setAiming(Player player, Aim aim)
	{
		if(aim == null)
			AIMING.remove(player.getUniqueId());
		else
			AIMING.put(player.getUniqueId(), aim);
		apply(player);
	}

	public static void setWading(Player player, float multiplier)
	{
		Float before = multiplier >= 1f ? WADING.remove(player.getUniqueId()) : WADING.put(player.getUniqueId(), multiplier);
		boolean changed = before == null ? multiplier < 1f : before != multiplier;
		if(changed)
			apply(player);
	}

	public static boolean isAiming(Player player)
	{
		return AIMING.containsKey(player.getUniqueId());
	}

	public static void apply(Player player)
	{
		player.setWalkSpeed(Math.max(-1f, Math.min(1f, compute(player))));
	}

	/** Drops all speed state for a player leaving the game and puts back the vanilla speed. */
	public static void reset(Player player)
	{
		AIMING.remove(player.getUniqueId());
		WADING.remove(player.getUniqueId());
		player.setWalkSpeed(DEFAULT_WALK_SPEED);
	}

	private static float compute(Player player)
	{
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || !game.isPlayerAlive(player))
			return DEFAULT_WALK_SPEED;

		DownedPlayer downed = game.downedPlayerManager.getDownedPlayer(player);
		if(downed != null && downed.isPlayerDown())
			return DOWNED_WALK_SPEED;

		float base = game.perkManager.hasPerk(player, PerkType.STAMIN_UP) ? (float) ConfigManager.getMainConfig().staminUpWalkSpeed : DEFAULT_WALK_SPEED;
		base *= WADING.getOrDefault(player.getUniqueId(), 1f);
		Aim aim = AIMING.get(player.getUniqueId());
		if(aim == Aim.SCOPE)
			return SCOPE_WALK_SPEED;
		if(aim == Aim.SIGHTS)
			return (float) (base * ConfigManager.getMainConfig().aimWalkSpeedMultiplier);
		return base;
	}
}
