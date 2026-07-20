package vn.pvtk.server.handler;

import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages;
import vn.pvtk.protocol.message.Messages.BagSnapshot;
import vn.pvtk.protocol.message.Messages.RedeemBuy;
import vn.pvtk.protocol.message.Messages.RedeemEntry;
import vn.pvtk.protocol.message.Messages.RedeemList;
import vn.pvtk.server.account.Account;
import vn.pvtk.server.data.GameData;
import vn.pvtk.server.data.GameData.RedeemBonus;
import vn.pvtk.server.data.GameData.RedeemPack;
import vn.pvtk.server.data.ItemDef;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * In-game top-up menu ("Gói nạp"): the player browses the admin-configured
 * packages and redeems one, spending web-wallet Xu to receive in-game coin
 * ("Tiền nạp") plus any bonus items. This is the game-side counterpart of the
 * website top-up flow — money is added to the wallet on the web, then turned
 * into in-game value by choosing a package here.
 */
public final class RedeemHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        switch (op) {
            case Opcodes.REDEEM_LIST -> sendCatalogue(session, ctx);
            case Opcodes.REDEEM_BUY -> redeem(session, RedeemBuy.from(packet).packageId(), ctx);
            default -> { }
        }
    }

    /** Sends the current catalogue of packages to the client. */
    public static void sendCatalogue(PlayerSession session, GameContext ctx) {
        GameData data = ctx.gameData();
        List<RedeemEntry> entries = new ArrayList<>();
        for (RedeemPack pk : data.redeemPacks()) {
            entries.add(new RedeemEntry(pk.id(), pk.name(), pk.costXu(), pk.coin(),
                    bonusText(pk, data)));
        }
        session.send(new RedeemList(entries).toPacket());
    }

    private void redeem(PlayerSession session, int packageId, GameContext ctx) {
        Account a = session.account();
        Player p = session.player();
        GameData data = ctx.gameData();
        RedeemPack pk = data.redeemPack(packageId);
        if (a == null || p == null || pk == null) {
            ConvertHandler.sendCurrency(session, a, p);
            return;
        }
        if (a.balance < pk.costXu()) {
            session.send(new Messages.ChatBroadcast(Messages.Channel.SYSTEM, 0, "Nạp game",
                    "Không đủ Xu để đổi gói \"" + pk.name() + "\". Cần "
                            + pk.costXu() + " Xu.").toPacket());
            ConvertHandler.sendCurrency(session, a, p);
            return;
        }

        // Spend Xu, grant coin.
        a.balance -= pk.costXu();
        a.coin += pk.coin();
        p.addCoin(pk.coin());

        // Grant bonus items into the bag.
        StringBuilder got = new StringBuilder();
        for (RedeemBonus b : pk.bonus()) {
            int count = Math.max(1, b.count());
            if (p.inventory().add(b.itemId(), count) < 0) {
                continue; // bag full — skip this bundle
            }
            ItemDef def = data.item(b.itemId());
            if (got.length() > 0) {
                got.append(", ");
            }
            got.append(def != null ? def.name() : ("#" + b.itemId())).append(" x").append(count);
        }

        ctx.accounts().save();

        session.send(new BagSnapshot(p.gold(), p.inventory().bagStacks(),
                p.inventory().equipmentStacks()).toPacket());
        ConvertHandler.sendCurrency(session, a, p);
        String detail = "Đổi gói \"" + pk.name() + "\": +" + pk.coin() + " Tiền nạp"
                + (got.length() > 0 ? ", nhận " + got : "") + ".";
        session.send(new Messages.ChatBroadcast(Messages.Channel.SYSTEM, 0, "Nạp game",
                detail).toPacket());
    }

    private static String bonusText(RedeemPack pk, GameData data) {
        StringBuilder sb = new StringBuilder();
        for (RedeemBonus b : pk.bonus()) {
            ItemDef def = data.item(b.itemId());
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(def != null ? def.name() : ("#" + b.itemId()))
              .append(" x").append(Math.max(1, b.count()));
        }
        return sb.toString();
    }
}
