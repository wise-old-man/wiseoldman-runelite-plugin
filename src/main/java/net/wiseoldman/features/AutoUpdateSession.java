package net.wiseoldman.features;

import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.wiseoldman.util.LocalPlayer;
import net.wiseoldman.web.WomClient;

import javax.inject.Inject;
import java.util.Date;
import java.util.HashMap;

public class AutoUpdateSession {
    @Inject()
    public Client client;

    @Inject()
    public LocalPlayer localPlayer;

    @Inject()
    public WomClient womClient;

    /**
     * The number of hours which a session lasts for.
     */
    private static final int HOURS_BETWEEN_SESSION = 6;

    /**
     * A record of players that have been updated for the current session.
     * Caters for multiple accounts being signed in to during the same session.
     */
    private final HashMap<String, Date> playersTracked = new HashMap<>();

    @Subscribe
    public void onGameTick(GameTick event) {
        if (! hasPlayerBeenTrackedCurrentSession()) {
            return;
        }

        trackLocalPlayer();
    }

    /**
     * Track the local player.
     */
    private void trackLocalPlayer() {
        womClient.updatePlayer(localPlayer.getName(), localPlayer.getHash());

        playersTracked.put(localPlayer.getName(), new Date());
    }


    /**
     * Determine if the player has been tracked as part of the current session.
     */
    private boolean hasPlayerBeenTrackedCurrentSession() {
        if (! hasPlayerBeenTracked()) {
            return false;
        }

        return getMillisecondsSinceLastSubmission() > getSessionAsMilliseconds();
    }

    /**
     * Determine if the player has already been tracked as a part of this session.
     */
    private boolean hasPlayerBeenTracked() {
        return playersTracked.containsKey(localPlayer.getName());
    }

    /**
     * Get the number of milliseconds since the session was last submitted.
     */
    private long getMillisecondsSinceLastSubmission() {
        if (! hasPlayerBeenTracked()) {
            return 0;
        }

        long currentTimestampMilliseconds = System.currentTimeMillis();

        return currentTimestampMilliseconds - playersTracked.get(localPlayer.getName()).getTime();
    }

    /**
     * Get the session hours as millisecond representation.
     */
    private long getSessionAsMilliseconds() {
        return HOURS_BETWEEN_SESSION * 60 * 60 * 1000;
    }
}
