package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.equipment.Equipment;
import com.theprogrammingturkey.comz.game.equipment.EquipmentCatalogue;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.traps.TrapType;
import com.theprogrammingturkey.comz.game.traps.TrapTypes;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.util.Compat;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The build-mode toolbox. Each constant is one hotbar slot (in declaration order); scrolling the hotbar
 * selects a tool, F cycles the active tool's variant, right-click places, left-click removes. Variant
 * lists are computed live so perks/guns/kits always reflect what the server actually has loaded.
 */
public enum BuildTool
{
	WARP("Warp", Material.BLAZE_ROD),
	SPAWN("Zombie Spawn", Material.END_PORTAL_FRAME),
	PERK("Perk Machine", Compat.materialOr(Material.POTION, "HONEY_BOTTLE")),
	WEAPON("Wall Buy", Material.IRON_HOE),
	MACHINE("Machine", Material.CHEST),
	FEATURE("Feature", Material.TRIPWIRE_HOOK),
	LOBBY("Lobby Sign", Material.OAK_SIGN),
	DOOR("Door", Material.IRON_DOOR),
	BARRIER("Barrier", Material.OAK_FENCE);

	/** The five things the Warp tool can set, in cycle order. */
	public static final List<String> WARP_VARIANTS =
			Collections.unmodifiableList(Arrays.asList("Point 1", "Point 2", "Game Warp", "Spectator Warp", "Lobby Warp"));

	/** Machine-tool variants: display label paired with the sign keyword its handler is registered under. */
	public static final List<String> MACHINE_VARIANTS =
			Collections.unmodifiableList(Arrays.asList("Mystery Box", "Pack-a-Punch", "Ammo Crate", "Grenade", "Fridge", "Bank", "Power"));

	public static final String TRAP_VARIANT_PREFIX = "Trap: ";

	public static final String TRANSPORT_VARIANT = "Transport";

	/** Sign features, the transport and trigger builders, then one variant per zone preset (two-corner zone placement). */
	public static final List<String> FEATURE_VARIANTS = Collections.unmodifiableList(featureVariants());

	public static final List<String> LOBBY_VARIANTS =
			Collections.unmodifiableList(Arrays.asList("Join", "Spectate", "Kit"));

	private final String displayName;
	private final Material icon;

	BuildTool(String displayName, Material icon)
	{
		this.displayName = displayName;
		this.icon = icon;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public Material getIcon()
	{
		return icon;
	}

	/** One variant per trap type (live from traps.json), the other sign features, then one per zone preset. */
	private static List<String> featureVariants()
	{
		List<String> variants = new ArrayList<>();
		for(TrapType type : TrapTypes.all())
			variants.add(TRAP_VARIANT_PREFIX + type.getName());
		variants.add("Buildable");
		variants.add("Quest");
		variants.add(TRANSPORT_VARIANT);
		variants.add(TriggerBuilder.TRIGGER_VARIANT);
		variants.add(TriggerBuilder.SONG_VARIANT);
		variants.add(TriggerBuilder.POOL_VARIANT);
		for(ZonePreset preset : ZonePreset.values())
			variants.add(preset.getLabel());
		return variants;
	}

	/** The trap type a Feature-tool variant label stands for, or null when it is not a trap variant. */
	public static TrapType trapTypeForLabel(String label)
	{
		if(label == null || !label.startsWith(TRAP_VARIANT_PREFIX))
			return null;
		String name = label.substring(TRAP_VARIANT_PREFIX.length());
		for(TrapType type : TrapTypes.all())
			if(type.getName().equals(name))
				return type;
		return null;
	}

	/** The tool occupying a given hotbar slot, or null if the slot is not a tool slot. */
	public static BuildTool fromSlot(int slot)
	{
		BuildTool[] tools = values();
		return (slot >= 0 && slot < tools.length) ? tools[slot] : null;
	}

	/** Perks that have a machine model (everything except DER_WUNDERFIZZ), in declaration order. */
	public static List<PerkType> placeablePerks()
	{
		return Arrays.stream(PerkType.values())
				.filter(p -> p.getMachineModelKey() != null)
				.collect(Collectors.toList());
	}

	public static String perkLabel(PerkType perk)
	{
		String name = perk.toString().toLowerCase().replace('_', ' ');
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	public static String equipmentLabel(Equipment equipment)
	{
		return equipment.name + " (equipment)";
	}

	/** The variant labels for this tool, computed live. Empty when the tool has no variants. */
	public List<String> variantLabels()
	{
		switch(this)
		{
			case WARP:
				return WARP_VARIANTS;
			case PERK:
				return placeablePerks().stream().map(BuildTool::perkLabel).collect(Collectors.toList());
			case WEAPON:
			{
				List<String> labels = WeaponManager.getBuyableGuns().stream().map(BaseGun::getName).collect(Collectors.toList());
				for(Equipment equipment : EquipmentCatalogue.all())
					labels.add(equipmentLabel(equipment));
				return labels;
			}
			case MACHINE:
				return MACHINE_VARIANTS;
			case FEATURE:
				return Collections.unmodifiableList(featureVariants());
			case LOBBY:
				return LOBBY_VARIANTS;
			default:
				return new ArrayList<>();
		}
	}

	/** Label for the currently-selected variant, or null when the tool has no variants. */
	public String variantLabel(int index)
	{
		List<String> labels = variantLabels();
		if(labels.isEmpty())
			return null;
		if(index < 0 || index >= labels.size())
			index = 0;
		return labels.get(index);
	}
}
