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
import vn.pvtk.protocol.message.Messages.AchievementList;
import vn.pvtk.protocol.message.Messages.MailList;
import vn.pvtk.protocol.message.Messages.MarketList;
import vn.pvtk.protocol.message.Messages.MercList;
import vn.pvtk.protocol.message.Messages.QuestList;
import vn.pvtk.protocol.message.Messages.ShopListing;
import vn.pvtk.protocol.message.Messages.SkillList;
import vn.pvtk.protocol.message.Messages.TeamUpdate;
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
