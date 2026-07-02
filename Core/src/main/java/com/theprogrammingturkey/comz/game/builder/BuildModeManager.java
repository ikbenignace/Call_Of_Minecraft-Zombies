package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.CommandUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every active {@link BuildSession} and the entry/exit lifecycle of build mode: saving and giving
 * the toolbar inventory, restoring it on exit, and refreshing the hotbar item names / action bar so the
 * admin always sees the active tool and selected variant.
 */
public final class BuildModeManager
{
	public static final BuildModeManager INSTANCE = new BuildModeManager();

	private final Map<UUID, BuildSession> sessions = new ConcurrentHashMap<>();

	private BuildModeManager()
	{
	}

	public boolean isInBuild(Player player)
	{
		return sessions.containsKey(player.getUniqueId());
	}

	public BuildSession getSession(Player player)
	{
		return sessions.get(player.getUniqueId());
	}

	/** Enter build mode for {@code game}: stash the real inventory, switch to a safe gamemode, hand over the toolbox. */
	public void enter(Player player, Game game)
	{
		BuildSession session = new BuildSession(player, game);
		sessions.put(player.getUniqueId(), session);

		// ADVENTURE so the player can't accidentally break/place world blocks, plus flight so build mode
		// feels like a free-cam editor (true spectator can't use inventory or click, so we fake it with
		// adventure + flight). Clicks and inventory are intercepted by BuildModeListener.
		player.setGameMode(GameMode.ADVENTURE);
		player.setAllowFlight(true);
		player.setFlying(true);
		giveToolbar(player, session);
		player.getInventory().setHeldItemSlot(0);

		CommandUtil.sendMessageToPlayer(player, ChatColor.GREEN + "" + ChatColor.BOLD + "Build mode" + ChatColor.GREEN + " for arena " + ChatColor.AQUA + game.getName());
		CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Scroll = switch tool, " + ChatColor.YELLOW + "F" + ChatColor.GRAY + " = cycle variant, right-click = place, left-click = remove.");
		CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/zombies build" + ChatColor.GRAY + " again to save & exit.");
		actionBar(player, currentToolText(session, 0));
	}

	/** Save & exit: restore the player's world state, despawn previews, persist the arena. */
	public void exit(Player player)
	{
		BuildSession session = sessions.remove(player.getUniqueId());
		if(session == null)
			return;
		warnRoomProgressionIssues(player, session);
		session.removeAllPreviews();
		session.restore(player);
		GameManager.INSTANCE.saveAllGames();
		CommandUtil.sendMessageToPlayer(player, ChatColor.GREEN + "Build mode saved & exited.");
	}

	/**
	 * Surface room-progression mistakes on exit: an unfinished door selection (discarded), and any door
	 * that gates a room with no zombie spawns (a "dead room" that never produces zombies once opened).
	 */
	private void warnRoomProgressionIssues(Player player, BuildSession session)
	{
		if(session.getDoorInProgress() != null)
			CommandUtil.sendMessageToPlayer(player, ChatColor.YELLOW + "Note: an unfinished door selection was discarded (never sneak-finalized).");

		for(com.theprogrammingturkey.comz.game.features.Door door : session.getGame().doorManager.getDoors())
			if(door.getSpawnsInRoomDoorLeadsTo().isEmpty())
				CommandUtil.sendMessageToPlayer(player, ChatColor.GOLD + "Warning: door " + door.doorID + " gates a room with no zombie spawns. Use the Spawn tool with that room active.");
	}

	/** Replace the player's inventory with the 9 tool items (one per hotbar slot). */
	public void giveToolbar(Player player, BuildSession session)
	{
		player.getInventory().clear();
		BuildTool[] tools = BuildTool.values();
		for(int slot = 0; slot < tools.length && slot < 9; slot++)
			player.getInventory().setItem(slot, toolItem(tools[slot], session));
		player.updateInventory();
	}

	/** Refresh a single tool's item in place (after a variant cycle) so its name shows the new pick. */
	public void refreshToolItem(Player player, BuildSession session, BuildTool tool)
	{
		player.getInventory().setItem(tool.ordinal(), toolItem(tool, session));
		player.updateInventory();
	}

	private ItemStack toolItem(BuildTool tool, BuildSession session)
	{
		List<String> labels = tool.variantLabels();
		session.clampVariant(tool, labels.size());

		ItemStack item = new ItemStack(tool.getIcon());
		ItemMeta meta = item.getItemMeta();
		if(meta != null)
		{
			meta.setDisplayName(toolDisplayName(tool, variantFor(tool, session), session));
			item.setItemMeta(meta);
		}
		return item;
	}

	/**
	 * The "variant" shown for a tool. Room-aware tools (Spawn, Door, Barrier) show the active room
	 * instead of a generic variant index; the others show their cycled variant label.
	 */
	private String variantFor(BuildTool tool, BuildSession session)
	{
		if(tool == BuildTool.SPAWN || tool == BuildTool.DOOR || tool == BuildTool.BARRIER)
			return session.roomLabel();
		return tool.variantLabel(session.getVariant(tool));
	}

	private String toolDisplayName(BuildTool tool, String variant, BuildSession session)
	{
		String base = ChatColor.GOLD + "" + ChatColor.BOLD + tool.getDisplayName();
		if(variant == null)
			return base;
		return base + ChatColor.GRAY + " ▶ " + ChatColor.AQUA + variant;
	}

	/** The action-bar string for the tool in {@code slot} with its current variant / active room. */
	public String currentToolText(BuildSession session, int slot)
	{
		BuildTool tool = BuildTool.fromSlot(slot);
		if(tool == null)
			return "";
		return ChatColor.stripColor(toolDisplayName(tool, variantFor(tool, session), session));
	}

	public void actionBar(Player player, String text)
	{
		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
	}
}
