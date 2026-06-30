package vn.pvtk.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.EntityState;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.session.SessionManager;

/**
 * The authoritative game world: owns the map instances and provides the
 * area-of-interest broadcast primitives that drive multiplayer. All gameplay
 * mutations funnel through here so state stays consistent.
 */
public final class World {

    private static final Logger log = LoggerFactory.getLogger(World.class);

    private final SessionManager sessions;
    private final Map<Integer, MapInstance> maps = new ConcurrentHashMap<>();

    public World(SessionManager sessions) {
        this.sessions = sessions;
        // A small set of starter maps. In a full build these load from data files
        // converted from the original client's `map/` resources.
        register(new MapInstance(1, "Tân Thủ Thôn", 64, 64, 32, 32));
        register(new MapInstance(2, "Lạc Dương Thành", 96, 96, 48, 48));
        register(new MapInstance(3, "Hoang Dã", 128, 128, 20, 20));
    }

    private void register(MapInstance map) {
        maps.put(map.mapId(), map);
    }

    public MapInstance map(int mapId) {
        return maps.getOrDefault(mapId, maps.get(1));
    }

    public SessionManager sessions() {
        return sessions;
    }

    /** Places a freshly logged-in player into their map and notifies neighbours. */
    public void enter(PlayerSession session) {
        Player p = session.player();
        MapInstance map = map(p.mapId());
        map.addPlayer(p.id());
        // Tell everyone already in the map that a new entity spawned.
        broadcastToMap(map, new vn.pvtk.protocol.message.Messages.Spawn(p.toState()).toPacket(), p.id());
        log.info("Player {} (#{}) entered map {} [{}] ({} online)",
                p.name(), p.id(), map.mapId(), map.name(), sessions.onlineCount());
    }

    /** Removes a player from the world and notifies neighbours of the despawn. */
    public void leave(PlayerSession session) {
        Player p = session.player();
        if (p == null) {
            return;
        }
        MapInstance map = map(p.mapId());
        map.removePlayer(p.id());
        broadcastToMap(map, new vn.pvtk.protocol.message.Messages.Despawn(p.id()).toPacket(), p.id());
        log.info("Player {} (#{}) left map {} ({} online)",
                p.name(), p.id(), map.mapId(), sessions.onlineCount());
    }

    /** Builds the world snapshot a client receives right after login. */
    public List<EntityState> visibleEntities(Player viewer) {
        MapInstance map = map(viewer.mapId());
        List<EntityState> list = new ArrayList<>();
        for (int otherId : map.playerIds()) {
            if (otherId == viewer.id()) {
                continue;
            }
            PlayerSession s = sessions.byPlayerId(otherId);
            if (s != null && s.player() != null) {
                list.add(s.player().toState());
            }
        }
        return list;
    }

    /** Applies an authoritative move and broadcasts it to everyone on the map. */
    public void move(PlayerSession session, int x, int y, int dir) {
        Player p = session.player();
        MapInstance map = map(p.mapId());
        int cx = map.clampX(x);
        int cy = map.clampY(y);
        p.moveTo(cx, cy, dir);
        Packet update = new vn.pvtk.protocol.message.Messages.MoveUpdate(p.id(), cx, cy, dir).toPacket();
        broadcastToMap(map, update, -1); // include the mover for authoritative reconciliation
    }

    /** Sends a packet to every player on {@code map} except {@code exceptPlayerId}. */
    public void broadcastToMap(MapInstance map, Packet packet, int exceptPlayerId) {
        for (int pid : map.playerIds()) {
            if (pid == exceptPlayerId) {
                continue;
            }
            sessions.sendTo(pid, packet);
        }
    }

    public void broadcastToAll(Packet packet) {
        for (PlayerSession s : sessions.all()) {
            if (s.isAuthenticated()) {
                s.send(packet);
            }
        }
    }
}
