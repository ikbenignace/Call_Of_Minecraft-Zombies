package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.visuals.MachineLooks;
import com.theprogrammingturkey.comz.game.visuals.MachineVisuals;
import com.theprogrammingturkey.comz.game.weapons.Weapon;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.PackModels;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RandomBox
{
	public static Map<RandomBox, Integer> boxes = new HashMap<>();
	/** The box's anchor block. In-game this is the CHEST; in edit/lobby it's the authoring SIGN. */
	private final Location boxLoc;
	private final BlockFace facing;
	private final Game boxGame;
	private final String boxId;
	private final int boxCost;

	/** Floating price hologram spawned in-game above the chest; owned by this box. */
	private Entity priceHologram;

	/** Pack model or block look-alike drawn around the chest in-game; owned by this box. */
	private final List<Entity> visuals = new ArrayList<>();

	private Player openedBy;

	private boolean running;
	private boolean gunSelected;
	private boolean isTeddyBear;
	/** Spins at this location since the box last moved. */
	private int useCount;
	private Weapon weapon;
	private Item item;
	private ArmorStand namePlate;


	public RandomBox(Location loc, BlockFace facing, Game game, String boxId, int cost)
	{
		boxLoc = loc;
		this.facing = facing;
		boxGame = game;
		this.boxId = boxId;
		boxCost = cost;
		this.running = false;
		this.gunSelected = false;
		this.isTeddyBear = false;
	}

	private static final int STEP_TICKS = 5;
	private static final int STEPS_PER_SECOND = 20 / STEP_TICKS;

	public void Start(Player player, int pointsNeeded)
	{
		if(boxGame == null || running)
			return;

		if(!(GameManager.INSTANCE.isPlayerInGame(player)))
			return;

		if(!PointManager.INSTANCE.canBuy(player, pointsNeeded))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			return;
		}

		weapon = WeaponManager.getRandomWeapon(false, boxGame.getPlayersWeapons(player));
		if(weapon == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "There is nothing left in the box for you!");
			return;
		}

		// In-game the box IS the chest at boxLoc (loadBox placed it there); no neighbour scan needed.
		Location chestLocation = boxLoc;

		PointManager.INSTANCE.takePoints(player, pointsNeeded);
		PointManager.INSTANCE.notifyPlayer(player);

		openedBy = player;
		useCount++;

		if(chestLocation != null)
			COMZombies.nmsUtil.playChestAction(chestLocation, true);

		running = true;
		Location itemLoc = getBoxCenterLoc(chestLocation);

		item = player.getWorld().dropItem(itemLoc, weapon.getStack());
		namePlate = (ArmorStand) player.getWorld().spawnEntity(itemLoc.clone().add(0, -1.7, 0), EntityType.ARMOR_STAND);
		namePlate.setVisible(false);
		namePlate.setPersistent(false);
		namePlate.setGravity(false);
		namePlate.setAI(false);
		namePlate.setCustomName(weapon.getName());
		namePlate.setCustomNameVisible(true);

		final int rollSteps = Math.max(1, (int) Math.round(ConfigManager.getMainConfig().boxRollSeconds * STEPS_PER_SECOND));
		final int pickupSteps = Math.max(1, (int) Math.round(ConfigManager.getMainConfig().boxPickupSeconds * STEPS_PER_SECOND));

		int taskID = COMZombies.scheduleTask(0, STEP_TICKS, new Runnable()
		{
			int time = rollSteps;

			public void run()
			{
				item.setTicksLived(5960);
				item.setPickupDelay(1000);
				item.setVelocity(new Vector(0, 0, 0));
				if(boxGame.getStatus() == Game.GameStatus.INGAME)
				{
					if(time > 0)
					{
						Weapon rolled = WeaponManager.getRandomWeapon(false, boxGame.getPlayersWeapons(player));
						if(rolled != null)
							weapon = rolled;
						item.setItemStack(weapon.getStack());
						namePlate.setCustomName(weapon.getName());
						if(time % 2 == 0)
							com.theprogrammingturkey.comz.util.SoundUtil.play(player.getWorld(), boxLoc, com.theprogrammingturkey.comz.util.SoundConfig.get("box.spin", "BLOCK_NOTE_BLOCK_HARP"), org.bukkit.SoundCategory.MASTER, 1f, 1f);
					}
					else if(time == 0)
					{
						if(teddyRolled())
						{
							CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_RED + "Teddy Bear!!!!!!");
							com.theprogrammingturkey.comz.util.SoundUtil.play(player.getWorld(), boxLoc, com.theprogrammingturkey.comz.util.SoundConfig.get("box.teddy", "ENTITY_WITHER_SPAWN"), org.bukkit.SoundCategory.MASTER, 1f, 1f);
							ItemStack teddy = new ItemStack(Material.TOTEM_OF_UNDYING);
							com.theprogrammingturkey.comz.util.PackModels.apply(teddy, "misc/box_teddy");
							item.setItemStack(teddy);
							namePlate.setCustomName("");
							namePlate.setCustomNameVisible(false);
							item.setGravity(false);
							item.setCustomName("Teddy Bear");
							item.setCustomNameVisible(true);
							isTeddyBear = true;

							COMZombies.scheduleTask(40, () ->
							{
								int velTask = COMZombies.scheduleTask(0, 5, () -> item.setVelocity(new Vector(0, 0.1, 0)));

								COMZombies.scheduleTask(60, () ->
								{
									Bukkit.getScheduler().cancelTask(velTask);
									useCount = 0;
									boxGame.boxManager.teddyBear();
									PointManager.INSTANCE.addPoints(player, pointsNeeded);
									reset();
								});
							});
						}
						else
						{
							com.theprogrammingturkey.comz.util.SoundUtil.play(player.getWorld(), boxLoc, com.theprogrammingturkey.comz.util.SoundConfig.get("box.lock", "ENTITY_EXPERIENCE_ORB_PICKUP"), org.bukkit.SoundCategory.MASTER, 1f, 1f);
							gunSelected = true;
						}
					}
					else if(time <= -pickupSteps)
					{
						reset();
					}
					else if(!isTeddyBear)
					{
						int secondsLeft = (pickupSteps + time + STEPS_PER_SECOND - 1) / STEPS_PER_SECOND;
						namePlate.setCustomName(weapon.getName() + " (" + secondsLeft + ")");
					}
					time--;
				}
			}
		});

		boxes.put(this, taskID);
	}

	/** Teddy needs a few uses at this location first; the 1-in-N roll applies after that, and a cap forces it. */
	private boolean teddyRolled()
	{
		if(boxGame.isFireSale() || boxGame.boxManager.isMultiBox())
			return false;
		int minUses = ConfigManager.getMainConfig().boxTeddyMinUses;
		if(useCount <= minUses)
			return false;
		int maxUses = ConfigManager.getMainConfig().boxTeddyMaxUses;
		if(maxUses > 0 && useCount >= maxUses)
			return true;
		int chance = boxGame.getTeddyBearPercent();
		return chance != 0 && COMZombies.rand.nextInt(chance) == 0;
	}

	/**
	 * Returns the location the spinning weapon should occupy: the horizontal centre of the box's
	 * chest, one block above it. For a double chest the centre is the midpoint of both halves, so
	 * the weapon (and its nameplate) sits in the middle of the full chest instead of over one half
	 * — matching the "weapon appears in the middle of the box" expectation.
	 */
	private Location getBoxCenterLoc(Location chestLocation)
	{
		Location base = chestLocation.clone();
		Block chestBlock = chestLocation.getBlock();
		BlockData data = chestBlock.getBlockData();
		if(data instanceof Chest && ((Chest) data).getType() != org.bukkit.block.data.type.Chest.Type.SINGLE)
		{
			Chest chest = (Chest) data;
			// Double chest: step to the other half via the facing direction and average the two centres.
			BlockFace other = chest.getFacing();
			// LEFT/RIGHT halves sit relative to the chest's facing; use the type to pick the offset.
			org.bukkit.block.data.type.Chest.Type t = chest.getType();
			if(t == org.bukkit.block.data.type.Chest.Type.LEFT)
				other = rotateFaceCW(chest.getFacing());
			else if(t == org.bukkit.block.data.type.Chest.Type.RIGHT)
				other = rotateFaceCCW(chest.getFacing());
			Block otherHalf = chestBlock.getRelative(other);
			if(otherHalf.getType() == Material.CHEST || otherHalf.getType() == Material.TRAPPED_CHEST)
			{
				Location a = chestLocation.clone().add(.5, 0, .5);
				Location b = otherHalf.getLocation().clone().add(.5, 0, .5);
				base = new Location(chestLocation.getWorld(), (a.getX() + b.getX()) / 2.0, chestLocation.getY(), (a.getZ() + b.getZ()) / 2.0);
			}
		}
		return base.add(0, 1.0, 0);
	}

	private static BlockFace rotateFaceCW(BlockFace f)
	{
		switch(f)
		{
			case NORTH:
				return BlockFace.EAST;
			case EAST:
				return BlockFace.SOUTH;
			case SOUTH:
				return BlockFace.WEST;
			case WEST:
				return BlockFace.NORTH;
			default:
				return f;
		}
	}

	private static BlockFace rotateFaceCCW(BlockFace f)
	{
		switch(f)
		{
			case NORTH:
				return BlockFace.WEST;
			case WEST:
				return BlockFace.SOUTH;
			case SOUTH:
				return BlockFace.EAST;
			case EAST:
				return BlockFace.NORTH;
			default:
				return f;
		}
	}

	public boolean canActivate()
	{
		return !this.running;
	}

	public boolean canPickWeapon(Player player)
	{
		return player.equals(openedBy) && this.gunSelected;
	}

	public void pickUpWeapon(Player player)
	{
		if(!gunSelected || weapon == null)
			return;
		com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager manager = boxGame.getPlayersWeapons(player);
		if(weapon instanceof com.theprogrammingturkey.comz.game.weapons.BaseGun && manager.hasGun((com.theprogrammingturkey.comz.game.weapons.BaseGun) weapon))
			manager.getGun((com.theprogrammingturkey.comz.game.weapons.BaseGun) weapon).maxAmmo();
		else
			manager.addWeapon(weapon);
		com.theprogrammingturkey.comz.util.SoundUtil.play(player.getWorld(), player.getLocation(), com.theprogrammingturkey.comz.util.SoundConfig.get("box.pickup", "BLOCK_LAVA_POP"), org.bukkit.SoundCategory.MASTER, 1, 1);
		reset();
	}

	public void reset()
	{
		gunSelected = false;
		isTeddyBear = false;
		if(item != null)
			item.remove();
		if(namePlate != null)
			namePlate.remove();
		// In-game the chest sits at boxLoc; close its lid animation.
		if(boxLoc != null && boxGame.getStatus() == Game.GameStatus.INGAME)
			COMZombies.nmsUtil.playChestAction(boxLoc, false);
		Integer id = RandomBox.boxes.remove(RandomBox.this);
		if(id != null)
			Bukkit.getScheduler().cancelTask(id);
		running = false;

		if(!boxGame.boxManager.isMultiBox() && !boxGame.isFireSale() && boxGame.boxManager.getCurrentbox() != this)
			this.removeBox();
	}

	/**
	 * Show the box in the world. Context-aware:
	 * <ul>
	 *   <li><b>In-game (INGAME):</b> the chest appears on {@code boxLoc} with a floating price
	 *       hologram above it — matching the look of the real game (no sign).</li>
	 *   <li><b>Edit / lobby (not INGAME):</b> an authoring {@code [Zombies] / Mystery Box} sign is
	 *       shown on {@code boxLoc}, so builders can see/move the box.</li>
	 * </ul>
	 */
	public void loadBox()
	{
		if(boxLoc == null)
		{
			Bukkit.getServer().broadcastMessage("Mysterybox " + this.getId() + "Is broken and has no location!! what did you do!!");
			return;
		}

		if(boxGame != null && boxGame.getStatus() == Game.GameStatus.INGAME)
			showChest();
		else
			showSign();
	}

	/** In-game representation: a CHEST on boxLoc + a price hologram floating above it. */
	private void showChest()
	{
		Block block = boxLoc.getBlock();
		// Only (re)place if it isn't already a chest — avoid wiping a mid-spin chest state.
		if(block.getType() != Material.CHEST)
		{
			block.setType(Material.CHEST, false);
			BlockData data = block.getBlockData();
			if(data instanceof Directional && ((Directional) data).getFaces().contains(facing))
			{
				((Directional) data).setFacing(facing);
				block.setBlockData(data, false);
			}
		}

		// The chest stays (it carries the click and lid animation); the model / crate is drawn around it.
		if(!MachineVisuals.anyAlive(visuals))
		{
			boolean usePack = PackModels.isPackEnabled() && ConfigManager.getMainConfig().resourcePackForce;
			visuals.clear();
			visuals.addAll(MachineVisuals.spawn(boxLoc, facing, MachineLooks.BOX, usePack));
		}

		if(priceHologram == null || priceHologram.isDead())
		{
			World world = boxLoc.getWorld();
			if(world != null)
			{
				String label = ChatColor.AQUA + "Mystery Box " + ChatColor.YELLOW + "$" + getCost();
				priceHologram = com.theprogrammingturkey.comz.util.DisplayEntityUtil.persistentText(
						world, boxLoc.clone().add(0.5, MachineLooks.BOX.hologramY, 0.5), label);
				if(priceHologram != null)
					priceHologram.setPersistent(false);
			}
		}
	}

	/** Edit/lobby representation: the authoring sign on boxLoc. */
	private void showSign()
	{
		// The lobby/edit view never shows a price hologram; this also clears the current box's
		// hologram at game end, which reset() deliberately leaves standing mid-game.
		removePriceHologram();
		MachineVisuals.remove(visuals);
		Block block = boxLoc.getBlock();
		block.setType(Material.OAK_WALL_SIGN);
		BlockData blockData = block.getBlockData();
		if(blockData instanceof Directional)
			((Directional) blockData).setFacing(facing);
		block.setBlockData(blockData);
		Sign sign = (Sign) block.getState();
		sign.setLine(0, ChatColor.RED + "[Zombies]");
		sign.setLine(1, ChatColor.AQUA + "Mystery Box");
		sign.setLine(2, String.valueOf(boxCost));
		sign.update();
	}

	/**
	 * Remove the box's world representation. Clears whatever loadBox placed (chest+hologram in-game,
	 * sign in lobby) back to air. No-op while a spin is running.
	 */
	public void removeBox()
	{
		if(this.running)
			return;
		BlockUtils.setBlockToAir(boxLoc);
		removePriceHologram();
		MachineVisuals.remove(visuals);
	}

	private void removePriceHologram()
	{
		if(priceHologram != null)
		{
			priceHologram.remove();
			priceHologram = null;
		}
	}

	/**
	 * Uniform entry point for any box interaction (right-click the chest, click the sign, or the F
	 * proximity-buy key). Starts a spin when idle, picks up the weapon once selected. Owned here so
	 * every trigger source shares the same logic and fire-sale cost override.
	 */
	public void interact(Player player)
	{
		if(canActivate())
		{
			if(!PointManager.INSTANCE.canBuy(player, getCost()))
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
				return;
			}
			Start(player, getCost());
			com.theprogrammingturkey.comz.util.SoundUtil.play(player.getWorld(), player.getLocation(),
					com.theprogrammingturkey.comz.util.SoundConfig.get("box.open", "BLOCK_CHEST_OPEN"),
					org.bukkit.SoundCategory.MASTER, 1, 1);
		}
		else if(canPickWeapon(player))
		{
			pickUpWeapon(player);
		}
	}

	public Location getLocation()
	{
		return boxLoc;
	}

	public BlockFace getFacing()
	{
		return facing;
	}

	public String getId()
	{
		return boxId;
	}


	public int getCost()
	{
		return boxGame.isFireSale() ? ConfigManager.getMainConfig().boxFireSaleCost : boxCost;
	}
}
