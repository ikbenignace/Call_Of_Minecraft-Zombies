package com.theprogrammingturkey.comz.listeners.customEvents;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Transport;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import javax.annotation.Nonnull;

/**
 * Fired once per rider when a transport ride completes (the rider reached the far end). Rides cut short by
 * going down, leaving or the game ending do not fire it.
 */
public class PlayerTransportRideEvent extends Event
{
	private static final HandlerList handlers = new HandlerList();
	private final Game game;
	private final Player player;
	private final Transport transport;
	private final boolean towardsEnd;

	public PlayerTransportRideEvent(Game game, Player player, Transport transport, boolean towardsEnd)
	{
		this.game = game;
		this.player = player;
		this.transport = transport;
		this.towardsEnd = towardsEnd;
	}

	public Game getGame()
	{
		return game;
	}

	public Player getPlayer()
	{
		return player;
	}

	public Transport getTransport()
	{
		return transport;
	}

	public String getTransportId()
	{
		return transport.getId();
	}

	/** True when the ride went from the first waypoint to the last, false for a return trip. */
	public boolean isTowardsEnd()
	{
		return towardsEnd;
	}

	@Nonnull
	public HandlerList getHandlers()
	{
		return handlers;
	}

	public static HandlerList getHandlerList()
	{
		return handlers;
	}
}
