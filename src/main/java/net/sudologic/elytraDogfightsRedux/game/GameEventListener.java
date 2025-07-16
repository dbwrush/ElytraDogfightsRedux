package net.sudologic.elytraDogfightsRedux.game;

import net.sudologic.elytraDogfightsRedux.ElytraDogfightsRedux;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import net.kyori.adventure.text.Component;

public class GameEventListener implements Listener {
    private final ElytraDogfightsRedux plugin;

    public GameEventListener(ElytraDogfightsRedux plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        handlePlayerDeath(player);
    }

    private void handlePlayerDeath(Player player) {
        var sessionManager = plugin.getSessionManager();
        var session = sessionManager.getPlayerSession(player.getUniqueId());

        if (session == null || session.getState() != SessionState.ACTIVE) {
            return; // Player not in active session
        }

        // Remove player from session
        session.removePlayer(player.getUniqueId());

        // Teleport player to global spawn
        Location globalSpawn = plugin.getConfigManager().getGlobalSpawn();
        if (globalSpawn != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(globalSpawn);
                player.sendMessage(Component.text("§cYou have been eliminated and teleported to spawn."));
            });
        }

        // Check if session should end
        if (session.shouldEnd()) {
            session.endGameWithWinner();
        }
    }
}
