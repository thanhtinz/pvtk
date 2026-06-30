package vn.pvtk.server.handler;

import vn.pvtk.protocol.Packet;
import vn.pvtk.server.session.PlayerSession;

/** Handles a single inbound opcode. Implementations must be stateless / thread-safe. */
public interface PacketHandler {

    void handle(PlayerSession session, Packet packet, GameContext ctx);

    /** Whether this opcode may be processed before the session has logged in. */
    default boolean allowsUnauthenticated() {
        return false;
    }
}
