package net.sudologic.elytraDogfightsRedux;

import net.sudologic.elytraDogfightsRedux.config.ConfigManager;
import net.sudologic.elytraDogfightsRedux.game.SessionManager;
import net.sudologic.elytraDogfightsRedux.game.GameEventListener;
import net.sudologic.elytraDogfightsRedux.game.CustomScoreboard;
import org.bukkit.plugin.java.JavaPlugin;

public final class ElytraDogfightsRedux extends JavaPlugin {

    private ConfigManager configManager;
    private CustomScoreboard scoreboardManager;

    @Override
    public void onEnable() {
        // Initialize configuration manager
        configManager = new ConfigManager(this);

        // Initialize scoreboard manager
        scoreboardManager = new CustomScoreboard(this);

        // Register event listener for player deaths
        getServer().getPluginManager().registerEvents(new GameEventListener(this), this);

        // Register commands using the dedicated CommandHandler
        new CommandHandler(this).registerCommands();

        getLogger().info("ElytraDogfightsRedux has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save configuration on shutdown
        if (configManager != null) {
            configManager.saveConfig();
        }

        getLogger().info("ElytraDogfightsRedux has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SessionManager getSessionManager() {
        return configManager.getSessionManager();
    }

    public CustomScoreboard getScoreboardManager() {
        return scoreboardManager;
    }
}
