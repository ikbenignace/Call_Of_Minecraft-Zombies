package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.Wunderfizz;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.game.builder.MachineSigns;
import com.theprogrammingturkey.comz.game.signs.IGameSign;
import com.theprogrammingturkey.comz.game.signs.PackAPunchSign;
import com.theprogrammingturkey.comz.game.visuals.MachineLooks;
import com.theprogrammingturkey.comz.game.visuals.MachineLooks.Look;
import com.theprogrammingturkey.comz.game.visuals.MachineVisuals;
import com.theprogrammingturkey.comz.listeners.SignListener;
import com.theprogrammingturkey.comz.util.Compat;
import com.theprogrammingturkey.comz.util.DisplayEntityUtil;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BO2-fidelity machines. Each perk / Pack-a-Punch sign becomes a physical machine occupying the sign's
 * own block: an invisible solid barrier to bump into, a 3D item model
 * (pack guaranteed for all players) or a block-display look-alike overlaying the block, an invisible
 * interaction hitbox so the machine is right-clickable, and a floating price
 * text. Right-clicking the interaction forwards to the backing sign's buy logic (the
 * sign's lines are snapshotted at spawn so buying works while the sign is hidden, and the sign is
 * rebuilt on teardown).
 *
 * <p>Spawned at game start by scanning the arena's loaded chunk tile-entities for feature signs, and
 * fully torn down (entities + restored signs) on game end.
 */
public class MachineModelManager
{
	private final Game game;
	private final List<MachineModel> machines = new ArrayList<>();
	private final Map<UUID, MachineModel> byInteraction = new HashMap<>();
	/** Hitboxes owned by mystery boxes; the box creates and removes them, this only routes clicks. */
	private final Map<UUID, RandomBox> boxHitboxes = new HashMap<>();

	public MachineModelManager(Game game)
	{
		this.game = game;
	}

	/** Scan the arena for perk / Pack-a-Punch signs and build a physical machine for each. */
	public void spawnAll()
	{
		removeAll();

		World world = game.arena.getWorld();
		if(world == null || !game.arena.areMinAndMaxSet())
			return;
		// Without a clickable hitbox the machine block couldn't be bought, so pre-1.19.4 servers keep
		// the plain signs.
		if(!Displays.supportsHitboxes())
			return;

		MachineSigns.forEachArenaSign(game, this::trySpawnForSign);
	}

	/** Builds the machine for one sign placed during play (a revealed pooled perk); no-op without hitboxes. */
	public void spawnFromSign(Sign sign)
	{
		if(Displays.supportsHitboxes())
			trySpawnForSign(sign);
	}

	private void trySpawnForSign(Sign sign)
	{
		String type = ChatColor.stripColor(sign.getLine(1)).trim().toLowerCase();
		Material base;
		String label;
		if(type.equals("pack-a-punch"))
		{
			base = Material.BARRIER;
			label = ChatColor.LIGHT_PURPLE + "Pack-a-Punch " + ChatColor.YELLOW + "[" + ChatColor.stripColor(sign.getLine(2)) + "]";
		}
		else if(type.equals("perk machine"))
		{
			PerkType perk = PerkType.getPerkType(ChatColor.stripColor(sign.getLine(2)));
			if(perk == null)
				return;
			base = Material.BARRIER;
			label = ChatColor.AQUA + titleCase(perk.toString()) + ChatColor.YELLOW + " [" + ChatColor.stripColor(sign.getLine(3)) + "]";
		}
		else if(type.equals("ammo crate"))
		{
			base = Material.BARRIER;
			label = ChatColor.AQUA + "Ammo Crate";
		}
		else if(type.equals("power"))
		{
			base = Material.AIR; // wall-mounted: nothing to collide with, the model hangs on the wall behind the sign block
			label = ChatColor.AQUA + "Power Switch";
		}
		else if(type.equals("fridge"))
		{
			base = Material.BARRIER;
			label = ChatColor.AQUA + "Fridge";
		}
		else if(type.equals("bank"))
		{
			base = Material.BARRIER;
			label = ChatColor.AQUA + "Bank";
		}
		else
		{
			return; // mystery box owns its own visuals (chest + model) via RandomBox
		}
		Look look = MachineLooks.forSignLines(sign.getLines());
		if(look == null)
			return;

		spawn(sign, look, base, label);
	}

	/** Build the base block + visuals + interaction + hologram for one machine sign. */
	private void spawn(Sign sign, Look look, Material base, String label)
	{
		Location signLoc = sign.getLocation();
		World world = signLoc.getWorld();
		BlockFace facing = com.theprogrammingturkey.comz.util.Compat.facingOf(sign.getBlockData());

		// The machine occupies the sign's own block (the sign's material/facing/text are snapshotted into
		// the MachineModel and fully rebuilt on teardown), dropped to the floor when the sign hangs higher
		// up the wall. The wall-mounted power switch stays where its sign is.
		Location baseLoc = base == Material.AIR ? signLoc.clone() : floorBelow(signLoc);
		String[] signLines = sign.getLines().clone();

		// Created before the sign block is overwritten: without a hitbox the machine can't be bought, so
		// the plain sign stays in place instead.
		Entity interaction = Displays.spawnHitbox(baseLoc.clone().add(0.5, 0.0, 0.5), look.hitWidth, look.hitHeight);
		if(interaction == null)
			return;
		boolean lowered = !sameBlock(baseLoc, signLoc);
		HiddenSignJournal.record(sign, lowered ? Material.AIR : base);
		BlockData floorData = null;
		if(lowered)
		{
			floorData = baseLoc.getBlock().getBlockData();
			HiddenSignJournal.record(baseLoc, baseLoc.getBlock().getType(), floorData, null, null, base);
			signLoc.getBlock().setType(Material.AIR, false);
		}
		MachineModel machine = new MachineModel(baseLoc, signLoc, sign.getType(), sign.getBlockData(), signLines, facing, look, floorData);
		machine.interaction = interaction;

		try
		{
			buildMachine(machine, base, label, world);
		} catch(RuntimeException e)
		{
			COMZombies.log.warning("Could not build the machine at " + baseLoc.getBlockX() + "," + baseLoc.getBlockY() + "," + baseLoc.getBlockZ() + ": " + e);
			machine.remove();
			return;
		}
		byInteraction.put(interaction.getUniqueId(), machine);
		machines.add(machine);
	}

	private void buildMachine(MachineModel machine, Material base, String label, World world)
	{
		Location baseLoc = machine.baseLoc;
		String[] signLines = machine.signLines;
		BlockFace facing = machine.facing;
		Look look = machine.look;

		baseLoc.getBlock().setType(base, false);
		if(ChatColor.stripColor(signLines[1]).trim().equalsIgnoreCase("pack-a-punch"))
			PackAPunchSign.registerMachine(baseLoc, facing);
		if(look == MachineLooks.forPerk(PerkType.DER_WUNDERFIZZ))
			Wunderfizz.registerMachine(game, baseLoc, facing);

		// The pack model is only used when the pack is enabled AND forced, so EVERY player has it (a single
		// shared ItemDisplay would otherwise render as a missing-model cube for pack-less players); everyone
		// else gets the block look-alike.
		boolean usePack = PackModels.isPackEnabled() && ConfigManager.getMainConfig().resourcePackForce;
		machine.usePack = usePack;
		machine.powerGated = look != MachineLooks.AMMO_CRATE && look != MachineLooks.POWER_SWITCH
				&& look != MachineLooks.FRIDGE && look != MachineLooks.BANK;
		machine.visuals.addAll(MachineVisuals.spawn(baseLoc, facing, look, usePack));
		if(machine.powerGated && game.hasPower())
			MachineVisuals.setPowered(machine.visuals, look, game.isPowered());
		if(look == MachineLooks.POWER_SWITCH && game.isPowered())
			flipSwitch(machine, true);

		// Floating price above the machine.
		Entity hologram = DisplayEntityUtil.persistentText(world, baseLoc.clone().add(0.5, look.hologramY, 0.5), label);
		machine.hologram = hologram;
		machine.label = label;
	}

	/**
	 * Right-click on a machine interaction → run the backing sign's buy logic (INGAME only). Returns true
	 * if the entity was one of our machines and was handled. Called by {@code MachineInteractListener}.
	 */
	public boolean handleInteract(Player player, Entity entity)
	{
		RandomBox box = boxHitboxes.get(entity.getUniqueId());
		if(box != null)
		{
			if(game.getStatus() == Game.GameStatus.INGAME && game.getLivingPlayers().contains(player)
					&& !game.downedPlayerManager.isDownedPlayer(player) && !game.downedPlayerManager.isGhost(player)
					&& BuyDebounce.tryAcquire(player))
				box.interact(player);
			return true;
		}
		MachineModel machine = byInteraction.get(entity.getUniqueId());
		if(machine == null)
			return false;
		return runMachineBuy(player, machine);
	}

	/**
	 * Proximity-buy trigger: run the backing sign's buy logic for the machine whose base centre matches
	 * the given location (as returned by {@link MachineTarget#centre}). Returns true if a machine at
	 * that location was found and handled. Used by the F-key path in {@code OnInventoryChangeEvent}.
	 */
	public boolean handleInteractByCentre(Player player, Location centre)
	{
		for(MachineModel m : machines)
		{
			Location c = m.baseLoc.clone().add(0.5, 0.5, 0.5);
			if(c.getBlockX() == centre.getBlockX() && c.getBlockY() == centre.getBlockY() && c.getBlockZ() == centre.getBlockZ()
					&& c.getWorld() != null && c.getWorld().equals(centre.getWorld()))
				return runMachineBuy(player, m);
		}
		return false;
	}

	private boolean runMachineBuy(Player player, MachineModel machine)
	{
		if(game.getStatus() != Game.GameStatus.INGAME)
			return true; // it's ours, but nothing to do outside a running game
		if(!game.getLivingPlayers().contains(player) || game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
			return true;
		if(!BuyDebounce.tryAcquire(player))
			return true;

		// The backing sign has been overwritten by the machine's base block during play, so the buy path
		// runs off the sign lines snapshotted at spawn rather than re-reading the world.
		String[] lines = machine.signLines;
		if(lines == null || lines.length < 2)
			return true;
		IGameSign handler = SignListener.getSignHandler(ChatColor.stripColor(lines[1]).toLowerCase());
		if(handler != null)
			handler.onInteract(game, player, machine.baseLoc, lines);
		return true;
	}

	/** Maps often hang buy signs at eye height; a machine belongs on the floor, at most two blocks lower. */
	private static Location floorBelow(Location signLoc)
	{
		Location at = signLoc.clone();
		for(int i = 0; i < 2; i++)
		{
			Block below = at.getBlock().getRelative(BlockFace.DOWN);
			if(!below.isPassable() || below.getState() instanceof Sign)
				break;
			at.subtract(0, 1, 0);
		}
		return at;
	}

	private static boolean sameBlock(Location a, Location b)
	{
		return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
	}

	private static String titleCase(String enumName)
	{
		String s = enumName.toLowerCase().replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public void registerBoxHitbox(Entity hitbox, RandomBox box)
	{
		boxHitboxes.put(hitbox.getUniqueId(), box);
	}

	public void unregisterBoxHitbox(Entity hitbox)
	{
		boxHitboxes.remove(hitbox.getUniqueId());
	}

	/**
	 * Power came on or went off: perk machines and Pack-a-Punch light up or go dark, and the power switch
	 * flips with a spark burst.
	 */
	public void onPowerChanged(boolean on)
	{
		for(MachineModel machine : machines)
		{
			World world = machine.baseLoc.getWorld();
			if(machine.look == MachineLooks.POWER_SWITCH || machine.look == MachineLooks.POWER_SWITCH_ON)
			{
				flipSwitch(machine, on);
				if(on && world != null)
					world.spawnParticle(Compat.ELECTRIC_SPARK, machine.baseLoc.clone().add(0.5, 0.6, 0.35), 60, 0.25, 0.3, 0.1, 0.4);
			}
			else if(machine.powerGated)
			{
				MachineVisuals.setPowered(machine.visuals, machine.look, on);
				if(on && world != null)
					world.spawnParticle(Compat.ELECTRIC_SPARK, machine.baseLoc.clone().add(0.5, 1.0, 0.5), 30, 0.35, 0.8, 0.35, 0.3);
			}
		}
	}

	private void flipSwitch(MachineModel machine, boolean on)
	{
		Look target = on ? MachineLooks.POWER_SWITCH_ON : MachineLooks.POWER_SWITCH;
		if(machine.look != target)
		{
			MachineVisuals.remove(machine.visuals);
			machine.look = target;
			machine.visuals.addAll(MachineVisuals.spawn(machine.baseLoc, machine.facing, target, machine.usePack));
		}
		if(on && machine.hologram != null)
		{
			machine.hologram.remove();
			machine.hologram = null;
		}
		if(on)
			machine.label = ChatColor.GREEN + "Power Switch (on)";
	}

	/** Remove all machines: despawn entities and restore base blocks. Safe to call repeatedly. */
	public void removeAll()
	{
		PackAPunchSign.clearGame(game);
		Wunderfizz.clearGame(game);
		for(MachineModel machine : machines)
			machine.remove();
		machines.clear();
		byInteraction.clear();
	}

	/**
	 * Snapshot of a machine for proximity-buy scanning: the centre of its base block (where a player
	 * must stand to buy it) and its floating price label (already formatted with color + cost).
	 */
	public static final class MachineTarget
	{
		public final Location centre;
		public final String label;
		/** Buy-logic location and snapshotted sign lines, for building per-player prompts. */
		public final Location base;
		public final String[] signLines;

		MachineTarget(Location centre, String label, Location base, String[] signLines)
		{
			this.centre = centre;
			this.label = label;
			this.base = base;
			this.signLines = signLines;
		}
	}

	/**
	 * Live snapshot of every spawned machine (base centre + price label) for the proximity-buy
	 * scanner. Empty when machines aren't spawned (e.g. before game start or pack disabled).
	 */
	public List<MachineTarget> getMachineTargets()
	{
		List<MachineTarget> out = new ArrayList<>(machines.size());
		for(MachineModel m : machines)
			out.add(new MachineTarget(m.baseLoc.clone().add(0.5, 0.5, 0.5), labelFor(m), m.baseLoc, m.signLines));
		return out;
	}

	/**
	 * Every perk machine in the arena (block centre to perk), from the spawned machine models plus any perk signs
	 * still standing (servers without machine models keep plain signs). Der Wunderfizz is not a single perk and is
	 * left out.
	 */
	public Map<Location, PerkType> getPerkMachines()
	{
		Map<Location, PerkType> out = new java.util.LinkedHashMap<>();
		for(MachineModel m : machines)
			addPerkMachine(out, m.baseLoc, m.signLines);
		MachineSigns.forEachArenaSign(game, sign -> addPerkMachine(out, sign.getLocation(), sign.getLines()));
		return out;
	}

	private static void addPerkMachine(Map<Location, PerkType> out, Location block, String[] lines)
	{
		if(lines == null || lines.length < 3 || !ChatColor.stripColor(lines[1]).trim().equalsIgnoreCase("perk machine"))
			return;
		PerkType perk = PerkType.getPerkType(ChatColor.stripColor(lines[2]).trim());
		if(perk == null || perk == PerkType.DER_WUNDERFIZZ)
			return;
		out.put(block.getBlock().getLocation().add(0.5, 0.5, 0.5), perk);
	}

	private String labelFor(MachineModel m)
	{
		if(m.label != null)
			return m.label; // already formatted, e.g. "§bJuggernog §e$2500"
		// Fallback when the hologram was never created (pack-less servers): rebuild from the snapshotted
		// sign lines (the world block is the machine's base block during play, not the sign).
		String[] lines = m.signLines;
		if(lines != null && lines.length >= 2)
		{
			String type = ChatColor.stripColor(lines[1]).trim().toLowerCase();
			if(type.equals("pack-a-punch"))
				return ChatColor.LIGHT_PURPLE + "Pack-a-Punch " + ChatColor.YELLOW + "$" + (lines.length > 2 ? ChatColor.stripColor(lines[2]) : "");
			if(type.equals("perk machine"))
				return ChatColor.AQUA + (lines.length > 2 ? ChatColor.stripColor(lines[2]) : "Perk") + ChatColor.YELLOW + " $" + (lines.length > 3 ? ChatColor.stripColor(lines[3]) : "");
		}
		return ChatColor.AQUA + "Machine";
	}
}