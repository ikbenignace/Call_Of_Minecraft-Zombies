package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.DownedPlayer;
import com.theprogrammingturkey.comz.game.managers.PlayerSpeed;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.weapons.WeaponType;
import com.theprogrammingturkey.comz.util.BreathingGear;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ScopeListener implements Listener
{
	@EventHandler
	public void onPlayerSneak(final PlayerToggleSneakEvent e)
	{
		Player player = e.getPlayer();
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || !game.isPlayerAlive(player))
			return;

		DownedPlayer downed = game.downedPlayerManager.getDownedPlayer(player);
		if(downed != null && downed.isPlayerDown())
		{
			// Sneaking dismounts the client from the down seat; put them straight back on it.
			COMZombies.scheduleTask(1, downed::keepSeated);
			return;
		}

		if(!e.isSneaking())
		{
			stopAiming(player);
			return;
		}

		if(game.getStatus() != Game.GameStatus.INGAME)
			return;
		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		if(!manager.isHeldItemGun())
			return;
		GunInstance gun = manager.getGun(player.getInventory().getHeldItemSlot());
		boolean scoped = gun != null && gun.getType().getWeaponType() == WeaponType.SNIPER_RIFLES;
		PlayerSpeed.setAiming(player, scoped ? PlayerSpeed.Aim.SCOPE : PlayerSpeed.Aim.SIGHTS);
		if(scoped && COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.ZoomTexture") && !BreathingGear.isWearing(player))
			player.getInventory().setHelmet(new ItemStack(Material.JACK_O_LANTERN, 1));
	}

	private static void stopAiming(Player player)
	{
		if(!PlayerSpeed.isAiming(player))
			return;
		PlayerSpeed.setAiming(player, null);
		ItemStack helmet = player.getInventory().getHelmet();
		if(helmet != null && helmet.getType() == Material.JACK_O_LANTERN)
			player.getInventory().setHelmet(defaultHelmet());
	}

	private static ItemStack defaultHelmet()
	{
		ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
		ItemMeta meta = helmet.getItemMeta();
		if(meta != null)
		{
			meta.setUnbreakable(true);
			helmet.setItemMeta(meta);
		}
		return helmet;
	}
}
