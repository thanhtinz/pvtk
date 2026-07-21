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

    /** Entity kind: distinguishes players from monsters/NPCs/pets on the wire. */
    public static final int KIND_PLAYER = 0;
    public static final int KIND_MONSTER = 1;
    public static final int KIND_PET = 2;
    public static final int KIND_NPC = 3;

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

    /** {@code mode}: 0 = log in to an existing account, 1 = register a new one. */
    public record LoginRequest(String username, String password, int serverLine, int mode) {
        public static final int MODE_LOGIN = 0;
        public static final int MODE_REGISTER = 1;

        public LoginRequest(String username, String password, int serverLine) {
            this(username, password, serverLine, MODE_LOGIN);
        }

        public Packet toPacket() {
            Packet p = new Packet(Opcodes.LOGIN);
            p.putByte(0); // sub-type 0 = login request
            p.putString(username).putString(password).putShort(serverLine).putByte(mode);
            return p;
        }

        public static LoginRequest from(Packet p) {
            p.getByte(); // sub-type
            String u = p.getString();
            String pw = p.getString();
            int line = p.getUShort();
            int mode = p.remaining() > 0 ? p.getUByte() : MODE_LOGIN; // back-compat
            return new LoginRequest(u, pw, line, mode);
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
        public static final int USE = 3;

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

    // ==================================================================
    // NPC shop (SHOP_LIST 12020, SHOP_BUY 12021, SHOP_SELL 16001)
    // ==================================================================

    public record ShopEntry(int itemId, String name, int price, int type, int icon) {
        public void write(Packet p) {
            p.putInt(itemId).putString(name).putInt(price).putByte(type).putInt(icon);
        }

        public static ShopEntry read(Packet p) {
            return new ShopEntry(p.getInt(), p.getString(), p.getInt(), p.getUByte(), p.getInt());
        }
    }

    public record ShopOpen(int shopId) {
        public Packet toPacket() {
            return new Packet(Opcodes.SHOP_LIST).putInt(shopId);
        }

        public static ShopOpen from(Packet p) {
            return new ShopOpen(p.getInt());
        }
    }

    public record ShopListing(int shopId, List<ShopEntry> entries) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.SHOP_LIST).putInt(shopId).putShort(entries.size());
            for (ShopEntry e : entries) {
                e.write(p);
            }
            return p;
        }

        public static ShopListing from(Packet p) {
            int shopId = p.getInt();
            int n = p.getUShort();
            List<ShopEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(ShopEntry.read(p));
            }
            return new ShopListing(shopId, list);
        }
    }

    public record ShopBuy(int itemId, int count) {
        public Packet toPacket() {
            return new Packet(Opcodes.SHOP_BUY).putInt(itemId).putShort(count);
        }

        public static ShopBuy from(Packet p) {
            return new ShopBuy(p.getInt(), p.getUShort());
        }
    }

    public record ShopSell(int bagSlot, int count) {
        public Packet toPacket() {
            return new Packet(Opcodes.SHOP_SELL).putShort(bagSlot).putShort(count);
        }

        public static ShopSell from(Packet p) {
            return new ShopSell(p.getUShort(), p.getUShort());
        }
    }

    // ==================================================================
    // Skills (SKILL_LIST = 14001)
    // ==================================================================

    public record SkillEntry(int id, int level, String name, int useMp) {
        public void write(Packet p) {
            p.putInt(id).putShort(level).putString(name).putShort(useMp);
        }

        public static SkillEntry read(Packet p) {
            return new SkillEntry(p.getInt(), p.getUShort(), p.getString(), p.getUShort());
        }
    }

    public record SkillList(List<SkillEntry> skills) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.SKILL_LIST).putShort(skills.size());
            for (SkillEntry s : skills) {
                s.write(p);
            }
            return p;
        }

        public static SkillList from(Packet p) {
            int n = p.getUShort();
            List<SkillEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(SkillEntry.read(p));
            }
            return new SkillList(list);
        }
    }

    // ==================================================================
    // Party / team (13501 invite, 13506 update, 13507 leave)
    // ==================================================================

    public record TeamMember(int id, String name, int level, int hp, int maxHp) {
        public void write(Packet p) {
            p.putInt(id).putString(name).putShort(level).putInt(hp).putInt(maxHp);
        }

        public static TeamMember read(Packet p) {
            return new TeamMember(p.getInt(), p.getString(), p.getUShort(), p.getInt(), p.getInt());
        }
    }

    public record TeamInvite(String targetName) {
        public Packet toPacket() {
            return new Packet(Opcodes.TEAM_INVITE).putString(targetName);
        }

        public static TeamInvite from(Packet p) {
            return new TeamInvite(p.getString());
        }
    }

    /** Server→client party roster (empty list means "you have no team"). */
    public record TeamUpdate(int leaderId, List<TeamMember> members) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.TEAM_UPDATE).putInt(leaderId).putShort(members.size());
            for (TeamMember m : members) {
                m.write(p);
            }
            return p;
        }

        public static TeamUpdate from(Packet p) {
            int leader = p.getInt();
            int n = p.getUShort();
            List<TeamMember> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(TeamMember.read(p));
            }
            return new TeamUpdate(leader, list);
        }
    }

    // ==================================================================
    // Mail (MAIL_SEND = 11009, MAIL_LIST = 11007)
    // ==================================================================

    public record MailSend(String toName, String subject, String body, int gold, int itemId, int itemCount) {
        public Packet toPacket() {
            return new Packet(Opcodes.MAIL_SEND)
                    .putString(toName).putString(subject).putString(body)
                    .putInt(gold).putInt(itemId).putShort(itemCount);
        }

        public static MailSend from(Packet p) {
            return new MailSend(p.getString(), p.getString(), p.getString(),
                    p.getInt(), p.getInt(), p.getUShort());
        }
    }

    public record MailEntry(int id, String fromName, String subject, String body,
                            int gold, int itemId, int itemCount, boolean claimed) {
        public void write(Packet p) {
            p.putInt(id).putString(fromName).putString(subject).putString(body)
                    .putInt(gold).putInt(itemId).putShort(itemCount).putBool(claimed);
        }

        public static MailEntry read(Packet p) {
            return new MailEntry(p.getInt(), p.getString(), p.getString(), p.getString(),
                    p.getInt(), p.getInt(), p.getUShort(), p.getBool());
        }
    }

    public record MailClaim(int mailId) {
        public Packet toPacket() {
            return new Packet(Opcodes.MAIL_CLAIM).putInt(mailId);
        }

        public static MailClaim from(Packet p) {
            return new MailClaim(p.getInt());
        }
    }

    public record MailList(List<MailEntry> mails) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.MAIL_LIST).putShort(mails.size());
            for (MailEntry m : mails) {
                m.write(p);
            }
            return p;
        }

        public static MailList from(Packet p) {
            int n = p.getUShort();
            List<MailEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(MailEntry.read(p));
            }
            return new MailList(list);
        }
    }

    // ==================================================================
    // Quests (QUEST_LIST 14512, QUEST_ACCEPT 14502, QUEST_COMPLETE 14503)
    // ==================================================================

    /** state: 0 = available, 1 = active, 2 = completed. */
    public record QuestEntry(int id, String name, String desc,
                             int progress, int target, int state,
                             int rewardExp, int rewardGold) {
        public void write(Packet p) {
            p.putInt(id).putString(name).putString(desc)
                    .putShort(progress).putShort(target).putByte(state)
                    .putInt(rewardExp).putInt(rewardGold);
        }

        public static QuestEntry read(Packet p) {
            return new QuestEntry(p.getInt(), p.getString(), p.getString(),
                    p.getUShort(), p.getUShort(), p.getUByte(), p.getInt(), p.getInt());
        }
    }

    public record QuestList(List<QuestEntry> quests) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.QUEST_LIST).putShort(quests.size());
            for (QuestEntry q : quests) {
                q.write(p);
            }
            return p;
        }

        public static QuestList from(Packet p) {
            int n = p.getUShort();
            List<QuestEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(QuestEntry.read(p));
            }
            return new QuestList(list);
        }
    }

    public record QuestAction(int questId) {
        public Packet accept() {
            return new Packet(Opcodes.QUEST_ACCEPT).putInt(questId);
        }

        public Packet complete() {
            return new Packet(Opcodes.QUEST_COMPLETE).putInt(questId);
        }

        public static QuestAction from(Packet p) {
            return new QuestAction(p.getInt());
        }
    }

    // ==================================================================
    // Achievements (ACHIEVE_LIST 11024, ACHIEVE_UNLOCK 11025)
    // ==================================================================

    public record AchievementEntry(int id, String name, String desc, boolean unlocked) {
        public void write(Packet p) {
            p.putInt(id).putString(name).putString(desc).putBool(unlocked);
        }

        public static AchievementEntry read(Packet p) {
            return new AchievementEntry(p.getInt(), p.getString(), p.getString(), p.getBool());
        }
    }

    public record AchievementList(List<AchievementEntry> achievements) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.ACHIEVE_LIST).putShort(achievements.size());
            for (AchievementEntry a : achievements) {
                a.write(p);
            }
            return p;
        }

        public static AchievementList from(Packet p) {
            int n = p.getUShort();
            List<AchievementEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(AchievementEntry.read(p));
            }
            return new AchievementList(list);
        }
    }

    public record AchievementUnlocked(int id, String name) {
        public Packet toPacket() {
            return new Packet(Opcodes.ACHIEVE_UNLOCK).putInt(id).putString(name);
        }

        public static AchievementUnlocked from(Packet p) {
            return new AchievementUnlocked(p.getInt(), p.getString());
        }
    }

    // ==================================================================
    // Marketplace (MARKET_LIST 13520, MARKET_SELL 13518, MARKET_BUY 13517)
    // ==================================================================

    public record MarketListing(int listingId, String sellerName,
                                int itemId, String itemName, int count, int price) {
        public void write(Packet p) {
            p.putInt(listingId).putString(sellerName)
                    .putInt(itemId).putString(itemName).putShort(count).putInt(price);
        }

        public static MarketListing read(Packet p) {
            return new MarketListing(p.getInt(), p.getString(),
                    p.getInt(), p.getString(), p.getUShort(), p.getInt());
        }
    }

    public record MarketList(List<MarketListing> listings) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.MARKET_LIST).putShort(listings.size());
            for (MarketListing l : listings) {
                l.write(p);
            }
            return p;
        }

        public static MarketList from(Packet p) {
            int n = p.getUShort();
            List<MarketListing> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(MarketListing.read(p));
            }
            return new MarketList(list);
        }
    }

    public record MarketSell(int bagSlot, int count, int price) {
        public Packet toPacket() {
            return new Packet(Opcodes.MARKET_SELL).putShort(bagSlot).putShort(count).putInt(price);
        }

        public static MarketSell from(Packet p) {
            return new MarketSell(p.getUShort(), p.getUShort(), p.getInt());
        }
    }

    public record MarketBuy(int listingId) {
        public Packet toPacket() {
            return new Packet(Opcodes.MARKET_BUY).putInt(listingId);
        }

        public static MarketBuy from(Packet p) {
            return new MarketBuy(p.getInt());
        }
    }

    // ==================================================================
    // Mercenary / pet companion (MERC_LIST 15503, MERC_BUY 15505)
    // ==================================================================

    public record MercEntry(int id, String name, int level, int atkBonus, int price, boolean owned) {
        public void write(Packet p) {
            p.putInt(id).putString(name).putShort(level).putShort(atkBonus).putInt(price).putBool(owned);
        }

        public static MercEntry read(Packet p) {
            return new MercEntry(p.getInt(), p.getString(), p.getUShort(), p.getUShort(),
                    p.getInt(), p.getBool());
        }
    }

    public record MercList(List<MercEntry> mercs) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.MERC_LIST).putShort(mercs.size());
            for (MercEntry m : mercs) {
                m.write(p);
            }
            return p;
        }

        public static MercList from(Packet p) {
            int n = p.getUShort();
            List<MercEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(MercEntry.read(p));
            }
            return new MercList(list);
        }
    }

    public record MercBuy(int mercId) {
        public Packet toPacket() {
            return new Packet(Opcodes.MERC_BUY).putInt(mercId);
        }

        public static MercBuy from(Packet p) {
            return new MercBuy(p.getInt());
        }
    }

    // ==================================================================
    // Friends / relations (RELATION_LIST 13529, ADD 13530, DEL 13531)
    // ==================================================================

    public record FriendEntry(int id, String name, int level, boolean online) {
        public void write(Packet p) {
            p.putInt(id).putString(name).putShort(level).putBool(online);
        }

        public static FriendEntry read(Packet p) {
            return new FriendEntry(p.getInt(), p.getString(), p.getUShort(), p.getBool());
        }
    }

    public record FriendList(List<FriendEntry> friends) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.RELATION_LIST).putShort(friends.size());
            for (FriendEntry f : friends) {
                f.write(p);
            }
            return p;
        }

        public static FriendList from(Packet p) {
            int n = p.getUShort();
            List<FriendEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(FriendEntry.read(p));
            }
            return new FriendList(list);
        }
    }

    public record FriendAction(String name) {
        public Packet add() {
            return new Packet(Opcodes.RELATION_ADD).putString(name);
        }

        public Packet del() {
            return new Packet(Opcodes.RELATION_DEL).putString(name);
        }

        public static FriendAction from(Packet p) {
            return new FriendAction(p.getString());
        }
    }

    // ==================================================================
    // Country war (WAR_DECLARE 15047, WAR_STATUS 15040)
    // ==================================================================

    public record WarDeclare(int targetCountryId) {
        public Packet toPacket() {
            return new Packet(Opcodes.WAR_DECLARE).putInt(targetCountryId);
        }

        public static WarDeclare from(Packet p) {
            return new WarDeclare(p.getInt());
        }
    }

    /** active=false means "no war"; otherwise the live scoreboard. */
    public record WarStatus(boolean active, String attacker, String defender,
                            int attackerScore, int defenderScore, String message) {
        public Packet toPacket() {
            return new Packet(Opcodes.WAR_STATUS)
                    .putBool(active).putString(attacker).putString(defender)
                    .putShort(attackerScore).putShort(defenderScore)
                    .putString(message == null ? "" : message);
        }

        public static WarStatus from(Packet p) {
            return new WarStatus(p.getBool(), p.getString(), p.getString(),
                    p.getUShort(), p.getUShort(), p.getString());
        }
    }

    // ==================================================================
    // Arena (ARENA_QUEUE 14526, ARENA_STATUS 14528)
    // ==================================================================

    /** state: 0 idle, 1 queued, 2 fighting, 3 result. */
    public record ArenaStatus(int state, String opponent, int rank, String message) {
        public Packet toPacket() {
            return new Packet(Opcodes.ARENA_STATUS)
                    .putByte(state).putString(opponent == null ? "" : opponent)
                    .putShort(rank).putString(message == null ? "" : message);
        }

        public static ArenaStatus from(Packet p) {
            return new ArenaStatus(p.getUByte(), p.getString(), p.getUShort(), p.getString());
        }
    }

    // ==================================================================
    // Escort (ESCORT_START 14510, ESCORT_STATUS 14514)
    // ==================================================================

    /** active escort progress; destMap names where the caravan must be delivered. */
    public record EscortStatus(boolean active, int progress, String destMap, String message) {
        public Packet toPacket() {
            return new Packet(Opcodes.ESCORT_STATUS)
                    .putBool(active).putShort(progress)
                    .putString(destMap == null ? "" : destMap)
                    .putString(message == null ? "" : message);
        }

        public static EscortStatus from(Packet p) {
            return new EscortStatus(p.getBool(), p.getUShort(), p.getString(), p.getString());
        }
    }

    // ==================================================================
    // Turn-based battle (BATTLE_ENTER 12501, BATTLE_PLAN 12505, BATTLE_UPDATE 12506)
    //
    // Faithful to the original's plan-then-resolve model: the client enters a
    // battle, then each round submits a plan; the server resolves every actor in
    // speed order and replies with the action log + updated combatant states until
    // one side is wiped out.
    // ==================================================================

    /** One participant in a battle. side 0 = the player's team, 1 = enemies. */
    public record Combatant(int index, String name, int side, int hp, int maxHp, int mp, int maxMp) {
        public boolean alive() {
            return hp > 0;
        }

        public void write(Packet p) {
            p.putByte(index).putString(name).putByte(side)
                    .putInt(hp).putInt(maxHp).putInt(mp).putInt(maxMp);
        }

        public static Combatant read(Packet p) {
            return new Combatant(p.getUByte(), p.getString(), p.getUByte(),
                    p.getInt(), p.getInt(), p.getInt(), p.getInt());
        }
    }

    /** One resolved action within a round (for playback / the combat log). */
    public record BattleAction(int attacker, int target, int damage, int targetHp, boolean died, int skillId) {
        public void write(Packet p) {
            p.putByte(attacker).putByte(target).putInt(damage).putInt(targetHp)
                    .putBool(died).putInt(skillId);
        }

        public static BattleAction read(Packet p) {
            return new BattleAction(p.getUByte(), p.getUByte(), p.getInt(), p.getInt(),
                    p.getBool(), p.getInt());
        }
    }

    /** Client → server: enter a turn battle against a monster on the current map. */
    public record BattleEnter(int monsterId) {
        public Packet toPacket() {
            return new Packet(Opcodes.BATTLE_ENTER).putInt(monsterId);
        }

        public static BattleEnter from(Packet p) {
            return new BattleEnter(p.getInt());
        }
    }

    /** Client → server: the player's chosen action for {@code round}. */
    public record BattlePlan(int round, int targetIndex, int skillId) {
        public Packet toPacket() {
            return new Packet(Opcodes.BATTLE_PLAN).putShort(round).putByte(targetIndex).putInt(skillId);
        }

        public static BattlePlan from(Packet p) {
            return new BattlePlan(p.getUShort(), p.getUByte(), p.getInt());
        }
    }

    // ==================================================================
    // Currency: gold (in-game), coin ("Tiền nạp"), xu (web wallet)
    // (CURRENCY_INFO 11078 server->client, CONVERT_XU 11030 client->server)
    // ==================================================================

    public record CurrencyInfo(long gold, long coin, long xu) {
        public Packet toPacket() {
            return new Packet(Opcodes.CURRENCY_INFO).putLong(gold).putLong(coin).putLong(xu);
        }

        public static CurrencyInfo from(Packet p) {
            return new CurrencyInfo(p.getLong(), p.getLong(), p.getLong());
        }
    }

    /** Client→server: convert {@code amount} of web Xu into in-game coin (rate applied server-side). */
    public record ConvertXu(long amount) {
        public Packet toPacket() {
            return new Packet(Opcodes.CONVERT_XU).putLong(amount);
        }

        public static ConvertXu from(Packet p) {
            return new ConvertXu(p.getLong());
        }
    }

    // ==================================================================
    // In-game top-up packages ("Gói nạp"): spend web Xu to receive
    // in-game coin + bonus items, chosen from a menu inside the game.
    // (REDEEM_LIST 11031 both ways, REDEEM_BUY 11032 client->server)
    // ==================================================================

    /** One redeemable package: {@code bonus} is a display string of the items granted. */
    public record RedeemEntry(int id, String name, long costXu, long coin, String bonus) {
        public void write(Packet p) {
            p.putInt(id).putString(name).putLong(costXu).putLong(coin).putString(bonus);
        }

        public static RedeemEntry read(Packet p) {
            return new RedeemEntry(p.getInt(), p.getString(), p.getLong(), p.getLong(), p.getString());
        }
    }

    /** Server→client: the catalogue of in-game top-up packages. */
    public record RedeemList(List<RedeemEntry> packages) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.REDEEM_LIST).putShort(packages.size());
            for (RedeemEntry e : packages) {
                e.write(p);
            }
            return p;
        }

        public static RedeemList from(Packet p) {
            int n = p.getUShort();
            List<RedeemEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(RedeemEntry.read(p));
            }
            return new RedeemList(list);
        }
    }

    /** Client→server: redeem the package with {@code packageId}. */
    public record RedeemBuy(int packageId) {
        public Packet toPacket() {
            return new Packet(Opcodes.REDEEM_BUY).putInt(packageId);
        }

        public static RedeemBuy from(Packet p) {
            return new RedeemBuy(p.getInt());
        }
    }

    /** roundState: 0 ongoing, 1 win, 2 lose, 3 error. */
    public record BattleUpdate(int battleId, int round, int roundState,
                               List<Combatant> combatants, List<BattleAction> actions,
                               int rewardExp, int rewardGold, String message) {
        public Packet toPacket() {
            Packet p = new Packet(Opcodes.BATTLE_UPDATE)
                    .putInt(battleId).putShort(round).putByte(roundState);
            p.putByte(combatants.size());
            for (Combatant c : combatants) {
                c.write(p);
            }
            p.putByte(actions.size());
            for (BattleAction a : actions) {
                a.write(p);
            }
            p.putInt(rewardExp).putInt(rewardGold).putString(message == null ? "" : message);
            return p;
        }

        public static BattleUpdate from(Packet p) {
            int battleId = p.getInt();
            int round = p.getUShort();
            int roundState = p.getUByte();
            int nc = p.getUByte();
            List<Combatant> combatants = new ArrayList<>(nc);
            for (int i = 0; i < nc; i++) {
                combatants.add(Combatant.read(p));
            }
            int na = p.getUByte();
            List<BattleAction> actions = new ArrayList<>(na);
            for (int i = 0; i < na; i++) {
                actions.add(BattleAction.read(p));
            }
            return new BattleUpdate(battleId, round, roundState, combatants, actions,
                    p.getInt(), p.getInt(), p.getString());
        }
    }
}
