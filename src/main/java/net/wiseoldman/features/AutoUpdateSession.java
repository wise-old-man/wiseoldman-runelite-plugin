package net.wiseoldman.features;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.wiseoldman.WomUtilsPlugin;
import net.wiseoldman.web.WomClient;

import javax.inject.Inject;
import java.util.Date;
import java.util.HashMap;

@Slf4j
public class AutoUpdateSession
{
	@Inject
	public Client client;

	@Inject
	public WomUtilsPlugin womUtilsPlugin;

	@Inject
	public WomClient womClient;

	@Inject
	private ClientThread clientThread;

	/**
	 * The number of hours which a session lasts for.
	 */
	private static final int HOURS_BETWEEN_SESSION = 24;

	/**
	 * A record of players that have been updated for the current session.
	 * Key: Player name
	 * Value: Date of when the player should next be updated.
	 */
	private final HashMap<String, Date> playersTracked = new HashMap<>();

	/**
	 * Whether the plugin is currently performing an update.
	 */
	private boolean isPerformingUpdate = false;


	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!shouldTrack())
		{
			return;
		}

		clientThread.invoke(this::trackPlayer);
	}

	/**
	 * Determine if the player should be tracked.
	 */
	private boolean shouldTrack()
	{
		return client.getGameState() == GameState.LOGGED_IN
			&& womUtilsPlugin.playerName != null
			&& !isPerformingUpdate
			&& !hasPlayerBeenTrackedCurrentSession();
	}

	/**
	 * Track the local player.
	 */
	private synchronized void trackPlayer()
	{
		isPerformingUpdate = true;

		playersTracked.put(womUtilsPlugin.playerName, getSessionExpiryTime());

		womClient.updatePlayer(womUtilsPlugin.playerName, womUtilsPlugin.accountHash);

		isPerformingUpdate = false;
	}


	/**
	 * Determine if the player has been tracked as part of the current session.
	 */
	private boolean hasPlayerBeenTrackedCurrentSession()
	{
		if (!hasPlayerBeenTracked())
		{
			return false;
		}

		return !hasSessionExpired();
	}

	/**
	 * Determine if the player has already been tracked as a part of this session.
	 */
	private boolean hasPlayerBeenTracked()
	{
		return playersTracked.containsKey(womUtilsPlugin.playerName);
	}

	/**
	 * Get the time that the users current session expires.
	 */
	private Date getSessionExpiryTime()
	{
		return new Date(System.currentTimeMillis() + getSessionAsMilliseconds());
	}

	/**
	 * Get the number of milliseconds since the session was last submitted.
	 */
	private boolean hasSessionExpired()
	{
		if (!hasPlayerBeenTracked())
		{
			return true;
		}

		return playersTracked.get(womUtilsPlugin.playerName).before(new Date());
	}

	/**
	 * Get the session hours as millisecond representation.
	 */
	private long getSessionAsMilliseconds()
	{
		return HOURS_BETWEEN_SESSION * 60 * 60 * 1000;
	}
}
