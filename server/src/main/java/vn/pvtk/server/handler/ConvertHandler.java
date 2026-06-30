package vn.pvtk.server.handler;

import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.ConvertXu;
import vn.pvtk.protocol.message.Messages.CurrencyInfo;
import vn.pvtk.server.account.Account;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/**
 * Converts web wallet "Xu" (top-ups) into in-game cash "Tiền nạp" (coin) from
 * inside the game. Rate is 1 Xu = {@link #XU_TO_COIN} coin.
 */
public final class ConvertHandler implements PacketHandler {

    public static final int XU_TO_COIN = 1;

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        long want = ConvertXu.from(packet).amount();
        Account a = session.account();
        Player p = session.player();
        if (a == null || want <= 0) {
            sendCurrency(session, a, p);
            return;
        }
        long amount = Math.min(want, a.balance);
        if (amount > 0) {
            a.balance -= amount;
            long coin = amount * XU_TO_COIN;
            a.coin += coin;
            p.addCoin(coin);
            ctx.accounts().save();
        }
        sendCurrency(session, a, p);
    }

    /** Sends the player's current gold / coin / xu to the client. */
    public static void sendCurrency(PlayerSession session, Account a, Player p) {
        long xu = a != null ? a.balance : 0;
        session.send(new CurrencyInfo(p != null ? p.gold() : 0,
                p != null ? p.coin() : 0, xu).toPacket());
    }
}
