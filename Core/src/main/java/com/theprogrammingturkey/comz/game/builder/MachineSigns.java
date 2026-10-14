package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.visuals.MachineLooks;
import com.theprogrammingturkey.comz.game.visuals.MachineLooks.Look;
import com.theprogrammingturkey.comz.game.visuals.MachineVisuals;
import com.theprogrammingturkey.comz.game.signs.IGameSign;
import com.theprogrammingturkey.comz.listeners.SignListener;
import com.theprogrammingturkey.comz.listeners.ResourcePackListener;
import com.theprogrammingturkey.comz.util.PackModels;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.visuals.WallGunVisuals;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Places a fully-styled COM:Z feature sign in one shot for the in-game build mode, then re-dispatches
 * the very same {@link IGameSign#onChange} an admin would trigger by hand-typing the sign. This keeps a
 * single source of truth for sign line formatting AND feature registration (mystery box, trap, buildable,
 * quest, join... all self-register inside their {@code onChange}); the build tool just supplies the
 * "typed" lines and lets the existing handler do the rest.
 */
public final class MachineSigns
{
	private MachineSigns()
	{
	}

	/**
	 * Place a wall sign of the given feature type on {@code signBlock} facing {@code facing}, fill in the
	 * "typed" lines, and run the registered {@link IGameSign#onChange} for that type. Returns true on
	 * success (handler found and sign written).
	 *
	 * @param keyword the sign type keyword that goes on line index 1 (e.g. {@code "perk machine"})
	 * @param line2   value for sign line index 2 (perk name / cost / id / arena ...), or null
	 * @param line3   value for sign line index 3 (cost / price / gate flag ...), or null
	 */
	public static boolean placeSign(Game game, Player player, Block signBlock, BlockFace facing, String keyword, String line2, String line3)
	{
		IGameSign handler = SignListener.getSignHandler(keyword);
		if(handler == null)
			return false;

		signBlock.setType(Material.OAK_WALL_SIGN, false);
		BlockData data = signBlock.getBlockData();
		if(data instanceof Directional)
		{
			((Directional) data).setFacing(facing);
			signBlock.setBlockData(data, false);
		}

		String[] lines = new String[]{"[Zombies]", keyword, line2 == null ? "" : line2, line3 == null ? "" : line3};
		// The deprecated 3-arg SignChangeEvent ctor is the one already used elsewhere (PlayerChatListener),
		// so it is the safe choice across server versions. onChange reads/writes via this event's lines and
		// reads event.getBlock() (= signBlock) for registration, which is already placed above.
		@SuppressWarnings("deprecation")
		SignChangeEvent event = new SignChangeEvent(signBlock, player, lines);
		handler.onChange(game, player, event);

		Sign sign = (Sign) signBlock.getState();
		for(int i = 0; i < 4; i++)
			sign.setLine(i, event.getLine(i));
		sign.update(true);
		return true;
	}

	/** Calls {@code action} for every sign inside the game's arena (loaded chunks only). */
	public static void forEachArenaSign(Game game, Consumer<Sign> action)
	{
		World world = game.arena.getWorld();
		if(world == null || !game.arena.areMinAndMaxSet())
			return;
		Location min = game.arena.getMin();
		Location max = game.arena.getMax();
		int minCX = Math.min(min.getBlockX(), max.getBlockX()) >> 4;
		int maxCX = Math.max(min.getBlockX(), max.getBlockX()) >> 4;
		int minCZ = Math.min(min.getBlockZ(), max.getBlockZ()) >> 4;
		int maxCZ = Math.max(min.getBlockZ(), max.getBlockZ()) >> 4;

		for(int cx = minCX; cx <= maxCX; cx++)
			for(int cz = minCZ; cz <= maxCZ; cz++)
			{
				if(!world.isChunkLoaded(cx, cz))
					continue;
				for(BlockState state : world.getChunkAt(cx, cz).getTileEntities())
					if(state instanceof Sign && game.arena.containsBlock(state.getLocation()))
						action.accept((Sign) state);
			}
	}

	/**
	 * Whether a build-time preview for {@code player} may use the pack model: the pack is enabled and this
	 * player has it loaded. Anyone else sees the block look-alike (previews are visible to every player in range).
	 */
	public static boolean previewUsesPack(Player player)
	{
		return PackModels.isPackEnabled() && ResourcePackListener.hasPackLoaded(player);
	}

	/**
	 * Spawn the build-time preview for a placed sign, mirroring the in-game visuals of
	 * {@code MachineModelManager} / {@code RandomBox}: the pack model when {@code usePack}, else the block
	 * look-alike. Empty when the sign is not a machine or the server has no display entities.
	 */
	public static boolean isWallGun(Sign sign)
	{
		return "gun".equalsIgnoreCase(ChatColor.stripColor(sign.getLine(1)).trim());
	}

	public static List<Entity> previewModel(Sign sign, boolean usePack)
	{
		if(isWallGun(sign))
		{
			List<Entity> preview = new ArrayList<>();
			BlockData signData = sign.getBlockData();
			BlockFace wallFacing = com.theprogrammingturkey.comz.util.Compat.facingOf(signData);
			Entity model = WallGunVisuals.spawn(sign.getLocation(), wallFacing, WeaponManager.getGun(ChatColor.stripColor(sign.getLine(2))), usePack, 0.9);
			if(model != null)
				preview.add(model);
			return preview;
		}
		Look look = MachineLooks.forSignLines(sign.getLines());
		if(look == null)
			return new ArrayList<>();

		BlockFace facing = com.theprogrammingturkey.comz.util.Compat.facingOf(sign.getBlockData());

		// The preview sits on the sign's own block, where the machine / box chest stands in-game.
		return MachineVisuals.spawn(sign.getLocation(), facing, look, usePack);
	}
}
