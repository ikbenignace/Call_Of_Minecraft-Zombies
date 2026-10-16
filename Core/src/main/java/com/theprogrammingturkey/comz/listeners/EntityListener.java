package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.BuildableItems;
import com.theprogrammingturkey.comz.game.features.LowHealthVignette;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.Tombstone;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.inventory.ItemStack;
import com.theprogrammingturkey.comz.spawning.BossSpawner;
import com.theprogrammingturkey.comz.spawning.RoundSpawner;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityListener implements Listener
{
	/**
	 * Single registered instance (set in the constructor, since {@code COMZombies.registerEvents}
	 * constructs one EntityListener). Lets {@link com.theprogrammingturkey.comz.game.Game#endGame}
	 * clear heal timers without holding a listener reference itself.
	 */
	private static EntityListener instance = null;

	private final Map<Player, Integer> healTimers = new HashMap<>();

	public EntityListener()
	{
		instance = this;
	}

	/** Metadata key used to record the last time a zombie damaged a player (0c). */
	private static final String LAST_ATTACK_META = "comz_last_attack_ms";

	/**
	 * Tier 0f — flat knife damage: a one-shot through the configured round, i.e.
	 * the (compressed) zombie HP of that round, independent of the current wave.
	 */
	public static float knifeDamage(int throughRound)
	{
		return RoundSpawner.zombieHealth(throughRound);
	}

	/**
	 * Kills a game mob through vanilla death so it plays the fall-over animation instead of vanishing. Silenced
	 * because the kill path plays the configured death sound itself; drops and XP are cleared in
	 * {@link EXPListener}. The body is removed shortly after in case another plugin cancels the death.
	 */
	public static void playDeath(Mob mob)
	{
		if(mob.isDead() || !mob.isValid())
			return;
		try
		{
			mob.setSilent(true);
			mob.setHealth(0);
		}
		catch(RuntimeException e)
		{
			mob.remove();
			return;
		}
		COMZombies.scheduleTask(30, () ->
		{
			if(mob.isValid())
				mob.remove();
		});
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void entityCombustEvent(EntityCombustEvent event)
	{
		if(GameManager.INSTANCE.isEntityInGame(event.getEntity()))
			event.setCancelled(true);
	}

	@EventHandler
	public void spawn(CreatureSpawnEvent event)
	{
		Entity entity = event.getEntity();
		// Tombstone bodies are summoned by command (no Bukkit API for mannequins) and must survive.
		if(entity.getScoreboardTags().contains(Tombstone.ENTITY_TAG))
			return;
		if(!event.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.CUSTOM))
			if(GameManager.INSTANCE.isLocationInGame(entity.getLocation()))
				event.setCancelled(true);
	}

	@EventHandler
	public void damage(EntityDamageByEntityEvent e)
	{
		if(e.getEntity() instanceof Player)
		{
			if(GameManager.INSTANCE.isPlayerInGame((Player) e.getEntity()))
			{
				if(e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK)
				{
					Entity damager = e.getDamager();
					if(damager instanceof Player || !GameManager.INSTANCE.isEntityInGame(damager))
					{
						e.setCancelled(true);
						return;
					}

					final Player player = (Player) e.getEntity();
					Game game = GameManager.INSTANCE.getGame(player);
					if(game.downedPlayerManager.isDownedPlayer(player))
					{
						e.setCancelled(true);
						return;
					}

					// 0c — per-zombie attack cooldown: each attacker may only hit a player
					// once per zombieAttackCooldownTicks; hits from different zombies stack
					// (no shared i-frame). Last-attack time lives in the ATTACKER's metadata.
					long now = System.currentTimeMillis();
					long cooldownMs = (long) ConfigManager.getMainConfig().zombieAttackCooldownTicks * 50L;
					long last = 0L;
					for(MetadataValue mv : damager.getMetadata(LAST_ATTACK_META))
					{
						if(mv.getOwningPlugin() == COMZombies.getPlugin())
						{
							last = mv.asLong();
							break;
						}
					}
					if(now - last < cooldownMs)
					{
						e.setCancelled(true);
						return;
					}
					damager.setMetadata(LAST_ATTACK_META, new FixedMetadataValue(COMZombies.getPlugin(), now));
					if(damager instanceof org.bukkit.entity.Zombie)
						com.theprogrammingturkey.comz.util.SoundUtil.playKey(damager.getWorld(), damager.getLocation(), "zombie.attack", "", org.bukkit.SoundCategory.HOSTILE, 1.0F, 1.0F);

					float damage = zombieHitDamage(game.perkManager.hasPerk(player, PerkType.JUGGERNOG));

					// Tier 4 — Zombie Shield (buildable): while held, reduce incoming zombie melee
					// damage and consume one of the shield's hits. When hits run out it breaks.
					damage = applyZombieShield(player, damage);

					damage = game.damagePlayer(player, damage);
					// The hit is applied by hand so armour can't change the hits-to-down count and the
					// player gets no vanilla knockback, as in BO.
					e.setCancelled(true);
					if(damage == 0)
						return;
					applyZombieHit(player, damage);

					//heal system
					resetHealingTimer(player);
					updateVignette(player);

					// Tier 4 — Brutus ability: when Brutus damages a player, temporarily disable one
					// of that player's perks, then restore it after brutusDisableSeconds (reversible).
					if(damager instanceof Mob && BossSpawner.isBrutus((Mob) damager))
						brutusDisablePerk(game, player);
				}
			}
		}
		else if(e.getEntity() instanceof Mob)
		{
			Entity entity = e.getEntity();
			Game game = GameManager.INSTANCE.getGame(entity);

			if(game == null)
				return;

			if(e.getDamager() instanceof Player)
			{
				if(e.getCause().equals(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK))
				{
					e.setCancelled(true);
					return;
				}

				Player player = (Player) e.getDamager();
				// BO2: there is no knife ITEM — you melee with whatever you're holding (your gun/fist)
				// and it deals the knife's damage. So any in-game player melee on a zombie knifes,
				// regardless of held item (was previously gated to a held IRON_SWORD).
				if(game.getPlayersInGame().contains(player))
				{
					Mob mob = (Mob) entity;
					double dist = mob.getLocation().distance(player.getLocation());
					if(dist <= ConfigManager.getMainConfig().meleeRange && game.equipmentManager.tryMelee(player))
					{
						float damage = game.equipmentManager.meleeDamage(player, knifeDamage(ConfigManager.getMainConfig().knifeOneShotThroughRound));
						game.damageMob(mob, player, damage, false, true);
						// BO2-fidelity: a knife-slash beat on a connecting melee — sweep arc + slash sound.
						// Pack-independent (vanilla particle/sound); the player's own arm swing supplies the motion.
						org.bukkit.Location hit = mob.getLocation().add(0, 1, 0);
						mob.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, hit, 1, 0, 0, 0, 0);
						com.theprogrammingturkey.comz.game.equipment.Equipment melee = game.equipmentManager.getMelee(player);
						String swing = melee == null ? null : melee.sound("swing");
						if(swing != null)
							com.theprogrammingturkey.comz.util.SoundUtil.play(mob.getWorld(), player.getLocation(), swing, org.bukkit.SoundCategory.PLAYERS, 0.8F, 1.0F);
						else
							com.theprogrammingturkey.comz.util.SoundUtil.playKey(mob.getWorld(), player.getLocation(), "melee.swing", "ENTITY_PLAYER_ATTACK_SWEEP", org.bukkit.SoundCategory.PLAYERS, 0.8F, 1.0F);
						com.theprogrammingturkey.comz.util.SoundUtil.playKey(mob.getWorld(), hit, "melee.hit", "ENTITY_PLAYER_ATTACK_STRONG", org.bukkit.SoundCategory.PLAYERS, 1.0F, 1.0F);
					}
				}
			}
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void damgeEvent(EntityDamageEvent e)
	{
		if(e.isCancelled())
			return;
		if(!GameManager.INSTANCE.isEntityInGame(e.getEntity()))
			return;

		if(e.getEntity() instanceof Player)
		{
			// Melee hits are fully handled (and cancelled) by damage(); listener order is not guaranteed.
			if(e instanceof EntityDamageByEntityEvent && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK)
				return;
			Player player = (Player) e.getEntity();
			Game game = GameManager.INSTANCE.getGame(player);

			if(game.downedPlayerManager.isDownedPlayer(player))
				e.setCancelled(true);

			if(game.getStatus() == Game.GameStatus.STARTING)
				e.setCancelled(true);

			if(game.getStatus() == Game.GameStatus.INGAME)
			{
				float damage = game.damagePlayer(player, (float) e.getDamage());
				e.setDamage(damage);
				if(damage == 0)
					e.setCancelled(true);
			}

			player.getLocation().getWorld().playEffect(player.getLocation().add(0, 1, 0), Effect.STEP_SOUND, 152);
		}
		else if((e.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK || e.getCause() == EntityDamageEvent.DamageCause.FIRE) && e.getEntity() instanceof Mob)
		{
			// Burning (fire traps, hellhounds) is visual only: zombie deaths must go through the game's kill path
			// so the round count stays right.
			e.setCancelled(true);
		}
		else if(e.getCause().equals(EntityDamageEvent.DamageCause.LAVA) && e.getEntity() instanceof Mob)
		{
			Mob m = (Mob) e.getEntity();
			Game game = GameManager.INSTANCE.getGame(m);
			m.setFireTicks(0);
			m.teleport(game.arena.getPlayerTPLocation());
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onHealthRegen(EntityRegainHealthEvent e)
	{
		Entity ent = e.getEntity();
		if(ent instanceof Player && GameManager.INSTANCE.isPlayerInGame((Player) ent))
			e.setCancelled(true);
	}

	/**
	 * Bug fix: heal-timer leak. {@link #healTimers} entries were only ever removed when a player's
	 * health reached full (via {@link #stopHealingTimer}). A player who disconnected mid-regen left
	 * their task id in the map forever, so ids accumulated and a later player reusing the (stale)
	 * Player key could mis-cancel the wrong scheduled task. Cancel and drop the leaving player's
	 * heal task on quit.
	 */
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e)
	{
		stopHealingTimer(e.getPlayer());
		com.theprogrammingturkey.comz.game.dev.DevTools.clearGod(e.getPlayer());
	}

	/** Damage of one zombie hit so a player goes down on exactly the configured hit (BO: 2, or 5 with Juggernog). */
	public static float zombieHitDamage(boolean juggernog)
	{
		int hits = juggernog ? ConfigManager.getMainConfig().juggernogHitsToDown : ConfigManager.getMainConfig().zombieHitsToDown;
		if(hits > 0)
			return PLAYER_HEALTH / hits;
		float damage = (float) ConfigManager.getMainConfig().zombieDamage;
		return juggernog ? damage / (float) ConfigManager.getMainConfig().juggernogHealth : damage;
	}

	private static final float PLAYER_HEALTH = 20f;

	private static void applyZombieHit(Player player, float damage)
	{
		player.setHealth(Math.max(1D, Math.min(player.getHealth(), PLAYER_HEALTH) - damage));
		player.playEffect(EntityEffect.HURT);
		player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
		player.getWorld().playEffect(player.getLocation().add(0, 1, 0), Effect.STEP_SOUND, 152);
	}

	/**
	 * Tier 4 — Zombie Shield damage hook. If the player is holding a Zombie Shield (main hand
	 * or off hand) with hits remaining, reduces the incoming damage by
	 * {@code config.buildable.zombieShieldDamageReduction} and decrements the shield's hit count
	 * (updating its lore). When the count hits zero the shield item is removed (it breaks).
	 *
	 * @return the (possibly reduced) damage to apply.
	 */
	private float applyZombieShield(Player player, float damage)
	{
		ItemStack hand = player.getInventory().getItemInMainHand();
		boolean offHand = false;
		if(!BuildableItems.isZombieShield(hand))
		{
			hand = player.getInventory().getItemInOffHand();
			offHand = true;
			if(!BuildableItems.isZombieShield(hand))
				return damage;
		}

		int hitsLeft = BuildableItems.getShieldHitsRemaining(hand);
		if(hitsLeft <= 0)
			return damage;

		double reduction = ConfigManager.getMainConfig().zombieShieldDamageReduction;
		reduction = Math.max(0.0, Math.min(1.0, reduction));
		float reduced = (float) (damage * (1.0 - reduction));

		int remaining = hitsLeft - 1;
		if(remaining <= 0)
		{
			// Shield breaks.
			if(offHand)
				player.getInventory().setItemInOffHand(null);
			else
				player.getInventory().setItemInMainHand(null);
			player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Your Zombie Shield broke!");
		}
		else
		{
			ItemStack updated = BuildableItems.createZombieShield(remaining);
			if(offHand)
				player.getInventory().setItemInOffHand(updated);
			else
				player.getInventory().setItemInMainHand(updated);
		}

		return reduced;
	}

	private void startHealingTimer(Player player)
	{
		if(!healTimers.containsKey(player))
		{
			BukkitRunnable healingTask = new BukkitRunnable()
			{
				@Override
				public void run()
				{
					Game game = GameManager.INSTANCE.getGame(player);
					if(!player.isOnline() || game == null || game.getStatus() != Game.GameStatus.INGAME
							|| game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
					{
						stopHealingTimer(player);
						return;
					}
					int currentHealth = (int) player.getHealth();
					if(currentHealth < 20)
						player.setHealth(Math.min(currentHealth + 1, 20));
					else
						stopHealingTimer(player);
					updateVignette(player);
				}
			};
			//every tick
			Game game = GameManager.INSTANCE.getGame(player);
			double delaySeconds = ConfigManager.getMainConfig().healTime;
			if(game != null && game.perkManager.hasPerk(player, PerkType.QUICK_REVIVE))
				delaySeconds *= ConfigManager.getMainConfig().quickReviveHealDelayFactor;
			int taskId = healingTask.runTaskTimer(COMZombies.getPlugin(), Math.round(20 * delaySeconds), 1).getTaskId();
			healTimers.put(player, taskId);
		}
	}

	private void updateVignette(Player player)
	{
		if(ConfigManager.getMainConfig().visualsLowHealthVignette && player.getHealth() <= 12 && player.getHealth() > 0)
			LowHealthVignette.show(player);
		else
			LowHealthVignette.clear(player);
	}

	private void stopHealingTimer(Player player)
	{
		LowHealthVignette.clear(player);
		if(healTimers.containsKey(player))
		{
			Bukkit.getScheduler().cancelTask(healTimers.get(player));
			healTimers.remove(player);
		}
	}

	/**
	 * Clears every outstanding heal timer. Called on game end and plugin disable so no recurring
	 * heal task survives a game/server stop against players who are no longer in a game. Quit is
	 * already handled per-player by {@link #onPlayerQuit}.
	 */
	public static void clearHealTimers()
	{
		if(instance == null)
			return;
		for(Integer taskId : instance.healTimers.values())
			Bukkit.getScheduler().cancelTask(taskId);
		instance.healTimers.clear();
		LowHealthVignette.clearAll();
	}

	private void resetHealingTimer(Player player)
	{
		stopHealingTimer(player);
		startHealingTimer(player);
	}

	/**
	 * Tier 4 — Brutus perk-disable: removes one of the player's currently held perks and restores
	 * it after {@code brutusDisableSeconds}. No-op if the player holds no perks. Reversible and
	 * simple: a full remove + re-grant of a single random perk rather than a greyed-out icon.
	 */
	private void brutusDisablePerk(Game game, final Player player)
	{
		List<PerkType> held = game.perkManager.getPlayersPerks(player);
		if(held.isEmpty())
			return;

		final PerkType disabled = held.get(COMZombies.rand.nextInt(held.size()));
		game.perkManager.removePerkEffect(player, disabled);
		player.sendMessage(ChatColor.RED + "Brutus disabled your " + disabled + "!");

		// Going down, Who's Who, leaving or a new game all replace the per-player perk list, so
		// identity of this list doubles as a "same life, same session" token for the restore.
		final List<PerkType> perkListAtDisable = held;
		long restoreTicks = (long) Math.max(1, ConfigManager.getMainConfig().brutusDisableSeconds) * 20L;
		COMZombies.scheduleTask(restoreTicks, () ->
		{
			if(game.getStatus() != Game.GameStatus.INGAME || !game.getPlayersInGame().contains(player))
				return;
			if(game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
				return;
			List<PerkType> current = game.perkManager.getPlayersPerks(player);
			if(current != perkListAtDisable || current.contains(disabled))
				return;
			// Restored directly rather than via addPerk: the perk was owned before, so the maxPerks
			// cap (which may have been filled in the meantime) must not swallow it.
			current.add(disabled);
			disabled.initialEffect(player);
			game.perkManager.applyPerkEffect(player, disabled);
			player.sendMessage(ChatColor.GREEN + "Your " + disabled + " is back.");
		});
	}
}
