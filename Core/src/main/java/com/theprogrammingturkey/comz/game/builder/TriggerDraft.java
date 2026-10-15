package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.features.Trigger;
import com.theprogrammingturkey.comz.game.features.TriggerStep;
import com.theprogrammingturkey.comz.game.features.TriggerTarget;

import java.util.ArrayList;
import java.util.List;

/** A builder's trigger work: the trigger being clicked together, what the next click adds, and the chat edit target. */
public class TriggerDraft
{
	private static final int STEP_KINDS = TriggerStep.Kind.values().length;

	Trigger building;
	boolean songPreset;
	/** Index into every step kind followed by every target kind. */
	int element;
	/** Order elements were added in (true = step), for undo. */
	final List<Boolean> history = new ArrayList<>();
	Trigger pendingOptions;
	int previewTask = -1;

	void start(Trigger trigger, boolean fromSongPreset)
	{
		building = trigger;
		songPreset = fromSongPreset;
		history.clear();
	}

	void clear()
	{
		building = null;
		songPreset = false;
		history.clear();
	}

	static int elementCount()
	{
		return STEP_KINDS + TriggerTarget.Kind.values().length;
	}

	TriggerStep.Kind stepKind()
	{
		return element < STEP_KINDS ? TriggerStep.Kind.values()[element] : null;
	}

	TriggerTarget.Kind targetKind()
	{
		return element >= STEP_KINDS ? TriggerTarget.Kind.values()[element - STEP_KINDS] : null;
	}

	String elementLabel()
	{
		TriggerStep.Kind step = stepKind();
		return step != null ? "Step: " + step.getLabel() : "Target: " + targetKind().getLabel();
	}

	void cycleElement()
	{
		element = (element + 1) % elementCount();
	}

	/** Removes the most recently added step or target; false when there was nothing to undo. */
	boolean undo()
	{
		if(building == null || history.isEmpty())
			return false;
		boolean step = history.remove(history.size() - 1);
		List<?> list = step ? building.getSteps() : building.getTargets();
		if(!list.isEmpty())
			list.remove(list.size() - 1);
		return true;
	}
}
