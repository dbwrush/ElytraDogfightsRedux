package net.sudologic.elytraDogfightsRedux.game;

import net.sudologic.elytraDogfightsRedux.config.DogfightMap;
import net.sudologic.elytraDogfightsRedux.config.TeamConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.Color;

import java.util.*;

public class Session {
    private final DogfightMap map;
    private final Set<UUID> queuedPlayers;
    private final Set<UUID> activePlayers;
    private final Set<UUID> originalPlayers; // Immutable list of players who started the game
    private final Map<UUID, Integer> playerTeams; // Player UUID -> Team number (0, 1, 2)
    private SessionState state;
    private final int requiredPlayers;
    private BukkitTask countdownTask;
    private int countdownDuration = 10; // Default
    private SessionManager sessionManager;
    private Location globalSpawn;

    public Session(DogfightMap map) {
        this.map = map;
        this.queuedPlayers = new HashSet<>();
        this.activePlayers = new HashSet<>();
        this.originalPlayers = new HashSet<>();
        this.playerTeams = new HashMap<>();
        this.state = SessionState.WAITING;
        this.requiredPlayers = calculateRequiredPlayers(map.getTeamConfig());
    }

    public void setCountdownDuration(int duration) {
        this.countdownDuration = duration;
    }

    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void setGlobalSpawn(Location globalSpawn) {
        this.globalSpawn = globalSpawn;
    }

    private int calculateRequiredPlayers(TeamConfiguration teamConfig) {
        switch (teamConfig) {
            case FREE_FOR_ALL:
            case TWO_TEAMS:
                return 2;
            case THREE_TEAMS:
                return 3;
            default:
                return 2;
        }
    }

    public boolean addPlayer(UUID playerId) {
        if (state == SessionState.ACTIVE) {
            return false; // Cannot join active games
        }
        boolean added = queuedPlayers.add(playerId);

        // Check if we can start countdown
        if (added && state == SessionState.WAITING && queuedPlayers.size() >= requiredPlayers) {
            startCountdown();
        }

        return added;
    }

    public boolean removePlayer(UUID playerId) {
        boolean removed = queuedPlayers.remove(playerId) || activePlayers.remove(playerId);
        playerTeams.remove(playerId);

        // If we're in countdown and drop below required players, cancel countdown
        if (state == SessionState.COUNTDOWN && queuedPlayers.size() < requiredPlayers) {
            cancelCountdown();
        }

        return removed;
    }

    private void startCountdown() {
        if (state != SessionState.WAITING) return;

        state = SessionState.COUNTDOWN;

        // Announce to all non-active players
        announceCountdownStart(countdownDuration);

        // Start countdown task
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                startGame();
            }
        }.runTaskLater(getPlugin(), countdownDuration * 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        state = SessionState.WAITING;
    }

    private void announceCountdownStart(int duration) {
        Component message = Component.text("§6A match on map " + map.getName() + " will start in " + duration + " seconds");

        if (sessionManager != null) {
            sessionManager.announceToNonActivePlayers(message);
        }
    }

    public void startGame() {
        if (state != SessionState.COUNTDOWN) return;

        // Assign teams and teleport players
        assignTeamsAndTeleport();

        // Store original players who made it to the game start (after team assignment)
        originalPlayers.addAll(queuedPlayers);

        // Move remaining players to active
        activePlayers.addAll(queuedPlayers);
        queuedPlayers.clear();

        state = SessionState.ACTIVE;
    }

    private void assignTeamsAndTeleport() {
        List<UUID> playersToAssign = new ArrayList<>(queuedPlayers);
        List<UUID> playersToRemove = new ArrayList<>();

        switch (map.getTeamConfig()) {
            case FREE_FOR_ALL:
                teleportPlayersToSpawn(playersToAssign, 0);
                break;

            case TWO_TEAMS:
                int twoTeamPlayers = (playersToAssign.size() / 2) * 2;
                if (playersToAssign.size() > twoTeamPlayers) {
                    playersToRemove.addAll(playersToAssign.subList(twoTeamPlayers, playersToAssign.size()));
                }

                Collections.shuffle(playersToAssign);
                for (int i = 0; i < twoTeamPlayers; i++) {
                    UUID playerId = playersToAssign.get(i);
                    int team = i % 2;
                    playerTeams.put(playerId, team);
                    teleportPlayerToTeamSpawn(playerId, team);
                }
                break;

            case THREE_TEAMS:
                int threeTeamPlayers = (playersToAssign.size() / 3) * 3;
                if (playersToAssign.size() > threeTeamPlayers) {
                    playersToRemove.addAll(playersToAssign.subList(threeTeamPlayers, playersToAssign.size()));
                }

                Collections.shuffle(playersToAssign);
                for (int i = 0; i < threeTeamPlayers; i++) {
                    UUID playerId = playersToAssign.get(i);
                    int team = i % 3;
                    playerTeams.put(playerId, team);
                    teleportPlayerToTeamSpawn(playerId, team);
                }
                break;
        }

        // Remove excess players from queue
        for (UUID playerId : playersToRemove) {
            queuedPlayers.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(Component.text("§cYou were removed from the queue due to team balance requirements."));
            }
        }
    }

    private void teleportPlayersToSpawn(List<UUID> players, int spawnIndex) {
        for (UUID playerId : players) {
            teleportPlayerToTeamSpawn(playerId, spawnIndex);
        }
    }

    private void teleportPlayerToTeamSpawn(UUID playerId, int teamIndex) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        Location spawnLocation = map.getSpawnPoint(teamIndex);
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
            String teamMessage = getTeamMessage(teamIndex);
            player.sendMessage(Component.text("§a" + teamMessage));
        } else {
            player.sendMessage(Component.text("§cError: Spawn point not configured for this map."));
        }
    }

    private String getTeamMessage(int teamIndex) {
        switch (map.getTeamConfig()) {
            case FREE_FOR_ALL:
                return "Game started! Fight for yourself!";
            case TWO_TEAMS:
            case THREE_TEAMS:
                return "You are on Team " + (teamIndex + 1) + "!";
            default:
                return "Game started!";
        }
    }

    public boolean shouldEnd() {
        if (state != SessionState.ACTIVE) return false;

        switch (map.getTeamConfig()) {
            case FREE_FOR_ALL:
                return activePlayers.size() <= 1;
            case TWO_TEAMS:
            case THREE_TEAMS:
                return getActiveTeamCount() <= 1;
            default:
                return false;
        }
    }

    private int getActiveTeamCount() {
        Set<Integer> activeTeams = new HashSet<>();
        for (UUID playerId : activePlayers) {
            Integer team = playerTeams.get(playerId);
            if (team != null) {
                activeTeams.add(team);
            }
        }
        return activeTeams.size();
    }

    public void endGameWithWinner() {
        if (state != SessionState.ACTIVE) return;

        List<UUID> winners = getWinners();
        String winnerMessage = getWinnerMessage(winners);

        teleportPlayersToGlobalSpawn();
        announceWinnerToOriginalPlayers(winnerMessage, winners);
        endGame();
    }

    private List<UUID> getWinners() {
        List<UUID> winners = new ArrayList<>();

        switch (map.getTeamConfig()) {
            case FREE_FOR_ALL:
                winners.addAll(activePlayers);
                break;
            case TWO_TEAMS:
            case THREE_TEAMS:
                if (!activePlayers.isEmpty()) {
                    Integer winningTeam = playerTeams.get(activePlayers.iterator().next());
                    if (winningTeam != null) {
                        for (UUID playerId : activePlayers) {
                            if (winningTeam.equals(playerTeams.get(playerId))) {
                                winners.add(playerId);
                            }
                        }
                    }
                }
                break;
        }

        return winners;
    }

    private String getWinnerMessage(List<UUID> winners) {
        if (winners.isEmpty()) {
            return "§7The match ended with no winners.";
        }

        switch (map.getTeamConfig()) {
            case FREE_FOR_ALL:
                if (winners.size() == 1) {
                    Player winner = Bukkit.getPlayer(winners.get(0));
                    return "§6" + (winner != null ? winner.getName() : "Unknown") + " won the match!";
                } else {
                    return "§6The match ended in a draw!";
                }
            case TWO_TEAMS:
            case THREE_TEAMS:
                if (!winners.isEmpty()) {
                    Integer winningTeam = playerTeams.get(winners.get(0));
                    if (winningTeam != null) {
                        return "§6Team " + (winningTeam + 1) + " won the match!";
                    }
                }
                break;
        }

        return "§7The match has ended.";
    }

    private void teleportPlayersToGlobalSpawn() {
        if (globalSpawn != null) {
            for (UUID playerId : activePlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.teleport(globalSpawn);
                }
            }
        }
    }

    private void announceWinnerToOriginalPlayers(String message, List<UUID> winners) {
        Component winnerComponent = Component.text(message);

        for (UUID playerId : originalPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(winnerComponent);

                if (winners.contains(playerId) && !isPlayerInOtherActiveSession(playerId)) {
                    spawnFirework(player);
                }
            }
        }
    }

    private boolean isPlayerInOtherActiveSession(UUID playerId) {
        if (sessionManager == null) return false;

        Session currentSession = sessionManager.getPlayerSession(playerId);
        return currentSession != null && currentSession != this && currentSession.getState() == SessionState.ACTIVE;
    }

    private void spawnFirework(Player player) {
        Bukkit.getScheduler().runTask(getPlugin(), () -> {
            Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();

            FireworkEffect effect = FireworkEffect.builder()
                .withColor(Color.WHITE, Color.YELLOW)
                .with(FireworkEffect.Type.STAR)
                .withTrail()
                .withFlicker()
                .build();

            meta.addEffect(effect);
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        });
    }

    private org.bukkit.plugin.Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin("ElytraDogfightsRedux");
    }

    public void endGame() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        activePlayers.clear();
        queuedPlayers.clear();
        originalPlayers.clear();
        playerTeams.clear();
        state = SessionState.WAITING;
    }

    // Getters
    public boolean isPlayerQueued(UUID playerId) { return queuedPlayers.contains(playerId); }
    public boolean isPlayerActive(UUID playerId) { return activePlayers.contains(playerId); }
    public boolean isPlayerInSession(UUID playerId) { return isPlayerQueued(playerId) || isPlayerActive(playerId); }
    public boolean isInUse() { return state == SessionState.ACTIVE; }
    public DogfightMap getMap() { return map; }
    public Set<UUID> getQueuedPlayers() { return new HashSet<>(queuedPlayers); }
    public Set<UUID> getActivePlayers() { return new HashSet<>(activePlayers); }
    public Set<UUID> getOriginalPlayers() { return new HashSet<>(originalPlayers); }
    public SessionState getState() { return state; }
    public int getRequiredPlayers() { return requiredPlayers; }
    public int getQueuedPlayerCount() { return queuedPlayers.size(); }
    public int getActivePlayerCount() { return activePlayers.size(); }
    public Map<UUID, Integer> getPlayerTeams() { return new HashMap<>(playerTeams); }
    public Integer getPlayerTeam(UUID playerId) { return playerTeams.get(playerId); }
}
