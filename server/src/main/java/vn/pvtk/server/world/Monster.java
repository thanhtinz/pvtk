package vn.pvtk.server.world;

import java.util.concurrent.atomic.AtomicInteger;
import vn.pvtk.protocol.message.Messages;
import vn.pvtk.protocol.message.Messages.EntityState;
import vn.pvtk.server.data.MonsterDef;

/** A live monster instance in a map, spawned from a {@link MonsterDef}. */
public final class Monster {

    private static final AtomicInteger IDS = new AtomicInteger(500_000);

    private final int id;
    private final MonsterDef def;
    private final int spawnX;
    private final int spawnY;

    private int x;
    private int y;
    private int hp;
    private volatile boolean dead;
    private volatile boolean locked; // engaged in a turn-based battle
    private long deadAtMs;

    public Monster(MonsterDef def, int x, int y) {
        this.id = IDS.getAndIncrement();
        this.def = def;
        this.spawnX = x;
        this.spawnY = y;
        this.x = x;
        this.y = y;
        this.hp = def.hpMax();
    }

    public int id() {
        return id;
    }

    public MonsterDef def() {
        return def;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int hp() {
        return hp;
    }

    public boolean isDead() {
        return dead;
    }

    public boolean isLocked() {
        return locked;
    }

    public void locked(boolean locked) {
        this.locked = locked;
    }

    /** Marks the monster dead immediately (e.g. defeated in a turn battle). */
    public void kill(long nowMs) {
        this.hp = 0;
        this.dead = true;
        this.locked = false;
        this.deadAtMs = nowMs;
    }

    public long deadAtMs() {
        return deadAtMs;
    }

    /** Applies damage; returns true if this hit killed the monster. */
    public boolean damage(int amount, long nowMs) {
        if (dead) {
            return false;
        }
        hp = Math.max(0, hp - Math.max(1, amount));
        if (hp == 0) {
            dead = true;
            deadAtMs = nowMs;
            return true;
        }
        return false;
    }

    /** Respawns at full HP at the spawn point. */
    public void respawn() {
        hp = def.hpMax();
        x = spawnX;
        y = spawnY;
        dead = false;
    }

    public EntityState toState(int mapId) {
        return new EntityState(id, def.name(), mapId, x, y, 0,
                hp, def.hpMax(), def.level(), Messages.KIND_MONSTER);
    }
}
