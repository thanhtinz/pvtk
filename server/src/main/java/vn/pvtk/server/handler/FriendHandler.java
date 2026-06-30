package vn.pvtk.server.handler;

import java.util.ArrayList;
import java.util.List;
import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.FriendAction;
import vn.pvtk.protocol.message.Messages.FriendEntry;
import vn.pvtk.protocol.message.Messages.FriendList;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/** Friends list: add / remove by name, list with live online status. */
public final class FriendHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        Player p = session.player();

        switch (op) {
            case Opcodes.RELATION_ADD -> {
                String name = FriendAction.from(packet).name();
                if (name != null && !name.isBlank() && !name.equalsIgnoreCase(p.name())) {
                    p.friends().add(name.trim());
                }
                sendList(session, p, ctx);
            }
            case Opcodes.RELATION_DEL -> {
                String name = FriendAction.from(packet).name();
                p.friends().removeIf(f -> f.equalsIgnoreCase(name));
                sendList(session, p, ctx);
            }
            default -> sendList(session, p, ctx); // RELATION_LIST
        }
    }

    private void sendList(PlayerSession session, Player p, GameContext ctx) {
        List<FriendEntry> list = new ArrayList<>();
        for (String name : p.friends()) {
            PlayerSession s = ctx.sessions().all().stream()
                    .filter(x -> x.player() != null && x.player().name().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
            if (s != null) {
                Player fp = s.player();
                list.add(new FriendEntry(fp.id(), fp.name(), fp.level(), true));
            } else {
                list.add(new FriendEntry(0, name, 0, false));
            }
        }
        session.send(new FriendList(list).toPacket());
    }
}
