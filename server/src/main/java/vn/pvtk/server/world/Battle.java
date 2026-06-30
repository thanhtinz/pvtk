package vn.pvtk.server.world;

import java.util.ArrayList;
import java.util.List;
import vn.pvtk.protocol.message.Messages.BattleAction;
import vn.pvtk.protocol.message.Messages.BattleUpdate;
import vn.pvtk.protocol.message.Messages.Combatant;
import vn.pvtk.server.data.GameData;
import vn.pvtk.server.data.MonsterDef;
import vn.pvtk.server.data.SkillDef;

/**
 * A single turn-based battle, faithful to the original's plan-then-resolve model:
 * each round the player submits one plan, the server resolves every living actor
 * in speed order, and the round result (action log + new states) is returned until
 * one side is wiped out.
 *
 * <p>Side 0 is the player's team (the player and, if present, their pet); side 1
 * is the enemy monster(s). Rewards on victory come from the monster definitions.
 */
public final class Battle {

    public static final int ONGOING = 0;
    public static final int WIN = 1;
    public static final int LOSE = 2;

    /** Internal combatant carrying the stats the resolver needs. */
    static final class Unit {
        int index;
        String name;
        int side;
        int hp, maxHp, mp, maxMp, atk, def, speed;
        // Enemy bookkeeping for rewards / world despawn.
        int monsterEntityId;
        int rewardExp, rewardGold;

        boolean alive() {
            return hp > 0;
        }

        Combatant toProto() {
            return new Combatant(index, name, side, Math.max(0, hp), maxHp, mp, maxMp);
        }
    }

    private final int id;
    private final GameData data;
    private final Player player;
    private final List<Unit> units = new ArrayList<>();
    private int round = 1;
    private int status = ONGOING;
    private int totalRewardExp;
    private int totalRewardGold;

    Battle(int id, GameData data, Player player, Pet pet, List<Monster> enemies) {
        this.id = id;
        this.data = data;
        this.player = player;
        int idx = 0;

        Unit pu = new Unit();
        pu.index = idx++;
        pu.name = player.name();
        pu.side = 0;
        pu.maxHp = player.maxHp();
        pu.hp = player.hp() > 0 ? player.hp() : player.maxHp();
        pu.maxMp = player.maxMp();
        pu.mp = player.mp();
        pu.atk = player.attackPower();
        pu.def = player.defense();
        pu.speed = 10 + player.level();
        units.add(pu);

        if (pet != null) {
            Unit petUnit = new Unit();
            petUnit.index = idx++;
            petUnit.name = pet.toState().name();
            petUnit.side = 0;
            petUnit.maxHp = petUnit.hp = 200;
            petUnit.atk = Math.max(5, pet.atkBonus());
            petUnit.def = 2;
            petUnit.speed = 9;
            units.add(petUnit);
        }

        for (Monster m : enemies) {
            MonsterDef def = m.def();
            Unit eu = new Unit();
            eu.index = idx++;
            eu.name = def.name();
            eu.side = 1;
            eu.maxHp = def.hpMax();
            eu.hp = m.hp() > 0 ? m.hp() : def.hpMax();
            eu.maxMp = eu.mp = def.mpMax();
            eu.atk = Math.max(1, Math.max((def.atkMin() + def.atkMax()) / 2, def.level()));
            // Keep defence modest so a starter can win a fight in a reasonable number of rounds.
            eu.def = Math.min(8, (def.defStr() + def.defAgi() + def.defMag()) / 9);
            eu.speed = Math.max(1, def.speed());
            eu.monsterEntityId = m.id();
            eu.rewardExp = def.rewardExp();
            eu.rewardGold = def.rewardMoney();
            units.add(eu);
        }
    }

    public int id() {
        return id;
    }

    public int round() {
        return round;
    }

    public int status() {
        return status;
    }

    public int rewardExp() {
        return totalRewardExp;
    }

    public int rewardGold() {
        return totalRewardGold;
    }

    /** Enemy entity ids, so the world can despawn them on victory. */
    public List<Integer> enemyEntityIds() {
        List<Integer> ids = new ArrayList<>();
        for (Unit u : units) {
            if (u.side == 1) {
                ids.add(u.monsterEntityId);
            }
        }
        return ids;
    }

    private List<Combatant> snapshot() {
        List<Combatant> list = new ArrayList<>();
        for (Unit u : units) {
            list.add(u.toProto());
        }
        return list;
    }

    /** The battle model sent to the client on entry (round 1, no actions yet). */
    public BattleUpdate enterUpdate() {
        return new BattleUpdate(id, round, ONGOING, snapshot(), List.of(), 0, 0, "Bắt đầu trận đấu!");
    }

    private Unit byIndex(int index) {
        for (Unit u : units) {
            if (u.index == index) {
                return u;
            }
        }
        return null;
    }

    private Unit firstAliveEnemy() {
        for (Unit u : units) {
            if (u.side == 1 && u.alive()) {
                return u;
            }
        }
        return null;
    }

    private Unit firstAliveAlly() {
        for (Unit u : units) {
            if (u.side == 0 && u.alive()) {
                return u;
            }
        }
        return null;
    }

    private boolean sideDead(int side) {
        return units.stream().filter(u -> u.side == side).noneMatch(Unit::alive);
    }

    /**
     * Resolves one round from the player's plan and returns the round update.
     * Actors act in descending speed order; dead actors are skipped.
     */
    public BattleUpdate resolve(int targetIndex, int skillId) {
        List<BattleAction> actions = new ArrayList<>();

        // Determine acting order by speed.
        List<Unit> order = new ArrayList<>(units);
        order.sort((a, b) -> Integer.compare(b.speed, a.speed));

        for (Unit actor : order) {
            if (!actor.alive() || status != ONGOING) {
                continue;
            }
            if (actor.side == 0) {
                boolean isPlayer = actor.index == playerIndex();
                Unit target;
                if (isPlayer) {
                    Unit chosen = byIndex(targetIndex);
                    target = (chosen != null && chosen.side == 1 && chosen.alive()) ? chosen : firstAliveEnemy();
                } else {
                    target = firstAliveEnemy(); // pet / ally auto-targets
                }
                if (target == null) {
                    break;
                }
                int bonus = 0;
                int usedSkill = 0;
                if (isPlayer && skillId > 0 && player.knowsSkill(skillId)) {
                    SkillDef sk = data.skill(skillId);
                    if (sk != null && actor.mp >= sk.useMp()) {
                        actor.mp -= sk.useMp();
                        bonus = sk.combatBonus();
                        usedSkill = skillId;
                    }
                }
                int dmg = Math.max(1, actor.atk + bonus - target.def);
                target.hp -= dmg;
                actions.add(new BattleAction(actor.index, target.index, dmg, Math.max(0, target.hp),
                        !target.alive(), usedSkill));
            } else {
                Unit target = firstAliveAlly();
                if (target == null) {
                    break;
                }
                int dmg = Math.max(1, actor.atk - target.def);
                target.hp -= dmg;
                actions.add(new BattleAction(actor.index, target.index, dmg, Math.max(0, target.hp),
                        !target.alive(), 0));
            }

            if (sideDead(1)) {
                status = WIN;
            } else if (sideDead(0)) {
                status = LOSE;
            }
        }

        String message;
        if (status == WIN) {
            for (Unit u : units) {
                if (u.side == 1) {
                    totalRewardExp += u.rewardExp;
                    totalRewardGold += u.rewardGold;
                }
            }
            message = "Chiến thắng! +" + totalRewardExp + " EXP, +" + totalRewardGold + " vàng";
        } else if (status == LOSE) {
            message = "Bạn đã bại trận.";
        } else {
            round++;
            message = "Vòng " + round;
        }

        // Sync the player's surviving hp/mp back.
        Unit pu = byIndex(playerIndex());
        if (pu != null) {
            player.syncFromBattle(Math.max(status == LOSE ? 0 : 1, pu.hp), pu.mp);
        }

        return new BattleUpdate(id, round, status, snapshot(), actions,
                totalRewardExp, totalRewardGold, message);
    }

    private int playerIndex() {
        return 0; // the player is always the first unit
    }
}
