package vn.pvtk.protocol.data;

/**
 * Canonical relative paths into the {@code assets/} tree preserved from the
 * original client. Keeping these in one place means the server (content tables)
 * and every graphical client (sprites, maps, UI) resolve assets the same way the
 * original game did.
 *
 * <pre>
 *   assets/
 *     ani/      animation packs (.pd) + frame pngs   — character/effect anims
 *     common/   shared sprites (.fr/.png)            — icons, effects, fonts
 *     map/      world maps (.m/.n/.pd/.pn/.png)       — tiles + collision
 *     mission/  mission/quest data (.mss)
 *     ui/       UI layout descriptors (.ui)
 *     *.txt     content database (item/monster/skill/shop/player ...)
 * </pre>
 */
public final class AssetPaths {

    private AssetPaths() {
    }

    public static final String ANI = "ani";
    public static final String COMMON = "common";
    public static final String MAP = "map";
    public static final String MISSION = "mission";
    public static final String UI = "ui";

    /** Animation pack, e.g. {@code ani/10.pd}. */
    public static String animation(int id) {
        return ANI + "/" + id + ".pd";
    }

    /** Common sprite frame data, e.g. {@code common/1.fr}. */
    public static String commonFrame(int id) {
        return COMMON + "/" + id + ".fr";
    }

    public static String commonImage(int id) {
        return COMMON + "/" + id + ".png";
    }

    /** Map tile image, e.g. {@code map/1.png}. */
    public static String mapImage(int id) {
        return MAP + "/" + id + ".png";
    }

    /** Map metadata/collision, e.g. {@code map/1.m}. */
    public static String mapMeta(int id) {
        return MAP + "/" + id + ".m";
    }

    /** Mission/quest data, e.g. {@code mission/1.mss}. */
    public static String mission(int id) {
        return MISSION + "/" + id + ".mss";
    }

    /** UI layout descriptor, e.g. {@code ui/100.ui}. */
    public static String ui(int id) {
        return UI + "/" + id + ".ui";
    }

    /** A root content table, e.g. {@code item.txt}. */
    public static String dataTable(String name) {
        return name.endsWith(".txt") ? name : name + ".txt";
    }
}
