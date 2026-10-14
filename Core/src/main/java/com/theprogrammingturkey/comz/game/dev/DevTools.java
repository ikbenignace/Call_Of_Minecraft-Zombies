package com.theprogrammingturkey.comz.game.dev;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.features.DownedPlayer;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.game.managers.PerkManager;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Testing shortcuts shared by {@code /zombies dev} and the developer GUI. Each action reports back to the player. */
public final class DevTools
{
	private static final Set<UUID> GOD_PLAYERS = new HashSet<>();

	private DevTools()
	{
	}

	public static boolean isGod(Player player)
	{
		return GOD_PLAYERS.contains(player.getUniqueId());
	}

	public static void toggleGod(Player player)
	{
		boolean enabled = GOD_PLAYERS.add(player.getUniqueId());
		if(!enabled)
			GOD_PLAYERS.remove(player.getUniqueId());
		ok(player, "God mode " + (enabled ? "enabled" : "disabled") + ".");
	}

	public static void addPoints(Player actor, Player target, int amount)
	{
		if(amount >= 0)
			PointManager.INSTANCE.addPoints(target, amount);
		else
			PointManager.INSTANCE.takePoints(target, -amount);
		PointManager.INSTANCE.notifyPlayer(target);
		ok(actor, (amount >= 0 ? "Added " : "Removed ") + Math.abs((long) amount) + " points " + (amount >= 0 ? "to " : "from ") + target.getName()
				+ ". Now at " + PointManager.INSTANCE.getPlayersPoints(target) + ".");
	}

	public static void setRound(Player player, Game game, int round)
	{
		if(round < 1)
		{
			fail(player, "The round must be at least 1.");
			return;
		}
		if(!game.devSetWave(round))
		{
			fail(player, "Can't change the round right now (the game isn't running or a round is starting).");
			return;
		}
		ok(player, "Jumping to round " + round + ".");
	}

	public static void skipRound(Player player, Game game)
	{
		if(game.getStatus() != GameStatus.INGAME)
		{
			fail(player, "The game isn't running.");
			return;
		}
		setRound(player, game, game.getWave() + 1);
	}

	public static void killAll(Player player, Game game)
	{
		int alive = game.spawnManager.getZombiesAlive();
		game.spawnManager.killAll(false);
		ok(player, "Killed " + alive + " zombies.");
	}

	public static void givePerk(Player player, Game game, PerkType perk)
	{
		if(perk == PerkType.DER_WUNDERFIZZ)
		{
			fail(player, "Der Wunderfizz is a machine, not a perk.");
			return;
		}
		if(game.perkManager.hasPerk(player, perk))
		{
			fail(player, "You already have " + perk + ".");
			return;
		}
		PerkManager.givePerk(game, player, perk);
	}

	public static void giveAllPerks(Player player, Game game)
	{
		int given = 0;
		for(PerkType perk : PerkType.values())
		{
			if(perk == PerkType.DER_WUNDERFIZZ || game.perkManager.hasPerk(player, perk))
				continue;
			if(!game.perkManager.addPerk(player, perk))
				break;
			perk.initialEffect(player);
			game.perkManager.applyPerkEffect(player, perk);
			given++;
		}
		ok(player, "Gave " + given + " perks.");
	}

	public static void giveGun(Player player, Game game, String name)
	{
		BaseGun gun = WeaponManager.getGun(name);
		if(gun == null)
		{
			fail(player, "No gun named '" + name + "'. Use tab-complete or /zombies listguns.");
			return;
		}
		game.getPlayersWeapons(player).addWeapon(gun);
		ok(player, "Gave you the " + gun.getName() + ".");
	}

	public static void packAPunch(Player player, Game game)
	{
		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		if(!manager.isHeldItemGun())
		{
			fail(player, "Hold the gun you want to Pack-a-Punch.");
			return;
		}
		GunInstance gun = manager.getGun(player.getInventory().getHeldItemSlot());
		if(gun.isPackOfPunched())
		{
			fail(player, "That gun is already Pack-a-Punched.");
			return;
		}
		if(!gun.getType().isPackAPunchable())
		{
			fail(player, "That gun has no Pack-a-Punch variant.");
			return;
		}
		gun.setPackOfPunch();
		ok(player, "Pack-a-Punched your " + gun.getType().getName() + ".");
	}

	public static void maxAmmo(Player player, Game game)
	{
		game.getPlayersWeapons(player).maxAmmo();
		ok(player, "Ammo refilled.");
	}

	public static void power(Player player, Game game)
	{
		if(game.isPowered())
		{
			fail(player, "The power is already on.");
			return;
		}
		game.turnOnPower();
		ok(player, "Power on.");
	}

	public static void spawnPowerUp(Player player, Game game, PowerUp powerUp)
	{
		if(powerUp == PowerUp.NONE)
		{
			fail(player, "Pick a real power-up.");
			return;
		}
		game.powerUpManager.dropPowerUp(player, powerUp);
		ok(player, "Dropped " + powerUp.getDisplay() + ".");
	}

	public static void down(Player player, Game game)
	{
		if(game.getStatus() != GameStatus.INGAME)
		{
			fail(player, "The game isn't running.");
			return;
		}
		if(game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
		{
			fail(player, "You are already down.");
			return;
		}
		game.downedPlayerManager.setPlayerDowned(player, game);
	}

	public static void revive(Player actor, Player target, Game game)
	{
		DownedPlayer downed = game.downedPlayerManager.getDownedPlayer(target);
		if(downed == null)
		{
			fail(actor, target.getName() + " isn't down.");
			return;
		}
		downed.revivePlayer();
	}

	public static void moveBox(Player player, Game game)
	{
		if(game.boxManager.isMultiBox())
		{
			fail(player, "Every box is active in multi-box mode.");
			return;
		}
		RandomBox current = game.boxManager.getCurrentbox();
		if(current != null && !current.canActivate())
		{
			fail(player, "The box is in use.");
			return;
		}
		RandomBox next = game.boxManager.getRandomBox(current);
		if(next == null || next == current)
		{
			fail(player, "There is no other box to move to.");
			return;
		}
		game.boxManager.setCurrentBox(next);
		ok(player, "Box moved to " + next.getId() + ".");
	}

	public static void status(Player player, Game game)
	{
		List<PerkType> perks = game.perkManager.getPlayersPerks(player);
		player.sendMessage(ChatColor.GOLD + "===== COM:Z Developer status =====");
		line(player, "Arena", game.getName() + " (" + game.getStatus() + ")");
		line(player, "Wave", String.valueOf(game.getWave()));
		line(player, "Zombies alive", String.valueOf(game.spawnManager.getZombiesAlive()));
		line(player, "Spawned this round", game.spawnManager.getMobsSpawned() + "/" + game.spawnManager.getMobsToSpawn());
		line(player, "Power", game.isPowered() ? "on" : "off");
		line(player, "Created", String.valueOf(game.isCreated()));
		line(player, "Power-ups", "double points " + game.isDoublePoints() + ", insta-kill " + game.isInstaKill() + ", fire sale " + game.isFireSale());
		line(player, "Points", String.valueOf(PointManager.INSTANCE.getPlayersPoints(player)));
		line(player, "God mode", isGod(player) ? "on" : "off");
		line(player, "Perks", perks.isEmpty() ? "none" : perks.stream().map(PerkType::name).collect(Collectors.joining(", ")));
	}

	private static void line(Player player, String label, String value)
	{
		player.sendMessage(ChatColor.YELLOW + label + ": " + ChatColor.WHITE + value);
	}

	private static void ok(Player player, String message)
	{
		CommandUtil.sendMessageToPlayer(player, ChatColor.GREEN + message);
	}

	private static void fail(Player player, String message)
	{
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + message);
	}
}
