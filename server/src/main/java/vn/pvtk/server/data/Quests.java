package vn.pvtk.server.data;

import java.util.List;

/**
 * Server-defined quest catalogue. The original {@code mission/*.mss} files are a
 * custom binary format; rather than reverse-engineer them, this rewrite ships a
 * small hand-authored set of kill-quests so the quest loop works end-to-end.
 */
public final class Quests {

    private Quests() {
    }

    public record QuestDef(int id, String name, String desc, int killTarget,
                           int rewardExp, int rewardGold) { }

    private static final List<QuestDef> ALL = List.of(
            new QuestDef(1, "Diệt Sói Hoang", "Hạ gục 3 quái trong vùng hoang dã.", 3, 150, 100),
            new QuestDef(2, "Thợ Săn Tập Sự", "Hạ gục 10 quái để rèn luyện.", 10, 500, 300),
            new QuestDef(3, "Cao Thủ Diệt Quái", "Hạ gục 25 quái.", 25, 1500, 1000));

    public static List<QuestDef> all() {
        return ALL;
    }

    public static QuestDef byId(int id) {
        return ALL.stream().filter(q -> q.id() == id).findFirst().orElse(null);
    }
}
