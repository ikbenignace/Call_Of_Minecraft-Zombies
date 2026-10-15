package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.ConfigSetup;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.util.BreathingGear;
import com.theprogrammingturkey.comz.util.Compat;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Arena-wide environment (gravity, jump boost, fog) plus the per-zone effects (gravity override, no air,
 * damage, entry titles). One repeating task per running game applies them to the living players and
 * strips them again when a player stops playing or the game ends.
 */
public class EnvironmentManager
{
	private static final double DEFAULT_GRAVITY = 0.08;
	private static final double VANILLA_JUMP_VELOCITY = 0.42;
	/** Extra jump velocity per Jump Boost level. */
	private static final double JUMP_BOOST_PER_LEVEL = 0.1;
	private static final int EFFECT_PERIOD_TICKS = 10;
	private static final int DAMAGE_PERIOD_TICKS = 20;

	private final Game game;

	/** Arena overrides; null means "use the config.yml default". */
	private Double gravity;
	private Double jumpBoost;
	private Boolean fog;

	private final Map<UUID, PlayerEnvironment> states = new HashMap<>();
	private int taskId = -1;
	private long tick;

	public EnvironmentManager(Game game)
	{
		this.game = game;
	}

	public double getGravity()
	{
		return gravity != null ? gravity : ConfigManager.getMainConfig().environmentGravity;
	}

	public void setGravity(double value)
	{
		gravity = clamp(value, 0.05, 4.0);
	}

	public double getJumpBoost()
	{
		return jumpBoost != null ? jumpBoost : ConfigManager.getMainConfig().environmentJumpBoost;
	}

	public void setJumpBoost(double value)
	{
		jumpBoost = clamp(value, 0.5, 4.0);
	}

	public boolean hasFog()
	{
		return fog != null ? fog : ConfigManager.getMainConfig().environmentFog;
	}

	public void setFog(boolean value)
	{
		fog = value;
	}

	private static double clamp(double value, double min, double max)
	{
		return Math.round(Math.max(min, Math.min(max, value)) * 100) / 100.0;
	}

	public void load(JsonObject settings)
	{
		gravity = settings.has("gravity") ? settings.get("gravity").getAsDouble() : null;
		jumpBoost = settings.has("jump_boost") ? settings.get("jump_boost").getAsDouble() : null;
		fog = settings.has("fog") ? settings.get("fog").getAsBoolean() : null;
	}

	/** Only explicitly set values are written, so unset arenas keep following the config defaults. */
	public void save(JsonObject settings)
	{
		if(gravity != null)
			settings.addProperty("gravity", gravity);
		if(jumpBoost != null)
			settings.addProperty("jump_boost", jumpBoost);
		if(fog != null)
			settings.addProperty("fog", fog);
	}

	public void start()
	{
		stop();
		tick = 0;
		taskId = COMZombies.scheduleTask(1L, 1L, this::update);
	}

	public void stop()
	{
		if(taskId != -1)
		{
			Bukkit.getScheduler().cancelTask(taskId);
			taskId = -1;
		}
		for(UUID uuid : new HashSet<>(states.keySet()))
		{
			Player player = Bukkit.getPlayer(uuid);
			if(player != null)
				clear(player);
		}
		states.clear();
	}

	/** Remove every environment effect from {@code player} (leave, death, spectate). */
	public void clear(Player player)
	{
		PlayerEnvironment state = states.remove(player.getUniqueId());
		if(state == null)
			return;
		resetAttribute(player, Compat.GRAVITY);
		resetAttribute(player, Compat.JUMP_STRENGTH);
		if(state.slowFalling)
			player.removePotionEffect(Compat.SLOW_FALLING);
		if(state.jumpAmplifier >= 0)
			player.removePotionEffect(Compat.JUMP_BOOST);
		if(state.fog)
			player.removePotionEffect(Compat.DARKNESS);
		if(state.managingAir)
			player.setRemainingAir(player.getMaximumAir());
	}

	private void update()
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
			return;
		tick++;
		List<Player> living = game.getLivingPlayers();
		Set<UUID> livingIds = new HashSet<>();
		for(Player player : living)
		{
			if(!player.isOnline())
				continue;
			livingIds.add(player.getUniqueId());
			PlayerEnvironment state = states.computeIfAbsent(player.getUniqueId(), id -> new PlayerEnvironment());
			List<Zone> zones = game.zoneManager.getZonesAt(player.getLocation());
			announceEntries(player, state, zones);
			boolean downed = game.downedPlayerManager.isDownedPlayer(player);
			updateOxygen(player, state, zones, downed);
			if(!downed && tick % DAMAGE_PERIOD_TICKS == 0)
				applyZoneDamage(player, zones);
			if(tick % EFFECT_PERIOD_TICKS == 0)
				applyMovement(player, state, zones);
		}

		if(states.size() == livingIds.size())
			return;
		for(UUID uuid : new HashSet<>(states.keySet()))
		{
			if(livingIds.contains(uuid))
				continue;
			Player player = Bukkit.getPlayer(uuid);
			if(player != null)
				clear(player);
			else
				states.remove(uuid);
		}
	}

	private void announceEntries(Player player, PlayerEnvironment state, List<Zone> zones)
	{
		Set<String> now = new HashSet<>();
		for(Zone zone : zones)
		{
			now.add(zone.getId());
			if(state.zoneIds.contains(zone.getId()))
				continue;
			if(zone.hasName() && ConfigManager.getMainConfig().environmentZoneTitles)
				player.sendTitle("", ChatColor.AQUA + ChatColor.translateAlternateColorCodes('&', zone.getName()), 5, 40, 10);
			if(zone.isNoAir() && !BreathingGear.isWearing(player))
				player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "No air here! Breathing gear required."));
		}
		state.zoneIds = now;
	}

	private void updateOxygen(Player player, PlayerEnvironment state, List<Zone> zones, boolean downed)
	{
		boolean noAir = false;
		for(Zone zone : zones)
			noAir |= zone.isNoAir();
		if(!noAir || downed || BreathingGear.isWearing(player))
		{
			// Hand the meter back to vanilla, which refills it outside water on its own.
			state.managingAir = false;
			return;
		}

		ConfigSetup config = ConfigManager.getMainConfig();
		int maxAir = player.getMaximumAir();
		if(!state.managingAir)
		{
			state.managingAir = true;
			state.oxygen = player.getRemainingAir();
		}
		double drainPerTick = maxAir / (Math.max(1, config.environmentOxygenSeconds) * 20.0);
		state.oxygen = Math.max(0, state.oxygen - drainPerTick);
		player.setRemainingAir((int) state.oxygen);
		if(state.oxygen <= 0 && tick % DAMAGE_PERIOD_TICKS == 0 && config.environmentSuffocationDamage > 0)
			player.damage(config.environmentSuffocationDamage);
	}

	private void applyZoneDamage(Player player, List<Zone> zones)
	{
		double damage = 0;
		for(Zone zone : zones)
			damage = Math.max(damage, zone.getDamagePerSecond());
		if(damage > 0)
			player.damage(damage);
	}

	/** Gravity of the smallest zone with an override, else the arena's. */
	public double effectiveGravity(List<Zone> zones)
	{
		for(Zone zone : zones)
			if(zone.hasGravityOverride())
				return zone.getGravity();
		return getGravity();
	}

	private void applyMovement(Player player, PlayerEnvironment state, List<Zone> zones)
	{
		double gravityFactor = effectiveGravity(zones);
		double jumpFactor = getJumpBoost();

		if(Compat.GRAVITY != null && Compat.JUMP_STRENGTH != null)
		{
			setAttribute(player, Compat.GRAVITY, DEFAULT_GRAVITY * gravityFactor);
			setAttribute(player, Compat.JUMP_STRENGTH, VANILLA_JUMP_VELOCITY * jumpFactor);
		}
		else
		{
			// Without the attributes: Slow Falling softens the fall and Jump Boost raises the jump to
			// roughly the height the same jump would reach under the lower gravity (height ~ v^2 / g).
			boolean slowFalling = gravityFactor < 0.95 && Compat.SLOW_FALLING != null;
			state.slowFalling = ensureEffect(player, Compat.SLOW_FALLING, slowFalling ? 0 : -1, state.slowFalling);
			double heightFactor = jumpFactor * jumpFactor / Math.max(0.05, gravityFactor);
			double velocity = VANILLA_JUMP_VELOCITY * Math.sqrt(heightFactor);
			int amplifier = (int) Math.round((velocity - VANILLA_JUMP_VELOCITY) / JUMP_BOOST_PER_LEVEL) - 1;
			boolean applied = ensureEffect(player, Compat.JUMP_BOOST, amplifier, state.jumpAmplifier >= 0);
			state.jumpAmplifier = applied ? amplifier : -1;
		}

		state.fog = ensureEffect(player, Compat.DARKNESS, hasFog() ? 0 : -1, state.fog);
	}

	/**
	 * Keeps {@code type} at {@code amplifier} (re-adding it if something else removed it), or removes it
	 * when {@code amplifier < 0} and we were the ones who applied it. Returns whether it is applied now.
	 */
	private static boolean ensureEffect(Player player, PotionEffectType type, int amplifier, boolean appliedBefore)
	{
		if(type == null)
			return false;
		if(amplifier < 0)
		{
			if(appliedBefore)
				player.removePotionEffect(type);
			return false;
		}
		PotionEffect current = player.getPotionEffect(type);
		if(current == null || current.getAmplifier() != amplifier)
		{
			player.removePotionEffect(type);
			player.addPotionEffect(new PotionEffect(type, Compat.INFINITE_DURATION, amplifier, true, false, false));
		}
		return true;
	}

	private static void setAttribute(Player player, Attribute attribute, double value)
	{
		AttributeInstance instance = player.getAttribute(attribute);
		if(instance != null && Math.abs(instance.getBaseValue() - value) > 1e-6)
			instance.setBaseValue(value);
	}

	private static void resetAttribute(Player player, Attribute attribute)
	{
		if(attribute == null)
			return;
		AttributeInstance instance = player.getAttribute(attribute);
		if(instance != null)
			instance.setBaseValue(instance.getDefaultValue());
	}

	private static final class PlayerEnvironment
	{
		private Set<String> zoneIds = new HashSet<>();
		private boolean managingAir;
		private double oxygen;
		private boolean slowFalling;
		private int jumpAmplifier = -1;
		private boolean fog;
	}
}
