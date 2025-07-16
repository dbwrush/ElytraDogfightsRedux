package net.sudologic.elytraDogfightsRedux.game;

import net.sudologic.elytraDogfightsRedux.ElytraDogfightsRedux;
import net.sudologic.elytraDogfightsRedux.config.ConfigManager;
import net.sudologic.elytraDogfightsRedux.config.TeamConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.UUID;

public class CustomScoreboard {
    private final ElytraDogfightsRedux plugin;
    private final ConfigManager configManager;
    private final SessionManager sessionManager;

    public CustomScoreboard(ElytraDogfightsRedux plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.sessionManager = plugin.getSessionManager();
    }

    public void setServerName(String serverName) {
        configManager.setServerName(serverName);
    }

    public String getServerName() {
        return configManager.getServerName();
    }

    public void updatePlayerScoreboard(Player player) {
        UUID playerId = player.getUniqueId();
        Session session = sessionManager.getPlayerSession(playerId);

        Scoreboard scoreboard = createScoreboard(player, session);
        player.setScoreboard(scoreboard);
    }

    private Scoreboard createScoreboard(Player player, Session session) {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = scoreboardManager.getNewScoreboard();

        Objective objective = scoreboard.registerNewObjective("dogfights", "dummy",
            ChatColor.GOLD + "" + ChatColor.BOLD + configManager.getServerName());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        if (session == null) {
            // Player is not in any session - show lobby scoreboard
            setupLobbyScoreboard(objective, player);
        } else {
            SessionState state = session.getState();
            switch (state) {
                case WAITING:
                case COUNTDOWN:
                    // Player is queued for a match
                    setupQueueScoreboard(objective, player, session);
                    break;
                case ACTIVE:
                    // Player is in an active match
                    setupActiveScoreboard(objective, player, session);
                    break;
            }
        }

        return scoreboard;
    }

    private void setupLobbyScoreboard(Objective objective, Player player) {
        int line = 15;

        // Empty line
        objective.getScore(ChatColor.RESET + "").setScore(line--);

        // Player name
        objective.getScore(ChatColor.YELLOW + "Player: " + ChatColor.WHITE + player.getName()).setScore(line--);

        // Empty line
        objective.getScore(ChatColor.RESET + " ").setScore(line--);

        // Map count
        int mapCount = configManager.getMaps().size();
        objective.getScore(ChatColor.GREEN + "Maps: " + ChatColor.WHITE + mapCount).setScore(line--);

        // Online players
        int onlineCount = Bukkit.getOnlinePlayers().size();
        objective.getScore(ChatColor.BLUE + "Online: " + ChatColor.WHITE + onlineCount).setScore(line--);

        // Empty line
        objective.getScore(ChatColor.RESET + "  ").setScore(line--);

        // Active sessions count
        int activeSessions = (int) sessionManager.getAllSessions().stream()
            .filter(session -> session.getState() == SessionState.ACTIVE)
            .count();
        objective.getScore(ChatColor.AQUA + "Active Games: " + ChatColor.WHITE + activeSessions).setScore(line--);

        // Empty line
        objective.getScore(ChatColor.RESET + "   ").setScore(line--);

        // Instructions
        objective.getScore(ChatColor.GRAY + "Use /elytradogfights").setScore(line--);
        objective.getScore(ChatColor.GRAY + "play <map> to join").setScore(line--);
    }

    private void setupQueueScoreboard(Objective objective, Player player, Session session) {
        int line = 15;

        // Empty line
        objective.getScore(ChatColor.RESET + "").setScore(line--);

        // Player name
        objective.getScore(ChatColor.YELLOW + "Player: " + ChatColor.WHITE + player.getName()).setScore(line--);

        // Empty line
        objective.getScore(ChatColor.RESET + " ").setScore(line--);

        // Map name
        objective.getScore(ChatColor.GREEN + "Map: " + ChatColor.WHITE + session.getMap().getName()).setScore(line--);

        // Team configuration
        String teamConfig = getTeamConfigDisplay(session.getMap().getTeamConfig());
        objective.getScore(ChatColor.BLUE + "Mode: " + ChatColor.WHITE + teamConfig).setScore(line--);

        // Empty line
        objective.getScore(ChatColor.RESET + "  ").setScore(line--);

        // Queue status
        int queuedCount = session.getQueuedPlayerCount();
        int requiredCount = session.getRequiredPlayers();

        if (session.getState() == SessionState.WAITING) {
            objective.getScore(ChatColor.YELLOW + "Waiting...").setScore(line--);
            objective.getScore(ChatColor.WHITE + "Players: " + queuedCount + "/" + requiredCount).setScore(line--);
        } else if (session.getState() == SessionState.COUNTDOWN) {
            objective.getScore(ChatColor.GOLD + "Starting Soon!").setScore(line--);
            objective.getScore(ChatColor.WHITE + "Players: " + queuedCount).setScore(line--);
        }

        // Empty line
        objective.getScore(ChatColor.RESET + "   ").setScore(line--);

        // Instructions
        objective.getScore(ChatColor.GRAY + "Get ready to fight!").setScore(line--);
    }

    private void setupActiveScoreboard(Objective objective, Player player, Session session) {
        int line = 15;

        // Empty line
        objective.getScore(ChatColor.RESET + "").setScore(line--);

        // Player name
        objective.getScore(ChatColor.YELLOW + "Player: " + ChatColor.WHITE + player.getName()).setScore(line--);

        // Empty line
        objective.getScore(ChatColor.RESET + " ").setScore(line--);

        // Map name
        objective.getScore(ChatColor.GREEN + "Map: " + ChatColor.WHITE + session.getMap().getName()).setScore(line--);

        // Team configuration
        String teamConfig = getTeamConfigDisplay(session.getMap().getTeamConfig());
        objective.getScore(ChatColor.BLUE + "Mode: " + ChatColor.WHITE + teamConfig).setScore(line--);

        // Empty line
        objective.getScore(ChatColor.RESET + "  ").setScore(line--);

        // Team assignment
        Integer playerTeam = session.getPlayerTeam(player.getUniqueId());
        if (playerTeam != null) {
            if (session.getMap().getTeamConfig() == TeamConfiguration.FREE_FOR_ALL) {
                objective.getScore(ChatColor.WHITE + "Free For All").setScore(line--);
            } else {
                objective.getScore(ChatColor.AQUA + "Team: " + ChatColor.WHITE + (playerTeam + 1)).setScore(line--);
            }
        }

        // Active players count
        int activePlayers = session.getActivePlayerCount();
        objective.getScore(ChatColor.RED + "Alive: " + ChatColor.WHITE + activePlayers).setScore(line--);

        // Empty line
        objective.getScore(ChatColor.RESET + "   ").setScore(line--);

        // Game status
        objective.getScore(ChatColor.GOLD + "" + ChatColor.BOLD + "FIGHT!").setScore(line--);
    }

    private String getTeamConfigDisplay(TeamConfiguration config) {
        switch (config) {
            case FREE_FOR_ALL:
                return "Free For All";
            case TWO_TEAMS:
                return "2 Teams";
            case THREE_TEAMS:
                return "3 Teams";
            default:
                return "Unknown";
        }
    }

    public void updateAllScoreboards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerScoreboard(player);
        }
    }

    public void removePlayerScoreboard(Player player) {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        player.setScoreboard(scoreboardManager.getMainScoreboard());
    }
}
