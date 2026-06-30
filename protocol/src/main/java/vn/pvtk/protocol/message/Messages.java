package vn.pvtk.protocol.message;

import java.util.ArrayList;
import java.util.List;
import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;

/**
 * Typed bodies for the core gameplay messages implemented by the rewritten
 * server and clients.
 *
 * <p>The original protocol carries hundreds of opcodes (see {@link Opcodes});
 * this rewrite implements an authoritative, server-driven subset covering the
 * essential multiplayer loop &mdash; login, the world snapshot, movement,
 * entity spawn/despawn and chat &mdash; on top of the faithful {@link Packet}
 * wire codec. Each record knows how to {@code encode} itself into a packet and
 * {@code decode} itself back, so the server and every client share one source
 * of truth for the byte layout.
 */
public final class Messages {

    private Messages() {
    }

    /** Entity kind: distinguishes players from monsters/NPCs on the wire. */
    public static final int KIND_PLAYER = 0;
    public static final int KIND_MONSTER = 1;

    /** A snapshot of one entity (player or NPC) as seen on the wire. */
    public record EntityState(
            int id, String name, int mapId,
            int x, int y, int dir,
            int hp, int maxHp, int level, int kind) {

        /** Convenience constructor for a player entity. */
        public EntityState(int id, String name, int mapId, int x, int y, int dir,
                           int hp, int maxHp, int level) {
            this(id, name, mapId, x, y, dir, hp, maxHp, level, KIND_PLAYER);
        }

        public boolean isMonster() {
            return kind == KIND_MONSTER;
        }

        public void write(Packet p) {
            p.putInt(id).putString(name).putShort(mapId)
                    .putShort(x).putShort(y).putByte(dir)
                    .putInt(hp).putInt(maxHp).putShort(level).putByte(kind);
        }

        public static EntityState read(Packet p) {
            return new EntityState(
                    p.getInt(), p.getString(), p.getUShort(),
                    p.getUShort(), p.getUShort(), p.getUByte(),
                    p.getInt(), p.getInt(), p.getUShort(), p.getUByte());
        }
    }

    // ------------------------------------------------------------------
    // Login (opcode LOGIN = 10003)
    // ------------------------------------------------------------------

    public record LoginRequest(String username, String password, int serverLine) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.LOGIN);
            p.putByte(0); // sub-type 0 = login request
            p.putString(username).putString(password).putShort(serverLine);
            return p;
        }

        public static LoginRequest from(Packet p) {
            p.getByte(); // sub-type
            return new LoginRequest(p.getString(), p.getString(), p.getUShort());
        }
    }

    public record LoginResponse(boolean ok, String message, EntityState self) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.LOGIN);
            p.putByte(1); // sub-type 1 = login response
            p.putBool(ok).putString(message == null ? "" : message);
            p.putBool(self != null);
            if (self != null) {
                self.write(p);
            }
            return p;
        }

        public static LoginResponse from(Packet p) {
            p.getByte();
            boolean ok = p.getBool();
            String msg = p.getString();
            EntityState self = p.getBool() ? EntityState.read(p) : null;
            return new LoginResponse(ok, msg, self);
        }
    }

    // ------------------------------------------------------------------
    // World snapshot (opcode WORLD_DATA = 10503)
    // ------------------------------------------------------------------

    public record WorldSnapshot(int mapId, List<EntityState> entities) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.WORLD_DATA);
            p.putShort(mapId).putShort(entities.size());
            for (EntityState e : entities) {
                e.write(p);
            }
            return p;
        }

        public static WorldSnapshot from(Packet p) {
            int mapId = p.getUShort();
            int n = p.getUShort();
            List<EntityState> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(EntityState.read(p));
            }
            return new WorldSnapshot(mapId, list);
        }
    }

    // ------------------------------------------------------------------
    // Movement (opcode AUTO_MOVE = 10518)
    // ------------------------------------------------------------------

    /** Client&rarr;server movement intent (target tile). */
    public record MoveRequest(int x, int y, int dir) {
        public Packet toPacket() {
            return new Packet(Opcodes.AUTO_MOVE).putShort(x).putShort(y).putByte(dir);
        }

        public static MoveRequest from(Packet p) {
            return new MoveRequest(p.getUShort(), p.getUShort(), p.getUByte());
        }
    }

    /** Server&rarr;client authoritative position update for an entity. */
    public record MoveUpdate(int entityId, int x, int y, int dir) {
        public Packet toPacket() {
            // sub-type byte 1 distinguishes a broadcast update from a request.
            return new Packet(Opcodes.AUTO_MOVE).putByte(1)
                    .putInt(entityId).putShort(x).putShort(y).putByte(dir);
        }

        public static MoveUpdate from(Packet p) {
            p.getByte();
            return new MoveUpdate(p.getInt(), p.getUShort(), p.getUShort(), p.getUByte());
        }
    }

    // ------------------------------------------------------------------
    // Spawn / despawn (opcode GET_SPRITE = 10520)
    // ------------------------------------------------------------------

    public record Spawn(EntityState entity) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.GET_SPRITE).putByte(1); // 1 = spawn
            entity.write(p);
            return p;
        }

        public static Spawn from(Packet p) {
            p.getByte();
            return new Spawn(EntityState.read(p));
        }
    }

    public record Despawn(int entityId) {
        public Packet toPacket() {
            return new Packet(Opcodes.GET_SPRITE).putByte(0).putInt(entityId); // 0 = despawn
        }

        public static Despawn from(Packet p) {
            p.getByte();
            return new Despawn(p.getInt());
        }
    }

    /** Peeks the spawn/despawn sub-type without consuming the rest of the packet. */
    public static boolean isSpawn(Packet p) {
        p.rewind();
        boolean spawn = p.getByte() == 1;
        p.rewind();
        return spawn;
    }

    // ------------------------------------------------------------------
    // Chat (opcode CHAT = 13509)
    // ------------------------------------------------------------------

    public enum Channel { WORLD, MAP, TEAM, COUNTRY, PRIVATE, SYSTEM }

    public record ChatRequest(Channel channel, String target, String text) {
        public Packet toPacket() {
            return new Packet(Opcodes.CHAT).putByte(channel.ordinal())
                    .putString(target == null ? "" : target).putString(text);
        }

        public static ChatRequest from(Packet p) {
            Channel ch = Channel.values()[p.getUByte() % Channel.values().length];
            return new ChatRequest(ch, p.getString(), p.getString());
        }
    }

    public record ChatBroadcast(Channel channel, int fromId, String fromName, String text) {
        public Packet toPacket() {
            return new Packet(Opcodes.CHAT).putByte(channel.ordinal())
                    .putInt(fromId).putString(fromName).putString(text);
        }

        public static ChatBroadcast from(Packet p) {
            Channel ch = Channel.values()[p.getUByte() % Channel.values().length];
            return new ChatBroadcast(ch, p.getInt(), p.getString(), p.getString());
        }
    }

    // ==================================================================
    // Inventory (opcode BAG = 12001)
    // ==================================================================

    /** One stack in a bag/equipment slot. {@code itemId <= 0} means empty. */
    public record ItemStack(int slot, int itemId, String name, int count, int type, int icon) {
        public void write(Packet p) {
            p.putShort(slot).putInt(itemId).putString(name).putShort(count).putByte(type).putInt(icon);
        }

        public static ItemStack read(Packet p) {
            return new ItemStack(p.getUShort(), p.getInt(), p.getString(),
                    p.getUShort(), p.getUByte(), p.getInt());
        }
    }

    /** Client→server inventory action. */
    public record BagAction(int kind, int slot, int arg) {
        public static final int LIST = 0;
        public static final int EQUIP = 1;
        public static final int UNEQUIP = 2;

        public Packet toPacket() {
            return new Packet(Opcodes.BAG).putByte(kind).putShort(slot).putShort(arg);
        }

        public static BagAction from(Packet p) {
            return new BagAction(p.getUByte(), p.getUShort(), p.getUShort());
        }
    }

    /** Server→client full inventory + equipment snapshot. */
    public record BagSnapshot(int gold, List<ItemStack> bag, List<ItemStack> equipment) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.BAG).putByte(9); // sub-type 9 = snapshot
            p.putInt(gold);
            p.putShort(bag.size());
            for (ItemStack s : bag) {
                s.write(p);
            }
            p.putShort(equipment.size());
            for (ItemStack s : equipment) {
                s.write(p);
            }
            return p;
        }

        public static BagSnapshot from(Packet p) {
            p.getByte(); // sub-type
            int gold = p.getInt();
            List<ItemStack> bag = readList(p);
            List<ItemStack> equip = readList(p);
            return new BagSnapshot(gold, bag, equip);
        }

        private static List<ItemStack> readList(Packet p) {
            int n = p.getUShort();
            List<ItemStack> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(ItemStack.read(p));
            }
            return list;
        }
    }

    /** Distinguishes a BAG snapshot (sub-type 9) from a client action. */
    public static boolean isBagSnapshot(Packet p) {
        p.rewind();
        boolean snap = p.getUByte() == 9;
        p.rewind();
        return snap;
    }

    // ==================================================================
    // Combat (ATTACK = 12505 request, COMBAT_EVENT = 12506 event)
    // ==================================================================

    public record AttackRequest(int targetId, int skillId) {
        public Packet toPacket() {
            return new Packet(Opcodes.ATTACK).putInt(targetId).putInt(skillId);
        }

        public static AttackRequest from(Packet p) {
            return new AttackRequest(p.getInt(), p.getInt());
        }
    }

    /** Broadcast result of a hit: damage dealt, target's remaining HP, and death flag. */
    public record CombatEvent(int attackerId, int targetId, int damage, int targetHp, boolean killed) {
        public Packet toPacket() {
            return new Packet(Opcodes.COMBAT_EVENT)
                    .putInt(attackerId).putInt(targetId).putInt(damage)
                    .putInt(targetHp).putBool(killed);
        }

        public static CombatEvent from(Packet p) {
            return new CombatEvent(p.getInt(), p.getInt(), p.getInt(), p.getInt(), p.getBool());
        }
    }

    // ==================================================================
    // Country / guild (15001 create, 15002 info, 15004 list, 15011 join, 15015 leave)
    // ==================================================================

    public record CountryInfo(int id, String name, String kingName, int memberCount, int level) {
        public void write(Packet p) {
            p.putInt(id).putString(name).putString(kingName).putShort(memberCount).putShort(level);
        }

        public static CountryInfo read(Packet p) {
            return new CountryInfo(p.getInt(), p.getString(), p.getString(), p.getUShort(), p.getUShort());
        }
    }

    public record CountryCreate(String name) {
        public Packet toPacket() {
            return new Packet(Opcodes.COUNTRY_CREATE).putString(name);
        }

        public static CountryCreate from(Packet p) {
            return new CountryCreate(p.getString());
        }
    }

    public record CountryActionResult(boolean ok, String message, CountryInfo country) {
        public Packet toPacket(int opcode) {
            Packet p = new Packet(opcode).putBool(ok).putString(message == null ? "" : message);
            p.putBool(country != null);
            if (country != null) {
                country.write(p);
            }
            return p;
        }

        public static CountryActionResult from(Packet p) {
            boolean ok = p.getBool();
            String msg = p.getString();
            CountryInfo c = p.getBool() ? CountryInfo.read(p) : null;
            return new CountryActionResult(ok, msg, c);
        }
    }

    public record CountryList(List<CountryInfo> countries) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.COUNTRY_LIST).putShort(countries.size());
            for (CountryInfo c : countries) {
                c.write(p);
            }
            return p;
        }

        public static CountryList from(Packet p) {
            int n = p.getUShort();
            List<CountryInfo> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(CountryInfo.read(p));
            }
            return new CountryList(list);
        }
    }

    public record CountryJoin(int countryId) {
        public Packet toPacket() {
            return new Packet(Opcodes.COUNTRY_JOIN).putInt(countryId);
        }

        public static CountryJoin from(Packet p) {
            return new CountryJoin(p.getInt());
        }
    }

    // ==================================================================
    // Map travel (JUMP_MAP = 10506)
    // ==================================================================

    public record JumpMap(int mapId) {
        public Packet toPacket() {
            return new Packet(Opcodes.JUMP_MAP).putShort(mapId);
        }

        public static JumpMap from(Packet p) {
            return new JumpMap(p.getUShort());
        }
    }
}
