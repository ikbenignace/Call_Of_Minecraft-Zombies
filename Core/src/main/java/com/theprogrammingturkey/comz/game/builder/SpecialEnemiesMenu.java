package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.spawning.special.SpecialEnemyCatalogue;
import com.theprogrammingturkey.comz.spawning.special.SpecialEnemyRule;
import com.theprogrammingturkey.comz.spawning.special.SpecialEnemyType;
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

/**
 * Chest-GUI for an arena's {@code special_enemies} rules: one column per catalogue type (first nine types), rows
 * for enabled, mode, value, max alive, first round and own round. Only the first rule of a type is edited here;
 * extra rules written by hand in arenas.json are kept.
 */
public class SpecialEnemiesMenu implements InventoryHolder
{
	private static final int ROW_ENABLED = 0, ROW_MODE = 1, ROW_VALUE = 2, ROW_MAX = 3, ROW_FIRST = 4, ROW_OWN = 5;

	private final Game game;
	private final Inventory inv;
	private final List<SpecialEnemyType> types;

	public SpecialEnemiesMenu(Game game)
	{
		this.game = game;
		this.types = SpecialEnemyCatalogue.all();
		this.inv = Bukkit.createInventory(this, 54, ChatColor.DARK_RED + "Special enemies: " + game.getName());
		render();
	}

	@Override
	public Inventory getInventory()
	{
		return inv;
	}

	private void render()
	{
		inv.clear();
		for(int col = 0; col < Math.min(9, types.size()); col++)
		{
			SpecialEnemyType type = types.get(col);
			SpecialEnemyRule rule = game.specialEnemyManager.getRule(type.id);
			boolean on = rule != null;
			inv.setItem(slot(ROW_ENABLED, col), item(on ? Material.ZOMBIE_HEAD : Material.SKELETON_SKULL,
					ChatColor.AQUA + type.displayName + ChatColor.GRAY + ": " + (on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"),
					"behaviour: " + type.behaviour.id(), "click to toggle"));
			if(!on)
			{
				for(int row = ROW_MODE; row <= ROW_OWN; row++)
					inv.setItem(slot(row, col), item(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "disabled"));
				continue;
			}
			inv.setItem(slot(ROW_MODE, col), item(Material.COMPARATOR, value("Mode", rule.mode.id()), "left/right = cycle"));
			inv.setItem(slot(ROW_VALUE, col), item(Material.GOLD_NUGGET, value("Value", rule.value), valueHint(rule.mode), "left +1 / right -1 (shift 5)"));
			inv.setItem(slot(ROW_MAX, col), item(Material.ARMOR_STAND, value("Max alive", rule.maxAlive), "left +1 / right -1 (shift 5)"));
			inv.setItem(slot(ROW_FIRST, col), item(Material.CLOCK, value("First round", rule.firstRound), "left +1 / right -1 (shift 5)"));
			inv.setItem(slot(ROW_OWN, col), item(Material.NETHER_STAR, value("Own round", rule.isOwnRound() ? "YES" : "NO"),
					"dedicated round instead of mixing in", "replaced dog rounds always are"));
		}
	}

	public void handleClick(int slot, ClickType click)
	{
		int row = slot / 9;
		int col = slot % 9;
		if(col >= types.size() || row > ROW_OWN)
			return;
		SpecialEnemyType type = types.get(col);
		SpecialEnemyRule rule = game.specialEnemyManager.getRule(type.id);
		int step = (click.isShiftClick() ? 5 : 1) * (click.isLeftClick() ? 1 : -1);

		if(row == ROW_ENABLED)
			game.specialEnemyManager.setEnabled(type, rule == null);
		else if(rule == null)
			return;
		else if(row == ROW_MODE)
		{
			SpecialEnemyRule.Mode mode = rule.mode;
			int cycles = click.isLeftClick() ? 1 : SpecialEnemyRule.Mode.values().length - 1;
			for(int i = 0; i < cycles; i++)
				mode = mode.next();
			rule.mode = mode;
			rule.value = SpecialEnemyRule.defaultValue(mode);
		}
		else if(row == ROW_VALUE)
			rule.value = clampValue(rule.mode, rule.value + step);
		else if(row == ROW_MAX)
			rule.maxAlive = Math.max(1, rule.maxAlive + step);
		else if(row == ROW_FIRST)
			rule.firstRound = Math.max(1, rule.firstRound + step);
		else if(row == ROW_OWN)
			rule.ownRound = !rule.ownRound;
		render();
	}

	public void save()
	{
		GameManager.INSTANCE.saveAllGames();
	}

	private static int clampValue(SpecialEnemyRule.Mode mode, int value)
	{
		if(mode == SpecialEnemyRule.Mode.CHANCE_PER_ROUND || mode == SpecialEnemyRule.Mode.REPLACES_DOG_ROUND)
			return Math.max(1, Math.min(100, value));
		return Math.max(1, value);
	}

	private static String valueHint(SpecialEnemyRule.Mode mode)
	{
		switch(mode)
		{
			case EVERY_X_ROUNDS:
				return "every X rounds from the first round";
			case CHANCE_PER_ROUND:
				return "% chance each round";
			case REPLACES_DOG_ROUND:
				return "% chance a dog round becomes this round";
			default:
				return "the round it appears on";
		}
	}

	private static int slot(int row, int col)
	{
		return row * 9 + col;
	}

	private static String value(String name, Object value)
	{
		return ChatColor.AQUA + name + ChatColor.GRAY + ": " + ChatColor.YELLOW + value;
	}

	private static ItemStack item(Material mat, String name, String... lore)
	{
		ItemStack it = new ItemStack(mat);
		ItemMeta meta = it.getItemMeta();
		if(meta != null)
		{
			meta.setDisplayName(name);
			String[] lines = new String[lore.length];
			for(int i = 0; i < lore.length; i++)
				lines[i] = ChatColor.DARK_GRAY + lore[i];
			meta.setLore(Arrays.asList(lines));
			it.setItemMeta(meta);
		}
		return it;
	}
}
