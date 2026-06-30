package vn.pvtk.server.handler;

import java.util.ArrayList;
import java.util.List;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.SkillEntry;
import vn.pvtk.protocol.message.Messages.SkillList;
import vn.pvtk.server.data.SkillDef;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/** Replies with the skills the player currently knows. */
public final class SkillHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        Player p = session.player();
        List<SkillEntry> list = new ArrayList<>();
        for (int skillId : p.learnedSkills()) {
            SkillDef def = ctx.gameData().skill(skillId);
            if (def != null) {
                list.add(new SkillEntry(def.id(), def.level(), def.name(), def.useMp()));
            }
        }
        session.send(new SkillList(list).toPacket());
    }
}
