package vn.pvtk.web;

import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.server.GameServer;
import vn.pvtk.server.ServerConfig;

/**
 * Single entry point that runs the game server and the website together in one
 * process, sharing the same world, accounts and content DB.
 *
 * <pre>
 *   ./gradlew :web:run
 *   PVTK_PORT=30000 PVTK_WEB_PORT=8080 ./gradlew :web:run
 * </pre>
 */
public final class WebMain {

    private static final Logger log = LoggerFactory.getLogger(WebMain.class);

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromArgs(args);
        int webPort = Integer.parseInt(env("PVTK_WEB_PORT", "8080"));

        GameServer game = new GameServer(config);
        WebData web = new WebData(Paths.get("data"));
        WebServer site = new WebServer(game, web, webPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            site.stop();
            game.stop();
        }, "pvtk-web-shutdown"));

        game.start();
        site.start();
        log.info("PVTK is up — game on :{}, website on http://localhost:{}", config.port(), webPort);
        game.awaitShutdown();
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v != null && !v.isBlank() ? v : def;
    }
}
