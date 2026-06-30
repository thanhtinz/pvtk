package vn.pvtk.server.handler;

import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.JumpMap;
import vn.pvtk.server.session.PlayerSession;

/** Moves the player to a different map/zone. */
public final class JumpMapHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        JumpMap req = JumpMap.from(packet);
        ctx.world().changeMap(session, req.mapId());
    }
}
