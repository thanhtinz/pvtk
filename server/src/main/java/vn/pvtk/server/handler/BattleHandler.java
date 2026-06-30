package vn.pvtk.server.handler;

import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.BattleEnter;
import vn.pvtk.protocol.message.Messages.BattlePlan;
import vn.pvtk.server.session.PlayerSession;

/** Turn-based battle: enter a fight and submit per-round plans. */
public final class BattleHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        if (op == Opcodes.BATTLE_ENTER) {
            BattleEnter req = BattleEnter.from(packet);
            ctx.world().enterBattle(session, req.monsterId());
        } else if (op == Opcodes.BATTLE_PLAN) {
            BattlePlan plan = BattlePlan.from(packet);
            ctx.world().battlePlan(session, plan.targetIndex(), plan.skillId());
        }
    }
}
