package vn.pvtk.server.session;

import io.netty.channel.Channel;
import java.util.concurrent.atomic.AtomicLong;
import vn.pvtk.protocol.Packet;
import vn.pvtk.server.world.Player;

/**
 * Per-connection server-side state. Wraps the Netty {@link Channel} and links it
 * to the authenticated {@link Player} once login succeeds.
 */
public final class PlayerSession {

    private static final AtomicLong IDS = new AtomicLong(1);

    private final long sessionId;
    private final Channel channel;
    private volatile Player player;
    private volatile boolean authenticated;
    private volatile long lastActivity;

    public PlayerSession(Channel channel) {
        this.sessionId = IDS.getAndIncrement();
        this.channel = channel;
        this.lastActivity = System.currentTimeMillis();
    }

    public long sessionId() {
        return sessionId;
    }

    public Channel channel() {
        return channel;
    }

    public Player player() {
        return player;
    }

    public void bindPlayer(Player player) {
        this.player = player;
        this.authenticated = true;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void touch() {
        lastActivity = System.currentTimeMillis();
    }

    public long lastActivity() {
        return lastActivity;
    }

    /** Sends a packet to this client. Non-blocking; Netty handles the write queue. */
    public void send(Packet packet) {
        if (channel.isActive()) {
            channel.writeAndFlush(packet);
        }
    }

    public void disconnect() {
        channel.close();
    }

    public String remoteAddress() {
        return String.valueOf(channel.remoteAddress());
    }
}
