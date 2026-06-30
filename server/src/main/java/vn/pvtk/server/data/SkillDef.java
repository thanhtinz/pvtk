package vn.pvtk.server.data;

import java.util.Map;

/** A typed skill definition parsed from the original {@code skill.txt} table. */
public record SkillDef(
        int id, int level, String name, int type, int reqLevel,
        int useMp, int useHp, int power, int powerValue) {

    /** A bounded, sane combat bonus derived from the (noisy) original power values. */
    public int combatBonus() {
        return Math.min(200, 10 + level * 5 + Math.min(100, powerValue / 100));
    }

    static SkillDef from(Map<String, String> row) {
        return new SkillDef(
                i(row, "id"), Math.max(1, i(row, "level")), row.getOrDefault("name", ""),
                i(row, "type"), i(row, "reqLevel"),
                i(row, "useMP"), i(row, "useHP"), i(row, "power1"), i(row, "powerValue1"));
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
