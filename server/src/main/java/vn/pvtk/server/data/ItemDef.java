package vn.pvtk.server.data;

import java.util.Map;

/**
 * A typed item definition parsed from the original {@code item.txt} content table.
 * Only the columns the gameplay actually uses are surfaced; the rest remain
 * available via the raw {@link vn.pvtk.protocol.data.DataTable} row if needed.
 */
public record ItemDef(
        int id, String name, int type, int grade, int reqLv,
        int atkMin, int atkMax, int defStr, int defAgi, int defMag,
        int price, int stackNum, int durMax, int icon, String info,
        int power1, int powerValue1, int power2, int powerValue2) {

    // Consumable effect codes from the original item.txt {@code powerN} columns.
    /** Restore a percentage of max HP (e.g. the vitality herb: power 52, value 30). */
    public static final int POWER_RESTORE_HP = 52;
    /** Restore a percentage of max MP (power 50, value 30). */
    public static final int POWER_RESTORE_MP = 50;

    /** Equipment slot encoding (item.txt {@code type}); non-equippable items use {@code NONE}. */
    public boolean isEquippable() {
        return type >= 0 && type <= 9; // types 0..9 are gear slots in the original data
    }

    public boolean isStackable() {
        return stackNum > 1;
    }

    /** Percentage restored for a given power code, checking both power slots. */
    public int restorePercent(int powerCode) {
        int pct = 0;
        if (power1 == powerCode) {
            pct = Math.max(pct, powerValue1);
        }
        if (power2 == powerCode) {
            pct = Math.max(pct, powerValue2);
        }
        return pct;
    }

    public int hpRestorePercent() {
        return restorePercent(POWER_RESTORE_HP);
    }

    public int mpRestorePercent() {
        return restorePercent(POWER_RESTORE_MP);
    }

    /** A usable consumable (restores HP and/or MP). */
    public boolean isConsumable() {
        return hpRestorePercent() > 0 || mpRestorePercent() > 0;
    }

    static ItemDef from(Map<String, String> row) {
        return new ItemDef(
                i(row, "id"), row.getOrDefault("name", ""), i(row, "type"), i(row, "grade"), i(row, "reqLv"),
                i(row, "atkMin"), i(row, "atkMax"), i(row, "def_str"), i(row, "def_agi"), i(row, "def_mag"),
                i(row, "price"), Math.max(1, i(row, "stackNum")), i(row, "durMax"), i(row, "icon"),
                row.getOrDefault("info", ""),
                i(row, "power1"), i(row, "powerValue1"), i(row, "power2"), i(row, "powerValue2"));
    }

    private static int i(Map<String, String> row, String key) {
        String v = row.get(key);
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
