package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PerkType;
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
 * own block: a solid base block (always visible, even without the pack), a 3D item model
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

	private void trySpawnForSign(Sign sign)
	{
		String type = ChatColor.stripColor(sign.getLine(1)).trim().toLowerCase();
		Material base;
		String label;
		if(type.equals("pack-a-punch"))
		{
			base = Compat.materialOr(Material.OBSIDIAN, "LODESTONE");
			label = ChatColor.LIGHT_PURPLE + "Pack-a-Punch " + ChatColor.YELLOW + "[" + ChatColor.stripColor(sign.getLine(2)) + "]";
		}
		else if(type.equals("perk machine"))
		{
			PerkType perk = PerkType.getPerkType(ChatColor.stripColor(sign.getLine(2)));
			if(perk == null)
				return;
			base = Material.BARREL;
			label = ChatColor.AQUA + titleCase(perk.toString()) + ChatColor.YELLOW + " [" + ChatColor.stripColor(sign.getLine(3)) + "]";
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
		BlockFace facing = BlockFace.NORTH;
		BlockData data = sign.getBlockData();
		if(data instanceof Directional)
			facing = ((Directional) data).getFacing();

		// The machine occupies the sign's own block (the sign's material/facing/text are snapshotted into
		// the MachineModel and fully rebuilt on teardown).
		Location baseLoc = signLoc.clone();
		String[] signLines = sign.getLines().clone();
		MachineModel machine = new MachineModel(baseLoc, signLoc, sign.getType(), data, signLines);

		baseLoc.getBlock().setType(base, false);
		if(ChatColor.stripColor(signLines[1]).trim().equalsIgnoreCase("pack-a-punch"))
			PackAPunchSign.registerMachine(baseLoc, facing);

		// The pack model is only used when the pack is enabled AND forced, so EVERY player has it (a single
		// shared ItemDisplay would otherwise render as a missing-model cube for pack-less players); everyone
		// else gets the block look-alike.
		boolean usePack = PackModels.isPackEnabled() && ConfigManager.getMainConfig().resourcePackForce;
		machine.visuals.addAll(MachineVisuals.spawn(baseLoc, facing, look, usePack));

		// Invisible clickable hitbox covering the whole machine.
		Entity interaction = Displays.spawnHitbox(baseLoc.clone().add(0.5, 0.0, 0.5), look.hitWidth, look.hitHeight);
		machine.interaction = interaction;
		byInteraction.put(interaction.getUniqueId(), machine);

		// Floating price above the machine.
		Entity hologram = DisplayEntityUtil.persistentText(world, baseLoc.clone().add(0.5, look.hologramY, 0.5), label);
		machine.hologram = hologram;
		machine.label = label;

		machines.add(machine);
	}

	/**
	 * Right-click on a machine interaction → run the backing sign's buy logic (INGAME only). Returns true
	 * if the entity was one of our machines and was handled. Called by {@code MachineInteractListener}.
	 */
	public boolean handleInteract(Player player, Entity entity)
	{
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

	private static String titleCase(String enumName)
	{
		String s = enumName.toLowerCase().replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Remove all machines: despawn entities and restore base blocks. Safe to call repeatedly. */
	public void removeAll()
	{
		PackAPunchSign.clearGame(game);
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