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
        int price, int stackNum, int durMax, int icon, String info) {

    /** Equipment slot encoding (item.txt {@code type}); non-equippable items use {@code NONE}. */
    public boolean isEquippable() {
        return type >= 0 && type <= 9; // types 0..9 are gear slots in the original data
    }

    public boolean isStackable() {
        return stackNum > 1;
    }

    static ItemDef from(Map<String, String> row) {
        return new ItemDef(
                i(row, "id"), row.getOrDefault("name", ""), i(row, "type"), i(row, "grade"), i(row, "reqLv"),
                i(row, "atkMin"), i(row, "atkMax"), i(row, "def_str"), i(row, "def_agi"), i(row, "def_mag"),
                i(row, "price"), Math.max(1, i(row, "stackNum")), i(row, "durMax"), i(row, "icon"),
                row.getOrDefault("info", ""));
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
