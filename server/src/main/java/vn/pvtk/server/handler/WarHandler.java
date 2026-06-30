package vn.pvtk.server.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.WarDeclare;
import vn.pvtk.protocol.message.Messages.WarStatus;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Country;
import vn.pvtk.server.world.Player;
import vn.pvtk.server.world.WarManager;

/** Country war: a king declares war on another country; both sides see the scoreboard. */
public final class WarHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(WarHandler.class);

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        WarManager war = ctx.world().war();
        Player p = session.player();

        switch (op) {
            case Opcodes.WAR_DECLARE -> {
                int targetId = WarDeclare.from(packet).targetCountryId();
                Country mine = ctx.world().countries().get(p.countryId());
                Country target = ctx.world().countries().get(targetId);
                if (mine == null || mine.kingId() != p.id()) {
                    session.send(new WarStatus(false, "", "", 0, 0, "Chỉ vua mới được tuyên chiến").toPacket());
                    return;
                }
                if (war.declare(mine, target)) {
                    Packet status = war.status().toPacket();
                    ctx.world().broadcastToCountry(mine.id(), status);
                    ctx.world().broadcastToCountry(target.id(), status);
                    log.info("War declared: {} vs {}", mine.name(), target.name());
                } else {
                    session.send(new WarStatus(false, "", "", 0, 0,
                            "Không thể tuyên chiến (đã có chiến tranh?)").toPacket());
                }
            }
            case Opcodes.WAR_STATUS -> session.send(war.status().toPacket());
            default -> { }
        }
    }
}
