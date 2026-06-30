package vn.pvtk.server.data;

import java.util.List;

/** Server-defined achievement catalogue with simple, checkable conditions. */
public final class Achievements {

    private Achievements() {
    }

    public enum Kind { FIRST_KILL, KILLS_10, REACH_LEVEL_5, JOIN_GUILD, GOLD_500 }

    public record AchievementDef(int id, String name, String desc, Kind kind, int threshold) { }

    private static final List<AchievementDef> ALL = List.of(
            new AchievementDef(1, "Sát Thủ Đầu Tiên", "Hạ gục quái đầu tiên.", Kind.FIRST_KILL, 1),
            new AchievementDef(2, "Thợ Săn", "Hạ gục 10 quái.", Kind.KILLS_10, 10),
            new AchievementDef(3, "Cao Thủ Sơ Cấp", "Đạt cấp 5.", Kind.REACH_LEVEL_5, 5),
            new AchievementDef(4, "Đồng Đạo", "Gia nhập một bang.", Kind.JOIN_GUILD, 1),
            new AchievementDef(5, "Phú Hộ", "Tích lũy 500 vàng.", Kind.GOLD_500, 500));

    public static List<AchievementDef> all() {
        return ALL;
    }
}
