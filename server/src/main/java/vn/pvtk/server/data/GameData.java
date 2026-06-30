package vn.pvtk.server.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.data.AssetPaths;
import vn.pvtk.protocol.data.DataTable;

/**
 * The server's in-memory content database, loaded from the original game's
 * preserved content tables in {@code assets/} ({@code item.txt},
 * {@code monster.txt}, {@code skill.txt}, {@code shop.txt}, ...).
 *
 * <p>This makes the rebuild a complete project: the authoritative server runs on
 * the real game data rather than placeholders. The assets directory is located
 * via the {@code PVTK_ASSETS} environment variable, falling back to {@code assets}
 * relative to the working directory (the repository root).
 */
public final class GameData {

    private static final Logger log = LoggerFactory.getLogger(GameData.class);

    private final Path assetsRoot;
    private final Map<String, DataTable> tables = new HashMap<>();

    public GameData(Path assetsRoot) {
        this.assetsRoot = assetsRoot;
    }

    /** Resolves the assets directory from {@code PVTK_ASSETS} or sensible defaults. */
    public static Path resolveAssetsRoot() {
        String env = System.getenv("PVTK_ASSETS");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        // Try ./assets then ../assets (when launched from the server module dir).
        Path here = Paths.get("assets");
        if (Files.isDirectory(here)) {
            return here;
        }
        return Paths.get("..", "assets");
    }

    /** Loads the core content tables. Missing files are logged and skipped. */
    public GameData loadAll() {
        if (!Files.isDirectory(assetsRoot)) {
            log.warn("Assets directory not found at {} — running without content tables. "
                    + "Set PVTK_ASSETS to the assets/ folder.", assetsRoot.toAbsolutePath());
            return this;
        }
        for (String name : List.of("item", "monster", "monsterGroup", "monster_reward",
                "skill", "skill_shop", "shop", "player", "player_skill", "job_setting")) {
            load(name);
        }
        log.info("Loaded content DB from {}: {} items, {} monsters, {} skills, {} shops",
                assetsRoot.toAbsolutePath(),
                count("item"), count("monster"), count("skill"), count("shop"));
        return this;
    }

    private void load(String name) {
        Path file = assetsRoot.resolve(AssetPaths.dataTable(name));
        if (!Files.exists(file)) {
            log.debug("Content table {} not present", file);
            return;
        }
        try {
            tables.put(name, DataTable.load(file));
        } catch (IOException e) {
            log.warn("Failed to load content table {}: {}", file, e.getMessage());
        }
    }

    public DataTable table(String name) {
        return tables.get(name);
    }

    public int count(String name) {
        DataTable t = tables.get(name);
        return t == null ? 0 : t.size();
    }

    public Path assetsRoot() {
        return assetsRoot;
    }
}
