package vn.pvtk.server.handler;

import java.util.ArrayList;
import java.util.List;
import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.QuestAction;
import vn.pvtk.protocol.message.Messages.QuestEntry;
import vn.pvtk.protocol.message.Messages.QuestList;
import vn.pvtk.server.data.Quests;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/** Quest board: list, accept, and turn in kill-quests. */
public final class QuestHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        Player p = session.player();

        switch (op) {
            case Opcodes.QUEST_LIST -> sendList(session, p);
            case Opcodes.QUEST_ACCEPT -> {
                int id = QuestAction.from(packet).questId();
                if (Quests.byId(id) != null && !p.completedQuests().contains(id)) {
                    p.acceptQuest(id);
                }
                sendList(session, p);
            }
            case Opcodes.QUEST_COMPLETE -> {
                int id = QuestAction.from(packet).questId();
                Quests.QuestDef def = Quests.byId(id);
                if (def != null && p.hasQuest(id) && p.questProgressOf(id) >= def.killTarget()) {
                    p.addGold(def.rewardGold());
                    p.addExp(def.rewardExp());
                    p.completeQuest(id);
                    session.send(new vn.pvtk.protocol.message.Messages.Spawn(p.toState()).toPacket());
                    ctx.world().checkAchievements(session);
                }
                sendList(session, p);
            }
            default -> { }
        }
    }

    private void sendList(PlayerSession session, Player p) {
        List<QuestEntry> entries = new ArrayList<>();
        for (Quests.QuestDef def : Quests.all()) {
            int state;
            int progress;
            if (p.completedQuests().contains(def.id())) {
                state = 2;
                progress = def.killTarget();
            } else if (p.hasQuest(def.id())) {
                state = 1;
                progress = Math.min(def.killTarget(), p.questProgressOf(def.id()));
            } else {
                state = 0;
                progress = 0;
            }
            entries.add(new QuestEntry(def.id(), def.name(), def.desc(),
                    progress, def.killTarget(), state, def.rewardExp(), def.rewardGold()));
        }
        session.send(new QuestList(entries).toPacket());
    }
}
