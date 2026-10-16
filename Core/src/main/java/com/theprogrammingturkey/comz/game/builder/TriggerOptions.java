package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Trigger;
import com.theprogrammingturkey.comz.game.features.TriggerStep;
import com.theprogrammingturkey.comz.game.features.TriggerTarget;
import com.theprogrammingturkey.comz.game.features.Zone;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat syntax for a trigger in build mode, e.g.
 * {@code ordered=false time=30 reset_on_fail s2.cost=500 s1.visual=teddy t1.value=MAX_AMMO name=Stepping Stones}.
 * {@code sN.} addresses step N and {@code tN.} target N, {@code tN.M.} target M inside timed target N;
 * {@code name=} and {@code text=} take the rest of the line. {@code tN.delay=<seconds>} puts a target on the
 * timeline (it runs that long after the trigger fires); {@code list} shows the timeline in order.
 */
public final class TriggerOptions
{
	public static final String USAGE = "ordered|unordered time=<s> reset_on_fail=<bool> all_players=<bool> repeatable=<bool> simultaneous=<bool> silent=<bool> name=<text>";
	public static final String ELEMENT_USAGE = "s<N>.cost|visual|count|chance|zone|transport|trigger|sign|gear|hold|text=..., "
			+ "t<N>.value|amount|title|delay|box|to|mode|block|filter|volume|on|text=..., t<N>.<M>.<key>= for timed targets, remove s<N>|t<N>|t<N>.<M>, list, delete, done";

	private static final Pattern REST_OF_LINE = Pattern.compile("(?i)(?:^|\\s)((?:[st]\\d+(?:\\.\\d+)?\\.)?(?:name|text))=(.*)$");
	private static final Pattern ELEMENT_KEY = Pattern.compile("(?i)([st])(\\d+)(?:\\.(\\d+))?\\.([a-z_]+)");

	private TriggerOptions()
	{
	}

	public static void prompt(Player player, Trigger trigger)
	{
		list(player, trigger);
		CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Type options in chat: " + USAGE);
		CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Per step/target: " + ELEMENT_USAGE);
	}

	public static void list(Player player, Trigger trigger)
	{
		CommandUtil.sendMessageToPlayer(player, ChatColor.GOLD + trigger.summary());
		for(int i = 0; i < trigger.getSteps().size(); i++)
		{
			TriggerStep step = trigger.getSteps().get(i);
			CommandUtil.sendMessageToPlayer(player, ChatColor.AQUA + " s" + (i + 1) + ChatColor.GRAY + ": " + step.describe()
					+ (step.getDescription().isEmpty() ? "" : " \"" + step.getDescription() + "\""));
		}
		for(int i = 0; i < trigger.getTargets().size(); i++)
		{
			TriggerTarget target = trigger.getTargets().get(i);
			if(target.getKind() != TriggerTarget.Kind.AFTER)
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.YELLOW + " t" + (i + 1) + ChatColor.GRAY + ": " + target.describe());
				continue;
			}
			CommandUtil.sendMessageToPlayer(player, ChatColor.YELLOW + " t" + (i + 1) + ChatColor.GRAY + ": at " + seconds(target.getAmount()) + "s");
			for(int j = 0; j < target.getTargets().size(); j++)
				CommandUtil.sendMessageToPlayer(player, ChatColor.YELLOW + "   t" + (i + 1) + "." + (j + 1) + ChatColor.GRAY + ": " + target.getTargets().get(j).describe());
		}
	}

	private static String seconds(int ticks)
	{
		return ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format(Locale.ROOT, "%.2f", ticks / 20.0);
	}

	/** Applies one chat line; {@link Result#DONE} and {@link Result#DELETE} end the edit session. */
	public static Result apply(Player player, Game game, Trigger trigger, String message)
	{
		String text = message.trim();
		String lower = text.toLowerCase(Locale.ROOT);
		if(lower.equals("done") || lower.equals("cancel") || lower.equals("skip"))
			return Result.DONE;
		if(lower.equals("delete"))
			return Result.DELETE;
		if(lower.equals("list") || lower.equals("info"))
		{
			list(player, trigger);
			return Result.KEEP;
		}
		if(lower.startsWith("remove "))
		{
			remove(player, trigger, lower.substring(7).trim());
			return Result.CHANGED;
		}

		StringBuilder errors = new StringBuilder();
		Matcher rest = REST_OF_LINE.matcher(text);
		if(rest.find())
		{
			if(!set(new Context(player, game), trigger, rest.group(1).toLowerCase(Locale.ROOT), rest.group(2).trim()))
				errors.append(' ').append(rest.group(1)).append('=');
			text = text.substring(0, rest.start());
		}
		for(String token : text.split("[,\\s]+"))
		{
			if(token.isEmpty())
				continue;
			String[] kv = token.split("=", 2);
			boolean ok;
			try
			{
				ok = set(new Context(player, game), trigger, kv[0].toLowerCase(Locale.ROOT), kv.length > 1 ? kv[1] : "");
			} catch(IllegalArgumentException e)
			{
				ok = false;
			}
			if(!ok)
				errors.append(' ').append(token);
		}
		if(errors.length() > 0)
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Ignored:" + errors + ChatColor.GRAY + " (" + USAGE + "; " + ELEMENT_USAGE + ")");
		return Result.CHANGED;
	}

	public enum Result
	{
		KEEP, CHANGED, DONE, DELETE
	}

	private static void remove(Player player, Trigger trigger, String ref)
	{
		Matcher m = Pattern.compile("([st])(\\d+)(?:\\.(\\d+))?").matcher(ref);
		if(!m.matches() || m.group(3) != null && m.group(1).equals("s"))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Use 'remove s<N>', 'remove t<N>' or 'remove t<N>.<M>'.");
			return;
		}
		int index = Integer.parseInt(m.group(2)) - 1;
		java.util.List<?> list = m.group(1).equals("s") ? trigger.getSteps() : trigger.getTargets();
		if(m.group(3) != null)
		{
			if(index < 0 || index >= list.size() || trigger.getTargets().get(index).getKind() != TriggerTarget.Kind.AFTER)
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "t" + (index + 1) + " is not a timed target.");
				return;
			}
			list = trigger.getTargets().get(index).getTargets();
			index = Integer.parseInt(m.group(3)) - 1;
		}
		if(index < 0 || index >= list.size())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "There is no " + ref + ".");
			return;
		}
		list.remove(index);
		CommandUtil.sendMessageToPlayer(player, ChatColor.YELLOW + "Removed " + ref + ".");
	}

	/** Who is typing, for values like {@code here}: the player's block or the smallest zone they stand in. */
	private static final class Context
	{
		final Player player;
		final Game game;

		Context(Player player, Game game)
		{
			this.player = player;
			this.game = game;
		}
	}

	private static boolean set(Context context, Trigger trigger, String key, String value)
	{
		Matcher element = ELEMENT_KEY.matcher(key);
		if(element.matches())
		{
			int index = Integer.parseInt(element.group(2)) - 1;
			if(element.group(1).equalsIgnoreCase("s"))
				return element.group(3) == null && index >= 0 && index < trigger.getSteps().size() && setStep(trigger.getSteps().get(index), element.group(4), value);
			if(index < 0 || index >= trigger.getTargets().size())
				return false;
			if(element.group(3) == null)
			{
				if(element.group(4).equals("delay"))
					return setDelay(trigger.getTargets(), index, value);
				return setTarget(context, trigger.getTargets().get(index), element.group(4), value);
			}
			TriggerTarget timed = trigger.getTargets().get(index);
			int nested = Integer.parseInt(element.group(3)) - 1;
			return timed.getKind() == TriggerTarget.Kind.AFTER && nested >= 0 && nested < timed.getTargets().size()
					&& setTarget(context, timed.getTargets().get(nested), element.group(4), value);
		}
		switch(key)
		{
			case "name":
				trigger.setName(value);
				return true;
			case "ordered":
				trigger.setOrdered(bool(value));
				return true;
			case "unordered":
			case "any_order":
				trigger.setOrdered(false);
				return true;
			case "time":
			case "time_limit":
			case "time_limit_seconds":
				trigger.setTimeLimitSeconds(Integer.parseInt(value));
				return true;
			case "reset_on_fail":
				trigger.setResetOnFail(bool(value));
				return true;
			case "all_players":
				trigger.setAllPlayers(bool(value));
				return true;
			case "repeatable":
				trigger.setRepeatable(bool(value));
				return true;
			case "simultaneous":
			case "all_at_once":
				trigger.setSimultaneous(bool(value));
				return true;
			case "silent":
				trigger.setSilent(bool(value));
				return true;
			default:
				return false;
		}
	}

	private static boolean setStep(TriggerStep step, String key, String value)
	{
		switch(key)
		{
			case "cost":
				step.setCost(Integer.parseInt(value));
				return true;
			case "visual":
				step.setVisual(value);
				return true;
			case "gear":
				step.setGear(value.equalsIgnoreCase("none") ? "" : value);
				return true;
			case "hold":
			case "hold_seconds":
				step.setHoldSeconds(Integer.parseInt(value));
				return true;
			case "count":
			case "amount":
			case "round":
			case "kills":
			case "players":
			case "chance":
				step.setAmount(value.equalsIgnoreCase("all") ? 0 : Integer.parseInt(value));
				return true;
			case "zone":
				step.setZone(value);
				return true;
			case "transport":
				step.setTransport(value);
				return true;
			case "trigger":
				step.setTrigger(value);
				return true;
			case "sign":
				step.setSign(value);
				return true;
			case "text":
			case "description":
				step.setDescription(value.equalsIgnoreCase("none") ? "" : value);
				return true;
			default:
				return false;
		}
	}

	/**
	 * Puts target {@code index} on the timeline: it moves into a timed target that runs {@code value} seconds after
	 * the trigger fires (joining one with the same delay). A timed target just gets the new delay; 0 takes its
	 * targets off the timeline again.
	 */
	private static boolean setDelay(List<TriggerTarget> targets, int index, String value)
	{
		int ticks = (int) Math.round(Double.parseDouble(value) * 20);
		if(ticks < 0)
			return false;
		TriggerTarget target = targets.get(index);
		if(target.getKind() == TriggerTarget.Kind.AFTER)
		{
			if(ticks == 0)
			{
				targets.remove(index);
				targets.addAll(index, target.getTargets());
			}
			else
				target.setAmount(ticks);
			return true;
		}
		if(ticks == 0)
			return true;
		targets.remove(index);
		for(TriggerTarget other : targets)
			if(other.getKind() == TriggerTarget.Kind.AFTER && other.getAmount() == ticks)
			{
				other.getTargets().add(target);
				return true;
			}
		TriggerTarget timed = new TriggerTarget(TriggerTarget.Kind.AFTER);
		timed.setAmount(ticks);
		timed.getTargets().add(target);
		targets.add(index, timed);
		return true;
	}

	private static boolean setTarget(Context context, TriggerTarget target, String key, String value)
	{
		switch(key)
		{
			case "value":
			case "door":
			case "teleporter":
			case "sound":
			case "song":
			case "type":
			case "perk":
			case "text":
			case "mode":
			case "block":
			case "zone":
			case "trigger":
				target.setValue(value);
				return true;
			case "filter":
				target.setFilter(value.equalsIgnoreCase("none") ? "" : value);
				return true;
			case "volume":
				target.setVolume(Float.parseFloat(value));
				return true;
			case "on":
				target.setAmount(bool(value) ? 1 : 0);
				return true;
			case "box":
				return setBox(context, target, value);
			case "to":
			case "at":
			{
				Vector point = point(context, value);
				if(point == null)
					return false;
				target.setPoint(point);
				return true;
			}
			case "amount":
			case "points":
				target.setAmount(Integer.parseInt(value));
				return true;
			case "title":
				target.setTitle(bool(value));
				return true;
			case "chat":
				target.setTitle(!bool(value));
				return true;
			default:
				return false;
		}
	}

	/** {@code here} (the zone you stand in), {@code zone:<id>} or {@code x1,y1,z1,x2,y2,z2}. */
	private static boolean setBox(Context context, TriggerTarget target, String value)
	{
		Zone zone = null;
		if(value.equalsIgnoreCase("here"))
			zone = context.game.zoneManager.getSmallestZoneAt(context.player.getLocation());
		else if(value.toLowerCase(Locale.ROOT).startsWith("zone:"))
			zone = context.game.zoneManager.getZone(value.substring(5));
		if(zone != null)
		{
			Location min = zone.getMin();
			Location max = zone.getMax();
			target.setBox(min.toVector(), max.toVector());
			return true;
		}
		String[] parts = value.split(",");
		if(parts.length != 6)
			return false;
		target.setBox(new Vector(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())),
				new Vector(Integer.parseInt(parts[3].trim()), Integer.parseInt(parts[4].trim()), Integer.parseInt(parts[5].trim())));
		return true;
	}

	/** {@code here} (the block you stand on top of) or {@code x,y,z}. */
	private static Vector point(Context context, String value)
	{
		if(value.equalsIgnoreCase("here"))
			return context.player.getLocation().toVector();
		String[] parts = value.split(",");
		if(parts.length != 3)
			return null;
		return new Vector(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
	}

	private static boolean bool(String value)
	{
		return value.isEmpty() || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("yes") || value.equals("1");
	}
}
