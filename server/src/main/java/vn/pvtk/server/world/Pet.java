package vn.pvtk.server.world;

import java.util.concurrent.atomic.AtomicInteger;
import vn.pvtk.protocol.message.Messages;
import vn.pvtk.protocol.message.Messages.EntityState;

/** A companion that follows its owner and auto-attacks nearby monsters. */
public final class Pet {

    private static final AtomicInteger IDS = new AtomicInteger(900_000);

    private final int id;
    private final int ownerId;
    private final String name;
    private final int atkBonus;

    private int mapId;
    private int x;
    private int y;

    public Pet(int ownerId, String name, int atkBonus, int mapId, int x, int y) {
        this.id = IDS.getAndIncrement();
        this.ownerId = ownerId;
        this.name = name;
        this.atkBonus = atkBonus;
        this.mapId = mapId;
        this.x = x;
        this.y = y;
    }

    public int id() {
        return id;
    }

    public int ownerId() {
        return ownerId;
    }

    public int atkBonus() {
        return atkBonus;
    }

    public int mapId() {
        return mapId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public void place(int mapId, int x, int y) {
        this.mapId = mapId;
        this.x = x;
        this.y = y;
    }

    public EntityState toState() {
        // Pets have no HP bar of their own; surface the bonus as the "level" field.
        return new EntityState(id, name, mapId, x, y, 0, 1, 1, atkBonus, Messages.KIND_PET);
    }
}
