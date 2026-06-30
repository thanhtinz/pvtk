package vn.pvtk.server.handler;

import java.util.ArrayList;
import java.util.List;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.AchievementEntry;
import vn.pvtk.protocol.message.Messages.AchievementList;
import vn.pvtk.server.data.Achievements;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/** Lists all achievements with the player's unlocked flags. */
public final class AchievementHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        ctx.world().checkAchievements(session); // re-evaluate before listing
        Player p = session.player();
        List<AchievementEntry> list = new ArrayList<>();
        for (var def : Achievements.all()) {
            list.add(new AchievementEntry(def.id(), def.name(), def.desc(),
                    p.unlockedAchievements().contains(def.id())));
        }
        session.send(new AchievementList(list).toPacket());
    }
}
