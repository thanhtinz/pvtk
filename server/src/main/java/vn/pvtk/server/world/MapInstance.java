package vn.pvtk.server.world;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A single map/zone holding the set of player ids currently present. */
public final class MapInstance {

    private final int mapId;
    private final String name;
    private final int width;
    private final int height;
    private final int spawnX;
    private final int spawnY;
    private final Set<Integer> players = ConcurrentHashMap.newKeySet();

    public MapInstance(int mapId, String name, int width, int height, int spawnX, int spawnY) {
        this.mapId = mapId;
        this.name = name;
        this.width = width;
        this.height = height;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
    }

    public int mapId() {
        return mapId;
    }

    public String name() {
        return name;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int spawnX() {
        return spawnX;
    }

    public int spawnY() {
        return spawnY;
    }

    public void addPlayer(int playerId) {
        players.add(playerId);
    }

    public void removePlayer(int playerId) {
        players.remove(playerId);
    }

    /** Player ids currently in this map (immutable snapshot view). */
    public Set<Integer> playerIds() {
        return Collections.unmodifiableSet(players);
    }

    /** Clamps a target tile into the walkable map bounds. */
    public int clampX(int x) {
        return Math.max(0, Math.min(width - 1, x));
    }

    public int clampY(int y) {
        return Math.max(0, Math.min(height - 1, y));
    }
}
