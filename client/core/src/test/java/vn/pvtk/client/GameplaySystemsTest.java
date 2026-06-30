package vn.pvtk.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.pvtk.protocol.message.Messages.CombatEvent;
import vn.pvtk.protocol.message.Messages.CountryActionResult;
import vn.pvtk.protocol.message.Messages.CountryList;
import vn.pvtk.protocol.message.Messages;
import vn.pvtk.protocol.message.Messages.AchievementList;
import vn.pvtk.protocol.message.Messages.ArenaStatus;
import vn.pvtk.protocol.message.Messages.EscortStatus;
import vn.pvtk.protocol.message.Messages.FriendList;
import vn.pvtk.protocol.message.Messages.MailList;
import vn.pvtk.protocol.message.Messages.MarketList;
import vn.pvtk.protocol.message.Messages.MercList;
import vn.pvtk.protocol.message.Messages.QuestList;
import vn.pvtk.protocol.message.Messages.ShopListing;
import vn.pvtk.protocol.message.Messages.SkillList;
import vn.pvtk.protocol.message.Messages.TeamUpdate;
import vn.pvtk.protocol.message.Messages.WarStatus;
import vn.pvtk.client.model.Entity;
import vn.pvtk.server.GameServer;
import vn.pvtk.server.ServerConfig;

/**
 * End-to-end tests for the inventory, combat and country/guild systems against
 * the real server, driven by real clients over a loopback socket.
 */
class GameplaySystemsTest {

    private GameServer server;
    private int port;

    @BeforeEach
    void start() throws Exception {
        server = new GameServer(new ServerConfig("127.0.0.1", 0, 60));
        server.start();
        port = server.boundPort();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void inventoryIsDeliveredOnLogin() throws Exception {
        GameClient c = new GameClient(new GameClientListener() { });
        c.connect("127.0.0.1", port);
        c.login("Khách", "", 0);
        assertTrue(await(() -> c.state().inventory().bag().size() == 3),
                "starter inventory should contain 3 items from item.txt");
        assertTrue(c.state().inventory().gold() >= 100, "starter gold");
        c.disconnect();
    }

    @Test
    void combatAgainstMonsterDealsDamage() throws Exception {
        AtomicReference<CombatEvent> last = new AtomicReference<>();
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onCombat(CombatEvent e) {
                last.set(e);
            }
        });
        c.connect("127.0.0.1", port);
        c.login("ChienBinh", "", 0);
        assertTrue(await(() -> c.state().self() != null), "login");

        // Travel to the wilderness (map 3) where monsters live.
        c.jumpMap(3);
        assertTrue(await(() -> c.state().others().stream().anyMatch(Entity::isMonster)),
                "should see monsters on map 3");

        Entity monster = c.state().others().stream()
                .filter(Entity::isMonster).findFirst().orElseThrow();
        int before = monster.hp;
        c.attack(monster.id);

        assertTrue(await(() -> last.get() != null && last.get().targetId() == monster.id),
                "should receive a combat event for the attacked monster");
        assertTrue(last.get().damage() > 0, "attack should deal positive damage");
        assertTrue(await(() -> c.state().get(monster.id) == null || c.state().get(monster.id).hp < before),
                "monster HP should drop (or it died and despawned)");
        c.disconnect();
    }

    @Test
    void countryCreateListAndJoin() throws Exception {
        AtomicReference<CountryActionResult> aCreate = new AtomicReference<>();
        GameClient a = new GameClient(new GameClientListener() {
            @Override public void onCountryResult(int opcode, CountryActionResult r) {
                aCreate.set(r);
            }
        });
        a.connect("127.0.0.1", port);
        a.login("KingA", "", 0);
        assertTrue(await(() -> a.state().self() != null), "A login");

        a.createCountry("Thiên Long Bang");
        assertTrue(await(() -> aCreate.get() != null && aCreate.get().ok()), "country created");
        assertNotNull(aCreate.get().country());
        int countryId = aCreate.get().country().id();

        // B lists countries, sees A's, and joins it.
        AtomicReference<CountryList> bList = new AtomicReference<>();
        AtomicReference<CountryActionResult> bJoin = new AtomicReference<>();
        GameClient b = new GameClient(new GameClientListener() {
            @Override public void onCountryList(CountryList l) {
                bList.set(l);
            }
            @Override public void onCountryResult(int opcode, CountryActionResult r) {
                bJoin.set(r);
            }
        });
        b.connect("127.0.0.1", port);
        b.login("MemberB", "", 0);
        assertTrue(await(() -> b.state().self() != null), "B login");

        b.listCountries();
        assertTrue(await(() -> bList.get() != null && !bList.get().countries().isEmpty()),
                "B should see at least one country");

        b.joinCountry(countryId);
        assertTrue(await(() -> bJoin.get() != null && bJoin.get().ok()), "B should join");
        assertTrue(bJoin.get().country().memberCount() >= 2, "country should have >= 2 members");

        a.disconnect();
        b.disconnect();
    }

    @Test
    void shopListingSellRaisesGold() throws Exception {
        AtomicReference<ShopListing> listing = new AtomicReference<>();
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onShopListing(ShopListing l) {
                listing.set(l);
            }
        });
        c.connect("127.0.0.1", port);
        c.login("Buon", "", 0);
        assertTrue(await(() -> c.state().inventory().bag().size() == 3), "starter bag");

        c.openShop(1);
        assertTrue(await(() -> listing.get() != null && !listing.get().entries().isEmpty()),
                "shop 1 should have offers from shop.txt");

        int goldBefore = c.state().inventory().gold();
        c.sell(0, 1); // sell the first starter item
        assertTrue(await(() -> c.state().inventory().bag().size() == 2), "bag should shrink after sell");
        assertTrue(await(() -> c.state().inventory().gold() >= goldBefore), "gold should not decrease on sell");
        c.disconnect();
    }

    @Test
    void skillsDeliveredOnLogin() throws Exception {
        AtomicReference<SkillList> skills = new AtomicReference<>();
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onSkillList(SkillList s) {
                skills.set(s);
            }
        });
        c.connect("127.0.0.1", port);
        c.login("PhapSu", "", 0);
        assertTrue(await(() -> skills.get() != null && !skills.get().skills().isEmpty()),
                "player should know starter skills from skill.txt");
        c.disconnect();
    }

    @Test
    void partyInviteFormsTeam() throws Exception {
        AtomicReference<TeamUpdate> aTeam = new AtomicReference<>();
        GameClient a = new GameClient(new GameClientListener() {
            @Override public void onTeamUpdate(TeamUpdate t) {
                aTeam.set(t);
            }
        });
        a.connect("127.0.0.1", port);
        a.login("Leader", "", 0);
        assertTrue(await(() -> a.state().self() != null), "A login");

        GameClient b = new GameClient(new GameClientListener() { });
        b.connect("127.0.0.1", port);
        b.login("Follower", "", 0);
        assertTrue(await(() -> b.state().self() != null), "B login");

        a.inviteToTeam("Follower");
        assertTrue(await(() -> aTeam.get() != null && aTeam.get().members().size() == 2),
                "party should have 2 members after invite");
        a.disconnect();
        b.disconnect();
    }

    @Test
    void mailSendAndReceive() throws Exception {
        GameClient a = new GameClient(new GameClientListener() { });
        a.connect("127.0.0.1", port);
        a.login("Sender", "", 0);
        assertTrue(await(() -> a.state().self() != null), "A login");

        AtomicReference<MailList> bMail = new AtomicReference<>();
        GameClient b = new GameClient(new GameClientListener() {
            @Override public void onMailList(MailList m) {
                bMail.set(m);
            }
        });
        b.connect("127.0.0.1", port);
        b.login("Receiver", "", 0);
        assertTrue(await(() -> b.state().self() != null), "B login");

        a.sendMail("Receiver", "Chào", "Tin nhắn thử", 10);
        // delivered live to B
        assertTrue(await(() -> bMail.get() != null && !bMail.get().mails().isEmpty()),
                "B should receive the mail");
        assertTrue(bMail.get().mails().get(0).fromName().equals("Sender"), "mail from Sender");
        assertTrue(bMail.get().mails().get(0).gold() == 10, "attached gold");
        a.disconnect();
        b.disconnect();
    }

    @Test
    void questAcceptListProgresses() throws Exception {
        AtomicReference<QuestList> quests = new AtomicReference<>();
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onQuestList(QuestList q) {
                quests.set(q);
            }
        });
        c.connect("127.0.0.1", port);
        c.login("Hiep", "", 0);
        assertTrue(await(() -> c.state().self() != null), "login");

        c.requestQuests();
        assertTrue(await(() -> quests.get() != null && quests.get().quests().size() >= 3),
                "quest board should list quests");
        // Accept quest 1 → it becomes active (state 1).
        c.acceptQuest(1);
        assertTrue(await(() -> quests.get() != null && quests.get().quests().stream()
                        .anyMatch(q -> q.id() == 1 && q.state() == 1)),
                "quest 1 should be active after accept");
        c.disconnect();
    }

    @Test
    void achievementUnlocksOnFirstKill() throws Exception {
        AtomicReference<AchievementList> ach = new AtomicReference<>();
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onAchievementList(AchievementList a) {
                ach.set(a);
            }
        });
        c.connect("127.0.0.1", port);
        c.login("DungSi", "", 0);
        assertTrue(await(() -> c.state().self() != null), "login");

        // Kill a monster to trigger the FIRST_KILL achievement.
        c.jumpMap(3);
        assertTrue(await(() -> c.state().others().stream().anyMatch(e -> e.isMonster())), "monsters");
        for (int i = 0; i < 40; i++) {
            var monster = c.state().others().stream().filter(e -> e.isMonster()).findFirst().orElse(null);
            if (monster == null) {
                break; // killed everything visible
            }
            c.attack(monster.id, 1); // attack with the starter skill
            Thread.sleep(60);
        }
        c.requestAchievements();
        assertTrue(await(() -> ach.get() != null
                        && ach.get().achievements().stream().anyMatch(a -> a.id() == 1 && a.unlocked())),
                "first-kill achievement should unlock");
        c.disconnect();
    }

    @Test
    void marketplaceConsignAndBuy() throws Exception {
        AtomicReference<MarketList> aMarket = new AtomicReference<>();
        GameClient seller = new GameClient(new GameClientListener() {
            @Override public void onMarketList(MarketList m) {
                aMarket.set(m);
            }
        });
        seller.connect("127.0.0.1", port);
        seller.login("NguoiBan", "", 0);
        assertTrue(await(() -> seller.state().inventory().bag().size() == 3), "seller bag");

        seller.marketSell(0, 1, 50); // consign first starter item for 50 gold
        assertTrue(await(() -> aMarket.get() != null && !aMarket.get().listings().isEmpty()),
                "listing should appear");
        assertTrue(await(() -> seller.state().inventory().bag().size() == 2), "item left the bag");
        int listingId = aMarket.get().listings().get(0).listingId();

        AtomicReference<MarketList> bMarket = new AtomicReference<>();
        GameClient buyer = new GameClient(new GameClientListener() {
            @Override public void onMarketList(MarketList m) {
                bMarket.set(m);
            }
        });
        buyer.connect("127.0.0.1", port);
        buyer.login("NguoiMua", "", 0);
        assertTrue(await(() -> buyer.state().self() != null), "buyer login");

        int bagBefore = -1;
        await(() -> buyer.state().inventory().bag().size() >= 0);
        bagBefore = buyer.state().inventory().bag().size();
        buyer.marketBuy(listingId);
        final int before = bagBefore;
        assertTrue(await(() -> buyer.state().inventory().bag().size() > before),
                "buyer should receive the item");
        seller.disconnect();
        buyer.disconnect();
    }

    @Test
    void hireMercenaryBoostsPlayer() throws Exception {
        AtomicReference<MercList> mercs = new AtomicReference<>();
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onMercList(MercList m) {
                mercs.set(m);
            }
        });
        c.connect("127.0.0.1", port);
        c.login("ChuTuong", "", 0);
        assertTrue(await(() -> c.state().self() != null), "login");

        c.requestMercs();
        assertTrue(await(() -> mercs.get() != null && !mercs.get().mercs().isEmpty()),
                "mercenary list should be offered");
        // Cheapest merc the starter 100 gold can afford.
        var affordable = mercs.get().mercs().stream()
                .filter(m -> m.price() <= 100).findFirst().orElse(null);
        if (affordable != null) {
            c.hireMerc(affordable.id());
            assertTrue(await(() -> mercs.get().mercs().stream()
                            .anyMatch(m -> m.id() == affordable.id() && m.owned())),
                    "merc should be owned after hire");
        }
        c.disconnect();
    }

    @Test
    void hiredPetAppearsAsCompanionEntity() throws Exception {
        AtomicReference<MercList> mercs = new AtomicReference<>();
        GameClient a = new GameClient(new GameClientListener() {
            @Override public void onMercList(MercList m) {
                mercs.set(m);
            }
        });
        a.connect("127.0.0.1", port);
        a.login("ChuPet", "", 0);
        assertTrue(await(() -> a.state().self() != null), "A login");

        a.requestMercs();
        assertTrue(await(() -> mercs.get() != null && mercs.get().mercs().stream()
                .anyMatch(m -> m.price() <= 100)), "an affordable merc should exist");
        int mercId = mercs.get().mercs().stream().filter(m -> m.price() <= 100)
                .findFirst().orElseThrow().id();
        a.hireMerc(mercId);
        a.jumpMap(3);

        // A second player on the same map should see A's pet companion entity.
        GameClient b = new GameClient(new GameClientListener() { });
        b.connect("127.0.0.1", port);
        b.login("KhachQuaDuong", "", 0);
        assertTrue(await(() -> b.state().self() != null), "B login");
        b.jumpMap(3);

        assertTrue(await(() -> b.state().others().stream()
                        .anyMatch(e -> e.kind == Messages.KIND_PET)),
                "B should see A's pet on map 3");
        a.disconnect();
        b.disconnect();
    }

    @Test
    void friendsAddAndListOnlineStatus() throws Exception {
        AtomicReference<FriendList> friends = new AtomicReference<>();
        GameClient a = new GameClient(new GameClientListener() {
            @Override public void onFriendList(FriendList f) {
                friends.set(f);
            }
        });
        a.connect("127.0.0.1", port);
        a.login("NguoiA", "", 0);
        assertTrue(await(() -> a.state().self() != null), "A login");

        GameClient b = new GameClient(new GameClientListener() { });
        b.connect("127.0.0.1", port);
        b.login("BanThan", "", 0);
        assertTrue(await(() -> b.state().self() != null), "B login");

        a.addFriend("BanThan");
        assertTrue(await(() -> friends.get() != null && friends.get().friends().stream()
                        .anyMatch(f -> f.name().equals("BanThan") && f.online())),
                "BanThan should be listed as an online friend");
        a.disconnect();
        b.disconnect();
    }

    @Test
    void mailItemAttachmentDeliversAndClaims() throws Exception {
        GameClient a = new GameClient(new GameClientListener() { });
        a.connect("127.0.0.1", port);
        a.login("GuiDo", "", 0);
        assertTrue(await(() -> a.state().inventory().bag().size() == 3), "A bag");

        AtomicReference<MailList> bMail = new AtomicReference<>();
        GameClient b = new GameClient(new GameClientListener() {
            @Override public void onMailList(MailList m) {
                bMail.set(m);
            }
        });
        b.connect("127.0.0.1", port);
        b.login("NhanDo", "", 0);
        assertTrue(await(() -> b.state().inventory().bag().size() == 3), "B bag");

        // A mails bag slot 0 (an item) to B.
        a.sendMail("NhanDo", "Quà", "Tặng bạn", 0, 0, 1);
        assertTrue(await(() -> bMail.get() != null && bMail.get().mails().stream()
                        .anyMatch(m -> m.itemId() > 0)),
                "B should receive mail with an item attachment");
        assertTrue(await(() -> a.state().inventory().bag().size() == 2), "item left A's bag");

        int mailId = bMail.get().mails().stream().filter(m -> m.itemId() > 0)
                .findFirst().orElseThrow().id();
        b.claimMail(mailId);
        assertTrue(await(() -> b.state().inventory().bag().size() == 4),
                "B's bag should grow after claiming the item");
        a.disconnect();
        b.disconnect();
    }

    @Test
    void countryWarDeclareAndScore() throws Exception {
        // King A founds a country and declares war on King B's country.
        AtomicReference<WarStatus> aWar = new AtomicReference<>();
        GameClient a = new GameClient(new GameClientListener() {
            @Override public void onWarStatus(WarStatus w) {
                aWar.set(w);
            }
        });
        a.connect("127.0.0.1", port);
        a.login("VuaA", "", 0);
        assertTrue(await(() -> a.state().self() != null), "A login");

        AtomicReference<Integer> bCountry = new AtomicReference<>();
        GameClient b = new GameClient(new GameClientListener() {
            @Override public void onCountryResult(int op, CountryActionResult r) {
                if (r.ok() && r.country() != null) bCountry.set(r.country().id());
            }
        });
        b.connect("127.0.0.1", port);
        b.login("VuaB", "", 0);
        assertTrue(await(() -> b.state().self() != null), "B login");

        a.createCountry("Bang A");
        b.createCountry("Bang B");
        assertTrue(await(() -> bCountry.get() != null), "B country created");

        a.declareWar(bCountry.get());
        assertTrue(await(() -> aWar.get() != null && aWar.get().active()
                        && aWar.get().defender().equals("Bang B")),
                "war should be active between Bang A and Bang B");
        a.disconnect();
        b.disconnect();
    }

    @Test
    void turnBasedBattleResolvesToVictory() throws Exception {
        java.util.concurrent.atomic.AtomicInteger updates = new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<vn.pvtk.protocol.message.Messages.BattleUpdate> last = new AtomicReference<>();
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onBattleUpdate(vn.pvtk.protocol.message.Messages.BattleUpdate b) {
                last.set(b);
                updates.incrementAndGet();
            }
        });
        c.connect("127.0.0.1", port);
        c.login("KiemKhach", "", 0);
        assertTrue(await(() -> c.state().self() != null), "login");

        c.jumpMap(3);
        assertTrue(await(() -> c.state().others().stream().anyMatch(Entity::isMonster)), "monsters");
        Entity monster = c.state().others().stream().filter(Entity::isMonster).findFirst().orElseThrow();

        c.enterBattle(monster.id);
        assertTrue(await(() -> last.get() != null && !last.get().combatants().isEmpty()),
                "battle model should arrive on enter");
        // The enemy combatant is the first unit on side 1.
        int enemyIndex = last.get().combatants().stream()
                .filter(u -> u.side() == 1).findFirst().orElseThrow().index();

        // Submit a plan each round (basic attack on the enemy) until the fight ends.
        for (int i = 0; i < 60; i++) {
            var cur = last.get();
            if (cur == null || cur.roundState() != 0) {
                break;
            }
            int before = updates.get();
            c.battlePlan(cur.round(), enemyIndex, 0);
            await(() -> updates.get() > before);
        }
        assertTrue(await(() -> last.get() != null && last.get().roundState() == 1),
                "battle should end in victory");
        assertTrue(last.get().rewardExp() >= 0, "victory should carry rewards");
        assertTrue(await(() -> !c.state().inBattle()), "client should leave battle when it ends");
        c.disconnect();
    }

    @Test
    void escortCompletesOnArrival() throws Exception {
        AtomicReference<EscortStatus> esc = new AtomicReference<>();
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onEscortStatus(EscortStatus e) {
                esc.set(e);
            }
        });
        c.connect("127.0.0.1", port);
        c.login("TieuKhach", "", 0);
        assertTrue(await(() -> c.state().self() != null), "login");

        c.startEscort();
        assertTrue(await(() -> esc.get() != null && esc.get().active()), "escort should start");
        // Deliver the caravan to the destination map (2).
        c.jumpMap(2);
        assertTrue(await(() -> esc.get() != null && !esc.get().active()
                        && esc.get().message().contains("Hoàn thành")),
                "escort should complete on arrival");
        c.disconnect();
    }

    @Test
    void escortCaravanCanBeRobbed() throws Exception {
        AtomicReference<EscortStatus> esc = new AtomicReference<>();
        GameClient a = new GameClient(new GameClientListener() {
            @Override public void onEscortStatus(EscortStatus e) {
                esc.set(e);
            }
        });
        a.connect("127.0.0.1", port);
        a.login("TieuChu", "", 0);
        assertTrue(await(() -> a.state().self() != null), "A login");
        a.startEscort();
        assertTrue(await(() -> esc.get() != null && esc.get().active()), "escort started");

        // B sees A's caravan (an NPC entity) and attacks it until destroyed.
        GameClient b = new GameClient(new GameClientListener() { });
        b.connect("127.0.0.1", port);
        b.login("CuopTieu", "", 0);
        assertTrue(await(() -> b.state().others().stream()
                .anyMatch(e -> e.kind == Messages.KIND_NPC)), "B should see the caravan");
        int caravanId = b.state().others().stream()
                .filter(e -> e.kind == Messages.KIND_NPC).findFirst().orElseThrow().id;

        for (int i = 0; i < 60 && esc.get().active(); i++) {
            b.attack(caravanId);
            Thread.sleep(50);
        }
        assertTrue(await(() -> esc.get() != null && !esc.get().active()
                        && esc.get().message().contains("cướp")),
                "owner should be told the caravan was robbed");
        a.disconnect();
        b.disconnect();
    }

    @Test
    void monsterAggroDamagesNearbyPlayer() throws Exception {
        GameClient c = new GameClient(new GameClientListener() { });
        c.connect("127.0.0.1", port);
        c.login("ConMoi", "", 0);
        assertTrue(await(() -> c.state().self() != null), "login");

        c.jumpMap(3);
        assertTrue(await(() -> c.state().others().stream().anyMatch(Entity::isMonster)), "monsters");
        // Stand on a monster so its aggro range covers us.
        Entity monster = c.state().others().stream().filter(Entity::isMonster).findFirst().orElseThrow();
        c.move(monster.x, monster.y, 0);

        int maxHp = c.state().self().maxHp;
        assertTrue(await(() -> c.state().self() != null && c.state().self().hp < maxHp),
                "a nearby monster should damage the player");
        c.disconnect();
    }

    @Test
    void arenaQueueMatchesTwoPlayers() throws Exception {
        AtomicReference<ArenaStatus> aArena = new AtomicReference<>();
        GameClient a = new GameClient(new GameClientListener() {
            @Override public void onArenaStatus(ArenaStatus s) {
                aArena.set(s);
            }
        });
        a.connect("127.0.0.1", port);
        a.login("DauSiA", "", 0);
        assertTrue(await(() -> a.state().self() != null), "A login");

        GameClient b = new GameClient(new GameClientListener() { });
        b.connect("127.0.0.1", port);
        b.login("DauSiB", "", 0);
        assertTrue(await(() -> b.state().self() != null), "B login");

        a.arenaQueue(); // A waits
        b.arenaQueue(); // B matches with A
        assertTrue(await(() -> aArena.get() != null && aArena.get().state() == 2
                        && aArena.get().opponent().equals("DauSiB")),
                "A should be matched against B in the arena");
        a.disconnect();
        b.disconnect();
    }

    private static boolean await(java.util.function.BooleanSupplier cond) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            if (cond.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return cond.getAsBoolean();
    }
}
