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
    private final vn.pvtk.server.account.AccountService accounts;
    private final CountryRegistry countries = new CountryRegistry();
    private final TeamRegistry teams = new TeamRegistry();
    private final MailRegistry mail = new MailRegistry();
    private final MarketRegistry market = new MarketRegistry();
    private final WarManager war = new WarManager();
    private final ArenaManager arena = new ArenaManager();
    // Followers keyed by owner player id.
    private final Map<Integer, Pet> petsByOwner = new ConcurrentHashMap<>();
    private final Map<Integer, Pet> escortByOwner = new ConcurrentHashMap<>();
    private final Map<Integer, MapInstance> maps = new ConcurrentHashMap<>();
    // monsters indexed globally by id and per-map for AOI.
    private final Map<Integer, Monster> monstersById = new ConcurrentHashMap<>();
    private final Map<Integer, List<Monster>> monstersByMap = new ConcurrentHashMap<>();

    public World(SessionManager sessions, GameData data, vn.pvtk.server.account.AccountService accounts) {
        this.sessions = sessions;
        this.data = data;
        this.accounts = accounts;
        // A small set of starter maps. In a full build these load from data files
        // converted from the original client's `map/` resources.
        register(new MapInstance(1, "Tân Thủ Thôn", 64, 64, 32, 32));
        register(new MapInstance(2, "Lạc Dương Thành", 96, 96, 48, 48));
        register(new MapInstance(3, "Hoang Dã", 128, 128, 20, 20));
        register(new MapInstance(4, "Đấu Trường", 48, 48, 24, 24));
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

    public MarketRegistry market() {
        return market;
    }

    public WarManager war() {
        return war;
    }

    public ArenaManager arena() {
        return arena;
    }

    // ------------------------------------------------------------------
    // Turn-based battle
    // ------------------------------------------------------------------

    private final Map<Integer, Battle> battles = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger battleIds =
            new java.util.concurrent.atomic.AtomicInteger(1);

    /** Starts a turn-based battle against a monster; sends the battle model to the client. */
    public void enterBattle(PlayerSession session, int monsterId) {
        Player p = session.player();
        if (p.inBattle()) {
            return;
        }
        Monster monster = monstersById.get(monsterId);
        if (monster == null || monster.isDead() || monster.isLocked()) {
            return;
        }
        monster.locked(true);
        Pet pet = petsByOwner.get(p.id());
        Battle battle = new Battle(battleIds.getAndIncrement(), data, p, pet, List.of(monster));
        battles.put(battle.id(), battle);
        p.battleId(battle.id());
        session.send(battle.enterUpdate().toPacket());
    }

    /** Resolves one battle round from the player's plan and applies the outcome. */
    public void battlePlan(PlayerSession session, int targetIndex, int skillId) {
        Player p = session.player();
        Battle battle = battles.get(p.battleId());
        if (battle == null) {
            return;
        }
        var update = battle.resolve(targetIndex, skillId);
        session.send(update.toPacket());

        if (battle.status() == Battle.WIN) {
            for (int enemyId : battle.enemyEntityIds()) {
                Monster m = monstersById.get(enemyId);
                if (m != null) {
                    m.kill(System.currentTimeMillis());
                    broadcastToMap(map(p.mapId()), new Messages.Despawn(enemyId).toPacket(), -1);
                }
            }
            p.addGold(battle.rewardGold());
            p.addExp(battle.rewardExp());
            p.addKill();
            p.incrementKillQuests();
            endBattle(session, battle);
            session.send(new Messages.Spawn(p.toState()).toPacket());
            checkAchievements(session);
        } else if (battle.status() == Battle.LOSE) {
            for (int enemyId : battle.enemyEntityIds()) {
                Monster m = monstersById.get(enemyId);
                if (m != null) {
                    m.locked(false);
                }
            }
            p.revive();
            MapInstance map = map(p.mapId());
            p.moveTo(map.spawnX(), map.spawnY(), 0);
            broadcastToMap(map, new Messages.MoveUpdate(p.id(), p.x(), p.y(), 0).toPacket(), -1);
            endBattle(session, battle);
            session.send(new Messages.Spawn(p.toState()).toPacket());
        }
    }

    private void endBattle(PlayerSession session, Battle battle) {
        battles.remove(battle.id());
        session.player().battleId(0);
    }

    // ------------------------------------------------------------------
    // Escort caravan (a non-combat follower delivered to a destination map)
    // ------------------------------------------------------------------

    public void startEscort(PlayerSession session) {
        Player p = session.player();
        if (p.escortActive() || p.mapId() != 1) {
            session.send(new Messages.EscortStatus(p.escortActive(), 0,
                    map(2).name(), "Chỉ nhận hộ tống tại Tân Thủ Thôn").toPacket());
            return;
        }
        int destMap = 2;
        p.startEscort(destMap);
        MapInstance map = map(p.mapId());
        Pet caravan = new Pet(p.id(), "Tiêu Xa", 0, p.mapId(),
                map.clampX(p.x() - 1), map.clampY(p.y()), Messages.KIND_NPC, 300);
        escortByOwner.put(p.id(), caravan);
        broadcastToMap(map, new Messages.Spawn(caravan.toState()).toPacket(), -1);
        session.send(new Messages.EscortStatus(true, 0, map(destMap).name(),
                "Hộ tống Tiêu Xa đến " + map(destMap).name()).toPacket());
    }

    private void despawnEscort(Player p) {
        Pet caravan = escortByOwner.get(p.id());
        if (caravan != null) {
            broadcastToMap(map(caravan.mapId()), new Messages.Despawn(caravan.id()).toPacket(), -1);
        }
    }

    /** Called after a map change: completes the escort if the destination is reached. */
    private void checkEscortArrival(PlayerSession session) {
        Player p = session.player();
        if (p.escortActive() && p.mapId() == p.escortDestMap()) {
            int rewardGold = 300;
            int rewardExp = 400;
            p.addGold(rewardGold);
            p.addExp(rewardExp);
            p.clearEscort();
            escortByOwner.remove(p.id());
            session.send(new Messages.Spawn(p.toState()).toPacket());
            session.send(new Messages.EscortStatus(false, 100, map(p.mapId()).name(),
                    "Hoàn thành hộ tống! +" + rewardGold + " vàng, +" + rewardExp + " EXP").toPacket());
        }
    }

    // ------------------------------------------------------------------
    // Pets
    // ------------------------------------------------------------------

    /** Spawns or moves the owner's pet next to them on their current map. */
    public void spawnPet(PlayerSession session) {
        Player p = session.player();
        if (!p.hasPet()) {
            return;
        }
        MapInstance map = map(p.mapId());
        Pet pet = petsByOwner.get(p.id());
        if (pet == null) {
            pet = new Pet(p.id(), p.petName(), p.petBonus(), p.mapId(),
                    map.clampX(p.x() + 1), map.clampY(p.y()));
            petsByOwner.put(p.id(), pet);
        } else {
            pet.place(p.mapId(), map.clampX(p.x() + 1), map.clampY(p.y()));
        }
        broadcastToMap(map, new Messages.Spawn(pet.toState()).toPacket(), -1);
    }

    private void despawnPet(Player p) {
        Pet pet = petsByOwner.get(p.id());
        if (pet != null) {
            broadcastToMap(map(pet.mapId()), new Messages.Despawn(pet.id()).toPacket(), -1);
        }
    }

    private void followWithPet(Player owner) {
        MapInstance map = map(owner.mapId());
        Pet pet = petsByOwner.get(owner.id());
        if (pet != null) {
            pet.place(owner.mapId(), map.clampX(owner.x() + 1), map.clampY(owner.y()));
            broadcastToMap(map, new Messages.MoveUpdate(pet.id(), pet.x(), pet.y(), 0).toPacket(), -1);
        }
        Pet caravan = escortByOwner.get(owner.id());
        if (caravan != null) {
            caravan.place(owner.mapId(), map.clampX(owner.x() - 1), map.clampY(owner.y()));
            broadcastToMap(map, new Messages.MoveUpdate(caravan.id(), caravan.x(), caravan.y(), 0).toPacket(), -1);
        }
    }

    /** Re-places the escort caravan onto the player's current map (after a jump). */
    private void respawnEscort(PlayerSession session) {
        Player p = session.player();
        Pet caravan = escortByOwner.get(p.id());
        if (caravan != null) {
            MapInstance map = map(p.mapId());
            caravan.place(p.mapId(), map.clampX(p.x() - 1), map.clampY(p.y()));
            broadcastToMap(map, new Messages.Spawn(caravan.toState()).toPacket(), -1);
        }
    }

    public GameData data() {
        return data;
    }

    /**
     * Re-evaluates a player's achievements after a relevant event and pushes any
     * newly unlocked ones to their client.
     */
    public void checkAchievements(PlayerSession session) {
        Player p = session.player();
        for (var def : vn.pvtk.server.data.Achievements.all()) {
            if (p.unlockedAchievements().contains(def.id())) {
                continue;
            }
            boolean met = switch (def.kind()) {
                case FIRST_KILL -> p.totalKills() >= 1;
                case KILLS_10 -> p.totalKills() >= 10;
                case REACH_LEVEL_5 -> p.level() >= 5;
                case JOIN_GUILD -> p.countryId() != 0;
                case GOLD_500 -> p.gold() >= 500;
            };
            if (met) {
                p.unlockedAchievements().add(def.id());
                session.send(new Messages.AchievementUnlocked(def.id(), def.name()).toPacket());
            }
        }
    }

    /** Queues a player for the arena; if matched, teleports both fighters in. */
    public void arenaQueue(PlayerSession session) {
        Player p = session.player();
        if (p.inArena()) {
            session.send(new Messages.ArenaStatus(2, name(p.arenaOpponentId()), p.arenaRank(),
                    "Đang trong trận").toPacket());
            return;
        }
        int opponentId = arena.enqueue(p.id());
        if (opponentId == 0) {
            session.send(new Messages.ArenaStatus(1, "", p.arenaRank(), "Đang chờ đối thủ...").toPacket());
            return;
        }
        PlayerSession oppSession = sessions.byPlayerId(opponentId);
        if (oppSession == null || oppSession.player() == null) {
            session.send(new Messages.ArenaStatus(1, "", p.arenaRank(), "Đang chờ đối thủ...").toPacket());
            return;
        }
        Player opp = oppSession.player();
        p.arenaOpponentId(opp.id());
        opp.arenaOpponentId(p.id());
        changeMap(session, 4);
        changeMap(oppSession, 4);
        session.send(new Messages.ArenaStatus(2, opp.name(), p.arenaRank(), "Trận đấu bắt đầu!").toPacket());
        oppSession.send(new Messages.ArenaStatus(2, p.name(), opp.arenaRank(), "Trận đấu bắt đầu!").toPacket());
    }

    private String name(int playerId) {
        PlayerSession s = sessions.byPlayerId(playerId);
        return s != null && s.player() != null ? s.player().name() : "?";
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
        List<MonsterDef> defs = new ArrayList<>(data.monsterList());
        if (defs.isEmpty()) {
            return;
        }
        // Weakest first, so the starter wilderness has beatable monsters.
        defs.sort((a, b) -> a.level() != b.level()
                ? Integer.compare(a.level(), b.level())
                : Integer.compare(a.hpMax(), b.hpMax()));
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

    /** Admin: broadcasts a system chat message to every online player. */
    public void announce(String message) {
        Packet p = new vn.pvtk.protocol.message.Messages.ChatBroadcast(
                vn.pvtk.protocol.message.Messages.Channel.SYSTEM, 0, "Hệ thống", message).toPacket();
        broadcastToAll(p);
    }

    /** Admin: removes a marketplace listing and mails the item back to the seller. */
    public boolean removeMarketListing(int listingId) {
        MarketRegistry.Listing l = market.remove(listingId);
        if (l == null) {
            return false;
        }
        mail.send("Chợ", l.sellerName(), "Hủy rao bán",
                "Quản trị đã gỡ tin rao bán của bạn, vật phẩm được hoàn lại.", 0, l.itemId(), l.count());
        PlayerSession s = findByName(l.sellerName());
        if (s != null && s.player() != null) {
            s.send(new Messages.MailList(mail.inbox(l.sellerName())).toPacket());
        }
        return true;
    }

    /** Admin: instantly respawn every monster (used for "reset boss"). Returns count. */
    public int resetMonsters() {
        int n = 0;
        for (Map.Entry<Integer, List<Monster>> e : monstersByMap.entrySet()) {
            MapInstance map = maps.get(e.getKey());
            for (Monster m : e.getValue()) {
                m.respawn();
                broadcastToMap(map, new Messages.Spawn(m.toState(map.mapId())).toPacket(), -1);
                n++;
            }
        }
        return n;
    }

    /** Finds an online session by player name (case-insensitive). */
    public PlayerSession findByName(String name) {
        if (name == null) {
            return null;
        }
        for (PlayerSession s : sessions.all()) {
            if (s.player() != null && s.player().name().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    /** Admin: adds gold to an online player and pushes the updated bag. Returns true if online. */
    public boolean addGoldOnline(String name, int delta) {
        PlayerSession s = findByName(name);
        if (s == null || s.player() == null) {
            return false;
        }
        Player p = s.player();
        p.addGold(delta);
        s.send(new Messages.BagSnapshot(p.gold(), p.inventory().bagStacks(),
                p.inventory().equipmentStacks()).toPacket());
        s.send(new Messages.Spawn(p.toState()).toPacket());
        return true;
    }

    /** Admin/web: sends a mail (optionally with gold + items) to a player, delivering live if online. */
    public void sendMail(String fromName, String toName, String subject, String body,
                         int gold, java.util.List<int[]> items) {
        mail.send(fromName, toName, subject, body, gold);
        if (items != null) {
            for (int[] it : items) {
                if (it.length >= 2 && it[0] > 0 && it[1] > 0) {
                    mail.send(fromName, toName, subject, "Vật phẩm đính kèm", 0, it[0], it[1]);
                }
            }
        }
        PlayerSession to = findByName(toName);
        if (to != null && to.player() != null) {
            to.send(new Messages.MailList(mail.inbox(toName)).toPacket());
        }
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
        petCombatTick(nowMs);
        monsterAggroTick(nowMs);
    }

    /** Living monsters attack the nearest player in range on their map. */
    private void monsterAggroTick(long nowMs) {
        for (Map.Entry<Integer, List<Monster>> e : monstersByMap.entrySet()) {
            MapInstance map = maps.get(e.getKey());
            for (Monster m : e.getValue()) {
                if (m.isDead() || m.isLocked()) {
                    continue;
                }
                PlayerSession victimSession = null;
                Player victim = null;
                for (int pid : map.playerIds()) {
                    PlayerSession s = sessions.byPlayerId(pid);
                    if (s != null && s.player() != null
                            && Math.abs(s.player().x() - m.x()) <= 3
                            && Math.abs(s.player().y() - m.y()) <= 3) {
                        victimSession = s;
                        victim = s.player();
                        break;
                    }
                }
                if (victim == null) {
                    continue;
                }
                int atk = Math.max(1, Math.max((m.def().atkMin() + m.def().atkMax()) / 2, m.def().level()));
                int dmg = Math.max(1, atk - victim.defense());
                victim.damage(dmg);
                boolean killed = victim.isDead();
                broadcastToMap(map, new CombatEvent(m.id(), victim.id(), dmg, victim.hp(), killed).toPacket(), -1);
                if (killed) {
                    victim.revive();
                    victim.moveTo(map.spawnX(), map.spawnY(), 0);
                    broadcastToMap(map, new MoveUpdate(victim.id(), victim.x(), victim.y(), 0).toPacket(), -1);
                    victimSession.send(new Messages.Spawn(victim.toState()).toPacket());
                }
            }
        }
    }

    /** Each pet auto-attacks the nearest living monster on its map. */
    private void petCombatTick(long nowMs) {
        for (Pet pet : petsByOwner.values()) {
            PlayerSession owner = sessions.byPlayerId(pet.ownerId());
            if (owner == null || owner.player() == null) {
                continue;
            }
            MapInstance map = map(pet.mapId());
            Monster target = null;
            for (Monster m : monstersOnMap(pet.mapId())) {
                if (!m.isDead() && !m.isLocked() && Math.abs(m.x() - pet.x()) <= 4 && Math.abs(m.y() - pet.y()) <= 4) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                continue;
            }
            int dmg = Math.max(1, pet.atkBonus());
            boolean killed = target.damage(dmg, nowMs);
            broadcastToMap(map, new CombatEvent(pet.id(), target.id(), dmg, target.hp(), killed).toPacket(), -1);
            if (killed) {
                Player p = owner.player();
                p.addGold(target.def().rewardMoney());
                p.addExp(target.def().rewardExp());
                p.addKill();
                p.incrementKillQuests();
                owner.send(new Messages.Spawn(p.toState()).toPacket());
                broadcastToMap(map, new Messages.Despawn(target.id()).toPacket(), -1);
                checkAchievements(owner);
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
        spawnPet(session);
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
        // Persist progress back to the account so it survives logout.
        var account = session.account();
        if (account != null && accounts != null) {
            account.gold = p.gold();
            account.level = p.level();
            account.exp = p.exp();
            accounts.save();
        }
        map.removePlayer(p.id());
        despawnPet(p);
        despawnEscort(p);
        petsByOwner.remove(p.id());
        escortByOwner.remove(p.id());
        arena.remove(p.id());
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
        for (Pet pet : petsByOwner.values()) {
            if (pet.mapId() == map.mapId() && pet.ownerId() != viewer.id()) {
                list.add(pet.toState());
            }
        }
        for (Pet caravan : escortByOwner.values()) {
            if (caravan.mapId() == map.mapId() && caravan.ownerId() != viewer.id()) {
                list.add(caravan.toState());
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
        if (monster != null && !monster.isDead() && !monster.isLocked()) {
            int dmg = Math.max(1, atk - 2);
            boolean killed = monster.damage(dmg, nowMs);
            broadcastToMap(map, new CombatEvent(attacker.id(), targetId, dmg, monster.hp(), killed).toPacket(), -1);
            if (killed) {
                attacker.addGold(monster.def().rewardMoney());
                boolean leveled = attacker.addExp(monster.def().rewardExp());
                attacker.addKill();
                attacker.incrementKillQuests();
                // Reward & possible level-up are reflected in the attacker's own state.
                session.send(new Messages.Spawn(attacker.toState()).toPacket());
                if (leveled) {
                    log.info("{} reached level {}", attacker.name(), attacker.level());
                }
                broadcastToMap(map, new Messages.Despawn(targetId).toPacket(), -1);
                checkAchievements(session);
            }
            return;
        }

        // Escort robbery: target is someone else's caravan.
        for (Pet caravan : escortByOwner.values()) {
            if (caravan.id() == targetId && caravan.ownerId() != attacker.id()) {
                boolean destroyed = caravan.damage(atk);
                broadcastToMap(map, new CombatEvent(attacker.id(), targetId, atk, caravan.hp(), destroyed).toPacket(), -1);
                if (destroyed) {
                    int loot = 200;
                    attacker.addGold(loot);
                    session.send(new Messages.Spawn(attacker.toState()).toPacket());
                    PlayerSession ownerSession = sessions.byPlayerId(caravan.ownerId());
                    broadcastToMap(map, new Messages.Despawn(caravan.id()).toPacket(), -1);
                    escortByOwner.remove(caravan.ownerId());
                    if (ownerSession != null && ownerSession.player() != null) {
                        ownerSession.player().clearEscort();
                        ownerSession.send(new Messages.EscortStatus(false, 0, "",
                                "Tiêu xa của bạn đã bị " + attacker.name() + " cướp!").toPacket());
                    }
                }
                return;
            }
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
            if (killed && attacker.inArena() && attacker.arenaOpponentId() == target.id()) {
                resolveArena(session, targetSession);
                return;
            }
            if (killed) {
                target.revive();
                // Respawn the defeated player at the map's spawn point.
                target.moveTo(map.spawnX(), map.spawnY(), 0);
                broadcastToMap(map, new MoveUpdate(target.id(), target.x(), target.y(), 0).toPacket(), -1);
                targetSession.send(new Messages.Spawn(target.toState()).toPacket());
                // Country-war scoring: a kill between the two warring nations scores a point.
                if (attacker.countryId() != 0 && target.countryId() != 0
                        && attacker.countryId() != target.countryId()) {
                    WarManager.War w = war.recordKill(attacker.countryId());
                    if (w != null) {
                        Packet status = w.toStatus().toPacket();
                        broadcastToCountry(w.attackerCountryId(), status);
                        broadcastToCountry(w.defenderCountryId(), status);
                    }
                }
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
        despawnPet(p);
        despawnEscort(p);
        broadcastToMap(old, new Messages.Despawn(p.id()).toPacket(), p.id());

        p.mapId(dest.mapId());
        p.moveTo(dest.spawnX(), dest.spawnY(), 0);
        dest.addPlayer(p.id());
        broadcastToMap(dest, new Messages.Spawn(p.toState()).toPacket(), p.id());
        spawnPet(session);
        respawnEscort(session);

        // Update the traveller's own position and give it the new map snapshot.
        session.send(new Messages.Spawn(p.toState()).toPacket());
        session.send(new Messages.WorldSnapshot(dest.mapId(), visibleEntities(p)).toPacket());
        checkEscortArrival(session);
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
        followWithPet(p);
    }

    /** Ends an arena match: winner gains rank, both return to town. */
    private void resolveArena(PlayerSession winnerSession, PlayerSession loserSession) {
        Player winner = winnerSession.player();
        Player loser = loserSession.player();
        winner.addArenaRank();
        winner.arenaOpponentId(0);
        loser.arenaOpponentId(0);
        loser.revive();
        winnerSession.send(new Messages.ArenaStatus(3, loser.name(), winner.arenaRank(),
                "Bạn thắng! Hạng +1").toPacket());
        loserSession.send(new Messages.ArenaStatus(3, winner.name(), loser.arenaRank(),
                "Bạn thua.").toPacket());
        changeMap(winnerSession, 1);
        changeMap(loserSession, 1);
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
