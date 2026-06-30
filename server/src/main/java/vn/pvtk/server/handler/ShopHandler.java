package vn.pvtk.server.handler;

import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.BagSnapshot;
import vn.pvtk.protocol.message.Messages.ShopBuy;
import vn.pvtk.protocol.message.Messages.ShopEntry;
import vn.pvtk.protocol.message.Messages.ShopListing;
import vn.pvtk.protocol.message.Messages.ShopOpen;
import vn.pvtk.protocol.message.Messages.ShopSell;
import vn.pvtk.server.data.GameData;
import vn.pvtk.server.data.ItemDef;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Inventory;
import vn.pvtk.server.world.Player;
import java.util.ArrayList;
import java.util.List;

/** NPC shop: open a listing, buy with gold, sell items back for gold. */
public final class ShopHandler implements PacketHandler {

    /** Sell-back is a fraction of the catalogue price. */
    private static final double SELL_RATE = 0.5;

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        GameData data = ctx.gameData();
        Player p = session.player();

        switch (op) {
            case Opcodes.SHOP_LIST -> {
                int shopId = ShopOpen.from(packet).shopId();
                if (shopId <= 0) {
                    shopId = 1; // default starter shop
                }
                List<ShopEntry> entries = new ArrayList<>();
                for (GameData.ShopOffer offer : data.shop(shopId)) {
                    ItemDef def = data.item(offer.itemId());
                    if (def != null) {
                        int price = offer.price() > 0 ? offer.price() : Math.max(1, def.price());
                        entries.add(new ShopEntry(def.id(), def.name(), price, def.type(), def.icon()));
                    }
                }
                session.send(new ShopListing(shopId, entries).toPacket());
            }
            case Opcodes.SHOP_BUY -> {
                ShopBuy buy = ShopBuy.from(packet);
                ItemDef def = data.item(buy.itemId());
                int count = Math.max(1, buy.count());
                if (def != null) {
                    int unit = Math.max(1, def.price());
                    int total = unit * count;
                    if (p.gold() >= total && p.inventory().add(def.id(), count) >= 0) {
                        p.addGold(-total);
                    }
                }
                sendBag(session, p);
            }
            case Opcodes.SHOP_SELL -> {
                ShopSell sell = ShopSell.from(packet);
                Inventory inv = p.inventory();
                int itemId = inv.itemAt(sell.bagSlot());
                if (itemId > 0) {
                    ItemDef def = data.item(itemId);
                    int removed = inv.remove(sell.bagSlot(), Math.max(1, sell.count()));
                    if (def != null && removed > 0) {
                        p.addGold((int) (def.price() * SELL_RATE) * removed);
                    }
                }
                sendBag(session, p);
            }
            default -> { }
        }
    }

    private void sendBag(PlayerSession session, Player p) {
        session.send(new BagSnapshot(p.gold(), p.inventory().bagStacks(),
                p.inventory().equipmentStacks()).toPacket());
    }
}
