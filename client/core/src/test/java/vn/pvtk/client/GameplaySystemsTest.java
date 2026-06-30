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
