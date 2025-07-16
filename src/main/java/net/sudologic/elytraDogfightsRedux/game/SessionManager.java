package net.sudologic.elytraDogfightsRedux.game;

import net.sudologic.elytraDogfightsRedux.config.DogfightMap;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import net.kyori.adventure.text.Component;

import java.util.*;

public class SessionManager {
    private final Map<String, Session> sessions;
    private int countdownDuration = 10; // Default, will be updated by ConfigManager
    private Location globalSpawn;

    public SessionManager() {
        this.sessions = new HashMap<>();
    }

    public void setCountdownDuration(int duration) {
        this.countdownDuration = duration;
    }

    public void setGlobalSpawn(Location globalSpawn) {
        this.globalSpawn = globalSpawn;
    }

    public Location getGlobalSpawn() {
        return globalSpawn;
    }

    public void createSession(DogfightMap map) {
        Session session = new Session(map);
        session.setCountdownDuration(countdownDuration);
        session.setSessionManager(this);
        session.setGlobalSpawn(globalSpawn);
        sessions.put(map.getName(), session);
    }

    public void removeSession(String mapName) {
        sessions.remove(mapName);
    }

    public boolean queuePlayer(Player player, String mapName) {
        return queuePlayer(player.getUniqueId(), mapName);
    }

    public boolean queuePlayer(UUID playerId, String mapName) {
        Session targetSession = sessions.get(mapName);
        if (targetSession == null) {
            return false; // Map/session doesn't exist
        }

        // Check if the map is already in active use
        if (targetSession.isInUse()) {
            return false; // Map is in use
        }

        // Check if player is already in an active game
        if (isPlayerInActiveGame(playerId)) {
            return false; // Player is in an active game
        }

        // Remove player from any other session they might be queued for
        removePlayerFromAllSessions(playerId);

        // Add player to the target session
        return targetSession.addPlayer(playerId);
    }

    public void removePlayerFromAllSessions(UUID playerId) {
        for (Session session : sessions.values()) {
            session.removePlayer(playerId);
        }
    }

    public Session getPlayerSession(UUID playerId) {
        for (Session session : sessions.values()) {
            if (session.isPlayerInSession(playerId)) {
                return session;
            }
        }
        return null;
    }

    public boolean isPlayerInActiveGame(UUID playerId) {
        Session session = getPlayerSession(playerId);
        return session != null && session.getState() == SessionState.ACTIVE && session.isPlayerActive(playerId);
    }

    public boolean isPlayerQueued(UUID playerId) {
        Session session = getPlayerSession(playerId);
        return session != null && session.isPlayerQueued(playerId);
    }

    public Session getSession(String mapName) {
        return sessions.get(mapName);
    }

    public boolean isMapInUse(String mapName) {
        Session session = sessions.get(mapName);
        return session != null && session.isInUse();
    }

    public Collection<Session> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }

    public void announceToNonActivePlayers(Component message) {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!isPlayerInActiveGame(player.getUniqueId())) {
                player.sendMessage(message);
            }
        }
    }
}
