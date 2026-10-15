package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.listeners.customEvents.PlayerPerkPurchaseEvent;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.util.ParticleFX;
import com.theprogrammingturkey.comz.util.SoundConfig;
import com.theprogrammingturkey.comz.util.SoundUtil;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Der Wunderfizz (Origins rules): one machine in the arena is lit at a time. Buying cycles perk bottles in
 * its window, then a random perk the player lacks lands and must be taken within a short window. After a
 * few uses a teddy bear can land instead: the points are refunded and the machine moves to another spot.
 */
public final class Wunderfizz
{
	private static final int TICK = 5;
	private static final float BOTTLE_SCALE = 0.45f;
	private static final float WINDOW_HEIGHT = 0.55f;
	private static final float WINDOW_DEPTH = 0.42f;
	private static final long MOVE_DELAY_TICKS = 60;
	private static final Particle.DustOptions ORB_DUST = new Particle.DustOptions(Color.fromRGB(170, 110, 255), 1.0f);

	private static final class Station
	{
		final Location base;
		final BlockFace facing;
		int uses;

		Station(Location base, BlockFace facing)
		{
			this.base = base;
			this.facing = facing;
		}

		Location window()
		{
			return base.clone().add(0.5 + facing.getModX() * WINDOW_DEPTH, WINDOW_HEIGHT, 0.5 + facing.getModZ() * WINDOW_DEPTH);
		}

		Location orb()
		{
			return base.clone().add(0.5, 1.75, 0.5);
		}
	}

	private static final class Session
	{
		final UUID owner;
		final int cost;
		final PerkType perk;
		final boolean teddy;
		Entity display;
		boolean ready;
		long ticksLeft;

		Session(Player owner, int cost, PerkType perk, boolean teddy)
		{
			this.owner = owner.getUniqueId();
			this.cost = cost;
			this.perk = perk;
			this.teddy = teddy;
		}
	}

	private static final class State
	{
		final List<Station> stations = new ArrayList<>();
		Station active;
		Session session;
		boolean moving;
		int task = -1;
	}

	private static final Map<Game, State> STATES = new HashMap<>();

	private Wunderfizz()
	{
	}

	private static boolean sameBlock(Location a, Location b)
	{
		return a.getWorld() != null && a.getWorld().equals(b.getWorld())
				&& a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
	}

	private static Station find(State state, Location base)
	{
		for(Station station : state.stations)
			if(sameBlock(station.base, base))
				return station;
		return null;
	}

	/** Called for every Wunderfizz machine as the arena's machines are built. The first one starts lit. */
	public static void registerMachine(Game game, Location base, BlockFace facing)
	{
		State state = STATES.computeIfAbsent(game, g -> new State());
		if(find(state, base) != null)
			return;
		state.stations.add(new Station(base.getBlock().getLocation(), facing));
		if(state.active == null || COMZombies.rand.nextInt(state.stations.size()) == 0)
			state.active = state.stations.get(state.stations.size() - 1);
		if(state.task == -1)
			state.task = COMZombies.scheduleTask(TICK, TICK, () -> tick(game, state));
	}

	public static void clearGame(Game game)
	{
		State state = STATES.remove(game);
		if(state == null)
			return;
		if(state.task != -1)
			Bukkit.getScheduler().cancelTask(state.task);
		endSession(state);
	}

	public static void onPlayerLeave(Game game, Player player)
	{
		State state = STATES.get(game);
		if(state != null && state.session != null && state.session.owner.equals(player.getUniqueId()))
			endSession(state);
	}

	public static String prompt(Game game, Player player, Location base, int cost)
	{
		State state = STATES.get(game);
		Station station = state == null ? null : find(state, base);
		if(station == null)
			return null;
		if(state.moving || station != state.active)
			return ChatColor.GRAY + "Der Wunderfizz is elsewhere";
		Session session = state.session;
		if(session != null)
		{
			if(!session.owner.equals(player.getUniqueId()))
				return ChatColor.GRAY + "Der Wunderfizz is in use";
			return session.ready ? ChatColor.GREEN + "[F] Take " + session.perk.displayName() : ChatColor.LIGHT_PURPLE + "Mixing...";
		}
		if(game.perkManager.getRandomPerk(player) == null)
			return ChatColor.GRAY + "Der Wunderfizz - no perks left";
		ChatColor color = PointManager.INSTANCE.canBuy(player, cost) ? ChatColor.GREEN : ChatColor.RED;
		return color + "[F] Der Wunderfizz " + ChatColor.YELLOW + "[" + cost + "]";
	}

	/**
	 * Buys a roll, or takes the landed perk. Returns false when the machine is not a registered station
	 * (pre-1.19.4 servers keep the sign), so the caller can fall back to an instant random perk.
	 */
	public static boolean interact(Game game, Player player, Location base, int cost)
	{
		State state = STATES.get(game);
		Station station = state == null ? null : find(state, base);
		if(station == null)
			return false;

		if(state.moving || station != state.active)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Der Wunderfizz is at another location.");
			return true;
		}

		Session session = state.session;
		if(session != null)
		{
			if(!session.owner.equals(player.getUniqueId()))
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Der Wunderfizz is in use!");
			else if(session.ready)
				take(game, state, player);
			return true;
		}

		PerkType perk = game.perkManager.getRandomPerk(player);
		if(perk == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You already have all the perks!");
			return true;
		}
		if(game.perkManager.getPlayersPerks(player).size() >= ConfigManager.getMainConfig().maxPerks)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You can only have " + ConfigManager.getMainConfig().maxPerks + " perks!");
			return true;
		}
		if(!PointManager.INSTANCE.canBuy(player, cost))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You do not have enough points to buy this!");
			return true;
		}

		PointManager.INSTANCE.takePoints(player, cost);
		PointManager.INSTANCE.notifyPlayer(player);
		station.uses++;

		Session started = new Session(player, cost, perk, rollTeddy(state, station));
		started.ticksLeft = ticks(ConfigManager.getMainConfig().wunderfizzRollSeconds);
		state.session = started;
		started.display = Displays.supportsItemDisplays() ? Displays.spawnItem(station.window(), bottle(perk), BOTTLE_SCALE, 0f, 0f, true) : null;
		SoundUtil.play(station.base.getWorld(), station.window(), SoundConfig.get("wunderfizz.roll", SoundConfig.get("box.open", "BLOCK_NOTE_BLOCK_CHIME")), SoundCategory.MASTER, 1, 1);
		return true;
	}

	private static boolean rollTeddy(State state, Station station)
	{
		return state.stations.size() > 1
				&& station.uses > ConfigManager.getMainConfig().wunderfizzTeddyMinUses
				&& COMZombies.rand.nextDouble() < ConfigManager.getMainConfig().wunderfizzTeddyChance;
	}

	private static void tick(Game game, State state)
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
		{
			endSession(state);
			return;
		}
		if(!state.moving && state.active != null && (!game.hasPower() || game.isPowered()))
			glow(state.active);

		Session session = state.session;
		if(session == null)
			return;
		Player owner = Bukkit.getPlayer(session.owner);
		if(owner == null || GameManager.INSTANCE.getGame(owner) != game || !game.getLivingPlayers().contains(owner))
		{
			endSession(state);
			return;
		}

		session.ticksLeft -= TICK;
		if(!session.ready)
		{
			cycle(session);
			if(session.ticksLeft <= 0)
				land(game, state, owner);
			return;
		}
		if(game.downedPlayerManager.isDownedPlayer(owner))
			return;
		if(session.ticksLeft <= 0)
		{
			CommandUtil.sendMessageToPlayer(owner, ChatColor.RED + "You waited too long - your " + session.perk.displayName() + " is gone!");
			endSession(state);
		}
	}

	private static void glow(Station station)
	{
		World world = station.base.getWorld();
		if(world == null)
			return;
		Location orb = station.orb();
		world.spawnParticle(Compat.DUST, orb, 3, 0.25, 0.25, 0.25, 0.0, ORB_DUST);
		if(COMZombies.rand.nextInt(6) == 0)
			world.spawnParticle(Particle.PORTAL, orb, 6, 0.2, 0.2, 0.2, 0.3);
	}

	private static void cycle(Session session)
	{
		if(session.display == null)
			return;
		PerkType shown = PerkType.getRandomPerk(new ArrayList<>());
		Displays.setItem(session.display, bottle(shown == null ? session.perk : shown));
	}

	private static void land(Game game, State state, Player owner)
	{
		Session session = state.session;
		Station station = state.active;
		World world = station.base.getWorld();
		if(session.teddy)
		{
			Displays.setItem(session.display, teddy());
			SoundUtil.play(world, station.window(), SoundConfig.get("wunderfizz.teddy", SoundConfig.get("box.teddy", "ENTITY_WITHER_SPAWN")), SoundCategory.MASTER, 1, 1);
			PointManager.INSTANCE.addPoints(owner, session.cost);
			PointManager.INSTANCE.notifyPlayer(owner);
			for(Player p : game.getPlayersInGame())
				CommandUtil.sendMessageToPlayer(p, ChatColor.DARK_PURPLE + "Der Wunderfizz is moving!");
			state.moving = true;
			COMZombies.scheduleTask(MOVE_DELAY_TICKS, () -> move(game, state));
			return;
		}

		session.ready = true;
		session.ticksLeft = ticks(ConfigManager.getMainConfig().wunderfizzPickupSeconds);
		Displays.setItem(session.display, bottle(session.perk));
		SoundUtil.play(world, station.window(), SoundConfig.get("wunderfizz.ready", SoundConfig.get("box.lock", "ENTITY_PLAYER_LEVELUP")), SoundCategory.MASTER, 1, 1);
		CommandUtil.sendMessageToPlayer(owner, ChatColor.LIGHT_PURPLE + "Der Wunderfizz poured you " + ChatColor.GOLD + session.perk.displayName() + ChatColor.LIGHT_PURPLE + " - take it!");
		if(session.display == null)
			take(game, state, owner);
	}

	private static void move(Game game, State state)
	{
		if(STATES.get(game) != state)
			return;
		endSession(state);
		Station from = state.active;
		List<Station> others = new ArrayList<>(state.stations);
		others.remove(from);
		if(from != null)
		{
			from.uses = 0;
			World world = from.base.getWorld();
			if(world != null)
				ParticleFX.burst(world, from.orb(), Color.PURPLE, 30);
			SoundUtil.play(world, from.orb(), SoundConfig.get("wunderfizz.leave", SoundConfig.get("box.leave", "ENTITY_ENDERMAN_TELEPORT")), SoundCategory.MASTER, 1, 1);
		}
		state.active = others.isEmpty() ? from : others.get(COMZombies.rand.nextInt(others.size()));
		state.moving = false;
		if(state.active != null && state.active.base.getWorld() != null)
		{
			World world = state.active.base.getWorld();
			world.strikeLightningEffect(state.active.orb());
			ParticleFX.burst(world, state.active.orb(), Color.PURPLE, 30);
		}
	}

	private static void take(Game game, State state, Player player)
	{
		Session session = state.session;
		endSession(state);
		PerkType perk = session.perk;
		if(game.perkManager.hasPerk(player, perk) || !game.perkManager.addPerk(player, perk))
			return;
		Bukkit.getPluginManager().callEvent(new PlayerPerkPurchaseEvent(player, perk));
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You now have " + perk.toString().toLowerCase() + "!");
		perk.initialEffect(player);
		perk.playDrinkAnimation(player);
		game.perkManager.startDrinking(player);
		game.perkManager.applyPerkEffect(player, perk);
	}

	private static void endSession(State state)
	{
		Session session = state.session;
		state.session = null;
		if(session != null && session.display != null && !session.display.isDead())
			session.display.remove();
	}

	private static ItemStack bottle(PerkType perk)
	{
		ItemStack stack = new ItemStack(Material.POTION);
		String key = perk.getBottleModelKey();
		if(key != null)
			PackModels.apply(stack, key);
		return stack;
	}

	private static ItemStack teddy()
	{
		ItemStack stack = new ItemStack(Material.TOTEM_OF_UNDYING);
		PackModels.apply(stack, "misc/box_teddy");
		return stack;
	}

	private static long ticks(double seconds)
	{
		return Math.max(1, Math.round(seconds * 20));
	}
}
