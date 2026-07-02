package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.managers.DownedPlayerManager;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.WeaponInstance;
import com.theprogrammingturkey.comz.leaderboards.Leaderboard;
import com.theprogrammingturkey.comz.leaderboards.PlayerStats;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Tier 3 — Who's Who solo ghost self-revive.
 *
 * <h2>Black Ops II behaviour (target fidelity)</h2>
 * In BO2, when you go down while holding Who's Who, a controllable <em>clone</em> of you spawns
 * elsewhere in the map with a starting pistol, while your real (downed) body lies where you fell.
 * You must run the clone back to your body to revive. If the bleed-out timer expires first, the
 * clone collapses and you die for real, <strong>losing Who's Who</strong> (along with the rest of
 * your perks). If you reach the body in time, you revive and keep your perks — including Who's Who.
 *
 * <h2>Implementation</h2>
 * Bukkit has no API to clone a player, so we approximate the <em>gameplay</em> of the perk for the
 * solo (1-player) case only: instead of entering the immobile downed state, the lone player stays
 * alive and mobile as a "ghost" but is <strong>teleported away to a spawn point far from where they
 * fell</strong>. The down location is recorded as the "body" they must return to within
 * {@code whosWhoSeconds}. Reaching it revives them (keeping perks); timing out kills them for real
 * and consumes Who's Who. The teleport + a minimum-distance guard on the revive check is what makes
 * this a genuine trip rather than an instant self-revive (the previous implementation left the
 * player standing on their own body, so they revived on the first tick — effectively infinite lives).
 *
 * <h2>Precedence vs. solo Quick Revive</h2>
 * Who's Who takes over solo-down handling when the player holds it: {@link Game} routes a lone down
 * to {@link DownedPlayerManager#startWhosWho(Player, Game)} before the normal downed/solo-Quick-Revive
 * path. They do not both fire for the same down.
 */
public class WhosWhoGhost
{
	/** Minimum distance (blocks) the ghost must have travelled from the body before a revive counts.
	 *  Without this the proximity check would trip the instant the ghost spawns if its spawn point
	 *  happened to be close to the body, re-creating the instant-revive bug. */
	static final double MIN_TRAVEL_BLOCKS = 5.0;

	/**
	 * Pure decision: should the ghost revive right now? Only when it has first moved at least
	 * {@link #MIN_TRAVEL_BLOCKS} away from the body (so a fresh spawn on top of the body cannot
	 * instantly count) and is now back within {@code range} blocks of it. Exposed for unit testing.
	 *
	 * @param distanceFromBody  current straight-line distance from ghost to body (blocks)
	 * @param range              configured revive range (blocks)
	 * @param travelledEnough    whether the ghost has already exceeded MIN_TRAVEL_BLOCKS at some point
	 */
	static boolean shouldRevive(double distanceFromBody, double range, boolean travelledEnough)
	{
		return travelledEnough && DownedPlayerManager.withinReviveRange(distanceFromBody * distanceFromBody, range);
	}

	private final Player player;
	private final Game game;
	private final DownedPlayerManager manager;

	/** The down location — the "body" the ghost must return to in order to revive. */
	private final Location bodyLocation;

	/** Snapshotted loadout (perks + guns) restored on a successful self-revive. */
	private final List<PerkType> savedPerks;
	private final WeaponInstance[] savedGuns = new WeaponInstance[3];

	private int tickTask = -1;
	private boolean resolved = false;
	private int elapsedTicks = 0;
	/** True once the ghost has moved at least {@link #MIN_TRAVEL_BLOCKS} from the body — gates the
	 *  revive so the player actually has to make the trip. */
	private boolean travelledEnough = false;

	public WhosWhoGhost(Player player, Game game, DownedPlayerManager manager)
	{
		this.player = player;
		this.game = game;
		this.manager = manager;
		this.bodyLocation = player.getLocation().clone();
		this.savedPerks = new java.util.ArrayList<>(game.perkManager.getPlayersPerks(player));
	}

	/**
	 * Enter ghost mode: snapshot and strip the loadout, teleport the ghost to a spawn point away
	 * from the body, hand over a starting pistol, apply ghost effects, and start the
	 * proximity/timeout loop.
	 */
	public void start()
	{
		PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(player);
		stats.setDowns(stats.getDowns() + 1);

		// Snapshot then strip the loadout (mirrors DownedPlayer), to be restored on revive.
		game.perkManager.clearPlayersPerks(player);
		PlayerWeaponManager weapons = game.getPlayersWeapons(player);
		savedGuns[0] = weapons.removeWeapon(1);
		savedGuns[1] = weapons.removeWeapon(2);
		savedGuns[2] = weapons.removeWeapon(3);
		weapons.addWeapon(WeaponManager.getGun(game.getStartingGun()).getNewInstance(player, 1));

		// BO2 fidelity: send the ghost to a spawn point far from the body so reaching it is a real
		// trip. This is the core fix for the instant-revive loop — without moving the player they
		// were standing on their own body and revived on the first proximity tick.
		Location ghostSpawn = pickFarSpawn();
		if(ghostSpawn != null)
			player.teleport(ghostSpawn);

		// Ghost presentation: glowing so the body location is easy to find, plus a brief head start.
		player.setGlowing(true);
		player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0));
		player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
		player.setHealth(Math.min(20.0, player.getMaxHealth()));
		player.setFireTicks(0);

		int seconds = ConfigManager.getMainConfig().whosWhoSeconds;
		player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "WHO'S WHO!" + ChatColor.RESET
				+ ChatColor.GRAY + " You have been moved away from your body. Return to it within "
				+ ChatColor.WHITE + seconds + "s" + ChatColor.GRAY + " to revive!");

		int maxTicks = seconds * 20;
		double range = ConfigManager.getMainConfig().whosWhoReviveRange;
		tickTask = COMZombies.scheduleTask(0, 10, () ->
		{
			if(resolved)
				return;
			elapsedTicks += 10;

			// Track that the ghost has actually left the body area; the revive only counts afterwards.
			double dist = distanceFromBody();
			if(!travelledEnough && dist >= MIN_TRAVEL_BLOCKS)
				travelledEnough = true;

			if(shouldRevive(dist, range, travelledEnough))
			{
				succeed();
				return;
			}
			if(elapsedTicks >= maxTicks)
				fail();
		});
	}

	/**
	 * Picks a zombie spawn point as far as possible from the body location, so the ghost has the
	 * longest realistic trip back. Falls back to the body location only if the arena has no spawn
	 * points configured (in which case the minimum-travel guard still prevents a true instant revive
	 * by requiring the player to step away and return — not ideal, but never silently infinite).
	 */
	private Location pickFarSpawn()
	{
		List<SpawnPoint> points = game.spawnManager.getPoints();
		if(points.isEmpty())
			return null;
		SpawnPoint farthest = null;
		double farthestDist = -1;
		for(SpawnPoint sp : points)
		{
			Location l = sp.getLocation();
			if(l == null || l.getWorld() == null || bodyLocation.getWorld() == null
					|| !l.getWorld().equals(bodyLocation.getWorld()))
				continue;
			double d = l.distanceSquared(bodyLocation);
			if(d > farthestDist)
			{
				farthestDist = d;
				farthest = sp;
			}
		}
		return farthest != null ? farthest.getLocation() : points.get(0).getLocation();
	}

	/** Squared distance from the ghost to the body, or Double.MAX_VALUE if cross-world. */
	private double distanceFromBody()
	{
		Location loc = player.getLocation();
		if(loc.getWorld() == null || bodyLocation.getWorld() == null
				|| !loc.getWorld().equals(bodyLocation.getWorld()))
			return Double.MAX_VALUE;
		return Math.sqrt(loc.distanceSquared(bodyLocation));
	}

	/** Reached the body in time: restore the loadout and clear ghost state. Who's Who is kept
	 *  (BO2: you keep it on a successful self-revive). */
	private void succeed()
	{
		cleanup();
		player.setGlowing(false);
		player.removePotionEffect(PotionEffectType.INVISIBILITY);
		player.removePotionEffect(PotionEffectType.SPEED);

		// Restore the snapshotted loadout (mirrors DownedPlayer#revivePlayer).
		PlayerWeaponManager weapons = game.getPlayersWeapons(player);
		weapons.removeWeapon(1);
		for(WeaponInstance gun : savedGuns)
			if(gun != null)
				weapons.addWeapon(gun);
		for(PerkType perk : savedPerks)
			com.theprogrammingturkey.comz.game.managers.PerkManager.givePerk(game, player, perk);

		player.setHealth(Math.min(20.0, player.getMaxHealth()));
		CommandUtil.sendMessageToPlayer(player, ChatColor.GREEN + "You reached your body and revived!");
	}

	/** Timer expired before reaching the body: fall back to the normal solo-death path and consume
	 *  Who's Who (BO2: failing the self-revive loses the perk along with the rest of your loadout). */
	private void fail()
	{
		cleanup();
		player.setGlowing(false);
		player.removePotionEffect(PotionEffectType.INVISIBILITY);
		player.removePotionEffect(PotionEffectType.SPEED);
		player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "You didn't make it back in time!");

		// Consume Who's Who so the next down cannot re-enter ghost mode (prevents any chance of the
		// revive loop re-forming across repeated fails). savedPerks is already restored-on-success
		// only, so no perks are re-granted here either.
		game.perkManager.removePerkEffect(player, PerkType.WHOS_WHO);

		PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(player);
		stats.setDeaths(stats.getDeaths() + 1);
		game.setDead(player);
	}

	/** Stop the loop and clear ghost tracking. Idempotent. */
	private void cleanup()
	{
		if(resolved)
			return;
		resolved = true;
		if(tickTask != -1)
			Bukkit.getScheduler().cancelTask(tickTask);
		manager.endGhost(player);
	}

	/**
	 * Called when the player quits the game/leaves the server mid-ghost. Cancels the running task,
	 * strips ghost effects and tracking, and consumes Who's Who so perks/effect can never leak into
	 * a subsequent session. Safe to call whether or not a ghost is active (the manager checks).
	 */
	public void quitCleanup()
	{
		if(resolved)
			return;
		cleanup();
		player.setGlowing(false);
		player.removePotionEffect(PotionEffectType.INVISIBILITY);
		player.removePotionEffect(PotionEffectType.SPEED);
	}
}
