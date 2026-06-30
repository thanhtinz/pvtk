package vn.pvtk.server.handler;

import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.MailList;
import vn.pvtk.protocol.message.Messages.MailSend;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.MailRegistry;
import vn.pvtk.server.world.Player;

/** Mail: send a message (optionally with gold) and list the mailbox. */
public final class MailHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        MailRegistry mail = ctx.world().mail();
        Player p = session.player();

        switch (op) {
            case Opcodes.MAIL_SEND -> {
                MailSend m = MailSend.from(packet);
                int gold = Math.max(0, Math.min(m.gold(), p.gold()));
                p.addGold(-gold); // escrow the attached gold from the sender
                mail.send(p.name(), m.toName(), m.subject(), m.body(), gold);
                // Reflect the sender's new gold and refresh their own mailbox view.
                session.send(new MailList(mail.inbox(p.name())).toPacket());
                // Deliver live if the recipient is online.
                PlayerSession to = findByName(ctx, m.toName());
                if (to != null && to.player() != null) {
                    to.send(new MailList(mail.inbox(to.player().name())).toPacket());
                }
            }
            case Opcodes.MAIL_LIST ->
                session.send(new MailList(mail.inbox(p.name())).toPacket());
            default -> { }
        }
    }

    private PlayerSession findByName(GameContext ctx, String name) {
        return ctx.sessions().all().stream()
                .filter(s -> s.player() != null && s.player().name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }
}
