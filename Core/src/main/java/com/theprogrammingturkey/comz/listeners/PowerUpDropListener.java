package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.game.managers.PerkManager;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.PowerUpManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.game.weapons.WeaponInstance;
import com.theprogrammingturkey.comz.util.Compat;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PowerUpDropListener implements Listener
{
	/**
	 * Tracks the active expiry task id for each timed power-up, keyed per
	 * game + power-up type. Lets a re-pickup of an already-active power-up
	 * cancel the in-flight expiry task and reschedule a fresh full-duration
	 * one when {@code powerUpRefreshOnPickup} is enabled.
	 */
	private static final Map<Game, Map<PowerUp, Integer>> activeTimerTasks = new HashMap<>();

	private static final Map<Game, Map<UUID, DeathMachineGrant>> activeDeathMachines = new HashMap<>();

	private static final Map<Game, Map<PowerUp, Long>> displayExpiry = new HashMap<>();

	private static final class DeathMachineGrant
	{
		final WeaponInstance instance;
		final WeaponInstance displaced;
		final ItemStack savedStack;
		final int slot;
		int taskId = -1;

		DeathMachineGrant(WeaponInstance instance, WeaponInstance displaced, ItemStack savedStack, int slot)
		{
			this.instance = instance;
			this.displaced = displaced;
			this.savedStack = savedStack;
			this.slot = slot;
		}
	}

	/**
	 * Cancels every pending power-up expiry (timed power-ups and Death Machines) for the game. The
	 * game itself resets the power-up flags and player inventories on end, so displaced weapons are
	 * simply dropped rather than restored.
	 */
	public static void cancelAllTimers(Game game)
	{
		displayExpiry.remove(game);
		Map<PowerUp, Integer> byType = activeTimerTasks.remove(game);
		if(byType != null)
			for(int taskId : byType.values())
				Bukkit.getScheduler().cancelTask(taskId);

		Map<UUID, DeathMachineGrant> grants = activeDeathMachines.remove(game);
		if(grants != null)
			for(DeathMachineGrant grant : grants.values())
				Bukkit.getScheduler().cancelTask(grant.taskId);
	}

	public static boolean isDeathMachine(WeaponInstance weapon)
	{
		return weapon != null && WeaponManager.DEATH_MACHINE_NAME.equals(weapon.getBaseWeapon().getName());
	}

	/**
	 * Schedules a power-up expiry that only fires while it is still the registered timer for this
	 * game, so a task that outlives its session (or was superseded by a refresh) can never touch
	 * the next game's state.
	 */
	private static void scheduleExpiry(Game game, PowerUp powerUp, int duration, Runnable onExpire)
	{
		final int[] taskId = {-1};
		taskId[0] = COMZombies.scheduleTask(duration, () ->
		{
			Map<PowerUp, Integer> byType = activeTimerTasks.get(game);
			if(byType == null || !Integer.valueOf(taskId[0]).equals(byType.get(powerUp)))
				return;
			byType.remove(powerUp);
			if(game.getStatus() == Game.GameStatus.INGAME)
				onExpire.run();
		});
		activeTimerTasks.computeIfAbsent(game, g -> new HashMap<>()).put(powerUp, taskId[0]);
	}

	private static void cancelActiveTimerTask(Game game, PowerUp powerUp)
	{
		Map<PowerUp, Integer> byType = activeTimerTasks.get(game);
		if(byType == null)
			return;
		Integer taskId = byType.remove(powerUp);
		if(taskId != null)
			Bukkit.getScheduler().cancelTask(taskId);
	}

	@EventHandler
	private void onPowerUpPickup(EntityPickupItemEvent event)
	{
		if(event.getEntity() instanceof Player)
		{
			Player player = (Player) event.getEntity();
			final Item eItem = event.getItem();

			if(!GameManager.INSTANCE.isPlayerInGame(player))
			{
				if(isPowerUpItem(event.getItem()))
					event.setCancelled(true);
				return;
			}
			else if(!isPowerUpItem(event.getItem()))
			{
				event.setCancelled(true);
				return;
			}

			final Game game = GameManager.INSTANCE.getGame(player);

			event.getItem().remove();
			event.setCancelled(true);

			game.powerUpManager.currentPowerUps.remove(event.getItem());

			ItemStack item = event.getItem().getItemStack();
			PowerUp powerUp = PowerUp.getPowerUpForMaterial(item.getType());

			if(powerUp != PowerUp.NONE)
			{
				player.getInventory().remove(item);
				notifyAll(game, powerUp);
			}

			int duration = -1;
			switch(powerUp)
			{
				case MAX_AMMO:
					for(Player pl : game.getPlayersInGame())
					{
						PlayerWeaponManager manager = game.getPlayersWeapons(pl);
						manager.maxAmmo();
					}
					break;
				case INSTA_KILL:
					duration = ConfigManager.getMainConfig().instaKillTimer * 20;
					if(game.isInstaKill())
					{
						if(!ConfigManager.getMainConfig().powerUpRefreshOnPickup)
						{
							duration = -1;
							break;
						}
						cancelActiveTimerTask(game, PowerUp.INSTA_KILL);
					}
					game.setInstaKill(true);
					scheduleExpiry(game, PowerUp.INSTA_KILL, duration, () -> game.setInstaKill(false));
					break;
				case CARPENTER:
					for(Barrier barrier : game.barrierManager.getBarriers())
						barrier.repairFull();
					for(Player pl : game.getPlayersInGame())
					{
						PointManager.INSTANCE.addPoints(pl, game.isDoublePoints() ? 400 : 200);
						PointManager.INSTANCE.notifyPlayer(pl);
					}
					break;
				case NUKE:
					// Clear, recognisable nuke feedback (a green flash + title, NOT a weather change —
					// the lightning-bolt SOUND on Insta-Kill is what players mistook for a "thunderstorm").
					for(Mob z : game.spawnManager.getEntities())
					{
						World zw = z.getWorld();
						if(zw != null)
							zw.spawnParticle(Compat.DUST, z.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, new Particle.DustOptions(Color.fromRGB(0x66, 0xFF, 0x33), 1.8F));
					}
					for(Player pl : game.getPlayersInGame())
					{
						PointManager.INSTANCE.addPoints(pl, game.isDoublePoints() ? 800 : 400);
						PointManager.INSTANCE.notifyPlayer(pl);
						World w = pl.getWorld();
						if(w != null)
						{
							w.spawnParticle(Compat.EXPLOSION_EMITTER, pl.getLocation().add(0, 1, 0), 1);
							w.spawnParticle(Compat.DUST, pl.getLocation().add(0, 1, 0), 40, 1.0, 1.0, 1.0, new Particle.DustOptions(Color.fromRGB(0x88, 0xFF, 0x44), 2.0F));
						}
						pl.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "NUKE", ChatColor.GRAY + "All zombies eliminated", 5, 30, 15);
					}
					game.spawnManager.nuke();
					break;
				case BONUS_POINTS:
					int bonus = ConfigManager.getMainConfig().bonusPointsAmount;
					for(Player pl : game.getPlayersInGame())
					{
						PointManager.INSTANCE.addPoints(pl, game.isDoublePoints() ? bonus * 2 : bonus);
						PointManager.INSTANCE.notifyPlayer(pl);
					}
					break;
				case RANDOM_PERK:
					PerkType randomPerk = game.perkManager.getRandomPerk(player);
					if(randomPerk != null)
						PerkManager.givePerk(game, player, randomPerk);
					break;
				case DOUBLE_POINTS:
					duration = ConfigManager.getMainConfig().doublePointsTimer * 20;
					if(game.isDoublePoints())
					{
						if(!ConfigManager.getMainConfig().powerUpRefreshOnPickup)
						{
							duration = -1;
							break;
						}
						cancelActiveTimerTask(game, PowerUp.DOUBLE_POINTS);
					}
					game.setDoublePoints(true);
					scheduleExpiry(game, PowerUp.DOUBLE_POINTS, duration, () -> game.setDoublePoints(false));
					break;
				case FIRE_SALE:
					duration = ConfigManager.getMainConfig().fireSaleTimer * 20;
					if(game.isFireSale())
					{
						if(!ConfigManager.getMainConfig().powerUpRefreshOnPickup)
						{
							duration = -1;
							break;
						}
						// Both sales share the fire-sale flag, so the other sale's timer must not end it early.
						cancelActiveTimerTask(game, PowerUp.FIRE_SALE);
						cancelActiveTimerTask(game, PowerUp.BONFIRE_SALE);
					}
					else
					{
						game.boxManager.FireSale();
					}
					game.setFireSale(true);
					scheduleExpiry(game, PowerUp.FIRE_SALE, duration, () ->
					{
						game.setFireSale(false);
						game.boxManager.FireSale();
						game.setPaPCostOverride(-1);
					});
					break;
				case BONFIRE_SALE:
					// Bonfire Sale = Fire Sale (cheap mystery box) + a global Pack-a-Punch discount.
					duration = ConfigManager.getMainConfig().fireSaleTimer * 20;
					if(game.isFireSale())
					{
						if(!ConfigManager.getMainConfig().powerUpRefreshOnPickup)
						{
							duration = -1;
							break;
						}
						cancelActiveTimerTask(game, PowerUp.FIRE_SALE);
						cancelActiveTimerTask(game, PowerUp.BONFIRE_SALE);
					}
					else
					{
						game.boxManager.FireSale();
					}
					game.setFireSale(true);
					game.setPaPCostOverride(ConfigManager.getMainConfig().bonfirePaPCost);
					scheduleExpiry(game, PowerUp.BONFIRE_SALE, duration, () ->
					{
						game.setFireSale(false);
						game.boxManager.FireSale();
						game.setPaPCostOverride(-1);
					});
					break;
				case DEATH_MACHINE:
					// Instant power-up: hand the picking player a temporary minigun for a fixed
					// duration, then remove it and restore their inventory. No shared timer display.
					giveDeathMachine(game, player);
					break;
				default:
					player.updateInventory();
					COMZombies.scheduleTask(5, () -> player.getInventory().removeItem(eItem.getItemStack()));
					break;
			}

			if(duration != -1)
			{
				long expiresAt = System.currentTimeMillis() + duration * 50L;
				displayExpiry.computeIfAbsent(game, g -> new HashMap<>()).put(powerUp, expiresAt);
				for(Player pl : game.getPlayersInGame())
					powerUpDisplayTimer(game, pl, powerUp, expiresAt);
			}
		}
		else if(GameManager.INSTANCE.isEntityInGame(event.getEntity()))
		{
			event.setCancelled(true);
		}
	}

	/**
	 * Action-bar countdown. Each chain remembers the expiry it was started for and stops as soon as a
	 * refreshing pickup stores a newer one, so re-picking never stacks two flickering counters.
	 */
	private void powerUpDisplayTimer(Game game, Player player, PowerUp powerUp, long expiresAt)
	{
		Map<PowerUp, Long> byType = displayExpiry.get(game);
		if(byType == null || !Long.valueOf(expiresAt).equals(byType.get(powerUp)) || !GameManager.INSTANCE.isPlayerInGame(player))
			return;
		long remaining = expiresAt - System.currentTimeMillis();
		if(remaining <= 0)
			return;

		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.RED + powerUp.getDisplay() + ": " + ((remaining + 999) / 1000)));
		COMZombies.scheduleTask(20, () -> powerUpDisplayTimer(game, player, powerUp, expiresAt));
	}

	/**
	 * Hands the player a temporary Death Machine minigun and schedules its removal.
	 * <p>
	 * The minigun overlays one gun slot chosen by {@link PlayerWeaponManager#getCorrectSlot}; the
	 * {@link WeaponInstance} and raw {@link ItemStack} it displaced are restored on expiry. A second
	 * pickup while one is active only refreshes the timer and ammo, so the first minigun is never
	 * mistaken for a real gun to restore. If the slot no longer holds the minigun at expiry (the
	 * player replaced it), the slot is left alone.
	 */
	private static void giveDeathMachine(Game game, Player player)
	{
		final PlayerWeaponManager manager = game.getPlayersWeapons(player);
		final BaseGun deathMachine = WeaponManager.getGun(WeaponManager.DEATH_MACHINE_NAME);
		if(manager == null || deathMachine == null)
			return;

		Map<UUID, DeathMachineGrant> grants = activeDeathMachines.computeIfAbsent(game, g -> new HashMap<>());
		int durationTicks = ConfigManager.getMainConfig().deathMachineDurationSeconds * 20;

		DeathMachineGrant active = grants.get(player.getUniqueId());
		if(active != null && manager.getWeapon(active.slot) == active.instance)
		{
			Bukkit.getScheduler().cancelTask(active.taskId);
			active.instance.maxAmmo();
			active.taskId = COMZombies.scheduleTask(durationTicks, () -> expireDeathMachine(game, player, active));
			return;
		}

		if(active != null)
			Bukkit.getScheduler().cancelTask(active.taskId);

		final int slot = pickDeathMachineSlot(manager, deathMachine, player);
		final WeaponInstance displaced = manager.getWeapon(slot);
		final ItemStack savedStack = player.getInventory().getItem(slot);

		if(displaced != null)
			manager.removeWeapon(displaced);

		final WeaponInstance deathMachineInstance = deathMachine.getNewInstance(player, slot);
		manager.addWeapon(deathMachineInstance);

		DeathMachineGrant grant = new DeathMachineGrant(deathMachineInstance, displaced, savedStack == null ? null : savedStack.clone(), slot);
		grants.put(player.getUniqueId(), grant);
		grant.taskId = COMZombies.scheduleTask(durationTicks, () -> expireDeathMachine(game, player, grant));
	}

	private static int pickDeathMachineSlot(PlayerWeaponManager manager, BaseGun deathMachine, Player player)
	{
		int held = player.getInventory().getHeldItemSlot();
		return manager.getGun(held) != null ? held : manager.getCorrectSlot(deathMachine);
	}

	/**
	 * Ends the player's Death Machine early and puts back the gun it displaced, e.g. before a
	 * downed-player loadout snapshot so the real gun is kept instead of the temporary minigun.
	 */
	public static void revertDeathMachine(Game game, Player player)
	{
		Map<UUID, DeathMachineGrant> grants = activeDeathMachines.get(game);
		DeathMachineGrant grant = grants == null ? null : grants.get(player.getUniqueId());
		if(grant == null)
			return;
		Bukkit.getScheduler().cancelTask(grant.taskId);
		expireDeathMachine(game, player, grant);
	}

	private static void expireDeathMachine(Game game, Player player, DeathMachineGrant grant)
	{
		Map<UUID, DeathMachineGrant> grants = activeDeathMachines.get(game);
		if(grants == null || grants.get(player.getUniqueId()) != grant)
			return;
		grants.remove(player.getUniqueId());

		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		if(manager == null || game.getStatus() != Game.GameStatus.INGAME || GameManager.INSTANCE.getGame(player) != game)
			return;
		if(manager.getWeapon(grant.slot) != grant.instance)
			return;

		manager.removeWeapon(grant.instance);
		if(grant.displaced != null)
			manager.addWeapon(grant.displaced);
		else
			player.getInventory().setItem(grant.slot, grant.savedStack);

		player.updateInventory();
	}

	public void notifyAll(Game game, PowerUp powerUp)
	{
		for(Player pl : game.getPlayersInGame())
		{
			pl.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + powerUp.getDisplay() + "!");
			String sound = com.theprogrammingturkey.comz.util.SoundConfig.get("powerup." + powerUp.name(), com.theprogrammingturkey.comz.util.Compat.soundName(powerUp.getSound()));
			com.theprogrammingturkey.comz.util.SoundUtil.play(pl, pl.getLocation(), sound, org.bukkit.SoundCategory.MASTER, 1, 1);
		}
	}

	/**
	 * Whether the given dropped item is a tracked ground power-up in ANY arena. Now that
	 * {@code currentPowerUps} is per-game, an out-of-game player (or one whose game lookup is
	 * ambiguous) still needs a global scan to know whether to block the pickup. Order is
	 * irrelevant; membership is what matters.
	 */
	private boolean isPowerUpItem(org.bukkit.entity.Item item)
	{
		for(Game game : GameManager.INSTANCE.getGames())
			if(game.powerUpManager.currentPowerUps.contains(item))
				return true;
		return false;
	}
}