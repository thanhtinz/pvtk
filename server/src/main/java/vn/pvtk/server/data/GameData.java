package vn.pvtk.server.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** One purchasable line in an NPC shop: an item and its gold price. */
    public record ShopOffer(int itemId, int price) { }

    // Typed registries built from the raw tables.
    private final Map<Integer, ItemDef> items = new LinkedHashMap<>();
    private final Map<Integer, MonsterDef> monsters = new LinkedHashMap<>();
    private final Map<Integer, SkillDef> skills = new LinkedHashMap<>();
    private final Map<Integer, List<ShopOffer>> shops = new LinkedHashMap<>();

    public GameData(Path assetsRoot) {
        this.assetsRoot = assetsRoot;
    }

    /**
     * Resolves the assets directory from {@code PVTK_ASSETS}, otherwise walks up
     * from the working directory looking for an {@code assets/} folder that
     * contains the content tables. This keeps it working no matter how deeply a
     * module (e.g. {@code client/core}) is nested.
     */
    public static Path resolveAssetsRoot() {
        String env = System.getenv("PVTK_ASSETS");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        Path dir = Paths.get("").toAbsolutePath();
        for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
            Path candidate = dir.resolve("assets");
            if (Files.isRegularFile(candidate.resolve("item.txt"))) {
                return candidate;
            }
        }
        return Paths.get("assets"); // fall back; loadAll() will warn if absent
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
        buildRegistries();
        log.info("Loaded content DB from {}: {} items, {} monsters, {} skills, {} shops",
                assetsRoot.toAbsolutePath(),
                items.size(), monsters.size(), count("skill"), count("shop"));
        return this;
    }

    private void buildRegistries() {
        DataTable itemTable = tables.get("item");
        if (itemTable != null) {
            for (Map<String, String> row : itemTable.rows()) {
                ItemDef def = ItemDef.from(row);
                if (def.id() > 0) {
                    items.put(def.id(), def);
                }
            }
        }
        DataTable monsterTable = tables.get("monster");
        if (monsterTable != null) {
            for (Map<String, String> row : monsterTable.rows()) {
                MonsterDef def = MonsterDef.from(row);
                if (def.id() > 0) {
                    monsters.put(def.id(), def);
                }
            }
        }
        DataTable skillTable = tables.get("skill");
        if (skillTable != null) {
            for (Map<String, String> row : skillTable.rows()) {
                SkillDef def = SkillDef.from(row);
                if (def.id() > 0 && !skills.containsKey(def.id())) {
                    skills.put(def.id(), def); // first (lowest) level row per skill id
                }
            }
        }
        DataTable shopTable = tables.get("shop");
        if (shopTable != null) {
            for (Map<String, String> row : shopTable.rows()) {
                int shopId = parse(row.get("shopID"));
                int itemId = parse(row.get("itemID"));
                int price = parse(row.get("money1"));
                if (shopId > 0 && itemId > 0) {
                    shops.computeIfAbsent(shopId, k -> new ArrayList<>()).add(new ShopOffer(itemId, price));
                }
            }
        }
    }

    private static int parse(String v) {
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public ItemDef item(int id) {
        return items.get(id);
    }

    public Map<Integer, ItemDef> items() {
        return Collections.unmodifiableMap(items);
    }

    public MonsterDef monster(int id) {
        return monsters.get(id);
    }

    public List<MonsterDef> monsterList() {
        return new ArrayList<>(monsters.values());
    }

    public SkillDef skill(int id) {
        return skills.get(id);
    }

    public Map<Integer, SkillDef> skills() {
        return Collections.unmodifiableMap(skills);
    }

    /** Offers for an NPC shop, or an empty list if the shop id is unknown. */
    public List<ShopOffer> shop(int shopId) {
        return shops.getOrDefault(shopId, List.of());
    }

    public int shopCount() {
        return shops.size();
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
