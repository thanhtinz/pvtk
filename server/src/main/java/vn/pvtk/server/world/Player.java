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
    private volatile int exp;
    private volatile int gold;
    private volatile int countryId;
    private volatile int mp;
    private volatile int maxMp;
    private volatile int teamId;

    private volatile int petBonus;
    private volatile String petName = "";
    private volatile int totalKills;

    private final Inventory inventory;
    private final java.util.Set<Integer> learnedSkills = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<Integer, Integer> questProgress = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Integer> completedQuests = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Integer> unlockedAchievements = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public Player(String name, int mapId, int x, int y, Inventory inventory) {
        this.id = IDS.getAndIncrement();
        this.name = name;
        this.mapId = mapId;
        this.x = x;
        this.y = y;
        this.dir = 0;
        this.maxHp = 1000;
        this.hp = 1000;
        this.level = 1;
        this.gold = 100;
        this.maxMp = 100;
        this.mp = 100;
        this.inventory = inventory;
    }

    public Inventory inventory() {
        return inventory;
    }

    public int mp() {
        return mp;
    }

    public int maxMp() {
        return maxMp;
    }

    /** Spends MP if affordable; returns true on success. */
    public boolean spendMp(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (mp < amount) {
            return false;
        }
        mp -= amount;
        return true;
    }

    public void regenMp(int amount) {
        mp = Math.min(maxMp, mp + amount);
    }

    public int teamId() {
        return teamId;
    }

    public void teamId(int teamId) {
        this.teamId = teamId;
    }

    public java.util.Set<Integer> learnedSkills() {
        return learnedSkills;
    }

    public void learnSkill(int skillId) {
        learnedSkills.add(skillId);
    }

    public boolean knowsSkill(int skillId) {
        return learnedSkills.contains(skillId);
    }

    // --- pets / mercenaries ---
    public int petBonus() {
        return petBonus;
    }

    public String petName() {
        return petName;
    }

    public void setPet(String name, int bonus) {
        this.petName = name;
        this.petBonus = bonus;
    }

    // --- quests ---
    public java.util.Map<Integer, Integer> questProgress() {
        return questProgress;
    }

    public java.util.Set<Integer> completedQuests() {
        return completedQuests;
    }

    public boolean hasQuest(int id) {
        return questProgress.containsKey(id);
    }

    public void acceptQuest(int id) {
        questProgress.putIfAbsent(id, 0);
    }

    public int questProgressOf(int id) {
        return questProgress.getOrDefault(id, 0);
    }

    /** Advances every active KILL-type quest; quest definitions decide the target. */
    public void incrementKillQuests() {
        questProgress.replaceAll((id, prog) -> prog + 1);
    }

    public void completeQuest(int id) {
        questProgress.remove(id);
        completedQuests.add(id);
    }

    // --- achievements / stats ---
    public int totalKills() {
        return totalKills;
    }

    public int addKill() {
        return ++totalKills;
    }

    public java.util.Set<Integer> unlockedAchievements() {
        return unlockedAchievements;
    }

    public int exp() {
        return exp;
    }

    public int gold() {
        return gold;
    }

    public void addGold(int amount) {
        this.gold = Math.max(0, gold + amount);
    }

    public int countryId() {
        return countryId;
    }

    public void countryId(int countryId) {
        this.countryId = countryId;
    }

    /** Base attack plus equipment and pet bonuses. */
    public int attackPower() {
        return 10 + level * 2 + petBonus + (inventory != null ? inventory.attackBonus() : 0);
    }

    public int defense() {
        return level + (inventory != null ? inventory.defenseBonus() : 0);
    }

    public void damage(int amount) {
        this.hp = Math.max(0, hp - Math.max(1, amount));
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public void revive() {
        this.hp = maxHp;
    }

    /** Grants experience and handles simple level-ups. Returns true if leveled. */
    public boolean addExp(int amount) {
        this.exp += amount;
        boolean leveled = false;
        int need = level * 100;
        while (exp >= need) {
            exp -= need;
            level++;
            maxHp += 100;
            hp = maxHp;
            need = level * 100;
            leveled = true;
        }
        return leveled;
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
