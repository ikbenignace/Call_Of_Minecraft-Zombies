package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.Transport;
import com.theprogrammingturkey.comz.listeners.customEvents.PlayerTransportRideEvent;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.EntitySpawner;
import com.theprogrammingturkey.comz.util.SoundUtil;
import com.theprogrammingturkey.comz.util.Util;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-game store and runtime of {@link Transport}s.
 *
 * <p>Riders are steered by velocity, not teleported: every tick the player (or the cart they sit in) gets the
 * velocity that carries it to the next point on the path, plus a small pull back towards where it should be.
 * The client predicts its own movement from that velocity, so the ride looks smooth to the rider on every
 * server version from 1.14 up, while a per-tick teleport stutters at 20 Hz and an entity with a passenger
 * can't be teleported at all on Spigot. When a rider drifts too far (a wall, lag) they are snapped back with a
 * teleport. Zipline and platform riders get flight permission for the ride only so the server's anti-fly check
 * doesn't kick them; it is restored when the ride ends however it ends.
 */
public class TransportManager
{
	private static final double CALL_REACH = 2.0D;
	private static final double CALL_REACH_Y = 2.5D;
	private static final double CLICK_REACH = 1.6D;
	private static final double SNAP_DISTANCE = 2.5D;
	private static final double DRIFT_PULL = 0.25D;
	private static final int EASE_TICKS = 10;
	private static final int TELEPORT_DELAY_TICKS = 25;
	private static final int PLATFORM_RETURN_DELAY_TICKS = 60;
	private static final int ROPE_PARTICLE_PERIOD = 10;
	private static final double ELEVATOR_RADIUS = 3.0D;
	private static final float ROPE_THICKNESS = 0.07f;
	private static final float PLATFORM_THICKNESS = 0.25f;

	private final Game game;
	private final Map<String, Transport> transports = new LinkedHashMap<>();
	private final Map<String, State> states = new HashMap<>();
	private final Map<String, Long> groupLastUseMs = new HashMap<>();
	private final List<Ride> rides = new ArrayList<>();
	private final Map<UUID, Ride> rideByPlayer = new HashMap<>();
	private final List<Entity> ropes = new ArrayList<>();
	private int taskId = -1;
	private long tick;
	/** Set while this manager itself takes riders out of a cart, so the exit guard lets it through. */
	private boolean releasing;

	public TransportManager(Game game)
	{
		this.game = game;
	}

	private static final class State
	{
		long lastUseMs;
		boolean platformAtStart = true;
		boolean busy;
		long returnAtTick = -1;
		Entity platform;
	}

	private static final class Rider
	{
		final Player player;
		final Vector offset;
		final boolean savedAllowFlight;

		Rider(Player player, Vector offset)
		{
			this.player = player;
			this.offset = offset;
			this.savedAllowFlight = player.getAllowFlight();
		}
	}

	private static final class Ride
	{
		final Transport transport;
		final boolean reverse;
		final double length;
		final List<Rider> riders = new ArrayList<>();
		Entity cart;
		double travelled;
		int ticks;
		Vector lastPos;

		Ride(Transport transport, boolean reverse)
		{
			this.transport = transport;
			this.reverse = reverse;
			this.length = transport.length();
			this.lastPos = transport.pointAt(0, reverse);
		}
	}

	/** A call point a player stands at, resolved to the end of the path it belongs to. */
	public static final class CallTarget
	{
		public final Transport transport;
		public final Vector point;
		public final boolean atStart;
		public final double distanceSq;

		CallTarget(Transport transport, Vector point, boolean atStart, double distanceSq)
		{
			this.transport = transport;
			this.point = point;
			this.atStart = atStart;
			this.distanceSq = distanceSq;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Persistence and editing

	public void loadAllTransportsToGame(JsonArray json)
	{
		transports.clear();
		for(JsonElement elem : json)
		{
			if(!elem.isJsonObject())
				continue;
			Transport transport = Transport.load(elem.getAsJsonObject());
			transports.put(transport.getId(), transport);
		}
	}

	public JsonArray save()
	{
		JsonArray array = new JsonArray();
		for(Transport transport : transports.values())
			array.add(transport.save());
		return array;
	}

	public void addTransport(Transport transport)
	{
		transports.put(transport.getId(), transport);
		GameManager.INSTANCE.saveAllGames();
	}

	public void removeTransport(String id)
	{
		Transport transport = transports.remove(id.toLowerCase());
		if(transport == null)
			return;
		endRidesOf(transport);
		State state = states.remove(transport.getId());
		if(state != null && state.platform != null)
			state.platform.remove();
		GameManager.INSTANCE.saveAllGames();
	}

	public Transport getTransport(String id)
	{
		return id == null ? null : transports.get(id.toLowerCase());
	}

	public List<Transport> getTransports()
	{
		return new ArrayList<>(transports.values());
	}

	public String nextId()
	{
		return Util.nextFreeId("transport", transports.size() + 1, transports::containsKey);
	}

	/** The transport with a waypoint or call point within {@code reach} blocks of {@code point}, closest first. */
	public Transport findNear(Vector point, double reach)
	{
		Transport best = null;
		double bestDist = reach;
		for(Transport transport : transports.values())
		{
			double d = transport.distanceTo(point);
			if(d <= bestDist)
			{
				bestDist = d;
				best = transport;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------------------------------------
	// Lifecycle

	public void start()
	{
		stop();
		World world = game.getWorld();
		if(world == null)
			return;
		boolean anyUsable = false;
		for(Transport transport : transports.values())
		{
			if(!transport.isUsable())
				continue;
			anyUsable = true;
			State state = state(transport);
			if(transport.getStyle() == Transport.Style.ZIPLINE)
				spawnRope(world, transport);
			else if(transport.getStyle() == Transport.Style.PLATFORM)
				state.platform = spawnPlatform(world, transport, transport.start());
		}
		// Transports only change in build mode, which disables the arena, so the next game start picks them up.
		if(anyUsable)
			taskId = COMZombies.scheduleTask(1L, 1L, this::tick);
	}

	/** Drops every rider safely, despawns all transport entities and cancels the ride task. */
	public void stop()
	{
		if(taskId != -1)
		{
			Bukkit.getScheduler().cancelTask(taskId);
			taskId = -1;
		}
		for(Ride ride : new ArrayList<>(rides))
			abortRide(ride);
		rides.clear();
		rideByPlayer.clear();
		for(Entity rope : ropes)
			rope.remove();
		ropes.clear();
		for(State state : states.values())
			if(state.platform != null)
				state.platform.remove();
		states.clear();
		groupLastUseMs.clear();
		tick = 0;
	}

	public boolean isRiding(Player player)
	{
		return rideByPlayer.containsKey(player.getUniqueId());
	}

	/** True for a cart this manager spawned, so listeners can stop it being entered, left or broken. */
	public boolean isTransportCart(Entity entity)
	{
		for(Ride ride : rides)
			if(ride.cart != null && ride.cart.equals(entity))
				return true;
		return false;
	}

	public boolean isReleasing()
	{
		return releasing;
	}

	/** Ends {@code player}'s ride early, dropping them at the nearest waypoint. No-op when not riding. */
	public void dismount(Player player)
	{
		Ride ride = rideByPlayer.get(player.getUniqueId());
		if(ride == null)
			return;
		for(Iterator<Rider> it = ride.riders.iterator(); it.hasNext(); )
		{
			Rider rider = it.next();
			if(rider.player.equals(player))
			{
				it.remove();
				dropSafely(ride, rider);
			}
		}
		rideByPlayer.remove(player.getUniqueId());
	}

	private State state(Transport transport)
	{
		return states.computeIfAbsent(transport.getId(), k -> new State());
	}

	// ---------------------------------------------------------------------------------------------
	// Call points and prompts

	public List<CallTarget> findCallTargets(Player player)
	{
		List<CallTarget> targets = new ArrayList<>();
		if(isRiding(player))
			return targets;
		Location loc = player.getLocation();
		for(Transport transport : transports.values())
		{
			if(!transport.isUsable())
				continue;
			for(Vector point : transport.effectiveCallPoints())
			{
				double dx = loc.getX() - point.getX();
				double dz = loc.getZ() - point.getZ();
				double dSq = dx * dx + dz * dz;
				if(dSq <= CALL_REACH * CALL_REACH && Math.abs(loc.getY() - point.getY()) <= CALL_REACH_Y)
					targets.add(new CallTarget(transport, point, transport.nearerStart(point), dSq));
			}
		}
		return targets;
	}

	/** The call point whose spot is the clicked block (or the block it stands on), for right-click use. */
	public CallTarget findClickedCallPoint(Location blockLoc)
	{
		Vector centre = blockLoc.toVector().add(new Vector(0.5, 0.5, 0.5));
		for(Transport transport : transports.values())
		{
			if(!transport.isUsable())
				continue;
			for(Vector point : transport.effectiveCallPoints())
				if(point.distance(centre) <= CLICK_REACH)
					return new CallTarget(transport, point, transport.nearerStart(point), 0);
		}
		return null;
	}

	private int secondsUntilReady(Transport transport)
	{
		long lastUse = state(transport).lastUseMs;
		if(!transport.getCooldownGroup().isEmpty())
			lastUse = Math.max(lastUse, groupLastUseMs.getOrDefault(transport.getCooldownGroup(), 0L));
		long remaining = lastUse + transport.getCooldownSeconds() * 1000L - System.currentTimeMillis();
		return remaining <= 0 ? 0 : (int) ((remaining + 999L) / 1000L);
	}

	/** True when using this call point would bring a waiting platform over instead of riding it. */
	private boolean isCall(CallTarget target)
	{
		return target.transport.getStyle() == Transport.Style.PLATFORM && state(target.transport).platformAtStart != target.atStart;
	}

	private boolean wrongWay(CallTarget target)
	{
		return !target.transport.isTwoWay() && !target.atStart && !isCall(target);
	}

	private static String rideVerb(Transport transport)
	{
		return transport.getStyle() == Transport.Style.TELEPORT ? "Use " : "Ride ";
	}

	public String prompt(Player player, CallTarget target)
	{
		Transport transport = target.transport;
		String name = transport.displayName();
		State state = state(transport);
		if(game.isPowerLocked(transport.requiresPower()))
			return ChatColor.RED + "[F] " + name + ChatColor.GRAY + " - power required";
		if(isLocked(transport))
			return ChatColor.GRAY + name + " - locked";
		if(state.busy)
			return ChatColor.GRAY + name + " is moving";
		int cooldown = secondsUntilReady(transport);
		if(cooldown > 0)
			return ChatColor.GRAY + name + " - on cooldown " + cooldown + "s";
		if(wrongWay(target))
			return ChatColor.GRAY + name + " - one way";
		if(isCall(target))
			return ChatColor.GREEN + "[F] Call " + name;
		if(transport.getCost() <= 0)
			return ChatColor.GREEN + "[F] " + rideVerb(transport) + name;
		ChatColor color = PointManager.INSTANCE.canBuy(player, transport.getCost()) ? ChatColor.GREEN : ChatColor.RED;
		return color + "[F] " + rideVerb(transport) + name + " " + ChatColor.YELLOW + "[" + transport.getCost() + "]";
	}

	// ---------------------------------------------------------------------------------------------
	// Using a transport

	/** Rides or calls the transport from the call point. Callers debounce the press. */
	public void use(Player player, CallTarget target)
	{
		Transport transport = target.transport;
		if(!transports.containsKey(transport.getId()) || !transport.isUsable() || !canRide(player) || isRiding(player))
			return;
		State state = state(transport);
		if(game.isPowerLocked(transport.requiresPower()))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must turn on the power first!");
			PerkType.noPower(player);
			return;
		}
		if(isLocked(transport))
		{
			deny(player, transport.displayName() + " is locked.");
			return;
		}
		if(state.busy)
		{
			deny(player, transport.displayName() + " is already moving.");
			return;
		}
		int cooldown = secondsUntilReady(transport);
		if(cooldown > 0)
		{
			deny(player, transport.displayName() + " is on cooldown (" + cooldown + "s).");
			return;
		}
		if(wrongWay(target))
		{
			deny(player, transport.displayName() + " only runs one way.");
			return;
		}

		if(isCall(target))
		{
			markUsed(transport, state);
			SoundUtil.playKey(player.getWorld(), player.getLocation(), "transport.call", "BLOCK_NOTE_BLOCK_PLING", SoundCategory.MASTER, 1f, 1.2f);
			sendPlatform(transport, target.atStart);
			return;
		}

		int cost = transport.getCost();
		if(cost > 0)
		{
			if(!PointManager.INSTANCE.canBuy(player, cost))
			{
				deny(player, "You don't have enough points!");
				return;
			}
			PointManager.INSTANCE.takePoints(player, cost);
			PointManager.INSTANCE.notifyPlayer(player);
		}
		markUsed(transport, state);
		boolean reverse = !target.atStart;

		switch(transport.getStyle())
		{
			case PLATFORM:
				departPlatform(transport, reverse, player);
				break;
			case TELEPORT:
				departElevator(transport, reverse, player);
				break;
			case CART:
				departCart(transport, reverse, player);
				break;
			default:
				departZipline(transport, reverse, player);
		}
	}

	private void markUsed(Transport transport, State state)
	{
		state.lastUseMs = System.currentTimeMillis();
		if(!transport.getCooldownGroup().isEmpty())
			groupLastUseMs.put(transport.getCooldownGroup(), state.lastUseMs);
	}

	/** A trigger with an {@code enable_teleporter} target naming the transport (id or name) keeps it shut until it fires. */
	private boolean isLocked(Transport transport)
	{
		return game.triggerManager.isTeleporterLocked(transport.getId())
				|| (!transport.getName().isEmpty() && game.triggerManager.isTeleporterLocked(transport.getName()));
	}

	private void deny(Player player, String message)
	{
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + message);
		SoundUtil.playKey(player, player.getLocation(), "transport.deny", "ENTITY_VILLAGER_NO", SoundCategory.MASTER, 1f, 1f);
	}

	private boolean canRide(Player player)
	{
		return player != null && player.isOnline() && game.getStatus() == Game.GameStatus.INGAME
				&& game.getLivingPlayers().contains(player)
				&& !game.downedPlayerManager.isDownedPlayer(player) && !game.downedPlayerManager.isGhost(player);
	}

	private void departZipline(Transport transport, boolean reverse, Player player)
	{
		Ride ride = new Ride(transport, reverse);
		addRider(ride, player, new Vector(), true);
		player.teleport(at(player, ride.lastPos));
		begin(ride, "zipline", "ENTITY_LEASH_KNOT_PLACE");
	}

	private void departCart(Transport transport, boolean reverse, Player player)
	{
		Ride ride = new Ride(transport, reverse);
		Location spawnAt = at(player, ride.lastPos);
		Vector heading = transport.pointAt(1.0, reverse).subtract(ride.lastPos);
		if(heading.lengthSquared() > 0)
			spawnAt.setDirection(heading);
		player.teleport(spawnAt);
		ride.cart = EntitySpawner.spawn(spawnAt.getWorld(), spawnAt, Minecart.class, cart ->
		{
			cart.setPersistent(false);
			cart.setInvulnerable(true);
			cart.setGravity(false);
			cart.setSlowWhenEmpty(false);
			cart.setMaxSpeed(64D);
			cart.setDerailedVelocityMod(new Vector(1, 1, 1));
			cart.setFlyingVelocityMod(new Vector(1, 1, 1));
		});
		addRider(ride, player, new Vector(), false);
		ride.cart.addPassenger(player);
		begin(ride, "cart", "ENTITY_MINECART_RIDING");
	}

	private void departPlatform(Transport transport, boolean reverse, Player buyer)
	{
		Ride ride = new Ride(transport, reverse);
		Vector dock = ride.lastPos;
		double half = transport.getPlatformSize() / 2.0D + 0.3D;
		for(Player player : game.getLivingPlayers())
		{
			if(!canRide(player) || isRiding(player) || !player.getWorld().equals(game.getWorld()))
				continue;
			Vector offset = player.getLocation().toVector().subtract(dock);
			if(Math.abs(offset.getX()) <= half && Math.abs(offset.getZ()) <= half && offset.getY() >= -0.6 && offset.getY() <= 1.2)
				addRider(ride, player, offset.setY(0), true);
		}
		if(ride.riders.isEmpty())
			CommandUtil.sendMessageToPlayer(buyer, ChatColor.YELLOW + "Nobody was standing on the " + transport.displayName() + ".");
		state(transport).busy = true;
		begin(ride, "platform", "BLOCK_PISTON_EXTEND");
	}

	/** Moves the platform to the requested end with nobody on it (a call or the automatic return of a one-way lander). */
	private void sendPlatform(Transport transport, boolean reverse)
	{
		State state = state(transport);
		state.busy = true;
		state.returnAtTick = -1;
		rides.add(new Ride(transport, reverse));
	}

	private void departElevator(Transport transport, boolean reverse, Player buyer)
	{
		Ride ride = new Ride(transport, reverse);
		Vector from = ride.lastPos;
		addRider(ride, buyer, new Vector(), false);
		for(Player player : game.getLivingPlayers())
		{
			if(player.equals(buyer) || !canRide(player) || isRiding(player) || !player.getWorld().equals(game.getWorld()))
				continue;
			if(player.getLocation().toVector().distanceSquared(from) <= ELEVATOR_RADIUS * ELEVATOR_RADIUS)
				addRider(ride, player, new Vector(), false);
		}
		for(Rider rider : ride.riders)
		{
			rider.player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, TELEPORT_DELAY_TICKS + 25, 1, false, false));
			rider.player.sendTitle("", ChatColor.GRAY + transport.displayName() + "...", 5, TELEPORT_DELAY_TICKS, 10);
		}
		begin(ride, "teleport", "BLOCK_BEACON_ACTIVATE");
	}

	private void addRider(Ride ride, Player player, Vector offset, boolean grantFlight)
	{
		Rider rider = new Rider(player, offset);
		ride.riders.add(rider);
		rideByPlayer.put(player.getUniqueId(), ride);
		if(grantFlight)
		{
			player.setAllowFlight(true);
			player.setFlying(false);
		}
		player.setFallDistance(0f);
	}

	private void begin(Ride ride, String styleKey, String vanillaSound)
	{
		rides.add(ride);
		Location where = Transport.toLocation(game.getWorld(), ride.lastPos);
		SoundUtil.playKey(game.getWorld(), where, "transport." + styleKey + ".depart", vanillaSound, SoundCategory.MASTER, 1f, 1f);
	}

	// ---------------------------------------------------------------------------------------------
	// Ticking

	private void tick()
	{
		tick++;
		if(game.getStatus() != Game.GameStatus.INGAME)
		{
			stop();
			return;
		}
		for(Ride ride : new ArrayList<>(rides))
			tickRide(ride);
		for(Transport transport : transports.values())
		{
			State state = states.get(transport.getId());
			if(state == null)
				continue;
			if(state.returnAtTick >= 0 && tick >= state.returnAtTick && !state.busy)
				sendPlatform(transport, true);
			if(tick % ROPE_PARTICLE_PERIOD == 0)
				drawIdle(transport, state);
		}
	}

	private void tickRide(Ride ride)
	{
		ride.ticks++;
		for(Iterator<Rider> it = ride.riders.iterator(); it.hasNext(); )
		{
			Rider rider = it.next();
			if(!canRide(rider.player) || (ride.cart != null && !ride.cart.getPassengers().contains(rider.player)))
			{
				it.remove();
				rideByPlayer.remove(rider.player.getUniqueId());
				dropSafely(ride, rider);
			}
		}

		Transport transport = ride.transport;
		if(transport.getStyle() == Transport.Style.TELEPORT)
		{
			if(ride.riders.isEmpty())
				finish(ride, false);
			else if(ride.ticks >= TELEPORT_DELAY_TICKS)
				finish(ride, true);
			return;
		}
		if(ride.riders.isEmpty() && transport.getStyle() != Transport.Style.PLATFORM)
		{
			finish(ride, false);
			return;
		}

		double perTick = transport.getSpeed() / 20.0D;
		double remaining = ride.length - ride.travelled;
		double ease = Math.min(1.0D, ride.ticks / (double) EASE_TICKS);
		double brake = Math.max(0.3D, Math.min(1.0D, remaining / (perTick * EASE_TICKS)));
		ride.travelled = Math.min(ride.length, ride.travelled + perTick * Math.min(ease, brake));
		Vector pos = transport.pointAt(ride.travelled, ride.reverse);
		Vector step = pos.clone().subtract(ride.lastPos);

		if(ride.cart != null)
			steer(ride.cart, ride.lastPos, step);
		else
			for(Rider rider : ride.riders)
				steerPlayer(rider.player, ride.lastPos.clone().add(rider.offset), step);

		if(transport.getStyle() == Transport.Style.PLATFORM)
			movePlatform(transport, pos);
		else if(transport.getStyle() == Transport.Style.ZIPLINE && ride.ticks % 6 == 0)
			SoundUtil.playKey(game.getWorld(), Transport.toLocation(game.getWorld(), pos), "transport.zipline.loop", "BLOCK_CHAIN_STEP", SoundCategory.MASTER, 0.4f, 1.4f);

		ride.lastPos = pos;
		if(ride.travelled >= ride.length)
			finish(ride, true);
	}

	private void steerPlayer(Player player, Vector expected, Vector step)
	{
		Vector actual = player.getLocation().toVector();
		Vector drift = expected.clone().subtract(actual);
		player.setFallDistance(0f);
		if(drift.length() > SNAP_DISTANCE)
		{
			player.teleport(at(player, expected.clone().add(step)));
			player.setVelocity(new Vector());
			return;
		}
		player.setVelocity(step.clone().add(drift.multiply(DRIFT_PULL)));
	}

	private void steer(Entity cart, Vector expected, Vector step)
	{
		Vector drift = expected.clone().subtract(cart.getLocation().toVector());
		if(drift.length() > SNAP_DISTANCE)
		{
			// Spigot refuses to teleport an entity that has passengers.
			List<Entity> passengers = new ArrayList<>(cart.getPassengers());
			eject(cart);
			Location target = cart.getLocation().clone();
			Vector to = expected.clone().add(step);
			target.setX(to.getX());
			target.setY(to.getY());
			target.setZ(to.getZ());
			cart.teleport(target);
			for(Entity passenger : passengers)
			{
				passenger.teleport(target);
				cart.addPassenger(passenger);
			}
			cart.setVelocity(new Vector());
			return;
		}
		cart.setVelocity(step.clone().add(drift.multiply(DRIFT_PULL)));
	}

	private void movePlatform(Transport transport, Vector pos)
	{
		State state = state(transport);
		Location loc = Transport.toLocation(game.getWorld(), pos);
		if(state.platform != null && !state.platform.isDead())
			state.platform.teleport(loc);
		else
			outlinePlatform(transport, pos);
	}

	/** Completes a ride: places riders exactly at the far end and announces the arrival, or just cleans up. */
	private void finish(Ride ride, boolean arrived)
	{
		rides.remove(ride);
		Transport transport = ride.transport;
		Vector end = transport.pointAt(ride.length, ride.reverse);
		World world = game.getWorld();
		Entity cart = ride.cart;
		if(cart != null)
			eject(cart);

		for(Rider rider : ride.riders)
		{
			rideByPlayer.remove(rider.player.getUniqueId());
			Player player = rider.player;
			if(arrived)
			{
				Vector spot = end.clone().add(rider.offset);
				if(transport.getStyle() == Transport.Style.TELEPORT && ride.riders.size() > 1)
					spot.add(new Vector((Math.random() - 0.5) * 1.2, 0, (Math.random() - 0.5) * 1.2));
				player.teleport(at(player, spot));
				restore(player, rider);
				if(transport.getStyle() == Transport.Style.TELEPORT)
					player.removePotionEffect(PotionEffectType.BLINDNESS);
				Bukkit.getPluginManager().callEvent(new PlayerTransportRideEvent(game, player, transport, !ride.reverse));
			}
			else
				dropSafely(ride, rider);
		}
		if(cart != null)
			cart.remove();

		if(arrived && !ride.riders.isEmpty() && transport.isRelockAfterUse())
			game.triggerManager.relockTeleporter(transport.getId(), transport.getName());

		if(arrived && world != null)
			SoundUtil.playKey(world, Transport.toLocation(world, end), "transport." + transport.getStyle().key() + ".arrive", "BLOCK_NOTE_BLOCK_BELL", SoundCategory.MASTER, 1f, 1f);

		if(transport.getStyle() == Transport.Style.PLATFORM)
		{
			State state = state(transport);
			state.busy = false;
			state.platformAtStart = ride.reverse;
			movePlatform(transport, end);
			if(!transport.isTwoWay() && !state.platformAtStart)
				state.returnAtTick = tick + PLATFORM_RETURN_DELAY_TICKS;
		}
	}

	private void abortRide(Ride ride)
	{
		rides.remove(ride);
		for(Rider rider : ride.riders)
		{
			rideByPlayer.remove(rider.player.getUniqueId());
			dropSafely(ride, rider);
		}
		ride.riders.clear();
		if(ride.cart != null)
		{
			eject(ride.cart);
			ride.cart.remove();
		}
		State state = states.get(ride.transport.getId());
		if(state != null)
			state.busy = false;
	}

	private void endRidesOf(Transport transport)
	{
		for(Ride ride : new ArrayList<>(rides))
			if(ride.transport == transport)
				abortRide(ride);
	}

	/** Puts a rider whose ride was cut short on the nearest waypoint, standing still, with their flight state back. */
	private void dropSafely(Ride ride, Rider rider)
	{
		Player player = rider.player;
		if(ride.cart != null && ride.cart.getPassengers().contains(player))
		{
			releasing = true;
			try
			{
				ride.cart.removePassenger(player);
			} finally
			{
				releasing = false;
			}
		}
		restore(player, rider);
		// Elevator riders never left the start pad, so there is nowhere to put them back.
		if(ride.transport.getStyle() == Transport.Style.TELEPORT)
		{
			player.removePotionEffect(PotionEffectType.BLINDNESS);
			return;
		}
		if(!player.isOnline() || !player.getWorld().equals(game.getWorld()))
			return;
		Vector safe = ride.transport.nearestWaypoint(player.getLocation().toVector());
		player.teleport(at(player, safe));
	}

	private void eject(Entity cart)
	{
		releasing = true;
		try
		{
			cart.eject();
		} finally
		{
			releasing = false;
		}
	}

	private static void restore(Player player, Rider rider)
	{
		player.setVelocity(new Vector());
		player.setFallDistance(0f);
		if(player.getAllowFlight() != rider.savedAllowFlight)
		{
			player.setFlying(false);
			player.setAllowFlight(rider.savedAllowFlight);
		}
	}

	private Location at(Player player, Vector v)
	{
		Location loc = Transport.toLocation(game.getWorld(), v);
		loc.setYaw(player.getLocation().getYaw());
		loc.setPitch(player.getLocation().getPitch());
		return loc;
	}

	// ---------------------------------------------------------------------------------------------
	// Visuals

	private static BlockData ropeBlock()
	{
		return Compat.materialOr(Material.BLACK_WOOL, "IRON_CHAIN", "CHAIN").createBlockData();
	}

	private void spawnRope(World world, Transport transport)
	{
		if(!Displays.supportsBlockDisplays())
			return;
		List<Vector> points = transport.getWaypoints();
		BlockData block = ropeBlock();
		for(int i = 1; i < points.size(); i++)
		{
			Vector from = points.get(i - 1).clone().add(new Vector(0, Transport.ROPE_HEIGHT, 0));
			Vector delta = points.get(i).clone().subtract(points.get(i - 1));
			Entity line = Displays.spawnLine(Transport.toLocation(world, from), delta.getX(), delta.getY(), delta.getZ(), block, ROPE_THICKNESS);
			if(line != null)
				ropes.add(line);
		}
	}

	private Entity spawnPlatform(World world, Transport transport, Vector at)
	{
		if(!Displays.supportsBlockDisplays())
			return null;
		float size = transport.getPlatformSize();
		Entity platform = Displays.spawnBlock(Transport.toLocation(world, at), Material.SMOOTH_STONE.createBlockData(),
				-size / 2f, -PLATFORM_THICKNESS + 0.02f, -size / 2f, size, PLATFORM_THICKNESS, size, 0f, false);
		// 1.20.2+ interpolates teleports client-side; older display servers simply step every tick.
		Compat.invokeIfPresent(platform, "setTeleportDuration", new Class<?>[]{int.class}, 2);
		return platform;
	}

	/** Without display entities the rope and the platform are drawn with particles. */
	private void drawIdle(Transport transport, State state)
	{
		World world = game.getWorld();
		if(world == null || Compat.DUST == null)
			return;
		if(transport.getStyle() == Transport.Style.ZIPLINE && ropes.isEmpty() && !Displays.supportsBlockDisplays())
		{
			Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(70, 60, 50), 0.7f);
			List<Vector> points = transport.getWaypoints();
			for(int i = 1; i < points.size(); i++)
			{
				Vector a = points.get(i - 1);
				Vector b = points.get(i);
				double len = a.distance(b);
				for(double d = 0; d <= len; d += 0.5)
				{
					Vector p = a.clone().add(b.clone().subtract(a).multiply(len <= 0 ? 0 : d / len)).add(new Vector(0, Transport.ROPE_HEIGHT, 0));
					world.spawnParticle(Compat.DUST, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0, dust);
				}
			}
		}
		else if(transport.getStyle() == Transport.Style.PLATFORM && state.platform == null && !state.busy)
			outlinePlatform(transport, state.platformAtStart ? transport.start() : transport.end());
	}

	private void outlinePlatform(Transport transport, Vector centre)
	{
		World world = game.getWorld();
		if(world == null || Compat.DUST == null)
			return;
		Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(200, 200, 200), 0.8f);
		double half = transport.getPlatformSize() / 2.0D;
		for(double d = -half; d <= half; d += 0.5)
		{
			world.spawnParticle(Compat.DUST, centre.getX() + d, centre.getY() + 0.05, centre.getZ() - half, 1, 0, 0, 0, 0, dust);
			world.spawnParticle(Compat.DUST, centre.getX() + d, centre.getY() + 0.05, centre.getZ() + half, 1, 0, 0, 0, 0, dust);
			world.spawnParticle(Compat.DUST, centre.getX() - half, centre.getY() + 0.05, centre.getZ() + d, 1, 0, 0, 0, 0, dust);
			world.spawnParticle(Compat.DUST, centre.getX() + half, centre.getY() + 0.05, centre.getZ() + d, 1, 0, 0, 0, 0, dust);
		}
	}
}
