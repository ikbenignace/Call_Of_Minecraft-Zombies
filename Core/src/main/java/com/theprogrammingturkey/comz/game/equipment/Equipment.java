package com.theprogrammingturkey.comz.game.equipment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.util.PackModels;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One entry of {@code equipment.json}. Fields that do not apply to the entry's {@link EquipmentType} are simply
 * left at their defaults.
 */
public final class Equipment
{
	private static NamespacedKey itemKey;

	public final String name;
	public final EquipmentType type;
	/** Free-form behaviour id for tacticals ({@code gersch}, {@code qed}, ...) and gear ({@code breathing}, {@code none}). */
	public final String behaviour;
	public final int cost;
	public final int refillCost;
	public final Material material;
	public final String itemModel;
	private final Map<String, String> sounds = new HashMap<>();

	public final int oneShotThroughRound;
	public final double damageMultiplier;
	public final long swingCooldownMs;

	public final int perBuy;
	public final int maxPlaced;
	public final int maxCarry;
	public final int refillPerRound;
	public final boolean coneTrigger;
	public final double triggerRadius;
	public final double coneAngle;
	public final double damageRadius;
	public final double damage;
	public final int fuseTicks;
	public final boolean popUp;
	public final String effect;

	public final int durationTicks;
	public final double radius;
	public final double killRadius;
	public final double pullStrength;
	public final int chain;
	public final int chainDelayTicks;
	public final double chainSpread;
	public final List<WeightedEffect> effects;

	public final Material helmetMaterial;

	private Equipment(JsonObject json, EquipmentType type)
	{
		this.type = type;
		name = CustomConfig.getString(json, "name", "Unnamed");
		behaviour = CustomConfig.getString(json, "behaviour", "").toLowerCase(Locale.ROOT);
		cost = CustomConfig.getInt(json, "cost", 1000);
		refillCost = CustomConfig.getInt(json, "refill_cost", cost);
		material = material(CustomConfig.getString(json, "material", "STICK"), Material.STICK);
		itemModel = CustomConfig.getString(json, "item_model", "");
		if(json.has("sounds") && json.get("sounds").isJsonObject())
			for(Map.Entry<String, JsonElement> entry : json.getAsJsonObject("sounds").entrySet())
				if(entry.getValue().isJsonPrimitive())
					sounds.put(entry.getKey(), entry.getValue().getAsString());

		oneShotThroughRound = CustomConfig.getInt(json, "one_shot_through_round", 0);
		damageMultiplier = CustomConfig.getDouble(json, "damage_multiplier", 1.0);
		swingCooldownMs = CustomConfig.getInt(json, "swing_cooldown_ms", -1);

		perBuy = Math.max(1, CustomConfig.getInt(json, "per_buy", 1));
		maxPlaced = Math.max(1, CustomConfig.getInt(json, "max_placed", perBuy * 2));
		maxCarry = Math.max(perBuy, CustomConfig.getInt(json, "max_carry", perBuy));
		refillPerRound = Math.max(0, CustomConfig.getInt(json, "refill_per_round", 0));
		coneTrigger = "cone".equalsIgnoreCase(CustomConfig.getString(json, "trigger", "radius"));
		triggerRadius = CustomConfig.getDouble(json, "trigger_radius", 3.0);
		coneAngle = CustomConfig.getDouble(json, "cone_angle", 70.0);
		damageRadius = CustomConfig.getDouble(json, "damage_radius", 5.0);
		damage = CustomConfig.getDouble(json, "damage", 0.0);
		fuseTicks = Math.max(0, CustomConfig.getInt(json, "fuse_ticks", 20));
		popUp = CustomConfig.getBoolean(json, "pop_up", false);
		effect = CustomConfig.getString(json, "effect", "blast").toLowerCase(Locale.ROOT);

		durationTicks = Math.max(1, CustomConfig.getInt(json, "duration_ticks", 100));
		radius = CustomConfig.getDouble(json, "radius", 6.0);
		killRadius = CustomConfig.getDouble(json, "kill_radius", 1.5);
		pullStrength = CustomConfig.getDouble(json, "pull_strength", 0.3);
		chain = Math.max(1, CustomConfig.getInt(json, "chain", 1));
		chainDelayTicks = Math.max(1, CustomConfig.getInt(json, "chain_delay_ticks", 15));
		chainSpread = CustomConfig.getDouble(json, "chain_spread", 3.0);
		effects = Collections.unmodifiableList(readEffects(json));

		helmetMaterial = material(CustomConfig.getString(json, "helmet_material", material.name()), material);
	}

	static Equipment fromJson(JsonObject json)
	{
		EquipmentType type = EquipmentType.fromId(CustomConfig.getString(json, "type", ""));
		if(type == null)
		{
			COMZombies.log.warning("equipment.json: '" + CustomConfig.getString(json, "name", "?") + "' has an unknown type and was skipped.");
			return null;
		}
		return new Equipment(json, type);
	}

	private static List<WeightedEffect> readEffects(JsonObject json)
	{
		List<WeightedEffect> out = new ArrayList<>();
		if(!json.has("effects") || !json.get("effects").isJsonArray())
			return out;
		for(JsonElement element : json.getAsJsonArray("effects"))
		{
			if(element.isJsonPrimitive())
				out.add(new WeightedEffect(element.getAsString().toLowerCase(Locale.ROOT), 1));
			else if(element.isJsonObject())
			{
				JsonObject entry = element.getAsJsonObject();
				int weight = CustomConfig.getInt(entry, "weight", 1);
				if(weight > 0)
					out.add(new WeightedEffect(CustomConfig.getString(entry, "effect", "").toLowerCase(Locale.ROOT), weight));
			}
		}
		return out;
	}

	private static Material material(String name, Material fallback)
	{
		Material match = name == null ? null : Material.matchMaterial(name);
		return match == null ? fallback : match;
	}

	public String sound(String event)
	{
		return sounds.get(event);
	}

	public boolean isMonkeyBomb()
	{
		return type == EquipmentType.TACTICAL && "monkey_bomb".equals(behaviour);
	}

	/** Hotbar item carrying this equipment's id, so the use listener can recognise it. */
	public ItemStack createItem(int amount)
	{
		ItemStack stack = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
		ItemMeta meta = stack.getItemMeta();
		if(meta != null)
		{
			meta.setDisplayName(ChatColor.GOLD + name);
			meta.setUnbreakable(true);
			meta.getPersistentDataContainer().set(itemKey(), PersistentDataType.STRING, name);
			stack.setItemMeta(meta);
		}
		PackModels.applyFull(stack, itemModel);
		return stack;
	}

	/** The look used on the wall and for placed mines: the pack model when it is in use, else the plain material. */
	public ItemStack displayStack(boolean usePack)
	{
		ItemStack stack = new ItemStack(material);
		if(usePack)
			PackModels.applyFull(stack, itemModel);
		return stack;
	}

	public boolean hasPackModel()
	{
		return itemModel != null && !itemModel.isEmpty();
	}

	/** The catalogue name stored on an equipment item, or null when {@code stack} is not one. */
	public static String nameOf(ItemStack stack)
	{
		if(stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta())
			return null;
		ItemMeta meta = stack.getItemMeta();
		return meta == null ? null : meta.getPersistentDataContainer().get(itemKey(), PersistentDataType.STRING);
	}

	private static NamespacedKey itemKey()
	{
		if(itemKey == null)
			itemKey = new NamespacedKey(COMZombies.getPlugin(), "equipment");
		return itemKey;
	}

	public static final class WeightedEffect
	{
		public final String effect;
		public final int weight;

		WeightedEffect(String effect, int weight)
		{
			this.effect = effect;
			this.weight = weight;
		}
	}
}
