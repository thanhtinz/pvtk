package vn.pvtk.server.handler;

import vn.pvtk.server.data.GameData;
import vn.pvtk.server.session.SessionManager;
import vn.pvtk.server.world.World;

/** Shared services handed to every packet handler. */
public final class GameContext {

    private final World world;
    private final SessionManager sessions;
    private final GameData gameData;
    private final vn.pvtk.server.account.AccountService accounts;

    public GameContext(World world, SessionManager sessions, GameData gameData,
                       vn.pvtk.server.account.AccountService accounts) {
        this.world = world;
        this.sessions = sessions;
        this.gameData = gameData;
        this.accounts = accounts;
    }

    public vn.pvtk.server.account.AccountService accounts() {
        return accounts;
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
