package com.theprogrammingturkey.comz.integration.wm;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import me.deecaad.weaponmechanics.weapon.damage.DamagePoint;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Bridges WeaponMechanics hits into the COM:Z economy. When a WM-backed COM:Z gun damages a zombie,
 * WM's own damage is cancelled and the hit is re-applied through {@link Game#damageMob}, which is the
 * single source of truth for round-scaled mob health, points-on-hit, headshot/kill bonuses, power-up
 * drops and wave progression. Only firing <i>delivery</i> differs from the native path — the game
 * rules are identical.
 *
 * <p>Registered only when the WeaponMechanics backend is active (see {@code COMZombies.onEnable}).
 */
public class WMDamageListener implements Listener
{
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onWeaponDamage(WeaponDamageEntityEvent event)
	{
		LivingEntity shooterEnt = event.getShooter();
		if(!(shooterEnt instanceof Player))
			return;
		Player shooter = (Player) shooterEnt;

		if(!GameManager.INSTANCE.isPlayerInGame(shooter))
			return;
		if(!(event.getVictim() instanceof Mob))
			return;

		Game game = GameManager.INSTANCE.getGame(shooter);
		if(game.getStatus() != GameStatus.INGAME)
			return;

		Mob mob = (Mob) event.getVictim();
		// Only act on COM:Z's own arena mobs; ignore unrelated entities the player might shoot.
		if(!game.spawnManager.getEntities().contains(mob))
			return;

		// WeaponMechanics must not apply its own damage to a COM:Z mob — COM:Z owns mob health.
		event.setCancelled(true);

		// Resolve the COM:Z gun from the WM weapon title so we use COM:Z's configured damage, not WM's.
		BaseGun gun = WeaponManager.getGunByWmWeapon(event.getWeaponTitle());
		float damage = gun != null ? gun.damage : (float) event.getFinalDamage();

		boolean headshot = event.getPoint() == DamagePoint.HEAD;
		if(headshot)
		{
			damage *= 1.5f;
			// Tier 1 — Deadshot Daiquiri: extra headshot damage, same multiplier as the native path.
			if(game.perkManager.hasPerk(shooter, PerkType.DEADSHOT_DAIQ))
				damage *= (float) ConfigManager.getMainConfig().deadshotHeadshotMultiplier;
		}

		game.damageMob(mob, shooter, damage, headshot, false);
	}
}
