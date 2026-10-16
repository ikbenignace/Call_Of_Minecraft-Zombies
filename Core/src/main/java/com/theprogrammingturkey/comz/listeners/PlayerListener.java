package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.CachedPlayerInfo;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.DownedPlayer;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener
{
	private static final long REVIVE_PROMPT_INTERVAL_MS = 3000;
	private final Map<UUID, Long> lastRevivePrompt = new HashMap<>();

	private boolean canSendRevivePrompt(Player player)
	{
		long now = System.currentTimeMillis();
		Long last = lastRevivePrompt.get(player.getUniqueId());
		if(last != null && now - last < REVIVE_PROMPT_INTERVAL_MS)
			return false;
		lastRevivePrompt.put(player.getUniqueId(), now);
		return true;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		CachedPlayerInfo.restoreBackup(player);
		for(Player pl : Bukkit.getOnlinePlayers())
		{
			if(GameManager.INSTANCE.isPlayerInGame(pl))
				pl.hidePlayer(COMZombies.getPlugin(), player);
			else
				pl.showPlayer(COMZombies.getPlugin(), player);
			player.showPlayer(COMZombies.getPlugin(), pl);
		}

		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null)
			return;
		if(game.isPlayerExited(player))
			CommandUtil.sendClickableMessageToPlayer(player, ChatColor.RED + "You can reconnect to your last game with /z rejoin or ", "CLICK HERE", "/z rejoin");
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event)
	{
		Player player = event.getPlayer();
		lastRevivePrompt.remove(player.getUniqueId());
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			Game game = GameManager.INSTANCE.getGame(player);
			game.removePlayer(player);
			player.removePotionEffect(PotionEffectType.BLINDNESS);
			player.removePotionEffect(Compat.SLOWNESS);
		}
		for(Player pl : Bukkit.getOnlinePlayers())
		{
			player.showPlayer(COMZombies.getPlugin(), pl);
			pl.showPlayer(COMZombies.getPlugin(), player);
		}
	}

	/**
	 * Checks if the player is leaving the arena and takes care of his action.
	 */
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		Game game = GameManager.INSTANCE.getGame(player);
		Location toLoc = event.getTo();
		float DEAD_ZONE = 0.02f;

		// Players who left, died or only spectate are free to go wherever they like.
		if(game == null || toLoc == null || !game.isPlayerAlive(player))
			return;

		if(!game.arena.containsBlock(toLoc) && !game.transportManager.isRiding(player))
		{
			if(game.getStatus() == Game.GameStatus.INGAME)
			{
				player.teleport(game.arena.getPlayerTPLocation());
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Please do not leave the arena!");
			}
		}


		// Reviving move check
		Location change = toLoc.clone().subtract(event.getFrom().clone());
		if(player.getGameMode() != GameMode.SPECTATOR && game.downedPlayerManager.isDownedPlayer(player) && change.getY() != 0)
			event.setCancelled(true);

		DownedPlayer downedPlayer = game.downedPlayerManager.getDownedPlayerForReviver(player);
		if(downedPlayer != null)
		{
			if(downedPlayer.isPlayerDown() && change.lengthSquared() > DEAD_ZONE)
			{
				// #143 — ignore small movement within the first few ticks of starting a revive. The
				// right-click interact that starts a revive causes a tiny position jitter that used
				// to instantly cancel the revive with a false "You Moved!", forcing a second click.
				if(!downedPlayer.isInReviveGracePeriod())
				{
					player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "You Moved! You are no longer reviving " + downedPlayer.getPlayer().getName());
					downedPlayer.cancelRevive();
				}
			}
		}

		//get downed players and show message if nearby
		for(DownedPlayer dp : game.downedPlayerManager.getDownedPlayers())
		{
			Player downed = dp.getPlayer();
			if(!dp.isPlayerDown() || dp.isBeingRevived() || downed.equals(player) || !downed.getWorld().equals(player.getWorld()))
				continue;
			if(downed.getLocation().distanceSquared(player.getLocation()) < 25 && canSendRevivePrompt(player))
				player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Revive " + downed.getName() + " by right clicking them!");
		}


		//Barriers move check
		// #140 — only block movement INTO a barrier block, never out of one. The old check cancelled
		// any move whose destination matched a barrier block, which trapped a player who ended up
		// inside a barrier (e.g. one placed around them, or pushed into a gap) because every escape
		// direction resolved to a barrier block and was cancelled. Now we let them leave.
		for(Barrier barrier : game.barrierManager.getBarriers())
		{
			for(Block b : barrier.getBlocks())
			{
				if(toLoc.getBlockX() == b.getX() && toLoc.getBlockY() == b.getY() && toLoc.getBlockZ() == b.getZ())
				{
					Location fromLoc = event.getFrom();
					boolean fromInside = fromLoc.getBlockX() == b.getX() && fromLoc.getBlockY() == b.getY() && fromLoc.getBlockZ() == b.getZ();
					if(!fromInside)
					{
						event.setCancelled(true);
						return;
					}
				}
			}
		}
	}

	@EventHandler
	public void OnPlayerVelocityEvent(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			player.setFoodLevel(20);
		}
	}

	private void phdLanding(Game game, Player player)
	{
		Location loc = player.getLocation();
		loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
		loc.getWorld().spawnParticle(Compat.EXPLOSION_EMITTER, loc, 1);
		for(int i = 0; i < 30; i++)
		{
			float x = (COMZombies.rand.nextFloat() * 2f);
			float y = (COMZombies.rand.nextFloat() * 2f);
			float z = (COMZombies.rand.nextFloat() * 2f);
			player.getWorld().spawnParticle(Particle.LAVA, loc.getX(), loc.getY(), loc.getZ(), 1, x, y, z, 1);
			player.getWorld().spawnParticle(Compat.FIREWORK, loc.getX(), loc.getY(), loc.getZ(), 1, x, y, z, 1);
		}

		double radius = ConfigManager.getMainConfig().phdFlopperRadius;
		float damage = (float) ConfigManager.getMainConfig().phdFlopperDamage;
		for(Entity e : player.getNearbyEntities(radius, radius, radius))
			if(e instanceof Mob && game.spawnManager.isEntitySpawned((Mob) e))
				game.damageMob((Mob) e, player, damage);
	}

	@EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
	public void ProjectileHit(EntityDamageEvent event)
	{
		if(event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
		{
			if(event.getEntity() instanceof Player)
			{
				Player player = (Player) event.getEntity();
				if(GameManager.INSTANCE.isPlayerInGame(player))
					event.setCancelled(true);
			}
		}
		else if(event.getCause() == EntityDamageEvent.DamageCause.FALL)
		{
			if(event.getEntity() instanceof Player)
			{
				Player player = (Player) event.getEntity();
				if(GameManager.INSTANCE.isPlayerInGame(player))
					if(GameManager.INSTANCE.getGame(player).perkManager.hasPerk(player, PerkType.PHD_FLOPPER))
					{
						event.setCancelled(true);
						phdLanding(GameManager.INSTANCE.getGame(player), player);
					}
			}
		}
	}

	@EventHandler
	public void playerExp(PlayerExpChangeEvent event)
	{
		Player player = event.getPlayer();
		if(GameManager.INSTANCE.isPlayerInGame(player))
			player.setExp(0);
	}

	@EventHandler
	public void interact(PlayerInteractAtEntityEvent event)
	{
		if(!(event.getRightClicked() instanceof Player))
			return;

		Player reviver = event.getPlayer();
		Player clickedPlayer = (Player) event.getRightClicked();

		Game game = GameManager.INSTANCE.getGame(reviver);

		if(game == null || !game.isPlayerPlaying(clickedPlayer))
			return;


		Location clickedPlayerLoc = clickedPlayer.getLocation();
		if(clickedPlayerLoc.getWorld() == null || !clickedPlayerLoc.getWorld().equals(reviver.getWorld()))
			return;
		if(reviver.getLocation().distance(clickedPlayerLoc) > ConfigManager.getMainConfig().reviveRange)
			return;

		if(game.downedPlayerManager.isDownedPlayer(reviver))
			return;

		DownedPlayer downedPlayer = game.downedPlayerManager.getDownedPlayer(clickedPlayer);
		if(downedPlayer == null)
			return;

		if(downedPlayer.isBeingRevived())
			return;

		if(!game.isActivePlayer(reviver))
			return;

		if(GameManager.INSTANCE.isPlayerInGame(reviver))
		{
			reviver.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "You are reviving " + clickedPlayer.getName());
			clickedPlayer.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "You are being revived by " + reviver.getName() + "!");
			downedPlayer.startRevive(reviver);
			event.setCancelled(true);
		}
	}
}
