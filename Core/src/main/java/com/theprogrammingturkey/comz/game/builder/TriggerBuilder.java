package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.Transport;
import com.theprogrammingturkey.comz.game.features.Trigger;
import com.theprogrammingturkey.comz.game.features.TriggerStep;
import com.theprogrammingturkey.comz.game.features.TriggerTarget;
import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.game.managers.PerkPoolManager;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * Feature-tool editor for triggers and perk pool spots.
 * <ul>
 *     <li><b>Trigger</b>: right-click adds what F has selected (a step kind or a target kind) using the clicked
 *     block, zone, transport, door or teleporter; left-click undoes; Sneak + F (or sneak-right-click) finishes and
 *     opens the chat options. Without a trigger in progress, left-click or sneak-right-click edits the trigger whose
 *     step is nearest.</li>
 *     <li><b>Song Egg</b>: each right-click hides a teddy bear; the third one finishes a "find them all, play the
 *     song" trigger.</li>
 *     <li><b>Perk Pool Spot</b>: right-click marks where a pooled perk machine may appear, left-click removes one.</li>
 * </ul>
 */
public final class TriggerBuilder
{
	public static final String TRIGGER_VARIANT = "Trigger";
	public static final String SONG_VARIANT = "Song Egg";
	public static final String POOL_VARIANT = "Perk Pool Spot";
	private static final int SONG_ITEMS = 3;
	private static final double EDIT_REACH = 4.0D;
	private static final double VIEW_DISTANCE_SQ = 64 * 64;

	private TriggerBuilder()
	{
	}

	/** The trigger-related variant the player holds on the Feature tool, or null. */
	public static String heldVariant(Player player, BuildSession session)
	{
		if(BuildTool.fromSlot(player.getInventory().getHeldItemSlot()) != BuildTool.FEATURE)
			return null;
		String label = BuildTool.FEATURE.variantLabel(session.getVariant(BuildTool.FEATURE));
		return TRIGGER_VARIANT.equals(label) || SONG_VARIANT.equals(label) || POOL_VARIANT.equals(label) ? label : null;
	}

	public static void interact(Player player, BuildSession session, String variant, Block clicked, BlockFace face, Action action, boolean sneaking)
	{
		if(action == Action.RIGHT_CLICK_BLOCK && session.onPlaceCooldown())
			return;
		if(POOL_VARIANT.equals(variant))
		{
			poolInteract(player, session, clicked, face, action);
			return;
		}
		TriggerDraft draft = session.getTriggerDraft();
		if(action == Action.LEFT_CLICK_BLOCK)
		{
			if(draft.building != null)
				undo(player, session);
			else
				editNearest(player, session, clicked);
			return;
		}
		if(action != Action.RIGHT_CLICK_BLOCK)
			return;
		if(sneaking)
		{
			if(draft.building != null)
				finish(player, session);
			else
				editNearest(player, session, clicked);
			return;
		}
		if(SONG_VARIANT.equals(variant))
			addSongItem(player, session, clicked);
		else
			addElement(player, session, clicked);
	}

	/** F while a trigger is being built: cycle what the next click adds; Sneak + F finishes. Returns true when consumed. */
	public static boolean onSwap(Player player, BuildSession session)
	{
		String variant = heldVariant(player, session);
		TriggerDraft draft = session.getTriggerDraft();
		if(variant == null || POOL_VARIANT.equals(variant) || draft.building == null)
			return false;
		if(player.isSneaking())
		{
			finish(player, session);
			return true;
		}
		draft.cycleElement();
		CommandUtil.actionBar(player, ChatColor.AQUA + "Next click adds " + ChatColor.WHITE + draft.elementLabel() + ChatColor.GRAY + " (F = next, Sneak + F = finish)");
		return true;
	}

	// ---------------------------------------------------------------- trigger building

	private static Trigger startTrigger(Player player, BuildSession session, boolean songPreset)
	{
		Trigger trigger = new Trigger(session.getGame().triggerManager.nextId());
		session.getTriggerDraft().start(trigger, songPreset);
		if(!songPreset)
			msg(player, ChatColor.GOLD + "New trigger " + trigger.getId() + ChatColor.GRAY + ": right-click adds the selected step/target, F = next kind, left-click = undo, Sneak + F = finish.");
		return trigger;
	}

	private static void addElement(Player player, BuildSession session, Block clicked)
	{
		TriggerDraft draft = session.getTriggerDraft();
		boolean fresh = draft.building == null;
		Trigger trigger = fresh ? startTrigger(player, session, false) : draft.building;
		String error;
		if(draft.stepKind() != null)
		{
			TriggerStep step = new TriggerStep(draft.stepKind());
			error = configureStep(session.getGame(), step, clicked);
			if(error == null)
			{
				trigger.getSteps().add(step);
				draft.history.add(true);
				feedback(player, clicked, ChatColor.GREEN + "Step " + trigger.getSteps().size() + ": " + step.describe());
				stepHint(player, trigger.getSteps().size(), step);
			}
		}
		else
		{
			TriggerTarget target = new TriggerTarget(draft.targetKind());
			error = configureTarget(player, session.getGame(), target, clicked);
			if(error == null)
			{
				trigger.getTargets().add(target);
				draft.history.add(false);
				feedback(player, clicked, ChatColor.YELLOW + "Target " + trigger.getTargets().size() + ": " + target.describe());
				targetHint(player, trigger.getTargets().size(), target);
			}
		}
		if(error != null)
		{
			msg(player, ChatColor.RED + error);
			if(fresh)
				draft.clear();
		}
	}

	private static void addSongItem(Player player, BuildSession session, Block clicked)
	{
		TriggerDraft draft = session.getTriggerDraft();
		Trigger trigger = draft.building;
		if(trigger == null)
		{
			trigger = startTrigger(player, session, true);
			trigger.setName("Song easter egg");
			trigger.setOrdered(false);
			trigger.getTargets().add(new TriggerTarget(TriggerTarget.Kind.PLAY_SONG));
			draft.history.add(false);
			msg(player, ChatColor.GOLD + "Song easter egg " + trigger.getId() + ChatColor.GRAY + ": right-click " + SONG_ITEMS
					+ " hiding spots for the teddy bears (left-click = undo, Sneak + F = finish early).");
		}
		TriggerStep step = new TriggerStep(TriggerStep.Kind.INTERACT);
		step.setPoint(clicked.getLocation().toVector());
		step.setVisual(TriggerStep.TEDDY_VISUAL);
		trigger.getSteps().add(step);
		draft.history.add(true);
		feedback(player, clicked, ChatColor.GREEN + "Teddy " + trigger.getSteps().size() + (draft.songPreset ? "/" + SONG_ITEMS : "") + " placed");
		if(draft.songPreset && trigger.getSteps().size() >= SONG_ITEMS)
			finish(player, session);
	}

	/** Fills a new step from the click; returns an error message or null. */
	private static String configureStep(Game game, TriggerStep step, Block clicked)
	{
		switch(step.getKind())
		{
			case INTERACT:
				step.setPoint(clicked.getLocation().toVector());
				return null;
			case STAND_ON:
			{
				Zone zone = zoneAt(game, clicked);
				if(zone != null)
					step.setZone(zone.getId());
				else
					step.setPoint(clicked.getLocation().toVector());
				return null;
			}
			case KILL_IN_ZONE:
			{
				Zone zone = zoneAt(game, clicked);
				if(zone == null)
					return "Click inside a zone (make one with a zone preset of the Feature tool first).";
				step.setZone(zone.getId());
				return null;
			}
			case RIDE_TRANSPORT:
			{
				Transport transport = game.transportManager.findNear(clicked.getLocation().add(0.5, 1, 0.5).toVector(), EDIT_REACH);
				if(transport == null)
					return "Click next to a transport's path or call point.";
				step.setTransport(transport.getId());
				return null;
			}
			case POWER_ON:
				return game.hasPower() ? null : "This arena has no power switch; place one with the Machine tool first.";
			case TRIGGER_DONE:
			{
				Trigger other = triggerNear(game, clicked);
				if(other == null)
					return "Click next to a step of the trigger that has to be done first.";
				step.setTrigger(other.getId());
				return null;
			}
			default:
				return null;
		}
	}

	private static void stepHint(Player player, int number, TriggerStep step)
	{
		switch(step.getKind())
		{
			case KILL_IN_ZONE:
				msg(player, ChatColor.GRAY + "Needs " + step.getAmount() + " kills; change it later with s" + number + ".count=<n>.");
				break;
			case ROUND_REACHED:
				msg(player, ChatColor.GRAY + "Round " + step.getAmount() + "; change it later with s" + number + ".round=<n>.");
				break;
			case STAND_ON:
				msg(player, ChatColor.GRAY + "1 player; change it with s" + number + ".count=<n|all>, and use 'simultaneous' to require every plate at once.");
				break;
			case INTERACT:
				msg(player, ChatColor.GRAY + "Make it a hack with s" + number + ".gear=<equipment, e.g. Hacker> and s" + number + ".hold=<seconds>.");
				break;
			case ROUND_CHANCE:
				msg(player, ChatColor.GRAY.toString() + step.getAmount() + "% each round; change it with s" + number + ".chance=<percent>.");
				break;
			default:
		}
	}

	private static void targetHint(Player player, int number, TriggerTarget target)
	{
		String t = "t" + number;
		switch(target.getKind())
		{
			case CLONE_BLOCKS:
				msg(player, ChatColor.GRAY + "Copies that zone's blocks. Where to: " + t + ".to=here|x,y,z; " + t + ".mode=replace|masked|move|masked move; "
						+ t + ".filter=<block|#tag>.");
				break;
			case FILL_BLOCKS:
				msg(player, ChatColor.GRAY + "Fills that zone with air. " + t + ".block=<block state>; " + t + ".filter=<block|#tag> to replace only that.");
				break;
			case PLAY_SOUND:
				msg(player, ChatColor.GRAY + "Set the sound with " + t + ".sound=<sound or sounds.json key> and " + t + ".volume=<n>.");
				break;
			case SET_ZONE:
				msg(player, ChatColor.GRAY + "Switches that zone off when it runs; " + t + ".on=true switches it back on.");
				break;
			case GIVE_POINTS_TO_PLAYER:
				msg(player, ChatColor.GRAY.toString() + target.getAmount() + " points to whoever finishes it; change with " + t + ".points=<n>.");
				break;
			default:
		}
		if(target.getKind() != TriggerTarget.Kind.AFTER)
			msg(player, ChatColor.DARK_GRAY + "Timeline: " + t + ".delay=<seconds> runs it that long after the trigger fires.");
	}

	/** Fills a new target from the click; returns an error message or null. */
	private static String configureTarget(Player player, Game game, TriggerTarget target, Block clicked)
	{
		switch(target.getKind())
		{
			case OPEN_DOOR:
			{
				Door door = doorNear(game, clicked);
				if(door == null)
					return "Click a door block (or right next to one).";
				target.setValue(door.doorID);
				return null;
			}
			case ENABLE_PACK_A_PUNCH:
				if(!game.isPackAPunchLockedByDefault())
				{
					game.setPackAPunchLocked(true);
					msg(player, ChatColor.GRAY + "Pack-a-Punch is now locked at game start in this arena (setting pap_locked).");
				}
				return null;
			case ENABLE_TELEPORTER:
			{
				String name = teleporterNear(game, clicked);
				if(name == null)
					return "Click a teleporter sign or its destination (teleporters: " + game.teleporterManager.getTeleporters().keySet() + ").";
				target.setValue(name);
				return null;
			}
			case GIVE_POWERUP:
				target.setPoint(clicked.getLocation().toVector());
				return null;
			case SPAWN_PERK:
				if(game.perkPoolManager.getSpots().isEmpty())
					msg(player, ChatColor.YELLOW + "No perk pool spots yet: place them with the " + POOL_VARIANT + " variant.");
				return null;
			case TURN_ON_POWER:
				return game.hasPower() ? null : "This arena has no power switch; place one with the Machine tool first.";
			case PLACE_BLOCKS:
				target.getBlocks().add(new TriggerTarget.PlacedBlock(clicked.getX(), clicked.getY(), clicked.getZ(), clicked.getBlockData().getAsString()));
				msg(player, ChatColor.GRAY + "Saved this block as it is now; take it away again and it appears when the trigger fires (restored at game end).");
				return null;
			case AFTER:
				return "A timeline step holds other targets: add the target itself, then type t<N>.delay=<seconds>.";
			case CLONE_BLOCKS:
			case FILL_BLOCKS:
			{
				Zone zone = zoneAt(game, clicked);
				if(zone == null)
					return "Click inside a zone around the blocks (mark it with a zone preset of the Feature tool first).";
				target.setBox(zone.getMin().toVector(), zone.getMax().toVector());
				if(target.getKind() == TriggerTarget.Kind.CLONE_BLOCKS)
					target.setPoint(zone.getMin().toVector());
				return null;
			}
			case PLAY_SOUND:
				target.setPoint(clicked.getLocation().toVector());
				return null;
			case RESET_TRIGGER:
			{
				Trigger other = triggerNear(game, clicked);
				if(other == null)
					return "Click next to a step of the trigger to reset.";
				target.setValue(other.getId());
				return null;
			}
			case SET_ZONE:
			{
				Zone zone = zoneAt(game, clicked);
				if(zone == null)
					return "Click inside the zone to switch.";
				target.setValue(zone.getId());
				return null;
			}
			case LOCK_TELEPORTER:
			{
				String name = teleporterNear(game, clicked);
				Transport transport = name == null ? game.transportManager.findNear(clicked.getLocation().add(0.5, 1, 0.5).toVector(), EDIT_REACH) : null;
				if(name == null && transport == null)
					return "Click a teleporter or next to a transport.";
				target.setValue(name != null ? name : transport.getId());
				return null;
			}
			default:
				return null;
		}
	}

	private static void undo(Player player, BuildSession session)
	{
		TriggerDraft draft = session.getTriggerDraft();
		draft.undo();
		if(draft.history.isEmpty() || draft.building.getSteps().isEmpty() && (draft.songPreset || draft.building.getTargets().isEmpty()))
		{
			draft.clear();
			CommandUtil.actionBar(player, ChatColor.YELLOW + "Trigger discarded");
			return;
		}
		CommandUtil.actionBar(player, ChatColor.RED + "Removed last" + ChatColor.GRAY + " - " + draft.building.getSteps().size() + " step(s), "
				+ draft.building.getTargets().size() + " target(s)");
	}

	private static void finish(Player player, BuildSession session)
	{
		TriggerDraft draft = session.getTriggerDraft();
		Trigger trigger = draft.building;
		if(trigger.getSteps().isEmpty())
		{
			msg(player, ChatColor.RED + "Add at least one step first.");
			return;
		}
		if(trigger.getTargets().isEmpty())
		{
			msg(player, ChatColor.RED + "Add at least one target: press F until a 'Target:' kind is selected, then right-click.");
			return;
		}
		session.getGame().triggerManager.addTrigger(trigger);
		draft.clear();
		GameManager.INSTANCE.saveAllGames();
		msg(player, ChatColor.GREEN + "" + ChatColor.BOLD + "Trigger created" + ChatColor.GREEN + " " + trigger.getId() + ".");
		openOptions(player, session, trigger);
	}

	private static void editNearest(Player player, BuildSession session, Block clicked)
	{
		Trigger trigger = triggerNear(session.getGame(), clicked);
		if(trigger == null)
		{
			msg(player, ChatColor.GRAY + "No trigger step near there. Right-click to start a new trigger.");
			return;
		}
		openOptions(player, session, trigger);
	}

	private static void openOptions(Player player, BuildSession session, Trigger trigger)
	{
		session.getTriggerDraft().pendingOptions = trigger;
		TriggerOptions.prompt(player, trigger);
	}

	// ---------------------------------------------------------------- chat

	public static boolean hasPendingOptions(Player player)
	{
		BuildSession session = BuildModeManager.INSTANCE.getSession(player);
		return session != null && session.getTriggerDraft().pendingOptions != null;
	}

	/** Applies a chat line to the trigger being edited. MUST run on the main thread. */
	public static void handleChat(Player player, String message)
	{
		BuildSession session = BuildModeManager.INSTANCE.getSession(player);
		if(session == null || session.getTriggerDraft().pendingOptions == null)
			return;
		TriggerDraft draft = session.getTriggerDraft();
		Trigger trigger = draft.pendingOptions;
		switch(TriggerOptions.apply(player, session.getGame(), trigger, message))
		{
			case DONE:
				draft.pendingOptions = null;
				msg(player, ChatColor.GRAY + "Finished editing " + trigger.getId() + ".");
				return;
			case DELETE:
				draft.pendingOptions = null;
				session.getGame().triggerManager.removeTrigger(trigger);
				GameManager.INSTANCE.saveAllGames();
				msg(player, ChatColor.YELLOW + "Deleted " + trigger.getId() + ".");
				return;
			case CHANGED:
				GameManager.INSTANCE.saveAllGames();
				msg(player, ChatColor.GREEN + "Saved " + trigger.summary() + ChatColor.GRAY + " ('list' shows steps, 'done' to stop)");
				return;
			default:
		}
	}

	// ---------------------------------------------------------------- perk pool spots

	private static void poolInteract(Player player, BuildSession session, Block clicked, BlockFace face, Action action)
	{
		Game game = session.getGame();
		PerkPoolManager pool = game.perkPoolManager;
		if(action == Action.LEFT_CLICK_BLOCK)
		{
			if(pool.removeSpotAt(clicked.getLocation().toVector()) || pool.removeSpotAt(clicked.getRelative(BlockFace.UP).getLocation().toVector()))
			{
				GameManager.INSTANCE.saveAllGames();
				msg(player, ChatColor.YELLOW + "Removed perk pool spot (" + pool.getSpots().size() + " left).");
			}
			else
				msg(player, ChatColor.GRAY + "No perk pool spot there.");
			return;
		}
		if(action != Action.RIGHT_CLICK_BLOCK)
			return;
		Block spot = clicked.getRelative(face);
		if(!spot.getType().isAir())
		{
			msg(player, ChatColor.RED + "There is no room for a machine there.");
			return;
		}
		if(!game.arena.containsBlock(spot.getLocation()))
		{
			msg(player, ChatColor.RED + "Perk pool spots must be inside the arena!");
			return;
		}
		pool.addSpot(new PerkPoolManager.Spot(spot.getLocation().toVector(), facingTowards(player)));
		GameManager.INSTANCE.saveAllGames();
		feedback(player, spot, ChatColor.GREEN + "Perk pool spot " + pool.getSpots().size() + " added");
		if(pool.getMode() == PerkPoolManager.Mode.FIXED)
			msg(player, ChatColor.GRAY + "Perks only use pool spots with perk_pool_mode = random_on_open or random_on_start (/zombies settings).");
	}

	/** The cardinal face pointing back at the player, so a machine placed here faces them. */
	private static BlockFace facingTowards(Player player)
	{
		float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
		BlockFace looking = yaw < 45 || yaw >= 315 ? BlockFace.SOUTH : yaw < 135 ? BlockFace.WEST : yaw < 225 ? BlockFace.NORTH : BlockFace.EAST;
		return looking.getOppositeFace();
	}

	// ---------------------------------------------------------------- lookups

	private static Zone zoneAt(Game game, Block clicked)
	{
		Zone zone = game.zoneManager.getSmallestZoneAt(clicked.getLocation().add(0.5, 0.5, 0.5));
		return zone != null ? zone : game.zoneManager.getSmallestZoneAt(clicked.getLocation().add(0.5, 1.5, 0.5));
	}

	private static Door doorNear(Game game, Block clicked)
	{
		Door best = null;
		double bestSq = EDIT_REACH * EDIT_REACH;
		for(Door door : game.doorManager.getDoors())
		{
			if(door.hasDoorLoc(clicked))
				return door;
			for(Block block : door.getBlocks())
			{
				double d = block.getLocation().distanceSquared(clicked.getLocation());
				if(d <= bestSq)
				{
					bestSq = d;
					best = door;
				}
			}
		}
		return best;
	}

	private static String teleporterNear(Game game, Block clicked)
	{
		Map<String, Location> teleporters = game.teleporterManager.getTeleporters();
		if(BlockUtils.isSign(clicked.getType()))
		{
			Sign sign = (Sign) clicked.getState();
			String name = ChatColor.stripColor(sign.getLine(2)).trim().toLowerCase();
			if(ChatColor.stripColor(sign.getLine(1)).toLowerCase().contains("teleporter") && teleporters.containsKey(name))
				return name;
		}
		String best = null;
		double bestSq = 6 * 6;
		for(Map.Entry<String, Location> entry : teleporters.entrySet())
		{
			Location loc = entry.getValue();
			if(loc == null || loc.getWorld() != clicked.getWorld())
				continue;
			double d = loc.distanceSquared(clicked.getLocation());
			if(d <= bestSq)
			{
				bestSq = d;
				best = entry.getKey();
			}
		}
		if(best == null && teleporters.size() == 1)
			return teleporters.keySet().iterator().next();
		return best;
	}

	/** The trigger with a step or target point (or step zone) closest to the click. */
	private static Trigger triggerNear(Game game, Block clicked)
	{
		Trigger best = null;
		double bestSq = EDIT_REACH * EDIT_REACH;
		Vector at = clicked.getLocation().toVector();
		for(Trigger trigger : game.triggerManager.getTriggers())
		{
			for(TriggerStep step : trigger.getSteps())
			{
				if(step.getPoint() != null && step.getPoint().distanceSquared(at) <= bestSq)
				{
					bestSq = step.getPoint().distanceSquared(at);
					best = trigger;
				}
				if(best == null && step.usesZone())
				{
					Zone zone = game.zoneManager.getZone(step.getZone());
					if(zone != null && zone.contains(clicked.getLocation().add(0.5, 1.5, 0.5)))
						best = trigger;
				}
			}
			for(TriggerTarget target : trigger.getTargets())
				if(target.getPoint() != null && target.getPoint().distanceSquared(at) <= bestSq)
				{
					bestSq = target.getPoint().distanceSquared(at);
					best = trigger;
				}
		}
		return best;
	}

	// ---------------------------------------------------------------- preview

	/** Draws every trigger, the one being built and the pool spots while a trigger variant is held. */
	public static void startPreview(Player player, BuildSession session)
	{
		stopPreview(session);
		TriggerDraft draft = session.getTriggerDraft();
		draft.previewTask = COMZombies.scheduleTask(5L, 5L, () ->
		{
			if(!player.isOnline() || heldVariant(player, session) == null)
				return;
			Game game = session.getGame();
			for(Trigger trigger : game.triggerManager.getTriggers())
				drawTrigger(player, game, trigger, 0.9f);
			if(draft.building != null)
				drawTrigger(player, game, draft.building, 1.6f);
			drawPoolSpots(player, game);
		});
	}

	public static void stopPreview(BuildSession session)
	{
		TriggerDraft draft = session.getTriggerDraft();
		if(draft.previewTask != -1)
		{
			Bukkit.getScheduler().cancelTask(draft.previewTask);
			draft.previewTask = -1;
		}
	}

	/** Build mode is closing: stop drawing and drop unfinished work. */
	public static void onExit(Player player, BuildSession session)
	{
		stopPreview(session);
		TriggerDraft draft = session.getTriggerDraft();
		if(draft.building != null)
			msg(player, ChatColor.YELLOW + "Note: an unfinished trigger was discarded (finish it with Sneak + F).");
		draft.clear();
		draft.pendingOptions = null;
	}

	private static void drawTrigger(Player player, Game game, Trigger trigger, float size)
	{
		Particle dust = Compat.DUST;
		if(dust == null)
			return;
		Location previous = null;
		for(TriggerStep step : trigger.getSteps())
		{
			Location at = stepMarker(game, step);
			if(at == null || at.getWorld() != player.getWorld() || at.distanceSquared(player.getLocation()) > VIEW_DISTANCE_SQ)
			{
				previous = null;
				continue;
			}
			Color color = step.getKind() == TriggerStep.Kind.INTERACT ? Color.FUCHSIA : step.getKind() == TriggerStep.Kind.STAND_ON ? Color.LIME : Color.AQUA;
			player.spawnParticle(dust, at, 4, 0.12, 0.12, 0.12, 0, new Particle.DustOptions(color, size));
			if(trigger.isOrdered() && previous != null)
				drawLine(player, dust, previous, at, new Particle.DustOptions(Color.WHITE, 0.6f));
			previous = at;
		}
		drawTargets(player, dust, trigger.getTargets(), size);
	}

	/** Target points in orange; clone and fill boxes as corner marks (yellow), a timeline's targets included. */
	private static void drawTargets(Player player, Particle dust, java.util.List<TriggerTarget> targets, float size)
	{
		for(TriggerTarget target : targets)
		{
			drawTargets(player, dust, target.getTargets(), size);
			if(target.getBoxMin() != null)
			{
				Vector min = target.getBoxMin();
				Vector max = target.getBoxMax().clone().add(new Vector(1, 1, 1));
				for(int i = 0; i < 8; i++)
				{
					Location corner = new Location(player.getWorld(), (i & 1) == 0 ? min.getX() : max.getX(), (i & 2) == 0 ? min.getY() : max.getY(),
							(i & 4) == 0 ? min.getZ() : max.getZ());
					if(corner.distanceSquared(player.getLocation()) <= VIEW_DISTANCE_SQ)
						player.spawnParticle(dust, corner, 2, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(Color.YELLOW, size));
				}
			}
			if(target.getPoint() == null)
				continue;
			Location at = target.pointLocation(player.getWorld()).add(0, 1.2, 0);
			if(at.distanceSquared(player.getLocation()) <= VIEW_DISTANCE_SQ)
				player.spawnParticle(dust, at, 4, 0.12, 0.12, 0.12, 0, new Particle.DustOptions(Color.ORANGE, size));
		}
	}

	private static Location stepMarker(Game game, TriggerStep step)
	{
		if(step.getPoint() != null)
			return step.pointLocation(game.getWorld()).add(0.5, step.getKind() == TriggerStep.Kind.STAND_ON ? 1.1 : 1.3, 0.5);
		if(step.usesZone())
		{
			Zone zone = game.zoneManager.getZone(step.getZone());
			if(zone == null)
				return null;
			Location min = zone.getMin();
			Location max = zone.getMax();
			return new Location(zone.getWorld(), (min.getX() + max.getX() + 1) / 2, min.getY() + 1.1, (min.getZ() + max.getZ() + 1) / 2);
		}
		return null;
	}

	private static void drawLine(Player player, Particle dust, Location from, Location to, Particle.DustOptions options)
	{
		double length = from.distance(to);
		if(length > 48)
			return;
		Vector step = to.toVector().subtract(from.toVector()).normalize().multiply(0.6);
		Location point = from.clone();
		for(double d = 0; d < length; d += 0.6)
		{
			player.spawnParticle(dust, point, 1, 0, 0, 0, 0, options);
			point.add(step);
		}
	}

	private static void drawPoolSpots(Player player, Game game)
	{
		Particle dust = Compat.DUST;
		if(dust == null)
			return;
		Particle.DustOptions column = new Particle.DustOptions(Color.ORANGE, 1.2f);
		Particle.DustOptions front = new Particle.DustOptions(Color.YELLOW, 1.0f);
		for(PerkPoolManager.Spot spot : game.perkPoolManager.getSpots())
		{
			Location base = spot.location(player.getWorld()).add(0.5, 0, 0.5);
			if(base.distanceSquared(player.getLocation()) > VIEW_DISTANCE_SQ)
				continue;
			for(double y = 0.2; y <= 1.8; y += 0.4)
				player.spawnParticle(dust, base.clone().add(0, y, 0), 1, 0.05, 0, 0.05, 0, column);
			player.spawnParticle(dust, base.clone().add(spot.facing.getModX() * 0.6, 1.0, spot.facing.getModZ() * 0.6), 2, 0.05, 0.05, 0.05, 0, front);
		}
	}

	// ---------------------------------------------------------------- feedback

	private static void feedback(Player player, Block block, String text)
	{
		block.getWorld().spawnParticle(Compat.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.8, 0.5), 6, 0.3, 0.3, 0.3, 0);
		CommandUtil.actionBar(player, text);
	}


	private static void msg(Player player, String text)
	{
		CommandUtil.sendMessageToPlayer(player, text);
	}
}
