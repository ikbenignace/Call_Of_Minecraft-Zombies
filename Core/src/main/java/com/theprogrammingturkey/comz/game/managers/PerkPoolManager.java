package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.builder.MachineSigns;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.ParticleFX;
import com.theprogrammingturkey.comz.util.SoundConfig;
import com.theprogrammingturkey.comz.util.SoundUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Perk placement pools (Shi No Numa): with {@code perk_pool_mode = random_on_open} the arena's perk machine
 * signs are taken off their walls at game start, shuffled, and each one appears at a random pool spot when a
 * door opens (or when a trigger's {@code spawn_perk} target fires). {@code fixed} leaves perks where they are.
 * A revealed perk is a real perk sign written at the spot and, where display entities exist, turned into a
 * machine, so buying it is the normal perk path. {@code random_on_start} fills every spot with a different pooled
 * perk as soon as the game starts; perks left over when the spots run out stay hidden for {@code spawn_perk}.
 * Everything is undone on teardown.
 */
public class PerkPoolManager
{
	public enum Mode
	{
		FIXED, RANDOM_ON_OPEN, RANDOM_ON_START;

		public String key()
		{
			return name().toLowerCase(Locale.ROOT);
		}

		public static Mode fromKey(String key)
		{
			for(Mode mode : values())
				if(mode.name().equalsIgnoreCase(key == null ? "" : key.trim()))
					return mode;
			return FIXED;
		}
	}

	/** Where a pooled machine may appear: the block it occupies and the way it faces. */
	public static final class Spot
	{
		public final Vector block;
		public final BlockFace facing;

		public Spot(Vector block, BlockFace facing)
		{
			this.block = new Vector(block.getBlockX(), block.getBlockY(), block.getBlockZ());
			this.facing = facing;
		}

		public Location location(World world)
		{
			return new Location(world, block.getX(), block.getY(), block.getZ());
		}
	}

	/** A spot that received a machine this game, with the block it replaced. */
	private static final class Placed
	{
		final Location location;
		final Material material;
		final BlockData data;

		Placed(Location location, Material material, BlockData data)
		{
			this.location = location;
			this.material = material;
			this.data = data;
		}
	}

	private final Game game;
	private final List<Spot> spots = new ArrayList<>();
	private Mode mode = Mode.FIXED;
	/** When false only {@code spawn_perk} trigger targets reveal pooled perks, not doors. */
	private boolean revealOnDoor = true;

	private boolean active;
	private final List<String[]> pending = new ArrayList<>();
	private final List<Spot> freeSpots = new ArrayList<>();
	private final List<SignSnapshot> hiddenSigns = new ArrayList<>();
	private final List<Placed> placed = new ArrayList<>();

	public PerkPoolManager(Game game)
	{
		this.game = game;
	}

	public Mode getMode()
	{
		return mode;
	}

	public void setMode(Mode mode)
	{
		this.mode = mode == null ? Mode.FIXED : mode;
	}

	public boolean isRevealOnDoor()
	{
		return revealOnDoor;
	}

	public void setRevealOnDoor(boolean revealOnDoor)
	{
		this.revealOnDoor = revealOnDoor;
	}

	public List<Spot> getSpots()
	{
		return Collections.unmodifiableList(spots);
	}

	public void addSpot(Spot spot)
	{
		removeSpotAt(spot.block);
		spots.add(spot);
	}

	public boolean removeSpotAt(Vector block)
	{
		Iterator<Spot> it = spots.iterator();
		while(it.hasNext())
		{
			Spot spot = it.next();
			if(spot.block.getBlockX() == block.getBlockX() && spot.block.getBlockY() == block.getBlockY() && spot.block.getBlockZ() == block.getBlockZ())
			{
				it.remove();
				return true;
			}
		}
		return false;
	}

	public void load(JsonObject saveJson, JsonObject settingsJson)
	{
		mode = Mode.fromKey(CustomConfig.getString(settingsJson, "perk_pool_mode", Mode.FIXED.key()));
		revealOnDoor = !CustomConfig.getString(settingsJson, "perk_pool_reveal", "door").equalsIgnoreCase("trigger");
		spots.clear();
		if(saveJson.has("perk_pool_spots") && saveJson.get("perk_pool_spots").isJsonArray())
			for(JsonElement element : saveJson.getAsJsonArray("perk_pool_spots"))
			{
				if(!element.isJsonObject())
					continue;
				JsonObject json = element.getAsJsonObject();
				BlockFace facing;
				try
				{
					facing = BlockFace.valueOf(CustomConfig.getString(json, "facing", "NORTH").toUpperCase(Locale.ROOT));
				} catch(IllegalArgumentException e)
				{
					facing = BlockFace.NORTH;
				}
				spots.add(new Spot(new Vector(CustomConfig.getInt(json, "x", 0), CustomConfig.getInt(json, "y", 0), CustomConfig.getInt(json, "z", 0)), facing));
			}
	}

	public void save(JsonObject saveJson, JsonObject settingsJson)
	{
		settingsJson.addProperty("perk_pool_mode", mode.key());
		settingsJson.addProperty("perk_pool_reveal", revealOnDoor ? "door" : "trigger");
		JsonArray array = new JsonArray();
		for(Spot spot : spots)
		{
			JsonObject json = new JsonObject();
			json.addProperty("x", spot.block.getBlockX());
			json.addProperty("y", spot.block.getBlockY());
			json.addProperty("z", spot.block.getBlockZ());
			json.addProperty("facing", spot.facing.name());
			array.add(json);
		}
		saveJson.add("perk_pool_spots", array);
	}

	/**
	 * Takes the perk machine signs off the map before the machines are built. Must run before
	 * {@link MachineModelManager#spawnAll()} so the hidden perks do not get a machine at their fixed spot.
	 */
	public void start()
	{
		restore();
		if(mode == Mode.FIXED || spots.isEmpty())
			return;
		MachineSigns.forEachArenaSign(game, sign ->
		{
			if(!ChatColor.stripColor(sign.getLine(1)).trim().equalsIgnoreCase("perk machine"))
				return;
			SignSnapshot snapshot = SignSnapshot.of(sign);
			pending.add(sign.getLines().clone());
			snapshot.hide();
			hiddenSigns.add(snapshot);
		});
		Collections.shuffle(pending);
		freeSpots.addAll(spots);
		Collections.shuffle(freeSpots);
		active = true;
	}

	/** {@code random_on_start}: reveals a pooled perk on every spot. Runs after the fixed machines are built. */
	public void revealOnStart()
	{
		if(!active || mode != Mode.RANDOM_ON_START)
			return;
		boolean revealed;
		do
			revealed = revealNext();
		while(revealed);
	}

	public void onDoorOpened()
	{
		if(active && revealOnDoor && mode == Mode.RANDOM_ON_OPEN)
			revealNext();
	}

	/**
	 * Target {@code spawn_perk}: {@code pool} (or blank) reveals the next pooled perk; a perk type reveals that
	 * pooled perk if it is still hidden, otherwise places a new machine of that type at {@code at} or a free spot.
	 */
	public boolean spawnPerk(String value, Location at)
	{
		if(value == null || value.trim().isEmpty() || value.equalsIgnoreCase("pool") || value.equalsIgnoreCase("next"))
			return revealNext();
		PerkType perk = PerkType.getPerkType(value.trim().replace(' ', '_'));
		if(perk == null)
			perk = PerkType.getPerkType(value.trim());
		if(perk == null)
			return false;

		String[] pooled = null;
		for(String[] candidate : pending)
			if(PerkType.getPerkType(ChatColor.stripColor(candidate[2]).trim()) == perk)
			{
				pooled = candidate;
				break;
			}
		String[] lines = pooled != null ? pooled
				: new String[]{ChatColor.RED + "[Zombies]", ChatColor.AQUA + "Perk Machine", perk.toString().toLowerCase(Locale.ROOT), Integer.toString(perk.getDefaultCost())};

		Spot spot = at != null ? new Spot(at.toVector(), BlockFace.SOUTH) : nextFreeSpot();
		if(spot == null || !place(lines, spot))
			return false;
		if(pooled != null)
			pending.remove(pooled);
		freeSpots.remove(spot);
		return true;
	}

	private boolean revealNext()
	{
		if(pending.isEmpty())
			return false;
		Spot spot = nextFreeSpot();
		if(spot == null || !place(pending.get(0), spot))
			return false;
		pending.remove(0);
		freeSpots.remove(spot);
		return true;
	}

	/** First free spot, dropping spots a perk was already placed on by an explicit {@code spawn_perk} location. */
	private Spot nextFreeSpot()
	{
		World world = game.getWorld();
		while(!freeSpots.isEmpty())
		{
			Spot spot = freeSpots.get(0);
			if(world == null || !isOccupied(spot.location(world).getBlock()))
				return spot;
			freeSpots.remove(0);
		}
		return null;
	}

	private boolean isOccupied(Block block)
	{
		for(Placed existing : placed)
			if(existing.location.getBlock().equals(block))
				return true;
		return false;
	}

	/** Builds the perk sign and machine at {@code spot}; false when nothing was placed so the caller keeps the perk. */
	private boolean place(String[] lines, Spot spot)
	{
		World world = game.getWorld();
		if(world == null)
			return false;
		Location location = spot.location(world);
		Block block = location.getBlock();
		if(isOccupied(block))
			return false;
		placed.add(new Placed(location, block.getType(), block.getBlockData()));
		// Journal the spot's real block first: a crash mid-game then restores the empty spot, not a perk sign.
		HiddenSignJournal.record(location, block.getType(), block.getBlockData(), new String[4], null, Material.OAK_WALL_SIGN);

		block.setType(Material.OAK_WALL_SIGN, false);
		BlockData data = block.getBlockData();
		if(data instanceof Directional)
		{
			((Directional) data).setFacing(spot.facing);
			block.setBlockData(data, false);
		}
		BlockState state = block.getState();
		if(!(state instanceof Sign))
			return false;
		Sign sign = (Sign) state;
		for(int i = 0; i < 4 && i < lines.length; i++)
			sign.setLine(i, lines[i] == null ? "" : lines[i]);
		sign.update(true);
		game.machineModelManager.spawnFromSign((Sign) block.getState());

		Location centre = location.clone().add(0.5, 0.5, 0.5);
		ParticleFX.lightColumn(world, centre, 3.0);
		SoundUtil.play(world, centre, SoundConfig.get("trigger.perk_reveal", "BLOCK_BEACON_ACTIVATE"), SoundCategory.MASTER, 1.5f, 1f);
		return true;
	}

	/**
	 * Puts every pool spot back to its original block and the hidden perk signs back on their walls. Runs after
	 * the machines are removed (that turns a revealed machine back into its sign first).
	 */
	public void restore()
	{
		for(Placed spot : placed)
		{
			try
			{
				Block block = spot.location.getBlock();
				block.setType(spot.material, false);
				block.setBlockData(spot.data, false);
			} catch(RuntimeException ignored)
			{
				// A world that is gone has nothing to restore.
			}
			HiddenSignJournal.clear(spot.location);
		}
		placed.clear();
		for(SignSnapshot snapshot : hiddenSigns)
			snapshot.restore();
		hiddenSigns.clear();
		pending.clear();
		freeSpots.clear();
		active = false;
	}
}
