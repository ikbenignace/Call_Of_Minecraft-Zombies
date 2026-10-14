package com.theprogrammingturkey.comz.game.dev;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Chest GUI with one-click developer actions; routed by {@code DevMenuListener}. */
public class DevMenu implements InventoryHolder
{
	private static final int SLOT_GOD = 19;
	private static final int SLOT_POWERUPS_START = 28;
	private static final int SLOT_POWERUPS_SECOND_ROW = 37;
	private static final int POWERUPS_FIRST_ROW_SIZE = 7;

	private final Player player;
	private final Game game;
	private final Inventory inv;
	private final Map<Integer, Runnable> actions = new HashMap<>();

	public DevMenu(Player player, Game game)
	{
		this.player = player;
		this.game = game;
		this.inv = Bukkit.createInventory(this, 54, ChatColor.DARK_RED + "COM:Z Developer");
		render();
	}

	@Override
	public Inventory getInventory()
	{
		return inv;
	}

	private void render()
	{
		actions.clear();
		inv.clear();

		ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
		for(int i = 0; i < inv.getSize(); i++)
			inv.setItem(i, filler);

		button(10, Material.GOLD_NUGGET, "+1000 points", () -> DevTools.addPoints(player, player, 1000));
		button(11, Material.GOLD_INGOT, "+10000 points", () -> DevTools.addPoints(player, player, 10000));
		button(12, Material.GOLD_BLOCK, "+100000 points", () -> DevTools.addPoints(player, player, 100000));
		button(14, Material.CLOCK, "Skip round", () -> DevTools.skipRound(player, game));
		button(15, Material.COMPASS, "Round +5", () -> DevTools.setRound(player, game, game.getWave() + 5));
		button(16, Material.ZOMBIE_HEAD, "Kill all zombies", () -> DevTools.killAll(player, game));

		boolean god = DevTools.isGod(player);
		button(SLOT_GOD, god ? Material.DIAMOND_CHESTPLATE : Material.LEATHER_CHESTPLATE,
				"God mode: " + (god ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"), () -> DevTools.toggleGod(player));
		button(20, Material.POTION, "All perks", () -> DevTools.giveAllPerks(player, game));
		button(21, Material.ARROW, "Max ammo", () -> DevTools.maxAmmo(player, game));
		button(22, Material.ANVIL, "Pack-a-Punch held gun", () -> DevTools.packAPunch(player, game));
		button(23, Material.REDSTONE_LAMP, "Power on", () -> DevTools.power(player, game));
		button(24, Material.SKELETON_SKULL, "Down yourself", () ->
		{
			DevTools.down(player, game);
			player.closeInventory();
		});
		button(25, Material.TOTEM_OF_UNDYING, "Revive yourself", () -> DevTools.revive(player, player, game));
		button(26, Material.CHEST, "Move mystery box", () -> DevTools.moveBox(player, game));

		int index = 0;
		for(PowerUp powerUp : PowerUp.values())
		{
			if(powerUp == PowerUp.NONE)
				continue;
			int slot = index < POWERUPS_FIRST_ROW_SIZE
					? SLOT_POWERUPS_START + index
					: SLOT_POWERUPS_SECOND_ROW + index - POWERUPS_FIRST_ROW_SIZE;
			button(slot, powerUp.getMaterial(), "Drop " + powerUp.getDisplay(), () -> DevTools.spawnPowerUp(player, game, powerUp));
			index++;
		}
	}

	private void button(int slot, Material material, String name, Runnable action)
	{
		inv.setItem(slot, icon(material, ChatColor.AQUA + name));
		actions.put(slot, action);
	}

	private ItemStack icon(Material material, String name)
	{
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		if(meta != null)
		{
			meta.setDisplayName(name);
			if(!name.trim().isEmpty())
				meta.setLore(Collections.singletonList(ChatColor.DARK_GRAY + "Click to apply"));
			stack.setItemMeta(meta);
		}
		return stack;
	}

	public void handleClick(int slot)
	{
		if(GameManager.INSTANCE.getGame(player) != game)
		{
			player.closeInventory();
			return;
		}
		Runnable action = actions.get(slot);
		if(action == null)
			return;
		action.run();
		// The action may have closed the inventory (down); only redraw while it is still showing.
		if(player.getOpenInventory().getTopInventory().getHolder() == this)
			render();
	}
}
