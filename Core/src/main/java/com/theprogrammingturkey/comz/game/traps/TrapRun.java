package com.theprogrammingturkey.comz.game.traps;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One activation of a trap: drives the type's visuals and sounds, and hits zombies (and players, when the
 * type hurts them) inside the area until the duration runs out or the game ends.
 */
public class TrapRun
{
	private static final int STEP_TICKS = 2;
	private static final int MAX_SPIKES = 64;
	private static final Particle LAVA = Compat.particle("LAVA");
	/** Share of max health an insta-kill trap takes per hit from specials immune to insta-kills (bosses). */
	private static final double IMMUNE_HIT_PERCENT = 0.1;

	private final Game game;
	private final TrapType type;
	private final TrapArea area;
	/** Players are hurt at most once a second, so walking out of a running trap is always possible. */
	private static final int PLAYER_HIT_INTERVAL_TICKS = 20;

	private final Player activator;
	private final int durationTicks;
	private final Runnable onFinish;

	private final Map<UUID, Integer> lastHit;
	private final List<Entity> displays = new ArrayList<>();
	private final List<double[]> spikePositions = new ArrayList<>();

	private int taskId = -1;
	private int ticks = 0;
	private boolean running = false;

	private Entity arm;
	private double armYaw = 0;
	private double armLength;
	private double armY;

	public TrapRun(Game game, TrapType type, TrapArea area, Player activator, int durationSeconds, Map<UUID, Integer> lastHit, Runnable onFinish)
	{
		this.lastHit = lastHit;
		this.game = game;
		this.type = type;
		this.area = area;
		this.activator = activator;
		this.durationTicks = Math.max(STEP_TICKS, durationSeconds * 20);
		this.onFinish = onFinish;
	}

	public void start()
	{
		if(area.getWorld() == null)
		{
			onFinish.run();
			return;
		}
		running = true;
		if(type.getActivateSound() != null)
			type.getActivateSound().play(area.centre());
		setupVisuals();
		taskId = COMZombies.scheduleTask(0L, STEP_TICKS, this::step);
		if(taskId == -1)
			finish();
	}

	public boolean isRunning()
	{
		return running;
	}

	/** Stops without running the finish callback; used when the game ends or the trap is reset. */
	public void cancel()
	{
		running = false;
		cleanup();
	}

	private void finish()
	{
		if(!running)
			return;
		running = false;
		cleanup();
		onFinish.run();
	}

	private void cleanup()
	{
		if(taskId != -1)
		{
			Bukkit.getScheduler().cancelTask(taskId);
			taskId = -1;
		}
		for(Entity display : displays)
			if(display != null && !display.isDead())
				display.remove();
		displays.clear();
		arm = null;
	}

	private void step()
	{
		if(!running)
			return;
		if(game.getStatus() != Game.GameStatus.INGAME)
		{
			cancel();
			return;
		}
		ticks += STEP_TICKS;
		if(ticks > durationTicks)
		{
			finish();
			return;
		}

		drawVisuals();
		TrapSound loop = type.getLoopSound();
		if(loop != null && type.getStyle() != TrapType.Style.TURRET && ticks % loop.getEveryTicks() < STEP_TICKS)
			loop.play(area.centre().add(0, 1, 0));
		hitEntities();
	}

	// ---------------------------------------------------------------------------------------------
	// Damage

	private void hitEntities()
	{
		if(type.getStyle() == TrapType.Style.SPIKES && !spikesJustRose())
			return;
		if(type.getStyle() == TrapType.Style.TURRET)
		{
			shootNearest();
			return;
		}

		for(Mob mob : new ArrayList<>(game.spawnManager.getEntities()))
		{
			if(mob == null || mob.isDead() || !inStrikeZone(mob.getLocation()) || !readyToHit(mob))
				continue;
			hitMob(mob);
		}

		if(!type.hurtsPlayers())
			return;
		for(Player player : new ArrayList<>(game.getPlayersInGame()))
		{
			if(player.isDead() || game.downedPlayerManager.isDownedPlayer(player) || !inStrikeZone(player.getLocation()) || !readyToHit(player, PLAYER_HIT_INTERVAL_TICKS))
				continue;
			// Only a swinging arm throws players; any other push, repeated every hit, pins them against
			// the walls of a narrow trap so they can never walk out of it.
			if(type.getStyle() == TrapType.Style.ARM)
				knockback(player);
			// Routed through EntityDamageEvent so the game's own player damage rules (downing, god mode) apply.
			player.damage(type.getPlayerDamage());
		}
	}

	/** A turret fires one round at a time, at the closest zombie in range it isn't still reloading on. */
	private void shootNearest()
	{
		Location muzzle = muzzle();
		Mob target = null;
		double best = Double.MAX_VALUE;
		for(Mob mob : game.spawnManager.getEntities())
		{
			if(mob == null || mob.isDead() || !area.contains(mob.getLocation()))
				continue;
			Integer last = lastHit.get(mob.getUniqueId());
			if(last != null && ticks - last < type.getHitIntervalTicks())
				continue;
			double distance = mob.getLocation().distanceSquared(muzzle);
			if(distance < best)
			{
				best = distance;
				target = mob;
			}
		}
		if(target == null)
			return;
		lastHit.put(target.getUniqueId(), ticks);
		Location hit = target.getLocation().add(0, target.getHeight() * 0.6, 0);
		Vector line = hit.toVector().subtract(muzzle.toVector());
		double length = line.length();
		for(double d = 0; d < length; d += 0.5)
			spawn(type.getParticle(), muzzle.clone().add(line.clone().multiply(d / length)), 1, 0, 0, 0, 0);
		spawn(type.getSecondaryParticle(), muzzle, 2, 0.05, 0.05, 0.05, 0.01);
		if(type.getLoopSound() != null)
			type.getLoopSound().play(muzzle);
		hitMob(target);
	}

	private Location muzzle()
	{
		return area.centre().add(0, 2.5, 0);
	}

	private boolean readyToHit(Entity entity)
	{
		return readyToHit(entity, 0);
	}

	private boolean readyToHit(Entity entity, int minInterval)
	{
		Integer last = lastHit.get(entity.getUniqueId());
		if(last != null && ticks - last < Math.max(minInterval, type.getHitIntervalTicks()))
			return false;
		lastHit.put(entity.getUniqueId(), ticks);
		return true;
	}

	private boolean inStrikeZone(Location location)
	{
		if(!area.contains(location))
			return false;
		if(type.getStyle() != TrapType.Style.ARM)
			return true;
		double dx = location.getX() - area.centreX();
		double dz = location.getZ() - area.centreZ();
		double yaw = Math.toRadians(armYaw);
		double ax = Math.cos(yaw), az = -Math.sin(yaw);
		double along = dx * ax + dz * az;
		if(Math.abs(along) > armLength + 0.5)
			return false;
		double across = Math.abs(dx * az - dz * ax);
		return across <= type.getArmHitWidth();
	}

	private void hitMob(Mob mob)
	{
		if(type.getIgniteTicks() > 0)
			mob.setFireTicks(type.getIgniteTicks());

		boolean instantKill = type.getDamageModel() == TrapType.DamageModel.INSTANT_KILL;
		boolean immune = instantKill && game.specialEnemyManager.isInstaKillImmune(mob);
		if(instantKill && !immune)
		{
			kill(mob);
			return;
		}

		double maxHealth = maxHealth(mob);
		double damage = immune ? Math.max(type.mobDamageFor(maxHealth), maxHealth * IMMUNE_HIT_PERCENT) : type.mobDamageFor(maxHealth);
		double health = mob.getHealth() - damage;
		if(health < 1)
		{
			kill(mob);
			return;
		}
		mob.setHealth(health);
		mob.playEffect(EntityEffect.HURT);
		knockback(mob);
	}

	private static double maxHealth(LivingEntity entity)
	{
		AttributeInstance attribute = Compat.MAX_HEALTH == null ? null : entity.getAttribute(Compat.MAX_HEALTH);
		return attribute != null ? attribute.getValue() : entity.getHealth();
	}

	private void kill(Mob mob)
	{
		Location at = mob.getLocation().add(0, 1, 0);
		spawn(type.getParticle(), at, 12, 0.3, 0.6, 0.3, 0.05);
		if(type.getKillSound() != null)
			type.getKillSound().play(at);
		game.killMobByTrap(mob, activator, type.getPointsPerKill());
	}

	private void knockback(Entity entity)
	{
		double strength = type.getKnockback();
		if(strength <= 0)
			return;
		Vector push;
		if(type.getStyle() == TrapType.Style.ARM)
		{
			// Tangent of the sweep, on the side of the pivot the entity is on.
			double yaw = Math.toRadians(armYaw);
			double dx = entity.getLocation().getX() - area.centreX();
			double dz = entity.getLocation().getZ() - area.centreZ();
			double side = Math.signum(dx * Math.cos(yaw) - dz * Math.sin(yaw));
			push = new Vector(-Math.sin(yaw) * side, 0, -Math.cos(yaw) * side);
		}
		else
		{
			push = new Vector(entity.getLocation().getX() - area.centreX(), 0, entity.getLocation().getZ() - area.centreZ());
		}
		if(push.lengthSquared() < 1.0E-4)
			push = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
		push.normalize().multiply(strength).setY(Math.min(0.6, 0.25 + strength * 0.2));
		entity.setVelocity(push);
	}

	// ---------------------------------------------------------------------------------------------
	// Visuals

	private void setupVisuals()
	{
		switch(type.getStyle())
		{
			case ARM:
				armLength = Math.max(1.0, Math.min(area.sizeX(), area.sizeZ()) / 2 - 0.25);
				armY = area.floorY() + type.getArmHeight();
				if(Displays.supportsBlockDisplays())
				{
					float t = (float) type.getArmThickness();
					arm = Displays.spawnBlock(new Location(area.getWorld(), area.centreX(), armY, area.centreZ()), blockData(type.getArmBlock()),
							(float) -armLength, -t / 2, -t / 2, (float) (armLength * 2), t, t, 0f, false);
					if(arm != null)
						displays.add(arm);
				}
				break;
			case SPIKES:
				double spacing = type.getSpikeSpacing();
				for(double x = area.minX() + spacing / 2; x < area.maxX() && spikePositions.size() < MAX_SPIKES; x += spacing)
					for(double z = area.minZ() + spacing / 2; z < area.maxZ() && spikePositions.size() < MAX_SPIKES; z += spacing)
						spikePositions.add(new double[]{x, z});
				if(Displays.supportsBlockDisplays())
				{
					BlockData spike = blockData(type.getSpikeBlock());
					for(double[] pos : spikePositions)
					{
						Entity display = Displays.spawnBlock(new Location(area.getWorld(), pos[0], area.floorY(), pos[1]), spike,
								-0.3f, 0f, -0.3f, 0.6f, 0.01f, 0.6f, 0f, false);
						if(display != null)
							displays.add(display);
					}
				}
				break;
			default:
		}
	}

	private static BlockData blockData(Material material)
	{
		try
		{
			return Bukkit.createBlockData(material);
		} catch(IllegalArgumentException e)
		{
			return Bukkit.createBlockData(Material.OAK_LOG);
		}
	}

	private void drawVisuals()
	{
		switch(type.getStyle())
		{
			case ELECTRIC:
				drawElectric();
				break;
			case FIRE:
				drawFire();
				break;
			case ARM:
				drawArm();
				break;
			case SPIKES:
				drawSpikes();
				break;
			case TURRET:
				if(ticks % 10 < STEP_TICKS)
					spawn(type.getSecondaryParticle(), muzzle(), 1, 0.1, 0.1, 0.1, 0);
				break;
			default:
				drawGeneric();
		}
	}

	private void drawElectric()
	{
		int arcs = Math.max(1, type.getDensity() / 3);
		for(int i = 0; i < arcs; i++)
		{
			boolean alongX = area.sizeX() >= area.sizeZ();
			double y1 = area.floorY() + 0.2 + Math.random() * 2;
			double y2 = area.floorY() + 0.2 + Math.random() * 2;
			Location from = alongX
					? new Location(area.getWorld(), area.minX(), y1, area.minZ() + Math.random() * area.sizeZ())
					: new Location(area.getWorld(), area.minX() + Math.random() * area.sizeX(), y1, area.minZ());
			Location to = alongX
					? new Location(area.getWorld(), area.maxX(), y2, area.minZ() + Math.random() * area.sizeZ())
					: new Location(area.getWorld(), area.minX() + Math.random() * area.sizeX(), y2, area.maxZ());
			drawArc(from, to);
		}
		if(type.getSecondaryParticle() != null)
			for(int i = 0; i < type.getDensity(); i++)
				spawn(type.getSecondaryParticle(), area.randomPoint(Math.random() * 2), 1, 0, 0, 0, 0.02);
	}

	/** A jagged bolt: a straight line with a random walk sideways and up/down. */
	private void drawArc(Location from, Location to)
	{
		Vector line = to.toVector().subtract(from.toVector());
		double length = line.length();
		if(length < 0.1)
			return;
		int points = (int) Math.min(120, length * 3);
		Vector step = line.clone().multiply(1.0 / points);
		Vector side = new Vector(-line.getZ(), 0, line.getX()).normalize();
		double drift = 0, lift = 0;
		for(int i = 0; i <= points; i++)
		{
			drift = clamp(drift + (Math.random() - 0.5) * 0.35, -0.6, 0.6);
			lift = clamp(lift + (Math.random() - 0.5) * 0.35, -0.6, 0.6);
			Location point = from.clone().add(step.clone().multiply(i)).add(side.clone().multiply(drift)).add(0, lift, 0);
			spawn(type.getParticle(), point, 1, 0, 0, 0, 0);
		}
	}

	private void drawFire()
	{
		int count = type.getDensity();
		for(int i = 0; i < count; i++)
			spawn(type.getParticle(), area.randomPoint(0.1 + Math.random() * 0.4), 1, 0.1, 0.3, 0.1, 0.03);
		for(int i = 0; i < Math.max(1, count / 4); i++)
			spawn(type.getSecondaryParticle(), area.randomPoint(2.5), 1, 0, 0, 0, 0);
		if(ticks % 10 < STEP_TICKS)
			spawn(LAVA, area.randomPoint(0.2), 1, 0, 0, 0, 0);
	}

	private void drawArm()
	{
		armYaw = (armYaw + type.getArmDegreesPerTick() * STEP_TICKS) % 360;
		double yaw = Math.toRadians(armYaw);
		double ax = Math.cos(yaw), az = -Math.sin(yaw);
		if(arm != null)
		{
			float t = (float) type.getArmThickness();
			Displays.animateBlock(arm, (float) -armLength, -t / 2, -t / 2, (float) (armLength * 2), t, t, (float) armYaw, STEP_TICKS);
		}
		else
		{
			for(double d = -armLength; d <= armLength; d += 0.4)
				spawn(type.getParticle(), new Location(area.getWorld(), area.centreX() + ax * d, armY, area.centreZ() + az * d), 1, 0, 0, 0, 0);
		}
		for(int tip = -1; tip <= 1; tip += 2)
			spawn(type.getSecondaryParticle(), new Location(area.getWorld(), area.centreX() + ax * armLength * tip, armY, area.centreZ() + az * armLength * tip),
					type.getDensity(), 0.1, 0.1, 0.1, 0.01);
	}

	private boolean spikesJustRose()
	{
		return ticks % type.getSpikeCycleTicks() < STEP_TICKS;
	}

	private void drawSpikes()
	{
		int cycle = type.getSpikeCycleTicks();
		boolean rise = spikesJustRose();
		boolean sink = (ticks + cycle / 2) % cycle < STEP_TICKS;
		if(!rise && !sink)
			return;
		if(!displays.isEmpty())
		{
			for(Entity display : displays)
				Displays.animateBlock(display, -0.3f, 0f, -0.3f, 0.6f, rise ? 1.1f : 0.01f, 0.6f, 0f, rise ? 2 : 6);
		}
		if(!rise)
			return;
		for(double[] pos : spikePositions)
		{
			Location base = new Location(area.getWorld(), pos[0], area.floorY(), pos[1]);
			if(displays.isEmpty())
				for(double y = 0.1; y <= 1.2; y += 0.3)
					spawn(type.getParticle(), base.clone().add(0, y, 0), 1, 0.05, 0, 0.05, 0);
			spawn(type.getSecondaryParticle(), base, Math.max(1, type.getDensity() / 2), 0.2, 0.05, 0.2, 0.01);
		}
	}

	private void drawGeneric()
	{
		if(ticks % 10 >= STEP_TICKS)
			return;
		Location centre = area.centre().add(0, 1, 0);
		spawn(type.getParticle(), centre, type.getDensity(), area.sizeX() / 4, 0.5, area.sizeZ() / 4, 0.01);
	}

	private void spawn(Particle particle, Location location, int count, double ox, double oy, double oz, double speed)
	{
		World world = area.getWorld();
		if(particle == null || world == null || count <= 0)
			return;
		world.spawnParticle(particle, location, count, ox, oy, oz, speed);
	}

	private static double clamp(double value, double min, double max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
