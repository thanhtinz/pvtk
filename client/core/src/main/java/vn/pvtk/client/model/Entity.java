package vn.pvtk.client.model;

import vn.pvtk.protocol.message.Messages.EntityState;

/** Client-side mutable view of a player/NPC entity. */
public final class Entity {

    public final int id;
    public String name;
    public int mapId;
    public int x;
    public int y;
    public int dir;
    public int hp;
    public int maxHp;
    public int level;
    public int kind;

    public Entity(EntityState s) {
        this.id = s.id();
        apply(s);
    }

    public void apply(EntityState s) {
        this.name = s.name();
        this.mapId = s.mapId();
        this.x = s.x();
        this.y = s.y();
        this.dir = s.dir();
        this.hp = s.hp();
        this.maxHp = s.maxHp();
        this.level = s.level();
        this.kind = s.kind();
    }

    public boolean isMonster() {
        return kind == vn.pvtk.protocol.message.Messages.KIND_MONSTER;
    }

    public void moveTo(int x, int y, int dir) {
        this.x = x;
        this.y = y;
        this.dir = dir;
    }

    @Override
    public String toString() {
        return name + "#" + id + " @(" + x + "," + y + ") map " + mapId;
    }
}
