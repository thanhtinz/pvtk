package vn.pvtk.server.handler;

import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.ArenaStatus;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/** Arena: queue for a 1-v-1 duel or query status. */
public final class ArenaHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        if (op == Opcodes.ARENA_QUEUE) {
            ctx.world().arenaQueue(session);
        } else {
            Player p = session.player();
            int state = p.inArena() ? 2 : 0;
            session.send(new ArenaStatus(state, "", p.arenaRank(), "").toPacket());
        }
    }
}
