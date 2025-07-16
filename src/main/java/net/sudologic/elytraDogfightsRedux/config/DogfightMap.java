package net.sudologic.elytraDogfightsRedux.config;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@SerializableAs("DogfightMap")
public class DogfightMap implements ConfigurationSerializable {
    private final String name;
    private final Location corner1;
    private final Location corner2;
    private final TeamConfiguration teamConfig;
    private final List<Location> spawnPoints;

    public DogfightMap(String name, Location corner1, Location corner2, TeamConfiguration teamConfig) {
        this.name = name;
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.teamConfig = teamConfig;
        this.spawnPoints = new ArrayList<>();
        // Initialize spawn points based on team configuration
        initializeSpawnPoints();
    }

    public DogfightMap(String name, Location corner1, Location corner2, TeamConfiguration teamConfig, List<Location> spawnPoints) {
        this.name = name;
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.teamConfig = teamConfig;
        this.spawnPoints = spawnPoints != null ? new ArrayList<>(spawnPoints) : new ArrayList<>();
        // Ensure we have the right number of spawn points
        ensureCorrectSpawnPointCount();
    }

    private void initializeSpawnPoints() {
        int requiredSpawnPoints = getRequiredSpawnPointCount();
        spawnPoints.clear();
        for (int i = 0; i < requiredSpawnPoints; i++) {
            spawnPoints.add(null);
        }
    }

    private void ensureCorrectSpawnPointCount() {
        int requiredSpawnPoints = getRequiredSpawnPointCount();
        while (spawnPoints.size() < requiredSpawnPoints) {
            spawnPoints.add(null);
        }
        while (spawnPoints.size() > requiredSpawnPoints) {
            spawnPoints.remove(spawnPoints.size() - 1);
        }
    }

    private int getRequiredSpawnPointCount() {
        switch (teamConfig) {
            case FREE_FOR_ALL:
                return 1;
            case TWO_TEAMS:
                return 2;
            case THREE_TEAMS:
                return 3;
            default:
                return 1;
        }
    }

    public List<Location> getSpawnPoints() {
        return new ArrayList<>(spawnPoints);
    }

    public Location getSpawnPoint(int index) {
        if (index >= 0 && index < spawnPoints.size()) {
            return spawnPoints.get(index);
        }
        return null;
    }

    public void setSpawnPoint(int index, Location location) {
        if (index >= 0 && index < spawnPoints.size()) {
            spawnPoints.set(index, location);
        }
    }

    public String getSpawnPointName(int index) {
        if (teamConfig == TeamConfiguration.FREE_FOR_ALL) {
            return "spawn";
        } else {
            return "team" + (index + 1) + "spawn";
        }
    }

    public String getName() {
        return name;
    }

    public Location getCorner1() {
        return corner1;
    }

    public Location getCorner2() {
        return corner2;
    }

    public TeamConfiguration getTeamConfig() {
        return teamConfig;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("corner1", corner1);
        map.put("corner2", corner2);
        map.put("teamConfig", teamConfig.name());
        map.put("spawnPoints", spawnPoints);
        return map;
    }

    public static DogfightMap deserialize(Map<String, Object> map) {
        String name = (String) map.get("name");
        Location corner1 = (Location) map.get("corner1");
        Location corner2 = (Location) map.get("corner2");
        TeamConfiguration teamConfig = TeamConfiguration.valueOf((String) map.get("teamConfig"));
        List<Location> spawnPoints = (List<Location>) map.get("spawnPoints");
        return new DogfightMap(name, corner1, corner2, teamConfig, spawnPoints);
    }
}