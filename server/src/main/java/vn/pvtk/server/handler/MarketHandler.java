package vn.pvtk.server.handler;

import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.BagSnapshot;
import vn.pvtk.protocol.message.Messages.MarketBuy;
import vn.pvtk.protocol.message.Messages.MarketList;
import vn.pvtk.protocol.message.Messages.MarketSell;
import vn.pvtk.server.data.ItemDef;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Inventory;
import vn.pvtk.server.world.MarketRegistry;
import vn.pvtk.server.world.Player;

/** Player-to-player marketplace: list, consign, and buy. */
public final class MarketHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        MarketRegistry market = ctx.world().market();
        Player p = session.player();

        switch (op) {
            case Opcodes.MARKET_LIST -> session.send(new MarketList(market.view()).toPacket());

            case Opcodes.MARKET_SELL -> {
                MarketSell sell = MarketSell.from(packet);
                Inventory inv = p.inventory();
                int itemId = inv.itemAt(sell.bagSlot());
                int price = Math.max(1, sell.price());
                int count = Math.max(1, sell.count());
                if (itemId > 0) {
                    ItemDef def = ctx.gameData().item(itemId);
                    int removed = inv.remove(sell.bagSlot(), count);
                    if (removed > 0) {
                        market.add(p.id(), p.name(), itemId,
                                def != null ? def.name() : "?", removed, price);
                    }
                }
                sendBag(session, p);
                session.send(new MarketList(market.view()).toPacket());
            }

            case Opcodes.MARKET_BUY -> {
                int listingId = MarketBuy.from(packet).listingId();
                MarketRegistry.Listing l = market.get(listingId);
                if (l != null && l.sellerId() != p.id() && p.gold() >= l.price()
                        && p.inventory().add(l.itemId(), l.count()) >= 0) {
                    p.addGold(-l.price());
                    market.remove(listingId);
                    // Pay the seller by mail so it works even if they are offline.
                    ctx.world().mail().send("Chợ", l.sellerName(),
                            "Bán được hàng", "Bạn đã bán " + l.itemName() + " x" + l.count(), l.price());
                    var to = ctx.sessions().all().stream()
                            .filter(s -> s.player() != null && s.player().name().equals(l.sellerName()))
                            .findFirst().orElse(null);
                    if (to != null) {
                        to.send(new vn.pvtk.protocol.message.Messages.MailList(
                                ctx.world().mail().inbox(l.sellerName())).toPacket());
                    }
                }
                sendBag(session, p);
                session.send(new MarketList(market.view()).toPacket());
            }
            default -> { }
        }
    }

    private void sendBag(PlayerSession session, Player p) {
        session.send(new BagSnapshot(p.gold(), p.inventory().bagStacks(),
                p.inventory().equipmentStacks()).toPacket());
    }
}
