package com.theprogrammingturkey.comz.spawning.special;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;

import java.util.Locale;

/**
 * One entry of an arena's {@code special_enemies} setting: when a catalogue type appears and how many may be alive.
 * <ul>
 *   <li>{@code every_x_rounds}: value = X, counted from {@code first_round};</li>
 *   <li>{@code chance_per_round}: value = percent chance each round from {@code first_round};</li>
 *   <li>{@code replaces_dog_round}: value = percent chance a dog round becomes this type's round instead;</li>
 *   <li>{@code once_at_round}: value = the round.</li>
 * </ul>
 * Nothing fires before the round floor: the rule's {@code min_round} when set, else the catalogue type's
 * {@code min_round} (bosses default to the catalogue's {@code boss.min_round}).
 * {@code own_round} turns a triggered round into a dedicated round of only this type (replaced dog rounds always
 * are); otherwise the specials join the regular zombies without holding up the round.
 */
public class SpecialEnemyRule
{
	public enum Mode
	{
		EVERY_X_ROUNDS,
		CHANCE_PER_ROUND,
		REPLACES_DOG_ROUND,
		ONCE_AT_ROUND;

		public static Mode fromId(String id, Mode def)
		{
			try
			{
				return valueOf(id.trim().toUpperCase(Locale.ROOT));
			} catch(IllegalArgumentException | NullPointerException e)
			{
				return def;
			}
		}

		public String id()
		{
			return name().toLowerCase(Locale.ROOT);
		}

		public Mode next()
		{
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public String type;
	public Mode mode;
	public int value;
	public int maxAlive;
	public int firstRound;
	public boolean ownRound;
	/** Overrides the catalogue type's earliest round when positive. */
	public int minRound;

	public SpecialEnemyRule(String type, Mode mode, int value, int maxAlive, int firstRound, boolean ownRound)
	{
		this(type, mode, value, maxAlive, firstRound, ownRound, 0);
	}

	public SpecialEnemyRule(String type, Mode mode, int value, int maxAlive, int firstRound, boolean ownRound, int minRound)
	{
		this.minRound = Math.max(0, minRound);
		this.type = type;
		this.mode = mode;
		this.value = value;
		this.maxAlive = maxAlive;
		this.firstRound = firstRound;
		this.ownRound = ownRound;
	}

	public SpecialEnemyRule copy()
	{
		return new SpecialEnemyRule(type, mode, value, maxAlive, firstRound, ownRound, minRound);
	}

	public static SpecialEnemyRule fromJson(JsonObject json)
	{
		Mode mode = Mode.fromId(CustomConfig.getString(json, "mode", "chance_per_round"), Mode.CHANCE_PER_ROUND);
		return new SpecialEnemyRule(
				CustomConfig.getString(json, "type", "").toLowerCase(Locale.ROOT),
				mode,
				CustomConfig.getInt(json, "value", defaultValue(mode)),
				Math.max(1, CustomConfig.getInt(json, "max_alive", 1)),
				Math.max(1, CustomConfig.getInt(json, "first_round", 1)),
				CustomConfig.getBoolean(json, "own_round", false),
				CustomConfig.getInt(json, "min_round", 0));
	}

	public JsonObject toJson()
	{
		JsonObject json = new JsonObject();
		json.addProperty("type", type);
		json.addProperty("mode", mode.id());
		json.addProperty("value", value);
		json.addProperty("max_alive", maxAlive);
		json.addProperty("first_round", firstRound);
		json.addProperty("own_round", ownRound);
		if(minRound > 0)
			json.addProperty("min_round", minRound);
		return json;
	}

	public static int defaultValue(Mode mode)
	{
		switch(mode)
		{
			case EVERY_X_ROUNDS:
				return 5;
			case ONCE_AT_ROUND:
				return 10;
			case CHANCE_PER_ROUND:
				return 20;
			default:
				return 100;
		}
	}

	public int roundFloor(SpecialEnemyType type)
	{
		return minRound > 0 ? minRound : type != null ? type.minRound : 1;
	}

	/**
	 * Whether this rule fires on {@code wave} for a mode other than {@code replaces_dog_round}; {@code roll} is 0-99.
	 * A first round (or once-at round) below {@code floor} moves up to it.
	 */
	public boolean firesOn(int wave, int roll, int floor)
	{
		int first = Math.max(firstRound, floor);
		switch(mode)
		{
			case EVERY_X_ROUNDS:
				return value > 0 && wave >= first && (wave - first) % value == 0;
			case CHANCE_PER_ROUND:
				return wave >= first && roll < value;
			case ONCE_AT_ROUND:
				return wave == Math.max(value, floor);
			default:
				return false;
		}
	}

	/** Whether this rule takes over a dog round on {@code wave}; {@code roll} is 0-99. */
	public boolean replacesDogRound(int wave, int roll, int floor)
	{
		return mode == Mode.REPLACES_DOG_ROUND && wave >= Math.max(firstRound, floor) && roll < value;
	}

	public boolean isOwnRound()
	{
		return ownRound || mode == Mode.REPLACES_DOG_ROUND;
	}
}
