package com.theprogrammingturkey.comz.game.traps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.ConfigSetup;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.util.Compat;
import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One entry of the trap catalogue ({@code traps.json}): how a trap looks, sounds and hurts. Arena traps only
 * store the type id plus their own cost/duration/cooldown overrides.
 */
public class TrapType
{
	public enum Style
	{
		ELECTRIC, FIRE, ARM, SPIKES, TURRET, GENERIC;

		static Style parse(String value)
		{
			for(Style style : values())
				if(style.name().equalsIgnoreCase(value))
					return style;
			return GENERIC;
		}
	}

	public enum DamageModel
	{
		INSTANT_KILL, PER_TICK
	}

	public static final String GENERIC_ID = "generic";

	private final String id;
	private final String name;
	private final Style style;
	private final int cost;
	private final int durationSeconds;
	private final int cooldownSeconds;
	private final boolean requiresPower;
	private final DamageModel damageModel;
	private final double mobDamage;
	private final double mobDamagePercent;
	private final int hitIntervalTicks;
	private final int igniteTicks;
	private final boolean hurtsPlayers;
	private final double playerDamage;
	private final double knockback;
	private final Particle particle;
	private final Particle secondaryParticle;
	private final int density;
	private final Material armBlock;
	private final double armDegreesPerTick;
	private final double armHeight;
	private final double armThickness;
	private final double armHitWidth;
	private final Material spikeBlock;
	private final double spikeSpacing;
	private final int spikeCycleTicks;
	private final int pointsPerKill;
	private final TrapSound activateSound;
	private final TrapSound loopSound;
	private final TrapSound killSound;

	TrapType(String id, JsonObject json, int defaultPointsPerKill)
	{
		ConfigSetup cfg = ConfigManager.getMainConfig();
		this.id = id.toLowerCase(Locale.ROOT);
		name = CustomConfig.getString(json, "name", capitalise(this.id));
		style = Style.parse(CustomConfig.getString(json, "style", "generic"));
		cost = Math.max(0, CustomConfig.getInt(json, "cost", 1000));
		durationSeconds = Math.max(1, CustomConfig.getInt(json, "duration_seconds", cfg == null ? 10 : cfg.trapDurationSeconds));
		cooldownSeconds = Math.max(0, CustomConfig.getInt(json, "cooldown_seconds", cfg == null ? 60 : cfg.trapCooldownSeconds));
		requiresPower = CustomConfig.getBoolean(json, "requires_power", true);
		damageModel = "per_tick".equalsIgnoreCase(CustomConfig.getString(json, "damage_model", "instant_kill")) ? DamageModel.PER_TICK : DamageModel.INSTANT_KILL;
		mobDamage = Math.max(0, CustomConfig.getDouble(json, "mob_damage", 0));
		mobDamagePercent = Math.max(0, CustomConfig.getDouble(json, "mob_damage_percent", 0));
		hitIntervalTicks = Math.max(1, CustomConfig.getInt(json, "hit_interval_ticks", 10));
		igniteTicks = Math.max(0, CustomConfig.getInt(json, "ignite_ticks", 0));
		hurtsPlayers = CustomConfig.getBoolean(json, "hurts_players", false);
		playerDamage = Math.max(0, CustomConfig.getDouble(json, "player_damage", 0));
		knockback = Math.max(0, CustomConfig.getDouble(json, "knockback", 0));
		particle = particle(json, "particles");
		secondaryParticle = particle(json, "secondary_particles");
		density = Math.max(0, CustomConfig.getInt(json, "density", 6));
		armBlock = material(CustomConfig.getString(json, "arm_block", ""), Material.OAK_LOG);
		armDegreesPerTick = CustomConfig.getDouble(json, "arm_degrees_per_tick", 8);
		armHeight = CustomConfig.getDouble(json, "arm_height", 1.0);
		armThickness = Math.max(0.1, CustomConfig.getDouble(json, "arm_thickness", 0.5));
		armHitWidth = Math.max(0.2, CustomConfig.getDouble(json, "arm_hit_width", 1.0));
		spikeBlock = material(CustomConfig.getString(json, "spike_block", ""),
				material(CustomConfig.getString(json, "spike_fallback_block", ""), Material.IRON_BARS));
		spikeSpacing = Math.max(0.75, CustomConfig.getDouble(json, "spike_spacing", 1.5));
		spikeCycleTicks = Math.max(4, CustomConfig.getInt(json, "spike_cycle_ticks", 20));
		pointsPerKill = Math.max(0, CustomConfig.getInt(json, "points_per_kill", defaultPointsPerKill));

		JsonObject sounds = json.has("sounds") && json.get("sounds").isJsonObject() ? json.getAsJsonObject("sounds") : new JsonObject();
		activateSound = TrapSound.parse(sounds, "activate");
		loopSound = TrapSound.parse(sounds, "loop");
		killSound = TrapSound.parse(sounds, "kill");
	}

	/** Hard-coded fallback used when traps.json has no usable "generic" entry; matches the old untyped trap. */
	static TrapType builtinGeneric()
	{
		JsonObject json = new JsonObject();
		json.addProperty("name", "Trap");
		json.addProperty("style", "generic");
		JsonArray particles = new JsonArray();
		particles.add("FLAME");
		json.add("particles", particles);
		json.addProperty("density", 30);
		return new TrapType(GENERIC_ID, json, 0);
	}

	private static Particle particle(JsonObject json, String key)
	{
		if(!json.has(key) || !json.get(key).isJsonArray())
			return null;
		List<String> names = new ArrayList<>();
		for(JsonElement element : json.getAsJsonArray(key))
			if(element.isJsonPrimitive())
				names.add(element.getAsString());
		if(names.isEmpty())
			return null;
		Particle particle = Compat.particle(names.toArray(new String[0]));
		// Particles needing extra data (dust colour, block state) would throw when spawned without it.
		return particle != null && particle.getDataType() == Void.class ? particle : null;
	}

	private static Material material(String name, Material fallback)
	{
		Material material = name == null || name.isEmpty() ? null : Material.getMaterial(name.toUpperCase(Locale.ROOT));
		return material != null && material.isBlock() ? material : fallback;
	}

	private static String capitalise(String id)
	{
		return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public Style getStyle()
	{
		return style;
	}

	public int getCost()
	{
		return cost;
	}

	public int getDurationSeconds()
	{
		return durationSeconds;
	}

	public int getCooldownSeconds()
	{
		return cooldownSeconds;
	}

	public boolean requiresPower()
	{
		return requiresPower;
	}

	public DamageModel getDamageModel()
	{
		return damageModel;
	}

	/** Health taken from a mob per hit under {@link DamageModel#PER_TICK}. */
	public double mobDamageFor(double maxHealth)
	{
		return Math.max(mobDamage, maxHealth * mobDamagePercent);
	}

	public int getHitIntervalTicks()
	{
		return hitIntervalTicks;
	}

	public int getIgniteTicks()
	{
		return igniteTicks;
	}

	public boolean hurtsPlayers()
	{
		return hurtsPlayers && playerDamage > 0;
	}

	public double getPlayerDamage()
	{
		return playerDamage;
	}

	public double getKnockback()
	{
		return knockback;
	}

	public Particle getParticle()
	{
		return particle;
	}

	public Particle getSecondaryParticle()
	{
		return secondaryParticle;
	}

	public int getDensity()
	{
		return density;
	}

	public Material getArmBlock()
	{
		return armBlock;
	}

	public double getArmDegreesPerTick()
	{
		return armDegreesPerTick;
	}

	public double getArmHeight()
	{
		return armHeight;
	}

	public double getArmThickness()
	{
		return armThickness;
	}

	public double getArmHitWidth()
	{
		return armHitWidth;
	}

	public Material getSpikeBlock()
	{
		return spikeBlock;
	}

	public double getSpikeSpacing()
	{
		return spikeSpacing;
	}

	public int getSpikeCycleTicks()
	{
		return spikeCycleTicks;
	}

	public int getPointsPerKill()
	{
		return pointsPerKill;
	}

	public TrapSound getActivateSound()
	{
		return activateSound;
	}

	public TrapSound getLoopSound()
	{
		return loopSound;
	}

	public TrapSound getKillSound()
	{
		return killSound;
	}
}
