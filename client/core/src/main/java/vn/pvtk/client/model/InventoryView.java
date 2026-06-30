package vn.pvtk.client.model;

import java.util.List;
import vn.pvtk.protocol.message.Messages.BagSnapshot;
import vn.pvtk.protocol.message.Messages.ItemStack;

/** Client-side view of the player's bag and equipment, updated from snapshots. */
public final class InventoryView {

    private volatile int gold;
    private volatile List<ItemStack> bag = List.of();
    private volatile List<ItemStack> equipment = List.of();

    public void apply(BagSnapshot snap) {
        this.gold = snap.gold();
        this.bag = List.copyOf(snap.bag());
        this.equipment = List.copyOf(snap.equipment());
    }

    public int gold() {
        return gold;
    }

    public List<ItemStack> bag() {
        return bag;
    }

    public List<ItemStack> equipment() {
        return equipment;
    }
}
