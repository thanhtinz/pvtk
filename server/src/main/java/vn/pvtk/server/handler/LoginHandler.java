package vn.pvtk.server.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.LoginRequest;
import vn.pvtk.protocol.message.Messages.LoginResponse;
import vn.pvtk.protocol.message.Messages.WorldSnapshot;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.MapInstance;
import vn.pvtk.server.world.Player;
import vn.pvtk.server.world.World;

/**
 * Authenticates a connection and brings the player into the world.
 *
 * <p>This rewrite uses a trivial "create-on-first-login" auth so the multiplayer
 * loop is demonstrable end-to-end; a production build would validate credentials
 * against a database here (the hook is the single {@code authenticate} method).
 */
public final class LoginHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginHandler.class);

    @Override
    public boolean allowsUnauthenticated() {
        return true;
    }

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        if (session.isAuthenticated()) {
            return; // ignore duplicate logins
        }
        LoginRequest req = LoginRequest.from(packet);

        if (!authenticate(req.username(), req.password())) {
            session.send(new LoginResponse(false, "Sai tài khoản hoặc mật khẩu", null).toPacket());
            return;
        }
        if (req.username().isBlank()) {
            session.send(new LoginResponse(false, "Tên tài khoản trống", null).toPacket());
            return;
        }

        World world = ctx.world();
        MapInstance start = world.map(1);
        Player player = new Player(req.username(), start.mapId(), start.spawnX(), start.spawnY());
        session.bindPlayer(player);
        ctx.sessions().onAuthenticated(session);

        // 1) Acknowledge login with the player's own state.
        session.send(new LoginResponse(true, "Đăng nhập thành công", player.toState()).toPacket());
        // 2) Enter the world (notifies neighbours of the spawn).
        world.enter(session);
        // 3) Send the full snapshot of everyone already visible.
        session.send(new WorldSnapshot(player.mapId(), world.visibleEntities(player)).toPacket());

        log.info("Login OK: {} from {} on line {}", req.username(), session.remoteAddress(), req.serverLine());
    }

    /** Replace with real credential validation. Returns true for any non-empty user here. */
    private boolean authenticate(String username, String password) {
        return username != null && !username.isBlank();
    }
}
