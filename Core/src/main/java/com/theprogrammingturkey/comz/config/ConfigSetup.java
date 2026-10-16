package com.theprogrammingturkey.comz.config;

import com.theprogrammingturkey.comz.game.managers.PlayerDataManager;
import com.theprogrammingturkey.comz.leaderboards.Leaderboard;
import com.theprogrammingturkey.comz.COMZombies;

import java.util.List;

public class ConfigSetup
{
	/**
	 * Default points when a player shoots a zombie.
	 */
	public int pointsOnHit;
	/**
	 * Default points when a player kills a zombie.
	 */
	public int pointsOnKill;
	/**
	 * Time it takes to revive a player.
	 */
	public int reviveTimer;
	/**
	 * Maximum wave.
	 */
	public int maxWave;
	/**
	 * Interval between round change and zombie spawn.
	 */
	public int waveSpawnInterval;
	/**
	 * Max zombies that can be contained in an arena or game.
	 */
	public int maxZombies;
	/**
	 * Does the plugin nag the player a message when they login?
	 */
	public String configVersion;
	/**
	 * Time it takes to reload a gun.
	 */
	public int reloadTime;
	/**
	 * Time double points is active for.
	 */
	public int doublePointsTimer;
	/**
	 * Time insta kill is active for.
	 */
	public int instaKillTimer;
	/**
	 * Maximum range the Reviver can remove from whoever (s)he is reviving.
	 */
	public int reviveRange;
	/**
	 * Maximum range melees are allowed for. Values over 6 -> 6 (Maximum vanilla reach) for now.
	 */
	public float meleeRange;
	/**
	 * Time Fire salse is active for.
	 */
	public int fireSaleTimer;
	/**
	 * Seconds a ground power-up stays before expiring (BO default 30). Was hardcoded in
	 * PowerUpManager and the config key was dead; now wired through.
	 */
	public double perkLife;
	/**
	 * Time it takes for an arena to start.
	 */
	public int arenaStartTime;
	/**
	 * Max possible perks a player can obtain.
	 */
	public int maxPerks;

	public int KillMoney;

	public float roundSoundVolume;

	public double zombieDamage;

	public double juggernogHealth;

	/** Zombie hits that down a player without Juggernog; when above 0 it overrides zombieDamage. */
	public int zombieHitsToDown;
	/** Zombie hits that down a player with Juggernog; when above 0 it overrides juggernogHealth. */
	public int juggernogHitsToDown;
	/** A dead player respawning at the next round keeps their own points (BO) instead of getting 500 x round. */
	public boolean respawnKeepsPoints;
	/** Revive every downed player for free when a round ends (not a BO rule). */
	public boolean reviveDownedOnRoundEnd;
	/** Walk-speed multiplier while aiming down sights with a non-sniper gun. */
	public double aimWalkSpeedMultiplier;
	/** Walk-speed multipliers while wading: feet in water, and water up to the waist or deeper. */
	public double waterWalkMultiplier;
	public double waterDeepWalkMultiplier;

	public double healTime;

	// ---- BO2 fidelity (Tier 0) -------------------------------------------------

	/**
	 * 0b — Base number of zombies allowed alive on the board for a single player.
	 * Real board max = zombieBoardBase + zombieBoardPerPlayer * (players - 1).
	 */
	public int zombieBoardBase;
	/**
	 * 0b — Extra board capacity added per additional player beyond the first.
	 */
	public int zombieBoardPerPlayer;
	public double zombieSpawnDelayMin;
	public int zombieCountBase;
	/** Zombies per extra player in the BO round count (solo adds half of it). */
	public int zombieCountPerPlayer;
	public double zombieCountMultiplier;
	/** BO1 speed roll: each round adds this to a zombie's speed roll, which walks up to zombieWalkThreshold, runs up to zombieRunThreshold, then sprints. */
	public int zombieMoveSpeedPerRound;
	public int zombieMoveSpeedSpread;
	public int zombieWalkThreshold;
	public int zombieRunThreshold;
	/** From this round the last zombie alive sprints; 0 disables. */
	public int lastZombieSprintRound;
	/** Highest real max health a mob gets; health above it is kept by scaling incoming damage (server cap is 2048). */
	public double zombieHealthCap;
	/** Spawn zombies only in zones holding a player and their adjacent zones (needs arena zones). */
	public boolean spawnActiveZonesOnly;
	/** Ground-speed multipliers over a walker (a sprinter at 2.2 stays just under a sprinting player). */
	public double zombieRunnerSpeed;
	public double zombieSprinterSpeed;
	/** Random per-zombie ground-speed spread (fraction either way) on top of the walker/runner/sprinter tier. */
	public double zombieSpeedVariation;
	public int dogRoundFirstMin;
	public int dogRoundFirstMax;
	public double doubleTapDamageMultiplier;
	public double staminUpWalkSpeed;
	public double phdFlopperDamage;
	public double phdFlopperRadius;
	public double quickReviveHealDelayFactor;
	public boolean visualsLowHealthVignette;
	/**
	 * 0b — Per-round zombie count multiplier for rounds >= 5 (count = round * mult * boardMax).
	 */
	public double zombieRoundMultiplier;

	/**
	 * 0c — Per-zombie attack cooldown in ticks. Each zombie may only damage a player
	 * once per this many ticks; hits from different zombies stack (no shared i-frame).
	 */
	public int zombieAttackCooldownTicks;

	/**
	 * 0d — Bonus points added on top of a kill when the killing blow is a headshot.
	 */
	public int headshotKillBonus;
	/**
	 * 0d — Bonus points added on top of a kill when the killing blow is a melee/knife hit.
	 */
	public int meleeKillBonus;

	/**
	 * Tier 1 — Deadshot Daiquiri extra headshot damage multiplier (applied on top of the base
	 * 1.5x headshot bonus when the player has the perk).
	 */
	public double deadshotHeadshotMultiplier;
	/**
	 * Tier 1 — Flat points awarded to every player by the Bonus Points power-up.
	 */
	public int bonusPointsAmount;
	/**
	 * Tier 1 — Maximum throwables (grenades / Monkey Bombs) a player may hold (buy-to-cap).
	 */
	public int maxGrenades;
	/** Lethal grenades every player gets at the start of each round, capped at {@link #maxGrenades} (BO: 2). */
	public int grenadesPerRound;
	/** Headshot damage multiplier for guns without their own {@code headshot_multiplier} in guns.json. */
	public double headshotDamageMultiplier;
	/** Damage (of 20 HP) an explosive deals to its own thrower/shooter at point blank; PhD Flopper negates it. 0 disables. */
	public double explosiveSelfDamage;

	/**
	 * 0e — Speed Cola reload-time multiplier (0.5 = reloads twice as fast).
	 */
	public double speedColaReloadMultiplier;
	/**
	 * 0e — Double Tap fire-delay divisor (1.5 = fires 1.5x as fast).
	 */
	public double doubleTapFireMultiplier;

	/**
	 * 0f — Base knife one-shots zombies through (and including) this round.
	 * Knife flat damage is derived from the zombie health curve at this round.
	 */
	public int knifeOneShotThroughRound;

	/**
	 * 0L — Seconds a zombie may make no progress (move less than the threshold) before it is
	 * teleported to a reachable spawn near a player. Fixes the "stuck on geometry / 1 zombie
	 * left" hang. 0 disables the stuck check.
	 */
	public int zombieStuckSeconds;
	/**
	 * 0L — A zombie that moves less than this many blocks within a check interval is considered
	 * to have made no progress.
	 */
	public double zombieStuckMoveThreshold;
	/** A mob this close to a player is attacking, never stuck. */
	public double zombieStuckTargetRange;

	/**
	 * 0g — Dogs spawned per player on the second and later dog rounds (total = dogsPerPlayer * players).
	 */
	public int dogsPerPlayer;
	/** Dogs spawned per player on the first dog round of a game. */
	public int dogsPerPlayerFirstRound;
	/** Hellhounds alive at once, per player. */
	public int dogMaxAlivePerPlayer;
	/** BO hellhound health for the 1st, 2nd, 3rd... dog round; the last entry repeats. */
	public List<Integer> dogHealthByDogRound;
	/** Hellhounds appear this many blocks (min..max) away from the player they hunt. */
	public double dogSpawnRadiusMin;
	public double dogSpawnRadiusMax;
	/** Ticks between the lightning strike and the hound appearing. */
	public int dogSpawnDelayTicks;
	/** Fire burst when a hound dies: damage to players within the radius. */
	public double dogExplosionDamage;
	public double dogExplosionRadius;
	/** Smoke fog around the players during a dog round. */
	public boolean dogRoundFog;
	/**
	 * 0g — Whether a guaranteed Max Ammo drops when the last dog of a dog round dies.
	 */
	public boolean dogRoundMaxAmmoDrop;

	/**
	 * 0j — Maximum power-ups allowed on the ground at once (oldest removed beyond this).
	 */
	public int maxPowerUpsOnGround;
	/**
	 * 0j — Chance (percent) that a normal zombie kill drops a power-up. BO uses ~2% per kill.
	 */
	public int powerUpDropPercentage;
	/**
	 * 0j — Maximum power-ups that may drop in a single round (BO caps drops per round ~4). 0 disables the cap.
	 */
	public int maxPowerUpsPerRound;
	/**
	 * 0j — Whether re-picking a timed power-up refreshes/extends its active timer.
	 */
	public boolean powerUpRefreshOnPickup;
	/**
	 * 0j — Whether re-Pack-a-Punching an already-packed gun is allowed (refills ammo).
	 */
	public boolean packAPunchRepackEnabled;
	/**
	 * 0j — Point cost to re-Pack-a-Punch a gun for an ammo refill.
	 */
	public int packAPunchRepackCost;
	/** Seconds the Pack-a-Punch machine works before the upgraded gun is ready. */
	public double packAPunchWorkSeconds;
	/** Seconds the upgraded gun waits in the machine for pickup before it is lost. */
	public double packAPunchPickupSeconds;
	public double wunderfizzRollSeconds;
	public double wunderfizzPickupSeconds;
	public int wunderfizzTeddyMinUses;
	public double wunderfizzTeddyChance;
	/** Seconds a player cannot fire while drinking a perk. */
	public double perkDrinkLockSeconds;
	/** Quick Revive price in a solo game. */
	public int soloQuickRevivePrice;
	/** Seconds the mystery box weapon roulette spins. */
	public double boxRollSeconds;
	/** Seconds the rolled weapon can be taken from the box. */
	public double boxPickupSeconds;
	/** Box uses at one location before the teddy bear can appear. */
	public int boxTeddyMinUses;
	/** Box uses at one location after which the teddy bear is guaranteed; 0 = never forced. */
	public int boxTeddyMaxUses;
	/** Mystery box price while Fire Sale is active. */
	public int boxFireSaleCost;
	/** Wall-buy ammo costs wall gun price divided by this; 0 uses the sign's second price. */
	public int wallAmmoPriceDivisor;
	/** Wall-buy ammo price for the Pack-a-Punched version of the wall gun. */
	public int wallAmmoPackAPunchCost;
	/** Ammo crate price for a held gun that is not a wall gun of the arena. */
	public int ammoCrateDefaultCost;
	/** Ammo crate price for a Pack-a-Punched held gun. */
	public int ammoCratePackAPunchCost;

	/**
	 * 0k — Teleporter recharge/cooldown in seconds before it can be used again.
	 */
	public int teleporterCooldownSeconds;
	/**
	 * 0k — Charge-up delay (ticks) between activating a teleporter and the teleport firing.
	 */
	public int teleporterChargeUpTicks;
	/**
	 * 0k — Radius around the teleporter pad in which zombies are killed on activation.
	 */
	public double teleporterPadKillRadius;
	/**
	 * 0k — Whether a teleport plays a sound.
	 */
	public boolean teleporterSound;
	/**
	 * Tier 2 — Seconds of Pack-a-Punch room access granted when a PaP-flagged teleporter is used.
	 */
	public int teleporterPaPAccessSeconds;

	/**
	 * Tier 2 — When a round's zombies are all spawned and exactly one regular zombie remains,
	 * convert it into a slow gas-emitting crawler so players can hold the round (BO behavior).
	 */
	public boolean lastZombieCrawler;
	/** Chance (0-1) that a zombie surviving explosive damage is turned into a crawler. */
	public double crawlerExplosiveChance;
	/**
	 * Tier 2 — Max health (plugin HP scale) a zombie is set to when converted into a crawler.
	 */
	public double crawlerHealth;
	/**
	 * Tier 2 — Radius (blocks) around a crawler within which its gas poisons players.
	 */
	public double crawlerGasRadius;
	/** Chance (0-1) a new crawler is a Nova-style gas crawler; ordinary BO crawlers leave no gas. */
	public double crawlerGasChance;
	/** Ticks between zombie target refreshes (BO re-evaluates about every second). */
	public int zombieRetargetTicks;
	/** Pick the player with the shortest walking route (Paper pathfinder) instead of the straight-line nearest. */
	public boolean zombiePathTargeting;
	/**
	 * Tier 2 — Movement-speed multiplier applied to a crawler (relative to the zombie's normal
	 * speed). 1.0 = full speed, 0.35 = 35% speed. This multiplies the zombie's real base speed
	 * (0.23), so 0.35 -> ~0.08, a clear "slow crawler" feel without the old Slowness potion stack.
	 * Keep below 0.5 or crawlers stop feeling like crawlers.
	 */
	public double crawlerSpeedMultiplier;

	/**
	 * Global default for how often a dog (hellhound) round occurs: a dog round fires every Nth
	 * wave. Used as the fallback when an arena's own {@code dog_round_every_x} setting is missing,
	 * zero or negative, so dog rounds can never be silently disabled by a bad/absent arena value.
	 * 0 disables dog rounds globally.
	 */
	public int dogRoundEveryX;

	/**
	 * Tier 2 — Duration in seconds the Death Machine temporary minigun is held before it
	 * is removed and the player's inventory is restored.
	 */
	public int deathMachineDurationSeconds;
	/**
	 * Tier 2 — Pack-a-Punch first-pack cost while Bonfire Sale is active (also runs a Fire Sale).
	 */
	public int bonfirePaPCost;

	/**
	 * Tier 2 — Vulture Aid: percent chance (0-100) that a zombie kill drops a reward for a
	 * player holding the perk.
	 */
	public int vultureDropChance;
	/**
	 * Tier 2 — Vulture Aid: points awarded when a Vulture Aid drop rolls the points reward.
	 */
	public int vulturePointsDrop;

	/**
	 * Tier 2 — Trap: how long (seconds) a triggered trap stays active killing zombies.
	 */
	public int trapDurationSeconds;
	/**
	 * Tier 2 — Trap: cooldown (seconds) after a trap finishes before it can be re-triggered.
	 */
	public int trapCooldownSeconds;
	/**
	 * Tier 2 — Trap: radius (blocks) around the trap center within which zombies are killed.
	 */
	public double trapKillRadius;
	/**
	 * Points awarded to a player per barrier repair level (global, not per-barrier). Total earned per
	 * barrier is capped at this * 6 (the six repair stages). BO2-style flat repair reward.
	 */
	public int barrierRepairPoints;
	public int barrierRepairRoundCap;

	/**
	 * #70 — Ticks between barrier damage stages when a zombie is breaking a barrier. Lower values
	 * mean zombies break through faster. Defaults to 60 (3 seconds) matching the original hard-coded
	 * period. Scales down by one tick per round above 1 (see {@link #barrierBreakIntervalMin} floor).
	 */
	public int barrierBreakInterval;

	/**
	 * #70 — Hard floor for the round-scaled barrier break interval, so high rounds never break a
	 * barrier faster than this many ticks. Prevents instant barriers on very high rounds.
	 */
	public int barrierBreakIntervalMin;

	/**
	 * #130/#96 — How close (in blocks) a zombie must be to a barrier's blocks to start breaking it.
	 * Replaces the old spawn-point linkage model where only a zombie spawned at a linked point would
	 * break the barrier. Defaults to 3.
	 */
	public double barrierBreakRadius;

	/**
	 * Tier 4 — Quest: points granted to every player in the arena when the easter-egg
	 * quest is completed.
	 */
	public int questCompletionReward;

	/**
	 * Tier 2 — Thundergun: velocity magnitude applied to each zombie blasted away from the
	 * shooter along the look direction (massive knockback wonder-weapon effect).
	 */
	public double thundergunKnockback;
	/**
	 * Tier 2 — Wunderwaffe DG-2: maximum number of additional zombies the initial lightning
	 * hit chains to (each chained zombie is struck and damaged). Caps the chain to avoid loops.
	 */
	public int wunderwaffeChainCount;
	/**
	 * Tier 2 — Wunderwaffe DG-2: radius (blocks) within which the chain searches for the next
	 * nearest un-chained zombie from the most recently struck one.
	 */
	public double wunderwaffeChainRadius;

	/**
	 * Tier 3 — Percentage fee (0-100) taken off a Bank deposit. The remainder is credited to the
	 * player's persistent bank balance. Default 10 (BO2-style banking levy).
	 */
	public int bankDepositFeePercent;

	/**
	 * Tier 3 — Tombstone Soda: when enabled, a player holding the perk has their perks snapshotted
	 * on down and left in a tombstone if they bleed out, reclaimable after respawn. Default true.
	 */
	public boolean tombstoneEnabled;

	/**
	 * Seconds a tombstone stays once its owner is back in play before it fades.
	 */
	public int tombstoneLifetime;

	/**
	 * Tier 3 — solo self-revive: number of times Quick Revive can self-revive a lone player in a
	 * solo (1-player) game (BO1/BO2 solo behavior). Default 3.
	 */
	public int soloQuickReviveUses;
	/**
	 * Tier 3 — solo self-revive: delay in seconds before the automatic self-revive fires after a
	 * lone player goes down. Default 5.
	 */
	public int soloReviveDelaySeconds;

	/**
	 * Tier 3 — Who's Who: duration in seconds of the ghost self-revive window before the lone player
	 * dies for real if they have not reached their body. Default 20.
	 */
	public int whosWhoSeconds;
	/**
	 * Tier 3 — Who's Who: distance (in blocks) within which the ghost must be of their body location to
	 * self-revive. Default 3.0.
	 */
	public double whosWhoReviveRange;

	/**
	 * Tier 4 — Boss-round cadence: a boss round occurs every Nth wave. 0 (default) disables boss
	 * rounds entirely. Boss rounds take precedence over dog rounds on shared waves.
	 */
	public int bossRoundEveryX;
	/**
	 * Tier 4 — Boss max-health multiplier applied on top of the normal zombie health curve for the
	 * round (clamped so a boss is never weaker than a normal zombie). Default 8.0.
	 */
	public double bossHealthMultiplier;
	/**
	 * Tier 4 — Brutus ability: seconds one of a damaged player's perks is temporarily disabled
	 * before it is restored. Default 5.
	 */
	public int brutusDisableSeconds;
	/** Brutus locks perk machines and mystery boxes within this many blocks; players pay to unlock them. */
	public double brutusLockRadius;
	public int brutusUnlockCost;

	/**
	 * Tier 4 — Zombie Shield (buildable): number of zombie hits the equipped shield absorbs
	 * before it breaks and is consumed. Default 5.
	 */
	public int zombieShieldHits;
	/**
	 * Tier 4 — Zombie Shield (buildable): fraction (0.0-1.0) of zombie melee damage blocked while
	 * the shield is held in the off-hand or main hand. 1.0 = fully blocks. Default 1.0.
	 */
	public double zombieShieldDamageReduction;
	/**
	 * Tier 4 — Buildable: point cost charged at a station to dispense one random still-missing
	 * part to the interacting player (this is how players obtain parts). Default 500.
	 */
	public int buildablePartCost;

	/**
	 * Tier 4 — Perma-perks master switch. When false, no progress is tracked and no perma-perk
	 * effect is applied (already-unlocked players simply get nothing). Default true.
	 */
	public boolean permaPerksEnabled;
	/**
	 * Tier 4 — Perma-perks: lifetime revives a player must perform (across all games) before the
	 * permanent "Perma Jugg (lite)" bonus unlocks. Default 10.
	 */
	public int permaJuggReviveThreshold;
	/**
	 * Tier 4 — Perma-perks: duration in seconds of the Regeneration burst granted at game entry to
	 * players who have unlocked Perma Jugg (lite). Default 5.
	 */
	public int permaJuggRegenSeconds;
	/**
	 * Tier 4 — Perma-perks: lifetime revives a player must perform (across all games) before the
	 * permanent "Perma Quick Revive (lite)" bonus unlocks. Default 15.
	 */
	public int permaQuickReviveThreshold;
	/**
	 * Tier 4 — Perma-perks: duration in seconds of the Speed burst granted at game entry to players
	 * who have unlocked Perma Quick Revive (lite). Default 5.
	 */
	public int permaQuickReviveSpeedSeconds;

	// ---- Custom resource pack -------------------------------------------------

	/** Whether COM:Z pushes its custom resource pack to players who join a game. */
	public boolean resourcePackEnabled;
	/** Download URL of the hosted resource-pack .zip. */
	public String resourcePackUrl;
	/** 40-char hex SHA-1 of the .zip (enables client-side caching; empty = always re-download). */
	public String resourcePackSha1;
	/** Whether the pack is forced (client must accept to keep playing). */
	public boolean resourcePackForce;
	/** Prompt shown in the client's accept dialog. */
	public String resourcePackPrompt;
	/** When forced, remove a player from the game if they decline / fail to download the pack. */
	public boolean resourcePackKickOnDecline;

	// ---- Visual feedback layer (BO2 immersion) --------------------------------

	/** Ammo / reload readout in the action bar. */
	public boolean visualsHud;
	/** Round-change ambient cues (stinger + brief darkness). */
	public boolean visualsRoundAmbience;
	/** Zombie-hit feedback particles (blood / headshot sparkle) + floating damage numbers. */
	public boolean visualsHitFeedback;
	/** Boss health bar + stinger on boss rounds. */
	public boolean visualsBossBar;
	public boolean visualsRoundBar;
	/** Vertical locator beam on the Mystery Box. */
	public boolean visualsBoxBeam;
	/** Visual flourish (burst + jingle) when buying / receiving a perk. */
	public boolean visualsPerkFeedback;
	/** Visual flourish (title + sound) when Pack-a-Punching a gun. */
	public boolean visualsPapFeedback;

	// ---- Alternative interaction layer (proximity + key, vs. world signs) ----------------

	/** Show the action-bar prompt + allow F (swap hands) to buy doors / machines when nearby. */
	public boolean proximityBuy;
	/** Keep door [Zombies] price signs visible in the world (classic look). False = bought via F. */
	public boolean useDoorSigns;
	/** Spawn the ephemeral [BarrierRepair] sign on damage (classic look). False = sneak-to-repair. */
	public boolean spawnBarrierRepairSign;

	// ---- Arena environment and zones (arena settings override these defaults) ----

	/** Default arena gravity multiplier; 1.0 = normal, 0.4 = moon. */
	public double environmentGravity;
	/** Default arena jump-height multiplier. */
	public double environmentJumpBoost;
	/** Default for the arena darkness/fog effect (1.19+ clients). */
	public boolean environmentFog;
	/** Seconds of air a player has inside a no-air zone without breathing gear. */
	public int environmentOxygenSeconds;
	/** Damage per second once the air meter is empty. */
	public double environmentSuffocationDamage;
	/** Show a zone's name as a title when a player enters it. */
	public boolean environmentZoneTitles;
	/** Helmet materials that always count as breathing gear. */
	public List<String> breathingGearMaterials;

	/**
	 * WeaponMechanics integration mode. {@code auto} (default) = use WM for mapped guns iff the
	 * plugin is installed + enabled; {@code off} = always native even if WM is present; {@code force}
	 * = behave like auto but warn loudly if WM is absent. Read by {@link com.theprogrammingturkey.comz.integration.WeaponBackends}.
	 */
	public String wmIntegrationMode;

	/**
	 * Main method to assign values to every field.
	 */
	public void setup()
	{
		COMZombies plugin = COMZombies.getPlugin();
		doublePointsTimer = plugin.getConfig().getInt("config.gameSettings.doublePointsTimer");
		instaKillTimer = plugin.getConfig().getInt("config.gameSettings.instaKillTimer");
		fireSaleTimer = plugin.getConfig().getInt("config.gameSettings.fireSaleTimer");
		perkLife = plugin.getConfig().getDouble("config.gameSettings.perkLife", 26.5);
		maxZombies = (int) plugin.getConfig().getDouble("config.gameSettings.maxZombies");
		waveSpawnInterval = plugin.getConfig().getInt("config.gameSettings.waveSpawnInterval");
		pointsOnHit = plugin.getConfig().getInt("config.gameSettings.defaultPointsOnHit", 10);
		pointsOnKill = plugin.getConfig().getInt("config.gameSettings.defaultPointsOnKill", 60);
		maxWave = plugin.getConfig().getInt("config.gameSettings.maxWave");
		reviveTimer = plugin.getConfig().getInt("config.ReviveSettings.ReviveTimer");
		reviveRange = Math.min(plugin.getConfig().getInt("config.ReviveSettings.ReviveRange"), 6);
		meleeRange = (float) plugin.getConfig().getDouble("config.gameSettings.MeleeRange");
		configVersion = plugin.getConfig().getString("vID");
		reloadTime = plugin.getConfig().getInt("config.gameSettings.reloadTime");
		roundSoundVolume = (float) plugin.getConfig().getDouble("config.gameSettings.roundSoundVolume");

		arenaStartTime = plugin.getConfig().getInt("config.gameSettings.arenaStartTime");
		// Perks are HUD potion-effect icons now (not inventory slots); cap at the 12 real perks.
		maxPerks = Math.min(plugin.getConfig().getInt("config.perks.maxPerks", 4), 12);
		KillMoney = plugin.getConfig().getInt("config.Economy.MoneyPerKill");
		//PistolMaterial = plugin.getConfig().getInt("config.Guns.PistolMaterial");
		zombieDamage = plugin.getConfig().getDouble("config.gameSettings.zombieDamage", 8);
		juggernogHealth = plugin.getConfig().getDouble("config.perks.juggernogHealth", 2.4);
		healTime = plugin.getConfig().getDouble("config.gameSettings.healTime", 2.5);
		zombieHitsToDown = plugin.getConfig().getInt("config.gameSettings.zombieHitsToDown", 2);
		juggernogHitsToDown = plugin.getConfig().getInt("config.perks.juggernogHitsToDown", 5);
		respawnKeepsPoints = plugin.getConfig().getBoolean("config.gameSettings.respawnKeepsPoints", true);
		reviveDownedOnRoundEnd = plugin.getConfig().getBoolean("config.ReviveSettings.ReviveOnRoundEnd", false);
		aimWalkSpeedMultiplier = plugin.getConfig().getDouble("config.gameSettings.aimWalkSpeedMultiplier", 0.75);
		waterWalkMultiplier = plugin.getConfig().getDouble("config.gameSettings.waterWalkMultiplier", 0.7);
		waterDeepWalkMultiplier = plugin.getConfig().getDouble("config.gameSettings.waterDeepWalkMultiplier", 0.5);

		// ---- BO2 fidelity (Tier 0) defaults ----
		zombieBoardBase = plugin.getConfig().getInt("config.gameSettings.zombieBoardBase", 24);
		zombieBoardPerPlayer = plugin.getConfig().getInt("config.gameSettings.zombieBoardPerPlayer", 0);
		zombieSpawnDelayMin = plugin.getConfig().getDouble("config.gameSettings.zombieSpawnDelayMin", 0.1);
		zombieCountBase = plugin.getConfig().getInt("config.gameSettings.zombieCountBase", 24);
		zombieCountPerPlayer = plugin.getConfig().getInt("config.gameSettings.zombieCountPerPlayer", 6);
		zombieCountMultiplier = plugin.getConfig().getDouble("config.gameSettings.zombieCountMultiplier", 1.0);
		zombieMoveSpeedPerRound = plugin.getConfig().getInt("config.gameSettings.zombieMoveSpeedPerRound", 8);
		zombieMoveSpeedSpread = plugin.getConfig().getInt("config.gameSettings.zombieMoveSpeedSpread", 35);
		zombieWalkThreshold = plugin.getConfig().getInt("config.gameSettings.zombieWalkThreshold", 35);
		zombieRunThreshold = plugin.getConfig().getInt("config.gameSettings.zombieRunThreshold", 70);
		lastZombieSprintRound = plugin.getConfig().getInt("config.gameSettings.lastZombieSprintRound", 4);
		zombieHealthCap = plugin.getConfig().getDouble("config.gameSettings.zombieHealthCap", 1024);
		spawnActiveZonesOnly = plugin.getConfig().getBoolean("config.gameSettings.spawnActiveZonesOnly", true);
		zombieRunnerSpeed = plugin.getConfig().getDouble("config.gameSettings.zombieRunnerSpeed", 1.6);
		zombieSprinterSpeed = plugin.getConfig().getDouble("config.gameSettings.zombieSprinterSpeed", 2.2);
		zombieSpeedVariation = plugin.getConfig().getDouble("config.gameSettings.zombieSpeedVariation", 0.08);
		dogRoundFirstMin = plugin.getConfig().getInt("config.gameSettings.dogRoundFirstMin", 5);
		dogRoundFirstMax = Math.max(dogRoundFirstMin, plugin.getConfig().getInt("config.gameSettings.dogRoundFirstMax", 8));
		doubleTapDamageMultiplier = plugin.getConfig().getDouble("config.perks.doubleTapDamageMultiplier", 2.0);
		staminUpWalkSpeed = plugin.getConfig().getDouble("config.perks.staminUpWalkSpeed", 0.22);
		phdFlopperDamage = plugin.getConfig().getDouble("config.perks.phdFlopperDamage", 100);
		phdFlopperRadius = plugin.getConfig().getDouble("config.perks.phdFlopperRadius", 5.0);
		quickReviveHealDelayFactor = plugin.getConfig().getDouble("config.gameSettings.quickReviveHealDelayFactor", 0.5);
		visualsLowHealthVignette = plugin.getConfig().getBoolean("config.visuals.lowHealthVignette", true);
		zombieRoundMultiplier = plugin.getConfig().getDouble("config.gameSettings.zombieRoundMultiplier", 0.15);
		zombieAttackCooldownTicks = plugin.getConfig().getInt("config.gameSettings.zombieAttackCooldownTicks", 20);
		headshotKillBonus = plugin.getConfig().getInt("config.gameSettings.headshotKillBonus", 40);
		meleeKillBonus = plugin.getConfig().getInt("config.gameSettings.meleeKillBonus", 70);
		deadshotHeadshotMultiplier = plugin.getConfig().getDouble("config.perks.deadshotHeadshotMultiplier", 2.0);
		bonusPointsAmount = plugin.getConfig().getInt("config.gameSettings.bonusPointsAmount", 100);
		maxGrenades = plugin.getConfig().getInt("config.gameSettings.maxGrenades", 4);
		grenadesPerRound = plugin.getConfig().getInt("config.gameSettings.grenadesPerRound", 2);
		headshotDamageMultiplier = plugin.getConfig().getDouble("config.gameSettings.headshotDamageMultiplier", 1.5);
		explosiveSelfDamage = plugin.getConfig().getDouble("config.gameSettings.explosiveSelfDamage", 8.0);
		speedColaReloadMultiplier = plugin.getConfig().getDouble("config.perks.speedColaReloadMultiplier", 0.5);
		doubleTapFireMultiplier = plugin.getConfig().getDouble("config.perks.doubleTapFireMultiplier", 1.5);
		knifeOneShotThroughRound = plugin.getConfig().getInt("config.gameSettings.knifeOneShotThroughRound", 1);
		zombieStuckSeconds = plugin.getConfig().getInt("config.gameSettings.zombieStuckSeconds", 6);
		zombieStuckMoveThreshold = plugin.getConfig().getDouble("config.gameSettings.zombieStuckMoveThreshold", 1.0);
		zombieStuckTargetRange = plugin.getConfig().getDouble("config.gameSettings.zombieStuckTargetRange", 2.5);
		dogsPerPlayer = plugin.getConfig().getInt("config.gameSettings.dogsPerPlayer", 8);
		dogsPerPlayerFirstRound = plugin.getConfig().getInt("config.gameSettings.dogsPerPlayerFirstRound", 6);
		dogMaxAlivePerPlayer = Math.max(1, plugin.getConfig().getInt("config.gameSettings.dogMaxAlivePerPlayer", 2));
		dogHealthByDogRound = plugin.getConfig().getIntegerList("config.gameSettings.dogHealthByDogRound");
		if(dogHealthByDogRound.isEmpty())
			dogHealthByDogRound = java.util.Arrays.asList(400, 900, 1300, 1600);
		dogSpawnRadiusMin = plugin.getConfig().getDouble("config.gameSettings.dogSpawnRadiusMin", 4.0);
		dogSpawnRadiusMax = Math.max(dogSpawnRadiusMin, plugin.getConfig().getDouble("config.gameSettings.dogSpawnRadiusMax", 8.0));
		dogSpawnDelayTicks = Math.max(0, plugin.getConfig().getInt("config.gameSettings.dogSpawnDelayTicks", 20));
		dogExplosionDamage = plugin.getConfig().getDouble("config.gameSettings.dogExplosionDamage", 2.0);
		dogExplosionRadius = plugin.getConfig().getDouble("config.gameSettings.dogExplosionRadius", 2.5);
		dogRoundFog = plugin.getConfig().getBoolean("config.gameSettings.dogRoundFog", true);
		dogRoundEveryX = plugin.getConfig().getInt("config.gameSettings.dogRoundEveryX", 4);
		dogRoundMaxAmmoDrop = plugin.getConfig().getBoolean("config.gameSettings.dogRoundMaxAmmoDrop", true);
		maxPowerUpsOnGround = plugin.getConfig().getInt("config.gameSettings.maxPowerUpsOnGround", 4);
		powerUpDropPercentage = plugin.getConfig().getInt("config.gameSettings.powerUpDropPercentage", 2);
		maxPowerUpsPerRound = plugin.getConfig().getInt("config.gameSettings.maxPowerUpsPerRound", 4);
		powerUpRefreshOnPickup = plugin.getConfig().getBoolean("config.gameSettings.powerUpRefreshOnPickup", true);
		packAPunchRepackEnabled = plugin.getConfig().getBoolean("config.gameSettings.packAPunchRepackEnabled", true);
		packAPunchRepackCost = plugin.getConfig().getInt("config.gameSettings.packAPunchRepackCost", 2000);
		packAPunchWorkSeconds = plugin.getConfig().getDouble("config.gameSettings.packAPunchWorkSeconds", 3.5);
		packAPunchPickupSeconds = plugin.getConfig().getDouble("config.gameSettings.packAPunchPickupSeconds", 15);
		wunderfizzRollSeconds = plugin.getConfig().getDouble("config.gameSettings.wunderfizzRollSeconds", 4.5);
		wunderfizzPickupSeconds = plugin.getConfig().getDouble("config.gameSettings.wunderfizzPickupSeconds", 10);
		wunderfizzTeddyMinUses = plugin.getConfig().getInt("config.gameSettings.wunderfizzTeddyMinUses", 3);
		wunderfizzTeddyChance = plugin.getConfig().getDouble("config.gameSettings.wunderfizzTeddyChance", 0.2);
		perkDrinkLockSeconds = plugin.getConfig().getDouble("config.perks.drinkLockSeconds", 2);
		soloQuickRevivePrice = plugin.getConfig().getInt("config.perks.soloQuickRevivePrice", 500);
		boxRollSeconds = plugin.getConfig().getDouble("config.gameSettings.boxRollSeconds", 4);
		boxPickupSeconds = plugin.getConfig().getDouble("config.gameSettings.boxPickupSeconds", 12);
		boxTeddyMinUses = plugin.getConfig().getInt("config.gameSettings.boxTeddyMinUses", 3);
		boxTeddyMaxUses = plugin.getConfig().getInt("config.gameSettings.boxTeddyMaxUses", 8);
		boxFireSaleCost = plugin.getConfig().getInt("config.gameSettings.boxFireSaleCost", 10);
		wallAmmoPriceDivisor = plugin.getConfig().getInt("config.gameSettings.wallAmmoPriceDivisor", 2);
		wallAmmoPackAPunchCost = plugin.getConfig().getInt("config.gameSettings.wallAmmoPackAPunchCost", 4500);
		ammoCrateDefaultCost = plugin.getConfig().getInt("config.gameSettings.ammoCrateDefaultCost", 1000);
		ammoCratePackAPunchCost = plugin.getConfig().getInt("config.gameSettings.ammoCratePackAPunchCost", 4500);
		teleporterCooldownSeconds = plugin.getConfig().getInt("config.teleporter.cooldownSeconds", 30);
		teleporterChargeUpTicks = plugin.getConfig().getInt("config.teleporter.chargeUpTicks", 40);
		teleporterPadKillRadius = plugin.getConfig().getDouble("config.teleporter.padKillRadius", 3.0);
		teleporterSound = plugin.getConfig().getBoolean("config.teleporter.sound", true);
		teleporterPaPAccessSeconds = plugin.getConfig().getInt("config.teleporter.paPAccessSeconds", 30);
		lastZombieCrawler = plugin.getConfig().getBoolean("config.gameSettings.lastZombieCrawler", false);
		crawlerExplosiveChance = plugin.getConfig().getDouble("config.gameSettings.crawlerExplosiveChance", 0.35);
		crawlerHealth = plugin.getConfig().getDouble("config.gameSettings.crawlerHealth", 0);
		crawlerGasRadius = plugin.getConfig().getDouble("config.gameSettings.crawlerGasRadius", 3.0);
		crawlerGasChance = plugin.getConfig().getDouble("config.gameSettings.crawlerGasChance", 0.0);
		zombieRetargetTicks = plugin.getConfig().getInt("config.gameSettings.zombieRetargetTicks", 20);
		zombiePathTargeting = plugin.getConfig().getBoolean("config.gameSettings.zombiePathTargeting", true);
		crawlerSpeedMultiplier = plugin.getConfig().getDouble("config.gameSettings.crawlerSpeedMultiplier", 0.35);
		deathMachineDurationSeconds = plugin.getConfig().getInt("config.gameSettings.deathMachineDurationSeconds", 30);
		bonfirePaPCost = plugin.getConfig().getInt("config.gameSettings.bonfirePaPCost", 1000);
		vultureDropChance = plugin.getConfig().getInt("config.gameSettings.vultureDropChance", 10);
		vulturePointsDrop = plugin.getConfig().getInt("config.gameSettings.vulturePointsDrop", 25);
		trapDurationSeconds = plugin.getConfig().getInt("config.trap.durationSeconds", 10);
		trapCooldownSeconds = plugin.getConfig().getInt("config.trap.cooldownSeconds", 60);
		trapKillRadius = plugin.getConfig().getDouble("config.trap.killRadius", 4.0);
		barrierRepairPoints = plugin.getConfig().getInt("config.barrier.repairPointsPerLevel", 10);
		barrierRepairRoundCap = plugin.getConfig().getInt("config.barrier.repairPointsRoundCap", 50);
		// #70 — configurable + round-scaled barrier break speed.
		barrierBreakInterval = plugin.getConfig().getInt("config.barrier.breakInterval", 60);
		barrierBreakIntervalMin = plugin.getConfig().getInt("config.barrier.breakIntervalMin", 20);
		// #130/#96 — proximity radius for zombie-vs-barrier breaking.
		barrierBreakRadius = plugin.getConfig().getDouble("config.barrier.breakRadius", 3.0);
		questCompletionReward = plugin.getConfig().getInt("config.quest.completionReward", 5000);
		thundergunKnockback = plugin.getConfig().getDouble("config.wonderWeapons.thundergunKnockback", 3.0);
		wunderwaffeChainCount = plugin.getConfig().getInt("config.wonderWeapons.wunderwaffeChainCount", 5);
		wunderwaffeChainRadius = plugin.getConfig().getDouble("config.wonderWeapons.wunderwaffeChainRadius", 6.0);
		bankDepositFeePercent = plugin.getConfig().getInt("config.bank.depositFeePercent", 10);
		tombstoneEnabled = plugin.getConfig().getBoolean("config.perks.tombstoneEnabled", true);
		tombstoneLifetime = Math.max(5, plugin.getConfig().getInt("config.perks.tombstoneLifetime", 90));
		soloQuickReviveUses = plugin.getConfig().getInt("config.perks.soloQuickReviveUses", 3);
		soloReviveDelaySeconds = plugin.getConfig().getInt("config.ReviveSettings.SoloReviveDelaySeconds", 10);
		whosWhoSeconds = plugin.getConfig().getInt("config.perks.whosWhoSeconds", 20);
		whosWhoReviveRange = plugin.getConfig().getDouble("config.perks.whosWhoReviveRange", 3.0);
		bossRoundEveryX = plugin.getConfig().getInt("config.gameSettings.bossRoundEveryX", 0);
		bossHealthMultiplier = plugin.getConfig().getDouble("config.gameSettings.bossHealthMultiplier", 8.0);
		brutusDisableSeconds = plugin.getConfig().getInt("config.gameSettings.brutusDisableSeconds", 5);
		brutusLockRadius = plugin.getConfig().getDouble("config.gameSettings.brutusLockRadius", 3.0);
		brutusUnlockCost = plugin.getConfig().getInt("config.gameSettings.brutusUnlockCost", 2000);
		zombieShieldHits = plugin.getConfig().getInt("config.buildable.zombieShieldHits", 5);
		zombieShieldDamageReduction = plugin.getConfig().getDouble("config.buildable.zombieShieldDamageReduction", 1.0);
		buildablePartCost = plugin.getConfig().getInt("config.buildable.partCost", 500);
		permaPerksEnabled = plugin.getConfig().getBoolean("config.permaPerks.enabled", true);
		permaJuggReviveThreshold = plugin.getConfig().getInt("config.permaPerks.permaJuggReviveThreshold", 10);
		permaJuggRegenSeconds = plugin.getConfig().getInt("config.permaPerks.permaJuggRegenSeconds", 5);
		permaQuickReviveThreshold = plugin.getConfig().getInt("config.permaPerks.permaQuickReviveThreshold", 15);
		permaQuickReviveSpeedSeconds = plugin.getConfig().getInt("config.permaPerks.permaQuickReviveSpeedSeconds", 5);

		resourcePackEnabled = plugin.getConfig().getBoolean("config.resourcePack.enabled", false);
		resourcePackUrl = plugin.getConfig().getString("config.resourcePack.url", "");
		resourcePackSha1 = plugin.getConfig().getString("config.resourcePack.sha1", "");
		resourcePackForce = plugin.getConfig().getBoolean("config.resourcePack.force", true);
		resourcePackPrompt = plugin.getConfig().getString("config.resourcePack.prompt", "Install the Call of Minecraft: Zombies pack for custom guns, perks & sounds");
		resourcePackKickOnDecline = plugin.getConfig().getBoolean("config.resourcePack.kickOnDecline", false);

		// ---- Visual feedback layer toggles (all default true) ----
		visualsHud = plugin.getConfig().getBoolean("config.visuals.hud", true);
		visualsRoundAmbience = plugin.getConfig().getBoolean("config.visuals.roundAmbience", true);
		visualsHitFeedback = plugin.getConfig().getBoolean("config.visuals.hitFeedback", true);
		visualsBossBar = plugin.getConfig().getBoolean("config.visuals.bossBar", true);
		visualsRoundBar = plugin.getConfig().getBoolean("config.visuals.roundBar", true);
		visualsBoxBeam = plugin.getConfig().getBoolean("config.visuals.boxBeam", true);
		visualsPerkFeedback = plugin.getConfig().getBoolean("config.visuals.perkFeedback", true);
		visualsPapFeedback = plugin.getConfig().getBoolean("config.visuals.papFeedback", true);

		// ---- Alternative interaction layer (proximity + key, vs. world signs) ----
		proximityBuy = plugin.getConfig().getBoolean("config.features.proximityBuy", true);
		useDoorSigns = plugin.getConfig().getBoolean("config.features.useDoorSigns", false);
		spawnBarrierRepairSign = plugin.getConfig().getBoolean("config.features.spawnBarrierRepairSign", false);

		environmentGravity = plugin.getConfig().getDouble("config.environment.gravity", 1.0);
		environmentJumpBoost = plugin.getConfig().getDouble("config.environment.jumpBoost", 1.0);
		environmentFog = plugin.getConfig().getBoolean("config.environment.fog", false);
		environmentOxygenSeconds = plugin.getConfig().getInt("config.environment.oxygenSeconds", 10);
		environmentSuffocationDamage = plugin.getConfig().getDouble("config.environment.suffocationDamage", 2.0);
		environmentZoneTitles = plugin.getConfig().getBoolean("config.environment.zoneTitles", true);
		breathingGearMaterials = plugin.getConfig().getStringList("config.environment.breathingGearMaterials");

		wmIntegrationMode = plugin.getConfig().getString("config.integration.weaponMechanics", "auto");

		Leaderboard.loadLeaderboard();
		PlayerDataManager.load();
	}
}