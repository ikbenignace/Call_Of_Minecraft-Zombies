package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version-tolerant lookups for Bukkit constants that were renamed or turned from enums into interfaces
 * between 1.14 and 26.x.
 * <p>
 * Core is compiled against 1.14, so referencing a renamed constant directly fails on newer servers
 * (NoSuchFieldError) and calling an enum method such as {@code Sound#name()} fails once that type became
 * an interface (IncompatibleClassChangeError). Every lookup here goes through reflection on the static
 * field, which works whatever the declaring type is, and tries the candidate names newest-first.
 */
public final class Compat
{
	// Resolved once: these are used on hot paths (per-shot particles, per-spawn attributes).
	public static final Particle BLOCK = particle("BLOCK", "BLOCK_CRACK");
	public static final Particle DUST = particle("DUST", "REDSTONE");
	public static final Particle ELECTRIC_SPARK = particle("ELECTRIC_SPARK", "CRIT");
	public static final Particle ENCHANTED_HIT = particle("ENCHANTED_HIT", "CRIT_MAGIC");
	public static final Particle EXPLOSION = particle("EXPLOSION", "EXPLOSION_LARGE");
	public static final Particle EXPLOSION_EMITTER = particle("EXPLOSION_EMITTER", "EXPLOSION_HUGE");
	public static final Particle FIREWORK = particle("FIREWORK", "FIREWORKS_SPARK");
	public static final Particle HAPPY_VILLAGER = particle("HAPPY_VILLAGER", "VILLAGER_HAPPY");
	public static final Particle ITEM_SLIME = particle("ITEM_SLIME", "SLIME");
	public static final Particle SMOKE = particle("SMOKE", "SMOKE_NORMAL");
	public static final Particle WITCH = particle("WITCH", "SPELL_WITCH");

	public static final PotionEffectType SLOWNESS = potionEffect("SLOWNESS", "SLOW");
	/** Null below 1.19. */
	public static final PotionEffectType DARKNESS = potionEffect("DARKNESS");

	public static final Attribute MAX_HEALTH = attribute("MAX_HEALTH", "GENERIC_MAX_HEALTH");
	public static final Attribute MOVEMENT_SPEED = attribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
	public static final Attribute FOLLOW_RANGE = attribute("FOLLOW_RANGE", "GENERIC_FOLLOW_RANGE");
	/** Null below 1.20.5. */
	public static final Attribute SCALE = attribute("SCALE", "GENERIC_SCALE");
	/** Null below 1.20.5 for non-horse mobs. */
	public static final Attribute JUMP_STRENGTH = attribute("JUMP_STRENGTH", "GENERIC_JUMP_STRENGTH");

	/** Shows as "∞" on 1.19.4+ clients; older servers have no infinite duration. */
	public static final int INFINITE_DURATION = intField(PotionEffect.class, "INFINITE_DURATION", Integer.MAX_VALUE);

	public static final EntityType FIREWORK_ROCKET = entityType("FIREWORK_ROCKET", "FIREWORK");
	/** Null below 1.16.2. */
	public static final EntityType PIGLIN_BRUTE = entityType("PIGLIN_BRUTE");

	// Sounds are resolved from config strings on every gunshot, so lookups are cached.
	private static final Map<String, Sound> SOUNDS = new ConcurrentHashMap<>();
	private static final Set<String> UNKNOWN_SOUNDS = ConcurrentHashMap.newKeySet();
	private static final Set<String> UNKNOWN_MATERIALS = ConcurrentHashMap.newKeySet();

	private Compat()
	{
	}

	private static int intField(Class<?> owner, String name, int fallback)
	{
		Object value = staticField(owner, name);
		return value instanceof Integer ? (Integer) value : fallback;
	}

	private static Object staticField(Class<?> owner, String... names)
	{
		for(String name : names)
		{
			try
			{
				Field field = owner.getField(name.toUpperCase(Locale.ROOT));
				return field.get(null);
			} catch(NoSuchFieldException | IllegalAccessException ignored)
			{
			}
		}
		return null;
	}

	/**
	 * Resolve a {@link Sound} by its constant name, falling back when the name is missing or no longer
	 * exists on this server version.
	 */
	public static Sound sound(String name, Sound fallback)
	{
		if(name == null || name.isEmpty())
			return fallback;
		String key = name.toUpperCase(Locale.ROOT);
		Sound cached = SOUNDS.get(key);
		if(cached != null)
			return cached;
		Object sound = staticField(Sound.class, key);
		if(sound instanceof Sound)
		{
			SOUNDS.put(key, (Sound) sound);
			return (Sound) sound;
		}
		// Many sounds don't exist on older servers; warn once per name instead of on every play.
		if(UNKNOWN_SOUNDS.add(key))
			COMZombies.getPlugin().getLogger().warning("Unknown sound '" + name + "' on this server version" + (fallback == null ? "; it won't play." : "; falling back to " + fallback + "."));
		return fallback;
	}

	/**
	 * Resolve a {@link Material} by name, or {@code null} if it is blank/unknown (callers
	 * already substitute a default material for {@code null}). Unknown names are logged.
	 */
	public static Material material(String name)
	{
		if(name == null || name.isEmpty())
			return null;
		Material material = Material.matchMaterial(name);
		if(material == null && UNKNOWN_MATERIALS.add(name.toUpperCase(Locale.ROOT)))
			COMZombies.getPlugin().getLogger().warning("Unknown material '" + name + "' in config; using the default for this weapon type.");
		return material;
	}

	/** First material that exists on this server, or {@code fallback}. */
	public static Material materialOr(Material fallback, String... names)
	{
		for(String name : names)
		{
			Material material = Material.getMaterial(name);
			if(material != null)
				return material;
		}
		return fallback;
	}

	/** Particle by candidate names (newest first), e.g. {@code particle("DUST", "REDSTONE")}. */
	public static Particle particle(String... names)
	{
		Object particle = staticField(Particle.class, names);
		return particle instanceof Particle ? (Particle) particle : null;
	}

	/** Potion effect by candidate names, or {@code null} when the effect doesn't exist on this server. */
	public static PotionEffectType potionEffect(String... names)
	{
		Object type = staticField(PotionEffectType.class, names);
		return type instanceof PotionEffectType ? (PotionEffectType) type : null;
	}

	/** Attribute by candidate names, e.g. {@code attribute("MAX_HEALTH", "GENERIC_MAX_HEALTH")}. */
	public static Attribute attribute(String... names)
	{
		Object attribute = staticField(Attribute.class, names);
		return attribute instanceof Attribute ? (Attribute) attribute : null;
	}

	public static EntityType entityType(String... names)
	{
		Object type = staticField(EntityType.class, names);
		return type instanceof EntityType ? (EntityType) type : null;
	}

	/**
	 * Constant name of a {@link Sound} without calling {@code Sound#name()}, which is an enum method
	 * before 1.21.3 and an interface method after.
	 */
	public static String soundName(Sound sound)
	{
		if(sound == null)
			return null;
		// Deliberately no Enum cast: compiled against the 1.14 enum, javac omits the checkcast and the
		// class then fails bytecode verification on servers where Sound is an interface.
		for(Field field : Sound.class.getFields())
		{
			try
			{
				if(Modifier.isStatic(field.getModifiers()) && field.get(null) == sound)
					return field.getName();
			} catch(IllegalAccessException ignored)
			{
			}
		}
		return null;
	}

	/** Invokes an optional no-arg/simple method if this server has it; returns false when it doesn't. */
	public static boolean invokeIfPresent(Object target, String method, Class<?>[] types, Object... args)
	{
		if(target == null)
			return false;
		try
		{
			Method m = target.getClass().getMethod(method, types);
			m.invoke(target, args);
			return true;
		} catch(ReflectiveOperationException | LinkageError e)
		{
			return false;
		}
	}

	public static void swingMainHand(Player player)
	{
		invokeIfPresent(player, "swingMainHand", new Class<?>[0]);
	}
}
