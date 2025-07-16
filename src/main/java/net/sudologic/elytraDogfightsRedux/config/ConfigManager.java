package net.sudologic.elytraDogfightsRedux.config;

import net.sudologic.elytraDogfightsRedux.ElytraDogfightsRedux;
import net.sudologic.elytraDogfightsRedux.game.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {
    private final ElytraDogfightsRedux plugin;
    private FileConfiguration config;
    private File configFile;
    private List<DogfightMap> maps;
    private Location globalSpawn;
    private final SessionManager sessionManager;
    private int countdownDuration = 10; // Default 10 seconds
    private String serverName = "ElytraDogfights"; // Default server name

    public ConfigManager(ElytraDogfightsRedux plugin) {
        this.plugin = plugin;
        this.maps = new ArrayList<>();
        this.sessionManager = new SessionManager();

        // Register serialization
        ConfigurationSerialization.registerClass(DogfightMap.class);

        loadConfig();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "maps.yml");

        if (!configFile.exists()) {
            plugin.saveResource("maps.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        loadMaps();
        loadGlobalSpawn();
    }

    private void loadMaps() {
        List<DogfightMap> loadedMaps = (List<DogfightMap>) config.getList("maps", new ArrayList<>());
        maps.clear();
        maps.addAll(loadedMaps);

        // Update SessionManager with countdown duration and global spawn
        sessionManager.setCountdownDuration(countdownDuration);
        sessionManager.setGlobalSpawn(globalSpawn);

        // Create sessions for all loaded maps
        for (DogfightMap map : maps) {
            sessionManager.createSession(map);
        }
    }

    private void loadGlobalSpawn() {
        Object locObj = config.get("globalSpawn");
        if (locObj instanceof Location) {
            globalSpawn = (Location) locObj;
        } else if (locObj instanceof org.bukkit.configuration.ConfigurationSection) {
            globalSpawn = (Location) config.get("globalSpawn");
        } else {
            globalSpawn = null;
        }
        
        // Load countdown duration
        countdownDuration = config.getInt("countdownDuration", 10);

        // Load server name
        serverName = config.getString("serverName", "ElytraDogfights");
    }

    public void saveConfig() {
        config.set("maps", maps);
        config.set("globalSpawn", globalSpawn);
        config.set("countdownDuration", countdownDuration);
        config.set("serverName", serverName);
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save maps.yml", e);
        }
    }

    public List<DogfightMap> getMaps() {
        return new ArrayList<>(maps);
    }

    public void addMap(DogfightMap map) {
        maps.add(map);
        sessionManager.createSession(map);
        saveConfig();
    }

    public boolean removeMap(String name) {
        boolean removed = maps.removeIf(map -> map.getName().equals(name));
        if (removed) {
            sessionManager.removeSession(name);
            saveConfig();
        }
        return removed;
    }

    public DogfightMap getMap(String name) {
        return maps.stream()
                .filter(map -> map.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public Location getGlobalSpawn() {
        return globalSpawn;
    }

    public void setGlobalSpawn(Location location) {
        this.globalSpawn = location;
        sessionManager.setGlobalSpawn(location);
        saveConfig();
    }

    public int getCountdownDuration() {
        return countdownDuration;
    }

    public void setCountdownDuration(int duration) {
        this.countdownDuration = duration;
        sessionManager.setCountdownDuration(duration);
        saveConfig();
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
        saveConfig();
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }
}