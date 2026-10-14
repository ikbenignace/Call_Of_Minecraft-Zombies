package com.theprogrammingturkey.comz.game.visuals;

import com.theprogrammingturkey.comz.game.features.PerkType;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Tweakable table of what each machine looks like. A {@link Look} carries the pack model key (shown when
 * every player has the resource pack) and a small composite of scaled block boxes (shown to everyone else).
 *
 * <p>Parts are authored in a local frame in blocks: the origin is the centre of the machine's floor block,
 * +Y is up and the machine front faces -Z, the same orientation as the pack models. A perk machine fills
 * x/z -0.5..0.5 and is about 2 blocks tall.
 */
public final class MachineLooks
{
	/** Block spec: {@code "NAME"}, {@code "NAME[state=value]"}, or alternatives {@code "NEW|OLD"} (first one that exists wins). */
	public static final class Part
	{
		final String block;
		final float x0, y0, z0, x1, y1, z1;
		final boolean glow;

		Part(String block, float x0, float y0, float z0, float x1, float y1, float z1, boolean glow)
		{
			this.block = block;
			this.x0 = x0;
			this.y0 = y0;
			this.z0 = z0;
			this.x1 = x1;
			this.y1 = y1;
			this.z1 = z1;
			this.glow = glow;
		}
	}

	public static final class Look
	{
		public final String modelKey;
		public final float modelScale;
		public final float hitWidth;
		public final float hitHeight;
		public final float hologramY;
		final List<Part> parts;

		Look(String modelKey, float modelScale, float hitWidth, float hitHeight, float hologramY, List<Part> parts)
		{
			this.modelKey = modelKey;
			this.modelScale = modelScale;
			this.hitWidth = hitWidth;
			this.hitHeight = hitHeight;
			this.hologramY = hologramY;
			this.parts = Collections.unmodifiableList(parts);
		}

		public int partCount()
		{
			return parts.size();
		}

		public boolean isGlowPart(int index)
		{
			return index >= 0 && index < parts.size() && parts.get(index).glow;
		}
	}

	private static final String LAMP_ON = "REDSTONE_LAMP[lit=true]";

	private static final Map<PerkType, Look> PERKS = new EnumMap<>(PerkType.class);
	public static final Look PAP;
	public static final Look BOX;
	public static final Look BOX_OPEN;
	public static final Look AMMO_CRATE;
	public static final Look POWER_SWITCH;
	public static final Look POWER_SWITCH_ON;
	public static final Look FRIDGE;
	public static final Look BANK;

	static
	{
		//                      body                panel                 trim                  glow
		perk(PerkType.JUGGERNOG, "RED_CONCRETE", "WHITE_CONCRETE", "BLACK_CONCRETE", LAMP_ON);
		perk(PerkType.SPEED_COLA, "LIME_CONCRETE", "WHITE_CONCRETE", "GREEN_CONCRETE", "VERDANT_FROGLIGHT|GLOWSTONE");
		perk(PerkType.QUICK_REVIVE, "LIGHT_BLUE_CONCRETE", "WHITE_CONCRETE", "CYAN_CONCRETE", "SEA_LANTERN",
				box("LIGHT_BLUE_CONCRETE", -0.06f, 0.85f, -0.58f, 0.06f, 1.35f, -0.54f, false),
				box("LIGHT_BLUE_CONCRETE", -0.25f, 1.04f, -0.58f, 0.25f, 1.16f, -0.54f, false));
		perk(PerkType.DOUBLE_TAP, "RED_NETHER_BRICKS", "GOLD_BLOCK", "BLACK_CONCRETE", "GLOWSTONE");
		perk(PerkType.STAMIN_UP, "ORANGE_CONCRETE", "YELLOW_CONCRETE", "BROWN_CONCRETE", "OCHRE_FROGLIGHT|GLOWSTONE");
		perk(PerkType.PHD_FLOPPER, "PURPLE_CONCRETE", "MAGENTA_CONCRETE", "BLACK_CONCRETE", "PEARLESCENT_FROGLIGHT|GLOWSTONE");
		perk(PerkType.DEADSHOT_DAIQ, "GRAY_CONCRETE", "BLACK_CONCRETE", "LIGHT_GRAY_CONCRETE", "SEA_LANTERN");
		perk(PerkType.MULE_KICK, "GREEN_CONCRETE", "DARK_OAK_PLANKS", "DARK_OAK_LOG", "GLOWSTONE");
		perk(PerkType.ELECTRIC_C, "BLUE_CONCRETE", "RED_CONCRETE", "LIGHT_BLUE_CONCRETE", "SEA_LANTERN");
		perk(PerkType.TOMBSTONE_SODA, "STONE_BRICKS", "CRACKED_STONE_BRICKS", "POLISHED_ANDESITE", "SEA_LANTERN",
				box("STONE_BRICKS", -0.3f, 2.1f, -0.1f, 0.3f, 2.4f, 0.1f, false));
		perk(PerkType.WHOS_WHO, "BLUE_CONCRETE", "RED_CONCRETE", "WHITE_CONCRETE", "SEA_LANTERN");
		perk(PerkType.VULTURE_AID, "BROWN_CONCRETE", "GREEN_CONCRETE", "DARK_OAK_PLANKS", "GLOWSTONE");

		PAP = new Look("machine/pap", 1.0f, 1.5f, 2.0f, 2.4f, Arrays.asList(
				box("POLISHED_BLACKSTONE", -0.75f, 0.0f, -0.5f, 0.75f, 1.55f, 0.5f, false),
				box("IRON_BLOCK", -0.82f, 0.0f, -0.56f, 0.82f, 0.2f, 0.56f, false),
				box("IRON_BLOCK", -0.82f, 1.55f, -0.56f, 0.82f, 1.7f, 0.56f, false),
				box("GRAY_CONCRETE", -0.82f, 0.2f, -0.56f, -0.65f, 1.55f, -0.4f, false),
				box("GRAY_CONCRETE", 0.65f, 0.2f, -0.56f, 0.82f, 1.55f, -0.4f, false),
				box("SEA_LANTERN", -0.42f, 0.55f, -0.54f, 0.42f, 1.25f, -0.5f, true),
				box("PURPLE_STAINED_GLASS", -0.48f, 0.5f, -0.6f, 0.48f, 1.3f, -0.54f, true),
				box("SEA_LANTERN", -0.3f, 1.7f, -0.3f, 0.3f, 2.0f, 0.3f, true),
				box("PURPLE_STAINED_GLASS", -0.36f, 1.7f, -0.36f, 0.36f, 2.06f, 0.36f, true)));

		List<Part> boxParts = Arrays.asList(
				box("DARK_OAK_PLANKS", -1.0f, 0.0f, -0.5f, -0.5f, 0.85f, 0.5f, false),
				box("DARK_OAK_PLANKS", 0.5f, 0.0f, -0.5f, 1.0f, 0.85f, 0.5f, false),
				box("DARK_OAK_LOG", -1.02f, 0.85f, -0.52f, -0.5f, 0.98f, 0.52f, false),
				box("DARK_OAK_LOG", 0.5f, 0.85f, -0.52f, 1.02f, 0.98f, 0.52f, false),
				box("GLOWSTONE", -0.12f, 1.0f, -0.12f, 0.12f, 1.24f, 0.12f, true),
				box("YELLOW_STAINED_GLASS", -0.2f, 0.96f, -0.2f, 0.2f, 1.32f, 0.2f, true));
		BOX = new Look("machine/mystery_box", 1.0f, 0f, 0f, 1.7f, boxParts);
		// Only the pack model has a hinged lid; the look-alike keeps the vanilla chest's own lid animation.
		BOX_OPEN = new Look("machine/mystery_box_open", 1.0f, 0f, 0f, 1.7f, boxParts);

		AMMO_CRATE = new Look("machine/ammo_crate", 1.0f, 1.0f, 0.7f, 1.0f, Arrays.asList(
				box("GREEN_WOOL", -0.44f, 0.0f, -0.31f, 0.44f, 0.4f, 0.31f, false),
				box("GREEN_TERRACOTTA", -0.46f, 0.4f, -0.33f, 0.46f, 0.47f, 0.33f, false),
				box("OAK_PLANKS", -0.5f, 0.14f, -0.14f, -0.44f, 0.3f, 0.14f, false),
				box("OAK_PLANKS", 0.44f, 0.14f, -0.14f, 0.5f, 0.3f, 0.14f, false),
				box("OAK_PLANKS", -0.1f, 0.2f, -0.35f, 0.1f, 0.4f, -0.31f, false),
				box("OAK_PLANKS", -0.4f, 0.0f, -0.35f, -0.34f, 0.4f, -0.31f, false),
				box("OAK_PLANKS", 0.34f, 0.0f, -0.35f, 0.4f, 0.4f, -0.31f, false),
				box("GOLD_BLOCK", -0.38f, 0.47f, -0.2f, 0.38f, 0.55f, 0.2f, false)));

		POWER_SWITCH = powerSwitch("machine/power_switch", false);
		POWER_SWITCH_ON = powerSwitch("machine/power_switch_on", true);

		FRIDGE = new Look("machine/fridge", 1.0f, 1.0f, 1.8f, 2.2f, Arrays.asList(
				box("GRAY_CONCRETE", -0.32f, 0.0f, -0.3f, 0.32f, 0.11f, 0.3f, false),
				box("WHITE_CONCRETE", -0.375f, 0.11f, -0.34f, 0.375f, 1.75f, 0.34f, false),
				box("LIGHT_GRAY_CONCRETE", -0.385f, 1.3f, -0.35f, 0.385f, 1.34f, -0.33f, false),
				box("IRON_BLOCK", 0.2f, 1.45f, -0.4f, 0.28f, 1.7f, -0.34f, false),
				box("IRON_BLOCK", 0.2f, 0.3f, -0.4f, 0.28f, 1.12f, -0.34f, false),
				box("IRON_BLOCK", -0.375f, 1.75f, -0.34f, 0.375f, 1.8f, 0.34f, false),
				box("LIGHT_BLUE_STAINED_GLASS", -0.12f, 1.4f, -0.355f, 0.1f, 1.55f, -0.34f, true)));

		BANK = new Look("machine/bank", 1.0f, 1.0f, 1.3f, 1.7f, Arrays.asList(
				box("IRON_BLOCK", -0.4f, 0.0f, -0.4f, 0.4f, 0.1f, 0.4f, false),
				box("GRAY_CONCRETE", -0.37f, 0.1f, -0.37f, 0.37f, 0.78f, 0.37f, false),
				box("BLACK_CONCRETE", -0.37f, 0.78f, -0.37f, 0.37f, 1.18f, 0.37f, false),
				box("LIME_CONCRETE", -0.3f, 0.86f, -0.39f, 0.3f, 1.1f, -0.37f, true),
				box("IRON_BARS", -0.28f, 0.16f, -0.52f, 0.28f, 0.62f, -0.28f, false),
				box("IRON_BARS", -0.08f, 1.18f, -0.08f, 0.08f, 1.5f, 0.08f, false)));
	}

	private MachineLooks()
	{
	}

	/**
	 * Wall-mounted breaker: its back sits on the far side of the block. The lever is stacked boxes since a
	 * block display cannot tilt; the off state leans one way and the on state the other, with the lamps swapped.
	 */
	private static Look powerSwitch(String modelKey, boolean on)
	{
		float lean = on ? -1f : 1f;
		String left = on ? "LIME_CONCRETE" : "RED_CONCRETE";
		String right = on ? "RED_CONCRETE" : "LIME_CONCRETE";
		return new Look(modelKey, 1.0f, 0.7f, 1.0f, 1.3f, Arrays.asList(
				box("GRAY_CONCRETE", -0.31f, 0.09f, 0.19f, 0.31f, 0.97f, 0.5f, false),
				box("YELLOW_CONCRETE", -0.34f, 0.09f, 0.16f, 0.34f, 0.23f, 0.19f, false),
				box("BLACK_CONCRETE", -0.26f, 0.28f, 0.17f, 0.26f, 0.6f, 0.19f, false),
				box("IRON_BLOCK", -0.28f, 0.76f, 0.17f, 0.28f, 0.9f, 0.19f, false),
				box("POLISHED_ANDESITE", 0.03f * lean - 0.04f, 0.3f, 0.1f, 0.03f * lean + 0.04f, 0.42f, 0.17f, false),
				box("POLISHED_ANDESITE", 0.07f * lean - 0.04f, 0.42f, 0.1f, 0.07f * lean + 0.04f, 0.5f, 0.17f, false),
				box("RED_CONCRETE", 0.13f * lean - 0.07f, 0.5f, 0.07f, 0.13f * lean + 0.07f, 0.62f, 0.17f, false),
				box(left, -0.21f, 0.22f, 0.15f, -0.15f, 0.27f, 0.17f, true),
				box(right, 0.15f, 0.22f, 0.15f, 0.21f, 0.27f, 0.17f, true)));
	}

	private static Part box(String block, float x0, float y0, float z0, float x1, float y1, float z1, boolean glow)
	{
		return new Part(block, x0, y0, z0, x1, y1, z1, glow);
	}

	/** Standard ~2 block perk cabinet: body, front logo panel, base and cap trim, glowing lamp on top. */
	private static void perk(PerkType type, String body, String panel, String trim, String glow, Part... extras)
	{
		List<Part> parts = new ArrayList<>(Arrays.asList(
				box(body, -0.505f, 0.0f, -0.505f, 0.505f, 1.75f, 0.505f, false),
				box(trim, -0.54f, 0.0f, -0.54f, 0.54f, 0.25f, 0.54f, false),
				box(trim, -0.54f, 1.75f, -0.54f, 0.54f, 1.9f, 0.54f, false),
				box(panel, -0.32f, 0.75f, -0.56f, 0.32f, 1.45f, -0.5f, false),
				box(glow, -0.22f, 1.9f, -0.22f, 0.22f, 2.1f, 0.22f, true)));
		parts.addAll(Arrays.asList(extras));
		PERKS.put(type, new Look(type.getMachineModelKey(), 1.01f, 1.0f, 2.0f, 2.4f, parts));
	}

	/** Null for perks without a machine (Der Wunderfizz). */
	public static Look forPerk(PerkType perk)
	{
		return perk == null ? null : PERKS.get(perk);
	}

	/** The look for a [Zombies] feature sign's lines, or null when that sign is not a machine. */
	public static Look forSignLines(String[] lines)
	{
		if(lines == null || lines.length < 3 || lines[1] == null)
			return null;
		String type = ChatColor.stripColor(lines[1]).trim().toLowerCase();
		switch(type)
		{
			case "pack-a-punch":
				return PAP;
			case "mystery box":
				return BOX;
			case "ammo crate":
				return AMMO_CRATE;
			case "power":
				return POWER_SWITCH;
			case "fridge":
				return FRIDGE;
			case "bank":
				return BANK;
			case "perk machine":
				return lines[2] == null ? null : forPerk(PerkType.getPerkType(ChatColor.stripColor(lines[2])));
			default:
				return null;
		}
	}
}
