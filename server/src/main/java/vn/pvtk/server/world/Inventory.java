package vn.pvtk.server.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.pvtk.protocol.message.Messages.ItemStack;
import vn.pvtk.server.data.ItemDef;
import vn.pvtk.server.data.GameData;

/**
 * A player's bag and equipment. Bag is a fixed set of slots holding item ids +
 * counts; equipment is keyed by gear slot (the item's {@code type}). Combat
 * derives attack/defence bonuses from equipped gear.
 */
public final class Inventory {

    public static final int BAG_SIZE = 30;

    private final GameData data;
    private final int[] bagItem = new int[BAG_SIZE];
    private final int[] bagCount = new int[BAG_SIZE];
    private final Map<Integer, Integer> equipment = new LinkedHashMap<>(); // type -> itemId

    public Inventory(GameData data) {
        this.data = data;
    }

    /** Adds an item to the first free / stackable slot. Returns the slot or -1 if full. */
    public int add(int itemId, int count) {
        ItemDef def = data.item(itemId);
        if (def == null) {
            return -1;
        }
        if (def.isStackable()) {
            for (int i = 0; i < BAG_SIZE; i++) {
                if (bagItem[i] == itemId) {
                    bagCount[i] += count;
                    return i;
                }
            }
        }
        for (int i = 0; i < BAG_SIZE; i++) {
            if (bagItem[i] == 0) {
                bagItem[i] = itemId;
                bagCount[i] = count;
                return i;
            }
        }
        return -1;
    }

    /** Equips the item in bag slot {@code slot}; swaps with whatever is in that gear slot. */
    public boolean equip(int slot) {
        if (slot < 0 || slot >= BAG_SIZE || bagItem[slot] == 0) {
            return false;
        }
        ItemDef def = data.item(bagItem[slot]);
        if (def == null || !def.isEquippable()) {
            return false;
        }
        int gearType = def.type();
        Integer previous = equipment.put(gearType, def.id());
        bagItem[slot] = previous != null ? previous : 0;
        bagCount[slot] = previous != null ? 1 : 0;
        return true;
    }

    /** Unequips the gear of the given slot/type back into the bag. */
    public boolean unequip(int gearType) {
        Integer itemId = equipment.remove(gearType);
        if (itemId == null) {
            return false;
        }
        return add(itemId, 1) >= 0;
    }

    public int attackBonus() {
        int atk = 0;
        for (int itemId : equipment.values()) {
            ItemDef d = data.item(itemId);
            if (d != null) {
                atk += (d.atkMin() + d.atkMax()) / 2;
            }
        }
        return atk;
    }

    public int defenseBonus() {
        int def = 0;
        for (int itemId : equipment.values()) {
            ItemDef d = data.item(itemId);
            if (d != null) {
                def += d.defStr() + d.defAgi() + d.defMag();
            }
        }
        return def;
    }

    public List<ItemStack> bagStacks() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < BAG_SIZE; i++) {
            if (bagItem[i] != 0) {
                ItemDef d = data.item(bagItem[i]);
                list.add(new ItemStack(i, bagItem[i],
                        d != null ? d.name() : "?", bagCount[i],
                        d != null ? d.type() : 0, d != null ? d.icon() : 0));
            }
        }
        return list;
    }

    public List<ItemStack> equipmentStacks() {
        List<ItemStack> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : equipment.entrySet()) {
            ItemDef d = data.item(e.getValue());
            list.add(new ItemStack(e.getKey(), e.getValue(),
                    d != null ? d.name() : "?", 1,
                    d != null ? d.type() : 0, d != null ? d.icon() : 0));
        }
        return list;
    }
}
