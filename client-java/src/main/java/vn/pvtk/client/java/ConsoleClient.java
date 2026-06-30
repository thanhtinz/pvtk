package vn.pvtk.client.java;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import vn.pvtk.client.GameClient;
import vn.pvtk.client.GameClientListener;
import vn.pvtk.client.model.Entity;
import vn.pvtk.protocol.message.Messages.ChatBroadcast;

/**
 * A fully runnable, dependency-free Java reference client for the PC/desktop.
 *
 * <p>It connects to the server, logs in, and offers a small interactive console
 * to move and chat &mdash; useful for manually testing the server and as the
 * canonical example of how to drive {@link GameClient} from any front-end.
 *
 * <pre>
 *   ./gradlew :client-java:run --args="--host 127.0.0.1 --port 30000 --user Alice"
 *
 *   commands:  m &lt;x&gt; &lt;y&gt;   move to tile
 *              s &lt;text&gt;     say on world chat
 *              who          list visible players
 *              quit         disconnect and exit
 * </pre>
 */
public final class ConsoleClient {

    public static void main(String[] args) throws Exception {
        String host = arg(args, "--host", "127.0.0.1");
        int port = Integer.parseInt(arg(args, "--port", "30000"));
        String user = arg(args, "--user", "Player" + (System.nanoTime() % 1000));

        // Holder lets the listener reference the client that is assigned just below.
        GameClient[] ref = new GameClient[1];
        GameClient client = new GameClient(new GameClientListener() {
            @Override public void onConnected() {
                System.out.println("[net] connected");
            }
            @Override public void onLoginResult(boolean ok, String message) {
                System.out.println("[login] " + (ok ? "OK" : "FAIL") + " - " + message);
            }
            @Override public void onWorldChanged() {
                Entity self = ref[0].state().self();
                System.out.println("[world] you=" + (self == null ? "?" : self)
                        + " | others=" + ref[0].state().others().size());
            }
            @Override public void onEntityMoved(int id, int x, int y, int dir) {
                System.out.println("[move] entity " + id + " -> (" + x + "," + y + ")");
            }
            @Override public void onChat(ChatBroadcast chat) {
                System.out.println("[" + chat.channel() + "] " + chat.fromName() + ": " + chat.text());
            }
            @Override public void onDisconnected(String reason) {
                System.out.println("[net] disconnected: " + reason);
            }
        });
        ref[0] = client;

        System.out.printf("Connecting to %s:%d as %s...%n", host, port, user);
        client.connect(host, port);
        client.login(user, "", 0);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        printHelp();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] t = line.split("\\s+", 2);
            switch (t[0]) {
                case "m" -> {
                    String[] xy = t.length > 1 ? t[1].split("\\s+") : new String[0];
                    if (xy.length == 2) {
                        client.move(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), 0);
                    } else {
                        System.out.println("usage: m <x> <y>");
                    }
                }
                case "s" -> {
                    if (t.length > 1) {
                        client.say(t[1]);
                    }
                }
                case "who" -> {
                    System.out.println("you: " + client.state().self());
                    client.state().others().forEach(e -> System.out.println("  - " + e));
                }
                case "quit", "exit" -> {
                    client.disconnect();
                    return;
                }
                default -> printHelp();
            }
        }
    }

    private static void printHelp() {
        System.out.println("commands: m <x> <y> | s <text> | who | quit");
    }

    private static String arg(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) {
                return args[i + 1];
            }
        }
        return def;
    }
}
