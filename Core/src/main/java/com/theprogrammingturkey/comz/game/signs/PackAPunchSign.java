package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.game.weapons.WeaponInstance;
import com.theprogrammingturkey.comz.listeners.PowerUpDropListener;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.ParticleFX;
import com.theprogrammingturkey.comz.util.SoundConfig;
import com.theprogrammingturkey.comz.util.SoundUtil;
import com.theprogrammingturkey.comz.util.display.Displays;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class PackAPunchSign implements IGameSign
{
	// Tier 2 sign syntax (OPTIONAL, backward compatible):
	//   Line 0: [Zombies]        (auto)
	//   Line 1: Pack-a-Punch      (auto)
	//   Line 2: <cost>
	//   Line 3: <blank>           — type "tp" (or "gated") here to make this PaP teleporter-gated
	// A gated PaP sign requires the player to currently hold timed access from a PaP-flagged
	// teleporter (TeleporterSign). Ungated signs (the default, blank line 3) are unaffected.
	// onChange normalises the opt-in to "[Teleporter]" on line 3. The "pap" label on line 1
	// is NOT used as the flag here, since it always contains "pap".

	private static final int DEFAULT_COST = 5000;

	/** One gun handed in to a machine: working, then waiting for its owner to take it. */
	private static final class Session
	{
		final Game game;
		final UUID owner;
		final Player ownerRef;
		final int cost;
		final GunInstance gun;
		final int slot;
		final boolean refill;
		final Location center;
		final BlockFace facing;
		Entity display;
		boolean ready;
		boolean refunded;
		int workTask = -1;
		int watchTask = -1;
		int bobTask = -1;
		long pickupTicksLeft;

		Session(Game game, Player owner, int cost, GunInstance gun, int slot, boolean refill, Location center, BlockFace facing)
		{
			this.game = game;
			this.owner = owner.getUniqueId();
			this.ownerRef = owner;
			this.cost = cost;
			this.gun = gun;
			this.slot = slot;
			this.refill = refill;
			this.center = center;
			this.facing = facing;
		}
	}

	private static final int WATCH_TICKS = 5;

	private static final Map<String, Session> SESSIONS = new HashMap<>();
	private static final Map<String, BlockFace> FACINGS = new HashMap<>();

	private static String key(Location loc)
	{
		return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
	}

	/** Remembers which way a spawned machine faces, since its sign block is replaced while playing. */
	public static void registerMachine(Location base, BlockFace facing)
	{
		FACINGS.put(key(base), facing);
	}

	/** Refunds and frees every machine holding the leaving player's gun. */
	public static void onPlayerLeave(Game game, Player player)
	{
		Iterator<Map.Entry<String, Session>> it = SESSIONS.entrySet().iterator();
		while(it.hasNext())
		{
			Session session = it.next().getValue();
			if(session.game == game && session.owner.equals(player.getUniqueId()))
			{
				refund(session);
				release(session);
				it.remove();
			}
		}
	}

	/** Refunds every session of a game; must run while the players' points still exist, before the game's points are cleared. */
	public static void refundGame(Game game)
	{
		for(Session session : SESSIONS.values())
			if(session.game == game)
				refund(session);
	}

	/** Drops all sessions of a game (the held guns are lost) and the registered machines. */
	public static void clearGame(Game game)
	{
		Iterator<Map.Entry<String, Session>> it = SESSIONS.entrySet().iterator();
		while(it.hasNext())
		{
			Session session = it.next().getValue();
			if(session.game == game)
			{
				release(session);
				it.remove();
			}
		}
		if(SESSIONS.isEmpty())
			FACINGS.clear();
	}

	/**
	 * Tier 2 — true when this PaP sign opts in to being teleporter-gated.
	 * Detected via the keyword "tp" or "gated" on line index 3, case-insensitive.
	 */
	private static boolean isTeleporterGated(String[] lines)
	{
		if(lines.length <= 3 || lines[3] == null)
			return false;
		String flag = ChatColor.stripColor(lines[3]).toLowerCase();
		return flag.contains("tp") || flag.contains("gated") || flag.contains("teleporter");
	}

	private static int signCost(Game game, String[] lines)
	{
		int signCost = DEFAULT_COST;
		if(lines.length > 2 && lines[2] != null && ChatColor.stripColor(lines[2]).matches("[0-9]{1,5}"))
			signCost = Integer.parseInt(ChatColor.stripColor(lines[2]));
		return Game.effectivePaPCost(game.getPaPCostOverride(), signCost);
	}

	/**
	 * Action-bar text for a player looking at or standing next to a machine, reflecting the gun in
	 * their hand: "Pack-a-Punch [5000]" or "Refill Ammo [2000]".
	 */
	public static String prompt(Game game, Player player, Location machine, String[] lines)
	{
		Session session = SESSIONS.get(key(machine));
		if(session != null)
		{
			if(!session.owner.equals(player.getUniqueId()))
				return ChatColor.GRAY + "Pack-a-Punch in use";
			return session.ready ? ChatColor.GREEN + "Take your " + session.gun.getType().getName() : ChatColor.LIGHT_PURPLE + "Upgrading...";
		}
		if(game.hasPower() && !game.isPowered())
			return ChatColor.RED + "Pack-a-Punch " + ChatColor.GRAY + "- power required";

		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		GunInstance gun = manager != null && manager.isHeldItemGun() ? manager.getGun(player.getInventory().getHeldItemSlot()) : null;
		if(gun == null)
			return ChatColor.LIGHT_PURPLE + "Pack-a-Punch " + ChatColor.YELLOW + "[" + signCost(game, lines) + "]";

		boolean refill = gun.isPackOfPunched();
		if(refill && !ConfigManager.getMainConfig().packAPunchRepackEnabled)
			return ChatColor.GRAY + "Already Pack-a-Punched";
		if(!refill && !gun.getType().isPackAPunchable())
			return ChatColor.GRAY + "Cannot be Pack-a-Punched";

		int cost = refill ? ConfigManager.getMainConfig().packAPunchRepackCost : signCost(game, lines);
		ChatColor color = PointManager.INSTANCE.canBuy(player, cost) ? ChatColor.YELLOW : ChatColor.RED;
		return ChatColor.LIGHT_PURPLE + (refill ? "Refill Ammo " : "Pack-a-Punch ") + color + "[" + cost + "]";
	}

	@Override
	public void onBreak(Game game, Player player, Location location)
	{

	}

	@Override
	public void onInteract(Game game, Player player, Location location, String[] lines)
	{
		String key = key(location);
		Session session = SESSIONS.get(key);
		if(session != null)
		{
			if(!session.owner.equals(player.getUniqueId()))
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "The Pack-a-Punch is in use!");
			else if(session.ready)
				collect(key, session, player);
			else
				CommandUtil.sendMessageToPlayer(player, ChatColor.LIGHT_PURPLE + "Your gun is still being upgraded...");
			return;
		}

		if(game.hasPower() && !game.isPowered())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must turn on the power before You can Pack-A-punch!");
			PerkType.noPower(player);
			return;
		}

		// Tier 2 — teleporter-gated PaP: only usable while the player holds timed teleporter access.
		if(isTeleporterGated(lines) && !game.teleporterManager.hasPaPAccess(player))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Reach Pack-a-Punch via the teleporter!");
			return;
		}

		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		if(!manager.isHeldItemGun())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must hold the gun you want to pack-a-punch!");
			deny(player, location);
			return;
		}

		int slot = player.getInventory().getHeldItemSlot();
		GunInstance gun = manager.getGun(slot);
		boolean refill = gun.isPackOfPunched();
		String gunName = gun.getType().getName();

		if(refill && !ConfigManager.getMainConfig().packAPunchRepackEnabled)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Your " + ChatColor.GOLD + gunName + ChatColor.RED + " is already Pack-A-Punched!");
			deny(player, location);
			return;
		}
		if(!refill && !gun.getType().isPackAPunchable())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Your " + ChatColor.GOLD + gunName + ChatColor.RED + " cannot be Pack-A-Punched!");
			deny(player, location);
			return;
		}

		int cost = refill ? ConfigManager.getMainConfig().packAPunchRepackCost : signCost(game, lines);
		if(!PointManager.INSTANCE.canBuy(player, cost))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You do not have enough points to " + (refill ? "refill" : "Pack-A-Punch") + " your " + gunName + "!");
			deny(player, location);
			return;
		}

		PointManager.INSTANCE.takePoints(player, cost);
		PointManager.INSTANCE.notifyPlayer(player);

		manager.removeWeapon(gun);
		player.getInventory().setItem(slot, null);

		Location center = location.getBlock().getLocation().add(0.5, 0, 0.5);
		Session started = new Session(game, player, cost, gun, slot, refill, center, facingOf(location));
		SESSIONS.put(key, started);

		SoundUtil.play(center.getWorld(), center, SoundConfig.get("pap.upgrade", SoundConfig.get("packapunch", "BLOCK_ANVIL_USE")), SoundCategory.MASTER, 1, 1);
		if(ConfigManager.getMainConfig().visualsPapFeedback)
			ParticleFX.burst(center.getWorld(), center.clone().add(0, 1, 0), org.bukkit.Color.PURPLE, 24);
		showGunInside(started, gun.getType().getStack());

		CommandUtil.sendMessageToPlayer(player, ChatColor.LIGHT_PURPLE + "Upgrading your " + ChatColor.GOLD + gunName + ChatColor.LIGHT_PURPLE + "...");
		started.workTask = COMZombies.scheduleTask(ticks(ConfigManager.getMainConfig().packAPunchWorkSeconds), () -> finishWork(key, started));
		started.watchTask = COMZombies.scheduleTask(WATCH_TICKS, WATCH_TICKS, () -> watch(key, started));
	}

	private static long ticks(double seconds)
	{
		return Math.max(1, Math.round(seconds * 20));
	}

	private static void deny(Player player, Location location)
	{
		SoundUtil.play(player, location, SoundConfig.get("pap.deny", "ENTITY_VILLAGER_NO"), SoundCategory.MASTER, 1, 1);
	}

	private static BlockFace facingOf(Location location)
	{
		BlockFace registered = FACINGS.get(key(location));
		if(registered != null)
			return registered;
		BlockData data = location.getBlock().getBlockData();
		return com.theprogrammingturkey.comz.util.Compat.facingOf(data);
	}

	private static void showGunInside(Session session, ItemStack stack)
	{
		if(!Displays.supportsItemDisplays())
			return;
		Entity display = Displays.spawnItem(session.center, stack, 0.7f, 0f, 1.0f, false);
		if(display == null)
			return;
		display.setPersistent(false);
		session.display = display;
	}

	private static void finishWork(String key, Session session)
	{
		if(SESSIONS.get(key) != session)
			return;
		session.workTask = -1;
		session.ready = true;

		Player owner = Bukkit.getPlayer(session.owner);
		SoundUtil.play(session.center.getWorld(), session.center, SoundConfig.get("pap.ready", "ENTITY_PLAYER_LEVELUP"), SoundCategory.MASTER, 1, 1);

		session.pickupTicksLeft = ticks(ConfigManager.getMainConfig().packAPunchPickupSeconds);

		if(session.display == null)
		{
			if(owner != null)
				collect(key, session, owner);
			return;
		}

		if(owner != null)
			CommandUtil.sendMessageToPlayer(owner, ChatColor.GREEN + "Your gun is ready! Take it from the machine.");

		if(!session.refill)
		{
			session.display.remove();
			session.display = null;
			showGunInside(session, session.gun.getType().getPackAPunchGun().getStack());
		}
		final Entity display = session.display;
		if(display == null)
		{
			if(owner != null)
				collect(key, session, owner);
			return;
		}
		float fx = session.facing.getModX() * 0.95f;
		float fz = session.facing.getModZ() * 0.95f;
		COMZombies.scheduleTask(2, () -> Displays.animate(display, fx, 1.0f, fz, 0.7f, 0f, 14));

		final float[] spin = {0f};
		final boolean[] up = {true};
		session.bobTask = COMZombies.scheduleTask(18, 20, () ->
		{
			if(display.isDead())
				return;
			spin[0] += 120f;
			up[0] = !up[0];
			Displays.animate(display, fx, up[0] ? 1.12f : 1.0f, fz, 0.7f, spin[0], 20);
		});
	}

	private static boolean isIncapacitated(Game game, Player player)
	{
		return game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player);
	}

	private static boolean ownerGone(Session session, Player owner)
	{
		Game game = session.game;
		return owner == null || game.getStatus() != Game.GameStatus.INGAME
				|| GameManager.INSTANCE.getGame(owner) != game || !game.getLivingPlayers().contains(owner);
	}

	/**
	 * Runs for the whole session: refunds when the owner is gone or dead, holds the pickup countdown
	 * while the owner is downed, and hands over a gun that finished while the owner could not take it.
	 */
	private static void watch(String key, Session session)
	{
		if(SESSIONS.get(key) != session)
			return;
		Player owner = Bukkit.getPlayer(session.owner);
		if(ownerGone(session, owner))
		{
			if(refund(session) && owner != null)
				CommandUtil.sendMessageToPlayer(owner, ChatColor.YELLOW + "Your Pack-a-Punch was cancelled and " + session.cost + " points were refunded.");
			end(key, session);
			return;
		}
		if(!session.ready || isIncapacitated(session.game, owner))
			return;
		if(session.display == null && collect(key, session, owner))
			return;
		session.pickupTicksLeft -= WATCH_TICKS;
		if(session.pickupTicksLeft <= 0)
			expire(key, session);
	}

	private static boolean refund(Session session)
	{
		if(session.refunded || session.cost <= 0 || !session.game.wasDisconnected(session.ownerRef))
			return false;
		session.refunded = true;
		PointManager.INSTANCE.addPoints(session.ownerRef, session.cost);
		PointManager.INSTANCE.notifyPlayer(session.ownerRef);
		return true;
	}

	private static void expire(String key, Session session)
	{
		if(SESSIONS.get(key) != session)
			return;
		Player owner = Bukkit.getPlayer(session.owner);
		if(owner != null)
			CommandUtil.sendMessageToPlayer(owner, ChatColor.RED + "You waited too long - your " + ChatColor.GOLD + session.gun.getType().getName() + ChatColor.RED + " was lost!");
		SoundUtil.play(session.center.getWorld(), session.center, SoundConfig.get("pap.deny", "ENTITY_VILLAGER_NO"), SoundCategory.MASTER, 1, 1);
		end(key, session);
	}

	/** Returns true when the session was resolved; false when the gun stays in the machine. */
	private static boolean collect(String key, Session session, Player player)
	{
		Game game = session.game;
		if(game.getStatus() != Game.GameStatus.INGAME || !GameManager.INSTANCE.isPlayerInGame(player) || GameManager.INSTANCE.getGame(player) != game)
		{
			refund(session);
			end(key, session);
			return true;
		}
		if(isIncapacitated(game, player))
			return false;

		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		int slot = freeSlot(manager, game, player, session.slot);
		if(slot < 0)
		{
			player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "No free weapon slot"));
			return false;
		}

		GunInstance gun = session.gun;
		if(session.refill)
			gun.maxAmmo();
		else
			gun.setPackOfPunch();
		gun.setSlot(slot);
		manager.addWeapon(gun);
		player.getInventory().setHeldItemSlot(slot);

		SoundUtil.play(player, player.getLocation(), SoundConfig.get("pap.ready", "ENTITY_PLAYER_LEVELUP"), SoundCategory.MASTER, 1, 1);
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Your " + ChatColor.GOLD + gun.getType().getName() + ChatColor.RED + (session.refill ? " was refilled" : " was Pack-A-Punched"));
		if(ConfigManager.getMainConfig().visualsPapFeedback)
			player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + (session.refill ? "REFILLED!" : "UPGRADED!"), ChatColor.GRAY + gun.getType().getName(), 5, 40, 10);
		end(key, session);
		return true;
	}

	/**
	 * The slot the gun came from when it is still free, otherwise another free gun slot, otherwise -1.
	 * A slot holding an active Death Machine counts as occupied.
	 */
	private static int freeSlot(PlayerWeaponManager manager, Game game, Player player, int preferred)
	{
		if(manager.getWeapon(preferred) == null)
			return preferred;
		int gunSlots = game.perkManager.hasPerk(player, PerkType.MULE_KICK) ? 3 : 2;
		for(int slot = 1; slot <= gunSlots; slot++)
			if(manager.getWeapon(slot) == null)
				return slot;
		return -1;
	}

	private static void end(String key, Session session)
	{
		release(session);
		SESSIONS.remove(key, session);
	}

	private static void release(Session session)
	{
		if(session.workTask != -1)
			Bukkit.getScheduler().cancelTask(session.workTask);
		if(session.watchTask != -1)
			Bukkit.getScheduler().cancelTask(session.watchTask);
		if(session.bobTask != -1)
			Bukkit.getScheduler().cancelTask(session.bobTask);
		session.workTask = session.watchTask = session.bobTask = -1;
		if(session.display != null && !session.display.isDead())
			session.display.remove();
		session.display = null;
	}

	@Override
	public void onChange(Game game, Player player, SignChangeEvent event)
	{
		String thirdLine = ChatColor.stripColor(event.getLine(2));

		int cost;
		if(thirdLine == null || thirdLine.equalsIgnoreCase(""))
		{
			cost = DEFAULT_COST;
		}
		else
		{
			if(thirdLine.matches("[0-9]{1,5}"))
			{
				cost = Integer.parseInt(thirdLine);
			}
			else
			{
				cost = DEFAULT_COST;
				CommandUtil.sendMessageToPlayer(player, thirdLine + " is not a valid amount!");
			}
		}
		// Tier 2 — preserve a teleporter-gate opt-in typed on line 3, normalised to "[Teleporter]".
		String typedFlag = event.getLine(3);
		boolean gated = typedFlag != null && (ChatColor.stripColor(typedFlag).toLowerCase().contains("tp")
				|| ChatColor.stripColor(typedFlag).toLowerCase().contains("gated")
				|| ChatColor.stripColor(typedFlag).toLowerCase().contains("teleporter"));

		event.setLine(0, ChatColor.RED + "[Zombies]");
		event.setLine(1, ChatColor.AQUA + "Pack-a-Punch");
		event.setLine(2, Integer.toString(cost));
		event.setLine(3, gated ? ChatColor.LIGHT_PURPLE + "[Teleporter]" : "");
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
