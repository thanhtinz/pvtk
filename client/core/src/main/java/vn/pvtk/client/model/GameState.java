package vn.pvtk.client.model;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import vn.pvtk.protocol.message.Messages.EntityState;

/**
 * The observable client-side snapshot of the world: the local player plus every
 * other entity currently visible. Updated by {@link vn.pvtk.client.GameClient}
 * from the network thread; rendering reads it on the main/render thread.
 */
public final class GameState {

    private volatile Entity self;
    private final Map<Integer, Entity> others = new ConcurrentHashMap<>();
    private final InventoryView inventory = new InventoryView();

    public InventoryView inventory() {
        return inventory;
    }

    public Entity self() {
        return self;
    }

    public void setSelf(EntityState s) {
        this.self = new Entity(s);
    }

    public Collection<Entity> others() {
        return others.values();
    }

    public Entity get(int id) {
        if (self != null && self.id == id) {
            return self;
        }
        return others.get(id);
    }

    public void upsert(EntityState s) {
        if (self != null && s.id() == self.id) {
            self.apply(s);
            return;
        }
        others.computeIfAbsent(s.id(), id -> new Entity(s)).apply(s);
    }

    public void remove(int id) {
        others.remove(id);
    }

    public void clearOthers() {
        others.clear();
    }

    public int visibleCount() {
        return others.size() + (self != null ? 1 : 0);
    }
}
