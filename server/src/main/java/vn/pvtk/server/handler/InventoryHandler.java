package vn.pvtk.server.handler;

import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.BagAction;
import vn.pvtk.protocol.message.Messages.BagSnapshot;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Inventory;
import vn.pvtk.server.world.Player;

/** Handles inventory actions: list, equip, unequip — always replies with a fresh snapshot. */
public final class InventoryHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        BagAction action = BagAction.from(packet);
        Player p = session.player();
        Inventory inv = p.inventory();

        switch (action.kind()) {
            case BagAction.EQUIP -> inv.equip(action.slot());
            case BagAction.UNEQUIP -> inv.unequip(action.slot());
            default -> { /* LIST: just resend below */ }
        }
        session.send(new BagSnapshot(p.gold(), inv.bagStacks(), inv.equipmentStacks()).toPacket());

        // Equipping changes derived stats; broadcast the player's refreshed state.
        if (action.kind() == BagAction.EQUIP || action.kind() == BagAction.UNEQUIP) {
            ctx.world().broadcastToMap(ctx.world().map(p.mapId()),
                    new vn.pvtk.protocol.message.Messages.Spawn(p.toState()).toPacket(), -1);
        }
    }
}
