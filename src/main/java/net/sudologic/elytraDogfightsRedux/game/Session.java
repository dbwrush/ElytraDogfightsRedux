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
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.GameMode;

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

        // Update scoreboards for all players in the session
        if (added) {
            updateSessionScoreboards();
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

        // Update scoreboards for all players in the session
        updateSessionScoreboards();

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

        // Update scoreboards for all players in the session
        updateSessionScoreboards();
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

        // Update scoreboards for all players in the session
        updateSessionScoreboards();
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

            // Setup player inventory and equipment
            setupPlayerInventory(player, teamIndex);

            String teamMessage = getTeamMessage(teamIndex);
            player.sendMessage(Component.text("§a" + teamMessage));
        } else {
            player.sendMessage(Component.text("§cError: Spawn point not configured for this map."));
        }
    }

    private void setupPlayerInventory(Player player, int teamIndex) {
        // Clear inventory and set game mode
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);

        // Set health and food levels
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        // Stop gliding/swimming
        player.setGliding(false);
        player.setSwimming(false);

        // Get team color for armor dyeing
        Color teamColor = getTeamColor(teamIndex);

        // Create leather armor with team color
        ItemStack helmet = createLeatherArmor(Material.LEATHER_HELMET, teamColor);
        ItemStack boots = createLeatherArmor(Material.LEATHER_BOOTS, teamColor);
        ItemStack leggings = createLeatherArmor(Material.LEATHER_LEGGINGS, teamColor);

        // Create elytra
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta elytraMeta = elytra.getItemMeta();
        elytraMeta.setUnbreakable(true);
        elytra.setItemMeta(elytraMeta);

        // Create bow with infinity enchantment
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.addEnchant(Enchantment.INFINITY, 1, true);
        bowMeta.setUnbreakable(true);
        bow.setItemMeta(bowMeta);

        // Create single arrow
        ItemStack arrow = new ItemStack(Material.ARROW, 1);

        // Create wooden sword with knockback
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.addEnchant(Enchantment.KNOCKBACK, 1, true);
        swordMeta.setUnbreakable(true);
        sword.setItemMeta(swordMeta);

        // Equip armor
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(elytra);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        // Add items to inventory
        player.getInventory().addItem(bow);
        player.getInventory().addItem(sword);
        player.getInventory().addItem(arrow);

        // Update inventory
        player.updateInventory();
    }

    private ItemStack createLeatherArmor(Material material, Color color) {
        ItemStack armor = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
        meta.setColor(color);
        meta.setUnbreakable(true);
        armor.setItemMeta(meta);
        return armor;
    }

    private Color getTeamColor(int teamIndex) {
        // Return white for free-for-all
        if (map.getTeamConfig() == TeamConfiguration.FREE_FOR_ALL) {
            return Color.WHITE;
        }

        // Assign random colors for teams
        Color[] teamColors = getRandomTeamColors();
        return teamColors[teamIndex % teamColors.length];
    }

    private Color[] getRandomTeamColors() {
        List<Color> availableColors = Arrays.asList(
            Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
            Color.PURPLE, Color.ORANGE, Color.LIME, Color.AQUA,
            Color.FUCHSIA, Color.NAVY, Color.MAROON, Color.TEAL
        );

        Collections.shuffle(availableColors);

        int teamsNeeded = map.getTeamConfig() == TeamConfiguration.THREE_TEAMS ? 3 : 2;
        Color[] teamColors = new Color[teamsNeeded];

        for (int i = 0; i < teamsNeeded; i++) {
            teamColors[i] = availableColors.get(i);
        }

        return teamColors;
    }

    public void clearPlayerInventoryAndReset(Player player) {
        // Clear inventory
        player.getInventory().clear();

        // Reset player state
        player.setGliding(false);
        player.setSwimming(false);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        // Update inventory
        player.updateInventory();
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
                    clearPlayerInventoryAndReset(player);
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

        // Update scoreboards for all original players
        updateSessionScoreboards();
    }

    private void updateSessionScoreboards() {
        org.bukkit.plugin.Plugin plugin = getPlugin();
        if (plugin instanceof net.sudologic.elytraDogfightsRedux.ElytraDogfightsRedux) {
            net.sudologic.elytraDogfightsRedux.ElytraDogfightsRedux dogfightsPlugin =
                (net.sudologic.elytraDogfightsRedux.ElytraDogfightsRedux) plugin;

            // Update scoreboards for all players in the session
            for (UUID playerId : queuedPlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    dogfightsPlugin.getScoreboardManager().updatePlayerScoreboard(player);
                }
            }

            for (UUID playerId : activePlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    dogfightsPlugin.getScoreboardManager().updatePlayerScoreboard(player);
                }
            }
        }
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
