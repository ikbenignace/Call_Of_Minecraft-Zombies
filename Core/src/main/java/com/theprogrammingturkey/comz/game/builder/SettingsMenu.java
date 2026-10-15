package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.managers.PerkPoolManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Chest-GUI editor for a single arena's settings (`/zombies settings &lt;arena&gt;`). Each slot is one
 * setting: left-click raises / toggles / cycles it, right-click lowers it, shift changes ints by 5.
 * The menu is its own {@link InventoryHolder} so {@code SettingsMenuListener} can recognise and route
 * clicks to it. Changes write straight to the {@link Game} and persist on close.
 */
public class SettingsMenu implements InventoryHolder
{
	private static final int SLOT_MIN = 10, SLOT_MAX = 11, SLOT_TEDDY = 12, SLOT_DOG = 13,
			SLOT_GUN = 14, SLOT_AMMO = 15, SLOT_NIGHT = 16, SLOT_MULTIBOX = 22,
			SLOT_GRAVITY = 19, SLOT_JUMP = 20, SLOT_FOG = 21, SLOT_PAP_LOCKED = 24, SLOT_PERK_POOL = 25, SLOT_POOL_REVEAL = 26, SLOT_POWER_SWITCHES = 17;

	/** Opens {@link SpecialEnemiesMenu}; handled by the listener because it needs the clicking player. */
	public static final int SLOT_SPECIALS = 23;

	private final Game game;
	private final Inventory inv;

	public SettingsMenu(Game game)
	{
		this.game = game;
		this.inv = Bukkit.createInventory(this, 27, ChatColor.DARK_AQUA + "Settings: " + game.getName());
		render();
	}

	@Override
	public Inventory getInventory()
	{
		return inv;
	}

	private void render()
	{
		inv.setItem(SLOT_MIN, item(Material.LIME_DYE, "Min players", game.minPlayers, "left +1 / right -1 (shift 5)"));
		inv.setItem(SLOT_MAX, item(Material.RED_DYE, "Max players", game.maxPlayers, "left +1 / right -1 (shift 5)"));
		inv.setItem(SLOT_TEDDY, item(Material.PINK_WOOL, "Teddy-bear %", game.getTeddyBearPercent(), "chance the box teddy ends the box run"));
		inv.setItem(SLOT_DOG, item(Material.BONE, "Dog round every X", game.getDogRoundEveryX(), "0 = never"));
		inv.setItem(SLOT_GUN, item(Material.IRON_HOE, "Starting gun", game.getStartingGun(), "left/right = cycle guns"));
		inv.setItem(SLOT_AMMO, toggle(Material.ARROW, "Max-ammo refills clip", game.doesMaxAmmoReplenishClip()));
		inv.setItem(SLOT_NIGHT, toggle(Material.CLOCK, "Force night", game.isForceNight()));
		inv.setItem(SLOT_MULTIBOX, toggle(Material.CHEST, "Multiple mystery boxes", game.boxManager.isMultiBox()));
		inv.setItem(SLOT_GRAVITY, item(Material.FEATHER, "Gravity", decimal(game.environmentManager.getGravity()), "1.0 = normal, 0.4 = moon; left +0.1 / right -0.1 (shift 0.5)"));
		inv.setItem(SLOT_JUMP, item(Material.RABBIT_FOOT, "Jump boost", decimal(game.environmentManager.getJumpBoost()), "jump height multiplier; left +0.1 / right -0.1 (shift 0.5)"));
		inv.setItem(SLOT_FOG, toggle(Material.BLACK_STAINED_GLASS, "Fog (darkness)", game.environmentManager.hasFog()));
		inv.setItem(SLOT_SPECIALS, item(Material.ZOMBIE_HEAD, "Special enemies", game.specialEnemyManager.getRules().size() + " rule(s)", "click to edit"));
		inv.setItem(SLOT_PAP_LOCKED, toggle(Material.ENCHANTING_TABLE, "Pack-a-Punch locked until a trigger", game.isPackAPunchLockedByDefault()));
		inv.setItem(SLOT_PERK_POOL, item(Material.BREWING_STAND, "Perk placement", game.perkPoolManager.getMode().key(),
				"fixed / random_on_open / random_on_start (" + game.perkPoolManager.getSpots().size() + " pool spots); click to switch"));
		inv.setItem(SLOT_POWER_SWITCHES, item(Material.LEVER, "Power switches", game.getPowerSwitches(), "switches to press before the power is on; left +1 / right -1"));
		inv.setItem(SLOT_POOL_REVEAL, item(Material.OAK_DOOR, "Pooled perks appear on", game.perkPoolManager.isRevealOnDoor() ? "door open" : "trigger only",
				"click to switch"));
	}

	/** Apply a click on {@code slot}; returns true if it was a settings slot (so the caller re-renders). */
	public boolean handleClick(int slot, ClickType click)
	{
		int step = click.isShiftClick() ? 5 : 1;
		boolean up = click.isLeftClick();
		switch(slot)
		{
			case SLOT_MIN:
				game.minPlayers = Math.max(1, Math.min(game.maxPlayers, game.minPlayers + (up ? step : -step)));
				break;
			case SLOT_MAX:
				game.maxPlayers = Math.max(Math.max(1, game.minPlayers), game.maxPlayers + (up ? step : -step));
				break;
			case SLOT_TEDDY:
				game.setTeddyBearPercent(game.getTeddyBearPercent() + (up ? step : -step));
				break;
			case SLOT_DOG:
				game.setDogRoundEveryX(game.getDogRoundEveryX() + (up ? step : -step));
				break;
			case SLOT_GUN:
				game.setStartingGun(cycleGun(game.getStartingGun(), up));
				break;
			case SLOT_AMMO:
				game.setMaxAmmoReplenishClip(!game.doesMaxAmmoReplenishClip());
				break;
			case SLOT_NIGHT:
				game.setForceNight(!game.isForceNight());
				break;
			case SLOT_MULTIBOX:
				game.boxManager.setMultiBox(!game.boxManager.isMultiBox());
				break;
			case SLOT_GRAVITY:
				game.environmentManager.setGravity(game.environmentManager.getGravity() + (up ? step : -step) * 0.1);
				break;
			case SLOT_JUMP:
				game.environmentManager.setJumpBoost(game.environmentManager.getJumpBoost() + (up ? step : -step) * 0.1);
				break;
			case SLOT_FOG:
				game.environmentManager.setFog(!game.environmentManager.hasFog());
				break;
			case SLOT_PAP_LOCKED:
				game.setPackAPunchLocked(!game.isPackAPunchLockedByDefault());
				break;
			case SLOT_PERK_POOL:
				game.perkPoolManager.setMode(cyclePoolMode(game.perkPoolManager.getMode(), up));
				break;
			case SLOT_POWER_SWITCHES:
				game.setPowerSwitches(game.getPowerSwitches() + (up ? step : -step));
				break;
			case SLOT_POOL_REVEAL:
				game.perkPoolManager.setRevealOnDoor(!game.perkPoolManager.isRevealOnDoor());
				break;
			default:
				return false;
		}
		render();
		return true;
	}

	public Game getGame()
	{
		return game;
	}

	/** Persist the arena after edits (called when the menu closes). */
	public void save()
	{
		GameManager.INSTANCE.saveAllGames();
	}

	private String cycleGun(String current, boolean forward)
	{
		List<BaseGun> guns = WeaponManager.getBuyableGuns();
		if(guns.isEmpty())
			return current;
		int idx = 0;
		for(int i = 0; i < guns.size(); i++)
			if(guns.get(i).getName().equalsIgnoreCase(current))
			{
				idx = i;
				break;
			}
		idx = (idx + (forward ? 1 : guns.size() - 1)) % guns.size();
		return guns.get(idx).getName();
	}

	private static PerkPoolManager.Mode cyclePoolMode(PerkPoolManager.Mode current, boolean forward)
	{
		PerkPoolManager.Mode[] modes = PerkPoolManager.Mode.values();
		return modes[(current.ordinal() + (forward ? 1 : modes.length - 1)) % modes.length];
	}

	private static String decimal(double value)
	{
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private ItemStack item(Material mat, String name, Object value, String hint)
	{
		ItemStack it = new ItemStack(mat);
		ItemMeta meta = it.getItemMeta();
		if(meta != null)
		{
			meta.setDisplayName(ChatColor.AQUA + name + ChatColor.GRAY + ": " + ChatColor.YELLOW + value);
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + hint));
			it.setItemMeta(meta);
		}
		return it;
	}

	private ItemStack toggle(Material mat, String name, boolean on)
	{
		ItemStack it = new ItemStack(mat);
		ItemMeta meta = it.getItemMeta();
		if(meta != null)
		{
			meta.setDisplayName(ChatColor.AQUA + name + ChatColor.GRAY + ": " + (on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
			meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + "click to toggle"));
			it.setItemMeta(meta);
		}
		return it;
	}
}
