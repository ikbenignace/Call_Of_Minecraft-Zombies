package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.BlockUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Barrier implements Runnable
{
	// Full BlockData (not just Material) so barrier window blocks rebuild EXACTLY as authored —
	// plank/stair orientation, fence/pane connections, etc. — instead of losing that metadata.
	private final Map<Block, BlockData> blocks = new HashMap<>();
	private Location repairLoc;
	private BlockFace signFacing;
	private final List<SpawnPoint> spawns = new ArrayList<>();

	private int stage;
	private boolean breaking = false;

	private final String id;

	private final Game game;

	private int reward;

	private final HashMap<Player, Integer> earnedPoints = new HashMap<>();

	public Barrier(String id, Game game)
	{
		stage = 0;
		this.id = id;
		this.game = game;
	}

	public boolean damage()
	{
		stage++;

		if(stage > 5)
			stage = 5;

		game.updateBarrierDamage(stage, blocks.keySet());

		if(stage >= 5)
		{
			for(Block b : blocks.keySet())
				BlockUtils.setBlockToAir(b);
			return true;
		}
		else
		{
			// The [BarrierRepair] sign is the classic "break-to-repair" interaction point. It is also
			// a common glitch surface (stale signs, double-place, wrong facing). When the alternative
			// interaction layer is on, skip spawning it entirely — BarrierRepairListener handles
			// repair via hold-sneak, keyed off repairLoc (not the sign). The repairLoc field itself
			// is still set/used for the proximity check, only the block is left as air.
			if(stage > -1 && com.theprogrammingturkey.comz.config.ConfigManager.getMainConfig().spawnBarrierRepairSign)
			{
				Block block = repairLoc.getBlock();
				block.setType(Material.OAK_WALL_SIGN);
				BlockData blockData = block.getBlockData();
				((Directional) blockData).setFacing(signFacing);
				block.setBlockData(blockData);
				Sign sign = (Sign) block.getState();
				sign.setLine(0, "[BarrierRepair]");
				sign.setLine(1, "Break this to");
				sign.setLine(2, "repair the");
				sign.setLine(3, "barrier");
				sign.update(true);
			}
			return false;
		}
	}

	public boolean repair(Player player)
	{
		// #70 — block the repair-while-being-broken exploit. Players used to be able to spam-repair
		// (shift-to-repair or sign-break) while zombies were actively breaking the same barrier,
		// ping-ponging the stage with no lock. While a zombie is actively breaking this barrier,
		// repairs are rejected so the breaker-out race is no longer winnable by spam.
		if(breaking)
			return stage <= -1;

		stage--;

		if(stage < -1)
			stage = -1;

		game.updateBarrierDamage(stage, blocks.keySet());
		int pointsEarned = earnedPoints.getOrDefault(player, 0);
		// Global config reward per repair level (not per-barrier), capped at reward * 6 (the 6 stages).
		int reward = com.theprogrammingturkey.comz.config.ConfigManager.getMainConfig().barrierRepairPoints;
		if(pointsEarned < reward * 6)
		{
			earnedPoints.put(player, pointsEarned + reward);
			PointManager.INSTANCE.addPoints(player, reward);
			PointManager.INSTANCE.notifyPlayer(player);
		}

		if(stage == -1)
			BlockUtils.setBlockToAir(repairLoc);

		for(Block b : blocks.keySet())
			if(game.getWorld().getBlockAt(b.getLocation()).getType().equals(Material.AIR))
				game.getWorld().getBlockAt(b.getLocation()).setBlockData(blocks.get(b), false);
		return stage <= -1;
	}

	public void repairFull()
	{
		stage = -1;

		game.updateBarrierDamage(-1, blocks.keySet());

		for(Block b : blocks.keySet())
			if(b.getType().equals(Material.AIR))
				b.setBlockData(blocks.get(b), false);

		BlockUtils.setBlockToAir(repairLoc);

		this.breaking = false;
	}

	public void resetEarnedPoints()
	{
		earnedPoints.replaceAll((p, v) -> 0);
	}

	public void addBarrierBlock(Location loc)
	{
		Block block = loc.getBlock();
		this.addBarrierBlock(block, block.getBlockData());
	}

	public void addBarrierBlock(Block block, BlockData data)
	{
		blocks.put(block, data);
	}

	public void removeBarrierBlock(Location loc)
	{
		blocks.remove(loc.getBlock());
	}

	public List<Block> getBlocks()
	{
		return new ArrayList<>(blocks.keySet());
	}

	public boolean hasBarrierLoc(Block b)
	{
		return blocks.containsKey(b);
	}

	public BlockData getBlockData(Block b)
	{
		return blocks.get(b);
	}

	public int getStage()
	{
		return stage;
	}

	public void addSpawnPoints(List<SpawnPoint> sps)
	{
		spawns.addAll(sps);
	}

	public void addSpawnPoint(SpawnPoint sp)
	{
		spawns.add(sp);
	}

	public boolean hasSpawnPoint(SpawnPoint sp)
	{
		return spawns.contains(sp);
	}

	public List<SpawnPoint> getSpawnPoints()
	{
		return spawns;
	}

	public String getID()
	{
		return id;
	}

	public int getReward()
	{
		return reward;
	}

	public void setReward(int reward)
	{
		this.reward = reward;
	}

	public Location getRepairLoc()
	{
		return repairLoc;
	}

	public void setRepairLoc(Location repairLoc)
	{
		this.repairLoc = repairLoc;
	}

	public BlockFace getSignFacing()
	{
		return signFacing;
	}

	public void setSignFacing(BlockFace signFacing)
	{
		if(signFacing == BlockFace.UP || signFacing == BlockFace.DOWN)
			signFacing = BlockFace.NORTH;
		this.signFacing = signFacing;
	}

	public Game getGame()
	{
		return game;
	}

	/**
	 * #70 — The barrier break interval (ticks between damage stages). Configurable via
	 * {@code config.barrier.breakInterval}, and scales down as rounds progress so zombies break
	 * through faster on higher rounds. Clamped to a minimum so it never becomes instant.
	 */
	private int breakIntervalTicks()
	{
		com.theprogrammingturkey.comz.config.ConfigSetup cfg = com.theprogrammingturkey.comz.config.ConfigManager.getMainConfig();
		int base = cfg.barrierBreakInterval;
		// Round scaling: subtract one tick per round above 1, down to a hard floor. This makes
		// barriers feel more urgent as the game goes on without a sudden snap.
		int scaled = base - Math.max(0, game.getWave() - 1);
		return Math.max(cfg.barrierBreakIntervalMin, scaled);
	}

	/**
	 * Drives the barrier-breaking schedule. Each tick damages the barrier one stage if it is still
	 * intact, then reschedules itself at {@link #breakIntervalTicks()}. Breaking stops when the
	 * barrier is fully broken (stage >= 5) or when {@link BarrierManager#tickBarriers} clears the
	 * {@code breaking} flag because no zombie is in proximity any more.
	 */
	public void update()
	{
		if(!breaking)
			return;

		if(!this.damage())
			COMZombies.scheduleTask(breakIntervalTicks(), this);
		else
			this.breaking = false;
	}

	/**
	 * #130/#96 — Starts barrier breaking driven by zombie proximity (called by
	 * {@link BarrierManager#tickBarriers} when a zombie is near the barrier). Replaces the old
	 * spawn-point-linked {@code initBarrier(zombie)} model where breaking was tied to the specific
	 * zombie that spawned at a linked point.
	 */
	public void startBreaking()
	{
		if(breaking)
			return;
		breaking = true;
		COMZombies.scheduleTask(breakIntervalTicks(), this);
	}

	/** Stops the proximity-driven breaking (no zombie nearby any more). */
	public void stopBreaking()
	{
		breaking = false;
	}

	public boolean isBreaking()
	{
		return breaking;
	}

	@Override
	public void run()
	{
		update();
	}
}