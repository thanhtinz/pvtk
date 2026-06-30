package vn.pvtk.server.handler;

import vn.pvtk.server.session.SessionManager;
import vn.pvtk.server.world.World;

/** Shared services handed to every packet handler. */
public final class GameContext {

    private final World world;
    private final SessionManager sessions;

    public GameContext(World world, SessionManager sessions) {
        this.world = world;
        this.sessions = sessions;
    }

    public World world() {
        return world;
    }

    public SessionManager sessions() {
        return sessions;
    }
}
