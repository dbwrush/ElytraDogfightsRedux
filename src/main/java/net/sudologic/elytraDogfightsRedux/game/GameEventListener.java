package net.sudologic.elytraDogfightsRedux.game;

import net.sudologic.elytraDogfightsRedux.ElytraDogfightsRedux;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class GameEventListener implements Listener {
    private final ElytraDogfightsRedux plugin;

    public GameEventListener(ElytraDogfightsRedux plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Update scoreboard for the joining player
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getScoreboardManager().updatePlayerScoreboard(player);
            // Update all other players' scoreboards to reflect new online count
            plugin.getScoreboardManager().updateAllScoreboards();
        }, 1L); // Small delay to ensure player is fully loaded
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove player from any sessions they might be in
        var sessionManager = plugin.getSessionManager();
        var session = sessionManager.getPlayerSession(player.getUniqueId());
        if (session != null) {
            session.removePlayer(player.getUniqueId());

            // Check if session should end
            if (session.shouldEnd()) {
                session.endGameWithWinner();
            }
        }

        // Update all remaining players' scoreboards to reflect new online count
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getScoreboardManager().updateAllScoreboards();
        }, 1L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        handlePlayerDeath(player, event);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Check if both entities are players
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = null;

        // Check if damage is from another player directly or via projectile
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null) return;

        // Check if both players are in the same session
        var sessionManager = plugin.getSessionManager();
        var session = sessionManager.getPlayerSession(victim.getUniqueId());

        if (session == null || session.getState() != SessionState.ACTIVE) return;
        if (!session.isPlayerActive(attacker.getUniqueId())) return;

        // Check if they're on the same team (friendly fire prevention)
        Integer victimTeam = session.getPlayerTeam(victim.getUniqueId());
        Integer attackerTeam = session.getPlayerTeam(attacker.getUniqueId());

        if (victimTeam != null && attackerTeam != null && victimTeam.equals(attackerTeam)) {
            // Cancel friendly fire
            event.setCancelled(true);
            attacker.sendMessage(Component.text("§cYou cannot damage teammates!"));
        }
    }

    private void handlePlayerDeath(Player player, PlayerDeathEvent event) {
        var sessionManager = plugin.getSessionManager();
        var session = sessionManager.getPlayerSession(player.getUniqueId());

        if (session == null || session.getState() != SessionState.ACTIVE) {
            return; // Player not in active session
        }

        // Cancel the death event to prevent respawn screen
        event.setCancelled(true);

        // Remove player from session
        session.removePlayer(player.getUniqueId());

        // Teleport player to global spawn and reset them
        Location globalSpawn = plugin.getConfigManager().getGlobalSpawn();
        if (globalSpawn != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(globalSpawn);
                session.clearPlayerInventoryAndReset(player);
                player.sendMessage(Component.text("§cYou have been eliminated and teleported to spawn."));

                // Update scoreboards for all players in the session
                updateSessionScoreboards(session);

                // Update the eliminated player's scoreboard
                plugin.getScoreboardManager().updatePlayerScoreboard(player);
            });
        }

        // Check if session should end
        if (session.shouldEnd()) {
            session.endGameWithWinner();
        }
    }

    private void updateSessionScoreboards(Session session) {
        // Update scoreboards for all players in the session
        for (UUID playerId : session.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.getScoreboardManager().updatePlayerScoreboard(player);
            }
        }
    }
}
