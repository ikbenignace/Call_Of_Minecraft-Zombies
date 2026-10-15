package com.theprogrammingturkey.comz.spawning.special;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.spawning.RoundSpawner;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import org.bukkit.entity.Mob;

/** Spawner for a dedicated special round: every mob of the round is the given catalogue type. */
public class SpecialRoundSpawner extends RoundSpawner
{
	private final SpecialEnemyType type;

	public SpecialRoundSpawner(SpecialEnemyType type)
	{
		this.type = type;
	}

	public SpecialEnemyType getType()
	{
		return type;
	}

	@Override
	public Mob spawnEntity(Game game, SpawnPoint loc, int wave)
	{
		return game.specialEnemyManager.spawn(type, loc.getLocation(), wave, true);
	}
}
