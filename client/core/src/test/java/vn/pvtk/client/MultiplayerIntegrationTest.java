package vn.pvtk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.pvtk.protocol.message.Messages.ChatBroadcast;
import vn.pvtk.server.GameServer;
import vn.pvtk.server.ServerConfig;

/**
 * Spins up the real Netty server and two real clients over a loopback socket and
 * verifies the core multiplayer loop: login, spawn broadcast, authoritative
 * movement, and world chat. This exercises the full stack &mdash; wire codec,
 * Netty framing, dispatcher, world broadcast and client parsing &mdash; exactly
 * as a real PC/Android/iOS client would.
 */
class MultiplayerIntegrationTest {

    private GameServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = new GameServer(new ServerConfig("127.0.0.1", 0, 60));
        server.start();
        port = server.boundPort();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void twoPlayersSeeEachOtherMoveAndChat() throws Exception {
        // --- Client A logs in ---
        CountDownLatch aLogin = new CountDownLatch(1);
        GameClient a = new GameClient(new GameClientListener() {
            @Override public void onLoginResult(boolean ok, String msg) {
                if (ok) aLogin.countDown();
            }
        });
        a.connect("127.0.0.1", port);
        a.login("Alice", "pw", 0);
        assertTrue(aLogin.await(3, TimeUnit.SECONDS), "Alice should log in");

        // --- Client B logs in; A should be notified of B's spawn (asserted by polling A's state) ---
        CountDownLatch bLogin = new CountDownLatch(1);
        GameClient b = new GameClient(new GameClientListener() {
            @Override public void onLoginResult(boolean ok, String msg) {
                if (ok) bLogin.countDown();
            }
        });
        b.connect("127.0.0.1", port);
        b.login("Bob", "pw", 0);
        assertTrue(bLogin.await(3, TimeUnit.SECONDS), "Bob should log in");

        // B's snapshot should include Alice; A should learn about Bob via spawn.
        // (Filter to players — the starting map also holds monsters.)
        assertTrue(await(() -> otherPlayers(b) == 1), "Bob should see Alice");
        assertTrue(await(() -> otherPlayers(a) == 1), "Alice should see Bob spawn");
        assertEquals(2, server.sessions().onlineCount());

        int bobId = b.state().self().id;

        // --- Movement: Bob moves, Alice receives the authoritative update ---
        b.move(40, 41, 2);
        assertTrue(await(() -> {
            var bobInAlice = a.state().get(bobId);
            return bobInAlice != null && bobInAlice.x == 40 && bobInAlice.y == 41;
        }), "Alice should see Bob's movement");

        // --- Chat: Bob shouts on WORLD, a third client C receives it ---
        AtomicReference<ChatBroadcast> heard = new AtomicReference<>();
        CountDownLatch cChat = new CountDownLatch(1);
        GameClient c = new GameClient(new GameClientListener() {
            @Override public void onChat(ChatBroadcast chat) {
                heard.set(chat);
                cChat.countDown();
            }
        });
        c.connect("127.0.0.1", port);
        c.login("Cara", "pw", 0);
        assertTrue(await(() -> c.state().self() != null), "Cara should log in");

        b.say("Xin chào thế giới!");
        assertTrue(cChat.await(3, TimeUnit.SECONDS), "Cara should hear world chat");
        assertNotNull(heard.get());
        assertEquals("Xin chào thế giới!", heard.get().text());
        assertEquals("Bob", heard.get().fromName());

        a.disconnect();
        b.disconnect();
        c.disconnect();
    }

    /** Polls a condition for up to 3 seconds. */
    /** Counts other players (KIND_PLAYER) visible to a client, ignoring monsters/pets/NPCs. */
    private static long otherPlayers(GameClient c) {
        return c.state().others().stream()
                .filter(e -> e.kind == vn.pvtk.protocol.message.Messages.KIND_PLAYER)
                .count();
    }

    private static boolean await(java.util.function.BooleanSupplier cond) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (cond.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return cond.getAsBoolean();
    }
}
