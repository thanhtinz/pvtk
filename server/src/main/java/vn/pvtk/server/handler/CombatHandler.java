package vn.pvtk.server.handler;

import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.AttackRequest;
import vn.pvtk.server.session.PlayerSession;

/** Resolves an attack request against a monster or another player. */
public final class CombatHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        AttackRequest req = AttackRequest.from(packet);
        ctx.world().attack(session, req.targetId(), System.currentTimeMillis());
    }
}
