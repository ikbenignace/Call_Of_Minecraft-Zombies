package com.theprogrammingturkey.comz.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Small reusable particle effects for the BO2-fidelity visual feedback layer
 * (hit feedback, Mystery Box beam, perk/PaP bursts). All effects null-guard the
 * world so callers may pass a Location whose world has been unloaded without NPEing.
 */
public final class ParticleFX
{
	// Utility class: never instantiated.
	private ParticleFX()
	{
	}

	/**
	 * Dark-red blood puff at a zombie hit location: a tight cluster of small DUST particles.
	 */
	public static void blood(World world, Location loc)
	{
		if(world == null)
			return;
		// ~8 dark-red dust particles with a small (~0.2) spread and ~1.0 size.
		world.spawnParticle(Compat.DUST, loc, 8, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.0f));
	}

	/**
	 * Headshot sparkle: a small CRIT + ENCHANTED_HIT burst to read as a clean head kill.
	 */
	public static void headshot(World world, Location loc)
	{
		if(world == null)
			return;
		// ~10 crit + ~10 enchant sparkles in a tight cluster at the hit location.
		world.spawnParticle(Particle.CRIT, loc, 10, 0.2, 0.2, 0.2, 0);
		world.spawnParticle(Compat.ENCHANTED_HIT, loc, 10, 0.2, 0.2, 0.2, 0);
	}

	/**
	 * Vertical coloured beam from {@code base} up to {@code base + height}, capped with an END_ROD
	 * spark at the top. Used to make the Mystery Box / objective locations visible from afar.
	 */
	public static void beam(World world, Location base, double height, Color color)
	{
		if(world == null)
			return;
		Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
		// One dust particle every ~0.3 blocks up the column.
		for(double y = 0; y <= height; y += 0.3)
			world.spawnParticle(Compat.DUST, base.clone().add(0, y, 0), 1, 0, 0, 0, 0, dust);
		// A single END_ROD spark marking the top of the beam.
		world.spawnParticle(Particle.END_ROD, base.clone().add(0, height, 0), 1, 0, 0, 0, 0);
	}

	/** Rising column of end-rod light from {@code base} up to {@code base + height}, like a beacon beam. */
	public static void lightColumn(World world, Location base, double height)
	{
		if(world == null)
			return;
		for(double y = 0; y <= height; y += 0.5)
			world.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 0, 0, 1, 0, 0.12);
	}

	/**
	 * Coloured burst of {@code count} DUST particles at a location (perk-buy / Pack-a-Punch flourish).
	 */
	public static void burst(World world, Location loc, Color color, int count)
	{
		if(world == null)
			return;
		// 'count' dust particles with a moderate (~0.4) spread and a chunky (~1.5) size.
		world.spawnParticle(Compat.DUST, loc, count, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(color, 1.5f));
	}

	/** Fireball with a smoke ring and shrapnel dust, used for grenade and monkey bomb detonations. */
	public static void blast(World world, Location loc)
	{
		if(world == null)
			return;
		world.spawnParticle(Compat.EXPLOSION_EMITTER, loc, 1);
		world.spawnParticle(Compat.EXPLOSION, loc, 6, 1.0, 0.6, 1.0, 0);
		world.spawnParticle(Compat.SMOKE, loc, 30, 1.2, 0.8, 1.2, 0.05);
		world.spawnParticle(Particle.FLAME, loc, 20, 0.8, 0.5, 0.8, 0.08);
	}

	/** Dirt crumbs and dust where a zombie claws out of the ground. */
	public static void groundBreak(World world, Location loc)
	{
		if(world == null)
			return;
		if(Compat.BLOCK != null)
			world.spawnParticle(Compat.BLOCK, loc.clone().add(0, 0.1, 0), 25, 0.4, 0.1, 0.4, 0.1, Material.DIRT.createBlockData());
		world.spawnParticle(Compat.SMOKE, loc.clone().add(0, 0.2, 0), 6, 0.3, 0.1, 0.3, 0.02);
	}

	/** Crumbs of the block itself plus dust, when a bought door or debris block disappears. */
	public static void blockVanish(World world, Location loc, org.bukkit.block.data.BlockData data)
	{
		if(world == null)
			return;
		Location centre = loc.clone().add(0.5, 0.5, 0.5);
		if(Compat.BLOCK != null)
			world.spawnParticle(Compat.BLOCK, centre, 12, 0.3, 0.3, 0.3, 0.1, data);
		world.spawnParticle(Compat.SMOKE, centre, 3, 0.3, 0.3, 0.3, 0.01);
	}

	/** Quick bright flash around a player, used when a power-up is grabbed. */
	public static void grabFlash(World world, Location loc, Color color)
	{
		if(world == null)
			return;
		Location chest = loc.clone().add(0, 1, 0);
		world.spawnParticle(Compat.FIREWORK, chest, 25, 0.4, 0.6, 0.4, 0.08);
		world.spawnParticle(Compat.DUST, chest, 30, 0.6, 0.8, 0.6, 0, new Particle.DustOptions(color, 1.8f));
		world.spawnParticle(Particle.END_ROD, chest, 10, 0.3, 0.5, 0.3, 0.1);
	}
}
