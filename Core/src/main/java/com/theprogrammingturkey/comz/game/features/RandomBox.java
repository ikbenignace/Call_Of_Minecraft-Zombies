package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.Weapon;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.CommandUtil;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
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
	private TextDisplay priceHologram;

	private Player openedBy;

	private boolean running;
	private boolean gunSelected;
	private boolean isTeddyBear;
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

	public void Start(Player player, int pointsNeeded)
	{
		if(boxGame == null)
			return;

		if(!(GameManager.INSTANCE.isPlayerInGame(player)))
			return;

		if(!PointManager.INSTANCE.canBuy(player, pointsNeeded))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			return;
		}

		// In-game the box IS the chest at boxLoc (loadBox placed it there); no neighbour scan needed.
		Location chestLocation = boxLoc;

		openedBy = player;

		if(chestLocation != null)
			COMZombies.nmsUtil.playChestAction(chestLocation, true);

		running = true;
		weapon = WeaponManager.getRandomWeapon(false, boxGame.getPlayersWeapons(player));
		Location itemLoc = getBoxCenterLoc(chestLocation);

		item = player.getWorld().dropItem(itemLoc, weapon.getStack());
		namePlate = (ArmorStand) player.getWorld().spawnEntity(itemLoc.clone().add(0, -1.7, 0), EntityType.ARMOR_STAND);
		namePlate.setVisible(false);
		namePlate.setGravity(false);
		namePlate.setAI(false);
		namePlate.setCustomName(weapon.getName());
		namePlate.setCustomNameVisible(true);

		PointManager.INSTANCE.takePoints(player, pointsNeeded);
		PointManager.INSTANCE.notifyPlayer(player);

		int taskID = COMZombies.scheduleTask(0, 10, new Runnable()
		{
			int time = 10;

			public void run()
			{
				item.setTicksLived(5960);
				item.setPickupDelay(1000);
				item.setVelocity(new Vector(0, 0, 0));
				if(boxGame.getStatus() == Game.GameStatus.INGAME)
				{
					if(time > 0)
					{
						weapon = WeaponManager.getRandomWeapon(false, boxGame.getPlayersWeapons(player));
						item.setItemStack(weapon.getStack());
						namePlate.setCustomName(weapon.getName());
						com.theprogrammingturkey.comz.util.SoundUtil.play(player.getWorld(), boxLoc, com.theprogrammingturkey.comz.util.SoundConfig.get("box.spin", Sound.BLOCK_NOTE_BLOCK_HARP.name()), org.bukkit.SoundCategory.MASTER, 1f, 1f);
					}
					else if(time == 0)
					{
						if(!boxGame.isFireSale() && !boxGame.boxManager.isMultiBox() && boxGame.getTeddyBearPercent() != 0 && COMZombies.rand.nextInt(boxGame.getTeddyBearPercent()) == 0)
						{
							CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_RED + "Teddy Bear!!!!!!");
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
									boxGame.boxManager.teddyBear();
									PointManager.INSTANCE.addPoints(player, pointsNeeded);
									reset();
								});
							});
						}
						else
						{
							com.theprogrammingturkey.comz.util.SoundUtil.play(player.getWorld(), boxLoc, com.theprogrammingturkey.comz.util.SoundConfig.get("box.lock", Sound.ENTITY_EXPERIENCE_ORB_PICKUP.name()), org.bukkit.SoundCategory.MASTER, 1f, 1f);
							gunSelected = true;
						}
					}
					else if(time == -20)
					{
						reset();
					}
					else if(!isTeddyBear)
					{
							namePlate.setCustomName(weapon.getName() + " (" + (20 + time) + ")");
					}
					time--;
				}
			}
		});

		boxes.put(this, taskID);
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
		if(data instanceof Chest chest && chest.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE)
		{
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
		return switch(f)
		{
			case NORTH -> BlockFace.EAST;
			case EAST -> BlockFace.SOUTH;
			case SOUTH -> BlockFace.WEST;
			case WEST -> BlockFace.NORTH;
			default -> f;
		};
	}

	private static BlockFace rotateFaceCCW(BlockFace f)
	{
		return switch(f)
		{
			case NORTH -> BlockFace.WEST;
			case WEST -> BlockFace.SOUTH;
			case SOUTH -> BlockFace.EAST;
			case EAST -> BlockFace.NORTH;
			default -> f;
		};
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
		boxGame.getPlayersWeapons(player).addWeapon(weapon);
		com.theprogrammingturkey.comz.util.SoundUtil.play(player.getWorld(), player.getLocation(), com.theprogrammingturkey.comz.util.SoundConfig.get("box.pickup", Sound.BLOCK_LAVA_POP.name()), org.bukkit.SoundCategory.MASTER, 1, 1);
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
			block.setType(Material.CHEST, false);

		if(priceHologram == null || priceHologram.isDead())
		{
			World world = boxLoc.getWorld();
			if(world != null)
			{
				String label = ChatColor.AQUA + "Mystery Box " + ChatColor.YELLOW + "$" + getCost();
				priceHologram = com.theprogrammingturkey.comz.util.DisplayEntityUtil.persistentText(
						world, boxLoc.clone().add(0.5, 1.4, 0.5), label);
			}
		}
	}

	/** Edit/lobby representation: the authoring sign on boxLoc. */
	private void showSign()
	{
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
		if(priceHologram != null && !priceHologram.isDead())
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
					com.theprogrammingturkey.comz.util.SoundConfig.get("box.open", Sound.BLOCK_CHEST_OPEN.name()),
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
		return boxGame.isFireSale() ? 10 : boxCost;
	}
}
