package vn.pvtk.server.world;

import java.util.concurrent.atomic.AtomicInteger;
import vn.pvtk.protocol.message.Messages.EntityState;

/** An authoritative player entity living in the server world. */
public final class Player {

    private static final AtomicInteger IDS = new AtomicInteger(1000);

    private final int id;
    private final String name;

    private volatile int mapId;
    private volatile int x;
    private volatile int y;
    private volatile int dir;
    private volatile int hp;
    private volatile int maxHp;
    private volatile int level;

    public Player(String name, int mapId, int x, int y) {
        this.id = IDS.getAndIncrement();
        this.name = name;
        this.mapId = mapId;
        this.x = x;
        this.y = y;
        this.dir = 0;
        this.maxHp = 1000;
        this.hp = 1000;
        this.level = 1;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int mapId() {
        return mapId;
    }

    public void mapId(int mapId) {
        this.mapId = mapId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int dir() {
        return dir;
    }

    public int level() {
        return level;
    }

    public int hp() {
        return hp;
    }

    public int maxHp() {
        return maxHp;
    }

    public void moveTo(int x, int y, int dir) {
        this.x = x;
        this.y = y;
        this.dir = dir;
    }

    public EntityState toState() {
        return new EntityState(id, name, mapId, x, y, dir, hp, maxHp, level);
    }
}
