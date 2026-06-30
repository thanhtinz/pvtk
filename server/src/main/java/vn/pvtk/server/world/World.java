package vn.pvtk.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages;
import vn.pvtk.protocol.message.Messages.CombatEvent;
import vn.pvtk.protocol.message.Messages.EntityState;
import vn.pvtk.protocol.message.Messages.MoveUpdate;
import vn.pvtk.server.data.GameData;
import vn.pvtk.server.data.MonsterDef;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.session.SessionManager;

/**
 * The authoritative game world: owns the map instances and provides the
 * area-of-interest broadcast primitives that drive multiplayer. All gameplay
 * mutations funnel through here so state stays consistent.
 */
public final class World {

    private static final Logger log = LoggerFactory.getLogger(World.class);

    /** How long a slain monster stays dead before respawning. */
    private static final long RESPAWN_MS = 8_000L;

    private final SessionManager sessions;
    private final GameData data;
    private final CountryRegistry countries = new CountryRegistry();
    private final TeamRegistry teams = new TeamRegistry();
    private final MailRegistry mail = new MailRegistry();
    private final Map<Integer, MapInstance> maps = new ConcurrentHashMap<>();
    // monsters indexed globally by id and per-map for AOI.
    private final Map<Integer, Monster> monstersById = new ConcurrentHashMap<>();
    private final Map<Integer, List<Monster>> monstersByMap = new ConcurrentHashMap<>();

    public World(SessionManager sessions, GameData data) {
        this.sessions = sessions;
        this.data = data;
        // A small set of starter maps. In a full build these load from data files
        // converted from the original client's `map/` resources.
        register(new MapInstance(1, "Tân Thủ Thôn", 64, 64, 32, 32));
        register(new MapInstance(2, "Lạc Dương Thành", 96, 96, 48, 48));
        register(new MapInstance(3, "Hoang Dã", 128, 128, 20, 20));
        spawnInitialMonsters();
    }

    private void register(MapInstance map) {
        maps.put(map.mapId(), map);
        monstersByMap.put(map.mapId(), new ArrayList<>());
    }

    public CountryRegistry countries() {
        return countries;
    }

    public TeamRegistry teams() {
        return teams;
    }

    public MailRegistry mail() {
        return mail;
    }

    public GameData data() {
        return data;
    }

    /** Sends a packet to every online member of {@code teamId}. */
    public void broadcastToTeam(int teamId, Packet packet) {
        Team t = teams.get(teamId);
        if (t == null) {
            return;
        }
        for (int pid : t.members()) {
            sessions.sendTo(pid, packet);
        }
    }

    // ------------------------------------------------------------------
    // Monsters
    // ------------------------------------------------------------------

    private void spawnInitialMonsters() {
        List<MonsterDef> defs = data.monsterList();
        if (defs.isEmpty()) {
            return;
        }
        // Populate the wilderness map (3) with a handful of monsters from the DB.
        MapInstance wild = maps.get(3);
        int count = Math.min(12, defs.size());
        for (int i = 0; i < count; i++) {
            MonsterDef def = defs.get(i % defs.size());
            int x = 8 + (i * 7) % (wild.width() - 16);
            int y = 8 + (i * 11) % (wild.height() - 16);
            Monster m = new Monster(def, x, y);
            monstersById.put(m.id(), m);
            monstersByMap.get(wild.mapId()).add(m);
        }
        log.info("Spawned {} monsters into map {} [{}]", count, wild.mapId(), wild.name());
    }

    public Monster monster(int id) {
        return monstersById.get(id);
    }

    private List<Monster> monstersOnMap(int mapId) {
        return monstersByMap.getOrDefault(mapId, List.of());
    }

    /** Called periodically by the server tick: respawns monsters and regenerates MP. */
    public void tick(long nowMs) {
        for (Map.Entry<Integer, List<Monster>> e : monstersByMap.entrySet()) {
            MapInstance map = maps.get(e.getKey());
            for (Monster m : e.getValue()) {
                if (m.isDead() && nowMs - m.deadAtMs() >= RESPAWN_MS) {
                    m.respawn();
                    broadcastToMap(map, new Messages.Spawn(m.toState(map.mapId())).toPacket(), -1);
                }
            }
        }
        for (PlayerSession s : sessions.all()) {
            if (s.player() != null) {
                s.player().regenMp(5);
            }
        }
    }

    public MapInstance map(int mapId) {
        return maps.getOrDefault(mapId, maps.get(1));
    }

    public SessionManager sessions() {
        return sessions;
    }

    /** Places a freshly logged-in player into their map and notifies neighbours. */
    public void enter(PlayerSession session) {
        Player p = session.player();
        MapInstance map = map(p.mapId());
        map.addPlayer(p.id());
        // Tell everyone already in the map that a new entity spawned.
        broadcastToMap(map, new vn.pvtk.protocol.message.Messages.Spawn(p.toState()).toPacket(), p.id());
        log.info("Player {} (#{}) entered map {} [{}] ({} online)",
                p.name(), p.id(), map.mapId(), map.name(), sessions.onlineCount());
    }

    /** Removes a player from the world and notifies neighbours of the despawn. */
    public void leave(PlayerSession session) {
        Player p = session.player();
        if (p == null) {
            return;
        }
        MapInstance map = map(p.mapId());
        map.removePlayer(p.id());
        broadcastToMap(map, new vn.pvtk.protocol.message.Messages.Despawn(p.id()).toPacket(), p.id());
        log.info("Player {} (#{}) left map {} ({} online)",
                p.name(), p.id(), map.mapId(), sessions.onlineCount());
    }

    /** Builds the world snapshot a client receives right after login. */
    public List<EntityState> visibleEntities(Player viewer) {
        MapInstance map = map(viewer.mapId());
        List<EntityState> list = new ArrayList<>();
        for (int otherId : map.playerIds()) {
            if (otherId == viewer.id()) {
                continue;
            }
            PlayerSession s = sessions.byPlayerId(otherId);
            if (s != null && s.player() != null) {
                list.add(s.player().toState());
            }
        }
        for (Monster m : monstersOnMap(map.mapId())) {
            if (!m.isDead()) {
                list.add(m.toState(map.mapId()));
            }
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Combat
    // ------------------------------------------------------------------

    /**
     * Resolves an attack from {@code attacker} on {@code targetId} (a monster or
     * another player), applies authoritative damage, and broadcasts the result.
     * This is a deliberately simple real-time model layered on the original's
     * (turn-based) combat opcodes — see docs/ARCHITECTURE.md.
     */
    public void attack(PlayerSession session, int targetId, long nowMs) {
        attack(session, targetId, 0, nowMs);
    }

    public void attack(PlayerSession session, int targetId, int skillId, long nowMs) {
        Player attacker = session.player();
        MapInstance map = map(attacker.mapId());
        int atk = attacker.attackPower();

        // A known skill with enough MP adds bonus damage.
        if (skillId > 0 && attacker.knowsSkill(skillId)) {
            var skill = data.skill(skillId);
            if (skill != null && attacker.spendMp(skill.useMp())) {
                atk += skill.combatBonus();
            }
        }

        Monster monster = monstersById.get(targetId);
        if (monster != null && !monster.isDead()) {
            int dmg = Math.max(1, atk - 2);
            boolean killed = monster.damage(dmg, nowMs);
            broadcastToMap(map, new CombatEvent(attacker.id(), targetId, dmg, monster.hp(), killed).toPacket(), -1);
            if (killed) {
                attacker.addGold(monster.def().rewardMoney());
                boolean leveled = attacker.addExp(monster.def().rewardExp());
                // Reward & possible level-up are reflected in the attacker's own state.
                session.send(new Messages.Spawn(attacker.toState()).toPacket());
                if (leveled) {
                    log.info("{} reached level {}", attacker.name(), attacker.level());
                }
                broadcastToMap(map, new Messages.Despawn(targetId).toPacket(), -1);
            }
            return;
        }

        // PvP: target is another player on the same map.
        PlayerSession targetSession = sessions.byPlayerId(targetId);
        if (targetSession != null && targetSession.player() != null) {
            Player target = targetSession.player();
            if (target.mapId() != attacker.mapId()) {
                return;
            }
            int dmg = Math.max(1, atk - target.defense());
            target.damage(dmg);
            boolean killed = target.isDead();
            broadcastToMap(map, new CombatEvent(attacker.id(), targetId, dmg, target.hp(), killed).toPacket(), -1);
            if (killed) {
                target.revive();
                // Respawn the defeated player at the map's spawn point.
                target.moveTo(map.spawnX(), map.spawnY(), 0);
                broadcastToMap(map, new MoveUpdate(target.id(), target.x(), target.y(), 0).toPacket(), -1);
                targetSession.send(new Messages.Spawn(target.toState()).toPacket());
            }
        }
    }

    // ------------------------------------------------------------------
    // Country broadcast
    // ------------------------------------------------------------------

    /** Sends a packet to every online member of {@code countryId}. */
    public void broadcastToCountry(int countryId, Packet packet) {
        Country c = countries.get(countryId);
        if (c == null) {
            return;
        }
        for (int pid : c.members()) {
            sessions.sendTo(pid, packet);
        }
    }

    /** Moves a player to another map: despawn on the old, spawn on the new, fresh snapshot. */
    public void changeMap(PlayerSession session, int targetMapId) {
        Player p = session.player();
        MapInstance old = map(p.mapId());
        MapInstance dest = map(targetMapId);
        if (dest.mapId() == old.mapId()) {
            return;
        }
        old.removePlayer(p.id());
        broadcastToMap(old, new Messages.Despawn(p.id()).toPacket(), p.id());

        p.mapId(dest.mapId());
        p.moveTo(dest.spawnX(), dest.spawnY(), 0);
        dest.addPlayer(p.id());
        broadcastToMap(dest, new Messages.Spawn(p.toState()).toPacket(), p.id());

        // Update the traveller's own position and give it the new map snapshot.
        session.send(new Messages.Spawn(p.toState()).toPacket());
        session.send(new Messages.WorldSnapshot(dest.mapId(), visibleEntities(p)).toPacket());
        log.info("Player {} jumped to map {} [{}]", p.name(), dest.mapId(), dest.name());
    }

    /** Applies an authoritative move and broadcasts it to everyone on the map. */
    public void move(PlayerSession session, int x, int y, int dir) {
        Player p = session.player();
        MapInstance map = map(p.mapId());
        int cx = map.clampX(x);
        int cy = map.clampY(y);
        p.moveTo(cx, cy, dir);
        Packet update = new vn.pvtk.protocol.message.Messages.MoveUpdate(p.id(), cx, cy, dir).toPacket();
        broadcastToMap(map, update, -1); // include the mover for authoritative reconciliation
    }

    /** Sends a packet to every player on {@code map} except {@code exceptPlayerId}. */
    public void broadcastToMap(MapInstance map, Packet packet, int exceptPlayerId) {
        for (int pid : map.playerIds()) {
            if (pid == exceptPlayerId) {
                continue;
            }
            sessions.sendTo(pid, packet);
        }
    }

    public void broadcastToAll(Packet packet) {
        for (PlayerSession s : sessions.all()) {
            if (s.isAuthenticated()) {
                s.send(packet);
            }
        }
    }
}
