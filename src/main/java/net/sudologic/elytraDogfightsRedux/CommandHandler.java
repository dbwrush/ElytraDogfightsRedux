package net.sudologic.elytraDogfightsRedux;

import net.kyori.adventure.text.Component;
import net.sudologic.elytraDogfightsRedux.config.ConfigManager;
import net.sudologic.elytraDogfightsRedux.config.TeamConfiguration;
import net.sudologic.elytraDogfightsRedux.game.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final ElytraDogfightsRedux plugin;
    private final ConfigManager configManager;
    private final SessionManager sessionManager;

    public CommandHandler(ElytraDogfightsRedux plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.sessionManager = plugin.getSessionManager();
    }

    public void registerCommands() {
        var command = plugin.getCommand("elytradogfights");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("§eUsage: /elytradogfights <map|play> ..."));
            return true;
        }

        if (args[0].equalsIgnoreCase("map")) {
            return handleMapCommand(sender, args);
        } else if (args[0].equalsIgnoreCase("play")) {
            return handlePlayCommand(sender, args);
        }

        sender.sendMessage(Component.text("§cUnknown subcommand. Use /elytradogfights map ... or /elytradogfights play ..."));
        return true;
    }

    private boolean handleMapCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("§eUsage: /elytradogfights map <add|remove|edit> ..."));
            return true;
        }

        String subCommand = args[1].toLowerCase();
        switch (subCommand) {
            case "add":
                return handleAdd(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "edit":
                return handleEdit(sender, args);
            default:
                sender.sendMessage(Component.text("§cUnknown map subcommand. Use add, remove, or edit."));
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("§cUsage: /elytradogfights map add <name>"));
            return true;
        }

        String name = args[2];
        if (configManager.getMap(name) != null) {
            sender.sendMessage(Component.text("§cA map with that name already exists."));
            return true;
        }

        configManager.addMap(new net.sudologic.elytraDogfightsRedux.config.DogfightMap(name, null, null, TeamConfiguration.FREE_FOR_ALL));
        sender.sendMessage(Component.text("§aMap '" + name + "' added."));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("§cUsage: /elytradogfights map remove <name>"));
            return true;
        }

        String name = args[2];
        if (configManager.removeMap(name)) {
            sender.sendMessage(Component.text("§aMap '" + name + "' removed."));
        } else {
            sender.sendMessage(Component.text("§cNo map found with that name."));
        }
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("§cUsage: /elytradogfights map edit <name> <property> [value...]"));
            return true;
        }

        String name = args[2];
        String property = args[3];
        String value = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : null;

        var map = configManager.getMap(name);
        if (map == null) {
            sender.sendMessage(Component.text("§cNo map found with that name."));
            return true;
        }

        switch (property.toLowerCase()) {
            case "teamconfig":
                if (value == null) {
                    sender.sendMessage(Component.text("§cUsage: /elytradogfights map edit <name> teamconfig <FREE_FOR_ALL|TWO_TEAMS|THREE_TEAMS>"));
                    return true;
                }
                try {
                    TeamConfiguration tc = TeamConfiguration.valueOf(value.toUpperCase());
                    configManager.removeMap(name);
                    configManager.addMap(new net.sudologic.elytraDogfightsRedux.config.DogfightMap(map.getName(), map.getCorner1(), map.getCorner2(), tc));
                    sender.sendMessage(Component.text("§aTeam configuration updated."));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("§cInvalid team configuration."));
                }
                break;
            case "corner1":
            case "corner2":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("§cOnly players can set corners (uses your current location)."));
                    return true;
                }
                Player player = (Player) sender;
                var loc = player.getLocation();
                net.sudologic.elytraDogfightsRedux.config.DogfightMap updatedMap;
                if (property.equalsIgnoreCase("corner1")) {
                    updatedMap = new net.sudologic.elytraDogfightsRedux.config.DogfightMap(map.getName(), loc, map.getCorner2(), map.getTeamConfig());
                } else {
                    updatedMap = new net.sudologic.elytraDogfightsRedux.config.DogfightMap(map.getName(), map.getCorner1(), loc, map.getTeamConfig());
                }
                configManager.removeMap(name);
                configManager.addMap(updatedMap);
                sender.sendMessage(Component.text("§a" + property + " updated to your current location."));
                break;
            case "name":
                if (value == null) {
                    sender.sendMessage(Component.text("§cUsage: /elytradogfights map edit <name> name <newName>"));
                    return true;
                }
                configManager.removeMap(name);
                configManager.addMap(new net.sudologic.elytraDogfightsRedux.config.DogfightMap(value, map.getCorner1(), map.getCorner2(), map.getTeamConfig()));
                sender.sendMessage(Component.text("§aMap renamed to '" + value + "'."));
                break;
            case "spawn":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("§cOnly players can set spawn (uses your current location)."));
                    return true;
                }
                player = (Player) sender;
                var playerLoc = player.getLocation();

                if (map.getTeamConfig() == TeamConfiguration.FREE_FOR_ALL) {
                    // For free-for-all, set the single spawn
                    var spawnPoints = map.getSpawnPoints();
                    spawnPoints.set(0, playerLoc);
                    var updatedMapWithSpawn = new net.sudologic.elytraDogfightsRedux.config.DogfightMap(
                        map.getName(), map.getCorner1(), map.getCorner2(), map.getTeamConfig(), spawnPoints);

                    configManager.removeMap(name);
                    configManager.addMap(updatedMapWithSpawn);
                    sender.sendMessage(Component.text("§aSpawn point updated to your current location."));
                } else {
                    // For team modes, require team specification
                    if (value == null) {
                        sender.sendMessage(Component.text("§cUsage: /elytradogfights map edit <name> spawn <team1|team2|team3>"));
                        return true;
                    }

                    int spawnIndex = -1;
                    if (value.toLowerCase().startsWith("team")) {
                        try {
                            int teamNumber = Integer.parseInt(value.substring(4));
                            spawnIndex = teamNumber - 1;
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Component.text("§cInvalid team number. Use team1, team2, or team3."));
                            return true;
                        }
                    } else {
                        sender.sendMessage(Component.text("§cInvalid spawn point. Use team1, team2, or team3."));
                        return true;
                    }

                    if (spawnIndex < 0 || spawnIndex >= map.getSpawnPoints().size()) {
                        sender.sendMessage(Component.text("§cInvalid spawn point index for this map configuration."));
                        return true;
                    }

                    var spawnPoints = map.getSpawnPoints();
                    spawnPoints.set(spawnIndex, playerLoc);
                    var updatedMapWithSpawn = new net.sudologic.elytraDogfightsRedux.config.DogfightMap(
                        map.getName(), map.getCorner1(), map.getCorner2(), map.getTeamConfig(), spawnPoints);

                    configManager.removeMap(name);
                    configManager.addMap(updatedMapWithSpawn);

                    String spawnName = map.getSpawnPointName(spawnIndex);
                    sender.sendMessage(Component.text("§a" + spawnName + " updated to your current location."));
                }
                break;
            default:
                sender.sendMessage(Component.text("§cUnknown property. Editable: teamconfig, corner1, corner2, name, spawn."));
        }
        return true;
    }

    private boolean handlePlayCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("§cUsage: /elytradogfights play <mapName> [playerName]"));
            return true;
        }

        String mapName = args[1];

        // Check if map exists
        if (configManager.getMap(mapName) == null) {
            sender.sendMessage(Component.text("§cNo map found with that name."));
            return true;
        }

        // Determine the target player
        Player targetPlayer;
        if (args.length >= 3) {
            // Command block usage or admin queuing another player
            String playerName = args[2];
            targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer == null) {
                sender.sendMessage(Component.text("§cPlayer '" + playerName + "' not found."));
                return true;
            }
        } else {
            // Player queuing themselves
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("§cOnly players can queue for maps. Use /elytradogfights play <mapName> <playerName> to queue another player."));
                return true;
            }
            targetPlayer = (Player) sender;
        }

        // Check if the map is already in use
        if (sessionManager.isMapInUse(mapName)) {
            sender.sendMessage(Component.text("§cMap '" + mapName + "' is currently in use."));
            return true;
        }

        // Check if player is already in an active game
        if (sessionManager.isPlayerInActiveGame(targetPlayer.getUniqueId())) {
            sender.sendMessage(Component.text("§c" + targetPlayer.getName() + " is already in an active game."));
            return true;
        }

        // Try to queue the player
        boolean success = sessionManager.queuePlayer(targetPlayer, mapName);
        if (success) {
            var session = sessionManager.getSession(mapName);
            if (session != null) {
                sender.sendMessage(Component.text("§a" + targetPlayer.getName() + " has been queued for map '" + mapName + "'."));
                targetPlayer.sendMessage(Component.text("§aYou have been queued for map '" + mapName + "'. Players: " +
                    session.getQueuedPlayerCount() + "/" + session.getRequiredPlayers()));

                // Notify all queued players about the new player
                for (UUID playerId : session.getQueuedPlayers()) {
                    Player queuedPlayer = Bukkit.getPlayer(playerId);
                    if (queuedPlayer != null && !queuedPlayer.equals(targetPlayer)) {
                        queuedPlayer.sendMessage(Component.text("§e" + targetPlayer.getName() + " joined the queue. Players: " +
                            session.getQueuedPlayerCount() + "/" + session.getRequiredPlayers()));
                    }
                }
            }
        } else {
            sender.sendMessage(Component.text("§cFailed to queue " + targetPlayer.getName() + " for map '" + mapName + "'."));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("map");
            completions.add("play");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("map")) {
                completions.addAll(Arrays.asList("add", "remove", "edit"));
            } else if (args[0].equalsIgnoreCase("play")) {
                completions.addAll(configManager.getMaps().stream().map(m -> m.getName()).collect(Collectors.toList()));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("map") && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("edit"))) {
                completions.addAll(configManager.getMaps().stream().map(m -> m.getName()).collect(Collectors.toList()));
            } else if (args[0].equalsIgnoreCase("play")) {
                completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("edit")) {
            completions.addAll(Arrays.asList("teamconfig", "corner1", "corner2", "name", "spawn"));
        } else if (args.length == 5 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("edit")) {
            if (args[3].equalsIgnoreCase("teamconfig")) {
                for (TeamConfiguration tc : TeamConfiguration.values()) {
                    completions.add(tc.name());
                }
            } else if (args[3].equalsIgnoreCase("spawn")) {
                String mapName = args[2];
                var map = configManager.getMap(mapName);
                if (map != null && map.getTeamConfig() != TeamConfiguration.FREE_FOR_ALL) {
                    int teamCount = map.getTeamConfig() == TeamConfiguration.TWO_TEAMS ? 2 : 3;
                    for (int i = 1; i <= teamCount; i++) {
                        completions.add("team" + i);
                    }
                }
            }
        }

        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
