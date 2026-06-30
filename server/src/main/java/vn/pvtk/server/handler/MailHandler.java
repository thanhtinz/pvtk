package vn.pvtk.server.handler;

import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.BagSnapshot;
import vn.pvtk.protocol.message.Messages.MailClaim;
import vn.pvtk.protocol.message.Messages.MailEntry;
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
                // Optionally escrow an attached item from the sender's bag.
                int itemId = 0;
                int itemCount = 0;
                if (m.itemCount() > 0) {
                    // m.itemId() doubles as the bag slot for the attachment here (slot 0 is valid).
                    int slot = m.itemId();
                    int bagItem = p.inventory().itemAt(slot);
                    if (bagItem > 0) {
                        int removed = p.inventory().remove(slot, m.itemCount());
                        if (removed > 0) {
                            itemId = bagItem;
                            itemCount = removed;
                        }
                    }
                }
                mail.send(p.name(), m.toName(), m.subject(), m.body(), gold, itemId, itemCount);
                session.send(new BagSnapshot(p.gold(), p.inventory().bagStacks(),
                        p.inventory().equipmentStacks()).toPacket());
                session.send(new MailList(mail.inbox(p.name())).toPacket());
                PlayerSession to = findByName(ctx, m.toName());
                if (to != null && to.player() != null) {
                    to.send(new MailList(mail.inbox(to.player().name())).toPacket());
                }
            }
            case Opcodes.MAIL_LIST ->
                session.send(new MailList(mail.inbox(p.name())).toPacket());
            case Opcodes.MAIL_CLAIM -> {
                int mailId = MailClaim.from(packet).mailId();
                MailEntry claimed = mail.claim(p.name(), mailId);
                if (claimed != null) {
                    if (claimed.gold() > 0) {
                        p.addGold(claimed.gold());
                    }
                    if (claimed.itemId() > 0 && claimed.itemCount() > 0) {
                        p.inventory().add(claimed.itemId(), claimed.itemCount());
                    }
                    session.send(new BagSnapshot(p.gold(), p.inventory().bagStacks(),
                            p.inventory().equipmentStacks()).toPacket());
                }
                session.send(new MailList(mail.inbox(p.name())).toPacket());
            }
            default -> { }
        }
    }

    private PlayerSession findByName(GameContext ctx, String name) {
        return ctx.sessions().all().stream()
                .filter(s -> s.player() != null && s.player().name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }
}
