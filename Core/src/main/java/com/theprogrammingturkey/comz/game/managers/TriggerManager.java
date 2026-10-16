package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.game.features.Trigger;
import com.theprogrammingturkey.comz.game.features.TriggerStep;
import com.theprogrammingturkey.comz.game.features.TriggerTarget;
import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.util.ParticleFX;
import com.theprogrammingturkey.comz.util.SoundConfig;
import com.theprogrammingturkey.comz.util.SoundUtil;
import com.theprogrammingturkey.comz.util.Util;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs an arena's {@link Trigger triggers} during a game: listens to interactions, kills, rides, power and
 * rounds, tracks progress, gives feedback and runs the targets. Also owns the per-game unlock state the
 * targets flip (Pack-a-Punch gate, locked teleporters). Progress never outlives a game.
 */
public class TriggerManager
{
	private static final long TICK_PERIOD = 10L;
	private static final double INTERACT_REACH = 2.0D;
	private static final double INTERACT_REACH_Y = 2.5D;
	/** Songs are long; a big volume keeps them audible while players keep moving around the map. */
	private static final float SONG_VOLUME = 10f;
	/** Largest clone or fill a trigger may do in one go; Ascension's rocket moves about 30000 blocks a step. */
	private static final long MAX_EDIT_VOLUME = 65536L;
	/** Fluid blocks woken per tick after a block edit; waking a whole flooded room at once stalls the server. */
	private static final int FLUID_UPDATES_PER_TICK = 512;
	private static final BlockFace[] FLUID_NEIGHBOURS = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

	private final Game game;
	private final List<Trigger> triggers = new ArrayList<>();
	private final Map<TriggerStep, Entity> visuals = new IdentityHashMap<>();
	private final Set<String> unlockedTeleporters = new HashSet<>();
	private final Set<String> firedTriggers = new HashSet<>();
	/** Blocks changed by place_blocks targets, with the state they had before the first change. */
	private final Map<Location, BlockData> replacedBlocks = new LinkedHashMap<>();
	/** Edited blocks that were or became water/lava, waiting for their fluid physics. */
	private final Set<Location> pendingFluidUpdates = new LinkedHashSet<>();
	private int fluidTaskId = -1;
	private boolean packAPunchUnlocked;
	private boolean packAPunchDisabled;
	private int taskId = -1;
	/** Scheduled {@code after} targets per trigger, so a reset or the game end can call them off. */
	private final Map<Trigger, List<Integer>> pending = new IdentityHashMap<>();
	/** The player who completed a trigger's last step, for targets that reward that player. */
	private final Map<Trigger, Player> completedBy = new IdentityHashMap<>();
	/** Players holding an interact point (hack), with the task that watches them. */
	private final Map<UUID, Integer> holds = new HashMap<>();

	public TriggerManager(Game game)
	{
		this.game = game;
	}

	/** An interact step a player can currently use with F, and where it is. */
	public static final class InteractPoint
	{
		public final Trigger trigger;
		public final TriggerStep step;
		public final Location centre;
		public final double distanceSq;

		InteractPoint(Trigger trigger, TriggerStep step, Location centre, double distanceSq)
		{
			this.trigger = trigger;
			this.step = step;
			this.centre = centre;
			this.distanceSq = distanceSq;
		}
	}

	// ---------------------------------------------------------------- definitions

	public List<Trigger> getTriggers()
	{
		return Collections.unmodifiableList(triggers);
	}

	public Trigger getTrigger(String id)
	{
		for(Trigger trigger : triggers)
			if(trigger.getId().equalsIgnoreCase(id))
				return trigger;
		return null;
	}

	public void addTrigger(Trigger trigger)
	{
		triggers.add(trigger);
	}

	public void removeTrigger(Trigger trigger)
	{
		removeVisuals(trigger);
		triggers.remove(trigger);
	}

	public String nextId()
	{
		return Util.nextFreeId("trigger", triggers.size() + 1, id -> getTrigger(id) != null);
	}

	public void load(JsonArray json)
	{
		triggers.clear();
		for(JsonElement element : json)
			if(element.isJsonObject())
				triggers.add(Trigger.load(element.getAsJsonObject()));
	}

	/**
	 * Converts the old single {@code "quest"} object (INTERACT sign steps and REACH_ROUND steps, completed in order,
	 * rewarding points) into an ordered trigger, so arenas saved before triggers keep their easter egg. The next
	 * save writes it under {@code "triggers"}.
	 */
	public void loadLegacyQuest(JsonElement questElem)
	{
		if(questElem == null || !questElem.isJsonObject())
			return;
		JsonObject questJson = questElem.getAsJsonObject();
		String id = CustomConfig.getString(questJson, "id", "quest");
		if(getTrigger(id) != null || !questJson.has("steps") || !questJson.get("steps").isJsonArray())
			return;

		Trigger trigger = new Trigger(id);
		trigger.setName("Easter egg");
		trigger.setOrdered(true);
		trigger.setResetOnFail(false);
		for(JsonElement stepElem : questJson.getAsJsonArray("steps"))
		{
			if(!stepElem.isJsonObject())
				continue;
			JsonObject stepJson = stepElem.getAsJsonObject();
			String param = CustomConfig.getString(stepJson, "param", "");
			TriggerStep step;
			if(CustomConfig.getString(stepJson, "type", "INTERACT").trim().equalsIgnoreCase("REACH_ROUND"))
			{
				step = new TriggerStep(TriggerStep.Kind.ROUND_REACHED);
				step.setAmount(parseInt(param, 0));
			}
			else
			{
				step = new TriggerStep(TriggerStep.Kind.INTERACT);
				step.setSign(param);
			}
			step.setDescription(CustomConfig.getString(stepJson, "description", ""));
			trigger.getSteps().add(step);
		}
		if(trigger.getSteps().isEmpty())
			return;

		TriggerTarget points = new TriggerTarget(TriggerTarget.Kind.GIVE_POINTS);
		points.setAmount(ConfigManager.getMainConfig().questCompletionReward);
		trigger.getTargets().add(points);
		TriggerTarget message = new TriggerTarget(TriggerTarget.Kind.MESSAGE);
		message.setValue("&6&lEASTER EGG COMPLETE!");
		trigger.getTargets().add(message);
		triggers.add(trigger);
	}

	public JsonArray save()
	{
		JsonArray json = new JsonArray();
		for(Trigger trigger : triggers)
			json.add(trigger.save());
		return json;
	}

	// ---------------------------------------------------------------- lifecycle

	public void start()
	{
		stop();
		for(Trigger trigger : triggers)
			spawnVisuals(trigger);
		if(!triggers.isEmpty())
			taskId = COMZombies.scheduleTask(TICK_PERIOD, TICK_PERIOD, this::tick);
	}

	/** Ends the game's run: stops ticking, removes visuals and forgets all progress and unlocks. */
	public void stop()
	{
		if(taskId != -1)
		{
			Bukkit.getScheduler().cancelTask(taskId);
			taskId = -1;
		}
		for(Entity entity : visuals.values())
			if(entity != null && !entity.isDead())
				entity.remove();
		visuals.clear();
		for(Trigger trigger : triggers)
			trigger.resetAll();
		unlockedTeleporters.clear();
		firedTriggers.clear();
		for(List<Integer> tasks : pending.values())
			for(int task : tasks)
				Bukkit.getScheduler().cancelTask(task);
		pending.clear();
		completedBy.clear();
		for(int task : holds.values())
			Bukkit.getScheduler().cancelTask(task);
		holds.clear();
		restorePlacedBlocks();
		game.zoneManager.switchAllOn();
		packAPunchUnlocked = false;
		packAPunchDisabled = false;
	}

	// ---------------------------------------------------------------- gates

	public boolean isPackAPunchLocked()
	{
		return packAPunchDisabled || (game.isPackAPunchLockedByDefault() && !packAPunchUnlocked);
	}

	/** A teleporter is locked while some trigger still has to enable it this game. */
	public boolean isTeleporterLocked(String name)
	{
		if(name == null || unlockedTeleporters.contains(name.toLowerCase(Locale.ROOT)))
			return false;
		for(Trigger trigger : triggers)
			for(TriggerTarget target : trigger.getTargets())
				if(target.getKind() == TriggerTarget.Kind.ENABLE_TELEPORTER && target.getValue().equalsIgnoreCase(name))
					return true;
		return false;
	}

	/**
	 * Locks a transport or teleporter again after use: forgets the unlock and resets every trigger with an
	 * {@code enable_teleporter} target naming it, so it has to be linked again.
	 */
	public void relockTeleporter(String... names)
	{
		Set<String> keys = new HashSet<>();
		for(String name : names)
			if(name != null && !name.isEmpty())
				keys.add(name.toLowerCase(Locale.ROOT));
		unlockedTeleporters.removeAll(keys);
		for(Trigger trigger : triggers)
			for(TriggerTarget target : trigger.getTargets())
				if(target.getKind() == TriggerTarget.Kind.ENABLE_TELEPORTER && keys.contains(target.getValue().toLowerCase(Locale.ROOT)))
				{
					reset(trigger);
					trigger.resetAll();
					break;
				}
	}

	// ---------------------------------------------------------------- interact steps

	/** Interact steps within reach of {@code player} that the player could still press. */
	public List<InteractPoint> interactPoints(Player player)
	{
		List<InteractPoint> points = new ArrayList<>();
		World world = game.getWorld();
		if(world == null || !world.equals(player.getWorld()))
			return points;
		Location at = player.getLocation();
		for(Trigger trigger : triggers)
		{
			if(!trigger.isActive())
				continue;
			for(TriggerStep step : trigger.getSteps())
			{
				if(step.getKind() != TriggerStep.Kind.INTERACT || step.getPoint() == null || step.isDone())
					continue;
				if(trigger.isAllPlayers() && step.getContributors().contains(player.getUniqueId()))
					continue;
				TriggerStep next = trigger.isOrdered() ? trigger.nextStep() : null;
				if(next != null && next != step && (next.isStateStep() || next.getKind() == TriggerStep.Kind.ROUND_CHANCE))
					continue;
				Location centre = interactCentre(step, world);
				double dx = at.getX() - centre.getX();
				double dz = at.getZ() - centre.getZ();
				double distanceSq = dx * dx + dz * dz;
				if(distanceSq <= INTERACT_REACH * INTERACT_REACH && Math.abs(at.getY() - centre.getY()) <= INTERACT_REACH_Y)
					points.add(new InteractPoint(trigger, step, centre, distanceSq));
			}
		}
		return points;
	}

	private static Location interactCentre(TriggerStep step, World world)
	{
		Location block = step.pointLocation(world);
		boolean solid = block.getBlock().getType().isSolid();
		return block.add(0.5, solid ? 1.35 : 0.5, 0.5);
	}

	public String prompt(InteractPoint point, Player player)
	{
		String label = point.step.getDescription().isEmpty() ? "Interact" : ChatColor.translateAlternateColorCodes('&', point.step.getDescription());
		if(!point.step.getGear().isEmpty() && !ownsGear(player, point.step.getGear()))
			return ChatColor.RED + "[F] " + ChatColor.GRAY + label + " - needs the " + point.step.getGear();
		if(holds.containsKey(player.getUniqueId()))
			return null;
		if(point.step.getHoldSeconds() > 0)
			label += ChatColor.GRAY + " (stay " + point.step.getHoldSeconds() + "s)";
		int cost = point.step.getCost();
		if(cost <= 0)
			return ChatColor.GREEN + "[F] " + ChatColor.RESET + label;
		ChatColor color = PointManager.INSTANCE.canBuy(player, cost) ? ChatColor.GREEN : ChatColor.RED;
		return color + "[F] " + ChatColor.RESET + label + " " + ChatColor.YELLOW + "[" + cost + "]";
	}

	public void interact(Player player, InteractPoint point)
	{
		interactStep(player, point.trigger, point.step);
	}

	public boolean hasInteractAt(Block block)
	{
		for(Trigger trigger : triggers)
			for(TriggerStep step : trigger.getSteps())
				if(step.getKind() == TriggerStep.Kind.INTERACT && step.isAtBlock(block.getLocation()))
					return true;
		return false;
	}

	/** Right-click on a block that is an interact step. */
	public void interactAtBlock(Player player, Block block)
	{
		for(Trigger trigger : triggers)
			for(TriggerStep step : trigger.getSteps())
				if(step.getKind() == TriggerStep.Kind.INTERACT && step.isAtBlock(block.getLocation()) && trigger.isActive() && !step.isDone())
				{
					interactStep(player, trigger, step);
					return;
				}
	}

	/** Legacy {@code [Zombies] / Quest} signs complete the interact step carrying their id. */
	public void onSignInteract(String signId, Player player)
	{
		for(Trigger trigger : triggers)
			for(TriggerStep step : trigger.getSteps())
				if(step.getKind() == TriggerStep.Kind.INTERACT && step.getSign().equalsIgnoreCase(signId.trim()) && trigger.accepts(step))
				{
					interactStep(player, trigger, step);
					return;
				}
	}

	public boolean isSignStepReady(String signId)
	{
		for(Trigger trigger : triggers)
			for(TriggerStep step : trigger.getSteps())
				if(step.getKind() == TriggerStep.Kind.INTERACT && step.getSign().equalsIgnoreCase(signId.trim()) && trigger.accepts(step))
					return true;
		return false;
	}

	private void interactStep(Player player, Trigger trigger, TriggerStep step)
	{
		if(game.getStatus() != Game.GameStatus.INGAME || !trigger.isActive() || step.isDone())
			return;
		Location where = step.getPoint() == null ? player.getLocation() : interactCentre(step, player.getWorld());
		if(trigger.isAllPlayers() && step.getContributors().contains(player.getUniqueId()))
			return;
		if(!trigger.accepts(step))
		{
			wrongOrder(player, trigger, where);
			return;
		}
		if(!step.getGear().isEmpty() && !ownsGear(player, step.getGear()))
		{
			CommandUtil.actionBar(player, ChatColor.RED + "You need the " + step.getGear());
			SoundUtil.play(player, where, SoundConfig.get("trigger.denied", "ENTITY_VILLAGER_NO"), SoundCategory.MASTER, 1, 1);
			return;
		}
		if(step.getCost() > 0 && !PointManager.INSTANCE.canBuy(player, step.getCost()))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			SoundUtil.play(player, where, SoundConfig.get("trigger.denied", "ENTITY_VILLAGER_NO"), SoundCategory.MASTER, 1, 1);
			return;
		}
		if(step.getHoldSeconds() > 0)
		{
			startHold(player, trigger, step, where);
			return;
		}
		finishInteract(player, trigger, step, where);
	}

	/**
	 * A step that takes time (hacking): the player has to stay within reach while a bar fills up; walking away,
	 * going down or the step being taken by someone else cancels it.
	 */
	private void startHold(Player player, Trigger trigger, TriggerStep step, Location where)
	{
		if(holds.containsKey(player.getUniqueId()))
			return;
		final int totalTicks = step.getHoldSeconds() * 20;
		final int[] ticks = {0};
		SoundUtil.play(player.getWorld(), where, SoundConfig.get("trigger.hold_start", "BLOCK_BEACON_ACTIVATE"), SoundCategory.MASTER, 1, 1.6f);
		int task = COMZombies.scheduleTask(0L, 5L, () ->
		{
			ticks[0] += 5;
			boolean inReach = player.isOnline() && player.getWorld().equals(where.getWorld())
					&& player.getLocation().distanceSquared(where) <= (INTERACT_REACH + 1.5) * (INTERACT_REACH + 1.5);
			boolean stillOpen = game.getStatus() == Game.GameStatus.INGAME && trigger.isActive() && !step.isDone() && trigger.accepts(step)
					&& game.getLivingPlayers().contains(player)
					&& (trigger.getDeadlineMs() == 0 || System.currentTimeMillis() <= trigger.getDeadlineMs());
			if(!inReach || !stillOpen || game.downedPlayerManager.isDownedPlayer(player))
			{
				endHold(player);
				if(player.isOnline())
					CommandUtil.actionBar(player, ChatColor.RED + "Interrupted");
				return;
			}
			int filled = Math.min(20, ticks[0] * 20 / totalTicks);
			StringBuilder bar = new StringBuilder(ChatColor.GREEN.toString());
			for(int i = 0; i < 20; i++)
			{
				if(i == filled)
					bar.append(ChatColor.DARK_GRAY);
				bar.append('|');
			}
			CommandUtil.actionBar(player, bar.toString());
			if(ticks[0] % 20 == 0)
				SoundUtil.play(player.getWorld(), where, SoundConfig.get("trigger.hold_tick", "BLOCK_NOTE_BLOCK_HAT"), SoundCategory.MASTER, 0.6f, 1.4f);
			if(ticks[0] >= totalTicks)
			{
				endHold(player);
				if(step.getCost() > 0 && !PointManager.INSTANCE.canBuy(player, step.getCost()))
				{
					CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
					return;
				}
				finishInteract(player, trigger, step, where);
			}
		});
		if(task != -1)
			holds.put(player.getUniqueId(), task);
	}

	private void endHold(Player player)
	{
		Integer task = holds.remove(player.getUniqueId());
		if(task != null)
			Bukkit.getScheduler().cancelTask(task);
	}

	private boolean ownsGear(Player player, String gear)
	{
		com.theprogrammingturkey.comz.game.equipment.Equipment equipment = com.theprogrammingturkey.comz.game.equipment.EquipmentCatalogue.get(gear);
		return equipment != null && game.equipmentManager.owns(player, equipment);
	}

	private void finishInteract(Player player, Trigger trigger, TriggerStep step, Location where)
	{
		if(step.getCost() > 0)
		{
			if(!PointManager.INSTANCE.canBuy(player, step.getCost()))
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
				SoundUtil.play(player, where, SoundConfig.get("trigger.denied", "ENTITY_VILLAGER_NO"), SoundCategory.MASTER, 1, 1);
				return;
			}
			PointManager.INSTANCE.takePoints(player, step.getCost());
			PointManager.INSTANCE.notifyPlayer(player);
		}
		completedBy.put(trigger, player);
		if(contributeAll(trigger, step, player.getUniqueId(), where))
			complete(trigger, step, where);
	}

	/**
	 * With {@code all_players} a step needs every active player; records this one and returns true once all
	 * have done it. Without it any single player completes the step.
	 */
	private boolean contributeAll(Trigger trigger, TriggerStep step, UUID player, Location where)
	{
		if(!trigger.isAllPlayers())
			return true;
		step.getContributors().add(player);
		List<Player> active = activePlayers();
		int done = 0;
		for(Player p : active)
			if(step.getContributors().contains(p.getUniqueId()))
				done++;
		if(done >= active.size())
			return true;
		SoundUtil.play(where.getWorld(), where, SoundConfig.get("trigger.step", "BLOCK_NOTE_BLOCK_CHIME"), SoundCategory.MASTER, 1, 0.8f);
		actionBarAll(ChatColor.LIGHT_PURPLE + trigger.displayName() + ChatColor.GRAY + " - " + done + "/" + active.size() + " players");
		return false;
	}

	private void wrongOrder(Player player, Trigger trigger, Location where)
	{
		if(trigger.isResetOnFail() && trigger.hasProgress())
		{
			fail(trigger);
			return;
		}
		SoundUtil.play(player, where, SoundConfig.get("trigger.denied", "ENTITY_VILLAGER_NO"), SoundCategory.MASTER, 1, 1);
		CommandUtil.actionBar(player, ChatColor.GRAY + "Nothing happens..." + firstHint(trigger));
	}

	/** What an ordered trigger waits for, so a player who starts at the wrong end knows where to go. */
	private String firstHint(Trigger trigger)
	{
		TriggerStep next = trigger.isOrdered() ? trigger.nextStep() : null;
		if(next == null)
			return "";
		if(next.getKind() == TriggerStep.Kind.POWER_ON)
			return ChatColor.GRAY + " Turn on the power first.";
		if(next.getDescription().isEmpty())
			return "";
		return ChatColor.GRAY + " First: " + ChatColor.RESET + ChatColor.translateAlternateColorCodes('&', next.getDescription());
	}

	// ---------------------------------------------------------------- other events

	public void onMobKilled(Location location)
	{
		if(game.getStatus() != Game.GameStatus.INGAME || location == null)
			return;
		for(Trigger trigger : triggers)
			for(TriggerStep step : trigger.getSteps())
			{
				if(step.getKind() != TriggerStep.Kind.KILL_IN_ZONE || !trigger.accepts(step))
					continue;
				Zone zone = game.zoneManager.getZone(step.getZone());
				if(zone == null || !zone.contains(location))
					continue;
				int kills = step.addProgress();
				if(location.getWorld() != null)
					location.getWorld().spawnParticle(Compat.WITCH, location.clone().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0);
				if(kills >= Math.max(1, step.getAmount()))
					complete(trigger, step, location);
			}
	}

	public void onRide(Player player, String transportId)
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
			return;
		for(Trigger trigger : triggers)
			for(TriggerStep step : trigger.getSteps())
				if(step.getKind() == TriggerStep.Kind.RIDE_TRANSPORT && step.getTransport().equalsIgnoreCase(transportId) && trigger.accepts(step)
						&& !step.getContributors().contains(player.getUniqueId())
						&& contributeAll(trigger, step, player.getUniqueId(), player.getLocation()))
					complete(trigger, step, player.getLocation());
	}

	public void onPowerOn()
	{
		checkStateSteps();
	}

	public void onRoundReached()
	{
		if(game.getStatus() == Game.GameStatus.INGAME)
			for(Trigger trigger : triggers)
				for(TriggerStep step : trigger.getSteps())
					if(step.getKind() == TriggerStep.Kind.ROUND_CHANCE && trigger.accepts(step) && COMZombies.rand.nextInt(100) < step.getAmount())
						complete(trigger, step, null);
		checkStateSteps();
	}

	private void checkStateSteps()
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
			return;
		for(Trigger trigger : triggers)
			for(TriggerStep step : trigger.getSteps())
			{
				if(!trigger.accepts(step))
					continue;
				boolean met = (step.getKind() == TriggerStep.Kind.POWER_ON && game.isPowered())
						|| (step.getKind() == TriggerStep.Kind.ROUND_REACHED && game.getWave() >= step.getAmount())
						|| (step.getKind() == TriggerStep.Kind.TRIGGER_DONE && firedTriggers.contains(step.getTrigger().toLowerCase(Locale.ROOT)));
				if(met)
					complete(trigger, step, null);
			}
	}

	private void tick()
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
			return;
		long now = System.currentTimeMillis();
		for(Trigger trigger : triggers)
		{
			if(!trigger.isActive())
				continue;
			if(trigger.getDeadlineMs() > 0 && now > trigger.getDeadlineMs())
			{
				fail(trigger);
				continue;
			}
			for(TriggerStep step : trigger.getSteps())
			{
				if(step.getKind() != TriggerStep.Kind.STAND_ON || !trigger.isActive())
					continue;
				boolean occupied = isOccupied(step);
				if(trigger.isSimultaneous())
				{
					if(occupied && !step.isDone())
						complete(trigger, step, standLocation(step));
					else if(!occupied && step.isDone())
						step.setDone(false);
				}
				else if(occupied && trigger.accepts(step))
					complete(trigger, step, standLocation(step));
			}
		}
		checkStateSteps();
	}

	private boolean isOccupied(TriggerStep step)
	{
		Zone zone = step.usesZone() ? game.zoneManager.getZone(step.getZone()) : null;
		if(step.usesZone() ? zone == null : step.getPoint() == null)
			return false;
		List<Player> active = activePlayers();
		int on = 0;
		for(Player player : active)
		{
			Location at = player.getLocation();
			if(zone != null ? zone.contains(at) : step.isAtBlock(at) || step.isAtBlock(at.clone().subtract(0, 0.2, 0)))
				on++;
		}
		int required = step.getAmount() <= 0 ? active.size() : step.getAmount();
		return on > 0 && on >= required;
	}

	private Location standLocation(TriggerStep step)
	{
		World world = game.getWorld();
		if(step.usesZone())
		{
			Zone zone = game.zoneManager.getZone(step.getZone());
			if(zone == null)
				return null;
			Location min = zone.getMin();
			Location max = zone.getMax();
			return new Location(world, (min.getX() + max.getX() + 1) / 2, min.getY() + 0.5, (min.getZ() + max.getZ() + 1) / 2);
		}
		return step.getPoint() == null ? null : step.pointLocation(world).add(0.5, 1.0, 0.5);
	}

	/** Players who can take part: in the game, alive and not downed. */
	private List<Player> activePlayers()
	{
		List<Player> active = new ArrayList<>();
		for(Player player : game.getLivingPlayers())
			if(player.isOnline() && !game.downedPlayerManager.isDownedPlayer(player))
				active.add(player);
		return active;
	}

	// ---------------------------------------------------------------- progress

	private void complete(Trigger trigger, TriggerStep step, Location where)
	{
		step.setDone(true);
		removeVisual(step);
		if(trigger.getTimeLimitSeconds() > 0 && trigger.getDeadlineMs() == 0)
			trigger.setDeadlineMs(System.currentTimeMillis() + trigger.getTimeLimitSeconds() * 1000L);
		if(trigger.allDone())
		{
			fire(trigger);
			return;
		}
		if(trigger.isSilent())
			return;

		if(where != null && where.getWorld() != null)
		{
			SoundUtil.play(where.getWorld(), where, SoundConfig.get("trigger.step", "BLOCK_NOTE_BLOCK_CHIME"), SoundCategory.MASTER, 1.5f, 1.2f);
			ParticleFX.burst(where.getWorld(), where, Color.FUCHSIA, 20);
		}
		StringBuilder text = new StringBuilder();
		text.append(ChatColor.LIGHT_PURPLE).append(trigger.displayName()).append(' ')
				.append(ChatColor.WHITE).append(trigger.completedCount()).append('/').append(trigger.getSteps().size());
		if(trigger.getDeadlineMs() > 0)
			text.append(ChatColor.GRAY).append(" - ").append(Math.max(0, (trigger.getDeadlineMs() - System.currentTimeMillis() + 999) / 1000)).append("s left");
		TriggerStep next = trigger.isOrdered() ? trigger.nextStep() : null;
		if(next != null && !next.getDescription().isEmpty())
			text.append(ChatColor.GRAY).append(" - Next: ").append(ChatColor.RESET).append(ChatColor.translateAlternateColorCodes('&', next.getDescription()));
		actionBarAll(text.toString());
	}

	private void fail(Trigger trigger)
	{
		for(Player player : game.getPlayersAndSpectators())
			SoundUtil.play(player, player.getLocation(), SoundConfig.get("trigger.fail", "BLOCK_NOTE_BLOCK_BASS"), SoundCategory.MASTER, 1, 0.6f);
		actionBarAll(ChatColor.RED + trigger.displayName() + " failed");
		if(trigger.isResetOnFail())
		{
			trigger.resetProgress();
			spawnVisuals(trigger);
		}
		else
		{
			trigger.setFailed(true);
			trigger.setDeadlineMs(0);
			removeVisuals(trigger);
		}
	}

	private void fire(Trigger trigger)
	{
		trigger.setFired(true);
		trigger.setDeadlineMs(0);
		firedTriggers.add(trigger.getId().toLowerCase(Locale.ROOT));
		if(!trigger.isSilent())
		{
			for(Player player : game.getPlayersAndSpectators())
				SoundUtil.play(player, player.getLocation(), SoundConfig.get("trigger.complete", "UI_TOAST_CHALLENGE_COMPLETE"), SoundCategory.MASTER, 1, 1);
			actionBarAll(ChatColor.GOLD + "" + ChatColor.BOLD + trigger.displayName() + ChatColor.GOLD + " complete");
		}
		runAll(trigger, trigger.getTargets());
		if(trigger.isRepeatable() && game.getStatus() == Game.GameStatus.INGAME)
		{
			trigger.rearm();
			spawnVisuals(trigger);
		}
	}

	private void runAll(Trigger trigger, List<TriggerTarget> targets)
	{
		for(TriggerTarget target : targets)
		{
			try
			{
				run(trigger, target);
			} catch(RuntimeException e)
			{
				COMZombies.log.warning("Trigger " + trigger.getId() + " could not " + target.describe() + ": " + e);
			}
		}
	}

	private void run(Trigger trigger, TriggerTarget target)
	{
		World world = game.getWorld();
		switch(target.getKind())
		{
			case AFTER:
			{
				List<Integer> tasks = pending.computeIfAbsent(trigger, t -> new ArrayList<>());
				int[] id = {-1};
				id[0] = Bukkit.getScheduler().scheduleSyncDelayedTask(COMZombies.getPlugin(), () ->
				{
					tasks.remove(Integer.valueOf(id[0]));
					if(game.getStatus() == Game.GameStatus.INGAME)
						runAll(trigger, target.getTargets());
				}, Math.max(1, target.getAmount()));
				if(id[0] != -1)
					tasks.add(id[0]);
				break;
			}
			case CLONE_BLOCKS:
				cloneBlocks(world, target);
				break;
			case FILL_BLOCKS:
				fillBlocks(world, target);
				break;
			case PLAY_SOUND:
			{
				Location at = target.getPoint() != null ? target.pointLocation(world) : randomPlayerLocation();
				if(at != null)
					SoundUtil.play(world, at, SoundConfig.get(target.getValue(), target.getValue()), SoundCategory.MASTER, target.getVolume(), 1);
				break;
			}
			case RESET_TRIGGER:
			{
				// A tick later, so a trigger resetting itself is never reset while its own fire() is still running.
				Trigger other = getTrigger(target.getValue());
				if(other != null)
					COMZombies.scheduleTask(1L, () ->
					{
						if(game.getStatus() == Game.GameStatus.INGAME)
							reset(other);
					});
				break;
			}
			case SET_ZONE:
				game.zoneManager.setSwitchedOff(target.getValue(), target.getAmount() <= 0);
				break;
			case LOCK_TELEPORTER:
				unlockedTeleporters.remove(target.getValue().toLowerCase(Locale.ROOT));
				break;
			case DISABLE_PACK_A_PUNCH:
				packAPunchDisabled = true;
				break;
			case GIVE_POINTS_TO_PLAYER:
			{
				Player player = completedBy.get(trigger);
				if(player != null && player.isOnline() && game.getPlayersInGame().contains(player))
				{
					PointManager.INSTANCE.addPoints(player, target.getAmount());
					PointManager.INSTANCE.notifyPlayer(player);
				}
				break;
			}
			case OPEN_DOOR:
				openDoor(target.getValue());
				break;
			case ENABLE_PACK_A_PUNCH:
				packAPunchUnlocked = true;
				packAPunchDisabled = false;
				break;
			case ENABLE_TELEPORTER:
				unlockedTeleporters.add(target.getValue().toLowerCase(Locale.ROOT));
				break;
			case PLAY_SONG:
				playSong(target.getValue());
				break;
			case GIVE_POWERUP:
			{
				Location at = target.getPoint() != null ? target.pointLocation(world).add(0, 1, 0) : randomPlayerLocation();
				if(at != null)
					game.powerUpManager.dropPowerUp(at, parsePowerUp(target.getValue()));
				break;
			}
			case SPAWN_PERK:
				game.perkPoolManager.spawnPerk(target.getValue(), target.getPoint() == null ? null : target.pointLocation(world));
				break;
			case MESSAGE:
			{
				String text = ChatColor.translateAlternateColorCodes('&', target.getValue());
				for(Player player : game.getPlayersAndSpectators())
				{
					if(target.isTitle())
						player.sendTitle(text, "", 10, 60, 20);
					else
						CommandUtil.sendMessageToPlayer(player, text);
				}
				break;
			}
			case TURN_ON_POWER:
				if(!game.isPowered())
					game.turnOnPower();
				break;
			case GIVE_POINTS:
				for(Player player : game.getPlayersInGame())
				{
					PointManager.INSTANCE.addPoints(player, target.getAmount());
					PointManager.INSTANCE.notifyPlayer(player);
				}
				break;
			case PLACE_BLOCKS:
				placeBlocks(world, target);
				break;
			default:
		}
	}

	private void openDoor(String doorId)
	{
		for(Door door : game.doorManager.getDoors())
			if(door.doorID.equalsIgnoreCase(doorId))
			{
				if(door.isOpened())
					return;
				door.openDoor();
				door.playerDoorOpenSound();
				game.signPropManager.onDoorOpened(door);
				game.perkPoolManager.onDoorOpened();
				return;
			}
	}

	private void placeBlocks(World world, TriggerTarget target)
	{
		if(world == null)
			return;
		for(TriggerTarget.PlacedBlock placed : target.getBlocks())
		{
			BlockData data;
			try
			{
				data = Bukkit.createBlockData(placed.blockData);
			} catch(IllegalArgumentException e)
			{
				COMZombies.log.warning("Trigger block " + placed.blockData + " at " + placed.x + "," + placed.y + "," + placed.z + " is not valid on this server");
				continue;
			}
			Block block = world.getBlockAt(placed.x, placed.y, placed.z);
			replacedBlocks.putIfAbsent(block.getLocation(), block.getBlockData());
			block.setBlockData(data, false);
		}
	}

	/** Calls off a trigger's pending timeline and arms it again from scratch, as if it never ran this game. */
	private void reset(Trigger trigger)
	{
		List<Integer> tasks = pending.remove(trigger);
		if(tasks != null)
			for(int task : tasks)
				Bukkit.getScheduler().cancelTask(task);
		firedTriggers.remove(trigger.getId().toLowerCase(Locale.ROOT));
		completedBy.remove(trigger);
		removeVisuals(trigger);
		trigger.rearm();
		for(TriggerStep step : trigger.getSteps())
			if(step.getKind() == TriggerStep.Kind.TRIGGER_DONE)
				step.resetProgress();
		trigger.setFailed(false);
		spawnVisuals(trigger);
	}

	/** Like /clone: {@code replace} copies every block, {@code masked} skips air, {@code move} also clears the source. */
	private void cloneBlocks(World world, TriggerTarget target)
	{
		Vector min = target.getBoxMin();
		Vector max = target.getBoxMax();
		Vector to = target.getPoint();
		if(world == null || min == null || to == null)
			return;
		String mode = target.getValue().toLowerCase(Locale.ROOT);
		boolean masked = mode.contains("masked");
		boolean move = mode.contains("move");
		Tag<Material> tag = blockTag(target.getFilter());
		Material only = tag == null && !target.getFilter().isEmpty() ? Material.matchMaterial(target.getFilter()) : null;
		if(!target.getFilter().isEmpty() && tag == null && only == null)
		{
			COMZombies.log.warning("Trigger clone filter " + target.getFilter() + " is not known on this server; skipped");
			return;
		}
		int sx = max.getBlockX() - min.getBlockX() + 1, sy = max.getBlockY() - min.getBlockY() + 1, sz = max.getBlockZ() - min.getBlockZ() + 1;
		if(tooBig(sx, sy, sz))
			return;
		BlockData[] copied = new BlockData[sx * sy * sz];
		for(int x = 0; x < sx; x++)
			for(int y = 0; y < sy; y++)
				for(int z = 0; z < sz; z++)
				{
					Block source = world.getBlockAt(min.getBlockX() + x, min.getBlockY() + y, min.getBlockZ() + z);
					Material type = source.getType();
					boolean wanted = (tag != null ? tag.isTagged(type) : only == null || type == only) && !(masked && type.isAir());
					if(wanted)
						copied[(x * sy + y) * sz + z] = source.getBlockData();
				}
		if(move)
			for(int x = 0; x < sx; x++)
				for(int y = 0; y < sy; y++)
					for(int z = 0; z < sz; z++)
						if(copied[(x * sy + y) * sz + z] != null)
							setJournaled(world.getBlockAt(min.getBlockX() + x, min.getBlockY() + y, min.getBlockZ() + z), Material.AIR.createBlockData());
		for(int x = 0; x < sx; x++)
			for(int y = 0; y < sy; y++)
				for(int z = 0; z < sz; z++)
				{
					BlockData data = copied[(x * sy + y) * sz + z];
					if(data != null)
						setJournaled(world.getBlockAt(to.getBlockX() + x, to.getBlockY() + y, to.getBlockZ() + z), data);
				}
	}

	/** Like /fill, optionally only replacing the {@code filter} block or tag. */
	private void fillBlocks(World world, TriggerTarget target)
	{
		Vector min = target.getBoxMin();
		Vector max = target.getBoxMax();
		if(world == null || min == null)
			return;
		BlockData data;
		try
		{
			data = Bukkit.createBlockData(target.getValue());
		} catch(IllegalArgumentException e)
		{
			COMZombies.log.warning("Trigger fill block " + target.getValue() + " is not valid on this server");
			return;
		}
		Tag<Material> tag = blockTag(target.getFilter());
		Material only = tag == null && !target.getFilter().isEmpty() ? Material.matchMaterial(target.getFilter()) : null;
		if(!target.getFilter().isEmpty() && tag == null && only == null)
		{
			COMZombies.log.warning("Trigger fill filter " + target.getFilter() + " is not known on this server; skipped");
			return;
		}
		if(tooBig(max.getBlockX() - min.getBlockX() + 1, max.getBlockY() - min.getBlockY() + 1, max.getBlockZ() - min.getBlockZ() + 1))
			return;
		for(int x = min.getBlockX(); x <= max.getBlockX(); x++)
			for(int y = min.getBlockY(); y <= max.getBlockY(); y++)
				for(int z = min.getBlockZ(); z <= max.getBlockZ(); z++)
				{
					Block block = world.getBlockAt(x, y, z);
					Material type = block.getType();
					if(tag != null ? tag.isTagged(type) : only == null || type == only)
						setJournaled(block, data);
				}
	}

	/** A box this big is almost certainly a typo'd coordinate; changing it would stall the server. */
	private static boolean tooBig(int sx, int sy, int sz)
	{
		long volume = (long) sx * sy * sz;
		if(volume <= MAX_EDIT_VOLUME)
			return false;
		COMZombies.log.warning("Trigger block edit of " + volume + " blocks is over the limit of " + MAX_EDIT_VOLUME + "; skipped");
		return true;
	}

	private void setJournaled(Block block, BlockData data)
	{
		replacedBlocks.putIfAbsent(block.getLocation(), block.getBlockData());
		setWithoutPhysics(block, data);
	}

	private void setWithoutPhysics(Block block, BlockData data)
	{
		boolean fluidChange = flows(data) || flows(block.getBlockData());
		block.setBlockData(data, false);
		if(fluidChange)
			queueFluidUpdate(block.getLocation());
	}

	private void queueFluidUpdate(Location location)
	{
		pendingFluidUpdates.add(location);
		if(fluidTaskId == -1)
			fluidTaskId = COMZombies.scheduleTask(1L, 1L, this::runFluidUpdates);
	}

	/**
	 * Wakes the queued fluids so they flow or drain, but only where they border an open block: the inside
	 * of a flooded room needs no update. A drained block wakes the fluid around it instead.
	 */
	private void runFluidUpdates()
	{
		Iterator<Location> it = pendingFluidUpdates.iterator();
		for(int done = 0; done < FLUID_UPDATES_PER_TICK && it.hasNext(); done++)
		{
			Location location = it.next();
			it.remove();
			if(location.getWorld() == null)
				continue;
			Block block = location.getBlock();
			if(flows(block.getBlockData()))
			{
				if(bordersOpenBlock(block))
					wakeFluid(block);
			}
			else if(!block.getType().isSolid())
			{
				for(BlockFace face : FLUID_NEIGHBOURS)
				{
					Block neighbour = block.getRelative(face);
					if(flows(neighbour.getBlockData()))
						wakeFluid(neighbour);
				}
			}
		}
		if(pendingFluidUpdates.isEmpty())
		{
			Bukkit.getScheduler().cancelTask(fluidTaskId);
			fluidTaskId = -1;
		}
	}

	private static boolean bordersOpenBlock(Block block)
	{
		for(BlockFace face : FLUID_NEIGHBOURS)
		{
			Block neighbour = block.getRelative(face);
			if(!neighbour.getType().isSolid() && !flows(neighbour.getBlockData()))
				return true;
		}
		return false;
	}

	/** Setting a block to its own data is a no-op on the server, so it is cleared first to make the fluid tick. */
	private static void wakeFluid(Block block)
	{
		BlockData data = block.getBlockData();
		block.setType(Material.AIR, false);
		block.setBlockData(data, true);
	}

	/** Water and lava a timeline puts in run like the map's flush (Shangri-La's stairs), and drain when taken away. */
	private static boolean flows(BlockData data)
	{
		return data.getMaterial() == Material.WATER || data.getMaterial() == Material.LAVA;
	}

	/** {@code #namespace:path} block tag, or null for a plain block id, no filter or a tag this server lacks. */
	@SuppressWarnings("deprecation")
	private static Tag<Material> blockTag(String filter)
	{
		if(filter == null || !filter.startsWith("#"))
			return null;
		String[] parts = filter.substring(1).split(":", 2);
		NamespacedKey key = parts.length == 2 ? new NamespacedKey(parts[0], parts[1]) : NamespacedKey.minecraft(parts[0]);
		return Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
	}

	private void restorePlacedBlocks()
	{
		for(Map.Entry<Location, BlockData> entry : replacedBlocks.entrySet())
			if(entry.getKey().getWorld() != null)
				setWithoutPhysics(entry.getKey().getBlock(), entry.getValue());
		replacedBlocks.clear();
	}

	/** {@code key} is a sounds.json path (e.g. {@code trigger.song}) or a sound name/event used as is. */
	private void playSong(String key)
	{
		String sound = SoundConfig.get(key, key.equalsIgnoreCase("trigger.song") ? "MUSIC_DISC_CAT" : key);
		for(Player player : game.getPlayersAndSpectators())
			SoundUtil.play(player, player.getLocation(), sound, SoundCategory.RECORDS, SONG_VOLUME, 1);
	}

	private static PowerUp parsePowerUp(String value)
	{
		for(PowerUp powerUp : PowerUp.values())
			if(powerUp != PowerUp.NONE && powerUp.name().equalsIgnoreCase(value.trim()))
				return powerUp;
		PowerUp[] all = PowerUp.values();
		PowerUp pick;
		do
			pick = all[COMZombies.rand.nextInt(all.length)];
		while(pick == PowerUp.NONE);
		return pick;
	}

	private Location randomPlayerLocation()
	{
		List<Player> active = activePlayers();
		return active.isEmpty() ? null : active.get(COMZombies.rand.nextInt(active.size())).getLocation();
	}

	// ---------------------------------------------------------------- visuals

	private void spawnVisuals(Trigger trigger)
	{
		if(game.getStatus() != Game.GameStatus.INGAME || !trigger.isActive())
			return;
		for(TriggerStep step : trigger.getSteps())
			spawnVisual(step);
	}

	private void removeVisuals(Trigger trigger)
	{
		for(TriggerStep step : trigger.getSteps())
			removeVisual(step);
	}

	private void spawnVisual(TriggerStep step)
	{
		removeVisual(step);
		World world = game.getWorld();
		if(world == null || step.getKind() != TriggerStep.Kind.INTERACT || !step.hasVisual() || step.getPoint() == null || step.isDone())
			return;
		ItemStack stack = visualStack(step.getVisual());
		if(stack == null)
			return;
		Location at = interactCentre(step, world);
		Entity entity = Displays.supportsItemDisplays() ? Displays.spawnItem(at, stack, 0.6f, COMZombies.rand.nextInt(360), 0f, false) : null;
		if(entity == null)
			entity = legacyVisual(at, stack);
		if(entity != null)
			visuals.put(step, entity);
	}

	private void removeVisual(TriggerStep step)
	{
		Entity entity = visuals.remove(step);
		if(entity != null && !entity.isDead())
			entity.remove();
	}

	/** {@code teddy} is the mystery box teddy bear model; anything else is an item/material name. */
	public static ItemStack visualStack(String visual)
	{
		if(TriggerStep.TEDDY_VISUAL.equalsIgnoreCase(visual))
		{
			ItemStack teddy = new ItemStack(Material.TOTEM_OF_UNDYING);
			PackModels.apply(teddy, "misc/box_teddy");
			return teddy;
		}
		Material material = Material.matchMaterial(visual);
		return material == null || !material.isItem() || material == Material.AIR ? null : new ItemStack(material);
	}

	/** Servers without display entities: a small invisible marker stand wearing the item. */
	private static Entity legacyVisual(Location at, ItemStack stack)
	{
		ArmorStand stand = (ArmorStand) at.getWorld().spawnEntity(at.clone().subtract(0, 0.7, 0), EntityType.ARMOR_STAND);
		stand.setVisible(false);
		stand.setGravity(false);
		stand.setMarker(true);
		stand.setSmall(true);
		stand.setInvulnerable(true);
		stand.setPersistent(false);
		if(stand.getEquipment() != null)
			stand.getEquipment().setHelmet(stack);
		return stand;
	}

	// ---------------------------------------------------------------- helpers

	private void actionBarAll(String text)
	{
		for(Player player : game.getPlayersAndSpectators())
			CommandUtil.actionBar(player, text);
	}


	private static int parseInt(String raw, int fallback)
	{
		try
		{
			return Integer.parseInt(raw.trim());
		} catch(NumberFormatException | NullPointerException e)
		{
			return fallback;
		}
	}
}
