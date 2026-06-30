package vn.pvtk.server.data;

import java.util.Map;

/** A typed monster definition parsed from the original {@code monster.txt} table. */
public record MonsterDef(
        int id, String name, int icon, int level, int hpMax, int mpMax, int speed,
        int atkMin, int atkMax, int defStr, int defAgi, int defMag,
        int rewardMoney, int rewardExp) {

    static MonsterDef from(Map<String, String> row) {
        return new MonsterDef(
                i(row, "id"), row.getOrDefault("name", ""), i(row, "icon"), i(row, "level"),
                Math.max(1, i(row, "hpMax")), i(row, "mpMax"), i(row, "speed"),
                i(row, "atkMin"), i(row, "atkMax"), i(row, "def_str"), i(row, "def_agi"), i(row, "def_magic"),
                i(row, "rewardMoney"), i(row, "rewardExp"));
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
