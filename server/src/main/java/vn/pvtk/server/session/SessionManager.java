package vn.pvtk.server.session;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import vn.pvtk.protocol.Packet;

/** Thread-safe registry of all live sessions, indexed by session id and player id. */
public final class SessionManager {

    private final ConcurrentHashMap<Long, PlayerSession> bySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, PlayerSession> byPlayer = new ConcurrentHashMap<>();

    public void add(PlayerSession session) {
        bySession.put(session.sessionId(), session);
    }

    public void onAuthenticated(PlayerSession session) {
        if (session.player() != null) {
            byPlayer.put(session.player().id(), session);
        }
    }

    public void remove(PlayerSession session) {
        bySession.remove(session.sessionId());
        if (session.player() != null) {
            byPlayer.remove(session.player().id());
        }
    }

    public PlayerSession byPlayerId(int playerId) {
        return byPlayer.get(playerId);
    }

    public Collection<PlayerSession> all() {
        return bySession.values();
    }

    public int onlineCount() {
        return byPlayer.size();
    }

    /** Sends a packet to a specific player if they are online. */
    public void sendTo(int playerId, Packet packet) {
        PlayerSession s = byPlayer.get(playerId);
        if (s != null) {
            s.send(packet);
        }
    }
}
