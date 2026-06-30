package vn.pvtk.server.handler;

import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.EscortStatus;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/** Escort: start a caravan mission or query status. */
public final class EscortHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        if (op == Opcodes.ESCORT_START) {
            ctx.world().startEscort(session);
        } else {
            Player p = session.player();
            session.send(new EscortStatus(p.escortActive(),
                    p.escortActive() ? 50 : 0,
                    p.escortActive() ? ctx.world().map(p.escortDestMap()).name() : "",
                    p.escortActive() ? "Đang hộ tống" : "Không có nhiệm vụ").toPacket());
        }
    }
}
