package vn.pvtk.server.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.Packet;
import vn.pvtk.server.handler.PacketDispatcher;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.session.SessionManager;

/**
 * Bridges a Netty channel to a {@link PlayerSession} and forwards decoded
 * packets to the {@link PacketDispatcher}. One instance is created per
 * connection (the pipeline is not shareable), so it can hold per-connection state.
 */
public final class SessionHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger log = LoggerFactory.getLogger(SessionHandler.class);

    private final SessionManager sessions;
    private final PacketDispatcher dispatcher;
    private PlayerSession session;

    public SessionHandler(SessionManager sessions, PacketDispatcher dispatcher) {
        this.sessions = sessions;
        this.dispatcher = dispatcher;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        session = new PlayerSession(ctx.channel());
        sessions.add(session);
        log.info("Connection opened: {} (session {})", session.remoteAddress(), session.sessionId());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        dispatcher.dispatch(session, msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (session != null) {
            if (session.player() != null) {
                // Tell neighbours the player vanished, then deregister.
                dispatcher.world().leave(session);
            }
            sessions.remove(session);
            log.info("Connection closed: session {}", session.sessionId());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Channel error on session {}: {}",
                session != null ? session.sessionId() : "?", cause.toString());
        ctx.close();
    }
}
