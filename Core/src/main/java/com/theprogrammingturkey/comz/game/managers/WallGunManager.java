package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.builder.MachineSigns;
import com.theprogrammingturkey.comz.game.equipment.Equipment;
import com.theprogrammingturkey.comz.game.signs.EquipmentSign;
import com.theprogrammingturkey.comz.game.signs.GunSign;
import com.theprogrammingturkey.comz.game.visuals.WallGunVisuals;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.util.DisplayEntityUtil;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.util.display.Displays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * Replaces every wall-buy gun and equipment sign with the item's 3D model on the wall, a price hologram and an
 * invisible click box for the length of a game. The sign is snapshotted and rebuilt afterwards, so the
 * buy logic keeps running off the snapshotted lines.
 */
public class WallGunManager
{
	private static final long UPKEEP_TICKS = 10L;

	private final Game game;
	private final List<WallGun> wallGuns = new ArrayList<>();
	private final Map<UUID, WallGun> byInteraction = new HashMap<>();
	private final Map<String, int[]> pricesByGun = new HashMap<>();
	private int taskId = -1;

	public WallGunManager(Game game)
	{
		this.game = game;
	}

	public void spawnAll()
	{
		removeAll();
		if(game.arena.getWorld() == null || !game.arena.areMinAndMaxSet())
			return;

		MachineSigns.forEachArenaSign(game, this::recordPrices);
		if(!Displays.supportsHitboxes())
			return;

		MachineSigns.forEachArenaSign(game, this::trySpawn);
		if(wallGuns.isEmpty())
			return;

		taskId = COMZombies.scheduleTask(UPKEEP_TICKS, UPKEEP_TICKS, () ->
		{
			if(game.getStatus() != Game.GameStatus.INGAME)
			{
				removeAll();
				return;
			}
			for(WallGun wallGun : wallGuns)
				WallGunVisuals.sparkle(wallGun.model);
		});
	}

	private void recordPrices(Sign sign)
	{
		if(!MachineSigns.isWallGun(sign))
			return;
		String[] lines = sign.getLines();
		int[] prices = GunSign.parsePrices(lines);
		if(prices != null)
			pricesByGun.put(ChatColor.stripColor(lines[2]).trim().toLowerCase(), prices);
	}

	/** {buy price, refill price} of the wall gun selling this gun in the arena, or null when it is not a wall gun. */
	public int[] getWallPrices(BaseGun gun)
	{
		return gun == null ? null : pricesByGun.get(gun.getName().trim().toLowerCase());
	}

	private void trySpawn(Sign sign)
	{
		if(!MachineSigns.isWallBuy(sign))
			return;
		String[] lines = sign.getLines().clone();
		BlockData data = sign.getBlockData();
		BlockFace facing = com.theprogrammingturkey.comz.util.Compat.facingOf(data);
		boolean usePack = PackModels.isPackEnabled() && ConfigManager.getMainConfig().resourcePackForce;
		Location blockLoc = sign.getLocation();

		String name;
		int price;
		Entity model;
		if(MachineSigns.isWallGun(sign))
		{
			BaseGun gun = com.theprogrammingturkey.comz.game.managers.WeaponManager.getGun(ChatColor.stripColor(lines[2]));
			int[] prices = GunSign.parsePrices(lines);
			if(gun == null || prices == null)
				return;
			name = gun.getName();
			price = prices[0];
			model = WallGunVisuals.spawn(blockLoc, facing, gun, usePack, 0.0);
		}
		else
		{
			Equipment equipment = EquipmentSign.equipmentOf(lines);
			if(equipment == null)
				return;
			name = equipment.name;
			price = EquipmentSign.costOf(lines, equipment);
			boolean packModel = usePack && equipment.hasPackModel();
			model = WallGunVisuals.spawn(blockLoc, facing, equipment.displayStack(packModel), packModel ? equipment.itemModel : null, 0.0);
		}
		if(model == null)
			return;

		Entity interaction = Displays.spawnHitbox(blockLoc.clone().add(0.5, 0.0, 0.5), 1.4f, 1.0f);
		if(interaction == null)
		{
			model.remove();
			return;
		}
		WallGun wallGun = new WallGun(blockLoc, sign.getType(), data, lines, model);
		wallGun.interaction = interaction;
		HiddenSignJournal.record(sign, Material.AIR);
		blockLoc.getBlock().setType(Material.AIR, false);
		byInteraction.put(wallGun.interaction.getUniqueId(), wallGun);
		wallGun.label = ChatColor.AQUA + name + ChatColor.YELLOW + " [" + price + "]";
		wallGun.hologram = DisplayEntityUtil.persistentText(blockLoc.getWorld(), blockLoc.clone().add(0.5, 1.05, 0.5), wallGun.label);
		wallGuns.add(wallGun);
	}

	public boolean handleInteract(Player player, Entity entity)
	{
		WallGun wallGun = byInteraction.get(entity.getUniqueId());
		if(wallGun == null)
			return false;
		buy(player, wallGun);
		return true;
	}

	public boolean handleInteractByCentre(Player player, Location centre)
	{
		for(WallGun wallGun : wallGuns)
			if(sameBlock(wallGun.blockLoc, centre))
			{
				buy(player, wallGun);
				return true;
			}
		return false;
	}

	private void buy(Player player, WallGun wallGun)
	{
		if(game.getStatus() != Game.GameStatus.INGAME || !game.getLivingPlayers().contains(player)
				|| game.downedPlayerManager.isDownedPlayer(player) || game.downedPlayerManager.isGhost(player))
			return;
		if(!BuyDebounce.tryAcquire(player))
			return;
		if(isEquipment(wallGun.signLines))
			new EquipmentSign().onInteract(game, player, wallGun.blockLoc, wallGun.signLines);
		else
			new GunSign().onInteract(game, player, wallGun.blockLoc, wallGun.signLines);
	}

	private static boolean isEquipment(String[] lines)
	{
		return "equipment".equalsIgnoreCase(ChatColor.stripColor(lines[1]).trim());
	}

	/** Action-bar prompt for a wall gun or equipment wall buy. */
	public String prompt(Player player, String[] lines)
	{
		return isEquipment(lines) ? EquipmentSign.prompt(game, player, lines) : GunSign.prompt(game, player, lines);
	}

	private static boolean sameBlock(Location a, Location b)
	{
		return a.getWorld() != null && a.getWorld().equals(b.getWorld()) && a.getBlockX() == b.getBlockX()
				&& a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
	}

	/** Centre, shared label and per-player prompt source for every spawned wall gun. */
	public List<MachineModelManager.MachineTarget> getTargets()
	{
		List<MachineModelManager.MachineTarget> out = new ArrayList<>(wallGuns.size());
		for(WallGun wallGun : wallGuns)
			out.add(new MachineModelManager.MachineTarget(wallGun.blockLoc.clone().add(0.5, 0.5, 0.5), wallGun.label, wallGun.blockLoc, wallGun.signLines));
		return out;
	}

	public void removeAll()
	{
		if(taskId != -1)
		{
			Bukkit.getScheduler().cancelTask(taskId);
			taskId = -1;
		}
		for(WallGun wallGun : wallGuns)
			wallGun.remove();
		wallGuns.clear();
		byInteraction.clear();
		pricesByGun.clear();
	}

	private static final class WallGun
	{
		final Location blockLoc;
		final Material signMaterial;
		final BlockData signData;
		final String[] signLines;
		final Entity model;
		Entity interaction;
		Entity hologram;
		String label;

		WallGun(Location blockLoc, Material signMaterial, BlockData signData, String[] signLines, Entity model)
		{
			this.blockLoc = blockLoc;
			this.signMaterial = signMaterial;
			this.signData = signData;
			this.signLines = signLines;
			this.model = model;
		}

		void remove()
		{
			for(Entity entity : new Entity[]{model, interaction, hologram})
				if(entity != null && !entity.isDead())
					entity.remove();
			try
			{
				Block block = blockLoc.getBlock();
				block.setType(signMaterial, false);
				if(signData != null)
					block.setBlockData(signData, false);
				BlockState state = block.getState();
				if(state instanceof Sign)
				{
					Sign sign = (Sign) state;
					for(int i = 0; i < signLines.length && i < 4; i++)
						sign.setLine(i, signLines[i]);
					sign.update(true);
				}
				HiddenSignJournal.clear(blockLoc);
			} catch(RuntimeException e)
			{
				COMZombies.log.warning("Failed to restore a wall gun sign: " + e.getMessage());
			}
		}
	}
}
