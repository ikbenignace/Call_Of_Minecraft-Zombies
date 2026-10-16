package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Plays a sound from a config-supplied string that may be EITHER a legacy Bukkit
 * {@link Sound} enum name (e.g. {@code BLOCK_LAVA_POP}) OR a raw namespaced sound
 * event key from a resource pack (e.g. {@code comz:weapon.ak74.shoot} or
 * {@code minecraft:custom.global.maxammo}).
 * <p>
 * Detection: a value containing {@code ':'} or {@code '.'} is treated as a raw event
 * key and played via the {@code String} {@code playSound} overload (which resolves
 * resource-pack events); anything else is resolved through {@link Compat#sound} as a
 * legacy enum. This keeps every existing enum-name config working unchanged while
 * letting the custom pack's events be wired purely from config.
 */
public final class SoundUtil
{
	private SoundUtil()
	{
	}

	private static boolean isEventKey(String value)
	{
		return value.indexOf(':') >= 0 || value.indexOf('.') >= 0;
	}

	/** Pack-only events (bare {@code custom.*} or a non-minecraft namespace) are skipped without the pack: nothing could hear them. */
	private static boolean blockedCustomEvent(String value)
	{
		return SoundConfig.isPackOnly(value) && !PackModels.isPackEnabled();
	}

	/**
	 * Play {@code value} at {@code loc} for everyone in {@code world}.
	 *
	 * @param value enum name or namespaced sound-event key; {@code null}/blank is a no-op
	 */
	public static void play(World world, Location loc, String value, SoundCategory category, float volume, float pitch)
	{
		if(world == null || value == null || value.isEmpty() || blockedCustomEvent(value))
			return;
		if(isEventKey(value))
		{
			world.playSound(loc, value.toLowerCase(Locale.ROOT), category, volume, pitch);
			return;
		}
		Sound sound = Compat.sound(value, null);
		if(sound != null)
			world.playSound(loc, sound, category, volume, pitch);
	}

	/**
	 * Play {@code value} for a single {@code player} only (client-side), at {@code loc}.
	 *
	 * @param value enum name or namespaced sound-event key; {@code null}/blank is a no-op
	 */
	public static void play(Player player, Location loc, String value, SoundCategory category, float volume, float pitch)
	{
		if(player == null || value == null || value.isEmpty() || blockedCustomEvent(value))
			return;
		if(isEventKey(value))
		{
			player.playSound(loc, value.toLowerCase(Locale.ROOT), category, volume, pitch);
			return;
		}
		Sound sound = Compat.sound(value, null);
		if(sound != null)
			player.playSound(loc, sound, category, volume, pitch);
	}

	/** Plays the sound registered under {@code key} in sounds.json, falling back to {@code vanilla} without the pack. */
	public static void playKey(World world, Location loc, String key, String vanilla, SoundCategory category, float volume, float pitch)
	{
		play(world, loc, SoundConfig.get(key, vanilla), category, volume, pitch);
	}

	/** Player-only variant of {@link #playKey(World, Location, String, String, SoundCategory, float, float)}. */
	public static void playKey(Player player, Location loc, String key, String vanilla, SoundCategory category, float volume, float pitch)
	{
		play(player, loc, SoundConfig.get(key, vanilla), category, volume, pitch);
	}
}
