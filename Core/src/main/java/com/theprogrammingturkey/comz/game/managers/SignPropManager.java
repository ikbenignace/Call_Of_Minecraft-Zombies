package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.ConfigSetup;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.builder.MachineSigns;
import com.theprogrammingturkey.comz.game.features.Buildable;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.Quest;
import com.theprogrammingturkey.comz.game.features.QuestStep;
import com.theprogrammingturkey.comz.game.features.QuestStepType;
import com.theprogrammingturkey.comz.game.features.Trap;
import com.theprogrammingturkey.comz.game.features.BuildableItems;
import com.theprogrammingturkey.comz.game.signs.BuildableSign;
import com.theprogrammingturkey.comz.game.signs.DoorSign;
import com.theprogrammingturkey.comz.game.signs.GrenadeSign;
import com.theprogrammingturkey.comz.game.signs.IGameSign;
import com.theprogrammingturkey.comz.game.signs.PerkMachineSign;
import com.theprogrammingturkey.comz.game.signs.QuestSign;
import com.theprogrammingturkey.comz.game.signs.TeleporterSign;
import com.theprogrammingturkey.comz.game.signs.TrapSign;
import com.theprogrammingturkey.comz.listeners.SignListener;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Takes the remaining feature signs (doors, traps, teleporters, buildable stations, quest triggers,
 * grenades and perk machines without a model) off the walls for the length of a game. Each one becomes a
 * price hologram and an invisible click box, and is bought with F or a right-click like the machines.
 * The signs are snapshotted when they are hidden and rebuilt exactly on teardown, and the buy logic keeps
 * running off the snapshotted lines.
 */
public class SignPropManager
{
	private static final long UPKEEP_TICKS = 10L;
	private static final long BUY_DEBOUNCE_MS = 250L;
	private static final float HITBOX_WIDTH = 1.0f;
	private static final float HITBOX_HEIGHT = 1.4f;
	private static final float MAX_DOOR_HITBOX = 3.0f;

	public enum PropType
	{
		DOOR, TRAP, TELEPORTER, BUILDABLE, QUEST, GRENADE, PERK
	}

	public static final class Prop
	{
		private final PropType type;
		private final IGameSign handler;
		private final Location signLoc;
		private final String[] lines;
		private final Door door;
		private final Location centre;
		private final Location hologramLoc;
		private final List<SignSnapshot> snapshots = new ArrayList<>();
		private Entity hitbox;
		private Entity hologram;
		private String label;
		private boolean retired;

		private Prop(PropType type, IGameSign handler, Location signLoc, String[] lines, Door door, Location centre, Location hologramLoc)
		{
			this.type = type;
			this.handler = handler;
			this.signLoc = signLoc;
			this.lines = lines;
			this.door = door;
			this.centre = centre;
			this.hologramLoc = hologramLoc;
		}

		public PropType getType()
		{
			return type;
		}

		/** Where the prop stands, used for distance and aim checks. */
		public Location getCentre()
		{
			return centre.clone();
		}

		public Door getDoor()
		{
			return door;
		}
	}

	private final Game game;
	private final List<Prop> props = new ArrayList<>();
	private final Map<UUID, Prop> byHitbox = new HashMap<>();
	private final Map<UUID, Long> lastBuy = new HashMap<>();
	private int taskId = -1;

	public SignPropManager(Game game)
	{
		this.game = game;
	}

	public void spawnAll()
	{
		removeAll();
		World world = game.arena.getWorld();
		if(world == null || !game.arena.areMinAndMaxSet() || !Displays.supportsHitboxes())
			return;

		for(Door door : game.doorManager.getDoors())
			if(!door.isOpened())
				trySpawnDoor(door, world);

		List<Sign> signs = new ArrayList<>();
		MachineSigns.forEachArenaSign(game, signs::add);
		for(Sign sign : signs)
			trySpawnSign(sign);

		if(props.isEmpty())
			return;
		for(Prop prop : props)
			refreshLabel(prop);
		taskId = COMZombies.scheduleTask(UPKEEP_TICKS, UPKEEP_TICKS, this::upkeep);
	}

	/** True while the door signs are replaced by props, so nothing else needs to clear them. */
	public boolean isActive()
	{
		return !props.isEmpty();
	}

	private void trySpawnDoor(Door door, World world)
	{
		List<Block> blocks = door.getBlocks();
		List<Location> signLocs = door.getSignsLocations();
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for(Block block : blocks)
		{
			minX = Math.min(minX, block.getX());
			minY = Math.min(minY, block.getY());
			minZ = Math.min(minZ, block.getZ());
			maxX = Math.max(maxX, block.getX() + 1);
			maxY = Math.max(maxY, block.getY() + 1);
			maxZ = Math.max(maxZ, block.getZ() + 1);
		}
		if(blocks.isEmpty())
		{
			if(signLocs.isEmpty())
				return;
			Location first = signLocs.get(0);
			minX = first.getBlockX();
			minY = first.getBlockY();
			minZ = first.getBlockZ();
			maxX = minX + 1;
			maxY = minY + 1;
			maxZ = minZ + 1;
		}

		double cx = (minX + maxX) / 2.0;
		double cz = (minZ + maxZ) / 2.0;
		Location centre = new Location(world, cx, (minY + maxY) / 2.0, cz);
		float width = (float) Math.min(MAX_DOOR_HITBOX, Math.max(1.0, Math.max(maxX - minX, maxZ - minZ)));
		float height = (float) Math.min(MAX_DOOR_HITBOX, Math.max(1.0, maxY - minY));
		Entity hitbox = Displays.spawnHitbox(new Location(world, cx, minY, cz), width, height);
		if(hitbox == null)
			return;

		Prop prop = new Prop(PropType.DOOR, null, signLocs.isEmpty() ? null : signLocs.get(0), null, door, centre, centre);
		prop.hitbox = hitbox;
		for(Location signLoc : signLocs)
		{
			BlockState state = signLoc.getBlock().getState();
			if(state instanceof Sign)
				prop.snapshots.add(SignSnapshot.of((Sign) state));
		}
		register(prop);
	}

	private void trySpawnSign(Sign sign)
	{
		String[] lines = sign.getLines().clone();
		String typeLine = ChatColor.stripColor(lines[1]).trim().toLowerCase();
		IGameSign handler = SignListener.getSignHandler(typeLine);
		PropType type = typeOf(handler, lines);
		if(type == null)
			return;

		Location blockLoc = sign.getLocation();
		Location centre = blockLoc.clone().add(0.5, 0.5, 0.5);
		Entity hitbox = Displays.spawnHitbox(blockLoc.clone().add(0.5, 0.0, 0.5), HITBOX_WIDTH, HITBOX_HEIGHT);
		if(hitbox == null)
			return;

		Prop prop = new Prop(type, handler, blockLoc, lines, null, centre, blockLoc.clone().add(0.5, 1.0, 0.5));
		prop.hitbox = hitbox;
		prop.snapshots.add(SignSnapshot.of(sign));
		register(prop);
	}

	private static PropType typeOf(IGameSign handler, String[] lines)
	{
		if(handler instanceof TrapSign)
			return PropType.TRAP;
		if(handler instanceof TeleporterSign)
			return PropType.TELEPORTER;
		if(handler instanceof BuildableSign)
			return PropType.BUILDABLE;
		if(handler instanceof QuestSign)
			return PropType.QUEST;
		if(handler instanceof GrenadeSign)
			return PropType.GRENADE;
		if(handler instanceof PerkMachineSign)
		{
			PerkType perk = PerkType.getPerkType(ChatColor.stripColor(lines[2]));
			if(perk != null && com.theprogrammingturkey.comz.game.visuals.MachineLooks.forPerk(perk) == null)
				return PropType.PERK;
		}
		return null;
	}

	private void register(Prop prop)
	{
		for(SignSnapshot snapshot : prop.snapshots)
			snapshot.hide();
		byHitbox.put(prop.hitbox.getUniqueId(), prop);
		props.add(prop);
	}

	private void upkeep()
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
		{
			removeAll();
			return;
		}
		for(Prop prop : props)
		{
			if(prop.retired)
				continue;
			if(prop.door != null && prop.door.isOpened())
			{
				retire(prop);
				continue;
			}
			refreshLabel(prop);
		}
	}

	/** Called as soon as a door has been bought so its prompt vanishes without waiting for the next upkeep. */
	public void onDoorOpened(Door door)
	{
		for(Prop prop : props)
			if(prop.door == door && !prop.retired)
				retire(prop);
	}

	/** Drops the visuals of a prop that is used up; its hidden signs stay hidden until teardown. */
	private void retire(Prop prop)
	{
		prop.retired = true;
		removeEntity(prop.hologram);
		prop.hologram = null;
		prop.label = null;
		if(prop.hitbox != null)
		{
			byHitbox.remove(prop.hitbox.getUniqueId());
			removeEntity(prop.hitbox);
			prop.hitbox = null;
		}
	}

	private void refreshLabel(Prop prop)
	{
		String label = labelFor(prop);
		if(Objects.equals(label, prop.label) && (label == null || (prop.hologram != null && !prop.hologram.isDead())))
			return;
		removeEntity(prop.hologram);
		prop.hologram = null;
		prop.label = label;
		if(label != null)
			prop.hologram = Displays.spawnText(prop.hologramLoc, label, prop.type == PropType.DOOR);
	}

	private static void removeEntity(Entity entity)
	{
		if(entity != null && !entity.isDead())
			entity.remove();
	}

	public List<Prop> getProps()
	{
		List<Prop> out = new ArrayList<>(props.size());
		for(Prop prop : props)
			if(!prop.retired)
				out.add(prop);
		return out;
	}

	/** Right-click on a prop hitbox. Returns true when the entity was one of ours. */
	public boolean handleInteract(Player player, Entity entity)
	{
		Prop prop = byHitbox.get(entity.getUniqueId());
		if(prop == null)
			return false;
		buy(player, prop);
		return true;
	}

	/** Right-click on a block of a closed door. Returns true when it belonged to one. */
	public boolean handleBlockClick(Player player, Block block)
	{
		for(Prop prop : props)
			if(!prop.retired && prop.door != null && prop.door.hasDoorLoc(block))
			{
				buy(player, prop);
				return true;
			}
		return false;
	}

	public void buy(Player player, Prop prop)
	{
		if(prop.retired || !mayBuy(player))
			return;
		if(prop.type == PropType.DOOR)
			DoorSign.tryOpen(game, player, prop.door);
		else
			prop.handler.onInteract(game, player, prop.signLoc, prop.lines);
	}

	/** F on a door the player is looking at. */
	public void buyDoor(Player player, Door door)
	{
		if(mayBuy(player))
			DoorSign.tryOpen(game, player, door);
	}

	/**
	 * Gate shared by every click and key path: the game is running, the player can act, and the same
	 * player did not already buy within the debounce window (a single press can reach us twice).
	 */
	private boolean mayBuy(Player player)
	{
		if(game.getStatus() != Game.GameStatus.INGAME || !game.getLivingPlayers().contains(player)
				|| game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
			return false;
		long now = System.currentTimeMillis();
		Long last = lastBuy.get(player.getUniqueId());
		if(last != null && now - last < BUY_DEBOUNCE_MS)
			return false;
		lastBuy.put(player.getUniqueId(), now);
		return true;
	}

	private boolean powerLocked()
	{
		return game.hasPower() && !game.isPowered();
	}

	private static String cap(String id)
	{
		String spaced = id.replace('_', ' ');
		return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private static int parseCost(String line, int fallback)
	{
		String stripped = ChatColor.stripColor(line);
		return stripped != null && stripped.trim().matches("[0-9]{1,9}") ? Integer.parseInt(stripped.trim()) : fallback;
	}

	private static String price(int cost)
	{
		return ChatColor.YELLOW + "[" + cost + "]";
	}

	private static String requiresPower()
	{
		return ChatColor.RED + "Requires Power";
	}

	/** Shared hologram text for a prop, or null when it shows nothing. The same for every viewer. */
	private String labelFor(Prop prop)
	{
		ConfigSetup cfg = ConfigManager.getMainConfig();
		switch(prop.type)
		{
			case DOOR:
				if(prop.door.requiresPower() && !game.isPowered())
					return requiresPower();
				return ChatColor.AQUA + "Open Door " + price(prop.door.getCost());
			case TRAP:
			{
				Trap trap = game.trapManager.getTrap(ChatColor.stripColor(prop.lines[2]));
				if(trap == null)
					return null;
				if(powerLocked())
					return requiresPower();
				if(trap.isActive())
					return ChatColor.GRAY + "Trap Active";
				if(trap.secondsUntilReady(cfg.trapCooldownSeconds) > 0)
					return ChatColor.GRAY + "Trap Recharging";
				return ChatColor.AQUA + "Activate Trap " + price(trap.getCost());
			}
			case TELEPORTER:
			{
				String name = ChatColor.stripColor(prop.lines[2]).toLowerCase();
				if(!game.teleporterManager.getTeleporters().containsKey(name))
					return null;
				if(powerLocked())
					return requiresPower();
				if(game.teleporterManager.secondsUntilReady(name, cfg.teleporterCooldownSeconds) > 0)
					return ChatColor.GRAY + "Teleporter Recharging";
				return ChatColor.AQUA + "Teleport " + price(parseCost(prop.lines[3], 500));
			}
			case BUILDABLE:
			{
				String id = ChatColor.stripColor(prop.lines[2]).trim().toLowerCase();
				Buildable buildable = game.buildableManager.getBuildable(id);
				if(buildable == null)
					return null;
				if(powerLocked())
					return requiresPower();
				if(buildable.isAssembled())
					return ChatColor.GRAY + cap(id) + " Built";
				return ChatColor.AQUA + cap(id) + " " + price(cfg.buildablePartCost);
			}
			case QUEST:
				return questReady(prop) ? ChatColor.LIGHT_PURPLE + "Interact" : null;
			case GRENADE:
				return ChatColor.AQUA + "Grenades " + price(parseCost(prop.lines[2], 250));
			case PERK:
			{
				PerkType perk = PerkType.getPerkType(ChatColor.stripColor(prop.lines[2]));
				if(perk == null)
					return null;
				return ChatColor.AQUA + cap(perk.toString().toLowerCase()) + " " + price(parseCost(prop.lines[3], perk.getDefaultCost()));
			}
			default:
				return null;
		}
	}

	private boolean questReady(Prop prop)
	{
		Quest quest = game.questManager.getQuest();
		if(quest == null || quest.isComplete())
			return false;
		QuestStep step = quest.currentStep();
		return step != null && step.matches(QuestStepType.INTERACT, ChatColor.stripColor(prop.lines[2]).trim());
	}

	/** Action-bar prompt for what F does on this prop for this player, or null when F does nothing here. */
	public String promptFor(Prop prop, Player player)
	{
		ConfigSetup cfg = ConfigManager.getMainConfig();
		switch(prop.type)
		{
			case TRAP:
			{
				Trap trap = game.trapManager.getTrap(ChatColor.stripColor(prop.lines[2]));
				if(trap == null)
					return ChatColor.RED + "[F] Invalid trap";
				if(powerLocked())
					return ChatColor.RED + "[F] " + ChatColor.GRAY + "Trap - Requires Power";
				if(trap.isActive())
					return ChatColor.GRAY + "Trap active";
				int seconds = trap.secondsUntilReady(cfg.trapCooldownSeconds);
				if(seconds > 0)
					return ChatColor.GRAY + "Trap recharging (" + seconds + "s)";
				return affordable(player, trap.getCost()) + "[F] Activate Trap " + price(trap.getCost());
			}
			case TELEPORTER:
			{
				String name = ChatColor.stripColor(prop.lines[2]).toLowerCase();
				if(!game.teleporterManager.getTeleporters().containsKey(name))
					return ChatColor.RED + "[F] Invalid teleporter";
				if(powerLocked())
					return ChatColor.RED + "[F] " + ChatColor.GRAY + "Teleporter - Requires Power";
				int seconds = game.teleporterManager.secondsUntilReady(name, cfg.teleporterCooldownSeconds);
				if(seconds > 0)
					return ChatColor.GRAY + "Teleporter recharging (" + seconds + "s)";
				int cost = parseCost(prop.lines[3], 500);
				return affordable(player, cost) + "[F] Teleport " + price(cost);
			}
			case BUILDABLE:
				return buildablePrompt(prop, player);
			case QUEST:
				return questReady(prop) ? ChatColor.GREEN + "[F] Interact" : null;
			case GRENADE:
			{
				PlayerWeaponManager manager = game.getPlayersWeapons(player);
				if(manager != null && manager.hasFullGrenades())
					return ChatColor.GRAY + "Grenades full";
				int cost = parseCost(prop.lines[2], 250);
				return affordable(player, cost) + "[F] Grenades " + price(cost);
			}
			case PERK:
				return game.proximityBuyManager.perkPrompt(prop.lines, player);
			default:
				return null;
		}
	}

	private String buildablePrompt(Prop prop, Player player)
	{
		String id = ChatColor.stripColor(prop.lines[2]).trim().toLowerCase();
		Buildable buildable = game.buildableManager.getBuildable(id);
		if(buildable == null)
			return ChatColor.RED + "[F] Invalid station";
		if(powerLocked())
			return ChatColor.RED + "[F] " + ChatColor.GRAY + cap(id) + " - Requires Power";
		if(buildable.isAssembled())
			return ChatColor.GRAY + cap(id) + " built";
		if(BuildableItems.getPartName(player.getInventory().getItemInMainHand(), id) != null)
			return ChatColor.GREEN + "[F] Add part to " + cap(id);
		int cost = ConfigManager.getMainConfig().buildablePartCost;
		return affordable(player, cost) + "[F] " + cap(id) + " part " + price(cost);
	}

	private static ChatColor affordable(Player player, int cost)
	{
		return PointManager.INSTANCE.canBuy(player, cost) ? ChatColor.GREEN : ChatColor.RED;
	}

	/** Despawn every entity and rebuild every hidden sign. Safe to call repeatedly. */
	public void removeAll()
	{
		if(taskId != -1)
		{
			Bukkit.getScheduler().cancelTask(taskId);
			taskId = -1;
		}
		for(Prop prop : props)
		{
			removeEntity(prop.hologram);
			removeEntity(prop.hitbox);
			for(SignSnapshot snapshot : prop.snapshots)
				snapshot.restore();
		}
		props.clear();
		byHitbox.clear();
		lastBuy.clear();
	}
}
