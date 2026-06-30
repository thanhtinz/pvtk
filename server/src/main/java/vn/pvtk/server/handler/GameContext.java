package vn.pvtk.server.handler;

import vn.pvtk.server.data.GameData;
import vn.pvtk.server.session.SessionManager;
import vn.pvtk.server.world.World;

/** Shared services handed to every packet handler. */
public final class GameContext {

    private final World world;
    private final SessionManager sessions;
    private final GameData gameData;

    public GameContext(World world, SessionManager sessions, GameData gameData) {
        this.world = world;
        this.sessions = sessions;
        this.gameData = gameData;
    }

    public World world() {
        return world;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public GameData gameData() {
        return gameData;
    }
}
