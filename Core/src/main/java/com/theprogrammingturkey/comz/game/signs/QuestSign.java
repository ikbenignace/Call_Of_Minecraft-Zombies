package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

/**
 * Completes the {@code interact} trigger step whose {@code sign} id equals line 2 (how quests saved before
 * triggers keep working). Clicks on a step that is not accepted yet are ignored.
 *
 * <pre>
 * [Zombies]
 * Quest
 * &lt;step id&gt;
 * &lt;optional label&gt;
 * </pre>
 */
public class QuestSign implements IGameSign
{
	@Override
	public void onBreak(Game game, Player player, Location location)
	{
		// Nothing to clean up: quest signs hold no per-sign state — they only reference a
		// step id defined by the arena's quest. Method present for IGameSign symmetry.
	}

	@Override
	public void onInteract(Game game, Player player, Location location, String[] lines)
	{
		if(game == null || !GameManager.INSTANCE.isPlayerInGame(player))
			return;

		String stepId = ChatColor.stripColor(lines[2]).trim();
		if(stepId.isEmpty())
			return;

		game.triggerManager.onSignInteract(stepId, player);
	}

	@Override
	public void onChange(Game game, Player player, SignChangeEvent event)
	{
		String stepId = ChatColor.stripColor(event.getLine(2));
		if(stepId == null || stepId.trim().isEmpty())
		{
			event.setLine(0, ChatColor.RED + "" + ChatColor.BOLD + "Missing");
			event.setLine(1, ChatColor.RED + "" + ChatColor.BOLD + "step id!");
			event.setLine(2, "");
			event.setLine(3, "");
			return;
		}

		stepId = stepId.trim().toLowerCase();
		event.setLine(0, ChatColor.RED + "[Zombies]");
		event.setLine(1, ChatColor.LIGHT_PURPLE + "Quest");
		event.setLine(2, stepId);
		event.setLine(3, ChatColor.GRAY + "right-click");
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
