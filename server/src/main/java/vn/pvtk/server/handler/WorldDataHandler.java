package vn.pvtk.server.handler;

import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.WorldSnapshot;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/** Re-sends the current world snapshot on demand (e.g. after a client map load). */
public final class WorldDataHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        Player p = session.player();
        session.send(new WorldSnapshot(p.mapId(), ctx.world().visibleEntities(p)).toPacket());
    }
}
