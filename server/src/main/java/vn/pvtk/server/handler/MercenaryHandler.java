package vn.pvtk.server.handler;

import java.util.ArrayList;
import java.util.List;
import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.MercBuy;
import vn.pvtk.protocol.message.Messages.MercEntry;
import vn.pvtk.protocol.message.Messages.MercList;
import vn.pvtk.server.data.MonsterDef;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/**
 * Mercenary / pet companions. Hireable companions are derived from low-level
 * monsters in {@code monster.txt}; hiring one spends gold and grants a permanent
 * attack bonus (a simplified pet model).
 */
public final class MercenaryHandler implements PacketHandler {

    private static final int OFFER_COUNT = 5;

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        Player p = session.player();
        List<MonsterDef> offers = hireable(ctx);

        switch (op) {
            case Opcodes.MERC_LIST -> session.send(buildList(offers, p).toPacket());
            case Opcodes.MERC_BUY -> {
                int mercId = MercBuy.from(packet).mercId();
                MonsterDef def = offers.stream().filter(m -> m.id() == mercId).findFirst().orElse(null);
                if (def != null) {
                    int price = price(def);
                    int bonus = atkBonus(def);
                    if (p.gold() >= price) {
                        p.addGold(-price);
                        p.setPet(def.name(), bonus);
                        session.send(new vn.pvtk.protocol.message.Messages.Spawn(p.toState()).toPacket());
                    }
                }
                session.send(buildList(offers, p).toPacket());
            }
            default -> { }
        }
    }

    private List<MonsterDef> hireable(GameContext ctx) {
        return ctx.gameData().monsterList().stream()
                .sorted((a, b) -> Integer.compare(a.level(), b.level()))
                .limit(OFFER_COUNT)
                .toList();
    }

    private MercList buildList(List<MonsterDef> offers, Player p) {
        List<MercEntry> list = new ArrayList<>();
        for (MonsterDef def : offers) {
            list.add(new MercEntry(def.id(), def.name(), def.level(), atkBonus(def),
                    price(def), p.petName().equals(def.name())));
        }
        return new MercList(list);
    }

    private int atkBonus(MonsterDef def) {
        return Math.max(5, def.level() * 3);
    }

    private int price(MonsterDef def) {
        return Math.max(50, def.level() * 50);
    }
}
