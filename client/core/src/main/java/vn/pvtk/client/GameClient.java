package vn.pvtk.client;

import java.io.IOException;
import vn.pvtk.client.model.GameState;
import vn.pvtk.client.net.ConnectionListener;
import vn.pvtk.client.net.GameConnection;
import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.ChatRequest;
import vn.pvtk.protocol.message.Messages.Despawn;
import vn.pvtk.protocol.message.Messages.LoginRequest;
import vn.pvtk.protocol.message.Messages.LoginResponse;
import vn.pvtk.protocol.message.Messages.MoveRequest;
import vn.pvtk.protocol.message.Messages.MoveUpdate;
import vn.pvtk.protocol.message.Messages.Spawn;
import vn.pvtk.protocol.message.Messages.WorldSnapshot;

/**
 * The shared, platform-neutral game client used by every front-end
 * (PC / Android / iOS / headless Java). It owns the connection, parses inbound
 * gameplay packets into the observable {@link GameState}, and exposes high-level
 * actions ({@link #login}, {@link #move}, {@link #say}).
 */
public final class GameClient implements ConnectionListener {

    private final GameConnection connection = new GameConnection(this);
    private final GameState state = new GameState();
    private final GameClientListener listener;

    public GameClient(GameClientListener listener) {
        this.listener = listener != null ? listener : new GameClientListener() { };
    }

    public GameState state() {
        return state;
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    public void connect(String host, int port) throws IOException {
        connection.connect(host, port, 8000);
    }

    public void login(String username, String password, int serverLine) {
        connection.send(new LoginRequest(username, password, serverLine).toPacket());
    }

    public void move(int x, int y, int dir) {
        connection.send(new MoveRequest(x, y, dir).toPacket());
    }

    public void say(String text) {
        say(vn.pvtk.protocol.message.Messages.Channel.WORLD, null, text);
    }

    public void say(vn.pvtk.protocol.message.Messages.Channel channel, String target, String text) {
        connection.send(new ChatRequest(channel, target, text).toPacket());
    }

    public void disconnect() {
        connection.disconnect("client requested");
    }

    // ------------------------------------------------------------------
    // ConnectionListener (network thread)
    // ------------------------------------------------------------------

    @Override
    public void onConnected() {
        listener.onConnected();
    }

    @Override
    public void onPacket(Packet p) {
        int op = p.command() & 0xFFFF;
        switch (op) {
            case Opcodes.LOGIN -> handleLogin(p);
            case Opcodes.WORLD_DATA -> handleWorld(p);
            case Opcodes.AUTO_MOVE -> handleMove(p);
            case Opcodes.GET_SPRITE -> handleSpawnOrDespawn(p);
            case Opcodes.CHAT -> listener.onChat(vn.pvtk.protocol.message.Messages.ChatBroadcast.from(p));
            default -> { /* opcode not yet implemented in this rewrite */ }
        }
    }

    @Override
    public void onDisconnected(String reason) {
        listener.onDisconnected(reason);
    }

    // ------------------------------------------------------------------
    // Inbound handlers
    // ------------------------------------------------------------------

    private void handleLogin(Packet p) {
        LoginResponse res = LoginResponse.from(p);
        if (res.ok() && res.self() != null) {
            state.setSelf(res.self());
        }
        listener.onLoginResult(res.ok(), res.message());
        listener.onWorldChanged();
    }

    private void handleWorld(Packet p) {
        WorldSnapshot snap = WorldSnapshot.from(p);
        state.clearOthers();
        snap.entities().forEach(state::upsert);
        listener.onWorldChanged();
    }

    private void handleMove(Packet p) {
        MoveUpdate u = MoveUpdate.from(p);
        var e = state.get(u.entityId());
        if (e != null) {
            e.moveTo(u.x(), u.y(), u.dir());
        }
        listener.onEntityMoved(u.entityId(), u.x(), u.y(), u.dir());
    }

    private void handleSpawnOrDespawn(Packet p) {
        if (vn.pvtk.protocol.message.Messages.isSpawn(p)) {
            Spawn s = Spawn.from(p);
            state.upsert(s.entity());
        } else {
            Despawn d = Despawn.from(p);
            state.remove(d.entityId());
        }
        listener.onWorldChanged();
    }
}
