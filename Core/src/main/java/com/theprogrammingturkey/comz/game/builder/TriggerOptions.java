package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.features.Trigger;
import com.theprogrammingturkey.comz.game.features.TriggerStep;
import com.theprogrammingturkey.comz.game.features.TriggerTarget;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat syntax for a trigger in build mode, e.g.
 * {@code ordered=false time=30 reset_on_fail s2.cost=500 s1.visual=teddy t1.value=MAX_AMMO name=Stepping Stones}.
 * {@code sN.} addresses step N and {@code tN.} target N; {@code name=} and {@code text=} take the rest of the line.
 */
public final class TriggerOptions
{
	public static final String USAGE = "ordered|unordered time=<s> reset_on_fail=<bool> all_players=<bool> repeatable=<bool> simultaneous=<bool> name=<text>";
	public static final String ELEMENT_USAGE = "s<N>.cost|visual|count|zone|transport|sign|text=..., t<N>.value|amount|title|text=..., remove s<N>|t<N>, list, delete, done";

	private static final Pattern REST_OF_LINE = Pattern.compile("(?i)(?:^|\\s)((?:[st]\\d+\\.)?(?:name|text))=(.*)$");
	private static final Pattern ELEMENT_KEY = Pattern.compile("(?i)([st])(\\d+)\\.(.+)");

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
			CommandUtil.sendMessageToPlayer(player, ChatColor.YELLOW + " t" + (i + 1) + ChatColor.GRAY + ": " + trigger.getTargets().get(i).describe());
	}

	/** Applies one chat line; {@link Result#DONE} and {@link Result#DELETE} end the edit session. */
	public static Result apply(Player player, Trigger trigger, String message)
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
			if(!set(trigger, rest.group(1).toLowerCase(Locale.ROOT), rest.group(2).trim()))
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
				ok = set(trigger, kv[0].toLowerCase(Locale.ROOT), kv.length > 1 ? kv[1] : "");
			} catch(NumberFormatException e)
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
		Matcher m = Pattern.compile("([st])(\\d+)").matcher(ref);
		if(!m.matches())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Use 'remove s<N>' or 'remove t<N>'.");
			return;
		}
		int index = Integer.parseInt(m.group(2)) - 1;
		java.util.List<?> list = m.group(1).equals("s") ? trigger.getSteps() : trigger.getTargets();
		if(index < 0 || index >= list.size())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "There is no " + ref + ".");
			return;
		}
		list.remove(index);
		CommandUtil.sendMessageToPlayer(player, ChatColor.YELLOW + "Removed " + ref + ".");
	}

	private static boolean set(Trigger trigger, String key, String value)
	{
		Matcher element = ELEMENT_KEY.matcher(key);
		if(element.matches())
		{
			int index = Integer.parseInt(element.group(2)) - 1;
			if(element.group(1).equalsIgnoreCase("s"))
				return index >= 0 && index < trigger.getSteps().size() && setStep(trigger.getSteps().get(index), element.group(3), value);
			return index >= 0 && index < trigger.getTargets().size() && setTarget(trigger.getTargets().get(index), element.group(3), value);
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
			case "count":
			case "amount":
			case "round":
			case "kills":
			case "players":
				step.setAmount(value.equalsIgnoreCase("all") ? 0 : Integer.parseInt(value));
				return true;
			case "zone":
				step.setZone(value);
				return true;
			case "transport":
				step.setTransport(value);
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

	private static boolean setTarget(TriggerTarget target, String key, String value)
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
				target.setValue(value);
				return true;
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

	private static boolean bool(String value)
	{
		return value.isEmpty() || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("yes") || value.equals("1");
	}
}
