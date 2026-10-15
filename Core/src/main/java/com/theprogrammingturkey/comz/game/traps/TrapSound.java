package com.theprogrammingturkey.comz.game.traps;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.SoundCategory;

/** A trap sound: a sounds.json key with the vanilla sound to use when the key is blank or needs the pack. */
public class TrapSound
{
	private final String key;
	private final String vanilla;
	private final float volume;
	private final float pitch;
	private final int everyTicks;

	private TrapSound(String key, String vanilla, float volume, float pitch, int everyTicks)
	{
		this.key = key;
		this.vanilla = vanilla;
		this.volume = volume;
		this.pitch = pitch;
		this.everyTicks = everyTicks;
	}

	static TrapSound parse(JsonObject sounds, String name)
	{
		if(!sounds.has(name) || !sounds.get(name).isJsonObject())
			return null;
		JsonObject json = sounds.getAsJsonObject(name);
		String key = CustomConfig.getString(json, "key", "");
		String vanilla = CustomConfig.getString(json, "vanilla", "");
		if(key.isEmpty() && vanilla.isEmpty())
			return null;
		return new TrapSound(key, vanilla, (float) CustomConfig.getDouble(json, "volume", 1), (float) CustomConfig.getDouble(json, "pitch", 1),
				Math.max(1, CustomConfig.getInt(json, "every_ticks", 20)));
	}

	public int getEveryTicks()
	{
		return everyTicks;
	}

	public void play(Location location)
	{
		if(location.getWorld() == null)
			return;
		if(key.isEmpty())
			SoundUtil.play(location.getWorld(), location, vanilla, SoundCategory.BLOCKS, volume, pitch);
		else
			SoundUtil.playKey(location.getWorld(), location, key, vanilla, SoundCategory.BLOCKS, volume, pitch);
	}
}
