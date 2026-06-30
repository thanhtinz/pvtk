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
    private final int kind;

    private int mapId;
    private int x;
    private int y;
    private int hp;
    private int maxHp;

    public Pet(int ownerId, String name, int atkBonus, int mapId, int x, int y) {
        this(ownerId, name, atkBonus, mapId, x, y, Messages.KIND_PET, 0);
    }

    public Pet(int ownerId, String name, int atkBonus, int mapId, int x, int y, int kind, int hp) {
        this.id = IDS.getAndIncrement();
        this.ownerId = ownerId;
        this.name = name;
        this.atkBonus = atkBonus;
        this.mapId = mapId;
        this.x = x;
        this.y = y;
        this.kind = kind;
        this.hp = hp;
        this.maxHp = hp;
    }

    public int hp() {
        return hp;
    }

    public boolean isDestructible() {
        return maxHp > 0;
    }

    /** Damages a destructible follower (e.g. a caravan). Returns true if destroyed. */
    public boolean damage(int amount) {
        if (maxHp <= 0) {
            return false;
        }
        hp = Math.max(0, hp - Math.max(1, amount));
        return hp == 0;
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
        // Pets surface their bonus as "level"; destructible followers show real HP.
        int shownHp = maxHp > 0 ? hp : 1;
        int shownMax = maxHp > 0 ? maxHp : 1;
        return new EntityState(id, name, mapId, x, y, 0, shownHp, shownMax, atkBonus, kind);
    }
}
