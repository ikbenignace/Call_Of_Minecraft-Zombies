package com.theprogrammingturkey.comz.game.equipment;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.BreathingGear;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-game equipment state: what each player owns (melee, placeable, tactical, gear), the mines they have
 * placed and the running tactical effects. Carry counts live in the hotbar stacks themselves, so the existing
 * Monkey Bomb throw (which just shrinks the held stack) works unchanged when it is sold as a tactical.
 */
public class EquipmentManager
{
	private final Game game;
	private final Map<UUID, Loadout> loadouts = new HashMap<>();
	private final Map<UUID, Long> lastMelee = new HashMap<>();
	final Set<Integer> tasks = new HashSet<>();
	final Set<Entity> entities = new HashSet<>();
	final PlacedMines mines;
	private final TacticalEffects tacticals;

	public EquipmentManager(Game game)
	{
		this.game = game;
		this.mines = new PlacedMines(game, this);
		this.tacticals = new TacticalEffects(game, this);
	}

	private static final class Loadout
	{
		Equipment melee;
		Equipment placeable;
		Equipment tactical;
		Equipment gear;
	}

	private Loadout loadout(Player player)
	{
		return loadouts.computeIfAbsent(player.getUniqueId(), id -> new Loadout());
	}

	public Equipment getMelee(Player player)
	{
		Loadout loadout = loadouts.get(player.getUniqueId());
		return loadout == null ? null : loadout.melee;
	}

	private Equipment owned(Player player, EquipmentType type)
	{
		Loadout loadout = loadouts.get(player.getUniqueId());
		if(loadout == null)
			return null;
		switch(type)
		{
			case MELEE:
				return loadout.melee;
			case PLACEABLE:
				return loadout.placeable;
			case TACTICAL:
				return loadout.tactical;
			default:
				return loadout.gear;
		}
	}

	private void setOwned(Player player, Equipment equipment)
	{
		Loadout loadout = loadout(player);
		switch(equipment.type)
		{
			case MELEE:
				loadout.melee = equipment;
				break;
			case PLACEABLE:
				loadout.placeable = equipment;
				break;
			case TACTICAL:
				loadout.tactical = equipment;
				break;
			default:
				loadout.gear = equipment;
				break;
		}
	}

	public boolean owns(Player player, Equipment equipment)
	{
		return owned(player, equipment.type) == equipment;
	}

	/** How many of a stackable item the player carries; read from its hotbar stack. */
	public int carried(Player player, Equipment equipment)
	{
		int slot = EquipmentCatalogue.slotFor(equipment.type);
		if(slot < 0)
			return 0;
		ItemStack stack = player.getInventory().getItem(slot);
		if(stack == null || !equipment.name.equals(Equipment.nameOf(stack)))
			return 0;
		return stack.getAmount();
	}

	private static boolean isStackable(Equipment equipment)
	{
		return equipment.type == EquipmentType.PLACEABLE || equipment.type == EquipmentType.TACTICAL;
	}

	private static int refillPrice(Equipment equipment, int signCost)
	{
		return equipment.refillCost == equipment.cost ? signCost : equipment.refillCost;
	}

	/** What pressing F at this wall buy would do, as an action-bar prompt. */
	public String prompt(Player player, Equipment equipment, int signCost)
	{
		if(owns(player, equipment))
		{
			if(!isStackable(equipment) || carried(player, equipment) >= equipment.maxCarry)
				return ChatColor.GRAY + "[F] " + equipment.name + " - owned";
			int price = refillPrice(equipment, signCost);
			ChatColor color = PointManager.INSTANCE.canBuy(player, price) ? ChatColor.GREEN : ChatColor.RED;
			return color + "[F] Refill " + equipment.name + " " + ChatColor.YELLOW + "[" + price + "]";
		}
		ChatColor color = PointManager.INSTANCE.canBuy(player, signCost) ? ChatColor.GREEN : ChatColor.RED;
		return color + "[F] " + equipment.name + " " + ChatColor.YELLOW + "[" + signCost + "]";
	}

	public void buy(Player player, Equipment equipment, int signCost)
	{
		boolean refill = owns(player, equipment);
		if(refill && (!isStackable(equipment) || carried(player, equipment) >= equipment.maxCarry))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You already have the " + equipment.name + "!");
			return;
		}
		Equipment blocking = blockedBy(player, equipment);
		if(blocking != null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Use up your " + blocking.name + " first!");
			return;
		}
		int price = refill ? refillPrice(equipment, signCost) : signCost;
		if(!PointManager.INSTANCE.canBuy(player, price))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			return;
		}
		PointManager.INSTANCE.takePoints(player, price);
		PointManager.INSTANCE.notifyPlayer(player);
		give(player, equipment);
		SoundUtil.play(player.getWorld(), player.getLocation(), equipment.sound("buy"), SoundCategory.PLAYERS, 1, 1);
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + (refill ? "Refilled the " : "You got the ") + ChatColor.GOLD + ChatColor.BOLD + equipment.name + ChatColor.RED + ChatColor.BOLD + "!");
	}

	/**
	 * With {@code one_placeable} / {@code one_tactical} off, a different item of that kind cannot be bought while
	 * the current one still has charges; returns that item, or null when buying is allowed.
	 */
	private Equipment blockedBy(Player player, Equipment equipment)
	{
		Equipment previous = owned(player, equipment.type);
		if(previous == null || previous == equipment)
			return null;
		boolean replaces = (equipment.type == EquipmentType.PLACEABLE && EquipmentCatalogue.onePlaceable)
				|| (equipment.type == EquipmentType.TACTICAL && EquipmentCatalogue.oneTactical)
				|| !isStackable(equipment);
		return replaces || carried(player, previous) <= 0 ? null : previous;
	}

	/** Hands {@code equipment} to the player for free, replacing what they had of that kind. */
	public void give(Player player, Equipment equipment)
	{
		Equipment previous = owned(player, equipment.type);
		if(previous != null && previous != equipment && previous.type == EquipmentType.GEAR)
			takeOffGear(player, previous);
		setOwned(player, equipment);

		int slot = EquipmentCatalogue.slotFor(equipment.type);
		if(isStackable(equipment))
		{
			int count = previous == equipment ? carried(player, equipment) : 0;
			setStack(player, equipment, Math.min(equipment.maxCarry, count + equipment.perBuy));
		}
		else if(slot >= 0)
		{
			player.getInventory().setItem(slot, gearItem(player, equipment));
		}
		if(equipment.type == EquipmentType.GEAR && "breathing".equals(equipment.behaviour))
			setBreathing(player, equipment, true);
		player.updateInventory();
	}

	private void setStack(Player player, Equipment equipment, int count)
	{
		int slot = EquipmentCatalogue.slotFor(equipment.type);
		if(slot < 0)
			return;
		player.getInventory().setItem(slot, count > 0 ? equipment.createItem(count) : null);
	}

	private ItemStack gearItem(Player player, Equipment equipment)
	{
		ItemStack stack = equipment.createItem(1);
		if(equipment.type == EquipmentType.GEAR && "breathing".equals(equipment.behaviour))
		{
			ItemMeta meta = stack.getItemMeta();
			if(meta != null)
			{
				boolean on = BreathingGear.isWearing(player);
				meta.setDisplayName(ChatColor.GOLD + equipment.name + (on ? ChatColor.GREEN + " [ON]" : ChatColor.GRAY + " [OFF]"));
				stack.setItemMeta(meta);
			}
		}
		return stack;
	}

	/** Toggles a breathing helmet on or off; other gear has no use action yet. */
	public void useGear(Player player, Equipment equipment)
	{
		if(!owns(player, equipment))
			return;
		if(!"breathing".equals(equipment.behaviour))
		{
			CommandUtil.actionBar(player, ChatColor.GRAY + equipment.name + ": nothing to use it on here");
			return;
		}
		setBreathing(player, equipment, !BreathingGear.isWearing(player));
		SoundUtil.play(player.getWorld(), player.getLocation(), equipment.sound("toggle"), SoundCategory.PLAYERS, 1, 1);
	}

	private void setBreathing(Player player, Equipment equipment, boolean on)
	{
		PlayerInventory inventory = player.getInventory();
		inventory.setHelmet(on ? BreathingGear.tag(namedHelmet(equipment)) : defaultHelmet());
		int slot = EquipmentCatalogue.gearSlot;
		if(slot >= 0)
			inventory.setItem(slot, gearItem(player, equipment));
		player.updateInventory();
		CommandUtil.actionBar(player, on ? ChatColor.GREEN + equipment.name + " on" : ChatColor.GRAY + equipment.name + " off");
	}

	private void takeOffGear(Player player, Equipment gear)
	{
		if("breathing".equals(gear.behaviour) && BreathingGear.isWearing(player))
			player.getInventory().setHelmet(defaultHelmet());
	}

	private static ItemStack namedHelmet(Equipment equipment)
	{
		ItemStack helmet = new ItemStack(equipment.helmetMaterial);
		ItemMeta meta = helmet.getItemMeta();
		if(meta != null)
		{
			meta.setDisplayName(ChatColor.GOLD + equipment.name);
			helmet.setItemMeta(meta);
		}
		return helmet;
	}

	private static ItemStack defaultHelmet()
	{
		ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
		ItemMeta meta = helmet.getItemMeta();
		if(meta != null)
		{
			meta.setUnbreakable(true);
			helmet.setItemMeta(meta);
		}
		return helmet;
	}

	/** Uses one placeable from the held stack at {@code at}, facing {@code facingYaw}. */
	public void usePlaceable(Player player, Equipment equipment, Location at, float facingYaw)
	{
		if(!owns(player, equipment) || carried(player, equipment) <= 0)
			return;
		if(mines.countPlaced(player, equipment) >= equipment.maxPlaced)
		{
			CommandUtil.actionBar(player, ChatColor.RED + "You can't place more than " + equipment.maxPlaced + " " + equipment.name + "s");
			return;
		}
		if(mines.place(player, equipment, at, facingYaw))
			setStack(player, equipment, carried(player, equipment) - 1);
	}

	public void useTactical(Player player, Equipment equipment)
	{
		if(!owns(player, equipment) || carried(player, equipment) <= 0 || equipment.isMonkeyBomb())
			return;
		setStack(player, equipment, carried(player, equipment) - 1);
		tacticals.use(player, equipment);
	}

	/**
	 * Melee gate: false while the swing cooldown of the player's melee weapon (or the default knife) is still
	 * running, otherwise records the swing.
	 */
	public boolean tryMelee(Player player)
	{
		Equipment melee = getMelee(player);
		long cooldown = melee != null && melee.swingCooldownMs >= 0 ? melee.swingCooldownMs : EquipmentCatalogue.defaultMeleeCooldownMs;
		long now = System.currentTimeMillis();
		Long last = lastMelee.get(player.getUniqueId());
		if(cooldown > 0 && last != null && now - last < cooldown)
			return false;
		lastMelee.put(player.getUniqueId(), now);
		return true;
	}

	/** Knife damage for this player's melee: the base knife unless a melee upgrade overrides the one-shot round. */
	public float meleeDamage(Player player, float baseKnifeDamage)
	{
		Equipment melee = getMelee(player);
		if(melee == null)
			return baseKnifeDamage;
		float damage = melee.oneShotThroughRound > 0
				? com.theprogrammingturkey.comz.spawning.RoundSpawner.zombieHealth(melee.oneShotThroughRound)
				: baseKnifeDamage;
		return (float) (damage * melee.damageMultiplier);
	}

	/**
	 * BO refill: every player gets {@code grenadesPerRound} lethal grenades (up to the cap; round 1 is the starting
	 * pair), and owned placeables and tacticals with a {@code refill_per_round} are topped back up to it.
	 */
	public void onNewRound()
	{
		int grenadesPerRound = ConfigManager.getMainConfig().grenadesPerRound;
		for(Player player : game.getLivingPlayers())
		{
			com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager weapons = game.getPlayersWeapons(player);
			if(weapons != null)
				weapons.addGrenades(grenadesPerRound, ConfigManager.getMainConfig().maxGrenades);
			Loadout loadout = loadouts.get(player.getUniqueId());
			if(loadout == null)
				continue;
			for(Equipment equipment : new Equipment[]{loadout.placeable, loadout.tactical})
			{
				if(equipment == null || equipment.refillPerRound <= 0)
					continue;
				int count = carried(player, equipment);
				int target = Math.min(equipment.maxCarry, equipment.refillPerRound);
				if(count < target)
					setStack(player, equipment, target);
			}
		}
	}

	public void onDowned(Player player)
	{
		mines.removeOwnedBy(player.getUniqueId());
	}

	/** Bled out or left: everything the player owned and placed is gone, like in BO. */
	public void clearPlayer(Player player)
	{
		mines.removeOwnedBy(player.getUniqueId());
		loadouts.remove(player.getUniqueId());
		lastMelee.remove(player.getUniqueId());
	}

	public void reset()
	{
		for(Integer task : tasks)
			Bukkit.getScheduler().cancelTask(task);
		tasks.clear();
		for(Entity entity : entities)
			if(!entity.isDead())
				entity.remove();
		entities.clear();
		mines.removeAll();
		loadouts.clear();
		lastMelee.clear();
	}

	int schedule(long delay, long period, Runnable runnable)
	{
		int id = COMZombies.scheduleTask(delay, period, runnable);
		if(id != -1)
			tasks.add(id);
		return id;
	}

	void cancel(int task)
	{
		if(task == -1)
			return;
		Bukkit.getScheduler().cancelTask(task);
		tasks.remove(task);
	}

	void track(Entity entity)
	{
		if(entity != null)
			entities.add(entity);
	}

	void untrack(Entity entity)
	{
		if(entity == null)
			return;
		entities.remove(entity);
		if(!entity.isDead())
			entity.remove();
	}

	boolean isRunning()
	{
		return game.getStatus() == Game.GameStatus.INGAME;
	}


	/** Damage that one-shots zombies through {@code oneShotThroughRound}, else the flat {@code damage}. */
	static float damageOf(Equipment equipment)
	{
		if(equipment.oneShotThroughRound > 0)
			return com.theprogrammingturkey.comz.spawning.RoundSpawner.zombieHealth(equipment.oneShotThroughRound);
		return (float) equipment.damage;
	}

	static List<org.bukkit.entity.Mob> zombiesNear(Game game, Location centre, double radius)
	{
		List<org.bukkit.entity.Mob> out = new ArrayList<>();
		double radiusSq = radius * radius;
		for(org.bukkit.entity.Mob mob : new ArrayList<>(game.spawnManager.getEntities()))
		{
			if(mob.isDead() || mob.getWorld() != centre.getWorld())
				continue;
			if(mob.getLocation().distanceSquared(centre) <= radiusSq)
				out.add(mob);
		}
		return out;
	}
}
