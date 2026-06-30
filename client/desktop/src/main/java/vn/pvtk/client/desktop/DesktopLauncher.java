package vn.pvtk.client.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import vn.pvtk.client.gdx.PvtkConfig;
import vn.pvtk.client.gdx.PvtkGame;

/**
 * PC launcher (Windows / macOS / Linux) for the libGDX client via the LWJGL3
 * backend. Run with:
 *
 * <pre>
 *   ./gradlew :client-desktop:run --args="--host 127.0.0.1 --port 30000 --user Alice"
 * </pre>
 */
public final class DesktopLauncher {

    public static void main(String[] args) {
        PvtkConfig cfg = new PvtkConfig();
        cfg.host = arg(args, "--host", cfg.host);
        cfg.port = Integer.parseInt(arg(args, "--port", String.valueOf(cfg.port)));
        cfg.username = arg(args, "--user", "PC-" + (System.nanoTime() % 1000));

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Phong Vân (PVTK)");
        config.setWindowedMode(960, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);

        new Lwjgl3Application(new PvtkGame(cfg), config);
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
