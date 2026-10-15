package com.theprogrammingturkey.comz.game.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of {@link TriggerStep steps} that runs its {@link TriggerTarget targets} once every step is complete.
 * Covers Pack-a-Punch gates, buildable-free easter eggs, song eggs and door/teleporter unlocks without
 * knowing any map. Holds its own per-game progress; {@link com.theprogrammingturkey.comz.game.managers.TriggerManager}
 * feeds it game events.
 */
public class Trigger
{
	private final String id;
	private String name = "";
	private final List<TriggerStep> steps = new ArrayList<>();
	private final List<TriggerTarget> targets = new ArrayList<>();

	private boolean ordered = true;
	private int timeLimitSeconds;
	private boolean resetOnFail = true;
	private boolean allPlayers;
	private boolean repeatable;
	/** Stand-on steps only count while occupied, so every plate must be held at the same moment. */
	private boolean simultaneous;

	private long deadlineMs;
	private boolean fired;
	private boolean failed;

	public Trigger(String id)
	{
		this.id = id;
	}

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name == null ? "" : name.trim();
	}

	public String displayName()
	{
		return name.isEmpty() ? id : name;
	}

	public List<TriggerStep> getSteps()
	{
		return steps;
	}

	public List<TriggerTarget> getTargets()
	{
		return targets;
	}

	public boolean isOrdered()
	{
		return ordered;
	}

	public void setOrdered(boolean ordered)
	{
		this.ordered = ordered;
	}

	public int getTimeLimitSeconds()
	{
		return timeLimitSeconds;
	}

	public void setTimeLimitSeconds(int timeLimitSeconds)
	{
		this.timeLimitSeconds = Math.max(0, timeLimitSeconds);
	}

	public boolean isResetOnFail()
	{
		return resetOnFail;
	}

	public void setResetOnFail(boolean resetOnFail)
	{
		this.resetOnFail = resetOnFail;
	}

	public boolean isAllPlayers()
	{
		return allPlayers;
	}

	public void setAllPlayers(boolean allPlayers)
	{
		this.allPlayers = allPlayers;
	}

	public boolean isRepeatable()
	{
		return repeatable;
	}

	public void setRepeatable(boolean repeatable)
	{
		this.repeatable = repeatable;
	}

	public boolean isSimultaneous()
	{
		return simultaneous;
	}

	public void setSimultaneous(boolean simultaneous)
	{
		this.simultaneous = simultaneous;
	}

	/** True while the trigger can still make progress this game. */
	public boolean isActive()
	{
		return !fired && !failed && !steps.isEmpty();
	}

	public boolean isFired()
	{
		return fired;
	}

	public void setFired(boolean fired)
	{
		this.fired = fired;
	}

	public void setFailed(boolean failed)
	{
		this.failed = failed;
	}

	public long getDeadlineMs()
	{
		return deadlineMs;
	}

	public void setDeadlineMs(long deadlineMs)
	{
		this.deadlineMs = deadlineMs;
	}

	public int completedCount()
	{
		int count = 0;
		for(TriggerStep step : steps)
			if(step.isDone())
				count++;
		return count;
	}

	public boolean allDone()
	{
		return !steps.isEmpty() && completedCount() == steps.size();
	}

	public boolean hasProgress()
	{
		for(TriggerStep step : steps)
			if(step.isDone() || step.getProgress() > 0 || !step.getContributors().isEmpty())
				return true;
		return false;
	}

	/** The first unfinished step: the only one an ordered trigger accepts. */
	public TriggerStep nextStep()
	{
		for(TriggerStep step : steps)
			if(!step.isDone())
				return step;
		return null;
	}

	/** Whether {@code step} may complete right now (not done, trigger active, and its turn when ordered). */
	public boolean accepts(TriggerStep step)
	{
		return isActive() && !step.isDone() && (!ordered || simultaneous || nextStep() == step);
	}

	public void resetProgress()
	{
		for(TriggerStep step : steps)
			step.resetProgress();
		deadlineMs = 0;
	}

	public void resetAll()
	{
		resetProgress();
		fired = false;
		failed = false;
	}

	/**
	 * Re-arms a repeatable trigger after it fired. Power, round and trigger-done steps stay done: they would otherwise be met
	 * again on the next check and fire the trigger every tick.
	 */
	public void rearm()
	{
		for(TriggerStep step : steps)
			if(!step.isStateStep())
				step.resetProgress();
		deadlineMs = 0;
		fired = false;
	}

	public String summary()
	{
		return displayName() + " (" + id + "): " + steps.size() + " step(s), " + targets.size() + " target(s), "
				+ (ordered ? "ordered" : "any order")
				+ (timeLimitSeconds > 0 ? ", " + timeLimitSeconds + "s limit" : "")
				+ (resetOnFail ? ", resets on fail" : ", fails for the game")
				+ (allPlayers ? ", all players" : "")
				+ (simultaneous ? ", simultaneous" : "")
				+ (repeatable ? ", repeatable" : "");
	}

	public static Trigger load(JsonObject json)
	{
		Trigger trigger = new Trigger(CustomConfig.getString(json, "id", "trigger"));
		trigger.setName(CustomConfig.getString(json, "name", ""));
		JsonObject options = json.has("options") && json.get("options").isJsonObject() ? json.getAsJsonObject("options") : new JsonObject();
		trigger.setOrdered(CustomConfig.getBoolean(options, "ordered", true));
		trigger.setTimeLimitSeconds(CustomConfig.getInt(options, "time_limit_seconds", 0));
		trigger.setResetOnFail(CustomConfig.getBoolean(options, "reset_on_fail", true));
		trigger.setAllPlayers(CustomConfig.getBoolean(options, "all_players", false));
		trigger.setRepeatable(CustomConfig.getBoolean(options, "repeatable", false));
		trigger.setSimultaneous(CustomConfig.getBoolean(options, "simultaneous", false));

		if(json.has("steps") && json.get("steps").isJsonArray())
			for(JsonElement element : json.getAsJsonArray("steps"))
				if(element.isJsonObject())
				{
					TriggerStep step = TriggerStep.load(element.getAsJsonObject());
					if(step != null)
						trigger.steps.add(step);
				}
		if(json.has("targets") && json.get("targets").isJsonArray())
			for(JsonElement element : json.getAsJsonArray("targets"))
				if(element.isJsonObject())
				{
					TriggerTarget target = TriggerTarget.load(element.getAsJsonObject());
					if(target != null)
						trigger.targets.add(target);
				}
		return trigger;
	}

	public JsonObject save()
	{
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("name", name);
		JsonObject options = new JsonObject();
		options.addProperty("ordered", ordered);
		options.addProperty("time_limit_seconds", timeLimitSeconds);
		options.addProperty("reset_on_fail", resetOnFail);
		options.addProperty("all_players", allPlayers);
		options.addProperty("repeatable", repeatable);
		options.addProperty("simultaneous", simultaneous);
		json.add("options", options);
		JsonArray stepsJson = new JsonArray();
		for(TriggerStep step : steps)
			stepsJson.add(step.save());
		json.add("steps", stepsJson);
		JsonArray targetsJson = new JsonArray();
		for(TriggerTarget target : targets)
			targetsJson.add(target.save());
		json.add("targets", targetsJson);
		return json;
	}
}
