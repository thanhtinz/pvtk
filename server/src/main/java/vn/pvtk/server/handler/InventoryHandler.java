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

        boolean used = false;
        switch (action.kind()) {
            case BagAction.EQUIP -> inv.equip(action.slot());
            case BagAction.UNEQUIP -> inv.unequip(action.slot());
            case BagAction.USE -> used = useConsumable(session, ctx, action.slot());
            default -> { /* LIST: just resend below */ }
        }
        session.send(new BagSnapshot(p.gold(), inv.bagStacks(), inv.equipmentStacks()).toPacket());

        // Equipping/using changes derived stats or HP/MP; refresh the player's state.
        if (action.kind() == BagAction.EQUIP || action.kind() == BagAction.UNEQUIP || used) {
            ctx.world().broadcastToMap(ctx.world().map(p.mapId()),
                    new vn.pvtk.protocol.message.Messages.Spawn(p.toState()).toPacket(), -1);
        }
    }

    /**
     * Consumes the item in a bag slot if it is a restore potion. Faithful to the
     * original vitality herb (power 52 = restore HP%, power 50 = restore MP%),
     * usable only outside of a turn-based battle ("非战斗时" in the item info).
     * Returns true if the item was used.
     */
    private boolean useConsumable(PlayerSession session, GameContext ctx, int slot) {
        Player p = session.player();
        Inventory inv = p.inventory();
        int itemId = inv.itemAt(slot);
        if (itemId <= 0) {
            return false;
        }
        var def = ctx.world().data().item(itemId);
        if (def == null || !def.isConsumable() || p.inBattle()) {
            return false;
        }
        int hpGain = p.maxHp() * def.hpRestorePercent() / 100;
        int mpGain = p.maxMp() * def.mpRestorePercent() / 100;
        if (!p.heal(hpGain, mpGain)) {
            return false; // already at full — don't waste the item
        }
        inv.remove(slot, 1);
        session.send(new vn.pvtk.protocol.message.Messages.ChatBroadcast(
                vn.pvtk.protocol.message.Messages.Channel.SYSTEM, 0, "Vật phẩm",
                "Dùng " + def.name() + " (+" + hpGain + " HP, +" + mpGain + " MP)").toPacket());
        return true;
    }
}
