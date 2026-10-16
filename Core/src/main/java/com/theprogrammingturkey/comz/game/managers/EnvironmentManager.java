package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.ConfigSetup;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.util.BreathingGear;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.SoundUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.WeatherType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Arena-wide environment (gravity, jump boost, fog, water wading, time of day, weather) plus the per-zone effects (gravity override, no air,
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
	private static final int WADE_PERIOD_TICKS = 5;
	private static final int SKY_PERIOD_TICKS = 20;
	/** Height above the feet that counts as the waist when deciding how deep a player wades. */
	private static final double WAIST_HEIGHT = 0.9;

	public static final long DAY_TICKS = 6000L;
	public static final long DUSK_TICKS = 12500L;
	public static final long NIGHT_TICKS = 18000L;

	public enum Weather
	{
		CLEAR, RAIN, STORM;

		public String key()
		{
			return name().toLowerCase(Locale.ROOT);
		}

		public static Weather fromKey(String key)
		{
			for(Weather weather : values())
				if(weather.key().equalsIgnoreCase(key))
					return weather;
			return null;
		}
	}

	private final Game game;

	/** Arena overrides; null means "use the config.yml default". */
	private Double gravity;
	private Double jumpBoost;
	private Boolean fog;
	private Double waterWalk;
	private Double waterDeepWalk;
	/** Per-player sky the arena holds during a game (the world may be shared by several arenas); null keeps the world's. */
	private Long timeOfDay;
	private Weather weather;

	private final Map<UUID, PlayerEnvironment> states = new HashMap<>();
	private final Set<UUID> skyPlayers = new HashSet<>();
	private final Random random = new Random();
	private int taskId = -1;
	private long tick;
	private long nextThunderTick;

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

	public double getWaterWalkMultiplier()
	{
		return waterWalk != null ? waterWalk : ConfigManager.getMainConfig().waterWalkMultiplier;
	}

	public double getWaterDeepWalkMultiplier()
	{
		return waterDeepWalk != null ? waterDeepWalk : ConfigManager.getMainConfig().waterDeepWalkMultiplier;
	}

	public Long getTimeOfDay()
	{
		return timeOfDay;
	}

	public void setTimeOfDay(Long ticks)
	{
		timeOfDay = ticks == null ? null : Math.floorMod(ticks, 24000L);
	}

	/** "day", "dusk", "night", "dawn" or a tick count; anything else (e.g. "world") leaves the world's own time. */
	public static Long parseTimeOfDay(String value)
	{
		if(value == null)
			return null;
		switch(value.trim().toLowerCase(Locale.ROOT))
		{
			case "day":
			case "noon":
				return DAY_TICKS;
			case "dusk":
			case "sunset":
				return DUSK_TICKS;
			case "night":
			case "midnight":
				return NIGHT_TICKS;
			case "dawn":
			case "sunrise":
				return 23000L;
			default:
				try
				{
					return Math.floorMod(Long.parseLong(value.trim()), 24000L);
				} catch(NumberFormatException e)
				{
					return null;
				}
		}
	}

	public static String timeOfDayKey(Long ticks)
	{
		if(ticks == null)
			return "world";
		if(ticks == DAY_TICKS)
			return "day";
		if(ticks == DUSK_TICKS)
			return "dusk";
		if(ticks == NIGHT_TICKS)
			return "night";
		return String.valueOf(ticks);
	}

	public Weather getWeather()
	{
		return weather;
	}

	public void setWeather(Weather weather)
	{
		this.weather = weather;
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
		waterWalk = settings.has("water_walk_multiplier") ? clamp(settings.get("water_walk_multiplier").getAsDouble(), 0.05, 1.0) : null;
		waterDeepWalk = settings.has("water_deep_walk_multiplier") ? clamp(settings.get("water_deep_walk_multiplier").getAsDouble(), 0.05, 1.0) : null;
		applyMapSky(game.getName());
		if(settings.has("time_of_day"))
			timeOfDay = parseTimeOfDay(settings.get("time_of_day").getAsString());
		else if(settings.has("force_night") && settings.get("force_night").getAsBoolean())
			timeOfDay = NIGHT_TICKS;
		if(settings.has("weather"))
			weather = Weather.fromKey(settings.get("weather").getAsString());
	}

	/** The sky each Black Ops map is played in, for arenas named after one that never set their own. */
	private void applyMapSky(String arenaName)
	{
		timeOfDay = null;
		weather = null;
		switch(arenaName == null ? "" : arenaName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""))
		{
			case "nachtderuntoten":
			case "verruckt":
			case "derriese":
			case "kinodertoten":
			case "moon":
			case "five":
				timeOfDay = NIGHT_TICKS;
				break;
			case "shinonuma":
				timeOfDay = NIGHT_TICKS;
				weather = Weather.RAIN;
				break;
			case "callofthedead":
				timeOfDay = NIGHT_TICKS;
				weather = Weather.STORM;
				break;
			case "shangrila":
				timeOfDay = DAY_TICKS;
				break;
			case "ascension":
				timeOfDay = DUSK_TICKS;
				break;
			default:
				break;
		}
	}

	/**
	 * Only explicitly set values are written, so unset arenas keep following the config defaults. The sky is
	 * always written so a deliberate "world" sky isn't replaced by the map-name default on the next load.
	 */
	public void save(JsonObject settings)
	{
		if(gravity != null)
			settings.addProperty("gravity", gravity);
		if(jumpBoost != null)
			settings.addProperty("jump_boost", jumpBoost);
		if(fog != null)
			settings.addProperty("fog", fog);
		if(waterWalk != null)
			settings.addProperty("water_walk_multiplier", waterWalk);
		if(waterDeepWalk != null)
			settings.addProperty("water_deep_walk_multiplier", waterDeepWalk);
		settings.addProperty("time_of_day", timeOfDayKey(timeOfDay));
		settings.addProperty("weather", weather == null ? "world" : weather.key());
	}

	public void start()
	{
		stop();
		tick = 0;
		nextThunderTick = nextThunderDelay();
		if(hasAnythingToApply())
			taskId = COMZombies.scheduleTask(1L, 1L, this::update);
	}

	/** Zones and arena settings only change in build mode, which disables the arena, so checking at start suffices. */
	private boolean hasAnythingToApply()
	{
		if(Math.abs(getGravity() - 1.0) > 1e-6 || Math.abs(getJumpBoost() - 1.0) > 1e-6 || hasFog())
			return true;
		if(getWaterWalkMultiplier() < 1.0 || getWaterDeepWalkMultiplier() < 1.0 || timeOfDay != null || weather != null)
			return true;
		if(game.zoneManager.hasPushOutZones())
			return true;
		boolean titles = ConfigManager.getMainConfig().environmentZoneTitles;
		for(Zone zone : game.zoneManager.getZones())
			if((titles && zone.hasName()) || zone.isNoAir() || zone.getDamagePerSecond() > 0 || zone.hasGravityOverride())
				return true;
		return false;
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
		for(UUID uuid : skyPlayers)
		{
			Player player = Bukkit.getPlayer(uuid);
			if(player != null)
				resetSky(player);
		}
		skyPlayers.clear();
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
		PlayerSpeed.setWading(player, 1f);
	}

	private void update()
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
			return;
		tick++;
		if(tick % SKY_PERIOD_TICKS == 1)
			updateSky();
		if(weather == Weather.STORM && tick >= nextThunderTick)
		{
			nextThunderTick = tick + nextThunderDelay();
			thunder();
		}
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
			if(!downed && !game.transportManager.isRiding(player))
				game.zoneManager.pushOut(player, zones);
			updateOxygen(player, state, zones, downed);
			if(!downed && tick % DAMAGE_PERIOD_TICKS == 0)
				applyZoneDamage(player, zones);
			if(tick % EFFECT_PERIOD_TICKS == 0)
				applyMovement(player, state, zones);
			if(tick % WADE_PERIOD_TICKS == 0)
				PlayerSpeed.setWading(player, wadingMultiplier(player));
		}
		if(tick % EFFECT_PERIOD_TICKS == 0)
			game.zoneManager.pushOutMobs(game.spawnManager.getEntities());

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

	/** Players and spectators see the arena's sky; anyone who left gets the world's back. */
	private void updateSky()
	{
		Set<UUID> present = new HashSet<>();
		for(Player player : game.getPlayersAndSpectators())
		{
			if(player == null || !player.isOnline())
				continue;
			present.add(player.getUniqueId());
			if(timeOfDay != null && (player.isPlayerTimeRelative() || player.getPlayerTime() % 24000L != timeOfDay))
				player.setPlayerTime(timeOfDay, false);
			WeatherType wanted = weather == null ? null : weather == Weather.CLEAR ? WeatherType.CLEAR : WeatherType.DOWNFALL;
			if(wanted != null && player.getPlayerWeather() != wanted)
				player.setPlayerWeather(wanted);
			skyPlayers.add(player.getUniqueId());
		}
		for(UUID uuid : new HashSet<>(skyPlayers))
		{
			if(present.contains(uuid))
				continue;
			skyPlayers.remove(uuid);
			Player player = Bukkit.getPlayer(uuid);
			if(player != null)
				resetSky(player);
		}
	}

	private void resetSky(Player player)
	{
		if(timeOfDay != null)
			player.resetPlayerTime();
		if(weather != null)
			player.resetPlayerWeather();
	}

	private long nextThunderDelay()
	{
		return 200L + random.nextInt(500);
	}

	/** Per-player weather can't thunder, so storms roll thunder for the arena's players only. */
	private void thunder()
	{
		float pitch = 0.8f + random.nextFloat() * 0.4f;
		for(Player player : game.getPlayersAndSpectators())
		{
			if(player == null || !player.isOnline())
				continue;
			Location at = player.getLocation().add(random.nextInt(61) - 30, 20, random.nextInt(61) - 30);
			SoundUtil.play(player, at, "minecraft:entity.lightning_bolt.thunder", SoundCategory.WEATHER, 1.5f + random.nextFloat(), pitch);
		}
	}

	private float wadingMultiplier(Player player)
	{
		Location feet = player.getLocation();
		if(!isWater(feet.getBlock()))
			return 1f;
		Block waist = feet.getWorld().getBlockAt(feet.getBlockX(), (int) Math.floor(feet.getY() + WAIST_HEIGHT), feet.getBlockZ());
		return (float) (isWater(waist) ? getWaterDeepWalkMultiplier() : getWaterWalkMultiplier());
	}

	private static boolean isWater(Block block)
	{
		if(block.getType() == Material.WATER)
			return true;
		BlockData data = block.getBlockData();
		return data instanceof Waterlogged && ((Waterlogged) data).isWaterlogged();
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
			if(zone.isNoAir() && isAirless(zones) && !BreathingGear.isWearing(player))
				player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "No air here! Breathing gear required."));
		}
		state.zoneIds = now;
	}

	private void updateOxygen(Player player, PlayerEnvironment state, List<Zone> zones, boolean downed)
	{
		if(!isAirless(zones) || downed || BreathingGear.isWearing(player))
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

	/** No air when some zone says so and no air pocket covers the spot. */
	public static boolean isAirless(List<Zone> zones)
	{
		boolean noAir = false;
		for(Zone zone : zones)
		{
			if(zone.isAirPocket())
				return false;
			noAir |= zone.isNoAir();
		}
		return noAir;
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
