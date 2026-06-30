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

    /** A snapshot of one entity (player or NPC) as seen on the wire. */
    public record EntityState(
            int id, String name, int mapId,
            int x, int y, int dir,
            int hp, int maxHp, int level) {

        public void write(Packet p) {
            p.putInt(id).putString(name).putShort(mapId)
                    .putShort(x).putShort(y).putByte(dir)
                    .putInt(hp).putInt(maxHp).putShort(level);
        }

        public static EntityState read(Packet p) {
            return new EntityState(
                    p.getInt(), p.getString(), p.getUShort(),
                    p.getUShort(), p.getUShort(), p.getUByte(),
                    p.getInt(), p.getInt(), p.getUShort());
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
}
