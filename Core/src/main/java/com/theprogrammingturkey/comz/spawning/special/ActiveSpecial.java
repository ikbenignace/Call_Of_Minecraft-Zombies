package com.theprogrammingturkey.comz.spawning.special;

import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.weapons.WeaponInstance;
import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Live state of one spawned special enemy. */
class ActiveSpecial
{
	final Mob mob;
	final SpecialEnemyType type;
	final boolean blocksRound;
	final long spawnedAt = System.currentTimeMillis();

	/** True while the behaviour drives movement itself (heading to a machine, fleeing). */
	boolean steering;
	Location goal;
	long phaseStartedAt;
	long lastActionAt;
	long nextAmbientAt;
	/** Set before a removal the behaviour caused itself (escape, explosion, timeout) so no death effects run. */
	boolean quietRemoval;
	boolean exploded;
	boolean escaped;
	BossBar bossBar;
	double maxHealth;

	final Set<Location> visitedMachines = new HashSet<>();
	final List<StolenPerk> stolenPerks = new ArrayList<>();
	Player weaponOwner;
	WeaponInstance stolenWeapon;
	int stolenSlot = -1;

	ActiveSpecial(Mob mob, SpecialEnemyType type, boolean blocksRound)
	{
		this.mob = mob;
		this.type = type;
		this.blocksRound = blocksRound;
		this.phaseStartedAt = spawnedAt;
	}

	double secondsInPhase()
	{
		return (System.currentTimeMillis() - phaseStartedAt) / 1000.0;
	}

	double secondsAlive()
	{
		return (System.currentTimeMillis() - spawnedAt) / 1000.0;
	}

	void newPhase()
	{
		phaseStartedAt = System.currentTimeMillis();
	}

	static final class StolenPerk
	{
		final Player player;
		final PerkType perk;

		StolenPerk(Player player, PerkType perk)
		{
			this.player = player;
			this.perk = perk;
		}
	}
}
