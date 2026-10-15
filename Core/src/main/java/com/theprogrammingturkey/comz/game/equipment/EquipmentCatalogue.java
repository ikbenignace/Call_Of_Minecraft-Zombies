package com.theprogrammingturkey.comz.game.equipment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.COMZConfig;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * The server-wide equipment catalogue read from {@code equipment.json}: entries plus the slot rules in its
 * {@code settings} block. Slots set to -1 keep that kind of item out of the hotbar.
 */
public final class EquipmentCatalogue
{
	private static final List<Equipment> entries = new ArrayList<>();

	public static int meleeSlot = 0;
	public static int gearSlot = 5;
	public static int tacticalSlot = 6;
	public static int placeableSlot = 7;
	public static boolean onePlaceable = true;
	public static boolean oneTactical = true;
	public static long defaultMeleeCooldownMs = 0;

	private EquipmentCatalogue()
	{
	}

	public static void load()
	{
		entries.clear();
		JsonElement root = ConfigManager.getConfig(COMZConfig.EQUIPMENT).getJson();
		if(root == null || !root.isJsonObject())
		{
			COMZombies.log.log(Level.WARNING, "Failed to load equipment.json; no equipment wall buys will work.");
			return;
		}
		JsonObject json = root.getAsJsonObject();
		JsonObject settings = json.has("settings") && json.get("settings").isJsonObject() ? json.getAsJsonObject("settings") : new JsonObject();
		meleeSlot = CustomConfig.getInt(settings, "melee_slot", 0);
		gearSlot = CustomConfig.getInt(settings, "gear_slot", 5);
		tacticalSlot = CustomConfig.getInt(settings, "tactical_slot", 6);
		placeableSlot = CustomConfig.getInt(settings, "placeable_slot", 7);
		onePlaceable = CustomConfig.getBoolean(settings, "one_placeable", true);
		oneTactical = CustomConfig.getBoolean(settings, "one_tactical", true);
		defaultMeleeCooldownMs = CustomConfig.getInt(settings, "default_melee_cooldown_ms", 0);

		if(!json.has("equipment") || !json.get("equipment").isJsonArray())
			return;
		for(JsonElement element : json.getAsJsonArray("equipment"))
		{
			if(!element.isJsonObject())
				continue;
			Equipment equipment = Equipment.fromJson(element.getAsJsonObject());
			if(equipment != null)
				entries.add(equipment);
		}
	}

	public static List<Equipment> all()
	{
		return Collections.unmodifiableList(entries);
	}

	public static Equipment get(String name)
	{
		if(name == null)
			return null;
		String key = normalize(name);
		for(Equipment equipment : entries)
			if(normalize(equipment.name).equals(key))
				return equipment;
		return null;
	}

	/** Slot a type's item goes in, or -1 when that type has no hotbar item. */
	public static int slotFor(EquipmentType type)
	{
		switch(type)
		{
			case MELEE:
				return meleeSlot;
			case PLACEABLE:
				return placeableSlot;
			case TACTICAL:
				return tacticalSlot;
			case GEAR:
				return gearSlot;
			default:
				return -1;
		}
	}

	public static String normalize(String name)
	{
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}
