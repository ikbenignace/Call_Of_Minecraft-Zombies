package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.managers.PermaPerkManager;
import com.theprogrammingturkey.comz.game.managers.PlayerSpeed;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.WeaponInstance;
import com.theprogrammingturkey.comz.leaderboards.Leaderboard;
import com.theprogrammingturkey.comz.leaderboards.PlayerStats;
import com.theprogrammingturkey.comz.listeners.PowerUpDropListener;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.EntitySpawner;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public class DownedPlayer implements Listener
{
	private final Player player;
	private Player reviver;
	private final Game game;
	private boolean isPlayerDown;
	private boolean isBeingRevived = false;
	private int downTime = 0;
	/** When true the bleed-out timer never kills this player — set while a solo self-revive is pending. */
	private boolean bleedoutSuppressed = false;

	private final WeaponInstance[] guns = new WeaponInstance[2];

	private int fireWorksTask = -1;
	private int reviveTask = -1;
	private int reviveBarTask = -1;
	/** Scheduled solo Quick Revive self-revive. Tracked so it can be cancelled in
	 * {@link #clearDownedState()} / {@link #cancelRevive()} when the down ends another way
	 * (game over, quit, manual revive) before the delay elapses — otherwise the orphaned
	 * task would fire {@link #revivePlayer()} on a dead/spectating player and re-arm a down. */
	private int soloReviveTask = -1;

	/** #143 — wall-clock time (ms) at which the current revive started, for a grace window that
	 * prevents the "You Moved!" false-cancel triggered by the interact/jitter on the very first
	 * click. Uses wall-clock rather than server ticks so it is portable across Bukkit API versions. */
	private long reviveStartMs = 0L;

	/** Down-state visuals: the invisible seat the player rides (sit pose) + the floating revive icon. */
	private ArmorStand seat;
	private Location downLocation;
	private Entity reviveIcon;
	/** Progress bar shown during a revive (co-op revive timer or the solo self-revive countdown). */
	private BossBar reviveBar;

	public DownedPlayer(Player player, Game game)
	{
		this.player = player;
		this.game = game;
	}

	public void setPlayerDown()
	{
		isPlayerDown = true;
		PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(player);
		stats.setDowns(stats.getDowns() + 1);
		// The down message is sent by DownedPlayerManager.setPlayerDowned, which knows whether this
		// is a co-op down (needs another player), a solo self-revive, or game over — so it isn't sent
		// here (it used to always say "need to be revived", which made no sense in a solo game).
		// Tier 3 — Tombstone Soda: snapshot perks BEFORE they are cleared. They only come back from the
		// tombstone left on bleed-out; Tombstone itself is spent, as in BO2.
		downLocation = player.getLocation().clone();
		if(ConfigManager.getMainConfig().tombstoneEnabled && game.perkManager.hasPerk(player, PerkType.TOMBSTONE_SODA))
		{
			List<PerkType> kept = new ArrayList<>(game.perkManager.getPlayersPerks(player));
			kept.remove(PerkType.TOMBSTONE_SODA);
			game.downedPlayerManager.storeTombstoneSnapshot(player.getUniqueId(), kept);
		}
		game.perkManager.clearPlayersPerks(player);
		PowerUpDropListener.revertDeathMachine(game, player);
		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		// Removed weapons leave their item behind, so each emptied gun slot is cleared. The Mule Kick
		// gun in slot 3 is not snapshotted: in BO it is gone for good once you go down.
		for(int slot = 1; slot <= 3; slot++)
		{
			WeaponInstance removed = manager.removeWeapon(slot);
			if(removed != null)
				player.getInventory().setItem(slot, null);
			if(slot <= 2)
				guns[slot - 1] = snapshotGun(removed);
		}
		manager.addWeapon(WeaponManager.getGun(game.getStartingGun()).getNewInstance(player, 1));
		player.setInvulnerable(true);
		PlayerSpeed.setAiming(player, null);
		// #114 — explicitly stop and lock sprinting while downed. The old code only set a very low
		// walk speed, which a client-side sprint glitch (especially while riding the down-state
		// armour-stand seat) could bypass, letting the player sprint at full pace while downed.
		player.setSprinting(false);
		applyDownVisuals();
		applyDownedEffects(true);
		scheduleTask();
	}

	private static WeaponInstance snapshotGun(WeaponInstance gun)
	{
		return gun != null && PowerUpDropListener.isDeathMachine(gun) ? null : gun;
	}

	public void clearDownedState()
	{
		isPlayerDown = false;
		isBeingRevived = false;
		if(fireWorksTask != -1)
		{
			Bukkit.getScheduler().cancelTask(fireWorksTask);
			fireWorksTask = -1;
		}
		if(reviveTask != -1)
		{
			Bukkit.getScheduler().cancelTask(reviveTask);
			reviveTask = -1;
		}
		// Cancel any pending solo self-revive so it cannot fire after the down has ended
		// (game over, quit, or a co-op revive that got there first) and re-arm a dead player.
		if(soloReviveTask != -1)
		{
			Bukkit.getScheduler().cancelTask(soloReviveTask);
			soloReviveTask = -1;
		}
		// Bleed-out suppression only applies while a solo self-revive is pending; once the
		// down ends any other way the flag must be cleared so a re-down on the same DownedPlayer
		// (or a reused instance) can actually bleed out instead of looping fireworks forever.
		bleedoutSuppressed = false;
		removeDownVisuals();
		removeDownedEffects();
		player.setGameMode(GameMode.SURVIVAL);
		player.setInvulnerable(false);
		PlayerSpeed.apply(player);
		player.setHealth(20);
		LowHealthVignette.clear(player);
		reviver = null;
	}

	/**
	 * Spawns the down-state visuals: an invisible armour-stand "seat" the player rides so they
	 * visibly slump/sit while downed, and a floating revive icon ({@code comz:misc/revive}) that
	 * rides the player so teammates can spot a downed player from across the map. Both are tracked
	 * and torn down in {@link #removeDownVisuals()} so they can never outlive the down state.
	 */
	private void applyDownVisuals()
	{
		Location loc = player.getLocation();
		World world = loc.getWorld();
		if(world == null)
			return;

		seat = EntitySpawner.spawn(world, loc, ArmorStand.class, as ->
		{
			as.setVisible(false);
			as.setGravity(false);
			as.setInvulnerable(true);
			as.setMarker(false);
			as.setSmall(true);
			as.setBasePlate(false);
			as.setCollidable(false);
			as.setSilent(true);
			as.setPersistent(false);
		});
		seat.addPassenger(player);

		ItemStack iconStack = new ItemStack(Material.PAPER);
		PackModels.applyFull(iconStack, "misc/revive");
		reviveIcon = Displays.spawnItem(loc, iconStack, 0.85f, 0f, 1.6f, true);
		if(reviveIcon != null)
		{
			reviveIcon.setPersistent(false);
			// The outline shows through walls so teammates can find a downed player.
			reviveIcon.setGlowing(true);
			player.addPassenger(reviveIcon);
		}
	}

	public void keepSeated()
	{
		if(!isPlayerDown || seat == null || !seat.isValid() || !player.isOnline())
			return;
		if(!seat.getPassengers().contains(player))
			seat.addPassenger(player);
	}

	/**
	 * Slow, dimmed view while down. Effects are short and re-applied from the bleed-out tick so they
	 * can never outlive the down state; the darkness pulse only exists from 1.19.
	 */
	private void applyDownedEffects(boolean bleeding)
	{
		if(Compat.SLOWNESS != null)
			player.addPotionEffect(new PotionEffect(Compat.SLOWNESS, DOWNED_EFFECT_TICKS, 2, false, false, false), true);
		if(Compat.DARKNESS != null && bleeding && downTime % DARKNESS_PULSE_SECONDS == 0)
			player.addPotionEffect(new PotionEffect(Compat.DARKNESS, DARKNESS_PULSE_TICKS, 0, false, false, false), true);
	}

	private void removeDownedEffects()
	{
		if(Compat.SLOWNESS != null)
			player.removePotionEffect(Compat.SLOWNESS);
		if(Compat.DARKNESS != null)
			player.removePotionEffect(Compat.DARKNESS);
	}

	private static final int DOWNED_EFFECT_TICKS = 40;
	private static final int DARKNESS_PULSE_TICKS = 60;
	private static final int DARKNESS_PULSE_SECONDS = 6;

	/** Removes the seat + revive icon and clears any active revive progress bar. Idempotent. */
	private void removeDownVisuals()
	{
		clearReviveProgress();
		if(reviveIcon != null)
		{
			reviveIcon.remove();
			reviveIcon = null;
		}
		if(seat != null)
		{
			seat.eject();
			seat.remove();
			seat = null;
		}
	}

	/** Removes the revive progress bar and cancels its updater (kept while still down). */
	private void clearReviveProgress()
	{
		if(reviveBarTask != -1)
		{
			Bukkit.getScheduler().cancelTask(reviveBarTask);
			reviveBarTask = -1;
		}
		if(reviveBar != null)
		{
			reviveBar.removeAll();
			reviveBar = null;
		}
	}

	/**
	 * Solo Quick Revive countdown bar: shows the lone downed player a filling bar over the
	 * self-revive delay. Called by {@code DownedPlayerManager.trySoloSelfRevive}.
	 */
	public void showSelfReviveBar(int seconds)
	{
		startReviveBar(ChatColor.YELLOW + "Quick Revive — getting up", BarColor.YELLOW, seconds * 20);
	}

	/**
	 * Creates a filling boss bar over {@code totalTicks} shown to the downed player (and the reviver,
	 * if any). Replaces any existing bar. The bar self-cancels when full or when the down state ends.
	 */
	private void startReviveBar(String title, BarColor color, int totalTicks)
	{
		clearReviveProgress();
		if(totalTicks <= 0)
			return;
		reviveBar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
		reviveBar.addPlayer(player);
		if(reviver != null)
			reviveBar.addPlayer(reviver);
		reviveBar.setProgress(0d);
		final int total = totalTicks;
		final int[] elapsed = {0};
		reviveBarTask = COMZombies.scheduleTask(0, 1, () ->
		{
			elapsed[0]++;
			double progress = Math.min(1d, (double) elapsed[0] / total);
			if(reviveBar != null)
				reviveBar.setProgress(progress);
			if(elapsed[0] >= total)
				clearReviveProgress();
		});
	}

	public void revivePlayer()
	{
		if(!isPlayerDown)
			return;
		player.sendMessage(ChatColor.GREEN + "You have been revived!");
		com.theprogrammingturkey.comz.util.SoundUtil.playKey(player.getWorld(), player.getLocation(), "player.revive", "ENTITY_PLAYER_LEVELUP", org.bukkit.SoundCategory.PLAYERS, 1, 1);
		if(reviver != null)
		{
			reviver.sendMessage(ChatColor.GREEN + "You revived " + ChatColor.DARK_GREEN + player.getName());
			PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(reviver);
			stats.setRevives(stats.getRevives() + 1);
			// Tier 4 — perma-perk progression: count this revive toward the reviver's lifetime total
			// and unlock any perma-perks whose play threshold has now been crossed.
			PermaPerkManager.recordRevive(reviver);
		}
		clearDownedState();
		game.downedPlayerManager.downedPlayerRevived(this);
		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		for(WeaponInstance gun : guns)
		{
			if(gun == null)
				continue;
			manager.removeWeapon(gun.getSlot());
			manager.addWeapon(gun);
		}

		// Revived players lose their perks, Tombstone included; the snapshot is only for a bleed-out.
		game.downedPlayerManager.consumeTombstoneSnapshot(player.getUniqueId());

	}

	public void cancelRevive()
	{
		this.isBeingRevived = false;
		this.reviver = null;
		clearReviveProgress();
		if(reviveTask != -1)
		{
			Bukkit.getScheduler().cancelTask(reviveTask);
			reviveTask = -1;
		}
		// A cancelled revive must not leave a pending solo self-revive or bleed-out suppression
		// in place — same reasoning as {@link #clearDownedState()}.
		if(soloReviveTask != -1)
		{
			Bukkit.getScheduler().cancelTask(soloReviveTask);
			soloReviveTask = -1;
		}
		bleedoutSuppressed = false;
	}

	/** Stops a co-op revive because the reviver went down, died or left, telling the downed player. */
	public void interruptRevive()
	{
		if(!isBeingRevived)
			return;
		Player lost = reviver;
		cancelRevive();
		if(lost != null)
			player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + lost.getName() + " stopped reviving you!");
	}

	/** Suppresses the bleed-out death (used when a solo Quick Revive self-revive is pending). */
	public void suppressBleedout()
	{
		this.bleedoutSuppressed = true;
	}

	/**
	 * Schedules a solo Quick Revive self-revive: suppresses bleed-out (so the bleed-out timer
	 * can't kill the player first), shows the self-revive countdown bar, and after
	 * {@code delaySeconds} revives the player if they are still down. The scheduled task id is
	 * stored in {@link #soloReviveTask} so that {@link #clearDownedState()} / {@link #cancelRevive()}
	 * cancel it when the down ends another way (game over, quit, manual revive) before the delay
	 * elapses — preventing the orphaned revive from re-arming a dead/spectating player.
	 */
	public void scheduleSoloSelfRevive(int delaySeconds)
	{
		suppressBleedout();
		showSelfReviveBar(delaySeconds);
		soloReviveTask = COMZombies.scheduleTask(delaySeconds * 20, () ->
		{
			soloReviveTask = -1;
			if(isPlayerDown)
				revivePlayer();
		});
	}

	public void startRevive(Player reviver)
	{
		// Guard against a second reviver stacking another revive task on an already-reviving player.
		if(isBeingRevived)
			return;
		if(reviveTask != -1)
			Bukkit.getScheduler().cancelTask(reviveTask);
		this.isBeingRevived = true;
		this.reviver = reviver;
		// #143 — record the start time so a brief grace window suppresses the false "You Moved!"
		// cancel that the right-click interact/jitter would otherwise trigger on the first revive.
		reviveStartMs = System.currentTimeMillis();
		int reviveTime = ConfigManager.getMainConfig().reviveTimer * 20;
		if (game.perkManager.hasPerk(reviver, PerkType.QUICK_REVIVE))
			reviveTime /= 2;
		startReviveBar(ChatColor.GREEN + "Reviving " + player.getName(), BarColor.GREEN, reviveTime);
		reviveTask = COMZombies.scheduleTask(reviveTime, () ->
		{
			reviveTask = -1;
			if(this.reviver != null && game.isActivePlayer(this.reviver) && this.reviver.isOnline())
				revivePlayer();
			else
				interruptRevive();
		});
		// Note: the consolidated branch shows revive progress via the boss bar (startReviveBar)
		// rather than action-bar messages, so the action-bar countdown that the "improved" branch
		// added (which depended on an NMS method not present here) is intentionally not restored.
	}

	private void scheduleTask()
	{
		fireWorksTask = COMZombies.scheduleTask(0, 20, () ->
		{
			// BO pauses the bleed-out while someone is reviving you.
			boolean bleeding = !isBeingRevived;
			if(bleeding)
				downTime++;
			applyDownedEffects(bleeding);
			keepSeated();
			displayDown();
			player.setHealth(1);
			// #114 — keep re-asserting sprint-off each tick while downed, in case a client-side
			// toggle sneaks through. Cheap and stops the downed-but-sprinting glitch reliably.
			if(player.isSprinting())
				player.setSprinting(false);
			if(!bleedoutSuppressed && downTime >= COMZombies.getPlugin().getConfig().getInt("config.ReviveSettings.MaxDownTime"))
			{
				player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "You have died!");
				//game.removePlayer(player);
				clearDownedState();
				game.downedPlayerManager.downedPlayerRevived(this);
				PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(player);
				stats.setDeaths(stats.getDeaths() + 1);
				try
				{
					game.downedPlayerManager.dropTombstone(game, player, downLocation, guns);
				} catch(RuntimeException e)
				{
					COMZombies.log.log(java.util.logging.Level.WARNING, "Could not drop the tombstone for " + player.getName(), e);
				}
				// Last: setDead can end the game, which tears down tombstones dropped before it.
				game.setDead(player);
			}
		});
	}

	private void displayDown()
	{
		Firework f = (Firework) player.getWorld().spawnEntity(player.getLocation(), Compat.FIREWORK_ROCKET);
		FireworkMeta meta = f.getFireworkMeta();
		meta.setPower(1);
		meta.addEffect(getRandomFireworkEffect());
		f.setFireworkMeta(meta);
	}

	private FireworkEffect getRandomFireworkEffect()
	{
		boolean trail = COMZombies.rand.nextBoolean();
		boolean flickr = COMZombies.rand.nextBoolean();

		Color color = Color.fromRGB(COMZombies.rand.nextInt(256), COMZombies.rand.nextInt(256), COMZombies.rand.nextInt(256));
		Type type = Type.values()[COMZombies.rand.nextInt(Type.values().length)];
		return FireworkEffect.builder().trail(trail).flicker(flickr).withColor(color).with(type).build();
	}

	public Player getPlayer()
	{
		return player;
	}

	public boolean isPlayerDown()
	{
		return isPlayerDown;
	}

	public boolean isBeingRevived()
	{
		return isBeingRevived;
	}

	/**
	 * #143 — Whether we are within the revive-start grace window (a few ticks after the revive
	 * began), during which small movement from the interact/jitter should NOT cancel the revive.
	 * This kills the false "You Moved!" on the first click.
	 */
	public boolean isInReviveGracePeriod()
	{
		if(reviveStartMs <= 0 || !isBeingRevived)
			return false;
		return (System.currentTimeMillis() - reviveStartMs) <= REVIVE_GRACE_MS;
	}

	/** Grace window in milliseconds (~5 ticks at 20 tps). */
	private static final long REVIVE_GRACE_MS = 250L;

	public Player getReviver()
	{
		return reviver;
	}
}