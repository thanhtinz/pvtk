package vn.pvtk.server.handler;

import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.MoveRequest;
import vn.pvtk.server.session.PlayerSession;

/** Applies a client's movement intent authoritatively and broadcasts the result. */
public final class MovementHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        MoveRequest req = MoveRequest.from(packet);
        ctx.world().move(session, req.x(), req.y(), req.dir());
    }
}
